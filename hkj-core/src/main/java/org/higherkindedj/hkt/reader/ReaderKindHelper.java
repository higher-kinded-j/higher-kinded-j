// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.util.validation.Operation.RUN_READER;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.FunctionValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.higherkindedj.hkt.util.validation.Operation;
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

  private static final Class<Reader> READER_CLASS = Reader.class;

  /**
   * Internal record implementing {@link ReaderKind ReaderKind&lt;R, A&gt;} to hold the concrete
   * {@link Reader Reader&lt;R, A&gt;} instance.
   *
   * @param <R> The environment type of the {@code Reader}.
   * @param <A> The value type of the {@code Reader}.
   * @param reader The non-null, actual {@link Reader Reader&lt;R, A&gt;} instance.
   */
  record ReaderHolder<R, A>(Reader<R, A> reader) implements ReaderKind<R, A> {
    /**
     * Constructs a {@code ReaderHolder}.
     *
     * @param reader The {@link Reader} to hold. Must not be null.
     * @throws NullPointerException if the provided {@code reader} instance is null.
     */
    ReaderHolder {
      KindValidator.requireForWiden(reader, READER_CLASS);
    }
  }

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
  public <R, A> Kind<ReaderKind.Witness<R>, A> widen(Reader<R, A> reader) {
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
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null}, or not an instance of {@code ReaderHolder}. The {@code ReaderHolder} guarantees its
   *     internal {@code reader} is non-null.
   */
  @Override
  public <R, A> Reader<R, A> narrow(@Nullable Kind<ReaderKind.Witness<R>, A> kind) {
    return KindValidator.narrow(kind, READER_CLASS, this::extractReader);
  }

  /**
   * Creates a {@code Kind<ReaderKind.Witness<R>, A>} that wraps a {@link Reader} computation
   * defined by the given function {@code R -> A}.
   *
   * @param <R> The type of the environment required by the {@code Reader}.
   * @param <A> The type of the value produced by the {@code Reader}.
   * @param runFunction The non-null function {@code (R -> A)} defining the reader's computation.
   * @return A new, non-null {@code Kind<ReaderKind.Witness<R>, A>} representing the {@code Reader}.
   * @throws NullPointerException if {@code runFunction} is null.
   */
  public <R, A> Kind<ReaderKind.Witness<R>, A> reader(Function<R, A> runFunction) {
    FunctionValidator.requireFunction(runFunction, "runFunction", READER_CLASS, Operation.READER);
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
  public <R, A> Kind<ReaderKind.Witness<R>, A> constant(@Nullable A value) {
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
  public <R> Kind<ReaderKind.Witness<R>, R> ask() {
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
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is
   *     invalid.
   * @throws NullPointerException if {@code environment} is {@code null}.
   */
  public <R, A> @Nullable A runReader(Kind<ReaderKind.Witness<R>, A> kind, R environment) {
    KindValidator.requireNonNull(kind, READER_CLASS, RUN_READER);
    // Note: We don't validate environment as null here since Reader interface allows @NonNull R
    // but the specific nullability contract depends on the design of the environment type R
    return this.narrow(kind).run(environment);
  }

  /** Internal narrowing implementation that performs the actual type checking and extraction. */
  @SuppressWarnings("unchecked")
  private <R, A> Reader<R, A> extractReader(Kind<ReaderKind.Witness<R>, A> kind) {
    return switch (kind) {
      case ReaderKindHelper.ReaderHolder<?, ?> holder -> (Reader<R, A>) holder.reader();
      default -> throw new ClassCastException(); // Will be caught and wrapped by KindValidator
    };
  }
}
