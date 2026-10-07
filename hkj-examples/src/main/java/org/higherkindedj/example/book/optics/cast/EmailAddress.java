// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.cast;

import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

// ANCHOR: email_address
@GenerateLenses
@GenerateFocus
public record EmailAddress(String value) {}
// ANCHOR_END: email_address
