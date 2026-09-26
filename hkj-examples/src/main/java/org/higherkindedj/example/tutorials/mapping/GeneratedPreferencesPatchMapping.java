// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.tutorials.mapping;

import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.UpdateSpec;

/**
 * Tutorial 27: the same sparse PATCH mapping over {@link GeneratedPreferencesForm}. It compiles
 * cleanly, which is the point: the defect is in the bean, where no compiler looks.
 */
@GenerateMapping
public interface GeneratedPreferencesPatchMapping
    extends UpdateSpec<GuestPreferences, GeneratedPreferencesForm> {}
