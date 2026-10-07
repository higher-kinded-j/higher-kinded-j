// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.cast;

import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

// ANCHOR: address
@GenerateLenses
@GenerateFocus
public record Address(String street, String city, String postcode) {}
// ANCHOR_END: address
