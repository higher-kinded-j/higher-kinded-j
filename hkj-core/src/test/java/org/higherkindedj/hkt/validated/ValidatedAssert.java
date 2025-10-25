package org.higherkindedj.hkt.validated;


import static org.junit.jupiter.api.Assertions.*;

/**
 * Fluent assertion utilities for {@link Validated} types.
 * Provides a convenient API for testing Validated instances in unit tests.
 *
 * @param <E> The error type
 * @param <A> The success value type
 */
public class ValidatedAssert<E, A> {

    private final Validated<E, A> validated;
    private final String description;

    private ValidatedAssert(Validated<E, A> validated, String description) {
        this.validated = validated;
        this.description = description;
    }

    /**
     * Creates a new ValidatedAssert for the given Validated instance.
     *
     * @param validated the Validated instance to assert on
     * @param <E> the error type
     * @param <A> the value type
     * @return a new ValidatedAssert instance
     */
    public static <E, A> ValidatedAssert<E, A> assertThatValidated(Validated<E, A> validated) {
        return new ValidatedAssert<>(validated, "Validated");
    }

    /**
     * Creates a new ValidatedAssert with a custom description.
     *
     * @param validated the Validated instance to assert on
     * @param description a description for this assertion
     * @param <E> the error type
     * @param <A> the value type
     * @return a new ValidatedAssert instance
     */
    public static <E, A> ValidatedAssert<E, A> assertThatValidated(Validated<E, A> validated, String description) {
        return new ValidatedAssert<>(validated, description);
    }

    /**
     * Asserts that the Validated is Valid.
     *
     * @return this ValidatedAssert for chaining
     * @throws AssertionError if the Validated is Invalid
     */
    public ValidatedAssert<E, A> isValid() {
        assertTrue(validated.isValid(),
                String.format("%s should be Valid but was Invalid with error: %s",
                        description,
                        validated.fold(Object::toString, v -> "N/A")));
        return this;
    }

    /**
     * Asserts that the Validated is Invalid.
     *
     * @return this ValidatedAssert for chaining
     * @throws AssertionError if the Validated is Valid
     */
    public ValidatedAssert<E, A> isInvalid() {
        assertTrue(validated.isInvalid(),
                String.format("%s should be Invalid but was Valid with value: %s",
                        description,
                        validated.fold(e -> "N/A", Object::toString)));
        return this;
    }

    /**
     * Asserts that the Validated is Valid and contains the expected value.
     *
     * @param expected the expected value
     * @return this ValidatedAssert for chaining
     * @throws AssertionError if the Validated is Invalid or contains a different value
     */
    public ValidatedAssert<E, A> hasValue(A expected) {
        isValid();
        A actual = validated.fold(
                e -> {
                    throw new AssertionError("Should not reach here - already verified Valid");
                },
                v -> v
        );
        assertEquals(expected, actual,
                String.format("%s should contain value %s but was %s", description, expected, actual));
        return this;
    }

    /**
     * Asserts that the Validated is Invalid and contains the expected error.
     *
     * @param expected the expected error
     * @return this ValidatedAssert for chaining
     * @throws AssertionError if the Validated is Valid or contains a different error
     */
    public ValidatedAssert<E, A> hasError(E expected) {
        isInvalid();
        E actual = validated.fold(
                e -> e,
                v -> {
                    throw new AssertionError("Should not reach here - already verified Invalid");
                }
        );
        assertEquals(expected, actual,
                String.format("%s should contain error %s but was %s", description, expected, actual));
        return this;
    }

    /**
     * Asserts that the Validated is Valid and the value satisfies the given predicate.
     *
     * @param predicate the predicate to test
     * @param predicateDescription description of what the predicate tests
     * @return this ValidatedAssert for chaining
     * @throws AssertionError if the Validated is Invalid or the predicate fails
     */
    public ValidatedAssert<E, A> hasValueSatisfying(
            java.util.function.Predicate<A> predicate,
            String predicateDescription) {
        isValid();
        A actual = validated.fold(
                e -> {
                    throw new AssertionError("Should not reach here - already verified Valid");
                },
                v -> v
        );
        assertTrue(predicate.test(actual),
                String.format("%s value should satisfy: %s, but value was: %s",
                        description, predicateDescription, actual));
        return this;
    }

    /**
     * Asserts that the Validated is Invalid and the error satisfies the given predicate.
     *
     * @param predicate the predicate to test
     * @param predicateDescription description of what the predicate tests
     * @return this ValidatedAssert for chaining
     * @throws AssertionError if the Validated is Valid or the predicate fails
     */
    public ValidatedAssert<E, A> hasErrorSatisfying(
            java.util.function.Predicate<E> predicate,
            String predicateDescription) {
        isInvalid();
        E actual = validated.fold(
                e -> e,
                v -> {
                    throw new AssertionError("Should not reach here - already verified Invalid");
                }
        );
        assertTrue(predicate.test(actual),
                String.format("%s error should satisfy: %s, but error was: %s",
                        description, predicateDescription, actual));
        return this;
    }

    /**
     * Asserts that the Validated is Valid and the value is an instance of the expected class.
     *
     * @param expectedClass the expected class
     * @return this ValidatedAssert for chaining
     * @throws AssertionError if the Validated is Invalid or the value is not an instance of the expected class
     */
    public ValidatedAssert<E, A> hasValueOfType(Class<?> expectedClass) {
        isValid();
        A actual = validated.fold(
                e -> {
                    throw new AssertionError("Should not reach here - already verified Valid");
                },
                v -> v
        );
        assertInstanceOf(expectedClass, actual,
                String.format("%s value should be instance of %s", description, expectedClass.getName()));
        return this;
    }

    /**
     * Asserts that the Validated is Invalid and the error is an instance of the expected class.
     *
     * @param expectedClass the expected class
     * @return this ValidatedAssert for chaining
     * @throws AssertionError if the Validated is Valid or the error is not an instance of the expected class
     */
    public ValidatedAssert<E, A> hasErrorOfType(Class<?> expectedClass) {
        isInvalid();
        E actual = validated.fold(
                e -> e,
                v -> {
                    throw new AssertionError("Should not reach here - already verified Invalid");
                }
        );
        assertInstanceOf(expectedClass, actual,
                String.format("%s error should be instance of %s", description, expectedClass.getName()));
        return this;
    }

    /**
     * Asserts that the Validated equals the expected Validated.
     *
     * @param expected the expected Validated
     * @return this ValidatedAssert for chaining
     * @throws AssertionError if the Validated instances are not equal
     */
    public ValidatedAssert<E, A> isEqualTo(Validated<E, A> expected) {
        assertEquals(expected, validated,
                String.format("%s should equal %s", description, expected));
        return this;
    }

    /**
     * Asserts that the Validated does not equal the given Validated.
     *
     * @param other the Validated to compare against
     * @return this ValidatedAssert for chaining
     * @throws AssertionError if the Validated instances are equal
     */
    public ValidatedAssert<E, A> isNotEqualTo(Validated<E, A> other) {
        assertNotEquals(other, validated,
                String.format("%s should not equal %s", description, other));
        return this;
    }
}