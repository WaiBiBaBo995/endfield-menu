package dev.pureneko.endfieldmenu.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.*;
import org.junit.jupiter.api.Test;

class CompleteFrameReceiverTest {
  @Test void closeReleasesBothBuffersAndCanBeRepeated() {
    var w = new Writer();
    var r = new CompleteFrameReceiver(w.b);
    w.rows(4,4,0,4,10,true);r.drain();
    assertEquals(128,r.allocatedBytes());
    r.close();r.close();
    assertEquals(0,r.allocatedBytes());assertNull(r.pixels());
    w.rows(4,4,0,4,20,true);r.drain();
    assertEquals(20,r.pixels().get(0));r.close();
  }
  private static final class Writer {
    final ByteBuffer b = ByteBuffer.allocateDirect(256 + 4096).order(ByteOrder.LITTLE_ENDIAN);
    long sequence, offset;

    Writer() {
      b.putInt(0, 1128879425);
      b.putInt(4, 1);
      b.putInt(8, 256);
      b.putInt(12, 4096);
    }

    void rows(int width, int height, int y, int count, int value, boolean full) {
      int payload = width * count * 4,
          bytes = (56 + payload + 7) & ~7,
          p = 256 + (int) (offset % 4096);
      b.putInt(p, 1263550805);
      b.putInt(p + 4, 40);
      b.putLong(p + 8, ++sequence);
      b.putInt(p + 16, width);
      b.putInt(p + 20, height);
      b.putInt(p + 24, 1);
      b.putInt(p + 28, payload);
      b.putInt(p + 32, full ? 1 : 0);
      b.putInt(p + 40, 0);
      b.putInt(p + 44, y);
      b.putInt(p + 48, width);
      b.putInt(p + 52, count);
      for (int i = 0; i < payload; i++) b.put(p + 56 + i, (byte) value);
      offset += bytes;
      b.putLong(32, offset);
    }
  }

  @Test
  void partialRefreshIsInvisibleUntilItsLastRow() {
    var w = new Writer();
    var r = new CompleteFrameReceiver(w.b);
    w.rows(4, 4, 0, 2, 10, true);
    r.drain();
    assertNull(r.pixels());
    assertEquals(0, r.generation());
    w.rows(4, 4, 2, 2, 20, true);
    r.drain();
    assertEquals(1, r.generation());
    assertEquals(10, r.pixels().get(0));
    assertEquals(20, r.pixels().get(4 * 2 * 4));
  }

  @Test
  void aNewTopRowRestartsAnIncompleteCapture() {
    var w = new Writer();
    var r = new CompleteFrameReceiver(w.b);
    w.rows(4, 4, 0, 2, 10, true);
    r.drain();
    w.rows(4, 4, 0, 2, 30, true);
    w.rows(4, 4, 2, 2, 40, true);
    r.drain();
    assertEquals(1, r.generation());
    assertEquals(1, r.discarded());
    assertEquals(30, r.pixels().get(0));
  }

  @Test
  void deltasAndGapsNeverCompleteAnOldCapture() {
    var w = new Writer();
    var r = new CompleteFrameReceiver(w.b);
    w.rows(4, 4, 0, 2, 10, true);
    r.drain();
    w.rows(4, 4, 2, 2, 20, false);
    r.drain();
    assertNull(r.pixels());
    w.rows(4, 4, 0, 2, 30, true);
    r.drain();
    w.sequence++;
    w.rows(4, 4, 2, 2, 40, true);
    r.drain();
    assertNull(r.pixels());
    w.rows(4, 4, 0, 4, 50, true);
    r.drain();
    assertEquals(1, r.generation());
  }

  @Test
  void aPartiallyReceivedResizeDoesNotBecomePresentable() {
    var w = new Writer();
    var r = new CompleteFrameReceiver(w.b);
    w.rows(4, 4, 0, 4, 10, true);
    r.drain();
    assertEquals(1, r.generation());
    w.rows(8, 4, 0, 2, 20, true);
    r.drain();
    assertEquals(1, r.generation());
    w.rows(8, 4, 2, 2, 30, true);
    r.drain();
    assertEquals(2, r.generation());
    assertEquals(8, r.width());
  }

  @Test
  void malformedPacketIsSkippedAndRecoveryRequestsAFullRefresh() {
    var w = new Writer();
    var r = new CompleteFrameReceiver(w.b);
    w.rows(4, 4, 0, 4, 10, true);
    w.b.putInt(256 + 28, -1);
    r.drain();
    assertNull(r.pixels());
    assertEquals(1, r.discarded());
    assertEquals(w.offset, w.b.getLong(88));
    assertEquals(1, w.b.getInt(28) & 1);
    w.rows(4, 4, 0, 4, 42, true);
    r.drain();
    assertEquals(1, r.generation());
  }
}
