// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.tuple;

import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * {@link Bifunctor} instance for {@link Tuple2}.
 *
 * <p>This instance enables transformation of both the first and second elements of a {@link Tuple2}
 * independently. Both type parameters are covariant.
 *
 * <p>Examples:
 *
 * <pre>{@code
 * Tuple2<String, Integer> tuple = new Tuple2<>("hello", 42);
 * Tuple2<Integer, String> transformed = Tuple2Bifunctor.INSTANCE.bimap(
 *     String::length,     // Transform first: "hello" -> 5
 *     n -> "Value: " + n, // Transform second: 42 -> "Value: 42"
 *     TUPLE2.widen2(tuple)
 * );
 * // result = new Tuple2<>(5, "Value: 42")
 * }</pre>
 */
@NullMarked
public class Tuple2Bifunctor implements Bifunctor<Tuple2Kind2.Witness> {

  /** Singleton instance of the Tuple2Bifunctor. */
  public static final Tuple2Bifunctor INSTANCE = new Tuple2Bifunctor();

  private Tuple2Bifunctor() {}

  @Override
  public <A, B, C, D> Kind2<Tuple2Kind2.Witness, C, D> bimap(
      Function<? super A, ? extends C> f,
      Function<? super B, ? extends D> g,
      Kind2<Tuple2Kind2.Witness, A, B> fab) {

    Validation.function().requireMapper(f, "f", Tuple2Bifunctor.class, BIMAP);
    Validation.function().requireMapper(g, "g", Tuple2Bifunctor.class, BIMAP);
    Objects.requireNonNull(fab, "Kind2 for Tuple2Bifunctor.bimap cannot be null");

    Tuple2<A, B> tuple = TUPLE2.narrow2(fab);
    Tuple2<C, D> result = tuple.bimap(f, g);
    return TUPLE2.widen2(result);
  }

  @Override
  public <A, B, C> Kind2<Tuple2Kind2.Witness, C, B> first(
      Function<? super A, ? extends C> f, Kind2<Tuple2Kind2.Witness, A, B> fab) {

    Validation.function().requireMapper(f, "f", Tuple2Bifunctor.class, FIRST);
    Objects.requireNonNull(fab, "Kind2 for Tuple2Bifunctor.first cannot be null");

    Tuple2<A, B> tuple = TUPLE2.narrow2(fab);
    Tuple2<C, B> result = tuple.mapFirst(f);
    return TUPLE2.widen2(result);
  }

  @Override
  public <A, B, D> Kind2<Tuple2Kind2.Witness, A, D> second(
      Function<? super B, ? extends D> g, Kind2<Tuple2Kind2.Witness, A, B> fab) {

    Validation.function().requireMapper(g, "g", Tuple2Bifunctor.class, SECOND);
    Objects.requireNonNull(fab, "Kind2 for Tuple2Bifunctor.second cannot be null");

    Tuple2<A, B> tuple = TUPLE2.narrow2(fab);
    Tuple2<A, D> result = tuple.mapSecond(g);
    return TUPLE2.widen2(result);
  }
}
