package dev.pureneko.endfieldmenu.terrain;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry: both the surface and isolines use the same triangulated height field. */
public final class TerrainGeometry {
  public record Point(double x, double y, double z) {
    public Point lerp(Point other, double t) {
      return new Point(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t);
    }
  }

  public record Face(Point a, Point b, Point c, double light) {}

  public record Contour(Point a, Point b, int elevation) {}

  public record Mesh(
      List<Face> faces, List<Contour> contours, double baseline, int loaded, int total) {}

  private TerrainGeometry() {}

  public static Mesh build(float[] heights, int size, int step, int interval) {
    if (size < 2 || heights.length != size * size || step < 1 || interval < 1) {
      throw new IllegalArgumentException("Invalid height field dimensions or interval");
    }
    List<Face> faces = new ArrayList<>();
    List<Contour> contours = new ArrayList<>();
    double sum = 0;
    int loaded = 0;
    for (float height : heights) {
      if (Float.isFinite(height)) {
        sum += height;
        loaded++;
      }
    }
    double offset = (size - 1) * step / 2.0;
    for (int z = 0; z < size - 1; z++) {
      for (int x = 0; x < size - 1; x++) {
        Point a = new Point(x * step - offset, heights[z * size + x], z * step - offset);
        Point b = new Point((x + 1) * step - offset, heights[z * size + x + 1], z * step - offset);
        Point c =
            new Point(
                (x + 1) * step - offset, heights[(z + 1) * size + x + 1], (z + 1) * step - offset);
        Point d =
            new Point(x * step - offset, heights[(z + 1) * size + x], (z + 1) * step - offset);
        addTriangle(a, b, c, interval, faces, contours);
        addTriangle(a, c, d, interval, faces, contours);
      }
    }
    return new Mesh(
        List.copyOf(faces),
        List.copyOf(contours),
        loaded == 0 ? 64 : sum / loaded,
        loaded,
        heights.length);
  }

  private static void addTriangle(
      Point a, Point b, Point c, int interval, List<Face> faces, List<Contour> contours) {
    if (!Double.isFinite(a.y) || !Double.isFinite(b.y) || !Double.isFinite(c.y)) return;
    double ux = b.x - a.x, uy = b.y - a.y, uz = b.z - a.z;
    double vx = c.x - a.x, vy = c.y - a.y, vz = c.z - a.z;
    double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
    if (ny < 0) {
      nx = -nx;
      ny = -ny;
      nz = -nz;
    }
    double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
    double light = Math.max(0.1, (nx * -0.4 + ny * 0.82 + nz * -0.4) / Math.max(1, length));
    faces.add(new Face(a, b, c, light));
    double min = Math.min(a.y, Math.min(b.y, c.y));
    double max = Math.max(a.y, Math.max(b.y, c.y));
    for (int elevation = (int) Math.ceil(min / interval) * interval;
        elevation < max;
        elevation += interval) {
      Point[] points = new Point[3];
      int count = 0;
      count = crossing(a, b, elevation, points, count);
      count = crossing(b, c, elevation, points, count);
      count = crossing(c, a, elevation, points, count);
      if (count == 2 && Math.hypot(points[0].x - points[1].x, points[0].z - points[1].z) > 1e-6) {
        contours.add(new Contour(points[0], points[1], elevation));
      }
    }
  }

  private static int crossing(Point a, Point b, double elevation, Point[] points, int count) {
    // Half-open height intervals keep shared vertices deterministic and avoid zero-length lines.
    if ((a.y <= elevation && b.y > elevation) || (b.y <= elevation && a.y > elevation)) {
      points[count++] = a.lerp(b, (elevation - a.y) / (b.y - a.y));
    }
    return count;
  }
}
