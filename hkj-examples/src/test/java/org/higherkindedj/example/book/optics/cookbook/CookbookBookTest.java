// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.cookbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ADA;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.BULB;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.LAMP;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER_ID;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.consignment;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.line;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.order;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.Bank;
import org.higherkindedj.example.book.optics.cast.Card;
import org.higherkindedj.example.book.optics.cast.CardFocus;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerFocus;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.CustomerProfileFocus;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.EmailAddressFocus;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.fluent.OpticOps;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Optics Cookbook makes about its recipes. */
@DisplayName("the Optics Cookbook")
class CookbookBookTest {

  private static final CustomerProfile PROFILE =
      new CustomerProfile(
          "Ada Lovelace",
          Optional.of("Countess"),
          Optional.of(new EmailAddress("ADA@Example.ORG")));

  private static final CustomerProfile NO_ALT_EMAIL =
      new CustomerProfile("Grace Hopper", Optional.empty(), Optional.empty());

  @Nested
  @DisplayName("recipe 1, a field several records deep")
  class DeepField {

    @Test
    @DisplayName("set writes the email and reuses everything off the path")
    void setWritesTheEmail() {
      Order updated = CookbookBook.changeEmail(ORDER);

      assertThat(updated.customer().email().value()).isEqualTo("ada@example.org");
      assertThat(updated.lines()).isSameAs(ORDER.lines());
      assertThat(ORDER.customer().email().value()).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("the composed lenses give the same order")
    void rawFormAgrees() {
      assertThat(CookbookBook.changeEmailRaw(ORDER)).isEqualTo(CookbookBook.changeEmail(ORDER));
    }
  }

  @Nested
  @DisplayName("recipe 2, a field inside an Optional")
  class OptionalField {

    @Test
    @DisplayName("modify changes a present email and leaves an absent one alone")
    void modifyWhenPresent() {
      CookbookBook.OptionalField result = CookbookBook.lowerAltEmail(PROFILE, NO_ALT_EMAIL);

      assertThat(result.lowered().altEmail()).contains(new EmailAddress("ada@example.org"));
      assertThat(result.untouched()).isEqualTo(NO_ALT_EMAIL);
    }

    @Test
    @DisplayName("set through a path ending in a lens leaves an absent email alone")
    void setThroughTheLensLeavesAbsentAlone() {
      AffinePath<CustomerProfile, String> altEmail =
          CustomerProfileFocus.altEmail().via(EmailAddressFocus.value());

      assertThat(altEmail.set("grace@example.org", NO_ALT_EMAIL)).isEqualTo(NO_ALT_EMAIL);
    }

    @Test
    @DisplayName("set through a path ending at the Optional writes even when it is empty")
    void setAtTheOptionalWrites() {
      CustomerProfile written =
          CustomerProfileFocus.altEmail().set(new EmailAddress("grace@example.org"), NO_ALT_EMAIL);

      assertThat(written.altEmail()).contains(new EmailAddress("grace@example.org"));
    }

    @Test
    @DisplayName("the lens, prism and lens give the same profile")
    void rawFormAgrees() {
      assertThat(CookbookBook.lowerAltEmailRaw(PROFILE))
          .isEqualTo(CookbookBook.lowerAltEmail(PROFILE, NO_ALT_EMAIL).lowered());
      assertThat(CookbookBook.lowerAltEmailRaw(NO_ALT_EMAIL)).isEqualTo(NO_ALT_EMAIL);
    }
  }

  @Nested
  @DisplayName("recipe 3, every element of a list inside a list")
  class EveryElement {

    private final OrderHistory history =
        new OrderHistory(
            List.of(
                order(List.of(line("LAMP", "36.000"), line("BULB", "2.250"))),
                order(List.of(line("DESK", "119.995")))));

