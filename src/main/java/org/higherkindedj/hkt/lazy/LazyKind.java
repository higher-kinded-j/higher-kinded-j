package org.higherkindedj.hkt.lazy;

import org.higherkindedj.hkt.Kind;

/** Kind marker for Lazy<A>. Witness F = LazyKind<?> Value A = A */
public interface LazyKind<A> extends Kind<LazyKind<?>, A> {}
