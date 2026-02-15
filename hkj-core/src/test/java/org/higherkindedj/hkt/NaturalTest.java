// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for {@link Natural} transformations.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Basic apply operations
 *   <li>Identity transformation
 *   <li>Composition (andThen and compose)
 *   <li>Composition laws (associativity, identity)
 *   <li>Null handling
 * </ul>
 */
@DisplayName("Natural Transformation Tests")
class NaturalTest {

  // ============================================================================
  // Test Natural Transformations
  // ============================================================================

  /** Natural transformation from Maybe to List: Just(x) -> [x], Nothing -> [] */
  private static final Natural<MaybeKind.Witness, ListKind.Witness> MAYBE_TO_LIST =
      new Natural<>() {
        @Override
        public <A> Kind<ListKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
          Maybe<A> maybe = MAYBE.narrow(fa);
          if (maybe.isJust()) {
            return LIST.widen(List.of(maybe.get()));
          } else {
            return LIST.widen(List.of());
          }
        }
      };

  /** Natural transformation from List to Maybe: [] -> Nothing, [x, ...] -> Just(x) */
  private static final Natural<ListKind.Witness, MaybeKind.Witness> LIST_HEAD_MAYBE =
      new Natural<>() {
        @Override
        public <A> Kind<MaybeKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
          List<A> list = LIST.narrow(fa);
          if (list.isEmpty()) {
            return MAYBE.nothing();
          } else {
            return MAYBE.just(list.getFirst());
          }
        }
      };

  /** Natural transformation from List to List: reverses the list */
  private static final Natural<ListKind.Witness, ListKind.Witness> LIST_REVERSE =
      new Natural<>() {
        @Override
        public <A> Kind<ListKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
          List<A> list = LIST.narrow(fa);
          List<A> reversed = list.reversed();
          return LIST.widen(reversed);
        }
      };

  // ============================================================================
  // Basic Apply Tests
  // ============================================================================

  @Nested
  @DisplayName("Basic Apply Operations")
  class BasicApplyTests {

    @Test
    @DisplayName("maybeToList transforms Just to singleton list")
    void maybeToListTransformsJustToSingletonList() {
      Kind<MaybeKind.Witness, String> just = MAYBE.just("hello");

      Kind<ListKind.Witness, String> result = MAYBE_TO_LIST.apply(just);

      List<String> list = LIST.narrow(result);
      assertThat(list).containsExactly("hello");
    }

    @Test
    @DisplayName("maybeToList transforms Nothing to empty list")
    void maybeToListTransformsNothingToEmptyList() {
      Kind<MaybeKind.Witness, String> nothing = MAYBE.nothing();

      Kind<ListKind.Witness, String> result = MAYBE_TO_LIST.apply(nothing);

      List<String> list = LIST.narrow(result);
      assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("listHeadMaybe transforms non-empty list to Just")
    void listHeadMaybeTransformsNonEmptyListToJust() {
      Kind<ListKind.Witness, Integer> list = LIST.widen(List.of(1, 2, 3));

      Kind<MaybeKind.Witness, Integer> result = LIST_HEAD_MAYBE.apply(list);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("listHeadMaybe transforms empty list to Nothing")
    void listHeadMaybeTransformsEmptyListToNothing() {
      Kind<ListKind.Witness, Integer> list = LIST.widen(List.of());

      Kind<MaybeKind.Witness, Integer> result = LIST_HEAD_MAYBE.apply(list);

      Maybe<Integer> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("apply works with different types polymorphically")
    void applyWorksPolymorphically() {
      // Test with String
      Kind<MaybeKind.Witness, String> stringJust = MAYBE.just("test");
      Kind<ListKind.Witness, String> stringResult = MAYBE_TO_LIST.apply(stringJust);
      assertThat(LIST.narrow(stringResult)).containsExactly("test");

      // Test with Integer
      Kind<MaybeKind.Witness, Integer> intJust = MAYBE.just(42);
      Kind<ListKind.Witness, Integer> intResult = MAYBE_TO_LIST.apply(intJust);
      assertThat(LIST.narrow(intResult)).containsExactly(42);

      // Test with custom type
      record Person(String name) {}

      Kind<MaybeKind.Witness, Person> personJust = MAYBE.just(new Person("Alice"));
      Kind<ListKind.Witness, Person> personResult = MAYBE_TO_LIST.apply(personJust);
      assertThat(LIST.narrow(personResult)).containsExactly(new Person("Alice"));
    }
  }

  // ============================================================================
  // Identity Tests
  // ============================================================================

  @Nested
  @DisplayName("Identity Transformation")
  class IdentityTests {

    @Test
    @DisplayName("identity returns input unchanged for Maybe")
    void identityReturnsInputUnchangedForMaybe() {
      Natural<MaybeKind.Witness, MaybeKind.Witness> id = Natural.identity();

      Kind<MaybeKind.Witness, String> input = MAYBE.just("hello");
      Kind<MaybeKind.Witness, String> result = id.apply(input);

      assertThat(result).isSameAs(input);
    }

    @Test
    @DisplayName("identity returns input unchanged for List")
    void identityReturnsInputUnchangedForList() {
      Natural<ListKind.Witness, ListKind.Witness> id = Natural.identity();

      Kind<ListKind.Witness, Integer> input = LIST.widen(List.of(1, 2, 3));
      Kind<ListKind.Witness, Integer> result = id.apply(input);

      assertThat(result).isSameAs(input);
    }

    @Test
    @DisplayName("identity works with Nothing")
    void identityWorksWithNothing() {
      Natural<MaybeKind.Witness, MaybeKind.Witness> id = Natural.identity();

      Kind<MaybeKind.Witness, String> nothing = MAYBE.nothing();
      Kind<MaybeKind.Witness, String> result = id.apply(nothing);

      assertThat(result).isSameAs(nothing);
    }
  }

  // ============================================================================
  // Composition Tests
  // ============================================================================

  @Nested
  @DisplayName("Composition Operations")
  class CompositionTests {

    @Test
    @DisplayName("andThen composes transformations left to right")
    void andThenComposesLeftToRight() {
      // Maybe -> List -> Maybe (head)
      Natural<MaybeKind.Witness, MaybeKind.Witness> roundTrip =
          MAYBE_TO_LIST.andThen(LIST_HEAD_MAYBE);

      Kind<MaybeKind.Witness, String> input = MAYBE.just("hello");
      Kind<MaybeKind.Witness, String> result = roundTrip.apply(input);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("andThen preserves Nothing through transformation")
    void andThenPreservesNothing() {
      Natural<MaybeKind.Witness, MaybeKind.Witness> roundTrip =
          MAYBE_TO_LIST.andThen(LIST_HEAD_MAYBE);

      Kind<MaybeKind.Witness, String> nothing = MAYBE.nothing();
      Kind<MaybeKind.Witness, String> result = roundTrip.apply(nothing);

      Maybe<String> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("compose composes transformations right to left")
    void composeComposesRightToLeft() {
      // List -> Maybe -> List (equivalent to LIST_HEAD_MAYBE.andThen(MAYBE_TO_LIST))
      Natural<ListKind.Witness, ListKind.Witness> composed = MAYBE_TO_LIST.compose(LIST_HEAD_MAYBE);

      Kind<ListKind.Witness, Integer> input = LIST.widen(List.of(1, 2, 3));
      Kind<ListKind.Witness, Integer> result = composed.apply(input);

      List<Integer> list = LIST.narrow(result);
      assertThat(list).containsExactly(1); // Head of [1,2,3] is 1, converted to [1]
    }

    @Test
    @DisplayName("compose with empty list gives empty list")
    void composeWithEmptyListGivesEmptyList() {
      Natural<ListKind.Witness, ListKind.Witness> composed = MAYBE_TO_LIST.compose(LIST_HEAD_MAYBE);

      Kind<ListKind.Witness, Integer> input = LIST.widen(List.of());
      Kind<ListKind.Witness, Integer> result = composed.apply(input);

      List<Integer> list = LIST.narrow(result);
      assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("andThen throws NullPointerException for null argument")
    void andThenThrowsForNullArgument() {
      assertThatThrownBy(() -> MAYBE_TO_LIST.andThen(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("after");
    }

    @Test
    @DisplayName("compose throws NullPointerException for null argument")
    void composeThrowsForNullArgument() {
      assertThatThrownBy(() -> MAYBE_TO_LIST.compose(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("before");
    }
  }

  // ============================================================================
  // Composition Laws Tests
  // ============================================================================

  @Nested
  @DisplayName("Composition Laws")
  class CompositionLawsTests {

    @Test
    @DisplayName("composition is associative: (f.andThen(g)).andThen(h) == f.andThen(g.andThen(h))")
    void compositionIsAssociative() {
      // f: Maybe -> List, g: List -> List (reverse), h: List -> Maybe
      Natural<MaybeKind.Witness, MaybeKind.Witness> leftAssoc =
          MAYBE_TO_LIST.andThen(LIST_REVERSE).andThen(LIST_HEAD_MAYBE);

      Natural<MaybeKind.Witness, MaybeKind.Witness> rightAssoc =
          MAYBE_TO_LIST.andThen(LIST_REVERSE.andThen(LIST_HEAD_MAYBE));

      Kind<MaybeKind.Witness, String> input = MAYBE.just("test");

      Kind<MaybeKind.Witness, String> leftResult = leftAssoc.apply(input);
      Kind<MaybeKind.Witness, String> rightResult = rightAssoc.apply(input);

      assertThat(MAYBE.narrow(leftResult).get()).isEqualTo(MAYBE.narrow(rightResult).get());
    }

    @Test
    @DisplayName("left identity: identity.andThen(f) == f")
    void leftIdentityLaw() {
      Natural<MaybeKind.Witness, MaybeKind.Witness> id = Natural.identity();
      Natural<MaybeKind.Witness, ListKind.Witness> composed = id.andThen(MAYBE_TO_LIST);

      Kind<MaybeKind.Witness, String> input = MAYBE.just("hello");

      Kind<ListKind.Witness, String> directResult = MAYBE_TO_LIST.apply(input);
      Kind<ListKind.Witness, String> composedResult = composed.apply(input);

      assertThat(LIST.narrow(directResult)).isEqualTo(LIST.narrow(composedResult));
    }

    @Test
    @DisplayName("right identity: f.andThen(identity) == f")
    void rightIdentityLaw() {
      Natural<ListKind.Witness, ListKind.Witness> id = Natural.identity();
      Natural<MaybeKind.Witness, ListKind.Witness> composed = MAYBE_TO_LIST.andThen(id);

      Kind<MaybeKind.Witness, String> input = MAYBE.just("hello");

      Kind<ListKind.Witness, String> directResult = MAYBE_TO_LIST.apply(input);
      Kind<ListKind.Witness, String> composedResult = composed.apply(input);

      assertThat(LIST.narrow(directResult)).isEqualTo(LIST.narrow(composedResult));
    }

    @Test
    @DisplayName("compose and andThen are inverse: f.andThen(g) == g.compose(f)")
    void composeAndThenInverse() {
      Natural<MaybeKind.Witness, MaybeKind.Witness> viaAndThen =
          MAYBE_TO_LIST.andThen(LIST_HEAD_MAYBE);
      Natural<MaybeKind.Witness, MaybeKind.Witness> viaCompose =
          LIST_HEAD_MAYBE.compose(MAYBE_TO_LIST);

      Kind<MaybeKind.Witness, Integer> input = MAYBE.just(42);

      Kind<MaybeKind.Witness, Integer> andThenResult = viaAndThen.apply(input);
      Kind<MaybeKind.Witness, Integer> composeResult = viaCompose.apply(input);

      assertThat(MAYBE.narrow(andThenResult).get()).isEqualTo(MAYBE.narrow(composeResult).get());
    }
  }

  // ============================================================================
  // Lambda Syntax Tests
  // ============================================================================

  @Nested
  @DisplayName("Anonymous Inner Class Syntax")
  class AnonymousInnerClassSyntaxTests {

    @Test
    @DisplayName("Natural can be created with anonymous inner class")
    void canBeCreatedWithAnonymousInnerClass() {
      // Anonymous inner class definition (required for generic method)
      Natural<MaybeKind.Witness, ListKind.Witness> customNat =
          new Natural<>() {
            @Override
            public <A> Kind<ListKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MaybeKindHelper.MAYBE.narrow(fa);
              return ListKindHelper.LIST.widen(maybe.isJust() ? List.of(maybe.get()) : List.of());
            }
          };

      Kind<MaybeKind.Witness, Integer> input = MAYBE.just(100);
      Kind<ListKind.Witness, Integer> result = customNat.apply(input);

      assertThat(LIST.narrow(result)).containsExactly(100);
    }

    @Test
    @DisplayName("identity can compose with custom transformation")
    void identityComposesWithCustomTransformation() {
      Natural<ListKind.Witness, ListKind.Witness> doubleElements =
          new Natural<>() {
            @Override
            public <A> Kind<ListKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
              // Note: This is a simplified example that only works for Integer
              // In practice, natural transformations should be polymorphic
              @SuppressWarnings("unchecked")
              List<Integer> list = (List<Integer>) ListKindHelper.LIST.narrow(fa);
              @SuppressWarnings("unchecked")
              Kind<ListKind.Witness, A> result =
                  (Kind<ListKind.Witness, A>)
                      ListKindHelper.LIST.widen(list.stream().map(x -> x * 2).toList());
              return result;
            }
          };

      Natural<ListKind.Witness, ListKind.Witness> composed =
          Natural.<ListKind.Witness>identity().andThen(doubleElements);

      Kind<ListKind.Witness, Integer> input = LIST.widen(List.of(1, 2, 3));
      Kind<ListKind.Witness, Integer> result = composed.apply(input);

      assertThat(LIST.narrow(result)).containsExactly(2, 4, 6);
    }
  }

  // ============================================================================
  // Edge Cases
  // ============================================================================

  @Nested
  @DisplayName("Edge Cases")
  class EdgeCaseTests {

    @Test
    @DisplayName("transformation works with single element list")
    void transformationWorksWithSingleElementList() {
      Kind<ListKind.Witness, String> singleList = LIST.widen(List.of("only"));
      Kind<MaybeKind.Witness, String> result = LIST_HEAD_MAYBE.apply(singleList);

      assertThat(MAYBE.narrow(result).get()).isEqualTo("only");
    }

    @Test
    @DisplayName("chained transformations preserve type safety")
    void chainedTransformationsPreserveTypeSafety() {
      // Chain multiple transformations
      Natural<MaybeKind.Witness, MaybeKind.Witness> chain =
          MAYBE_TO_LIST
              .andThen(LIST_REVERSE)
              .andThen(LIST_HEAD_MAYBE)
              .andThen(MAYBE_TO_LIST)
              .andThen(LIST_HEAD_MAYBE);

      Kind<MaybeKind.Witness, String> input = MAYBE.just("preserved");
      Kind<MaybeKind.Witness, String> result = chain.apply(input);

      assertThat(MAYBE.narrow(result).get()).isEqualTo("preserved");
    }
  }
}
