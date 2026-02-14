// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.Map;
import java.util.Optional;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.util.Traversals;

/**
 * A runnable example demonstrating configuration management using prisms for type-safe, layered
 * configuration resolution.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Type-safe configuration value handling with sealed interfaces
 *   <li>Layered configuration resolution (environment, file, defaults)
 *   <li>Type coercion with fallback strategies using {@code orElse()}
 *   <li>Nested configuration access with composed prisms
 *   <li>Validation and constraint enforcement using {@code modifyWhen()}
 * </ul>
 */
public class ConfigurationManagementExample {

  // Configuration value types
  @GeneratePrisms
  public sealed interface ConfigValue permits StringValue, IntValue, BoolValue, NestedConfig {}

  @GenerateLenses
  public record StringValue(String value) implements ConfigValue {}

  @GenerateLenses
  public record IntValue(int value) implements ConfigValue {}

  public record BoolValue(boolean value) implements ConfigValue {}

  @GenerateLenses
  public record NestedConfig(Map<String, ConfigValue> values) implements ConfigValue {}

  // Application configuration
  @GenerateLenses
  public record AppConfig(Map<String, ConfigValue> settings) {}

  // Prisms for type-safe access
  private static final Prism<ConfigValue, IntValue> INT = ConfigValuePrisms.intValue();
  private static final Prism<ConfigValue, StringValue> STRING = ConfigValuePrisms.stringValue();
  private static final Prism<ConfigValue, BoolValue> BOOL = ConfigValuePrisms.boolValue();
  private static final Prism<ConfigValue, NestedConfig> NESTED = ConfigValuePrisms.nestedConfig();

  // Default values
  private static final int DEFAULT_POOL_SIZE = 10;
  private static final int DEFAULT_TIMEOUT_MS = 5000;
  private static final boolean DEFAULT_DEBUG_MODE = false;

  public static void main(String[] args) {
    System.out.println("=== Configuration Management with Prisms ===\n");

    demonstrateBasicAccess();
    demonstrateTypeCoercion();
    demonstrateNestedAccess();
    demonstrateValidation();
    demonstrateLayeredResolution();
  }

  private static void demonstrateBasicAccess() {
    System.out.println("--- Basic Type-Safe Configuration Access ---");

    AppConfig config =
        new AppConfig(
            Map.of(
                "pool.size", new IntValue(20),
                "server.host", new StringValue("localhost"),
                "debug.enabled", new BoolValue(true)));

    // Type-safe extraction
    int poolSize =
        INT.mapOptional(IntValue::value, config.settings().get("pool.size"))
            .orElse(DEFAULT_POOL_SIZE);

    String serverHost =
        STRING
            .mapOptional(StringValue::value, config.settings().get("server.host"))
            .orElse("0.0.0.0");

    boolean debugEnabled =
        BOOL.mapOptional(BoolValue::value, config.settings().get("debug.enabled"))
            .orElse(DEFAULT_DEBUG_MODE);

    System.out.println("Pool size: " + poolSize);
    System.out.println("Server host: " + serverHost);
    System.out.println("Debug enabled: " + debugEnabled);

    // Handle missing keys safely
    int missingValue =
        INT.mapOptional(IntValue::value, config.settings().get("missing.key"))
            .orElse(DEFAULT_POOL_SIZE);

    System.out.println("Missing key (with default): " + missingValue);

    System.out.println();
  }

  private static void demonstrateTypeCoercion() {
    System.out.println("--- Type Coercion with Fallback Strategies ---");

    // Config with mixed types - some values might be strings that need parsing
    AppConfig config =
        new AppConfig(
            Map.of(
                "timeout.ms", new IntValue(3000),
                "pool.size", new StringValue("15"), // String instead of int
                "retries", new StringValue("not a number"))); // Invalid

    // Try integer first, fall back to parsing string using mapOptional
    int timeout =
        INT.mapOptional(IntValue::value, config.settings().get("timeout.ms"))
            .or(
                () ->
                    STRING
                        .mapOptional(StringValue::value, config.settings().get("timeout.ms"))
                        .flatMap(ConfigurationManagementExample::safeParseInt))
            .orElse(DEFAULT_TIMEOUT_MS);

    int poolSize =
        INT.mapOptional(IntValue::value, config.settings().get("pool.size"))
            .or(
                () ->
                    STRING
                        .mapOptional(StringValue::value, config.settings().get("pool.size"))
                        .flatMap(ConfigurationManagementExample::safeParseInt))
            .orElse(DEFAULT_POOL_SIZE);

    int retries =
        INT.mapOptional(IntValue::value, config.settings().get("retries"))
            .or(
                () ->
                    STRING
                        .mapOptional(StringValue::value, config.settings().get("retries"))
                        .flatMap(ConfigurationManagementExample::safeParseInt))
            .orElse(3);

    System.out.println("Timeout (from int): " + timeout + "ms");
    System.out.println("Pool size (from string): " + poolSize);
    System.out.println("Retries (invalid, using default): " + retries);

    System.out.println();
  }

