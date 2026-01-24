// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics.external;

/** A bank transfer payment method. */
public record BankTransfer(String accountNumber, String sortCode) implements PaymentMethod {}
