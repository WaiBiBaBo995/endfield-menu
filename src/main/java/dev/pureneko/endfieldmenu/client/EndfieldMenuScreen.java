package dev.pureneko.endfieldmenu.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.realmsclient.RealmsMainScreen;
import com.sighs.apricityui.element.Iframe;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.screen.ApricityScreen;
import dev.pureneko.endfieldmenu.mixin.ApricityScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;
import org.lwjgl.opengl.GL11;

/** Persistent AUI screen: AUI input, original browser layout, native terrain and frost. */
public final class EndfieldMenuScreen extends ApricityScreen {
  private final FrostRenderer frost = new FrostRenderer();
  private final NativeTerrainRenderer terrain = new NativeTerrainRenderer();
  private MenuWebBridge bridge;
  private Document document;
  private Iframe iframe;
  private TerrainSurvey survey, pending;
  private boolean active, avatarReady;
  private String phase = "hidden", failure;
  private long openedAt,
      closedAt,
      firstFrameAt,
      nextState,
      nextRefresh,
      lastFrame,
      avatarRetry,
      knownView;
  private double initMs, closeMs, exitMs;
  private int epoch;
  private long warmedView;
  private long hiddenAt;
  private boolean evicted;
  private int surveyRevision;
  private final java.util.ArrayDeque<Double> frameTimes = new java.util.ArrayDeque<>();

  public EndfieldMenuScreen() {
    super("endfield/host.html");
    setPauseGame(true);
    setShowDefaultBackground(false);
  }

  public void prewarm() {
    minecraft = Minecraft.getInstance();
    if (document != null || failure != null) return;
    try {
      bridge = new MenuWebBridge(this::activate);
      document = Document.create("endfield/host.html");
      if (document == null) throw new IllegalStateException("Missing endfield/host.html");
      ((ApricityScreenAccess) (Object) this).endfieldDocument(document);
      document.setManuallyRendered(true);
      document.applyViewport(true);
      iframe = (Iframe) document.getElementById("endfield-original-page");
      if (iframe == null) throw new IllegalStateException("Missing menu iframe");
      iframe.setAttribute("src", bridge.url());
      hiddenAt = System.nanoTime();
      publishState();
    } catch (Exception e) {
      failure = e.toString();
      com.mojang.logging.LogUtils.getLogger().error("Endfield page prewarm failed", e);
    }
  }

  @Override
  protected void init() {
    long start = System.nanoTime();
    prewarm();
    active = true;
    evicted = false;
    hiddenAt = 0;
    epoch++;
    phase = "in";
    openedAt = start;
    closedAt = 0;
    lastFrame = firstFrameAt = 0;
    frameTimes.clear();
    if (document != null) {
      document.setManuallyRendered(false);
      document.applyViewport(true);
    }
    if (survey == null && pending == null) refreshTerrain();
    publishState();
    var view = view();
    if (view != null) {
      view.setAutoCapture(frames() == null || !frames().usesStream());
      view.setFrameInterval(16);
      view.eval("window.endfieldSetActive&&window.endfieldSetActive(true," + epoch + ")");
    }
    var frames = frames();
    if (frames != null) frames.active(true);
    initMs = (System.nanoTime() - start) / 1e6;
  }

  @Override
  public void resize(Minecraft mc, int w, int h) {
    minecraft = mc;
    width = w;
    height = h;
    if (document != null) document.applyViewport(true);
  }

  private com.sighs.apricityui.spi.AuiWebViewService.View view() {
    return iframe == null ? null : ((MenuIframe) iframe).endfieldView();
  }

  private MenuFrameUpload frames() {
    return iframe == null ? null : ((MenuIframe) iframe).endfieldFrames();
  }

  public int terrainTexture() {
    return terrain.texture();
  }

  public String phase() {
    return phase;
  }

  public int epoch() {
    return epoch;
  }

  public float compositionAlpha() {
    return phase.equals("out")
        ? 1 - Math.clamp((System.nanoTime() - closedAt - 450_000_000L) / 50_000_000f, 0, 1)
        : 1;
  }

  public float mapYaw() {
    return terrain.yaw;
  }

  public float mapZoom() {
    return terrain.zoom;
  }

