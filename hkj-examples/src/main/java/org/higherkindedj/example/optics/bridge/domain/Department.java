// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge.domain;

import java.util.List;
import org.higherkindedj.example.optics.bridge.external.Address;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

/** Department record - a local domain type with external {@link Address} location. */
@GenerateFocus
@GenerateLenses
public record Department(String name, Employee manager, List<Employee> staff, Address location) {}
