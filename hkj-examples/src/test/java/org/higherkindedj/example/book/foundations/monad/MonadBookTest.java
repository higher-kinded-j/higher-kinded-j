// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.monad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.monad.MonadBook.ORDER;
import static org.higherkindedj.example.book.foundations.monad.MonadBook.markPaid;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Instances.monad;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.nonEmptyList;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.instances.Witnesses.stream;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.instances.Witnesses.vtask;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Monad page makes about what each instance's {@code flatMap} does. */
@DisplayName("Monad: the instance decides what flatMap does")
class MonadBookTest {

  private static final Monad<EitherKind.Witness<String>> EITHER_MONAD = monad(either());

  @Test
  @DisplayName("Either runs save on a Right, and gives the order marked paid")
  void eitherRunsTheStepOnARight() {
    Either<String, Order> saved =
        EITHER.narrow(
            markPaid(
                EITHER_MONAD,
                EITHER.widen(Either.right(ORDER)),
                order -> EITHER.widen(Either.right(order))));

    assertThat(saved.getRight().status()).isEqualTo(OrderStatus.PAID);
  }

  @Test
  @DisplayName("Either passes a Left on unchanged, and save never runs")
  void eitherPassesALeftOn() {
    List<Order> stored = new ArrayList<>();
    Either<String, Order> notFound =
        EITHER.narrow(
            markPaid(
                EITHER_MONAD,
                EITHER.widen(Either.left("order not found")),
                order -> EITHER.widen(Either.right(MonadBook.store(stored, order)))));

    assertThat(notFound).isEqualTo(Either.left("order not found"));
    assertThat(stored).isEmpty();
  }

  @Test
  @DisplayName("Either gives save's own Left when save fails")
  void eitherGivesTheStepsLeft() {
    Either<String, Order> refused =
        EITHER.narrow(
            markPaid(
                EITHER_MONAD,
                EITHER.widen(Either.right(ORDER)),
                order -> EITHER.widen(Either.left("order has shipped"))));

    assertThat(refused).isEqualTo(Either.left("order has shipped"));
  }

  @Test
  @DisplayName("Try passes a Failure on, and turns an exception the step throws into a Failure")
  void tryPassesAFailureOnAndCatchesTheStepsException() {
    List<Order> stored = new ArrayList<>();
    IllegalStateException missing = new IllegalStateException("order not found");
    IllegalStateException declined = new IllegalStateException("card declined");

    Try<Order> notFound =
        TRY.narrow(
            markPaid(
                monad(try_()),
                TRY.widen(Try.<Order>failure(missing)),
                order -> TRY.widen(Try.success(MonadBook.store(stored, order)))));
    Try<Order> thrown =
        TRY.narrow(
            markPaid(
                monad(try_()),
                TRY.widen(Try.success(ORDER)),
                order -> {
                  throw declined;
                }));

    assertThat(notFound)
        .isInstanceOfSatisfying(
            Try.Failure.class, failure -> assertThat(failure.cause()).isSameAs(missing));
    assertThat(thrown)
        .isInstanceOfSatisfying(
            Try.Failure.class, failure -> assertThat(failure.cause()).isSameAs(declined));
    assertThat(stored).isEmpty();
  }

  @Test
  @DisplayName("Validated's flatMap runs the step on a Valid, and stops at an Invalid without it")
  void validatedRunsTheStepOnAValidAndStopsAtAnInvalid() {
    List<Order> stored = new ArrayList<>();
    Monad<ValidatedKind.Witness<List<String>>> validated = Instances.validated(Semigroups.list());
    Validated<List<String>, Order> notFound =
        VALIDATED.narrow(
            markPaid(
                validated,
                VALIDATED.widen(Validated.<List<String>, Order>invalid(List.of("order not found"))),
                order -> VALIDATED.widen(Validated.valid(MonadBook.store(stored, order)))));

    assertThat(notFound).isEqualTo(Validated.invalid(List.of("order not found")));
    assertThat(stored).isEmpty();

    Validated<List<String>, Order> saved =
        VALIDATED.narrow(
            markPaid(
                validated,
                VALIDATED.widen(Validated.<List<String>, Order>valid(ORDER)),
                order -> VALIDATED.widen(Validated.valid(MonadBook.store(stored, order)))));

    assertThat(saved.get().status()).isEqualTo(OrderStatus.PAID);
    assertThat(stored).hasSize(1);
  }

