// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.applicative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.applicative.ApplicativeBook.checkPrice;
import static org.higherkindedj.example.book.foundations.applicative.ApplicativeBook.checkQuantity;
import static org.higherkindedj.example.book.foundations.applicative.ApplicativeBook.checkSku;
import static org.higherkindedj.example.book.foundations.applicative.ApplicativeBook.lineItem;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.FUTURE;
import static org.higherkindedj.hkt.instances.Instances.applicative;
import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.nonEmptyList;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.instances.Witnesses.vtask;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.nonemptylist.NonEmptyListKindHelper.NON_EMPTY_LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.list.ListTraverse;
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

/** Holds the claims the Applicative page makes about what each instance's {@code ap} does. */
@DisplayName("Applicative: the instance decides what combining does")
class ApplicativeBookTest {

  private static final BigDecimal PRICE = new BigDecimal("40.00");

  private static final Applicative<ValidatedKind.Witness<List<String>>> VALIDATED_APPLICATIVE =
      Instances.validated(Semigroups.list());

  @Test
  @DisplayName("Validated gives every error, in the order of the inputs, or the line item")
  void validatedReportsEveryError() {
    Validated<List<String>, LineItem> threeErrors =
        VALIDATED.narrow(
            lineItem(
                VALIDATED_APPLICATIVE,
                VALIDATED.widen(checkSku(" ")),
                VALIDATED.widen(checkQuantity(0)),
                VALIDATED.widen(checkPrice(new BigDecimal("-1")))));
    Validated<List<String>, LineItem> noErrors =
        VALIDATED.narrow(
            lineItem(
                VALIDATED_APPLICATIVE,
                VALIDATED.widen(checkSku("LAMP")),
                VALIDATED.widen(checkQuantity(1)),
                VALIDATED.widen(checkPrice(PRICE))));

    assertThat(threeErrors)
        .isEqualTo(
            Validated.invalid(
                List.of("SKU is blank", "quantity must be positive", "price is negative")));
    assertThat(noErrors).isEqualTo(Validated.valid(new LineItem("LAMP", 1, PRICE)));
  }

  @Test
  @DisplayName("Either and Try give the first failure, and drop the second")
  void eitherAndTryStopAtTheFirstFailure() {
    Applicative<EitherKind.Witness<List<String>>> eitherApplicative = applicative(either());
    IllegalStateException first = new IllegalStateException("SKU is blank");
    IllegalStateException second = new IllegalStateException("quantity must be positive");

    assertThat(
            EITHER.narrow(
                lineItem(
                    eitherApplicative,
                    EITHER.widen(checkSku(" ").toEither()),
                    EITHER.widen(checkQuantity(0).toEither()),
                    EITHER.widen(checkPrice(new BigDecimal("-1")).toEither()))))
        .hasToString("Left([SKU is blank])");
    assertThat(
            EITHER.narrow(
                eitherApplicative.map2(
                    EITHER.widen(checkSku(" ").toEither()),
                    EITHER.widen(checkQuantity(0).toEither()),
                    (sku, quantity) -> sku + quantity)))
        .hasToString("Left([SKU is blank])");
    Try<String> both =
        TRY.narrow(
            applicative(try_())
                .map2(
                    TRY.widen(Try.<String>failure(first)),
                    TRY.widen(Try.<Integer>failure(second)),
                    (sku, quantity) -> sku + quantity));
    assertThat(both)
        .isInstanceOfSatisfying(
            Try.Failure.class, failure -> assertThat(failure.cause()).isSameAs(first));
  }

  @Test
  @DisplayName("Maybe and Optional are empty when either input is empty")
  void maybeAndOptionalAreEmptyIfEitherIs() {
    assertThat(
            MAYBE.narrow(
                applicative(maybe())
                    .map2(
                        MAYBE.widen(Maybe.just("LAMP")),
                        MAYBE.widen(Maybe.<Integer>nothing()),
                        (sku, quantity) -> sku + quantity)))
        .isEqualTo(Maybe.nothing());
    assertThat(
            OPTIONAL.narrow(
                applicative(optional())
                    .map2(
                        OPTIONAL.widen(Optional.<String>empty()),
                        OPTIONAL.widen(Optional.of(1)),
                        (sku, quantity) -> sku + quantity)))
        .isEmpty();
  }

  @Test
  @DisplayName("List and NonEmptyList give every combination of their inputs")
  void listsGiveEveryCombination() {
    List<String> fromLists =
        LIST.narrow(
            applicative(list())
                .map2(
                    LIST.widen(List.of("LAMP", "DESK")),
                    LIST.widen(List.of(1, 2)),
                    (sku, quantity) -> sku + "x" + quantity));
    NonEmptyList<String> fromNonEmptyLists =
        NON_EMPTY_LIST.narrow(
            applicative(nonEmptyList())
                .map2(
                    NON_EMPTY_LIST.widen(NonEmptyList.of("LAMP", "DESK")),
                    NON_EMPTY_LIST.widen(NonEmptyList.of(1, 2)),
                    (sku, quantity) -> sku + "x" + quantity));

    assertThat(fromLists).containsExactly("LAMPx1", "LAMPx2", "DESKx1", "DESKx2");
    assertThat(fromNonEmptyLists.toJavaList())
        .containsExactly("LAMPx1", "LAMPx2", "DESKx1", "DESKx2");
  }

