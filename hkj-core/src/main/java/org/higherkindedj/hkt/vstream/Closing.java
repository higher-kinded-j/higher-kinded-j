// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.util.Cleanup;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.jspecify.annotations.Nullable;

/**
 * How the streams in this package close what they read, and how they report a failure to close. The
 * rule is that whatever holds the rest of a stream when a failure happens closes it. One failure
 * does not stop the rest of the cleanup; a later failure is suppressed onto the first, as {@link
 * Cleanup} reports it.
 */
final class Closing {

  private Closing() {}

  /** Closes each stream in order, suppressing later failures onto the first. */
  static VTask<Unit> closeAll(VStream<?>... streams) {
    return () -> {
      Throwable failure = null;
      for (VStream<?> stream : streams) {
        try {
          stream.close().execute();
        } catch (Throwable t) {
          failure = Cleanup.keep(failure, t);
        }
      }
      if (failure != null) {
        throw failure;
      }
      return Unit.INSTANCE;
    };
  }

  /**
   * Closes a stream after a failure while reading it, so its finalisers still run, and closes the
   * rest of the stream that a {@link VStream#mapTask} failure carries, which nothing else holds. A
   * failure to close is suppressed onto the original failure, which the caller goes on to throw.
   */
  static void closeAfterFailure(VStream<?> stream, Throwable failure) {
    runAfterFailure(stream.close(), failure);
    closeMarkedRest(failure);
  }

  /**
   * Closes the rest of the stream that a {@link VStream#mapTask} failure carries for {@link
   * VStream#recover} to resume from, for an operation that does not resume.
   */
  static void closeMarkedRest(Throwable failure) {
    VStream<?> rest = markedRest(failure);
    if (rest != null) {
      runAfterFailure(rest.close(), failure);
    }
  }

  /**
   * The rest of the stream a {@link VStream#mapTask} failure carries, from which {@link
   * VStream#recover} resumes, or null if the failure carries none. A failure carries it itself, or,
   * when a stream written outside this package pulled it with {@link VTask#run()}, inside the
   * {@link VTaskExecutionException} that wraps a checked one.
   */
  static @Nullable VStream<?> markedRest(Throwable failure) {
    VStream<?> rest = restMarkedOn(failure);
    if (rest == null && failure instanceof VTaskExecutionException) {
      rest = restMarkedOn(Objects.requireNonNullElse(failure.getCause(), failure));
    }
    return rest;
  }

  /**
   * The rest the latest marker on a failure holds, so an exception thrown at more than one element
   * resumes from where it last failed.
   */
  private static @Nullable VStream<?> restMarkedOn(Throwable failure) {
    VStream<?> rest = null;
    for (Throwable suppressed : failure.getSuppressed()) {
      if (suppressed instanceof StreamTailMarker marker) {
        rest = marker.remainingTail();
      }
    }
    return rest;
  }

  /**
   * Makes a failure carry the rest of the stream, for {@link VStream#recover} to resume from, or
   * for whatever stops at the failure to close. A failure with suppression disabled cannot carry
   * it, so the rest is closed at once, and a {@code recover} ends the stream there.
   */
  static void carry(Throwable failure, VStream<?> rest) {
    StreamTailMarker marker = new StreamTailMarker(rest);
    failure.addSuppressed(marker);
    if (!Arrays.asList(failure.getSuppressed()).contains(marker)) {
      runAfterFailure(rest.close(), failure);
    }
  }

  /**
   * The error an operator fails with in place of a failure: what its function gives, or what the
   * function throws. That error carries the rest of the stream the failure carries, with the
   * operator applied to it again. A {@link VStream#recover} after the operator then resumes through
   * it, and whatever stops at the failure still closes that rest.
   */
  static Throwable carryingRest(
      Throwable failure, UnaryOperator<Throwable> replacing, UnaryOperator<VStream<?>> continuing) {
    Throwable replacement;
    try {
      replacement = replacing.apply(failure);
    } catch (Throwable thrown) {
      replacement = thrown;
    }
    VStream<?> rest = markedRest(failure);
    if (rest != null) {
      carry(replacement, continuing.apply(rest));
    }
    return replacement;
  }

  /**
   * Runs a cleanup task after a failure, as {@link Cleanup#afterFailure} runs cleanup: a failure of
   * the cleanup is suppressed onto the original, which the caller goes on to throw, and an
   * interrupt that stopped the reading does not cut the cleanup short. The cleanup's failure is
   * suppressed as it threw it, a checked one included, as a stream's finalisers report one when
   * they run after a successful read, and an {@code InterruptedException} sets the interrupt
   * status.
   */
  static void runAfterFailure(VTask<?> cleanup, Throwable failure) {
    Cleanup.afterFailure(failure, () -> Executing.executed(cleanup));
  }

  /**
   * A task that, if it fails, closes streams that only it holds, which closing its caller would not
   * reach, then fails as before.
   */
  static <T> VTask<T> closingOnFailure(VTask<T> task, VStream<?>... held) {
    return task.recoverWith(
        failure ->
            () -> {
              runAfterFailure(closeAll(held), failure);
              throw failure;
            });
  }

  /**
   * Tests an element whose rest only the caller holds. If the test throws, the rest is closed, so
   * its finalisers run, and the failure is rethrown.
   */
  static <A> boolean testOrClose(Predicate<? super A> predicate, A element, VStream<?> rest) {
    try {
      return predicate.test(element);
    } catch (Throwable t) {
      closeAfterFailure(rest, t);
      throw t;
    }
  }

  /**
   * Applies a function that must not return null to an element whose rest only the caller holds. If
   * the function throws or returns null, the rest is closed, so its finalisers run, and the failure
   * is rethrown.
   */
  static <A, B> B applyNonNullOrClose(
      Function<? super A, ? extends @Nullable B> f,
      A element,
      VStream<?> rest,
      String nullMessage) {
    try {
      return Objects.requireNonNull(f.apply(element), nullMessage);
    } catch (Throwable t) {
      closeAfterFailure(rest, t);
      throw t;
    }
  }

  /**
   * Gives an element whose rest only the caller holds to an action. If the action throws, the rest
   * is closed, so its finalisers run, and the failure is rethrown.
   */
  static <A> void acceptOrClose(Consumer<? super A> action, A element, VStream<?> rest) {
    try {
      action.accept(element);
    } catch (Throwable t) {
      closeAfterFailure(rest, t);
      throw t;
    }
  }

  /**
   * How far a stream reading two others has got, which decides what closing it closes. Before it
   * has read either, it closes both; once it has read one, only that one, so a stream it never
   * reached is left as it is; once it has read both, both.
   */
  enum Reading {
    NEITHER,
    ONE,
    BOTH;

    /**
     * Closes what this state has read.
     *
     * @param readFirst the stream read first, which {@link #ONE} has read
     * @param other the other stream
     */
    VTask<Unit> close(VStream<?> readFirst, VStream<?> other) {
      return this == ONE ? readFirst.close() : closeAll(readFirst, other);
    }

    /** The state once the stream read first has been read from again. */
    Reading withFirstRead() {
      return this == NEITHER ? ONE : this;
    }

    /** The state once one more stream has been read from. */
    Reading next() {
      return this == NEITHER ? ONE : BOTH;
    }
  }
}
