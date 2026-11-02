package com.ledsync.phone;
import java.io.*; import java.net.*; import java.util.*;
// Fake Yeelight on 127.0.0.1:55443: logs control commands, connects back on set_music, logs music commands.
public class YeelightFakeDeviceTest {
  public static void main(String[] a) throws Exception {
    List<String> log = Collections.synchronizedList(new ArrayList<>());
    ServerSocket dev = new ServerSocket(55443, 5, InetAddress.getByName("127.0.0.1"));
    Thread t = new Thread(() -> { try (Socket c = dev.accept()) {
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream())); String line;
        while ((line = r.readLine()) != null) { log.add("CTRL " + line);
          if (line.contains("\"set_music\"") && line.contains("[1,")) {
            String[] p = line.substring(line.indexOf("[1,")+3, line.indexOf("]")).split(",");
            String host = p[0].replace("\"",""); int port = Integer.parseInt(p[1].trim());
            new Thread(() -> { try (Socket m = new Socket(host, port)) {
              BufferedReader mr = new BufferedReader(new InputStreamReader(m.getInputStream())); String l;
              while ((l = mr.readLine()) != null) log.add("MUSIC " + l); } catch (Exception e) {} }).start();
          } } } catch (Exception e) { } });
    t.start();
    YeelightLink y = new YeelightLink("127.0.0.1");
    System.out.println("open: " + y.open() + " " + y.error);
    y.show(255, 0, 0); Thread.sleep(80); y.show(0, 128, 0); Thread.sleep(80); y.show(0,0,0); Thread.sleep(80);
    y.setOff(true); Thread.sleep(50); y.setOff(false); Thread.sleep(80); y.show(10,20,200);
    Thread.sleep(200); y.close(); Thread.sleep(200);
    synchronized (log) { for (String s : log) System.out.println(s); }
    System.out.println(YeelightLink.header("HTTP/1.1 200 OK\r\nLocation: yeelight://192.168.1.239:55443\r\nname: Divano\r\n", "location"));
    dev.close(); System.exit(0);
  }
}
