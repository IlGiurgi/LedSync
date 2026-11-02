import com.ledsync.phone.AudioAnalyzer;
import java.nio.*;
public class AnalyzerTest {
  static int SR=48000, HOP=960;
  static AudioAnalyzer a; static byte[] pk=new byte[44]; static long t=0;
  static void feed(java.util.function.IntToDoubleFunction f,int frames){ short[] b=new short[HOP]; for(int n=0;n<frames;n++){ for(int i=0;i<HOP;i++,t++) b[i]=(short)Math.max(-32768,Math.min(32767,f.applyAsDouble((int)t)*32767)); a.process(b,HOP,pk);} }
  static String show(){ ByteBuffer bb=ByteBuffer.wrap(pk).order(ByteOrder.LITTLE_ENDIAN); StringBuilder s=new StringBuilder(new String(pk,0,5)+" hdr0="+pk[5]); bb.position(8); s.append(String.format(" raw=%.0f smth=%.0f",bb.getFloat(),bb.getFloat())); s.append(" peak="+bb.get()+" fc="+(bb.get()&255)+" geq=["); for(int i=0;i<16;i++) s.append((bb.get()&255)+(i<15?",":"]")); s.append(" zc="+(bb.getShort()&0xffff)); s.append(String.format(" mag=%.0f major=%.0fHz pos=%d",bb.getFloat(),bb.getFloat(),bb.position())); return s.toString(); }
  public static void main(String[] x){
    a=new AudioAnalyzer(SR);
    feed(i->0,50); System.out.println("silence  : "+show());
    feed(i->0.5*Math.sin(2*Math.PI*100*i/SR),50); System.out.println("100Hz    : "+show());
    feed(i->0.5*Math.sin(2*Math.PI*1000*i/SR),50); System.out.println("1kHz     : "+show());
    feed(i->0.5*Math.sin(2*Math.PI*5000*i/SR),50); System.out.println("5kHz     : "+show());
    feed(i->0.02*Math.sin(2*Math.PI*5000*i/SR),300); System.out.println("5kHz -28dB after AGC: "+show());
    a=new AudioAnalyzer(SR); java.util.Random r=new java.util.Random(1);
    int peaks=0; // 120 BPM kick (60Hz burst) every 0.5s over noise
    for(int n=0;n<500;n++){ final int fr=n; feed(i->{ double tt=(i%(SR/2))/(double)SR; return 0.05*(r.nextDouble()-0.5)+ (tt<0.08?0.8*Math.exp(-tt*30)*Math.sin(2*Math.PI*60*tt):0);},1); if(pk[16]==1)peaks++; }
    System.out.println("kick 120BPM, 10s -> peaks="+peaks+" (expected ~20)");
  }
}
