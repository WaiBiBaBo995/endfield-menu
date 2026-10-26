package dev.pureneko.endfieldmenu.client;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Complete browser compositor frames; AUI still owns the browser, focus and all input. */
final class BrowserFrameStream implements AutoCloseable, WebSocket.Listener {
  record Frame(ByteBuffer pixels, int width, int height, double decodeMs, double sourceTime, java.util.function.Consumer<ByteBuffer> releaser)
      implements AutoCloseable {
    @Override
    public void close() {
      releaser.accept(pixels);
    }
  }

  private final ExecutorService network =
      Executors.newFixedThreadPool(
          3,
          r -> {
            var t = new Thread(r, "Endfield browser frames");
            t.setDaemon(true);
            return t;
          });
  private final ExecutorService decoder =
      Executors.newSingleThreadExecutor(
          r -> {
            var t = new Thread(r, "Endfield frame decode");
            t.setDaemon(true);
            return t;
          });
  private final HttpClient client =
      HttpClient.newBuilder().executor(network).connectTimeout(Duration.ofSeconds(2)).build();
  private final AtomicReference<Frame> latest = new AtomicReference<>();
  private final AtomicReference<JsonObject> encoded = new AtomicReference<>();
  private final AtomicBoolean decoding = new AtomicBoolean();
  private StringBuilder message = new StringBuilder();
  private final java.util.concurrent.ConcurrentLinkedQueue<ByteBuffer> buffers = new java.util.concurrent.ConcurrentLinkedQueue<>();
  private ByteBuffer compressedBuffer;
  private final AtomicLong allocatedBytes = new AtomicLong();
  private volatile String usedCodec = "pending";
  private WicImageDecoder wic;
  private final NativePngDecoder.Api codec = NativePngDecoder.API;
  private volatile long session;
  private volatile WebSocket socket;
  private CompletableFuture<WebSocket> writes;
  private volatile boolean connected, closed, wanted = true, streaming;
  private volatile String failure;
  private int requestId, width, height;
  private volatile long received, replaced, lastReceived;
  private volatile double decodeMs;
  private String previousPayload;
  private volatile long duplicates;
  private final java.util.ArrayDeque<Double> incomingTimes = new java.util.ArrayDeque<>();
  private long previousIncoming;
  private volatile long activatedAt;

  BrowserFrameStream(int port, String url, int width, int height) {
    this.width = width;
    this.height = height;
    network.execute(() -> discover(port, url));
  }

