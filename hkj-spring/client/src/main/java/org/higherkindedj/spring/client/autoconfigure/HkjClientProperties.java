// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Higher-Kinded-J Spring Boot client, bound from {@code hkj.client.*}.
 *
 * <p>The client-side analogue of the server's {@code hkj.web.error-status-mappings}: where the
 * server maps an error type to a status code, this maps a status code back to an error type so a
 * generated {@code @HkjHttpClient} decodes that status into the configured type globally (when it
 * is assignable to the method's declared error type), without a per-method {@code @OnStatus}.
 *
 * <pre>{@code
 * hkj:
 *   client:
 *     status-error-mappings:
 *       404: com.example.UserNotFoundError
 *       429: com.example.RateLimitError
 * }</pre>
 */
@ConfigurationProperties(prefix = "hkj.client")
public class HkjClientProperties {

  /** Global HTTP status code → error type (fully-qualified class name) mapping. */
  private Map<Integer, String> statusErrorMappings = new LinkedHashMap<>();

  /**
   * The configured status → error-type-name mapping.
   *
   * @return the mapping (never {@code null})
   */
  public Map<Integer, String> getStatusErrorMappings() {
    return statusErrorMappings;
  }

  /**
   * Sets the status → error-type-name mapping.
   *
   * @param statusErrorMappings the mapping
   */
  public void setStatusErrorMappings(Map<Integer, String> statusErrorMappings) {
    this.statusErrorMappings = statusErrorMappings;
  }
}
