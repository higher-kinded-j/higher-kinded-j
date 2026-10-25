// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Instruction set for optic operations in the Free monad DSL.
 *
 * <p>Each sealed subtype represents a single optic operation that can be composed into larger
 * programs using the Free monad. Operations are described as data and can be interpreted in
 * different ways.
 *
 * <p>This enables:
 *
 * <ul>
 *   <li>Building complex optic workflows as data structures
 *   <li>Multiple interpreters (direct execution, logging, validation, optimisation)
 *   <li>Program inspection and transformation before execution
 *   <li>Transactional semantics (validate then commit)
 * </ul>
 *
 * <p>Each operation carries a continuation {@code k} from what the operation produces to {@code A},
 * in the same continuation-passing style as an {@code @EffectAlgebra}. {@link #mapK(Function)}
 * composes onto it, so {@link OpticOpFunctor} is a lawful Functor. An interpreter carries out the
 * operation and hands its result to {@code k}:
 *
 * <pre>{@code
 * case OpticOp.Get<?, ?, ?> get -> executeGet(get);
 * ...
 * private <S, A, R> R executeGet(OpticOp.Get<S, A, R> op) {
 *   return op.k().apply(op.optic().get(op.source()));
 * }
 * }</pre>
 *
 * @param <S> The source structure type
 * @param <A> The result type, what the continuation produces
 */
@NullMarked
public sealed interface OpticOp<S extends @Nullable Object, A> extends OpticOpKind<A> {

  /**
   * The continuation from what this operation produces to the result type.
   *
   * <p>Each record narrows the input type: a {@link Get} takes the focus, a {@link Set} takes the
   * updated source. An interpreter that handles every operation the same way, such as a mock that
   * looks up a stub, can hand its value to this.
   *
   * @return The continuation
   */
  Function<?, A> k();

  /**
   * Maps a function over the result, composing it onto the continuation.
   *
   * @param f The function to apply to the result
   * @param <B> The new result type
   * @return The same operation with {@code f} composed onto its continuation
   */
  <B> OpticOp<S, B> mapK(Function<? super A, ? extends B> f);

  /**
   * Get operation - reads a single value through a Getter or Lens.
   *
   * @param <S> The source type
   * @param <A> The value type
   * @param <R> The result type of the continuation
   */
  record Get<S extends @Nullable Object, A, R>(S source, Getter<S, A> optic, Function<A, R> k)
      implements OpticOp<S, R> {
    @Override
    public <B> OpticOp<S, B> mapK(Function<? super R, ? extends B> f) {
      return new Get<>(source, optic, k.andThen(f));
    }
  }

  /**
   * Preview operation - reads an optional value through a Fold (first element).
   *
   * @param <S> The source type
   * @param <A> The value type
   * @param <R> The result type of the continuation
   */
  record Preview<S extends @Nullable Object, A extends @Nullable Object, R>(
      S source, Fold<S, A> optic, Function<Optional<@NonNull A>, R> k) implements OpticOp<S, R> {
    @Override
    public <B> OpticOp<S, B> mapK(Function<? super R, ? extends B> f) {
      return new Preview<>(source, optic, k.andThen(f));
    }
  }

  /**
   * GetAll operation - reads all values through a Fold or Traversal.
   *
   * @param <S> The source type
   * @param <A> The value type
   * @param <R> The result type of the continuation
   */
  record GetAll<S extends @Nullable Object, A extends @Nullable Object, R>(
      S source, Fold<S, A> optic, Function<List<A>, R> k) implements OpticOp<S, R> {
    @Override
    public <B> OpticOp<S, B> mapK(Function<? super R, ? extends B> f) {
      return new GetAll<>(source, optic, k.andThen(f));
    }
  }

  /**
   * Set operation - writes a value through a Lens.
   *
   * @param <S> The source type
   * @param <A> The value type
   * @param <R> The result type of the continuation
   */
  record Set<S, A extends @Nullable Object, R>(
      S source, Lens<S, A> optic, A newValue, Function<S, R> k) implements OpticOp<S, R> {
    @Override
    public <B> OpticOp<S, B> mapK(Function<? super R, ? extends B> f) {
      return new Set<>(source, optic, newValue, k.andThen(f));
    }
  }

  /**
   * SetAll operation - writes the same value to all focuses of a Traversal.
   *
   * @param <S> The source type
   * @param <A> The value type
   * @param <R> The result type of the continuation
   */
  record SetAll<S, A extends @Nullable Object, R>(
      S source, Traversal<S, A> optic, A newValue, Function<S, R> k) implements OpticOp<S, R> {
    @Override
    public <B> OpticOp<S, B> mapK(Function<? super R, ? extends B> f) {
      return new SetAll<>(source, optic, newValue, k.andThen(f));
    }
  }

  /**
   * Modify operation - transforms a value through a Lens.
   *
   * @param <S> The source type
   * @param <A> The value type
   * @param <R> The result type of the continuation
   */
  record Modify<S, A extends @Nullable Object, R>(
      S source, Lens<S, A> optic, Function<A, A> modifier, Function<S, R> k)
      implements OpticOp<S, R> {
    @Override
    public <B> OpticOp<S, B> mapK(Function<? super R, ? extends B> f) {
      return new Modify<>(source, optic, modifier, k.andThen(f));
    }
  }

  /**
   * ModifyAll operation - transforms all values through a Traversal.
   *
   * @param <S> The source type
   * @param <A> The value type
   * @param <R> The result type of the continuation
   */
  record ModifyAll<S, A extends @Nullable Object, R>(
      S source, Traversal<S, A> optic, Function<A, A> modifier, Function<S, R> k)
      implements OpticOp<S, R> {
    @Override
    public <B> OpticOp<S, B> mapK(Function<? super R, ? extends B> f) {
      return new ModifyAll<>(source, optic, modifier, k.andThen(f));
    }
  }

  /**
   * Query operation - checks if any focused element matches a predicate.
   *
   * @param <S> The source type
   * @param <A> The value type
   * @param <R> The result type of the continuation
   */
  record Exists<S extends @Nullable Object, A extends @Nullable Object, R>(
      S source, Fold<S, A> optic, Predicate<A> predicate, Function<Boolean, R> k)
      implements OpticOp<S, R> {
    @Override
    public <B> OpticOp<S, B> mapK(Function<? super R, ? extends B> f) {
      return new Exists<>(source, optic, predicate, k.andThen(f));
    }
  }

  /**
   * Query operation - checks if all focused elements match a predicate.
   *
   * @param <S> The source type
   * @param <A> The value type
   * @param <R> The result type of the continuation
   */
  record All<S extends @Nullable Object, A extends @Nullable Object, R>(
      S source, Fold<S, A> optic, Predicate<A> predicate, Function<Boolean, R> k)
      implements OpticOp<S, R> {
    @Override
    public <B> OpticOp<S, B> mapK(Function<? super R, ? extends B> f) {
      return new All<>(source, optic, predicate, k.andThen(f));
    }
  }

  /**
   * Query operation - counts the number of focused elements.
   *
   * @param <S> The source type
   * @param <A> The value type
   * @param <R> The result type of the continuation
   */
  record Count<S extends @Nullable Object, A extends @Nullable Object, R>(
      S source, Fold<S, A> optic, Function<Integer, R> k) implements OpticOp<S, R> {
    @Override
    public <B> OpticOp<S, B> mapK(Function<? super R, ? extends B> f) {
      return new Count<>(source, optic, k.andThen(f));
    }
  }
}
