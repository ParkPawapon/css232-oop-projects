package io.chronos.engine.domain.ecs;

/**
 * Type-safe 64-bit identifier for an entity inside a {@link World}.
 *
 * <p>The upper 32 bits store the monotonically increasing generation of the slot, and the lower 32
 * bits store the dense slot index.
 *
 * @param raw 64-bit packed entity handle
 */
public record EntityId(long raw) implements Comparable<EntityId> {

  private static final long INDEX_MASK = 0xFFFF_FFFFL;
  private static final int GENERATION_SHIFT = 32;

  public EntityId {
    if (index(raw) < 0) {
      throw new IllegalArgumentException("Entity slot index must be non-negative: " + index(raw));
    }
    if (generation(raw) <= 0) {
      throw new IllegalArgumentException(
          "Entity generation must be strictly positive: " + generation(raw));
    }
  }

  /**
   * Packs a slot index and generation into an {@link EntityId}.
   *
   * @param slotIndex zero-based entity table index
   * @param generation positive generation counter
   * @return packed EntityId
   */
  public static EntityId of(int slotIndex, int generation) {
    if (slotIndex < 0) {
      throw new IllegalArgumentException("slotIndex cannot be negative: " + slotIndex);
    }
    if (generation <= 0) {
      throw new IllegalArgumentException("generation must be > 0: " + generation);
    }
    long packed =
        ((long) generation << GENERATION_SHIFT) | (Integer.toUnsignedLong(slotIndex) & INDEX_MASK);
    return new EntityId(packed);
  }

  public int index() {
    return index(this.raw);
  }

  public int generation() {
    return generation(this.raw);
  }

  private static int index(long packed) {
    return (int) (packed & INDEX_MASK);
  }

  private static int generation(long packed) {
    return (int) (packed >>> GENERATION_SHIFT);
  }

  @Override
  public int compareTo(EntityId other) {
    return Long.compareUnsigned(this.raw, other.raw);
  }

  @Override
  public String toString() {
    return "EntityId[idx=" + index() + ",gen=" + generation() + "]";
  }
}
