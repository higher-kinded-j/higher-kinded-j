// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either_t;

import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.optional.OptionalKind;

/**
 * Property-based Functor- and Monad-law verification for EitherT over Optional. Mirrors
 * EitherTMonadTest's law coverage but with jqwik-generated fixtures spanning the three transformer
 * states (Right, Left, empty-outer).
 */
class EitherTMonadPropertyTest {

  record TestError(String code) {}

  private final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());
  private final MonadError<EitherTKind.Witness<OptionalKind.Witness, TestError>, TestError>
      eitherTMonad = Instances.eitherT(outerMonad);

  private <A> Optional<Either<TestError, A>> unwrapKind(
      Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, A> kind) {
    if (kind == null) return null;
    var eitherT = EITHER_T.narrow(kind);
    Kind<OptionalKind.Witness, Either<TestError, A>> outerKind = eitherT.value();
    return OPTIONAL.narrow(outerKind);
  }

  private final BiPredicate<
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, ?>,
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, ?>>
      eq = KindEquivalence.byEqualsAfter(this::unwrapKind);

  private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> rightT(R value) {
    return EITHER_T.widen(EitherT.right(outerMonad, value));
  }

  private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> leftT(String code) {
    return EITHER_T.widen(EitherT.left(outerMonad, new TestError(code)));
  }

  private <R> Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, R> emptyT() {
    Kind<OptionalKind.Witness, Either<TestError, R>> emptyOuter = OPTIONAL.widen(Optional.empty());
    return EITHER_T.widen(EitherT.fromKind(emptyOuter));
  }

  @Provide
  Arbitrary<Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer>> eitherTKinds() {
    // Mix of Right (success), Left (in-Either error), and empty-outer Optional states.
    return Arbitraries.integers()
        .between(-100, 100)
        .injectNull(0.15)
        .flatMap(
            i -> {
              if (i == null) {
                return Arbitraries.just(this.<Integer>emptyT());
              }
              if (i % 5 == 0) {
                return Arbitraries.of("err-a", "err-b", "err-c").map(this::<Integer>leftT);
              }
              return Arbitraries.just(rightT(i));
            });
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Provide
  Arbitrary<Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>>
      intToTKindString() {
    return Arbitraries.of(
        i -> i % 2 == 0 ? rightT("even:" + i) : leftT("odd-" + i),
        i -> i > 0 ? rightT("positive:" + i) : emptyT(),
        i -> rightT("value:" + i),
        i -> i == 0 ? leftT("zero") : rightT(String.valueOf(i)));
  }

  @Provide
  Arbitrary<Function<String, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>>>
      stringToTKindString() {
    return Arbitraries.of(
        s -> s.isEmpty() ? leftT("empty") : rightT(s.toUpperCase()),
        s -> s.length() > 3 ? rightT("long:" + s) : emptyT(),
        s -> rightT("transformed:" + s));
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("eitherTKinds")
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa) {
    FunctorLaws.assertIdentity(eitherTMonad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("eitherTKinds")
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(eitherTMonad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTKindString")
          Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>> f) {
    MonadLaws.assertLeftIdentity(eitherTMonad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(
      @ForAll("eitherTKinds")
          Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> m) {
    MonadLaws.assertRightIdentity(eitherTMonad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("eitherTKinds") Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, Integer> m,
      @ForAll("intToTKindString")
          Function<Integer, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>> f,
      @ForAll("stringToTKindString")
          Function<String, Kind<EitherTKind.Witness<OptionalKind.Witness, TestError>, String>> g) {
    MonadLaws.assertAssociativity(eitherTMonad, m, f, g, eq);
  }
}
