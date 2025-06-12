// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.function.Function5;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple3;
import org.higherkindedj.hkt.tuple.Tuple4;
import org.higherkindedj.hkt.tuple.Tuple5;

/**
 * Provides a statically-typed, fluent for-comprehension builder for monadic types, simulating a
 * feature common in languages like Scala.
 *
 * <p>For-comprehensions offer a convenient and readable syntax for composing sequences of monadic
 * operations (like {@code flatMap} and {@code map}). This builder de-sugars the comprehension into
 * a series of monadic calls, guiding the user through the composition process while ensuring type
 * safety at each step.
 *
 * <h3>Usage</h3>
 *
 * <p>A comprehension is a chain of operations that includes generators, bindings, filters, and a
 * final projection:
 *
 * <ul>
 *   <li><b>Generators ({@code .from(...)}):</b> These are the core of the comprehension, equivalent
 *       to a {@code flatMap}. Each generator takes a value from a previous step and produces a new
 *       monadic value, which is then flattened into the ongoing computation. The results of all
 *       generators are accumulated in a tuple.
 *   <li><b>Bindings ({@code .let(...)}):</b> Binds the result of a pure computation to a new
 *       variable in the comprehension's scope. This is equivalent to a {@code map} operation that
 *       adds the computed value to the accumulated tuple.
 *   <li><b>Filters ({@code .when(...)}):</b> Filters the results based on a predicate. If the
 *       predicate returns {@code false}, the computation for that path is short-circuited. This
 *       operation is only available for monads that implement {@link MonadZero}, which provides an
 *       "empty" or "zero" element (e.g., an empty list, {@code Optional.empty()}).
 *   <li><b>Projection ({@code .yield(...)}):</b> Concludes the comprehension by applying a function
 *       to the accumulated results from all previous steps, producing the final monadic value.
 * </ul>
 *
 * <h3>Example with List (a {@code MonadZero})</h3>
 *
 * <pre>{@code
 * // Imports for context
 * import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
 * import java.util.Arrays;
 *
 * ListMonad listMonad = ListMonad.INSTANCE;
 * Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2, 3));
 * Kind<ListKind.Witness, Integer> list2 = LIST.widen(Arrays.asList(10, 20));
 *
 * // Equivalent to Scala: for { a <- list1; b <- list2 if (a+b) % 2 != 0; c = "Sum: " + (a+b) } yield s"$a + $b = $c"
 * Kind<ListKind.Witness, String> result =
 * For.from(listMonad, list1)                       // Generator: a <- list1
 * .from(a -> list2)                             // Generator: b <- list2
 * .when(t -> (t._1() + t._2()) % 2 != 0)        // Filter: if (a+b) is odd
 * .let(t -> "Sum: " + (t._1() + t._2()))        // Binding: c = "Sum: " + (a+b)
 * .yield((a, b, c) -> a + "+" + b + " = " + c); // Projection
 *
 * List<String> finalResult = LIST.narrow(result);
 * // finalResult: ["1+10 = Sum: 11", "1+20 = Sum: 21", "3+10 = Sum: 13", "3+20 = Sum: 23"]
 * }</pre>
 *
 * @see Monad
 * @see MonadZero
 * @see Kind
 */
public final class For {

  private For() {} // Static access only

  /**
   * Initiates a for-comprehension for any {@link Monad}. The resulting builder chain will not
   * support filtering with {@code .when(...)}.
   *
   * @param monad The Monad instance that defines the behavior of {@code flatMap} and {@code map}.
   * @param source The initial monadic source, the first generator in the comprehension.
   * @param <M> The witness type of the Monad.
   * @param <A> The value type of the source.
   * @return The first step of the builder, ready for the next operation.
   */
  public static <M, A> MonadicSteps1<M, A> from(Monad<M> monad, Kind<M, A> source) {
    return new MonadicSteps1<>(monad, source);
  }

