// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import java.util.Random;

/**
 * Effects computed on the phone and streamed to WLED as raw pixel colours.
 * Several are re-implementations of LedFx's classic effects (Energy, Scroll, Wavelength,
 * Bands, Power, Rain, Magnitude, Melt, Pitch Spectrum), following the same ideas: a
 * normalised log-frequency "melbank", lows/mids/highs levels, gradients, mirror and blur.
 * Pure Java, testable on a desktop JVM.
 *
 * The LedFx-based effects are adapted from LedFx (https://github.com/LedFx/LedFx),
 * Copyright (C) the LedFx developers, licensed under the GNU GPL v3. See NOTICE.md.
 */
public final class DirectEffects {
    public static final String[] NAMES = {
            L.t("Pulsazione bassi", "Bass pulse"),
            "Energy",
            "Scroll",
            "Wavelength",
            "Bands",
            "Power",
            "Rain",
            "Magnitude",
            L.t("Strobo sul battito", "Beat strobe"),
            "Melt",
            "Pitch Spectrum",
            "VU meter",
            L.t("Spettro (16 bande)", "Spectrum (16 bands)"),
            "Equalizer",
            "Blocks",
            "Blade Power",
            "Energy 2",
            L.t("Fuoco", "Fire"),
            "Lava lamp",
            "Marching",
            "Water",
            "Real Strobe",
            "Bar",
            "Scan",
            "Hierarchy",
            "Crawler",
            "BPM Flash",
            "BPM Chase",
            L.t("Fuochi d'artificio", "Fireworks"),
            L.t("Meteore", "Meteors"),
            L.t("Onde sul battito", "Beat ripples"),
            L.t("Cielo stellato", "Starry sky"),
            L.t("Respiro", "Breathe"),
            L.t("Battito cardiaco", "Heartbeat"),
            L.t("Caleidoscopio", "Kaleidoscope"),
            "BPM Sweep",
    };
    public static final String[] DESCRIPTIONS = {
            L.t("Tutta la luce batte con la cassa", "All lights hit with the kick"),
            L.t("Barre di bassi, medi e alti", "Bars for lows, mids and highs"),
            L.t("Colori che scorrono via", "Colours flowing away"),
            L.t("Lo spettro col gradiente", "The spectrum in your gradient"),
            L.t("Sei barre di frequenze", "Six frequency bars"),
            L.t("Gradiente, bassi e scintille", "Gradient, bass and sparks"),
            L.t("Gocce di colore sui colpi", "Colour drops on the hits"),
            L.t("Il gradiente pulsa coi bassi", "The gradient pulses with the bass"),
            L.t("Flash a tempo di musica", "Flashes in time with the music"),
            L.t("Onde liquide, rilassante", "Liquid waves, relaxing"),
            L.t("Il colore segue la nota", "The colour follows the note"),
            L.t("Livello del volume", "Volume level"),
            L.t("Sedici bande arcobaleno", "Sixteen rainbow bands"),
            L.t("Barre di frequenze col gradiente", "Frequency bars in your gradient"),
            L.t("Blocchi che si accendono", "Blocks lighting up"),
            L.t("Barra arcobaleno sui bassi", "Rainbow bar on the bass"),
            L.t("Onde morbide coi bassi", "Soft waves with the bass"),
            L.t("Fiamme che crescono coi bassi", "Flames growing with the bass"),
            L.t("Bolle di colore lente", "Slow colour bubbles"),
            L.t("Colori in marcia a tempo", "Colours marching in time"),
            L.t("Increspature d'acqua", "Water ripples"),
            L.t("Flash colorati e lampi bianchi", "Colour flashes and white sparks"),
            L.t("Barra che rimbalza sul battito", "A bar bouncing on the beat"),
            L.t("Luce che corre coi bassi", "Light running with the bass"),
            L.t("Colore della parte più forte", "Colour of the loudest part"),
            L.t("Colori che strisciano", "Crawling colours"),
            L.t("Flash esatti sul tempo", "Flashes locked to the tempo"),
            L.t("Luce che salta a ogni battito", "Light jumping on every beat"),
            L.t("Razzi che esplodono sui colpi", "Rockets bursting on the hits"),
            L.t("Comete che sfrecciano sui colpi", "Comets shooting on the hits"),
            L.t("Un'onda parte a ogni battito", "A wave starts on every beat"),
            L.t("Stelle che brillano con gli alti", "Stars twinkling with the highs"),
            L.t("Luce calma che segue il volume", "Calm light following the volume"),
            L.t("Doppio colpo, come un cuore", "Double pulse, like a heart"),
            L.t("Colori che girano e cambiano verso", "Colours spinning and turning around"),
            L.t("Luce che attraversa tutto a tempo", "Light sweeping across in time"),
    };

    /** Effects shown on the main screen; all the others are under "Altri effetti". */
    public static final int[] MAIN_EFFECTS = {0, 1, 5, 2, 17, 6};
    /** Groups for the "Altri effetti" screen. */
    public static final String[] CATEGORY_NAMES = {L.t("Bassi e ritmo", "Bass and rhythm"), L.t("Classici LedFx", "LedFx classics"), L.t("Frequenze", "Frequencies"), L.t("Atmosfera", "Ambience")};
    public static final int[][] CATEGORIES = {
            {0, 26, 27, 35, 33, 15, 8, 21, 22, 7, 24},
            {1, 2, 3, 5, 6, 10, 20},
            {4, 13, 14, 12, 11},
            {28, 29, 30, 34, 31, 32, 9, 16, 17, 18, 19, 25},
    };
    public static final int PULSE = 0, ENERGY = 1, SCROLL = 2, WAVELENGTH = 3, BANDS = 4, POWER = 5,
            RAIN = 6, MAGNITUDE = 7, STROBE = 8, MELT = 9, PITCH = 10, VU = 11, SPECTRUM = 12,
            EQUALIZER = 13, BLOCKS = 14, BLADE = 15, ENERGY2 = 16, FIRE = 17, LAVA = 18, MARCHING = 19,
            WATER = 20, REAL_STROBE = 21, BAR = 22, SCAN = 23, HIERARCHY = 24, CRAWLER = 25,
            BPM_FLASH = 26, BPM_CHASE = 27, FIREWORKS = 28, METEOR = 29, RIPPLE = 30, TWINKLE = 31,
            BREATHE = 32, HEARTBEAT = 33, KALEIDO = 34, BPM_SWEEP = 35;

    public static final String[] GRADIENT_NAMES = {
            L.t("RGB classico", "Classic RGB"), L.t("Arcobaleno", "Rainbow"), "Dancefloor", "Plasma", L.t("Oceano", "Ocean"), L.t("Tramonto", "Sunset"), L.t("Fuoco", "Fire"), L.t("Foresta", "Forest"), "Aurora",
    };
    static final int[][] GRADIENTS = {
            {0xFF0000, 0x00FF00, 0x0000FF},
            {0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF},
            {0xFF0000, 0xFF0080, 0x0000FF},
            {0x0000FF, 0x8000FF, 0xFF0080, 0xFF8000},
            {0x000080, 0x0060FF, 0x00FFFF},
            {0xFF4000, 0xFF0060, 0x8000FF},
            {0xFF0000, 0xFF6000, 0xFFD000},
            {0x00FF40, 0xA0FF00, 0xFFFF00},
            {0x00FF80, 0x0080FF, 0x8000FF},
    };

    private static final int MEL_BINS = 32;
    private static final double MEL_MIN_HZ = 40, MEL_MAX_HZ = 12000;
    private static final double FLOOR_DB = -65;     // quieter than this is silence
    private static final double REF_MIN_DB = -50;
    private static final double REF_FALL_DB = 0.04; // peak reference falls 2 dB/s at 50 fps

    // ---- audio features
    private double refBass = REF_MIN_DB, refMid = REF_MIN_DB, refHigh = REF_MIN_DB, refVol = REF_MIN_DB, refMel = REF_MIN_DB;
    private final double[] refBand = new double[16];
    private double bass, mid, high, vol;      // 0..1, smoothed
    private double prevHigh, prevMid;
    private final double[] spec = new double[16];
    private final double[] mel = new double[MEL_BINS];
    private final double[] melDb = new double[MEL_BINS];
    private final double[] refMelBin = new double[MEL_BINS];
    private int[] melLo, melHi;
    private double majorPeakHz;
    private boolean beat, onset, silent = true;
    private int beatCount;

