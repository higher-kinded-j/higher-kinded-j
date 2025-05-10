package org.higherkindedj.hkt.future;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the {@link java.util.concurrent.CompletableFuture} type in
 * Higher-Kinded-J. Represents CompletableFuture as a type constructor 'F' in {@code Kind<F, A>}.
 * The witness type F is {@link CompletableFutureKind.Witness}.
 *
 * @param <A> The type of the value potentially held by the CompletableFuture.
 */
public interface CompletableFutureKind<A> extends Kind<CompletableFutureKind.Witness, A> {

  /**
   * The phantom type marker (witness type) for the CompletableFuture type constructor. This is used
   * as the 'F' in {@code Kind<F, A>} for CompletableFuture.
   */
  final class Witness {
    // Private constructor to prevent instantiation of the witness type itself.
    // Its purpose is purely for type-level representation.
    private Witness() {}
  }
}