  public Vector2f actionPoint(String action) {
    if (bridge == null) return null;
    var r = bridge.report();
    if (!r.has("controls") || !r.getAsJsonObject("controls").has(action)) return null;
    var p = r.getAsJsonObject("controls").getAsJsonObject(action);
    return new Vector2f(
        p.get("x").getAsFloat() * width / r.get("width").getAsFloat(),
        p.get("y").getAsFloat() * height / r.get("height").getAsFloat());
  }

  public Vector2f mapCenter() {
    if (bridge == null || !bridge.report().has("map")) return new Vector2f(width / 2f, height / 2f);
    var r = bridge.report();
    var p = r.getAsJsonObject("map");
    return new Vector2f(
        p.get("x").getAsFloat() * width / r.get("width").getAsFloat(),
        p.get("y").getAsFloat() * height / r.get("height").getAsFloat());
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float partial) {
    long now = System.nanoTime();
    if (lastFrame != 0) {
      frameTimes.add((now - lastFrame) / 1e6);
      if (frameTimes.size() > 1200) frameTimes.remove();
    }
    lastFrame = now;
    service();
    if (phase.equals("out") && now - closedAt >= 520_000_000L) {
      minecraft.setScreen(null);
      return;
    }
    if (phase.equals("in") && now - openedAt >= 900_000_000L) phase = "idle";
    frost.render(
        g,
        phase.equals("out")
            ? 1 - Math.clamp((now - closedAt - 450_000_000L) / 50_000_000f, 0, 1)
            : Math.clamp((now - openedAt - 50_000_000L) / 50_000_000f, 0, 1));
    var captured = frames();
    if (captured != null && captured.epoch() == epoch) {
      terrain.yaw = captured.yaw();
      terrain.tilt = captured.tilt();
      terrain.zoom = captured.zoom();
    }
    terrain.render(
        survey, (int) (minecraft.getWindow().getHeight() * .95), (now % 600_000_000_000L) / 1e9f);
    if (document != null) super.render(g, mx, my, partial);
    if (failure != null)
      g.drawCenteredString(
          font, I18n.get("endfieldmenu.bridgeFailed", failure), width / 2, height / 2, 0xffffde00);
    var frames = frames();
    if (frames != null) {
      frames.active(true);
      if (firstFrameAt == 0 && frames.ready() && acceptsFrame(frames)) firstFrameAt = now;
    }
  }

  @Override
  public void renderBackground(GuiGraphics g, int x, int y, float partial) {}

  public boolean acceptsFrame(MenuFrameUpload frames) {
    return frames.epoch() == epoch;
  }

  public int backgroundTexture() {
    return frost.texture();
  }

  @Override
  public void onClose() {
    if (phase.equals("out")) return;
    phase = "out";
    closedAt = System.nanoTime();
    var view = view();
    if (view != null) view.eval("window.endfieldRequestClose&&window.endfieldRequestClose()");
  }

  @Override
  public void removed() {
    long start = System.nanoTime();
    if (phase.equals("out")) exitMs = (start - closedAt) / 1e6;
    active = false;
    hiddenAt = System.nanoTime();
    phase = "hidden";
    if (document != null) {
      document.clearFocus();
      document.setManuallyRendered(true);
    }
    var view = view();
    if (view != null) {
      view.eval("window.endfieldSetActive&&window.endfieldSetActive(false," + epoch + ")");
      view.setFocus(false);
      view.setAutoCapture(false);
    }
    var frames = frames();
    if (frames != null) frames.active(false);
    publishState();
    closeMs = (System.nanoTime() - start) / 1e6;
  }

  public void backgroundTick() {
    if (hiddenAt != 0 && !evicted && System.nanoTime() - hiddenAt >= 60_000_000_000L) {
      evicted = true;
      if (MenuConfig.LOW_MEMORY.get()) {
        disposeSession();
        avatarReady = false;
        knownView = warmedView = 0;
        return;
      }
      if (iframe != null) ((MenuIframe)iframe).endfieldEvictView();
      terrain.close();
      frost.close();
      survey = pending = null;
    }
    if (evicted) return;
    service();
  }

