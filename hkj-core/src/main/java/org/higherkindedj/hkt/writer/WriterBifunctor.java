// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * {@link Bifunctor} instance for {@link Writer}.
 *
 * <p>This instance enables transformation of both the log (written output) and value channels of a
 * {@link Writer} independently. Both type parameters are covariant.
 *
 * <p>Examples:
 *
 * <pre>{@code
 * Writer<String, Integer> writer = new Writer<>("log entry", 42);
 * Writer<Integer, String> transformed = WriterBifunctor.INSTANCE.bimap(
 *     String::length,     // Transform log: "log entry" -> 9
 *     n -> "Value: " + n, // Transform value: 42 -> "Value: 42"
 *     WRITER.widen2(writer)
 * );
 * // result = new Writer<>(9, "Value: 42")
 * }</pre>
 */
@NullMarked
public class WriterBifunctor implements Bifunctor<WriterKind2.Witness> {

  /** Singleton instance of the WriterBifunctor. */
  public static final WriterBifunctor INSTANCE = new WriterBifunctor();

  private WriterBifunctor() {}

  @Override
  public <A, B, C, D> Kind2<WriterKind2.Witness, C, D> bimap(
      Function<? super A, ? extends C> f,
      Function<? super B, ? extends D> g,
      Kind2<WriterKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(f, "f", WriterBifunctor.class, BIMAP);
    Validation.function().requireMapper(g, "g", WriterBifunctor.class, BIMAP);
    Validation.kind().requireNonNull(fab, WriterBifunctor.class, BIMAP);

    Writer<A, B> writer = WRITER.narrow2(fab);
    Writer<C, D> result = writer.bimap(f, g);
    return WRITER.widen2(result);
  }

  @Override
  public <A, B, C> Kind2<WriterKind2.Witness, C, B> first(
      Function<? super A, ? extends C> f, Kind2<WriterKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(f, "f", WriterBifunctor.class, FIRST);
    Validation.kind().requireNonNull(fab, WriterBifunctor.class, FIRST);

    Writer<A, B> writer = WRITER.narrow2(fab);
    Writer<C, B> result = writer.mapWritten(f);
    return WRITER.widen2(result);
  }

  @Override
  public <A, B, D> Kind2<WriterKind2.Witness, A, D> second(
      Function<? super B, ? extends D> g, Kind2<WriterKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(g, "g", WriterBifunctor.class, SECOND);
    Validation.kind().requireNonNull(fab, WriterBifunctor.class, SECOND);

    Writer<A, B> writer = WRITER.narrow2(fab);
    Writer<A, D> result = writer.map(g);
    return WRITER.widen2(result);
  }
}
