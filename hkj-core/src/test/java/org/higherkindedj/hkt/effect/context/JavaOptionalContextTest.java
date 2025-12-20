// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.io.IOKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for JavaOptionalContext.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Factory methods: io(), ioOptional(), some(), none(), fromOptional()
 *   <li>Chainable operations: map(), via(), flatMap(), then()
 *   <li>Recovery operations: recover(), orElseValue(), recoverWith(), orElse()
 *   <li>Conversion methods: toErrorContext(), toOptionalContext()
 *   <li>Execution methods: runIO(), runIOOrElse(), runIOOrThrow()
 *   <li>Escape hatch: toOptionalT()
 * </ul>
 */
@DisplayName("JavaOptionalContext")
class JavaOptionalContextTest {

  private static final Integer TEST_VALUE = 42;
  private static final String TEST_STRING = "test";

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodTests {

    @Test
    @DisplayName("io() wraps non-null value as present")
    void ioWrapsNonNullAsPresent() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.io(() -> TEST_VALUE);

      Optional<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("io() wraps null value as empty")
    void ioWrapsNullAsEmpty() {
      JavaOptionalContext<IOKind.Witness, String> ctx = JavaOptionalContext.io(() -> null);

      Optional<String> result = ctx.runIO().unsafeRun();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("io() defers computation until run")
    void ioDefersComputation() {
      AtomicBoolean called = new AtomicBoolean(false);

      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.io(
              () -> {
                called.set(true);
                return TEST_VALUE;
              });

      assertThat(called.get()).isFalse();
      ctx.runIO().unsafeRun();
      assertThat(called.get()).isTrue();
    }

    @Test
    @DisplayName("io() validates non-null supplier")
    void ioValidatesSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> JavaOptionalContext.io(null))
          .withMessageContaining("supplier must not be null");
    }

    @Test
    @DisplayName("ioOptional() wraps computation returning Optional")
    void ioOptionalWrapsComputation() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.ioOptional(() -> Optional.of(TEST_VALUE));

      Optional<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("ioOptional() defers computation")
    void ioOptionalDefersComputation() {
      AtomicBoolean called = new AtomicBoolean(false);

      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.ioOptional(
              () -> {
                called.set(true);
                return Optional.of(TEST_VALUE);
              });

      assertThat(called.get()).isFalse();
      ctx.runIO().unsafeRun();
      assertThat(called.get()).isTrue();
    }

