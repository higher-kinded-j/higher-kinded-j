// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.demo;

import java.util.List;
import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article4.ast.Expr.Variable;
import org.higherkindedj.article5.typecheck.Type;
import org.higherkindedj.article5.typecheck.TypeEnv;
import org.higherkindedj.article5.typecheck.TypeError;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.ValidationPath;

/**
 * Demonstrates the Effect Path API introduced in Article 5.
 *
 * <p>The Effect Path API provides a fluent interface for computations that might fail,
 * accumulate errors, or require deferred execution. This demo showcases:
 *
 * <ul>
 *   <li>{@link MaybePath} - Optional values that might be absent
 *   <li>{@link EitherPath} - Fail-fast error handling with typed errors
 *   <li>{@link TryPath} - Exception handling converted to values
 *   <li>{@link ValidationPath} - Error accumulation using Semigroup
 * </ul>
 *
 * <p>The Effect Path types follow the "railway" metaphor where values travel along
 * success or failure tracks.
 */
public final class EffectPathDemo {

  public static void main(String[] args) {
    System.out.println("Effect Path API Demo");
    System.out.println("====================");
    System.out.println();

    demoMaybePath();
    demoEitherPath();
    demoTryPath();
    demoValidationPath();
    demoTypeCheckerWithValidationPath();
  }

