// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe_t.MaybeTKindHelper.MAYBE_T;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.maybe_t.MaybeTKind;
import org.higherkindedj.hkt.maybe_t.MaybeTMonad;
import org.jspecify.annotations.Nullable;

/**
 * Effect context for optional values across effectful operations.
 *
 * <p>OptionalContext wraps {@link MaybeT} with a user-friendly API, hiding the complexity of
 * higher-kinded types while preserving the full capability of the transformer. It provides
 * optionality for computations that may not produce a value.
 *
 * <h2>Factory Methods</h2>
 *
 * <ul>
 *   <li>{@link #io(Supplier)} - Create from a computation that may return null
 *   <li>{@link #ioMaybe(Supplier)} - Create from a computation returning Maybe
 *   <li>{@link #some(Object)} - Create a context containing a value
 *   <li>{@link #none()} - Create an empty context
 *   <li>{@link #fromMaybe(Maybe)} - Create from an existing Maybe
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Maybe<Profile> profile = OptionalContext
 *     .<User>io(() -> userRepo.findById(userId))
 *     .via(user -> OptionalContext.io(() -> profileRepo.findByUserId(user.id())))
 *     .orElse(() -> OptionalContext.some(Profile.defaultProfile()))
 *     .runIO()
 *     .unsafeRun();
 * }</pre>
 *
 * @param <F> the underlying effect type witness (e.g., {@code IOKind.Witness})
 * @param <A> the value type
 */
public final class OptionalContext<F, A> implements EffectContext<F, A> {

  private final MaybeT<F, A> transformer;
  private final Monad<F> outerMonad;
  private final MaybeTMonad<F> maybeTMonad;

  private OptionalContext(MaybeT<F, A> transformer, Monad<F> outerMonad) {
    this.transformer = Objects.requireNonNull(transformer, "transformer must not be null");
    this.outerMonad = Objects.requireNonNull(outerMonad, "outerMonad must not be null");
    this.maybeTMonad = new MaybeTMonad<>(outerMonad);
  }

  // --- Factory Methods for IO-based contexts ---

