// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.navigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.BULB;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.HOME;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.LAMP;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.ORDER_ID;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.consignment;
import static org.higherkindedj.example.book.optics.cast.CastFixtures.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.example.book.optics.cast.Bank;
import org.higherkindedj.example.book.optics.cast.Card;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Collections, Optionals and Sealed Types page makes about its examples. */
@DisplayName("the Collections, Optionals and Sealed Types page")
class NavigationBookTest {

  private static final Catalogue CATALOGUE =
      new Catalogue("Autumn", Map.of("LAMP", new BigDecimal("40.00")));
  private static final Consignment CONSIGNMENT = consignment(new ConsignmentState.Pending());

  @Test
  @DisplayName("the generated list method and .each() on the lens reach the same elements")
  void eachGenerated() {
    assertThat(NavigationBook.eachGenerated(ORDER))
        .containsExactly(List.of(LAMP, BULB), List.of(LAMP, BULB));
  }

  @Test
  @DisplayName(".each(Each) reaches a map's values and a Maybe behind a lens")
  void eachCustom() {
    SavedForLater saved = new SavedForLater(Maybe.just(BULB));

    assertThat(NavigationBook.eachCustom(CATALOGUE, saved))
        .containsExactly(new BigDecimal("40.00"), BULB);
  }

  @Test
  @DisplayName("indexing reads the first line and a map value; headOption writes to every line")
  void byIndex() {
    NavigationBook.Indexed indexed = NavigationBook.byIndex(ORDER, CATALOGUE);

    assertThat(indexed.first()).contains(LAMP);
    assertThat(indexed.alsoFirst().getOptional(ORDER)).contains(LAMP);
    assertThat(indexed.lampPrice()).contains(new BigDecimal("40.00"));
    assertThat(NavigationBook.byIndex(order(List.of()), new Catalogue("Empty", Map.of())).first())
        .isEmpty();
    assertThat(indexed.alsoFirst().set(BULB, ORDER).lines()).containsExactly(BULB, BULB);
  }

  @Test
  @DisplayName(".nullable() reads a null as absent")
  void nullable() {
    assertThat(NavigationBook.nullable())
        .containsExactly(Optional.empty(), Optional.of("Amazing Grace"));
  }

  @Test
  @DisplayName("the generated prism and instanceOf both reach only the cards")
  void sealedVariants() {
    PaymentHistory history =
        new PaymentHistory(ORDER_ID, List.of(new Card("4242424242424242"), new Bank("GB00TEST")));

    NavigationBook.Variants variants = NavigationBook.sealedVariants(history);

    assertThat(variants.pans()).containsExactly("4242424242424242");
    assertThat(variants.samePans().getAll(history)).containsExactly("4242424242424242");
    assertThat(variants.samePans().modifyAll(pan -> "x", history).payments())
        .containsExactly(new Card("x"), new Bank("GB00TEST"));
    assertThat(variants.masked().payments())
        .containsExactly(new Card("**** 4242"), new Bank("GB00TEST"));
  }

  @Test
  @DisplayName("composing with a lens, a prism and a traversal gives the three path types")
  void composeExisting() {
    NavigationBook.Composed composed = NavigationBook.composeExisting();

    assertThat(composed.street().get(CONSIGNMENT)).isEqualTo("1 Long Street");
    assertThat(composed.firstLine().getOptional(ORDER)).contains(LAMP);
    assertThat(composed.allLines().count(ORDER)).isEqualTo(2);
  }

  @Test
  @DisplayName("a navigator and the spelled-out path read the same city")
  void navigators() {
    assertThat(NavigationBook.withAndWithoutNavigators(CONSIGNMENT))
        .containsExactly("London", "London");

    NavigationBook.NavigatorOrVia result = NavigationBook.navigatorOrVia(ORDER);
    assertThat(result.email()).isEqualTo("ada@example.com");
    assertThat(result.skus()).containsExactly("LAMP", "BULB");
  }

  @Test
  @DisplayName("toPath() reaches traced, which sees the address on its way")
  void toPath() {
    NavigationBook.Normalised normalised = NavigationBook.normalisePostcode(CONSIGNMENT);

    assertThat(normalised.consignment().to())
        .isEqualTo(new Address(HOME.street(), HOME.city(), "N1 1AA"));
    assertThat(normalised.seen()).containsExactly("n1 1aa");
  }

