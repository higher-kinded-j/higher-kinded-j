// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.stream.StreamTraverse;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * Utility class providing {@link Traversal} instances derived from {@link Traverse} type class
 * instances.
 *
 * <p>This class bridges the Traverse type class with the optics Traversal, enabling generic
 * collection manipulation through optics for any type with a Traverse instance.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create a Traversal for List elements
 * Traversal<Kind<ListKind.Witness, String>, String> listTraversal =
 *     TraverseTraversals.forTraverse(ListTraverse.INSTANCE);
 *
 * // Use convenience methods for common types
 * Traversal<Maybe<String>, String> maybeTraversal = TraverseTraversals.forMaybe();
 *
 * // Compose with other optics
 * FocusPath<User, Kind<ListKind.Witness, Role>> rolesPath = UserFocus.roles();
 * TraversalPath<User, Role> allRoles = rolesPath.traverseOver(
 *     ListTraverse.INSTANCE,
 *     ListKindHelper.LIST
 * );
 * }</pre>
 *
 * @see Traverse
 * @see Traversal
 */
@NullMarked
public final class TraverseTraversals {

  /** Private constructor to prevent instantiation. */
  private TraverseTraversals() {}

  /**
   * Creates a {@link Traversal} for any type with a {@link Traverse} instance.
   *
   * <p>This is the fundamental bridge between the Traverse type class and optics Traversal. It
   * allows any traversable container to be used with the optics composition API.
   *
   * <h2>Example</h2>
   *
   * <pre>{@code
   * // For a custom Tree type with TreeTraverse instance
   * Traverse<TreeKind.Witness> treeTraverse = TreeTraverse.INSTANCE;
   *
   * Traversal<Kind<TreeKind.Witness, String>, String> treeTraversal =
   *     TraverseTraversals.forTraverse(treeTraverse);
   *
   * // Now use with optics
   * Kind<TreeKind.Witness, String> tree = createTree();
   * List<String> allValues = Traversals.getAll(treeTraversal, tree);
   * }</pre>
   *
   * @param <F> the witness type of the traversable container
   * @param <A> the element type within the container
   * @param traverse the Traverse instance for the container type; must not be null
   * @return a Traversal over the container's elements; never null
   * @throws NullPointerException if traverse is null
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> Traversal<Kind<F, A>, A> forTraverse(
      Traverse<F> traverse) {
    Objects.requireNonNull(traverse, "traverse must not be null");

    return new Traversal<>() {
      @Override
      public <G extends WitnessArity<TypeArity.Unary>> Kind<G, Kind<F, A>> modifyF(
          Function<A, Kind<G, A>> f, Kind<F, A> source, Applicative<G> applicative) {
        // Use Traverse.traverse to map over elements with the effectful function
        return traverse.traverse(applicative, f, source);
      }
    };
  }

  /**
   * Creates a {@link Traversal} for {@link List} elements using the standard List encoding.
   *
   * <p>This traversal focuses on all elements within a {@code Kind<ListKind.Witness, A>}. For
   * working with raw {@code java.util.List}, use {@link ListTraversals#forList()} instead.
   *
   * <h2>Example</h2>
   *
   * <pre>{@code
   * Kind<ListKind.Witness, String> names = ListKindHelper.LIST.widen(List.of("Alice", "Bob"));
   *
   * Traversal<Kind<ListKind.Witness, String>, String> traversal =
   *     TraverseTraversals.forListKind();
   *
   * Kind<ListKind.Witness, String> upper = Traversals.modify(
   *     traversal,
   *     String::toUpperCase,
   *     names
   * );
   * // ["ALICE", "BOB"]
   * }</pre>
   *
   * @param <A> the element type within the list
   * @return a Traversal over list elements; never null
   */
  public static <A> Traversal<Kind<ListKind.Witness, A>, A> forListKind() {
    return forTraverse(ListTraverse.INSTANCE);
  }

  /**
   * Creates a {@link Traversal} for {@link Maybe} values.
   *
   * <p>This traversal focuses on the value inside a {@code Maybe} when present (the {@code Just}
   * case). When the {@code Maybe} is {@code Nothing}, the traversal has zero targets.
   *
   * <h2>Example</h2>
   *
   * <pre>{@code
   * Maybe<String> present = Maybe.just("hello");
   * Maybe<String> absent = Maybe.nothing();
   *
   * Traversal<Maybe<String>, String> traversal = TraverseTraversals.forMaybe();
   *
   * Maybe<String> modified = Traversals.modify(traversal, String::toUpperCase, present);
   * // Maybe.just("HELLO")
   *
   * Maybe<String> unchanged = Traversals.modify(traversal, String::toUpperCase, absent);
   * // Maybe.nothing()
   * }</pre>
   *
   * @param <A> the element type within the Maybe
   * @return a Traversal over Maybe's value when present; never null
   */
  public static <A> Traversal<Maybe<A>, A> forMaybe() {
    return new Traversal<>() {
      @Override
      public <G extends WitnessArity<TypeArity.Unary>> Kind<G, Maybe<A>> modifyF(
          Function<A, Kind<G, A>> f, Maybe<A> source, Applicative<G> applicative) {
        if (source.isJust()) {
          return applicative.map(Maybe::just, f.apply(source.get()));
        }
        return applicative.of(source);
      }
    };
  }

