// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * Base class providing shared class imports for architecture tests.
 *
 * <p>This class provides a cached import of all production classes using {@link ClassFileImporter}
 * directly, which works reliably in multi-module Gradle projects where the {@code @AnalyzeClasses}
 * annotation may not find all classes.
 *
 * <p>Note: package-info classes are excluded from imports as they don't represent actual code and
 * can cause false positives in architecture tests. This exclusion is required for compatibility
 * with ArchUnit 1.4.x which includes package-info classes in its class resolution.
 */
public final class ArchitectureTestBase {

  /** The base package for all higher-kinded-j code. */
  public static final String BASE_PACKAGE = "org.higherkindedj";

  /**
   * ImportOption that excludes package-info classes from import.
   *
   * <p>ArchUnit 1.4.x includes package-info classes in class resolution, which can cause false
   * positives in architecture tests since these classes don't have typical class characteristics.
   */
  private static final ImportOption DO_NOT_INCLUDE_PACKAGE_INFO =
      (Location location) -> !location.contains("package-info");

  /** Cached import of all production classes (no test classes, no package-info). */
  private static volatile JavaClasses productionClasses;

  /** Cached import of all test classes only. */
  private static volatile JavaClasses testClasses;

  private ArchitectureTestBase() {
    // Utility class
  }

  /**
   * Returns all production classes in the higher-kinded-j packages.
   *
   * <p>This method imports classes programmatically using {@link ClassFileImporter}, which provides
   * reliable class discovery in multi-module projects. The result is cached for performance.
   *
   * <p>Excludes test classes and package-info classes.
   *
   * @return the imported production classes
   */
  public static JavaClasses getProductionClasses() {
    if (productionClasses == null) {
      synchronized (ArchitectureTestBase.class) {
        if (productionClasses == null) {
          productionClasses =
              new ClassFileImporter()
                  .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                  .withImportOption(DO_NOT_INCLUDE_PACKAGE_INFO)
                  .importPackages(BASE_PACKAGE);
        }
      }
    }
    return productionClasses;
  }

  /**
   * Returns all test classes in the higher-kinded-j packages.
   *
   * <p>This method imports only test classes programmatically using {@link ClassFileImporter}. The
   * result is cached for performance.
   *
   * @return the imported test classes
   */
  public static JavaClasses getTestClasses() {
    if (testClasses == null) {
      synchronized (ArchitectureTestBase.class) {
        if (testClasses == null) {
          testClasses =
              new ClassFileImporter()
                  .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                  .importPackages(BASE_PACKAGE);
        }
      }
    }
    return testClasses;
  }
}
