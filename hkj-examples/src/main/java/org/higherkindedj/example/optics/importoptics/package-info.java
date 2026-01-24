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
 *   <li>External records: {@link org.higherkindedj.example.optics.importoptics.external.Customer}
 *   <li>Sealed interfaces: {@link
 *       org.higherkindedj.example.optics.importoptics.external.PaymentMethod}
 *   <li>Custom enums: {@link org.higherkindedj.example.optics.importoptics.external.OrderStatus}
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
  // External record (simulated third-party library)
  Customer.class,
  // External sealed interface (simulated third-party library)
  PaymentMethod.class,
  // External enum (simulated third-party library)
  OrderStatus.class
})
package org.higherkindedj.example.optics.importoptics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.example.optics.importoptics.external.Customer;
import org.higherkindedj.example.optics.importoptics.external.OrderStatus;
import org.higherkindedj.example.optics.importoptics.external.PaymentMethod;
import org.higherkindedj.optics.annotations.ImportOptics;
