// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.context;

import java.util.Objects;

public record TypeClassContext(String typeClassName, String operation)
    implements ValidationContext {

  public TypeClassContext {
    Objects.requireNonNull(typeClassName, "typeClassName cannot be null");
    Objects.requireNonNull(operation, "operation cannot be null");
  }

  public static TypeClassContext monad(String monadName, String operation) {
    return new TypeClassContext(monadName + "Monad", operation);
  }

  public static TypeClassContext functor(String functorName, String operation) {
    return new TypeClassContext(functorName + "Functor", operation);
  }

  @Override
  public String nullParameterMessage() {
    return String.format("%s.%s cannot accept null parameter", typeClassName, operation);
  }

  @Override
  public String nullInputMessage() {
    return nullParameterMessage();
  }

  @Override
  public String customMessage(String template, Object... args) {
    return String.format(template, args);
  }
}
