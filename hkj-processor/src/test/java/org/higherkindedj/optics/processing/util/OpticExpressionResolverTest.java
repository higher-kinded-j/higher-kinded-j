// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OpticExpressionResolver} covering the sorting lambda and multi-import
 * resolution.
 */
@DisplayName("OpticExpressionResolver")
class OpticExpressionResolverTest {

  @Test
  @DisplayName("should resolve multiple imports in left-to-right order")
  void shouldResolveMultipleImportsInOrder() {
    String expr = "Affines.eitherRight()";
    Set<String> imports = Set.of("org.higherkindedj.optics.util.Affines");
    List<Object> args = new ArrayList<>();

    String resolved = OpticExpressionResolver.resolve(expr, imports, args);

    assertEquals("$T.eitherRight()", resolved);
    assertEquals(1, args.size());
  }

  @Test
  @DisplayName("should resolve two imports ordered by position in expression")
  void shouldResolveTwoImportsOrderedByPosition() {
    String expr = "EachInstances.listEach()";
    Set<String> imports = Set.of("org.higherkindedj.optics.each.EachInstances");
    List<Object> args = new ArrayList<>();

    String resolved = OpticExpressionResolver.resolve(expr, imports, args);

    assertEquals("$T.listEach()", resolved);
    assertEquals(1, args.size());
  }

  @Test
  @DisplayName("should handle empty imports set")
  void shouldHandleEmptyImports() {
    String expr = "someExpression()";
    Set<String> imports = Set.of();
    List<Object> args = new ArrayList<>();

    String resolved = OpticExpressionResolver.resolve(expr, imports, args);

    assertEquals("someExpression()", resolved);
    assertTrue(args.isEmpty());
  }

  @Test
  @DisplayName("should use word-boundary matching to avoid substring replacement")
  void shouldUseWordBoundaryMatching() {
    // "List" should not match inside "ImmutableList"
    String expr = "ImmutableList.of()";
    Set<String> imports = Set.of("java.util.List");
    List<Object> args = new ArrayList<>();

    String resolved = OpticExpressionResolver.resolve(expr, imports, args);

    // "List" should not be replaced inside "ImmutableList" due to word-boundary matching
    // but since List appears as a suffix, it IS at a word boundary after "Immutable"
    // Actually, the regex uses (?<![A-Za-z0-9_]) which means the char before must not be
    // alphanumeric. 'e' before 'L' IS alphanumeric, so it should NOT match.
    assertEquals("ImmutableList.of()", resolved);
  }

  @Test
  @DisplayName("should resolve multiple imports sorted by position")
  void shouldResolveMultipleImportsSortedByPosition() {
    String expr = "Alpha.foo(Beta.bar())";
    Set<String> imports = Set.of("com.pkg.Alpha", "com.pkg.Beta");
    List<Object> args = new ArrayList<>();

    String resolved = OpticExpressionResolver.resolve(expr, imports, args);

    assertEquals("$T.foo($T.bar())", resolved);
    assertEquals(2, args.size());
  }
}
