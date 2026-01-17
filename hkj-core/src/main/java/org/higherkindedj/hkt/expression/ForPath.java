// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.either.EitherMonad;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.higherkindedj.hkt.function.Function5;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryKindHelper;
import org.higherkindedj.hkt.trymonad.TryMonad;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple3;
import org.higherkindedj.hkt.tuple.Tuple4;
import org.higherkindedj.hkt.tuple.Tuple5;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.hkt.vtask.VTaskKindHelper;
import org.higherkindedj.hkt.vtask.VTaskMonad;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * Path-native for-comprehension builder that works directly with Effect Path types.
 *
 * <p>This class bridges the gap between the {@link For} comprehension system and the Effect Path
 * API, allowing users to compose Path types using for-comprehension style while preserving Path
 * semantics and returning Path types directly.
 *
 * <h2>Motivation</h2>
 *
 * <p>The standard {@link For} class works with raw {@link Kind} values and {@link Monad} instances,
 * requiring manual extraction and rewrapping when working with Path types. {@code ForPath} provides
 * a seamless experience:
 *
 * <ul>
 *   <li>Entry points accept Path types directly
 *   <li>All operations preserve Path types
 *   <li>{@code yield} returns Path types
 *   <li>Full FocusPath/AffinePath integration
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>MaybePath Comprehension</h3>
 *
 * <pre>{@code
 * MaybePath<String> result = ForPath.from(Path.just(user))
 *     .from(u -> Path.maybe(u.getAddress()))       // flatMap
 *     .let(addr -> addr.getCity())                  // map
 *     .when(t -> !t._2().isEmpty())                 // filter
 *     .yield((user, addr, city) -> city + ", " + addr.getCountry());
 * }</pre>
 *
 * <h3>EitherPath Comprehension</h3>
 *
 * <pre>{@code
 * EitherPath<Error, Order> result = ForPath.from(Path.<Error, User>right(user))
 *     .from(u -> validateUser(u))                   // returns EitherPath
 *     .from((u, valid) -> createOrder(u))           // returns EitherPath
 *     .yield((user, validated, order) -> order);
 * }</pre>
 *
 * <h3>With FocusPath Integration</h3>
 *
 * <pre>{@code
 * FocusPath<User, Address> addressPath = UserFocus.address();
 *
 * MaybePath<String> city = ForPath.from(Path.just(user))
 *     .focus(addressPath)                           // extract address
 *     .yield((user, address) -> address.getCity());
 * }</pre>
 *
 * <h3>With AffinePath (Optional Focus)</h3>
 *
 * <pre>{@code
 * AffinePath<User, Email> emailPath = UserFocus.optionalEmail();
 *
 * MaybePath<String> email = ForPath.from(Path.just(user))
 *     .match(emailPath)                             // extract optional email
 *     .yield((user, email) -> email.toString());
 * // Returns Nothing if user has no email
 * }</pre>
 *
 * @see For
 * @see Path
 * @see Chainable
 * @see FocusPath
 * @see AffinePath
 */
public final class ForPath {

  private static final String YIELD_CANNOT_RETURN_NULL = "The yield function must not return null.";

  private ForPath() {} // Static access only

  // ===== MaybePath Entry Points =====

  /**
   * Initiates a for-comprehension with a MaybePath.
   *
   * <p>MaybePath supports filtering via {@code when()}, making it a filterable comprehension.
   *
   * @param source the initial MaybePath
   * @param <A> the value type
   * @return the first step of the builder
   */
  public static <A> MaybePathSteps1<A> from(MaybePath<A> source) {
    Objects.requireNonNull(source, "source must not be null");
    return new MaybePathSteps1<>(source);
  }

  // ===== OptionalPath Entry Points =====

  /**
   * Initiates a for-comprehension with an OptionalPath.
   *
   * <p>OptionalPath supports filtering via {@code when()}, making it a filterable comprehension.
   *
   * @param source the initial OptionalPath
   * @param <A> the value type
   * @return the first step of the builder
   */
  public static <A> OptionalPathSteps1<A> from(OptionalPath<A> source) {
    Objects.requireNonNull(source, "source must not be null");
    return new OptionalPathSteps1<>(source);
  }

  // ===== EitherPath Entry Points =====

  /**
   * Initiates a for-comprehension with an EitherPath.
   *
   * @param source the initial EitherPath
   * @param <E> the error type
   * @param <A> the value type
   * @return the first step of the builder
   */
  public static <E, A> EitherPathSteps1<E, A> from(EitherPath<E, A> source) {
    Objects.requireNonNull(source, "source must not be null");
    return new EitherPathSteps1<>(source);
  }

  // ===== TryPath Entry Points =====

  /**
   * Initiates a for-comprehension with a TryPath.
   *
   * @param source the initial TryPath
   * @param <A> the value type
   * @return the first step of the builder
   */
  public static <A> TryPathSteps1<A> from(TryPath<A> source) {
    Objects.requireNonNull(source, "source must not be null");
    return new TryPathSteps1<>(source);
  }

  // ===== IOPath Entry Points =====

  /**
   * Initiates a for-comprehension with an IOPath.
   *
   * @param source the initial IOPath
   * @param <A> the value type
   * @return the first step of the builder
   */
  public static <A> IOPathSteps1<A> from(IOPath<A> source) {
    Objects.requireNonNull(source, "source must not be null");
    return new IOPathSteps1<>(source);
  }

  // ===== VTaskPath Entry Points =====

  /**
   * Initiates a for-comprehension with a VTaskPath.
   *
   * <p>VTaskPath computations execute on virtual threads, providing lightweight concurrency.
   *
   * @param source the initial VTaskPath
   * @param <A> the value type
   * @return the first step of the builder
   */
  public static <A> VTaskPathSteps1<A> from(VTaskPath<A> source) {
    Objects.requireNonNull(source, "source must not be null");
    return new VTaskPathSteps1<>(source);
  }

  // ===== IdPath Entry Points =====

  /**
   * Initiates a for-comprehension with an IdPath.
   *
   * @param source the initial IdPath
   * @param <A> the value type
   * @return the first step of the builder
   */
  public static <A> IdPathSteps1<A> from(IdPath<A> source) {
    Objects.requireNonNull(source, "source must not be null");
    return new IdPathSteps1<>(source);
  }

  // ===== NonDetPath (List) Entry Points =====

  /**
   * Initiates a for-comprehension with a NonDetPath (list with Cartesian product semantics).
   *
   * <p>NonDetPath supports filtering via {@code when()}, making it a filterable comprehension.
   *
   * @param source the initial NonDetPath
   * @param <A> the element type
   * @return the first step of the builder
   */
  public static <A> NonDetPathSteps1<A> from(NonDetPath<A> source) {
    Objects.requireNonNull(source, "source must not be null");
    return new NonDetPathSteps1<>(source);
  }

  // ===== Generic Entry Point =====

