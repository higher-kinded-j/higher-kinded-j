// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.external;

import java.math.BigDecimal;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.ViaBuilder;

/**
 * Spec interface for generating optics for {@link LineItemRecord}.
 *
 * <p>This example demonstrates {@code @ViaBuilder} for a simple builder-based type. The generated
 * optics can be composed with {@link OrderRecordOpticsSpec}'s traversal to modify line items within
 * an order.
 *
 * <p>After annotation processing, the generated {@code LineItemRecordOptics} class provides:
 *
 * <pre>{@code
 * LineItemRecordOptics.productId()    // Lens<LineItemRecord, String>
 * LineItemRecordOptics.productName()  // Lens<LineItemRecord, String>
 * LineItemRecordOptics.quantity()     // Lens<LineItemRecord, Integer>
 * LineItemRecordOptics.unitPrice()    // Lens<LineItemRecord, BigDecimal>
 * }</pre>
 *
 * <p>Usage with order traversal:
 *
 * <pre>{@code
 * // Apply 10% discount to all items in an order
 * OrderRecord discounted = OrderRecordOptics.eachItem()
 *     .andThen(LineItemRecordOptics.unitPrice())
 *     .modify(price -> price.multiply(new BigDecimal("0.90")), order);
 *
 * // Double the quantity of all items
 * OrderRecord doubled = OrderRecordOptics.eachItem()
 *     .andThen(LineItemRecordOptics.quantity())
 *     .modify(qty -> qty * 2, order);
 * }</pre>
 *
 * @see LineItemRecord
 * @see OrderRecordOpticsSpec
 */
@ImportOptics
public interface LineItemRecordOpticsSpec extends OpticsSpec<LineItemRecord> {

  /**
   * Lens focusing on the product ID.
   *
   * @return a lens from LineItemRecord to its product ID
   */
  @ViaBuilder
  Lens<LineItemRecord, String> productId();

  /**
   * Lens focusing on the product name.
   *
   * @return a lens from LineItemRecord to its product name
   */
  @ViaBuilder
  Lens<LineItemRecord, String> productName();

  /**
   * Lens focusing on the quantity.
   *
   * @return a lens from LineItemRecord to its quantity
   */
  @ViaBuilder
  Lens<LineItemRecord, Integer> quantity();

  /**
   * Lens focusing on the unit price.
   *
   * @return a lens from LineItemRecord to its unit price
   */
  @ViaBuilder
  Lens<LineItemRecord, BigDecimal> unitPrice();
}
