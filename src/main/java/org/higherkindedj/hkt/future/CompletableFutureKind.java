package org.higherkindedj.hkt.future;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the CompletableFuture type in Higher-Kinded-J. Represents
 * CompletableFuture as a type constructor 'F' in {@code Kind<F, A>}.
 *
 * @param <A> The type of the value potentially held by the CompletableFuture.
 */
public interface CompletableFutureKind<A> extends Kind<CompletableFutureKind<?>, A> {
  // Witness type F = CompletableFutureKind<?>
  // Value type A = A
}
