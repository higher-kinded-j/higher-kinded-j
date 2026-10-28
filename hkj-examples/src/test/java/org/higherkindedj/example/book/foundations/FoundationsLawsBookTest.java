// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks the laws that the book's Foundations type class pages state, with {@code hkj-test}'s law
 * helpers. Each page {@code {{#include}}}s the region for its own type class.
 */
@DisplayName("Foundations: the type class laws, checked with hkj-test")
class FoundationsLawsBookTest {

  private static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
          Instant.parse("2026-10-01T09:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.NEW);

  // ANCHOR: functor_laws
  static Stream<Kind<MaybeKind.Witness, Order>> maybeOrders() {
    return Stream.of(MAYBE.widen(Maybe.just(ORDER)), MAYBE.widen(Maybe.nothing()));
  }

  @ParameterizedTest
  @MethodSource("maybeOrders")
  @DisplayName("Maybe's Functor keeps both laws, on a Just and on a Nothing")
  void maybeFunctorLaws(Kind<MaybeKind.Witness, Order> fa) {
    Functor<MaybeKind.Witness> functor = Instances.functor(maybe());
    BiPredicate<Kind<MaybeKind.Witness, ?>, Kind<MaybeKind.Witness, ?>> same =
        KindEquivalence.byEqualsAfter(MAYBE::narrow);
    FunctorLaws.assertIdentity(functor, fa, same);
    FunctorLaws.assertComposition(functor, fa, Order::customer, Customer::email, same);
  }

  @Test
  @DisplayName("IO's Functor keeps both laws, comparing what each IO gives when it runs")
  void ioFunctorLaws() {
    Functor<IOKind.Witness> functor = Instances.functor(io());
    Kind<IOKind.Witness, Order> fa = IO_OP.widen(IO.delay(() -> ORDER));
    BiPredicate<Kind<IOKind.Witness, ?>, Kind<IOKind.Witness, ?>> sameResult =
        KindEquivalence.byEqualsAfter(kind -> IO_OP.narrow(kind).unsafeRunSync());
    FunctorLaws.assertIdentity(functor, fa, sameResult);
    FunctorLaws.assertComposition(functor, fa, Order::customer, Customer::email, sameResult);
  }

  // ANCHOR_END: functor_laws
}
