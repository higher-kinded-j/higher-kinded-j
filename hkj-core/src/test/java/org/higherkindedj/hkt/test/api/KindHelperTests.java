// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.reader.ReaderKindHelper.READER;
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.lazy.LazyKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;

/**
 * Entry point for {@code KindHelper} (widen/narrow) round-trip testing of the built-in core types.
 *
 * <p>Each method returns a {@link KindHelperTestConfig} that verifies the widen/narrow isomorphism
 * — round-trip, idempotency and edge cases delegate to the shipped {@code hkj-test} {@code
 * KindHelperLaws}, plus the defensive null / foreign-kind checks. Concrete-type behaviour is tested
 * directly in each type's own test ({@code EitherTest}, {@code MaybeTest}, …) and type-class laws
 * via {@link org.higherkindedj.hkt.test.contract.TypeClassContract}.
 *
 * <pre>{@code
 * KindHelperTests.eitherKindHelper(Either.right("test")).test();
 * KindHelperTests.maybeKindHelper(Maybe.just(42)).skipValidations().test();
 * }</pre>
 */
public final class KindHelperTests {

  private KindHelperTests() {
    throw new AssertionError("KindHelperTests is a utility class");
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<T> typed(Class<?> raw) {
    return (Class<T>) raw;
  }

  /**
   * Verifies the Either KindHelper round-trip (widen/narrow via {@code EitherKindHelper.EITHER}).
   */
  public static <L, R>
      KindHelperTestConfig<Either<L, R>, EitherKind.Witness<L>, R> eitherKindHelper(
          Either<L, R> instance) {
    return new KindHelperTestConfig<>(
        instance, typed(Either.class), either -> EITHER.widen(either), kind -> EITHER.narrow(kind));
  }

  /** Verifies the Maybe KindHelper round-trip (widen/narrow via {@code MaybeKindHelper.MAYBE}). */
  public static <A> KindHelperTestConfig<Maybe<A>, MaybeKind.Witness, A> maybeKindHelper(
      Maybe<A> instance) {
    return new KindHelperTestConfig<>(
        instance, typed(Maybe.class), maybe -> MAYBE.widen(maybe), kind -> MAYBE.narrow(kind));
  }

  /** Verifies the IO KindHelper round-trip (widen/narrow via {@code IOKindHelper.IO_OP}). */
  public static <A> KindHelperTestConfig<IO<A>, IOKind.Witness, A> ioKindHelper(IO<A> instance) {
    return new KindHelperTestConfig<>(
        instance, typed(IO.class), io -> IO_OP.widen(io), kind -> IO_OP.narrow(kind));
  }

  /** Verifies the Lazy KindHelper round-trip (widen/narrow via {@code LazyKindHelper.LAZY}). */
  public static <A> KindHelperTestConfig<Lazy<A>, LazyKind.Witness, A> lazyKindHelper(
      Lazy<A> instance) {
    return new KindHelperTestConfig<>(
        instance, typed(Lazy.class), lazy -> LAZY.widen(lazy), kind -> LAZY.narrow(kind));
  }

  /**
   * Verifies the Reader KindHelper round-trip (widen/narrow via {@code ReaderKindHelper.READER}).
   */
  public static <R, A>
      KindHelperTestConfig<Reader<R, A>, ReaderKind.Witness<R>, A> readerKindHelper(
          Reader<R, A> instance) {
    return new KindHelperTestConfig<>(
        instance, typed(Reader.class), reader -> READER.widen(reader), kind -> READER.narrow(kind));
  }

  /**
   * Verifies the Writer KindHelper round-trip (widen/narrow via {@code WriterKindHelper.WRITER}).
   */
  public static <W, A>
      KindHelperTestConfig<Writer<W, A>, WriterKind.Witness<W>, A> writerKindHelper(
          Writer<W, A> instance) {
    return new KindHelperTestConfig<>(
        instance, typed(Writer.class), writer -> WRITER.widen(writer), kind -> WRITER.narrow(kind));
  }

  /** Verifies the State KindHelper round-trip (widen/narrow via {@code StateKindHelper.STATE}). */
  public static <S, A> KindHelperTestConfig<State<S, A>, StateKind.Witness<S>, A> stateKindHelper(
      State<S, A> instance) {
    return new KindHelperTestConfig<>(
        instance, typed(State.class), state -> STATE.widen(state), kind -> STATE.narrow(kind));
  }

  /** Verifies the Try KindHelper round-trip (widen/narrow via {@code TryKindHelper.TRY}). */
  public static <T> KindHelperTestConfig<Try<T>, TryKind.Witness, T> tryKindHelper(
      Try<T> instance) {
    return new KindHelperTestConfig<>(
        instance, typed(Try.class), t -> TRY.widen(t), kind -> TRY.narrow(kind));
  }

  /**
   * Verifies the Validated KindHelper round-trip (widen/narrow via {@code
   * ValidatedKindHelper.VALIDATED}).
   */
  public static <E, A>
      KindHelperTestConfig<Validated<E, A>, ValidatedKind.Witness<E>, A> validatedKindHelper(
          Validated<E, A> instance) {
    return new KindHelperTestConfig<>(
        instance,
        typed(Validated.class),
        validated -> VALIDATED.widen(validated),
        kind -> VALIDATED.narrow(kind));
  }

  /** Verifies the Id KindHelper round-trip (widen/narrow via {@code IdKindHelper.ID}). */
  public static <A> KindHelperTestConfig<Id<A>, IdKind.Witness, A> idKindHelper(Id<A> instance) {
    return new KindHelperTestConfig<>(
        instance, typed(Id.class), id -> ID.widen(id), kind -> ID.narrow(kind));
  }
}
