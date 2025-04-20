package org.simulation.hkt.optional;



import org.simulation.hkt.Kind;

import java.util.Optional;

public class OptionalKindHelper {

  public static <A> Optional<A> unwrap(Kind<OptionalKind<?>, A> kind){
    // Now returns Optional.empty() for null or unknown types instead of throwing
    return switch(kind) {
      case OptionalHolder<A> holder -> holder.optional();
      case null, default -> Optional.empty(); // Return default for null or unknown types
    };
  }

  public  static <A> OptionalKind<A> wrap(Optional<A> optional){
    return new OptionalHolder<>(optional);
  }


  record OptionalHolder<A>(Optional<A> optional) implements OptionalKind<A> {
  }
}
