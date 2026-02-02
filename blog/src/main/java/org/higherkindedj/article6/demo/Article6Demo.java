// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article6.demo;

import java.util.List;
import java.util.Map;
import org.higherkindedj.article4.ast.BinaryOp;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.ast.Expr.Binary;
import org.higherkindedj.article4.ast.Expr.Literal;
import org.higherkindedj.article5.interpret.Environment;
import org.higherkindedj.article5.typecheck.Type;
import org.higherkindedj.article5.typecheck.TypeEnv;
import org.higherkindedj.article5.typecheck.TypeError;
import org.higherkindedj.article6.pipeline.ParallelPipeline;
import org.higherkindedj.article6.pipeline.Pipeline;
import org.higherkindedj.article6.pipeline.PipelineError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;

/**
 * Demonstration of the complete expression language pipeline.
 *
 * <p>This demo shows the pipeline from Article 6, combining:
 *
 * <ul>
 *   <li>Parsing (source text to AST)
 *   <li>Type checking (with error accumulation)
 *   <li>Optimisation (constant folding, etc.)
 *   <li>Interpretation (with environment state)
 *   <li>Parallel processing with VTask and Scope (v0.3.2+)
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :review:run
 * -PmainClass=org.higherkindedj.article6.demo.Article6Demo}
 */
public final class Article6Demo {

  public static void main(String[] args) {
    System.out.println("=== Article 6: Complete Pipeline Demo ===\n");

    // Set up environments
    TypeEnv typeEnv = TypeEnv.of(Map.of("x", Type.INT, "y", Type.INT, "flag", Type.BOOL));
    Environment runtimeEnv = Environment.of("x", 10, "y", 5, "flag", true);

    // Create the standard pipeline
    Pipeline pipeline = Pipeline.standard(typeEnv);

    // Demo 1: Simple arithmetic
    demo(pipeline, "Simple arithmetic", "(x + y) * 2", runtimeEnv);

    // Demo 2: Conditional expression
    demo(pipeline, "Conditional", "if flag then x * 2 else y * 3", runtimeEnv);

    // Demo 3: Nested conditionals
    demo(pipeline, "Nested conditionals", "if x > y then if flag then x else y else 0", runtimeEnv);

    // Demo 4: Expression with optimisable constants
    demo(pipeline, "With constant folding", "(2 + 3) * x + (10 - 5)", runtimeEnv);

    // Demo 5: Comparison operations
    demo(pipeline, "Comparisons", "if x >= y then x - y else y - x", runtimeEnv);

    // Demo 6: Type error demonstration
    System.out.println("--- Demo: Type errors (error accumulation) ---");
    String badExpr = "if x then y + flag else true";
    System.out.println("Expression: " + badExpr);
    Either<PipelineError, Object> badResult = pipeline.run(badExpr, runtimeEnv);
    badResult.fold(
        error -> {
          System.out.println("Error (as expected):\n" + error);
          return null;
        },
        result -> {
          System.out.println("Unexpected success: " + result);
          return null;
        });
    System.out.println();

    // Demo 7: Parse error demonstration
    System.out.println("--- Demo: Parse error ---");
    String parseError = "x + + y";
    System.out.println("Expression: " + parseError);
    Either<PipelineError, Object> parseResult = pipeline.run(parseError, runtimeEnv);
    parseResult.fold(
        error -> {
          System.out.println("Error (as expected): " + error);
          return null;
        },
        result -> {
          System.out.println("Unexpected success: " + result);
          return null;
        });
    System.out.println();

    // Demo 8: Type check only (no interpretation)
    System.out.println("--- Demo: Type checking only ---");
    String toTypeCheck = "if x > 0 then x * 2 else 0 - x";
    System.out.println("Expression: " + toTypeCheck);
    Either<PipelineError, Type> typeResult = pipeline.typeCheck(toTypeCheck);
    typeResult.fold(
        error -> {
          System.out.println("Type error: " + error);
          return null;
        },
        type -> {
          System.out.println("Type: " + type);
          return null;
        });
    System.out.println();

    // Demo 9: Parallel Pipeline (v0.3.2+ feature)
    System.out.println("════════════════════════════════════════════════════════════════════");
    System.out.println("=== Parallel Pipeline Demo (VTask + Scope) ===\n");

    ParallelPipeline parallelPipeline = ParallelPipeline.standard(typeEnv);

    // Same expression through parallel pipeline
    demoParallel(parallelPipeline, "Parallel arithmetic", "(x + y) * 2", runtimeEnv);

    // Parallel type checking of multiple expressions
    System.out.println("--- Demo: Parallel type checking multiple expressions ---");
    List<Expr> expressions = List.of(
        new Binary(new Literal(1), BinaryOp.ADD, new Literal(2)),
        new Binary(new Literal(true), BinaryOp.AND, new Literal(false)),
        new Binary(new Literal(1), BinaryOp.ADD, new Literal(true)),  // Type error
        new Binary(new Literal(10), BinaryOp.MUL, new Literal(5))
    );

    long start = System.currentTimeMillis();
    List<Validated<List<TypeError>, Type>> results =
        parallelPipeline.typeCheckAllParallel(expressions, typeEnv);
    long elapsed = System.currentTimeMillis() - start;

    for (int i = 0; i < expressions.size(); i++) {
      Expr expr = expressions.get(i);
      Validated<List<TypeError>, Type> result = results.get(i);
      String status = result.fold(
          errors -> "Invalid: " + errors.getFirst().message(),
          type -> "Valid: " + type
      );
      System.out.println("  " + expr.format() + " -> " + status);
    }
    System.out.println("  Time: " + elapsed + "ms (all checked in parallel)");
    System.out.println();

    System.out.println("=== Demo complete ===");
  }

  private static void demo(Pipeline pipeline, String name, String source, Environment env) {
    System.out.println("--- Demo: " + name + " ---");
    System.out.println("Expression: " + source);

    Either<PipelineError, Object> result = pipeline.run(source, env);

    result.fold(
        error -> {
          System.out.println("Error: " + error);
          return null;
        },
        value -> {
          System.out.println("Result: " + value);
          return null;
        });
    System.out.println();
  }

  private static void demoParallel(
      ParallelPipeline pipeline, String name, String source, Environment env) {
    System.out.println("--- Demo: " + name + " ---");
    System.out.println("Expression: " + source);

    Either<PipelineError, Object> result = pipeline.run(source, env);

    result.fold(
        error -> {
          System.out.println("Error: " + error);
          return null;
        },
        value -> {
          System.out.println("Result: " + value);
          return null;
        });
    System.out.println();
  }
}
