package io.chronos.engine.domain.port;

import java.util.concurrent.CompletableFuture;

/** Outbound port for non-blocking network state synchronization packets. */
public interface NetworkTransportPort {

  CompletableFuture<Integer> broadcastStatePacketAsync(long tick, byte[] payload);
}
