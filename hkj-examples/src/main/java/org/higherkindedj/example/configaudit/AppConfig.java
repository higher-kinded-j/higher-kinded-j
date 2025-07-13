// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.configaudit;

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

/**
 * Represents the top-level application configuration. Lenses and Traversals will be generated for
 * its properties.
 */
@GenerateLenses
@GenerateTraversals
public record AppConfig(String appId, List<Setting> settings, DeploymentTarget target) {}
