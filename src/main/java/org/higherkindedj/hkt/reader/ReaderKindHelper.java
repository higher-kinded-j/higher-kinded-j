package org.higherkindedj.hkt.reader;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Helper class for working with {@link ReaderKind} HKT simulation. Provides static methods for
 * wrapping and unwrapping {@link Reader} instances.
 */
public final class ReaderKindHelper {

  // Error Messages
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Reader";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ReaderHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG =
      "ReaderHolder contained null Reader instance";

  private ReaderKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a ReaderKind back to the concrete {@code Reader<R, A>} type. Throws KindUnwrapException
   * if the Kind is null or not a valid ReaderHolder.
   *
   * @param <R> The type of the environment required by the Reader.
   * @param <A> The type of the value produced by the Reader.
   * @param kind The {@code Kind<ReaderKind<R, ?>, A>} instance to unwrap. Can be {@code @Nullable}.
   * @return The unwrapped, non-null {@code Reader<R, A>} instance. Returns {@code @NonNull}.
   * @throws KindUnwrapException if the input {@code kind} is null, not an instance of {@code
   *     ReaderHolder}, or (theoretically) if the holder contains a null reader instance.
   */
  @SuppressWarnings("unchecked")
  public static <R, A> @NonNull Reader<R, A> unwrap(@Nullable Kind<ReaderKind<R, ?>, A> kind) {

    // Use switch expression with pattern matching
    return switch (kind) {
      // Case 1: Input Kind is null
      case null -> throw new KindUnwrapException(ReaderKindHelper.INVALID_KIND_NULL_MSG);

      // Case 2: Input Kind is a ReaderHolder (record pattern extracts non-null reader)
      // The @NonNull contract on ReaderHolder.reader guarantees reader is not null here.
      case ReaderKindHelper.ReaderHolder<?, ?>(var reader) ->
          // Cast is safe because pattern matched and reader is known non-null.
          (Reader<R, A>) reader;

      // Case 3: Input Kind is non-null but not a ReaderHolder
      default ->
          throw new KindUnwrapException(
              ReaderKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@code Reader<R, A>} value into the ReaderKind Higher-Kinded-J type. Requires
   * a non-null Reader as input.
   *
   * @param <R> The type of the environment required by the Reader.
   * @param <A> The type of the value produced by the Reader.
   * @param reader The concrete {@code Reader<R, A>} instance to wrap. Must be {@code @NonNull}.
   * @return The {@code ReaderKind<R, A>} representation. Returns {@code @NonNull}.
   */
  public static <R, A> @NonNull ReaderKind<R, A> wrap(@NonNull Reader<R, A> reader) {
    Objects.requireNonNull(reader, "Input Reader cannot be null for wrap");
    return new ReaderHolder<>(reader);
  }

  /**
   * Creates a ReaderKind directly from a function {@code R -> A}. Wraps {@link
   * Reader#of(Function)}.
   *
   * @param <R> The type of the environment required by the Reader.
   * @param <A> The type of the value produced by the Reader.
   * @param runFunction The function defining the reader's computation. Must be {@code @NonNull}.
   * @return A {@code ReaderKind<R, A>} wrapping the created Reader. Returns {@code @NonNull}.
   * @throws NullPointerException if runFunction is null.
   */
  public static <R, A> @NonNull ReaderKind<R, A> reader(@NonNull Function<R, A> runFunction) {
    return wrap(Reader.of(runFunction));
  }

  /**
   * Creates a ReaderKind that ignores the environment and always returns the given constant value.
   * Wraps {@link Reader#constant(Object)}.
   *
   * @param <R> The type of the environment (ignored).
   * @param <A> The type of the constant value.
   * @param value The constant value to return. Can be {@code @Nullable}.
   * @return A {@code ReaderKind<R, A>} wrapping the constant Reader. Returns {@code @NonNull}.
   */
  public static <R, A> @NonNull ReaderKind<R, A> constant(@Nullable A value) {
    return wrap(Reader.constant(value));
  }

  /**
   * Creates a ReaderKind that simply returns the environment R as its result value. Wraps {@link
   * Reader#ask()}.
   *
   * @param <R> The type of the environment.
   * @return A {@code ReaderKind<R, R>} wrapping the ask Reader. Returns {@code @NonNull}.
   */
  public static <R> @NonNull ReaderKind<R, R> ask() {
    return wrap(Reader.ask());
  }

  /**
   * Runs the Reader computation held within the {@code Kind} wrapper using the provided
   * environment. Unwraps the Kind and calls {@link Reader#run(Object)}.
   *
   * @param <R> The type of the environment.
   * @param <A> The type of the value produced.
   * @param kind The {@code Kind<ReaderKind<R, ?>, A>} holding the Reader computation. Must be
   *     {@code @NonNull}.
   * @param environment The environment value to provide to the Reader. Must be {@code @NonNull}.
   * @return The result of the Reader computation. Can be {@code @Nullable} depending on A.
   * @throws KindUnwrapException if the input {@code kind} is invalid (null or wrong type).
   * @throws NullPointerException if environment is null (although Reader.run requires NonNull).
   */
  public static <R, A> @Nullable A runReader(
      @NonNull Kind<ReaderKind<R, ?>, A> kind, @NonNull R environment) {
    // `unwrap` throws KindUnwrapException if kind is invalid
    // `Reader.run` expects NonNull environment
    return unwrap(kind).run(environment);
  }

  // Internal holder record
  record ReaderHolder<R, A>(@NonNull Reader<R, A> reader) implements ReaderKind<R, A> {}
}
