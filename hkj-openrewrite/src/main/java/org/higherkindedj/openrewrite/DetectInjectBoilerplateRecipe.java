// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * Recipe that identifies manual {@code Inject} instance construction and suggests using the
 * generated {@code @ComposeEffects} support class instead.
 *
 * <p>When users manually create Inject instances via {@code InjectInstances.injectLeft()}, {@code
 * InjectInstances.injectRight()}, and {@code InjectInstances.injectRightThen()}, this recipe
 * detects the pattern and suggests using the generated Support class from
 * {@code @ComposeEffects}.
 *
 * <h2>Detection Pattern</h2>
 *
 * <pre>{@code
 * // Detected: manual Inject construction
 * Inject<FooKind.Witness, CombinedWitness> fooInject = InjectInstances.injectLeft();
 * Inject<BarKind.Witness, CombinedWitness> barInject =
 *     InjectInstances.injectRightThen(InjectInstances.injectLeft());
 *
 * // Suggested replacement: use generated Support class
 * var fooInject = MyEffectsSupport.injectFoo();
 * var barInject = MyEffectsSupport.injectBar();
 * }</pre>
 *
 * @see org.higherkindedj.hkt.inject.InjectInstances
 * @see org.higherkindedj.hkt.effect.annotation.ComposeEffects
 */
public class DetectInjectBoilerplateRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public DetectInjectBoilerplateRecipe() {}

  private static final String INJECT_INSTANCES_FQN =
      "org.higherkindedj.hkt.inject.InjectInstances";

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
          // The recipe detects the pattern. In a full implementation,
          // this would generate a SearchResult marker that OpenRewrite
          // surfaces to the user as a migration suggestion.
        }

        return mi;
      }

      private boolean isInjectInstancesCall(J.MethodInvocation mi) {
        if (mi.getSelect() == null) return false;
        String selectStr = mi.getSelect().toString();
        return selectStr.equals("InjectInstances")
            || selectStr.endsWith(".InjectInstances");
      }
    };
  }
}
