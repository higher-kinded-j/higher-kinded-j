// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.context.ContextBook.ORDER;
import static org.higherkindedj.example.book.foundations.context.ContextBook.auditLine;
import static org.higherkindedj.example.book.foundations.context.ContextBook.boundWhileBuilding;
import static org.higherkindedj.example.book.foundations.context.ContextBook.boundWhileRunning;
import static org.higherkindedj.example.book.foundations.context.ContextBook.confirmation;
import static org.higherkindedj.example.book.foundations.context.ContextBook.timedWithTrace;
import static org.higherkindedj.hkt.context.ContextKindHelper.CONTEXT;
import static org.higherkindedj.hkt.instances.Witnesses.context;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.ContextException;
import org.higherkindedj.hkt.context.ContextKind;
import org.higherkindedj.hkt.context.RequestContext;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.Resilience;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamPar;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Holds the claims the Context page makes about reading, binding and forking. */
@DisplayName("Context: a computation that reads a ScopedValue when it runs")
class ContextBookTest {

  private static final Context<String, String> PAID = auditLine(ORDER, "payment taken");

  private static <A> Try<A> inRequest(VTask<A> task) {
    return ScopedValue.where(RequestContext.TRACE_ID, "req-7").call(task::runSafe);
  }

  private static <A> A failureOf(Try<?> outcome, Class<A> type) {
    assertThat(outcome).isInstanceOf(Try.Failure.class);
    Throwable cause = ((Try.Failure<?>) outcome).cause();
    assertThat(cause).isInstanceOf(type);
    return type.cast(cause);
  }

  @Nested
  @DisplayName("reading and binding")
  class ReadingAndBinding {

    @Test
    @DisplayName("the audit line reads the trace id the request bound")
    void readsTheBinding() {
      String line = ScopedValue.where(RequestContext.TRACE_ID, "req-7").call(PAID::run);

      assertThat(line).isEqualTo("[req-7] payment taken for Ada");
    }

    @Test
    @DisplayName("building a Context reads nothing, so it may be built outside any binding")
    void buildingReadsNothing() {
      Context<String, String> built = auditLine(ORDER, "refund issued");

      assertThat(ScopedValue.where(RequestContext.TRACE_ID, "req-8").call(built::run))
          .isEqualTo("[req-8] refund issued for Ada");
    }

    @Test
    @DisplayName("an inner binding hides the outer one until its call returns")
    void anInnerBindingHidesTheOuter() {
      Context<String, String> trace = Context.ask(RequestContext.TRACE_ID);

      String seen =
          ScopedValue.where(RequestContext.TRACE_ID, "outer")
              .call(
                  () ->
                      trace.run()
                          + "/"
                          + ScopedValue.where(RequestContext.TRACE_ID, "inner").call(trace::run)
                          + "/"
                          + trace.run());

      assertThat(seen).isEqualTo("outer/inner/outer");
    }

    @Test
    @DisplayName("a binding made while building a task has ended before the task runs")
    void bindAroundTheRun() {
      assertThat(
              failureOf(boundWhileBuilding(ORDER, "req-7").runSafe(), NoSuchElementException.class))
          .hasMessage("ScopedValue not bound");
      assertThat(boundWhileRunning(ORDER, "req-7"))
          .isEqualTo(Try.success("[req-7] payment taken for Ada"));
    }

    @Test
    @DisplayName("inside another binding, the task built too early reads that one instead")
    void tooEarlyReadsAnOuterBinding() {
      VTask<String> builtTooEarly = boundWhileBuilding(ORDER, "req-7");

      assertThat(ScopedValue.where(RequestContext.TRACE_ID, "outer").call(builtTooEarly::runSafe))
          .isEqualTo(Try.success("[outer] payment taken for Ada"));
    }

    @Test
    @DisplayName("a test binds the value it wants, and the same task reads each binding")
    void eachBindingIsReadByTheSameTask() {
      VTask<String> task = PAID.toVTask();

      assertThat(ScopedValue.where(RequestContext.TRACE_ID, "test-1").call(task::run))
          .isEqualTo("[test-1] payment taken for Ada");
      assertThat(ScopedValue.where(RequestContext.TRACE_ID, "test-2").call(task::run))
          .isEqualTo("[test-2] payment taken for Ada");
    }
  }

  @Nested
  @DisplayName("which threads see a binding")
  class WhichThreads {

    private static final VTask<String> READ = PAID.toVTask();
    private static final Duration LIMIT = Duration.ofSeconds(30);

    private static VStream<String> readOnce() {
      return VStream.of(1).mapTask(_ -> READ);
    }

