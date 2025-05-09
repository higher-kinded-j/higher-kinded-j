package org.higherkindedj.hkt.lazy;

import org.higherkindedj.hkt.Kind;

/**
 * A higher-kinded type marker for the {@link Lazy} monad.
 *
 * <p>In the higher-kinded types (HKT) emulation provided by this library, direct type constructor
 * polymorphism (like {@code F<A>}) isn't natively supported in Java. Instead, "witness" types or
 * marker interfaces like {@code LazyKind} are used. This interface allows {@link Lazy} to be
 * treated abstractly in contexts requiring HKTs.
 *
 * <p>This interface, {@code LazyKind<A>}, serves as a "kinded" version of {@code Lazy<A>}. It
 * allows {@code Lazy} to be treated as a type constructor {@code LazyKind<?>} which takes one type
 * argument {@code A} (the type of the lazily computed value).
 *
 * <p>Specifically, when using {@code LazyKind} in generic HKT abstractions:
 *
 * <ul>
 *   <li>The "higher-kinded type witness" (often denoted as {@code F} or {@code Mu} in HKT
 *       literature) becomes {@code LazyKind<?>}. This represents the {@code Lazy} type constructor.
 *   <li>The "value type" (often denoted as {@code A}) is {@code A}, representing the result type of
 *       the computation encapsulated and deferred by the {@code Lazy} instance.
 * </ul>
 *
 * <p>An instance of {@code Kind<LazyKind<?>, A>} can be converted back to a concrete {@code
 * Lazy<A>} using the {@link LazyKindHelper#unwrap(Kind)} method. This helper method handles the
 * necessary type casting, often via an internal representation (like a private record implementing
 * this {@code LazyKind} interface).
 *
 * <p>The {@link Lazy} monad encapsulates a computation that is not executed until its result is
 * explicitly requested (e.g., via a {@code run()} or {@code get()} method on the concrete {@code
 * Lazy} type). This marker interface enables {@code Lazy} to participate in generic monadic
 * abstractions that operate over various HKTs.
 *
 * @param <A> The type of the value that will be lazily computed. This is the type parameter that
 *     varies for the higher-kinded type {@code LazyKind<?>}.
 * @see Lazy
 * @see LazyKindHelper
 * @see LazyKindHelper#unwrap(Kind)
 * @see org.higherkindedj.hkt.Kind
 */
public interface LazyKind<A> extends Kind<LazyKind<?>, A> {
  // This interface is a marker and does not declare additional methods.
  // It signifies that a type is a Kind<F, A> where F is LazyKind<?>
  // (the Lazy type constructor) and A is the lazily computed result type.
  // The concrete Lazy<A> type is typically wrapped by an internal class/record
  // within LazyKindHelper that implements this interface.
}
