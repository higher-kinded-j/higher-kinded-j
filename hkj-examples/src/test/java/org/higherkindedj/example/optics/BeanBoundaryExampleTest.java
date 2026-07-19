// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.higherkindedj.example.optics.BeanBoundaryExample.EmailAddress;
import org.higherkindedj.example.optics.BeanBoundaryExample.User;
import org.higherkindedj.example.optics.BeanBoundaryExample.UserRequest;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link BeanBoundaryExample}: both bean mappings are law-checked through {@code
 * MappingLaws}, and the inbound parse accumulates and locates problems (including an unset
 * property) rather than throwing.
 */
@DisplayName("BeanBoundaryExample: bean DTOs at an I/O boundary")
class BeanBoundaryExampleTest {

  @Test
  @DisplayName("inbound mapping: the domain round trip through the request bean is lawful")
  void userRequestMappingIsLawful() {
    // The bean has no value equals(), so the domain-sample overload is the comparable law:
    // parse(build(user)) == Valid(user).
    MappingLaws.assertMappingLaws(
        BeanBoundaryExampleUserRequestMappingImpl.INSTANCE.asValidatedPrism(),
        new User("Ada", new EmailAddress("ada@corp.example")));
  }

  @Test
  @DisplayName("outbound mapping: the domain round trip through the builder view bean is lawful")
  void userViewMappingIsLawful() {
    MappingLaws.assertMappingLaws(
        BeanBoundaryExampleUserViewMappingImpl.INSTANCE.asValidatedPrism(),
        new User("Grace", new EmailAddress("grace@corp.example")));
  }

  @Test
  @DisplayName("inbound parse accumulates every problem and locates an unset property, not an NPE")
  void inboundParseAccumulatesAndLocates() {
    UserRequest bad = new UserRequest();
    bad.setEmail("not-an-email"); // name deliberately left unset (null)

    var parsed = BeanBoundaryExampleUserRequestMappingImpl.INSTANCE.parse(bad);

    assertThat(parsed.isInvalid()).isTrue();
    assertThat(parsed.getError().toJavaList())
        .containsExactly(
            new FieldError(List.of("name"), "must not be null"),
            new FieldError(List.of("email"), "not an email address"));
  }
}
