// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.util.validation.Operation.TELL;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.unit.Unit;
import org.higherkindedj.hkt.util.validation.CoreTypeValidator;
import org.higherkindedj.hkt.util.validation.KindValidator;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link WriterConverterOps} for widen/narrow operations, and providing
 * additional factory and utility instance methods for {@link Writer} types.
 *
 * <p>Access these operations via the singleton {@code WRITER}. For example: {@code
 * WriterKindHelper.WRITER.widen(Writer.tell("log"));}
 */
public enum WriterKindHelper implements WriterConverterOps {
  WRITER;

  private static final Class<Writer> WRITER_CLASS = Writer.class;

  /**
   * Internal record implementing {@link WriterKind WriterKind&lt;W, A&gt;} to hold the concrete
   * {@link Writer Writer&lt;W, A&gt;} instance. Changed to package-private for potential test
   * access.
   *
   * @param <W> The log type.
   * @param <A> The value type.
   * @param writer The non-null {@link Writer Writer&lt;W, A&gt;} instance.
   */
  record WriterHolder<W, A>(Writer<W, A> writer) implements WriterKind<W, A> {
    WriterHolder {
      KindValidator.requireForWiden(writer, WRITER_CLASS);
    }
  }

  /**
   * Widens a concrete {@link Writer Writer&lt;W, A&gt;} instance into its higher-kinded
   * representation, {@code Kind<WriterKind.Witness<W>, A>}.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param writer The concrete {@link Writer Writer&lt;W, A&gt;} instance to widen. Must be
   *     non-null.
   * @return A non-null {@code Kind<WriterKind.Witness<W>, A>} representing the wrapped {@code
   *     Writer}.
   * @throws NullPointerException if {@code writer} is null.
   */
  @Override
  public <W, A> Kind<WriterKind.Witness<W>, A> widen(Writer<W, A> writer) {
    return new WriterHolder<>(writer);
  }

  /**
   * Narrows a {@code Kind<WriterKind.Witness<W>, A>} back to its concrete {@link Writer
   * Writer&lt;W, A&gt;} type.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind.Witness<W>, A>} instance to narrow. May be {@code null}.
   * @return The unwrapped, non-null {@link Writer Writer&lt;W, A&gt;} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is null
   *     or not an instance of {@code WriterHolder}. The {@code WriterHolder} guarantees its
   *     internal {@code writer} is non-null.
   */
  @Override
  public <W, A> Writer<W, A> narrow(@Nullable Kind<WriterKind.Witness<W>, A> kind) {
    return KindValidator.narrow(kind, WRITER_CLASS, this::extractWriter);
  }

  /**
   * Creates a {@code Kind<WriterKind.Witness<W>, A>} with an empty log (based on the provided
   * {@link Monoid}) and the given value.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param monoidW The {@link Monoid} instance for the log type {@code W}. Must be non-null.
   * @param value The computed value. Can be {@code @Nullable}.
   * @return A {@code Kind<WriterKind.Witness<W>, A>} representing the value with an empty log.
   *     Never null.
   */
  public <W, A> Kind<WriterKind.Witness<W>, A> value(Monoid<W> monoidW, @Nullable A value) {
    return this.widen(Writer.value(monoidW, value));
  }

  /**
   * Creates a {@code Kind<WriterKind.Witness<W>, Unit>} that logs a message and has a {@link
   * Unit#INSTANCE} value.
   *
   * @param <W> The type of the accumulated log/output.
   * @param log The log message to accumulate. Must not be null.
   * @return A {@code Kind<WriterKind.Witness<W>, Unit>} representing only the log action. Never
   *     null.
   * @throws NullPointerException if {@code log} is null (delegated to Writer.tell).
   */
  public <W> Kind<WriterKind.Witness<W>, Unit> tell(W log) {
    CoreTypeValidator.requireValue(log, WRITER_CLASS, TELL);
    return this.widen(Writer.tell(log));
  }

  /**
   * Runs the {@link Writer} computation held within the {@link Kind} wrapper, returning the
   * complete {@link Writer Writer&lt;W, A&gt;} record which contains both the log and the value.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind.Witness<W>, A>} holding the {@code Writer} computation.
   *     Must be non-null.
   * @return The {@link Writer Writer&lt;W, A&gt;} record containing the final value and log. Never
   *     null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is
   *     invalid.
   */
  public <W, A> Writer<W, A> runWriter(Kind<WriterKind.Witness<W>, A> kind) {
    return this.narrow(kind);
  }

  /**
   * Runs the {@link Writer} computation held within the {@link Kind} wrapper, returning only the
   * computed value {@code A} and discarding the log.
   *
   * @param <W> The type of the accumulated log/output (discarded).
   * @param <A> The type of the computed value.
   * @param kind The {@code Kind<WriterKind.Witness<W>, A>} holding the {@code Writer} computation.
   *     Must be non-null.
   * @return The computed value {@code A}. Can be null if the Writer wrapped a null value.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is
   *     invalid.
   */
  public <W, A> @Nullable A run(Kind<WriterKind.Witness<W>, A> kind) {
    return this.narrow(kind).run();
  }

  /**
   * Runs the {@link Writer} computation held within the {@link Kind} wrapper, returning only the
   * accumulated log {@code W} and discarding the value.
   *
   * @param <W> The type of the accumulated log/output.
   * @param <A> The type of the computed value (discarded).
   * @param kind The {@code Kind<WriterKind.Witness<W>, A>} holding the {@code Writer} computation.
   *     Must be non-null.
   * @return The accumulated log {@code W}. Never null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is
   *     invalid.
   */
  public <W, A> W exec(Kind<WriterKind.Witness<W>, A> kind) {
    return this.narrow(kind).exec();
  }

  /**
   * Internal extraction method for narrowing operations.
   *
   * @throws ClassCastException if kind is not a WriterHolder (will be caught and wrapped by
   *     KindValidator)
   */
  private <W, A> Writer<W, A> extractWriter(Kind<WriterKind.Witness<W>, A> kind) {
    return switch (kind) {
      case WriterHolder<W, A> holder -> holder.writer();
      default -> throw new ClassCastException();
    };
  }
}
