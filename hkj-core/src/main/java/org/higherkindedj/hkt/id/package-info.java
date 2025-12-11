/**
 * Provides components for the {@code Id} (Identity) monad and its simulation as a Higher-Kinded
 * Type. The Identity monad is the simplest monad, used for wrapping pure values without adding
 * computational context.
 *
 * <h2>Key Components</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.id.Id} - The Identity container holding exactly one value
 *   <li>{@link org.higherkindedj.hkt.id.IdKind} - Kind interface marker for HKT simulation
 *   <li>{@link org.higherkindedj.hkt.id.IdKindHelper} - Widen/narrow operations
 *   <li>{@link org.higherkindedj.hkt.id.IdMonad} - Monad instance for Id
 *   <li>{@link org.higherkindedj.hkt.id.IdTraverse} - Traverse instance for Id
 * </ul>
 *
 * <h2>Exactly-One Semantics</h2>
 *
 * <p>The Identity monad has "exactly one" cardinality - it always contains precisely one value.
 * This makes it useful as a base case for:
 *
 * <ul>
 *   <li>Monad transformers (e.g., {@code StateT<S, IdKind.Witness, A>} ≡ {@code State<S, A>})
 *   <li>Generic programming where no additional effects are needed
 *   <li>Testing higher-kinded abstractions with the simplest possible context
 * </ul>
 */
@NullMarked
package org.higherkindedj.hkt.id;

import org.jspecify.annotations.NullMarked;
