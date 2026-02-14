// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.context;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOMonad;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.higherkindedj.hkt.reader_t.ReaderTMonad;

/**
 * Effect context for dependency injection using {@link ReaderT}.
 *
 * <p>ConfigContext wraps {@link ReaderT} with a user-friendly API, hiding the complexity of
 * higher-kinded types while preserving the full capability of the transformer. It provides a clean
 * way to thread configuration or dependencies through a computation.
 *
 * <h2>Factory Methods</h2>
 *
 * <ul>
 *   <li>{@link #io(Function)} - Create from a function that uses the config
 *   <li>{@link #ioDeferred(Function)} - Create from a deferred computation using the config
 *   <li>{@link #ask()} - Get the config itself
 *   <li>{@link #pure(Object)} - Lift a value ignoring the config
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * record AppConfig(String apiUrl, int timeout) {}
 *
 * ConfigContext<IOKind.Witness, AppConfig, String> workflow =
 *     ConfigContext.<AppConfig>ask()
 *         .map(config -> "Connecting to: " + config.apiUrl())
 *         .via(msg -> ConfigContext.io(config ->
 *             fetchData(config.apiUrl(), config.timeout())));
 *
 * String result = workflow.runWithSync(new AppConfig("https://api.example.com", 30));
 * }</pre>
 *
 * @param <F> the underlying effect type witness (e.g., {@code IOKind.Witness})
 * @param <R> the configuration/environment type
 * @param <A> the value type
 */
