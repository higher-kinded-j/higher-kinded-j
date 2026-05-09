// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.poly;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;

import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.optics.Optic;
import org.jspecify.annotations.NullMarked;

/**
 * Factories and runners for polymorphic ({@code S, T, A, B}) optics over the existing {@link Optic}
 * interface.
 *
 * <p>Most users should keep using the monomorphic {@code Lens}, {@code Prism}, {@code Iso}, {@code
 * Affine}, {@code Traversal}, and {@code Setter} types in {@link org.higherkindedj.optics}. Reach
 * for {@link PolyOptics} only when you need a type-changing update, typically when authoring a
 * generic wrapper or container, or when composing in a leaf step that changes element type.
 *
 * <h2>Quick Tour</h2>
 *
 * <pre>{@code
 * // Generic wrapper
 * record Box<A>(A value) {}
 *
 * Optic<Box<String>, Box<Integer>, String, Integer> contents =
 *     PolyOptics.polyLens(Box::value, (b, n) -> new Box<>(n));
 *
 * Box<Integer> bi = PolyOptics.modify(contents, Integer::parseInt, new Box<>("42"));
 * // bi == new Box<>(42)
 *
 * String s = PolyOptics.get(contents, new Box<>("hello"));
 * // s == "hello"
 * }</pre>
 *
 * @see Optics typeclass-driven polymorphic factories ({@code mapped}, {@code traversed})
 */
@NullMarked
public final class PolyOptics {

  private PolyOptics() {}

  // ---------------------------------------------------------------------------------------------
  // Construction
  // ---------------------------------------------------------------------------------------------

  /**
   * Creates a lens-shaped polymorphic optic from a getter and a type-changing setter.
   *
   * <p>The setter receives the original source {@code S} together with a new part {@code B} and
   * returns a new whole {@code T}. This lets the part change type and, in turn, the whole.
   *
   * @param get function extracting the focused part {@code A} from the source {@code S}
   * @param set function building a new whole {@code T} from the original source and a new part
   * @param <S> the original whole type
   * @param <T> the resulting whole type
   * @param <A> the original part type
   * @param <B> the resulting part type
   * @return a polymorphic optic with lens semantics
   */
  public static <S, T, A, B> Optic<S, T, A, B> polyLens(
      Function<? super S, ? extends A> get, BiFunction<? super S, ? super B, ? extends T> set) {
    return new Optic<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, T> modifyF(
          Function<A, Kind<F, B>> f, S source, Applicative<F> app) {
        Kind<F, B> fb = f.apply(get.apply(source));
        return app.map(b -> set.apply(source, b), fb);
      }
    };
  }

  /**
   * Creates an isomorphism-shaped polymorphic optic from a forward and a reverse conversion.
   *
   * <p>An iso represents a lossless, two-way conversion that may change types. The forward
   * direction extracts the part {@code A}; the reverse direction reconstructs the whole {@code T}
   * from a new part {@code B} <em>without</em> referring to the original source.
   *
   * @param get forward conversion {@code S -> A}
   * @param reverseGet reverse conversion {@code B -> T}
   * @param <S> the original whole type
   * @param <T> the resulting whole type
   * @param <A> the original part type
   * @param <B> the resulting part type
   * @return a polymorphic optic with iso semantics
   */
  public static <S, T, A, B> Optic<S, T, A, B> polyIso(
      Function<? super S, ? extends A> get, Function<? super B, ? extends T> reverseGet) {
    return new Optic<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, T> modifyF(
          Function<A, Kind<F, B>> f, S source, Applicative<F> app) {
        Kind<F, B> fb = f.apply(get.apply(source));
        return app.map(reverseGet::apply, fb);
      }
    };
  }

  // ---------------------------------------------------------------------------------------------
  // Runners
  // ---------------------------------------------------------------------------------------------

  /**
   * Modifies the focused part using an effectful function under any {@link Applicative}.
   *
   * <p>This is the most general runner; the specialised {@link #modify}, {@link #set}, and {@link
   * #get} runners delegate to it through {@link IdMonad} or a Const applicative.
   *
   * @param optic the polymorphic optic
   * @param f effectful transformation {@code A -> Kind<F, B>}
   * @param source the source whole
   * @param applicative the {@link Applicative} for context {@code F}
   * @param <F> the applicative witness
   * @param <S> the original whole type
   * @param <T> the resulting whole type
   * @param <A> the original part type
   * @param <B> the resulting part type
   * @return the resulting whole {@code T} wrapped in {@code F}
   */
  public static <F extends WitnessArity<TypeArity.Unary>, S, T, A, B> Kind<F, T> modifyF(
      Optic<S, T, A, B> optic, Function<A, Kind<F, B>> f, S source, Applicative<F> applicative) {
    return optic.modifyF(f, source, applicative);
  }

  /**
   * Modifies the focused part with a pure function, returning the resulting whole.
   *
   * <p>Internally runs {@link #modifyF} under {@link IdMonad}.
   *
   * @param optic the polymorphic optic
   * @param f pure transformation {@code A -> B}
   * @param source the source whole
   * @return the resulting whole with the part transformed
   */
  public static <S, T, A, B> T modify(
      Optic<S, T, A, B> optic, Function<? super A, ? extends B> f, S source) {
    Function<A, Kind<IdKind.Witness, B>> lifted = a -> Id.of(f.apply(a));
    Kind<IdKind.Witness, T> result = optic.modifyF(lifted, source, IdMonad.instance());
    return ID.narrow(result).value();
  }

  /**
   * Sets the focused part to a new value, returning the resulting whole.
   *
   * <p>Equivalent to {@code modify(optic, ignored -> value, source)}.
   *
   * @param optic the polymorphic optic
   * @param value the new value for the focused part
   * @param source the source whole
   * @return the resulting whole with the part replaced
   */
  public static <S, T, A, B> T set(Optic<S, T, A, B> optic, B value, S source) {
    return modify(optic, ignored -> value, source);
  }

  /**
   * Extracts the focused part from a lens-shaped polymorphic optic.
   *
   * <p>This runner is <b>lens-like only</b>; it works with optics built by {@link #polyLens} and
   * {@link #polyIso}, and with their compositions. If the supplied optic has prism or traversal
   * shape (it can fail to focus, or focuses on more than one part), an {@link
   * UnsupportedOperationException} is raised pointing the caller at {@link #modifyF}.
   *
   * @param optic the polymorphic optic
   * @param source the source whole
   * @return the focused part
   * @throws UnsupportedOperationException if the optic has non-lens shape
   */
  public static <S, T, A, B> A get(Optic<S, T, A, B> optic, S source) {
    Applicative<PolyConst.Witness<A>> capture = PolyConst.applicative();
    Function<A, Kind<PolyConst.Witness<A>, B>> f = PolyConst::new;
    Kind<PolyConst.Witness<A>, T> result = optic.modifyF(f, source, capture);
    return PolyConst.narrow(result).value();
  }
}
