// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 01: ForState Basics - State-Threaded Comprehensions
 *
 * <p>ForState provides a fluent builder for monadic workflows where state is automatically threaded
 * through each step, and updates are expressed through Lens optics.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li>State threading: carry state through each computation step
 *   <li>Lens-based updates: update/modify state fields using lenses
 *   <li>Guards (when): short-circuit workflows based on predicates
 *   <li>Pattern matching (matchThen): extract values from sum types via Prisms
 *   <li>Traversals (traverse): bulk operations over collection fields
 *   <li>Zooming (zoom/endZoom): narrow state scope to sub-parts
 *   <li>Bridge (toState): transition from For comprehensions to ForState mid-chain
 * </ul>
 *
 * <p>By the end of this tutorial you will understand how ForState enables declarative, type-safe
 * workflow definitions.
 */
public class Tutorial01_ForStateBasics {

  /** Helper for incomplete exercises. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // --- Shared Domain Model ---

  record OrderContext(String orderId, boolean validated, boolean processed, String confirmationId) {
    static OrderContext start(String orderId) {
      return new OrderContext(orderId, false, false, null);
    }
  }

  static final Lens<OrderContext, Boolean> validatedLens =
      Lens.of(
          OrderContext::validated,
          (ctx, v) -> new OrderContext(ctx.orderId(), v, ctx.processed(), ctx.confirmationId()));

  static final Lens<OrderContext, Boolean> processedLens =
      Lens.of(
          OrderContext::processed,
          (ctx, p) -> new OrderContext(ctx.orderId(), ctx.validated(), p, ctx.confirmationId()));

  static final Lens<OrderContext, String> confirmationIdLens =
      Lens.of(
          OrderContext::confirmationId,
          (ctx, id) -> new OrderContext(ctx.orderId(), ctx.validated(), ctx.processed(), id));

  // =========================================================================
  // Exercise 1: Basic state threading with update()
  // =========================================================================

  /**
   * Exercise 1: Use ForState.withState to create a workflow that sets the validated and processed
   * fields to true, then sets the confirmationId to "CONF-001".
   *
   * <p>Hint: Use .update(lens, value) for each field, then .yield()
   */
  @Test
  void exercise1_basicStateUpdate() {
    IdMonad idMonad = IdMonad.instance();
    OrderContext initial = OrderContext.start("ORD-100");

    // TODO: Build a ForState workflow using idMonad and Id.of(initial)
    //   that sets validated=true, processed=true, confirmationId="CONF-001"
    Kind<IdKind.Witness, OrderContext> result =
        ForState.withState(idMonad, Id.of(initial))
            .update(validatedLens, true)
            .update(processedLens, true)
            .update(confirmationIdLens, "CONF-001")
            .yield();

    OrderContext ctx = IdKindHelper.ID.unwrap(result);
    assertThat(ctx.validated()).isTrue();
    assertThat(ctx.processed()).isTrue();
    assertThat(ctx.confirmationId()).isEqualTo("CONF-001");
    assertThat(ctx.orderId()).isEqualTo("ORD-100");
  }

  // =========================================================================
  // Exercise 2: Effectful operations with fromThen()
  // =========================================================================

  /**
   * Exercise 2: Use fromThen() to perform an effectful computation that generates a confirmation ID
   * by prefixing the order ID with "CONF-".
   *
   * <p>Hint: .fromThen(ctx -> Id.of("CONF-" + ctx.orderId()), confirmationIdLens)
   */
  @Test
  void exercise2_fromThenEffectfulUpdate() {
    IdMonad idMonad = IdMonad.instance();
    OrderContext initial = OrderContext.start("ORD-200");

    // TODO: Use fromThen to generate confirmationId = "CONF-ORD-200"
    Kind<IdKind.Witness, OrderContext> result =
        ForState.withState(idMonad, Id.of(initial))
            .fromThen(ctx -> Id.of("CONF-" + ctx.orderId()), confirmationIdLens)
            .yield();

    OrderContext ctx = IdKindHelper.ID.unwrap(result);
    assertThat(ctx.confirmationId()).isEqualTo("CONF-ORD-200");
  }

  // =========================================================================
  // Exercise 3: modify() to transform a field
  // =========================================================================

