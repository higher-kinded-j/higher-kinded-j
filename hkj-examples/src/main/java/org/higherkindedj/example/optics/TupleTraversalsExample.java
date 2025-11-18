// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;
import org.higherkindedj.optics.util.TupleTraversals;

/**
 * Demonstrates tuple traversals for parallel operations on Tuple2 pairs using {@link
 * TupleTraversals}.
 *
 * <p>The {@code both()} traversal focuses on both elements of a Tuple2 when they share the same
 * type, enabling:
 *
 * <ul>
 *   <li>Parallel transformations on paired data
 *   <li>Coordinate system operations (latitude/longitude, x/y)
 *   <li>Range and boundary manipulations (min/max, start/end)
 *   <li>Simultaneous updates to homogeneous pairs
 * </ul>
 *
 * <p>This example covers:
 *
 * <ul>
 *   <li>Geographic coordinate transformations
 *   <li>Statistical range operations
 *   <li>Bounding box calculations
 *   <li>Time period adjustments
 *   <li>Normalisation and scaling
 * </ul>
 */
public class TupleTraversalsExample {

  public static void main(String[] args) {
    System.out.println("=== TUPLE TRAVERSALS EXAMPLE ===\n");

    demonstrateBasicTupleOperations();
    demonstrateGeographicCoordinates();
    demonstrateStatisticalRanges();
    demonstrateBoundingBoxes();
    demonstrateTimePeriods();

    System.out.println("\n=== TUPLE TRAVERSALS COMPLETE ===");
  }

  private static void demonstrateBasicTupleOperations() {
    System.out.println("--- SCENARIO 1: Basic Tuple Operations ---\n");

    // Create a tuple traversal
    Traversal<Tuple2<Integer, Integer>, Integer> both = TupleTraversals.both();

    // Basic transformation
    Tuple2<Integer, Integer> range = new Tuple2<>(10, 20);
    Tuple2<Integer, Integer> doubled = Traversals.modify(both, x -> x * 2, range);

    System.out.println("Original range: " + range);
    System.out.println("Doubled:        " + doubled);

    // Extract both elements
    List<Integer> values = Traversals.getAll(both, range);
    System.out.println("Extracted:      " + values);

    // String tuple transformation
    Traversal<Tuple2<String, String>, String> bothStrings = TupleTraversals.both();
    Tuple2<String, String> names = new Tuple2<>("alice", "bob");
    Tuple2<String, String> capitalised =
        Traversals.modify(
            bothStrings, s -> s.substring(0, 1).toUpperCase() + s.substring(1), names);

    System.out.println("\nOriginal names:  " + names);
    System.out.println("Capitalised:     " + capitalised);
    System.out.println();
  }

  private static void demonstrateGeographicCoordinates() {
    System.out.println("--- SCENARIO 2: Geographic Coordinates ---\n");

    record Location(String name, Tuple2<Double, Double> coordinates) {
      // coordinates: (latitude, longitude)
      @Override
      public String toString() {
        return String.format("%s (lat: %.6f, lon: %.6f)", name, coordinates._1(), coordinates._2());
      }
    }

    Traversal<Tuple2<Double, Double>, Double> bothCoords = TupleTraversals.both();

    Location london = new Location("London", new Tuple2<>(51.5074, -0.1278));
    Location paris = new Location("Paris", new Tuple2<>(48.8566, 2.3522));

    System.out.println("Original locations:");
    System.out.println("  " + london);
    System.out.println("  " + paris);

    // Round coordinates to 2 decimal places
    double roundingFactor = 100.0;
    Location londonRounded =
        new Location(
            london.name(),
            Traversals.modify(
                bothCoords,
                coord -> Math.round(coord * roundingFactor) / roundingFactor,
                london.coordinates()));

    Location parisRounded =
        new Location(
            paris.name(),
            Traversals.modify(
                bothCoords,
                coord -> Math.round(coord * roundingFactor) / roundingFactor,
                paris.coordinates()));

    System.out.println("\nRounded to 2 decimals:");
    System.out.println("  " + londonRounded);
    System.out.println("  " + parisRounded);

    // Apply offset to both coordinates (e.g., coordinate system adjustment)
    double offset = 0.001; // Small offset
    Location londonOffset =
        new Location(
            london.name() + " (offset)",
            Traversals.modify(bothCoords, coord -> coord + offset, london.coordinates()));

    System.out.println("\nWith offset +0.001:");
    System.out.println("  " + londonOffset);

    // Scale coordinates (useful for projection transformations)
    double scale = 1000000.0; // Convert to microDegrees
    Tuple2<Double, Double> scaled =
        Traversals.modify(bothCoords, coord -> coord * scale, london.coordinates());

    System.out.println("\nScaled to microDegrees:");
    System.out.println("  " + scaled);
    System.out.println();
  }

