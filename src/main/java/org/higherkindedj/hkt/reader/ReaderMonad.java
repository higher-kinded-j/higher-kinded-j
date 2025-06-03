// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.reader;

import static org.higherkindedj.hkt.reader.ReaderKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;

/**
 * Implements the {@link Monad} interface for the {@link Reader} monad.
 *
 * <p>This class allows {@link Reader Reader&lt;R, A&gt;} to be used as a monad. The {@code Reader}
 * monad allows sequencing of operations that depend on a shared, read-only environment of type
 * {@code R}.
 *
 * <p>The higher-kinded type (HKT) witness for {@code Reader<R, ?>} is {@link ReaderKind.Witness
 * Witness&lt;R&gt;}. This means that when using {@code ReaderMonad} with generic HKT abstractions,
 * a {@code Reader<R, A>} is represented as {@code Kind<ReaderKind.Witness<R>, A>}.
 *
 * @param <R> The type of the read-only environment. This environment is fixed for a given instance
 *     of {@code ReaderMonad}.
 * @see Reader
 * @see ReaderKind
 * @see ReaderKind.Witness
 * @see ReaderKindHelper
 * @see Monad
 * @see ReaderApplicative
 */
public class ReaderMonad<R> extends ReaderApplicative<R> implements Monad<ReaderKind.Witness<R>> {

  /**
   * Sequentially composes two {@link Reader} actions, passing the result of the first {@code
   * Reader} ({@code ma}) into a function {@code f} that produces the second {@code Reader}.
   *
   * <p>The {@code flatMap} operation allows dependent computations: the computation of the second
   * {@code Reader} can depend on the result of the first. Both {@code Reader}s will be evaluated
   * with the same environment {@code R}.
   *
   * @param <A> The value type of the initial {@code Reader} (represented by {@code ma}).
   * @param <B> The value type of the {@code Reader} produced by the function {@code f}.
   * @param f A function that takes a value of type {@code A} (from the first {@code Reader}) and
   *     returns a {@code Kind<ReaderKind.Witness<R>, B>} (which is a wrapped {@code Reader<R, B>}).
   *     Must not be null.
   * @param ma A {@code Kind<ReaderKind.Witness<R>, A>} representing the initial {@code Reader<R,
   *     A>}. Must not be null.
   * @return A new {@code Kind<ReaderKind.Witness<R>, B>} representing the composed {@code Reader<R,
   *     B>}. Never null. The resulting {@code Reader} when run with an environment {@code r}, will
   *     first run {@code ma} with {@code r} to get {@code a}, then apply {@code f} to {@code a} to
   *     get a new {@code Reader}, and then run that new {@code Reader} with the same environment
   *     {@code r}.
   */
  @Override
  public <A, B> @NonNull Kind<ReaderKind.Witness<R>, B> flatMap(
      @NonNull Function<A, Kind<ReaderKind.Witness<R>, B>> f,
      @NonNull Kind<ReaderKind.Witness<R>, A> ma) {

    Reader<R, A> readerA = READER.narrow(ma); // Convert Kind back to concrete Reader

    // flatMap on the concrete Reader<R,A> takes a function A -> Reader<R,B>
    // The input function f is A -> Kind<ReaderKind.Witness<R>, B>
    // So, we need to adapt f by unwrapping its result.
    Reader<R, B> readerB =
        readerA.flatMap(
            a -> {
              Kind<ReaderKind.Witness<R>, B> kindB = f.apply(a);
              return READER.narrow(kindB); // unwrap Kind<ReaderKind.Witness<R>, B> to Reader<R,B>
            });

    return READER.widen(readerB); // Wrap the resulting concrete Reader back into a Kind
  }
}
