// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.alternative;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;

/**
 * Demonstrates the Alternative type class with a practical configuration loading example.
 *
 * <p>This example shows how Alternative enables elegant fallback chains when loading configuration
 * from multiple sources. The system tries sources in priority order, using the first successful
 * one.
 *
 * <p>Key Alternative concepts demonstrated:
 *
 * <ul>
 *   <li><b>orElse()</b>: Combining alternatives with lazy evaluation
 *   <li><b>empty()</b>: Representing the absence of configuration
 *   <li><b>guard()</b>: Conditional validation
 *   <li><b>orElseAll()</b>: Chaining multiple fallback sources
 * </ul>
 */
public class AlternativeConfigExample {

  // Alternative instance for Maybe
  private static final Alternative<MaybeKind.Witness> alt = MaybeMonad.INSTANCE;

  public static void main(String[] args) {
    System.out.println("=== Alternative Type Class Example: Configuration Loading ===\n");

    demonstrateBasicOrElse();
    demonstrateOrElseChain();
    demonstrateGuard();
    demonstrateLazyEvaluation();
    demonstrateParserCombinator();
  }

  /** Demonstrates basic orElse() for trying two sources. */
  private static void demonstrateBasicOrElse() {
    System.out.println("1. Basic orElse() - Fallback Pattern");
    System.out.println("-----------------------------------");

    // Primary source (environment variable) - not found
    Kind<MaybeKind.Witness, ConfigValue> fromEnv = readFromEnvironment("DATABASE_URL");

    // Fallback source (config file) - found
    Kind<MaybeKind.Witness, ConfigValue> fromFile = readFromConfigFile("DATABASE_URL");

    // Use Alternative.orElse() to try env first, then file
    Kind<MaybeKind.Witness, ConfigValue> result = alt.orElse(fromEnv, () -> fromFile);

    Maybe<ConfigValue> config = MAYBE.narrow(result);
    if (config.isJust()) {
      System.out.println("  ✓ Loaded: " + config.get());
    }
    System.out.println();
  }

  /** Demonstrates orElseAll() for chaining multiple sources. */
  private static void demonstrateOrElseChain() {
    System.out.println("2. orElseAll() - Multiple Fallback Sources");
    System.out.println("------------------------------------------");

    String key = "API_KEY";

    // Try multiple sources in priority order
    Kind<MaybeKind.Witness, ConfigValue> config =
        alt.orElseAll(
            readFromEnvironment(key),
            () -> readFromSystemProperty(key),
            () -> readFromConfigFile(key),
            () -> readFromDatabase(key),
            () -> getDefaultValue(key));

    Maybe<ConfigValue> result = MAYBE.narrow(config);
    if (result.isJust()) {
      System.out.println("  ✓ Loaded: " + result.get());
    }
    System.out.println();
  }

  /** Demonstrates guard() for conditional validation. */
  private static void demonstrateGuard() {
    System.out.println("3. guard() - Conditional Validation");
    System.out.println("-----------------------------------");

    String key = "MAX_CONNECTIONS";

    Kind<MaybeKind.Witness, ConfigValue> config = readFromConfigFile(key);

    // Validate that the config value is reasonable
    Kind<MaybeKind.Witness, ConfigValue> validated =
        MaybeMonad.INSTANCE.flatMap(
            value -> {
              int maxConn = Integer.parseInt(value.value());
              boolean isValid = maxConn > 0 && maxConn <= 1000;

              // guard(true) returns Just(Unit), guard(false) returns Nothing
              return MaybeMonad.INSTANCE.map(unit -> value, alt.guard(isValid));
            },
            config);

    Maybe<ConfigValue> result = MAYBE.narrow(validated);
    if (result.isJust()) {
      System.out.println("  ✓ Valid: " + result.get());
    } else {
      System.out.println("  ✗ Validation failed (value out of range)");
    }
    System.out.println();
  }

