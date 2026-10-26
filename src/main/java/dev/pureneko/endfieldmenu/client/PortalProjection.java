package dev.pureneko.endfieldmenu.client;

import java.nio.ByteBuffer;
import org.joml.Matrix4f;

/** Decode the map's four projected corners from the captured image itself. */
final class PortalProjection {
  static Matrix4f decode(ByteBuffer rgba, int width, int height) {
    if (!hasHeader(rgba, width, 32)) return null;
    double[][] a = new double[8][9];
    double[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    for (int i = 0; i < 4; i++) {
      double x = read(rgba, width, 4 + i * 8) / width,
          y = read(rgba, width, 8 + i * 8) / height,
          u = uv[i][0],
          v = uv[i][1];
      if (!Double.isFinite(x) || !Double.isFinite(y)) return null;
      a[i * 2] = new double[] {x, y, 1, 0, 0, 0, -u * x, -u * y, u};
      a[i * 2 + 1] = new double[] {0, 0, 0, x, y, 1, -v * x, -v * y, v};
    }
    for (int col = 0; col < 8; col++) {
      int pivot = col;
      for (int i = col + 1; i < 8; i++)
        if (Math.abs(a[i][col]) > Math.abs(a[pivot][col])) pivot = i;
      if (Math.abs(a[pivot][col]) < 1e-10) return null;
      double[] tmp = a[col];
      a[col] = a[pivot];
      a[pivot] = tmp;
      double divisor = a[col][col];
      for (int j = col; j < 9; j++) a[col][j] /= divisor;
      for (int i = 0; i < 8; i++)
        if (i != col) {
          double f = a[i][col];
          for (int j = col; j < 9; j++) a[i][j] -= a[col][j] * f;
        }
    }
    return new Matrix4f()
        .zero()
        .m00((float) a[0][8])
        .m10((float) a[1][8])
        .m30((float) a[2][8])
        .m01((float) a[3][8])
        .m11((float) a[4][8])
        .m31((float) a[5][8])
        .m03((float) a[6][8])
        .m13((float) a[7][8])
        .m33(1)
        .m22(1);
  }

  static int readByte(ByteBuffer b, int width, int index) {
    int value = 0;
    for (int bit = 0; bit < 8; bit++) {
      int n = index * 8 + bit, p = ((n / 64 * 4 + 2) * width + n % 64 * 4 + 2) * 4;
      if ((b.get(p) & 255) + (b.get(p + 1) & 255) + (b.get(p + 2) & 255) > 384) value |= 1 << bit;
    }
    return value;
  }

  static boolean hasHeader(ByteBuffer b, int width, int height) {
    return width >= 256
        && height >= 32
        && b.capacity() >= (long) width * height * 4
        && readByte(b, width, 0) == 69
        && readByte(b, width, 1) == 70
        && readByte(b, width, 2) == 77
        && readByte(b, width, 3) == 50;
  }

  static int readInt(ByteBuffer b, int width, int index) {
    int bits = 0;
    for (int i = 0; i < 4; i++) bits |= readByte(b, width, index + i) << (i * 8);
    return bits;
  }

  static float read(ByteBuffer b, int width, int index) {
    return Float.intBitsToFloat(readInt(b, width, index));
  }
}
