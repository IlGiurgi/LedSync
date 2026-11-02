// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

/**
 * A light that shows one colour at a time (Yeelight, Govee, Philips Hue). The effect is rendered on a few
 * virtual pixels and the average colour is sent. Implementations must never block the audio thread.
 */
interface ColorLight {
    /** Shows a colour (0..255 each); brightness comes from the colour. Rate-limited by the implementation. */
    void show(int r, int g, int b);

    /** Turns the light off (true) or back on (false). */
    void setOff(boolean off);

    boolean isOpen();

    /** Starts connecting in the background if needed. */
    void openAsync();

    /** Last problem in Italian, or null. */
    String error();

    /** A note for the status line (e.g. "slow mode"), or null. */
    default String note() { return null; }

    void close();

    /** Closes for good at the end of a run: a connection still being opened must not stay open. */
    default void dispose() { close(); }

    /** "y:ip" = Yeelight, "g:ip" = Govee, "h:bridge#light" = Philips Hue. */
    static ColorLight forKey(String key) {
        if (key.startsWith("y:")) return new YeelightLink(key.substring(2));
        if (key.startsWith("g:")) return new GoveeLink(key.substring(2));
        if (key.startsWith("h:")) {
            String[] p = key.substring(2).split("#", 2);
            return new HueLink(p[0], p.length > 1 ? p[1] : "1");
        }
        return null;
    }

    static boolean isColorKey(String key) {
        return key.startsWith("y:") || key.startsWith("g:") || key.startsWith("h:");
    }

    /** The address to ping / show for a device key. */
    static String hostOf(String key) {
        if (!isColorKey(key)) return key;
        String rest = key.substring(2);
        int h = rest.indexOf('#');
        return h >= 0 ? rest.substring(0, h) : rest;
    }
}
