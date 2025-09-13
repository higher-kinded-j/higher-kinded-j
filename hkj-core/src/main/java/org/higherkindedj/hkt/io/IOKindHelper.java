// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.util.ErrorHandling.narrowKind;
import static org.higherkindedj.hkt.util.ErrorHandling.requireNonNullForHolder;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link IOConverterOps} for widen/narrow operations, and providing additional
 * factory and utility instance methods for {@link IO} types.
 */
public enum IOKindHelper implements IOConverterOps {
  IO_OP;

  private static final String TYPE_NAME = "IO";

  /**
   * Widens a concrete {@link IO<A>} instance into its higher-kinded representation, {@code
   * Kind<IOKind.Witness, A>}. Implements {@link IOConverterOps#widen}.
   */
  @Override
  public <A> Kind<IOKind.Witness, A> widen(IO<A> io) {
    Objects.requireNonNull(io, "Input IO cannot be null for widen");
    return new IOHolder<>(io);
  }

  /**
   * Narrows a {@code Kind<IOKind.Witness, A>} back to its concrete {@link IO<A>} type. Implements
   * {@link IOConverterOps#narrow}.
   */
  @Override
  public <A> IO<A> narrow(@Nullable Kind<IOKind.Witness, A> kind) {
    return narrowKind(kind, TYPE_NAME, this::extractIO);
  }

  /**
   * Creates a {@link Kind<IOKind.Witness, A>} that wraps an {@link IO} computation produced by
   * delaying the execution of a {@link Supplier}.
   */
  public <A> Kind<IOKind.Witness, A> delay(Supplier<A> thunk) {
    return this.widen(IO.delay(thunk));
  }

  /**
   * Executes the {@link IO} computation held within the {@link Kind} wrapper and retrieves its
   * result. This method synchronously runs the {@code IO} action.
   */
  public <A> A unsafeRunSync(Kind<IOKind.Witness, A> kind) {
    return this.narrow(kind).unsafeRunSync();
  }

  private <A> IO<A> extractIO(Kind<IOKind.Witness, A> kind) {
    return switch (kind) {
      case IOKindHelper.IOHolder<?> holder -> (IO<A>) holder.ioInstance();
      default -> throw new ClassCastException(); // Will be caught and wrapped by narrowKind
    };
  }

  /**
   * Internal record implementing {@link IOKind} to hold the concrete {@link IO} instance. Updated
   * to use standardized holder validation.
   *
   * @param <A> The result type of the IO computation.
   * @param ioInstance The non-null, actual {@link IO} instance.
   */
  record IOHolder<A>(IO<A> ioInstance) implements IOKind<A> {
    IOHolder {
      requireNonNullForHolder(ioInstance, TYPE_NAME);
    }
  }
}
