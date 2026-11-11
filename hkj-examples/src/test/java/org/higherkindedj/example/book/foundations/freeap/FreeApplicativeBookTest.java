// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.freeap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.example.book.foundations.OrderOpKindHelper.ORDER_OP;
import static org.higherkindedj.example.book.foundations.freeap.FreeApplicativeBook.BASKET;
import static org.higherkindedj.example.book.foundations.freeap.FreeApplicativeBook.CATALOGUE;
import static org.higherkindedj.example.book.foundations.freeap.FreeApplicativeBook.LIMIT;
import static org.higherkindedj.example.book.foundations.freeap.FreeApplicativeBook.ORDER;
import static org.higherkindedj.example.book.foundations.freeap.FreeApplicativeBook.ORDER_OPS;
import static org.higherkindedj.example.book.foundations.freeap.FreeApplicativeBook.basketTotal;
import static org.higherkindedj.example.book.foundations.freeap.FreeApplicativeBook.cancelIfTotalOver;
import static org.higherkindedj.example.book.foundations.freeap.FreeApplicativeBook.priceInOneCall;
import static org.higherkindedj.example.book.foundations.freeap.FreeApplicativeBook.priceOf;
import static org.higherkindedj.hkt.free_ap.FreeApKindHelper.FREE_AP;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.higherkindedj.example.book.foundations.OrderOp;
import org.higherkindedj.example.book.foundations.OrderOpInterpreter;
import org.higherkindedj.example.book.foundations.OrderOpKind;
import org.higherkindedj.example.book.foundations.OrderOpOps;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.FreeApPath;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.free_ap.FreeApApplicative;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Free Applicative page makes. */
@DisplayName("Free Applicative: a program of independent instructions, seen whole before it runs")
class FreeApplicativeBookTest {

  /** Prices from the catalogue, and records each SKU in the order it is asked for. */
  private static final class RecordedPrices extends OrderOpInterpreter<IdKind.Witness> {
    final List<String> asked = new ArrayList<>();

    @Override
    protected <A> Kind<IdKind.Witness, A> handlePriceOf(OrderOp.PriceOf<A> op) {
      asked.add(op.sku());
      return ID.widen(Id.of(op.k().apply(CATALOGUE.get(op.sku()))));
    }

    @Override
    protected <A> Kind<IdKind.Witness, A> handleCancel(OrderOp.Cancel<A> op) {
      return ID.widen(Id.of(op.k().apply(Unit.INSTANCE)));
    }
  }

  private static LineItem line(String sku, int quantity) {
    return new LineItem(sku, quantity, BigDecimal.ONE);
  }

  private static BigDecimal runInId(
      FreeApPath<OrderOpKind.Witness, BigDecimal> program,
      OrderOpInterpreter<IdKind.Witness> interpreter) {
    return ID.narrow(program.foldMap(interpreter, Instances.monad(id())).runKind()).value();
  }

  @Nested
  @DisplayName("Instructions held as data")
  class HeldAsData {

    @Test
    @DisplayName("building and reading the program prices nothing; foldMap prices each line")
    void readingRunsNothing() {
      RecordedPrices prices = new RecordedPrices();
      FreeApPath<OrderOpKind.Witness, BigDecimal> program = basketTotal(BASKET);

      FreeApAnalyzer.countOperations(program.toFreeAp());
      FreeApAnalyzer.analyseWith(
          program.toFreeAp(), new FreeApplicativeBook.SkusToPrice(), Monoids.list());
      assertThat(prices.asked).isEmpty();

      assertThat(runInId(program, prices)).isEqualByComparingTo("50.00");
      assertThat(prices.asked).containsExactly("LAMP", "BULB");
    }

