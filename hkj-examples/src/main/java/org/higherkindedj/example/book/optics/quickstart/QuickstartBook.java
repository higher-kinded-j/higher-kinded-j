// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.quickstart;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import org.higherkindedj.example.book.optics.JsonNodeOptics;
import org.higherkindedj.example.book.optics.cast.Consignment;
import org.higherkindedj.example.book.optics.cast.ConsignmentState;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.LineItemFocus;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderFocus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.StringNode;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/quickstart.html">Optics Quickstart</a>. The
 * page {@code {{#include}}}s the anchored regions, and {@code QuickstartBookTest} holds the claims
 * the page makes about this code. The records are the chapter's cast, from {@code
 * org.higherkindedj.example.book.optics.cast}.
 */
public final class QuickstartBook {

  private QuickstartBook() {}

  static Order changeEmail(Order order) {
    // ANCHOR: one_liner
    Order updated = OrderFocus.customer().email().value().set("ada@example.org", order);
    // ANCHOR_END: one_liner
    return updated;
  }

  static Order roundPrices(Order order) {
    // ANCHOR: round
    Order rounded =
        OrderFocus.lines()
            .via(LineItemFocus.price())
            .modifyAll(price -> price.setScale(2, RoundingMode.HALF_EVEN), order);
    // ANCHOR_END: round
    return rounded;
  }

  /** What the page's prism block computes, so the test can read each value. */
  record PrismResults(boolean isPending, ConsignmentState tidied, ConsignmentState dispatched) {}

  static PrismResults matchAndMove(Consignment consignment, Instant dispatchedAt) {
    // ANCHOR: prism
    boolean isPending = ConsignmentStatePrisms.pending().matches(consignment.state());

    // modify rebuilds the variant it narrowed to, so the function is Returned -> Returned
    ConsignmentState tidied =
        ConsignmentStatePrisms.returned()
            .modify(
                returned -> new ConsignmentState.Returned(returned.reason().strip()),
                consignment.state());

    // Moving to a different variant is not a modify. Read through the prism, then build.
    ConsignmentState dispatched =
        ConsignmentStatePrisms.pending()
            .getOptional(consignment.state())
            .<ConsignmentState>map(_ -> new ConsignmentState.Dispatched(dispatchedAt))
            .orElse(consignment.state());
    // ANCHOR_END: prism
    return new PrismResults(isPending, tidied, dispatched);
  }

  static Optional<StringNode> firstName(String json) {
    // ANCHOR: json
    JsonNode response = new ObjectMapper().readTree(json);

    Optional<ArrayNode> items = JsonNodeOptics.array().getOptional(response.get("items"));

    Optional<StringNode> firstName =
        items
            .flatMap(array -> JsonNodeOptics.object().getOptional(array.get(0)))
            .flatMap(object -> JsonNodeOptics.text().getOptional(object.get("name")));
    // ANCHOR_END: json
    return firstName;
  }
}
