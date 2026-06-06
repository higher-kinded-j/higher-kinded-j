// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link ForState} class.
 *
 * <p>These tests verify that state-threaded comprehensions using lenses work correctly for managing
 * state in monadic workflows, including Phase 2 features: FilterableSteps, when(), matchThen(),
 * traverse(), and zoom().
 */
@DisplayName("ForState Tests")
class ForStateTest {

  // --- Test Data Classes ---

  record WorkflowContext(
      String orderId, boolean validated, boolean processed, String confirmationId) {
    @SuppressWarnings({"SameParameterValue", "DataFlowIssue"}) // confirmationId is null here
    static WorkflowContext start(String orderId) {
      return new WorkflowContext(orderId, false, false, null);
    }
  }

  record Counter(int value, String lastOperation) {}

  /** A sum type for testing matchThen with Prism. */
  sealed interface OrderStatus {
    record Pending(String reason) implements OrderStatus {}

    record Confirmed(String confirmationId) implements OrderStatus {}

    @SuppressWarnings("unused")
    record Cancelled(String reason) implements OrderStatus {}
  }

  record OrderContext(String orderId, OrderStatus status, String extractedId, List<String> tags) {}

  /** A nested record for testing zoom. */
  record Address(String street, String city, String zip) {}

  record Customer(String name, Address address, int loyaltyPoints) {}

  // --- Common Test Fixtures ---

  private Monad<IdKind.Witness> idMonad;
  private MonadZero<MaybeKind.Witness> maybeMonad;

  private Lens<WorkflowContext, Boolean> validatedLens;
  private Lens<WorkflowContext, Boolean> processedLens;
  private Lens<WorkflowContext, String> confirmationIdLens;

  private Lens<Counter, Integer> valueLens;
  private Lens<Counter, String> operationLens;

  private Lens<OrderContext, OrderStatus> statusLens;
  private Lens<OrderContext, String> extractedIdLens;
  private Lens<OrderContext, List<String>> tagsLens;

  private Lens<Customer, Address> addressLens;
  private Lens<Customer, String> nameLens;
  private Lens<Customer, Integer> loyaltyLens;
  private Lens<Address, String> streetLens;
  private Lens<Address, String> cityLens;
  private Lens<Address, String> zipLens;

  @BeforeEach
  void setUp() {
    idMonad = Instances.monad(id());
    maybeMonad = Instances.monadZero(maybe());

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

    statusLens =
        Lens.of(
            OrderContext::status,
            (ctx, s) -> new OrderContext(ctx.orderId(), s, ctx.extractedId(), ctx.tags()));
    extractedIdLens =
        Lens.of(
            OrderContext::extractedId,
            (ctx, id) -> new OrderContext(ctx.orderId(), ctx.status(), id, ctx.tags()));
    tagsLens =
        Lens.of(
            OrderContext::tags,
            (ctx, t) -> new OrderContext(ctx.orderId(), ctx.status(), ctx.extractedId(), t));

    addressLens =
        Lens.of(Customer::address, (c, a) -> new Customer(c.name(), a, c.loyaltyPoints()));
    nameLens = Lens.of(Customer::name, (c, n) -> new Customer(n, c.address(), c.loyaltyPoints()));
    loyaltyLens =
        Lens.of(Customer::loyaltyPoints, (c, lp) -> new Customer(c.name(), c.address(), lp));
    streetLens = Lens.of(Address::street, (a, s) -> new Address(s, a.city(), a.zip()));
    cityLens = Lens.of(Address::city, (a, c) -> new Address(a.street(), c, a.zip()));
    zipLens = Lens.of(Address::zip, (a, z) -> new Address(a.street(), a.city(), z));
  }

  // --- Basic Operations (Phase 1) ---

  @Nested
  @DisplayName("withState(Monad)")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
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
      assertThatThrownBy(() -> ForState.withState((Monad<IdKind.Witness>) null, Id.of("test")))
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

  @Nested
  @DisplayName("withState(MonadZero)")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class WithStateMonadZeroTests {

    @Test
    @DisplayName("should create filterable state builder")
    void withStateMonadZeroCreatesFilterableBuilder() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      ForState.FilterableSteps<MaybeKind.Witness, WorkflowContext> steps =
          ForState.withState(maybeMonad, MAYBE.just(ctx));

      assertThat(steps).isNotNull();
    }

    @Test
    @DisplayName("should throw on null MonadZero")
    @SuppressWarnings("RedundantCast") // explicit cast pins the overload under test
    void withStateMonadZeroThrowsOnNullMonad() {
      assertThatThrownBy(
              () -> ForState.withState((MonadZero<MaybeKind.Witness>) null, MAYBE.just("test")))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("monad");
    }

