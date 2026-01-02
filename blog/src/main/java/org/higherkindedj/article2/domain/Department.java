// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.util.List;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * A department with a name, manager, and staff.
 *
 * <p>The {@code @GenerateLenses} annotation generates {@code DepartmentLenses} with static lens
 * methods for each field.
 */
@GenerateLenses
public record Department(String name, Employee manager, List<Employee> staff) {}
