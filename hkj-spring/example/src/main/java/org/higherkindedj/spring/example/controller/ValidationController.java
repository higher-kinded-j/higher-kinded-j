// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import java.util.List;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.ValidationError;
import org.higherkindedj.spring.example.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller demonstrating Validated-based error accumulation.
 *
 * <p>The key difference from Either-based validation: - Either: Fails fast on the FIRST error -
 * Validated: Accumulates ALL errors and returns them together
 *
 * <p>The ValidatedReturnValueHandler automatically converts: - Valid(user) → HTTP 200 with user
 * JSON - Invalid(errors) → HTTP 400 with all accumulated errors as JSON
 */
@RestController
@RequestMapping("/api/validation")
public class ValidationController {

  private final UserService userService;

  public ValidationController(UserService userService) {
    this.userService = userService;
  }

  /**
   * Create user with accumulating validation.
   *
   * <p>Examples: - POST /api/validation/users with all valid data → 200 OK with user JSON - POST
   * /api/validation/users with invalid email only → 400 Bad Request with 1 error - POST
   * /api/validation/users with ALL invalid data → 400 Bad Request with ALL 3 errors
   *
   * <p>Compare this to the Either-based endpoint at POST /api/users which would only return the
   * FIRST validation error.
   */
  @PostMapping("/users")
  public Validated<List<ValidationError>, User> createUserWithValidation(
      @RequestBody CreateUserRequest request) {
    return userService.validateAndCreate(request.email(), request.firstName(), request.lastName());
  }

  /**
   * Example of testing what happens when we send invalid data.
   *
   * <p>Try these curl commands:
   *
   * <pre>
   * # All fields invalid - returns ALL 3 errors
   * curl -X POST http://localhost:8080/api/validation/users \
   *   -H "Content-Type: application/json" \
   *   -d '{"email":"invalid","firstName":"","lastName":""}'
   *
   * # Just email invalid - returns 1 error
   * curl -X POST http://localhost:8080/api/validation/users \
   *   -H "Content-Type: application/json" \
   *   -d '{"email":"invalid","firstName":"John","lastName":"Doe"}'
   *
   * # All valid - returns 200 with user
   * curl -X POST http://localhost:8080/api/validation/users \
   *   -H "Content-Type: application/json" \
   *   -d '{"email":"john@example.com","firstName":"John","lastName":"Doe"}'
   * </pre>
   */

  /**
   * Validate a batch of user creation requests demonstrating Jackson serialization of nested
   * Validated values.
   *
   * <p>This endpoint returns a DTO containing a List of Validated values. Unlike the top-level
   * Validated endpoint above (which is unwrapped by ValidatedReturnValueHandler), the nested
   * Validated values in the list are serialized by Jackson using the custom ValidatedSerializer.
   *
   * <p>Example response:
   *
   * <pre>
   * {
   *   "batchId": "batch-1234567890",
   *   "results": [
   *     {"valid": true, "value": {"id": "4", ...}},
   *     {"valid": false, "errors": [{"field": "email", ...}, ...]},
   *     {"valid": true, "value": {"id": "5", ...}}
   *   ]
   * }
   * </pre>
   */
  @PostMapping("/batch")
  public ValidationBatchResult validateBatch(@RequestBody List<CreateUserRequest> requests) {
    List<Validated<List<ValidationError>, User>> results =
        requests.stream()
            .map(req -> userService.validateAndCreate(req.email(), req.firstName(), req.lastName()))
            .toList();

    return new ValidationBatchResult("batch-" + System.currentTimeMillis(), results);
  }

  /**
   * Response DTO demonstrating nested Validated values. The List of Validated values will be
   * serialized using Jackson's ValidatedSerializer, producing wrapped JSON with valid/value/errors
   * structure.
   */
  public record ValidationBatchResult(
      String batchId, List<Validated<List<ValidationError>, User>> results) {}
}
