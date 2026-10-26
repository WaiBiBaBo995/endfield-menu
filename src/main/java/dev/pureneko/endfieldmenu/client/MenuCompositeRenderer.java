package dev.pureneko.endfieldmenu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.sighs.apricityui.render.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

/** Same-frame projected HTML map portal, preserving CSS perspective, masks and occlusion. */
public final class MenuCompositeRenderer {
  static ShaderInstance shader;

  public static void draw(PoseStack pose, Rect rect, MenuFrameUpload frames) {
    if (shader == null || frames.texture() < 0) return;
    com.sighs.apricityui.render.Base.commitDraws();
    var mc = Minecraft.getInstance();
    if (mc.screen instanceof EndfieldMenuScreen menu && !menu.acceptsFrame(frames)) return;
    var size = MenuResolution.current();
    if (frames.width() != size.width() || frames.height() != size.height()) return;
    int map = mc.screen instanceof EndfieldMenuScreen menu ? menu.terrainTexture() : -1;
    var p = rect.getContentPosition();
    var s = rect.getElementSize();
    float x = (float) p.x, y = (float) p.y, w = (float) s.width(), h = (float) s.height();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableDepthTest();
    RenderSystem.disableCull();
    RenderSystem.setShader(() -> shader);
    RenderSystem.setShaderTexture(0, frames.texture());
    RenderSystem.setShaderTexture(1, map < 0 ? frames.texture() : map);
    int background = mc.screen instanceof EndfieldMenuScreen menu ? menu.backgroundTexture() : -1;
    RenderSystem.setShaderTexture(2, background < 0 ? frames.texture() : background);
    shader
        .safeGetUniform("KeyedBackground")
        .set(frames.keyedBackground() && background >= 0 ? 1f : 0f);
    shader.safeGetUniform("MapReady").set(map < 0 || frames.projection() == null ? 0f : 1f);
    float[] ax=frames.avatarX(),ay=frames.avatarY();
    shader.safeGetUniform("AvatarX").set(ax[0],ax[1],ax[2],ax[3]);
    shader.safeGetUniform("AvatarY").set(ay[0],ay[1],ay[2],ay[3]);
    shader
        .safeGetUniform("PageOpacity")
        .set(mc.screen instanceof EndfieldMenuScreen menu ? menu.compositionAlpha() : 1f);
    shader
        .safeGetUniform("ScreenToMap")
        .set(frames.projection() == null ? new org.joml.Matrix4f() : frames.projection());
    shader
        .safeGetUniform("Raster")
        .set((float) frames.width(), (float) frames.height());
    var b =
        Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    var m = pose.last().pose();
    b.addVertex(m, x, y + h, 0).setUv(0, 1).setColor(-1);
    b.addVertex(m, x + w, y + h, 0).setUv(1, 1).setColor(-1);
    b.addVertex(m, x + w, y, 0).setUv(1, 0).setColor(-1);
    b.addVertex(m, x, y, 0).setUv(0, 0).setColor(-1);
    BufferUploader.drawWithShader(b.buildOrThrow());
    frames.presented();
    RenderSystem.enableCull();
    RenderSystem.enableDepthTest();
  }
}
