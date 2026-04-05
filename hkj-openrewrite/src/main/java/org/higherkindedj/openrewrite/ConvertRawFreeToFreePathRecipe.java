// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * Recipe that detects direct {@code Free.liftF()} and {@code Free.suspend()} calls that could be
 * migrated to the {@code FreePath} fluent API or generated {@code *Ops} smart constructors.
 *
 * <p>The FreePath API provides a more ergonomic and type-safe way to compose Free monad programs.
 * This recipe identifies common patterns of direct Free construction and marks them for migration.
 *
 * <h2>Detection Patterns</h2>
 *
 * <ul>
 *   <li>{@code Free.liftF(kindHelper.widen(op), functor)} — should use generated Ops methods
 *   <li>{@code Free.suspend(fa)} — should use FreePath.from() or generated Ops methods
 * </ul>
 *
 * <p>Detected calls are annotated with a TODO comment indicating the migration opportunity.
 *
 * @see org.higherkindedj.hkt.free.Free
 * @see org.higherkindedj.hkt.effect.FreePath
 */
public class ConvertRawFreeToFreePathRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public ConvertRawFreeToFreePathRecipe() {}

  @Override
  public String getDisplayName() {
    return "Convert raw Free monad usage to FreePath API";
  }

  @Override
  public String getDescription() {
    return "Detects direct Free.liftF() and Free.suspend() calls that could be replaced with "
        + "FreePath fluent API or generated *Ops smart constructors. Marks detected usages "
        + "with TODO comments for manual migration.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

        if (isFreeLiftFOrSuspend(mi)) {
          // Mark with a TODO comment for migration
          return mi.withPrefix(
              mi.getPrefix()
                  .withComments(
                      java.util.List.of(
                          new org.openrewrite.java.tree.TextComment(
                              false,
                              " TODO: Consider using generated *Ops methods or FreePath API instead of raw Free",
                              "",
                              org.openrewrite.marker.Markers.EMPTY))));
        }

        return mi;
      }

      private boolean isFreeLiftFOrSuspend(J.MethodInvocation mi) {
        if (mi.getSelect() == null) return false;
        String selectStr = mi.getSelect().toString();
        String methodName = mi.getSimpleName();
        return "Free".equals(selectStr)
            && ("liftF".equals(methodName) || "suspend".equals(methodName));
      }
    };
  }
}
