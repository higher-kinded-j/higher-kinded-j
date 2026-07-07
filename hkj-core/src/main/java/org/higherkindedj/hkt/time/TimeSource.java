// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * An effectful time source: {@link java.time.Clock} lifted into {@link IO} and {@link VTask}, so
 * reading the current instant is a lazy, composable effect instead of an eager side effect — and
 * deterministic in tests.
 *
 * <p>{@code java.time.Clock} is already the JDK's injectable-time abstraction, with {@code
 * fixed}/{@code offset}/{@code tick} instances built in; this type deliberately lifts it rather
 * than reinventing it, and any JDK clock plugs in through {@link #of(Clock)}. (It is named {@code
 * TimeSource}, not {@code Clock}, precisely so it never clashes with {@code java.time.Clock} in the
 * files that use both.)
 *
 * <pre>{@code
 * TimeSource time = TimeSource.fixed(Instant.parse("2026-07-07T00:00:00Z"));
 *
 * IO<Reservation> reserve(Order order) {
 *   return time.now().map(t -> new Reservation(order.id(), order.items(), t.plus(hold)));
 * }
 * }</pre>
 *
 * @param clock the underlying JDK clock; must not be null
 */
public record TimeSource(Clock clock) {

  /**
   * Validates the wrapped clock.
   *
   * @throws NullPointerException if {@code clock} is null
   */
  public TimeSource {
    Objects.requireNonNull(clock, "clock must not be null");
  }

  /**
   * The live system time source (UTC).
   *
   * @return a time source over {@link Clock#systemUTC()} (non-null)
   */
  public static TimeSource system() {
    return new TimeSource(Clock.systemUTC());
  }

  /**
   * Lifts any JDK clock — including {@link Clock#fixed}, {@link Clock#offset} and {@link
   * Clock#tick} instances.
   *
   * @param clock the clock to lift; must not be null
   * @return a time source over {@code clock} (non-null)
   * @throws NullPointerException if {@code clock} is null
   */
  public static TimeSource of(Clock clock) {
    return new TimeSource(clock);
  }

  /**
   * A test convenience: a time source frozen at the given instant (UTC).
   *
   * @param instant the instant every read returns; must not be null
   * @return a fixed time source (non-null)
   * @throws NullPointerException if {@code instant} is null
   */
  public static TimeSource fixed(Instant instant) {
    Objects.requireNonNull(instant, "instant must not be null");
    return new TimeSource(Clock.fixed(instant, ZoneOffset.UTC));
  }

  /**
   * The current instant as a lazy {@link IO}: nothing is read until the effect runs, and each run
   * reads afresh.
   *
   * @return the instant-reading effect (non-null)
   */
  public IO<Instant> now() {
    return IO.delay(clock::instant);
  }

  /**
   * The current instant as a {@link VTask}, for asynchronous pipelines.
   *
   * @return the instant-reading task (non-null)
   */
  public VTask<Instant> nowAsync() {
    return VTask.delay(clock::instant);
  }
}
