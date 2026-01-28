// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge.domain;

import java.util.List;
import org.higherkindedj.example.optics.bridge.external.Address;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * Company record - the top-level domain type.
 *
 * <p>Contains references to external Address type for headquarters, and a list of Departments which
 * themselves contain external types.
 */
@GenerateFocus
@GenerateLenses
public record Company(String name, Address headquarters, List<Department> departments) {}
