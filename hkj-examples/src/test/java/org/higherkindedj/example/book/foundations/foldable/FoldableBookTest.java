// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.foldable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.foldable.FoldableBook.ZERO_QUANTITIES;
import static org.higherkindedj.example.book.foundations.foldable.FoldableBook.checkLine;
import static org.higherkindedj.example.book.foundations.foldable.FoldableBook.total;
import static org.higherkindedj.example.book.foundations.monoid.MonoidBook.LINES;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeTraverse;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Foldable and Traverse page makes about folding and traversing. */
@DisplayName("Foldable and Traverse: one walk, and the instance decides what combining does")
class FoldableBookTest {

  private static final Applicative<ValidatedKind.Witness<List<String>>> EVERY_ERROR =
      Instances.validated(Semigroups.list());

  @Test
  @DisplayName("one total method serves List and Maybe, and an empty Maybe totals to ZERO")
  void oneTotalForAnyContainer() {
    assertThat(total(ListTraverse.INSTANCE, LIST.widen(LINES))).isEqualTo(new BigDecimal("50.00"));
    assertThat(total(MaybeTraverse.INSTANCE, MAYBE.just(LINES.getFirst())))
        .isEqualTo(new BigDecimal("40.00"));
    assertThat(total(MaybeTraverse.INSTANCE, MAYBE.nothing())).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  @DisplayName("traverse with Validated or Either gives every line when all pass")
  void bothKeepEveryLine() {
    Validated<List<String>, List<LineItem>> checked =
        VALIDATED
            .narrow(
                ListTraverse.INSTANCE.traverse(
                    EVERY_ERROR, line -> VALIDATED.widen(checkLine(line)), LIST.widen(LINES)))
            .map(LIST::narrow);

    Either<List<String>, List<LineItem>> passed =
        EITHER
            .narrow(
                ListTraverse.INSTANCE.traverse(
                    Instances.applicative(either()),
                    line -> EITHER.widen(checkLine(line).toEither()),
                    LIST.widen(LINES)))
            .map(LIST::narrow);

    assertThat(checked).isEqualTo(Validated.valid(LINES));
    assertThat(passed).isEqualTo(Either.right(LINES));
  }

  @Test
  @DisplayName("traverse with Validated reports every failing line; with Either, the first")
  void instanceDecidesWhatFailureDoes() {
    Validated<List<String>, List<LineItem>> reported =
        VALIDATED
            .narrow(
                ListTraverse.INSTANCE.traverse(
                    EVERY_ERROR,
                    line -> VALIDATED.widen(checkLine(line)),
                    LIST.widen(ZERO_QUANTITIES)))
            .map(LIST::narrow);
    Either<List<String>, List<LineItem>> firstError =
        EITHER
            .narrow(
                ListTraverse.INSTANCE.traverse(
                    Instances.applicative(either()),
                    line -> EITHER.widen(checkLine(line).toEither()),
                    LIST.widen(ZERO_QUANTITIES)))
            .map(LIST::narrow);

    assertThat(reported)
        .isEqualTo(
            Validated.invalid(
                List.of("CHAIR: quantity must be positive", "DESK: quantity must be positive")));
    assertThat(firstError).isEqualTo(Either.left(List.of("CHAIR: quantity must be positive")));
  }

  @Test
  @DisplayName("traverse with Either keeps the first error, yet runs the check on every line")
  void eitherRunsTheCheckOnEveryLine() {
    List<String> checked = new ArrayList<>();
    Either<List<String>, List<LineItem>> firstError =
        EITHER
            .narrow(
                ListTraverse.INSTANCE.traverse(
                    Instances.applicative(either()),
                    line -> {
                      checked.add(line.sku());
                      return EITHER.widen(checkLine(line).toEither());
                    },
                    LIST.widen(ZERO_QUANTITIES)))
            .map(LIST::narrow);

    assertThat(firstError).isEqualTo(Either.left(List.of("CHAIR: quantity must be positive")));
    assertThat(checked).containsExactly("LAMP", "CHAIR", "DESK");
  }

  @Test
  @DisplayName("sequenceA is traverse with the identity function")
  void sequenceIsTraverseWithIdentity() {
    Applicative<MaybeKind.Witness> maybeApplicative = Instances.applicative(maybe());
    List<Kind<MaybeKind.Witness, BigDecimal>> missing =
        List.of(MAYBE.just(new BigDecimal("40.00")), MAYBE.nothing());
    List<Kind<MaybeKind.Witness, BigDecimal>> found =
        List.of(MAYBE.just(new BigDecimal("40.00")), MAYBE.just(new BigDecimal("2.50")));

    for (List<Kind<MaybeKind.Witness, BigDecimal>> prices : List.of(missing, found)) {
      Maybe<List<BigDecimal>> sequenced =
          MAYBE
              .narrow(ListTraverse.INSTANCE.sequenceA(maybeApplicative, LIST.widen(prices)))
              .map(LIST::narrow);
      Maybe<List<BigDecimal>> traversed =
          MAYBE
              .narrow(
                  ListTraverse.INSTANCE.traverse(
                      maybeApplicative, Function.identity(), LIST.widen(prices)))
              .map(LIST::narrow);
      assertThat(sequenced).isEqualTo(traversed);
    }
    assertThat(
            MAYBE
                .narrow(ListTraverse.INSTANCE.sequenceA(maybeApplicative, LIST.widen(found)))
                .map(LIST::narrow))
        .isEqualTo(Maybe.just(List.of(new BigDecimal("40.00"), new BigDecimal("2.50"))));
  }

  @Test
  @DisplayName("traverse with IO runs each step in element order, one after another")
  void ioRunsTheStepsInOrder() {
    List<String> ran = new ArrayList<>();
    Function<LineItem, Kind<IOKind.Witness, String>> reserve =
        line ->
            IO_OP.widen(
                IO.delay(
                    () -> {
                      ran.add(line.sku());
                      return line.sku();
                    }));

    Kind<IOKind.Witness, Kind<ListKind.Witness, String>> program =
        ListTraverse.INSTANCE.traverse(
            Instances.applicative(io()), reserve, LIST.widen(ZERO_QUANTITIES));

    assertThat(ran).isEmpty();
    IO_OP.narrow(program).unsafeRunSync();
    assertThat(ran).containsExactly("LAMP", "CHAIR", "DESK");
  }
}
