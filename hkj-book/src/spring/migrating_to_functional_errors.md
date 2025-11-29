# Migrating from Exceptions to Functional Error Handling
## _A Practical Step-by-Step Guide_

~~~admonish info title="What You'll Learn"
- How to incrementally migrate exception-based code to functional patterns
- Converting exception-throwing methods to Either
- Replacing `@ExceptionHandler` methods with automatic response conversion
- Migrating validation logic to Validated
- Converting async operations to EitherT
- Maintaining backwards compatibility during migration
- Common migration patterns and pitfalls to avoid
~~~

## Overview

Migrating from exception-based error handling to functional patterns doesn't have to be all-or-nothing. This guide shows you how to migrate incrementally, maintaining backwards compatibility whilst gradually introducing type-safe error handling.

**Key Principle:** Start with new endpoints or the most problematic areas, then expand as you see the benefits.

---

## Incremental Migration

Start by using functional types for all **new** endpoints. This allows your team to learn the patterns without touching existing code.

**Approach:**
- Use Either/Validated for new controllers
- Leave existing exception-based endpoints unchanged
- Build confidence with the new patterns


Identify endpoints with complex error handling or frequent bugs. These are prime candidates for migration:
- Endpoints with multiple `@ExceptionHandler` methods
- Validation-heavy endpoints
- Async operations with complicated error propagation

Gradually migrate remaining endpoints as you touch them for other reasons (features, bug fixes, refactoring).

---

## Pattern 1: Simple Exception to Either

### Before: Exception-Throwing Method

```java
@Service
public class UserService {

    @Autowired
    private UserRepository repository;

    public User findById(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new UserNotFoundException(id));
    }
}

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public User getUser(@PathVariable String id) {
        return userService.findById(id);  // What exceptions can this throw?
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse("USER_NOT_FOUND", ex.getMessage()));
    }
}
```

**Potential Problems:**
- Error types hidden in implementation
- Requires reading method bodies to understand possible failures
- `@ExceptionHandler` catches exceptions from unrelated methods
- Testing requires exception mocking

### After: Either-Returning Method

```java
@Service
public class UserService {

    @Autowired
    private UserRepository repository;

    public Either<DomainError, User> findById(String id) {
        return repository.findById(id)
            .map(Either::<DomainError, User>right)
            .orElseGet(() -> Either.left(new UserNotFoundError(id)));
    }
}

// Domain error types
public sealed interface DomainError permits UserNotFoundError, ValidationError {
}

public record UserNotFoundError(String userId) implements DomainError {
}

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public Either<DomainError, User> getUser(@PathVariable String id) {
        return userService.findById(id);  // Clear: returns User or DomainError
    }

    // No @ExceptionHandler needed! Framework handles Either → HTTP conversion
}
```

**Some Benefits:**
- ✅ Errors explicit in method signature
- ✅ Compiler enforces error handling at call sites
- ✅ No `@ExceptionHandler` boilerplate
- ✅ Easy to test—no exception mocking

### Migration Steps

**Step 1:** Define your error types as a sealed interface hierarchy

```java
public sealed interface DomainError permits
    UserNotFoundError,
    ValidationError,
    AuthorizationError {
}

public record UserNotFoundError(String userId) implements DomainError {
}
```

**Step 2:** Convert service methods one at a time

```java
// Keep old method temporarily for backwards compatibility
@Deprecated
public User findById_OLD(String id) {
    return repository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
}

// New method with functional return type
public Either<DomainError, User> findById(String id) {
    return repository.findById(id)
        .map(Either::<DomainError, User>right)
        .orElseGet(() -> Either.left(new UserNotFoundError(id)));
}
```

**Step 3:** Update controller methods

```java
@GetMapping("/{id}")
public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);
}
```

**Step 4:** Remove `@ExceptionHandler` methods once all callers are migrated

```java
// DELETE THIS - no longer needed!
// @ExceptionHandler(UserNotFoundException.class)
// public ResponseEntity<ErrorResponse> handleNotFound(...)
```

---

## Pattern 2: Multiple Exceptions to Either

### Before: Multiple Exception Types

