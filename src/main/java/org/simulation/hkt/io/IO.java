package org.simulation.hkt.io;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.Function;

@FunctionalInterface
public interface IO<A> {
  A unsafeRunSync();

  // Static factory to create an IO from a Supplier (lazy)
  static <A> IO<A> delay(Supplier<A> thunk) {
    Objects.requireNonNull(thunk, "Supplier (thunk) cannot be null for IO.delay");

    // The lambda itself is the implementation of unsafeRunSync
    return () -> thunk.get();
  }

  default <B> IO<B> map(Function<? super A, ? extends B> f) {
    // IO(() -> f.apply(this.unsafeRunSync()))
    return IO.delay(() -> f.apply(this.unsafeRunSync()));
  }

  default <B> IO<B> flatMap(Function<? super A, ? extends IO<B>> f) {
    // IO(() -> f.apply(this.unsafeRunSync()).unsafeRunSync())
    return IO.delay(() -> f.apply(this.unsafeRunSync()).unsafeRunSync());
  }
}
