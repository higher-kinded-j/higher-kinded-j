/**
 * Entry points for testing built-in core-type machinery.
 *
 * <p>{@link org.higherkindedj.hkt.test.api.KindHelperTests} provides per-type {@code KindHelper}
 * (widen/narrow) round-trip testing for the built-in types (Either, Maybe, IO, …):
 *
 * <pre>{@code
 * KindHelperTests.eitherKindHelper(Either.right("test")).test();
 * KindHelperTests.maybeKindHelper(Maybe.just(42)).skipValidations().test();
 * }</pre>
 *
 * <p>Each entry returns a {@link org.higherkindedj.hkt.test.api.KindHelperTestConfig} whose
 * round-trip / idempotency / edge-case laws delegate to the shipped {@code hkj-test} {@code
 * KindHelperLaws}, plus the defensive null / foreign-kind checks.
 *
 * <p>Type-class law testing (Functor, Applicative, Monad, MonadError, Traverse, Bifunctor, …) lives
 * in {@link org.higherkindedj.hkt.test.contract.TypeClassContract}; Selective is exercised directly
 * via the shipped {@code hkj-test} {@code SelectiveLaws}.
 */
@NullMarked
package org.higherkindedj.hkt.test.api;

import org.jspecify.annotations.NullMarked;
