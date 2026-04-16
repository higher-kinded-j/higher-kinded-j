// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import org.higherkindedj.hkt.either.Either;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test controller for verifying that the {@code hkj.web.either.default-error-status} property
 * governs the HTTP status of {@code Left} values whose class name matches none of the {@code
 * ErrorStatusCodeMapper} heuristics.
 *
 * <p>Uses a sealed domain-error hierarchy with three variants:
 *
 * <ul>
 *   <li>{@code NotFoundErr} — matches the {@code NotFound} heuristic → 404
 *   <li>{@code Dup} — matches no heuristic → configured default
 *   <li>{@code Pers} — matches no heuristic → configured default
 * </ul>
 *
 * <p>Exists as a top-level class (rather than a nested static class inside the test) because
 * {@code @WebMvcTest} requires the controller to be independently resolvable by Spring's bean
 * registration machinery.
 *
 * @see org.higherkindedj.spring.example.EitherDefaultErrorStatusSliceTest
 */
@RestController
@RequestMapping("/api/either-status")
public class EitherDefaultErrorStatusController {

  /** Creates an EitherDefaultErrorStatusController. */
  public EitherDefaultErrorStatusController() {}

  /** Sealed domain error hierarchy with variants that do and do not match heuristics. */
  public sealed interface DomainError
      permits DomainError.NotFoundErr, DomainError.Dup, DomainError.Pers {
    record NotFoundErr(String id) implements DomainError {}

    record Dup(String id) implements DomainError {}

    record Pers(String op) implements DomainError {}
  }

  /**
   * Returns a Left(NotFoundErr) — the heuristic should map this to 404.
   *
   * @return Either with a NotFoundErr left value
   */
  @GetMapping("/not-found")
  public Either<DomainError, String> notFound() {
    return Either.left(new DomainError.NotFoundErr("x"));
  }

  /**
   * Returns a Left(Dup) — no heuristic match, should fall back to configured default.
   *
   * @return Either with a Dup left value
   */
  @GetMapping("/duplicate")
  public Either<DomainError, String> duplicate() {
    return Either.left(new DomainError.Dup("x"));
  }

  /**
   * Returns a Left(Pers) — no heuristic match, should fall back to configured default.
   *
   * @return Either with a Pers left value
   */
  @GetMapping("/persistence")
  public Either<DomainError, String> persistence() {
    return Either.left(new DomainError.Pers("save"));
  }
}
