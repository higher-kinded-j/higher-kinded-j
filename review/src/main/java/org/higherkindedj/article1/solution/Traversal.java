// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.solution;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A Traversal focuses on zero or more values within a structure.
 *
 * <p>Unlike a Lens which focuses on exactly one value, a Traversal can focus on multiple values
 * simultaneously - perfect for updating all elements in a collection.
 *
 * <p>This implementation demonstrates the core optics concepts.
 *
 * @param <S> the type of the whole structure
 * @param <A> the type of each focused element
 */
public record Traversal<S, A>(
    Function<S, List<A>> getAll, BiFunction<Function<A, A>, S, S> modifyAll) {

  /** Get all focused values from the structure. */
  public List<A> getAll(S whole) {
    return getAll.apply(whole);
  }

  /** Modify all focused values using a function. */
  public S modify(Function<A, A> f, S whole) {
    return modifyAll.apply(f, whole);
  }

  /** Set all focused values to the same new value. */
  public S set(A newValue, S whole) {
    return modify(_ -> newValue, whole);
  }

  /** Create a traversal over all elements of a list. */
  public static <A> Traversal<List<A>, A> list() {
    return new Traversal<>(
        list -> list,
        (f, list) -> {
          List<A> result = new ArrayList<>();
          for (A item : list) {
            result.add(f.apply(item));
          }
          return List.copyOf(result);
        });
  }

  /** Compose a lens with a traversal to traverse through a field. */
  public static <S, A, B> Traversal<S, B> fromLens(
      Lens<S, List<A>> lens, Traversal<List<A>, A> listTraversal, Lens<A, B> elemLens) {
    return new Traversal<>(
        s -> {
          List<B> result = new ArrayList<>();
          for (A item : lens.get(s)) {
            result.add(elemLens.get(item));
          }
          return result;
        },
        (f, s) -> {
          List<A> items = lens.get(s);
          List<A> updated = new ArrayList<>();
          for (A item : items) {
            updated.add(elemLens.modify(f, item));
          }
          return lens.set(List.copyOf(updated), s);
        });
  }
}
