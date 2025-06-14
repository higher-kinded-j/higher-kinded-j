// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Function;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.typeclass.StringMonoid;
import org.higherkindedj.hkt.unit.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Direct tests for the Writer<W, A> record. */
@DisplayName("Writer<W, A> Direct Tests")
class WriterTest {

  private Monoid<String> stringMonoid;

  @BeforeEach
  void setUp() {
    stringMonoid = new StringMonoid(); // Use String concatenation as the monoid
  }

  @Nested
  @DisplayName("Factory Methods")
  class FactoryMethods {
    @Test
    void create_shouldStoreLogAndValue() {
      Writer<String, Integer> w = new Writer<>("Log1", 10);
      assertThat(w.log()).isEqualTo("Log1");
      assertThat(w.value()).isEqualTo(10);
    }

    @Test
    void create_shouldAllowNullValue() {
      Writer<String, Integer> w = new Writer<>("Log2", null);
      assertThat(w.log()).isEqualTo("Log2");
      assertThat(w.value()).isNull();
    }

    @Test
    void create_shouldThrowNPEForNullLog() {
      assertThatNullPointerException()
          .isThrownBy(() -> new Writer<>(null, 10))
          .withMessageContaining("Log (W) in Writer cannot be null.");
    }

    @Test
    void value_shouldCreateWriterWithEmptyLog() {
      Writer<String, Integer> w = Writer.value(stringMonoid, 20);
      assertThat(w.log()).isEqualTo(stringMonoid.empty()); // ""
      assertThat(w.value()).isEqualTo(20);
    }

    @Test
    void value_shouldAllowNullValue() {
      Writer<String, Integer> w = Writer.value(stringMonoid, null);
      assertThat(w.log()).isEqualTo(stringMonoid.empty());
      assertThat(w.value()).isNull();
    }

    @Test
    void value_shouldThrowNPEForNullMonoid() {
      // This test should now pass after the fix in Writer.value
      assertThatNullPointerException()
          .isThrownBy(() -> Writer.value(null, 10))
          .withMessageContaining("Monoid<W> cannot be null for Writer.value");
    }

    @Test
    void tell_shouldCreateWriterWithLogAndNullValue() {
      Writer<String, Unit> w = Writer.tell("Message");
      assertThat(w.log()).isEqualTo("Message");
      assertThat(w.value()).isEqualTo(Unit.INSTANCE);
    }

    @Test
    void tell_shouldThrowNPEForNullLog() {
      assertThatNullPointerException()
          .isThrownBy(() -> Writer.tell(null))
          .withMessageContaining("Log message for Writer.tell cannot be null");
    }
  }

  @Nested
  @DisplayName("Instance Methods")
  class InstanceMethods {
    final Writer<String, Integer> writer1 = new Writer<>("Init;", 5);

    @Test
    void map_shouldTransformValueAndKeepLog() {
      Writer<String, String> mappedWriter = writer1.map(i -> "Val:" + i);
      assertThat(mappedWriter.log()).isEqualTo("Init;");
      assertThat(mappedWriter.value()).isEqualTo("Val:5");
    }

    @Test
    void map_shouldHandleMappingToNull() {
      Writer<String, String> mappedWriter = writer1.map(i -> null);
      assertThat(mappedWriter.log()).isEqualTo("Init;");
      assertThat(mappedWriter.value()).isNull();
    }

    @Test
    void map_shouldThrowNPEForNullMapper() {
      assertThatNullPointerException()
          .isThrownBy(() -> writer1.map(null))
          .withMessageContaining("Mapper function cannot be null for Writer.map");
    }

    @Test
    void flatMap_shouldCombineLogsAndTransformValue() {
      Function<Integer, Writer<String, Double>> multiplyAndLog =
          i -> new Writer<>("Mult(" + i + ");", i * 2.0);

      Writer<String, Double> resultWriter = writer1.flatMap(stringMonoid, multiplyAndLog);

      // Log = "Init;" + "Mult(5);"
      assertThat(resultWriter.log()).isEqualTo("Init;Mult(5);");
      // Value = 5 * 2.0
      assertThat(resultWriter.value()).isEqualTo(10.0);
    }

