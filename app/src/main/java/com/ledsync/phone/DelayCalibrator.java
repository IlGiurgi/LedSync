// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Measures the "Light delay" by itself: plays a few short chirps through the speaker the music
 * goes to (e.g. a Bluetooth box), listens to them with the microphone and times how long after
 * the mixer released them they were heard. That is how much later than the lights the sound
 * arrives, so the lights are held back by the same amount (minus their own path to the LEDs).
 *
 * The signal work (chirp, matched filter, median) is plain Java so it can be tested on a PC.
 */
final class DelayCalibrator {
    private DelayCalibrator() { }

    static final int SR = 48000;
    /** Chirps played, one every SPACING frames, after LEAD frames of silence. */
    static final int BEEPS = 6;
    static final int LEAD = SR / 2;
    static final int SPACING = SR * 9 / 10;
    /** Sound is looked for from 20 ms before to 750 ms after it left the mixer. */
    static final int SEARCH_BEFORE = SR / 50, SEARCH_AFTER = SR * 3 / 4;
    /**
     * Time the lights themselves need after the mixer: one capture block (20 ms) plus Wi-Fi and the
     * controller drawing the frame. Subtracted from the measured speaker delay.
     */
    static final int LIGHT_PATH_MS = 40;
    /** A correlation peak must stand this many times above the typical level to count as heard. */
    static final double MIN_CLARITY = 10;
    /** The beeps that count must agree within this much of their median. */
    static final double MAX_SPREAD_MS = 25;
    static final int MIN_GOOD = 3;
    private static final int REQ_MIC = 7;

    // ================================================================ signal (plain Java)

