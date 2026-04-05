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
 * Recipe that identifies manual {@code Inject} instance construction and suggests using the
 * generated {@code @ComposeEffects} support class instead.
 *
 * <p>When users manually create Inject instances via {@code InjectInstances.injectLeft()}, {@code
 * InjectInstances.injectRight()}, and {@code InjectInstances.injectRightThen()}, this recipe
 * detects the pattern and marks it with a TODO comment suggesting migration to the generated
 * Support class from {@code @ComposeEffects}.
 *
 * <h2>Detection Pattern</h2>
 *
 * <p>Any call to {@code InjectInstances.injectLeft()}, {@code InjectInstances.injectRight()}, or
 * {@code InjectInstances.injectRightThen()} is flagged.
 *
 * @see org.higherkindedj.hkt.inject.InjectInstances
 * @see org.higherkindedj.hkt.effect.annotation.ComposeEffects
 */
public class DetectInjectBoilerplateRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public DetectInjectBoilerplateRecipe() {}

  private static final Set<String> INJECT_METHODS =
      Set.of("injectLeft", "injectRight", "injectRightThen");

  @Override
  public String getDisplayName() {
    return "Detect manual Inject boilerplate";
  }

  @Override
  public String getDescription() {
    return "Identifies manual Inject instance construction via InjectInstances and suggests "
        + "using @ComposeEffects with the generated Support class instead. This reduces "
        + "boilerplate and ensures correct nesting of EitherF compositions.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

        if (isInjectInstancesCall(mi)) {
          return mi.withPrefix(
              mi.getPrefix()
                  .withComments(
                      List.of(
                          new TextComment(
                              false,
                              " TODO: Consider using @ComposeEffects generated Support class instead of manual InjectInstances",
                              "",
                              Markers.EMPTY))));
        }

        return mi;
      }

      private boolean isInjectInstancesCall(J.MethodInvocation mi) {
        if (mi.getSelect() == null) return false;
        String selectStr = mi.getSelect().toString();
        boolean isOnInjectInstances =
            selectStr.equals("InjectInstances") || selectStr.endsWith(".InjectInstances");
        return isOnInjectInstances && INJECT_METHODS.contains(mi.getSimpleName());
      }
    };
  }
}