  private static void demonstrateNestedAccess() {
    System.out.println("--- Nested Configuration Access ---");

    // Create nested configuration structure
    Map<String, ConfigValue> databaseSettings =
        Map.of(
            "host", new StringValue("db.example.com"),
            "port", new IntValue(5432),
            "ssl", new BoolValue(true));

    Map<String, ConfigValue> serverSettings =
        Map.of(
            "database", new NestedConfig(databaseSettings),
            "timeout", new IntValue(30000));

    AppConfig config = new AppConfig(Map.of("server", new NestedConfig(serverSettings)));

    // Build path to nested value: config -> server -> database -> host
    Lens<AppConfig, Map<String, ConfigValue>> settingsLens = AppConfigLenses.settings();
    Lens<NestedConfig, Map<String, ConfigValue>> valuesLens = NestedConfigLenses.values();

    Traversal<AppConfig, String> databaseHost =
        settingsLens
            .asTraversal()
            .andThen(Traversals.forMap("server"))
            .andThen(NESTED.asTraversal())
            .andThen(valuesLens.asTraversal())
            .andThen(Traversals.forMap("database"))
            .andThen(NESTED.asTraversal())
            .andThen(valuesLens.asTraversal())
            .andThen(Traversals.forMap("host"))
            .andThen(STRING.asTraversal())
            .andThen(StringValueLenses.value().asTraversal());

    Optional<String> host = Traversals.getAll(databaseHost, config).stream().findFirst();

    System.out.println("Database host: " + host.orElse("N/A"));

    // Extract nested integer
    Traversal<AppConfig, Integer> databasePort =
        settingsLens
            .asTraversal()
            .andThen(Traversals.forMap("server"))
            .andThen(NESTED.asTraversal())
            .andThen(valuesLens.asTraversal())
            .andThen(Traversals.forMap("database"))
            .andThen(NESTED.asTraversal())
            .andThen(valuesLens.asTraversal())
            .andThen(Traversals.forMap("port"))
            .andThen(INT.asTraversal())
            .andThen(IntValueLenses.value().asTraversal());

    Optional<Integer> port = Traversals.getAll(databasePort, config).stream().findFirst();

    System.out.println("Database port: " + port.orElse(-1));

    System.out.println();
  }

  private static void demonstrateValidation() {
    System.out.println("--- Configuration Validation and Constraints ---");

    AppConfig config =
        new AppConfig(
            Map.of(
                "pool.size", new IntValue(150), // Too large
                "timeout.ms", new IntValue(-100), // Negative
                "server.host", new StringValue(""))); // Empty

    // Validate and constrain pool size (1-100)
    ConfigValue poolSize = config.settings().get("pool.size");
    ConfigValue validatedPool =
        INT.modifyWhen(
            iv -> iv.value() < 1 || iv.value() > 100,
            iv -> {
              int clamped = Math.max(1, Math.min(100, iv.value()));
              System.out.println(
                  "  Pool size " + iv.value() + " out of range, clamped to " + clamped);
              return new IntValue(clamped);
            },
            poolSize);

    // Validate timeout (must be positive)
    ConfigValue timeout = config.settings().get("timeout.ms");
    ConfigValue validatedTimeout =
        INT.modifyWhen(
            iv -> iv.value() < 0,
            iv -> {
              System.out.println(
                  "  Timeout " + iv.value() + " is negative, using default " + DEFAULT_TIMEOUT_MS);
              return new IntValue(DEFAULT_TIMEOUT_MS);
            },
            timeout);

    // Validate string not empty
    ConfigValue serverHost = config.settings().get("server.host");
    ConfigValue validatedHost =
        STRING.setWhen(sv -> sv.value().isEmpty(), new StringValue("localhost"), serverHost);

    System.out.println(
        "Validated pool size: " + INT.mapOptional(IntValue::value, validatedPool).orElse(-1));
    System.out.println(
        "Validated timeout: " + INT.mapOptional(IntValue::value, validatedTimeout).orElse(-1));
    System.out.println(
        "Validated host: " + STRING.mapOptional(StringValue::value, validatedHost).orElse("N/A"));

    System.out.println();
  }

  private static void demonstrateLayeredResolution() {
    System.out.println("--- Layered Configuration Resolution ---");

    // Three layers: defaults, file config, environment overrides
    Map<String, ConfigValue> defaults =
        Map.of(
            "pool.size", new IntValue(10),
            "timeout.ms", new IntValue(5000),
            "debug.enabled", new BoolValue(false));

    Map<String, ConfigValue> fileConfig =
        Map.of(
            "pool.size", new IntValue(20),
            "server.host", new StringValue("config.example.com"));

    Map<String, ConfigValue> envOverrides =
        Map.of(
            "debug.enabled", new BoolValue(true),
            "server.host", new StringValue("env.example.com"));

    // Resolution strategy: env -> file -> defaults
    String[] keys = {"pool.size", "timeout.ms", "debug.enabled", "server.host", "missing.key"};

    System.out.println("Configuration resolution (env -> file -> defaults):");
    for (String key : keys) {
      ConfigValue resolved = resolveConfig(key, envOverrides, fileConfig, defaults);

      String valueStr =
          INT.mapOptional(iv -> "IntValue(" + iv.value() + ")", resolved)
              .or(() -> STRING.mapOptional(sv -> "StringValue(" + sv.value() + ")", resolved))
              .or(() -> BOOL.mapOptional(bv -> "BoolValue(" + bv.value() + ")", resolved))
              .orElse("null");

      System.out.println("  " + key + " = " + valueStr);
    }

    System.out.println();
  }

  // Helper methods

  private static Optional<Integer> safeParseInt(String s) {
    try {
      return Optional.of(Integer.parseInt(s));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static ConfigValue resolveConfig(
      String key,
      Map<String, ConfigValue> env,
      Map<String, ConfigValue> file,
      Map<String, ConfigValue> defaults) {
    return Optional.ofNullable(env.get(key))
        .or(() -> Optional.ofNullable(file.get(key)))
        .or(() -> Optional.ofNullable(defaults.get(key)))
        .orElse(new StringValue("unset"));
  }
}
