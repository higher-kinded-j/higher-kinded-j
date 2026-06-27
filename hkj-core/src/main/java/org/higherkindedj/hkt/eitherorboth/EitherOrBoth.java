// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.eitherorboth;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.util.validation.Validation;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Represents an <em>inclusive</em>-or: a value that is a {@link Left}, a {@link Right}, <b>or
 * {@link Both} at once</b>. This is the principled type for "success that also carries accumulated,
 * non-fatal problems": a value-with-warnings result that neither {@link Either} (exclusive) nor
 * {@link Validated} (no partial value) can express.
 *
 * <p>Known in other ecosystems as {@code Ior} (Cats) or {@code These} (Haskell); this library uses
 * the descriptive name {@code EitherOrBoth} and treats {@code Ior}/{@code These} as aliases.
 *
 * <p>By convention {@code EitherOrBoth} is <b>right-biased</b>: {@link #map(Function)}, {@link
 * #flatMap(Semigroup, Function)} and friends operate on the {@link Right} (success) channel, while
 * the {@link Left} channel carries warnings or errors. A {@link Both} therefore models "a
 * successful value <em>and</em> the warnings gathered while producing it".
 *
 * <p>It is a sealed interface whose only implementations are the {@link Left}, {@link Right} and
 * {@link Both} records, so values are immutable and fully {@code switch}-matchable:
 *
 * <pre>{@code
 * String describe(EitherOrBoth<NonEmptyList<Warning>, Config> r) {
 *   return switch (r) {
 *     case EitherOrBoth.Left<NonEmptyList<Warning>, Config>(var w)        -> "failed: " + w;
 *     case EitherOrBoth.Right<NonEmptyList<Warning>, Config>(var cfg)     -> "ok: " + cfg;
 *     case EitherOrBoth.Both<NonEmptyList<Warning>, Config>(var w, var c) -> "ok with warnings: " + c;
 *   };
 * }
 * }</pre>
 *
 * <h2>The {@code flatMap} contract (the subtle part)</h2>
 *
 * <p>{@code Left} is fatal and short-circuits; {@code Both} carries its warnings and continues,
 * accumulating the left channel via a supplied {@link Semigroup}. See {@link #flatMap(Semigroup,
 * Function)} for the full matrix.
 *
 * <p><b>Null-safety.</b> Like {@link Validated}, an {@code EitherOrBoth} never holds {@code null}:
 * the {@link Left}, {@link Right} and {@link Both} components are validated non-null at
 * construction, which is what makes the total {@link #getLeft()}/{@link #getRight()} accessors
 * return a {@link Maybe} that is never {@code Just(null)}.
 *
 * @param <L> the type of the {@link Left} (warning/error) channel
 * @param <R> the type of the {@link Right} (success) channel
 * @see Either
 * @see Validated
 * @see EitherOrBothKindHelper
 */
public sealed interface EitherOrBoth<L, R> extends EitherOrBothKind<L, R>, EitherOrBothKind2<L, R>
    permits EitherOrBoth.Left, EitherOrBoth.Right, EitherOrBoth.Both {

  // --- Total accessors (no throwing getters) ---

  /**
   * Returns {@code true} if this is a {@link Left}.
   *
   * @return {@code true} for {@link Left}, otherwise {@code false}
   */
  default boolean isLeft() {
    return this instanceof Left<L, R>;
  }

  /**
   * Returns {@code true} if this is a {@link Right}.
   *
   * @return {@code true} for {@link Right}, otherwise {@code false}
   */
  default boolean isRight() {
    return this instanceof Right<L, R>;
  }

  /**
   * Returns {@code true} if this is a {@link Both}.
   *
   * @return {@code true} for {@link Both}, otherwise {@code false}
   */
  default boolean isBoth() {
    return this instanceof Both<L, R>;
  }

  /**
   * Returns the left value if present. Present for {@link Left} and {@link Both}; absent for {@link
   * Right}. This is a total accessor and never throws.
   *
   * @return {@code Just(left)} for {@link Left}/{@link Both}, otherwise {@code Nothing}
   */
  default Maybe<L> getLeft() {
    return switch (this) {
      case Left<L, R>(var left) -> Maybe.just(left);
      case Right<L, R> ignored -> Maybe.nothing();
      case Both<L, R>(var left, var ignored) -> Maybe.just(left);
    };
  }

  /**
   * Returns the right value if present. Present for {@link Right} and {@link Both}; absent for
   * {@link Left}. This is a total accessor and never throws.
   *
   * @return {@code Just(right)} for {@link Right}/{@link Both}, otherwise {@code Nothing}
   */
  default Maybe<R> getRight() {
    return switch (this) {
      case Left<L, R> ignored -> Maybe.nothing();
      case Right<L, R>(var right) -> Maybe.just(right);
      case Both<L, R>(var ignored, var right) -> Maybe.just(right);
    };
  }

  // --- Folding ---

  /**
   * Collapses this {@code EitherOrBoth} to a single value by applying the matching function.
   * Exactly one of the three functions is invoked.
   *
   * @param onLeft applied to the left value if this is a {@link Left}; must not be null
   * @param onRight applied to the right value if this is a {@link Right}; must not be null
   * @param onBoth applied to both values if this is a {@link Both}; must not be null
   * @param <T> the result type
   * @return the result of the matching function
   * @throws NullPointerException if any function is null
   */
  default <T> T fold(
      Function<? super L, ? extends T> onLeft,
      Function<? super R, ? extends T> onRight,
      BiFunction<? super L, ? super R, ? extends T> onBoth) {
    Validation.function().require(onLeft, "onLeft", FOLD);
    Validation.function().require(onRight, "onRight", FOLD);
    Validation.function().require(onBoth, "onBoth", FOLD);
    return switch (this) {
      case Left<L, R>(var left) -> onLeft.apply(left);
      case Right<L, R>(var right) -> onRight.apply(right);
      case Both<L, R>(var left, var right) -> onBoth.apply(left, right);
    };
  }

  // --- Transformations ---

  /**
   * Transforms the right (success) value, leaving the left channel untouched. Right-biased: a
   * {@link Left} is returned unchanged and a {@link Both} keeps its warnings.
   *
   * @param mapper the function to apply to the right value; must not be null
   * @param <R2> the new right type
   * @return a new {@code EitherOrBoth} with the right value transformed
   * @throws NullPointerException if {@code mapper} is null
   */
  default <R2> EitherOrBoth<L, R2> map(Function<? super R, ? extends R2> mapper) {
    Validation.function().require(mapper, "mapper", MAP);
    return switch (this) {
      case Left<L, R>(var left) -> left(left);
      case Right<L, R>(var right) -> right(mapper.apply(right));
      case Both<L, R>(var left, var right) -> both(left, mapper.apply(right));
    };
  }

  /**
   * Transforms the left (warning/error) value, leaving the right channel untouched.
   *
   * @param mapper the function to apply to the left value; must not be null
   * @param <L2> the new left type
   * @return a new {@code EitherOrBoth} with the left value transformed
   * @throws NullPointerException if {@code mapper} is null
   */
  default <L2> EitherOrBoth<L2, R> mapLeft(Function<? super L, ? extends L2> mapper) {
    Validation.function().require(mapper, "mapper", MAP_LEFT);
    return switch (this) {
      case Left<L, R>(var left) -> left(mapper.apply(left));
      case Right<L, R>(var right) -> right(right);
      case Both<L, R>(var left, var right) -> both(mapper.apply(left), right);
    };
  }

  /**
   * Transforms both channels independently. The left function reaches the warnings; the right
   * function reaches the value. For a {@link Both}, both functions are applied.
   *
   * @param leftMapper the function to apply to the left value; must not be null
   * @param rightMapper the function to apply to the right value; must not be null
   * @param <L2> the new left type
   * @param <R2> the new right type
   * @return a new {@code EitherOrBoth} with both channels transformed as appropriate
   * @throws NullPointerException if either mapper is null
   */
  default <L2, R2> EitherOrBoth<L2, R2> bimap(
      Function<? super L, ? extends L2> leftMapper, Function<? super R, ? extends R2> rightMapper) {
    Validation.function().require(leftMapper, "leftMapper", BIMAP);
    Validation.function().require(rightMapper, "rightMapper", BIMAP);
    return switch (this) {
      case Left<L, R>(var left) -> left(leftMapper.apply(left));
      case Right<L, R>(var right) -> right(rightMapper.apply(right));
      case Both<L, R>(var left, var right) ->
          both(leftMapper.apply(left), rightMapper.apply(right));
    };
  }

  /**
   * Right-biased monadic bind that accumulates the left channel via {@code semigroup}.
   *
   * <p>Semantics (the contract users must understand):
   *
   * <ul>
   *   <li>{@link Left} short-circuits; {@code mapper} is not run.
   *   <li>{@link Right} continues; the result of {@code mapper} is returned unchanged.
   *   <li>{@link Both} continues but <b>accumulates</b>: the carried warnings are combined with any
   *       warnings the result produces:
   *       <table border="1">
   *         <caption>{@code Both(l, r).flatMap(sg, f)}</caption>
   *         <tr><th>{@code f(r)}</th><th>result</th></tr>
   *         <tr><td>{@code Left(l2)}</td><td>{@code Left(l ⊕ l2)}</td></tr>
   *         <tr><td>{@code Right(r2)}</td><td>{@code Both(l, r2)}</td></tr>
   *         <tr><td>{@code Both(l2, r2)}</td><td>{@code Both(l ⊕ l2, r2)}</td></tr>
   *       </table>
   * </ul>
   *
   * <p>Accumulation is left-to-right and requires only an associative {@link Semigroup}; the
   * companion {@link EitherOrBothMonad} is lawful for any such semigroup.
   *
   * @param semigroup combines accumulated left values; must not be null
   * @param mapper the function to apply to the right value; must not be null and must not return
   *     null
   * @param <R2> the new right type
   * @return the bound result per the table above
   * @throws NullPointerException if {@code semigroup} or {@code mapper} is null
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code mapper} returns null
   */
  default <R2> EitherOrBoth<L, R2> flatMap(
      Semigroup<L> semigroup, Function<? super R, ? extends EitherOrBoth<L, ? extends R2>> mapper) {
    Validation.function().require(semigroup, "semigroup", FLAT_MAP);
    Validation.function().require(mapper, "mapper", FLAT_MAP);
    return switch (this) {
      case Left<L, R>(var left) -> left(left);
      case Right<L, R>(var right) -> {
        EitherOrBoth<L, ? extends R2> result = mapper.apply(right);
        Validation.function().requireNonNullResult(result, "mapper", FLAT_MAP);
        yield covary(result);
      }
      case Both<L, R>(var left, var right) -> {
        EitherOrBoth<L, ? extends R2> result = mapper.apply(right);
        Validation.function().requireNonNullResult(result, "mapper", FLAT_MAP);
        yield switch (result) {
          case Left<L, ? extends R2>(var l2) -> left(semigroup.combine(left, l2));
          case Right<L, ? extends R2>(var r2) -> both(left, r2);
          case Both<L, ? extends R2>(var l2, var r2) -> both(semigroup.combine(left, l2), r2);
        };
      }
    };
  }

  // --- Conversions ---

  /**
   * Converts to an {@link Either}, <b>dropping</b> any warnings: a {@link Both} becomes {@code
   * Right(right)}. Use {@link #toEitherFailingOnWarnings()} when warnings should be fatal instead.
   *
   * @return {@code Left(left)} for {@link Left}; {@code Right(right)} for {@link Right} and {@link
   *     Both}
   */
  default Either<L, R> toEitherDroppingWarnings() {
    return switch (this) {
      case Left<L, R>(var left) -> Either.left(left);
      case Right<L, R>(var right) -> Either.right(right);
      case Both<L, R>(var ignored, var right) -> Either.right(right);
    };
  }

  /**
   * Converts to an {@link Either}, treating warnings as <b>fatal</b>: a {@link Both} becomes {@code
   * Left(left)}. Use {@link #toEitherDroppingWarnings()} to keep the value and drop warnings.
   *
   * @return {@code Left(left)} for {@link Left} and {@link Both}; {@code Right(right)} for {@link
   *     Right}
   */
  default Either<L, R> toEitherFailingOnWarnings() {
    return switch (this) {
      case Left<L, R>(var left) -> Either.left(left);
      case Right<L, R>(var right) -> Either.right(right);
      case Both<L, R>(var left, var ignored) -> Either.left(left);
    };
  }

  /**
   * Converts to a {@link Validated}, dropping any warnings carried by a {@link Both}: a {@link
   * Both} becomes {@code Valid(right)}.
   *
   * @return {@code Invalid(left)} for {@link Left}; {@code Valid(right)} for {@link Right} and
   *     {@link Both}
   */
  default Validated<L, R> toValidated() {
    return switch (this) {
      case Left<L, R>(var left) -> Validated.invalid(left);
      case Right<L, R>(var right) -> Validated.valid(right);
      case Both<L, R>(var ignored, var right) -> Validated.valid(right);
    };
  }

  /**
   * Returns the right value as a {@link Maybe}, discarding the left channel entirely. Equivalent to
   * {@link #getRight()}.
   *
   * @return {@code Just(right)} for {@link Right}/{@link Both}, otherwise {@code Nothing}
   */
  default Maybe<R> toMaybe() {
    return getRight();
  }

  // --- Static factory methods ---

  /**
   * Creates a {@link Left}.
   *
   * @param value the non-null left value
   * @param <L> the left type
   * @param <R> the (absent) right type
   * @return a new {@link Left}
   * @throws NullPointerException if {@code value} is null
   */
  static <L, R> EitherOrBoth<L, R> left(L value) {
    return new Left<>(value);
  }

  /**
   * Creates a {@link Right}.
   *
   * @param value the non-null right value
   * @param <L> the (absent) left type
   * @param <R> the right type
   * @return a new {@link Right}
   * @throws NullPointerException if {@code value} is null
   */
  static <L, R> EitherOrBoth<L, R> right(R value) {
    return new Right<>(value);
  }

  /**
   * Creates a {@link Both} carrying a value <em>and</em> warnings.
   *
   * @param left the non-null left value
   * @param right the non-null right value
   * @param <L> the left type
   * @param <R> the right type
   * @return a new {@link Both}
   * @throws NullPointerException if {@code left} or {@code right} is null
   */
  static <L, R> EitherOrBoth<L, R> both(L left, R right) {
    return new Both<>(left, right);
  }

  /**
   * Lifts an {@link Either} into an {@code EitherOrBoth} (the {@link Both} case is unreachable from
   * an exclusive {@code Either}).
   *
   * @param either the source; must not be null
   * @param <L> the left type
   * @param <R> the right type
   * @return {@code Left(l)} for {@code Left(l)}; {@code Right(r)} for {@code Right(r)}
   * @throws NullPointerException if {@code either} is null
   */
  static <L, R> EitherOrBoth<L, R> fromEither(Either<L, R> either) {
    Validation.coreType().requireValue(either, EitherOrBoth.class, FROM_EITHER);
    return either.fold(EitherOrBoth::left, EitherOrBoth::right);
  }

  /**
   * Lifts a {@link Validated} into an {@code EitherOrBoth} (the {@link Both} case is unreachable
   * from an exclusive {@code Validated}).
   *
   * @param validated the source; must not be null
   * @param <L> the left/error type
   * @param <R> the right/value type
   * @return {@code Left(e)} for {@code Invalid(e)}; {@code Right(a)} for {@code Valid(a)}
   * @throws NullPointerException if {@code validated} is null
   */
  static <L, R> EitherOrBoth<L, R> fromValidated(Validated<L, R> validated) {
    Validation.coreType().requireValue(validated, EitherOrBoth.class, FROM_KIND);
    return validated.fold(EitherOrBoth::left, EitherOrBoth::right);
  }

  /**
   * Reinterprets an {@code EitherOrBoth<L, ? extends R>} as an {@code EitherOrBoth<L, R>}. Safe:
   * the type is sealed and immutable, so the narrowed right type can never be written back.
   * Centralises the covariant reinterpretation {@link #flatMap(Semigroup, Function)} would
   * otherwise inline.
   *
   * @param e the value to reinterpret; never null in practice (callers validate first)
   * @param <L> the left type
   * @param <R> the target right type
   * @return {@code e} viewed as {@code EitherOrBoth<L, R>}
   */
  @SuppressWarnings("unchecked") // sealed + immutable: covariant reinterpretation is unobservable
  private static <L, R> EitherOrBoth<L, R> covary(EitherOrBoth<L, ? extends R> e) {
    return (EitherOrBoth<L, R>) e;
  }

  /**
   * The {@link Left} case: a fatal failure with no value. Holds a non-null left value.
   *
   * @param <L> the type of the held value
   * @param <R> the (phantom) right type
   * @param value the non-null left value
   */
  record Left<L, R>(L value) implements EitherOrBoth<L, R> {

    /**
     * Canonical constructor enforcing non-null.
     *
     * @throws NullPointerException if {@code value} is null
     */
    public Left {
      Validation.coreType().requireValue(value, EitherOrBoth.class, CONSTRUCTION);
    }

    @Override
    public String toString() {
      return "Left(" + value + ")";
    }
  }

  /**
   * The {@link Right} case: a clean success with no warnings. Holds a non-null right value.
   *
   * @param <L> the (phantom) left type
   * @param <R> the type of the held value
   * @param value the non-null right value
   */
  record Right<L, R>(R value) implements EitherOrBoth<L, R> {

    /**
     * Canonical constructor enforcing non-null.
     *
     * @throws NullPointerException if {@code value} is null
     */
    public Right {
      Validation.coreType().requireValue(value, EitherOrBoth.class, CONSTRUCTION);
    }

    @Override
    public String toString() {
      return "Right(" + value + ")";
    }
  }

  /**
   * The {@link Both} case: a success that also carries warnings. Holds non-null left and right
   * values; by construction a {@code Both} always carries at least the warning value, which is what
   * makes a {@code NonEmptyList} warning channel non-empty.
   *
   * @param <L> the type of the warning/error value
   * @param <R> the type of the success value
   * @param left the non-null left (warning) value
   * @param right the non-null right (success) value
   */
  record Both<L, R>(L left, R right) implements EitherOrBoth<L, R> {

    /**
     * Canonical constructor enforcing non-null on both components.
     *
     * @throws NullPointerException if {@code left} or {@code right} is null
     */
    public Both {
      Validation.coreType().requireValue(left, EitherOrBoth.class, CONSTRUCTION);
      Validation.coreType().requireValue(right, EitherOrBoth.class, CONSTRUCTION);
    }

    @Override
    public String toString() {
      return "Both(" + left + ", " + right + ")";
    }
  }
}