    static Stream<Arguments> operatorsWhoseTasksSeeTheBinding() {
      return Stream.of(
          Arguments.of("Par.map2", Par.map2(READ, READ, (a, b) -> a)),
          Arguments.of("Par.map3", Par.map3(READ, READ, READ, (a, b, c) -> a)),
          Arguments.of("Par.zip", Par.zip(READ, READ)),
          Arguments.of("Par.zip3", Par.zip3(READ, READ, READ)),
          Arguments.of("Par.all", Par.all(List.of(READ, READ))),
          Arguments.of("Par.traverse", Par.traverse(List.of(1, 2), _ -> READ)),
          Arguments.of("Par.race", Par.race(List.of(READ, READ))),
          Arguments.of("Scope.allSucceed", Scope.<String>allSucceed().fork(READ).fork(READ).join()),
          Arguments.of("Scope.anySucceed", Scope.<String>anySucceed().fork(READ).fork(READ).join()),
          Arguments.of(
              "Scope.firstComplete", Scope.<String>firstComplete().fork(READ).fork(READ).join()),
          Arguments.of(
              "Scope.accumulating",
              Scope.<Throwable, String>accumulating(error -> error)
                  .fork(READ)
                  .join()
                  .map(validated -> validated.isValid() ? "valid" : validated.toString())
                  .flatMap(
                      outcome ->
                          outcome.equals("valid")
                              ? VTask.succeed(outcome)
                              : VTask.fail(new AssertionError(outcome)))),
          Arguments.of(
              "Scope with its own timeout",
              Scope.<String>allSucceed().timeout(LIMIT).fork(READ).join()),
          Arguments.of(
              "VStreamPar.parEvalMap",
              VStreamPar.parEvalMap(VStream.of(1, 2), 2, _ -> READ).toList()),
          Arguments.of(
              "VStreamPar.merge of one stream", VStreamPar.merge(List.of(readOnce())).toList()));
    }