    /** 40 ms linear chirp 1.5 -> 5 kHz with soft edges: a sharp, unique correlation peak. */
    static float[] chirp() {
        int n = SR / 25;
        float[] c = new float[n];
        double f0 = 1500, f1 = 5000, dur = n / (double) SR;
        int edge = n / 8;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SR;
            double phase = 2 * Math.PI * (f0 * t + (f1 - f0) * t * t / (2 * dur));
            double env = 1;
            if (i < edge) env = 0.5 - 0.5 * Math.cos(Math.PI * i / edge);
            else if (i >= n - edge) env = 0.5 - 0.5 * Math.cos(Math.PI * (n - 1 - i) / edge);
            c[i] = (float) (Math.sin(phase) * env);
        }
        return c;
    }

    /** The whole test signal: silence, BEEPS chirps SPACING apart, a silent tail. */
    static short[] sequence(float[] chirp, double amplitude) {
        short[] s = new short[LEAD + BEEPS * SPACING + SR / 2];
        for (int k = 0; k < BEEPS; k++) {
            int at = beepFrame(k);
            for (int i = 0; i < chirp.length; i++) s[at + i] = (short) Math.round(chirp[i] * amplitude * 32767);
        }
        return s;
    }

    static int beepFrame(int k) {
        return LEAD + k * SPACING;
    }

    /**
     * Matched filter: where in rec[from..to) the chirp starts. Returns {index, clarity}, where
     * clarity is the peak over the median of the correlation (noise and music give low values).
     * Returns null when the window is outside the recording.
     */
    static double[] findChirp(short[] rec, int recLen, int from, int to, float[] chirp) {
        from = Math.max(0, from);
        to = Math.min(recLen - chirp.length, to);
        if (to <= from) return null;
        // correlation through the FFT: about 100x quicker than the direct sum on a phone
        int span = to - from + chirp.length;
        int n = Integer.highestOneBit(span - 1) << 1;
        double[] xr = new double[n], xi = new double[n], cr = new double[n], ci = new double[n];
        for (int i = 0; i < span; i++) xr[i] = rec[from + i];
        for (int i = 0; i < chirp.length; i++) cr[i] = chirp[i];
        fft(xr, xi, false);
        fft(cr, ci, false);
        for (int i = 0; i < n; i++) {           // X * conj(C)
            double r = xr[i] * cr[i] + xi[i] * ci[i];
            double im = xi[i] * cr[i] - xr[i] * ci[i];
            xr[i] = r;
            xi[i] = im;
        }
        fft(xr, xi, true);
        double[] corr = new double[to - from];
        double best = -1;
        int bestAt = -1;
        for (int lag = 0; lag < corr.length; lag++) {
            double a = Math.abs(xr[lag]);
            corr[lag] = a;
            if (a > best) { best = a; bestAt = lag; }
        }
        java.util.Arrays.sort(corr);
        double median = corr[corr.length / 2];
        return new double[]{from + bestAt, best / Math.max(1e-9, median)};
    }

    /** In-place radix-2 FFT (n a power of two); inverse includes the 1/n. */
    static void fft(double[] re, double[] im, boolean inverse) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? 1 : -1);
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double ur = 1, ui = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    double vr = re[b] * ur - im[b] * ui, vi = re[b] * ui + im[b] * ur;
                    re[b] = re[a] - vr; im[b] = im[a] - vi;
                    re[a] += vr; im[a] += vi;
                    double nr = ur * wr - ui * wi;
                    ui = ur * wi + ui * wr;
                    ur = nr;
                }
            }
        }
        if (inverse) for (int i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
    }

    /**
     * Combines the per-beep delays (ms, NaN = not heard): median of those that agree with it.
     * Returns {delayMs, beepsUsed} or null if too few beeps were heard or they disagree.
     */
    static double[] combine(double[] delays) {
        List<Double> ok = new ArrayList<>();
        for (double d : delays) if (!Double.isNaN(d)) ok.add(d);
        if (ok.size() < MIN_GOOD) return null;
        Collections.sort(ok);
        double median = ok.get(ok.size() / 2);
        List<Double> close = new ArrayList<>();
        for (double d : ok) if (Math.abs(d - median) <= MAX_SPREAD_MS) close.add(d);
        if (close.size() < MIN_GOOD) return null;
        double sum = 0;
        for (double d : close) sum += d;
        return new double[]{sum / close.size(), close.size()};
    }

    /** The "Light delay" slider value for a measured speaker delay: minus the lights' own path, in 10 ms steps. */
    static int suggestedDelay(double speakerMs) {
        int v = (int) Math.round((speakerMs - LIGHT_PATH_MS) / 10.0) * 10;
        return Math.max(0, Math.min(500, v));
    }

    // ================================================================ measurement (Android)

    static final class Result {
        double speakerMs;   // NaN = failed
        int heard;
        String error;
    }

    /** Blocking (about 6 s): plays the chirps and listens. Call from a background thread. */
    static Result measure(Context ctx, java.util.concurrent.atomic.AtomicBoolean cancel) {
        Result r = new Result();
        r.speakerMs = Double.NaN;
        float[] chirp = chirp();
        short[] seq = sequence(chirp, 0.7);
        int recCap = seq.length + SR;              // a second extra for start-up and the last echo
        short[] rec = new short[recCap];
        AudioRecord ar = null;
        AudioTrack at = null;
        try {
            int minRec = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioFormat inFmt = new AudioFormat.Builder().setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build();
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            boolean unprocessed = am != null && "true".equals(am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
            // no echo cancelling / noise suppression: they would eat the chirps
            ar = new AudioRecord.Builder()
                    .setAudioSource(unprocessed ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(inFmt).setBufferSizeInBytes(Math.max(minRec, SR / 5 * 2)).build();
            if (ar.getState() != AudioRecord.STATE_INITIALIZED) { r.error = "mic"; return r; }

            int minPlay = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            at = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(SR)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(Math.max(minPlay, SR / 10 * 2)).build();
            if (at.getState() != AudioTrack.STATE_INITIALIZED) { r.error = "speaker"; return r; }

            // ---- microphone on its own thread; sample i was heard at micOffsetNs + i / SR
            final AudioRecord mic = ar;
            final int[] recLen = {0};
            final long[] micOffset = {Long.MAX_VALUE};   // fallback when getTimestamp is not supported
            final long[] tsOffset = {Long.MIN_VALUE};
            Thread reader = new Thread(() -> {
                AudioTimestamp ts = new AudioTimestamp();
                while (recLen[0] < recCap && !cancel.get()) {
                    int n = mic.read(rec, recLen[0], Math.min(SR / 100, recCap - recLen[0]));
                    long now = System.nanoTime();
                    if (n <= 0) break;
                    recLen[0] += n;
                    micOffset[0] = Math.min(micOffset[0], now - framesToNs(recLen[0]));
                    if (mic.getTimestamp(ts, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS)
                        tsOffset[0] = ts.nanoTime - framesToNs(ts.framePosition);
                }
            }, "calib-mic");
            ar.startRecording();
            reader.start();
            Thread.sleep(300);                          // let the mic settle before the first chirp

            // ---- play, and note when the mixer takes the frames (that is when the lights see them)
            List<long[]> jumps = new ArrayList<>();      // {nanoTime, head}
            at.play();
            int written = 0;
            long lastHead = 0;
            long deadline = System.nanoTime() + framesToNs(seq.length) + 3_000_000_000L;
            while (!cancel.get() && System.nanoTime() < deadline) {
                if (written < seq.length) {
                    int n = at.write(seq, written, seq.length - written, AudioTrack.WRITE_NON_BLOCKING);
                    if (n < 0) { r.error = "speaker"; return r; }
                    written += n;
                }
                long head = at.getPlaybackHeadPosition() & 0xFFFFFFFFL;
                long now = System.nanoTime();
                if (head != lastHead) { jumps.add(new long[]{now, head}); lastHead = head; }
                if (head >= seq.length) break;
                java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
            }
            at.stop();
            reader.join(4000);
            ar.stop();
            if (cancel.get()) { r.error = "cancel"; return r; }
            if (jumps.size() < 10) { r.error = "speaker"; return r; }

            // frame f left the mixer at trackOffset + f / SR (median over the steady part)
            long[] offs = new long[jumps.size() - 2];
            for (int i = 2; i < jumps.size(); i++) offs[i - 2] = jumps.get(i)[0] - framesToNs(jumps.get(i)[1]);
            java.util.Arrays.sort(offs);
            long trackOffset = offs[offs.length / 2];
            long micOff = tsOffset[0] != Long.MIN_VALUE ? tsOffset[0] : micOffset[0];

            double[] delays = new double[BEEPS];
            for (int k = 0; k < BEEPS; k++) {
                long sent = trackOffset + framesToNs(beepFrame(k));
                int center = (int) Math.round((sent - micOff) * (double) SR / 1e9);
                double[] hit = findChirp(rec, recLen[0], center - SEARCH_BEFORE, center + SEARCH_AFTER, chirp);
                if (hit == null || hit[1] < MIN_CLARITY) { delays[k] = Double.NaN; continue; }
                long heard = micOff + framesToNs((long) hit[0]);
                delays[k] = (heard - sent) / 1e6;
            }
            double[] c = combine(delays);
            if (c == null) {
                int heard = 0;
                for (double d : delays) if (!Double.isNaN(d)) heard++;
                r.heard = heard;
                r.error = heard < MIN_GOOD ? "quiet" : "unstable";
                return r;
            }
            r.speakerMs = Math.max(0, c[0]);
            r.heard = (int) c[1];
            return r;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            r.error = "cancel";
            return r;
        } catch (Exception e) {
            r.error = "mic";
            return r;
        } finally {
            if (at != null) at.release();
            if (ar != null) ar.release();
        }
    }

    private static long framesToNs(long frames) {
        return frames * 1_000_000_000L / SR;
    }

    static boolean bluetoothOut(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = d.getType();
            if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLE_SPEAKER
                    || t == AudioDeviceInfo.TYPE_BLE_HEADSET || t == AudioDeviceInfo.TYPE_BLE_BROADCAST) return true;
        }
        return false;
    }

    // ================================================================ dialogs

    /** Asks for the microphone if needed; MainActivity calls {@link #onPermission} with the answer. */
    static void start(Activity a, IntConsumer apply) {
        if (a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingApply = apply;
            a.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        intro(a, apply);
    }

    private static IntConsumer pendingApply;

    /** @return true if the result was for the calibration. */
    static boolean onPermission(Activity a, int requestCode) {
        if (requestCode != REQ_MIC) return false;
        IntConsumer apply = pendingApply;
        pendingApply = null;
        if (a.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && apply != null)
            intro(a, apply);
        return true;
    }

    private static void intro(Activity a, IntConsumer apply) {
        if (CaptureService.running && Tuning.micMode) {
            new AppDialog(a).setTitle(L.t("Microfono occupato", "Microphone busy"))
                    .setMessage(L.t("LedSync sta già ascoltando col microfono. Ferma la sincronizzazione e riprova.",
                            "LedSync is already listening with the microphone. Stop syncing and try again."))
                    .setPositiveButton("OK", null).show();
            return;
        }
        String msg = L.t("1. Metti in pausa la musica.\n2. Tieni il telefono vicino alla cassa (1-2 metri) con il volume medio-alto.\n3. Premi Inizia: la cassa farà 6 bip e il microfono li ascolterà (circa 6 secondi, stai in silenzio).",
                "1. Pause the music.\n2. Keep the phone near the speaker (1-2 metres) with the volume medium-high.\n3. Tap Start: the speaker will beep 6 times and the microphone will listen (about 6 seconds, keep quiet).");
        if (!bluetoothOut(a)) msg += L.t("\n\nAttenzione: non vedo una cassa Bluetooth collegata. Senza Bluetooth di solito il ritardo giusto è 0.",
                "\n\nNote: no Bluetooth speaker seems connected. Without Bluetooth the right delay is usually 0.");
        new AppDialog(a).setTitle(L.t("Misura il ritardo della cassa", "Measure the speaker delay"))
                .setMessage(msg)
                .setNegativeButton(L.t("Annulla", "Cancel"), null)
                .setPositiveButton(L.t("Inizia", "Start"), (d, w) -> run(a, apply))
                .show();
    }

    private static void run(Activity a, IntConsumer apply) {
        java.util.concurrent.atomic.AtomicBoolean cancel = new java.util.concurrent.atomic.AtomicBoolean();
        AppDialog wait = new AppDialog(a).setTitle(L.t("In ascolto…", "Listening…"))
                .setMessage(L.t("Bip in corso, stai in silenzio.", "Beeping, please keep quiet."))
                .setNegativeButton(L.t("Annulla", "Cancel"), (d, w) -> cancel.set(true))
                .show();
        Handler ui = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            Result r = measure(a.getApplicationContext(), cancel);
            ui.post(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                wait.dismiss();
                if (!"cancel".equals(r.error)) result(a, apply, r);
            });
        }, "calib").start();
    }

    private static void result(Activity a, IntConsumer apply, Result r) {
        if (Double.isNaN(r.speakerMs)) {
            String why;
            if ("quiet".equals(r.error)) why = L.t("Non ho sentito abbastanza bip (" + r.heard + " su " + BEEPS + "). Alza il volume, avvicina il telefono alla cassa e controlla che la musica sia in pausa.",
                    "I did not hear enough beeps (" + r.heard + " of " + BEEPS + "). Turn the volume up, move the phone closer to the speaker and make sure the music is paused.");
            else if ("unstable".equals(r.error)) why = L.t("I bip sono arrivati con tempi troppo diversi tra loro. Riprova in una stanza più silenziosa.",
                    "The beeps arrived with timings too different from each other. Try again in a quieter room.");
            else why = L.t("Non riesco a usare il microfono o la cassa in questo momento. Chiudi le app che li usano e riprova.",
                    "I can't use the microphone or the speaker right now. Close apps that use them and try again.");
            new AppDialog(a).setTitle(L.t("Misura non riuscita", "Measurement failed")).setMessage(why)
                    .setNegativeButton(L.t("Chiudi", "Close"), null)
                    .setPositiveButton(L.t("Riprova", "Try again"), (d, w) -> run(a, apply))
                    .show();
            return;
        }
        int ms = (int) Math.round(r.speakerMs);
        int value = suggestedDelay(r.speakerMs);
        new AppDialog(a).setTitle(L.t("Ritardo misurato", "Delay measured"))
                .setMessage(L.t("Il suono arriva " + ms + " ms dopo (" + r.heard + " bip su " + BEEPS + ").\nRitardo luci consigliato: " + value + " ms.\n\nSe serve puoi ritoccarlo a mano con lo slider.",
                        "The sound arrives " + ms + " ms later (" + r.heard + " of " + BEEPS + " beeps).\nSuggested light delay: " + value + " ms.\n\nYou can still fine-tune it with the slider."))
                .setNegativeButton(L.t("Annulla", "Cancel"), null)
                .setPositiveButton(L.t("Usa " + value + " ms", "Use " + value + " ms"), (d, w) -> apply.accept(value))
                .show();
    }
}
