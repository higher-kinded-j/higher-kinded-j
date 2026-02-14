// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.higherkindedj.hkt.reader.Reader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for ReaderPath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable operations, environment handling,
 * and conversions. ReaderPath represents computations that read from an environment.
 */
@DisplayName("ReaderPath<R, A> Complete Test Suite")
class ReaderPathTest {

  private static final String TEST_VALUE = "test";

  // Simple environment record for testing
  record Config(String host, int port, boolean debug) {}

  private static final Config TEST_CONFIG = new Config("localhost", 8080, true);

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.readerPure() creates ReaderPath that ignores environment")
    void readerPureIgnoresEnvironment() {
      ReaderPath<Config, String> path = Path.readerPure(TEST_VALUE);

      assertThat(path.run(TEST_CONFIG)).isEqualTo(TEST_VALUE);
      assertThat(path.run(new Config("other", 9090, false))).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("Path.ask() returns the entire environment")
    void askReturnsEntireEnvironment() {
      ReaderPath<Config, Config> path = Path.ask();

      assertThat(path.run(TEST_CONFIG)).isEqualTo(TEST_CONFIG);
    }

    @Test
    @DisplayName("Path.asks() extracts value from environment")
    void asksExtractsFromEnvironment() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      assertThat(path.run(TEST_CONFIG)).isEqualTo("localhost");
    }

