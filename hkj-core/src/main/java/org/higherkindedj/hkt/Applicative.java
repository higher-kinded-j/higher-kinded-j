// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents the Applicative Functor type class, an algebraic structure that lies between {@link
 * Functor} and {@link Monad} in terms of power.
 *
 * <p>An Applicative Functor allows for applying a function wrapped in a context (e.g., {@code
 * Optional<Function<A,B>>}) to a value also wrapped in a context (e.g., {@code Optional<A>}),
 * yielding a result in the same context (e.g., {@code Optional<B>}). This is more powerful than a
 * {@link Functor}, which can only apply a pure function to a wrapped value.
 *
 * <p>Key properties and operations:
 *
 * <ul>
 *   <li>It extends {@link Functor}, so it must provide a {@code map} operation.
 *   <li>{@link #of(Object)} (also known as {@code pure} or {@code point}): Lifts a regular value
 *       {@code A} into the applicative context {@code F<A>}.
 *   <li>{@link #ap(Kind, Kind)} (apply): Takes a context containing a function {@code
 *       F<Function<A,B>>} and a context containing a value {@code F<A>}, and returns a context
 *       containing the result {@code F<B>}.
 * </ul>
 *
 * <p>Applicative Functors must satisfy certain laws (related to identity, composition,
 * homomorphism, and interchange), which ensure their behavior is consistent and predictable. For
 * example:
 *
 * <pre>
 * 1. Identity:        ap(of(x -> x), fa) == fa
 * 2. Homomorphism:    ap(of(f), of(x)) == of(f.apply(x))
 * 3. Interchange:     ap(ff, of(x)) == ap(of(f -> f.apply(x)), ff)
 * 4. Composition:     ap(ap(map(curry(compose), ff), fg), fa) == ap(ff, ap(fg, fa))
 * (where compose is function composition, and curry converts (B->C) to A->B->C)
 * </pre>
 *
 * (Note: Proving these laws is typically done for specific implementations.)
 *
 * <p>The {@code mapN} methods (e.g., {@link #map2(Kind, Kind, BiFunction)}, {@link #map3(Kind,
 * Kind, Kind, Function3)}) are convenient derived operations that allow combining multiple values
 * within the applicative context using a pure N-ary function.
 *
 * @param <F> The higher-kinded type witness representing the type constructor of the applicative
 *     context (e.g., {@code OptionalKind.Witness}, {@code ListKind.Witness}).
 * @see Functor
 * @see Monad
 * @see Kind
 */
public interface Applicative<F> extends Functor<F> {

  /**
   * Lifts a pure value {@code value} into the applicative context {@code F}. This is also known in
   * other contexts as {@code pure}, {@code return} (in Haskell for Monads, which are Applicatives),
   * or {@code unit}.
   *
   * <p>For example:
   *
   * <ul>
   *   <li>For {@code Optional}, {@code of(x)} would be {@code Optional.ofNullable(x)}.
   *   <li>For {@code List}, {@code of(x)} would be {@code List.of(x)}.
   * </ul>
   *
   * @param value The value to lift into the context. The nullability of this value depends on the
   *     specific applicative context {@code F} (e.g., {@code Optional} can handle a {@code null}
   *     input to become {@code Optional.empty()} or {@code Optional.ofNullable()}, while a custom
   *     list might not allow null elements).
   * @param <A> The type of the value being lifted.
   * @return A non-null {@link Kind Kind&lt;F, A&gt;} representing the value {@code A} wrapped in
   *     the applicative context {@code F}.
   */
  <A> @NonNull Kind<F, A> of(@Nullable A value);

