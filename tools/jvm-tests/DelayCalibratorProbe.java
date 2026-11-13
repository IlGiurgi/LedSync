package com.ledsync.phone;

/** Opens the package-private signal code of DelayCalibrator to DelayCalibratorTest. */
public final class DelayCalibratorProbe {
    public static final int SR = DelayCalibrator.SR, BEEPS = DelayCalibrator.BEEPS;
    public static final double MIN_CLARITY = DelayCalibrator.MIN_CLARITY;
    private static final float[] CHIRP = DelayCalibrator.chirp();

    public static short[] sequence(double amp) { return DelayCalibrator.sequence(CHIRP, amp); }
    public static int beepFrame(int k) { return DelayCalibrator.beepFrame(k); }
    public static double[] find(short[] rec, int center) {
        return DelayCalibrator.findChirp(rec, rec.length, center - DelayCalibrator.SEARCH_BEFORE, center + DelayCalibrator.SEARCH_AFTER, CHIRP);
    }
    public static double[] combine(double[] d) { return DelayCalibrator.combine(d); }
    public static int suggested(double ms) { return DelayCalibrator.suggestedDelay(ms); }
}
