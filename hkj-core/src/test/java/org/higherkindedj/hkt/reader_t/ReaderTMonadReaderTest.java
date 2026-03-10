// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ReaderTMonadReader Test Suite")
class ReaderTMonadReaderTest {

  private Monad<OptionalKind.Witness> outerMonad;
  private MonadReader<ReaderTKind.Witness<OptionalKind.Witness, String>, String> monadReader;

  private final String testEnvironment = "test-env";

  @BeforeEach
  void setUp() {
    outerMonad = OptionalMonad.INSTANCE;
    monadReader = new ReaderTMonadReader<>(outerMonad);
  }

  private <A> Optional<A> run(Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> kind) {
    return run(kind, testEnvironment);
  }

  private <A> Optional<A> run(
      Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, A> kind, String env) {
    if (kind == null) return Optional.empty();
    var readerT = READER_T.<OptionalKind.Witness, String, A>narrow(kind);
    return OPTIONAL.narrow(readerT.run().apply(env));
  }

  @Nested
  @DisplayName("Ask Tests")
  class AskTests {

    @Test
    @DisplayName("ask should return the environment")
    void ask_shouldReturnEnvironment() {
      var result = monadReader.ask();
      assertThat(run(result)).isPresent().contains(testEnvironment);
    }

    @Test
    @DisplayName("ask should return different environments")
    void ask_shouldReturnDifferentEnvironments() {
      var result = monadReader.ask();
      assertThat(run(result, "env-1")).isPresent().contains("env-1");
      assertThat(run(result, "env-2")).isPresent().contains("env-2");
    }
  }

  @Nested
  @DisplayName("Local Tests")
  class LocalTests {

    @Test
    @DisplayName("local should modify environment for computation")
    void local_shouldModifyEnvironment() {
      var askResult = monadReader.ask();
      var localResult = monadReader.local(String::toUpperCase, askResult);
      assertThat(run(localResult)).isPresent().contains(testEnvironment.toUpperCase());
    }

    @Test
    @DisplayName("local should restore original environment after scope")
    void local_shouldRestoreOriginalEnvironment() {
      var localComputation = monadReader.local(String::toUpperCase, monadReader.ask());
      var outerComputation = monadReader.ask();

      // Compose: use local, then use outer
      var composed =
          monadReader.flatMap(
              localVal -> monadReader.map(outerVal -> localVal + ":" + outerVal, outerComputation),
              localComputation);

      assertThat(run(composed))
          .isPresent()
          .contains(testEnvironment.toUpperCase() + ":" + testEnvironment);
    }

    @Test
    @DisplayName("local with identity should not change behaviour")
    void local_withIdentity_shouldNotChangeBehaviour() {
      var askResult = monadReader.ask();
      var localResult = monadReader.local(Function.identity(), askResult);
      assertThat(run(localResult)).isEqualTo(run(askResult));
    }

    @Test
    @DisplayName("local should compose correctly")
    void local_shouldCompose() {
      var askResult = monadReader.ask();
      var doubleLocal =
          monadReader.local(s -> s + "!", monadReader.local(String::toUpperCase, askResult));
      // Should apply toUpperCase first, then append "!"
      assertThat(run(doubleLocal)).isPresent().contains(testEnvironment.toUpperCase() + "!");
    }
  }

  @Nested
  @DisplayName("Reader Tests")
  class ReaderTests {

    @Test
    @DisplayName("reader should extract value from environment")
    void reader_shouldExtractFromEnvironment() {
      var result = monadReader.reader(String::length);
      assertThat(run(result)).isPresent().contains(testEnvironment.length());
    }

    @Test
    @DisplayName("reader should be equivalent to map(f, ask())")
    void reader_shouldEqualMapAsk() {
      Function<String, Integer> f = String::length;
      var readerResult = monadReader.reader(f);
      var mapAskResult = monadReader.map(f, monadReader.ask());
      assertThat(run(readerResult)).isEqualTo(run(mapAskResult));
    }
  }

  @Nested
  @DisplayName("Asks Tests")
  class AsksTests {

    @Test
    @DisplayName("asks should be alias for reader")
    void asks_shouldBeAliasForReader() {
      Function<String, Integer> f = String::length;
      var asksResult = monadReader.asks(f);
      var readerResult = monadReader.reader(f);
      assertThat(run(asksResult)).isEqualTo(run(readerResult));
    }
  }

  @Nested
  @DisplayName("MonadReader Laws")
  class LawTests {

    @Test
    @DisplayName("Ask idempotent: flatMap(_ -> ask(), ask()) == ask()")
    void askIdempotent() {
      var leftSide = monadReader.flatMap(_ -> monadReader.ask(), monadReader.ask());
      var rightSide = monadReader.ask();
      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }

    @Test
    @DisplayName("Local-ask coherence: local(f, ask()) == map(f, ask())")
    void localAskCoherence() {
      Function<String, String> f = String::toUpperCase;
      var leftSide = monadReader.local(f, monadReader.ask());
      var rightSide = monadReader.map(f, monadReader.ask());
      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }

    @Test
    @DisplayName("Local identity: local(identity, ma) == ma")
    void localIdentity() {
      var ma = monadReader.of(42);
      var localResult = monadReader.local(Function.identity(), ma);
      assertThat(run(localResult)).isEqualTo(run(ma));
    }

    @Test
    @DisplayName("Local composition: local(f, local(g, ma)) == local(g.compose(f), ma)")
    void localComposition() {
      Function<String, String> f = String::toUpperCase;
      Function<String, String> g = s -> s + "!";
      var ma = monadReader.ask();

      var leftSide = monadReader.local(f, monadReader.local(g, ma));
      var rightSide = monadReader.local(f.andThen(g), ma);
      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }
  }

  @Nested
  @DisplayName("Null Tests")
  class NullTests {

    @Test
    @DisplayName("local should reject null function")
    void local_shouldRejectNullFunction() {
      assertThatThrownBy(() -> monadReader.local(null, monadReader.ask()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("local should reject null computation")
    void local_shouldRejectNullComputation() {
      assertThatThrownBy(() -> monadReader.local(Function.identity(), null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("constructor should reject null outer monad")
    void constructor_shouldRejectNullOuterMonad() {
      assertThatThrownBy(() -> new ReaderTMonadReader<>(null))
          .isInstanceOf(NullPointerException.class);
    }
  }
}
