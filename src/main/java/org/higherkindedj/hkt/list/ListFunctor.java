package org.higherkindedj.hkt.list;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;

/**
 * {@link Functor} instance for {@link ListKind.Witness}.
 *
 * <p>Assumes Functor.java defines: public interface Functor<F> { // F is the HKT marker, e.g.,
 * ListKind.Witness <A, B> @NonNull Kind<F, B> map(@NonNull Function<A, B> f, @NonNull Kind<F, A>
 * fa); }
 */
class ListFunctor implements Functor<ListKind.Witness> {

  public static final ListFunctor INSTANCE = new ListFunctor();

  ListFunctor() {} // Package-private constructor

  @Override
  public <A, B> @NonNull Kind<ListKind.Witness, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<ListKind.Witness, A> fa) {
    // Narrow to ListKind<A> to call unwrap, or directly to ListView<A>
    List<A> listA = ListKind.narrow(fa).unwrap();
    List<B> listB = new ArrayList<>(listA.size());
    for (A a : listA) {
      listB.add(f.apply(a));
    }
    // Use the static factory on ListKind to return the correct HKT
    return ListKind.of(listB);
  }
}
