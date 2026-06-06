// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free_ap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test suite for {@link FreeApApplicative}.
 *
 * <p>Verifies the Applicative operations and laws. {@code FreeAp} is a <em>free</em> applicative:
 * {@code map}/{@code ap}/{@code map2} build up a {@code Pure}/{@code Lift}/{@code Ap} structure and
 * never apply their functions — those run only later under a {@code foldMap}/{@code analyse} pass.
 * Because the functions are never invoked at build time, map/ap cannot throw a function exception,
 * so the contract omits {@link Category#EXCEPTIONS}. The laws are driven by the shipped {@link
 * ApplicativeLaws} over {@link FreeApLawFixtures}, whose {@code EQ} interprets both sides into
 * {@code Maybe}.
 */
@DisplayName("FreeApApplicative Tests")
class FreeApApplicativeTest {

  private FreeApApplicative<MaybeKind.Witness> applicative;
  private Applicative<FreeApKind.Witness<MaybeKind.Witness>> applicativeTyped;
  private Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> validKind;
  private Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> validKind2;
  private Function<Integer, String> mapper;
  private Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<Integer, String>> functionKind;
  private BiFunction<Integer, Integer, String> combiningFunction;

  @BeforeEach
  void setUp() {
    applicative = new FreeApApplicative<>();
    applicativeTyped = applicative;
    validKind = FREE_AP.widen(FreeAp.lift(MAYBE.just(42)));
    validKind2 = FREE_AP.widen(FreeAp.lift(MAYBE.just(10)));
    mapper = x -> "value:" + x;
    functionKind = FREE_AP.widen(FreeAp.pure(mapper));
    combiningFunction = (a, b) -> a + "+" + b;
  }

  // Interpret a FreeAp into Maybe and extract the Just value.
  private <A> A interpret(Kind<FreeApKind.Witness<MaybeKind.Witness>, A> kind) {
    Kind<MaybeKind.Witness, A> result =
        FREE_AP
            .narrow(kind)
            .foldMap(FreeApLawFixtures.IDENTITY_NAT, FreeApLawFixtures.MAYBE_APPLICATIVE);
    return MAYBE.narrow(result).get();
  }

  /**
   * {@code FreeAp} builds structure rather than applying its functions (they run only at {@code
   * foldMap}/{@code analyse} time), so a thrown mapper cannot surface at build time — {@link
   * Category#EXCEPTIONS} is omitted. The Applicative laws are verified in the {@code Laws} block
   * below.
   */
  @Test
  @DisplayName("Applicative contract — operations & validations")
  void applicativeContract() {
    TypeClassContract.<FreeApKind.Witness<MaybeKind.Witness>>applicative(FreeApApplicative.class)
        .<Integer>instance(applicativeTyped)
        .<String>withKind(validKind)
        .withOperations(validKind2, mapper, functionKind, combiningFunction)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
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
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.free_ap.FreeApLawFixtures#kinds")
    void identity(String label, Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> v) {
      ApplicativeLaws.assertIdentity(applicativeTyped, v, FreeApLawFixtures.EQ);
    }

    @ParameterizedTest(name = "homomorphism holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.free_ap.FreeApLawFixtures#values")
    void homomorphism(int value) {
      ApplicativeLaws.assertHomomorphism(applicativeTyped, value, mapper, FreeApLawFixtures.EQ);
    }

    @ParameterizedTest(name = "interchange holds on value {0}")
    @MethodSource("org.higherkindedj.hkt.free_ap.FreeApLawFixtures#values")
    void interchange(int value) {
      ApplicativeLaws.assertInterchange(
          applicativeTyped, functionKind, value, FreeApLawFixtures.EQ);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.free_ap.FreeApLawFixtures#kinds")
    void composition(String label, Kind<FreeApKind.Witness<MaybeKind.Witness>, Integer> w) {
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<String, Integer>> u =
          applicativeTyped.of(String::length);
      Kind<FreeApKind.Witness<MaybeKind.Witness>, Function<Integer, String>> v =
          applicativeTyped.of(Object::toString);
      ApplicativeLaws.assertComposition(applicativeTyped, u, v, w, FreeApLawFixtures.EQ);
    }
  }
}
