// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import org.higherkindedj.example.optics.iso.CircleLenses;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Lenses;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateIsos;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A runnable example demonstrating how to use an Iso (Isomorphism) to perform lossless, two-way
 * conversions between equivalent types.
 */
public class IsoUsageExample {

  @GenerateLenses(targetPackage = "org.higherkindedj.example.optics.iso")
  public record Point(int x, int y) {}

  @GenerateLenses(targetPackage = "org.higherkindedj.example.optics.iso")
  public record Circle(Point centre, int radius) {}

  public static class Converters {
    @GenerateIsos
    public static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
      return Iso.of(
          point -> Tuple.of(point.x(), point.y()), tuple -> new Point(tuple._1(), tuple._2()));
    }

    // Additional useful Isos
    public static final Iso<Point, String> POINT_STRING =
        Iso.of(
            point -> point.x() + "," + point.y(),
            str -> {
              String[] parts = str.split(",");
              return new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            });
  }

  // Test helper
  private static <A, B> void testRoundTrip(Iso<A, B> iso, A original, String description) {
    B converted = iso.get(original);
    A roundTrip = iso.reverse().get(converted);
    System.out.println(description + ":");
    System.out.println("  Original:  " + original);
    System.out.println("  Converted: " + converted);
    System.out.println("  Round-trip: " + roundTrip);
    System.out.println("  Success: " + original.equals(roundTrip));
    System.out.println();
  }

  public static void main(String[] args) {
    // 1. Define a point and circle.
    var myPoint = new Point(10, 20);
    var myCircle = new Circle(myPoint, 5);

    System.out.println("=== ISO USAGE EXAMPLE ===");
    System.out.println("Original Point: " + myPoint);
    System.out.println("Original Circle: " + myCircle);
    System.out.println("------------------------------------------");

    // 2. Get the generated Iso.
    var pointToTupleIso = ConvertersIsos.pointToTuple;

    // --- SCENARIO 1: Direct conversions and round-trip testing ---
    System.out.println("--- Scenario 1: Direct Conversions ---");
    testRoundTrip(pointToTupleIso, myPoint, "Point to Tuple conversion");
    testRoundTrip(Converters.POINT_STRING, myPoint, "Point to String conversion");

    // --- SCENARIO 2: Using reverse() ---
    System.out.println("--- Scenario 2: Reverse Operations ---");
    var tupleToPointIso = pointToTupleIso.reverse();
    var myTuple = Tuple.of(30, 40);
    Point pointFromTuple = tupleToPointIso.get(myTuple);
    System.out.println("Tuple: " + myTuple + " -> Point: " + pointFromTuple);
    System.out.println();

    // --- SCENARIO 3: Composition with lenses ---
    System.out.println("--- Scenario 3: Composition with Lenses ---");

    // Create a lens manually that works with Point directly
    Lens<Point, Integer> pointToXLens =
        Lens.of(Point::x, (point, newX) -> new Point(newX, point.y()));

    // Use the lens
    Point movedPoint = pointToXLens.modify(x -> x + 5, myPoint);
    System.out.println("Original point: " + myPoint);
    System.out.println("After moving X by 5: " + movedPoint);
    System.out.println();

    // --- SCENARIO 4: Demonstrating Iso composition ---
    System.out.println("--- Scenario 4: Iso Composition ---");

    // Show how the Iso can be used to convert and work with tuples
    Tuple2<Integer, Integer> tupleRepresentation = pointToTupleIso.get(myPoint);
    System.out.println("Point as tuple: " + tupleRepresentation);

    // Modify the tuple using tuple operations
    Lens<Tuple2<Integer, Integer>, Integer> tupleFirstLens = Tuple2Lenses._1();
    Tuple2<Integer, Integer> modifiedTuple = tupleFirstLens.modify(x -> x * 2, tupleRepresentation);

    // Convert back to Point
    Point modifiedPoint = pointToTupleIso.reverse().get(modifiedTuple);
    System.out.println("Modified tuple: " + modifiedTuple);
    System.out.println("Back to point: " + modifiedPoint);
    System.out.println();

    // --- SCENARIO 5: String format conversions ---
    System.out.println("--- Scenario 5: String Format Conversions ---");

    String pointAsString = Converters.POINT_STRING.get(myPoint);
    System.out.println("Point as string: " + pointAsString);

    Point recoveredFromString = Converters.POINT_STRING.reverse().get(pointAsString);
    System.out.println("Recovered from string: " + recoveredFromString);
    System.out.println("Perfect round-trip: " + myPoint.equals(recoveredFromString));

    // --- SCENARIO 6: Working with Circle centre through Iso ---
    System.out.println("--- Scenario 6: Circle Centre Manipulation ---");

    // Get the centre as a tuple, modify it, and put it back
    Point originalCentre = myCircle.centre();
    Tuple2<Integer, Integer> centreAsTuple = pointToTupleIso.get(originalCentre);
    Tuple2<Integer, Integer> shiftedCentre =
        Tuple.of(centreAsTuple._1() + 10, centreAsTuple._2() + 10);
    Point newCentre = pointToTupleIso.reverse().get(shiftedCentre);
    Circle newCircle = CircleLenses.centre().set(newCentre, myCircle);

    System.out.println("Original circle: " + myCircle);
    System.out.println("Centre as tuple: " + centreAsTuple);
    System.out.println("Shifted centre tuple: " + shiftedCentre);
    System.out.println("New circle: " + newCircle);
  }
}
