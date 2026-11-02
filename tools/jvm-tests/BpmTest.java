package com.ledsync.phone;
// Synthetic songs at known tempos: the tracker must find the BPM and tick on the kicks.
public class BpmTest {
  public static void main(String[] x) {
    int SR=48000,HOP=960; boolean ok=true;
    for (double bpm : new double[]{90, 100, 120, 128, 140, 150, 174}) {
      AudioAnalyzer a=new AudioAnalyzer(SR); java.util.Random r=new java.util.Random(7);
      short[] b=new short[HOP]; byte[] pk=new byte[44]; long t=0; double beatLen=60.0/bpm;
      int ticks=0, onKick=0; int frames=50*20;
      for(int f=0;f<frames;f++){
        for(int i=0;i<HOP;i++,t++){ double s=t/(double)SR, ph=s%beatLen, half=s%(beatLen/2);
          double kick= ph<0.15? 0.7*Math.exp(-ph*25)*Math.sin(2*Math.PI*(50+80*Math.exp(-ph*40))*ph):0;
          double snare= (s%(2*beatLen))>beatLen && ph<0.1 ? 0.3*(r.nextDouble()-0.5)*Math.exp(-ph*30):0;
          double hat= half<0.03? 0.12*(r.nextDouble()-0.5)*Math.exp(-half*150):0;
          double bass=0.2*Math.sin(2*Math.PI*55*s);
          double mel=0.08*Math.sin(2*Math.PI*new double[]{440,494,523,587}[(int)(s*2)%4]*s);
          b[i]=(short)Math.max(-32768,Math.min(32767,(kick+snare+hat+bass+mel)*32767*0.8)); }
        a.process(b,HOP,pk);
        if (f>50*10 && a.beats.tick) { ticks++; double ph=(t/(double)SR)%beatLen; double d=Math.min(ph, beatLen-ph); if (d<0.07) onKick++; }
      }
      double got=a.beats.bpm; boolean good=Math.abs(got-bpm)<2 || Math.abs(got-bpm/2)<1.5 || Math.abs(got-bpm*2)<3;
      System.out.printf("%5.0f BPM -> %6.1f  conf %.2f  ticks in last 10s %d (expected %.0f), on the kick %d%n", bpm, got, a.beats.confidence, ticks, 10/beatLen, onKick);
      ok &= good && onKick >= ticks*0.7;
    }
    // silence: no ticks
    AudioAnalyzer a=new AudioAnalyzer(SR); short[] z=new short[HOP]; byte[] pk=new byte[44]; int ticks=0;
    for (int f=0;f<1000;f++){ a.process(z,HOP,pk); if(a.beats.tick) ticks++; }
    System.out.println("silence ticks: "+ticks); ok &= ticks==0;
    System.out.println(ok?"ALL OK":"FAIL");
  }
}
