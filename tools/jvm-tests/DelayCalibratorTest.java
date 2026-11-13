import com.ledsync.phone.DelayCalibratorProbe;

import java.util.Random;

/**
 * Simulates the calibration: the chirp sequence goes through a "speaker" with a known delay,
 * room echo, background noise and quiet music, and the matched filter must find the delay.
 * Compile with DelayCalibrator.java, L.java and tools/jvm-tests/DelayCalibratorProbe.java.
 */
public class DelayCalibratorTest {
    public static void main(String[] args) {
        int fails = 0;
        Random rnd = new Random(1);
        int sr = DelayCalibratorProbe.SR;
        for (int trueMs : new int[]{0, 35, 120, 180, 250, 330, 480, 700}) {
            for (double noise : new double[]{200, 1500, 4000}) {
                short[] seq = DelayCalibratorProbe.sequence(0.7);
                int d = trueMs * sr / 1000;
                short[] rec = new short[seq.length + sr];
                double gain = 0.05 + rnd.nextDouble() * 0.1;          // far, quiet speaker
                for (int i = 0; i < rec.length; i++) {
                    double v = rnd.nextGaussian() * noise
                            + 2500 * Math.sin(2 * Math.PI * 220 * i / sr) * Math.sin(2 * Math.PI * 0.5 * i / sr); // music bed
                    int j = i - d;
                    if (j >= 0 && j < seq.length) v += seq[j] * gain;
                    int e = j - sr * 13 / 1000;                          // a wall echo 13 ms later
                    if (e >= 0 && e < seq.length) v += seq[e] * gain * 0.6;
                    rec[i] = (short) Math.max(-32768, Math.min(32767, Math.round(v)));
                }
                double[] delays = new double[DelayCalibratorProbe.BEEPS];
                double minClarity = 1e9;
                for (int k = 0; k < delays.length; k++) {
                    int sent = DelayCalibratorProbe.beepFrame(k);
                    double[] hit = DelayCalibratorProbe.find(rec, sent);
                    if (hit != null) minClarity = Math.min(minClarity, hit[1]);
                    delays[k] = hit == null || hit[1] < DelayCalibratorProbe.MIN_CLARITY ? Double.NaN : (hit[0] - sent) * 1000.0 / sr;
                }
                double[] c = DelayCalibratorProbe.combine(delays);
                boolean inRange = trueMs <= 750 - 40;
                boolean ok = inRange ? c != null && Math.abs(c[0] - trueMs) <= 1 : c == null || Math.abs(c[0] - trueMs) > 1;
                System.out.printf("true %3d ms noise %5.0f clarity %5.1f -> %s%n", trueMs, noise, minClarity,
                        c == null ? "not found" : String.format("%.1f ms (%d beeps) slider %d", c[0], (int) c[1], DelayCalibratorProbe.suggested(c[0])));
                if (!ok) fails++;
            }
        }
        // pure noise must not produce a result
        short[] noiseOnly = new short[DelayCalibratorProbe.sequence(0.7).length + sr];
        for (int i = 0; i < noiseOnly.length; i++) noiseOnly[i] = (short) Math.round(rnd.nextGaussian() * 3000);
        double[] delays = new double[DelayCalibratorProbe.BEEPS];
        int clear = 0;
        for (int k = 0; k < delays.length; k++) {
            double[] hit = DelayCalibratorProbe.find(noiseOnly, DelayCalibratorProbe.beepFrame(k));
            if (hit != null && hit[1] >= DelayCalibratorProbe.MIN_CLARITY) clear++;
            delays[k] = hit == null || hit[1] < DelayCalibratorProbe.MIN_CLARITY ? Double.NaN : hit[0];
        }
        System.out.println("noise only: " + clear + " false peaks, result " + (DelayCalibratorProbe.combine(delays) == null ? "none (good)" : "WRONG"));
        if (clear > 0 || DelayCalibratorProbe.combine(delays) != null) fails++;
        if (DelayCalibratorProbe.suggested(30) != 0 || DelayCalibratorProbe.suggested(236) != 200 || DelayCalibratorProbe.suggested(900) != 500) fails++;
        System.out.println(fails == 0 ? "OK" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
