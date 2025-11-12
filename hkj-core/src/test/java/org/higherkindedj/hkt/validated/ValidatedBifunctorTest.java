// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Comprehensive test suite for {@link ValidatedBifunctor}. */
@DisplayName("ValidatedBifunctor Complete Test Suite")
class ValidatedBifunctorTest {

  private ValidatedBifunctor bifunctor;

  @BeforeEach
  void setUp() {
    bifunctor = ValidatedBifunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Bifunctor test pattern")
    void runCompleteBifunctorTestPattern() {
      Kind2<ValidatedKind2.Witness, String, Integer> validValidated =
          VALIDATED.widen2(Validated.valid(42));
      Function<String, Integer> firstMapper = String::length;
      Function<Integer, String> secondMapper = n -> "Value:" + n;
      Function<Integer, String> compositionFirstMapper = i -> "#" + i;
      Function<String, String> compositionSecondMapper = s -> s + "!";
      BiPredicate<Kind2<ValidatedKind2.Witness, ?, ?>, Kind2<ValidatedKind2.Witness, ?, ?>>
          equalityChecker = (k1, k2) -> VALIDATED.narrow2(k1).equals(VALIDATED.narrow2(k2));

      TypeClassTest.<ValidatedKind2.Witness>bifunctor(ValidatedBifunctor.class)
          .<String, Integer>instance(bifunctor)
          .withKind2(validValidated)
          .withFirstMapper(firstMapper)
          .withSecondMapper(secondMapper)
          .withCompositionFirstMapper(compositionFirstMapper)
          .withCompositionSecondMapper(compositionSecondMapper)
          .withEqualityChecker(equalityChecker)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Bifunctor Operation Tests")
  class BifunctorOperationTests {

    @Test
    @DisplayName("bimap() transforms both Invalid and Valid")
    void bimapTransformsBothChannels() {
      Kind2<ValidatedKind2.Witness, String, Integer> invalid =
          VALIDATED.widen2(Validated.invalid("error"));
      Kind2<ValidatedKind2.Witness, String, Integer> valid =
          VALIDATED.widen2(Validated.valid(42));

      Function<String, Integer> errorMapper = String::length;
      Function<Integer, String> valueMapper = n -> "Value:" + n;

      Validated<Integer, String> transformedInvalid =
          VALIDATED.narrow2(bifunctor.bimap(errorMapper, valueMapper, invalid));
      Validated<Integer, String> transformedValid =
          VALIDATED.narrow2(bifunctor.bimap(errorMapper, valueMapper, valid));

      assertThat(transformedInvalid).isEqualTo(Validated.invalid(5));
      assertThat(transformedValid).isEqualTo(Validated.valid("Value:42"));
    }

    @Test
    @DisplayName("first() transforms only Invalid channel")
    void firstTransformsOnlyErrorChannel() {
      Kind2<ValidatedKind2.Witness, String, Integer> invalid =
          VALIDATED.widen2(Validated.invalid("error"));
      Kind2<ValidatedKind2.Witness, String, Integer> valid =
          VALIDATED.widen2(Validated.valid(42));

      Function<String, Integer> errorMapper = String::length;

      Validated<Integer, Integer> transformedInvalid =
          VALIDATED.narrow2(bifunctor.first(errorMapper, invalid));
      Validated<Integer, Integer> transformedValid =
          VALIDATED.narrow2(bifunctor.first(errorMapper, valid));

      assertThat(transformedInvalid).isEqualTo(Validated.invalid(5));
      assertThat(transformedValid).isEqualTo(Validated.valid(42)); // Unchanged
    }

    @Test
    @DisplayName("second() transforms only Valid channel")
    void secondTransformsOnlyValueChannel() {
      Kind2<ValidatedKind2.Witness, String, Integer> invalid =
          VALIDATED.widen2(Validated.invalid("error"));
      Kind2<ValidatedKind2.Witness, String, Integer> valid =
          VALIDATED.widen2(Validated.valid(42));

      Function<Integer, String> valueMapper = n -> "Value:" + n;

      Validated<String, String> transformedInvalid =
          VALIDATED.narrow2(bifunctor.second(valueMapper, invalid));
      Validated<String, String> transformedValid =
          VALIDATED.narrow2(bifunctor.second(valueMapper, valid));

      assertThat(transformedInvalid).isEqualTo(Validated.invalid("error")); // Unchanged
      assertThat(transformedValid).isEqualTo(Validated.valid("Value:42"));
    }
  }

  @Nested
  @DisplayName("Bifunctor Law Tests")
  class BifunctorLawTests {

    private final BiPredicate<
            Kind2<ValidatedKind2.Witness, ?, ?>, Kind2<ValidatedKind2.Witness, ?, ?>>
        equalityChecker = (k1, k2) -> VALIDATED.narrow2(k1).equals(VALIDATED.narrow2(k2));

    @Test
    @DisplayName("Identity Law: bimap(id, id, fab) == fab (Invalid)")
    void identityLawInvalid() {
      Kind2<ValidatedKind2.Witness, String, Integer> invalid =
          VALIDATED.widen2(Validated.invalid("error"));

      Kind2<ValidatedKind2.Witness, String, Integer> result =
          bifunctor.bimap(Function.identity(), Function.identity(), invalid);

      assertThat(equalityChecker.test(result, invalid))
          .as("Identity law should hold for Invalid")
          .isTrue();
    }

    @Test
    @DisplayName("Identity Law: bimap(id, id, fab) == fab (Valid)")
    void identityLawValid() {
      Kind2<ValidatedKind2.Witness, String, Integer> valid =
          VALIDATED.widen2(Validated.valid(42));

      Kind2<ValidatedKind2.Witness, String, Integer> result =
          bifunctor.bimap(Function.identity(), Function.identity(), valid);

      assertThat(equalityChecker.test(result, valid))
          .as("Identity law should hold for Valid")
          .isTrue();
    }

    @Test
    @DisplayName("Composition Law")
    void compositionLaw() {
      Kind2<ValidatedKind2.Witness, String, Integer> valid =
          VALIDATED.widen2(Validated.valid(42));

      Function<String, Integer> f1 = String::length;
      Function<Integer, String> f2 = i -> "#" + i;
      Function<Integer, String> g1 = n -> "Value:" + n;
      Function<String, String> g2 = s -> s + "!";

      // Left side
      Kind2<ValidatedKind2.Witness, String, String> leftSide =
          bifunctor.bimap(s -> f2.apply(f1.apply(s)), i -> g2.apply(g1.apply(i)), valid);

      // Right side
      Kind2<ValidatedKind2.Witness, Integer, String> intermediate =
          bifunctor.bimap(f1, g1, valid);
      Kind2<ValidatedKind2.Witness, String, String> rightSide =
          bifunctor.bimap(f2, g2, intermediate);

      assertThat(equalityChecker.test(leftSide, rightSide))
          .as("Composition law should hold")
          .isTrue();
    }
  }
}
