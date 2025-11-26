/**
 * Provides the {@link org.higherkindedj.hkt.maybe_t.MaybeT} monad transformer. {@code MaybeT}
 * allows layering {@code Maybe} (or Optional-like) functionality onto another monad, representing a
 * computation that might be absent within an outer monadic context. Includes the transformer
 * itself, its Kind representation, and Monad instance.
 */
@NullMarked
package org.higherkindedj.hkt.maybe_t;

import org.jspecify.annotations.NullMarked;
