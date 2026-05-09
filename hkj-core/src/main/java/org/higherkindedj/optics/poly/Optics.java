// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.poly;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.optics.Optic;
import org.jspecify.annotations.NullMarked;

/**
 * Typeclass-driven factories for polymorphic optics.
 *
 * <p>These factories turn an HKJ {@link Functor} or {@link Traverse} instance into a polymorphic
 * optic over its contents. They are the one place in the optics surface where polymorphism gives us
 * something the monomorphic optics cannot: composing a leaf step that <em>changes element type</em>
 * with the existing {@code Lens} / {@code Prism} / {@code Iso} / {@code Affine} / {@code Traversal}
 * heads, via {@link Optic#andThen}.
 *
 * <h2>Quick Tour</h2>
 *
 * <pre>{@code
 * // mapped: a polymorphic Setter into the elements of any unary Functor.
 * // Use with PolyOptics.modify / set (i.e. under the Identity applicative).
 * Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> elems =
 *     Optics.mapped(ListFunctor.INSTANCE);
 *
 * Kind<ListKind.Witness, Integer> ints =
 *     PolyOptics.modify(elems, Integer::parseInt, LIST.widen(List.of("1", "2", "3")));
 *
 * // traversed: a polymorphic Traversal into the elements of any unary Traverse.
 * // Works under any Applicative; here we accumulate validation errors.
 * Optic<Kind<ListKind.Witness, String>, Kind<ListKind.Witness, Integer>, String, Integer> trav =
 *     Optics.traversed(ListTraverse.INSTANCE);
 *
 * Kind<ValidatedKind.Witness<List<String>>, Kind<ListKind.Witness, Integer>> result =
 *     PolyOptics.modifyF(trav, parseValidated, LIST.widen(input), validatedApp);
 * }</pre>
 *
 * @see PolyOptics low-level factories and runners over the raw {@link Optic} interface
 */
@NullMarked
public final class Optics {

  private Optics() {}

  /**
   * Returns a polymorphic optic into the elements of any unary {@link Functor}.
   *
   * <p>The returned optic is a <b>polymorphic Setter</b>: it supports pure type-changing
   * modification under the Identity applicative ({@link PolyOptics#modify} and {@link
   * PolyOptics#set}). It does <em>not</em> support arbitrary effectful traversal; if you need to
   * thread an effect through the elements, use {@link #traversed(Traverse)} instead.
   *
   * <p>The optic composes with the monomorphic optics in {@link org.higherkindedj.optics} via
   * {@link Optic#andThen}, allowing a monomorphic head ({@code Lens<User, List<String>>}, say) to
   * be followed by this polymorphic leaf to express "map every element, possibly changing type".
   *
   * @param functor the {@link Functor} instance for {@code F}
   * @param <F> the unary HKT witness
   * @param <A> the original element type
   * @param <B> the resulting element type
   * @return a polymorphic Setter into the elements of {@code F}
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B>
      Optic<Kind<F, A>, Kind<F, B>, A, B> mapped(Functor<F> functor) {
    return new Optic<>() {
      @Override
      public <G extends WitnessArity<TypeArity.Unary>> Kind<G, Kind<F, B>> modifyF(
          Function<A, Kind<G, B>> f, Kind<F, A> source, Applicative<G> app) {
        // mapped is a polymorphic Setter: it requires the Identity applicative.
        // Under Identity, f returns Kind<IdKind.Witness, B> = Id<B>, so we can recover B
        // by narrowing. For any other Applicative the optic is not sound; we raise a clear error.
        Function<A, B> pure =
            a -> {
              Kind<G, B> gb = f.apply(a);
              if (!(gb instanceof Id<?> id)) {
                throw new UnsupportedOperationException(
                    "Optics.mapped(Functor) is a polymorphic Setter and only supports pure "
                        + "modification under IdMonad (use PolyOptics.modify or PolyOptics.set). "
                        + "For effectful traversal, use Optics.traversed(Traverse) with a Traverse "
                        + "instance for "
                        + functor.getClass().getSimpleName()
                        + ".");
              }
              @SuppressWarnings("unchecked")
              B b = (B) id.value();
              return b;
            };
        Kind<F, B> mappedF = functor.map(pure, source);
        return app.of(mappedF);
      }
    };
  }

  /**
   * Returns a polymorphic optic into the elements of any unary {@link Traverse}.
   *
   * <p>This is a <b>polymorphic Traversal</b>: it can run under any {@link Applicative}, so it
   * supports both pure modification (via {@link PolyOptics#modify}) and effectful traversal (via
   * {@link PolyOptics#modifyF}). Common applicatives include {@code Validated} for error
   * accumulation, {@code Either} for short-circuiting, and {@code IdMonad} for pure mapping.
   *
   * <p>The optic composes with the monomorphic optics in {@link org.higherkindedj.optics} via
   * {@link Optic#andThen}, allowing a monomorphic head to be followed by this polymorphic leaf.
   *
   * @param traverse the {@link Traverse} instance for {@code F}
   * @param <F> the unary HKT witness
   * @param <A> the original element type
   * @param <B> the resulting element type
   * @return a polymorphic Traversal into the elements of {@code F}
   */
  public static <F extends WitnessArity<TypeArity.Unary>, A, B>
      Optic<Kind<F, A>, Kind<F, B>, A, B> traversed(Traverse<F> traverse) {
    return new Optic<>() {
      @Override
      public <G extends WitnessArity<TypeArity.Unary>> Kind<G, Kind<F, B>> modifyF(
          Function<A, Kind<G, B>> f, Kind<F, A> source, Applicative<G> app) {
        return traverse.traverse(app, f::apply, source);
      }
    };
  }
}
