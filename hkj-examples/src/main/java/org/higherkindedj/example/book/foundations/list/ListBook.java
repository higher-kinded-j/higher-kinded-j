// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.list;

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/list_monad.html">List</a> page: every way
 * to ship an order within a budget, found by branching on each courier's services; every variant of
 * a lamp from its colours and shades; lines paired with their dispatch days by position; and the
 * instance over {@code Kind}. The page {@code {{#include}}}s the anchored regions, {@code
 * ListBookTest} holds the claims the page makes, and the book's output gate runs {@link #main}
 * against the comments in the regions.
 */
public final class ListBook {

  private ListBook() {}

  // ANCHOR: services
  // A courier's service, and what it charges
  record Service(String courier, String name, BigDecimal price) {}

  static final List<String> COURIERS = List.of("Parcelforce", "DPD");

  // Each courier offers several services
  static List<Service> servicesOf(String courier) {
    return switch (courier) {
      case "Parcelforce" ->
          List.of(
              new Service(courier, "standard", new BigDecimal("4.99")),
              new Service(courier, "express", new BigDecimal("9.99")));
      case "DPD" ->
          List.of(
              new Service(courier, "next day", new BigDecimal("7.49")),
              new Service(courier, "timed", new BigDecimal("12.99")));
      default -> List.of();
    };
  }

  // ANCHOR_END: services

  // ANCHOR: options
  // Every service from every courier, and only those within the budget
  static NonDetPath<Service> optionsWithin(BigDecimal budget) {
    return Path.list(COURIERS)
        .via(courier -> Path.list(servicesOf(courier)))
        .filter(service -> service.price().compareTo(budget) <= 0);
  }

  // ANCHOR_END: options

  public static void main(String[] args) {
    // ANCHOR: options_run
    List<String> underTen =
        optionsWithin(new BigDecimal("10.00")).map(s -> s.courier() + " " + s.name()).run();
    // [Parcelforce standard, Parcelforce express, DPD next day]
    // ANCHOR_END: options_run
    System.out.println(underTen);

    // ANCHOR: variants
    // Every colour with every shade
    List<String> variants =
        Path.list("black", "brass")
            .zipWith(Path.list("linen", "glass"), (colour, shade) -> colour + "/" + shade)
            .run();
    // [black/linen, black/glass, brass/linen, brass/glass]
    // ANCHOR_END: variants
    System.out.println(variants);

    // ANCHOR: by_position
    // Each line with the day it is dispatched, position by position
    List<String> dispatched =
        Path.listPath("LAMP", "BULB", "SHADE")
            .zipWith(Path.listPath("Monday", "Tuesday"), (sku, day) -> sku + " on " + day)
            .run();
    // [LAMP on Monday, BULB on Tuesday]
    // ANCHOR_END: by_position
    System.out.println(dispatched);

    // ANCHOR: kind
    MonadZero<ListKind.Witness> lists = Instances.monadZero(list());

    Kind<ListKind.Witness, String> couriers = LIST.widen(COURIERS);
    Function<String, Kind<ListKind.Witness, Service>> services =
        courier -> LIST.widen(servicesOf(courier));

    int offered = LIST.narrow(lists.flatMap(services, couriers)).size();
    // 4
    // ANCHOR_END: kind
    System.out.println(offered);
  }
}
