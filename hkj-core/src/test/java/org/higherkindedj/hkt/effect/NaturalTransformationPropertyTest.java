// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;

/**
 * Property-based tests for NaturalTransformation using jQwik.
 *
 * <p>Verifies that natural transformations satisfy the naturality law and composition laws across a
 * wide range of inputs.
 *
 * <h2>Naturality Law</h2>
 *
 * <p>For any natural transformation η: F ~> G and any function f: A -> B:
 *
 * <pre>
 *   η_B ∘ F.map(f) = G.map(f) ∘ η_A
 * </pre>
 *
 * <p>In code: {@code transform(fa.map(f)) == transform(fa).map(f)}
 *
 * <h2>Composition Laws</h2>
 *
 * <ul>
 *   <li>Identity left: {@code identity.andThen(f) == f}
 *   <li>Identity right: {@code f.andThen(identity) == f}
 *   <li>Associativity: {@code (f.andThen(g)).andThen(h) == f.andThen(g.andThen(h))}
 * </ul>
 */
class NaturalTransformationPropertyTest {

  // Monad instances for testing
  private static final IdMonad ID_MONAD = IdMonad.instance();
  private static final MaybeMonad MAYBE_MONAD = MaybeMonad.INSTANCE;
  private static final OptionalMonad OPTIONAL_MONAD = OptionalMonad.INSTANCE;

  // ===== Natural Transformations =====

