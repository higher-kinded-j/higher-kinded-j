// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial08 NaturalTransformation — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial08_NaturalTransformation_Solution {

  /**
   * Why this is idiomatic: a {@code Natural<F, G>} is parametric in {@code A} — the {@code <A>} on
   * {@code apply} signals "I treat every element type the same". The body only touches the
   * structure ({@code Just} → singleton list, {@code Nothing} → empty list); it never inspects the
   * value.
   *
   * <p>Alternative: a plain {@code Function<Maybe<A>, List<A>>} for a fixed {@code A}. Same runtime
   * work; loses parametricity, so callers cannot reuse it across element types without rewriting.
   *
   * <p>Common wrong attempt: implement the body with a {@code switch} on the value of {@code A}
   * (e.g. special-case strings). That breaks the natural-transformation law — the result must
   * depend on shape, not on the contents.
   */
  @Test
  void exercise1_maybeToList() {
    // SOLUTION: Create a Natural transformation using anonymous class
    Natural<MaybeKind.Witness, ListKind.Witness> maybeToList =
        new Natural<>() {
          @Override
          public <A> Kind<ListKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
            Maybe<A> maybe = MAYBE.narrow(fa);
            List<A> list = maybe.map(List::of).orElse(List.of());
            return LIST.widen(list);
          }
        };

    // Test with Just
    Kind<MaybeKind.Witness, String> justHello = MAYBE.widen(Maybe.just("hello"));
    Kind<ListKind.Witness, String> listFromJust = maybeToList.apply(justHello);
    assertThat(LIST.narrow(listFromJust)).containsExactly("hello");

    // Test with Nothing
    Kind<MaybeKind.Witness, String> nothing = MAYBE.widen(Maybe.nothing());
    Kind<ListKind.Witness, String> listFromNothing = maybeToList.apply(nothing);
    assertThat(LIST.narrow(listFromNothing)).isEmpty();
  }

  /**
   * Why this is idiomatic: {@code either.fold(left -> Maybe.nothing(), Maybe::just)} is the
   * idiomatic "drop the error, keep the value" recipe. The transformation forgets the {@code
   * String} entirely — exactly what the {@code MaybeKind.Witness} target signals.
   *
   * <p>Alternative: pattern-match with {@code if (either.isRight())}. Equivalent; the {@code fold}
   * form is a single expression and pairs more naturally with the natural-transformation shape
   * (apply once, return once).
   *
   * <p>Common wrong attempt: keep the error in the {@code Maybe} by wrapping it ({@code
   * Maybe.just(left)} on the {@code Left} branch). The element type would change to {@code Object}
   * and the transformation is no longer natural — it depends on which branch fired.
   */
  @Test
  void exercise2_eitherToMaybe() {
    // SOLUTION: Transform Either<String, A> to Maybe<A>
    Natural<EitherKind.Witness<String>, MaybeKind.Witness> eitherToMaybe =
        new Natural<>() {
          @Override
          public <A> Kind<MaybeKind.Witness, A> apply(Kind<EitherKind.Witness<String>, A> fa) {
            Either<String, A> either = EITHER.narrow(fa);
            Maybe<A> maybe = either.fold(left -> Maybe.nothing(), Maybe::just);
            return MAYBE.widen(maybe);
          }
        };

    // Test with Right
    Kind<EitherKind.Witness<String>, Integer> right = EITHER.widen(Either.right(42));
    Kind<MaybeKind.Witness, Integer> maybeFromRight = eitherToMaybe.apply(right);
    assertThatMaybe(MAYBE.narrow(maybeFromRight)).hasValue(42);

    // Test with Left
    Kind<EitherKind.Witness<String>, Integer> left = EITHER.widen(Either.left("error"));
    Kind<MaybeKind.Witness, Integer> maybeFromLeft = eitherToMaybe.apply(left);
    assertThatMaybe(MAYBE.narrow(maybeFromLeft)).isNothing();
  }

  /**
   * Why this is idiomatic: "head of a list" is the canonical {@code List ~> Maybe} natural
   * transformation. Empty list to {@code Nothing}, non-empty to {@code Just(first)} — both branches
   * preserve the element type.
   *
   * <p>Alternative: {@code list.stream().findFirst()} for direct {@code Optional} access. Same
   * idea; the {@code Natural} form lifts that into the higher-kinded-j vocabulary so it can be
   * composed with other transformations.
   *
   * <p>Common wrong attempt: throw {@code NoSuchElementException} on empty input ({@code
   * list.get(0)} without the guard). That breaks totality — natural transformations must be defined
   * for every input of the source functor.
   */
  @Test
  void exercise3_listHead() {
    // SOLUTION: Take the head of a list
    Natural<ListKind.Witness, MaybeKind.Witness> listHead =
        new Natural<>() {
          @Override
          public <A> Kind<MaybeKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
            List<A> list = LIST.narrow(fa);
            Maybe<A> head = list.isEmpty() ? Maybe.nothing() : Maybe.just(list.get(0));
            return MAYBE.widen(head);
          }
        };

    // Test with non-empty list
    Kind<ListKind.Witness, String> nonEmpty = LIST.widen(List.of("first", "second"));
    Kind<MaybeKind.Witness, String> headNonEmpty = listHead.apply(nonEmpty);
    assertThatMaybe(MAYBE.narrow(headNonEmpty)).hasValue("first");

    // Test with empty list
    Kind<ListKind.Witness, String> empty = LIST.widen(List.of());
    Kind<MaybeKind.Witness, String> headEmpty = listHead.apply(empty);
    assertThatMaybe(MAYBE.narrow(headEmpty)).isNothing();
  }

  /**
   * Why this is idiomatic: {@code andThen} composes natural transformations end-to-end — {@code
   * Maybe ~> List ~> Maybe}. Each leg is parametric in {@code A}; the composition is automatically
   * parametric too, so no extra plumbing is needed.
   *
   * <p>Alternative: write a single transformation that performs both steps in one pass. Faster (one
   * allocation) but loses the named pieces; reach for the fused form only when profiling shows the
   * intermediate {@code List} matters.
   *
   * <p>Common wrong attempt: {@code listHead.andThen(maybeToList)}. The types disagree — {@code
   * listHead} starts at {@code List}, {@code maybeToList} starts at {@code Maybe}. Read
   * left-to-right: the first transformation's output must match the second's input.
   */
  @Test
  void exercise4_composition() {
    Natural<MaybeKind.Witness, ListKind.Witness> maybeToList =
        new Natural<>() {
          @Override
          public <A> Kind<ListKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
            Maybe<A> maybe = MAYBE.narrow(fa);
            return LIST.widen(maybe.map(List::of).orElse(List.of()));
          }
        };

    Natural<ListKind.Witness, MaybeKind.Witness> listHead =
        new Natural<>() {
          @Override
          public <A> Kind<MaybeKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
            List<A> list = LIST.narrow(fa);
            return MAYBE.widen(list.isEmpty() ? Maybe.nothing() : Maybe.just(list.get(0)));
          }
        };

    // SOLUTION: Compose using andThen
    Natural<MaybeKind.Witness, MaybeKind.Witness> composed = maybeToList.andThen(listHead);

    // Test: Just(x) -> [x] -> Just(x)
    Kind<MaybeKind.Witness, String> justValue = MAYBE.widen(Maybe.just("test"));
    Kind<MaybeKind.Witness, String> result = composed.apply(justValue);
    assertThatMaybe(MAYBE.narrow(result)).hasValue("test");

    // Test: Nothing -> [] -> Nothing
    Kind<MaybeKind.Witness, String> nothing = MAYBE.widen(Maybe.nothing());
    Kind<MaybeKind.Witness, String> resultNothing = composed.apply(nothing);
    assertThatMaybe(MAYBE.narrow(resultNothing)).isNothing();
  }

  /**
   * Why this is idiomatic: {@code Natural.identity()} is the unit of natural-transformation
   * composition — it returns the input unchanged, even by reference. Useful as a placeholder when
   * wiring composable pipelines that may or may not actually transform.
   *
   * <p>Alternative: {@code new Natural<>() { apply(fa) { return fa; } }}. Same behaviour; {@code
   * Natural.identity()} is the named, allocation-free spelling.
   *
   * <p>Common wrong attempt: implement "identity" by widening then narrowing ({@code
   * MAYBE.widen(MAYBE.narrow(fa))}). The result is equal but not the same reference, and the
   * round-trip is wasted work — identity must really be identity.
   */
  @Test
  void exercise5_identity() {
    // SOLUTION: Use Natural.identity()
    Natural<MaybeKind.Witness, MaybeKind.Witness> identity = Natural.identity();

    Kind<MaybeKind.Witness, Integer> original = MAYBE.widen(Maybe.just(42));

    // Apply identity
    Kind<MaybeKind.Witness, Integer> afterIdentity = identity.apply(original);

    // Should be unchanged
    assertThatMaybe(MAYBE.narrow(afterIdentity)).hasValue(42);

    // Should be the same reference
    assertThat(afterIdentity).isSameAs(original);
  }
}
