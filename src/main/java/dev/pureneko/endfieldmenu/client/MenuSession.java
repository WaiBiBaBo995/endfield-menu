package dev.pureneko.endfieldmenu.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Browser, terrain and GPU resources are retained for the connected world. */
public final class MenuSession {
  private static EndfieldMenuScreen menu;
  private static ClientLevel world;
  private static int ticks;

  private MenuSession() {}

  public static EndfieldMenuScreen screen() {
    if (menu == null) menu = new EndfieldMenuScreen();
    return menu;
  }

  public static void tick(ClientTickEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (world != mc.level || !MenuConfig.ENABLED.get()) {
      if (menu != null) menu.disposeSession();
      menu = null;
      world = mc.level;
      ticks = 0;
    }
    if (!MenuConfig.ENABLED.get()) return;
    if (mc.level != null && ++ticks == 40) screen().prewarm();
    if (menu != null && mc.screen != menu && mc.level != null) menu.backgroundTick();
  }
}
