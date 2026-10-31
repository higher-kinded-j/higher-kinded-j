// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.maybe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.maybe.MaybeBook.ARCHIVED;
import static org.higherkindedj.example.book.foundations.maybe.MaybeBook.PLACED;
import static org.higherkindedj.example.book.foundations.maybe.MaybeBook.SHIPPED;
import static org.higherkindedj.example.book.foundations.maybe.MaybeBook.UNKNOWN;
import static org.higherkindedj.example.book.foundations.maybe.MaybeBook.deliveryAddress;
import static org.higherkindedj.example.book.foundations.maybe.MaybeBook.deliveryCity;
import static org.higherkindedj.example.book.foundations.maybe.MaybeBook.findArchived;
import static org.higherkindedj.example.book.foundations.maybe.MaybeBook.findOrder;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.higherkindedj.example.book.foundations.OrderError;
import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Maybe page makes about absence, the bridges and the null rules. */
@DisplayName("Maybe: a value, or nothing, and never null")
class MaybeBookTest {

  private static final Address LEEDS = new Address("1 High Street", "Leeds", "LS1 4AP");

  @Nested
  @DisplayName("the chain")
  class TheChain {

    @Test
    @DisplayName("gives the address of an order that has shipped")
    void givesTheAddressOfAShippedOrder() {
      assertThat(deliveryAddress(SHIPPED.id())).isEqualTo(Maybe.just(LEEDS));
    }

    @Test
    @DisplayName("gives Nothing when either lookup finds nothing")
    void givesNothingWhenALookupFindsNothing() {
      assertThat(deliveryAddress(PLACED.id())).isEqualTo(Maybe.nothing());
      assertThat(deliveryAddress(UNKNOWN)).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("never runs a later step after Nothing")
    void neverRunsALaterStep() {
      List<String> ran = new ArrayList<>();

      Maybe<String> result =
          findOrder(UNKNOWN)
              .flatMap(
                  order -> {
                    ran.add("flatMap");
                    return MaybeBook.findConsignment(order);
                  })
              .map(
                  consignment -> {
                    ran.add("map");
                    return consignment.to().city();
                  });

      assertThat(result.isNothing()).isTrue();
      assertThat(ran).isEmpty();
    }
  }

  @Nested
  @DisplayName("reading the result")
  class ReadingTheResult {

    @Test
    @DisplayName("orElse gives a fallback for Nothing")
    void orElseGivesAFallback() {
      assertThat(deliveryAddress(PLACED.id()).map(Address::city).orElse("not dispatched yet"))
          .isEqualTo("not dispatched yet");
      assertThat(deliveryAddress(SHIPPED.id()).map(Address::city).orElse("not dispatched yet"))
          .isEqualTo("Leeds");
      assertThat(Maybe.<String>nothing().orElseGet(() -> "later")).isEqualTo("later");
    }

    @Test
    @DisplayName("toEither gives Nothing a reason, eagerly or from a supplier")
    void toEitherGivesAReason() {
      assertThat(findOrder(UNKNOWN).toEither(OrderError.notFound(UNKNOWN)))
          .isEqualTo(Either.left(OrderError.notFound(UNKNOWN)));
      assertThat(findOrder(PLACED.id()).toEither(() -> OrderError.notFound(PLACED.id())))
          .isEqualTo(Either.right(PLACED));
      assertThat(findOrder(UNKNOWN).toEither(() -> OrderError.notFound(UNKNOWN)))
          .isEqualTo(Either.left(OrderError.notFound(UNKNOWN)));
    }

    @Test
    @DisplayName("get throws on Nothing")
    void getThrowsOnNothing() {
      assertThatThrownBy(() -> Maybe.nothing().get()).isInstanceOf(NoSuchElementException.class);
      assertThat(findOrder(PLACED.id()).get()).isEqualTo(PLACED);
    }
  }

  @Nested
  @DisplayName("the JDK bridges")
  class TheJdkBridges {

    @Test
    @DisplayName("toOptional and fromOptional go both ways")
    void toOptionalAndFromOptional() {
      assertThat(Maybe.just(LEEDS).toOptional()).isEqualTo(Optional.of(LEEDS));
      assertThat(Maybe.nothing().toOptional()).isEqualTo(Optional.empty());
      assertThat(Maybe.fromOptional(Optional.of(LEEDS))).isEqualTo(Maybe.just(LEEDS));
      assertThat(Maybe.fromOptional(Optional.empty())).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("stream gives zero or one element")
    void streamGivesZeroOrOneElement() {
      assertThat(Maybe.just(LEEDS).stream()).containsExactly(LEEDS);
      assertThat(Maybe.nothing().stream()).isEmpty();
    }
  }

  @Nested
  @DisplayName("values, never null")
  class ValuesNeverNull {

    @Test
    @DisplayName("just refuses null")
    void justRefusesNull() {
      assertThatThrownBy(() -> Maybe.just(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("fromNullable, a map that returns null, and the instance's of give Nothing")
    void theOtherWaysInGiveNothing() {
      MonadError<MaybeKind.Witness, Unit> absence = Instances.monadError(maybe());

      assertThat(Maybe.fromNullable(null)).isEqualTo(Maybe.nothing());
      assertThat(Maybe.just(1).map(n -> null)).isEqualTo(Maybe.nothing());
      assertThat(MAYBE.narrow(absence.of(null))).isEqualTo(Maybe.nothing());
    }
  }

  @Nested
  @DisplayName("on a MaybePath, and over Kind")
  class OnAPathAndOverKind {

    @Test
    @DisplayName("deliveryCity gives the city, or Nothing")
    void deliveryCityGivesTheCity() {
      assertThat(deliveryCity(SHIPPED.id()).run()).isEqualTo(Maybe.just("Leeds"));
      assertThat(deliveryCity(UNKNOWN).getOrElse("unknown")).isEqualTo("unknown");
    }

    @Test
    @DisplayName("handleErrorWith falls back to the archive, and raiseError gives Nothing")
    void handleErrorWithFallsBack() {
      MonadError<MaybeKind.Witness, Unit> absence = Instances.monadError(maybe());

      assertThat(
              MAYBE.narrow(
                  absence.handleErrorWith(
                      MAYBE.widen(findOrder(ARCHIVED.id())),
                      unit -> MAYBE.widen(findArchived(ARCHIVED.id())))))
          .isEqualTo(Maybe.just(ARCHIVED));
      assertThat(MAYBE.narrow(absence.raiseError(Unit.INSTANCE))).isEqualTo(Maybe.nothing());
    }

    @Test
    @DisplayName("widening returns the same object, and Nothing is one shared instance")
    void wideningReturnsTheSameObject() {
      Maybe<Address> found = deliveryAddress(SHIPPED.id());

      assertThat(MAYBE.widen(found)).isSameAs(found);
      assertThat((Object) Maybe.<String>nothing()).isSameAs(Maybe.<Integer>nothing());
    }

    @Test
    @DisplayName("Just and Nothing are not public, so code outside the library cannot name them")
    void justAndNothingAreNotPublic() {
      assertThat(Modifier.isPublic(Maybe.just(1).getClass().getModifiers())).isFalse();
      assertThat(Modifier.isPublic(Maybe.nothing().getClass().getModifiers())).isFalse();
    }
  }
}
