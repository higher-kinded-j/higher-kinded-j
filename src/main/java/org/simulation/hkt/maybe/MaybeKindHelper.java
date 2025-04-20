package org.simulation.hkt.maybe;

import org.simulation.hkt.Kind;

public class MaybeKindHelper {

  public static <A> Maybe<A> unwrap(Kind<MaybeKind<?>, A> kind) {
      // Now returns Maybe.nothing() for null or unknown types instead of throwing
      return switch(kind) {
          case MaybeHolder<A> holder -> holder.maybe();
          case null, default -> Maybe.nothing(); // Return default for null or unknown types
      };
  }

  public static <A> MaybeKind<A> wrap(Maybe<A> maybe) {
    return new MaybeHolder<>(maybe);
  }


 public static <A> Kind<MaybeKind<?>, A> just(A value) {
    // Maybe.just throws if value is null
    return wrap(Maybe.just(value));
  }

  public static <A> Kind<MaybeKind<?>, A> nothing() {
    return wrap(Maybe.nothing());
  }

  record MaybeHolder<A>(Maybe<A> maybe) implements MaybeKind<A> { }
}