  @Test
  @DisplayName("an Either field reads its Right; set replaces a Left, and modify leaves it")
  void spiEither() {
    Stockroom left = new Stockroom("North", Map.of(), Either.left("unverified"));

    NavigationBook.Verified verified = NavigationBook.verified(left);

    assertThat(verified.name()).isEmpty();
    assertThat(verified.renamed().verifiedName()).isEqualTo(Either.right("Northern"));
    assertThat(verified.untouched()).isEqualTo(left);

    Stockroom right = new Stockroom("North", Map.of(), Either.right("North"));
    NavigationBook.Verified fromRight = NavigationBook.verified(right);

    assertThat(fromRight.name()).contains("North");
    assertThat(fromRight.untouched().verifiedName()).isEqualTo(Either.right("NORTH"));
  }

  @Test
  @DisplayName("joining a path keeps the field names, and joining its raw optic drops them")
  void fieldNamesThroughVia() {
    assertThat(OrderFocus.lines().via(LineItemFocus.sku()).segments())
        .containsExactly("lines", "sku");
    assertThat(OrderFocus.lines().via(LineItemFocus.sku().toLens()).segments())
        .containsExactly("lines");
  }

  @Test
  @DisplayName("ListPrisms read the head, the last element and the tail")
  void listPrisms() {
    NavigationBook.Decomposed decomposed = NavigationBook.decompose(ORDER);

    assertThat(decomposed.first()).contains(LAMP);
    assertThat(decomposed.last()).contains(BULB);
    assertThat(decomposed.tail()).contains(List.of(BULB));
  }

  @Test
  @DisplayName("a Map field's values are reached through .each(mapValuesEach())")
  void spiWidening() {
    Stockroom stockroom = new Stockroom("North", Map.of("LAMP", 12), Either.right("North"));

    assertThat(NavigationBook.spiWidening(stockroom)).containsExactly(12);
  }

  @Test
  @DisplayName("nested containers widen as the table says")
  void nested() {
    NestedConfig nestedConfig =
        new NestedConfig(
            Optional.of(List.of("alpha")),
            List.of(Optional.of("beta"), Optional.empty()),
            Optional.of(Optional.of("gamma")),
            List.of(List.of("x", "y"), List.of("z")),
            Optional.of(Either.right("approved")),
            Either.right(List.of(1, 2, 3)),
            Either.right(Map.of("hits", 7)));
    WidenedConfig widenedConfig = new WidenedConfig(Either.right(Map.of("hits", 7)));

    NavigationBook.Nested nested = NavigationBook.nested(nestedConfig, widenedConfig);

    assertThat(nested.tagValues()).containsExactly("alpha");
    assertThat(nested.innerValue()).contains("gamma");
    assertThat(nested.data()).containsExactly(1, 2, 3);
    assertThat(nested.meta()).contains(Map.of("hits", 7));
    assertThat(nested.hits()).containsExactly(7);
  }

  @Test
  @DisplayName("the nested rows the example does not read widen as the table says")
  void nestedRows() {
    NestedConfig nestedConfig =
        new NestedConfig(
            Optional.empty(),
            List.of(Optional.of("beta"), Optional.empty()),
            Optional.empty(),
            List.of(List.of("x", "y"), List.of("z")),
            Optional.of(Either.left("pending")),
            Either.left("no data"),
            Either.left("no meta"));

    TraversalPath<NestedConfig, String> items = NestedConfigFocus.items();
    TraversalPath<NestedConfig, String> grid = NestedConfigFocus.grid();
    AffinePath<NestedConfig, String> approval = NestedConfigFocus.approval();

    assertThat(items.getAll(nestedConfig)).containsExactly("beta");
    assertThat(grid.getAll(nestedConfig)).containsExactly("x", "y", "z");
    assertThat(approval.getOptional(nestedConfig)).isEmpty();
  }

  @Test
  @DisplayName("checkpoint: at(0) replaces only the first line, and headOption replaces every line")
  void checkpointAtZeroOrHeadOption() {
    LineItem sofa = new LineItem("SOFA", 1, new BigDecimal("400.00"));

    assertThat(FocusPath.of(OrderLenses.lines()).<LineItem>at(0).set(sofa, ORDER).lines())
        .containsExactly(sofa, BULB);
    assertThat(OrderFocus.lines().headOption().set(sofa, ORDER).lines())
        .containsExactly(sofa, sofa);
  }
}
