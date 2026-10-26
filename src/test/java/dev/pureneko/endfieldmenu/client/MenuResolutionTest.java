package dev.pureneko.endfieldmenu.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MenuResolutionTest {
  @Test void explicitPresetsOnlyChangeWebRaster() {
    assertEquals(new MenuResolution(1920,1080),MenuResolution.fit(2,2560,1440));
    assertEquals(new MenuResolution(3840,2160),MenuResolution.fit(4,1920,1080));
    assertEquals(new MenuResolution(2560,1440),MenuResolution.fit(0,2560,1440));
  }
  @Test void ultrawideAndPortraitKeepWindowAspect() {
    var wide=MenuResolution.fit(2,3440,1440);
    assertEquals(1920,wide.width());
    assertEquals(804,wide.height());
    var portrait=MenuResolution.fit(2,1080,1920);
    assertEquals(608,portrait.width());
    assertEquals(1080,portrait.height());
  }
  @Test void MinimizedWindowHasBoundedRaster() {
    assertEquals(new MenuResolution(256,64),MenuResolution.fit(0,0,0));
  }
}
