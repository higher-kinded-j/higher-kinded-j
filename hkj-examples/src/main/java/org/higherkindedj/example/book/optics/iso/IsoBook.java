// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.iso;

import static org.higherkindedj.hkt.instances.Witnesses.id;

import java.math.BigDecimal;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/iso.html">Isomorphisms</a> page. The page
 * {@code {{#include}}}s the anchored regions, and {@code IsoBookTest} holds the claims the page
 * makes about this code.
 */
public final class IsoBook {

  /** The two conversions the page's Step 2 makes, forward and back. */
  record RoundTrip(Tuple2<Integer, Integer> myTuple, Point convertedBack) {}

  private static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  private IsoBook() {}

  static RoundTrip coreOperations() {
    // ANCHOR: core_operations
    var pointToTupleIso = Converters.pointToTuple();
    var myPoint = new Point(10, 20);

    // Forward conversion: Tuple2[_1=10, _2=20]
    Tuple2<Integer, Integer> myTuple = pointToTupleIso.get(myPoint);

    // Backward conversion using the reversed Iso: Point[x=10, y=20], equal to myPoint
    Point convertedBack = pointToTupleIso.reverse().get(myTuple);
    // ANCHOR_END: core_operations
    return new RoundTrip(myTuple, convertedBack);
  }

  static Point moveX() {
    var pointToTupleIso = Converters.pointToTuple();
    var myPoint = new Point(10, 20);
    // ANCHOR: compose
    // A standard Lens that gets the first element of any Tuple2
    Lens<Tuple2<Integer, Integer>, Integer> tupleFirstElementLens =
        Lens.of(Tuple2::_1, (t, v) -> Tuple.of(v, t._2()));

    // The composition: Iso<Point, Tuple2> + Lens<Tuple2, Integer> = Lens<Point, Integer>
    Lens<Point, Integer> pointToX = pointToTupleIso.andThen(tupleFirstElementLens);

    // We can now use this new Lens to modify the 'x' coordinate: Point[x=15, y=20]
    Point movedPoint = pointToX.modify(x -> x + 5, myPoint);
    // ANCHOR_END: compose
    return movedPoint;
  }

  static Kind<IdKind.Witness, String> budget() {
    // ANCHOR: through
    // Whole cents, and the same amount in dollars to two decimal places
    Iso<Integer, BigDecimal> centsToDollars =
        Iso.of(
            cents -> BigDecimal.valueOf(cents, 2),
            dollars -> dollars.movePointRight(2).intValueExact());

    // An Id holding "Budget: 50000 cents = $500.00"
    Kind<IdKind.Witness, String> result =
        For.from(idMonad, Id.of(50000))
            .through(centsToDollars)
            .yield((cents, dollars) -> "Budget: " + cents + " cents = $" + dollars);
    // ANCHOR_END: through
    return result;
  }
}

// ANCHOR: point
record Point(int x, int y) {}

// ANCHOR_END: point

// ANCHOR: converters
class Converters {
  static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
    return Iso.of(
        // Function to get the Tuple from the Point
        point -> Tuple.of(point.x(), point.y()),
        // Function to get the Point from the Tuple
        tuple -> new Point(tuple._1(), tuple._2()));
  }
}
// ANCHOR_END: converters
