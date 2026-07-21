// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedKindHelper;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.spring.example.domain.DomainError;
import org.higherkindedj.spring.example.domain.PatchValidationError;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.UserNotFoundError;
import org.higherkindedj.spring.example.domain.ValidationError;
import org.springframework.stereotype.Service;

/**
 * User service demonstrating Either-based error handling. All methods return {@code
 * Either<DomainError, T>} instead of throwing exceptions.
 */
@Service
public class UserService {

  // In-memory "database" for the example
  private final Map<String, User> users = new ConcurrentHashMap<>();

  // Monotonic id generator: seeded past the pre-populated users so generated ids
  // never collide, even after deletions or under concurrent creates.
  private final AtomicLong idGenerator = new AtomicLong(3);

  /** Creates a new user service with pre-populated test data. */
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
   * Atomically apply a sparse patch to a stored user: look up, apply, and replace in one step, so
   * concurrent PATCHes cannot read the same user and overwrite each other (the read-modify-write
   * lost-update race). The patch is an {@link Edits.Accumulated} — its validation already ran,
   * source-independently, when {@code updateFrom} built it — so only the pure {@code apply} runs
   * under {@link ConcurrentHashMap#compute}'s per-key lock.
   *
   * @param id the id of the user to patch
   * @param patch the accumulated sparse update (from {@code UserPatchMappingImpl.updateFrom})
   * @return {@code Left(UserNotFoundError)} if no such user, {@code Left(PatchValidationError)} if
   *     a present field was invalid (nothing written), else {@code Right(patched)}
   */
  public Either<DomainError, User> patch(String id, Edits.Accumulated<User> patch) {
    // compute() applies the patch atomically under the key's lock; it returns the value to store,
    // but the caller also needs the Either outcome, so that is threaded out through this one cell.
    AtomicReference<Either<DomainError, User>> outcome = new AtomicReference<>();
    users.compute(
        id,
        (key, current) -> {
          Either<DomainError, User> result =
              current == null
                  ? Either.left(new UserNotFoundError(id))
                  : patch.apply(current).toEither().<DomainError>mapLeft(PatchValidationError::new);
          outcome.set(result);
          // absent -> null (stays absent); invalid -> current (unchanged); valid -> updated.
          return result.fold(error -> current, updated -> updated);
        });
    return outcome.get();
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

    String id = String.valueOf(idGenerator.incrementAndGet());
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
              String id = String.valueOf(idGenerator.incrementAndGet());
              User user = new User(id, e, f, l);
              users.put(id, user);
              return user;
            });

    // Narrow back from Kind to Validated
    return ValidatedKindHelper.VALIDATED.narrow(result);
  }
}
