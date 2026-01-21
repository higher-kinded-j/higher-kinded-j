// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics;

import java.time.LocalDate;
import java.time.LocalTime;
import org.higherkindedj.optics.Lens;

/**
 * A runnable example demonstrating how to use {@code @ImportOptics} to generate lenses for external
 * types like {@link java.time.LocalDate}.
 *
 * <p>The {@code @ImportOptics} annotation in the package-info.java file generates lenses for {@code
 * LocalDate} and {@code LocalTime}, allowing immutable updates via their wither methods.
 */
public class ImportOpticsBasicExample {

  public static void main(String[] args) {
    System.out.println("=== ImportOptics Basic Example ===\n");

    // 1. Working with LocalDate
    LocalDate today = LocalDate.of(2024, 6, 15);
    System.out.println("Original date: " + today);

    // Get the generated lens for the year field
    Lens<LocalDate, Integer> yearLens = LocalDateLenses.year();

    // Use the lens to read the year
    int year = yearLens.get(today);
    System.out.println("Year via lens: " + year);

    // Use the lens to update the year immutably
    LocalDate nextYear = yearLens.modify(y -> y + 1, today);
    System.out.println("Next year: " + nextYear);

    // Use the convenience with method
    LocalDate specificYear = LocalDateLenses.withYear(today, 2030);
    System.out.println("Year 2030: " + specificYear);

    System.out.println();

    // 2. Composing multiple field updates
    // Update multiple fields by chaining lens operations
    LocalDate modified = LocalDateLenses.withYear(LocalDateLenses.withDayOfMonth(today, 1), 2025);
    System.out.println("Modified (Jan 1, 2025): " + modified);

    System.out.println();

    // 3. Working with LocalTime
    LocalTime time = LocalTime.of(14, 30, 0);
    System.out.println("Original time: " + time);

    // Get the hour lens
    Lens<LocalTime, Integer> hourLens = LocalTimeLenses.hour();
    int hour = hourLens.get(time);
    System.out.println("Hour via lens: " + hour);

    // Modify the hour
    LocalTime evening = hourLens.set(20, time);
    System.out.println("Evening time: " + evening);

    System.out.println();

    // 4. Using modify for calculations
    // Add 2 hours to the time
    LocalTime laterTime = hourLens.modify(h -> (h + 2) % 24, time);
    System.out.println("Two hours later: " + laterTime);

    System.out.println("\n=== Example Complete ===");
  }
}
