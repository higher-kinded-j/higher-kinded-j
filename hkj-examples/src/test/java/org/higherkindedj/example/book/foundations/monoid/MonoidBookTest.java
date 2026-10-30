// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.monoid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.higherkindedj.example.book.foundations.monoid.MonoidBook.LINES;
import static org.higherkindedj.example.book.foundations.monoid.MonoidBook.REFILL;
import static org.higherkindedj.example.book.foundations.monoid.MonoidBook.UNITS;
import static org.higherkindedj.example.book.foundations.monoid.MonoidBook.lineTotal;
import static org.higherkindedj.example.book.foundations.monoid.MonoidBook.units;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.effect.ListPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Semigroup and Monoid page makes about combining and totalling values. */
@DisplayName("Semigroup and Monoid: combining two values, and starting from nothing")
class MonoidBookTest {

  private static final Monoid<BigDecimal> MONEY = Monoids.bigDecimalAddition();

  @Test
  @DisplayName("a semigroup combines two values, and Semigroups.string joins with its delimiter")
  void semigroupsCombine() {
    assertThat(Semigroups.<String>list().combine(List.of("a"), List.of("b")))
        .containsExactly("a", "b");
    assertThat(Semigroups.string("; ").combine("SKU is blank", "quantity must be positive"))
        .isEqualTo("SKU is blank; quantity must be positive");
  }

  @Test
  @DisplayName("an order's total keeps two decimal places, and no lines total to ZERO")
  void totalKeepsItsScale() {
    BigDecimal total = MONEY.combineAll(LINES.stream().map(MonoidBook::lineTotal).toList());

    assertThat(total).isEqualTo(new BigDecimal("50.00"));
    assertThat(MONEY.combineAll(List.of())).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("ListPath.foldMap gives the same total as combineAll")
  void listPathFoldMapMatchesCombineAll() {
    assertThat(ListPath.of(LINES).foldMap(MONEY, MonoidBook::lineTotal))
        .isEqualTo(MONEY.combineAll(LINES.stream().map(MonoidBook::lineTotal).toList()));
  }

  @Test
  @DisplayName("the units monoid adds each SKU's units across orders, from an empty map")
  void unitsAddAcrossOrders() {
    assertThat(UNITS.combine(units(LINES), units(REFILL))).isEqualTo(Map.of("BULB", 7, "LAMP", 1));
    assertThat(units(List.of())).isEmpty();
    assertThat(units(LINES)).isUnmodifiable();
  }

  @Test
  @DisplayName("combineN repeats a value or refuses a negative count; isEmpty ignores scale")
  void defaults() {
    assertThat(MONEY.combineN(new BigDecimal("40.00"), 6)).isEqualTo(new BigDecimal("240.00"));
    assertThat(MONEY.combineN(new BigDecimal("40.00"), 0)).isEqualTo(BigDecimal.ZERO);
    assertThatIllegalArgumentException().isThrownBy(() -> MONEY.combineN(BigDecimal.ONE, -1));
    assertThat(MONEY.isEmpty(new BigDecimal("0.00"))).isTrue();
    assertThat(BigDecimal.ZERO.equals(new BigDecimal("0.00"))).isFalse();
  }

  @Test
  @DisplayName("maximum finds the priciest line, and gives an empty Optional for no lines")
  void priciestLine() {
    Monoid<Optional<LineItem>> priciest = Monoids.maximum(Comparator.comparing(LineItem::price));

    assertThat(priciest.combineAll(LINES.stream().map(Optional::of).toList()))
        .contains(LINES.getFirst());
    assertThat(priciest.combineAll(List.of())).isEmpty();
  }

  @Test
  @DisplayName("firstOptional and lastOptional pick by position, comparing nothing")
  void firstAndLastPickByPosition() {
    List<Optional<String>> skus =
        List.of(
            Optional.empty(),
            Optional.of("DESK"),
            Optional.of("LAMP"),
            Optional.of("BULB"),
            Optional.of("CHAIR"),
            Optional.empty());

    assertThat(Monoids.<String>firstOptional().combineAll(skus)).contains("DESK");
    assertThat(Monoids.<String>lastOptional().combineAll(skus)).contains("CHAIR");
    assertThat(Monoids.<String>maximum().combineAll(skus)).contains("LAMP");
    assertThat(Monoids.<String>minimum().combineAll(skus)).contains("BULB");
  }

  @Test
  @DisplayName("the update monoid applies its updates left to right")
  void updatesApplyLeftToRight() {
    Update<LineItem> restock = line -> new LineItem(line.sku(), line.quantity() + 10, line.price());
    Update<LineItem> doubled = line -> new LineItem(line.sku(), line.quantity() * 2, line.price());
    Monoid<Update<LineItem>> steps = Monoids.update();

    assertThat(steps.combineAll(List.of(restock, doubled)).apply(LINES.getFirst()).quantity())
        .isEqualTo(22);
    assertThat(steps.combineAll(List.of(doubled, restock)).apply(LINES.getFirst()).quantity())
        .isEqualTo(12);
    assertThat(steps.combineAll(List.of()).apply(LINES.getFirst())).isEqualTo(LINES.getFirst());
  }

  @Test
  @DisplayName("double addition rounds where BigDecimal addition is exact")
  void doubleRounds() {
    assertThat(Monoids.doubleAddition().combine(0.1, 0.2)).isEqualTo(0.30000000000000004);
    assertThat(MONEY.combine(new BigDecimal("0.10"), new BigDecimal("0.20")))
        .isEqualTo(new BigDecimal("0.30"));
  }

  @Test
  @DisplayName("double addition is not associative: regrouping changes the sum")
  void doubleAdditionIsNotAssociative() {
    Monoid<Double> sum = Monoids.doubleAddition();

    assertThat(sum.combine(sum.combine(1e16, -1e16), 1.0)).isEqualTo(1.0);
    assertThat(sum.combine(1e16, sum.combine(-1e16, 1.0))).isEqualTo(0.0);
  }

  @Test
  @DisplayName("a monoid need not be commutative: string concatenation keeps its order")
  void combineNeedNotCommute() {
    assertThat(Monoids.string().combine("LAMP", "BULB")).isEqualTo("LAMPBULB");
    assertThat(Monoids.string().combine("BULB", "LAMP")).isEqualTo("BULBLAMP");
  }

  @Test
  @DisplayName(
      "Semigroups.string with a delimiter has no empty value: combineAll from \"\" adds one")
  void delimitedStringIsNoMonoid() {
    Monoid<String> notAMonoid = Monoid.of("", Semigroups.string(", "));

    assertThat(notAMonoid.combine("LAMP", "BULB")).isEqualTo("LAMP, BULB");
    assertThat(notAMonoid.combineAll(List.of("LAMP", "BULB"))).isEqualTo(", LAMP, BULB");
  }

  @Test
  @DisplayName("lineTotal is the price times the quantity")
  void lineTotalMultiplies() {
    assertThat(lineTotal(LINES.get(1))).isEqualTo(new BigDecimal("10.00"));
  }
}
