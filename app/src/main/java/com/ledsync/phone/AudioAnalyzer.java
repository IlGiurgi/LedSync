// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

/**
 * Turns blocks of mono PCM audio into WLED "UDP Sound Sync" v2 packets (44 bytes).
 * Pure Java, no Android dependencies, so it can be tested on a desktop JVM.
 *
 * Packet layout (little endian), as expected by WLED AudioReactive in "Receive" mode:
 *   char[6]  header "00002\0"
 *   uint8[2] pressure (8.8 fixed point, ignored by older WLED)
 *   float    sampleRaw      0..255
 *   float    sampleSmth     0..255
 *   uint8    samplePeak     1 = beat
 *   uint8    frameCounter
 *   uint8[16] fftResult     GEQ channels 0..255
 *   uint16   zeroCrossingCount
 *   float    FFT_Magnitude
 *   float    FFT_MajorPeak  Hz
 */
public final class AudioAnalyzer {
    public static final int PACKET_SIZE = 44;
    public static final int FFT_SIZE = 2048;

    // Same 16 GEQ channel edges (Hz) that WLED uses for its own microphone analysis.
    private static final double[] BAND_EDGES = {
            43, 86, 129, 216, 301, 430, 560, 818, 1120, 1421, 1895, 2412, 3015, 3704, 4479, 7106, 9259
    };
    private static final double SILENCE_DB = -70.0;   // below this the output is forced to zero
    private static final double MEAN_ALPHA = 0.03;    // ~0.7 s time constant at 50 fps

    private final int sampleRate;
    private final double[] ring = new double[FFT_SIZE];
    private int ringPos = 0;
    private final double[] window = new double[FFT_SIZE];
    private final double[] re = new double[FFT_SIZE];
    private final double[] im = new double[FFT_SIZE];
    private final int[] bandLo = new int[16];
    private final int[] bandHi = new int[16];

    private final double[] bands = new double[16]; // smoothed 0..255
    // Running average level (dB) of each band and of the volume: the output shows how far the
    // current sound is above/below its recent average, which is what makes lights pulse with the beat.
    private final double[] bandMean = new double[16];
    private double volMean;
    private boolean primed = false;
    private double volSmooth = 0;
    private double bassAvg = 0;
    private long framesSincePeak = 1000;
    private int frameCounter = 0;

    // Last results, readable by the UI.
    public final int[] lastBands = new int[16];
    public volatile float lastVolume;
    public volatile boolean lastPeak;
    public volatile float lastMajorPeakHz;
    /** Raw (unsmoothed) band levels in dB, volume in dBFS and silence flag of the last frame. */
    public final double[] lastBandDb = new double[16];
    public volatile double lastVolDb = -120;
    public volatile boolean lastSilent = true;
    /** FFT power spectrum (|X|^2) of the last frame, FFT_SIZE/2 bins. */
    public final double[] lastPower = new double[FFT_SIZE / 2];
    public final double binHz;
    /** Tempo and beat clock, updated every frame. */
    final BeatTracker beats = new BeatTracker();

