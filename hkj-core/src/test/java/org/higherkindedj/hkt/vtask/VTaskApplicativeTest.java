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
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    @Test
    @DisplayName("Identity law: ap(of(id), fa) == fa")
    void identityLaw() {
      VTask<Integer> original = VTask.succeed(TEST_VALUE);
      Kind<VTaskKind.Witness, Integer> fa = VTASK.widen(original);
      Kind<VTaskKind.Witness, Function<Integer, Integer>> identity =
          applicative.of(Function.identity());

      Kind<VTaskKind.Witness, Integer> result = applicative.ap(identity, fa);

      assertThat(VTASK.narrow(result).run()).isEqualTo(original.run());
    }

    @Test
    @DisplayName("Homomorphism law: ap(of(f), of(x)) == of(f(x))")
    void homomorphismLaw() {
      Function<Integer, String> f = Object::toString;
      Kind<VTaskKind.Witness, Function<Integer, String>> ff = applicative.of(f);
      Kind<VTaskKind.Witness, Integer> fx = applicative.of(TEST_VALUE);

      Kind<VTaskKind.Witness, String> apResult = applicative.ap(ff, fx);
      Kind<VTaskKind.Witness, String> directResult = applicative.of(f.apply(TEST_VALUE));

      assertThat(VTASK.narrow(apResult).run()).isEqualTo(VTASK.narrow(directResult).run());
    }

    @Test
    @DisplayName("Interchange law: ap(u, of(y)) == ap(of(f -> f(y)), u)")
    void interchangeLaw() {
      Function<Integer, String> f = i -> "Result:" + i;
      Kind<VTaskKind.Witness, Function<Integer, String>> u = VTASK.widen(VTask.succeed(f));
      int y = TEST_VALUE;

      // Left side: ap(u, of(y))
      Kind<VTaskKind.Witness, String> leftSide = applicative.ap(u, applicative.of(y));

      // Right side: ap(of(f -> f(y)), u)
      Function<Function<Integer, String>, String> applyY = fn -> fn.apply(y);
      Kind<VTaskKind.Witness, Function<Function<Integer, String>, String>> ofApplyY =
          applicative.of(applyY);
      Kind<VTaskKind.Witness, String> rightSide = applicative.ap(ofApplyY, u);

      assertThat(VTASK.narrow(leftSide).run()).isEqualTo(VTASK.narrow(rightSide).run());
    }

    @Test
    @DisplayName("Composition law: ap(ap(ap(of(compose), u), v), w) == ap(u, ap(v, w))")
    void compositionLaw() {
      // u: VTask<Function<String, Integer>> - converts String to its length
      Function<String, Integer> stringToLength = String::length;
      Kind<VTaskKind.Witness, Function<String, Integer>> u =
          VTASK.widen(VTask.succeed(stringToLength));

      // v: VTask<Function<Integer, String>> - converts Integer to String
      Function<Integer, String> intToString = i -> "Value:" + i;
      Kind<VTaskKind.Witness, Function<Integer, String>> v =
          VTASK.widen(VTask.succeed(intToString));

      // w: VTask<Integer> - the value to transform
      Kind<VTaskKind.Witness, Integer> w = VTASK.widen(VTask.succeed(TEST_VALUE));

      // compose: (b -> c) -> (a -> b) -> (a -> c)
      Function<
              Function<String, Integer>,
              Function<Function<Integer, String>, Function<Integer, Integer>>>
          compose = bc -> ab -> a -> bc.apply(ab.apply(a));

      // Left side: ap(ap(ap(of(compose), u), v), w)
      Kind<
              VTaskKind.Witness,
              Function<
                  Function<String, Integer>,
                  Function<Function<Integer, String>, Function<Integer, Integer>>>>
          ofCompose = applicative.of(compose);
      Kind<VTaskKind.Witness, Function<Function<Integer, String>, Function<Integer, Integer>>>
          step1 = applicative.ap(ofCompose, u);
      Kind<VTaskKind.Witness, Function<Integer, Integer>> step2 = applicative.ap(step1, v);
      Kind<VTaskKind.Witness, Integer> leftSide = applicative.ap(step2, w);

      // Right side: ap(u, ap(v, w))
      Kind<VTaskKind.Witness, String> innerAp = applicative.ap(v, w);
      Kind<VTaskKind.Witness, Integer> rightSide = applicative.ap(u, innerAp);

      assertThat(VTASK.narrow(leftSide).run()).isEqualTo(VTASK.narrow(rightSide).run());
    }
  }
}
