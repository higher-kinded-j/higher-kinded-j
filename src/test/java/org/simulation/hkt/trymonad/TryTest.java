package org.simulation.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Try<T> Direct Tests")
class TryTest {

  private final String successValue = "Success Value";
  private final RuntimeException failureException = new RuntimeException("Operation failed");
  private final IOException checkedException = new IOException("Checked I/O failed");
  private final Error error = new StackOverflowError("Stack overflow simulation");

  private final Try<String> successInstance = Try.success(successValue);
  private final Try<String> successNullInstance = Try.success(null); // Test null success value
  private final Try<String> failureInstance = Try.failure(failureException);
  private final Try<String> failureCheckedInstance = Try.failure(checkedException);
  private final Try<String> failureErrorInstance = Try.failure(error);

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {
    @Test
    void success_shouldCreateSuccessInstance() {
      assertThat(successInstance).isInstanceOf(Try.Success.class);
      assertThat(successInstance.isSuccess()).isTrue();
      assertThat(successInstance.isFailure()).isFalse();
      assertThatCode(() -> successInstance.get()).doesNotThrowAnyException();
      assertThatCode(() -> assertThat(successInstance.get()).isEqualTo(successValue))
          .doesNotThrowAnyException();
    }

    @Test
    void success_shouldAllowNullValue() {
      assertThat(successNullInstance).isInstanceOf(Try.Success.class);
      assertThat(successNullInstance.isSuccess()).isTrue();
      assertThatCode(() -> successNullInstance.get()).doesNotThrowAnyException();
      assertThatCode(() -> assertThat(successNullInstance.get()).isNull())
          .doesNotThrowAnyException();
    }

    @Test
    void failure_shouldCreateFailureInstance() {
      assertThat(failureInstance).isInstanceOf(Try.Failure.class);
      assertThat(failureInstance.isFailure()).isTrue();
      assertThat(failureInstance.isSuccess()).isFalse();
      assertThatThrownBy(failureInstance::get)
          .isInstanceOf(RuntimeException.class)
          .isSameAs(failureException);
    }

    @Test
    void failure_shouldCreateFailureInstanceWithCheckedException() {
      assertThat(failureCheckedInstance).isInstanceOf(Try.Failure.class);
      assertThat(failureCheckedInstance.isFailure()).isTrue();
      assertThatThrownBy(failureCheckedInstance::get)
          .isInstanceOf(IOException.class)
          .isSameAs(checkedException);
    }

    @Test
    void failure_shouldCreateFailureInstanceWithError() {
      assertThat(failureErrorInstance).isInstanceOf(Try.Failure.class);
      assertThat(failureErrorInstance.isFailure()).isTrue();
      assertThatThrownBy(failureErrorInstance::get)
          .isInstanceOf(StackOverflowError.class)
          .isSameAs(error);
    }

    @Test
    void failure_shouldThrowNPEForNullThrowable() {
      assertThatNullPointerException()
          .isThrownBy(() -> Try.failure(null))
          .withMessageContaining("Throwable for Failure cannot be null");
    }

    @Test
    void of_shouldCreateSuccessForNormalExecution() {
      Supplier<String> supplier = () -> successValue;
      Try<String> tryResult = Try.of(supplier);
      assertThat(tryResult).isEqualTo(successInstance); // Use equals for comparison
    }

    @Test
    void of_shouldCreateSuccessForNullReturn() {
      Supplier<String> supplier = () -> null;
      Try<String> tryResult = Try.of(supplier);
      assertThat(tryResult).isEqualTo(successNullInstance);
    }

    @Test
    void of_shouldCreateFailureWhenSupplierThrowsRuntimeException() {
      Supplier<String> supplier =
          () -> {
            throw failureException;
          };
      Try<String> tryResult = Try.of(supplier);
      assertThat(tryResult.isFailure()).isTrue();
      assertThatThrownBy(tryResult::get).isSameAs(failureException);
    }

    // Removed the test for Try.of catching checked exceptions directly via lambda,
    // as it's hard to implement cleanly due to Supplier signature limitations.
    // The Try.failure test covers creating Failure with checked exceptions,
    // and Try.of catching Throwable is tested via RuntimeException/Error.

    @Test
    void of_shouldCreateFailureWhenSupplierThrowsError() {
      Supplier<String> supplier =
          () -> {
            throw error;
          };
      Try<String> tryResult = Try.of(supplier);
      assertThat(tryResult.isFailure()).isTrue();
      assertThatThrownBy(tryResult::get).isSameAs(error);
    }

    @Test
    void of_shouldThrowNPEForNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> Try.of(null))
          .withMessageContaining("Supplier cannot be null");
    }
  }

  @Nested
  @DisplayName("Getters and Checks")
  class GettersAndChecks {
    @Test
    void isSuccess_returnsCorrectValue() {
      assertThat(successInstance.isSuccess()).isTrue();
      assertThat(failureInstance.isSuccess()).isFalse();
    }

    @Test
    void isFailure_returnsCorrectValue() {
      assertThat(successInstance.isFailure()).isFalse();
      assertThat(failureInstance.isFailure()).isTrue();
    }

    @Test
    void get_onSuccess_shouldReturnValue() throws Throwable {
      assertThat(successInstance.get()).isEqualTo(successValue);
      assertThat(successNullInstance.get()).isNull();
    }

    @Test
    void get_onFailure_shouldThrowContainedException() {
      assertThatThrownBy(failureInstance::get)
          .isInstanceOf(RuntimeException.class)
          .isSameAs(failureException);

      assertThatThrownBy(failureCheckedInstance::get)
          .isInstanceOf(IOException.class)
          .isSameAs(checkedException);

      assertThatThrownBy(failureErrorInstance::get)
          .isInstanceOf(StackOverflowError.class)
          .isSameAs(error);
    }
  }

  @Nested
  @DisplayName("orElse / orElseGet")
  class OrElseTests {
    final String defaultValue = "Default";
    final Supplier<String> defaultSupplier = () -> defaultValue;
    final Supplier<String> throwingSupplier =
        () -> {
          throw new IllegalStateException("Supplier failed");
        };
    final Supplier<String> nullSupplier = null;

    @Test
    void orElse_onSuccess_shouldReturnValue() {
      assertThat(successInstance.orElse(defaultValue)).isEqualTo(successValue);
      assertThat(successNullInstance.orElse(defaultValue))
          .isNull(); // Returns null if Success(null)
    }

    @Test
    void orElse_onFailure_shouldReturnDefault() {
      assertThat(failureInstance.orElse(defaultValue)).isEqualTo(defaultValue);
      assertThat(failureCheckedInstance.orElse(defaultValue)).isEqualTo(defaultValue);
      assertThat(failureErrorInstance.orElse(defaultValue)).isEqualTo(defaultValue);
    }

    @Test
    void orElseGet_onSuccess_shouldReturnValueAndNotCallSupplier() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> trackingSupplier =
          () -> {
            supplierCalled.set(true);
            return defaultValue;
          };
      assertThat(successInstance.orElseGet(trackingSupplier)).isEqualTo(successValue);
      assertThat(supplierCalled).isFalse();

      assertThat(successNullInstance.orElseGet(trackingSupplier)).isNull();
      assertThat(supplierCalled).isFalse();
    }

    @Test
    void orElseGet_onFailure_shouldCallSupplierAndReturnResult() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> trackingSupplier =
          () -> {
            supplierCalled.set(true);
            return defaultValue;
          };
      assertThat(failureInstance.orElseGet(trackingSupplier)).isEqualTo(defaultValue);
      assertThat(supplierCalled).isTrue();
    }

    @Test
    void orElseGet_onFailure_shouldThrowIfSupplierThrows() {
      assertThatThrownBy(() -> failureInstance.orElseGet(throwingSupplier))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Supplier failed");
    }

    @Test
    void orElseGet_onFailure_shouldThrowIfSupplierIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> failureInstance.orElseGet(nullSupplier))
          .withMessageContaining("supplier cannot be null");
    }

    @Test
    void orElseGet_onSuccess_shouldNotThrowIfSupplierIsNull() {
      // Supplier isn't called for Success, so null supplier is ok
      assertThatCode(() -> successInstance.orElseGet(nullSupplier)).doesNotThrowAnyException();
      assertThat(successInstance.orElseGet(nullSupplier)).isEqualTo(successValue);
    }
  }

  @Nested
  @DisplayName("fold()")
  class FoldTests {
    final Function<String, String> successMapper = s -> "Success mapped: " + s;
    final Function<Throwable, String> failureMapper = t -> "Failure mapped: " + t.getMessage();
    final Function<Throwable, String> throwingFailureMapper =
        t -> {
          throw new IllegalStateException("Failure mapper failed");
        };

    @Test
    void fold_onSuccess_shouldApplySuccessMapper() {
      String result = successInstance.fold(successMapper, failureMapper);
      assertThat(result).isEqualTo("Success mapped: " + successValue);

      String resultNull = successNullInstance.fold(successMapper, failureMapper);
      assertThat(resultNull).isEqualTo("Success mapped: null");
    }

    @Test
    void fold_onFailure_shouldApplyFailureMapper() {
      String result = failureInstance.fold(successMapper, failureMapper);
      assertThat(result).isEqualTo("Failure mapped: " + failureException.getMessage());

      String resultChecked = failureCheckedInstance.fold(successMapper, failureMapper);
      assertThat(resultChecked).isEqualTo("Failure mapped: " + checkedException.getMessage());

      String resultError = failureErrorInstance.fold(successMapper, failureMapper);
      assertThat(resultError).isEqualTo("Failure mapped: " + error.getMessage());
    }

    @Test
    void fold_onSuccess_shouldThrowIfSuccessMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> successInstance.fold(null, failureMapper))
          .withMessageContaining("successMapper cannot be null");
    }

    @Test
    void fold_onFailure_shouldThrowIfFailureMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> failureInstance.fold(successMapper, null))
          .withMessageContaining("failureMapper cannot be null");
    }

    @Test
    void fold_onFailure_shouldPropagateExceptionFromFailureMapper() {
      assertThatThrownBy(() -> failureInstance.fold(successMapper, throwingFailureMapper))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failure mapper failed");
    }
  }

  @Nested
  @DisplayName("map()")
  class MapTests {
    final Function<String, Integer> mapper = String::length;
    final Function<String, Integer> throwingMapper =
        s -> {
          throw new IllegalArgumentException("Mapper failed");
        };
    final Function<String, String> mapperToNull = s -> null;

    @Test
    void map_onSuccess_shouldApplyMapperAndReturnSuccess() {
      Try<Integer> result = successInstance.map(mapper);
      assertThat(result.isSuccess()).isTrue();
      assertThatCode(() -> assertThat(result.get()).isEqualTo(successValue.length()))
          .doesNotThrowAnyException();
    }

    @Test
    void map_onSuccess_shouldApplyMapperReturningNullAndReturnSuccess() {
      Try<String> result = successInstance.map(mapperToNull);
      assertThat(result.isSuccess()).isTrue();
      assertThatCode(() -> assertThat(result.get()).isNull()).doesNotThrowAnyException();
    }

    @Test
    void map_onSuccess_shouldReturnFailureIfMapperThrows() {
      Try<Integer> result = successInstance.map(throwingMapper);
      assertThat(result.isFailure()).isTrue();
      assertThatThrownBy(result::get)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Mapper failed");
    }

    @Test
    void map_onFailure_shouldReturnSameFailureInstance() {
      Try<Integer> result = failureInstance.map(mapper);
      assertThat(result).isSameAs(failureInstance); // Should return the same instance
      assertThat(result.isFailure()).isTrue();
      assertThatThrownBy(result::get).isSameAs(failureException);
    }

    @Test
    void map_shouldThrowIfMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> successInstance.map(null))
          .withMessageContaining("mapper cannot be null");
      // Failure case also checks for null mapper first
      assertThatNullPointerException()
          .isThrownBy(() -> failureInstance.map(null))
          .withMessageContaining("mapper cannot be null");
    }
  }

  @Nested
  @DisplayName("flatMap()")
  class FlatMapTests {
    final Function<String, Try<Integer>> mapperSuccess = s -> Try.success(s.length());
    final Function<String, Try<Integer>> mapperFailure =
        s -> Try.failure(new IOException("Inner flatMap failure"));
    final Function<String, Try<Integer>> mapperThrows =
        s -> {
          throw new IllegalStateException("FlatMap mapper func failed");
        };
    final Function<String, Try<Integer>> mapperNull = s -> null;

    @Test
    void flatMap_onSuccess_shouldApplyMapperAndReturnResult() {
      Try<Integer> resultSuccess = successInstance.flatMap(mapperSuccess);
      assertThat(resultSuccess.isSuccess()).isTrue();
      assertThatCode(() -> assertThat(resultSuccess.get()).isEqualTo(successValue.length()))
          .doesNotThrowAnyException();

      Try<Integer> resultFailure = successInstance.flatMap(mapperFailure);
      assertThat(resultFailure.isFailure()).isTrue();
      assertThatThrownBy(resultFailure::get)
          .isInstanceOf(IOException.class)
          .hasMessageContaining("Inner flatMap failure");
    }

    @Test
    void flatMap_onSuccess_shouldReturnFailureIfMapperFuncThrows() {
      Try<Integer> result = successInstance.flatMap(mapperThrows);
      assertThat(result.isFailure()).isTrue();
      assertThatThrownBy(result::get)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("FlatMap mapper func failed");
    }

    @Test
    void flatMap_onFailure_shouldReturnSameFailureInstance() {
      Try<Integer> result = failureInstance.flatMap(mapperSuccess);
      assertThat(result).isSameAs(failureInstance); // Should return the same instance
      assertThat(result.isFailure()).isTrue();
      assertThatThrownBy(result::get).isSameAs(failureException);
    }

    @Test
    void flatMap_shouldThrowIfMapperIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> successInstance.flatMap(null))
          .withMessageContaining("mapper cannot be null");
      // Failure case also checks for null mapper first
      assertThatNullPointerException()
          .isThrownBy(() -> failureInstance.flatMap(null))
          .withMessageContaining("mapper cannot be null");
    }

    @Test
    void flatMap_onSuccess_shouldThrowIfMapperReturnsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> successInstance.flatMap(mapperNull))
          .withMessageContaining("flatMap mapper returned null Try");
    }
  }

  @Nested
  @DisplayName("recover()")
  class RecoverTests {
    final Function<Throwable, String> recoveryFunc = t -> "Recovered: " + t.getMessage();
    final Function<Throwable, String> throwingRecoveryFunc =
        t -> {
          throw new IllegalStateException("Recovery failed");
        };
    final Function<Throwable, String> nullRecoveryFunc = null;

    @Test
    void recover_onSuccess_shouldReturnOriginalSuccess() {
      Try<String> result = successInstance.recover(recoveryFunc);
      assertThat(result).isSameAs(successInstance);
    }

    @Test
    void recover_onFailure_shouldApplyRecoveryFuncAndReturnSuccess() {
      Try<String> result = failureInstance.recover(recoveryFunc);
      assertThat(result.isSuccess()).isTrue();
      assertThatCode(
              () ->
                  assertThat(result.get()).isEqualTo("Recovered: " + failureException.getMessage()))
          .doesNotThrowAnyException();

      Try<String> resultChecked = failureCheckedInstance.recover(recoveryFunc);
      assertThat(resultChecked.isSuccess()).isTrue();
      assertThatCode(
              () ->
                  assertThat(resultChecked.get())
                      .isEqualTo("Recovered: " + checkedException.getMessage()))
          .doesNotThrowAnyException();
    }

    @Test
    void recover_onFailure_shouldReturnFailureIfRecoveryFuncThrows() {
      Try<String> result = failureInstance.recover(throwingRecoveryFunc);
      assertThat(result.isFailure()).isTrue();
      assertThatThrownBy(result::get)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Recovery failed");
    }

    @Test
    void recover_shouldThrowIfRecoveryFuncIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> failureInstance.recover(nullRecoveryFunc))
          .withMessageContaining("recoveryFunction cannot be null");
      // Success case doesn't call the function, but checks null first
      assertThatNullPointerException()
          .isThrownBy(() -> successInstance.recover(nullRecoveryFunc))
          .withMessageContaining("recoveryFunction cannot be null");
    }
  }

  @Nested
  @DisplayName("recoverWith()")
  class RecoverWithTests {
    final Function<Throwable, Try<String>> recoveryFuncSuccess =
        t -> Try.success("Recovered: " + t.getMessage());
    final Function<Throwable, Try<String>> recoveryFuncFailure =
        t -> Try.failure(new IOException("Recovery with failure"));
    final Function<Throwable, Try<String>> throwingRecoveryFunc =
        t -> {
          throw new IllegalStateException("RecoveryWith failed");
        };
    final Function<Throwable, Try<String>> nullReturningRecoveryFunc = t -> null;
    final Function<Throwable, Try<String>> nullRecoveryFunc = null;

    @Test
    void recoverWith_onSuccess_shouldReturnOriginalSuccess() {
      Try<String> result = successInstance.recoverWith(recoveryFuncSuccess);
      assertThat(result).isSameAs(successInstance);
    }

    @Test
    void recoverWith_onFailure_shouldApplyRecoveryFuncAndReturnItsResult() {
      Try<String> resultSuccess = failureInstance.recoverWith(recoveryFuncSuccess);
      assertThat(resultSuccess.isSuccess()).isTrue();
      assertThatCode(
              () ->
                  assertThat(resultSuccess.get())
                      .isEqualTo("Recovered: " + failureException.getMessage()))
          .doesNotThrowAnyException();

      Try<String> resultFailure = failureInstance.recoverWith(recoveryFuncFailure);
      assertThat(resultFailure.isFailure()).isTrue();
      assertThatThrownBy(resultFailure::get)
          .isInstanceOf(IOException.class)
          .hasMessageContaining("Recovery with failure");
    }

    @Test
    void recoverWith_onFailure_shouldReturnFailureIfRecoveryFuncThrows() {
      Try<String> result = failureInstance.recoverWith(throwingRecoveryFunc);
      assertThat(result.isFailure()).isTrue();
      assertThatThrownBy(result::get)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("RecoveryWith failed");
    }

    @Test
    void recoverWith_onFailure_shouldThrowIfRecoveryFuncReturnsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> failureInstance.recoverWith(nullReturningRecoveryFunc))
          .withMessageContaining("recoverWith function returned null Try");
    }

    @Test
    void recoverWith_shouldThrowIfRecoveryFuncIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> failureInstance.recoverWith(nullRecoveryFunc))
          .withMessageContaining("recoveryFunction cannot be null");
      // Success case doesn't call the function, but checks null first
      assertThatNullPointerException()
          .isThrownBy(() -> successInstance.recoverWith(nullRecoveryFunc))
          .withMessageContaining("recoveryFunction cannot be null");
    }
  }

  @Nested
  @DisplayName("match()")
  class MatchTests {
    final AtomicReference<String> successResult = new AtomicReference<>();
    final AtomicReference<Throwable> failureResult = new AtomicReference<>();
    final Consumer<String> successAction = s -> successResult.set("Success saw: " + s);
    final Consumer<Throwable> failureAction = t -> failureResult.set(t);
    final Consumer<String> throwingSuccessAction =
        s -> {
          throw new IllegalStateException("Success action failed");
        };
    final Consumer<Throwable> throwingFailureAction =
        t -> {
          throw new IllegalStateException("Failure action failed");
        };

    @Test
    void match_onSuccess_shouldExecuteSuccessAction() {
      successResult.set(null);
      failureResult.set(null);
      successInstance.match(successAction, failureAction);
      assertThat(successResult.get()).isEqualTo("Success saw: " + successValue);
      assertThat(failureResult.get()).isNull();
    }

    @Test
    void match_onFailure_shouldExecuteFailureAction() {
      successResult.set(null);
      failureResult.set(null);
      failureInstance.match(successAction, failureAction);
      assertThat(successResult.get()).isNull();
      assertThat(failureResult.get()).isSameAs(failureException);
    }

    @Test
    void match_onSuccess_shouldCatchExceptionFromSuccessAction() {
      successResult.set(null);
      failureResult.set(null);
      // Should not throw, but might log an error internally
      assertThatCode(() -> successInstance.match(throwingSuccessAction, failureAction))
          .doesNotThrowAnyException();
      assertThat(successResult.get()).isNull(); // Action didn't complete
      assertThat(failureResult.get()).isNull();
    }

    @Test
    @DisplayName("match on Failure should catch exception from failureAction")
    void match_onFailure_shouldCatchExceptionFromFailureAction() {
      // Use the existing failureInstance and throwingFailureAction defined in the test class
      // failureInstance = Try.failure(failureException);
      // throwingFailureAction = t -> { throw new IllegalStateException("Failure action failed"); };

      // Reset/clear any tracking variables if they were used (like successResult, failureResult)
      // In this specific test, we only care that match doesn't re-throw.
      successResult.set(null);
      failureResult.set(null);

      // Call match with the Failure instance and the action that throws
      // This forces the execution into the 'case Failure -> { try { ... } catch { ... } }' block,
      // and specifically into the 'catch' part within that block.
      assertThatCode(() -> failureInstance.match(successAction, throwingFailureAction))
          .doesNotThrowAnyException(); // Verify the match method itself doesn't throw

      // We cannot easily assert the System.err output without more complex test setup.
      // However, by confirming no exception is thrown *by match*, we know the internal
      // catch block handled the exception from throwingFailureAction.
      // This test ensures the code path leading to and executing the catch block is taken.

      // Assert that the non-throwing actions were not called or completed
      assertThat(successResult.get()).isNull();
      assertThat(failureResult.get())
          .isNull(); // failureResult isn't set because throwingFailureAction failed
    }

    @Test
    void match_shouldThrowIfSuccessActionIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> successInstance.match(null, failureAction))
          .withMessageContaining("successAction cannot be null");
    }

    @Test
    void match_shouldThrowIfFailureActionIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> failureInstance.match(successAction, null))
          .withMessageContaining("failureAction cannot be null");
    }
  }

  @Nested
  @DisplayName("toString()")
  class ToStringTests {
    @Test
    void toString_onSuccess() {
      assertThat(successInstance.toString()).isEqualTo("Success[value=" + successValue + "]");
      assertThat(successNullInstance.toString()).isEqualTo("Success[value=null]");
    }

    @Test
    void toString_onFailure() {
      assertThat(failureInstance.toString()).isEqualTo("Failure[cause=" + failureException + "]");
      assertThat(failureCheckedInstance.toString())
          .isEqualTo("Failure[cause=" + checkedException + "]");
    }
  }

  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsHashCodeTests {
    @Test
    void successEquals() {
      Try<String> success1 = Try.success("A");
      Try<String> success2 = Try.success("A");
      Try<String> success3 = Try.success("B");
      Try<String> successNull1 = Try.success(null);
      Try<String> successNull2 = Try.success(null);
      Try<String> failure1 = Try.failure(new RuntimeException("X"));

      assertThat(success1).isEqualTo(success2);
      assertThat(success1).hasSameHashCodeAs(success2);
      assertThat(success1).isNotEqualTo(success3);
      assertThat(success1).isNotEqualTo(successNull1);
      assertThat(success1).isNotEqualTo(failure1);
      assertThat(success1).isNotEqualTo(null);
      assertThat(success1).isNotEqualTo("A"); // Different type

      assertThat(successNull1).isEqualTo(successNull2);
      assertThat(successNull1).hasSameHashCodeAs(successNull2);
      assertThat(successNull1).isNotEqualTo(success1);
    }

    @Test
    void failureEquals() {
      RuntimeException ex1 = new RuntimeException("X");
      RuntimeException ex2 = new RuntimeException("X"); // Different instance, same content
      RuntimeException ex3 = new RuntimeException("Y");
      IOException ioEx = new IOException("X");

      Try<String> failure1 = Try.failure(ex1);
      Try<String> failure1Again = Try.failure(ex1); // Same exception instance
      Try<String> failure2 = Try.failure(ex2); // Different exception instance
      Try<String> failure3 = Try.failure(ex3);
      Try<String> failureIO = Try.failure(ioEx);
      Try<String> success1 = Try.success("A");

      assertThat(failure1).isEqualTo(failure1Again); // Equal because same exception instance
      assertThat(failure1).hasSameHashCodeAs(failure1Again);

      // Failures are generally equal only if they hold the *same* exception instance.
      // Equality based on exception type and message is possible but often not desired.
      // The record's default equals/hashCode relies on the contained object's equals/hashCode.
      // Standard Throwables often don't override equals based on content.
      assertThat(failure1).isNotEqualTo(failure2); // Different instances
      assertThat(failure1).isNotEqualTo(failure3);
      assertThat(failure1).isNotEqualTo(failureIO); // Different types
      assertThat(failure1).isNotEqualTo(success1);
      assertThat(failure1).isNotEqualTo(null);
      assertThat(failure1).isNotEqualTo(ex1); // Different type
    }
  }
}
