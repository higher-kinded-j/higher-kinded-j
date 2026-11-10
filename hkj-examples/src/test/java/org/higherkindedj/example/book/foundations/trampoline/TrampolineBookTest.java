// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.trampoline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.trampoline.TrampolineBook.isEven;
import static org.higherkindedj.example.book.foundations.trampoline.TrampolineBook.isOdd;
import static org.higherkindedj.example.book.foundations.trampoline.TrampolineBook.sum;
import static org.higherkindedj.example.book.foundations.trampoline.TrampolineBook.sumPath;
import static org.higherkindedj.example.book.foundations.trampoline.TrampolineBook.sumTail;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.trampoline;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.trampoline.TrampolineKindHelper.TRAMPOLINE;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.hkt.trampoline.TrampolineKind;
import org.higherkindedj.hkt.trampoline.TrampolineMonad;
import org.higherkindedj.hkt.trampoline.TrampolineUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Trampoline page makes about running deep recursion in a loop. */
@DisplayName("Trampoline: deep recursion in a loop")
class TrampolineBookTest {

  /** Runs {@code body} on a thread with the default 1 MB stack, and gives what it threw. */
  private static Throwable onOneMegabyteStack(Runnable body) throws InterruptedException {
    AtomicReference<Throwable> thrown = new AtomicReference<>();
    Thread thread =
        new Thread(
            null,
            () -> {
              try {
                body.run();
              } catch (Throwable t) {
                thrown.set(t);
              }
            },
            "one-megabyte-stack",
            1024 * 1024);
    thread.start();
    thread.join();
    return thrown.get();
  }

  @Nested
  @DisplayName("recursion that overflows")
  class Overflow {

    @Test
    @DisplayName("the plain sum of 1,000 is 500500")
    void shallowSum() {
      assertThat(sum(1_000)).isEqualTo(500_500L);
    }

    @Test
    @DisplayName("on a 1 MB stack, 5,000 calls fit and 100,000 throw StackOverflowError")
    void deepSumOverflows() throws InterruptedException {
      assertThat(onOneMegabyteStack(() -> sum(5_000))).isNull();
      assertThat(onOneMegabyteStack(() -> sum(100_000))).isInstanceOf(StackOverflowError.class);
    }

    @Test
    @DisplayName("the StackOverflowError can be caught, though the sum is lost")
    void overflowCanBeCaught() {
      assertThatThrownBy(() -> sum(1_000_000)).isInstanceOf(StackOverflowError.class);
    }
  }

  @Nested
  @DisplayName("the same sum, held as steps")
  class HeldAsSteps {

    @Test
    @DisplayName("on a TrampolinePath, the sum of 1,000,000 is 500000500000")
    void pathSum() {
      assertThat(sumPath(1_000_000).run()).isEqualTo(500_000_500_000L);
    }

    @Test
    @DisplayName("with the total carried in the call, defer alone gives the same sum")
    void tailSum() {
      assertThat(sumTail(1_000_000, 0).run()).isEqualTo(500_000_500_000L);
    }

    @Test
    @DisplayName("the sum of 1,000,000 also runs on a 1 MB stack")
    void pathSumOnASmallStack() throws InterruptedException {
      assertThat(onOneMegabyteStack(() -> sumPath(1_000_000).run())).isNull();
    }

    @Test
    @DisplayName("nothing runs until run is called")
    void nothingRunsUntilRun() {
      AtomicInteger steps = new AtomicInteger();
      Trampoline<Integer> counted =
          Trampoline.defer(() -> Trampoline.done(steps.incrementAndGet())).map(n -> n * 10);

      assertThat(steps).hasValue(0);
      assertThat(counted.run()).isEqualTo(10);
      assertThat(steps).hasValue(1);
    }

    @Test
    @DisplayName("a chain of 100,000 maps runs without overflowing")
    void longMapChain() {
      Trampoline<Integer> chain = Trampoline.done(0);
      for (int i = 0; i < 100_000; i++) {
        chain = chain.map(n -> n + 1);
      }

      assertThat(chain.run()).isEqualTo(100_000);
    }

    @Test
    @DisplayName("two deferred sums combine with flatMap")
    void twoCalls() {
      Trampoline<Long> both = sumTail(10, 0).flatMap(a -> sumTail(20, 0).map(b -> a + b));

      assertThat(both.run()).isEqualTo(55L + 210L);
    }

    @Test
    @DisplayName("done refuses null")
    void doneRefusesNull() {
      assertThatThrownBy(() -> Trampoline.done(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("functions that call each other")
  class Mutual {

    @Test
    @DisplayName("1,000,001 is odd, and 999,999 is odd")
    void parity() {
      assertThat(isEven(1_000_001).run()).isFalse();
      assertThat(isOdd(999_999).run()).isTrue();
    }
  }

  @Nested
  @DisplayName("a long list under IO")
  class Traverse {

    @Test
    @DisplayName("traverseListStackSafe labels 100,000 lines, in order")
    void labelsEveryLine() {
      List<Integer> lines = IntStream.rangeClosed(1, 100_000).boxed().toList();

      List<String> labels =
          IO_OP
              .narrow(
                  TrampolineUtils.traverseListStackSafe(
                      lines, n -> IO_OP.widen(IO.delay(() -> "line " + n)), Instances.monad(io())))
              .unsafeRunSync();

      assertThat(labels).hasSize(100_000).startsWith("line 1", "line 2").endsWith("line 100000");
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("Instances.monad(trampoline()) is TrampolineMonad's instance")
    void instance() {
      Monad<TrampolineKind.Witness> trampolines = Instances.monad(trampoline());

      assertThat(trampolines).isSameAs(TrampolineMonad.INSTANCE);
    }

    @Test
    @DisplayName("flatMap through the instance sequences two trampolines")
    void flatMapThroughTheInstance() {
      Monad<TrampolineKind.Witness> trampolines = Instances.monad(trampoline());

      long result =
          TRAMPOLINE
              .narrow(
                  trampolines.flatMap(
                      a -> TRAMPOLINE.widen(sumTail(20, 0).map(b -> a + b)),
                      TRAMPOLINE.widen(sumTail(10, 0))))
              .run();

      assertThat(result).isEqualTo(265L);
    }

    @Test
    @DisplayName("a chain of 100,000 flatMaps built through the instance runs in the loop")
    void deepChainThroughTheInstance() {
      Monad<TrampolineKind.Witness> trampolines = Instances.monad(trampoline());
      Kind<TrampolineKind.Witness, Integer> chain = trampolines.of(0);
      for (int i = 0; i < 100_000; i++) {
        chain = trampolines.flatMap(n -> trampolines.of(n + 1), chain);
      }

      assertThat(TRAMPOLINE.narrow(chain).run()).isEqualTo(100_000);
    }
  }
}
