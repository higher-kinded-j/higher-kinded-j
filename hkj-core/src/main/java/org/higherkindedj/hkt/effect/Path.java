// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.spi.PathRegistry;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.writer.Writer;
import org.jspecify.annotations.Nullable;

/**
 * Factory class for creating Effect Path instances.
 *
 * <p>This is the primary entry point for the Effect Path API. It provides static factory methods
 * for creating path instances from raw values, existing effect types, or deferred computations.
 *
 * <h2>Creating MaybePath instances</h2>
 *
 * <pre>{@code
 * MaybePath<String> fromValue = Path.just("hello");
 * MaybePath<String> empty = Path.nothing();
 * MaybePath<String> fromNullable = Path.maybe(nullableValue);
 * MaybePath<User> fromMaybe = Path.maybe(userRepo.findById(id));
 * }</pre>
 *
 * <h2>Creating EitherPath instances</h2>
 *
 * <pre>{@code
 * EitherPath<Error, User> success = Path.right(user);
 * EitherPath<Error, User> failure = Path.left(Error.notFound());
 * EitherPath<Error, User> fromEither = Path.either(someEither);
 * }</pre>
 *
 * <h2>Creating TryPath instances</h2>
 *
 * <pre>{@code
 * TryPath<String> fromSupplier = Path.tryOf(() -> Files.readString(path));
 * TryPath<Integer> success = Path.success(42);
 * TryPath<Integer> failure = Path.failure(new IOException("Not found"));
 * TryPath<Config> fromTry = Path.tryPath(someTry);
 * }</pre>
 *
 * <h2>Creating IOPath instances</h2>
 *
 * <pre>{@code
 * IOPath<String> deferred = Path.io(() -> Files.readString(path));
 * IOPath<Unit> action = Path.ioRunnable(() -> System.out.println("Hello"));
 * IOPath<Integer> pure = Path.ioPure(42);
 * IOPath<Config> fromIO = Path.ioPath(someIO);
 * }</pre>
 *
 * <h2>Creating ValidationPath instances</h2>
 *
 * <pre>{@code
 * ValidationPath<List<Error>, User> success = Path.valid(user, Semigroups.list());
 * ValidationPath<List<Error>, User> failure = Path.invalid(errors, Semigroups.list());
 * ValidationPath<List<Error>, User> fromValidated = Path.validated(v, Semigroups.list());
 * }</pre>
 *
 * <h2>Creating IdPath instances</h2>
 *
 * <pre>{@code
 * IdPath<User> path = Path.id(user);
 * IdPath<User> fromId = Path.idPath(Id.of(user));
 * }</pre>
 *
 * <h2>Creating OptionalPath instances</h2>
 *
 * <pre>{@code
 * OptionalPath<User> path = Path.optional(Optional.of(user));
 * OptionalPath<User> present = Path.present(user);
 * OptionalPath<User> absent = Path.absent();
 * }</pre>
 *
 * <h2>Creating GenericPath instances</h2>
 *
 * <pre>{@code
 * GenericPath<CustomKind.Witness, User> path = Path.generic(kindValue, customMonad);
 * GenericPath<CustomKind.Witness, User> pure = Path.genericPure(user, customMonad);
 * }</pre>
 *
 * @see MaybePath
 * @see EitherPath
 * @see TryPath
 * @see IOPath
 * @see ValidationPath
 * @see IdPath
 * @see OptionalPath
 * @see GenericPath
 * @see ReaderPath
 * @see WithStatePath
 * @see WriterPath
 * @see LazyPath
 * @see CompletableFuturePath
 * @see NonDetPath
 * @see StreamPath
 * @see TrampolinePath
 * @see FreePath
 * @see FreeApPath
 */
public final class Path {

  private Path() {
    // Utility class - no instantiation
  }

  // ===== MaybePath factory methods =====

  /**
   * Creates a MaybePath containing the given non-null value.
   *
   * @param value the value to wrap; must not be null
   * @param <A> the type of the value
   * @return a MaybePath containing the value
   * @throws NullPointerException if value is null
   */
  public static <A> MaybePath<A> just(A value) {
    return new MaybePath<>(Maybe.just(value));
  }

