// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.pcollections;

import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.pcollections.ConsPStack;
import org.pcollections.PStack;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

/**
 * Demonstrates that PCollections persistent data structures interoperate with the existing {@code
 * ListKind} HKT pipeline through their {@link java.util.List} compatibility.
 *
 * <p>Run with:
 *
 * <pre>{@code
 * ./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.basic.pcollections.PCollectionsExample
 * }</pre>
 *
 * <p>Phase 1 caveat: although a {@link PVector} or {@link PStack} can be widened directly into
 * {@link ListKind}, operations like {@code map} and {@code flatMap} return a JDK {@link List}.
 * Preserving the persistent type end-to-end is the subject of later phases.
 */
public final class PCollectionsExample {

  private PCollectionsExample() {}

  public static void main(String[] args) {
    System.out.println("=== PCollections × Higher-Kinded-J (Phase 1) ===\n");

    widenAndMap();
    flatMapAcrossPVectorAndPStack();
    traverseValidatesEntireVector();
    foldMapAggregates();
  }

  /** Wrap a {@link PVector} as a {@code Kind<ListKind.Witness, A>} and map a function over it. */
  private static void widenAndMap() {
    System.out.println("--- Functor: map over a PVector ---");

    PVector<Integer> prices = TreePVector.from(List.of(100, 250, 999));
    Kind<ListKind.Witness, Integer> kind = LIST.widen(prices);

    Kind<ListKind.Witness, String> formatted =
        Instances.monadZero(list()).map(p -> String.format("£%.2f", p / 100.0), kind);

    System.out.println("source PVector  : " + prices);
    System.out.println("after map (List): " + LIST.narrow(formatted));
    System.out.println();
  }

  /** flatMap can mix PVector and PStack inputs without any code change. */
  private static void flatMapAcrossPVectorAndPStack() {
    System.out.println("--- Monad: flatMap mixing PVector and PStack ---");

    Kind<ListKind.Witness, Integer> orders = LIST.widen(TreePVector.from(List.of(1, 2, 3)));

    Function<Integer, Kind<ListKind.Witness, String>> expandViaPStack =
        id -> LIST.widen(ConsPStack.from(List.of("order-" + id + "-A", "order-" + id + "-B")));

    Kind<ListKind.Witness, String> expanded =
        Instances.monadZero(list()).flatMap(expandViaPStack, orders);

    System.out.println("orders   : " + LIST.narrow(orders));
    System.out.println("expanded : " + LIST.narrow(expanded));
    System.out.println();
  }

  /** traverse over a PVector with the Optional applicative gives all-or-nothing validation. */
  private static void traverseValidatesEntireVector() {
    System.out.println("--- Traverse: validate every element of a PVector ---");

    Kind<ListKind.Witness, Integer> goodInputs = LIST.widen(TreePVector.from(List.of(1, 2, 3, 4)));
    Kind<ListKind.Witness, Integer> badInputs = LIST.widen(TreePVector.from(List.of(1, 2, -3, 4)));

    Function<Integer, Kind<OptionalKind.Witness, Integer>> mustBePositive =
        i -> OPTIONAL.widen(i > 0 ? Optional.of(i) : Optional.empty());

    Kind<OptionalKind.Witness, Kind<ListKind.Witness, Integer>> good =
        ListTraverse.INSTANCE.traverse(
            Instances.monadError(optional()), mustBePositive, goodInputs);
    Kind<OptionalKind.Witness, Kind<ListKind.Witness, Integer>> bad =
        ListTraverse.INSTANCE.traverse(Instances.monadError(optional()), mustBePositive, badInputs);

    System.out.println("good : " + OPTIONAL.narrow(good).map(LIST::narrow));
    System.out.println("bad  : " + OPTIONAL.narrow(bad).map(LIST::narrow));
    System.out.println();
  }

  /** foldMap aggregates a PVector into a monoid. */
  private static void foldMapAggregates() {
    System.out.println("--- Foldable: foldMap with sum monoid ---");

    PVector<Integer> nums = TreePVector.from(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
    Kind<ListKind.Witness, Integer> kind = LIST.widen(nums);

    int total = ListTraverse.INSTANCE.foldMap(Monoids.integerAddition(), i -> i, kind);

    System.out.println("PVector       : " + nums);
    System.out.println("foldMap (sum) : " + total);
    System.out.println();
  }
}
