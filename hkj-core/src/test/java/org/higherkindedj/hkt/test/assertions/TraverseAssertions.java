// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.assertions;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert;

/**
 * Traverse and Foldable specific assertion shortcuts.
 *
 * <p>Provides convenient methods for Traverse/Foldable operation assertions by composing from
 * {@link FunctionAssertions} and {@link KindAssertions}.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // Traverse assertions
 * assertTraverseApplicativeNull(() -> traverse.traverse(null, f, kind));
 * assertTraverseFunctionNull(() -> traverse.traverse(app, null, kind));
 *
 * // Foldable assertions
 * assertFoldMapMonoidNull(() -> traverse.foldMap(null, f, kind));
 * }</pre>
 */
public final class TraverseAssertions {

  private TraverseAssertions() {
    throw new AssertionError("TraverseAssertions is a utility class");
  }

  // Traverse operation assertions

  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseApplicativeNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertApplicativeNull(executable, "traverse");
  }

  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseFunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertMapperNull(executable, "traverse");
  }

  public static AbstractThrowableAssert<?, ? extends Throwable> assertTraverseKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "traverse");
  }

  // Foldable operation assertions

  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldMapMonoidNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertMonoidNull(executable, "foldMap");
  }

  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldMapFunctionNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertMapperNull(executable, "foldMap");
  }

  public static AbstractThrowableAssert<?, ? extends Throwable> assertFoldMapKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "foldMap");
  }

  // SequenceA operation assertions

  public static AbstractThrowableAssert<?, ? extends Throwable> assertSequenceApplicativeNull(
      ThrowableAssert.ThrowingCallable executable) {
    return FunctionAssertions.assertApplicativeNull(executable, "sequenceA");
  }

  public static AbstractThrowableAssert<?, ? extends Throwable> assertSequenceKindNull(
      ThrowableAssert.ThrowingCallable executable) {
    return KindAssertions.assertKindNull(executable, "sequenceA");
  }
}
