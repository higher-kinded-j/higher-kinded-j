// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Direct tests for the Reader<R, A> implementation. */
@DisplayName("Reader<R, A> Direct Tests")
class ReaderTest {

  // Simple environment type for testing
  record Config(String dbUrl, int timeout) {}

  final Config testConfig = new Config("jdbc:test", 5000);

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {
    @Test
    void of_shouldCreateReaderFromFunction() {
      Function<Config, String> getUrl = Config::dbUrl;
      Reader<Config, String> reader = Reader.of(getUrl);
      assertThat(reader.run(testConfig)).isEqualTo("jdbc:test");
    }

    @Test
    void of_shouldThrowNPEForNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> Reader.of(null))
          .withMessageContaining("runFunction for Reader.of cannot be null");
    }

    @Test
    void constant_shouldCreateReaderReturningConstant() {
      Reader<Config, Integer> reader = Reader.constant(42);
      assertThat(reader.run(testConfig)).isEqualTo(42);
      assertThat(reader.run(new Config("other", 0))).isEqualTo(42); // Ignores env
    }

    @Test
    void constant_shouldAllowNullConstant() {
      Reader<Config, String> reader = Reader.constant(null);
      assertThat(reader.run(testConfig)).isNull();
    }

    @Test
    void ask_shouldCreateReaderReturningEnvironment() {
      Reader<Config, Config> reader = Reader.ask();
      assertThat(reader.run(testConfig)).isSameAs(testConfig);
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceMethods {
    final Reader<Config, String> getUrlReader = Reader.of(Config::dbUrl);
    final Reader<Config, Integer> getTimeoutReader = Reader.of(Config::timeout);

    @Test
    void run_shouldExecuteFunctionWithEnvironment() {
      assertThat(getUrlReader.run(testConfig)).isEqualTo("jdbc:test");
      assertThat(getTimeoutReader.run(testConfig)).isEqualTo(5000);
    }

    @Test
    void map_shouldTransformReaderResult() {
      Reader<Config, Integer> urlLengthReader = getUrlReader.map(String::length);
      assertThat(urlLengthReader.run(testConfig)).isEqualTo("jdbc:test".length());
    }

    @Test
    void map_shouldThrowNPEForNullMapper() {
      assertThatNullPointerException()
          .isThrownBy(() -> getUrlReader.map(null))
          .withMessageContaining("mapper function for Reader.map cannot be null");
    }

    @Test
    void flatMap_shouldComposeReaders() {
      // Reader 1: Gets the timeout
      // Reader 2 (created by function): Gets URL if timeout > 1000, else constant "low"
      Function<Integer, Reader<Config, String>> getUrlIfHighTimeout =
          timeout ->
              (timeout > 1000)
                  ? Reader.of(Config::dbUrl) // Uses original env implicitly
                  : Reader.constant("low timeout");

      Reader<Config, String> composedReader = getTimeoutReader.flatMap(getUrlIfHighTimeout);

      // Test with high timeout config
      assertThat(composedReader.run(testConfig)).isEqualTo("jdbc:test");

      // Test with low timeout config
      Config lowTimeoutConfig = new Config("jdbc:low", 500);
      assertThat(composedReader.run(lowTimeoutConfig)).isEqualTo("low timeout");
    }

    @Test
    void flatMap_shouldThrowNPEForNullMapperFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> getTimeoutReader.flatMap(null))
          .withMessageContaining("flatMap mapper function for Reader.flatMap cannot be null");
    }

    @Test
    void flatMap_shouldThrowNPEIfMapperReturnsNull() {
      Function<Integer, Reader<Config, String>> nullReturningMapper = timeout -> null;
      Reader<Config, String> reader = getTimeoutReader.flatMap(nullReturningMapper);

      // The exception occurs when reader.run is called
      assertThatNullPointerException()
          .isThrownBy(() -> reader.run(testConfig))
          .withMessageContaining("flatMap function returned null Reader");
    }
  }

  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsHashCodeTests {
    // Readers are functional interfaces. Standard equality is reference equality.
    // Different lambdas implementing the same logic are NOT equal.
    @Test
    void readerEqualityIsReferenceBased() {
      Reader<String, Integer> r1 = String::length;
      Reader<String, Integer> r2 =
          String::length; // Same logic, different lambda instance (usually)
      Reader<String, Integer> r3 = s -> s.length(); // Different lambda syntax
      Reader<String, Integer> r4 = r1; // Same instance

      assertThat(r1).isNotEqualTo(r2); // Different instances
      assertThat(r1).isNotEqualTo(r3);
      assertThat(r1).isEqualTo(r4); // Same instance

      // Hashcode *might* be the same for identical lambdas in some JVMs, but not guaranteed.
      // Don't rely on hashcode equality for distinct lambda instances.
    }
  }
}