  @Test
  @DisplayName("Maybe and Optional run the step on a value, and stay empty without running it")
  void maybeAndOptionalRunTheStepOnAValueOnly() {
    List<Order> stored = new ArrayList<>();
    Maybe<Order> nothing =
        MAYBE.narrow(
            markPaid(
                monad(maybe()),
                MAYBE.widen(Maybe.nothing()),
                order -> MAYBE.widen(Maybe.just(MonadBook.store(stored, order)))));
    Optional<Order> empty =
        OPTIONAL.narrow(
            markPaid(
                monad(optional()),
                OPTIONAL.widen(Optional.empty()),
                order -> OPTIONAL.widen(Optional.of(MonadBook.store(stored, order)))));

    assertThat(nothing).isEqualTo(Maybe.nothing());
    assertThat(empty).isEmpty();
    assertThat(stored).isEmpty();

    Maybe<Order> just =
        MAYBE.narrow(
            markPaid(
                monad(maybe()),
                MAYBE.widen(Maybe.just(ORDER)),
                order -> MAYBE.widen(Maybe.just(MonadBook.store(stored, order)))));
    Optional<Order> present =
        OPTIONAL.narrow(
            markPaid(
                monad(optional()),
                OPTIONAL.widen(Optional.of(ORDER)),
                order -> OPTIONAL.widen(Optional.of(MonadBook.store(stored, order)))));

    assertThat(just.get().status()).isEqualTo(OrderStatus.PAID);
    assertThat(present).get().extracting(Order::status).isEqualTo(OrderStatus.PAID);
    assertThat(stored).hasSize(2);
  }

  @Test
  @DisplayName("List, NonEmptyList and Stream run the step on every element, joining the results")
  void listsRunTheStepOnEveryElement() {
    List<String> lists =
        LIST.narrow(
            monad(list())
                .flatMap(
                    sku -> LIST.widen(List.of(sku + "x1", sku + "x2")),
                    LIST.widen(List.of("LAMP", "DESK"))));
    NonEmptyList<String> nonEmpty =
        NON_EMPTY_LIST.narrow(
            monad(nonEmptyList())
                .flatMap(
                    sku -> NON_EMPTY_LIST.widen(NonEmptyList.of(sku + "x1", sku + "x2")),
                    NON_EMPTY_LIST.widen(NonEmptyList.of("LAMP", "DESK"))));
    Stream<String> streams =
        STREAM.narrow(
            monad(stream())
                .flatMap(
                    sku -> STREAM.widen(Stream.of(sku + "x1", sku + "x2")),
                    STREAM.widen(Stream.of("LAMP", "DESK"))));

    List<String> expected = List.of("LAMPx1", "LAMPx2", "DESKx1", "DESKx2");
    assertThat(lists).isEqualTo(expected);
    assertThat(nonEmpty.toJavaList()).isEqualTo(expected);
    assertThat(streams.toList()).isEqualTo(expected);
  }

  @Test
  @DisplayName("IO and VTask run nothing until they are run, then the input before the step")
  void ioAndVTaskRunNothingUntilRun() {
    List<String> ran = new ArrayList<>();
    Kind<IOKind.Witness, String> io =
        monad(io())
            .flatMap(
                found -> IO_OP.widen(IO.delay(() -> record(ran, "save " + found))),
                IO_OP.widen(IO.delay(() -> record(ran, "find"))));
    Kind<VTaskKind.Witness, String> vtask =
        monad(vtask())
            .flatMap(
                found -> VTASK.widen(VTask.delay(() -> record(ran, "save " + found))),
                VTASK.widen(VTask.delay(() -> record(ran, "find"))));

    assertThat(ran).isEmpty();
    IO_OP.narrow(io).unsafeRunSync();
    VTASK.narrow(vtask).run();
    assertThat(ran).containsExactly("find", "save find", "find", "save find");
  }

  @Test
  @DisplayName("IO and VTask never run the step when the input fails")
  void ioAndVTaskStopWhenTheInputFails() {
    List<String> ran = new ArrayList<>();
    IllegalStateException missing = new IllegalStateException("order not found");
    Kind<IOKind.Witness, String> io =
        monad(io())
            .flatMap(
                found -> IO_OP.widen(IO.delay(() -> record(ran, "save"))),
                IO_OP.widen(
                    IO.<String>delay(
                        () -> {
                          throw missing;
                        })));
    Kind<VTaskKind.Witness, String> vtask =
        monad(vtask())
            .flatMap(
                found -> VTASK.widen(VTask.delay(() -> record(ran, "save"))),
                VTASK.widen(
                    VTask.<String>delay(
                        () -> {
                          throw missing;
                        })));

    assertThatThrownBy(() -> IO_OP.narrow(io).unsafeRunSync()).isSameAs(missing);
    assertThatThrownBy(() -> VTASK.narrow(vtask).run()).isSameAs(missing);
    assertThat(ran).isEmpty();
  }

