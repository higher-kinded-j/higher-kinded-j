/**
 * Provides the {@link org.higherkindedj.hkt.eitherorboth.EitherOrBoth} type and its simulation as a
 * Higher-Kinded Type. {@code EitherOrBoth} is an inclusive-or (known elsewhere as {@code Ior} or
 * {@code These}): a value that is a {@code Left}, a {@code Right}, or {@code Both} at once, used to
 * model "success that also carries accumulated, non-fatal warnings".
 *
 * <p>The {@code Left}, {@code Right} and {@code Both} records directly implement {@link
 * org.higherkindedj.hkt.eitherorboth.EitherOrBothKind} and {@link
 * org.higherkindedj.hkt.eitherorboth.EitherOrBothKind2}, so {@code widen}/{@code narrow} are
 * cast-free upcasts rather than object wrapping.
 *
 * <p>Includes {@link org.higherkindedj.hkt.eitherorboth.EitherOrBoth}, {@link
 * org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper}, and the right-biased {@link
 * org.higherkindedj.hkt.eitherorboth.EitherOrBothFunctor}/{@link
 * org.higherkindedj.hkt.eitherorboth.EitherOrBothMonad}/{@link
 * org.higherkindedj.hkt.eitherorboth.EitherOrBothTraverse} instances plus the {@link
 * org.higherkindedj.hkt.eitherorboth.EitherOrBothBifunctor}.
 */
@NullMarked
package org.higherkindedj.hkt.eitherorboth;

import org.jspecify.annotations.NullMarked;
