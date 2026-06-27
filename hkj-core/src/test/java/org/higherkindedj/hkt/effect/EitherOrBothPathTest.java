// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.FocusPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherOrBothPath<L, A> Complete Test Suite")
class EitherOrBothPathTest {

  private static final Semigroup<String> CONCAT = Semigroups.string();

  private EitherOrBothPath<String, Integer> left() {
    return Path.left("e", CONCAT);
  }

  private EitherOrBothPath<String, Integer> right() {
    return Path.right(5, CONCAT);
  }

  private EitherOrBothPath<String, Integer> both() {
    return Path.both("w", 5, CONCAT);
  }

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethods {

    @Test
    void rightLeftBothEitherOrBoth() {
      assertThat(right().run()).isEqualTo(EitherOrBoth.right(5));
      assertThat(left().run()).isEqualTo(EitherOrBoth.left("e"));
      assertThat(both().run()).isEqualTo(EitherOrBoth.both("w", 5));
      assertThat(Path.eitherOrBoth(EitherOrBoth.<String, Integer>both("w", 5), CONCAT).run())
          .isEqualTo(EitherOrBoth.both("w", 5));
    }

    @Test
    void nelConveniences() {
      assertThat(Path.<String, Integer>rightNel(5).run()).isEqualTo(EitherOrBoth.right(5));
      assertThat(Path.<String, Integer>leftNel("e").run())
          .isEqualTo(EitherOrBoth.left(NonEmptyList.single("e")));
      assertThat(Path.<String, Integer>bothNel("w", 5).run())
          .isEqualTo(EitherOrBoth.both(NonEmptyList.single("w"), 5));
      assertThat(Path.<String, Integer>rightNel(5).semigroup()).isNotNull();
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void factoriesRejectNull() {
      assertThatNullPointerException().isThrownBy(() -> Path.right(5, null));
      assertThatNullPointerException().isThrownBy(() -> Path.eitherOrBoth(null, CONCAT));
    }
  }

  @Nested
  @DisplayName("Run, accessors and fold")
  class Accessors {

    @Test
    void runAndSemigroup() {
      assertThat(right().run()).isEqualTo(EitherOrBoth.right(5));
      assertThat(right().semigroup()).isSameAs(CONCAT);
    }

    @Test
    void predicates() {
      assertThat(left().isLeft()).isTrue();
      assertThat(right().isRight()).isTrue();
      assertThat(both().isBoth()).isTrue();
    }

    @Test
    void warningsAcrossCases() {
      assertThat(left().warnings()).isEqualTo(Maybe.just("e"));
      assertThat(right().warnings()).isEqualTo(Maybe.nothing());
      assertThat(both().warnings()).isEqualTo(Maybe.just("w"));
    }

    @Test
    void getOrElseAndGetOrElseGet() {
      assertThat(left().getOrElse(99)).isEqualTo(99);
      assertThat(right().getOrElse(99)).isEqualTo(5);
      assertThat(both().getOrElse(99)).isEqualTo(5);
      assertThat(left().getOrElseGet(() -> 7)).isEqualTo(7);
      assertThat(right().getOrElseGet(() -> 7)).isEqualTo(5);
      assertThat(both().getOrElseGet(() -> 7)).isEqualTo(5);
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // null passed deliberately to verify rejection
    void getOrElseGetRejectsNull() {
      assertThatNullPointerException().isThrownBy(() -> right().getOrElseGet(null));
    }

    @Test
    void foldThreeWays() {
      assertThat(left().<String>fold(l -> "L" + l, r -> "R" + r, (l, r) -> "B" + l + r))
          .isEqualTo("Le");
      assertThat(right().<String>fold(l -> "L" + l, r -> "R" + r, (l, r) -> "B" + l + r))
          .isEqualTo("R5");
      assertThat(both().<String>fold(l -> "L" + l, r -> "R" + r, (l, r) -> "B" + l + r))
          .isEqualTo("Bw5");
    }
  }

  @Nested
  @DisplayName("Conversions")
  class Conversions {

    @Test
    void toEitherPathVariants() {
      assertThat(both().toEitherPathDroppingWarnings().run()).isEqualTo(Either.right(5));
      assertThat(both().toEitherPathFailingOnWarnings().run()).isEqualTo(Either.left("w"));
      assertThat(left().toEitherPathDroppingWarnings().run()).isEqualTo(Either.left("e"));
    }

    @Test
    void toValidationPathDropsWarnings() {
      assertThat(both().toValidationPath().run()).isEqualTo(Validated.valid(5));
      assertThat(left().toValidationPath().run()).isEqualTo(Validated.invalid("e"));
    }

    @Test
    void toMaybePathAndOptionalPath() {
      assertThat(both().toMaybePath().run()).isEqualTo(Maybe.just(5));
      assertThat(left().toMaybePath().run()).isEqualTo(Maybe.nothing());
      assertThat(right().toOptionalPath().run()).contains(5);
      assertThat(both().toOptionalPath().run()).contains(5);
      assertThat(left().toOptionalPath().run()).isEmpty();
    }

    @Test
    void toTryPath() {
      assertThat(right().toTryPath(IllegalStateException::new).run().isSuccess()).isTrue();
      assertThat(both().toTryPath(IllegalStateException::new).run().isSuccess()).isTrue();
      assertThat(left().toTryPath(IllegalStateException::new).run().isFailure()).isTrue();
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // null passed deliberately to verify rejection
    void toTryPathRejectsNull() {
      assertThatNullPointerException().isThrownBy(() -> right().toTryPath(null));
    }
  }

  @Nested
  @DisplayName("Composable (map, peek, peekLeft)")
  class Composable {

    @Test
    void mapIsRightBiased() {
      assertThat(left().map(r -> r + 1).run()).isEqualTo(EitherOrBoth.left("e"));
      assertThat(right().map(r -> r + 1).run()).isEqualTo(EitherOrBoth.right(6));
      assertThat(both().map(r -> r + 1).run()).isEqualTo(EitherOrBoth.both("w", 6));
    }

    @Test
    void peekObservesRightOnlyWhenPresent() {
      AtomicReference<Integer> seen = new AtomicReference<>();
      left().peek(seen::set);
      assertThat(seen.get()).isNull();
      right().peek(seen::set);
      assertThat(seen.get()).isEqualTo(5);
      both().peek(seen::set);
      assertThat(seen.get()).isEqualTo(5);
    }

    @Test
    void peekLeftObservesWarningsWhenPresent() {
      AtomicReference<String> seen = new AtomicReference<>();
      right().peekLeft(seen::set);
      assertThat(seen.get()).isNull();
      left().peekLeft(seen::set);
      assertThat(seen.get()).isEqualTo("e");
      both().peekLeft(seen::set);
      assertThat(seen.get()).isEqualTo("w");
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void composableRejectNull() {
      assertThatNullPointerException().isThrownBy(() -> right().map(null));
      assertThatNullPointerException().isThrownBy(() -> right().peek(null));
      assertThatNullPointerException().isThrownBy(() -> right().peekLeft(null));
    }
  }

  @Nested
  @DisplayName("Chainable (via, then) — short-circuit, accumulating Both")
  class Chainable {

    @Test
    void viaSemantics() {
      assertThat(left().via(a -> Path.right(a + 1, CONCAT)).run())
          .isEqualTo(EitherOrBoth.left("e"));
      assertThat(right().via(a -> Path.right(a + 1, CONCAT)).run())
          .isEqualTo(EitherOrBoth.right(6));
      assertThat(both().via(a -> Path.both("x", a + 1, CONCAT)).run())
          .isEqualTo(EitherOrBoth.both("wx", 6)); // accumulates
    }

    @Test
    void thenSemantics() {
      assertThat(left().then(() -> Path.right(1, CONCAT)).run()).isEqualTo(EitherOrBoth.left("e"));
      assertThat(right().then(() -> Path.right(1, CONCAT)).run()).isEqualTo(EitherOrBoth.right(1));
      assertThat(both().then(() -> Path.both("x", 1, CONCAT)).run())
          .isEqualTo(EitherOrBoth.both("wx", 1));
    }

    @Test
    void viaRejectsNonEitherOrBothPathResult() {
      assertThatThrownBy(() -> right().via(a -> Path.just(a)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("via");
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void chainableRejectNull() {
      assertThatNullPointerException().isThrownBy(() -> right().via(null));
      assertThatNullPointerException().isThrownBy(() -> right().then(null));
    }
  }

  @Nested
  @DisplayName("Combinable (zipWith) — monad-consistent")
  class CombinableOps {

    @Test
    void zipWithShortCircuitsAndAccumulatesBoth() {
      assertThat(right().zipWith(Path.right(3, CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.right(8));
      assertThat(both().zipWith(Path.both("x", 3, CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.both("wx", 8));
      // Left short-circuits, dropping the other side's warnings (contrast with zipWithAccum)
      assertThat(left().zipWith(Path.both("x", 3, CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.left("e"));
    }

    @Test
    void zipWith3() {
      EitherOrBothPath<String, Integer> result =
          both().zipWith3(Path.both("x", 3, CONCAT), Path.right(2, CONCAT), (a, b, c) -> a + b + c);
      assertThat(result.run()).isEqualTo(EitherOrBoth.both("wx", 10));
    }

    @Test
    void zipWithRejectsNonEitherOrBothPath() {
      assertThatThrownBy(() -> right().zipWith(Path.just(1), Integer::sum))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("zipWith");
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void zipWithRejectNull() {
      assertThatNullPointerException().isThrownBy(() -> right().zipWith(null, Integer::sum));
      assertThatNullPointerException()
          .isThrownBy(() -> right().zipWith(Path.right(1, CONCAT), null));
    }
  }

  @Nested
  @DisplayName("Accumulating (zipWithAccum) — collect every left")
  class AccumulatingOps {

    @Test
    void zipWithAccumMatrix() {
      assertThat(right().zipWithAccum(Path.right(3, CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.right(8));
      assertThat(right().zipWithAccum(Path.both("x", 3, CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.both("x", 8));
      assertThat(both().zipWithAccum(Path.right(3, CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.both("w", 8));
      assertThat(both().zipWithAccum(Path.both("x", 3, CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.both("wx", 8));
      // Left present on either side: right dropped, every left accumulated
      assertThat(left().zipWithAccum(Path.right(3, CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.left("e"));
      assertThat(right().zipWithAccum(Path.left("x", CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.left("x"));
      assertThat(left().zipWithAccum(Path.left("x", CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.left("ex"));
      assertThat(both().zipWithAccum(Path.left("x", CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.left("wx"));
      assertThat(left().zipWithAccum(Path.both("x", 3, CONCAT), Integer::sum).run())
          .isEqualTo(EitherOrBoth.left("ex"));
    }

    @Test
    void zipWith3AccumCollectsAll() {
      EitherOrBothPath<String, Integer> result =
          both()
              .zipWith3Accum(
                  Path.<String, Integer>left("x", CONCAT),
                  Path.<String, Integer>left("y", CONCAT),
                  (a, b, c) -> a + b + c);
      assertThat(result.run()).isEqualTo(EitherOrBoth.left("wxy"));

      // All three present: the combiner runs and warnings accumulate.
      EitherOrBothPath<String, Integer> combined =
          both()
              .zipWith3Accum(
                  Path.both("x", 3, CONCAT), Path.right(2, CONCAT), (a, b, c) -> a + b + c);
      assertThat(combined.run()).isEqualTo(EitherOrBoth.both("wx", 10));
    }

    @Test
    void andAlsoKeepsThisAndThenKeepsOther() {
      assertThat(both().andAlso(Path.both("x", 9, CONCAT)).run())
          .isEqualTo(EitherOrBoth.both("wx", 5));
      assertThat(both().andThen(Path.both("x", 9, CONCAT)).run())
          .isEqualTo(EitherOrBoth.both("wx", 9));
    }

    @Test
    void zipWithAccumRejectsNonEitherOrBothPath() {
      // ValidationPath is the other Accumulating implementation: a usage error here.
      assertThatThrownBy(() -> right().zipWithAccum(Path.valid(1, CONCAT), Integer::sum))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("non-EitherOrBothPath");
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void accumulatingRejectNull() {
      assertThatNullPointerException().isThrownBy(() -> right().zipWithAccum(null, Integer::sum));
      assertThatNullPointerException()
          .isThrownBy(() -> right().zipWithAccum(Path.right(1, CONCAT), null));
      assertThatNullPointerException().isThrownBy(() -> right().andAlso(null));
      assertThatNullPointerException().isThrownBy(() -> right().andThen(null));
    }
  }

  @Nested
  @DisplayName("Recoverable (recover, recoverWith, orElse, mapError)")
  class RecoverableOps {

    @Test
    void recoverActsOnLeftOnly() {
      assertThat(left().recover(l -> 0).run()).isEqualTo(EitherOrBoth.right(0));
      assertThat(right().recover(l -> 0).run()).isEqualTo(EitherOrBoth.right(5));
      assertThat(both().recover(l -> 0).run()).isEqualTo(EitherOrBoth.both("w", 5));
    }

    @Test
    void recoverWithActsOnLeftOnly() {
      assertThat(left().recoverWith(l -> Path.right(0, CONCAT)).run())
          .isEqualTo(EitherOrBoth.right(0));
      assertThat(right().recoverWith(l -> Path.right(0, CONCAT)).run())
          .isEqualTo(EitherOrBoth.right(5));
      assertThat(both().recoverWith(l -> Path.right(0, CONCAT)).run())
          .isEqualTo(EitherOrBoth.both("w", 5));
    }

    @Test
    void orElseActsOnLeftOnly() {
      assertThat(left().orElse(() -> Path.right(0, CONCAT)).run()).isEqualTo(EitherOrBoth.right(0));
      assertThat(right().orElse(() -> Path.right(0, CONCAT)).run())
          .isEqualTo(EitherOrBoth.right(5));
      assertThat(both().orElse(() -> Path.right(0, CONCAT)).run())
          .isEqualTo(EitherOrBoth.both("w", 5));
    }

    @Test
    void recoverWithRejectsNonEitherOrBothPath() {
      assertThatThrownBy(
              () -> left().recoverWith(l -> Path.<String, Integer>either(Either.right(0))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("recovery");
    }

    @Test
    void mapErrorOnRightUsesPlaceholderSemigroup() {
      EitherOrBothPath<Integer, Integer> mapped = right().mapError(String::length);
      assertThat(mapped.run()).isEqualTo(EitherOrBoth.right(5));
      // The placeholder semigroup throws if accumulation is attempted.
      assertThatThrownBy(() -> mapped.semigroup().combine(1, 2))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapErrorWithWarningsIsUnsupported() {
      assertThatThrownBy(() -> left().mapError(String::length))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> both().mapError(String::length))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapErrorWithRebasesWarnings() {
      Semigroup<Integer> sum = Integer::sum;
      assertThat(left().mapErrorWith(String::length, sum).run()).isEqualTo(EitherOrBoth.left(1));
      assertThat(right().mapErrorWith(String::length, sum).run()).isEqualTo(EitherOrBoth.right(5));
      assertThat(both().mapErrorWith(String::length, sum).run()).isEqualTo(EitherOrBoth.both(1, 5));
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void recoverableRejectNull() {
      assertThatNullPointerException().isThrownBy(() -> left().recover(null));
      assertThatNullPointerException().isThrownBy(() -> left().recoverWith(null));
      assertThatNullPointerException().isThrownBy(() -> left().orElse(null));
      assertThatNullPointerException().isThrownBy(() -> right().mapError(null));
      assertThatNullPointerException().isThrownBy(() -> right().mapErrorWith(null, Integer::sum));
      assertThatNullPointerException().isThrownBy(() -> right().mapErrorWith(String::length, null));
    }
  }

  @Nested
  @DisplayName("Focus bridge")
  class FocusBridge {

    record Box(int n) {}

    record OptBox(Optional<Integer> maybe) {}

    private final Lens<Box, Integer> boxN = Lens.of(Box::n, (b, v) -> new Box(v));
    private final Lens<OptBox, Optional<Integer>> optLens =
        Lens.of(OptBox::maybe, (b, o) -> new OptBox(o));
    private final Affine<Optional<Integer>, Integer> some = FocusPaths.optionalSome();

    @Test
    void focusFocusPathKeepsCase() {
      FocusPath<Box, Integer> path = FocusPath.of(boxN);
      assertThat(Path.right(new Box(7), CONCAT).focus(path).run()).isEqualTo(EitherOrBoth.right(7));
      assertThat(Path.both("w", new Box(7), CONCAT).focus(path).run())
          .isEqualTo(EitherOrBoth.both("w", 7));
    }

    @Test
    void focusAffinePathMatchesOrFailsWithError() {
      AffinePath<OptBox, Integer> affine = FocusPath.of(optLens).via(some);
      assertThat(Path.right(new OptBox(Optional.of(7)), CONCAT).focus(affine, "missing").run())
          .isEqualTo(EitherOrBoth.right(7));
      assertThat(Path.right(new OptBox(Optional.empty()), CONCAT).focus(affine, "missing").run())
          .isEqualTo(EitherOrBoth.left("missing"));
    }

    @Test
    @SuppressWarnings("DataFlowIssue") // nulls passed deliberately to verify rejection
    void focusRejectsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> right().focus((FocusPath<Integer, Integer>) null));
      AffinePath<OptBox, Integer> affine = FocusPath.of(optLens).via(some);
      assertThatNullPointerException()
          .isThrownBy(() -> Path.right(new OptBox(Optional.of(1)), CONCAT).focus(affine, null));
    }
  }

  @Nested
  @DisplayName("Object methods")
  class ObjectMethods {

    @Test
    void equalsHashCodeToString() {
      EitherOrBothPath<String, Integer> p = both();
      assertThat(p.equals(p)).isTrue(); // same-reference branch
      assertThat(p)
          .isEqualTo(Path.both("w", 5, CONCAT))
          .hasSameHashCodeAs(Path.both("w", 5, CONCAT));
      assertThat(p).isNotEqualTo(right()).isNotEqualTo("x");
      assertThat(p).hasToString("EitherOrBothPath(Both(w, 5))");
    }
  }
}
