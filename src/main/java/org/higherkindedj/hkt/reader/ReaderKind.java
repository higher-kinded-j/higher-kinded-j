package org.higherkindedj.hkt.reader;

import org.higherkindedj.hkt.Kind;

/** Kind marker for Reader<R, A>. Witness F = ReaderKind<R, ?> Value A = A */
public interface ReaderKind<R, A> extends Kind<ReaderKind<R, ?>, A> {}