    // ---- effect state
    private final Random rnd = new Random();
    private float[] work = new float[0], filt = new float[0], scroll = new float[0], sparks = new float[0],
            bassBar = new float[0], pitch = new float[0];
    private int[] dropAge = new int[0];
    private float[] dropColor = new float[0];
    private final double[] rainFilt = new double[3];
    private double rollOffset, strobe, meltTime, bassSlow;
    // tempo: beat clock from the analyser's BeatTracker, and the colour shift that steps on each beat
    private boolean bpmTick, bpmSure;
    private long bpmIndex;
    private double bpmPhase, colorShift, colorShiftTarget;
    private int beatCooldown;
    private long frame, lastBeatFrame;
    private double beatInterval = 25, barColor, animTime, scanPos = 0, bladeLen;
    private int barPhase, scanDir = 1;
    private float[] heat = new float[0], blade = new float[0], realBass = new float[0], realFlash = new float[0];
    private double[] waterA = new double[0], waterB = new double[0];
    private int[] hierColor = {0, 0, 0};
    private double hierPower;
    private long lastLowFrame = -1000, lastMidFrame = -1000;

    /** Smoothed 0..1 levels, for tests and UI. */
    public double bass() { return bass; }
    public double mid() { return mid; }
    public double high() { return high; }
    public double volume() { return vol; }

    public DirectEffects() {
        java.util.Arrays.fill(refBand, REF_MIN_DB);
        java.util.Arrays.fill(refMelBin, REF_MIN_DB);
    }

    // ================================================================== audio analysis

    /** Updates all levels from the analyzer's latest frame. Call once per audio frame. */
    public void update(AudioAnalyzer a) {
        double[] db = a.lastBandDb;
        silent = a.lastSilent;
        double bDb = sumDb(db, 0, 3), mDb = sumDb(db, 4, 9), hDb = sumDb(db, 10, 15);
        refBass = track(refBass, bDb);
        refMid = track(refMid, mDb);
        refHigh = track(refHigh, hDb);
        refVol = track(refVol, a.lastVolDb);

        prevHigh = high;
        prevMid = mid;
        // balance first, then the user's bass/mid/treble sliders, so the sliders still have an effect
        bass = smooth(bass, silent ? 0 : gain(balance(0, level(bDb, refBass, 0)), Tuning.bassDb));
        mid = smooth(mid, silent ? 0 : gain(balance(1, level(mDb, refMid, 0)), Tuning.midDb));
        high = smooth(high, silent ? 0 : gain(balance(2, level(hDb, refHigh, 0)), Tuning.trebleDb));
        vol = smooth(vol, silent ? 0 : level(a.lastVolDb, refVol, 0));
        for (int b = 0; b < 16; b++) {
            refBand[b] = track(refBand[b], db[b]);
            spec[b] = smooth(spec[b], silent ? 0 : level(db[b], refBand[b], gainFor(BAND_CENTER_HZ[b])));
        }

        // log-spaced "melbank", normalised against one shared peak so loud and quiet ranges keep
        // their balance; +2.5 dB/octave tilt because music has far more energy in the bass
        if (melLo == null) buildMel(a.binHz, a.lastPower.length);
        double maxDb = -200;
        for (int m = 0; m < MEL_BINS; m++) {
            double p = 0;
            for (int k = melLo[m]; k <= melHi[m]; k++) p += a.lastPower[k];
            double centre = MEL_MIN_HZ * Math.pow(MEL_MAX_HZ / MEL_MIN_HZ, (m + 0.5) / MEL_BINS);
            melDb[m] = 10 * Math.log10(p * 16.0 / ((double) AudioAnalyzer.FFT_SIZE * AudioAnalyzer.FFT_SIZE) + 1e-14)
                    + 2.5 * log2(centre / 100.0);
            maxDb = Math.max(maxDb, melDb[m]);
        }
        refMel = track(refMel, maxDb);
        for (int m = 0; m < MEL_BINS; m++) {
            double centre = MEL_MIN_HZ * Math.pow(MEL_MAX_HZ / MEL_MIN_HZ, (m + 0.5) / MEL_BINS);
            // each bin against its own recent peak, but never boosted more than 15 dB above the loudest
            refMelBin[m] = track(refMelBin[m], melDb[m]);
            double ref = Math.max(refMelBin[m], refMel - 15);
            mel[m] = smooth(mel[m], silent ? 0 : level(melDb[m], ref, gainFor(centre), 0.75));
        }

        majorPeakHz = a.lastMajorPeakHz;
        beat = a.lastPeak;
        if (beat) {
            beatCount++;
            long gap = frame - lastBeatFrame;
            if (gap >= 15 && gap <= 75) beatInterval += (gap - beatInterval) * 0.3; // 0.3-1.5 s at 50 fps
            lastBeatFrame = frame;
            barPhase = 1 - barPhase;
            if (barPhase == 0) barColor = (barColor + 0.15) % 1.0;
        }
        onset = beat || (high - prevHigh > 0.3) || (mid - prevMid > 0.35);

        BeatTracker bt = a.beats;
        bpmSure = bt.confident() && !silent;
        bpmTick = bpmSure && bt.tick;
        bpmIndex = bt.beatIndex;
        bpmPhase = bt.phase;
        // "colours on the beat": the gradient steps forward on every beat (the tempo clock when it is sure,
        // otherwise the bass hits) and glides there in ~100 ms
        if (beatCooldown > 0) beatCooldown--;
        boolean step = bpmSure ? bpmTick : (beat && beatCooldown == 0);
        if (Tuning.beatColors && step) {
            colorShiftTarget += 1.0 / 6;
            beatCooldown = 12;
        }
        colorShift += (colorShiftTarget - colorShift) * 0.35;
        if (colorShiftTarget > 1000) { colorShiftTarget -= 1000; colorShift -= 1000; }
    }

    /** Tempo-locked flash: the whole strip lights on every beat and fades over the beat; first beat of 4 brighter. */
    private void bpmFlash(int len) {
        double ph = bpmSure ? bpmPhase : Math.min(1, (frame - lastBeatFrame) / Math.max(10, beatInterval));
        long idx = bpmSure ? bpmIndex : beatCount;
        boolean down = idx % 4 == 0;
        double v = Math.pow(1 - ph, 1.6) * (down ? 1.0 : 0.75) * Math.min(1, presence() * 3);
        int[] c = gradient((idx % 8) / 8.0);
        float wr = down ? (float) (0.35 * (1 - ph)) : 0;   // a touch of white on the bar's first beat
        for (int i = 0; i < len; i++)
            set(work, i, (float) ((c[0] + (255 - c[0]) * wr) * v), (float) ((c[1] + (255 - c[1]) * wr) * v), (float) ((c[2] + (255 - c[2]) * wr) * v));
    }

    /** Tempo-locked chase: four segments, one per beat of the bar, the lit one fading out over the beat. */
    private void bpmChase(int len) {
        double ph = bpmSure ? bpmPhase : Math.min(1, (frame - lastBeatFrame) / Math.max(10, beatInterval));
        long idx = bpmSure ? bpmIndex : beatCount;
        int seg = (int) (idx % 4);
        int prev = (seg + 3) % 4;
        double pres = Math.min(1, presence() * 3);
        int[] c = gradient(((idx / 4) % 6) / 6.0);
        for (int i = 0; i < len; i++) {
            int sgm = Math.min(3, i * 4 / Math.max(1, len));
            double v = sgm == seg ? 1 - 0.6 * ph : (sgm == prev ? 0.4 * (1 - ph) : 0);
            v *= pres;
            set(work, i, (float) (c[0] * v), (float) (c[1] * v), (float) (c[2] * v));
        }
    }

    private static final double[] BAND_CENTER_HZ = {
            60, 105, 170, 255, 360, 490, 680, 950, 1260, 1640, 2140, 2700, 3350, 4080, 5650, 8100};

