// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks the laws that the book's Foundations type class pages state, with {@code hkj-test}'s law
 * helpers where it has one, and by hand where it has none. Each page {@code {{#include}}}s the
 * region for its own type class.
 */
@DisplayName("Foundations: the type class laws each page states")
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

  // ANCHOR: applicative_laws
  void assertApplicativeLaws(
      Validated<List<String>, Order> order,
      Validated<List<String>, Function<Order, Customer>> customer,
      Validated<List<String>, Function<Customer, EmailAddress>> email) {
    Applicative<ValidatedKind.Witness<List<String>>> applicative =
        Instances.validated(Semigroups.list());
    var same =
        KindEquivalence.<ValidatedKind.Witness<List<String>>>byEqualsAfter(VALIDATED::narrow);
    ApplicativeLaws.assertIdentity(applicative, VALIDATED.widen(order), same);
    ApplicativeLaws.assertHomomorphism(applicative, ORDER, Order::customer, same);
    ApplicativeLaws.assertInterchange(applicative, VALIDATED.widen(customer), ORDER, same);
    ApplicativeLaws.assertComposition(
        applicative,
        VALIDATED.widen(email),
        VALIDATED.widen(customer),
        VALIDATED.widen(order),
        same);
  }

  @Test
  @DisplayName("Validated's Applicative keeps all four laws when every value is Valid")
  void validatedApplicativeLawsWhenValid() {
    assertApplicativeLaws(
        Validated.valid(ORDER), Validated.valid(Order::customer), Validated.valid(Customer::email));
  }

  @Test
  @DisplayName("Validated's Applicative keeps all four laws when every value is Invalid")
  void validatedApplicativeLawsWhenInvalid() {
    assertApplicativeLaws(
        Validated.invalid(List.of("order not found")),
        Validated.invalid(List.of("no customer")),
        Validated.invalid(List.of("no email")));
  }

  // ANCHOR_END: applicative_laws

  // ANCHOR: monad_laws
  static Stream<Kind<EitherKind.Witness<String>, Order>> eitherOrders() {
    return Stream.of(
        EITHER.widen(Either.right(ORDER)), EITHER.widen(Either.left("order not found")));
  }

  @ParameterizedTest
  @MethodSource("eitherOrders")
  @DisplayName("Either's Monad keeps all three laws, on a Right and on a Left")
  void eitherMonadLaws(Kind<EitherKind.Witness<String>, Order> fa) {
    Monad<EitherKind.Witness<String>> monad = Instances.monad(either());
    var same = KindEquivalence.<EitherKind.Witness<String>>byEqualsAfter(EITHER::narrow);
    Function<Order, Kind<EitherKind.Witness<String>, Customer>> findCustomer =
        order -> EITHER.widen(Either.right(order.customer()));
    Function<Customer, Kind<EitherKind.Witness<String>, EmailAddress>> verifyEmail =
        customer -> EITHER.widen(Either.left("email not verified"));
    MonadLaws.assertLeftIdentity(monad, ORDER, findCustomer, same);
    MonadLaws.assertLeftIdentity(monad, ORDER.customer(), verifyEmail, same);
    MonadLaws.assertRightIdentity(monad, fa, same);
    MonadLaws.assertAssociativity(monad, fa, findCustomer, verifyEmail, same);
  }

  // ANCHOR_END: monad_laws

  // ANCHOR: monad_error_laws
  <F extends WitnessArity<TypeArity.Unary>, E> void assertMonadErrorLaws(
      MonadError<F, E> monadError, E error, BiPredicate<Kind<F, ?>, Kind<F, ?>> same) {
    Function<E, Kind<F, E>> giveBackTheError = monadError::of;
    Function<E, Kind<F, String>> handled = raised -> monadError.of("handled");
    Function<Order, Kind<F, Customer>> findCustomer = order -> monadError.of(order.customer());

    // handling a raised error is the handler, given that same error
    assertThat(
            same.test(
                monadError.handleErrorWith(monadError.<E>raiseError(error), giveBackTheError),
                giveBackTheError.apply(error)))
        .isTrue();
    // a success is never handled
    assertThat(
            same.test(
                monadError.handleErrorWith(monadError.of("kept"), handled), monadError.of("kept")))
        .isTrue();
    // a raised error skips every step
    assertThat(
            same.test(
                monadError.flatMap(findCustomer, monadError.<Order>raiseError(error)),
                monadError.raiseError(error)))
        .isTrue();
  }

  @Test
  @DisplayName("Either's, Try's and Maybe's MonadError keep the laws that tie raising to handling")
  void monadErrorLaws() {
    MonadError<EitherKind.Witness<String>, String> eitherErrors = Instances.monadError(either());
    MonadError<TryKind.Witness, Throwable> tryErrors = Instances.monadError(try_());
    MonadError<MaybeKind.Witness, Unit> maybeErrors = Instances.monadError(maybe());

    assertMonadErrorLaws(
        eitherErrors,
        "order not found",
        KindEquivalence.<EitherKind.Witness<String>>byEqualsAfter(EITHER::narrow));
    assertMonadErrorLaws(
        tryErrors,
        new IllegalStateException("order not found"),
        KindEquivalence.<TryKind.Witness>byEqualsAfter(TRY::narrow));
    assertMonadErrorLaws(
        maybeErrors,
        Unit.INSTANCE,
        KindEquivalence.<MaybeKind.Witness>byEqualsAfter(MAYBE::narrow));
  }

  // ANCHOR_END: monad_error_laws
}
