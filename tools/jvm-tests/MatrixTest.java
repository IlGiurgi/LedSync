import com.ledsync.phone.*;
import java.awt.image.BufferedImage;
// Word clock 11x10 + 4 corner dots: snapshots of a few effects mid-song, and darkness after silence.
public class MatrixTest {
  public static void main(String[] x) throws Exception {
    int SR=48000,HOP=960,W=11,N=114,F=400, S=14; // pixel size in png
    int[] effects={DirectEffects.EQUALIZER, DirectEffects.BANDS, DirectEffects.ENERGY, DirectEffects.PULSE, DirectEffects.POWER, DirectEffects.FIRE, DirectEffects.RAIN, DirectEffects.SCROLL};
    int[] snaps={120,135,150,175};
    BufferedImage img=new BufferedImage(snaps.length*(W*S+10), effects.length*(10*S+10), BufferedImage.TYPE_INT_RGB);
    for(int ei=0;ei<effects.length;ei++){ int e=effects[ei];
      AudioAnalyzer a=new AudioAnalyzer(SR); DirectEffects fx=new DirectEffects(); java.util.Random r=new java.util.Random(3);
      short[] b=new short[HOP]; byte[] pk=new byte[44]; byte[] px=new byte[N*3]; long t=0; int lastLit=-1; double sumB=0; int sn=0;
      for(int f=0;f<F;f++){ boolean music=f<300;
        for(int i=0;i<HOP;i++,t++){ double s=t/(double)SR, beat=s%0.5, eighth=s%0.25;
          double kick= beat<0.15? 0.7*Math.exp(-beat*25)*Math.sin(2*Math.PI*(50+80*Math.exp(-beat*40))*beat):0;
          double bass=0.25*Math.sin(2*Math.PI*55*s)*(0.6+0.4*Math.exp(-beat*6));
          double hat= eighth<0.03? 0.15*(r.nextDouble()-0.5)*Math.exp(-eighth*150):0;
          double mel=0.08*Math.sin(2*Math.PI*new double[]{440,494,523,587}[(int)(s*2)%4]*s);
          b[i]=(short)(music? Math.max(-32768,Math.min(32767,(kick+bass+hat+mel)*32767*0.8)) : 0); }
        a.process(b,HOP,pk); fx.update(a); fx.renderMatrix(e,N,W,0,px);
        int lit=0; double bri=0; for(int i=0;i<N*3;i++){ int v=px[i]&255; bri+=v; if(v>8) lit++; }
        if(music&&f>50) sumB+=bri/(N*3*255.0);
        if(!music&&lit>0) lastLit=f;
        if(sn<snaps.length && f==snaps[sn]){ int ox=sn*(W*S+10), oy=ei*(10*S+10);
          for(int p=0;p<110;p++){ int rgb=((px[p*3]&255)<<16)|((px[p*3+1]&255)<<8)|(px[p*3+2]&255);
            for(int dy=1;dy<S-1;dy++) for(int dx=1;dx<S-1;dx++) img.setRGB(ox+(p%W)*S+dx, oy+(p/W)*S+dy, rgb);} sn++; }
      }
      System.out.printf("%-12s avg bri %.2f  dark after silence: %s%n", DirectEffects.NAMES[e], sumB/250, lastLit<0?"immediately":((lastLit-299)*20)+" ms");
    }
    javax.imageio.ImageIO.write(img,"png",new java.io.File(x[0]));
  }
}
