// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.payment.model;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

/**
 * Immutable event log for replay interpretation.
 *
 * <p>Stores pre-recorded results for each operation type, allowing a program to be replayed
 * deterministically from stored events without calling external services.
 *
 * @param events map from operation key to pre-recorded result
 */
@NullMarked
public record EventLog(Map<String, Object> events) {

  public EventLog {
    Objects.requireNonNull(events, "events cannot be null");
    events = Map.copyOf(events);
  }

  /**
   * Retrieves a pre-recorded event by key.
   *
   * @param key the operation key
   * @param <T> the expected result type
   * @return the stored result
   * @throws IllegalStateException if the key is not present
   */
  @SuppressWarnings("unchecked")
  public <T> T get(String key) {
    Object value = events.get(key);
    if (value == null) {
      throw new IllegalStateException("No recorded event for key: " + key);
    }
    return (T) value;
  }
}
