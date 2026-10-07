// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.cast;

import java.util.Optional;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

// ANCHOR: customer_profile
@GenerateLenses
@GenerateFocus
public record CustomerProfile(
    String name, Optional<String> nickname, Optional<EmailAddress> altEmail) {}
// ANCHOR_END: customer_profile
