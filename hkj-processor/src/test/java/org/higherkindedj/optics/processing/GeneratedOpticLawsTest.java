// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.compile;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.packageInfo;

import com.google.testing.compile.JavaFileObjects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

/**
 * Verifies that generated optics satisfy their mathematical laws.
 *
 * <p>This test class uses runtime compilation to generate optics for test types, then verifies the
 * generated implementations satisfy the required laws:
 *
 * <h3>Lens Laws</h3>
 *
 * <ul>
 *   <li><b>Get-Put</b>: {@code set(get(s), s) == s} - Setting what you get doesn't change anything
 *   <li><b>Put-Get</b>: {@code get(set(a, s)) == a} - Getting what you set returns what you set
 *   <li><b>Put-Put</b>: {@code set(b, set(a, s)) == set(b, s)} - Second set wins
 * </ul>
 *
 * <h3>Prism Laws</h3>
 *
 * <ul>
 *   <li><b>Review-Preview</b>: {@code getOptional(build(a)) == Optional.of(a)}
 *   <li><b>Preview-Review</b>: {@code getOptional(s).map(this::build).orElse(s) == s}
 * </ul>
 */
@DisplayName("Generated Optics Law Verification")
class GeneratedOpticLawsTest {

  // ==================== TEST SOURCES ====================

  private static final String TEST_PACKAGE = "org.higherkindedj.test.optics";