```java
@Service
public class OrderService {

    public Order processOrder(OrderRequest request) throws
            UserNotFoundException,
            InsufficientStockException,
            PaymentFailedException {

        User user = userService.findById(request.userId());  // throws UserNotFoundException
        checkStock(request.items());                          // throws InsufficientStockException
        processPayment(request.payment());                    // throws PaymentFailedException

        return createOrder(request);
    }
}

@RestController
public class OrderController {

    @PostMapping("/orders")
    public Order createOrder(@RequestBody OrderRequest request) {
        return orderService.processOrder(request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<?> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<?> handleOutOfStock(InsufficientStockException ex) {
        return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<?> handlePaymentFailed(PaymentFailedException ex) {
        return ResponseEntity.status(402).body(new ErrorResponse(ex.getMessage()));
    }
}
```

### After: Either with Discriminated Errors

```java
public sealed interface OrderError permits
    UserNotFoundError,
    OutOfStockError,
    PaymentFailedError {
}

@Service
public class OrderService {

    public Either<OrderError, Order> processOrder(OrderRequest request) {
        return userService.findById(request.userId())
            .mapLeft(this::toDomainError)  // Convert DomainError to OrderError
            .flatMap(user -> checkStock(request.items()))
            .flatMap(stock -> processPayment(request.payment()))
            .map(payment -> createOrder(request, payment));

        // Short-circuits on first error
        // All error types explicit in OrderError sealed interface
    }

    private Either<OrderError, Stock> checkStock(List<Item> items) {
        // Check stock logic
        if (/* out of stock */) {
            return Either.left(new OutOfStockError(unavailableItems));
        }
        return Either.right(stock);
    }

    private Either<OrderError, Payment> processPayment(PaymentRequest payment) {
        // Payment logic
        if (/* payment failed */) {
            return Either.left(new PaymentFailedError(reason));
        }
        return Either.right(payment);
    }
}

@RestController
public class OrderController {

    @PostMapping("/orders")
    public Either<OrderError, Order> createOrder(@RequestBody OrderRequest request) {
        return orderService.processOrder(request);
    }

    // No @ExceptionHandler methods needed!
    // Framework maps error types to HTTP status:
    // - UserNotFoundError → 404
    // - OutOfStockError → 400
    // - PaymentFailedError → 402
}
```

**Key Improvement:** All possible errors are visible in the `OrderError` sealed interface.

---

## Pattern 3: Validation Exceptions to Validated

### Before: Validation with Exceptions

```java
@PostMapping
public User createUser(@Valid @RequestBody UserRequest request, BindingResult bindingResult) {
    if (bindingResult.hasErrors()) {
        List<String> errors = bindingResult.getAllErrors()
            .stream()
            .map(ObjectError::getDefaultMessage)
            .toList();
        throw new ValidationException(errors);
    }

    // Additional custom validation
    if (!emailService.isValid(request.email())) {
        throw new ValidationException("Invalid email format");
    }

    if (userRepository.existsByEmail(request.email())) {
        throw new ValidationException("Email already exists");
    }

    return userService.create(request);
}

@ExceptionHandler(ValidationException.class)
public ResponseEntity<?> handleValidation(ValidationException ex) {
    return ResponseEntity.status(400)
        .body(new ErrorResponse(ex.getErrors()));
}
```

**Problem:** Only the first validation error is thrown. To see all errors, user must fix one at a time.

### After: Validated with Error Accumulation

```java
public record ValidationError(String field, String message) {
}

@Service
public class UserService {

    public Validated<List<ValidationError>, User> validateAndCreate(UserRequest request) {
        return Validated.validateAll(
            validateEmail(request.email()),
            validateFirstName(request.firstName()),
            validateLastName(request.lastName()),
            validateUniqueEmail(request.email())
        ).map(tuple -> createUser(
            tuple._1(),  // validated email
            tuple._2(),  // validated firstName
            tuple._3(),  // validated lastName
            tuple._4()   // uniqueness confirmed
        ));
    }

    private Validated<ValidationError, String> validateEmail(String email) {
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return Validated.invalid(
                new ValidationError("email", "Invalid email format"));
        }
        return Validated.valid(email);
    }

    private Validated<ValidationError, String> validateFirstName(String name) {
        if (name == null || name.trim().length() < 2) {
            return Validated.invalid(
                new ValidationError("firstName", "First name must be at least 2 characters"));
        }
        return Validated.valid(name);
    }

    private Validated<ValidationError, String> validateLastName(String name) {
        if (name == null || name.trim().length() < 2) {
            return Validated.invalid(
                new ValidationError("lastName", "Last name must be at least 2 characters"));
        }
        return Validated.valid(name);
    }

    private Validated<ValidationError, String> validateUniqueEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            return Validated.invalid(
                new ValidationError("email", "Email already exists"));
        }
        return Validated.valid(email);
    }
}

@RestController
@RequestMapping("/api/users")
public class UserController {

    @PostMapping
    public Validated<List<ValidationError>, User> createUser(@RequestBody UserRequest request) {
        return userService.validateAndCreate(request);
    }

    // No @ExceptionHandler needed!
    // Framework converts:
    // - Valid(user) → 200 OK with user JSON
    // - Invalid(errors) → 400 Bad Request with ALL validation errors
}
```

