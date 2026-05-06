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
 * Solution for Tutorial01 ForStateBasics — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
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

  /**
   * Why this is idiomatic: chained {@code update(lens, value)} calls read top-to-bottom in the
   * order the fields change. {@code ForState} threads the {@code OrderContext} through each step;
   * you never name the intermediate values.
   *
   * <p>Alternative: a series of {@code lens.set(value, ctx)} calls bound to local variables.
   * Equivalent runtime; the {@code ForState} comprehension hides the plumbing and pairs more
   * naturally with effectful steps later in the same pipeline.
   *
   * <p>Common wrong attempt: rebuild {@code OrderContext} by hand at the end with the new values
   * inlined. The compiler accepts the wider record now, but the next field added to {@code
   * OrderContext} silently drops back to its default; the lenses keep every field accounted for.
   */
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

  /**
   * Why this is idiomatic: {@code fromThen(producer, lens)} runs an effectful producer over the
   * current state and writes the result through the supplied lens. The state is read once, the
   * effect runs once, the lens writes once — no shadowed variables.
   *
   * <p>Alternative: read the field, run the producer, then call {@code lens.set} on the unwrapped
   * value. Same outcome; you have to remember to widen back into the monad afterwards, which is
   * exactly what {@code fromThen} encapsulates.
   *
   * <p>Common wrong attempt: ignore the producer's effect channel and call it as a plain {@code
   * Function}. Subsequent steps in {@code Maybe} or {@code Either} would lose the ability to
   * short-circuit; let the comprehension thread the effect for you.
   */
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

  /**
   * Why this is idiomatic: {@code modify(lens, fn)} captures "transform this field by this pure
   * function" in one call. The lens does the read-and-rebuild; the function only sees the leaf
   * value.
   *
   * <p>Alternative: {@code update(lens, !ctx.validated())}. Same answer for a unary flip; {@code
   * modify} composes more cleanly when the transform depends on the current value or grows past a
   * single negation.
   *
   * <p>Common wrong attempt: read with the lens, transform, then write with the same lens — three
   * statements where one would do. {@code modify} is the named "atomic field transform" in the
   * comprehension vocabulary.
   */
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

  /**
   * Why this is idiomatic: {@code when(predicate)} aborts the comprehension into the monad's empty
   * branch when the predicate fails. With {@code MaybeMonad} the rest of the chain is skipped and
   * the result is {@code Just} only if every guard passes.
   *
   * <p>Alternative: a hand-written {@code if/else} that emits {@code Maybe.nothing()} on the
   * failing branch. Equivalent; {@code when} keeps the guard inline so the chain still reads
   * top-to-bottom.
   *
   * <p>Common wrong attempt: use {@code IdMonad} with {@code when}. With no failure channel the
   * guard has nowhere to escape to; either lift to {@code Maybe} (or another monad with a failure
   * case) or fold the predicate into a regular {@code if} block.
   */
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

  /**
   * Why this is idiomatic: the symmetric counterpart to 4a — the guard fails and the entire chain
   * collapses to {@code Maybe.nothing()}. The downstream {@code update} never runs; the
   * comprehension is total either way.
   *
   * <p>Alternative: write the guard as the first {@code if} in a procedural helper and return
   * {@code Maybe.nothing()} explicitly. Same answer; the comprehension keeps a uniform shape for
   * both the pass and fail cases.
   *
   * <p>Common wrong attempt: assume the {@code update} still applies to the original state. It does
   * not — once {@code when} short-circuits, no later step runs. Treat the comprehension as one
   * expression with one outcome.
   */
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

  /**
   * Why this is idiomatic: {@code matchThen(focusLens, prism, targetLens)} fuses three familiar
   * pieces — focus on a sub-state, run a prism partially, then write the matched value through
   * another lens. When the prism matches, the comprehension keeps going.
   *
   * <p>Alternative: spell out the steps with {@code modify} and an inline {@code instanceof}.
   * Equivalent; the named combinator advertises the pattern-matching intent and stays in sync with
   * the rest of the comprehension's effect channel.
   *
   * <p>Common wrong attempt: try to chain the prism manually with {@code update} on the focused
   * field. Because the prism may fail, it has to participate in the monad's failure branch; {@code
   * matchThen} routes that failure through {@code Maybe.nothing()} automatically.
   */
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

  /**
   * Why this is idiomatic: when the prism fails to match, {@code matchThen} routes the entire
   * comprehension to {@code Maybe.nothing()}. No hand-written guard, no leaked partial state — the
   * failure semantics live in the monad.
   *
   * <p>Alternative: manually call {@code prism.getOptional} and {@code orElseThrow}. The code
   * compiles but the failure becomes an exception rather than a typed empty result; reach for
   * {@code matchThen} when you want the type system to carry the absence forward.
   *
   * <p>Common wrong attempt: assume the {@code extractedCode} field becomes {@code null} on a
   * non-match. It does not — the comprehension is empty, and the field's previous value is never
   * referenced.
   */
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

  /**
   * Why this is idiomatic: {@code traverse(lens, traversal, fn)} runs an effectful function against
   * every element a traversal exposes, then writes the rebuilt collection back through the lens.
   * One call covers focus + iteration + reassembly.
   *
   * <p>Alternative: read the list with the lens, {@code stream().map(...).toList()}, write it back.
   * Same outcome for {@code IdMonad}; the {@code traverse} form generalises to effectful element
   * transformations ({@code Maybe}, {@code Either}, {@code VTask}) that a plain {@code map} cannot
   * express.
   *
   * <p>Common wrong attempt: mutate the underlying list in place. {@code TaggedItem} is a record
   * holding an immutable {@code List.of(...)}; the mutation throws at runtime, and even if it
   * succeeded the lens-driven design would not see the change.
   */
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

  /**
   * Why this is idiomatic: {@code zoom(lens)} narrows the comprehension's state to a sub-record so
   * subsequent {@code update} calls speak in the inner type's vocabulary; {@code endZoom()} pops
   * back out. Inside the zoom, lenses on {@code Address} feel top-level.
   *
   * <p>Alternative: prefix every step with {@code customerAddressLens.andThen(...)}. Same answer;
   * loses the visual scoping that says "these three updates all belong to the address".
   *
   * <p>Common wrong attempt: forget {@code endZoom()} and try to update {@code customerName} inside
   * the zoom. The compiler complains that {@code customerNameLens} expects a {@code Customer}, not
   * an {@code Address} — pop the zoom before changing scope.
   */
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

  /**
   * Why this is idiomatic: an unconditional update, a {@code when} guard, an effectful {@code
   * fromThen}, and a final projection — all in one comprehension. The shape mirrors a real
   * workflow: validate, gate on validation, fetch a derived value, return a summary.
   *
   * <p>Alternative: split each phase into a private method that returns a fresh {@code
   * Maybe<OrderContext>}. Tidier in production code; the comprehension is the smaller surface for a
   * tutorial that wants every step visible.
   *
   * <p>Common wrong attempt: forget the {@code yield(projection)} and call {@code yield()} with no
   * argument. The result type would be {@code Maybe<OrderContext>}; passing the projection lambda
   * rephrases the answer as {@code Maybe<String>}.
   */
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
