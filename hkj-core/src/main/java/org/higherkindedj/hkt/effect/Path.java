// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
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
}
