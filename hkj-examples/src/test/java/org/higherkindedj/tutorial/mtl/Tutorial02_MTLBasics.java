// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.mtl;

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
 * Tutorial: MTL Basics - Writing Stack-Independent Effectful Code
 *
 * <p>Learn to use MonadReader, MonadState, and MonadWriter to declare capabilities without fixing a
 * concrete transformer stack. These interfaces let you write polymorphic functions that work with
 * any stack providing the required capability.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>MonadReader: read-only access to a shared environment (ask, reader, local)
 *   <li>MonadState: read-write state threading (get, put, modify, gets)
 *   <li>MonadWriter: append-only output accumulation (tell, listen, censor)
 *   <li>Polymorphic functions: accept capability interfaces, not concrete types
 * </ul>
 *
 * <p>Estimated time: 30-40 minutes
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
@DisplayName("Tutorial 02: MTL Basics")
public class Tutorial02_MTLBasics {

  // --- Test Data ---

  record AppConfig(String dbUrl, int maxRetries) {}

  record Counter(int count, int total) {}

  // --- Fixtures ---

  private IdMonad idMonad;

  @BeforeEach
  void setUp() {
    idMonad = IdMonad.instance();
  }

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required - replace answerRequired() with your solution");
  }

  // ===========================================================================
  // Part 1: MonadReader - Reading from an Environment
  // ===========================================================================

  @Nested
  @DisplayName("Part 1: MonadReader")
  class MonadReaderExercises {

    /**
     * Exercise 1: Use ask() to read the full environment
     *
     * <p>MonadReader.ask() returns the entire environment as a value inside the monad. This is the
     * most fundamental reader operation.
     *
     * <p>Task: Use env.ask() in a For comprehension to read the config and extract the dbUrl.
     */
    @Test
    @DisplayName("Exercise 1: Read environment with ask()")
    void exercise1_askReadsEnvironment() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 3);

      // TODO: Replace answerRequired() with:
      // For.from(env, env.ask()).yield(c -> c.dbUrl())
      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, String> result = answerRequired();

      ReaderT<IdKind.Witness, AppConfig, String> readerT = READER_T.narrow(result);
      String value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo("jdbc:pg://host/db");
    }

    /**
     * Exercise 2: Use reader() to extract a single field
     *
     * <p>MonadReader.reader(f) applies a function to the environment and returns the result. It is
     * a shorthand for ask() followed by map().
     *
     * <p>Task: Use env.reader() to extract the maxRetries field from AppConfig.
     */
    @Test
    @DisplayName("Exercise 2: Extract a field with reader()")
    void exercise2_readerExtractsField() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 5);

      // TODO: Replace answerRequired() with env.reader(AppConfig::maxRetries)
      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, Integer> result = answerRequired();

      ReaderT<IdKind.Witness, AppConfig, Integer> readerT = READER_T.narrow(result);
      int value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo(5);
    }

    /**
     * Exercise 3: Use local() to temporarily modify the environment
     *
     * <p>MonadReader.local(f, ma) runs a computation with a modified environment. The original
     * environment is restored after the computation completes.
     *
     * <p>Task: Use env.local() to double the maxRetries before reading it.
     */
    @Test
    @DisplayName("Exercise 3: Modify environment with local()")
    void exercise3_localModifiesEnvironment() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 3);

      // TODO: Replace answerRequired() with:
      // env.local(c -> new AppConfig(c.dbUrl(), c.maxRetries() * 2),
      // env.reader(AppConfig::maxRetries))
      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, Integer> result = answerRequired();

      ReaderT<IdKind.Witness, AppConfig, Integer> readerT = READER_T.narrow(result);
      int value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo(6);
    }

    /**
     * Exercise 4: Combine ask() with a For comprehension
     *
     * <p>The real power of MonadReader emerges in For comprehensions, where you can read the
     * environment and use it to build a result across multiple steps.
     *
     * <p>Task: Read the config and produce a connection string: "dbUrl?retries=maxRetries"
     */
    @Test
    @DisplayName("Exercise 4: Build a connection string with For")
    void exercise4_forComprehensionWithReader() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 5);

      // TODO: Replace answerRequired() with:
      // For.from(env, env.ask())
      //     .yield(c -> c.dbUrl() + "?retries=" + c.maxRetries())
      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, String> result = answerRequired();

      ReaderT<IdKind.Witness, AppConfig, String> readerT = READER_T.narrow(result);
      String value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo("jdbc:pg://host/db?retries=5");
    }
  }

  // ===========================================================================
  // Part 2: MonadState - Threading Mutable State
  // ===========================================================================

  @Nested
  @DisplayName("Part 2: MonadState")
  class MonadStateExercises {

    /**
     * Exercise 5: Use get() to read the current state
     *
     * <p>MonadState.get() returns the current state as a value inside the monad. The state itself
     * is not changed.
     *
     * <p>Task: Use state.get() to read the current counter.
     */
    @Test
    @DisplayName("Exercise 5: Read state with get()")
    void exercise5_getReadsState() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      // TODO: Replace answerRequired() with state.get()
      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Counter> result = answerRequired();

      StateT<Counter, IdKind.Witness, Counter> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Counter> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(7, 42)));
      assertThat(tuple.value()).isEqualTo(new Counter(7, 42));
      assertThat(tuple.state()).isEqualTo(new Counter(7, 42));
    }

    /**
     * Exercise 6: Use modify() to transform the state
     *
     * <p>MonadState.modify(f) applies a function to the current state and stores the result. It
     * returns Unit (the functional void).
     *
     * <p>Task: Use state.modify() to increment the count by 1.
     */
    @Test
    @DisplayName("Exercise 6: Transform state with modify()")
    void exercise6_modifyTransformsState() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      // TODO: Replace answerRequired() with:
      // state.modify(c -> new Counter(c.count() + 1, c.total()))
      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Unit> result = answerRequired();

      StateT<Counter, IdKind.Witness, Unit> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Unit> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(3, 100)));
      assertThat(tuple.state()).isEqualTo(new Counter(4, 100));
    }

    /**
     * Exercise 7: Use gets() to extract a value from the state
     *
     * <p>MonadState.gets(f) reads the state and applies a function to extract a value. The state is
     * not modified.
     *
     * <p>Task: Use state.gets() to extract the total from the counter.
     */
    @Test
    @DisplayName("Exercise 7: Extract from state with gets()")
    void exercise7_getsExtractsFromState() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      // TODO: Replace answerRequired() with state.gets(Counter::total)
      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Integer> result = answerRequired();

      StateT<Counter, IdKind.Witness, Integer> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Integer> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(5, 75)));
      assertThat(tuple.value()).isEqualTo(75);
      assertThat(tuple.state()).isEqualTo(new Counter(5, 75));
    }

    /**
     * Exercise 8: Chain state operations in a For comprehension
     *
     * <p>Each step in a For comprehension sees the state left by the previous step. This is the key
     * insight of MonadState: state threads automatically through flatMap chains.
     *
     * <p>Task: Modify the counter twice (add 1 to count, add 10 to total each time), then read the
     * total.
     */
    @Test
    @DisplayName("Exercise 8: Chain state operations with For")
    void exercise8_chainedStateOperations() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      // TODO: Replace answerRequired() with:
      // For.from(state, state.modify(c -> new Counter(c.count() + 1, c.total() + 10)))
      //     .from(_ -> state.modify(c -> new Counter(c.count() + 1, c.total() + 10)))
      //     .from(_ -> state.gets(Counter::total))
      //     .yield((_, _, total) -> total)
      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Integer> result = answerRequired();

      StateT<Counter, IdKind.Witness, Integer> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Integer> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(0, 0)));
      assertThat(tuple.value()).isEqualTo(20);
      assertThat(tuple.state()).isEqualTo(new Counter(2, 20));
    }
  }

  // ===========================================================================
  // Part 3: MonadWriter - Accumulating Output
  // ===========================================================================

  @Nested
  @DisplayName("Part 3: MonadWriter")
  class MonadWriterExercises {

    /**
     * Exercise 9: Use tell() to append output
     *
     * <p>MonadWriter.tell(w) appends a value to the accumulated output. The output is combined
     * using a Monoid, so for List the entries are concatenated.
     *
     * <p>Task: Use writer.tell() to log a single entry.
     */
    @Test
    @DisplayName("Exercise 9: Append output with tell()")
    void exercise9_tellAppendsOutput() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      // TODO: Replace answerRequired() with writer.tell(List.of("hello"))
      Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, Unit> result = answerRequired();

      WriterT<IdKind.Witness, List<String>, Unit> writerT = WRITER_T.narrow(result);
      Pair<Unit, List<String>> pair = IdKindHelper.ID.unwrap(writerT.run());
      assertThat(pair.second()).containsExactly("hello");
    }

    /**
     * Exercise 10: Accumulate multiple entries with tell() in a For comprehension
     *
     * <p>Each tell() in a For chain appends to the same output log. The entries accumulate in
     * order.
     *
     * <p>Task: Tell three log entries and yield "done".
     */
    @Test
    @DisplayName("Exercise 10: Accumulate entries with For")
    void exercise10_accumulateWithFor() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      // TODO: Replace answerRequired() with:
      // For.from(writer, writer.tell(List.of("step 1")))
      //     .from(_ -> writer.tell(List.of("step 2")))
      //     .from(_ -> writer.tell(List.of("step 3")))
      //     .yield((_, _, _) -> "done")
      Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, String> result = answerRequired();

      WriterT<IdKind.Witness, List<String>, String> writerT = WRITER_T.narrow(result);
      Pair<String, List<String>> pair = IdKindHelper.ID.unwrap(writerT.run());
      assertThat(pair.first()).isEqualTo("done");
      assertThat(pair.second()).containsExactly("step 1", "step 2", "step 3");
    }

    /**
     * Exercise 11: Use censor() to transform accumulated output
     *
     * <p>MonadWriter.censor(f, ma) runs a computation and applies function f to the accumulated
     * output before returning. This lets you redact, filter, or transform log entries.
     *
     * <p>Task: Use writer.censor() to convert all log entries to uppercase.
     */
    @Test
    @DisplayName("Exercise 11: Transform output with censor()")
    void exercise11_censorTransformsOutput() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      var computation =
          For.from(writer, writer.tell(List.of("hello")))
              .from(_ -> writer.tell(List.of("world")))
              .yield((_, _) -> "result");

      // TODO: Replace answerRequired() with:
      // writer.censor(entries -> entries.stream().map(String::toUpperCase).toList(), computation)
      Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, String> result = answerRequired();

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

    /**
     * Exercise 12: Write a polymorphic function using MonadReader
     *
     * <p>The real benefit of MTL is writing functions that accept a capability interface instead of
     * a concrete type. The function below should work with any monad that provides MonadReader.
     *
     * <p>Task: Complete the buildUrl method below, then call it here.
     */
    @Test
    @DisplayName("Exercise 12: Polymorphic reader function")
    void exercise12_polymorphicReader() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 3);

      // TODO: Replace answerRequired() with buildUrl(env)
      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, String> result = answerRequired();

      ReaderT<IdKind.Witness, AppConfig, String> readerT = READER_T.narrow(result);
      String value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo("jdbc:pg://host/db?retries=3");
    }

    /**
     * Exercise 13: Write a polymorphic function using MonadState
     *
     * <p>Task: Complete the incrementAndTotal method below, then call it here.
     */
    @Test
    @DisplayName("Exercise 13: Polymorphic state function")
    void exercise13_polymorphicState() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      // TODO: Replace answerRequired() with incrementAndTotal(state, 25)
      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Integer> result = answerRequired();

      StateT<Counter, IdKind.Witness, Integer> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Integer> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(0, 0)));
      assertThat(tuple.value()).isEqualTo(25);
      assertThat(tuple.state()).isEqualTo(new Counter(1, 25));
    }

    /**
     * Exercise 14: Write a polymorphic function using MonadWriter
     *
     * <p>Task: Complete the auditedProcess method below, then call it here.
     */
    @Test
    @DisplayName("Exercise 14: Polymorphic writer function")
    void exercise14_polymorphicWriter() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      // TODO: Replace answerRequired() with auditedProcess(writer, "item-42")
      Kind<WriterTKind.Witness<IdKind.Witness, List<String>>, String> result = answerRequired();

      WriterT<IdKind.Witness, List<String>, String> writerT = WRITER_T.narrow(result);
      Pair<String, List<String>> pair = IdKindHelper.ID.unwrap(writerT.run());
      assertThat(pair.first()).isEqualTo("processed-item-42");
      assertThat(pair.second()).containsExactly("Processing item-42", "Completed item-42");
    }
  }

  // ===========================================================================
  // Polymorphic helper methods
  //
  // Complete these methods to make exercises 12-14 pass.
  // Each method accepts a capability interface (not a concrete type).
  // ===========================================================================

  /**
   * Exercise 12 helper: Build a URL from environment config.
   *
   * <p>TODO: Use For.from(env, env.ask()) to read the config, then yield a string of the form
   * "dbUrl?retries=maxRetries".
   */
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> buildUrl(
      MonadReader<F, AppConfig> env) {
    return answerRequired();
  }

  /**
   * Exercise 13 helper: Increment count by 1, add amount to total, return the new total.
   *
   * <p>TODO: Use For.from(state, state.modify(...)) to update the counter, then use
   * state.gets(Counter::total) to read the new total.
   */
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, Integer> incrementAndTotal(
      MonadState<F, Counter> state, int amount) {
    return answerRequired();
  }

  /**
   * Exercise 14 helper: Log "Processing {item}" and "Completed {item}", return "processed-{item}".
   *
   * <p>TODO: Use For.from(audit, audit.tell(...)) to log two entries, then yield the result string.
   */
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> auditedProcess(
      MonadWriter<F, List<String>> audit, String item) {
    return answerRequired();
  }

  /**
   * Congratulations! You've completed Tutorial 02: MTL Basics
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to use MonadReader to read from an environment (ask, reader, local)
   *   <li>How to use MonadState to thread mutable state (get, modify, gets)
   *   <li>How to use MonadWriter to accumulate output (tell, censor)
   *   <li>How to write polymorphic functions that accept capability interfaces
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>MTL interfaces declare capabilities without fixing the transformer stack
   *   <li>Polymorphic functions work with any stack that provides the required capability
   *   <li>For comprehensions compose MTL operations into readable, sequential workflows
   *   <li>The same business logic can run over Id (for testing) or CompletableFuture (for
   *       production)
   * </ul>
   */
}
