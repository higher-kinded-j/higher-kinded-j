// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.laws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Instances.monad;
import static org.higherkindedj.hkt.instances.Instances.monadError;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.math.BigDecimal;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;
import org.higherkindedj.hkt.writer.WriterMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Holds the claims the Laws page makes about where an instance bends a law: the {@code double}
 * monoids round and lose the sign of zero, and a {@code Writer} built on one inherits both; {@code
 * bigDecimalAddition}'s identity holds by {@code equals} only at scale zero or more; {@code Try}'s
 * {@code flatMap} catches a step that throws; {@code CompletableFuture}'s handler receives a raised
 * {@code CompletionException}'s cause; and {@code Maybe}'s {@code of(null)} fails left identity.
 * Each bend is checked beside a case that keeps the law, so the failure comes from the bend and not
 * from the equality.
 */
@DisplayName("Laws: where an instance bends a law, beside a case that keeps it")
class LawsBookTest {

  private static final Monoid<Double> SUM = Monoids.doubleAddition();

  private static final Monoid<Double> PRODUCT = Monoids.doubleMultiplication();

  @Test
  @DisplayName("doubleAddition gives two sums for 0.1, 0.2 and 0.3, one for each grouping")
  void additionIsNotAssociative() {
    assertThat(SUM.combine(SUM.combine(0.1, 0.2), 0.3)).isEqualTo(0.6000000000000001);
    assertThat(SUM.combine(0.1, SUM.combine(0.2, 0.3))).isEqualTo(0.6);
  }

  @Test
  @DisplayName("doubleMultiplication gives two products for 0.1, 0.2 and 0.3")
  void multiplicationIsNotAssociative() {
    assertThat(PRODUCT.combine(PRODUCT.combine(0.1, 0.2), 0.3)).isEqualTo(0.006000000000000001);
    assertThat(PRODUCT.combine(0.1, PRODUCT.combine(0.2, 0.3))).isEqualTo(0.006);
  }

  @Test
  @DisplayName("doubleAddition's empty value turns -0.0 into 0.0, on either side")
  void additionIdentityLosesTheSignOfZero() {
    Double left = SUM.combine(SUM.empty(), -0.0);
    Double right = SUM.combine(-0.0, SUM.empty());

    assertThat(left.equals(-0.0)).as("0.0 + -0.0 equals -0.0").isFalse();
    assertThat(right.equals(-0.0)).as("-0.0 + 0.0 equals -0.0").isFalse();
    assertThat(left == -0.0).as("0.0 + -0.0 == -0.0, as numbers").isTrue();
    assertThat(right == -0.0).as("-0.0 + 0.0 == -0.0, as numbers").isTrue();
  }

  @Test
  @DisplayName("a Writer logging doubleAddition sums fails associativity and right identity")
  void aWriterInheritsTheBend() {
    var writer = new WriterMonad<>(SUM);
    var same = KindEquivalence.<WriterKind.Witness<Double>>byEqualsAfter(WRITER::narrow);

    assertThatThrownBy(
            () ->
                MonadLaws.assertAssociativity(
                    writer, logged(0.1), status -> logged(0.2), status -> logged(0.3), same))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Monad associativity");
    assertThatThrownBy(() -> MonadLaws.assertRightIdentity(writer, logged(-0.0), same))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Monad right identity");

    MonadLaws.assertAssociativity(
        writer, logged(0.5), status -> logged(0.25), status -> logged(0.125), same);
    MonadLaws.assertRightIdentity(writer, logged(0.5), same);
  }

  @Test
  @DisplayName("bigDecimalAddition's ZERO turns 1E+3 into 1000, equal by compareTo only")
  void bigDecimalIdentityHoldsByEqualsAtScaleZeroOrMore() {
    Monoid<BigDecimal> money = Monoids.bigDecimalAddition();
    BigDecimal thousand = new BigDecimal("1E+3");
    BigDecimal price = new BigDecimal("1.50");

    BigDecimal sum = money.combine(money.empty(), thousand);
    assertThat(sum.equals(thousand)).as("ZERO + 1E+3 equals 1E+3").isFalse();
    assertThat(sum.compareTo(thousand)).as("ZERO + 1E+3 compared with 1E+3").isZero();
    assertThat(sum).hasToString("1000");
    assertThat(money.combine(money.empty(), price)).isEqualTo(price);
  }

  @Test
  @DisplayName(
      "Try's flatMap turns a step that throws into a Failure, where the step itself throws")
  void tryCatchesAThrowingStep() {
    Monad<TryKind.Witness> tries = monad(try_());
    IllegalStateException boom = new IllegalStateException("no stock");
    Function<String, Kind<TryKind.Witness, String>> reserve =
        sku -> {
          throw boom;
        };

    Try<String> chained = TRY.narrow(tries.flatMap(reserve, tries.of("LAMP")));

    assertThat(chained).isEqualTo(Try.failure(boom));
    assertThatThrownBy(() -> reserve.apply("LAMP")).isSameAs(boom);
    MonadLaws.assertLeftIdentity(
        tries,
        "LAMP",
        sku -> TRY.widen(Try.success(sku + " reserved")),
        KindEquivalence.byEqualsAfter(TRY::narrow));
  }

  @Test
  @DisplayName("CompletableFuture's handler receives a raised CompletionException's cause")
  void completableFutureHandsTheHandlerTheCause() {
    MonadError<CompletableFutureKind.Witness, Throwable> futures = monadError(completableFuture());
    IllegalStateException cause = new IllegalStateException("payment refused");
    CompletionException raised = new CompletionException(cause);
    Function<Throwable, Kind<CompletableFutureKind.Witness, Throwable>> seen = futures::of;

    Throwable handled = FUTURE.join(futures.handleErrorWith(futures.raiseError(raised), seen));

    assertThat(handled).isSameAs(cause);
    assertThat(FUTURE.join(seen.apply(raised))).isSameAs(raised);
    assertThat(FUTURE.join(futures.handleErrorWith(futures.raiseError(cause), seen)))
        .isSameAs(cause);
  }

  @Test
  @DisplayName(
      "Maybe's of(null) is Nothing, so a step that turns null into a value fails left identity")
  void aNullValueFailsLeftIdentity() {
    Function<String, Kind<MaybeKind.Witness, String>> orUnknown =
        email -> MAYBE.widen(Maybe.just(email == null ? "unknown" : email));
    var same = KindEquivalence.<MaybeKind.Witness>byEqualsAfter(MAYBE::narrow);

    assertThat(MAYBE.narrow(monad(maybe()).of(null))).isEqualTo(Maybe.nothing());
    assertThatThrownBy(() -> MonadLaws.assertLeftIdentity(monad(maybe()), null, orUnknown, same))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Monad left identity");
    MonadLaws.assertLeftIdentity(monad(maybe()), "ada@example.com", orUnknown, same);
  }

  private static Kind<WriterKind.Witness<Double>, String> logged(double amount) {
    return WRITER.widen(Writer.of(amount, "logged"));
  }
}
