// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.expression;

import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTMonadReader;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.state_t.StateTMonadState;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTMonad;

/**
 * Demonstrates MTL-style capability classes: MonadReader, MonadState, and MonadWriter.
 *
 * <p>MTL (Monad Transformer Library) interfaces let you write polymorphic functions that declare
 * what capabilities they need (environment access, state, logging) without specifying a concrete
 * transformer stack. The same function can then run over ReaderT&lt;Id&gt;, ReaderT&lt;IO&gt;, or
 * any other stack.
 *
 * <p>See <a href="https://higher-kinded-j.github.io/mtl_capabilities.html">MTL Capabilities</a>
 */
public class MTLCapabilitiesExample {

  // --- Domain models ---

  record AppConfig(String dbUrl, int maxRetries, boolean debugMode) {}

  record Counter(int count, int total) {}

  public static void main(String[] args) {
    new MTLCapabilitiesExample().run();
  }

  public void run() {
    var idMonad = IdMonad.instance();

    System.out.println("=== MTL Capabilities Example ===\n");

    // --- MonadReader: polymorphic environment access ---

    System.out.println("--- MonadReader ---");

    var readerInstance = new ReaderTMonadReader<IdKind.Witness, AppConfig>(idMonad);
    var config = new AppConfig("jdbc:postgresql://localhost/mydb", 3, true);

    // Build a connection string using only MonadReader (no concrete stack in the function)
    var connResult = connectionString(readerInstance);
    ReaderT<IdKind.Witness, AppConfig, String> connReaderT = READER_T.narrow(connResult);
    String connStr = IdKindHelper.ID.unwrap(connReaderT.run().apply(config));
    System.out.println("Connection string: " + connStr);

    // Use local() to temporarily override debug mode
    var debugResult = withDebugOff(readerInstance);
    ReaderT<IdKind.Witness, AppConfig, String> debugReaderT = READER_T.narrow(debugResult);
    String debugStr = IdKindHelper.ID.unwrap(debugReaderT.run().apply(config));
    System.out.println("Debug off result: " + debugStr);

    // --- MonadState: polymorphic state management ---

    System.out.println("\n--- MonadState ---");

    var stateInstance = new StateTMonadState<Counter, IdKind.Witness>(idMonad);
    var initial = new Counter(0, 0);

    // Run a stateful computation using only MonadState
    var countResult = countThreeItems(stateInstance);
    StateT<Counter, IdKind.Witness, Integer> countStateT = STATE_T.narrow(countResult);
    StateTuple<Counter, Integer> counted = IdKindHelper.ID.unwrap(countStateT.runStateT(initial));
    System.out.println("Final total: " + counted.value());
    System.out.println(
        "Final state: count=" + counted.state().count() + ", total=" + counted.state().total());

    // --- MonadWriter: polymorphic output accumulation ---

    System.out.println("\n--- MonadWriter ---");

    var writerInstance = new WriterTMonad<IdKind.Witness, List<String>>(idMonad, Monoids.list());

    // Run a computation that accumulates an audit log
    var auditResult = processOrder(writerInstance, "ORD-42");
    WriterT<IdKind.Witness, List<String>, String> auditWriterT = WRITER_T.narrow(auditResult);
    var pair = IdKindHelper.ID.unwrap(auditWriterT.run());
    System.out.println("Result: " + pair.first());
    System.out.println("Audit log: " + pair.second());
  }

  // --- Polymorphic functions using MTL capabilities ---

  /**
   * Builds a connection string from the environment. Works with any monad providing MonadReader.
   */
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> connectionString(
      MonadReader<F, AppConfig> env) {
    return For.from(env, env.ask())
        .yield(
            config ->
                config.dbUrl()
                    + "?retries="
                    + config.maxRetries()
                    + (config.debugMode() ? "&debug=true" : ""));
  }

  /** Demonstrates local() to temporarily modify the environment. */
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> withDebugOff(
      MonadReader<F, AppConfig> env) {
    return env.local(
        config -> new AppConfig(config.dbUrl(), config.maxRetries(), false),
        env.reader(config -> "debug=" + config.debugMode()));
  }

  /** Counts three items using MonadState. Works with any monad providing MonadState. */
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, Integer> countThreeItems(
      MonadState<F, Counter> state) {
    return For.from(state, state.modify(c -> new Counter(c.count() + 1, c.total() + 10)))
        .from(_ -> state.modify(c -> new Counter(c.count() + 1, c.total() + 20)))
        .from(_ -> state.modify(c -> new Counter(c.count() + 1, c.total() + 30)))
        .from(_ -> state.gets(Counter::total))
        .yield((_, _, _, total) -> total);
  }

  /** Processes an order with audit logging. Works with any monad providing MonadWriter. */
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> processOrder(
      MonadWriter<F, List<String>> audit, String orderId) {
    return For.from(audit, audit.tell(List.of("Validated order " + orderId)))
        .from(_ -> audit.tell(List.of("Charged payment")))
        .from(_ -> audit.tell(List.of("Dispatched shipment")))
        .yield((_, _, _) -> "receipt-" + orderId);
  }
}
