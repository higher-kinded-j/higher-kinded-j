// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.id;

import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage.BaseKindHelperConfig;

/**
 * KindHelper testing for Id type.
 *
 * <p>This class provides convenient testing for Id's KindHelper implementation with automatic
 * helper detection.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.idKindHelper(Id.of(42))
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.idKindHelper(Id.of("test"))
 *     .skipValidations()
 *     .withPerformanceTests()
 *     .test();
 * }</pre>
 *
 * @param <A> The value type
 */
public final class IdKindHelperTest<A> extends BaseKindHelperConfig<Id<A>, IdKind.Witness, A> {

  private static final IdKindHelper ID = IdKindHelper.ID;

  public IdKindHelperTest(Id<A> instance) {
    super(instance, getIdClass(), id -> ID.widen(id), kind -> ID.narrow(kind));
  }

  @SuppressWarnings("unchecked")
  private static <A> Class<Id<A>> getIdClass() {
    return (Class<Id<A>>) (Class<?>) Id.class;
  }

  @Override
  protected IdKindHelperTest<A> self() {
    return this;
  }
}
