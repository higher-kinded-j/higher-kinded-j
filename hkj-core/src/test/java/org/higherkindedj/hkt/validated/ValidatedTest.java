// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Core functionality test suite for {@link Validated}, exercising both inhabitants ({@link Valid}
 * and {@link Invalid}) of every operation inline. The type-class laws and contracts live in the
 * dedicated {@code ValidatedFunctorTest}/{@code ValidatedMonadTest}/{@code ValidatedTraverseTest}
 * etc., so this suite focuses on the type's own behaviour.
 */
@DisplayName("Validated<E, A> Core Functionality - Standardised Test Suite")
class ValidatedTest extends ValidatedTestBase {

  private MonadError<ValidatedKind.Witness<String>, String> monad;
  private Semigroup<String> semigroup;

  private Validated<String, Integer> validInstance;
  private Validated<String, Integer> invalidInstance;

  @BeforeEach
  void setUpValidated() {
    semigroup = createDefaultSemigroup();
    monad = Instances.validated(semigroup);

    validInstance = Validated.valid(DEFAULT_VALID_VALUE);
    invalidInstance = Validated.invalid(DEFAULT_ERROR);
  }

  @Nested
  @DisplayName("Record Construction")
  @SuppressWarnings("DataFlowIssue")
  class RecordConstructionTests {

    @Test
    @DisplayName("new Valid succeeds with non-null value and exposes it")
    void validConstructionSucceeds() {
      Valid<String, Integer> valid = new Valid<>(DEFAULT_VALID_VALUE);

      assertThat(valid.value()).isEqualTo(DEFAULT_VALID_VALUE);
      Validated<String, Integer> asValidated = valid;
      assertThatValidated(asValidated)
          .isValid()
          .hasValue(DEFAULT_VALID_VALUE)
          .hasValueOfType(Integer.class);
    }

