// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

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
 * <p>The clock is fixed to UTC; {@link #withZone} returns {@code this} (zone handling is out of a
 * test fixture's scope). The current instant is {@code volatile}, so advancing from a test thread
 * is visible to code reading the clock on other (e.g. virtual) threads.
 */
public final class SteppableClock extends Clock {

  private volatile Instant current;

  private SteppableClock(Instant start) {
    this.current = start;
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
    return new SteppableClock(start);
  }

  /**
   * Moves the clock forward (or, with a negative duration, backward).
   *
   * @param step the amount to move; must not be null
   * @throws NullPointerException if {@code step} is null
   */
  public void advance(Duration step) {
    Objects.requireNonNull(step, "step must not be null");
    current = current.plus(step);
  }

  /**
   * Jumps the clock to an exact instant.
   *
   * @param instant the new current instant; must not be null
   * @throws NullPointerException if {@code instant} is null
   */
  public void set(Instant instant) {
    current = Objects.requireNonNull(instant, "instant must not be null");
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return current;
  }
}
