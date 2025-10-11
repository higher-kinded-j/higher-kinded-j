// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.context;

import java.util.Objects;

/** Context for validating function parameters in monad operations */
public record FunctionContext(String functionName, String operation) implements ValidationContext {

  public FunctionContext {
    Objects.requireNonNull(functionName, "functionName cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");
  }

  public static FunctionContext mapper(String operation) {
    return new FunctionContext("function f", operation);
  }

  public static FunctionContext flatMapper(String operation) {
    return new FunctionContext("function f", operation);
  }

  public static FunctionContext applicative(String operation) {
    return new FunctionContext("applicative instance", operation);
  }

  @Override
  public String nullParameterMessage() {
    return String.format("Function %s for %s cannot be null", functionName, operation);
  }

  @Override
  public String nullInputMessage() {
    return nullParameterMessage(); // Same for functions
  }

  @Override
  public String customMessage(String template, Object... args) {
    return String.format(template, args);
  }
}
