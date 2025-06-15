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
 * Enum implementing {@link ReaderConverterOps} for widen/narrow operations, and providing
 * additional factory and utility instance methods for {@link Reader} types.
 *
 * <p>Access these operations via the singleton {@code READER}. For example: {@code
 * ReaderKindHelper.READER.widen(Reader.ask());}
 */
public enum ReaderKindHelper implements ReaderConverterOps {
  READER;

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #narrow(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for Reader";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #narrow(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a ReaderHolder: ";

  public static final String INVALID_KIND_TYPE_NULL_MSG = "Input Reader cannot be null for widen";

  /**
   * Error message for when the internal holder in {@link #narrow(Kind)} contains a {@code null}
   * Reader instance. This should ideally not occur if {@link #widen(Reader)} enforces non-null
   * Reader instances and ReaderHolder guarantees its content.
   */
  public static final String INVALID_HOLDER_STATE_MSG =
      "ReaderHolder contained null Reader instance";

  /**
   * Internal record implementing {@link ReaderKind ReaderKind&lt;R, A&gt;} to hold the concrete
   * {@link Reader Reader&lt;R, A&gt;} instance. Changed to package-private for potential test
   * access.
   *
   * @param <R> The environment type of the {@code Reader}.
   * @param <A> The value type of the {@code Reader}.
   * @param reader The non-null, actual {@link Reader Reader&lt;R, A&gt;} instance.
   */
  record ReaderHolder<R, A>(@NonNull Reader<R, A> reader) implements ReaderKind<R, A> {}

  /**
   * Widens a concrete {@link Reader Reader&lt;R, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<ReaderKind.Witness<R>, A>}. Implements {@link
   * ReaderConverterOps#widen}.
   *
   * @param <R> The type of the environment required by the {@code Reader}.
   * @param <A> The type of the value produced by the {@code Reader}.
   * @param reader The concrete {@link Reader Reader&lt;R, A&gt;} instance to widen. Must be
   *     non-null.
   * @return A non-null {@code Kind<ReaderKind.Witness<R>, A>} representing the wrapped {@code
   *     Reader}.
   * @throws NullPointerException if {@code reader} is {@code null}.
   */
  @Override
  public <R, A> @NonNull Kind<ReaderKind.Witness<R>, A> widen(@NonNull Reader<R, A> reader) {
    Objects.requireNonNull(reader, INVALID_KIND_TYPE_NULL_MSG);
    return new ReaderHolder<>(reader);
  }

  /**
   * Narrows a {@code Kind<ReaderKind.Witness<R>, A>} back to its concrete {@link Reader
   * Reader&lt;R, A&gt;} type. Implements {@link ReaderConverterOps#narrow}.
   *
   * @param <R> The type of the environment required by the {@code Reader}.
   * @param <A> The type of the value produced by the {@code Reader}.
   * @param kind The {@code Kind<ReaderKind.Witness<R>, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Reader Reader&lt;R, A&gt;} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, or not an instance of
   *     {@code ReaderHolder}. The {@code ReaderHolder} guarantees its internal {@code reader} is
   *     non-null.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <R, A> @NonNull Reader<R, A> narrow(@Nullable Kind<ReaderKind.Witness<R>, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(ReaderKindHelper.INVALID_KIND_NULL_MSG);
      // ReaderHolder's record component 'reader' is @NonNull.
      case ReaderKindHelper.ReaderHolder<?, ?> holder -> (Reader<R, A>) holder.reader();
      default ->
          throw new KindUnwrapException(
              ReaderKindHelper.INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Creates a {@code Kind<ReaderKind.Witness<R>, A>} that wraps a {@link Reader} computation
   * defined by the given function {@code R -> A}.
   *
   * @param <R> The type of the environment required by the {@code Reader}.
   * @param <A> The type of the value produced by the {@code Reader}.
   * @param runFunction The non-null function {@code (R -> A)} defining the reader's computation.
   * @return A new, non-null {@code Kind<ReaderKind.Witness<R>, A>} representing the {@code Reader}.
   */
  public <R, A> @NonNull Kind<ReaderKind.Witness<R>, A> reader(
      @NonNull Function<R, A> runFunction) {
    return this.widen(Reader.of(runFunction));
  }

  /**
   * Creates a {@code Kind<ReaderKind.Witness<R>, A>} that wraps a {@link Reader} which ignores the
   * environment and always returns the given constant {@code value}.
   *
   * @param <R> The type of the environment (which will be ignored by the {@code Reader}).
   * @param <A> The type of the constant value.
   * @param value The constant value to be returned by the {@code Reader}.
   * @return A new, non-null {@code Kind<ReaderKind.Witness<R>, A>} representing the constant {@code
   *     Reader}.
   */
  public <R, A> @NonNull Kind<ReaderKind.Witness<R>, A> constant(@Nullable A value) {
    return this.widen(Reader.constant(value));
  }

  /**
   * Creates a {@code Kind<ReaderKind.Witness<R>, R>} that wraps a {@link Reader} which, when run,
   * simply returns the environment {@code R} itself.
   *
   * @param <R> The type of the environment, which is also the type of the value produced.
   * @return A new, non-null {@code Kind<ReaderKind.Witness<R>, R>} representing the "ask" {@code
   *     Reader}.
   */
  public <R> @NonNull Kind<ReaderKind.Witness<R>, R> ask() {
    return this.widen(Reader.ask());
  }

  /**
   * Executes the {@link Reader} computation held within the {@link Kind} wrapper using the provided
   * {@code environment} and retrieves its result.
   *
   * @param <R> The type of the environment.
   * @param <A> The type of the value produced by the {@code Reader} computation.
   * @param kind The non-null {@code Kind<ReaderKind.Witness<R>, A>} holding the {@code Reader}
   *     computation.
   * @param environment The non-null environment {@code R} to provide to the {@code Reader}.
   * @return The result of the {@code Reader} computation.
   * @throws KindUnwrapException if the input {@code kind} is invalid.
   * @throws NullPointerException if {@code environment} is {@code null}.
   */
  public <R, A> @Nullable A runReader(
      @NonNull Kind<ReaderKind.Witness<R>, A> kind, @NonNull R environment) {
    return this.narrow(kind).run(environment);
  }
}
