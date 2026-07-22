// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.higherkindedj.spring.example.domain.User;

/**
 * Sparse PATCH write-back (issue #645) for {@link User}: the generated {@code
 * UserPatchMappingImpl.updateFrom(UserPatchRequest)} folds the present (non-null) request fields
 * into an {@code Edits.Accumulated<User>}, leaving the absent ones — and the unmapped {@code id} —
 * untouched.
 *
 * <p>Extending {@link UpdateSpec} (rather than {@code MappingSpec}) opts into the null-as-absent
 * contract; the generated Impl exposes only {@code updateFrom} (no {@code build}/{@code parse}).
 * {@code firstName}/{@code lastName} copy by identity; {@code email} is validated through a leaf,
 * so a present-but-malformed email accumulates a located {@code FieldError} and the patch fails.
 */
@GenerateMapping
public interface UserPatchMapping extends UpdateSpec<User, UserPatchRequest> {

  /**
   * Validates a present email, keeping it a {@code String} (an explicit leaf wins over identity).
   *
   * <p>{@code updateFrom} only calls this for a <em>present</em> field, so the sparse flow never
   * hands it {@code null} — and {@code ValidatedPrism.parse} rejects a null source outright by
   * contract, so the lambda's own null test is defence in depth. Parse-don't-validate: a malformed
   * present value becomes a located error.
   */
  default ValidatedPrism<String, String> email() {
    return ValidatedPrism.of(
        raw ->
            raw != null && raw.contains("@")
                ? Validated.validNel(raw)
                : Validated.invalidNel(FieldError.of("not a valid email address")),
        email -> email);
  }
}
