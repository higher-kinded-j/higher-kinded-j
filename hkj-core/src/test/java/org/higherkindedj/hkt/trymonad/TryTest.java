// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trymonad;

import static org.assertj.core.api.Assertions.*;


import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Try<T> Complete Test Suite")
class TryTest extends TypeClassTestBase<TryKind.Witness, String, Integer> {

    private final String successValue = "Success Value";
    private final RuntimeException failureException = new RuntimeException("Test exception: map test");
    private final IOException checkedException = new IOException("Checked I/O failed");
    private final Error error = new StackOverflowError("Stack overflow simulation");

    private final Try<String> successInstance = Try.success(successValue);
    private final Try<String> successNullInstance = Try.success(null);
    private final Try<String> failureInstance = Try.failure(failureException);
    private final Try<String> failureCheckedInstance = Try.failure(checkedException);
    private final Try<String> failureErrorInstance = Try.failure(error);

    // ============================================================================
    // TypeClassTestBase Implementation
    // ============================================================================

    @Override
    protected Kind<TryKind.Witness, String> createValidKind() {
        return TryKindHelper.TRY.widen(successInstance);
    }

    @Override
    protected Kind<TryKind.Witness, String> createValidKind2() {
        return TryKindHelper.TRY.widen(Try.success("Second Value"));
    }

    @Override
    protected Function<String, Integer> createValidMapper() {
        return String::length;
    }

    @Override
    protected BiPredicate<Kind<TryKind.Witness, ?>, Kind<TryKind.Witness, ?>>
    createEqualityChecker() {
        return (k1, k2) -> {
            Try<?> t1 = TryKindHelper.TRY.narrow(k1);
            Try<?> t2 = TryKindHelper.TRY.narrow(k2);
            return t1.equals(t2);
        };
    }

    @Override
    protected Function<Integer, String> createSecondMapper() {
        return Object::toString;
    }

    @Override
    protected Function<String, Kind<TryKind.Witness, Integer>> createValidFlatMapper() {
        return s -> TryKindHelper.TRY.widen(Try.success(s.length()));
    }

    // ============================================================================
    // Comprehensive Test Suite Using Framework
    // ============================================================================

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete Try core type tests")
        void runCompleteTryPattern() {
            CoreTypeTest.<String>tryType(Try.class)
                    .withSuccess(successInstance)
                    .withFailure(failureInstance)
                    .withMappers(validMapper)
                    .testAll();
        }

        @Test
        @DisplayName("Test Try KindHelper implementation")
        void testKindHelper() {
            CoreTypeTest.tryKindHelper(successInstance).test();
        }

