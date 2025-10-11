// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.util.validation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Either core functionality test using standardised patterns.
 *
 * <p>This test focuses on the core Either functionality whilst using the standardised validation
 * framework for consistent error handling.
 */
@DisplayName("Either<L, R> Core Functionality - Standardised Test Suite")
class EitherTest extends TypeClassTestBase<EitherKind.Witness<String>, Integer, String> {

    private final String leftValue = "Error Message";
    private final Integer rightValue = 123;
    private final Either<String, Integer> leftInstance = Either.left(leftValue);
    private final Either<String, Integer> rightInstance = Either.right(rightValue);
    private final Either<String, Integer> leftNullInstance = Either.left(null);
    private final Either<String, Integer> rightNullInstance = Either.right(null);

    // Type class testing fixtures
    private EitherMonad<String> monad;
    private EitherFunctor<String> functor;

    @Override
    protected Kind<EitherKind.Witness<String>, Integer> createValidKind() {
        return EitherKindHelper.EITHER.widen(rightInstance);
    }

    @Override
    protected Kind<EitherKind.Witness<String>, Integer> createValidKind2() {
        return EitherKindHelper.EITHER.widen(Either.right(456));
    }

    @Override
    protected Function<Integer, String> createValidMapper() {
        return Object::toString;
    }

    @Override
    protected BiPredicate<Kind<EitherKind.Witness<String>, ?>, Kind<EitherKind.Witness<String>, ?>> createEqualityChecker() {
        return (k1, k2) -> EitherKindHelper.EITHER.narrow(k1).equals(EitherKindHelper.EITHER.narrow(k2));
    }

    @Override
    protected Function<String, String> createSecondMapper() {
        return s -> s; // String -> String for law testing
    }

    @Override
    protected Function<Integer, Kind<EitherKind.Witness<String>, String>> createValidFlatMapper() {
        return i -> EitherKindHelper.EITHER.widen(Either.right(String.valueOf(i)));
    }

    @Override
    protected Kind<EitherKind.Witness<String>, Function<Integer, String>> createValidFunctionKind() {
        return EitherKindHelper.EITHER.widen(Either.right(validMapper));
    }

