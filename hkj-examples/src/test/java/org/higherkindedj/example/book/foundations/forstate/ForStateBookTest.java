// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.forstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.forstate.ForStateBook.BACKORDERED;
import static org.higherkindedj.example.book.foundations.forstate.ForStateBook.MAYBE_MONAD;
import static org.higherkindedj.example.book.foundations.forstate.ForStateBook.ORDER;
import static org.higherkindedj.example.book.foundations.forstate.ForStateBook.SHIPPED;
import static org.higherkindedj.example.book.foundations.forstate.ForStateBook.pay;
import static org.higherkindedj.example.book.foundations.forstate.ForStateBook.receipt;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.foroptics.ForOpticsBook;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.CustomerProfileFocus;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.EmailAddressLenses;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the ForState page makes about each step. */
@DisplayName("ForState: each step maps or flatMaps a record through the instance")
class ForStateBookTest {

  private static final Monad<IdKind.Witness> ID_MONAD = Instances.monad(id());
  private static final Monad<EitherKind.Witness<String>> EITHER_MONAD = Instances.monad(either());
  private static final Lens<Order, EmailAddress> EMAIL =
      OrderLenses.customer().andThen(CustomerLenses.email());

  private static Order run(ForState.Steps<IdKind.Witness, Order> steps) {
    return ID.unwrap(steps.yield());
  }

  @Nested
  @DisplayName("The page's two examples")
  class Examples {

    @Test
    @DisplayName("pay marks a new order paid, its email in lower case and its lines unchanged")
    void payMarksANewOrderPaid() {
      Maybe<Order> paid = pay(ORDER);

      assertThat(paid.isJust()).isTrue();
      Order order = paid.get();
      assertThat(order.status()).isEqualTo(OrderStatus.PAID);
      assertThat(order.customer().email().value()).isEqualTo("ada@example.com");
      assertThat(order.lines()).isEqualTo(ORDER.lines());
    }

