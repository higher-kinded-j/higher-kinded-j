// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for tracking default method coverage in interfaces.
 *
 * <p>This test class helps identify default methods in hkj-api interfaces that are overridden by
 * ALL implementing classes in hkj-core. Such methods are unreachable through the default
 * implementation and should be excluded from JaCoCo coverage metrics.
 *
 * <p>When this test reports unreachable default methods, add them to the JaCoCo exclusion list in
 * hkj-core/build.gradle.kts.
 */
@DisplayName("Default Method Coverage Rules")
class DefaultMethodCoverageRules {

  private static final String API_PACKAGE = "org.higherkindedj.hkt";
  private static final String OPTICS_API_PACKAGE = "org.higherkindedj.optics";

  private static JavaClasses productionClasses;

  @BeforeAll
  static void setup() {
    productionClasses = getProductionClasses();
  }

  /**
   * Analyzes default methods in API interfaces and reports which ones are overridden by all
   * implementations.
   *
   * <p>This is an informational test that helps maintain the JaCoCo exclusion list. Default methods
   * that are overridden by ALL implementations are unreachable and should be excluded from coverage
   * metrics.
   */
  @Test
  @DisplayName("Report default methods overridden by all implementations")
  void report_unreachable_default_methods() {
    Map<String, Set<String>> unreachableMethodsByInterface = detectUnreachableDefaultMethods();

    // Report findings (informational - does not fail the test)
    if (!unreachableMethodsByInterface.isEmpty()) {
      StringBuilder report = new StringBuilder();
      report.append("\n\n=== Unreachable Default Methods Report ===\n");
      report.append("The following default methods are overridden by ALL implementations.\n");
      report.append("Consider adding them to JaCoCo exclusions in hkj-core/build.gradle.kts:\n\n");

      for (var entry : unreachableMethodsByInterface.entrySet()) {
        report.append("Interface: ").append(entry.getKey()).append("\n");
        for (String method : entry.getValue()) {
          report.append("  - ").append(method).append("\n");
        }
      }
      report.append("\n===========================================\n");

      // Print as info (test passes but reports findings)
      System.out.println(report);
    }
  }

  /**
   * Verifies that the known unreachable default methods match the actually detected ones.
   *
   * <p>This test ensures the JaCoCo exclusion list stays in sync with the codebase. If a default
   * method becomes reachable (no longer overridden by all implementations) or a new unreachable
   * method is discovered, this test will fail and indicate what needs to be updated.
   */
  @Test
  @DisplayName("Known unreachable default methods match detected ones")
  void known_unreachable_methods_are_documented() {
    // Currently known unreachable default methods (add entries as discovered)
    // Format: "InterfaceName.methodSignature"
    Set<String> knownUnreachable =
        Set.of(
            // Example entries (uncomment and add real ones as discovered):
            // "Applicative.map(Function,Kind)"  // Overridden by all Applicative implementations
            );

    // Detect actually unreachable methods
    Map<String, Set<String>> detected = detectUnreachableDefaultMethods();

    // Flatten detected methods to "InterfaceName.methodSignature" format for comparison
    Set<String> detectedFlat = new HashSet<>();
    for (var entry : detected.entrySet()) {
      for (String method : entry.getValue()) {
        detectedFlat.add(entry.getKey() + "." + method);
      }
    }

    // Verify the sets match - this ensures JaCoCo exclusions stay in sync
    assertThat(detectedFlat)
        .as(
            "Detected unreachable default methods should match known list. "
                + "If this fails, update knownUnreachable set or JaCoCo exclusions in build.gradle.kts")
        .isEqualTo(knownUnreachable);
  }

  /**
   * Detects default methods that are overridden by ALL implementations and thus unreachable through
   * the default implementation.
   *
   * @return Map of interface simple name to set of unreachable method signatures
   */
  private Map<String, Set<String>> detectUnreachableDefaultMethods() {
    // Find all interfaces with default methods in the API packages
    List<JavaClass> apiInterfaces =
        productionClasses.stream()
            .filter(JavaClass::isInterface)
            .filter(
                c ->
                    c.getPackageName().startsWith(API_PACKAGE)
                        || c.getPackageName().startsWith(OPTICS_API_PACKAGE))
            .filter(c -> hasDefaultMethods(c))
            .toList();

    // For each interface, find implementing classes and check default method overrides
    Map<String, Set<String>> unreachableMethodsByInterface = new HashMap<>();

    for (JavaClass apiInterface : apiInterfaces) {
      Set<String> defaultMethodNames = getDefaultMethodSignatures(apiInterface);
      if (defaultMethodNames.isEmpty()) {
        continue;
      }

      // Find all concrete implementations
      String interfaceName = apiInterface.getName();
      List<JavaClass> implementations =
          productionClasses.stream()
              .filter(c -> !c.isInterface())
              .filter(c -> c.isAssignableTo(interfaceName))
              .filter(c -> !c.getSimpleName().contains("Test"))
              .toList();

      if (implementations.isEmpty()) {
        continue;
      }

      // Check which default methods are overridden by ALL implementations
      Set<String> unreachable = new HashSet<>(defaultMethodNames);
      for (String methodSig : defaultMethodNames) {
        for (JavaClass impl : implementations) {
          if (!overridesMethod(impl, methodSig)) {
            unreachable.remove(methodSig);
            break;
          }
        }
      }

      if (!unreachable.isEmpty()) {
        unreachableMethodsByInterface.put(apiInterface.getSimpleName(), unreachable);
      }
    }

    return unreachableMethodsByInterface;
  }

  private boolean hasDefaultMethods(JavaClass javaClass) {
    return javaClass.getMethods().stream().anyMatch(m -> m.getModifiers().contains("default"));
  }

  private Set<String> getDefaultMethodSignatures(JavaClass javaClass) {
    return javaClass.getMethods().stream()
        .filter(m -> m.getModifiers().contains("default"))
        .map(this::getMethodSignature)
        .collect(Collectors.toSet());
  }

  private String getMethodSignature(JavaMethod method) {
    return method.getName()
        + "("
        + method.getRawParameterTypes().stream()
            .map(JavaClass::getSimpleName)
            .collect(Collectors.joining(","))
        + ")";
  }

  private boolean overridesMethod(JavaClass impl, String methodSignature) {
    return impl.getMethods().stream()
        .filter(m -> !m.getModifiers().contains("default"))
        .anyMatch(m -> getMethodSignature(m).equals(methodSignature));
  }
}