    @Override
    protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
        return (i1, i2) -> String.valueOf(i1 + i2);
    }

    @Override
    protected Integer createTestValue() {
        return rightValue;
    }

    @Override
    protected Function<Integer, Kind<EitherKind.Witness<String>, String>> createTestFunction() {
        return i -> EitherKindHelper.EITHER.widen(Either.right(i.toString()));
    }

    @Override
    protected Function<String, Kind<EitherKind.Witness<String>, String>> createChainFunction() {
        return s -> EitherKindHelper.EITHER.widen(Either.right(s + "!"));
    }

    @BeforeEach
    void setUpEither() {
        monad = EitherMonad.instance();
        functor = EitherFunctor.instance();
    }

    @Nested
    @DisplayName("Complete Type Class Test Suite")
    class CompleteTypeClassTestSuite {

        @Test
        @DisplayName("Run complete Monad test pattern")
        void runCompleteMonadTestPattern() {
            // Test complete Monad behaviour using available methods
            TypeClassTest.<EitherKind.Witness<String>>monad(EitherMonad.class)
                    .<Integer>instance(monad)
                    .<String>withKind(validKind)
                    .withMonadOperations(
                            validKind2,
                            validMapper,
                            validFlatMapper,
                            validFunctionKind,
                            validCombiningFunction)
                    .withLawsTesting(
                            testValue,
                            testFunction,
                            chainFunction,
                            equalityChecker)
                    .configureValidation()
                    .useInheritanceValidation()
                    .withMapFrom(EitherFunctor.class)
                    .withApFrom(EitherMonad.class)
                    .withFlatMapFrom(EitherMonad.class)
                    .testAll();
        }

        @Test
        @DisplayName("Run complete Functor test pattern")
        void runCompleteFunctorTestPattern() {
            TypeClassTest.<EitherKind.Witness<String>>functor(EitherFunctor.class)
                    .<Integer>instance(functor)
                    .<String>withKind(validKind)
                    .withMapper(validMapper)
                    .withSecondMapper(secondMapper)
                    .withEqualityChecker(equalityChecker)
                    .testAll();
        }
    }

    @Nested
    @DisplayName("Core Type Testing with TypeClassTest API")
    class CoreTypeTestingSuite {

        @Test
        @DisplayName("Test all Either core operations")
        void testAllEitherCoreOperations() {
            TypeClassTest.<String, Integer>either(Either.class)
                    .withLeft(leftInstance)
                    .withRight(rightInstance)
                    .withMappers(TestFunctions.INT_TO_STRING)
                    .testAll();
        }

        @Test
        @DisplayName("Test Either with validation configuration")
        void testEitherWithValidationConfiguration() {
            TypeClassTest.<String, Integer>either(Either.class)
                    .withLeft(leftInstance)
                    .withRight(rightInstance)
                    .withMappers(TestFunctions.INT_TO_STRING)
                    .configureValidation()
                    .useInheritanceValidation()
                    .withMapFrom(EitherFunctor.class)
                    .withFlatMapFrom(EitherMonad.class)
                    .testAll();
        }

        @Test
        @DisplayName("Test Either selective operations")
        void testEitherSelectiveOperations() {
            TypeClassTest.<String, Integer>either(Either.class)
                    .withLeft(leftInstance)
                    .withRight(rightInstance)
                    .withMappers(TestFunctions.INT_TO_STRING)
                    .onlyFactoryMethods()
                    .testAll();
        }
    }

    @Nested
    @DisplayName("Factory Methods - Complete Coverage")
    class FactoryMethods {

        @Test
        @DisplayName("left() creates correct Left instances with all value types")
        void leftCreatesCorrectInstances() {
            // Non-null values
            assertThat(leftInstance).isInstanceOf(Either.Left.class);
            assertThat(leftInstance.isLeft()).isTrue();
            assertThat(leftInstance.isRight()).isFalse();
            assertThat(leftInstance.getLeft()).isEqualTo(leftValue);

            // Null values
            assertThat(leftNullInstance).isInstanceOf(Either.Left.class);
            assertThat(leftNullInstance.isLeft()).isTrue();
            assertThat(leftNullInstance.getLeft()).isNull();

            // Complex types
            Exception exception = new RuntimeException("test");
            Either<Exception, String> exceptionLeft = Either.left(exception);
            assertThat(exceptionLeft.getLeft()).isSameAs(exception);

            // Empty string
            Either<String, Integer> emptyLeft = Either.left("");
            assertThat(emptyLeft.getLeft()).isEmpty();
        }

        @Test
        @DisplayName("right() creates correct Right instances with all value types")
        void rightCreatesCorrectInstances() {
            // Non-null values
            assertThat(rightInstance).isInstanceOf(Either.Right.class);
            assertThat(rightInstance.isRight()).isTrue();
            assertThat(rightInstance.isLeft()).isFalse();
            assertThat(rightInstance.getRight()).isEqualTo(rightValue);

            // Null values
            assertThat(rightNullInstance).isInstanceOf(Either.Right.class);
            assertThat(rightNullInstance.isRight()).isTrue();
            assertThat(rightNullInstance.getRight()).isNull();

            // Complex types
            List<String> list = List.of("a", "b", "c");
            Either<String, List<String>> listRight = Either.right(list);
            assertThat(listRight.getRight()).isSameAs(list);

            // Primitives and wrappers
            Either<String, Boolean> boolRight = Either.right(true);
            assertThat(boolRight.getRight()).isTrue();
        }

        @Test
        @DisplayName("Factory methods type inference works correctly")
        void factoryMethodsTypeInference() {
            // Test that type inference works without explicit type parameters
            var stringLeft = Either.left("error");
            var intRight = Either.right(42);

            // Should be able to assign to properly typed variables
            Either<String, Object> leftAssignment = stringLeft;
            Either<Object, Integer> rightAssignment = intRight;

            assertThat(leftAssignment.getLeft()).isEqualTo("error");
            assertThat(rightAssignment.getRight()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("Getter Methods - Comprehensive Edge Cases")
    class GetterMethodsTests {

        @Test
        @DisplayName("getLeft() works correctly on all Left variations")
        void getLeftWorksCorrectly() {
            // Standard case
            assertThat(leftInstance.getLeft()).isEqualTo(leftValue);

            // Null case
            assertThat(leftNullInstance.getLeft()).isNull();

            // Complex types
            RuntimeException exception = new RuntimeException("Test exception: test");
            Either<RuntimeException, String> exceptionLeft = Either.left(exception);
            assertThat(exceptionLeft.getLeft()).isSameAs(exception);

            // Generic types
            List<String> errorList = List.of("error1", "error2");
            Either<List<String>, Integer> listLeft = Either.left(errorList);
            assertThat(listLeft.getLeft()).isSameAs(errorList);
        }

        @Test
        @DisplayName("getLeft() throws correct exceptions on Right instances")
        void getLeftThrowsOnRight() {
            // Standard Right
            assertThatThrownBy(rightInstance::getLeft)
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Cannot invoke getLeft() on a Right instance.");

            // Right with null
            assertThatThrownBy(rightNullInstance::getLeft)
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Cannot invoke getLeft() on a Right instance.");

            // Complex Right types
            Either<String, List<Integer>> listRight = Either.right(List.of(1, 2, 3));
            assertThatThrownBy(listRight::getLeft)
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Cannot invoke getLeft() on a Right instance.");
        }

        @Test
        @DisplayName("getRight() works correctly on all Right variations")
        void getRightWorksCorrectly() {
            // Standard case
            assertThat(rightInstance.getRight()).isEqualTo(rightValue);

            // Null case
            assertThat(rightNullInstance.getRight()).isNull();

            // Complex types
            List<String> resultList = List.of("a", "b", "c");
            Either<String, List<String>> listRight = Either.right(resultList);
            assertThat(listRight.getRight()).isSameAs(resultList);

            // Nested Either (Either as value)
            Either<String, Integer> nested = Either.right(99);
            Either<String, Either<String, Integer>> nestedRight = Either.right(nested);
            assertThat(nestedRight.getRight()).isSameAs(nested);
        }

        @Test
        @DisplayName("getRight() throws correct exceptions on Left instances")
        void getRightThrowsOnLeft() {
            // Standard Left
            assertThatThrownBy(leftInstance::getRight)
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Cannot invoke getRight() on a Left instance.");

            // Left with null
            assertThatThrownBy(leftNullInstance::getRight)
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Cannot invoke getRight() on a Left instance.");

            // Complex Left types
            Either<RuntimeException, String> exceptionLeft = Either.left(new RuntimeException("Test exception: test"));
            assertThatThrownBy(exceptionLeft::getRight)
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Cannot invoke getRight() on a Left instance.");
        }
    }

    @Nested
    @DisplayName("fold() Method - Complete Validation and Edge Cases")
    class FoldMethodTests {

        private final Function<String, String> leftMapper = l -> "Left mapped: " + l;
        private final Function<Integer, String> rightMapper = r -> "Right mapped: " + r;

        @Test
        @DisplayName("fold() applies correct mapper based on Either type")
        void foldAppliesCorrectMapper() {
            // Left mapping
            String leftResult = leftInstance.fold(leftMapper, rightMapper);
            assertThat(leftResult).isEqualTo("Left mapped: " + leftValue);

            // Right mapping
            String rightResult = rightInstance.fold(leftMapper, rightMapper);
            assertThat(rightResult).isEqualTo("Right mapped: " + rightValue);

            // Null value handling
            String leftNullResult = leftNullInstance.fold(leftMapper, rightMapper);
            assertThat(leftNullResult).isEqualTo("Left mapped: null");

            String rightNullResult = rightNullInstance.fold(leftMapper, rightMapper);
            assertThat(rightNullResult).isEqualTo("Right mapped: null");
        }

        @Test
        @DisplayName("fold() validates null mappers using standardised validation")
        void foldValidatesNullMappers() {
            ValidationTestBuilder.create()
                    .assertFunctionNull(
                            () -> leftInstance.fold(null, rightMapper),
                            "leftMapper",
                            Either.class,
                            Operation.FOLD)
                    .assertFunctionNull(
                            () -> rightInstance.fold(leftMapper, null),
                            "rightMapper",
                            Either.class,
                            Operation.FOLD)
                    .assertFunctionNull(
                            () -> leftInstance.fold(null, null), "leftMapper", Either.class, Operation.FOLD)
                    .execute();
        }

        @Test
        @DisplayName("fold() handles exception propagation correctly")
        void foldHandlesExceptionPropagation() {
            RuntimeException testException = new RuntimeException("Test exception: fold test");
            Function<String, String> throwingLeftMapper = TestFunctions.throwingFunction(testException);
            Function<Integer, String> throwingRightMapper = TestFunctions.throwingFunction(testException);

            // Left instance should call left mapper and propagate exception
            assertThatThrownBy(() -> leftInstance.fold(throwingLeftMapper, rightMapper))
                    .isSameAs(testException);

            // Right instance should call right mapper and propagate exception
            assertThatThrownBy(() -> rightInstance.fold(leftMapper, throwingRightMapper))
                    .isSameAs(testException);

            // Non-throwing mapper shouldn't be called
            String leftResult = leftInstance.fold(leftMapper, throwingRightMapper);
            assertThat(leftResult).isEqualTo("Left mapped: " + leftValue);

            String rightResult = rightInstance.fold(throwingLeftMapper, rightMapper);
            assertThat(rightResult).isEqualTo("Right mapped: " + rightValue);
        }

        @Test
        @DisplayName("fold() works with complex type transformations")
        void foldWorksWithComplexTransformations() {
            Either<List<String>, Integer> complexEither = Either.left(List.of("error1", "error2"));

            String result =
                    complexEither.fold(
                            errors -> "Errors: " + String.join(", ", errors), value -> "Success: " + value);

            assertThat(result).isEqualTo("Errors: error1, error2");

            // Test with Right complex type
            Either<String, List<Integer>> rightComplex = Either.right(List.of(1, 2, 3));
            String rightResult =
                    rightComplex.fold(
                            error -> "Error: " + error,
                            values -> "Sum: " + values.stream().mapToInt(Integer::intValue).sum());

            assertThat(rightResult).isEqualTo("Sum: 6");
        }
    }

    @Nested
    @DisplayName("map() Method - Comprehensive Right-Biased Testing")
    class MapMethodTests {

        @Test
        @DisplayName("map() applies function to Right values")
        void mapAppliesFunctionToRight() {
            // Standard transformation
            Either<String, String> result = rightInstance.map(TestFunctions.INT_TO_STRING);
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isEqualTo("123");

            // Complex transformation
            Either<String, List<Integer>> listResult = rightInstance.map(i -> List.of(i, i * 2));
            assertThat(listResult.getRight()).containsExactly(123, 246);

            // Null-safe transformation
            Either<String, String> nullResult = rightNullInstance.map(i -> String.valueOf(i));
            assertThat(nullResult.isRight()).isTrue();
            assertThat(nullResult.getRight()).isEqualTo("null");
        }

        @Test
        @DisplayName("map() preserves Left instances unchanged")
        void mapPreservesLeftInstances() {
            // Standard Left
            Either<String, String> result = leftInstance.map(TestFunctions.INT_TO_STRING);
            assertThat(result).isSameAs(leftInstance);
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isEqualTo(leftValue);

            // Left with null
            Either<String, String> nullResult = leftNullInstance.map(TestFunctions.INT_TO_STRING);
            assertThat(nullResult).isSameAs(leftNullInstance);
            assertThat(nullResult.isLeft()).isTrue();
            assertThat(nullResult.getLeft()).isNull();

            // Complex Left type
            RuntimeException exception = new RuntimeException("Test exception: test");
            Either<RuntimeException, Integer> exceptionLeft = Either.left(exception);
            Either<RuntimeException, String> mappedLeft = exceptionLeft.map(TestFunctions.INT_TO_STRING);
            assertThat(mappedLeft).isSameAs(exceptionLeft);
            assertThat(mappedLeft.getLeft()).isSameAs(exception);
        }

        @Test
        @DisplayName("map() validates null mapper using standardised validation")
        void mapValidatesNullMapper() {
            ValidationTestBuilder.create()
                    .assertMapperNull(() -> rightInstance.map(null), Either.class, Operation.MAP)
                    .assertMapperNull(() -> leftInstance.map(null), Either.class, Operation.MAP)
                    .execute();
        }

        @Test
        @DisplayName("map() handles exception propagation and chaining")
        void mapHandlesExceptionPropagation() {
            RuntimeException testException = new RuntimeException("Test exception: map test");
            Function<Integer, String> throwingMapper = TestFunctions.throwingFunction(testException);

            // Right instances should propagate exceptions
            assertThatThrownBy(() -> rightInstance.map(throwingMapper)).isSameAs(testException);

            // Left instances should not call mapper
            Either<String, String> leftResult = leftInstance.map(throwingMapper);
            assertThat(leftResult).isSameAs(leftInstance);

            // Test chaining
            Either<String, Integer> start = Either.right(10);
            Either<String, String> chainResult =
                    start.map(i -> i * 2).map(i -> "Value: " + i).map(String::toUpperCase);
            assertThat(chainResult.getRight()).isEqualTo("VALUE: 20");

            // Test chaining with Left short-circuit
            Either<String, Integer> leftStart = Either.left("error");
            Either<String, String> leftChainResult =
                    leftStart.map(i -> i * 2).map(i -> "Value: " + i).map(String::toUpperCase);
            assertThat(leftChainResult.isLeft()).isTrue();
            assertThat(leftChainResult.getLeft()).isEqualTo("error");
        }

        @Test
        @DisplayName("map() handles null-returning functions")
        void mapHandlesNullReturningFunctions() {
            Function<Integer, String> nullReturningMapper = TestFunctions.nullReturningFunction();

            Either<String, String> result = rightInstance.map(nullReturningMapper);
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isNull();
        }
    }

    @Nested
    @DisplayName("Side Effect Methods - Complete ifLeft/ifRight Testing")
    class SideEffectMethodsTests {

        @Test
        @DisplayName("ifLeft() executes action on Left instances")
        void ifLeftExecutesOnLeft() {
            AtomicBoolean executed = new AtomicBoolean(false);
            AtomicBoolean correctValue = new AtomicBoolean(false);

            Consumer<String> action =
                    s -> {
                        executed.set(true);
                        correctValue.set(leftValue.equals(s));
                    };

            leftInstance.ifLeft(action);
            assertThat(executed).isTrue();
            assertThat(correctValue).isTrue();

            // Test with null value
            AtomicBoolean nullExecuted = new AtomicBoolean(false);
            Consumer<String> nullAction =
                    s -> {
                        nullExecuted.set(true);
                        assertThat(s).isNull();
                    };

            leftNullInstance.ifLeft(nullAction);
            assertThat(nullExecuted).isTrue();
        }

        @Test
        @DisplayName("ifLeft() does not execute action on Right instances")
        void ifLeftDoesNotExecuteOnRight() {
            AtomicBoolean shouldNotExecute = new AtomicBoolean(false);
            Consumer<String> action = s -> shouldNotExecute.set(true);

            rightInstance.ifLeft(action);
            assertThat(shouldNotExecute).isFalse();

            rightNullInstance.ifLeft(action);
            assertThat(shouldNotExecute).isFalse();
        }

        @Test
        @DisplayName("ifRight() executes action on Right instances")
        void ifRightExecutesOnRight() {
            AtomicBoolean executed = new AtomicBoolean(false);
            AtomicBoolean correctValue = new AtomicBoolean(false);

            Consumer<Integer> action =
                    i -> {
                        executed.set(true);
                        correctValue.set(rightValue.equals(i));
                    };

            rightInstance.ifRight(action);
            assertThat(executed).isTrue();
            assertThat(correctValue).isTrue();

            // Test with null value
            AtomicBoolean nullExecuted = new AtomicBoolean(false);
            Consumer<Integer> nullAction =
                    i -> {
                        nullExecuted.set(true);
                        assertThat(i).isNull();
                    };

            rightNullInstance.ifRight(nullAction);
            assertThat(nullExecuted).isTrue();
        }

        @Test
        @DisplayName("ifRight() does not execute action on Left instances")
        void ifRightDoesNotExecuteOnLeft() {
            AtomicBoolean shouldNotExecute = new AtomicBoolean(false);
            Consumer<Integer> action = i -> shouldNotExecute.set(true);

            leftInstance.ifRight(action);
            assertThat(shouldNotExecute).isFalse();

            leftNullInstance.ifRight(action);
            assertThat(shouldNotExecute).isFalse();
        }

        @Test
        @DisplayName("Side effect methods validate null actions")
        void sideEffectMethodsValidateNullActions() {
            ValidationTestBuilder.create()
                    .assertFunctionNull(
                            () -> leftInstance.ifLeft(null), "action", Either.class, Operation.IF_LEFT)
                    .assertFunctionNull(
                            () -> rightInstance.ifLeft(null), "action", Either.class, Operation.IF_LEFT)
                    .assertFunctionNull(
                            () -> rightInstance.ifRight(null), "action", Either.class, Operation.IF_RIGHT)
                    .assertFunctionNull(
                            () -> leftInstance.ifRight(null), "action", Either.class, Operation.IF_RIGHT)
                    .execute();
        }

        @Test
        @DisplayName("Side effect methods handle exceptions in actions")
        void sideEffectMethodsHandleExceptions() {
            RuntimeException testException = new RuntimeException("Test exception: side effect test");
            Consumer<String> throwingLeftAction =
                    s -> {
                        throw testException;
                    };
            Consumer<Integer> throwingRightAction =
                    i -> {
                        throw testException;
                    };

            // Exceptions should propagate from actions
            assertThatThrownBy(() -> leftInstance.ifLeft(throwingLeftAction)).isSameAs(testException);

            assertThatThrownBy(() -> rightInstance.ifRight(throwingRightAction)).isSameAs(testException);

            // Non-matching sides should not call actions
            assertThatCode(() -> rightInstance.ifLeft(throwingLeftAction)).doesNotThrowAnyException();

            assertThatCode(() -> leftInstance.ifRight(throwingRightAction)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("flatMap() Method - Comprehensive Monadic Testing")
    class FlatMapMethodTests {

        @Test
        @DisplayName("flatMap() applies function to Right values")
        void flatMapAppliesFunctionToRight() {
            Function<Integer, Either<String, String>> mapper = i -> Either.right("Value: " + i);

            Either<String, String> result = rightInstance.flatMap(mapper);
            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isEqualTo("Value: 123");

            // Test returning Left from flatMap
            Function<Integer, Either<String, String>> errorMapper =
                    i -> Either.left("Converted to error");
            Either<String, String> errorResult = rightInstance.flatMap(errorMapper);
            assertThat(errorResult.isLeft()).isTrue();
            assertThat(errorResult.getLeft()).isEqualTo("Converted to error");
        }

        @Test
        @DisplayName("flatMap() preserves Left instances unchanged")
        void flatMapPreservesLeftInstances() {
            Function<Integer, Either<String, String>> mapper = i -> Either.right("Value: " + i);

            Either<String, String> result = leftInstance.flatMap(mapper);
            assertThat(result).isSameAs(leftInstance);
            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isEqualTo(leftValue);
        }

        @Test
        @DisplayName("flatMap() validates parameters using standardised validation")
        void flatMapValidatesParameters() {
            ValidationTestBuilder.create()
                    .assertFlatMapperNull(() -> rightInstance.flatMap(null), Either.class, Operation.FLAT_MAP)
                    .assertFlatMapperNull(() -> leftInstance.flatMap(null), Either.class, Operation.FLAT_MAP)
                    .execute();
        }

        @Test
        @DisplayName("flatMap() validates non-null results")
        void flatMapValidatesNonNullResults() {
            Function<Integer, Either<String, String>> nullReturningMapper = i -> null;

            // The FunctionValidator.requireNonNullResult throws KindUnwrapException, not
            // NullPointerException
            assertThatThrownBy(() -> rightInstance.flatMap(nullReturningMapper))
                    .isInstanceOf(KindUnwrapException.class)
                    .hasMessageContaining("Function in flatMap returned null, which is not allowed");
        }

        @Test
        @DisplayName("flatMap() supports complex chaining patterns")
        void flatMapSupportsComplexChaining() {
            // Success chain
            Either<String, Integer> start = Either.right(10);
            Either<String, String> result =
                    start
                            .flatMap(i -> Either.right(i * 2))
                            .flatMap(i -> Either.right("Value: " + i))
                            .flatMap(s -> Either.right(s.toUpperCase()));
            assertThat(result.getRight()).isEqualTo("VALUE: 20");

            // Failure in middle of chain
            Either<String, String> failureResult =
                    start
                            .flatMap(i -> Either.right(i * 2))
                            .flatMap(i -> Either.left("Error occurred"))
                            .flatMap(i -> Either.right("Should not reach"));
            assertThat(failureResult.isLeft()).isTrue();
            assertThat(failureResult.getLeft()).isEqualTo("Error occurred");

            // Mixed operations
            Either<String, Integer> mixedResult =
                    start
                            .map(i -> i + 5) // 15
                            .flatMap(i -> Either.right(i * 2)) // 30
                            .map(i -> i - 10); // 20
            assertThat(mixedResult.getRight()).isEqualTo(20);
        }

        @Test
        @DisplayName("flatMap() handles exception propagation")
        void flatMapHandlesExceptionPropagation() {
            RuntimeException testException = new RuntimeException("Test exception: flatMap test");
            Function<Integer, Either<String, String>> throwingMapper =
                    TestFunctions.throwingFunction(testException);

            // Right instances should propagate exceptions
            assertThatThrownBy(() -> rightInstance.flatMap(throwingMapper)).isSameAs(testException);

            // Left instances should not call mapper
            Either<String, String> leftResult = leftInstance.flatMap(throwingMapper);
            assertThat(leftResult).isSameAs(leftInstance);
        }
    }

    @Nested
    @DisplayName("toString() and Object Methods")
    class ObjectMethodsTests {

        @Test
        @DisplayName("toString() provides meaningful representations")
        void toStringProvidesMeaningfulRepresentations() {
            // Left toString
            assertThat(leftInstance.toString()).isEqualTo("Left(" + leftValue + ")");
            assertThat(leftNullInstance.toString()).isEqualTo("Left(null)");

            // Right toString
            assertThat(rightInstance.toString()).isEqualTo("Right(" + rightValue + ")");
            assertThat(rightNullInstance.toString()).isEqualTo("Right(null)");

            // Complex types
            Either<List<String>, Integer> complexLeft = Either.left(List.of("a", "b"));
            assertThat(complexLeft.toString()).isEqualTo("Left([a, b])");

            Either<String, List<Integer>> complexRight = Either.right(List.of(1, 2, 3));
            assertThat(complexRight.toString()).isEqualTo("Right([1, 2, 3])");
        }

        @Test
        @DisplayName("equals() and hashCode() work correctly")
        void equalsAndHashCodeWorkCorrectly() {
            // Same instances
            assertThat(leftInstance).isEqualTo(leftInstance);
            assertThat(rightInstance).isEqualTo(rightInstance);

            // Equal instances
            Either<String, Integer> anotherLeft = Either.left(leftValue);
            Either<String, Integer> anotherRight = Either.right(rightValue);
            assertThat(leftInstance).isEqualTo(anotherLeft);
            assertThat(rightInstance).isEqualTo(anotherRight);
            assertThat(leftInstance.hashCode()).isEqualTo(anotherLeft.hashCode());
            assertThat(rightInstance.hashCode()).isEqualTo(anotherRight.hashCode());

            // Different instances
            assertThat(leftInstance).isNotEqualTo(rightInstance);
            assertThat(leftInstance).isNotEqualTo(Either.left("different"));
            assertThat(rightInstance).isNotEqualTo(Either.right(999));

            // Null handling
            assertThat(leftNullInstance).isEqualTo(Either.left(null));
            assertThat(rightNullInstance).isEqualTo(Either.right(null));
            assertThat(leftNullInstance).isNotEqualTo(rightNullInstance);
        }
    }

    @Nested
    @DisplayName("Advanced Usage Patterns")
    class AdvancedUsagePatterns {

        @Test
        @DisplayName("Either as functor maintains structure")
        void eitherAsFunctorMaintainsStructure() {
            // Multiple transformations should maintain Either structure
            Either<String, Integer> start = Either.right(5);

            Either<String, Double> result =
                    start.map(i -> i * 2.0).map(d -> d + 0.5).map(d -> Math.sqrt(d));

            assertThat(result.isRight()).isTrue();
            assertThat(result.getRight()).isCloseTo(Math.sqrt(10.5), within(0.001));
        }

        @Test
        @DisplayName("Either for railway oriented programming")
        void eitherForRailwayOrientedProgramming() {
            // Simulate a pipeline where each step can fail
            Function<String, Either<String, Integer>> parseInteger =
                    s -> {
                        try {
                            return Either.right(Integer.parseInt(s));
                        } catch (NumberFormatException e) {
                            return Either.left("Invalid number: " + s);
                        }
                    };

            Function<Integer, Either<String, Double>> squareRoot =
                    i -> {
                        if (i < 0) {
                            return Either.left("Cannot take square root of negative number: " + i);
                        }
                        return Either.right(Math.sqrt(i));
                    };

            Function<Double, Either<String, String>> formatResult =
                    d -> {
                        if (d > 100) {
                            return Either.left("Result too large: " + d);
                        }
                        return Either.right(String.format("%.2f", d));
                    };

            Either<String, String> success =
                    Either.<String, String>right("16")
                            .flatMap(parseInteger)
                            .flatMap(squareRoot)
                            .flatMap(formatResult);
            assertThat(success.getRight()).isEqualTo("4.00");

            // Failure paths
            Either<String, String> parseFailure =
                    Either.<String, String>right("not-a-number")
                            .flatMap(parseInteger)
                            .flatMap(squareRoot)
                            .flatMap(formatResult);
            assertThat(parseFailure.getLeft()).contains("Invalid number");

            Either<String, String> negativeFailure =
                    Either.<String, String>right("-4")
                            .flatMap(parseInteger)
                            .flatMap(squareRoot)
                            .flatMap(formatResult);
            assertThat(negativeFailure.getLeft()).contains("Cannot take square root");
        }

        @Test
        @DisplayName("Either with resource management patterns")
        void eitherWithResourceManagement() {
            // Simulate resource acquisition and cleanup
            record Resource(String name, boolean open) {
                Resource close() {
                    return new Resource(name, false);
                }
            }

            Function<String, Either<String, Resource>> openResource =
                    name -> {
                        if (name.equals("invalid")) {
                            return Either.left("Cannot open resource: " + name);
                        }
                        return Either.right(new Resource(name, true));
                    };

            Function<Resource, Either<String, String>> processResource =
                    resource -> {
                        if (!resource.open()) {
                            return Either.left("Resource is closed");
                        }
                        if (resource.name().equals("fail")) {
                            return Either.left("Processing failed");
                        }
                        return Either.right("Processed: " + resource.name());
                    };

            // Success case
            Either<String, String> result =
                    openResource
                            .apply("test")
                            .flatMap(
                                    resource -> {
                                        Either<String, String> processed = processResource.apply(resource);
                                        Resource closed = resource.close();
                                        assertThat(closed.open()).isFalse();
                                        return processed;
                                    });

            assertThat(result.getRight()).isEqualTo("Processed: test");

            // Failure case
            Either<String, String> failureResult =
                    openResource
                            .apply("fail")
                            .flatMap(
                                    resource -> {
                                        Either<String, String> processed = processResource.apply(resource);
                                        Resource closed = resource.close();
                                        assertThat(closed.open()).isFalse();
                                        return processed;
                                    });

            assertThat(failureResult.getLeft()).isEqualTo("Processing failed");
        }

        @Test
        @DisplayName("Either pattern matching with switch expressions")
        void eitherPatternMatchingWithSwitch() {
            // Test exhaustive pattern matching
            Function<Either<String, Integer>, String> processEither =
                    either ->
                            switch (either) {
                                case Either.Left<String, Integer>(var error) -> "Error: " + error;
                                case Either.Right<String, Integer>(var value) -> "Success: " + value;
                            };

            assertThat(processEither.apply(leftInstance)).isEqualTo("Error: " + leftValue);
            assertThat(processEither.apply(rightInstance)).isEqualTo("Success: " + rightValue);
            assertThat(processEither.apply(leftNullInstance)).isEqualTo("Error: null");
            assertThat(processEither.apply(rightNullInstance)).isEqualTo("Success: null");

            // Test with nested pattern matching
            Either<Either<String, Integer>, Boolean> nested = Either.left(Either.right(42));
            String nestedResult =
                    switch (nested) {
                        case Either.Left<Either<String, Integer>, Boolean>(var innerEither) ->
                                switch (innerEither) {
                                    case Either.Left<String, Integer>(var error) -> "Nested error: " + error;
                                    case Either.Right<String, Integer>(var value) -> "Nested value: " + value;
                                };
                        case Either.Right<Either<String, Integer>, Boolean>(var bool) -> "Boolean: " + bool;
                    };
            assertThat(nestedResult).isEqualTo("Nested value: 42");
        }
    }

    @Nested
    @DisplayName("Performance and Memory Characteristics")
    class PerformanceAndMemoryTests {

        @Test
        @DisplayName("Either operations have predictable performance")
        void eitherOperationsHavePredictablePerformance() {
            // Test that basic operations are fast
            Either<String, Integer> test = Either.right(42);

            // Simple operations should be very fast
            long start = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                test.map(x -> x + 1).flatMap(x -> Either.right(x * 2)).isRight();
            }
            long duration = System.nanoTime() - start;

            // Should complete in reasonable time (less than 100ms for 10k ops)
            assertThat(duration).isLessThan(100_000_000L);
        }

        @Test
        @DisplayName("Left instances are reused efficiently")
        void leftInstancesAreReusedEfficiently() {
            Either<String, Integer> left = Either.left("error");

            // map should return same instance for Left
            Either<String, String> mapped = left.map(Object::toString);
            assertThat(mapped).isSameAs(left);

            // Multiple map operations should all return same instance
            Either<String, Boolean> multiMapped =
                    left.map(Object::toString).map(String::length).map(len -> len > 0);
            assertThat(multiMapped).isSameAs(left);

            // flatMap should also return same instance for Left
            Either<String, String> flatMapped = left.flatMap(x -> Either.right("not reached"));
            assertThat(flatMapped).isSameAs(left);
        }

        @Test
        @DisplayName("Memory usage is reasonable for large chains")
        void memoryUsageIsReasonableForLargeChains() {
            // Test that long chains don't create excessive rubbish
            Either<String, Integer> start = Either.right(1);

            Either<String, Integer> result = start;
            for (int i = 0; i < 1000; i++) {
                final int increment = i;
                result = result.map(x -> x + increment);
            }

            // Should complete without memory issues
            assertThat(result.getRight()).isEqualTo(1 + (999 * 1000) / 2);

            // Left chains should be even more efficient
            Either<String, Integer> leftStart = Either.left("error");
            Either<String, Integer> leftResult = leftStart;
            for (int i = 0; i < 1000; i++) {
                int finalI = i;
                leftResult = leftResult.map(x -> x + finalI);
            }

            // Should be same instance throughout
            assertThat(leftResult).isSameAs(leftStart);
        }
    }

    @Nested
    @DisplayName("Type Safety and Variance")
    class TypeSafetyAndVarianceTests {

        @Test
        @DisplayName("Either maintains type safety across operations")
        void eitherMaintainsTypeSafety() {
            // Test covariance in Right type
            Either<String, Number> numberEither = Either.right(42);
            Either<String, Integer> intEither = numberEither.flatMap(n -> Either.right(n.intValue()));
            assertThat(intEither.getRight()).isEqualTo(42);

            Either<Exception, String> exceptionEither = Either.left(new RuntimeException("test"));

            Either<Exception, String> processedEither =
                    exceptionEither.flatMap(s -> Either.right(s.toUpperCase()));
            assertThat(processedEither.getLeft()).isInstanceOf(RuntimeException.class);

            Either<RuntimeException, String> runtimeEither;
            if (exceptionEither.isLeft() && exceptionEither.getLeft() instanceof RuntimeException) {
                runtimeEither = Either.left((RuntimeException) exceptionEither.getLeft());
            } else {
                runtimeEither = Either.right("default");
            }

            Either<RuntimeException, String> processedRuntime =
                    runtimeEither.flatMap(s -> Either.right(s.toUpperCase()));
            assertThat(processedRuntime.getLeft()).isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("Either works with complex generic types")
        void eitherWorksWithComplexGenericTypes() {
            // Nested generics
            Either<List<String>, List<Integer>> complexEither = Either.right(List.of(1, 2, 3));

            Either<List<String>, Integer> summed =
                    complexEither.map(list -> list.stream().mapToInt(Integer::intValue).sum());
            assertThat(summed.getRight()).isEqualTo(6);

            // Map transformations
            Either<String, java.util.Map<String, Integer>> mapEither =
                    Either.right(java.util.Map.of("a", 1, "b", 2));

            Either<String, java.util.Set<String>> keySet = mapEither.map(map -> map.keySet());
            assertThat(keySet.getRight()).containsExactlyInAnyOrder("a", "b");
        }

        @Test
        @DisplayName("Either handles wildcard types correctly")
        void eitherHandlesWildcardTypesCorrectly() {
            // Test with wildcards
            Either<? extends Exception, ? extends Number> wildcardEither = Either.right(42.0);

            // Should be able to call methods that don't require specific bounds
            assertThat(wildcardEither.isRight()).isTrue();

            // Folding should work with appropriate bounds
            String result =
                    wildcardEither.fold(
                            ex -> "Exception: " + ex.getMessage(), num -> "Number: " + num.doubleValue());
            assertThat(result).isEqualTo("Number: 42.0");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCasesAndBoundaryConditions {

        @Test
        @DisplayName("Either handles extreme values correctly")
        void eitherHandlesExtremeValuesCorrectly() {
            // Very large strings
            String largeString = "x".repeat(10000);
            Either<String, String> largeRight = Either.right(largeString);
            assertThat(largeRight.map(String::length).getRight()).isEqualTo(10000);

            // Maximum/minimum integer values
            Either<String, Integer> maxInt = Either.right(Integer.MAX_VALUE);
            Either<String, Long> promoted = maxInt.map(i -> i.longValue() + 1);
            assertThat(promoted.getRight()).isEqualTo((long) Integer.MAX_VALUE + 1);

            // Very nested structures
            Either<String, Either<String, Either<String, Integer>>> tripleNested =
                    Either.right(Either.right(Either.right(42)));

            Either<String, Integer> flattened =
                    tripleNested.flatMap(inner -> inner.flatMap(innerInner -> innerInner));
            assertThat(flattened.getRight()).isEqualTo(42);
        }

        @Test
        @DisplayName("Either operations are stack-safe for deep recursion")
        void eitherOperationsAreStackSafe() {
            // Test that deep map chains don't cause stack overflow
            Either<String, Integer> start = Either.right(0);

            // Create a very deep chain (but not infinite)
            Either<String, Integer> result = start;
            for (int i = 0; i < 10000; i++) {
                result = result.map(x -> x + 1);
            }

            assertThat(result.getRight()).isEqualTo(10000);

            // Test with flatMap chains
            Either<String, Integer> flatMapResult = start;
            for (int i = 0; i < 1000; i++) {
                flatMapResult = flatMapResult.flatMap(x -> Either.right(x + 1));
            }

            assertThat(flatMapResult.getRight()).isEqualTo(1000);
        }

        @Test
        @DisplayName("Either handles concurrent modifications safely")
        void eitherHandlesConcurrentModificationsSafely() {
            // Either instances should be immutable and thread-safe
            Either<String, java.util.List<Integer>> listEither =
                    Either.right(new java.util.ArrayList<>(List.of(1, 2, 3)));

            // Even if the contained list is mutable, Either operations should be safe
            Either<String, Integer> sizeEither = listEither.map(List::size);
            assertThat(sizeEither.getRight()).isEqualTo(3);

            // Modifying original list shouldn't affect Either operations
            listEither.getRight().add(4);
            Either<String, Integer> newSizeEither = listEither.map(List::size);
            assertThat(newSizeEither.getRight()).isEqualTo(4);
        }

        @Test
        @DisplayName("Either maintains referential transparency")
        void eitherMaintainsReferentialTransparency() {
            // Same operations should always produce same results
            Either<String, Integer> either = Either.right(42);
            Function<Integer, String> transform = i -> "value:" + i;

            Either<String, String> result1 = either.map(transform);
            Either<String, String> result2 = either.map(transform);

            assertThat(result1).isEqualTo(result2);
            assertThat(result1.getRight()).isEqualTo(result2.getRight());

            // This should be true for all Either operations
            Either<String, String> flatMapResult1 = either.flatMap(i -> Either.right("flat:" + i));
            Either<String, String> flatMapResult2 = either.flatMap(i -> Either.right("flat:" + i));

            assertThat(flatMapResult1).isEqualTo(flatMapResult2);
        }
    }
}