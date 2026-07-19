// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.example.optics.UpdateSpecExample.EmailAddress;
import org.higherkindedj.example.optics.UpdateSpecExample.User;
import org.higherkindedj.example.optics.UpdateSpecExample.UserPatchRequest;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link UpdateSpecExample}: the sparse-update mapping is law-checked through {@code
 * MappingLaws}, and {@code applyPatch} folds present fields, leaves absent ones untouched, and
 * fails a present invalid field, located.
 */
@DisplayName("UpdateSpecExample: sparse PATCH write-back through UpdateSpec")
class UpdateSpecExampleTest {

  private static UserPatchRequest request(String name, String email, Integer age) {
    UserPatchRequest request = new UserPatchRequest();
    request.setName(name);
    request.setEmail(email);
    request.setAge(age);
    return request;
  }

  @Test
  @DisplayName("the sparse-update mapping satisfies identity, idempotence and validation")
  void updateMappingIsLawful() {
    MappingLaws.assertMappingLaws(
        UpdateSpecExampleUserPatchMappingImpl.INSTANCE::updateFrom,
        new User("Ada", new EmailAddress("ada@corp.example"), 36),
        request(null, null, null),
        request("Grace", "grace@corp.example", 41),
        request(null, "not-an-email", null));
  }

  @Test
  @DisplayName("present fields are applied, absent ones keep their current value")
  void appliesPresentLeavesAbsent() {
    User current = new User("Ada", new EmailAddress("ada@corp.example"), 36);

    var patched = UpdateSpecExample.applyPatch(current, request("Ada Lovelace", null, null));

    assertThat(patched.isValid()).isTrue();
    assertThat(patched.get())
        .isEqualTo(new User("Ada Lovelace", new EmailAddress("ada@corp.example"), 36));
  }

  @Test
  @DisplayName("an all-absent request is the identity update")
  void allAbsentIsIdentity() {
    User current = new User("Ada", new EmailAddress("ada@corp.example"), 36);

    var patched = UpdateSpecExample.applyPatch(current, request(null, null, null));

    assertThat(patched.isValid()).isTrue();
    assertThat(patched.get()).isEqualTo(current);
  }

  @Test
  @DisplayName("a present invalid field fails the patch, located, writing nothing")
  void presentInvalidFieldFailsLocated() {
    User current = new User("Ada", new EmailAddress("ada@corp.example"), 36);

    var patched = UpdateSpecExample.applyPatch(current, request("Grace", "not-an-email", null));

    assertThat(patched.isInvalid()).isTrue();
    assertThat(patched.getError().toJavaList())
        .containsExactly(new FieldError(List.of("email"), "not an email address"));
  }
}
