// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.higherkindedj.hkt.free.FreeKindHelper.FREE;
import static org.higherkindedj.hkt.free.test.IdentityKindHelper.IDENTITY;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.free.test.IdentityKind;
import org.higherkindedj.hkt.free.test.IdentityMonad;
import org.higherkindedj.hkt.test.fixtures.TypeClassTestBase;

/**
 * Base class for Free monad tests.
 *
 * <p>Provides common fixtures and helper methods for testing the Free monad using Identity as the
 * underlying functor. The Monad-law equality is delegated to the shared {@link FreeLawFixtures#EQ},
 * which interprets both programs through the same Identity interpreter.
 */
abstract class FreeTestBase
    extends TypeClassTestBase<FreeKind.Witness<IdentityKind.Witness>, Integer, String> {

  // Test constants
  protected static final Integer DEFAULT_VALUE = 42;
  protected static final Integer ALTERNATIVE_VALUE = 24;

  // Helper methods

  /** Creates a Free monad with a pure value. */
  protected <A> Kind<FreeKind.Witness<IdentityKind.Witness>, A> pureKind(A value) {
    return FREE.widen(Free.pure(value));
  }

  /** Narrows a Kind to a concrete Free instance. */
  protected <A> Free<IdentityKind.Witness, A> narrowToFree(
      Kind<FreeKind.Witness<IdentityKind.Witness>, A> kind) {
    return FREE.narrow(kind);
  }

  /** Interprets a Free program into Identity. */
  protected <A> A runFree(Free<IdentityKind.Witness, A> free) {
    Natural<IdentityKind.Witness, IdentityKind.Witness> transform = Natural.identity();
    Kind<IdentityKind.Witness, A> result = free.foldMap(transform, IdentityMonad.INSTANCE);
    return IDENTITY.narrow(result).value();
  }

  // Fixture implementations from parent class

  @Override
  protected Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> createValidKind() {
    return pureKind(DEFAULT_VALUE);
  }

  @Override
  protected Kind<FreeKind.Witness<IdentityKind.Witness>, Integer> createValidKind2() {
    return pureKind(ALTERNATIVE_VALUE);
  }

  @Override
  protected Function<Integer, String> createValidMapper() {
    return i -> "mapped:" + i;
  }

  @Override
  protected Function<Integer, Kind<FreeKind.Witness<IdentityKind.Witness>, String>>
      createValidFlatMapper() {
    return i -> pureKind("flatMapped:" + i);
  }

  @Override
  protected Kind<FreeKind.Witness<IdentityKind.Witness>, Function<Integer, String>>
      createValidFunctionKind() {
    return pureKind(i -> "function:" + i);
  }

  @Override
  protected BiFunction<Integer, Integer, String> createValidCombiningFunction() {
    return (a, b) -> "combined:" + a + "," + b;
  }

  @Override
  protected Integer createTestValue() {
    return DEFAULT_VALUE;
  }

  @Override
  protected Function<String, String> createSecondMapper() {
    return s -> "second:" + s;
  }

  @Override
  protected Function<Integer, Kind<FreeKind.Witness<IdentityKind.Witness>, String>>
      createTestFunction() {
    return i -> pureKind("test:" + i);
  }

  @Override
  protected Function<String, Kind<FreeKind.Witness<IdentityKind.Witness>, String>>
      createChainFunction() {
    return s -> pureKind(s + "!");
  }

  @Override
  protected BiPredicate<
          Kind<FreeKind.Witness<IdentityKind.Witness>, ?>,
          Kind<FreeKind.Witness<IdentityKind.Witness>, ?>>
      createEqualityChecker() {
    return FreeLawFixtures.EQ;
  }
}
