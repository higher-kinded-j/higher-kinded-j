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
    return new Concat<>(first, second, true, true, false);
  }

  /**
   * As {@link #concatContinuing}, for a first stream not yet reached, such as the one {@code
   * flatMap}'s function gives for an element. Until the first stream's first pull, closing it
   * closes only the second, and a first pull that fails closes the first, as {@link #unreached}
   * describes.
   */
  static <A> VStream<A> concatContinuingUnreached(VStream<A> first, VStream<A> second) {
    return new Concat<>(first, second, true, false, false);
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
   * A stream handed on unreached: {@code concat}'s second stream as the first ends, {@code
   * interleave}'s second as the first ends before either is read, and the stream after {@code
   * prepend}'s element. Pulling it pulls the stream, so each step after is the stream's own, and a
   * first pull that fails closes the stream, as closing this would not. Closing it before that
   * first pull leaves the stream as it is, since this run has not reached it.
   */
  static <A> VStream<A> unreached(VStream<A> stream) {
    // A Concat, not a class of its own, so a loop that pulls it meets no more kinds of stream
    return new Concat<>(stream, VStream.empty(), false, false, true);
  }

  /**
   * Concatenation as {@link VStream#concat(VStream, VStream)} gives it: closing it closes the
   * stream it is reading, and leaves a second stream it has not reached, before and after the first
   * ends.
   */
  static <A> VStream<A> concatReading(VStream<A> first, VStream<A> second) {
    return new Concat<>(first, second, false, true, false);
  }

  /**
   * The concatenation of two streams, reading {@code first} until it ends, then {@code second}, in
   * one of four states, one for each factory:
   *
   * <ul>
   *   <li>{@link #concatReading}: closing closes {@code first}, or the rest of it, and leaves
   *       {@code second}, which it hands on unreached when {@code first} ends;
   *   <li>{@link #concatContinuing}: closing closes both;
   *   <li>{@link #concatContinuingUnreached}: until {@code first}'s first pull, closing closes only
   *       {@code second}, and a first pull that fails closes {@code first}; after it, as {@link
   *       #concatContinuing};
   *   <li>{@link #unreached}: {@code second} is empty, and the first pull gives {@code first}'s
   *       step as it is, so the run holds {@code first}'s own tails after it; closing it before
   *       then closes nothing, and a first pull that fails closes {@code first}.
   * </ul>
   *
   * <p>One class for all four, since a loop that pulls a kind of stream it has not met before
   * becomes slower to call.
   */
  private static final class Concat<A> extends DerivedStream<A, A> {

    private final VStream<A> second;
    // Whether closing closes second, which goes on with a stream already being read
    private final boolean closesSecond;
    // Whether closing closes first: from the start for concat's own first stream, otherwise once
    // this run has pulled it
    private final boolean firstReached;
    // Only while first is unreached, with an empty second: the first pull hands on first's step
    private final boolean handsOn;

    Concat(
        VStream<A> first,
        VStream<A> second,
        boolean closesSecond,
        boolean firstReached,
        boolean handsOn) {
      super(first);
      this.second = second;
      this.closesSecond = closesSecond;
      this.firstReached = firstReached;
      this.handsOn = handsOn;
    }

    @Override
    public VTask<Step<A>> pull() {
      // One task for each pull, where pullMap would add a function for its step, since a
      // concatenation is pulled for every element it hands on
      if (!firstReached) {
        return firstPull();
      }
      VTask<Step<A>> pulled = upstream.pull();
      return () -> next(pulled.execute());
    }

    /** The step after one of {@code first}'s, over the rest of {@code first}. */
    private Step<A> next(Step<A> step) {
      return switch (step) {
        case Step.Emit<A> e -> new Step.Emit<>(e.value(), reading(e.tail()));
        case Step.Skip<A> s -> new Step.Skip<>(reading(s.tail()));
        case Step.Fail<A> failed -> new Step.Fail<>(failed.error(), reading(failed.tail()));
        // concatContinuing's second stream goes on with one already being read, and concat's
        // has not been reached
        case Step.Done<A> _ -> new Step.Skip<>(closesSecond ? second : unreached(second));
      };
    }

    /**
     * The first pull of {@code first}, which closes it if it fails, as closing this would not. One
     * that hands on {@code first}'s steps gives them as they are, so from here on the run holds
     * {@code first}'s own tails.
     */
    private VTask<Step<A>> firstPull() {
      VTask<Step<A>> pulled;
      try {
        pulled = upstream.pull();
      } catch (Throwable t) {
        // A hand-written stream may fail as it builds its pull, before the pull runs
        Closing.closeAfterFailure(upstream, t);
        throw t;
      }
      return () -> {
        Step<A> step;
        try {
          step = pulled.execute();
        } catch (Throwable t) {
          Closing.closeAfterFailure(upstream, t);
          throw t;
        }
        return handsOn ? step : next(step);
      };
    }

    /** The concatenation over the rest of {@code first}, which this run has now reached. */
    private Concat<A> reading(VStream<A> rest) {
      return new Concat<>(rest, second, closesSecond, true, false);
    }

    @Override
    public VTask<Unit> close() {
      if (!firstReached) {
        return closesSecond ? second.close() : VTask.succeed(Unit.INSTANCE);
      }
      return closesSecond ? Closing.closeAll(upstream, second) : upstream.close();
    }
  }
}