  private void service() {
    if (iframe != null) ((MenuIframe) iframe).endfieldPoll();
    if (pending != null) {
      pending.advance();
      if (pending.complete()) {
        survey = pending;
        pending = null;
        surveyRevision++;
      }
    }
    long now = System.nanoTime();
    if (active && pending == null && !minecraft.isPaused() && now >= nextRefresh) refreshTerrain();
    var view = view();
    if (view != null && knownView != view.id()) {
      knownView = view.id();
      view.setCaptureQuality(MenuConfig.LOSSLESS_CAPTURE.get() ? 2 : 3);
      view.setFrameInterval(16);
      view.setAutoCapture(frames() == null || !frames().usesStream());
      view.eval("window.endfieldSetActive&&window.endfieldSetActive(" + active + "," + epoch + ")");
    }
    if (!active
        && view != null
        && warmedView != knownView
        && frames() != null
        && frames().ready()) {
      view.setAutoCapture(false);
      frames().active(false);
      warmedView = knownView;
    }
    if (bridge != null && now >= nextState) {
      publishState();
      nextState = now + (active ? 200_000_000L : 1_000_000_000L);
    }
    if (bridge != null && !avatarReady && now >= avatarRetry) {
      captureAvatar();
      avatarRetry = now + 2_000_000_000L;
    }
  }

  private void refreshTerrain() {
    if (minecraft == null || minecraft.player == null || minecraft.level == null) return;
    if (pending == null)
      pending =
          new TerrainSurvey(
              minecraft.level, minecraft.player.getBlockX(), minecraft.player.getBlockZ());
    nextRefresh = System.nanoTime() + MenuConfig.REFRESH_SECONDS.get() * 1_000_000_000L;
  }

