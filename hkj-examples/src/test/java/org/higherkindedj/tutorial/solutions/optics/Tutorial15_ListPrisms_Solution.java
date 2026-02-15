// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.ListPrisms;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 15: List Prisms - Functional List Decomposition (Solutions)
 *
 * <p>This file contains the complete solutions for all exercises in Tutorial 15.
 *
 * <p>Key Concepts:
 *
 * <ul>
 *   <li><b>cons (head/tail)</b>: Decompose a list as (first element, remaining elements)
 *   <li><b>snoc (init/last)</b>: Decompose a list as (all but last, last element)
 *   <li><b>head/last</b>: Affines for accessing first/last elements
 *   <li><b>tail/init</b>: Prisms for accessing the rest of a list
 *   <li><b>empty</b>: Prism for matching empty lists
 *   <li><b>Stack-safe operations</b>: Trampoline-based functions for large lists
 * </ul>
 */
public class Tutorial15_ListPrisms_Solution {

  // =========================================================================
  // SECTION 1: The Cons Pattern (Head/Tail Decomposition)
  // =========================================================================

  /**
   * Exercise 1: Decomposing a list with cons
   *
   * <p>The cons prism decomposes a non-empty list into a Pair of (head, tail) where head is the
   * first element and tail is the remaining list.
   *
   * <p>Task: Use the cons prism to extract the head and tail of a list
   */
  @Test
  void exercise1_consDecomposition() {
    Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

    List<String> names = List.of("Alice", "Bob", "Charlie");

    // SOLUTION: Use cons.getOptional() to decompose the list
    Optional<Pair<String, List<String>>> decomposed = cons.getOptional(names);

    assertThat(decomposed).isPresent();
    assertThat(decomposed.get().first()).isEqualTo("Alice");
    assertThat(decomposed.get().second()).containsExactly("Bob", "Charlie");
  }

  /**
   * Exercise 2: Cons fails on empty lists
   *
   * <p>The cons prism returns Optional.empty() for empty lists.
   *
   * <p>Task: Verify that cons returns empty for an empty list
   */
  @Test
  void exercise2_consOnEmptyList() {
    Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();

    List<Integer> emptyList = List.of();

    // SOLUTION: cons returns empty for empty lists
    Optional<Pair<Integer, List<Integer>>> result = cons.getOptional(emptyList);

    assertThat(result).isEmpty();
  }

  /**
   * Exercise 3: Building a list with cons
   *
   * <p>The cons prism can also build a list from a (head, tail) pair using the build method.
   *
   * <p>Task: Use cons.build() to create a list by prepending an element
   */
  @Test
  void exercise3_buildingWithCons() {
    Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

    Pair<String, List<String>> headTail = Pair.of("First", List.of("Second", "Third"));

    // SOLUTION: Use cons.build() to create a list from a (head, tail) pair
    List<String> built = cons.build(headTail);

    assertThat(built).containsExactly("First", "Second", "Third");
  }

  // =========================================================================
  // SECTION 2: The Snoc Pattern (Init/Last Decomposition)
  // =========================================================================

  /**
   * Exercise 4: Decomposing a list with snoc
   *
   * <p>The snoc prism (cons spelled backwards) decomposes a non-empty list into a Pair of (init,
   * last).
   *
   * <p>Task: Use the snoc prism to extract the init and last of a list
   */
  @Test
  void exercise4_snocDecomposition() {
    Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();

    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // SOLUTION: Use snoc.getOptional() to decompose the list
    Optional<Pair<List<Integer>, Integer>> decomposed = snoc.getOptional(numbers);

    assertThat(decomposed).isPresent();
    assertThat(decomposed.get().first()).containsExactly(1, 2, 3, 4);
    assertThat(decomposed.get().second()).isEqualTo(5);
  }

  /**
   * Exercise 5: Building a list with snoc
   *
   * <p>The snoc prism can build a list from an (init, last) pair.
   *
   * <p>Task: Use snoc.build() to create a list by appending an element
   */
  @Test
  void exercise5_buildingWithSnoc() {
    Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();

    Pair<List<Integer>, Integer> initLast = Pair.of(List.of(1, 2, 3), 4);

    // SOLUTION: Use snoc.build() to create a list from an (init, last) pair
    List<Integer> built = snoc.build(initLast);

    assertThat(built).containsExactly(1, 2, 3, 4);
  }

  // =========================================================================
  // SECTION 3: Convenience Accessors (head, last, tail, init)
  // =========================================================================

  /**
   * Exercise 6: Accessing the first element with head
   *
   * <p>The head() affine focuses on the first element of a list.
   *
   * <p>Task: Use the head affine to get and modify the first element
   */
  @Test
  void exercise6_headAffine() {
    Affine<List<String>, String> head = ListPrisms.head();

    List<String> words = List.of("hello", "world", "foo");

    // SOLUTION: Use head.getOptional() to get the first element
    Optional<String> firstWord = head.getOptional(words);

    assertThat(firstWord).contains("hello");

    // SOLUTION: Use head.modify() to uppercase the first element
    List<String> modified = head.modify(String::toUpperCase, words);

    assertThat(modified).containsExactly("HELLO", "world", "foo");
  }

