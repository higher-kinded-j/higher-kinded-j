// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.fluent;

import module java.base;
import module org.higherkindedj.core;

import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Comprehensive example demonstrating validation-aware modification operations using {@link
 * OpticOps}.
 *
 * <p>This example showcases Phase 2 of the optics core types integration, demonstrating:
 *
 * <ul>
 *   <li>Single-field validation with {@code modifyEither} (short-circuiting)
 *   <li>Optional validation with {@code modifyMaybe}
 *   <li>Multi-field validation with {@code modifyAllValidated} (error accumulation)
 *   <li>Multi-field validation with {@code modifyAllEither} (short-circuiting)
 *   <li>Fluent builder style with {@code modifyingWithValidation}
 *   <li>Real-world scenarios: user registration, order processing, configuration validation
 * </ul>
 *
 * <p>The key difference between validation approaches:
 *
 * <ul>
 *   <li><b>Either</b>: Short-circuits on first error (fail-fast)
 *   <li><b>Maybe</b>: Returns success or nothing (no error details)
 *   <li><b>Validated</b>: Accumulates all errors (comprehensive feedback)
 * </ul>
 */
public class FluentValidationExample {

  // ============================================================================
  // Domain Models
  // ============================================================================

  record UserProfile(String username, String email, int age, String bio) {}

  record OrderForm(String orderId, List<BigDecimal> itemPrices, String customerEmail) {}

  record AppConfig(String apiEndpoint, int maxConnections, int timeoutSeconds, String logLevel) {}

  record DataImport(List<String> emails, String importedBy) {}

  // ============================================================================
  // Optics Definitions
  // ============================================================================

  // UserProfile lenses
  static final Lens<UserProfile, String> USERNAME =
      Lens.of(UserProfile::username, (u, v) -> new UserProfile(v, u.email(), u.age(), u.bio()));

  static final Lens<UserProfile, String> EMAIL =
      Lens.of(UserProfile::email, (u, v) -> new UserProfile(u.username(), v, u.age(), u.bio()));

  static final Lens<UserProfile, Integer> AGE =
      Lens.of(UserProfile::age, (u, v) -> new UserProfile(u.username(), u.email(), v, u.bio()));

  static final Lens<UserProfile, String> BIO =
      Lens.of(UserProfile::bio, (u, v) -> new UserProfile(u.username(), u.email(), u.age(), v));

  // OrderForm lenses and traversals
  static final Lens<OrderForm, String> ORDER_ID =
      Lens.of(OrderForm::orderId, (o, v) -> new OrderForm(v, o.itemPrices(), o.customerEmail()));

  static final Lens<OrderForm, List<BigDecimal>> ITEM_PRICES =
      Lens.of(OrderForm::itemPrices, (o, v) -> new OrderForm(o.orderId(), v, o.customerEmail()));

  static final Lens<OrderForm, String> CUSTOMER_EMAIL =
      Lens.of(OrderForm::customerEmail, (o, v) -> new OrderForm(o.orderId(), o.itemPrices(), v));

  static final Traversal<OrderForm, BigDecimal> ALL_ITEM_PRICES =
      new Traversal<OrderForm, BigDecimal>() {
        @Override
        public <F> Kind<F, OrderForm> modifyF(
            Function<BigDecimal, Kind<F, BigDecimal>> f, OrderForm source, Applicative<F> app) {
          BiFunction<OrderForm, List<BigDecimal>, OrderForm> setter =
              (order, newPrices) ->
                  new OrderForm(order.orderId(), newPrices, order.customerEmail());
          return app.map(
              newValues -> setter.apply(source, newValues),
              Traversals.traverseList(source.itemPrices(), f, app));
        }
      };

  // AppConfig lenses
  static final Lens<AppConfig, String> API_ENDPOINT =
      Lens.of(
          AppConfig::apiEndpoint,
          (c, v) -> new AppConfig(v, c.maxConnections(), c.timeoutSeconds(), c.logLevel()));

