// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.cast;

import java.time.Instant;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GeneratePrisms;

// ANCHOR: consignment_state
/** Where a consignment has got to. */
@GeneratePrisms
public sealed interface ConsignmentState
    permits ConsignmentState.Pending, ConsignmentState.Dispatched, ConsignmentState.Returned {

  record Pending() implements ConsignmentState {}

  @GenerateFocus
  record Dispatched(Instant at) implements ConsignmentState {}

  @GenerateFocus
  record Returned(String reason) implements ConsignmentState {}
}
// ANCHOR_END: consignment_state
