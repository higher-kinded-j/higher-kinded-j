// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.vtask.VTaskAssert.assertThatVTask;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VTaskFunctor Test Suite")
class VTaskFunctorTest {

  private static final int TEST_VALUE = 42;
  private final VTaskFunctor functor = VTaskFunctor.INSTANCE;
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, Integer> stringLength = String::length;

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
      Function<Integer, String> nullSafeMapper = i -> String.valueOf(i);

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
          i -> {
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

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(VTask.succeed(TEST_VALUE));

      assertThatThrownBy(() -> functor.map(null, kind)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("map() validates null Kind")
    void mapValidatesNullKind() {
      assertThatThrownBy(() -> functor.map(intToString, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {

    @Test
    @DisplayName("Identity law: map(id, fa) == fa")
    void identityLaw() {
      VTask<Integer> original = VTask.succeed(TEST_VALUE);
      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(original);
      Function<Integer, Integer> identity = Function.identity();

      Kind<VTaskKind.Witness, Integer> result = functor.map(identity, kind);

      assertThat(VTASK.narrow(result).run()).isEqualTo(original.run());
    }

    @Test
    @DisplayName("Composition law: map(g.f, fa) == map(g, map(f, fa))")
    void compositionLaw() {
      VTask<Integer> original = VTask.succeed(TEST_VALUE);
      Kind<VTaskKind.Witness, Integer> kind = VTASK.widen(original);

      // Compose directly
      Function<Integer, Integer> composed = intToString.andThen(stringLength);
      Kind<VTaskKind.Witness, Integer> directResult = functor.map(composed, kind);

      // Compose via map
      Kind<VTaskKind.Witness, String> intermediate = functor.map(intToString, kind);
      Kind<VTaskKind.Witness, Integer> chainedResult = functor.map(stringLength, intermediate);

      assertThat(VTASK.narrow(directResult).run()).isEqualTo(VTASK.narrow(chainedResult).run());
    }
  }
}
