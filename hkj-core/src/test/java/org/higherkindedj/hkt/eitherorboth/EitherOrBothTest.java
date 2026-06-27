// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherOrBoth (the sealed type)")
class EitherOrBothTest {

  private static final Semigroup<String> CONCAT = Semigroups.string();

  private static final EitherOrBoth<String, Integer> LEFT = EitherOrBoth.left("e");
  private static final EitherOrBoth<String, Integer> RIGHT = EitherOrBoth.right(5);
  private static final EitherOrBoth<String, Integer> BOTH = EitherOrBoth.both("w", 5);

  @Nested
  @DisplayName("Construction & predicates")
  class Construction {

    @Test
    void leftIsLeftOnly() {
      assertThat(LEFT.isLeft()).isTrue();
      assertThat(LEFT.isRight()).isFalse();
      assertThat(LEFT.isBoth()).isFalse();
      assertThat(LEFT).isInstanceOf(EitherOrBoth.Left.class);
    }

    @Test
    void rightIsRightOnly() {
      assertThat(RIGHT.isLeft()).isFalse();
      assertThat(RIGHT.isRight()).isTrue();
      assertThat(RIGHT.isBoth()).isFalse();
      assertThat(RIGHT).isInstanceOf(EitherOrBoth.Right.class);
    }

    @Test
    void bothIsBothOnly() {
      assertThat(BOTH.isLeft()).isFalse();
      assertThat(BOTH.isRight()).isFalse();
      assertThat(BOTH.isBoth()).isTrue();
      assertThat(BOTH).isInstanceOf(EitherOrBoth.Both.class);
    }
  }

  @Nested
  @DisplayName("Total accessors")
  class Accessors {

    @Test
    void getLeftAcrossCases() {
      assertThat(LEFT.getLeft()).isEqualTo(Maybe.just("e"));
      assertThat(RIGHT.getLeft()).isEqualTo(Maybe.nothing());
      assertThat(BOTH.getLeft()).isEqualTo(Maybe.just("w"));
    }

    @Test
    void getRightAcrossCases() {
      assertThat(LEFT.getRight()).isEqualTo(Maybe.nothing());
      assertThat(RIGHT.getRight()).isEqualTo(Maybe.just(5));
      assertThat(BOTH.getRight()).isEqualTo(Maybe.just(5));
    }
  }

  @Nested
  @DisplayName("fold")
  class Fold {

    private final Function<String, String> onLeft = l -> "L:" + l;
    private final Function<Integer, String> onRight = r -> "R:" + r;
    private final BiFunction<String, Integer, String> onBoth = (l, r) -> "B:" + l + ":" + r;

    @Test
    void foldAcrossCases() {
      assertThat(LEFT.fold(onLeft, onRight, onBoth)).isEqualTo("L:e");
      assertThat(RIGHT.fold(onLeft, onRight, onBoth)).isEqualTo("R:5");
      assertThat(BOTH.fold(onLeft, onRight, onBoth)).isEqualTo("B:w:5");
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void foldRejectsNullFunctions() {
      assertThatNullPointerException().isThrownBy(() -> RIGHT.fold(null, onRight, onBoth));
      assertThatNullPointerException().isThrownBy(() -> RIGHT.fold(onLeft, null, onBoth));
      assertThatNullPointerException().isThrownBy(() -> RIGHT.fold(onLeft, onRight, null));
    }
  }

  @Nested
  @DisplayName("map / mapLeft / bimap")
  class Transformations {

    @Test
    void mapIsRightBiased() {
      assertThat(LEFT.map(r -> r + 1)).isEqualTo(EitherOrBoth.left("e"));
      assertThat(RIGHT.map(r -> r + 1)).isEqualTo(EitherOrBoth.right(6));
      assertThat(BOTH.map(r -> r + 1)).isEqualTo(EitherOrBoth.both("w", 6));
    }

    @Test
    void mapLeftTouchesOnlyLeft() {
      assertThat(LEFT.mapLeft(l -> l + "!")).isEqualTo(EitherOrBoth.left("e!"));
      assertThat(RIGHT.mapLeft(l -> l + "!")).isEqualTo(EitherOrBoth.right(5));
      assertThat(BOTH.mapLeft(l -> l + "!")).isEqualTo(EitherOrBoth.both("w!", 5));
    }

    @Test
    void bimapTouchesBothChannels() {
      Function<String, String> f = l -> l + "!";
      Function<Integer, Integer> g = r -> r * 10;
      assertThat(LEFT.bimap(f, g)).isEqualTo(EitherOrBoth.left("e!"));
      assertThat(RIGHT.bimap(f, g)).isEqualTo(EitherOrBoth.right(50));
      assertThat(BOTH.bimap(f, g)).isEqualTo(EitherOrBoth.both("w!", 50));
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void transformationsRejectNullFunctions() {
      assertThatNullPointerException().isThrownBy(() -> RIGHT.map(null));
      assertThatNullPointerException().isThrownBy(() -> RIGHT.mapLeft(null));
      assertThatNullPointerException().isThrownBy(() -> RIGHT.bimap(null, r -> r));
      assertThatNullPointerException().isThrownBy(() -> RIGHT.bimap(l -> l, null));
    }
  }

  @Nested
  @DisplayName("flatMap — the accumulation matrix")
  class FlatMap {

