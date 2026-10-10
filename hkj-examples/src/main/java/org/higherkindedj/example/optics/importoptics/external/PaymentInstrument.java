// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics.external;

/**
 * A payment gateway's SDK type, simulated here: a guest from outside the order service. The gateway
 * owns it, so this package imports its optics rather than annotating it, and it keeps the gateway's
 * names rather than the order service's {@code Payment}, {@code Card} and {@code Bank}.
 */
public sealed interface PaymentInstrument permits CardInstrument, BankAccount, CryptoWallet {}