  /**
   * Exercise 7: Accessing the last element
   *
   * <p>The last() affine focuses on the last element of a list.
   *
   * <p>Task: Use the last affine to get and modify the last element
   */
  @Test
  void exercise7_lastAffine() {
    Affine<List<Integer>, Integer> last = ListPrisms.last();

    List<Integer> numbers = List.of(10, 20, 30, 40);

    // SOLUTION: Use last.getOptional() to get the last element
    Optional<Integer> lastNumber = last.getOptional(numbers);

    assertThat(lastNumber).contains(40);

    // SOLUTION: Use last.modify() to double the last element
    List<Integer> modified = last.modify(x -> x * 2, numbers);

    assertThat(modified).containsExactly(10, 20, 30, 80);
  }

  /**
   * Exercise 8: Setting values on empty lists
   *
   * <p>The head and last affines create singleton lists when setting on empty lists.
   *
   * <p>Task: Use head.set() to create a list from an empty list
   */
  @Test
  void exercise8_settingOnEmptyList() {
    Affine<List<String>, String> head = ListPrisms.head();

    List<String> emptyList = List.of();

    // SOLUTION: head.set() on empty list creates a singleton
    // Note: Affine.set() signature is set(newValue, source)
    List<String> singleton = head.set("First", emptyList);

    assertThat(singleton).containsExactly("First");
  }

  /**
   * Exercise 9: Accessing the tail of a list
   *
   * <p>The tail() prism focuses on all elements except the first.
   *
   * <p>Task: Use the tail affine to get the remaining elements
   */
  @Test
  void exercise9_tailAffine() {
    Affine<List<String>, List<String>> tail = ListPrisms.tail();

    List<String> letters = List.of("a", "b", "c", "d");

    // SOLUTION: Use tail.getOptional() to get the tail
    Optional<List<String>> theTail = tail.getOptional(letters);

    assertThat(theTail).isPresent();
    assertThat(theTail.get()).containsExactly("b", "c", "d");

    // Single element list has empty tail
    Optional<List<String>> singleTail = tail.getOptional(List.of("solo"));
    assertThat(singleTail).contains(List.of());
  }

  /**
   * Exercise 10: Accessing the init of a list
   *
   * <p>The init() prism focuses on all elements except the last.
   *
   * <p>Task: Use the init affine to get all but the last element
   */
  @Test
  void exercise10_initAffine() {
    Affine<List<Integer>, List<Integer>> init = ListPrisms.init();

    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // SOLUTION: Use init.getOptional() to get the init
    Optional<List<Integer>> theInit = init.getOptional(numbers);

    assertThat(theInit).isPresent();
    assertThat(theInit.get()).containsExactly(1, 2, 3, 4);
  }

  // =========================================================================
  // SECTION 4: The Empty Prism
  // =========================================================================

  /**
   * Exercise 11: Pattern matching with empty
   *
   * <p>The empty() prism matches only empty lists.
   *
   * <p>Task: Use the empty prism for pattern matching
   */
  @Test
  void exercise11_emptyPrism() {
    Prism<List<String>, Unit> empty = ListPrisms.empty();
    Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

    List<String> emptyList = List.of();
    List<String> nonEmptyList = List.of("hello", "world");

    // SOLUTION: Use empty.matches() to check if list is empty
    boolean isEmptyList = empty.matches(emptyList);
    boolean isNonEmpty = empty.matches(nonEmptyList);

    assertThat(isEmptyList).isTrue();
    assertThat(isNonEmpty).isFalse();

    // Pattern matching style: check empty first, then decompose with cons
    String describe =
        empty.matches(nonEmptyList)
            ? "Empty list"
            : cons.getOptional(nonEmptyList)
                .map(p -> "Head: " + p.first() + ", Tail size: " + p.second().size())
                .orElse("Unknown");

    assertThat(describe).isEqualTo("Head: hello, Tail size: 1");
  }

  // =========================================================================
  // SECTION 5: Stack-Safe Operations
  // =========================================================================

  /**
   * Exercise 12: Stack-safe mapping with mapTrampoline
   *
   * <p>Task: Use mapTrampoline to transform a list
   */
  @Test
  void exercise12_stackSafeMap() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // SOLUTION: Use ListPrisms.mapTrampoline() to double each number
    List<Integer> doubled = ListPrisms.mapTrampoline(numbers, x -> x * 2);

