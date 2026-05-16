// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.EitherTAssert.assertThatEitherT;
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Showcase for {@link org.higherkindedj.hkt.assertions.EitherTAssert}.
 *
 * <p>EitherT wraps an Either inside an outer monad. Tests need: (1) an outer monad instance, (2) an
 * unwrap function that pulls the result back out into a plain {@code Optional<Either<E, A>>} (or
 * whatever the outer monad's "value form" is) so the assertion can inspect it.
 */
@DisplayName("EitherTAssert showcase")
class EitherTAssertExample {

  private final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());

  /** Pulls the EitherT result out of the outer Optional monad so the assertion can read it. */
  private <E, A> Optional<Either<E, A>> unwrap(Kind<OptionalKind.Witness, Either<E, A>> kind) {
    return OPTIONAL.narrow(kind);
  }

  @Test
  @DisplayName("isPresentRight() and hasRightValue() inspect the success branch")
  void rightInsideOuterPresent() {
    Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> kind =
        EITHER_T.widen(EitherT.right(outerMonad, 42));

    assertThatEitherT(kind, this::unwrap).isPresentRight().hasRightValue(42);
  }

  @Test
  @DisplayName("isPresentLeft() and hasLeftValue() inspect the failure branch")
  void leftInsideOuterPresent() {
    Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> kind =
        EITHER_T.widen(EitherT.left(outerMonad, "not found"));

    assertThatEitherT(kind, this::unwrap).isPresentLeft().hasLeftValue("not found");
  }

  @Test
  @DisplayName("isEmpty() asserts the outer monad has no value at all")
  void outerEmpty() {
    Kind<OptionalKind.Witness, Either<String, Integer>> emptyOuter =
        OPTIONAL.widen(Optional.empty());
    Kind<EitherTKind.Witness<OptionalKind.Witness, String>, Integer> kind =
        EITHER_T.widen(EitherT.fromKind(emptyOuter));

    assertThatEitherT(kind, this::unwrap).isEmpty();
  }
}