  static final Lens<AppConfig, Integer> MAX_CONNECTIONS =
      Lens.of(
          AppConfig::maxConnections,
          (c, v) -> new AppConfig(c.apiEndpoint(), v, c.timeoutSeconds(), c.logLevel()));

  static final Lens<AppConfig, Integer> TIMEOUT_SECONDS =
      Lens.of(
          AppConfig::timeoutSeconds,
          (c, v) -> new AppConfig(c.apiEndpoint(), c.maxConnections(), v, c.logLevel()));

  static final Lens<AppConfig, String> LOG_LEVEL =
      Lens.of(
          AppConfig::logLevel,
          (c, v) -> new AppConfig(c.apiEndpoint(), c.maxConnections(), c.timeoutSeconds(), v));

  // DataImport traversal
  static final Traversal<DataImport, String> ALL_EMAILS =
      new Traversal<DataImport, String>() {
        @Override
        public <F> Kind<F, DataImport> modifyF(
            Function<String, Kind<F, String>> f, DataImport source, Applicative<F> app) {
          BiFunction<DataImport, List<String>, DataImport> setter =
              (d, newEmails) -> new DataImport(newEmails, d.importedBy());
          return app.map(
              newValues -> setter.apply(source, newValues),
              Traversals.traverseList(source.emails(), f, app));
        }
      };

  // ============================================================================
  // Validators
  // ============================================================================

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  private static Either<String, String> validateEmail(String email) {
    if (email == null || email.isBlank()) {
      return Either.left("Email cannot be empty");
    }
    if (!EMAIL_PATTERN.matcher(email).matches()) {
      return Either.left("Invalid email format: " + email);
    }
    return Either.right(email);
  }

  private static Either<String, String> validateUsername(String username) {
    if (username == null || username.isBlank()) {
      return Either.left("Username cannot be empty");
    }
    if (username.length() < 3) {
      return Either.left("Username must be at least 3 characters");
    }
    if (username.length() > 20) {
      return Either.left("Username must not exceed 20 characters");
    }
    if (!username.matches("^[a-zA-Z0-9_]+$")) {
      return Either.left("Username can only contain letters, numbers, and underscores");
    }
    return Either.right(username);
  }

  private static Either<String, Integer> validateAge(int age) {
    if (age < 13) {
      return Either.left("Age must be at least 13");
    }
    if (age > 120) {
      return Either.left("Age must be at most 120");
    }
    return Either.right(age);
  }

  private static Maybe<String> validateBioOptional(String bio) {
    if (bio == null || bio.isBlank()) {
      return Maybe.nothing(); // Empty bio is not allowed
    }
    if (bio.length() > 500) {
      return Maybe.nothing(); // Too long
    }
    return Maybe.just(bio.trim());
  }

  private static Validated<String, BigDecimal> validatePrice(BigDecimal price) {
    if (price == null) {
      return Validated.invalid("Price cannot be null");
    }
    if (price.compareTo(BigDecimal.ZERO) < 0) {
      return Validated.invalid("Price cannot be negative: " + price);
    }
    if (price.compareTo(new BigDecimal("10000")) > 0) {
      return Validated.invalid("Price exceeds maximum: " + price);
    }
    return Validated.valid(price);
  }

  private static Either<String, BigDecimal> validatePriceEither(BigDecimal price) {
    if (price == null) {
      return Either.left("Price cannot be null");
    }
    if (price.compareTo(BigDecimal.ZERO) < 0) {
      return Either.left("Price cannot be negative: " + price);
    }
    if (price.compareTo(new BigDecimal("10000")) > 0) {
      return Either.left("Price exceeds maximum: " + price);
    }
    return Either.right(price);
  }

  private static Either<String, Integer> validateMaxConnections(int maxConnections) {
    if (maxConnections < 1) {
      return Either.left("Max connections must be at least 1");
    }
    if (maxConnections > 1000) {
      return Either.left("Max connections cannot exceed 1000");
    }
    return Either.right(maxConnections);
  }

