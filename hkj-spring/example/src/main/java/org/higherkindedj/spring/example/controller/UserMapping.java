// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.higherkindedj.spring.example.domain.User;

/**
 * Full bidirectional mapping between {@link User} and {@link UserDto} (issue #600), showcasing the
 * 422 leg (issue #627): a controller returns the generated {@code
 * UserMappingImpl.INSTANCE.parse(dto)} directly, and the {@code ValidationPathReturnValueHandler}
 * renders an invalid parse as one {@code hkj.web.validation-field-error-status} response (default
 * 422 Unprocessable Content) carrying every located {@code FieldError} by path.
 *
 * <p>{@code email} and {@code firstName} are validated through leaves, so a request with both bad
 * accumulates both errors in a single response; {@code id} and {@code lastName} copy by identity.
 * The wire side is a bean, so every reference read is null-guarded by the generated {@code parse}:
 * an absent field becomes a located {@code FieldError} ({@code "must not be null"}) and a {@code
 * null} never reaches a leaf.
 */
@GenerateMapping
public interface UserMapping extends MappingSpec<User, UserDto> {

  /**
   * Validates a present email, keeping it a {@code String} (an explicit leaf wins over identity).
   *
   * <p>The bean-shaped {@code parse} null-guards the read, so this leaf never sees {@code null}
   * (and {@code ValidatedPrism.parse} rejects a null source outright by contract); the lambda's own
   * null test is defence in depth. A malformed present value becomes a located error.
   */
  default ValidatedPrism<String, String> email() {
    return ValidatedPrism.of(
        raw ->
            raw != null && raw.contains("@")
                ? Validated.validNel(raw)
                : Validated.invalidNel(FieldError.of("not a valid email address")),
        email -> email);
  }

  /** Validates a present first name as non-blank; the null test is defence in depth as above. */
  default ValidatedPrism<String, String> firstName() {
    return ValidatedPrism.of(
        raw ->
            raw != null && !raw.isBlank()
                ? Validated.validNel(raw)
                : Validated.invalidNel(FieldError.of("must not be blank")),
        firstName -> firstName);
  }
}