  /**
   * Creates an empty MaybePath.
   *
   * @param <A> the phantom type of the absent value
   * @return an empty MaybePath
   */
  public static <A> MaybePath<A> nothing() {
    return new MaybePath<>(Maybe.nothing());
  }

  /**
   * Creates a MaybePath from a nullable value.
   *
   * <p>If the value is non-null, returns a MaybePath containing it. If the value is null, returns
   * an empty MaybePath.
   *
   * @param value the possibly-null value
   * @param <A> the type of the value
   * @return a MaybePath that is Just if value is non-null, Nothing otherwise
   */
  public static <A> MaybePath<A> maybe(@Nullable A value) {
    return new MaybePath<>(Maybe.fromNullable(value));
  }

  /**
   * Creates a MaybePath from an existing Maybe.
   *
   * @param maybe the Maybe to wrap; must not be null
   * @param <A> the type of the value
   * @return a MaybePath wrapping the Maybe
   * @throws NullPointerException if maybe is null
   */
  public static <A> MaybePath<A> maybe(Maybe<A> maybe) {
    Objects.requireNonNull(maybe, "maybe must not be null");
    return new MaybePath<>(maybe);
  }

  // ===== EitherPath factory methods =====

  /**
   * Creates an EitherPath containing a Right (success) value.
   *
   * @param value the success value
   * @param <E> the error type (phantom)
   * @param <A> the success type
   * @return an EitherPath containing Right
   */
  public static <E, A> EitherPath<E, A> right(@Nullable A value) {
    return new EitherPath<>(Either.right(value));
  }

  /**
   * Creates an EitherPath containing a Left (error) value.
   *
   * @param error the error value
   * @param <E> the error type
   * @param <A> the success type (phantom)
   * @return an EitherPath containing Left
   */
  public static <E, A> EitherPath<E, A> left(@Nullable E error) {
    return new EitherPath<>(Either.left(error));
  }

  /**
   * Creates an EitherPath from an existing Either.
   *
   * @param either the Either to wrap; must not be null
   * @param <E> the error type
   * @param <A> the success type
   * @return an EitherPath wrapping the Either
   * @throws NullPointerException if either is null
   */
  public static <E, A> EitherPath<E, A> either(Either<E, A> either) {
    Objects.requireNonNull(either, "either must not be null");
    return new EitherPath<>(either);
  }

  // ===== TryPath factory methods =====

  /**
   * Creates a TryPath by executing the given supplier.
   *
   * <p>If the supplier completes normally, returns a success TryPath. If the supplier throws, the
   * exception is captured in a failure TryPath.
   *
   * @param supplier the computation to execute; must not be null
   * @param <A> the type of the result
   * @return a TryPath containing the result or exception
   * @throws NullPointerException if supplier is null
   */
  public static <A> TryPath<A> tryOf(Supplier<? extends A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new TryPath<>(Try.of(supplier));
  }

  /**
   * Creates a successful TryPath containing the given value.
   *
   * @param value the success value
   * @param <A> the type of the value
   * @return a success TryPath
   */
  public static <A> TryPath<A> success(@Nullable A value) {
    return new TryPath<>(Try.success(value));
  }

  /**
   * Creates a failure TryPath containing the given exception.
   *
   * @param exception the exception; must not be null
   * @param <A> the phantom type of the success value
   * @return a failure TryPath
   * @throws NullPointerException if exception is null
   */
  public static <A> TryPath<A> failure(Throwable exception) {
    Objects.requireNonNull(exception, "exception must not be null");
    return new TryPath<>(Try.failure(exception));
  }

  /**
   * Creates a TryPath from an existing Try.
   *
   * @param tryValue the Try to wrap; must not be null
   * @param <A> the type of the value
   * @return a TryPath wrapping the Try
   * @throws NullPointerException if tryValue is null
   */
  public static <A> TryPath<A> tryPath(Try<A> tryValue) {
    Objects.requireNonNull(tryValue, "tryValue must not be null");
    return new TryPath<>(tryValue);
  }

