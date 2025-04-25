package org.simulation.hkt.lazy;

import org.simulation.hkt.Kind;

/**
 * Kind marker for Lazy<A>. Witness F = LazyKind<?> Value A = A
 */
public interface LazyKind<A> extends Kind<LazyKind<?>, A> {}
