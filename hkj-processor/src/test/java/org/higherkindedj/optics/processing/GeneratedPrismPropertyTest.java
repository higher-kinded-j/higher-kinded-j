// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.compile;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.packageInfo;

import com.google.testing.compile.JavaFileObjects;
import java.util.Optional;
import javax.tools.JavaFileObject;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;

/**
 * Property-based tests for generated prisms using jqwik.
 *
 * <p>These tests verify that generated prisms satisfy the fundamental prism laws with random
 * inputs:
 *
 * <ul>
 *   <li><b>Build-GetOptional</b>: {@code getOptional(build(a)) == Some(a)}
 *   <li><b>GetOptional-Build</b>: {@code getOptional(s).map(build) == getOptional(s).map(_ -> s)}
 *       (if match)
 * </ul>
 *
 * <p>The tests use runtime compilation to generate prisms for test types, then verify the laws hold
 * for hundreds of randomly generated test cases.
 */
class GeneratedPrismPropertyTest {

  private static final String TEST_PACKAGE = "org.higherkindedj.test.optics";

  // Test sealed interface with multiple variants
  private static final JavaFileObject PAYMENT_METHOD =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.test.PaymentMethod",
          """
          package org.higherkindedj.test;

          public sealed interface PaymentMethod {
              record CreditCard(String cardNumber, String expiry) implements PaymentMethod {}
              record BankTransfer(String accountNumber, String routingNumber) implements PaymentMethod {}
              record Cash(int amount) implements PaymentMethod {}
          }
          """);

  // Test enum
  private static final JavaFileObject ORDER_STATUS =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.test.OrderStatus",
          """
          package org.higherkindedj.test;

          public enum OrderStatus {
              PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
          }
          """);

  private static final JavaFileObject PACKAGE_INFO =
      packageInfo(
          TEST_PACKAGE,
          "org.higherkindedj.test.PaymentMethod",
          "org.higherkindedj.test.OrderStatus");

  private static CompiledResult compiled;

  @BeforeContainer
  static void compileTestTypes() {
    compiled = compile(PAYMENT_METHOD, ORDER_STATUS, PACKAGE_INFO);
  }

  // ===== Prism Law Properties for CreditCard =====

  @Property(tries = 100)
  @Label("Build-GetOptional Law for CreditCard prism: getOptional(build(a)) == Some(a)")
  void creditCardPrismBuildGetOptionalLaw(
      @ForAll @StringLength(min = 16, max = 19) String cardNumber,
      @ForAll @StringLength(min = 5, max = 7) String expiry)
      throws ReflectiveOperationException {

    Object prism = compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "creditCard");
    Object creditCard =
        compiled.newInstance("org.higherkindedj.test.PaymentMethod$CreditCard", cardNumber, expiry);

    Object built = compiled.invokePrismBuild(prism, creditCard);
    @SuppressWarnings("unchecked")
    Optional<Object> previewed = (Optional<Object>) compiled.invokePrismGetOptional(prism, built);

    assertThat(previewed).isPresent();
    assertThat(previewed.get()).isEqualTo(creditCard);
  }

  @Property(tries = 100)
  @Label("GetOptional-Build Law for CreditCard prism (matching case)")
  void creditCardPrismGetOptionalBuildLaw(
      @ForAll @StringLength(min = 16, max = 19) String cardNumber,
      @ForAll @StringLength(min = 5, max = 7) String expiry)
      throws ReflectiveOperationException {

    Object prism = compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "creditCard");
    Object creditCard =
        compiled.newInstance("org.higherkindedj.test.PaymentMethod$CreditCard", cardNumber, expiry);

    @SuppressWarnings("unchecked")
    Optional<Object> previewed =
        (Optional<Object>) compiled.invokePrismGetOptional(prism, creditCard);
    assertThat(previewed).isPresent();

    Object rebuiltBack = compiled.invokePrismBuild(prism, previewed.get());
    assertThat(rebuiltBack).isEqualTo(creditCard);
  }

  @Property(tries = 100)
  @Label("CreditCard prism returns empty for non-matching types")
  void creditCardPrismReturnsEmptyForNonMatching(
      @ForAll @StringLength(min = 10, max = 20) String accountNumber,
      @ForAll @StringLength(min = 9, max = 9) String routingNumber)
      throws ReflectiveOperationException {

    Object prism = compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "creditCard");
    Object bankTransfer =
        compiled.newInstance(
            "org.higherkindedj.test.PaymentMethod$BankTransfer", accountNumber, routingNumber);

    @SuppressWarnings("unchecked")
    Optional<Object> previewed =
        (Optional<Object>) compiled.invokePrismGetOptional(prism, bankTransfer);

    assertThat(previewed).isEmpty();
  }

  // ===== Prism Law Properties for BankTransfer =====

  @Property(tries = 100)
  @Label("Build-GetOptional Law for BankTransfer prism: getOptional(build(a)) == Some(a)")
  void bankTransferPrismBuildGetOptionalLaw(
      @ForAll @StringLength(min = 10, max = 20) String accountNumber,
      @ForAll @StringLength(min = 9, max = 9) String routingNumber)
      throws ReflectiveOperationException {

    Object prism = compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "bankTransfer");
    Object bankTransfer =
        compiled.newInstance(
            "org.higherkindedj.test.PaymentMethod$BankTransfer", accountNumber, routingNumber);

    Object built = compiled.invokePrismBuild(prism, bankTransfer);
    @SuppressWarnings("unchecked")
    Optional<Object> previewed = (Optional<Object>) compiled.invokePrismGetOptional(prism, built);

    assertThat(previewed).isPresent();
    assertThat(previewed.get()).isEqualTo(bankTransfer);
  }

  @Property(tries = 100)
  @Label("BankTransfer prism returns empty for non-matching types")
  void bankTransferPrismReturnsEmptyForNonMatching(
      @ForAll @StringLength(min = 16, max = 19) String cardNumber,
      @ForAll @StringLength(min = 5, max = 7) String expiry)
      throws ReflectiveOperationException {

    Object prism = compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "bankTransfer");
    Object creditCard =
        compiled.newInstance("org.higherkindedj.test.PaymentMethod$CreditCard", cardNumber, expiry);

    @SuppressWarnings("unchecked")
    Optional<Object> previewed =
        (Optional<Object>) compiled.invokePrismGetOptional(prism, creditCard);

    assertThat(previewed).isEmpty();
  }

  // ===== Prism Law Properties for Cash =====

  @Property(tries = 100)
  @Label("Build-GetOptional Law for Cash prism: getOptional(build(a)) == Some(a)")
  void cashPrismBuildGetOptionalLaw(@ForAll @IntRange(min = 0, max = 10000) int amount)
      throws ReflectiveOperationException {

    Object prism = compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "cash");
    Object cash = compiled.newInstance("org.higherkindedj.test.PaymentMethod$Cash", amount);

    Object built = compiled.invokePrismBuild(prism, cash);
    @SuppressWarnings("unchecked")
    Optional<Object> previewed = (Optional<Object>) compiled.invokePrismGetOptional(prism, built);

    assertThat(previewed).isPresent();
    assertThat(previewed.get()).isEqualTo(cash);
  }

  @Property(tries = 100)
  @Label("Cash prism returns empty for non-matching types")
  void cashPrismReturnsEmptyForNonMatching(
      @ForAll @StringLength(min = 16, max = 19) String cardNumber,
      @ForAll @StringLength(min = 5, max = 7) String expiry)
      throws ReflectiveOperationException {

    Object prism = compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "cash");
    Object creditCard =
        compiled.newInstance("org.higherkindedj.test.PaymentMethod$CreditCard", cardNumber, expiry);

    @SuppressWarnings("unchecked")
    Optional<Object> previewed =
        (Optional<Object>) compiled.invokePrismGetOptional(prism, creditCard);

    assertThat(previewed).isEmpty();
  }

  // ===== Enum Prism Properties =====

  @Property(tries = 50)
  @Label("Build-GetOptional Law for enum prisms: getOptional(build(a)) == Some(a)")
  void enumPrismBuildGetOptionalLaw(@ForAll("enumValues") String enumName)
      throws ReflectiveOperationException {

    String methodName = enumName.toLowerCase();
    Object prism = compiled.invokeStatic(TEST_PACKAGE + ".OrderStatusPrisms", methodName);
    Object enumValue =
        compiled.invokeStatic(
            "org.higherkindedj.test.OrderStatus", "valueOf", String.class, enumName);

    Object built = compiled.invokePrismBuild(prism, enumValue);
    @SuppressWarnings("unchecked")
    Optional<Object> previewed = (Optional<Object>) compiled.invokePrismGetOptional(prism, built);

    assertThat(previewed).isPresent();
    assertThat(previewed.get()).isEqualTo(enumValue);
  }

  @Provide
  Arbitrary<String> enumValues() {
    return Arbitraries.of("PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED");
  }

  @Property(tries = 100)
  @Label("Enum prism returns empty for non-matching values")
  void enumPrismReturnsEmptyForNonMatching(
      @ForAll("enumValues") String targetEnum, @ForAll("enumValues") String otherEnum)
      throws ReflectiveOperationException {

    Assume.that(!targetEnum.equals(otherEnum));

    String methodName = targetEnum.toLowerCase();
    Object prism = compiled.invokeStatic(TEST_PACKAGE + ".OrderStatusPrisms", methodName);
    Object otherValue =
        compiled.invokeStatic(
            "org.higherkindedj.test.OrderStatus", "valueOf", String.class, otherEnum);

    @SuppressWarnings("unchecked")
    Optional<Object> previewed =
        (Optional<Object>) compiled.invokePrismGetOptional(prism, otherValue);

    assertThat(previewed).isEmpty();
  }

  // ===== Additional Properties =====

  @Property(tries = 50)
  @Label("Prism preview is total: never throws for valid PaymentMethod")
  void prismPreviewIsTotalForPaymentMethod(
      @ForAll @StringLength(min = 16, max = 19) String cardNumber,
      @ForAll @StringLength(min = 5, max = 7) String expiry,
      @ForAll @StringLength(min = 10, max = 20) String accountNumber,
      @ForAll @StringLength(min = 9, max = 9) String routingNumber,
      @ForAll @IntRange(min = 0, max = 10000) int amount,
      @ForAll @IntRange(min = 0, max = 2) int choice)
      throws ReflectiveOperationException {

    // Create a random PaymentMethod variant
    Object payment =
        switch (choice) {
          case 0 ->
              compiled.newInstance(
                  "org.higherkindedj.test.PaymentMethod$CreditCard", cardNumber, expiry);
          case 1 ->
              compiled.newInstance(
                  "org.higherkindedj.test.PaymentMethod$BankTransfer",
                  accountNumber,
                  routingNumber);
          default -> compiled.newInstance("org.higherkindedj.test.PaymentMethod$Cash", amount);
        };

    // All prisms should not throw when previewing any PaymentMethod
    Object creditCardPrism =
        compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "creditCard");
    Object bankTransferPrism =
        compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "bankTransfer");
    Object cashPrism = compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "cash");

    // These should all succeed without throwing (result may be empty or present)
    compiled.invokePrismGetOptional(creditCardPrism, payment);
    compiled.invokePrismGetOptional(bankTransferPrism, payment);
    compiled.invokePrismGetOptional(cashPrism, payment);
  }

  @Property(tries = 50)
  @Label("Exactly one prism matches for any PaymentMethod")
  void exactlyOnePrismMatchesForPaymentMethod(
      @ForAll @StringLength(min = 16, max = 19) String cardNumber,
      @ForAll @StringLength(min = 5, max = 7) String expiry,
      @ForAll @StringLength(min = 10, max = 20) String accountNumber,
      @ForAll @StringLength(min = 9, max = 9) String routingNumber,
      @ForAll @IntRange(min = 0, max = 10000) int amount,
      @ForAll @IntRange(min = 0, max = 2) int choice)
      throws ReflectiveOperationException {

    // Create a random PaymentMethod variant
    Object payment =
        switch (choice) {
          case 0 ->
              compiled.newInstance(
                  "org.higherkindedj.test.PaymentMethod$CreditCard", cardNumber, expiry);
          case 1 ->
              compiled.newInstance(
                  "org.higherkindedj.test.PaymentMethod$BankTransfer",
                  accountNumber,
                  routingNumber);
          default -> compiled.newInstance("org.higherkindedj.test.PaymentMethod$Cash", amount);
        };

    Object creditCardPrism =
        compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "creditCard");
    Object bankTransferPrism =
        compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "bankTransfer");
    Object cashPrism = compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "cash");

    @SuppressWarnings("unchecked")
    Optional<Object> creditCardMatch =
        (Optional<Object>) compiled.invokePrismGetOptional(creditCardPrism, payment);
    @SuppressWarnings("unchecked")
    Optional<Object> bankTransferMatch =
        (Optional<Object>) compiled.invokePrismGetOptional(bankTransferPrism, payment);
    @SuppressWarnings("unchecked")
    Optional<Object> cashMatch =
        (Optional<Object>) compiled.invokePrismGetOptional(cashPrism, payment);

    int matchCount =
        (creditCardMatch.isPresent() ? 1 : 0)
            + (bankTransferMatch.isPresent() ? 1 : 0)
            + (cashMatch.isPresent() ? 1 : 0);

    assertThat(matchCount).isEqualTo(1);
  }
}
