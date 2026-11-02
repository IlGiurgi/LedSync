import com.ledsync.phone.*;
// More realistic mix: varying-velocity kick, bass, strummed chords with dynamics, a vocal-like
// vibrato line, noisy hi-hats/shaker with random accents. Reports average bass/mid/high levels.
public class RealisticMixTest {
  public static void main(String[] x){
    if(x.length>0){Tuning.gate=Double.parseDouble(x[0]);Tuning.bassDb=Double.parseDouble(x[1]);}
    int SR=48000,HOP=960; AudioAnalyzer a=new AudioAnalyzer(SR); DirectEffects fx=new DirectEffects();
    java.util.Random r=new java.util.Random(7); short[] b=new short[HOP]; byte[] pk=new byte[44]; long t=0;
    double sb=0,sm=0,sh=0; int n=0, midOn=0, highOn=0; double hatAmp=0.1;
    for(int f=0;f<1500;f++){
      for(int i=0;i<HOP;i++,t++){ double s=t/(double)SR, beat=s%0.5, six=s%0.125;
        if(i==0 && six<0.021) hatAmp=0.04+0.12*r.nextDouble();
        double kick= beat<0.15? 0.6*Math.exp(-beat*25)*Math.sin(2*Math.PI*(50+80*Math.exp(-beat*40))*beat):0;
        double bass=0.2*Math.sin(2*Math.PI*new double[]{55,55,73.4,65.4}[(int)(s/2)%4]*s);
        double chordEnv=Math.exp(-(s%1.0)*3)*0.08;
        double chord=chordEnv*(Math.sin(2*Math.PI*262*s)+Math.sin(2*Math.PI*330*s)+Math.sin(2*Math.PI*392*s)+0.5*Math.sin(2*Math.PI*784*s));
        double voxF=440*Math.pow(2,new int[]{0,2,4,5,7,5,4,2}[(int)(s*2)%8]/12.0)*(1+0.01*Math.sin(2*Math.PI*5*s));
        double voxEnv=0.05*(0.5+0.5*Math.sin(2*Math.PI*0.25*s));
        double vox=voxEnv*(Math.sin(2*Math.PI*voxF*s)+0.5*Math.sin(4*Math.PI*voxF*s)+0.3*Math.sin(6*Math.PI*voxF*s));
        double hat= six<0.04? hatAmp*(r.nextDouble()-0.5)*Math.exp(-six*80):0;
        double shaker=0.015*(r.nextDouble()-0.5);
        b[i]=(short)Math.max(-32768,Math.min(32767,(kick+bass+chord+vox+hat+shaker)*32767*0.7)); }
      a.process(b,HOP,pk); fx.update(a);
      if(f>=200){ sb+=fx.bass(); sm+=fx.mid(); sh+=fx.high(); n++; if(fx.mid()>0.3)midOn++; if(fx.high()>0.3)highOn++; }
    }
    System.out.printf("avg bass %.2f  mid %.2f  high %.2f   mid>0.3: %d%%  high>0.3: %d%%%n", sb/n, sm/n, sh/n, 100*midOn/n, 100*highOn/n);
  }
}
