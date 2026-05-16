// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.OptionalTAssert.assertThatOptionalT;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.optional_t.OptionalTKindHelper.OPTIONAL_T;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.optional_t.OptionalTKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.OptionalTAssert}. */
@DisplayName("OptionalTAssert showcase")
class OptionalTAssertExample {

  private final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());

  private <A> Optional<Optional<A>> unwrap(Kind<OptionalKind.Witness, Optional<A>> kind) {
    return OPTIONAL.narrow(kind);
  }

  @Test
  @DisplayName("isPresentSome() and hasSomeValue() inspect the inner Optional")
  void someInsidePresent() {
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> kind =
        OPTIONAL_T.widen(OptionalT.some(outerMonad, "value"));

    assertThatOptionalT(kind, this::unwrap).isPresentSome().hasSomeValue("value");
  }

  @Test
  @DisplayName("isPresentNone() asserts outer is present but inner is empty")
  void noneInsidePresent() {
    Kind<OptionalTKind.Witness<OptionalKind.Witness>, String> kind =
        OPTIONAL_T.widen(OptionalT.none(outerMonad));

    assertThatOptionalT(kind, this::unwrap).isPresentNone();
  }
}