  // ===== IOPath factory methods =====

  /**
   * Creates an IOPath with a deferred computation.
   *
   * <p>The supplier is not executed until {@link IOPath#unsafeRun()} or {@link IOPath#runSafe()} is
   * called.
   *
   * @param supplier the computation to defer; must not be null
   * @param <A> the type of the result
   * @return an IOPath representing the deferred computation
   * @throws NullPointerException if supplier is null
   */
  public static <A> IOPath<A> io(Supplier<A> supplier) {
    Objects.requireNonNull(supplier, "supplier must not be null");
    return new IOPath<>(IO.delay(supplier));
  }

  /**
   * Creates an IOPath that executes a side-effecting runnable.
   *
   * <p>The runnable is not executed until {@link IOPath#unsafeRun()} or {@link IOPath#runSafe()} is
   * called.
   *
   * @param runnable the side effect to defer; must not be null
   * @return an IOPath that produces Unit when run
   * @throws NullPointerException if runnable is null
   */
  public static IOPath<Unit> ioRunnable(Runnable runnable) {
    Objects.requireNonNull(runnable, "runnable must not be null");
    return new IOPath<>(IO.fromRunnable(runnable));
  }

  /**
   * Creates an IOPath containing a pure value.
   *
   * <p>The value is already computed; no side effects occur when this IOPath is run.
   *
   * @param value the value to wrap
   * @param <A> the type of the value
   * @return an IOPath that immediately produces the value when run
   */
  public static <A> IOPath<A> ioPure(A value) {
    return new IOPath<>(IO.delay(() -> value));
  }

  /**
   * Creates an IOPath from an existing IO.
   *
   * @param io the IO to wrap; must not be null
   * @param <A> the type of the value
   * @return an IOPath wrapping the IO
   * @throws NullPointerException if io is null
   */
  public static <A> IOPath<A> ioPath(IO<A> io) {
    Objects.requireNonNull(io, "io must not be null");
    return new IOPath<>(io);
  }

  // ===== ValidationPath factory methods =====

  /**
   * Creates a valid ValidationPath containing the given value.
   *
   * @param value the success value
   * @param semigroup the Semigroup for error accumulation; must not be null
   * @param <E> the error type
   * @param <A> the value type
   * @return a valid ValidationPath
   * @throws NullPointerException if semigroup is null
   */
  public static <E, A> ValidationPath<E, A> valid(A value, Semigroup<E> semigroup) {
    Objects.requireNonNull(semigroup, "semigroup must not be null");
    return new ValidationPath<>(Validated.valid(value), semigroup);
  }

  /**
   * Creates an invalid ValidationPath containing the given error.
   *
   * @param error the error value; must not be null
   * @param semigroup the Semigroup for error accumulation; must not be null
   * @param <E> the error type
   * @param <A> the phantom type of the success value
   * @return an invalid ValidationPath
   * @throws NullPointerException if error or semigroup is null
   */
  public static <E, A> ValidationPath<E, A> invalid(E error, Semigroup<E> semigroup) {
    Objects.requireNonNull(error, "error must not be null");
    Objects.requireNonNull(semigroup, "semigroup must not be null");
    return new ValidationPath<>(Validated.invalid(error), semigroup);
  }

  /**
   * Creates a ValidationPath from an existing Validated.
   *
   * @param validated the Validated to wrap; must not be null
   * @param semigroup the Semigroup for error accumulation; must not be null
   * @param <E> the error type
   * @param <A> the value type
   * @return a ValidationPath wrapping the Validated
   * @throws NullPointerException if validated or semigroup is null
   */
  public static <E, A> ValidationPath<E, A> validated(
      Validated<E, A> validated, Semigroup<E> semigroup) {
    Objects.requireNonNull(validated, "validated must not be null");
    Objects.requireNonNull(semigroup, "semigroup must not be null");
    return new ValidationPath<>(validated, semigroup);
  }

