// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import java.math.BigDecimal;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * An employee with id, name, address, and salary.
 *
 * <p>The {@code @GenerateLenses} annotation generates {@code EmployeeLenses} with static lens
 * methods for each field.
 */
@GenerateLenses
public record Employee(String id, String name, Address address, BigDecimal salary) {}
