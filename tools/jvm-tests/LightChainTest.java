import com.ledsync.phone.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;

// Joined strip: strip A (60 LED) + lamp + strip B (90 LED) as one strip. Checks that every light gets its
// own piece (not the same picture), that the effect really travels from one light into the next, and writes
// a PNG timeline (argument: output file; rows = time, columns = the joined strip, grey lines = light borders).
public class LightChainTest {
  static Object chain; static Method begin, add, active, render, fill, pixels;
  public static void main(String[] x) throws Exception {
    Class<?> c = Class.forName("com.ledsync.phone.LightChain");
    Constructor<?> k = c.getDeclaredConstructor(); k.setAccessible(true); chain = k.newInstance();
    begin = m(c, "begin"); add = m(c, "add", String.class, int.class); active = m(c, "active");
    render = m(c, "render", AudioAnalyzer.class, double.class); fill = m(c, "fill", String.class, byte[].class, int.class);
    pixels = m(c, "pixels");
    String[] keys = {"192.168.1.50", "y:192.168.1.60", "192.168.1.51"}; int[] lens = {60, 30, 90}; int N = 180;
    int[] effects = {DirectEffects.METEOR, DirectEffects.SCROLL, DirectEffects.BPM_CHASE, DirectEffects.SCAN};
    Tuning.mirror = false;
    int SR = 48000, HOP = 960, F = 300, S = 3;
    BufferedImage img = new BufferedImage(effects.length * (N * S + 20), F, BufferedImage.TYPE_INT_RGB);
    boolean ok = true;

    // fewer than two lights: nothing is joined
    begin.invoke(chain); add.invoke(chain, keys[0], 60);
    if ((boolean) active.invoke(chain)) { System.out.println("FAIL: one light must not be joined"); ok = false; }

    for (int ei = 0; ei < effects.length; ei++) {
      Tuning.directEffect = effects[ei];
      AudioAnalyzer a = new AudioAnalyzer(SR); java.util.Random r = new java.util.Random(3);
      short[] b = new short[HOP]; byte[] pk = new byte[44]; long t = 0;
      byte[][] own = {new byte[60 * 3], new byte[30 * 3], new byte[90 * 3]};
      int[] litFrames = new int[3]; int differ = 0, firstLit[] = {-1, -1, -1};
      for (int f = 0; f < F; f++) {
        for (int i = 0; i < HOP; i++, t++) { double s = t / (double) SR, beat = s % 0.5;
          double kick = beat < 0.15 ? 0.7 * Math.exp(-beat * 25) * Math.sin(2 * Math.PI * (50 + 80 * Math.exp(-beat * 40)) * beat) : 0;
          double bass = 0.25 * Math.sin(2 * Math.PI * 55 * s) * (0.6 + 0.4 * Math.exp(-beat * 6));
          double mel = 0.08 * Math.sin(2 * Math.PI * new double[]{440, 494, 523, 587}[(int) (s * 2) % 4] * s);
          b[i] = (short) Math.max(-32768, Math.min(32767, (kick + bass + mel + 0.02 * (r.nextDouble() - .5)) * 32767 * 0.8)); }
        a.process(b, HOP, pk);
        begin.invoke(chain);
        for (int d = 0; d < 3; d++) add.invoke(chain, keys[d], lens[d]);
        render.invoke(chain, a, 0.0);
        byte[] whole = (byte[]) pixels.invoke(chain);
        int at = 0;
        for (int d = 0; d < 3; d++) {
          if (!(boolean) fill.invoke(chain, keys[d], own[d], lens[d])) { System.out.println("FAIL: not filled " + keys[d]); ok = false; }
          for (int i = 0; i < lens[d] * 3; i++) if (own[d][i] != whole[at * 3 + i]) { System.out.println("FAIL: piece mismatch"); ok = false; break; }
          int lit = 0; for (byte v : own[d]) if ((v & 255) > 20) lit++;
          if (lit > 0) { litFrames[d]++; if (firstLit[d] < 0) firstLit[d] = f; }
          at += lens[d];
        }
        // strip A and the first 60 LEDs of strip B would be identical if the effect just repeated
        for (int i = 0; i < 60 * 3; i++) if (own[0][i] != own[2][i]) { differ++; break; }
        int ox = ei * (N * S + 20);
        for (int p = 0; p < N; p++) {
          int rgb = ((whole[p * 3] & 255) << 16) | ((whole[p * 3 + 1] & 255) << 8) | (whole[p * 3 + 2] & 255);
          for (int dx = 0; dx < S; dx++) img.setRGB(ox + p * S + dx, f, rgb);
        }
        for (int edge : new int[]{60, 90}) img.setRGB(ox + edge * S, f, 0x555555);
      }
      boolean travels = litFrames[0] > 0 && litFrames[1] > 0 && litFrames[2] > 0 && differ > F / 2;
      ok &= travels;
      System.out.printf("%-10s lit frames A/lamp/B: %d/%d/%d  first lit: %d/%d/%d  frames where A != B: %d  %s%n",
          DirectEffects.NAMES[effects[ei]], litFrames[0], litFrames[1], litFrames[2], firstLit[0], firstLit[1], firstLit[2], differ,
          travels ? "OK" : "FAIL");
    }
    if (x.length > 0) javax.imageio.ImageIO.write(img, "png", new java.io.File(x[0]));
    System.out.println(ok ? "ALL OK" : "SOME FAILED");
    if (!ok) System.exit(1);
  }
  static Method m(Class<?> c, String n, Class<?>... p) throws Exception { Method m = c.getDeclaredMethod(n, p); m.setAccessible(true); return m; }
}
