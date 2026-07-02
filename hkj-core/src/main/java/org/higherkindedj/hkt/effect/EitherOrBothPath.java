// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.effect.capability.Accumulating;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.capability.Combinable;
import org.higherkindedj.hkt.effect.capability.Recoverable;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * A fluent path wrapper for {@link EitherOrBoth} values: a success track that can also carry
 * accumulated, non-fatal warnings.
 *
 * <p>Like {@link ValidationPath}, {@code EitherOrBothPath} implements both {@link Chainable} (for
 * sequential, short-circuiting composition) and {@link Accumulating} (for parallel,
 * error-collecting composition), and carries a {@link Semigroup} for combining the left (warning)
 * channel. The common case bakes in {@code NonEmptyList.semigroup()} via {@link
 * Path#rightNel}/{@link Path#leftNel}/{@link Path#bothNel}.
 *
 * <h2>Two modes of composition</h2>
 *
 * <ul>
 *   <li><b>Short-circuit</b> ({@link #via}, {@link #zipWith}): a {@code Left} stops the chain; a
 *       {@code Both} carries its warnings forward and accumulates them, matching the monadic
 *       semantics of {@link EitherOrBoth#flatMap}.
 *   <li><b>Accumulating</b> ({@link #zipWithAccum}, {@link #andAlso}): combines independent
 *       results, collecting <em>every</em> left value (even across a fatal {@code Left}): the
 *       {@code Validated}-style accumulation.
 * </ul>
 *
 * @param <L> the type of the left (warning/error) channel
 * @param <A> the type of the success value
 */
public final class EitherOrBothPath<L, A>
    implements Chainable<A>, Accumulating<L, A>, Recoverable<L, A> {

  private final EitherOrBoth<L, A> value;
  private final Semigroup<L> semigroup;

  /**
   * Creates a new {@code EitherOrBothPath} wrapping the given value with the supplied semigroup.
   *
   * @param value the {@link EitherOrBoth} to wrap; must not be null
   * @param semigroup the semigroup for combining warnings; must not be null
   */
  EitherOrBothPath(EitherOrBoth<L, A> value, Semigroup<L> semigroup) {
    this.value = Objects.requireNonNull(value, "value must not be null");
    this.semigroup = Objects.requireNonNull(semigroup, "semigroup must not be null");
  }

  /**
   * Returns the underlying {@link EitherOrBoth}.
   *
   * @return the wrapped value
   */
  public EitherOrBoth<L, A> run() {
    return value;
  }

  /**
   * Returns the semigroup used to combine the warning channel.
   *
   * @return the semigroup
   */
  public Semigroup<L> semigroup() {
    return semigroup;
  }

  /**
   * Returns the success value (present for {@code Right} and {@code Both}), otherwise the default.
   *
   * @param defaultValue the value to return if this path is a {@code Left}
   * @return the success value or the default
   */
  public A getOrElse(A defaultValue) {
    return value.fold(l -> defaultValue, r -> r, (l, r) -> r);
  }

  /**
   * Returns the success value (present for {@code Right} and {@code Both}), otherwise the
   * supplier's result.
   *
   * @param supplier provides the default value; must not be null
   * @return the success value or the supplier's result
   * @throws NullPointerException if supplier is null
   */
  public A getOrElseGet(Supplier<? extends A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return value.fold(l -> supplier.get(), r -> r, (l, r) -> r);
  }

  /**
   * Folds the three cases into a single value.
   *
   * @param onLeft applied to the left value if this is a {@code Left}; must not be null
   * @param onRight applied to the right value if this is a {@code Right}; must not be null
   * @param onBoth applied to both values if this is a {@code Both}; must not be null
   * @param <B> the result type
   * @return the result of the matching function
   * @throws NullPointerException if any function is null
   */
  public <B> B fold(
      Function<? super L, ? extends B> onLeft,
      Function<? super A, ? extends B> onRight,
      BiFunction<? super L, ? super A, ? extends B> onBoth) {
    return value.fold(onLeft, onRight, onBoth);
  }

  /**
   * Returns the warnings carried on the left channel, if any (present for {@code Left} and {@code
   * Both}).
   *
   * @return {@code Just(left)} for {@code Left}/{@code Both}, otherwise {@code Nothing}
   */
  public Maybe<L> warnings() {
    return value.getLeft();
  }

  /**
   * Returns true if this is a {@code Left}.
   *
   * @return true for {@code Left}
   */
  public boolean isLeft() {
    return value.isLeft();
  }

  /**
   * Returns true if this is a {@code Right}.
   *
   * @return true for {@code Right}
   */
  public boolean isRight() {
    return value.isRight();
  }

  /**
   * Returns true if this is a {@code Both}.
   *
   * @return true for {@code Both}
   */
  public boolean isBoth() {
    return value.isBoth();
  }

  // ===== Conversions =====

  /**
   * Converts to an {@link EitherPath}, dropping any warnings: a {@code Both} becomes {@code Right}.
   *
   * @return an EitherPath with warnings discarded
   */
  public EitherPath<L, A> toEitherPathDroppingWarnings() {
    return new EitherPath<>(value.toEitherDroppingWarnings());
  }

  /**
   * Converts to an {@link EitherPath}, treating warnings as fatal: a {@code Both} becomes {@code
   * Left}.
   *
   * @return an EitherPath with warnings treated as errors
   */
  public EitherPath<L, A> toEitherPathFailingOnWarnings() {
    return new EitherPath<>(value.toEitherFailingOnWarnings());
  }

  /**
   * Converts to a {@link ValidationPath}, dropping any warnings carried by a {@code Both}.
   *
   * @return a ValidationPath sharing this path's semigroup
   */
  public ValidationPath<L, A> toValidationPath() {
    return new ValidationPath<>(value.toValidated(), semigroup);
  }

  /**
   * Converts to a {@link MaybePath}, keeping the success value and discarding the left channel.
   *
   * @return a MaybePath containing the value if {@code Right}/{@code Both}, otherwise empty
   */
  public MaybePath<A> toMaybePath() {
    return new MaybePath<>(value.toMaybe());
  }

  /**
   * Converts to an {@link OptionalPath}, keeping the success value and discarding the left channel.
   *
   * @return an OptionalPath containing the value if {@code Right}/{@code Both}, otherwise empty
   */
  public OptionalPath<A> toOptionalPath() {
    return value.fold(
        l -> new OptionalPath<>(Optional.empty()),
        r -> new OptionalPath<>(Optional.of(r)),
        (l, r) -> new OptionalPath<>(Optional.of(r)));
  }

  /**
   * Converts to a {@link TryPath}: a {@code Left} becomes a failure (via {@code errorToException});
   * a {@code Right}/{@code Both} becomes a success holding the value.
   *
   * @param errorToException converts the left value to an exception; must not be null
   * @return a TryPath representing this path
   * @throws NullPointerException if errorToException is null
   */
  public TryPath<A> toTryPath(Function<? super L, ? extends Throwable> errorToException) {
    Objects.requireNonNull(errorToException, "errorToException must not be null");
    return value.fold(
        l -> new TryPath<>(Try.failure(errorToException.apply(l))),
        r -> new TryPath<>(Try.success(r)),
        (l, r) -> new TryPath<>(Try.success(r)));
  }

  // ===== Composable =====

  @Override
  public <B> EitherOrBothPath<L, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return new EitherOrBothPath<>(value.map(mapper), semigroup);
  }

  @Override
  public EitherOrBothPath<L, A> peek(Consumer<? super A> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    Maybe<A> right = value.getRight();
    if (right.isJust()) {
      consumer.accept(right.get());
    }
    return this;
  }

  /**
   * Observes the warning channel without modifying it (for debugging).
   *
   * @param consumer the action to perform on the warnings; must not be null
   * @return this path unchanged
   * @throws NullPointerException if consumer is null
   */
  public EitherOrBothPath<L, A> peekLeft(Consumer<? super L> consumer) {
    Objects.requireNonNull(consumer, "consumer must not be null");
    Maybe<L> left = value.getLeft();
    if (left.isJust()) {
      consumer.accept(left.get());
    }
    return this;
  }

  // ===== Combinable (short-circuit, monad-consistent) =====

  @Override
  public <B, C> EitherOrBothPath<L, C> zipWith(
      Combinable<B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");

    if (!(other instanceof EitherOrBothPath<?, ?> otherPath)) {
      throw new IllegalArgumentException(
          "Cannot zipWith non-EitherOrBothPath: " + other.getClass());
    }
    @SuppressWarnings("unchecked")
    EitherOrBothPath<L, B> typedOther = (EitherOrBothPath<L, B>) otherPath;

    EitherOrBoth<L, C> result =
        value.flatMap(semigroup, a -> typedOther.value.map(b -> combiner.apply(a, b)));
    return new EitherOrBothPath<>(result, semigroup);
  }

  /**
   * Combines this path with two others (short-circuit, monad-consistent).
   *
   * @param second the second path; must not be null
   * @param third the third path; must not be null
   * @param combiner combines the three values; must not be null
   * @param <B> the second path's value type
   * @param <C> the third path's value type
   * @param <D> the combined result type
   * @return the combined path
   * @throws NullPointerException if any argument is null
   */
  public <B, C, D> EitherOrBothPath<L, D> zipWith3(
      EitherOrBothPath<L, B> second,
      EitherOrBothPath<L, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");
    return this.zipWith(second, Tuple2::new)
        .zipWith(third, (pair, c) -> combiner.apply(pair._1(), pair._2(), c));
  }

  // ===== Accumulating (collect every left) =====

  @Override
  public <B, C> EitherOrBothPath<L, C> zipWithAccum(
      Accumulating<L, B> other, BiFunction<? super A, ? super B, ? extends C> combiner) {
    Objects.requireNonNull(other, "other must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");
    EitherOrBothPath<L, B> typedOther = narrowAccumulating(other);
    return new EitherOrBothPath<>(
        value.zipWithAccum(typedOther.value, semigroup, combiner), semigroup);
  }

  @Override
  public <B, C, D> EitherOrBothPath<L, D> zipWith3Accum(
      Accumulating<L, B> second,
      Accumulating<L, C> third,
      Function3<? super A, ? super B, ? super C, ? extends D> combiner) {
    Objects.requireNonNull(second, "second must not be null");
    Objects.requireNonNull(third, "third must not be null");
    Objects.requireNonNull(combiner, "combiner must not be null");
    return this.zipWithAccum(second, Tuple2::new)
        .zipWithAccum(third, (pair, c) -> combiner.apply(pair._1(), pair._2(), c));
  }

  @Override
  public EitherOrBothPath<L, A> andAlso(Accumulating<L, ?> other) {
    Objects.requireNonNull(other, "other must not be null");
    return zipWithAccum(other, (a, ignored) -> a);
  }

  @Override
  public <B> EitherOrBothPath<L, B> andThen(Accumulating<L, B> other) {
    Objects.requireNonNull(other, "other must not be null");
    return zipWithAccum(other, (ignored, b) -> b);
  }

  // ===== Chainable (short-circuit, accumulating Both) =====

  @Override
  public <B> EitherOrBothPath<L, B> via(Function<? super A, ? extends Chainable<B>> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    EitherOrBoth<L, B> result =
        value.flatMap(
            semigroup,
            a -> {
              Chainable<B> r = mapper.apply(a);
              Objects.requireNonNull(r, "mapper must not return null");
              return EitherOrBothPath.<L, B>narrowChainable(r, "via mapper").value;
            });
    return new EitherOrBothPath<>(result, semigroup);
  }

  @Override
  public <B> EitherOrBothPath<L, B> then(Supplier<? extends Chainable<B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    EitherOrBoth<L, B> result =
        value.flatMap(
            semigroup,
            ignored -> {
              Chainable<B> r = supplier.get();
              Objects.requireNonNull(r, "supplier must not return null");
              return EitherOrBothPath.<L, B>narrowChainable(r, "then supplier").value;
            });
    return new EitherOrBothPath<>(result, semigroup);
  }

  // ===== Recoverable =====

  @Override
  public EitherOrBothPath<L, A> recover(Function<? super L, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return value.fold(
        l -> new EitherOrBothPath<>(EitherOrBoth.right(recovery.apply(l)), semigroup),
        r -> this,
        (l, r) -> this);
  }

  @Override
  public EitherOrBothPath<L, A> recoverWith(
      Function<? super L, ? extends Recoverable<L, A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");
    return value.fold(
        l -> {
          Recoverable<L, A> r = recovery.apply(l);
          Objects.requireNonNull(r, "recovery must not return null");
          return EitherOrBothPath.<L, A>narrowRecoverable(r, "recovery");
        },
        r -> this,
        (l, r) -> this);
  }

  @Override
  public EitherOrBothPath<L, A> orElse(Supplier<? extends Recoverable<L, A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    return value.fold(
        l -> {
          Recoverable<L, A> r = alternative.get();
          Objects.requireNonNull(r, "alternative must not return null");
          return EitherOrBothPath.<L, A>narrowRecoverable(r, "alternative");
        },
        r -> this,
        (l, r) -> this);
  }

  /**
   * Transforms the warning type. Because accumulation needs a {@link Semigroup} for the new type,
   * this is only supported when there are no warnings to carry (a {@code Right}); use {@link
   * #mapErrorWith(Function, Semigroup)} otherwise.
   *
   * <p><b>Caveat for the {@code Right} case:</b> the returned path keeps the value but carries a
   * placeholder semigroup, so a later operation that would accumulate warnings (for example
   * combining it with another path) throws {@link UnsupportedOperationException}. Prefer {@link
   * #mapErrorWith(Function, Semigroup)}, which supplies a real semigroup for the new type.
   *
   * @param mapper the function to transform the warnings; must not be null
   * @param <L2> the new warning type
   * @return a path with the transformed warning type (for {@code Right} values only)
   * @throws UnsupportedOperationException if this path carries warnings ({@code Left} or {@code
   *     Both})
   * @throws NullPointerException if mapper is null
   * @see #mapErrorWith(Function, Semigroup)
   */
  @Override
  public <L2> EitherOrBothPath<L2, A> mapError(Function<? super L, ? extends L2> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    return value.fold(
        l -> {
          throw mapErrorUnsupported();
        },
        r -> new EitherOrBothPath<>(EitherOrBoth.right(r), placeholderSemigroup()),
        (l, r) -> {
          throw mapErrorUnsupported();
        });
  }

  /**
   * Transforms the warning type with a new {@link Semigroup}, preserving accumulation capability.
   * This is the supported way to rebase the warning channel.
   *
   * @param mapper the function to transform the warnings; must not be null
   * @param newSemigroup the semigroup for the new warning type; must not be null
   * @param <L2> the new warning type
   * @return a path with the transformed warning type and new semigroup
   * @throws NullPointerException if mapper or newSemigroup is null
   */
  public <L2> EitherOrBothPath<L2, A> mapErrorWith(
      Function<? super L, ? extends L2> mapper, Semigroup<L2> newSemigroup) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    Objects.requireNonNull(newSemigroup, "newSemigroup must not be null");
    return new EitherOrBothPath<>(value.mapLeft(mapper), newSemigroup);
  }

  // ===== Focus bridge =====

  /**
   * Applies a {@link FocusPath} to navigate within the success value, preserving the case.
   *
   * @param path the FocusPath to apply; must not be null
   * @param <B> the focused type
   * @return a new path containing the focused value
   * @throws NullPointerException if path is null
   */
  public <B> EitherOrBothPath<L, B> focus(FocusPath<A, B> path) {
    Objects.requireNonNull(path, "path must not be null");
    return map(path::get);
  }

  /**
   * Applies an {@link AffinePath} to navigate within the success value. If the path does not match,
   * a {@code Left} with {@code errorIfAbsent} is produced; warnings carried by a {@code Both} are
   * preserved through the navigation.
   *
   * @param path the AffinePath to apply; must not be null
   * @param errorIfAbsent the warning value to use if the path does not match; must not be null
   * @param <B> the focused type
   * @return a new path containing the focused value or the error
   * @throws NullPointerException if path or errorIfAbsent is null
   */
  public <B> EitherOrBothPath<L, B> focus(AffinePath<A, B> path, L errorIfAbsent) {
    Objects.requireNonNull(path, "path must not be null");
    Objects.requireNonNull(errorIfAbsent, "errorIfAbsent must not be null");
    return via(
        a ->
            path.getOptional(a)
                .<EitherOrBothPath<L, B>>map(b -> Path.right(b, semigroup))
                .orElseGet(() -> Path.left(errorIfAbsent, semigroup)));
  }

  // ===== Object methods =====

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof EitherOrBothPath<?, ?> other)) return false;
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "EitherOrBothPath(" + value + ")";
  }

  // ===== Helpers =====

  private static <L, B> EitherOrBothPath<L, B> narrowChainable(Chainable<B> c, String op) {
    if (!(c instanceof EitherOrBothPath<?, ?> path)) {
      throw new IllegalArgumentException(
          op + " must return EitherOrBothPath, got: " + c.getClass());
    }
    @SuppressWarnings("unchecked")
    EitherOrBothPath<L, B> typed = (EitherOrBothPath<L, B>) path;
    return typed;
  }

  private static <L, A> EitherOrBothPath<L, A> narrowRecoverable(Recoverable<L, A> r, String op) {
    if (!(r instanceof EitherOrBothPath<?, ?> path)) {
      throw new IllegalArgumentException(
          op + " must return EitherOrBothPath, got: " + r.getClass());
    }
    @SuppressWarnings("unchecked")
    EitherOrBothPath<L, A> typed = (EitherOrBothPath<L, A>) path;
    return typed;
  }

  private static <L, B> EitherOrBothPath<L, B> narrowAccumulating(Accumulating<L, B> other) {
    if (!(other instanceof EitherOrBothPath<?, ?> path)) {
      throw new IllegalArgumentException(
          "Cannot accumulate with non-EitherOrBothPath: " + other.getClass());
    }
    @SuppressWarnings("unchecked")
    EitherOrBothPath<L, B> typed = (EitherOrBothPath<L, B>) path;
    return typed;
  }

  private static UnsupportedOperationException mapErrorUnsupported() {
    return new UnsupportedOperationException(
        "mapError on EitherOrBothPath that carries warnings requires a new Semigroup for the new "
            + "warning type. Use mapErrorWith(mapper, newSemigroup) instead.");
  }

  private static <L2> Semigroup<L2> placeholderSemigroup() {
    return (a, b) -> {
      throw new UnsupportedOperationException(
          "Cannot accumulate warnings after mapError. Create a new EitherOrBothPath with a proper "
              + "Semigroup (see mapErrorWith).");
    };
  }
}
