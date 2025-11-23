// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.spring.example.domain.DomainError;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.UserNotFoundError;
import org.higherkindedj.spring.example.service.UserService;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller demonstrating Either-based error handling.
 *
 * <p>The EitherReturnValueHandler automatically converts: - Right(user) → HTTP 200 with user JSON -
 * Left(UserNotFoundError) → HTTP 404 with error JSON - Left(ValidationError) → HTTP 400 with error
 * JSON
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;
  private final ObjectMapper objectMapper;

  public UserController(UserService userService, ObjectMapper objectMapper) {
    this.userService = userService;
    this.objectMapper = objectMapper;
  }

  /**
   * Get user by ID.
   *
   * <p>Examples: - GET /api/users/1 → 200 OK with user JSON - GET /api/users/999 → 404 Not Found
   * with error JSON
   */
  @GetMapping("/{id}")
  public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);
  }

  /**
   * Get all users.
   *
   * <p>Example: - GET /api/users → 200 OK with list of users
   */
  @GetMapping
  public Either<DomainError, List<User>> getAllUsers() {
    return userService.findAll();
  }

  /**
   * Create a new user.
   *
   * <p>Examples: - POST /api/users with valid data → 200 OK with created user - POST /api/users
   * with invalid email → 400 Bad Request with error JSON
   */
  @PostMapping
  public Either<DomainError, User> createUser(@RequestBody CreateUserRequest request) {
    return userService.create(request.email(), request.firstName(), request.lastName());
  }

  /**
   * Demonstrates composing Either values.
   *
   * <p>Chains two operations: 1. Find user by ID 2. Return just the email
   */
  @GetMapping("/{id}/email")
  public Either<DomainError, String> getUserEmail(@PathVariable String id) {
    return userService.findById(id).map(User::email); // Functor map - transforms the Right value
  }

  /**
   * Get batch of users demonstrating Jackson serialization of nested Either values.
   *
   * <p>This endpoint returns a DTO containing a List of Either values. Unlike the top-level Either
   * endpoints above (which are unwrapped by EitherReturnValueHandler), the nested Either values in
   * the list are serialized by Jackson using the custom EitherSerializer.
   *
   * <p>Example response:
   *
   * <pre>
   * {
   *   "batchId": "batch-1234567890",
   *   "results": [
   *     {"isRight": true, "right": {"id": "1", ...}},
   *     {"isRight": false, "left": {"userId": "999", "message": "..."}},
   *     {"isRight": true, "right": {"id": "2", ...}}
   *   ]
   * }
   * </pre>
   */
  @GetMapping("/batch")
  public BatchResult getUserBatch() {
    return new BatchResult(
        "batch-" + System.currentTimeMillis(),
        List.of(
            Either.right(new User("1", "alice@example.com", "Alice", "Smith")),
            Either.left(new UserNotFoundError("999")),
            Either.right(new User("2", "bob@example.com", "Bob", "Jones"))));
  }

  /**
   * Debug endpoint to verify Jackson module registration.
   *
   * <p>This endpoint shows which Jackson modules are registered with the ObjectMapper, including
   * the HkjJacksonModule for Either and Validated serialization.
   *
   * <p>Example response:
   *
   * <pre>
   * {
   *   "registeredModules": ["ParameterNamesModule", "Jdk8Module", "JavaTimeModule", "HkjJacksonModule"],
   *   "hkjModulePresent": true
   * }
   * </pre>
   */
  @GetMapping("/debug/jackson-modules")
  public Map<String, Object> getJacksonModules() {
    List<String> moduleIds =
        objectMapper.getRegisteredModuleIds().stream()
            .map(Object::toString)
            .collect(Collectors.toList());

    return Map.of(
        "registeredModules", moduleIds, "hkjModulePresent", moduleIds.contains("HkjJacksonModule"));
  }

  /**
   * Response DTO demonstrating nested Either values. The List of Either values will be serialized
   * using Jackson's EitherSerializer, producing wrapped JSON with isRight/left/right structure.
   */
  public record BatchResult(String batchId, List<Either<DomainError, User>> results) {}
}
