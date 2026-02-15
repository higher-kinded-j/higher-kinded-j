// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent.model;

import java.math.BigDecimal;
import java.time.Instant;
import org.higherkindedj.optics.annotations.GenerateLenses;

/** Represents a financial transaction between accounts. */
@GenerateLenses(targetPackage = "org.higherkindedj.example.optics.fluent.generated")
public record Transaction(
    String txnId,
    Account fromAccount,
    Account toAccount,
    BigDecimal amount,
    TransactionStatus status,
    Instant timestamp) {}
