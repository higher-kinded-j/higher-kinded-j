// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.math.BigDecimal;
import org.higherkindedj.optics.annotations.GenerateLenses;

/** Represents a financial account. */
@GenerateLenses
public record Account(String accountId, String owner, BigDecimal balance, AccountStatus status) {}
