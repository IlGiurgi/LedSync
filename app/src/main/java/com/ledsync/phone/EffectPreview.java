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

/**
 * Small animated previews of the effects. They follow the music the app is listening to while it is
 * running, and stay dark when it is stopped.
 */
final class EffectPreview {
    private final List<Strip> strips = new ArrayList<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean running, lit;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            AudioAnalyzer a = CaptureService.running ? CaptureService.analyzer : null;
            if (a != null) {
                // one step per analysis frame (20 ms), like the effects on the lights
                for (Strip s : strips) { s.step(a); s.invalidate(); }
                lit = true;
            } else if (lit) {
                for (Strip s : strips) { s.clear(); s.invalidate(); }
                lit = false;
            }
            ui.postDelayed(this, 20);
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

        void clear() {
            java.util.Arrays.fill(px, (byte) 0);
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
