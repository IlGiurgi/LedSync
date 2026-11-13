import com.ledsync.phone.*;
// Synthetic 128 BPM dance track with a build-up and a drop, a breakdown whose bass fades back in slowly,
// a pause and a new song that starts with full bass. Exactly one drop must be found, right at the drop,
// and none on a steady mix. Also checks that the drop layer dims during the build-up and flashes on the drop.
public class DropTest {
  static final int SR = 48000, HOP = 960;

  public static void main(String[] x) throws Exception {
    java.lang.reflect.Field fd = AudioAnalyzer.class.getDeclaredField("drops"); fd.setAccessible(true);
    Class<?> dc = Class.forName("com.ledsync.phone.DropDetector");
    java.lang.reflect.Field fState = dc.getDeclaredField("state"), fProg = dc.getDeclaredField("progress"),
        fCount = dc.getDeclaredField("count"), fSince = dc.getDeclaredField("sinceDrop");
    fState.setAccessible(true); fProg.setAccessible(true); fCount.setAccessible(true); fSince.setAccessible(true);

    AudioAnalyzer a = new AudioAnalyzer(SR); DirectEffects fx = new DirectEffects();
    Object d = fd.get(a);
    java.util.Random r = new java.util.Random(5); short[] b = new short[HOP]; byte[] pk = new byte[44];
    int n = 60; byte[] px = new byte[n * 3];
    long t = 0; double hp = 0, prevN = 0;
    int frames = 86 * 50, firstDrop = -1, buildFrames = 0; double maxProg = 0, buildBri = 0, normBri = 0, dropBri = 0;
    int nb = 0, nn = 0;
    StringBuilder tl = new StringBuilder();
    for (int f = 0; f < frames; f++) {
      for (int i = 0; i < HOP; i++, t++) {
        double s = t / (double) SR, beat = s % 0.46875, six = s % 0.1171875;
        double kick = beat < 0.15 ? Math.exp(-beat * 25) * Math.sin(2 * Math.PI * (50 + 80 * Math.exp(-beat * 40)) * beat) : 0;
        double bass = 0.35 * Math.sin(2 * Math.PI * new double[]{55, 55, 73.4, 65.4}[(int) (s / 2) % 4] * s) * (0.6 + 0.4 * Math.exp(-beat * 6));
        double hat = six < 0.03 ? 0.12 * (r.nextDouble() - 0.5) * Math.exp(-six * 150) : 0;
        double pad = 0.05 * (Math.sin(2 * Math.PI * 440 * s) + Math.sin(2 * Math.PI * 554 * s) + Math.sin(2 * Math.PI * 659 * s));
        double noise = r.nextDouble() - 0.5; hp = noise - prevN; prevN = noise;   // crude high-pass: riser / snare
        double v;
        if (s < 16) v = 0.7 * (kick + bass) + hat + pad;                            // intro, full
        else if (s < 28) {                                                          // build-up: no kick, no bass
          double k = (s - 16) / 12;
          double div = k < 0.5 ? 0.46875 : (k < 0.8 ? 0.234375 : 0.1171875);       // snare roll speeds up
          double sn = s % div;
          double snare = s < 27.5 && sn < 0.06 ? (0.1 + 0.25 * k) * hp * Math.exp(-sn * 60) : 0;
          v = pad * (1 - 0.5 * k) + (0.02 + 0.25 * k) * hp + snare;
        } else if (s < 44) v = 0.9 * (kick + bass) + hat + pad;                     // THE DROP
        else if (s < 56) v = pad + 0.4 * hat;                                       // breakdown
        else if (s < 64) { double k = (s - 56) / 8; v = k * 0.7 * (kick + bass) + hat + pad; } // slow fade-in
        else if (s < 68) v = 0;                                                     // pause
        else v = 0.7 * (kick + bass) + hat + pad;                                   // next song, bass at once
        b[i] = (short) Math.max(-32768, Math.min(32767, v * 32767 * 0.6));
      }
      a.process(b, HOP, pk); fx.update(a);
      fx.render(DirectEffects.ENERGY, n, 0, px);
      fx.applyDrop(px, n);
      int st = fState.getInt(d); double p = fProg.getDouble(d); int cnt = fCount.getInt(d);
      if (cnt > 0 && firstDrop < 0) firstDrop = f;
      double bri = 0; for (byte v : px) bri += v & 255; bri /= px.length;
      double s = f / 50.0;
      if (st == 1) { buildFrames++; maxProg = Math.max(maxProg, p); }
      if (s > 25 && s < 27.5) { buildBri += bri; nb++; }
      if (s > 10 && s < 15) { normBri += bri; nn++; }
      if (firstDrop >= 0 && f < firstDrop + 3) dropBri = Math.max(dropBri, bri);
      if (f % 50 == 0) tl.append(st == 0 ? '.' : st == 1 ? 'b' : 'D');
    }
    int count = fCount.getInt(d);
    System.out.println("timeline (1 char = 1 s): " + tl);
    System.out.printf("drops %d, first at %.2f s (real drop 28.00 s), build-up %.1f s, max progress %.2f%n",
        count, firstDrop / 50.0, buildFrames / 50.0, maxProg);
    System.out.printf("average pixel: normal %.0f, late build-up %.0f, brightest of the first 3 frames after the drop %.0f%n",
        normBri / nn, buildBri / nb, dropBri);
    boolean ok = count == 1 && firstDrop >= 1400 && firstDrop <= 1408 && buildFrames > 200 && maxProg > 0.5
        && buildBri / nb < normBri / nn && dropBri > 230;

    // steady mix for 3 minutes: no drops at all
    AudioAnalyzer a2 = new AudioAnalyzer(SR); Object d2 = fd.get(a2); t = 0;
    for (int f = 0; f < 9000; f++) {
      for (int i = 0; i < HOP; i++, t++) {
        double s = t / (double) SR, beat = s % 0.5, six = s % 0.125;
        double kick = beat < 0.15 ? 0.6 * Math.exp(-beat * 25) * Math.sin(2 * Math.PI * (50 + 80 * Math.exp(-beat * 40)) * beat) : 0;
        double bass = 0.2 * Math.sin(2 * Math.PI * new double[]{55, 55, 73.4, 65.4}[(int) (s / 2) % 4] * s);
        double chord = Math.exp(-(s % 1.0) * 3) * 0.08 * (Math.sin(2 * Math.PI * 262 * s) + Math.sin(2 * Math.PI * 330 * s));
        double hat = six < 0.04 ? 0.1 * (r.nextDouble() - 0.5) * Math.exp(-six * 80) : 0;
        double dyn = 0.6 + 0.4 * Math.sin(2 * Math.PI * s / 40);   // slow verse/chorus loudness changes
        b[i] = (short) Math.max(-32768, Math.min(32767, dyn * (kick + bass + chord + hat) * 32767 * 0.7));
      }
      a2.process(b, HOP, pk);
    }
    int steady = fCount.getInt(d2);
    System.out.println("steady mix, 3 min: drops " + steady);
    ok &= steady == 0;
    System.out.println(ok ? "OK" : "FAIL");
    if (!ok) System.exit(1);
  }
}
