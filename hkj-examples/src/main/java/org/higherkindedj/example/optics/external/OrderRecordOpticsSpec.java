// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.external;

import java.time.LocalDate;
import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.ThroughField;
import org.higherkindedj.optics.annotations.ViaBuilder;

/**
 * Spec interface demonstrating {@code @ThroughField} auto-detection for collection traversals.
 *
 * <p>This example shows the key feature: <b>automatic traversal detection</b>. The {@code
 * eachItem()} method uses {@code @ThroughField(field = "items")} without specifying a traversal -
 * the processor automatically detects that {@code items} is a {@code List<LineItemRecord>} and uses
 * {@code Traversals.forList()}.
 *
 * <h2>Auto-Detection Support</h2>
 *
 * The processor auto-detects traversals for these container types:
 *
 * <ul>
 *   <li>{@code List<A>} (and subtypes like ArrayList, LinkedList) → {@code Traversals.forList()}
 *   <li>{@code Set<A>} (and subtypes like HashSet, TreeSet) → {@code Traversals.forSet()}
 *   <li>{@code Optional<A>} → {@code Traversals.forOptional()}
 *   <li>{@code A[]} → {@code Traversals.forArray()}
 *   <li>{@code Map<K,V>} (and subtypes like HashMap, TreeMap) → {@code Traversals.forMapValues()}
 * </ul>
 *
 * <h2>Generated Code</h2>
 *
 * After annotation processing, the generated {@code OrderRecordOptics} class provides:
 *
 * <pre>{@code
 * OrderRecordOptics.orderId()     // Lens<OrderRecord, String>
 * OrderRecordOptics.customerId()  // Lens<OrderRecord, Long>
 * OrderRecordOptics.orderDate()   // Lens<OrderRecord, LocalDate>
 * OrderRecordOptics.items()       // Lens<OrderRecord, List<LineItemRecord>>
 * OrderRecordOptics.eachItem()    // Traversal<OrderRecord, LineItemRecord>
 * }</pre>
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>{@code
 * // Get all product names from an order
 * List<String> productNames = OrderRecordOptics.eachItem()
 *     .andThen(LineItemRecordOptics.productName())
 *     .toListOf(order);
 *
 * // Apply 15% discount to all items
 * OrderRecord discounted = OrderRecordOptics.eachItem()
 *     .andThen(LineItemRecordOptics.unitPrice())
 *     .modify(price -> price.multiply(new BigDecimal("0.85")), order);
 *
 * // Increment all quantities by 1
 * OrderRecord moreItems = OrderRecordOptics.eachItem()
 *     .andThen(LineItemRecordOptics.quantity())
 *     .modify(qty -> qty + 1, order);
 *
 * // Calculate total from all items
 * BigDecimal total = OrderRecordOptics.eachItem()
 *     .andThen(LineItemRecordOptics.unitPrice())
 *     .toListOf(order)
 *     .stream()
 *     .reduce(BigDecimal.ZERO, BigDecimal::add);
 * }</pre>
 *
 * @see OrderRecord
 * @see LineItemRecord
 * @see LineItemRecordOpticsSpec
 * @see ThroughField
 */
@ImportOptics
public interface OrderRecordOpticsSpec extends OpticsSpec<OrderRecord> {

  /**
   * Lens focusing on the order ID.
   *
   * @return a lens from OrderRecord to its order ID
   */
  @ViaBuilder
  Lens<OrderRecord, String> orderId();

  /**
   * Lens focusing on the customer ID.
   *
   * @return a lens from OrderRecord to its customer ID
   */
  @ViaBuilder
  Lens<OrderRecord, Long> customerId();

  /**
   * Lens focusing on the order date.
   *
   * @return a lens from OrderRecord to its order date
   */
  @ViaBuilder
  Lens<OrderRecord, LocalDate> orderDate();

  /**
   * Lens focusing on the items list as a whole.
   *
   * <p>Use this when you want to replace the entire list of items.
   *
   * @return a lens from OrderRecord to its items list
   */
  @ViaBuilder
  Lens<OrderRecord, List<LineItemRecord>> items();

  /**
   * Traversal focusing on each individual item in the order.
   *
   * <p><b>Auto-detection in action:</b> The {@code traversal} parameter is not specified here. The
   * processor detects that the {@code items} field is a {@code List<LineItemRecord>} and
   * automatically uses {@code Traversals.forList()}.
   *
   * <p>This is equivalent to writing:
   *
   * <pre>{@code
   * @ThroughField(
   *     field = "items",
   *     traversal = "org.higherkindedj.optics.util.Traversals.forList()"
   * )
   * }</pre>
   *
   * <p>But the auto-detection makes it much cleaner and less error-prone.
   *
   * @return a traversal from OrderRecord to each LineItemRecord
   */
  @ThroughField(field = "items")
  Traversal<OrderRecord, LineItemRecord> eachItem();
}
