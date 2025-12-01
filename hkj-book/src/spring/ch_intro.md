# Integration Guides

> *"If thought corrupts language, language can also corrupt thought."*
>
> – George Orwell, *Politics and the English Language*

---

Exception-based error handling corrupts thought. When a method signature says `User getUser(String id)`, it lies by omission. The user might not exist. The database might be down. The ID might be malformed. None of this appears in the signature. The exceptions, when they come, arrive as surprises, handled in catch blocks scattered across the codebase, their semantics unclear, their taxonomy baroque.

Functional error handling clarifies thought. When a method returns `Either<DomainError, User>`, the signature tells the truth. Failure is possible. The error type is explicit. Callers must acknowledge this reality; the compiler ensures it. The code becomes honest.

This chapter bridges functional programming and enterprise Java. The `hkj-spring-boot-starter` allows Spring controllers to return `Either`, `Validated`, and `EitherT` directly. The framework handles the translation to HTTP responses: `Right` becomes 200 OK; `Left` becomes the appropriate error status. Validation errors accumulate properly. Async operations compose cleanly.

The integration is non-invasive. Existing exception-based endpoints continue to work. Migration can proceed incrementally. But as more of your codebase adopts functional error handling, a subtle shift occurs. Errors become data. Control flow becomes explicit. The language of your code begins to clarify your thought rather than corrupt it.

---

## The Transformation

```
    ┌─────────────────────────────────────────────────────────────┐
    │  EXCEPTION-BASED (Traditional)                              │
    │                                                             │
    │    User getUser(String id)    ← What can go wrong?         │
    │                                                             │
    │    @ExceptionHandler(UserNotFoundException.class)           │
    │    @ExceptionHandler(ValidationException.class)             │
    │    @ExceptionHandler(DatabaseException.class)               │
    │    ...scattered across the codebase                         │
    └─────────────────────────────────────────────────────────────┘

    ┌─────────────────────────────────────────────────────────────┐
    │  FUNCTIONAL (With hkj-spring)                               │
    │                                                             │
    │    Either<DomainError, User> getUser(String id)            │
    │           ↑                                                 │
    │    Errors visible in the type signature                     │
    │                                                             │
    │    → Right(user) automatically becomes HTTP 200             │
    │    → Left(NotFoundError) automatically becomes HTTP 404     │
    │    → Left(ValidationError) automatically becomes HTTP 400   │
    └─────────────────────────────────────────────────────────────┘
```

---

## What the Starter Provides

| Feature | Benefit |
|---------|---------|
| `Either` return types | Typed errors in controller signatures |
| `Validated` return types | Accumulate all validation errors |
| `EitherT` return types | Async operations with typed errors |
| Automatic status mapping | Error types → HTTP status codes |
| JSON serialisation | Configurable output formats |
| Actuator integration | Metrics for functional operations |
| Security integration | Functional authentication patterns |

---

## What You'll Learn

~~~admonish info title="In This Chapter"
- **Spring Boot Integration** – Using Either, Validated, and EitherT in controllers
- **Migration Guide** – Moving from exceptions to functional error handling
~~~

---

## Chapter Contents

