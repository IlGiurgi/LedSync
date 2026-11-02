import com.ledsync.phone.*;
import java.awt.image.BufferedImage;
public class AllEffectsTest {
  public static void main(String[] x) throws Exception {
    int SR=48000,HOP=960,N=300,F=400; // 6 s music then 2 s silence
    int effects=DirectEffects.NAMES.length;
    BufferedImage img=new BufferedImage(N*2+10, (F/2+12)*((effects+1)/2), BufferedImage.TYPE_INT_RGB);
    for(int e=0;e<effects;e++){
      AudioAnalyzer a=new AudioAnalyzer(SR); DirectEffects fx=new DirectEffects(); java.util.Random r=new java.util.Random(3);
      short[] b=new short[HOP]; byte[] pk=new byte[44]; byte[] px=new byte[N*3]; long t=0; double sumB=0, sumDiff=0; byte[] prev=new byte[N*3]; boolean sym=true; int lastLit=-1;
      for(int f=0;f<F;f++){ boolean music=f<300;
        for(int i=0;i<HOP;i++,t++){ double s=t/(double)SR, beat=s%0.5, eighth=s%0.25;
          double kick= beat<0.15? 0.7*Math.exp(-beat*25)*Math.sin(2*Math.PI*(50+80*Math.exp(-beat*40))*beat):0;
          double bassF=new double[]{55,55,73.4,65.4}[(int)(s/2)%4]; double bass=0.25*Math.sin(2*Math.PI*bassF*s)*(0.6+0.4*Math.exp(-beat*6));
          double hat= eighth<0.03? 0.15*(r.nextDouble()-0.5)*Math.exp(-eighth*150):0;
          double mel=0.08*Math.sin(2*Math.PI*new double[]{440,494,523,587}[(int)(s*2)%4]*s);
          b[i]=(short)(music? Math.max(-32768,Math.min(32767,(kick+bass+hat+mel)*32767*0.8)) : 0); }
        a.process(b,HOP,pk); fx.update(a); fx.render(e,N,0,px);
        int lit=0; double bri=0, diff=0;
        for(int i=0;i<N*3;i++){ int v=px[i]&255; bri+=v; diff+=Math.abs(v-(prev[i]&255)); if(v>8) lit++; }
        if(f>=50&&music){ sumB+=bri/(N*3*255.0); sumDiff+=diff/(N*3*255.0);} 
        if(!music && lit>0) lastLit=f;
        for(int i=0;i<N/2;i++) for(int c=0;c<3;c++) if(Math.abs((px[i*3+c]&255)-(px[(N-1-i)*3+c]&255))>1 && e!=12) sym=false;
        System.arraycopy(px,0,prev,0,px.length);
        if(f%2==0){ int ox=(e%2)*(N+10), oy=(e/2)*(F/2+12)+f/2; for(int i=0;i<N;i++) img.setRGB(ox+i,oy,((px[i*3]&255)<<16)|((px[i*3+1]&255)<<8)|(px[i*3+2]&255)); }
      }
      System.out.printf("%-22s avg bri %.2f  motion %.3f  mirrored %s  dark after silence: %s%n", DirectEffects.NAMES[e], sumB/250, sumDiff/250, sym, lastLit<0?"immediately":((lastLit-299)*20)+" ms");
    }
    javax.imageio.ImageIO.write(img,"png",new java.io.File("effects.png"));
  }
}
