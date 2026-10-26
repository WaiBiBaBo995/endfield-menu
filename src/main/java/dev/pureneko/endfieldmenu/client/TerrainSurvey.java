package dev.pureneko.endfieldmenu.client;

import dev.pureneko.endfieldmenu.terrain.TerrainGeometry;
import java.util.Arrays;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/** Access to chunks is confined to the client thread and a bounded per-frame budget. */
public final class TerrainSurvey {
  private final ClientLevel level;
  private final int centerX, centerZ, step, size;
  private final float[] heights;
  private int cursor;
  private TerrainGeometry.Mesh mesh;
  private java.util.concurrent.CompletableFuture<TerrainGeometry.Mesh> geometry;

  public TerrainSurvey(ClientLevel level, int centerX, int centerZ) {
    this.level = level;
    this.step = MenuConfig.SAMPLE_STEP.get();
    int halfSteps = MenuConfig.RADIUS.get() / step;
    this.size = halfSteps * 2 + 1;
    this.centerX = centerX;
    this.centerZ = centerZ;
    this.heights = new float[size * size];
    Arrays.fill(heights, Float.NaN);
  }

  public void advance() {
    if (complete()) return;
    if (geometry != null) {
      if (geometry.isDone()) mesh = geometry.join();
      return;
    }
    long deadline = System.nanoTime() + 750_000;
    int remaining = 192;
    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    do {
      int gridX = cursor % size, gridZ = cursor / size;
      int x = centerX + (gridX - size / 2) * step;
      int z = centerZ + (gridZ - size / 2) * step;
      // false is essential: this must never request or generate a chunk.
      LevelChunk chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, false);
      if (chunk != null) {
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        while (y >= level.getMinBuildHeight()) {
          BlockState state = chunk.getBlockState(pos.set(x, y, z));
          if ((!state.isAir()
                  && state.canOcclude()
                  && !state.is(BlockTags.LEAVES)
                  && !state.is(BlockTags.LOGS))
              || !state.getFluidState().isEmpty()) {
            heights[cursor] = y + 1;
            break;
          }
          y--;
        }
      }
      cursor++;
    } while (cursor < heights.length && --remaining > 0 && System.nanoTime() < deadline);
    if (cursor >= heights.length) {
      float[] snapshot = heights;
      int gridSize = size, gridStep = step, interval = MenuConfig.CONTOUR_INTERVAL.get();
      // Sampling has finished; the worker sees immutable numbers and never touches a level or
      // chunk.
      geometry =
          java.util.concurrent.CompletableFuture.supplyAsync(
              () -> TerrainGeometry.build(snapshot, gridSize, gridStep, interval));
    }
  }

  public boolean complete() {
    return mesh != null;
  }

  public int progress() {
    return cursor * 100 / heights.length;
  }

  public TerrainGeometry.Mesh mesh() {
    return mesh;
  }

  public int centerX() {
    return centerX;
  }

  public int centerZ() {
    return centerZ;
  }

  public int radius() {
    return size / 2 * step;
  }

  public double surfaceHeight(double x, double z) {
    int gridX = Math.clamp((int) Math.round((x - centerX) / step + (size - 1) / 2.0), 0, size - 1);
    int gridZ = Math.clamp((int) Math.round((z - centerZ) / step + (size - 1) / 2.0), 0, size - 1);
    float height = heights[gridZ * size + gridX];
    return Float.isFinite(height) ? height : mesh == null ? 64 : mesh.baseline();
  }
}
