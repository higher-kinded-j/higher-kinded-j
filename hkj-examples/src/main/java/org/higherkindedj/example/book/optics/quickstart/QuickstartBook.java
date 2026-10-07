// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.quickstart;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.example.book.optics.JsonNodeOptics;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.StringNode;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/quickstart.html">Optics Quickstart</a>. The
 * page {@code {{#include}}}s the anchored regions, and {@code QuickstartBookTest} holds the claims
 * the page makes about this code.
 */
public final class QuickstartBook {

  private QuickstartBook() {}

  static User rename(User user) {
    // ANCHOR: one_liner
    User updated = UserFocus.address().street().name().set("New Street", user);
    // ANCHOR_END: one_liner
    return updated;
  }

  static Order discount(Order order) {
    // ANCHOR: discount
    Order discounted =
        OrderFocus.items()
            .via(LineItemFocus.price())
            .modifyAll(price -> price.multiply(new BigDecimal("0.9")), order);
    // ANCHOR_END: discount
    return discounted;
  }

  /** What the page's prism block computes, so the test can read each value. */
  record PrismResults(boolean isPending, Status tidied, Status fulfilled) {}

  static PrismResults matchAndMove(Order order, Instant shippedAt) {
    // ANCHOR: prism
    boolean isPending = StatusPrisms.pending().matches(order.status());

    // modify rebuilds the variant it narrowed to, so the function is Cancelled -> Cancelled.
    Status tidied =
        StatusPrisms.cancelled()
            .modify(cancelled -> new Status.Cancelled(cancelled.reason().strip()), order.status());

    // Moving to a different variant is not a modify. Read through the prism, then build.
    Status fulfilled =
        StatusPrisms.pending()
            .getOptional(order.status())
            .<Status>map(_ -> new Status.Shipped(shippedAt))
            .orElse(order.status());
    // ANCHOR_END: prism
    return new PrismResults(isPending, tidied, fulfilled);
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

// ANCHOR: records
@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Street(String name, int number) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Address(Street street, String city) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record User(String name, Address address) {}

// ANCHOR_END: records

// ANCHOR: order_types
@GeneratePrisms
sealed interface Status permits Status.Pending, Status.Shipped, Status.Cancelled {
  record Pending() implements Status {}

  record Shipped(Instant at) implements Status {}

  record Cancelled(String reason) implements Status {}
}

@GenerateLenses
@GenerateFocus
record LineItem(String sku, BigDecimal price) {}

@GenerateLenses
@GenerateFocus
@GenerateTraversals
record Order(String id, Status status, List<LineItem> items) {}
// ANCHOR_END: order_types
