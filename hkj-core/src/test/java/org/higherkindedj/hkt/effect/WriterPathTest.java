// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.writer.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive test suite for WriterPath.
 *
 * <p>Tests cover factory methods, Composable/Combinable/Chainable operations, log accumulation, and
 * conversions.
 */
@DisplayName("WriterPath<W, A> Complete Test Suite")
class WriterPathTest {

  private static final String TEST_VALUE = "test";

  // String concatenation monoid for testing
  private static final Monoid<String> STRING_MONOID =
      new Monoid<>() {
        @Override
        public String empty() {
          return "";
        }

        @Override
        public String combine(String a, String b) {
          return a + b;
        }
      };

  // List monoid for testing
  @SuppressWarnings("unchecked")
  private static final Monoid<List<String>> LIST_MONOID =
      new Monoid<>() {
        @Override
        public List<String> empty() {
          return List.of();
        }

        @Override
        public List<String> combine(List<String> a, List<String> b) {
          return Stream.concat(a.stream(), b.stream()).toList();
        }
      };

  @Nested
  @DisplayName("Factory Methods via Path")
  class FactoryMethodsTests {

    @Test
    @DisplayName("Path.writerPure() creates WriterPath with empty log")
    void writerPureCreatesPathWithEmptyLog() {
      WriterPath<String, Integer> path = Path.writerPure(42, STRING_MONOID);

      assertThat(path.value()).isEqualTo(42);
      assertThat(path.written()).isEmpty();
    }

    @Test
    @DisplayName("Path.tell() creates WriterPath that only logs")
    void tellCreatesLoggingPath() {
      WriterPath<String, Unit> path = Path.tell("logged message", STRING_MONOID);

      assertThat(path.value()).isEqualTo(Unit.INSTANCE);
      assertThat(path.written()).isEqualTo("logged message");
    }

    @Test
    @DisplayName("WriterPath.pure() creates path with empty log")
    void staticPureCreatesEmptyLogPath() {
      WriterPath<String, String> path = WriterPath.pure(TEST_VALUE, STRING_MONOID);

      assertThat(path.value()).isEqualTo(TEST_VALUE);
      assertThat(path.written()).isEmpty();
    }

    @Test
    @DisplayName("WriterPath.tell() produces log with Unit value")
    void staticTellProducesLog() {
      WriterPath<List<String>, Unit> path = WriterPath.tell(List.of("entry1"), LIST_MONOID);

      assertThat(path.value()).isEqualTo(Unit.INSTANCE);
      assertThat(path.written()).containsExactly("entry1");
    }

    @Test
    @DisplayName("WriterPath.writer() creates path with both value and log")
    void staticWriterCreatesPathWithValueAndLog() {
      WriterPath<String, Integer> path = WriterPath.writer(42, "logged|", STRING_MONOID);

      assertThat(path.value()).isEqualTo(42);
      assertThat(path.written()).isEqualTo("logged|");
    }

    @Test
    @DisplayName("Path.writer() creates WriterPath from existing Writer")
    void pathWriterCreatesPathFromExistingWriter() {
      Writer<String, Integer> writer = new Writer<>("existing-log|", 42);
      WriterPath<String, Integer> path = Path.writer(writer, STRING_MONOID);

      assertThat(path.value()).isEqualTo(42);
      assertThat(path.written()).isEqualTo("existing-log|");
    }

    @Test
    @DisplayName("Path.writer() validates non-null writer")
    void pathWriterValidatesNonNullWriter() {
      assertThatNullPointerException()
          .isThrownBy(() -> Path.writer(null, STRING_MONOID))
          .withMessageContaining("writer must not be null");
    }

    @Test
    @DisplayName("Path.writer() validates non-null monoid")
    void pathWriterValidatesNonNullMonoid() {
      Writer<String, Integer> writer = new Writer<>("log|", 42);
      assertThatNullPointerException()
          .isThrownBy(() -> Path.writer(writer, null))
          .withMessageContaining("monoid must not be null");
    }
  }

  @Nested
  @DisplayName("Run and Terminal Methods")
  class RunAndTerminalMethodsTests {

    @Test
    @DisplayName("run() returns both value and log")
    void runReturnsBothValueAndLog() {
      WriterPath<String, Integer> path = WriterPath.pure(42, STRING_MONOID);

      var result = path.run();

      assertThat(result.value()).isEqualTo(42);
      assertThat(result.log()).isEmpty();
    }

    @Test
    @DisplayName("value() returns just the value")
    void valueReturnsJustValue() {
      WriterPath<String, Integer> path = WriterPath.pure(42, STRING_MONOID);

      assertThat(path.value()).isEqualTo(42);
    }

    @Test
    @DisplayName("written() returns just the log")
    void writtenReturnsJustLog() {
      WriterPath<String, Unit> path = WriterPath.tell("log", STRING_MONOID);

      assertThat(path.written()).isEqualTo("log");
    }

