// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.lazy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.higherkindedj.example.book.foundations.lazy.LazyBook.ORDER;
import static org.higherkindedj.example.book.foundations.lazy.LazyBook.shippingLabel;
import static org.higherkindedj.example.book.foundations.lazy.LazyBook.view;
import static org.higherkindedj.hkt.instances.Witnesses.lazy;
import static org.higherkindedj.hkt.lazy.LazyKindHelper.LAZY;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.LazyPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.lazy.LazyKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Lazy page makes about deferring, keeping and failing. */
@DisplayName("Lazy: a value worked out once, when first asked for")
class LazyBookTest {

  @Nested
  @DisplayName("deferring and keeping")
  class DeferringAndKeeping {

    @Test
    @DisplayName("the quote is worked out on the first read, and only once")
    void theQuoteIsWorkedOutOnce() throws Throwable {
      AtomicInteger quotes = new AtomicInteger();
      LazyBook.OrderView opened = view(ORDER, quotes);

      assertThat(quotes).hasValue(0);
      assertThat(opened.shipping().isEvaluated()).isFalse();
      assertThat(opened.shipping().force()).isEqualByComparingTo("7.99");
      assertThat(opened.shipping().force()).isEqualByComparingTo("7.99");
      assertThat(quotes).hasValue(1);
      assertThat(opened.shipping().isEvaluated()).isTrue();
    }

    @Test
    @DisplayName("map and flatMap build a new Lazy without working anything out")
    void mapAndFlatMapWorkNothingOut() throws Throwable {
      AtomicInteger quotes = new AtomicInteger();
      Lazy<BigDecimal> shipping = view(ORDER, quotes).shipping();

      Lazy<String> label =
          shipping.map(cost -> "shipping " + cost).flatMap(s -> Lazy.defer(() -> s + "!"));

      assertThat(quotes).hasValue(0);
      assertThat(label.force()).isEqualTo("shipping 7.99!");
      assertThat(quotes).hasValue(1);
    }

    @Test
    @DisplayName("a failure is kept too, and the work is not tried again")
    void aFailureIsKept() {
      AtomicInteger attempts = new AtomicInteger();
      Lazy<BigDecimal> failing =
          Lazy.defer(
              () -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("no rates for LS1");
              });

      Throwable first = catchThrowable(failing::force);
      Throwable second = catchThrowable(failing::force);

      assertThat(first).hasMessage("no rates for LS1");
      assertThat(second).isSameAs(first);
      assertThat(attempts).hasValue(1);
      assertThat(failing.hasFailed()).isTrue();
    }

    @Test
    @DisplayName("forces from many threads at once still work the value out once")
    void concurrentForcesWorkItOutOnce() throws Exception {
      AtomicInteger runs = new AtomicInteger();
      Lazy<BigDecimal> slow =
          Lazy.defer(
              () -> {
                runs.incrementAndGet();
                Thread.sleep(20);
                return new BigDecimal("7.99");
              });
      CyclicBarrier start = new CyclicBarrier(8);
      List<Future<BigDecimal>> reads = new ArrayList<>();

      try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
        for (int i = 0; i < 8; i++) {
          reads.add(
              pool.submit(
                  () -> {
                    start.await();
                    try {
                      return slow.force();
                    } catch (Throwable t) {
                      throw new IllegalStateException(t);
                    }
                  }));
        }
        for (Future<BigDecimal> read : reads) {
          assertThat(read.get()).isEqualByComparingTo("7.99");
        }
      }
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("flatMap joins the subtotal and the quote, working nothing out until forced")
    void flatMapJoinsWithoutForcing() throws Throwable {
      AtomicInteger quotes = new AtomicInteger();
      Lazy<BigDecimal> total = LazyBook.totalWithShipping(ORDER, view(ORDER, quotes).shipping());

      assertThat(quotes).hasValue(0);
      assertThat(total.force()).isEqualByComparingTo("100.49");
      assertThat(quotes).hasValue(1);
    }
  }

  @Nested
  @DisplayName("values, never null")
  class ValuesNeverNull {

    @Test
    @DisplayName("now refuses null, and work that gives null fails when forced")
    void nullIsRefused() {
      Lazy<String> nothing = Lazy.defer(() -> null);

      assertThatThrownBy(() -> Lazy.now(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(nothing::force).isInstanceOf(NullPointerException.class);
      assertThat(nothing.hasFailed()).isTrue();
      assertThatThrownBy(Lazy.now("x").map(s -> (String) null)::force)
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("on a LazyPath, and over Kind")
  class OnAPathAndOverKind {

    @Test
    @DisplayName("a LazyPath works nothing out until get, then keeps the value")
    void aLazyPathWaitsForGet() {
      AtomicInteger quotes = new AtomicInteger();
      LazyPath<String> label = shippingLabel(ORDER, quotes);

      assertThat(quotes).hasValue(0);
      assertThat(label.isEvaluated()).isFalse();
      assertThat(label.get()).isEqualTo("shipping 7.99");
      assertThat(label.get()).isEqualTo("shipping 7.99");
      assertThat(quotes).hasValue(1);
    }

    @Test
    @DisplayName("toIOPath keeps the work deferred, while toMaybePath works it out at once")
    void theConversions() {
      AtomicInteger quotes = new AtomicInteger();

      IOPath<String> later = shippingLabel(ORDER, quotes).toIOPath();
      assertThat(quotes).hasValue(0);
      assertThat(later.unsafeRun()).isEqualTo("shipping 7.99");

      AtomicInteger eager = new AtomicInteger();
      MaybePath<String> now = shippingLabel(ORDER, eager).toMaybePath();
      assertThat(eager).hasValue(1);
      assertThat(now.getOrElse("none")).isEqualTo("shipping 7.99");
    }

    @Test
    @DisplayName(
        "get wraps a checked failure in a RuntimeException, and force rethrows it as it is")
    void getWrapsACheckedFailure() {
      LazyPath<String> failing =
          LazyPath.deferThrowable(
              () -> {
                throw new IOException("feed unreadable");
              });

      assertThatThrownBy(failing::get)
          .isInstanceOf(RuntimeException.class)
          .hasCauseInstanceOf(IOException.class);
      assertThatThrownBy(failing::force).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("get rethrows the exception the work threw")
    void getRethrows() {
      LazyPath<String> failing =
          LazyPath.defer(
              () -> {
                throw new IllegalStateException("no rates for LS1");
              });

      assertThatThrownBy(failing::get)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("no rates for LS1");
    }

    @Test
    @DisplayName("the instance's map works nothing out, and force gives the result")
    void theInstancesMapWaits() throws Throwable {
      AtomicInteger quotes = new AtomicInteger();
      Monad<LazyKind.Witness> deferred = Instances.monad(lazy());

      Kind<LazyKind.Witness, String> label =
          deferred.map(cost -> "shipping " + cost, LAZY.widen(view(ORDER, quotes).shipping()));

      assertThat(quotes).hasValue(0);
      assertThat(LAZY.force(label)).isEqualTo("shipping 7.99");
    }

    @Test
    @DisplayName("widening wraps the Lazy in a new holder, and narrowing unwraps it")
    void wideningAllocatesAHolder() {
      Lazy<BigDecimal> shipping = view(ORDER, new AtomicInteger()).shipping();
      Kind<LazyKind.Witness, BigDecimal> once = LAZY.widen(shipping);

      assertThat(once).isNotSameAs(LAZY.widen(shipping));
      assertThat(LAZY.narrow(once)).isSameAs(shipping);
    }
  }
}
