// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.selfcheck;

import java.util.function.Supplier;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;

/**
 * The answers to the write-code questions on the book's <a
 * href="https://higher-kinded-j.github.io/latest/hkts/self_check.html">Foundations self-check</a>:
 * three methods, each written once for any container, each asking for the least capability it
 * needs. The page {@code {{#include}}}s the anchored regions, and {@code SelfCheckBookTest} holds
 * every result the page's answers predict.
 */
public final class SelfCheckBook {

  private SelfCheckBook() {}

  // ANCHOR: count_lines
  // Only map is needed, so the method asks for a Functor
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, Integer> countLines(
      Functor<F> functor, Kind<F, Order> orders) {
    return functor.map(order -> order.lines().size(), orders);
  }

  // ANCHOR_END: count_lines

  // ANCHOR: receipt_line
  // The order and the email are found apart, so the method asks for an Applicative
  public static <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> receiptLine(
      Applicative<F> applicative, Kind<F, Order> order, Kind<F, EmailAddress> email) {
    return applicative.map2(order, email, (o, e) -> o.id() + " to " + e.value());
  }

  // ANCHOR_END: receipt_line

  // ANCHOR: from_cache_or
  // The service is called only when the cache missed, so the call sits in a handler
  public static <F extends WitnessArity<TypeArity.Unary>, E> Kind<F, Order> fromCacheOr(
      MonadError<F, E> monadError, Kind<F, Order> cached, Supplier<Kind<F, Order>> service) {
    return monadError.handleErrorWith(cached, missed -> service.get());
  }

  // ANCHOR_END: from_cache_or
}
