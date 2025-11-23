// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedKindHelper;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.spring.example.domain.DomainError;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.UserNotFoundError;
import org.higherkindedj.spring.example.domain.ValidationError;
import org.springframework.stereotype.Service;

/**
 * User service demonstrating Either-based error handling. All methods return Either<DomainError, T>
 * instead of throwing exceptions.
 */
@Service
public class UserService {

  // In-memory "database" for the example
  private final Map<String, User> users = new ConcurrentHashMap<>();

  public UserService() {
    // Pre-populate with some test users
    users.put("1", new User("1", "alice@example.com", "Alice", "Smith"));
    users.put("2", new User("2", "bob@example.com", "Bob", "Johnson"));
    users.put("3", new User("3", "charlie@example.com", "Charlie", "Brown"));
  }

  /**
   * Find a user by ID.
   *
   * @param id the user ID
   * @return Either a UserNotFoundError (Left) or the User (Right)
   */
  public Either<DomainError, User> findById(String id) {
    User user = users.get(id);
    if (user == null) {
      return Either.left(new UserNotFoundError(id));
    }
    return Either.right(user);
  }

  /**
   * Get all users.
   *
   * @return Either an error or the list of all users
   */
  public Either<DomainError, List<User>> findAll() {
    return Either.right(List.copyOf(users.values()));
  }

  /**
   * Find user by email.
   *
   * @param email the email to search for
   * @return Either a UserNotFoundError (Left) or the User (Right)
   */
  public Either<DomainError, User> findByEmail(String email) {
    User user =
        users.values().stream().filter(u -> u.email().equals(email)).findFirst().orElse(null);

    if (user == null) {
      return Either.left(new UserNotFoundError("email:" + email));
    }
    return Either.right(user);
  }

  /**
   * Create a new user with validation.
   *
   * @param email the email
   * @param firstName the first name
   * @param lastName the last name
   * @return Either a ValidationError (Left) or the created User (Right)
   */
  public Either<DomainError, User> create(String email, String firstName, String lastName) {
    // Simple validation
    if (email == null || !email.contains("@")) {
      return Either.left(new ValidationError("email", "Invalid email format"));
    }
    if (firstName == null || firstName.trim().isEmpty()) {
      return Either.left(new ValidationError("firstName", "First name cannot be empty"));
    }
    if (lastName == null || lastName.trim().isEmpty()) {
      return Either.left(new ValidationError("lastName", "Last name cannot be empty"));
    }

    String id = String.valueOf(users.size() + 1);
    User user = new User(id, email, firstName, lastName);
    users.put(id, user);
    return Either.right(user);
  }

  /**
   * Validate email format.
   *
   * @param email the email to validate
   * @return Valid(email) or Invalid(ValidationError)
   */
  private Validated<List<ValidationError>, String> validateEmail(String email) {
    if (email == null || !email.contains("@")) {
      return Validated.invalid(List.of(new ValidationError("email", "Invalid email format")));
    }
    return Validated.valid(email);
  }

  /**
   * Validate first name is not empty.
   *
   * @param firstName the first name to validate
   * @return Valid(firstName) or Invalid(ValidationError)
   */
  private Validated<List<ValidationError>, String> validateFirstName(String firstName) {
    if (firstName == null || firstName.trim().isEmpty()) {
      return Validated.invalid(
          List.of(new ValidationError("firstName", "First name cannot be empty")));
    }
    return Validated.valid(firstName);
  }

  /**
   * Validate last name is not empty.
   *
   * @param lastName the last name to validate
   * @return Valid(lastName) or Invalid(ValidationError)
   */
  private Validated<List<ValidationError>, String> validateLastName(String lastName) {
    if (lastName == null || lastName.trim().isEmpty()) {
      return Validated.invalid(
          List.of(new ValidationError("lastName", "Last name cannot be empty")));
    }
    return Validated.valid(lastName);
  }

  /**
   * Create a new user with accumulating validation. Unlike the Either-based create() method which
   * fails fast, this method accumulates ALL validation errors and returns them together.
   *
   * @param email the email
   * @param firstName the first name
   * @param lastName the last name
   * @return Validated with all errors accumulated, or the created User
   */
  public Validated<List<ValidationError>, User> validateAndCreate(
      String email, String firstName, String lastName) {

    // Create an Applicative instance for Validated with List<ValidationError> accumulation
    Applicative<ValidatedKind.Witness<List<ValidationError>>> applicative =
        ValidatedMonad.instance(Semigroups.list());

    // Combine all three validations using Applicative.map3
    // This will accumulate ALL errors if multiple validations fail
    var validatedEmail = validateEmail(email);
    var validatedFirstName = validateFirstName(firstName);
    var validatedLastName = validateLastName(lastName);

    // Use map3 to combine the three validated values
    var result =
        applicative.map3(
            ValidatedKindHelper.VALIDATED.widen(validatedEmail),
            ValidatedKindHelper.VALIDATED.widen(validatedFirstName),
            ValidatedKindHelper.VALIDATED.widen(validatedLastName),
            (e, f, l) -> {
              String id = String.valueOf(users.size() + 1);
              User user = new User(id, e, f, l);
              users.put(id, user);
              return user;
            });

    // Narrow back from Kind to Validated
    return ValidatedKindHelper.VALIDATED.narrow(result);
  }
}
