// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.problem;

import java.util.List;

/** A department containing employees. */
public record Department(String name, Employee manager, List<Employee> staff) {}
