// Fixture for hkj-book/src/spring/spring_boot_integration.md
//
// The chapter's controllers, services and tests are the reader's own, so they are declared here
// with the shapes the page assumes. `UserMapping` and `UserPatchMapping` are REAL @GenerateMapping
// specs, mirroring the ones in hkj-spring/example, so the processor generates UserMappingImpl and
// UserPatchMappingImpl on the gate's processor path and the page's parse/patch snippets name the
// genuine article.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.CompletableFuturePath;
import org.higherkindedj.hkt.effect.EitherOrBothPath;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.higherkindedj.spring.autoconfigure.HkjAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjJacksonAutoConfiguration;
import org.higherkindedj.spring.autoconfigure.HkjWebMvcAutoConfiguration;
import org.higherkindedj.spring.security.EitherAuthenticationConverter;
import org.higherkindedj.spring.security.ValidatedUserDetailsService;
import org.higherkindedj.spring.web.returnvalue.ErrorStatusCodeMapper;
import org.higherkindedj.spring.web.returnvalue.ErrorStatusCodeStrategy;
import org.higherkindedj.spring.web.returnvalue.HttpHeaderCarrier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

// ---------------------------------------------------------------------------------------------
// The reader's own domain, which the chapter quite properly elides.
// ---------------------------------------------------------------------------------------------

record User(String id, String email, String firstName, String lastName) {}

record UserDto(String id, String email, String firstName, String lastName) {}

/**
 * A PATCH body is a bean, not a record: sparse PATCH reads null as "not provided, leave
 * unchanged", and a record component is always present.
 */
class UserPatchRequest {

  private String email;
  private String firstName;
  private String lastName;

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }
}

record UserRequest(String email, String firstName, String lastName) {}

record Order(String id, String userId) {}

record OrderRequest(String userId, List<String> items, String payment) {}

record Payment(String id) {}

record Availability(boolean allAvailable, List<String> unavailableItems) {}

record OrderSummary(String userId, OrderRequest request, Payment payment) {}

record Profile(String bio) {}

record EnrichedUser(User user, Profile profile, OrderSummary orders) {}

record TickEvent(long sequence) {}

record ImportRequest(List<String> rows) {}

record ImportSummary(int imported) {}

record ImportWarning(String message) {}

/** The exception-based version the page contrasts with, plus the async failure it raises. */
class UserNotFoundException extends RuntimeException {}

class ValidationException extends RuntimeException {}

class OutOfStockException extends RuntimeException {

  OutOfStockException(List<String> items) {
    super(String.join(", ", items));
  }
}

record ErrorResponse(String message) {}

/** The typed error channel. A snippet that shows the hierarchy declares its own. */
sealed interface DomainError
    permits UserNotFoundError,
        ValidationError,
        AuthorizationError,
        AuthenticationError,
        PatchValidationError,
        MfaThrottledError {}

record UserNotFoundError(String id) implements DomainError {}

record ValidationError(String field, String message) implements DomainError {}

record AuthorizationError(String detail) implements DomainError {}

record AuthenticationError(String detail) implements DomainError {}

record PatchValidationError(NonEmptyList<FieldError> errors) implements DomainError {}

record MfaThrottledError(int retryAfterSeconds) implements DomainError, HttpHeaderCarrier {

  @Override
  public Map<String, String> headers() {
    return Map.of("Retry-After", Integer.toString(retryAfterSeconds));
  }
}

// ---------------------------------------------------------------------------------------------
// The reader's own services. A snippet that declares its own shadows these.
// ---------------------------------------------------------------------------------------------

/** The exception-based service the chapter's "before" snippet calls. */
interface ThrowingUserService {
  User findById(String id);
}

interface UserService {
  Either<DomainError, User> findById(String id);

  List<User> findAll();

  Validated<List<ValidationError>, User> validateAndCreate(UserRequest request);

  Validated<DomainError, UserRequest> validateUpdate(UserRequest request);

  User update(String id, UserRequest request);

  Either<DomainError, Void> delete(String id);

