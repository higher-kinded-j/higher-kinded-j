// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Descriptor-driven, HKJ-idiomatic type-class contract harness.
 *
 * <p>{@link org.higherkindedj.hkt.test.contract.TypeClassContract} is the entry point: each algebra
 * (Functor, Applicative, Monad, …) is one small contract class that describes its checks as values,
 * run by the shared {@link org.higherkindedj.hkt.test.contract.ContractEngine} (Monoid-based
 * failure accumulation) and {@link org.higherkindedj.hkt.test.contract.ContractValidations}.
 * Algebraic laws delegate to the shipped {@code hkj-test} module ({@code
 * org.higherkindedj.hkt.laws.*}, {@code KindEquivalence}).
 */
@NullMarked
package org.higherkindedj.hkt.test.contract;

import org.jspecify.annotations.NullMarked;
