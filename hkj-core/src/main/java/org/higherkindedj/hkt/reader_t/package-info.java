/**
 * Provides the {@link org.higherkindedj.hkt.reader_t.ReaderT} monad transformer. {@code ReaderT}
 * allows layering {@code Reader} functionality (dependency injection) onto another monad, enabling
 * computations that depend on a shared environment within an outer monadic context. Includes the
 * transformer itself, its Kind representation, and Monad instance.
 */
@NullMarked
package org.higherkindedj.hkt.reader_t;

import org.jspecify.annotations.NullMarked;
