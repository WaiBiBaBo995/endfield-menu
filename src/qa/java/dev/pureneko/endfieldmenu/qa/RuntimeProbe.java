package dev.pureneko.endfieldmenu.qa;

import com.mojang.logging.LogUtils;
import dev.pureneko.endfieldmenu.client.EndfieldMenuScreen;
import java.lang.reflect.Method;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Invokes the actual GLFW callback entry points on MC handlers, including NeoForge input
 * interception.
 */
@Mod(value = "endfieldmenuqa", dist = Dist.CLIENT)
public final class RuntimeProbe {
  private int phase, ticks, worldTicks, sequence;
  private long sequenceStart;
  private int sequenceShot;
  private boolean started;
  private EndfieldMenuScreen menu;
  private final java.util.Queue<Runnable> inputs = new java.util.ArrayDeque<>();
  private double tileY;
  private int surveyRevision;
  private String camera;
  private long browserId;
  private int serverTickBefore;
  private long backgroundBefore;
  private long backgroundHash;

  public RuntimeProbe() {
    if (Boolean.getBoolean("endfieldmenu.finalQa")) new FinalProbe();
    if (System.getProperty("endfieldmenu.optimizationQa") != null) new OptimizationProbe();
    NeoForge.EVENT_BUS.addListener(this::tick);
    NeoForge.EVENT_BUS.addListener(this::film);
  }

  private void tick(ClientTickEvent.Post event) {
    if (Boolean.getBoolean("endfieldmenu.finalQa")) return;
    if (System.getProperty("endfieldmenu.optimizationQa") != null) return;
    Minecraft mc = Minecraft.getInstance();
    if (!inputs.isEmpty()) inputs.remove().run();
    if (!started
        && mc.screen != null
        && mc.screen.getClass().getSimpleName().contains("AccessibilityOnboarding")) {
      mc.setScreen(new TitleScreen());
    }
    if (!started && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
      started = true;
      mc.options.renderDistance().set(6);
      mc.options.simulationDistance().set(5);
      mc.options.pauseOnLostFocus = false;
      mc.options.enableVsync().set(false);
      mc.options.framerateLimit().set(120);
      if (Files.exists(mc.gameDirectory.toPath().resolve("saves/endfield-qa/level.dat"))) {
        mc.createWorldOpenFlows().openWorld("endfield-qa", () -> mc.setScreen(new TitleScreen()));
      } else {
        mc.createWorldOpenFlows()
            .createFreshLevel(
                "endfield-qa",
                new LevelSettings(
                    "Endfield Menu QA",
                    GameType.SURVIVAL,
                    false,
                    Difficulty.PEACEFUL,
                    true,
                    new GameRules(),
                    WorldDataConfiguration.DEFAULT),
                new WorldOptions(8675309L, false, false),
                registry ->
                    registry
                        .registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.NORMAL)
                        .value()
                        .createWorldDimensions(),
                new TitleScreen());
      }
      LogUtils.getLogger().info("[Endfield QA] started fresh isolated test world");
    }

