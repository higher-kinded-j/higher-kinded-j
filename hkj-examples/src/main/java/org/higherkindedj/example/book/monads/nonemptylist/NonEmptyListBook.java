// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.monads.nonemptylist;

import static org.higherkindedj.hkt.instances.Witnesses.nonEmptyList;

import java.util.Comparator;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.nonemptylist.NonEmptyListKind;
import org.higherkindedj.hkt.validated.Validated;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/monads/nonemptylist_monad.html">NonEmptyList</a> page.
 * The page {@code {{#include}}}s the anchored regions, so it cannot drift from the API.
 */
public final class NonEmptyListBook {

  private NonEmptyListBook() {}

  public static void main(String[] args) {
    ValidationError error = new ValidationError("boom");

    // ANCHOR: partial
    // An "invalid" result always has at least one error, yet the type allows zero.
    ValidationPath<List<ValidationError>, User> failure =
        Path.invalid(List.of(error), Semigroups.list());
    ValidationError first =
        failure.run().getError().getFirst(); // partial: getFirst() throws on empty
    // ANCHOR_END: partial
    System.out.println(first);

    // ANCHOR: total
    // NonEmptyList error channel: no Semigroup argument, no List.of wrapping.
    ValidationPath<NonEmptyList<ValidationError>, User> nelFailure = Path.invalidNel(error);
    ValidationError head =
        nelFailure.run().getError().head(); // total: never throws, returns ValidationError
    // ANCHOR_END: total
    System.out.println(head);

    // ANCHOR: construct
    NonEmptyList<Integer> a = NonEmptyList.of(1, 2, 3); // head + varargs tail
    NonEmptyList<Integer> b = NonEmptyList.of(1, List.of(2)); // head + explicit tail
    NonEmptyList<Integer> c = NonEmptyList.single(42); // single element
    // ANCHOR_END: construct
    System.out.println(a + " " + b + " " + c);

    // ANCHOR: from_list
    Maybe<NonEmptyList<Integer>> maybe = NonEmptyList.fromList(List.of(1, 2, 3)); // Just([1, 2, 3])
    Maybe<NonEmptyList<Integer>> none = NonEmptyList.fromList(List.of()); // Nothing
    // ANCHOR_END: from_list
    System.out.println(maybe + " " + none);

    totalOps();
    accumulation();
    validatedAndInstances();
  }

  /** Its own scope, so the page can show `first` rather than a de-collided `first3`. */
  static void totalOps() {
    // ANCHOR: total_ops
    NonEmptyList<Integer> nel = NonEmptyList.of(3, 1, 2);

    int first = nel.head(); // 3   (never throws)
    int last = nel.last(); // 2   (never throws)
    int sum = nel.reduce((x, y) -> x + y); // 6   (reduce without an identity)
    int min = nel.min(Comparator.naturalOrder()); // 1
    int max = nel.max(Comparator.naturalOrder()); // 3
    // ANCHOR_END: total_ops
    System.out.println(first + " " + last + " " + sum + " " + min + " " + max);
  }

  static void accumulation() {
    // ANCHOR: accumulate
    // A single-error leaf wraps its error in a singleton NonEmptyList; that is the whole idiom.
    ValidationPath<NonEmptyList<String>, String> name = Path.invalidNel("name is blank");
    ValidationPath<NonEmptyList<String>, String> email = Path.invalidNel("email is invalid");

    // Accumulation just concatenates the two NonEmptyLists, non-empty by construction.
    ValidationPath<NonEmptyList<String>, String> both = name.andAlso(email);
    both.run().getError().toJavaList(); // ["name is blank", "email is invalid"]  (left-to-right)
    // ANCHOR_END: accumulate
    System.out.println(both.run().getError().toJavaList());
  }

  static void validatedAndInstances() {
    // ANCHOR: validated_nel
    Validated<NonEmptyList<String>, Integer> ok = Validated.validNel(42);
    Validated<NonEmptyList<String>, Integer> bad = Validated.invalidNel("must be positive");
    // ANCHOR_END: validated_nel
    System.out.println(ok + " " + bad);

    // ANCHOR: instances
    Monad<NonEmptyListKind.Witness> monad = Instances.monad(nonEmptyList());
    Kind<NonEmptyListKind.Witness, Integer> lifted = monad.of(1);
    // ANCHOR_END: instances
    System.out.println(lifted);
  }
}

/** The page's illustrative domain error. */
record ValidationError(String message) {}

record User(String name) {}
