// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import static java.util.Objects.requireNonNull;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.function.Function5;
import org.jspecify.annotations.NullMarked;

/**
 * Represents the Monad type class, extending Applicative. Inherits 'of', 'ap', 'map' and adds
 * 'flatMap' (sequencing operations).
 *
 * @param <M> The witness type for the Monad (e.g., ListKind.class, OptionalKind.class)
 */
@NullMarked
public interface Monad<M extends WitnessArity<TypeArity.Unary>> extends Applicative<M> {

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

  // --- flatMapN implementations ---

  /**
   * Sequences two monadic values and combines their results using a function that returns a monadic
   * value. This is the monadic equivalent of {@link Applicative#map2}.
   *
   * <p>This operation is useful when you need to perform two independent monadic computations and
   * then combine their results in a way that may involve additional effects.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Fetch user and their preferences, then validate together
   * Kind<IO.Witness, User> user = fetchUser(userId);
   * Kind<IO.Witness, Preferences> prefs = fetchPreferences(userId);
   * Kind<IO.Witness, ValidatedProfile> profile = monad.flatMap2(
   *     user,
   *     prefs,
   *     (u, p) -> validateProfile(u, p) // returns Kind<IO.Witness, ValidatedProfile>
   * );
   * }</pre>
   *
   * @param ma The first non-null monadic value {@code Kind<M, A>}.
   * @param mb The second non-null monadic value {@code Kind<M, B>}.
   * @param f A non-null function that takes values from both monads and returns a new monadic
   *     value.
   * @param <A> The type of the value in {@code ma}.
   * @param <B> The type of the value in {@code mb}.
   * @param <R> The type of the result within the returned monad.
   * @return A non-null {@code Kind<M, R>} containing the result of the combined computation.
   * @throws NullPointerException if any argument is null.
   */
  default <A, B, R> Kind<M, R> flatMap2(
      final Kind<M, A> ma,
      final Kind<M, B> mb,
      final BiFunction<? super A, ? super B, ? extends Kind<M, R>> f) {
    requireNonNull(ma, "Kind<M, A> ma for flatMap2 cannot be null");
    requireNonNull(mb, "Kind<M, B> mb for flatMap2 cannot be null");
    requireNonNull(f, "combining function for flatMap2 cannot be null");
    return flatMap(a -> flatMap(b -> f.apply(a, b), mb), ma);
  }

  /**
   * Sequences three monadic values and combines their results using a function that returns a
   * monadic value. This is the monadic equivalent of {@link Applicative#map3}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Combine three database queries with a final validation
   * Kind<IO.Witness, Result> result = monad.flatMap3(
   *     fetchUser(id),
   *     fetchOrder(orderId),
   *     fetchInventory(itemId),
   *     (user, order, inventory) -> validateAndProcess(user, order, inventory)
   * );
   * }</pre>
   *
   * @param ma The first non-null monadic value {@code Kind<M, A>}.
   * @param mb The second non-null monadic value {@code Kind<M, B>}.
   * @param mc The third non-null monadic value {@code Kind<M, C>}.
   * @param f A non-null function that takes values from all three monads and returns a new monadic
   *     value.
   * @param <A> The type of the value in {@code ma}.
   * @param <B> The type of the value in {@code mb}.
   * @param <C> The type of the value in {@code mc}.
   * @param <R> The type of the result within the returned monad.
   * @return A non-null {@code Kind<M, R>} containing the result of the combined computation.
   * @throws NullPointerException if any argument is null.
   */
  default <A, B, C, R> Kind<M, R> flatMap3(
      final Kind<M, A> ma,
      final Kind<M, B> mb,
      final Kind<M, C> mc,
      final Function3<? super A, ? super B, ? super C, ? extends Kind<M, R>> f) {
    requireNonNull(ma, "Kind<M, A> ma for flatMap3 cannot be null");
    requireNonNull(mb, "Kind<M, B> mb for flatMap3 cannot be null");
    requireNonNull(mc, "Kind<M, C> mc for flatMap3 cannot be null");
    requireNonNull(f, "combining function for flatMap3 cannot be null");
    return flatMap(a -> flatMap2(mb, mc, (b, c) -> f.apply(a, b, c)), ma);
  }

