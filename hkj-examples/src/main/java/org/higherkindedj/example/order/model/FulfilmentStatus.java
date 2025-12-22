// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.model;

/** Status of order fulfilment. */
public enum FulfilmentStatus {
  /** All items were available and shipped. */
  COMPLETE,

  /** Some items shipped, others back-ordered. */
  PARTIAL,

  /** Order is pending fulfilment. */
  PENDING,

  /** All items were unavailable (order failed). */
  FAILED
}
