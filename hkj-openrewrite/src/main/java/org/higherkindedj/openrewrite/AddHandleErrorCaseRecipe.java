// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.List;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TextComment;
import org.openrewrite.marker.Markers;

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
 * <p>Matched switches are marked with a search result comment so they appear in recipe run reports.
 *
 * @see org.higherkindedj.hkt.free.Free
 */
public class AddHandleErrorCaseRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public AddHandleErrorCaseRecipe() {}

  private static final Set<String> KNOWN_FREE_CASES =
      Set.of("Free.Pure", "Pure", "Free.Suspend", "Suspend", "Free.FlatMapped", "FlatMapped");

  private static final Set<String> NEW_CASE_MARKERS = Set.of("HandleError", "Free.HandleError");

  private static final Set<String> AP_CASE_MARKERS = Set.of("Ap", "Free.Ap");

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
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.Switch visitSwitch(J.Switch switch_, ExecutionContext ctx) {
        J.Switch s = super.visitSwitch(switch_, ctx);

        boolean matchesFreeVariants = false;
        boolean hasHandleError = false;
        boolean hasAp = false;

        for (var stmt : s.getCases().getStatements()) {
          String stmtStr = stmt.toString();
          if (KNOWN_FREE_CASES.stream().anyMatch(stmtStr::contains)) {
            matchesFreeVariants = true;
          }
          if (NEW_CASE_MARKERS.stream().anyMatch(stmtStr::contains)) {
            hasHandleError = true;
          }
          if (AP_CASE_MARKERS.stream().anyMatch(stmtStr::contains)) {
            hasAp = true;
          }
        }

        if (matchesFreeVariants && (!hasHandleError || !hasAp)) {
          // Mark the switch with a search result so it appears in recipe reports
          return s.withPrefix(
              s.getPrefix()
                  .withComments(
                      List.of(
                          new TextComment(
                              false,
                              " TODO: Add missing Free.HandleError and/or Free.Ap case(s)",
                              "",
                              Markers.EMPTY))));
        }

        return s;
      }
    };
  }
}
