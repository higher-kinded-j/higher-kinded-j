// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Objects;
import java.util.Optional;
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
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.optional_t.OptionalTKind;
import org.higherkindedj.hkt.optional_t.OptionalTMonad;
import org.jspecify.annotations.Nullable;

/**
 * Effect context for optional values using Java's {@link Optional}.
 *
 * <p>JavaOptionalContext wraps {@link OptionalT} with a user-friendly API, hiding the complexity of
 * higher-kinded types while preserving the full capability of the transformer. It provides
 * optionality for computations that may not produce a value, specifically targeting Java's {@link
 * Optional} type.
 *
 * <p>This context is similar to {@link OptionalContext} but uses {@link java.util.Optional} instead
 * of the library's {@link org.higherkindedj.hkt.maybe.Maybe} type, making it more convenient for
 * code that already works with Java's standard Optional.
 *
 * <h2>Factory Methods</h2>
 *
 * <ul>
 *   <li>{@link #io(Supplier)} - Create from a computation that may return null
 *   <li>{@link #ioOptional(Supplier)} - Create from a computation returning Optional
 *   <li>{@link #some(Object)} - Create a context containing a value
 *   <li>{@link #none()} - Create an empty context
 *   <li>{@link #fromOptional(Optional)} - Create from an existing Optional
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * Optional<Profile> profile = JavaOptionalContext
 *     .<User>io(() -> userRepo.findById(userId))
 *     .via(user -> JavaOptionalContext.io(() -> profileRepo.findByUserId(user.id())))
 *     .orElse(() -> JavaOptionalContext.some(Profile.defaultProfile()))
 *     .runIO()
 *     .unsafeRun();
 * }</pre>
 *
 * @param <F> the underlying effect type witness (e.g., {@code IOKind.Witness})
 * @param <A> the value type
 */
public final class JavaOptionalContext<F, A> implements EffectContext<F, A> {

  private final OptionalT<F, A> transformer;
  private final Monad<F> outerMonad;
  private final OptionalTMonad<F> optionalTMonad;

  private JavaOptionalContext(OptionalT<F, A> transformer, Monad<F> outerMonad) {
    this.transformer = Objects.requireNonNull(transformer, "transformer must not be null");
    this.outerMonad = Objects.requireNonNull(outerMonad, "outerMonad must not be null");
    this.optionalTMonad = new OptionalTMonad<>(outerMonad);
  }

  // --- Factory Methods for IO-based contexts ---

