// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.WriterTAssert.assertThatWriterT;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.WriterTAssert}. */
@DisplayName("WriterTAssert showcase")
class WriterTAssertExample {

  private final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());

  private <A> Optional<Pair<A, String>> unwrap(Kind<OptionalKind.Witness, Pair<A, String>> kind) {
    return OPTIONAL.narrow(kind);
  }

  @Test
  @DisplayName("hasPair() asserts both the value and the accumulated log")
  void inspectPair() {
    WriterT<OptionalKind.Witness, String, Integer> writerT =
        WriterT.of(outerMonad, Monoids.string(), 42);

    Kind<WriterTKind.Witness<OptionalKind.Witness, String>, Integer> kind = WRITER_T.widen(writerT);

    assertThatWriterT(kind, this::unwrap).isPresent().hasPair(42, "");
  }
}
