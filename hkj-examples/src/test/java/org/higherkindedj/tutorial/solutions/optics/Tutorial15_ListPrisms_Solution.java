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
 * Solution for Tutorial15 ListPrisms — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial15_ListPrisms_Solution {

  // =========================================================================
  // SECTION 1: The Cons Pattern (Head/Tail Decomposition)
  // =========================================================================

  /**
   * Why this is idiomatic: {@code ListPrisms.cons()} brings the classic functional head/tail
   * decomposition into Java. {@code getOptional} answers "if the list is non-empty, give me the
   * pair"; the type forces empty-list handling.
   *
   * <p>Alternative: {@code list.get(0)} and {@code list.subList(1, list.size())}. Same runtime;
   * throws on empty lists, while the prism returns {@code Optional.empty()}.
   *
   * <p>Common wrong attempt: assume the tail is always non-empty. A two-element list decomposes
   * into a head and a one-element tail; the prism does not promise length.
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
   * Why this is idiomatic: cons on an empty list is partial — it returns {@code Optional.empty()}.
   * The type carries the absence forward, no exception thrown.
   *
   * <p>Alternative: {@code if (list.isEmpty())} guards before calling. Equivalent; the prism's
   * partiality removes the need for ad-hoc guards.
   *
   * <p>Common wrong attempt: assume cons returns a pair with a {@code null} head for empties. The
   * prism never returns {@code null}; the result is {@code Optional.empty()}.
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
   * Why this is idiomatic: {@code cons.build(pair)} is the symmetric partner — given a head and a
   * tail, produce a list. Total in the write direction; the head is always prepended.
   *
   * <p>Alternative: a manual {@code List.of(head, ...)}-style concatenation. Equivalent runtime;
   * the prism's {@code build} is the named pair-of-cons constructor.
   *
   * <p>Common wrong attempt: assume {@code build} returns the same {@code List} instance each time.
   * The lists are immutable and {@code build} returns a fresh list each call.
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
   * Why this is idiomatic: snoc is cons read backwards — decompose into (init, last) rather than
   * (head, tail). Useful when the algorithm naturally examines the trailing element.
   *
   * <p>Alternative: {@code list.get(list.size() - 1)} and {@code subList(0, size - 1)}. Same
   * runtime; throws on empty lists, while snoc returns {@code Optional.empty()}.
   *
   * <p>Common wrong attempt: confuse cons and snoc when reaching for "last element". Cons exposes
   * the head; snoc exposes the last. Pick the one that matches the algorithm's intent.
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
   * Why this is idiomatic: {@code snoc.build(pair)} appends the last element to the init — total in
   * the write direction, mirroring cons.
   *
   * <p>Alternative: a {@code Stream.concat(...)} or {@code ArrayList.add(last)}. The prism version
   * is one named call and stays immutable.
   *
   * <p>Common wrong attempt: pass {@code (last, init)} the wrong way around. The pair stores init
   * first, last second — read {@code Pair.of(init, last)} once and remember.
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
   * Why this is idiomatic: {@code ListPrisms.head()} packages "first element" as an affine. Reads
   * return {@code Optional}, modifies leave the rest of the list intact.
   *
   * <p>Alternative: cons + {@code map} on the head. Same answer; {@code head} is the named
   * accessor.
   *
   * <p>Common wrong attempt: assume {@code head} returns the first element directly. It returns an
   * affine — call {@code getOptional} or {@code modify} to use it.
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
   * Why this is idiomatic: {@code ListPrisms.last()} is the symmetric companion to {@code head}.
   * {@code modify} touches only the trailing element; the rest of the list stays unchanged.
   *
   * <p>Alternative: snoc + {@code map} on the last. Same answer; {@code last} is the named
   * accessor.
   *
   * <p>Common wrong attempt: assume modifying the last is O(1). For an immutable {@code List} the
   * rebuild is O(n) — unavoidable without mutation, but worth knowing.
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
   * Why this is idiomatic: {@code head.set} on an empty list materialises the singleton {@code
   * [value]}. The affine's write side is total — no special-case handling needed.
   *
   * <p>Alternative: {@code list.isEmpty() ? List.of(v) : ...}. The affine's unconditional set hides
   * this branching for you.
   *
   * <p>Common wrong attempt: assume the affine refuses empty inputs. It accepts and grows the list
   * to a singleton; {@code modify} is the operation that no-ops on empty.
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
   * Why this is idiomatic: {@code tail} returns "all but the first" as an affine. A single-element
   * list has an empty tail; an empty list has no tail at all.
   *
   * <p>Alternative: {@code list.subList(1, list.size())}. Equivalent for non-empty lists; throws
   * for empty, while the affine returns {@code Optional.empty()}.
   *
   * <p>Common wrong attempt: confuse "empty tail" with "no tail". A singleton has the empty list as
   * its tail — present but empty.
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
   * Why this is idiomatic: {@code init} mirrors {@code tail} on the other end — every element
   * except the last. Used in algorithms that consume from the back without special-casing
   * single-element lists.
   *
   * <p>Alternative: {@code list.subList(0, list.size() - 1)}. Equivalent; throws on empty, while
   * the affine returns {@code Optional.empty()}.
   *
   * <p>Common wrong attempt: mix up {@code init} and {@code tail}. {@code init} drops the last;
   * {@code tail} drops the first.
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
   * Why this is idiomatic: pair {@code empty} and {@code cons} for full pattern matching. Empty
   * fires its branch; non-empty falls through to the cons decomposition.
   *
   * <p>Alternative: {@code list.isEmpty()} guard plus {@code list.get(0)}. Same answer; the prism
   * pair lifts both into the optic vocabulary.
   *
   * <p>Common wrong attempt: rely on {@code empty.build(Unit.INSTANCE)} to produce a non-empty
   * list. It always builds an empty list — the prism is one-way like {@code only} and {@code
   * nearly}.
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
   * Why this is idiomatic: {@code mapTrampoline} produces the same result as {@code
   * stream().map(...).toList()} but without consuming JVM stack frames per element. The trampoline
   * shape is what makes it safe for very large lists.
   *
   * <p>Alternative: {@code list.stream().map(fn).toList()}. Same answer for medium inputs; the
   * trampoline form scales to a million elements without overflow.
   *
   * <p>Common wrong attempt: assume the trampoline is always faster. For small lists the stream
   * version wins on constant overhead; reach for the trampoline when stack safety matters.
   */
  @Test
  void exercise12_stackSafeMap() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // SOLUTION: Use ListPrisms.mapTrampoline() to double each number
    List<Integer> doubled = ListPrisms.mapTrampoline(numbers, x -> x * 2);

    assertThat(doubled).containsExactly(2, 4, 6, 8, 10);
  }

  /**
   * Why this is idiomatic: {@code filterTrampoline} keeps only the elements the predicate accepts
   * and rebuilds the list without recursion. The trampoline is the mechanical equivalent of a
   * foldRight implementation that does not blow the stack.
   *
   * <p>Alternative: {@code list.stream().filter(p).toList()}. Same answer; pick the trampoline form
   * when the input may be enormous.
   *
   * <p>Common wrong attempt: combine {@code filter} + {@code map} into one trampoline call. They
   * compose with {@code flatMapTrampoline} or by chaining; reach for the single-purpose helper that
   * matches the intent.
   */
  @Test
  void exercise13_stackSafeFilter() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    // SOLUTION: Use ListPrisms.filterTrampoline() to keep only even numbers
    List<Integer> evens = ListPrisms.filterTrampoline(numbers, x -> x % 2 == 0);

    assertThat(evens).containsExactly(2, 4, 6, 8, 10);
  }

  /**
   * Why this is idiomatic: {@code foldRight} reduces from the right with the supplied combiner —
   * sum, concat, build — and stays stack-safe via the trampoline.
   *
   * <p>Alternative: {@code list.stream().reduce(0, Integer::sum)}. Same answer for commutative
   * operations; the right-fold form preserves order for non-commutative combiners.
   *
   * <p>Common wrong attempt: apply a non-associative combiner expecting left-fold results. {@code
   * foldRight} starts from the seed at the right; the order matters when the operator is
   * non-associative.
   */
  @Test
  void exercise14_stackSafeFold() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5);

    // SOLUTION: Use ListPrisms.foldRight() to sum all numbers
    Integer sum = ListPrisms.foldRight(numbers, 0, Integer::sum);

    assertThat(sum).isEqualTo(15);
  }

  /**
   * Why this is idiomatic: {@code flatMapTrampoline} applies a list-returning function to each
   * element and concatenates the results. Stack-safe; ideal for tree-flattening or expansion
   * algorithms.
   *
   * <p>Alternative: {@code list.stream().flatMap(x -> innerList(x).stream()).toList()}. Same
   * answer; the trampoline form is preferable on large inputs.
   *
   * <p>Common wrong attempt: return {@code null} from the inner function for "no result". Use
   * {@code List.of()} (the empty list) — flatMap concatenates the empty contribution cleanly.
   */
  @Test
  void exercise15_stackSafeFlatMap() {
    List<Integer> numbers = List.of(1, 2, 3);

    // SOLUTION: Use ListPrisms.flatMapTrampoline() to create [x, x * 10] for each x
    List<Integer> expanded = ListPrisms.flatMapTrampoline(numbers, x -> List.of(x, x * 10));

    assertThat(expanded).containsExactly(1, 10, 2, 20, 3, 30);
  }

  /**
   * Why this is idiomatic: {@code zipWithTrampoline} pairs corresponding elements and applies the
   * combiner — stack-safe and stops at the shorter list.
   *
   * <p>Alternative: an indexed loop. Same runtime; the trampoline keeps the call shape consistent
   * with the rest of the helpers.
   *
   * <p>Common wrong attempt: assume the result length is the longer list's length. The zip stops
   * when either input is exhausted; pad the shorter list explicitly if a uniform length is wanted.
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
   * Why this is idiomatic: {@code take} and {@code drop} are the dual slicing helpers — one keeps
   * the prefix, the other drops it. The trampoline form scales without stack cost.
   *
   * <p>Alternative: {@code list.subList(...)} bounds. Same answer; the trampoline form keeps the
   * API consistent with {@code map}/{@code filter} when chaining.
   *
   * <p>Common wrong attempt: pass a negative {@code n}. The helpers expect non-negative counts;
   * clamp to zero or the list size before calling.
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
   * Why this is idiomatic: {@code reverseTrampoline} produces a reversed list without recursion.
   * The classic accumulator-based reverse runs in O(n) and the trampoline makes it stack-safe.
   *
   * <p>Alternative: {@code Collections.reverse(new ArrayList<>(list))}. Mutates a copy; the
   * trampoline version stays immutable end-to-end.
   *
   * <p>Common wrong attempt: build a recursive reverse by hand. The naïve recursion blows the stack
   * on long lists; the helper exists to avoid that footgun.
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
   * Why this is idiomatic: list optics compose with lenses and prisms like any other optic.
   * Lens-into-list followed by {@code head} gives an {@code Affine<Container, String>} — the first
   * item of the nested list.
   *
   * <p>Alternative: read the items, take {@code stream().findFirst()}, write back. Same runtime;
   * the optic composition keeps the path inspectable.
   *
   * <p>Common wrong attempt: forget that {@code head} is an affine. Composing with a lens produces
   * another affine, not a lens; the partiality propagates through.
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
