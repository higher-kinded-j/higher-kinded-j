/**
 * Provides components for the {@code Trampoline} type and its simulation as a Higher-Kinded Type.
 * {@code Trampoline} enables stack-safe recursion by converting recursive calls into iterative data
 * structure processing, preventing {@link StackOverflowError} in deeply recursive computations.
 *
 * <p>Key components:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.trampoline.Trampoline} - Core sealed interface representing
 *       stack-safe computations
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineKind} - Higher-kinded type marker
 *       interface
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineKindHelper} - Widen/narrow conversion
 *       operations
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineFunctor} - Functor instance for
 *       Trampoline
 *   <li>{@link org.higherkindedj.hkt.trampoline.TrampolineMonad} - Monad instance for Trampoline
 * </ul>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * // Stack-safe factorial
 * Trampoline<BigInteger> factorial(BigInteger n, BigInteger acc) {
 *     if (n.compareTo(BigInteger.ZERO) <= 0) {
 *         return Trampoline.done(acc);
 *     }
 *     return Trampoline.defer(() ->
 *         factorial(n.subtract(BigInteger.ONE), n.multiply(acc))
 *     );
 * }
 *
 * // Safe for very large numbers
 * BigInteger result = factorial(BigInteger.valueOf(100000), BigInteger.ONE).run();
 * }</pre>
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.hkt.trampoline;
