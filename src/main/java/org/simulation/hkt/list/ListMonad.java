package org.simulation.hkt.list;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static org.simulation.hkt.list.ListKindHelper.*;

/**
 * Monad implementation for ListKind.
 */
public class ListMonad extends ListFunctor implements Monad<ListKind<?>> {

  @Override
  public <A> ListKind<A> of(A value) {
    // Lifts a single value into a List context (singleton list).
    if (value == null) {
      return wrap(Collections.emptyList());
    }
    return wrap(Collections.singletonList(value));
  }

  @Override
  public <A, B> ListKind<B> flatMap(Function<A, Kind<ListKind<?>, B>> f, Kind<ListKind<?>, A> ma) {
    List<A> listA = unwrap(ma);
    List<B> resultList = new ArrayList<>();

    for (A a : listA) {
      // Apply the function f, which returns a Kind<ListKind<?>, B>
      Kind<ListKind<?>, B> kindB = f.apply(a);
      // Unwrap the result of f to get the inner List<B>
      List<B> listB = unwrap(kindB);
      // Add all elements from the inner list to the final result list
      resultList.addAll(listB);
    }
    // Wrap the flattened list back into ListKind
    return wrap(resultList);
  }

  @Override
  public <A, B> Kind<ListKind<?>, B> ap(Kind<ListKind<?>, Function<A, B>> ff, Kind<ListKind<?>, A> fa) {
    List<Function<A, B>> listF = unwrap(ff);
    List<A> listA = unwrap(fa);
    List<B> resultList = new ArrayList<>();

    // Standard List applicative behavior: apply each function to each element (Cartesian product style)
    for (Function<A, B> f : listF) {
      for (A a : listA) {
        resultList.add(f.apply(a));
      }
    }
    return wrap(resultList);
  }

}

