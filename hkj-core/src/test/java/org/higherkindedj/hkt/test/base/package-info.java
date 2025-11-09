/**
 * Base classes for standardised type class testing.
 *
 * <p>Provides foundational classes that reduce boilerplate and ensure consistency:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.test.base.TypeClassTestBase} - Base class with standard
 *       fixture setup
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * public class MyMonadTest extends TypeClassTestBase<F, Integer, String> {
 *     @Override
 *     protected Kind<F, Integer> createValidKind() {
 *         return HELPER.widen(MyMonad.of(42));
 *     }
 *     // ... implement other abstract methods
 * }
 * }</pre>
 *
 * @see org.higherkindedj.hkt.test.patterns
 * @see org.higherkindedj.hkt.test.assertions
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.hkt.test.base;
