// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.func;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FunctionProfunctor Tests")
class FunctionProfunctorTest {

  private FunctionProfunctor profunctor;

  @BeforeEach
  void setUp() {
    profunctor = FunctionProfunctor.INSTANCE;
  }

  @Nested
  @DisplayName("Profunctor operation tests")
  class ProfunctorOperationTests {

    @Test
    @DisplayName("dimap should compose functions correctly")
    void dimap_shouldComposeFunctionsCorrectly() {
      Kind2<FunctionKind.Witness, String, Integer> stringLength = FUNCTION.widen(String::length);
      Function<Integer, String> intToString = String::valueOf;
      Function<Integer, Double> doubleValue = Integer::doubleValue;

      Kind2<FunctionKind.Witness, Integer, Double> dimapped =
          profunctor.dimap(intToString, doubleValue, stringLength);

      FunctionKind<Integer, Double> result = FUNCTION.narrow(dimapped);

      assertThat(result.apply(123)).isEqualTo(3.0);
    }

    @Test
    @DisplayName("lmap should pre-compose the input function")
    void lmap_shouldPreComposeInputFunction() {
      Kind2<FunctionKind.Witness, String, Integer> stringLength = FUNCTION.widen(String::length);
      Function<Integer, String> intToString = String::valueOf;

      Kind2<FunctionKind.Witness, Integer, Integer> lmapped =
          profunctor.lmap(intToString, stringLength);

      FunctionKind<Integer, Integer> result = FUNCTION.narrow(lmapped);

      assertThat(result.apply(54321)).isEqualTo(5);
    }

    @Test
    @DisplayName("rmap should post-compose the output function")
    void rmap_shouldPostComposeOutputFunction() {
      Kind2<FunctionKind.Witness, String, Integer> stringLength = FUNCTION.widen(String::length);
      Function<Integer, String> intToString = i -> "Length: " + i;

      Kind2<FunctionKind.Witness, String, String> rmapped =
          profunctor.rmap(intToString, stringLength);

      FunctionKind<String, String> result = FUNCTION.narrow(rmapped);

      assertThat(result.apply("hello")).isEqualTo("Length: 5");
    }
  }

  @Nested
  @DisplayName("Profunctor Laws")
  class ProfunctorLawTests {

    private final Kind2<FunctionKind.Witness, String, Integer> stringLength =
        FUNCTION.widen(String::length);

    @Test
    @DisplayName("1. Identity: dimap(id, id, p) == p")
    void identityLaw() {
      Kind2<FunctionKind.Witness, String, Integer> dimapped =
          profunctor.dimap(Function.identity(), Function.identity(), stringLength);

      FunctionKind<String, Integer> result = FUNCTION.narrow(dimapped);
      FunctionKind<String, Integer> original = FUNCTION.narrow(stringLength);

      assertThat(result.apply("test")).isEqualTo(original.apply("test"));
    }

    @Test
    @DisplayName("2. Composition: dimap(f . g, h . i, p) == dimap(g, h, dimap(f, i, p))")
    void compositionLaw() {
      Function<Integer, String> f1 = String::valueOf;
      Function<Double, Integer> f2 = Double::intValue;
      Function<Integer, Double> g1 = Integer::doubleValue;
      Function<Double, String> g2 = d -> d + "!";

      // The composition on the left side of the equation
      Kind2<FunctionKind.Witness, Double, String> composedDimap =
          profunctor.dimap(f1.compose(f2), g1.andThen(g2), stringLength);

      // The composition on the right side of the equation
      Kind2<FunctionKind.Witness, Integer, Double> innerDimap =
          profunctor.dimap(f1, g1, stringLength);
      Kind2<FunctionKind.Witness, Double, String> nestedDimap =
          profunctor.dimap(f2, g2, innerDimap);

      FunctionKind<Double, String> composedResult = FUNCTION.narrow(composedDimap);
      FunctionKind<Double, String> nestedResult = FUNCTION.narrow(nestedDimap);

      // Both sides should produce the same result
      assertThat(composedResult.apply(123.45)).isEqualTo(nestedResult.apply(123.45));
    }
  }

  @Test
  @DisplayName("INSTANCE should be a valid singleton")
  void instance_shouldBeAValidSingleton() {
    assertThat(FunctionProfunctor.INSTANCE).isNotNull();
    assertThat(FunctionProfunctor.INSTANCE).isSameAs(profunctor);
  }

  @Nested
  @DisplayName("FunctionKind Tests")
  class FunctionKindTest {

    @Test
    @DisplayName("apply should execute the wrapped function")
    void apply_shouldExecuteTheWrappedFunction() {
      FunctionKind<String, Integer> stringLength = FUNCTION.narrow(FUNCTION.widen(String::length));
      assertThat(stringLength.apply("hello")).isEqualTo(5);
    }

    @Test
    @DisplayName("Witness can be instantiated for code coverage")
    void witness_canBeInstantiated() {
      // This test exists solely to ensure the Witness class is covered by tests.
      // A witness type has no behaviour, but coverage tools check for instantiation.
      var witness = new FunctionKind.Witness();
      assertThat(witness).isNotNull();
    }
  }
}
