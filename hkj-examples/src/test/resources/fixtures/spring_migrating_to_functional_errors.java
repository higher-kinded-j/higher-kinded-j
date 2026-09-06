// Fixture for hkj-book/src/spring/migrating_to_functional_errors.md
//
// The page shows each step of the migration twice: the exception-based version it is leaving and
// the functional one it arrives at. The functional halves are gated, so the code a reader copies is
// held to the library; the exception-based halves are quotations of what not to write (see
// BOOK-SNIPPETS.md).
//
// `OrderError` is deliberately NOT sealed here. The page declares its own sealed version, and a
// sealed fixture copy would be broken by the snippet that declares its own `UserNotFoundError`.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

record User(String id, String email, String name) {}

record UserRequest(String email, String firstName, String lastName, String name, int age) {}

record Order(String id, String userId) {}

record OrderRequest(String userId, List<String> items, String payment) {}

record OrderItem(String id) {}

record Payment(String id) {}

record Stock(boolean isAvailable, List<String> unavailableItems) {}

record ErrorResponse(String code, String message) {

  ErrorResponse(String message) {
    this("ERROR", message);
  }
}

class UserNotFoundException extends RuntimeException {

  UserNotFoundException(String id) {
    super(id);
  }
}

class ItemNotFoundException extends RuntimeException {}

class OutOfStockException extends RuntimeException {

  OutOfStockException() {}

  OutOfStockException(List<String> items) {
    super(String.join(", ", items));
  }
}

// Neither error interface is sealed here. The page declares its own sealed versions - three of
// them, permitting three different sets - and a sealed fixture copy is broken by whichever
// snippet declares a variant for itself.
interface DomainError {}

interface OrderError {}

record UserNotFoundError(String userId) implements DomainError, OrderError {}

record ValidationError(String field, String message) implements DomainError {}

record OutOfStockError(List<String> items) implements OrderError {}

record PaymentFailedError(String reason) implements OrderError {}

interface Inventory {
  Stock check(List<String> items);
}

interface Payments {
  Payment take(String payment);
}

interface UserRepository {
  Optional<User> findById(String id);

  boolean existsByEmail(String email);
}

interface UserService {
  Either<DomainError, User> findById(String id);

  ValidationPath<NonEmptyList<ValidationError>, User> validateAndCreate(UserRequest request);
}

interface OrderService {
  Either<DomainError, Order> findById(String id);

  Either<DomainError, Order> verifyOwnership(Order order, String userId);

  Either<DomainError, OrderItem> findItem(Order order, String itemId);

  Either<OrderError, Stock> checkStock(List<String> items);

  Either<OrderError, Payment> processPayment(String payment);
}

interface AsyncUserService {
  CompletableFuturePath<User> findByIdAsync(String id);
}

interface AsyncInventoryService {
  CompletableFuturePath<Stock> checkStockAsync(List<String> items);
}

interface AsyncPaymentService {
  CompletableFuturePath<Payment> processPaymentAsync(String payment);
}

interface AsyncOrderServiceApi {
  CompletableFuturePath<Order> getOrderAsync(String id);
}

class Fixture {

  @Autowired UserRepository repository;

  @Autowired UserService userService;

  @Autowired OrderService orderService;

  @Autowired AsyncUserService asyncUserService;

  @Autowired AsyncInventoryService asyncInventoryService;

  @Autowired AsyncPaymentService asyncPaymentService;

  @Autowired AsyncOrderServiceApi asyncOrderService;

  static final Executor asyncExecutor = Runnable::run;

  static User createUser(String email, String name) {
    return new User("1", email, name);
  }

  static User createUser(String email, String firstName, String lastName) {
    return new User("1", email, firstName + " " + lastName);
  }

  static User createUser(String email, String name, int age) {
    return new User("1", email, name);
  }

  static Order createOrder(OrderRequest request, Payment payment) {
    return new Order(payment.id(), request.userId());
  }

  static OrderError toDomainError(DomainError error) {
    return new PaymentFailedError("mapped");
  }

  CompletableFuturePath<Stock> checkStockAsync(List<String> items) {
    return asyncInventoryService.checkStockAsync(items);
  }

  CompletableFuturePath<Payment> processPaymentAsync(String payment) {
    return asyncPaymentService.processPaymentAsync(payment);
  }

  CompletableFuturePath<User> findByIdAsync(String id) {
    return asyncUserService.findByIdAsync(id);
  }

  ValidationPath<NonEmptyList<ValidationError>, String> validateEmail(String email) {
    return email != null && email.contains("@")
        ? Path.validNel(email)
        : Path.invalidNel(new ValidationError("email", "Invalid email format"));
  }

  ValidationPath<NonEmptyList<ValidationError>, String> validateName(String name) {
    return name != null && !name.isBlank()
        ? Path.validNel(name)
        : Path.invalidNel(new ValidationError("name", "must not be blank"));
  }

  // Package-private and not static: a snippet redeclares `validateEmail`, and a method may
  // only override one of the same access.
  ValidationPath<NonEmptyList<ValidationError>, String> validateFirstName(String name) {
    return validateName(name);
  }

  ValidationPath<NonEmptyList<ValidationError>, String> validateLastName(String name) {
    return validateName(name);
  }

  ValidationPath<NonEmptyList<ValidationError>, Integer> validateAge(int age) {
    return age > 0 ? Path.validNel(age) : Path.invalidNel(new ValidationError("age", "must be > 0"));
  }

  ValidationPath<NonEmptyList<ValidationError>, String> validateUniqueEmail(String email) {
    return validateEmail(email);
  }
}
