// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

/**
 * Tempo (BPM) and beat clock from the audio, 50 frames per second.
 * <ul>
 * <li>Onset strength: log spectral flux from 40 Hz to 4 kHz, minus its local average.</li>
 * <li>Tempo: autocorrelation of the last 10 s of onsets for 60-180 BPM, weighted towards ~120 BPM so the
 *     tracker prefers the beat over half/double time, refined between frames.</li>
 * <li>Phase: a comb over the last 8 beats finds where the beats fall; a running clock then ticks on every
 *     predicted beat and is nudged towards the estimate, so ticks stay steady even through quiet bars.</li>
 * </ul>
 */
final class BeatTracker {
    static final double FPS = 50;
    private static final int N = 512;                 // ~10 s of history
    private static final int MIN_LAG = 16, MAX_LAG = 50; // 187..60 BPM

    private final double[] onset = new double[N];
    private int pos;            // next write index
    private long frame;
    private double[] prevLog;
    private double fluxMean;

    /** Tempo in BPM (0 = not sure yet). */
    volatile double bpm;
    /** 0..1: how clear the beat is. Below ~0.25 the clock is not trusted. */
    volatile double confidence;
    /** True on the frame of each predicted beat (only when confident). */
    volatile boolean tick;
    /** Counts beats; beatIndex % 4 == 0 is the first beat of a 4-beat bar (approximate). */
    volatile long beatIndex;
    /** 0..1 position inside the current beat. */
    volatile double phase;

    private double period;      // frames per beat
    private double nextBeat = -1;

    void process(double[] power, double binHz, boolean silent) {
        int lo = Math.max(1, (int) (40 / binHz)), hi = Math.min(power.length - 1, (int) (4000 / binHz));
        if (prevLog == null) prevLog = new double[power.length];
        double flux = 0;
        for (int k = lo; k <= hi; k++) {
            double l = Math.log1p(power[k] * 1e-3);
            double d = l - prevLog[k];
            if (d > 0) flux += d;
            prevLog[k] = l;
        }
        fluxMean += (flux - fluxMean) * 0.1;
        double o = silent ? 0 : Math.max(0, flux - fluxMean);
        onset[pos] = o;
        pos = (pos + 1) % N;
        frame++;

        if (silent) confidence *= 0.98;
        if (frame % 25 == 0 && frame >= 200) estimate();
        runClock();
    }

    private double at(int back) {           // onset 'back' frames ago (0 = newest)
        return onset[((pos - 1 - back) % N + N) % N];
    }

    private void estimate() {
        double energy = 0;
        for (int i = 0; i < N; i++) energy += onset[i] * onset[i];
        if (energy <= 1e-9) { confidence *= 0.8; return; }
        double[] acf = new double[MAX_LAG + 2];
        for (int lag = MIN_LAG - 1; lag <= MAX_LAG + 1; lag++) {
            double s = 0;
            for (int i = 0; i < N - lag; i++) s += at(i) * at(i + lag);
            acf[lag] = s / (N - lag);
        }
        double norm = energy / N;
        int best = -1;
        double bestScore = 0;
        for (int lag = MIN_LAG; lag <= MAX_LAG; lag++) {
            if (acf[lag] < acf[lag - 1] || acf[lag] < acf[lag + 1]) continue;   // local peaks only
            double bpmHere = 60 * FPS / lag;
            double oct = Math.log(bpmHere / 120) / Math.log(2);
            double score = acf[lag] * Math.exp(-0.5 * oct * oct / 0.5);
            if (score > bestScore) { bestScore = score; best = lag; }
        }
        if (best < 0) { confidence *= 0.9; return; }
        // parabolic refinement between frames
        double a = acf[best - 1], b = acf[best], c = acf[best + 1];
        double den = a - 2 * b + c;
        double lagF = best + (Math.abs(den) > 1e-12 ? 0.5 * (a - c) / den : 0);
        double conf = Math.max(0, Math.min(1, acf[best] / norm));
        confidence = confidence * 0.6 + conf * 0.4;

        double newPeriod = lagF;
        if (period > 0 && Math.abs(newPeriod - period) / period < 0.08) newPeriod = period * 0.7 + newPeriod * 0.3;
        period = newPeriod;
        bpm = 60 * FPS / period;

        // phase: which offset inside one beat collects the most onset over the last 8 beats
        int p = (int) Math.round(period);
        double bestSum = -1;
        int bestOff = 0;
        for (int off = 0; off < p; off++) {
            double s = 0;
            for (int k = 0; k < 8; k++) {
                int back = (int) Math.round(off + k * period);
                if (back >= N) break;
                s += at(back) + 0.5 * (back > 0 ? at(back - 1) : 0) + 0.5 * at(back + 1);
            }
            if (s > bestSum) { bestSum = s; bestOff = off; }
        }
        double lastBeat = frame - 1 - bestOff;           // frame number of the latest beat
        double predicted = lastBeat + period;
        while (predicted < frame) predicted += period;
        if (nextBeat < 0) nextBeat = predicted;
        else {
            // nudge the running clock towards the estimate (wrap the difference to ±half a beat)
            double diff = predicted - nextBeat;
            diff -= Math.round(diff / period) * period;
            nextBeat += diff * 0.35;
        }
    }

    private void runClock() {
        tick = false;
        if (period <= 0 || nextBeat < 0) return;
        if (frame >= nextBeat) {
            while (nextBeat <= frame) nextBeat += period;
            if (confidence >= 0.25) {
                tick = true;
                beatIndex++;
            }
        }
        phase = Math.max(0, Math.min(1, 1 - (nextBeat - frame) / period));
    }

    boolean confident() {
        return confidence >= 0.25 && bpm > 0;
    }
}
