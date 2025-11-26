/**
 * Test suite for {@link java.util.stream.Stream} as a Higher-Kinded Type.
 *
 * <p>These tests verify that Stream operations maintain proper lazy evaluation semantics while
 * adhering to the type class laws (Functor, Applicative, Monad).
 *
 * @see org.higherkindedj.hkt.stream.StreamKind
 * @see org.higherkindedj.hkt.stream.StreamMonad
 */
@NullMarked
package org.higherkindedj.hkt.stream;

import org.jspecify.annotations.NullMarked;
