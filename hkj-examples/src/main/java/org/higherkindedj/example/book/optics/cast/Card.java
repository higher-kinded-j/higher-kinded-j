// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.cast;

import org.higherkindedj.optics.annotations.GenerateFocus;

// ANCHOR: card
@GenerateFocus
public record Card(String pan) implements Payment {}
// ANCHOR_END: card