    static Stream<Arguments> operatorsWhoseTasksRunOnThreadsOfTheirOwn() {
      return Stream.of(
          Arguments.of("VTask.timeout", READ.timeout(LIMIT)),
          Arguments.of("VTask.detachOnCancel", READ.detachOnCancel()),
          Arguments.of(
              "VStreamPar.parEvalMapUnordered",
              VStreamPar.parEvalMapUnordered(VStream.of(1, 2), 2, _ -> READ).toList()),
          Arguments.of(
              "VStreamPar.merge of two streams",
              VStreamPar.merge(List.of(readOnce(), readOnce())).toList()),
          Arguments.of("VTaskPath.timeout", Path.vtaskPath(READ).timeout(LIMIT).run()),
          Arguments.of("VTaskPath.withTimeout", Path.vtaskPath(READ).withTimeout(LIMIT).run()),
          Arguments.of(
              "ResilienceBuilder with a timeout",
              Resilience.builder(READ).withTimeout(LIMIT).build()),
          Arguments.of("CircuitBreaker.protect", CircuitBreaker.withDefaults().protect(READ)),
          Arguments.of(
              "VTaskPath.withCircuitBreaker",
              Path.vtaskPath(READ).withCircuitBreaker(CircuitBreaker.withDefaults()).run()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operatorsWhoseTasksSeeTheBinding")
    @DisplayName("these operators run their tasks where they see the caller's binding")
    void theseSee(String operator, VTask<?> task) {
      assertThat(inRequest(task).isSuccess()).as(operator).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operatorsWhoseTasksRunOnThreadsOfTheirOwn")
    @DisplayName("these operators run their tasks on threads of their own, which are not bound")
    void theseDoNotSee(String operator, VTask<?> task) {
      failureOf(inRequest(task), NoSuchElementException.class);
    }

    @Test
    @DisplayName("Par runs the tasks it forks on threads other than the caller's")
    void parForksOntoOtherThreads() {
      Thread caller = Thread.currentThread();
      VTask<Thread> where = VTask.of(Thread::currentThread);
      VTask<String> both =
          Par.map2(where, READ, (thread, line) -> thread == caller ? "on the caller" : line);

      assertThat(inRequest(both)).isEqualTo(Try.success("[req-7] payment taken for Ada"));
    }

    @Test
    @DisplayName("Par.map2 joins two audit lines, each with the request's trace id")
    void parForksSee() {
      VTask<String> both =
          Par.map2(
              READ,
              auditLine(ORDER, "receipt sent").toVTask(),
              (first, second) -> first + "; " + second);

      assertThat(inRequest(both))
          .isEqualTo(Try.success("[req-7] payment taken for Ada; [req-7] receipt sent for Ada"));
    }

    @Test
    @DisplayName("runAsync runs its task on a thread of its own, which is not bound")
    void runAsyncDoesNotSee() {
      assertThatThrownBy(
              () ->
                  ScopedValue.where(RequestContext.TRACE_ID, "req-7")
                      .call(() -> READ.runAsync().join()))
          .isInstanceOf(CompletionException.class)
          .hasCauseInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("reading the value first and binding it again inside the timed task carries it")
    void carryTheValueAcross() {
      assertThat(inRequest(timedWithTrace(PAID, LIMIT)))
          .isEqualTo(Try.success("[req-7] payment taken for Ada"));
    }
  }

  @Nested
  @DisplayName("several keys")
  class SeveralKeys {

    @Test
    @DisplayName("two keys of different types are read together in a VTask")
    void twoKeysMeetInAVTask() {
      Try<String> confirmed =
          ScopedValue.where(RequestContext.TRACE_ID, "req-7")
              .where(RequestContext.LOCALE, Locale.UK)
              .call(confirmation(ORDER)::runSafe);

      assertThat(confirmed).isEqualTo(Try.success("[req-7] confirmation for Ada in en-GB"));
    }

    @Test
    @DisplayName("two keys of the same type share one Context chain")
    void sameTypedKeysShareAChain() {
      Context<String, String> both =
          Context.map2(
              Context.ask(RequestContext.TRACE_ID),
              Context.ask(RequestContext.CORRELATION_ID),
              (trace, correlation) -> trace + "/" + correlation);

      String read =
          ScopedValue.where(RequestContext.TRACE_ID, "req-7")
              .where(RequestContext.CORRELATION_ID, "checkout-3")
              .call(both::run);

      assertThat(read).isEqualTo("req-7/checkout-3");
    }
  }

  @Nested
  @DisplayName("a key nobody bound")
  class Unbound {

    @Test
    @DisplayName("run throws NoSuchElementException, and runSafe gives it as a Failure")
    void runThrows() {
      assertThatThrownBy(PAID::run)
          .isInstanceOf(NoSuchElementException.class)
          .hasMessage("ScopedValue not bound");
      failureOf(PAID.toVTask().runSafe(), NoSuchElementException.class);
    }

    @Test
    @DisplayName("recover gives a default, and toMaybe runs at once and gives Nothing")
    void recoverAndToMaybe() {
      assertThat(Context.ask(RequestContext.TRACE_ID).recover(error -> "no trace").run())
          .isEqualTo("no trace");
      assertThat(PAID.toMaybe()).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("a key bound to null fails ask, and asks with Maybe::fromNullable reads Nothing")
    void boundToNull() {
      assertThatThrownBy(
              () ->
                  ScopedValue.where(RequestContext.TRACE_ID, null)
                      .call(() -> Context.ask(RequestContext.TRACE_ID).run()))
          .isInstanceOf(NullPointerException.class);

      Maybe<String> maybeTrace =
          ScopedValue.where(RequestContext.TRACE_ID, null)
              .call(() -> Context.asks(RequestContext.TRACE_ID, Maybe::fromNullable).run());

      assertThat(maybeTrace).isEqualTo(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("failure and null")
  class FailureAndNull {

    @Test
    @DisplayName(
        "fail with a checked exception throws ContextException, and recover sees the original")
    void checkedFailures() {
      Context<String, String> failed = Context.fail(new IOException("feed closed"));

      assertThatThrownBy(failed::run)
          .isInstanceOf(ContextException.class)
          .hasCauseInstanceOf(IOException.class);
      assertThat(failed.recover(error -> error.getClass().getSimpleName()).run())
          .isEqualTo("IOException");
    }

    @Test
    @DisplayName("a step that returns null fails the run, with a message naming the function")
    void aNullStepFails() {
      Context<String, String> nothing = Context.asks(RequestContext.TRACE_ID, _ -> null);

      assertThatThrownBy(
              () -> ScopedValue.where(RequestContext.TRACE_ID, "req-7").call(nothing::run))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f must not return null");
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("the instance's map2 joins two widened reads, and runContext runs them")
    void theInstanceJoins() {
      Monad<ContextKind.Witness<String>> traced = Instances.monad(context());

      String ran =
          ScopedValue.where(RequestContext.TRACE_ID, "req-7")
              .call(
                  () ->
                      CONTEXT.runContext(
                          traced.map2(
                              CONTEXT.widen(PAID),
                              CONTEXT.widen(auditLine(ORDER, "receipt sent")),
                              (first, second) -> first + "; " + second)));

      assertThat(ran).isEqualTo("[req-7] payment taken for Ada; [req-7] receipt sent for Ada");
    }

    @Test
    @DisplayName("widen returns the same object, since Context implements ContextKind")
    void widenAllocatesNothing() {
      assertThat(CONTEXT.widen(PAID)).isSameAs(PAID);
      assertThat(CONTEXT.narrow(CONTEXT.widen(PAID))).isSameAs(PAID);
    }
  }
}