    @Test
    @DisplayName("pay gives Nothing for a shipped order, at when, and for a line out of stock")
    void payRefusesShippedAndBackordered() {
      assertThat(pay(SHIPPED)).isEqualTo(Maybe.nothing());
      assertThat(pay(BACKORDERED)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("receipt takes the discount and marks the order paid inside the Checkout")
    void receiptTakesTheDiscount() {
      Either<String, String> sent =
          EITHER.narrow(
              receipt(
                  EITHER_MONAD,
                  EITHER.widen(Either.right(ORDER)),
                  customer -> EITHER.widen(Either.right(new EmailAddress("ada@example.com"))),
                  new BigDecimal("5.00")));

      assertThat(sent).isEqualTo(Either.right("PAID: 45.00 to ada@example.com"));
    }

    @Test
    @DisplayName("receipt gives the first Left, from a missing order or an unverified email")
    void receiptGivesTheFirstLeft() {
      Either<String, String> notFound =
          EITHER.narrow(
              receipt(
                  EITHER_MONAD,
                  EITHER.widen(Either.left("order not found")),
                  customer -> EITHER.widen(Either.right(customer.email())),
                  BigDecimal.ZERO));
      Either<String, String> unverified =
          EITHER.narrow(
              receipt(
                  EITHER_MONAD,
                  EITHER.widen(Either.right(ORDER)),
                  customer -> EITHER.widen(Either.left("email not verified")),
                  BigDecimal.ZERO));

      assertThat(notFound).isEqualTo(Either.left("order not found"));
      assertThat(unverified).isEqualTo(Either.left("email not verified"));
    }

    @Test
    @DisplayName("A Left from verify stops the chain before toState builds anything")
    void aLeftStopsTheChainBeforeToState() {
      List<String> built = new ArrayList<>();

      Either<String, Checkout> result =
          EITHER.narrow(
              For.from(EITHER_MONAD, EITHER.widen(Either.<String, Order>right(ORDER)))
                  .from(
                      order ->
                          EITHER.widen(Either.<String, EmailAddress>left("email not verified")))
                  .toState(
                      (order, email) -> {
                        built.add("toState ran");
                        return new Checkout(order, email, BigDecimal.ZERO);
                      })
                  .yield());

      assertThat(result).isEqualTo(Either.left("email not verified"));
      assertThat(built).isEmpty();
    }

    @Test
    @DisplayName(
        "Every For step, from one value to twelve, has toState, kept filterable after a MonadZero")
    void toStateAtEveryArity() throws ClassNotFoundException {
      List<Class<?>> monadic = new ArrayList<>(List.of(For.MonadicSteps1.class));
      List<Class<?>> filterable = new ArrayList<>(List.of(For.FilterableSteps1.class));
      for (int arity = 2; arity <= 12; arity++) {
        monadic.add(Class.forName("org.higherkindedj.hkt.expression.MonadicSteps" + arity));
        filterable.add(Class.forName("org.higherkindedj.hkt.expression.FilterableSteps" + arity));
      }

      for (Class<?> steps : monadic) {
        assertThat(toStateReturns(steps))
            .as(steps.getSimpleName())
            .containsOnly(ForState.Steps.class);
      }
      for (Class<?> steps : filterable) {
        assertThat(toStateReturns(steps))
            .as(steps.getSimpleName())
            .containsOnly(ForState.FilterableSteps.class);
      }
    }

    private static List<Class<?>> toStateReturns(Class<?> steps) {
      return Arrays.stream(steps.getMethods())
          .filter(method -> method.getName().equals("toState"))
          .<Class<?>>map(Method::getReturnType)
          .toList();
    }
  }

  @Nested
  @DisplayName("When the steps run")
  class WhenStepsRun {

    @Test
    @DisplayName("Over Maybe, a step's function runs when the step is added, before yield")
    void eagerInstanceRunsAtOnce() {
      List<String> calls = new ArrayList<>();

      ForState.withState(MAYBE_MONAD, MAYBE.just(ORDER))
          .from(
              order -> {
                calls.add("from ran");
                return MAYBE.just(order.id());
              });

      assertThat(calls).containsExactly("from ran");
    }

    @Test
    @DisplayName("Over IO, nothing runs until the IO is run")
    void deferredInstanceWaits() {
      List<String> calls = new ArrayList<>();
      Monad<IOKind.Witness> ioMonad = Instances.monad(io());

      Kind<IOKind.Witness, Order> program =
          ForState.withState(ioMonad, IO_OP.widen(IO.delay(() -> ORDER)))
              .from(
                  order -> {
                    calls.add("from ran");
                    return IO_OP.widen(IO.delay(order::id));
                  })
              .update(OrderLenses.status(), OrderStatus.PAID)
              .yield();

      assertThat(calls).isEmpty();
      assertThat(IO_OP.narrow(program).unsafeRunSync().status()).isEqualTo(OrderStatus.PAID);
      assertThat(calls).containsExactly("from ran");
    }

    @Test
    @DisplayName("On an Effect Path, map with a lens changes the record, as update does")
    void onAPathMapWithALens() {
      Either<String, Order> onPath =
          Path.<String, Order>right(ORDER)
              .map(order -> OrderLenses.status().set(OrderStatus.PAID, order))
              .run();
      Either<String, Order> viaForState =
          EITHER.narrow(
              ForState.withState(EITHER_MONAD, EITHER.widen(Either.<String, Order>right(ORDER)))
                  .update(OrderLenses.status(), OrderStatus.PAID)
                  .yield());

      assertThat(onPath).isEqualTo(viaForState);
    }
  }

  @Nested
  @DisplayName("Each step in the table")
  class Steps {

    @Test
    @DisplayName("update sets a field, and modify runs a function on it")
    void updateAndModify() {
      Order result =
          run(
              ForState.withState(ID_MONAD, Id.of(ORDER))
                  .update(OrderLenses.status(), OrderStatus.PAID)
                  .modify(
                      OrderLenses.customer().andThen(CustomerLenses.name()), String::toUpperCase));

      assertThat(result.status()).isEqualTo(OrderStatus.PAID);
      assertThat(result.customer().name()).isEqualTo("ADA");
    }

    @Test
    @DisplayName(
        "from keeps the state and drops the value; fromThen stores the value through a lens")
    void fromAndFromThen() {
      Order kept = run(ForState.withState(ID_MONAD, Id.of(ORDER)).from(order -> Id.of("ignored")));
      Order stored =
          run(
              ForState.withState(ID_MONAD, Id.of(ORDER))
                  .fromThen(order -> Id.of(OrderStatus.SHIPPED), OrderLenses.status()));

      assertThat(kept).isEqualTo(ORDER);
      assertThat(stored.status()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("when and matchThen exist only after withState is given a MonadZero")
    void guardsNeedAMonadZero() {
      List<String> plain =
          Arrays.stream(ForState.Steps.class.getMethods()).map(Method::getName).toList();
      List<String> filterable =
          Arrays.stream(ForState.FilterableSteps.class.getMethods()).map(Method::getName).toList();

      assertThat(plain).doesNotContain("when", "matchThen");
      assertThat(filterable).contains("when", "matchThen");
    }

    @Test
    @DisplayName(
        "matchThen stores what the prism matches, and gives the empty value when it misses")
    void matchThenShortCircuits() {
      record Delivery(ConsignmentState state, ConsignmentState.Dispatched last) {}
      Lens<Delivery, ConsignmentState> state =
          Lens.of(Delivery::state, (d, s) -> new Delivery(s, d.last()));
      Lens<Delivery, ConsignmentState.Dispatched> last =
          Lens.of(Delivery::last, (d, l) -> new Delivery(d.state(), l));
      ConsignmentState.Dispatched earlier = new ConsignmentState.Dispatched(Instant.EPOCH);
      ConsignmentState.Dispatched now =
          new ConsignmentState.Dispatched(Instant.parse("2026-10-02T08:00:00Z"));

      Maybe<Delivery> matched =
          MAYBE.narrow(
              ForState.withState(MAYBE_MONAD, MAYBE.just(new Delivery(now, earlier)))
                  .matchThen(state, ConsignmentStatePrisms.dispatched(), last)
                  .yield());
      Maybe<Delivery> missed =
          MAYBE.narrow(
              ForState.withState(
                      MAYBE_MONAD,
                      MAYBE.just(new Delivery(new ConsignmentState.Pending(), earlier)))
                  .matchThen(state, ConsignmentStatePrisms.dispatched(), last)
                  .yield());

      assertThat(matched).isEqualTo(Maybe.just(new Delivery(now, now)));
      assertThat(missed).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("traverse runs an effect on every element of a field, and one Nothing ends it")
    void traverseRunsOnEveryElement() {
      Maybe<Order> backordered =
          MAYBE.narrow(
              ForState.withState(MAYBE_MONAD, MAYBE.just(BACKORDERED))
                  .traverse(OrderLenses.lines(), Traversals.forList(), ForStateBook::inStock)
                  .yield());

      assertThat(backordered).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("traverseOver traverses the state itself")
    void traverseOverTheState() {
      Traversal<List<LineItem>, LineItem> each = Traversals.forList();

      Maybe<List<LineItem>> inStock =
          MAYBE.narrow(
              ForState.withState(MAYBE_MONAD, MAYBE.just(ORDER.lines()))
                  .traverseOver(each, ForStateBook::inStock)
                  .yield());
      Maybe<List<LineItem>> outOfStock =
          MAYBE.narrow(
              ForState.withState(MAYBE_MONAD, MAYBE.just(BACKORDERED.lines()))
                  .traverseOver(each, ForStateBook::inStock)
                  .yield());

      assertThat(inStock).isEqualTo(Maybe.just(ORDER.lines()));
      assertThat(outOfStock).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("modifyThrough is Traversals.modify, and its lens form composes the lens first")
    void modifyThroughIsTraversalsModify() {
      Order doubled =
          run(
              ForState.withState(ID_MONAD, Id.of(ORDER))
                  .modifyThrough(OrderTraversals.lines(), LineItemLenses.quantity(), q -> q * 2));

      assertThat(doubled)
          .isEqualTo(
              Traversals.modify(
                  OrderTraversals.lines().andThen(LineItemLenses.quantity()), q -> q * 2, ORDER));
    }

    @Test
    @DisplayName("modifyVia is modify through the lens and the Iso composed")
    void modifyViaIsModifyThroughTheComposition() {
      Order via =
          run(
              ForState.withState(ID_MONAD, Id.of(ORDER))
                  .modifyVia(EMAIL, ForOpticsBook.EMAIL_TEXT, String::toLowerCase));
      Order composed =
          run(
              ForState.withState(ID_MONAD, Id.of(ORDER))
                  .modify(EMAIL.andThen(ForOpticsBook.EMAIL_TEXT), String::toLowerCase));

      assertThat(via).isEqualTo(composed);
      assertThat(via.customer().email().value()).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("updateVia stores the Iso's reverseGet of the value")
    void updateViaStoresTheReverseGet() {
      Order result =
          run(
              ForState.withState(ID_MONAD, Id.of(ORDER))
                  .updateVia(EMAIL, ForOpticsBook.EMAIL_TEXT, "ops@example.com"));

      assertThat(result.customer().email()).isEqualTo(new EmailAddress("ops@example.com"));
    }

    @Test
    @DisplayName("zoom with a FocusPath does what zoom with its lens does")
    void zoomWithAFocusPath() {
      Order viaPath =
          run(
              ForState.withState(ID_MONAD, Id.of(ORDER))
                  .zoom(OrderFocus.customer())
                  .modify(CustomerLenses.name(), String::toUpperCase)
                  .endZoom());
      Order viaLens =
          run(
              ForState.withState(ID_MONAD, Id.of(ORDER))
                  .zoom(OrderLenses.customer())
                  .modify(CustomerLenses.name(), String::toUpperCase)
                  .endZoom());

      assertThat(viaPath).isEqualTo(viaLens);
      assertThat(viaPath.customer().name()).isEqualTo("ADA");
    }

    @Test
    @DisplayName("zoom with an Iso works on the whole state in its other form")
    void zoomWithAnIso() {
      Lens<String, String> whole = Lens.of(Function.identity(), (old, text) -> text);

      EmailAddress lowered =
          ID.unwrap(
              ForState.withState(ID_MONAD, Id.of(new EmailAddress("Ada@Example.com")))
                  .zoom(ForOpticsBook.EMAIL_TEXT)
                  .modify(whole, String::toLowerCase)
                  .endZoom()
                  .yield());

      assertThat(lowered).isEqualTo(new EmailAddress("ada@example.com"));
    }

    @Test
    @DisplayName("zoom with an AffinePath gives the empty value when the part is absent")
    void zoomWithAnAffinePath() {
      CustomerProfile without = new CustomerProfile("Ada", Optional.empty(), Optional.empty());
      CustomerProfile with =
          new CustomerProfile(
              "Ada", Optional.empty(), Optional.of(new EmailAddress("Ada@Work.example")));

      Maybe<CustomerProfile> absent =
          MAYBE.narrow(
              ForState.withState(MAYBE_MONAD, MAYBE.just(without))
                  .zoom(CustomerProfileFocus.altEmail())
                  .modify(EmailAddressLenses.value(), String::toLowerCase)
                  .endZoom()
                  .yield());
      Maybe<CustomerProfile> present =
          MAYBE.narrow(
              ForState.withState(MAYBE_MONAD, MAYBE.just(with))
                  .zoom(CustomerProfileFocus.altEmail())
                  .modify(EmailAddressLenses.value(), String::toLowerCase)
                  .endZoom()
                  .yield());

      assertThat(absent).isEqualTo(Maybe.nothing());
      assertThat(present.get().altEmail()).contains(new EmailAddress("ada@work.example"));
    }

    @Test
    @DisplayName("yield with a function maps the final state")
    void yieldMapsTheState() {
      Id<OrderStatus> status =
          ID.narrow(ForState.withState(ID_MONAD, Id.of(ORDER)).yield(Order::status));

      assertThat(status.value()).isEqualTo(OrderStatus.NEW);
    }
  }
}