  private static void demonstrateStatisticalRanges() {
    System.out.println("--- SCENARIO 3: Statistical Ranges ---\n");

    record DataRange(String metric, Tuple2<Double, Double> range) {
      // range: (min, max)
      double spread() {
        return range._2() - range._1();
      }

      @Override
      public String toString() {
        return String.format(
            "%s: [%.2f, %.2f] (spread: %.2f)", metric, range._1(), range._2(), spread());
      }
    }

    Traversal<Tuple2<Double, Double>, Double> bothValues = TupleTraversals.both();

    DataRange temperature = new DataRange("Temperature (°C)", new Tuple2<>(-5.0, 35.0));
    DataRange humidity = new DataRange("Humidity (%)", new Tuple2<>(20.0, 95.0));

    System.out.println("Original ranges:");
    System.out.println("  " + temperature);
    System.out.println("  " + humidity);

    // Convert temperature to Fahrenheit
    DataRange tempFahrenheit =
        new DataRange(
            "Temperature (°F)",
            Traversals.modify(
                bothValues, celsius -> celsius * 9.0 / 5.0 + 32.0, temperature.range()));

    System.out.println("\nConverted to Fahrenheit:");
    System.out.println("  " + tempFahrenheit);

    // Normalise range to [0, 1]
    double minTemp = temperature.range._1();
    double maxTemp = temperature.range._2();
    double range = maxTemp - minTemp;

    Tuple2<Double, Double> normalised =
        Traversals.modify(bothValues, temp -> (temp - minTemp) / range, temperature.range());

    System.out.println("\nNormalised temperature range:");
    System.out.println("  " + normalised);

    // Expand range by 10% on both sides
    DataRange expanded =
        new DataRange(
            humidity.metric() + " (expanded)",
            Traversals.modify(
                bothValues,
                value -> {
                  double spread = humidity.range._2() - humidity.range._1();
                  return value == humidity.range._1()
                      ? value - spread * 0.1 // Expand min
                      : value + spread * 0.1; // Expand max
                },
                humidity.range()));

    System.out.println("\nExpanded humidity range by 10%:");
    System.out.println("  " + humidity);
    System.out.println("  " + expanded);
    System.out.println();
  }

  private static void demonstrateBoundingBoxes() {
    System.out.println("--- SCENARIO 4: Bounding Box Operations ---\n");

    record BoundingBox(Tuple2<Integer, Integer> topLeft, Tuple2<Integer, Integer> bottomRight) {
      int width() {
        return bottomRight._1() - topLeft._1();
      }

      int height() {
        return bottomRight._2() - topLeft._2();
      }

      @Override
      public String toString() {
        return String.format(
            "Box[TL:(%d,%d), BR:(%d,%d), Size:%dx%d]",
            topLeft._1(), topLeft._2(), bottomRight._1(), bottomRight._2(), width(), height());
      }
    }

    Traversal<Tuple2<Integer, Integer>, Integer> bothCoords = TupleTraversals.both();

    BoundingBox box = new BoundingBox(new Tuple2<>(10, 10), new Tuple2<>(100, 50));

    System.out.println("Original bounding box:");
    System.out.println("  " + box);

    // Scale top-left corner
    Tuple2<Integer, Integer> scaledTopLeft =
        Traversals.modify(bothCoords, coord -> coord * 2, box.topLeft());
    BoundingBox scaledBox = new BoundingBox(scaledTopLeft, box.bottomRight());

    System.out.println("\nTop-left corner scaled 2x:");
    System.out.println("  " + scaledBox);

    // Offset entire box
    int offsetX = 50;
    int offsetY = 20;
    BoundingBox offsetBox =
        new BoundingBox(
            Traversals.modify(bothCoords, coord -> coord + offsetX, box.topLeft()),
            Traversals.modify(bothCoords, coord -> coord + offsetX, box.bottomRight()));

    System.out.println("\nOffset by (50, 50):");
    System.out.println("  " + offsetBox);

    // Clamp coordinates to positive values
    Tuple2<Integer, Integer> negativeCoords = new Tuple2<>(-5, -10);
    Tuple2<Integer, Integer> clamped =
        Traversals.modify(bothCoords, coord -> Math.max(0, coord), negativeCoords);

    System.out.println("\nClamping negative coordinates:");
    System.out.println("  Original: " + negativeCoords);
    System.out.println("  Clamped:  " + clamped);
    System.out.println();
  }

  private static void demonstrateTimePeriods() {
    System.out.println("--- SCENARIO 5: Time Period Operations ---\n");

    record TimePeriod(String name, Tuple2<Integer, Integer> hourRange) {
      // hourRange: (startHour, endHour) in 24-hour format
      int duration() {
        return hourRange._2() - hourRange._1();
      }

      @Override
      public String toString() {
        return String.format(
            "%s: %02d:00-%02d:00 (%d hours)", name, hourRange._1(), hourRange._2(), duration());
      }
    }

    Traversal<Tuple2<Integer, Integer>, Integer> bothHours = TupleTraversals.both();

    TimePeriod workHours = new TimePeriod("Work Hours", new Tuple2<>(9, 17));
    TimePeriod eveningShift = new TimePeriod("Evening Shift", new Tuple2<>(17, 23));

    System.out.println("Original time periods:");
    System.out.println("  " + workHours);
    System.out.println("  " + eveningShift);

    // Shift work hours forward by 2 hours
    TimePeriod shiftedWork =
        new TimePeriod(
            "Shifted Work Hours",
            Traversals.modify(bothHours, hour -> hour + 2, workHours.hourRange()));

    System.out.println("\nShifted forward 2 hours:");
    System.out.println("  " + shiftedWork);

    // Extend both periods by 1 hour
    TimePeriod extendedWork =
        new TimePeriod(
            "Extended Work",
            Traversals.modify(
                bothHours,
                hour -> {
                  // Extend start earlier, end later
                  return hour == workHours.hourRange._1() ? hour - 1 : hour + 1;
                },
                workHours.hourRange()));

    System.out.println("\nExtended by 1 hour on both ends:");
    System.out.println("  " + workHours);
    System.out.println("  " + extendedWork);

    // Modulo 24 for wrap-around
    Tuple2<Integer, Integer> late = new Tuple2<>(22, 26); // 26 should wrap to 2
    Tuple2<Integer, Integer> wrapped = Traversals.modify(bothHours, hour -> hour % 24, late);

    System.out.println("\nWrap-around for 24-hour format:");
    System.out.println("  Original: " + late);
    System.out.println("  Wrapped:  " + wrapped);
  }
}
