// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.context;

import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;
import static org.higherkindedj.hkt.instances.Witnesses.context;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.ContextKind;
import org.higherkindedj.hkt.context.RequestContext;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/context_scoped.html">Context</a> page: an
 * audit line that reads the request's trace id from a {@code ScopedValue}, a binding made around
 * building a task and one made around running it, the forks that see a binding and one that does
 * not, two keys of different types read together, a key nobody bound, and the instance over {@code
 * Kind}. The page {@code {{#include}}}s the anchored regions, {@code ContextBookTest} holds the
 * claims the page makes, and the book's output gate runs {@link #main} against the comments in the
 * regions.
 */
public final class ContextBook {

  /** An order Ada has paid for. */
  static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
          Instant.parse("2026-10-30T15:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.PAID);

  private ContextBook() {}

  // ANCHOR: audit
  // The trace id comes from the binding, not from a parameter
  static Context<String, String> auditLine(Order order, String event) {
    return Context.asks(
        RequestContext.TRACE_ID,
        traceId -> "[%s] %s for %s".formatted(traceId, event, order.customer().name()));
  }

  // ANCHOR_END: audit

  // ANCHOR: handlers
  // The binding ends when call returns, before anything runs the task it built
  static VTask<String> boundWhileBuilding(Order order, String traceId) {
    return ScopedValue.where(RequestContext.TRACE_ID, traceId)
        .call(() -> auditLine(order, "payment taken").toVTask());
  }

  // The binding lasts while the task runs, so the task reads it
  static Try<String> boundWhileRunning(Order order, String traceId) {
    VTask<String> task = auditLine(order, "payment taken").toVTask();
    return ScopedValue.where(RequestContext.TRACE_ID, traceId).call(task::runSafe);
  }

  // ANCHOR_END: handlers

  // ANCHOR: carry
  // Read the trace id on the thread that runs the task, then bind it again inside the timed task,
  // which runs on a thread of its own. Only TRACE_ID is carried, so a step that reads another key
  // needs that key bound again too
  static VTask<String> timedWithTrace(Context<String, String> step, Duration limit) {
    return Context.ask(RequestContext.TRACE_ID)
        .toVTask()
        .flatMap(
            traceId ->
                VTask.of(() -> ScopedValue.where(RequestContext.TRACE_ID, traceId).call(step::run))
                    .timeout(limit));
  }

  // ANCHOR_END: carry

  // ANCHOR: two_keys
  // TRACE_ID holds a String and LOCALE a Locale, so the two reads meet in a VTask
  static VTask<String> confirmation(Order order) {
    return Context.ask(RequestContext.TRACE_ID)
        .toVTask()
        .flatMap(
            traceId ->
                Context.asks(RequestContext.LOCALE, Locale::toLanguageTag)
                    .toVTask()
                    .map(
                        language ->
                            "[%s] confirmation for %s in %s"
                                .formatted(traceId, order.customer().name(), language)));
  }

  // ANCHOR_END: two_keys

  public static void main(String[] args) {
    // ANCHOR: bound
    Context<String, String> paid = auditLine(ORDER, "payment taken");

    String line = ScopedValue.where(RequestContext.TRACE_ID, "req-7").call(paid::run);
    // ANCHOR_END: bound
    System.out.println(line);

    // ANCHOR: handlers_outcome
    Try<String> tooEarly = boundWhileBuilding(ORDER, "req-7").runSafe();
    // Failure(java.util.NoSuchElementException: ScopedValue not bound)
    Try<String> whileRunning = boundWhileRunning(ORDER, "req-7");
    // Success([req-7] payment taken for Ada)
    // ANCHOR_END: handlers_outcome
    System.out.println(tooEarly);
    System.out.println(whileRunning);

    // ANCHOR: forks
    VTask<String> both =
        Par.map2(
            paid.toVTask(),
            auditLine(ORDER, "receipt sent").toVTask(),
            (first, second) -> first + "; " + second);
    VTask<String> timed = paid.toVTask().timeout(Duration.ofSeconds(30));

    Try<String> forked = ScopedValue.where(RequestContext.TRACE_ID, "req-7").call(both::runSafe);
    // Success([req-7] payment taken for Ada; [req-7] receipt sent for Ada)
    Try<String> ownThread =
        ScopedValue.where(RequestContext.TRACE_ID, "req-7").call(timed::runSafe);
    // Failure(java.util.NoSuchElementException: ScopedValue not bound)
    // ANCHOR_END: forks
    System.out.println(forked);
    System.out.println(ownThread);

    // ANCHOR: carry_outcome
    VTask<String> carried = timedWithTrace(paid, Duration.ofSeconds(30));

    Try<String> timedAndTraced =
        ScopedValue.where(RequestContext.TRACE_ID, "req-7").call(carried::runSafe);
    // Success([req-7] payment taken for Ada)
    // ANCHOR_END: carry_outcome
    System.out.println(timedAndTraced);

    // ANCHOR: two_keys_outcome
    VTask<String> confirm = confirmation(ORDER);

    Try<String> confirmed =
        ScopedValue.where(RequestContext.TRACE_ID, "req-7")
            .where(RequestContext.LOCALE, Locale.UK)
            .call(confirm::runSafe);
    // Success([req-7] confirmation for Ada in en-GB)
    // ANCHOR_END: two_keys_outcome
    System.out.println(confirmed);

    // ANCHOR: unbound
    Try<String> nobodyBound = paid.toVTask().runSafe();
    // Failure(java.util.NoSuchElementException: ScopedValue not bound)
    String withDefault = Context.ask(RequestContext.TRACE_ID).recover(error -> "no trace").run();
    // ANCHOR_END: unbound
    System.out.println(nobodyBound);
    System.out.println(withDefault);

    // ANCHOR: kind
    Monad<ContextKind.Witness<String>> traced = Instances.monad(context());

    Kind<ContextKind.Witness<String>, String> taken = CONTEXT.widen(paid);
    Kind<ContextKind.Witness<String>, String> sent =
        CONTEXT.widen(auditLine(ORDER, "receipt sent"));
    Kind<ContextKind.Witness<String>, String> together =
        traced.map2(taken, sent, (first, second) -> first + "; " + second);

    String ran =
        ScopedValue.where(RequestContext.TRACE_ID, "req-7")
            .call(() -> CONTEXT.runContext(together));
    // ANCHOR_END: kind
    System.out.println(ran);
  }
}
