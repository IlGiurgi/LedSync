// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Philips Hue through the bridge's local REST API (v1). The bridge accepts about 10 light commands per
 * second in total, so each light gets an equal share: with 2 lights ~5 changes per second each.
 * Each light sends from its own thread, so the audio never waits for the network.
 * The bridge user ("username") is created once with the bridge's link button (see {@link #pair}).
 */
final class HueLink implements ColorLight {
    /** bridge ip -> username, filled by the app from its settings. */
    static final Map<String, String> users = new ConcurrentHashMap<>();
    /** bridge ip -> lights currently streaming on it, to share the bridge's rate. */
    private static final Map<String, AtomicInteger> active = new ConcurrentHashMap<>();

    private final String bridge, light;
    private volatile boolean open, stop, disposed;
    private volatile int wantRgb = -1, wantBright = -1;
    private volatile boolean wantOff;
    private volatile String error;
    private Thread worker;

    HueLink(String bridge, String light) {
        this.bridge = bridge;
        this.light = light;
    }

    @Override public synchronized void openAsync() {
        if (open || disposed) return;
        open = true;
        stop = false;
        active.computeIfAbsent(bridge, k -> new AtomicInteger()).incrementAndGet();
        worker = new Thread(this::loop, "hue-" + light);
        worker.setDaemon(true);
        worker.start();
    }

    @Override public boolean isOpen() {
        return open;
    }

    private void loop() {
        int sentRgb = -1, sentBright = -1;
        boolean sentOff = false, first = true;
        while (!stop) {
            int lights = Math.max(1, active.getOrDefault(bridge, new AtomicInteger(1)).get());
            long interval = Math.max(100, lights * 100L);
            long t0 = System.currentTimeMillis();
            int rgb = wantRgb, bri = wantBright;
            boolean off = wantOff;
            String user = users.get(bridge);
            if (user == null) {
                error = L.t("Hue: bridge ", "Hue: bridge ") + bridge + L.t(" non associato (togli e riaggiungi la luce)", " not paired (remove and add the light again)");
            } else if (off != sentOff || first) {
                if (put(user, "{\"on\":" + !off + "}")) { sentOff = off; first = false; }
            } else if (!off && rgb >= 0 && (rgb != sentRgb || Math.abs(bri - sentBright) >= 4)) {
                double[] xy = xy(rgb);
                String body = String.format(java.util.Locale.US,
                        "{\"bri\":%d,\"xy\":[%.4f,%.4f],\"transitiontime\":1}", bri, xy[0], xy[1]);
                if (put(user, body)) { sentRgb = rgb; sentBright = bri; }
            }
            long wait = interval - (System.currentTimeMillis() - t0);
            if (wait > 0) try { Thread.sleep(wait); } catch (InterruptedException e) { return; }
        }
    }

    private boolean put(String user, String body) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://" + bridge + "/api/" + user + "/lights/" + light + "/state").openConnection();
            c.setRequestMethod("PUT");
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            String reply = read(c.getInputStream());
            c.disconnect();
            if (reply.contains("\"error\"")) {
                error = "Hue: " + reply.replaceAll(".*\"description\":\"([^\"]*)\".*", "$1");
                return false;
            }
            error = null;
            return true;
        } catch (Exception e) {
            error = "Hue " + bridge + ": " + e.getMessage();
            return false;
        }
    }

    static String read(InputStream in) throws Exception {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = i.read(b)) > 0) out.write(b, 0, n);
            return out.toString("UTF-8");
        }
    }

    @Override public void show(int r, int g, int b) {
        if (!open) openAsync();
        int max = Math.max(r, Math.max(g, b));
        wantBright = max < 4 ? 1 : Math.max(1, Math.min(254, Math.round(max * 254f / 255f)));
        if (max >= 4) wantRgb = ((r * 255 / max) << 16) | ((g * 255 / max) << 8) | (b * 255 / max);
        else if (wantRgb < 0) wantRgb = 0xFFFFFF;
    }

    @Override public void setOff(boolean off) {
        if (!open && !off) openAsync();
        wantOff = off;
    }

    @Override public void dispose() {
        disposed = true;
        close();
    }

    @Override public String error() {
        return error;
    }

    @Override public synchronized void close() {
        if (!open) return;
        open = false;
        stop = true;
        AtomicInteger n = active.get(bridge);
        if (n != null) n.decrementAndGet();
        if (worker != null) worker.interrupt();
    }

    /** sRGB colour -> CIE xy (Philips' recommended conversion, wide-gamut matrix). */
    static double[] xy(int rgb) {
        double r = lin((rgb >> 16 & 255) / 255.0), g = lin((rgb >> 8 & 255) / 255.0), b = lin((rgb & 255) / 255.0);
        double X = r * 0.664511 + g * 0.154324 + b * 0.162028;
        double Y = r * 0.283881 + g * 0.668433 + b * 0.047685;
        double Z = r * 0.000088 + g * 0.072310 + b * 0.986039;
        double sum = X + Y + Z;
        if (sum <= 0) return new double[]{0.3227, 0.329};
        return new double[]{X / sum, Y / sum};
    }

    private static double lin(double c) {
        return c > 0.04045 ? Math.pow((c + 0.055) / 1.055, 2.4) : c / 12.92;
    }

    /**
     * Creates a bridge user; the link button on the bridge must have been pressed in the last 30 s.
     * Returns the username, or throws with the bridge's message.
     */
    static String pair(String bridge) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + bridge + "/api").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(3000);
        c.setReadTimeout(4000);
        c.setDoOutput(true);
        try (OutputStream o = c.getOutputStream()) {
            o.write("{\"devicetype\":\"ledsync#android\"}".getBytes(StandardCharsets.UTF_8));
        }
        String reply = read(c.getInputStream());
        org.json.JSONObject first = new org.json.JSONArray(reply).getJSONObject(0);
        if (first.has("success")) return first.getJSONObject("success").getString("username");
        String desc = first.optJSONObject("error") != null ? first.getJSONObject("error").optString("description") : reply;
        if (desc.contains("link button")) throw new Exception(L.t("premi il pulsante rotondo sul bridge Hue e riprova entro 30 secondi", "press the round button on the Hue bridge and try again within 30 seconds"));
        throw new Exception(desc);
    }

    /** Colour-capable lights of a bridge: id -> name. */
    static Map<String, String> lights(String bridge, String user) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://" + bridge + "/api/" + user + "/lights").openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(4000);
        org.json.JSONObject all = new org.json.JSONObject(read(c.getInputStream()));
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (java.util.Iterator<String> it = all.keys(); it.hasNext(); ) {
            String id = it.next();
            org.json.JSONObject l = all.getJSONObject(id);
            org.json.JSONObject st = l.optJSONObject("state");
            if (st != null && st.has("xy")) out.put(id, l.optString("name", "Hue " + id));
        }
        return out;
    }

    static void power(String bridge, String light, boolean on) {
        String user = users.get(bridge);
        if (user == null) return;
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://" + bridge + "/api/" + user + "/lights/" + light + "/state").openConnection();
            c.setRequestMethod("PUT");
            c.setConnectTimeout(2000);
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) { o.write(("{\"on\":" + on + "}").getBytes(StandardCharsets.UTF_8)); }
            read(c.getInputStream());
        } catch (Exception ignored) { }
    }
}
