package org.higherkindedj.hkt.typeclass;

import org.jspecify.annotations.NonNull;

/**
 * Represents the Monoid type class, a fundamental concept in abstract algebra and functional
 * programming. A Monoid defines a structure for a specific type {@code A} that allows elements of
 * that type to be combined, and it includes a special "identity" element for this combination.
 *
 * <p>A Monoid consists of:
 *
 * <ol>
 *   <li>A set of elements of type {@code A}.
 *   <li>A binary operation ({@link #combine(Object, Object)}) that takes two elements of type
 *       {@code A} and returns another element of type {@code A}.
 *   <li>An identity element ({@link #empty()}) of type {@code A} which, when combined with any
 *       element using the binary operation, yields that same element.
 * </ol>
 *
 * <p><b>Monoid Laws:</b> For an implementation to be a valid Monoid, it must satisfy the following
 * laws:
 *
 * <ol>
 *   <li><b>Associativity:</b> The {@code combine} operation must be associative. For any elements
 *       {@code x}, {@code y}, {@code z} of type {@code A}: <br>
 *       {@code monoid.combine(x, monoid.combine(y, z))} must be equal to {@code
 *       monoid.combine(monoid.combine(x, y), z)}. <br>
 *       This means the order in which combinations are grouped does not affect the final result.
 *   <li><b>Identity Element (Left Identity):</b> The {@code empty} element must act as a left
 *       identity for the {@code combine} operation. For any element {@code x} of type {@code A}:
 *       <br>
 *       {@code monoid.combine(monoid.empty(), x)} must be equal to {@code x}.
 *   <li><b>Identity Element (Right Identity):</b> The {@code empty} element must act as a right
 *       identity for the {@code combine} operation. For any element {@code x} of type {@code A}:
 *       <br>
 *       {@code monoid.combine(x, monoid.empty())} must be equal to {@code x}.
 * </ol>
 *
 * <p><b>Common Examples of Monoids:</b>
 *
 * <ul>
 *   <li>Integers with addition: {@code empty = 0}, {@code combine(x, y) = x + y}
 *   <li>Integers with multiplication: {@code empty = 1}, {@code combine(x, y) = x * y}
 *   <li>Strings with concatenation: {@code empty = ""}, {@code combine(x, y) = x + y}
 *   <li>Lists with concatenation: {@code empty = Collections.emptyList()}, {@code combine(x, y) =
 *       newList.addAll(x); newList.addAll(y);}
 *   <li>Booleans with conjunction (AND): {@code empty = true}, {@code combine(x, y) = x && y}
 *   <li>Booleans with disjunction (OR): {@code empty = false}, {@code combine(x, y) = x || y}
 *   <li>Functions {@code A -> A} with function composition: {@code empty = a -> a} (identity
 *       function), {@code combine(f, g) = f.compose(g)} or {@code f.andThen(g)} (depending on
 *       composition order preference).
 * </ul>
 *
 * <p>Monoids are particularly useful for aggregating data, folding collections, and defining
 * operations that can be parallelized due to the associativity law.
 *
 * @param <A> The type for which the Monoid instance is defined. This type must adhere to the Monoid
 *     laws.
 */
public interface Monoid<A> {

  /**
   * Provides the identity element for the Monoid's {@code combine} operation.
   *
   * <p>The identity element {@code e} must satisfy the following for all {@code x} of type {@code
   * A}:
   *
   * <ul>
   *   <li>{@code combine(empty(), x) == x} (Left identity)
   *   <li>{@code combine(x, empty()) == x} (Right identity)
   * </ul>
   *
   * This element is often the "zero" or "neutral" element for the operation, such as {@code 0} for
   * addition, {@code 1} for multiplication, or an empty string/list for concatenation.
   *
   * @return The non-null identity element of type {@code A}. While conceptually the identity
   *     element can sometimes be represented by {@code null} in certain contexts (e.g., a nullable
   *     wrapper type), for this interface, a non-null value is generally expected for common Monoid
   *     instances like numbers, strings, and collections.
   */
  @NonNull A empty();

  /**
   * Combines two elements of type {@code A} into a single element of type {@code A}.
   *
   * <p>This operation must be associative: {@code combine(x, combine(y, z)) == combine(combine(x,
   * y), z)} for all {@code x, y, z} of type {@code A}.
   *
   * <p>Associativity allows for flexible grouping of operations, which is crucial for tasks like
   * parallel processing or efficient folding of data structures.
   *
   * @param x The first non-null element of type {@code A}.
   * @param y The second non-null element of type {@code A}.
   * @return The non-null result of combining {@code x} and {@code y}, also of type {@code A}. The
   *     non-null annotation implies that combining two non-null values should typically result in a
   *     non-null value, fitting most common Monoid definitions.
   */
  @NonNull A combine(@NonNull A x, @NonNull A y);
}
