// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics;

import java.time.DayOfWeek;
import java.time.Month;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.example.optics.importoptics.external.BankTransfer;
import org.higherkindedj.example.optics.importoptics.external.CreditCard;
import org.higherkindedj.example.optics.importoptics.external.DigitalWallet;
import org.higherkindedj.example.optics.importoptics.external.OrderStatus;
import org.higherkindedj.example.optics.importoptics.external.PaymentMethod;
import org.higherkindedj.optics.Prism;

/**
 * A runnable example demonstrating how to use {@code @ImportOptics} to generate prisms for external
 * sealed interfaces and enums.
 *
 * <p>This example shows that sealed interfaces and enums from external libraries can have prisms
 * generated via the {@code @ImportOptics} annotation. It demonstrates:
 *
 * <ul>
 *   <li>JDK enums: {@link DayOfWeek}, {@link Month}, {@link TimeUnit}
 *   <li>Custom sealed interfaces: PaymentMethod
 *   <li>Custom enums: OrderStatus
 * </ul>
 */
public class ImportOpticsSealedExample {

  public static void main(String[] args) {
    System.out.println("=== ImportOptics Sealed Interface & Enum Example ===\n");

    // ===== JDK ENUM PRISMS =====
    demonstrateJdkEnums();

    // ===== SEALED INTERFACE PRISMS =====
    System.out.println("--- Sealed Interface Prisms (Custom Types) ---\n");

    // 1. Create instances of the sealed interface subtypes
    PaymentMethod creditCard = new CreditCard("4111-1111-1111-1111", "12/25");
    PaymentMethod bankTransfer = new BankTransfer("12345678", "00-11-22");
    PaymentMethod digitalWallet = new DigitalWallet("PayPal", "user@paypal.com");

    // 2. Get the generated prisms for each subtype
    Prism<PaymentMethod, CreditCard> creditCardPrism = PaymentMethodPrisms.creditCard();
    Prism<PaymentMethod, BankTransfer> bankTransferPrism = PaymentMethodPrisms.bankTransfer();
    Prism<PaymentMethod, DigitalWallet> digitalWalletPrism = PaymentMethodPrisms.digitalWallet();

    // 3. Use getOptional to safely extract subtypes
    Optional<CreditCard> asCreditCard = creditCardPrism.getOptional(creditCard);
    System.out.println("Credit card as CreditCard: " + asCreditCard);

    Optional<BankTransfer> asBankTransfer = bankTransferPrism.getOptional(creditCard);
    System.out.println("Credit card as BankTransfer: " + asBankTransfer); // Empty

    // 4. Use getOrModify for pattern matching
    System.out.println("\nProcessing payments:");
    for (PaymentMethod payment : new PaymentMethod[] {creditCard, bankTransfer, digitalWallet}) {
      String description = describePayment(payment);
      System.out.println("  " + description);
    }

    // 5. Use build to construct values (upcast)
    CreditCard newCard = new CreditCard("5500-0000-0000-0004", "06/28");
    PaymentMethod asPaymentMethod = creditCardPrism.build(newCard);
    System.out.println("\nBuilt credit card: " + asPaymentMethod);

    System.out.println();

    // ===== CUSTOM ENUM PRISMS =====
    System.out.println("--- Custom Enum Prisms ---\n");

    // 6. Get prisms for enum constants
    Prism<OrderStatus, OrderStatus> pendingPrism = OrderStatusPrisms.pending();
    Prism<OrderStatus, OrderStatus> shippedPrism = OrderStatusPrisms.shipped();
    Prism<OrderStatus, OrderStatus> cancelledPrism = OrderStatusPrisms.cancelled();

    // 7. Use getOptional to check specific status
    OrderStatus status1 = OrderStatus.PENDING;
    OrderStatus status2 = OrderStatus.SHIPPED;

    System.out.println("Is PENDING pending? " + pendingPrism.getOptional(status1).isPresent());
    System.out.println("Is SHIPPED pending? " + pendingPrism.getOptional(status2).isPresent());
    System.out.println("Is SHIPPED shipped? " + shippedPrism.getOptional(status2).isPresent());
    System.out.println();

    // 8. Filter orders by status using prisms
    OrderStatus[] orders = {
      OrderStatus.PENDING,
      OrderStatus.CONFIRMED,
      OrderStatus.SHIPPED,
      OrderStatus.PENDING,
      OrderStatus.DELIVERED,
      OrderStatus.CANCELLED
    };

    long pendingCount =
        Arrays.stream(orders).filter(s -> pendingPrism.getOptional(s).isPresent()).count();

    long cancelledCount =
        Arrays.stream(orders).filter(s -> cancelledPrism.getOptional(s).isPresent()).count();

    System.out.println("Pending orders: " + pendingCount);
    System.out.println("Cancelled orders: " + cancelledCount);

    System.out.println("\n=== Example Complete ===");
  }

