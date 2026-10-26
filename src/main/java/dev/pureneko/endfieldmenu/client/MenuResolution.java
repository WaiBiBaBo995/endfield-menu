package dev.pureneko.endfieldmenu.client;

/** A preset is an upper bounding box; preserve the game window aspect ratio. */
public record MenuResolution(int width, int height) {
  public int rasterHeight() {return height + 64;}
  public static MenuResolution current() {
    var window = net.minecraft.client.Minecraft.getInstance().getWindow();
    return fit(MenuConfig.RESOLUTION.get(), window.getWidth(), window.getHeight());
  }
  public static MenuResolution fit(int preset, int width, int height) {
    width = Math.max(256, width);
    height = Math.max(64, height);
    if (preset == 0) return new MenuResolution(Math.max(256,width),Math.max(64,height));
    int h = switch(preset) { case 1 -> 720; case 2 -> 1080; case 3 -> 1440; default -> 2160; };
    double scale = Math.min(h * 16.0 / 9 / width, (double)h / height);
    return new MenuResolution(Math.max(256,(int)Math.round(width*scale)),Math.max(64,(int)Math.round(height*scale)));
  }
}
