// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.typecheck;

import java.util.Arrays;
import java.util.List;
import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Conditional;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.validated.Invalid;
import org.higherkindedj.hkt.validated.Valid;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Type checker for the expression language using Higher-Kinded-J's Validated for error
 * accumulation.
 *
 * <p>This type checker uses {@link Validated} from Higher-Kinded-J to accumulate ALL type errors in
 * a single pass, rather than failing on the first error. This provides a better user experience:
 * users see all errors at once instead of fixing them one at a time.
 *
 * <p>Key insight: type checking sub-expressions is INDEPENDENT. The type of the left operand
 * doesn't affect whether we should check the right operand. This makes {@code Validated} (which is
 * Applicative) perfect for the job. We use pattern matching on Valid/Invalid for clean error
 * accumulation.
 */
public final class ExprTypeChecker {

  /** Semigroup for accumulating type errors. */
  private static final Semigroup<List<TypeError>> ERROR_SEMIGROUP = TypeError.semigroup();

  private ExprTypeChecker() {}

  /**
   * Type check an expression in the given type environment.
   *
   * <p>Uses Higher-Kinded-J's Validated to accumulate all type errors in a single pass.
   *
   * @param expr the expression to type check
   * @param env the type environment
   * @return Valid(type) if well-typed, Invalid(errors) if there are type errors
   */
  public static Validated<List<TypeError>, Type> typeCheck(Expr expr, TypeEnv env) {
    return switch (expr) {
      case Literal(var value) -> typeCheckLiteral(value);
      case Variable(var name) -> typeCheckVariable(name, env);
      case Binary(var left, var op, var right) -> typeCheckBinary(left, op, right, env);
      case Conditional(var cond, var then_, var else_) ->
          typeCheckConditional(cond, then_, else_, env);
    };
  }

  private static Validated<List<TypeError>, Type> typeCheckLiteral(Object value) {
    return switch (value) {
      case Integer _ -> Validated.valid(Type.INT);
      case Boolean _ -> Validated.valid(Type.BOOL);
      case String _ -> Validated.valid(Type.STRING);
      default ->
          Validated.invalid(
              TypeError.single(
                  "Unknown literal type: %s".formatted(value.getClass().getSimpleName())));
    };
  }

  private static Validated<List<TypeError>, Type> typeCheckVariable(String name, TypeEnv env) {
    return env.lookup(name)
        .map(Validated::<List<TypeError>, Type>valid)
        .orElseGet(() -> Validated.invalid(TypeError.single("Undefined variable: " + name)));
  }

  private static Validated<List<TypeError>, Type> typeCheckBinary(
      Expr left, BinaryOp op, Expr right, TypeEnv env) {
    Validated<List<TypeError>, Type> leftResult = typeCheck(left, env);
    Validated<List<TypeError>, Type> rightResult = typeCheck(right, env);

    // Use Java 21+ pattern matching on Validated's Valid/Invalid subtypes
    // This accumulates errors from both sub-expressions before checking type compatibility
    return switch (leftResult) {
      case Valid(var lt) ->
          switch (rightResult) {
            case Valid(var rt) -> checkBinaryTypes(op, lt, rt);
            case Invalid(var errors) -> Validated.invalid(errors);
          };
      case Invalid(var leftErrors) ->
          switch (rightResult) {
            case Valid(_) -> Validated.invalid(leftErrors);
            case Invalid(var rightErrors) ->
                Validated.invalid(ERROR_SEMIGROUP.combine(leftErrors, rightErrors));
          };
    };
  }

  private static Validated<List<TypeError>, Type> typeCheckConditional(
      Expr cond, Expr then_, Expr else_, TypeEnv env) {
    Validated<List<TypeError>, Type> condResult = typeCheck(cond, env);
    Validated<List<TypeError>, Type> thenResult = typeCheck(then_, env);
    Validated<List<TypeError>, Type> elseResult = typeCheck(else_, env);

    // Accumulate all sub-expression errors using pattern matching
    List<TypeError> subExprErrors = collectErrors(condResult, thenResult, elseResult);

    if (!subExprErrors.isEmpty()) {
      return Validated.invalid(subExprErrors);
    }

    // All sub-expressions valid - extract types and check constraints
    Type ct = ((Valid<List<TypeError>, Type>) condResult).value();
    Type tt = ((Valid<List<TypeError>, Type>) thenResult).value();
    Type et = ((Valid<List<TypeError>, Type>) elseResult).value();

    return checkConditionalTypes(ct, tt, et);
  }

  @SafeVarargs
  private static List<TypeError> collectErrors(Validated<List<TypeError>, Type>... results) {
    return Arrays.stream(results)
        .filter(Validated::isInvalid)
        .map(Validated::getError)
        .reduce(List.of(), ERROR_SEMIGROUP::combine);
  }

  private static Validated<List<TypeError>, Type> checkBinaryTypes(
      BinaryOp op, Type left, Type right) {
    return switch (op) {
      case ADD, SUB, MUL, DIV ->
          (left == Type.INT && right == Type.INT)
              ? Validated.valid(Type.INT)
              : Validated.invalid(
                  TypeError.single(
                      "Arithmetic operator '%s' requires INT operands, got %s and %s"
                          .formatted(op.symbol(), left, right)));
      case AND, OR ->
          (left == Type.BOOL && right == Type.BOOL)
              ? Validated.valid(Type.BOOL)
              : Validated.invalid(
                  TypeError.single(
                      "Logical operator '%s' requires BOOL operands, got %s and %s"
                          .formatted(op.symbol(), left, right)));
      case EQ, NE ->
          (left == right)
              ? Validated.valid(Type.BOOL)
              : Validated.invalid(
                  TypeError.single(
                      "Equality operator '%s' requires matching types, got %s and %s"
                          .formatted(op.symbol(), left, right)));
      case LT, LE, GT, GE ->
          (left == Type.INT && right == Type.INT)
              ? Validated.valid(Type.BOOL)
              : Validated.invalid(
                  TypeError.single(
                      "Comparison operator '%s' requires INT operands, got %s and %s"
                          .formatted(op.symbol(), left, right)));
    };
  }

  private static Validated<List<TypeError>, Type> checkConditionalTypes(
      Type cond, Type then_, Type else_) {
    List<TypeError> errors = List.of();

    if (cond != Type.BOOL) {
      errors =
          ERROR_SEMIGROUP.combine(
              errors,
              TypeError.single("Conditional requires BOOL condition, got %s".formatted(cond)));
    }

    if (then_ != else_) {
      errors =
          ERROR_SEMIGROUP.combine(
              errors,
              TypeError.single(
                  "Conditional branches must have same type, got %s and %s"
                      .formatted(then_, else_)));
    }

    return errors.isEmpty() ? Validated.valid(then_) : Validated.invalid(errors);
  }
}
