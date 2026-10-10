// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.higherkindedj.openrewrite.NullLiterals.isNullLiteral;
import static org.higherkindedj.openrewrite.NullLiterals.returnsNull;

import java.util.List;
import java.util.Map;
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
 * Recipe that marks the code a null error breaks from 0.5.0, where a {@code Left} always holds an
 * error.
 *
 * <h2>Detection Pattern</h2>
 *
 * <ul>
 *   <li>A {@code null} literal, cast or not, passed as the error to {@code Either.left}, {@code
 *       EitherT.left}, {@code Maybe.toEither}, {@code raiseError} on an {@code Either} or {@code
 *       EitherT} monad, or the {@code getEither}, {@code getValidated}, {@code modifyEither} and
 *       {@code modifyValidated} helpers of {@code LensExtensions} and {@code PrismExtensions}.
 *       These now throw {@code NullPointerException}.
 *   <li>A lambda given as the error function of {@code Either.mapLeft} or {@code Either.bimap} that
 *       returns a {@code null} literal. It now throws, where it built a {@code Left(null)}.
 *   <li>An {@code Either}, {@code EitherT}, {@code EitherMonad} or {@code EitherTMonad} whose error
 *       type is {@code Void}, which has no value for a {@code Left} to hold.
 * </ul>
 *
 * <h2>Why this is detection-only</h2>
 *
 * <p>No one replacement fits every error type: {@code Unit.INSTANCE} fits an error typed {@code
 * Unit}, and anywhere else the fix is an error that says what went wrong. So this recipe marks each
 * site for a human to change.
 */
public class DetectNullErrorValuesRecipe extends Recipe {

  /** Creates a new instance of this recipe. */
  public DetectNullErrorValuesRecipe() {}

  private static final List<MethodMatcher> ERROR_FACTORIES =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.either.Either left(..)"),
          new MethodMatcher("org.higherkindedj.hkt.either_t.EitherT left(..)"),
          new MethodMatcher("org.higherkindedj.hkt.maybe.Maybe toEither(..)", true));

  /** The optics helpers, each taking its error as the second argument. */
  private static final List<MethodMatcher> ERROR_HELPERS =
      List.of(
          new MethodMatcher("org.higherkindedj.optics.extensions.LensExtensions getEither(..)"),
          new MethodMatcher("org.higherkindedj.optics.extensions.LensExtensions getValidated(..)"),
          new MethodMatcher("org.higherkindedj.optics.extensions.PrismExtensions getEither(..)"),
          new MethodMatcher("org.higherkindedj.optics.extensions.PrismExtensions getValidated(..)"),
          new MethodMatcher("org.higherkindedj.optics.extensions.PrismExtensions modifyEither(..)"),
          new MethodMatcher(
              "org.higherkindedj.optics.extensions.PrismExtensions modifyValidated(..)"));

  private static final MethodMatcher RAISE_ERROR =
      new MethodMatcher("org.higherkindedj.hkt.MonadError raiseError(..)", true);

  private static final List<MethodMatcher> ERROR_MAPPERS =
      List.of(
          new MethodMatcher("org.higherkindedj.hkt.either.Either mapLeft(..)"),
          new MethodMatcher("org.higherkindedj.hkt.either.Either bimap(..)"));

  private static final Set<String> EITHER_MONADS =
      Set.of(
          "org.higherkindedj.hkt.either.EitherMonad",
          "org.higherkindedj.hkt.either_t.EitherTMonad");

  /** Each type whose error type argument is {@code Void}, with that argument's position. */
  private static final Map<String, Integer> ERROR_TYPES =
      Map.of(
          "org.higherkindedj.hkt.either.Either", 0,
          "org.higherkindedj.hkt.either.EitherMonad", 0,
          "org.higherkindedj.hkt.either_t.EitherT", 1,
          "org.higherkindedj.hkt.either_t.EitherTMonad", 1);

  private static final Set<String> EITHER_WITNESSES =
      Set.of(
          "org.higherkindedj.hkt.either.EitherKind.Witness",
          "org.higherkindedj.hkt.either_t.EitherTKind.Witness");

  static final String NULL_ERROR =
      "A Left always holds an error: pass one that says what went wrong, or Unit.INSTANCE where"
          + " the error type is Unit";

  static final String NULL_ERROR_FUNCTION =
      "Returns null, which now throws: return an error that says what went wrong, or Unit.INSTANCE"
          + " where the error type is Unit";

  static final String VOID_ERROR_TYPE = "Void has no value for an error to hold: use Unit";

  @Override
  public String getDisplayName() {
    return "Detect null error values";
  }

  @Override
  public String getDescription() {
    return "Marks a null passed to Either.left, EitherT.left, Maybe.toEither, an Either monad's"
        + " raiseError or an optics helper's error, a mapLeft or bimap error lambda that returns"
        + " null, and an Either typed with a Void error. From 0.5.0 a Left always holds an error.";
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
        if (ERROR_FACTORIES.stream().anyMatch(m -> m.matches(mi))
            && isNullLiteral(args.getLast())) {
          return SearchResult.found(mi, NULL_ERROR);
        }
        if (ERROR_HELPERS.stream().anyMatch(m -> m.matches(mi))
            && args.size() > 1
            && isNullLiteral(args.get(1))) {
          return SearchResult.found(mi, NULL_ERROR);
        }
        if (RAISE_ERROR.matches(mi) && isNullLiteral(args.getFirst()) && onAnEitherMonad(mi)) {
          return SearchResult.found(mi, NULL_ERROR);
        }
        if (ERROR_MAPPERS.stream().anyMatch(m -> m.matches(mi))
            && args.getFirst() instanceof J.Lambda lambda
            && returnsNull(lambda)) {
          return SearchResult.found(mi, NULL_ERROR_FUNCTION);
        }
        return mi;
      }

      @Override
      public J.ParameterizedType visitParameterizedType(
          J.ParameterizedType type, ExecutionContext ctx) {
        J.ParameterizedType pt = super.visitParameterizedType(type, ctx);
        JavaType.FullyQualified raw = TypeUtils.asFullyQualified(pt.getType());
        List<Expression> params = pt.getTypeParameters();
        if (raw == null || params == null) {
          return pt;
        }
        Integer error = ERROR_TYPES.get(raw.getFullyQualifiedName());
        if (error != null
            && error < params.size()
            && TypeUtils.isOfClassType(params.get(error).getType(), "java.lang.Void")) {
          return SearchResult.found(pt, VOID_ERROR_TYPE);
        }
        return pt;
      }
    };
  }

  /**
   * Whether {@code raiseError} is called on an {@code EitherMonad} or {@code EitherTMonad}, or a
   * subtype such as {@code EitherSelective}, or on a {@code MonadError} whose witness is an {@code
   * Either} one. Other monads, such as {@code Optional}'s, take a {@code Unit} error and are left
   * alone.
   */
  private static boolean onAnEitherMonad(J.MethodInvocation mi) {
    if (mi.getSelect() == null) {
      return false;
    }
    JavaType receiver = mi.getSelect().getType();
    if (EITHER_MONADS.stream().anyMatch(monad -> TypeUtils.isAssignableTo(monad, receiver))) {
      return true;
    }
    return receiver instanceof JavaType.Parameterized parameterized
        && !parameterized.getTypeParameters().isEmpty()
        && TypeUtils.asFullyQualified(parameterized.getTypeParameters().getFirst())
            instanceof JavaType.FullyQualified witness
        && EITHER_WITNESSES.contains(witness.getFullyQualifiedName().replace('$', '.'));
  }
}
