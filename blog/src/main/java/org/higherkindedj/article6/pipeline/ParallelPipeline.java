// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article6.pipeline;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.transform.ExprOptimiser;
import org.higherkindedj.article5.interpret.Environment;
import org.higherkindedj.article5.interpret.ExprInterpreter;
import org.higherkindedj.article5.typecheck.ExprTypeChecker;
import org.higherkindedj.article5.typecheck.Type;
import org.higherkindedj.article5.typecheck.TypeEnv;
import org.higherkindedj.article5.typecheck.TypeError;
import org.higherkindedj.article6.parse.ExprParser;
import org.higherkindedj.article6.parse.ParseError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;

/**
 * A parallel processing pipeline using VTask and Scope for concurrent execution.
 *
 * <p>This pipeline demonstrates how to use Higher-Kinded-J's structured concurrency support
 * (introduced in v0.3.2-0.3.3) for parallel type checking and other concurrent operations.
 *
 * <p>Key features:
 * <ul>
 *   <li>Parallel type checking of independent sub-expressions using {@link Scope}
 *   <li>Configurable timeouts for long-running operations
 *   <li>Integration with {@link VTask} for virtual thread execution
 * </ul>
 *
 * <p>Compare with {@link Pipeline} which uses sequential execution. This parallel version
 * can significantly speed up type checking for expressions with many independent sub-trees.
 *
 * @param parser function to parse source text into an AST
 * @param typeChecker function to type check an AST (with error accumulation)
 * @param optimiser function to optimise an AST
 * @param interpreter function to interpret an AST
 * @param timeout maximum time to wait for type checking
 */
public record ParallelPipeline(
    Function<String, Either<ParseError, Expr>> parser,
    Function<Expr, Validated<List<TypeError>, Type>> typeChecker,
    Function<Expr, Expr> optimiser,
    Function<Expr, Function<Environment, Object>> interpreter,
    Duration timeout) {

  /**
   * Create the default parallel pipeline using the standard components.
   *
   * @param typeEnv the type environment for type checking
   * @return a configured parallel pipeline with 5-second timeout
   */
  public static ParallelPipeline standard(TypeEnv typeEnv) {
    return new ParallelPipeline(
        source -> new ExprParser(source).parse(),
        expr -> ExprTypeChecker.typeCheck(expr, typeEnv),
        ExprOptimiser::optimise,
        expr -> env -> ExprInterpreter.eval(expr, env),
        Duration.ofSeconds(5));
  }

  /**
   * Create a parallel pipeline with a custom timeout.
   *
   * @param typeEnv the type environment for type checking
   * @param timeout maximum time to wait for type checking
   * @return a configured parallel pipeline
   */
  public static ParallelPipeline withTimeout(TypeEnv typeEnv, Duration timeout) {
    return new ParallelPipeline(
        source -> new ExprParser(source).parse(),
        expr -> ExprTypeChecker.typeCheck(expr, typeEnv),
        ExprOptimiser::optimise,
        expr -> env -> ExprInterpreter.eval(expr, env),
        timeout);
  }

  /**
   * Run the complete pipeline on source text using parallel type checking.
   *
   * <p>The pipeline proceeds through four stages:
   * <ol>
   *   <li>Parse the source text (fail-fast on syntax errors)
   *   <li>Type check the AST using VTask (with timeout)
   *   <li>Optimise the AST (pure transformation)
   *   <li>Interpret the result (thread environment state)
   * </ol>
   *
   * @param source the source text to process
   * @param env the runtime environment for interpretation
   * @return Either.right(result) on success, Either.left(error) on failure
   */
  public Either<PipelineError, Object> run(String source, Environment env) {
    // Phase 1: Parse
    Either<ParseError, Expr> parseResult = parser.apply(source);

    return parseResult
        .mapLeft(PipelineError::fromParseError)
        .flatMap(ast -> {
          // Phase 2: Type check using VTask with timeout
          VTask<Validated<List<TypeError>, Type>> typeCheckTask =
              VTask.of(() -> typeChecker.apply(ast));

          try {
            Validated<List<TypeError>, Type> typeResult =
                Scope.<Validated<List<TypeError>, Type>>allSucceed()
                    .fork(typeCheckTask)
                    .timeout(timeout)
                    .join()
                    .run()
                    .getFirst();

            return typeResult.fold(
                // Type errors: return all accumulated errors
                errors -> Either.left(PipelineError.fromTypeErrors(errors)),
                // Type check passed: proceed to optimisation and interpretation
                type -> {
                  try {
                    // Phase 3: Optimise
                    Expr optimised = optimiser.apply(ast);

                    // Phase 4: Interpret
                    Object result = interpreter.apply(optimised).apply(env);
                    return Either.right(result);
                  } catch (Exception e) {
                    return Either.left(PipelineError.runtime(e.getMessage()));
                  }
                });
          } catch (Exception e) {
            return Either.left(PipelineError.runtime("Type checking timed out or failed: " + e.getMessage()));
          }
        });
  }

  /**
   * Type check multiple expressions in parallel.
   *
   * <p>This method demonstrates using {@link Scope#allSucceed()} to run multiple
   * type checks concurrently, which can significantly speed up processing when
   * checking many independent expressions.
   *
   * @param expressions the expressions to type check
   * @param typeEnv the type environment
   * @return a list of validation results, one per expression
   */
  public List<Validated<List<TypeError>, Type>> typeCheckAllParallel(
      List<Expr> expressions, TypeEnv typeEnv) {

    List<VTask<Validated<List<TypeError>, Type>>> tasks = expressions.stream()
        .map(expr -> VTask.of(() -> ExprTypeChecker.typeCheck(expr, typeEnv)))
        .toList();

    return Scope.<Validated<List<TypeError>, Type>>allSucceed()
        .forkAll(tasks)
        .timeout(timeout)
        .join()
        .run();
  }

  /**
   * Race multiple parser implementations and return the first successful result.
   *
   * <p>This demonstrates using {@link Scope#anySucceed()} for racing operations,
   * useful when you have multiple ways to parse and want the fastest one.
   *
   * @param source the source text to parse
   * @param parsers alternative parser implementations
   * @return the first successful parse result
   */
  @SafeVarargs
  public final Either<PipelineError, Expr> parseWithFallbacks(
      String source, Function<String, Either<ParseError, Expr>>... parsers) {

    if (parsers.length == 0) {
      return parser.apply(source).mapLeft(PipelineError::fromParseError);
    }

    List<VTask<Either<ParseError, Expr>>> tasks = java.util.Arrays.stream(parsers)
        .map(p -> VTask.of(() -> p.apply(source)))
        .toList();

    try {
      Either<ParseError, Expr> result = Scope.<Either<ParseError, Expr>>anySucceed()
          .forkAll(tasks)
          .timeout(timeout)
          .join()
          .run();

      return result.mapLeft(PipelineError::fromParseError);
    } catch (Exception e) {
      return Either.left(PipelineError.runtime("All parsers failed: " + e.getMessage()));
    }
  }
}
