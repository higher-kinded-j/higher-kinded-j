// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.natural;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/natural_transformation.html">Natural
 * Transformation</a> page: three conversions between containers, each a generic method that never
 * looks at the value it carries, used as {@link Natural}s through method references and composed.
 * The page {@code {{#include}}}s the anchored regions, {@code NaturalBookTest} holds the claims the
 * page makes, and the book's output gate runs {@link #main} against the comments in the regions.
 */
public final class NaturalBook {

  private NaturalBook() {}

  /** A lamp, the line the examples carry from one container to another. */
  static final LineItem LAMP = new LineItem("LAMP", 1, new BigDecimal("40.00"));

  // ANCHOR: transformations
  // Each works for every A, and none looks at the value it carries
  public static <A> Kind<ListKind.Witness, A> maybeToList(Kind<MaybeKind.Witness, A> fa) {
    Maybe<A> maybe = MAYBE.narrow(fa);
    return LIST.widen(maybe.isJust() ? List.of(maybe.get()) : List.of());
  }

  public static <A> Kind<MaybeKind.Witness, A> eitherToMaybe(
      Kind<EitherKind.Witness<String>, A> fa) {
    return MAYBE.widen(EITHER.<String, A>narrow(fa).fold(error -> Maybe.nothing(), Maybe::just));
  }

  public static <A> Kind<MaybeKind.Witness, A> firstOf(Kind<ListKind.Witness, A> fa) {
    List<A> list = LIST.narrow(fa);
    return list.isEmpty() ? MAYBE.nothing() : MAYBE.just(list.getFirst());
  }

  // ANCHOR_END: transformations

  public static void main(String[] args) {
    // ANCHOR: apply
    Natural<MaybeKind.Witness, ListKind.Witness> toList = NaturalBook::maybeToList;

    List<LineItem> found = LIST.narrow(toList.apply(MAYBE.just(LAMP)));
    // [LineItem[sku=LAMP, quantity=1, price=40.00]]

    List<LineItem> notFound = LIST.narrow(toList.apply(MAYBE.nothing()));
    // []
    // ANCHOR_END: apply
    System.out.println(found);
    System.out.println(notFound);

    // ANCHOR: compose
    Natural<EitherKind.Witness<String>, MaybeKind.Witness> toMaybe = NaturalBook::eitherToMaybe;
    Natural<EitherKind.Witness<String>, ListKind.Witness> eitherToList = toMaybe.andThen(toList);

    List<LineItem> priced = LIST.narrow(eitherToList.apply(EITHER.widen(Either.right(LAMP))));
    // [LineItem[sku=LAMP, quantity=1, price=40.00]]

    List<LineItem> unpriced =
        LIST.narrow(eitherToList.apply(EITHER.widen(Either.left("no price for LAMP"))));
    // []
    // ANCHOR_END: compose
    System.out.println(priced);
    System.out.println(unpriced);
  }
}
