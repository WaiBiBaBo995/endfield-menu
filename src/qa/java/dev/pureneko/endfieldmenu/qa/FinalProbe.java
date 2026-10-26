package dev.pureneko.endfieldmenu.qa;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.pureneko.endfieldmenu.client.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Final opt-in checks; this source set is never packaged into the release. */
public final class FinalProbe {
  private boolean started;
  private int phase, preset;
  private long next, browserId;
  private EndfieldMenuScreen menu;
  public FinalProbe() { NeoForge.EVENT_BUS.addListener(this::tick); }
  private void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    long now = System.nanoTime();
    try {
      if (!started && mc.screen != null && mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding")) mc.setScreen(new TitleScreen());
      if (!started && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
        started = true;
        mc.options.pauseOnLostFocus = false;
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(120);
        mc.options.renderDistance().set(6);
        mc.options.simulationDistance().set(5);
        // Desktop activity must not inject extra Esc/clicks into this automated run.
        // Test input still calls MC's real GLFW handler entry points below.
        long window = mc.getWindow().getWindow();
        GLFW.glfwSetKeyCallback(window, null);
        GLFW.glfwSetMouseButtonCallback(window, null);
        GLFW.glfwSetCursorPosCallback(window, null);
        GLFW.glfwSetScrollCallback(window, null);
        mc.createWorldOpenFlows().openWorld("endfield-qa", () -> mc.setScreen(new TitleScreen()));
        next = now + 20_000_000_000L;
      }
      if (!started || mc.level == null || mc.player == null || mc.getOverlay() != null || now < next) return;
      switch (phase) {
        case 0 -> { key(); menu = (EndfieldMenuScreen)mc.screen; next = now + 8_000_000_000L; phase = 1; }
        case 1 -> {
          browserId = menu.timingReport().get("viewId").getAsLong();
          greenAvatar(); next = now + 2_000_000_000L; phase = 2;
        }
        case 2 -> {
          if (Boolean.getBoolean("endfieldmenu.inputTimingQa")) {
            var p = menu.mapCenter(); move(p.x, p.y); phase = 20; next = now + 100_000_000L; break;
          }
          checkGreen();
          Screenshot.grab(mc.gameDirectory, "final-green-avatar.png", mc.getMainRenderTarget(), m -> {});
          LogUtils.getLogger().info("[Final QA] before idle {}", menu.timingReport());
          key(); next = now + 65_000_000_000L; phase = 3;
        }
        case 3 -> {
          var r = menu.timingReport();
          LogUtils.getLogger().info("[Final QA] idle state screen={} {}", mc.screen, r);
          require(mc.screen == null && r.get("cacheEvicted").getAsBoolean(), "hidden cache evicted");
          require(r.getAsJsonObject("browser").get("gpuTextureBytes").getAsLong() == 0, "web GPU released");
          require(menu.backgroundTexture() < 0 && menu.terrainTexture() < 0, "terrain and frost released");
          require(r.get("viewId").getAsLong() == browserId, "default retains browser");
          LogUtils.getLogger().info("[Final QA] after idle {}", r);
          key(); next = now + 7_000_000_000L; phase = 4;
        }
        case 4 -> {
          var r = menu.timingReport();
          require(r.get("viewId").getAsLong() == browserId, "reopen uses same browser");
          require(r.getAsJsonObject("browser").getAsJsonObject("stream").get("streaming").getAsBoolean(), "stream resumes after browser shrink");
          greenAvatar(); preset = 0; phase = 5; next = now;
        }
        case 5 -> {
          MenuConfig.RESOLUTION.set(preset);
          menu.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
          next = now + 6_000_000_000L; phase = 6;
        }
        case 6 -> {
          var r = menu.timingReport().getAsJsonObject("browser");
          var size = MenuResolution.current();
          require(r.get("width").getAsInt() == size.width() && r.get("height").getAsInt() == size.height(), "preset " + preset + " raster");
          checkGreen();
          var p = menu.actionPoint("settings"); require(p != null, "preset settings coordinates");
          move(p.x, p.y); next = now + 100_000_000L; phase = 7;
        }
        case 7 -> { press(GLFW.GLFW_PRESS); next = now + 100_000_000L; phase = 8; }
        case 8 -> { press(GLFW.GLFW_RELEASE); next = now + 1_000_000_000L; phase = 9; }
        case 9 -> {
          require(mc.screen instanceof OptionsScreen, "preset " + preset + " real mouse callback");
          mc.screen.onClose();
          if (++preset < 5) { phase = 5; next = now + 1_000_000_000L; }
          else {
            MenuConfig.RESOLUTION.set(0); MenuConfig.RESOLUTION.save();
            LogUtils.getLogger().info("[Final QA] final metrics {}", menu.timingReport());
            LogUtils.getLogger().info("[Final QA] DONE"); mc.stop(); phase = 10;
          }
        }
        case 20 -> { press(GLFW.GLFW_PRESS); phase = 21; next = now + 100_000_000L; }
        case 21 -> { var p=menu.mapCenter(); move(p.x+50,p.y+12); phase=22; next=now+100_000_000L; }
        case 22 -> { press(GLFW.GLFW_RELEASE); phase=23; next=now+2_000_000_000L; }
        case 23 -> {
          var timing=menu.timingReport();
          require(timing.getAsJsonObject("browser").get("domInputToUploadP50Ms").getAsDouble()>=0, "active input latency recorded");
          LogUtils.getLogger().info("[Final QA] active input metrics {}",timing);
          LogUtils.getLogger().info("[Final QA] DONE input timing"); mc.stop(); phase=24;
        }
      }
    } catch (Throwable ex) { LogUtils.getLogger().error("[Final QA] FAILED phase " + phase, ex); mc.stop(); phase = 10; }
  }
  private Object field(Object target, String name) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
  private void greenAvatar() throws Exception {
    Object bridge = field(menu, "bridge");
    try (var img = new NativeImage(8, 8, false)) {
      for (int y=0;y<8;y++) for(int x=0;x<8;x++) img.setPixelRGBA(x,y,0xff00ff00);
      Method m = bridge.getClass().getDeclaredMethod("avatar", byte[].class); m.setAccessible(true); m.invoke(bridge, (Object)img.asByteArray());
    }
    var iframe = (MenuIframe)field(menu, "iframe");
    iframe.endfieldView().eval("document.querySelector('.ef-badge__avatar img').src='avatar.png?qa='+Date.now()");
  }
  private void checkGreen() throws Exception {
    var frame = ((MenuIframe)field(menu, "iframe")).endfieldFrames();
    float[] xs=(float[])field(frame,"avatarX"), ys=(float[])field(frame,"avatarY");
    try(var shot = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
      int x=Math.clamp(Math.round((xs[0]+xs[1]+xs[2]+xs[3])*.25f*shot.getWidth()),0,shot.getWidth()-1);
      int y=Math.clamp(Math.round((ys[0]+ys[1]+ys[2]+ys[3])*.25f*shot.getHeight()),0,shot.getHeight()-1);
      int rgba=shot.getPixelRGBA(x,y);
      require(((rgba>>>8)&255)>170 && (rgba&255)<100 && ((rgba>>>16)&255)<120, "green avatar survives composite at "+x+","+y+" rgba="+Integer.toHexString(rgba));
    }
  }
  private void key() { var mc=Minecraft.getInstance(); for(int a:new int[]{GLFW.GLFW_PRESS,GLFW.GLFW_RELEASE}) mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), GLFW.GLFW_KEY_ESCAPE,0,a,0); }
  private void move(double x,double y) throws Exception { var mc=Minecraft.getInstance();var w=mc.getWindow();double px=x*w.getScreenWidth()/w.getGuiScaledWidth(),py=y*w.getScreenHeight()/w.getGuiScaledHeight();GLFW.glfwSetCursorPos(w.getWindow(),px,py);invoke("onMove",new Class[]{long.class,double.class,double.class},w.getWindow(),px,py); }
  private void press(int a) throws Exception {invoke("onPress",new Class[]{long.class,int.class,int.class,int.class},Minecraft.getInstance().getWindow().getWindow(),0,a,0);}
  private void invoke(String name,Class<?>[] types,Object... args) throws Exception {var m=net.minecraft.client.MouseHandler.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(Minecraft.getInstance().mouseHandler,args);}
  private void require(boolean ok,String message) { if(!ok)throw new IllegalStateException(message);LogUtils.getLogger().info("[Final QA] PASS {}",message); }
}
