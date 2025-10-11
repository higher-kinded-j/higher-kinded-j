package org.higherkindedj.hkt.state;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.test.api.CoreTypeTest;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.higherkindedj.hkt.test.base.TypeClassTestBase;
import org.higherkindedj.hkt.test.data.TestFunctions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StateFunctor<S> Complete Test Suite")
class StateFunctorTest extends TypeClassTestBase<StateKind.Witness<Integer>, Integer, String> {

    private final Integer initialState = 10;
    private StateFunctor<Integer> functor;

    @Override
    protected Kind<StateKind.Witness<Integer>, Integer> createValidKind() {
        State<Integer, Integer> state = State.of(s -> new StateTuple<>(s + 1, s + 1));
        return STATE.widen(state);
    }

    @Override
    protected Kind<StateKind.Witness<Integer>, Integer> createValidKind2() {
        State<Integer, Integer> state = State.of(s -> new StateTuple<>(s * 2, s * 2));
        return STATE.widen(state);
    }

    @Override
    protected Function<Integer, String> createValidMapper() {
        return TestFunctions.INT_TO_STRING;
    }

    @Override
    protected Function<String, String> createSecondMapper() {
        return String::toUpperCase;
    }

    @Override
    protected BiPredicate<Kind<StateKind.Witness<Integer>, ?>, Kind<StateKind.Witness<Integer>, ?>>
    createEqualityChecker() {
        return (k1, k2) -> {
            StateTuple<Integer, ?> result1 = STATE.runState(k1, initialState);
            StateTuple<Integer, ?> result2 = STATE.runState(k2, initialState);
            return result1.equals(result2);
        };
    }

    @BeforeEach
    void setUpFunctor() {
        functor = new StateFunctor<>();
    }

    @Nested
    @DisplayName("Complete Type Class Test Suite")
    class CompleteTypeClassTestSuite {

        @Test
        @DisplayName("Run complete Functor test pattern")
        void runCompleteFunctorTestPattern() {
            TypeClassTest.<StateKind.Witness<Integer>>functor(StateFunctor.class)
                    .<Integer>instance(functor)
                    .<String>withKind(validKind)
                    .withMapper(validMapper)
                    .withSecondMapper(secondMapper)
                    .withEqualityChecker(equalityChecker)
                    .selectTests()
                    .skipExceptions() // State is lazy - exceptions deferred until run()
                    .test();
        }
    }

    @Nested
    @DisplayName("Functor Operations")
    class FunctorOperations {

        @Test
        @DisplayName("map should transform value whilst preserving state transition")
        void mapShouldTransformValue() {
            State<Integer, Integer> state = State.of(s -> new StateTuple<>(s + 5, s * 2));
            Kind<StateKind.Witness<Integer>, Integer> kind = STATE.widen(state);

            Kind<StateKind.Witness<Integer>, String> mapped = functor.map(validMapper, kind);

            StateTuple<Integer, String> result = STATE.runState(mapped, initialState);
            assertThat(result.value()).isEqualTo("15"); // initialState + 5
            assertThat(result.state()).isEqualTo(20); // initialState * 2
        }

        @Test
        @DisplayName("map should compose correctly")
        void mapShouldComposeCorrectly() {
            Kind<StateKind.Witness<Integer>, String> mapped =
                    functor.<String, String>map(secondMapper, functor.map(validMapper, validKind));

            StateTuple<Integer, String> result = STATE.runState(mapped, initialState);
            assertThat(result.value()).isEqualTo("11"); // (10 + 1).toString().toUpperCase()
        }

        @Test
        @DisplayName("map should handle identity function")
        void mapShouldHandleIdentityFunction() {
            Function<Integer, Integer> identity = i -> i;
            Kind<StateKind.Witness<Integer>, Integer> mapped = functor.map(identity, validKind);

            assertThat(equalityChecker.test(mapped, validKind))
                    .as("map with identity should equal original")
                    .isTrue();
        }

