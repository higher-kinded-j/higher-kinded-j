// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.cast;

import java.util.UUID;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

// ANCHOR: consignment
@GenerateLenses
@GenerateFocus(generateNavigators = true)
public record Consignment(UUID orderId, Address to, ConsignmentState state) {}
// ANCHOR_END: consignment
