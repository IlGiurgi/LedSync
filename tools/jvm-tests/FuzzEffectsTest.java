import com.ledsync.phone.*;
// Every effect on odd sizes (1, 2, 3, 7, 1000 LEDs, matrices) with noise, silence and clipping: must never throw.
public class FuzzEffectsTest {
  public static void main(String[] x) {
    int SR=48000,HOP=960; java.util.Random r=new java.util.Random(1); int fails=0;
    int[] sizes={1,2,3,7,1000}; int[][] grids={{1,5},{2,2},{11,114},{11,110},{3,3}};
    for (int e=0;e<DirectEffects.NAMES.length;e++){
      for (boolean mir: new boolean[]{true,false}) { Tuning.mirror=mir;
      for (int blur: new int[]{0,10}) { Tuning.blur=blur; Tuning.roll=10;
        AudioAnalyzer a=new AudioAnalyzer(SR); short[] b=new short[HOP]; byte[] pk=new byte[44];
        DirectEffects[] fx=new DirectEffects[sizes.length+grids.length]; for(int i=0;i<fx.length;i++) fx[i]=new DirectEffects();
        for (int f=0;f<300;f++){
          int mode=(f/50)%3;
          for(int i=0;i<HOP;i++) b[i]=(short)(mode==0?0: mode==1? r.nextGaussian()*8000 : (r.nextBoolean()?32767:-32768));
          a.process(b,HOP,pk);
          try {
            for(int s=0;s<sizes.length;s++){ fx[s].update(a); byte[] px=new byte[sizes[s]*3]; fx[s].render(e,sizes[s],r.nextInt(360),px);}
            for(int g=0;g<grids.length;g++){ DirectEffects d=fx[sizes.length+g]; d.update(a); int n=grids[g][1]; byte[] px=new byte[n*3]; d.renderMatrix(e,n,grids[g][0],0,px);}
          } catch (Throwable t) { if (fails++<5) { System.out.println(DirectEffects.NAMES[e]+" mirror="+mir+" blur="+blur+": "+t); t.printStackTrace(System.out);} break; }
        }
      }}
    }
    System.out.println(fails==0?"ALL OK":"FAILURES: "+fails);
  }
}
