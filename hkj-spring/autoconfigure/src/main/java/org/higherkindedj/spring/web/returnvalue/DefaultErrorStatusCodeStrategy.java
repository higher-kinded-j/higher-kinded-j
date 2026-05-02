// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.web.returnvalue;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Default {@link ErrorStatusCodeStrategy} that combines the configured {@code
 * hkj.web.error-status-mappings} property map with the built-in heuristics in {@link
 * ErrorStatusCodeMapper}.
 *
 * <p>Resolution order matches {@link ErrorStatusCodeMapper#determineStatusCode(Object, int, Map)}:
 * explicit mapping by simple class name → explicit mapping by fully-qualified class name → token
 * heuristic → {@code defaultStatus}.
 */
public final class DefaultErrorStatusCodeStrategy implements ErrorStatusCodeStrategy {

  private final Map<String, Integer> errorStatusMappings;

  /**
   * Creates a strategy backed by the supplied mapping table.
   *
   * @param errorStatusMappings explicit overrides keyed by simple or fully-qualified class name;
   *     {@code null} is treated as an empty map. A defensive copy is taken so subsequent mutations
   *     of the supplied map do not affect resolution.
   */
  public DefaultErrorStatusCodeStrategy(@Nullable Map<String, Integer> errorStatusMappings) {
    this.errorStatusMappings =
        errorStatusMappings == null || errorStatusMappings.isEmpty()
            ? Map.of()
            : Map.copyOf(errorStatusMappings);
  }

  @Override
  public int statusCodeFor(Object error, int defaultStatus) {
    return ErrorStatusCodeMapper.determineStatusCode(error, defaultStatus, errorStatusMappings);
  }

  /**
   * Returns an unmodifiable view of the mappings backing this strategy. Useful for diagnostics and
   * tests.
   *
   * @return the configured mappings
   */
  public Map<String, Integer> mappings() {
    return errorStatusMappings;
  }
}
