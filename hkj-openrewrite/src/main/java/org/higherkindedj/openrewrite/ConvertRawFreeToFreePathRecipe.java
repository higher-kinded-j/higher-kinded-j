// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.marker.SearchResult;

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
 * <p>Detected calls are tagged with an OpenRewrite {@link org.openrewrite.marker.SearchResult}
 * marker indicating the migration opportunity.
 *
 * <h2>Why this is detection-only</h2>
 *
 * <p>The replacement target is user-specific generated code: the {@code *Ops} smart constructors
 * are generated per effect algebra and named after it, so the correct replacement cannot be derived
 * generically. {@code Free.suspend} to {@code FreePath.from} also changes the static type, which
 * would not compile without migrating the surrounding composition. An automated rewrite would
 * therefore produce broken code; this recipe deliberately flags call sites for a human to migrate
 * instead.
 *
 * @see "org.higherkindedj.hkt.free.Free"
 * @see "org.higherkindedj.hkt.effect.FreePath"
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
        + "FreePath fluent API or generated *Ops smart constructors. Tags detected usages "
        + "with a search-result marker for manual migration.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "effects", "free", "migration");
  }

  private static final MethodMatcher FREE_LIFT_F =
      new MethodMatcher("org.higherkindedj.hkt.free.Free liftF(..)");
  private static final MethodMatcher FREE_SUSPEND =
      new MethodMatcher("org.higherkindedj.hkt.free.Free suspend(..)");

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

        if (FREE_LIFT_F.matches(mi) || FREE_SUSPEND.matches(mi)) {
          return SearchResult.found(
              mi, "Consider generated *Ops methods or the FreePath API instead of raw Free");
        }

        return mi;
      }
    };
  }
}
