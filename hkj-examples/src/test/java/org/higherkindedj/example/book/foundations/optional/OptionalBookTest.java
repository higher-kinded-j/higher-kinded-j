// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.optional.OptionalBook.ADA;
import static org.higherkindedj.example.book.foundations.optional.OptionalBook.CHARLES;
import static org.higherkindedj.example.book.foundations.optional.OptionalBook.contact;
import static org.higherkindedj.example.book.foundations.optional.OptionalBook.greeting;
import static org.higherkindedj.example.book.foundations.optional.OptionalBook.secondAddress;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Optional page makes about OptionalPath and Optional's instance. */
@DisplayName("Optional: Java's Optional in generic code")
class OptionalBookTest {

  private static final MonadError<OptionalKind.Witness, Unit> OPTIONALS =
      Instances.monadError(optional());

  @Nested
  @DisplayName("on an OptionalPath")
  class OnAnOptionalPath {

    @Test
    @DisplayName("greeting uses the nickname when there is one")
    void greetingUsesTheNickname() {
      assertThat(greeting(ADA)).isEqualTo("Hello, Countess");
      assertThat(greeting(CHARLES)).isEqualTo("Hello, Charles");
    }

    @Test
    @DisplayName("secondAddress gives the address, or an empty Optional")
    void secondAddressGivesTheAddressOrEmpty() {
      assertThat(secondAddress(CHARLES).run()).isEqualTo(Optional.of("charles@example.org"));
      assertThat(secondAddress(ADA).run()).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("toMaybePath and orElsePath bridge and recover")
    void toMaybePathAndOrElsePath() {
      assertThat(secondAddress(CHARLES).toMaybePath().run())
          .isEqualTo(Maybe.just("charles@example.org"));
      assertThat(secondAddress(ADA).orElsePath("none").run()).isEqualTo(Optional.of("none"));
      assertThat(secondAddress(CHARLES).orElsePath("none").run())
          .isEqualTo(Optional.of("charles@example.org"));
      assertThat(Path.absent().isEmpty()).isTrue();
      assertThat(Path.present("x").isPresent()).isTrue();
    }

    @Test
    @DisplayName("via chains a step, filter keeps a passing value, toEitherPath gives a reason")
    void viaFilterAndToEitherPath() {
      assertThat(Path.present("Ada").via(name -> Path.present(name.length())).run())
          .isEqualTo(Optional.of(3));
      assertThat(Path.present("Ada").filter(name -> name.length() > 5).run())
          .isEqualTo(Optional.empty());
      assertThat(Path.present("Ada").filter(name -> name.length() == 3).run())
          .isEqualTo(Optional.of("Ada"));
      assertThat(Path.present("Ada").map(name -> (String) null).run()).isEqualTo(Optional.empty());
      assertThat(secondAddress(ADA).toEitherPath("no second address").run())
          .isEqualTo(Either.left("no second address"));
    }
  }

  @Nested
  @DisplayName("over Kind")
  class OverKind {

    @Test
    @DisplayName(
        "the generic method gives the same answer through Optional's and Maybe's instances")
    void theGenericMethodWorksForBoth() {
      MonadError<MaybeKind.Witness, Unit> maybes = Instances.monadError(maybe());

      assertThat(OPTIONAL.narrow(contact(OPTIONALS, OPTIONAL.widen(CHARLES.altEmail()), "none")))
          .isEqualTo(Optional.of("charles@example.org"));
      assertThat(OPTIONAL.narrow(contact(OPTIONALS, OPTIONAL.widen(ADA.altEmail()), "none")))
          .isEqualTo(Optional.of("none"));
      assertThat(
              MAYBE.narrow(
                  contact(maybes, MAYBE.widen(Maybe.fromOptional(ADA.altEmail())), "none")))
          .isEqualTo(Maybe.just("none"));
    }

    @Test
    @DisplayName("widening wraps the Optional in a new holder each time, and narrowing unwraps it")
    void wideningAllocatesAHolder() {
      Optional<EmailAddress> email = CHARLES.altEmail();
      Kind<OptionalKind.Witness, EmailAddress> once = OPTIONAL.widen(email);
      Kind<OptionalKind.Witness, EmailAddress> twice = OPTIONAL.widen(email);

      assertThat(once).isNotSameAs(twice);
      assertThat(OPTIONAL.narrow(once)).isSameAs(email);
    }

    @Test
    @DisplayName("of(null) and a map that returns null give an empty Optional")
    void nullGivesEmpty() {
      assertThat(OPTIONAL.narrow(OPTIONALS.of(null))).isEqualTo(Optional.empty());
      assertThat(OPTIONAL.narrow(OPTIONALS.map(n -> null, OPTIONAL.widen(Optional.of(1)))))
          .isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("raiseError gives an empty Optional, and recover replaces it")
    void raiseErrorAndRecover() {
      Kind<OptionalKind.Witness, String> empty = OPTIONALS.raiseError(Unit.INSTANCE);

      assertThat(OPTIONAL.narrow(empty)).isEqualTo(Optional.empty());
      assertThat(OPTIONAL.narrow(OPTIONALS.recover(empty, "fallback")))
          .isEqualTo(Optional.of("fallback"));
    }
  }
}
