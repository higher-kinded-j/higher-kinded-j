// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.ListPrisms;

/**
 * A runnable example demonstrating how to use ListPrisms for functional list decomposition.
 *
 * <p>ListPrisms provides cons and snoc patterns for decomposing lists from either end:
 *
 * <ul>
 *   <li><b>cons (head/tail)</b>: Decompose as (first element, remaining elements)
 *   <li><b>snoc (init/last)</b>: Decompose as (all but last, last element)
 * </ul>
 *
 * <p>These patterns enable functional programming idioms like pattern matching and recursive
 * algorithms in Java.
 */
public class ListDecompositionExample {

  public static void main(String[] args) {
    System.out.println("=".repeat(60));
    System.out.println("LIST DECOMPOSITION EXAMPLE");
    System.out.println("=".repeat(60));
    System.out.println();

    // Sample data
    List<String> names = List.of("Alice", "Bob", "Charlie", "Diana", "Eve");
    List<Integer> numbers = List.of(1, 2, 3, 4, 5);
    List<String> emptyList = List.of();
    List<String> singleItem = List.of("Solo");

    // =======================================================================
    // SCENARIO 1: Cons pattern - decompose as (head, tail)
    // =======================================================================

    System.out.println("--- Scenario 1: Cons Pattern (head/tail) ---");
    System.out.println();

    Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

    System.out.println("Original list: " + names);
    Optional<Pair<String, List<String>>> consResult = cons.getOptional(names);
    consResult.ifPresent(
        pair -> {
          System.out.println("Head: " + pair.first());
          System.out.println("Tail: " + pair.second());
        });
    System.out.println();

    // Cons on empty list returns Optional.empty()
    System.out.println("Empty list: " + emptyList);
    System.out.println("Cons on empty: " + cons.getOptional(emptyList));
    System.out.println();

    // Cons on single item
    System.out.println("Single item: " + singleItem);
    Optional<Pair<String, List<String>>> singleConsResult = cons.getOptional(singleItem);
    singleConsResult.ifPresent(
        pair -> {
          System.out.println("Head: " + pair.first());
          System.out.println("Tail: " + pair.second() + " (empty)");
        });
    System.out.println();

    // =======================================================================
    // SCENARIO 2: Snoc pattern - decompose as (init, last)
    // =======================================================================

    System.out.println("--- Scenario 2: Snoc Pattern (init/last) ---");
    System.out.println();

    Prism<List<String>, Pair<List<String>, String>> snoc = ListPrisms.snoc();

    System.out.println("Original list: " + names);
    Optional<Pair<List<String>, String>> snocResult = snoc.getOptional(names);
    snocResult.ifPresent(
        pair -> {
          System.out.println("Init: " + pair.first());
          System.out.println("Last: " + pair.second());
        });
    System.out.println();

    // Snoc on single item
    System.out.println("Single item: " + singleItem);
    Optional<Pair<List<String>, String>> singleSnocResult = snoc.getOptional(singleItem);
    singleSnocResult.ifPresent(
        pair -> {
          System.out.println("Init: " + pair.first() + " (empty)");
          System.out.println("Last: " + pair.second());
        });
    System.out.println();

    // =======================================================================
    // SCENARIO 3: Building lists with cons and snoc
    // =======================================================================

    System.out.println("--- Scenario 3: Building Lists ---");
    System.out.println();

    // Build with cons (prepend)
    List<String> prependedList = cons.build(Pair.of("NewFirst", List.of("A", "B", "C")));
    System.out.println("Cons build (prepend): " + prependedList);

    // Build with snoc (append)
    List<String> appendedList = snoc.build(Pair.of(List.of("A", "B", "C"), "NewLast"));
    System.out.println("Snoc build (append): " + appendedList);
    System.out.println();

    // =======================================================================
    // SCENARIO 4: Convenience affines for direct element access
    // =======================================================================

    System.out.println("--- Scenario 4: Convenience Affines ---");
    System.out.println();

    Affine<List<String>, String> head = ListPrisms.head();
    Affine<List<String>, String> last = ListPrisms.last();
    Affine<List<String>, List<String>> tail = ListPrisms.tail();
    Affine<List<String>, List<String>> init = ListPrisms.init();

    System.out.println("List: " + names);
    System.out.println("head: " + head.getOptional(names));
    System.out.println("last: " + last.getOptional(names));
    System.out.println("tail: " + tail.getOptional(names));
    System.out.println("init: " + init.getOptional(names));
    System.out.println();

    System.out.println("Empty list:");
    System.out.println("head on empty: " + head.getOptional(emptyList));
    System.out.println("last on empty: " + last.getOptional(emptyList));
    System.out.println();

    // =======================================================================
    // SCENARIO 5: Modifying elements through affines
    // =======================================================================

    System.out.println("--- Scenario 5: Modifying Elements ---");
    System.out.println();

    // Modify head
    List<String> modifiedHead = head.modify(String::toUpperCase, names);
    System.out.println("Original: " + names);
    System.out.println("After modifying head to uppercase: " + modifiedHead);

    // Modify last
    List<String> modifiedLast = last.modify(s -> s + "!", names);
    System.out.println("After modifying last with exclamation: " + modifiedLast);
    System.out.println();

    // Set head on empty list creates singleton
    List<String> createdFromEmpty = head.set("First", emptyList);
    System.out.println("Set head on empty list: " + createdFromEmpty);
    System.out.println();

    // =======================================================================
    // SCENARIO 6: Matching empty lists
    // =======================================================================

    System.out.println("--- Scenario 6: Matching Empty Lists ---");
    System.out.println();

    System.out.println("names.isEmpty() via cons: " + !cons.matches(names));
    System.out.println("emptyList.isEmpty() via cons: " + !cons.matches(emptyList));
    System.out.println();

    // Using the empty prism
    Prism<List<String>, Unit> empty = ListPrisms.empty();
    System.out.println("empty prism matches names: " + empty.matches(names));
    System.out.println("empty prism matches emptyList: " + empty.matches(emptyList));
    System.out.println();

    // =======================================================================
    // SCENARIO 7: Stack-safe operations for large lists
    // =======================================================================

    System.out.println("--- Scenario 7: Stack-Safe Operations ---");
    System.out.println();

    // Map with trampoline
    List<Integer> doubled = ListPrisms.mapTrampoline(numbers, n -> n * 2);
    System.out.println("Original: " + numbers);
    System.out.println("Doubled with mapTrampoline: " + doubled);

    // Filter with trampoline
    List<Integer> evens = ListPrisms.filterTrampoline(numbers, n -> n % 2 == 0);
    System.out.println("Evens with filterTrampoline: " + evens);

    // Reverse with trampoline
    List<Integer> reversed = ListPrisms.reverseTrampoline(numbers);
    System.out.println("Reversed with reverseTrampoline: " + reversed);

    // FoldRight with trampoline
    Integer sum = ListPrisms.foldRight(numbers, 0, Integer::sum);
    System.out.println("Sum with foldRight: " + sum);
    System.out.println();

    // =======================================================================
    // SCENARIO 8: Composing with other optics
    // =======================================================================

    System.out.println("--- Scenario 8: Composing with Other Optics ---");
    System.out.println();

    record Team(String name, List<String> players) {}

    // Create a lens for the players field
    var playersLens = Lens.<Team, List<String>>of(Team::players, (t, p) -> new Team(t.name(), p));

    // Compose lens with head affine to get the captain (first player)
    Affine<Team, String> captainAffine = playersLens.andThen(ListPrisms.head());

    Team team = new Team("Eagles", List.of("Captain Kirk", "Spock", "McCoy"));

    System.out.println("Team: " + team);
    System.out.println("Captain: " + captainAffine.getOptional(team));

    // Modify the captain's name
    Team updatedTeam = captainAffine.modify(String::toUpperCase, team);
    System.out.println("After uppercasing captain: " + updatedTeam);
    System.out.println();

    // =======================================================================
    // SCENARIO 9: Pattern matching example
    // =======================================================================

    System.out.println("--- Scenario 9: Pattern Matching with Prisms ---");
    System.out.println();

    // Describe a list using pattern matching
    System.out.println("Describing lists:");
    System.out.println("  " + names + " -> " + describeList(names));
    System.out.println("  " + singleItem + " -> " + describeList(singleItem));
    System.out.println("  " + emptyList + " -> " + describeList(emptyList));
    System.out.println();

    System.out.println("=".repeat(60));
    System.out.println("Example complete!");
    System.out.println("=".repeat(60));
  }

  /**
   * Describes a list using pattern matching via prisms.
   *
   * @param list the list to describe
   * @param <A> the element type
   * @return a description of the list structure
   */
  private static <A> String describeList(List<A> list) {
    if (ListPrisms.<A>empty().matches(list)) {
      return "Empty list";
    }

    Pair<A, List<A>> headTail = ListPrisms.<A>cons().getOptional(list).orElseThrow();

    if (headTail.second().isEmpty()) {
      return "Single element: " + headTail.first();
    }

    return "List starting with "
        + headTail.first()
        + ", followed by "
        + headTail.second().size()
        + " more elements";
  }
}
