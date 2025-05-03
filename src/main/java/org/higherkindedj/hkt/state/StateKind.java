package org.higherkindedj.hkt.state;

import org.higherkindedj.hkt.Kind;

/**
 * Kind marker for {@code State<S, A>}. Witness F = {@code StateKind<S, ?>} (S is fixed for a specific Monad
 * instance) Value A = A
 *
 * @param <S> The fixed state type for this Kind instance.
 * @param <A> The value type produced by the state computation.
 */
public interface StateKind<S, A> extends Kind<StateKind<S, ?>, A> {}
