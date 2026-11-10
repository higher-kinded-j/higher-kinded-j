// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.state;

import static org.higherkindedj.hkt.instances.Witnesses.state;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.higherkindedj.example.book.foundations.Basket;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.WithStatePath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateTuple;

/**
 * The basket shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/state_monad.html">State</a> page: adding a
 * line only while the basket stays within a limit, the same steps run from two baskets, a loop that
 * builds one step per line, the steps on a {@code WithStatePath}, and the instance over {@code
 * Kind}. The page {@code {{#include}}}s the anchored regions, {@code StateBookTest} holds the
 * claims the page makes, and the book's output gate runs {@link #main} against the comments in the
 * regions.
 */
public final class StateBook {

  /** The most a basket may come to. */
  static final BigDecimal LIMIT = new BigDecimal("100.00");

  static final LineItem LAMP = new LineItem("LAMP", 1, new BigDecimal("40.00"));
  static final LineItem BULBS = new LineItem("BULB", 4, new BigDecimal("2.50"));
  static final LineItem SOFA = new LineItem("SOFA", 1, new BigDecimal("450.00"));

  private StateBook() {}

  // ANCHOR: add
  // Adds the line if the basket stays within the limit, and says whether it did
  static State<Basket, Boolean> add(LineItem line) {
    return State.of(
        basket -> {
          Basket bigger = basket.add(line);
          return bigger.total().compareTo(LIMIT) <= 0
              ? new StateTuple<>(true, bigger)
              : new StateTuple<>(false, basket);
        });
  }

  // ANCHOR_END: add

  // ANCHOR: add_all
  // One step for each line: each is handed the basket the step before it left
  static State<Basket, List<String>> addAll(List<LineItem> lines) {
    State<Basket, List<String>> refused = State.pure(List.of());
    for (LineItem line : lines) {
      refused =
          refused.flatMap(
              soFar ->
                  add(line)
                      .map(
                          added ->
                              added
                                  ? soFar
                                  : Stream.concat(soFar.stream(), Stream.of(line.sku())).toList()));
    }
    return refused;
  }

  // ANCHOR_END: add_all

  // ANCHOR: checkout
  // zipWith runs the second path on the basket the first one left
  static WithStatePath<Basket, String> checkout(List<LineItem> lines) {
    return Path.state(addAll(lines))
        .zipWith(
            WithStatePath.inspect(Basket::total),
            (refused, total) ->
                refused.isEmpty() ? "pay " + total : "pay " + total + ", refused " + refused);
  }

  // ANCHOR_END: checkout

  public static void main(String[] args) {
    // ANCHOR: run
    State<Basket, Boolean> lampThenSofa = add(LAMP).flatMap(_ -> add(SOFA));

    StateTuple<Basket, Boolean> after = lampThenSofa.run(Basket.EMPTY);
    boolean sofaAdded = after.value();
    // false
    BigDecimal total = after.state().total();
    // 40.00

    BigDecimal fromBulbs = lampThenSofa.run(new Basket(List.of(BULBS))).state().total();
    // 50.00
    // ANCHOR_END: run
    System.out.println(sofaAdded);
    System.out.println(total);
    System.out.println(fromBulbs);

    // ANCHOR: add_all_outcome
    StateTuple<Basket, List<String>> trip = addAll(List.of(LAMP, SOFA, BULBS)).run(Basket.EMPTY);
    List<String> refused = trip.value();
    // [SOFA]
    BigDecimal tripTotal = trip.state().total();
    // 50.00
    // ANCHOR_END: add_all_outcome
    System.out.println(refused);
    System.out.println(tripTotal);

    // ANCHOR: checkout_outcome
    String receipt = checkout(List.of(LAMP, SOFA, BULBS)).evalState(Basket.EMPTY);
    // ANCHOR_END: checkout_outcome
    System.out.println(receipt);

    // ANCHOR: kind
    Monad<StateKind.Witness<Basket>> baskets = Instances.monad(state());

    Kind<StateKind.Witness<Basket>, Boolean> lamp = STATE.widen(add(LAMP));
    Kind<StateKind.Witness<Basket>, String> label =
        baskets.map(added -> added ? "added" : "refused", lamp);

    String ran = STATE.evalState(label, Basket.EMPTY);
    // ANCHOR_END: kind
    System.out.println(ran);
  }
}
