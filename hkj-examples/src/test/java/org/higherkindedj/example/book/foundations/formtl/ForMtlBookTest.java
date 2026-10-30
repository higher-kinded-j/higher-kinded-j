// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.formtl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.formtl.ForMtlBook.BULBS;
import static org.higherkindedj.example.book.foundations.formtl.ForMtlBook.LAMP;
import static org.higherkindedj.example.book.foundations.formtl.ForMtlBook.ORDER;
import static org.higherkindedj.example.book.foundations.formtl.ForMtlBook.addBoth;
import static org.higherkindedj.example.book.foundations.formtl.ForMtlBook.ship;
import static org.higherkindedj.example.book.foundations.formtl.ForMtlBook.toPay;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.example.book.foundations.AuditEntry;
import org.higherkindedj.example.book.foundations.Basket;
import org.higherkindedj.example.book.foundations.PricingConfig;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.reader_t.ReaderTMonadReader;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateTMonadState;
import org.higherkindedj.hkt.writer_t.WriterTMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the For with MTL Capabilities page makes. */
@DisplayName("For with MTL capabilities: each capability is a Monad, so For chains its operations")
class ForMtlBookTest {

  private static final Monad<IdKind.Witness> ID_MONAD = Instances.monad(id());
  private static final PricingConfig CONFIG =
      new PricingConfig(new BigDecimal("0.20"), new BigDecimal("4.95"), new BigDecimal("50.00"));

  private static BigDecimal priced(BigDecimal net) {
    ReaderTMonadReader<IdKind.Witness, PricingConfig> pricing = new ReaderTMonadReader<>(ID_MONAD);
    return ID.unwrap(READER_T.narrow(toPay(pricing, net)).run().apply(CONFIG));
  }

  @Test
  @DisplayName("Each capability extends Monad, so For.from takes it")
  void eachCapabilityIsAMonad() {
    assertThat(Monad.class)
        .isAssignableFrom(MonadReader.class)
        .isAssignableFrom(MonadState.class)
        .isAssignableFrom(MonadWriter.class);
  }

  @Test
  @DisplayName("The transformers provide the capabilities")
  void theTransformersProvideThem() {
    assertThat(MonadReader.class).isAssignableFrom(ReaderTMonadReader.class);
    assertThat(MonadState.class).isAssignableFrom(StateTMonadState.class);
    assertThat(MonadWriter.class).isAssignableFrom(WriterTMonad.class);
  }

  @Test
  @DisplayName("Delivery is charged below the threshold and not from it, then VAT on the whole")
  void deliveryBelowTheThreshold() {
    assertThat(priced(new BigDecimal("10.00"))).isEqualByComparingTo("17.94");
    assertThat(priced(new BigDecimal("49.99"))).isEqualByComparingTo("65.93");
    assertThat(priced(new BigDecimal("50.00"))).isEqualByComparingTo("60.00");
  }

  @Test
  @DisplayName("The same method runs over another stack: ReaderT on Maybe")
  void theCallerPicksTheStack() {
    Monad<MaybeKind.Witness> maybeMonad = Instances.monad(maybe());
    ReaderTMonadReader<MaybeKind.Witness, PricingConfig> pricing =
        new ReaderTMonadReader<>(maybeMonad);

    Maybe<BigDecimal> result =
        MAYBE.narrow(READER_T.narrow(toPay(pricing, new BigDecimal("10.00"))).run().apply(CONFIG));

    assertThat(result).isEqualTo(Maybe.just(new BigDecimal("17.94")));
  }

  @Test
  @DisplayName("The same method runs over ReaderT on CompletableFuture")
  void itRunsOverCompletableFuture() {
    ReaderTMonadReader<CompletableFutureKind.Witness, PricingConfig> pricing =
        new ReaderTMonadReader<>(Instances.monad(completableFuture()));

    CompletableFuture<BigDecimal> result =
        FUTURE.narrow(READER_T.narrow(toPay(pricing, new BigDecimal("10.00"))).run().apply(CONFIG));

    assertThat(result.join()).isEqualByComparingTo("17.94");
  }

  @Test
  @DisplayName("ReaderPath gives the same price for one environment, with no Kind")
  void readerPathGivesTheSamePrice() {
    BigDecimal net = new BigDecimal("10.00");
    ReaderPath<PricingConfig, BigDecimal> onPath =
        ReaderPath.<PricingConfig>ask()
            .map(
                config ->
                    net.add(
                            net.compareTo(config.freeDeliveryFrom()) >= 0
                                ? BigDecimal.ZERO
                                : config.deliveryCharge())
                        .multiply(BigDecimal.ONE.add(config.vatRate()))
                        .setScale(2, RoundingMode.HALF_UP));

    assertThat(onPath.run(CONFIG)).isEqualTo(priced(net));
  }

  @Test
  @DisplayName("Each state step sees the basket the step before it left")
  void eachStepSeesThePreviousBasket() {
    StateTMonadState<Basket, IdKind.Witness> basket = new StateTMonadState<>(ID_MONAD);

    StateTuple<Basket, BigDecimal> after =
        ID.unwrap(STATE_T.narrow(addBoth(basket, LAMP, BULBS)).runStateT(Basket.EMPTY));

    assertThat(after.state().lines()).containsExactly(LAMP, BULBS);
    assertThat(after.value()).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName("Each tell adds to the log in order, and the method's value comes back beside it")
  void eachTellAddsToTheLog() {
    WriterTMonad<IdKind.Witness, List<AuditEntry>> audit =
        new WriterTMonad<>(ID_MONAD, Monoids.list());

    Pair<OrderStatus, List<AuditEntry>> shipped =
        ID.unwrap(WRITER_T.narrow(ship(audit, ORDER)).run());

    assertThat(shipped.first()).isEqualTo(OrderStatus.SHIPPED);
    assertThat(shipped.second())
        .containsExactly(
            new AuditEntry(ORDER.id(), "packed"), new AuditEntry(ORDER.id(), "shipped"));
  }
}