    @Test
    @DisplayName("ioOptional() validates non-null supplier")
    void ioOptionalValidatesSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> JavaOptionalContext.ioOptional(null))
          .withMessageContaining("supplier must not be null");
    }

    @Test
    @DisplayName("some() creates present context")
    void someCreatesPresent() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.some(TEST_VALUE);

      Optional<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("some() validates non-null value")
    void someValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> JavaOptionalContext.some(null))
          .withMessageContaining("value must not be null");
    }

    @Test
    @DisplayName("none() creates empty context")
    void noneCreatesEmpty() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.none();

      Optional<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fromOptional() wraps present Optional")
    void fromOptionalWrapsPresent() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.fromOptional(Optional.of(TEST_VALUE));

      Optional<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("fromOptional() wraps empty Optional")
    void fromOptionalWrapsEmpty() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.fromOptional(Optional.empty());

      Optional<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fromOptional() validates non-null")
    void fromOptionalValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> JavaOptionalContext.fromOptional(null))
          .withMessageContaining("optional must not be null");
    }
  }

  @Nested
  @DisplayName("Chainable Operations")
  class ChainableOperationsTests {

    @Test
    @DisplayName("map() transforms value")
    void mapTransformsValue() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.some(21);

      JavaOptionalContext<IOKind.Witness, Integer> result = ctx.map(x -> x * 2);

      assertThat(result.runIO().unsafeRun().get()).isEqualTo(42);
    }

    @Test
    @DisplayName("map() preserves empty")
    void mapPreservesEmpty() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.none();

      JavaOptionalContext<IOKind.Witness, Integer> result = ctx.map(x -> x * 2);

      assertThat(result.runIO().unsafeRun()).isEmpty();
    }

    @Test
    @DisplayName("map() validates non-null mapper")
    void mapValidatesMapper() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.some(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() chains successful contexts")
    void viaChainsSuccess() {
      JavaOptionalContext<IOKind.Witness, Integer> result =
          JavaOptionalContext.<Integer>some(10)
              .via(x -> JavaOptionalContext.some(x + 5))
              .via(x -> JavaOptionalContext.some(x * 2));

      assertThat(result.runIO().unsafeRun().get()).isEqualTo(30);
    }

    @Test
    @DisplayName("via() short-circuits on empty")
    void viaShortCircuits() {
      AtomicBoolean called = new AtomicBoolean(false);

      JavaOptionalContext<IOKind.Witness, Integer> result =
          JavaOptionalContext.<Integer>none()
              .via(
                  x -> {
                    called.set(true);
                    return JavaOptionalContext.some(x * 2);
                  });

      assertThat(result.runIO().unsafeRun()).isEmpty();
      assertThat(called.get()).isFalse();
    }

    @Test
    @DisplayName("via() validates non-null function")
    void viaValidatesFunction() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.some(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.via(null))
          .withMessageContaining("fn must not be null");
    }

    @Test
    @DisplayName("via() throws when function returns wrong context type")
    void viaThrowsOnWrongContextType() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.some(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> ctx.via(x -> ErrorContext.success(x.toString())).runIO().unsafeRun())
          .withMessageContaining("via function must return a JavaOptionalContext");
    }

    @Test
    @DisplayName("flatMap() chains contexts")
    void flatMapChainsContexts() {
      JavaOptionalContext<IOKind.Witness, Integer> result =
          JavaOptionalContext.<Integer>some(10).flatMap(x -> JavaOptionalContext.some(x * 2));

      assertThat(result.runIO().unsafeRun().get()).isEqualTo(20);
    }

    @Test
    @DisplayName("then() sequences independent computation")
    void thenSequences() {
      AtomicBoolean firstCalled = new AtomicBoolean(false);
      AtomicBoolean secondCalled = new AtomicBoolean(false);

      JavaOptionalContext<IOKind.Witness, String> result =
          JavaOptionalContext.<Integer>io(
                  () -> {
                    firstCalled.set(true);
                    return 1;
                  })
              .then(
                  () ->
                      JavaOptionalContext.io(
                          () -> {
                            secondCalled.set(true);
                            return "done";
                          }));

      // Before running, neither should be called
      assertThat(firstCalled.get()).isFalse();
      assertThat(secondCalled.get()).isFalse();

      // After running
      result.runIO().unsafeRun();
      assertThat(firstCalled.get()).isTrue();
      assertThat(secondCalled.get()).isTrue();
    }
  }

  @Nested
  @DisplayName("Recovery Operations")
  class RecoveryOperationsTests {

    @Test
    @DisplayName("recover() provides fallback for empty")
    void recoverProvidesFallback() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.<Integer>none().recover(unit -> TEST_VALUE);

      assertThat(ctx.runIO().unsafeRun().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recover() passes through present")
    void recoverPassesThroughPresent() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.<Integer>some(100).recover(unit -> TEST_VALUE);

      assertThat(ctx.runIO().unsafeRun().get()).isEqualTo(100);
    }

    @Test
    @DisplayName("recover() validates non-null function")
    void recoverValidatesFunction() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.none();

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.recover(null))
          .withMessageContaining("recovery must not be null");
    }

    @Test
    @DisplayName("orElseValue() provides default for empty")
    void orElseValueProvidesDefault() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.<Integer>none().orElseValue(TEST_VALUE);

      assertThat(ctx.runIO().unsafeRun().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() provides fallback context for empty")
    void recoverWithProvidesFallback() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.<Integer>none()
              .recoverWith(unit -> JavaOptionalContext.some(TEST_VALUE));

      assertThat(ctx.runIO().unsafeRun().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() can return empty")
    void recoverWithCanReturnEmpty() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.<Integer>none().recoverWith(unit -> JavaOptionalContext.none());

      assertThat(ctx.runIO().unsafeRun()).isEmpty();
    }

    @Test
    @DisplayName("orElse() provides alternative context")
    void orElseProvidesFallback() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx =
          JavaOptionalContext.<Integer>none().orElse(() -> JavaOptionalContext.some(TEST_VALUE));

      assertThat(ctx.runIO().unsafeRun().get()).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toErrorContext() converts present to Right")
    void toErrorContextConvertsPresentToRight() {
      ErrorContext<IOKind.Witness, String, Integer> result =
          JavaOptionalContext.<Integer>some(TEST_VALUE).toErrorContext("not found");

      assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toErrorContext() converts empty to Left with error")
    void toErrorContextConvertsEmptyToLeft() {
      ErrorContext<IOKind.Witness, String, Integer> result =
          JavaOptionalContext.<Integer>none().toErrorContext("not found");

      assertThat(result.runIO().unsafeRun().getLeft()).isEqualTo("not found");
    }

    @Test
    @DisplayName("toOptionalContext() converts present to Just")
    void toOptionalContextConvertsPresentToJust() {
      OptionalContext<IOKind.Witness, Integer> result =
          JavaOptionalContext.<Integer>some(TEST_VALUE).toOptionalContext();

      assertThat(result.runIO().unsafeRun().isJust()).isTrue();
      assertThat(result.runIO().unsafeRun().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toOptionalContext() converts empty to Nothing")
    void toOptionalContextConvertsEmptyToNothing() {
      OptionalContext<IOKind.Witness, Integer> result =
          JavaOptionalContext.<Integer>none().toOptionalContext();

      assertThat(result.runIO().unsafeRun().isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Execution Methods")
  class ExecutionMethodsTests {

    @Test
    @DisplayName("runIO() returns IOPath with Optional")
    void runIOReturnsIOPath() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.some(TEST_VALUE);

      var ioPath = ctx.runIO();

      assertThat(ioPath).isNotNull();
      assertThat(ioPath.unsafeRun().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runIOOrElse() returns value on present")
    void runIOOrElseReturnsValueOnPresent() {
      Integer result = JavaOptionalContext.<Integer>some(TEST_VALUE).runIOOrElse(0);

      assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runIOOrElse() returns default on empty")
    void runIOOrElseReturnsDefaultOnEmpty() {
      Integer result = JavaOptionalContext.<Integer>none().runIOOrElse(0);

      assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("runIOOrThrow() returns value on present")
    void runIOOrThrowReturnsValueOnPresent() {
      Integer result = JavaOptionalContext.<Integer>some(TEST_VALUE).runIOOrThrow();

      assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runIOOrThrow() throws on empty")
    void runIOOrThrowThrowsOnEmpty() {
      assertThatThrownBy(() -> JavaOptionalContext.<Integer>none().runIOOrThrow())
          .isInstanceOf(NoSuchElementException.class);
    }
  }

  @Nested
  @DisplayName("Escape Hatch")
  class EscapeHatchTests {

    @Test
    @DisplayName("toOptionalT() returns underlying transformer")
    void toOptionalTReturnsTransformer() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.some(TEST_VALUE);

      var optionalT = ctx.toOptionalT();

      assertThat(optionalT).isNotNull();
    }

    @Test
    @DisplayName("underlying() returns Kind")
    void underlyingReturnsKind() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.some(TEST_VALUE);

      var underlying = ctx.underlying();

      assertThat(underlying).isNotNull();
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringTests {

    @Test
    @DisplayName("toString() contains JavaOptionalContext")
    void toStringContainsClassName() {
      JavaOptionalContext<IOKind.Witness, Integer> ctx = JavaOptionalContext.some(TEST_VALUE);

      assertThat(ctx.toString()).contains("JavaOptionalContext");
    }
  }
}
