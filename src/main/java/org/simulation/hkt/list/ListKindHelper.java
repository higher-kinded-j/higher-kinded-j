package org.simulation.hkt.list;

import org.simulation.hkt.Kind;

import java.util.Collections;
import java.util.List;

public class ListKindHelper {
  /**
   * returns empty list for null or unknown types instead of throwing
   * @param kind
   * @return
   * @param <A>
   */
  public static <A> List<A> unwrap(Kind<ListKind<?>, A> kind) {
    return switch(kind) {
      case ListHolder<A> holder -> holder.list();
      case null, default -> Collections.emptyList(); // Return default for null or unknown types
    };
  }

  public static <A> ListKind<A> wrap(List<A> list) {
    return new ListHolder<>(list);
  }

  record ListHolder<A>(List<A> list) implements ListKind<A> {
  }
}