  /**
   * Creates a {@link Traversal} for {@link Maybe} values using the Kind encoding.
   *
   * <p>This is the Kind-encoded version of {@link #forMaybe()}, useful when working with the
   * higher-kinded type representation.
   *
   * @param <A> the element type within the Maybe
   * @return a Traversal over Maybe's value when present; never null
   */
  public static <A> Traversal<Kind<MaybeKind.Witness, A>, A> forMaybeKind() {
    return new Traversal<>() {
      @Override
      public <G extends WitnessArity<TypeArity.Unary>> Kind<G, Kind<MaybeKind.Witness, A>> modifyF(
          Function<A, Kind<G, A>> f,
          Kind<MaybeKind.Witness, A> source,
          Applicative<G> applicative) {
        Maybe<A> maybe = MaybeKindHelper.MAYBE.narrow(source);
        if (maybe.isJust()) {
          return applicative.map(
              a -> MaybeKindHelper.MAYBE.widen(Maybe.just(a)), f.apply(maybe.get()));
        }
        return applicative.of(source);
      }
    };
  }

  /**
   * Creates a {@link Traversal} for {@link Stream} elements using the Kind encoding.
   *
   * <p>This traversal focuses on all elements within a {@code Kind<StreamKind.Witness, A>}.
   *
   * <p><b>Note:</b> Streams are consumed during traversal, so the resulting stream can only be used
   * once.
   *
   * @param <A> the element type within the stream
   * @return a Traversal over stream elements; never null
   */
  public static <A> Traversal<Kind<StreamKind.Witness, A>, A> forStreamKind() {
    return forTraverse(StreamTraverse.INSTANCE);
  }

  /**
   * Creates a {@link Traversal} for {@link java.util.Set} elements.
   *
   * <p>This traversal focuses on all elements within a {@code Set<A>}. Since sets have no defined
   * order, the traversal order may vary between invocations.
   *
   * <h2>Example</h2>
   *
   * <pre>{@code
   * Set<String> names = Set.of("Alice", "Bob", "Charlie");
   *
   * Traversal<Set<String>, String> traversal = TraverseTraversals.forSet();
   *
   * Set<String> upper = Traversals.modify(traversal, String::toUpperCase, names);
   * // Set.of("ALICE", "BOB", "CHARLIE")
   * }</pre>
   *
   * @param <A> the element type within the set
   * @return a Traversal over set elements; never null
   */
  public static <A> Traversal<Set<A>, A> forSet() {
    return new Traversal<>() {
      @Override
      public <G extends WitnessArity<TypeArity.Unary>> Kind<G, Set<A>> modifyF(
          Function<A, Kind<G, A>> f, Set<A> source, Applicative<G> applicative) {
        // Convert Set to List for traversal, then collect back to Set
        List<A> asList = List.copyOf(source);
        Kind<ListKind.Witness, A> listKind = ListKindHelper.LIST.widen(asList);

        Kind<G, Kind<ListKind.Witness, A>> traversed =
            ListTraverse.INSTANCE.traverse(applicative, f, listKind);

        return applicative.map(
            kindResult -> {
              List<A> resultList = ListKindHelper.LIST.narrow(kindResult);
              return Set.copyOf(resultList);
            },
            traversed);
      }
    };
  }

  /**
   * Creates a {@link Traversal} for {@link java.util.stream.Stream} elements.
   *
   * <p>This traversal focuses on all elements within a {@code Stream<A>}.
   *
   * <p><b>Warning:</b> Streams can only be consumed once. After using this traversal, the original
   * stream will be exhausted. The result will be a new stream.
   *
   * @param <A> the element type within the stream
   * @return a Traversal over stream elements; never null
   */
  public static <A> Traversal<Stream<A>, A> forStream() {
    return new Traversal<>() {
      @Override
      public <G extends WitnessArity<TypeArity.Unary>> Kind<G, Stream<A>> modifyF(
          Function<A, Kind<G, A>> f, Stream<A> source, Applicative<G> applicative) {
        // Collect stream to list (consuming it), traverse, then convert back to stream
        List<A> asList = source.collect(Collectors.toList());
        Kind<ListKind.Witness, A> listKind = ListKindHelper.LIST.widen(asList);

        Kind<G, Kind<ListKind.Witness, A>> traversed =
            ListTraverse.INSTANCE.traverse(applicative, f, listKind);

        return applicative.map(
            kindResult -> {
              List<A> resultList = ListKindHelper.LIST.narrow(kindResult);
              return resultList.stream();
            },
            traversed);
      }
    };
  }
}
