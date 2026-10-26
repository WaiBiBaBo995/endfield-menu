package dev.pureneko.endfieldmenu.client;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import com.sun.jna.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.*;

/** Standalone bounded-codec benchmark; run with Minecraft's JNA/LWJGL jars. */
public class CodecBenchmark {
  interface Codec extends com.sun.jna.Library {
    int ef_decode_jpeg(ByteBuffer in,int length,ByteBuffer out,int capacity,int w,int h);
    int ef_decode_png(ByteBuffer in,int length,ByteBuffer out,int capacity,int w,int h);
  }
  public static void main(String[] args)throws Exception {
    var codec=Native.load(Path.of(args[0]).toAbsolutePath().toString(),Codec.class);
    byte[] bytes=Files.readAllBytes(Path.of(args[1]));boolean png=bytes[0]==(byte)137;
    ByteBuffer input=MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
    try(var stack=MemoryStack.stackPush()) {
      var w=stack.mallocInt(1);var h=stack.mallocInt(1);var n=stack.mallocInt(1);
      if(!STBImage.stbi_info_from_memory(input,w,h,n))throw new Exception("Invalid fixture");
      ByteBuffer out=MemoryUtil.memAlloc(w.get(0)*h.get(0)*4);
      long[] times=new long[100];
      for(int i=-10;i<100;i++) {
        long t=System.nanoTime();int r=png?codec.ef_decode_png(input,bytes.length,out,out.capacity(),w.get(0),h.get(0))
          :codec.ef_decode_jpeg(input,bytes.length,out,out.capacity(),w.get(0),h.get(0));
        if(r!=0)throw new Exception("decode result "+r);
        if(i>=0)times[i]=System.nanoTime()-t;
      }
      int invalid=png?codec.ef_decode_png(input,bytes.length,out,4,w.get(0),h.get(0))
        :codec.ef_decode_jpeg(input,bytes.length,out,4,w.get(0),h.get(0));
      if(invalid!=-1)throw new Exception("Bounds check failed");
      Arrays.sort(times);
      System.out.println("{\"width\":"+w.get(0)+",\"height\":"+h.get(0)+",\"p50Ms\":"+times[50]/1e6+",\"p95Ms\":"+times[95]/1e6+",\"boundsCheck\":true}");
      MemoryUtil.memFree(out);
    } finally {MemoryUtil.memFree(input);}
  }
}
