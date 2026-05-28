// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import java.util.List;
import java.util.Set;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

/**
 * Rewrites call sites of the deprecated success-first {@code Try.fold(successMapper,
 * failureMapper)} and {@code TryPath.fold(successMapper, failureMapper)} methods to the error-first
 * {@code foldFailureFirst(failureMapper, successMapper)}.
 *
 * <p>This is not a pure rename: the argument order is also swapped to match the error-first
 * convention used by {@code Either.fold}, {@code Validated.fold}, {@code EitherF.fold}, {@code
 * EitherPath.fold}, and {@code ValidationPath.fold}. The legacy method is
 * {@code @Deprecated(forRemoval = true, since = "0.4.6")} and is removed in 0.5.0; the canonical
 * name {@code fold} is planned to be reintroduced with the error-first argument order in 0.6.0. See
 * <a href="https://github.com/higher-kinded-j/higher-kinded-j/issues/452">#452</a>.
 *
 * <h2>Why a custom recipe (rather than {@code ChangeMethodName})</h2>
 *
 * <p>The off-the-shelf {@code org.openrewrite.java.ChangeMethodName} cannot swap arguments. A naked
 * rename would change {@code .fold(s, f)} to {@code .foldFailureFirst(s, f)} which compiles but
 * silently inverts behaviour (the rebound success and failure mappers would land in the wrong
 * branches), defeating the entire point of the rename. This recipe rewrites the name and reorders
 * the two argument positions atomically.
 *
 * <h2>Match precision</h2>
 *
 * <p>The {@link MethodMatcher} patterns are pinned to the fully-qualified types {@code
 * org.higherkindedj.hkt.trymonad.Try} and {@code org.higherkindedj.hkt.effect.TryPath}, so
 * unrelated {@code .fold(...)} call sites on {@code Either}, {@code Validated}, {@code EitherPath},
 * {@code ValidationPath}, or any user-defined type with a {@code fold} method are left untouched.
 */
public class SwapTryFoldToFoldFailureFirstRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public SwapTryFoldToFoldFailureFirstRecipe() {}

  // Two arguments only: matches the deprecated Function/Function overload, not the
  // unrelated `match(Consumer, Consumer)` variant on Try.
  private static final MethodMatcher TRY_FOLD =
      new MethodMatcher(
          "org.higherkindedj.hkt.trymonad.Try fold(java.util.function.Function,"
              + " java.util.function.Function)");
  private static final MethodMatcher TRY_PATH_FOLD =
      new MethodMatcher(
          "org.higherkindedj.hkt.effect.TryPath fold(java.util.function.Function,"
              + " java.util.function.Function)");

  @Override
  public String getDisplayName() {
    return "Try.fold/TryPath.fold -> foldFailureFirst (argument order swapped)";
  }

  @Override
  public String getDescription() {
    return "Rewrites deprecated success-first `Try.fold(successMapper, failureMapper)` and "
        + "`TryPath.fold(successMapper, failureMapper)` call sites to the error-first "
        + "`foldFailureFirst(failureMapper, successMapper)`. Both methods are deprecated for "
        + "removal in 0.5.0; the recipe renames the method and swaps the two arguments "
        + "atomically so behaviour is preserved. See "
        + "https://github.com/higher-kinded-j/higher-kinded-j/issues/452.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "deprecation", "migration");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

        if (!TRY_FOLD.matches(mi) && !TRY_PATH_FOLD.matches(mi)) {
          return mi;
        }

        List<Expression> args = mi.getArguments();
        // The matcher already constrains arity to two; this is a belt-and-braces guard
        // for the synthetic empty-marker argument list that some malformed ASTs carry.
        if (args.size() != 2) {
          return mi;
        }

        Expression originalSuccessMapper = args.get(0);
        Expression originalFailureMapper = args.get(1);

        // Each argument carries a leading `Space` prefix that holds both the
        // indentation whitespace and any comments attached to the argument. A
        // naive swap of the full prefix would also transfer the comments into
        // the wrong slots (a `/* success */` comment in front of arg 0 would
        // end up describing the failure mapper after the swap). Swap only the
        // whitespace portion so each argument keeps its own comments while the
        // indentation matches its new position.
        Expression newFirst =
            originalFailureMapper.withPrefix(
                originalFailureMapper
                    .getPrefix()
                    .withWhitespace(originalSuccessMapper.getPrefix().getWhitespace()));
        Expression newSecond =
            originalSuccessMapper.withPrefix(
                originalSuccessMapper
                    .getPrefix()
                    .withWhitespace(originalFailureMapper.getPrefix().getWhitespace()));

        J.Identifier renamed = mi.getName().withSimpleName("foldFailureFirst");

        // Update the method type so downstream tooling that re-attributes the rewritten
        // tree (and the RewriteTest harness re-parsing the result) sees the call as
        // bound to the new method with the new parameter order.
        JavaType.Method updatedType = renameAndSwapParameters(mi.getMethodType());

        return mi.withName(renamed.withType(updatedType))
            .withMethodType(updatedType)
            .withArguments(List.of(newFirst, newSecond));
      }

      private JavaType.Method renameAndSwapParameters(JavaType.Method original) {
        if (original == null) {
          return null;
        }
        JavaType.Method renamed = original.withName("foldFailureFirst");
        List<JavaType> params = renamed.getParameterTypes();
        if (params.size() != 2) {
          return renamed;
        }
        List<JavaType> swapped = List.of(params.get(1), params.get(0));
        List<String> names = renamed.getParameterNames();
        List<String> swappedNames =
            (names != null && names.size() == 2) ? List.of(names.get(1), names.get(0)) : names;
        return renamed.withParameterTypes(swapped).withParameterNames(swappedNames);
      }
    };
  }
}