  private static void demoMaybePath() {
    System.out.println("1. MaybePath: Optional Values");
    System.out.println("   ---------------------------");

    // Creating MaybePaths
    MaybePath<String> present = Path.just("Hello");
    MaybePath<String> absent = Path.nothing();

    // Chaining with map and filter
    MaybePath<String> result = present
        .map(String::toUpperCase)
        .filter(s -> s.length() > 3)
        .map(s -> s + "!");

    System.out.println("   Path.just(\"Hello\").map(toUpperCase).filter(len>3).map(+\"!\")");
    System.out.println("   Result: " + result.getOrElse("(empty)"));

    // Chaining with via
    MaybePath<Integer> parsed = Path.just("42")
        .via(s -> {
          try {
            return Path.just(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            return Path.nothing();
          }
        });
    System.out.println("   Path.just(\"42\").via(parseInt): " + parsed.getOrElse(-1));

    // Converting to other Effect Paths
    EitherPath<String, String> eitherFromMaybe = absent.toEitherPath("Value was missing");
    System.out.println("   absent.toEitherPath(\"Value was missing\"): "
        + eitherFromMaybe.run().fold(err -> "Left(" + err + ")", val -> "Right(" + val + ")"));

    System.out.println();
  }

  private static void demoEitherPath() {
    System.out.println("2. EitherPath: Fail-Fast Error Handling");
    System.out.println("   -------------------------------------");

    // Creating EitherPaths
    EitherPath<String, Integer> success = Path.right(42);
    EitherPath<String, Integer> failure = Path.left("Division by zero");

    // Chaining with map and via
    EitherPath<String, Integer> result = success
        .map(n -> n * 2)
        .via(n -> n > 0 ? Path.right(n) : Path.left("Must be positive"));

    System.out.println("   Path.right(42).map(*2).via(checkPositive)");
    System.out.println("   Result: " + result.run().fold(
        err -> "Left(" + err + ")",
        val -> "Right(" + val + ")"));

    // Error recovery
    EitherPath<String, Integer> recovered = failure.recover(err -> -1);
    System.out.println("   failure.recover(err -> -1): " + recovered.getOrElse(0));

    // Error transformation
    EitherPath<Integer, Integer> errorMapped = failure.mapError(String::length);
    System.out.println("   failure.mapError(String::length): "
        + errorMapped.run().fold(
            code -> "Left(" + code + ")",
            val -> "Right(" + val + ")"));

    System.out.println();
  }

  private static void demoTryPath() {
    System.out.println("3. TryPath: Exception Handling");
    System.out.println("   ----------------------------");

    // Wrapping throwing code
    TryPath<Integer> parsed = Path.tryOf(() -> Integer.parseInt("42"));
    TryPath<Integer> parseFailed = Path.tryOf(() -> Integer.parseInt("not a number"));

    System.out.println("   Path.tryOf(() -> parseInt(\"42\")): " + parsed.getOrElse(-1));
    System.out.println("   Path.tryOf(() -> parseInt(\"not a number\")): " + parseFailed.getOrElse(-1));

    // Chaining with recovery
    TryPath<Integer> withRecovery = parseFailed.recover(ex -> {
      System.out.println("   (Recovered from: " + ex.getMessage() + ")");
      return 0;
    });
    System.out.println("   After recovery: " + withRecovery.getOrElse(-1));

    System.out.println();
  }

  private static void demoValidationPath() {
    System.out.println("4. ValidationPath: Error Accumulation");
    System.out.println("   -----------------------------------");

    // Simple validators
    ValidationPath<List<String>, String> validName =
        Path.valid("Alice", Semigroups.list());
    ValidationPath<List<String>, String> invalidName =
        Path.invalid(List.of("Name is required"), Semigroups.list());
    ValidationPath<List<String>, Integer> invalidAge =
        Path.invalid(List.of("Age must be positive"), Semigroups.list());

    // Short-circuit composition (via)
    System.out.println("   Short-circuit composition (via):");
    ValidationPath<List<String>, String> sequential = invalidName
        .via(n -> Path.valid(n.toUpperCase(), Semigroups.list()));
    System.out.println("   invalidName.via(toUpperCase): "
        + sequential.run().fold(
            errs -> "Invalid(" + errs + ")",
            val -> "Valid(" + val + ")"));

    // Accumulating composition (zipWithAccum)
    System.out.println("   Accumulating composition (zipWithAccum):");
    ValidationPath<List<String>, String> accumulated = invalidName
        .zipWithAccum(invalidAge, (name, age) -> name + " is " + age);
    System.out.println("   invalidName.zipWithAccum(invalidAge, combine): "
        + accumulated.run().fold(
            errs -> "Invalid(" + errs + ")",
            val -> "Valid(" + val + ")"));

    // Valid accumulation
    ValidationPath<List<String>, Integer> validAge =
        Path.valid(30, Semigroups.list());
    ValidationPath<List<String>, String> bothValid = validName
        .zipWithAccum(validAge, (name, age) -> name + " is " + age);
    System.out.println("   validName.zipWithAccum(validAge, combine): "
        + bothValid.run().fold(
            errs -> "Invalid(" + errs + ")",
            val -> "Valid(" + val + ")"));

    System.out.println();
  }

  private static void demoTypeCheckerWithValidationPath() {
    System.out.println("5. Type Checking with ValidationPath");
    System.out.println("   ----------------------------------");

    // Expression with multiple type errors: (1 + true) * (false && 42)
    Expr leftError = new Binary(new Literal(1), BinaryOp.ADD, new Literal(true));
    Expr rightError = new Binary(new Literal(false), BinaryOp.AND, new Literal(42));
    Expr expr = new Binary(leftError, BinaryOp.MUL, rightError);

    System.out.println("   Expression: " + expr.format());
    System.out.println("   Using ValidationPath for error accumulation:");

    // Type check using ValidationPath
    ValidationPath<List<TypeError>, Type> result = typeCheckWithPath(expr, TypeEnv.empty());

    result.run().fold(
        errors -> {
          System.out.println("   Result: Invalid with " + errors.size() + " error(s):");
          for (TypeError error : errors) {
            System.out.println("     - " + error.message());
          }
          return null;
        },
        type -> {
          System.out.println("   Result: Valid, type = " + type);
          return null;
        }
    );

    System.out.println();
  }

  /**
   * Type checks an expression using ValidationPath for error accumulation.
   * This demonstrates using the Effect Path API directly.
   */
  private static ValidationPath<List<TypeError>, Type> typeCheckWithPath(Expr expr, TypeEnv env) {
    return switch (expr) {
      case Literal(var value) -> typeCheckLiteralWithPath(value);
      case Variable(var name) -> typeCheckVariableWithPath(name, env);
      case Binary(var left, var op, var right) -> typeCheckBinaryWithPath(left, op, right, env);
      case Expr.Conditional(var cond, var then_, var else_) ->
          typeCheckConditionalWithPath(cond, then_, else_, env);
    };
  }

  private static ValidationPath<List<TypeError>, Type> typeCheckLiteralWithPath(Object value) {
    return switch (value) {
      case Integer _ -> Path.valid(Type.INT, Semigroups.list());
      case Boolean _ -> Path.valid(Type.BOOL, Semigroups.list());
      case String _ -> Path.valid(Type.STRING, Semigroups.list());
      default -> Path.invalid(
          List.of(new TypeError("Unknown literal type: " + value.getClass().getSimpleName())),
          Semigroups.list());
    };
  }

  private static ValidationPath<List<TypeError>, Type> typeCheckVariableWithPath(
      String name, TypeEnv env) {
    return env.lookup(name)
        .map(type -> Path.<List<TypeError>, Type>valid(type, Semigroups.list()))
        .orElseGet(() -> Path.invalid(
            List.of(new TypeError("Undefined variable: " + name)),
            Semigroups.list()));
  }

  private static ValidationPath<List<TypeError>, Type> typeCheckBinaryWithPath(
      Expr left, BinaryOp op, Expr right, TypeEnv env) {
    // Use zipWithAccum to accumulate errors from both operands
    return typeCheckWithPath(left, env)
        .zipWithAccum(
            typeCheckWithPath(right, env),
            (lt, rt) -> checkBinaryTypesWithPath(op, lt, rt))
        .via(result -> result);  // Flatten nested validation
  }

  private static ValidationPath<List<TypeError>, Type> typeCheckConditionalWithPath(
      Expr cond, Expr then_, Expr else_, TypeEnv env) {
    // Use zipWith3Accum to accumulate errors from all three sub-expressions
    return typeCheckWithPath(cond, env)
        .zipWith3Accum(
            typeCheckWithPath(then_, env),
            typeCheckWithPath(else_, env),
            EffectPathDemo::checkConditionalTypesWithPath)
        .via(result -> result);
  }

  private static ValidationPath<List<TypeError>, Type> checkBinaryTypesWithPath(
      BinaryOp op, Type left, Type right) {
    return switch (op) {
      case ADD, SUB, MUL, DIV -> {
        if (left == Type.INT && right == Type.INT) {
          yield Path.valid(Type.INT, Semigroups.list());
        }
        yield Path.invalid(List.of(new TypeError(
            "Arithmetic operator '%s' requires INT operands, got %s and %s"
                .formatted(op.symbol(), left, right))),
            Semigroups.list());
      }
      case AND, OR -> {
        if (left == Type.BOOL && right == Type.BOOL) {
          yield Path.valid(Type.BOOL, Semigroups.list());
        }
        yield Path.invalid(List.of(new TypeError(
            "Logical operator '%s' requires BOOL operands, got %s and %s"
                .formatted(op.symbol(), left, right))),
            Semigroups.list());
      }
      case EQ, NE -> Path.valid(Type.BOOL, Semigroups.list());
      case LT, LE, GT, GE -> {
        if (left == Type.INT && right == Type.INT) {
          yield Path.valid(Type.BOOL, Semigroups.list());
        }
        yield Path.invalid(List.of(new TypeError(
            "Comparison operator '%s' requires INT operands, got %s and %s"
                .formatted(op.symbol(), left, right))),
            Semigroups.list());
      }
    };
  }

  private static ValidationPath<List<TypeError>, Type> checkConditionalTypesWithPath(
      Type cond, Type then_, Type else_) {
    ValidationPath<List<TypeError>, Type> condCheck = cond == Type.BOOL
        ? Path.valid(cond, Semigroups.list())
        : Path.invalid(
            List.of(new TypeError("Condition must be BOOL, got " + cond)),
            Semigroups.list());

    ValidationPath<List<TypeError>, Type> branchCheck = then_ == else_
        ? Path.valid(then_, Semigroups.list())
        : Path.invalid(
            List.of(new TypeError("Branches must have same type, got %s and %s"
                .formatted(then_, else_))),
            Semigroups.list());

    // Accumulate both checks
    return condCheck.zipWithAccum(branchCheck, (_, t) -> t);
  }
}
