// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.controller;

import java.util.List;
import java.util.Map;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.spring.example.domain.DomainError;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.UserNotFoundError;
import org.higherkindedj.spring.example.service.UserService;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;

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
  private final JsonMapper jsonMapper;

  /**
   * Constructs a UserController.
   *
   * @param userService the user service
   * @param jsonMapper the application's Jackson 3.x mapper, used by the debug endpoint to probe
   *     whether HkjJacksonModule is actually registered
   */
  public UserController(UserService userService, JsonMapper jsonMapper) {
    this.userService = userService;
    this.jsonMapper = jsonMapper;
  }

  /**
   * Get user by ID.
   *
   * <p>Examples: - GET /api/users/1 → 200 OK with user JSON - GET /api/users/999 → 404 Not Found
   * with error JSON
   *
   * @param id the user ID
   * @return Either containing a DomainError or the User
   */
  @GetMapping("/{id}")
  public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);
  }

  /**
   * Get all users.
   *
   * <p>Example: - GET /api/users → 200 OK with list of users
   *
   * @return Either containing a DomainError or the list of users
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
   *
   * @param request the user creation request
   * @return Either containing a DomainError or the created User
   */
  @PostMapping
  public Either<DomainError, User> createUser(@RequestBody CreateUserRequest request) {
    return userService.create(request.email(), request.firstName(), request.lastName());
  }

  /**
   * Parse a wire-side user into the domain — the 422 leg (issue #627).
   *
   * <p>The generated {@code UserMappingImpl.parse} validates every field at once, accumulating a
   * located {@code FieldError} per bad field, and the returned {@code Validated} is rendered by the
   * {@code ValidationPathReturnValueHandler} directly:
   *
   * <ul>
   *   <li>all fields valid → 200 with the parsed {@link User};
   *   <li>any bad fields → one {@code hkj.web.validation-field-error-status} response (default 422
   *       Unprocessable Content) listing <em>every</em> bad field as {@code { path, segments,
   *       message }};
   *   <li>an absent (or {@code null}) field → the same 422, located as {@code "must not be null"}
   *       under the field's path — the bean-shaped wire's null-guarded parse, never an exception.
   * </ul>
   *
   * <p>No service call and no error wrapping: unlike {@link #patchUser}, which mixes not-found with
   * validation and therefore folds both into one {@code Either} channel, a pure parse boundary
   * returns its {@code Validated} as-is.
   *
   * @param dto the wire-side user representation
   * @return the parse result — every located field error, or the parsed User
   */
  @PostMapping("/parse")
  public Validated<NonEmptyList<FieldError>, User> parseUser(@RequestBody UserDto dto) {
    return UserMappingImpl.INSTANCE.parse(dto);
  }

  /**
   * Partially update a user (sparse PATCH, issue #645).
   *
   * <p>The request bean carries only the fields to change; every omitted field is {@code null},
   * meaning <em>leave unchanged</em>. {@link UserPatchMapping}'s generated {@code updateFrom} folds
   * the present fields into an update; {@code id} is never mapped, so the resource cannot move.
   *
   * <p>Three outcomes flow through one {@code Either<DomainError, User>} channel:
   *
   * <ul>
   *   <li>unknown id → {@code Left(UserNotFoundError)} → 404 (from {@code findById});
   *   <li>a present but invalid field (e.g. a malformed email) → {@code Left(PatchValidationError)}
   *       → 400, carrying every located {@code FieldError} at once;
   *   <li>otherwise → {@code Right(patched)} → 200 with the updated user.
   * </ul>
   *
   * <p>This endpoint deliberately stays on the Either leg rather than the 422 leg ({@link
   * #parseUser}): a PATCH mixes not-found with validation, and one {@code Either} channel carries
   * both; a pure parse boundary has only validation to report.
   *
   * @param id the id of the user to patch
   * @param request the partial-update request
   * @return Either a DomainError or the patched User
   */
  @PatchMapping("/{id}")
  public Either<DomainError, User> patchUser(
      @PathVariable String id, @RequestBody UserPatchRequest request) {
    // updateFrom validates and folds the present fields once, off the store lock; patch then
    // applies that accumulated update to the current user atomically.
    return userService.patch(id, UserPatchMappingImpl.INSTANCE.updateFrom(request));
  }

  /**
   * Demonstrates composing Either values.
   *
   * <p>Chains two operations: 1. Find user by ID 2. Return just the email
   *
   * @param id the user ID
   * @return Either containing a DomainError or the user's email
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
   *
   * @return BatchResult containing sample user results
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
   * Debug endpoint reporting the <em>actual</em> HKJ Jackson module state.
   *
   * <p>Jackson 3.x does not expose registered module IDs directly, so instead of echoing a
   * hardcoded value this endpoint probes real behaviour: it serialises a sample {@code Either} with
   * the injected {@link JsonMapper} and checks whether the tagged {@code isRight} shape (produced
   * only by HkjJacksonModule's EitherSerializer) came back.
   *
   * <p>Example response:
   *
   * <pre>
   * {
   *   "hkjModulePresent": true,
   *   "eitherProbe": "{\"isRight\":true,\"right\":\"probe\"}",
   *   "message": "hkjModulePresent is derived by serialising a sample Either with the application's JsonMapper"
   * }
   * </pre>
   *
   * @return a map describing the observed Jackson configuration
   */
  @GetMapping("/debug/jackson-modules")
  public Map<String, Object> getJacksonModules() {
    String eitherProbe = jsonMapper.writeValueAsString(Either.<String, String>right("probe"));
    boolean hkjModulePresent = eitherProbe.contains("\"isRight\"");
    return Map.of(
        "hkjModulePresent",
        hkjModulePresent,
        "eitherProbe",
        eitherProbe,
        "message",
        "hkjModulePresent is derived by serialising a sample Either with the application's"
            + " JsonMapper");
  }

  /**
   * Response DTO demonstrating nested Either values. The List of Either values will be serialized
   * using Jackson's EitherSerializer, producing wrapped JSON with isRight/left/right structure.
   *
   * @param batchId the batch identifier
   * @param results the list of Either results for each user lookup
   */
  public record BatchResult(String batchId, List<Either<DomainError, User>> results) {}
}
