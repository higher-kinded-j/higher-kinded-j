// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

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
 * Tutorial 15: List Prisms - Functional List Decomposition
 *
 * <p>This tutorial introduces {@link ListPrisms}, a utility class that provides optics for
 * functional list decomposition patterns. These patterns originate from functional programming
 * languages like Haskell and enable elegant, type-safe list manipulation.
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
 *
 * <p>Why these patterns matter:
 *
 * <ul>
 *   <li>Enable functional-style recursive algorithms in Java
 *   <li>Provide safe handling of empty list edge cases
 *   <li>Compose with other optics for deep list manipulation
 *   <li>Stack-safe operations prevent overflow with large data sets
 * </ul>
 *
 * <p>See the documentation: List Decomposition in hkj-book
 *
 * <p>Replace each placeholder with the correct code to make the tests pass.
 */
public class Tutorial15_ListPrisms {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  // =========================================================================
  // SECTION 1: The Cons Pattern (Head/Tail Decomposition)
  // =========================================================================

  /**
   * Exercise 1: Decomposing a list with cons
   *
   * <p>The cons prism decomposes a non-empty list into a Pair of (head, tail) where head is the
   * first element and tail is the remaining list. This is the classic functional programming
   * pattern for list processing.
   *
   * <p>Task: Use the cons prism to extract the head and tail of a list
   */
  @Test
  void exercise1_consDecomposition() {
    Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

    List<String> names = List.of("Alice", "Bob", "Charlie");

    // TODO: Replace null with code that uses cons.getOptional() to decompose the list
    // Hint: cons.getOptional(names)
    Optional<Pair<String, List<String>>> decomposed = answerRequired();

    assertThat(decomposed).isPresent();
    assertThat(decomposed.get().first()).isEqualTo("Alice");
    assertThat(decomposed.get().second()).containsExactly("Bob", "Charlie");
  }

  /**
   * Exercise 2: Cons fails on empty lists
   *
   * <p>The cons prism returns Optional.empty() for empty lists, providing safe handling of the
   * empty case without exceptions.
   *
   * <p>Task: Verify that cons returns empty for an empty list
   */
  @Test
  void exercise2_consOnEmptyList() {
    Prism<List<Integer>, Pair<Integer, List<Integer>>> cons = ListPrisms.cons();

    List<Integer> emptyList = List.of();

    // TODO: Replace null with code that uses cons.getOptional() on the empty list
    Optional<Pair<Integer, List<Integer>>> result = answerRequired();

    assertThat(result).isEmpty();
  }

  /**
   * Exercise 3: Building a list with cons
   *
   * <p>The cons prism can also build a list from a (head, tail) pair using the build method. This
   * is equivalent to prepending an element to a list.
   *
   * <p>Task: Use cons.build() to create a list by prepending an element
   */
  @Test
  void exercise3_buildingWithCons() {
    Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

    Pair<String, List<String>> headTail = Pair.of("First", List.of("Second", "Third"));

    // TODO: Replace null with code that uses cons.build() to create a list
    // Hint: cons.build(headTail)
    List<String> built = answerRequired();

    assertThat(built).containsExactly("First", "Second", "Third");
  }

  // =========================================================================
  // SECTION 2: The Snoc Pattern (Init/Last Decomposition)
  // =========================================================================

  /**
   * Exercise 4: Decomposing a list with snoc
   *
   * <p>The snoc prism (cons spelled backwards) decomposes a non-empty list into a Pair of (init,
   * last) where init is all elements except the last and last is the final element. This pattern is
   * useful when you need to process lists from the end.
   *
   * <p>Task: Use the snoc prism to extract the init and last of a list
   */
  @Test
  void exercise4_snocDecomposition() {
    Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();

    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // TODO: Replace null with code that uses snoc.getOptional() to decompose the list
    Optional<Pair<List<Integer>, Integer>> decomposed = answerRequired();

    assertThat(decomposed).isPresent();
    assertThat(decomposed.get().first()).containsExactly(1, 2, 3, 4);
    assertThat(decomposed.get().second()).isEqualTo(5);
  }

  /**
   * Exercise 5: Building a list with snoc
   *
   * <p>The snoc prism can build a list from an (init, last) pair, effectively appending an element
   * to the end of a list.
   *
   * <p>Task: Use snoc.build() to create a list by appending an element
   */
  @Test
  void exercise5_buildingWithSnoc() {
    Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();

    Pair<List<Integer>, Integer> initLast = Pair.of(List.of(1, 2, 3), 4);

    // TODO: Replace null with code that uses snoc.build() to create a list
    List<Integer> built = answerRequired();

    assertThat(built).containsExactly(1, 2, 3, 4);
  }

  // =========================================================================
  // SECTION 3: Convenience Accessors (head, last, tail, init)
  // =========================================================================

