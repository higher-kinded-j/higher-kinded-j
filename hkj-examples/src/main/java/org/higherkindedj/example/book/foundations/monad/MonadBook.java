// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.monad;

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/functional/monad.html">Monad</a> page: {@code
 * markPaid}, one chain written once over any container, run with {@code Either}'s and {@code IO}'s
 * instances, then the helpers {@code Monad} adds. The page {@code {{#include}}}s the anchored
 * regions, {@code MonadBookTest} holds what the page says each instance does, and the book's output
 * gate runs {@link #main} against the comments in the regions.
 */
public final class MonadBook {

  /** An order that has not been paid for yet. */
  static final Order ORDER =
      new Order(
          UUID.fromString("00000000-0000-0000-0000-000000000001"),
          new Customer("Ada", new EmailAddress("ada@example.com")),
          List.of(new LineItem("LAMP", 1, new BigDecimal("40.00"))),
          Instant.parse("2026-10-01T09:00:00Z"),
          Currency.getInstance("GBP"),
          OrderStatus.NEW);

  private MonadBook() {}

  // ANCHOR: generic
  // One chain for any container F: save needs the order that was found
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, Order> markPaid(
      Monad<F> monad, Kind<F, Order> found, Function<Order, Kind<F, Order>> save) {
    return monad.flatMap(
        order -> save.apply(OrderLenses.status().set(OrderStatus.PAID, order)), found);
  }

  // ANCHOR_END: generic

  /** Records the order in {@code saved}, as a store would, and hands it back. */
  static Order store(List<Order> saved, Order order) {
    saved.add(order);
    return order;
  }

  public static void main(String[] args) {
    // ANCHOR: instances
    Monad<EitherKind.Witness<String>> eitherMonad = Instances.monad(either());
    Function<Order, Kind<EitherKind.Witness<String>, Order>> save =
        paid -> EITHER.widen(Either.right(paid));

    Either<String, Order> saved =
        EITHER.narrow(markPaid(eitherMonad, EITHER.widen(Either.right(ORDER)), save));
    // Right(Order[id=00000000-0000-0000-0000-000000000001, ..., status=PAID])

    Either<String, Order> notFound =
        EITHER.narrow(markPaid(eitherMonad, EITHER.widen(Either.left("order not found")), save));
    // Left(order not found)

    List<Order> stored = new ArrayList<>();
    Kind<IOKind.Witness, Order> program =
        markPaid(
            Instances.monad(io()),
            IO_OP.widen(IO.delay(() -> ORDER)),
            paid -> IO_OP.widen(IO.delay(() -> store(stored, paid))));
    int savedBeforeRunning = stored.size();
    // 0
    IO_OP.narrow(program).unsafeRunSync();
    int savedAfterRunning = stored.size();
    // 1
    // ANCHOR_END: instances

    // ANCHOR: helpers
    Kind<EitherKind.Witness<String>, Order> found = EITHER.widen(Either.right(ORDER));

    Either<String, Order> unshipped =
        EITHER.narrow(
            eitherMonad.flatMapIfOrElse(
                order -> order.status() == OrderStatus.SHIPPED,
                order -> EITHER.widen(Either.left("order has shipped")),
                order -> EITHER.widen(Either.right(order)),
                found));
    // Right(Order[id=00000000-0000-0000-0000-000000000001, ..., status=NEW])

    Either<String, String> receipt = EITHER.narrow(eitherMonad.as("receipt sent", found));
    // Right(receipt sent)
    // ANCHOR_END: helpers

    // ANCHOR: flatmap2
    Monad<ValidatedKind.Witness<List<String>>> validatedMonad =
        Instances.validated(Semigroups.list());
    Kind<ValidatedKind.Witness<List<String>>, String> blankSku =
        VALIDATED.widen(Validated.invalid(List.of("SKU is blank")));
    Kind<ValidatedKind.Witness<List<String>>, Integer> noQuantity =
        VALIDATED.widen(Validated.invalid(List.of("quantity must be positive")));

    Validated<List<String>, String> mapped =
        VALIDATED.narrow(
            validatedMonad.map2(blankSku, noQuantity, (sku, quantity) -> quantity + " x " + sku));
    // Invalid([SKU is blank, quantity must be positive])

    Validated<List<String>, String> flatMapped =
        VALIDATED.narrow(
            validatedMonad.flatMap2(
                blankSku,
                noQuantity,
                (sku, quantity) -> VALIDATED.widen(Validated.valid(quantity + " x " + sku))));
    // Invalid([SKU is blank])
    // ANCHOR_END: flatmap2

    System.out.println(saved);
    System.out.println(notFound);
    System.out.println(savedBeforeRunning);
    System.out.println(savedAfterRunning);
    System.out.println(unshipped);
    System.out.println(receipt);
    System.out.println(mapped);
    System.out.println(flatMapped);
  }
}
