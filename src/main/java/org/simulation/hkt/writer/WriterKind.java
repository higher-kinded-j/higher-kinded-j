package org.simulation.hkt.writer;

import org.simulation.hkt.Kind;

/**
 * Kind marker for Writer<W, A>.
 * Witness F = WriterKind<W, ?> (W is fixed by the Monad instance)
 * Value A = A
 */
public interface WriterKind<W, A> extends Kind<WriterKind<W, ?>, A> {
}