package org.simulation.hkt.optional;



import org.simulation.hkt.Kind;

import java.util.Optional;

public class OptionalKindHelper {

  public static <A> Optional<A> unwrap(Kind<OptionalKind<?>, A> kind){
    return switch(kind) {
      case OptionalHolder<A>  holder -> holder.optional();
      case null -> Optional.empty(); // or throw??
      default -> throw new IllegalArgumentException("Kind instance is not an OptionalHolder: " + kind.getClass().getName());
    };
  }
  public  static <A> OptionalKind<A> wrap(Optional<A> optional){
    return new OptionalHolder<>(optional);
  }


  record OptionalHolder<A>(Optional<A> optional) implements OptionalKind<A> {
  }
}
