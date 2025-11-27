// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/** Represents a team of players. */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
@GenerateTraversals(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record Team(String name, List<Player> players) {}