    assertThat(doubled).containsExactly(2, 4, 6, 8, 10);
  }

  /**
   * Exercise 13: Stack-safe filtering with filterTrampoline
   *
   * <p>Task: Use filterTrampoline to keep only even numbers
   */
  @Test
  void exercise13_stackSafeFilter() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    // SOLUTION: Use ListPrisms.filterTrampoline() to keep only even numbers
    List<Integer> evens = ListPrisms.filterTrampoline(numbers, x -> x % 2 == 0);

    assertThat(evens).containsExactly(2, 4, 6, 8, 10);
  }

  /**
   * Exercise 14: Stack-safe folding with foldRight
   *
   * <p>Task: Use foldRight to sum a list of numbers
   */
  @Test
  void exercise14_stackSafeFold() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // SOLUTION: Use ListPrisms.foldRight() to sum all numbers
    Integer sum = ListPrisms.foldRight(numbers, 0, Integer::sum);

    assertThat(sum).isEqualTo(15);
  }

  /**
   * Exercise 15: Stack-safe flatMap with flatMapTrampoline
   *
   * <p>Task: Use flatMapTrampoline to duplicate each element
   */
  @Test
  void exercise15_stackSafeFlatMap() {
    List<Integer> numbers = List.of(1, 2, 3);

    // SOLUTION: Use ListPrisms.flatMapTrampoline() to create [x, x * 10] for each x
    List<Integer> expanded = ListPrisms.flatMapTrampoline(numbers, x -> List.of(x, x * 10));

    assertThat(expanded).containsExactly(1, 10, 2, 20, 3, 30);
  }

  /**
   * Exercise 16: Stack-safe zip with zipWithTrampoline
   *
   * <p>Task: Use zipWithTrampoline to combine names and ages
   */
  @Test
  void exercise16_stackSafeZip() {
    List<String> names = List.of("Alice", "Bob", "Charlie");
    List<Integer> ages = List.of(30, 25, 35);

    // SOLUTION: Use ListPrisms.zipWithTrampoline() to combine names and ages
    List<String> combined =
        ListPrisms.zipWithTrampoline(names, ages, (name, age) -> name + " is " + age);

    assertThat(combined).containsExactly("Alice is 30", "Bob is 25", "Charlie is 35");
  }

  /**
   * Exercise 17: Stack-safe take and drop
   *
   * <p>Task: Use takeTrampoline and dropTrampoline to slice a list
   */
  @Test
  void exercise17_stackSafeTakeAndDrop() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    // SOLUTION: Use ListPrisms.takeTrampoline() to get the first 3 elements
    List<Integer> firstThree = ListPrisms.takeTrampoline(numbers, 3);

    // SOLUTION: Use ListPrisms.dropTrampoline() to drop the first 7 elements
    List<Integer> lastThree = ListPrisms.dropTrampoline(numbers, 7);

    assertThat(firstThree).containsExactly(1, 2, 3);
    assertThat(lastThree).containsExactly(8, 9, 10);
  }

  /**
   * Exercise 18: Stack-safe reverse
   *
   * <p>Task: Use reverseTrampoline to reverse a list
   */
  @Test
  void exercise18_stackSafeReverse() {
    List<String> words = List.of("one", "two", "three", "four", "five");

    // SOLUTION: Use ListPrisms.reverseTrampoline() to reverse the list
    List<String> reversed = ListPrisms.reverseTrampoline(words);

    assertThat(reversed).containsExactly("five", "four", "three", "two", "one");
  }

  // =========================================================================
  // SECTION 6: Combining with Other Optics
  // =========================================================================

  /**
   * Exercise 19: Composing head with other optics
   *
   * <p>Task: Compose a lens with head to access a nested list's first element
   */
  @Test
  void exercise19_composingWithHead() {
    record Container(List<String> items) {}

    // Manually create a lens to the items field
    var itemsLens =
        Lens.<Container, List<String>>of(
            Container::items, (container, items) -> new Container(items));

    Affine<List<String>, String> head = ListPrisms.head();

    // Compose: Container -> List<String> -> String (first element)
    Affine<Container, String> firstItem = itemsLens.andThen(head);

    Container container = new Container(List.of("apple", "banana", "cherry"));

    // SOLUTION: Use firstItem.getOptional() to get the first item
    Optional<String> first = firstItem.getOptional(container);

    assertThat(first).contains("apple");

    // SOLUTION: Use firstItem.modify() to uppercase the first item
    Container modified = firstItem.modify(String::toUpperCase, container);

    assertThat(modified.items()).containsExactly("APPLE", "banana", "cherry");
  }

  /**
   * Congratulations! You've completed Tutorial 15: List Prisms
   *
   * <p>You now understand:
   *
   * <ul>
   *   <li>How to use cons() for (head, tail) decomposition
   *   <li>How to use snoc() for (init, last) decomposition
   *   <li>How to use head() and last() affines for element access
   *   <li>How to use tail() and init() prisms for sublist access
   *   <li>How to use empty() for pattern matching on list structure
   *   <li>How to use stack-safe trampoline operations for large lists
   *   <li>How to compose list optics with other optics
   * </ul>
   *
   * <p>Key Takeaways:
   *
   * <ul>
   *   <li>List decomposition patterns enable functional-style list processing in Java
   *   <li>All operations safely handle empty lists via Optional
   *   <li>Trampoline operations prevent stack overflow with large lists
   *   <li>These optics compose naturally with lenses, prisms, and affines
   * </ul>
   *
   * <p>This completes the Optics tutorial series!
   */
}
