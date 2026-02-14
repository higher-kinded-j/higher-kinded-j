// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.security;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

@DisplayName("EitherAuthenticationConverter Tests")
class EitherAuthenticationConverterTest {

  private EitherAuthenticationConverter converter;

  @BeforeEach
  void setUp() {
    converter = new EitherAuthenticationConverter();
  }

  @Nested
  @DisplayName("convert Tests - Success Cases")
  class ConvertSuccessTests {

    @Test
    @DisplayName("Should convert JWT with roles successfully")
    void shouldConvertJwtWithRoles() {
      Jwt jwt = createJwt("user123", List.of("USER", "ADMIN"));

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      assertThat(token.getName()).isEqualTo("user123");
      assertThat(token.getAuthorities()).hasSize(2);
      assertThat(token.getAuthorities())
          .extracting("authority")
          .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should convert JWT with single role")
    void shouldConvertJwtWithSingleRole() {
      Jwt jwt = createJwt("user456", List.of("USER"));

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      assertThat(token.getAuthorities()).hasSize(1);
      assertThat(token.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Should convert JWT with many roles")
    void shouldConvertJwtWithManyRoles() {
      Jwt jwt = createJwt("superuser", List.of("USER", "ADMIN", "MODERATOR", "AUDITOR"));

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      assertThat(token.getAuthorities()).hasSize(4);
      assertThat(token.getAuthorities())
          .extracting("authority")
          .contains("ROLE_USER", "ROLE_ADMIN", "ROLE_MODERATOR", "ROLE_AUDITOR");
    }
  }

  @Nested
  @DisplayName("convert Tests - Error Cases")
  class ConvertErrorTests {

    @Test
    @DisplayName("Should handle JWT with missing roles claim")
    void shouldHandleJwtWithMissingRolesClaim() {
      Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "none")
              .subject("user123")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              // No roles claim
              .build();

      AbstractAuthenticationToken token = converter.convert(jwt);

      // On error, returns token with empty authorities
      assertThat(token).isNotNull();
      assertThat(token.getName()).isEqualTo("user123");
      assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("Should handle JWT with invalid roles type")
    void shouldHandleJwtWithInvalidRolesType() {
      Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "none")
              .subject("user123")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .claim("roles", "ADMIN") // String instead of Collection
              .build();

      AbstractAuthenticationToken token = converter.convert(jwt);

      // On error, returns token with empty authorities
      assertThat(token).isNotNull();
      assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("Should handle JWT with empty roles list")
    void shouldHandleJwtWithEmptyRolesList() {
      Jwt jwt = createJwt("user123", List.of());

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("Should filter out non-string roles")
    void shouldFilterOutNonStringRoles() {
      Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "none")
              .subject("user123")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .claim(
                  "roles", Arrays.asList("USER", 123, "ADMIN", null)) // Mixed types including null
              .build();

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      assertThat(token.getAuthorities()).hasSize(2);
      assertThat(token.getAuthorities())
          .extracting("authority")
          .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }
  }

  @Nested
  @DisplayName("Custom Configuration Tests")
  class CustomConfigurationTests {

    @Test
    @DisplayName("Should use custom authorities claim name")
    void shouldUseCustomAuthoritiesClaimName() {
      converter = new EitherAuthenticationConverter("permissions", "ROLE_");

      Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "none")
              .subject("user123")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .claim("permissions", List.of("READ", "WRITE"))
              .build();

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      assertThat(token.getAuthorities()).hasSize(2);
      assertThat(token.getAuthorities())
          .extracting("authority")
          .containsExactlyInAnyOrder("ROLE_READ", "ROLE_WRITE");
    }

    @Test
    @DisplayName("Should use custom authority prefix")
    void shouldUseCustomAuthorityPrefix() {
      converter = new EitherAuthenticationConverter("roles", "PERMISSION_");

      Jwt jwt = createJwt("user123", List.of("READ", "WRITE"));

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      assertThat(token.getAuthorities()).hasSize(2);
      assertThat(token.getAuthorities())
          .extracting("authority")
          .containsExactlyInAnyOrder("PERMISSION_READ", "PERMISSION_WRITE");
    }

    @Test
    @DisplayName("Should use empty authority prefix when specified")
    void shouldUseEmptyAuthorityPrefix() {
      converter = new EitherAuthenticationConverter("roles", "");

      Jwt jwt = createJwt("user123", List.of("USER", "ADMIN"));

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      assertThat(token.getAuthorities())
          .extracting("authority")
          .containsExactlyInAnyOrder("USER", "ADMIN");
    }
  }

  @Nested
  @DisplayName("Token Properties Tests")
  class TokenPropertiesTests {

    @Test
    @DisplayName("Should set principal from JWT subject")
    void shouldSetPrincipalFromSubject() {
      Jwt jwt = createJwt("alice@example.com", List.of("USER"));

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token.getName()).isEqualTo("alice@example.com");
      assertThat(token.getPrincipal()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Should set credentials to JWT")
    void shouldSetCredentialsToJwt() {
      Jwt jwt = createJwt("user123", List.of("USER"));

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token.getCredentials()).isEqualTo(jwt);
    }

    @Test
    @DisplayName("Should preserve JWT claims")
    void shouldPreserveJwtClaims() {
      Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "none")
              .subject("user123")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .claim("roles", List.of("USER"))
              .claim("email", "user@example.com")
              .claim("tenant", "acme-corp")
              .build();

      AbstractAuthenticationToken token = converter.convert(jwt);
      Jwt credentials = (Jwt) token.getCredentials();

      assertThat((Object) credentials.getClaim("email")).isEqualTo("user@example.com");
      assertThat((Object) credentials.getClaim("tenant")).isEqualTo("acme-corp");
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("Should handle JWT with null subject")
    void shouldHandleJwtWithNullSubject() {
      Jwt jwt =
          Jwt.withTokenValue("token")
              .header("alg", "none")
              // No subject
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .claim("roles", List.of("USER"))
              .build();

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      // Spring Security JWT returns empty string when subject is null
      assertThat(token.getName()).isEqualTo("");
    }

    @Test
    @DisplayName("Should handle roles with whitespace")
    void shouldHandleRolesWithWhitespace() {
      Jwt jwt = createJwt("user123", List.of("  USER  ", "ADMIN"));

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      // Whitespace is preserved (no trimming by converter)
      assertThat(token.getAuthorities()).extracting("authority").contains("ROLE_  USER  ");
    }

    @Test
    @DisplayName("Should handle duplicate roles")
    void shouldHandleDuplicateRoles() {
      Jwt jwt = createJwt("user123", List.of("USER", "USER", "ADMIN"));

      AbstractAuthenticationToken token = converter.convert(jwt);

      assertThat(token).isNotNull();
      assertThat(token.getAuthorities()).hasSize(3); // Duplicates not removed
    }
  }

  // Helper method to create JWT with roles
  private Jwt createJwt(String subject, List<String> roles) {
    return Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject(subject)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .claim("roles", roles)
        .build();
  }
}