  /**
   * Applies a function wrapped in an applicative context {@code ff} to a value wrapped in the same
   * applicative context {@code fa}.
   *
   * <p>This is the core operation distinguishing Applicatives from Functors. It allows function
   * application where both the function and its arguments are "effectful" or "contextual".
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Assume OptionalApplicative implements Applicative<OptionalKind.Witness>
   * Kind<OptionalKind.Witness, Function<Integer, String>> fOpt = OptionalApplicative.of(x -> "Value: " + x);
   * Kind<OptionalKind.Witness, Integer> valOpt = OptionalApplicative.of(10);
   * Kind<OptionalKind.Witness, String> resultOpt = OptionalApplicative.ap(fOpt, valOpt);
   * // resultOpt would be OptionalKind containing "Value: 10"
   *
   * Kind<OptionalKind.Witness, Integer> emptyOpt = OptionalApplicative.of(null); // or Optional.empty()
   * Kind<OptionalKind.Witness, String> resultEmpty = OptionalApplicative.ap(fOpt, emptyOpt);
   * // resultEmpty would be an empty OptionalKind
   * }</pre>
   *
   * @param ff A non-null {@link Kind Kind&lt;F, Function&lt;A, B&gt;&gt;} representing the function
   *     wrapped in the applicative context {@code F}.
   * @param fa A non-null {@link Kind Kind&lt;F, A&gt;} representing the argument value wrapped in
   *     the applicative context {@code F}.
   * @param <A> The input type of the function and the type of the value in {@code fa}.
   * @param <B> The output type of the function and the type of the value in the resulting context.
   * @return A non-null {@link Kind Kind&lt;F, B&gt;} representing the result of applying the
   *     function within the context {@code F}. If either {@code ff} or {@code fa} represents an
   *     "empty" or "failed" context (e.g., {@code Optional.empty()}), the result is typically also
   *     such a context.
   */
  <A, B> @NonNull Kind<F, B> ap(@NonNull Kind<F, Function<A, B>> ff, @NonNull Kind<F, A> fa);

  // --- mapN implementations ---
  // These are default methods providing convenient ways to combine multiple applicative values.
  // They are typically implemented using `ap` and `map`.

  /**
   * Combines two values {@code fa} and {@code fb}, both in the applicative context {@code F}, using
   * a curried pure function {@code f: A -> (B -> C)}.
   *
   * <p>This is a common way {@code map2} is defined in terms of {@code ap} and {@code map}. {@code
   * map(f, fa)} results in {@code Kind<F, Function<B, C>>}, which is then applied to {@code fb}.
   *
   * @param fa The first non-null applicative value {@code Kind<F, A>}.
   * @param fb The second non-null applicative value {@code Kind<F, B>}.
   * @param f A non-null pure function that takes a value of type {@code A} and returns a function
   *     from {@code B} to {@code C}.
   * @param <A> The type of the value in {@code fa}.
   * @param <B> The type of the value in {@code fb}.
   * @param <C> The type of the result of the combined computation.
   * @return A non-null {@code Kind<F, C>} containing the result of applying the curried function
   *     {@code f} to the values from {@code fa} and {@code fb} within the context {@code F}.
   */
  default <A, B, C> @NonNull Kind<F, C> map2(
      @NonNull Kind<F, A> fa, @NonNull Kind<F, B> fb, @NonNull Function<A, Function<B, C>> f) {
    // map(f, fa) yields Kind<F, Function<B, C>>
    // ap( Kind<F, Function<B, C>>, Kind<F, B> ) yields Kind<F, C>
    return ap(map(f, fa), fb);
  }

  /**
   * Combines two values {@code fa} and {@code fb}, both in the applicative context {@code F}, using
   * a pure {@link BiFunction BiFunction&lt;A, B, C&gt;}.
   *
   * <p>This is often a more convenient signature for {@code map2}. It's implemented by currying the
   * {@link BiFunction} and then using the other {@code map2} or {@code ap} directly.
   *
   * @param fa The first non-null applicative value {@code Kind<F, A>}.
   * @param fb The second non-null applicative value {@code Kind<F, B>}.
   * @param f A non-null pure {@link BiFunction} to combine the values from {@code fa} and {@code
   *     fb}.
   * @param <A> The type of the value in {@code fa}.
   * @param <B> The type of the value in {@code fb}.
   * @param <C> The type of the result of applying {@code f}.
   * @return A non-null {@code Kind<F, C>} containing the result of applying {@code f} to the values
   *     from {@code fa} and {@code fb} within the context {@code F}.
   */
  default <A, B, C> @NonNull Kind<F, C> map2(
      @NonNull Kind<F, A> fa, @NonNull Kind<F, B> fb, @NonNull BiFunction<A, B, C> f) {
    // Curry the BiFunction: A -> (B -> C)
    Function<A, Function<B, C>> curriedF = a -> b -> f.apply(a, b);
    // Delegate to the other map2 or implement directly using ap:
    // return ap(map(curriedF, fa), fb);
    return map2(fa, fb, curriedF);
  }

