package com.ledsync.phone;
import java.io.*; import java.net.*; import java.util.*;
// Fake Yeelight that replies like the real one. musicOk=false simulates a light/phone where music mode never calls back.
public class YeelightModesTest {
  static List<String> log = Collections.synchronizedList(new ArrayList<>());
  static ServerSocket fake(boolean musicOk) throws Exception {
    ServerSocket dev = new ServerSocket(55443, 5, InetAddress.getByName("127.0.0.1"));
    new Thread(() -> { while (!dev.isClosed()) { try { Socket c = dev.accept(); new Thread(() -> serve(c, musicOk)).start(); } catch (Exception e) { return; } } }).start();
    return dev;
  }
  static void serve(Socket c, boolean musicOk) { try {
    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream())); PrintWriter w = new PrintWriter(c.getOutputStream(), true); String line;
    while ((line = r.readLine()) != null) { log.add("CTRL " + line);
      String id = line.substring(6, line.indexOf(','));
      if (line.contains("get_prop")) w.print("{\"id\":" + id + ",\"result\":[\"on\",\"50\",\"16711680\",\"1\"]}\r\n");
      else w.print("{\"id\":" + id + ",\"result\":[\"ok\"]}\r\n");
      w.flush();
      if (musicOk && line.contains("\"set_music\"") && line.contains("[1,")) {
        String[] p = line.substring(line.indexOf("[1,")+3, line.indexOf("]")).split(",");
        String host = p[0].replace("\"",""); int port = Integer.parseInt(p[1].trim());
        new Thread(() -> { try (Socket m = new Socket(host, port)) {
          BufferedReader mr = new BufferedReader(new InputStreamReader(m.getInputStream())); String l;
          while ((l = mr.readLine()) != null) log.add("MUSIC " + l); } catch (Exception e) {} }).start();
      } } } catch (Exception e) { } }
  static long count(String p) { synchronized (log) { return log.stream().filter(s -> s.startsWith(p)).count(); } }
  public static void main(String[] a) throws Exception {
    // 1) refused (LAN control off)
    YeelightLink y = new YeelightLink("127.0.0.1");
    System.out.println("refused open=" + y.open() + " err=" + y.error);
    System.out.println("refused test:\n" + YeelightLink.test("127.0.0.1"));
    // 2) music mode
    ServerSocket d = fake(true);
    y = new YeelightLink("127.0.0.1");
    System.out.println("music open=" + y.open() + " mode=" + y.mode());
    for (int i = 0; i < 20; i++) { y.show(i % 2 * 255, 0, 255 - i % 2 * 255); Thread.sleep(70); }
    Thread.sleep(200); y.close(); Thread.sleep(100);
    System.out.println("music cmds=" + count("MUSIC") + " ctrl=" + count("CTRL"));
    if (count("MUSIC") < 15) throw new AssertionError("music mode too few commands");
    log.clear();
    System.out.println("test report:\n" + YeelightLink.test("127.0.0.1"));
    d.close(); Thread.sleep(200); log.clear();
    // 3) basic fallback: music never calls back
    d = fake(false);
    y = new YeelightLink("127.0.0.1");
    long t0 = System.currentTimeMillis();
    System.out.println("basic open=" + y.open() + " mode=" + y.mode() + " took=" + (System.currentTimeMillis() - t0) + "ms");
    if (y.mode() != YeelightLink.BASIC) throw new AssertionError("expected basic");
    log.clear();
    t0 = System.currentTimeMillis();
    while (System.currentTimeMillis() - t0 < 5000) { y.show((int)((System.currentTimeMillis()/300)%2*255), 50, 0); Thread.sleep(20); }
    System.out.println("basic cmds in 5s=" + count("CTRL"));
    synchronized (log) { log.forEach(System.out::println); }
    if (count("CTRL") > 6 || count("CTRL") < 3) throw new AssertionError("basic rate wrong");
    y.close(); d.close();
    // 4) async open does not block
    YeelightLink z = new YeelightLink("127.0.0.1");
    t0 = System.currentTimeMillis(); z.show(255, 0, 0);
    System.out.println("async show returned in " + (System.currentTimeMillis() - t0) + "ms");
    Thread.sleep(500); System.out.println("async err=" + z.error);
    System.out.println("ALL OK"); System.exit(0);
  }
}
