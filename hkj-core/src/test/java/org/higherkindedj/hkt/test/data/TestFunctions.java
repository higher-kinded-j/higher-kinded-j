// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.data;

import java.util.function.Function;

public final class TestFunctions {

  private TestFunctions() {
    throw new AssertionError("TestFunctions is a utility class");
  }

  public static final Function<Integer, String> INT_TO_STRING = Object::toString;

  /**
   * Creates a function that throws the given exception.
   *
   * @param exception The exception to throw
   * @param <A> The input type
   * @param <B> The output type
   * @return A function that always throws the given exception
   */
  public static <A, B> Function<A, B> throwingFunction(RuntimeException exception) {
    return a -> {
      throw exception;
    };
  }

  /**
   * Creates a function that returns null (for testing null handling).
   *
   * @param <A> The input type
   * @param <B> The output type
   * @return A function that always returns null
   */
  public static <A, B> Function<A, B> nullReturningFunction() {
    return a -> null;
  }
}