  @Test
  @DisplayName("IO and VTask run nothing until run, then the first input before the second")
  void ioAndVTaskRunTheInputsInOrder() {
    List<String> ran = new ArrayList<>();
    Kind<IOKind.Witness, String> io =
        applicative(io())
            .map2(
                IO_OP.widen(IO.delay(() -> record(ran, "first"))),
                IO_OP.widen(IO.delay(() -> record(ran, "second"))),
                (first, second) -> first + " then " + second);
    Kind<VTaskKind.Witness, String> vtask =
        applicative(vtask())
            .map2(
                VTASK.widen(VTask.delay(() -> record(ran, "first"))),
                VTASK.widen(VTask.delay(() -> record(ran, "second"))),
                (first, second) -> first + " then " + second);

    assertThat(ran).isEmpty();
    assertThat(IO_OP.narrow(io).unsafeRunSync()).isEqualTo("first then second");
    assertThat(ran).containsExactly("first", "second");
    ran.clear();
    assertThat(VTASK.narrow(vtask).run()).isEqualTo("first then second");
    assertThat(ran).containsExactly("first", "second");
  }

  @Test
  @DisplayName("IO and VTask never run the second input when the first fails")
  void ioAndVTaskStopWhenTheFirstInputFails() {
    List<String> ran = new ArrayList<>();
    IllegalStateException declined = new IllegalStateException("payment declined");
    Kind<IOKind.Witness, String> io =
        applicative(io())
            .map2(
                IO_OP.widen(
                    IO.<String>delay(
                        () -> {
                          throw declined;
                        })),
                IO_OP.widen(IO.delay(() -> record(ran, "second"))),
                (first, second) -> first + second);
    Kind<VTaskKind.Witness, String> vtask =
        applicative(vtask())
            .map2(
                VTASK.widen(
                    VTask.<String>delay(
                        () -> {
                          throw declined;
                        })),
                VTASK.widen(VTask.delay(() -> record(ran, "second"))),
                (first, second) -> first + second);

    assertThatThrownBy(() -> IO_OP.narrow(io).unsafeRunSync()).isSameAs(declined);
    assertThatThrownBy(() -> VTASK.narrow(vtask).run()).isSameAs(declined);
    assertThat(ran).isEmpty();
  }

  @Test
  @DisplayName("CompletableFuture's map2 completes once both inputs have, whichever finishes last")
  void completableFutureWaitsForBoth() {
    CompletableFuture<String> sku = new CompletableFuture<>();
    CompletableFuture<Integer> quantity = new CompletableFuture<>();
    CompletableFuture<String> both =
        FUTURE.narrow(
            applicative(completableFuture())
                .map2(FUTURE.widen(sku), FUTURE.widen(quantity), (s, q) -> s + "x" + q));

    quantity.complete(2);
    assertThat(both).isNotDone();
    sku.complete("LAMP");
    assertThat(both).isCompletedWithValue("LAMPx2");
  }

  @Test
  @DisplayName("Writer keeps the value and combines both logs, first log first")
  void writerCombinesBothLogs() {
    Monad<WriterKind.Witness<List<String>>> writer = Instances.writer(Monoids.list());

    Writer<List<String>, String> both =
        WRITER.narrow(
            writer.map2(
                WRITER.widen(Writer.of(List.of("checked SKU"), "LAMP")),
                WRITER.widen(Writer.of(List.of("checked quantity"), 2)),
                (sku, quantity) -> sku + "x" + quantity));

    assertThat(both).isEqualTo(Writer.of(List.of("checked SKU", "checked quantity"), "LAMPx2"));
  }

  @Test
  @DisplayName("Validated's flatMap stops at the first error, where its map2 keeps both")
  void validatedFlatMapStopsAtTheFirstError() {
    Monad<ValidatedKind.Witness<List<String>>> validated = Instances.validated(Semigroups.list());

    Validated<List<String>, LineItem> chained =
        VALIDATED.narrow(
            validated.flatMap(
                sku ->
                    validated.map(
                        quantity -> new LineItem(sku, quantity, PRICE),
                        VALIDATED.widen(checkQuantity(0))),
                VALIDATED.widen(checkSku(" "))));

    assertThat(chained).isEqualTo(Validated.invalid(List.of("SKU is blank")));
  }

  @Test
  @DisplayName("traverse with Validated's instance reports every element's errors")
  void traverseWithValidatedReportsEveryElementsErrors() {
    Validated<List<String>, List<String>> skus =
        VALIDATED.narrow(
            VALIDATED_APPLICATIVE.map(
                LIST::narrow,
                ListTraverse.INSTANCE.traverse(
                    VALIDATED_APPLICATIVE,
                    (String sku) -> VALIDATED.widen(checkSku(sku)),
                    LIST.widen(List.of(" ", "LAMP", "")))));

    assertThat(skus).isEqualTo(Validated.invalid(List.of("SKU is blank", "SKU is blank")));
  }

  private static String record(List<String> ran, String step) {
    ran.add(step);
    return step;
  }
}