  private void discover(int port, String url) {
    try {
      long deadline = System.nanoTime() + 8_000_000_000L;
      while (!closed && System.nanoTime() < deadline) {
        try {
          var response =
              client.send(
                  HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list"))
                      .timeout(Duration.ofSeconds(2))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          for (var item : JsonParser.parseString(response.body()).getAsJsonArray()) {
            var target = item.getAsJsonObject();
            if (!url.equals(target.get("url").getAsString()) || !target.has("webSocketDebuggerUrl"))
              continue;
            URI endpoint = URI.create(target.get("webSocketDebuggerUrl").getAsString());
            if (!("127.0.0.1".equals(endpoint.getHost()) || "localhost".equals(endpoint.getHost()))
                || endpoint.getPort() != port)
              throw new IllegalStateException("Non-loopback frame endpoint");
            client
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .buildAsync(endpoint, this)
                .join();
            return;
          }
        } catch (java.io.IOException ignored) {
        }
        Thread.sleep(100);
      }
      if (!closed) fail("Browser frame target was not available");
    } catch (Exception e) {
      if (!closed) fail(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  @Override
  public synchronized void onOpen(WebSocket ws) {
    if (closed) {
      ws.abort();
      return;
    }
    socket = ws;
    writes = CompletableFuture.completedFuture(ws);
    connected = true;
    command("Page.enable", new JsonObject());
    reconcile();
    ws.request(1);
  }

  private synchronized void command(String method, JsonObject params) {
    if (closed || socket == null) return;
    var request = new JsonObject();
    request.addProperty("id", ++requestId);
    request.addProperty("method", method);
    request.add("params", params);
    String text = request.toString();
    writes = writes.thenCompose(ws -> ws.sendText(text, true));
    writes.exceptionally(
        e -> {
          if (!closed) fail("Frame command failed");
          return null;
        });
  }

  private synchronized void reconcile() {
    if (!connected || closed) return;
    if (wanted == streaming) return;
    streaming = wanted;
    if (!streaming) {
      command("Page.stopScreencast", new JsonObject());
      return;
    }
    var params = new JsonObject();
    params.addProperty("format", MenuConfig.LOSSLESS_CAPTURE.get() ? "png" : "jpeg");
    params.addProperty("quality", 95);
    params.addProperty("maxWidth", width);
    params.addProperty("maxHeight", height);
    params.addProperty("everyNthFrame", 1);
    command("Page.startScreencast", params);
  }

  synchronized void active(boolean active) {
    if (closed || wanted == active) return;
    if (wanted != active && active) {
      incomingTimes.clear();
      previousIncoming = 0;
      activatedAt = System.nanoTime();
    }
    wanted = active;
    if (!active) {
      session++;
      message.trimToSize();
      var frame=latest.getAndSet(null);if(frame!=null)frame.close();
      encoded.set(null);
      decoder.execute(()->{previousPayload=null;freeCompressed();clearBuffers();});
    }
    reconcile();
  }

  synchronized void resize(int w, int h) {
    if (width == w && height == h) return;
    session++;
    var stale=latest.getAndSet(null);if(stale!=null)stale.close();
    encoded.set(null);
    width = w;
    height = h;
    if (streaming) {
      command("Page.stopScreencast", new JsonObject());
      streaming = false;
    }
    reconcile();
  }

  @Override
  public synchronized CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
    try {
      if (message.length() + data.length() > 24_000_000)
        throw new IllegalStateException("Oversized frame message");
      message.append(data);
      if (last) {
        var event = JsonParser.parseString(message.toString()).getAsJsonObject();
        message.setLength(0);
        if (!wanted && message.capacity()>65536) message=new StringBuilder();
        if (event.has("error")) throw new IllegalStateException(event.get("error").toString());
        if (event.has("method")
            && "Page.screencastFrame".equals(event.get("method").getAsString())) {
          var params = event.getAsJsonObject("params");
          var ack = new JsonObject();
          ack.add("sessionId", params.get("sessionId"));
          command("Page.screencastFrameAck", ack);
          lastReceived = System.nanoTime();
          received++;
          synchronized (this) {
            if (previousIncoming != 0) {
              incomingTimes.add((lastReceived - previousIncoming) / 1e6);
              if (incomingTimes.size() > 240) incomingTimes.remove();
            }
            previousIncoming = lastReceived;
          }
          if (!wanted) { ws.request(1); return null; }
          if (encoded.getAndSet(params) != null) replaced++;
          scheduleDecode();
        }
      }
    } catch (RuntimeException e) {
      fail("Frame protocol: " + e.getMessage());
    }
    if (!closed) ws.request(1);
    return null;
  }

  private void scheduleDecode() {
    if (closed || !decoding.compareAndSet(false, true)) return;
    try {
      decoder.execute(
          () -> {
            try {
              JsonObject next;
              while (!closed && (next = encoded.getAndSet(null)) != null) decode(next);
            } catch (Exception e) {
              if (!closed) fail("Frame decode: " + e.getMessage());
            } finally {
              decoding.set(false);
              if (!closed && encoded.get() != null) scheduleDecode();
            }
          });
    } catch (RejectedExecutionException e) {
      decoding.set(false);
    }
  }

  private void decode(JsonObject frame) throws java.io.IOException {
    long start = System.nanoTime();
    long serial = session;
    String payload = frame.get("data").getAsString();
    if (payload.equals(previousPayload)) {
      duplicates++;
      return;
    }
    byte[] bytes = Base64.getDecoder().decode(payload);
    if(compressedBuffer==null||compressedBuffer.capacity()<bytes.length){
      freeCompressed(); compressedBuffer=allocate((bytes.length+65535)&~65535);
    }
    ByteBuffer compressed=compressedBuffer.clear();
    try (var stack = MemoryStack.stackPush()) {
      compressed.put(bytes).flip();
      var w = stack.mallocInt(1);
      var h = stack.mallocInt(1);
      var channels = stack.mallocInt(1);
      if (!STBImage.stbi_info_from_memory(compressed, w, h, channels)
          || w.get(0) < 1
          || h.get(0) < 1
          || (long) w.get(0) * h.get(0) > 33_554_432)
        throw new java.io.IOException("Invalid browser raster");
      STBImage.stbi_set_flip_vertically_on_load_thread(0);
      double stamp = frame.getAsJsonObject("metadata").get("timestamp").getAsDouble();
      boolean png = bytes.length>8 && bytes[0]==(byte)137 && bytes[1]==80;
      ByteBuffer pixels;
      java.util.function.Consumer<ByteBuffer> releaser;
      if (codec != null) {
        int count=w.get(0)*h.get(0)*4; pixels=buffers.poll();
        if(pixels!=null&&pixels.capacity()!=count){free(pixels);pixels=null;}
        if(pixels==null)pixels=allocate(count);
        int result=png?codec.ef_decode_png(compressed,bytes.length,pixels,count,w.get(0),h.get(0))
          :codec.ef_decode_jpeg(compressed,bytes.length,pixels,count,w.get(0),h.get(0));
        if(result!=0){free(pixels);throw new java.io.IOException("Image codec: "+result);}
        usedCodec=png?"libspng-zlib-ng":"libjpeg-turbo-simd";
        releaser=this::recycle;
      } else if (com.sun.jna.Platform.isWindows()) {
        if(wic==null)wic=new WicImageDecoder();
        int count=w.get(0)*h.get(0)*4;pixels=buffers.poll();
        if(pixels!=null&&pixels.capacity()!=count){free(pixels);pixels=null;}
        if(pixels==null)pixels=allocate(count);
        try{wic.decode(compressed,pixels,w.get(0),h.get(0));}catch(RuntimeException e){free(pixels);throw e;}
        usedCodec="windows-wic";
        releaser=this::recycle;
      } else {
        pixels=STBImage.stbi_load_from_memory(compressed,w,h,channels,4);
        releaser=STBImage::stbi_image_free;
        usedCodec="stb";
      }
      if (pixels == null) throw new java.io.IOException(STBImage.stbi_failure_reason());
      previousPayload = payload;
      decodeMs = (System.nanoTime() - start) / 1e6;
      Frame decoded = new Frame(pixels, w.get(0), h.get(0), decodeMs, stamp, releaser);
      synchronized (this) {
        if (closed || !wanted || serial!=session) decoded.close();
        else {
          Frame previous = latest.getAndSet(decoded);
          if (previous != null) {
            previous.close();
            replaced++;
          }
        }
      }
    }
  }

  private synchronized void recycle(ByteBuffer pixels) {
    if(closed || !wanted || buffers.size()>=2) free(pixels);
    else buffers.offer(pixels.clear());
  }
  private ByteBuffer allocate(int size){var b=MemoryUtil.memAlloc(size);allocatedBytes.addAndGet(size);return b;}
  private void free(ByteBuffer b){allocatedBytes.addAndGet(-b.capacity());MemoryUtil.memFree(b);}
  private void clearBuffers(){ByteBuffer b;while((b=buffers.poll())!=null)free(b);}
  private void freeCompressed(){if(compressedBuffer!=null)free(compressedBuffer);compressedBuffer=null;}

  Frame poll() {
    return latest.getAndSet(null);
  }

  boolean healthy() {
    return failure == null
        && !closed
        && connected
        && (!wanted
            || lastReceived != 0 && System.nanoTime() - lastReceived < 2_000_000_000L
            || activatedAt != 0 && System.nanoTime() - activatedAt < 500_000_000L);
  }

  synchronized JsonObject metrics() {
    var j = new JsonObject();
    j.addProperty("codec",usedCodec);
    j.addProperty("nativeBufferBytes",allocatedBytes.get());
    j.addProperty("pooledBytes",buffers.stream().mapToLong(ByteBuffer::capacity).sum());
    j.addProperty("connected", connected);
    j.addProperty("streaming", streaming);
    j.addProperty("received", received);
    j.addProperty("coalesced", replaced);
    j.addProperty("duplicateImages", duplicates);
    j.addProperty("decodeMs", decodeMs);
    if (failure != null) j.addProperty("failure", failure);
    j.addProperty(
        "incomingFps",
        incomingTimes.isEmpty()
            ? 0
            : 1000
                / incomingTimes.stream().mapToDouble(Double::doubleValue).average().orElse(1000));
    return j;
  }

  @Override
  public void onError(WebSocket ws, Throwable error) {
    if (!closed) fail(error.toString());
  }

  @Override
  public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
    connected = false;
    if (!closed) fail("Frame connection closed: " + status);
    return null;
  }

  private void fail(String message) {
    if (failure == null) {
      failure = message;
      com.mojang.logging.LogUtils.getLogger()
          .warn("Endfield direct capture unavailable; using AUI capture: {}", message);
    }
    close();
  }

  @Override
  public synchronized void close() {
    if (closed) return;
    closed = true;
    connected = streaming = false;
    if (socket != null) socket.abort();
    var frame = latest.getAndSet(null);
    if (frame != null) frame.close();
    encoded.set(null);
    decoder.execute(()->{previousPayload=null;freeCompressed();clearBuffers();if(wic!=null){wic.close();wic=null;}});
    decoder.shutdown();
    network.shutdown();
    client.shutdown();
  }
}