    @Test
    @DisplayName("monoid() returns the monoid instance")
    void monoidReturnsMonoidInstance() {
      WriterPath<String, Integer> path = WriterPath.pure(42, STRING_MONOID);

      assertThat(path.monoid()).isSameAs(STRING_MONOID);
    }
  }

  @Nested
  @DisplayName("Composable Operations (map, peek)")
  class ComposableOperationsTests {

    @Test
    @DisplayName("map() transforms value preserving log")
    void mapTransformsValuePreservingLog() {
      WriterPath<String, Integer> path =
          WriterPath.tell("init|", STRING_MONOID).then(() -> WriterPath.pure(5, STRING_MONOID));

      WriterPath<String, Integer> result = path.map(n -> n * 2);

      assertThat(result.value()).isEqualTo(10);
      assertThat(result.written()).isEqualTo("init|");
    }

    @Test
    @DisplayName("map() validates null mapper")
    void mapValidatesNullMapper() {
      WriterPath<String, String> path = WriterPath.pure(TEST_VALUE, STRING_MONOID);

      assertThatNullPointerException()
          .isThrownBy(() -> path.map(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("map() chains correctly")
    void mapChainsCorrectly() {
      WriterPath<String, String> path = WriterPath.pure("hello", STRING_MONOID);

      WriterPath<String, String> result =
          path.map(String::toUpperCase).map(s -> s + "!").map(s -> "[" + s + "]");

      assertThat(result.value()).isEqualTo("[HELLO!]");
    }

    @Test
    @DisplayName("peek() observes value without modifying")
    void peekObservesValueWithoutModifying() {
      WriterPath<String, String> path = WriterPath.pure(TEST_VALUE, STRING_MONOID);
      AtomicBoolean called = new AtomicBoolean(false);

      WriterPath<String, String> result = path.peek(v -> called.set(true));

      assertThat(result.value()).isEqualTo(TEST_VALUE);
      assertThat(called).isTrue();
    }
  }

  @Nested
  @DisplayName("Chainable Operations (via, flatMap, then)")
  class ChainableOperationsTests {

    @Test
    @DisplayName("via() chains computations accumulating logs")
    void viaChainsAccumulatingLogs() {
      WriterPath<String, Integer> path =
          WriterPath.tell("step1|", STRING_MONOID).then(() -> WriterPath.pure(5, STRING_MONOID));

      WriterPath<String, String> result =
          path.via(
              n ->
                  WriterPath.tell("step2|", STRING_MONOID)
                      .then(() -> WriterPath.pure("value=" + n, STRING_MONOID)));

      assertThat(result.value()).isEqualTo("value=5");
      assertThat(result.written()).isEqualTo("step1|step2|");
    }

    @Test
    @DisplayName("via() validates null mapper")
    void viaValidatesNullMapper() {
      WriterPath<String, String> path = WriterPath.pure(TEST_VALUE, STRING_MONOID);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(null))
          .withMessageContaining("mapper must not be null");
    }

    @Test
    @DisplayName("via() validates non-null result")
    void viaValidatesNonNullResult() {
      WriterPath<String, String> path = WriterPath.pure(TEST_VALUE, STRING_MONOID);

      assertThatNullPointerException()
          .isThrownBy(() -> path.via(s -> null))
          .withMessageContaining("mapper must not return null");
    }

    @Test
    @DisplayName("via() validates result is WriterPath")
    void viaValidatesResultType() {
      WriterPath<String, String> path = WriterPath.pure(TEST_VALUE, STRING_MONOID);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.via(s -> Path.just(s)))
          .withMessageContaining("via mapper must return WriterPath");
    }

    @Test
    @DisplayName("flatMap() is alias for via")
    void flatMapIsAliasForVia() {
      WriterPath<String, String> path = WriterPath.pure("hello", STRING_MONOID);

      WriterPath<String, Integer> viaResult =
          path.via(s -> WriterPath.pure(s.length(), STRING_MONOID));
      @SuppressWarnings("unchecked")
      WriterPath<String, Integer> flatMapResult =
          (WriterPath<String, Integer>)
              path.flatMap(s -> WriterPath.pure(s.length(), STRING_MONOID));

      assertThat(flatMapResult.value()).isEqualTo(viaResult.value());
    }

    @Test
    @DisplayName("then() sequences computations accumulating logs")
    void thenSequencesAccumulatingLogs() {
      WriterPath<String, String> first =
          WriterPath.tell("A|", STRING_MONOID)
              .then(() -> WriterPath.pure("ignored", STRING_MONOID));

      WriterPath<String, Integer> result =
          first.then(
              () ->
                  WriterPath.tell("B|", STRING_MONOID)
                      .then(() -> WriterPath.pure(42, STRING_MONOID)));

      assertThat(result.value()).isEqualTo(42);
      assertThat(result.written()).isEqualTo("A|B|");
    }

    @Test
    @DisplayName("then() throws for incompatible path type")
    void thenThrowsForIncompatibleType() {
      WriterPath<String, String> path = WriterPath.pure(TEST_VALUE, STRING_MONOID);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.then(() -> Path.id(42)))
          .withMessageContaining("then supplier must return WriterPath");
    }
  }

