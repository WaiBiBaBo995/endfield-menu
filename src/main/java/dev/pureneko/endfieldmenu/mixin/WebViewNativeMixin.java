package dev.pureneko.endfieldmenu.mixin;

import com.sighs.apricityui.webview.WebViewNative;
import dev.pureneko.endfieldmenu.client.BrowserCaptureBootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Also releases a frame connection when an asynchronously created view is cancelled. */
@Mixin(value = WebViewNative.class, remap = false)
public abstract class WebViewNativeMixin {
  @Inject(method = "destroy", at = @At("HEAD"))
  private static void endfield$releasePending(long handle, CallbackInfo ci) {
    BrowserCaptureBootstrap.release(handle);
  }
}
