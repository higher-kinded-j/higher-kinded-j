// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.external;

import java.math.BigDecimal;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.ViaBuilder;

/**
 * Spec interface demonstrating how to generate optics for builder-based classes.
 *
 * <p>This example shows how to use the {@code @ViaBuilder} copy strategy for external types that
 * use the builder pattern for immutable modifications. This pattern is common in:
 *
 * <ul>
 *   <li>JOOQ generated record classes
 *   <li>Lombok {@code @Builder} annotated classes
 *   <li>AutoValue classes
 *   <li>Immutables library
 *   <li>Protocol Buffer message classes
 * </ul>
 *
 * <p>The {@code @ViaBuilder} annotation specifies:
 *
 * <ul>
 *   <li>{@code getter} - the getter method name (defaults to optic method name)
 *   <li>{@code toBuilder} - method to obtain a builder (defaults to "toBuilder")
 *   <li>{@code setter} - setter method on the builder (defaults to optic method name)
 *   <li>{@code build} - method to build the final object (defaults to "build")
 * </ul>
 *
 * <p>This example uses {@link CustomerRecord}, a hypothetical external builder-based class
 * representing a database entity that might come from JOOQ code generation or similar ORM tools.
 *
 * <p>After annotation processing, the generated {@code JooqStyleRecordOptics} class provides:
 *
 * <pre>{@code
 * JooqStyleRecordOptics.id()        // Lens<CustomerRecord, Long>
 * JooqStyleRecordOptics.name()      // Lens<CustomerRecord, String>
 * JooqStyleRecordOptics.email()     // Lens<CustomerRecord, String>
 * JooqStyleRecordOptics.creditLimit() // Lens<CustomerRecord, java.math.BigDecimal>
 * }</pre>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * CustomerRecord customer = CustomerRecord.builder()
 *     .id(1L)
 *     .name("Alice")
 *     .email("alice@example.com")
 *     .creditLimit(new BigDecimal("1000.00"))
 *     .build();
 *
 * // Update the email address (returns new CustomerRecord)
 * CustomerRecord updated = JooqStyleRecordOptics.email()
 *     .set("alice.new@example.com", customer);
 *
 * // Increase credit limit by 20%
 * CustomerRecord raised = JooqStyleRecordOptics.creditLimit()
 *     .modify(limit -> limit.multiply(new BigDecimal("1.20")), customer);
 *
 * // Compose multiple updates
 * CustomerRecord fullyUpdated = JooqStyleRecordOptics.name().modify(String::toUpperCase,
 *     JooqStyleRecordOptics.email().set("contact@example.com", customer));
 * }</pre>
 *
 * @see CustomerRecord
 * @see ViaBuilder
 * @see OpticsSpec
 */
@ImportOptics
public interface JooqStyleRecordOpticsSpec extends OpticsSpec<CustomerRecord> {

  /**
   * Lens focusing on the customer ID.
   *
   * @return a lens from CustomerRecord to its ID
   */
  @ViaBuilder
  Lens<CustomerRecord, Long> id();

  /**
   * Lens focusing on the customer name.
   *
   * @return a lens from CustomerRecord to its name
   */
  @ViaBuilder
  Lens<CustomerRecord, String> name();

  /**
   * Lens focusing on the customer email address.
   *
   * @return a lens from CustomerRecord to its email
   */
  @ViaBuilder
  Lens<CustomerRecord, String> email();

  /**
   * Lens focusing on the customer credit limit.
   *
   * @return a lens from CustomerRecord to its credit limit
   */
  @ViaBuilder
  Lens<CustomerRecord, BigDecimal> creditLimit();
}
