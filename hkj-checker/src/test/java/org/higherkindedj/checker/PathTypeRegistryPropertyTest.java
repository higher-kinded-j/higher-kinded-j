// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;

/**
 * Property-based tests for {@link PathTypeRegistry} and {@link DiagnosticMessages}.
 *
 * <p>These tests generate random inputs to verify invariants hold across all possible combinations.
 */
class PathTypeRegistryPropertyTest {

  private static final List<String> ALL_SIMPLE_NAMES =
      List.copyOf(PathTypeRegistry.allSimpleNames());

  @Provide
  Arbitrary<String> pathSimpleNames() {
    return Arbitraries.of(ALL_SIMPLE_NAMES);
  }

  @Provide
  Arbitrary<String> methodNames() {
    return Arbitraries.of("via", "flatMap", "then", "zipWith", "zipWith3", "recoverWith",
        "orElse");
  }

  @Property
  void areSamePathFamily_isReflexive(@ForAll("pathSimpleNames") String name) {
    // Construct a qualified name from the simple name
    // Use the effect package as default; this works because areSamePathFamily uses the map
    String qualified = findQualifiedName(name);
    if (qualified != null) {
      assertThat(PathTypeRegistry.areSamePathFamily(qualified, qualified))
          .as("areSamePathFamily should be reflexive for %s", name)
          .isTrue();
    }
  }

  @Property
  void areSamePathFamily_isSymmetric(
      @ForAll("pathSimpleNames") String name1, @ForAll("pathSimpleNames") String name2) {
    String qualified1 = findQualifiedName(name1);
    String qualified2 = findQualifiedName(name2);
    if (qualified1 != null && qualified2 != null) {
      assertThat(PathTypeRegistry.areSamePathFamily(qualified1, qualified2))
          .as(
              "areSamePathFamily should be symmetric for %s and %s",
              name1, name2)
          .isEqualTo(PathTypeRegistry.areSamePathFamily(qualified2, qualified1));
    }
  }

  @Property
  void pathTypeMismatch_alwaysProducesNonEmptyMessage(
      @ForAll("methodNames") String method,
      @ForAll("pathSimpleNames") String expected,
      @ForAll("pathSimpleNames") String actual) {
    String message = DiagnosticMessages.pathTypeMismatch(method, expected, actual);
    assertThat(message)
        .as("Message should never be empty")
        .isNotEmpty();
  }

  @Property
  void pathTypeMismatch_alwaysContainsMethodName(
      @ForAll("methodNames") String method,
      @ForAll("pathSimpleNames") String expected,
      @ForAll("pathSimpleNames") String actual) {
    String message = DiagnosticMessages.pathTypeMismatch(method, expected, actual);
    assertThat(message).contains(method + "()");
  }

  @Property
  void pathTypeMismatch_alwaysContainsExpectedAndActualTypes(
      @ForAll("methodNames") String method,
      @ForAll("pathSimpleNames") String expected,
      @ForAll("pathSimpleNames") String actual) {
    String message = DiagnosticMessages.pathTypeMismatch(method, expected, actual);
    assertThat(message).contains(expected).contains(actual);
  }

  @Property
  void isPathType_consistentWithGetPathCategory(@ForAll("pathSimpleNames") String name) {
    String qualified = findQualifiedName(name);
    if (qualified != null) {
      assertThat(PathTypeRegistry.isPathType(qualified))
          .as("isPathType and getPathCategory should be consistent for %s", name)
          .isEqualTo(PathTypeRegistry.getPathCategory(qualified).isPresent());
    }
  }

  /**
   * Finds the qualified name for a given simple name by searching the registry.
   */
  private String findQualifiedName(String simpleName) {
    // Build a reverse lookup
    String[] packages = {
      "org.higherkindedj.hkt.effect.",
      "org.higherkindedj.hkt.expression.",
      "org.higherkindedj.optics.focus."
    };
    for (String pkg : packages) {
      String qualified = pkg + simpleName;
      if (PathTypeRegistry.isPathType(qualified)) {
        return qualified;
      }
    }
    return null;
  }
}
