package dev.pureneko.endfieldmenu.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Only terrain, frost and HTML/terrain composition run natively. */
public final class NativeMenuRenderer {
  static ShaderInstance blurShader, terrainShader;

  public static void registerShaders(RegisterShadersEvent event) {
    try {
      event.registerShader(
          new ShaderInstance(
              event.getResourceProvider(), id("frost"), DefaultVertexFormat.POSITION_TEX_COLOR),
          s -> blurShader = s);
      event.registerShader(
          new ShaderInstance(
              event.getResourceProvider(), id("terrain"), DefaultVertexFormat.POSITION_COLOR),
          s -> terrainShader = s);
      event.registerShader(
          new ShaderInstance(
              event.getResourceProvider(),
              id("menu_composite"),
              DefaultVertexFormat.POSITION_TEX_COLOR),
          s -> MenuCompositeRenderer.shader = s);
    } catch (IOException e) {
      throw new IllegalStateException("Endfield shader failed", e);
    }
  }

  private static ResourceLocation id(String name) {
    return ResourceLocation.fromNamespaceAndPath("endfieldmenu", name);
  }
}