  @Test
  @DisplayName("CompletableFuture starts the step when the input completes, and not on a failure")
  void completableFutureStartsTheStepWhenTheInputCompletes() {
    List<String> ran = new ArrayList<>();
    Function<String, Kind<CompletableFutureKind.Witness, String>> save =
        sku -> FUTURE.widen(CompletableFuture.completedFuture(record(ran, "save " + sku)));
    CompletableFuture<String> input = new CompletableFuture<>();
    CompletableFuture<String> saved =
        FUTURE.narrow(monad(completableFuture()).flatMap(save, FUTURE.widen(input)));
    CompletableFuture<String> notSaved =
        FUTURE.narrow(
            monad(completableFuture())
                .flatMap(
                    save,
                    FUTURE.widen(
                        CompletableFuture.<String>failedFuture(
                            new IllegalStateException("order not found")))));

    assertThat(ran).isEmpty();
    assertThat(saved).isNotDone();
    input.complete("LAMP");
    assertThat(saved).isCompletedWithValue("save LAMP");
    assertThat(notSaved).isCompletedExceptionally();
    assertThat(ran).containsExactly("save LAMP");
  }

  @Test
  @DisplayName("Writer joins the input's log and the step's, in that order")
  void writerJoinsBothLogs() {
    Monad<WriterKind.Witness<List<String>>> writer = Instances.writer(Monoids.list());
    Writer<List<String>, String> priced =
        WRITER.narrow(
            writer.flatMap(
                sku -> WRITER.widen(Writer.of(List.of("priced " + sku), sku + " at 40.00")),
                WRITER.widen(Writer.of(List.of("found LAMP"), "LAMP"))));

    assertThat(priced).isEqualTo(Writer.of(List.of("found LAMP", "priced LAMP"), "LAMP at 40.00"));
  }

  @Test
  @DisplayName("peek runs where map runs: on a Right and on every element, never on a Left")
  void peekRunsWhereMapRuns() {
    List<String> seen = new ArrayList<>();
    EITHER_MONAD.peek(sku -> seen.add("right " + sku), EITHER.widen(Either.right("LAMP")));
    EITHER_MONAD.peek(sku -> seen.add("left " + sku), EITHER.widen(Either.left("not found")));
    monad(list()).peek(sku -> seen.add("element " + sku), LIST.widen(List.of("LAMP", "DESK")));

    assertThat(seen).containsExactly("right LAMP", "element LAMP", "element DESK");
  }

  @Test
  @DisplayName("as refuses null and points to asUnit, which gives Unit")
  void asRefusesNullAndAsUnitGivesUnit() {
    Kind<EitherKind.Witness<String>, Order> found = EITHER.widen(Either.right(ORDER));

    assertThatThrownBy(() -> EITHER_MONAD.as(null, found))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("asUnit()");
    assertThat(EITHER.narrow(EITHER_MONAD.asUnit(found))).isEqualTo(Either.right(Unit.INSTANCE));
  }

  @Test
  @DisplayName("flatMap2 on Validated stops at the first error, where map2 reports both")
  void validatedFlatMap2StopsAtTheFirstError() {
    Monad<ValidatedKind.Witness<List<String>>> validated = Instances.validated(Semigroups.list());
    Kind<ValidatedKind.Witness<List<String>>, String> blankSku =
        VALIDATED.widen(Validated.invalid(List.of("SKU is blank")));
    Kind<ValidatedKind.Witness<List<String>>, Integer> noQuantity =
        VALIDATED.widen(Validated.invalid(List.of("quantity must be positive")));

    assertThat(VALIDATED.narrow(validated.map2(blankSku, noQuantity, (sku, n) -> n + sku)))
        .isEqualTo(Validated.invalid(List.of("SKU is blank", "quantity must be positive")));
    assertThat(
            VALIDATED.narrow(
                validated.flatMap2(
                    blankSku, noQuantity, (sku, n) -> VALIDATED.widen(Validated.valid(n + sku)))))
        .isEqualTo(Validated.invalid(List.of("SKU is blank")));
  }

  private static String record(List<String> ran, String step) {
    ran.add(step);
    return step;
  }
}
