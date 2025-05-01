package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Monad implementation for ListKind. */
public class ListMonad extends ListFunctor implements Monad<ListKind<?>> {

  @Override
  public <A> @NonNull ListKind<A> of(@Nullable A value) { // Value can be null
    // Lifts a single value into a List context (singleton list).
    // Represent null input as empty list for consistency with other Monads like Optional/Maybe
    if (value == null) {
      return wrap(Collections.emptyList());
    }
    return wrap(Collections.singletonList(value));
  }

  @Override
  public <A, B> @NonNull ListKind<B> flatMap(
      @NonNull Function<A, Kind<ListKind<?>, B>> f, @NonNull Kind<ListKind<?>, A> ma) {
    List<A> listA = unwrap(ma); // Handles null/invalid ma
    List<B> resultList = new ArrayList<>();

    for (A a : listA) { // `a` can be null if listA contains nulls
      // Apply the function f, which returns a Kind<ListKind<?>, B>
      Kind<ListKind<?>, B> kindB = f.apply(a); // f is NonNull
      // Unwrap the result of f to get the inner List<B>
      List<B> listB = unwrap(kindB); // Returns NonNull List
      // Add all elements from the inner list to the final result list
      resultList.addAll(listB); // listB is NonNull
    }
    // Wrap the flattened list back into ListKind
    return wrap(resultList); // wrap requires NonNull List
  }

  @Override
  public <A, B> @NonNull Kind<ListKind<?>, B> ap(
      @NonNull Kind<ListKind<?>, Function<A, B>> ff, @NonNull Kind<ListKind<?>, A> fa) {
    List<Function<A, B>> listF = unwrap(ff); // Handles null/invalid ff
    List<A> listA = unwrap(fa); // Handles null/invalid fa
    List<B> resultList = new ArrayList<>();

    // Standard List applicative behavior: apply each function to each element (Cartesian product
    // style)
    for (Function<A, B> f : listF) { // f is NonNull (assuming listF doesn't contain null functions)
      for (A a : listA) { // a can be null
        // Result of f.apply(a) can be null if B is nullable
        resultList.add(f.apply(a));
      }
    }
    return wrap(resultList); // wrap requires NonNull List
  }
}
