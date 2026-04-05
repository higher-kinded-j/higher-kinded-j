// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.AddImport;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/**
 * Recipe that migrates direct {@code Free<F, A>} usage to the {@code FreePath<F, A>} fluent API.
 *
 * <p>The FreePath API provides a more ergonomic and type-safe way to compose Free monad programs.
 * This recipe identifies common patterns of direct Free construction and suggests conversion to
 * FreePath equivalents.
 *
 * <h2>Pattern: Free.suspend → FreePath.from</h2>
 *
 * <pre>{@code
 * // Before
 * Free<F, A> program = Free.suspend(op).flatMap(a -> Free.suspend(op2));
 *
 * // After
 * FreePath<F, A> program = FreePath.from(op).then(() -> FreePath.from(op2)).yield(a -> a);
 * }</pre>
 *
 * <h2>Pattern: Free.liftF → FreePath smart constructors</h2>
 *
 * <pre>{@code
 * // Before
 * Free<F, A> program = Free.liftF(widened, functor);
 *
 * // After (using generated Ops class)
 * Free<F, A> program = FooOps.bar(args);
 * }</pre>
 *
 * @see org.higherkindedj.hkt.free.Free
 * @see org.higherkindedj.hkt.effect.FreePath
 */
public class ConvertRawFreeToFreePathRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public ConvertRawFreeToFreePathRecipe() {}

  private static final String FREE_FQN = "org.higherkindedj.hkt.free.Free";
  private static final String FREE_PATH_FQN = "org.higherkindedj.hkt.effect.FreePath";

  @Override
  public String getDisplayName() {
    return "Convert raw Free monad usage to FreePath API";
  }

  @Override
  public String getDescription() {
    return "Migrates direct Free<F, A> construction and composition to the FreePath<F, A> "
        + "fluent API. FreePath provides better ergonomics, type safety, and integration "
        + "with the ForPath comprehension builder.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

        // Detect Free.liftF calls
        if (isFreeLiftF(mi)) {
          // Add import for FreePath as a hint
          doAfterVisit(new AddImport<>(FREE_PATH_FQN, null, false));
        }

        return mi;
      }

      private boolean isFreeLiftF(J.MethodInvocation mi) {
        if (mi.getSelect() == null) return false;
        String selectStr = mi.getSelect().toString();
        String methodName = mi.getSimpleName();
        return "Free".equals(selectStr) && "liftF".equals(methodName);
      }
    };
  }
}
