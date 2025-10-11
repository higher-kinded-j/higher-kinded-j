// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.test.api.TypeClassTest.kindHelper;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MaybeKindHelper Complete Test Suite")
class MaybeKindHelperTest extends TypeClassTestBase<MaybeKind.Witness, String, String> {

    @Override
    protected Kind<MaybeKind.Witness, String> createValidKind() {
        return MaybeKindHelper.MAYBE.widen(Maybe.just("Success"));
    }

    @Override
    protected Kind<MaybeKind.Witness, String> createValidKind2() {
        return MaybeKindHelper.MAYBE.widen(Maybe.just("Another"));
    }

    @Override
    protected Function<String, String> createValidMapper() {
        return String::toUpperCase;
    }

    @Override
    protected BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> createEqualityChecker() {
        return (k1, k2) -> MaybeKindHelper.MAYBE.narrow(k1).equals(MaybeKindHelper.MAYBE.narrow(k2));
    }

    @Nested
    @DisplayName("Complete KindHelper Test Suite")
    class CompleteTestSuite {
        @Test
        @DisplayName("Run complete KindHelper test suite for Maybe")
        void completeKindHelperTestSuite() {
            Maybe<String> validInstance = Maybe.just("Success");

            kindHelper()
                    .forMaybe(validInstance)
                    .test();
        }

        @Test
        @DisplayName("Complete test suite with multiple Maybe types")
        void completeTestSuiteWithMultipleTypes() {
            List<Maybe<String>> testInstances =
                    List.of(Maybe.just("Success"), Maybe.nothing(), Maybe.just(""), Maybe.just("Test"));

            for (Maybe<String> instance : testInstances) {
                kindHelper()
                        .forMaybe(instance)
                        .test();
            }
        }

        @Test
        @DisplayName("Comprehensive test with implementation validation")
        void comprehensiveTestWithImplementationValidation() {
            Maybe<String> validInstance = Maybe.just("Comprehensive");

            kindHelper()
                    .forMaybe(validInstance)
                    .testWithValidation(MaybeKindHelper.class);
        }
    }

