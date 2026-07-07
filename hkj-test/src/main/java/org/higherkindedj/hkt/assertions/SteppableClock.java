// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A test clock that only moves when told to: start it at a known instant, then {@link #advance} or
 * {@link #set} it by hand. Pairs with {@code TimeSource.of(clock)} so time-dependent code is
 * exercised without sleeping.
 *
 * <pre>{@code
 * SteppableClock clock = SteppableClock.startingAt(Instant.parse("2026-07-07T00:00:00Z"));
 * var service = new InventoryService(TimeSource.of(clock));
 * service.reserve(order);
 * clock.advance(Duration.ofMinutes(16));   // the hold has now expired
 * }</pre>
 *
 * <p>The clock is UTC by default; per the {@link Clock} contract, {@link #withZone} returns a new
 * clock with the requested zone sharing the same underlying timeline. The current instant is held
 * in an {@link AtomicReference}, so stepping from a test thread is atomic and visible to code
 * reading the clock on other (e.g. virtual) threads.
 */
public final class SteppableClock extends Clock {

  private final AtomicReference<Instant> current;
  private final ZoneId zone;

  private SteppableClock(AtomicReference<Instant> current, ZoneId zone) {
    this.current = current;
    this.zone = zone;
  }

  /**
   * A clock frozen at {@code start} until stepped.
   *
   * @param start the initial instant; must not be null
   * @return the steppable clock (non-null)
   * @throws NullPointerException if {@code start} is null
   */
  public static SteppableClock startingAt(Instant start) {
    Objects.requireNonNull(start, "start must not be null");
    return new SteppableClock(new AtomicReference<>(start), ZoneOffset.UTC);
  }

  /**
   * Moves the clock forward (or, with a negative duration, backward).
   *
   * @param step the amount to move; must not be null
   * @throws NullPointerException if {@code step} is null
   */
  public void advance(Duration step) {
    Objects.requireNonNull(step, "step must not be null");
    current.updateAndGet(instant -> instant.plus(step));
  }

  /**
   * Jumps the clock to an exact instant.
   *
   * @param instant the new current instant; must not be null
   * @throws NullPointerException if {@code instant} is null
   */
  public void set(Instant instant) {
    Objects.requireNonNull(instant, "instant must not be null");
    current.set(instant);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    Objects.requireNonNull(zone, "zone must not be null");
    return new SteppableClock(current, zone);
  }

  @Override
  public Instant instant() {
    return current.get();
  }
}
