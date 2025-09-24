// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.context;

import java.util.Objects;

/** Context for domain-specific validations (transformers, etc.) */
public record DomainContext(String domainType, String objectName) implements ValidationContext {

  public DomainContext {
    Objects.requireNonNull(domainType, "domainType cannot be null");
    Objects.requireNonNull(objectName, "objectName cannot be null");
  }

  public static DomainContext transformer(String transformerName) {
    return new DomainContext("transformer", transformerName);
  }

  public static DomainContext witness(String operation) {
    return new DomainContext("witness", operation);
  }

  @Override
  public String nullParameterMessage() {
    return "%s %s cannot be null for %s"
        .formatted(
            domainType.substring(0, 1).toUpperCase() + domainType.substring(1),
            objectName.equals("witness") ? "Monad" : "",
            objectName);
  }

  @Override
  public String nullInputMessage() {
    return nullParameterMessage();
  }

  @Override
  public String customMessage(String template, Object... args) {
    return template.formatted(args);
  }

  public String mismatchMessage(Class<?> expected, Class<?> actual) {
    return "Witness type mismatch in %s: expected %s, got %s"
        .formatted(objectName, expected.getSimpleName(), actual.getSimpleName());
  }
}
