// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.affine;

// ANCHOR: config_model
import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.util.Prisms;

// Domain model with nested optionals
record AppConfig(String appName, Optional<DatabaseConfig> database, Optional<CacheConfig> cache) {}

record DatabaseConfig(String host, int port, Optional<PoolConfig> pool) {}

record PoolConfig(int minSize, int maxSize) {}

record CacheConfig(String provider, int ttlSeconds) {}

// ANCHOR_END: config_model

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/affine.html">Affines</a> page, in its
 * configuration management example. The page {@code {{#include}}}s the anchored regions, and the
 * book's output gate runs {@code main} to hold each output comment to what it prints.
 */
// ANCHOR: config_optics
public class ConfigOptics {
  // Lenses for required fields
  public static final Lens<AppConfig, String> appName =
      Lens.of(AppConfig::appName, (c, n) -> new AppConfig(n, c.database(), c.cache()));

  public static final Lens<AppConfig, Optional<DatabaseConfig>> database =
      Lens.of(AppConfig::database, (c, db) -> new AppConfig(c.appName(), db, c.cache()));

  public static final Lens<DatabaseConfig, String> host =
      Lens.of(DatabaseConfig::host, (db, h) -> new DatabaseConfig(h, db.port(), db.pool()));

  public static final Lens<DatabaseConfig, Optional<PoolConfig>> pool =
      Lens.of(DatabaseConfig::pool, (db, p) -> new DatabaseConfig(db.host(), db.port(), p));

  public static final Lens<PoolConfig, Integer> maxSize =
      Lens.of(PoolConfig::maxSize, (p, m) -> new PoolConfig(p.minSize(), m));

  // Affines for optional access
  public static final Affine<AppConfig, DatabaseConfig> databaseAffine =
      database.andThen(Prisms.some());

  public static final Affine<AppConfig, String> databaseHost = databaseAffine.andThen(host);

  public static final Affine<AppConfig, PoolConfig> poolConfig =
      databaseAffine.andThen(pool).andThen(Prisms.some());

  public static final Affine<AppConfig, Integer> poolMaxSize = poolConfig.andThen(maxSize);

  public static void main(String[] args) {
    // Create a config with nested optionals
    AppConfig config =
        new AppConfig(
            "MyApp",
            Optional.of(new DatabaseConfig("localhost", 5432, Optional.of(new PoolConfig(5, 20)))),
            Optional.empty());

    // Read nested values safely
    Optional<String> configHost = databaseHost.getOptional(config);
    // Optional[localhost]
    System.out.println("Host: " + configHost);

    Optional<Integer> poolMax = poolMaxSize.getOptional(config);
    // Optional[20]
    System.out.println("Pool max: " + poolMax);

    // Update deeply nested value
    AppConfig updated = poolMaxSize.set(50, config);
    Optional<Integer> updatedMax = poolMaxSize.getOptional(updated);
    // Optional[50]
    System.out.println("Updated pool max: " + updatedMax);

    // Conditional modification
    AppConfig doubled = poolMaxSize.modify(n -> n * 2, config);
    Optional<Integer> doubledMax = poolMaxSize.getOptional(doubled);
    // Optional[40]
    System.out.println("Doubled pool max: " + doubledMax);

    // Safe operation on missing config: no database reads as empty, and nothing throws
    AppConfig emptyConfig = new AppConfig("EmptyApp", Optional.empty(), Optional.empty());
    boolean hostMissing = databaseHost.getOptional(emptyConfig).isEmpty();
    // true
    System.out.println("Missing host: " + hostMissing);

    // Modification on missing does nothing
    AppConfig unchanged = poolMaxSize.modify(n -> n * 2, emptyConfig);
    boolean sameConfig = unchanged == emptyConfig;
    // true
    System.out.println("Empty config unchanged: " + sameConfig);
  }
}
// ANCHOR_END: config_optics