  /**
   * Exercise 6: Accessing the first element with head
   *
   * <p>The head() affine focuses on the first element of a list. Unlike the cons prism which
   * decomposes into a pair, head() gives you direct access to just the first element.
   *
   * <p>Task: Use the head affine to get and modify the first element
   */
  @Test
  void exercise6_headAffine() {
    Affine<List<String>, String> head = ListPrisms.head();

    List<String> words = List.of("hello", "world", "foo");

    // TODO: Replace null with code that uses head.getOptional() to get the first element
    Optional<String> firstWord = answerRequired();

    assertThat(firstWord).contains("hello");

    // TODO: Replace null with code that uses head.modify() to uppercase the first element
    // Hint: head.modify(String::toUpperCase, words)
    List<String> modified = answerRequired();

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

    // TODO: Replace null with code that uses last.getOptional() to get the last element
    Optional<Integer> lastNumber = answerRequired();

    assertThat(lastNumber).contains(40);

    // TODO: Replace null with code that uses last.modify() to double the last element
    // Hint: last.modify(x -> x * 2, numbers)
    List<Integer> modified = answerRequired();

    assertThat(modified).containsExactly(10, 20, 30, 80);
  }

  /**
   * Exercise 8: Setting values on empty lists
   *
   * <p>A key feature of the head and last affines is that set() on an empty list creates a
   * singleton list. This is useful for initialising lists with a first element.
   *
   * <p>Task: Use head.set() to create a list from an empty list
   */
  @Test
  void exercise8_settingOnEmptyList() {
    Affine<List<String>, String> head = ListPrisms.head();

    List<String> emptyList = List.of();

    // TODO: Replace null with code that uses head.set() to create a singleton list
    // Remember: Affine.set() signature is set(newValue, source)
    // Hint: head.set("First", emptyList)
    List<String> singleton = answerRequired();

    assertThat(singleton).containsExactly("First");
  }

  /**
   * Exercise 9: Accessing the tail of a list
   *
   * <p>The tail() affine focuses on all elements except the first. Unlike cons which gives you a
   * Pair, tail gives you just the remaining list.
   *
   * <p>Task: Use the tail affine to get the remaining elements
   */
  @Test
  void exercise9_tailAffine() {
    Affine<List<String>, List<String>> tail = ListPrisms.tail();

    List<String> letters = List.of("a", "b", "c", "d");

    // TODO: Replace null with code that uses tail.getOptional() to get the tail
    Optional<List<String>> theTail = answerRequired();

    assertThat(theTail).isPresent();
    assertThat(theTail.get()).containsExactly("b", "c", "d");

    // Single element list has empty tail
    Optional<List<String>> singleTail = tail.getOptional(List.of("solo"));
    assertThat(singleTail).contains(List.of());
  }

  /**
   * Exercise 10: Accessing the init of a list
   *
   * <p>The init() affine focuses on all elements except the last.
   *
   * <p>Task: Use the init affine to get all but the last element
   */
  @Test
  void exercise10_initAffine() {
    Affine<List<Integer>, List<Integer>> init = ListPrisms.init();

    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // TODO: Replace null with code that uses init.getOptional() to get the init
    Optional<List<Integer>> theInit = answerRequired();

    assertThat(theInit).isPresent();
    assertThat(theInit.get()).containsExactly(1, 2, 3, 4);
  }

  // =========================================================================
  // SECTION 4: The Empty Prism
  // =========================================================================

  /**
   * Exercise 11: Pattern matching with empty
   *
   * <p>The empty() prism matches only empty lists. Combined with cons or snoc, it enables complete
   * pattern matching on list structure, similar to functional programming languages.
   *
   * <p>Task: Use the empty prism for pattern matching
   */
  @Test
  void exercise11_emptyPrism() {
    Prism<List<String>, Unit> empty = ListPrisms.empty();
    Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

    List<String> emptyList = List.of();
    List<String> nonEmptyList = List.of("hello", "world");

    // TODO: Replace false with code that uses empty.matches() to check if list is empty
    // Hint: empty.matches(emptyList)
    boolean isEmptyList = answerRequired();
    boolean isNonEmpty = answerRequired();

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
   * <p>For very large lists, standard recursive operations can cause stack overflow. ListPrisms
   * provides trampoline-based operations that are stack-safe for any list size.
   *
   * <p>Task: Use mapTrampoline to transform a list
   */
  @Test
  void exercise12_stackSafeMap() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // TODO: Replace null with code that uses ListPrisms.mapTrampoline()
    // to double each number
    // Hint: ListPrisms.mapTrampoline(numbers, x -> x * 2)
    List<Integer> doubled = answerRequired();

    assertThat(doubled).containsExactly(2, 4, 6, 8, 10);
  }

