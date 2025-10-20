// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.trymonad;

import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage.BaseKindHelperConfig;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.trymonad.TryKindHelper;

/**
 * KindHelper testing for Try type.
 *
 * <p>This class provides convenient testing for Try's KindHelper implementation with automatic
 * helper detection.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.tryKindHelper(Try.success("test"))
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.tryKindHelper(Try.success("test"))
 *     .skipValidations()
 *     .withPerformanceTests()
 *     .test();
 * }</pre>
 *
 * @param <T> The value type
 */
public final class TryKindHelperTest<T> extends BaseKindHelperConfig<Try<T>, TryKind.Witness, T> {

  private static final TryKindHelper TRY = TryKindHelper.TRY;

  public TryKindHelperTest(Try<T> instance) {
    super(instance, getTryClass(), tryInstance -> TRY.widen(tryInstance), kind -> TRY.narrow(kind));
  }

  @SuppressWarnings("unchecked")
  private static <T> Class<Try<T>> getTryClass() {
    return (Class<Try<T>>) (Class<?>) Try.class;
  }

  @Override
  protected TryKindHelperTest<T> self() {
    return this;
  }
}
