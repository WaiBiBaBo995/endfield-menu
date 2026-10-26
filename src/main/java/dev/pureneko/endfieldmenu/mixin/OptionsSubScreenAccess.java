package dev.pureneko.endfieldmenu.mixin;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.components.OptionsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(OptionsSubScreen.class)
public interface OptionsSubScreenAccess {
  @Accessor("list") OptionsList endfieldOptions();
}
