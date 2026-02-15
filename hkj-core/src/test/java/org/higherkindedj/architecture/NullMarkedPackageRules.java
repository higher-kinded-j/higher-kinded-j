// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing @NullMarked package annotation patterns.
 *
 * <p>In higher-kinded-j, all packages should use @NullMarked annotation from JSpecify to enable
 * null-safety checking at the package level. This rule verifies that:
 *
 * <ul>
 *   <li>All HKT packages have a package-info.java with @NullMarked
 *   <li>The package-info.java files contain the @NullMarked annotation
 * </ul>
 *
 * <p>Note: This test uses file system checks rather than ArchUnit because ArchUnit's
 * ClassFileImporter does not load package-info.class files by default.
 */
@DisplayName("NullMarked Package Annotation Rules")
class NullMarkedPackageRules {

  /** Base path for hkj-core main sources. */
  private static final String HKJ_CORE_MAIN = "hkj-core/src/main/java/org/higherkindedj/hkt";

  /** Base path for hkj-api main sources. */
  private static final String HKJ_API_MAIN = "hkj-api/src/main/java/org/higherkindedj";

  /**
   * Core HKT packages that must have @NullMarked via package-info.java.
   *
   * <p>These are the primary type packages in hkj-core.
   */
  private static final String[] CORE_HKT_PACKAGES = {
    "maybe",
    "either",
    "trymonad",
    "validated",
    "list",
    "optional",
    "id",
    "state",
    "reader",
    "writer",
    "io",
    "lazy",
    "future",
    "stream",
    "trampoline"
  };

  /**
   * Verify that package-info.java files exist for core HKT types.
   *
   * <p>This test checks that package-info.java files exist for core HKT packages and contain the
   * {@code @NullMarked} annotation.
   */
  @Test
  @DisplayName("Core HKT packages should have package-info.java with @NullMarked")
  void core_hkt_packages_should_have_nullmarked() throws IOException {
    Path projectRoot = findProjectRoot();
    List<String> missingPackages = new ArrayList<>();
    List<String> missingNullMarked = new ArrayList<>();

    for (String pkg : CORE_HKT_PACKAGES) {
      Path packageInfoPath =
          projectRoot.resolve(HKJ_CORE_MAIN).resolve(pkg).resolve("package-info.java");

      if (!Files.exists(packageInfoPath)) {
        missingPackages.add("org.higherkindedj.hkt." + pkg);
      } else {
        // Check if the file contains @NullMarked
        String content = Files.readString(packageInfoPath);
        if (!content.contains("@NullMarked")
            && !content.contains("@org.jspecify.annotations.NullMarked")) {
          missingNullMarked.add("org.higherkindedj.hkt." + pkg);
        }
      }
    }

    StringBuilder errors = new StringBuilder();

    if (!missingPackages.isEmpty()) {
      errors.append("The following core HKT packages are missing package-info.java:\n");
      missingPackages.forEach(pkg -> errors.append("  - ").append(pkg).append("\n"));
    }

    if (!missingNullMarked.isEmpty()) {
      if (errors.length() > 0) {
        errors.append("\n");
      }
      errors.append("The following packages have package-info.java but missing @NullMarked:\n");
      missingNullMarked.forEach(pkg -> errors.append("  - ").append(pkg).append("\n"));
    }

    assertTrue(
        errors.length() == 0,
        errors
            + "\nEach package should have a package-info.java file with"
            + " @org.jspecify.annotations.NullMarked");
  }

  /**
   * Verify that hkj-api packages have @NullMarked annotations.
   *
   * <p>The API module defines core interfaces like Functor, Applicative, Monad.
   */
  @Test
  @DisplayName("HKJ-API packages should have package-info.java with @NullMarked")
  void api_packages_should_have_nullmarked() throws IOException {
    Path projectRoot = findProjectRoot();
    Path hktPackageInfo = projectRoot.resolve(HKJ_API_MAIN).resolve("hkt/package-info.java");
    Path opticsPackageInfo = projectRoot.resolve(HKJ_API_MAIN).resolve("optics/package-info.java");

    List<String> missingOrInvalid = new ArrayList<>();

    checkPackageInfo(hktPackageInfo, "org.higherkindedj.hkt (api)", missingOrInvalid);
    checkPackageInfo(opticsPackageInfo, "org.higherkindedj.optics (api)", missingOrInvalid);

    assertTrue(
        missingOrInvalid.isEmpty(),
        "The following API packages are missing package-info.java or @NullMarked:\n"
            + String.join("\n", missingOrInvalid));
  }

  /**
   * Check if a package-info.java file exists and contains @NullMarked.
   *
   * @param packageInfoPath path to the package-info.java file
   * @param packageName the package name for error reporting
   * @param errors list to add error messages to
   */
  private void checkPackageInfo(Path packageInfoPath, String packageName, List<String> errors)
      throws IOException {
    if (!Files.exists(packageInfoPath)) {
      errors.add("  - " + packageName + " (missing package-info.java)");
    } else {
      String content = Files.readString(packageInfoPath);
      if (!content.contains("@NullMarked")
          && !content.contains("@org.jspecify.annotations.NullMarked")) {
        errors.add("  - " + packageName + " (missing @NullMarked annotation)");
      }
    }
  }

  /**
   * Find the project root directory by looking for settings.gradle.kts.
   *
   * @return the project root path
   */
  private Path findProjectRoot() {
    Path current = Paths.get("").toAbsolutePath();

    // Walk up the directory tree to find the project root
    while (current != null) {
      if (Files.exists(current.resolve("settings.gradle.kts"))) {
        return current;
      }
      current = current.getParent();
    }

    // Fallback to current directory
    return Paths.get("").toAbsolutePath();
  }
}
