// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.foroptics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.foroptics.ForOpticsBook.CONSIGNMENTS;
import static org.higherkindedj.example.book.foundations.foroptics.ForOpticsBook.EMAIL_TEXT;
import static org.higherkindedj.example.book.foundations.foroptics.ForOpticsBook.ORDER;
import static org.higherkindedj.example.book.foundations.foroptics.ForOpticsBook.halfPrice;
import static org.higherkindedj.example.book.foundations.foroptics.ForOpticsBook.tenPercentOff;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentFocus;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.expression.FilterableSteps2;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForIndexed;
import org.higherkindedj.hkt.expression.ForPath;
import org.higherkindedj.hkt.expression.ForTraversal;
import org.higherkindedj.hkt.expression.MonadicSteps2;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the For with Optics page makes about each optic step and builder. */
@DisplayName("For with Optics: each optic step is a shorthand, and ForTraversal's steps are pure")
class ForOpticsBookTest {

  private static final MonadZero<ListKind.Witness> LIST_MONAD = Instances.monadZero(list());
  private static final MonadZero<MaybeKind.Witness> MAYBE_MONAD = Instances.monadZero(maybe());
  private static final Monad<IdKind.Witness> ID_MONAD = Instances.monad(id());

  private static List<String> names(Class<?> type) {
    return Arrays.stream(type.getMethods()).map(Method::getName).toList();
  }

  @Nested
  @DisplayName("The optic steps on For")
  class OpticSteps {

    @Test
    @DisplayName("focus(lens) adds what let(lens::get) adds")
    void focusIsLetOfGet() {
      List<String> focused =
          LIST.narrow(
              For.from(LIST_MONAD, LIST.widen(CONSIGNMENTS))
                  .focus(ConsignmentLenses.state())
                  .yield((consignment, state) -> consignment.to().city() + " " + state));
      List<String> let =
          LIST.narrow(
              For.from(LIST_MONAD, LIST.widen(CONSIGNMENTS))
                  .let(ConsignmentLenses.state()::get)
                  .yield((consignment, state) -> consignment.to().city() + " " + state));

      assertThat(focused).isEqualTo(let).hasSize(3);
    }

    @Test
    @DisplayName("through(iso) adds iso.get, and never runs the way back")
    void throughUsesOnlyGet() {
      AtomicInteger reverseGets = new AtomicInteger();
      Iso<EmailAddress, String> counted =
          Iso.of(
              EMAIL_TEXT::get,
              text -> {
                reverseGets.incrementAndGet();
                return EMAIL_TEXT.reverseGet(text);
              });

      Maybe<String> through =
          MAYBE.narrow(
              For.from(MAYBE_MONAD, MAYBE.just(ORDER.customer().email()))
                  .through(counted)
                  .yield((email, text) -> text));
      Maybe<String> let =
          MAYBE.narrow(
              For.from(MAYBE_MONAD, MAYBE.just(ORDER.customer().email()))
                  .let(EMAIL_TEXT::get)
                  .yield((email, text) -> text));

      assertThat(through).isEqualTo(let).isEqualTo(Maybe.just("ada@example.com"));
      assertThat(reverseGets).hasValue(0);
    }

