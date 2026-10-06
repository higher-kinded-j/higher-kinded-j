// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Collections, Optionals and Sealed Types page makes about its examples. */
@DisplayName("the Collections, Optionals and Sealed Types page")
class NavigationBookTest {

  private static final Item LAPTOP = new Item("LAP-1", 999.0);
  private static final Item MOUSE = new Item("MOU-1", 25.0);
  private static final Container CONTAINER = new Container(List.of(LAPTOP, MOUSE));
  private static final Config CONFIG = new Config(Map.of("database", new Setting("primary")));
  private static final Address LONDON = new Address("1 Long Street", "London");
  private static final Company ACME =
      new Company(
          "Acme",
          LONDON,
          List.of(
              new Department(
                  "Engineering",
                  List.of(new Employee("Bob", LONDON), new Employee("Carol", LONDON)))));

  @Test
  @DisplayName("the generated list method and .each() on the lens reach the same elements")
  void eachGenerated() {
    assertThat(NavigationBook.eachGenerated(CONTAINER))
        .containsExactly(List.of(LAPTOP, MOUSE), List.of(LAPTOP, MOUSE));
  }

  @Test
  @DisplayName(".each(Each) reaches a map's values and a Maybe behind a lens")
  void eachCustom() {
    Wrapper wrapper = new Wrapper(Maybe.just(new Setting("cache")));

    assertThat(NavigationBook.eachCustom(CONFIG, wrapper))
        .containsExactly(new Setting("primary"), new Setting("cache"));
  }

  @Test
  @DisplayName("indexing reads the first item and a map value; headOption writes to every element")
  void byIndex() {
    NavigationBook.Indexed indexed = NavigationBook.byIndex(CONTAINER, CONFIG);

    assertThat(indexed.first()).contains(LAPTOP);
    assertThat(indexed.alsoFirst().getOptional(CONTAINER)).contains(LAPTOP);
    assertThat(indexed.setting()).contains(new Setting("primary"));
    assertThat(NavigationBook.byIndex(new Container(List.of()), new Config(Map.of())).first())
        .isEmpty();
    assertThat(indexed.alsoFirst().set(MOUSE, CONTAINER).items()).containsExactly(MOUSE, MOUSE);
  }

  @Test
  @DisplayName(".nullable() reads a null as absent")
  void nullable() {
    assertThat(NavigationBook.nullable()).containsExactly(Optional.empty(), Optional.of("Bobby"));
  }

  @Test
  @DisplayName("the generated prism and instanceOf both reach only the circles")
  void sealedVariants() {
    Drawing drawing = new Drawing(List.of(new Circle(2.0), new Square(3.0)));

    NavigationBook.Variants variants = NavigationBook.sealedVariants(drawing);

    assertThat(variants.radii()).containsExactly(2.0);
    assertThat(variants.sameRadii()).containsExactly(2.0);
    assertThat(variants.doubled().shapes()).containsExactly(new Circle(4.0), new Square(3.0));
  }

  @Test
  @DisplayName("composing with a lens, a prism and a traversal gives the three path types")
  void composeExisting() {
    NavigationBook.Composed composed = NavigationBook.composeExisting();

    assertThat(composed.hqStreet().get(ACME)).isEqualTo("1 Long Street");
    assertThat(composed.firstItem().getOptional(CONTAINER)).contains(LAPTOP);
    assertThat(composed.allEmployees().count(ACME)).isEqualTo(2);
  }

  @Test
  @DisplayName("a navigator and the spelled-out path read the same city")
  void navigators() {
    assertThat(NavigationBook.withAndWithoutNavigators(ACME)).containsExactly("London", "London");

    NavigationBook.NavigatorOrVia result = NavigationBook.navigatorOrVia(ACME);
    assertThat(result.city()).isEqualTo("London");
    assertThat(result.employeeNames()).containsExactly("Bob", "Carol");
  }

  @Test
  @DisplayName("toPath() reaches traced, which sees the address on its way")
  void toPath() {
    NavigationBook.Relocated relocated = NavigationBook.relocate(ACME);

    assertThat(relocated.company().headquarters())
        .isEqualTo(new Address("1 Long Street", "Manchester"));
    assertThat(relocated.seen()).containsExactly("London");
  }

  @Test
  @DisplayName("an Either field reads its Right; set replaces a Left, and modify leaves it")
  void spiEither() {
    Warehouse left = new Warehouse("North", Map.of(), Either.left("unverified"));

    NavigationBook.Verified verified = NavigationBook.verified(left);

    assertThat(verified.name()).isEmpty();
    assertThat(verified.renamed().verifiedName()).isEqualTo(Either.right("Northern"));
    assertThat(verified.untouched()).isEqualTo(left);
  }

  @Test
  @DisplayName("ListPrisms read the head, the last element and the tail")
  void listPrisms() {
    NavigationBook.Decomposed decomposed = NavigationBook.decompose(CONTAINER);

    assertThat(decomposed.first()).contains(LAPTOP);
    assertThat(decomposed.last()).contains(MOUSE);
    assertThat(decomposed.tail()).contains(List.of(MOUSE));
  }

  @Test
  @DisplayName("a Map field's values are reached through .each(mapValuesEach())")
  void spiWidening() {
    Warehouse warehouse = new Warehouse("North", Map.of("widget", 12), Either.right("North"));

    assertThat(NavigationBook.spiWidening(warehouse)).containsExactly(12);
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
}
