// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;

/**
 * Foreground service: captures the audio other apps are playing (AudioPlaybackCapture, Android 10+),
 * analyses it and streams WLED UDP Sound Sync packets on port 11988.
 */
public class CaptureService extends Service {
    public static final String ACTION_START = "com.ledsync.phone.START";
    public static final String ACTION_STOP = "com.ledsync.phone.STOP";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_TARGETS = "targets";
    public static final String EXTRA_MULTICAST = "multicast";
    public static final String EXTRA_MIC = "mic";
    public static final String ACTION_NEXT = "com.ledsync.phone.NEXT";

    public static final int SYNC_PORT = 11988;
    public static final int REALTIME_PORT = 21324;
    private static final int DNRGB_MAX_LEDS = 489;
    private static final int SAMPLE_RATE = 48000;
    private static final int HOP = SAMPLE_RATE / 50; // 20 ms -> 50 packets/s
    private static final String CHANNEL_ID = "sync";

    // Shared with the UI (polled).
    public static volatile boolean running = false;
    public static volatile String status = "";

    // ---- timing stats for "Diagnostica ritardi", updated every second while running
    /** Frames per second actually produced (should be 50). */
    public static volatile double statFps;
    /** Longest pause between two frames in the last second, ms (should be ~20). */
    public static volatile double statMaxGapMs;
    /** Average time to analyse, render and send one frame, ms. */
    public static volatile double statProcMs;
    /** Audio buffer of the capture, ms: the most audio that can pile up if the app falls behind. */
    public static volatile double statBufferMs;
    /** Network send errors since start. */
    public static volatile long statSendErrors;
    /** Frames where the audio was already waiting (the app was behind), in the last second. */
    public static volatile int statLateFrames;
    public static volatile AudioAnalyzer analyzer;

