// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics.external;

/**
 * A geocoding service's SDK record, simulated here: a guest from outside the order service. The
 * geocoder owns it, so this package imports its optics rather than annotating it.
 */
public record Coordinate(double lat, double lon) {}
