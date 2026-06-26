// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.nonemptylist;

import static org.higherkindedj.hkt.util.validation.Operation.APPEND;
import static org.higherkindedj.hkt.util.validation.Operation.CONCAT;
import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;
import static org.higherkindedj.hkt.util.validation.Operation.FLAT_MAP;
import static org.higherkindedj.hkt.util.validation.Operation.FOLD_LEFT;
import static org.higherkindedj.hkt.util.validation.Operation.FROM_ITERABLE;
import static org.higherkindedj.hkt.util.validation.Operation.FROM_LIST;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;
import static org.higherkindedj.hkt.util.validation.Operation.MAX;
import static org.higherkindedj.hkt.util.validation.Operation.MIN;
import static org.higherkindedj.hkt.util.validation.Operation.PREPEND;
import static org.higherkindedj.hkt.util.validation.Operation.REDUCE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * An immutable, ordered list that is guaranteed to contain at least one element.
 *
 * <p>A {@code NonEmptyList} is represented as a {@code head} (always present) plus a {@code tail}
 * of zero or more further elements. Because non-emptiness is part of the type, operations that are
 * partial on an ordinary {@link List} are <em>total</em> here:
 *
 * <ul>
 *   <li>{@link #head()}, {@link #last()} — always return an element, never throw.
 *   <li>{@link #reduce(Semigroup)}, {@link #min(Comparator)}, {@link #max(Comparator)} — always
 *       return a result; no {@link java.util.Optional}, no empty case to handle.
 * </ul>
 *
 * <p>This makes {@code NonEmptyList} the natural carrier for any value that must contain at least
 * one element — most notably the error channel of accumulating validation, where an
 * <em>invalid</em> result always has one or more errors. See {@link #semigroup()} for the
 * concatenating combine used for error accumulation.
 *
 * <p><b>No empty, by design.</b> There is no factory that produces an empty {@code NonEmptyList}
 * and therefore no {@code Monoid} instance — the absence of an empty value is the whole point. To
 * build one from data that <em>might</em> be empty, use {@link #fromList(List)} or {@link
 * #fromIterable(Iterable)}, which return a {@link Maybe} and never throw.
 *
 * <p><b>Immutability and null-safety.</b> Instances are deeply immutable: the {@code tail} is
 * defensively copied on construction and {@link #tail()} returns an unmodifiable view. Elements are
 * never {@code null}; any attempt to introduce a {@code null} element is rejected at construction
 * time.
 *
 * <p>{@code NonEmptyList} implements {@link Iterable}, so it works directly in for-each loops and
 * stream pipelines.
 *
 * @param <A> the (non-null) element type
 * @param head the first element; never {@code null}
 * @param tail the remaining elements; never {@code null} and never containing {@code null},
 *     possibly empty
 */
public record NonEmptyList<A>(A head, List<A> tail) implements NonEmptyListKind<A>, Iterable<A> {

  private static final Class<?> NON_EMPTY_LIST_CLASS = NonEmptyList.class;

  /**
   * Canonical constructor. Validates that {@code head} and {@code tail} are non-null and replaces
   * {@code tail} with a defensive, immutable copy (which also rejects any {@code null} element).
   *
   * @throws NullPointerException if {@code head} or {@code tail} is {@code null}, or if {@code
   *     tail} contains a {@code null} element
   */
  public NonEmptyList {
    Validation.coreType().requireValue(head, NON_EMPTY_LIST_CLASS, CONSTRUCTION);
    Validation.coreType().requireValue(tail, "tail", NON_EMPTY_LIST_CLASS, CONSTRUCTION);
    tail = List.copyOf(tail);
  }

  // ===== Construction =====

  /**
   * Creates a {@code NonEmptyList} from a head element and zero or more further elements.
   *
   * @param head the first element; must not be {@code null}
   * @param rest the remaining elements; must not contain {@code null}
   * @param <A> the element type
   * @return a non-null {@code NonEmptyList}
   */
  @SafeVarargs
  public static <A> NonEmptyList<A> of(A head, A... rest) {
    return new NonEmptyList<>(head, List.of(rest));
  }

  /**
   * Creates a {@code NonEmptyList} from a head element and an explicit tail list.
   *
   * @param head the first element; must not be {@code null}
   * @param tail the remaining elements; must not be {@code null} or contain {@code null}
   * @param <A> the element type
   * @return a non-null {@code NonEmptyList}
   */
  public static <A> NonEmptyList<A> of(A head, List<A> tail) {
    return new NonEmptyList<>(head, tail);
  }

  /**
   * Creates a single-element {@code NonEmptyList}.
   *
   * @param value the sole element; must not be {@code null}
   * @param <A> the element type
   * @return a non-null, single-element {@code NonEmptyList}
   */
  public static <A> NonEmptyList<A> single(A value) {
    return new NonEmptyList<>(value, List.of());
  }

  /**
   * Attempts to build a {@code NonEmptyList} from a possibly-empty {@link List}.
   *
   * @param list the source list; must not be {@code null}
   * @param <A> the element type
   * @return {@code Just} the {@code NonEmptyList} if {@code list} is non-empty, otherwise {@code
   *     Nothing}; never throws for an empty list
   */
  public static <A> Maybe<NonEmptyList<A>> fromList(List<A> list) {
    Validation.coreType().requireValue(list, "list", NON_EMPTY_LIST_CLASS, FROM_LIST);
    if (list.isEmpty()) {
      return Maybe.nothing();
    }
    return Maybe.just(new NonEmptyList<>(list.get(0), list.subList(1, list.size())));
  }

  /**
   * Attempts to build a {@code NonEmptyList} from a possibly-empty {@link Iterable}.
   *
   * @param iterable the source iterable; must not be {@code null}
   * @param <A> the element type
   * @return {@code Just} the {@code NonEmptyList} if {@code iterable} yields at least one element,
   *     otherwise {@code Nothing}; never throws for an empty iterable
   */
  public static <A> Maybe<NonEmptyList<A>> fromIterable(Iterable<A> iterable) {
    Validation.coreType().requireValue(iterable, "iterable", NON_EMPTY_LIST_CLASS, FROM_ITERABLE);
    Iterator<A> it = iterable.iterator();
    if (!it.hasNext()) {
      return Maybe.nothing();
    }
    A head = it.next();
    List<A> tail = new ArrayList<>();
    while (it.hasNext()) {
      tail.add(it.next());
    }
    return Maybe.just(new NonEmptyList<>(head, tail));
  }

  // ===== Total accessors =====

  /**
   * Returns the last element. Total — never throws.
   *
   * @return the last element
   */
  public A last() {
    return tail.isEmpty() ? head : tail.get(tail.size() - 1);
  }

  /**
   * Returns the number of elements, always {@code >= 1}.
   *
   * @return the size
   */
  public int size() {
    return 1 + tail.size();
  }

  /**
   * Reduces all elements to a single value using the given {@link Semigroup}, left-to-right
   * starting from the {@link #head()}. Total — never throws, because there is always at least one
   * element.
   *
   * @param semigroup the combine operation; must not be {@code null}
   * @return the reduced value
   */
  public A reduce(Semigroup<A> semigroup) {
    Validation.function().require(semigroup, "semigroup", REDUCE);
    A acc = head;
    for (A a : tail) {
      acc = semigroup.combine(acc, a);
    }
    return acc;
  }

  /**
   * Returns the smallest element according to the given {@link Comparator}. Total — never throws.
   *
   * @param comparator the ordering; must not be {@code null}
   * @return the minimum element
   */
  public A min(Comparator<? super A> comparator) {
    Validation.function().require(comparator, "comparator", MIN);
    A result = head;
    for (A a : tail) {
      if (comparator.compare(a, result) < 0) {
        result = a;
      }
    }
    return result;
  }

  /**
   * Returns the largest element according to the given {@link Comparator}. Total — never throws.
   *
   * @param comparator the ordering; must not be {@code null}
   * @return the maximum element
   */
  public A max(Comparator<? super A> comparator) {
    Validation.function().require(comparator, "comparator", MAX);
    A result = head;
    for (A a : tail) {
      if (comparator.compare(a, result) > 0) {
        result = a;
      }
    }
    return result;
  }

  // ===== Fluent transforms (no Kind required) =====

  /**
   * Applies {@code mapper} to every element, preserving order and non-emptiness.
   *
   * @param mapper the transform; must not be {@code null} and must not return {@code null}
   * @param <B> the result element type
   * @return a new {@code NonEmptyList} of the mapped elements
   */
  public <B> NonEmptyList<B> map(Function<? super A, ? extends B> mapper) {
    Validation.function().require(mapper, "mapper", MAP);
    B newHead = mapper.apply(head);
    List<B> newTail = new ArrayList<>(tail.size());
    for (A a : tail) {
      newTail.add(mapper.apply(a));
    }
    return new NonEmptyList<>(newHead, newTail);
  }

  /**
   * Applies {@code mapper} to every element and concatenates the resulting non-empty lists. The
   * result is non-empty by construction.
   *
   * @param mapper the transform; must not be {@code null} and must not return {@code null}
   * @param <B> the result element type
   * @return a new flattened {@code NonEmptyList}
   */
  public <B> NonEmptyList<B> flatMap(Function<? super A, ? extends NonEmptyList<B>> mapper) {
    Validation.function().require(mapper, "mapper", FLAT_MAP);
    NonEmptyList<B> first = mapper.apply(head);
    Validation.coreType().requireValue(first, "result", NON_EMPTY_LIST_CLASS, FLAT_MAP);
    List<B> newTail = new ArrayList<>(first.tail);
    for (A a : tail) {
      NonEmptyList<B> mapped = mapper.apply(a);
      Validation.coreType().requireValue(mapped, "result", NON_EMPTY_LIST_CLASS, FLAT_MAP);
      newTail.add(mapped.head);
      newTail.addAll(mapped.tail);
    }
    return new NonEmptyList<>(first.head, newTail);
  }

  /**
   * Left fold over all elements, starting from {@code initial} and visiting {@link #head()} first.
   *
   * @param initial the seed value
   * @param folder the accumulating function; must not be {@code null}
   * @param <B> the accumulator type
   * @return the folded result
   */
  public <B> B foldLeft(B initial, BiFunction<? super B, ? super A, ? extends B> folder) {
    Validation.function().require(folder, "folder", FOLD_LEFT);
    B acc = folder.apply(initial, head);
    for (A a : tail) {
      acc = folder.apply(acc, a);
    }
    return acc;
  }

  /**
   * Returns a new {@code NonEmptyList} with the elements in reverse order.
   *
   * @return the reversed list
   */
  public NonEmptyList<A> reverse() {
    List<A> reversed = new ArrayList<>(toJavaList());
    Collections.reverse(reversed);
    return new NonEmptyList<>(reversed.get(0), reversed.subList(1, reversed.size()));
  }

  /**
   * Concatenates this list with {@code other}, this list's elements first. The associative combine
   * behind {@link #semigroup()}.
   *
   * @param other the list to append; must not be {@code null}
   * @return the concatenated {@code NonEmptyList}
   */
  public NonEmptyList<A> concat(NonEmptyList<A> other) {
    Validation.coreType().requireValue(other, "other", NON_EMPTY_LIST_CLASS, CONCAT);
    List<A> newTail = new ArrayList<>(tail.size() + 1 + other.tail.size());
    newTail.addAll(tail);
    newTail.add(other.head);
    newTail.addAll(other.tail);
    return new NonEmptyList<>(head, newTail);
  }

  /**
   * Returns a new {@code NonEmptyList} with {@code element} added at the end.
   *
   * @param element the element to append; must not be {@code null}
   * @return the extended list
   */
  public NonEmptyList<A> append(A element) {
    Validation.coreType().requireValue(element, "element", NON_EMPTY_LIST_CLASS, APPEND);
    List<A> newTail = new ArrayList<>(tail);
    newTail.add(element);
    return new NonEmptyList<>(head, newTail);
  }

  /**
   * Returns a new {@code NonEmptyList} with {@code element} added at the front as the new head.
   *
   * @param element the element to prepend; must not be {@code null}
   * @return the extended list
   */
  public NonEmptyList<A> prepend(A element) {
    Validation.coreType().requireValue(element, "element", NON_EMPTY_LIST_CLASS, PREPEND);
    List<A> newTail = new ArrayList<>(size());
    newTail.add(head);
    newTail.addAll(tail);
    return new NonEmptyList<>(element, newTail);
  }

  // ===== Interop =====

  /**
   * Converts this {@code NonEmptyList} to an immutable {@link java.util.List} containing the same
   * elements in the same order.
   *
   * @return an unmodifiable {@code List} of {@code head} followed by {@code tail}
   */
  public List<A> toJavaList() {
    List<A> all = new ArrayList<>(size());
    all.add(head);
    all.addAll(tail);
    return Collections.unmodifiableList(all);
  }

  @Override
  public Iterator<A> iterator() {
    return toJavaList().iterator();
  }

  @Override
  public String toString() {
    return "NonEmptyList" + toJavaList();
  }

  // ===== Error-accumulation combine =====

  /**
   * Returns a {@link Semigroup} that concatenates two {@code NonEmptyList}s, left-to-right (the
   * first list's elements come first). The result is non-empty by construction.
   *
   * <p>This is the canonical combine for using {@code NonEmptyList} as an accumulating error
   * channel. Concatenation is associative but <em>not</em> commutative — error order is preserved.
   *
   * @param <A> the element type
   * @return a non-null {@code Semigroup} for {@code NonEmptyList} concatenation
   */
  public static <A> Semigroup<NonEmptyList<A>> semigroup() {
    return NonEmptyList::concat;
  }
}
