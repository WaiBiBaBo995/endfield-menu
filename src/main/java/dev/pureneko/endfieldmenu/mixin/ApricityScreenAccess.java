package dev.pureneko.endfieldmenu.mixin;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.screen.ApricityScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ApricityScreen.class, remap = false)
public interface ApricityScreenAccess {
  @Accessor("linkedDocument")
  void endfieldDocument(Document document);
}