        @Test
        @DisplayName("map should preserve state threading")
        void mapShouldPreserveStateThreading() {
            State<Integer, Integer> state = State.of(s -> new StateTuple<>(s, s + 10));
            Kind<StateKind.Witness<Integer>, Integer> kind = STATE.widen(state);

            Kind<StateKind.Witness<Integer>, String> mapped = functor.map(validMapper, kind);

            StateTuple<Integer, String> result = STATE.runState(mapped, initialState);
            assertThat(result.value()).isEqualTo("10");
            assertThat(result.state()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Functor Validations")
    class FunctorValidations {

        @Test
        @DisplayName("map should validate mapper is non-null")
        void mapShouldValidateMapperIsNonNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> functor.map(null, validKind))
                    .withMessageContaining("function f for StateFunctor.map cannot be null");
        }

        @Test
        @DisplayName("map should validate Kind is non-null")
        void mapShouldValidateKindIsNonNull() {
            assertThatNullPointerException()
                    .isThrownBy(() -> functor.map(validMapper, null))
                    .withMessageContaining("Kind for StateFunctor.map cannot be null");
        }
    }

    @Nested
    @DisplayName("Lazy Evaluation")
    class LazyEvaluation {

        @Test
        @DisplayName("map should defer exceptions until run()")
        void mapShouldDeferExceptions() {
            RuntimeException testException = new RuntimeException("Test exception");
            Function<Integer, String> throwingMapper =
                    i -> {
                        throw testException;
                    };

            Kind<StateKind.Witness<Integer>, String> mapped = functor.map(throwingMapper, validKind);
            assertThat(mapped).as("map should return successfully (lazy evaluation)").isNotNull();

            assertThatThrownBy(() -> STATE.runState(mapped, initialState))
                    .as("Exception should be thrown during run()")
                    .isSameAs(testException);
        }

        @Test
        @DisplayName("map should allow building complex computations before evaluation")
        void mapShouldAllowBuildingComplexComputations() {
            // Step 1: Integer -> String (multiply by 2, convert to string)
            Kind<StateKind.Witness<Integer>, String> step1 =
                    functor.map(i -> String.valueOf(i * 2), validKind);

            // Step 2: String -> String (prepend "Value: ")
            Kind<StateKind.Witness<Integer>, String> step2 =
                    functor.map(s -> "Value: " + s, step1);

            // Step 3: String -> Integer (get length)
            Kind<StateKind.Witness<Integer>, Integer> computation =
                    functor.map(s -> s.length(), step2);

            // Building the computation succeeds
            assertThat(computation).isNotNull();

            // Evaluation produces correct result
            StateTuple<Integer, Integer> result = STATE.runState(computation, initialState);
            assertThat(result.value()).isEqualTo(9); // "Value: 22".length()
        }
    }

    @Nested
    @DisplayName("Functor Laws")
    class FunctorLaws {

        @Test
        @DisplayName("Identity law: map(id) == id")
        void identityLaw() {
            Function<Integer, Integer> identity = i -> i;
            Kind<StateKind.Witness<Integer>, Integer> mapped = functor.map(identity, validKind);

            assertThat(equalityChecker.test(mapped, validKind))
                    .as("Functor Identity Law: map(id, fa) == fa")
                    .isTrue();
        }

        @Test
        @DisplayName("Composition law: map(g . f) == map(g) . map(f)")
        void compositionLaw() {
            Function<Integer, String> f = validMapper;
            Function<String, String> g = secondMapper;

            // Left side: map(g ∘ f)
            Function<Integer, String> composed = i -> g.apply(f.apply(i));
            Kind<StateKind.Witness<Integer>, String> leftSide = functor.map(composed, validKind);

            // Right side: map(g, map(f, validKind))
            Kind<StateKind.Witness<Integer>, String> intermediate = functor.map(f, validKind);
            Kind<StateKind.Witness<Integer>, String> rightSide = functor.map(g, intermediate);

            assertThat(equalityChecker.test(leftSide, rightSide))
                    .as("Functor Composition Law: map(g ∘ f, fa) == map(g, map(f, fa))")
                    .isTrue();
        }

