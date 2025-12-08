# EffectPath Annotation Assembly: User Perspective and Phasing Analysis

> **Status**: Design Review Document (v1.0)
> **Last Updated**: 2025-01-15
> **Purpose**: Clarify annotation assembly user experience and recommend phasing
> **Java Baseline**: Java 25 (RELEASE_25)

---

## Table of Contents

1. [User Perspective: What Will Users See?](#user-perspective)
2. [Annotation Types Comparison](#annotation-types)
3. [Complete User Journey Examples](#user-journey)
4. [Phasing Analysis and Recommendation](#phasing-analysis)
5. [Updated Implementation Timeline](#updated-timeline)

---

## User Perspective: What Will Users See?

### The Two Annotation Categories

| Annotation | Purpose | Target User | Phase Recommendation |
|------------|---------|-------------|---------------------|
| `@GeneratePathBridge` + `@PathVia` | Generate service bridges | **Every user** with service layers | **Phase 2** |
| `@PathSource` | Generate custom Path types | Advanced users with custom effects | **Phase 3** |

### Why This Distinction Matters

**`@GeneratePathBridge`** is the **bread and butter** annotation:
- Works with existing core types (Maybe, Either, Try, IO)
- Immediately useful in any project with service layers
- No knowledge of HKT required
- High ROI: one annotation, many generated methods

**`@PathSource`** is for **library authors and advanced users**:
- Creates new Path types for custom effects
- Requires understanding of HKT witness types
- Lower frequency of use
- Can wait until Phase 3 without impacting most users

---

## Annotation Types Comparison

### @GeneratePathBridge + @PathVia (Recommended Phase 2)

**What users write:**
```java
@GeneratePathBridge
public interface UserRepository {
    @PathVia Maybe<User> findById(Long id);
    @PathVia Maybe<User> findByEmail(String email);
    @PathVia Either<DbError, User> save(User user);
    @PathVia Try<List<User>> findAll();
}
```

**What gets generated:**
```java
// UserRepositoryPaths.java (generated)
@Generated("org.higherkindedj.hkt.path.processor.PathProcessor")
public final class UserRepositoryPaths {

    private UserRepositoryPaths() {}

    public static MaybePath<User> findById(UserRepository repo, Long id) {
        return MaybePath.of(repo.findById(id));
    }

    public static MaybePath<User> findByEmail(UserRepository repo, String email) {
        return MaybePath.of(repo.findByEmail(email));
    }

    public static EitherPath<DbError, User> save(UserRepository repo, User user) {
        return EitherPath.of(repo.save(user));
    }

    public static TryPath<List<User>> findAll(UserRepository repo) {
        return TryPath.of(repo.findAll());
    }
}
```

**How users consume it:**
```java
// Clean, fluent usage
User result = UserRepositoryPaths.findById(userRepo, 42L)
    .via(user -> UserRepositoryPaths.save(userRepo, user.withName("Updated")))
    .recover(err -> User.guest())
    .run()
    .getOrElse(User.anonymous());
```

### @PathSource (Recommended Phase 3)

**What advanced users write:**
```java
// Custom effect type with Path support
@PathSource(
    witness = ResultKind.Witness.class,
    errorType = AppError.class
)
public sealed interface Result<A> permits Success, Failure {
    <B> Result<B> map(Function<? super A, ? extends B> f);
    <B> Result<B> flatMap(Function<? super A, ? extends Result<B>> f);

    static <A> Result<A> success(A value) { return new Success<>(value); }
    static <A> Result<A> failure(AppError error) { return new Failure<>(error); }
}
```

**What gets generated:**
```java
// ResultPath.java (generated)
@Generated("org.higherkindedj.hkt.path.processor.PathProcessor")
public final class ResultPath<A> implements Recoverable<AppError, A> {

    private final Result<A> value;

    private ResultPath(Result<A> value) {
        this.value = Objects.requireNonNull(value);
    }

    // Factory methods
    public static <A> ResultPath<A> of(Result<A> result) {
        return new ResultPath<>(result);
    }

    public static <A> ResultPath<A> success(A value) {
        return new ResultPath<>(Result.success(value));
    }

    public static <A> ResultPath<A> failure(AppError error) {
        return new ResultPath<>(Result.failure(error));
    }

    // Composable
    @Override
    public <B> ResultPath<B> map(Function<? super A, ? extends B> f) {
        return new ResultPath<>(value.map(f));
    }

    // Chainable
    @Override
    public <B> ResultPath<B> via(Function<? super A, ? extends Chainable<B>> f) {
        return new ResultPath<>(value.flatMap(a -> {
            Chainable<B> result = f.apply(a);
            if (result instanceof ResultPath<?> rp) {
                return ((ResultPath<B>) rp).value;
            }
            throw new IllegalArgumentException("via must return ResultPath");
        }));
    }

    // Recoverable
    @Override
    public ResultPath<A> recover(Function<? super AppError, ? extends A> recovery) {
        // ... implementation
    }

    // Terminal
    public Result<A> run() {
        return value;
    }
}
```

---

## User Journey Examples

### Journey 1: Basic Service Layer (No Annotations)

**Without annotations (Phase 1):**
```java
public interface OrderService {
    Maybe<Order> findById(Long id);
    Either<ValidationError, Order> validate(Order order);
    Try<Order> process(Order order);
}

// Manual usage - verbose but works
public class OrderWorkflow {
    private final OrderService orderService;

    public Either<String, Order> processOrder(Long orderId) {
        // Using Path API directly
        return Path.maybe(orderService.findById(orderId))
            .map(order -> order.withStatus(PROCESSING))
            .toEitherPath("Order not found")
            .via(order -> Path.either(orderService.validate(order)))
            .mapError(ValidationError::message)
            .run();
    }
}
```

### Journey 2: Service Layer with Annotations (Phase 2)

**With `@GeneratePathBridge`:**
```java
@GeneratePathBridge
public interface OrderService {
    @PathVia Maybe<Order> findById(Long id);
    @PathVia Either<ValidationError, Order> validate(Order order);
    @PathVia Try<Order> process(Order order);
}

// Generated: OrderServicePaths.java
// Usage becomes cleaner:
public class OrderWorkflow {
    private final OrderService orderService;

    public Either<String, Order> processOrder(Long orderId) {
        return OrderServicePaths.findById(orderService, orderId)
            .map(order -> order.withStatus(PROCESSING))
            .toEitherPath("Order not found")
            .via(order -> OrderServicePaths.validate(orderService, order))
            .mapError(ValidationError::message)
            .run();
    }
}
```

### Journey 3: Multi-Service Composition (Phase 2)

**Real-world example with multiple services:**
```java
@GeneratePathBridge
public interface UserService {
    @PathVia Maybe<User> findById(Long id);
    @PathVia Either<AuthError, User> authenticate(Credentials creds);
}

@GeneratePathBridge
public interface OrderService {
    @PathVia Either<OrderError, Order> createOrder(User user, Cart cart);
    @PathVia Either<PaymentError, Receipt> processPayment(Order order);
}

@GeneratePathBridge
public interface NotificationService {
    @PathVia Try<Void> sendConfirmation(User user, Receipt receipt);
}

// Compose across services with full type safety:
public class CheckoutWorkflow {

    public Either<String, Receipt> checkout(Long userId, Cart cart) {
        return UserServicePaths.findById(userService, userId)
            .toEitherPath("User not found")
            .via(user -> OrderServicePaths.createOrder(orderService, user, cart))
            .mapError(OrderError::message)
            .via(order -> OrderServicePaths.processPayment(orderService, order))
            .mapError(PaymentError::message)
            .peek(receipt -> {
                // Fire-and-forget notification
                NotificationServicePaths.sendConfirmation(notificationService, user, receipt)
                    .recover(err -> { log.warn("Notification failed", err); return null; })
                    .run();
            })
            .run();
    }
}
```

### Journey 4: Custom Effect Type (Phase 3)

**Library author creating a domain-specific effect:**
```java
// Domain-specific result type with rich error handling
@PathSource(
    witness = ApiResultKind.Witness.class,
    errorType = ApiError.class,
    pathClassName = "ApiPath"  // Custom name
)
public sealed interface ApiResult<A> permits ApiSuccess, ApiFailure {

    <B> ApiResult<B> map(Function<? super A, ? extends B> f);
    <B> ApiResult<B> flatMap(Function<? super A, ? extends ApiResult<B>> f);

    // Rich factory methods
    static <A> ApiResult<A> success(A value) {
        return new ApiSuccess<>(value);
    }

    static <A> ApiResult<A> notFound(String resource) {
        return new ApiFailure<>(ApiError.notFound(resource));
    }

    static <A> ApiResult<A> unauthorized() {
        return new ApiFailure<>(ApiError.unauthorized());
    }

    static <A> ApiResult<A> serverError(Throwable cause) {
        return new ApiFailure<>(ApiError.serverError(cause));
    }
}

// Now usable in services:
@GeneratePathBridge
public interface ProductApi {
    @PathVia ApiResult<Product> getProduct(String sku);
    @PathVia ApiResult<List<Product>> search(String query);
}

// And in code:
ApiPath<Product> product = ProductApiPaths.getProduct(api, "SKU-123")
    .via(p -> ProductApiPaths.search(api, p.category()))
    .map(List::getFirst)
    .recover(err -> Product.placeholder());
```

---

## Phasing Analysis and Recommendation

### Current Phase 1 Plan (Path Types Only)

```
Phase 1: MaybePath, EitherPath, TryPath, IOPath
         ↓
         Users must manually wrap:
         Path.maybe(service.findById(id))
         Path.either(service.validate(order))
```

**Problem:** Every service method call requires explicit wrapping.

### Recommended Change: Move @GeneratePathBridge to Phase 2

| Phase | Components | Rationale |
|-------|------------|-----------|
| **Phase 1** | Path types only | Foundation, learn patterns |
| **Phase 2** | `@GeneratePathBridge` + `@PathVia` | Immediate productivity boost for ALL users |
| **Phase 3** | `@PathSource` + `@PathConfig` | Extension point for advanced users |

### Why Not Phase 1?

1. **Complexity Budget**: Phase 1 already has significant work
2. **Testing Foundation**: Path types need solid tests before processor depends on them
3. **API Stability**: Bridge generation depends on stable Path APIs
4. **Feedback Loop**: Users can provide feedback on Path API before we "lock in" generated code

### Why Not Phase 3?

1. **Immediate Value**: `@GeneratePathBridge` works with core types from Phase 1
2. **User Experience**: Manual wrapping is tedious; annotations solve this immediately
3. **Documentation**: Examples are cleaner with generated bridges
4. **Adoption**: Lower barrier = faster adoption

### Decision Matrix

| Factor | Phase 1 | Phase 2 | Phase 3 |
|--------|---------|---------|---------|
| Core types stable? | No | Yes | Yes |
| Path API finalized? | No | Mostly | Yes |
| Users want it? | Future | Now | Later |
| Implementation risk | High | Medium | Low |
| **Recommendation** | ❌ | ✅ | ❌ |

---

## Updated Implementation Timeline

### Phase 1: Core Path API (Unchanged)

**Focus**: Hand-written path types with 100% test coverage

| Task | Description |
|------|-------------|
| Capability interfaces | Composable, Combinable, Chainable, Recoverable, Effectful |
| Core path types | MaybePath, EitherPath, TryPath, IOPath |
| Path factory | `Path.maybe()`, `Path.either()`, etc. |
| Testing | 100% line + branch coverage, law verification |
| Documentation | Book chapter outline, API Javadoc |
| Examples | Basic usage in hkj-examples |

**Deliverable**: Usable Path API without annotations

### Phase 2: Annotation Assembly for Services (NEW)

**Focus**: `@GeneratePathBridge` for immediate productivity

| Task | Description |
|------|-------------|
| Annotation definitions | `@GeneratePathBridge`, `@PathVia` |
| PathBridgeGenerator | Generate `*Paths` classes |
| PathValidations | Compile-time validation |
| Processor tests | Compile-testing with google-compile-testing |
| Documentation | Annotation usage in book chapter |
| Examples | Service layer examples with annotations |

**Deliverable**: Annotate services, get generated bridges

### Phase 3: Custom Path Generation

**Focus**: `@PathSource` for library authors

| Task | Description |
|------|-------------|
| @PathSource annotation | Custom Path type generation |
| @PathConfig annotation | Global configuration |
| PathSourceGenerator | Generate custom Path types |
| Advanced validation | Witness type checking, method signature validation |
| Documentation | Extension guide for library authors |
| Examples | Custom effect type examples |

**Deliverable**: Full extensibility for custom effects

---

## Test Coverage Requirements Update

### Phase 1 Coverage

| Component | Line Coverage | Branch Coverage |
|-----------|---------------|-----------------|
| Capability interfaces | 100% | 100% |
| MaybePath | 100% | 100% |
| EitherPath | 100% | 100% |
| TryPath | 100% | 100% |
| IOPath | 100% | 100% |
| Path factory | 100% | 100% |

### Phase 2 Coverage (Annotation Processor)

| Component | Line Coverage | Branch Coverage |
|-----------|---------------|-----------------|
| PathProcessor | 100% | 100% |
| PathBridgeGenerator | 100% | 100% |
| PathValidations | 100% | 100% |
| Generated code tests | N/A | N/A (verified by compile-testing) |

---

## Book Chapter Placement

### Recommended: New "Effects" Section

```markdown
# SUMMARY.md structure

# Type Classes
- [Introduction](functional/ch_intro.md)
  - [Functor](functional/functor.md)
  - [Applicative](functional/applicative.md)
  - [Monad](functional/monad.md)
  - ...

# Monads in Practice
- [Introduction](monads/ch_intro.md)
  - [Maybe](monads/maybe_monad.md)
  - [Either](monads/either_monad.md)
  - [Try](monads/try_monad.md)
  - [IO](monads/io_monad.md)
  - ...

# Effect Path API  ← NEW SECTION
- [Introduction](effects/ch_intro.md)
  - [Effect Path Overview](effects/effect_path_overview.md)
  - [Capability Interfaces](effects/capabilities.md)
  - [Path Types](effects/path_types.md)
  - [Service Bridges](effects/service_bridges.md)  ← Phase 2
  - [Custom Effects](effects/custom_effects.md)    ← Phase 3
  - [Patterns and Recipes](effects/patterns.md)

# Optics I: Fundamentals
- [Introduction](optics/ch1_intro.md)
  ...
```

### Rationale for Placement

1. **After "Monads in Practice"**: Users understand Maybe, Either, Try, IO
2. **Before "Optics"**: Effect Path is simpler; Optics is more advanced
3. **Parallel to Focus DSL**: Both are "fluent APIs over complex machinery"

---

## Summary of Recommendations

| Item | Recommendation |
|------|----------------|
| **Phase 2 includes** | `@GeneratePathBridge` + `@PathVia` |
| **Phase 3 includes** | `@PathSource` + `@PathConfig` |
| **Branch coverage** | 100% for all components |
| **Book placement** | New "Effect Path API" section after Monads, before Optics |
| **Chapter review items** | Intro, graphics, diagrams, quotes - to be reviewed before commit |

---

## ✅ Confirmed Decisions

### Annotation Phasing: CONFIRMED

| Phase | Components | Status |
|-------|------------|--------|
| **Phase 1** | Core Path types (MaybePath, EitherPath, TryPath, IOPath) | Ready for implementation |
| **Phase 2** | `@GeneratePathBridge` + `@PathVia` for service bridges | **CONFIRMED** |
| **Phase 3** | `@PathSource` + `@PathConfig` for custom effect types | **CONFIRMED** |

**Rationale Summary:**

1. **Phase 2 for `@GeneratePathBridge`** (not Phase 1 or Phase 3):
    - Phase 1 needs stable Path APIs before generating code that depends on them
    - Users can provide feedback on Path API before locking in generated patterns
    - Immediate productivity boost once Phase 1 is complete
    - Works directly with core types—no custom effect types needed
    - Eliminates tedious manual wrapping: `Path.maybe(service.findById(id))`

2. **Phase 3 for `@PathSource`** (not earlier):
    - Advanced feature for library authors, not typical users
    - Requires understanding of HKT witness types
    - Lower frequency of use; most users work with core types
    - Phase 2 feedback will inform @PathSource design

---