// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Examples demonstrating the {@code @ImportOptics} annotation for generating optics for external
 * types.
 *
 * <p>This package contains examples of how to use the {@code @ImportOptics} annotation to generate
 * lenses and prisms for types you do not own. It demonstrates:
 *
 * <ul>
 *   <li>Wither-based classes: {@link java.time.LocalDate}, {@link java.time.LocalTime}
 *   <li>JDK enums: {@link java.time.DayOfWeek}, {@link java.time.Month}, {@link
 *       java.util.concurrent.TimeUnit}
 *   <li>External records: {@link org.higherkindedj.example.optics.importoptics.external.Coordinate}
 *   <li>Sealed interfaces: {@link
 *       org.higherkindedj.example.optics.importoptics.external.PaymentInstrument}
 *   <li>Custom enums: {@link org.higherkindedj.example.optics.importoptics.external.TrackingStatus}
 * </ul>
 */
@ImportOptics({
  // Wither-based classes from java.time
  LocalDate.class,
  LocalTime.class,
  // JDK enums - real external types from the standard library
  DayOfWeek.class,
  Month.class,
  TimeUnit.class,
  // External record (simulated geocoding SDK)
  Coordinate.class,
  // External sealed interface (simulated payment gateway SDK)
  PaymentInstrument.class,
  // External enum (simulated courier SDK)
  TrackingStatus.class
})
package org.higherkindedj.example.optics.importoptics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.example.optics.importoptics.external.Coordinate;
import org.higherkindedj.example.optics.importoptics.external.PaymentInstrument;
import org.higherkindedj.example.optics.importoptics.external.TrackingStatus;
import org.higherkindedj.optics.annotations.ImportOptics;
