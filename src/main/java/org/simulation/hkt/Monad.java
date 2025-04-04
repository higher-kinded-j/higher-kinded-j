package org.simulation.hkt;

import java.util.function.Function;

/**
 * Represents the Monad type class, extending Functor.
 * Provides 'pure' (lifting a value) and 'flatMap' (sequencing operations).
 *
 * @param <M> The witness type for the Monad (e.g., ListKind.class, OptionalKind.class)
 */
public interface Monad<M> extends Functor<M> {

  /**
   * Lifts a plain value 'a' into the monadic context.
   * Also known as 'return' or 'unit'.
   *
   * @param value The value to lift.
   * @param <A>   The type of the value.
   * @return The value wrapped in the monadic context (e.g., ListKind<A>, OptionalKind<A>).
   */
  <A> Kind<M, A> pure(A value);

  /**
   * Sequences monadic operations. Takes a monadic value and a function that produces
   * a new monadic value, returning the result within the monadic context.
   * Also known as 'bind' or '>>='.
   *
   * @param f   The function to apply, which returns a monadic value (e.g., A -> ListKind<B>).
   * @param ma  The input monadic value (e.g., ListKind<A>).
   * @param <A> The input type within the monad.
   * @param <B> The result type within the monad.
   * @return The resulting monadic value (e.g., ListKind<B>).
   */
  <A, B> Kind<M, B> flatMap(Function<A, Kind<M, B>> f, Kind<M, A> ma);


}