**Why it helps:**
- ✅ Returns **all** validation errors at once
- ✅ Better user experience (fix all issues in one go)
- ✅ Validation logic is composable and testable
- ✅ No special exception types needed

### Migration Steps

**Step 1:** Extract validation logic into individual `Validated` functions

```java
private Validated<ValidationError, String> validateEmail(String email) {
    // validation logic
}
```

**Step 2:** Compose validations with `Validated.validateAll()`

```java
public Validated<List<ValidationError>, User> validateAndCreate(UserRequest request) {
    return Validated.validateAll(
        validateEmail(request.email()),
        validateName(request.name())
        // ... more validations
    ).map(tuple -> createUser(...));
}
```

**Step 3:** Return `Validated` from controller

```java
@PostMapping
public Validated<List<ValidationError>, User> createUser(@RequestBody UserRequest request) {
    return userService.validateAndCreate(request);
}
```

---

## Pattern 4: Async Exceptions to EitherT

### Before: CompletableFuture with Exception Handling

```java
@Service
public class AsyncOrderService {

    public CompletableFuture<Order> processOrderAsync(OrderRequest request) {
        return userService.findByIdAsync(request.userId())
            .thenCompose(user -> {
                if (user == null) {
                    throw new CompletionException(new UserNotFoundException(request.userId()));
                }
                return inventoryService.checkStockAsync(request.items());
            })
            .thenCompose(stock -> {
                if (!stock.isAvailable()) {
                    throw new CompletionException(new OutOfStockException());
                }
                return paymentService.processPaymentAsync(request.payment());
            })
            .handle((payment, ex) -> {
                if (ex != null) {
                    // Complex error handling logic
                    Throwable cause = ex.getCause();
                    if (cause instanceof UserNotFoundException) {
                        throw new CompletionException(cause);
                    } else if (cause instanceof OutOfStockException) {
                        throw new CompletionException(cause);
                    }
                    throw new CompletionException(ex);
                }
                return createOrder(request, payment);
            });
    }
}

@RestController
public class OrderController {

    @GetMapping("/{id}")
    public CompletableFuture<Order> getOrder(@PathVariable String id) {
        return asyncOrderService.getOrderAsync(id)
            .exceptionally(ex -> {
                // More error handling...
                throw new CompletionException(ex);
            });
    }
}
```

**Potential Problems:**
- Wrapped exceptions in `CompletionException`
- Error handling scattered across `.handle()` and `.exceptionally()`
- Type safety lost

### After: EitherT for Async + Typed Errors

```java
@Service
public class AsyncOrderService {

    public EitherT<CompletableFutureKind.Witness, OrderError, Order>
    processOrderAsync(OrderRequest request) {

        return asyncUserService.findByIdAsync(request.userId())
            .flatMap(user -> asyncInventoryService.checkStockAsync(request.items()))
            .flatMap(stock -> {
                if (!stock.isAvailable()) {
                    return EitherT.leftT(
                        CompletableFutureKindHelper.FUTURE,
                        new OutOfStockError(stock.unavailableItems())
                    );
                }
                return EitherT.rightT(CompletableFutureKindHelper.FUTURE, stock);
            })
            .flatMap(stock -> asyncPaymentService.processPaymentAsync(request.payment()))
            .map(payment -> createOrder(request, payment));

        // Clean, composable, type-safe
        // Short-circuits on first error
    }
}

@RestController
public class OrderController {

    @GetMapping("/{id}/async")
    public EitherT<CompletableFutureKind.Witness, OrderError, Order>
    getOrder(@PathVariable String id) {

        return asyncOrderService.getOrderAsync(id);
        // Framework handles async → sync HTTP response conversion automatically
    }
}
```

**Improvements:**
- ✅ Type-safe error handling in async context
- ✅ Clean composition with `flatMap`
- ✅ Automatic short-circuiting on errors
- ✅ Framework handles async processing

### Migration Steps

**Step 1:** Convert async methods to return `EitherT`

