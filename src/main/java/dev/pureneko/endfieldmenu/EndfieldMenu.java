package dev.pureneko.endfieldmenu;

import dev.pureneko.endfieldmenu.client.MenuConfig;
import dev.pureneko.endfieldmenu.client.MenuSession;
import dev.pureneko.endfieldmenu.client.NativeMenuRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** No entry point is loaded on a dedicated server. */
@Mod(value = EndfieldMenu.ID, dist = Dist.CLIENT)
public final class EndfieldMenu {
  public static final String ID = "endfieldmenu";

  public EndfieldMenu(IEventBus modBus, ModContainer container) {
    container.registerConfig(ModConfig.Type.CLIENT, MenuConfig.SPEC);
    NeoForge.EVENT_BUS.addListener(this::openingScreen);
    NeoForge.EVENT_BUS.addListener(MenuSession::tick);
    modBus.addListener(NativeMenuRenderer::registerShaders);
  }

  private void openingScreen(ScreenEvent.Opening event) {
    Minecraft minecraft = Minecraft.getInstance();
    if (MenuConfig.ENABLED.get()
        && minecraft.level != null
        && minecraft.player != null
        && event.getNewScreen() instanceof PauseScreen pause
        && pause.showsPauseMenu()) {
      event.setNewScreen(MenuSession.screen());
    }
  }
}