    private static double gainFor(double hz) {
        return hz < 430 ? Tuning.bassDb : (hz < 2400 ? Tuning.midDb : Tuning.trebleDb);
    }

    private void buildMel(double binHz, int bins) {
        melLo = new int[MEL_BINS];
        melHi = new int[MEL_BINS];
        for (int m = 0; m < MEL_BINS; m++) {
            double f0 = MEL_MIN_HZ * Math.pow(MEL_MAX_HZ / MEL_MIN_HZ, (double) m / MEL_BINS);
            double f1 = MEL_MIN_HZ * Math.pow(MEL_MAX_HZ / MEL_MIN_HZ, (double) (m + 1) / MEL_BINS);
            int lo = (int) Math.round(f0 / binHz), hi = (int) Math.round(f1 / binHz) - 1;
            lo = Math.max(1, Math.min(bins - 1, lo));
            hi = Math.max(lo, Math.min(bins - 1, hi));
            melLo[m] = lo;
            melHi[m] = hi;
        }
    }

    /** Melbank value at position x in 0..1 (linear interpolation). */
    private double melAt(double x) {
        double f = Math.max(0, Math.min(MEL_BINS - 1, x * (MEL_BINS - 1)));
        int i = (int) f;
        double t = f - i;
        return i + 1 < MEL_BINS ? mel[i] * (1 - t) + mel[i + 1] * t : mel[i];
    }

    // ================================================================== rendering

    /** Renders {@code n} pixels as RGB bytes into {@code out} (length >= 3n). */
    /** Effects used by the automatic change: the ones that follow the music best. */
    public static final int[] PARTY = {PULSE, ENERGY, POWER, SCROLL, FIRE, RAIN, ENERGY2, BLADE, EQUALIZER, BANDS, MAGNITUDE, MARCHING, BAR, SCAN, LAVA, BPM_FLASH, BPM_CHASE,
            FIREWORKS, METEOR, RIPPLE, HEARTBEAT, KALEIDO, BPM_SWEEP};

    static boolean mirrors(int effect) {
        return Tuning.mirror && effect != PULSE && effect != STROBE && effect != SPECTRUM
                && effect != HIERARCHY && effect != EQUALIZER && effect != BLOCKS
                && effect != HEARTBEAT && effect != BREATHE && effect != KALEIDO;
    }

    // ---------------------------------------------------------------- LED matrix (e.g. a word clock)

    private static final int RADIAL = 48;
    private final byte[] radial = new byte[RADIAL * 3];
    private float[] colLevel = new float[0], colPeak = new float[0];