  /**
   * Initiates a for-comprehension for a {@link MonadZero}. The resulting builder chain supports
   * filtering with {@code .when(...)}.
   *
   * @param monad The MonadZero instance, providing {@code zero()} for filtering.
   * @param source The initial monadic source, the first generator in the comprehension.
   * @param <M> The witness type of the Monad.
   * @param <A> The value type of the source.
   * @return The first step of the filterable builder, ready for the next operation.
   */
  public static <M, A> FilterableSteps1<M, A> from(MonadZero<M> monad, Kind<M, A> source) {
    return new FilterableSteps1<>(monad, source);
  }

  /**
   * A marker interface for all builder steps, ensuring they can be permitted by a sealed interface.
   * This is an internal detail to organize the different step types.
   *
   * @param <M> The witness type of the Monad.
   */
  public sealed interface Steps<M>
      permits MonadicSteps1,
          MonadicSteps2,
          MonadicSteps3,
          MonadicSteps4,
          MonadicSteps5,
          FilterableSteps1,
          FilterableSteps2,
          FilterableSteps3,
          FilterableSteps4,
          FilterableSteps5 {}

  // --- Monadic (Non-Filterable) Steps ---

  /**
   * Represents the first step in a non-filterable for-comprehension, holding a single monadic
   * value.
   *
   * @param <M> The witness type of the Monad.
   * @param <A> The value type of the initial computation.
   */
  public static final class MonadicSteps1<M, A> implements Steps<M> {
    private final Monad<M> monad;
    private final Kind<M, A> computation;

