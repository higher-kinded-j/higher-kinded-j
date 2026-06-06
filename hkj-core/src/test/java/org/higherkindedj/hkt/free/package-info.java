/**
 * Test suite for the {@code Free} monad and its Higher-Kinded Type components.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.free.FreeMonadTest} - Monad instance, contract smoke and laws
 *   <li>{@link org.higherkindedj.hkt.free.FreeMonadPropertyTest} - jqwik Functor/Monad law
 *       verification over random {@code Free} programs
 *   <li>{@link org.higherkindedj.hkt.free.FreeFactoryTest} - {@link
 *       org.higherkindedj.hkt.free.FreeFactory} type-inference helpers
 *   <li>{@link org.higherkindedj.hkt.free.FreeKindHelperTest} - Widen/narrow conversion tests
 *   <li>{@link org.higherkindedj.hkt.free.FreeApTest} - Applicative {@code Ap} sub-expression
 *       interpretation
 *   <li>{@link org.higherkindedj.hkt.free.FreeHandleErrorTest} - Typed error recovery
 *   <li>{@link org.higherkindedj.hkt.free.FreeTranslateTest} - Natural-transformation translation
 *   <li>{@link org.higherkindedj.hkt.free.FreeFunctionFoldMapTest} - Function-based {@code foldMap}
 *       coverage
 *   <li>{@link org.higherkindedj.hkt.free.ProgramAnalyserTest} - Static program analysis
 * </ul>
 *
 * <p>Shared fixtures live in {@link org.higherkindedj.hkt.free.FreeLawFixtures} and {@link
 * org.higherkindedj.hkt.free.FreeArbitraries}.
 */
@NullMarked
package org.higherkindedj.hkt.free;

import org.jspecify.annotations.NullMarked;
