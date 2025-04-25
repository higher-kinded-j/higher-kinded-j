package org.simulation.hkt.maybe;

import org.simulation.hkt.Kind;

/**
 * Kind interface marker for the Maybe type in the HKT simulation. Represents Maybe as a type
 * constructor 'F' in Kind<F, A>.
 *
 * @param <A> The type of the value potentially held by the Maybe.
 */
public interface MaybeKind<A> extends Kind<MaybeKind<?>, A> {}
