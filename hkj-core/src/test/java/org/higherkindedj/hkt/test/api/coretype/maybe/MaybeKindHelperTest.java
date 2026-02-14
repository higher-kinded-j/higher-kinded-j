// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.maybe;

import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage.BaseKindHelperConfig;

/**
 * KindHelper testing for Maybe type.
 *
 * <p>This class provides convenient testing for Maybe's KindHelper implementation with automatic
 * helper detection.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.maybeKindHelper(Maybe.just(42))
 *     .test();
 * }</pre>
 *
 * <h3>Skip Validations:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.maybeKindHelper(Maybe.just(42))
 *     .skipValidations()
 *     .test();
 * }</pre>
 *
 * @param <A> The value type
 */
public final class MaybeKindHelperTest<A>
    extends BaseKindHelperConfig<Maybe<A>, MaybeKind.Witness, A> {

  private static final MaybeKindHelper MAYBE = MaybeKindHelper.MAYBE;

  public MaybeKindHelperTest(Maybe<A> instance) {
    super(instance, getMaybeClass(), maybe -> MAYBE.widen(maybe), kind -> MAYBE.narrow(kind));
  }

  @SuppressWarnings("unchecked")
  private static <A> Class<Maybe<A>> getMaybeClass() {
    return (Class<Maybe<A>>) (Class<?>) Maybe.class;
  }

  @Override
  protected MaybeKindHelperTest<A> self() {
    return this;
  }
}
