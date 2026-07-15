// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.util.List;
import java.util.function.UnaryOperator;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;
import org.jspecify.annotations.Nullable;

/**
 * The {@code @GenerateErrorEnvelope} code shown on the book's <a
 * href="https://higher-kinded-j.github.io/optics/record_mapping.html">Record Mapping</a> page. The
 * page {@code {{#include}}}s the anchored regions, so it cannot drift from the API.
 *
 * <p>This exists because the page's {@code editContext} example is an interface {@code default}
 * method spliced together with call-site usage, which cannot be assembled into a compilation unit
 * by the snippet gate. As a real example it compiles naturally, so the block is verified rather
 * than merely asserted.
 */
public final class OrderErrorBook {

  private OrderErrorBook() {}

  public static void main(String[] args) {
    OrderId orderId = new OrderId("ORD-1");
    TraceId traceId = new TraceId("trace-1");
    List<ProductId> products = List.of(new ProductId("PROD-1"));

    // ANCHOR: edit_context
    OrderError error =
        OrderErrors.outOfStock(products) // typed factory
            .editContext(
                ctx -> ctx.orderId(orderId).traceId(traceId)); // typed context, not map.put
    // ANCHOR_END: edit_context
    System.out.println(error.envelope());
  }
}

record OrderId(String value) {}

record TraceId(String value) {}

record ProductId(String value) {}

record CardRef(String value) {}

// ANCHOR: error_envelope
// The context is records-as-schema: nullable components, an all-absent default.
record OrderErrorContext(@Nullable OrderId orderId, @Nullable TraceId traceId) {}

@GenerateErrorEnvelope
sealed interface OrderError {
  ErrorEnvelope<OrderErrorContext> envelope(); // declared once

  // A one-line default so the generated wither reads as an instance method.
  default OrderError editContext(UnaryOperator<OrderErrors.ContextBuilder> edit) {
    return OrderErrors.editContext(this, edit);
  }

  record OutOfStock(List<ProductId> products, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {}

  record PaymentDeclined(CardRef card, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {}
}
// ANCHOR_END: error_envelope
