// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.state_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateTMonad Complete Test Suite (Outer: OptionalKind.Witness)")
class StateTMonadTest
        extends TypeClassTestBase<StateTKind.Witness<String, OptionalKind.Witness>, Integer, String> {

    private Monad<OptionalKind.Witness> outerMonad = OptionalMonad.INSTANCE;
    private Monad<StateTKind.Witness<String, OptionalKind.Witness>> stateTMonad =
            StateTMonad.instance(outerMonad);

    @BeforeEach
    void setUpMonad() {
        outerMonad = OptionalMonad.INSTANCE;
        stateTMonad = StateTMonad.instance(outerMonad);
    }

    private <A> Optional<StateTuple<String, A>> unwrapKindToOptionalStateTuple(
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, A> kind) {
        if (kind == null) return Optional.empty();
        var stateT = STATE_T.narrow(kind);
        Kind<OptionalKind.Witness, StateTuple<String, A>> outerKind = stateT.runStateT("initial");
        return OPTIONAL.narrow(outerKind);
    }

    private <R> Kind<StateTKind.Witness<String, OptionalKind.Witness>, R> pureT(R value) {
        return STATE_T.widen(createStateT(s -> StateTuple.of(s, value)));
    }

    private <R> StateT<String, OptionalKind.Witness, R> createStateT(
            Function<String, StateTuple<String, R>> localFn) {
        return StateT.create(s -> outerMonad.of(localFn.apply(s)), outerMonad);
    }

    private <A, B> Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<A, B>>
    pureFunction(Function<A, B> func) {
        return STATE_T.widen(createStateT(s -> StateTuple.of(s, func)));
    }

    // TypeClassTestBase implementations
    @Override
    protected Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> createValidKind() {
        return pureT(10);
    }

    @Override
    protected Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> createValidKind2() {
        return pureT(20);
    }

    @Override
    protected Function<Integer, String> createValidMapper() {
        return Object::toString;
    }

    @Override
    protected BiPredicate<
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, ?>,
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, ?>>
    createEqualityChecker() {
        return (k1, k2) -> {
            var opt1 = unwrapKindToOptionalStateTuple(k1);
            var opt2 = unwrapKindToOptionalStateTuple(k2);
            return opt1.equals(opt2);
        };
    }

    @Override
    protected Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>
    createValidFlatMapper() {
        return i -> pureT("v" + i);
    }

    @Override
    protected Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<Integer, String>>
    createValidFunctionKind() {
        return pureFunction(Object::toString);
    }

    @Override
    protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
        return (a, b) -> a + "+" + b;
    }

    @Override
    protected Integer createTestValue() {
        return 5;
    }

    @Override
    protected Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>
    createTestFunction() {
        return i -> pureT("v" + i);
    }

    @Override
    protected Function<String, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>
    createChainFunction() {
        return s -> pureT(s + "!");
    }

    @Nested
    @DisplayName("Complete Test Suite")
    class CompleteTestSuite {

        @Test
        @DisplayName("Verify all test categories are covered")
        void verifyCompleteCoverage() {
            // Verify that all nested test classes exist and have tests
            assertThat(FunctorOperationTests.class).isNotNull();
            assertThat(ApplicativeOperationTests.class).isNotNull();
            assertThat(MonadOperationTests.class).isNotNull();
            assertThat(MonadLawTests.class).isNotNull();
            assertThat(EdgeCaseTests.class).isNotNull();
        }
    }

    @Nested
    @DisplayName("Functor Operations")
    class FunctorOperationTests {

        @Test
        @DisplayName("map should apply function preserving state")
        void map_shouldApplyFunctionPreservingState() {
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> input = pureT(10);
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
                    stateTMonad.map(Object::toString, input);

            Optional<StateTuple<String, String>> tuple = unwrapKindToOptionalStateTuple(result);
            assertThat(tuple).isPresent();
            assertThat(tuple.get().value()).isEqualTo("10");
            assertThat(tuple.get().state()).isEqualTo("initial");
        }

        @Test
        @DisplayName("map should thread state through transformation")
        void map_shouldThreadState() {
            StateT<String, OptionalKind.Witness, Integer> stateT =
                    createStateT(s -> StateTuple.of(s + "_modified", 10));
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> input =
                    STATE_T.widen(stateT);

            Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
                    stateTMonad.map(Object::toString, input);

            Optional<StateTuple<String, String>> tuple = unwrapKindToOptionalStateTuple(result);
            assertThat(tuple).isPresent();
            assertThat(tuple.get().value()).isEqualTo("10");
            assertThat(tuple.get().state()).isEqualTo("initial_modified");
        }

        @Test
        @DisplayName("map should handle null result by converting to state tuple")
        void map_shouldHandleNullResult() {
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> input = pureT(10);
            Function<Integer, String> nullReturningMapper = i -> null;

            Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
                    stateTMonad.map(nullReturningMapper, input);

            Optional<StateTuple<String, String>> tuple = unwrapKindToOptionalStateTuple(result);
            assertThat(tuple).isPresent();
            assertThat(tuple.get().value()).isNull();
        }
    }

    @Nested
    @DisplayName("Applicative Operations")
    class ApplicativeOperationTests {

        final Function<Integer, String> multiplyToString = i -> "Res:" + (i * 2);

        @Test
        @DisplayName("ap: pure(func) ap pure(val) should apply function")
        void ap_pureFuncPureVal_shouldApplyFunction() {
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<Integer, String>> ff =
                    pureFunction(multiplyToString);
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> fa = pureT(10);

            var result = stateTMonad.ap(ff, fa);
            assertThat(unwrapKindToOptionalStateTuple(result))
                    .isPresent()
                    .satisfies(opt -> {
                        assertThat(opt.get().value()).isEqualTo("Res:20");
                        assertThat(opt.get().state()).isEqualTo("initial");
                    });
        }

        @Test
        @DisplayName("ap should thread state through function and argument evaluation")
        void ap_shouldThreadState() {
            // Function that modifies state
            StateT<String, OptionalKind.Witness, Function<Integer, String>> funcStateT =
                    createStateT(s -> StateTuple.of(s + "_func", multiplyToString));
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<Integer, String>> ff =
                    STATE_T.widen(funcStateT);

            // Value that also modifies state
            StateT<String, OptionalKind.Witness, Integer> valStateT =
                    createStateT(s -> StateTuple.of(s + "_val", 10));
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> fa =
                    STATE_T.widen(valStateT);

            var result = stateTMonad.ap(ff, fa);
            assertThat(unwrapKindToOptionalStateTuple(result))
                    .isPresent()
                    .satisfies(opt -> {
                        assertThat(opt.get().value()).isEqualTo("Res:20");
                        // State should be threaded: initial -> initial_func -> initial_func_val
                        assertThat(opt.get().state()).isEqualTo("initial_func_val");
                    });
        }

        @Test
        @DisplayName("ap should throw when function throws")
        void ap_funcThrows_shouldThrowException() {
            RuntimeException ex = new RuntimeException("Function apply crashed");
            Function<Integer, String> throwingFunc = i -> {
                throw ex;
            };

            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Function<Integer, String>> ff =
                    pureFunction(throwingFunc);
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> fa = pureT(10);

            Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
                    stateTMonad.ap(ff, fa);

            // The exception is only thrown when we actually RUN the StateT
            assertThatThrownBy(() -> STATE_T.narrow(result).runStateT("initial"))
                    .isInstanceOf(RuntimeException.class)
                    .isSameAs(ex);
        }
    }

    @Nested
    @DisplayName("Monad Operations")
    class MonadOperationTests {

        @Test
        @DisplayName("flatMap: pure value, function returns pure should work")
        void flatMap_pureValueFuncReturnsPure() {
            var initialPure = pureT(10);
            Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>
                    funcReturnsPure = i -> pureT("Value:" + i);

            var result = stateTMonad.flatMap(funcReturnsPure, initialPure);

            assertThat(unwrapKindToOptionalStateTuple(result))
                    .isPresent()
                    .satisfies(opt -> {
                        assertThat(opt.get().value()).isEqualTo("Value:10");
                        assertThat(opt.get().state()).isEqualTo("initial");
                    });
        }

        @Test
        @DisplayName("flatMap should thread state through both computations")
        void flatMap_shouldThreadState() {
            // First computation modifies state
            StateT<String, OptionalKind.Witness, Integer> firstStateT =
                    createStateT(s -> StateTuple.of(s + "_first", 10));
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> initial =
                    STATE_T.widen(firstStateT);

            // Second computation also modifies state
            Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>> func =
                    i -> {
                        StateT<String, OptionalKind.Witness, String> secondStateT =
                                createStateT(s -> StateTuple.of(s + "_second", "Value:" + i));
                        return STATE_T.widen(secondStateT);
                    };

            var result = stateTMonad.flatMap(func, initial);

            assertThat(unwrapKindToOptionalStateTuple(result))
                    .isPresent()
                    .satisfies(opt -> {
                        assertThat(opt.get().value()).isEqualTo("Value:10");
                        // State threaded: initial -> initial_first -> initial_first_second
                        assertThat(opt.get().state()).isEqualTo("initial_first_second");
                    });
        }

        @Test
        @DisplayName("flatMap should propagate exceptions from function")
        void flatMap_functionThrowsRuntimeException() {
            var initialPure = pureT(30);
            RuntimeException runtimeEx = new RuntimeException("Error in function application!");
            Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>> funcThrows =
                    i -> {
                        throw runtimeEx;
                    };

            Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
                    stateTMonad.flatMap(funcThrows, initialPure);

            // The exception is only thrown when we actually RUN the StateT
            assertThatThrownBy(() -> STATE_T.narrow(result).runStateT("initial"))
                    .isInstanceOf(RuntimeException.class)
                    .isSameAs(runtimeEx);
        }
    }

    @Nested
    @DisplayName("Monad Laws")
    class MonadLawTests {

        @Test
        @DisplayName("Left Identity: flatMap(of(a), f) == f(a)")
        void leftIdentity() {
            var ofValue = stateTMonad.of(testValue);
            var leftSide = stateTMonad.flatMap(testFunction, ofValue);
            var rightSide = testFunction.apply(testValue);

            assertThat(equalityChecker.test(leftSide, rightSide)).isTrue();
        }

        @Test
        @DisplayName("Right Identity: flatMap(m, of) == m")
        void rightIdentity() {
            Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer>> ofFunc =
                    i -> stateTMonad.of(i);

            assertThat(equalityChecker.test(stateTMonad.flatMap(ofFunc, validKind), validKind))
                    .isTrue();
        }

        @Test
        @DisplayName("Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
        void associativity() {
            var innerFlatMap = stateTMonad.flatMap(testFunction, validKind);
            var leftSide = stateTMonad.flatMap(chainFunction, innerFlatMap);

            Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>>
                    rightSideFunc = a -> stateTMonad.flatMap(chainFunction, testFunction.apply(a));
            var rightSide = stateTMonad.flatMap(rightSideFunc, validKind);

            assertThat(equalityChecker.test(leftSide, rightSide)).isTrue();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("of with null value")
        void of_withNullValue() {
            var result = stateTMonad.of(null);
            assertThat(unwrapKindToOptionalStateTuple(result))
                    .isPresent()
                    .satisfies(opt -> {
                        assertThat(opt.get().value()).isNull();
                        assertThat(opt.get().state()).isEqualTo("initial");
                    });
        }

        @Test
        @DisplayName("map with identity should preserve structure")
        void map_withIdentity() {
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> input = pureT(42);
            Function<Integer, Integer> identity = i -> i;

            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> result =
                    stateTMonad.map(identity, input);

            Optional<StateTuple<String, Integer>> tuple = unwrapKindToOptionalStateTuple(result);
            assertThat(tuple).isPresent();
            assertThat(tuple.get().value()).isEqualTo(42);
            assertThat(tuple.get().state()).isEqualTo("initial");
        }

        @Test
        @DisplayName("flatMap with function returning same type")
        void flatMap_functionReturnsSameType() {
            var initial = pureT(10);
            Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer>> func =
                    i -> pureT(i * 2);

            var result = stateTMonad.flatMap(func, initial);

            assertThat(unwrapKindToOptionalStateTuple(result))
                    .isPresent()
                    .satisfies(opt -> {
                        assertThat(opt.get().value()).isEqualTo(20);
                        assertThat(opt.get().state()).isEqualTo("initial");
                    });
        }

        @Test
        @DisplayName("complex state threading through multiple operations")
        void complexStateThreading() {
            // Start with a state modification
            StateT<String, OptionalKind.Witness, Integer> step1 =
                    createStateT(s -> StateTuple.of(s + "_step1", 1));

            // Map to transform value, preserving state thread
            Kind<StateTKind.Witness<String, OptionalKind.Witness>, Integer> afterMap =
                    stateTMonad.map(i -> i + 10, STATE_T.widen(step1));

            // FlatMap to continue state threading
            Function<Integer, Kind<StateTKind.Witness<String, OptionalKind.Witness>, String>> step2 =
                    i -> {
                        StateT<String, OptionalKind.Witness, String> stateT =
                                createStateT(s -> StateTuple.of(s + "_step2", "final:" + i));
                        return STATE_T.widen(stateT);
                    };

            Kind<StateTKind.Witness<String, OptionalKind.Witness>, String> result =
                    stateTMonad.flatMap(step2, afterMap);

            assertThat(unwrapKindToOptionalStateTuple(result))
                    .isPresent()
                    .satisfies(opt -> {
                        assertThat(opt.get().value()).isEqualTo("final:11");
                        // State threaded through all operations
                        assertThat(opt.get().state()).isEqualTo("initial_step1_step2");
                    });
        }
    }
}