    @Test
    void flatMap_shouldWorkWithTellInSequence() {
      // Start -> Tell -> Final Value
      Writer<String, Integer> start = Writer.value(stringMonoid, 10); // ("", 10)
      Function<Integer, Writer<String, Unit>> logStep =
          i -> Writer.tell("Logged " + i + ";"); // ("Logged 10;", null)
      Function<Unit, Writer<String, String>> finalStep =
          v -> new Writer<>("Final;", "Done"); // ("Final;", "Done")

      Writer<String, String> result =
          start
              .flatMap(
                  stringMonoid,
                  i -> logStep.apply(i)) // Combines "" and "Logged 10;", value becomes null
              .flatMap(
                  stringMonoid,
                  v ->
                      finalStep.apply(
                          v)); // Combines "Logged 10;" and "Final;", value becomes "Done"

      assertThat(result.log()).isEqualTo("Logged 10;Final;");
      assertThat(result.value()).isEqualTo("Done");
    }

    @Test
    void flatMap_shouldThrowNPEForNullMonoid() {
      Function<Integer, Writer<String, Double>> func = i -> new Writer<>("Log", 1.0);
      assertThatNullPointerException()
          .isThrownBy(() -> writer1.flatMap(null, func))
          .withMessageContaining("Monoid<W> cannot be null for Writer.flatMap");
    }

    @Test
    void flatMap_shouldThrowNPEForNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> writer1.flatMap(stringMonoid, null))
          .withMessageContaining("flatMap mapper function cannot be null");
    }

    @Test
    void flatMap_shouldThrowNPEIfFunctionReturnsNull() {
      Function<Integer, Writer<String, Double>> nullReturningFunc = i -> null;
      assertThatNullPointerException()
          .isThrownBy(() -> writer1.flatMap(stringMonoid, nullReturningFunc))
          .withMessageContaining("flatMap function returned a null Writer instance");
    }

    @Test
    void run_shouldReturnValue() {
      assertThat(writer1.run()).isEqualTo(5);
      assertThat(new Writer<>("Log", (String) null).run()).isNull();
    }

    @Test
    void exec_shouldReturnLog() {
      assertThat(writer1.exec()).isEqualTo("Init;");
      assertThat(Writer.value(stringMonoid, 10).exec()).isEqualTo("");
    }
  }

  @Nested
  @DisplayName("equals() and hashCode()")
  class EqualsHashCodeTests {
    @Test
    void writerEquality() {
      Writer<String, Integer> w1a = new Writer<>("Log", 1);
      Writer<String, Integer> w1b = new Writer<>("Log", 1); // Same content
      Writer<String, Integer> w2 = new Writer<>("Log", 2); // Different value
      Writer<String, Integer> w3 = new Writer<>("Other", 1); // Different log
      Writer<String, String> w4 = new Writer<>("Log", "1"); // Different value type

      assertThat(w1a).isEqualTo(w1b);
      assertThat(w1a).hasSameHashCodeAs(w1b);

      assertThat(w1a).isNotEqualTo(w2);
      assertThat(w1a).isNotEqualTo(w3);
      assertThat(w1a).isNotEqualTo(w4);
      assertThat(w1a).isNotEqualTo(null);
      assertThat(w1a).isNotEqualTo("Log");
    }

    @Test
    void writerEqualityWithNullValue() {
      Writer<String, Integer> w1a = new Writer<>("Log", null);
      Writer<String, Integer> w1b = new Writer<>("Log", null);
      Writer<String, Integer> w2 = new Writer<>("Log", 1);

      assertThat(w1a).isEqualTo(w1b);
      assertThat(w1a).hasSameHashCodeAs(w1b);
      assertThat(w1a).isNotEqualTo(w2);
    }
  }
}
