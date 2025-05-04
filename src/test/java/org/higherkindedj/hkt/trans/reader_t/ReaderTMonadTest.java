package org.higherkindedj.hkt.trans.reader_t;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;

import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


@DisplayName("ReaderT Monad Tests (Outer: Optional, Env: Config)")
class ReaderTMonadTest {

    // Simple Environment for testing
    record Config(String url, int connections) {}

    final Config testConfig1 = new Config("db://localhost", 5);
    final Config testConfig2 = new Config("http://remote", 10);

    // --- Declare fields ---
    private Monad<OptionalKind<?>> outerMonad;
    private ReaderTMonad<OptionalKind<?>, Config> readerTMonad;
    private Function<Integer, String> intToString;
    private Function<String, String> appendWorld;
    private Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> mValue;
    private Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> mOuterEmpty;
    // Function a -> M b (Integer -> ReaderTKind<Optional, Config, String>)
    private Function<Integer, Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String>> fT;
    // Function b -> M c (String -> ReaderTKind<Optional, Config, String>)
    private Function<String, Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String>> gT;


    @BeforeEach
    void setUp() {
        outerMonad = new OptionalMonad();
        readerTMonad = new ReaderTMonad<>(outerMonad);
        intToString = Object::toString;
        appendWorld = s -> s + " world";

        // Initialize mValue and mOuterEmpty using correct wrapping
        mValue = ReaderTKindHelper.wrap(ReaderT.reader(outerMonad, Config::connections));
        mOuterEmpty = outerEmptyT(); // Helper creates wrapped Kind

        // Initialize fT and gT ensuring they return WRAPPED Kinds
        fT = i -> ReaderTKindHelper.wrap( // Explicit wrap
                ReaderT.of(env -> outerMonad.of(env.url() + ":" + i))
        );
        gT = s -> ReaderTKindHelper.wrap( // Explicit wrap
                ReaderT.of(env -> outerMonad.of(s + " [" + env.connections() + "]"))
        );
    }

    // --- Helper Methods ---

    private <A> Optional<A> runReaderT(Kind<ReaderTKind<OptionalKind<?>, Config, ?>, A> kind, Config config) {
        ReaderT<OptionalKind<?>, Config, A> readerT = ReaderTKindHelper.unwrap(kind);
        Kind<OptionalKind<?>, A> resultKind = readerT.run().apply(config);
        Objects.requireNonNull(resultKind, "ReaderT run function returned null Kind for env " + config);
        return OptionalKindHelper.unwrap(resultKind);
    }

    private <A> Kind<ReaderTKind<OptionalKind<?>, Config, ?>, A> successT(A value) {
        return readerTMonad.of(value); // readerTMonad.of returns wrapped Kind
    }

    private <A> Kind<ReaderTKind<OptionalKind<?>, Config, ?>, A> outerEmptyT() {
        return ReaderTKindHelper.wrap(ReaderT.of(r -> OptionalKindHelper.wrap(Optional.empty())));
    }

    private Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> configDependentT(String prefix) {
        return ReaderTKindHelper.wrap(ReaderT.reader(outerMonad, cfg -> prefix + cfg.url()));
    }

    // --- Basic Operations Tests ---
    // ... (Keep OfTests, MapTests, ApTests, FlatMapTests as they were) ...
    @Nested
    @DisplayName("Applicative 'of' tests")
    class OfTests {
        @Test
        void of_shouldCreateReaderTReturningOuterOfValue() {
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> kind = readerTMonad.of("constant");
            assertThat(runReaderT(kind, testConfig1)).isPresent().contains("constant");
            assertThat(runReaderT(kind, testConfig2)).isPresent().contains("constant");
        }

