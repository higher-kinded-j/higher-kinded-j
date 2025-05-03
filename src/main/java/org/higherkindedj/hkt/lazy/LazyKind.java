package org.higherkindedj.hkt.lazy;

import org.higherkindedj.hkt.Kind;

/** Kind marker for {@code Lazy<A>}. Witness F = {@code LazyKind<?>} Value A = A */
public interface LazyKind<A> extends Kind<LazyKind<?>, A> {}
