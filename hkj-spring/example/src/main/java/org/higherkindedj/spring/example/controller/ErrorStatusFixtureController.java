// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import java.util.Map;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.spring.web.returnvalue.HttpHeaderCarrier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canonical {@code @WebMvcTest} fixture controller for asserting error → HTTP status mapping
 * end-to-end. Adopters can copy this verbatim (or import it via {@code @ContextConfiguration}) to
 * drive a slice test that exercises every status code their domain emits.
 *
 * <p>The endpoint {@code /api/error-status-fixture/{kind}} returns one variant per {@code kind},
 * covering:
 *
 * <ul>
 *   <li>Each built-in heuristic ({@code not-found}, {@code validation}, {@code invalid}, {@code
 *       forbidden}, {@code authorization}, {@code authentication}, {@code unauthorized})
 *   <li>Status codes that require explicit {@code hkj.web.error-status-mappings} entries ({@code
 *       conflict} → 409, {@code unprocessable} → 422)
 *   <li>An error that surfaces a {@code Retry-After} header via {@link HttpHeaderCarrier} ({@code
 *       throttled})
 *   <li>A no-match variant that should fall through to the configured default ({@code unmapped})
 * </ul>
 *
 * <p>See {@code ErrorStatusFixtureSliceTest} for the matching assertions and {@code
 * application-error-status-fixture.yml}-style property bindings.
 */
@RestController
@RequestMapping("/api/error-status-fixture")
public class ErrorStatusFixtureController {

  /** Creates an ErrorStatusFixtureController. */
  public ErrorStatusFixtureController() {}

  /**
   * Returns one error variant per {@code kind} so a single slice test can assert every
   * status-mapping rule the project depends on.
   *
   * @param kind the variant to emit (see class Javadoc)
   * @return an {@code Either} whose {@code Left} encodes the requested error variant
   */
  @GetMapping("/{kind}")
  public Either<DomainError, String> raise(@PathVariable String kind) {
    return Either.left(
        switch (kind) {
          case "not-found" -> new DomainError.UserNotFoundError("123");
          case "validation" -> new DomainError.ValidationError("missing field");
          case "invalid" -> new DomainError.MfaCodeInvalidError("E_BAD_CODE");
          case "forbidden" -> new DomainError.ForbiddenAction("delete");
          case "authorization" -> new DomainError.AuthorizationError("scope");
          case "authentication" -> new DomainError.AuthenticationError("missing token");
          case "unauthorized" -> new DomainError.UnauthorizedAccess("expired");
          case "conflict" -> new DomainError.MfaAlreadyEnrolledError("user-1");
          case "unprocessable" -> new DomainError.PaymentDeclinedError("insufficient funds");
          case "throttled" -> new DomainError.MfaThrottledError(30);
          case "unmapped" -> new DomainError.UnmappedError("opaque");
          default -> throw new IllegalArgumentException("Unknown kind: " + kind);
        });
  }

  /** Sealed domain-error hierarchy that exercises every supported mapping rule. */
  public sealed interface DomainError {

    /** Heuristic match → 404. */
    record UserNotFoundError(String userId) implements DomainError {}

    /** Heuristic match → 400. */
    record ValidationError(String field) implements DomainError {}

    /** Heuristic match (token "Invalid") → 400. */
    record MfaCodeInvalidError(String code) implements DomainError {}

    /** Heuristic match → 403. */
    record ForbiddenAction(String action) implements DomainError {}

    /** Heuristic match → 403. */
    record AuthorizationError(String reason) implements DomainError {}

    /** Heuristic match → 401. */
    record AuthenticationError(String reason) implements DomainError {}

    /** Heuristic match → 401. */
    record UnauthorizedAccess(String reason) implements DomainError {}

    /** Requires explicit mapping → 409. */
    record MfaAlreadyEnrolledError(String userId) implements DomainError {}

    /** Requires explicit mapping → 422. */
    record PaymentDeclinedError(String reason) implements DomainError {}

    /**
     * Requires explicit mapping → 429 and surfaces a {@code Retry-After} header by implementing
     * {@link HttpHeaderCarrier}.
     */
    record MfaThrottledError(int retryAfterSeconds) implements DomainError, HttpHeaderCarrier {
      @Override
      public Map<String, String> headers() {
        return Map.of("Retry-After", Integer.toString(retryAfterSeconds));
      }
    }

    /** No heuristic, no mapping → falls back to the configured default. */
    record UnmappedError(String detail) implements DomainError {}
  }
}
