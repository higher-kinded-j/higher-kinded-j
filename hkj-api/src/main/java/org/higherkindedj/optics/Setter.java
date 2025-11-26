// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;

/**
 * A **Setter** is a write-only optic that can modify focused elements without necessarily reading
 * them. Think of it as a functional "modifier" that provides a composable way to update values
 * within structures.
 *
 * <p>A Setter is the right tool for:
 *
 * <ul>
 *   <li>Modifying elements when you only need to transform them, not read them
 *   <li>Updating potentially multiple elements in a structure (like a {@link Traversal})
 *   <li>Creating write-only access to parts of a data structure
 * </ul>
 *
 * <p>A Setter is more general than a {@link Lens} in the sense that it can modify multiple elements
 * (like a Traversal) but doesn't need to provide read access. However, it's also more restricted
 * because it can't extract values.
 *
 * <p>Every {@link Lens} and {@link Traversal} can be viewed as a Setter, but not every Setter can
 * be a Lens or Traversal.
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * record Person(String name, int age) {}
 *
 * // Create a setter for the name field
 * Setter<Person, String> nameSetter = Setter.of(
 *   f -> person -> new Person(f.apply(person.name()), person.age())
 * );
 *
 * Person person = new Person("John", 30);
 * Person updated = nameSetter.modify(String::toUpperCase, person);
 * // updated = Person("JOHN", 30)
 *
 * Person set = nameSetter.set("Jane", person);
 * // set = Person("Jane", 30)
 * }</pre>
 *
 * @param <S> The source type (the structure being modified).
 * @param <A> The focus type (the values being modified).
 */
public interface Setter<S, A> extends Optic<S, S, A, A> {

  /**
   * Modifies the focused elements using a pure function.
   *
   * <p>This is the core operation of a Setter. It applies the modifier function to all focused
   * elements and returns the updated structure.
   *
   * @param f The function to apply to each focused element.
   * @param source The source structure.
   * @return The modified structure.
   */
  S modify(Function<A, A> f, S source);

  /**
   * Sets all focused elements to a specific value.
   *
   * <p>This is equivalent to {@code modify(a -> value, source)}.
   *
   * @param value The value to set.
   * @param source The source structure.
   * @return The modified structure with all focused elements set to the given value.
   */
  default S set(A value, S source) {
    return modify(a -> value, source);
  }

  /**
   * Composes this {@code Setter<S, A>} with another {@code Setter<A, B>} to create a new {@code
   * Setter<S, B>}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Setter<Person, Address> addressSetter = ...;
   * Setter<Address, String> citySetter = ...;
   * Setter<Person, String> personCitySetter = addressSetter.andThen(citySetter);
   * }</pre>
   *
   * @param other The Setter to compose with.
   * @param <B> The type of the final focused values.
   * @return A new composed Setter.
   */
  default <B> Setter<S, B> andThen(Setter<A, B> other) {
    Setter<S, A> self = this;
    return new Setter<>() {
      @Override
      public S modify(Function<B, B> f, S source) {
        return self.modify(a -> other.modify(f, a), source);
      }

      @Override
      public <F> Kind<F, S> modifyF(Function<B, Kind<F, B>> f, S s, Applicative<F> app) {
        return self.modifyF(a -> other.modifyF(f, a, app), s, app);
      }
    };
  }

