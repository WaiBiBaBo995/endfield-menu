package dev.pureneko.endfieldmenu.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.*;

/** Two GPU-only blur passes, independent of the vanilla menu blur setting. */
final class FrostRenderer implements AutoCloseable {
  private TextureTarget horizontal, vertical, finished;
  private long frames;

  int texture() {
    return finished == null ? -1 : finished.getColorTextureId();
  }

  long frames() {
    return frames;
  }

  void render(GuiGraphics graphics, float amount) {
    if (NativeMenuRenderer.blurShader == null) return;
    graphics.flush();
    var mc = Minecraft.getInstance();
    var main = mc.getMainRenderTarget();
    int w = Math.max(1, main.width / 2), h = Math.max(1, main.height / 2);
    if (horizontal == null || horizontal.width != w || horizontal.height != h) {
      close();
      horizontal = new TextureTarget(w, h, false, Minecraft.ON_OSX);
      vertical = new TextureTarget(w, h, false, Minecraft.ON_OSX);
      finished = new TextureTarget(w, h, false, Minecraft.ON_OSX);
      finished.setFilterMode(GL11.GL_LINEAR);
      horizontal.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
      vertical.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
    }
    RenderSystem.disableDepthTest();
    RenderSystem.disableCull();
    RenderSystem.disableBlend();
    float radius = MenuConfig.FROST_RADIUS.get().floatValue() * amount;
    horizontal.bindWrite(true);
    pass(main.getColorTextureId(), radius / main.width, 0, 0, 0);
    vertical.bindWrite(true);
    pass(horizontal.getColorTextureId(), 0, radius / main.height, 0, 0);
    finished.bindWrite(true);
    pass(
        vertical.getColorTextureId(),
        0,
        0,
        MenuConfig.FROST_TINT.get().floatValue() * amount,
        MenuConfig.FROST_GRAIN.get().floatValue() * amount);
    main.bindWrite(true);
    pass(finished.getColorTextureId(), 0, 0, 0, 0);
    frames++;
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.enableCull();
    RenderSystem.enableDepthTest();
  }

  private static void pass(int texture, float dx, float dy, float tint, float grain) {
    var shader = NativeMenuRenderer.blurShader;
    RenderSystem.setShader(() -> shader);
    RenderSystem.setShaderTexture(0, texture);
    shader.safeGetUniform("Direction").set(dx, dy);
    shader.safeGetUniform("Finish").set(tint, grain, 0f, 0f);
    var b =
        Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    b.addVertex(-1, -1, 0).setUv(0, 0).setColor(-1);
    b.addVertex(1, -1, 0).setUv(1, 0).setColor(-1);
    b.addVertex(1, 1, 0).setUv(1, 1).setColor(-1);
    b.addVertex(-1, 1, 0).setUv(0, 1).setColor(-1);
    BufferUploader.drawWithShader(b.buildOrThrow());
  }

  @Override
  public void close() {
    if (horizontal != null) horizontal.destroyBuffers();
    if (vertical != null) vertical.destroyBuffers();
    if (finished != null) finished.destroyBuffers();
    horizontal = vertical = finished = null;
  }
}
