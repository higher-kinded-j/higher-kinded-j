// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.List;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * Marks each call of, and each method reference to, a deprecated {@code Try.foldFailureFirst} or
 * {@code TryPath.foldFailureFirst}.
 *
 * <p>{@code RenameTryFoldFailureFirst} runs only in a file where this finds one. {@code
 * foldFailureFirst} is deprecated from 0.6.0, where {@code fold} takes the failure function first,
 * so the rename is safe there. On 0.4.x it is not deprecated, and {@code fold} takes the success
 * function first, so this finds nothing and the rename changes nothing. OpenRewrite's {@code
 * FindDeprecatedMethods} marks calls only, so a file whose only use is a method reference, such as
 * {@code t::foldFailureFirst}, would be left out.
 */
public class FindDeprecatedFoldFailureFirstRecipe extends Recipe {

  private static final List<MethodMatcher> FOLD_FAILURE_FIRST =
      List.of(
          new MethodMatcher(
              "org.higherkindedj.hkt.trymonad.Try foldFailureFirst(java.util.function.Function,"
                  + " java.util.function.Function)",
              true),
          new MethodMatcher(
              "org.higherkindedj.hkt.effect.TryPath foldFailureFirst(java.util.function.Function,"
                  + " java.util.function.Function)",
              true));

  /** Creates a new instance of this recipe. */
  public FindDeprecatedFoldFailureFirstRecipe() {}

  @Override
  public String getDisplayName() {
    return "Find deprecated Try.foldFailureFirst and TryPath.foldFailureFirst";
  }

  @Override
  public String getDescription() {
    return "Marks each call of, and method reference to, Try.foldFailureFirst or"
        + " TryPath.foldFailureFirst where it is deprecated, as it is from 0.6.0.";
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation call = super.visitMethodInvocation(method, ctx);
        return isDeprecatedFoldFailureFirst(call.getMethodType()) ? SearchResult.found(call) : call;
      }

      @Override
      public J.MemberReference visitMemberReference(
          J.MemberReference memberRef, ExecutionContext ctx) {
        J.MemberReference ref = super.visitMemberReference(memberRef, ctx);
        return isDeprecatedFoldFailureFirst(ref.getMethodType()) ? SearchResult.found(ref) : ref;
      }
    };
  }

  private static boolean isDeprecatedFoldFailureFirst(JavaType.Method method) {
    return method != null
        && FOLD_FAILURE_FIRST.stream().anyMatch(matcher -> matcher.matches(method))
        && method.getAnnotations().stream()
            .anyMatch(annotation -> TypeUtils.isOfClassType(annotation, "java.lang.Deprecated"));
  }
}