    @Test
    @DisplayName("modifyAll rounds the price of every line of every order")
    void roundsEveryPrice() {
      OrderHistory rounded = CookbookBook.roundEveryPrice(history);

      assertThat(rounded.orders())
          .flatMap(Order::lines)
          .extracting(LineItem::price)
          .containsExactly(
              new BigDecimal("36.00"), new BigDecimal("2.25"), new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("the generated traversals give the same history")
    void rawFormAgrees() {
      assertThat(CookbookBook.roundEveryPriceRaw(history))
          .isEqualTo(CookbookBook.roundEveryPrice(history));
    }
  }

  @Nested
  @DisplayName("recipe 4, only the elements that match")
  class MatchingElements {

    @Test
    @DisplayName("filter discounts only the bulk line, and modifyWhen doubles only it")
    void onlyTheBulkLine() {
      CookbookBook.Matching matching = CookbookBook.bulkLines(ORDER);

      assertThat(matching.discounted().lines().get(0)).isEqualTo(LAMP);
      assertThat(matching.discounted().lines().get(1).price()).isEqualByComparingTo("2.25");
      assertThat(matching.doubled().lines())
          .containsExactly(LAMP, new LineItem("BULB", 8, BULB.price()));
    }

    @Test
    @DisplayName("the filtered generated traversal gives the same order")
    void rawFormAgrees() {
      assertThat(CookbookBook.bulkLinesRaw(ORDER))
          .isEqualTo(CookbookBook.bulkLines(ORDER).discounted());
    }

    @Test
    @DisplayName("modifyWhen is filter followed by modifyAll")
    void modifyWhenIsFilterThenModifyAll() {
      TraversalPath<Order, LineItem> lines = OrderFocus.lines();

      assertThat(lines.modifyWhen(line -> line.quantity() >= 4, line -> LAMP, ORDER))
          .isEqualTo(lines.filter(line -> line.quantity() >= 4).modifyAll(line -> LAMP, ORDER));
    }
  }

  @Nested
  @DisplayName("recipe 5, one variant of a sealed type")
  class OneVariant {

    private final Consignment returned =
        consignment(new ConsignmentState.Returned("  wrong colour  "));
    private final Consignment pending = consignment(new ConsignmentState.Pending());

    @Test
    @DisplayName("modify strips a returned reason and leaves a pending consignment alone")
    void onlyTheReturnedVariant() {
      CookbookBook.OneVariant result = CookbookBook.tidyReturnReason(returned, pending);

      assertThat(result.tidied().state()).isEqualTo(new ConsignmentState.Returned("wrong colour"));
      assertThat(result.untouched()).isEqualTo(pending);
    }

    @Test
    @DisplayName("the lens and prism, rebuilding the variant, give the same consignment")
    void rawFormAgrees() {
      assertThat(CookbookBook.tidyReturnReasonRaw(returned))
          .isEqualTo(CookbookBook.tidyReturnReason(returned, pending).tidied());
      assertThat(CookbookBook.tidyReturnReasonRaw(pending)).isEqualTo(pending);
    }
  }

  @Nested
  @DisplayName("recipe 6, one variant in a list of mixed types")
  class VariantsInAList {

    private final PaymentHistory history =
        new PaymentHistory(
            ORDER_ID,
            List.of(
                new Card("4242424242424242"), new Bank("GB00TEST"), new Card("5555444433331111")));

    @Test
    @DisplayName("the path reads and masks the card numbers, skipping the bank payment")
    void cardsOnly() {
      CookbookBook.Variants variants = CookbookBook.cardNumbers(history);

      assertThat(variants.pans()).containsExactly("4242424242424242", "5555444433331111");
      assertThat(variants.masked().payments())
          .containsExactly(new Card("**** 4242"), new Bank("GB00TEST"), new Card("**** 1111"));
    }

    @Test
    @DisplayName("instanceOf takes the generated prism's place")
    void instanceOfAgrees() {
      TraversalPath<PaymentHistory, String> byClass =
          PaymentHistoryFocus.payments()
              .via(AffinePath.instanceOf(Card.class))
              .via(CardFocus.pan());

      assertThat(byClass.getAll(history)).isEqualTo(CookbookBook.cardNumbers(history).pans());
    }

    @Test
    @DisplayName("the generated traversal, prism and a hand-written lens read the same numbers")
    void rawFormAgrees() {
      assertThat(CookbookBook.cardNumbersRaw(history))
          .isEqualTo(CookbookBook.cardNumbers(history).pans());
    }
  }

  @Nested
  @DisplayName("recipe 7, a map entry with a fallback")
  class MapEntry {

    private final Stockroom stockroom =
        new Stockroom("North", Map.of("LAMP", 3, "BULB", 40), Either.right("Northern"));

    @Test
    @DisplayName("a missing key reads as the fallback, modify skips it, and set adds it")
    void readModifySet() {
      CookbookBook.MapEntry entry = CookbookBook.stockLevels(stockroom);

      assertThat(entry.desksInStock()).isZero();
      assertThat(entry.picked().stock()).containsEntry("LAMP", 2).containsEntry("BULB", 40);
      assertThat(entry.stocked().stock()).containsEntry("DESK", 5).hasSize(3);

      AffinePath<Stockroom, Integer> desks = StockroomFocus.stock().atKey("DESK");
      assertThat(desks.modify(count -> count - 1, stockroom)).isEqualTo(stockroom);
    }

    @Test
    @DisplayName("the lens and mapAt read the same fallback")
    void rawFormAgrees() {
      assertThat(CookbookBook.desksInStockRaw(stockroom)).isZero();
      Stockroom withDesks = CookbookBook.stockLevels(stockroom).stocked();
      assertThat(CookbookBook.desksInStockRaw(withDesks)).isEqualTo(5);
    }
  }

  @Nested
  @DisplayName("recipe 8, the value inside an Either field")
  class EitherField {

    private final Stockroom verified =
        new Stockroom("North", Map.of(), Either.right("  Northern  "));
    private final Stockroom unverified =
        new Stockroom("South", Map.of(), Either.left("  no match  "));

    @Test
    @DisplayName("modify changes a Right and leaves a Left alone")
    void rightOnly() {
      CookbookBook.EitherField result = CookbookBook.tidyVerifiedName(verified, unverified);

      assertThat(result.tidied().verifiedName()).isEqualTo(Either.right("Northern"));
      assertThat(result.untouched()).isEqualTo(unverified);
    }

    @Test
    @DisplayName("the lens and Prisms.right() give the same stockroom")
    void rawFormAgrees() {
      assertThat(CookbookBook.tidyVerifiedNameRaw(verified))
          .isEqualTo(CookbookBook.tidyVerifiedName(verified, unverified).tidied());
    }

    @Test
    @DisplayName("on a bare Either, map does what the prism's modify does")
    void plainEitherMap() {
      Either<String, String> name = verified.verifiedName();

      assertThat(name.map(String::strip))
          .isEqualTo(CookbookBook.tidyVerifiedName(verified, unverified).tidied().verifiedName());
    }
  }

  @Nested
  @DisplayName("recipe 9, a PATCH that reports every bad field")
  class Patch {

    @Test
    @DisplayName("two bad fields are both reported, each by its path, in edit order")
    void everyBadField() {
      var patched = CookbookBook.applyPatch(ADA, new CustomerPatch("  ", "ada at example.org"));

      assertThat(patched)
          .hasToString("Invalid(NonEmptyList[name: must not be blank, email: not an address])");
    }

    @Test
    @DisplayName("a good patch changes only the fields it supplies")
    void onlySuppliedFields() {
      var patched = CookbookBook.applyPatch(ADA, new CustomerPatch(null, " ada@example.org "));

      assertThat(patched.isValid()).isTrue();
      assertThat(patched.get()).isEqualTo(new Customer("Ada", new EmailAddress("ada@example.org")));
    }

    @Test
    @DisplayName("a lens-built path with at(...) reports the same errors")
    void rawFormAgrees() {
      CustomerPatch bad = new CustomerPatch("  ", "ada at example.org");

      assertThat(CookbookBook.applyPatchRaw(ADA, bad))
          .hasToString(CookbookBook.applyPatch(ADA, bad).toString());
    }

    @Test
    @DisplayName("a generated path is labelled, and a lens-built one is not")
    void labels() {
      assertThat(CustomerFocus.email().toPath().pathString()).isEqualTo("email");
      assertThat(FocusPath.of(CustomerLenses.email()).segments()).isEmpty();
    }
  }

  @Nested
  @DisplayName("recipe 10, checking the values a path reaches")
  class CheckValues {

    private final Order empty =
        order(
            List.of(new LineItem("LAMP", 0, LAMP.price()), new LineItem("BULB", -1, BULB.price())));

    @Test
    @DisplayName("modifyAllValidated reports every bad quantity, modifyAllEither the first")
    void everyOrFirst() {
      CookbookBook.Checked checked = CookbookBook.checkQuantities(empty);

      assertThat(checked.every()).hasToString("Invalid([No items: 0, No items: -1])");
      assertThat(checked.first()).hasToString("Left(No items: 0)");
    }

    @Test
    @DisplayName("modifyAllEither keeps the first error, but still checks every quantity")
    void failFastStillChecksEveryValue() {
      List<Integer> checkedQuantities = new ArrayList<>();
      Traversal<Order, Integer> quantities =
          OrderFocus.lines().via(LineItemFocus.quantity()).toTraversal();

      OpticOps.modifyAllEither(
          empty,
          quantities,
          quantity -> {
            checkedQuantities.add(quantity);
            return Quantities.check(quantity).toEither();
          });

      assertThat(checkedQuantities).containsExactly(0, -1);
    }

    @Test
    @DisplayName("a good order comes back valid and unchanged")
    void goodOrder() {
      CookbookBook.Checked checked = CookbookBook.checkQuantities(ORDER);

      assertThat(checked.every().isValid()).isTrue();
      assertThat(checked.every().get()).isEqualTo(ORDER);
      assertThat(checked.first()).isEqualTo(Either.right(ORDER));
    }

    @Test
    @DisplayName("the generated traversal and lens report the same errors")
    void rawFormAgrees() {
      assertThat(CookbookBook.checkQuantitiesRaw(empty))
          .isEqualTo(CookbookBook.checkQuantities(empty).every());
    }
  }

  @Nested
  @DisplayName("recipe 11, totalling, counting and testing")
  class Totals {

    @Test
    @DisplayName("Ada's order of a lamp and four bulbs holds five items over two lines, 50.00")
    void adasOrder() {
      CookbookBook.Totals totals = CookbookBook.totals(ORDER);

      assertThat(totals.items()).isEqualTo(5);
      assertThat(totals.anyBulk()).isTrue();
      assertThat(totals.noEmptyLines()).isTrue();
      assertThat(totals.lineCount()).isEqualTo(2);
      assertThat(totals.total()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("the traversal's fold gives the same answers")
    void rawFormAgrees() {
      CookbookBook.RawTotals raw = CookbookBook.totalsRaw(ORDER);

      assertThat(raw.items()).isEqualTo(5);
      assertThat(raw.anyBulk()).isTrue();
      assertThat(raw.lineCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a stream over the list field reads the same quantity")
    void plainStream() {
      assertThat(ORDER.lines().stream().mapToInt(LineItem::quantity).sum())
          .isEqualTo(CookbookBook.totals(ORDER).items());
    }
  }

  @Nested
  @DisplayName("recipe 12, values from several places")
  class SeveralPlaces {

    @Test
    @DisplayName("the combined fold reads the first fold's values before the second's")
    void inOrder() {
      CookbookBook.SeveralPlaces places = CookbookBook.names(PROFILE);

      assertThat(places.names()).containsExactly("Ada Lovelace", "Countess");
      assertThat(places.known()).isTrue();
      assertThat(places.searchTerms())
          .containsExactly("Ada Lovelace", "Countess", "ADA@Example.ORG");
      assertThat(CookbookBook.names(NO_ALT_EMAIL).searchTerms()).containsExactly("Grace Hopper");
    }

    @Test
    @DisplayName("the lens and prism folds read the same names")
    void rawFormAgrees() {
      assertThat(CookbookBook.namesRaw(PROFILE)).isEqualTo(CookbookBook.names(PROFILE).names());
    }

    @Test
    @DisplayName("Stream.concat reads the same names")
    void plainJava() {
      List<String> plain =
          Stream.concat(Stream.of(PROFILE.name()), PROFILE.nickname().stream()).toList();

      assertThat(plain).isEqualTo(CookbookBook.names(PROFILE).names());
    }
  }

  @Nested
  @DisplayName("recipe 13, sorting and reversing")
  class Sort {

    private static final LineItem DESK = line("DESK", "120.00");

    @Test
    @DisplayName("sorted orders the lines by price, and reversed reverses them")
    void sortAndReverse() {
      CookbookBook.Sorted sorted = CookbookBook.sortLines(order(List.of(LAMP, BULB, DESK)));

      assertThat(sorted.byPrice().lines()).containsExactly(BULB, LAMP, DESK);
      assertThat(sorted.reversed().lines()).containsExactly(DESK, BULB, LAMP);
    }

    @Test
    @DisplayName("a Focus path's toTraversal() sorts the same way")
    void fromAFocusPath() {
      Order order = order(List.of(LAMP, BULB, DESK));

      Order sorted =
          Traversals.sorted(
              OrderFocus.lines().toTraversal(), Comparator.comparing(LineItem::price), order);

      assertThat(sorted).isEqualTo(CookbookBook.sortLines(order).byPrice());
    }

    @Test
    @DisplayName("a filtered traversal sorts only the lines it keeps, and the others stay put")
    void filteredSortsInPlace() {
      LineItem a = new LineItem("A", 1, new BigDecimal("30"));
      LineItem b = new LineItem("B", 5, new BigDecimal("20"));
      LineItem c = new LineItem("C", 1, new BigDecimal("10"));
      LineItem d = new LineItem("D", 5, new BigDecimal("5"));

      Order sorted =
          Traversals.sorted(
              OrderTraversals.lines().filtered(line -> line.quantity() >= 4),
              Comparator.comparing(LineItem::price),
              order(List.of(a, b, c, d)));

      assertThat(sorted.lines()).containsExactly(a, d, c, b);
    }
  }

  @Nested
  @DisplayName("recipe 14, tracing a path")
  class Trace {

    @Test
    @DisplayName("a read calls the observer with every value it found")
    void readIsObserved() {
      CookbookBook.Traced traced = CookbookBook.traceSkus(ORDER);

      assertThat(traced.read()).containsExactly("LAMP", "BULB");
      assertThat(traced.log()).containsExactly("read 2 SKUs: [LAMP, BULB]");
    }

    @Test
    @DisplayName("modifyAll does not call the observer")
    void writeIsNotObserved() {
      CookbookBook.Traced traced = CookbookBook.traceSkus(ORDER);

      traced.skus().modifyAll(String::toLowerCase, ORDER);

      assertThat(traced.log()).hasSize(1);
    }

    @Test
    @DisplayName("a via or filter after traced returns a path the observer does not see")
    void composingDropsTheTrace() {
      CookbookBook.Traced traced = CookbookBook.traceSkus(ORDER);

      traced.skus().filter(sku -> sku.startsWith("L")).getAll(ORDER);
      traced.skus().via(Lens.<String, String>of(sku -> sku, (_, sku) -> sku)).getAll(ORDER);

      assertThat(traced.log()).hasSize(1);
    }

    @Test
    @DisplayName("the queries built on getAll are observed too")
    void queriesAreObserved() {
      List<String> seen = new ArrayList<>();
      TraversalPath<Order, String> skus =
          OrderFocus.lines().via(LineItemFocus.sku()).traced((_, found) -> seen.add("read"));

      skus.count(ORDER);
      skus.exists(sku -> sku.isEmpty(), ORDER);

      assertThat(seen).hasSize(2);
    }
  }
}