    public AudioAnalyzer(int sampleRate) {
        this.sampleRate = sampleRate;
        this.binHz = (double) sampleRate / FFT_SIZE;
        for (int i = 0; i < FFT_SIZE; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1));
        }
        double binHz = (double) sampleRate / FFT_SIZE;
        for (int b = 0; b < 16; b++) {
            bandLo[b] = Math.max(1, (int) Math.round(BAND_EDGES[b] / binHz));
            bandHi[b] = Math.max(bandLo[b] + 1, (int) Math.round(BAND_EDGES[b + 1] / binHz));
        }
    }

    /**
     * Feeds one block of samples (typically 20 ms) and writes a complete packet into {@code out}.
     */
    public void process(short[] samples, int count, byte[] out) {
        // --- time domain: RMS and zero crossings of this block
        double sumSq = 0;
        int zeroCross = 0;
        short prev = 0;
        for (int i = 0; i < count; i++) {
            short s = samples[i];
            sumSq += (double) s * s;
            if (i > 0 && ((prev < 0) != (s < 0))) zeroCross++;
            prev = s;
            ring[ringPos] = s / 32768.0;
            ringPos = (ringPos + 1) % FFT_SIZE;
        }
        double rms = count > 0 ? Math.sqrt(sumSq / count) / 32768.0 : 0;
        double volDb = 20 * Math.log10(rms + 1e-9);
        boolean silent = volDb < SILENCE_DB;

        // --- frequency domain
        for (int i = 0; i < FFT_SIZE; i++) {
            re[i] = ring[(ringPos + i) % FFT_SIZE] * window[i];
            im[i] = 0;
        }
        fft(re, im);

        double range = Math.max(10, Tuning.rangeDb);
        double gate = Math.max(0, Math.min(200, Tuning.gate));
        double decay = Math.max(0.3, Math.min(0.97, Tuning.decay));
        double[] bandDb = new double[16];
        for (int b = 0; b < 16; b++) {
            double e = 0;
            for (int k = bandLo[b]; k < bandHi[b] && k < FFT_SIZE / 2; k++) {
                e += re[k] * re[k] + im[k] * im[k];
            }
            // normalise so a full-scale sine is ~0 dB
            bandDb[b] = 10 * Math.log10(e * 4.0 / ((double) FFT_SIZE * FFT_SIZE / 4.0) + 1e-14);
        }

        System.arraycopy(bandDb, 0, lastBandDb, 0, 16);
        lastVolDb = volDb;
        lastSilent = silent;

        for (int k = 0; k < FFT_SIZE / 2; k++) lastPower[k] = re[k] * re[k] + im[k] * im[k];
        beats.process(lastPower, binHz, silent);

        // major peak between 60 Hz and 10 kHz
        int kMin = (int) (60 / binHz), kMax = Math.min(FFT_SIZE / 2 - 1, (int) (10000 / binHz));
        int peakK = kMin;
        double peakMag = 0;
        for (int k = kMin; k <= kMax; k++) {
            double m = re[k] * re[k] + im[k] * im[k];
            if (m > peakMag) { peakMag = m; peakK = k; }
        }

        // --- adaptive levels: compare each band with its own recent average
        if (!silent) {
            if (!primed) {
                System.arraycopy(bandDb, 0, bandMean, 0, 16);
                volMean = volDb;
                primed = true;
            }
            for (int b = 0; b < 16; b++) bandMean[b] += (bandDb[b] - bandMean[b]) * MEAN_ALPHA;
            volMean += (volDb - volMean) * MEAN_ALPHA;
        }
        double loudestMean = -200;
        for (int b = 0; b < 16; b++) loudestMean = Math.max(loudestMean, bandMean[b]);

        for (int b = 0; b < 16; b++) {
            // average level -> middle of the scale; bands much quieter than the loudest one are pulled down
            double shape = 0.5 * (bandMean[b] - loudestMean);
            double gain = b <= 4 ? Tuning.bassDb : (b <= 10 ? Tuning.midDb : Tuning.trebleDb);
            double v = silent ? 0 : applyGate(clamp((bandDb[b] - bandMean[b] + range * 0.5 + shape + gain) / range * 255.0), gate);
            // instant rise, adjustable fall, like WLED's own GEQ
            bands[b] = v >= bands[b] ? v : bands[b] * decay + v * (1 - decay);
            lastBands[b] = (int) Math.round(bands[b]);
        }

        double volRange = range * 0.6; // volume swings less than single bands, so stretch it more
        double volRaw = silent ? 0 : applyGate(clamp((volDb - volMean + volRange * 0.5) / volRange * 255.0), gate);
        double volDecay = Math.min(0.97, decay + 0.05);
        volSmooth = volRaw > volSmooth ? volSmooth * 0.3 + volRaw * 0.7 : volSmooth * volDecay + volRaw * (1 - volDecay);

        // --- beat detection on the bass channels
        double bass = (bands[0] + bands[1] + bands[2]) / 3.0;
        boolean peak = false;
        framesSincePeak++;
        if (!silent && bass > 140 && bass > bassAvg * Math.max(1.05, Tuning.beatRatio) && framesSincePeak > 6) {
            peak = true;
            framesSincePeak = 0;
        }
        bassAvg = bassAvg * 0.95 + bass * 0.05;

        float majorPeakHz = silent ? 0 : (float) (peakK * binHz);
        lastVolume = (float) volSmooth;
        lastPeak = peak;
        lastMajorPeakHz = majorPeakHz;

        // --- build packet
        int p = 0;
        out[p++] = '0'; out[p++] = '0'; out[p++] = '0'; out[p++] = '0'; out[p++] = '2'; out[p++] = 0;
        int pressure = (int) Math.round(volSmooth * 256.0); // 8.8 fixed point
        out[p++] = (byte) (pressure >> 8);
        out[p++] = (byte) pressure;
        p = putFloat(out, p, (float) volRaw);
        p = putFloat(out, p, (float) volSmooth);
        out[p++] = (byte) (peak ? 1 : 0);
        out[p++] = (byte) (frameCounter++ & 0xFF);
        for (int b = 0; b < 16; b++) out[p++] = (byte) lastBands[b];
        out[p++] = (byte) (zeroCross & 0xFF);
        out[p++] = (byte) ((zeroCross >> 8) & 0xFF);
        p = putFloat(out, p, (float) (volSmooth * 16.0)); // magnitude, roughly WLED's scale
        putFloat(out, p, majorPeakHz);
    }

    /** Cuts everything below the gate and stretches the rest back to 0..255. */
    private static double applyGate(double v, double gate) {
        if (gate <= 0) return v;
        return v <= gate ? 0 : (v - gate) / (255.0 - gate) * 255.0;
    }

    private static double clamp(double v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int putFloat(byte[] b, int p, float f) {
        int bits = Float.floatToIntBits(f);
        b[p] = (byte) bits;
        b[p + 1] = (byte) (bits >> 8);
        b[p + 2] = (byte) (bits >> 16);
        b[p + 3] = (byte) (bits >> 24);
        return p + 4;
    }

    /** In-place iterative radix-2 FFT. */
    private static void fft(double[] re, double[] im) {
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
            double ang = -2 * Math.PI / len;
            double wRe = Math.cos(ang), wIm = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cRe = 1, cIm = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    double tRe = re[b] * cRe - im[b] * cIm;
                    double tIm = re[b] * cIm + im[b] * cRe;
                    re[b] = re[a] - tRe; im[b] = im[a] - tIm;
                    re[a] += tRe; im[a] += tIm;
                    double nRe = cRe * wRe - cIm * wIm;
                    cIm = cRe * wIm + cIm * wRe;
                    cRe = nRe;
                }
            }
        }
    }
}
