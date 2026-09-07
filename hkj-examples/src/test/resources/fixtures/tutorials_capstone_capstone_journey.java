// Fixture for hkj-book/src/tutorials/capstone/capstone_journey.md
//
// The capstone quotes the chapter anchor expression grown up. The order it walks and the lens it
// modifies through are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import java.util.List;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.optics.Lens;

record LineItem(String sku, int quantity) {}

record Order(String id, List<LineItem> items) {}

enum OrderError {
  NOT_FOUND
}

class Fixture {

  static final String id = "ORD-1";

  static final Lens<Order, List<LineItem>> itemsLens =
      Lens.of(Order::items, (o, v) -> new Order(o.id(), v));

  static MaybePath<Order> findOrder(String id) {
    return Path.nothing();
  }

  List<LineItem> recompute(List<LineItem> items) {
    return items;
  }

  EitherPath<OrderError, Order> reserveInventory(Order order) {
    return Path.right(order);
  }

  EitherPath<OrderError, Order> save(Order order) {
    return Path.right(order);
  }
}