  // ===== IdPath factory methods =====

  /**
   * Creates an IdPath containing the given value.
   *
   * @param value the value to wrap
   * @param <A> the type of the value
   * @return an IdPath containing the value
   */
  public static <A> IdPath<A> id(@Nullable A value) {
    return new IdPath<>(Id.of(value));
  }

  /**
   * Creates an IdPath from an existing Id.
   *
   * @param id the Id to wrap; must not be null
   * @param <A> the type of the value
   * @return an IdPath wrapping the Id
   * @throws NullPointerException if id is null
   */
  public static <A> IdPath<A> idPath(Id<A> id) {
    Objects.requireNonNull(id, "id must not be null");
    return new IdPath<>(id);
  }

  // ===== OptionalPath factory methods =====

  /**
   * Creates an OptionalPath from a java.util.Optional.
   *
   * @param optional the Optional to wrap; must not be null
   * @param <A> the type of the value
   * @return an OptionalPath wrapping the Optional
   * @throws NullPointerException if optional is null
   */
  public static <A> OptionalPath<A> optional(Optional<A> optional) {
    Objects.requireNonNull(optional, "optional must not be null");
    return new OptionalPath<>(optional);
  }

  /**
   * Creates an OptionalPath containing the given non-null value.
   *
   * @param value the value to wrap; must not be null
   * @param <A> the type of the value
   * @return an OptionalPath containing the value
   * @throws NullPointerException if value is null
   */
  public static <A> OptionalPath<A> present(A value) {
    Objects.requireNonNull(value, "value must not be null");
    return new OptionalPath<>(Optional.of(value));
  }

  /**
   * Creates an empty OptionalPath.
   *
   * @param <A> the phantom type of the absent value
   * @return an empty OptionalPath
   */
  public static <A> OptionalPath<A> absent() {
    return new OptionalPath<>(Optional.empty());
  }

  // ===== GenericPath factory methods =====

  /**
   * Creates a GenericPath from a Kind and Monad instance.
   *
   * <p>This is the escape hatch for using custom monads in Path composition.
   *
   * @param value the Kind to wrap; must not be null
   * @param monad the Monad instance; must not be null
   * @param <F> the witness type
   * @param <A> the value type
   * @return a GenericPath wrapping the Kind
   * @throws NullPointerException if value or monad is null
   */
  public static <F, A> GenericPath<F, A> generic(Kind<F, A> value, Monad<F> monad) {
    return GenericPath.of(value, monad);
  }

  /**
   * Creates a GenericPath by lifting a pure value using the Monad's {@code of} method.
   *
   * @param value the value to lift
   * @param monad the Monad instance; must not be null
   * @param <F> the witness type
   * @param <A> the value type
   * @return a GenericPath containing the lifted value
   * @throws NullPointerException if monad is null
   */
  public static <F, A> GenericPath<F, A> genericPure(A value, Monad<F> monad) {
    return GenericPath.pure(value, monad);
  }

  // ===== ReaderPath factory methods =====

  /**
   * Creates a ReaderPath from an existing Reader.
   *
   * @param reader the Reader to wrap; must not be null
   * @param <R> the environment type
   * @param <A> the value type
   * @return a ReaderPath wrapping the Reader
   * @throws NullPointerException if reader is null
   */
  public static <R, A> ReaderPath<R, A> reader(Reader<R, A> reader) {
    Objects.requireNonNull(reader, "reader must not be null");
    return new ReaderPath<>(reader);
  }

  /**
   * Creates a ReaderPath that returns a pure value, ignoring the environment.
   *
   * @param value the value to return
   * @param <R> the environment type (phantom)
   * @param <A> the value type
   * @return a ReaderPath that always returns the given value
   */
  public static <R, A> ReaderPath<R, A> readerPure(A value) {
    return ReaderPath.pure(value);
  }

  /**
   * Creates a ReaderPath that returns the entire environment.
   *
   * @param <R> the environment type
   * @return a ReaderPath that returns the environment
   */
  public static <R> ReaderPath<R, R> ask() {
    return ReaderPath.ask();
  }