  /**
   * Creates a JavaOptionalContext from a computation that may return null.
   *
   * <p>If the computation returns null, the context will be empty. If it returns a value, the
   * context will contain that value.
   *
   * @param supplier the computation to execute; must not be null
   * @param <A> the value type
   * @return a new JavaOptionalContext wrapping the computation
   * @throws NullPointerException if supplier is null
   */
  public static <A> JavaOptionalContext<IOKind.Witness, A> io(Supplier<@Nullable A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    IO<Optional<A>> io = IO.delay(() -> Optional.ofNullable(supplier.get()));
    OptionalT<IOKind.Witness, A> transformer = OptionalT.fromKind(IO_OP.widen(io));
    return new JavaOptionalContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a JavaOptionalContext from a computation that returns an Optional.
   *
   * <p>Use this when the computation already handles its own optionality.
   *
   * @param supplier the computation returning Optional; must not be null
   * @param <A> the value type
   * @return a new JavaOptionalContext wrapping the computation
   * @throws NullPointerException if supplier is null
   */
  public static <A> JavaOptionalContext<IOKind.Witness, A> ioOptional(
      Supplier<Optional<A>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");

    IO<Optional<A>> io = IO.delay(supplier);
    OptionalT<IOKind.Witness, A> transformer = OptionalT.fromKind(IO_OP.widen(io));
    return new JavaOptionalContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a JavaOptionalContext containing the given value.
   *
   * @param value the value to contain; must not be null
   * @param <A> the value type
   * @return a new JavaOptionalContext containing the value
   * @throws NullPointerException if value is null
   */
  public static <A extends Object> JavaOptionalContext<IOKind.Witness, A> some(A value) {
    Objects.requireNonNull(value, "value must not be null; use none() for empty context");
    OptionalT<IOKind.Witness, A> transformer = OptionalT.some(IOMonad.INSTANCE, value);
    return new JavaOptionalContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates an empty JavaOptionalContext.
   *
   * @param <A> the value type
   * @return a new empty JavaOptionalContext
   */
  public static <A> JavaOptionalContext<IOKind.Witness, A> none() {
    OptionalT<IOKind.Witness, A> transformer = OptionalT.none(IOMonad.INSTANCE);
    return new JavaOptionalContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a JavaOptionalContext from an existing Optional value.
   *
   * @param optional the Optional to wrap; must not be null
   * @param <A> the value type
   * @return a new JavaOptionalContext wrapping the Optional
   * @throws NullPointerException if optional is null
   */
  public static <A> JavaOptionalContext<IOKind.Witness, A> fromOptional(Optional<A> optional) {
    Objects.requireNonNull(optional, "optional must not be null");
    OptionalT<IOKind.Witness, A> transformer = OptionalT.fromOptional(IOMonad.INSTANCE, optional);
    return new JavaOptionalContext<>(transformer, IOMonad.INSTANCE);
  }

  // --- Chainable Operations ---

  @Override
  @SuppressWarnings("unchecked")
  public <B> JavaOptionalContext<F, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    Kind<OptionalTKind.Witness<F>, B> result =
        optionalTMonad.map(mapper, OPTIONAL_T.widen(transformer));
    return new JavaOptionalContext<>(OPTIONAL_T.narrow(result), outerMonad);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <B> JavaOptionalContext<F, B> via(Function<? super A, ? extends EffectContext<F, B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");

    Kind<OptionalTKind.Witness<F>, B> result =
        optionalTMonad.flatMap(
            a -> {
              EffectContext<F, B> next = fn.apply(a);
              Objects.requireNonNull(next, "fn must not return null");
              if (!(next instanceof JavaOptionalContext<?, ?> nextCtx)) {
                throw new IllegalArgumentException(
                    "via function must return a JavaOptionalContext, got: "
                        + next.getClass().getName());
              }
              @SuppressWarnings("unchecked")
              JavaOptionalContext<F, B> typedNext = (JavaOptionalContext<F, B>) nextCtx;
              return OPTIONAL_T.widen(typedNext.transformer);
            },
            OPTIONAL_T.widen(transformer));

    return new JavaOptionalContext<>(OPTIONAL_T.narrow(result), outerMonad);
  }

  /**
   * Chains a dependent computation using JavaOptionalContext-specific typing.
   *
   * <p>This is a convenience method that preserves the type in the signature.
   *
   * @param fn the function to apply, returning a new JavaOptionalContext; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context returned by the function, or an empty context
   * @throws NullPointerException if fn is null or returns null
   */
  public <B> JavaOptionalContext<F, B> flatMap(
      Function<? super A, ? extends JavaOptionalContext<F, B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");

    Kind<OptionalTKind.Witness<F>, B> result =
        optionalTMonad.flatMap(
            a -> {
              JavaOptionalContext<F, B> next = fn.apply(a);
              Objects.requireNonNull(next, "fn must not return null");
              return OPTIONAL_T.widen(next.transformer);
            },
            OPTIONAL_T.widen(transformer));

    return new JavaOptionalContext<>(OPTIONAL_T.narrow(result), outerMonad);
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
  public <B> JavaOptionalContext<F, B> then(
      Supplier<? extends JavaOptionalContext<F, B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return flatMap(ignored -> supplier.get());
  }

  // --- Recovery Operations ---

  /**
   * Provides a fallback value if this context is empty.
   *
   * <p>If this context is empty, the recovery function is applied to produce a value. If this
   * context contains a value, it is returned unchanged.
   *
   * @param recovery the function to produce a fallback value; must not be null
   * @return a context containing either the original value or the recovered value
   * @throws NullPointerException if recovery is null
   */
  public JavaOptionalContext<F, A> recover(Function<? super Unit, ? extends A> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");

    Kind<OptionalTKind.Witness<F>, A> result =
        optionalTMonad.handleErrorWith(
            OPTIONAL_T.widen(transformer), unit -> optionalTMonad.of(recovery.apply(unit)));

    return new JavaOptionalContext<>(OPTIONAL_T.narrow(result), outerMonad);
  }

  /**
   * Provides a fallback value if this context is empty.
   *
   * <p>This is a convenience method that ignores the Unit error type.
   *
   * @param defaultValue the default value to use if empty
   * @return a context containing either the original value or the default
   */
  public JavaOptionalContext<F, A> orElseValue(A defaultValue) {
    return recover(ignored -> defaultValue);
  }

  /**
   * Provides a fallback JavaOptionalContext if this one is empty.
   *
   * <p>If this context is empty, the recovery function is applied to produce an alternative
   * context. If this context contains a value, it is returned unchanged.
   *
   * @param recovery the function to produce a fallback context; must not be null
   * @return either this context (if non-empty) or the fallback context
   * @throws NullPointerException if recovery is null or returns null
   */
  public JavaOptionalContext<F, A> recoverWith(
      Function<? super Unit, ? extends JavaOptionalContext<F, A>> recovery) {
    Objects.requireNonNull(recovery, "recovery must not be null");

    Kind<OptionalTKind.Witness<F>, A> result =
        optionalTMonad.handleErrorWith(
            OPTIONAL_T.widen(transformer),
            unit -> {
              JavaOptionalContext<F, A> next = recovery.apply(unit);
              Objects.requireNonNull(next, "recovery must not return null");
              return OPTIONAL_T.widen(next.transformer);
            });

    return new JavaOptionalContext<>(OPTIONAL_T.narrow(result), outerMonad);
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
  public JavaOptionalContext<F, A> orElse(
      Supplier<? extends JavaOptionalContext<F, A>> alternative) {
    Objects.requireNonNull(alternative, "alternative must not be null");
    return recoverWith(ignored -> alternative.get());
  }

  // --- Conversion Methods ---

  /**
   * Converts this JavaOptionalContext to an ErrorContext with a typed error.
   *
   * <p>If this context is empty, the resulting ErrorContext will contain the provided error. If
   * this context contains a value, the ErrorContext will contain that value.
   *
   * <p>This method runs the underlying computation to check the result. For a deferred conversion,
   * use the escape hatch to OptionalT and convert manually.
   *
   * @param errorForEmpty the error to use if this context is empty
   * @param <E> the error type
   * @return an ErrorContext representing this context with typed errors
   * @throws ClassCastException if F is not IOKind.Witness
   */
  public <E> ErrorContext<IOKind.Witness, E, A> toErrorContext(E errorForEmpty) {
    Optional<A> result = runIO().unsafeRun();
    return result.isPresent()
        ? ErrorContext.success(result.get())
        : ErrorContext.failure(errorForEmpty);
  }

  /**
   * Converts this JavaOptionalContext to an OptionalContext using the library's Maybe type.
   *
   * <p>This method runs the underlying computation and converts the result to a Maybe-based
   * OptionalContext.
   *
   * @return an OptionalContext representing this context
   * @throws ClassCastException if F is not IOKind.Witness
   */
  public OptionalContext<IOKind.Witness, A> toOptionalContext() {
    Optional<A> result = runIO().unsafeRun();
    return result.isPresent() ? OptionalContext.some(result.get()) : OptionalContext.none();
  }

  // --- Execution Methods (IO-specific) ---

  /**
   * Runs the computation and returns an IOPath containing the Optional result.
   *
   * <p>This method is only available when F is IOKind.Witness.
   *
   * @return an IOPath that will produce the Optional when run
   * @throws ClassCastException if F is not IOKind.Witness
   */
  @SuppressWarnings("unchecked")
  public IOPath<Optional<A>> runIO() {
    Kind<F, Optional<A>> underlying = transformer.value();
    IO<Optional<A>> io = IO_OP.narrow((Kind<IOKind.Witness, Optional<A>>) underlying);
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
    Optional<A> result = runIO().unsafeRun();
    return result.orElse(defaultValue);
  }

  /**
   * Runs the computation and returns the value, throwing on empty.
   *
   * @return the contained value
   * @throws java.util.NoSuchElementException if the context is empty
   * @throws ClassCastException if F is not IOKind.Witness
   */
  public A runIOOrThrow() {
    Optional<A> result = runIO().unsafeRun();
    return result.orElseThrow();
  }

  // --- Escape Hatch ---

  /**
   * Returns the underlying OptionalT transformer.
   *
   * <p>This is an escape hatch to Layer 3 (raw transformers) for users who need full control over
   * the transformer operations.
   *
   * @return the underlying OptionalT transformer
   */
  public OptionalT<F, A> toOptionalT() {
    return transformer;
  }

  @Override
  public Kind<?, A> underlying() {
    return OPTIONAL_T.widen(transformer);
  }

  @Override
  public String toString() {
    return "JavaOptionalContext(" + transformer + ")";
  }
}
