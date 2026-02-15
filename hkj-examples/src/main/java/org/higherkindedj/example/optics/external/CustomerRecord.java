// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.external;

import java.math.BigDecimal;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A builder-based immutable class representing a customer entity.
 *
 * <p>This class simulates the kind of generated code you might get from JOOQ, Lombok
 * {@code @Builder}, AutoValue, or similar libraries. It demonstrates the builder pattern that
 * {@code @ViaBuilder} optics can target.
 *
 * <p>The class provides:
 *
 * <ul>
 *   <li>{@code builder()} - static factory to create a new builder
 *   <li>{@code toBuilder()} - instance method to create a builder pre-populated with current values
 *   <li>Getter methods for all fields ({@code id()}, {@code name()}, etc.)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * CustomerRecord customer = CustomerRecord.builder()
 *     .id(1L)
 *     .name("Alice")
 *     .email("alice@example.com")
 *     .creditLimit(new BigDecimal("1000.00"))
 *     .build();
 *
 * // Create modified copy using builder
 * CustomerRecord updated = customer.toBuilder()
 *     .email("alice.new@example.com")
 *     .build();
 * }</pre>
 */
public final class CustomerRecord {

  private final Long id;
  private final String name;
  private final String email;
  private final BigDecimal creditLimit;

  private CustomerRecord(Builder builder) {
    this.id = builder.id;
    this.name = builder.name;
    this.email = builder.email;
    this.creditLimit = builder.creditLimit;
  }

  /** Returns a new builder for creating CustomerRecord instances. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns a builder pre-populated with this record's values for creating modified copies. */
  public Builder toBuilder() {
    return new Builder().id(id).name(name).email(email).creditLimit(creditLimit);
  }

  /** Returns the customer ID. */
  public Long id() {
    return id;
  }

  /** Returns the customer name. */
  public String name() {
    return name;
  }

  /** Returns the customer email address. */
  public String email() {
    return email;
  }

  /** Returns the customer credit limit. */
  public BigDecimal creditLimit() {
    return creditLimit;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (!(o instanceof CustomerRecord that)) return false;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(email, that.email)
        && Objects.equals(creditLimit, that.creditLimit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, email, creditLimit);
  }

  @Override
  public String toString() {
    return "CustomerRecord{"
        + "id="
        + id
        + ", name='"
        + name
        + '\''
        + ", email='"
        + email
        + '\''
        + ", creditLimit="
        + creditLimit
        + '}';
  }

  /** Builder for creating CustomerRecord instances. */
  public static final class Builder {

    private @Nullable Long id;
    private @Nullable String name;
    private @Nullable String email;
    private @Nullable BigDecimal creditLimit;

    private Builder() {}

    /** Sets the customer ID. */
    public Builder id(Long id) {
      this.id = id;
      return this;
    }

    /** Sets the customer name. */
    public Builder name(String name) {
      this.name = name;
      return this;
    }

    /** Sets the customer email. */
    public Builder email(String email) {
      this.email = email;
      return this;
    }

    /** Sets the customer credit limit. */
    public Builder creditLimit(BigDecimal creditLimit) {
      this.creditLimit = creditLimit;
      return this;
    }

    /** Builds the CustomerRecord instance. */
    public CustomerRecord build() {
      return new CustomerRecord(this);
    }
  }
}