  /**
   * Exercise 13: Stack-safe filtering with filterTrampoline
   *
   * <p>The filterTrampoline operation safely filters elements from large lists.
   *
   * <p>Task: Use filterTrampoline to keep only even numbers
   */
  @Test
  void exercise13_stackSafeFilter() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    // TODO: Replace null with code that uses ListPrisms.filterTrampoline()
    // to keep only even numbers
    // Hint: ListPrisms.filterTrampoline(numbers, x -> x % 2 == 0)
    List<Integer> evens = answerRequired();

    assertThat(evens).containsExactly(2, 4, 6, 8, 10);
  }

  /**
   * Exercise 14: Stack-safe folding with foldRight
   *
   * <p>The foldRight operation processes elements from right to left, which is the natural
   * direction for building new lists or computing right-associative operations.
   *
   * <p>Task: Use foldRight to sum a list of numbers
   */
  @Test
  void exercise14_stackSafeFold() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // TODO: Replace null with code that uses ListPrisms.foldRight()
    // to sum all numbers (initial value 0, combine with Integer::sum)
    // Hint: ListPrisms.foldRight(numbers, 0, Integer::sum)
    Integer sum = answerRequired();

    assertThat(sum).isEqualTo(15);
  }

  /**
   * Exercise 15: Stack-safe flatMap with flatMapTrampoline
   *
   * <p>The flatMapTrampoline operation safely expands each element into a list and concatenates the
   * results.
   *
   * <p>Task: Use flatMapTrampoline to duplicate each element
   */
  @Test
  void exercise15_stackSafeFlatMap() {
    List<Integer> numbers = List.of(1, 2, 3);

    // TODO: Replace null with code that uses ListPrisms.flatMapTrampoline()
    // to create [x, x * 10] for each x
    // Hint: ListPrisms.flatMapTrampoline(numbers, x -> List.of(x, x * 10))
    List<Integer> expanded = answerRequired();

    assertThat(expanded).containsExactly(1, 10, 2, 20, 3, 30);
  }

  /**
   * Exercise 16: Stack-safe zip with zipWithTrampoline
   *
   * <p>The zipWithTrampoline operation safely combines two lists element-by-element.
   *
   * <p>Task: Use zipWithTrampoline to combine names and ages
   */
  @Test
  void exercise16_stackSafeZip() {
    List<String> names = List.of("Alice", "Bob", "Charlie");
    List<Integer> ages = List.of(30, 25, 35);

    // TODO: Replace null with code that uses ListPrisms.zipWithTrampoline()
    // to combine names and ages into descriptive strings
    // Hint: ListPrisms.zipWithTrampoline(names, ages, (name, age) -> name + " is " + age)
    List<String> combined = answerRequired();

    assertThat(combined).containsExactly("Alice is 30", "Bob is 25", "Charlie is 35");
  }

  /**
   * Exercise 17: Stack-safe take and drop
   *
   * <p>The takeTrampoline and dropTrampoline operations safely slice lists.
   *
   * <p>Task: Use takeTrampoline and dropTrampoline to slice a list
   */
  @Test
  void exercise17_stackSafeTakeAndDrop() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    // TODO: Replace null with code that uses ListPrisms.takeTrampoline()
    // to get the first 3 elements
    List<Integer> firstThree = answerRequired();

    // TODO: Replace null with code that uses ListPrisms.dropTrampoline()
    // to drop the first 7 elements
    List<Integer> lastThree = answerRequired();

    assertThat(firstThree).containsExactly(1, 2, 3);
    assertThat(lastThree).containsExactly(8, 9, 10);
  }

  /**
   * Exercise 18: Stack-safe reverse
   *
   * <p>The reverseTrampoline operation safely reverses a list.
   *
   * <p>Task: Use reverseTrampoline to reverse a list
   */
  @Test
  void exercise18_stackSafeReverse() {
    List<String> words = List.of("one", "two", "three", "four", "five");

    // TODO: Replace null with code that uses ListPrisms.reverseTrampoline()
    // to reverse the list
    List<String> reversed = answerRequired();

    assertThat(reversed).containsExactly("five", "four", "three", "two", "one");
  }

  // =========================================================================
  // SECTION 6: Combining with Other Optics
  // =========================================================================

  /**
   * Exercise 19: Composing head with other optics
   *
   * <p>The list decomposition optics compose naturally with other optics. This exercise shows how
   * to focus on the first element of a nested list.
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

    // TODO: Replace null with code that uses firstItem.getOptional()
    Optional<String> first = answerRequired();

    assertThat(first).contains("apple");

    // TODO: Replace null with code that uses firstItem.modify() to uppercase the first item
    // Hint: firstItem.modify(String::toUpperCase, container)
    Container modified = answerRequired();

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
