// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.iso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.optics.laws.IsoLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Isomorphisms page makes about its Point, tuple and cents isos. */
@DisplayName("the Isomorphisms page: converting a Point, and cents to dollars")
class IsoBookTest {

  @Test
  @DisplayName("get converts the Point to a tuple, and the reversed iso converts it back")
  void getAndReverseRoundTrip() {
    IsoBook.RoundTrip roundTrip = IsoBook.coreOperations();

    assertThat(roundTrip.myTuple()).isEqualTo(Tuple.of(10, 20)).hasToString("Tuple2[_1=10, _2=20]");
    assertThat(roundTrip.convertedBack())
        .isEqualTo(new Point(10, 20))
        .hasToString("Point[x=10, y=20]");
  }

  @Test
  @DisplayName("the iso composed with a tuple lens moves the Point's x")
  void composedLensMovesX() {
    assertThat(IsoBook.moveX()).isEqualTo(new Point(15, 20)).hasToString("Point[x=15, y=20]");
  }

  @Test
  @DisplayName("through keeps the cents and the dollars, at two decimal places, in scope")
  void throughKeepsBothRepresentations() {
    assertThat(ID.narrow(IsoBook.budget()).value()).isEqualTo("Budget: 50000 cents = $500.00");
  }

  @Test
  @DisplayName("IsoLaws passes the Point iso on both round trips")
  void pointIsoIsLawful() {
    IsoLaws.assertIsoLaws(Converters.pointToTuple(), new Point(10, 20), Tuple.of(10, 20));
  }
}
