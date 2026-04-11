# Domain Modelling with Java 25 and HKJ

Patterns for modelling domain concepts using records, sealed interfaces, and HKJ types.

---

## Error Hierarchies

### Flat Error Hierarchy

```java
public sealed interface AppError {
    record NotFound(String resource, String id) implements AppError {}
    record Unauthorized(String reason) implements AppError {}
    record ValidationFailed(List<String> errors) implements AppError {}
    record Timeout(String operation, Duration elapsed) implements AppError {}
    record Conflict(String resource, String id) implements AppError {}
}
```

### Nested Error Hierarchy (Multi-Module)

```java
// Top-level application errors
public sealed interface AppError permits OrderError, UserError, PaymentError {}

// Module-specific errors
public sealed interface OrderError extends AppError {
    record InvalidItems(List<String> reasons) implements OrderError {}
    record InsufficientStock(String itemId) implements OrderError {}
}

public sealed interface UserError extends AppError {
    record NotFound(String userId) implements UserError {}
    record Suspended(String userId, String reason) implements UserError {}
}

public sealed interface PaymentError extends AppError {
    record Declined(String reason) implements PaymentError {}
    record GatewayTimeout(String gateway) implements PaymentError {}
}
```

### Pattern Matching at the Boundary

```java
// Exhaustive handling -- compiler verifies all cases covered
public int toHttpStatus(AppError error) {
    return switch (error) {
        case OrderError.InvalidItems _       -> 400;
        case OrderError.InsufficientStock _  -> 409;
        case UserError.NotFound _            -> 404;
        case UserError.Suspended _           -> 403;
        case PaymentError.Declined _         -> 402;
        case PaymentError.GatewayTimeout _   -> 504;
    };
}
```

---

## Result Types

### Simple Result

```java
// Just use Either<Error, Success>
Either<OrderError, Order> result = orderService.processOrder(request);
```

### Rich Result with Metadata

```java
public record ProcessingResult<T>(
    T value,
    List<String> warnings,
    Duration processingTime
) {}

// Return from service
EitherPath<AppError, ProcessingResult<Order>> processOrder(OrderRequest req) {
    var start = Instant.now();
    return validateOrder(req)
        .via(this::executeOrder)
        .map(order -> new ProcessingResult<>(order, warnings, Duration.between(start, Instant.now())));
}
```

---

## State Machines

### Order Lifecycle

```java
@GeneratePrisms
public sealed interface OrderStatus {
    record Draft(Instant created) implements OrderStatus {}
    record Confirmed(Instant confirmedAt, PaymentRef payment) implements OrderStatus {}
    record Shipped(TrackingNumber tracking, Instant shippedAt) implements OrderStatus {}
    record Delivered(Instant deliveredAt, String signedBy) implements OrderStatus {}
    record Cancelled(String reason, Instant cancelledAt) implements OrderStatus {}
    record Returned(String reason, Instant returnedAt) implements OrderStatus {}
}
```

### Transitions as Pure Functions

```java
public class OrderTransitions {

    public static EitherPath<OrderError, OrderStatus> confirm(
            OrderStatus current, PaymentRef payment) {
        return switch (current) {
            case Draft _ -> Path.right(new Confirmed(Instant.now(), payment));
            case Confirmed _ -> Path.left(new OrderError.AlreadyConfirmed());
            default -> Path.left(new OrderError.InvalidTransition(current, "confirm"));
        };
    }

    public static EitherPath<OrderError, OrderStatus> ship(
            OrderStatus current, TrackingNumber tracking) {
        return switch (current) {
            case Confirmed _ -> Path.right(new Shipped(tracking, Instant.now()));
            default -> Path.left(new OrderError.InvalidTransition(current, "ship"));
        };
    }
}
```

### Navigate Status with Optics

```java
@GenerateLenses
@GenerateFocus
public record Order(OrderId id, Customer customer, OrderStatus status) {}

// Use prism to access specific state
Prism<OrderStatus, Confirmed> confirmedPrism = OrderStatusPrisms.confirmed();

// Check if confirmed
boolean isConfirmed = confirmedPrism.preview(order.status()).isPresent();

// Extract tracking from shipped orders
Optional<TrackingNumber> tracking = OrderStatusPrisms.shipped()
    .preview(order.status())
    .map(Shipped::tracking);
```

---

## Value Objects

### Type-Safe IDs

```java
public record OrderId(String value) {
    public OrderId {
        Objects.requireNonNull(value, "OrderId cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("OrderId cannot be blank");
    }
}

public record CustomerId(String value) {
    // Prevents accidentally passing CustomerId where OrderId expected
}
```

### Money Type

```java
@GenerateLenses
public record Money(BigDecimal amount, Currency currency) {
    public Money add(Money other) {
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch");
        return new Money(amount.add(other.amount), currency);
    }
    public Money multiply(double factor) {
        return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
    }
    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance("USD"));
    }
    public static final Money ZERO = Money.of(0);
}
```

---

## Composition Patterns

### Aggregate with Optics

```java
@GenerateLenses
@GenerateFocus
public record Order(
    OrderId id,
    Customer customer,
    List<LineItem> items,
    OrderStatus status,
    Optional<ShippingAddress> shipping,
    Optional<Money> discount
) {
    public Money subtotal() {
        return items.stream()
            .map(LineItem::total)
            .reduce(Money.ZERO, Money::add);
    }
}

// Deep navigation with Focus DSL
TraversalPath<Order, Money> allItemPrices =
    OrderFocus.items().price();

// Apply discount to all items
Order discounted = allItemPrices
    .modifyAll(price -> price.multiply(0.9), order);
```

### Domain Events

```java
public sealed interface OrderEvent {
    record Created(OrderId id, Customer customer, Instant at) implements OrderEvent {}
    record Confirmed(OrderId id, PaymentRef payment, Instant at) implements OrderEvent {}
    record Shipped(OrderId id, TrackingNumber tracking, Instant at) implements OrderEvent {}
    record Cancelled(OrderId id, String reason, Instant at) implements OrderEvent {}
}

// Events are just data -- accumulate with WriterPath
WriterPath<List<OrderEvent>, Order> processWithEvents(Order order) {
    return Path.writer(List.of(), Semigroup.listSemigroup())
        .via(_ -> confirmOrder(order))
        .peek(confirmed -> Path.tell(
            List.of(new OrderEvent.Confirmed(order.id(), confirmed.payment(), Instant.now())),
            Semigroup.listSemigroup()));
}
```

---

## Quick Reference: When to Use What

| Concept | Java 25 Feature | HKJ Type |
|---------|----------------|----------|
| Domain data | `record` | `@GenerateLenses` + `@GenerateFocus` |
| Error types | `sealed interface` + records | `EitherPath<Error, A>` |
| Validation | `sealed interface` for errors | `ValidationPath<List<E>, A>` |
| State machines | `sealed interface` variants | `@GeneratePrisms` + pattern matching |
| Workflows | Records for intermediate state | `ForPath` comprehensions |
| Value objects | `record` with validation | Type-safe wrappers |
| Domain events | `sealed interface` + records | `WriterPath<List<Event>, A>` |
