/**
 * Provides {@link org.higherkindedj.hkt.eitherf.EitherF}, a sum type for composing effect algebras
 * at the type constructor level.
 *
 * <p>{@code EitherF<F, G, A>} represents an instruction that is either from effect set {@code F} or
 * effect set {@code G}. This is {@code Either} lifted to the functor/effect level, following the
 * established {@code modifyF} naming convention where the {@code F} suffix means "lifted to the
 * functor level."
 *
 * <p>Includes {@link org.higherkindedj.hkt.eitherf.EitherF}, {@link
 * org.higherkindedj.hkt.eitherf.EitherFKind}, {@link
 * org.higherkindedj.hkt.eitherf.EitherFKindHelper}, and {@link
 * org.higherkindedj.hkt.eitherf.EitherFFunctor}.
 */
@NullMarked
package org.higherkindedj.hkt.eitherf;

import org.jspecify.annotations.NullMarked;
