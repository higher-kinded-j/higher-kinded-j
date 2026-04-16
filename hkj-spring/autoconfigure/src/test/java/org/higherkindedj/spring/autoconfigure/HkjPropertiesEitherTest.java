// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.spring.autoconfigure.HkjProperties.Web;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the nested {@link HkjProperties.Web.Either} configuration and its interaction with
 * the legacy flat {@code Web} accessors introduced to fix issue #490.
 *
 * <p>These tests drive the POJO directly (no Spring context) to guarantee every added accessor is
 * exercised and covered, independent of the binder's behaviour.
 */
@DisplayName("HkjProperties.Web.Either (issue #490)")
class HkjPropertiesEitherTest {

  @Nested
  @DisplayName("Either POJO accessors")
  class EitherPojoAccessors {

    @Test
    @DisplayName("Default constructor yields default status 400")
    void defaultStatusIs400() {
      Web.Either either = new Web.Either();
      assertThat(either.getDefaultErrorStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("setDefaultErrorStatus updates the field")
    void setDefaultErrorStatusUpdatesField() {
      Web.Either either = new Web.Either();
      either.setDefaultErrorStatus(500);
      assertThat(either.getDefaultErrorStatus()).isEqualTo(500);
    }
  }

  @Nested
  @DisplayName("Web accessors delegate to nested Either")
  class WebDelegation {

    @Test
    @DisplayName("getEither returns the eagerly-created Either instance")
    void getEitherReturnsNonNull() {
      Web web = new Web();
      assertThat(web.getEither()).isNotNull();
      assertThat(web.getEither().getDefaultErrorStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("setEither replaces the nested instance and drives getDefaultErrorStatus")
    void setEitherReplacesInstance() {
      Web web = new Web();
      Web.Either replacement = new Web.Either();
      replacement.setDefaultErrorStatus(503);

      web.setEither(replacement);

      assertThat(web.getEither()).isSameAs(replacement);
      // Delegation: flat accessor now sees the replacement's value.
      assertThat(web.getDefaultErrorStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("setEither(null) is ignored so delegation never NPEs")
    void setEitherNullIsIgnored() {
      Web web = new Web();
      Web.Either original = web.getEither();

      web.setEither(null);

      // The existing instance is preserved.
      assertThat(web.getEither()).isSameAs(original);
      // And the delegating accessors still return the default without error.
      assertThat(web.getDefaultErrorStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Web.getDefaultErrorStatus reads from the nested Either")
    void flatGetterDelegatesToEither() {
      Web web = new Web();
      web.getEither().setDefaultErrorStatus(418);
      assertThat(web.getDefaultErrorStatus()).isEqualTo(418);
    }

    @Test
    @DisplayName("Web.setDefaultErrorStatus writes through to the nested Either")
    void flatSetterDelegatesToEither() {
      Web web = new Web();
      web.setDefaultErrorStatus(422);
      assertThat(web.getEither().getDefaultErrorStatus()).isEqualTo(422);
      // And the round-trip via the flat accessor agrees.
      assertThat(web.getDefaultErrorStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("Writes via nested and flat accessors converge on a single value")
    void convergesOnSingleValue() {
      Web web = new Web();

      web.setDefaultErrorStatus(500); // flat path
      assertThat(web.getEither().getDefaultErrorStatus()).isEqualTo(500);

      web.getEither().setDefaultErrorStatus(501); // nested path
      assertThat(web.getDefaultErrorStatus()).isEqualTo(501);
    }
  }
}
