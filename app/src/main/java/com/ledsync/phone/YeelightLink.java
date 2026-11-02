// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 LedSync contributors
package com.ledsync.phone;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Yeelight LAN control (needs "LAN Control" switched on in the Yeelight app).
 * Normal commands are limited to about one per second, so this first tries Yeelight's "music mode":
 * the light connects back to a TCP server on the phone and then accepts commands without limit.
 * If music mode does not work it falls back to normal mode (slower, about one change per second).
 * A Yeelight strip shows one colour at a time, so it gets the average colour of the effect.
 * Opening happens on its own thread and is retried every 10 s, so the audio never waits for the light.
 */
final class YeelightLink implements ColorLight {
    static final int PORT = 55443;
    static final int CLOSED = 0, MUSIC = 1, BASIC = 2;

    private final String ip;
    private Socket control, music;
    private volatile OutputStream out;
    private volatile int mode = CLOSED;
    private volatile boolean connecting, disposed;
    private long lastTry;
    private int cmdId = 1;
    private long lastSend;
    private int lastRgb = -1, lastBright = -1;
    private boolean off;
    /** Last problem, in Italian, or null. */
    volatile String error;

    YeelightLink(String ip) {
        this.ip = ip;
    }

    /** Finds Yeelights with SSDP on 239.255.255.250:1982. Returns ip -> name (or model). */
    static Map<String, String> discover(int timeoutMs) {
        Map<String, String> found = new LinkedHashMap<>();
        String msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1982\r\nMAN: \"ssdp:discover\"\r\nST: wifi_bulb\r\n";
        try (DatagramSocket s = new DatagramSocket()) {
            s.setSoTimeout(400);
            byte[] data = msg.getBytes(StandardCharsets.US_ASCII);
            InetAddress group = InetAddress.getByName("239.255.255.250");
            long end = System.currentTimeMillis() + timeoutMs;
            int sent = 0;
            byte[] buf = new byte[2048];
            while (System.currentTimeMillis() < end) {
                if (sent < 3) { s.send(new DatagramPacket(data, data.length, group, 1982)); sent++; }
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    s.receive(p);
                    String reply = new String(p.getData(), 0, p.getLength(), StandardCharsets.US_ASCII);
                    String loc = header(reply, "Location");
                    if (loc == null || !loc.startsWith("yeelight://")) continue;
                    String host = loc.substring("yeelight://".length()).split(":")[0];
                    String name = header(reply, "name");
                    String model = header(reply, "model");
                    found.put(host, name != null && !name.isEmpty() ? name : "Yeelight " + (model != null ? model : ""));
                } catch (SocketTimeoutException ignored) { }
            }
        } catch (Exception ignored) { }
        return found;
    }

    static String header(String reply, String name) {
        for (String line : reply.split("\r\n")) {
            int c = line.indexOf(':');
            if (c > 0 && line.substring(0, c).trim().equalsIgnoreCase(name)) return line.substring(c + 1).trim();
        }
        return null;
    }

    /** Builds one command line, e.g. {"id":3,"method":"set_rgb","params":[16711680,"sudden",0]}. */
    synchronized String command(String method, String params) {
        return "{\"id\":" + (cmdId++) + ",\"method\":\"" + method + "\",\"params\":[" + params + "]}\r\n";
    }

    static String explain(Exception e) {
        if (e instanceof ConnectException) return L.t("connessione rifiutata: attiva \"Controllo LAN\" nell'app Yeelight", "connection refused: switch on \"LAN Control\" in the Yeelight app");
        if (e instanceof SocketTimeoutException) return L.t("non raggiungibile (spenta dalla corrente o IP cambiato?)", "not reachable (powered off or new IP?)");
        return String.valueOf(e.getMessage());
    }

    /** Connects (blocking, up to ~7 s), turns the light on and picks music mode or the slower normal mode. */
    boolean open() {
        close();
        try {
            control = new Socket();
            control.connect(new InetSocketAddress(ip, PORT), 3000);
            OutputStream c = control.getOutputStream();
            c.write(command("set_power", "\"on\",\"smooth\",300").getBytes(StandardCharsets.US_ASCII));
            String phoneIp = control.getLocalAddress().getHostAddress();
            try (ServerSocket server = new ServerSocket(0)) {
                server.setSoTimeout(4000);
                c.write(command("set_music", "1,\"" + phoneIp + "\"," + server.getLocalPort()).getBytes(StandardCharsets.US_ASCII));
                c.flush();
                music = server.accept();
                music.setTcpNoDelay(true);
                out = music.getOutputStream();
                mode = MUSIC;
                error = null;
            } catch (SocketTimeoutException noMusic) {
                // the light did not call back (some models, or the phone blocks incoming connections)
                out = c;
                mode = BASIC;
                // the light answers every command here; read the answers so they never pile up
                final Socket sock = control;
                Thread drain = new Thread(() -> {
                    byte[] b = new byte[512];
                    try { while (sock.getInputStream().read(b) >= 0) { } } catch (Exception ignored) { }
                }, "yeelight-drain");
                drain.setDaemon(true);
                drain.start();
                error = null;
            }
            lastRgb = -1;
            lastBright = -1;
            off = false;
            return true;
        } catch (Exception e) {
            error = "Yeelight " + ip + ": " + explain(e);
            close();
            return false;
        }
    }

    /** Opens in the background if closed; retried at most every 10 s. */
    @Override public void openAsync() {
        long now = System.currentTimeMillis();
        if (disposed || mode != CLOSED || connecting || now - lastTry < 10_000) return;
        lastTry = now;
        connecting = true;
        new Thread(() -> {
            try {
                open();
                if (disposed) close();   // the run ended while we were connecting
            } finally {
                connecting = false;
            }
        }, "yeelight-" + ip).start();
    }

    @Override public boolean isOpen() {
        return mode != CLOSED && out != null;
    }

    @Override public void dispose() {
        disposed = true;
        close();
        writer.shutdown();
    }

    @Override public String error() {
        return error;
    }

    @Override public String note() {
        return mode == BASIC ? L.t("Yeelight in modalità lenta (la modalità musica non risponde)", "Yeelight in slow mode (music mode does not respond)") : null;
    }

    int mode() {
        return mode;
    }

    /** Shows a colour; brightness comes from the colour itself. */
    @Override public void show(int r, int g, int b) {
        if (!isOpen()) { openAsync(); return; }
        long now = System.currentTimeMillis();
        int max = Math.max(r, Math.max(g, b));
        int bright, rgb;
        if (max < 4) {
            bright = 1;
            rgb = lastRgb > 0 ? lastRgb : 0xFFFFFF;
        } else {
            bright = Math.max(1, Math.min(100, Math.round(max * 100f / 255f)));
            rgb = ((r * 255 / max) << 16) | ((g * 255 / max) << 8) | (b * 255 / max);
            if (rgb == 0) rgb = 0xFFFFFF;
        }
        boolean colorChanged = rgb != lastRgb;
        boolean brightChanged = Math.abs(bright - lastBright) >= 2;
        StringBuilder cmd = new StringBuilder();
        if (mode == MUSIC) {
            // music mode: no limit, but keep it to ~16 per second so the light can keep up
            if (now - lastSend < 60 || (!colorChanged && !brightChanged && now - lastSend < 1000)) return;
            if (colorChanged) cmd.append(command("set_rgb", rgb + ",\"sudden\",0"));
            if (brightChanged || !colorChanged) cmd.append(command("set_bright", bright + ",\"sudden\",0"));
        } else {
            // normal mode: the light accepts ~1 command per second, so send one smooth change at a time
            if (now - lastSend < 1100) return;
            if (brightChanged && (!colorChanged || Math.abs(bright - lastBright) > 25)) {
                cmd.append(command("set_bright", bright + ",\"smooth\",500"));
                colorChanged = false;
            } else if (colorChanged) {
                cmd.append(command("set_rgb", rgb + ",\"smooth\",500"));
                brightChanged = false;
            } else return;
        }
        if (send(cmd.toString())) {
            lastSend = now;
            if (colorChanged) lastRgb = rgb;
            if (brightChanged || mode == MUSIC) lastBright = bright;
        }
    }

    private final java.util.concurrent.ExecutorService writer = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "yeelight-write");
        t.setDaemon(true);
        return t;
    });
    private volatile long busySince;

    /**
     * Writes on a separate thread: if the Wi-Fi drops, a TCP write can block for minutes and must never
     * stall the audio. While a write is pending new commands are dropped; after 3 s the link is reset.
     */
    private boolean send(String text) {
        OutputStream o = out;
        if (o == null) return false;
        long now = System.currentTimeMillis();
        if (busySince != 0) {
            if (now - busySince > 3000) {
                error = "Yeelight " + ip + L.t(": non risponde, riprovo", ": not responding, retrying");
                close();
            }
            return false;
        }
        busySince = now;
        byte[] data = text.getBytes(StandardCharsets.US_ASCII);
        try {
            writer.execute(() -> {
                try {
                    o.write(data);
                    o.flush();
                } catch (Exception e) {
                    error = "Yeelight " + ip + L.t(": collegamento perso, riprovo", ": connection lost, retrying");
                    close();
                } finally {
                    busySince = 0;
                }
            });
        } catch (Exception e) {
            busySince = 0;
            return false;
        }
        return true;
    }

    /** Turns the light off (true) or back on (false) while staying connected. */
    @Override public void setOff(boolean wantOff) {
        if (!isOpen()) { if (!wantOff) openAsync(); return; }
        if (wantOff == off) return;
        if (send(command("set_power", wantOff ? "\"off\",\"smooth\",300" : "\"on\",\"smooth\",300"))) {
            off = wantOff;
            lastRgb = -1;
            lastSend = System.currentTimeMillis();
        }
    }

    @Override public synchronized void close() {
        mode = CLOSED;
        out = null;
        try {
            if (control != null && !control.isClosed() && music != null) {
                control.getOutputStream().write(command("set_music", "0").getBytes(StandardCharsets.US_ASCII));
            }
        } catch (Exception ignored) { }
        try { if (music != null) music.close(); } catch (Exception ignored) { }
        try { if (control != null) control.close(); } catch (Exception ignored) { }
        music = null;
        control = null;
    }

    /** One-shot power command over a fresh connection (used by the UI on/off switch). */
    static void power(String ip, boolean on) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, PORT), 3000);
            YeelightLink tmp = new YeelightLink(ip);
            s.getOutputStream().write(tmp.command("set_power", on ? "\"on\",\"smooth\",300" : "\"off\",\"smooth\",300")
                    .getBytes(StandardCharsets.US_ASCII));
            s.getOutputStream().flush();
        } catch (Exception ignored) { }
    }

    /**
     * Step-by-step check used by the "Prova luce" button: connection, a red/green/blue flash,
     * then music mode. Returns a short Italian report.
     */
    static String test(String ip) {
        StringBuilder rep = new StringBuilder();
        YeelightLink tmp = new YeelightLink(ip);
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, PORT), 3000);
            s.setSoTimeout(2000);
            rep.append(L.t("✓ Collegata (Controllo LAN attivo)\n", "✓ Connected (LAN Control is on)\n"));
            OutputStream o = s.getOutputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
            o.write(tmp.command("get_prop", "\"power\",\"bright\",\"rgb\",\"color_mode\"").getBytes(StandardCharsets.US_ASCII));
            o.flush();
            String props = readResult(in);
            rep.append(props != null ? L.t("✓ Risponde: ", "✓ Answers: ") + props + "\n" : L.t("✗ Non risponde ai comandi\n", "✗ Does not answer commands\n"));
            o.write(tmp.command("set_power", "\"on\",\"sudden\",0").getBytes(StandardCharsets.US_ASCII));
            o.write(tmp.command("set_rgb", "16711680,\"sudden\",0").getBytes(StandardCharsets.US_ASCII));
            o.flush();
            readResult(in);
            String rgbReply = readResult(in);
            rep.append(rgbReply != null && rgbReply.contains("ok") ? L.t("✓ Colore rosso accettato\n", "✓ Red colour accepted\n")
                    : L.t("✗ Colore rifiutato: ", "✗ Colour refused: ") + rgbReply + "\n");
            Thread.sleep(1200);
            o.write(tmp.command("set_rgb", "65280,\"smooth\",300").getBytes(StandardCharsets.US_ASCII));
            o.flush();
            readResult(in);
        } catch (Exception e) {
            return rep.append("✗ ").append(explain(e)).toString().trim();
        }
        YeelightLink link = new YeelightLink(ip);
        if (link.open()) {
            if (link.mode() == MUSIC) {
                rep.append(L.t("✓ Modalità musica attiva (veloce)\n", "✓ Music mode on (fast)\n"));
                for (int i = 0; i < 6; i++) {
                    link.lastSend = 0;
                    link.show(i % 2 == 0 ? 0 : 255, 0, i % 2 == 0 ? 255 : 0);
                    try { Thread.sleep(250); } catch (InterruptedException ignored) { }
                }
            } else {
                rep.append(L.t("! Modalità musica non disponibile: userò quella lenta (~1 cambio al secondo)\n", "! Music mode not available: using slow mode (~1 change per second)\n"));
            }
            link.close();
        } else {
            rep.append("✗ ").append(link.error).append('\n');
        }
        return rep.toString().trim();
    }

    private static String readResult(BufferedReader in) {
        try {
            for (int i = 0; i < 5; i++) {
                String line = in.readLine();
                if (line == null) return null;
                if (line.contains("\"result\"") || line.contains("\"error\"")) {
                    int a = line.indexOf("\"result\"");
                    return a >= 0 ? line.substring(a + 9).replace("}", "").trim() : line;
                }
            }
        } catch (Exception ignored) { }
        return null;
    }
}