public final class ConfigContext<F extends WitnessArity<TypeArity.Unary>, R, A>
    implements EffectContext<F, A> {

  private final ReaderT<F, R, A> transformer;
  private final Monad<F> outerMonad;
  private final ReaderTMonad<F, R> readerTMonad;

  private ConfigContext(ReaderT<F, R, A> transformer, Monad<F> outerMonad) {
    this.transformer = Objects.requireNonNull(transformer, "transformer must not be null");
    this.outerMonad = Objects.requireNonNull(outerMonad, "outerMonad must not be null");
    this.readerTMonad = new ReaderTMonad<>(outerMonad);
  }

  // --- Factory Methods for IO-based contexts ---

  /**
   * Creates a ConfigContext from a function that uses the configuration.
   *
   * <p>The function is applied to the configuration when the context is run.
   *
   * @param computation the function from config to value; must not be null
   * @param <R> the configuration type
   * @param <A> the value type
   * @return a new ConfigContext wrapping the computation
   * @throws NullPointerException if computation is null
   */
  public static <R, A> ConfigContext<IOKind.Witness, R, A> io(Function<R, A> computation) {
    Objects.requireNonNull(computation, "computation must not be null");

    ReaderT<IOKind.Witness, R, A> transformer = ReaderT.reader(IOMonad.INSTANCE, computation);
    return new ConfigContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a ConfigContext from a function that produces a deferred computation.
   *
   * <p>This allows the computation itself to be deferred until the IO is run.
   *
   * @param computation the function from config to a deferred value supplier; must not be null
   * @param <R> the configuration type
   * @param <A> the value type
   * @return a new ConfigContext wrapping the deferred computation
   * @throws NullPointerException if computation is null
   */
  public static <R, A> ConfigContext<IOKind.Witness, R, A> ioDeferred(
      Function<R, Supplier<A>> computation) {
    Objects.requireNonNull(computation, "computation must not be null");

    ReaderT<IOKind.Witness, R, A> transformer =
        ReaderT.of(r -> IO_OP.widen(IO.delay(computation.apply(r))));
    return new ConfigContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a ConfigContext that provides the configuration itself.
   *
   * <p>This is the "ask" operation from Reader monad, allowing access to the environment.
   *
   * @param <R> the configuration type
   * @return a new ConfigContext that yields the configuration
   */
  public static <R> ConfigContext<IOKind.Witness, R, R> ask() {
    ReaderT<IOKind.Witness, R, R> transformer = ReaderT.ask(IOMonad.INSTANCE);
    return new ConfigContext<>(transformer, IOMonad.INSTANCE);
  }

  /**
   * Creates a ConfigContext containing the given value, ignoring the configuration.
   *
   * @param value the value to contain
   * @param <R> the configuration type
   * @param <A> the value type
   * @return a new ConfigContext containing the value
   */
  public static <R, A> ConfigContext<IOKind.Witness, R, A> pure(A value) {
    ReaderT<IOKind.Witness, R, A> transformer = ReaderT.of(r -> IO_OP.widen(IO.delay(() -> value)));
    return new ConfigContext<>(transformer, IOMonad.INSTANCE);
  }

  // --- Chainable Operations ---

  @Override
  @SuppressWarnings("unchecked")
  public <B> ConfigContext<F, R, B> map(Function<? super A, ? extends B> mapper) {
    Objects.requireNonNull(mapper, "mapper must not be null");
    Kind<ReaderTKind.Witness<F, R>, B> result =
        readerTMonad.map(mapper, READER_T.widen(transformer));
    return new ConfigContext<>(READER_T.narrow(result), outerMonad);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <B> ConfigContext<F, R, B> via(Function<? super A, ? extends EffectContext<F, B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");

    Kind<ReaderTKind.Witness<F, R>, B> result =
        readerTMonad.flatMap(
            a -> {
              EffectContext<F, B> next = fn.apply(a);
              Objects.requireNonNull(next, "fn must not return null");
              if (!(next instanceof ConfigContext<?, ?, ?> nextCtx)) {
                throw new IllegalArgumentException(
                    "via function must return a ConfigContext, got: " + next.getClass().getName());
              }
              @SuppressWarnings("unchecked")
              ConfigContext<F, R, B> typedNext = (ConfigContext<F, R, B>) nextCtx;
              return READER_T.widen(typedNext.transformer);
            },
            READER_T.widen(transformer));

    return new ConfigContext<>(READER_T.narrow(result), outerMonad);
  }

  /**
   * Chains a dependent computation using ConfigContext-specific typing.
   *
   * <p>This is a convenience method that preserves the config type in the signature.
   *
   * @param fn the function to apply, returning a new ConfigContext; must not be null
   * @param <B> the type of the value in the returned context
   * @return the context returned by the function
   * @throws NullPointerException if fn is null or returns null
   */
  public <B> ConfigContext<F, R, B> flatMap(
      Function<? super A, ? extends ConfigContext<F, R, B>> fn) {
    Objects.requireNonNull(fn, "fn must not be null");

    Kind<ReaderTKind.Witness<F, R>, B> result =
        readerTMonad.flatMap(
            a -> {
              ConfigContext<F, R, B> next = fn.apply(a);
              Objects.requireNonNull(next, "fn must not return null");
              return READER_T.widen(next.transformer);
            },
            READER_T.widen(transformer));

    return new ConfigContext<>(READER_T.narrow(result), outerMonad);
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
  public <B> ConfigContext<F, R, B> then(Supplier<? extends ConfigContext<F, R, B>> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return flatMap(ignored -> supplier.get());
  }

  // --- Config-specific Operations ---

  /**
   * Adapts this context to work with a different configuration type.
   *
   * <p>The provided function converts from the new config type to the original, allowing this
   * context to be used in a computation with a different environment.
   *
   * @param f the function to convert configurations; must not be null
   * @param <R2> the new configuration type
   * @return a context that works with the new configuration type
   * @throws NullPointerException if f is null
   */
  public <R2> ConfigContext<F, R2, A> contramap(Function<R2, R> f) {
    Objects.requireNonNull(f, "f must not be null");

    ReaderT<F, R2, A> newTransformer = ReaderT.of(r2 -> transformer.run().apply(f.apply(r2)));
    return new ConfigContext<>(newTransformer, outerMonad);
  }

  /**
   * Modifies the configuration for this computation.
   *
   * <p>The modifier function is applied to the configuration before it's used. This is useful for
   * locally adjusting settings.
   *
   * @param modifier the function to modify the configuration; must not be null
   * @return a context that uses the modified configuration
   * @throws NullPointerException if modifier is null
   */
  public ConfigContext<F, R, A> local(UnaryOperator<R> modifier) {
    Objects.requireNonNull(modifier, "modifier must not be null");
    return contramap(modifier);
  }

  // --- Execution Methods (IO-specific) ---

  /**
   * Runs the computation with the given configuration, returning an IOPath.
   *
   * <p>This method is only available when F is IOKind.Witness.
   *
   * @param config the configuration to use
   * @return an IOPath that will produce the value when run
   * @throws ClassCastException if F is not IOKind.Witness
   */
  @SuppressWarnings("unchecked")
  public IOPath<A> runWith(R config) {
    Kind<F, A> result = transformer.run().apply(config);
    IO<A> io = IO_OP.narrow((Kind<IOKind.Witness, A>) result);
    return Path.ioPath(io);
  }

  /**
   * Runs the computation synchronously with the given configuration.
   *
   * @param config the configuration to use
   * @return the computed value
   * @throws ClassCastException if F is not IOKind.Witness
   */
  public A runWithSync(R config) {
    return runWith(config).unsafeRun();
  }

  // --- Escape Hatch ---

  /**
   * Returns the underlying ReaderT transformer.
   *
   * <p>This is an escape hatch to Layer 3 (raw transformers) for users who need full control over
   * the transformer operations.
   *
   * @return the underlying ReaderT transformer
   */
  public ReaderT<F, R, A> toReaderT() {
    return transformer;
  }

  @Override
  public Kind<?, A> underlying() {
    return READER_T.widen(transformer);
  }

  @Override
  public String toString() {
    return "ConfigContext(" + transformer + ")";
  }
}