  /**
   * Demonstrates using prisms generated for JDK enums.
   *
   * <p>This shows that @ImportOptics works with real external types from the Java standard library,
   * not just custom types.
   */
  private static void demonstrateJdkEnums() {
    System.out.println("--- JDK Enum Prisms (Real External Types) ---\n");

    // 1. DayOfWeek prisms - useful for scheduling logic
    Prism<DayOfWeek, DayOfWeek> mondayPrism = DayOfWeekPrisms.monday();
    Prism<DayOfWeek, DayOfWeek> saturdayPrism = DayOfWeekPrisms.saturday();
    Prism<DayOfWeek, DayOfWeek> sundayPrism = DayOfWeekPrisms.sunday();

    DayOfWeek today = DayOfWeek.SATURDAY;
    boolean isWeekend =
        saturdayPrism.getOptional(today).isPresent() || sundayPrism.getOptional(today).isPresent();
    System.out.println("Is " + today + " a weekend? " + isWeekend);

    // Count weekdays in a schedule
    DayOfWeek[] schedule = {
      DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
    };
    long mondayCount =
        Arrays.stream(schedule).filter(d -> mondayPrism.getOptional(d).isPresent()).count();
    System.out.println("Mondays in schedule: " + mondayCount);
    System.out.println();

    // 2. Month prisms - useful for seasonal logic
    Prism<Month, Month> januaryPrism = MonthPrisms.january();
    Prism<Month, Month> decemberPrism = MonthPrisms.december();
    Prism<Month, Month> julyPrism = MonthPrisms.july();

    Month currentMonth = Month.DECEMBER;
    boolean isYearEnd = decemberPrism.getOptional(currentMonth).isPresent();
    boolean isYearStart = januaryPrism.getOptional(currentMonth).isPresent();
    System.out.println("Is " + currentMonth + " year-end? " + isYearEnd);
    System.out.println("Is " + currentMonth + " year-start? " + isYearStart);

    // Find summer months
    Month[] fiscalYear = {Month.APRIL, Month.MAY, Month.JUNE, Month.JULY, Month.AUGUST};
    boolean hasSummerPeak =
        Arrays.stream(fiscalYear).anyMatch(m -> julyPrism.getOptional(m).isPresent());
    System.out.println("Fiscal year includes July (summer peak)? " + hasSummerPeak);
    System.out.println();

    // 3. TimeUnit prisms - useful for duration handling
    Prism<TimeUnit, TimeUnit> millisPrism = TimeUnitPrisms.milliseconds();
    Prism<TimeUnit, TimeUnit> secondsPrism = TimeUnitPrisms.seconds();
    Prism<TimeUnit, TimeUnit> daysPrism = TimeUnitPrisms.days();

    TimeUnit unit = TimeUnit.SECONDS;
    boolean isSmallUnit =
        millisPrism.getOptional(unit).isPresent() || secondsPrism.getOptional(unit).isPresent();
    System.out.println("Is " + unit + " a small time unit? " + isSmallUnit);

    // Check if a timeout unit is appropriate for human interaction
    TimeUnit[] timeoutUnits = {TimeUnit.MILLISECONDS, TimeUnit.SECONDS, TimeUnit.MINUTES};
    boolean hasDayTimeout =
        Arrays.stream(timeoutUnits).anyMatch(u -> daysPrism.getOptional(u).isPresent());
    System.out.println("Any day-level timeouts? " + hasDayTimeout + " (good for UX!)");

    System.out.println();
  }

  /**
   * Describes a payment method using prisms for type-safe pattern matching.
   *
   * @param payment the payment method to describe
   * @return a description of the payment method
   */
  private static String describePayment(PaymentMethod payment) {
    Prism<PaymentMethod, CreditCard> ccPrism = PaymentMethodPrisms.creditCard();
    Prism<PaymentMethod, BankTransfer> btPrism = PaymentMethodPrisms.bankTransfer();
    Prism<PaymentMethod, DigitalWallet> dwPrism = PaymentMethodPrisms.digitalWallet();

    return ccPrism
        .getOptional(payment)
        .map(
            cc ->
                "Credit Card ending in " + cc.cardNumber().substring(cc.cardNumber().length() - 4))
        .or(
            () ->
                btPrism
                    .getOptional(payment)
                    .map(bt -> "Bank Transfer to account " + bt.accountNumber()))
        .or(
            () ->
                dwPrism
                    .getOptional(payment)
                    .map(dw -> dw.provider() + " wallet (" + dw.accountEmail() + ")"))
        .orElse("Unknown payment method");
  }
}
