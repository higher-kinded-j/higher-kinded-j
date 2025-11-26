/**
 * Test suite for the {@code Trampoline} type and its Higher-Kinded Type components.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineTest} - Core Trampoline operations and
 *       stack safety
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineMonadTest} - Monad instance tests
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineKindHelperTest} - Widen/narrow conversion
 *       tests
 * </ul>
 */
@NullMarked
package org.higherkindedj.hkt.trampoline;

import org.jspecify.annotations.NullMarked;
