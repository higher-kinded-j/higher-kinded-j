// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Workflow engine for the Order Workflow V2 example.
 *
 * <p>This package contains the core workflow orchestration using ForPath for readable, composable
 * multi-step operations.
 *
 * <h2>Main Components</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.order.workflow.OrderWorkflowState} - Immutable state
 *       accumulator
 * </ul>
 *
 * <h2>ForPath Usage</h2>
 *
 * <p>The workflow uses ForPath for readable multi-step composition:
 *
 * <pre>{@code
 * ForPath.from(validateOrderRequest(request))
 *     .from(validated -> lookupCustomer(validated.customerId()))
 *     .from((validated, customer) -> checkInventory(validated))
 *     .from((validated, customer, inventory) -> applyDiscounts(validated))
 *     .from((validated, customer, inventory, discount) -> processPayment(validated, discount))
 *     .yield((validated, customer, inventory, discount, payment) ->
 *         buildResult(validated, customer, payment));
 * }</pre>
 */
@NullMarked
package org.higherkindedj.example.order.workflow;

import org.jspecify.annotations.NullMarked;
