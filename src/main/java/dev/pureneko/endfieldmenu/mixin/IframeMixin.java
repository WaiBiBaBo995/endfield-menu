package dev.pureneko.endfieldmenu.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.element.Iframe;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.spi.AuiWebViewService;
import dev.pureneko.endfieldmenu.client.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = Iframe.class, remap = false)
public abstract class IframeMixin implements MenuIframe {
  @Shadow private AuiWebViewService.View view;
  @Shadow private AuiWebViewService.Channel viewChannel;
  @Shadow private int viewportWidth;
  @Shadow private int viewportHeight;

  @Shadow
  private void tickView() {
    throw new AssertionError();
  }

  @Unique private MenuFrameUpload endfield$frames;

  @Unique
  private boolean endfield$ours() {
    return "endfield-original-page".equals(((Element) (Object) this).id);
  }

  @Override
  public AuiWebViewService.View endfieldView() {
    return view;
  }

  @Override
  public MenuFrameUpload endfieldFrames() {
    return endfield$frames;
  }

  @Override
  public void endfieldEvictView() {
    if (endfield$frames != null) endfield$frames.evict();
    if (view != null) view.resize(256,128,1);
    // Force the ordinary AUI resize path to restore the selected raster on reopen.
    viewportWidth=viewportHeight=0;
  }

  @Override
  public void endfieldPoll() {
    tickView();
    if (viewChannel != null) {
      if (endfield$frames == null) endfield$frames = new MenuFrameUpload(view.id());
      endfield$frames.drain(viewChannel.buffer());
      boolean showing =
          net.minecraft.client.Minecraft.getInstance().screen instanceof EndfieldMenuScreen;
      view.setAutoCapture(!endfield$frames.usesStream() && (showing || !endfield$frames.ready()));
    }
  }

  @Inject(method = "tickIdleCapture", at = @At("HEAD"), cancellable = true)
  private void endfield$lifecycle(CallbackInfo ci) {
    if (endfield$ours()) ci.cancel();
  }

  @Redirect(
      method = "lambda$startView$2",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/sighs/apricityui/spi/AuiWebViewService;create(Ljava/lang/String;IIZI)Lcom/sighs/apricityui/spi/AuiWebViewService$View;"))
  private static AuiWebViewService.View endfield$transparent(
      AuiWebViewService service, String url, int w, int h, boolean transparent, int interval) {
    boolean ours = url.startsWith("http://127.0.0.1:") && url.contains("/endfield/");
    var result = service.create(url, w, h, ours || transparent, interval);
    if (ours && result != null) result.setCaptureQuality(MenuConfig.LOSSLESS_CAPTURE.get() ? 2 : 3);
    return result;
  }

  @Inject(method = "desiredViewport", at = @At("HEAD"), cancellable = true)
  private void endfield$nativeResolution(CallbackInfoReturnable<double[]> ci) {
    if (!endfield$ours()) return;
    var size = MenuResolution.current();
    ci.setReturnValue(new double[] {size.width(), size.rasterHeight(), 1});
  }

  @Inject(method = "scaleY", at = @At("HEAD"), cancellable = true)
  private void endfield$inputY(double y, double extent, CallbackInfoReturnable<Integer> ci) {
    if (endfield$ours() && extent > 0)
      ci.setReturnValue((int)Math.clamp(Math.round(y * MenuResolution.current().height() / extent),0,MenuResolution.current().height()));
  }

  @Inject(method = "tickCaptureInterval", at = @At("HEAD"), cancellable = true)
  private void endfield$cadence(CallbackInfo ci) {
    if (endfield$ours()) ci.cancel();
  }

  @Inject(method = "drainUpdates", at = @At("HEAD"), cancellable = true)
  private void endfield$completeFrames(CallbackInfo ci) {
    if (!endfield$ours()) return;
    // EndfieldMenuScreen polls once before drawing terrain. This keeps the uploaded HTML,
    // homography and map orientation on the same capture instead of racing a second drain.
    ci.cancel();
  }

  @Inject(method = "drawView", at = @At("HEAD"), cancellable = true)
  private void endfield$compose(PoseStack pose, Rect rect, CallbackInfo ci) {
    if (!endfield$ours()) return;
    if (endfield$frames != null) MenuCompositeRenderer.draw(pose, rect, endfield$frames);
    ci.cancel();
  }

  @Inject(method = "releaseChannel", at = @At("HEAD"))
  private void endfield$release(CallbackInfo ci) {
    if (endfield$frames != null) {
      endfield$frames.close();
      endfield$frames = null;
    }
  }
}