    private MediaProjection projection;
    private Thread worker;
    private volatile boolean stop;
    /** Each start gets a new number, so a worker from a previous run can never keep sending or stop the new one. */
    private volatile int generation;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            return START_NOT_STICKY;
        }
        if (ACTION_NEXT.equals(intent.getAction())) {
            nextEffect(this);
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;

        boolean mic = intent.getBooleanExtra(EXTRA_MIC, false);
        startInForeground(mic);

        int code = intent.getIntExtra(EXTRA_CODE, 0);
        Intent data = intent.getParcelableExtra(EXTRA_DATA);
        String targets = intent.getStringExtra(EXTRA_TARGETS);
        boolean multicast = intent.getBooleanExtra(EXTRA_MULTICAST, true);

        if (mic) {
            projection = null;
            stop = false;
            running = true;
            status = L.t("Avvio (microfono)…", "Starting (microphone)…");
            int gen = ++generation;
            worker = new Thread(() -> runLoop(targets, multicast, gen), "wled-sync");
            worker.start();
            return START_NOT_STICKY;
        }

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        try {
            projection = mpm.getMediaProjection(code, data);
        } catch (Exception e) {
            projection = null;
        }
        if (projection == null) {
            status = L.t("Permesso di cattura negato", "Permission to capture denied");
            shutdown();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                shutdown();
            }
        }, new Handler(Looper.getMainLooper()));

        stop = false;
        running = true;
        status = L.t("Avvio…", "Starting…");
        int gen = ++generation;
        worker = new Thread(() -> runLoop(targets, multicast, gen), "wled-sync");
        worker.start();
        return START_NOT_STICKY;
    }

    /** Next effect of the "party" list (notification button and automatic change); remembered for the app. */
    static void nextEffect(Context ctx) {
        int[] list = DirectEffects.PARTY;
        int at = -1;
        for (int i = 0; i < list.length; i++) if (list[i] == Tuning.directEffect) at = i;
        Tuning.directEffect = list[(at + 1) % list.length];
        ctx.getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("d_effect", Tuning.directEffect).apply();
    }

    private void startInForeground(boolean mic) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, L.t("Sincronizzazione WLED", "Light sync"),
                NotificationManager.IMPORTANCE_LOW));

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPi = PendingIntent.getService(this, 1,
                new Intent(this, CaptureService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("LedSync attivo")
                .setContentText(L.t("L'audio del telefono pilota i WLED", "The phone's audio drives your lights"))
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, L.t("Effetto successivo", "Next effect"), PendingIntent.getService(this, 2,
                        new Intent(this, CaptureService.class).setAction(ACTION_NEXT), PendingIntent.FLAG_IMMUTABLE)).build())
                .addAction(new Notification.Action.Builder(null, L.t("Ferma", "Stop"), stopPi).build())
                .build();

        int type = mic ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE : ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
        if (!mic && Build.VERSION.SDK_INT >= 30) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        startForeground(1, n, type);
    }

    private boolean stopped(int gen) {
        return stop || gen != generation;
    }

    private void runLoop(String targets, boolean multicast, int gen) {
        AudioRecord rec = null;
        List<ColorDevice> yeelights = new ArrayList<>();
        DatagramSocket socket = null;
        silentSinceMs = 0;
        stoppedBySilence = false;
        try {
            AudioPlaybackCaptureConfiguration cfg = projection == null ? null
                    : new AudioPlaybackCaptureConfiguration.Builder(projection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();
            int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord.Builder rb = new AudioRecord.Builder();
            // microphone: UNPROCESSED skips noise suppression / auto gain that flatten the music
            if (cfg != null) rb.setAudioPlaybackCaptureConfig(cfg);
            else rb.setAudioSource(android.media.MediaRecorder.AudioSource.UNPROCESSED);
            rec = rb
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(Math.max(minBuf, HOP * 2 * 4))
                    .build();
            if (cfg == null && rec.getState() != AudioRecord.STATE_INITIALIZED) {
                rec.release();
                rec = new AudioRecord.Builder()
                        .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(fmt)
                        .setBufferSizeInBytes(Math.max(minBuf, HOP * 2 * 4))
                        .build();
            }
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException(L.t("AudioRecord non inizializzato", "AudioRecord could not start"));
            }

            // Targets: "192.168.1.50" = WLED, "y:192.168.1.60" = Yeelight.
            List<Device> devices = new ArrayList<>();

            if (targets != null) {
                // LED counts are read in parallel, so a light that is off delays the start by 3 s at most
                java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newCachedThreadPool();
                List<String> keys = new ArrayList<>();
                List<java.util.concurrent.Future<Device>> pending = new ArrayList<>();
                for (String raw : targets.split("[,;\\s]+")) {
                    String t = raw.trim();
                    if (t.isEmpty()) continue;
                    if (ColorLight.isColorKey(t)) {
                        yeelights.add(new ColorDevice(t, ColorLight.forKey(t)));
                        continue;
                    }
                    keys.add(t);
                    pending.add(pool.submit(() -> {
                        InetAddress addr = InetAddress.getByName(t);
                        // every WLED gets its own renderer sized to its own LED count
                        Integer manual = Tuning.ledCount.get(t);
                        int fetched = fetchLedCount(addr);   // 0 = no WLED JSON API (e.g. word clock, ESPHome)
                        Device dv = new Device(t, addr, manual != null ? manual : fetched > 0 ? fetched : 300);
                        dv.isWled = fetched > 0;
                        return dv;
                    }));
                }
                for (int i = 0; i < pending.size(); i++) {
                    try {
                        devices.add(pending.get(i).get());
                    } catch (Exception e) {
                        status = L.t("Indirizzo non valido: ", "Invalid address: ") + keys.get(i);   // skip it, keep the others
                    }
                }
                pool.shutdown();
            }
            InetAddress multicastAddr = (devices.isEmpty() && multicast) ? InetAddress.getByName("239.0.0.1") : null;
            if (devices.isEmpty() && yeelights.isEmpty() && multicastAddr == null) {
                throw new IllegalStateException(L.t("Nessun dispositivo impostato", "No device set up"));
            }

            MulticastSocket ms = new MulticastSocket();
            ms.setTimeToLive(2);
            NetworkInterface wlan = NetworkInterface.getByName("wlan0");
            if (wlan != null && wlan.isUp()) {
                try { ms.setNetworkInterface(wlan); } catch (Exception ignored) { }
            }
            socket = ms;

            // Yeelights connect on their own threads (and retry), so the music starts right away
            for (ColorDevice y : yeelights) {
                if (!Tuning.disabled.contains(y.key)) y.link.openAsync();
            }

            AudioAnalyzer an = new AudioAnalyzer(SAMPLE_RATE);
            analyzer = an;
            short[] buf = new short[HOP];
            byte[] packet = new byte[AudioAnalyzer.PACKET_SIZE];
            rec.startRecording();

            int errors = 0;
            long lastErrorMs = 0;
            java.util.ArrayDeque<Object[]> queue = new java.util.ArrayDeque<>();
            statSendErrors = 0;
            try { statBufferMs = rec.getBufferSizeInFrames() * 1000.0 / SAMPLE_RATE; } catch (Exception ignored) { }
            long winStart = System.nanoTime(), lastFrame = 0, procSum = 0;
            int winFrames = 0, late = 0;
            double maxGap = 0;
            long frameNo = 0;
            while (!stopped(gen)) {
                frameNo++;
                int filled = 0;
                long readStart = System.nanoTime();
                while (filled < HOP && !stopped(gen)) {
                    int r = rec.read(buf, filled, HOP - filled);
                    if (r < 0) throw new IllegalStateException(L.t("Errore lettura audio ", "Audio read error ") + r);
                    filled += r;
                }
                long frameStart = System.nanoTime();
                // a read that returns at once means audio was already waiting: the app is behind
                if (frameStart - readStart < 2_000_000L) late++;
                if (lastFrame != 0) maxGap = Math.max(maxGap, (frameStart - lastFrame) / 1e6);
                lastFrame = frameStart;
                an.process(buf, filled, packet);
                // automatic stop after a long silence (saves battery when the music has ended)
                if (an.lastSilent) {
                    if (silentSinceMs == 0) silentSinceMs = System.currentTimeMillis();
                    else if (Tuning.autoOffMin > 0 && System.currentTimeMillis() - silentSinceMs > Tuning.autoOffMin * 60_000L) {
                        stoppedBySilence = true;
                        break;
                    }
                } else silentSinceMs = 0;
                // automatic effect change: when the time is up, wait for a beat so the change lands on the music
                if (Tuning.autoCycleSec > 0 && Tuning.directMode) {
                    long nowMs = System.currentTimeMillis();
                    if (nextCycleMs == 0) nextCycleMs = nowMs + Tuning.autoCycleSec * 1000L;
                    if (nowMs >= nextCycleMs && (an.lastPeak || nowMs >= nextCycleMs + 4000)) {
                        nextEffect(this);
                        nextCycleMs = nowMs + Tuning.autoCycleSec * 1000L;
                    }
                } else nextCycleMs = 0;
                // frame items: {address, port, packet} for UDP, {YeelightLink, int[] rgb or null=off}
                List<Object[]> frame = new ArrayList<>();
                int active = 0;
                {
                    for (Device dv : devices) {
                        if (Tuning.disabled.contains(dv.key)) continue;
                        if (!Tuning.directMode && dv.isWled) continue;   // handled by sound sync below
                        active++;
                        dv.fx.update(an);
                        Integer gridW = Tuning.matrixWidth.get(dv.key);
                        if (gridW != null && gridW > 0 && gridW <= dv.leds)
                            dv.fx.renderMatrix(Tuning.effectFor(dv.key), dv.leds, gridW, Tuning.hue, dv.pixels);
                        else dv.fx.render(Tuning.effectFor(dv.key), dv.leds, Tuning.hue, dv.pixels);
                        finishFrame(dv.pixels, dv.leds, Tuning.flipped.contains(dv.key), dv.key);
                        // save battery and Wi-Fi: skip frames that repeat the last one (still refresh every
                        // 500 ms so the light stays in realtime mode) and, in battery saver, every other frame
                        long nowNs = System.nanoTime();
                        boolean same = dv.lastSent != null && java.util.Arrays.equals(dv.lastSent, dv.pixels);
                        boolean keepAlive = nowNs - dv.lastSentNs >= 500_000_000L;
                        if ((same || (Tuning.batterySaver && (frameNo & 1) == 1)) && !keepAlive) continue;
                        if (dv.lastSent == null) dv.lastSent = new byte[dv.pixels.length];
                        System.arraycopy(dv.pixels, 0, dv.lastSent, 0, dv.pixels.length);
                        dv.lastSentNs = nowNs;
                        for (byte[] pk : dnrgbPackets(dv.pixels, dv.leds)) frame.add(new Object[]{dv.addr, REALTIME_PORT, pk});
                    }
                }
                if (!Tuning.directMode) {
                    byte[] pk = packet.clone();
                    for (Device dv : devices) {
                        if (Tuning.disabled.contains(dv.key) || !dv.isWled) continue;
                        active++;
                        frame.add(new Object[]{dv.addr, SYNC_PORT, pk});
                    }
                    if (multicastAddr != null) { frame.add(new Object[]{multicastAddr, SYNC_PORT, pk}); active++; }
                }
                String yeeNote = null;
                for (ColorDevice y : yeelights) {
                    if (Tuning.disabled.contains(y.key)) {
                        if (y.link.isOpen()) frame.add(new Object[]{y.link, null});
                        continue;
                    }
                    if (!y.link.isOpen()) {
                        y.link.openAsync();
                        yeeNote = y.link.error() != null ? y.link.error() : L.t("Collegamento a ", "Connecting to ") + ColorLight.hostOf(y.key) + "…";
                        continue;
                    }
                    if (y.link.error() != null) yeeNote = y.link.error();
                    else if (y.link.note() != null) yeeNote = y.link.note();
                    active++;
                    y.fx.update(an);
                    y.fx.render(Tuning.effectFor(y.key), ColorDevice.PIXELS, Tuning.hue, y.pixels);
                    finishFrame(y.pixels, ColorDevice.PIXELS, false, y.key);
                    if (Tuning.batterySaver && (frameNo & 1) == 1) continue;
                    frame.add(new Object[]{y.link, averageColor(y.pixels, ColorDevice.PIXELS)});
                }
                if (frameNo % 10 == 0) {
                String info = active == 0 ? L.t("Tutti i dispositivi sono spenti", "All devices are off")
                        : (Tuning.directMode ? L.t("Effetto attivo su ", "Effect running on ") : L.t("Sound sync verso ", "Sound sync to ")) + active
                        + (active == 1 ? L.t(" dispositivo", " device") : L.t(" dispositivi", " devices"));
                if (an.beats.confident()) info += String.format(java.util.Locale.ROOT, " · %.0f BPM", an.beats.bpm);
                if (yeeNote != null) info += "\n" + yeeNote;
                if (System.currentTimeMillis() - lastErrorMs > 3000) status = info;
                }
                // optional delay to line up with Bluetooth speakers: hold frames back, send when due
                long now = System.nanoTime();
                queue.addLast(new Object[]{now, frame});
                long delayNs = Math.max(0, Tuning.delayMs) * 1_000_000L;
                while (!queue.isEmpty() && now - (long) queue.peekFirst()[0] >= delayNs) {
                    @SuppressWarnings("unchecked") List<Object[]> due = (List<Object[]>) queue.pollFirst()[1];
                    for (Object[] item : due) {
                        if (item[0] instanceof ColorLight) {
                            ColorLight link = (ColorLight) item[0];
                            int[] c = (int[]) item[1];
                            if (c == null) {
                                link.setOff(true);
                            } else {
                                link.setOff(false);
                                link.show(c[0], c[1], c[2]);
                            }
                            continue;
                        }
                        byte[] pk = (byte[]) item[2];
                        try {
                            socket.send(new DatagramPacket(pk, pk.length, (InetAddress) item[0], (int) item[1]));
                        } catch (Exception e) {
                            statSendErrors++;
                            if (++errors % 250 == 1) {
                                status = L.t("Errore di rete: ", "Network error: ") + e.getMessage();
                                lastErrorMs = System.currentTimeMillis();
                            }
                        }
                    }
                }
                long frameEnd = System.nanoTime();
                procSum += frameEnd - frameStart;
                winFrames++;
                if (frameEnd - winStart >= 1_000_000_000L) {
                    double secs = (frameEnd - winStart) / 1e9;
                    statFps = winFrames / secs;
                    statProcMs = procSum / 1e6 / winFrames;
                    statMaxGapMs = maxGap;
                    statLateFrames = late;
                    winStart = frameEnd;
                    winFrames = 0;
                    procSum = 0;
                    maxGap = 0;
                    late = 0;
                }
            }
            if (stoppedBySilence) status = L.t("Fermato dopo ", "Stopped after ") + Tuning.autoOffMin + L.t(" minuti di silenzio", " minutes of silence");
        } catch (Exception e) {
            status = L.t("Errore: ", "Error: ") + e.getMessage();
        } finally {
            if (rec != null) {
                try { rec.stop(); } catch (Exception ignored) { }
                rec.release();
            }
            if (socket != null) socket.close();
            for (ColorDevice y : yeelights) y.link.dispose();
            new Handler(Looper.getMainLooper()).post(() -> { if (gen == generation) shutdown(); });
        }
    }

    private long nextCycleMs, silentSinceMs;
    private boolean stoppedBySilence;

    /** A one-colour light (Yeelight, Govee, Hue): the effect is rendered on a few virtual pixels and averaged. */
    private static final class ColorDevice {
        static final int PIXELS = 30;
        final String key;
        final ColorLight link;
        final DirectEffects fx = new DirectEffects();
        final byte[] pixels = new byte[PIXELS * 3];

        ColorDevice(String key, ColorLight link) {
            this.key = key;
            this.link = link;
        }
    }

    /** Applies the app brightness, the device's own brightness and its direction to a rendered frame. */
    static void finishFrame(byte[] px, int leds, boolean flip, String key) {
        Integer own = key == null ? null : Tuning.deviceBrightness.get(key);
        double bri = Math.max(0, Math.min(1, Tuning.brightness * (own == null ? 1 : own / 100.0)));
        if (bri < 0.999) {
            for (int i = 0; i < leds * 3; i++) px[i] = (byte) Math.round((px[i] & 255) * bri);
        }
        if (flip) {
            for (int i = 0, j = leds - 1; i < j; i++, j--) {
                for (int c = 0; c < 3; c++) {
                    byte t = px[i * 3 + c];
                    px[i * 3 + c] = px[j * 3 + c];
                    px[j * 3 + c] = t;
                }
            }
        }
    }

    /** Average colour of a frame, boosted a little because effects rarely light every pixel. */
    static int[] averageColor(byte[] px, int leds) {
        long r = 0, g = 0, b = 0;
        for (int i = 0; i < leds; i++) {
            r += px[i * 3] & 255;
            g += px[i * 3 + 1] & 255;
            b += px[i * 3 + 2] & 255;
        }
        return new int[]{(int) Math.min(255, r * 1.6 / leds), (int) Math.min(255, g * 1.6 / leds), (int) Math.min(255, b * 1.6 / leds)};
    }

    /** One WLED: its own LED count, renderer and pixel buffer. */
    private static final class Device {
        final String key;
        final InetAddress addr;
        final int leds;
        final DirectEffects fx = new DirectEffects();
        final byte[] pixels;
        /** false for lights without WLED's JSON/sync support: they always get the app's effects. */
        boolean isWled = true;
        /** Last frame actually sent, to skip repeats (e.g. dark during silence). */
        byte[] lastSent;
        long lastSentNs;

        Device(String key, InetAddress addr, int leds) {
            this.key = key;
            this.addr = addr;
            this.leds = leds;
            this.pixels = new byte[leds * 3];
        }
    }

    /** Reads the LED count from WLED's JSON API; falls back to 300. */
    private static int fetchLedCount(InetAddress host) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL("http://" + host.getHostAddress() + "/json/info").openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(4000);
            try (java.io.InputStream in = c.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                org.json.JSONObject info = new org.json.JSONObject(out.toString("UTF-8"));
                int count = info.getJSONObject("leds").getInt("count");
                return Math.max(count, 0);
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * WLED UDP realtime "DNRGB" (protocol 4): [4, timeout s, start hi, start lo, R,G,B...], at most
     * 489 LEDs per packet. After the timeout without packets WLED goes back to its normal effect.
     */
    static List<byte[]> dnrgbPackets(byte[] rgb, int ledCount) {
        List<byte[]> out = new ArrayList<>();
        for (int start = 0; start < ledCount; start += DNRGB_MAX_LEDS) {
            int n = Math.min(DNRGB_MAX_LEDS, ledCount - start);
            byte[] p = new byte[4 + n * 3];
            p[0] = 4;
            p[1] = 2;
            p[2] = (byte) (start >> 8);
            p[3] = (byte) start;
            System.arraycopy(rgb, start * 3, p, 4, n * 3);
            out.add(p);
        }
        return out;
    }

    private void shutdown() {
        stop = true;
        running = false;
        if (projection != null) {
            MediaProjection p = projection;
            projection = null;
            p.stop();
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stop = true;
        running = false;
        super.onDestroy();
    }
}
