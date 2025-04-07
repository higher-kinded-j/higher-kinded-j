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

}