        @Test
        @DisplayName("map preserves structure")
        void mapPreservesStructure() {
            State<Integer, Integer> state = State.of(s -> new StateTuple<>(42, 100));
            Kind<StateKind.Witness<Integer>, Integer> kind = STATE.widen(state);

            Kind<StateKind.Witness<Integer>, String> mapped = functor.map(validMapper, kind);

            StateTuple<Integer, String> result = STATE.runState(mapped, initialState);
            assertThat(result.value()).isEqualTo("42");
            assertThat(result.state())
                    .as("State should be preserved by map")
                    .isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("CoreTypeTest Integration")
    class CoreTypeTestIntegration {

        @Test
        @DisplayName("Test State core operations using CoreTypeTest API")
        void testStateCoreOperations() {
            State<Integer, Integer> state = State.of(s -> new StateTuple<>(s + 1, s + 1));

            CoreTypeTest.<Integer, Integer>state(State.class)
                    .withState(state)
                    .withInitialState(initialState)
                    .withMappers(TestFunctions.INT_TO_STRING)
                    .testAll();
        }

        @Test
        @DisplayName("Test State with validation configuration")
        void testStateWithValidationConfiguration() {
            State<Integer, Integer> state = State.of(s -> new StateTuple<>(s * 2, s + 5));

            CoreTypeTest.<Integer, Integer>state(State.class)
                    .withState(state)
                    .withInitialState(initialState)
                    .withMappers(TestFunctions.INT_TO_STRING)
                    .configureValidation()
                    .useInheritanceValidation()
                    .withMapFrom(StateFunctor.class)
                    .withFlatMapFrom(StateMonad.class)
                    .testAll();
        }

        @Test
        @DisplayName("Test State selective operations")
        void testStateSelectiveOperations() {
            State<Integer, Integer> state = State.pure(42);

            CoreTypeTest.<Integer, Integer>state(State.class)
                    .withState(state)
                    .withInitialState(initialState)
                    .withMappers(TestFunctions.INT_TO_STRING)
                    .onlyFactoryMethods()
                    .testAll();
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("map should handle null-returning mapper")
        void mapShouldHandleNullReturningMapper() {
            Function<Integer, String> nullMapper = i -> null;
            Kind<StateKind.Witness<Integer>, String> mapped = functor.map(nullMapper, validKind);

            StateTuple<Integer, String> result = STATE.runState(mapped, initialState);
            assertThat(result.value()).isNull();
            assertThat(result.state()).isNotNull();
        }

        @Test
        @DisplayName("map should handle stateless computations")
        void mapShouldHandleStatelessComputations() {
            State<Integer, Integer> stateless = State.of(s -> new StateTuple<>(42, s));
            Kind<StateKind.Witness<Integer>, Integer> kind = STATE.widen(stateless);

            Kind<StateKind.Witness<Integer>, String> mapped = functor.map(validMapper, kind);

            StateTuple<Integer, String> result1 = STATE.runState(mapped, 10);
            StateTuple<Integer, String> result2 = STATE.runState(mapped, 100);

            assertThat(result1.value()).isEqualTo("42");
            assertThat(result2.value()).isEqualTo("42");
            assertThat(result1.state()).isEqualTo(10);
            assertThat(result2.state()).isEqualTo(100);
        }

        @Test
        @DisplayName("map should work with complex state transformations")
        void mapShouldWorkWithComplexStateTransformations() {
            State<Integer, Integer> complex = State.of(s -> new StateTuple<>(s * s, s + 1));
            Kind<StateKind.Witness<Integer>, Integer> kind = STATE.widen(complex);

            Kind<StateKind.Witness<Integer>, String> mapped =
                    functor.map(i -> "Square: " + i, kind);

            StateTuple<Integer, String> result = STATE.runState(mapped, 5);
            assertThat(result.value()).isEqualTo("Square: 25");
            assertThat(result.state()).isEqualTo(6);
        }
    }
}