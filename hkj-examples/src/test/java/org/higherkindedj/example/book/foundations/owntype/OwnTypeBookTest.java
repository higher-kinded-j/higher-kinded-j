// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.owntype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.owntype.OwnTypeBook.path;
import static org.higherkindedj.example.book.foundations.owntype.OwnTypeBook.verified;
import static org.higherkindedj.example.book.foundations.owntype.ResultKindHelper.RESULT;
import static org.higherkindedj.example.book.foundations.owntype.SetKindHelper.SET;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.example.book.foundations.intro.FoundationsIntroBook;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.KindHelperLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Holds the claims the Adding Your Own Type page makes, and proves Result's instance lawful. */
@DisplayName("Adding Your Own Type: Result, its instance, its laws and its Path")
class OwnTypeBookTest {

  private static final Order ORDER =
      FoundationsIntroBook.order(
          "00000000-0000-0000-0000-000000000001",
          List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))));

  // ANCHOR: laws
  static Stream<Kind<ResultKind.Witness, Order>> results() {
    return Stream.of(
        RESULT.widen(Result.ok(ORDER)), RESULT.widen(Result.failed("order not found")));
  }

  @ParameterizedTest
  @MethodSource("results")
  @DisplayName("Result's helper and Monad keep their laws, on an Ok and on a Failed")
  void resultKeepsItsLaws(Kind<ResultKind.Witness, Order> fa) {
    Monad<ResultKind.Witness> monad = ResultMonad.INSTANCE;
    var same = KindEquivalence.<ResultKind.Witness>byEqualsAfter(RESULT::narrow);
    Function<Order, Kind<ResultKind.Witness, Customer>> findCustomer =
        order -> RESULT.widen(Result.ok(order.customer()));
    Function<Customer, Kind<ResultKind.Witness, EmailAddress>> verifyEmail =
        customer -> RESULT.widen(Result.failed("email not verified"));

    KindHelperLaws.assertRoundTrip(RESULT.narrow(fa), RESULT::widen, RESULT::narrow);
    FunctorLaws.assertIdentity(monad, fa, same);
    FunctorLaws.assertComposition(monad, fa, Order::customer, Customer::email, same);
    MonadLaws.assertLeftIdentity(monad, ORDER, findCustomer, same);
    MonadLaws.assertLeftIdentity(monad, ORDER.customer(), verifyEmail, same);
    MonadLaws.assertRightIdentity(monad, fa, same);
    MonadLaws.assertAssociativity(monad, fa, findCustomer, verifyEmail, same);
  }

  // ANCHOR_END: laws

  @Test
  @DisplayName("Result's ap keeps the Applicative laws")
  void resultKeepsTheApplicativeLaws() {
    Monad<ResultKind.Witness> monad = ResultMonad.INSTANCE;
    var same = KindEquivalence.<ResultKind.Witness>byEqualsAfter(RESULT::narrow);
    Kind<ResultKind.Witness, Function<Order, Customer>> customer =
        RESULT.widen(Result.ok(Order::customer));
    Kind<ResultKind.Witness, Function<Customer, EmailAddress>> email =
        RESULT.widen(Result.failed("no email"));

    for (Kind<ResultKind.Witness, Order> fa : results().toList()) {
      ApplicativeLaws.assertIdentity(monad, fa, same);
      ApplicativeLaws.assertComposition(monad, email, customer, fa, same);
    }
    ApplicativeLaws.assertHomomorphism(monad, ORDER, Order::customer, same);
    ApplicativeLaws.assertInterchange(monad, customer, ORDER, same);
  }

  @Test
  @DisplayName("ap stops at the first failure: when both fail, the function's reason wins")
  void apStopsAtTheFirstFailure() {
    Kind<ResultKind.Witness, Function<Order, Customer>> noFunction =
        RESULT.widen(Result.failed("no function"));
    Kind<ResultKind.Witness, Order> noOrder = RESULT.widen(Result.failed("order not found"));

    assertThat(RESULT.narrow(ResultMonad.INSTANCE.ap(noFunction, noOrder)))
        .isEqualTo(Result.failed("no function"));
  }

  @Test
  @DisplayName("flatMap refuses a step that returns null, and ap a null Kind")
  void nullIsRefused() {
    Kind<ResultKind.Witness, Order> found = RESULT.widen(Result.ok(ORDER));
    Kind<ResultKind.Witness, Function<Order, Customer>> noFunction =
        RESULT.widen(Result.failed("no function"));

    assertThatThrownBy(() -> ResultMonad.INSTANCE.flatMap(order -> null, found))
        .isInstanceOf(KindUnwrapException.class);
    assertThatThrownBy(() -> ResultMonad.INSTANCE.ap(noFunction, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> ResultMonad.INSTANCE.of(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("generic code takes the instance: totals, and a For comprehension")
  void genericCodeTakesTheInstance() {
    Result<BigDecimal> total =
        RESULT.narrow(
            FoundationsIntroBook.totals(ResultMonad.INSTANCE, RESULT.widen(Result.ok(ORDER))));
    Result<String> receipt =
        RESULT.narrow(
            For.from(ResultMonad.INSTANCE, RESULT.widen(Result.ok(ORDER)))
                .from(placed -> RESULT.widen(verified(placed.customer().email())))
                .yield((placed, email) -> "order " + placed.id() + " to " + email));

    assertThat(total).isEqualTo(Result.ok(new BigDecimal("40.00")));
    assertThat(receipt)
        .isEqualTo(Result.ok("order 00000000-0000-0000-0000-000000000001 to ada@example.com"));
  }

  @Test
  @DisplayName("a Result is its own Kind: widen hands back the same object")
  void resultWidensToItself() {
    Result<Order> found = Result.ok(ORDER);

    assertThat(RESULT.widen(found)).isSameAs(found);
  }

  @Test
  @DisplayName("narrow refuses null and a Kind that is not a Result")
  void narrowRefusesWhatIsNotAResult() {
    record NotAResult<A>() implements ResultKind<A> {}

    assertThatThrownBy(() -> RESULT.narrow(null)).isInstanceOf(KindUnwrapException.class);
    assertThatThrownBy(() -> RESULT.narrow(new NotAResult<Order>()))
        .isInstanceOf(KindUnwrapException.class);
  }

  @Test
  @DisplayName("a Set widens into a new holder, and narrow gives back the same Set")
  void setRoundTripsThroughAHolder() {
    Set<String> skus = Set.of("LAMP", "BULB");

    assertThat(SET.widen(skus)).isNotSameAs(skus).isNotSameAs(SET.widen(skus));
    KindHelperLaws.assertRoundTrip(skus, SET::widen, SET::narrow);
    assertThatThrownBy(() -> SET.narrow(null)).isInstanceOf(KindUnwrapException.class);
  }

  @Test
  @DisplayName("as a Path, an Ok runs every step and a Failed skips them")
  void resultAsAPath() {
    Result<String> email =
        RESULT.narrow(
            path(Result.ok(ORDER))
                .map(Order::customer)
                .via(customer -> path(verified(customer.email())))
                .runKind());
    Result<String> missing =
        RESULT.narrow(
            path(Result.<Order>failed("order not found"))
                .map(Order::customer)
                .via(customer -> path(verified(customer.email())))
                .runKind());

    assertThat(email).isEqualTo(Result.ok("ada@example.com"));
    assertThat(missing).isEqualTo(Result.failed("order not found"));
    assertThat(verified(new EmailAddress("ada@elsewhere.org")))
        .isEqualTo(Result.failed("email not verified"));
  }

  @Test
  @DisplayName("built from a Monad alone, the Path's recover and recoverWith throw")
  void recoverNeedsAMonadError() {
    assertThatThrownBy(() -> path(Result.ok(ORDER)).recover(error -> ORDER))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> path(Result.ok(ORDER)).recoverWith(error -> path(Result.ok(ORDER))))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
