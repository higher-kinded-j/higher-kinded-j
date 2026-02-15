// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.external;

import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A builder-based immutable class representing an order line item.
 *
 * <p>This class simulates a line item entity that might come from JOOQ code generation or similar
 * ORM tools. It demonstrates the builder pattern that {@code @ViaBuilder} optics can target.
 *
 * <p>Used together with {@link OrderRecord} to demonstrate {@code @ThroughField} traversals into
 * collection fields.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * LineItemRecord item = LineItemRecord.builder()
 *     .productId("SKU-001")
 *     .productName("Widget")
 *     .quantity(3)
 *     .unitPrice(new BigDecimal("29.99"))
 *     .build();
 *
 * // Create modified copy using builder
 * LineItemRecord updated = item.toBuilder()
 *     .quantity(5)
 *     .build();
 * }</pre>
 *
 * @see OrderRecord
 * @see OrderRecordOpticsSpec
 */
public final class LineItemRecord {

  private final String productId;
  private final String productName;
  private final int quantity;
  private final BigDecimal unitPrice;

  private LineItemRecord(Builder builder) {
    this.productId = builder.productId;
    this.productName = builder.productName;
    this.quantity = builder.quantity;
    this.unitPrice = builder.unitPrice;
  }

  /** Returns a new builder for creating LineItemRecord instances. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns a builder pre-populated with this record's values for creating modified copies. */
  public Builder toBuilder() {
    return new Builder()
        .productId(productId)
        .productName(productName)
        .quantity(quantity)
        .unitPrice(unitPrice);
  }

  /** Returns the product ID (SKU). */
  public String productId() {
    return productId;
  }

  /** Returns the product name. */
  public String productName() {
    return productName;
  }

  /** Returns the quantity ordered. */
  public int quantity() {
    return quantity;
  }

  /** Returns the unit price. */
  public BigDecimal unitPrice() {
    return unitPrice;
  }

  /** Calculates the line total (quantity * unitPrice). */
  public BigDecimal lineTotal() {
    return unitPrice.multiply(BigDecimal.valueOf(quantity));
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof LineItemRecord that)) return false;
    return quantity == that.quantity
        && Objects.equals(productId, that.productId)
        && Objects.equals(productName, that.productName)
        && Objects.equals(unitPrice, that.unitPrice);
  }

  @Override
  public int hashCode() {
    return Objects.hash(productId, productName, quantity, unitPrice);
  }

  @Override
  public String toString() {
    return "LineItemRecord{"
        + "productId='"
        + productId
        + '\''
        + ", productName='"
        + productName
        + '\''
        + ", quantity="
        + quantity
        + ", unitPrice="
        + unitPrice
        + '}';
  }

  /** Builder for creating LineItemRecord instances. */
  public static final class Builder {

    private @Nullable String productId;
    private @Nullable String productName;
    private int quantity;
    private @Nullable BigDecimal unitPrice;

    private Builder() {}

    /** Sets the product ID. */
    public Builder productId(String productId) {
      this.productId = productId;
      return this;
    }

    /** Sets the product name. */
    public Builder productName(String productName) {
      this.productName = productName;
      return this;
    }

    /** Sets the quantity. */
    public Builder quantity(int quantity) {
      this.quantity = quantity;
      return this;
    }

    /** Sets the unit price. */
    public Builder unitPrice(BigDecimal unitPrice) {
      this.unitPrice = unitPrice;
      return this;
    }

    /** Builds the LineItemRecord instance. */
    public LineItemRecord build() {
      return new LineItemRecord(this);
    }
  }
}
