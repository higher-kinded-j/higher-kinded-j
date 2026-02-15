// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.external;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A builder-based immutable class representing an order with line items.
 *
 * <p>This class simulates an order entity that might come from JOOQ code generation or similar ORM
 * tools. It demonstrates:
 *
 * <ul>
 *   <li>The builder pattern that {@code @ViaBuilder} optics can target
 *   <li>A collection field ({@code List<LineItemRecord>}) that {@code @ThroughField} can traverse
 * </ul>
 *
 * <p>The {@link OrderRecordOpticsSpec} demonstrates how to use {@code @ThroughField} to create a
 * traversal into the items list, with <b>auto-detection</b> of the appropriate traversal type.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * OrderRecord order = OrderRecord.builder()
 *     .orderId("ORD-001")
 *     .customerId(42L)
 *     .orderDate(LocalDate.now())
 *     .items(List.of(
 *         LineItemRecord.builder()
 *             .productId("SKU-001")
 *             .productName("Widget")
 *             .quantity(2)
 *             .unitPrice(new BigDecimal("19.99"))
 *             .build()
 *     ))
 *     .build();
 *
 * // Use generated optics to traverse into items
 * BigDecimal total = OrderRecordOptics.eachItem()
 *     .andThen(LineItemRecordOptics.unitPrice())
 *     .toListOf(order)
 *     .stream()
 *     .reduce(BigDecimal.ZERO, BigDecimal::add);
 * }</pre>
 *
 * @see LineItemRecord
 * @see OrderRecordOpticsSpec
 */
public final class OrderRecord {

  private final String orderId;
  private final Long customerId;
  private final LocalDate orderDate;
  private final List<LineItemRecord> items;

  private OrderRecord(Builder builder) {
    this.orderId = builder.orderId;
    this.customerId = builder.customerId;
    this.orderDate = builder.orderDate;
    this.items = builder.items != null ? List.copyOf(builder.items) : List.of();
  }

  /** Returns a new builder for creating OrderRecord instances. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns a builder pre-populated with this record's values for creating modified copies. */
  public Builder toBuilder() {
    return new Builder()
        .orderId(orderId)
        .customerId(customerId)
        .orderDate(orderDate)
        .items(new ArrayList<>(items));
  }

  /** Returns the order ID. */
  public String orderId() {
    return orderId;
  }

  /** Returns the customer ID. */
  public Long customerId() {
    return customerId;
  }

  /** Returns the order date. */
  public LocalDate orderDate() {
    return orderDate;
  }

  /** Returns an unmodifiable view of the line items. */
  public List<LineItemRecord> items() {
    return items;
  }

  /** Calculates the order total from all line items. */
  public BigDecimal orderTotal() {
    return items.stream().map(LineItemRecord::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof OrderRecord that)) return false;
    return Objects.equals(orderId, that.orderId)
        && Objects.equals(customerId, that.customerId)
        && Objects.equals(orderDate, that.orderDate)
        && Objects.equals(items, that.items);
  }

  @Override
  public int hashCode() {
    return Objects.hash(orderId, customerId, orderDate, items);
  }

  @Override
  public String toString() {
    return "OrderRecord{"
        + "orderId='"
        + orderId
        + '\''
        + ", customerId="
        + customerId
        + ", orderDate="
        + orderDate
        + ", items="
        + items
        + '}';
  }

  /** Builder for creating OrderRecord instances. */
  public static final class Builder {

    private @Nullable String orderId;
    private @Nullable Long customerId;
    private @Nullable LocalDate orderDate;
    private @Nullable List<LineItemRecord> items;

    private Builder() {}

    /** Sets the order ID. */
    public Builder orderId(String orderId) {
      this.orderId = orderId;
      return this;
    }

    /** Sets the customer ID. */
    public Builder customerId(Long customerId) {
      this.customerId = customerId;
      return this;
    }

    /** Sets the order date. */
    public Builder orderDate(LocalDate orderDate) {
      this.orderDate = orderDate;
      return this;
    }

    /** Sets the line items. */
    public Builder items(List<LineItemRecord> items) {
      this.items = items;
      return this;
    }

    /** Builds the OrderRecord instance. */
    public OrderRecord build() {
      return new OrderRecord(this);
    }
  }
}
