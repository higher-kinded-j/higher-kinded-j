// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Configuration for the Order Workflow V2 example.
 *
 * <p>This package contains configuration records that are threaded through the workflow using
 * {@code ReaderPath} or {@code ConfigContext}.
 *
 * <h2>Usage with ReaderPath</h2>
 *
 * <pre>{@code
 * ReaderPath<WorkflowConfig, OrderResult> workflow =
 *     ReaderPath.ask()
 *         .flatMap(config -> processOrder(request, config));
 *
 * OrderResult result = workflow.run(WorkflowConfig.defaults());
 * }</pre>
 */
@NullMarked
package org.higherkindedj.example.order.config;

import org.jspecify.annotations.NullMarked;
