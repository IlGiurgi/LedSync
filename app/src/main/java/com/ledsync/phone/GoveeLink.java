// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Govee "LAN API": JSON over UDP. Needs "LAN Control" switched on for the light in the Govee Home app.
 * Discovery: multicast scan to 239.255.255.250:4001, answers arrive on port 4002. Commands go to port 4003.
 */
final class GoveeLink implements ColorLight {
    static final int SCAN_PORT = 4001, REPLY_PORT = 4002, CMD_PORT = 4003;

    private final String ip;
    private DatagramSocket socket;
    private InetAddress addr;
    private volatile String error;
    private long lastSend, lastBriSend;
    private int lastRgb = -1, lastBright = -1;
    private boolean off;
    private volatile boolean disposed;

    GoveeLink(String ip) {
        this.ip = ip;
    }

    /** ip -> model (e.g. "H6199"). */
    static Map<String, String> discover(int timeoutMs) {
        Map<String, String> found = new LinkedHashMap<>();
        try (DatagramSocket s = new DatagramSocket(null)) {
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(REPLY_PORT));
            s.setSoTimeout(400);
            byte[] scan = "{\"msg\":{\"cmd\":\"scan\",\"data\":{\"account_topic\":\"reserve\"}}}".getBytes(StandardCharsets.UTF_8);
            InetAddress group = InetAddress.getByName("239.255.255.250");
            long end = System.currentTimeMillis() + timeoutMs;
            int sent = 0;
            byte[] buf = new byte[2048];
            while (System.currentTimeMillis() < end) {
                if (sent < 3) { s.send(new DatagramPacket(scan, scan.length, group, SCAN_PORT)); sent++; }
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    s.receive(p);
                    org.json.JSONObject data = new org.json.JSONObject(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8))
                            .getJSONObject("msg").getJSONObject("data");
                    String host = data.optString("ip", p.getAddress().getHostAddress());
                    found.put(host, "Govee " + data.optString("sku", ""));
                } catch (SocketTimeoutException ignored) {
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return found;
    }

    @Override public void openAsync() {
        if (socket != null || disposed) return;
        try {
            socket = new DatagramSocket();
            addr = InetAddress.getByName(ip);   // a literal IP: no network lookup
            send("{\"msg\":{\"cmd\":\"turn\",\"data\":{\"value\":1}}}");
            error = null;
        } catch (Exception e) {
            error = "Govee " + ip + ": " + e.getMessage();
            close();
        }
    }

    @Override public boolean isOpen() {
        return socket != null;
    }

    private void send(String json) throws Exception {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(b, b.length, addr, CMD_PORT));
    }

    @Override public void show(int r, int g, int b) {
        if (socket == null) { openAsync(); return; }
        long now = System.currentTimeMillis();
        if (now - lastSend < 50) return;   // ~20 changes per second
        int max = Math.max(r, Math.max(g, b));
        int bright = max < 4 ? 1 : Math.max(1, Math.min(100, Math.round(max * 100f / 255f)));
        int rgb = max < 4 ? (lastRgb > 0 ? lastRgb : 0xFFFFFF)
                : ((r * 255 / max) << 16) | ((g * 255 / max) << 8) | (b * 255 / max);
        boolean colorChanged = rgb != lastRgb, brightChanged = Math.abs(bright - lastBright) >= 3;
        if (!colorChanged && !brightChanged && now - lastSend < 1000) return;
        try {
            if (colorChanged || !brightChanged) {
                send("{\"msg\":{\"cmd\":\"colorwc\",\"data\":{\"color\":{\"r\":" + (rgb >> 16 & 255) + ",\"g\":" + (rgb >> 8 & 255)
                        + ",\"b\":" + (rgb & 255) + "},\"colorTemInKelvin\":0}}}");
                lastRgb = rgb;
            }
            if (brightChanged && now - lastBriSend >= 50) {
                send("{\"msg\":{\"cmd\":\"brightness\",\"data\":{\"value\":" + bright + "}}}");
                lastBright = bright;
                lastBriSend = now;
            }
            lastSend = now;
        } catch (Exception e) {
            error = "Govee " + ip + ": " + e.getMessage();
        }
    }

    @Override public void setOff(boolean wantOff) {
        if (socket == null) { if (!wantOff) openAsync(); return; }
        if (wantOff == off) return;
        try {
            send("{\"msg\":{\"cmd\":\"turn\",\"data\":{\"value\":" + (wantOff ? 0 : 1) + "}}}");
            off = wantOff;
            lastRgb = -1;
        } catch (Exception ignored) { }
    }

    @Override public void dispose() {
        disposed = true;
        close();
    }

    @Override public String error() {
        return error;
    }

    @Override public void close() {
        if (socket != null) socket.close();
        socket = null;
    }

    static void power(String ip, boolean on) {
        try (DatagramSocket s = new DatagramSocket()) {
            byte[] b = ("{\"msg\":{\"cmd\":\"turn\",\"data\":{\"value\":" + (on ? 1 : 0) + "}}}").getBytes(StandardCharsets.UTF_8);
            s.send(new DatagramPacket(b, b.length, InetAddress.getByName(ip), CMD_PORT));
        } catch (Exception ignored) { }
    }
}
