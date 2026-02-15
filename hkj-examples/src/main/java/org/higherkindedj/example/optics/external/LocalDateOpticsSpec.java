// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.external;

import java.time.LocalDate;
import java.time.Month;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.Wither;

/**
 * Spec interface demonstrating how to generate optics for {@code java.time.LocalDate}.
 *
 * <p>This is an example of using the {@code @Wither} copy strategy for external types that use the
 * wither pattern (methods like {@code withYear()}, {@code withMonth()}, etc.) for immutable
 * modifications.
 *
 * <p>The {@code @Wither} annotation specifies:
 *
 * <ul>
 *   <li>{@code value} - the wither method name (e.g., "withYear")
 *   <li>{@code getter} - the getter method name (e.g., "getYear")
 * </ul>
 *
 * <p>After annotation processing, the generated {@code LocalDateOptics} class provides:
 *
 * <pre>{@code
 * LocalDateOptics.year()       // Lens<LocalDate, Integer>
 * LocalDateOptics.monthValue() // Lens<LocalDate, Integer>
 * LocalDateOptics.dayOfMonth() // Lens<LocalDate, Integer>
 * LocalDateOptics.dayOfYear()  // Lens<LocalDate, Integer>
 * }</pre>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * LocalDate date = LocalDate.of(2024, 6, 15);
 *
 * // Get the year
 * int year = LocalDateOptics.year().get(date); // 2024
 *
 * // Set a new year (returns new LocalDate)
 * LocalDate nextYear = LocalDateOptics.year().set(2025, date);
 *
 * // Modify the day
 * LocalDate endOfMonth = LocalDateOptics.dayOfMonth().set(30, date);
 *
 * // Compose lenses for complex updates
 * LocalDate adjusted = LocalDateOptics.year().modify(y -> y + 1,
 *     LocalDateOptics.monthValue().set(1, date));  // January = 1
 * }</pre>
 *
 * @see java.time.LocalDate
 * @see Wither
 * @see OpticsSpec
 */
@ImportOptics
public interface LocalDateOpticsSpec extends OpticsSpec<LocalDate> {

  /**
   * Lens focusing on the year component of a LocalDate.
   *
   * @return a lens from LocalDate to its year as an integer
   */
  @Wither(value = "withYear", getter = "getYear")
  Lens<LocalDate, Integer> year();

  /**
   * Lens focusing on the month component as an integer (1-12).
   *
   * <p>Note: {@code LocalDate.withMonth()} accepts an int, so we use {@code getMonthValue()} which
   * returns an int. For working with {@link Month} enums, use {@code monthValue().modify(m ->
   * Month.JANUARY.getValue(), date)} or similar patterns.
   *
   * @return a lens from LocalDate to its month value as an integer
   */
  @Wither(value = "withMonth", getter = "getMonthValue")
  Lens<LocalDate, Integer> monthValue();

  /**
   * Lens focusing on the day of month component (1-31).
   *
   * @return a lens from LocalDate to its day of month
   */
  @Wither(value = "withDayOfMonth", getter = "getDayOfMonth")
  Lens<LocalDate, Integer> dayOfMonth();

  /**
   * Lens focusing on the day of year component (1-366).
   *
   * @return a lens from LocalDate to its day of year
   */
  @Wither(value = "withDayOfYear", getter = "getDayOfYear")
  Lens<LocalDate, Integer> dayOfYear();
}
