package org.simulation.hkt.io;

import org.simulation.hkt.Kind;

public interface IOKind<A> extends Kind<IOKind<?>, A> {
  // Witness F = IOKind<?>
  // Value A = A
}