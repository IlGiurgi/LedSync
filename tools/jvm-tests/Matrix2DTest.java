import com.ledsync.phone.*;
import java.awt.image.BufferedImage;
// Word clock 11x10 + 4 minute dots: every effect with its own 2D version, mid-song snapshots in a PNG,
// plus checks that it lights up with music, is not just rings from the centre, and goes dark after the song.
public class Matrix2DTest {
  public static void main(String[] x) throws Exception {
    int SR=48000,HOP=960,W=11,H=10,N=114,F=420,S=12;
    int[] effects=DirectEffects.NATIVE_2D;
    int[] snaps={110,118,126,134,142,150,165,180};
    BufferedImage img=new BufferedImage(snaps.length*(W*S+8), effects.length*(H*S+8), BufferedImage.TYPE_INT_RGB);
    boolean ok=true;
    for(int ei=0;ei<effects.length;ei++){ int e=effects[ei];
      AudioAnalyzer a=new AudioAnalyzer(SR); DirectEffects fx=new DirectEffects(); java.util.Random r=new java.util.Random(3);
      short[] b=new short[HOP]; byte[] pk=new byte[44]; byte[] px=new byte[N*3]; long t=0; int lastLit=-1; double sumB=0, asym=0; int sn=0;
      for(int f=0;f<F;f++){ boolean music=f<300;
        for(int i=0;i<HOP;i++,t++){ double s=t/(double)SR, beat=s%0.5, eighth=s%0.25;
          double kick= beat<0.15? 0.7*Math.exp(-beat*25)*Math.sin(2*Math.PI*(50+80*Math.exp(-beat*40))*beat):0;
          double bass=0.25*Math.sin(2*Math.PI*55*s)*(0.6+0.4*Math.exp(-beat*6));
          double hat= eighth<0.03? 0.15*(r.nextDouble()-0.5)*Math.exp(-eighth*150):0;
          double mel=0.08*Math.sin(2*Math.PI*new double[]{440,494,523,587}[(int)(s*2)%4]*s);
          b[i]=(short)(music? Math.max(-32768,Math.min(32767,(kick+bass+hat+mel)*32767*0.8)) : 0); }
        a.process(b,HOP,pk); fx.update(a); fx.renderMatrix(e,N,W,0,px);
        int lit=0; double bri=0; for(int i=0;i<N*3;i++){ int v=px[i]&255; bri+=v; if(v>8) lit++; }
        if(music&&f>50){ sumB+=bri/(N*3*255.0);
          // how different is the picture from "same colour at the same distance from the centre" (= rings)
          for(int y=0;y<H;y++) for(int c=0;c<W/2;c++){ int p1=(y*W+c)*3, p2=(y*W+W-1-c)*3, p3=((H-1-y)*W+c)*3;
            for(int k=0;k<3;k++) asym+=Math.abs((px[p1+k]&255)-(px[p2+k]&255))+Math.abs((px[p1+k]&255)-(px[p3+k]&255)); } }
        if(!music&&lit>0) lastLit=f;
        if(sn<snaps.length && f==snaps[sn]){ int ox=sn*(W*S+8), oy=ei*(H*S+8);
          for(int p=0;p<W*H;p++){ int rgb=((px[p*3]&255)<<16)|((px[p*3+1]&255)<<8)|(px[p*3+2]&255);
            for(int dy=1;dy<S-1;dy++) for(int dx=1;dx<S-1;dx++) img.setRGB(ox+(p%W)*S+dx, oy+(p/W)*S+dy, rgb);} sn++; }
      }
      double avg=sumB/250, asy=asym/250/(H*(W/2)*6*255.0);
      int darkMs=lastLit<0?0:(lastLit-299)*20;
      boolean pass=avg>0.02 && darkMs<=3000;
      ok&=pass;
      System.out.printf("%-22s avg bri %.2f  asymmetry %.3f  dark after %4d ms  %s%n", DirectEffects.NAMES[e], avg, asy, darkMs, pass?"ok":"FAIL");
    }
    javax.imageio.ImageIO.write(img,"png",new java.io.File(x[0]));
    if(!ok) System.exit(1);
  }
}