  @Nested
  @DisplayName("Combinable Operations (zipWith)")
  class CombinableOperationsTests {

    @Test
    @DisplayName("zipWith() combines values and accumulates logs")
    void zipWithCombinesValuesAndLogs() {
      WriterPath<String, Integer> first =
          WriterPath.tell("first|", STRING_MONOID).then(() -> WriterPath.pure(10, STRING_MONOID));
      WriterPath<String, Integer> second =
          WriterPath.tell("second|", STRING_MONOID).then(() -> WriterPath.pure(5, STRING_MONOID));

      WriterPath<String, Integer> result = first.zipWith(second, Integer::sum);

      assertThat(result.value()).isEqualTo(15);
      assertThat(result.written()).isEqualTo("first|second|");
    }

    @Test
    @DisplayName("zipWith() validates null parameters")
    void zipWithValidatesNullParameters() {
      WriterPath<String, String> path = WriterPath.pure(TEST_VALUE, STRING_MONOID);

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(null, (a, b) -> a + b))
          .withMessageContaining("other must not be null");

      assertThatNullPointerException()
          .isThrownBy(() -> path.zipWith(WriterPath.pure("x", STRING_MONOID), null))
          .withMessageContaining("combiner must not be null");
    }

    @Test
    @DisplayName("zipWith() throws when given non-WriterPath")
    void zipWithThrowsWhenGivenNonWriterPath() {
      WriterPath<String, String> path = WriterPath.pure(TEST_VALUE, STRING_MONOID);
      IdPath<Integer> idPath = Path.id(42);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> path.zipWith(idPath, (s, i) -> s + i))
          .withMessageContaining("Cannot zipWith non-WriterPath");
    }

    @Test
    @DisplayName("zipWith3() combines three values")
    void zipWith3CombinesThreeValues() {
      WriterPath<String, String> first =
          WriterPath.tell("1|", STRING_MONOID).then(() -> WriterPath.pure("hello", STRING_MONOID));
      WriterPath<String, String> second =
          WriterPath.tell("2|", STRING_MONOID).then(() -> WriterPath.pure(" ", STRING_MONOID));
      WriterPath<String, String> third =
          WriterPath.tell("3|", STRING_MONOID).then(() -> WriterPath.pure("world", STRING_MONOID));

      WriterPath<String, String> result = first.zipWith3(second, third, (a, b, c) -> a + b + c);

      assertThat(result.value()).isEqualTo("hello world");
      assertThat(result.written()).isEqualTo("1|2|3|");
    }
  }

  @Nested
  @DisplayName("Writer-Specific Operations")
  class WriterSpecificOperationsTests {

    @Test
    @DisplayName("censor() transforms the log")
    void censorTransformsLog() {
      WriterPath<String, Integer> path =
          WriterPath.tell("hello", STRING_MONOID).then(() -> WriterPath.pure(42, STRING_MONOID));

      WriterPath<String, Integer> result = path.censor(String::toUpperCase);

      assertThat(result.value()).isEqualTo(42);
      assertThat(result.written()).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("censor() validates non-null function")
    void censorValidatesNonNullFunction() {
      WriterPath<String, Integer> path = WriterPath.pure(42, STRING_MONOID);

      assertThatNullPointerException()
          .isThrownBy(() -> path.censor(null))
          .withMessageContaining("f must not be null");
    }

    @Test
    @DisplayName("listen() adds additional log to existing log")
    void listenAddsAdditionalLog() {
      WriterPath<String, Integer> path =
          WriterPath.tell("log|", STRING_MONOID).then(() -> WriterPath.pure(42, STRING_MONOID));

      var result = path.listen("extra");

      assertThat(result.value()).isEqualTo(42);
      assertThat(result.written()).isEqualTo("log|extra");
    }
  }

  @Nested
  @DisplayName("Conversion Methods")
  class ConversionMethodsTests {

    @Test
    @DisplayName("toIOPath() converts to IOPath returning value")
    void toIOPathConvertsCorrectly() {
      WriterPath<String, Integer> path = WriterPath.pure(42, STRING_MONOID);

      IOPath<Integer> result = path.toIOPath();

      assertThat(result.unsafeRun()).isEqualTo(42);
    }

    @Test
    @DisplayName("toIdPath() converts to IdPath")
    void toIdPathConvertsCorrectly() {
      WriterPath<String, Integer> path = WriterPath.pure(42, STRING_MONOID);

      IdPath<Integer> result = path.toIdPath();

      assertThat(result.get()).isEqualTo(42);
    }

    @Test
    @DisplayName("toMaybePath() converts to MaybePath with Just")
    void toMaybePathConvertsToJust() {
      WriterPath<String, String> path = WriterPath.pure(TEST_VALUE, STRING_MONOID);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isJust()).isTrue();
      assertThat(result.run().get()).isEqualTo(TEST_VALUE);
    }

    @Test
    @DisplayName("toMaybePath() converts to Nothing for null")
    void toMaybePathConvertsToNothing() {
      WriterPath<String, String> path = WriterPath.pure(null, STRING_MONOID);

      MaybePath<String> result = path.toMaybePath();

      assertThat(result.run().isNothing()).isTrue();
    }

    @Test
    @DisplayName("toEitherPath() converts to EitherPath with Right")
    void toEitherPathConvertsToRight() {
      WriterPath<String, Integer> path = WriterPath.pure(42, STRING_MONOID);

      EitherPath<String, Integer> result = path.toEitherPath();

      assertThat(result.run().isRight()).isTrue();
      assertThat(result.run().getRight()).isEqualTo(42);
    }
  }

  @Nested
  @DisplayName("Object Methods")
  class ObjectMethodsTests {

    @Test
    @DisplayName("equals() returns true for same instance")
    void equalsReturnsTrueForSameInstance() {
      WriterPath<String, Integer> path = WriterPath.pure(42, STRING_MONOID);

      assertThat(path).isEqualTo(path);
    }

    @Test
    @DisplayName("equals() returns false for non-WriterPath")
    void equalsReturnsFalseForNonWriterPath() {
      WriterPath<String, Integer> path = WriterPath.pure(42, STRING_MONOID);

      assertThat(path.equals("not a WriterPath")).isFalse();
      assertThat(path.equals(null)).isFalse();
      assertThat(path.equals(Path.id(42))).isFalse();
    }

    @Test
    @DisplayName("equals() compares both writer and monoid")
    void equalsComparesBothWriterAndMonoid() {
      WriterPath<String, Integer> path1 = WriterPath.pure(42, STRING_MONOID);
      WriterPath<String, Integer> path2 = WriterPath.pure(42, STRING_MONOID);
      WriterPath<String, Integer> path3 = WriterPath.pure(99, STRING_MONOID);

      assertThat(path1).isEqualTo(path2);
      assertThat(path1).isNotEqualTo(path3);
    }

    @Test
    @DisplayName("equals() returns false for different monoids")
    void equalsReturnsFalseForDifferentMonoids() {
      Monoid<String> otherMonoid =
          new Monoid<>() {
            @Override
            public String empty() {
              return "";
            }

            @Override
            public String combine(String a, String b) {
              return a + b;
            }
          };

      WriterPath<String, Integer> path1 = WriterPath.pure(42, STRING_MONOID);
      WriterPath<String, Integer> path2 = WriterPath.pure(42, otherMonoid);

      // Different monoid instances should result in not equal
      assertThat(path1).isNotEqualTo(path2);
    }

    @Test
    @DisplayName("hashCode() is consistent with equals")
    void hashCodeIsConsistentWithEquals() {
      WriterPath<String, Integer> path1 = WriterPath.pure(42, STRING_MONOID);
      WriterPath<String, Integer> path2 = WriterPath.pure(42, STRING_MONOID);

      assertThat(path1.hashCode()).isEqualTo(path2.hashCode());
    }

    @Test
    @DisplayName("toString() provides meaningful representation")
    void toStringProvidesMeaningfulRepresentation() {
      WriterPath<String, Integer> path = WriterPath.pure(42, STRING_MONOID);

      assertThat(path.toString()).contains("WriterPath");
    }
  }

  @Nested
  @DisplayName("Practical Usage Patterns")
  class PracticalUsagePatternsTests {

    @Test
    @DisplayName("Can accumulate audit log during computation")
    void canAccumulateAuditLog() {
      WriterPath<List<String>, Integer> computation =
          WriterPath.tell(List.of("Starting computation"), LIST_MONOID)
              .then(() -> WriterPath.pure(10, LIST_MONOID))
              .via(
                  n ->
                      WriterPath.tell(List.of("Processing: " + n), LIST_MONOID)
                          .then(() -> WriterPath.pure(n * 2, LIST_MONOID)))
              .via(
                  n ->
                      WriterPath.tell(List.of("Result: " + n), LIST_MONOID)
                          .then(() -> WriterPath.pure(n, LIST_MONOID)));

      assertThat(computation.value()).isEqualTo(20);
      assertThat(computation.written())
          .containsExactly("Starting computation", "Processing: 10", "Result: 20");
    }
  }
}
