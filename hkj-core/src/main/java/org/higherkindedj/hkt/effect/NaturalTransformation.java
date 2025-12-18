// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import org.higherkindedj.hkt.Kind;

/**
 * A natural transformation from functor F to functor G.
 *
 * <p>Natural transformations allow converting computations from one effect type to another while
 * preserving structure. This is the categorical concept of a morphism between functors.
 *
 * <h2>Laws</h2>
 *
 * <p>For a natural transformation {@code nt: F ~> G}, the following naturality law must hold for
 * all functions {@code f: A -> B}:
 *
 * <pre>
 * nt.apply(fa.map(f)) == nt.apply(fa).map(f)
 * </pre>
 *
 * <p>In other words, it doesn't matter whether you transform first then map, or map first then
 * transform - the result is the same.
 *
 * <h2>Use Cases</h2>
 *
 * <ul>
 *   <li>Converting between effect types (e.g., Maybe to Either)
 *   <li>Interpreting a DSL into a concrete implementation
 *   <li>Running tests with a different effect type than production
 *   <li>Adapting between library boundaries
 * </ul>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * // Convert Maybe to Either with a default error
 * NaturalTransformation<MaybeKind.Witness, EitherKind.Witness<String>> maybeToEither =
 *     new NaturalTransformation<>() {
 *         @Override
 *         public <A> Kind<EitherKind.Witness<String>, A> apply(Kind<MaybeKind.Witness, A> fa) {
 *             Maybe<A> maybe = MaybeKindHelper.narrow(fa);
 *             return maybe.isJust()
 *                 ? EitherKind.widen(Either.right(maybe.get()))
 *                 : EitherKind.widen(Either.left("Value was Nothing"));
 *         }
 *     };
 *
 * // Use with GenericPath
 * GenericPath<MaybeKind.Witness, User> userPath = ...;
 * GenericPath<EitherKind.Witness<String>, User> eitherPath =
 *     userPath.mapK(maybeToEither, eitherMonad);
 * }</pre>
 *
 * @param <F> the source witness type
 * @param <G> the target witness type
 */
@FunctionalInterface
public interface NaturalTransformation<F, G> {

  /**
   * Applies this natural transformation to a Kind value.
   *
   * @param fa the source Kind value; must not be null
   * @param <A> the value type
   * @return the transformed Kind value
   */
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
  default <H> NaturalTransformation<F, H> andThen(NaturalTransformation<G, H> after) {
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
  default <E> NaturalTransformation<E, G> compose(NaturalTransformation<E, F> before) {
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
  static <F> NaturalTransformation<F, F> identity() {
    return new NaturalTransformation<>() {
      @Override
      public <A> Kind<F, A> apply(Kind<F, A> fa) {
        return fa;
      }
    };
  }
}
