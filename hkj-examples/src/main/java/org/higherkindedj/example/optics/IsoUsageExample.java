// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Lenses;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateIsos;

/**
 * A runnable example demonstrating how to use an Iso (Isomorphism) to perform lossless, two-way
 * conversions between equivalent types.
 */
public class IsoUsageExample {

  // 1. Define two types that are isomorphic (hold the same information).
  public record Point(int x, int y) {}

  // A container class for our Iso definition.
  public static class Converters {
    @GenerateIsos
    public static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
      return Iso.of(
          point -> Tuple.of(point.x(), point.y()), tuple -> new Point(tuple._1(), tuple._2()));
    }
  }

  public static void main(String[] args) {
    var myPoint = new Point(10, 20);

    System.out.println("Original Point: " + myPoint);
    System.out.println("------------------------------------------");

    // 2. Get the generated Iso.
    // Corrected: Accessing the generated field directly, not as a method.
    var pointToTupleIso = ConvertersIsos.pointToTuple;

    // --- Use the Iso to perform conversions ---
    Tuple2<Integer, Integer> myTuple = pointToTupleIso.get(myPoint);
    System.out.println("After `get`:       " + myTuple);

    Point convertedBackPoint = pointToTupleIso.reverse().get(myTuple);
    System.out.println("After `reverse`:   " + convertedBackPoint);
    System.out.println("------------------------------------------");

    // 3. Compose the Iso with other optics.
    // Corrected: Using the now-generated Tuple2Lenses class.
    Lens<Tuple2<Integer, Integer>, Integer> tupleFirstElementLens = Tuple2Lenses._1();

    // The result of composing an Iso and a Lens is a new Lens.
    Lens<Point, Integer> pointToX = pointToTupleIso.andThen(tupleFirstElementLens);

    // Use the new Lens to modify the 'x' coordinate of the Point.
    Point movedPoint = pointToX.modify(x -> x + 5, myPoint);

    System.out.println("After composing with a Lens to modify 'x': " + movedPoint);
    System.out.println("Original is unchanged: " + myPoint);
  }
}
