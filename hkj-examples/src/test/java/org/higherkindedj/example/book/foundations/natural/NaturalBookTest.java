// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.natural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.example.book.foundations.natural.NaturalBook.LAMP;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Natural Transformation page makes about converting containers. */
@DisplayName("Natural Transformation: the container changes, never the cargo")
class NaturalBookTest {

  private static final LineItem BULB = new LineItem("BULB", 4, new BigDecimal("2.50"));

  @Test
  @DisplayName("maybeToList gives a list of the one value, or an empty list")
  void maybeToList() {
    Natural<MaybeKind.Witness, ListKind.Witness> toList = NaturalBook::maybeToList;

    assertThat(LIST.narrow(toList.apply(MAYBE.just(LAMP)))).containsExactly(LAMP);
    assertThat(LIST.narrow(toList.apply(MAYBE.<LineItem>nothing()))).isEmpty();
  }

  @Test
  @DisplayName("eitherToMaybe drops the error, and keeps the value")
  void eitherToMaybe() {
    Natural<EitherKind.Witness<String>, MaybeKind.Witness> toMaybe = NaturalBook::eitherToMaybe;

    assertThat(MAYBE.narrow(toMaybe.apply(EITHER.widen(Either.right(LAMP)))))
        .isEqualTo(Maybe.just(LAMP));
    assertThat(MAYBE.narrow(toMaybe.apply(EITHER.<String, LineItem>widen(Either.left("none")))))
        .isEqualTo(Maybe.nothing());
  }

  @Test
  @DisplayName("firstOf keeps the first element, or gives Nothing for an empty list")
  void firstOf() {
    Natural<ListKind.Witness, MaybeKind.Witness> first = NaturalBook::firstOf;

    assertThat(MAYBE.narrow(first.apply(LIST.widen(List.of(LAMP, BULB)))))
        .isEqualTo(Maybe.just(LAMP));
    assertThat(MAYBE.narrow(first.apply(LIST.widen(List.<LineItem>of()))))
        .isEqualTo(Maybe.nothing());
  }

  @Test
  @DisplayName("andThen applies the first transformation, then the second")
  void andThenComposes() {
    Natural<EitherKind.Witness<String>, MaybeKind.Witness> toMaybe = NaturalBook::eitherToMaybe;
    Natural<EitherKind.Witness<String>, ListKind.Witness> eitherToList =
        toMaybe.andThen(NaturalBook::maybeToList);

    assertThat(LIST.narrow(eitherToList.apply(EITHER.widen(Either.right(LAMP)))))
        .containsExactly(LAMP);
    assertThat(LIST.narrow(eitherToList.apply(EITHER.<String, LineItem>widen(Either.left("none")))))
        .isEmpty();
  }

  @Test
  @DisplayName("compose runs its argument first, the reverse of andThen")
  void composeRunsItsArgumentFirst() {
    Natural<MaybeKind.Witness, ListKind.Witness> toList = NaturalBook::maybeToList;
    Natural<EitherKind.Witness<String>, MaybeKind.Witness> toMaybe = NaturalBook::eitherToMaybe;

    assertThat(LIST.narrow(toList.compose(toMaybe).apply(EITHER.widen(Either.right(LAMP)))))
        .isEqualTo(LIST.narrow(toMaybe.andThen(toList).apply(EITHER.widen(Either.right(LAMP)))))
        .containsExactly(LAMP);
  }

  // Compiles as a Natural, but looks at the value: it drops a String and keeps anything else
  static <A> Kind<ListKind.Witness, A> dropStrings(Kind<MaybeKind.Witness, A> fa) {
    Maybe<A> maybe = MAYBE.narrow(fa);
    return LIST.widen(
        maybe.isJust() && !(maybe.get() instanceof String) ? List.of(maybe.get()) : List.of());
  }

  @Test
  @DisplayName("a transformation that inspects the value with instanceof breaks naturality")
  void inspectingTheValueBreaksNaturality() {
    Natural<MaybeKind.Witness, ListKind.Witness> inspecting = NaturalBookTest::dropStrings;
    Function<LineItem, String> sku = LineItem::sku;

    List<String> mapThenConvert =
        LIST.narrow(inspecting.apply(MAYBE.widen(MAYBE.narrow(MAYBE.just(LAMP)).map(sku))));
    List<String> convertThenMap =
        LIST.narrow(inspecting.apply(MAYBE.just(LAMP))).stream().map(sku).toList();

    assertThat(mapThenConvert).isEmpty();
    assertThat(convertThenMap).containsExactly("LAMP");
  }
}