  /**
   * Creates a ReaderPath that extracts a value from the environment.
   *
   * @param f the function to extract from the environment; must not be null
   * @param <R> the environment type
   * @param <A> the extracted value type
   * @return a ReaderPath that extracts from the environment
   * @throws NullPointerException if f is null
   */
  public static <R, A> ReaderPath<R, A> asks(Function<? super R, ? extends A> f) {
    return ReaderPath.asks(f);
  }

  // ===== WithStatePath factory methods =====

  /**
   * Creates a WithStatePath from an existing State.
   *
   * @param state the State to wrap; must not be null
   * @param <S> the state type
   * @param <A> the value type
   * @return a WithStatePath wrapping the State
   * @throws NullPointerException if state is null
   */
  public static <S, A> WithStatePath<S, A> state(State<S, A> state) {
    Objects.requireNonNull(state, "state must not be null");
    return new WithStatePath<>(state);
  }

  /**
   * Creates a WithStatePath that returns a pure value without modifying state.
   *
   * @param value the value to return
   * @param <S> the state type
   * @param <A> the value type
   * @return a WithStatePath that always returns the given value
   */
  public static <S, A> WithStatePath<S, A> statePure(A value) {
    return WithStatePath.pure(value);
  }

  /**
   * Creates a WithStatePath that returns the current state.
   *
   * @param <S> the state type
   * @return a WithStatePath that returns the state
   */
  public static <S> WithStatePath<S, S> getState() {
    return WithStatePath.get();
  }

  /**
   * Creates a WithStatePath that sets the state.
   *
   * @param newState the new state; must not be null
   * @param <S> the state type
   * @return a WithStatePath that sets the state and returns Unit
   * @throws NullPointerException if newState is null
   */
  public static <S> WithStatePath<S, Unit> setState(S newState) {
    return WithStatePath.set(newState);
  }

  /**
   * Creates a WithStatePath that modifies the state.
   *
   * @param f the function to modify state; must not be null
   * @param <S> the state type
   * @return a WithStatePath that modifies state and returns Unit
   * @throws NullPointerException if f is null
   */
  public static <S> WithStatePath<S, Unit> modifyState(UnaryOperator<S> f) {
    return WithStatePath.modify(f);
  }

  // ===== WriterPath factory methods =====

  /**
   * Creates a WriterPath from an existing Writer.
   *
   * @param writer the Writer to wrap; must not be null
   * @param monoid the Monoid for log accumulation; must not be null
   * @param <W> the log type
   * @param <A> the value type
   * @return a WriterPath wrapping the Writer
   * @throws NullPointerException if writer or monoid is null
   */
  public static <W, A> WriterPath<W, A> writer(Writer<W, A> writer, Monoid<W> monoid) {
    Objects.requireNonNull(writer, "writer must not be null");
    Objects.requireNonNull(monoid, "monoid must not be null");
    return new WriterPath<>(writer, monoid);
  }

  /**
   * Creates a WriterPath with a pure value and empty log.
   *
   * @param value the value to return
   * @param monoid the Monoid for log accumulation; must not be null
   * @param <W> the log type
   * @param <A> the value type
   * @return a WriterPath with empty log
   * @throws NullPointerException if monoid is null
   */
  public static <W, A> WriterPath<W, A> writerPure(A value, Monoid<W> monoid) {
    return WriterPath.pure(value, monoid);
  }

  /**
   * Creates a WriterPath that only produces log output.
   *
   * @param log the log to produce; must not be null
   * @param monoid the Monoid for log accumulation; must not be null
   * @param <W> the log type
   * @return a WriterPath that produces log and returns Unit
   * @throws NullPointerException if log or monoid is null
   */
  public static <W> WriterPath<W, Unit> tell(W log, Monoid<W> monoid) {
    return WriterPath.tell(log, monoid);
  }

  // ===== LazyPath factory methods =====

