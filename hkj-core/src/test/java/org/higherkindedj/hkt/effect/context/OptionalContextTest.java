// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.assertj.core.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for OptionalContext.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Factory methods: io(), ioMaybe(), some(), none(), fromMaybe()
 *   <li>Chainable operations: map(), via(), flatMap()
 *   <li>Recovery operations: recover(), orElseValue(), recoverWith(), orElse()
 *   <li>Conversion methods: toErrorContext()
 *   <li>Execution methods: runIO(), runIOOrElse(), runIOOrThrow()
 *   <li>Escape hatch: toMaybeT()
 * </ul>
 */
@DisplayName("OptionalContext")
class OptionalContextTest {

  private static final Integer TEST_VALUE = 42;
  private static final String TEST_STRING = "test";

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethodTests {

    @Test
    @DisplayName("io() wraps non-null value as Just")
    void ioWrapsNonNullAsJust() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.io(() -> TEST_VALUE);

      Maybe<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("io() wraps null value as Nothing")
    void ioWrapsNullAsNothing() {
      OptionalContext<IOKind.Witness, String> ctx = OptionalContext.io(() -> null);

      Maybe<String> result = ctx.runIO().unsafeRun();
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("io() defers computation until run")
    void ioDefersComputation() {
      AtomicBoolean called = new AtomicBoolean(false);

      OptionalContext<IOKind.Witness, Integer> ctx =
          OptionalContext.io(
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
          .isThrownBy(() -> OptionalContext.io(null))
          .withMessageContaining("supplier must not be null");
    }

    @Test
    @DisplayName("ioMaybe() wraps computation returning Maybe")
    void ioMaybeWrapsComputation() {
      OptionalContext<IOKind.Witness, Integer> ctx =
          OptionalContext.ioMaybe(() -> Maybe.just(TEST_VALUE));

      Maybe<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("ioMaybe() defers computation")
    void ioMaybeDefersComputation() {
      AtomicBoolean called = new AtomicBoolean(false);

      OptionalContext<IOKind.Witness, Integer> ctx =
          OptionalContext.ioMaybe(
              () -> {
                called.set(true);
                return Maybe.just(TEST_VALUE);
              });

      assertThat(called.get()).isFalse();
      ctx.runIO().unsafeRun();
      assertThat(called.get()).isTrue();
    }

    @Test
    @DisplayName("some() creates Just context")
    void someCreatesJust() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.some(TEST_VALUE);

      Maybe<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.isJust()).isTrue();
      assertThat(result.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("some() validates non-null value")
    void someValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> OptionalContext.some(null))
          .withMessageContaining("value must not be null");
    }

    @Test
    @DisplayName("none() creates Nothing context")
    void noneCreatesNothing() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.none();

      Maybe<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("fromMaybe() wraps Just")
    void fromMaybeWrapsJust() {
      OptionalContext<IOKind.Witness, Integer> ctx =
          OptionalContext.fromMaybe(Maybe.just(TEST_VALUE));

      Maybe<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("fromMaybe() wraps Nothing")
    void fromMaybeWrapsNothing() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.fromMaybe(Maybe.nothing());

      Maybe<Integer> result = ctx.runIO().unsafeRun();
      assertThat(result.isNothing()).isTrue();
    }

    @Test
    @DisplayName("fromMaybe() validates non-null")
    void fromMaybeValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> OptionalContext.fromMaybe(null))
          .withMessageContaining("maybe must not be null");
    }
  }

  @Nested
  @DisplayName("Chainable Operations")
  class ChainableOperationsTests {

    @Test
    @DisplayName("map() transforms value")
    void mapTransformsValue() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.some(21);

      OptionalContext<IOKind.Witness, Integer> result = ctx.map(x -> x * 2);

      assertThat(result.runIO().unsafeRun().get()).isEqualTo(42);
    }

    @Test
    @DisplayName("map() preserves Nothing")
    void mapPreservesNothing() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.none();

      OptionalContext<IOKind.Witness, Integer> result = ctx.map(x -> x * 2);

      assertThat(result.runIO().unsafeRun().isNothing()).isTrue();
    }

    @Test
    @DisplayName("map() validates non-null mapper")
    void mapValidatesMapper() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.some(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() chains successful contexts")
    void viaChainsSuccess() {
      OptionalContext<IOKind.Witness, Integer> result =
          OptionalContext.<Integer>some(10)
              .via(x -> OptionalContext.some(x + 5))
              .via(x -> OptionalContext.some(x * 2));

      assertThat(result.runIO().unsafeRun().get()).isEqualTo(30);
    }

    @Test
    @DisplayName("via() short-circuits on Nothing")
    void viaShortCircuits() {
      AtomicBoolean called = new AtomicBoolean(false);

      OptionalContext<IOKind.Witness, Integer> result =
          OptionalContext.<Integer>none()
              .via(
                  x -> {
                    called.set(true);
                    return OptionalContext.some(x * 2);
                  });

      assertThat(result.runIO().unsafeRun().isNothing()).isTrue();
      assertThat(called.get()).isFalse();
    }

    @Test
    @DisplayName("via() validates non-null function")
    void viaValidatesFunction() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.some(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.via(null))
          .withMessageContaining("fn must not be null");
    }

    @Test
    @DisplayName("via() throws when function returns wrong context type")
    void viaThrowsOnWrongContextType() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.some(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> ctx.via(x -> ErrorContext.success(x.toString())).runIO().unsafeRun())
          .withMessageContaining("via function must return an OptionalContext");
    }

    @Test
    @DisplayName("flatMap() chains contexts")
    void flatMapChainsContexts() {
      OptionalContext<IOKind.Witness, Integer> result =
          OptionalContext.<Integer>some(10).flatMap(x -> OptionalContext.some(x * 2));

      assertThat(result.runIO().unsafeRun().get()).isEqualTo(20);
    }

    @Test
    @DisplayName("then() sequences independent computation")
    void thenSequences() {
      AtomicBoolean firstCalled = new AtomicBoolean(false);
      AtomicBoolean secondCalled = new AtomicBoolean(false);

      OptionalContext<IOKind.Witness, String> result =
          OptionalContext.<Integer>io(
                  () -> {
                    firstCalled.set(true);
                    return 1;
                  })
              .then(
                  () ->
                      OptionalContext.io(
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
    @DisplayName("recover() provides fallback for Nothing")
    void recoverProvidesFallback() {
      OptionalContext<IOKind.Witness, Integer> ctx =
          OptionalContext.<Integer>none().recover(unit -> TEST_VALUE);

      assertThat(ctx.runIO().unsafeRun().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recover() passes through Just")
    void recoverPassesThroughJust() {
      OptionalContext<IOKind.Witness, Integer> ctx =
          OptionalContext.<Integer>some(100).recover(unit -> TEST_VALUE);

      assertThat(ctx.runIO().unsafeRun().get()).isEqualTo(100);
    }

    @Test
    @DisplayName("recover() validates non-null function")
    void recoverValidatesFunction() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.none();

      assertThatNullPointerException()
          .isThrownBy(() -> ctx.recover(null))
          .withMessageContaining("recovery must not be null");
    }

    @Test
    @DisplayName("orElseValue() provides default for Nothing")
    void orElseValueProvidesDefault() {
      OptionalContext<IOKind.Witness, Integer> ctx =
          OptionalContext.<Integer>none().orElseValue(TEST_VALUE);

      assertThat(ctx.runIO().unsafeRun().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() provides fallback context for Nothing")
    void recoverWithProvidesFallback() {
      OptionalContext<IOKind.Witness, Integer> ctx =
          OptionalContext.<Integer>none().recoverWith(unit -> OptionalContext.some(TEST_VALUE));

      assertThat(ctx.runIO().unsafeRun().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("recoverWith() can return Nothing")
    void recoverWithCanReturnNothing() {
      OptionalContext<IOKind.Witness, Integer> ctx =
          OptionalContext.<Integer>none().recoverWith(unit -> OptionalContext.none());

      assertThat(ctx.runIO().unsafeRun().isNothing()).isTrue();
    }

    @Test
    @DisplayName("orElse() provides alternative context")
    void orElseProvidesFallback() {
      OptionalContext<IOKind.Witness, Integer> ctx =
          OptionalContext.<Integer>none().orElse(() -> OptionalContext.some(TEST_VALUE));

      assertThat(ctx.runIO().unsafeRun().get()).isEqualTo(TEST_VALUE);
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toErrorContext() converts Just to Right")
    void toErrorContextConvertsJustToRight() {
      ErrorContext<IOKind.Witness, String, Integer> result =
          OptionalContext.<Integer>some(TEST_VALUE).toErrorContext("not found");

      assertThat(result.runIO().unsafeRun().getRight()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toErrorContext() converts Nothing to Left with error")
    void toErrorContextConvertsNothingToLeft() {
      ErrorContext<IOKind.Witness, String, Integer> result =
          OptionalContext.<Integer>none().toErrorContext("not found");

      assertThat(result.runIO().unsafeRun().getLeft()).isEqualTo("not found");
    }
  }

  @Nested
  @DisplayName("Execution Methods")
  class ExecutionMethodsTests {

    @Test
    @DisplayName("runIO() returns IOPath with Maybe")
    void runIOReturnsIOPath() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.some(TEST_VALUE);

      var ioPath = ctx.runIO();

      assertThat(ioPath).isNotNull();
      assertThat(ioPath.unsafeRun().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runIOOrElse() returns value on Just")
    void runIOOrElseReturnsValueOnJust() {
      Integer result = OptionalContext.<Integer>some(TEST_VALUE).runIOOrElse(0);

      assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runIOOrElse() returns default on Nothing")
    void runIOOrElseReturnsDefaultOnNothing() {
      Integer result = OptionalContext.<Integer>none().runIOOrElse(0);

      assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("runIOOrThrow() returns value on Just")
    void runIOOrThrowReturnsValueOnJust() {
      Integer result = OptionalContext.<Integer>some(TEST_VALUE).runIOOrThrow();

      assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("runIOOrThrow() throws on Nothing")
    void runIOOrThrowThrowsOnNothing() {
      assertThatThrownBy(() -> OptionalContext.<Integer>none().runIOOrThrow())
          .isInstanceOf(NoSuchElementException.class);
    }
  }

  @Nested
  @DisplayName("Escape Hatch")
  class EscapeHatchTests {

    @Test
    @DisplayName("toMaybeT() returns underlying transformer")
    void toMaybeTReturnsTransformer() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.some(TEST_VALUE);

      var maybeT = ctx.toMaybeT();

      assertThat(maybeT).isNotNull();
    }

    @Test
    @DisplayName("underlying() returns Kind")
    void underlyingReturnsKind() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.some(TEST_VALUE);

      var underlying = ctx.underlying();

      assertThat(underlying).isNotNull();
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringTests {

    @Test
    @DisplayName("toString() contains OptionalContext")
    void toStringContainsClassName() {
      OptionalContext<IOKind.Witness, Integer> ctx = OptionalContext.some(TEST_VALUE);

      assertThat(ctx.toString()).contains("OptionalContext");
    }
  }
}
