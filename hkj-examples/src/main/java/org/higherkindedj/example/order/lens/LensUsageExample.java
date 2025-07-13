// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.lens;

import java.util.List;
import org.higherkindedj.example.order.model.WorkflowContextLenses;
import org.higherkindedj.example.order.model.WorkflowModels.*;
import org.higherkindedj.optics.Lens;

public class LensUsageExample {

  public static void main(String[] args) {
    // Initial state
    OrderData initialData =
        new OrderData("Order-123", "PROD-ABC", 1, "card", "address", "cust1", List.of());
    WorkflowContext context = WorkflowContext.start(initialData);
    System.out.println("Initial context: " + context);

    // --- Simple Update ---
    // Before (using with... method):
    // WorkflowContext inventoryCheckedContext = context.withInventoryChecked();

    // After (using a Lens):
    WorkflowContext inventoryCheckedContext =
        WorkflowContextLenses.inventoryChecked().set(true, context);

    System.out.println("Inventory checked: " + inventoryCheckedContext);

    // --- Composed (Deep) Update ---
    // Let's say we have a ValidatedOrder and want to update its amount.

    ValidatedOrder validated =
        new ValidatedOrder(
            "Order-123", "PROD-ABC", 1, "card", 99.99, "address", "cust1", List.of());
    WorkflowContext validationContext =
        WorkflowContextLenses.validatedOrder().set(validated, inventoryCheckedContext);

    // Before (manual, verbose, and error-prone deep copy):
    /*
    ValidatedOrder currentOrder = validationContext.validatedOrder();
    ValidatedOrder updatedOrder = new ValidatedOrder(
        currentOrder.orderId(),
        currentOrder.productId(),
        currentOrder.quantity(),
        currentOrder.paymentDetails(),
        105.99, // the new amount
        currentOrder.shippingAddress(),
        currentOrder.customerId()
    );
    WorkflowContext updatedAmountContext = validationContext.withValidatedOrder(updatedOrder);
    */

    // After (using composed Lenses):
    // Assume a 'ValidatedOrderLenses' class was also generated
    // Lens<ValidatedOrder, Double> amountLens = ValidatedOrderLenses.amount();
    // Lens<WorkflowContext, ValidatedOrder> validatedOrderLens =
    // WorkflowContextLenses.validatedOrder();
    // Lens<WorkflowContext, Double> composedLens = validatedOrderLens.andThen(amountLens);

    // For this example, let's create the lens manually to show the power
    Lens<ValidatedOrder, Double> amountLens =
        Lens.of(
            ValidatedOrder::amount,
            (vo, newAmount) ->
                new ValidatedOrder(
                    vo.orderId(),
                    vo.productId(),
                    vo.quantity(),
                    vo.paymentDetails(),
                    newAmount,
                    vo.shippingAddress(),
                    vo.customerId(),
                    vo.promoCodes()));

    Lens<WorkflowContext, Double> composedLens =
        WorkflowContextLenses.validatedOrder().andThen(amountLens);

    // Now, update the deeply nested amount with one clear, concise operation
    WorkflowContext updatedAmountContext = composedLens.set(105.99, validationContext);

    System.out.println("Updated amount: " + updatedAmountContext);
  }
}
