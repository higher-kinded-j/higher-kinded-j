package org.higherkindedj.hkt.io;

import org.higherkindedj.hkt.Kind;

public interface IOKind<A> extends Kind<IOKind<?>, A> {
  // Witness F = IOKind<?>
  // Value A = A
}