  /**
   * Exercise 3: Use modify() to toggle the validated field from false to true.
   *
   * <p>Hint: .modify(validatedLens, v -> !v)
   */
  @Test
  void exercise3_modifyField() {
    IdMonad idMonad = IdMonad.instance();
    OrderContext initial = OrderContext.start("ORD-300");

    // TODO: Use modify to toggle validated from false to true
    Kind<IdKind.Witness, OrderContext> result =
        ForState.withState(idMonad, Id.of(initial)).modify(validatedLens, v -> !v).yield();

    OrderContext ctx = IdKindHelper.ID.unwrap(result);
    assertThat(ctx.validated()).isTrue();
  }

  // =========================================================================
  // Exercise 4: Guards with when() and MonadZero
  // =========================================================================

  /**
   * Exercise 4a: Use when() to guard a workflow. The workflow should only proceed if the order is
   * validated. Use Maybe as the monad context.
   *
   * <p>Hint: Use ForState.withState(maybeMonad, MAYBE.just(ctx)).when(predicate)
   */
  @Test
  void exercise4a_whenGuardPasses() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    OrderContext validCtx = new OrderContext("ORD-400", true, false, null);

    // TODO: Create a workflow with a when() guard on validated, then update processed=true
    Kind<MaybeKind.Witness, OrderContext> result =
        ForState.withState(maybeMonad, MAYBE.just(validCtx))
            .when(OrderContext::validated)
            .update(processedLens, true)
            .yield();