  /**
   * Combines three values {@code fa}, {@code fb}, and {@code fc}, all in the applicative context
   * {@code F}, using a pure {@link Function3 Function3&lt;A, B, C, R&gt;}.
   *
   * @param fa The first non-null applicative value {@code Kind<F, A>}.
   * @param fb The second non-null applicative value {@code Kind<F, B>}.
   * @param fc The third non-null applicative value {@code Kind<F, C>}.
   * @param f A non-null pure {@link Function3} to combine the values from {@code fa}, {@code fb},
   *     and {@code fc}.
   * @param <A> The type of the value in {@code fa}.
   * @param <B> The type of the value in {@code fb}.
   * @param <C> The type of the value in {@code fc}.
   * @param <R> The type of the result of applying {@code f}.
   * @return A non-null {@code Kind<F, R>} containing the result of applying {@code f} to the values
   *     from {@code fa}, {@code fb}, and {@code fc} within the context {@code F}.
   */
  default <A, B, C, R> @NonNull Kind<F, R> map3(
      @NonNull Kind<F, A> fa,
      @NonNull Kind<F, B> fb,
      @NonNull Kind<F, C> fc,
      @NonNull Function3<A, B, C, R> f) {
    // Curry the function: A -> (B -> (C -> R))
    Function<A, Function<B, Function<C, R>>> curriedF = a -> b -> c -> f.apply(a, b, c);
    // Chained application:
    // map(curriedF, fa)                                 -> Kind<F, Function<B, Function<C, R>>>
    // ap(map(curriedF, fa), fb)                         -> Kind<F, Function<C, R>>
    // ap(ap(map(curriedF, fa), fb), fc)                 -> Kind<F, R>
    return ap(ap(map(curriedF, fa), fb), fc);
  }

  /**
   * Combines four values {@code fa}, {@code fb}, {@code fc}, and {@code fd}, all in the applicative
   * context {@code F}, using a pure {@link Function4 Function4&lt;A, B, C, D, R&gt;}.
   *
   * @param fa The first non-null applicative value {@code Kind<F, A>}.
   * @param fb The second non-null applicative value {@code Kind<F, B>}.
   * @param fc The third non-null applicative value {@code Kind<F, C>}.
   * @param fd The fourth non-null applicative value {@code Kind<F, D>}.
   * @param f A non-null pure {@link Function4} to combine the values.
   * @param <A> The type of the value in {@code fa}.
   * @param <B> The type of the value in {@code fb}.
   * @param <C> The type of the value in {@code fc}.
   * @param <D> The type of the value in {@code fd}.
   * @param <R> The type of the result of applying {@code f}.
   * @return A non-null {@code Kind<F, R>} containing the result of applying {@code f} to the values
   *     from the four applicative arguments within the context {@code F}.
   */
  default <A, B, C, D, R> @NonNull Kind<F, R> map4(
      @NonNull Kind<F, A> fa,
      @NonNull Kind<F, B> fb,
      @NonNull Kind<F, C> fc,
      @NonNull Kind<F, D> fd,
      @NonNull Function4<A, B, C, D, R> f) {
    // Curry the function: A -> (B -> (C -> (D -> R)))
    Function<A, Function<B, Function<C, Function<D, R>>>> curriedF =
        a -> b -> c -> d -> f.apply(a, b, c, d);
    // Chained application:
    return ap(ap(ap(map(curriedF, fa), fb), fc), fd);
  }
}
