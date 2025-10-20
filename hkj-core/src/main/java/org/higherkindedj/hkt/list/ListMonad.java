// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * {@link MonadZero} instance for {@link ListKind.Witness}. This class provides monadic operations
 * for lists, treating lists as a context that can hold zero, one, or multiple values. It also
 * provides a "zero" element (an empty list).
 */
public class ListMonad implements MonadZero<ListKind.Witness> {

  /** A ListMonad singleton */
  public static final ListMonad INSTANCE = new ListMonad();

  /** Private constructor to enforce singleton pattern. */
  private ListMonad() {}

  /**
   * Lifts a single value {@code a} into the List context. If the value is null, it creates an empty
   * list. Otherwise, it creates a singleton list containing the value.
   *
   * @param value The value to lift. Can be {@code null}.
   * @param <A> The type of the value.
   * @return A {@code Kind<ListKind.Witness, A>} representing a list. If value is null, it's an
   *     empty list; otherwise, a list containing the single value. Never null.
   */
  @Override
  public <A> Kind<ListKind.Witness, A> of(@Nullable A value) {
    if (value == null) {
      return LIST.widen(Collections.emptyList());
    }
    return LIST.widen(Collections.singletonList(value));
  }

  /**
   * Applies a function contained within a List context to a value contained within another List
   * context. This involves taking all functions from the first list and applying each one to all
   * values from the second list, collecting all results into a new list.
   *
   * @param ff A {@code Kind<ListKind.Witness, Function<A, B>>} (a list of functions). Must not be
   *     null.
   * @param fa A {@code Kind<ListKind.Witness, A>} (a list of values). Must not be null.
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @return A {@code Kind<ListKind.Witness, B>} containing all results of applying each function in
   *     {@code ff} to each value in {@code fa}. Never null.
   * @throws NullPointerException if {@code ff} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ff} or {@code fa} cannot
   *     be unwrapped to valid List representations.
   */
  @Override
  public <A, B> Kind<ListKind.Witness, B> ap(
      Kind<ListKind.Witness, ? extends Function<A, B>> ff, Kind<ListKind.Witness, A> fa) {

    Validation.kind().requireNonNull(ff, ListMonad.class, AP, "function");
    Validation.kind().requireNonNull(fa, ListMonad.class, AP, "argument");

    List<? extends Function<A, B>> functions = LIST.narrow(ff);
    List<A> values = LIST.narrow(fa);
    List<B> resultList = new ArrayList<>();

    if (functions.isEmpty() || values.isEmpty()) {
      return LIST.widen(Collections.emptyList());
    }

    for (Function<A, B> func : functions) {
      for (A val : values) {
        resultList.add(func.apply(val));
      }
    }
    return LIST.widen(resultList);
  }

  /**
   * Maps a function over a list in a higher-kinded context. This delegates to the {@link
   * ListFunctor} instance.
   *
   * @param f The function to apply. Must not be null.
   * @param fa The {@code Kind<ListKind.Witness, A>} (a list of values). Must not be null.
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @return A {@code Kind<ListKind.Witness, B>} containing the results of applying {@code f} to
   *     each element. Never null.
   * @throws NullPointerException if {@code f} or {@code fa} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fa} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<ListKind.Witness, B> map(
      Function<? super A, ? extends B> f, Kind<ListKind.Witness, A> fa) {

    Validation.function().requireMapper(f, "f", ListMonad.class, MAP);
    Validation.kind().requireNonNull(fa, ListMonad.class, MAP);

    return ListFunctor.INSTANCE.map(f, fa);
  }

  /**
   * Applies a function {@code f} to each element of a list {@code ma}, where {@code f} itself
   * returns a list (wrapped in {@code Kind<ListKind.Witness, B>}). All resulting lists are then
   * concatenated (flattened) into a single result list.
   *
   * @param f A function from {@code A} to {@code Kind<ListKind.Witness, B>} (a list of {@code B}s).
   *     Must not be null.
   * @param ma The input {@code Kind<ListKind.Witness, A>} (a list of {@code A}s). Must not be null.
   * @param <A> The type of elements in the input list.
   * @param <B> The type of elements in the lists produced by the function {@code f}.
   * @return A {@code Kind<ListKind.Witness, B>} which is the flattened list of all results. Never
   *     null.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code ma} or the result of
   *     {@code f} cannot be unwrapped.
   */
  @Override
  public <A, B> Kind<ListKind.Witness, B> flatMap(
      Function<? super A, ? extends Kind<ListKind.Witness, B>> f, Kind<ListKind.Witness, A> ma) {

    Validation.function().requireFlatMapper(f, "f", ListMonad.class, FLAT_MAP);
    Validation.kind().requireNonNull(ma, ListMonad.class, FLAT_MAP);

    List<A> inputList = LIST.narrow(ma);
    List<B> resultList = new ArrayList<>();

    for (A a : inputList) {
      Kind<ListKind.Witness, B> kindB = f.apply(a);
      Validation.function().requireNonNullResult(kindB, "f", ListMonad.class, FLAT_MAP, Kind.class);
      resultList.addAll(LIST.narrow(kindB));
    }
    return LIST.widen(resultList);
  }

  /**
   * Returns the "zero" or "empty" value for this Monad, which is an empty list.
   *
   * @param <T> The type parameter of the Kind.
   * @return A {@code Kind<ListKind.Witness, T>} representing an empty list. Never null.
   */
  @Override
  public <T> Kind<ListKind.Witness, T> zero() {
    return LIST.widen(Collections.emptyList());
  }
}
