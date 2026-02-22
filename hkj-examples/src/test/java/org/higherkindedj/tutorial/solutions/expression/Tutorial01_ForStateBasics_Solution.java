// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
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
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Solutions for Tutorial 01: ForState Basics.
 *
 * <p>Each exercise solution demonstrates the correct usage of ForState operations.
 */
public class Tutorial01_ForStateBasics_Solution {

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

  // --- Exercise 1 Solution ---

  @Test
  void exercise1_basicStateUpdate() {
    IdMonad idMonad = IdMonad.instance();
    OrderContext initial = OrderContext.start("ORD-100");

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

  // --- Exercise 2 Solution ---

  @Test
  void exercise2_fromThenEffectfulUpdate() {
    IdMonad idMonad = IdMonad.instance();
    OrderContext initial = OrderContext.start("ORD-200");

    Kind<IdKind.Witness, OrderContext> result =
        ForState.withState(idMonad, Id.of(initial))
            .fromThen(ctx -> Id.of("CONF-" + ctx.orderId()), confirmationIdLens)
            .yield();

    OrderContext ctx = IdKindHelper.ID.unwrap(result);
    assertThat(ctx.confirmationId()).isEqualTo("CONF-ORD-200");
  }

  // --- Exercise 3 Solution ---

  @Test
  void exercise3_modifyField() {
    IdMonad idMonad = IdMonad.instance();
    OrderContext initial = OrderContext.start("ORD-300");

    Kind<IdKind.Witness, OrderContext> result =
        ForState.withState(idMonad, Id.of(initial)).modify(validatedLens, v -> !v).yield();

    OrderContext ctx = IdKindHelper.ID.unwrap(result);
    assertThat(ctx.validated()).isTrue();
  }

  // --- Exercise 4a Solution ---

  @Test
  void exercise4a_whenGuardPasses() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    OrderContext validCtx = new OrderContext("ORD-400", true, false, null);

    Kind<MaybeKind.Witness, OrderContext> result =
        ForState.withState(maybeMonad, MAYBE.just(validCtx))
            .when(OrderContext::validated)
            .update(processedLens, true)
            .yield();

    assertThat(MAYBE.narrow(result))
        .isEqualTo(Maybe.just(new OrderContext("ORD-400", true, true, null)));
  }

  // --- Exercise 4b Solution ---

  @Test
  void exercise4b_whenGuardFails() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    OrderContext invalidCtx = OrderContext.start("ORD-401");

    Kind<MaybeKind.Witness, OrderContext> result =
        ForState.withState(maybeMonad, MAYBE.just(invalidCtx))
            .when(OrderContext::validated)
            .update(processedLens, true)
            .yield();

    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
  }

  // --- Exercise 5a Solution ---

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

  @Test
  void exercise5a_matchThenSuccess() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    MatchContext ctx = new MatchContext(new Status.Active("A-001"), null);

    Kind<MaybeKind.Witness, MatchContext> result =
        ForState.withState(maybeMonad, MAYBE.just(ctx))
            .matchThen(matchStatusLens, activeCodePrism, extractedCodeLens)
            .yield();

    assertThat(MAYBE.narrow(result))
        .isEqualTo(Maybe.just(new MatchContext(new Status.Active("A-001"), "A-001")));
  }

  // --- Exercise 5b Solution ---

  @Test
  void exercise5b_matchThenFailure() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    MatchContext ctx = new MatchContext(new Status.Inactive("retired"), null);

    Kind<MaybeKind.Witness, MatchContext> result =
        ForState.withState(maybeMonad, MAYBE.just(ctx))
            .matchThen(matchStatusLens, activeCodePrism, extractedCodeLens)
            .yield();

    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.nothing());
  }

  // --- Exercise 6 Solution ---

  record TaggedItem(String name, List<String> tags) {}

  static final Lens<TaggedItem, List<String>> itemTagsLens =
      Lens.of(TaggedItem::tags, (item, t) -> new TaggedItem(item.name(), t));

  @Test
  void exercise6_traverseBulkOperation() {
    IdMonad idMonad = IdMonad.instance();
    TaggedItem item = new TaggedItem("Widget", List.of("sale", "new", "featured"));

    Traversal<List<String>, String> listTraversal = Traversals.forList();

    Kind<IdKind.Witness, TaggedItem> result =
        ForState.withState(idMonad, Id.of(item))
            .traverse(itemTagsLens, listTraversal, tag -> Id.of(tag.toUpperCase()))
            .yield();

    TaggedItem finalItem = IdKindHelper.ID.unwrap(result);
    assertThat(finalItem.tags()).containsExactly("SALE", "NEW", "FEATURED");
    assertThat(finalItem.name()).isEqualTo("Widget");
  }

  // --- Exercise 7 Solution ---

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

  @Test
  void exercise7_zoomIntoSubState() {
    IdMonad idMonad = IdMonad.instance();
    Customer customer = new Customer("Alice", new Address("123 Main", "Springfield", "62701"));

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
    assertThat(finalCustomer.address().zip()).isEqualTo("62701");
  }

  // --- Exercise 8 Solution ---

  @Test
  void exercise8_combinedWorkflow() {
    MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
    OrderContext initial = OrderContext.start("ORD-800");

    Kind<MaybeKind.Witness, String> result =
        ForState.withState(maybeMonad, MAYBE.just(initial))
            .update(validatedLens, true)
            .when(OrderContext::validated)
            .fromThen(ctx -> MAYBE.just("CONF-" + ctx.orderId()), confirmationIdLens)
            .yield(ctx -> ctx.orderId() + ":" + ctx.confirmationId());

    assertThat(MAYBE.narrow(result)).isEqualTo(Maybe.just("ORD-800:CONF-ORD-800"));
  }
}
