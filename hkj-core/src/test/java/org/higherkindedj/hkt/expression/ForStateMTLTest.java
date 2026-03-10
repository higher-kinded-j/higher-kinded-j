// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

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
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for MTL capabilities used within ForState and For workflows.
 *
 * <p>These tests verify that MonadReader, MonadState, and MonadWriter operations compose correctly
 * with For comprehensions and ForState pipelines.
 */
@DisplayName("ForState MTL Integration Tests")
class ForStateMTLTest {

  // --- Test Data ---

  record AppConfig(String dbUrl, int maxRetries) {}

  record Counter(int count, int total) {}

  record PipelineContext(String input, String processed, List<String> log) {}

  // --- Fixtures ---

  private IdMonad idMonad;

  @BeforeEach
  void setUp() {
    idMonad = IdMonad.instance();
  }

  // =========================================================================
  // MonadReader with ForState
  // =========================================================================

  @Nested
  @DisplayName("MonadReader with ForState")
  class ReaderWithForState {

    @Test
    @DisplayName("ask() provides environment in For comprehension")
    void askInForComprehension() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 5);

      Kind<ReaderTKind.Witness<IdKind.Witness, AppConfig>, String> result =
          For.from(env, env.ask()).yield(c -> c.dbUrl() + "?retries=" + c.maxRetries());

