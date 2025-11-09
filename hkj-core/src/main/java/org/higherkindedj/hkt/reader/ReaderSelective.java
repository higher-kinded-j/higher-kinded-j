// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Implements the {@link Selective} type class for {@link Reader}, with a fixed environment type
 * {@code R}. This provides selective applicative operations that allow conditional execution based
 * on the result of previous computations.
 *
 * <p>The Selective interface sits between {@link org.higherkindedj.hkt.Applicative} and {@link
 * org.higherkindedj.hkt.Monad} in terms of power. It allows for static analysis of effects while
 * still supporting conditional behaviour.
 *
 * <p>For Reader, selective operations allow conditional computations that may or may not require
 * accessing the environment, potentially enabling Optimisations where the environment is only read
 * when necessary.
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
 * @param <R> The fixed type for the environment required by the {@code Reader}.
 * @see Reader
 * @see ReaderMonad
 * @see Selective
 * @see Choice
 */
public final class ReaderSelective<R> extends ReaderMonad<R>
    implements Selective<ReaderKind.Witness<R>> {

  private static final ReaderSelective<?> INSTANCE = new ReaderSelective<>();
  private static final Class<ReaderSelective> READER_SELECTIVE_CLASS = ReaderSelective.class;

  private ReaderSelective() {
    super();
  }

  /**
   * Returns the singleton instance of {@code ReaderSelective} for the specified environment type.
   *
   * @param <R> The type of the environment.
   * @return The singleton instance.
   */
  @SuppressWarnings("unchecked")
  public static <R> ReaderSelective<R> instance() {
    return (ReaderSelective<R>) INSTANCE;
  }

  /**
   * The core selective operation for Reader. Given an effectful choice {@code fab} and an effectful
   * function {@code ff}, applies the function only if the choice is a {@code Left}.
   *
   * <p>For Reader, both the choice and the function are computations that depend on the environment
   * {@code R}. The selective operation sequences these computations appropriately.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>If {@code fab} produces {@code Right(b)}: Returns a Reader that produces {@code b},
   *       {@code ff} is not evaluated.
   *   <li>If {@code fab} produces {@code Left(a)}: The function from {@code ff} is applied to
   *       {@code a}.
   * </ul>
   *
   * @param fab A {@link Kind} representing {@code Reader<R, Choice<A, B>>}. Must not be null.
   * @param ff A {@link Kind} representing {@code Reader<R, Function<A, B>>}. Must not be null.
   * @param <A> The input type of the function (the type inside {@code Left} of the Choice).
   * @param <B> The output type and the type inside {@code Right} of the Choice.
   * @return A {@link Kind} representing {@code Reader<R, B>}. Never null.
   * @throws NullPointerException if {@code fab} or {@code ff} is null.
   * @throws org.higherkindedj.hkt.exception.KindUnwrapException if {@code fab} or {@code ff} cannot
   *     be unwrapped.
   */
  @Override
  public <A, B> Kind<ReaderKind.Witness<R>, B> select(
      Kind<ReaderKind.Witness<R>, Choice<A, B>> fab,
      Kind<ReaderKind.Witness<R>, Function<A, B>> ff) {

    Validation.kind().requireNonNull(fab, READER_SELECTIVE_CLASS, SELECT, "choice");
    Validation.kind().requireNonNull(ff, READER_SELECTIVE_CLASS, SELECT, "function");

    Reader<R, Choice<A, B>> readerChoice = READER.narrow(fab);
    Reader<R, Function<A, B>> readerFunction = READER.narrow(ff);

    Reader<R, B> readerB =
        (R r) -> {
          Choice<A, B> choice = readerChoice.run(r);

          // If choice is Right(b), we already have our value
          if (choice.isRight()) {
            return choice.getRight();
          }

          // Choice is Left(a), so we need to apply the function
          A value = choice.getLeft();
          Function<A, B> function = readerFunction.run(r);
          return function.apply(value);
        };

    return READER.widen(readerB);
  }

  /**
   * Optimised implementation of {@code branch} for Reader. Provides a two-way conditional choice,
   * applying the appropriate handler based on whether the Choice is Left or Right.
   *
   * @param fab A {@link Kind} representing {@code Reader<R, Choice<A, B>>}. Must not be null.
   * @param fl A {@link Kind} representing {@code Reader<R, Function<A, C>>} for the Left case. Must
   *     not be null.
   * @param fr A {@link Kind} representing {@code Reader<R, Function<B, C>>} for the Right case.
   *     Must not be null.
   * @param <A> The type inside {@code Left} of the Choice.
   * @param <B> The type inside {@code Right} of the Choice.
   * @param <C> The result type.
   * @return A {@link Kind} representing {@code Reader<R, C>}. Never null.
   */
  @Override
  public <A, B, C> Kind<ReaderKind.Witness<R>, C> branch(
      Kind<ReaderKind.Witness<R>, Choice<A, B>> fab,
      Kind<ReaderKind.Witness<R>, Function<A, C>> fl,
      Kind<ReaderKind.Witness<R>, Function<B, C>> fr) {

    Validation.kind().requireNonNull(fab, READER_SELECTIVE_CLASS, BRANCH, "choice");
    Validation.kind().requireNonNull(fl, READER_SELECTIVE_CLASS, BRANCH, "leftHandler");
    Validation.kind().requireNonNull(fr, READER_SELECTIVE_CLASS, BRANCH, "rightHandler");

    Reader<R, Choice<A, B>> readerChoice = READER.narrow(fab);
    Reader<R, Function<A, C>> leftHandler = READER.narrow(fl);
    Reader<R, Function<B, C>> rightHandler = READER.narrow(fr);

    Reader<R, C> readerC =
        (R r) -> {
          Choice<A, B> choice = readerChoice.run(r);

          if (choice.isLeft()) {
            Function<A, C> leftFunc = leftHandler.run(r);
            return leftFunc.apply(choice.getLeft());
          } else {
            Function<B, C> rightFunc = rightHandler.run(r);
            return rightFunc.apply(choice.getRight());
          }
        };

    return READER.widen(readerC);
  }

  /**
   * Conditionally executes a Unit-returning effect based on a boolean condition.
   *
   * <p>Key improvement: Returns Unit.INSTANCE instead of null in the Reader computation, making the
   * result type-safe.
   *
   * @param fcond The effectful condition
   * @param fa The Unit-returning effect to execute if condition is true
   * @return Reader with Unit result
   */
  @Override
  public Kind<ReaderKind.Witness<R>, Unit> whenS(
      Kind<ReaderKind.Witness<R>, Boolean> fcond, Kind<ReaderKind.Witness<R>, Unit> fa) {

    Validation.kind().requireNonNull(fcond, READER_SELECTIVE_CLASS, WHEN_S, "condition");
    Validation.kind().requireNonNull(fa, READER_SELECTIVE_CLASS, WHEN_S, "effect");

    Reader<R, Boolean> condReader = READER.narrow(fcond);
    Reader<R, Unit> effectReader = READER.narrow(fa);

    Reader<R, Unit> readerUnit =
        (R r) -> {
          boolean condition = condReader.run(r);

          if (condition) {
            return effectReader.run(r);
          } else {
            // Condition is false, return Unit (not null!)
            return Unit.INSTANCE;
          }
        };

    return READER.widen(readerUnit);
  }

  /**
   * Optimised implementation of {@code ifS} for Reader. A ternary conditional operator for
   * selective functors.
   *
   * @param fcond A {@link Kind} representing {@code Reader<R, Boolean>}. Must not be null.
   * @param fthen A {@link Kind} representing {@code Reader<R, A>} for the true branch. Must not be
   *     null.
   * @param felse A {@link Kind} representing {@code Reader<R, A>} for the false branch. Must not be
   *     null.
   * @param <A> The type of the result.
   * @return A {@link Kind} representing {@code Reader<R, A>}. Never null.
   */
  @Override
  public <A> Kind<ReaderKind.Witness<R>, A> ifS(
      Kind<ReaderKind.Witness<R>, Boolean> fcond,
      Kind<ReaderKind.Witness<R>, A> fthen,
      Kind<ReaderKind.Witness<R>, A> felse) {

    Validation.kind().requireNonNull(fcond, READER_SELECTIVE_CLASS, IF_S, "condition");
    Validation.kind().requireNonNull(fthen, READER_SELECTIVE_CLASS, IF_S, "thenBranch");
    Validation.kind().requireNonNull(felse, READER_SELECTIVE_CLASS, IF_S, "elseBranch");

    Reader<R, Boolean> condReader = READER.narrow(fcond);
    Reader<R, A> thenReader = READER.narrow(fthen);
    Reader<R, A> elseReader = READER.narrow(felse);

    Reader<R, A> readerA =
        (R r) -> {
          boolean condition = condReader.run(r);
          return condition ? thenReader.run(r) : elseReader.run(r);
        };

    return READER.widen(readerA);
  }
}
