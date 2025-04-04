package org.simulation.hkt.list;

import org.simulation.hkt.Kind;

import java.util.Collections;
import java.util.List;

public class ListKindHelper {
  public static <A> List<A> unwrap(Kind<ListKind<?>, A> kind) {
    return switch(kind) {
      case ListHolder<A> holder -> holder.list();
      case null -> Collections.emptyList();
      default -> throw  new IllegalArgumentException("Kind instance is not a ListHolder: " + kind.getClass().getName());
    };
  }

  public static <A> ListKind<A> wrap(List<A> list) {
    return new ListHolder<>(list);
  }

  record ListHolder<A>(List<A> list) implements ListKind<A> {
  }
}