    @Test
    @DisplayName("should throw on null initial state for MonadZero overload")
    void withStateMonadZeroThrowsOnNullInitialState() {
      assertThatThrownBy(() -> ForState.withState(maybeMonad, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("initialState");
    }
  }

  // --- yield() Operations ---

  @Nested
  @DisplayName("yield() - Return Final State")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
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

    @Test
    @DisplayName("filterable yield should return final state")
    void filterableYieldReturnsFinalState() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx)).update(validatedLens, true).yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(new WorkflowContext("ORD-123", true, false, null)));
    }

    @Test
    @DisplayName("filterable yield with projection should transform")
    void filterableYieldWithProjection() {
      WorkflowContext ctx = new WorkflowContext("ORD-123", true, true, "CONF-456");

      Kind<MaybeKind.Witness, String> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .yield(c -> c.orderId() + ":" + c.confirmationId());

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("ORD-123:CONF-456"));
    }

    @Test
    @DisplayName("filterable yield with projection should throw on null")
    void filterableYieldThrowsOnNullProjection() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      assertThatThrownBy(() -> ForState.withState(maybeMonad, MAYBE.just(ctx)).yield(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("projection");
    }
  }

  // --- update() Operations ---

  @Nested
  @DisplayName("update() - Set Field Value")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
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
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
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
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
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
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
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
              .fromThen(_ -> Id.of(true), validatedLens)
              .fromThen(_ -> Id.of(true), processedLens)
              .fromThen(_ -> Id.of("CONF-456"), confirmationIdLens)
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
              () -> ForState.withState(idMonad, Id.of(ctx)).fromThen(_ -> Id.of(true), null))
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
              .fromThen(_ -> MAYBE.nothing(), confirmationIdLens) // Fails here
              .update(processedLens, true) // Never reached
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }
  }

  // --- Phase 2: when() Guard ---

  @Nested
  @DisplayName("when() - Predicate Guard")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class WhenTests {

    @Test
    @DisplayName("should continue when predicate is true")
    void whenPredicateTrueContinues() {
      WorkflowContext ctx = new WorkflowContext("ORD-123", true, false, null);

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .when(WorkflowContext::validated)
              .update(processedLens, true)
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(new WorkflowContext("ORD-123", true, true, null)));
    }

    @Test
    @DisplayName("should short-circuit to zero when predicate is false")
    void whenPredicateFalseShortCircuits() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .when(WorkflowContext::validated) // false -> Nothing
              .update(processedLens, true) // Never reached
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should chain multiple when guards")
    void whenChainedMultiple() {
      WorkflowContext ctx = new WorkflowContext("ORD-123", true, true, null);

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .when(WorkflowContext::validated)
              .when(WorkflowContext::processed)
              .update(confirmationIdLens, "CONF-OK")
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(new WorkflowContext("ORD-123", true, true, "CONF-OK")));
    }

    @Test
    @DisplayName("should short-circuit on second guard failure")
    void whenSecondGuardFails() {
      WorkflowContext ctx = new WorkflowContext("ORD-123", true, false, null);

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .when(WorkflowContext::validated) // true
              .when(WorkflowContext::processed) // false -> Nothing
              .update(confirmationIdLens, "CONF-OK") // Never reached
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("should work with update before when")
    void whenAfterUpdate() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .update(validatedLens, true)
              .when(WorkflowContext::validated) // now true after update
              .update(processedLens, true)
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(new WorkflowContext("ORD-123", true, true, null)));
    }

    @Test
    @DisplayName("when() should throw on null predicate")
    void whenThrowsOnNullPredicate() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      assertThatThrownBy(() -> ForState.withState(maybeMonad, MAYBE.just(ctx)).when(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("predicate");
    }

    @Test
    @DisplayName("when() should propagate Nothing input")
    void whenPropagatesNothing() {
      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.<WorkflowContext>nothing())
              .when(_ -> true) // Nothing stays Nothing
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }
  }

  // --- Phase 2: matchThen() with Prism ---

  @Nested
  @DisplayName("matchThen() - Prism-Based Pattern Matching")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class MatchThenPrismTests {

    @Test
    @DisplayName("should extract and store when prism matches")
    void matchThenExtractsWhenPrismMatches() {
      OrderContext ctx =
          new OrderContext("ORD-1", new OrderStatus.Confirmed("CONF-ABC"), null, List.of());

      Lens<OrderContext, String> extractedIdLensLocal = extractedIdLens;
      Lens<OrderContext, OrderStatus> statusLensLocal = statusLens;

      // We need a lens for the confirmationId field from Confirmed,
      // and a prism from OrderStatus -> Confirmed. We'll use matchThen to
      // extract confirmationId from the Confirmed case.
      // matchThen(sourceLens, prism, targetLens)
      // sourceLens: gets OrderStatus from OrderContext
      // prism: matches Confirmed from OrderStatus
      // targetLens: here we need to extract and store. But the prism gives us Confirmed,
      // and targetLens expects the prism target type.
      // So we need a Lens<OrderContext, OrderStatus.Confirmed> that stores the Confirmed value.
      // Actually matchThen stores the prism result type into targetLens.
      // Let's create a lens that stores the confirmation id string from the Confirmed record.

      // Actually the signature is: <X, A> matchThen(Lens<S, X> sourceLens, Prism<X, A> prism,
      // Lens<S, A> targetLens)
      // So: X = OrderStatus, A = OrderStatus.Confirmed
      // targetLens: Lens<OrderContext, OrderStatus.Confirmed>

      // For simplicity, let's test with a String prism approach instead
      Prism<OrderStatus, String> confirmedIdPrism =
          Prism.of(
              s ->
                  s instanceof OrderStatus.Confirmed(var confirmationId)
                      ? Optional.of(confirmationId)
                      : Optional.empty(),
              OrderStatus.Confirmed::new);

      Kind<MaybeKind.Witness, OrderContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .matchThen(statusLensLocal, confirmedIdPrism, extractedIdLensLocal)
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(
              Maybe.just(
                  new OrderContext(
                      "ORD-1", new OrderStatus.Confirmed("CONF-ABC"), "CONF-ABC", List.of())));
    }

    @Test
    @DisplayName("should short-circuit to zero when prism does not match")
    void matchThenShortCircuitsWhenPrismDoesNotMatch() {
      OrderContext ctx =
          new OrderContext("ORD-1", new OrderStatus.Pending("awaiting payment"), null, List.of());

      Prism<OrderStatus, String> confirmedIdPrism =
          Prism.of(
              s ->
                  s instanceof OrderStatus.Confirmed(var confirmationId)
                      ? Optional.of(confirmationId)
                      : Optional.empty(),
              OrderStatus.Confirmed::new);

      Kind<MaybeKind.Witness, OrderContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .matchThen(
                  statusLens, confirmedIdPrism, extractedIdLens) // Pending != Confirmed -> Nothing
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("matchThen() should throw on null sourceLens")
    void matchThenThrowsOnNullSourceLens() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());

      Prism<OrderStatus, String> prism =
          Prism.of(_ -> Optional.empty(), OrderStatus.Confirmed::new);

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(ctx))
                      .matchThen(null, prism, extractedIdLens))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("sourceLens");
    }

    @Test
    @DisplayName("matchThen() should throw on null prism")
    void matchThenThrowsOnNullPrism() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(ctx))
                      .matchThen(statusLens, null, extractedIdLens))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("prism");
    }

    @Test
    @DisplayName("matchThen() should throw on null targetLens")
    void matchThenThrowsOnNullTargetLens() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());

      Prism<OrderStatus, String> prism =
          Prism.of(_ -> Optional.empty(), OrderStatus.Confirmed::new);

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(ctx))
                      .matchThen(statusLens, prism, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("targetLens");
    }
  }

  // --- Phase 2: matchThen() with Function ---

  @Nested
  @DisplayName("matchThen() - Function-Based Extraction")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class MatchThenFunctionTests {

    @Test
    @DisplayName("should extract and store when function returns present")
    void matchThenFunctionExtractsWhenPresent() {
      OrderContext ctx =
          new OrderContext("ORD-1", new OrderStatus.Confirmed("CONF-XYZ"), null, List.of());

      Kind<MaybeKind.Witness, OrderContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .matchThen(
                  c ->
                      c.status() instanceof OrderStatus.Confirmed(var confirmationId)
                          ? Optional.of(confirmationId)
                          : Optional.empty(),
                  extractedIdLens)
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(
              Maybe.just(
                  new OrderContext(
                      "ORD-1", new OrderStatus.Confirmed("CONF-XYZ"), "CONF-XYZ", List.of())));
    }

    @Test
    @DisplayName("should short-circuit when function returns empty")
    void matchThenFunctionShortCircuitsWhenEmpty() {
      OrderContext ctx =
          new OrderContext("ORD-1", new OrderStatus.Pending("waiting"), null, List.of());

      Kind<MaybeKind.Witness, OrderContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .matchThen(
                  c ->
                      c.status() instanceof OrderStatus.Confirmed(var confirmationId)
                          ? Optional.of(confirmationId)
                          : Optional.empty(),
                  extractedIdLens)
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("matchThen(function) should throw on null extractor")
    void matchThenFunctionThrowsOnNullExtractor() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(ctx)).matchThen(null, extractedIdLens))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("extractor");
    }

    @Test
    @DisplayName("matchThen(function) should throw on null targetLens")
    void matchThenFunctionThrowsOnNullTargetLens() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(ctx))
                      .matchThen(_ -> Optional.of("x"), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("targetLens");
    }
  }

  // --- Phase 2: traverse() ---

  @Nested
  @DisplayName("traverse() - Bulk Operations on Collection Fields")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class TraverseTests {

    @Test
    @DisplayName("should apply function to each element in collection field")
    void traverseAppliesFunctionToEachElement() {
      OrderContext ctx =
          new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of("a", "b", "c"));
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      Kind<IdKind.Witness, OrderContext> result =
          ForState.withState(idMonad, Id.of(ctx))
              .traverse(tagsLens, listTraversal, tag -> Id.of(tag.toUpperCase()))
              .yield();

      OrderContext finalCtx = IdKindHelper.ID.unwrap(result);
      assertThat(finalCtx.tags()).containsExactly("A", "B", "C");
      assertThat(finalCtx.orderId()).isEqualTo("ORD-1"); // Other fields unchanged
    }

    @Test
    @DisplayName("should handle empty collection")
    void traverseHandlesEmptyCollection() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      Kind<IdKind.Witness, OrderContext> result =
          ForState.withState(idMonad, Id.of(ctx))
              .traverse(tagsLens, listTraversal, tag -> Id.of(tag.toUpperCase()))
              .yield();

      OrderContext finalCtx = IdKindHelper.ID.unwrap(result);
      assertThat(finalCtx.tags()).isEmpty();
    }

    @Test
    @DisplayName("should work with Maybe monad for effectful traversal")
    void traverseWithMaybeMonad() {
      OrderContext ctx =
          new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of("ok", "ok"));
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      Kind<MaybeKind.Witness, OrderContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .traverse(
                  tagsLens,
                  listTraversal,
                  tag -> tag.equals("ok") ? MAYBE.just(tag + "!") : MAYBE.nothing())
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(
              Maybe.just(
                  new OrderContext(
                      "ORD-1", new OrderStatus.Pending("x"), null, List.of("ok!", "ok!"))));
    }

    @Test
    @DisplayName("should short-circuit when any element fails in Maybe")
    void traverseShortCircuitsOnFailure() {
      OrderContext ctx =
          new OrderContext(
              "ORD-1", new OrderStatus.Pending("x"), null, List.of("ok", "fail", "ok"));
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      Kind<MaybeKind.Witness, OrderContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .traverse(
                  tagsLens,
                  listTraversal,
                  tag -> tag.equals("ok") ? MAYBE.just(tag) : MAYBE.nothing())
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("traverse() should throw on null collectionLens")
    void traverseThrowsOnNullCollectionLens() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      assertThatThrownBy(
              () -> ForState.withState(idMonad, Id.of(ctx)).traverse(null, listTraversal, Id::of))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("collectionLens");
    }

    @Test
    @DisplayName("traverse() should throw on null traversal")
    void traverseThrowsOnNullTraversal() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());

      assertThatThrownBy(
              () -> ForState.withState(idMonad, Id.of(ctx)).traverse(tagsLens, null, Id::of))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("traverse() should throw on null function")
    void traverseThrowsOnNullFunction() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      assertThatThrownBy(
              () -> ForState.withState(idMonad, Id.of(ctx)).traverse(tagsLens, listTraversal, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function");
    }

    @Test
    @DisplayName("filterable traverse should return FilterableSteps")
    void filterableTraverseReturnsFilterableSteps() {
      OrderContext ctx =
          new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of("a", "b"));
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      // Verify that traverse on filterable steps returns FilterableSteps (can chain when())
      Kind<MaybeKind.Witness, OrderContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .traverse(tagsLens, listTraversal, tag -> MAYBE.just(tag.toUpperCase()))
              .when(c -> c.tags().contains("A")) // Chaining when after traverse
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(
              Maybe.just(
                  new OrderContext(
                      "ORD-1", new OrderStatus.Pending("x"), null, List.of("A", "B"))));
    }
  }

  // --- Phase 2: zoom() / endZoom() ---

  @Nested
  @DisplayName("zoom() - State Scope Narrowing")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class ZoomTests {

    @Test
    @DisplayName("should update sub-state fields via zoom")
    void zoomUpdatesSubStateFields() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      Kind<IdKind.Witness, Customer> result =
          ForState.withState(idMonad, Id.of(customer))
              .zoom(addressLens)
              .update(streetLens, "456 Oak")
              .update(cityLens, "Shelbyville")
              .endZoom()
              .yield();

      Customer finalCustomer = IdKindHelper.ID.unwrap(result);
      assertThat(finalCustomer.name()).isEqualTo("Alice");
      assertThat(finalCustomer.address().street()).isEqualTo("456 Oak");
      assertThat(finalCustomer.address().city()).isEqualTo("Shelbyville");
      assertThat(finalCustomer.address().zip()).isEqualTo("62701"); // unchanged
      assertThat(finalCustomer.loyaltyPoints()).isEqualTo(100); // unchanged
    }

    @Test
    @DisplayName("should modify sub-state fields via zoom")
    void zoomModifiesSubStateFields() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      Kind<IdKind.Witness, Customer> result =
          ForState.withState(idMonad, Id.of(customer))
              .zoom(addressLens)
              .modify(zipLens, zip -> zip + "-1234")
              .endZoom()
              .yield();

      Customer finalCustomer = IdKindHelper.ID.unwrap(result);
      assertThat(finalCustomer.address().zip()).isEqualTo("62701-1234");
    }

    @Test
    @DisplayName("should support fromThen within zoom")
    void zoomFromThen() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      Kind<IdKind.Witness, Customer> result =
          ForState.withState(idMonad, Id.of(customer))
              .zoom(addressLens)
              .fromThen(addr -> Id.of(addr.city().toUpperCase()), cityLens)
              .endZoom()
              .yield();

      Customer finalCustomer = IdKindHelper.ID.unwrap(result);
      assertThat(finalCustomer.address().city()).isEqualTo("SPRINGFIELD");
    }

    @Test
    @DisplayName("should return to outer state scope after endZoom")
    void endZoomReturnsToOuterScope() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      Kind<IdKind.Witness, Customer> result =
          ForState.withState(idMonad, Id.of(customer))
              .zoom(addressLens)
              .update(streetLens, "456 Oak")
              .endZoom()
              .update(nameLens, "Bob") // Back to outer state
              .modify(loyaltyLens, lp -> lp + 50)
              .yield();

      Customer finalCustomer = IdKindHelper.ID.unwrap(result);
      assertThat(finalCustomer.name()).isEqualTo("Bob");
      assertThat(finalCustomer.address().street()).isEqualTo("456 Oak");
      assertThat(finalCustomer.loyaltyPoints()).isEqualTo(150);
    }

    @Test
    @DisplayName("zoom() should throw on null zoomLens")
    void zoomThrowsOnNullZoomLens() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(customer)).zoom((Lens<Customer, Address>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("zoomLens");
    }

    @Test
    @DisplayName("zoomed update() should throw on null lens")
    void zoomedUpdateThrowsOnNullLens() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(customer)).zoom(addressLens).update(null, "x"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("zoomed modify() should throw on null lens")
    void zoomedModifyThrowsOnNullLens() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(customer))
                      .zoom(addressLens)
                      .modify(null, x -> x))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("zoomed modify() should throw on null modifier")
    void zoomedModifyThrowsOnNullModifier() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(customer))
                      .zoom(addressLens)
                      .modify(streetLens, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }

    @Test
    @DisplayName("zoomed fromThen() should throw on null function")
    void zoomedFromThenThrowsOnNullFunction() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(customer))
                      .zoom(addressLens)
                      .fromThen(null, streetLens))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function");
    }

    @Test
    @DisplayName("zoomed fromThen() should throw on null lens")
    void zoomedFromThenThrowsOnNullLens() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, Id.of(customer))
                      .zoom(addressLens)
                      .fromThen(_ -> Id.of("x"), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }
  }

  // --- FilterableSteps Covariant Return Types ---

  @Nested
  @DisplayName("FilterableSteps - Covariant Returns")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class FilterableStepsCovariantTests {

    @Test
    @DisplayName("filterable from() should return FilterableSteps")
    void filterableFromReturnsFilterableSteps() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .from(_ -> MAYBE.just("side-effect"))
              .when(_ -> true) // Can chain when() after from()
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(ctx));
    }

    @Test
    @DisplayName("filterable fromThen() should return FilterableSteps")
    void filterableFromThenReturnsFilterableSteps() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .fromThen(_ -> MAYBE.just(true), validatedLens)
              .when(WorkflowContext::validated) // Can chain when() after fromThen()
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(new WorkflowContext("ORD-123", true, false, null)));
    }

    @Test
    @DisplayName("filterable modify() should return FilterableSteps")
    void filterableModifyReturnsFilterableSteps() {
      Counter counter = new Counter(10, "init");

      Kind<MaybeKind.Witness, Counter> result =
          ForState.withState(maybeMonad, MAYBE.just(counter))
              .modify(valueLens, v -> v * 2)
              .when(c -> c.value() > 15) // Can chain when() after modify()
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just(new Counter(20, "init")));
    }

    @Test
    @DisplayName("filterable update() should return FilterableSteps")
    void filterableUpdateReturnsFilterableSteps() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      Kind<MaybeKind.Witness, WorkflowContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .update(validatedLens, true)
              .when(WorkflowContext::validated) // Can chain when() after update()
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(Maybe.just(new WorkflowContext("ORD-123", true, false, null)));
    }

    @Test
    @DisplayName("filterable from() should throw on null function")
    void filterableFromThrowsOnNullFunction() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      assertThatThrownBy(() -> ForState.withState(maybeMonad, MAYBE.just(ctx)).from(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function");
    }

    @Test
    @DisplayName("filterable fromThen() should throw on null function")
    void filterableFromThenThrowsOnNullFunction() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      assertThatThrownBy(
              () -> ForState.withState(maybeMonad, MAYBE.just(ctx)).fromThen(null, validatedLens))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function");
    }

    @Test
    @DisplayName("filterable fromThen() should throw on null lens")
    void filterableFromThenThrowsOnNullLens() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(ctx))
                      .fromThen(_ -> MAYBE.just(true), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("filterable modify() should throw on null lens")
    void filterableModifyThrowsOnNullLens() {
      Counter counter = new Counter(10, "init");

      assertThatThrownBy(
              () -> ForState.withState(maybeMonad, MAYBE.just(counter)).modify(null, x -> x))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("filterable modify() should throw on null modifier")
    void filterableModifyThrowsOnNullModifier() {
      Counter counter = new Counter(10, "init");

      assertThatThrownBy(
              () -> ForState.withState(maybeMonad, MAYBE.just(counter)).modify(valueLens, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }

    @Test
    @DisplayName("filterable update() should throw on null lens")
    void filterableUpdateThrowsOnNullLens() {
      WorkflowContext ctx = WorkflowContext.start("ORD-123");

      assertThatThrownBy(() -> ForState.withState(maybeMonad, MAYBE.just(ctx)).update(null, true))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }
  }

  // --- FilterableSteps traverse null checks ---

  @Nested
  @DisplayName("FilterableSteps traverse() null checks")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class FilterableTraverseNullTests {

    @Test
    @DisplayName("filterable traverse() should throw on null collectionLens")
    void filterableTraverseThrowsOnNullCollectionLens() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(ctx))
                      .traverse(null, listTraversal, MAYBE::just))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("collectionLens");
    }

    @Test
    @DisplayName("filterable traverse() should throw on null traversal")
    void filterableTraverseThrowsOnNullTraversal() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(ctx))
                      .traverse(tagsLens, null, MAYBE::just))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("traversal");
    }

    @Test
    @DisplayName("filterable traverse() should throw on null function")
    void filterableTraverseThrowsOnNullFunction() {
      OrderContext ctx = new OrderContext("ORD-1", new OrderStatus.Pending("x"), null, List.of());
      Traversal<List<String>, String> listTraversal = Traversals.forList();

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(ctx))
                      .traverse(tagsLens, listTraversal, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function");
    }
  }

  // --- FilterableSteps zoom ---

  @Nested
  @DisplayName("FilterableSteps zoom()")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class FilterableZoomTests {

    @Test
    @DisplayName("filterable zoom should work and return to filterable scope")
    void filterableZoomWorks() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      Kind<MaybeKind.Witness, Customer> result =
          ForState.withState(maybeMonad, MAYBE.just(customer))
              .zoom(addressLens)
              .update(streetLens, "456 Oak")
              .endZoom()
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(
              Maybe.just(
                  new Customer("Alice", new Address("456 Oak", "Springfield", "62701"), 100)));
    }

    @Test
    @DisplayName("filterable zoom endZoom should preserve FilterableSteps allowing when()")
    void filterableZoomEndZoomPreservesWhen() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      // when() after endZoom should compile and work — this was previously impossible
      Kind<MaybeKind.Witness, Customer> result =
          ForState.withState(maybeMonad, MAYBE.just(customer))
              .zoom(addressLens)
              .update(streetLens, "456 Oak")
              .endZoom()
              .when(c -> c.loyaltyPoints() >= 50) // guard passes
              .modify(loyaltyLens, lp -> lp + 10)
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(
              Maybe.just(
                  new Customer("Alice", new Address("456 Oak", "Springfield", "62701"), 110)));
    }

    @Test
    @DisplayName("filterable zoom endZoom when() guard fails should short-circuit")
    void filterableZoomEndZoomWhenGuardFails() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 10);

      Kind<MaybeKind.Witness, Customer> result =
          ForState.withState(maybeMonad, MAYBE.just(customer))
              .zoom(addressLens)
              .update(streetLens, "456 Oak")
              .endZoom()
              .when(c -> c.loyaltyPoints() >= 50) // guard fails
              .modify(loyaltyLens, lp -> lp + 10)
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("filterable zoom should throw on null")
    void filterableZoomThrowsOnNull() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(customer))
                      .zoom((Lens<Customer, Address>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("zoomLens");
    }

    @Test
    @DisplayName("filterable zoomed modify should update sub-state field")
    void filterableZoomedModify() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      Kind<MaybeKind.Witness, Customer> result =
          ForState.withState(maybeMonad, MAYBE.just(customer))
              .zoom(addressLens)
              .modify(zipLens, zip -> zip + "-1234")
              .endZoom()
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(
              Maybe.just(
                  new Customer(
                      "Alice", new Address("123 Main", "Springfield", "62701-1234"), 100)));
    }

    @Test
    @DisplayName("filterable zoomed fromThen should perform effectful operation on sub-state")
    void filterableZoomedFromThen() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      Kind<MaybeKind.Witness, Customer> result =
          ForState.withState(maybeMonad, MAYBE.just(customer))
              .zoom(addressLens)
              .fromThen(addr -> MAYBE.just(addr.city().toUpperCase()), cityLens)
              .endZoom()
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(
              Maybe.just(
                  new Customer("Alice", new Address("123 Main", "SPRINGFIELD", "62701"), 100)));
    }

    @Test
    @DisplayName("filterable zoomed update() should throw on null lens")
    void filterableZoomedUpdateThrowsOnNullLens() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(customer))
                      .zoom(addressLens)
                      .update(null, "x"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("filterable zoomed modify() should throw on null lens")
    void filterableZoomedModifyThrowsOnNullLens() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(customer))
                      .zoom(addressLens)
                      .modify(null, x -> x))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }

    @Test
    @DisplayName("filterable zoomed modify() should throw on null modifier")
    void filterableZoomedModifyThrowsOnNullModifier() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(customer))
                      .zoom(addressLens)
                      .modify(streetLens, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("modifier");
    }

    @Test
    @DisplayName("filterable zoomed fromThen() should throw on null function")
    void filterableZoomedFromThenThrowsOnNullFunction() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(customer))
                      .zoom(addressLens)
                      .fromThen(null, streetLens))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("function");
    }

    @Test
    @DisplayName("filterable zoomed fromThen() should throw on null lens")
    void filterableZoomedFromThenThrowsOnNullLens() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(customer))
                      .zoom(addressLens)
                      .fromThen(_ -> MAYBE.just("x"), null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("lens");
    }
  }

  // --- Complex Workflow Scenarios ---

  @Nested
  @DisplayName("Complex Workflow Scenarios")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
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

    @Test
    @DisplayName("should combine when, matchThen, and traverse in workflow")
    void combinedFilterableWorkflow() {
      OrderContext ctx =
          new OrderContext(
              "ORD-1", new OrderStatus.Confirmed("CONF-999"), null, List.of("rush", "fragile"));

      Prism<OrderStatus, String> confirmedIdPrism =
          Prism.of(
              s ->
                  s instanceof OrderStatus.Confirmed(var confirmationId)
                      ? Optional.of(confirmationId)
                      : Optional.empty(),
              OrderStatus.Confirmed::new);

      Traversal<List<String>, String> listTraversal = Traversals.forList();

      Kind<MaybeKind.Witness, OrderContext> result =
          ForState.withState(maybeMonad, MAYBE.just(ctx))
              .when(c -> c.orderId().startsWith("ORD"))
              .matchThen(statusLens, confirmedIdPrism, extractedIdLens)
              .traverse(tagsLens, listTraversal, tag -> MAYBE.just("[" + tag + "]"))
              .yield();

      assertThat(MAYBE.narrow(result))
          .isEqualTo(
              Maybe.just(
                  new OrderContext(
                      "ORD-1",
                      new OrderStatus.Confirmed("CONF-999"),
                      "CONF-999",
                      List.of("[rush]", "[fragile]"))));
    }

    @Test
    @DisplayName("should combine zoom with other operations")
    void zoomCombinedWithOtherOperations() {
      Customer customer =
          new Customer("Alice", new Address("123 Main", "Springfield", "62701"), 100);

      Kind<IdKind.Witness, Customer> result =
          ForState.withState(idMonad, Id.of(customer))
              .update(nameLens, "Alice Smith")
              .zoom(addressLens)
              .update(streetLens, "456 Oak Ave")
              .modify(zipLens, z -> z + "-5678")
              .fromThen(a -> Id.of(a.city().toUpperCase()), cityLens)
              .endZoom()
              .modify(loyaltyLens, lp -> lp + 50)
              .yield();

      Customer finalCustomer = IdKindHelper.ID.unwrap(result);
      assertThat(finalCustomer.name()).isEqualTo("Alice Smith");
      assertThat(finalCustomer.address().street()).isEqualTo("456 Oak Ave");
      assertThat(finalCustomer.address().city()).isEqualTo("SPRINGFIELD");
      assertThat(finalCustomer.address().zip()).isEqualTo("62701-5678");
      assertThat(finalCustomer.loyaltyPoints()).isEqualTo(150);
    }
  }

  // --- Optic-polymorphic zoom (Issue #506) ---

  @Nested
  @DisplayName("Optic-Polymorphic zoom() - FocusPath, Iso")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class OpticPolymorphicZoomTests {

    @Test
    @DisplayName("zoom(FocusPath) narrows state via the wrapped lens")
    void zoomFocusPathNarrowsViaLens() {
      Customer initial =
          new Customer("Alice", new Address("123 Main St", "Springfield", "62701"), 100);

      FocusPath<Customer, Address> addressPath = FocusPath.of(addressLens);

      Kind<IdKind.Witness, Customer> result =
          ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
              .zoom(addressPath)
              .update(streetLens, "456 Oak Ave")
              .endZoom()
              .yield();

      Customer finalCustomer = IdKindHelper.ID.unwrap(result);
      assertThat(finalCustomer.address().street()).isEqualTo("456 Oak Ave");
      assertThat(finalCustomer.address().city()).isEqualTo("Springfield");
    }

    @Test
    @DisplayName("zoom(FocusPath) is behaviourally equivalent to zoom(FocusPath.toLens())")
    void zoomFocusPathDelegatesToLens() {
      Customer initial =
          new Customer("Alice", new Address("123 Main St", "Springfield", "62701"), 100);
      FocusPath<Customer, Address> addressPath = FocusPath.of(addressLens);

      Kind<IdKind.Witness, Customer> viaPath =
          ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
              .zoom(addressPath)
              .update(cityLens, "Capital City")
              .endZoom()
              .yield();

      Kind<IdKind.Witness, Customer> viaLens =
          ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
              .zoom(addressPath.toLens())
              .update(cityLens, "Capital City")
              .endZoom()
              .yield();

      assertThat(IdKindHelper.ID.unwrap(viaPath)).isEqualTo(IdKindHelper.ID.unwrap(viaLens));
    }

    @Test
    @DisplayName("zoom(FocusPath) supports nested zoom and endZoom")
    void zoomFocusPathNestsAndEndZooms() {
      Customer initial =
          new Customer("Alice", new Address("123 Main St", "Springfield", "62701"), 100);
      FocusPath<Customer, Address> addressPath = FocusPath.of(addressLens);

      Kind<IdKind.Witness, Customer> result =
          ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
              .zoom(addressPath)
              .update(streetLens, "456 Oak Ave")
              .modify(zipLens, z -> z + "-5678")
              .endZoom()
              .modify(loyaltyLens, lp -> lp + 50)
              .yield();

      Customer finalCustomer = IdKindHelper.ID.unwrap(result);
      assertThat(finalCustomer.address().street()).isEqualTo("456 Oak Ave");
      assertThat(finalCustomer.address().zip()).isEqualTo("62701-5678");
      assertThat(finalCustomer.loyaltyPoints()).isEqualTo(150);
    }

    @Test
    @DisplayName("zoom(Iso) reconstructs the outer state on endZoom")
    void zoomIsoReconstructsOuterState() {
      Customer initial = new Customer("Alice", new Address("123", "City", "00000"), 0);

      // Iso between Customer and a "swapped" representation, just to exercise reverseGet.
      record CustomerView(int loyaltyPoints, String name, Address address) {}
      Iso<Customer, CustomerView> swap =
          Iso.of(
              c -> new CustomerView(c.loyaltyPoints(), c.name(), c.address()),
              v -> new Customer(v.name(), v.address(), v.loyaltyPoints()));

      Lens<CustomerView, Integer> viewLoyalty =
          Lens.of(
              CustomerView::loyaltyPoints, (v, lp) -> new CustomerView(lp, v.name(), v.address()));

      Kind<IdKind.Witness, Customer> result =
          ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
              .zoom(swap)
              .update(viewLoyalty, 999)
              .endZoom()
              .yield();

      assertThat(IdKindHelper.ID.unwrap(result).loyaltyPoints()).isEqualTo(999);
      assertThat(IdKindHelper.ID.unwrap(result).name()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("zoom(Iso) is behaviourally equivalent to zoom(iso.asLens())")
    void zoomIsoDelegatesToLens() {
      Counter initial = new Counter(0, "init");

      // Iso<Counter, Counter> that round-trips through a swap of (value, lastOperation).
      record Pair(String op, int value) {}
      Iso<Counter, Pair> iso =
          Iso.of(c -> new Pair(c.lastOperation(), c.value()), p -> new Counter(p.value(), p.op()));

      Lens<Pair, Integer> pairValue = Lens.of(Pair::value, (p, v) -> new Pair(p.op(), v));

      Kind<IdKind.Witness, Counter> viaIso =
          ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
              .zoom(iso)
              .update(pairValue, 42)
              .endZoom()
              .yield();

      Kind<IdKind.Witness, Counter> viaAsLens =
          ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
              .zoom(iso.asLens())
              .update(pairValue, 42)
              .endZoom()
              .yield();

      assertThat(IdKindHelper.ID.unwrap(viaIso)).isEqualTo(IdKindHelper.ID.unwrap(viaAsLens));
    }

    @Test
    @DisplayName("FilterableSteps.zoom(FocusPath) preserves filtering after endZoom")
    void filterableZoomFocusPathReturnsFilterableSteps() {
      Customer initial =
          new Customer("Alice", new Address("123 Main St", "Springfield", "62701"), 100);

      FocusPath<Customer, Address> addressPath = FocusPath.of(addressLens);

      Kind<MaybeKind.Witness, Customer> result =
          ForState.withState(maybeMonad, MAYBE.just(initial))
              .zoom(addressPath)
              .update(streetLens, "Oak Ave")
              .endZoom()
              .when(c -> c.address().street().equals("Oak Ave"))
              .yield();

      Maybe<Customer> outcome = MAYBE.narrow(result);
      assertThat(outcome.isJust()).isTrue();
    }

    @Test
    @DisplayName("FilterableSteps.zoom(Iso) preserves filtering after endZoom")
    void filterableZoomIsoReturnsFilterableSteps() {
      Counter initial = new Counter(0, "init");

      record Pair(String op, int value) {}
      Iso<Counter, Pair> iso =
          Iso.of(c -> new Pair(c.lastOperation(), c.value()), p -> new Counter(p.value(), p.op()));
      Lens<Pair, Integer> pairValue = Lens.of(Pair::value, (p, v) -> new Pair(p.op(), v));

      Kind<MaybeKind.Witness, Counter> result =
          ForState.withState(maybeMonad, MAYBE.just(initial))
              .zoom(iso)
              .update(pairValue, 99)
              .endZoom()
              .when(c -> c.value() > 0)
              .yield();

      assertThat(MAYBE.narrow(result).isJust()).isTrue();
    }

    @Test
    @DisplayName("FilterableSteps.zoom(FocusPath) when() guard after zoom can short-circuit")
    void filterableZoomFocusPathWhenCanShortCircuit() {
      Customer initial =
          new Customer("Alice", new Address("123 Main St", "Springfield", "62701"), 100);

      FocusPath<Customer, Address> addressPath = FocusPath.of(addressLens);

      Kind<MaybeKind.Witness, Customer> result =
          ForState.withState(maybeMonad, MAYBE.just(initial))
              .zoom(addressPath)
              .update(streetLens, "Oak Ave")
              .endZoom()
              .when(c -> c.address().city().equals("Nowhere")) // Springfield, fails predicate
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("zoom(FocusPath) throws on null path")
    void zoomFocusPathThrowsOnNull() {
      Customer initial =
          new Customer("Alice", new Address("123 Main St", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
                      .zoom((FocusPath<Customer, Address>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("path");
    }

    @Test
    @DisplayName("zoom(Iso) throws on null iso")
    void zoomIsoThrowsOnNull() {
      Counter initial = new Counter(0, "init");

      assertThatThrownBy(
              () ->
                  ForState.withState(idMonad, IdKindHelper.ID.widen(Id.of(initial)))
                      .zoom((Iso<Counter, Counter>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("iso");
    }

    @Test
    @DisplayName("FilterableSteps.zoom(FocusPath) throws on null path")
    void filterableZoomFocusPathThrowsOnNull() {
      Customer initial =
          new Customer("Alice", new Address("123 Main St", "Springfield", "62701"), 100);

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(initial))
                      .zoom((FocusPath<Customer, Address>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("path");
    }

    @Test
    @DisplayName("FilterableSteps.zoom(Iso) throws on null iso")
    void filterableZoomIsoThrowsOnNull() {
      Counter initial = new Counter(0, "init");

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(initial))
                      .zoom((Iso<Counter, Counter>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("iso");
    }
  }

  @Nested
  @DisplayName("FilterableSteps zoom(AffinePath) - Optional Sub-State")
  @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
  class FilterableAffineZoomTests {

    /** Customer record with an optional address (for affine zoom tests). */
    record OptionalAddressCustomer(String name, Optional<Address> address) {}

    @Test
    @DisplayName("zoom(AffinePath) updates inner state when target is present")
    void affineZoomUpdatesWhenPresent() {
      OptionalAddressCustomer initial =
          new OptionalAddressCustomer(
              "Alice", Optional.of(new Address("123 Main St", "Springfield", "62701")));

      Affine<OptionalAddressCustomer, Address> addressAffine =
          Affine.of(
              OptionalAddressCustomer::address,
              (c, a) -> new OptionalAddressCustomer(c.name(), Optional.of(a)));
      AffinePath<OptionalAddressCustomer, Address> addressPath = AffinePath.of(addressAffine);

      Kind<MaybeKind.Witness, OptionalAddressCustomer> result =
          ForState.withState(maybeMonad, MAYBE.just(initial))
              .zoom(addressPath)
              .update(streetLens, "Oak Ave")
              .endZoom()
              .yield();

      Maybe<OptionalAddressCustomer> outcome = MAYBE.narrow(result);
      assertThat(outcome.isJust()).isTrue();
      Address updated = outcome.orElse(initial).address().orElseThrow();
      assertThat(updated.street()).isEqualTo("Oak Ave");
      assertThat(updated.city()).isEqualTo("Springfield");
    }

    @Test
    @DisplayName("zoom(AffinePath) short-circuits to zero when target is absent")
    void affineZoomShortCircuitsWhenAbsent() {
      OptionalAddressCustomer initial = new OptionalAddressCustomer("Alice", Optional.empty());

      Affine<OptionalAddressCustomer, Address> addressAffine =
          Affine.of(
              OptionalAddressCustomer::address,
              (c, a) -> new OptionalAddressCustomer(c.name(), Optional.of(a)));
      AffinePath<OptionalAddressCustomer, Address> addressPath = AffinePath.of(addressAffine);

      Kind<MaybeKind.Witness, OptionalAddressCustomer> result =
          ForState.withState(maybeMonad, MAYBE.just(initial))
              .zoom(addressPath)
              .update(streetLens, "Oak Ave")
              .endZoom()
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("zoom(AffinePath) endZoom returns FilterableSteps so when() remains available")
    void affineZoomEndZoomPreservesFilterable() {
      OptionalAddressCustomer initial =
          new OptionalAddressCustomer(
              "Alice", Optional.of(new Address("123 Main St", "Springfield", "62701")));

      Affine<OptionalAddressCustomer, Address> addressAffine =
          Affine.of(
              OptionalAddressCustomer::address,
              (c, a) -> new OptionalAddressCustomer(c.name(), Optional.of(a)));
      AffinePath<OptionalAddressCustomer, Address> addressPath = AffinePath.of(addressAffine);

      Kind<MaybeKind.Witness, OptionalAddressCustomer> result =
          ForState.withState(maybeMonad, MAYBE.just(initial))
              .zoom(addressPath)
              .update(streetLens, "Oak Ave")
              .endZoom()
              .when(c -> c.address().isPresent()) // verify FilterableSteps still in scope
              .yield();

      assertThat(MAYBE.narrow(result).isJust()).isTrue();
    }

    @Test
    @DisplayName("zoom(AffinePath) propagates Nothing input unchanged")
    void affineZoomPropagatesNothingInput() {
      Affine<OptionalAddressCustomer, Address> addressAffine =
          Affine.of(
              OptionalAddressCustomer::address,
              (c, a) -> new OptionalAddressCustomer(c.name(), Optional.of(a)));
      AffinePath<OptionalAddressCustomer, Address> addressPath = AffinePath.of(addressAffine);

      Kind<MaybeKind.Witness, OptionalAddressCustomer> result =
          ForState.withState(maybeMonad, MAYBE.<OptionalAddressCustomer>nothing())
              .zoom(addressPath)
              .update(streetLens, "Oak Ave")
              .endZoom()
              .yield();

      assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("zoom(AffinePath) throws on null path")
    void affineZoomThrowsOnNull() {
      OptionalAddressCustomer initial =
          new OptionalAddressCustomer(
              "Alice", Optional.of(new Address("123 Main St", "Springfield", "62701")));

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(initial))
                      .zoom((AffinePath<OptionalAddressCustomer, Address>) null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("path");
    }

    @Test
    @DisplayName(
        "zoom(AffinePath) raises IllegalStateException if a non-deterministic Affine reports the"
            + " target absent inside the zoom block")
    void affineZoomRaisesIllegalStateOnInvariantViolation() {
      // Construct a deliberately non-deterministic Affine: returns Optional.of(...) on the
      // first call (so the FilterableSteps guard passes) but Optional.empty() on subsequent
      // calls (when the synthesised lens.get fires inside update/modify). Real Affine
      // implementations should never behave this way; the test exists to confirm the lens's
      // orElseThrow message fires with a clear diagnostic when the invariant is violated.
      AtomicInteger callCount = new AtomicInteger(0);
      Address present = new Address("123 Main St", "Springfield", "62701");
      Affine<OptionalAddressCustomer, Address> flakyAffine =
          Affine.of(
              _ -> callCount.getAndIncrement() == 0 ? Optional.of(present) : Optional.empty(),
              (c, a) -> new OptionalAddressCustomer(c.name(), Optional.of(a)));
      AffinePath<OptionalAddressCustomer, Address> flakyPath = AffinePath.of(flakyAffine);

      OptionalAddressCustomer initial = new OptionalAddressCustomer("Alice", Optional.of(present));

      assertThatThrownBy(
              () ->
                  ForState.withState(maybeMonad, MAYBE.just(initial))
                      .zoom(flakyPath)
                      .update(streetLens, "Oak Ave")
                      .endZoom()
                      .yield())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Affine target unexpectedly absent")
          .hasMessageContaining("non-deterministic Affine");
    }
  }
}
