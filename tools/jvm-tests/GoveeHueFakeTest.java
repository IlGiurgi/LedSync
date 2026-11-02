package com.ledsync.phone;
import java.net.*; import java.util.*; import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;
// Fake Govee (UDP 4003 on 127.0.0.1) and fake Hue bridge (HTTP on 127.0.0.1:8099 is not possible: Hue uses port 80,
// so HueLink is pointed at "127.0.0.1:8099" as the bridge address).
public class GoveeHueFakeTest {
  public static void main(String[] a) throws Exception {
    // ---- Govee
    List<String> glog = Collections.synchronizedList(new ArrayList<>());
    DatagramSocket dev = new DatagramSocket(4003, InetAddress.getByName("127.0.0.1"));
    Thread gt = new Thread(() -> { byte[] b = new byte[1024]; while (!dev.isClosed()) { try { DatagramPacket p = new DatagramPacket(b, b.length); dev.receive(p); glog.add(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8)); } catch (Exception e) { return; } } });
    gt.start();
    GoveeLink g = new GoveeLink("127.0.0.1");
    long t0 = System.currentTimeMillis();
    while (System.currentTimeMillis() - t0 < 2000) { long t = System.currentTimeMillis(); g.show((int)(t/200%2*255), 0, (int)(255 - t/200%2*255)); Thread.sleep(20); }
    g.setOff(true); Thread.sleep(100); g.close(); dev.close();
    System.out.println("govee msgs in 2s: " + glog.size() + "  first: " + glog.get(0) + "  second: " + glog.get(1) + "  last: " + glog.get(glog.size()-1));
    if (glog.size() < 10 || glog.size() > 90) throw new AssertionError("govee rate");
    // ---- Hue
    List<String> hlog = Collections.synchronizedList(new ArrayList<>());
    HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 8099), 0);
    srv.createContext("/api", ex -> {
      String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      String path = ex.getRequestURI().getPath(); String reply;
      if (ex.getRequestMethod().equals("POST")) reply = "[{\"success\":{\"username\":\"abc123\"}}]";
      else if (path.endsWith("/lights")) reply = "{\"1\":{\"name\":\"Soggiorno\",\"state\":{\"xy\":[0.3,0.3]}},\"2\":{\"name\":\"Bianca\",\"state\":{\"bri\":1}}}";
      else { hlog.add(System.currentTimeMillis() + " " + ex.getRequestMethod() + " " + path + " " + body); reply = "[{\"success\":{}}]"; }
      byte[] r = reply.getBytes(StandardCharsets.UTF_8); ex.sendResponseHeaders(200, r.length); ex.getResponseBody().write(r); ex.close(); });
    srv.start();
    String br = "127.0.0.1:8099";
    String user = HueLink.pair(br); System.out.println("hue pair -> " + user + "  lights -> " + HueLink.lights(br, user));
    HueLink.users.put(br, user);
    HueLink h1 = new HueLink(br, "1"), h2 = new HueLink(br, "3");
    t0 = System.currentTimeMillis();
    while (System.currentTimeMillis() - t0 < 3000) { long t = System.currentTimeMillis(); int v = (int)(t/150%2*255); h1.show(v, 0, 255-v); h2.show(0, v, 0); Thread.sleep(20); }
    h1.close(); h2.close(); srv.stop(0);
    long l1 = hlog.stream().filter(s -> s.contains("/lights/1/")).count(), l3 = hlog.stream().filter(s -> s.contains("/lights/3/")).count();
    System.out.println("hue cmds in 3s: light1=" + l1 + " light3=" + l3 + "  sample: " + hlog.get(1).substring(14));
    if (l1 + l3 > 34 || l1 < 8) throw new AssertionError("hue rate");
    double[] red = HueLink.xy(0xFF0000); System.out.printf("red xy %.3f,%.3f%n", red[0], red[1]);
    System.out.println("ALL OK"); System.exit(0);
  }
}
