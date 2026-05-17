// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

/**
 * Recipe that detects switch expressions matching on Free monad variants that are missing {@code
 * HandleError} and {@code Ap} cases.
 *
 * <p>When the Free monad is extended with error handling via {@code Free.HandleError}, existing
 * interpreters need to add corresponding cases. This recipe marks switch expressions that match on
 * Free variants (Pure, Suspend, FlatMapped) but lack the newer cases.
 *
 * <h2>Detection</h2>
 *
 * <p>Identifies switch expressions/statements where:
 *
 * <ul>
 *   <li>At least one case matches {@code Free.Pure}, {@code Free.Suspend}, or {@code
 *       Free.FlatMapped}
 *   <li>No case matches {@code Free.HandleError} or {@code Free.Ap}
 * </ul>
 *
 * <p>Matched switches are tagged with an OpenRewrite {@link SearchResult} marker so they appear in
 * recipe run reports and data tables without rewriting the user's source.
 *
 * @see "org.higherkindedj.hkt.free.Free"
 */
public class AddHandleErrorCaseRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public AddHandleErrorCaseRecipe() {}

  // Whole-word matching, optionally qualified by a `Free.` prefix. Word boundaries prevent
  // false positives such as "Apply"/"map"/"wrap" matching the "Ap" variant, or an unrelated
  // identifier merely containing "Pure".
  private static final Pattern KNOWN_FREE_CASE =
      Pattern.compile("\\b(?:Free\\s*\\.\\s*)?(Pure|Suspend|FlatMapped)\\b");

  private static final Pattern HANDLE_ERROR_CASE =
      Pattern.compile("\\b(?:Free\\s*\\.\\s*)?HandleError\\b");

  private static final Pattern AP_CASE = Pattern.compile("\\b(?:Free\\s*\\.\\s*)?Ap\\b");

  @Override
  public String getDisplayName() {
    return "Add HandleError and Ap cases to Free switch expressions";
  }

  @Override
  public String getDescription() {
    return "Detects switch expressions that pattern-match on Free monad variants (Pure, Suspend, "
        + "FlatMapped) but are missing HandleError and Ap cases. These cases are required when "
        + "using Free monad error handling extensions.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "effects", "free", "migration");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      // Switch statement form: `switch (free) { case ... }`.
      @Override
      public J.Switch visitSwitch(J.Switch switch_, ExecutionContext ctx) {
        J.Switch s = super.visitSwitch(switch_, ctx);
        return isIncompleteFreeSwitch(s.getCases()) ? markFound(s) : s;
      }

      // Switch expression form: `return switch (free) { case ... }`. Free
      // interpreters almost always use this form, so it must be handled too.
      @Override
      public J.SwitchExpression visitSwitchExpression(
          J.SwitchExpression switch_, ExecutionContext ctx) {
        J.SwitchExpression s = super.visitSwitchExpression(switch_, ctx);
        return isIncompleteFreeSwitch(s.getCases()) ? markFound(s) : s;
      }

      private <T extends J> T markFound(T tree) {
        return SearchResult.found(tree, "Free switch is missing HandleError and/or Ap case(s)");
      }

      private boolean isIncompleteFreeSwitch(J.Block cases) {
        boolean matchesFreeVariants = false;
        boolean hasHandleError = false;
        boolean hasAp = false;

        for (J.Case c : collectCases(cases)) {
          String label = caseLabelText(c);
          if (label.isEmpty()) {
            continue;
          }
          if (KNOWN_FREE_CASE.matcher(label).find()) {
            matchesFreeVariants = true;
          }
          if (HANDLE_ERROR_CASE.matcher(label).find()) {
            hasHandleError = true;
          }
          if (AP_CASE.matcher(label).find()) {
            hasAp = true;
          }
        }

        return matchesFreeVariants && (!hasHandleError || !hasAp);
      }

      private List<J.Case> collectCases(J.Block cases) {
        List<J.Case> result = new ArrayList<>();
        for (var stmt : cases.getStatements()) {
          if (stmt instanceof J.Case c) {
            result.add(c);
          }
        }
        return result;
      }

      // Render only the case label/expression node(s) via structured LST access — never
      // the case body — so identifiers used inside the branch (e.g. a local variable
      // named "apply") cannot trigger a false match. If the label cannot be read
      // structurally we return empty (no match) rather than scanning rendered text.
      private String caseLabelText(J.Case c) {
        StringBuilder sb = new StringBuilder();
        if (c.getCaseLabels() != null) {
          for (J label : c.getCaseLabels()) {
            sb.append(label).append(' ');
          }
        }
        if (c.getExpressions() != null) {
          for (J e : c.getExpressions()) {
            sb.append(e).append(' ');
          }
        }
        return sb.toString();
      }
    };
  }
}
