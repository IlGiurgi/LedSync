// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Several lights joined into one long virtual strip (Tuning.chain, in order): the effect is rendered once on
 * the sum of their lengths and every light gets its own piece, so a meteor leaves one strip and enters the
 * next instead of the same picture repeating on each. A one-colour lamp counts as {@link #LAMP_PIXELS} pixels.
 * Pure Java, used by the audio thread only.
 */
final class LightChain {
    /** Length of a one-colour lamp (Yeelight, Govee, Hue) inside the joined strip. */
    static final int LAMP_PIXELS = 30;

    private final DirectEffects fx = new DirectEffects();
    private final List<String> keys = new ArrayList<>();
    private final List<Integer> lengths = new ArrayList<>();
    private final Map<String, Integer> offset = new HashMap<>();
    private byte[] pixels = new byte[0];
    private int total;

    /** Starts a new frame: add the lights that take part now, in chain order, then call {@link #render}. */
    void begin() {
        keys.clear();
        lengths.clear();
        offset.clear();
        total = 0;
    }

    void add(String key, int leds) {
        if (leds <= 0 || offset.containsKey(key)) return;
        keys.add(key);
        lengths.add(leds);
        offset.put(key, total);
        total += leds;
    }

    /** Joining needs at least two lights; with fewer every light just runs its own effect. */
    boolean active() {
        return keys.size() >= 2;
    }

    int total() {
        return total;
    }

    /** Renders the effect of the first light on the whole joined strip. */
    void render(AudioAnalyzer an, double hue) {
        if (!active()) return;
        if (pixels.length != total * 3) pixels = new byte[total * 3];
        fx.update(an);
        fx.render(Tuning.effectFor(keys.get(0)), total, hue, pixels);
        fx.applyDrop(pixels, total);   // build-up and drop on the whole joined strip at once
    }

    /** Copies this light's piece of the joined strip into its own frame; false if it is not joined now. */
    boolean fill(String key, byte[] out, int leds) {
        if (!active()) return false;
        Integer at = offset.get(key);
        if (at == null) return false;
        System.arraycopy(pixels, at * 3, out, 0, Math.min(leds, total - at) * 3);
        return true;
    }

    /** The whole joined strip of the last frame (for tests and previews). */
    byte[] pixels() {
        return pixels;
    }
}
