package org.simulation.hkt.maybe;

import org.simulation.hkt.Kind;

public class MaybeKindHelper {

  public static <A> Maybe<A> unwrap(Kind<MaybeKind<?>, A> kind) {
    return switch(kind) {
      case MaybeHolder<A> holder -> holder.maybe();
      case null -> Maybe.nothing();
      default -> throw new IllegalArgumentException("Kind instance is not a MaybeHolder: " + kind.getClass().getName());
    };
  }

  public static <A> MaybeKind<A> wrap(Maybe<A> maybe) {
    return new MaybeHolder<>(maybe);
  }

  record MaybeHolder<A>(Maybe<A> maybe) implements MaybeKind<A> { }
}