  private void publishState() {
    if (bridge == null || minecraft.player == null || minecraft.level == null) return;
    var p = minecraft.player;
    var j = new JsonObject();
    j.addProperty("active", active);
    j.addProperty("name", p.getGameProfile().getName());
    j.addProperty("epoch", epoch);
    j.addProperty("lossless", MenuConfig.LOSSLESS_CAPTURE.get());
    j.addProperty("diagnostics", Boolean.getBoolean("endfieldmenu.dev"));
    j.addProperty("id", p.getUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT));
    j.addProperty("level", p.experienceLevel);
    j.addProperty("health", (int) Math.ceil(p.getHealth()));
    j.addProperty("food", p.getFoodData().getFoodLevel());
    j.addProperty("x", p.getX());
    j.addProperty("y", p.getY());
    j.addProperty("z", p.getZ());
    String d = minecraft.level.dimension().location().toString(),
        key = "endfieldmenu.dimension." + d.replace(':', '.');
    j.addProperty("dimension", I18n.exists(key) ? I18n.get(key) : d);
    j.addProperty(
        "worldState",
        I18n.get(minecraft.hasSingleplayerServer() ? "endfieldmenu.paused" : "endfieldmenu.live"));
    j.addProperty(
        "survey",
        pending != null
            ? I18n.get("endfieldmenu.sampling", pending.progress())
            : survey == null
                ? I18n.get("endfieldmenu.noTerrain")
                : I18n.get(
                    "endfieldmenu.loaded",
                    survey.mesh().loaded() * 100 / Math.max(1, survey.mesh().total())));
    j.addProperty("interval", I18n.get("endfieldmenu.interval", MenuConfig.CONTOUR_INTERVAL.get()));
    var labels = new JsonObject();
    for (String k :
        new String[] {
          "resume",
          "inventory",
          "advancements",
          "stats",
          "settings",
          "mapReset",
          "viewReset",
          "refresh",
          "locked",
          "controls"
        }) labels.addProperty(k, I18n.get("endfieldmenu." + k));
    j.add("labels", labels);
    var legend = new com.google.gson.JsonArray();
    for (int y : MenuConfig.ALTITUDES.get()) {
      var a = new JsonObject();
      a.addProperty("y", y);
      a.addProperty("color", String.format("#%06x", NativeTerrainRenderer.color(y)));
      legend.add(a);
    }
    j.add("legend", legend);
    j.addProperty("avatarReady", avatarReady);
    bridge.state(j);
  }

  private void captureAvatar() {
    if (minecraft.player == null) return;
    try {
      minecraft.getTextureManager().getTexture(minecraft.player.getSkin().texture()).bind();
      int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH),
          h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
      if (w < 64 || h < 32 || w > 2048 || h > 2048) return;
      try (var skin = new NativeImage(w, h, false);
          var face = new NativeImage(8, 8, true)) {
        skin.downloadTexture(0, false);
        int scale = w / 64;
        for (int y = 0; y < 8; y++)
          for (int x = 0; x < 8; x++) {
            int base = skin.getPixelRGBA((x + 8) * scale, (y + 8) * scale),
                hat = skin.getPixelRGBA((x + 40) * scale, (y + 8) * scale);
            float a = (hat >>> 24) / 255f;
            int out = 0xff000000;
            for (int shift = 0; shift < 24; shift += 8)
              out |=
                  ((int) (((base >>> shift) & 255) * (1 - a) + ((hat >>> shift) & 255) * a))
                      << shift;
            face.setPixelRGBA(x, y, out);
          }
        bridge.avatar(face.asByteArray());
        avatarReady = true;
      }
    } catch (Exception e) {
      com.mojang.logging.LogUtils.getLogger().debug("Endfield skin not ready", e);
    }
  }

  private void activate(String action) {
    if (!active || minecraft.screen != this || minecraft.player == null || phase.equals("out"))
      return;
    if (action.equals("close")) {
      onClose();
      return;
    }
    switch (action) {
      case "resume" -> onClose();
      case "inventory" -> minecraft.setScreen(new InventoryScreen(minecraft.player));
      case "advancements" -> {
        if (minecraft.getConnection() != null)
          minecraft.setScreen(
              new AdvancementsScreen(minecraft.getConnection().getAdvancements(), this));
      }
      case "stats" -> minecraft.setScreen(new StatsScreen(this, minecraft.player.getStats()));
      case "settings" -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
      case "refresh" -> refreshTerrain();
      case "quit" ->
          minecraft.setScreen(
              new ConfirmScreen(
                  yes -> {
                    if (yes) disconnect();
                    else minecraft.setScreen(this);
                  },
                  Component.translatable("endfieldmenu.quit.title"),
                  Component.translatable(
                      minecraft.hasSingleplayerServer()
                          ? "endfieldmenu.quit.singleplayer"
                          : "endfieldmenu.quit.multiplayer")));
    }
  }

  private void disconnect() {
    boolean local = minecraft.hasSingleplayerServer();
    ServerData server = minecraft.getCurrentServer();
    boolean realms = server != null && server.isRealm();
    minecraft.level.disconnect();
    if (local)
      minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
    else minecraft.disconnect();
    TitleScreen title = new TitleScreen();
    minecraft.setScreen(
        local ? title : realms ? new RealmsMainScreen(title) : new JoinMultiplayerScreen(title));
  }

  public JsonObject timingReport() {
    var j = new JsonObject();
    j.addProperty("renderer", "HTML / AUI input + native terrain");
    j.addProperty("initMs", initMs);
    j.addProperty("closeMs", closeMs);
    j.addProperty("exitAnimationMs", exitMs);
    j.addProperty("firstDrawMs", firstFrameAt == 0 ? -1 : (firstFrameAt - openedAt) / 1e6);
    j.addProperty("viewId", knownView);
    j.addProperty("gameFps", minecraft.getFps());
    j.addProperty("phase", phase);
    j.addProperty("cacheEvicted", evicted);
    j.addProperty("surveyRevision", surveyRevision);
    j.addProperty("epoch", epoch);
    j.addProperty("backgroundBackend", "live-gpu-shader");
    j.addProperty("backgroundFrames", frost.frames());
    double[] times = frameTimes.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    j.addProperty("frameP95Ms", times.length == 0 ? -1 : times[(int) (times.length * .95)]);
    j.add("browser", frames() == null ? new JsonObject() : frames().metrics());
    j.add("page", bridge == null ? new JsonObject() : bridge.report());
    if (view() != null) j.addProperty("capture", view().status());
    return j;
  }

  public void disposeSession() {
    if (document != null) {
      document.remove();
      document = null;
      iframe = null;
      ((ApricityScreenAccess) (Object) this).endfieldDocument(null);
    }
    if (bridge != null) {
      bridge.close();
      bridge = null;
    }
    terrain.close();
    frost.close();
    survey = pending = null;
  }
}