  /**
   * Creates a LazyPath from an existing Lazy.
   *
   * @param lazy the Lazy to wrap; must not be null
   * @param <A> the value type
   * @return a LazyPath wrapping the Lazy
   * @throws NullPointerException if lazy is null
   */
  public static <A> LazyPath<A> lazy(Lazy<A> lazy) {
    Objects.requireNonNull(lazy, "lazy must not be null");
    return new LazyPath<>(lazy);
  }

  /**
   * Creates an already-evaluated LazyPath.
   *
   * @param value the value
   * @param <A> the value type
   * @return a LazyPath holding the value
   */
  public static <A> LazyPath<A> lazyNow(A value) {
    return LazyPath.now(value);
  }

  /**
   * Creates a LazyPath that defers computation.
   *
   * @param supplier the computation to defer; must not be null
   * @param <A> the value type
   * @return a LazyPath that defers computation
   * @throws NullPointerException if supplier is null
   */
  public static <A> LazyPath<A> lazyDefer(Supplier<? extends A> supplier) {
    return LazyPath.defer(supplier);
  }

  // ===== CompletableFuturePath factory methods =====

  /**
   * Creates a CompletableFuturePath from an existing CompletableFuture.
   *
   * @param future the CompletableFuture to wrap; must not be null
   * @param <A> the value type
   * @return a CompletableFuturePath wrapping the future
   * @throws NullPointerException if future is null
   */
  public static <A> CompletableFuturePath<A> future(CompletableFuture<A> future) {
    return CompletableFuturePath.fromFuture(future);
  }

  /**
   * Creates an already-completed CompletableFuturePath.
   *
   * @param value the completed value
   * @param <A> the value type
   * @return a completed CompletableFuturePath
   */
  public static <A> CompletableFuturePath<A> futureCompleted(A value) {
    return CompletableFuturePath.completed(value);
  }

  /**
   * Creates a failed CompletableFuturePath.
   *
   * @param exception the exception; must not be null
   * @param <A> the value type
   * @return a failed CompletableFuturePath
   * @throws NullPointerException if exception is null
   */
  public static <A> CompletableFuturePath<A> futureFailed(Exception exception) {
    return CompletableFuturePath.failed(exception);
  }

  /**
   * Creates a CompletableFuturePath from an async computation.
   *
   * @param supplier the async computation; must not be null
   * @param <A> the value type
   * @return a CompletableFuturePath running asynchronously
   * @throws NullPointerException if supplier is null
   */
  public static <A> CompletableFuturePath<A> futureAsync(Supplier<A> supplier) {
    return CompletableFuturePath.supplyAsync(supplier);
  }

  // ===== NonDetPath factory methods =====

  /**
   * Creates a NonDetPath from an existing List.
   *
   * @param list the list to wrap; must not be null
   * @param <A> the element type
   * @return a NonDetPath wrapping the list
   * @throws NullPointerException if list is null
   */
  public static <A> NonDetPath<A> list(List<A> list) {
    return NonDetPath.of(list);
  }

  /**
   * Creates a NonDetPath from varargs.
   *
   * @param elements the elements
   * @param <A> the element type
   * @return a NonDetPath containing the elements
   */
  @SafeVarargs
  public static <A> NonDetPath<A> list(A... elements) {
    return NonDetPath.of(elements);
  }

  /**
   * Creates a NonDetPath with a single element.
   *
   * @param value the single element
   * @param <A> the element type
   * @return a NonDetPath containing one element
   */
  public static <A> NonDetPath<A> listPure(A value) {
    return NonDetPath.pure(value);
  }

  /**
   * Creates an empty NonDetPath.
   *
   * @param <A> the element type
   * @return an empty NonDetPath
   */
  public static <A> NonDetPath<A> listEmpty() {
    return NonDetPath.empty();
  }

  // ===== ListPath factory methods =====

  /**
   * Creates a ListPath from a List.
   *
   * <p>ListPath uses positional zipWith semantics (pairs corresponding elements), unlike NonDetPath
   * which uses Cartesian product semantics.
   *
   * @param list the list to wrap; must not be null
   * @param <A> the element type
   * @return a ListPath wrapping the list
   * @throws NullPointerException if list is null
   */
  public static <A> ListPath<A> listPath(List<A> list) {
    return ListPath.of(list);
  }