  /**
   * Initiates a for-comprehension with a GenericPath.
   *
   * <p>This is the escape hatch for custom monad types not covered by specific entry points.
   *
   * @param source the initial GenericPath
   * @param <F> the witness type
   * @param <A> the value type
   * @return the first step of the builder
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A> GenericPathSteps1<F, A> from(
      GenericPath<F, A> source) {
    Objects.requireNonNull(source, "source must not be null");
    return new GenericPathSteps1<>(source);
  }

  // ========================================================================
  // MaybePath Steps (Filterable)
  // ========================================================================

  /** First step in a MaybePath comprehension. */
  public static final class MaybePathSteps1<A> {
    private static final MaybeMonad MONAD = MaybeMonad.INSTANCE;
    private final Kind<MaybeKind.Witness, A> computation;

    private MaybePathSteps1(MaybePath<A> source) {
      this.computation = MaybeKindHelper.MAYBE.widen(source.run());
    }

    private MaybePathSteps1(Kind<MaybeKind.Witness, A> computation) {
      this.computation = computation;
    }

    /**
     * Adds a generator that produces another MaybePath.
     *
     * @param next function producing the next MaybePath
     * @param <B> the new value type
     * @return the next step
     */
    public <B> MaybePathSteps2<A, B> from(Function<A, MaybePath<B>> next) {
      Kind<MaybeKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(
              a -> MONAD.map(b -> Tuple.of(a, b), MaybeKindHelper.MAYBE.widen(next.apply(a).run())),
              computation);
      return new MaybePathSteps2<>(newComp);
    }

