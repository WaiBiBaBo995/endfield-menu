package dev.pureneko.endfieldmenu.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector4f;

/** Shared scene transforms drive both rasterized planes and inverse-projected hit tests. */
public final class NativeScene {
  public static final class Track {
    final float[][] rows;

    Track(JsonArray data) {
      rows = new float[data.size()][];
      for (int i = 0; i < rows.length; i++) rows[i] = floats(data.get(i).getAsJsonArray());
    }

    void sample(float time, float[] result) {
      int low = 0, high = rows.length - 1;
      while (low < high) {
        int mid = (low + high + 1) / 2;
        if (rows[mid][0] <= time) low = mid;
        else high = mid - 1;
      }
      float[] a = rows[low], b = rows[Math.min(low + 1, rows.length - 1)];
      float t = b[0] == a[0] ? 0 : Math.clamp((time - a[0]) / (b[0] - a[0]), 0, 1);
      for (int i = 0; i < result.length; i++) result[i] = a[i + 1] + (b[i + 1] - a[i + 1]) * t;
    }
  }

  public static final class Node {
    int parent, row, id;
    public String name, action;
    String hoverAction = "", nav = "";
    float width, height, alpha;
    float[] hit;
    final Matrix4f world = new Matrix4f(), child = new Matrix4f(), space;
    final Map<String, Track> tracks;
    final float[] sample = new float[17];

    Node(JsonObject j) {
      nav = str(j, "nav");
      id = j.get("id").getAsInt();
      parent = j.get("parent").getAsInt();
      row = j.get("row").getAsInt();
      name = j.get("name").getAsString();
      action = j.get("action").getAsString();
      width = j.get("w").getAsFloat();
      height = j.get("h").getAsFloat();
      space = new Matrix4f().set(floats(j.getAsJsonArray("space")));
      tracks = tracks(j);
      if (j.has("hit")) {
        var a = j.getAsJsonArray("hit");
        hit = new float[a.size() * 2];
        for (int i = 0; i < a.size(); i++) {
          hit[i * 2] = a.get(i).getAsJsonArray().get(0).getAsFloat();
          hit[i * 2 + 1] = a.get(i).getAsJsonArray().get(1).getAsFloat();
        }
      }
    }
  }

  public static final class Plane {
    public int id, node, clip, row, group;
    public String kind, key, action, hover, name;
    public ResourceLocation texture;
    public float[] bounds, uv, arc;
    public float font;
    public boolean empty, svg;
    public Matrix4f pseudo;
    final Map<String, Track> tracks;
    final float[] fx = new float[5];
    final Matrix4f matrix = new Matrix4f();

    Plane(JsonObject j, List<Node> ns) {
      id = j.get("id").getAsInt();
      node = j.get("node").getAsInt();
      clip = j.get("clip").getAsInt();
      row = j.get("row").getAsInt();
      group = j.get("group").getAsInt();
      kind = str(j, "kind");
      key = str(j, "key");
      action = str(j, "action");
      hover = str(j, "hover");
      name = ns.get(node).name;
      svg = name.startsWith("svg.");
      font = j.get("font").getAsFloat();
      empty = j.has("empty") && j.get("empty").getAsBoolean();
      if (j.has("bounds")) bounds = floats(j.getAsJsonArray("bounds"));
      if (j.has("uv")) uv = floats(j.getAsJsonArray("uv"));
      if (j.has("arc")) arc = floats(j.getAsJsonArray("arc"));
      if (j.has("texture"))
        texture =
            ResourceLocation.fromNamespaceAndPath("endfieldmenu", "native/" + str(j, "texture"));
      tracks = tracks(j);
      if (j.has("pseudoMatrix"))
        pseudo = new Matrix4f().set(floats(j.getAsJsonArray("pseudoMatrix")));
    }
  }

