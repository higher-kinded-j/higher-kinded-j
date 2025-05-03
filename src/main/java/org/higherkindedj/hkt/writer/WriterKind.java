package org.higherkindedj.hkt.writer;

import org.higherkindedj.hkt.Kind;

/**
 * Kind marker for {@code Writer<W, A>}. Witness F = {@code WriterKind<W, ?>} (W is fixed by the Monad instance)
 * Value A = A
 *
 */
public interface WriterKind<W, A> extends Kind<WriterKind<W, ?>, A> {}
