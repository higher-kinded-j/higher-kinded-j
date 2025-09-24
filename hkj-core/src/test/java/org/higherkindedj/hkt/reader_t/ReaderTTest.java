// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderT Class Tests (Outer: OptionalKind.Witness, Env: Config)")
class ReaderTTest {

  // Using R_ENV naming convention to match the updated ReaderT
  record Config(String setting) {}

  final Config testConfig1 = new Config("value1");
  final Config testConfig2 = new Config("value2");

  private Monad<OptionalKind.Witness> outerMonad;

  // R_ENV is Config
  private <A> Optional<A> runReaderT(
      ReaderT<OptionalKind.Witness, Config, A> readerT, Config config) {
    Kind<OptionalKind.Witness, A> resultKind = readerT.run().apply(config);
    return OPTIONAL.narrow(resultKind);
  }

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;
  }

  @Nested
  @DisplayName("Static Factory Methods")
  class FactoryTests {

    @Test
    @DisplayName("of should wrap the run function")
    void of_wrapsRunFunction() {
      Function<Config, Kind<OptionalKind.Witness, String>> runFunc =
          cfg -> OPTIONAL.widen(Optional.of("Computed:" + cfg.setting()));
      ReaderT<OptionalKind.Witness, Config, String> rt = ReaderT.of(runFunc);

      assertThat(rt.run()).isSameAs(runFunc);
      assertThat(runReaderT(rt, testConfig1)).isPresent().contains("Computed:value1");
    }

    @Test
    @DisplayName("of should throw NullPointerException for null function")
    void of_throwsOnNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> ReaderT.of(null))
          .withMessageContaining("ReaderTHolder contained null ReaderT instance");
    }

    @Test
    @DisplayName("lift should create ReaderT ignoring env, returning F<A>")
    void lift_ignoresEnv() {
      Kind<OptionalKind.Witness, Integer> outerValue = OPTIONAL.widen(Optional.of(123));
      Kind<OptionalKind.Witness, Integer> outerEmpty = OPTIONAL.widen(Optional.empty());

      ReaderT<OptionalKind.Witness, Config, Integer> rtValue =
          ReaderT.liftF(outerMonad, outerValue);
      ReaderT<OptionalKind.Witness, Config, Integer> rtEmpty =
          ReaderT.liftF(outerMonad, outerEmpty);

      assertThat(rtValue.run().apply(testConfig1)).isSameAs(outerValue);
      assertThat(rtValue.run().apply(testConfig2)).isSameAs(outerValue);
      assertThat(runReaderT(rtValue, testConfig1)).isPresent().contains(123);

      assertThat(rtEmpty.run().apply(testConfig1)).isSameAs(outerEmpty);
      assertThat(rtEmpty.run().apply(testConfig2)).isSameAs(outerEmpty);
      assertThat(runReaderT(rtEmpty, testConfig1)).isEmpty();
    }

    @Test
    @DisplayName("lift should throw NullPointerException for null monad or value")
    void lift_throwsOnNulls() {
      Kind<OptionalKind.Witness, Integer> outerValue = OPTIONAL.widen(Optional.of(123));
      assertThatNullPointerException()
          .isThrownBy(() -> ReaderT.liftF(null, outerValue))
          .withMessageContaining("Outer Monad cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ReaderT.liftF(outerMonad, null))
          .withMessageContaining("fa for lift cannot be null");
    }

    @Test
    @DisplayName("reader should lift R_ENV -> A into R_ENV -> F<A>")
    void reader_liftsFunction() {
      Function<Config, String> plainFunc = cfg -> "Setting: " + cfg.setting();
      ReaderT<OptionalKind.Witness, Config, String> rt = ReaderT.reader(outerMonad, plainFunc);

      assertThat(runReaderT(rt, testConfig1)).isPresent().contains("Setting: value1");
      assertThat(runReaderT(rt, testConfig2)).isPresent().contains("Setting: value2");
    }

    @Test
    @DisplayName("reader should lift function returning null into F<Optional.empty>")
    void reader_liftsFunctionReturningNull() {
      Function<Config, String> nullFunc = cfg -> null;
      ReaderT<OptionalKind.Witness, Config, String> rt = ReaderT.reader(outerMonad, nullFunc);
      // outerMonad.of(null) for OptionalMonad results in Optional.empty()
      assertThat(runReaderT(rt, testConfig1)).isEmpty();
    }

    @Test
    @DisplayName("reader should throw NullPointerException for null monad or function")
    void reader_throwsOnNulls() {
      Function<Config, String> plainFunc = Config::setting;
      assertThatNullPointerException()
          .isThrownBy(() -> ReaderT.reader(null, plainFunc))
          .withMessageContaining("Outer Monad cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> ReaderT.reader(outerMonad, null))
          .withMessageContaining("function f for reader cannot be null");
    }

    @Test
    @DisplayName("ask should create ReaderT returning environment in F")
    void ask_returnsEnvironmentInF() {
      ReaderT<OptionalKind.Witness, Config, Config> rt = ReaderT.ask(outerMonad);
      assertThat(runReaderT(rt, testConfig1)).isPresent().containsSame(testConfig1);
      assertThat(runReaderT(rt, testConfig2)).isPresent().containsSame(testConfig2);
    }

    @Test
    @DisplayName("ask should throw NullPointerException for null monad")
    void ask_throwsOnNullMonad() {
      assertThatNullPointerException()
          .isThrownBy(() -> ReaderT.ask(null))
          .withMessageContaining("Outer Monad cannot be null");
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceTests {

    @Test
    @DisplayName("run() should return the underlying function")
    void run_returnsFunction() {
      Function<Config, Kind<OptionalKind.Witness, String>> runFunc =
          cfg -> outerMonad.of(cfg.setting());
      ReaderT<OptionalKind.Witness, Config, String> rt = ReaderT.of(runFunc);
      assertThat(rt.run()).isSameAs(runFunc);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectTests {

    @Test
    @DisplayName("equals/hashCode should compare based on run function instance")
    void equalsHashCode_comparesRunFunction() {
      Function<Config, Kind<OptionalKind.Witness, String>> runFunc1 = cfg -> outerMonad.of("A");
      Function<Config, Kind<OptionalKind.Witness, String>> runFunc2 = cfg -> outerMonad.of("A");
      Function<Config, Kind<OptionalKind.Witness, String>> runFunc3 = cfg -> outerMonad.of("B");

      ReaderT<OptionalKind.Witness, Config, String> rt1a = ReaderT.of(runFunc1);
      ReaderT<OptionalKind.Witness, Config, String> rt1b = ReaderT.of(runFunc1);
      ReaderT<OptionalKind.Witness, Config, String> rt2 = ReaderT.of(runFunc2);
      ReaderT<OptionalKind.Witness, Config, String> rt3 = ReaderT.of(runFunc3);

      assertThat(rt1a).isEqualTo(rt1b);
      assertThat(rt1a).hasSameHashCodeAs(rt1b);

      assertThat(rt1a).isNotEqualTo(rt2);
      assertThat(rt1a).isNotEqualTo(rt3);

      assertThat(rt1a).isNotEqualTo(null);
      assertThat(rt1a).isNotEqualTo(runFunc1);
    }

    @Test
    @DisplayName("toString should represent the structure")
    void toString_representsStructure() {
      Function<Config, Kind<OptionalKind.Witness, String>> runFunc =
          cfg -> outerMonad.of(cfg.setting());
      ReaderT<OptionalKind.Witness, Config, String> rt = ReaderT.of(runFunc);

      assertThat(rt.toString()).startsWith("ReaderT[run=");
      assertThat(rt.toString()).contains(runFunc.toString());
      assertThat(rt.toString()).endsWith("]");
    }
  }
}
