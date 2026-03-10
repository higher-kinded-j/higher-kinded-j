// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.mtl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.higherkindedj.hkt.reader_t.ReaderTMonadReader;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.higherkindedj.hkt.state_t.StateTMonadState;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.higherkindedj.hkt.writer_t.WriterTMonad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 02: MTL Basics
 *
 * <p>This file contains the completed solutions for all exercises. Compare your answers with these
 * solutions after attempting the tutorial.
 */
@DisplayName("Tutorial 02: MTL Basics - Solutions")
public class Tutorial02_MTLBasics_Solution {

  record AppConfig(String dbUrl, int maxRetries) {}

  record Counter(int count, int total) {}

  private IdMonad idMonad;

  @BeforeEach
  void setUp() {
    idMonad = IdMonad.instance();
  }

  // ===========================================================================
  // Part 1: MonadReader
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: MonadReader")
  class MonadReaderExercises {

    @Test
    @DisplayName("Exercise 1: Read environment with ask()")
    void exercise1_askReadsEnvironment() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 3);

      // SOLUTION: Use For.from with env.ask() and yield to extract the dbUrl
      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, String> result =
          For.from(env, env.ask()).yield(c -> c.dbUrl());

      ReaderT<IdKind.Witness, AppConfig, String> readerT = READER_T.narrow(result);
      String value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo("jdbc:pg://host/db");
    }

    @Test
    @DisplayName("Exercise 2: Extract a field with reader()")
    void exercise2_readerExtractsField() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 5);

      // SOLUTION: Use env.reader() with a field accessor
      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, Integer> result =
          env.reader(AppConfig::maxRetries);

      ReaderT<IdKind.Witness, AppConfig, Integer> readerT = READER_T.narrow(result);
      int value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo(5);
    }

    @Test
    @DisplayName("Exercise 3: Modify environment with local()")
    void exercise3_localModifiesEnvironment() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 3);

      // SOLUTION: Use env.local() to double the maxRetries before reading
      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, Integer> result =
          env.local(
              c -> new AppConfig(c.dbUrl(), c.maxRetries() * 2), env.reader(AppConfig::maxRetries));

      ReaderT<IdKind.Witness, AppConfig, Integer> readerT = READER_T.narrow(result);
      int value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo(6);
    }

    @Test
    @DisplayName("Exercise 4: Build a connection string with For")
    void exercise4_forComprehensionWithReader() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 5);

      // SOLUTION: Use For comprehension to read config and build connection string
      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, String> result =
          For.from(env, env.ask()).yield(c -> c.dbUrl() + "?retries=" + c.maxRetries());

      ReaderT<IdKind.Witness, AppConfig, String> readerT = READER_T.narrow(result);
      String value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo("jdbc:pg://host/db?retries=5");
    }
  }

  // ===========================================================================
  // Part 2: MonadState
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: MonadState")
  class MonadStateExercises {

    @Test
    @DisplayName("Exercise 5: Read state with get()")
    void exercise5_getReadsState() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      // SOLUTION: Use state.get() to read the current state
      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Counter> result = state.get();

      StateT<Counter, IdKind.Witness, Counter> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Counter> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(7, 42)));
      assertThat(tuple.value()).isEqualTo(new Counter(7, 42));
      assertThat(tuple.state()).isEqualTo(new Counter(7, 42));
    }

    @Test
    @DisplayName("Exercise 6: Transform state with modify()")
    void exercise6_modifyTransformsState() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      // SOLUTION: Use state.modify() to increment the count
      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Unit> result =
          state.modify(c -> new Counter(c.count() + 1, c.total()));

      StateT<Counter, IdKind.Witness, Unit> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Unit> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(3, 100)));
      assertThat(tuple.state()).isEqualTo(new Counter(4, 100));
    }

    @Test
    @DisplayName("Exercise 7: Extract from state with gets()")
    void exercise7_getsExtractsFromState() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      // SOLUTION: Use state.gets() with a field accessor
      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Integer> result =
          state.gets(Counter::total);

      StateT<Counter, IdKind.Witness, Integer> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Integer> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(5, 75)));
      assertThat(tuple.value()).isEqualTo(75);
      assertThat(tuple.state()).isEqualTo(new Counter(5, 75));
    }

    @Test
    @DisplayName("Exercise 8: Chain state operations with For")
    void exercise8_chainedStateOperations() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      // SOLUTION: Chain modify and gets in a For comprehension
      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Integer> result =
          For.from(state, state.modify(c -> new Counter(c.count() + 1, c.total() + 10)))
              .from(_ -> state.modify(c -> new Counter(c.count() + 1, c.total() + 10)))
              .from(_ -> state.gets(Counter::total))
              .yield((_, _, total) -> total);

      StateT<Counter, IdKind.Witness, Integer> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Integer> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(0, 0)));
      assertThat(tuple.value()).isEqualTo(20);
      assertThat(tuple.state()).isEqualTo(new Counter(2, 20));
    }
  }

  // ===========================================================================
  // Part 3: MonadWriter
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: MonadWriter")
  class MonadWriterExercises {

    @Test
    @DisplayName("Exercise 9: Append output with tell()")
    void exercise9_tellAppendsOutput() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      // SOLUTION: Use writer.tell() to append a single entry
      Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, Unit> result =
          writer.tell(List.of("hello"));

      WriterT<IdKind.Witness, List<String>, Unit> writerT = WRITER_T.narrow(result);
      Pair<Unit, List<String>> pair = IdKindHelper.ID.unwrap(writerT.run());
      assertThat(pair.second()).containsExactly("hello");
    }

    @Test
    @DisplayName("Exercise 10: Accumulate entries with For")
    void exercise10_accumulateWithFor() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      // SOLUTION: Chain three tell() calls in a For comprehension
      Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, String> result =
          For.from(writer, writer.tell(List.of("step 1")))
              .from(_ -> writer.tell(List.of("step 2")))
              .from(_ -> writer.tell(List.of("step 3")))
              .yield((_, _, _) -> "done");

      WriterT<IdKind.Witness, List<String>, String> writerT = WRITER_T.narrow(result);
      Pair<String, List<String>> pair = IdKindHelper.ID.unwrap(writerT.run());
      assertThat(pair.first()).isEqualTo("done");
      assertThat(pair.second()).containsExactly("step 1", "step 2", "step 3");
    }

    @Test
    @DisplayName("Exercise 11: Transform output with censor()")
    void exercise11_censorTransformsOutput() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      var computation =
          For.from(writer, writer.tell(List.of("hello")))
              .from(_ -> writer.tell(List.of("world")))
              .yield((_, _) -> "result");

      // SOLUTION: Use writer.censor() to transform all entries to uppercase
      Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, String> result =
          writer.censor(entries -> entries.stream().map(String::toUpperCase).toList(), computation);

      WriterT<IdKind.Witness, List<String>, String> writerT = WRITER_T.narrow(result);
      Pair<String, List<String>> pair = IdKindHelper.ID.unwrap(writerT.run());
      assertThat(pair.first()).isEqualTo("result");
      assertThat(pair.second()).containsExactly("HELLO", "WORLD");
    }
  }

  // ===========================================================================
  // Part 4: Polymorphic Functions
  // ===========================================================================

  @Nested
  @DisplayName("Part 4: Polymorphic Functions")
  class PolymorphicFunctions {

    @Test
    @DisplayName("Exercise 12: Polymorphic reader function")
    void exercise12_polymorphicReader() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 3);

      // SOLUTION: Call the polymorphic buildUrl method
      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, String> result = buildUrl(env);

      ReaderT<IdKind.Witness, AppConfig, String> readerT = READER_T.narrow(result);
      String value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo("jdbc:pg://host/db?retries=3");
    }

    @Test
    @DisplayName("Exercise 13: Polymorphic state function")
    void exercise13_polymorphicState() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      // SOLUTION: Call the polymorphic incrementAndTotal method
      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Integer> result =
          incrementAndTotal(state, 25);

      StateT<Counter, IdKind.Witness, Integer> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Integer> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(0, 0)));
      assertThat(tuple.value()).isEqualTo(25);
      assertThat(tuple.state()).isEqualTo(new Counter(1, 25));
    }

    @Test
    @DisplayName("Exercise 14: Polymorphic writer function")
    void exercise14_polymorphicWriter() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      // SOLUTION: Call the polymorphic auditedProcess method
      Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, String> result =
          auditedProcess(writer, "item-42");

      WriterT<IdKind.Witness, List<String>, String> writerT = WRITER_T.narrow(result);
      Pair<String, List<String>> pair = IdKindHelper.ID.unwrap(writerT.run());
      assertThat(pair.first()).isEqualTo("processed-item-42");
      assertThat(pair.second()).containsExactly("Processing item-42", "Completed item-42");
    }
  }

  // ===========================================================================
  // Polymorphic helper methods (solutions)
  // ===========================================================================

  // SOLUTION: Read the config with ask() and build a URL string
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> buildUrl(
      MonadReader<F, AppConfig> env) {
    return For.from(env, env.ask()).yield(c -> c.dbUrl() + "?retries=" + c.maxRetries());
  }

  // SOLUTION: Modify the counter, then read the new total
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, Integer> incrementAndTotal(
      MonadState<F, Counter> state, int amount) {
    return For.from(state, state.modify(c -> new Counter(c.count() + 1, c.total() + amount)))
        .from(_ -> state.gets(Counter::total))
        .yield((_, total) -> total);
  }

  // SOLUTION: Log two entries with tell(), then yield the result string
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> auditedProcess(
      MonadWriter<F, List<String>> audit, String item) {
    return For.from(audit, audit.tell(List.of("Processing " + item)))
        .from(_ -> audit.tell(List.of("Completed " + item)))
        .yield((_, _) -> "processed-" + item);
  }
}
