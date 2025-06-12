// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.trans.reader_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.trans.reader_t.ReaderTKindHelper.READER_T;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderTMonad Tests (Outer F=OptionalKind.Witness, Env R_ENV=ReaderTMonadTest.Config)")
class ReaderTMonadTest {

  record Config(String value) {}

  private final Config testConfig = new Config("testConfigValue");
  private final Config otherConfig = new Config("otherConfigValue");

  private Monad<OptionalKind.Witness> outerMonad;
  private Monad<ReaderTKind.Witness<OptionalKind.Witness, Config>> readerTMonad;

  private <A> Optional<A> runOptReaderT(
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, A> kind, Config config) {
    ReaderT<OptionalKind.Witness, Config, A> readerT = READER_T.narrow(kind);
    Kind<OptionalKind.Witness, A> resultOfRun = readerT.run().apply(config);
    return OPTIONAL.narrow(resultOfRun);
  }

  private <A> Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, A> createReaderTKind(
      Function<Config, Kind<OptionalKind.Witness, A>> runFn) {
    ReaderT<OptionalKind.Witness, Config, A> readerT = ReaderT.of(runFn);
    return READER_T.widen(readerT);
  }

  private <A>
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, A> createEmptyOuterReaderTKind() {
    return createReaderTKind(cfg -> OPTIONAL.widen(Optional.empty()));
  }

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;
    readerTMonad = new ReaderTMonad<>(outerMonad);
  }

  @Test
  @DisplayName("Constructor should throw NullPointerException if outerMonad is null")
  void constructor_nullOuterMonad_throwsNPE() {
    assertThatThrownBy(() -> new ReaderTMonad<>(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Outer Monad instance cannot be null");
  }

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldCreateReaderTReturningValueAndIgnoringEnv() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> kind =
          readerTMonad.of("constant");
      assertThat(runOptReaderT(kind, testConfig)).isPresent().contains("constant");
      assertThat(runOptReaderT(kind, otherConfig)).isPresent().contains("constant");
    }

    @Test
    void of_shouldWrapNullAsOuterOptionalOfEmptyIfOuterMonadDoes() { // OptionalMonad.of(null) ->
      // Optional.empty()
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> kind = readerTMonad.of(null);
      assertThat(runOptReaderT(kind, testConfig)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> initialKind =
        createReaderTKind(cfg -> OPTIONAL.widen(Optional.of(cfg.value().length())));

    Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> emptyOuterKind =
        createEmptyOuterReaderTKind();

    @Test
    void map_shouldApplyFunctionToValueWhenOuterIsPresent() {
      Function<Integer, String> intToString = Object::toString;
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> mappedKind =
          readerTMonad.map(intToString, initialKind);

      assertThat(runOptReaderT(mappedKind, testConfig))
          .isPresent()
          .contains(String.valueOf(testConfig.value().length()));
    }

    @Test
    void map_shouldPropagateOuterEmpty() {
      Function<Integer, String> intToString = Object::toString;
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> mappedKind =
          readerTMonad.map(intToString, emptyOuterKind);
      assertThat(runOptReaderT(mappedKind, testConfig)).isEmpty();
    }

    @Test
    void map_functionReturningNull_shouldResultInOuterEmptyViaOptionalMap() {
      // Optional.map(fnReturningNull) results in Optional.empty()
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> mappedToNullKind =
          readerTMonad.map(val -> (String) null, initialKind);
      assertThat(runOptReaderT(mappedToNullKind, testConfig)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' specific tests")
  class ApSpecificTests {
    Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Function<Integer, String>>
        presentFuncKind =
            createReaderTKind(cfg -> OPTIONAL.widen(Optional.of(i -> cfg.value() + ":" + i)));
    Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> presentValKind =
        createReaderTKind(cfg -> OPTIONAL.widen(Optional.of(cfg.value().length())));

    Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Function<Integer, String>>
        emptyOuterFuncKind = createEmptyOuterReaderTKind();
    Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> emptyOuterValKind =
        createEmptyOuterReaderTKind();

    Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Function<Integer, String>>
        funcProducingNullResultKind =
            createReaderTKind(cfg -> OPTIONAL.widen(Optional.of(i -> (String) null)));

    @Test
    void ap_presentFunc_presentValue_shouldCombine() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> result =
          readerTMonad.ap(presentFuncKind, presentValKind);
      String expected = testConfig.value() + ":" + testConfig.value().length();
      assertThat(runOptReaderT(result, testConfig)).isPresent().contains(expected);
    }

    @Test
    void ap_funcProducesNull_presentValue_shouldResultInEmpty() {
      // When the function wrapped in Optional applies and returns null,
      // outerMonad.ap (OptionalMonad.ap) will result in Optional.empty()
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> result =
          readerTMonad.ap(funcProducingNullResultKind, presentValKind);
      assertThat(runOptReaderT(result, testConfig)).isEmpty();
    }

    @Test
    void ap_emptyOuterFunc_presentValue_shouldBeEmptyOuter() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> result =
          readerTMonad.ap(emptyOuterFuncKind, presentValKind);
      assertThat(runOptReaderT(result, testConfig)).isEmpty();
    }

    @Test
    void ap_presentFunc_emptyOuterValue_shouldBeEmptyOuter() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> result =
          readerTMonad.ap(presentFuncKind, emptyOuterValKind);
      assertThat(runOptReaderT(result, testConfig)).isEmpty();
    }

    @Test
    void ap_emptyOuterFunc_emptyOuterValue_shouldBeEmptyOuter() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> result =
          readerTMonad.ap(emptyOuterFuncKind, emptyOuterValKind);
      assertThat(runOptReaderT(result, testConfig)).isEmpty();
    }
  }

  private <A> void assertReaderTEquals(
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, A> k1,
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, A> k2,
      Config readerEnv) {
    assertThat(runOptReaderT(k1, readerEnv)).isEqualTo(runOptReaderT(k2, readerEnv));
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    int value = 5;
    Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> mValue =
        createReaderTKind(cfg -> outerMonad.of(value + cfg.value().length()));
    Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> mEmpty =
        createEmptyOuterReaderTKind();

    Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String>> f =
        i -> createReaderTKind(cfg -> outerMonad.of("v" + i + cfg.value()));
    Function<String, Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String>> g =
        s -> createReaderTKind(cfg -> outerMonad.of(s + "!" + cfg.value().length()));

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> ofValue =
          readerTMonad.of(value);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> leftSide =
          readerTMonad.flatMap(f, ofValue);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> rightSide = f.apply(value);

      assertReaderTEquals(leftSide, rightSide, testConfig);
      assertReaderTEquals(leftSide, rightSide, otherConfig);
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer>> ofFunc =
          readerTMonad::of;

      assertReaderTEquals(readerTMonad.flatMap(ofFunc, mValue), mValue, testConfig);
      assertReaderTEquals(readerTMonad.flatMap(ofFunc, mEmpty), mEmpty, testConfig);
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> innerFlatMap =
          readerTMonad.flatMap(f, mValue);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> leftSide =
          readerTMonad.flatMap(g, innerFlatMap);

      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String>>
          rightSideFunc = a -> readerTMonad.flatMap(g, f.apply(a));
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> rightSide =
          readerTMonad.flatMap(rightSideFunc, mValue);

      assertReaderTEquals(leftSide, rightSide, testConfig);

      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> innerFlatMapEmpty =
          readerTMonad.flatMap(f, mEmpty);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> leftSideEmpty =
          readerTMonad.flatMap(g, innerFlatMapEmpty);
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> rightSideEmpty =
          readerTMonad.flatMap(rightSideFunc, mEmpty);
      assertReaderTEquals(leftSideEmpty, rightSideEmpty, testConfig);
    }
  }

  @Nested
  @DisplayName("flatMap specific scenarios for ReaderTMonad coverage")
  class FlatMapSpecificCoverageTests {

    @Test
    @DisplayName("flatMap: initial ReaderT's outer monad is empty")
    void flatMap_initialOuterMonadIsEmpty() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> initialEmptyOuter =
          createEmptyOuterReaderTKind();
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String>> func =
          i -> createReaderTKind(cfg -> OPTIONAL.widen(Optional.of("Val:" + i)));

      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> result =
          readerTMonad.flatMap(func, initialEmptyOuter);

      assertThat(runOptReaderT(result, testConfig)).isEmpty();
    }

    @Test
    @DisplayName("flatMap: function f returns a ReaderT whose outer monad is empty")
    void flatMap_functionReturnsEmptyOuterMonad() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> initialPresentOuter =
          createReaderTKind(cfg -> OPTIONAL.widen(Optional.of(cfg.value().length())));

      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String>>
          funcReturnsEmptyOuter = i -> createEmptyOuterReaderTKind();

      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> result =
          readerTMonad.flatMap(funcReturnsEmptyOuter, initialPresentOuter);

      assertThat(runOptReaderT(result, testConfig)).isEmpty();
    }

    @Test
    @DisplayName("flatMap: function f itself throws an exception when run")
    void flatMap_functionThrowsException() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> initialPresentOuter =
          createReaderTKind(cfg -> OPTIONAL.widen(Optional.of(10)));
      RuntimeException ex = new RuntimeException("Function f failed");
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String>>
          funcThrows =
              i -> {
                throw ex;
              };

      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> resultKind =
          readerTMonad.flatMap(funcThrows, initialPresentOuter);
      // Exception is caught during the execution of the ReaderT's run function
      assertThatThrownBy(() -> runOptReaderT(resultKind, testConfig))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(ex);
    }

    @Test
    @DisplayName("flatMap: function f returns null Kind, leads to KindUnwrapException when run")
    void flatMap_functionReturnsNullKind() {
      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, Integer> initialPresentOuter =
          createReaderTKind(cfg -> OPTIONAL.widen(Optional.of(10)));
      Function<Integer, Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String>>
          funcReturnsNull = i -> null;

      Kind<ReaderTKind.Witness<OptionalKind.Witness, Config>, String> resultKind =
          readerTMonad.flatMap(funcReturnsNull, initialPresentOuter);

      assertThatThrownBy(() -> runOptReaderT(resultKind, testConfig))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(ReaderTKindHelper.INVALID_KIND_NULL_MSG);
    }
  }
}
