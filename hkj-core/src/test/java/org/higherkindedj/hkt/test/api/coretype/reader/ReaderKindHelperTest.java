// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.reader;

import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader.ReaderKind;
import org.higherkindedj.hkt.reader.ReaderKindHelper;
import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage.BaseKindHelperConfig;

/**
 * KindHelper testing for Reader type.
 *
 * <p>This class provides convenient testing for Reader's KindHelper implementation with automatic
 * helper detection.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.readerKindHelper(Reader.ask())
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.readerKindHelper(Reader.constant("test"))
 *     .skipValidations()
 *     .withPerformanceTests()
 *     .test();
 * }</pre>
 *
 * @param <R> The environment type
 * @param <A> The value type
 */
public final class ReaderKindHelperTest<R, A>
    extends BaseKindHelperConfig<Reader<R, A>, ReaderKind.Witness<R>, A> {

  private static final ReaderKindHelper READER = ReaderKindHelper.READER;

  public ReaderKindHelperTest(Reader<R, A> instance) {
    super(instance, getReaderClass(), reader -> READER.widen(reader), kind -> READER.narrow(kind));
  }

  @SuppressWarnings("unchecked")
  private static <R, A> Class<Reader<R, A>> getReaderClass() {
    return (Class<Reader<R, A>>) (Class<?>) Reader.class;
  }

  @Override
  protected ReaderKindHelperTest<R, A> self() {
    return this;
  }
}