        @Test
        void of_shouldCreateReaderTReturningOuterEmptyForNull() {
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> kind = readerTMonad.of(null);
            assertThat(runReaderT(kind, testConfig1)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Functor 'map' tests")
    class MapTests {
        @Test
        void map_shouldApplyFunctionWhenOuterIsSuccess() {
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> initialKind = configDependentT("URL:");
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> input = readerTMonad.map(String::length, initialKind);
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> result = readerTMonad.map(i -> "Length:" + i, input);

            assertThat(runReaderT(result, testConfig1)).isPresent().contains("Length:" + ("URL:db://localhost".length()));
            assertThat(runReaderT(result, testConfig2)).isPresent().contains("Length:" + ("URL:http://remote".length()));
        }

        @Test
        void map_shouldPropagateOuterEmpty() {
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> input = outerEmptyT();
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> result = readerTMonad.map(Object::toString, input);
            assertThat(runReaderT(result, testConfig1)).isEmpty();
        }
    }


    @Nested
    @DisplayName("Applicative 'ap' tests")
    class ApTests {
        Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Function<Integer, String>> funcKindSuccess;
        Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Function<Integer, String>> funcKindEmpty;
        Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> valKindSuccess;
        Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> valKindEmpty;

        @BeforeEach
        void setUpAp() {
            funcKindSuccess = successT((Function<Integer, String>) x -> "N" + x);
            funcKindEmpty = outerEmptyT();
            valKindSuccess = successT(10);
            valKindEmpty = outerEmptyT();
        }


        @Test
        void ap_shouldApplySuccessFuncToSuccessValue() {
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> result =
                    readerTMonad.ap(funcKindSuccess, valKindSuccess);
            assertThat(runReaderT(result, testConfig1)).isPresent().contains("N10");
        }

        @Test
        void ap_shouldReturnOuterEmptyIfFuncIsEmpty() {
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> result =
                    readerTMonad.ap(funcKindEmpty, valKindSuccess);
            assertThat(runReaderT(result, testConfig1)).isEmpty();
        }

        @Test
        void ap_shouldReturnOuterEmptyIfValueIsEmpty() {
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> result =
                    readerTMonad.ap(funcKindSuccess, valKindEmpty);
            assertThat(runReaderT(result, testConfig1)).isEmpty();
        }
    }


    @Nested
    @DisplayName("Monad 'flatMap' tests")
    class FlatMapTests {
        Function<Integer, Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String>> currentFT;
        Function<Integer, Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String>> currentFOuterEmpty;
        Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> initialSuccess;
        Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> initialOuterEmpty;

        @BeforeEach
        void setUpFlatMap() {
            currentFT = fT; // Use fT from outer setUp (now explicitly wrapped)
            currentFOuterEmpty = i -> outerEmptyT();
            initialSuccess = successT(5);
            initialOuterEmpty = outerEmptyT();
        }


        @Test
        void flatMap_shouldApplyFuncWhenOuterIsSuccess() {
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> result =
                    readerTMonad.flatMap(currentFT, initialSuccess);
            assertThat(runReaderT(result, testConfig1)).isPresent().contains("db://localhost:5");
            assertThat(runReaderT(result, testConfig2)).isPresent().contains("http://remote:5");
        }

        @Test
        void flatMap_shouldPropagateOuterEmptyFromInitial() {
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> result =
                    readerTMonad.flatMap(currentFT, initialOuterEmpty);
            assertThat(runReaderT(result, testConfig1)).isEmpty();
        }

        @Test
        void flatMap_shouldPropagateOuterEmptyFromResulting() {
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> result =
                    readerTMonad.flatMap(currentFOuterEmpty, initialSuccess);
            assertThat(runReaderT(result, testConfig1)).isEmpty();
        }
    }

    // --- Law Tests ---

    @Nested
    @DisplayName("Monad Laws")
    class MonadLaws {
        int value = 5; // Plain value for 'of' tests

        // Helper to run both sides and assert equality for different configs
        private <A> void assertLaw(
                Kind<ReaderTKind<OptionalKind<?>, Config, ?>, A> leftSide,
                Kind<ReaderTKind<OptionalKind<?>, Config, ?>, A> rightSide) {
            assertThat(runReaderT(leftSide, testConfig1))
                    .overridingErrorMessage("Law failed for testConfig1:\nLeft: %s\nRight: %s", runReaderT(leftSide, testConfig1), runReaderT(rightSide, testConfig1))
                    .isEqualTo(runReaderT(rightSide, testConfig1));
            assertThat(runReaderT(leftSide, testConfig2))
                    .overridingErrorMessage("Law failed for testConfig2:\nLeft: %s\nRight: %s", runReaderT(leftSide, testConfig2), runReaderT(rightSide, testConfig2))
                    .isEqualTo(runReaderT(rightSide, testConfig2));
        }

        @Test
        @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
        void leftIdentity() {
            // fT is initialized in outer setUp and uses explicit wrap
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> ofValue = readerTMonad.of(value); // of returns wrapped Kind
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> leftSide = readerTMonad.flatMap(fT, ofValue);
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> rightSide = fT.apply(value); // fT returns wrapped Kind

            assertLaw(leftSide, rightSide);
        }

        @Test
        @DisplayName("2. Right Identity: flatMap(m, of) == m")
        void rightIdentity() {
            // mValue is initialized in outer setUp and is wrapped Kind
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> currentMValue = mValue;

            // Define ofFunc LOCALLY using readerTMonad.of (which correctly wraps)
            Function<Integer, Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer>> ofFunc =
                    i -> readerTMonad.of(i);

            // Success Case
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> leftSideSuccess =
                    readerTMonad.flatMap(ofFunc, currentMValue);
            assertLaw(leftSideSuccess, currentMValue);

            // Test Outer Empty Case
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> currentMOuterEmpty = mOuterEmpty; // Use field
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> leftSideEmpty =
                    readerTMonad.flatMap(ofFunc, currentMOuterEmpty);
            assertLaw(leftSideEmpty, currentMOuterEmpty);
        }

        @Test
        @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
        void associativity() {
            // Use fT, gT, mValue initialized in outer setUp (all are wrapped Kinds)

            // Left Side: flatMap(gT, flatMap(fT, mValue))
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> innerFlatMap =
                    readerTMonad.flatMap(fT, mValue);
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> leftSide =
                    readerTMonad.flatMap(gT, innerFlatMap);

            // Right Side: flatMap(mValue, a -> flatMap(gT, fT.apply(a)))
            Function<Integer, Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String>> rightSideFunc =
                    a -> readerTMonad.flatMap(gT, fT.apply(a));
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> rightSide =
                    readerTMonad.flatMap(rightSideFunc, mValue);

            assertLaw(leftSide, rightSide);

            // Test outer empty propagation
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, Integer> currentMOuterEmpty = mOuterEmpty;
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> innerFlatMapEmpty =
                    readerTMonad.flatMap(fT, currentMOuterEmpty);
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> leftSideEmpty =
                    readerTMonad.flatMap(gT, innerFlatMapEmpty);
            Kind<ReaderTKind<OptionalKind<?>, Config, ?>, String> rightSideEmpty =
                    readerTMonad.flatMap(rightSideFunc, currentMOuterEmpty);
            assertLaw(leftSideEmpty, rightSideEmpty); // Both should result in outer empty
        }
    }
}