package org.simulation.hkt.list;

import static org.simulation.hkt.list.ListKindHelper.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.simulation.hkt.Functor;
import org.simulation.hkt.Kind;

public class ListFunctor implements Functor<ListKind<?>> {
  @Override
  public <A, B> @NonNull ListKind<B> map(
      @NonNull Function<A, B> f, @NonNull Kind<ListKind<?>, A> fa) {
    List<A> list = unwrap(fa);
    List<B> result = new ArrayList<>(list.size());
    // Note: f.apply(element) could return null if B is nullable
    list.forEach(element -> result.add(f.apply(element)));
    return wrap(result);
  }
}
