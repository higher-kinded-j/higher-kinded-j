// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Invalid Complete Test Suite")
class InvalidTest {

    private Invalid<String, Integer> invalidInstance;
    private Semigroup<String> semigroup;


    @BeforeEach
    void setUp() {
        invalidInstance = new Invalid<>("test-error");
        semigroup = Semigroups.string(",");
    }

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Run complete Invalid core type test pattern")
        void runCompleteInvalidValidationTests() {
            Validated<String, Integer> anotherInvalid = Validated.invalid("another-error");

            CoreTypeTest.<String,Integer>validated(Validated.class)
                .withInvalid(invalidInstance)
                .withValid(anotherInvalid)  // Use another Invalid, not Valid
                .withMappers(Object::toString)
                .configureValidation()
                .useInheritanceValidation()
                .withMapFrom(Invalid.class)
                .withFlatMapFrom(Invalid.class)
                .withIfValidFrom(Invalid.class)
                .withIfInvalidFrom(Invalid.class)
                .testValidations();
        }
    }

    @Nested
    @DisplayName("Construction Tests")
    class ConstructionTests {

        @Test
        @DisplayName("Invalid creation succeeds with non-null error")
        void invalidCreationSucceedsWithNonNullError() {
            Invalid<String, Integer> invalid = new Invalid<>("error");
            assertThat(invalid.error()).isEqualTo("error");
        }

        @Test
        @DisplayName("Invalid creation rejects null error")
        void invalidCreationRejectsNullError() {
            assertThatThrownBy(() -> new Invalid<String, Integer>(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("error")
                    .hasMessageContaining("Invalid");
        }

        @Test
        @DisplayName("Factory method creates Invalid instance")
        void factoryMethodCreatesInvalidInstance() {
            Validated<String, Integer> validated = Validated.invalid("error");

            assertThat(validated).isInstanceOf(Invalid.class);
            assertThat(validated.isInvalid()).isTrue();
            assertThat(validated.getError()).isEqualTo("error");
        }
    }

    @Nested
    @DisplayName("Query Operations")
    class QueryOperations {

        @Test
        @DisplayName("IsValid returns false")
        void isValidReturnsFalse() {
            assertThat(invalidInstance.isValid()).isFalse();
        }

        @Test
        @DisplayName("IsInvalid returns true")
        void isInvalidReturnsTrue() {
            assertThat(invalidInstance.isInvalid()).isTrue();
        }

        @Test
        @DisplayName("Get throws NoSuchElementException")
        void getThrowsNoSuchElementException() {
            assertThatThrownBy(() -> invalidInstance.get())
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("Cannot get() from an Invalid instance")
                    .hasMessageContaining("Error: test-error");
        }

        @Test
        @DisplayName("GetError returns the encapsulated error")
        void getErrorReturnsTheEncapsulatedError() {
            assertThat(invalidInstance.getError()).isEqualTo("test-error");
        }
    }

    @Nested
    @DisplayName("OrElse Operations")
    class OrElseOperations {

        @Test
        @DisplayName("OrElse returns the alternative value")
        void orElseReturnsTheAlternativeValue() {
            assertThat(invalidInstance.orElse(100)).isEqualTo(100);
        }

        @Test
        @DisplayName("OrElse rejects null parameter")
        void orElseRejectsNullParameter() {
            assertThatThrownBy(() -> invalidInstance.orElse(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("orElse")
                    .hasMessageContaining("other")
                    .hasMessageContaining("Invalid");
        }

        @Test
        @DisplayName("OrElseGet returns the supplied value")
        void orElseGetReturnsTheSuppliedValue() {
            Integer result = invalidInstance.orElseGet(() -> 100);
            assertThat(result).isEqualTo(100);
        }

        @Test
        @DisplayName("OrElseGet validates supplier is non-null")
        void orElseGetValidatesSupplierIsNonNull() {
            assertThatThrownBy(() -> invalidInstance.orElseGet(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("otherSupplier")
                    .hasMessageContaining("Invalid")
                    .hasMessageContaining("orElseGet");
        }

        @Test
        @DisplayName("OrElseGet validates supplier result is non-null")
        void orElseGetValidatesSupplierResultIsNonNull() {
            assertThatThrownBy(() -> invalidInstance.orElseGet(() -> null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("orElseGet supplier returned null")
                    .hasMessageContaining("Invalid");
        }

        @Test
        @DisplayName("OrElseThrow throws the supplied exception")
        void orElseThrowThrowsTheSuppliedException() {
            RuntimeException exception = new RuntimeException("Test exception");

            assertThatThrownBy(() -> invalidInstance.orElseThrow(() -> exception))
                    .isSameAs(exception);
        }

        @Test
        @DisplayName("OrElseThrow validates supplier is non-null")
        void orElseThrowValidatesSupplierIsNonNull() {
            assertThatThrownBy(() -> invalidInstance.orElseThrow(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("exceptionSupplier")
                    .hasMessageContaining("Invalid")
                    .hasMessageContaining("orElseThrow");
        }

        @Test
        @DisplayName("OrElseThrow validates supplier result is non-null")
        void orElseThrowValidatesSupplierResultIsNonNull() {
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
    class SideEffectOperations {

        @Test
        @DisplayName("IfValid does not execute consumer")
        void ifValidDoesNotExecuteConsumer() {
            AtomicBoolean executed = new AtomicBoolean(false);

            invalidInstance.ifValid(v -> executed.set(true));

            assertThat(executed).isFalse();
        }

        @Test
        @DisplayName("IfValid validates consumer is non-null")
        void ifValidValidatesConsumerIsNonNull() {
            assertThatThrownBy(() -> invalidInstance.ifValid(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("consumer")
                    .hasMessageContaining("Invalid")
                    .hasMessageContaining("ifValid");
        }

        @Test
        @DisplayName("IfInvalid executes consumer with the error")
        void ifInvalidExecutesConsumerWithTheError() {
            AtomicBoolean executed = new AtomicBoolean(false);

            invalidInstance.ifInvalid(e -> {
                assertThat(e).isEqualTo("test-error");
                executed.set(true);
            });

            assertThat(executed).isTrue();
        }

        @Test
        @DisplayName("IfInvalid validates consumer is non-null")
        void ifInvalidValidatesConsumerIsNonNull() {
            assertThatThrownBy(() -> invalidInstance.ifInvalid(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("consumer")
                    .hasMessageContaining("Invalid")
                    .hasMessageContaining("ifInvalid");
        }
    }

    @Nested
    @DisplayName("Transformation Operations")
    class TransformationOperations {

        @Test
        @DisplayName("Map preserves the Invalid instance")
        void mapPreservesTheInvalidInstance() {
            Validated<String, String> result = invalidInstance.map(Object::toString);

            assertThat(result).isSameAs(invalidInstance);
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getError()).isEqualTo("test-error");
        }

        @Test
        @DisplayName("Map validates mapper is non-null")
        void mapValidatesMapperIsNonNull() {
            assertThatThrownBy(() -> invalidInstance.map(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("fn")
                    .hasMessageContaining("Invalid")
                    .hasMessageContaining("map");
        }

        @Test
        @DisplayName("FlatMap preserves the Invalid instance")
        void flatMapPreservesTheInvalidInstance() {
            Validated<String, String> result =
                    invalidInstance.flatMap(i -> Validated.valid(i.toString()));

            assertThat(result).isSameAs(invalidInstance);
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getError()).isEqualTo("test-error");
        }

        @Test
        @DisplayName("FlatMap validates mapper is non-null")
        void flatMapValidatesMapperIsNonNull() {
            assertThatThrownBy(() -> invalidInstance.flatMap(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("fn")
                    .hasMessageContaining("Invalid")
                    .hasMessageContaining("flatMap");
        }
    }

    @Nested
    @DisplayName("Ap Operations")
    class ApOperations {

        @Test
        @DisplayName("Ap with Invalid function accumulates errors")
        void apWithInvalidFunctionAccumulatesErrors() {
            Validated<String, Function<? super Integer, ? extends String>> fnValidated =
                    Validated.invalid("function error");

            Validated<String, String> result = invalidInstance.ap(fnValidated, semigroup);

            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getError()).isEqualTo("function error,test-error");
        }

        @Test
        @DisplayName("Ap with Valid function preserves Invalid instance")
        void apWithValidFunctionPreservesInvalidInstance() {
            Validated<String, Function<? super Integer, ? extends String>> fnValidated =
                    Validated.valid(Object::toString);

            Validated<String, String> result = invalidInstance.ap(fnValidated, semigroup);

            assertThat(result).isSameAs(invalidInstance);
            assertThat(result.isInvalid()).isTrue();
            assertThat(result.getError()).isEqualTo("test-error");
        }

        @Test
        @DisplayName("Ap validates function is non-null")
        void apValidatesFunctionIsNonNull() {
            assertThatThrownBy(() -> invalidInstance.ap(null, semigroup))
                    .isInstanceOf(KindUnwrapException.class)
                    .hasMessageContaining("Function fnValidated in Validated.ap returned null, which is not allowed");
        }

        @Test
        @DisplayName("Ap validates semigroup is non-null")
        void apValidatesSemigroupIsNonNull() {
            Validated<String, Function<? super Integer, ? extends String>> fnValidated =
                    Validated.valid(Object::toString);

            assertThatThrownBy(() -> invalidInstance.ap(fnValidated, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Invalid.ap semigroup cannot be null");
        }
    }

    @Nested
    @DisplayName("Fold Operations")
    class FoldOperations {

        @Test
        @DisplayName("Fold applies invalid mapper")
        void foldAppliesInvalidMapper() {
            String result = invalidInstance.fold(
                    error -> "Error: " + error,
                    value -> "Value: " + value
            );

            assertThat(result).isEqualTo("Error: test-error");
        }

        @Test
        @DisplayName("Fold validates invalid mapper is non-null")
        void foldValidatesInvalidMapperIsNonNull() {
            assertThatThrownBy(() -> invalidInstance.fold(null, v -> "valid"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Function invalidMapper for Validated.fold cannot be null");
        }

        @Test
        @DisplayName("Fold validates valid mapper is non-null")
        void foldValidatesValidMapperIsNonNull() {
            assertThatThrownBy(() -> invalidInstance.fold(e -> "invalid", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Function validMapper for Validated.fold cannot be null");
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("ToString produces readable output")
        void toStringProducesReadableOutput() {
            assertThat(invalidInstance.toString()).isEqualTo("Invalid(test-error)");
        }

        @Test
        @DisplayName("Equals compares errors correctly")
        void equalsComparesErrorsCorrectly() {
            Invalid<String, Integer> same = new Invalid<>("test-error");
            Invalid<String, Integer> different = new Invalid<>("other-error");

            assertThat(invalidInstance).isEqualTo(same);
            assertThat(invalidInstance).isNotEqualTo(different);
            assertThat(invalidInstance).isNotEqualTo(null);
            assertThat(invalidInstance).isNotEqualTo("not an Invalid");
        }

        @Test
        @DisplayName("HashCode is consistent")
        void hashCodeIsConsistent() {
            Invalid<String, Integer> same = new Invalid<>("test-error");

            assertThat(invalidInstance.hashCode()).isEqualTo(same.hashCode());
        }

        @Test
        @DisplayName("Record accessor returns error")
        void recordAccessorReturnsError() {
            assertThat(invalidInstance.error()).isEqualTo("test-error");
        }
    }
}