// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * A stream read from another stream. An operator extends it with its own {@code pull()}, reading
 * from {@link #upstream}, and its {@code close()} closes the upstream, so closing it reaches every
 * finaliser upstream.
 *
 * <p>An operator passes on an element that failed upstream, a {@link Step.Fail}, with itself
 * applied to the stream after it, as it does an {@link Step.Emit}'s tail, so a {@link
 * VStream#recover} after it goes on through it. The failed element counts as one the operator has
 * read, so {@code take(3)} counts it.
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
   * Pulls the upstream and gives its step to {@code next}, as {@link VTask#map} would, without
   * checking {@code next} again on every pull.
   */
  final <R> VTask<R> pullMap(Function<? super Step<U>, ? extends R> next) {
    VTask<Step<U>> pulled = upstream.pull();
    return () -> next.apply(pulled.execute());
  }

  /** As {@link #pullMap}, for an operator whose next step is itself a task to run. */
  final <R> VTask<R> pullFlatMap(Function<? super Step<U>, ? extends VTask<R>> next) {
    VTask<Step<U>> pulled = upstream.pull();
    return () -> next.apply(pulled.execute()).execute();
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
   * A stream whose first step is an element that failed, with {@code tail} after it. Closing it
   * closes the tail, which nothing else holds.
   */
  static <A> VStream<A> failedElement(Throwable error, VStream<A> tail) {
    return new VStream<>() {
      @Override
      public VTask<Step<A>> pull() {
        return VTask.succeed(new Step.Fail<>(error, tail));
      }

      @Override
      public VTask<Unit> close() {
        return tail.close();
      }
    };
  }

  /**
   * What an operator that reads ahead, such as {@code chunk}, gives when an element it reads has
   * failed: what it read before that element, then the failed element, with the operator over the
   * stream after it, as {@code next} gives it, so a {@link VStream#recover} after the operator goes
   * on through it.
   *
   * @param read what the operator hands on before the failed element
   * @param failed the failed element
   * @param next the operator, over the stream after the failed element
   */
  static <A, B> VStream<B> handingOnThenFailedElement(
      List<B> read, Step.Fail<A> failed, Function<? super VStream<A>, ? extends VStream<B>> next) {
    return handingOn(read, failedElement(failed.error(), next.apply(failed.tail())));
  }

  /**
   * What an operator that reads ahead gives when reading fails: what it read before the failure,
   * then the failure, which ends the stream. It closes where it got to at once, since nothing else
   * holds it. An interrupt stops the stream, so for one it hands on nothing. A consumer that stops
   * before the failure does not see it, nor a failure of a finaliser that ran as reading failed.
   *
   * @param read what the operator hands on before the failure
   * @param failure what reading failed with
   * @param current the stream whose pull failed
   */
  static <B> VStream<B> handingOnThenEnding(List<B> read, Throwable failure, VStream<?> current) {
    Closing.closeAfterFailure(current, failure);
    VStream<B> failing = VStream.fail(failure);
    return Executing.isInterrupt(failure) ? failing : handingOn(read, failing);
  }

  private static <B> VStream<B> handingOn(List<B> read, VStream<B> then) {
    return read.isEmpty() ? then : concatContinuing(VStream.fromList(read), then);
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
      return pullMap(
          step ->
              switch (step) {
                case Step.Emit<A> e ->
                    new Step.Emit<>(e.value(), new Concat<>(e.tail(), second, closesSecond));
                case Step.Skip<A> s ->
                    new Step.Skip<>(new Concat<>(s.tail(), second, closesSecond));
                case Step.Fail<A> failed ->
                    new Step.Fail<>(
                        failed.error(), new Concat<>(failed.tail(), second, closesSecond));
                case Step.Done<A> _ -> new Step.Skip<>(second);
              });
    }

    @Override
    public VTask<Unit> close() {
      return closesSecond ? Closing.closeAll(upstream, second) : upstream.close();
    }
  }
}
