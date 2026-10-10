// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics.external;

/** A bank account the payment gateway can debit. */
public record BankAccount(String iban) implements PaymentInstrument {}
