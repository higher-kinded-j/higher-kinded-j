// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.UpdateSpec;

/**
 * Tutorial 26: the sparse PATCH sibling. Extending {@link UpdateSpec} opts into null-as-absent; the
 * inherited email leaf still validates a present value.
 */
@GenerateMapping
public interface GuestPatchMapping extends GuestVocabulary, UpdateSpec<Guest, GuestPatchForm> {}
