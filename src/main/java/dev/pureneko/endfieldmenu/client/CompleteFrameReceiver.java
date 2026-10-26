package dev.pureneko.endfieldmenu.client;

import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** AUI 1.2.8 channel reader. Only a contiguous full-width full refresh is presentable. */
public final class CompleteFrameReceiver implements AutoCloseable {
  private final ByteBuffer channel;
  private final int arena;
  private ByteBuffer back, front;
  private int width, height, nextY = -1;
  private long lastSequence, generation, discarded;

  public CompleteFrameReceiver(ByteBuffer channel) {
    this.channel = channel.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    if (channel.capacity() < 256
        || this.channel.getInt(0) != 1128879425
        || this.channel.getInt(4) != 1
        || this.channel.getInt(8) != 256)
      throw new IllegalArgumentException("Unsupported AUI frame channel");
    arena = this.channel.getInt(12);
    if (arena <= 0 || 256L + arena > channel.capacity())
      throw new IllegalArgumentException("Invalid AUI arena");
    requestFullRefresh();
  }

  public void requestFullRefresh() {
    VarHandle.releaseFence();
    channel.putInt(28, channel.getInt(28) | 1);
  }

  public void drain() {
    // Bound each render-thread visit; a busy producer must not monopolise the game.
    for (int visit = 0; visit < 512; visit++) {
      long read = channel.getLong(88), write = channel.getLong(32);
      VarHandle.acquireFence();
      if (read == write) break;
      if (write < read || write - read > arena) {
        resync(write);
        break;
      }
      int p = 256 + (int) (read % arena), magic = channel.getInt(p);
      if (magic == 558121296) {
        int padding = channel.getInt(p + 4);
        if (padding < 8 || padding > arena || read + padding > write) {
          resync(write);
          break;
        }
        releaseRead(read + padding, lastSequence);
        continue;
      }
      if (magic != 1263550805 || p + 40 > channel.capacity()) {
        resync(write);
        break;
      }
      long seq = channel.getLong(p + 8);
      int w = channel.getInt(p + 16), h = channel.getInt(p + 20);
      int rects = channel.getInt(p + 24), payload = channel.getInt(p + 28);
      long size = (40L + 16L * rects + payload + 7) & ~7L;
      if (w <= 0
          || h <= 0
          || w > 8192
          || h > 8192
          || (long) w * h > 33_554_432
          || rects < 1
          || rects > 1024
          || payload < 0
          || payload > 262144
          || size > arena
          || p + size > channel.capacity()
          || read + size > write) {
        resync(write);
        break;
      }
      if (lastSequence != 0 && seq != lastSequence + 1) nextY = -1;
      lastSequence = seq;
      int data = p + 40 + rects * 16, end = data + payload;
      boolean full = (channel.getInt(p + 32) & 1) != 0;
      for (int i = 0; i < rects; i++) {
        int t = p + 40 + i * 16;
        int x = channel.getInt(t), y = channel.getInt(t + 4);
        int rw = channel.getInt(t + 8), rh = channel.getInt(t + 12);
        long bytes = (long) rw * rh * 4;
        if (x < 0
            || y < 0
            || rw < 1
            || rh < 1
            || (long) x + rw > w
            || (long) y + rh > h
            || bytes > end - data) {
          resync(write);
          return;
        }
        if (full && x == 0 && rw == w) {
          if (y == 0) {
            if (nextY > 0) discarded++;
            if (width != w || height != h || back == null) {
              close();
              width = w;
              height = h;
              back = org.lwjgl.system.MemoryUtil.memAlloc(w * h * 4);
              front = null;
            }
            nextY = 0;
          }
          if (nextY == y && w == width && h == height) {
            back.put(y * w * 4, channel, data, (int) bytes);
            nextY += rh;
            if (nextY == height) {
              ByteBuffer old = front;
              front = back;
              back = old == null ? org.lwjgl.system.MemoryUtil.memAlloc(w * h * 4) : old;
              nextY = -1;
              generation++;
            }
          } else nextY = -1;
        } else nextY = -1;
        data += (int) bytes;
      }
      releaseRead(read + size, seq);
    }
    requestFullRefresh();
  }

  private void releaseRead(long offset, long seq) {
    VarHandle.releaseFence();
    channel.putLong(88, offset);
    channel.putLong(96, seq);
  }

  private void resync(long write) {
    nextY = -1;
    discarded++;
    releaseRead(write, 0);
    requestFullRefresh();
  }

  public ByteBuffer pixels() {
    return front;
  }

  public long generation() {
    return generation;
  }

  public long discarded() {
    return discarded;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public long allocatedBytes() {
    return (back == null ? 0L : back.capacity()) + (front == null ? 0L : front.capacity());
  }

  @Override public void close() {
    if (back != null) org.lwjgl.system.MemoryUtil.memFree(back);
    if (front != null) org.lwjgl.system.MemoryUtil.memFree(front);
    back = front = null;
    nextY = -1;
  }
}
