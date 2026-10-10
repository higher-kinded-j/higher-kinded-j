// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.higherkindedj.openrewrite.NullLiterals.isNullLiteral;
import static org.higherkindedj.openrewrite.NullLiterals.returnsNull;

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
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

/**
 * Recipe that marks the code a null success breaks from 0.5.0, where a {@code Right} or a {@code
 * Success} always holds a value.
 *
 * <h2>Detection Pattern</h2>
 *
 * <ul>
 *   <li>A {@code null} literal passed as the value to {@code Either.right}, {@code Try.success},
 *       {@code Path.right}, {@code Path.success}, {@code Path.vresultRight}, {@code
 *       VResultPath.pure}, {@code TryKindHelper.success}, {@code ErrorContext.success} or {@code
 *       EitherT.right}. These now throw {@code NullPointerException}.
 *   <li>A lambda given to {@code Try.of}, {@code Try.attempt}, {@code Path.tryOf} or {@code
 *       TryKindHelper.tryOf} that returns a {@code null} literal. It now gives a {@code Failure}
 *       rather than a {@code Success}.
 *   <li>An {@code Either}, {@code Try}, {@code EitherT}, {@code EitherPath}, {@code TryPath},
 *       {@code VResultPath} or {@code ErrorContext} whose success type is {@code Void}, which has
 *       no value to hold.
 *   <li>A {@code VTask}, {@code VTaskPath}, {@code IO}, {@code IOPath}, {@code FreePath}, {@code
 *       CompletableFuturePath} or {@code Saga} whose result type is {@code Void}. It still runs,
 *       but {@code runSafe} and the conversions to {@code Try} or {@code Either} now fail on its
 *       null result.
 * </ul>
 *
 * <p>Where the value's type is {@code Void}, or unknown, the mark suggests {@code Unit}; where it
 * is another type, it suggests a {@code Maybe} or an error that says why the value is missing.
 *
 * <h2>Why this is detection-only</h2>
 *
 * <p>The replacement is {@code Unit}, but changing {@code Void} to {@code Unit} changes a method's
 * signature for every caller and implementer, and a lambda can return {@code null} from anywhere in
 * its body. So this recipe marks each site for a human to change, rather than risking a rewrite
 * that compiles and then fails when it runs.
 */
public class DetectNullSuccessValuesRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public DetectNullSuccessValuesRecipe() {}

  private static final List<MethodMatcher> SUCCESS_FACTORIES =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.either.Either right(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trymonad.Try success(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path right(*)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path success(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path vresultRight(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.VResultPath pure(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trymonad.TryKindHelper success(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.context.ErrorContext success(..)"),
          new MethodMatcher("org.higherkindedj.hkt.either_t.EitherT right(..)"));

  private static final List<MethodMatcher> CAPTURING_FACTORIES =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.trymonad.Try of(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trymonad.Try attempt(..)"),
          new MethodMatcher("org.higherkindedj.hkt.effect.Path tryOf(..)"),
          new MethodMatcher("org.higherkindedj.hkt.trymonad.TryKindHelper tryOf(..)"));

  private static final Set<String> SUCCESS_TYPES =
      Set.of(
          "org.higherkindedj.hkt.either.Either",
          "org.higherkindedj.hkt.trymonad.Try",
          "org.higherkindedj.hkt.either_t.EitherT",
          "org.higherkindedj.hkt.effect.EitherPath",
          "org.higherkindedj.hkt.effect.TryPath",
          "org.higherkindedj.hkt.effect.VResultPath",
          "org.higherkindedj.hkt.effect.context.ErrorContext");

  private static final Set<String> LAZY_TYPES =
      Set.of(
          "org.higherkindedj.hkt.vtask.VTask",
          "org.higherkindedj.hkt.effect.VTaskPath",
          "org.higherkindedj.hkt.io.IO",
          "org.higherkindedj.hkt.effect.IOPath",
          "org.higherkindedj.hkt.effect.FreePath",
          "org.higherkindedj.hkt.effect.CompletableFuturePath",
          "org.higherkindedj.hkt.resilience.Saga");

  static final String NULL_VALUE =
      "A success always holds a value: pass Unit.INSTANCE for a step with nothing to return";

  static final String NULL_TYPED_VALUE =
      "A success always holds a value: use a Maybe for a value that may be missing, or an error"
          + " that says why";

  static final String NULL_SUPPLIER =
      "Returns null, which now gives a Failure: return Unit.INSTANCE for a step with nothing to"
          + " return";

  static final String NULL_TYPED_SUPPLIER =
      "Returns null, which now gives a Failure: return a value, or fail with an error that says"
          + " why";

  static final String VOID_TYPE = "Void has no value for a success to hold: use Unit";

  static final String VOID_LAZY_TYPE =
      "Void still runs here, but runSafe and the conversions to Try or Either now fail on its"
          + " null: use Unit, as VTask.exec, Path.vtaskExec and Path.ioRunnable give";

  @Override
  public String getDisplayName() {
    return "Detect null success values";
  }

  @Override
  public String getDescription() {
    return "Marks a null passed to Either.right, Try.success and the Path success factories, a"
        + " Try.of supplier that returns null, and an Either, Try or Path typed with Void. From"
        + " 0.5.0 a Right or Success always holds a value, and Unit replaces Void.";
  }

  @Override
  public Set<String> getTags() {
    return Set.of("higher-kinded-j", "migration", "null");
  }

  @Override
  public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<>() {

      @Override
      public J.MethodInvocation visitMethodInvocation(
          J.MethodInvocation method, ExecutionContext ctx) {
        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
        List<Expression> args = mi.getArguments();
        if (SUCCESS_FACTORIES.stream().anyMatch(m -> m.matches(mi))
            && isNullLiteral(args.getLast())) {
          return SearchResult.found(mi, holdsVoid(mi) ? NULL_VALUE : NULL_TYPED_VALUE);
        }
        if (CAPTURING_FACTORIES.stream().anyMatch(m -> m.matches(mi))
            && args.getFirst() instanceof J.Lambda lambda
            && returnsNull(lambda)) {
          return SearchResult.found(mi, holdsVoid(mi) ? NULL_SUPPLIER : NULL_TYPED_SUPPLIER);
        }
        return mi;
      }

      @Override
      public J.ParameterizedType visitParameterizedType(
          J.ParameterizedType type, ExecutionContext ctx) {
        J.ParameterizedType pt = super.visitParameterizedType(type, ctx);
        JavaType.FullyQualified raw = TypeUtils.asFullyQualified(pt.getType());
        List<Expression> params = pt.getTypeParameters();
        if (raw == null
            || params == null
            || params.isEmpty()
            || !TypeUtils.isOfClassType(params.getLast().getType(), "java.lang.Void")) {
          return pt;
        }
        if (SUCCESS_TYPES.contains(raw.getFullyQualifiedName())) {
          return SearchResult.found(pt, VOID_TYPE);
        }
        if (LAZY_TYPES.contains(raw.getFullyQualifiedName())) {
          return SearchResult.found(pt, VOID_LAZY_TYPE);
        }
        return pt;
      }
    };
  }

  /**
   * Whether the invocation's result holds {@code Void}, or a type the parser could not resolve, so
   * {@code Unit} is the advice that fits.
   */
  private static boolean holdsVoid(J.MethodInvocation mi) {
    JavaType.Method method = mi.getMethodType();
    if (method == null
        || !(method.getReturnType() instanceof JavaType.Parameterized returned)
        || returned.getTypeParameters().isEmpty()) {
      return true;
    }
    JavaType value = returned.getTypeParameters().getLast();
    return TypeUtils.isOfClassType(value, "java.lang.Void") || value instanceof JavaType.Unknown;
  }
}