        @Test
        @DisplayName("Verify core Try operations")
        void verifyCoreOperations() {
            CoreTypeTest.<String>tryType(Try.class)
                    .withSuccess(successInstance)
                    .withFailure(failureInstance)
                    .withMappers(validMapper)
                    .testOperations();
        }
    }

    // ============================================================================
    // Factory Methods
    // ============================================================================

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("success() should create Success instance")
        void success_shouldCreateSuccessInstance() {
            assertThat(successInstance).isInstanceOf(Try.Success.class);
            assertThat(successInstance.isSuccess()).isTrue();
            assertThat(successInstance.isFailure()).isFalse();
            assertThatCode(() -> successInstance.get()).doesNotThrowAnyException();
            assertThatCode(() -> assertThat(successInstance.get()).isEqualTo(successValue))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("success() should allow null value")
        void success_shouldAllowNullValue() {
            assertThat(successNullInstance).isInstanceOf(Try.Success.class);
            assertThat(successNullInstance.isSuccess()).isTrue();
            assertThatCode(() -> successNullInstance.get()).doesNotThrowAnyException();
            assertThatCode(() -> assertThat(successNullInstance.get()).isNull())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("failure() should create Failure instance")
        void failure_shouldCreateFailureInstance() {
            assertThat(failureInstance).isInstanceOf(Try.Failure.class);
            assertThat(failureInstance.isFailure()).isTrue();
            assertThat(failureInstance.isSuccess()).isFalse();
            assertThatThrownBy(failureInstance::get)
                    .isInstanceOf(RuntimeException.class)
                    .isSameAs(failureException);
        }

        @Test
        @DisplayName("failure() should create Failure instance with checked exception")
        void failure_shouldCreateFailureInstanceWithCheckedException() {
            assertThat(failureCheckedInstance).isInstanceOf(Try.Failure.class);
            assertThat(failureCheckedInstance.isFailure()).isTrue();
            assertThatThrownBy(failureCheckedInstance::get)
                    .isInstanceOf(IOException.class)
                    .isSameAs(checkedException);
        }

        @Test
        @DisplayName("failure() should create Failure instance with Error")
        void failure_shouldCreateFailureInstanceWithError() {
            assertThat(failureErrorInstance).isInstanceOf(Try.Failure.class);
            assertThat(failureErrorInstance.isFailure()).isTrue();
            assertThatThrownBy(failureErrorInstance::get)
                    .isInstanceOf(StackOverflowError.class)
                    .isSameAs(error);
        }

        @Test
        @DisplayName("failure() should throw NPE for null Throwable")
        void failure_shouldThrowNPEForNullThrowable() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Try.failure(null))
                    .withMessageContaining("Throwable for Failure cannot be null");
        }

        @Test
        @DisplayName("of() should create Success for normal execution")
        void of_shouldCreateSuccessForNormalExecution() {
            Supplier<String> supplier = () -> successValue;
            Try<String> tryResult = Try.of(supplier);
            assertThat(tryResult).isEqualTo(successInstance);
        }

        @Test
        @DisplayName("of() should create Success for null return")
        void of_shouldCreateSuccessForNullReturn() {
            Supplier<String> supplier = () -> null;
            Try<String> tryResult = Try.of(supplier);
            assertThat(tryResult).isEqualTo(successNullInstance);
        }

        @Test
        @DisplayName("of() should create Failure when supplier throws RuntimeException")
        void of_shouldCreateFailureWhenSupplierThrowsRuntimeException() {
            Supplier<String> supplier =
                    () -> {
                        throw failureException;
                    };
            Try<String> tryResult = Try.of(supplier);
            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isSameAs(failureException);
        }

        @Test
        @DisplayName("of() should create Failure when supplier throws Error")
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
        @DisplayName("of() should create Failure when supplier throws checked exception")
        void of_shouldCreateFailureWhenSupplierThrowsCheckedException() {
            Supplier<String> supplier =
                    () -> {
                        return sneakyThrow(checkedException);
                    };
            Try<String> tryResult = Try.of(supplier);
            assertThat(tryResult.isFailure()).isTrue();
            assertThatThrownBy(tryResult::get).isInstanceOf(IOException.class).isSameAs(checkedException);
        }

        @Test
        @DisplayName("of() should throw NPE for null supplier")
        void of_shouldThrowNPEForNullSupplier() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Try.of(null))
                    .withMessageContaining("Function supplier for Try.of cannot be null");
        }
    }

    // ============================================================================
    // Getters and Checks
    // ============================================================================

    @Nested
    @DisplayName("Getters and Checks")
    class GettersAndChecks {

        @Test
        @DisplayName("isSuccess() returns correct value")
        void isSuccess_returnsCorrectValue() {
            assertThat(successInstance.isSuccess()).isTrue();
            assertThat(failureInstance.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("isFailure() returns correct value")
        void isFailure_returnsCorrectValue() {
            assertThat(successInstance.isFailure()).isFalse();
            assertThat(failureInstance.isFailure()).isTrue();
        }

        @Test
        @DisplayName("get() on Success should return value")
        void get_onSuccess_shouldReturnValue() throws Throwable {
            assertThat(successInstance.get()).isEqualTo(successValue);
            assertThat(successNullInstance.get()).isNull();
        }

        @Test
        @DisplayName("get() on Failure should throw contained exception")
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

    // ============================================================================
    // orElse / orElseGet
    // ============================================================================

    @Nested
    @DisplayName("orElse / orElseGet")
    class OrElseTests {
        final String defaultValue = "Default";
        final Supplier<String> defaultSupplier = () -> defaultValue;
        final Supplier<String> throwingSupplier =
                () -> {
                    throw new IllegalStateException("Supplier failed");
                };

        @Test
        @DisplayName("orElse() on Success should return value")
        void orElse_onSuccess_shouldReturnValue() {
            assertThat(successInstance.orElse(defaultValue)).isEqualTo(successValue);
            assertThat(successNullInstance.orElse(defaultValue)).isNull();
        }

        @Test
        @DisplayName("orElse() on Failure should return default")
        void orElse_onFailure_shouldReturnDefault() {
            assertThat(failureInstance.orElse(defaultValue)).isEqualTo(defaultValue);
            assertThat(failureCheckedInstance.orElse(defaultValue)).isEqualTo(defaultValue);
            assertThat(failureErrorInstance.orElse(defaultValue)).isEqualTo(defaultValue);
        }

        @Test
        @DisplayName("orElse() on Failure should return null if other is null")
        void orElse_onFailure_shouldReturnNullIfOtherIsNull() {
            assertThat(failureInstance.orElse(null)).isNull();
        }

        @Test
        @DisplayName("orElseGet() on Success should return value and not call supplier")
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
        @DisplayName("orElseGet() on Failure should call supplier and return result")
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
        @DisplayName("orElseGet() on Failure should return null if supplier returns null")
        void orElseGet_onFailure_shouldReturnNullIfSupplierReturnsNull() {
            Supplier<String> nullReturningSupplier = () -> null;
            assertThat(failureInstance.orElseGet(nullReturningSupplier)).isNull();
        }

        @Test
        @DisplayName("orElseGet() on Failure should throw if supplier throws")
        void orElseGet_onFailure_shouldThrowIfSupplierThrows() {
            assertThatThrownBy(() -> failureInstance.orElseGet(throwingSupplier))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Supplier failed");
        }

        @Test
        @DisplayName("orElseGet() should throw NPE if supplier is null")
        void orElseGet_shouldThrowIfSupplierIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> failureInstance.orElseGet(null))
                    .withMessageContaining("Function supplier for Try.orElseGet cannot be null");

            assertThatNullPointerException()
                    .isThrownBy(() -> successInstance.orElseGet(null))
                    .withMessageContaining("Function supplier for Try.orElseGet cannot be null");
        }
    }

    // ============================================================================
    // fold()
    // ============================================================================

    @Nested
    @DisplayName("fold()")
    class FoldTests {
        final Function<String, String> successMapper = s -> "Success mapped: " + s;
        final Function<Throwable, String> failureMapper = t -> "Failure mapped: " + t.getMessage();
        final Function<Throwable, String> throwingFailureMapper =
                t -> {
                    throw new IllegalStateException("Failure mapper failed");
                };
        final Function<String, String> throwingSuccessMapper =
                s -> {
                    throw new IllegalStateException("Success mapper failed");
                };

        @Test
        @DisplayName("fold() on Success should apply success mapper")
        void fold_onSuccess_shouldApplySuccessMapper() {
            String result = successInstance.fold(successMapper, failureMapper);
            assertThat(result).isEqualTo("Success mapped: " + successValue);

            String resultNull = successNullInstance.fold(successMapper, failureMapper);
            assertThat(resultNull).isEqualTo("Success mapped: null");
        }

        @Test
        @DisplayName("fold() on Failure should apply failure mapper")
        void fold_onFailure_shouldApplyFailureMapper() {
            String result = failureInstance.fold(successMapper, failureMapper);
            assertThat(result).isEqualTo("Failure mapped: " + failureException.getMessage());

            String resultChecked = failureCheckedInstance.fold(successMapper, failureMapper);
            assertThat(resultChecked).isEqualTo("Failure mapped: " + checkedException.getMessage());

            String resultError = failureErrorInstance.fold(successMapper, failureMapper);
            assertThat(resultError).isEqualTo("Failure mapped: " + error.getMessage());
        }

        @Test
        @DisplayName("fold() should throw NPE if success mapper is null")
        void fold_shouldThrowIfSuccessMapperIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> successInstance.fold(null, failureMapper))
                    .withMessageContaining("Function successMapper for Try.fold cannot be null");
        }

        @Test
        @DisplayName("fold() should throw NPE if failure mapper is null")
        void fold_shouldThrowIfFailureMapperIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> failureInstance.fold(successMapper, null))
                    .withMessageContaining("Function failureMapper for Try.fold cannot be null");
        }

        @Test
        @DisplayName("fold() on Success should propagate exception from success mapper")
        void fold_onSuccess_shouldPropagateExceptionFromSuccessMapper() {
            assertThatThrownBy(() -> successInstance.fold(throwingSuccessMapper, failureMapper))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Success mapper failed");
        }

        @Test
        @DisplayName("fold() on Failure should propagate exception from failure mapper")
        void fold_onFailure_shouldPropagateExceptionFromFailureMapper() {
            assertThatThrownBy(() -> failureInstance.fold(successMapper, throwingFailureMapper))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Failure mapper failed");
        }
    }

    // ============================================================================
    // toEither()
    // ============================================================================

    @Nested
    @DisplayName("toEither()")
    class ToEitherTests {
        final Function<Throwable, String> exToMessage = Throwable::getMessage;
        final Function<Throwable, String> exToCustom =
                t -> "CustomError: " + t.getClass().getSimpleName();
        final Function<Throwable, String> throwingMapper =
                t -> {
                    throw new IllegalStateException("toEither mapper failed");
                };

        @Test
        @DisplayName("toEither() on Success should return Right with value")
        void toEither_onSuccess_shouldReturnRightWithValue() {
            Either<String, String> result = successInstance.toEither(ex -> "ShouldNotBeCalled");
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isEqualTo(successValue);
        }

        @Test
        @DisplayName("toEither() on Success with null value should return Right with null")
        void toEither_onSuccessWithNullValue_shouldReturnRightWithNull() {
            Either<String, String> result = successNullInstance.toEither(ex -> "ShouldNotBeCalled");
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isNull();
        }

        @Test
        @DisplayName("toEither() on Failure should apply mapper and return Left")
        void toEither_onFailure_shouldApplyMapperAndReturnLeft() {
            Either<String, String> result = failureInstance.toEither(exToMessage);
            assertThat(result.isLeft()).isTrue();
            result.fold(
                    leftVal -> assertThat(leftVal).isEqualTo(failureException.getMessage()),
                    rightVal -> fail("Should be Left"));

            Either<String, String> resultChecked = failureCheckedInstance.toEither(exToCustom);
            assertThat(resultChecked.isLeft()).isTrue();
            resultChecked.fold(
                    leftVal -> assertThat(leftVal).isEqualTo("CustomError: IOException"),
                    rightVal -> fail("Should be Left"));
        }

        @Test
        @DisplayName("toEither() should throw NPE if mapper is null")
        void toEither_shouldThrowNPEIfMapperIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> failureInstance.toEither(null))
                    .withMessageContaining("Function failureToLeftMapper for Try.toEither cannot be null");

            assertThatNullPointerException()
                    .isThrownBy(() -> successInstance.toEither(null))
                    .withMessageContaining("Function failureToLeftMapper for Try.toEither cannot be null");
        }

        @Test
        @DisplayName("toEither() on Failure should throw NPE if mapper returns null")
        void toEither_onFailure_shouldThrowNPEIfMapperReturnsNull() {
            Function<Throwable, String> nullReturningMapper = t -> null;
            assertThatNullPointerException()
                    .isThrownBy(() -> failureInstance.toEither(nullReturningMapper))
                    .withMessageContaining(
                            "failureToLeftMapper returned null, which is not allowed for the left value of"
                                    + " Either");
        }

        @Test
        @DisplayName("toEither() on Failure should propagate exception from mapper")
        void toEither_onFailure_shouldPropagateExceptionFromMapper() {
            assertThatThrownBy(() -> failureInstance.toEither(throwingMapper))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("toEither mapper failed");
        }
    }

    // ============================================================================
    // map()
    // ============================================================================

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
        @DisplayName("map() on Success should apply mapper and return Success")
        void map_onSuccess_shouldApplyMapperAndReturnSuccess() {
            Try<Integer> result = successInstance.map(mapper);
            assertThat(result.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(result.get()).isEqualTo(successValue.length()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("map() on Success should apply mapper returning null and return Success")
        void map_onSuccess_shouldApplyMapperReturningNullAndReturnSuccess() {
            Try<String> result = successInstance.map(mapperToNull);
            assertThat(result.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(result.get()).isNull()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("map() on Success should return Failure if mapper throws")
        void map_onSuccess_shouldReturnFailureIfMapperThrows() {
            Try<Integer> result = successInstance.map(throwingMapper);
            assertThat(result.isFailure()).isTrue();
            assertThatThrownBy(result::get)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mapper failed");
        }

        @Test
        @DisplayName("map() on Failure should return same Failure instance")
        void map_onFailure_shouldReturnSameFailureInstance() {
            Try<Integer> result = failureInstance.map(mapper);
            assertThat(result).isSameAs(failureInstance);
            assertThat(result.isFailure()).isTrue();
            assertThatThrownBy(result::get).isSameAs(failureException);
        }

        @Test
        @DisplayName("map() should throw NPE if mapper is null")
        void map_shouldThrowIfMapperIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> successInstance.map(null))
                    .withMessageContaining("Function mapper for Try.map cannot be null");

            assertThatNullPointerException()
                    .isThrownBy(() -> failureInstance.map(null))
                    .withMessageContaining("Function mapper for Try.map cannot be null");
        }
    }

    // ============================================================================
    // flatMap()
    // ============================================================================

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

        @Test
        @DisplayName("flatMap() on Success should apply mapper and return result")
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
        @DisplayName("flatMap() on Success should return Failure if mapper func throws")
        void flatMap_onSuccess_shouldReturnFailureIfMapperFuncThrows() {
            Try<Integer> result = successInstance.flatMap(mapperThrows);
            assertThat(result.isFailure()).isTrue();
            assertThatThrownBy(result::get)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FlatMap mapper func failed");
        }

        @Test
        @DisplayName("flatMap() on Failure should return same Failure instance")
        void flatMap_onFailure_shouldReturnSameFailureInstance() {
            Try<Integer> result = failureInstance.flatMap(mapperSuccess);
            assertThat(result).isSameAs(failureInstance);
            assertThat(result.isFailure()).isTrue();
            assertThatThrownBy(result::get).isSameAs(failureException);
        }

        @Test
        @DisplayName("flatMap() should throw NPE if mapper is null")
        void flatMap_shouldThrowIfMapperIsNull() {
            assertThatException()
                    .isThrownBy(() -> successInstance.flatMap(null))
                    .withMessageContaining("Function mapper for Try.flatMap cannot be null");

            assertThatNullPointerException()
                    .isThrownBy(() -> failureInstance.flatMap(null))
                    .withMessageContaining("Function mapper for Try.flatMap cannot be null");
        }

        @Test
        @DisplayName("flatMap() on Success should throw NPE if mapper returns null")
        void flatMap_onSuccess_shouldThrowIfMapperReturnsNull() {
            Function<String, Try<Integer>> mapperNull = s -> null;
            assertThatThrownBy(() -> successInstance.flatMap(mapperNull))
                    .isInstanceOf(KindUnwrapException.class)
                    .hasMessageContaining(
                            "Function mapper in Try.flatMap returned null when Try expected, which is not allowed");
        }
    }

    // ============================================================================
    // recover()
    // ============================================================================

    @Nested
    @DisplayName("recover()")
    class RecoverTests {
        final Function<Throwable, String> recoveryFunc = t -> "Recovered: " + t.getMessage();
        final Function<Throwable, String> throwingRecoveryFunc =
                t -> {
                    throw new IllegalStateException("Recovery failed");
                };
        final Function<Throwable, String> nullReturningRecoveryFunc = t -> null;

        @Test
        @DisplayName("recover() on Success should return original Success")
        void recover_onSuccess_shouldReturnOriginalSuccess() {
            Try<String> result = successInstance.recover(recoveryFunc);
            assertThat(result).isSameAs(successInstance);
        }

        @Test
        @DisplayName("recover() on Failure should apply recovery func and return Success")
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
        @DisplayName("recover() on Failure should return Success with null if recovery func returns null")
        void recover_onFailure_shouldReturnSuccessWithNullIfRecoveryFuncReturnsNull() {
            Try<String> result = failureInstance.recover(nullReturningRecoveryFunc);
            assertThat(result.isSuccess()).isTrue();
            assertThatCode(() -> assertThat(result.get()).isNull()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("recover() on Failure should return Failure if recovery func throws")
        void recover_onFailure_shouldReturnFailureIfRecoveryFuncThrows() {
            Try<String> result = failureInstance.recover(throwingRecoveryFunc);
            assertThat(result.isFailure()).isTrue();
            assertThatThrownBy(result::get)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Recovery failed");
        }

        @Test
        @DisplayName("recover() should throw NPE if recovery func is null")
        void recover_shouldThrowIfRecoveryFuncIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> failureInstance.recover(null))
                    .withMessageContaining("Function recoveryFunction for Try.recover cannot be null");

            assertThatNullPointerException()
                    .isThrownBy(() -> successInstance.recover(null))
                    .withMessageContaining("Function recoveryFunction for Try.recoverFunction cannot be null");
        }
    }

    // ============================================================================
    // recoverWith()
    // ============================================================================

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

        @Test
        @DisplayName("recoverWith() on Success should return original Success")
        void recoverWith_onSuccess_shouldReturnOriginalSuccess() {
            Try<String> result = successInstance.recoverWith(recoveryFuncSuccess);
            assertThat(result).isSameAs(successInstance);
        }

        @Test
        @DisplayName("recoverWith() on Failure should apply recovery func and return its result")
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
        @DisplayName("recoverWith() on Failure should return Failure if recovery func throws")
        void recoverWith_onFailure_shouldReturnFailureIfRecoveryFuncThrows() {
            Try<String> result = failureInstance.recoverWith(throwingRecoveryFunc);
            assertThat(result.isFailure()).isTrue();
            assertThatThrownBy(result::get)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RecoveryWith failed");
        }

        @Test
        @DisplayName("recoverWith() on Failure should throw KindUnwrapException if recovery func returns null")
        void recoverWith_onFailure_shouldThrowIfRecoveryFuncReturnsNull() {
            assertThatThrownBy(() -> failureInstance.recoverWith(nullReturningRecoveryFunc))
                    .isInstanceOf(KindUnwrapException.class)
                    .hasMessageContaining("Function recoveryFunction in Try.recoverWith returned null when Try expected, which is not allowed");
        }

        @Test
        @DisplayName("recoverWith() should throw NPE if recovery func is null")
        void recoverWith_shouldThrowIfRecoveryFuncIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> failureInstance.recoverWith(null))
                    .withMessageContaining("Function recoveryFunction for Try.recoverWith cannot be null");

            assertThatNullPointerException()
                    .isThrownBy(() -> successInstance.recoverWith(null))
                    .withMessageContaining("Function recoveryFunction for Try.recoverWith cannot be null");
        }
    }

    // ============================================================================
    // match()
    // ============================================================================

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
        @DisplayName("match() on Success should execute success action")
        void match_onSuccess_shouldExecuteSuccessAction() {
            successResult.set(null);
            failureResult.set(null);
            successInstance.match(successAction, failureAction);
            assertThat(successResult.get()).isEqualTo("Success saw: " + successValue);
            assertThat(failureResult.get()).isNull();
        }

        @Test
        @DisplayName("match() on Failure should execute failure action")
        void match_onFailure_shouldExecuteFailureAction() {
            successResult.set(null);
            failureResult.set(null);
            failureInstance.match(successAction, failureAction);
            assertThat(successResult.get()).isNull();
            assertThat(failureResult.get()).isSameAs(failureException);
        }

        @Test
        @DisplayName("match() on Success should catch exception from success action")
        void match_onSuccess_shouldCatchExceptionFromSuccessAction() {
            successResult.set(null);
            failureResult.set(null);
            assertThatCode(() -> successInstance.match(throwingSuccessAction, failureAction))
                    .doesNotThrowAnyException();
            assertThat(successResult.get()).isNull();
            assertThat(failureResult.get()).isNull();
        }

        @Test
        @DisplayName("match() on Failure should catch exception from failure action")
        void match_onFailure_shouldCatchExceptionFromFailureAction() {
            successResult.set(null);
            failureResult.set(null);
            assertThatCode(() -> failureInstance.match(successAction, throwingFailureAction))
                    .doesNotThrowAnyException();
            assertThat(successResult.get()).isNull();
            assertThat(failureResult.get()).isNull();
        }

        @Test
        @DisplayName("match() should throw NPE if success action is null")
        void match_shouldThrowIfSuccessActionIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> successInstance.match(null, failureAction))
                    .withMessageContaining("Function successAction for Try.match cannot be null");
        }

        @Test
        @DisplayName("match() should throw NPE if failure action is null")
        void match_shouldThrowIfFailureActionIsNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> failureInstance.match(successAction, null))
                    .withMessageContaining("Function failureAction for Try.match cannot be null");
        }
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("toString() on Success")
        void toString_onSuccess() {
            assertThat(successInstance.toString()).isEqualTo("Success(" +successValue + ")");
            assertThat(successNullInstance.toString()).isEqualTo("Success(null)");
        }

        @Test
        @DisplayName("toString() on Failure")
        void toString_onFailure() {
            assertThat(failureInstance.toString()).isEqualTo("Failure(" + failureException + ")");
            assertThat(failureCheckedInstance.toString())
                    .isEqualTo("Failure(" + checkedException + ")");
        }

        @Test
        @DisplayName("equals() and hashCode() for Success")
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
            assertThat(success1).isNotEqualTo("A");

            assertThat(successNull1).isEqualTo(successNull2);
            assertThat(successNull1).hasSameHashCodeAs(successNull2);
            assertThat(successNull1).isNotEqualTo(success1);
        }

        @Test
        @DisplayName("equals() and hashCode() for Failure")
        void failureEquals() {
            RuntimeException ex1 = new RuntimeException("X");
            RuntimeException ex2 = new RuntimeException("X");
            RuntimeException ex3 = new RuntimeException("Y");
            IOException ioEx = new IOException("X");

            Try<String> failure1 = Try.failure(ex1);
            Try<String> failure1Again = Try.failure(ex1);
            Try<String> failure2 = Try.failure(ex2);
            Try<String> failure3 = Try.failure(ex3);
            Try<String> failureIO = Try.failure(ioEx);
            Try<String> success1 = Try.success("A");

            assertThat(failure1).isEqualTo(failure1Again);
            assertThat(failure1).hasSameHashCodeAs(failure1Again);

            assertThat(failure1).isNotEqualTo(failure2);
            assertThat(failure1).isNotEqualTo(failure3);
            assertThat(failure1).isNotEqualTo(failureIO);
            assertThat(failure1).isNotEqualTo(success1);
            assertThat(failure1).isNotEqualTo(null);
            assertThat(failure1).isNotEqualTo(ex1);
        }
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }
}