package dev.pureneko.endfieldmenu.mixin;

import dev.pureneko.endfieldmenu.client.*;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsMixin {
  @Inject(method="addOptions",at=@At("TAIL"))
  private void endfield$options(CallbackInfo ci) {
    OptionsList list = ((OptionsSubScreenAccess)this).endfieldOptions();
    if (list == null) return;
    list.addBig(new OptionInstance<Integer>("endfieldmenu.resolution",OptionInstance.noTooltip(),
        (label,value)-> {
          var window=net.minecraft.client.Minecraft.getInstance().getWindow();
          var size=MenuResolution.fit(value,window.getWidth(),window.getHeight());
          Component mode=value==0?Component.translatable("endfieldmenu.resolution.auto"):Component.literal(switch(value){case 1->"1280×720";case 2->"1920×1080";case 3->"2560×1440";default->"3840×2160";});
          return Options.genericValueLabel(label,Component.translatable("endfieldmenu.resolution.value",mode,size.width(),size.height()));
        },new OptionInstance.Enum<Integer>(java.util.List.of(0,1,2,3,4),com.mojang.serialization.Codec.INT),MenuConfig.RESOLUTION.get(),value->{MenuConfig.RESOLUTION.set(value);MenuConfig.SPEC.save();}));
    list.addBig(OptionInstance.createBoolean("endfieldmenu.lowMemory",MenuConfig.LOW_MEMORY.get(),value->{MenuConfig.LOW_MEMORY.set(value);MenuConfig.SPEC.save();}));
  }
}
