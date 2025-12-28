// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.traverse;

import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.test.api.typeclass.internal.TestMethodRegistry;

/**
 * Stage 6: Optional configuration and execution.
 *
 * <p>All type parameters have been successfully inferred through the progressive stages.
 *
 * @param <F> The Traverse witness type
 * @param <G> The Applicative witness type
 * @param <A> The input type
 * @param <B> The output type
 * @param <M> The Monoid type
 */
public final class TraverseTestConfigStage<
    F extends WitnessArity<TypeArity.Unary>, G extends WitnessArity<TypeArity.Unary>, A, B, M> {
  private final Class<?> contextClass;
  private final Traverse<F> traverse;
  private final Kind<F, A> validKind;
  private final Function<A, B> mapper;
  private final Applicative<G> applicative;
  private final Function<A, Kind<G, B>> traverseFunction;
  private final Monoid<M> monoid;
  private final Function<A, M> foldMapFunction;

  private BiPredicate<Kind<G, ?>, Kind<G, ?>> equalityChecker;

  TraverseTestConfigStage(
      Class<?> contextClass,
      Traverse<F> traverse,
      Kind<F, A> validKind,
      Function<A, B> mapper,
      Applicative<G> applicative,
      Function<A, Kind<G, B>> traverseFunction,
      Monoid<M> monoid,
      Function<A, M> foldMapFunction) {

    this.contextClass = contextClass;
    this.traverse = traverse;
    this.validKind = validKind;
    this.mapper = mapper;
    this.applicative = applicative;
    this.traverseFunction = traverseFunction;
    this.monoid = monoid;
    this.foldMapFunction = foldMapFunction;
  }

  /**
   * Configures equality checker for law testing.
   *
   * <p>Progressive disclosure: Next step is test execution.
   *
   * @param checker The equality checker
   * @return This stage for execution
   */
  public TraverseTestConfigStage<F, G, A, B, M> withEqualityChecker(
      BiPredicate<Kind<G, ?>, Kind<G, ?>> checker) {
    this.equalityChecker = checker;
    return this;
  }

  /** Executes all tests including laws. */
  public void testAll() {
    testOperations();
    testValidations();
    testExceptions();
    if (equalityChecker != null) {
      testLaws();
    }
  }

  /** Executes only operation tests. */
  public void testOperations() {
    TestMethodRegistry.testTraverseOperations(
        traverse, validKind, mapper, applicative, traverseFunction, monoid, foldMapFunction);
  }

  /** Executes only validation tests. */
  public void testValidations() {
    TestMethodRegistry.testTraverseValidations(
        traverse,
        contextClass,
        validKind,
        mapper,
        applicative,
        traverseFunction,
        monoid,
        foldMapFunction);
  }

  /** Executes only exception tests. */
  public void testExceptions() {
    TestMethodRegistry.testTraverseExceptionPropagation(traverse, validKind, applicative, monoid);
  }

  /** Executes only law tests. */
  public void testLaws() {
    if (equalityChecker == null) {
      throw new IllegalStateException(
          "Cannot test laws without equality checker. " + "Use .withEqualityChecker() first.");
    }
    TestMethodRegistry.testTraverseLaws(
        traverse, applicative, validKind, traverseFunction, equalityChecker);
  }
}