    @Test
    @DisplayName(
        "match(prism) keeps a value the prism matches and gives the empty value for one it misses")
    void matchKeepsOrEmpties() {
      Prism<ConsignmentState, ConsignmentState.Dispatched> dispatched =
          ConsignmentStatePrisms.dispatched();
      List<ConsignmentState> states = CONSIGNMENTS.stream().map(Consignment::state).toList();

      List<ConsignmentState.Dispatched> kept =
          LIST.narrow(
              For.from(LIST_MONAD, LIST.widen(states))
                  .match(dispatched)
                  .yield((state, sent) -> sent));
      Maybe<ConsignmentState.Dispatched> missed =
          MAYBE.narrow(
              For.from(MAYBE_MONAD, MAYBE.just(states.get(1)))
                  .match(dispatched)
                  .yield((state, sent) -> sent));

      assertThat(kept).containsExactly((ConsignmentState.Dispatched) states.get(0));
      assertThat(missed).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("The page's chain keeps only the dispatched consignment")
    void theChainKeepsTheDispatchedConsignment() {
      List<String> dispatched =
          LIST.narrow(
              For.from(LIST_MONAD, LIST.widen(CONSIGNMENTS))
                  .focus(ConsignmentLenses.state())
                  .match(t -> ConsignmentStatePrisms.dispatched().getOptional(t._2()))
                  .yield((consignment, state, sent) -> consignment.to().city()));

      assertThat(dispatched).containsExactly("Leeds");
    }

    @Test
    @DisplayName("match exists only on a chain started from a MonadZero")
    void matchNeedsAMonadZero() {
      assertThat(names(For.MonadicSteps1.class)).doesNotContain("match", "when");
      assertThat(names(For.FilterableSteps1.class)).contains("match", "when");
      assertThat(names(MonadicSteps2.class)).doesNotContain("match");
    }

    @Test
    @DisplayName("From the second value on, focus and match take a function, and through is gone")
    void laterStepsTakeFunctions() throws NoSuchMethodException {
      assertThat(MonadicSteps2.class.getMethod("focus", Function.class)).isNotNull();
      assertThat(FilterableSteps2.class.getMethod("match", Function.class)).isNotNull();
      assertThat(names(MonadicSteps2.class)).doesNotContain("through");
      assertThat(names(FilterableSteps2.class)).doesNotContain("through");
    }

    @Test
    @DisplayName("ForPath's focus takes a FocusPath and reads the same field")
    void forPathFocusReadsTheSameField() {
      Consignment first = CONSIGNMENTS.get(0);

      Maybe<ConsignmentState> onPath =
          ForPath.from(Path.just(first))
              .focus(ConsignmentFocus.state())
              .yield((consignment, state) -> state)
              .run();

      assertThat(onPath).isEqualTo(Maybe.just(first.state()));
    }
  }

  @Nested
  @DisplayName("ForTraversal and ForIndexed")
  class Builders {

    @Test
    @DisplayName("The discount reaches only the lines of two or more")
    void theDiscountReachesTheBulkLines() {
      Order discounted =
          ID.unwrap(
              ForTraversal.over(OrderTraversals.lines(), ORDER, ID_MONAD)
                  .filter(line -> line.quantity() >= 2)
                  .modify(LineItemLenses.price(), ForOpticsBook::tenPercentOff)
                  .run());

      assertThat(discounted.lines())
          .extracting(LineItem::price)
          .containsExactly(new BigDecimal("40.00"), new BigDecimal("2.25"));
    }

    @Test
    @DisplayName("Plain optics give the same order: a filtered traversal and Traversals.modify")
    void plainOpticsGiveTheSameOrder() {
      Order viaBuilder =
          ID.unwrap(
              ForTraversal.over(OrderTraversals.lines(), ORDER, ID_MONAD)
                  .filter(line -> line.quantity() >= 2)
                  .modify(LineItemLenses.price(), ForOpticsBook::tenPercentOff)
                  .run());
      Order viaOptics =
          Traversals.modify(
              OrderTraversals.lines()
                  .filtered(line -> line.quantity() >= 2)
                  .andThen(LineItemLenses.price()),
              ForOpticsBook::tenPercentOff,
              ORDER);

      assertThat(viaBuilder).isEqualTo(viaOptics);
    }

    @Test
    @DisplayName(
        "Every step is pure: over List's instance, run gives one result, the instance's of")
    void everyStepIsPure() {
      List<Order> results =
          LIST.narrow(
              ForTraversal.over(OrderTraversals.lines(), ORDER, Instances.monad(list()))
                  .modify(LineItemLenses.quantity(), quantity -> quantity + 1)
                  .run());

      Order expected =
          Traversals.modify(
              OrderTraversals.lines().andThen(LineItemLenses.quantity()), q -> q + 1, ORDER);
      assertThat(results).containsExactly(expected);
    }

