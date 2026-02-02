// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.demo;

import java.time.Duration;
import java.util.List;
import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article5.typecheck.Type;
import org.higherkindedj.article5.typecheck.TypeEnv;
import org.higherkindedj.article5.typecheck.TypeError;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * Demonstrates parallel type checking using VTask and Scope.
 *
 * <p>This demo shows how to combine the error accumulation of ValidationPath with the parallel
 * execution of VTask's structured concurrency. When type checking independent sub-expressions, we
 * can check them concurrently while still accumulating all errors.
 *
 * <p>Key patterns demonstrated:
 *
 * <ul>
 *   <li>Using {@link Scope#accumulating} for concurrent validation
 *   <li>Converting between ValidationPath and VTask results
 *   <li>Comparing sequential vs parallel type checking
 * </ul>
 */
public final class ParallelTypeCheckerDemo {

  public static void main(String[] args) {
    System.out.println("Parallel Type Checking Demo");
    System.out.println("===========================");
    System.out.println();

    demoSequentialVsParallel();
    demoParallelSubExpressionChecking();
  }

  private static void demoSequentialVsParallel() {
    System.out.println("1. Sequential vs Parallel Type Checking");
    System.out.println("   ------------------------------------");

    // Create multiple independent expressions to check
    List<Expr> expressions = List.of(
        new Binary(new Literal(1), BinaryOp.ADD, new Literal(2)),       // Valid: INT + INT
        new Binary(new Literal(true), BinaryOp.AND, new Literal(false)), // Valid: BOOL && BOOL
        new Binary(new Literal(1), BinaryOp.ADD, new Literal(true)),    // Invalid: INT + BOOL
        new Binary(new Literal("a"), BinaryOp.MUL, new Literal(2))      // Invalid: STRING * INT
    );

    TypeEnv env = TypeEnv.empty();

    // Sequential checking
    System.out.println("   Sequential (one at a time):");
    long start = System.currentTimeMillis();
    for (Expr expr : expressions) {
      ValidationPath<List<TypeError>, Type> result = typeCheckWithDelay(expr, env, 50);
      String status = result.run().fold(
          errors -> "Invalid: " + errors.getFirst().message(),
          type -> "Valid: " + type
      );
      System.out.println("     " + expr.format() + " -> " + status);
    }
    long sequentialTime = System.currentTimeMillis() - start;
    System.out.println("   Sequential time: " + sequentialTime + "ms");

    // Parallel checking using Scope
    System.out.println();
    System.out.println("   Parallel (concurrent):");
    start = System.currentTimeMillis();

    List<VTask<Validated<List<TypeError>, Type>>> tasks = expressions.stream()
        .map(expr -> VTask.of(() -> typeCheckWithDelay(expr, env, 50).run()))
        .toList();

    VTask<List<Validated<List<TypeError>, Type>>> allResults = Scope
        .<Validated<List<TypeError>, Type>>allSucceed()
        .forkAll(tasks)
        .join();

    List<Validated<List<TypeError>, Type>> results = allResults.run();
    for (int i = 0; i < expressions.size(); i++) {
      Expr expr = expressions.get(i);
      String status = results.get(i).fold(
          errors -> "Invalid: " + errors.getFirst().message(),
          type -> "Valid: " + type
      );
      System.out.println("     " + expr.format() + " -> " + status);
    }
    long parallelTime = System.currentTimeMillis() - start;
    System.out.println("   Parallel time: " + parallelTime + "ms");
    System.out.println("   Speedup: " + String.format("%.1fx", (double) sequentialTime / parallelTime));

    System.out.println();
  }

  private static void demoParallelSubExpressionChecking() {
    System.out.println("2. Parallel Sub-Expression Checking with Error Accumulation");
    System.out.println("   --------------------------------------------------------");

    // Complex expression with multiple type errors in independent sub-expressions
    // (1 + true) * (false && 42) - two independent errors
    Expr leftError = new Binary(new Literal(1), BinaryOp.ADD, new Literal(true));
    Expr rightError = new Binary(new Literal(false), BinaryOp.AND, new Literal(42));
    Expr complexExpr = new Binary(leftError, BinaryOp.MUL, rightError);

    System.out.println("   Expression: " + complexExpr.format());
    System.out.println();

    TypeEnv env = TypeEnv.empty();

    // Check sub-expressions in parallel, accumulating errors
    VTask<Validated<List<TypeError>, Type>> parallelCheck = Scope
        .<Validated<List<TypeError>, Type>>allSucceed()
        .fork(VTask.of(() -> typeCheckWithDelay(leftError, env, 50).run()))
        .fork(VTask.of(() -> typeCheckWithDelay(rightError, env, 50).run()))
        .timeout(Duration.ofSeconds(5))
        .join()
        .map(results -> {
          // Combine the validation results
          Validated<List<TypeError>, Type> left = results.get(0);
          Validated<List<TypeError>, Type> right = results.get(1);

          // Accumulate errors from both sides
          return left.ap(
              right.map(rt -> lt -> checkMultiply(lt, rt)),
              Semigroups.list()
          ).flatMap(v -> v);
        });

    long start = System.currentTimeMillis();
    Validated<List<TypeError>, Type> result = parallelCheck.run();
    long elapsed = System.currentTimeMillis() - start;

    result.fold(
        errors -> {
          System.out.println("   Errors found (accumulated from parallel checks):");
          for (TypeError error : errors) {
            System.out.println("     - " + error.message());
          }
          return null;
        },
        type -> {
          System.out.println("   Valid type: " + type);
          return null;
        }
    );

    System.out.println("   Time: " + elapsed + "ms (both sub-expressions checked in parallel)");
    System.out.println();
  }

  /**
   * Type checks an expression with an artificial delay (to demonstrate parallel speedup).
   */
  private static ValidationPath<List<TypeError>, Type> typeCheckWithDelay(
      Expr expr, TypeEnv env, long delayMs) {
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return switch (expr) {
      case Literal(var value) -> typeCheckLiteral(value);
      case Expr.Variable(var name) -> typeCheckVariable(name, env);
      case Binary(var left, var op, var right) -> typeCheckBinary(left, op, right, env);
      case Expr.Conditional(var cond, var then_, var else_) ->
          typeCheckConditional(cond, then_, else_, env);
    };
  }

  private static ValidationPath<List<TypeError>, Type> typeCheckLiteral(Object value) {
    return switch (value) {
      case Integer _ -> Path.valid(Type.INT, Semigroups.list());
      case Boolean _ -> Path.valid(Type.BOOL, Semigroups.list());
      case String _ -> Path.valid(Type.STRING, Semigroups.list());
      default -> Path.invalid(
          List.of(new TypeError("Unknown literal type: " + value.getClass().getSimpleName())),
          Semigroups.list()
      );
    };
  }

  private static ValidationPath<List<TypeError>, Type> typeCheckVariable(String name, TypeEnv env) {
    return env.lookup(name)
        .map(type -> Path.<List<TypeError>, Type>valid(type, Semigroups.list()))
        .orElseGet(() -> Path.invalid(
            List.of(new TypeError("Undefined variable: " + name)),
            Semigroups.list()
        ));
  }

  private static ValidationPath<List<TypeError>, Type> typeCheckBinary(
      Expr left, BinaryOp op, Expr right, TypeEnv env) {
    return typeCheckWithDelay(left, env, 0)
        .zipWithAccum(typeCheckWithDelay(right, env, 0), (lt, rt) -> checkBinaryTypes(op, lt, rt))
        .via(result -> result);
  }

  private static ValidationPath<List<TypeError>, Type> typeCheckConditional(
      Expr cond, Expr then_, Expr else_, TypeEnv env) {
    return typeCheckWithDelay(cond, env, 0)
        .zipWith3Accum(
            typeCheckWithDelay(then_, env, 0),
            typeCheckWithDelay(else_, env, 0),
            ParallelTypeCheckerDemo::checkConditionalTypes
        )
        .via(result -> result);
  }

  private static ValidationPath<List<TypeError>, Type> checkBinaryTypes(
      BinaryOp op, Type left, Type right) {
    return switch (op) {
      case ADD, SUB, MUL, DIV -> {
        if (left == Type.INT && right == Type.INT) {
          yield Path.valid(Type.INT, Semigroups.list());
        }
        yield Path.invalid(List.of(new TypeError(
            "Arithmetic operator '%s' requires INT operands, got %s and %s"
                .formatted(op.symbol(), left, right)
        )), Semigroups.list());
      }
      case AND, OR -> {
        if (left == Type.BOOL && right == Type.BOOL) {
          yield Path.valid(Type.BOOL, Semigroups.list());
        }
        yield Path.invalid(List.of(new TypeError(
            "Logical operator '%s' requires BOOL operands, got %s and %s"
                .formatted(op.symbol(), left, right)
        )), Semigroups.list());
      }
      case EQ, NE -> Path.valid(Type.BOOL, Semigroups.list());
      case LT, LE, GT, GE -> {
        if (left == Type.INT && right == Type.INT) {
          yield Path.valid(Type.BOOL, Semigroups.list());
        }
        yield Path.invalid(List.of(new TypeError(
            "Comparison '%s' requires INT operands, got %s and %s"
                .formatted(op.symbol(), left, right)
        )), Semigroups.list());
      }
    };
  }

  private static ValidationPath<List<TypeError>, Type> checkMultiply(Type left, Type right) {
    if (left == Type.INT && right == Type.INT) {
      return Path.valid(Type.INT, Semigroups.list());
    }
    return Path.invalid(List.of(new TypeError(
        "Multiplication requires INT operands, got %s and %s".formatted(left, right)
    )), Semigroups.list());
  }

  private static ValidationPath<List<TypeError>, Type> checkConditionalTypes(
      Type cond, Type then_, Type else_) {
    java.util.ArrayList<TypeError> errors = new java.util.ArrayList<>();

    if (cond != Type.BOOL) {
      errors.add(new TypeError("Condition must be BOOL, got " + cond));
    }
    if (then_ != else_) {
      errors.add(new TypeError(
          "Branches must have same type, got %s and %s".formatted(then_, else_)
      ));
    }

    if (errors.isEmpty()) {
      return Path.valid(then_, Semigroups.list());
    }
    return Path.invalid(errors, Semigroups.list());
  }
}
