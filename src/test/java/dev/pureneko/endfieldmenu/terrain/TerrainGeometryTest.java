package dev.pureneko.endfieldmenu.terrain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TerrainGeometryTest {
  @Test
  void planarSlopeProducesExactHeightIntersections() {
    var mesh = TerrainGeometry.build(new float[] {0, 16, 0, 16}, 2, 4, 8);
    assertEquals(2, mesh.faces().size());
    var levelEight = mesh.contours().stream().filter(line -> line.elevation() == 8).toList();
    assertEquals(2, levelEight.size());
    for (var line : levelEight) {
      assertEquals(0, line.a().x(), 1e-6);
      assertEquals(0, line.b().x(), 1e-6);
      assertEquals(8, line.a().y(), 1e-6);
      assertEquals(8, line.b().y(), 1e-6);
    }
  }

  @Test
  void unknownChunksAreNeverBridgedByTriangles() {
    var mesh = TerrainGeometry.build(new float[] {64, Float.NaN, 64, 80}, 2, 4, 8);
    assertEquals(3, mesh.loaded());
    assertEquals(1, mesh.faces().size());
    assertTrue(
        mesh.faces().stream()
            .allMatch(
                face ->
                    Double.isFinite(face.a().y())
                        && Double.isFinite(face.b().y())
                        && Double.isFinite(face.c().y())));
  }

  @Test
  void flatSurfaceHasNoPhantomContours() {
    var mesh = TerrainGeometry.build(new float[] {64, 64, 64, 64}, 2, 4, 8);
    assertEquals(64, mesh.baseline());
    assertEquals(2, mesh.faces().size());
    assertTrue(mesh.contours().isEmpty());
  }

  @Test
  void negativeHeightsKeepCorrectContourLevels() {
    var mesh = TerrainGeometry.build(new float[] {-19, -1, -19, -1}, 2, 4, 8);
    assertTrue(mesh.contours().stream().anyMatch(line -> line.elevation() == -16));
    assertTrue(mesh.contours().stream().anyMatch(line -> line.elevation() == -8));
    assertTrue(mesh.contours().stream().noneMatch(line -> line.elevation() == 0));
  }

  @Test
  void allMissingTerrainRemainsEmpty() {
    var mesh =
        TerrainGeometry.build(new float[] {Float.NaN, Float.NaN, Float.NaN, Float.NaN}, 2, 4, 8);
    assertEquals(0, mesh.loaded());
    assertTrue(mesh.faces().isEmpty());
    assertTrue(mesh.contours().isEmpty());
    assertTrue(Double.isFinite(mesh.baseline()));
  }
}
