// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for NaturalTransformation.
 *
 * <p>Tests cover transformation application, composition, and identity transformations.
 */
@DisplayName("NaturalTransformation<F, G> Complete Test Suite")
class NaturalTransformationTest {

  // Helper method to create Id -> Maybe transformation
  private static NaturalTransformation<IdKind.Witness, MaybeKind.Witness> idToMaybe() {
    return new NaturalTransformation<>() {
      @Override
      public <A> Kind<MaybeKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
        Id<A> id = IdKindHelper.ID.narrow(fa);
        A value = id.value();
        return MaybeKindHelper.MAYBE.widen(value != null ? Maybe.just(value) : Maybe.nothing());
      }
    };
  }

  // Helper method to create Maybe -> Id transformation
  private static NaturalTransformation<MaybeKind.Witness, IdKind.Witness> maybeToId() {
    return new NaturalTransformation<>() {
      @Override
      public <A> Kind<IdKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
        Maybe<A> maybe = MaybeKindHelper.MAYBE.narrow(fa);
        A value = maybe.isJust() ? maybe.get() : null;
        return IdKindHelper.ID.widen(Id.of(value));
      }
    };
  }

  @Nested
  @DisplayName("Basic Transformation")
  class BasicTransformationTests {

    @Test
    @DisplayName("Can create transformation from Id to Maybe")
    void canCreateIdToMaybeTransformation() {
      NaturalTransformation<IdKind.Witness, MaybeKind.Witness> transform = idToMaybe();

      Kind<IdKind.Witness, String> idKind = IdKindHelper.ID.widen(Id.of("hello"));
      Kind<MaybeKind.Witness, String> result = transform.apply(idKind);

      Maybe<String> maybe = MaybeKindHelper.MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Can create transformation from Maybe to Id")
    void canCreateMaybeToIdTransformation() {
      NaturalTransformation<MaybeKind.Witness, IdKind.Witness> transform = maybeToId();

      Kind<MaybeKind.Witness, String> maybeKind = MaybeKindHelper.MAYBE.widen(Maybe.just("hello"));
      Kind<IdKind.Witness, String> result = transform.apply(maybeKind);

      Id<String> id = IdKindHelper.ID.narrow(result);
      assertThat(id.value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Transformation handles null values correctly")
    void transformationHandlesNullValues() {
      NaturalTransformation<IdKind.Witness, MaybeKind.Witness> transform = idToMaybe();

      Kind<IdKind.Witness, String> idKind = IdKindHelper.ID.widen(Id.of(null));
      Kind<MaybeKind.Witness, String> result = transform.apply(idKind);

      Maybe<String> maybe = MaybeKindHelper.MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Identity Transformation")
  class IdentityTransformationTests {

    @Test
    @DisplayName("identity() returns same Kind unchanged")
    void identityReturnsSameKind() {
      NaturalTransformation<IdKind.Witness, IdKind.Witness> identity =
          NaturalTransformation.identity();

      Kind<IdKind.Witness, String> original = IdKindHelper.ID.widen(Id.of("test"));
      Kind<IdKind.Witness, String> result = identity.apply(original);

      assertThat(result).isSameAs(original);
    }

    @Test
    @DisplayName("identity() preserves value")
    void identityPreservesValue() {
      NaturalTransformation<MaybeKind.Witness, MaybeKind.Witness> identity =
          NaturalTransformation.identity();

      Kind<MaybeKind.Witness, Integer> original = MaybeKindHelper.MAYBE.widen(Maybe.just(42));
      Kind<MaybeKind.Witness, Integer> result = identity.apply(original);

      Maybe<Integer> maybe = MaybeKindHelper.MAYBE.narrow(result);
      assertThat(maybe.get()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Composition")
  class CompositionTests {

    @Test
    @DisplayName("andThen() composes transformations in order")
    void andThenComposesInOrder() {
      // Id -> Maybe -> Id (round trip via Maybe)
      NaturalTransformation<IdKind.Witness, MaybeKind.Witness> toMaybe = idToMaybe();
      NaturalTransformation<MaybeKind.Witness, IdKind.Witness> toId = maybeToId();

      NaturalTransformation<IdKind.Witness, IdKind.Witness> roundTrip = toMaybe.andThen(toId);

      Kind<IdKind.Witness, String> original = IdKindHelper.ID.widen(Id.of("hello"));
      Kind<IdKind.Witness, String> result = roundTrip.apply(original);

      Id<String> id = IdKindHelper.ID.narrow(result);
      assertThat(id.value()).isEqualTo("hello");
    }

    @Test
    @DisplayName("compose() composes transformations in reverse order")
    void composeComposesInReverseOrder() {
      NaturalTransformation<IdKind.Witness, MaybeKind.Witness> toMaybe = idToMaybe();
      NaturalTransformation<MaybeKind.Witness, IdKind.Witness> toId = maybeToId();

      // compose: toId happens first (on Maybe input), then toMaybe
      NaturalTransformation<MaybeKind.Witness, MaybeKind.Witness> composed = toMaybe.compose(toId);

      Kind<MaybeKind.Witness, String> original = MaybeKindHelper.MAYBE.widen(Maybe.just("world"));
      Kind<MaybeKind.Witness, String> result = composed.apply(original);

      Maybe<String> maybe = MaybeKindHelper.MAYBE.narrow(result);
      assertThat(maybe.get()).isEqualTo("world");
    }
  }

  @Nested
  @DisplayName("Natural Transformation Laws")
  class NaturalTransformationLawsTests {

    @Test
    @DisplayName("Identity left: identity.andThen(f) == f")
    void identityLeftLaw() {
      NaturalTransformation<IdKind.Witness, MaybeKind.Witness> f = idToMaybe();

      NaturalTransformation<IdKind.Witness, IdKind.Witness> identity =
          NaturalTransformation.identity();

      NaturalTransformation<IdKind.Witness, MaybeKind.Witness> composed = identity.andThen(f);

      Kind<IdKind.Witness, String> input = IdKindHelper.ID.widen(Id.of("test"));

      Maybe<String> directResult = MaybeKindHelper.MAYBE.narrow(f.apply(input));
      Maybe<String> composedResult = MaybeKindHelper.MAYBE.narrow(composed.apply(input));

      assertThat(composedResult.get()).isEqualTo(directResult.get());
    }

    @Test
    @DisplayName("Identity right: f.andThen(identity) == f")
    void identityRightLaw() {
      NaturalTransformation<IdKind.Witness, MaybeKind.Witness> f = idToMaybe();

      NaturalTransformation<MaybeKind.Witness, MaybeKind.Witness> identity =
          NaturalTransformation.identity();

      NaturalTransformation<IdKind.Witness, MaybeKind.Witness> composed = f.andThen(identity);

      Kind<IdKind.Witness, String> input = IdKindHelper.ID.widen(Id.of("test"));

      Maybe<String> directResult = MaybeKindHelper.MAYBE.narrow(f.apply(input));
      Maybe<String> composedResult = MaybeKindHelper.MAYBE.narrow(composed.apply(input));

      assertThat(composedResult.get()).isEqualTo(directResult.get());
    }

    @Test
    @DisplayName("Associativity: (f.andThen(g)).andThen(h) == f.andThen(g.andThen(h))")
    void associativityLaw() {
      // For this test, we use transformations that modify values to verify composition order
      NaturalTransformation<IdKind.Witness, IdKind.Witness> addPrefix =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
              Id<A> id = IdKindHelper.ID.narrow(fa);
              @SuppressWarnings("unchecked")
              A newValue = (A) ("A" + id.value());
              return IdKindHelper.ID.widen(Id.of(newValue));
            }
          };

      NaturalTransformation<IdKind.Witness, IdKind.Witness> addSuffix =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
              Id<A> id = IdKindHelper.ID.narrow(fa);
              @SuppressWarnings("unchecked")
              A newValue = (A) (id.value() + "B");
              return IdKindHelper.ID.widen(Id.of(newValue));
            }
          };

      NaturalTransformation<IdKind.Witness, IdKind.Witness> wrap =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
              Id<A> id = IdKindHelper.ID.narrow(fa);
              @SuppressWarnings("unchecked")
              A newValue = (A) ("[" + id.value() + "]");
              return IdKindHelper.ID.widen(Id.of(newValue));
            }
          };

      // (f.andThen(g)).andThen(h)
      NaturalTransformation<IdKind.Witness, IdKind.Witness> left =
          addPrefix.andThen(addSuffix).andThen(wrap);

      // f.andThen(g.andThen(h))
      NaturalTransformation<IdKind.Witness, IdKind.Witness> right =
          addPrefix.andThen(addSuffix.andThen(wrap));

      Kind<IdKind.Witness, String> input = IdKindHelper.ID.widen(Id.of("X"));

      Id<String> leftResult = IdKindHelper.ID.narrow(left.apply(input));
      Id<String> rightResult = IdKindHelper.ID.narrow(right.apply(input));

      assertThat(leftResult.value()).isEqualTo(rightResult.value());
      assertThat(leftResult.value()).isEqualTo("[AXB]");
    }
  }

  @Nested
  @DisplayName("Practical Usage Patterns")
  class PracticalUsagePatternsTests {

    @Test
    @DisplayName("Can use transformation to convert between effect types")
    void canConvertBetweenEffectTypes() {
      // Simulate converting from a sync effect (Id) to an async-friendly representation (Maybe)
      NaturalTransformation<IdKind.Witness, MaybeKind.Witness> syncToAsync = idToMaybe();

      Kind<IdKind.Witness, String> syncResult = IdKindHelper.ID.widen(Id.of("data"));
      Kind<MaybeKind.Witness, String> asyncResult = syncToAsync.apply(syncResult);

      Maybe<String> maybe = MaybeKindHelper.MAYBE.narrow(asyncResult);
      assertThat(maybe.isJust()).isTrue();
      assertThat(maybe.get()).isEqualTo("data");
    }
  }
}
