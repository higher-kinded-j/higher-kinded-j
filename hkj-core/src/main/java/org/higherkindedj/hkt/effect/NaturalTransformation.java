// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.higherkindedj.hkt.util.validation.Operation.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * A natural transformation from functor F to functor G: the effect package's name for {@link
 * Natural}, which it extends.
 *
 * <p>The library takes a {@link Natural} wherever it takes a natural transformation: {@link
 * GenericPath#mapK(Natural, org.higherkindedj.hkt.Monad)}, {@link FreePath#foldMap(Natural,
 * org.higherkindedj.hkt.Monad)}, {@link FreeApPath#foldMapKind(Natural,
 * org.higherkindedj.hkt.Applicative)}, and the transformations {@link VStreamTransformations}
 * gives. A {@code NaturalTransformation} is a {@code Natural}, so one written against this
 * interface can still be passed to each of them.
 *
 * <p>The naturality law is {@link Natural}'s: for all functions {@code f}, {@code
 * nt.apply(fa.map(f))} gives the same as {@code nt.apply(fa).map(f)}.
 *
 * @param <F> the source witness type
 * @param <G> the target witness type
 * @deprecated since 0.6.0, scheduled for removal in 0.7.0. Use {@link Natural}, which has the same
 *     single method, {@code apply}, and offers {@code andThen}, {@code compose} and {@code
 *     identity} too. The {@code MigrateDeprecationsTo0_7_0} recipe in {@code hkj-openrewrite}
 *     changes the type at each use.
 */
@Deprecated(since = "0.6.0", forRemoval = true)
@FunctionalInterface
public interface NaturalTransformation<
        F extends WitnessArity<TypeArity.Unary>, G extends WitnessArity<TypeArity.Unary>>
    extends Natural<F, G> {

  /**
   * Applies this natural transformation to a Kind value.
   *
   * @param fa the source Kind value; must not be null
   * @param <A> the value type
   * @return the transformed Kind value
   */
  @Override
  <A> Kind<G, A> apply(Kind<F, A> fa);

  /**
   * Composes this natural transformation with another.
   *
   * <p>The resulting transformation first applies this transformation, then the {@code after}
   * transformation.
   *
   * @param after the transformation to apply after this one; must not be null
   * @param <H> the final target witness type
   * @return a composed natural transformation
   */
  default <H extends WitnessArity<TypeArity.Unary>> NaturalTransformation<F, H> andThen(
      NaturalTransformation<G, H> after) {
    Validation.function().require(after, "after", AND_THEN);
    return new NaturalTransformation<>() {
      @Override
      public <A> Kind<H, A> apply(Kind<F, A> fa) {
        return after.apply(NaturalTransformation.this.apply(fa));
      }
    };
  }

  /**
   * Composes this natural transformation with another applied before this one.
   *
   * <p>The resulting transformation first applies the {@code before} transformation, then this
   * transformation.
   *
   * @param before the transformation to apply before this one; must not be null
   * @param <E> the initial source witness type
   * @return a composed natural transformation
   */
  default <E extends WitnessArity<TypeArity.Unary>> NaturalTransformation<E, G> compose(
      NaturalTransformation<E, F> before) {
    Validation.function().require(before, "before", COMPOSE);
    return new NaturalTransformation<>() {
      @Override
      public <A> Kind<G, A> apply(Kind<E, A> ea) {
        return NaturalTransformation.this.apply(before.apply(ea));
      }
    };
  }

  /**
   * Returns the identity natural transformation that returns its input unchanged.
   *
   * @param <F> the witness type
   * @return the identity natural transformation
   */
  static <F extends WitnessArity<TypeArity.Unary>> NaturalTransformation<F, F> identity() {
    return new NaturalTransformation<>() {
      @Override
      public <A> Kind<F, A> apply(Kind<F, A> fa) {
        return fa;
      }
    };
  }
}