    private MonadicSteps1(Monad<M> monad, Kind<M, A> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    /**
     * Binds the result of another monadic computation (a generator).
     *
     * <p>This corresponds to a {@code flatMap} operation. The function {@code next} is applied to
     * the result of the first step, and the resulting monadic value is flattened into the
     * comprehension.
     *
     * @param next A function that takes the result of the first step (type {@code A}) and returns a
     *     new monadic computation {@code Kind<M, B>}.
     * @param <B> The value type of the new monadic computation.
     * @return The next step in the builder, now tracking types {@code A} and {@code B} as a {@code
     *     Tuple2<A, B>}.
     */
    public <B> MonadicSteps2<M, A, B> from(Function<A, Kind<M, B>> next) {
      Kind<M, Tuple2<A, B>> newComputation =
          monad.flatMap(a -> monad.map(b -> Tuple.of(a, b), next.apply(a)), this.computation);
      return new MonadicSteps2<>(monad, newComputation);
    }

    /**
     * Binds the result of a pure computation.
     *
     * <p>This corresponds to a {@code map} operation. The function {@code f} is applied to the
     * result of the first step to produce a new value, which is then carried along in the
     * comprehension.
     *
     * @param f A function that takes the result of the first step (type {@code A}) and returns a
     *     pure value of type {@code B}.
     * @param <B> The type of the computed value.
     * @return The next step in the builder, now tracking types {@code A} and {@code B} as a {@code
     *     Tuple2<A, B>}.
     */
    public <B> MonadicSteps2<M, A, B> let(Function<A, B> f) {
      Kind<M, Tuple2<A, B>> newComputation =
          monad.map(a -> Tuple.of(a, f.apply(a)), this.computation);
      return new MonadicSteps2<>(monad, newComputation);
    }

    /**
     * Completes the for-comprehension by applying a function to the final result.
     *
     * @param f A function to transform the final value of type {@code A} into the result type
     *     {@code R}.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function<A, R> f) {
      return monad.map(f, computation);
    }
  }

  /**
   * Represents the second step in a non-filterable for-comprehension, holding a tuple of two
   * results.
   *
   * @param <M> The witness type of the Monad.
   * @param <A> The value type of the first result.
   * @param <B> The value type of the second result.
   */
  public static final class MonadicSteps2<M, A, B> implements Steps<M> {
    private final Monad<M> monad;
    private final Kind<M, Tuple2<A, B>> computation;

    private MonadicSteps2(Monad<M> monad, Kind<M, Tuple2<A, B>> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    /**
     * Adds a new monadic generator to the comprehension.
     *
     * @param next A function that takes the current tuple of results (a {@code Tuple2<A, B>}) and
     *     returns a new monadic computation.
     * @param <C> The value type of the new computation.
     * @return The next step in the builder, now tracking three results as a {@code Tuple3<A, B,
     *     C>}.
     */
    public <C> MonadicSteps3<M, A, B, C> from(Function<Tuple2<A, B>, Kind<M, C>> next) {
      Kind<M, Tuple3<A, B, C>> newComputation =
          monad.flatMap(
              ab -> monad.map(c -> Tuple.of(ab._1(), ab._2(), c), next.apply(ab)),
              this.computation);
      return new MonadicSteps3<>(monad, newComputation);
    }

    /**
     * Adds a pure computation to the comprehension.
     *
     * @param f A function that takes the current tuple of results (a {@code Tuple2<A, B>}) and
     *     returns a new pure value.
     * @param <C> The type of the new computed value.
     * @return The next step in the builder, now tracking three results as a {@code Tuple3<A, B,
     *     C>}.
     */
    public <C> MonadicSteps3<M, A, B, C> let(Function<Tuple2<A, B>, C> f) {
      Kind<M, Tuple3<A, B, C>> newComputation =
          monad.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), this.computation);
      return new MonadicSteps3<>(monad, newComputation);
    }

    /**
     * Completes the comprehension by yielding a final result from the two accumulated values.
     *
     * @param f A function that combines the two results ({@code A}, {@code B}) into a final value.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(BiFunction<A, B, R> f) {
      return monad.map(t -> f.apply(t._1(), t._2()), computation);
    }

    /**
     * Completes the comprehension by yielding a final result from the tuple of accumulated values.
     *
     * @param f A function that transforms the tuple of results (a {@code Tuple2<A, B>}) into a
     *     final value.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function<Tuple2<A, B>, R> f) {
      return monad.map(f, computation);
    }
  }

  /**
   * Represents the third step in a non-filterable for-comprehension, holding a tuple of three
   * results.
   */
  public static final class MonadicSteps3<M, A, B, C> implements Steps<M> {
    private final Monad<M> monad;
    private final Kind<M, Tuple3<A, B, C>> computation;

    private MonadicSteps3(Monad<M> monad, Kind<M, Tuple3<A, B, C>> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    /**
     * Adds a new monadic generator to the comprehension.
     *
     * @param next A function taking the current 3-tuple of results and returning a new monadic
     *     computation.
     * @param <D> The value type of the new computation.
     * @return The next step in the builder, now tracking four results.
     */
    public <D> MonadicSteps4<M, A, B, C, D> from(Function<Tuple3<A, B, C>, Kind<M, D>> next) {
      Kind<M, Tuple4<A, B, C, D>> newComputation =
          monad.flatMap(
              abc -> monad.map(d -> Tuple.of(abc._1(), abc._2(), abc._3(), d), next.apply(abc)),
              this.computation);
      return new MonadicSteps4<>(monad, newComputation);
    }

    /**
     * Adds a pure computation to the comprehension.
     *
     * @param f A function taking the current 3-tuple of results and returning a new pure value.
     * @param <D> The type of the new computed value.
     * @return The next step in the builder, now tracking four results.
     */
    public <D> MonadicSteps4<M, A, B, C, D> let(Function<Tuple3<A, B, C>, D> f) {
      Kind<M, Tuple4<A, B, C, D>> newComputation =
          monad.map(abc -> Tuple.of(abc._1(), abc._2(), abc._3(), f.apply(abc)), this.computation);
      return new MonadicSteps4<>(monad, newComputation);
    }

    /**
     * Completes the comprehension by yielding a final result from the three accumulated values.
     *
     * @param f A function that combines the three results into a final value.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function3<A, B, C, R> f) {
      return monad.map(t -> f.apply(t._1(), t._2(), t._3()), computation);
    }

    /**
     * Completes the comprehension by yielding a final result from the tuple of accumulated values.
     *
     * @param f A function that transforms the tuple of results into a final value.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function<Tuple3<A, B, C>, R> f) {
      return monad.map(f, computation);
    }
  }

  /**
   * Represents the fourth step in a non-filterable for-comprehension, holding a tuple of four
   * results.
   */
  public static final class MonadicSteps4<M, A, B, C, D> implements Steps<M> {
    private final Monad<M> monad;
    private final Kind<M, Tuple4<A, B, C, D>> computation;

    private MonadicSteps4(Monad<M> monad, Kind<M, Tuple4<A, B, C, D>> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    /**
     * Adds a new monadic generator to the comprehension.
     *
     * @param next A function taking the current 4-tuple of results and returning a new monadic
     *     computation.
     * @param <E> The value type of the new computation.
     * @return The next step in the builder, now tracking five results.
     */
    public <E> MonadicSteps5<M, A, B, C, D, E> from(Function<Tuple4<A, B, C, D>, Kind<M, E>> next) {
      Kind<M, Tuple5<A, B, C, D, E>> newComputation =
          monad.flatMap(
              abcd ->
                  monad.map(
                      e -> Tuple.of(abcd._1(), abcd._2(), abcd._3(), abcd._4(), e),
                      next.apply(abcd)),
              this.computation);
      return new MonadicSteps5<>(monad, newComputation);
    }

    /**
     * Adds a pure computation to the comprehension.
     *
     * @param f A function taking the current 4-tuple of results and returning a new pure value.
     * @param <E> The type of the new computed value.
     * @return The next step in the builder, now tracking five results.
     */
    public <E> MonadicSteps5<M, A, B, C, D, E> let(Function<Tuple4<A, B, C, D>, E> f) {
      Kind<M, Tuple5<A, B, C, D, E>> newComputation =
          monad.map(
              abcd -> Tuple.of(abcd._1(), abcd._2(), abcd._3(), abcd._4(), f.apply(abcd)),
              this.computation);
      return new MonadicSteps5<>(monad, newComputation);
    }

    /**
     * Completes the comprehension by yielding a final result from the four accumulated values.
     *
     * @param f A function that combines the four results into a final value.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function4<A, B, C, D, R> f) {
      return monad.map(t -> f.apply(t._1(), t._2(), t._3(), t._4()), computation);
    }

    /**
     * Completes the comprehension by yielding a final result from the tuple of accumulated values.
     *
     * @param f A function that transforms the tuple of results into a final value.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function<Tuple4<A, B, C, D>, R> f) {
      return monad.map(f, computation);
    }
  }

  /**
   * Represents the fifth (and final supported) step in a non-filterable for-comprehension, holding
   * a tuple of five results.
   */
  public static final class MonadicSteps5<M, A, B, C, D, E> implements Steps<M> {
    private final Monad<M> monad;
    private final Kind<M, Tuple5<A, B, C, D, E>> computation;

    private MonadicSteps5(Monad<M> monad, Kind<M, Tuple5<A, B, C, D, E>> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    /**
     * Completes the comprehension by yielding a final result from the five accumulated values.
     *
     * @param f A function that combines the five results into a final value.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function5<A, B, C, D, E, R> f) {
      return monad.map(t -> f.apply(t._1(), t._2(), t._3(), t._4(), t._5()), computation);
    }

    /**
     * Completes the comprehension by yielding a final result from the tuple of accumulated values.
     *
     * @param f A function that transforms the tuple of results into a final value.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function<Tuple5<A, B, C, D, E>, R> f) {
      return monad.map(f, computation);
    }
  }

  // --- Filterable Steps (for MonadZero) ---

  /**
   * Represents the first step in a filterable for-comprehension, holding a single monadic value.
   *
   * @param <M> The witness type of the Monad.
   * @param <A> The value type of the initial computation.
   */
  public static final class FilterableSteps1<M, A> implements Steps<M> {
    private final MonadZero<M> monad;
    private final Kind<M, A> computation;

    private FilterableSteps1(MonadZero<M> monad, Kind<M, A> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    /**
     * Adds a new monadic generator to the comprehension.
     *
     * @param next A function producing the next monadic computation.
     * @param <B> The value type of the new computation.
     * @return The next step in the builder, now tracking two results.
     */
    public <B> FilterableSteps2<M, A, B> from(Function<A, Kind<M, B>> next) {
      Kind<M, Tuple2<A, B>> newComputation =
          monad.flatMap(a -> monad.map(b -> Tuple.of(a, b), next.apply(a)), this.computation);
      return new FilterableSteps2<>(monad, newComputation);
    }

    /**
     * Adds a pure computation to the comprehension.
     *
     * @param f A function producing a new pure value.
     * @param <B> The type of the new computed value.
     * @return The next step in the builder, now tracking two results.
     */
    public <B> FilterableSteps2<M, A, B> let(Function<A, B> f) {
      Kind<M, Tuple2<A, B>> newComputation =
          monad.map(a -> Tuple.of(a, f.apply(a)), this.computation);
      return new FilterableSteps2<>(monad, newComputation);
    }

    /**
     * Filters the results of the comprehension based on a predicate. If the predicate returns
     * {@code false}, the comprehension short-circuits for that path by using the monad's {@link
     * MonadZero#zero()} element (e.g., an empty list).
     *
     * @param filter The predicate to apply to the current value {@code A}.
     * @return The current builder step, with the filter applied.
     */
    public FilterableSteps1<M, A> when(Predicate<A> filter) {
      Kind<M, A> newComputation =
          monad.flatMap(a -> filter.test(a) ? monad.of(a) : monad.zero(), this.computation);
      return new FilterableSteps1<>(monad, newComputation);
    }

    /**
     * Completes the for-comprehension by applying a function to the final result.
     *
     * @param f A function to transform the final value.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function<A, R> f) {
      return monad.map(f, computation);
    }
  }

  /**
   * Represents the second step in a filterable for-comprehension, holding a tuple of two results.
   *
   * @param <M> The witness type of the Monad.
   * @param <A> The value type of the first result.
   * @param <B> The value type of the second result.
   */
  public static final class FilterableSteps2<M, A, B> implements Steps<M> {
    private final MonadZero<M> monad;
    private final Kind<M, Tuple2<A, B>> computation;

    private FilterableSteps2(MonadZero<M> monad, Kind<M, Tuple2<A, B>> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    /**
     * Adds a new monadic generator to the comprehension.
     *
     * @param next A function producing the next monadic computation.
     * @param <C> The value type of the new computation.
     * @return The next step in the builder, now tracking three results.
     */
    public <C> FilterableSteps3<M, A, B, C> from(Function<Tuple2<A, B>, Kind<M, C>> next) {
      Kind<M, Tuple3<A, B, C>> newComputation =
          monad.flatMap(
              ab -> monad.map(c -> Tuple.of(ab._1(), ab._2(), c), next.apply(ab)),
              this.computation);
      return new FilterableSteps3<>(monad, newComputation);
    }

    /**
     * Adds a pure computation to the comprehension.
     *
     * @param f A function producing a new pure value.
     * @param <C> The type of the new computed value.
     * @return The next step in the builder, now tracking three results.
     */
    public <C> FilterableSteps3<M, A, B, C> let(Function<Tuple2<A, B>, C> f) {
      Kind<M, Tuple3<A, B, C>> newComputation =
          monad.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), this.computation);
      return new FilterableSteps3<>(monad, newComputation);
    }

    /**
     * Filters the results of the comprehension based on a predicate.
     *
     * @param filter The predicate to apply to the tuple of results.
     * @return The current builder step, with the filter applied.
     */
    public FilterableSteps2<M, A, B> when(Predicate<Tuple2<A, B>> filter) {
      Kind<M, Tuple2<A, B>> newComputation =
          monad.flatMap(ab -> filter.test(ab) ? monad.of(ab) : monad.zero(), this.computation);
      return new FilterableSteps2<>(monad, newComputation);
    }

    /**
     * Completes the comprehension by yielding a final result from the two accumulated values.
     *
     * @param f A function that combines the two results.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(BiFunction<A, B, R> f) {
      return monad.map(t -> f.apply(t._1(), t._2()), computation);
    }

    /**
     * Completes the comprehension by yielding a final result from the tuple of accumulated values.
     *
     * @param f A function that transforms the tuple of results.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function<Tuple2<A, B>, R> f) {
      return monad.map(f, computation);
    }
  }

  /**
   * Represents the third step in a filterable for-comprehension, holding a tuple of three results.
   */
  public static final class FilterableSteps3<M, A, B, C> implements Steps<M> {
    private final MonadZero<M> monad;
    private final Kind<M, Tuple3<A, B, C>> computation;

    private FilterableSteps3(MonadZero<M> monad, Kind<M, Tuple3<A, B, C>> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    /**
     * Adds a new monadic generator to the comprehension.
     *
     * @param next A function producing the next monadic computation.
     * @param <D> The value type of the new computation.
     * @return The next step in the builder, now tracking four results.
     */
    public <D> FilterableSteps4<M, A, B, C, D> from(Function<Tuple3<A, B, C>, Kind<M, D>> next) {
      Kind<M, Tuple4<A, B, C, D>> newComputation =
          monad.flatMap(
              abc -> monad.map(d -> Tuple.of(abc._1(), abc._2(), abc._3(), d), next.apply(abc)),
              this.computation);
      return new FilterableSteps4<>(monad, newComputation);
    }

    /**
     * Adds a pure computation to the comprehension.
     *
     * @param f A function producing a new pure value.
     * @param <D> The type of the new computed value.
     * @return The next step in the builder, now tracking four results.
     */
    public <D> FilterableSteps4<M, A, B, C, D> let(Function<Tuple3<A, B, C>, D> f) {
      Kind<M, Tuple4<A, B, C, D>> newComputation =
          monad.map(abc -> Tuple.of(abc._1(), abc._2(), abc._3(), f.apply(abc)), this.computation);
      return new FilterableSteps4<>(monad, newComputation);
    }

    /**
     * Filters the results of the comprehension based on a predicate.
     *
     * @param filter The predicate to apply to the tuple of results.
     * @return The current builder step, with the filter applied.
     */
    public FilterableSteps3<M, A, B, C> when(Predicate<Tuple3<A, B, C>> filter) {
      Kind<M, Tuple3<A, B, C>> newComputation =
          monad.flatMap(abc -> filter.test(abc) ? monad.of(abc) : monad.zero(), this.computation);
      return new FilterableSteps3<>(monad, newComputation);
    }

    /**
     * Completes the comprehension by yielding a final result from the three accumulated values.
     *
     * @param f A function that combines the three results.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function3<A, B, C, R> f) {
      return monad.map(t -> f.apply(t._1(), t._2(), t._3()), computation);
    }

    /**
     * Completes the comprehension by yielding a final result from the tuple of accumulated values.
     *
     * @param f A function that transforms the tuple of results.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function<Tuple3<A, B, C>, R> f) {
      return monad.map(f, computation);
    }
  }

  /**
   * Represents the fourth step in a filterable for-comprehension, holding a tuple of four results.
   */
  public static final class FilterableSteps4<M, A, B, C, D> implements Steps<M> {
    private final MonadZero<M> monad;
    private final Kind<M, Tuple4<A, B, C, D>> computation;

    private FilterableSteps4(MonadZero<M> monad, Kind<M, Tuple4<A, B, C, D>> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    /**
     * Adds a new monadic generator to the comprehension.
     *
     * @param next A function producing the next monadic computation.
     * @param <E> The value type of the new computation.
     * @return The next step in the builder, now tracking five results.
     */
    public <E> FilterableSteps5<M, A, B, C, D, E> from(
        Function<Tuple4<A, B, C, D>, Kind<M, E>> next) {
      Kind<M, Tuple5<A, B, C, D, E>> newComputation =
          monad.flatMap(
              abcd ->
                  monad.map(
                      e -> Tuple.of(abcd._1(), abcd._2(), abcd._3(), abcd._4(), e),
                      next.apply(abcd)),
              this.computation);
      return new FilterableSteps5<>(monad, newComputation);
    }

    /**
     * Adds a pure computation to the comprehension.
     *
     * @param f A function producing a new pure value.
     * @param <E> The type of the new computed value.
     * @return The next step in the builder, now tracking five results.
     */
    public <E> FilterableSteps5<M, A, B, C, D, E> let(Function<Tuple4<A, B, C, D>, E> f) {
      Kind<M, Tuple5<A, B, C, D, E>> newComputation =
          monad.map(
              abcd -> Tuple.of(abcd._1(), abcd._2(), abcd._3(), abcd._4(), f.apply(abcd)),
              this.computation);
      return new FilterableSteps5<>(monad, newComputation);
    }

    /**
     * Filters the results of the comprehension based on a predicate.
     *
     * @param filter The predicate to apply to the tuple of results.
     * @return The current builder step, with the filter applied.
     */
    public FilterableSteps4<M, A, B, C, D> when(Predicate<Tuple4<A, B, C, D>> filter) {
      Kind<M, Tuple4<A, B, C, D>> newComputation =
          monad.flatMap(
              abcd -> filter.test(abcd) ? monad.of(abcd) : monad.zero(), this.computation);
      return new FilterableSteps4<>(monad, newComputation);
    }

    /**
     * Completes the comprehension by yielding a final result from the four accumulated values.
     *
     * @param f A function that combines the four results.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function4<A, B, C, D, R> f) {
      return monad.map(t -> f.apply(t._1(), t._2(), t._3(), t._4()), computation);
    }

    /**
     * Completes the comprehension by yielding a final result from the tuple of accumulated values.
     *
     * @param f A function that transforms the tuple of results.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function<Tuple4<A, B, C, D>, R> f) {
      return monad.map(f, computation);
    }
  }

  /**
   * Represents the fifth step in a filterable for-comprehension, holding a tuple of five results.
   */
  public static final class FilterableSteps5<M, A, B, C, D, E> implements Steps<M> {
    private final MonadZero<M> monad;
    private final Kind<M, Tuple5<A, B, C, D, E>> computation;

    private FilterableSteps5(MonadZero<M> monad, Kind<M, Tuple5<A, B, C, D, E>> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    /**
     * Filters the results of the comprehension based on a predicate.
     *
     * @param filter The predicate to apply to the tuple of results.
     * @return The current builder step, with the filter applied.
     */
    public FilterableSteps5<M, A, B, C, D, E> when(Predicate<Tuple5<A, B, C, D, E>> filter) {
      Kind<M, Tuple5<A, B, C, D, E>> newComputation =
          monad.flatMap(
              abcde -> filter.test(abcde) ? monad.of(abcde) : monad.zero(), this.computation);
      return new FilterableSteps5<>(monad, newComputation);
    }

    /**
     * Completes the comprehension by yielding a final result from the five accumulated values.
     *
     * @param f A function that combines the five results.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function5<A, B, C, D, E, R> f) {
      return monad.map(t -> f.apply(t._1(), t._2(), t._3(), t._4(), t._5()), computation);
    }

    /**
     * Completes the comprehension by yielding a final result from the tuple of accumulated values.
     *
     * @param f A function that transforms the tuple of results.
     * @param <R> The final result type.
     * @return A monadic value of type {@code R}.
     */
    public <R> Kind<M, R> yield(Function<Tuple5<A, B, C, D, E>, R> f) {
      return monad.map(f, computation);
    }
  }
}
