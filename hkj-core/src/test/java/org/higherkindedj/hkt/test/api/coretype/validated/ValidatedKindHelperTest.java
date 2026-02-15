// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.validated;

import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage.BaseKindHelperConfig;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedKindHelper;

/**
 * KindHelper testing for Validated type.
 *
 * <p>This class provides convenient testing for Validated's KindHelper implementation with
 * automatic helper detection.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.validatedKindHelper(Validated.valid("test"))
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.validatedKindHelper(Validated.valid("test"))
 *     .skipValidations()
 *     .withPerformanceTests()
 *     .test();
 * }</pre>
 *
 * @param <E> The error type
 * @param <A> The value type
 */
public final class ValidatedKindHelperTest<E, A>
    extends BaseKindHelperConfig<Validated<E, A>, ValidatedKind.Witness<E>, A> {

  private static final ValidatedKindHelper VALIDATED = ValidatedKindHelper.VALIDATED;

  public ValidatedKindHelperTest(Validated<E, A> instance) {
    super(
        instance,
        getValidatedClass(),
        validated -> VALIDATED.widen(validated),
        kind -> VALIDATED.narrow(kind));
  }

  @SuppressWarnings("unchecked")
  private static <E, A> Class<Validated<E, A>> getValidatedClass() {
    return (Class<Validated<E, A>>) (Class<?>) Validated.class;
  }

  @Override
  protected ValidatedKindHelperTest<E, A> self() {
    return this;
  }
}
