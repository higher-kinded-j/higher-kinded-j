// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing static helper methods for working with {@link Reader} instances in the
 * context of higher-kinded types (HKT). It facilitates the conversion between the concrete {@link
 * Reader Reader&lt;R, A&gt;} type and its HKT representation, {@code Kind<ReaderKind.Witness<R>,
 * A>}.
 *
 * <p>This class is essential for bridging the gap between the concrete {@code Reader} monad and
 * generic functional programming abstractions that operate over {@link Kind} instances. It provides
 * methods for:
 *
 * <ul>
 *   <li>Wrapping a {@link Reader} into its {@link Kind} form ({@link #wrap(Reader)}).
 *   <li>Unwrapping a {@link Kind} back to a {@link Reader} ({@link #unwrap(Kind)}).
 *   <li>Convenience factory methods for creating {@link Reader} instances and returning them in
 *       their {@link Kind} form (e.g., {@link #reader(Function)}, {@link #constant(Object)}, {@link
 *       #ask()}).
 *   <li>Running a {@code Reader} that is currently in its {@link Kind} form ({@link
 *       #runReader(Kind, Object)}).
 * </ul>
 *
 * <p>The unwrapping mechanism relies on an internal private record, {@code ReaderHolder}, which
 * implements {@link ReaderKind} to encapsulate the actual {@code Reader} instance.
 *
 * @see Reader
 * @see ReaderKind
 * @see ReaderKind.Witness
 * @see Kind
 * @see KindUnwrapException
 */
public final class ReaderKindHelper {

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #unwrap(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Reader";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #unwrap(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ReaderHolder: ";

  /**
   * Error message for when the internal holder in {@link #unwrap(Kind)} contains a {@code null}
   * Reader instance. This should ideally not occur if {@link #wrap(Reader)} enforces non-null
   * Reader instances.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "ReaderHolder contained null Reader instance";

  private ReaderKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Internal record implementing {@link ReaderKind ReaderKind&lt;R, A&gt;} to hold the concrete
   * {@link Reader Reader&lt;R, A&gt;} instance. This is used by {@link #wrap(Reader)} and {@link
   * #unwrap(Kind)}. Since {@code ReaderKind<R, A>} extends {@code Kind<ReaderKind.Witness<R>, A>},
   * this holder effectively bridges between the concrete type and its HKT representation.
   *
   * @param <R> The environment type of the {@code Reader}.
   * @param <A> The value type of the {@code Reader}.
   * @param reader The non-null, actual {@link Reader Reader&lt;R, A&gt;} instance.
   */
  record ReaderHolder<R, A>(@NonNull Reader<R, A> reader) implements ReaderKind<R, A> {}

  /**
   * Unwraps a {@code Kind<ReaderKind.Witness<R>, A>} back to its concrete {@link Reader
   * Reader&lt;R, A&gt;} type.
   *
   * <p>This method performs runtime checks to ensure the provided {@link Kind} is valid and
   * actually represents a {@link Reader} computation.
   *
   * @param <R> The type of the environment required by the {@code Reader}.
   * @param <A> The type of the value produced by the {@code Reader}.
   * @param kind The {@code Kind<ReaderKind.Witness<R>, A>} instance to unwrap. May be {@code null}.
   * @return The underlying, non-null {@link Reader Reader&lt;R, A&gt;} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, not an instance of
   *     {@code ReaderHolder}, or if (theoretically) the holder contains a null reader instance.
   */
  @SuppressWarnings("unchecked")
  public static <R, A> @NonNull Reader<R, A> unwrap(@Nullable Kind<ReaderKind.Witness<R>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(ReaderKindHelper.INVALID_KIND_NULL_MSG);
      case ReaderKindHelper.ReaderHolder<?, ?> holder -> (Reader<R, A>) holder.reader();
      default ->
          throw new KindUnwrapException(
              ReaderKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link Reader Reader&lt;R, A&gt;} instance into its higher-kinded
   * representation, {@code ReaderKind<R, A>} (which is also a {@code Kind<ReaderKind.Witness<R>,
   * A>}).
   *
   * @param <R> The type of the environment required by the {@code Reader}.
   * @param <A> The type of the value produced by the {@code Reader}.
   * @param reader The concrete {@link Reader Reader&lt;R, A&gt;} instance to wrap. Must be
   *     {@code @NonNull}.
   * @return A non-null {@link ReaderKind ReaderKind&lt;R, A&gt;} representing the wrapped {@code
   *     Reader}.
   * @throws NullPointerException if {@code reader} is {@code null}.
   */
  public static <R, A> @NonNull ReaderKind<R, A> wrap(@NonNull Reader<R, A> reader) {
    Objects.requireNonNull(reader, "Input Reader cannot be null for wrap");
    return new ReaderHolder<>(reader);
  }

  /**
   * Creates a {@code Kind<ReaderKind.Witness<R>, A>} that wraps a {@link Reader} computation
   * defined by the given function {@code R -> A}.
   *
   * <p>This is a convenience factory method that delegates to {@link Reader#of(Function)} and then
   * wraps the result using {@link #wrap(Reader)}.
   *
   * @param <R> The type of the environment required by the {@code Reader}.
   * @param <A> The type of the value produced by the {@code Reader}.
   * @param runFunction The non-null function {@code (R -> A)} defining the reader's computation.
   * @return A new, non-null {@code Kind<ReaderKind.Witness<R>, A>} representing the {@code Reader}.
   * @throws NullPointerException if {@code runFunction} is {@code null}.
   */
  public static <R, A> @NonNull Kind<ReaderKind.Witness<R>, A> reader(
      @NonNull Function<R, A> runFunction) {
    return wrap(Reader.of(runFunction));
  }

  /**
   * Creates a {@code Kind<ReaderKind.Witness<R>, A>} that wraps a {@link Reader} which ignores the
   * environment and always returns the given constant {@code value}.
   *
   * <p>This delegates to {@link Reader#constant(Object)} and then wraps the result.
   *
   * @param <R> The type of the environment (which will be ignored by the {@code Reader}).
   * @param <A> The type of the constant value.
   * @param value The constant value to be returned by the {@code Reader}. Can be {@code @Nullable}
   *     if {@code A} is a nullable type.
   * @return A new, non-null {@code Kind<ReaderKind.Witness<R>, A>} representing the constant {@code
   *     Reader}.
   */
  public static <R, A> @NonNull Kind<ReaderKind.Witness<R>, A> constant(@Nullable A value) {
    return wrap(Reader.constant(value));
  }

  /**
   * Creates a {@code Kind<ReaderKind.Witness<R>, R>} that wraps a {@link Reader} which, when run,
   * simply returns the environment {@code R} itself.
   *
   * <p>This delegates to {@link Reader#ask()} and then wraps the result. This is a fundamental
   * operation for accessing the environment from within a {@code Reader} computation.
   *
   * @param <R> The type of the environment, which is also the type of the value produced.
   * @return A new, non-null {@code Kind<ReaderKind.Witness<R>, R>} representing the "ask" {@code
   *     Reader}.
   */
  public static <R> @NonNull Kind<ReaderKind.Witness<R>, R> ask() {
    return wrap(Reader.ask());
  }

  /**
   * Executes the {@link Reader} computation held within the {@link Kind} wrapper using the provided
   * {@code environment} and retrieves its result.
   *
   * <p>This method first unwraps the {@link Kind} to get the underlying {@link Reader Reader&lt;R,
   * A&gt;} and then calls {@link Reader#run(Object)} on it with the given {@code environment}.
   *
   * @param <R> The type of the environment.
   * @param <A> The type of the value produced by the {@code Reader} computation.
   * @param kind The non-null {@code Kind<ReaderKind.Witness<R>, A>} holding the {@code Reader}
   *     computation.
   * @param environment The non-null environment {@code R} to provide to the {@code Reader}.
   * @return The result of the {@code Reader} computation. Can be {@code @Nullable} if the
   *     computation defined within the {@code Reader} produces a {@code null} value.
   * @throws KindUnwrapException if the input {@code kind} is invalid (e.g., null or wrong type).
   * @throws NullPointerException if {@code environment} is {@code null} (as {@link
   *     Reader#run(Object)} typically expects a non-null environment).
   */
  public static <R, A> @Nullable A runReader(
      @NonNull Kind<ReaderKind.Witness<R>, A> kind, @NonNull R environment) {
    // unwrap will throw KindUnwrapException if kind is invalid.
    // Reader.run itself expects a non-null environment.
    return unwrap(kind).run(environment);
  }
}
