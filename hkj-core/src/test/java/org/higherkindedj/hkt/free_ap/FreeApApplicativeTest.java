// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite for {@link FreeApApplicative} using the TypeClassTest API.
 *
 * <p>Tests verify that FreeApApplicative satisfies all Applicative laws and operations.
 */
@DisplayName("FreeApApplicative Tests")
class FreeApApplicativeTest {

  private FreeApApplicative<MaybeKind.Witness> applicative;
  private Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> validKind;
  private Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> validKind2;
  private Function<Integer, String> mapper;
  private Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<Integer, String>> functionKind;
  private BiFunction<Integer, Integer, String> combiningFunction;

  private static final Applicative<MaybeKind.Witness> MAYBE_APPLICATIVE = MaybeMonad.INSTANCE;
  private static final Natural<MaybeKind.Witness, MaybeKind.Witness> IDENTITY_NAT =
      Natural.identity();

  @BeforeEach
  void setUp() {
    applicative = new FreeApApplicative<>();
    validKind = FREE_AP.widen(FreeAp.lift(MAYBE.just(42)));
    validKind2 = FREE_AP.widen(FreeAp.lift(MAYBE.just(10)));
    mapper = x -> "value:" + x;
    functionKind = FREE_AP.widen(FreeAp.pure(mapper));
    combiningFunction = (a, b) -> a + "+" + b;
  }

  // Helper to interpret and compare FreeAp values
  private <A> A interpret(Kind<FreeApKind.Witness<MaybeKind.Witness>, A> kind) {
    FreeAp<MaybeKind.Witness, A> freeAp = FREE_AP.narrow(kind);
    Kind<MaybeKind.Witness, A> result = freeAp.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE);
    return MAYBE.narrow(result).get();
  }

  @Nested
  @DisplayName("Basic Operations")
  class BasicOperationsTests {

    @Test
    @DisplayName("of creates pure FreeAp")
    void ofCreatesPureFreeAp() {
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> result = applicative.of(42);

      assertThat(interpret(result)).isEqualTo(42);
    }

    @Test
    @DisplayName("map transforms value correctly")
    void mapTransformsValue() {
      Kind<FreeApKind.Witness<MaybeKind.Witness>, String> result =
          applicative.map(mapper, validKind);

      assertThat(interpret(result)).isEqualTo("value:42");
    }

    @Test
    @DisplayName("ap applies wrapped function")
    void apAppliesFunction() {
      Kind<FreeApKind.Witness<MaybeKind.Witness>, String> result =
          applicative.ap(functionKind, validKind);

      assertThat(interpret(result)).isEqualTo("value:42");
    }

    @Test
    @DisplayName("map2 combines two values")
    void map2CombinesTwoValues() {
      Kind<FreeApKind.Witness<MaybeKind.Witness>, String> result =
          applicative.map2(validKind, validKind2, combiningFunction);

      assertThat(interpret(result)).isEqualTo("42+10");
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLawsTests {

    private BiPredicate<
            Kind<FreeApKind.Witness<MaybeKind.Witness>, ?>,
            Kind<FreeApKind.Witness<MaybeKind.Witness>, ?>>
        equalityChecker;

    @BeforeEach
    void setUpEquality() {
      equalityChecker =
          (k1, k2) -> {
            FreeAp<MaybeKind.Witness, ?> f1 = FREE_AP.narrow(k1);
            FreeAp<MaybeKind.Witness, ?> f2 = FREE_AP.narrow(k2);

            var r1 = MAYBE.narrow(f1.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE));
            var r2 = MAYBE.narrow(f2.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE));

            if (r1.isNothing() && r2.isNothing()) {
              return true;
            }
            if (r1.isJust() && r2.isJust()) {
              return Objects.equals(r1.get(), r2.get());
            }
            return false;
          };
    }

    @Test
    @DisplayName("Identity law: pure(id).ap(fa) == fa")
    void identityLaw() {
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<Integer, Integer>> pureId =
          applicative.of(Function.identity());

      Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> result =
          applicative.ap(pureId, validKind);

      assertThat(equalityChecker.test(validKind, result)).isTrue();
    }

    @Test
    @DisplayName("Homomorphism law: pure(f).ap(pure(x)) == pure(f(x))")
    void homomorphismLaw() {
      Function<Integer, String> f = Object::toString;
      Integer x = 42;

      Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<Integer, String>> pureF =
          applicative.of(f);
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> pureX = applicative.of(x);

      Kind<FreeApKind.Witness<MaybeKind.Witness>, String> left = applicative.ap(pureF, pureX);
      Kind<FreeApKind.Witness<MaybeKind.Witness>, String> right = applicative.of(f.apply(x));

      assertThat(equalityChecker.test(left, right)).isTrue();
    }

    @Test
    @DisplayName("Interchange law: ff.ap(pure(x)) == pure(f -> f(x)).ap(ff)")
    void interchangeLaw() {
      Integer x = 42;
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<Integer, String>> ff =
          applicative.of(Object::toString);

      Kind<FreeApKind.Witness<MaybeKind.Witness>, String> left =
          applicative.ap(ff, applicative.of(x));

      Function<Function<Integer, String>, String> applyToX = f -> f.apply(x);
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<Function<Integer, String>, String>>
          pureApply = applicative.of(applyToX);
      Kind<FreeApKind.Witness<MaybeKind.Witness>, String> right = applicative.ap(pureApply, ff);

      assertThat(equalityChecker.test(left, right)).isTrue();
    }
  }

  @Nested
  @DisplayName("TypeClassTest Integration")
  class TypeClassTestIntegration {

    @Test
    @DisplayName("Passes Applicative operations test")
    void passesApplicativeOperationsTest() {
      TypeClassTest.<FreeApKind.Witness<MaybeKind.Witness>>applicative(FreeApApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, mapper, functionKind, combiningFunction)
          .testOperations();
    }

    @Test
    @DisplayName("Passes Applicative validations test")
    void passesApplicativeValidationsTest() {
      TypeClassTest.<FreeApKind.Witness<MaybeKind.Witness>>applicative(FreeApApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, mapper, functionKind, combiningFunction)
          .testValidations();
    }

    @Test
    @DisplayName("Passes Applicative laws test")
    void passesApplicativeLawsTest() {
      BiPredicate<
              Kind<FreeApKind.Witness<MaybeKind.Witness>, ?>,
              Kind<FreeApKind.Witness<MaybeKind.Witness>, ?>>
          eqChecker =
              (k1, k2) -> {
                FreeAp<MaybeKind.Witness, ?> f1 = FREE_AP.narrow(k1);
                FreeAp<MaybeKind.Witness, ?> f2 = FREE_AP.narrow(k2);

                var r1 = MAYBE.narrow(f1.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE));
                var r2 = MAYBE.narrow(f2.foldMap(IDENTITY_NAT, MAYBE_APPLICATIVE));

                if (r1.isNothing() && r2.isNothing()) {
                  return true;
                }
                if (r1.isJust() && r2.isJust()) {
                  return Objects.equals(r1.get(), r2.get());
                }
                return false;
              };

      Function<String, String> secondMapper = s -> s + "!";

      TypeClassTest.<FreeApKind.Witness<MaybeKind.Witness>>applicative(FreeApApplicative.class)
          .<Integer>instance(applicative)
          .<String>withKind(validKind)
          .withOperations(validKind2, mapper, functionKind, combiningFunction)
          .withLawsTesting(42, mapper, eqChecker)
          .testLaws();
    }
  }
}
