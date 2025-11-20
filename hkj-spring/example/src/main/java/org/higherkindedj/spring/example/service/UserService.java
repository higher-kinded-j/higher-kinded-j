package org.higherkindedj.spring.example.service;

import org.higherkindedj.hkt.Either;
import org.higherkindedj.spring.example.domain.DomainError;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.UserNotFoundError;
import org.higherkindedj.spring.example.domain.ValidationError;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User service demonstrating Either-based error handling.
 * All methods return Either<DomainError, T> instead of throwing exceptions.
 */
@Service
public class UserService {

  // In-memory "database" for the example
  private final Map<String, User> users = new ConcurrentHashMap<>();

  public UserService() {
    // Pre-populate with some test users
    users.put("1", new User("1", "alice@example.com", "Alice", "Smith"));
    users.put("2", new User("2", "bob@example.com", "Bob", "Jones"));
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
}
