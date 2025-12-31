// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article1.problem;

import java.math.BigDecimal;

/** An employee with their salary and contact address. */
public record Employee(String id, String name, BigDecimal salary, Address address) {}