      ReaderT<IdKind.Witness, AppConfig, String> readerT = READER_T.narrow(result);
      String value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo("jdbc:pg://host/db?retries=5");
    }

    @Test
    @DisplayName("reader() extracts a single field")
    void readerExtractsField() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 3);

      var result = env.reader(AppConfig::dbUrl);

      ReaderT<IdKind.Witness, AppConfig, String> readerT = READER_T.narrow(result);
      String value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo("jdbc:pg://host/db");
    }

    @Test
    @DisplayName("local() temporarily modifies environment")
    void localModifiesEnvironment() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 3);

      var result = env.local(c -> new AppConfig(c.dbUrl(), 10), env.reader(AppConfig::maxRetries));

      ReaderT<IdKind.Witness, AppConfig, Integer> readerT = READER_T.narrow(result);
      int value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo(10);
    }

    @Test
    @DisplayName("ForState from() integrates with MonadReader.ask()")
    void forStateWithReaderAsk() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 3);

      Lens<String, String> selfLens = Lens.of(s -> s, (_, v) -> v);

      var result = ForState.withState(env, env.reader(AppConfig::dbUrl)).yield();

      ReaderT<IdKind.Witness, AppConfig, String> readerT = READER_T.narrow(result);
      String value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo("jdbc:pg://host/db");
    }
  }

  // =========================================================================
  // MonadState with ForState
  // =========================================================================

  @Nested
  @DisplayName("MonadState with ForState")
  class StateWithForState {

    @Test
    @DisplayName("get() and put() thread state through For comprehension")
    void getAndPutInFor() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      Kind<StateTKind.Witness<Counter, IdKind.Witness>, Integer> result =
          For.from(state, state.modify(c -> new Counter(c.count() + 1, c.total() + 10)))
              .from(_ -> state.modify(c -> new Counter(c.count() + 1, c.total() + 20)))
              .from(_ -> state.gets(Counter::total))
              .yield((_, _, total) -> total);

      StateT<Counter, IdKind.Witness, Integer> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Integer> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(0, 0)));
      assertThat(tuple.value()).isEqualTo(30);
      assertThat(tuple.state()).isEqualTo(new Counter(2, 30));
    }

    @Test
    @DisplayName("modify() transforms state correctly")
    void modifyTransformsState() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      var result = state.modify(c -> new Counter(c.count() + 5, c.total() + 100));

      StateT<Counter, IdKind.Witness, Unit> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Unit> tuple = IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(0, 0)));
      assertThat(tuple.state()).isEqualTo(new Counter(5, 100));
    }

    @Test
    @DisplayName("gets() extracts value from current state")
    void getsExtractsFromState() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      var result = state.gets(Counter::count);

      StateT<Counter, IdKind.Witness, Integer> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Integer> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(7, 42)));
      assertThat(tuple.value()).isEqualTo(7);
      assertThat(tuple.state()).isEqualTo(new Counter(7, 42));
    }

    @Test
    @DisplayName("ForState with MonadState operations via from()")
    void forStateWithMonadStateOps() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      Lens<Counter, Integer> countLens =
          Lens.of(Counter::count, (c, v) -> new Counter(v, c.total()));
      Lens<Counter, Integer> totalLens =
          Lens.of(Counter::total, (c, v) -> new Counter(c.count(), v));

      var result =
          ForState.withState(state, state.of(new Counter(0, 0)))
              .modify(countLens, c -> c + 1)
              .modify(totalLens, t -> t + 100)
              .yield();

      StateT<Counter, IdKind.Witness, Counter> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Counter> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(0, 0)));
      assertThat(tuple.value().count()).isEqualTo(1);
      assertThat(tuple.value().total()).isEqualTo(100);
    }
  }

  // =========================================================================
  // MonadWriter with ForState
  // =========================================================================

  @Nested
  @DisplayName("MonadWriter with ForState")
  class WriterWithForState {

    @Test
    @DisplayName("tell() accumulates output in For comprehension")
    void tellAccumulatesInFor() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

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
    @DisplayName("listen() observes accumulated output")
    void listenObservesOutput() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      var computation = For.from(writer, writer.tell(List.of("entry"))).yield(_ -> 42);

      var listened = writer.listen(computation);
      WriterT<IdKind.Witness, List<String>, Pair<Integer, List<String>>> writerT =
          WRITER_T.narrow(listened);
      Pair<Pair<Integer, List<String>>, List<String>> pair = IdKindHelper.ID.unwrap(writerT.run());

      assertThat(pair.first().first()).isEqualTo(42);
      assertThat(pair.first().second()).containsExactly("entry");
      assertThat(pair.second()).containsExactly("entry");
    }

    @Test
    @DisplayName("censor() transforms accumulated output")
    void censorTransformsOutput() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      var computation =
          For.from(writer, writer.tell(List.of("secret: abc123")))
              .from(_ -> writer.tell(List.of("public info")))
              .yield((_, _) -> "result");

      var censored =
          writer.censor(
              entries ->
                  entries.stream().map(e -> e.startsWith("secret:") ? "[REDACTED]" : e).toList(),
              computation);

      WriterT<IdKind.Witness, List<String>, String> writerT = WRITER_T.narrow(censored);
      Pair<String, List<String>> pair = IdKindHelper.ID.unwrap(writerT.run());
      assertThat(pair.first()).isEqualTo("result");
      assertThat(pair.second()).containsExactly("[REDACTED]", "public info");
    }
  }

  // =========================================================================
  // Combined Capabilities
  // =========================================================================

  @Nested
  @DisplayName("Combined MTL Capabilities")
  class CombinedCapabilities {

    @Test
    @DisplayName("Polymorphic function uses MonadReader and produces correct result")
    void polymorphicReaderFunction() {
      var env = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
      var config = new AppConfig("jdbc:pg://host/db", 3);

      // Call a polymorphic function that only knows about MonadReader
      var result = buildUrl(env);

      ReaderT<IdKind.Witness, AppConfig, String> readerT = READER_T.narrow(result);
      String value = IdKindHelper.ID.unwrap(readerT.run().apply(config));
      assertThat(value).isEqualTo("jdbc:pg://host/db?retries=3");
    }

    @Test
    @DisplayName("Polymorphic function uses MonadState and produces correct state")
    void polymorphicStateFunction() {
      var state = new StateTMonadState<Counter, IdKind.Witness>(idMonad);

      var result = incrementAndTotal(state, 15);

      StateT<Counter, IdKind.Witness, Integer> stateT = STATE_T.narrow(result);
      StateTuple<Counter, Integer> tuple =
          IdKindHelper.ID.unwrap(stateT.runStateT(new Counter(0, 0)));
      assertThat(tuple.value()).isEqualTo(15);
      assertThat(tuple.state()).isEqualTo(new Counter(1, 15));
    }

    @Test
    @DisplayName("Polymorphic function uses MonadWriter and accumulates output")
    void polymorphicWriterFunction() {
      var writer = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

      var result = auditedProcess(writer, "item-7");

      WriterT<IdKind.Witness, List<String>, String> writerT = WRITER_T.narrow(result);
      Pair<String, List<String>> pair = IdKindHelper.ID.unwrap(writerT.run());
      assertThat(pair.first()).isEqualTo("processed-item-7");
      assertThat(pair.second()).containsExactly("Processing item-7", "Completed item-7");
    }
  }

  // --- Polymorphic helper functions (stack-independent) ---

  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> buildUrl(
      MonadReader<F, AppConfig> env) {
    return For.from(env, env.ask()).yield(c -> c.dbUrl() + "?retries=" + c.maxRetries());
  }

  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, Integer> incrementAndTotal(
      MonadState<F, Counter> state, int amount) {
    return For.from(state, state.modify(c -> new Counter(c.count() + 1, c.total() + amount)))
        .from(_ -> state.gets(Counter::total))
        .yield((_, total) -> total);
  }

  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> auditedProcess(
      MonadWriter<F, List<String>> audit, String item) {
    return For.from(audit, audit.tell(List.of("Processing " + item)))
        .from(_ -> audit.tell(List.of("Completed " + item)))
        .yield((_, _) -> "processed-" + item);
  }
}