    @Test
    @DisplayName(
        "a line's total is today's price times its quantity, not the price it was added at")
    void linesArePricedAgain() {
      assertThat(runInId(basketTotal(List.of(line("BULB", 4))), new RecordedPrices()))
          .isEqualByComparingTo("10.00");
      assertThat(runInId(basketTotal(List.of()), new RecordedPrices())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("the interpreter meets the instructions in the order the lines were written")
    void interpreterMeetsInstructionsInOrder() {
      RecordedPrices prices = new RecordedPrices();

      runInId(basketTotal(List.of(line("BULB", 1), line("LAMP", 1), line("BULB", 2))), prices);

      assertThat(prices.asked).containsExactly("BULB", "LAMP", "BULB");
    }

    @Test
    @DisplayName("a basket of ten thousand lines is read and priced without overflowing the stack")
    void aLongBasketDoesNotOverflow() {
      List<LineItem> lines = IntStream.range(0, 10_000).mapToObj(_ -> line("BULB", 1)).toList();
      FreeApPath<OrderOpKind.Witness, BigDecimal> program = basketTotal(lines);

      assertThat(FreeApAnalyzer.countOperations(program.toFreeAp())).isEqualTo(10_000);
      assertThat(
              FreeApAnalyzer.analyseWith(
                  program.toFreeAp(), new FreeApplicativeBook.SkusToPrice(), Monoids.list()))
          .hasSize(10_000);
      assertThat(runInId(program, new RecordedPrices())).isEqualByComparingTo("25000.00");
    }
  }

  @Nested
  @DisplayName("Reading the program before it runs")
  class Reading {

    @Test
    @DisplayName("the analysis counts one instruction per line and lists the SKUs in order")
    void countsAndListsSkus() {
      FreeAp<OrderOpKind.Witness, BigDecimal> program =
          basketTotal(List.of(line("RUG", 1), line("LAMP", 2), line("BULB", 3))).toFreeAp();

      assertThat(FreeApAnalyzer.countOperations(program)).isEqualTo(3);
      assertThat(
              FreeApAnalyzer.analyseWith(
                  program, new FreeApplicativeBook.SkusToPrice(), Monoids.list()))
          .containsExactly("RUG", "LAMP", "BULB");
    }

    @Test
    @DisplayName("the analysis finds no cancellation in the basket's program")
    void noCancellation() {
      assertThat(
              FreeApAnalyzer.containsOperation(
                  basketTotal(BASKET).toFreeAp(),
                  op -> ORDER_OP.narrow(op) instanceof OrderOp.Cancel<?>))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("One catalogue call")
  class OneCall {

    @Test
    @DisplayName("prices any number of lines with one catalogue call, asking for every SKU")
    void oneCallForTheWholeBasket() {
      List<List<String>> calls = new ArrayList<>();
      FreeApplicativeBook.Catalogue catalogue =
          requested -> {
            calls.add(requested);
            return CATALOGUE;
          };
      List<LineItem> five =
          List.of(
              line("LAMP", 1), line("BULB", 2), line("LAMP", 1), line("BULB", 1), line("BULB", 1));

      BigDecimal total = priceInOneCall(basketTotal(five), catalogue);

      assertThat(total).isEqualByComparingTo("90.00");
      assertThat(calls).hasSize(1);
      assertThat(calls.getFirst()).containsExactly("LAMP", "BULB", "LAMP", "BULB", "BULB");
    }
  }

  @Nested
  @DisplayName("Every unknown SKU")
  class EveryUnknown {

    private final List<LineItem> withUnknowns =
        List.of(line("RUG", 1), line("LAMP", 1), line("VASE", 2));

    @Test
    @DisplayName("interpreted into Validated, the basket reports every unknown SKU, in order")
    void reportsEveryUnknown() {
      Validated<List<String>, BigDecimal> checked =
          VALIDATED.narrow(
              basketTotal(withUnknowns)
                  .foldMap(
                      new FreeApplicativeBook.CheckedPrices(),
                      Instances.validated(Semigroups.list()))
                  .runKind());

      assertThat(checked.isInvalid()).isTrue();
      assertThat(checked.getError()).containsExactly("no price for RUG", "no price for VASE");
    }

    @Test
    @DisplayName("a basket the catalogue knows comes back valid, with its total")
    void knownBasketIsValid() {
      Validated<List<String>, BigDecimal> checked =
          VALIDATED.narrow(
              basketTotal(BASKET)
                  .foldMap(
                      new FreeApplicativeBook.CheckedPrices(),
                      Instances.validated(Semigroups.list()))
                  .runKind());

      assertThat(checked.isValid()).isTrue();
      assertThat(checked.get()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("the same lines as a Free program stop at the first unknown SKU")
    void freeStopsAtTheFirst() {
      Function<String, FreePath<OrderOpKind.Witness, BigDecimal>> price =
          sku -> Path.free(OrderOpOps.priceOf(sku, Function.identity()), ORDER_OPS);
      FreePath<OrderOpKind.Witness, BigDecimal> asFree =
          price
              .apply("RUG")
              .zipWith(price.apply("LAMP"), BigDecimal::add)
              .zipWith(price.apply("VASE"), BigDecimal::add);

      Validated<List<String>, BigDecimal> checked =
          VALIDATED.narrow(
              asFree
                  .foldMap(
                      new FreeApplicativeBook.CheckedPrices(),
                      Instances.validated(Semigroups.list()))
                  .runKind());

      assertThat(checked.getError()).containsExactly("no price for RUG");
    }
  }

  @Nested
  @DisplayName("Inside a Free program")
  class InsideFree {

    @Test
    @DisplayName("a total over the limit cancels the order")
    void overTheLimitCancels() {
      FreeApplicativeBook.FetchedPrices store = new FreeApplicativeBook.FetchedPrices(CATALOGUE);

      boolean cancelled =
          ID.narrow(
                  cancelIfTotalOver(ORDER, BASKET, LIMIT)
                      .foldMap(store, Instances.monad(id()))
                      .runKind())
              .value();

      assertThat(cancelled).isTrue();
      assertThat(store.cancelled).containsExactly(ORDER);
    }

    @Test
    @DisplayName("a total within the limit cancels nothing")
    void withinTheLimitCancelsNothing() {
      FreeApplicativeBook.FetchedPrices store = new FreeApplicativeBook.FetchedPrices(CATALOGUE);

      boolean cancelled =
          ID.narrow(
                  cancelIfTotalOver(ORDER, BASKET, new BigDecimal("60.00"))
                      .foldMap(store, Instances.monad(id()))
                      .runKind())
              .value();

      assertThat(cancelled).isFalse();
      assertThat(store.cancelled).isEmpty();
    }

    @Test
    @DisplayName("ProgramAnalyser counts the basket as one step, with the cancel still hidden")
    void analyserCountsTheBasketAsOneStep() {
      ProgramAnalysis analysis =
          ProgramAnalyser.analyse(cancelIfTotalOver(ORDER, BASKET, LIMIT).toFree());

      assertThat(analysis.parallelScopes()).isEqualTo(1);
      assertThat(analysis.suspendCount()).isZero();
      assertThat(analysis.hasOpaqueRegions()).isTrue();
    }
  }

  @Nested
  @DisplayName("Interpreted into CompletableFuture")
  class Concurrent {

    @Test
    @DisplayName("an interpreter that starts each price with supplyAsync prices the lines at once")
    void pricesTheLinesAtOnce() throws Exception {
      // Each price waits until both have started, so they finish only if they run together
      CountDownLatch bothStarted = new CountDownLatch(2);
      List<String> started = Collections.synchronizedList(new ArrayList<>());
      OrderOpInterpreter<CompletableFutureKind.Witness> async =
          new OrderOpInterpreter<>() {
            @Override
            protected <A> Kind<CompletableFutureKind.Witness, A> handlePriceOf(
                OrderOp.PriceOf<A> op) {
              return FUTURE.widen(
                  CompletableFuture.supplyAsync(
                      () -> {
                        started.add(op.sku());
                        bothStarted.countDown();
                        await(bothStarted);
                        return op.k().apply(CATALOGUE.get(op.sku()));
                      }));
            }

            @Override
            protected <A> Kind<CompletableFutureKind.Witness, A> handleCancel(
                OrderOp.Cancel<A> op) {
              return FUTURE.widen(CompletableFuture.completedFuture(op.k().apply(Unit.INSTANCE)));
            }
          };

      CompletableFuture<BigDecimal> total =
          FUTURE.narrow(
              basketTotal(BASKET).foldMapKind(async, Instances.applicative(completableFuture())));

      assertThat(total.get(5, TimeUnit.SECONDS)).isEqualByComparingTo("50.00");
      assertThat(started).containsExactlyInAnyOrder("LAMP", "BULB");
    }

    private static void await(CountDownLatch latch) {
      try {
        if (!latch.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("the other price never started");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
  }

  @Nested
  @DisplayName("A program always gives a value")
  class NeverNull {

    @Test
    @DisplayName("a null pure value is refused at the call, on a Path and on a FreeAp")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void nullPureIsRefused() {
      assertThatNullPointerException().isThrownBy(() -> Path.freeApPure(null, ORDER_OPS));
      assertThatNullPointerException().isThrownBy(() -> FreeAp.pure(null));
    }

    @Test
    @DisplayName("a zipWith function that returns null fails the program when it is interpreted")
    void nullFromZipWithFailsAtInterpretation() {
      FreeApPath<OrderOpKind.Witness, BigDecimal> program =
          priceOf("LAMP").zipWith(priceOf("BULB"), (_, _) -> null);

      assertThatNullPointerException().isThrownBy(() -> runInId(program, new RecordedPrices()));
    }

    @Test
    @DisplayName("a map function that returns null fails the program when it is interpreted")
    void nullFromMapFailsAtInterpretation() {
      FreeApPath<OrderOpKind.Witness, BigDecimal> program = priceOf("LAMP").map(_ -> null);

      assertThatNullPointerException()
          .isThrownBy(() -> runInId(program, new RecordedPrices()))
          .withMessage("mapper must not return null");
    }
  }

  @Test
  @DisplayName("FreeApApplicative's map2 builds the program as FreeAp's own map2 does")
  void kindLevelMap2() {
    FreeAp<OrderOpKind.Witness, BigDecimal> lamp = priceOf("LAMP").toFreeAp();
    FreeAp<OrderOpKind.Witness, BigDecimal> bulb = priceOf("BULB").toFreeAp();
    RecordedPrices viaInstance = new RecordedPrices();
    RecordedPrices viaFreeAp = new RecordedPrices();

    BigDecimal byInstance =
        ID.narrow(
                FREE_AP
                    .narrow(
                        FreeApApplicative.<OrderOpKind.Witness>instance()
                            .map2(FREE_AP.widen(lamp), FREE_AP.widen(bulb), BigDecimal::add))
                    .foldMap(viaInstance, Instances.monad(id())))
            .value();
    BigDecimal byFreeAp =
        ID.narrow(lamp.map2(bulb, BigDecimal::add).foldMap(viaFreeAp, Instances.monad(id())))
            .value();

    assertThat(byInstance).isEqualByComparingTo("42.50").isEqualByComparingTo(byFreeAp);
    assertThat(viaInstance.asked).isEqualTo(viaFreeAp.asked).containsExactly("LAMP", "BULB");
  }

  @Test
  @DisplayName("FetchedPrices prices from the map it was given")
  void fetchedPricesUsesItsMap() {
    assertThat(
            runInId(
                priceOf("LAMP"),
                new FreeApplicativeBook.FetchedPrices(Map.of("LAMP", new BigDecimal("1.00")))))
        .isEqualByComparingTo("1.00");
  }
}
