// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.maybe.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.OR_ELSE_GET;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.builders.ValidationTestBuilder;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.higherkindedj.hkt.util.validation.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for Maybe using standardised patterns.
 *
 * <p>Coverage includes factory methods, Functor/Monad operations, utility methods, object methods,
 * algebraic laws, and performance characteristics.
 */
@DisplayName("Maybe<T> Complete Test Suite")
class MaybeTest extends MaybeTestBase {

  private final String justValue = "Present Value";
  private final Maybe<String> justInstance = Maybe.just(justValue);
  private final Maybe<String> nothingInstance = Maybe.nothing();
  private final MaybeMonad MONAD = MaybeMonad.INSTANCE;

  @Nested
  @DisplayName("Complete Type Class Test Suite")
  class CompleteTypeClassTestSuite {

    @Test
    @DisplayName("Run complete Monad test pattern")
    void runCompleteMonadTestPattern() {
      Kind<MaybeKind.Witness, String> stringKind = MAYBE.widen(justInstance);
      Kind<MaybeKind.Witness, String> stringKind2 = MAYBE.widen(Maybe.just("Another"));

      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<String>instance(MONAD)
          .<Integer>withKind(stringKind)
          .withMonadOperations(
              stringKind2,
              stringToIntMapper(),
              stringToIntFlatMapper(),
              stringToIntFunctionKind(),
              stringCombiningFunction())
          .withLawsTesting(justValue, stringTestFunction(), intChainFunction(), equalityChecker)
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withApFrom(MaybeMonad.class)
          .withFlatMapFrom(MaybeMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Functor test pattern")
    void runCompleteFunctorTestPattern() {
      Kind<MaybeKind.Witness, String> stringKind = MAYBE.widen(justInstance);

      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<String>instance(MONAD)
          .<Integer>withKind(stringKind)
          .withMapper(stringToIntMapper())
          .withSecondMapper(intToStringMapper())
          .withEqualityChecker(equalityChecker)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Maybe core type tests")
    void runCompleteMaybeCoreTypeTests() {
      CoreTypeTest.<String>maybe(Maybe.class)
          .withJust(justInstance)
          .withNothing(nothingInstance)
          .withMapper(stringToIntMapper())
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withFlatMapFrom(MaybeMonad.class)
          .testAll();
    }

    @Test
    @DisplayName("Run complete Maybe Selective core type tests - operations only")
    void runCompleteMaybeSelectiveCoreTypeTestsOperationsOnly() {
      // Create Choice instances for Selective testing
      Choice<String, Integer> choiceLeft = Selective.left(justValue);
      Choice<String, Integer> choiceRight = Selective.right(DEFAULT_INT_VALUE);

      Maybe<Choice<String, Integer>> maybeChoiceLeft = Maybe.just(choiceLeft);
      Maybe<Choice<String, Integer>> maybeChoiceRight = Maybe.just(choiceRight);
      Maybe<Boolean> maybeTrue = Maybe.just(true);
      Maybe<Boolean> maybeFalse = Maybe.just(false);

      CoreTypeTest.<String>maybe(Maybe.class)
          .withJust(justInstance)
          .withNothing(nothingInstance)
          .withMapper(stringToIntMapper())
          .withSelectiveOperations(maybeChoiceLeft, maybeChoiceRight, maybeTrue, maybeFalse)
          .withHandlers(String::length, String::length, i -> i * 2)
          .testOperations();
    }

    @Test
    @DisplayName("Run complete Maybe Selective core type tests - with validation")
    void runCompleteMaybeSelectiveCoreTypeTestsWithValidation() {
      // Create Choice instances for Selective testing
      Choice<String, Integer> choiceLeft = Selective.left(justValue);
      Choice<String, Integer> choiceRight = Selective.right(DEFAULT_INT_VALUE);

      Maybe<Choice<String, Integer>> maybeChoiceLeft = Maybe.just(choiceLeft);
      Maybe<Choice<String, Integer>> maybeChoiceRight = Maybe.just(choiceRight);
      Maybe<Boolean> maybeTrue = Maybe.just(true);
      Maybe<Boolean> maybeFalse = Maybe.just(false);

      CoreTypeTest.<String>maybe(Maybe.class)
          .withJust(justInstance)
          .withNothing(nothingInstance)
          .withMapper(stringToIntMapper())
          .withSelectiveOperations(maybeChoiceLeft, maybeChoiceRight, maybeTrue, maybeFalse)
          .withHandlers(String::length, String::length, i -> i * 2)
          .configureValidation()
          .useSelectiveInheritanceValidation()
          .withSelectFrom(MaybeSelective.class)
          .withBranchFrom(MaybeSelective.class)
          .withWhenSFrom(MaybeSelective.class)
          .withIfSFrom(MaybeSelective.class)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Individual Type Class Components")
  class IndividualTypeClassComponents {

    @Test
    @DisplayName("Test Functor operations only")
    void testFunctorOperationsOnly() {
      Kind<MaybeKind.Witness, String> stringKind = MAYBE.widen(justInstance);

      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<String>instance(MONAD)
          .<Integer>withKind(stringKind)
          .withMapper(stringToIntMapper())
          .testOperations();
    }

    @Test
    @DisplayName("Test Functor validations only")
    void testFunctorValidationsOnly() {
      CoreTypeTest.<String>maybe(Maybe.class)
          .withJust(justInstance)
          .withNothing(nothingInstance)
          .withMapper(stringToIntMapper())
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withFlatMapFrom(MaybeMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test Functor exception propagation only")
    void testFunctorExceptionPropagationOnly() {
      RuntimeException testException = new RuntimeException("Test exception: functor test");
      Function<String, Integer> throwingMapper = TestFunctions.throwingFunction(testException);

      Kind<MaybeKind.Witness, String> stringKind = MAYBE.widen(justInstance);
      assertThatThrownBy(() -> MONAD.map(throwingMapper, stringKind)).isSameAs(testException);

      Kind<MaybeKind.Witness, String> nothingKind = MAYBE.widen(nothingInstance);
      assertThatCode(() -> MONAD.map(throwingMapper, nothingKind)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test Functor laws only")
    void testFunctorLawsOnly() {
      Kind<MaybeKind.Witness, String> stringKind = MAYBE.widen(justInstance);

      TypeClassTest.<MaybeKind.Witness>functor(MaybeFunctor.class)
          .<String>instance(MONAD)
          .<Integer>withKind(stringKind)
          .withMapper(stringToIntMapper())
          .withSecondMapper(intToStringMapper())
          .withEqualityChecker(equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }

    @Test
    @DisplayName("Test Monad operations only")
    void testMonadOperationsOnly() {
      Kind<MaybeKind.Witness, String> stringKind = MAYBE.widen(justInstance);
      Kind<MaybeKind.Witness, String> stringKind2 = MAYBE.widen(Maybe.just("Another"));

      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<String>instance(MONAD)
          .<Integer>withKind(stringKind)
          .withMonadOperations(
              stringKind2,
              stringToIntMapper(),
              stringToIntFlatMapper(),
              stringToIntFunctionKind(),
              stringCombiningFunction())
          .testOperations();
    }

    @Test
    @DisplayName("Test Monad validations only with full hierarchy")
    void testMonadValidationsOnly() {
      CoreTypeTest.<String>maybe(Maybe.class)
          .withJust(justInstance)
          .withNothing(nothingInstance)
          .withMapper(stringToIntMapper())
          .configureValidation()
          .useInheritanceValidation()
          .withMapFrom(MaybeFunctor.class)
          .withFlatMapFrom(MaybeMonad.class)
          .testValidations();
    }

    @Test
    @DisplayName("Test Monad exception propagation only")
    void testMonadExceptionPropagationOnly() {
      RuntimeException testException = new RuntimeException("Test exception: monad test");
      Function<String, Kind<MaybeKind.Witness, Integer>> throwingFlatMapper =
          TestFunctions.throwingFunction(testException);

      Kind<MaybeKind.Witness, String> stringKind = MAYBE.widen(justInstance);
      assertThatThrownBy(() -> MONAD.flatMap(throwingFlatMapper, stringKind))
          .isSameAs(testException);

      Kind<MaybeKind.Witness, String> nothingKind = MAYBE.widen(nothingInstance);
      assertThatCode(() -> MONAD.flatMap(throwingFlatMapper, nothingKind))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Test Monad laws only")
    void testMonadLawsOnly() {
      Kind<MaybeKind.Witness, String> stringKind = MAYBE.widen(justInstance);
      Kind<MaybeKind.Witness, String> stringKind2 = MAYBE.widen(Maybe.just("Another"));

      TypeClassTest.<MaybeKind.Witness>monad(MaybeMonad.class)
          .<String>instance(MONAD)
          .<Integer>withKind(stringKind)
          .withMonadOperations(
              stringKind2,
              stringToIntMapper(),
              stringToIntFlatMapper(),
              stringToIntFunctionKind(),
              stringCombiningFunction())
          .withLawsTesting(justValue, stringTestFunction(), intChainFunction(), equalityChecker)
          .selectTests()
          .onlyLaws()
          .test();
    }

    @Test
    @DisplayName("Test Selective operations only")
    void testSelectiveOperationsOnly() {
      // Create Choice instances for Selective testing
      Choice<String, Integer> choiceLeft = Selective.left(justValue);
      Choice<String, Integer> choiceRight = Selective.right(DEFAULT_INT_VALUE);

      Maybe<Choice<String, Integer>> maybeChoiceLeft = Maybe.just(choiceLeft);
      Maybe<Choice<String, Integer>> maybeChoiceRight = Maybe.just(choiceRight);
      Maybe<Boolean> maybeTrue = Maybe.just(true);
      Maybe<Boolean> maybeFalse = Maybe.just(false);

      CoreTypeTest.<String>maybe(Maybe.class)
          .withJust(justInstance)
          .withNothing(nothingInstance)
          .withMapper(stringToIntMapper())
          .withSelectiveOperations(maybeChoiceLeft, maybeChoiceRight, maybeTrue, maybeFalse)
          .withHandlers(String::length, String::length, i -> i * 2)
          .testOperations();
    }

    @Test
    @DisplayName("Test Selective validations only")
    void testSelectiveValidationsOnly() {
      // Create Choice instances for Selective testing
      Choice<String, Integer> choiceLeft = Selective.left(justValue);
      Choice<String, Integer> choiceRight = Selective.right(DEFAULT_INT_VALUE);

      Maybe<Choice<String, Integer>> maybeChoiceLeft = Maybe.just(choiceLeft);
      Maybe<Choice<String, Integer>> maybeChoiceRight = Maybe.just(choiceRight);
      Maybe<Boolean> maybeTrue = Maybe.just(true);
      Maybe<Boolean> maybeFalse = Maybe.just(false);

      CoreTypeTest.<String>maybe(Maybe.class)
          .withJust(justInstance)
          .withNothing(nothingInstance)
          .withMapper(stringToIntMapper())
          .withSelectiveOperations(maybeChoiceLeft, maybeChoiceRight, maybeTrue, maybeFalse)
          .withHandlers(String::length, String::length, i -> i * 2)
          .configureValidation()
          .useSelectiveInheritanceValidation()
          .withSelectFrom(MaybeSelective.class)
          .withBranchFrom(MaybeSelective.class)
          .withWhenSFrom(MaybeSelective.class)
          .withIfSFrom(MaybeSelective.class)
          .testValidations();
    }
  }

  @Nested
  @DisplayName("Factory Methods - Complete Coverage")
  class FactoryMethods {

    @Test
    @DisplayName("just() creates correct Just instances with all value types")
    void justCreatesCorrectInstances() {
      assertThatMaybe(justInstance).isJust().hasValue(justValue);

      List<Integer> list = List.of(1, 2, 3);
      Maybe<List<Integer>> listJust = Maybe.just(list);
      assertThatMaybe(listJust).isJust().hasValueSatisfying(l -> assertThat(l).isSameAs(list));

      Maybe<Boolean> boolJust = Maybe.just(true);
      assertThatMaybe(boolJust).isJust().hasValue(true);
    }

    @Test
    @DisplayName("just() validates non-null requirement")
    void justValidatesNonNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Maybe.just(null))
          .withMessageContaining("Maybe.just value cannot be null");
    }

    @Test
    @DisplayName("nothing() returns singleton Nothing instance")
    void nothingReturnsSingleton() {
      assertThatMaybe(nothingInstance).isNothing();

      Maybe<Integer> nothingInt = Maybe.nothing();
      assertThat(nothingInstance).isSameAs(nothingInt);
    }

    @Test
    @DisplayName("fromNullable() creates Just for non-null values")
    void fromNullableCreatesJustForNonNull() {
      Maybe<String> fromNullableJust = Maybe.fromNullable(justValue);
      assertThatMaybe(fromNullableJust).isJust().hasValue(justValue);

      Maybe<String> emptyJust = Maybe.fromNullable("");
      assertThatMaybe(emptyJust).isJust().hasValue("");
    }

    @Test
    @DisplayName("fromNullable() creates Nothing for null values")
    void fromNullableCreatesNothingForNull() {
      Maybe<String> fromNullableNothing = Maybe.fromNullable(null);
      assertThatMaybe(fromNullableNothing).isNothing();
    }
  }

  @Nested
  @DisplayName("Getter Methods - Comprehensive Edge Cases")
  class GetterMethodsTests {

    @Test
    @DisplayName("get() works correctly on all Just variations")
    void getWorksCorrectly() {
      assertThat(justInstance.get()).isEqualTo(justValue);

      List<String> list = List.of("a", "b", "c");
      Maybe<List<String>> listJust = Maybe.just(list);
      assertThat(listJust.get()).isSameAs(list);

      Maybe<Integer> nested = Maybe.just(99);
      Maybe<Maybe<Integer>> nestedJust = Maybe.just(nested);
      assertThat(nestedJust.get()).isSameAs(nested);
    }

    @Test
    @DisplayName("get() throws correct exceptions on Nothing instances")
    void getThrowsOnNothing() {
      assertThatThrownBy(nothingInstance::get).hasMessageContaining("Cannot call get() on Nothing");

      assertThatThrownBy(() -> Maybe.nothing().get())
          .hasMessageContaining("Cannot call get() on Nothing");

      assertThatThrownBy(() -> Maybe.fromNullable(null).get())
          .hasMessageContaining("Cannot call get() on Nothing");
    }

    @Test
    @DisplayName("isJust() and isNothing() work correctly")
    void isJustAndIsNothingWorkCorrectly() {
      assertThatMaybe(justInstance).isJust();
      assertThatMaybe(nothingInstance).isNothing();

      assertThatMaybe(Maybe.fromNullable(justValue)).isJust();
      assertThatMaybe(Maybe.fromNullable(null)).isNothing();
    }
  }

  @Nested
  @DisplayName("orElse / orElseGet - Complete Validation")
  class OrElseTests {

    private final String defaultValue = "Default";

    @Test
    @DisplayName("orElse() returns value for Just instances")
    void orElseReturnsValueForJust() {
      assertThat(justInstance.orElse(defaultValue)).isEqualTo(justValue);

      List<String> list = List.of("a", "b");
      List<String> defaultList = List.of("x", "y");
      Maybe<List<String>> listJust = Maybe.just(list);
      assertThat(listJust.orElse(defaultList)).isSameAs(list);
    }

    @Test
    @DisplayName("orElse() returns default for Nothing instances")
    void orElseReturnsDefaultForNothing() {
      assertThat(nothingInstance.orElse(defaultValue)).isEqualTo(defaultValue);
      assertThat(nothingInstance.orElse(null)).isNull();
    }

    @Test
    @DisplayName("orElseGet() returns value for Just without calling supplier")
    void orElseGetReturnsValueForJustWithoutCallingSupplier() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> trackingSupplier =
          () -> {
            supplierCalled.set(true);
            return defaultValue;
          };

      assertThat(justInstance.orElseGet(trackingSupplier)).isEqualTo(justValue);
      assertThat(supplierCalled).isFalse();
    }

    @Test
    @DisplayName("orElseGet() calls supplier and returns result for Nothing")
    void orElseGetCallsSupplierForNothing() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> trackingSupplier =
          () -> {
            supplierCalled.set(true);
            return defaultValue;
          };

      assertThat(nothingInstance.orElseGet(trackingSupplier)).isEqualTo(defaultValue);
      assertThat(supplierCalled).isTrue();
    }

    @Test
    @DisplayName("orElseGet() validates null supplier using ValidationTestBuilder")
    void orElseGetValidatesNullSupplier() {
      ValidationTestBuilder.create()
          .assertFunctionNull(
              () -> nothingInstance.orElseGet(null), "otherSupplier", Maybe.class, OR_ELSE_GET)
          .execute();
    }

    @Test
    @DisplayName("orElseGet() doesn't validate null supplier for Just")
    void orElseGetDoesNotValidateNullSupplierForJust() {
      assertThatCode(() -> justInstance.orElseGet(null)).doesNotThrowAnyException();
      assertThat(justInstance.orElseGet(null)).isEqualTo(justValue);
    }
  }

  @Nested
  @DisplayName("Map Method - Comprehensive Testing")
  class MapMethodTests {

    @Test
    @DisplayName("map() applies function to Just values")
    void mapAppliesFunctionToJust() {
      Maybe<Integer> result = justInstance.map(String::length);
      assertThatMaybe(result).isJust().hasValue(justValue.length());

      Maybe<List<Character>> listResult =
          justInstance.map(s -> s.chars().mapToObj(c -> (char) c).toList());
      assertThatMaybe(listResult)
          .isJust()
          .hasValueSatisfying(l -> assertThat(l).hasSize(justValue.length()));

      Maybe<String> nullResult = justInstance.map(s -> null);
      assertThatMaybe(nullResult).isNothing();
    }

    @Test
    @DisplayName("map() preserves Nothing instances unchanged")
    void mapPreservesNothingInstances() {
      Maybe<Integer> result = nothingInstance.map(String::length);
      assertThat(result).isSameAs(nothingInstance);

      Maybe<List<String>> complexResult = nothingInstance.map(s -> List.of(s, s.toUpperCase()));
      assertThat(complexResult).isSameAs(nothingInstance);
    }

    @Test
    @DisplayName("map() validates null mapper using ValidationTestBuilder")
    void mapValidatesNullMapper() {
      ValidationTestBuilder.create()
          .assertMapperNull(() -> justInstance.map(null), "mapper", Just.class, Operation.MAP)
          .assertMapperNull(() -> nothingInstance.map(null), "mapper", Maybe.class, Operation.MAP)
          .execute();
    }

    @Test
    @DisplayName("map() handles exception propagation and chaining")
    void mapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: map test");
      Function<String, Integer> throwingMapper = TestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> justInstance.map(throwingMapper)).isSameAs(testException);

      Maybe<Integer> nothingResult = nothingInstance.map(throwingMapper);
      assertThat(nothingResult).isSameAs(nothingInstance);

      Maybe<String> start = Maybe.just("hello");
      Maybe<String> chainResult =
          start.map(String::toUpperCase).map(s -> s + "!").map(s -> s.repeat(2));
      assertThatMaybe(chainResult).isJust().hasValue("HELLO!HELLO!");

      Maybe<String> nothingStart = Maybe.nothing();
      Maybe<String> nothingChainResult =
          nothingStart.map(String::toUpperCase).map(s -> s + "!").map(s -> s.repeat(2));
      assertThatMaybe(nothingChainResult).isNothing();
    }

    @Test
    @DisplayName("map() handles null-returning functions")
    void mapHandlesNullReturningFunctions() {
      Function<String, Integer> nullReturningMapper = TestFunctions.nullReturningFunction();
      Maybe<Integer> result = justInstance.map(nullReturningMapper);
      assertThatMaybe(result).isNothing();
    }
  }

  @Nested
  @DisplayName("FlatMap Method - Comprehensive Monadic Testing")
  class FlatMapMethodTests {

    @Test
    @DisplayName("flatMap() applies function to Just values")
    void flatMapAppliesFunctionToJust() {
      Function<String, Maybe<Integer>> mapper = s -> Maybe.just(s.length());
      Maybe<Integer> result = justInstance.flatMap(mapper);
      assertThatMaybe(result).isJust().hasValue(justValue.length());

      Function<String, Maybe<Integer>> nothingMapper = s -> Maybe.nothing();
      Maybe<Integer> nothingResult = justInstance.flatMap(nothingMapper);
      assertThatMaybe(nothingResult).isNothing();
    }

    @Test
    @DisplayName("flatMap() preserves Nothing instances unchanged")
    void flatMapPreservesNothingInstances() {
      Function<String, Maybe<Integer>> mapper = s -> Maybe.just(s.length());
      Maybe<Integer> result = nothingInstance.flatMap(mapper);
      assertThat(result).isSameAs(nothingInstance);
    }

    @Test
    @DisplayName("flatMap() validates parameters using ValidationTestBuilder")
    void flatMapValidatesParameters() {
      ValidationTestBuilder.create()
          .assertFlatMapperNull(() -> justInstance.flatMap(null), "mapper", Just.class, FLAT_MAP)
          .assertFlatMapperNull(
              () -> nothingInstance.flatMap(null), "mapper", Maybe.class, FLAT_MAP)
          .execute();
    }

    @Test
    @DisplayName("flatMap() validates non-null results")
    void flatMapValidatesNonNullResults() {
      Function<String, Maybe<Integer>> nullReturningMapper = s -> null;
      assertThatThrownBy(() -> justInstance.flatMap(nullReturningMapper))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("flatMap returned null");
    }

    @Test
    @DisplayName("flatMap() supports complex chaining patterns")
    void flatMapSupportsComplexChaining() {
      Maybe<Integer> start = Maybe.just(10);
      Maybe<String> result =
          start
              .flatMap(i -> Maybe.just(i * 2))
              .flatMap(i -> Maybe.just("Value: " + i))
              .flatMap(s -> Maybe.just(s.toUpperCase()));
      assertThatMaybe(result).isJust().hasValue("VALUE: 20");

      Maybe<String> failureResult =
          start
              .flatMap(i -> Maybe.just(i * 2))
              .flatMap(i -> Maybe.nothing())
              .flatMap(i -> Maybe.just("Should not reach"));
      assertThatMaybe(failureResult).isNothing();

      Maybe<Integer> mixedResult =
          start.map(i -> i + 5).flatMap(i -> Maybe.just(i * 2)).map(i -> i - 10);
      assertThatMaybe(mixedResult).isJust().hasValue(20);
    }

    @Test
    @DisplayName("flatMap() handles exception propagation")
    void flatMapHandlesExceptionPropagation() {
      RuntimeException testException = new RuntimeException("Test exception: flatMap test");
      Function<String, Maybe<Integer>> throwingMapper =
          TestFunctions.throwingFunction(testException);

      assertThatThrownBy(() -> justInstance.flatMap(throwingMapper)).isSameAs(testException);

      Maybe<Integer> nothingResult = nothingInstance.flatMap(throwingMapper);
      assertThat(nothingResult).isSameAs(nothingInstance);
    }
  }

  @Nested
  @DisplayName("toEither Method - Conversion Testing")
  class ToEitherMethodTests {

    private final String errorMessage = "Value was not present";

    @Test
    @DisplayName("toEither(L) returns Right for Just instances")
    void toEitherReturnsRightForJust() {
      Either<String, String> result = justInstance.toEither(errorMessage);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(justValue);
    }

    @Test
    @DisplayName("toEither(L) returns Left for Nothing instances")
    void toEitherReturnsLeftForNothing() {
      Either<String, String> result = nothingInstance.toEither(errorMessage);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("toEither(L) allows null left value")
    void toEitherAllowsNullLeftValue() {
      String nullValue = null;
      Either<String, String> result = nothingInstance.toEither(nullValue);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isNull();
    }

    @Test
    @DisplayName("toEither(Supplier) returns Right for Just without calling supplier")
    void toEitherSupplierReturnsRightForJustWithoutCallingSupplier() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> trackingSupplier =
          () -> {
            supplierCalled.set(true);
            return errorMessage;
          };

      Either<String, String> result = justInstance.toEither(trackingSupplier);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(justValue);
      assertThat(supplierCalled).isFalse();
    }

    @Test
    @DisplayName("toEither(Supplier) returns Left for Nothing and calls supplier")
    void toEitherSupplierReturnsLeftForNothingAndCallsSupplier() {
      AtomicBoolean supplierCalled = new AtomicBoolean(false);
      Supplier<String> trackingSupplier =
          () -> {
            supplierCalled.set(true);
            return errorMessage;
          };

      Either<String, String> result = nothingInstance.toEither(trackingSupplier);
      assertThat(result.isLeft()).isTrue();
      assertThat(result.getLeft()).isEqualTo(errorMessage);
      assertThat(supplierCalled).isTrue();
    }

    @Test
    @DisplayName("toEither(Supplier) validates null supplier for Nothing")
    void toEitherSupplierValidatesNullSupplier() {
      assertThatNullPointerException()
          .isThrownBy(() -> nothingInstance.toEither((Supplier<String>) null))
          .withMessageContaining("leftSupplier");
    }

    @Test
    @DisplayName("toEither(Supplier) does not validate null supplier for Just")
    void toEitherSupplierDoesNotValidateNullSupplierForJust() {
      // For Just, supplier is never called, so null is allowed (consistent with orElseGet)
      assertThatCode(() -> justInstance.toEither((Supplier<String>) null))
          .doesNotThrowAnyException();
      Either<String, String> result = justInstance.toEither((Supplier<String>) null);
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(justValue);
    }

    @Test
    @DisplayName("toEither works with different types")
    void toEitherWorksWithDifferentTypes() {
      Maybe<Integer> justInt = Maybe.just(42);
      Either<Exception, Integer> result = justInt.toEither(new RuntimeException("missing"));
      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(42);

      Maybe<Integer> nothingInt = Maybe.nothing();
      RuntimeException error = new RuntimeException("missing value");
      Either<Exception, Integer> leftResult = nothingInt.toEither(error);
      assertThat(leftResult.isLeft()).isTrue();
      assertThat(leftResult.getLeft()).isSameAs(error);
    }

    @Test
    @DisplayName("toEither integrates with Either operations")
    void toEitherIntegratesWithEitherOperations() {
      // Test that the returned Either can be used with Either's operations
      Either<String, String> result =
          justInstance
              .toEither(errorMessage)
              .map(String::toUpperCase)
              .mapLeft(err -> "ERROR: " + err);

      assertThat(result.isRight()).isTrue();
      assertThat(result.getRight()).isEqualTo(justValue.toUpperCase());

      Either<String, String> nothingResult =
          nothingInstance
              .toEither(errorMessage)
              .map(String::toUpperCase)
              .mapLeft(err -> "ERROR: " + err);

      assertThat(nothingResult.isLeft()).isTrue();
      assertThat(nothingResult.getLeft()).isEqualTo("ERROR: " + errorMessage);
    }
  }

  @Nested
  @DisplayName("ToString and Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString() provides meaningful representations")
    void toStringProvidesMeaningfulRepresentations() {
      assertThat(justInstance.toString()).isEqualTo("Just(" + justValue + ")");
      assertThat(nothingInstance.toString()).isEqualTo("Nothing");

      Maybe<List<String>> complexJust = Maybe.just(List.of("a", "b"));
      assertThat(complexJust.toString()).isEqualTo("Just([a, b])");
    }

    @Test
    @DisplayName("equals() and hashCode() work correctly")
    void equalsAndHashCodeWorkCorrectly() {
      assertThat(justInstance).isEqualTo(justInstance);
      assertThat(nothingInstance).isEqualTo(nothingInstance);

      Maybe<String> anotherJust = Maybe.just(justValue);
      assertThat(justInstance).isEqualTo(anotherJust);
      assertThat(justInstance.hashCode()).isEqualTo(anotherJust.hashCode());

      assertThat(justInstance).isNotEqualTo(nothingInstance);
      assertThat(justInstance).isNotEqualTo(Maybe.just("different"));

      Maybe<String> anotherNothing = Maybe.nothing();
      assertThat(nothingInstance).isEqualTo(anotherNothing);
      assertThat(nothingInstance).isSameAs(anotherNothing);
      assertThat(nothingInstance.hashCode()).isEqualTo(anotherNothing.hashCode());
    }
  }

  @Nested
  @DisplayName("Advanced Usage Patterns")
  class AdvancedUsagePatterns {

    @Test
    @DisplayName("Maybe as functor maintains structure")
    void maybeAsFunctorMaintainsStructure() {
      Maybe<Integer> start = Maybe.just(5);
      Maybe<Double> result = start.map(i -> i * 2.0).map(d -> d + 0.5).map(Math::sqrt);

      assertThatMaybe(result)
          .isJust()
          .hasValueSatisfying(d -> assertThat(d).isCloseTo(Math.sqrt(10.5), within(0.001)));
    }

    @Test
    @DisplayName("Maybe for railway-oriented programming")
    void maybeForRailwayOrientedProgramming() {
      Function<String, Maybe<Integer>> parseInteger =
          s -> {
            try {
              return Maybe.just(Integer.parseInt(s));
            } catch (NumberFormatException e) {
              return Maybe.nothing();
            }
          };

      Function<Integer, Maybe<Double>> squareRoot =
          i -> i < 0 ? Maybe.nothing() : Maybe.just(Math.sqrt(i));

      Function<Double, Maybe<String>> formatResult =
          d -> d > 100 ? Maybe.nothing() : Maybe.just(String.format("%.2f", d));

      Maybe<String> success =
          Maybe.just("16").flatMap(parseInteger).flatMap(squareRoot).flatMap(formatResult);
      assertThatMaybe(success).isJust().hasValue("4.00");

      Maybe<String> parseFailure =
          Maybe.just("not-a-number")
              .flatMap(parseInteger)
              .flatMap(squareRoot)
              .flatMap(formatResult);
      assertThatMaybe(parseFailure).isNothing();

      Maybe<String> negativeFailure =
          Maybe.just("-4").flatMap(parseInteger).flatMap(squareRoot).flatMap(formatResult);
      assertThatMaybe(negativeFailure).isNothing();
    }

    @Test
    @DisplayName("Maybe pattern matching with expressions")
    void maybePatternMatchingWithExpressions() {
      Function<Maybe<Integer>, String> processMaybe =
          maybe ->
              switch (maybe) {
                case Just<Integer>(var value) -> "Just: " + value;
                case Nothing<Integer> n -> "Nothing";
              };

      assertThat(processMaybe.apply(Maybe.just(42))).isEqualTo("Just: 42");
      assertThat(processMaybe.apply(Maybe.nothing())).isEqualTo("Nothing");

      Maybe<Maybe<Integer>> nested = Maybe.just(Maybe.just(42));
      String nestedResult =
          switch (nested) {
            case Just<Maybe<Integer>>(var inner) ->
                switch (inner) {
                  case Just<Integer>(var value) -> "Nested value: " + value;
                  case Nothing<Integer> n -> "Inner nothing";
                };
            case Nothing<Maybe<Integer>> n -> "Outer nothing";
          };
      assertThat(nestedResult).isEqualTo("Nested value: 42");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Characteristics")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("Maybe operations complete in reasonable time")
    void maybeOperationsCompleteInReasonableTime() {
      Maybe<Integer> test = Maybe.just(42);

      // Verify operations complete without hanging (generous timeout)
      // Use JMH benchmarks in hkj-benchmarks module for precise performance measurement
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () -> {
            for (int i = 0; i < 100_000; i++) {
              test.map(x -> x + 1).flatMap(x -> Maybe.just(x * 2)).isJust();
            }
          },
          "Maybe operations should complete within reasonable time");
    }

    @Test
    @DisplayName("Nothing instances are reused efficiently")
    void nothingInstancesAreReusedEfficiently() {
      Maybe<String> nothing = Maybe.nothing();

      Maybe<Integer> mapped = nothing.map(String::length);
      assertThat(mapped).isSameAs(nothing);

      Maybe<Boolean> multiMapped = nothing.map(String::length).map(len -> len > 0).map(b -> !b);
      assertThat(multiMapped).isSameAs(nothing);

      Maybe<Integer> flatMapped = nothing.flatMap(s -> Maybe.just(s.length()));
      assertThat(flatMapped).isSameAs(nothing);
    }

    @Test
    @DisplayName("Memory usage is reasonable for large chains")
    void memoryUsageIsReasonableForLargeChains() {
      Maybe<Integer> start = Maybe.just(1);

      Maybe<Integer> result = start;
      for (int i = 0; i < 1000; i++) {
        final int increment = i;
        result = result.map(x -> x + increment);
      }

      assertThatMaybe(result).isJust().hasValue(1 + (999 * 1000) / 2);

      Maybe<Integer> nothingStart = Maybe.nothing();
      Maybe<Integer> nothingResult = nothingStart;
      for (int i = 0; i < 1000; i++) {
        int finalI = i;
        nothingResult = nothingResult.map(x -> x + finalI);
      }

      assertThat(nothingResult).isSameAs(nothingStart);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesTests {

    @Test
    @DisplayName("Maybe handles extreme values correctly")
    void maybeHandlesExtremeValuesCorrectly() {
      String largeString = "x".repeat(10000);
      Maybe<String> largeJust = Maybe.just(largeString);
      assertThatMaybe(largeJust.map(String::length)).isJust().hasValue(10000);

      Maybe<Integer> maxInt = Maybe.just(Integer.MAX_VALUE);
      Maybe<Long> promoted = maxInt.map(i -> i.longValue() + 1);
      assertThatMaybe(promoted).isJust().hasValue((long) Integer.MAX_VALUE + 1);

      Maybe<Maybe<Maybe<Integer>>> tripleNested = Maybe.just(Maybe.just(Maybe.just(42)));
      Maybe<Integer> flattened =
          tripleNested.flatMap(inner -> inner.flatMap(innerInner -> innerInner));
      assertThatMaybe(flattened).isJust().hasValue(42);
    }

    @Test
    @DisplayName("Maybe operations are stack-safe for deep recursion")
    void maybeOperationsAreStackSafe() {
      Maybe<Integer> start = Maybe.just(0);

      Maybe<Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        result = result.map(x -> x + 1);
      }
      assertThatMaybe(result).isJust().hasValue(10000);

      Maybe<Integer> flatMapResult = start;
      for (int i = 0; i < 1000; i++) {
        flatMapResult = flatMapResult.flatMap(x -> Maybe.just(x + 1));
      }
      assertThatMaybe(flatMapResult).isJust().hasValue(1000);
    }

    @Test
    @DisplayName("Maybe maintains referential transparency")
    void maybeMaintainsReferentialTransparency() {
      Maybe<Integer> maybe = Maybe.just(42);
      Function<Integer, String> transform = i -> "value:" + i;

      Maybe<String> result1 = maybe.map(transform);
      Maybe<String> result2 = maybe.map(transform);

      assertThat(result1).isEqualTo(result2);
      assertThat(result1.get()).isEqualTo(result2.get());

      Maybe<String> flatMapResult1 = maybe.flatMap(i -> Maybe.just("flat:" + i));
      Maybe<String> flatMapResult2 = maybe.flatMap(i -> Maybe.just("flat:" + i));

      assertThat(flatMapResult1).isEqualTo(flatMapResult2);
    }
  }
}
