// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vtask;

import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link VTaskConverterOps} for widen/narrow operations, and providing additional
 * factory and utility instance methods for {@link VTask} types.
 *
 * <p>Access these operations via the singleton {@code VTASK}. For example: {@code
 * VTaskKindHelper.VTASK.widen(myVTask);} Or, with static import: {@code import static
 * org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK; VTASK.widen(...);}
 *
 * @see VTask
 * @see VTaskKind
 * @see VTaskConverterOps
 */
public enum VTaskKindHelper implements VTaskConverterOps {
  /** The singleton instance for VTask operations. */
  VTASK;

  private static final Class<VTask> VTASK_CLASS = VTask.class;

  /**
   * Widens a concrete {@link VTask<A>} instance into its higher-kinded representation, {@code
   * Kind<VTaskKind.Witness, A>}. Implements {@link VTaskConverterOps#widen}.
   *
   * <p>Since {@code VTask} directly extends {@code VTaskKind}, this method performs a simple
   * type-safe cast without requiring a wrapper object.
   *
   * @param <A> The result type of the {@code VTask} computation.
   * @param vtask The non-null, concrete {@link VTask<A>} instance to widen.
   * @return A non-null {@link Kind<VTaskKind.Witness, A>} representing the {@code VTask}
   *     computation.
   * @throws NullPointerException if {@code vtask} is {@code null}.
   */
  @Override
  public <A> Kind<VTaskKind.Witness, A> widen(VTask<A> vtask) {
    Validation.kind().requireForWiden(vtask, VTASK_CLASS);
    return vtask;
  }

  /**
   * Narrows a {@code Kind<VTaskKind.Witness, A>} back to its concrete {@link VTask<A>} type.
   * Implements {@link VTaskConverterOps#narrow}.
   *
   * <p>Since {@code VTask} directly extends {@code VTaskKind}, this method performs a direct type
   * check and cast without needing to unwrap from a holder.
   *
   * @param <A> The result type of the {@code VTask} computation.
   * @param kind The {@code Kind<VTaskKind.Witness, A>} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link VTask<A>} instance.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if the input {@code kind} is {@code
   *     null}, or not an instance of {@code VTask}.
   */
  @Override
  public <A> VTask<A> narrow(@Nullable Kind<VTaskKind.Witness, A> kind) {
    return Validation.kind().narrowWithTypeCheck(kind, VTASK_CLASS);
  }

  /**
   * Creates a {@link Kind<VTaskKind.Witness, A>} from a {@link Callable}.
   *
   * @param <A> The result type of the computation.
   * @param callable The callable to execute. Must not be null.
   * @return A non-null {@link Kind<VTaskKind.Witness, A>} representing the computation.
   * @throws NullPointerException if {@code callable} is null.
   */
  public <A> Kind<VTaskKind.Witness, A> of(Callable<A> callable) {
    return this.widen(VTask.of(callable));
  }

  /**
   * Creates a {@link Kind<VTaskKind.Witness, A>} that wraps a {@link VTask} computation produced by
   * delaying the execution of a {@link Supplier}.
   *
   * @param <A> The result type of the computation.
   * @param thunk The supplier to delay. Must not be null.
   * @return A non-null {@link Kind<VTaskKind.Witness, A>} representing the delayed computation.
   * @throws NullPointerException if {@code thunk} is null.
   */
  public <A> Kind<VTaskKind.Witness, A> delay(Supplier<A> thunk) {
    return this.widen(VTask.delay(thunk));
  }

  /**
   * Creates a {@link Kind<VTaskKind.Witness, A>} that succeeds with the given value.
   *
   * @param <A> The type of the value.
   * @param value The value to wrap. Can be {@code null}.
   * @return A non-null {@link Kind<VTaskKind.Witness, A>} representing the successful computation.
   */
  public <A> Kind<VTaskKind.Witness, A> succeed(@Nullable A value) {
    return this.widen(VTask.succeed(value));
  }

  /**
   * Creates a {@link Kind<VTaskKind.Witness, A>} that fails with the given error.
   *
   * @param <A> The phantom type parameter.
   * @param error The error to fail with. Must not be null.
   * @return A non-null {@link Kind<VTaskKind.Witness, A>} representing the failed computation.
   * @throws NullPointerException if {@code error} is null.
   */
  public <A> Kind<VTaskKind.Witness, A> fail(Throwable error) {
    return this.widen(VTask.fail(error));
  }

  /**
   * Executes the {@link VTask} computation held within the {@link Kind} wrapper, blocking until
   * completion.
   *
   * @param <A> The result type of the computation.
   * @param kind The {@code Kind<VTaskKind.Witness, A>} holding the VTask computation. Must not be
   *     null.
   * @return The result of executing the VTask computation.
   * @throws Throwable If the computation fails.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} cannot be
   *     unwrapped.
   */
  public <A> @Nullable A run(Kind<VTaskKind.Witness, A> kind) throws Throwable {
    return this.narrow(kind).run();
  }

  /**
   * Executes the {@link VTask} computation held within the {@link Kind} wrapper, returning a {@link
   * Try}.
   *
   * @param <A> The result type of the computation.
   * @param kind The {@code Kind<VTaskKind.Witness, A>} holding the VTask computation. Must not be
   *     null.
   * @return A {@link Try} containing the result or failure.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code kind} cannot be
   *     unwrapped.
   */
  public <A> Try<A> runSafe(Kind<VTaskKind.Witness, A> kind) {
    return this.narrow(kind).runSafe();
  }
}
