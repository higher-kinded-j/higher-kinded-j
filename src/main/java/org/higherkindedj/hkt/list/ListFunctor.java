package org.higherkindedj.hkt.list;

import static org.higherkindedj.hkt.list.ListKindHelper.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

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
