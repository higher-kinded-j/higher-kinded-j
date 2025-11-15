// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.constant;

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.util.validation.Validation;
import org.jspecify.annotations.NullMarked;

/**
 * {@link Bifunctor} instance for {@link Const}.
 *
 * <p>This instance enables transformation of the constant value (first parameter) while leaving the
 * phantom type (second parameter) unchanged at runtime. When mapping the second parameter, only the
 * type signature changes; the constant value remains the same.
 *
 * <p>This behavior makes {@code Const} particularly useful for implementing efficient folds and
 * traversals where you want to accumulate a value while abstracting over a type parameter.
 *
 * <p>Examples:
 *
 * <pre>{@code
 * Const<String, Integer> const = new Const<>("hello");
 *
 * // Transform the constant value (first parameter)
 * Const<Integer, Integer> result1 = ConstBifunctor.INSTANCE.first(
 *     String::length,  // "hello" -> 5
 *     CONST.widen2(const)
 * );
 * // result1.value() is 5
 *
 * // Transform the phantom type (second parameter) - no runtime effect
 * Const<String, Double> result2 = ConstBifunctor.INSTANCE.second(
 *     i -> i * 2.0,  // This function is validated but not applied
 *     CONST.widen2(const)
 * );
 * // result2.value() is still "hello"
 *
 * // Transform both parameters
 * Const<Integer, Double> result3 = ConstBifunctor.INSTANCE.bimap(
 *     String::length,  // Transform constant: "hello" -> 5
 *     i -> i * 2.0,    // Transform phantom type only
 *     CONST.widen2(const)
 * );
 * // result3.value() is 5
 * }</pre>
 */
@NullMarked
public class ConstBifunctor implements Bifunctor<ConstKind2.Witness> {

  /** Singleton instance of the ConstBifunctor. */
  public static final ConstBifunctor INSTANCE = new ConstBifunctor();

  private ConstBifunctor() {}

  @Override
  public <A, B, C, D> Kind2<ConstKind2.Witness, C, D> bimap(
      Function<? super A, ? extends C> f,
      Function<? super B, ? extends D> g,
      Kind2<ConstKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(f, "f", ConstBifunctor.class, BIMAP);
    Validation.function().requireMapper(g, "g", ConstBifunctor.class, BIMAP);
    Objects.requireNonNull(fab, "Kind for ConstBifunctor.bimap cannot be null");

    Const<A, B> const_ = CONST.narrow2(fab);
    Const<C, D> result = const_.bimap(f, g);
    return CONST.widen2(result);
  }

  @Override
  public <A, B, C> Kind2<ConstKind2.Witness, C, B> first(
      Function<? super A, ? extends C> f, Kind2<ConstKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(f, "f", ConstBifunctor.class, FIRST);
    Objects.requireNonNull(fab, "Kind for ConstBifunctor.first cannot be null");

    Const<A, B> const_ = CONST.narrow2(fab);
    Const<C, B> result = const_.mapFirst(f);
    return CONST.widen2(result);
  }

  @Override
  public <A, B, D> Kind2<ConstKind2.Witness, A, D> second(
      Function<? super B, ? extends D> g, Kind2<ConstKind2.Witness, A, B> fab) {

    Validation.function().requireMapper(g, "g", ConstBifunctor.class, SECOND);
    Objects.requireNonNull(fab, "Kind for ConstBifunctor.second cannot be null");

    Const<A, B> const_ = CONST.narrow2(fab);
    Const<A, D> result = const_.mapSecond(g);
    return CONST.widen2(result);
  }
}
