// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.identity;

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.math.BigDecimal;
import org.higherkindedj.example.book.foundations.Basket;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/identity.html">Identity</a> page: a basket
 * total on an {@code IdPath}, the step that cannot fail; a delivery charge written once for any
 * monad, tested with {@code Id} and run with {@code IO}; and the State page's basket step written
 * as a {@code StateT} over {@code Id}. The page {@code {{#include}}}s the anchored regions, {@code
 * IdentityBookTest} holds the claims the page makes, and the book's output gate runs {@link #main}
 * against the comments in the regions.
 */
public final class IdentityBook {

  static final LineItem LAMP = new LineItem("LAMP", 1, new BigDecimal("40.00"));
  static final LineItem BULBS = new LineItem("BULB", 4, new BigDecimal("2.50"));
  static final LineItem SOFA = new LineItem("SOFA", 1, new BigDecimal("450.00"));
  static final BigDecimal LIMIT = new BigDecimal("100.00");

  private IdentityBook() {}

  // ANCHOR: delivery
  static final BigDecimal DELIVERY = new BigDecimal("4.99");
  static final BigDecimal FREE_FROM = new BigDecimal("50.00");

  // Written once for any monad: F might be IO in the service and Id in a test
  static <F extends WitnessArity<TypeArity.Unary>> Kind<F, BigDecimal> withDelivery(
      Monad<F> monad, Kind<F, BigDecimal> subtotal) {
    return monad.map(
        total -> total.compareTo(FREE_FROM) >= 0 ? total : total.add(DELIVERY), subtotal);
  }

  // ANCHOR_END: delivery

  // ANCHOR: state_t
  // The State page's step, as a StateT over Id: adds the line if the basket stays within the limit
  static StateT<Basket, IdKind.Witness, Boolean> add(LineItem line) {
    return StateT.create(
        basket -> {
          Basket bigger = basket.add(line);
          return Id.of(
              bigger.total().compareTo(LIMIT) <= 0
                  ? new StateTuple<>(true, bigger)
                  : new StateTuple<>(false, basket));
        });
  }

  // ANCHOR_END: state_t

  public static void main(String[] args) {
    // ANCHOR: path
    // A step that cannot fail, on the Path that adds no effect
    IdPath<BigDecimal> subtotal = Path.id(Basket.EMPTY.add(LAMP).add(BULBS)).map(Basket::total);

    BigDecimal total = subtotal.get();
    // 50.00

    // It joins a chain that can fail as the first step of an EitherPath
    EitherPath<String, BigDecimal> withinLimit =
        subtotal
            .<String>toEitherPath()
            .via(t -> t.compareTo(LIMIT) <= 0 ? Path.right(t) : Path.left("over the limit"));
    // ANCHOR_END: path
    System.out.println(total);
    System.out.println(withinLimit.run());

    // ANCHOR: delivery_test
    // In a test, Id runs the arithmetic with no effect around it
    Monad<IdKind.Witness> ids = Instances.monad(id());

    BigDecimal small = ID.narrow(withDelivery(ids, Id.of(new BigDecimal("12.50")))).value();
    // 17.49
    BigDecimal large = ID.narrow(withDelivery(ids, Id.of(new BigDecimal("60.00")))).value();
    // 60.00
    // ANCHOR_END: delivery_test
    System.out.println(small);
    System.out.println(large);

    // ANCHOR: delivery_io
    // In the service, the subtotal is read when the IO runs, and so is the charge added
    Kind<IOKind.Witness, BigDecimal> subtotalLater =
        IO_OP.widen(IO.delay(() -> new BigDecimal("12.50")));
    IO<BigDecimal> charged = IO_OP.narrow(withDelivery(Instances.monad(io()), subtotalLater));

    BigDecimal served = charged.unsafeRunSync();
    // 17.49
    // ANCHOR_END: delivery_io
    System.out.println(served);

    // ANCHOR: state_t_run
    // runStateT gives an Id holding the result and the basket the step leaves
    StateTuple<Basket, Boolean> after = ID.narrow(add(SOFA).runStateT(Basket.EMPTY)).value();

    // The sofa would take the basket past the limit, so the step leaves it out
    boolean added = after.value();
    // false
    // ANCHOR_END: state_t_run
    System.out.println(added);
  }
}
