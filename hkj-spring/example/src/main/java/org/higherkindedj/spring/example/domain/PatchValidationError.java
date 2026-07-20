// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.domain;

import java.util.stream.Collectors;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;

/**
 * A validation failure from a sparse PATCH (issue #645): carries <em>every</em> located field error
 * at once, exactly as the sparse mapping accumulated them.
 *
 * <p>The class name contains "Validation", so the {@code EitherReturnValueHandler} maps it to HTTP
 * 400 automatically, and the {@code errors} — a {@link NonEmptyList} of {@link FieldError} — are
 * serialised by {@code HkjJacksonModule}, so the response body carries each problem located by its
 * field path.
 *
 * @param errors the located field errors, in accumulation order
 */
public record PatchValidationError(NonEmptyList<FieldError> errors) implements DomainError {
  @Override
  public String message() {
    return "Validation error: "
        + errors.toJavaList().stream().map(FieldError::message).collect(Collectors.joining("; "));
  }
}