    @Test
    @DisplayName("Path.asks() validates non-null function")
    void asksValidatesNonNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.asks(null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("ReaderPath.pure() creates path with constant value")
    void staticPureCreatesConstantPath() {
      ReaderPath<Config, Integer> path = ReaderPath.pure(42);

      assertThat(path.run(TEST_CONFIG)).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Run and Terminal Methods")
  class RunAndTerminalMethodsTests {

    @Test
    @DisplayName("run() executes computation with environment")
    void runExecutesWithEnvironment() {
      ReaderPath<Config, String> path = Path.asks(c -> c.host() + ":" + c.port());

      assertThat(path.run(TEST_CONFIG)).isEqualTo("localhost:8080");
    }

    @Test
    @DisplayName("run() validates non-null environment")
    void runValidatesNonNullEnvironment() {
      ReaderPath<Config, String> path = Path.readerPure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.run(null))
          .withMessageContaining("environment must not be null");
    }

    @Test
    @DisplayName("toReader() returns underlying Reader")
    void toReaderReturnsUnderlyingReader() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      assertThat(path.toReader()).isNotNull();
      assertThat(path.toReader().run(TEST_CONFIG)).isEqualTo("localhost");
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value")
    void mapTransformsValue() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      ReaderPath<Config, Integer> result = path.map(String::length);

      assertThat(result.run(TEST_CONFIG)).isEqualTo(9); // "localhost".length()
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      ReaderPath<Config, String> result =
          path.map(String::toUpperCase).map(s -> s + "!").map(s -> "[" + s + "]");

      assertThat(result.run(TEST_CONFIG)).isEqualTo("[LOCALHOST!]");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      ReaderPath<Config, String> path = Path.asks(Config::host);
      AtomicBoolean called = new AtomicBoolean(false);

      ReaderPath<Config, String> result = path.peek(v -> called.set(true));

      assertThat(result.run(TEST_CONFIG)).isEqualTo("localhost");
      assertThat(called).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains dependent computations")
    void viaChainsComputations() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      ReaderPath<Config, String> result =
          path.via(host -> ReaderPath.<Config, String>asks(c -> host + ":" + c.port()));

      assertThat(result.run(TEST_CONFIG)).isEqualTo("localhost:8080");
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      ReaderPath<Config, String> path = Path.readerPure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates non-null result")
    void viaValidatesNonNullResult() {
      ReaderPath<Config, String> path = Path.readerPure(TEST_VALUE);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(s -> null).run(TEST_CONFIG))
          .withMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("via() validates result is ReaderPath")
    void viaValidatesResultType() {
      ReaderPath<Config, String> path = Path.readerPure(TEST_VALUE);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(s -> Path.just(s)).run(TEST_CONFIG))
          .withMessageContaining("via mapper must return ReaderPath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      ReaderPath<Config, Integer> viaResult = path.via(s -> Path.readerPure(s.length()));
      @SuppressWarnings("unchecked")
      ReaderPath<Config, Integer> flatMapResult =
          (ReaderPath<Config, Integer>) path.flatMap(s -> Path.readerPure(s.length()));

      assertThat(flatMapResult.run(TEST_CONFIG)).isEqualTo(viaResult.run(TEST_CONFIG));
    }

    @Test
    @DisplayName("then() sequences computations discarding value")
    void thenSequencesComputationsDiscardingValue() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      ReaderPath<Config, Integer> result = path.then(() -> Path.asks(Config::port));

      assertThat(result.run(TEST_CONFIG)).isEqualTo(8080);
    }

    @Test
    @DisplayName("then() throws for incompatible path type")
    void thenThrowsForIncompatibleType() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      ReaderPath<Config, Integer> result = path.then(() -> Path.id(42));

      assertThatIllegalArgumentException()
          .isThrownBy(() -> result.run(TEST_CONFIG))
          .withMessageContaining("then supplier must return ReaderPath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines two values from same environment")
    void zipWithCombinesTwoValues() {
      ReaderPath<Config, String> hostPath = Path.asks(Config::host);
      ReaderPath<Config, Integer> portPath = Path.asks(Config::port);

      ReaderPath<Config, String> result = hostPath.zipWith(portPath, (h, p) -> h + ":" + p);

      assertThat(result.run(TEST_CONFIG)).isEqualTo("localhost:8080");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(Path.asks(Config::host), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-ReaderPath")
    void zipWithThrowsWhenGivenNonReaderPath() {
      ReaderPath<Config, String> path = Path.asks(Config::host);
      IdPath<Integer> idPath = Path.id(42);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(idPath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-ReaderPath");
    }

    @Test
    @DisplayName("zipWith3() combines three values")
    void zipWith3CombinesThreeValues() {
      ReaderPath<Config, String> hostPath = Path.asks(Config::host);
      ReaderPath<Config, Integer> portPath = Path.asks(Config::port);
      ReaderPath<Config, Boolean> debugPath = Path.asks(Config::debug);

      ReaderPath<Config, String> result =
          hostPath.zipWith3(portPath, debugPath, (h, p, d) -> h + ":" + p + " (debug=" + d + ")");

      assertThat(result.run(TEST_CONFIG)).isEqualTo("localhost:8080 (debug=true)");
    }
  }

  @Nested
  @DisplayName("Reader-Specific Operations")
  class ReaderSpecificOperationsTests {

    @Test
    @DisplayName("local() modifies environment for sub-computation")
    void localModifiesEnvironment() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      ReaderPath<Config, String> result =
          path.local(c -> new Config("remapped", c.port(), c.debug()));

      assertThat(result.run(TEST_CONFIG)).isEqualTo("remapped");
    }

    @Test
    @DisplayName("local() validates non-null function")
    void localValidatesNonNullFunction() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      assertThatNullPointerException()
          .isThrownBy(() -> path.local(null))
          .withMessageContaining("f must not be null");
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toIOPath() converts to IOPath that captures environment")
    void toIOPathConvertsCorrectly() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      IOPath<String> result = path.toIOPath(TEST_CONFIG);

      assertThat(result.unsafeRun()).isEqualTo("localhost");
    }

    @Test
    @DisplayName("toIdPath() converts to IdPath with environment")
    void toIdPathConvertsCorrectly() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      IdPath<String> result = path.toIdPath(TEST_CONFIG);

      assertThat(result.get()).isEqualTo("localhost");
    }

    @Test
    @DisplayName("toMaybePath() converts to MaybePath with environment")
    void toMaybePathConvertsCorrectly() {
      ReaderPath<Config, String> path = Path.asks(Config::host);

      MaybePath<String> result = path.toMaybePath(TEST_CONFIG);

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo("localhost");
    }

    @Test
    @DisplayName("toMaybePath() returns Nothing for null value")
    void toMaybePathReturnsNothingForNull() {
      ReaderPath<Config, String> path = Path.readerPure(null);

      MaybePath<String> result = path.toMaybePath(TEST_CONFIG);

      assertThat(result.run().isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      ReaderPath<Config, String> path = ReaderPath.pure("test");

      assertThat(path).isEqualTo(path);
    }

    @Test
    @DisplayName("equals() returns false for non-ReaderPath")
    void equalsReturnsFalseForNonReaderPath() {
      ReaderPath<Config, String> path = ReaderPath.pure("test");

      assertThat(path.equals("not a ReaderPath")).isFalse();
      assertThat(path.equals(null)).isFalse();
      assertThat(path.equals(Path.id(42))).isFalse();
    }

    @Test
    @DisplayName("equals() compares underlying readers")
    void equalsComparesUnderlyingReaders() {
      // Two different ReaderPath instances wrapping the same Reader
      Reader<Config, String> reader = Reader.constant("test");
      ReaderPath<Config, String> path1 = Path.reader(reader);
      ReaderPath<Config, String> path2 = Path.reader(reader);

      assertThat(path1).isEqualTo(path2);
    }

    @Test
    @DisplayName("hashCode() returns consistent value")
    void hashCodeReturnsConsistentValue() {
      ReaderPath<Config, String> path = ReaderPath.pure("test");

      int hash1 = path.hashCode();
      int hash2 = path.hashCode();

      assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("hashCode() based on underlying reader")
    void hashCodeBasedOnUnderlyingReader() {
      Reader<Config, String> reader = Reader.constant("test");
      ReaderPath<Config, String> path1 = Path.reader(reader);
      ReaderPath<Config, String> path2 = Path.reader(reader);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      ReaderPath<Config, String> path = Path.readerPure(TEST_VALUE);

      assertThat(path.toString()).contains("ReaderPath");
    }
  }

  @Nested
  @DisplayName("Practical Usage Patterns")
  class PracticalUsagePatternsTests {

    @Test
    @DisplayName("Can build connection string from config")
    void canBuildConnectionString() {
      ReaderPath<Config, String> connectionString =
          Path.<Config>ask().map(c -> "jdbc:mysql://" + c.host() + ":" + c.port() + "/db");

      assertThat(connectionString.run(TEST_CONFIG)).isEqualTo("jdbc:mysql://localhost:8080/db");
    }

    @Test
    @DisplayName("Can compose multiple environment reads")
    void canComposeMultipleReads() {
      ReaderPath<Config, String> combined =
          ReaderPath.<Config, String>asks(Config::host)
              .via(
                  host ->
                      ReaderPath.<Config, Integer>asks(Config::port)
                          .via(
                              port ->
                                  ReaderPath.<Config, Boolean>asks(Config::debug)
                                      .map(
                                          debug -> host + ":" + port + (debug ? " [DEBUG]" : ""))));

      assertThat(combined.run(TEST_CONFIG)).isEqualTo("localhost:8080 [DEBUG]");
    }
  }
}
