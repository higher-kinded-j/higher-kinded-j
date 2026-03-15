// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import net.jqwik.api.*;
import org.higherkindedj.hkt.Monoid;

/**
 * Property-based tests for the monoid laws of {@link Fold#plus}, {@link Fold#empty}, and {@link
 * Fold#sum}.
 *
 * <p>These tests verify that {@code Fold} instances combined via {@code plus} satisfy the monoid
 * laws:
 *
 * <ul>
 *   <li><b>Left identity:</b> {@code Fold.empty().plus(fold).getAll(s) == fold.getAll(s)}
 *   <li><b>Right identity:</b> {@code fold.plus(Fold.empty()).getAll(s) == fold.getAll(s)}
 *   <li><b>Associativity:</b> {@code (a.plus(b)).plus(c).getAll(s) == a.plus(b.plus(c)).getAll(s)}
 * </ul>
 *
 * <p>Additionally tests length additivity and foldMap distributivity.
 */
class FoldPlusPropertyTest {

  record Item(String name, int price) {}

  record Order(List<Item> items) {}

  private static final Monoid<Integer> SUM_MONOID =
      new Monoid<>() {
        @Override
        public Integer empty() {
          return 0;
        }

        @Override
        public Integer combine(Integer a, Integer b) {
          return a + b;
        }
      };

  @Provide
  Arbitrary<Order> orders() {
    Arbitrary<Item> items =
        Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.integers().between(1, 1000))
            .as(Item::new);
    return items.list().ofMinSize(0).ofMaxSize(10).map(Order::new);
  }

  @Provide
  Arbitrary<Fold<Order, Item>> folds() {
    return Arbitraries.of(
        Fold.of(Order::items),
        Fold.of(order -> order.items().isEmpty() ? List.of() : List.of(order.items().getFirst())),
        Fold.<Order, Item>empty(),
        Fold.of(order -> order.items().stream().filter(i -> i.price() > 500).toList()));
  }

  @Property
  @Label("Left identity: Fold.empty().plus(fold).getAll(s) == fold.getAll(s)")
  void leftIdentity(@ForAll("folds") Fold<Order, Item> fold, @ForAll("orders") Order order) {
    Fold<Order, Item> combined = Fold.<Order, Item>empty().plus(fold);
    assertThat(combined.getAll(order)).isEqualTo(fold.getAll(order));
  }

  @Property
  @Label("Right identity: fold.plus(Fold.empty()).getAll(s) == fold.getAll(s)")
  void rightIdentity(@ForAll("folds") Fold<Order, Item> fold, @ForAll("orders") Order order) {
    Fold<Order, Item> combined = fold.plus(Fold.empty());
    assertThat(combined.getAll(order)).isEqualTo(fold.getAll(order));
  }

  @Property
  @Label("Associativity: (a.plus(b)).plus(c).getAll(s) == a.plus(b.plus(c)).getAll(s)")
  void associativity(
      @ForAll("folds") Fold<Order, Item> a,
      @ForAll("folds") Fold<Order, Item> b,
      @ForAll("folds") Fold<Order, Item> c,
      @ForAll("orders") Order order) {
    assertThat(a.plus(b).plus(c).getAll(order)).isEqualTo(a.plus(b.plus(c)).getAll(order));
  }

  @Property
  @Label("Length additivity: combined.length(s) == fold1.length(s) + fold2.length(s)")
  void lengthAdditivity(
      @ForAll("folds") Fold<Order, Item> fold1,
      @ForAll("folds") Fold<Order, Item> fold2,
      @ForAll("orders") Order order) {
    Fold<Order, Item> combined = fold1.plus(fold2);
    assertThat(combined.length(order)).isEqualTo(fold1.length(order) + fold2.length(order));
  }

  @Property
  @Label(
      "foldMap distributivity: combined.foldMap(m, f, s) == m.combine(fold1.foldMap(...), fold2.foldMap(...))")
  void foldMapDistributivity(
      @ForAll("folds") Fold<Order, Item> fold1,
      @ForAll("folds") Fold<Order, Item> fold2,
      @ForAll("orders") Order order) {
    Fold<Order, Item> combined = fold1.plus(fold2);
    Function<Item, Integer> f = Item::price;

    assertThat(combined.foldMap(SUM_MONOID, f, order))
        .isEqualTo(
            SUM_MONOID.combine(
                fold1.foldMap(SUM_MONOID, f, order), fold2.foldMap(SUM_MONOID, f, order)));
  }
}
