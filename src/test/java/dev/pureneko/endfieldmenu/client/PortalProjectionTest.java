package dev.pureneko.endfieldmenu.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.*;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class PortalProjectionTest {
  private ByteBuffer header(float[][] corners, int epoch) {
    var bytes = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
    bytes.put(new byte[] {69, 70, 77, 50});
    for (var p : corners) bytes.putFloat(p[0]).putFloat(p[1]);
    bytes.putInt(40, epoch);
    var image = ByteBuffer.allocate(512 * 256 * 4);
    for (int i = 0; i < 384; i++) {
      int value = (bytes.get(i / 8) & (1 << (i % 8))) == 0 ? 12 : 241;
      for (int y = 0; y < 4; y++)
        for (int x = 0; x < 4; x++) {
          int p = ((i / 64 * 4 + y) * 512 + i % 64 * 4 + x) * 4;
          image
              .put(p, (byte) value)
              .put(p + 1, (byte) value)
              .put(p + 2, (byte) value)
              .put(p + 3, (byte) 255);
        }
    }
    return image;
  }

  @Test
  void perspectiveCornersMapToTheSameCapturedPortal() {
    float[][] points = {{93, 27}, {411, 51}, {463, 219}, {47, 233}};
    var image = header(points, 812);
    var m = PortalProjection.decode(image, 512, 256);
    assertNotNull(m);
    float[][] expected = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    for (int i = 0; i < 4; i++) {
      var p = m.transform(new Vector4f(points[i][0] / 512, points[i][1] / 256, 0, 1));
      assertEquals(expected[i][0], p.x / p.w, 1e-5);
      assertEquals(expected[i][1], p.y / p.w, 1e-5);
    }
    assertEquals(812, PortalProjection.readInt(image, 512, 40));
  }

  @Test
  void hiddenCollapsedMapKeepsThePageHeaderButHasNoProjection() {
    var image = header(new float[][] {{0, 0}, {0, 0}, {0, 0}, {0, 0}}, 7);
    assertTrue(PortalProjection.hasHeader(image, 512, 256));
    assertNull(PortalProjection.decode(image, 512, 256));
  }

  @Test
  void invalidHeaderAndNonFiniteCornersAreRejected() {
    assertNull(PortalProjection.decode(ByteBuffer.allocate(8), 1, 1));
    var image = header(new float[][] {{Float.NaN, 0}, {400, 30}, {450, 200}, {20, 200}}, 1);
    assertNull(PortalProjection.decode(image, 512, 256));
    int pixel = 2 * 512 * 4 + 2 * 4;
    image.put(pixel, (byte) 0).put(pixel + 1, (byte) 0).put(pixel + 2, (byte) 0);
    assertFalse(PortalProjection.hasHeader(image, 512, 256));
  }
}