    /**
     * Binds the result of a pure computation.
     *
     * @param f the pure computation
     * @param <B> the result type
     * @return the next step
     */
    public <B> MaybePathSteps2<A, B> let(Function<A, B> f) {
      Kind<MaybeKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, f.apply(a)), computation);
      return new MaybePathSteps2<>(newComp);
    }

    /**
     * Extracts a value using a FocusPath.
     *
     * @param focusPath the lens to apply
     * @param <B> the focused type
     * @return the next step
     */
    public <B> MaybePathSteps2<A, B> focus(FocusPath<A, B> focusPath) {
      Objects.requireNonNull(focusPath, "focusPath must not be null");
      Kind<MaybeKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, focusPath.get(a)), computation);
      return new MaybePathSteps2<>(newComp);
    }

    /**
     * Pattern matches using an AffinePath, short-circuiting if the match fails.
     *
     * @param affinePath the optional focus to apply
     * @param <B> the focused type
     * @return the next step
     */
    public <B> MaybePathSteps2<A, B> match(AffinePath<A, B> affinePath) {
      Objects.requireNonNull(affinePath, "affinePath must not be null");
      Kind<MaybeKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(
              a ->
                  affinePath
                      .getOptional(a)
                      .map(b -> MONAD.of(Tuple.of(a, b)))
                      .orElseGet(MONAD::zero),
              computation);
      return new MaybePathSteps2<>(newComp);
    }

    /**
     * Filters the value based on a predicate.
     *
     * @param predicate the filter condition
     * @return this step with the filter applied
     */
    public MaybePathSteps1<A> when(Predicate<A> predicate) {
      Kind<MaybeKind.Witness, A> newComp =
          MONAD.flatMap(a -> predicate.test(a) ? MONAD.of(a) : MONAD.zero(), computation);
      return new MaybePathSteps1<>(newComp);
    }

    /**
     * Completes the comprehension by yielding a final result.
     *
     * @param f the yield function
     * @param <R> the result type
     * @return the resulting MaybePath
     */
    public <R> MaybePath<R> yield(Function<A, R> f) {
      Kind<MaybeKind.Witness, R> result = MONAD.map(f, computation);
      return Path.maybe(MaybeKindHelper.MAYBE.narrow(result));
    }
  }

  /** Second step in a MaybePath comprehension. */
  public static final class MaybePathSteps2<A, B> {
    private static final MaybeMonad MONAD = MaybeMonad.INSTANCE;
    private final Kind<MaybeKind.Witness, Tuple2<A, B>> computation;

    private MaybePathSteps2(Kind<MaybeKind.Witness, Tuple2<A, B>> computation) {
      this.computation = computation;
    }

    public <C> MaybePathSteps3<A, B, C> from(Function<Tuple2<A, B>, MaybePath<C>> next) {
      Kind<MaybeKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(
              ab ->
                  MONAD.map(
                      c -> Tuple.of(ab._1(), ab._2(), c),
                      MaybeKindHelper.MAYBE.widen(next.apply(ab).run())),
              computation);
      return new MaybePathSteps3<>(newComp);
    }

    public <C> MaybePathSteps3<A, B, C> let(Function<Tuple2<A, B>, C> f) {
      Kind<MaybeKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), computation);
      return new MaybePathSteps3<>(newComp);
    }

    public <C> MaybePathSteps3<A, B, C> focus(Function<Tuple2<A, B>, C> extractor) {
      Objects.requireNonNull(extractor, "extractor must not be null");
      return let(extractor);
    }

    public <C> MaybePathSteps3<A, B, C> match(Function<Tuple2<A, B>, Optional<C>> matcher) {
      Objects.requireNonNull(matcher, "matcher must not be null");
      Kind<MaybeKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(
              ab ->
                  matcher
                      .apply(ab)
                      .map(c -> MONAD.of(Tuple.of(ab._1(), ab._2(), c)))
                      .orElseGet(MONAD::zero),
              computation);
      return new MaybePathSteps3<>(newComp);
    }

    public MaybePathSteps2<A, B> when(Predicate<Tuple2<A, B>> predicate) {
      Kind<MaybeKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(ab -> predicate.test(ab) ? MONAD.of(ab) : MONAD.zero(), computation);
      return new MaybePathSteps2<>(newComp);
    }

    public <R> MaybePath<R> yield(BiFunction<A, B, R> f) {
      Kind<MaybeKind.Witness, R> result =
          MONAD.map(
              t -> Objects.requireNonNull(f.apply(t._1(), t._2()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.maybe(MaybeKindHelper.MAYBE.narrow(result));
    }

    public <R> MaybePath<R> yield(Function<Tuple2<A, B>, R> f) {
      Kind<MaybeKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.maybe(MaybeKindHelper.MAYBE.narrow(result));
    }
  }

  /** Third step in a MaybePath comprehension. */
  public static final class MaybePathSteps3<A, B, C> {
    private static final MaybeMonad MONAD = MaybeMonad.INSTANCE;
    private final Kind<MaybeKind.Witness, Tuple3<A, B, C>> computation;

    private MaybePathSteps3(Kind<MaybeKind.Witness, Tuple3<A, B, C>> computation) {
      this.computation = computation;
    }

    public <D> MaybePathSteps4<A, B, C, D> from(Function<Tuple3<A, B, C>, MaybePath<D>> next) {
      Kind<MaybeKind.Witness, Tuple4<A, B, C, D>> newComp =
          MONAD.flatMap(
              abc ->
                  MONAD.map(
                      d -> Tuple.of(abc._1(), abc._2(), abc._3(), d),
                      MaybeKindHelper.MAYBE.widen(next.apply(abc).run())),
              computation);
      return new MaybePathSteps4<>(newComp);
    }

    public <D> MaybePathSteps4<A, B, C, D> let(Function<Tuple3<A, B, C>, D> f) {
      Kind<MaybeKind.Witness, Tuple4<A, B, C, D>> newComp =
          MONAD.map(abc -> Tuple.of(abc._1(), abc._2(), abc._3(), f.apply(abc)), computation);
      return new MaybePathSteps4<>(newComp);
    }

    public MaybePathSteps3<A, B, C> when(Predicate<Tuple3<A, B, C>> predicate) {
      Kind<MaybeKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(abc -> predicate.test(abc) ? MONAD.of(abc) : MONAD.zero(), computation);
      return new MaybePathSteps3<>(newComp);
    }

    public <R> MaybePath<R> yield(Function3<A, B, C, R> f) {
      Kind<MaybeKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(f.apply(t._1(), t._2(), t._3()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.maybe(MaybeKindHelper.MAYBE.narrow(result));
    }

    public <R> MaybePath<R> yield(Function<Tuple3<A, B, C>, R> f) {
      Kind<MaybeKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.maybe(MaybeKindHelper.MAYBE.narrow(result));
    }
  }

  /** Fourth step in a MaybePath comprehension. */
  public static final class MaybePathSteps4<A, B, C, D> {
    private static final MaybeMonad MONAD = MaybeMonad.INSTANCE;
    private final Kind<MaybeKind.Witness, Tuple4<A, B, C, D>> computation;

    private MaybePathSteps4(Kind<MaybeKind.Witness, Tuple4<A, B, C, D>> computation) {
      this.computation = computation;
    }

    public <E> MaybePathSteps5<A, B, C, D, E> from(
        Function<Tuple4<A, B, C, D>, MaybePath<E>> next) {
      Kind<MaybeKind.Witness, Tuple5<A, B, C, D, E>> newComp =
          MONAD.flatMap(
              abcd ->
                  MONAD.map(
                      e -> Tuple.of(abcd._1(), abcd._2(), abcd._3(), abcd._4(), e),
                      MaybeKindHelper.MAYBE.widen(next.apply(abcd).run())),
              computation);
      return new MaybePathSteps5<>(newComp);
    }

    public <E> MaybePathSteps5<A, B, C, D, E> let(Function<Tuple4<A, B, C, D>, E> f) {
      Kind<MaybeKind.Witness, Tuple5<A, B, C, D, E>> newComp =
          MONAD.map(
              abcd -> Tuple.of(abcd._1(), abcd._2(), abcd._3(), abcd._4(), f.apply(abcd)),
              computation);
      return new MaybePathSteps5<>(newComp);
    }

    public MaybePathSteps4<A, B, C, D> when(Predicate<Tuple4<A, B, C, D>> predicate) {
      Kind<MaybeKind.Witness, Tuple4<A, B, C, D>> newComp =
          MONAD.flatMap(abcd -> predicate.test(abcd) ? MONAD.of(abcd) : MONAD.zero(), computation);
      return new MaybePathSteps4<>(newComp);
    }

    public <R> MaybePath<R> yield(Function4<A, B, C, D, R> f) {
      Kind<MaybeKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(
                      f.apply(t._1(), t._2(), t._3(), t._4()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.maybe(MaybeKindHelper.MAYBE.narrow(result));
    }

    public <R> MaybePath<R> yield(Function<Tuple4<A, B, C, D>, R> f) {
      Kind<MaybeKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.maybe(MaybeKindHelper.MAYBE.narrow(result));
    }
  }

  /** Fifth step in a MaybePath comprehension. */
  public static final class MaybePathSteps5<A, B, C, D, E> {
    private static final MaybeMonad MONAD = MaybeMonad.INSTANCE;
    private final Kind<MaybeKind.Witness, Tuple5<A, B, C, D, E>> computation;

    private MaybePathSteps5(Kind<MaybeKind.Witness, Tuple5<A, B, C, D, E>> computation) {
      this.computation = computation;
    }

    public MaybePathSteps5<A, B, C, D, E> when(Predicate<Tuple5<A, B, C, D, E>> predicate) {
      Kind<MaybeKind.Witness, Tuple5<A, B, C, D, E>> newComp =
          MONAD.flatMap(
              abcde -> predicate.test(abcde) ? MONAD.of(abcde) : MONAD.zero(), computation);
      return new MaybePathSteps5<>(newComp);
    }

    public <R> MaybePath<R> yield(Function5<A, B, C, D, E, R> f) {
      Kind<MaybeKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(
                      f.apply(t._1(), t._2(), t._3(), t._4(), t._5()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.maybe(MaybeKindHelper.MAYBE.narrow(result));
    }

    public <R> MaybePath<R> yield(Function<Tuple5<A, B, C, D, E>, R> f) {
      Kind<MaybeKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.maybe(MaybeKindHelper.MAYBE.narrow(result));
    }
  }

  // ========================================================================
  // OptionalPath Steps (Filterable)
  // ========================================================================

  /** First step in an OptionalPath comprehension. */
  public static final class OptionalPathSteps1<A> {
    private static final OptionalMonad MONAD = OptionalMonad.INSTANCE;
    private final Kind<OptionalKind.Witness, A> computation;

    private OptionalPathSteps1(OptionalPath<A> source) {
      this.computation = OptionalKindHelper.OPTIONAL.widen(source.run());
    }

    private OptionalPathSteps1(Kind<OptionalKind.Witness, A> computation) {
      this.computation = computation;
    }

    public <B> OptionalPathSteps2<A, B> from(Function<A, OptionalPath<B>> next) {
      Kind<OptionalKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(
              a ->
                  MONAD.map(
                      b -> Tuple.of(a, b), OptionalKindHelper.OPTIONAL.widen(next.apply(a).run())),
              computation);
      return new OptionalPathSteps2<>(newComp);
    }

    public <B> OptionalPathSteps2<A, B> let(Function<A, B> f) {
      Kind<OptionalKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, f.apply(a)), computation);
      return new OptionalPathSteps2<>(newComp);
    }

    public <B> OptionalPathSteps2<A, B> focus(FocusPath<A, B> focusPath) {
      Objects.requireNonNull(focusPath, "focusPath must not be null");
      Kind<OptionalKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, focusPath.get(a)), computation);
      return new OptionalPathSteps2<>(newComp);
    }

    public <B> OptionalPathSteps2<A, B> match(AffinePath<A, B> affinePath) {
      Objects.requireNonNull(affinePath, "affinePath must not be null");
      Kind<OptionalKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(
              a ->
                  affinePath
                      .getOptional(a)
                      .map(b -> MONAD.of(Tuple.of(a, b)))
                      .orElseGet(MONAD::zero),
              computation);
      return new OptionalPathSteps2<>(newComp);
    }

    public OptionalPathSteps1<A> when(Predicate<A> predicate) {
      Kind<OptionalKind.Witness, A> newComp =
          MONAD.flatMap(a -> predicate.test(a) ? MONAD.of(a) : MONAD.zero(), computation);
      return new OptionalPathSteps1<>(newComp);
    }

    public <R> OptionalPath<R> yield(Function<A, R> f) {
      Kind<OptionalKind.Witness, R> result = MONAD.map(f, computation);
      return Path.optional(OptionalKindHelper.OPTIONAL.narrow(result));
    }
  }

  /** Second step in an OptionalPath comprehension. */
  public static final class OptionalPathSteps2<A, B> {
    private static final OptionalMonad MONAD = OptionalMonad.INSTANCE;
    private final Kind<OptionalKind.Witness, Tuple2<A, B>> computation;

    private OptionalPathSteps2(Kind<OptionalKind.Witness, Tuple2<A, B>> computation) {
      this.computation = computation;
    }

    public <C> OptionalPathSteps3<A, B, C> from(Function<Tuple2<A, B>, OptionalPath<C>> next) {
      Kind<OptionalKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(
              ab ->
                  MONAD.map(
                      c -> Tuple.of(ab._1(), ab._2(), c),
                      OptionalKindHelper.OPTIONAL.widen(next.apply(ab).run())),
              computation);
      return new OptionalPathSteps3<>(newComp);
    }

    public <C> OptionalPathSteps3<A, B, C> let(Function<Tuple2<A, B>, C> f) {
      Kind<OptionalKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), computation);
      return new OptionalPathSteps3<>(newComp);
    }

    public OptionalPathSteps2<A, B> when(Predicate<Tuple2<A, B>> predicate) {
      Kind<OptionalKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(ab -> predicate.test(ab) ? MONAD.of(ab) : MONAD.zero(), computation);
      return new OptionalPathSteps2<>(newComp);
    }

    public <R> OptionalPath<R> yield(BiFunction<A, B, R> f) {
      Kind<OptionalKind.Witness, R> result =
          MONAD.map(
              t -> Objects.requireNonNull(f.apply(t._1(), t._2()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.optional(OptionalKindHelper.OPTIONAL.narrow(result));
    }

    public <R> OptionalPath<R> yield(Function<Tuple2<A, B>, R> f) {
      Kind<OptionalKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.optional(OptionalKindHelper.OPTIONAL.narrow(result));
    }
  }

  /** Third step in an OptionalPath comprehension. */
  public static final class OptionalPathSteps3<A, B, C> {
    private static final OptionalMonad MONAD = OptionalMonad.INSTANCE;
    private final Kind<OptionalKind.Witness, Tuple3<A, B, C>> computation;

    private OptionalPathSteps3(Kind<OptionalKind.Witness, Tuple3<A, B, C>> computation) {
      this.computation = computation;
    }

    public OptionalPathSteps3<A, B, C> when(Predicate<Tuple3<A, B, C>> predicate) {
      Kind<OptionalKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(abc -> predicate.test(abc) ? MONAD.of(abc) : MONAD.zero(), computation);
      return new OptionalPathSteps3<>(newComp);
    }

    public <R> OptionalPath<R> yield(Function3<A, B, C, R> f) {
      Kind<OptionalKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(f.apply(t._1(), t._2(), t._3()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.optional(OptionalKindHelper.OPTIONAL.narrow(result));
    }

    public <R> OptionalPath<R> yield(Function<Tuple3<A, B, C>, R> f) {
      Kind<OptionalKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.optional(OptionalKindHelper.OPTIONAL.narrow(result));
    }
  }

  // ========================================================================
  // EitherPath Steps (Non-Filterable)
  // ========================================================================

  /** First step in an EitherPath comprehension. */
  public static final class EitherPathSteps1<E, A> {
    private final Kind<EitherKind.Witness<E>, A> computation;

    private static <E> EitherMonad<E> monad() {
      return EitherMonad.instance();
    }

    private EitherPathSteps1(EitherPath<E, A> source) {
      this.computation = EitherKindHelper.EITHER.widen(source.run());
    }

    public <B> EitherPathSteps2<E, A, B> from(Function<A, EitherPath<E, B>> next) {
      EitherMonad<E> m = monad();
      Kind<EitherKind.Witness<E>, Tuple2<A, B>> newComp =
          m.flatMap(
              a -> m.map(b -> Tuple.of(a, b), EitherKindHelper.EITHER.widen(next.apply(a).run())),
              computation);
      return new EitherPathSteps2<>(newComp);
    }

    public <B> EitherPathSteps2<E, A, B> let(Function<A, B> f) {
      EitherMonad<E> m = monad();
      Kind<EitherKind.Witness<E>, Tuple2<A, B>> newComp =
          m.map(a -> Tuple.of(a, f.apply(a)), computation);
      return new EitherPathSteps2<>(newComp);
    }

    public <B> EitherPathSteps2<E, A, B> focus(FocusPath<A, B> focusPath) {
      Objects.requireNonNull(focusPath, "focusPath must not be null");
      EitherMonad<E> m = monad();
      Kind<EitherKind.Witness<E>, Tuple2<A, B>> newComp =
          m.map(a -> Tuple.of(a, focusPath.get(a)), computation);
      return new EitherPathSteps2<>(newComp);
    }

    public <R> EitherPath<E, R> yield(Function<A, R> f) {
      EitherMonad<E> m = monad();
      Kind<EitherKind.Witness<E>, R> result = m.map(f, computation);
      return Path.either(EitherKindHelper.EITHER.narrow(result));
    }
  }

  /** Second step in an EitherPath comprehension. */
  public static final class EitherPathSteps2<E, A, B> {
    private final Kind<EitherKind.Witness<E>, Tuple2<A, B>> computation;

    private static <E> EitherMonad<E> monad() {
      return EitherMonad.instance();
    }

    private EitherPathSteps2(Kind<EitherKind.Witness<E>, Tuple2<A, B>> computation) {
      this.computation = computation;
    }

    public <C> EitherPathSteps3<E, A, B, C> from(Function<Tuple2<A, B>, EitherPath<E, C>> next) {
      EitherMonad<E> m = monad();
      Kind<EitherKind.Witness<E>, Tuple3<A, B, C>> newComp =
          m.flatMap(
              ab ->
                  m.map(
                      c -> Tuple.of(ab._1(), ab._2(), c),
                      EitherKindHelper.EITHER.widen(next.apply(ab).run())),
              computation);
      return new EitherPathSteps3<>(newComp);
    }

    public <C> EitherPathSteps3<E, A, B, C> let(Function<Tuple2<A, B>, C> f) {
      EitherMonad<E> m = monad();
      Kind<EitherKind.Witness<E>, Tuple3<A, B, C>> newComp =
          m.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), computation);
      return new EitherPathSteps3<>(newComp);
    }

    public <R> EitherPath<E, R> yield(BiFunction<A, B, R> f) {
      EitherMonad<E> m = monad();
      Kind<EitherKind.Witness<E>, R> result =
          m.map(
              t -> Objects.requireNonNull(f.apply(t._1(), t._2()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.either(EitherKindHelper.EITHER.narrow(result));
    }

    public <R> EitherPath<E, R> yield(Function<Tuple2<A, B>, R> f) {
      EitherMonad<E> m = monad();
      Kind<EitherKind.Witness<E>, R> result =
          m.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.either(EitherKindHelper.EITHER.narrow(result));
    }
  }

  /** Third step in an EitherPath comprehension. */
  public static final class EitherPathSteps3<E, A, B, C> {
    private final Kind<EitherKind.Witness<E>, Tuple3<A, B, C>> computation;

    private static <E> EitherMonad<E> monad() {
      return EitherMonad.instance();
    }

    private EitherPathSteps3(Kind<EitherKind.Witness<E>, Tuple3<A, B, C>> computation) {
      this.computation = computation;
    }

    public <R> EitherPath<E, R> yield(Function3<A, B, C, R> f) {
      EitherMonad<E> m = monad();
      Kind<EitherKind.Witness<E>, R> result =
          m.map(
              t ->
                  Objects.requireNonNull(f.apply(t._1(), t._2(), t._3()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.either(EitherKindHelper.EITHER.narrow(result));
    }

    public <R> EitherPath<E, R> yield(Function<Tuple3<A, B, C>, R> f) {
      EitherMonad<E> m = monad();
      Kind<EitherKind.Witness<E>, R> result =
          m.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.either(EitherKindHelper.EITHER.narrow(result));
    }
  }

  // ========================================================================
  // TryPath Steps
  // ========================================================================

  /** First step in a TryPath comprehension. */
  public static final class TryPathSteps1<A> {
    private static final TryMonad MONAD = TryMonad.INSTANCE;
    private final Kind<TryKind.Witness, A> computation;

    private TryPathSteps1(TryPath<A> source) {
      this.computation = TryKindHelper.TRY.widen(source.run());
    }

    public <B> TryPathSteps2<A, B> from(Function<A, TryPath<B>> next) {
      Kind<TryKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(
              a -> MONAD.map(b -> Tuple.of(a, b), TryKindHelper.TRY.widen(next.apply(a).run())),
              computation);
      return new TryPathSteps2<>(newComp);
    }

    public <B> TryPathSteps2<A, B> let(Function<A, B> f) {
      Kind<TryKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, f.apply(a)), computation);
      return new TryPathSteps2<>(newComp);
    }

    public <B> TryPathSteps2<A, B> focus(FocusPath<A, B> focusPath) {
      Objects.requireNonNull(focusPath, "focusPath must not be null");
      Kind<TryKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, focusPath.get(a)), computation);
      return new TryPathSteps2<>(newComp);
    }

    public <R> TryPath<R> yield(Function<A, R> f) {
      Kind<TryKind.Witness, R> result = MONAD.map(f, computation);
      return Path.tryPath(TryKindHelper.TRY.narrow(result));
    }
  }

  /** Second step in a TryPath comprehension. */
  public static final class TryPathSteps2<A, B> {
    private static final TryMonad MONAD = TryMonad.INSTANCE;
    private final Kind<TryKind.Witness, Tuple2<A, B>> computation;

    private TryPathSteps2(Kind<TryKind.Witness, Tuple2<A, B>> computation) {
      this.computation = computation;
    }

    public <C> TryPathSteps3<A, B, C> from(Function<Tuple2<A, B>, TryPath<C>> next) {
      Kind<TryKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(
              ab ->
                  MONAD.map(
                      c -> Tuple.of(ab._1(), ab._2(), c),
                      TryKindHelper.TRY.widen(next.apply(ab).run())),
              computation);
      return new TryPathSteps3<>(newComp);
    }

    public <C> TryPathSteps3<A, B, C> let(Function<Tuple2<A, B>, C> f) {
      Kind<TryKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), computation);
      return new TryPathSteps3<>(newComp);
    }

    public <R> TryPath<R> yield(BiFunction<A, B, R> f) {
      Kind<TryKind.Witness, R> result =
          MONAD.map(
              t -> Objects.requireNonNull(f.apply(t._1(), t._2()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.tryPath(TryKindHelper.TRY.narrow(result));
    }

    public <R> TryPath<R> yield(Function<Tuple2<A, B>, R> f) {
      Kind<TryKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.tryPath(TryKindHelper.TRY.narrow(result));
    }
  }

  /** Third step in a TryPath comprehension. */
  public static final class TryPathSteps3<A, B, C> {
    private static final TryMonad MONAD = TryMonad.INSTANCE;
    private final Kind<TryKind.Witness, Tuple3<A, B, C>> computation;

    private TryPathSteps3(Kind<TryKind.Witness, Tuple3<A, B, C>> computation) {
      this.computation = computation;
    }

    public <R> TryPath<R> yield(Function3<A, B, C, R> f) {
      Kind<TryKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(f.apply(t._1(), t._2(), t._3()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.tryPath(TryKindHelper.TRY.narrow(result));
    }

    public <R> TryPath<R> yield(Function<Tuple3<A, B, C>, R> f) {
      Kind<TryKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.tryPath(TryKindHelper.TRY.narrow(result));
    }
  }

  // ========================================================================
  // IOPath Steps
  // ========================================================================

  /** First step in an IOPath comprehension. */
  public static final class IOPathSteps1<A> {
    private static final IOMonad MONAD = IOMonad.INSTANCE;
    private final Kind<IOKind.Witness, A> computation;

    private IOPathSteps1(IOPath<A> source) {
      this.computation = IOKindHelper.IO_OP.widen(source.run());
    }

    public <B> IOPathSteps2<A, B> from(Function<A, IOPath<B>> next) {
      Kind<IOKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(
              a -> MONAD.map(b -> Tuple.of(a, b), IOKindHelper.IO_OP.widen(next.apply(a).run())),
              computation);
      return new IOPathSteps2<>(newComp);
    }

    public <B> IOPathSteps2<A, B> let(Function<A, B> f) {
      Kind<IOKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, f.apply(a)), computation);
      return new IOPathSteps2<>(newComp);
    }

    public <B> IOPathSteps2<A, B> focus(FocusPath<A, B> focusPath) {
      Objects.requireNonNull(focusPath, "focusPath must not be null");
      Kind<IOKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, focusPath.get(a)), computation);
      return new IOPathSteps2<>(newComp);
    }

    public <R> IOPath<R> yield(Function<A, R> f) {
      Kind<IOKind.Witness, R> result = MONAD.map(f, computation);
      return Path.ioPath(IOKindHelper.IO_OP.narrow(result));
    }
  }

  /** Second step in an IOPath comprehension. */
  public static final class IOPathSteps2<A, B> {
    private static final IOMonad MONAD = IOMonad.INSTANCE;
    private final Kind<IOKind.Witness, Tuple2<A, B>> computation;

    private IOPathSteps2(Kind<IOKind.Witness, Tuple2<A, B>> computation) {
      this.computation = computation;
    }

    public <C> IOPathSteps3<A, B, C> from(Function<Tuple2<A, B>, IOPath<C>> next) {
      Kind<IOKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(
              ab ->
                  MONAD.map(
                      c -> Tuple.of(ab._1(), ab._2(), c),
                      IOKindHelper.IO_OP.widen(next.apply(ab).run())),
              computation);
      return new IOPathSteps3<>(newComp);
    }

    public <C> IOPathSteps3<A, B, C> let(Function<Tuple2<A, B>, C> f) {
      Kind<IOKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), computation);
      return new IOPathSteps3<>(newComp);
    }

    public <R> IOPath<R> yield(BiFunction<A, B, R> f) {
      Kind<IOKind.Witness, R> result =
          MONAD.map(
              t -> Objects.requireNonNull(f.apply(t._1(), t._2()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.ioPath(IOKindHelper.IO_OP.narrow(result));
    }

    public <R> IOPath<R> yield(Function<Tuple2<A, B>, R> f) {
      Kind<IOKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.ioPath(IOKindHelper.IO_OP.narrow(result));
    }
  }

  /** Third step in an IOPath comprehension. */
  public static final class IOPathSteps3<A, B, C> {
    private static final IOMonad MONAD = IOMonad.INSTANCE;
    private final Kind<IOKind.Witness, Tuple3<A, B, C>> computation;

    private IOPathSteps3(Kind<IOKind.Witness, Tuple3<A, B, C>> computation) {
      this.computation = computation;
    }

    public <R> IOPath<R> yield(Function3<A, B, C, R> f) {
      Kind<IOKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(f.apply(t._1(), t._2(), t._3()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.ioPath(IOKindHelper.IO_OP.narrow(result));
    }

    public <R> IOPath<R> yield(Function<Tuple3<A, B, C>, R> f) {
      Kind<IOKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.ioPath(IOKindHelper.IO_OP.narrow(result));
    }
  }

  // ========================================================================
  // VTaskPath Steps
  // ========================================================================

  /** First step in a VTaskPath comprehension. */
  public static final class VTaskPathSteps1<A> {
    private static final VTaskMonad MONAD = VTaskMonad.INSTANCE;
    private final Kind<VTaskKind.Witness, A> computation;

    private VTaskPathSteps1(VTaskPath<A> source) {
      this.computation = VTaskKindHelper.VTASK.widen(source.run());
    }

    public <B> VTaskPathSteps2<A, B> from(Function<A, VTaskPath<B>> next) {
      Kind<VTaskKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(
              a -> MONAD.map(b -> Tuple.of(a, b), VTaskKindHelper.VTASK.widen(next.apply(a).run())),
              computation);
      return new VTaskPathSteps2<>(newComp);
    }

    public <B> VTaskPathSteps2<A, B> let(Function<A, B> f) {
      Kind<VTaskKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, f.apply(a)), computation);
      return new VTaskPathSteps2<>(newComp);
    }

    public <B> VTaskPathSteps2<A, B> focus(FocusPath<A, B> focusPath) {
      Objects.requireNonNull(focusPath, "focusPath must not be null");
      Kind<VTaskKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, focusPath.get(a)), computation);
      return new VTaskPathSteps2<>(newComp);
    }

    public <R> VTaskPath<R> yield(Function<A, R> f) {
      Kind<VTaskKind.Witness, R> result = MONAD.map(f, computation);
      return Path.vtaskPath(VTaskKindHelper.VTASK.narrow(result));
    }
  }

  /** Second step in a VTaskPath comprehension. */
  public static final class VTaskPathSteps2<A, B> {
    private static final VTaskMonad MONAD = VTaskMonad.INSTANCE;
    private final Kind<VTaskKind.Witness, Tuple2<A, B>> computation;

    private VTaskPathSteps2(Kind<VTaskKind.Witness, Tuple2<A, B>> computation) {
      this.computation = computation;
    }

    public <C> VTaskPathSteps3<A, B, C> from(Function<Tuple2<A, B>, VTaskPath<C>> next) {
      Kind<VTaskKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(
              ab ->
                  MONAD.map(
                      c -> Tuple.of(ab._1(), ab._2(), c),
                      VTaskKindHelper.VTASK.widen(next.apply(ab).run())),
              computation);
      return new VTaskPathSteps3<>(newComp);
    }

    public <C> VTaskPathSteps3<A, B, C> let(Function<Tuple2<A, B>, C> f) {
      Kind<VTaskKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), computation);
      return new VTaskPathSteps3<>(newComp);
    }

    public <R> VTaskPath<R> yield(BiFunction<A, B, R> f) {
      Kind<VTaskKind.Witness, R> result =
          MONAD.map(
              t -> Objects.requireNonNull(f.apply(t._1(), t._2()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.vtaskPath(VTaskKindHelper.VTASK.narrow(result));
    }

    public <R> VTaskPath<R> yield(Function<Tuple2<A, B>, R> f) {
      Kind<VTaskKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.vtaskPath(VTaskKindHelper.VTASK.narrow(result));
    }
  }

  /** Third step in a VTaskPath comprehension. */
  public static final class VTaskPathSteps3<A, B, C> {
    private static final VTaskMonad MONAD = VTaskMonad.INSTANCE;
    private final Kind<VTaskKind.Witness, Tuple3<A, B, C>> computation;

    private VTaskPathSteps3(Kind<VTaskKind.Witness, Tuple3<A, B, C>> computation) {
      this.computation = computation;
    }

    public <D> VTaskPathSteps4<A, B, C, D> from(Function<Tuple3<A, B, C>, VTaskPath<D>> next) {
      Kind<VTaskKind.Witness, Tuple4<A, B, C, D>> newComp =
          MONAD.flatMap(
              abc ->
                  MONAD.map(
                      d -> Tuple.of(abc._1(), abc._2(), abc._3(), d),
                      VTaskKindHelper.VTASK.widen(next.apply(abc).run())),
              computation);
      return new VTaskPathSteps4<>(newComp);
    }

    public <D> VTaskPathSteps4<A, B, C, D> let(Function<Tuple3<A, B, C>, D> f) {
      Kind<VTaskKind.Witness, Tuple4<A, B, C, D>> newComp =
          MONAD.map(abc -> Tuple.of(abc._1(), abc._2(), abc._3(), f.apply(abc)), computation);
      return new VTaskPathSteps4<>(newComp);
    }

    public <R> VTaskPath<R> yield(Function3<A, B, C, R> f) {
      Kind<VTaskKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(f.apply(t._1(), t._2(), t._3()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.vtaskPath(VTaskKindHelper.VTASK.narrow(result));
    }

    public <R> VTaskPath<R> yield(Function<Tuple3<A, B, C>, R> f) {
      Kind<VTaskKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.vtaskPath(VTaskKindHelper.VTASK.narrow(result));
    }
  }

  /** Fourth step in a VTaskPath comprehension. */
  public static final class VTaskPathSteps4<A, B, C, D> {
    private static final VTaskMonad MONAD = VTaskMonad.INSTANCE;
    private final Kind<VTaskKind.Witness, Tuple4<A, B, C, D>> computation;

    private VTaskPathSteps4(Kind<VTaskKind.Witness, Tuple4<A, B, C, D>> computation) {
      this.computation = computation;
    }

    public <E> VTaskPathSteps5<A, B, C, D, E> from(
        Function<Tuple4<A, B, C, D>, VTaskPath<E>> next) {
      Kind<VTaskKind.Witness, Tuple5<A, B, C, D, E>> newComp =
          MONAD.flatMap(
              abcd ->
                  MONAD.map(
                      e -> Tuple.of(abcd._1(), abcd._2(), abcd._3(), abcd._4(), e),
                      VTaskKindHelper.VTASK.widen(next.apply(abcd).run())),
              computation);
      return new VTaskPathSteps5<>(newComp);
    }

    public <E> VTaskPathSteps5<A, B, C, D, E> let(Function<Tuple4<A, B, C, D>, E> f) {
      Kind<VTaskKind.Witness, Tuple5<A, B, C, D, E>> newComp =
          MONAD.map(
              abcd -> Tuple.of(abcd._1(), abcd._2(), abcd._3(), abcd._4(), f.apply(abcd)),
              computation);
      return new VTaskPathSteps5<>(newComp);
    }

    public <R> VTaskPath<R> yield(Function4<A, B, C, D, R> f) {
      Kind<VTaskKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(
                      f.apply(t._1(), t._2(), t._3(), t._4()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.vtaskPath(VTaskKindHelper.VTASK.narrow(result));
    }

    public <R> VTaskPath<R> yield(Function<Tuple4<A, B, C, D>, R> f) {
      Kind<VTaskKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.vtaskPath(VTaskKindHelper.VTASK.narrow(result));
    }
  }

  /** Fifth step in a VTaskPath comprehension. */
  public static final class VTaskPathSteps5<A, B, C, D, E> {
    private static final VTaskMonad MONAD = VTaskMonad.INSTANCE;
    private final Kind<VTaskKind.Witness, Tuple5<A, B, C, D, E>> computation;

    private VTaskPathSteps5(Kind<VTaskKind.Witness, Tuple5<A, B, C, D, E>> computation) {
      this.computation = computation;
    }

    public <R> VTaskPath<R> yield(Function5<A, B, C, D, E, R> f) {
      Kind<VTaskKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(
                      f.apply(t._1(), t._2(), t._3(), t._4(), t._5()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.vtaskPath(VTaskKindHelper.VTASK.narrow(result));
    }

    public <R> VTaskPath<R> yield(Function<Tuple5<A, B, C, D, E>, R> f) {
      Kind<VTaskKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.vtaskPath(VTaskKindHelper.VTASK.narrow(result));
    }
  }

  // ========================================================================
  // IdPath Steps
  // ========================================================================

  /** First step in an IdPath comprehension. */
  public static final class IdPathSteps1<A> {
    private static final IdMonad MONAD = IdMonad.instance();
    private final Kind<IdKind.Witness, A> computation;

    private IdPathSteps1(IdPath<A> source) {
      this.computation = IdKindHelper.ID.widen(source.run());
    }

    public <B> IdPathSteps2<A, B> from(Function<A, IdPath<B>> next) {
      Kind<IdKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(
              a -> MONAD.map(b -> Tuple.of(a, b), IdKindHelper.ID.widen(next.apply(a).run())),
              computation);
      return new IdPathSteps2<>(newComp);
    }

    public <B> IdPathSteps2<A, B> let(Function<A, B> f) {
      Kind<IdKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, f.apply(a)), computation);
      return new IdPathSteps2<>(newComp);
    }

    public <B> IdPathSteps2<A, B> focus(FocusPath<A, B> focusPath) {
      Objects.requireNonNull(focusPath, "focusPath must not be null");
      Kind<IdKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, focusPath.get(a)), computation);
      return new IdPathSteps2<>(newComp);
    }

    public <R> IdPath<R> yield(Function<A, R> f) {
      Kind<IdKind.Witness, R> result = MONAD.map(f, computation);
      return Path.idPath(IdKindHelper.ID.narrow(result));
    }
  }

  /** Second step in an IdPath comprehension. */
  public static final class IdPathSteps2<A, B> {
    private static final IdMonad MONAD = IdMonad.instance();
    private final Kind<IdKind.Witness, Tuple2<A, B>> computation;

    private IdPathSteps2(Kind<IdKind.Witness, Tuple2<A, B>> computation) {
      this.computation = computation;
    }

    public <C> IdPathSteps3<A, B, C> from(Function<Tuple2<A, B>, IdPath<C>> next) {
      Kind<IdKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(
              ab ->
                  MONAD.map(
                      c -> Tuple.of(ab._1(), ab._2(), c),
                      IdKindHelper.ID.widen(next.apply(ab).run())),
              computation);
      return new IdPathSteps3<>(newComp);
    }

    public <C> IdPathSteps3<A, B, C> let(Function<Tuple2<A, B>, C> f) {
      Kind<IdKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), computation);
      return new IdPathSteps3<>(newComp);
    }

    public <R> IdPath<R> yield(BiFunction<A, B, R> f) {
      Kind<IdKind.Witness, R> result =
          MONAD.map(
              t -> Objects.requireNonNull(f.apply(t._1(), t._2()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.idPath(IdKindHelper.ID.narrow(result));
    }

    public <R> IdPath<R> yield(Function<Tuple2<A, B>, R> f) {
      Kind<IdKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.idPath(IdKindHelper.ID.narrow(result));
    }
  }

  /** Third step in an IdPath comprehension. */
  public static final class IdPathSteps3<A, B, C> {
    private static final IdMonad MONAD = IdMonad.instance();
    private final Kind<IdKind.Witness, Tuple3<A, B, C>> computation;

    private IdPathSteps3(Kind<IdKind.Witness, Tuple3<A, B, C>> computation) {
      this.computation = computation;
    }

    public <R> IdPath<R> yield(Function3<A, B, C, R> f) {
      Kind<IdKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(f.apply(t._1(), t._2(), t._3()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return Path.idPath(IdKindHelper.ID.narrow(result));
    }

    public <R> IdPath<R> yield(Function<Tuple3<A, B, C>, R> f) {
      Kind<IdKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return Path.idPath(IdKindHelper.ID.narrow(result));
    }
  }

  // ========================================================================
  // NonDetPath Steps (Filterable, uses Cartesian product semantics)
  // ========================================================================

  /** First step in a NonDetPath comprehension. */
  public static final class NonDetPathSteps1<A> {
    private static final ListMonad MONAD = ListMonad.INSTANCE;
    private final Kind<ListKind.Witness, A> computation;

    private NonDetPathSteps1(NonDetPath<A> source) {
      this.computation = ListKindHelper.LIST.widen(source.run());
    }

    private NonDetPathSteps1(Kind<ListKind.Witness, A> computation) {
      this.computation = computation;
    }

    public <B> NonDetPathSteps2<A, B> from(Function<A, NonDetPath<B>> next) {
      Kind<ListKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(
              a -> MONAD.map(b -> Tuple.of(a, b), ListKindHelper.LIST.widen(next.apply(a).run())),
              computation);
      return new NonDetPathSteps2<>(newComp);
    }

    public <B> NonDetPathSteps2<A, B> let(Function<A, B> f) {
      Kind<ListKind.Witness, Tuple2<A, B>> newComp =
          MONAD.map(a -> Tuple.of(a, f.apply(a)), computation);
      return new NonDetPathSteps2<>(newComp);
    }

    public NonDetPathSteps1<A> when(Predicate<A> predicate) {
      Kind<ListKind.Witness, A> newComp =
          MONAD.flatMap(a -> predicate.test(a) ? MONAD.of(a) : MONAD.zero(), computation);
      return new NonDetPathSteps1<>(newComp);
    }

    public <R> NonDetPath<R> yield(Function<A, R> f) {
      Kind<ListKind.Witness, R> result = MONAD.map(f, computation);
      return NonDetPath.of(ListKindHelper.LIST.narrow(result));
    }
  }

  /** Second step in a NonDetPath comprehension. */
  public static final class NonDetPathSteps2<A, B> {
    private static final ListMonad MONAD = ListMonad.INSTANCE;
    private final Kind<ListKind.Witness, Tuple2<A, B>> computation;

    private NonDetPathSteps2(Kind<ListKind.Witness, Tuple2<A, B>> computation) {
      this.computation = computation;
    }

    public <C> NonDetPathSteps3<A, B, C> from(Function<Tuple2<A, B>, NonDetPath<C>> next) {
      Kind<ListKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(
              ab ->
                  MONAD.map(
                      c -> Tuple.of(ab._1(), ab._2(), c),
                      ListKindHelper.LIST.widen(next.apply(ab).run())),
              computation);
      return new NonDetPathSteps3<>(newComp);
    }

    public <C> NonDetPathSteps3<A, B, C> let(Function<Tuple2<A, B>, C> f) {
      Kind<ListKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), computation);
      return new NonDetPathSteps3<>(newComp);
    }

    public NonDetPathSteps2<A, B> when(Predicate<Tuple2<A, B>> predicate) {
      Kind<ListKind.Witness, Tuple2<A, B>> newComp =
          MONAD.flatMap(ab -> predicate.test(ab) ? MONAD.of(ab) : MONAD.zero(), computation);
      return new NonDetPathSteps2<>(newComp);
    }

    public <R> NonDetPath<R> yield(BiFunction<A, B, R> f) {
      Kind<ListKind.Witness, R> result =
          MONAD.map(
              t -> Objects.requireNonNull(f.apply(t._1(), t._2()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return NonDetPath.of(ListKindHelper.LIST.narrow(result));
    }

    public <R> NonDetPath<R> yield(Function<Tuple2<A, B>, R> f) {
      Kind<ListKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return NonDetPath.of(ListKindHelper.LIST.narrow(result));
    }
  }

  /** Third step in a NonDetPath comprehension. */
  public static final class NonDetPathSteps3<A, B, C> {
    private static final ListMonad MONAD = ListMonad.INSTANCE;
    private final Kind<ListKind.Witness, Tuple3<A, B, C>> computation;

    private NonDetPathSteps3(Kind<ListKind.Witness, Tuple3<A, B, C>> computation) {
      this.computation = computation;
    }

    public NonDetPathSteps3<A, B, C> when(Predicate<Tuple3<A, B, C>> predicate) {
      Kind<ListKind.Witness, Tuple3<A, B, C>> newComp =
          MONAD.flatMap(abc -> predicate.test(abc) ? MONAD.of(abc) : MONAD.zero(), computation);
      return new NonDetPathSteps3<>(newComp);
    }

    public <R> NonDetPath<R> yield(Function3<A, B, C, R> f) {
      Kind<ListKind.Witness, R> result =
          MONAD.map(
              t ->
                  Objects.requireNonNull(f.apply(t._1(), t._2(), t._3()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return NonDetPath.of(ListKindHelper.LIST.narrow(result));
    }

    public <R> NonDetPath<R> yield(Function<Tuple3<A, B, C>, R> f) {
      Kind<ListKind.Witness, R> result =
          MONAD.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return NonDetPath.of(ListKindHelper.LIST.narrow(result));
    }
  }

  // ========================================================================
  // GenericPath Steps (Escape Hatch)
  // ========================================================================

  /** First step in a GenericPath comprehension. */
  public static final class GenericPathSteps1<F extends WitnessArity<TypeArity.Unary>, A> {
    private final Monad<F> monad;
    private final Kind<F, A> computation;

    private GenericPathSteps1(GenericPath<F, A> source) {
      this.monad = source.monad();
      this.computation = source.runKind();
    }

    public <B> GenericPathSteps2<F, A, B> from(Function<A, GenericPath<F, B>> next) {
      Kind<F, Tuple2<A, B>> newComp =
          monad.flatMap(a -> monad.map(b -> Tuple.of(a, b), next.apply(a).runKind()), computation);
      return new GenericPathSteps2<>(monad, newComp);
    }

    public <B> GenericPathSteps2<F, A, B> let(Function<A, B> f) {
      Kind<F, Tuple2<A, B>> newComp = monad.map(a -> Tuple.of(a, f.apply(a)), computation);
      return new GenericPathSteps2<>(monad, newComp);
    }

    public <B> GenericPathSteps2<F, A, B> focus(FocusPath<A, B> focusPath) {
      Objects.requireNonNull(focusPath, "focusPath must not be null");
      Kind<F, Tuple2<A, B>> newComp = monad.map(a -> Tuple.of(a, focusPath.get(a)), computation);
      return new GenericPathSteps2<>(monad, newComp);
    }

    public <R> GenericPath<F, R> yield(Function<A, R> f) {
      Kind<F, R> result = monad.map(f, computation);
      return GenericPath.of(result, monad);
    }
  }

  /** Second step in a GenericPath comprehension. */
  public static final class GenericPathSteps2<F extends WitnessArity<TypeArity.Unary>, A, B> {
    private final Monad<F> monad;
    private final Kind<F, Tuple2<A, B>> computation;

    private GenericPathSteps2(Monad<F> monad, Kind<F, Tuple2<A, B>> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    public <C> GenericPathSteps3<F, A, B, C> from(Function<Tuple2<A, B>, GenericPath<F, C>> next) {
      Kind<F, Tuple3<A, B, C>> newComp =
          monad.flatMap(
              ab -> monad.map(c -> Tuple.of(ab._1(), ab._2(), c), next.apply(ab).runKind()),
              computation);
      return new GenericPathSteps3<>(monad, newComp);
    }

    public <C> GenericPathSteps3<F, A, B, C> let(Function<Tuple2<A, B>, C> f) {
      Kind<F, Tuple3<A, B, C>> newComp =
          monad.map(ab -> Tuple.of(ab._1(), ab._2(), f.apply(ab)), computation);
      return new GenericPathSteps3<>(monad, newComp);
    }

    public <R> GenericPath<F, R> yield(BiFunction<A, B, R> f) {
      Kind<F, R> result =
          monad.map(
              t -> Objects.requireNonNull(f.apply(t._1(), t._2()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return GenericPath.of(result, monad);
    }

    public <R> GenericPath<F, R> yield(Function<Tuple2<A, B>, R> f) {
      Kind<F, R> result =
          monad.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return GenericPath.of(result, monad);
    }
  }

  /** Third step in a GenericPath comprehension. */
  public static final class GenericPathSteps3<F extends WitnessArity<TypeArity.Unary>, A, B, C> {
    private final Monad<F> monad;
    private final Kind<F, Tuple3<A, B, C>> computation;

    private GenericPathSteps3(Monad<F> monad, Kind<F, Tuple3<A, B, C>> computation) {
      this.monad = monad;
      this.computation = computation;
    }

    public <R> GenericPath<F, R> yield(Function3<A, B, C, R> f) {
      Kind<F, R> result =
          monad.map(
              t ->
                  Objects.requireNonNull(f.apply(t._1(), t._2(), t._3()), YIELD_CANNOT_RETURN_NULL),
              computation);
      return GenericPath.of(result, monad);
    }

    public <R> GenericPath<F, R> yield(Function<Tuple3<A, B, C>, R> f) {
      Kind<F, R> result =
          monad.map(t -> Objects.requireNonNull(f.apply(t), YIELD_CANNOT_RETURN_NULL), computation);
      return GenericPath.of(result, monad);
    }
  }
}
