package org.higherkindedj.hkt.maybe;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface marker for the Maybe type in Higher-Kinded-J. Represents Maybe as a type
 * constructor 'F' in Kind<F, A>.
 *
 * @param <A> The type of the value potentially held by the Maybe.
 */
public interface MaybeKind<A> extends Kind<MaybeKind<?>, A> {}
