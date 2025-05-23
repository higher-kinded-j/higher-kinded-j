// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import static java.util.Objects.requireNonNull;

import java.util.function.Consumer;
import org.jspecify.annotations.NonNull;

/**
 * A record to hold external dependencies required by the order workflow steps. This promotes
 * dependency injection, making the steps more testable and configurable.
 *
 * @param logger A Consumer function to handle log messages. In a real application, this could be a
 *     structured logger instance (e.g., SLF4j Logger).
 */
public record Dependencies(@NonNull Consumer<String> logger /* , Add other dependencies here */) {

  /** Default constructor ensures logger is non-null. */
  public Dependencies {
    requireNonNull(logger, "Logger dependency cannot be null");
  }

  /** Convenience method to log a message using the configured logger. */
  public void log(String message) {
    logger.accept(message); // Use the injected logger
  }
}
