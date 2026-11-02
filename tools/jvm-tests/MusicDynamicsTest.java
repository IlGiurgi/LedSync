import com.ledsync.phone.AudioAnalyzer;
public class MusicDynamicsTest {
  // synthetic 120 BPM track: kick on beats, bass line, hihat 8ths, chord pad; measures how much each GEQ band moves
  public static void main(String[] x){
    int SR=48000,HOP=960; AudioAnalyzer a=new AudioAnalyzer(SR); com.ledsync.phone.Tuning.rangeDb=Double.parseDouble(x.length>0?x[0]:"24"); if(x.length>1) com.ledsync.phone.Tuning.bassDb=Double.parseDouble(x[1]); if(x.length>2) com.ledsync.phone.Tuning.gate=Double.parseDouble(x[2]); if(x.length>3) com.ledsync.phone.Tuning.decay=Double.parseDouble(x[3]); if(x.length>4) com.ledsync.phone.Tuning.beatRatio=Double.parseDouble(x[4]);
    java.util.Random r=new java.util.Random(3); short[] b=new short[HOP]; byte[] pk=new byte[44];
    int frames=1000; double[] sum=new double[16], sq=new double[16]; double vs=0,vq=0; int peaks=0; long t=0;
    for(int f=0;f<frames;f++){ for(int i=0;i<HOP;i++,t++){ double s=t/(double)SR; double beat=s%0.5, eighth=s%0.25;
        double kick= beat<0.15? 0.7*Math.exp(-beat*25)*Math.sin(2*Math.PI*(50+80*Math.exp(-beat*40))*beat):0;
        double bassF= new double[]{55,55,73.4,65.4}[(int)(s/2)%4]; double bass=0.25*Math.sin(2*Math.PI*bassF*s)*(0.6+0.4*Math.exp(-beat*6));
        double hat= eighth<0.03? 0.15*(r.nextDouble()-0.5)*Math.exp(-eighth*150):0;
        double pad=0.06*(Math.sin(2*Math.PI*440*s)+Math.sin(2*Math.PI*554*s)+Math.sin(2*Math.PI*659*s));
        double v=kick+bass+hat+pad; b[i]=(short)Math.max(-32768,Math.min(32767,v*32767*0.8)); }
      a.process(b,HOP,pk); if(f<100) continue; if(pk[16]==1)peaks++;
      for(int k=0;k<16;k++){ double v=a.lastBands[k]; sum[k]+=v; sq[k]+=v*v; } vs+=a.lastVolume; vq+=a.lastVolume*a.lastVolume; }
    int n=frames-100; StringBuilder m=new StringBuilder("mean:"), d=new StringBuilder("std :");
    for(int k=0;k<16;k++){ double mu=sum[k]/n; m.append(String.format("%4.0f",mu)); d.append(String.format("%4.0f",Math.sqrt(Math.max(0,sq[k]/n-mu*mu)))); }
    double vmu=vs/n; System.out.println(m+"\n"+d+String.format("\nvolume mean %.0f std %.0f  peaks %d/36",vmu,Math.sqrt(vq/n-vmu*vmu),peaks));
  }
}
