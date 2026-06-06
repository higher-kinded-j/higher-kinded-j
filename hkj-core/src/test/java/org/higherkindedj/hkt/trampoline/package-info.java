/**
 * Test suite for the {@code Trampoline} type and its Higher-Kinded Type components.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineTest} - Core Trampoline operations and
 *       stack safety
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineFunctorTest} - Functor instance, contract
 *       smoke and laws
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineMonadTest} - Monad instance, contract
 *       smoke and laws
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineMonadPropertyTest} - jqwik Functor/Monad
 *       law verification
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineKindHelperTest} - Widen/narrow conversion
 *       tests
 * </ul>
 *
 * <p>Shared fixtures live in {@link org.higherkindedj.hkt.trampoline.TrampolineLawFixtures} and
 * {@link org.higherkindedj.hkt.trampoline.TrampolineArbitraries}.
 */
@NullMarked
package org.higherkindedj.hkt.trampoline;

import org.jspecify.annotations.NullMarked;