    @Test
    @DisplayName(
        "filter tests each element as it was in the source, not as an earlier modify left it")
    void filterTestsTheSourceElement() {
      Order result =
          ID.unwrap(
              ForTraversal.over(OrderTraversals.lines(), ORDER, ID_MONAD)
                  .modify(LineItemLenses.quantity(), quantity -> quantity + 1)
                  .filter(line -> line.quantity() >= 2)
                  .modify(LineItemLenses.quantity(), quantity -> quantity * 10)
                  .run());

      // the lamp had 1, became 2, and is still skipped: the filter saw the 1
      assertThat(result.lines()).extracting(LineItem::quantity).containsExactly(2, 50);
    }

    @Test
    @DisplayName("filter governs only the steps after it")
    void filterGovernsLaterSteps() {
      Order result =
          ID.unwrap(
              ForTraversal.over(OrderTraversals.lines(), ORDER, ID_MONAD)
                  .modify(LineItemLenses.quantity(), quantity -> quantity + 1)
                  .filter(line -> line.quantity() >= 2)
                  .run());

      assertThat(result.lines()).extracting(LineItem::quantity).containsExactly(2, 5);
    }

    @Test
    @DisplayName("toList reads the source, so an earlier modify does not show")
    void toListReadsTheSource() {
      List<LineItem> listed =
          ID.unwrap(
              ForTraversal.over(OrderTraversals.lines(), ORDER, ID_MONAD)
                  .modify(LineItemLenses.price(), ForOpticsBook::tenPercentOff)
                  .filter(line -> line.quantity() >= 2)
                  .toList());

      assertThat(listed).containsExactly(ORDER.lines().get(1));
    }

    @Test
    @DisplayName("ForIndexed counts from 0, and toIndexedList keeps only what the filters pass")
    void forIndexedCountsFromZero() {
      List<Pair<Integer, LineItem>> all =
          ID.unwrap(
              ForIndexed.overIndexed(IndexedTraversals.forList(), ORDER.lines(), ID_MONAD)
                  .toIndexedList());
      List<Pair<Integer, LineItem>> second =
          ID.unwrap(
              ForIndexed.overIndexed(IndexedTraversals.forList(), ORDER.lines(), ID_MONAD)
                  .filterIndex(index -> index % 2 == 1)
                  .toIndexedList());

      assertThat(all).extracting(Pair::first).containsExactly(0, 1);
      assertThat(second).containsExactly(new Pair<>(1, ORDER.lines().get(1)));
    }

    @Test
    @DisplayName("ForIndexed's filter also tests the source element")
    void forIndexedFilterTestsTheSource() {
      List<LineItem> result =
          ID.unwrap(
              ForIndexed.overIndexed(IndexedTraversals.forList(), ORDER.lines(), ID_MONAD)
                  .modify(LineItemLenses.quantity(), (index, quantity) -> quantity + 1)
                  .filter((index, line) -> line.quantity() >= 2)
                  .modify(LineItemLenses.quantity(), (index, quantity) -> quantity * 10)
                  .run());

      assertThat(result).extracting(LineItem::quantity).containsExactly(2, 50);
    }

    @Test
    @DisplayName("ForIndexed's set takes a function of the index")
    void forIndexedSetTakesAFunctionOfTheIndex() {
      List<LineItem> numbered =
          ID.unwrap(
              ForIndexed.overIndexed(IndexedTraversals.forList(), ORDER.lines(), ID_MONAD)
                  .set(LineItemLenses.sku(), index -> "LINE-" + index)
                  .run());

      assertThat(numbered).extracting(LineItem::sku).containsExactly("LINE-0", "LINE-1");
    }

    @Test
    @DisplayName("tenPercentOff and halfPrice round to pence")
    void pricesRoundToPence() {
      assertThat(tenPercentOff(new BigDecimal("2.55"))).isEqualTo(new BigDecimal("2.30"));
      assertThat(halfPrice(new BigDecimal("2.55"))).isEqualTo(new BigDecimal("1.28"));
      assertThat(halfPrice(new BigDecimal("2.50"))).isEqualTo(new BigDecimal("1.25"));
    }
  }
}
