// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.io;

import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Selective} type class for {@link IO}. This provides selective applicative
 * operations that allow conditional execution based on the result of previous computations.
 *
 * <p>The Selective interface sits between {@link org.higherkindedj.hkt.Applicative} and {@link
 * org.higherkindedj.hkt.Monad} in terms of power. It allows for static analysis of effects while
 * still supporting conditional behavior.
 *
 * <p>For IO, selective operations maintain lazy evaluation semantics. This means that effects are
 * only executed when necessary based on the conditions evaluated at runtime. This can lead to more
 * efficient programs where unnecessary side effects are avoided.
 *
 * <p>Key operations:
 *
 * <ul>
 *   <li>{@link #select(Kind, Kind)}: Conditionally applies an effectful function based on a Choice.
 *   <li>{@link #branch(Kind, Kind, Kind)}: Provides two-way conditional choice with different
 *       handlers.
 *   <li>{@link #whenS(Kind, Kind)}: Conditionally executes an effect based on a boolean.
 *   <li>{@link #ifS(Kind, Kind, Kind)}: Ternary conditional for selective functors.
 * </ul>
 *
 * @see IO
 * @see IOMonad
 * @see Selective
 * @see Choice
 */
public final class IOSelective extends IOMonad implements Selective<IOKind.Witness> {

  /** Singleton instance of {@code IOSelective}. */
  public static final IOSelective INSTANCE = new IOSelective();

  private static final Class<IOSelective> IO_SELECTIVE_CLASS = IOSelective.class;

  /** Private constructor to enforce singleton pattern. */
  private IOSelective() {
    super();
  }

  /**
   * The core selective operation for IO. Given an effectful choice {@code fab} and an effectful
   * function {@code ff}, applies the function only if the choice is a {@code Left}.
   *
   * <p>This operation maintains IO's lazy evaluation semantics. The function IO is only executed if
   * the choice IO produces a {@code Left} value.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>If {@code fab} produces {@code Right(b)}: Returns an IO that produces {@code b}, {@code
   *       ff} is not executed.
   *   <li>If {@code fab} produces {@code Left(a)}: Executes {@code ff} to get a function, then
   *       applies it to {@code a}.
   * </ul>
   *
   * @param fab A {@link Kind} representing {@code IO<Choice<A, B>>}. Must not be null.
   * @param ff A {@link Kind} representing {@code IO<Function<A, B>>}. Must not be null.
   * @param <A> The input type of the function (the type inside {@code Left} of the Choice).
   * @param <B> The output type and the type inside {@code Right} of the Choice.
   * @return A {@link Kind} representing {@code IO<B>}. Never null.
   * @throws NullPointerException if {@code fab} or {@code ff} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fab} or {@code ff} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<IOKind.Witness, B> select(
      Kind<IOKind.Witness, Choice<A, B>> fab, Kind<IOKind.Witness, Function<A, B>> ff) {

    Validation.kind().requireNonNull(fab, IO_SELECTIVE_CLASS, SELECT, "choice");
    Validation.kind().requireNonNull(ff, IO_SELECTIVE_CLASS, SELECT, "function");

    IO<Choice<A, B>> ioChoice = IO_OP.narrow(fab);
    IO<Function<A, B>> ioFunction = IO_OP.narrow(ff);

    IO<B> ioB =
        IO.delay(
            () -> {
              Choice<A, B> choice = ioChoice.unsafeRunSync();

              // If choice is Right(b), we already have our value
              if (choice.isRight()) {
                return choice.getRight();
              }

              // Choice is Left(a), so we need to apply the function
              A value = choice.getLeft();
              Function<A, B> function = ioFunction.unsafeRunSync();
              return function.apply(value);
            });

    return IO_OP.widen(ioB);
  }

  /**
   * Optimized implementation of {@code branch} for IO. Provides a two-way conditional choice,
   * applying the appropriate handler based on whether the Choice is Left or Right.
   *
   * <p>Only the relevant handler IO is executed based on the choice result.
   *
   * @param fab A {@link Kind} representing {@code IO<Choice<A, B>>}. Must not be null.
   * @param fl A {@link Kind} representing {@code IO<Function<A, C>>} for the Left case. Must not be
   *     null.
   * @param fr A {@link Kind} representing {@code IO<Function<B, C>>} for the Right case. Must not
   *     be null.
   * @param <A> The type inside {@code Left} of the Choice.
   * @param <B> The type inside {@code Right} of the Choice.
   * @param <C> The result type.
   * @return A {@link Kind} representing {@code IO<C>}. Never null.
   */
  @Override
  public <A, B, C> Kind<IOKind.Witness, C> branch(
      Kind<IOKind.Witness, Choice<A, B>> fab,
      Kind<IOKind.Witness, Function<A, C>> fl,
      Kind<IOKind.Witness, Function<B, C>> fr) {

    Validation.kind().requireNonNull(fab, IO_SELECTIVE_CLASS, BRANCH, "choice");
    Validation.kind().requireNonNull(fl, IO_SELECTIVE_CLASS, BRANCH, "leftHandler");
    Validation.kind().requireNonNull(fr, IO_SELECTIVE_CLASS, BRANCH, "rightHandler");

    IO<Choice<A, B>> ioChoice = IO_OP.narrow(fab);
    IO<Function<A, C>> leftHandler = IO_OP.narrow(fl);
    IO<Function<B, C>> rightHandler = IO_OP.narrow(fr);

    IO<C> ioC =
        IO.delay(
            () -> {
              Choice<A, B> choice = ioChoice.unsafeRunSync();

              if (choice.isLeft()) {
                Function<A, C> leftFunc = leftHandler.unsafeRunSync();
                return leftFunc.apply(choice.getLeft());
              } else {
                Function<B, C> rightFunc = rightHandler.unsafeRunSync();
                return rightFunc.apply(choice.getRight());
              }
            });

    return IO_OP.widen(ioC);
  }

  /**
   * Optimized implementation of {@code whenS} for IO. Conditionally executes an effect based on a
   * boolean condition.
   *
   * <p>The effect IO is only executed if the condition is true, maintaining lazy evaluation.
   *
   * @param fcond A {@link Kind} representing {@code IO<Boolean>}. Must not be null.
   * @param fa A {@link Kind} representing {@code IO<A>} to execute if condition is true. Must not
   *     be null.
   * @param <A> The type of the effect's result.
   * @return A {@link Kind} representing {@code IO<A>}. Never null.
   */
  @Override
  public <A> Kind<IOKind.Witness, A> whenS(
      Kind<IOKind.Witness, Boolean> fcond, Kind<IOKind.Witness, A> fa) {

    Validation.kind().requireNonNull(fcond, IO_SELECTIVE_CLASS, WHEN_S, "condition");
    Validation.kind().requireNonNull(fa, IO_SELECTIVE_CLASS, WHEN_S, "effect");

    IO<Boolean> condIO = IO_OP.narrow(fcond);
    IO<A> effectIO = IO_OP.narrow(fa);

    IO<A> ioA =
        IO.delay(
            () -> {
              boolean condition = condIO.unsafeRunSync();

              if (condition) {
                return effectIO.unsafeRunSync();
              } else {
                // Condition is false, return null as unit
                return null;
              }
            });

    return IO_OP.widen(ioA);
  }

  /**
   * Optimized implementation of {@code ifS} for IO. A ternary conditional operator for selective
   * functors.
   *
   * <p>Only the selected branch IO is executed based on the condition result.
   *
   * @param fcond A {@link Kind} representing {@code IO<Boolean>}. Must not be null.
   * @param fthen A {@link Kind} representing {@code IO<A>} for the true branch. Must not be null.
   * @param felse A {@link Kind} representing {@code IO<A>} for the false branch. Must not be null.
   * @param <A> The type of the result.
   * @return A {@link Kind} representing {@code IO<A>}. Never null.
   */
  @Override
  public <A> Kind<IOKind.Witness, A> ifS(
      Kind<IOKind.Witness, Boolean> fcond,
      Kind<IOKind.Witness, A> fthen,
      Kind<IOKind.Witness, A> felse) {

    Validation.kind().requireNonNull(fcond, IO_SELECTIVE_CLASS, IF_S, "condition");
    Validation.kind().requireNonNull(fthen, IO_SELECTIVE_CLASS, IF_S, "thenBranch");
    Validation.kind().requireNonNull(felse, IO_SELECTIVE_CLASS, IF_S, "elseBranch");

    IO<Boolean> condIO = IO_OP.narrow(fcond);
    IO<A> thenIO = IO_OP.narrow(fthen);
    IO<A> elseIO = IO_OP.narrow(felse);

    IO<A> ioA =
        IO.delay(
            () -> {
              boolean condition = condIO.unsafeRunSync();
              return condition ? thenIO.unsafeRunSync() : elseIO.unsafeRunSync();
            });

    return IO_OP.widen(ioA);
  }
}
