// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.VTaskAssert.assertThatVTask;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("VTaskApplicative Test Suite")
class VTaskApplicativeTest {

  private static final int TEST_VALUE = 42;
  private static final String TEST_STRING = "hello";
  private final VTaskApplicative applicative = VTaskApplicative.INSTANCE;

  @Nested
  @DisplayName("of() Operations")
  class OfOperations {

    @Test
    @DisplayName("of() wraps value in VTask")
    void ofWrapsValueInVTask() {
      Kind<VTaskKind.Witness, Integer> kind = applicative.of(TEST_VALUE);

      VTask<Integer> vtask = VTASK.narrow(kind);
      assertThat(vtask.run()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("of() handles null value")
    void ofHandlesNullValue() {
      Kind<VTaskKind.Witness, String> kind = applicative.of(null);

      VTask<String> vtask = VTASK.narrow(kind);
      assertThat(vtask.run()).isNull();
    }

    @Test
    @DisplayName("of() creates lazy VTask")
    void ofCreatesLazyVTask() {
      Kind<VTaskKind.Witness, Integer> kind = applicative.of(TEST_VALUE);

      // Should not throw, VTask is lazy
      assertThat(kind).isNotNull();
      assertThat(VTASK.narrow(kind)).isInstanceOf(VTask.class);
    }
  }

  @Nested
  @DisplayName("ap() Operations")
  class ApOperations {

    @Test
    @DisplayName("ap() applies function to value")
    void apAppliesFunctionToValue() {
      Function<Integer, String> intToString = Object::toString;
      Kind<VTaskKind.Witness, Function<Integer, String>> ff =
          VTASK.widen(VTask.succeed(intToString));
      Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(VTask.succeed(TEST_VALUE));

      Kind<VTaskKind.Witness, String> result = applicative.ap(ff, fa);

      VTask<String> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo("42");
    }

    @Test
    @DisplayName("ap() with multiple applications")
    void apWithMultipleApplications() {
      Function<Integer, Function<String, String>> curried = i -> s -> s + ":" + i;
      Kind<VTaskKind.Witness, Function<Integer, Function<String, String>>> ff =
          VTASK.widen(VTask.succeed(curried));
      Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(VTask.succeed(TEST_VALUE));

      Kind<VTaskKind.Witness, Function<String, String>> intermediate = applicative.ap(ff, fa);
      Kind<VTaskKind.Witness, String> fb = VTASK.widen(VTask.succeed(TEST_STRING));
      Kind<VTaskKind.Witness, String> result = applicative.ap(intermediate, fb);

      VTask<String> vtask = VTASK.narrow(result);
      assertThat(vtask.run()).isEqualTo("hello:42");
    }

    @Test
    @DisplayName("ap() propagates exception from function VTask")
    void apPropagatesExceptionFromFunctionVTask() {
      RuntimeException exception = new RuntimeException("Function VTask failed");
      Kind<VTaskKind.Witness, Function<Integer, String>> ff = VTASK.widen(VTask.fail(exception));
      Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(VTask.succeed(TEST_VALUE));

      Kind<VTaskKind.Witness, String> result = applicative.ap(ff, fa);

      assertThatVTask(VTASK.narrow(result))
          .fails()
          .withExceptionType(RuntimeException.class)
          .withMessage("Function VTask failed");
    }

    @Test
    @DisplayName("ap() propagates exception from value VTask")
    void apPropagatesExceptionFromValueVTask() {
      Function<Integer, String> intToString = Object::toString;
      Kind<VTaskKind.Witness, Function<Integer, String>> ff =
          VTASK.widen(VTask.succeed(intToString));
      RuntimeException exception = new RuntimeException("Value VTask failed");
      Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(VTask.fail(exception));

      Kind<VTaskKind.Witness, String> result = applicative.ap(ff, fa);

      assertThatVTask(VTASK.narrow(result))
          .fails()
          .withExceptionType(RuntimeException.class)
          .withMessage("Value VTask failed");
    }
  }

  @Nested
  @DisplayName("Validation Tests")
  class ValidationTests {

    @Test
    @DisplayName("ap() validates null function Kind")
    void apValidatesNullFunctionKind() {
      Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(VTask.succeed(TEST_VALUE));

      assertThatThrownBy(() -> applicative.ap(null, fa)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ap() validates null value Kind")
    void apValidatesNullValueKind() {
      Function<Integer, String> intToString = Object::toString;
      Kind<VTaskKind.Witness, Function<Integer, String>> ff =
          VTASK.widen(VTask.succeed(intToString));

      assertThatThrownBy(() -> applicative.ap(ff, null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    private final BiPredicate<Kind<VTaskKind.Witness, ?>, Kind<VTaskKind.Witness, ?>> eq =
        (k1, k2) -> Objects.equals(VTASK.narrow(k1).run(), VTASK.narrow(k2).run());

    @Test
    void identity() {
      Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(VTask.succeed(TEST_VALUE));
      ApplicativeLaws.assertIdentity(applicative, fa, eq);
    }

    @Test
    void homomorphism() {
      Function<Integer, String> f = Object::toString;
      ApplicativeLaws.assertHomomorphism(applicative, TEST_VALUE, f, eq);
    }

    @Test
    void interchange() {
      Function<Integer, String> f = i -> "Result:" + i;
      Kind<VTaskKind.Witness, Function<Integer, String>> u = VTASK.widen(VTask.succeed(f));
      ApplicativeLaws.assertInterchange(applicative, u, TEST_VALUE, eq);
    }

    @Test
    void composition() {
      Kind<VTaskKind.Witness, Function<String, Integer>> u =
          VTASK.widen(VTask.succeed(String::length));
      Function<Integer, String> intToString = i -> "Value:" + i;
      Kind<VTaskKind.Witness, Function<Integer, String>> v =
          VTASK.widen(VTask.succeed(intToString));
      Kind<VTaskKind.Witness, Integer> w = VTASK.widen(VTask.succeed(TEST_VALUE));
      ApplicativeLaws.assertComposition(applicative, u, v, w, eq);
    }
  }
}
