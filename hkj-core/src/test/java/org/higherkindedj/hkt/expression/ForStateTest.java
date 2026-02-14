// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Lens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link ForState} class.
 *
 * <p>These tests verify that state-threaded comprehensions using lenses work correctly for managing
 * state in monadic workflows.
 */
@DisplayName("ForState Tests")
class ForStateTest {

  // --- Test Data Classes ---

  record WorkflowContext(
      String orderId, boolean validated, boolean processed, String confirmationId) {
    static WorkflowContext start(String orderId) {
      return new WorkflowContext(orderId, false, false, null);
    }
  }

  record Counter(int value, String lastOperation) {}

  // --- Common Test Fixtures ---

  private IdMonad idMonad;
  private MaybeMonad maybeMonad;

  private Lens<WorkflowContext, Boolean> validatedLens;
  private Lens<WorkflowContext, Boolean> processedLens;
  private Lens<WorkflowContext, String> confirmationIdLens;

  private Lens<Counter, Integer> valueLens;
  private Lens<Counter, String> operationLens;

  @BeforeEach
  void setUp() {
    idMonad = IdMonad.instance();
    maybeMonad = MaybeMonad.INSTANCE;

    validatedLens =
        Lens.of(
            WorkflowContext::validated,
            (ctx, v) ->
                new WorkflowContext(ctx.orderId(), v, ctx.processed(), ctx.confirmationId()));
    processedLens =
        Lens.of(
            WorkflowContext::processed,
            (ctx, p) ->
                new WorkflowContext(ctx.orderId(), ctx.validated(), p, ctx.confirmationId()));
    confirmationIdLens =
        Lens.of(
            WorkflowContext::confirmationId,
            (ctx, id) -> new WorkflowContext(ctx.orderId(), ctx.validated(), ctx.processed(), id));

    valueLens = Lens.of(Counter::value, (c, v) -> new Counter(v, c.lastOperation()));
    operationLens = Lens.of(Counter::lastOperation, (c, op) -> new Counter(c.value(), op));
  }

  // --- Basic Operations ---

  @Nested
  @DisplayName("withState()")
  class WithStateTests {

    @Test
    @DisplayName("should create state builder")
    void withStateCreatesBuilder() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      ForState.Steps<IdKind.Witness, WorkflowContext> steps =
          ForState.withState(idMonad, Id.of(ctx));

      assertThat(steps).isNotNull();
    }

    @Test
    @DisplayName("should throw on null monad")
    void withStateThrowsOnNullMonad() {
      assertThatThrownBy(() -> ForState.withState(null, Id.of("test")))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("monad");
    }

    @Test
    @DisplayName("should throw on null initial state")
    void withStateThrowsOnNullInitialState() {
      assertThatThrownBy(() -> ForState.withState(idMonad, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("initialState");
    }
  }

  // --- yield() Operations ---

  @Nested
  @DisplayName("yield() - Return Final State")
  class YieldTests {

    @Test
    @DisplayName("should return unchanged state with no operations")
    void yieldReturnsUnchangedState() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<IdKind.Witness, WorkflowContext> result =
          ForState.withState(idMonad, Id.of(ctx)).yield();

      WorkflowContext finalCtx = IdKindHelper.ID.unwrap(result);
      assertThat(finalCtx).isEqualTo(ctx);
    }

    @Test
    @DisplayName("should apply projection function")
    void yieldWithProjection() {
      WorkflowContext ctx = new WorkflowContext("ORD-123", true, true, "CONF-456");

      Kind<IdKind.Witness, String> result =
          ForState.withState(idMonad, Id.of(ctx))
              .yield(c -> c.orderId() + ":" + c.confirmationId());

      String projected = IdKindHelper.ID.unwrap(result);
      assertThat(projected).isEqualTo("ORD-123:CONF-456");
    }

    @Test
    @DisplayName("yield with projection should throw on null function")
    void yieldThrowsOnNullProjection() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      assertThatThrownBy(() -> ForState.withState(idMonad, Id.of(ctx)).yield(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("projection");
    }
  }

  // --- update() Operations ---

  @Nested
  @DisplayName("update() - Set Field Value")
  class UpdateTests {

    @Test
    @DisplayName("should set field value via lens")
    void updateSetsFieldValue() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<IdKind.Witness, WorkflowContext> result =
          ForState.withState(idMonad, Id.of(ctx)).update(validatedLens, true).yield();

      WorkflowContext finalCtx = IdKindHelper.ID.unwrap(result);
      assertThat(finalCtx.validated()).isTrue();
      assertThat(finalCtx.orderId()).isEqualTo("ORD-123");
    }

    @Test
    @DisplayName("should chain multiple updates")
    void updateChainedMultiple() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<IdKind.Witness, WorkflowContext> result =
          ForState.withState(idMonad, Id.of(ctx))
              .update(validatedLens, true)
              .update(processedLens, true)
              .update(confirmationIdLens, "CONF-456")
              .yield();

      WorkflowContext finalCtx = IdKindHelper.ID.unwrap(result);
      assertThat(finalCtx.validated()).isTrue();
      assertThat(finalCtx.processed()).isTrue();
      assertThat(finalCtx.confirmationId()).isEqualTo("CONF-456");
    }