    @Test
    @DisplayName("new Valid rejects null value")
    void validConstructionRejectsNull() {
      assertThatThrownBy(() -> new Valid<String, Integer>(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("value")
          .hasMessageContaining("Valid")
          .hasMessageContaining("construction");
    }

    @Test
    @DisplayName("new Invalid succeeds with non-null error and exposes it")
    void invalidConstructionSucceeds() {
      Invalid<String, Integer> invalid = new Invalid<>(DEFAULT_ERROR);

      assertThat(invalid.error()).isEqualTo(DEFAULT_ERROR);
      Validated<String, Integer> asValidated = invalid;
      assertThatValidated(asValidated)
          .isInvalid()
          .hasError(DEFAULT_ERROR)
          .hasErrorOfType(String.class);
    }

    @Test
    @DisplayName("new Invalid rejects null error")
    void invalidConstructionRejectsNull() {
      assertThatThrownBy(() -> new Invalid<String, Integer>(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("error")
          .hasMessageContaining("Invalid");
    }
  }

  @Nested
  @DisplayName("Factory Methods - Complete Coverage")
  @SuppressWarnings("DataFlowIssue")
  class FactoryMethodTests {

    @Test
    @DisplayName("valid() creates correct Valid instances with all value types")
    void validCreatesCorrectInstances() {
      assertThatValidated(validInstance)
          .isValid()
          .hasValue(DEFAULT_VALID_VALUE)
          .hasValueOfType(Integer.class);

      List<String> list = List.of("a", "b", "c");
      Validated<String, List<String>> listValid = Validated.valid(list);
      assertThatValidated(listValid)
          .isValid()
          .hasValueSatisfying(l -> l == list, "value is same list");

      Validated<String, Boolean> boolValid = Validated.valid(true);
      assertThatValidated(boolValid).isValid().hasValue(true);
    }

    @Test
    @DisplayName("invalid() creates correct Invalid instances with all error types")
    void invalidCreatesCorrectInstances() {
      assertThatValidated(invalidInstance)
          .isInvalid()
          .hasError(DEFAULT_ERROR)
          .hasErrorOfType(String.class);

      Exception exception = new RuntimeException("test");
      Validated<Exception, String> exceptionInvalid = Validated.invalid(exception);
      assertThatValidated(exceptionInvalid).isInvalid().hasError(exception);

      Validated<String, Integer> emptyInvalid = Validated.invalid("");
      assertThatValidated(emptyInvalid).isInvalid().hasError("");
    }

    @Test
    @DisplayName("valid() validates non-null requirement")
    void validValidatesNonNull() {
      assertThatThrownBy(() -> Validated.<String, Integer>valid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("value")
          .hasMessageContaining("Validated");
    }

    @Test
    @DisplayName("invalid() validates non-null requirement")
    void invalidValidatesNonNull() {
      assertThatThrownBy(() -> Validated.<String, Integer>invalid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("error")
          .hasMessageContaining("Validated");
    }

    @Test
    @DisplayName("Factory methods type inference works correctly")
    void factoryMethodsTypeInference() {
      Validated<String, Object> invalidAssignment = Validated.invalid("error");
      Validated<Object, Integer> validAssignment = Validated.valid(42);

      assertThatValidated(invalidAssignment).isInvalid().hasError("error");
      assertThatValidated(validAssignment).isValid().hasValue(42);
    }
  }

  @Nested
  @DisplayName("ValidateThat Static Methods")
  @SuppressWarnings("DataFlowIssue")
  class ValidateThatStaticMethods {

    @Test
    @DisplayName("validateThat returns Valid when condition is true")
    void validateThatReturnsValidWhenConditionIsTrue() {
      Validated<String, Unit> result = Validated.validateThat(true, DEFAULT_ERROR);

      assertThatValidated(result).isValid().hasValue(Unit.INSTANCE).hasValueOfType(Unit.class);
    }

    @Test
    @DisplayName("validateThat returns Invalid when condition is false")
    void validateThatReturnsInvalidWhenConditionIsFalse() {
      Validated<String, Unit> result = Validated.validateThat(false, DEFAULT_ERROR);

      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR).hasErrorOfType(String.class);
    }

    @Test
    @DisplayName("validateThat rejects null error")
    void validateThatRejectsNullError() {
      assertThatThrownBy(() -> Validated.validateThat(false, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("errorSupplier cannot be null");
    }

    @Test
    @DisplayName("validateThat with supplier returns Valid when condition is true")
    void validateThatWithSupplierReturnsValidWhenConditionIsTrue() {
      Supplier<String> errorSupplier =
          () -> {
            throw new AssertionError("Should not be called");
          };

      Validated<String, Unit> result = Validated.validateThat(true, errorSupplier);

      assertThatValidated(result).isValid().hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("validateThat with supplier returns Invalid when condition is false")
    void validateThatWithSupplierReturnsInvalidWhenConditionIsFalse() {
      Supplier<String> errorSupplier = () -> "lazy-error";

      Validated<String, Unit> result = Validated.validateThat(false, errorSupplier);

      assertThatValidated(result).isInvalid().hasError("lazy-error");
    }

    @Test
    @DisplayName("validateThat with supplier rejects null supplier")
    void validateThatWithSupplierRejectsNullSupplier() {
      assertThatThrownBy(() -> Validated.validateThat(false, (Supplier<String>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("errorSupplier cannot be null");
    }

    @Test
    @DisplayName("validateThat with supplier evaluates error lazily")
    void validateThatWithSupplierEvaluatesErrorLazily() {
      AtomicInteger counter = new AtomicInteger(0);

      Supplier<String> errorSupplier =
          () -> {
            counter.incrementAndGet();
            return "error";
          };

      Validated.validateThat(true, errorSupplier);
      assertThat(counter.get()).isEqualTo(0);

      Validated.validateThat(false, errorSupplier);
      assertThat(counter.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Query Methods - get / getError")
  class QueryMethodTests {

    @Test
    @DisplayName("isValid / isInvalid reflect the case")
    void isValidAndIsInvalid() {
      assertThat(validInstance.isValid()).isTrue();
      assertThat(validInstance.isInvalid()).isFalse();
      assertThat(invalidInstance.isValid()).isFalse();
      assertThat(invalidInstance.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("get returns the value on Valid")
    void getReturnsValueOnValid() {
      assertThat(validInstance.get()).isEqualTo(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("get throws NoSuchElementException on Invalid")
    void getThrowsOnInvalid() {
      assertThatThrownBy(invalidInstance::get)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot get() from an Invalid instance")
          .hasMessageContaining("Error: " + DEFAULT_ERROR);
    }

    @Test
    @DisplayName("getError returns the error on Invalid")
    void getErrorReturnsErrorOnInvalid() {
      assertThat(invalidInstance.getError()).isEqualTo(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("getError throws NoSuchElementException on Valid")
    void getErrorThrowsOnValid() {
      assertThatThrownBy(validInstance::getError)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessageContaining("Cannot getError() from a Valid instance");
    }
  }

  @Nested
  @DisplayName("OrElse Operations")
  @SuppressWarnings("DataFlowIssue")
  class OrElseOperations {

    @Test
    @DisplayName("orElse returns the value on Valid and the alternative on Invalid")
    void orElseReturnsValueOrAlternative() {
      assertThat(validInstance.orElse(ALTERNATIVE_VALID_VALUE)).isEqualTo(DEFAULT_VALID_VALUE);
      assertThat(invalidInstance.orElse(ALTERNATIVE_VALID_VALUE))
          .isEqualTo(ALTERNATIVE_VALID_VALUE);
    }

    @Test
    @DisplayName("orElse rejects null parameter on Valid")
    void orElseRejectsNullOnValid() {
      assertThatThrownBy(() -> validInstance.orElse(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("orElse")
          .hasMessageContaining("other");
    }

    @Test
    @DisplayName("orElse rejects null parameter on Invalid")
    void orElseRejectsNullOnInvalid() {
      assertThatThrownBy(() -> invalidInstance.orElse(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("orElse")
          .hasMessageContaining("other")
          .hasMessageContaining("Invalid");
    }

    @Test
    @DisplayName("orElseGet returns the value on Valid without invoking the supplier")
    void orElseGetReturnsValueOnValidWithoutSupplier() {
      AtomicBoolean invoked = new AtomicBoolean(false);
      Integer result =
          validInstance.orElseGet(
              () -> {
                invoked.set(true);
                return ALTERNATIVE_VALID_VALUE;
              });
      assertThat(result).isEqualTo(DEFAULT_VALID_VALUE);
      assertThat(invoked).isFalse();
    }

    @Test
    @DisplayName("orElseGet returns the supplied value on Invalid")
    void orElseGetReturnsSuppliedValueOnInvalid() {
      assertThat(invalidInstance.orElseGet(() -> ALTERNATIVE_VALID_VALUE))
          .isEqualTo(ALTERNATIVE_VALID_VALUE);
    }

    @Test
    @DisplayName("orElseGet validates supplier is non-null on Valid")
    void orElseGetValidatesSupplierOnValid() {
      assertThatThrownBy(() -> validInstance.orElseGet(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("otherSupplier")
          .hasMessageContaining("orElseGet");
    }

    @Test
    @DisplayName("orElseGet validates supplier is non-null on Invalid")
    void orElseGetValidatesSupplierOnInvalid() {
      assertThatThrownBy(() -> invalidInstance.orElseGet(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("otherSupplier")
          .hasMessageContaining("orElseGet");
    }

    @Test
    @DisplayName("orElseGet rejects a null supplier result on Invalid")
    void orElseGetRejectsNullSupplierResultOnInvalid() {
      assertThatThrownBy(() -> invalidInstance.orElseGet(() -> null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("orElseGet supplier returned null")
          .hasMessageContaining("Invalid");
    }

    @Test
    @DisplayName("orElseThrow returns the value on Valid without invoking the supplier")
    void orElseThrowReturnsValueOnValidWithoutSupplier() {
      AtomicBoolean invoked = new AtomicBoolean(false);
      assertThatCode(
              () ->
                  validInstance.orElseThrow(
                      () -> {
                        invoked.set(true);
                        return new RuntimeException("should not be thrown");
                      }))
          .doesNotThrowAnyException();
      assertThat(invoked).isFalse();
    }

    @Test
    @DisplayName("orElseThrow throws the supplied exception on Invalid")
    void orElseThrowThrowsOnInvalid() {
      RuntimeException exception = new RuntimeException("boom");
      assertThatThrownBy(() -> invalidInstance.orElseThrow(() -> exception)).isSameAs(exception);
    }

    @Test
    @DisplayName("orElseThrow validates supplier is non-null on Valid")
    void orElseThrowValidatesSupplierOnValid() {
      assertThatThrownBy(() -> validInstance.orElseThrow(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("exceptionSupplier")
          .hasMessageContaining("orElseThrow");
    }

    @Test
    @DisplayName("orElseThrow validates supplier is non-null on Invalid")
    void orElseThrowValidatesSupplierOnInvalid() {
      assertThatThrownBy(() -> invalidInstance.orElseThrow(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("exceptionSupplier")
          .hasMessageContaining("orElseThrow");
    }

    @Test
    @DisplayName("orElseThrow rejects a null supplier result on Invalid")
    void orElseThrowRejectsNullSupplierResultOnInvalid() {
      assertThatThrownBy(() -> invalidInstance.orElseThrow(() -> null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("orElseThrow")
          .hasMessageContaining("exceptionSupplier")
          .hasMessageContaining("null throwable")
          .hasMessageContaining("Invalid");
    }
  }

  @Nested
  @DisplayName("Side Effect Operations")
  @SuppressWarnings("DataFlowIssue")
  class SideEffectOperations {

    @Test
    @DisplayName("ifValid executes the consumer on Valid but not on Invalid")
    void ifValidExecutesOnlyOnValid() {
      AtomicBoolean validExecuted = new AtomicBoolean(false);
      validInstance.ifValid(
          v -> {
            assertThat(v).isEqualTo(DEFAULT_VALID_VALUE);
            validExecuted.set(true);
          });
      assertThat(validExecuted).isTrue();

      AtomicBoolean invalidExecuted = new AtomicBoolean(false);
      invalidInstance.ifValid(_ -> invalidExecuted.set(true));
      assertThat(invalidExecuted).isFalse();
    }

    @Test
    @DisplayName("ifValid validates consumer is non-null on Valid")
    void ifValidValidatesConsumerOnValid() {
      assertThatThrownBy(() -> validInstance.ifValid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("consumer")
          .hasMessageContaining("Valid")
          .hasMessageContaining("ifValid");
    }

    @Test
    @DisplayName("ifValid validates consumer is non-null on Invalid")
    void ifValidValidatesConsumerOnInvalid() {
      assertThatThrownBy(() -> invalidInstance.ifValid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("consumer")
          .hasMessageContaining("ifValid");
    }

    @Test
    @DisplayName("ifInvalid executes the consumer on Invalid but not on Valid")
    void ifInvalidExecutesOnlyOnInvalid() {
      AtomicBoolean invalidExecuted = new AtomicBoolean(false);
      invalidInstance.ifInvalid(
          e -> {
            assertThat(e).isEqualTo(DEFAULT_ERROR);
            invalidExecuted.set(true);
          });
      assertThat(invalidExecuted).isTrue();

      AtomicBoolean validExecuted = new AtomicBoolean(false);
      validInstance.ifInvalid(_ -> validExecuted.set(true));
      assertThat(validExecuted).isFalse();
    }

    @Test
    @DisplayName("ifInvalid validates consumer is non-null on Valid")
    void ifInvalidValidatesConsumerOnValid() {
      assertThatThrownBy(() -> validInstance.ifInvalid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("consumer")
          .hasMessageContaining("ifInvalid");
    }

    @Test
    @DisplayName("ifInvalid validates consumer is non-null on Invalid")
    void ifInvalidValidatesConsumerOnInvalid() {
      assertThatThrownBy(() -> invalidInstance.ifInvalid(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("consumer")
          .hasMessageContaining("Invalid")
          .hasMessageContaining("ifInvalid");
    }
  }

  @Nested
  @DisplayName("Map Method - Right-Biased Testing")
  @SuppressWarnings("DataFlowIssue")
  class MapMethodTests {

    @Test
    @DisplayName("map transforms the value on Valid")
    void mapTransformsValid() {
      Validated<String, String> result = validInstance.map(Object::toString);
      assertThatValidated(result).isValid().hasValue("42").hasValueOfType(String.class);
    }

    @Test
    @DisplayName("map preserves the Invalid instance unchanged")
    void mapPreservesInvalid() {
      Validated<String, String> result = invalidInstance.map(Object::toString);

      assertThat((Object) result).isSameAs(invalidInstance);
      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("map validates mapper is non-null on Valid")
    void mapValidatesMapperOnValid() {
      assertThatThrownBy(() -> validInstance.map(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fn for map cannot be null");
    }

    @Test
    @DisplayName("map validates mapper is non-null on Invalid")
    void mapValidatesMapperOnInvalid() {
      assertThatThrownBy(() -> invalidInstance.map(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fn")
          .hasMessageContaining("map");
    }

    @Test
    @DisplayName("map validates mapper result is non-null on Valid")
    void mapValidatesMapperResultOnValid() {
      Function<Integer, String> nullReturningMapper = _ -> null;

      assertThatThrownBy(() -> validInstance.map(nullReturningMapper))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Function fn in map returned null, which is not allowed");
    }
  }

  @Nested
  @DisplayName("FlatMap Method - Monadic Testing")
  @SuppressWarnings("DataFlowIssue")
  class FlatMapMethodTests {

    @Test
    @DisplayName("flatMap chains computations on Valid")
    void flatMapChainsOnValid() {
      Validated<String, String> result = validInstance.flatMap(i -> Validated.valid(i.toString()));
      assertThatValidated(result).isValid().hasValue("42");
    }

    @Test
    @DisplayName("flatMap can produce Invalid from a Valid source")
    void flatMapCanProduceInvalid() {
      Validated<String, String> result =
          validInstance.flatMap(_ -> Validated.invalid("computed error"));
      assertThatValidated(result).isInvalid().hasError("computed error");
    }

    @Test
    @DisplayName("flatMap preserves the Invalid instance unchanged")
    void flatMapPreservesInvalid() {
      Validated<String, String> result =
          invalidInstance.flatMap(i -> Validated.valid(i.toString()));

      assertThat((Object) result).isSameAs(invalidInstance);
      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("flatMap validates mapper is non-null on Valid")
    void flatMapValidatesMapperOnValid() {
      assertThatThrownBy(() -> validInstance.flatMap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fn")
          .hasMessageContaining("flatMap");
    }

    @Test
    @DisplayName("flatMap validates mapper is non-null on Invalid")
    void flatMapValidatesMapperOnInvalid() {
      assertThatThrownBy(() -> invalidInstance.flatMap(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fn")
          .hasMessageContaining("flatMap");
    }

    @Test
    @DisplayName("flatMap validates mapper result is non-null on Valid")
    void flatMapValidatesMapperResultOnValid() {
      Function<Integer, Validated<String, String>> nullReturningMapper = _ -> null;

      assertThatThrownBy(() -> validInstance.flatMap(nullReturningMapper))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Function fn in flatMap returned null, which is not allowed");
    }
  }

  @Nested
  @DisplayName("Ap Operations")
  @SuppressWarnings("DataFlowIssue")
  class ApOperations {

    @Test
    @DisplayName("ap applies a Valid function to a Valid value")
    void apAppliesValidFunctionToValidValue() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.valid(Object::toString);

      Validated<String, String> result = validInstance.ap(fnValidated, semigroup);
      assertThatValidated(result).isValid().hasValue("42");
    }

    @Test
    @DisplayName("ap propagates an Invalid function over a Valid value")
    void apPropagatesInvalidFunction() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.invalid("function error");

      Validated<String, String> result = validInstance.ap(fnValidated, semigroup);
      assertThatValidated(result).isInvalid().hasError("function error");
    }

    @Test
    @DisplayName("ap preserves the Invalid value when the function is Valid")
    void apPreservesInvalidValue() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.valid(Object::toString);

      Validated<String, String> result = invalidInstance.ap(fnValidated, semigroup);

      assertThat((Object) result).isSameAs(invalidInstance);
      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("ap accumulates errors when both function and value are Invalid")
    void apAccumulatesErrors() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.invalid("function error");

      Validated<String, String> result = invalidInstance.ap(fnValidated, semigroup);
      assertThatValidated(result).isInvalid().hasError("function error, " + DEFAULT_ERROR);
    }

    @Test
    @DisplayName("ap validates function is non-null on Valid")
    void apValidatesFunctionOnValid() {
      assertThatThrownBy(() -> validInstance.ap(null, semigroup))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fnValidated")
          .hasMessageContaining("Validated")
          .hasMessageContaining("ap");
    }

    @Test
    @DisplayName("ap validates function is non-null on Invalid")
    void apValidatesFunctionOnInvalid() {
      assertThatThrownBy(() -> invalidInstance.ap(null, semigroup))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Function fnValidated in ap returned null, which is not allowed");
    }

    @Test
    @DisplayName("ap validates semigroup is non-null on Valid")
    void apValidatesSemigroupOnValid() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.valid(Object::toString);

      assertThatThrownBy(() -> validInstance.ap(fnValidated, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("semigroup")
          .hasMessageContaining("Valid")
          .hasMessageContaining("ap");
    }

    @Test
    @DisplayName("ap validates semigroup is non-null on Invalid")
    void apValidatesSemigroupOnInvalid() {
      Validated<String, Function<? super Integer, ? extends String>> fnValidated =
          Validated.valid(Object::toString);

      assertThatThrownBy(() -> invalidInstance.ap(fnValidated, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Invalid.ap semigroup cannot be null");
    }

    @Test
    @DisplayName("ap chains multiple Valid values via a curried function")
    void apWithMultipleValidValues() {
      Validated<String, Integer> val1 = Validated.valid(10);
      Validated<String, Integer> val2 = Validated.valid(20);

      Function<Integer, Function<Integer, Integer>> addFunc = a -> b -> a + b;
      Validated<String, Function<? super Integer, ? extends Integer>> fnValidated =
          val1.map(addFunc);

      Validated<String, Integer> result = val2.ap(fnValidated, semigroup);
      assertThatValidated(result).isValid().hasValue(30);
    }
  }

  @Nested
  @DisplayName("Fold Operations")
  @SuppressWarnings("DataFlowIssue")
  class FoldOperations {

    @Test
    @DisplayName("fold applies valid mapper on Valid")
    void foldAppliesValidMapperOnValid() {
      String result = validInstance.fold(error -> "Error: " + error, value -> "Value: " + value);

      assertThat(result).isEqualTo("Value: 42");
    }

    @Test
    @DisplayName("fold applies invalid mapper on Invalid")
    void foldAppliesInvalidMapperOnInvalid() {
      String result = invalidInstance.fold(error -> "Error: " + error, value -> "Value: " + value);

      assertThat(result).isEqualTo("Error: " + DEFAULT_ERROR);
    }

    @Test
    @DisplayName("fold validates invalid mapper is non-null")
    void foldValidatesInvalidMapperIsNonNull() {
      assertThatThrownBy(() -> validInstance.fold(null, _ -> "valid"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("invalidMapper")
          .hasMessageContaining("fold");
    }

    @Test
    @DisplayName("fold validates valid mapper is non-null")
    void foldValidatesValidMapperIsNonNull() {
      assertThatThrownBy(() -> validInstance.fold(_ -> "invalid", null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("validMapper")
          .hasMessageContaining("fold");
    }
  }

  @Nested
  @DisplayName("toEither Conversion Tests")
  class ToEitherConversionTests {

    @Test
    @DisplayName("Valid toEither produces Right")
    void validToEitherProducesRight() {
      Either<String, Integer> either = validInstance.toEither();

      assertThat(either.isRight()).isTrue();
      assertThat(either.isLeft()).isFalse();
      assertThat(either.getRight()).isEqualTo(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("Invalid toEither produces Left")
    void invalidToEitherProducesLeft() {
      Either<String, Integer> either = invalidInstance.toEither();

      assertThat(either.isLeft()).isTrue();
      assertThat(either.isRight()).isFalse();
      assertThat(either.getLeft()).isEqualTo(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("Valid toEither roundtrip preserves value")
    void validToEitherRoundtripPreservesValue() {
      Either<String, Integer> either = validInstance.toEither();

      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(validInstance.get());
    }

    @Test
    @DisplayName("Invalid toEither roundtrip preserves error")
    void invalidToEitherRoundtripPreservesError() {
      Either<String, Integer> either = invalidInstance.toEither();

      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(invalidInstance.getError());
    }
  }

  @Nested
  @DisplayName("AsUnit Method Tests")
  class AsUnitMethodTests {

    @Test
    @DisplayName("asUnit converts Valid to Valid Unit")
    void asUnitConvertsValidToValidUnit() {
      Validated<String, Unit> result = validInstance.asUnit();

      assertThatValidated(result).isValid().hasValue(Unit.INSTANCE).hasValueOfType(Unit.class);
    }

    @Test
    @DisplayName("asUnit preserves Invalid")
    void asUnitPreservesInvalid() {
      Validated<String, Unit> result = invalidInstance.asUnit();

      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR).hasErrorOfType(String.class);
    }

    @Test
    @DisplayName("asUnit is idempotent")
    void asUnitIsIdempotent() {
      Validated<String, Unit> result1 = validInstance.asUnit();
      Validated<String, Unit> result2 = result1.asUnit();

      assertThatValidated(result1).isEqualTo(result2);
    }
  }

  @Nested
  @DisplayName("ToString and Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("toString produces readable output for Valid")
    void toStringProducesReadableOutputForValid() {
      assertThat(validInstance.toString()).isEqualTo("Valid(42)");
    }

    @Test
    @DisplayName("toString produces readable output for Invalid")
    void toStringProducesReadableOutputForInvalid() {
      assertThat(invalidInstance.toString()).isEqualTo("Invalid(" + DEFAULT_ERROR + ")");
    }

    @Test
    @DisplayName("equals and hashCode compare Valid values correctly")
    void equalsAndHashCodeForValid() {
      Validated<String, Integer> valid1 = Validated.valid(DEFAULT_VALID_VALUE);
      Validated<String, Integer> valid2 = Validated.valid(DEFAULT_VALID_VALUE);
      Validated<String, Integer> valid3 = Validated.valid(ALTERNATIVE_VALID_VALUE);

      assertThatValidated(valid1).isEqualTo(valid2);
      assertThatValidated(valid1).isNotEqualTo(valid3);
      assertThat(valid1).isNotEqualTo(null);
      assertThat(valid1.hashCode()).isEqualTo(valid2.hashCode());
    }

    @Test
    @DisplayName("equals and hashCode compare Invalid values correctly")
    void equalsAndHashCodeForInvalid() {
      Validated<String, Integer> invalid1 = Validated.invalid(DEFAULT_ERROR);
      Validated<String, Integer> invalid2 = Validated.invalid(DEFAULT_ERROR);
      Validated<String, Integer> invalid3 = Validated.invalid(ALTERNATIVE_ERROR);

      assertThatValidated(invalid1).isEqualTo(invalid2);
      assertThatValidated(invalid1).isNotEqualTo(invalid3);
      assertThat(invalid1).isNotEqualTo(null);
      assertThat(invalid1.hashCode()).isEqualTo(invalid2.hashCode());
    }

    @Test
    @DisplayName("Valid and Invalid are never equal")
    void validAndInvalidAreNeverEqual() {
      assertThatValidated(validInstance).isNotEqualTo(invalidInstance);
    }
  }

  @Nested
  @DisplayName("Type Variance Tests")
  class TypeVarianceTests {

    @Test
    @DisplayName("map changes value type")
    void mapChangesValueType() {
      Validated<String, Integer> intValid = Validated.valid(42);
      Validated<String, String> stringValid = intValid.map(Object::toString);

      assertThatValidated(stringValid).isValid().hasValue("42").hasValueOfType(String.class);
    }

    @Test
    @DisplayName("flatMap changes value type")
    void flatMapChangesValueType() {
      Validated<String, Integer> intValid = Validated.valid(42);
      Validated<String, Boolean> boolValid = intValid.flatMap(n -> Validated.valid(n > 40));

      assertThatValidated(boolValid).isValid().hasValue(true).hasValueOfType(Boolean.class);
    }

    @Test
    @DisplayName("error type remains consistent through transformations")
    void errorTypeRemainsConsistentThroughTransformations() {
      Validated<String, Integer> start = Validated.invalid("initial error");

      Validated<String, String> afterMap = start.map(Object::toString);
      assertThatValidated(afterMap)
          .isInvalid()
          .hasError("initial error")
          .hasErrorOfType(String.class);

      Validated<String, Boolean> afterFlatMap = afterMap.flatMap(s -> Validated.valid(s.isEmpty()));
      assertThatValidated(afterFlatMap)
          .isInvalid()
          .hasError("initial error")
          .hasErrorOfType(String.class);
    }

    @Test
    @DisplayName("different error types with different Validated instances")
    void differentErrorTypesWithDifferentValidatedInstances() {
      Validated<Integer, String> intError = Validated.invalid(404);
      Validated<Boolean, String> boolError = Validated.invalid(true);

      assertThatValidated(intError).isInvalid().hasError(404).hasErrorOfType(Integer.class);
      assertThatValidated(boolError).isInvalid().hasError(true).hasErrorOfType(Boolean.class);
    }
  }

  @Nested
  @DisplayName("Advanced Usage Patterns")
  class AdvancedUsagePatterns {

    @Test
    @DisplayName("Validated as functor maintains structure")
    void validatedAsFunctorMaintainsStructure() {
      Validated<String, Integer> start = Validated.valid(5);

      Validated<String, Double> result = start.map(i -> i * 2.0).map(d -> d + 0.5).map(Math::sqrt);

      assertThatValidated(result)
          .isValid()
          .hasValueSatisfying(
              value -> Math.abs(value - Math.sqrt(10.5)) < 0.001, "value is square root of 10.5");
    }

    @Test
    @DisplayName("Validated for parallel validation")
    void validatedForParallelValidation() {
      Function<String, Validated<String, Unit>> validateLength =
          s -> Validated.validateThat(s.length() >= 3, "Name must be at least 3 characters");

      Function<String, Validated<String, Unit>> validateNotEmpty =
          s -> Validated.validateThat(!s.isEmpty(), "Name cannot be empty");

      Function<String, Validated<String, Unit>> validateAlpha =
          s ->
              Validated.validateThat(
                  s.chars().allMatch(Character::isLetter), "Name must contain only letters");

      String validName = "John";
      Validated<String, Unit> v1 = validateLength.apply(validName);
      Validated<String, Unit> v2 = validateNotEmpty.apply(validName);
      Validated<String, Unit> v3 = validateAlpha.apply(validName);

      Kind<ValidatedKind.Witness<String>, Unit> k1 = VALIDATED.widen(v1);
      Kind<ValidatedKind.Witness<String>, Unit> k2 = VALIDATED.widen(v2);
      Kind<ValidatedKind.Witness<String>, Unit> k3 = VALIDATED.widen(v3);

      Kind<ValidatedKind.Witness<String>, Unit> allValid =
          monad.map2(monad.map2(k1, k2, (_, _) -> Unit.INSTANCE), k3, (_, _) -> Unit.INSTANCE);

      Validated<String, Unit> result = VALIDATED.narrow(allValid);
      assertThatValidated(result).isValid();

      String invalidName = "A1";
      Validated<String, Unit> iv1 = validateLength.apply(invalidName);
      Validated<String, Unit> iv2 = validateNotEmpty.apply(invalidName);
      Validated<String, Unit> iv3 = validateAlpha.apply(invalidName);

      Kind<ValidatedKind.Witness<String>, Unit> ik1 = VALIDATED.widen(iv1);
      Kind<ValidatedKind.Witness<String>, Unit> ik2 = VALIDATED.widen(iv2);
      Kind<ValidatedKind.Witness<String>, Unit> ik3 = VALIDATED.widen(iv3);

      Kind<ValidatedKind.Witness<String>, Unit> allInvalid =
          monad.map2(monad.map2(ik1, ik2, (_, _) -> Unit.INSTANCE), ik3, (_, _) -> Unit.INSTANCE);

      Validated<String, Unit> invalidResult = VALIDATED.narrow(allInvalid);
      assertThatValidated(invalidResult)
          .isInvalid()
          .hasErrorSatisfying(
              error -> error.contains("at least 3 characters") && error.contains("only letters"),
              "accumulates multiple errors");
    }

    @Test
    @DisplayName("Validated pattern matching with switch expressions")
    void validatedPatternMatchingWithSwitch() {
      Function<Validated<String, Integer>, String> processValidated =
          validated ->
              switch (validated) {
                case Invalid<String, Integer>(var error) -> "Error: " + error;
                case Valid<String, Integer>(var value) -> "Success: " + value;
              };

      assertThat(processValidated.apply(invalidInstance)).isEqualTo("Error: " + DEFAULT_ERROR);
      assertThat(processValidated.apply(validInstance))
          .isEqualTo("Success: " + DEFAULT_VALID_VALUE);

      Validated<Validated<String, Integer>, Boolean> nested =
          Validated.invalid(Validated.valid(42));
      String nestedResult =
          switch (nested) {
            case Invalid<Validated<String, Integer>, Boolean>(var innerValidated) ->
                switch (innerValidated) {
                  case Invalid<String, Integer>(var error) -> "Nested error: " + error;
                  case Valid<String, Integer>(var value) -> "Nested value: " + value;
                };
            case Valid<Validated<String, Integer>, Boolean>(var bool) -> "Boolean: " + bool;
          };
      assertThat(nestedResult).isEqualTo("Nested value: 42");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("chain multiple Valid operations")
    void chainMultipleValidOperations() {
      Validated<String, Integer> result =
          Validated.<String, Integer>valid(10)
              .map(n -> n * 2)
              .flatMap(n -> Validated.valid(n + 5))
              .map(n -> n * 3);

      assertThatValidated(result).isValid().hasValue(75);
    }

    @Test
    @DisplayName("chain operations short-circuits on Invalid")
    void chainOperationsShortCircuitsOnInvalid() {
      Validated<String, Integer> result =
          Validated.<String, Integer>valid(10)
              .map(n -> n * 2)
              .flatMap(_ -> Validated.<String, Integer>invalid("computation failed"))
              .map(n -> n * 3);

      assertThatValidated(result).isInvalid().hasError("computation failed");
    }

    @Test
    @DisplayName("combining Valid values with map2")
    void combiningValidValuesWithMap2() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = validKind(10);
      Kind<ValidatedKind.Witness<String>, Integer> v2 = validKind(20);

      Kind<ValidatedKind.Witness<String>, Integer> result = monad.map2(v1, v2, Integer::sum);
      assertThatValidated(result).isValid().hasValue(30);
    }

    @Test
    @DisplayName("combining Invalid values accumulates errors")
    void combiningInvalidValuesAccumulatesErrors() {
      Kind<ValidatedKind.Witness<String>, Integer> v1 = invalidKind("error1");
      Kind<ValidatedKind.Witness<String>, Integer> v2 = invalidKind("error2");

      Kind<ValidatedKind.Witness<String>, Integer> result = monad.map2(v1, v2, Integer::sum);
      assertThatValidated(result).isInvalid().hasError("error1, error2");
    }

    @Test
    @DisplayName("validation pipeline with multiple checks")
    void validationPipelineWithMultipleChecks() {
      Validated<String, Unit> ageCheck = Validated.validateThat(true, "Must be 18 or older");
      Validated<String, Unit> nameCheck = Validated.validateThat(true, "Name required");

      Kind<ValidatedKind.Witness<String>, Unit> ageKind = VALIDATED.widen(ageCheck);
      Kind<ValidatedKind.Witness<String>, Unit> nameKind = VALIDATED.widen(nameCheck);

      Kind<ValidatedKind.Witness<String>, Unit> allValid =
          monad.map2(ageKind, nameKind, (_, _) -> Unit.INSTANCE);
      assertThatValidated(allValid).isValid().hasValue(Unit.INSTANCE);
    }

    @Test
    @DisplayName("validation pipeline accumulates all errors")
    void validationPipelineAccumulatesAllErrors() {
      Validated<String, Unit> ageCheck = Validated.validateThat(false, "Must be 18 or older");
      Validated<String, Unit> nameCheck = Validated.validateThat(false, "Name required");

      Kind<ValidatedKind.Witness<String>, Unit> ageKind = VALIDATED.widen(ageCheck);
      Kind<ValidatedKind.Witness<String>, Unit> nameKind = VALIDATED.widen(nameCheck);

      Kind<ValidatedKind.Witness<String>, Unit> allInvalid =
          monad.map2(ageKind, nameKind, (_, _) -> Unit.INSTANCE);
      assertThatValidated(allInvalid)
          .isInvalid()
          .hasErrorSatisfying(
              error -> error.contains("Must be 18 or older") && error.contains("Name required"),
              "contains both validation errors");
    }
  }

  @Nested
  @DisplayName("Performance and Memory Characteristics")
  class PerformanceAndMemoryTests {

    @Test
    @DisplayName("Validated operations complete in reasonable time")
    void validatedOperationsCompleteInReasonableTime() {
      Validated<String, Integer> test = Validated.valid(DEFAULT_VALID_VALUE);

      // Verify operations complete without hanging (generous timeout)
      // Use JMH benchmarks in hkj-benchmarks module for precise performance measurement
      assertTimeoutPreemptively(
          Duration.ofSeconds(2),
          () -> {
            for (int i = 0; i < 100_000; i++) {
              test.map(x -> x + 1).flatMap(x -> Validated.valid(x * 2)).isValid();
            }
          },
          "Validated operations should complete within reasonable time");
    }

    @Test
    @DisplayName("Invalid instances are reused efficiently")
    void invalidInstancesAreReusedEfficiently() {
      Validated<String, Integer> invalid = Validated.invalid("error");

      Validated<String, String> mapped = invalid.map(Object::toString);
      assertThat((Object) mapped).isSameAs(invalid);

      Validated<String, Boolean> multiMapped =
          invalid.map(Object::toString).map(String::length).map(len -> len > 0);
      assertThat((Object) multiMapped).isSameAs(invalid);

      Validated<String, String> flatMapped = invalid.flatMap(_ -> Validated.valid("not reached"));
      assertThat((Object) flatMapped).isSameAs(invalid);
    }

    @Test
    @DisplayName("Memory usage is reasonable for large chains")
    void memoryUsageIsReasonableForLargeChains() {
      Validated<String, Integer> result = Validated.valid(1);
      for (int i = 0; i < 1000; i++) {
        final int increment = i;
        result = result.map(x -> x + increment);
      }

      assertThatValidated(result).isValid().hasValue(1 + (999 * 1000) / 2);

      Validated<String, Integer> invalidStart = Validated.invalid("error");
      Validated<String, Integer> invalidResult = invalidStart;
      for (int i = 0; i < 1000; i++) {
        int finalI = i;
        invalidResult = invalidResult.map(x -> x + finalI);
      }

      assertThat(invalidResult).isSameAs(invalidStart);
    }
  }

  @Nested
  @DisplayName("Edge Cases and Boundary Conditions")
  class EdgeCasesTests {

    @Test
    @DisplayName("Validated handles extreme values correctly")
    void validatedHandlesExtremeValuesCorrectly() {
      String largeString = "x".repeat(10000);
      Validated<String, String> largeValid = Validated.valid(largeString);
      assertThatValidated(largeValid.map(String::length)).isValid().hasValue(10000);

      Validated<String, Integer> maxInt = Validated.valid(Integer.MAX_VALUE);
      Validated<String, Long> promoted = maxInt.map(i -> i.longValue() + 1);
      assertThatValidated(promoted).isValid().hasValue((long) Integer.MAX_VALUE + 1);

      Validated<String, Validated<String, Validated<String, Integer>>> tripleNested =
          Validated.valid(Validated.valid(Validated.valid(42)));

      Validated<String, Integer> flattened =
          tripleNested.flatMap(inner -> inner.flatMap(innerInner -> innerInner));
      assertThatValidated(flattened).isValid().hasValue(42);
    }

    @Test
    @DisplayName("Validated operations are stack-safe for deep recursion")
    void validatedOperationsAreStackSafe() {
      Validated<String, Integer> start = Validated.valid(0);

      Validated<String, Integer> result = start;
      for (int i = 0; i < 10000; i++) {
        result = result.map(x -> x + 1);
      }

      assertThatValidated(result).isValid().hasValue(10000);

      Validated<String, Integer> flatMapResult = start;
      for (int i = 0; i < 1000; i++) {
        flatMapResult = flatMapResult.flatMap(x -> Validated.valid(x + 1));
      }

      assertThatValidated(flatMapResult).isValid().hasValue(1000);
    }

    @Test
    @DisplayName("Validated maintains referential transparency")
    void validatedMaintainsReferentialTransparency() {
      Validated<String, Integer> validated = Validated.valid(DEFAULT_VALID_VALUE);

      Validated<String, String> result1 = validated.map(i -> "value:" + i);
      Validated<String, String> result2 = validated.map(i -> "value:" + i);

      assertThat(result1).isEqualTo(result2);
      assertThat(result1.get()).isEqualTo(result2.get());

      Validated<String, String> flatMapResult1 =
          validated.flatMap(i -> Validated.valid("flat:" + i));
      Validated<String, String> flatMapResult2 =
          validated.flatMap(i -> Validated.valid("flat:" + i));

      assertThat(flatMapResult1).isEqualTo(flatMapResult2);
    }
  }
}
