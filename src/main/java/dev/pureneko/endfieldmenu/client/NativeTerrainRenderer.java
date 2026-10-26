package dev.pureneko.endfieldmenu.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.pureneko.endfieldmenu.terrain.TerrainGeometry;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/** Persistent GPU height-field buffers, rebuilt only when a survey snapshot changes. */
final class NativeTerrainRenderer implements AutoCloseable {
  private TextureTarget target;
  private VertexBuffer faces, contours;
  private TerrainGeometry.Mesh uploaded;
  float yaw = 0, tilt = .95f, zoom = 1.45f;

  void reset() {
    yaw = 0;
    tilt = .95f;
    zoom = 1.45f;
  }

  int texture() {
    return target == null ? -1 : target.getColorTextureId();
  }

  void render(TerrainSurvey survey, int resolution, float time) {
    if (NativeMenuRenderer.terrainShader == null) return;
    int size = Math.clamp(resolution, 512, 2048);
    if (target == null || target.width != size) {
      if (target != null) target.destroyBuffers();
      target = new TextureTarget(size, size, true, Minecraft.ON_OSX);
      target.setClearColor(.035f, .039f, .047f, 1);
      target.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
    }
    if (survey != null && survey.mesh() != uploaded) upload(survey.mesh());
    target.clear(Minecraft.ON_OSX);
    target.bindWrite(true);
    RenderSystem.enableDepthTest();
    RenderSystem.depthMask(true);
    RenderSystem.disableCull();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    var shader = NativeMenuRenderer.terrainShader;
    float radius = survey == null ? 128 : survey.radius(),
        baseline = uploaded == null ? 64 : (float) uploaded.baseline();
    shader.safeGetUniform("View").set(yaw, tilt, zoom, radius);
    shader.safeGetUniform("Resolution").set((float) size, (float) size);
    var currentPlayer = Minecraft.getInstance().player;
    shader
        .safeGetUniform("Scanner")
        .set(
            survey == null || currentPlayer == null
                ? 0f
                : (float) (currentPlayer.getX() - survey.centerX()),
            survey == null || currentPlayer == null
                ? 0f
                : (float) (currentPlayer.getZ() - survey.centerZ()),
            time * .48f,
            time);
    shader
        .safeGetUniform("Terrain")
        .set(
            baseline,
            MenuConfig.HEIGHT_SCALE.get().floatValue(),
            0f,
            (time % 5 / 5 * 2 - 1) * radius);
    if (faces != null) draw(faces);
    shader
        .safeGetUniform("Terrain")
        .set(
            baseline,
            MenuConfig.HEIGHT_SCALE.get().floatValue(),
            1f,
            (time % 5 / 5 * 2 - 1) * radius);
    RenderSystem.lineWidth(1.35f);
    if (contours != null) draw(contours);
    RenderSystem.lineWidth(1);
    if (survey != null && Minecraft.getInstance().player != null) {
      var player = Minecraft.getInstance().player;
      float px = (float) (player.getX() - survey.centerX()),
          pz = (float) (player.getZ() - survey.centerZ());
      float py = (float) survey.surfaceHeight(player.getX(), player.getZ()) + 2;
      double a = Math.toRadians(player.getYRot());
      float dx = (float) -Math.sin(a), dz = (float) Math.cos(a), r = radius * .016f;
      RenderSystem.setShader(() -> shader);
      shader
          .safeGetUniform("Terrain")
          .set(baseline, MenuConfig.HEIGHT_SCALE.get().floatValue(), 3f, 10000f);
      var b =
          Tesselator.getInstance()
              .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      b.addVertex(px + dx * r * 2, py, pz + dz * r * 2).setColor(255, 226, 65, 255);
      b.addVertex(px - dx * r - dz * r, py, pz - dz * r + dx * r).setColor(255, 226, 65, 255);
      b.addVertex(px - dx * r + dz * r, py, pz - dz * r - dx * r).setColor(255, 226, 65, 255);
      BufferUploader.drawWithShader(b.buildOrThrow());
      var rings =
          Tesselator.getInstance()
              .begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
      for (float rr : new float[] {radius * .038f, radius * .067f})
        for (int i = 0; i < 64; i++) {
          double t = i * Math.PI / 32, u = (i + 1) * Math.PI / 32;
          rings
              .addVertex(px + (float) Math.cos(t) * rr, py, pz + (float) Math.sin(t) * rr)
              .setColor(218, 202, 35, 180);
          rings
              .addVertex(px + (float) Math.cos(u) * rr, py, pz + (float) Math.sin(u) * rr)
              .setColor(218, 202, 35, 180);
        }
      BufferUploader.drawWithShader(rings.buildOrThrow());
    }
    VertexBuffer.unbind();
    Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
    RenderSystem.enableCull();
    RenderSystem.disableDepthTest();
  }