  public final List<Node> nodes = new ArrayList<>();
  public final List<Plane> planes = new ArrayList<>();
  private static NativeScene cached;
  private final Matrix4f local = new Matrix4f(), sway = new Matrix4f();
  public float pitch, yaw, scroll, scrollTarget;
  private float pitchV, yawV;
  public String hovered = "", pressed = "";
  private float hoverProgress, pressProgress;
  private String phase = "in";
  private float phaseTime;

  public static NativeScene load() {
    if (cached != null) return cached;
    try (var in = NativeScene.class.getResourceAsStream("/assets/endfieldmenu/native/scene.json")) {
      if (in == null) throw new IOException("Missing native scene export");
      var data =
          JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
              .getAsJsonObject();
      var s = new NativeScene();
      for (var j : data.getAsJsonArray("nodes")) s.nodes.add(new Node(j.getAsJsonObject()));
      for (var j : data.getAsJsonArray("planes"))
        s.planes.add(new Plane(j.getAsJsonObject(), s.nodes));
      for (var p : s.planes)
        if (p.group >= 0 && !p.hover.isEmpty()) s.nodes.get(p.group).hoverAction = p.hover;
      cached = s;
      return s;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load exported menu", e);
    }
  }

  public void update(String phase, float ms, double mx, double my, float delta) {
    this.phase = phase;
    this.phaseTime = ms;
    float d = Math.min(delta * 60, 3),
        tx = (float) ((my / 1080 * 2 - 1) * 2.5),
        ty = (float) (-(mx / 1920 * 2 - 1) * 4);
    pitchV = (pitchV + (tx - pitch) * .008f * d) * (float) Math.pow(.8, d);
    yawV = (yawV + (ty - yaw) * .008f * d) * (float) Math.pow(.8, d);
    pitch += pitchV * d;
    yaw += yawV * d;
    scroll += (scrollTarget - scroll) * (1 - (float) Math.pow(.82, d));
    hoverProgress += (hovered.isEmpty() ? 0 : 1 - hoverProgress) * (1 - (float) Math.pow(.6, d));
    if (hovered.isEmpty()) hoverProgress *= Math.pow(.6, d);
    pressProgress += ((pressed.isEmpty() ? 0 : 1) - pressProgress) * (1 - (float) Math.pow(.5, d));
    for (var n : nodes) {
      n.tracks.get(phase).sample(ms, n.sample);
      local.set(n.sample);
      if (n.parent < 0) local.identity();
      if (n.name.equals("div.ef-world")) {
        // Source camera rests at 20deg. Add live, spring-damped mouse sway in the same parent
        // frame.
        local
            .translate(960, 540, 0)
            .rotateXYZ((float) Math.toRadians(pitch), (float) Math.toRadians(yaw), 0)
            .translate(-960, -540, 0);
      }
      if (n.row >= 0) {
        double a = (22.52 - n.row * 15.5 + scroll) * Math.PI / 180;
        double base = (22.52 - n.row * 15.5) * Math.PI / 180;
        local.m30(local.m30() + (float) (582.32 * (Math.cos(a) - Math.cos(base))));
        local.m31(local.m31() - (float) (490 * (a - base)));
      }
      if (!hovered.isEmpty() && n.hoverAction.equals(hovered) && !phase.equals("out")) {
        float lift = hoverProgress,
            k = 1 - (n.name.contains("rtile") ? .045f : .03f) * pressProgress;
        local
            .translate(
                n.name.contains("rtile") ? 4 * lift : -2 * lift,
                n.name.contains("rtile") ? -5 * lift : -3 * lift,
                0)
            .translate(n.width / 2, n.height / 2, 0)
            .scale(k, k, 1)
            .translate(-n.width / 2, -n.height / 2, 0);
      }
      n.world.set(n.parent < 0 ? new Matrix4f() : nodes.get(n.parent).child).mul(local);
      n.child.set(n.world).mul(n.space);
      n.alpha = n.sample[16] * (n.parent < 0 ? 1 : nodes.get(n.parent).alpha);
      if (!n.nav.isEmpty())
        n.alpha *=
            ((n.nav.equals("up") ? scroll > 0.3 : scroll < 77.2)
                ? (.65f + .35f * (float) Math.cos(ms / 1800 * 6.283185))
                : .3f);
    }
    for (var p : planes) {
      p.tracks.get(phase).sample(ms, p.fx);
      var n = nodes.get(p.node);
      p.matrix.set(p.svg ? n.child : n.world);
      if (p.pseudo != null) p.matrix.mul(p.pseudo);
    }
  }

