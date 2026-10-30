// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.forstate;

import java.math.BigDecimal;
import java.util.Objects;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.optics.annotations.GenerateLenses;

// ANCHOR: checkout
/** A checkout in progress: the order, the address its receipt goes to, and what it costs. */
@GenerateLenses
public record Checkout(Order order, EmailAddress sendTo, BigDecimal total) {

  public Checkout {
    Objects.requireNonNull(order, "order");
    Objects.requireNonNull(sendTo, "sendTo");
    Objects.requireNonNull(total, "total");
  }
}
// ANCHOR_END: checkout
