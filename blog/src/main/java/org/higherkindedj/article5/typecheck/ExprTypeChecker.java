// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.typecheck;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Conditional;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;

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
 * Applicative) perfect for the job.
 *
 * <p>We use Higher-Kinded-J's {@link ValidatedMonad} with its {@code map2} and {@code map3} methods
 * to combine multiple validations while accumulating all errors. This is the idiomatic way to use
 * Applicative-style error accumulation in Higher-Kinded-J.
 */
public final class ExprTypeChecker {

  /** Semigroup for accumulating type errors. */
  private static final Semigroup<List<TypeError>> ERROR_SEMIGROUP = TypeError.semigroup();

  /** ValidatedMonad instance for applicative-style error accumulation. */
  private static final ValidatedMonad<List<TypeError>> VALIDATED_MONAD =
      ValidatedMonad.instance(ERROR_SEMIGROUP);

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

    // Use Higher-Kinded-J's ValidatedMonad.map2 for applicative-style error accumulation.
    // This is the idiomatic approach: map2 automatically accumulates errors from both
    // operands before attempting to check type compatibility. If either (or both) operands
    // have type errors, all errors are collected. Only when both are Valid do we proceed
    // to check the operator's type constraints.
    //
    // map2 combines two Validated values: if both are Valid, it applies the combining function;
    // if either (or both) are Invalid, it accumulates all errors using the semigroup.
    // We then flatMap to handle the type constraint validation.
    Kind<ValidatedKind.Witness<List<TypeError>>, Validated<List<TypeError>, Type>> combined =
        VALIDATED_MONAD.map2(
            VALIDATED.widen(leftResult),
            VALIDATED.widen(rightResult),
            (lt, rt) -> checkBinaryTypes(op, lt, rt));

    // Flatten the nested Validated: if sub-expression checking succeeded, return the type check
    return VALIDATED.narrow(combined).flatMap(innerResult -> innerResult);
  }

  private static Validated<List<TypeError>, Type> typeCheckConditional(
      Expr cond, Expr then_, Expr else_, TypeEnv env) {
    Validated<List<TypeError>, Type> condResult = typeCheck(cond, env);
    Validated<List<TypeError>, Type> thenResult = typeCheck(then_, env);
    Validated<List<TypeError>, Type> elseResult = typeCheck(else_, env);

    // Use Higher-Kinded-J's ValidatedMonad.map3 for applicative-style error accumulation.
    // This elegantly handles three sub-expressions: if any have errors, all errors are
    // accumulated. Only when all three are Valid do we proceed to check the constraints.
    //
    // This eliminates the need for unsafe casts and manual error collection - map3 does
    // all the heavy lifting through the Applicative abstraction.
    Kind<ValidatedKind.Witness<List<TypeError>>, Validated<List<TypeError>, Type>> combined =
        VALIDATED_MONAD.map3(
            VALIDATED.widen(condResult),
            VALIDATED.widen(thenResult),
            VALIDATED.widen(elseResult),
            ExprTypeChecker::checkConditionalTypes);

    // Flatten: if sub-expression checking succeeded, return the constraint check result
    return VALIDATED.narrow(combined).flatMap(innerResult -> innerResult);
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
