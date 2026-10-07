// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * A stream read from another stream. An operator extends it with its own {@code pull()}, reading
 * from {@link #upstream}, and its {@code close()} closes the upstream, so closing it reaches every
 * finaliser upstream.
 *
 * @param <U> The upstream's element type.
 * @param <A> The element type.
 */
abstract class DerivedStream<U, A> implements VStream<A> {

  /** The stream this one reads from. */
  final VStream<U> upstream;

  DerivedStream(VStream<U> upstream) {
    this.upstream = upstream;
  }

  @Override
  public VTask<Unit> close() {
    return upstream.close();
  }

  /**
   * Concatenation for an operator whose second stream continues one it is already reading, such as
   * the rest of {@code chunk}'s source after a batch. Closing it closes both streams, where {@link
   * VStream#concat(VStream, VStream)} closes only the one it is reading.
   */
  static <A> VStream<A> concatContinuing(VStream<A> first, VStream<A> second) {
    return new Concat<>(first, second, true);
  }

  /**
   * Concatenation as {@link VStream#concat(VStream, VStream)} gives it: closing it closes the
   * stream it is reading, and leaves a second stream it has not reached.
   */
  static <A> VStream<A> concatReading(VStream<A> first, VStream<A> second) {
    return new Concat<>(first, second, false);
  }

  /** The concatenation of two streams, reading {@code first} until it ends, then {@code second}. */
  private static final class Concat<A> extends DerivedStream<A, A> {

    private final VStream<A> second;
    private final boolean closesSecond;

    Concat(VStream<A> first, VStream<A> second, boolean closesSecond) {
      super(first);
      this.second = second;
      this.closesSecond = closesSecond;
    }

    @Override
    public VTask<Step<A>> pull() {
      return upstream
          .pull()
          .map(
              step ->
                  switch (step) {
                    case Step.Emit<A> e ->
                        new Step.Emit<>(e.value(), new Concat<>(e.tail(), second, closesSecond));
                    case Step.Skip<A> s ->
                        new Step.Skip<>(new Concat<>(s.tail(), second, closesSecond));
                    case Step.Done<A> _ -> new Step.Skip<>(second);
                  });
    }

    @Override
    public VTask<Unit> close() {
      return closesSecond ? Closing.closeAll(upstream, second) : upstream.close();
    }
  }
}
