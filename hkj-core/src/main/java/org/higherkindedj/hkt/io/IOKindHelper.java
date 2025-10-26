// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link IOConverterOps} for widen/narrow operations, and providing additional
 * factory and utility instance methods for {@link IO} types.
 *
 * <p>Access these operations via the singleton {@code IO_OP}. For example: {@code
 * IOKindHelper.IO_OP.widen(myIO);} Or, with static import: {@code import static
 * org.higherkindedj.hkt.io.IOKindHelper.IO_OP; IO_OP.widen(...);}
 */
public enum IOKindHelper implements IOConverterOps {
  IO_OP;

  private static final Class<IO> IO_CLASS = IO.class;

  /**
   * Internal record implementing {@link IOKind} to hold the concrete {@link IO} instance.
   *
   * @param <A> The result type of the IO computation.
   * @param ioInstance The non-null, actual {@link IO} instance.
   */
  record IOHolder<A>(IO<A> ioInstance) implements IOKind<A> {
    IOHolder {
      Validation.kind().requireForWiden(ioInstance, IO_CLASS);
    }
  }

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
    return new IOHolder<>(io);
  }

  /**
   * Narrows a {@code Kind<IOKind.Witness, A>} back to its concrete {@link IO<A>} type. Implements
   * {@link IOConverterOps#narrow}.
   *
   * <p>This implementation uses a holder-based approach with modern switch expressions for
   * consistent pattern matching.
   *
   * @param <A> The result type of the {@code IO} computation.
   * @param kind The {@code Kind<IOKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link IO<A>} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null}, or not an instance of the expected underlying holder type for IO.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A> IO<A> narrow(@Nullable Kind<IOKind.Witness, A> kind) {
    return Validation.kind()
        .narrowWithPattern(
            kind, IO_CLASS, IOHolder.class, holder -> ((IOHolder<A>) holder).ioInstance());
  }

  /**
   * Creates a {@link Kind<IOKind.Witness, A>} that wraps an {@link IO} computation produced by
   * delaying the execution of a {@link Supplier}.
   *
   * @param <A> The result type of the computation.
   * @param thunk The supplier to delay. Must not be null.
   * @return A non-null {@link Kind<IOKind.Witness, A>} representing the delayed computation.
   * @throws NullPointerException if {@code thunk} is null.
   */
  public <A> Kind<IOKind.Witness, A> delay(Supplier<A> thunk) {
    return this.widen(IO.delay(thunk));
  }

  /**
   * Executes the {@link IO} computation held within the {@link Kind} wrapper and retrieves its
   * result. This method synchronously runs the {@code IO} action.
   *
   * @param <A> The result type of the computation.
   * @param kind The {@code Kind<IOKind.Witness, A>} holding the IO computation. Must not be null.
   * @return The result of executing the IO computation.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} cannot be
   *     unwrapped.
   */
  public <A> A unsafeRunSync(Kind<IOKind.Witness, A> kind) {
    return this.narrow(kind).unsafeRunSync();
  }
}