  private static void draw(VertexBuffer buffer) {
    buffer.bind();
    buffer.drawWithShader(new Matrix4f(), new Matrix4f(), NativeMenuRenderer.terrainShader);
    VertexBuffer.unbind();
  }

  private void upload(TerrainGeometry.Mesh mesh) {
    if (mesh == null) return;
    if (faces != null) faces.close();
    if (contours != null) contours.close();
    faces = contours = null;
    uploaded = mesh;
    if (!mesh.faces().isEmpty()) {
      var b =
          Tesselator.getInstance()
              .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      for (var f : mesh.faces()) {
        int light = (int) (22 + f.light() * 31);
        vertex(b, f.a(), light, light + 2, light + 3);
        vertex(b, f.b(), light, light + 2, light + 3);
        vertex(b, f.c(), light, light + 2, light + 3);
      }
      faces = buffer(b);
    }
    if (!mesh.contours().isEmpty()) {
      var b =
          Tesselator.getInstance()
              .begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
      for (var c : mesh.contours()) {
        int color = color(c.elevation());
        vertex(b, c.a(), color >> 16 & 255, color >> 8 & 255, color & 255);
        vertex(b, c.b(), color >> 16 & 255, color >> 8 & 255, color & 255);
      }
      contours = buffer(b);
    }
  }

  private static VertexBuffer buffer(BufferBuilder b) {
    var v = new VertexBuffer(VertexBuffer.Usage.STATIC);
    v.bind();
    v.upload(b.buildOrThrow());
    VertexBuffer.unbind();
    return v;
  }

  private static void vertex(BufferBuilder b, TerrainGeometry.Point p, int r, int g, int blue) {
    b.addVertex((float) p.x(), (float) p.y(), (float) p.z()).setColor(r, g, blue, 255);
  }

  static int color(float y) {
    var levels = MenuConfig.ALTITUDES.get();
    var colors = MenuConfig.COLORS.get();
    int[] fallback = {63, 96, 144, 200, 256};
    int[] rgb = {0x4a9fe8, 0x77c98a, 0xe4cf70, 0xb88558, 0xf4f3ed};
    boolean valid = levels.size() == 5;
    for (int i = 1; i < levels.size(); i++) valid &= levels.get(i) > levels.get(i - 1);
    if (colors.size() == 5)
      for (int i = 0; i < 5; i++) rgb[i] = Integer.parseInt(colors.get(i), 16);
    int i = 0;
    while (i < 3 && y > (valid ? levels.get(i + 1) : fallback[i + 1])) i++;
    float a = valid ? levels.get(i) : fallback[i], z = valid ? levels.get(i + 1) : fallback[i + 1];
    float t = Math.clamp((y - a) / (z - a), 0, 1);
    t = t * t * (3 - 2 * t);
    int c = 0;
    for (int shift : new int[] {0, 8, 16})
      c |= (int) (((rgb[i] >> shift) & 255) * (1 - t) + ((rgb[i + 1] >> shift) & 255) * t) << shift;
    return c;
  }

  @Override
  public void close() {
    if (target != null) target.destroyBuffers();
    if (faces != null) faces.close();
    if (contours != null) contours.close();
    target = null;
    faces = contours = null;
    uploaded = null;
  }
}
