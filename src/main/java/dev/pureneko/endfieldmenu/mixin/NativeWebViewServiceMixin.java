package dev.pureneko.endfieldmenu.mixin;

import com.sighs.apricityui.webview.NativeWebViewService;
import com.sighs.apricityui.webview.WebViewNative;
import dev.pureneko.endfieldmenu.client.BrowserCaptureBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = NativeWebViewService.class, remap = false)
public abstract class NativeWebViewServiceMixin {
  @Redirect(
      method = "create",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/sighs/apricityui/webview/WebViewNative;create(Ljava/lang/String;Ljava/lang/String;IIZZII)J"))
  private long endfield$frameConnection(
      String url,
      String profile,
      int w,
      int h,
      boolean transparent,
      boolean auto,
      int interval,
      int format) {
    if (url.startsWith("http://127.0.0.1:") && url.contains("/endfield/"))
      return BrowserCaptureBootstrap.create(
          url, profile, w, h, transparent, auto, interval, format);
    synchronized (BrowserCaptureBootstrap.class) {
      return WebViewNative.create(url, profile, w, h, transparent, auto, interval, format);
    }
  }
}
