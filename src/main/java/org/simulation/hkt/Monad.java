package org.simulation.hkt;

import org.jspecify.annotations.NonNull;
import java.util.function.Function;

/**
 * Represents the Monad type class, extending Applicative.
 * Inherits 'of', 'ap', 'map' and adds 'flatMap' (sequencing operations).
 *
 * @param <M> The witness type for the Monad (e.g., ListKind.class, OptionalKind.class)
 */
public interface Monad<M> extends Applicative<M> {

  // 'of' is now inherited from Applicative
  // 'map' is inherited from Functor (via Applicative)
  // 'ap' is inherited from Applicative

  /**
   * Sequences monadic operations. Takes a monadic value and a function that produces
   * a new monadic value, returning the result within the monadic context.
   * Also known as 'bind' or '>>='.
   *
   * @param f   The function to apply, which returns a monadic value (e.g., A -> ListKind<B>). Assumed non-null.
   * @param ma  The input monadic value (e.g., ListKind<A>). Assumed non-null.
   * @param <A> The input type within the monad.
   * @param <B> The result type within the monad.
   * @return The resulting monadic value (e.g., ListKind<B>). Guaranteed non-null.
   */
  <A, B> @NonNull Kind<M, B> flatMap(@NonNull Function<A, Kind<M, B>> f, @NonNull Kind<M, A> ma);

  // flatMap can define ap: ap(ff, fa) = flatMap(f -> map(f, fa), ff)
  // You could provide a default implementation of ap here if desired,
  // but typically specific implementations are more efficient.
  // @Override
  // default <A, B> Kind<M, B> ap(Kind<M, Function<A, B>> ff, Kind<M, A> fa) {
  //     return flatMap(f -> map(f, fa), ff);
  // }
}