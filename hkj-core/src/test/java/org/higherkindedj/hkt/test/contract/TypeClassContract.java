// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Entry point for type-class contract testing.
 *
 * <p>Each algebra adds a single small contract class plus one entry method here, reusing the shared
 * {@link ContractEngine} and {@link ContractValidations}. Implemented so far: {@link #functor},
 * {@link #foldable}, {@link #applicative}, {@link #monad}, {@link #monadError}, {@link #traverse},
 * {@link #bifunctor}.
 */
public final class TypeClassContract {

  private TypeClassContract() {
    throw new AssertionError("TypeClassContract is a utility class");
  }

  /**
   * Begins configuring a Functor contract.
   *
   * @param contextClass the implementation under test, used only for error-message context
   * @param <F> the Functor witness type
   */
  public static <F extends WitnessArity<TypeArity.Unary>> FunctorContract.Start<F> functor(
      Class<?> contextClass) {
    return new FunctorContract.Start<>(contextClass);
  }

  /**
   * Begins configuring a Foldable contract.
   *
   * @param contextClass the implementation under test, used only for error-message context
   * @param <F> the Foldable witness type
   */
  public static <F extends WitnessArity<TypeArity.Unary>> FoldableContract.Start<F> foldable(
      Class<?> contextClass) {
    return new FoldableContract.Start<>(contextClass);
  }

  /**
   * Begins configuring an Applicative contract.
   *
   * @param contextClass the implementation under test, used only for error-message context
   * @param <F> the Applicative witness type
   */
  public static <F extends WitnessArity<TypeArity.Unary>> ApplicativeContract.Start<F> applicative(
      Class<?> contextClass) {
    return new ApplicativeContract.Start<>(contextClass);
  }

  /**
   * Begins configuring a Monad contract.
   *
   * @param contextClass the implementation under test, used only for error-message context
   * @param <F> the Monad witness type
   */
  public static <F extends WitnessArity<TypeArity.Unary>> MonadContract.Start<F> monad(
      Class<?> contextClass) {
    return new MonadContract.Start<>(contextClass);
  }

  /**
   * Begins configuring a MonadError contract.
   *
   * @param contextClass the implementation under test, used only for error-message context
   * @param <F> the MonadError witness type
   * @param <E> the error type
   */
  public static <F extends WitnessArity<TypeArity.Unary>, E>
      MonadErrorContract.Start<F, E> monadError(Class<?> contextClass) {
    return new MonadErrorContract.Start<>(contextClass);
  }

  /**
   * Begins configuring a Traverse contract.
   *
   * @param contextClass the implementation under test, used only for error-message context
   * @param <F> the Traverse witness type
   */
  public static <F extends WitnessArity<TypeArity.Unary>> TraverseContract.Start<F> traverse(
      Class<?> contextClass) {
    return new TraverseContract.Start<>(contextClass);
  }

  /**
   * Begins configuring a Bifunctor contract.
   *
   * @param contextClass the implementation under test, used only for error-message context
   * @param <F> the Bifunctor witness type
   */
  public static <F extends WitnessArity<TypeArity.Binary>> BifunctorContract.Start<F> bifunctor(
      Class<?> contextClass) {
    return new BifunctorContract.Start<>(contextClass);
  }
}
