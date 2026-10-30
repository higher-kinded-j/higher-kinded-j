// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.monadzero;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.monadzero.MonadZeroBook.BASKETS;
import static org.higherkindedj.example.book.foundations.monadzero.MonadZeroBook.LAMP;
import static org.higherkindedj.example.book.foundations.monadzero.MonadZeroBook.bulk;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.instances.Witnesses.stream;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the MonadZero page makes about filtering with a zero. */
@DisplayName("MonadZero: a zero ends a chain, so a chain can filter")
class MonadZeroBookTest {

  private static final MonadZero<ListKind.Witness> LIST_ZERO = Instances.monadZero(list());
  private static final MonadZero<MaybeKind.Witness> MAYBE_ZERO = Instances.monadZero(maybe());

  @Test
  @DisplayName("each type's zero: an empty list, Nothing, an empty Optional, an empty Stream")
  void eachTypesZero() {
    assertThat(LIST.narrow(LIST_ZERO.<String>zero())).isEmpty();
    assertThat(MAYBE.narrow(MAYBE_ZERO.<String>zero())).isEqualTo(Maybe.nothing());
    assertThat(OPTIONAL.narrow(Instances.monadZero(optional()).<String>zero())).isEmpty();
    assertThat(STREAM.narrow(Instances.monadZero(stream()).<String>zero())).isEmpty();
  }

  @Test
  @DisplayName("bulk keeps a bulk line in a Maybe, and drops a single one to Nothing")
  void bulkOnMaybe() {
    LineItem bulbs = BASKETS.getFirst().get(1);

    assertThat(MAYBE.narrow(bulk(MAYBE_ZERO, MAYBE.just(bulbs)))).isEqualTo(Maybe.just(bulbs));
    assertThat(MAYBE.narrow(bulk(MAYBE_ZERO, MAYBE.just(LAMP)))).isEqualTo(Maybe.nothing());
  }

  @Test
  @DisplayName("when on one value tests the value itself, and a failed test ends at Nothing")
  void whenOnMaybe() {
    Maybe<String> sku =
        MAYBE.narrow(
            For.from(MAYBE_ZERO, MAYBE.just(LAMP))
                .when(line -> line.quantity() > 1)
                .yield(LineItem::sku));

    assertThat(sku).isEqualTo(Maybe.nothing());
  }

  @Test
  @DisplayName("when in ForState tests the state itself, and a failed test ends at Nothing")
  void whenInForStateTestsTheState() {
    Maybe<LineItem> line =
        MAYBE.narrow(
            ForState.withState(MAYBE_ZERO, MAYBE.just(LAMP))
                .when(state -> state.quantity() > 1)
                .yield());

    assertThat(line).isEqualTo(Maybe.nothing());
  }

  @Test
  @DisplayName("filter keeps the lines that pass, in order, with List's zero for the rest")
  void filterOnList() {
    LineItem desk = new LineItem("DESK", 2, new BigDecimal("120.00"));

    assertThat(LIST.narrow(bulk(LIST_ZERO, LIST.widen(List.of(desk, LAMP, BASKETS.get(1).get(0))))))
        .containsExactly(desk, BASKETS.get(1).get(0));
  }

  @Test
  @DisplayName("Instances.monadZero compiles for Either, and fails when it runs")
  void eitherHasNoZero() {
    assertThatThrownBy(() -> Instances.monadZero(either())).isInstanceOf(ClassCastException.class);
  }
}