  /**
   * Converts this Setter to a {@link Traversal}.
   *
   * <p>This is always valid since a Setter satisfies the requirements of a Traversal (it can
   * traverse and modify elements with effects).
   *
   * @return A Traversal view of this Setter.
   */
  default Traversal<S, A> asTraversal() {
    Setter<S, A> self = this;
    return new Traversal<>() {
      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
        return self.modifyF(f, s, app);
      }
    };
  }

  /**
   * Creates a Setter from a modification function.
   *
   * <p>The modification function takes a pure transformation {@code A -> A} and returns a structure
   * transformation {@code S -> S}. This is the canonical way to define a Setter.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Setter for all elements in a list
   * Setter<List<String>, String> listSetter = Setter.of(
   *   f -> list -> list.stream().map(f).toList()
   * );
   *
   * // Setter for a record field
   * Setter<Person, String> nameSetter = Setter.of(
   *   f -> person -> new Person(f.apply(person.name()), person.age())
   * );
   * }</pre>
   *
   * @param over The function that lifts element transformations to structure transformations.
   * @param <S> The source type.
   * @param <A> The focus type.
   * @return A new Setter.
   */
  static <S, A> Setter<S, A> of(Function<Function<A, A>, Function<S, S>> over) {
    return new Setter<>() {
      @Override
      public S modify(Function<A, A> f, S source) {
        return over.apply(f).apply(source);
      }

      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
        // Cannot implement modifyF without access to the focused element.
        // Use Setter.fromGetSet() for effectful modifications.
        throw new UnsupportedOperationException(
            "modifyF is not supported for Setters created via of(). Use Setter.fromGetSet() to"
                + " create a Setter that supports effectful modifications.");
      }
    };
  }

  /**
   * Creates a Setter from a getter and setter pair.
   *
   * <p>This is useful when you have both get and set operations but want to create a write-only
   * view. The resulting Setter will focus on exactly one element.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Setter<Person, String> nameSetter = Setter.fromGetSet(
   *   Person::name,
   *   (person, name) -> new Person(name, person.age())
   * );
   * }</pre>
   *
   * @param getter The function to extract the current value.
   * @param setter The function to create an updated structure.
   * @param <S> The source type.
   * @param <A> The focus type.
   * @return A new Setter.
   */
  static <S, A> Setter<S, A> fromGetSet(Function<S, A> getter, BiFunction<S, A, S> setter) {
    return new Setter<>() {
      @Override
      public S modify(Function<A, A> f, S source) {
        return setter.apply(source, f.apply(getter.apply(source)));
      }

      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> app) {
        Kind<F, A> fa = f.apply(getter.apply(s));
        return app.map(a -> setter.apply(s, a), fa);
      }
    };
  }

  /**
   * Creates a Setter that modifies all elements in a list.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Setter<List<Integer>, Integer> listElements = Setter.forList();
   * List<Integer> doubled = listElements.modify(x -> x * 2, List.of(1, 2, 3));
   * // doubled = [2, 4, 6]
   * }</pre>
   *
   * @param <A> The element type.
   * @return A Setter for list elements.
   */
  static <A> Setter<List<A>, A> forList() {
    return new Setter<>() {
      @Override
      public List<A> modify(Function<A, A> f, List<A> source) {
        return source.stream().map(f).toList();
      }

      @Override
      public <F> Kind<F, List<A>> modifyF(
          Function<A, Kind<F, A>> f, List<A> s, Applicative<F> app) {
        // Collect all effectful results first
        List<Kind<F, A>> effects = new ArrayList<>(s.size());
        for (A a : s) {
          effects.add(f.apply(a));
        }

        // Sequence effects using right-to-left fold with LinkedList for O(1) prepending
        Kind<F, LinkedList<A>> acc = app.of(new LinkedList<>());
        for (int i = effects.size() - 1; i >= 0; i--) {
          Kind<F, A> fa = effects.get(i);
          acc =
              app.map2(
                  fa,
                  acc,
                  (elem, list) -> {
                    list.addFirst(elem); // O(1) prepend
                    return list;
                  });
        }

        // Convert LinkedList to immutable List at the end (O(n) total)
        return app.map(List::copyOf, acc);
      }
    };
  }

  /**
   * Creates a Setter that modifies all values in a map.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Setter<Map<String, Integer>, Integer> mapValues = Setter.forMapValues();
   * Map<String, Integer> doubled = mapValues.modify(x -> x * 2,
   *   Map.of("a", 1, "b", 2));
   * // doubled = {a=2, b=4}
   * }</pre>
   *
   * @param <K> The key type.
   * @param <V> The value type.
   * @return A Setter for map values.
   */
  static <K, V> Setter<Map<K, V>, V> forMapValues() {
    return new Setter<>() {
      @Override
      public Map<K, V> modify(Function<V, V> f, Map<K, V> source) {
        Map<K, V> result = new HashMap<>();
        for (Map.Entry<K, V> entry : source.entrySet()) {
          result.put(entry.getKey(), f.apply(entry.getValue()));
        }
        return result;
      }

      @Override
      public <F> Kind<F, Map<K, V>> modifyF(
          Function<V, Kind<F, V>> f, Map<K, V> s, Applicative<F> app) {
        // Collect all keys and effectful values
        List<K> keys = new ArrayList<>(s.size());
        List<Kind<F, V>> effects = new ArrayList<>(s.size());
        for (Map.Entry<K, V> entry : s.entrySet()) {
          keys.add(entry.getKey());
          effects.add(f.apply(entry.getValue()));
        }

        // Sequence effects to get Kind<F, List<V>>
        Kind<F, LinkedList<V>> acc = app.of(new LinkedList<>());
        for (int i = effects.size() - 1; i >= 0; i--) {
          Kind<F, V> fv = effects.get(i);
          acc =
              app.map2(
                  fv,
                  acc,
                  (v, list) -> {
                    list.addFirst(v); // O(1) prepend
                    return list;
                  });
        }

        // Convert to Map at the end (O(n) total)
        return app.map(
            values -> {
              Map<K, V> resultMap = new HashMap<>();
              Iterator<V> valIter = values.iterator();
              for (K key : keys) {
                resultMap.put(key, valIter.next());
              }
              return resultMap;
            },
            acc);
      }
    };
  }

  /**
   * Creates a Setter that does nothing (identity setter).
   *
   * <p>This setter passes through modifications without applying them. Useful as a base case for
   * composition.
   *
   * @param <S> The source type.
   * @return An identity Setter.
   */
  static <S> Setter<S, S> identity() {
    return new Setter<>() {
      @Override
      public S modify(Function<S, S> f, S source) {
        return f.apply(source);
      }

      @Override
      public <F> Kind<F, S> modifyF(Function<S, Kind<F, S>> f, S s, Applicative<F> app) {
        return f.apply(s);
      }
    };
  }
}