  /**
   * Creates a ListPath from varargs.
   *
   * @param elements the elements
   * @param <A> the element type
   * @return a ListPath containing the elements
   */
  @SafeVarargs
  public static <A> ListPath<A> listPath(A... elements) {
    return ListPath.of(elements);
  }

  /**
   * Creates a ListPath with a single element.
   *
   * @param value the single element
   * @param <A> the element type
   * @return a ListPath containing one element
   */
  public static <A> ListPath<A> listPathPure(A value) {
    return ListPath.pure(value);
  }

  /**
   * Creates an empty ListPath.
   *
   * @param <A> the element type
   * @return an empty ListPath
   */
  public static <A> ListPath<A> listPathEmpty() {
    return ListPath.empty();
  }

  /**
   * Creates a ListPath from a range of integers.
   *
   * @param startInclusive the start value (inclusive)
   * @param endExclusive the end value (exclusive)
   * @return a ListPath containing integers in the range
   */
  public static ListPath<Integer> listPathRange(int startInclusive, int endExclusive) {
    return ListPath.range(startInclusive, endExclusive);
  }

  // ===== StreamPath factory methods =====

  /**
   * Creates a StreamPath from an existing Stream.
   *
   * <p>Note: The stream is materialized for reusability.
   *
   * @param stream the stream to wrap; must not be null
   * @param <A> the element type
   * @return a StreamPath wrapping the stream
   * @throws NullPointerException if stream is null
   */
  public static <A> StreamPath<A> stream(Stream<A> stream) {
    return StreamPath.of(stream);
  }

  /**
   * Creates a StreamPath from a List.
   *
   * @param list the list to stream; must not be null
   * @param <A> the element type
   * @return a StreamPath streaming the list
   * @throws NullPointerException if list is null
   */
  public static <A> StreamPath<A> streamFromList(List<A> list) {
    return StreamPath.fromList(list);
  }

  /**
   * Creates a StreamPath with a single element.
   *
   * @param value the single element
   * @param <A> the element type
   * @return a StreamPath containing one element
   */
  public static <A> StreamPath<A> streamPure(A value) {
    return StreamPath.pure(value);
  }

  /**
   * Creates an empty StreamPath.
   *
   * @param <A> the element type
   * @return an empty StreamPath
   */
  public static <A> StreamPath<A> streamEmpty() {
    return StreamPath.empty();
  }

  /**
   * Creates an infinite StreamPath by iteration.
   *
   * <p><b>Warning:</b> Use {@code take()} to limit infinite streams.
   *
   * @param seed the initial value
   * @param f the iteration function; must not be null
   * @param <A> the element type
   * @return an infinite StreamPath
   * @throws NullPointerException if f is null
   */
  public static <A> StreamPath<A> streamIterate(A seed, UnaryOperator<A> f) {
    return StreamPath.iterate(seed, f);
  }

  // ===== PathRegistry integration =====

  /**
   * Creates a Path from any registered Kind type using the PathRegistry.
   *
   * <p>This method looks up a {@link org.higherkindedj.hkt.effect.spi.PathProvider} for the given
   * witness type and uses it to create an appropriate Path wrapper.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // With a registered custom effect type
   * Kind<ApiResultKind.Witness, User> kind = apiService.getUser(id);
   * Optional<Chainable<User>> path = Path.from(kind, ApiResultKind.Witness.class);
   * }</pre>
   *
   * @param kind the Kind value to wrap; must not be null
   * @param witnessType the witness type class
   * @param <F> the witness type
   * @param <A> the value type
   * @return an Optional containing the path if a provider is registered
   * @throws NullPointerException if kind or witnessType is null
   * @see org.higherkindedj.hkt.effect.spi.PathRegistry
   * @see org.higherkindedj.hkt.effect.spi.PathProvider
   */
  public static <F, A> Optional<Chainable<A>> from(Kind<F, A> kind, Class<?> witnessType) {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(witnessType, "witnessType must not be null");
    return PathRegistry.createPath(kind, witnessType);
  }

