package dev.pureneko.endfieldmenu.client;

import com.sun.jna.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Independent, bounded PNG/JPEG codecs. ApricityUI native binaries remain untouched. */
final class NativePngDecoder {
  interface Api extends Library {
    int ef_decode_png(ByteBuffer input, int length, ByteBuffer output, int capacity, int width, int height);
    int ef_decode_jpeg(ByteBuffer input, int length, ByteBuffer output, int capacity, int width, int height);
  }
  static final Api API = load();
  private static Api load() {
    if (!com.sun.jna.Platform.isWindows() || !"amd64".equals(System.getProperty("os.arch"))) return null;
    try(var stream=NativePngDecoder.class.getResourceAsStream("/natives/windows-x86_64/endfield_codec.dll")) {
      if(stream==null)return null;
      byte[] bytes=stream.readAllBytes();
      String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
      var dir=net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("endfield/native").resolve(hash);
      Files.createDirectories(dir);var dll=dir.resolve("endfield_codec.dll");
      if(!Files.exists(dll)||!MessageDigest.isEqual(bytes,Files.readAllBytes(dll))) Files.write(dll,bytes);
      return Native.load(dll.toAbsolutePath().toString(),Api.class);
    } catch(Exception|LinkageError ex) {
      com.mojang.logging.LogUtils.getLogger().warn("Endfield fast image codecs unavailable; using platform fallback",ex);
      return null;
    }
  }
}
