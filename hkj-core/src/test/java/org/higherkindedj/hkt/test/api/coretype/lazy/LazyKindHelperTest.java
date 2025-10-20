// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.lazy;

import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.lazy.LazyKind;
import org.higherkindedj.hkt.lazy.LazyKindHelper;
import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage.BaseKindHelperConfig;

/**
 * KindHelper testing for Lazy type.
 *
 * <p>This class provides convenient testing for Lazy's KindHelper implementation with automatic
 * helper detection.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.lazyKindHelper(Lazy.defer(() -> "test"))
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.lazyKindHelper(Lazy.now(42))
 *     .skipValidations()
 *     .withPerformanceTests()
 *     .test();
 * }</pre>
 *
 * @param <A> The value type
 */
public final class LazyKindHelperTest<A>
    extends BaseKindHelperConfig<Lazy<A>, LazyKind.Witness, A> {

  private static final LazyKindHelper LAZY = LazyKindHelper.LAZY;

  public LazyKindHelperTest(Lazy<A> instance) {
    super(instance, getLazyClass(), lazy -> LAZY.widen(lazy), kind -> LAZY.narrow(kind));
  }

  @SuppressWarnings("unchecked")
  private static <A> Class<Lazy<A>> getLazyClass() {
    return (Class<Lazy<A>>) (Class<?>) Lazy.class;
  }

  @Override
  protected LazyKindHelperTest<A> self() {
    return this;
  }
}