```java
public EitherT<CompletableFutureKind.Witness, DomainError, User>
findByIdAsync(String id) {

    CompletableFuture<Either<DomainError, User>> future =
        CompletableFuture.supplyAsync(() -> {
            return repository.findById(id)
                .map(Either::<DomainError, User>right)
                .orElseGet(() -> Either.left(new UserNotFoundError(id)));
        });

    return EitherT.fromKind(CompletableFutureKindHelper.FUTURE.widen(future));
}
```

**Step 2:** Compose operations with `flatMap`

```java
public EitherT<CompletableFutureKind.Witness, OrderError, Order>
processOrderAsync(OrderRequest request) {

    return asyncUserService.findByIdAsync(request.userId())
        .flatMap(user -> asyncInventoryService.checkStockAsync(request.items()))
        .flatMap(stock -> asyncPaymentService.processPaymentAsync(request.payment()))
        .map(payment -> createOrder(request, payment));
}
```

**Step 3:** Return `EitherT` from controller

```java
@GetMapping("/{id}/async")
public EitherT<CompletableFutureKind.Witness, OrderError, Order>
getOrder(@PathVariable String id) {
    return asyncOrderService.getOrderAsync(id);
}
```

---

## Pattern 5: Chained Operations

### Before: Nested Try-Catch

```java
@GetMapping("/{userId}/orders/{orderId}/items/{itemId}")
public OrderItem getOrderItem(
        @PathVariable String userId,
        @PathVariable String orderId,
        @PathVariable String itemId) {

    try {
        User user = userService.findById(userId);

        try {
            Order order = orderService.findById(orderId);

            try {
                orderService.verifyOwnership(order, userId);

                try {
                    return orderService.findItem(order, itemId);
                } catch (ItemNotFoundException ex) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
                }
            } catch (UnauthorizedException ex) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your order");
            }
        } catch (OrderNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
    } catch (UserNotFoundException ex) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
}
```

### After: flatMap Composition

```java
@GetMapping("/{userId}/orders/{orderId}/items/{itemId}")
public Either<DomainError, OrderItem> getOrderItem(
        @PathVariable String userId,
        @PathVariable String orderId,
        @PathVariable String itemId) {

    return userService.findById(userId)
        .flatMap(user -> orderService.findById(orderId))
        .flatMap(order -> orderService.verifyOwnership(order, userId).map(_ -> order))
        .flatMap(order -> orderService.findItem(order, itemId));

    // Clean, linear, composable
    // Short-circuits on first error
}
```

**Major Improvement:** Nested try-catch pyramid eliminated, replaced with clean functional composition.

---

## Pattern 6: Maintaining Backwards Compatibility

During migration, you may need to support both old and new clients. Here are strategies:

### Strategy 1: Dual Endpoints

Expose both old and new versions:

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    // Old endpoint (deprecated)
    @GetMapping("/{id}")
    @Deprecated
    public User getUserLegacy(@PathVariable String id) {
        Either<DomainError, User> result = userService.findById(id);

        return result.fold(
            error -> { throw new UserNotFoundException(id); },  // Convert back to exception
            user -> user
        );
    }

    // New endpoint (functional)
    @GetMapping("/v2/{id}")
    public Either<DomainError, User> getUser(@PathVariable String id) {
        return userService.findById(id);
    }
}
```

### Strategy 2: Content Negotiation

Use different response format based on `Accept` header:

```java
@GetMapping("/{id}")
public ResponseEntity<?> getUser(@PathVariable String id,
                                  @RequestHeader("Accept") String accept) {

    Either<DomainError, User> result = userService.findById(id);

    if (accept.contains("application/vnd.myapp.v2+json")) {
        // New clients get Either JSON
        return ResponseEntity.ok(result);
    } else {
        // Old clients get traditional format
        return result.fold(
            error -> ResponseEntity.status(404).build(),
            user -> ResponseEntity.ok(user)
        );
    }
}
```

### Strategy 3: Convert Either to Exception Temporarily

If you must maintain existing exception-based behaviour:

```java
@Service
public class UserService {

    // New internal method
    public Either<DomainError, User> findById(String id) {
        return repository.findById(id)
            .map(Either::<DomainError, User>right)
            .orElseGet(() -> Either.left(new UserNotFoundError(id)));
    }

