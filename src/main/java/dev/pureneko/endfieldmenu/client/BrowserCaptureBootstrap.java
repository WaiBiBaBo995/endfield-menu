package dev.pureneko.endfieldmenu.client;

import com.sighs.apricityui.webview.WebViewNative;
import com.sun.jna.platform.win32.Kernel32;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/** Enables a loopback frame connection only for our own WebView2 profile. */
public final class BrowserCaptureBootstrap {
  private static final String ARGUMENTS = "WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS";
  private static final ConcurrentHashMap<Long, BrowserFrameStream> STREAMS =
      new ConcurrentHashMap<>();
  private static int profilePort;

  private BrowserCaptureBootstrap() {}

  public static synchronized long create(
      String url,
      String profile,
      int width,
      int height,
      boolean transparent,
      boolean autoCapture,
      int interval,
      int format) {
    if (!MenuConfig.DIRECT_CAPTURE.get())
      return WebViewNative.create(
          url, profile, width, height, transparent, autoCapture, interval, format);
    var win = Kernel32.INSTANCE;
    char[] buffer = new char[32768];
    int length = win.GetEnvironmentVariable(ARGUMENTS, buffer, buffer.length);
    String previous = length > 0 && length < buffer.length ? new String(buffer, 0, length) : null;
    // Respect a debugger explicitly configured by the launcher. Never attach to its targets.
    if (length >= buffer.length || previous != null && previous.contains("--remote-debugging"))
      return WebViewNative.create(
          url, profile, width, height, transparent, autoCapture, interval, format);
    if (profilePort == 0) {
      try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
        profilePort = socket.getLocalPort();
      } catch (java.io.IOException e) {
        return WebViewNative.create(
            url, profile, width, height, transparent, autoCapture, interval, format);
      }
    }
    // WebView2 can retain its browser process briefly after the last world closes.
    // Keep the profile's port stable across world sessions so a quick rejoin still connects.
    int port = profilePort;
    String arguments =
        (previous == null ? "" : previous + " ")
            + "--remote-debugging-address=127.0.0.1 --remote-debugging-port="
            + port;
    if (!win.SetEnvironmentVariable(ARGUMENTS, arguments))
      return WebViewNative.create(
          url, profile, width, height, transparent, autoCapture, interval, format);
    try {
      // A separate profile keeps this option out of every other AUI iframe/browser process.
      String ownProfile = Path.of(profile).resolveSibling("endfield-webview").toString();
      long handle =
          WebViewNative.create(
              url,
              ownProfile,
              width,
              height,
              transparent,
              false,
              interval,
              MenuConfig.LOSSLESS_CAPTURE.get() ? 0 : 1);
      if (handle != 0) STREAMS.put(handle, new BrowserFrameStream(port, url, width, height));
      return handle;
    } finally {
      win.SetEnvironmentVariable(ARGUMENTS, previous);
    }
  }

  static BrowserFrameStream take(long view) {
    return STREAMS.remove(view);
  }

  public static void release(long view) {
    var pending = STREAMS.remove(view);
    if (pending != null) pending.close();
  }
}
