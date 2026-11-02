// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Small animated previews of the effects. One built-in demo song (120 BPM: kick, bass line, hi-hats,
 * melody, and a quiet break every few bars) is analysed once and drives every preview strip.
 */
final class EffectPreview {
    private static final int SR = 48000, HOP = 960;   // 20 ms per analysis frame, like the real capture

    private final AudioAnalyzer analyzer = new AudioAnalyzer(SR);
    private final short[] buf = new short[HOP];
    private final byte[] packet = new byte[44];
    private final Random rnd = new Random(7);
    private final List<Strip> strips = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private long t;
    private boolean running;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            // two analysis frames per 40 ms redraw, so the effects run at their normal 50 fps speed
            for (int k = 0; k < 2; k++) {
                fill();
                analyzer.process(buf, HOP, packet);
                for (Strip s : strips) s.step(analyzer);
            }
            for (Strip s : strips) s.invalidate();
            ui.postDelayed(this, 40);
        }
    };

    Strip strip(Context c, int effect) {
        Strip s = new Strip(c, effect);
        strips.add(s);
        return s;
    }

    void remove(List<? extends View> views) {
        strips.removeAll(views);
    }

    void start() {
        if (running) return;
        running = true;
        ui.post(tick);
    }

    void stop() {
        running = false;
        ui.removeCallbacks(tick);
    }

    /** The demo song: 16 s loop, the last 3 s of every 8 are a soft break without drums. */
    private void fill() {
        for (int i = 0; i < HOP; i++, t++) {
            double s = (t % (16L * SR)) / (double) SR;
            boolean brk = (s % 8) > 5;
            double beat = s % 0.5, eighth = s % 0.25;
            double kick = !brk && beat < 0.15 ? 0.7 * Math.exp(-beat * 25) * Math.sin(2 * Math.PI * (50 + 80 * Math.exp(-beat * 40)) * beat) : 0;
            double bassF = new double[]{55, 55, 73.4, 65.4}[(int) (s / 2) % 4];
            double bass = (brk ? 0.08 : 0.25) * Math.sin(2 * Math.PI * bassF * s) * (0.6 + 0.4 * Math.exp(-beat * 6));
            double hat = !brk && eighth < 0.03 ? 0.15 * (rnd.nextDouble() - 0.5) * Math.exp(-eighth * 150) : 0;
            double mel = (brk ? 0.12 : 0.08) * Math.sin(2 * Math.PI * new double[]{440, 494, 523, 587, 659, 587, 523, 494}[(int) (s * 2) % 8] * s);
            double v = (kick + bass + hat + mel) * 32767 * 0.8;
            buf[i] = (short) Math.max(-32768, Math.min(32767, v));
        }
    }

    /** A row of LEDs showing one effect. */
    static final class Strip extends View {
        private static final int LEDS = 30;
        private static final int[] GAMMA = new int[256];
        static {
            for (int i = 0; i < 256; i++) GAMMA[i] = (int) Math.round(255 * Math.pow(i / 255.0, 0.55));
        }
        private final DirectEffects fx = new DirectEffects();
        private final byte[] px = new byte[LEDS * 3];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        int effect;

        Strip(Context c, int effect) {
            super(c);
            this.effect = effect;
        }

        void step(AudioAnalyzer a) {
            fx.update(a);
            fx.render(effect, LEDS, Tuning.hue, px);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float gap = Math.max(1, w * 0.006f);
            float lw = (w - gap * (LEDS - 1)) / LEDS;
            float r = Math.min(lw, h) / 2;
            for (int i = 0; i < LEDS; i++) {
                // brightened for the screen: real LEDs look much brighter than the same value on a phone
                int red = GAMMA[px[i * 3] & 255], g = GAMMA[px[i * 3 + 1] & 255], b = GAMMA[px[i * 3 + 2] & 255];
                float x = i * (lw + gap);
                rect.set(x, 0, x + lw, h);
                paint.setColor(0x14FFFFFF);
                c.drawRoundRect(rect, r, r, paint);
                if (red + g + b > 6) {
                    paint.setColor(0xFF000000 | (red << 16) | (g << 8) | b);
                    c.drawRoundRect(rect, r, r, paint);
                }
            }
        }
    }
}
