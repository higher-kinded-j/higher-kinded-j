package org.simulation.hkt.list;

import org.simulation.hkt.Kind;
import org.simulation.hkt.Functor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import static org.simulation.hkt.list.ListKindHelper.*;


public class ListFunctor implements Functor<ListKind<?>> {
  @Override
  public <A, B> ListKind<B> map(Function<A, B> f, Kind<ListKind<?>, A> fa) {
    List<A> list = unwrap(fa);
    List<B> result = new ArrayList<>();
    list.forEach(element -> result.add(f.apply(element)));
    return wrap(result);
  }

}


