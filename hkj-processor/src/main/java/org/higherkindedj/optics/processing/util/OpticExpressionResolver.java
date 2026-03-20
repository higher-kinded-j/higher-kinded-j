// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import com.palantir.javapoet.ClassName;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves optic expressions from SPI generators into JavaPoet-compatible format.
 *
 * <p>Replaces simple class names with {@code $T} placeholders and appends the corresponding {@link
 * ClassName} objects to an args list, so that JavaPoet generates proper import statements.
 *
 * <p>The replacement is word-boundary-aware: a simple name like {@code "List"} will not match
 * inside a longer identifier like {@code "ImmutableList"}.
 */
public final class OpticExpressionResolver {

  private OpticExpressionResolver() {}

  /**
   * Resolves an optic expression by replacing simple class names from required imports with {@code
   * $T} placeholders and appending the corresponding {@link ClassName} objects to the args list.
   *
   * <p>Imports are sorted by the position of their simple name in the expression so that positional
   * {@code $T} placeholders are appended to {@code args} in left-to-right order.
   *
   * <p>Replacements use word-boundary matching to avoid incorrectly substituting substrings (e.g.,
   * {@code "List"} inside {@code "ImmutableList"}).
   *
   * @param opticExpr the optic expression string (e.g., {@code "Affines.eitherRight()"})
   * @param requiredImports the set of fully qualified class names referenced in the expression
   * @param args the mutable list to which {@link ClassName} objects are appended
   * @return the resolved expression with simple names replaced by {@code $T}
   */
  public static String resolve(String opticExpr, Set<String> requiredImports, List<Object> args) {
    // Sort imports by the position of their simple name in the expression so that
    // positional $T placeholders are appended to args in left-to-right order.
    List<String> sorted =
        requiredImports.stream()
            .sorted(
                Comparator.comparingInt(
                    fqcn -> {
                      String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
                      return opticExpr.indexOf(simple);
                    }))
            .toList();

    String resolved = opticExpr;
    for (String fqcn : sorted) {
      int lastDot = fqcn.lastIndexOf('.');
      String simpleName = fqcn.substring(lastDot + 1);
      String pkg = fqcn.substring(0, lastDot);
      // Use word-boundary matching so that e.g. "List" does not match inside "ImmutableList".
      resolved =
          Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(simpleName) + "(?![A-Za-z0-9_])")
              .matcher(resolved)
              .replaceFirst("\\$T");
      args.add(ClassName.get(pkg, simpleName));
    }
    return resolved;
  }
}