    /**
     * Renders on a grid of w columns, row by row from the top left (pixel = row * w + column).
     * Equalizer and Bands become vertical bars, one per column, with a falling peak dot.
     * Every other effect is drawn as rings: the 1D effect goes from the centre of the grid outwards.
     * LEDs after the last full row (e.g. the 4 minute dots of a word clock) get the centre colour.
     */
    public void renderMatrix(int effect, int n, int w, double hue, byte[] out) {
        int h = Math.max(1, n / w);
        java.util.Arrays.fill(out, 0, n * 3, (byte) 0);
        if (effect == EQUALIZER || effect == BANDS) {
            frame++;
            rollOffset = (rollOffset + Tuning.roll * 0.002) % 1.0;
            if (colLevel.length != w) { colLevel = new float[w]; colPeak = new float[w]; }
            for (int c = 0; c < w; c++) {
                int from = c * MEL_BINS / w, to = Math.max(from + 1, (c + 1) * MEL_BINS / w);
                double sum = 0;
                for (int m = from; m < to; m++) sum += mel[m];
                float v = (float) Math.min(1, 1.6 * sum / (to - from));
                colLevel[c] = Math.max(v, colLevel[c] * 0.82f);
                float height = colLevel[c] * h;
                colPeak[c] = Math.max(height, colPeak[c] - 0.12f);
                int[] col = gradient(w == 1 ? 0 : (double) c / (w - 1));
                for (int r = 0; r < h; r++) {
                    int fromBottom = h - 1 - r;
                    float lit = Math.max(0, Math.min(1, height - fromBottom));
                    int i = (r * w + c) * 3;
                    if (lit > 0) {
                        out[i] = toByte(col[0] * lit);
                        out[i + 1] = toByte(col[1] * lit);
                        out[i + 2] = toByte(col[2] * lit);
                    } else if (colPeak[c] > 0.5f && fromBottom == Math.min(h - 1, (int) colPeak[c])) {
                        out[i] = toByte(col[0] * 0.5f);
                        out[i + 1] = toByte(col[1] * 0.5f);
                        out[i + 2] = toByte(col[2] * 0.5f);
                    }
                }
            }
            int[] dot = gradient(0);
            float b = (float) bass;
            for (int i = w * h; i < n; i++) {
                out[i * 3] = toByte(dot[0] * b);
                out[i * 3 + 1] = toByte(dot[1] * b);
                out[i * 3 + 2] = toByte(dot[2] * b);
            }
            return;
        }
        render(effect, RADIAL, hue, radial);
        boolean centre = mirrors(effect);
        double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0;
        double maxD = Math.hypot(cx, cy);
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                double d = maxD == 0 ? 0 : Math.hypot(c - cx, r - cy) / maxD;
                int src = centre ? RADIAL / 2 + (int) Math.round(d * (RADIAL / 2 - 1)) : (int) Math.round(d * (RADIAL - 1));
                int i = (r * w + c) * 3;
                out[i] = radial[src * 3];
                out[i + 1] = radial[src * 3 + 1];
                out[i + 2] = radial[src * 3 + 2];
            }
        }
        int mid = (centre ? RADIAL / 2 : 0) * 3;
        for (int i = w * h; i < n; i++) {
            out[i * 3] = radial[mid];
            out[i * 3 + 1] = radial[mid + 1];
            out[i * 3 + 2] = radial[mid + 2];
        }
    }

    public void render(int effect, int n, double hue, byte[] out) {
        boolean mirror = mirrors(effect);
        frame++;
        int len = mirror ? (n + 1) / 2 : n;
        if (work.length != len * 3) work = new float[len * 3];
        java.util.Arrays.fill(work, 0);
        rollOffset = (rollOffset + Tuning.roll * 0.002) % 1.0;

        switch (effect) {
            case ENERGY: energy(len); break;
            case SCROLL: scroll(len); break;
            case WAVELENGTH: wavelength(len); break;
            case BANDS: bands(len); break;
            case POWER: power(len); break;
            case RAIN: rain(len); break;
            case MAGNITUDE: magnitude(len); break;
            case STROBE: strobe(len); break;
            case MELT: melt(len); break;
            case PITCH: pitch(len); break;
            case VU: vu(len); break;
            case SPECTRUM: spectrum(len); break;
            case EQUALIZER: equalizer(len); break;
            case BLOCKS: blocks(len); break;
            case BLADE: blade(len); break;
            case ENERGY2: energy2(len); break;
            case FIRE: fire(len); break;
            case LAVA: lava(len); break;
            case MARCHING: marching(len); break;
            case WATER: water(len); break;
            case REAL_STROBE: realStrobe(len); break;
            case BAR: bar(len); break;
            case SCAN: scan(len); break;
            case HIERARCHY: hierarchy(len); break;
            case CRAWLER: crawler(len); break;
            case BPM_FLASH: bpmFlash(len); break;
            case BPM_CHASE: bpmChase(len); break;
            case FIREWORKS: fireworks(len); break;
            case METEOR: meteor(len); break;
            case RIPPLE: ripple(len); break;
            case TWINKLE: twinkle(len); break;
            case BREATHE: breathe(len); break;
            case HEARTBEAT: heartbeat(len); break;
            case KALEIDO: kaleido(len); break;
            case BPM_SWEEP: bpmSweep(len); break;
            default: pulse(len, hue); break;
        }

        if (Tuning.blur > 0 && effect != PULSE && effect != STROBE && effect != HIERARCHY && effect != REAL_STROBE
                && effect != HEARTBEAT && effect != BREATHE)
            blur(work, len, Tuning.blur);

        for (int i = 0; i < n; i++) {
            // mirrored: index 0 of the effect is the centre of the strip
            int src = mirror ? Math.abs(i - n / 2) - ((n % 2 == 0 && i < n / 2) ? 1 : 0) : i;
            src = Math.max(0, Math.min(len - 1, src));
            out[i * 3] = toByte(work[src * 3]);
            out[i * 3 + 1] = toByte(work[src * 3 + 1]);
            out[i * 3 + 2] = toByte(work[src * 3 + 2]);
        }
    }

    // ---------------------------------------------------------------- effects

    private void pulse(int len, double hue) {
        int[] c = hsv(hue + colorShift * 360, 1, bass);
        for (int i = 0; i < len; i++) set(work, i, c[0], c[1], c[2]);
    }

    /** LedFx Energy: bars from the origin whose lengths follow lows (red), mids (green), highs (blue). */
    private void energy(int len) {
        int lo = (int) Math.round(len * bass), mi = (int) Math.round(len * mid), hi = (int) Math.round(len * high);
        int[] cl = gradient(0), cm = gradient(0.5), ch = gradient(0.999);
        for (int i = 0; i < len; i++) {
            float r = 0, g = 0, b = 0;
            if (Tuning.additive) {
                if (i < lo) { r += cl[0]; g += cl[1]; b += cl[2]; }
                if (i < mi) { r += cm[0]; g += cm[1]; b += cm[2]; }
                if (i < hi) { r += ch[0]; g += ch[1]; b += ch[2]; }
            } else {
                // overlap: the shortest bar is drawn on top
                if (i < lo) { r = cl[0]; g = cl[1]; b = cl[2]; }
                if (i < mi) { r = cm[0]; g = cm[1]; b = cm[2]; }
                if (i < hi) { r = ch[0]; g = ch[1]; b = ch[2]; }
            }
            set(work, i, r, g, b);
        }
        pixelFilter(len, 0.6f, 0.35f);
    }

    /** LedFx Scroll: colours for lows/mids/highs are injected at the origin and scroll outward, fading. */
    private void scroll(int len) {
        if (scroll.length != len * 3) scroll = new float[len * 3];
        int speed = Math.max(1, Math.round(Tuning.scrollSpeed * len / 150f));
        System.arraycopy(scroll, 0, scroll, speed * 3, Math.max(0, (len - speed) * 3));
        for (int i = 0; i < scroll.length; i++) scroll[i] *= 0.97f;
        int[] cl = gradient(0), cm = gradient(0.5), ch = gradient(0.999);
        float r = (float) (cl[0] * bass + cm[0] * mid + ch[0] * high);
        float g = (float) (cl[1] * bass + cm[1] * mid + ch[1] * high);
        float b = (float) (cl[2] * bass + cm[2] * mid + ch[2] * high);
        for (int i = 0; i < speed && i < len; i++) set(scroll, i, r, g, b);
        System.arraycopy(scroll, 0, work, 0, len * 3);
    }

    /** LedFx Wavelength: the melbank stretched over the strip, coloured by the gradient. */
    private void wavelength(int len) {
        for (int i = 0; i < len; i++) {
            double x = len == 1 ? 0 : (double) i / (len - 1);
            int[] c = gradient(x);
            double v = melAt(x);
            set(work, i, (float) (c[0] * v), (float) (c[1] * v), (float) (c[2] * v));
        }
    }

    /** LedFx Bands: 6 blocks, each a bar as long as the loudest part of its frequency range. */
    private void bands(int len) {
        int count = Math.min(6, len);
        for (int bnd = 0; bnd < count; bnd++) {
            int start = bnd * len / count, end = (bnd + 1) * len / count;
            double peak = 0;
            for (int m = bnd * MEL_BINS / count; m < (bnd + 1) * MEL_BINS / count; m++) peak = Math.max(peak, mel[m]);
            int lit = (int) Math.round(peak * (end - start));
            int[] c = gradient((double) bnd / count);
            for (int i = start; i < start + lit && i < end; i++) set(work, i, c[0], c[1], c[2]);
        }
    }

    /** LedFx Power: gradient background from the melbank, a bass bar, white sparks on hits. */
    private void power(int len) {
        if (sparks.length != len * 3) { sparks = new float[len * 3]; bassBar = new float[len * 3]; }
        for (int i = 0; i < sparks.length; i++) { sparks[i] *= 0.85f; bassBar[i] *= 0.95f; }
        if (onset) {
            for (int k = 0; k < Math.max(1, len / 20); k++) set(sparks, rnd.nextInt(len), 255, 255, 255);
        }
        bassSlow = bass > bassSlow ? bassSlow + (bass - bassSlow) * 0.8 : bassSlow + (bass - bassSlow) * 0.1;
        int idx = (int) (bassSlow * len);
        int[] bc = gradient(bassSlow);
        for (int i = 0; i < idx; i++) set(bassBar, i, bc[0], bc[1], bc[2]);
        for (int i = 0; i < len; i++) {
            double x = len == 1 ? 0 : (double) i / (len - 1);
            int[] c = gradient(x);
            double v = melAt(x);
            for (int ch = 0; ch < 3; ch++) work[i * 3 + ch] = (float) (c[ch] * v) + bassBar[i * 3 + ch] + sparks[i * 3 + ch];
        }
    }

    /** LedFx Rain: a sudden rise in lows/mids/highs drops a red/green/blue ripple at a random spot. */
    private void rain(int len) {
        if (dropAge.length != len) { dropAge = new int[len]; dropColor = new float[len * 3]; }
        double[] in = {bass, mid, high};
        double[] sens = {0.25, 0.15, 0.15};
        int[][] col = {gradient(0), gradient(0.5), gradient(0.999)};
        for (int k = 0; k < 3; k++) {
            if (in[k] - rainFilt[k] > sens[k]) {
                int at = rnd.nextInt(len);
                dropAge[at] = 1;
                set(dropColor, at, col[k][0], col[k][1], col[k][2]);
            }
            double alpha = in[k] > rainFilt[k] ? 0.99 : 0.5;
            rainFilt[k] += (in[k] - rainFilt[k]) * alpha;
        }
        final int frames = 24;
        for (int at = 0; at < len; at++) {
            int age = dropAge[at];
            if (age == 0) continue;
            double radius = age * Math.max(0.8, len / 120.0), fade = 1.0 - (double) age / frames;
            int reach = (int) Math.ceil(radius + 3);
            for (int d = -reach; d <= reach; d++) {
                int i = at + d;
                if (i < 0 || i >= len) continue;
                double v = Math.max(0, 1 - Math.abs(Math.abs(d) - radius) / 2.5) * fade;
                for (int ch = 0; ch < 3; ch++) work[i * 3 + ch] += (float) (dropColor[at * 3 + ch] * v);
            }
            dropAge[at] = age + 1 >= frames ? 0 : age + 1;
        }
    }

    /** LedFx Magnitude: the whole gradient, brightness following the bass. */
    private void magnitude(int len) {
        for (int i = 0; i < len; i++) {
            int[] c = gradient(len == 1 ? 0 : (double) i / (len - 1));
            set(work, i, (float) (c[0] * bass), (float) (c[1] * bass), (float) (c[2] * bass));
        }
    }

    /** Flash on every detected beat, colour stepping through the gradient. */
    private void strobe(int len) {
        if (beat) strobe = 1;
        else strobe *= 0.7;
        if (silent) strobe = 0;
        int[] c = gradient((beatCount % 8) / 8.0);
        double v = strobe * strobe;
        for (int i = 0; i < len; i++) set(work, i, (float) (c[0] * v), (float) (c[1] * v), (float) (c[2] * v));
    }

    /** LedFx Melt: slow liquid colour waves that speed up with the bass. */
    private void melt(int len) {
        meltTime += 0.02 + bass * 0.08;
        double t1 = frac(meltTime / 5.0), t2 = frac(meltTime / 6.5);
        for (int i = 0; i < len; i++) {
            double x = 1.0 - (len == 1 ? 0 : (double) i / (len - 1));
            double v = sin01(x);
            v = sin01(v + t1);
            v = sin01(v + t1);
            v = v * v;
            v *= Math.sqrt(Math.max(vol, bass)); // dark when the music stops
            int[] c = hsv((x + t2) * 360.0, 1, v);
            set(work, i, c[0], c[1], c[2]);
        }
    }

    /** LedFx Pitch Spectrum: the melbank lights the strip in the colour of the dominant note. */
    private void pitch(int len) {
        if (pitch.length != len * 3) pitch = new float[len * 3];
        int[] note = {0, 0, 0};
        if (majorPeakHz > 60) {
            double x = Math.max(0, Math.min(1, log2(majorPeakHz / 80.0) / log2(2000.0 / 80.0)));
            note = gradient(x);
        }
        for (int i = 0; i < len; i++) {
            double m = melAt(len == 1 ? 0 : (double) i / (len - 1));
            for (int ch = 0; ch < 3; ch++) {
                float v = (float) (pitch[i * 3 + ch] * (1 - m) + note[ch] * m);
                pitch[i * 3 + ch] = v * 0.9f;
            }
        }
        System.arraycopy(pitch, 0, work, 0, len * 3);
    }

    private void vu(int len) {
        int lit = (int) Math.round(vol * len);
        for (int i = 0; i < lit && i < len; i++) {
            double d = len == 1 ? 0 : (double) i / (len - 1);
            set(work, i, (float) Math.min(255, 510 * d), (float) Math.min(255, 510 * (1 - d)), 0);
        }
    }

    private void spectrum(int len) {
        for (int i = 0; i < len; i++) {
            int b = Math.min(15, i * 16 / Math.max(1, len));
            int[] c = hsv(b * 300.0 / 16.0, 1, spec[b]);
            set(work, i, c[0], c[1], c[2]);
        }
    }

    // ---------------------------------------------------------------- more LedFx effects

    /** Music "presence": 0 in silence, used to make the atmospheric effects go dark. */
    private double presence() {
        return Math.sqrt(Math.max(vol, bass));
    }

    private double pos(int i, int len) {
        return len == 1 ? 0 : (double) i / (len - 1);
    }

    /** LedFx Equalizer: the strip split in segments, each lit as far as its frequency range is loud. */
    private void equalizer(int len) {
        int segs = Math.min(8, len);
        for (int sgm = 0; sgm < segs; sgm++) {
            int start = sgm * len / segs, end = (sgm + 1) * len / segs;
            double sum = 0;
            int cnt = 0;
            for (int m = sgm * MEL_BINS / segs; m < (sgm + 1) * MEL_BINS / segs; m++) { sum += mel[m]; cnt++; }
            double v = cnt > 0 ? Math.min(1, 1.6 * sum / cnt) : 0;
            int lit = (int) Math.round(v * (end - start));
            for (int i = start; i < start + lit && i < end; i++) {
                int[] c = gradient(pos(i, len));
                set(work, i, c[0], c[1], c[2]);
            }
        }
    }

    /** LedFx Blocks: blocks of the gradient whose brightness follows their frequency range. */
    private void blocks(int len) {
        int count = Math.min(6, len);
        for (int bl = 0; bl < count; bl++) {
            int start = bl * len / count, end = (bl + 1) * len / count;
            double peak = 0;
            for (int m = bl * MEL_BINS / count; m < (bl + 1) * MEL_BINS / count; m++) peak = Math.max(peak, mel[m]);
            int[] c = gradient((double) bl / count);
            for (int i = start; i < end; i++) set(work, i, (float) (c[0] * peak), (float) (c[1] * peak), (float) (c[2] * peak));
        }
    }

    /** LedFx Blade Power+: a rainbow bar as long as the bass, leaving a fading trail. */
    private void blade(int len) {
        if (blade.length != len) blade = new float[len];
        bladeLen = Math.min(1, bass * 1.2);
        int idx = (int) (bladeLen * len);
        for (int i = 0; i < len; i++) {
            blade[i] *= 0.82f;
            if (i < idx) blade[i] = 1;
            int[] c = hsv(pos(i, len) * 360, 1, blade[i]);
            set(work, i, c[0], c[1], c[2]);
        }
    }

    /** LedFx Energy 2: soft moving waves whose colour and position shift with the bass. */
    private void energy2(int len) {
        animTime += 0.01;
        double t1 = frac(animTime);
        double p = presence();
        for (int i = 0; i < len; i++) {
            double v = frac(pos(i, len) + 2.0 * sin01(t1 + 0.6 * bass));
            v = 1 - Math.abs(2 * v - 1); // triangle
            v = v * v;
            boolean white = v >= 0.9 - 0.9 * bass;
            int[] c = hsv((bass * 0.5 + t1) * 360, white ? 0 : 1, v * p);
            set(work, i, c[0], c[1], c[2]);
        }
    }

    /** LedFx Fire: sparks rise from the base and cool down; the bass feeds the flames. */
    private void fire(int len) {
        if (heat.length != len) heat = new float[len];
        float cooling = (float) (0.90 + bass * 0.08);
        for (int i = 0; i < len; i++) heat[i] *= cooling;
        for (int i = len - 1; i >= 3; i--) heat[i] = (heat[i - 1] + heat[i - 2] * 2 + heat[i - 3]) / 4f;
        int sparks = 2 + (int) (bass * 6);
        for (int k = 0; k < sparks; k++) {
            int at = rnd.nextInt(Math.max(1, Math.min(len, 5 + len / 10)));
            heat[at] = Math.min(1.5f, heat[at] + (float) (0.4 + bass * 1.5 * rnd.nextDouble()));
        }
        double p = presence();
        for (int i = 0; i < len; i++) {
            float hv = heat[i];
            double h = Math.min(1, hv * hv) * 0.1;
            double sat = Math.max(0, Math.min(1, 1 - (hv - 1) * 2));
            double v = Math.min(1, hv * 2) * p;
            int[] c = hsv(h * 360, sat, v);
            set(work, i, c[0], c[1], c[2]);
        }
    }

    /** LedFx Lava lamp: slow blobs of colour, a little faster and brighter with the bass. */
    private void lava(int len) {
        animTime += 0.004 * (1 + bass);
        double t1 = frac(animTime), t2 = frac(animTime * 2);
        double p = presence();
        for (int i = 0; i < len; i++) {
            double x = pos(i, len);
            double w1 = sin01(t1 + x), w2 = sin01(t2 - x);
            double w3 = sin01(frac(x + w1 + w2));
            double h = t1 + x + (w1 + 0.1) * (w2 + bass * 0.7) * (w3 + bass * 0.9) * 0.1;
            double v = (w1 + 0.1) * (w2 + bass * 0.7) * (w3 + bass * 0.9) + 0.3;
            v = Math.min(1, v * v);
            int[] c = hsv(h * 360, 1, v * p);
            set(work, i, c[0], c[1], c[2]);
        }
    }

    /** LedFx Marching: bands of colour that march along, pushed by the bass. */
    private void marching(int len) {
        animTime += 0.003 + bass * 0.02;
        double t1 = frac(animTime * 20), t2 = animTime;
        double p = presence();
        for (int i = 0; i < len; i++) {
            double x = pos(i, len);
            double w1 = sin01(t1 + x);
            double h = sin01(sin01(w1 - x));
            double w2 = sin01(t2 + 2 - x * 10);
            double v = Math.max(0, w1 - w2);
            int[] c = hsv(h * 360, 1, Math.min(1, v) * p);
            set(work, i, c[0], c[1], c[2]);
        }
    }

    /** LedFx Water: a 1D ripple simulation; bass, mids and highs drop "stones" in the water. */
    private void water(int len) {
        if (waterA.length != len) { waterA = new double[len]; waterB = new double[len]; }
        if (len < 5) return;
        drop(1, bass * 1.5);
        drop(len / 2, bass * 1.5);
        drop(len - 2, bass * 1.5);
        drop((int) (len * frac(0.25 + frame * 0.0004)), mid);
        drop((int) (len * frac(0.75 - frame * 0.0004)), mid);
        for (int k = 0; k < 4; k++) drop((int) (len * frac(0.125 + k * 0.25 + frame * 0.0007 * (k % 2 == 0 ? 1 : -1))), high * 0.7);
        for (int step = 0; step < 2; step++) {
            double[] tmp = waterA;
            for (int i = 1; i < len - 1; i++) {
                double v = (waterA[i - 1] + waterA[i + 1]) - waterB[i];
                waterB[i] = v - v / 16.0; // damping
            }
            waterA = waterB;
            waterB = tmp;
        }
        for (int i = 0; i < len; i++) {
            double h = waterA[i];
            double v = Math.min(1, Math.abs(h) * 2);
            int[] c = gradient(Math.max(0, Math.min(0.999, 0.5 + h * 0.5)));
            set(work, i, (float) (c[0] * v), (float) (c[1] * v), (float) (c[2] * v));
        }
    }

    private void drop(int at, double height) {
        if (height <= 0.02 || at < 1 || at >= waterA.length - 1) return;
        waterA[at] = Math.max(-1.5, Math.min(1.5, waterA[at] + height * 0.4));
    }

    /** LedFx Real Strobe: the whole strip flashes a gradient colour on beats, white strobes on hits. */
    private void realStrobe(int len) {
        if (realBass.length != len * 3) { realBass = new float[len * 3]; realFlash = new float[len * 3]; }
        for (int i = 0; i < realBass.length; i++) { realBass[i] *= 0.82f; realFlash[i] *= 0.6f; }
        if (beat) {
            int[] c = gradient((beatCount % 10) / 10.0);
            for (int i = 0; i < len; i++) set(realBass, i, c[0], c[1], c[2]);
        }
        if (onset && !beat) {
            int w = Math.max(1, Math.min(len, len / 6));
            int at = len > w ? rnd.nextInt(len - w) : 0;
            for (int i = at; i < at + w; i++) set(realFlash, i, 255, 255, 255);
        }
        for (int i = 0; i < len * 3; i++) work[i] = realBass[i] + realFlash[i];
    }

    /** LedFx Bar: a bar that bounces from one end to the other on every beat. */
    private void bar(int len) {
        double osc = Math.min(1, (frame - lastBeatFrame) / Math.max(10, beatInterval));
        double x = 0.5 * Math.sin(Math.PI * (osc - 0.5)) + 0.5; // ease in-out
        double barLen = 0.3;
        x = x * (1 - barLen);
        double start = barPhase == 0 ? x : 1 - (x + barLen), end = start + barLen;
        int[] c = gradient(barColor);
        double v = Math.min(1, 0.25 + presence());
        if (silent) v = 0;
        for (int i = (int) (start * len); i < Math.min(len, (int) Math.ceil(end * len)); i++) {
            set(work, i, (float) (c[0] * v), (float) (c[1] * v), (float) (c[2] * v));
        }
    }

    /** LedFx Scan: a block of light runs back and forth, faster and brighter with the bass. */
    private void scan(int len) {
        int width = Math.max(1, len / 6);
        scanPos += scanDir * (0.2 + bass * 3.0) * len / 150.0;
        if (scanPos > len - width) { scanPos = len - width; scanDir = -1; }
        if (scanPos < 0) { scanPos = 0; scanDir = 1; }
        int[] c = gradient(scanPos / Math.max(1, len - width));
        double v = silent ? 0 : Math.min(1, 0.3 * presence() + bass);
        for (int i = (int) scanPos; i < Math.min(len, (int) scanPos + width); i++) {
            set(work, i, (float) (c[0] * v), (float) (c[1] * v), (float) (c[2] * v));
        }
    }

    /** LedFx Hierarchy: shows the colour of the strongest part of the sound (bass first, then mids, then highs). */
    private void hierarchy(int len) {
        int[] cl = gradient(0), cm = gradient(0.5), ch = gradient(0.999);
        if (bass > 0.35) {
            hierColor = cl;
            hierPower = bass;
            lastLowFrame = frame;
        } else if (frame - lastLowFrame > 15) {
            if (mid > 0.3) {
                hierColor = cm;
                hierPower = mid;
                lastMidFrame = frame;
            } else if (frame - lastMidFrame > 15) {
                hierColor = ch;
                hierPower = high;
            }
        }
        double v = silent ? 0 : Math.min(1, Math.pow(hierPower, 0.6));
        for (int i = 0; i < len; i++) set(work, i, (float) (hierColor[0] * v), (float) (hierColor[1] * v), (float) (hierColor[2] * v));
    }

    /** LedFx Crawler: stretched colour patterns that crawl along, faster with the bass. */
    private void crawler(int len) {
        animTime += 0.004 + bass * 0.03;
        double t1 = frac(animTime * 0.7), t2 = frac(animTime * 1.3), t3 = frac(animTime * 1.3 + bass * 0.2);
        double p = presence();
        for (int i = 0; i < len; i++) {
            double h = (i + t3 * len) / len * 1.5;
            h = (h % 0.15) + pos(i, len) + Math.sin(2 * Math.PI * t1);
            double v = sin01(h + t2);
            v = v * v;
            int[] c = hsv(h * 360, 1, v * p);
            set(work, i, c[0], c[1], c[2]);
        }
    }

    /** Per-pixel exponential filter (LedFx's pixel smoothing): fast rise, slower fall. */
    private void pixelFilter(int len, float rise, float fall) {
        if (filt.length != len * 3) filt = new float[len * 3];
        for (int i = 0; i < len * 3; i++) {
            float a = work[i] > filt[i] ? rise : fall;
            filt[i] += (work[i] - filt[i]) * a;
            work[i] = filt[i];
        }
    }


    // ---------------------------------------------------------------- LedSync effects

    // particles shared by Fireworks and Meteors: position and speed in strip lengths, colour, life 1..0
    private static final int MAX_P = 160;
    private final double[] pPos = new double[MAX_P], pVel = new double[MAX_P], pLife = new double[MAX_P], pFade = new double[MAX_P];
    private final int[] pColor = new int[MAX_P];
    private final boolean[] pRocket = new boolean[MAX_P];
    private int pCount;
    private float[] trail = new float[0];

    private void spawn(double pos, double vel, int rgb, double fade, boolean rocket) {
        if (pCount >= MAX_P) return;
        pPos[pCount] = pos; pVel[pCount] = vel; pColor[pCount] = rgb; pLife[pCount] = 1; pFade[pCount] = fade; pRocket[pCount] = rocket;
        pCount++;
    }

    private static int pack(int[] c) {
        return (c[0] << 16) | (c[1] << 8) | c[2];
    }

    /** Draws a soft dot (about 1.5 LEDs wide) at pos 0..1, adding to what is there. */
    private void dot(float[] px, int len, double pos, int rgb, double v) {
        double f = pos * (len - 1);
        int i = (int) Math.floor(f);
        double t = f - i;
        for (int k = 0; k < 2; k++) {
            int at = i + k;
            if (at < 0 || at >= len) continue;
            double w = (k == 0 ? 1 - t : t) * v;
            px[at * 3] = Math.min(255, px[at * 3] + (float) (((rgb >> 16) & 255) * w));
            px[at * 3 + 1] = Math.min(255, px[at * 3 + 1] + (float) (((rgb >> 8) & 255) * w));
            px[at * 3 + 2] = Math.min(255, px[at * 3 + 2] + (float) ((rgb & 255) * w));
        }
    }

    /** Steps every particle; rockets that run out of life burst into sparks. Faded ones are removed. */
    private void stepParticles(double gravity) {
        int alive = 0;
        int born = pCount;
        for (int k = 0; k < born; k++) {
            pPos[k] += pVel[k];
            pVel[k] = pVel[k] * 0.97 - gravity;
            pLife[k] -= pFade[k];
            if (pRocket[k] && (pVel[k] <= 0.002 || pLife[k] <= 0)) {
                int n = 10 + rnd.nextInt(10);
                double hue = rnd.nextDouble();
                for (int s = 0; s < n && pCount < MAX_P; s++) {
                    int[] c = gradient(hue + rnd.nextDouble() * 0.15);
                    double v = (rnd.nextDouble() - 0.5) * 0.03;
                    spawn(pPos[k], v, pack(c), 0.02 + rnd.nextDouble() * 0.02, false);
                }
                pLife[k] = 0;
            }
        }
        for (int k = 0; k < pCount; k++) {
            if (pLife[k] > 0 && pPos[k] > -0.05 && pPos[k] < 1.05) {
                pPos[alive] = pPos[k]; pVel[alive] = pVel[k]; pLife[alive] = pLife[k]; pFade[alive] = pFade[k];
                pColor[alive] = pColor[k]; pRocket[alive] = pRocket[k];
                alive++;
            }
        }
        pCount = alive;
    }

    /** Fireworks: each bass hit launches a rocket from the start; it slows down and bursts into coloured sparks. */
    private void fireworks(int len) {
        if (trail.length != len * 3) trail = new float[len * 3];
        if (beat && !silent) {
            double height = 0.35 + 0.6 * Math.max(bass, rnd.nextDouble() * 0.6);
            spawn(0, 0.02 + 0.03 * height, 0xFFE0B0, 0.01, true);   // tops out at about 0.35-0.95 of the strip
        }
        if (onset && !beat && !silent && high > 0.5 && rnd.nextDouble() < 0.3) {
            spawn(0.3 + rnd.nextDouble() * 0.6, 0, 0xFFFFFF, 0.08, false);   // little crackle on the hi-hats
        }
        stepParticles(0.0006);
        for (int i = 0; i < trail.length; i++) trail[i] *= 0.82f;
        for (int k = 0; k < pCount; k++) {
            double v = pRocket[k] ? 0.8 : pLife[k] * pLife[k];
            dot(trail, len, pPos[k], pColor[k], v);
        }
        System.arraycopy(trail, 0, work, 0, len * 3);
    }

    /** Meteors: onsets shoot comets with long fading tails; louder hits are faster and brighter. */
    private void meteor(int len) {
        if (trail.length != len * 3) trail = new float[len * 3];
        if (onset && !silent && pCount < 12) {
            int[] c = gradient(beat ? (beatCount % 6) / 6.0 : 0.5 + rnd.nextDouble() * 0.5);
            spawn(0, 0.008 + 0.025 * Math.max(bass, vol), pack(c), 0.004, false);
        }
        int alive = 0;
        for (int k = 0; k < pCount; k++) {
            pPos[k] += pVel[k];
            pLife[k] -= pFade[k];
            if (pPos[k] < 1.05 && pLife[k] > 0) {
                pPos[alive] = pPos[k]; pVel[alive] = pVel[k]; pLife[alive] = pLife[k]; pFade[alive] = pFade[k]; pColor[alive] = pColor[k];
                pRocket[alive] = false;
                alive++;
            }
        }
        pCount = alive;
        float decay = (float) (0.80 + 0.12 * (1 - bass));   // shorter tails on heavy bass, longer in calm parts
        for (int i = 0; i < trail.length; i++) trail[i] *= decay;
        for (int k = 0; k < pCount; k++) {
            dot(trail, len, pPos[k], pColor[k], 1);
            if (rnd.nextDouble() < 0.15) dot(trail, len, Math.max(0, pPos[k] - rnd.nextDouble() * 0.1), 0xFFFFFF, 0.3);
        }
        System.arraycopy(trail, 0, work, 0, len * 3);
    }

    // Beat ripples: up to 8 rings expanding from the origin
    private final double[] ringPos = new double[8], ringAmp = new double[8], ringCol = new double[8];
    private int ringNext;

    /** Beat ripples: every beat sends a ring of colour outward from the origin (the centre when mirrored). */
    private void ripple(int len) {
        boolean step = bpmSure ? bpmTick : beat;
        if (step && !silent) {
            ringPos[ringNext] = 0;
            ringAmp[ringNext] = 0.7 + 0.3 * bass;
            ringCol[ringNext] = ((bpmSure ? bpmIndex : beatCount) % 6) / 6.0;
            ringNext = (ringNext + 1) % ringPos.length;
        }
        double speed = bpmSure && beatInterval > 0 ? 1.0 / Math.max(15, beatInterval * 1.6) : 0.025;
        double width = 0.07 + 0.08 * mid;
        double base = 0.08 * presence();
        int[] bg = gradient(0.75);
        for (int i = 0; i < len; i++) set(work, i, (float) (bg[0] * base), (float) (bg[1] * base), (float) (bg[2] * base));
        for (int r = 0; r < ringPos.length; r++) {
            if (ringAmp[r] <= 0.01) continue;
            int[] c = gradient(ringCol[r]);
            for (int i = 0; i < len; i++) {
                double d = Math.abs(pos(i, len) - ringPos[r]) / width;
                if (d >= 1) continue;
                double v = ringAmp[r] * (1 - d * d);
                work[i * 3] = Math.min(255, work[i * 3] + (float) (c[0] * v));
                work[i * 3 + 1] = Math.min(255, work[i * 3 + 1] + (float) (c[1] * v));
                work[i * 3 + 2] = Math.min(255, work[i * 3 + 2] + (float) (c[2] * v));
            }
            ringPos[r] += speed;
            ringAmp[r] *= 0.975;
            if (ringPos[r] > 1 + width) ringAmp[r] = 0;
        }
    }

    private float[] star = new float[0], starHue = new float[0];

    /** Starry sky: a dim gradient night with stars that light up on the highs and twinkle out. */
    private void twinkle(int len) {
        if (star.length != len) { star = new float[len]; starHue = new float[len]; }
        double pres = presence();
        int births = (int) Math.floor((0.001 + high * high * 0.012 + (onset ? 0.015 : 0)) * len + rnd.nextDouble());
        if (silent) births = 0;
        for (int k = 0; k < births; k++) {
            int at = rnd.nextInt(len);
            if (star[at] < 0.3f) { star[at] = (float) (0.8 + 0.2 * rnd.nextDouble()); starHue[at] = (float) rnd.nextDouble(); }
        }
        for (int i = 0; i < len; i++) {
            int[] bg = gradient(pos(i, len) * 0.5 + animTime * 0.02);
            double night = 0.12 * pres;
            float s = star[i];
            double tw = s * (0.75 + 0.25 * Math.sin(frame * 0.4 + i));   // gentle shimmer while it fades
            int[] sc = gradient(starHue[i]);
            // stars are mostly white with a hint of the gradient
            float r = (float) (bg[0] * night + (255 * 0.6 + sc[0] * 0.4) * tw);
            float g = (float) (bg[1] * night + (255 * 0.6 + sc[1] * 0.4) * tw);
            float b = (float) (bg[2] * night + (255 * 0.6 + sc[2] * 0.4) * tw);
            set(work, i, r, g, b);
            star[i] = s * 0.95f;
        }
        animTime += 0.02;
    }

    private double breathLevel, breathPhase;

    /** Breathe: the gradient slowly flows and the whole light breathes with the average volume, no flashing. */
    private void breathe(int len) {
        breathLevel += (Math.max(vol, bass * 0.8) - breathLevel) * 0.03;   // ~0.7 s to follow the song
        double rate = bpmSure ? 1.0 / Math.max(30, beatInterval * 4) : 0.006; // one breath per bar when the tempo is known
        breathPhase = frac(breathPhase + rate);
        double v = Math.min(1, presence() * 2) * (0.25 + 0.75 * breathLevel) * (0.65 + 0.35 * sin01(breathPhase));
        double drift = frame * 0.0008;
        for (int i = 0; i < len; i++) {
            int[] c = gradient(pos(i, len) * 0.6 + drift);
            set(work, i, (float) (c[0] * v), (float) (c[1] * v), (float) (c[2] * v));
        }
    }

    private double heartA, heartB;
    private int heartDelay = -1;

    /** Heartbeat: every beat gives a strong pulse followed by a softer one, spreading from the centre. */
    private void heartbeat(int len) {
        boolean step = bpmSure ? bpmTick : beat;
        if (step && !silent) { heartA = 1; heartDelay = 9; }   // the second "dub" 180 ms later
        if (heartDelay > 0 && --heartDelay == 0) heartB = 0.65;
        heartA *= 0.9;
        heartB *= 0.9;
        double v = Math.max(heartA, heartB) * Math.min(1, presence() * 3);
        double reach = 0.35 + 0.65 * v;   // stronger beats light more of the strip
        int[] c = gradient(0);
        int[] edge = gradient(0.3);
        double base = 0.05 * presence();
        for (int i = 0; i < len; i++) {
            double d = Math.abs(pos(i, len) - 0.5) * 2;   // 0 centre, 1 ends
            double k = d < reach ? 1 - d / reach : 0;
            double lvl = v * (0.3 + 0.7 * k);
            set(work, i, (float) (c[0] * lvl * (1 - d) + edge[0] * lvl * d + c[0] * base),
                    (float) (c[1] * lvl * (1 - d) + edge[1] * lvl * d + c[1] * base),
                    (float) (c[2] * lvl * (1 - d) + edge[2] * lvl * d + c[2] * base));
        }
    }

    private double kaleidoPos, kaleidoDir = 1, kaleidoSpeed;

    /** Kaleidoscope: mirrored gradient slices that spin with the mids and turn around on strong beats. */
    private void kaleido(int len) {
        if (beat && bass > 0.7 && rnd.nextDouble() < 0.35) kaleidoDir = -kaleidoDir;
        kaleidoSpeed += ((0.002 + 0.02 * mid) * kaleidoDir - kaleidoSpeed) * 0.15;
        kaleidoPos += kaleidoSpeed;
        int slices = len >= 60 ? 6 : (len >= 20 ? 4 : 2);
        double pres = Math.min(1, presence() * 1.8);
        for (int i = 0; i < len; i++) {
            double x = pos(i, len) * slices;
            int sl = (int) Math.min(slices - 1, Math.floor(x));
            double t = x - sl;
            if (sl % 2 == 1) t = 1 - t;   // every other slice is mirrored
            double v = pres * (0.55 + 0.45 * Math.max(bass, Math.pow(1 - Math.abs(t - 0.5) * 2, 2)));
            int[] c = gradient(t * 0.5 + kaleidoPos);
            set(work, i, (float) (c[0] * v), (float) (c[1] * v), (float) (c[2] * v));
        }
    }

    /** BPM Sweep: a bright band crosses the whole light once per beat, back and forth, locked to the tempo. */
    private void bpmSweep(int len) {
        double ph = bpmSure ? bpmPhase : Math.min(1, (frame - lastBeatFrame) / Math.max(10, beatInterval));
        long idx = bpmSure ? bpmIndex : beatCount;
        double eased = 0.5 - 0.5 * Math.cos(Math.PI * ph);
        double at = idx % 2 == 0 ? eased : 1 - eased;
        double width = Math.max(1.5 / len, 0.22);
        int[] c = gradient(((idx / 2) % 6) / 6.0);
        double pres = Math.min(1, presence() * 3);
        for (int i = 0; i < len; i++) {
            double d = Math.abs(pos(i, len) - at) / width;
            double v = d < 1 ? (1 - d) * (1 - d) : 0;
            v = (v + 0.08) * pres;   // faint glow everywhere so it never goes fully dark mid-song
            set(work, i, (float) (c[0] * v), (float) (c[1] * v), (float) (c[2] * v));
        }
    }

    /** Colour of an effect at position t (0..1) for the on-screen visualiser: the gradient, or the chosen hue for Bass pulse. */
    static int previewColor(int effect, double t) {
        if (effect == PULSE) {
            int[] c = hsv(Tuning.hue, 1, 1);
            return (c[0] << 16) | (c[1] << 8) | c[2];
        }
        int[] stops = GRADIENTS[Math.max(0, Math.min(GRADIENTS.length - 1, Tuning.gradient))];
        double f = Math.max(0, Math.min(1, t)) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) f);
        double u = f - i;
        int a = stops[i], b = stops[i + 1];
        return ((int) Math.round(((a >> 16) & 255) * (1 - u) + ((b >> 16) & 255) * u) << 16)
                | ((int) Math.round(((a >> 8) & 255) * (1 - u) + ((b >> 8) & 255) * u) << 8)
                | (int) Math.round((a & 255) * (1 - u) + (b & 255) * u);
    }

    // ---------------------------------------------------------------- helpers

    private int[] gradient(double pos) {
        int[] stops = GRADIENTS[Math.max(0, Math.min(GRADIENTS.length - 1, Tuning.gradient))];
        double x = frac(pos + rollOffset + colorShift);
        double f = x * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) f);
        double t = f - i;
        int a = stops[i], b = stops[i + 1];
        return new int[]{
                (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t),
                (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                (int) Math.round((a & 255) * (1 - t) + (b & 255) * t)};
    }

    private static void blur(float[] px, int len, int radius) {
        float[] tmp = new float[px.length];
        for (int pass = 0; pass < 2; pass++) {
            for (int ch = 0; ch < 3; ch++) {
                float sum = 0;
                int count = 0;
                for (int i = -radius; i < len + radius; i++) {
                    int add = i + radius, rem = i - radius - 1;
                    if (add >= 0 && add < len) { sum += px[add * 3 + ch]; count++; }
                    if (rem >= 0 && rem < len) { sum -= px[rem * 3 + ch]; count--; }
                    if (i >= 0 && i < len) tmp[i * 3 + ch] = count > 0 ? sum / count : 0;
                }
            }
            System.arraycopy(tmp, 0, px, 0, len * 3);
        }
    }

    private static double sumDb(double[] db, int from, int to) {
        double p = 0;
        for (int i = from; i <= to; i++) p += Math.pow(10, db[i] / 10);
        return 10 * Math.log10(p + 1e-14);
    }

    private double track(double ref, double db) {
        if (silent) return ref;
        return db > ref ? db : Math.max(REF_MIN_DB, ref - REF_FALL_DB);
    }

    /** 0..1: how close the level is to its recent peak, with a punchy curve and noise gate. */
    private static double level(double db, double ref, double gainDb) {
        return level(db, ref, gainDb, 0.5);
    }

    private static double level(double db, double ref, double gainDb, double rangeScale) {
        if (db < FLOOR_DB) return 0;
        double range = Math.max(6, Tuning.rangeDb * rangeScale);
        double x = (db - (ref - range)) / range;
        x = x < 0 ? 0 : (x > 1 ? 1 : x);
        // noise gate on the linear value (gentle), then a punchy curve
        double g = Math.max(0, Math.min(0.9, Tuning.gate / 255.0));
        x = x <= g ? 0 : (x - g) / (1 - g);
        x = x * x;
        // the bass/mid/treble sliders scale the result instead of shifting it into saturation
        return Math.min(1, x * Math.pow(10, gainDb / 20.0));
    }

    /**
     * Automatic balance between lows, mids and highs: each one's long-term average (~4 s) is pulled
     * towards the same target, so all three colours show up whatever the mix of the song.
     */
    private final double[] balanceAvg = {0.3, 0.3, 0.3};

    private double balance(int group, double raw) {
        balanceAvg[group] += (raw - balanceAvg[group]) * 0.005;
        double factor = Math.max(0.7, Math.min(3.0, 0.35 / Math.max(0.02, balanceAvg[group])));
        return Math.min(1, raw * factor);
    }

    private static double gain(double v, double db) {
        return Math.min(1, v * Math.pow(10, db / 20.0));
    }

    private static double smooth(double cur, double target) {
        double decay = Math.max(0.3, Math.min(0.97, Tuning.decay));
        return target >= cur ? target : cur * decay + target * (1 - decay);
    }

    private static double log2(double v) {
        return Math.log(v) / Math.log(2);
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static double sin01(double x) {
        return (Math.sin(2 * Math.PI * x) + 1) / 2;
    }

    static int[] hsv(double hueDeg, double s, double v) {
        double h = ((hueDeg % 360) + 360) % 360 / 60.0;
        int i = (int) h;
        double f = h - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        double r, g, b;
        switch (i) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return new int[]{(int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255)};
    }

    private static void set(float[] px, int i, float r, float g, float b) {
        px[i * 3] = r;
        px[i * 3 + 1] = g;
        px[i * 3 + 2] = b;
    }

    private static byte toByte(float v) {
        return (byte) (v <= 0 ? 0 : (v >= 255 ? 255 : Math.round(v)));
    }
}