    @Nested
    @DisplayName("Individual Component Tests")
    class IndividualComponentTests {
        @Test
        @DisplayName("Test round-trip widen/narrow operations")
        void testRoundTripOperations() {
            Maybe<String> validInstance = Maybe.just("test");

            kindHelper()
                    .forMaybe(validInstance)
                    .skipValidations()
                    .skipInvalidType()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test null parameter validations")
        void testNullParameterValidations() {
            kindHelper()
                    .forMaybe(Maybe.just("test"))
                    .skipRoundTrip()
                    .skipInvalidType()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test invalid Kind type handling")
        void testInvalidKindType() {
            kindHelper()
                    .forMaybe(Maybe.just("test"))
                    .skipRoundTrip()
                    .skipValidations()
                    .skipIdempotency()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test idempotency of operations")
        void testIdempotency() {
            Maybe<String> validInstance = Maybe.just("idempotent");

            kindHelper()
                    .forMaybe(validInstance)
                    .skipRoundTrip()
                    .skipValidations()
                    .skipInvalidType()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Test edge cases and boundary conditions")
        void testEdgeCases() {
            Maybe<String> validInstance = Maybe.just("edge");

            kindHelper()
                    .forMaybe(validInstance)
                    .skipRoundTrip()
                    .skipValidations()
                    .skipInvalidType()
                    .skipIdempotency()
                    .test();
        }
    }

    @Nested
    @DisplayName("Specific Maybe Behaviour Tests")
    class SpecificBehaviourTests {
        @Test
        @DisplayName("Both Just and Nothing instances work correctly")
        void testJustAndNothingInstances() {
            Maybe<String> just = Maybe.just("Success");
            Maybe<String> nothing = Maybe.nothing();

            kindHelper().forMaybe(just).test();
            kindHelper().forMaybe(nothing).test();
        }

        @Test
        @DisplayName("Nothing singleton is preserved")
        void testNothingSingletonPreserved() {
            Maybe<String> nothing1 = Maybe.nothing();
            Maybe<Integer> nothing2 = Maybe.nothing();

            kindHelper().forMaybe(nothing1).test();
            kindHelper().forMaybe(nothing2).test();

            assertThat(nothing1).isSameAs(nothing2);
        }

        @Test
        @DisplayName("Complex value types work correctly")
        void testComplexValueTypes() {
            List<String> complexValue = List.of("a", "b", "c");
            Maybe<List<String>> complexMaybe = Maybe.just(complexValue);

            kindHelper().forMaybe(complexMaybe).test();

            assertThat(complexMaybe.get()).isSameAs(complexValue);
        }

        @Test
        @DisplayName("Empty string is valid Just value")
        void testEmptyStringIsValidJust() {
            Maybe<String> emptyJust = Maybe.just("");

            kindHelper().forMaybe(emptyJust).test();

            assertThat(emptyJust.isJust()).isTrue();
            assertThat(emptyJust.get()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Performance and Memory Tests")
    class PerformanceTests {
        @Test
        @DisplayName("Holder creates minimal overhead")
        void testMinimalOverhead() {
            Maybe<String> original = Maybe.just("test");

            kindHelper()
                    .forMaybe(original)
                    .skipPerformance()
                    .test();
        }

        @Test
        @DisplayName("Multiple operations are idempotent")
        void testIdempotentOperations() {
            Maybe<String> original = Maybe.just("idempotent");

            kindHelper()
                    .forMaybe(original)
                    .skipRoundTrip()
                    .skipValidations()
                    .skipInvalidType()
                    .skipEdgeCases()
                    .test();
        }

        @Test
        @DisplayName("Performance characteristics test")
        void testPerformanceCharacteristics() {
            if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
                Maybe<String> testInstance = Maybe.just("performance_test");

                kindHelper()
                        .forMaybe(testInstance)
                        .withPerformanceTests()
                        .test();
            }
        }

        @Test
        @DisplayName("Memory efficiency test")
        void testMemoryEfficiency() {
            if (Boolean.parseBoolean(System.getProperty("test.performance", "false"))) {
                Maybe<String> testInstance = Maybe.just("memory_test");

                kindHelper()
                        .forMaybe(testInstance)
                        .withPerformanceTests()
                        .test();
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases and Corner Cases")
    class EdgeCasesTests {
        @Test
        @DisplayName("All combinations of Maybe states")
        void testAllMaybeStates() {
            List<Maybe<String>> allStates =
                    List.of(
                            Maybe.just("success"),
                            Maybe.just(""),
                            Maybe.nothing(),
                            Maybe.fromNullable("value"),
                            Maybe.fromNullable(null));

            for (Maybe<String> state : allStates) {
                kindHelper().forMaybe(state).test();
            }
        }

        @Test
        @DisplayName("Full lifecycle test")
        void testFullLifecycle() {
            Maybe<String> original = Maybe.just("lifecycle_test");
            Maybe<String> nothingOriginal = Maybe.nothing();

            kindHelper().forMaybe(original).test();
            kindHelper().forMaybe(nothingOriginal).test();
        }
    }

    @Nested
    @DisplayName("Advanced Testing Scenarios")
    class AdvancedTestingScenarios {
        @Test
        @DisplayName("Concurrent access test")
        void testConcurrentAccess() {
            if (Boolean.parseBoolean(System.getProperty("test.concurrency", "false"))) {
                Maybe<String> testInstance = Maybe.just("concurrent_test");

                kindHelper()
                        .forMaybe(testInstance)
                        .withConcurrencyTests()
                        .test();
            }
        }

        @Test
        @DisplayName("Implementation standards validation")
        void testImplementationStandards() {
            KindHelperTestPattern.validateImplementationStandards(Maybe.class, MaybeKindHelper.class);
        }

        @Test
        @DisplayName("Quick test for fast test suites")
        void testQuickValidation() {
            Maybe<String> testInstance = Maybe.just("quick_test");

            kindHelper()
                    .forMaybe(testInstance)
                    .test();
        }

        @Test
        @DisplayName("Stress test with complex scenarios")
        void testComplexStressScenarios() {
            List<Maybe<Object>> complexInstances =
                    List.of(
                            Maybe.just("simple_string"),
                            Maybe.just(42),
                            Maybe.just(List.of(1, 2, 3)),
                            Maybe.just(java.util.Map.of("key", "value")),
                            Maybe.nothing());

            for (Maybe<Object> instance : complexInstances) {
                kindHelper().forMaybe(instance).test();
            }
        }
    }

    @Nested
    @DisplayName("Comprehensive Coverage Tests")
    class ComprehensiveCoverageTests {
        @Test
        @DisplayName("All Maybe types and states")
        void testAllMaybeTypesAndStates() {
            List<Maybe<String>> allStates =
                    List.of(
                            Maybe.just("success"),
                            Maybe.just(""),
                            Maybe.nothing(),
                            Maybe.fromNullable("value"),
                            Maybe.fromNullable(null));

            for (Maybe<String> state : allStates) {
                kindHelper().forMaybe(state).test();
            }
        }

        @Test
        @DisplayName("Full lifecycle test")
        void testFullLifecycle() {
            Maybe<String> original = Maybe.just("lifecycle_test");

            kindHelper().forMaybe(original).test();

            KindHelperTestPattern.validateImplementationStandards(Maybe.class, MaybeKindHelper.class);
        }
    }
}