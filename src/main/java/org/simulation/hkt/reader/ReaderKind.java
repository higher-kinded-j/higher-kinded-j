package org.simulation.hkt.reader;

import org.simulation.hkt.Kind;

/**
 * Kind marker for Reader<R, A>.
 * Witness F = ReaderKind<R, ?>
 * Value A = A
 */
public interface ReaderKind<R, A> extends Kind<ReaderKind<R, ?>, A> {
}
