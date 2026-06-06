// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.VTaskAssert.assertThatVTask;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("VTaskFunctor Test Suite")
class VTaskFunctorTest {

  private static final int TEST_VALUE = 42;
  private final VTaskFunctor functor = VTaskFunctor.INSTANCE;
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, Integer> stringLength = String::length;
  private final Kind<VTaskKind.Witness, Integer> validKind = VTASK.widen(VTask.succeed(TEST_VALUE));

  /**
   * {@link Category#EXCEPTIONS} is omitted: the generic contract asserts that {@code map}
   * <em>propagates</em> a thrown mapper exception immediately, but a VTask is lazy — the exception
   * surfaces only when the task is run. That deferral is exercised by {@link ExceptionHandling}.
   */
  @Test
  @DisplayName("Functor contract — operations & validations (laws verified below)")
  void functorContract() {
    TypeClassContract.<VTaskKind.Witness>functor(VTaskFunctor.class)
        .<Integer>instance(functor)
        .<String>withKind(validKind)
        .withMapper(intToString)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS);
  }

  @Nested
  @DisplayName("map() Operations")
  class MapOperations {

    @Test
    @DisplayName("map() transforms the value")
    void mapTransformsValue() {
      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(VTask.succeed(TEST_VALUE));

      Kind<VTaskKind.Witness, String> result = functor.map(intToString, kind);

      VTask<String> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo("42");
    }

    @Test
    @DisplayName("map() preserves VTask structure")
    void mapPreservesVTaskStructure() {
      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(VTask.succeed(TEST_VALUE));

      Kind<VTaskKind.Witness, String> result = functor.map(intToString, kind);

      assertThat(result).isNotNull();
      assertThat(VTASK.narrow(result)).isInstanceOf(VTask.class);
    }

    @Test
    @DisplayName("map() with null value in VTask")
    void mapWithNullValueInVTask() {
      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(VTask.succeed(null));
      Function<Integer, String> nullSafeMapper = String::valueOf;

      Kind<VTaskKind.Witness, String> result = functor.map(nullSafeMapper, kind);

      VTask<String> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo("null");
    }

    @Test
    @DisplayName("map() chains multiple transformations")
    void mapChainsMultipleTransformations() {
      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(VTask.succeed(TEST_VALUE));

      Kind<VTaskKind.Witness, String> intermediate = functor.map(intToString, kind);
      Kind<VTaskKind.Witness, Integer> result = functor.map(stringLength, intermediate);

      VTask<Integer> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo(2); // "42".length() == 2
    }

    @Test
    @DisplayName("map() with complex transformations")
    void mapWithComplexTransformations() {
      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(VTask.succeed(TEST_VALUE));
      Function<Integer, String> complexMapper =
          i -> {
            if (i < 0) return "negative";
            if (i == 0) return "zero";
            return "positive:" + i;
          };

      Kind<VTaskKind.Witness, String> result = functor.map(complexMapper, kind);

      VTask<String> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo("positive:42");
    }
  }

  @Nested
  @DisplayName("Exception Handling")
  class ExceptionHandling {

    @Test
    @DisplayName("map() propagates exceptions from mapper function")
    void mapPropagatesExceptionsFromMapper() {
      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(VTask.succeed(TEST_VALUE));
      Function<Integer, String> throwingMapper =
          _ -> {
            throw new RuntimeException("Mapper failed");
          };

      Kind<VTaskKind.Witness, String> result = functor.map(throwingMapper, kind);

      assertThatVTask(VTASK.narrow(result))
          .fails()
          .withExceptionType(RuntimeException.class)
          .withMessage("Mapper failed");
    }

    @Test
    @DisplayName("map() propagates exceptions from source VTask")
    void mapPropagatesExceptionsFromSourceVTask() {
      RuntimeException exception = new RuntimeException("Source VTask failed");
      Kind<VTaskKind.Witness, Integer> failingKind = VTASK.widen(VTask.fail(exception));

      Kind<VTaskKind.Witness, String> result = functor.map(intToString, failingKind);

      assertThatVTask(VTASK.narrow(result))
          .fails()
          .withExceptionType(RuntimeException.class)
          .withMessage("Source VTask failed");
    }
  }

  // The standard map null-mapper / null-Kind validations are covered by functorContract's
  // VALIDATIONS category above. The VTask-specific exception deferral is kept in ExceptionHandling.

  @Nested
  @DisplayName("Laws")
  class Laws {

    private final BiPredicate<Kind<VTaskKind.Witness, ?>, Kind<VTaskKind.Witness, ?>> eq =
        (k1, k2) -> Objects.equals(VTASK.narrow(k1).run(), VTASK.narrow(k2).run());

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.vtask.VTaskLawFixtures#kinds")
    void identity(String label, Kind<VTaskKind.Witness, Integer> fa) {
      FunctorLaws.assertIdentity(functor, fa, eq);
    }

    @ParameterizedTest(name = "composition holds on {0}")
    @MethodSource("org.higherkindedj.hkt.vtask.VTaskLawFixtures#kinds")
    void composition(String label, Kind<VTaskKind.Witness, Integer> fa) {
      FunctorLaws.assertComposition(functor, fa, intToString, stringLength, eq);
    }
  }
}