    assertThat(MAYBE.narrow(result))
        .isEqualTo(Maybe.just(new OrderContext("ORD-400", true, true, null)));
  }

  /** Exercise 4b: Same workflow but with an unvalidated order. Should produce Nothing. */
  @Test
  void exercise4b_whenGuardFails() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    OrderContext invalidCtx = OrderContext.start("ORD-401");

    // TODO: Same workflow as 4a but with invalidCtx (validated=false)
    Kind<MaybeKind.Witness, OrderContext> result =
        ForState.withState(maybeMonad, MAYBE.just(invalidCtx))
            .when(OrderContext::validated)
            .update(processedLens, true)
            .yield();

    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
  }

  // =========================================================================
  // Exercise 5: Pattern matching with matchThen()
  // =========================================================================

  sealed interface Status {
    record Active(String code) implements Status {}

    record Inactive(String reason) implements Status {}
  }

  record MatchContext(Status status, String extractedCode) {}

  static final Lens<MatchContext, Status> matchStatusLens =
      Lens.of(MatchContext::status, (ctx, s) -> new MatchContext(s, ctx.extractedCode()));

  static final Lens<MatchContext, String> extractedCodeLens =
      Lens.of(MatchContext::extractedCode, (ctx, c) -> new MatchContext(ctx.status(), c));

  static final Prism<Status, String> activeCodePrism =
      Prism.of(
          s -> s instanceof Status.Active a ? Optional.of(a.code()) : Optional.empty(),
          code -> new Status.Active(code));

  /**
   * Exercise 5a: Use matchThen(sourceLens, prism, targetLens) to extract the code from an Active
   * status and store it in extractedCode. Should succeed for Active.
   */
  @Test
  void exercise5a_matchThenSuccess() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    MatchContext ctx = new MatchContext(new Status.Active("A-001"), null);

    // TODO: Use matchThen to extract code from Active status into extractedCode
    Kind<MaybeKind.Witness, MatchContext> result =
        ForState.withState(maybeMonad, MAYBE.just(ctx))
            .matchThen(matchStatusLens, activeCodePrism, extractedCodeLens)
            .yield();

    assertThat(MAYBE.narrow(result))
        .isEqualTo(Maybe.just(new MatchContext(new Status.Active("A-001"), "A-001")));
  }

  /** Exercise 5b: Same but with Inactive status. Should produce Nothing. */
  @Test
  void exercise5b_matchThenFailure() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    MatchContext ctx = new MatchContext(new Status.Inactive("retired"), null);

    // TODO: Same matchThen as 5a but with Inactive status
    Kind<MaybeKind.Witness, MatchContext> result =
        ForState.withState(maybeMonad, MAYBE.just(ctx))
            .matchThen(matchStatusLens, activeCodePrism, extractedCodeLens)
            .yield();

    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
  }

  // =========================================================================
  // Exercise 6: Bulk operations with traverse()
  // =========================================================================

  record TaggedItem(String name, List<String> tags) {}

  static final Lens<TaggedItem, List<String>> itemTagsLens =
      Lens.of(TaggedItem::tags, (item, t) -> new TaggedItem(item.name(), t));

  /**
   * Exercise 6: Use traverse() to uppercase all tags in a TaggedItem.
   *
   * <p>Hint: .traverse(itemTagsLens, Traversals.forList(), tag -> Id.of(tag.toUpperCase()))
   */
  @Test
  void exercise6_traverseBulkOperation() {
    IdMonad idMonad = IdMonad.instance();
    TaggedItem item = new TaggedItem("Widget", List.of("sale", "new", "featured"));

    // TODO: Use traverse to uppercase all tags
    Kind<IdKind.Witness, TaggedItem> result =
        ForState.withState(idMonad, Id.of(item))
            .traverse(itemTagsLens, Traversals.forList(), tag -> Id.of(tag.toUpperCase()))
            .yield();

    TaggedItem finalItem = IdKindHelper.ID.unwrap(result);
    assertThat(finalItem.tags()).containsExactly("SALE", "NEW", "FEATURED");
    assertThat(finalItem.name()).isEqualTo("Widget");
  }

  // =========================================================================
  // Exercise 7: Zooming into sub-state
  // =========================================================================

  record Address(String street, String city, String zip) {}

  record Customer(String name, Address address) {}

  static final Lens<Customer, Address> customerAddressLens =
      Lens.of(Customer::address, (c, a) -> new Customer(c.name(), a));

  static final Lens<Customer, String> customerNameLens =
      Lens.of(Customer::name, (c, n) -> new Customer(n, c.address()));

  static final Lens<Address, String> streetLens =
      Lens.of(Address::street, (a, s) -> new Address(s, a.city(), a.zip()));

  static final Lens<Address, String> cityLens =
      Lens.of(Address::city, (a, c) -> new Address(a.street(), c, a.zip()));

  /**
   * Exercise 7: Use zoom(addressLens) to enter the Address scope, update the street and city, then
   * endZoom() to return to the Customer scope and update the name.
   */
  @Test
  void exercise7_zoomIntoSubState() {
    IdMonad idMonad = IdMonad.instance();
    Customer customer = new Customer("Alice", new Address("123 Main", "Springfield", "62701"));

    // TODO: Use zoom to update address fields, then endZoom and update name
    Kind<IdKind.Witness, Customer> result =
        ForState.withState(idMonad, Id.of(customer))
            .zoom(customerAddressLens)
            .update(streetLens, "456 Oak")
            .update(cityLens, "Shelbyville")
            .endZoom()
            .update(customerNameLens, "Bob")
            .yield();

    Customer finalCustomer = IdKindHelper.ID.unwrap(result);
    assertThat(finalCustomer.name()).isEqualTo("Bob");
    assertThat(finalCustomer.address().street()).isEqualTo("456 Oak");
    assertThat(finalCustomer.address().city()).isEqualTo("Shelbyville");
    assertThat(finalCustomer.address().zip()).isEqualTo("62701"); // unchanged
  }

  // =========================================================================
  // Exercise 8: Combined workflow
  // =========================================================================

  /**
   * Exercise 8: Build a complete workflow that:
   *
   * <ol>
   *   <li>Starts with an unvalidated order
   *   <li>Sets validated=true
   *   <li>Guards that validated is true (using when)
   *   <li>Uses fromThen to generate a confirmation ID
   *   <li>Yields a summary string using yield(projection)
   * </ol>
   */
  @Test
  void exercise8_combinedWorkflow() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    OrderContext initial = OrderContext.start("ORD-800");

    // TODO: Build the combined workflow described above
    Kind<MaybeKind.Witness, String> result =
        ForState.withState(maybeMonad, MAYBE.just(initial))
            .update(validatedLens, true)
            .when(OrderContext::validated)
            .fromThen(ctx -> MAYBE.just("CONF-" + ctx.orderId()), confirmationIdLens)
            .yield(ctx -> ctx.orderId() + ":" + ctx.confirmationId());

    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("ORD-800:CONF-ORD-800"));
  }

  // =========================================================================
  // Exercise 9: Bridging from For to ForState with toState()
  // =========================================================================

  record Dashboard(String user, int count, boolean ready) {}

  static final Lens<Dashboard, Boolean> dashboardReadyLens =
      Lens.of(Dashboard::ready, (d, v) -> new Dashboard(d.user(), d.count(), v));

  static final Lens<Dashboard, Integer> dashboardCountLens =
      Lens.of(Dashboard::count, (d, v) -> new Dashboard(d.user(), v, d.ready()));

  /**
   * Exercise 9a: Use For.from() to get a name, then toState() to construct a Dashboard record, then
   * use lens operations to update it.
   *
   * <p>The workflow should: start with "Alice", construct Dashboard("Alice", 0, false), set
   * ready=true, multiply count by 10 (still 0), and yield the Dashboard.
   *
   * <p>Hint: For.from(idMonad, Id.of("Alice")).toState(name -> new Dashboard(...)).update(...)
   */
  @Test
  void exercise9a_basicToStateBridge() {
    IdMonad idMonad = IdMonad.instance();

    // TODO: Use For.from() then .toState() to bridge into ForState
    Kind<IdKind.Witness, Dashboard> result =
        For.from(idMonad, Id.of("Alice"))
            .toState(name -> new Dashboard(name, 0, false))
            .update(dashboardReadyLens, true)
            .yield();

    Dashboard dashboard = IdKindHelper.ID.unwrap(result);
    assertThat(dashboard.user()).isEqualTo("Alice");
    assertThat(dashboard.ready()).isTrue();
  }

  /**
   * Exercise 9b: Accumulate two values with For (a name and a count), then bridge to ForState using
   * the spread-style toState constructor.
   *
   * <p>Hint: .toState((name, count) -> new Dashboard(name, count, false))
   */
  @Test
  void exercise9b_multiValueToStateBridge() {
    IdMonad idMonad = IdMonad.instance();

    // TODO: Accumulate name and count with For, then bridge to ForState
    Kind<IdKind.Witness, Dashboard> result =
        For.from(idMonad, Id.of("Bob"))
            .from(name -> Id.of(name.length()))
            .toState((name, count) -> new Dashboard(name, count, false))
            .modify(dashboardCountLens, c -> c * 10)
            .update(dashboardReadyLens, true)
            .yield();

    Dashboard dashboard = IdKindHelper.ID.unwrap(result);
    assertThat(dashboard.user()).isEqualTo("Bob");
    assertThat(dashboard.count()).isEqualTo(30); // 3 * 10
    assertThat(dashboard.ready()).isTrue();
  }

  /**
   * Exercise 9c: Use toState() with a MonadZero (Maybe) to show that filtering is preserved across
   * the bridge. The workflow should filter on the state after the bridge.
   *
   * <p>Hint: For.from(maybeMonad, MAYBE.just(...)).toState(...).when(d -> d.count() > 5)
   */
  @Test
  void exercise9c_filterableToStateBridge() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;

    // TODO: Bridge from For into ForState with Maybe, then apply a guard
    Kind<MaybeKind.Witness, Dashboard> passResult =
        For.from(maybeMonad, MAYBE.just("Grace"))
            .from(name -> MAYBE.just(8))
            .toState((name, count) -> new Dashboard(name, count, false))
            .when(d -> d.count() > 5) // guard passes
            .update(dashboardReadyLens, true)
            .yield();

    Kind<MaybeKind.Witness, Dashboard> failResult =
        For.from(maybeMonad, MAYBE.just("Hank"))
            .from(name -> MAYBE.just(2))
            .toState((name, count) -> new Dashboard(name, count, false))
            .when(d -> d.count() > 5) // guard fails
            .yield();

    Maybe<Dashboard> pass = MAYBE.narrow(passResult);
    assertThat(pass.isJust()).isTrue();
    assertThat(pass.get().user()).isEqualTo("Grace");
    assertThat(pass.get().ready()).isTrue();

    assertThat(MAYBE.narrow(failResult).isNothing()).isTrue();
  }
}