  /** Transforms Id to Maybe (always succeeds) */
  private static final NaturalTransformation<IdKind.Witness, MaybeKind.Witness> ID_TO_MAYBE =
      new NaturalTransformation<>() {
        @Override
        public <A> Kind<MaybeKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          Id<A> id = ID.narrow(fa);
          return MAYBE.widen(Maybe.fromNullable(id.value()));
        }
      };

  /** Transforms Maybe to Id (uses null for Nothing) */
  private static final NaturalTransformation<MaybeKind.Witness, IdKind.Witness> MAYBE_TO_ID =
      new NaturalTransformation<>() {
        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
          Maybe<A> maybe = MAYBE.narrow(fa);
          return ID.widen(Id.of(maybe.isJust() ? maybe.get() : null));
        }
      };

  /** Transforms Maybe to Optional */
  private static final NaturalTransformation<MaybeKind.Witness, OptionalKind.Witness>
      MAYBE_TO_OPTIONAL =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<OptionalKind.Witness, A> apply(Kind<MaybeKind.Witness, A> fa) {
              Maybe<A> maybe = MAYBE.narrow(fa);
              return OPTIONAL.widen(maybe.isJust() ? Optional.of(maybe.get()) : Optional.empty());
            }
          };

  /** Transforms Optional to Maybe */
  private static final NaturalTransformation<OptionalKind.Witness, MaybeKind.Witness>
      OPTIONAL_TO_MAYBE =
          new NaturalTransformation<>() {
            @Override
            public <A> Kind<MaybeKind.Witness, A> apply(Kind<OptionalKind.Witness, A> fa) {
              Optional<A> opt = OPTIONAL.narrow(fa);
              return MAYBE.widen(opt.map(Maybe::just).orElse(Maybe.nothing()));
            }
          };

  // ===== Arbitrary Providers =====

  @Provide
  Arbitrary<Kind<IdKind.Witness, Integer>> idKinds() {
    return Arbitraries.integers().between(-1000, 1000).map(i -> ID.widen(Id.of(i)));
  }

  @Provide
  Arbitrary<Kind<MaybeKind.Witness, Integer>> maybeKinds() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .injectNull(0.2)
        .map(i -> MAYBE.widen(Maybe.fromNullable(i)));
  }

  @Provide
  Arbitrary<Kind<MaybeKind.Witness, Integer>> justKinds() {
    // Only Just values - for transformations to Id which doesn't have an "empty" concept
    return Arbitraries.integers().between(-1000, 1000).map(i -> MAYBE.widen(Maybe.just(i)));
  }

  @Provide
  Arbitrary<Kind<OptionalKind.Witness, Integer>> optionalKinds() {
    return Arbitraries.integers()
        .between(-1000, 1000)
        .injectNull(0.2)
        .map(i -> OPTIONAL.widen(Optional.ofNullable(i)));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToStringFunctions() {
    // Functions must be null-safe since Id can hold null (from Maybe.Nothing -> Id transformation)
    return Arbitraries.of(
        i -> "value:" + i,
        i -> i == null ? "null" : String.valueOf(i * 2),
        i -> i == null ? "n-null" : "n" + Math.abs(i),
        i -> i == null ? "unknown" : (i >= 0 ? "pos" : "neg"));
  }

  @Provide
  Arbitrary<Function<Integer, Integer>> intToIntFunctions() {
    // Functions must be null-safe since Id can hold null
    return Arbitraries.of(
        i -> i == null ? null : i + 1,
        i -> i == null ? null : i * 2,
        i -> i == null ? null : Math.abs(i),
        i -> i == null ? null : -i,
        i -> i == null ? null : i * i);
  }

  // ===== Naturality Law Tests =====

  @Property
  @Label("Naturality Law: Id->Maybe transformation commutes with map")
  void naturalityLawIdToMaybe(
      @ForAll("idKinds") Kind<IdKind.Witness, Integer> fa,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    // Left side: transform then map in target functor
    Kind<MaybeKind.Witness, String> leftSide = MAYBE_MONAD.map(f, ID_TO_MAYBE.apply(fa));

    // Right side: map in source functor then transform
    Kind<MaybeKind.Witness, String> rightSide = ID_TO_MAYBE.apply(ID_MONAD.map(f, fa));

    assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
  }

  @Property
  @Label("Naturality Law: Maybe->Id transformation commutes with map (Just values only)")
  void naturalityLawMaybeToId(
      @ForAll("justKinds") Kind<MaybeKind.Witness, Integer> fa,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {
    // Note: This test uses only Just values because Maybe->Id is not a valid natural
    // transformation for Nothing. Id doesn't have an "empty" state, so:
    // - Maybe.map(f) on Nothing returns Nothing (doesn't apply f)
    // - Id.map(f) on Id(null) returns Id(f(null)) (does apply f)
    // The naturality square only commutes for non-empty values.

    // Left side: transform then map in target functor
    Kind<IdKind.Witness, String> leftSide = ID_MONAD.map(f, MAYBE_TO_ID.apply(fa));

    // Right side: map in source functor then transform
    Kind<IdKind.Witness, String> rightSide = MAYBE_TO_ID.apply(MAYBE_MONAD.map(f, fa));

    assertThat(ID.narrow(leftSide)).isEqualTo(ID.narrow(rightSide));
  }

  @Property
  @Label("Naturality Law: Maybe->Optional transformation commutes with map")
  void naturalityLawMaybeToOptional(
      @ForAll("maybeKinds") Kind<MaybeKind.Witness, Integer> fa,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    // Left side: transform then map in target functor
    Kind<OptionalKind.Witness, String> leftSide =
        OPTIONAL_MONAD.map(f, MAYBE_TO_OPTIONAL.apply(fa));

    // Right side: map in source functor then transform
    Kind<OptionalKind.Witness, String> rightSide = MAYBE_TO_OPTIONAL.apply(MAYBE_MONAD.map(f, fa));

    assertThat(OPTIONAL.narrow(leftSide)).isEqualTo(OPTIONAL.narrow(rightSide));
  }

  @Property
  @Label("Naturality Law: Optional->Maybe transformation commutes with map")
  void naturalityLawOptionalToMaybe(
      @ForAll("optionalKinds") Kind<OptionalKind.Witness, Integer> fa,
      @ForAll("intToStringFunctions") Function<Integer, String> f) {

    // Left side: transform then map in target functor
    Kind<MaybeKind.Witness, String> leftSide = MAYBE_MONAD.map(f, OPTIONAL_TO_MAYBE.apply(fa));

    // Right side: map in source functor then transform
    Kind<MaybeKind.Witness, String> rightSide = OPTIONAL_TO_MAYBE.apply(OPTIONAL_MONAD.map(f, fa));

    assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
  }

  // ===== Identity Law Tests =====

  @Property
  @Label("Identity Left Law: identity.andThen(f) == f")
  void identityLeftLaw(@ForAll("idKinds") Kind<IdKind.Witness, Integer> fa) {
    NaturalTransformation<IdKind.Witness, IdKind.Witness> identity =
        NaturalTransformation.identity();

    NaturalTransformation<IdKind.Witness, MaybeKind.Witness> composed =
        identity.andThen(ID_TO_MAYBE);

    Kind<MaybeKind.Witness, Integer> leftSide = composed.apply(fa);
    Kind<MaybeKind.Witness, Integer> rightSide = ID_TO_MAYBE.apply(fa);

    assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
  }

  @Property
  @Label("Identity Right Law: f.andThen(identity) == f")
  void identityRightLaw(@ForAll("idKinds") Kind<IdKind.Witness, Integer> fa) {
    NaturalTransformation<MaybeKind.Witness, MaybeKind.Witness> identity =
        NaturalTransformation.identity();

    NaturalTransformation<IdKind.Witness, MaybeKind.Witness> composed =
        ID_TO_MAYBE.andThen(identity);

    Kind<MaybeKind.Witness, Integer> leftSide = composed.apply(fa);
    Kind<MaybeKind.Witness, Integer> rightSide = ID_TO_MAYBE.apply(fa);

    assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
  }

  // ===== Composition Associativity Tests =====

  @Property
  @Label("Composition Associativity: (f.andThen(g)).andThen(h) == f.andThen(g.andThen(h))")
  void compositionAssociativity(@ForAll("idKinds") Kind<IdKind.Witness, Integer> fa) {
    // f: Id -> Maybe
    // g: Maybe -> Optional
    // h: Optional -> Maybe

    // Left side: (f.andThen(g)).andThen(h)
    NaturalTransformation<IdKind.Witness, MaybeKind.Witness> left =
        ID_TO_MAYBE.andThen(MAYBE_TO_OPTIONAL).andThen(OPTIONAL_TO_MAYBE);

    // Right side: f.andThen(g.andThen(h))
    NaturalTransformation<IdKind.Witness, MaybeKind.Witness> right =
        ID_TO_MAYBE.andThen(MAYBE_TO_OPTIONAL.andThen(OPTIONAL_TO_MAYBE));

    Kind<MaybeKind.Witness, Integer> leftResult = left.apply(fa);
    Kind<MaybeKind.Witness, Integer> rightResult = right.apply(fa);

    assertThat(MAYBE.narrow(leftResult)).isEqualTo(MAYBE.narrow(rightResult));
  }

  // ===== Round-trip Tests =====

  @Property
  @Label("Round-trip Id->Maybe->Id preserves non-null values")
  void roundTripIdMaybeId(@ForAll @IntRange(min = -1000, max = 1000) int value) {
    Kind<IdKind.Witness, Integer> original = ID.widen(Id.of(value));

    NaturalTransformation<IdKind.Witness, IdKind.Witness> roundTrip =
        ID_TO_MAYBE.andThen(MAYBE_TO_ID);

    Kind<IdKind.Witness, Integer> result = roundTrip.apply(original);

    assertThat(ID.narrow(result).value()).isEqualTo(value);
  }

  @Property
  @Label("Round-trip Maybe->Optional->Maybe preserves structure")
  void roundTripMaybeOptionalMaybe(
      @ForAll("maybeKinds") Kind<MaybeKind.Witness, Integer> original) {

    NaturalTransformation<MaybeKind.Witness, MaybeKind.Witness> roundTrip =
        MAYBE_TO_OPTIONAL.andThen(OPTIONAL_TO_MAYBE);

    Kind<MaybeKind.Witness, Integer> result = roundTrip.apply(original);

    assertThat(MAYBE.narrow(result)).isEqualTo(MAYBE.narrow(original));
  }

  // ===== Naturality with Multiple Map Operations =====

  @Property
  @Label("Naturality holds for composed functions")
  void naturalityWithComposedFunctions(
      @ForAll("idKinds") Kind<IdKind.Witness, Integer> fa,
      @ForAll("intToIntFunctions") Function<Integer, Integer> f,
      @ForAll("intToIntFunctions") Function<Integer, Integer> g) {

    Function<Integer, Integer> composed = f.andThen(g);

    // Left side: transform then map composed function
    Kind<MaybeKind.Witness, Integer> leftSide = MAYBE_MONAD.map(composed, ID_TO_MAYBE.apply(fa));

    // Right side: map composed function then transform
    Kind<MaybeKind.Witness, Integer> rightSide = ID_TO_MAYBE.apply(ID_MONAD.map(composed, fa));

    assertThat(MAYBE.narrow(leftSide)).isEqualTo(MAYBE.narrow(rightSide));
  }

  // ===== Edge Cases =====

  @Property
  @Label("Transformation handles empty/Nothing values correctly")
  void transformationHandlesEmptyValues() {
    Kind<MaybeKind.Witness, Integer> nothing = MAYBE.widen(Maybe.nothing());

    // Maybe -> Optional preserves empty
    Kind<OptionalKind.Witness, Integer> optResult = MAYBE_TO_OPTIONAL.apply(nothing);
    assertThat(OPTIONAL.narrow(optResult)).isEmpty();

    // Maybe -> Id converts Nothing to null
    Kind<IdKind.Witness, Integer> idResult = MAYBE_TO_ID.apply(nothing);
    assertThat(ID.narrow(idResult).value()).isNull();

    // Optional -> Maybe preserves empty
    Kind<OptionalKind.Witness, Integer> emptyOpt = OPTIONAL.widen(Optional.empty());
    Kind<MaybeKind.Witness, Integer> maybeResult = OPTIONAL_TO_MAYBE.apply(emptyOpt);
    assertThat(MAYBE.narrow(maybeResult).isNothing()).isTrue();
  }
}
