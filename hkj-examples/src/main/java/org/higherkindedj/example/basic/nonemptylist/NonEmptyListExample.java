// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.nonemptylist;

import java.util.Comparator;
import java.util.List;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;

/**
 * Demonstrates {@link NonEmptyList} — a list guaranteed to contain at least one element.
 *
 * <p>Covers construction, the total accessors that never throw ({@code head} / {@code last} /
 * {@code reduce} / {@code min} / {@code max}), checked construction from possibly-empty data via
 * {@link Maybe}, fluent transforms, and its use as a streamlined validation error channel.
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.basic.nonemptylist.NonEmptyListExample}
 */
public class NonEmptyListExample {

  public static void main(String[] args) {
    System.out.println("=== NonEmptyList ===\n");

    construction();
    totalAccessors();
    checkedConstruction();
    fluentTransforms();
    validationChannel();
  }

  private static void construction() {
    System.out.println("--- Construction ---");
    NonEmptyList<Integer> a = NonEmptyList.of(1, 2, 3); // head + varargs tail
    NonEmptyList<Integer> b = NonEmptyList.of(1, List.of(2, 3)); // head + explicit tail
    NonEmptyList<Integer> c = NonEmptyList.single(42); // single element

    System.out.println("of(1, 2, 3)      = " + a);
    System.out.println("of(1, [2, 3])    = " + b);
    System.out.println("single(42)       = " + c);
    System.out.println();
  }

  private static void totalAccessors() {
    System.out.println("--- Total accessors (never throw) ---");
    NonEmptyList<Integer> nel = NonEmptyList.of(3, 1, 2);

    System.out.println("head           = " + nel.head()); // 3
    System.out.println("last           = " + nel.last()); // 2
    System.out.println("reduce(+)      = " + nel.reduce((x, y) -> x + y)); // 6
    System.out.println("min            = " + nel.min(Comparator.naturalOrder())); // 1
    System.out.println("max            = " + nel.max(Comparator.naturalOrder())); // 3
    System.out.println("toJavaList     = " + nel.toJavaList()); // [3, 1, 2]
    System.out.println();
  }

  private static void checkedConstruction() {
    System.out.println("--- Checked construction (never throws) ---");
    Maybe<NonEmptyList<Integer>> fromNonEmpty = NonEmptyList.fromList(List.of(1, 2, 3));
    Maybe<NonEmptyList<Integer>> fromEmpty = NonEmptyList.fromList(List.of());

    System.out.println("fromList([1,2,3]) = " + fromNonEmpty); // Just(NonEmptyList[1, 2, 3])
    System.out.println("fromList([])      = " + fromEmpty); // Nothing
    System.out.println();
  }

  private static void fluentTransforms() {
    System.out.println("--- Fluent transforms (no Kind required) ---");
    NonEmptyList<Integer> nel = NonEmptyList.of(1, 2, 3);

    System.out.println("map(*10)            = " + nel.map(n -> n * 10));
    System.out.println("flatMap(n->[n,-n])  = " + nel.flatMap(n -> NonEmptyList.of(n, -n)));
    System.out.println("reverse             = " + nel.reverse());
    System.out.println();
  }

  private static void validationChannel() {
    System.out.println("--- Validation error channel ---");

    // A single-error leaf wraps its error in a singleton NonEmptyList — no Semigroup argument.
    ValidationPath<NonEmptyList<String>, String> name = Path.invalidNel("name is blank");
    ValidationPath<NonEmptyList<String>, String> email = Path.invalidNel("email is invalid");

    // Accumulation just concatenates the two NonEmptyLists, left-to-right.
    ValidationPath<NonEmptyList<String>, String> both = name.andAlso(email);

    System.out.println("accumulated errors = " + both.run().getError().toJavaList());
    // [name is blank, email is invalid]
    System.out.println("first error (total) = " + both.run().getError().head());
    // name is blank

    ValidationPath<NonEmptyList<String>, Integer> ok = Path.validNel(42);
    System.out.println("valid              = " + ok.run());
    System.out.println();
  }
}
