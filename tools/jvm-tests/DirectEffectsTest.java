import com.ledsync.phone.*;
public class DirectEffectsTest {
  public static void main(String[] x) throws Exception {
    int SR=48000,HOP=960,N=300; AudioAnalyzer a=new AudioAnalyzer(SR); DirectEffects fx=new DirectEffects();
    java.util.Random r=new java.util.Random(3); short[] b=new short[HOP]; byte[] pk=new byte[44]; byte[] px=new byte[N*3]; long t=0;
    double onKick=0, offKick=0; int nOn=0,nOff=0, dark=0, frames=0; double maxB=0;
    for(int f=0;f<600;f++){ // 10 s music, then 3 s silence
      boolean music=f<500;
      for(int i=0;i<HOP;i++,t++){ double s=t/(double)SR, beat=s%0.5, eighth=s%0.25;
        double kick= beat<0.15? 0.7*Math.exp(-beat*25)*Math.sin(2*Math.PI*(50+80*Math.exp(-beat*40))*beat):0;
        double bassF=new double[]{55,55,73.4,65.4}[(int)(s/2)%4]; double bass=0.25*Math.sin(2*Math.PI*bassF*s)*(0.6+0.4*Math.exp(-beat*6));
        double hat= eighth<0.03? 0.15*(r.nextDouble()-0.5)*Math.exp(-eighth*150):0;
        double pad=0.06*(Math.sin(2*Math.PI*440*s)+Math.sin(2*Math.PI*554*s)+Math.sin(2*Math.PI*659*s));
        b[i]=(short)(music? Math.max(-32768,Math.min(32767,(kick+bass+hat+pad)*32767*0.8)) : 0); }
      a.process(b,HOP,pk); fx.update(a); fx.render(0,N,0,px);
      double bri=(px[0]&255)/255.0;
      if(f>=100 && music){ double beatPos=((t-HOP/2)/(double)SR)%0.5; if(beatPos<0.06){onKick+=bri;nOn++;} else if(beatPos>0.25&&beatPos<0.45){offKick+=bri;nOff++;} frames++; if(bri<0.1)dark++; maxB=Math.max(maxB,bri);}
      if(f==510||f==520||f==599) System.out.printf("silence +%d ms: pulse brightness %.2f%n",(f-499)*20,bri);
    }
    System.out.printf("Pulse: on kick avg %.2f, between kicks avg %.2f, dark frames %d%% , max %.2f%n", onKick/nOn, offKick/nOff, 100*dark/frames, maxB);
    // energy + wave + vu + spectrum quick sanity on silence: all zero
    for(int e=1;e<5;e++){ fx.render(e,N,0,px); int sum=0; for(byte v:px) sum+=v&255; System.out.println(DirectEffects.NAMES[e]+" in silence, sum="+sum); }
    // DNRGB packets
  }
}