    // Old public method for legacy callers
    @Deprecated
    public User findById_LEGACY(String id) throws UserNotFoundException {
        return findById(id).fold(
            error -> {
                throw new UserNotFoundException(id);  // Convert back to exception
            },
            user -> user
        );
    }
}
```

---

## Potential Pitfalls and Remedies

### Pitfall 1: Forgetting to Handle Both Cases

```java
// ❌ BAD: Only handles Right case
@GetMapping("/{id}/email")
public String getUserEmail(@PathVariable String id) {
    return userService.findById(id)
        .map(User::email)
        .getRight();  // Throws NoSuchElementException if Left!
}

// ✅ GOOD: Return Either, let framework handle it
@GetMapping("/{id}/email")
public Either<DomainError, String> getUserEmail(@PathVariable String id) {
    return userService.findById(id)
        .map(User::email);
}
```

### Pitfall 2: Mixing Exceptions and Either

```java
// ❌ BAD: Throwing exception inside Either
public Either<DomainError, User> findById(String id) {
    if (id == null) {
        throw new IllegalArgumentException("ID cannot be null");  // Don't do this!
    }
    return repository.findById(id)
        .map(Either::<DomainError, User>right)
        .orElseGet(() -> Either.left(new UserNotFoundError(id)));
}

// ✅ GOOD: Return Left for all errors
public Either<DomainError, User> findById(String id) {
    if (id == null) {
        return Either.left(new ValidationError("id", "ID cannot be null"));
    }
    return repository.findById(id)
        .map(Either::<DomainError, User>right)
        .orElseGet(() -> Either.left(new UserNotFoundError(id)));
}
```

### Pitfall 3: Not Using Validated for Multiple Errors

```java
// ❌ BAD: Using Either for validation (only returns first error)
public Either<ValidationError, User> validateUser(UserRequest request) {
    return validateEmail(request.email())
        .flatMap(email -> validateName(request.name()))
        .flatMap(name -> validateAge(request.age()))
        .map(age -> createUser(...));
    // Stops at first error!
}

// ✅ GOOD: Use Validated to accumulate all errors
public Validated<List<ValidationError>, User> validateUser(UserRequest request) {
    return Validated.validateAll(
        validateEmail(request.email()),
        validateName(request.name()),
        validateAge(request.age())
    ).map(tuple -> createUser(...));
    // Returns ALL errors!
}
```

---

## Checklist

When migrating an endpoint:

- [ ] Define domain error types as sealed interface
- [ ] Convert service methods to return Either/Validated/EitherT
- [ ] Update controller methods to return functional types
- [ ] Remove corresponding `@ExceptionHandler` methods
- [ ] Update unit tests (no more exception mocking!)
- [ ] Update integration tests to verify HTTP responses
- [ ] Document the new error types in API docs
- [ ] Consider backwards compatibility strategy if needed
- [ ] Monitor error rates with Spring Boot Actuator (optional)

---

## Testing

### Before: Testing Exception-Throwing Code

```java
@Test
void shouldThrowWhenUserNotFound() {
    assertThrows(UserNotFoundException.class, () -> {
        userService.findById("999");
    });
}
```

### After: Testing Either

```java
@Test
void shouldReturnLeftWhenUserNotFound() {
    Either<DomainError, User> result = userService.findById("999");

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(UserNotFoundError.class);
}
```

**Much cleaner:** No need to set up exception expectations or catch blocks.

---

## Performance Considerations

Functional error handling is typically **as fast or faster** than exception-throwing:

**Exception Throwing:**
- Stack trace generation: ~1-10μs
- Exception propagation: variable overhead
- Expensive for expected errors

**Either/Validated:**
- Object allocation: ~10-50ns
- No stack traces
- Predictable performance

---

## Summing it all up

When moving to a functional error handling approach:

- ✅ **Start small** - New endpoints or high-value migrations first
- ✅ **Incremental approach** - No need to migrate everything at once
- ✅ **Backwards compatible** - Support legacy and functional endpoints simultaneously
- ✅ **Better type safety** - Errors explicit in signatures
- ✅ **Easier testing** - No exception mocking required
- ✅ **Cleaner code** - Functional composition replaces nested try-catch
- ✅ **Better UX** - Validated accumulates all errors

The migration is straightforward and the benefits are immediate. Start with one endpoint and experience the difference!

---

## Related Documentation

- [Spring Boot Integration](./spring_boot_integration.md) - Complete integration guide
- [Either Monad](../monads/either_monad.md) - Either usage patterns
- [Validated Monad](../monads/validated_monad.md) - Validation patterns
- [EitherT Transformer](../transformers/eithert_transformer.md) - Async composition
- [Configuration Guide](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-spring/CONFIGURATION.md) - Configuration options