  // Record type for lens testing
  private static final JavaFileObject CUSTOMER_RECORD =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.test.Customer",
          """
          package org.higherkindedj.test;

          public record Customer(String id, String name, String email, int loyaltyPoints) {}
          """);

  // Sealed interface for prism testing
  private static final JavaFileObject PAYMENT_METHOD_SEALED =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.test.PaymentMethod",
          """
          package org.higherkindedj.test;

          public sealed interface PaymentMethod permits CreditCard, BankTransfer, DigitalWallet {}
          """);

  private static final JavaFileObject CREDIT_CARD =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.test.CreditCard",
          """
          package org.higherkindedj.test;

          public record CreditCard(String cardNumber, String expiryDate) implements PaymentMethod {}
          """);

  private static final JavaFileObject BANK_TRANSFER =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.test.BankTransfer",
          """
          package org.higherkindedj.test;

          public record BankTransfer(String accountNumber, String sortCode) implements PaymentMethod {}
          """);

  private static final JavaFileObject DIGITAL_WALLET =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.test.DigitalWallet",
          """
          package org.higherkindedj.test;

          public record DigitalWallet(String provider, String email) implements PaymentMethod {}
          """);

  // Enum type for prism testing
  private static final JavaFileObject ORDER_STATUS_ENUM =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.test.OrderStatus",
          """
          package org.higherkindedj.test;

          public enum OrderStatus {
              PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
          }
          """);

  // Generic record for generic lens testing
  // NOTE: Generic type law verification is deferred to Phase 3 (Edge Cases)
  // as it requires special handling for type parameters in reflection
  // private static final JavaFileObject PAIR_RECORD = ...

  // Package info importing all test types
  private static final JavaFileObject PACKAGE_INFO =
      packageInfo(
          TEST_PACKAGE,
          "org.higherkindedj.test.Customer",
          "org.higherkindedj.test.PaymentMethod",
          "org.higherkindedj.test.OrderStatus");

  // ==================== COMPILED RESULTS ====================

  private static CompiledResult compiled;

  @BeforeAll
  static void compileTestTypes() {
    compiled =
        compile(
            CUSTOMER_RECORD,
            PAYMENT_METHOD_SEALED,
            CREDIT_CARD,
            BANK_TRANSFER,
            DIGITAL_WALLET,
            ORDER_STATUS_ENUM,
            PACKAGE_INFO);
  }

  // ==================== LENS LAWS ====================

  @Nested
  @DisplayName("Generated Lens Laws")
  class GeneratedLensLaws {

    @TestFactory
    @DisplayName("Record lenses satisfy Get-Put law")
    Stream<DynamicTest> recordLensesSatisfyGetPut() {
      return Stream.of(
          lensGetPutTest(
              "Customer.id", "CustomerLenses", "id", "C001", "Alice", "alice@test.com", 100),
          lensGetPutTest(
              "Customer.name", "CustomerLenses", "name", "C001", "Alice", "alice@test.com", 100),
          lensGetPutTest(
              "Customer.email", "CustomerLenses", "email", "C001", "Alice", "alice@test.com", 100),
          lensGetPutTest(
              "Customer.loyaltyPoints",
              "CustomerLenses",
              "loyaltyPoints",
              "C001",
              "Alice",
              "alice@test.com",
              100));
    }

    @TestFactory
    @DisplayName("Record lenses satisfy Put-Get law")
    Stream<DynamicTest> recordLensesSatisfyPutGet() {
      return Stream.of(
          lensPutGetTest(
              "Customer.id",
              "CustomerLenses",
              "id",
              "NEW-ID",
              "C001",
              "Alice",
              "alice@test.com",
              100),
          lensPutGetTest(
              "Customer.name",
              "CustomerLenses",
              "name",
              "Bob",
              "C001",
              "Alice",
              "alice@test.com",
              100),
          lensPutGetTest(
              "Customer.email",
              "CustomerLenses",
              "email",
              "bob@test.com",
              "C001",
              "Alice",
              "alice@test.com",
              100),
          lensPutGetTest(
              "Customer.loyaltyPoints",
              "CustomerLenses",
              "loyaltyPoints",
              999,
              "C001",
              "Alice",
              "alice@test.com",
              100));
    }

    @TestFactory
    @DisplayName("Record lenses satisfy Put-Put law")
    Stream<DynamicTest> recordLensesSatisfyPutPut() {
      return Stream.of(
          lensPutPutTest(
              "Customer.id",
              "CustomerLenses",
              "id",
              "ID-1",
              "ID-2",
              "C001",
              "Alice",
              "alice@test.com",
              100),
          lensPutPutTest(
              "Customer.name",
              "CustomerLenses",
              "name",
              "Name1",
              "Name2",
              "C001",
              "Alice",
              "alice@test.com",
              100),
          lensPutPutTest(
              "Customer.email",
              "CustomerLenses",
              "email",
              "a@t.com",
              "b@t.com",
              "C001",
              "Alice",
              "alice@test.com",
              100),
          lensPutPutTest(
              "Customer.loyaltyPoints",
              "CustomerLenses",
              "loyaltyPoints",
              100,
              200,
              "C001",
              "Alice",
              "alice@test.com",
              50));
    }

    // NOTE: Generic record lens tests (Pair<A, B>) are deferred to Phase 3 (Edge Cases)
    // as they require special handling for type parameters in reflection-based testing.

    // ----- Lens Law Test Helpers -----

    private DynamicTest lensGetPutTest(
        String testName, String lensClass, String lensMethod, Object... constructorArgs) {
      return DynamicTest.dynamicTest(
          testName + " satisfies Get-Put",
          () -> {
            Object lens = compiled.invokeStatic(TEST_PACKAGE + "." + lensClass, lensMethod);
            Object source =
                compiled.newInstance("org.higherkindedj.test.Customer", constructorArgs);

            Object currentValue = compiled.invokeLensGet(lens, source);
            Object result = compiled.invokeLensSet(lens, currentValue, source);

            assertThat(result).as("Get-Put: set(get(s), s) == s").isEqualTo(source);
          });
    }

    private DynamicTest lensPutGetTest(
        String testName,
        String lensClass,
        String lensMethod,
        Object newValue,
        Object... constructorArgs) {
      return DynamicTest.dynamicTest(
          testName + " satisfies Put-Get",
          () -> {
            Object lens = compiled.invokeStatic(TEST_PACKAGE + "." + lensClass, lensMethod);
            Object source =
                compiled.newInstance("org.higherkindedj.test.Customer", constructorArgs);

            Object updated = compiled.invokeLensSet(lens, newValue, source);
            Object retrieved = compiled.invokeLensGet(lens, updated);

            assertThat(retrieved).as("Put-Get: get(set(a, s)) == a").isEqualTo(newValue);
          });
    }

    private DynamicTest lensPutPutTest(
        String testName,
        String lensClass,
        String lensMethod,
        Object value1,
        Object value2,
        Object... constructorArgs) {
      return DynamicTest.dynamicTest(
          testName + " satisfies Put-Put",
          () -> {
            Object lens = compiled.invokeStatic(TEST_PACKAGE + "." + lensClass, lensMethod);
            Object source =
                compiled.newInstance("org.higherkindedj.test.Customer", constructorArgs);

            Object doubleSet =
                compiled.invokeLensSet(lens, value2, compiled.invokeLensSet(lens, value1, source));
            Object singleSet = compiled.invokeLensSet(lens, value2, source);

            assertThat(doubleSet)
                .as("Put-Put: set(b, set(a, s)) == set(b, s)")
                .isEqualTo(singleSet);
          });
    }
  }

  // ==================== PRISM LAWS ====================

  @Nested
  @DisplayName("Generated Prism Laws")
  class GeneratedPrismLaws {

    @TestFactory
    @DisplayName("Sealed interface prisms satisfy Review-Preview law")
    Stream<DynamicTest> sealedPrismsSatisfyReviewPreview() {
      return Stream.of(
          prismReviewPreviewTest(
              "PaymentMethod.creditCard",
              "PaymentMethodPrisms",
              "creditCard",
              "org.higherkindedj.test.CreditCard",
              "4111-1111-1111-1111",
              "12/25"),
          prismReviewPreviewTest(
              "PaymentMethod.bankTransfer",
              "PaymentMethodPrisms",
              "bankTransfer",
              "org.higherkindedj.test.BankTransfer",
              "12345678",
              "00-11-22"),
          prismReviewPreviewTest(
              "PaymentMethod.digitalWallet",
              "PaymentMethodPrisms",
              "digitalWallet",
              "org.higherkindedj.test.DigitalWallet",
              "PayPal",
              "user@paypal.com"));
    }

    @TestFactory
    @DisplayName("Sealed interface prisms satisfy Preview-Review law")
    Stream<DynamicTest> sealedPrismsSatisfyPreviewReview() {
      return Stream.of(
          // Matching cases
          prismPreviewReviewTest(
              "PaymentMethod.creditCard (matching)",
              "PaymentMethodPrisms",
              "creditCard",
              "org.higherkindedj.test.CreditCard",
              "4111-1111-1111-1111",
              "12/25"),
          prismPreviewReviewTest(
              "PaymentMethod.bankTransfer (matching)",
              "PaymentMethodPrisms",
              "bankTransfer",
              "org.higherkindedj.test.BankTransfer",
              "12345678",
              "00-11-22"),
          // Non-matching case: CreditCard prism on BankTransfer
          DynamicTest.dynamicTest(
              "PaymentMethod.creditCard (non-matching) satisfies Preview-Review",
              () -> {
                Object prism =
                    compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "creditCard");
                Object bankTransfer =
                    compiled.newInstance(
                        "org.higherkindedj.test.BankTransfer", "12345678", "00-11-22");

                Optional<Object> extracted = compiled.invokePrismGetOptional(prism, bankTransfer);
                Object result =
                    extracted.map(v -> invokePrismBuildSafe(prism, v)).orElse(bankTransfer);

                assertThat(result)
                    .as("Preview-Review: getOptional(s).map(build).orElse(s) == s")
                    .isEqualTo(bankTransfer);
              }));
    }

    @TestFactory
    @DisplayName("Enum prisms satisfy Review-Preview law")
    Stream<DynamicTest> enumPrismsSatisfyReviewPreview() {
      return Stream.of(
          enumPrismReviewPreviewTest("OrderStatus.pending", "pending", "PENDING"),
          enumPrismReviewPreviewTest("OrderStatus.confirmed", "confirmed", "CONFIRMED"),
          enumPrismReviewPreviewTest("OrderStatus.shipped", "shipped", "SHIPPED"),
          enumPrismReviewPreviewTest("OrderStatus.cancelled", "cancelled", "CANCELLED"));
    }

    @TestFactory
    @DisplayName("Enum prisms satisfy Preview-Review law")
    Stream<DynamicTest> enumPrismsSatisfyPreviewReview() {
      return Stream.of(
          // Matching cases
          enumPrismPreviewReviewTest("OrderStatus.pending (matching)", "pending", "PENDING"),
          enumPrismPreviewReviewTest("OrderStatus.shipped (matching)", "shipped", "SHIPPED"),
          // Non-matching cases
          enumPrismPreviewReviewTest("OrderStatus.pending (non-matching)", "pending", "SHIPPED"),
          enumPrismPreviewReviewTest("OrderStatus.shipped (non-matching)", "shipped", "PENDING"));
    }

    // ----- Prism Law Test Helpers -----

    private DynamicTest prismReviewPreviewTest(
        String testName,
        String prismClass,
        String prismMethod,
        String valueClass,
        Object... constructorArgs) {
      return DynamicTest.dynamicTest(
          testName + " satisfies Review-Preview",
          () -> {
            Object prism = compiled.invokeStatic(TEST_PACKAGE + "." + prismClass, prismMethod);
            Object value = compiled.newInstance(valueClass, constructorArgs);

            Object built = compiled.invokePrismBuild(prism, value);
            Optional<Object> extracted = compiled.invokePrismGetOptional(prism, built);

            assertThat(extracted)
                .as("Review-Preview: getOptional(build(a)) == Optional.of(a)")
                .isPresent()
                .contains(value);
          });
    }

    private DynamicTest prismPreviewReviewTest(
        String testName,
        String prismClass,
        String prismMethod,
        String sourceClass,
        Object... constructorArgs) {
      return DynamicTest.dynamicTest(
          testName + " satisfies Preview-Review",
          () -> {
            Object prism = compiled.invokeStatic(TEST_PACKAGE + "." + prismClass, prismMethod);
            Object source = compiled.newInstance(sourceClass, constructorArgs);

            Optional<Object> extracted = compiled.invokePrismGetOptional(prism, source);
            Object result = extracted.map(v -> invokePrismBuildSafe(prism, v)).orElse(source);

            assertThat(result)
                .as("Preview-Review: getOptional(s).map(build).orElse(s) == s")
                .isEqualTo(source);
          });
    }

    private DynamicTest enumPrismReviewPreviewTest(
        String testName, String prismMethod, String enumConstant) {
      return DynamicTest.dynamicTest(
          testName + " satisfies Review-Preview",
          () -> {
            Object prism = compiled.invokeStatic(TEST_PACKAGE + ".OrderStatusPrisms", prismMethod);
            Class<?> enumClass = compiled.loadClass("org.higherkindedj.test.OrderStatus");
            Object value = Enum.valueOf(enumClass.asSubclass(Enum.class), enumConstant);

            Object built = compiled.invokePrismBuild(prism, value);
            Optional<Object> extracted = compiled.invokePrismGetOptional(prism, built);

            assertThat(extracted)
                .as("Review-Preview: getOptional(build(a)) == Optional.of(a)")
                .isPresent()
                .contains(value);
          });
    }

    private DynamicTest enumPrismPreviewReviewTest(
        String testName, String prismMethod, String enumConstant) {
      return DynamicTest.dynamicTest(
          testName + " satisfies Preview-Review",
          () -> {
            Object prism = compiled.invokeStatic(TEST_PACKAGE + ".OrderStatusPrisms", prismMethod);
            Class<?> enumClass = compiled.loadClass("org.higherkindedj.test.OrderStatus");
            Object source = Enum.valueOf(enumClass.asSubclass(Enum.class), enumConstant);

            Optional<Object> extracted = compiled.invokePrismGetOptional(prism, source);
            Object result = extracted.map(v -> invokePrismBuildSafe(prism, v)).orElse(source);

            assertThat(result)
                .as("Preview-Review: getOptional(s).map(build).orElse(s) == s")
                .isEqualTo(source);
          });
    }

    private Object invokePrismBuildSafe(Object prism, Object value) {
      try {
        return compiled.invokePrismBuild(prism, value);
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    }
  }

  // ==================== COMPOSED OPTICS LAWS ====================

  @Nested
  @DisplayName("Composed Optics Laws")
  class ComposedOpticsLaws {

    @TestFactory
    @DisplayName("Chained lens operations preserve laws")
    Stream<DynamicTest> chainedLensOperationsPreserveLaws() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Multiple lens updates are independent",
              () -> {
                Object nameLens = compiled.invokeStatic(TEST_PACKAGE + ".CustomerLenses", "name");
                Object pointsLens =
                    compiled.invokeStatic(TEST_PACKAGE + ".CustomerLenses", "loyaltyPoints");
                Object customer =
                    compiled.newInstance(
                        "org.higherkindedj.test.Customer", "C001", "Alice", "alice@test.com", 150);

                // Apply two independent lens updates
                Object updated =
                    compiled.invokeLensSet(
                        pointsLens,
                        200,
                        compiled.invokeLensSet(nameLens, "Updated Name", customer));

                // Verify both updates took effect
                assertThat(compiled.invokeLensGet(nameLens, updated)).isEqualTo("Updated Name");
                assertThat(compiled.invokeLensGet(pointsLens, updated)).isEqualTo(200);

                // Verify original unchanged
                assertThat(compiled.invokeLensGet(nameLens, customer)).isEqualTo("Alice");
                assertThat(compiled.invokeLensGet(pointsLens, customer)).isEqualTo(150);
              }),
          DynamicTest.dynamicTest(
              "Lens set is order-independent for different fields",
              () -> {
                Object nameLens = compiled.invokeStatic(TEST_PACKAGE + ".CustomerLenses", "name");
                Object emailLens = compiled.invokeStatic(TEST_PACKAGE + ".CustomerLenses", "email");
                Object customer =
                    compiled.newInstance(
                        "org.higherkindedj.test.Customer", "C001", "Alice", "alice@test.com", 150);

                // Apply updates in different orders
                Object order1 =
                    compiled.invokeLensSet(
                        emailLens,
                        "new@test.com",
                        compiled.invokeLensSet(nameLens, "Bob", customer));
                Object order2 =
                    compiled.invokeLensSet(
                        nameLens,
                        "Bob",
                        compiled.invokeLensSet(emailLens, "new@test.com", customer));

                assertThat(order1).isEqualTo(order2);
              }));
    }

    @TestFactory
    @DisplayName("Prism operations maintain type safety")
    Stream<DynamicTest> prismOperationsMaintainTypeSafety() {
      return Stream.of(
          DynamicTest.dynamicTest(
              "Prism getOptional returns empty for non-matching types",
              () -> {
                Object creditCardPrism =
                    compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "creditCard");
                Object bankTransfer =
                    compiled.newInstance(
                        "org.higherkindedj.test.BankTransfer", "12345678", "00-11-22");

                Optional<Object> result =
                    compiled.invokePrismGetOptional(creditCardPrism, bankTransfer);

                assertThat(result).isEmpty();
              }),
          DynamicTest.dynamicTest(
              "Prism getOptional returns value for matching types",
              () -> {
                Object creditCardPrism =
                    compiled.invokeStatic(TEST_PACKAGE + ".PaymentMethodPrisms", "creditCard");
                Object creditCard =
                    compiled.newInstance(
                        "org.higherkindedj.test.CreditCard", "4111-1111-1111-1111", "12/25");

                Optional<Object> result =
                    compiled.invokePrismGetOptional(creditCardPrism, creditCard);

                assertThat(result).isPresent().contains(creditCard);
              }));
    }
  }
}
