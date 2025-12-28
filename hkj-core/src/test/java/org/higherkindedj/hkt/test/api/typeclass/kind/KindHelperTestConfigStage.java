// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.kind;

import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern;

public final class KindHelperTestConfigStage<T, F extends WitnessArity<TypeArity.Unary>, A> {
  private final Class<T> targetClass;
  private final KindHelperTestPattern.KindHelper<T, F, A> helper;
  private final T validInstance;

  private boolean skipRoundTrip = false;
  private boolean skipValidations = false;
  private boolean skipInvalidType = false;
  private boolean skipIdempotency = false;
  private boolean skipEdgeCases = false;
  private boolean withPerformance = false;

  public KindHelperTestConfigStage(
      Class<T> targetClass, KindHelperTestPattern.KindHelper<T, F, A> helper, T validInstance) {
    this.targetClass = targetClass;
    this.helper = helper;
    this.validInstance = validInstance;
  }

  public KindHelperTestConfigStage<T, F, A> skipRoundTrip() {
    skipRoundTrip = true;
    return this;
  }

  public KindHelperTestConfigStage<T, F, A> skipValidations() {
    skipValidations = true;
    return this;
  }

  public KindHelperTestConfigStage<T, F, A> skipInvalidType() {
    skipInvalidType = true;
    return this;
  }

  public KindHelperTestConfigStage<T, F, A> skipIdempotency() {
    skipIdempotency = true;
    return this;
  }

  public KindHelperTestConfigStage<T, F, A> skipEdgeCases() {
    skipEdgeCases = true;
    return this;
  }

  public KindHelperTestConfigStage<T, F, A> withPerformanceTests() {
    withPerformance = true;
    return this;
  }

  public void testAll() {
    if (!skipRoundTrip) {
      KindHelperTestPattern.testRoundTripWithHelper(validInstance, helper);
    }
    if (!skipValidations) {
      KindHelperTestPattern.testValidationsWithHelper(targetClass, helper);
    }
    if (!skipInvalidType) {
      KindHelperTestPattern.testInvalidTypeWithHelper(targetClass, helper);
    }
    if (!skipIdempotency) {
      KindHelperTestPattern.testIdempotencyWithHelper(validInstance, helper);
    }
    if (!skipEdgeCases) {
      KindHelperTestPattern.testEdgeCasesWithHelper(validInstance, targetClass, helper);
    }
    if (withPerformance) {
      KindHelperTestPattern.testPerformance(validInstance, helper::widen, helper::narrow);
    }
  }

  public void test() {
    testAll();
  }
}
