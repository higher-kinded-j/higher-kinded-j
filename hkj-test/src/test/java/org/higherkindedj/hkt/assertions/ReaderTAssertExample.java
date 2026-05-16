// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.ReaderTAssert.assertThatReaderT;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.reader_t.ReaderTKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.ReaderTAssert}. */
@DisplayName("ReaderTAssert showcase")
class ReaderTAssertExample {

  private final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());

  private <A> Optional<A> unwrap(Kind<OptionalKind.Witness, A> kind) {
    return OPTIONAL.narrow(kind);
  }

  @Test
  @DisplayName("whenRunWith() supplies the environment and lets you assert on the lifted value")
  void runReaderT() {
    ReaderT<OptionalKind.Witness, String, Integer> readerT =
        ReaderT.reader(outerMonad, env -> env.length());

    Kind<ReaderTKind.Witness<OptionalKind.Witness, String>, Integer> kind = READER_T.widen(readerT);

    assertThatReaderT(kind, this::unwrap).whenRunWith("hello").isPresent().hasValue(5);
  }
}
