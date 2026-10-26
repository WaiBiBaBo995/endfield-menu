package dev.pureneko.endfieldmenu.client;

import com.sighs.apricityui.spi.AuiWebViewService;

/** Implemented by the narrowly scoped AUI iframe mixin. */
public interface MenuIframe {
  AuiWebViewService.View endfieldView();

  MenuFrameUpload endfieldFrames();

  void endfieldPoll();

  void endfieldEvictView();
}
