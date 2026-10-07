// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.comingfrom;

import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/from_monocle.html">Coming from Monocle or
 * Haskell lens</a> page. The page {@code {{#include}}}s the anchored regions, and {@code
 * FromMonocleBookTest} holds the claims the page makes about this code and the library.
 *
 * <p>It works on the chapter's cast, imported by name, which shadows the "before" records {@code
 * FromJavaBook} declares in this package.
 */
public final class FromMonocleBook {

  private FromMonocleBook() {}

  /** What the page's affine block computes, so the test can read each value. */
  record AffineSet(Consignment written, Consignment untouched) {}

  static AffineSet affineSet(Consignment pending) {
    // ANCHOR: affine_set
    // A lens then a prism is an Affine, and its last step, the prism, can build a Returned
    Affine<Consignment, ConsignmentState.Returned> returned =
        ConsignmentLenses.state().andThen(ConsignmentStatePrisms.returned());

    // The consignment is pending, so the focus is absent, and set writes all the same
    Consignment written = returned.set(new ConsignmentState.Returned("damaged"), pending);

    // The set of lens and Monocle: write only where the focus is present
    Consignment untouched = returned.modify(_ -> new ConsignmentState.Returned("damaged"), pending);
    // ANCHOR_END: affine_set
    return new AffineSet(written, untouched);
  }

  /** What the page's prism block computes, so the test can read each value. */
  record PrismOps(
      Optional<ConsignmentState.Returned> seen,
      ConsignmentState built,
      ConsignmentState replaced,
      ConsignmentState passedOver) {}

  static PrismOps prismOps(ConsignmentState pendingState) {
    // ANCHOR: prism_ops
    Prism<ConsignmentState, ConsignmentState.Returned> returned = ConsignmentStatePrisms.returned();

    // preview: empty, since the state is pending
    Optional<ConsignmentState.Returned> seen = returned.getOptional(pendingState);

    // review: build the whole from the part
    ConsignmentState built = returned.build(new ConsignmentState.Returned("damaged"));

    // set, as lens has it: modify writes a match and passes any other state through
    ConsignmentState replaced = returned.modify(_ -> new ConsignmentState.Returned("lost"), built);
    ConsignmentState passedOver =
        returned.modify(_ -> new ConsignmentState.Returned("lost"), pendingState);
    // ANCHOR_END: prism_ops
    return new PrismOps(seen, built, replaced, passedOver);
  }

  /** What the page's headOption block computes, so the test can read each value. */
  record HeadOption(Optional<Integer> read, Order everyLine, Order firstLine) {}

  static HeadOption headOption(Order order) {
    // ANCHOR: head_option
    AffinePath<Order, Integer> firstQuantity =
        OrderFocus.lines().via(LineItemFocus.quantity()).headOption();

    // The read is the first line's quantity
    Optional<Integer> read = firstQuantity.getOptional(order);

    // The write goes to every line
    Order everyLine = firstQuantity.set(7, order);

    // To write the first line alone, index the list
    Order firstLine =
        FocusPath.of(OrderLenses.lines())
            .<LineItem>at(0)
            .via(LineItemFocus.quantity())
            .set(7, order);
    // ANCHOR_END: head_option
    return new HeadOption(read, everyLine, firstLine);
  }
}