  Either<DomainError, User> patch(String id, Edits.Accumulated<User> patch);
}

interface OrderService {
  EitherPath<DomainError, Order> create(OrderRequest request);

  MaybePath<Void> cancel(String id);

  Either<DomainError, List<Order>> getOrdersForUser(User user);

  Either<DomainError, Order> findById(String id);

  Either<DomainError, Order> verifyOwnership(Order order, String userId);
}

interface AsyncUserService {
  CompletableFuturePath<User> findByIdAsync(String id);
}

interface AsyncInventoryService {
  CompletableFuturePath<Availability> checkAvailabilityAsync(List<String> items);

  CompletableFuturePath<Availability> checkAvailability(List<String> items);
}

interface AsyncPaymentService {
  CompletableFuturePath<Payment> processPaymentAsync(String payment);

  CompletableFuturePath<Payment> processPayment(String payment);
}

interface OrderRepository {
  Order save(Order order);
}

/** A Spring Data style repository, which the FAQ wraps in a service. */
interface UserRepository {
  Optional<User> findById(String id);
}

interface VirtualThreadUserService {
  VTaskPath<User> findById(String id);

  VStreamPath<User> streamAllUsers();

  VStreamPath<TickEvent> streamTicks(int count);
}

interface ImportService {
  EitherOrBothPath<NonEmptyList<ImportWarning>, ImportSummary> importBatch(ImportRequest request);
}

// ---------------------------------------------------------------------------------------------
// The mapping specs, declared for real so the processor generates the Impls the page names.
// ---------------------------------------------------------------------------------------------

@GenerateMapping
interface UserMapping extends MappingSpec<User, UserDto> {

  default ValidatedPrism<String, String> email() {
    return ValidatedPrism.of(
        raw ->
            raw != null && raw.contains("@")
                ? Validated.validNel(raw)
                : Validated.invalidNel(FieldError.of("not a valid email address")),
        email -> email);
  }
}

@GenerateMapping
interface UserPatchMapping extends UpdateSpec<User, UserPatchRequest> {

  default ValidatedPrism<String, String> email() {
    return ValidatedPrism.of(
        raw ->
            raw != null && raw.contains("@")
                ? Validated.validNel(raw)
                : Validated.invalidNel(FieldError.of("not a valid email address")),
        email -> email);
  }
}

/** Named by the @WebMvcTest slice; the controller snippets declare their own. */
class UserController {}

class Fixture {

  static final UserService userService = sample();

  static final OrderService orderService = sample();

  static final AsyncUserService asyncUserService = sample();

  static final AsyncInventoryService asyncInventoryService = sample();

  static final AsyncPaymentService asyncPaymentService = sample();

  static final VirtualThreadUserService vtUserService = sample();

  static final ImportService importService = sample();

  static final ThrowingUserService throwingUserService = sample();

  static final String secret = "s3cr3t";

  // The test snippets are quoted a method at a time, so the harness they sit in is here.
  MockMvc mockMvc;

  static final User current = new User("1", "alice@example.com", "Alice", "Smith");

  static final Edits.Accumulated<User> patch = sample();

  // The gate compiles snippets; it never runs them.
  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static User findUser(String id) {
    return current;
  }

  static Profile fetchProfile(String id) {
    return new Profile("");
  }

  static OrderSummary fetchOrders(String id) {
    return sample();
  }

  static Order createOrderRecord(OrderRequest request, Payment payment) {
    return new Order("ORD-1", request.userId());
  }

  /** The accumulating field validators the ValidationPath builder composes. */
  static ValidationPath<NonEmptyList<ValidationError>, String> validateEmail(String email) {
    return email != null && email.contains("@")
        ? Path.validNel(email)
        : Path.invalidNel(new ValidationError("email", "Invalid email format"));
  }

  static ValidationPath<NonEmptyList<ValidationError>, String> validateName(
      String field, String name) {
    return name != null && !name.isBlank()
        ? Path.validNel(name)
        : Path.invalidNel(new ValidationError(field, "Name cannot be empty"));
  }
}
