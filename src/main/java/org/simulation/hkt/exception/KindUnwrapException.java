package org.simulation.hkt.exception;

/**
 * Custom unchecked exception thrown when unwrapping an invalid Kind instance
 * within the HKT simulation framework.
 */
public class KindUnwrapException extends IllegalArgumentException {
  public KindUnwrapException(String message) {
    super(message);
  }

  public KindUnwrapException(String message, Throwable cause) {
    super(message, cause);
  }
}