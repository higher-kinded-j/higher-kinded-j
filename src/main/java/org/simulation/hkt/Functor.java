package org.simulation.hkt;

import java.util.function.Function;

public interface Functor<F> {
  <A, B> Kind<F, B> map(Function<A, B> f, Kind<F, A> fa);
}
