// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import org.higherkindedj.optics.annotations.GenerateLenses;

/** Represents user profile settings and preferences. */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record ProfileSettings(
    boolean emailNotifications, boolean smsNotifications, String preferredLanguage, String theme) {}
