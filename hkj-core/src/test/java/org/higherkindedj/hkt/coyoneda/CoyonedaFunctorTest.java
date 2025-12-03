// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper.COYONEDA;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.MaybeFunctor;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test suite for {@link CoyonedaFunctor} using the TypeClassTest API.
 *
 * <p>Tests verify that CoyonedaFunctor satisfies all Functor laws and operations.
 */
@DisplayName("CoyonedaFunctor Tests")
class CoyonedaFunctorTest {

  private CoyonedaFunctor<MaybeKind.Witness> functor;
  private Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> validKind;
  private Function<Integer, String> mapper;

  private static final MaybeFunctor MAYBE_FUNCTOR = MaybeFunctor.INSTANCE;

  @BeforeEach
  void setUp() {
    functor = new CoyonedaFunctor<>();
    validKind = COYONEDA.lift(MAYBE.just(42));
    mapper = x -> "value:" + x;
  }

  @Nested
  @DisplayName("Basic Operations")
  class BasicOperationsTests {

    @Test
    @DisplayName("map transforms value correctly")
    void mapTransformsValue() {
      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, String> result = functor.map(mapper, validKind);

      Coyoneda<MaybeKind.Witness, String> coyo = COYONEDA.narrow(result);
      Kind<MaybeKind.Witness, String> lowered = coyo.lower(MAYBE_FUNCTOR);

      assertThat(MAYBE.narrow(lowered).get()).isEqualTo("value:42");
    }

    @Test
    @DisplayName("map preserves Nothing")
    void mapPreservesNothing() {
      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> nothingKind =
          COYONEDA.lift(MAYBE.nothing());

      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, String> result =
          functor.map(Object::toString, nothingKind);

      Coyoneda<MaybeKind.Witness, String> coyo = COYONEDA.narrow(result);
      Kind<MaybeKind.Witness, String> lowered = coyo.lower(MAYBE_FUNCTOR);

      assertThat(MAYBE.narrow(lowered).isNothing()).isTrue();
    }

    @Test
    @DisplayName("multiple maps are fused")
    void multipleMapsAreFused() {
      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> result =
          functor.map(x -> x * 2, functor.map(x -> x + 1, validKind));

      Coyoneda<MaybeKind.Witness, Integer> coyo = COYONEDA.narrow(result);
      Kind<MaybeKind.Witness, Integer> lowered = coyo.lower(MAYBE_FUNCTOR);

      // (42 + 1) * 2 = 86
      assertThat(MAYBE.narrow(lowered).get()).isEqualTo(86);
    }
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLawsTests {

    private BiPredicate<
            Kind<CoyonedaKind.Witness<MaybeKind.Witness>, ?>,
            Kind<CoyonedaKind.Witness<MaybeKind.Witness>, ?>>
        equalityChecker;

    @BeforeEach
    void setUpEquality() {
      equalityChecker =
          (k1, k2) -> {
            Coyoneda<MaybeKind.Witness, ?> c1 = COYONEDA.narrow(k1);
            Coyoneda<MaybeKind.Witness, ?> c2 = COYONEDA.narrow(k2);

            var r1 = MAYBE.narrow(c1.lower(MAYBE_FUNCTOR));
            var r2 = MAYBE.narrow(c2.lower(MAYBE_FUNCTOR));

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
    @DisplayName("Identity law: map(id, fa) == fa")
    void identityLaw() {
      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, Integer> mapped =
          functor.map(Function.identity(), validKind);

      assertThat(equalityChecker.test(validKind, mapped)).isTrue();
    }

    @Test
    @DisplayName("Composition law: map(g, map(f, fa)) == map(g.compose(f), fa)")
    void compositionLaw() {
      Function<Integer, Integer> f = x -> x + 1;
      Function<Integer, String> g = Object::toString;

      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, String> chained =
          functor.map(g, functor.map(f, validKind));

      Kind<CoyonedaKind.Witness<MaybeKind.Witness>, String> composed =
          functor.map(f.andThen(g), validKind);

      assertThat(equalityChecker.test(chained, composed)).isTrue();
    }
  }

  @Nested
  @DisplayName("TypeClassTest Integration")
  class TypeClassTestIntegration {

    @Test
    @DisplayName("Passes Functor operations test")
    void passesFunctorOperationsTest() {
      TypeClassTest.<CoyonedaKind.Witness<MaybeKind.Witness>>functor(CoyonedaFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(mapper)
          .testOperations();
    }

    @Test
    @DisplayName("Passes Functor validations test")
    void passesFunctorValidationsTest() {
      TypeClassTest.<CoyonedaKind.Witness<MaybeKind.Witness>>functor(CoyonedaFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(mapper)
          .testValidations();
    }

    @Test
    @DisplayName("Passes Functor laws test")
    void passesFunctorLawsTest() {
      BiPredicate<
              Kind<CoyonedaKind.Witness<MaybeKind.Witness>, ?>,
              Kind<CoyonedaKind.Witness<MaybeKind.Witness>, ?>>
          eqChecker =
              (k1, k2) -> {
                Coyoneda<MaybeKind.Witness, ?> c1 = COYONEDA.narrow(k1);
                Coyoneda<MaybeKind.Witness, ?> c2 = COYONEDA.narrow(k2);

                var r1 = MAYBE.narrow(c1.lower(MAYBE_FUNCTOR));
                var r2 = MAYBE.narrow(c2.lower(MAYBE_FUNCTOR));

                if (r1.isNothing() && r2.isNothing()) {
                  return true;
                }
                if (r1.isJust() && r2.isJust()) {
                  return Objects.equals(r1.get(), r2.get());
                }
                return false;
              };

      Function<String, String> secondMapper = s -> s + "!";

      TypeClassTest.<CoyonedaKind.Witness<MaybeKind.Witness>>functor(CoyonedaFunctor.class)
          .<Integer>instance(functor)
          .<String>withKind(validKind)
          .withMapper(mapper)
          .withSecondMapper(secondMapper)
          .withEqualityChecker(eqChecker)
          .testLaws();
    }
  }
}