  /**
   * Sequences four monadic values and combines their results using a function that returns a
   * monadic value. This is the monadic equivalent of {@link Applicative#map4}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Combine four data sources with validation
   * Kind<IO.Witness, OrderConfirmation> confirmation = monad.flatMap4(
   *     fetchUser(userId),
   *     fetchProduct(productId),
   *     checkInventory(productId),
   *     getShippingInfo(addressId),
   *     (user, product, inventory, shipping) ->
   *         validateAndCreateOrder(user, product, inventory, shipping)
   * );
   * }</pre>
   *
   * @param ma The first non-null monadic value {@code Kind<M, A>}.
   * @param mb The second non-null monadic value {@code Kind<M, B>}.
   * @param mc The third non-null monadic value {@code Kind<M, C>}.
   * @param md The fourth non-null monadic value {@code Kind<M, D>}.
   * @param f A non-null function that takes values from all four monads and returns a new monadic
   *     value.
   * @param <A> The type of the value in {@code ma}.
   * @param <B> The type of the value in {@code mb}.
   * @param <C> The type of the value in {@code mc}.
   * @param <D> The type of the value in {@code md}.
   * @param <R> The type of the result within the returned monad.
   * @return A non-null {@code Kind<M, R>} containing the result of the combined computation.
   * @throws NullPointerException if any argument is null.
   */
  default <A, B, C, D, R> Kind<M, R> flatMap4(
      final Kind<M, A> ma,
      final Kind<M, B> mb,
      final Kind<M, C> mc,
      final Kind<M, D> md,
      final Function4<? super A, ? super B, ? super C, ? super D, ? extends Kind<M, R>> f) {
    requireNonNull(ma, "Kind<M, A> ma for flatMap4 cannot be null");
    requireNonNull(mb, "Kind<M, B> mb for flatMap4 cannot be null");
    requireNonNull(mc, "Kind<M, C> mc for flatMap4 cannot be null");
    requireNonNull(md, "Kind<M, D> md for flatMap4 cannot be null");
    requireNonNull(f, "combining function for flatMap4 cannot be null");
    return flatMap(a -> flatMap3(mb, mc, md, (b, c, d) -> f.apply(a, b, c, d)), ma);
  }

  /**
   * Sequences five monadic values and combines their results using a function that returns a
   * monadic value. This is the monadic equivalent of {@link Applicative#map5}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Combine five data sources for a complex operation
   * Kind<IO.Witness, Report> report = monad.flatMap5(
   *     fetchUser(userId),
   *     fetchAccount(accountId),
   *     getTransactions(accountId),
   *     loadPreferences(userId),
   *     checkCompliance(userId),
   *     (user, account, transactions, prefs, compliance) ->
   *         generateReport(user, account, transactions, prefs, compliance)
   * );
   * }</pre>
   *
   * @param ma The first non-null monadic value {@code Kind<M, A>}.
   * @param mb The second non-null monadic value {@code Kind<M, B>}.
   * @param mc The third non-null monadic value {@code Kind<M, C>}.
   * @param md The fourth non-null monadic value {@code Kind<M, D>}.
   * @param me The fifth non-null monadic value {@code Kind<M, E>}.
   * @param f A non-null function that takes values from all five monads and returns a new monadic
   *     value.
   * @param <A> The type of the value in {@code ma}.
   * @param <B> The type of the value in {@code mb}.
   * @param <C> The type of the value in {@code mc}.
   * @param <D> The type of the value in {@code md}.
   * @param <E> The type of the value in {@code me}.
   * @param <R> The type of the result within the returned monad.
   * @return A non-null {@code Kind<M, R>} containing the result of the combined computation.
   * @throws NullPointerException if any argument is null.
   */
  default <A, B, C, D, E, R> Kind<M, R> flatMap5(
      final Kind<M, A> ma,
      final Kind<M, B> mb,
      final Kind<M, C> mc,
      final Kind<M, D> md,
      final Kind<M, E> me,
      final Function5<? super A, ? super B, ? super C, ? super D, ? super E, ? extends Kind<M, R>>
          f) {
    requireNonNull(ma, "Kind<M, A> ma for flatMap5 cannot be null");
    requireNonNull(mb, "Kind<M, B> mb for flatMap5 cannot be null");
    requireNonNull(mc, "Kind<M, C> mc for flatMap5 cannot be null");
    requireNonNull(md, "Kind<M, D> md for flatMap5 cannot be null");
    requireNonNull(me, "Kind<M, E> me for flatMap5 cannot be null");
    requireNonNull(f, "combining function for flatMap5 cannot be null");
    return flatMap(a -> flatMap4(mb, mc, md, me, (b, c, d, e) -> f.apply(a, b, c, d, e)), ma);
  }
}
