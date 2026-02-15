// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import org.higherkindedj.optics.annotations.GenerateLenses;

/** Represents a player on a team. */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record Player(String name, int age, int score) {}
