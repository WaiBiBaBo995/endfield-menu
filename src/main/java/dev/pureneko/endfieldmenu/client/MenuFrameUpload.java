package dev.pureneko.endfieldmenu.client;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;

/** One GPU upload per complete browser image; partial images never reach the visible texture. */
public final class MenuFrameUpload implements AutoCloseable {
  private CompleteFrameReceiver receiver;
  private final BrowserFrameStream direct;
  private boolean directPresented;
  private boolean measuringStream;
  private long nativeGeneration;
  private double sourceAgeMs, uploadMs;
  private int lastInput;
  private long inputActivatedAt;
  private final java.util.ArrayDeque<Double> inputTimes = new java.util.ArrayDeque<>();
  private int texture = -1, width, height, rasterHeight;
  private long uploaded, presented, lastPresented;
  private boolean active;
  private final java.util.ArrayDeque<Double> intervals = new java.util.ArrayDeque<>();
  private long previous;
  private org.joml.Matrix4f projection;
  private int epoch;
  private boolean keyedBackground;
  private final float[] avatarX=new float[4],avatarY=new float[4];
  private float yaw, tilt = .95f, zoom = 1.45f;

  public MenuFrameUpload(long view) {
    direct = BrowserCaptureBootstrap.take(view);
  }

  public void drain(ByteBuffer buffer) {
    if (direct != null) {
      var size = MenuResolution.current();
      direct.resize(size.width(), size.rasterHeight());
      var frame = direct.poll();
      if (frame != null)
        try (frame) {
          if (upload(frame.pixels(), frame.width(), frame.height())) {
            directPresented = true;
            sourceAgeMs = Math.max(0, System.currentTimeMillis() - frame.sourceTime() * 1000);
          }
        }
    }
    boolean streaming = usesStream();
    if (streaming != measuringStream) {
      measuringStream = streaming;
      previous = presented = 0;
      intervals.clear();
    }
    if (streaming) {
      if (receiver != null) { receiver.close(); receiver = null; nativeGeneration = 0; }
      return;
    }
    if (receiver == null) receiver = new CompleteFrameReceiver(buffer);
    receiver.drain();
    if (receiver.pixels() == null || receiver.generation() == nativeGeneration) return;
    if (upload(receiver.pixels(), receiver.width(), receiver.height()))
      nativeGeneration = receiver.generation();
  }

  public boolean usesStream() {
    return directPresented && direct != null && direct.healthy();
  }

