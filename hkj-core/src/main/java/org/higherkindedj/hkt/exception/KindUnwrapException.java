// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.exception;

/**
 * Custom unchecked exception thrown when unwrapping an invalid Kind instance within the HKT
 * Higher-Kinded-J framework.
 */
public class KindUnwrapException extends IllegalArgumentException {
  public KindUnwrapException(String message) {
    super(message);
  }

  public KindUnwrapException(String message, Throwable cause) {
    super(message, cause);
  }
}
