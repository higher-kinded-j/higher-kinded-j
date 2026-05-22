// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.fetch;

import java.util.Set;

/**
 * Thrown when a batch resolver (or {@link BatchLoader}) returns no entry for one or more requested
 * keys.
 *
 * <p>A {@code Fetch} round hands the resolver a precise set of keys and requires a value for every
 * one of them. Silently substituting {@code null} for an omitted key would corrupt the result, so
 * the omission is reported as this exception instead. (A future railway error channel will let this
 * be surfaced as an {@code Either}/{@code Validated} value rather than thrown.)
 */
public final class MissingKeyException extends RuntimeException {

  /**
   * @param missingKeys the requested keys for which the resolver returned no entry
   */
  public MissingKeyException(Set<?> missingKeys) {
    super("Batch resolver returned no value for requested key(s): " + missingKeys);
  }
}
