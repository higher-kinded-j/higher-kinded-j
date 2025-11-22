package org.higherkindedj.spring.example.controller;

import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.spring.example.domain.DomainError;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller demonstrating Either-based error handling.
 * <p>
 * The EitherReturnValueHandler automatically converts:
 * - Right(user) → HTTP 200 with user JSON
 * - Left(UserNotFoundError) → HTTP 404 with error JSON
 * - Left(ValidationError) → HTTP 400 with error JSON
 * </p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  /**
   * Get user by ID.
   * <p>
   * Examples:
   * - GET /api/users/1 → 200 OK with user JSON
   * - GET /api/users/999 → 404 Not Found with error JSON
   * </p>
   */
  @GetMapping("/{id}")
  public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);
  }

  /**
   * Get all users.
   * <p>
   * Example:
   * - GET /api/users → 200 OK with list of users
   * </p>
   */
  @GetMapping
  public Either<DomainError, List<User>> getAllUsers() {
    return userService.findAll();
  }

  /**
   * Create a new user.
   * <p>
   * Examples:
   * - POST /api/users with valid data → 200 OK with created user
   * - POST /api/users with invalid email → 400 Bad Request with error JSON
   * </p>
   */
  @PostMapping
  public Either<DomainError, User> createUser(@RequestBody CreateUserRequest request) {
    return userService.create(
        request.email(),
        request.firstName(),
        request.lastName()
    );
  }

  /**
   * Demonstrates composing Either values.
   * <p>
   * Chains two operations:
   * 1. Find user by ID
   * 2. Return just the email
   * </p>
   */
  @GetMapping("/{id}/email")
  public Either<DomainError, String> getUserEmail(@PathVariable String id) {
    return userService.findById(id)
        .map(User::email);  // Functor map - transforms the Right value
  }

  /**
   * Request DTO for creating users.
   */
  public record CreateUserRequest(
      String email,
      String firstName,
      String lastName
  ) {}
}
