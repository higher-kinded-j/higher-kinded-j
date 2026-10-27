// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.encoding;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/hkts/core-concepts.html">How the Encoding
 * Works</a> page: one value carried into the {@code Kind} encoding, through a type class and back,
 * and {@code Unit} as a value for "nothing to return". The page {@code {{#include}}}s the anchored
 * regions, {@code EncodingBookTest} holds the claims the page makes, and the book's output gate
 * runs {@link #main} against the comments in the regions.
 *
 * <p>The round trip keeps the placeholder values {@code 10} and {@code "v=" + n}: it is the
 * expression the Lifting the Hood page traces line by line, so the two pages can be read side by
 * side.
 */
public final class EncodingBook {

  private EncodingBook() {}

  public static void main(String[] args) {
    // ANCHOR: roundtrip
    Monad<EitherKind.Witness<String>> monad = Instances.monad(either());

    Either<String, Integer> start = Either.right(10);
    // widen: the same Either, seen as a Kind
    Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(start);
    // flatMap takes the function first, then the Kind, and returns a Kind
    Kind<EitherKind.Witness<String>, String> result =
        monad.flatMap(i -> EITHER.widen(Either.right("v=" + i)), kind);
    // narrow: back to an Either
    Either<String, String> finalEither = EITHER.narrow(result);
    // Right(v=10)
    // ANCHOR_END: roundtrip

    // ANCHOR: unit
    // run for its effect, with nothing to return, so it gives Unit
    Kind<IOKind.Witness, Unit> confirm =
        IO_OP.delay(
            () -> {
              System.out.println("Order confirmed");
              return Unit.INSTANCE;
            });
    IO_OP.unsafeRunSync(confirm);

    // Maybe's error type is Unit, so raising it gives Nothing
    MonadError<MaybeKind.Witness, Unit> maybeErrors = Instances.monadError(maybe());
    Maybe<String> absent = MAYBE.narrow(maybeErrors.raiseError(Unit.INSTANCE));
    // Nothing
    // ANCHOR_END: unit

    System.out.println(finalEither);
    System.out.println(absent);
  }
}