    @Test
    void leftShortCircuits() {
      assertThat(LEFT.flatMap(CONCAT, r -> EitherOrBoth.right(r + 1)))
          .isEqualTo(EitherOrBoth.left("e"));
    }

    @Test
    void rightPassesResultThrough() {
      assertThat(RIGHT.flatMap(CONCAT, r -> EitherOrBoth.left("x")))
          .isEqualTo(EitherOrBoth.left("x"));
      assertThat(RIGHT.flatMap(CONCAT, r -> EitherOrBoth.right(r + 1)))
          .isEqualTo(EitherOrBoth.right(6));
      assertThat(RIGHT.flatMap(CONCAT, r -> EitherOrBoth.both("x", r + 1)))
          .isEqualTo(EitherOrBoth.both("x", 6));
    }

    @Test
    void bothAccumulatesAcrossEveryResult() {
      assertThat(BOTH.flatMap(CONCAT, r -> EitherOrBoth.left("x")))
          .isEqualTo(EitherOrBoth.left("wx")); // Both -> Left: w ⊕ x
      assertThat(BOTH.flatMap(CONCAT, r -> EitherOrBoth.right(r + 1)))
          .isEqualTo(EitherOrBoth.both("w", 6)); // Both -> Right: carry warning
      assertThat(BOTH.flatMap(CONCAT, r -> EitherOrBoth.both("x", r + 1)))
          .isEqualTo(EitherOrBoth.both("wx", 6)); // Both -> Both: w ⊕ x
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void rejectsNullArguments() {
      assertThatNullPointerException().isThrownBy(() -> RIGHT.flatMap(null, r -> RIGHT));
      assertThatNullPointerException().isThrownBy(() -> RIGHT.flatMap(CONCAT, null));
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // mapper deliberately returns null to verify rejection
    void rejectsNullMapperResult() {
      assertThatThrownBy(() -> RIGHT.flatMap(CONCAT, r -> null))
          .isInstanceOf(KindUnwrapException.class);
      assertThatThrownBy(() -> BOTH.flatMap(CONCAT, r -> null))
          .isInstanceOf(KindUnwrapException.class);
    }
  }

  @Nested
  @DisplayName("Conversions")
  class Conversions {

    @Test
    void toEitherDroppingWarnings() {
      assertThat(LEFT.toEitherDroppingWarnings()).isEqualTo(Either.left("e"));
      assertThat(RIGHT.toEitherDroppingWarnings()).isEqualTo(Either.right(5));
      assertThat(BOTH.toEitherDroppingWarnings()).isEqualTo(Either.right(5));
    }

    @Test
    void toEitherFailingOnWarnings() {
      assertThat(LEFT.toEitherFailingOnWarnings()).isEqualTo(Either.left("e"));
      assertThat(RIGHT.toEitherFailingOnWarnings()).isEqualTo(Either.right(5));
      assertThat(BOTH.toEitherFailingOnWarnings()).isEqualTo(Either.left("w"));
    }

    @Test
    void toValidatedDropsWarnings() {
      assertThat(LEFT.toValidated()).isEqualTo(Validated.invalid("e"));
      assertThat(RIGHT.toValidated()).isEqualTo(Validated.valid(5));
      assertThat(BOTH.toValidated()).isEqualTo(Validated.valid(5));
    }

    @Test
    void toMaybeKeepsTheRight() {
      assertThat(LEFT.toMaybe()).isEqualTo(Maybe.nothing());
      assertThat(RIGHT.toMaybe()).isEqualTo(Maybe.just(5));
      assertThat(BOTH.toMaybe()).isEqualTo(Maybe.just(5));
    }

    @Test
    void fromEither() {
      assertThat(EitherOrBoth.fromEither(Either.<String, Integer>left("e")))
          .isEqualTo(EitherOrBoth.left("e"));
      assertThat(EitherOrBoth.fromEither(Either.<String, Integer>right(5)))
          .isEqualTo(EitherOrBoth.right(5));
    }

    @Test
    void fromValidated() {
      assertThat(EitherOrBoth.fromValidated(Validated.<String, Integer>invalid("e")))
          .isEqualTo(EitherOrBoth.left("e"));
      assertThat(EitherOrBoth.fromValidated(Validated.<String, Integer>valid(5)))
          .isEqualTo(EitherOrBoth.right(5));
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void fromRejectsNull() {
      assertThatNullPointerException().isThrownBy(() -> EitherOrBoth.fromEither(null));
      assertThatNullPointerException().isThrownBy(() -> EitherOrBoth.fromValidated(null));
    }
  }

  @Nested
  @DisplayName("Records — toString & null-safety")
  class Records {

    @Test
    void toStringIsReadable() {
      assertThat(LEFT).hasToString("Left(e)");
      assertThat(RIGHT).hasToString("Right(5)");
      assertThat(BOTH).hasToString("Both(w, 5)");
    }

    @Test
    void recordsAreValueEqual() {
      assertThat(EitherOrBoth.both("w", 5)).isEqualTo(BOTH).hasSameHashCodeAs(BOTH);
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void rejectNullComponents() {
      assertThatNullPointerException().isThrownBy(() -> EitherOrBoth.left(null));
      assertThatNullPointerException().isThrownBy(() -> EitherOrBoth.right(null));
      assertThatNullPointerException().isThrownBy(() -> EitherOrBoth.both(null, 5));
      assertThatNullPointerException().isThrownBy(() -> EitherOrBoth.both("w", null));
    }
  }
}