  public Node hit(double x, double y) {
    if (phase.equals("out")) return null;
    for (int i = nodes.size() - 1; i >= 0; i--) {
      var n = nodes.get(i);
      if (n.hit == null || n.alpha < .15f || n.action.equals("locked")) continue;
      Vector2f p = unproject(n.world, (float) x, (float) y);
      if (p != null && polygon(n.hit, p.x, p.y) && insideClip(n, x, y)) return n;
    }
    return null;
  }

  private boolean insideClip(Node n, double x, double y) {
    for (Node p = n; p.parent >= 0; p = nodes.get(p.parent))
      if (p.name.equals("div.ef-right__viewport")) {
        var v = unproject(p.world, (float) x, (float) y);
        return v != null && v.x >= 0 && v.x <= 512 && v.y >= 58 && v.y <= 767;
      }
    return true;
  }

  public Plane terrain() {
    return planes.stream().filter(p -> p.kind.equals("terrain")).findFirst().orElseThrow();
  }

  public Vector2f mapPoint(double x, double y) {
    return unproject(terrain().matrix, (float) x, (float) y);
  }

  public Vector2f center(String action) {
    for (var n : nodes)
      if (n.action.equals(action) && n.hit != null) {
        float x = 0, y = 0;
        for (int i = 0; i < n.hit.length; i += 2) {
          x += n.hit[i];
          y += n.hit[i + 1];
        }
        return project(n.world, x / (n.hit.length / 2), y / (n.hit.length / 2));
      }
    return null;
  }

  public static Vector2f project(Matrix4f m, float x, float y) {
    Vector4f p = m.transform(new Vector4f(x, y, 0, 1));
    return new Vector2f(p.x / p.w, p.y / p.w);
  }

  public static Vector2f unproject(Matrix4f m, float x, float y) {
    // Invert the projective homography of local z=0, rather than a screen-space bounding box.
    float a = m.m00() - x * m.m03(), b = m.m10() - x * m.m13(), c = x * m.m33() - m.m30();
    float d = m.m01() - y * m.m03(),
        e = m.m11() - y * m.m13(),
        f = y * m.m33() - m.m31(),
        det = a * e - b * d;
    return Math.abs(det) < 1e-7 ? null : new Vector2f((c * e - b * f) / det, (a * f - c * d) / det);
  }

  static boolean polygon(float[] v, float x, float y) {
    boolean in = false;
    for (int i = 0, j = v.length - 2; i < v.length; j = i, i += 2)
      if ((v[i + 1] > y) != (v[j + 1] > y)
          && x < (v[j] - v[i]) * (y - v[i + 1]) / (v[j + 1] - v[i + 1]) + v[i]) in = !in;
    return in;
  }

  private static String str(JsonObject j, String k) {
    return j.has(k) ? j.get(k).getAsString() : "";
  }

  private static float[] floats(JsonArray a) {
    float[] r = new float[a.size()];
    for (int i = 0; i < r.length; i++) r[i] = a.get(i).getAsFloat();
    return r;
  }

  private static Map<String, Track> tracks(JsonObject j) {
    Map<String, Track> r = new HashMap<>();
    for (var e : j.getAsJsonObject("tracks").entrySet())
      r.put(e.getKey(), new Track(e.getValue().getAsJsonArray()));
    return r;
  }
}
