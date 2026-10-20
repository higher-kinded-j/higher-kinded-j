// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.trymonad.Try;
import org.jspecify.annotations.Nullable;

/**
 * Custom AssertJ assertions for {@link VResultPath} instances.
 *
 * <p>A {@code VResultPath<E, A>} run has three possible outcomes, and the assertions cover all of
 * them: a typed success ({@code Right}), a typed domain error ({@code Left}), or a defect (an
 * exception thrown outside the typed channel). Since VResultPath is a lazy computation executing on
 * virtual threads, these assertions run the path once on first use and cache the outcome for
 * subsequent assertions in the chain.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.VResultPathAssert.assertThatVResultPath;
 *
 * assertThatVResultPath(Path.vresultRight(42)).isRight().hasRight(42);
 * assertThatVResultPath(Path.vresultLeft(error)).isLeft().hasLeftSatisfying(e -> ...);
 * assertThatVResultPath(defective).hasDefect().withDefectType(IllegalStateException.class);
 * }</pre>
 *
 * @param <E> The type of the typed error carried on the {@code Left} channel
 * @param <A> The type of the value produced on the {@code Right} channel
 */
public class VResultPathAssert<E, A>
    extends AbstractAssert<VResultPathAssert<E, A>, VResultPath<E, A>> {

  /** Entry point. */
  public static <E, A> VResultPathAssert<E, A> assertThatVResultPath(
      @Nullable VResultPath<E, A> actual) {
    return new VResultPathAssert<>(actual);
  }

  private @Nullable Either<E, A> executedResult;
  private @Nullable Throwable executedDefect;
  private boolean hasBeenExecuted = false;
  private long executionTimeMillis = -1;

  protected VResultPathAssert(@Nullable VResultPath<E, A> actual) {
    super(actual, VResultPathAssert.class);
  }

  private Throwable defect() {
    return Objects.requireNonNull(executedDefect);
  }

  private Either<E, A> executed() {
    return Objects.requireNonNull(executedResult);
  }

  private VResultPathAssert<E, A> ensureExecuted() {
    if (!hasBeenExecuted) {
      long startNanos = System.nanoTime();
      boolean interruptedBefore = Thread.currentThread().isInterrupted();
      Try<Either<E, A>> outcome = actual.run().runSafe();
      if (outcome instanceof Try.Failure<Either<E, A>>(Throwable defect)) {
        Interrupts.clearAfterCancellation(interruptedBefore, defect);
      }
      executionTimeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
      executedResult = outcome.orElse(null);
      executedDefect =
          outcome instanceof Try.Failure<Either<E, A>>(Throwable defect) ? defect : null;
      hasBeenExecuted = true;
    }
    return this;
  }

  private String describeOutcome() {
    if (executedDefect != null) {
      return "a defect: " + executedDefect;
    }
    if (executed().isLeft()) {
      return "Left(" + executed().getLeft() + ")";
    }
    return "Right(" + executed().getRight() + ")";
  }

  /** Asserts that the path produces a typed success ({@code Right}). */
  public VResultPathAssert<E, A> isRight() {
    isNotNull();
    ensureExecuted();
    Assertions.assertThat(executedDefect == null && executed().isRight())
        .withFailMessage(
            "Expected VResultPath to produce a Right but it produced %s", describeOutcome())
        .isTrue();
    return this;
  }

  /** Asserts that the path produces a typed error ({@code Left}). */
  public VResultPathAssert<E, A> isLeft() {
    isNotNull();
    ensureExecuted();
    Assertions.assertThat(executedDefect == null && executed().isLeft())
        .withFailMessage(
            "Expected VResultPath to produce a Left but it produced %s", describeOutcome())
        .isTrue();
    return this;
  }

  /** Asserts that the path produces a {@code Right} equal to the expected value. */
  public VResultPathAssert<E, A> hasRight(A expected) {
    isRight();
    A actualValue = executed().getRight();
    Assertions.assertThat(actualValue)
        .withFailMessage("Expected Right value <%s> but was <%s>", expected, actualValue)
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the path produces a {@code Left} equal to the expected error. */
  public VResultPathAssert<E, A> hasLeft(E expected) {
    isLeft();
    E actualError = executed().getLeft();
    Assertions.assertThat(actualError)
        .withFailMessage("Expected Left error <%s> but was <%s>", expected, actualError)
        .isEqualTo(expected);
    return this;
  }

  /** Asserts that the {@code Right} value satisfies the given requirements. */
  public VResultPathAssert<E, A> hasRightSatisfying(Consumer<? super A> requirements) {
    isRight();
    requirements.accept(executed().getRight());
    return this;
  }

  /** Asserts that the {@code Left} error satisfies the given requirements. */
  public VResultPathAssert<E, A> hasLeftSatisfying(Consumer<? super E> requirements) {
    isLeft();
    requirements.accept(executed().getLeft());
    return this;
  }

  /** Asserts that the path fails with a defect (an exception outside the typed channel). */
  public VResultPathAssert<E, A> hasDefect() {
    isNotNull();
    ensureExecuted();
    Assertions.assertThat(executedDefect)
        .withFailMessage(
            "Expected VResultPath to fail with a defect but it produced %s", describeOutcome())
        .isNotNull();
    return this;
  }

  /** Asserts that the defect is of the expected type. */
  public VResultPathAssert<E, A> withDefectType(Class<? extends Throwable> expectedType) {
    hasDefect();
    Assertions.assertThat(executedDefect)
        .withFailMessage(
            "Expected defect of type <%s> but was <%s>",
            expectedType.getName(), defect().getClass().getName())
        .isInstanceOf(expectedType);
    return this;
  }

  /** Asserts that the defect's message contains the expected substring. */
  public VResultPathAssert<E, A> withDefectMessageContaining(String expectedSubstring) {
    hasDefect();
    String message = defect().getMessage();
    Assertions.assertThat(message)
        .withFailMessage(
            "Expected defect message to contain <%s> but was <%s>", expectedSubstring, message)
        .contains(expectedSubstring);
    return this;
  }

  /** Asserts that the VResultPath completes within the specified duration. */
  public VResultPathAssert<E, A> completesWithin(Duration timeout) {
    isNotNull();
    ensureExecuted();
    Assertions.assertThat(executionTimeMillis)
        .withFailMessage(
            "Expected completion within %dms but took %dms",
            timeout.toMillis(), executionTimeMillis)
        .isLessThanOrEqualTo(timeout.toMillis());
    return this;
  }
}
