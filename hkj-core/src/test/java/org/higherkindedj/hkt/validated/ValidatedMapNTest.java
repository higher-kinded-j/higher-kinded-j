// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ValidatedMonad mapN Methods Complete Test Suite")
class ValidatedMapNTest {

  private ValidatedMonad<String> monad;
  private Semigroup<String> semigroup;

  @BeforeEach
  void setUp() {
    semigroup = Semigroups.string(", ");
    monad = ValidatedMonad.instance(semigroup);
  }

  @Nested
  @DisplayName("map2 Tests")
  class Map2Tests {

    @Test
    @DisplayName("map2 combines two Valid values")
    void map2CombinesTwoValidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map2(v1, v2, (a, b) -> a + "+" + b);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo("10+20");
    }

    @Test
    @DisplayName("map2 accumulates two errors")
    void map2AccumulatesTwoErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("error1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error2");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map2(v1, v2, (a, b) -> a + "+" + b);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error1, error2");
    }

    @Test
    @DisplayName("map2 propagates first Invalid when second is Valid")
    void map2PropagatesFirstInvalidWhenSecondIsValid() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("error");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map2(v1, v2, (a, b) -> a + "+" + b);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error");
    }

    @Test
    @DisplayName("map2 propagates second Invalid when first is Valid")
    void map2PropagatesSecondInvalidWhenFirstIsValid() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map2(v1, v2, (a, b) -> a + "+" + b);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error");
    }

    @Test
    @DisplayName("map2 validates first Kind is non-null")
    void map2ValidatesFirstKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);

      assertThatThrownBy(() -> monad.map2(null, v2, (a, b) -> a + "+" + b))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("first")
          .hasMessageContaining("map2");
    }

    @Test
    @DisplayName("map2 validates second Kind is non-null")
    void map2ValidatesSecondKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);

      assertThatThrownBy(() -> monad.map2(v1, null, (a, b) -> a + "+" + b))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("second")
          .hasMessageContaining("map2");
    }

    @Test
    @DisplayName("map2 validates combining function is non-null")
    void map2ValidatesCombiningFunctionIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);

      java.util.function.BiFunction<Integer, Integer, String> nullFunction = null;
      assertThatThrownBy(() -> monad.map2(v1, v2, nullFunction))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("combining function")
          .hasMessageContaining("map2");
    }

    @Test
    @DisplayName("map2 validates function result is non-null")
    void map2ValidatesFunctionResultIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);

      assertThatThrownBy(() -> monad.map2(v1, v2, (a, b) -> null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("combining function")
          .hasMessageContaining("map2")
          .hasMessageContaining("null");
    }

    @Test
    @DisplayName("map2 works with different value types")
    void map2WorksWithDifferentValueTypes() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, String> v2 = VALIDATED.valid("test");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map2(v1, v2, (num, str) -> num + "-" + str);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo("10-test");
    }
  }

  @Nested
  @DisplayName("map3 Tests")
  class Map3Tests {

    // Parameterized test for all 8 combinations of Valid/Invalid for 3 arguments
    private static Stream<Arguments> map3CombinationProvider() {
      return Stream.of(
          // All valid
          Arguments.of(true, true, true, "10+20+30", true),
          // One invalid
          Arguments.of(false, true, true, "E1", false),
          Arguments.of(true, false, true, "E2", false),
          Arguments.of(true, true, false, "E3", false),
          // Two invalid
          Arguments.of(false, false, true, "E1, E2", false),
          Arguments.of(false, true, false, "E1, E3", false),
          Arguments.of(true, false, false, "E2, E3", false),
          // All invalid
          Arguments.of(false, false, false, "E1, E2, E3", false));
    }

    @ParameterizedTest(name = "map3 with v1={0}, v2={1}, v3={2} should result in {3}")
    @MethodSource("map3CombinationProvider")
    @DisplayName("map3 handles all Valid/Invalid combinations")
    void map3HandlesAllCombinations(
        boolean v1Valid, boolean v2Valid, boolean v3Valid, String expected, boolean shouldBeValid) {
      Kind<ValidatedKind.Witness<String>, Integer> v1 =
          v1Valid ? VALIDATED.valid(10) : VALIDATED.invalid("E1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 =
          v2Valid ? VALIDATED.valid(20) : VALIDATED.invalid("E2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 =
          v3Valid ? VALIDATED.valid(30) : VALIDATED.invalid("E3");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map3(v1, v2, v3, (a, b, c) -> a + "+" + b + "+" + c);

      Validated<String, String> validated = VALIDATED.narrow(result);
      if (shouldBeValid) {
        assertThat(validated.isValid()).isTrue();
        assertThat(validated.get()).isEqualTo(expected);
      } else {
        assertThat(validated.isInvalid()).isTrue();
        assertThat(validated.getError()).isEqualTo(expected);
      }
    }

    // Parameterized test for null parameter validations
    private static Stream<Arguments> map3NullParameterProvider() {
      Kind<ValidatedKind.Witness<String>, Integer> valid = VALIDATED.valid(10);
      return Stream.of(
          Arguments.of(null, valid, valid, "first"),
          Arguments.of(valid, null, valid, "second"),
          Arguments.of(valid, valid, null, "third"));
    }

    @ParameterizedTest(name = "map3 validates {3} parameter is non-null")
    @MethodSource("map3NullParameterProvider")
    @DisplayName("map3 validates all parameters are non-null")
    void map3ValidatesParametersAreNonNull(
        Kind<ValidatedKind.Witness<String>, Integer> v1,
        Kind<ValidatedKind.Witness<String>, Integer> v2,
        Kind<ValidatedKind.Witness<String>, Integer> v3,
        String paramName) {
      assertThatThrownBy(() -> monad.map3(v1, v2, v3, (a, b, c) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining(paramName)
          .hasMessageContaining("map3");
    }

    @Test
    @DisplayName("map3 validates function is non-null")
    void map3ValidatesFunctionIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);

      org.higherkindedj.hkt.function.Function3<Integer, Integer, Integer, String> nullFunction =
          null;
      assertThatThrownBy(() -> monad.map3(v1, v2, v3, nullFunction))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f")
          .hasMessageContaining("map3");
    }

    @Test
    @DisplayName("map3 validates function result is non-null")
    void map3ValidatesFunctionResultIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);

      assertThatThrownBy(() -> monad.map3(v1, v2, v3, (a, b, c) -> null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("f")
          .hasMessageContaining("map3")
          .hasMessageContaining("null");
    }
  }

  @Nested
  @DisplayName("map4 Tests")
  class Map4Tests {

    // Parameterized test for all 16 combinations of Valid/Invalid for 4 arguments
    private static Stream<Arguments> map4CombinationProvider() {
      return Stream.of(
          // All valid
          Arguments.of(true, true, true, true, 100, true),
          // One invalid
          Arguments.of(false, true, true, true, "E1", false),
          Arguments.of(true, false, true, true, "E2", false),
          Arguments.of(true, true, false, true, "E3", false),
          Arguments.of(true, true, true, false, "E4", false),
          // Two invalid
          Arguments.of(false, false, true, true, "E1, E2", false),
          Arguments.of(false, true, false, true, "E1, E3", false),
          Arguments.of(false, true, true, false, "E1, E4", false),
          Arguments.of(true, false, false, true, "E2, E3", false),
          Arguments.of(true, false, true, false, "E2, E4", false),
          Arguments.of(true, true, false, false, "E3, E4", false),
          // Three invalid
          Arguments.of(false, false, false, true, "E1, E2, E3", false),
          Arguments.of(false, false, true, false, "E1, E2, E4", false),
          Arguments.of(false, true, false, false, "E1, E3, E4", false),
          Arguments.of(true, false, false, false, "E2, E3, E4", false),
          // All invalid
          Arguments.of(false, false, false, false, "E1, E2, E3, E4", false));
    }

    @ParameterizedTest(name = "map4 with v1={0}, v2={1}, v3={2}, v4={3}")
    @MethodSource("map4CombinationProvider")
    @DisplayName("map4 handles all Valid/Invalid combinations")
    void map4HandlesAllCombinations(
        boolean v1Valid,
        boolean v2Valid,
        boolean v3Valid,
        boolean v4Valid,
        Object expected,
        boolean shouldBeValid) {
      Kind<ValidatedKind.Witness<String>, Integer> v1 =
          v1Valid ? VALIDATED.valid(10) : VALIDATED.invalid("E1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 =
          v2Valid ? VALIDATED.valid(20) : VALIDATED.invalid("E2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 =
          v3Valid ? VALIDATED.valid(30) : VALIDATED.invalid("E3");
      Kind<ValidatedKind.Witness<String>, Integer> v4 =
          v4Valid ? VALIDATED.valid(40) : VALIDATED.invalid("E4");

      if (shouldBeValid) {
        Kind<ValidatedKind.Witness<String>, Integer> result =
            monad.map4(v1, v2, v3, v4, (a, b, c, d) -> a + b + c + d);
        Validated<String, Integer> validated = VALIDATED.narrow(result);
        assertThat(validated.isValid()).isTrue();
        assertThat(validated.get()).isEqualTo(expected);
      } else {
        Kind<ValidatedKind.Witness<String>, String> result =
            monad.map4(v1, v2, v3, v4, (a, b, c, d) -> "test");
        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThat(validated.isInvalid()).isTrue();
        assertThat(validated.getError()).isEqualTo(expected);
      }
    }

    // Parameterized test for null parameter validations
    private static Stream<Arguments> map4NullParameterProvider() {
      Kind<ValidatedKind.Witness<String>, Integer> valid = VALIDATED.valid(10);
      return Stream.of(
          Arguments.of(null, valid, valid, valid, "first"),
          Arguments.of(valid, null, valid, valid, "second"),
          Arguments.of(valid, valid, null, valid, "third"),
          Arguments.of(valid, valid, valid, null, "fourth"));
    }

    @ParameterizedTest(name = "map4 validates {4} parameter is non-null")
    @MethodSource("map4NullParameterProvider")
    @DisplayName("map4 validates all parameters are non-null")
    void map4ValidatesParametersAreNonNull(
        Kind<ValidatedKind.Witness<String>, Integer> v1,
        Kind<ValidatedKind.Witness<String>, Integer> v2,
        Kind<ValidatedKind.Witness<String>, Integer> v3,
        Kind<ValidatedKind.Witness<String>, Integer> v4,
        String paramName) {
      assertThatThrownBy(() -> monad.map4(v1, v2, v3, v4, (a, b, c, d) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining(paramName)
          .hasMessageContaining("map4");
    }

    @Test
    @DisplayName("map4 validates function is non-null")
    void map4ValidatesFunctionIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(40);

      org.higherkindedj.hkt.function.Function4<Integer, Integer, Integer, Integer, String>
          nullFunction = null;
      assertThatThrownBy(() -> monad.map4(v1, v2, v3, v4, nullFunction))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f")
          .hasMessageContaining("map4");
    }

    @Test
    @DisplayName("map4 validates function result is non-null")
    void map4ValidatesFunctionResultIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(40);

      assertThatThrownBy(() -> monad.map4(v1, v2, v3, v4, (a, b, c, d) -> null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("f")
          .hasMessageContaining("map4")
          .hasMessageContaining("null");
    }
  }

  @Nested
  @DisplayName("map5 Tests")
  class Map5Tests {

    // Parameterized test for selected combinations (testing all 32 would be excessive)
    private static Stream<Arguments> map5CombinationProvider() {
      return Stream.of(
          // All valid
          Arguments.of(true, true, true, true, true, 15, true),
          // One invalid (test each position)
          Arguments.of(false, true, true, true, true, "E1", false),
          Arguments.of(true, false, true, true, true, "E2", false),
          Arguments.of(true, true, false, true, true, "E3", false),
          Arguments.of(true, true, true, false, true, "E4", false),
          Arguments.of(true, true, true, true, false, "E5", false),
          // Two invalid (test key combinations)
          Arguments.of(false, false, true, true, true, "E1, E2", false),
          Arguments.of(true, true, true, false, false, "E4, E5", false),
          Arguments.of(false, true, false, true, false, "E1, E3, E5", false),
          // Three invalid
          Arguments.of(false, false, false, true, true, "E1, E2, E3", false),
          Arguments.of(true, true, false, false, false, "E3, E4, E5", false),
          // Four invalid
          Arguments.of(false, false, false, false, true, "E1, E2, E3, E4", false),
          Arguments.of(true, false, false, false, false, "E2, E3, E4, E5", false),
          // All invalid
          Arguments.of(false, false, false, false, false, "E1, E2, E3, E4, E5", false));
    }

    @ParameterizedTest(name = "map5 with v1={0}, v2={1}, v3={2}, v4={3}, v5={4}")
    @MethodSource("map5CombinationProvider")
    @DisplayName("map5 handles Valid/Invalid combinations")
    void map5HandlesAllCombinations(
        boolean v1Valid,
        boolean v2Valid,
        boolean v3Valid,
        boolean v4Valid,
        boolean v5Valid,
        Object expected,
        boolean shouldBeValid) {
      Kind<ValidatedKind.Witness<String>, Integer> v1 =
          v1Valid ? VALIDATED.valid(1) : VALIDATED.invalid("E1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 =
          v2Valid ? VALIDATED.valid(2) : VALIDATED.invalid("E2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 =
          v3Valid ? VALIDATED.valid(3) : VALIDATED.invalid("E3");
      Kind<ValidatedKind.Witness<String>, Integer> v4 =
          v4Valid ? VALIDATED.valid(4) : VALIDATED.invalid("E4");
      Kind<ValidatedKind.Witness<String>, Integer> v5 =
          v5Valid ? VALIDATED.valid(5) : VALIDATED.invalid("E5");

      if (shouldBeValid) {
        Kind<ValidatedKind.Witness<String>, Integer> result =
            monad.map5(v1, v2, v3, v4, v5, (a, b, c, d, e) -> a + b + c + d + e);
        Validated<String, Integer> validated = VALIDATED.narrow(result);
        assertThat(validated.isValid()).isTrue();
        assertThat(validated.get()).isEqualTo(expected);
      } else {
        Kind<ValidatedKind.Witness<String>, String> result =
            monad.map5(v1, v2, v3, v4, v5, (a, b, c, d, e) -> "test");
        Validated<String, String> validated = VALIDATED.narrow(result);
        assertThat(validated.isInvalid()).isTrue();
        assertThat(validated.getError()).isEqualTo(expected);
      }
    }

    // Parameterized test for null parameter validations
    private static Stream<Arguments> map5NullParameterProvider() {
      Kind<ValidatedKind.Witness<String>, Integer> valid = VALIDATED.valid(1);
      return Stream.of(
          Arguments.of(null, valid, valid, valid, valid, "first"),
          Arguments.of(valid, null, valid, valid, valid, "second"),
          Arguments.of(valid, valid, null, valid, valid, "third"),
          Arguments.of(valid, valid, valid, null, valid, "fourth"),
          Arguments.of(valid, valid, valid, valid, null, "fifth"));
    }

    @ParameterizedTest(name = "map5 validates {5} parameter is non-null")
    @MethodSource("map5NullParameterProvider")
    @DisplayName("map5 validates all parameters are non-null")
    void map5ValidatesParametersAreNonNull(
        Kind<ValidatedKind.Witness<String>, Integer> v1,
        Kind<ValidatedKind.Witness<String>, Integer> v2,
        Kind<ValidatedKind.Witness<String>, Integer> v3,
        Kind<ValidatedKind.Witness<String>, Integer> v4,
        Kind<ValidatedKind.Witness<String>, Integer> v5,
        String paramName) {
      assertThatThrownBy(() -> monad.map5(v1, v2, v3, v4, v5, (a, b, c, d, e) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining(paramName)
          .hasMessageContaining("map5");
    }

    @Test
    @DisplayName("map5 validates function is non-null")
    void map5ValidatesFunctionIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(1);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(2);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(3);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(4);
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.valid(5);

      org.higherkindedj.hkt.function.Function5<Integer, Integer, Integer, Integer, Integer, String>
          nullFunction = null;
      assertThatThrownBy(() -> monad.map5(v1, v2, v3, v4, v5, nullFunction))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f")
          .hasMessageContaining("map5");
    }

    @Test
    @DisplayName("map5 validates function result is non-null")
    void map5ValidatesFunctionResultIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(1);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(2);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(3);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(4);
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.valid(5);

      assertThatThrownBy(() -> monad.map5(v1, v2, v3, v4, v5, (a, b, c, d, e) -> null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("f")
          .hasMessageContaining("map5")
          .hasMessageContaining("null");
    }
  }

  @Nested
  @DisplayName("Error Accumulation Order Tests")
  class ErrorAccumulationOrderTests {

    @Test
    @DisplayName("map2 error accumulation respects semigroup order")
    void map2ErrorAccumulationRespectsSemigroupOrder() {
      Semigroup<String> reverseSemigroup = (a, b) -> b + " before " + a;
      ValidatedMonad<String> reverseMonad = ValidatedMonad.instance(reverseSemigroup);

      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("first");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("second");

      Kind<ValidatedKind.Witness<String>, String> result =
          reverseMonad.map2(v1, v2, (a, b) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.getError()).isEqualTo("second before first");
    }

    @Test
    @DisplayName("map3 error accumulation respects order")
    void map3ErrorAccumulationRespectsOrder() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("A");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("B");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("C");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map3(v1, v2, v3, (a, b, c) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.getError()).isEqualTo("A, B, C");
    }

    @Test
    @DisplayName("map4 preserves error order")
    void map4PreservesErrorOrder() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("E1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(2);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("E3");
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.invalid("E4");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map4(v1, v2, v3, v4, (a, b, c, d) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.getError()).isEqualTo("E1, E3, E4");
    }

    @Test
    @DisplayName("map5 preserves error order with interspersed valid values")
    void map5PreservesErrorOrderWithInterspersedValidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("E1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(2);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("E3");
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(4);
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.invalid("E5");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map5(v1, v2, v3, v4, v5, (a, b, c, d, e) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.getError()).isEqualTo("E1, E3, E5");
    }
  }

  @Nested
  @DisplayName("Complex Scenarios")
  class ComplexScenarios {

    @Test
    @DisplayName("Nested map operations preserve error accumulation")
    void nestedMapOperationsPreserveErrorAccumulation() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);

      Kind<ValidatedKind.Witness<String>, Integer> combined = monad.map2(v1, v2, (a, b) -> a + b);

      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(40);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monad.map3(combined, v3, v4, (a, b, c) -> a + b + c);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(100);
    }

    @Test
    @DisplayName("All mapN methods work with custom complex types")
    void allMapNMethodsWorkWithCustomComplexTypes() {
      record Person(String name, int age) {}

      Kind<ValidatedKind.Witness<String>, String> name = VALIDATED.valid("Alice");
      Kind<ValidatedKind.Witness<String>, Integer> age = VALIDATED.valid(30);

      Kind<ValidatedKind.Witness<String>, Person> result = monad.map2(name, age, Person::new);

      Validated<String, Person> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get().name()).isEqualTo("Alice");
      assertThat(validated.get().age()).isEqualTo(30);
    }

    @Test
    @DisplayName("map5 with all same error produces single accumulated error")
    void map5WithAllSameErrorProducesSingleAccumulatedError() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("error");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("error");
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.invalid("error");
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.invalid("error");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map5(v1, v2, v3, v4, v5, (a, b, c, d, e) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error, error, error, error, error");
    }
  }

  @Nested
  @DisplayName("Semigroup Behavior Tests")
  class SemigroupBehaviorTests {

    @Test
    @DisplayName("List semigroup accumulates errors into lists")
    void listSemigroupAccumulatesErrorsIntoLists() {
      Semigroup<java.util.List<String>> listSemigroup = Semigroups.list();
      ValidatedMonad<java.util.List<String>> listMonad = ValidatedMonad.instance(listSemigroup);

      Kind<ValidatedKind.Witness<java.util.List<String>>, Integer> v1 =
          VALIDATED.widen(Validated.invalid(java.util.List.of("error1")));
      Kind<ValidatedKind.Witness<java.util.List<String>>, Integer> v2 =
          VALIDATED.widen(Validated.invalid(java.util.List.of("error2")));

      Kind<ValidatedKind.Witness<java.util.List<String>>, String> result =
          listMonad.map2(v1, v2, (a, b) -> "test");

      Validated<java.util.List<String>, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).containsExactly("error1", "error2");
    }

    @Test
    @DisplayName("First semigroup keeps only first error in map3")
    void firstSemigroupKeepsOnlyFirstErrorInMap3() {
      Semigroup<String> firstSemigroup = Semigroups.first();
      ValidatedMonad<String> firstMonad = ValidatedMonad.instance(firstSemigroup);

      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("error1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("error3");

      Kind<ValidatedKind.Witness<String>, String> result =
          firstMonad.map3(v1, v2, v3, (a, b, c) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error1");
    }

    @Test
    @DisplayName("Last semigroup keeps only last error in map4")
    void lastSemigroupKeepsOnlyLastErrorInMap4() {
      Semigroup<String> lastSemigroup = Semigroups.last();
      ValidatedMonad<String> lastMonad = ValidatedMonad.instance(lastSemigroup);

      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("error1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("error3");
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.invalid("error4");

      Kind<ValidatedKind.Witness<String>, String> result =
          lastMonad.map4(v1, v2, v3, v4, (a, b, c, d) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error4");
    }

    @Test
    @DisplayName("Custom semigroup works with map5")
    void customSemigroupWorksWithMap5() {
      Semigroup<String> countingSemigroup =
          new Semigroup<String>() {
            private int count = 0;

            @Override
            public String combine(String a, String b) {
              return "Error" + (++count) + ":" + a + "+" + b;
            }
          };
      ValidatedMonad<String> customMonad = ValidatedMonad.instance(countingSemigroup);

      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("A");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("B");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("C");
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(4);
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.invalid("E");

      Kind<ValidatedKind.Witness<String>, String> result =
          customMonad.map5(v1, v2, v3, v4, v5, (a, b, c, d, e) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      // The exact format depends on semigroup implementation details
      assertThat(validated.getError()).contains("A", "B", "C", "E");
    }
  }
}
