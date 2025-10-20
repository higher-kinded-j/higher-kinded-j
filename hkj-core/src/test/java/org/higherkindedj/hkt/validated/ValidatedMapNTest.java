// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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

    @Test
    @DisplayName("map3 combines three Valid values")
    void map3CombinesThreeValidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map3(v1, v2, v3, (a, b, c) -> a + "+" + b + "+" + c);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo("10+20+30");
    }

    @Test
    @DisplayName("map3 accumulates three errors")
    void map3AccumulatesThreeErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("error1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("error3");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map3(v1, v2, v3, (a, b, c) -> a + "+" + b + "+" + c);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error1, error2, error3");
    }

    @Test
    @DisplayName("map3 accumulates only errors (mixed valid and invalid)")
    void map3AccumulatesOnlyErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("error3");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map3(v1, v2, v3, (a, b, c) -> a + "+" + b + "+" + c);

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error2, error3");
    }

    @Test
    @DisplayName("map3 validates first Kind is non-null")
    void map3ValidatesFirstKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);

      assertThatThrownBy(() -> monad.map3(null, v2, v3, (a, b, c) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("first")
          .hasMessageContaining("map3");
    }

    @Test
    @DisplayName("map3 validates second Kind is non-null")
    void map3ValidatesSecondKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);

      assertThatThrownBy(() -> monad.map3(v1, null, v3, (a, b, c) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("second")
          .hasMessageContaining("map3");
    }

    @Test
    @DisplayName("map3 validates third Kind is non-null")
    void map3ValidatesThirdKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);

      assertThatThrownBy(() -> monad.map3(v1, v2, null, (a, b, c) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("third")
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

    @Test
    @DisplayName("map4 combines four Valid values")
    void map4CombinesFourValidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(40);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monad.map4(v1, v2, v3, v4, (a, b, c, d) -> a + b + c + d);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(100);
    }

    @Test
    @DisplayName("map4 accumulates four errors")
    void map4AccumulatesFourErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("error1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("error3");
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.invalid("error4");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map4(v1, v2, v3, v4, (a, b, c, d) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error1, error2, error3, error4");
    }

    @Test
    @DisplayName("map4 accumulates subset of errors")
    void map4AccumulatesSubsetOfErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.invalid("error4");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map4(v1, v2, v3, v4, (a, b, c, d) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error2, error4");
    }

    @Test
    @DisplayName("map4 validates first Kind is non-null")
    void map4ValidatesFirstKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(40);

      assertThatThrownBy(() -> monad.map4(null, v2, v3, v4, (a, b, c, d) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("first")
          .hasMessageContaining("map4");
    }

    @Test
    @DisplayName("map4 validates second Kind is non-null")
    void map4ValidatesSecondKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(40);

      assertThatThrownBy(() -> monad.map4(v1, null, v3, v4, (a, b, c, d) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("second")
          .hasMessageContaining("map4");
    }

    @Test
    @DisplayName("map4 validates third Kind is non-null")
    void map4ValidatesThirdKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(40);

      assertThatThrownBy(() -> monad.map4(v1, v2, null, v4, (a, b, c, d) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("third")
          .hasMessageContaining("map4");
    }

    @Test
    @DisplayName("map4 validates fourth Kind is non-null")
    void map4ValidatesFourthKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(20);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);

      assertThatThrownBy(() -> monad.map4(v1, v2, v3, null, (a, b, c, d) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fourth")
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

    @Test
    @DisplayName("map5 combines five Valid values")
    void map5CombinesFiveValidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(1);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(2);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(3);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(4);
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.valid(5);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monad.map5(v1, v2, v3, v4, v5, (a, b, c, d, e) -> a + b + c + d + e);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(15);
    }

    @Test
    @DisplayName("map5 accumulates five errors")
    void map5AccumulatesFiveErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("error1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("error3");
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.invalid("error4");
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.invalid("error5");

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map5(v1, v2, v3, v4, v5, (a, b, c, d, e) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error1, error2, error3, error4, error5");
    }

    @Test
    @DisplayName("map5 accumulates only errors from invalid values")
    void map5AccumulatesOnlyErrorsFromInvalidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(1);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(3);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.invalid("error4");
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.valid(5);

      Kind<ValidatedKind.Witness<String>, String> result =
          monad.map5(v1, v2, v3, v4, v5, (a, b, c, d, e) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error2, error4");
    }

    @Test
    @DisplayName("map5 validates first Kind is non-null")
    void map5ValidatesFirstKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(2);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(3);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(4);
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.valid(5);

      assertThatThrownBy(() -> monad.map5(null, v2, v3, v4, v5, (a, b, c, d, e) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("first")
          .hasMessageContaining("map5");
    }

    @Test
    @DisplayName("map5 validates second Kind is non-null")
    void map5ValidatesSecondKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(1);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(3);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(4);
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.valid(5);

      assertThatThrownBy(() -> monad.map5(v1, null, v3, v4, v5, (a, b, c, d, e) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("second")
          .hasMessageContaining("map5");
    }

    @Test
    @DisplayName("map5 validates third Kind is non-null")
    void map5ValidatesThirdKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(1);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(2);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(4);
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.valid(5);

      assertThatThrownBy(() -> monad.map5(v1, v2, null, v4, v5, (a, b, c, d, e) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("third")
          .hasMessageContaining("map5");
    }

    @Test
    @DisplayName("map5 validates fourth Kind is non-null")
    void map5ValidatesFourthKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(1);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(2);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(3);
      Kind<ValidatedKind.Witness<String>, Integer> v5 = VALIDATED.valid(5);

      assertThatThrownBy(() -> monad.map5(v1, v2, v3, null, v5, (a, b, c, d, e) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fourth")
          .hasMessageContaining("map5");
    }

    @Test
    @DisplayName("map5 validates fifth Kind is non-null")
    void map5ValidatesFifthKindIsNonNull() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.valid(1);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.valid(2);
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(3);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(4);

      assertThatThrownBy(() -> monad.map5(v1, v2, v3, v4, null, (a, b, c, d, e) -> "test"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fifth")
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

      // First map2
      Kind<ValidatedKind.Witness<String>, Integer> combined = monad.map2(v1, v2, (a, b) -> a + b);

      // Use result in map3
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.valid(30);
      Kind<ValidatedKind.Witness<String>, Integer> v4 = VALIDATED.valid(40);

      Kind<ValidatedKind.Witness<String>, Integer> result =
          monad.map3(combined, v3, v4, (a, b, c) -> a + b + c);

      Validated<String, Integer> validated = VALIDATED.narrow(result);
      assertThat(validated.isValid()).isTrue();
      assertThat(validated.get()).isEqualTo(100); // (10+20) + 30 + 40
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
    @DisplayName("First semigroup keeps only first error")
    void firstSemigroupKeepsOnlyFirstError() {
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
    @DisplayName("Last semigroup keeps only last error")
    void lastSemigroupKeepsOnlyLastError() {
      Semigroup<String> lastSemigroup = Semigroups.last();
      ValidatedMonad<String> lastMonad = ValidatedMonad.instance(lastSemigroup);

      Kind<ValidatedKind.Witness<String>, Integer> v1 = VALIDATED.invalid("error1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = VALIDATED.invalid("error2");
      Kind<ValidatedKind.Witness<String>, Integer> v3 = VALIDATED.invalid("error3");

      Kind<ValidatedKind.Witness<String>, String> result =
          lastMonad.map3(v1, v2, v3, (a, b, c) -> "test");

      Validated<String, String> validated = VALIDATED.narrow(result);
      assertThat(validated.isInvalid()).isTrue();
      assertThat(validated.getError()).isEqualTo("error3");
    }
  }
}
