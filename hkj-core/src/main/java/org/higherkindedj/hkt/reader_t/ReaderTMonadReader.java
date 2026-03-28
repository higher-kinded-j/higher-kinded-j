// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader_t;

import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Provides a {@link MonadReader} instance for {@link ReaderT}.
 *
 * <p>This class extends {@link ReaderTMonad} to inherit all monad operations and adds the
 * reader-specific operations {@code ask()} and {@code local()} from {@link MonadReader}.
 *
 * @param <F> The witness type of the outer monad.
 * @param <R_ENV> The type of the environment.
 * @see ReaderTMonad
 * @see MonadReader
 * @see ReaderT
 */
public class ReaderTMonadReader<F extends WitnessArity<TypeArity.Unary>, R_ENV>
    extends ReaderTMonad<F, R_ENV> implements MonadReader<ReaderTKind.Witness<F, R_ENV>, R_ENV> {

  /**
   * Constructs a {@link ReaderTMonadReader} instance.
   *
   * @param outerMonad The {@link Monad} instance for the outer type constructor {@code F}. Must not
   *     be null.
   * @throws NullPointerException if {@code outerMonad} is null.
   */
  public ReaderTMonadReader(Monad<F> outerMonad) {
    super(outerMonad);
  }

  /**
   * Returns the current environment, lifted into the {@code ReaderT} context.
   *
   * <p>This is equivalent to {@code ReaderT(r -> outerMonad.of(r))}.
   *
   * @return A {@code Kind} representing a {@code ReaderT} that yields the environment.
   */
  @Override
  public Kind<ReaderTKind.Witness<F, R_ENV>, R_ENV> ask() {
    return READER_T.widen(ReaderT.ask(outerMonad));
  }

  /**
   * Runs a computation in a modified environment.
   *
   * <p>The function {@code f} transforms the environment before it is made available to the
   * computation {@code ma}. This is equivalent to {@code ReaderT(r ->
   * narrow(ma).run().apply(f.apply(r)))}.
   *
   * @param f The function to modify the environment. Must not be null.
   * @param ma The computation to run in the modified environment. Must not be null.
   * @param <A> The result type of the computation.
   * @return The result of running {@code ma} with the modified environment.
   * @throws NullPointerException if {@code f} or {@code ma} is null.
   */
  @Override
  public <A> Kind<ReaderTKind.Witness<F, R_ENV>, A> local(
      Function<R_ENV, R_ENV> f, Kind<ReaderTKind.Witness<F, R_ENV>, A> ma) {
    Validation.function().require(f, "f", LOCAL);
    Validation.kind().requireNonNull(ma, LOCAL);

    ReaderT<F, R_ENV, A> readerT = READER_T.narrow(ma);
    ReaderT<F, R_ENV, A> localReaderT = new ReaderT<>(r -> readerT.run().apply(f.apply(r)));
    return READER_T.widen(localReaderT);
  }
}
