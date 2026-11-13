// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

/**
 * Finds the "drop" in dance music: the bass and kick go away for a while (a build-up or a breakdown), the
 * music keeps playing and often grows, then the bass comes back all at once. Runs at 50 frames per second.
 * <ul>
 * <li>Reference: the usual bass level of the song, a slow average (~12 s) that stops following while the
 *     bass is missing, so the build-up is measured against the part before it.</li>
 * <li>Build-up: the bass stays at least {@value #GAP_DB} dB under the reference for more than 2 s while the
 *     music is still playing. Progress (0..1) grows with its length and with how much the highs and the
 *     volume rose since it started (risers, snare rolls).</li>
 * <li>Drop: after a build-up the bass jumps back to around the reference, at least {@value #JUMP_DB} dB
 *     above both its level during the build-up and its own last second. A slow fade-in of the bass, or bass
 *     after a pause between songs, does not count.</li>
 * </ul>
 * Pure Java, tested on a desktop JVM (tools/jvm-tests/DropTest.java).
 */
final class DropDetector {
    static final int NORMAL = 0, BUILD = 1, DROP = 2;

    private static final double GAP_DB = 6, JUMP_DB = 8, RETURN_DB = 3;
    private static final int MIN_GAP = 100;        // 2 s of missing bass before a drop can happen
    private static final int WARMUP = 400;         // 8 s of music before the reference is trusted
    private static final int DROP_FRAMES = 150;    // the drop "lasts" 3 s for the lights
    private static final int COOLDOWN = 750;       // at most one drop every 15 s

    /** NORMAL, BUILD or DROP. */
    volatile int state;
    /** 0..1 during a build-up: how close the drop probably is. */
    volatile double progress;
    /** Frames since the last drop (large when there was none). */
    volatile int sinceDrop = 100000;
    /** Number of drops found since the start, for tests and the status line. */
    volatile int count;

    private double bassFast, bassShort, bassRef, highShort, volShort;
    private double gapBassSum, gapHighStart, gapVolStart;
    private int gapFrames, musicFrames, silentFrames;
    private boolean primed;

    /**
     * @param bandDb the 16 GEQ band levels in dB of this frame
     * @param volDb  the frame volume in dBFS
     */
    void process(double[] bandDb, double volDb, boolean silent) {
        sinceDrop++;
        if (state == DROP && sinceDrop >= DROP_FRAMES) state = NORMAL;
        if (silent) {
            // a pause (end of a song): start over, the next song's bass is not a drop
            if (++silentFrames > 50) { primed = false; musicFrames = 0; endGap(); }
            progress *= 0.95;
            return;
        }
        silentFrames = 0;
        double bass = sumDb(bandDb, 0, 2), high = sumDb(bandDb, 11, 15);
        if (!primed) {
            bassFast = bassShort = bassRef = bass;
            highShort = high;
            volShort = volDb;
            primed = true;
        }
        bassFast += (bass - bassFast) * 0.4;       // ~50 ms
        bassShort += (bass - bassShort) * 0.02;    // ~1 s
        highShort += (high - highShort) * 0.02;
        volShort += (volDb - volShort) * 0.02;
        musicFrames++;

        boolean missing = musicFrames > WARMUP && bassShort < bassRef - GAP_DB;
        if (gapFrames > 0 || missing) {
            if (gapFrames == 0) { gapHighStart = highShort; gapVolStart = volShort; gapBassSum = 0; }
            // the drop: the bass is suddenly back after a long enough gap. bassShort lags ~1 s behind, so
            // it is still near the gap level on a sudden return but close to bassFast on a slow fade-in
            double gapBass = gapBassSum / Math.max(1, gapFrames);
            if (gapFrames >= MIN_GAP && sinceDrop > COOLDOWN && bassFast > bassRef - RETURN_DB
                    && bassFast - Math.max(gapBass, bassShort) > JUMP_DB) {
                state = DROP;
                sinceDrop = 0;
                count++;
                endGap();
                bassShort = bassFast;
                return;
            }
            if (bassShort > bassRef - RETURN_DB) {   // the bass came back slowly: no drop
                endGap();
            } else {
                gapFrames++;
                gapBassSum += bassFast;
                // after 30 s the reference starts to forget, so a quiet song without bass leaves this state
                if (gapFrames > 1500) bassRef += (bassShort - bassRef) * 0.001;
                if (gapFrames >= MIN_GAP) {
                    double rise = Math.max(highShort - gapHighStart, volShort - gapVolStart);
                    double p = 0.5 * Math.min(1, (gapFrames - MIN_GAP) / 600.0) + 0.5 * clamp01(rise / 8.0);
                    progress += (p - progress) * 0.05;
                    if (state != DROP) state = BUILD;
                }
            }
        } else {
            bassRef += (bassShort - bassRef) * 0.0017;   // ~12 s
            if (bassShort > bassRef) bassRef += (bassShort - bassRef) * 0.01; // rises faster than it falls
        }
        if (state != BUILD) progress *= 0.9;
    }

    private void endGap() {
        gapFrames = 0;
        if (state == BUILD) state = NORMAL;
    }

    private static double sumDb(double[] db, int from, int to) {
        double p = 0;
        for (int i = from; i <= to; i++) p += Math.pow(10, db[i] / 10);
        return 10 * Math.log10(p + 1e-14);
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
