// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.foldable;

import java.util.function.Function;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.test.api.typeclass.internal.TestMethodRegistry;

/**
 * Stage 4: Configuration and execution.
 *
 * @param <F> The Foldable witness type
 * @param <A> The input type
 * @param <M> The Monoid type
 */
public final class FoldableTestConfigStage<F extends WitnessArity<TypeArity.Unary>, A, M> {
  private final Class<?> contextClass;
  private final Foldable<F> foldable;
  private final Kind<F, A> validKind;
  private final Monoid<M> monoid;
  private final Function<A, M> foldMapFunction;

  FoldableTestConfigStage(
      Class<?> contextClass,
      Foldable<F> foldable,
      Kind<F, A> validKind,
      Monoid<M> monoid,
      Function<A, M> foldMapFunction) {

    this.contextClass = contextClass;
    this.foldable = foldable;
    this.validKind = validKind;
    this.monoid = monoid;
    this.foldMapFunction = foldMapFunction;
  }

  /** Executes all tests. */
  public void testAll() {
    testOperations();
    testValidations();
    testExceptions();
  }

  /** Executes only operation tests. */
  public void testOperations() {
    TestMethodRegistry.testFoldableOperations(foldable, validKind, monoid, foldMapFunction);
  }

  /** Executes only validation tests. */
  public void testValidations() {
    TestMethodRegistry.testFoldableValidations(
        foldable, contextClass, validKind, monoid, foldMapFunction);
  }

  /** Executes only exception tests. */
  public void testExceptions() {
    TestMethodRegistry.testFoldableExceptionPropagation(foldable, validKind, monoid);
  }
}