  private boolean upload(ByteBuffer pixels, int w, int h) {
    long start = System.nanoTime();
    int contentHeight = h - 64;
    if (contentHeight < 64 || w < 256) return false;
    var metadata = pixels.slice(w * contentHeight * 4, w * 64 * 4);
    if (!PortalProjection.hasHeader(metadata, w, 32))
      return false; // never present a half-created page
    var decoded = PortalProjection.decode(metadata, w, contentHeight);
    float capturedYaw = PortalProjection.read(metadata, w, 44),
        capturedTilt = PortalProjection.read(metadata, w, 48),
        capturedZoom = PortalProjection.read(metadata, w, 52);
    if (!Float.isFinite(capturedYaw)
        || !Float.isFinite(capturedTilt)
        || !Float.isFinite(capturedZoom)
        || capturedZoom < .5f
        || capturedZoom > 3) return false;
    epoch = PortalProjection.readInt(metadata, w, 40);
    int input = PortalProjection.readInt(metadata,w,56);
    if (input != 0 && input != lastInput) {
      lastInput = input;
      long delay = ((System.currentTimeMillis() & 0xffffffffL)
          - Integer.toUnsignedLong(PortalProjection.readInt(metadata,w,60))) & 0xffffffffL;
      if (delay < 10000 && delay <= System.currentTimeMillis()-inputActivatedAt) {
        inputTimes.add((double)delay);if(inputTimes.size()>240)inputTimes.remove();
      }
    }
    int mode=PortalProjection.readByte(metadata,w,36);
    keyedBackground = mode==1;
    for(int i=0;i<4;i++){
      avatarX[i]=PortalProjection.read(metadata,w,64+i*8)/w;
      avatarY[i]=PortalProjection.read(metadata,w,68+i*8)/contentHeight;
    }
    yaw = capturedYaw;
    tilt = capturedTilt;
    zoom = capturedZoom;
    if (texture < 0 || width != w || rasterHeight != h) {
      if (texture >= 0) TextureUtil.releaseTextureId(texture);
      texture = TextureUtil.generateTextureId();
      width = w;
      height = contentHeight;
      rasterHeight = h;
      TextureUtil.prepareImage(texture, width, rasterHeight);
      RenderSystem.bindTexture(texture);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }
    RenderSystem.bindTexture(texture);
    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
    GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
    projection = decoded;
    pixels.position(0).limit(width * rasterHeight * 4);
    GL11.glTexSubImage2D(
        GL11.GL_TEXTURE_2D, 0, 0, 0, width, rasterHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
    uploaded++;
    uploadMs = (System.nanoTime() - start) / 1e6;
    return true;
  }

  public void presented() {
    if (active && uploaded != lastPresented) {
      long now = System.nanoTime();
      if (previous != 0) {
        intervals.add((now - previous) / 1e6);
        if (intervals.size() > 1200) intervals.remove();
      }
      previous = now;
      presented++;
      lastPresented = uploaded;
    }
  }

  public void active(boolean value) {
    if (direct != null) direct.active(value);
    if (active == value) return;
    active = value;
    previous = 0;
    if (value) {
      inputActivatedAt=System.currentTimeMillis();
      inputTimes.clear();
      presented = 0;
      intervals.clear();
    }
  }

  public int texture() {
    return texture;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public float yaw() {
    return yaw;
  }

  public float tilt() {
    return tilt;
  }

  public float zoom() {
    return zoom;
  }

  public int epoch() {
    return epoch;
  }

  public boolean keyedBackground() {
    return keyedBackground;
  }
  public float[] avatarX(){return avatarX;}
  public float[] avatarY(){return avatarY;}

  public org.joml.Matrix4f projection() {
    return projection;
  }

  public boolean ready() {
    return texture >= 0;
  }

  public void evict() {
    if (texture >= 0) TextureUtil.releaseTextureId(texture);
    texture = -1;
    projection = null;
    if (receiver != null) { receiver.close(); receiver = null; nativeGeneration = 0; }
  }

  public com.google.gson.JsonObject metrics() {
    var j = new com.google.gson.JsonObject();
    var samples = intervals.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    j.addProperty("width", width);
    j.addProperty("height", height);
    j.addProperty("gpuTextureBytes", texture < 0 ? 0L : (long)width*rasterHeight*4);
    j.addProperty("receiverBytes", receiver == null ? 0 : receiver.allocatedBytes());
    j.addProperty("completeFrames", presented);
    j.addProperty("totalFrames", uploaded);
    j.addProperty("discardedIncomplete", receiver == null ? 0 : receiver.discarded());
    j.addProperty("losslessCapture", MenuConfig.LOSSLESS_CAPTURE.get());
    j.addProperty("portalProjection", projection != null);
    j.addProperty("keyedBackground", keyedBackground);
    j.addProperty("backend", usesStream() ? "browser-compositor" : "aui-capture");
    j.addProperty("uploadMs", uploadMs);
    var inputSamples=inputTimes.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    j.addProperty("domInputToUploadP50Ms",inputSamples.length==0?-1:inputSamples[inputSamples.length/2]);
    j.addProperty("domInputToUploadP95Ms",inputSamples.length==0?-1:inputSamples[(int)(inputSamples.length*.95)]);
    if (direct != null) {
      j.add("stream", direct.metrics());
      j.addProperty("sourceAgeMs", sourceAgeMs);
    }
    j.addProperty(
        "htmlPresentedFps",
        samples.length == 0 ? 0 : 1000 / java.util.Arrays.stream(samples).average().orElse(1000));
    j.addProperty(
        "htmlFrameP95Ms", samples.length == 0 ? -1 : samples[(int) (samples.length * .95)]);
    return j;
  }

  @Override
  public void close() {
    if (direct != null) direct.close();
    evict();
    uploaded = 0;
    projection = null;
    epoch = 0;
    active = false;
  }
}
