package org.higherkindedj.hkt.list;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * {@link Monad} instance for {@link ListKind.Witness}. This class provides monadic operations for
 * lists, treating lists as context that can hold zero, one, or multiple values.
 */
public class ListMonad implements Monad<ListKind.Witness> {

  public static final ListMonad INSTANCE = new ListMonad();

  // Constructor can be private if INSTANCE is the only way to get it.
  private ListMonad() {}

  /**
   * Lifts a single value {@code a} into the List context. If the value is null, it creates an empty
   * list. Otherwise, it creates a singleton list containing the value.
   *
   * @param value The value to lift. Can be {@code null}.
   * @param <A> The type of the value.
   * @return A {@code Kind<ListKind.Witness, A>} representing a list. If value is null, it's an
   *     empty list; otherwise, a list containing the single value.
   */
  @Override
  public <A> @NonNull Kind<ListKind.Witness, A> of(@Nullable A value) {
    if (value == null) {
      return ListKind.of(Collections.emptyList()); // Create an empty list for null input
    }
    return ListKind.of(
        Collections.singletonList(value)); // Create a singleton list for non-null input
  }

  /**
   * Applies a function contained within a List context to a value contained within another List
   * context. This involves taking all functions from the first list and applying each one to all
   * values from the second list, collecting all results into a new list.
   *
   * @param ff A {@code Kind<ListKind.Witness, Function<A, B>>} (a list of functions).
   * @param fa A {@code Kind<ListKind.Witness, A>} (a list of values).
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @return A {@code Kind<ListKind.Witness, B>} containing all results of applying each function in
   *     {@code ff} to each value in {@code fa}.
   */
  @Override
  public <A, B> @NonNull Kind<ListKind.Witness, B> ap(
      @NonNull Kind<ListKind.Witness, Function<A, B>> ff, @NonNull Kind<ListKind.Witness, A> fa) {

    List<Function<A, B>> functions = ListKind.narrow(ff).unwrap();
    List<A> values = ListKind.narrow(fa).unwrap();
    List<B> resultList = new ArrayList<>();

    if (functions.isEmpty() || values.isEmpty()) {
      return ListKind.of(Collections.emptyList());
    }

    for (Function<A, B> func : functions) {
      for (A val : values) {
        resultList.add(func.apply(val));
      }
    }
    return ListKind.of(resultList);
  }

  /**
   * Maps a function over a list in a higher-kinded context. This delegates to the {@link
   * ListFunctor} instance.
   *
   * @param f The function to apply.
   * @param fa The {@code Kind<ListKind.Witness, A>} (a list of values).
   * @param <A> The input type of the function.
   * @param <B> The output type of the function.
   * @return A {@code Kind<ListKind.Witness, B>} containing the results of applying {@code f} to
   *     each element.
   */
  @Override
  public <A, B> @NonNull Kind<ListKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<ListKind.Witness, A> fa) {
    return ListFunctor.INSTANCE.map(f, fa);
  }

  /**
   * Applies a function {@code f} to each element of a list {@code ma}, where {@code f} itself
   * returns a list (wrapped in {@code Kind<ListKind.Witness, B>}). All resulting lists are then
   * concatenated (flattened) into a single result list.
   *
   * @param f A function from {@code A} to {@code Kind<ListKind.Witness, B>} (a list of {@code B}s).
   * @param ma The input {@code Kind<ListKind.Witness, A>} (a list of {@code A}s).
   * @param <A> The type of elements in the input list.
   * @param <B> The type of elements in the lists produced by the function {@code f}.
   * @return A {@code Kind<ListKind.Witness, B>} which is the flattened list of all results.
   */
  @Override
  public <A, B> @NonNull Kind<ListKind.Witness, B> flatMap(
      @NonNull Function<A, Kind<ListKind.Witness, B>> f, @NonNull Kind<ListKind.Witness, A> ma) {

    List<A> inputList = ListKind.narrow(ma).unwrap();
    List<B> resultList = new ArrayList<>();

    for (A a : inputList) {
      Kind<ListKind.Witness, B> kindB = f.apply(a); // This is a ListKind<B> (ListView<B>)
      resultList.addAll(ListKind.narrow(kindB).unwrap());
    }
    return ListKind.of(resultList);
  }
}