  private static Either<String, Integer> validateTimeout(int timeout) {
    if (timeout < 1) {
      return Either.left("Timeout must be at least 1 second");
    }
    if (timeout > 300) {
      return Either.left("Timeout cannot exceed 300 seconds");
    }
    return Either.right(timeout);
  }

  private static Validated<String, String> validateEmailForImport(String email) {
    if (email == null || email.isBlank()) {
      return Validated.invalid("Email cannot be empty");
    }
    if (!EMAIL_PATTERN.matcher(email).matches()) {
      return Validated.invalid("Invalid email: " + email);
    }
    return Validated.valid(email.toLowerCase().trim());
  }

  // ============================================================================
  // Main Entry Point
  // ============================================================================

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    System.out.println("=== FLUENT VALIDATION EXAMPLE ===\n");

    staticMethodExamples();
    System.out.println("\n" + "=".repeat(80) + "\n");

    fluentBuilderExamples();
    System.out.println("\n" + "=".repeat(80) + "\n");

    realWorldScenarios();
    System.out.println("\n" + "=".repeat(80) + "\n");

    comparisonExamples();
  }

  // ============================================================================
  // PART 1: Static Method Style
  // ============================================================================

  private static void staticMethodExamples() {
    System.out.println("--- Part 1: Static Method Style ---\n");

    // Example 1: modifyEither - Single field validation (success)
    System.out.println("Example 1: Validate email with modifyEither (success case)");
    UserProfile validUser =
        new UserProfile("alice123", "alice@example.com", 25, "Software engineer");
    Either<String, UserProfile> emailResult =
        OpticOps.modifyEither(validUser, EMAIL, FluentValidationExample::validateEmail);

    emailResult.fold(
        error -> {
          System.out.println("  ✗ Validation failed: " + error);
          return null;
        },
        user -> {
          System.out.println("  ✓ Email validated: " + user.email());
          return null;
        });
    System.out.println();

    // Example 2: modifyEither - Single field validation (failure)
    System.out.println("Example 2: Validate email with modifyEither (failure case)");
    UserProfile invalidUser = new UserProfile("bob456", "invalid-email", 30, "Designer");
    Either<String, UserProfile> invalidEmailResult =
        OpticOps.modifyEither(invalidUser, EMAIL, FluentValidationExample::validateEmail);

    invalidEmailResult.fold(
        error -> {
          System.out.println("  ✗ Validation failed: " + error);
          return null;
        },
        user -> {
          System.out.println("  ✓ Email validated: " + user.email());
          return null;
        });
    System.out.println();

    // Example 3: modifyMaybe - Optional validation (success)
    System.out.println("Example 3: Validate bio with modifyMaybe (success case)");
    UserProfile userWithBio =
        new UserProfile("charlie789", "charlie@example.com", 28, "  Product manager  ");
    Maybe<UserProfile> bioResult =
        OpticOps.modifyMaybe(userWithBio, BIO, FluentValidationExample::validateBioOptional);

    if (bioResult.isNothing()) {
      System.out.println("  ✗ Bio validation failed");
    } else {
      UserProfile user = bioResult.get();
      System.out.println("  ✓ Bio validated and trimmed: \"" + user.bio() + "\"");
    }
    System.out.println();

    // Example 4: modifyMaybe - Optional validation (failure)
    System.out.println("Example 4: Validate bio with modifyMaybe (failure case)");
    UserProfile userWithEmptyBio = new UserProfile("david101", "david@example.com", 35, "   ");
    Maybe<UserProfile> emptyBioResult =
        OpticOps.modifyMaybe(userWithEmptyBio, BIO, FluentValidationExample::validateBioOptional);

    if (emptyBioResult.isNothing()) {
      System.out.println("  ✗ Bio validation failed (empty bio not allowed)");
    } else {
      UserProfile user = emptyBioResult.get();
      System.out.println("  ✓ Bio validated: \"" + user.bio() + "\"");
    }
    System.out.println();

    // Example 5: modifyAllValidated - Multi-field validation with error accumulation
    System.out.println("Example 5: Validate all prices with modifyAllValidated");
    OrderForm order =
        new OrderForm(
            "ORD-001",
            List.of(
                new BigDecimal("50.00"),
                new BigDecimal("-10.00"), // Invalid: negative
                new BigDecimal("15000.00"), // Invalid: exceeds max
                new BigDecimal("25.50")),
            "customer@example.com");

    Validated<List<String>, OrderForm> pricesResult =
        OpticOps.modifyAllValidated(order, ALL_ITEM_PRICES, FluentValidationExample::validatePrice);

    pricesResult.fold(
        errors -> {
          System.out.println("  ✗ Validation failed with " + errors.size() + " error(s):");
          errors.forEach(error -> System.out.println("    - " + error));
          return null;
        },
        validOrder -> {
          System.out.println("  ✓ All prices validated: " + validOrder.itemPrices());
          return null;
        });
    System.out.println();

    // Example 6: modifyAllEither - Multi-field validation with short-circuiting
    System.out.println("Example 6: Validate all prices with modifyAllEither (short-circuits)");
    Either<String, OrderForm> pricesEitherResult =
        OpticOps.modifyAllEither(
            order, ALL_ITEM_PRICES, FluentValidationExample::validatePriceEither);

    pricesEitherResult.fold(
        error -> {
          System.out.println("  ✗ Validation stopped at first error: " + error);
          return null;
        },
        validOrder -> {
          System.out.println("  ✓ All prices validated: " + validOrder.itemPrices());
          return null;
        });
    System.out.println();
  }

  // ============================================================================
  // PART 2: Fluent Builder Style
  // ============================================================================

  private static void fluentBuilderExamples() {
    System.out.println("--- Part 2: Fluent Builder Style ---\n");

    // Example 1: throughEither - Fluent single field validation
    System.out.println("Example 1: Validate username with throughEither");
    UserProfile user = new UserProfile("ab", "test@example.com", 20, "Tester");

    Either<String, UserProfile> usernameResult =
        OpticOps.modifyingWithValidation(user)
            .throughEither(USERNAME, FluentValidationExample::validateUsername);

    usernameResult.fold(
        error -> {
          System.out.println("  ✗ " + error);
          return null;
        },
        validUser -> {
          System.out.println("  ✓ Username validated: " + validUser.username());
          return null;
        });
    System.out.println();

    // Example 2: throughMaybe - Fluent optional validation
    System.out.println("Example 2: Validate bio with throughMaybe");
    UserProfile userWithLongBio =
        new UserProfile("alice123", "alice@example.com", 25, "a".repeat(600));

    Maybe<UserProfile> longBioResult =
        OpticOps.modifyingWithValidation(userWithLongBio)
            .throughMaybe(BIO, FluentValidationExample::validateBioOptional);

    if (longBioResult.isNothing()) {
      System.out.println("  ✗ Bio validation failed (too long)");
    } else {
      UserProfile validUser = longBioResult.get();
      System.out.println("  ✓ Bio validated: " + validUser.bio());
    }
    System.out.println();

    // Example 3: allThroughValidated - Fluent multi-field validation with accumulation
    System.out.println("Example 3: Validate all emails with allThroughValidated");
    DataImport importData =
        new DataImport(
            List.of("valid@example.com", "invalid-email", "another@valid.com", "bad@"), "admin");

    Validated<List<String>, DataImport> emailsResult =
        OpticOps.modifyingWithValidation(importData)
            .allThroughValidated(ALL_EMAILS, FluentValidationExample::validateEmailForImport);

    emailsResult.fold(
        errors -> {
          System.out.println("  ✗ " + errors.size() + " invalid email(s):");
          errors.forEach(error -> System.out.println("    - " + error));
          return null;
        },
        validImport -> {
          System.out.println("  ✓ All emails validated and normalized:");
          validImport.emails().forEach(email -> System.out.println("    - " + email));
          return null;
        });
    System.out.println();

    // Example 4: allThroughEither - Fluent multi-field validation with short-circuit
    System.out.println("Example 4: Validate all prices with allThroughEither");
    OrderForm invalidOrder =
        new OrderForm(
            "ORD-002",
            List.of(new BigDecimal("100.00"), new BigDecimal("-50.00"), new BigDecimal("200.00")),
            "buyer@example.com");

    Either<String, OrderForm> orderResult =
        OpticOps.modifyingWithValidation(invalidOrder)
            .allThroughEither(ALL_ITEM_PRICES, FluentValidationExample::validatePriceEither);

    orderResult.fold(
        error -> {
          System.out.println("  ✗ First error encountered: " + error);
          return null;
        },
        validOrder -> {
          System.out.println("  ✓ All prices validated: " + validOrder.itemPrices());
          return null;
        });
    System.out.println();
  }

  // ============================================================================
  // PART 3: Real-World Scenarios
  // ============================================================================

  private static void realWorldScenarios() {
    System.out.println("--- Part 3: Real-World Scenarios ---\n");

    userRegistrationScenario();
    System.out.println();

    orderProcessingScenario();
    System.out.println();

    configurationValidationScenario();
    System.out.println();

    dataImportScenario();
  }

  private static void userRegistrationScenario() {
    System.out.println("Scenario A: User Registration Form Validation\n");

    UserProfile registrationForm = new UserProfile("jo", "not-an-email", 10, "I'm a new user!");

    System.out.println("Registration attempt:");
    System.out.println("  Username: " + registrationForm.username());
    System.out.println("  Email: " + registrationForm.email());
    System.out.println("  Age: " + registrationForm.age());
    System.out.println("  Bio: " + registrationForm.bio());
    System.out.println();

    // Validate username (short-circuit on failure)
    Either<String, UserProfile> step1 =
        OpticOps.modifyEither(
            registrationForm, USERNAME, FluentValidationExample::validateUsername);

    Either<String, UserProfile> step2 =
        step1.flatMap(
            profile ->
                OpticOps.modifyEither(profile, EMAIL, FluentValidationExample::validateEmail));

    Either<String, UserProfile> step3 =
        step2.flatMap(
            profile -> OpticOps.modifyEither(profile, AGE, FluentValidationExample::validateAge));

    step3.fold(
        error -> {
          System.out.println("✗ Registration failed at first validation error: " + error);
          return null;
        },
        validProfile -> {
          System.out.println("✓ Registration successful!");
          System.out.println("  Validated user: " + validProfile.username());
          return null;
        });
  }

  private static void orderProcessingScenario() {
    System.out.println("Scenario B: Order Processing with Price Validation\n");

    OrderForm order =
        new OrderForm(
            "ORD-12345",
            List.of(
                new BigDecimal("99.99"),
                new BigDecimal("149.50"),
                new BigDecimal("25.00"),
                new BigDecimal("299.99")),
            "customer@store.com");

    System.out.println("Processing order: " + order.orderId());
    System.out.println("  Original prices: " + order.itemPrices());
    System.out.println();

    // Validate all prices (accumulate all errors)
    Validated<List<String>, OrderForm> validatedOrder =
        OpticOps.modifyingWithValidation(order)
            .allThroughValidated(ALL_ITEM_PRICES, FluentValidationExample::validatePrice);

    validatedOrder.fold(
        errors -> {
          System.out.println("✗ Order validation failed:");
          errors.forEach(error -> System.out.println("  - " + error));
          return null;
        },
        validOrder -> {
          System.out.println("✓ Order validated successfully!");
          BigDecimal total =
              validOrder.itemPrices().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
          System.out.println("  Total: $" + total);
          return null;
        });
  }

  private static void configurationValidationScenario() {
    System.out.println("Scenario C: Application Configuration Validation\n");

    AppConfig config = new AppConfig("https://api.example.com", 5000, 10, "INFO");

    System.out.println("Validating configuration:");
    System.out.println("  API Endpoint: " + config.apiEndpoint());
    System.out.println("  Max Connections: " + config.maxConnections());
    System.out.println("  Timeout: " + config.timeoutSeconds() + "s");
    System.out.println("  Log Level: " + config.logLevel());
    System.out.println();

    // Validate max connections (short-circuit)
    Either<String, AppConfig> validatedConfig =
        OpticOps.modifyEither(
                config, MAX_CONNECTIONS, FluentValidationExample::validateMaxConnections)
            .flatMap(
                c ->
                    OpticOps.modifyEither(
                        c, TIMEOUT_SECONDS, FluentValidationExample::validateTimeout));

    validatedConfig.fold(
        error -> {
          System.out.println("✗ Configuration invalid: " + error);
          return null;
        },
        valid -> {
          System.out.println("✓ Configuration validated successfully");
          return null;
        });
  }

  private static void dataImportScenario() {
    System.out.println("Scenario D: Bulk Email Import with Error Accumulation\n");

    DataImport importBatch =
        new DataImport(
            List.of(
                "alice@example.com",
                "BOB@EXAMPLE.COM",
                "charlie.brown@test.org",
                "invalid.email",
                "diana@company.com",
                "@no-local-part.com"),
            "import-job-001");

    System.out.println("Importing " + importBatch.emails().size() + " emails...\n");

    Validated<List<String>, DataImport> result =
        OpticOps.modifyingWithValidation(importBatch)
            .allThroughValidated(ALL_EMAILS, FluentValidationExample::validateEmailForImport);

    result.fold(
        errors -> {
          System.out.println("✗ Import completed with errors:");
          System.out.println("  Failed: " + errors.size() + " email(s)");
          errors.forEach(error -> System.out.println("    - " + error));
          return null;
        },
        validImport -> {
          System.out.println("✓ Import successful!");
          System.out.println(
              "  Imported and normalized " + validImport.emails().size() + " email(s):");
          validImport.emails().forEach(email -> System.out.println("    - " + email));
          return null;
        });
  }

  // ============================================================================
  // PART 4: Comparison of Validation Strategies
  // ============================================================================

  private static void comparisonExamples() {
    System.out.println("--- Part 4: Comparing Validation Strategies ---\n");

    OrderForm testOrder =
        new OrderForm(
            "TEST-001",
            List.of(
                new BigDecimal("-100.00"), // Error 1: negative
                new BigDecimal("20000.00"), // Error 2: exceeds max
                new BigDecimal("50.00")), // Valid
            "test@example.com");

    System.out.println("Test order with multiple invalid prices:");
    System.out.println("  Prices: " + testOrder.itemPrices());
    System.out.println();

    // Strategy 1: Either (short-circuits)
    System.out.println("Strategy 1: Using Either (short-circuits at first error)");
    Either<String, OrderForm> eitherResult =
        OpticOps.modifyAllEither(
            testOrder, ALL_ITEM_PRICES, FluentValidationExample::validatePriceEither);

    eitherResult.fold(
        error -> {
          System.out.println("  Result: FAILED - " + error);
          return null;
        },
        order -> {
          System.out.println("  Result: SUCCESS");
          return null;
        });
    System.out.println();

    // Strategy 2: Validated (accumulates all errors)
    System.out.println("Strategy 2: Using Validated (accumulates all errors)");
    Validated<List<String>, OrderForm> validatedResult =
        OpticOps.modifyAllValidated(
            testOrder, ALL_ITEM_PRICES, FluentValidationExample::validatePrice);

    validatedResult.fold(
        errors -> {
          System.out.println("  Result: FAILED - " + errors.size() + " error(s)");
          errors.forEach(error -> System.out.println("    * " + error));
          return null;
        },
        order -> {
          System.out.println("  Result: SUCCESS");
          return null;
        });
    System.out.println();

    System.out.println("Key Differences:");
    System.out.println("  • Either: Fast failure, reports only first error");
    System.out.println("  • Validated: Checks all fields, reports all errors");
    System.out.println("  • Maybe: No error details, just success/nothing");
    System.out.println();
    System.out.println("When to use:");
    System.out.println("  • Either: Sequential workflows, early exit desired");
    System.out.println("  • Validated: Form validation, comprehensive error reporting");
    System.out.println("  • Maybe: Optional enrichment, error details not needed");
  }
}
