// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.eitherorboth.EitherOrBothKind;

/**
 * Custom AssertJ assertions for {@link EitherOrBoth} instances (the inclusive-or), accepting either
 * a concrete {@code EitherOrBoth} or its {@link Kind} representation and narrowing internally.
 *
 * <p>Because {@code EitherOrBoth} has three cases, the fluent methods come in {@code Left}/{@code
 * Right}/{@code Both} triples.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.assertions.EitherOrBothAssert.assertThatEitherOrBoth;
 *
 * assertThatEitherOrBoth(EitherOrBoth.both("deprecated key", config))
 *     .isBoth()
 *     .hasBoth("deprecated key", config);
 *
 * assertThatEitherOrBoth(EitherOrBoth.right(42)).isRight().hasRight(42);
 * }</pre>
 *
 * @param <L> the type of the Left (warning/error) value
 * @param <R> the type of the Right (success) value
 */
public class EitherOrBothAssert<L, R>
    extends AbstractAssert<EitherOrBothAssert<L, R>, Kind<EitherOrBothKind.Witness<L>, R>> {

  /** Entry point accepting a {@code Kind<EitherOrBothKind.Witness<L>, R>}. */
  public static <L, R> EitherOrBothAssert<L, R> assertThatEitherOrBoth(
      Kind<EitherOrBothKind.Witness<L>, R> actual) {
    return new EitherOrBothAssert<>(actual);
  }

  /** Entry point accepting a bare {@code EitherOrBoth<L, R>}. */
  public static <L, R> EitherOrBothAssert<L, R> assertThatEitherOrBoth(EitherOrBoth<L, R> actual) {
    return new EitherOrBothAssert<>(EITHER_OR_BOTH.widen(actual));
  }

  protected EitherOrBothAssert(Kind<EitherOrBothKind.Witness<L>, R> actual) {
    super(actual, EitherOrBothAssert.class);
  }

  private EitherOrBoth<L, R> narrow() {
    return EITHER_OR_BOTH.narrow(actual);
  }

  /** Verifies the actual {@code EitherOrBoth} is a {@code Left}. */
  public EitherOrBothAssert<L, R> isLeft() {
    isNotNull();
    EitherOrBoth<L, R> e = narrow();
    Assertions.assertThat(e.isLeft())
        .withFailMessage(() -> "Expected EitherOrBoth to be Left but was: <" + e + ">")
        .isTrue();
    return this;
  }

  /** Verifies the actual {@code EitherOrBoth} is a {@code Right}. */
  public EitherOrBothAssert<L, R> isRight() {
    isNotNull();
    EitherOrBoth<L, R> e = narrow();
    Assertions.assertThat(e.isRight())
        .withFailMessage(() -> "Expected EitherOrBoth to be Right but was: <" + e + ">")
        .isTrue();
    return this;
  }

  /** Verifies the actual {@code EitherOrBoth} is a {@code Both}. */
  public EitherOrBothAssert<L, R> isBoth() {
    isNotNull();
    EitherOrBoth<L, R> e = narrow();
    Assertions.assertThat(e.isBoth())
        .withFailMessage(() -> "Expected EitherOrBoth to be Both but was: <" + e + ">")
        .isTrue();
    return this;
  }

  /** Verifies the actual {@code EitherOrBoth} is a {@code Left} containing {@code expected}. */
  public EitherOrBothAssert<L, R> hasLeft(L expected) {
    isLeft();
    L actualLeft = narrow().getLeft().get();
    Assertions.assertThat(actualLeft)
        .withFailMessage(
            "Expected EitherOrBoth.Left to contain <%s> but contained <%s>", expected, actualLeft)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies the actual {@code EitherOrBoth} is a {@code Right} containing {@code expected}. */
  public EitherOrBothAssert<L, R> hasRight(R expected) {
    isRight();
    R actualRight = narrow().getRight().get();
    Assertions.assertThat(actualRight)
        .withFailMessage(
            "Expected EitherOrBoth.Right to contain <%s> but contained <%s>", expected, actualRight)
        .isEqualTo(expected);
    return this;
  }

  /**
   * Verifies the actual {@code EitherOrBoth} is a {@code Both} containing the expected left and
   * right values.
   */
  public EitherOrBothAssert<L, R> hasBoth(L expectedLeft, R expectedRight) {
    isBoth();
    EitherOrBoth<L, R> e = narrow();
    L actualLeft = e.getLeft().get();
    R actualRight = e.getRight().get();
    Assertions.assertThat(actualLeft)
        .withFailMessage(
            "Expected EitherOrBoth.Both left <%s> but was <%s>", expectedLeft, actualLeft)
        .isEqualTo(expectedLeft);
    Assertions.assertThat(actualRight)
        .withFailMessage(
            "Expected EitherOrBoth.Both right <%s> but was <%s>", expectedRight, actualRight)
        .isEqualTo(expectedRight);
    return this;
  }

  /**
   * Verifies the actual {@code EitherOrBoth} is a {@code Left} whose value satisfies {@code
   * requirements}.
   */
  public EitherOrBothAssert<L, R> hasLeftSatisfying(Consumer<? super L> requirements) {
    isLeft();
    requirements.accept(narrow().getLeft().get());
    return this;
  }

  /**
   * Verifies the actual {@code EitherOrBoth} is a {@code Right} whose value satisfies {@code
   * requirements}.
   */
  public EitherOrBothAssert<L, R> hasRightSatisfying(Consumer<? super R> requirements) {
    isRight();
    requirements.accept(narrow().getRight().get());
    return this;
  }

  /**
   * Verifies the actual {@code EitherOrBoth} is a {@code Both} whose values satisfy {@code
   * requirements}.
   */
  public EitherOrBothAssert<L, R> hasBothSatisfying(BiConsumer<? super L, ? super R> requirements) {
    isBoth();
    EitherOrBoth<L, R> e = narrow();
    requirements.accept(e.getLeft().get(), e.getRight().get());
    return this;
  }
}
