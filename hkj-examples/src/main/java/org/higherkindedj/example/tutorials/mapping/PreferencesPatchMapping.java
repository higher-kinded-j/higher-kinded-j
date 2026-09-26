// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.UpdateSpec;

/** Tutorial 27: the sparse PATCH mapping over the well-behaved {@link PreferencesForm}. */
@GenerateMapping
public interface PreferencesPatchMapping extends UpdateSpec<GuestPreferences, PreferencesForm> {}
