// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.io.IOBook.LEEDS;
import static org.higherkindedj.example.book.foundations.io.IOBook.NINE_AM;
import static org.higherkindedj.example.book.foundations.io.IOBook.ORDER;
import static org.higherkindedj.example.book.foundations.io.IOBook.bookCourier;
import static org.higherkindedj.example.book.foundations.io.IOBook.dispatch;
import static org.higherkindedj.example.book.foundations.io.IOBook.dispatchAndRecord;
import static org.higherkindedj.example.book.foundations.io.IOBook.dispatchSafely;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.assertions.SteppableClock;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.time.TimeSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the IO page makes about describing, running and failing. */
@DisplayName("IO: a side effect described now and run later")
class IOBookTest {

  @Nested
  @DisplayName("the time source")
  class TheTimeSource {

    @Test
    @DisplayName("a fixed time source stamps the consignment with its instant")
    void aFixedTimeSourceStamps() {
      Consignment consignment = dispatch(TimeSource.fixed(NINE_AM), ORDER, LEEDS).unsafeRunSync();

      assertThat(consignment.state()).isEqualTo(new ConsignmentState.Dispatched(NINE_AM));
      assertThat(consignment.to()).isEqualTo(LEEDS);
    }

    @Test
    @DisplayName("the time is read when the IO runs, and read afresh on each run")
    void theTimeIsReadOnEachRun() {
      SteppableClock clock = SteppableClock.startingAt(NINE_AM);
      IO<Consignment> described = dispatch(TimeSource.of(clock), ORDER, LEEDS);

      clock.advance(Duration.ofMinutes(5));
      Consignment first = described.unsafeRunSync();
      clock.advance(Duration.ofMinutes(5));
      Consignment second = described.unsafeRunSync();

      assertThat(first.state())
          .isEqualTo(new ConsignmentState.Dispatched(NINE_AM.plus(Duration.ofMinutes(5))));
      assertThat(second.state())
          .isEqualTo(new ConsignmentState.Dispatched(NINE_AM.plus(Duration.ofMinutes(10))));
    }
  }

  @Nested
  @DisplayName("describing and running")
  class DescribingAndRunning {

    @Test
    @DisplayName("nothing runs until unsafeRunSync, and each run runs again")
    void nothingRunsUntilTheEdge() {
      List<Consignment> log = new ArrayList<>();
      IO<Consignment> program = dispatchAndRecord(TimeSource.fixed(NINE_AM), log, ORDER, LEEDS);

      assertThat(log).isEmpty();
      program.unsafeRunSync();
      program.unsafeRunSync();
      assertThat(log).hasSize(2);
    }

    @Test
    @DisplayName("map and flatMap build a new IO without running anything")
    void mapAndFlatMapRunNothing() {
      AtomicInteger runs = new AtomicInteger();
      IO<Integer> counted = IO.delay(runs::incrementAndGet);

      IO<String> built = counted.map(n -> "run " + n).flatMap(s -> IO.delay(() -> s + "!"));

      assertThat(runs).hasValue(0);
      assertThat(built.unsafeRunSync()).isEqualTo("run 1!");
      assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("an exception propagates out of unsafeRunSync")
    void anExceptionPropagates() {
      IO<Consignment> refused =
          bookCourier(dispatch(TimeSource.fixed(NINE_AM), ORDER, LEEDS).unsafeRunSync());

      assertThatThrownBy(refused::unsafeRunSync)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("the courier is closed");
    }

    @Test
    @DisplayName("unsafeRunSync runs the effect on the calling thread")
    void unsafeRunSyncRunsOnTheCallingThread() {
      Thread caller = Thread.currentThread();

      assertThat(IO.delay(Thread::currentThread).unsafeRunSync()).isSameAs(caller);
    }

    @Test
    @DisplayName("fromRunnable and asUnit give an IO of Unit")
    void fromRunnableGivesUnit() {
      AtomicInteger runs = new AtomicInteger();

      assertThat(IO.fromRunnable(runs::incrementAndGet).unsafeRunSync()).isEqualTo(Unit.INSTANCE);
      assertThat(IO.delay(() -> 2).asUnit().unsafeRunSync()).isEqualTo(Unit.INSTANCE);
      assertThat(runs).hasValue(1);
    }
  }

  @Nested
  @DisplayName("values, never null")
  class ValuesNeverNull {

    @Test
    @DisplayName("a supplier or map function that returns null fails the IO when it runs")
    void aNullFailsWhenItRuns() {
      IO<String> nothing = IO.delay(() -> null);
      IO<String> mappedToNull = IO.delay(() -> "x").map(s -> null);

      assertThatThrownBy(nothing::unsafeRunSync).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(mappedToNull::unsafeRunSync).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("the instance's of(null) throws at the call")
    void ofNullThrows() {
      Monad<IOKind.Witness> effects = Instances.monad(io());

      assertThatThrownBy(() -> effects.of(null)).isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("on an IOPath, and over Kind")
  class OnAPathAndOverKind {

    @Test
    @DisplayName("catching turns the courier's exception into a Left")
    void catchingGivesALeft() {
      assertThat(dispatchSafely(TimeSource.fixed(NINE_AM), ORDER, LEEDS).unsafeRun())
          .isEqualTo(Either.left("booking failed: the courier is closed"));
    }

    @Test
    @DisplayName("handleError turns the exception into a value, and asTry into a Try")
    void handleErrorAndAsTry() {
      IOPath<String> failing =
          Path.io(
              () -> {
                throw new IllegalStateException("closed");
              });

      assertThat(failing.handleError(Throwable::getMessage).unsafeRun()).isEqualTo("closed");
      assertThat(failing.asTry().unsafeRun().foldFailureFirst(Throwable::getMessage, s -> s))
          .isEqualTo("closed");
    }

    @Test
    @DisplayName("the instance's map runs nothing until the result is run")
    void theInstancesMapRunsNothing() {
      AtomicInteger runs = new AtomicInteger();
      Monad<IOKind.Witness> effects = Instances.monad(io());

      var label = effects.map(n -> "run " + n, IO_OP.widen(IO.delay(runs::incrementAndGet)));

      assertThat(runs).hasValue(0);
      assertThat(IO_OP.unsafeRunSync(label)).isEqualTo("run 1");
    }

    @Test
    @DisplayName("the instance's map labels the consignment, and IO has no MonadError")
    void theInstanceLabelsTheConsignment() {
      Monad<IOKind.Witness> effects = Instances.monad(io());

      String label =
          IO_OP.unsafeRunSync(
              effects.map(
                  consignment -> "to " + consignment.to().city(),
                  IO_OP.widen(dispatch(TimeSource.fixed(NINE_AM), ORDER, LEEDS))));

      assertThat(label).isEqualTo("to Leeds");
      assertThat(effects).isNotInstanceOf(MonadError.class);
    }

    @Test
    @DisplayName("widening returns the same object")
    void wideningReturnsTheSameObject() {
      IO<Consignment> described = dispatch(TimeSource.fixed(NINE_AM), ORDER, LEEDS);

      assertThat(IO_OP.widen(described)).isSameAs(described);
    }
  }
}
