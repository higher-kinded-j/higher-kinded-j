// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.trymonad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.trymonad.TryBook.MISSING_FEED;
import static org.higherkindedj.example.book.foundations.trymonad.TryBook.checkedLine;
import static org.higherkindedj.example.book.foundations.trymonad.TryBook.describe;
import static org.higherkindedj.example.book.foundations.trymonad.TryBook.lineOrReason;
import static org.higherkindedj.example.book.foundations.trymonad.TryBook.parseLine;
import static org.higherkindedj.example.book.foundations.trymonad.TryBook.price;
import static org.higherkindedj.example.book.foundations.trymonad.TryBook.readFeed;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.math.BigDecimal;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.trymonad.Try;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Try page makes about catching, chaining, recovering and reading. */
@DisplayName("Try: an exception kept as a value")
class TryBookTest {

  private static final LineItem LAMP = new LineItem("LAMP", 2, new BigDecimal("40.00"));

  private static Throwable causeOf(Try<?> result) {
    return result.foldFailureFirst(cause -> cause, value -> null);
  }

  @Nested
  @DisplayName("catching")
  class Catching {

    @Test
    @DisplayName("Try.of gives a Success for a line it can read")
    void ofGivesASuccess() {
      assertThat(parseLine("LAMP,2,40.00")).isEqualTo(Try.success(LAMP));
    }

    @Test
    @DisplayName("Try.of keeps the exception a bad line throws")
    void ofKeepsTheException() {
      assertThat(causeOf(parseLine("LAMP,two,40.00")))
          .isInstanceOf(NumberFormatException.class)
          .hasMessage("For input string: \"two\"");
    }

    @Test
    @DisplayName("Try.attempt keeps a checked exception")
    void attemptKeepsACheckedException() {
      assertThat(causeOf(readFeed(MISSING_FEED)))
          .isInstanceOf(NoSuchFileException.class)
          .hasMessage(MISSING_FEED);
    }

    @Test
    @DisplayName("an InterruptedException is caught, and the interrupt flag is set again")
    void anInterruptIsCaughtAndTheFlagSetAgain() {
      Try<String> interrupted =
          Try.attempt(
              () -> {
                throw new InterruptedException("stop");
              });

      assertThat(causeOf(interrupted)).isInstanceOf(InterruptedException.class);
      assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @DisplayName("an Error is not caught: it leaves Try.of")
    void anErrorIsNotCaught() {
      assertThatThrownBy(
              () ->
                  Try.of(
                      () -> {
                        throw new StackOverflowError();
                      }))
          .isInstanceOf(StackOverflowError.class);
    }
  }

  @Nested
  @DisplayName("chaining")
  class Chaining {

