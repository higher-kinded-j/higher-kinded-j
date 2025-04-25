package org.simulation.hkt.lazy;

import static org.simulation.hkt.lazy.LazyKindHelper.*;

import java.util.function.Function;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Applicative;
import org.simulation.hkt.Functor;
import org.simulation.hkt.Kind;
import org.simulation.hkt.Monad;

public class LazyMonad
    implements Monad<LazyKind<?>>, Applicative<LazyKind<?>>, Functor<LazyKind<?>> {

  // Functor map
  @Override
  public <A, B> @NonNull Kind<LazyKind<?>, B> map(
      @NonNull Function<A, B> f, @NonNull Kind<LazyKind<?>, A> fa) {
    Lazy<A> lazyA = unwrap(fa);
    Lazy<B> lazyB = lazyA.map(f); // Use Lazy's map
    return wrap(lazyB);
  }

  // Applicative of/pure
  @Override
  public <A> @NonNull Kind<LazyKind<?>, A> of(@Nullable A value) {
    // 'of'/'pure' creates a Lazy that is already evaluated
    return wrap(Lazy.now(value));
  }

  // Applicative ap
  @Override
  public <A, B> @NonNull Kind<LazyKind<?>, B> ap(
      @NonNull Kind<LazyKind<?>, Function<A, B>> ff, @NonNull Kind<LazyKind<?>, A> fa) {
    Lazy<Function<A, B>> lazyF = unwrap(ff);
    Lazy<A> lazyA = unwrap(fa);

    // Defer the application: force F, force A, then apply
    Lazy<B> lazyB = Lazy.defer(() -> lazyF.force().apply(lazyA.force()));
    return wrap(lazyB);
  }

  // Monad flatMap
  @Override
  public <A, B> @NonNull Kind<LazyKind<?>, B> flatMap(
      @NonNull Function<A, Kind<LazyKind<?>, B>> f, @NonNull Kind<LazyKind<?>, A> ma) {
    Lazy<A> lazyA = unwrap(ma);

    // Adapt the function for Lazy's flatMap
    Lazy<B> lazyB = lazyA.flatMap(a -> unwrap(f.apply(a)));
    return wrap(lazyB);
  }
}
