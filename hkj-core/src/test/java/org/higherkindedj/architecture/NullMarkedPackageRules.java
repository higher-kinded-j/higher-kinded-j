// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing @NullMarked package annotation patterns.
 *
 * <p>In higher-kinded-j, every package uses the @NullMarked annotation from JSpecify in its
 * package-info.java. The module-info carries it too, but a nullness checker reading the jar from
 * the classpath, such as NullAway, ignores the module's annotation and reads only the package's.
 * This rule verifies that every package of hkj-core, hkj-api and hkj-test that holds a source file
 * has a package-info.java, and that it carries @NullMarked.
 *
 * <p>Note: This test uses file system checks rather than ArchUnit because ArchUnit's
 * ClassFileImporter does not load package-info.class files by default.
 */
@DisplayName("NullMarked Package Annotation Rules")
class NullMarkedPackageRules {

  /** The main source roots whose every package must be {@code @NullMarked}. */
  private static final List<String> SOURCE_ROOTS =
      List.of("hkj-core/src/main/java", "hkj-api/src/main/java", "hkj-test/src/main/java");

  /**
   * Verify that every package has a package-info.java with {@code @NullMarked}.
   *
   * <p>A package is any directory under a source root that holds a Java source file other than
   * package-info.java or module-info.java.
   */
  @Test
  @DisplayName(
      "Every hkj-core, hkj-api and hkj-test package should have package-info.java with @NullMarked")
  void every_package_should_have_nullmarked() throws IOException {
    Path projectRoot = findProjectRoot();
    List<String> scanned = new ArrayList<>();
    List<String> missingPackages = new ArrayList<>();
    List<String> missingNullMarked = new ArrayList<>();

    for (String root : SOURCE_ROOTS) {
      Path sourceRoot = projectRoot.resolve(root);
      for (Path directory : packageDirectories(sourceRoot)) {
        String pkg = sourceRoot.relativize(directory).toString().replace(File.separatorChar, '.');
        scanned.add(pkg);
        Path packageInfoPath = directory.resolve("package-info.java");
        if (!Files.exists(packageInfoPath)) {
          missingPackages.add(pkg);
        } else if (!isNullMarked(Files.readString(packageInfoPath))) {
          missingNullMarked.add(pkg);
        }
      }
    }

    // A scan that finds nothing would pass while checking nothing.
    assertTrue(
        scanned.contains("org.higherkindedj.hkt.either")
            && scanned.contains("org.higherkindedj.hkt")
            && scanned.contains("org.higherkindedj.hkt.assertions"),
        "The scan did not find the hkj-core, hkj-api and hkj-test packages under " + SOURCE_ROOTS);

    StringBuilder errors = new StringBuilder();

    if (!missingPackages.isEmpty()) {
      errors.append("The following packages are missing package-info.java:\n");
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
   * Lists the directories under a source root that hold a Java source file other than
   * package-info.java or module-info.java.
   *
   * @param sourceRoot the source root to walk
   * @return each package directory, once, in order
   */
  private static List<Path> packageDirectories(Path sourceRoot) throws IOException {
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      return files
          .filter(file -> file.getFileName().toString().endsWith(".java"))
          .filter(file -> !file.getFileName().toString().equals("package-info.java"))
          .filter(file -> !file.getFileName().toString().equals("module-info.java"))
          .map(Path::getParent)
          .distinct()
          .sorted()
          .toList();
    }
  }

  /**
   * Whether a package-info.java source carries {@code @NullMarked}.
   *
   * @param content the source text
   * @return true if the annotation is present
   */
  private static boolean isNullMarked(String content) {
    return content.contains("@NullMarked")
        || content.contains("@org.jspecify.annotations.NullMarked");
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