    @Test
    @DisplayName("flatMap runs the check on a Success")
    void flatMapRunsTheCheckOnASuccess() {
      assertThat(causeOf(checkedLine("LAMP,2,0.00")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("price must be positive: 0.00");
      assertThat(checkedLine("LAMP,2,40.00")).isEqualTo(Try.success(LAMP));
    }

    @Test
    @DisplayName("a Failure passes through flatMap, and the check never runs")
    void aFailurePassesThroughFlatMap() {
      List<String> ran = new ArrayList<>();

      Try<LineItem> result =
          parseLine("LAMP,two,0.00")
              .flatMap(
                  item -> {
                    ran.add("check");
                    return Try.success(item);
                  });

      assertThat(causeOf(result)).isInstanceOf(NumberFormatException.class);
      assertThat(ran).isEmpty();
    }

    @Test
    @DisplayName("a map function that throws gives a Failure")
    void aMapThatThrowsGivesAFailure() {
      Try<Integer> result =
          Try.success(2)
              .map(
                  n -> {
                    throw new IllegalStateException("boom");
                  });

      assertThat(causeOf(result)).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }
  }

  @Nested
  @DisplayName("recovering")
  class Recovering {

    @Test
    @DisplayName("recoverWith tries again without the pound sign")
    void recoverWithTriesAgain() {
      assertThat(price("£40.00")).isEqualTo(Try.success(new BigDecimal("40.00")));
      assertThat(price("40.00")).isEqualTo(Try.success(new BigDecimal("40.00")));
      assertThat(causeOf(price("forty"))).isInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("recover gives a value in place of the failure")
    void recoverGivesAValue() {
      assertThat(readFeed(MISSING_FEED).recover(error -> "")).isEqualTo(Try.success(""));
    }

    @Test
    @DisplayName("recover and recoverWith never run their function on a Success")
    void recoveryNeverRunsOnASuccess() {
      List<String> ran = new ArrayList<>();

      Try<Integer> recovered =
          Try.success(2)
              .recover(
                  error -> {
                    ran.add("recover");
                    return 0;
                  });
      Try<Integer> recoveredWith =
          Try.success(2)
              .recoverWith(
                  error -> {
                    ran.add("recoverWith");
                    return Try.success(0);
                  });

      assertThat(recovered).isEqualTo(Try.success(2));
      assertThat(recoveredWith).isEqualTo(Try.success(2));
      assertThat(ran).isEmpty();
    }

    @Test
    @DisplayName("a recovery function that throws gives a Failure with its exception")
    void aRecoveryThatThrowsGivesItsException() {
      Try<Integer> result =
          Try.<Integer>failure(new IllegalStateException("first"))
              .recover(
                  error -> {
                    throw new IllegalArgumentException("second");
                  });

      assertThat(causeOf(result)).isInstanceOf(IllegalArgumentException.class).hasMessage("second");
    }

    @Test
    @DisplayName("a recoverWith function that throws gives a Failure with its exception")
    void aRecoverWithThatThrowsGivesItsException() {
      Try<Integer> result =
          Try.<Integer>failure(new IllegalStateException("first"))
              .recoverWith(
                  error -> {
                    throw new IllegalArgumentException("second");
                  });

      assertThat(causeOf(result)).isInstanceOf(IllegalArgumentException.class).hasMessage("second");
    }
  }

  @Nested
  @DisplayName("reading the result")
  class ReadingTheResult {

    @Test
    @DisplayName("the switch names each case")
    void theSwitchNamesEachCase() {
      assertThat(describe(parseLine("LAMP,2,40.00"))).isEqualTo("2 x LAMP");
      assertThat(describe(parseLine("LAMP,two,40.00")))
          .isEqualTo("unreadable line: For input string: \"two\"");
    }

    @Test
    @DisplayName("foldFailureFirst takes the failure's function first")
    void foldFailureFirstTakesTheFailureFirst() {
      String fromSuccess = Try.success(2).foldFailureFirst(error -> "failed", n -> "got " + n);
      String fromFailure =
          Try.<Integer>failure(new IllegalStateException("boom"))
              .foldFailureFirst(error -> "failed: " + error.getMessage(), n -> "got " + n);

      assertThat(fromSuccess).isEqualTo("got 2");
      assertThat(fromFailure).isEqualTo("failed: boom");
    }

    @Test
    @DisplayName("match runs the action for the case it holds, the success's first")
    void matchRunsTheActionForItsCase() {
      List<String> ran = new ArrayList<>();

      Try.success(2).match(n -> ran.add("success " + n), error -> ran.add("failure"));
      Try.failure(new IllegalStateException("boom"))
          .match(n -> ran.add("success"), error -> ran.add("failure " + error.getMessage()));

      assertThat(ran).containsExactly("success 2", "failure boom");
    }

    @Test
    @DisplayName("orElse and orElseGet give the value or the fallback")
    void theFallbacksGiveTheValueOrTheFallback() {
      Try<Integer> failed = Try.failure(new IllegalStateException("boom"));

      assertThat(Try.success(2).orElse(0)).isEqualTo(2);
      assertThat(Try.success(2).orElseGet(() -> 0)).isEqualTo(2);
      assertThat(failed.orElse(0)).isEqualTo(0);
      assertThat(failed.orElseGet(() -> 0)).isEqualTo(0);
    }

    @Test
    @DisplayName("get throws the exception a Failure holds")
    void getThrowsTheException() throws Throwable {
      Try<Integer> failed = Try.failure(new IllegalStateException("boom"));

      assertThat(Try.success(2).get()).isEqualTo(2);
      assertThatThrownBy(failed::get).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }

    @Test
    @DisplayName("toEither turns the exception into a Left")
    void toEitherTurnsTheExceptionIntoALeft() {
      Try<Integer> failed = Try.failure(new IllegalStateException("boom"));

      assertThat(failed.toEither(error -> "failed: " + error.getMessage()))
          .isEqualTo(Either.left("failed: boom"));
      assertThat(Try.success(2).toEither(error -> "failed")).isEqualTo(Either.right(2));
    }
  }

  @Nested
  @DisplayName("on a TryPath")
  class OnATryPath {

    @Test
    @DisplayName("toEitherPath gives the reason a line was skipped")
    void toEitherPathGivesTheReason() {
      assertThat(lineOrReason("LAMP,2,0.00").run())
          .isEqualTo(Either.left("skipped LAMP,2,0.00: price must be positive: 0.00"));
      assertThat(lineOrReason("LAMP,2,40.00").run()).isEqualTo(Either.right(LAMP));
    }
  }

  @Nested
  @DisplayName("values, never null")
  class ValuesNeverNull {

    @Test
    @DisplayName("success refuses null")
    void successRefusesNull() {
      assertThatThrownBy(() -> Try.success(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a flatMap or recoverWith function that returns null throws")
    void aNullTryThrows() {
      assertThatThrownBy(() -> Try.success(2).flatMap(n -> null))
          .isInstanceOf(KindUnwrapException.class);
      assertThatThrownBy(() -> Try.failure(new IllegalStateException()).recoverWith(error -> null))
          .isInstanceOf(KindUnwrapException.class);
    }

    @Test
    @DisplayName("a supplier, map or recover function that returns null gives a Failure")
    void aNullResultGivesAFailure() {
      assertThat(causeOf(Try.of(() -> null))).isInstanceOf(NullPointerException.class);
      assertThat(causeOf(Try.success(2).map(n -> null))).isInstanceOf(NullPointerException.class);
      assertThat(causeOf(Try.failure(new IllegalStateException()).recover(error -> null)))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Test
  @DisplayName("widening returns the same object, and refuses null")
  void wideningReturnsTheSameObject() {
    Try<LineItem> parsed = parseLine("LAMP,2,40.00");

    assertThat(TRY.widen(parsed)).isSameAs(parsed);
    assertThatThrownBy(() -> TRY.widen(null)).isInstanceOf(NullPointerException.class);
  }
}
