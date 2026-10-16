package io.chronos.engine.adapter.network;

import io.chronos.engine.domain.port.NetworkTransportPort;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Network transport adapter implementing {@link NetworkTransportPort} over Java 21 Virtual Threads.
 */
public final class VirtualThreadNetworkGateway implements NetworkTransportPort {

  private static final Logger LOG = LoggerFactory.getLogger(VirtualThreadNetworkGateway.class);
  private static final int HEADER_BYTES = Long.BYTES + Integer.BYTES;

  private final ExecutorService virtualThreadExecutor;
  private final AtomicLong totalBytesBroadcast;

  public VirtualThreadNetworkGateway(ExecutorService virtualThreadExecutor) {
    this.virtualThreadExecutor =
        Objects.requireNonNull(virtualThreadExecutor, "virtualThreadExecutor must not be null");
    this.totalBytesBroadcast = new AtomicLong(0L);
  }

  @Override
  public CompletableFuture<Integer> broadcastStatePacketAsync(long tick, byte[] payload) {
    Objects.requireNonNull(payload, "payload must not be null");
    byte[] defensiveCopy = payload.clone();

    return CompletableFuture.supplyAsync(
        () -> encodeAndSendPacket(tick, defensiveCopy), virtualThreadExecutor);
  }

  public long totalBytesBroadcast() {
    return totalBytesBroadcast.get();
  }

  private int encodeAndSendPacket(long tick, byte[] payload) {
    ByteBuffer frame = ByteBuffer.allocate(HEADER_BYTES + payload.length);
    frame.putLong(tick);
    frame.putInt(payload.length);
    frame.put(payload);
    frame.flip();

    int frameSize = frame.remaining();
    totalBytesBroadcast.addAndGet(frameSize);
    LOG.debug(
        "Broadcast state packet [tick={}, bytes={}, virtual={}]",
        tick,
        frameSize,
        Thread.currentThread().isVirtual());
    return frameSize;
  }
}
