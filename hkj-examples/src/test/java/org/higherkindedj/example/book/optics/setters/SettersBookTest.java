// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.setters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.optics.Setter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the values the Setters page's comments show for its included regions. */
@DisplayName("the Setters page: what each setter writes")
class SettersBookTest {

  private static final UserSettings SETTINGS = new UserSettings("light", true, 14, Map.of());

  @Test
  @DisplayName("modify lowercases or suffixes the username and leaves the rest alone")
  void modifyRewritesTheUsername() {
    assertThat(SettersBook.modify(SETTINGS))
        .containsExactly(
            new User("john_doe", "john@example.com", 10, SETTINGS),
            new User("JOHN_DOE_admin", "john@example.com", 10, SETTINGS));
    assertThat(SettersBook.modify(SETTINGS).getFirst().toString())
        .startsWith("User[username=john_doe, email=john@example.com, loginCount=10, settings=");
  }

  @Test
  @DisplayName("set replaces the login count")
  void setReplacesTheLoginCount() {
    assertThat(SettersBook.set(SETTINGS))
        .isEqualTo(new User("john", "john@example.com", 0, SETTINGS));
  }

  @Test
  @DisplayName("composed setters write the theme, and the font size two points larger")
  void composedSettersWriteDeep() {
    assertThat(SettersBook.compose())
        .containsExactly(
            new User("john", "john@example.com", 10, new UserSettings("dark", true, 14, Map.of())),
            new User(
                "john", "john@example.com", 10, new UserSettings("light", true, 16, Map.of())));
  }

  @Test
  @DisplayName("forList doubles or replaces every element")
  void forListWritesEveryElement() {
    assertThat(SettersBook.forList())
        .containsExactly(List.of(2, 4, 6, 8, 10), List.of(0, 0, 0, 0, 0));
  }

  @Test
  @DisplayName("forMapValues curves or resets every score, keeping the keys")
  void forMapValuesWritesEveryValue() {
    List<Map<String, Integer>> results = SettersBook.forMapValues();

    assertThat(results)
        .containsExactly(
            Map.of("Alice", 90, "Bob", 95, "Charlie", 83),
            Map.of("Alice", 0, "Bob", 0, "Charlie", 0));
    assertThat(results.get(0)).hasToString("{Alice=90, Bob=95, Charlie=83}");
    assertThat(results.get(1)).hasToString("{Alice=0, Bob=0, Charlie=0}");
  }

  @Test
  @DisplayName("a composed setter discounts every price to the penny, and restocks every product")
  void nestedSettersDiscountAndRestock() {
    List<Inventory> results = SettersBook.nested();

    // BigDecimal equality is scale-sensitive: the claim is two-decimal prices, so the scale counts.
    assertThat(results.get(0).products())
        .extracting(Product::price)
        .as("prices after a 10 percent discount, at scale 2")
        .containsExactly(
            new BigDecimal("899.99"), new BigDecimal("71.99"), new BigDecimal("269.99"));
    assertThat(results.get(1).products()).extracting(Product::stock).containsExactly(60, 110, 40);
    assertThat(results.get(1).products())
        .extracting(Product::price)
        .as("restocking leaves the prices alone")
        .containsExactly(
            new BigDecimal("999.99"), new BigDecimal("79.99"), new BigDecimal("299.99"));
  }

  @Test
  @DisplayName("modifyF keeps a valid username and gives Optional.empty for an invalid one")
  void modifyFValidates() {
    assertThat(SettersBook.modifyF(SETTINGS))
        .containsExactly(
            Optional.of(new User("john_doe", "john@example.com", 10, SETTINGS)), Optional.empty());
  }

  @Test
  @DisplayName("modifyF over a list succeeds only when every element does")
  void modifyFOverAListSequences() {
    assertThat(SettersBook.sequencing())
        .containsExactly(Optional.of(List.of(2, 4, 6)), Optional.empty());
  }

  @Test
  @DisplayName("the identity setter modifies or replaces the source itself")
  void identitySetter() {
    assertThat(SettersBook.identity()).containsExactly("HELLO", "world");
  }

  @Test
  @DisplayName("a composed setter normalises every product name")
  void normalisesNames() {
    assertThat(SettersBook.normalise())
        .extracting(Product::name)
        .containsExactly("Laptop", "Keyboard", "Monitor");
    assertThat(SettersBook.normalise().getFirst().toString()).startsWith("Product[name=Laptop, ");
  }

  @Test
  @DisplayName("a Setter.of setter refuses modifyF, and a fromGetSet setter runs it")
  void setterOfRefusesModifyF() {
    record Person(String name, int age) {}
    Person person = new Person("Ada", 36);
    Setter<Person, String> viaOf = Setter.of(f -> p -> new Person(f.apply(p.name()), p.age()));
    Setter<Person, String> viaGetSet =
        Setter.fromGetSet(Person::name, (p, name) -> new Person(name, p.age()));

    assertThatThrownBy(
            () ->
                viaOf.modifyF(
                    name -> OptionalKindHelper.OPTIONAL.widen(Optional.of(name)),
                    person,
                    Instances.monadError(optional())))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(
            OptionalKindHelper.OPTIONAL.narrow(
                viaGetSet.modifyF(
                    name -> OptionalKindHelper.OPTIONAL.widen(Optional.of(name.toUpperCase())),
                    person,
                    Instances.monadError(optional()))))
        .contains(new Person("ADA", 36));
  }
}
