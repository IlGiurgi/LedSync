// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

/** Live-adjustable analysis settings, written by the UI sliders and read every frame. */
public final class Tuning {
    private Tuning() { }

    /** Dynamic range (dB) mapped onto 0..255. Smaller = more contrast. */
    public static volatile double rangeDb = 24;
    /** Extra gain (dB) for bass (43-430 Hz), mids (430-2400 Hz) and treble (2.4-9.3 kHz). */
    public static volatile double bassDb = 0, midDb = 0, trebleDb = 0;
    /** Values below this (0..255) are cut to zero; removes background flicker. */
    public static volatile double gate = 0;
    /** How slowly lights fall after a hit: 0.5 = fast, 0.95 = slow. */
    public static volatile double decay = 0.75;
    /** Bass must exceed its average by this ratio to count as a beat. Lower = more beats. */
    public static volatile double beatRatio = 1.3;
    /** Packets are held back this long, to line up with a Bluetooth speaker's delay. */
    public static volatile int delayMs = 0;

    /** true = the app computes the effect and streams pixels; false = WLED effects via sound sync. */
    public static volatile boolean directMode = true;
    /** Index into DirectEffects.NAMES. */
    public static volatile int directEffect = 0;
    /** Colour (hue in degrees) for single-colour effects. */
    public static volatile int hue = 0;
    /** Index into DirectEffects.GRADIENT_NAMES. */
    public static volatile int gradient = 0;
    /** Effect starts at the centre and is mirrored to both ends. */
    public static volatile boolean mirror = true;
    /** Blur radius in pixels, 0..10. */
    public static volatile int blur = 2;
    /** Gradient rotation speed, 0..10. */
    public static volatile int roll = 0;
    /** Energy: colours of bass/mids/highs add up (true) or cover each other (false). */
    public static volatile boolean additive = true;
    /** Scroll / wave speed, 1..10. */
    public static volatile int scrollSpeed = 3;
    /** Overall brightness applied by the app to every frame, 0..1. */
    public static volatile double brightness = 0.8;

    /** true = listen with the microphone (music from TV, PC, speakers) instead of the phone's own audio. */
    public static volatile boolean micMode = false;
    /** Battery saver: frames are sent at 25 per second instead of 50 (effects still run at full speed). */
    public static volatile boolean batterySaver = false;
    /** Change effect automatically every this many seconds, on a beat; 0 = off. */
    public static volatile int autoCycleSec = 0;

    /** The colours step forward on every beat. */
    public static volatile boolean beatColors = false;
    /** Stop by itself after this many minutes of silence; 0 = never. */
    public static volatile int autoOffMin = 0;

    // ---- per device settings, keyed by IP (live: the service reads them every frame)
    /** Devices switched off in the app (kept in the list). */
    public static final java.util.Set<String> disabled = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Effect override per device; missing = the main effect. */
    public static final java.util.Map<String, Integer> deviceEffect = new java.util.concurrent.ConcurrentHashMap<>();
    /** Devices whose direction is reversed. */
    /** Per-device brightness in percent (missing = 100), on top of the overall brightness. */
    public static final java.util.Map<String, Integer> deviceBrightness = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Set<String> flipped = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** LED count typed by hand (for lights that are not WLED, e.g. ESPHome with the "wled" effect). */
    public static final java.util.Map<String, Integer> ledCount = new java.util.concurrent.ConcurrentHashMap<>();

    /** Grid width for LED matrices (e.g. 11 for the word clock); missing = a normal strip. */
    public static final java.util.Map<String, Integer> matrixWidth = new java.util.concurrent.ConcurrentHashMap<>();

    public static int effectFor(String ip) {
        Integer e = deviceEffect.get(ip);
        return e != null ? e : directEffect;
    }
}
