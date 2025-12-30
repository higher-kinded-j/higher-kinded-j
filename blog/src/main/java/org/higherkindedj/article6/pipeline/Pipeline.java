// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article6.pipeline;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.article4.ast.Expr;
import org.higherkindedj.article4.transform.ExprOptimiser;
import org.higherkindedj.article5.interpret.Environment;
import org.higherkindedj.article5.interpret.ExprInterpreter;
import org.higherkindedj.article5.typecheck.ExprTypeChecker;
import org.higherkindedj.article5.typecheck.Type;
import org.higherkindedj.article5.typecheck.TypeError;
import org.higherkindedj.article5.typecheck.TypeEnv;
import org.higherkindedj.article6.parse.ExprParser;
import org.higherkindedj.article6.parse.ParseError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;

/**
 * A complete processing pipeline for the expression language.
 *
 * <p>This class composes all the components we've built throughout the series:
 *
 * <ol>
 *   <li><b>Parser</b> - converts source text to AST ({@link ExprParser})
 *   <li><b>Type Checker</b> - validates types with error accumulation ({@link ExprTypeChecker})
 *   <li><b>Optimiser</b> - transforms the AST for efficiency ({@link ExprOptimiser})
 *   <li><b>Interpreter</b> - evaluates the expression with state threading ({@link ExprInterpreter})
 * </ol>
 *
 * <p>Each phase uses the effect system appropriate to its needs:
 *
 * <ul>
 *   <li>Parsing uses {@link Either} for fail-fast errors
 *   <li>Type checking uses {@link Validated} for error accumulation
 *   <li>Optimisation is pure (no effects)
 *   <li>Interpretation uses State for environment threading
 * </ul>
 *
 * <p>This is data-oriented architecture at the system level: data flows through transformations,
 * each operating on the same underlying AST structure but with different effects.
 *
 * @param parser function to parse source text into an AST
 * @param typeChecker function to type check an AST (with error accumulation)
 * @param optimiser function to optimise an AST
 * @param interpreter function to interpret an AST
 */
public record Pipeline(
    Function<String, Either<ParseError, Expr>> parser,
    Function<Expr, Validated<List<TypeError>, Type>> typeChecker,
    Function<Expr, Expr> optimiser,
    Function<Expr, Function<Environment, Object>> interpreter) {

  /**
   * Create the default pipeline using the standard components.
   *
   * @param typeEnv the type environment for type checking
   * @return a configured pipeline
   */
  public static Pipeline standard(TypeEnv typeEnv) {
    return new Pipeline(
        source -> new ExprParser(source).parse(),
        expr -> ExprTypeChecker.typeCheck(expr, typeEnv),
        ExprOptimiser::optimise,
        expr -> env -> ExprInterpreter.eval(expr, env));
  }

  /**
   * Run the complete pipeline on source text.
   *
   * <p>The pipeline proceeds through four stages:
   *
   * <ol>
   *   <li>Parse the source text (fail-fast on syntax errors)
   *   <li>Type check the AST (accumulate all type errors)
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
        .flatMap(
            ast -> {
              // Phase 2: Type check (with error accumulation)
              Validated<List<TypeError>, Type> typeResult = typeChecker.apply(ast);

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
            });
  }

  /**
   * Run the pipeline with type checking only (no interpretation).
   *
   * <p>Useful for IDE integration where you want to show type errors without running the code.
   *
   * @param source the source text to process
   * @return Either.right(type) on success, Either.left(error) on failure
   */
  public Either<PipelineError, Type> typeCheck(String source) {
    return parser
        .apply(source)
        .mapLeft(PipelineError::fromParseError)
        .flatMap(
            ast ->
                typeChecker
                    .apply(ast)
                    .fold(
                        errors -> Either.left(PipelineError.fromTypeErrors(errors)),
                        Either::right));
  }

  /**
   * Parse and optimise without type checking or interpretation.
   *
   * <p>Useful for exploring optimisation behaviour.
   *
   * @param source the source text to process
   * @return Either.right(optimisedExpr) on success, Either.left(error) on failure
   */
  public Either<PipelineError, Expr> parseAndOptimise(String source) {
    return parser.apply(source).mapLeft(PipelineError::fromParseError).map(optimiser);
  }
}
