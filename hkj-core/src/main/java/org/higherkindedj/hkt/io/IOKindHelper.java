// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link IOConverterOps} for widen/narrow operations, and providing additional
 * factory and utility instance methods for {@link IO} types.
 *
 * <p>Access these operations via the singleton {@code IO_OP}. For example: {@code
 * IOKindHelper.IO_OP.widen(IO.delay(() -> "effect"));}
 */
public enum IOKindHelper implements IOConverterOps {
  IO_OP;

  /** Error message for when a {@code null} {@link Kind} is passed to {@link #narrow(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for IO";

  /**
   * Error message for when a {@link Kind} of an unexpected type is passed to {@link #narrow(Kind)}.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an IOHolder: ";

  /**
   * Error message for when the internal holder in {@link #narrow(Kind)} contains a {@code null} IO
   * instance. This should ideally not occur if {@link #widen(IO)} enforces non-null IO instances
   * and IOHolder guarantees its content via .
   */
  public static final String INVALID_HOLDER_STATE_MSG = "IOHolder contained null IO instance";

  /**
   * Internal record implementing {@link IOKind} to hold the concrete {@link IO} instance. Changed
   * to package-private for potential test access.
   *
   * @param <A> The result type of the IO computation.
   * @param ioInstance The non-null, actual {@link IO} instance.
   */
  record IOHolder<A>(IO<A> ioInstance) implements IOKind<A> {}

  /**
   * Widens a concrete {@link IO<A>} instance into its higher-kinded representation, {@code
   * Kind<IOKind.Witness, A>}. Implements {@link IOConverterOps#widen}.
   *
   * @param <A> The result type of the {@code IO} computation.
   * @param io The non-null, concrete {@link IO<A>} instance to widen.
   * @return A non-null {@link Kind<IOKind.Witness, A>} representing the wrapped {@code IO}
   *     computation.
   * @throws NullPointerException if {@code io} is {@code null}.
   */
  @Override
  public <A> Kind<IOKind.Witness, A> widen(IO<A> io) {
    Objects.requireNonNull(io, "Input IO cannot be null for widen");
    return new IOHolder<>(io);
  }

  /**
   * Narrows a {@code Kind<IOKind.Witness, A>} back to its concrete {@link IO<A>} type. Implements
   * {@link IOConverterOps#narrow}.
   *
   * @param <A> The result type of the {@code IO} computation.
   * @param kind The {@code Kind<IOKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link IO<A>} instance.
   * @throws KindUnwrapException if the input {@code kind} is {@code null}, or not an instance of
   *     {@code IOHolder}. The {@code IOHolder} guarantees its internal {@code ioInstance} is
   *     non-null.
   */
  @Override
  @SuppressWarnings("unchecked") // For casting holder.ioInstance()
  public <A> IO<A> narrow(@Nullable Kind<IOKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      // IOHolder's record component ioInstance is , so no further null check needed here.
      case IOKindHelper.IOHolder<?> holder -> (IO<A>) holder.ioInstance();
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Creates a {@link Kind<IOKind.Witness, A>} that wraps an {@link IO} computation produced by
   * delaying the execution of a {@link Supplier}.
   *
   * @param <A> The type of the value produced by the {@code IO} computation.
   * @param thunk The non-null {@link Supplier} representing the deferred computation.
   * @return A new, non-null {@code Kind<IOKind.Witness, A>} representing the delayed {@code IO}
   *     computation.
   * @throws NullPointerException if {@code thunk} is {@code null}.
   */
  public <A> Kind<IOKind.Witness, A> delay(Supplier<A> thunk) {
    return this.widen(IO.delay(thunk));
  }

  /**
   * Executes the {@link IO} computation held within the {@link Kind} wrapper and retrieves its
   * result. This method synchronously runs the {@code IO} action.
   *
   * @param <A> The type of the result produced by the {@code IO} computation.
   * @param kind The non-null {@code Kind<IOKind.Witness, A>} holding the {@code IO} computation.
   * @return The result of the {@code IO} computation.
   * @throws KindUnwrapException if the input {@code kind} is invalid. Any exceptions from the
   *     {@code IO} computation propagate.
   */
  public <A> A unsafeRunSync(Kind<IOKind.Witness, A> kind) {
    return this.narrow(kind).unsafeRunSync();
  }
}
