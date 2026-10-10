// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.importoptics;

import java.time.DayOfWeek;
import java.time.Month;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.higherkindedj.example.optics.importoptics.external.BankAccount;
import org.higherkindedj.example.optics.importoptics.external.CardInstrument;
import org.higherkindedj.example.optics.importoptics.external.CryptoWallet;
import org.higherkindedj.example.optics.importoptics.external.PaymentInstrument;
import org.higherkindedj.example.optics.importoptics.external.TrackingStatus;
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
 *   <li>Custom sealed interfaces: PaymentInstrument, a payment gateway's SDK type
 *   <li>Custom enums: TrackingStatus, a courier's SDK type
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
    PaymentInstrument card = new CardInstrument("tok_visa_4242");
    PaymentInstrument bankAccount = new BankAccount("GB29NWBK60161331926819");
    PaymentInstrument cryptoWallet = new CryptoWallet("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq");

    // 2. Get the generated prisms for each subtype
    Prism<PaymentInstrument, CardInstrument> cardPrism = PaymentInstrumentPrisms.cardInstrument();
    Prism<PaymentInstrument, BankAccount> bankAccountPrism = PaymentInstrumentPrisms.bankAccount();
    Prism<PaymentInstrument, CryptoWallet> cryptoWalletPrism =
        PaymentInstrumentPrisms.cryptoWallet();

    // 3. Use getOptional to safely extract subtypes
    Optional<CardInstrument> asCard = cardPrism.getOptional(card);
    System.out.println("Card as CardInstrument: " + asCard);

    Optional<BankAccount> asBankAccount = bankAccountPrism.getOptional(card);
    System.out.println("Card as BankAccount: " + asBankAccount); // Empty

    // 4. Chain getOptional across the prisms, falling through with or(), to match each subtype
    System.out.println("\nProcessing payments:");
    for (PaymentInstrument payment : new PaymentInstrument[] {card, bankAccount, cryptoWallet}) {
      String description = describePayment(payment);
      System.out.println("  " + description);
    }

    // 5. Use build to construct values (upcast)
    CardInstrument newCard = new CardInstrument("tok_mastercard_4444");
    PaymentInstrument asInstrument = cardPrism.build(newCard);
    System.out.println("\nBuilt card instrument: " + asInstrument);

    System.out.println();

    // ===== CUSTOM ENUM PRISMS =====
    System.out.println("--- Custom Enum Prisms ---\n");

    // 6. Get prisms for enum constants
    Prism<TrackingStatus, TrackingStatus> pendingPrism = TrackingStatusPrisms.pending();
    Prism<TrackingStatus, TrackingStatus> inTransitPrism = TrackingStatusPrisms.inTransit();
    Prism<TrackingStatus, TrackingStatus> returnedPrism = TrackingStatusPrisms.returned();

    // 7. Use getOptional to check specific status
    TrackingStatus status1 = TrackingStatus.PENDING;
    TrackingStatus status2 = TrackingStatus.IN_TRANSIT;

    System.out.println("Is PENDING pending? " + pendingPrism.getOptional(status1).isPresent());
    System.out.println("Is IN_TRANSIT pending? " + pendingPrism.getOptional(status2).isPresent());
    System.out.println(
        "Is IN_TRANSIT in transit? " + inTransitPrism.getOptional(status2).isPresent());
    System.out.println();

    // 8. Filter parcels by tracking status using prisms
    TrackingStatus[] parcels = {
      TrackingStatus.PENDING,
      TrackingStatus.IN_TRANSIT,
      TrackingStatus.DELIVERED,
      TrackingStatus.PENDING,
      TrackingStatus.DELIVERED,
      TrackingStatus.RETURNED
    };

    long pendingCount =
        Arrays.stream(parcels).filter(s -> pendingPrism.getOptional(s).isPresent()).count();

    long returnedCount =
        Arrays.stream(parcels).filter(s -> returnedPrism.getOptional(s).isPresent()).count();

    System.out.println("Pending parcels: " + pendingCount);
    System.out.println("Returned parcels: " + returnedCount);

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
   * Describes a payment instrument using prisms for type-safe pattern matching.
   *
   * @param payment the payment instrument to describe
   * @return a description of the payment instrument
   */
  private static String describePayment(PaymentInstrument payment) {
    Prism<PaymentInstrument, CardInstrument> cardPrism = PaymentInstrumentPrisms.cardInstrument();
    Prism<PaymentInstrument, BankAccount> bankPrism = PaymentInstrumentPrisms.bankAccount();
    Prism<PaymentInstrument, CryptoWallet> walletPrism = PaymentInstrumentPrisms.cryptoWallet();

    return cardPrism
        .getOptional(payment)
        .map(c -> "Card token ending in " + c.token().substring(c.token().length() - 4))
        .or(() -> bankPrism.getOptional(payment).map(b -> "Bank account " + b.iban()))
        .or(() -> walletPrism.getOptional(payment).map(w -> "Crypto wallet " + w.address()))
        .orElse("Unknown payment instrument");
  }
}
