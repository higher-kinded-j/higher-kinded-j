// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.laws.BifunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Test suite for {@link ValidatedBifunctor}. */
@DisplayName("ValidatedBifunctor")
class ValidatedBifunctorTest {

  private ValidatedBifunctor bifunctor;

  private final BiPredicate<
          Kind2<ValidatedKind2.Witness, ?, ?>, Kind2<ValidatedKind2.Witness, ?, ?>>
      equalityChecker = (k1, k2) -> VALIDATED.narrow2(k1).equals(VALIDATED.narrow2(k2));

  @BeforeEach
  void setUp() {
    bifunctor = ValidatedBifunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("kind2Fixtures")
    void identity(String label, Kind2<ValidatedKind2.Witness, String, Integer> fab) {
      BifunctorLaws.assertIdentity(bifunctor, fab, equalityChecker);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("kind2Fixtures")
    void composition(String label, Kind2<ValidatedKind2.Witness, String, Integer> fab) {
      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";
      BifunctorLaws.assertComposition(bifunctor, fab, f1, f2, g1, g2, equalityChecker);
    }

    @ParameterizedTest(name = "first-consistency holds on {0}")
    @MethodSource("kind2Fixtures")
    void firstConsistency(String label, Kind2<ValidatedKind2.Witness, String, Integer> fab) {
      Function<String, String> f = s -> s + "!";
      BifunctorLaws.assertFirstConsistency(bifunctor, fab, f, equalityChecker);
    }

    @ParameterizedTest(name = "second-consistency holds on {0}")
    @MethodSource("kind2Fixtures")
    void secondConsistency(String label, Kind2<ValidatedKind2.Witness, String, Integer> fab) {
      Function<Integer, String> g = n -> "Value:" + n;
      BifunctorLaws.assertSecondConsistency(bifunctor, fab, g, equalityChecker);
    }

    /** Both inhabitants, so {@code bimap}/{@code first}/{@code second} exercise each channel. */
    static Stream<Arguments> kind2Fixtures() {
      return Stream.of(
          Arguments.of(
              "Invalid(\"error\")", VALIDATED.widen2(Validated.<String, Integer>invalid("error"))),
          Arguments.of("Valid(42)", VALIDATED.widen2(Validated.<String, Integer>valid(42))));
    }
  }

  @Test
  @DisplayName("Bifunctor contract — operations, validations & exceptions (laws verified above)")
  void bifunctorContract() {
    Kind2<ValidatedKind2.Witness, String, Integer> invalid =
        VALIDATED.widen2(Validated.invalid("error"));
    Kind2<ValidatedKind2.Witness, String, Integer> valid = VALIDATED.widen2(Validated.valid(42));

    TypeClassContract.<ValidatedKind2.Witness>bifunctor(ValidatedBifunctor.class)
        .<String, Integer>instance(bifunctor)
        .withKind2(valid)
        .withFirstMapper(String::length)
        .withSecondMapper(n -> "Value:" + n)
        .withFirstExceptionKind(invalid)
        .withSecondExceptionKind(valid)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Operations")
  class Operations {

    @Test
    @DisplayName("bimap() transforms both Invalid and Valid")
    void bimapTransformsBothChannels() {
      Kind2<ValidatedKind2.Witness, String, Integer> invalid =
          VALIDATED.widen2(Validated.invalid("error"));
      Kind2<ValidatedKind2.Witness, String, Integer> valid = VALIDATED.widen2(Validated.valid(42));

      Function<String, Integer> errorMapper = String::length;
      Function<Integer, String> valueMapper = n -> "Value:" + n;

      Validated<Integer, String> transformedInvalid =
          VALIDATED.narrow2(bifunctor.bimap(errorMapper, valueMapper, invalid));
      Validated<Integer, String> transformedValid =
          VALIDATED.narrow2(bifunctor.bimap(errorMapper, valueMapper, valid));

      assertThat(transformedInvalid).isEqualTo(Validated.invalid(5)); // "error".length() == 5
      assertThat(transformedValid).isEqualTo(Validated.valid("Value:42"));
    }

    @Test
    @DisplayName("first() transforms only the Invalid channel")
    void firstTransformsOnlyErrorChannel() {
      Kind2<ValidatedKind2.Witness, String, Integer> invalid =
          VALIDATED.widen2(Validated.invalid("error"));
      Kind2<ValidatedKind2.Witness, String, Integer> valid = VALIDATED.widen2(Validated.valid(42));

      Function<String, Integer> errorMapper = String::length;

      assertThat(VALIDATED.narrow2(bifunctor.first(errorMapper, invalid)))
          .isEqualTo(Validated.invalid(5));
      assertThat(VALIDATED.narrow2(bifunctor.first(errorMapper, valid)))
          .isEqualTo(Validated.valid(42)); // Unchanged
    }

    @Test
    @DisplayName("second() transforms only the Valid channel")
    void secondTransformsOnlyValueChannel() {
      Kind2<ValidatedKind2.Witness, String, Integer> invalid =
          VALIDATED.widen2(Validated.invalid("error"));
      Kind2<ValidatedKind2.Witness, String, Integer> valid = VALIDATED.widen2(Validated.valid(42));

      Function<Integer, String> valueMapper = n -> "Value:" + n;

      assertThat(VALIDATED.narrow2(bifunctor.second(valueMapper, invalid)))
          .isEqualTo(Validated.invalid("error")); // Unchanged
      assertThat(VALIDATED.narrow2(bifunctor.second(valueMapper, valid)))
          .isEqualTo(Validated.valid("Value:42"));
    }
  }
}
