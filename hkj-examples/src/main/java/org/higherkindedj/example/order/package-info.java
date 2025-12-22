// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Order Workflow V2: A showcase for the Effect API and Focus DSL in Higher-Kinded-J.
 *
 * <p>This example demonstrates modern functional programming patterns in Java 25, showing how
 * Higher-Kinded-J enables elegant, type-safe, and composable code.
 *
 * <h2>What This Example Demonstrates</h2>
 *
 * <ul>
 *   <li><b>ForPath</b> - Readable for-comprehension style workflow composition
 *   <li><b>Effect Contexts</b> - Clean, user-friendly effect composition
 *   <li><b>Focus DSL</b> - Type-safe immutable data navigation and updates
 *   <li><b>WriterPath</b> - Accumulated audit logging throughout the workflow
 *   <li><b>ReaderPath</b> - Dependency injection and configuration threading
 *   <li><b>Parallel execution</b> - Independent operations run concurrently
 *   <li><b>Resilience patterns</b> - Retry with exponential backoff
 *   <li><b>Code generation</b> - Reduced boilerplate with annotations
 * </ul>
 *
 * <h2>Package Structure</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.example.order.model} - Domain model records
 *   <li>{@link org.higherkindedj.example.order.model.value} - Value objects
 *   <li>{@link org.higherkindedj.example.order.error} - Error hierarchy
 *   <li>{@link org.higherkindedj.example.order.service} - Service interfaces
 *   <li>{@link org.higherkindedj.example.order.workflow} - Workflow engine
 *   <li>{@link org.higherkindedj.example.order.config} - Configuration
 *   <li>{@link org.higherkindedj.example.order.audit} - Audit logging
 *   <li>{@link org.higherkindedj.example.order.resilience} - Retry/circuit breaker
 * </ul>
 *
 * <h2>The Order Workflow</h2>
 *
 * <p>The workflow processes an order through these steps:
 *
 * <ol>
 *   <li>Validate the order request
 *   <li>Look up customer details
 *   <li>Check and reserve inventory
 *   <li>Apply discounts (promo codes, loyalty)
 *   <li>Process payment
 *   <li>Create shipment
 *   <li>Send notifications
 * </ol>
 *
 * <h2>Getting Started</h2>
 *
 * <pre>{@code
 * OrderWorkflow workflow = OrderWorkflow.create(config);
 * EitherPath<OrderError, OrderResult> result = workflow.process(orderRequest);
 * }</pre>
 *
 * @see org.higherkindedj.example.order.workflow.OrderWorkflow
 * @see org.higherkindedj.hkt.effect.path.ForPath
 */
@NullMarked
package org.higherkindedj.example.order;

import org.jspecify.annotations.NullMarked;
