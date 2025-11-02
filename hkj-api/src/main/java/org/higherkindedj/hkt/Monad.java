// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static java.util.Objects.requireNonNull;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/**
 * Represents the Monad type class, extending Applicative. Inherits 'of', 'ap', 'map' and adds
 * 'flatMap' (sequencing operations).
 *
 * @param <M> The witness type for the Monad (e.g., ListKind.class, OptionalKind.class)
 */
@NullMarked
public interface Monad<M> extends Applicative<M> {

  // 'of' is  inherited from Applicative
  // 'map' is inherited from Functor (via Applicative)
  // 'ap' is inherited from Applicative

  /**
   * Sequences monadic operations. Takes a monadic value and a function that produces a new monadic
   * value, returning the result within the monadic context. Also known as 'bind' or '>>='.
   *
   * @param f The function to apply, which returns a monadic value (e.g., A -> {@code ListKind<B>}).
   *     Assumed non-null.
   * @param ma The input monadic value (e.g., {@code ListKind<A>}). Assumed non-null.
   * @param <A> The input type within the monad.
   * @param <B> The result type within the monad.
   * @return The resulting monadic value (e.g., {@code ListKind<B>}). Guaranteed non-null.
   */
  <A, B> Kind<M, B> flatMap(final Function<? super A, ? extends Kind<M, B>> f, final Kind<M, A> ma);

  /**
   * Conditionally sequences a monadic operation. If the predicate is true, it applies {@code
   * flatMap} with the given function; otherwise, it returns the original monadic value lifted into
   * the new type.
   *
   * @param predicate The condition to test on the monad's value.
   * @param ifTrue The function to apply if the predicate is true.
   * @param ifFalse The function to apply if the predicate is false.
   * @param ma The input monadic value.
   * @param <A> The input type within the monad.
   * @param <B> The result type within the monad.
   * @return A new monadic value.
   */
  default <A, B> Kind<M, B> flatMapIfOrElse(
      final Predicate<? super A> predicate,
      final Function<? super A, ? extends Kind<M, B>> ifTrue,
      final Function<? super A, ? extends Kind<M, B>> ifFalse,
      final Kind<M, A> ma) {
    // Both branches are guaranteed by the compiler to return a Kind<M, B>.
    return flatMap(a -> predicate.test(a) ? ifTrue.apply(a) : ifFalse.apply(a), ma);
  }

  /**
   * Allows "peeking" at the value inside the Monad without changing the flow. This is useful for
   * logging or debugging.
   *
   * @param action The consumer to execute on the value inside the monad.
   * @param ma The input monadic value.
   * @param <A> The type of the value in the monad.
   * @return The original monadic value.
   */
  default <A> Kind<M, A> peek(final Consumer<? super A> action, final Kind<M, A> ma) {
    return map(
        a -> {
          action.accept(a);
          return a;
        },
        ma);
  }

  /**
   * Discards the result of a monadic computation, replacing it with Unit. This explicitly
   * represents "computation completed but result is not interesting".
   *
   * <p>This is more explicit and type-safe than using {@code as(null, ma)}.
   *
   * <p><b>Example:</b>
   *
   * <pre>{@code
   * // Database write returns row count, but we don't care
   * Kind<IO.Witness, Integer> write = database.write(data);
   * Kind<IO.Witness, Unit> justWrite = monad.asUnit(write);
   *
   * // Chain with other effects
   * Kind<IO.Witness, Unit> sequence = monad.flatMap(
   *     _ -> monad.asUnit(database.write(moreData)),
   *     justWrite
   * );
   * }</pre>
   *
   * @param ma The monadic computation whose result will be discarded
   * @param <A> The type of the discarded result
   * @return The same computation structure, but with Unit as the result
   */
  default <A> Kind<M, Unit> asUnit(final Kind<M, A> ma) {
    return map(_ -> Unit.INSTANCE, ma);
  }

  /**
   * Keeps the effect of this Monad, but replaces the result with the given value.
   *
   * <p><b>Null Safety:</b> If you want to explicitly represent "no result", use {@link
   * #asUnit(Kind)} instead of passing null.
   *
   * @param b The new value to replace the result with. Must not be null.
   * @param ma The input monadic value.
   * @param <A> The original type of the value in the monad.
   * @param <B> The type of the new value.
   * @return A new monadic value with the result replaced by 'b'.
   * @throws NullPointerException if b is null (use asUnit() for Unit results)
   */
  default <A, B> Kind<M, B> as(final B b, final Kind<M, A> ma) {
    requireNonNull(b, "Use asUnit() instead of as(null, ma)");
    return map(_ -> b, ma);
  }
}
