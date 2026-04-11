# Architecture Examples: Imperative -> FCIS

Before/after examples showing how to restructure imperative Java services into "functional core, imperative shell" architecture with HKJ.

---

## Example 1: Order Processing Service

### Before (Imperative)

```java
@Service
public class OrderService {

    @Autowired private UserRepository userRepo;
    @Autowired private InventoryService inventory;
    @Autowired private PaymentGateway payments;
    @Autowired private NotificationService notifications;

    public OrderResult processOrder(String userId, OrderRequest request) {
        // Side effects mixed with business logic
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        try {
            if (!inventory.checkAvailability(request.getItems())) {
                throw new InsufficientStockException();
            }

            double total = 0;
            for (var item : request.getItems()) {
                total += item.price() * item.quantity();
                if (user.isGoldMember()) total *= 0.9;  // discount logic buried here
            }

            PaymentResult payment = payments.charge(user, total);
            if (!payment.isSuccess()) {
                throw new PaymentFailedException(payment.reason());
            }

            notifications.sendConfirmation(user, payment);  // side effect in business logic
            return new OrderResult(payment.transactionId(), total);

        } catch (Exception e) {
            notifications.sendFailureAlert(user, e);  // another side effect in catch block
            throw e;
        }
    }
}
```

**Problems**: Side effects everywhere, business logic buried in exception handling, hard to test without mocking 4 dependencies, discount logic mixed with I/O.

### After (FCIS with HKJ)

**Functional Core** (pure, testable):

```java
// Pure domain logic -- no injected dependencies, no side effects
public class OrderRules {

    // Pure validation
    public static EitherPath<OrderError, ValidatedOrder> validateOrder(
            User user, OrderRequest request, InventoryStatus inventory) {

        return Path.<OrderError, OrderRequest>right(request)
            .via(req -> checkInventory(req, inventory))
            .map(req -> calculateTotal(req, user))
            .map(total -> new ValidatedOrder(user, request, total));
    }

    // Pure calculation
    static Money calculateTotal(OrderRequest request, User user) {
        Money subtotal = request.items().stream()
            .map(item -> item.price().multiply(item.quantity()))
            .reduce(Money.ZERO, Money::add);

        return user.isGoldMember() ? subtotal.multiply(0.9) : subtotal;
    }

    // Pure validation
    static EitherPath<OrderError, OrderRequest> checkInventory(
            OrderRequest request, InventoryStatus inventory) {
        var unavailable = request.items().stream()
            .filter(item -> !inventory.isAvailable(item.id(), item.quantity()))
            .toList();
        return unavailable.isEmpty()
            ? Path.right(request)
            : Path.left(new OrderError.InsufficientStock(unavailable));
    }
}
```

**Imperative Shell** (orchestrates effects):

```java
@Service
public class OrderService {

    @Autowired private UserRepository userRepo;
    @Autowired private InventoryService inventory;
    @Autowired private PaymentGateway payments;
    @Autowired private NotificationService notifications;

    public Either<OrderError, OrderResult> processOrder(String userId, OrderRequest request) {
        return Path.maybe(userRepo.findById(userId))
            .toEitherPath(new OrderError.NotFound(userId))
            .via(user -> {
                var invStatus = inventory.checkStatus(request.items());
                return OrderRules.validateOrder(user, request, invStatus);  // pure core
            })
            .via(validated -> Path.tryOf(() ->
                    payments.charge(validated.user(), validated.total()))
                .toEitherPath(OrderError.PaymentFailed::new))
            .peek(result -> notifications.sendConfirmation(result))  // side effect in shell
            .run();
    }
}
```

**Controller** (thin shell):

```java
@PostMapping("/orders")
public Either<OrderError, OrderResult> createOrder(@RequestBody OrderRequest req) {
    return orderService.processOrder(currentUserId(), req);
}
```

**Test the core** (no mocks needed):

```java
@Test
void goldMemberGetsDiscount() {
    var user = new User("alice", true);  // gold member
    var request = new OrderRequest(List.of(new Item("widget", Money.of(100), 2)));
    var inventory = InventoryStatus.allAvailable();

    var result = OrderRules.validateOrder(user, request, inventory).run();

    assertEquals(Right(new ValidatedOrder(user, request, Money.of(180))), result);
}
```

---

## Example 2: User Registration

### Before

```java
public User register(RegistrationForm form) {
    if (form.name() == null) throw new ValidationException("Name required");
    if (form.email() == null) throw new ValidationException("Email required");
    if (!form.email().contains("@")) throw new ValidationException("Invalid email");
    if (userRepo.existsByEmail(form.email())) throw new DuplicateException("Email taken");

    User user = new User(UUID.randomUUID().toString(), form.name(), form.email());
    userRepo.save(user);
    emailService.sendWelcome(user);
    return user;
}
```

### After (FCIS)

**Core** (pure validation):

```java
public static Validated<List<String>, RegistrationForm> validateForm(RegistrationForm form) {
    return Path.valid(form.name(), Semigroup.listSemigroup())
        .via(name -> name == null || name.isBlank()
            ? Path.invalid(List.of("Name required"), Semigroup.listSemigroup())
            : Path.valid(name, Semigroup.listSemigroup()))
        .zipWithAccum(
            validateEmail(form.email()),
            (name, email) -> new RegistrationForm(name, email))
        .run();
}
```

**Shell** (effects at boundary):

```java
public Either<RegistrationError, User> register(RegistrationForm form) {
    return Path.either(validateForm(form))
        .mapError(RegistrationError.ValidationFailed::new)
        .via(valid -> userRepo.existsByEmail(valid.email())
            ? Path.left(new RegistrationError.DuplicateEmail(valid.email()))
            : Path.right(valid))
        .map(valid -> new User(UUID.randomUUID().toString(), valid.name(), valid.email()))
        .peek(user -> { userRepo.save(user); emailService.sendWelcome(user); })
        .run();
}
```

---

## Key Structural Differences

| Aspect | Imperative | FCIS with HKJ |
|--------|-----------|---------------|
| Business logic location | Mixed with I/O | Isolated in pure functions |
| Error handling | Exceptions (invisible) | Return types (explicit) |
| Testability | Requires mocking | Pure functions, no mocks |
| Composability | Manual if/else nesting | Pipeline with `via`/`map` |
| Side effects | Scattered throughout | Contained in shell |
| Validation | Fail-fast exceptions | Accumulating or fail-fast (choice) |