  /** Demonstrates lazy evaluation - fallback not computed unless needed. */
  private static void demonstrateLazyEvaluation() {
    System.out.println("4. Lazy Evaluation - Efficiency");
    System.out.println("-------------------------------");

    boolean[] expensiveCallExecuted = {false};

    Kind<MaybeKind.Witness, ConfigValue> primary =
        MAYBE.just(new ConfigValue("found-in-primary", ConfigSource.ENVIRONMENT_VARIABLE));

    Kind<MaybeKind.Witness, ConfigValue> result =
        alt.orElse(
            primary,
            () -> {
              expensiveCallExecuted[0] = true;
              System.out.println("  ⚠ Expensive fallback computation running...");
              return readFromDatabase("SOME_KEY");
            });

    Maybe<ConfigValue> config = MAYBE.narrow(result);
    System.out.println("  ✓ Result: " + config.get());
    System.out.println("  ℹ Expensive call executed: " + expensiveCallExecuted[0]);
    System.out.println();
  }

  /** Demonstrates using Alternative for simple parser combinators. */
  private static void demonstrateParserCombinator() {
    System.out.println("5. Parser Combinator Pattern");
    System.out.println("----------------------------");

    String input1 = "true";
    String input2 = "123";
    String input3 = "hello";

    System.out.println("  Parsing '" + input1 + "':");
    Maybe<String> result1 = parseValue(input1);
    if (result1.isJust()) {
      System.out.println("    ✓ " + result1.get());
    }

    System.out.println("  Parsing '" + input2 + "':");
    Maybe<String> result2 = parseValue(input2);
    if (result2.isJust()) {
      System.out.println("    ✓ " + result2.get());
    }

    System.out.println("  Parsing '" + input3 + "':");
    Maybe<String> result3 = parseValue(input3);
    if (result3.isNothing()) {
      System.out.println("    ✗ Not a boolean or integer");
    }
    System.out.println();
  }

  /** Tries to parse input as boolean, then as integer, then returns original string. */
  private static Maybe<String> parseValue(String input) {
    Kind<MaybeKind.Witness, String> result =
        alt.orElseAll(
            parseBoolean(input), () -> parseInt(input), () -> MAYBE.just("String: " + input));
    return MAYBE.narrow(result);
  }

  private static Kind<MaybeKind.Witness, String> parseBoolean(String s) {
    if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
      return MAYBE.just("Boolean: " + s);
    }
    return MAYBE.nothing();
  }

  private static Kind<MaybeKind.Witness, String> parseInt(String s) {
    try {
      int value = Integer.parseInt(s);
      return MAYBE.just("Integer: " + value);
    } catch (NumberFormatException e) {
      return MAYBE.nothing();
    }
  }

  // === Simulated Configuration Sources ===

  private static Kind<MaybeKind.Witness, ConfigValue> readFromEnvironment(String key) {
    // Simulate environment variable lookup
    String value = System.getenv(key);
    if (value != null) {
      return MAYBE.just(new ConfigValue(value, ConfigSource.ENVIRONMENT_VARIABLE));
    }
    System.out.println("  ✗ Not found in environment variables");
    return MAYBE.nothing();
  }

  private static Kind<MaybeKind.Witness, ConfigValue> readFromSystemProperty(String key) {
    // Simulate system property lookup
    String value = System.getProperty(key);
    if (value != null) {
      return MAYBE.just(new ConfigValue(value, ConfigSource.SYSTEM_PROPERTY));
    }
    System.out.println("  ✗ Not found in system properties");
    return MAYBE.nothing();
  }

  private static Kind<MaybeKind.Witness, ConfigValue> readFromConfigFile(String key) {
    // Simulate reading from config file
    System.out.println("  → Checking config file...");
    // Simulate some keys being found
    if (key.equals("DATABASE_URL") || key.equals("MAX_CONNECTIONS")) {
      String value = key.equals("DATABASE_URL") ? "jdbc:postgresql://localhost:5432/mydb" : "100";
      return MAYBE.just(new ConfigValue(value, ConfigSource.CONFIG_FILE));
    }
    System.out.println("  ✗ Not found in config file");
    return MAYBE.nothing();
  }

  private static Kind<MaybeKind.Witness, ConfigValue> readFromDatabase(String key) {
    // Simulate database lookup (expensive operation)
    System.out.println("  → Querying database...");
    // Simulate not found
    System.out.println("  ✗ Not found in database");
    return MAYBE.nothing();
  }

  private static Kind<MaybeKind.Witness, ConfigValue> getDefaultValue(String key) {
    // Always provides a default value
    System.out.println("  → Using default value");
    String defaultValue = "default-" + key.toLowerCase();
    return MAYBE.just(new ConfigValue(defaultValue, ConfigSource.DEFAULT_VALUE));
  }
}
