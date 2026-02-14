// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.writer;

import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage.BaseKindHelperConfig;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;
import org.higherkindedj.hkt.writer.WriterKindHelper;

/**
 * KindHelper testing for Writer type.
 *
 * <p>This class provides convenient testing for Writer's KindHelper implementation with automatic
 * helper detection.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.writerKindHelper(Writer.tell("log"))
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.writerKindHelper(Writer.value(monoid, 42))
 *     .skipValidations()
 *     .withPerformanceTests()
 *     .test();
 * }</pre>
 *
 * @param <W> The log type
 * @param <A> The value type
 */
public final class WriterKindHelperTest<W, A>
    extends BaseKindHelperConfig<Writer<W, A>, WriterKind.Witness<W>, A> {

  private static final WriterKindHelper WRITER = WriterKindHelper.WRITER;

  public WriterKindHelperTest(Writer<W, A> instance) {
    super(instance, getWriterClass(), writer -> WRITER.widen(writer), kind -> WRITER.narrow(kind));
  }

  @SuppressWarnings("unchecked")
  private static <W, A> Class<Writer<W, A>> getWriterClass() {
    return (Class<Writer<W, A>>) (Class<?>) Writer.class;
  }

  @Override
  protected WriterKindHelperTest<W, A> self() {
    return this;
  }
}