    @Test
    @DisplayName("update() should throw on null lens")
    void updateThrowsOnNullLens() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      assertThatThrownBy(() -> ForState.withState(idMonad, Id.of(ctx)).update(null, true))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }
  }

  // --- modify() Operations ---

  @Nested
  @DisplayName("modify() - Transform Field Value")
  class ModifyTests {

    @Test
    @DisplayName("should modify field value via lens")
    void modifyTransformsFieldValue() {
      Counter counter = new Counter(10, "init");

      Kind<IdKind.Witness, Counter> result =
          ForState.withState(idMonad, Id.of(counter))
              .modify(valueLens, v -> v * 2)
              .modify(operationLens, op -> op + ":doubled")
              .yield();

      Counter finalCounter = IdKindHelper.ID.unwrap(result);
      assertThat(finalCounter.value()).isEqualTo(20);
      assertThat(finalCounter.lastOperation()).isEqualTo("init:doubled");
    }

    @Test
    @DisplayName("modify() should throw on null lens")
    void modifyThrowsOnNullLens() {
      Counter counter = new Counter(10, "init");

      assertThatThrownBy(() -> ForState.withState(idMonad, Id.of(counter)).modify(null, x -> x))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("modify() should throw on null modifier")
    void modifyThrowsOnNullModifier() {
      Counter counter = new Counter(10, "init");

      assertThatThrownBy(() -> ForState.withState(idMonad, Id.of(counter)).modify(valueLens, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }
  }

  // --- from() Operations ---

  @Nested
  @DisplayName("from() - Execute Operation Without State Change")
  class FromTests {

    @Test
    @DisplayName("should execute operation but keep state unchanged")
    void fromExecutesWithoutStateChange() {
      Counter counter = new Counter(10, "init");

      Kind<IdKind.Witness, Counter> result =
          ForState.withState(idMonad, Id.of(counter))
              .from(c -> Id.of(c.value() * 100)) // Compute but don't store
              .yield();

      Counter finalCounter = IdKindHelper.ID.unwrap(result);
      assertThat(finalCounter.value()).isEqualTo(10); // Unchanged
      assertThat(finalCounter.lastOperation()).isEqualTo("init"); // Unchanged
    }

    @Test
    @DisplayName("from() should throw on null function")
    void fromThrowsOnNullFunction() {
      Counter counter = new Counter(10, "init");

      assertThatThrownBy(() -> ForState.withState(idMonad, Id.of(counter)).from(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function");
    }
  }

  // --- fromThen() Operations ---

  @Nested
  @DisplayName("fromThen() - Execute Operation and Update State")
  class FromThenTests {

    @Test
    @DisplayName("should execute operation and update state via lens")
    void fromThenExecutesAndUpdates() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<IdKind.Witness, WorkflowContext> result =
          ForState.withState(idMonad, Id.of(ctx))
              .fromThen(c -> Id.of("CONF-" + c.orderId()), confirmationIdLens)
              .yield();

      WorkflowContext finalCtx = IdKindHelper.ID.unwrap(result);
      assertThat(finalCtx.confirmationId()).isEqualTo("CONF-ORD-123");
    }

    @Test
    @DisplayName("should chain fromThen operations")
    void fromThenChained() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<IdKind.Witness, WorkflowContext> result =
          ForState.withState(idMonad, Id.of(ctx))
              .fromThen(c -> Id.of(true), validatedLens)
              .fromThen(c -> Id.of(true), processedLens)
              .fromThen(c -> Id.of("CONF-456"), confirmationIdLens)
              .yield();

      WorkflowContext finalCtx = IdKindHelper.ID.unwrap(result);
      assertThat(finalCtx.validated()).isTrue();
      assertThat(finalCtx.processed()).isTrue();
      assertThat(finalCtx.confirmationId()).isEqualTo("CONF-456");
    }

    @Test
    @DisplayName("fromThen() should throw on null function")
    void fromThenThrowsOnNullFunction() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      assertThatThrownBy(
              () -> ForState.withState(idMonad, Id.of(ctx)).fromThen(null, validatedLens))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function");
    }

    @Test
    @DisplayName("fromThen() should throw on null lens")
    void fromThenThrowsOnNullLens() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      assertThatThrownBy(
              () -> ForState.withState(idMonad, Id.of(ctx)).fromThen(c -> Id.of(true), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }
  }

  // --- Maybe Monad Integration ---

  @Nested
  @DisplayName("Maybe Monad Integration")
  class MaybeMonadTests {

    @Test
    @DisplayName("should propagate Just through state operations")
    void propagatesJustThroughOperations() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .update(validatedLens, true)
              .update(confirmationIdLens, "CONF-789")
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(new WorkflowContext("ORD-123", true, false, "CONF-789")));
    }

    @Test
    @DisplayName("should short-circuit Nothing through state operations")
    void shortCircuitsNothingThroughOperations() {
      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.<WorkflowContext>nothing())
              .update(validatedLens, true)
              .update(confirmationIdLens, "CONF-789")
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should short-circuit when fromThen returns Nothing")
    void shortCircuitsWhenFromThenReturnsNothing() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .update(validatedLens, true)
              .fromThen(c -> MAYBE.<String>nothing(), confirmationIdLens) // Fails here
              .update(processedLens, true) // Never reached
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }
  }

  // --- Complex Workflow Scenarios ---

  @Nested
  @DisplayName("Complex Workflow Scenarios")
  class ComplexScenarios {

    @Test
    @DisplayName("should support complete workflow with mixed operations")
    void completeWorkflowWithMixedOperations() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<IdKind.Witness, String> result =
          ForState.withState(idMonad, Id.of(ctx))
              .update(validatedLens, true)
              .from(c -> Id.of("logging: " + c.orderId())) // Side effect
              .update(processedLens, true)
              .fromThen(c -> Id.of("CONF-" + c.orderId().hashCode()), confirmationIdLens)
              .yield(c -> "Order " + c.orderId() + " confirmed: " + c.confirmationId());

      String finalResult = IdKindHelper.ID.unwrap(result);
      assertThat(finalResult).startsWith("Order ORD-123 confirmed: CONF-");
    }
  }
}
