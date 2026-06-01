/**
 * Internal test harness for hkj-core — type-class contracts, {@code KindHelper} round-trip checks
 * and shared fixtures. This harness only <em>orchestrates</em>; the reusable verification
 * primitives live in the shipped {@code hkj-test} module ({@code org.higherkindedj.hkt.laws.*}
 * laws, {@code KindEquivalence}, the {@code *Assert} types).
 *
 * <h2>Which entry point</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.test.contract.TypeClassContract} — verify a type-class
 *       instance (Functor / Foldable / Applicative / Monad / MonadError / Traverse / Bifunctor).
 *       {@code verify()} runs operations, null-validations, exception-propagation and the algebraic
 *       laws; {@code verifyOnly(Category...)} runs a subset.
 *   <li>{@link org.higherkindedj.hkt.test.api.KindHelperTests} — verify a {@code KindHelper}'s
 *       widen/narrow round-trip (plus null / foreign-kind rejection) for a built-in core type.
 *   <li>{@link org.higherkindedj.hkt.test.fixtures.TypeClassTestBase} — fixture base supplying the
 *       standard fixtures (valid kind, mappers, equality checker, …) for a type's test bases.
 * </ul>
 *
 * <pre>{@code
 * TypeClassContract.functor(MaybeFunctor.class)
 *     .instance(MaybeFunctor.INSTANCE).withKind(validKind).withMapper(mapper)
 *     .withEqualityChecker(eq).verify();
 *
 * KindHelperTests.eitherKindHelper(Either.right("x")).test();
 * }</pre>
 *
 * <h2>Packages</h2>
 *
 * <ul>
 *   <li>{@code contract} — the {@code TypeClassContract} harness: {@code ContractEngine} (Monoid
 *       failure accumulation), {@code ContractValidations}, and the per-family contracts.
 *   <li>{@code api} — {@code KindHelperTests} / {@code KindHelperTestConfig}.
 *   <li>{@code assertions} — production-aligned null/validation assertions ({@code
 *       FunctionAssertions}, {@code KindAssertions}, {@code ValidationTestBuilder}).
 *   <li>{@code fixtures} — {@code TypeClassTestBase} and {@code TestFunctions}.
 * </ul>
 *
 * <p>See {@code docs/TESTING-GUIDE.md} for worked examples.
 */
@NullMarked
package org.higherkindedj.hkt.test;

import org.jspecify.annotations.NullMarked;
