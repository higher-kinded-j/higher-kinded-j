// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the prism should match based on an instanceof check.
 *
 * <p>Apply this to abstract methods in an {@link OpticsSpec} interface to generate a prism that
 * uses Java's pattern matching instanceof to extract a specific subtype from a base type.
 *
 * <p>The processor will generate code like:
 *
 * <pre>{@code
 * Prism.of(
 *     source -> source instanceof SubType s ? Optional.of(s) : Optional.empty(),
 *     subtype -> subtype
 * )
 * }</pre>
 *
 * <p>This is particularly useful for working with sealed type hierarchies where you want to focus
 * on a specific variant.
 *
 * <p>Example:
 *
 * <pre>{@code
 * // Sealed hierarchy
 * sealed interface PaymentMethod permits CreditCard, BankTransfer, Wallet {}
 * record CreditCard(String number, String expiry) implements PaymentMethod {}
 * record BankTransfer(String iban) implements PaymentMethod {}
 * record Wallet(String provider) implements PaymentMethod {}
 *
 * @ImportOptics
 * interface PaymentMethodOptics extends OpticsSpec<PaymentMethod> {
 *
 *     @InstanceOf(CreditCard.class)
 *     Prism<PaymentMethod, CreditCard> creditCard();
 *
 *     @InstanceOf(BankTransfer.class)
 *     Prism<PaymentMethod, BankTransfer> bankTransfer();
 *
 *     @InstanceOf(Wallet.class)
 *     Prism<PaymentMethod, Wallet> wallet();
 * }
 * }</pre>
 *
 * <p><strong>Note:</strong> The processor validates that the target class is a subtype of the
 * source type from {@code OpticsSpec<S>}. If validation fails, a compile-time error is reported.
 *
 * @see OpticsSpec
 * @see MatchWhen
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface InstanceOf {

  /**
   * The subtype to match against.
   *
   * <p>Must be a subtype of the source type {@code S} from {@code OpticsSpec<S>}.
   *
   * @return the target subtype class
   */
  Class<?> value();
}