  // ===== TrampolinePath factory methods =====

  /**
   * Creates a TrampolinePath with an immediate value.
   *
   * @param value the completed value
   * @param <A> the value type
   * @return a TrampolinePath containing the value
   */
  public static <A> TrampolinePath<A> trampolineDone(A value) {
    return TrampolinePath.done(value);
  }

  /**
   * Creates a TrampolinePath with a deferred computation.
   *
   * <p>This is the key method for achieving stack safety. Instead of making a recursive call
   * directly, wrap the call in {@code trampolineDefer}.
   *
   * @param supplier supplies the next TrampolinePath step; must not be null
   * @param <A> the value type
   * @return a TrampolinePath representing the deferred computation
   * @throws NullPointerException if supplier is null
   */
  public static <A> TrampolinePath<A> trampolineDefer(Supplier<TrampolinePath<A>> supplier) {
    return TrampolinePath.defer(supplier);
  }

  /**
   * Creates a TrampolinePath from an existing Trampoline.
   *
   * @param trampoline the Trampoline to wrap; must not be null
   * @param <A> the value type
   * @return a TrampolinePath wrapping the Trampoline
   * @throws NullPointerException if trampoline is null
   */
  public static <A> TrampolinePath<A> trampoline(Trampoline<A> trampoline) {
    return TrampolinePath.of(trampoline);
  }

  // ===== FreePath factory methods =====

  /**
   * Creates a FreePath containing a pure value.
   *
   * @param value the value to wrap
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the value type
   * @return a FreePath containing the value
   * @throws NullPointerException if functor is null
   */
  public static <F, A> FreePath<F, A> freePure(A value, Functor<F> functor) {
    return FreePath.pure(value, functor);
  }

  /**
   * Lifts a functor value into FreePath.
   *
   * @param fa the functor value to lift; must not be null
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the result type
   * @return a FreePath containing the lifted instruction
   * @throws NullPointerException if fa or functor is null
   */
  public static <F, A> FreePath<F, A> freeLift(Kind<F, A> fa, Functor<F> functor) {
    return FreePath.liftF(fa, functor);
  }

  /**
   * Creates a FreePath from an existing Free monad.
   *
   * @param free the Free monad to wrap; must not be null
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the value type
   * @return a FreePath wrapping the Free monad
   * @throws NullPointerException if free or functor is null
   */
  public static <F, A> FreePath<F, A> free(Free<F, A> free, Functor<F> functor) {
    return FreePath.of(free, functor);
  }

  // ===== FreeApPath factory methods =====

  /**
   * Creates a FreeApPath containing a pure value.
   *
   * @param value the value to wrap
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the value type
   * @return a FreeApPath containing the value
   * @throws NullPointerException if functor is null
   */
  public static <F, A> FreeApPath<F, A> freeApPure(A value, Functor<F> functor) {
    return FreeApPath.pure(value, functor);
  }

  /**
   * Lifts a functor value into FreeApPath.
   *
   * @param fa the functor value to lift; must not be null
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the result type
   * @return a FreeApPath containing the lifted instruction
   * @throws NullPointerException if fa or functor is null
   */
  public static <F, A> FreeApPath<F, A> freeApLift(Kind<F, A> fa, Functor<F> functor) {
    return FreeApPath.liftF(fa, functor);
  }

  /**
   * Creates a FreeApPath from an existing FreeAp.
   *
   * @param freeAp the FreeAp to wrap; must not be null
   * @param functor the Functor instance for F; must not be null
   * @param <F> the functor witness type
   * @param <A> the value type
   * @return a FreeApPath wrapping the FreeAp
   * @throws NullPointerException if freeAp or functor is null
   */
  public static <F, A> FreeApPath<F, A> freeAp(FreeAp<F, A> freeAp, Functor<F> functor) {
    return FreeApPath.of(freeAp, functor);
  }
}
