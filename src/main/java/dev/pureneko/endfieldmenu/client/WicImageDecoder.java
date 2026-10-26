package dev.pureneko.endfieldmenu.client;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.*;
import java.nio.ByteBuffer;

/** Thread-confined Windows codec; outputs straight RGBA without a per-pixel Java conversion. */
final class WicImageDecoder implements AutoCloseable {
  private Pointer factory;
  private boolean initialized;
  private static final Guid.GUID RGBA = new Guid.GUID("{f5c7ad2d-6a8d-43dd-a7a8-a29935261ae9}");

  WicImageDecoder() {
    int hr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, 0).intValue();
    if (hr < 0) throw new IllegalStateException("WIC COM initialization: " + hr);
    initialized = true;
    var result = new PointerByReference();
    hr = Ole32.INSTANCE.CoCreateInstance(
        new Guid.GUID("{cacaf262-9370-4615-a13b-9f5539da4c0a}"), null, 1,
        new Guid.GUID("{ec5ec8a9-c395-4314-9c77-54d7a935ff70}"), result).intValue();
    if (hr < 0) { close(); throw new IllegalStateException("WIC factory: " + hr); }
    factory = result.getValue();
  }

  static int call(Pointer object, int slot, Object... tail) {
    Object[] args = new Object[tail.length + 1];
    args[0] = object;
    System.arraycopy(tail, 0, args, 1, tail.length);
    return Function.getFunction(object.getPointer(0).getPointer((long) slot * Native.POINTER_SIZE),
        Function.ALT_CONVENTION).invokeInt(args);
  }

  private static void check(int hr) {
    if (hr < 0) throw new IllegalStateException("WIC decode: " + Integer.toHexString(hr));
  }

  static void release(Pointer object) { if (object != null) call(object, 2); }

  void decode(ByteBuffer compressed, ByteBuffer rgba, int width, int height) {
    Pointer stream = null, decoder = null, frame = null, converter = null;
    try {
      var out = new PointerByReference();
      check(call(factory, 14, out)); stream = out.getValue();
      check(call(stream, 16, Native.getDirectBufferPointer(compressed), compressed.remaining()));
      check(call(factory, 4, stream, null, 0, out)); decoder = out.getValue();
      check(call(decoder, 13, 0, out)); frame = out.getValue();
      var w = new IntByReference(); var h = new IntByReference();
      check(call(frame, 3, w, h));
      if (w.getValue() != width || h.getValue() != height) throw new IllegalStateException("WIC raster changed");
      check(call(factory, 10, out)); converter = out.getValue();
      check(call(converter, 8, frame, RGBA, 0, null, 0.0, 0));
      check(call(converter, 7, null, width * 4, width * height * 4, Native.getDirectBufferPointer(rgba)));
    } finally { release(converter); release(frame); release(decoder); release(stream); }
  }

  @Override public void close() {
    release(factory); factory = null;
    if (initialized) { initialized = false; Ole32.INSTANCE.CoUninitialize(); }
  }
}