  /**
   * Creates an OptionalContext from a computation that may return null.
   *
   * <p>If the computation returns null, the context will be empty (Nothing). If it returns a value,
   * the context will contain that value (Just).
   *
   * @param supplier the computation to execute; must not be null
   * @param <A> the value type
   * @return a new OptionalContext wrapping the computation
   * @throws NullPointerException if supplier is null
   */
  public static <A> OptionalContext<IOKind.Witness, A> io(Supplier<@Nullable A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    IO<Maybe<A>> io = IO.delay(() -> Maybe.fromNullable(supplier.get()));
    MaybeT<IOKind.Witness, A> transformer = MaybeT.fromKind(IO_OP.widen(io));
    return new OptionalContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates an OptionalContext from a computation that returns a Maybe.
   *
   * <p>Use this when the computation already handles its own optionality.
   *
   * @param supplier the computation returning Maybe; must not be null
   * @param <A> the value type
   * @return a new OptionalContext wrapping the computation
   * @throws NullPointerException if supplier is null
   */
  public static <A> OptionalContext<IOKind.Witness, A> ioMaybe(Supplier<Maybe<A>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    IO<Maybe<A>> io = IO.delay(supplier);
    MaybeT<IOKind.Witness, A> transformer = MaybeT.fromKind(IO_OP.widen(io));
    return new OptionalContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates an OptionalContext containing the given value.
   *
   * @param value the value to contain; must not be null
   * @param <A> the value type
   * @return a new OptionalContext containing the value
   * @throws NullPointerException if value is null
   */
  public static <A extends Object> OptionalContext<IOKind.Witness, A> some(A value) {
    Objects.requireNonNull(value, "value must not be null; use none() for empty context");
    MaybeT<IOKind.Witness, A> transformer = MaybeT.just(IOMonad.INSTANCE, value);
    return new OptionalContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates an empty OptionalContext (Nothing).
   *
   * @param <A> the value type
   * @return a new empty OptionalContext
   */
  public static <A> OptionalContext<IOKind.Witness, A> none() {
    MaybeT<IOKind.Witness, A> transformer = MaybeT.nothing(IOMonad.INSTANCE);
    return new OptionalContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates an OptionalContext from an existing Maybe value.
   *
   * @param maybe the Maybe to wrap; must not be null
   * @param <A> the value type
   * @return a new OptionalContext wrapping the Maybe
   * @throws NullPointerException if maybe is null
   */
  public static <A> OptionalContext<IOKind.Witness, A> fromMaybe(Maybe<A> maybe) {
    Objects.requireNonNull(maybe, "maybe must not be null");
    MaybeT<IOKind.Witness, A> transformer = MaybeT.fromMaybe(IOMonad.INSTANCE, maybe);
    return new OptionalContext<>(transformer, IOMonad.INSTANCE);
  }

  // --- Chainable Operations ---

  @Override
  @SuppressWarnings("unchecked")
  public <B> OptionalContext<F, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    Kind<MaybeTKind.Witness<F>, B> result = maybeTMonad.map(mapper, MAYBE_T.widen(transformer));
    return new OptionalContext<>(MAYBE_T.narrow(result), outerMonad);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <B> OptionalContext<F, B> via(Function<? super A, ? extends EffectContext<F, B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");

    Kind<MaybeTKind.Witness<F>, B> result =
        maybeTMonad.flatMap(
            a -> {
              EffectContext<F, B> next = fn.apply(a);
              Objects.requireNonNull(next, "fn must not return null");
              if (!(next instanceof OptionalContext<?, ?> nextCtx)) {
                throw new IllegalArgumentException(
                    "via function must return an OptionalContext, got: "
                        + next.getClass().getName());
              }
              @SuppressWarnings("unchecked")
              OptionalContext<F, B> typedNext = (OptionalContext<F, B>) nextCtx;
              return MAYBE_T.widen(typedNext.transformer);
            },
            MAYBE_T.widen(transformer));

    return new OptionalContext<>(MAYBE_T.narrow(result), outerMonad);
  }

  /**
   * Chains a dependent computation using OptionalContext-specific typing.
   *
   * <p>This is a convenience method that preserves the type in the signature.
   *
   * @param fn the function to apply, returning a new OptionalContext; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context returned by the function, or an empty context
   * @throws NullPointerException if fn is null or returns null
   */
  public <B> OptionalContext<F, B> flatMap(
      Function<? super A, ? extends OptionalContext<F, B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");

    Kind<MaybeTKind.Witness<F>, B> result =
        maybeTMonad.flatMap(
            a -> {
              OptionalContext<F, B> next = fn.apply(a);
              Objects.requireNonNull(next, "fn must not return null");
              return MAYBE_T.widen(next.transformer);
            },
            MAYBE_T.widen(transformer));

    return new OptionalContext<>(MAYBE_T.narrow(result), outerMonad);
  }

  /**
   * Sequences an independent computation, discarding this context's value.
   *
   * <p>This is useful for sequencing effects where only the final result matters.
   *
   * @param supplier provides the next context; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context from the supplier
   * @throws NullPointerException if supplier is null or returns null
   */
  public <B> OptionalContext<F, B> then(Supplier<? extends OptionalContext<F, B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return flatMap(ignored -> supplier.get());
  }

  // --- Recovery Operations ---

  /**
   * Provides a fallback value if this context is empty.
   *
   * <p>If this context is empty (Nothing), the recovery function is applied to produce a value. If
   * this context contains a value, it is returned unchanged.
   *
   * @param recovery the function to produce a fallback value; must not be null
   * @return a context containing either the original value or the recovered value
   * @throws NullPointerException if recovery is null
   */
  public OptionalContext<F, A> recover(Function<? super Unit, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");

    Kind<MaybeTKind.Witness<F>, A> result =
        maybeTMonad.handleErrorWith(
            MAYBE_T.widen(transformer), unit -> maybeTMonad.of(recovery.apply(unit)));

    return new OptionalContext<>(MAYBE_T.narrow(result), outerMonad);
  }

  /**
   * Provides a fallback value if this context is empty.
   *
   * <p>This is a convenience method that ignores the Unit error type.
   *
   * @param defaultValue the default value to use if empty
   * @return a context containing either the original value or the default
   */
  public OptionalContext<F, A> orElseValue(A defaultValue) {
    return recover(ignored -> defaultValue);
  }

  /**
   * Provides a fallback OptionalContext if this one is empty.
   *
   * <p>If this context is empty, the recovery function is applied to produce an alternative
   * context. If this context contains a value, it is returned unchanged.
   *
   * @param recovery the function to produce a fallback context; must not be null
   * @return either this context (if non-empty) or the fallback context
   * @throws NullPointerException if recovery is null or returns null
   */
  public OptionalContext<F, A> recoverWith(
      Function<? super Unit, ? extends OptionalContext<F, A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");

    Kind<MaybeTKind.Witness<F>, A> result =
        maybeTMonad.handleErrorWith(
            MAYBE_T.widen(transformer),
            unit -> {
              OptionalContext<F, A> next = recovery.apply(unit);
              Objects.requireNonNull(next, "recovery must not return null");
              return MAYBE_T.widen(next.transformer);
            });

    return new OptionalContext<>(MAYBE_T.narrow(result), outerMonad);
  }

  /**
   * Provides an alternative context if this one is empty.
   *
   * <p>This is a convenience method that ignores the Unit error type.
   *
   * @param alternative provides the fallback context; must not be null
   * @return either this context (if non-empty) or the alternative
   * @throws NullPointerException if alternative is null or returns null
   */
  public OptionalContext<F, A> orElse(Supplier<? extends OptionalContext<F, A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    return recoverWith(ignored -> alternative.get());
  }

  // --- Conversion Methods ---

  /**
   * Converts this OptionalContext to an ErrorContext with a typed error.
   *
   * <p>If this context is empty, the resulting ErrorContext will contain the provided error. If
   * this context contains a value, the ErrorContext will contain that value.
   *
   * <p>This method runs the underlying computation to check the result. For a deferred conversion,
   * use the escape hatch to MaybeT and convert manually.
   *
   * @param errorForNothing the error to use if this context is empty
   * @param <E> the error type
   * @return an ErrorContext representing this context with typed errors
   * @throws ClassCastException if F is not IOKind.Witness
   */
  public <E> ErrorContext<IOKind.Witness, E, A> toErrorContext(E errorForNothing) {
    Maybe<A> result = runIO().unsafeRun();
    return result.isJust()
        ? ErrorContext.success(result.get())
        : ErrorContext.failure(errorForNothing);
  }

  // --- Execution Methods (IO-specific) ---

  /**
   * Runs the computation and returns an IOPath containing the Maybe result.
   *
   * <p>This method is only available when F is IOKind.Witness.
   *
   * @return an IOPath that will produce the Maybe when run
   * @throws ClassCastException if F is not IOKind.Witness
   */
  @SuppressWarnings("unchecked")
  public IOPath<Maybe<A>> runIO() {
    Kind<F, Maybe<A>> underlying = transformer.value();
    IO<Maybe<A>> io = IO_OP.narrow((Kind<IOKind.Witness, Maybe<A>>) underlying);
    return Path.ioPath(io);
  }

  /**
   * Runs the computation and returns the value, or a default if empty.
   *
   * @param defaultValue the value to return if the context is empty
   * @return the contained value or the default
   * @throws ClassCastException if F is not IOKind.Witness
   */
  public A runIOOrElse(A defaultValue) {
    Maybe<A> result = runIO().unsafeRun();
    return result.isJust() ? result.get() : defaultValue;
  }

  /**
   * Runs the computation and returns the value, throwing on empty.
   *
   * @return the contained value
   * @throws java.util.NoSuchElementException if the context is empty
   * @throws ClassCastException if F is not IOKind.Witness
   */
  public A runIOOrThrow() {
    Maybe<A> result = runIO().unsafeRun();
    return result.get(); // Will throw NoSuchElementException if Nothing
  }

  // --- Escape Hatch ---

  /**
   * Returns the underlying MaybeT transformer.
   *
   * <p>This is an escape hatch to Layer 3 (raw transformers) for users who need full control over
   * the transformer operations.
   *
   * @return the underlying MaybeT transformer
   */
  public MaybeT<F, A> toMaybeT() {
    return transformer;
  }

  @Override
  public Kind<?, A> underlying() {
    return MAYBE_T.widen(transformer);
  }

  @Override
  public String toString() {
    return "OptionalContext(" + transformer + ")";
  }
}
