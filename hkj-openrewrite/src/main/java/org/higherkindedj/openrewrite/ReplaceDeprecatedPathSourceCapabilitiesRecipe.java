// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.ChangeFieldName;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;

/**
 * Replaces {@code @PathSource}'s deprecated {@code EFFECTFUL} capability with {@code CHAINABLE},
 * and {@code ACCUMULATING} with {@code RECOVERABLE}.
 *
 * <p>Each deprecated level generates exactly the class its replacement does, so the replacement
 * changes nothing generated. Only the constant's name changes: a reference keeps the qualifier it
 * was written with, whether {@code PathSource.Capability.EFFECTFUL}, a fully qualified name, or a
 * static import. Every reference to the two constants is renamed, not only those in
 * {@code @PathSource}, since the constants mean nothing elsewhere.
 */
public class ReplaceDeprecatedPathSourceCapabilitiesRecipe extends Recipe {

  private static final String CAPABILITY =
      "org.higherkindedj.hkt.effect.annotation.PathSource$Capability";

  /** Creates a new instance of this recipe. */
  public ReplaceDeprecatedPathSourceCapabilitiesRecipe() {}

  @Override
  public String getDisplayName() {
    return "@PathSource EFFECTFUL -> CHAINABLE, ACCUMULATING -> RECOVERABLE";
  }

  @Override
  public String getDescription() {
    return "`PathSource.Capability.EFFECTFUL` and `ACCUMULATING` were deprecated since 0.4.11 and"
        + " scheduled for removal in 0.5.0. `EFFECTFUL` generates exactly what `CHAINABLE` does,"
        + " and `ACCUMULATING` exactly what `RECOVERABLE` does, so replacing them in `@PathSource`"
        + " changes nothing generated. Every reference to the two constants is renamed, so a"
        + " `switch` over `PathSource.Capability` that lists a deprecated level beside its"
        + " replacement is left with a duplicate label to remove.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "deprecation", "migration");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {
      @Override
      public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
        J.CompilationUnit renamed =
            (J.CompilationUnit)
                new ChangeFieldName<ExecutionContext>(CAPABILITY, "EFFECTFUL", "CHAINABLE")
                    .visitNonNull(cu, ctx);
        return (J.CompilationUnit)
            new ChangeFieldName<ExecutionContext>(CAPABILITY, "ACCUMULATING", "RECOVERABLE")
                .visitNonNull(renamed, ctx);
      }
    };
  }
}