    if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
    if (phase == 0) {
      if (++worldTicks < 160) return;
      key(GLFW.GLFW_KEY_ESCAPE);
      menu = (EndfieldMenuScreen) mc.screen;
      phase = 1;
      ticks = 0;
      return;
    }
    if (++ticks < 80) return;
    ticks = 0;
    try {
      switch (phase++) {
        case 1 -> {
          capture(mc, "hybrid-01-1080.png");
          require(mc.isPaused(), "singleplayer pauses");
          require(
              menu.getLinkedDocument() != null && !menu.getLinkedDocument().isManuallyRendered(),
              "active AUI document receives actual input");
          LogUtils.getLogger().info("[Endfield QA] metrics {}", menu.timingReport());
          if (Boolean.getBoolean("endfieldmenu.captureLab")) {
            phase = 1;
            return;
          }
          if (Boolean.getBoolean("endfieldmenu.backgroundQa")) {
            phase = 45;
            return;
          }
          if (Boolean.getBoolean("endfieldmenu.captureSmoke")) {
            var browser = menu.timingReport().getAsJsonObject("browser");
            require(browser.get("completeFrames").getAsLong() > 0, "smoke presents complete frames");
            require(browser.get("portalProjection").getAsBoolean(), "smoke map projection");
            if (dev.pureneko.endfieldmenu.client.MenuConfig.LOSSLESS_CAPTURE.get()) {
              require(!browser.get("keyedBackground").getAsBoolean(), "PNG uses real alpha without chroma key");
              require(browser.getAsJsonObject("stream").get("codec").getAsString().equals("libspng-zlib-ng"), "PNG native decoder active");
            }
            mc.stop();
            return;
          }
          if (Boolean.getBoolean("endfieldmenu.fallbackQa")) {
            browserId = menu.timingReport().get("viewId").getAsLong();
            phase = 37;
            return;
          }
          click("settings");
        }
        case 2 -> {
          require(mc.screen instanceof OptionsScreen, "real callback settings");
          mc.screen.onClose();
        }
        case 3 -> {
          require(mc.screen == menu, "settings returns");
          click("advancements");
        }
        case 4 -> {
          require(mc.screen instanceof AdvancementsScreen, "real callback advancements");
          mc.screen.onClose();
        }
        case 5 -> click("stats");
        case 6 -> {
          require(mc.screen instanceof StatsScreen, "real callback statistics");
          mc.screen.onClose();
        }
        case 7 -> click("inventory");
        case 8 -> {
          require(mc.screen instanceof InventoryScreen, "real callback inventory");
          mc.screen.onClose();
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 9 -> {
          var p = menu.mapCenter();
          move(p.x, p.y);
          inputs.add(() -> press(1));
          inputs.add(() -> move(p.x + 85, p.y + 20));
          inputs.add(() -> press(0));
          inputs.add(() -> wheel(2));
        }
        case 10 -> {
          require(menu.mapYaw() != 0 && menu.mapZoom() > 1, "real map drag and wheel");
          capture(mc, "hybrid-02-map.png");
          click("map-reset");
        }
        case 11 -> {
          require(menu.mapYaw() == 0 && Math.abs(menu.mapZoom() - 1.45f) < .001, "map reset");
          click("quit");
        }
        case 12 -> {
          require(mc.screen instanceof ConfirmScreen, "quit confirmation");
          key(GLFW.GLFW_KEY_ESCAPE);
          mc.setScreen(menu);
        }
        case 13 -> {
          LogUtils.getLogger().info("[Endfield QA] stable metrics {}", menu.timingReport());
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 14 -> {
          require(mc.screen == null, "Esc closes after 520ms");
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 15 -> {
          require(mc.screen == menu, "reuses browser screen");
          GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720);
        }
        case 16 -> {
          capture(mc, "hybrid-03-720.png");
          click("settings");
        }
        case 17 -> {
          require(mc.screen instanceof OptionsScreen, "input after resize");
          mc.screen.onClose();
          mc.options.guiScale().set(2);
          mc.resizeDisplay();
        }
        case 18 -> {
          capture(mc, "hybrid-04-gui2.png");
          click("resume");
        }
        case 19 -> {
          require(mc.screen == null, "resume real input");
          GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
          mc.options.guiScale().set(3);
          mc.resizeDisplay();
        }
        case 20 -> {
          key(GLFW.GLFW_KEY_ESCAPE);
          sequence = 1;
          sequenceStart = System.nanoTime();
          sequenceShot = 0;
        }
        case 21 -> {
          sequence = 2;
          sequenceStart = System.nanoTime();
          sequenceShot = 0;
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 22 -> {
          require(mc.screen == null, "film exit closes");
          LogUtils.getLogger().info("[Endfield QA] film lifecycle {}", menu.timingReport());
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 23 -> {
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 24 -> {
          require(mc.screen == null, "consecutive exit 2");
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 25 -> {
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 26 -> {
          require(mc.screen == null, "consecutive exit 3");
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 27 -> {
          var r = menu.timingReport();
          LogUtils.getLogger().info("[Endfield QA] final hybrid metrics {}", r);
          browserId = r.get("viewId").getAsLong();
          var p = r.getAsJsonObject("page");
          tileY = p.get("tileY").getAsDouble();
          var pt = p.getAsJsonObject("right");
          move(
              pt.get("x").getAsDouble() * menu.width / p.get("width").getAsDouble(),
              pt.get("y").getAsDouble() * menu.height / p.get("height").getAsDouble());
          inputs.add(() -> wheel(-3));
        }
        case 28 -> {
          var p = menu.timingReport().getAsJsonObject("page");
          require(Math.abs(p.get("tileY").getAsDouble() - tileY) > 20, "right list real wheel");
          tileY = p.get("tileY").getAsDouble();
          var pt = p.getAsJsonObject("right");
          double x = pt.get("x").getAsDouble() * menu.width / p.get("width").getAsDouble(),
              y = pt.get("y").getAsDouble() * menu.height / p.get("height").getAsDouble();
          move(x, y);
          inputs.add(() -> press(1));
          inputs.add(() -> move(x, y - 35));
          inputs.add(() -> press(0));
        }
        case 29 -> {
          var p = menu.timingReport().getAsJsonObject("page");
          require(
              Math.abs(p.get("tileY").getAsDouble() - tileY) > 15 && mc.screen == menu,
              "right list drag without accidental action");
          camera = p.get("camera").getAsString();
          move(5, 5);
        }
        case 30 -> {
          require(
              !menu.timingReport()
                  .getAsJsonObject("page")
                  .get("camera")
                  .getAsString()
                  .equals(camera),
              "original mouse parallax");
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 31 -> {
          require(mc.screen == null, "close after list interaction");
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 32 -> {
          var p = menu.mapCenter();
          move(p.x, p.y);
          inputs.add(() -> press(1));
          inputs.add(() -> move(p.x + 45, p.y));
          inputs.add(() -> press(0));
        }
        case 33 -> {
          require(menu.mapYaw() != 0, "map changed before view reset");
          click("view-reset");
        }
        case 34 -> {
          require(
              menu.mapYaw() == 0 && Math.abs(menu.mapZoom() - 1.45f) < .001,
              "view reset real input");
          surveyRevision = menu.timingReport().get("surveyRevision").getAsInt();
          click("refresh");
        }
        case 35 -> {
          require(
              menu.timingReport().get("surveyRevision").getAsInt() > surveyRevision,
              "refresh terrain real input");
          require(
              menu.timingReport().get("viewId").getAsLong() == browserId,
              "one browser retained across all checks");
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 36 -> {
          require(mc.screen == null, "final close");
          LogUtils.getLogger().info("[Endfield QA] hidden capture {}", menu.timingReport());
          var stream = menu.timingReport().getAsJsonObject("browser").getAsJsonObject("stream");
          require(
              stream != null && !stream.get("streaming").getAsBoolean(),
              "hidden compositor capture stops");
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 37 -> {
          require(
              "browser-compositor"
                  .equals(
                      menu.timingReport().getAsJsonObject("browser").get("backend").getAsString()),
              "compositor stream resumes");
          var iframe =
              (dev.pureneko.endfieldmenu.client.MenuIframe)
                  menu.getLinkedDocument().getElementById("endfield-original-page");
          var field =
              dev.pureneko.endfieldmenu.client.MenuFrameUpload.class.getDeclaredField("direct");
          field.setAccessible(true);
          ((AutoCloseable) field.get(iframe.endfieldFrames())).close();
        }
        case 38 -> {
          var browser = menu.timingReport().getAsJsonObject("browser");
          require(
              "aui-capture".equals(browser.get("backend").getAsString())
                  && browser.get("htmlPresentedFps").getAsDouble() > 0,
              "interrupted stream falls back to AUI capture");
          LogUtils.getLogger().info("[Endfield QA] fallback metrics {}", menu.timingReport());
          click("settings");
        }
        case 39 -> {
          require(mc.screen instanceof OptionsScreen, "real input after capture fallback");
          mc.screen.onClose();
        }
        case 40 -> {
          menu.setPauseGame(false);
          serverTickBefore = mc.getSingleplayerServer().getTickCount();
          backgroundBefore = menu.timingReport().get("backgroundFrames").getAsLong();
        }
        case 41 -> {
          require(
              !mc.isPaused() && mc.getSingleplayerServer().getTickCount() > serverTickBefore + 20,
              "world continues when pause disabled");
          require(
              menu.timingReport().get("backgroundFrames").getAsLong() > backgroundBefore + 20,
              "running world frosted background renders each frame on GPU");
          menu.setPauseGame(true);
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 42 -> {
          require(mc.screen == null, "fallback exit closes");
          mc.level.disconnect();
          mc.disconnect();
          mc.createWorldOpenFlows().openWorld("endfield-qa", () -> mc.setScreen(new TitleScreen()));
        }
        case 43 -> {
          key(GLFW.GLFW_KEY_ESCAPE);
          menu = (EndfieldMenuScreen) mc.screen;
        }
        case 44 -> {
          require(
              menu.timingReport().get("viewId").getAsLong() != browserId,
              "new world creates a new browser session");
          require(
              "browser-compositor"
                  .equals(
                      menu.timingReport().getAsJsonObject("browser").get("backend").getAsString()),
              "compositor reconnects after rejoining world");
          LogUtils.getLogger().info("[Endfield QA] rejoin metrics {}", menu.timingReport());
          LogUtils.getLogger().info("[Endfield QA] ALL HYBRID INPUT CHECKS PASSED");
          mc.stop();
        }
        case 45 -> {
          require(
              menu.timingReport().get("backgroundBackend").getAsString().equals("live-gpu-shader"),
              "live GPU background backend");
          backgroundHash = backgroundHash();
          backgroundBefore = menu.timingReport().get("backgroundFrames").getAsLong();
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 46 -> {
          require(mc.screen == null, "shader background exit completes");
          mc.player.setYRot(mc.player.getYRot() + 150);
          mc.player.yRotO = mc.player.getYRot();
        }
        case 47 -> key(GLFW.GLFW_KEY_ESCAPE);
        case 48 -> {
          require(mc.screen == menu, "shader background reuses browser");
          require(
              backgroundHash() != backgroundHash,
              "reopening samples current camera instead of cached world");
          capture(mc, "background-1.4.2-reopen.png");
          menu.setPauseGame(false);
          backgroundBefore = menu.timingReport().get("backgroundFrames").getAsLong();
          backgroundHash = backgroundHash();
        }
        case 49 -> {
          require(
              menu.timingReport().get("backgroundFrames").getAsLong() > backgroundBefore + 100,
              "background updates independently of HTML FPS");
          mc.player.setYRot(mc.player.getYRot() + 90);
          mc.player.yRotO = mc.player.getYRot();
        }
        case 50 -> {
          require(
              backgroundHash() != backgroundHash,
              "world changes reach shader while menu remains open");
          capture(mc, "background-1.4.2-live.png");
          menu.setPauseGame(true);
          GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720);
        }
        case 51 -> {
          require(menu.backgroundTexture() >= 0, "live frost target survives resize");
          capture(mc, "background-1.4.2-720.png");
          LogUtils.getLogger()
              .info("[Endfield QA] background final metrics {}", menu.timingReport());
          key(GLFW.GLFW_KEY_ESCAPE);
        }
        case 52 -> {
          require(mc.screen == null, "shader background final close");
          LogUtils.getLogger().info("[Endfield QA] ALL LIVE BACKGROUND CHECKS PASSED");
          mc.stop();
        }
      }
    } catch (Throwable e) {
      LogUtils.getLogger()
          .error(
              "[Endfield QA] FAILED phase {} metrics {}",
              phase - 1,
              menu == null ? null : menu.timingReport(),
              e);
      capture(mc, "hybrid-failure.png");
      mc.stop();
      phase = 999;
    }
  }

  private void film(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
    if (sequence == 0) return;
    long ms = (System.nanoTime() - sequenceStart) / 1_000_000;
    int[] times =
        sequence == 1
            ? new int[] {0, 80, 160, 240, 360, 520, 700, 900}
            : new int[] {0, 65, 130, 195, 260, 330, 420, 510};
    if (sequenceShot < times.length && ms >= times[sequenceShot]) {
      capture(
          Minecraft.getInstance(),
          "hybrid-film-" + sequence + "-" + String.format("%02d", sequenceShot) + ".png");
      sequenceShot++;
    }
    if (sequenceShot >= times.length) sequence = 0;
  }

  private static void require(boolean ok, String label) {
    if (!ok) throw new IllegalStateException(label);
    LogUtils.getLogger().info("[Endfield QA] PASS {}", label);
  }

  private long backgroundHash() {
    com.mojang.blaze3d.systems.RenderSystem.bindTexture(menu.backgroundTexture());
    int width =
        org.lwjgl.opengl.GL11.glGetTexLevelParameteri(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH);
    int height =
        org.lwjgl.opengl.GL11.glGetTexLevelParameteri(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT);
    try (var image = new com.mojang.blaze3d.platform.NativeImage(width, height, false)) {
      image.downloadTexture(0, false);
      long hash = 1;
      for (int y = 0; y < height; y += 7)
        for (int x = 0; x < width; x += 7)
          hash = hash * 31 + (image.getPixelRGBA(x, y) & 0x00f0f0f0);
      return hash;
    }
  }

  private static void capture(Minecraft mc, String name) {
    Screenshot.grab(
        mc.gameDirectory,
        name,
        mc.getMainRenderTarget(),
        s -> LogUtils.getLogger().info("[Endfield QA] {}", s.getString()));
  }

  private void click(String action) {
    var p = menu.actionPoint(action);
    require(p != null, "action exists " + action);
    LogUtils.getLogger().info("[Endfield QA] click {} {},{}", action, p.x, p.y);
    move(p.x, p.y);
    inputs.add(
        () -> {
          move(p.x, p.y);
          press(1);
        });
    inputs.add(
        () -> {
          move(p.x, p.y);
          press(0);
        });
  }

  private static void key(int code) {
    var mc = Minecraft.getInstance();
    mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), code, 0, GLFW.GLFW_PRESS, 0);
    mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), code, 0, GLFW.GLFW_RELEASE, 0);
  }

  private static void move(double x, double y) {
    var mc = Minecraft.getInstance();
    var w = mc.getWindow();
    double px = x * w.getScreenWidth() / w.getGuiScaledWidth(),
        py = y * w.getScreenHeight() / w.getGuiScaledHeight();
    GLFW.glfwSetCursorPos(w.getWindow(), px, py);
    invoke("onMove", new Class[] {long.class, double.class, double.class}, w.getWindow(), px, py);
  }

  private static void press(int action) {
    var mc = Minecraft.getInstance();
    invoke(
        "onPress",
        new Class[] {long.class, int.class, int.class, int.class},
        mc.getWindow().getWindow(),
        0,
        action,
        0);
  }

  private static void wheel(double amount) {
    var mc = Minecraft.getInstance();
    invoke(
        "onScroll",
        new Class[] {long.class, double.class, double.class},
        mc.getWindow().getWindow(),
        0d,
        amount);
  }

  private static void invoke(String name, Class<?>[] types, Object... args) {
    try {
      Method m = net.minecraft.client.MouseHandler.class.getDeclaredMethod(name, types);
      m.setAccessible(true);
      m.invoke(Minecraft.getInstance().mouseHandler, args);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
