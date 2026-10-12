// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.configaudit;

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * Represents a service's top-level configuration: its name, its settings and where it deploys.
 * Lenses and Traversals will be generated for its properties.
 */
@GenerateLenses
@GenerateTraversals
public record ServiceManifest(String name, List<Setting> settings, DeploymentTarget target) {}
