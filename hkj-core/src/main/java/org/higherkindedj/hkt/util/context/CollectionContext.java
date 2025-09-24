// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.context;

import java.util.Objects;

/** Context for validating collections and arrays */
public record CollectionContext(String collectionType, String parameterName)
    implements ValidationContext {

  public CollectionContext {
    Objects.requireNonNull(collectionType, "collectionType cannot be null");
    Objects.requireNonNull(parameterName, "parameterName cannot be null");
  }

  public static CollectionContext collection(String parameterName) {
    return new CollectionContext("collection", parameterName);
  }

  public static CollectionContext array(String parameterName) {
    return new CollectionContext("array", parameterName);
  }

  @Override
  public String nullParameterMessage() {
    return "%s cannot be null".formatted(parameterName);
  }

  @Override
  public String nullInputMessage() {
    return nullParameterMessage();
  }

  @Override
  public String customMessage(String template, Object... args) {
    return template.formatted(args);
  }

  public String emptyMessage() {
    return "%s cannot be empty".formatted(parameterName);
  }
}
