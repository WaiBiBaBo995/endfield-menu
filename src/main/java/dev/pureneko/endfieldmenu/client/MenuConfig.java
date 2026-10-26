package dev.pureneko.endfieldmenu.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class MenuConfig {
  private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
  public static final ModConfigSpec.BooleanValue ENABLED =
      BUILDER.comment("Replace the in-world Esc pause menu.").define("enabled", true);
  public static final ModConfigSpec.IntValue RESOLUTION =
      BUILDER.comment("Menu webpage only: 0=window, 1=720p, 2=1080p, 3=1440p, 4=2160p. Aspect ratio is preserved.")
          .defineInRange("menuResolution", 0, 0, 4);
  public static final ModConfigSpec.BooleanValue LOW_MEMORY =
      BUILDER.comment("Close the menu browser after 60 seconds hidden; reopening loads it again.")
          .define("lowMemoryMode", false);
  public static final ModConfigSpec.IntValue RADIUS =
      BUILDER
          .comment("Survey radius in blocks. Only already loaded client chunks are sampled.")
          .defineInRange("radius", 128, 32, 256);
  public static final ModConfigSpec.IntValue SAMPLE_STEP =
      BUILDER
          .comment("Blocks between terrain samples. Smaller values show more detail.")
          .defineInRange("sampleStep", 4, 2, 8);
  public static final ModConfigSpec.IntValue CONTOUR_INTERVAL =
      BUILDER
          .comment("Height difference in blocks between real contour lines.")
          .defineInRange("contourInterval", 8, 2, 32);
  public static final ModConfigSpec.IntValue REFRESH_SECONDS =
      BUILDER
          .comment(
              "Refresh interval while the world is running. Paused worlds keep their snapshot.")
          .defineInRange("refreshSeconds", 5, 2, 60);
  public static final ModConfigSpec.DoubleValue HEIGHT_SCALE =
      BUILDER.comment("Vertical terrain exaggeration.").defineInRange("heightScale", 1.1, 0.2, 3.0);
  public static final ModConfigSpec.DoubleValue FROST_RADIUS =
      BUILDER
          .comment("Independent background blur radius in physical pixels; 0 disables blur.")
          .defineInRange("frostRadius", 14.0, 0.0, 24.0);
  public static final ModConfigSpec.DoubleValue FROST_TINT =
      BUILDER.defineInRange("frostTint", .50, 0.0, .8);
  public static final ModConfigSpec.BooleanValue LOSSLESS_CAPTURE =
      BUILDER
          .comment(
              "Lossless PNG preserves exact alpha but is slower on WebView2. Default JPEG"
                  + " uses shader matte removal with protected player avatar colors.")
          .define("losslessCapture", false);
  public static final ModConfigSpec.BooleanValue DIRECT_CAPTURE =
      BUILDER
          .comment(
              "Use complete browser compositor frames. Falls back to AUI capture if unavailable.")
          .define("directCapture", true);
  public static final ModConfigSpec.DoubleValue FROST_GRAIN =
      BUILDER.defineInRange("frostGrain", .018, 0.0, .08);
  public static final ModConfigSpec.ConfigValue<java.util.List<? extends Integer>> ALTITUDES =
      BUILDER
          .comment(
              "Five strictly increasing absolute Y anchors: blue, green, yellow, brown, white.")
          .defineList(
              "contourAltitudes",
              java.util.List.of(63, 96, 144, 200, 256),
              o -> o instanceof Integer);
  public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> COLORS =
      BUILDER
          .comment("Five contour colors as hexadecimal RGB.")
          .defineList(
              "contourColors",
              java.util.List.of("4A9FE8", "77C98A", "E4CF70", "B88558", "F4F3ED"),
              o -> o instanceof String s && s.matches("[0-9a-fA-F]{6}"));
  public static final ModConfigSpec SPEC = BUILDER.build();

  private MenuConfig() {}
}
