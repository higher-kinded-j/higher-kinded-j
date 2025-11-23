# Testing the Spring Boot Integration

This document provides comprehensive testing instructions for the higher-kinded-j Spring Boot integration.

## Prerequisites

1. Build the project:
   ```bash
   ./gradlew :hkj-spring:example:build
   ```

2. Start the application:
   ```bash
   ./gradlew :hkj-spring:example:bootRun
   ```

   The application will start on `http://localhost:8080`

## Testing Either-based Error Handling

The `Either<L, R>` type provides fail-fast error handling. When validation fails, it returns the **first** error encountered.

### 1. Get User by ID (Success Case)

```bash
curl -X GET http://localhost:8080/api/users/1
```

**Expected Response:** HTTP 200
```json
{
  "id": "1",
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Smith"
}
```

### 2. Get User by ID (Not Found Case)

```bash
curl -X GET http://localhost:8080/api/users/999
```

**Expected Response:** HTTP 404
```json
{
  "error": "User with id '999' not found"
}
```

### 3. Create User (Valid Data)

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

**Expected Response:** HTTP 200
```json
{
  "id": "4",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe"
}
```

### 4. Create User (Invalid Email - Fail Fast)

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "invalid",
    "firstName": "John",
    "lastName": "Doe"
  }'
```

**Expected Response:** HTTP 400
```json
{
  "error": "Validation error on field 'email': Invalid email format"
}
```

**Note:** Even though this request only has one error, if we had multiple errors (e.g., invalid email AND empty firstName), Either would only return the FIRST error.

### 5. Create User (Multiple Errors - Fail Fast)

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "invalid",
    "firstName": "",
    "lastName": ""
  }'
```

**Expected Response:** HTTP 400
```json
{
  "error": "Validation error on field 'email': Invalid email format"
}
```

**Important:** Notice that even though there are 3 validation errors (invalid email, empty firstName, empty lastName), Either returns only the FIRST error (email). This is "fail-fast" behavior.

## Testing Validated-based Error Accumulation

The `Validated<E, A>` type provides error accumulation. When validation fails, it returns **ALL** errors together.

### 1. Create User with Validated (All Valid)

```bash
curl -X POST http://localhost:8080/api/validation/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jane@example.com",
    "firstName": "Jane",
    "lastName": "Smith"
  }'
```

**Expected Response:** HTTP 200
```json
{
  "id": "5",
  "email": "jane@example.com",
  "firstName": "Jane",
  "lastName": "Smith"
}
```

### 2. Create User with Validated (One Error)

```bash
curl -X POST http://localhost:8080/api/validation/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "invalid",
    "firstName": "Jane",
    "lastName": "Smith"
  }'
```

**Expected Response:** HTTP 400
```json
{
  "valid": false,
  "errors": [
    {
      "field": "email",
      "message": "Invalid email format"
    }
  ]
}
```

### 3. Create User with Validated (Multiple Errors - Error Accumulation)

```bash
curl -X POST http://localhost:8080/api/validation/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "invalid",
    "firstName": "",
    "lastName": ""
  }'
```

**Expected Response:** HTTP 400
```json
{
  "valid": false,
  "errors": [
    {
      "field": "email",
      "message": "Invalid email format"
    },
    {
      "field": "firstName",
      "message": "First name cannot be empty"
    },
    {
      "field": "lastName",
      "message": "Last name cannot be empty"
    }
  ]
}
```

**Key Difference:** Notice that Validated returns **ALL 3 errors** at once! This is much better UX for forms where you want to show all validation errors to the user simultaneously.

## Comparing Either vs Validated

| Feature | Either | Validated |
|---------|--------|-----------|
| Error Handling | Fail-fast (first error only) | Accumulates all errors |
| HTTP Response (errors) | Single error object | Array of errors |
| Use Case | Quick validation, early exit | Form validation, comprehensive feedback |
| Implementation | Monad (flatMap) | Applicative (map2, map3, etc.) |

### Side-by-Side Example

**Request:**
```json
{
  "email": "invalid",
  "firstName": "",
  "lastName": ""
}
```

**Either Response (POST /api/users):**
```json
{
  "error": "Validation error on field 'email': Invalid email format"
}
```

**Validated Response (POST /api/validation/users):**
```json
{
  "valid": false,
  "errors": [
    {"field": "email", "message": "Invalid email format"},
    {"field": "firstName", "message": "First name cannot be empty"},
    {"field": "lastName", "message": "Last name cannot be empty"}
  ]
}
```

## Understanding the Implementation

### Either-based Validation (UserController)

```java
@PostMapping
public Either<DomainError, User> createUser(@RequestBody CreateUserRequest request) {
    return userService.create(
        request.email(),
        request.firstName(),
        request.lastName()
    );
}
```

The service validates fields sequentially and returns on the first error:
```java
public Either<DomainError, User> create(String email, String firstName, String lastName) {
    if (email == null || !email.contains("@")) {
        return Either.left(new ValidationError("email", "Invalid email format"));
        // Stops here if email is invalid - never checks firstName/lastName
    }
    if (firstName == null || firstName.trim().isEmpty()) {
        return Either.left(new ValidationError("firstName", "First name cannot be empty"));
    }
    // ...
}
```

### Validated-based Validation (ValidationController)

```java
@PostMapping("/users")
public Validated<List<ValidationError>, User> createUserWithValidation(
    @RequestBody CreateUserRequest request) {
    return userService.validateAndCreate(
        request.email(),
        request.firstName(),
        request.lastName()
    );
}
```

The service validates ALL fields and accumulates errors using Applicative:
```java
public Validated<List<ValidationError>, User> validateAndCreate(
    String email, String firstName, String lastName) {

    // Create an Applicative instance for error accumulation
    Applicative<ValidatedKind.Witness<List<ValidationError>>> applicative =
        ValidatedMonad.instance(Semigroups.list());

    // Validate each field independently
    var validatedEmail = validateEmail(email);
    var validatedFirstName = validateFirstName(firstName);
    var validatedLastName = validateLastName(lastName);

    // Combine all validations - accumulates ALL errors!
    var result = applicative.map3(
        ValidatedKindHelper.VALIDATED.widen(validatedEmail),
        ValidatedKindHelper.VALIDATED.widen(validatedFirstName),
        ValidatedKindHelper.VALIDATED.widen(validatedLastName),
        (e, f, l) -> new User(generateId(), e, f, l)
    );

    return ValidatedKindHelper.VALIDATED.narrow(result);
}
```

## How the Framework Handles Return Values

### EitherReturnValueHandler

```java
public void handleReturnValue(Object returnValue, ...) {
    if (returnValue instanceof Either<?, ?> either) {
        either.fold(
            error -> {
                // Determine HTTP status from error type
                int status = error.getClass().getSimpleName().contains("NotFound") ? 404 : 400;
                response.setStatus(status);
                // Serialize error as JSON
                return null;
            },
            value -> {
                response.setStatus(200);
                // Serialize value as JSON
                return null;
            }
        );
    }
}
```

### ValidatedReturnValueHandler

```java
public void handleReturnValue(Object returnValue, ...) {
    if (returnValue instanceof Validated<?, ?> validated) {
        validated.fold(
            errors -> {
                response.setStatus(400);
                // Wrap errors in a response object
                Map<String, Object> errorBody = Map.of(
                    "valid", false,
                    "errors", errors  // List of all errors
                );
                objectMapper.writeValue(response.getWriter(), errorBody);
                return null;
            },
            value -> {
                response.setStatus(200);
                // Serialize value as JSON
                objectMapper.writeValue(response.getWriter(), value);
                return null;
            }
        );
    }
}
```

## Advanced Testing

### Test Error Accumulation with Different Combinations

1. **Invalid email only:**
   ```bash
   curl -X POST http://localhost:8080/api/validation/users \
     -H "Content-Type: application/json" \
     -d '{"email":"invalid","firstName":"Jane","lastName":"Smith"}'
   ```
   Expected: 1 error (email)

2. **Empty names only:**
   ```bash
   curl -X POST http://localhost:8080/api/validation/users \
     -H "Content-Type: application/json" \
     -d '{"email":"jane@example.com","firstName":"","lastName":""}'
   ```
   Expected: 2 errors (firstName, lastName)

3. **Invalid email and empty firstName:**
   ```bash
   curl -X POST http://localhost:8080/api/validation/users \
     -H "Content-Type: application/json" \
     -d '{"email":"invalid","firstName":"","lastName":"Smith"}'
   ```
   Expected: 2 errors (email, firstName)

4. **All invalid:**
   ```bash
   curl -X POST http://localhost:8080/api/validation/users \
     -H "Content-Type: application/json" \
     -d '{"email":"invalid","firstName":"","lastName":""}'
   ```
   Expected: 3 errors (email, firstName, lastName)

## Testing Jackson Serialization (Nested Either/Validated)

The Spring Boot integration provides two ways of handling Either and Validated:

1. **Top-level return values**: Handled by return value handlers (unwrapped, clean API)
2. **Nested in DTOs**: Handled by Jackson serializers (wrapped with metadata)

### Understanding Wrapped vs Unwrapped Responses

**Top-level (Return Value Handler - Unwrapped):**
```java
@GetMapping("/users/{id}")
public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);
}

// Success Response: {"id": "1", "email": "alice@example.com"}  ← Unwrapped!
// Error Response: {"success": false, "error": "Not found"}     ← Unwrapped!
```

**Nested (Jackson Serializer - Wrapped):**
```java
@GetMapping("/batch")
public BatchResult getBatch() {
    return new BatchResult(
        "batch-123",
        List.of(Either.right(user1), Either.left("error"))
    );
}

// Response: {
//   "batchId": "batch-123",
//   "results": [
//     {"isRight": true, "right": {"id": "1", ...}},   ← Wrapped!
//     {"isRight": false, "left": "error"}             ← Wrapped!
//   ]
// }
```

### Example: Testing Nested Either/Validated

To demonstrate Jackson serialization, you can add a test endpoint to your controllers:

**Add to UserController.java:**
```java
@GetMapping("/batch")
public BatchResult getUserBatch() {
    return new BatchResult(
        "batch-" + System.currentTimeMillis(),
        List.of(
            Either.right(new User("1", "alice@example.com", "Alice", "Smith")),
            Either.left(new UserNotFoundError("999")),
            Either.right(new User("2", "bob@example.com", "Bob", "Jones"))
        )
    );
}

public record BatchResult(String batchId, List<Either<DomainError, User>> results) {}
```

**Test the endpoint:**
```bash
curl -X GET http://localhost:8080/api/users/batch
```

**Expected Response:** HTTP 200
```json
{
  "batchId": "batch-1234567890",
  "results": [
    {
      "isRight": true,
      "right": {
        "id": "1",
        "email": "alice@example.com",
        "firstName": "Alice",
        "lastName": "Smith"
      }
    },
    {
      "isRight": false,
      "left": {
        "userId": "999",
        "message": "User with id '999' not found"
      }
    },
    {
      "isRight": true,
      "right": {
        "id": "2",
        "email": "bob@example.com",
        "firstName": "Bob",
        "lastName": "Jones"
      }
    }
  ]
}
```

### Example: Testing Nested Validated

**Add to ValidationController.java:**
```java
@PostMapping("/batch")
public ValidationBatchResult validateBatch(@RequestBody List<CreateUserRequest> requests) {
    List<Validated<List<ValidationError>, User>> results = requests.stream()
        .map(req -> userService.validateAndCreate(req.email(), req.firstName(), req.lastName()))
        .toList();

    return new ValidationBatchResult("batch-" + System.currentTimeMillis(), results);
}

public record ValidationBatchResult(
    String batchId,
    List<Validated<List<ValidationError>, User>> results
) {}
```

**Test the endpoint:**
```bash
curl -X POST http://localhost:8080/api/validation/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"email": "valid@example.com", "firstName": "John", "lastName": "Doe"},
    {"email": "invalid", "firstName": "", "lastName": ""},
    {"email": "another@example.com", "firstName": "Jane", "lastName": "Smith"}
  ]'
```

**Expected Response:** HTTP 200
```json
{
  "batchId": "batch-1234567890",
  "results": [
    {
      "valid": true,
      "value": {
        "id": "4",
        "email": "valid@example.com",
        "firstName": "John",
        "lastName": "Doe"
      }
    },
    {
      "valid": false,
      "errors": [
        {"field": "email", "message": "Invalid email format"},
        {"field": "firstName", "message": "First name cannot be empty"},
        {"field": "lastName", "message": "Last name cannot be empty"}
      ]
    },
    {
      "valid": true,
      "value": {
        "id": "5",
        "email": "another@example.com",
        "firstName": "Jane",
        "lastName": "Smith"
      }
    }
  ]
}
```

### JSON Format Specifications

**Either JSON Structure:**
```json
// Right (success)
{
  "isRight": true,
  "right": <value>
}

// Left (error)
{
  "isRight": false,
  "left": <error>
}
```

**Validated JSON Structure:**
```json
// Valid (success)
{
  "valid": true,
  "value": <value>
}

// Invalid (errors)
{
  "valid": false,
  "errors": <errors>
}
```

### When Jackson Serializers Are Used

| Scenario | Handler | JSON Format | Example |
|----------|---------|-------------|---------|
| Top-level Either return | EitherReturnValueHandler | Unwrapped | `GET /api/users/1` |
| Top-level Validated return | ValidatedReturnValueHandler | Wrapped with valid/errors | `POST /api/validation/users` |
| Nested Either in DTO | Jackson EitherSerializer | Wrapped with isRight/left/right | `GET /api/users/batch` |
| Nested Validated in DTO | Jackson ValidatedSerializer | Wrapped with valid/value/errors | `POST /api/validation/batch` |
| Map<String, Either> | Jackson EitherSerializer | Wrapped | Any DTO field |
| List<Validated> | Jackson ValidatedSerializer | Wrapped | Any DTO field |

### Verifying Jackson Module Registration

You can verify the Jackson module is registered by checking the ObjectMapper:

**Add a test endpoint:**
```java
@GetMapping("/debug/jackson-modules")
public Map<String, Object> getJacksonModules(@Autowired ObjectMapper objectMapper) {
    return Map.of(
        "registeredModules", objectMapper.getRegisteredModuleIds(),
        "hkjModulePresent", objectMapper.getRegisteredModuleIds().contains("HkjJacksonModule")
    );
}
```

**Test:**
```bash
curl -X GET http://localhost:8080/api/debug/jackson-modules
```

**Expected Response:**
```json
{
  "registeredModules": [
    "com.fasterxml.jackson.module.paramnames.ParameterNamesModule",
    "com.fasterxml.jackson.datatype.jdk8.Jdk8Module",
    "com.fasterxml.jackson.datatype.jsr310.JavaTimeModule",
    "HkjJacksonModule"
  ],
  "hkjModulePresent": true
}
```

### Testing Manual ObjectMapper Usage

If you use ObjectMapper directly in your code, Jackson serializers will be applied:

```java
@Service
public class ReportService {
    @Autowired
    private ObjectMapper objectMapper;

    public String generateReport(Either<Error, Report> result) throws JsonProcessingException {
        // Jackson serializer is used here
        return objectMapper.writeValueAsString(result);
        // Output: {"isRight": true, "right": {...}} or {"isRight": false, "left": {...}}
    }
}
```

## Verification Checklist

### Return Value Handlers (Unwrapped Responses)
- [ ] Either endpoint returns single error on validation failure
- [ ] Validated endpoint returns all errors on validation failure
- [ ] Either endpoint returns HTTP 200 on success
- [ ] Validated endpoint returns HTTP 200 on success
- [ ] Either endpoint returns HTTP 400 for validation errors
- [ ] Validated endpoint returns HTTP 400 for validation errors
- [ ] Either endpoint returns HTTP 404 for not found errors
- [ ] Error response format is valid JSON
- [ ] Success response format is valid JSON
- [ ] Multiple simultaneous errors are accumulated in Validated response

### Jackson Serializers (Wrapped Responses)
- [ ] Nested Either.Right serializes with `"isRight": true` and `"right"` field
- [ ] Nested Either.Left serializes with `"isRight": false` and `"left"` field
- [ ] Nested Validated.Valid serializes with `"valid": true` and `"value"` field
- [ ] Nested Validated.Invalid serializes with `"valid": false` and `"errors"` field
- [ ] List of Either values serializes correctly
- [ ] List of Validated values serializes correctly
- [ ] Map with Either values serializes correctly
- [ ] HkjJacksonModule is registered with ObjectMapper
- [ ] Manual ObjectMapper.writeValueAsString() uses custom serializers

## Testing EitherT Async Support

The `EitherT<CompletableFuture.Witness, E, A>` type enables non-blocking async operations with functional error handling. All async operations run on the configured thread pool, freeing up request threads.

### Key Differences from Synchronous Either

| Feature | Either (Sync) | EitherT (Async) |
|---------|---------------|-----------------|
| Execution | Blocks request thread | Non-blocking, uses async executor |
| Return type | `Either<E, A>` | `EitherT<CompletableFuture.Witness, E, A>` |
| Composition | `flatMap` on sync values | `flatMap` on async operations |
| Response time | Immediate | Delayed (after async completion) |
| Thread usage | Request thread | Async thread pool |
| Spring integration | HandlerMethodReturnValueHandler | AsyncHandlerMethodReturnValueHandler |

### 1. Async User Retrieval (Success Case)

```bash
curl -X GET http://localhost:8080/api/async/users/1
```

**Expected Behavior:**
- Request returns immediately (non-blocking)
- Operation executes on async thread pool
- Response arrives after ~100ms delay (simulated I/O)

**Expected Response:** HTTP 200
```json
{
  "id": "1",
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Smith"
}
```

**Note:** The response structure is identical to sync Either, but the execution is async.

### 2. Async User Retrieval (Not Found Case)

```bash
curl -X GET http://localhost:8080/api/async/users/999
```

**Expected Response:** HTTP 404 (after async delay)
```json
{
  "success": false,
  "error": {
    "userId": "999"
  }
}
```

### 3. Async User Lookup by Email

```bash
# Success case
curl -X GET "http://localhost:8080/api/async/users/by-email?email=alice@example.com"

# Not found case
curl -X GET "http://localhost:8080/api/async/users/by-email?email=nonexistent@example.com"
```

**Expected Responses:**
- Success: HTTP 200 with user JSON (after ~150ms delay)
- Not found: HTTP 404 with error JSON (after ~150ms delay)

### 4. Composed Async Operations (Enriched User)

This endpoint demonstrates EitherT composition with `flatMap`:
1. Async find user by ID
2. Async load profile data
3. Combine into enriched result

```bash
curl -X GET http://localhost:8080/api/async/users/1/enriched
```

**Expected Response:** HTTP 200 (after ~200ms total - two async calls)
```json
{
  "user": {
    "id": "1",
    "email": "alice@example.com",
    "firstName": "Alice",
    "lastName": "Smith"
  },
  "profile": {
    "userId": "1",
    "tier": "Premium",
    "points": 100
  }
}
```

**How it works:**
```java
findByIdAsync(id)           // First async operation
    .flatMap(user ->        // Chains to second async operation
        loadProfileAsync(user)
            .map(profile -> new EnrichedUser(user, profile))
    );
// If findByIdAsync returns Left, loadProfileAsync is never called (short-circuit)
// Both operations run asynchronously on the thread pool
```

### 5. Async Update with Validation

```bash
# Success case
curl -X PUT "http://localhost:8080/api/async/users/1/email?newEmail=newemail@example.com"

# Validation error
curl -X PUT "http://localhost:8080/api/async/users/1/email?newEmail=invalid"

# User not found
curl -X PUT "http://localhost:8080/api/async/users/999/email?newEmail=test@example.com"
```

**Expected Responses:**
- Success: HTTP 200 with updated user (after ~200ms)
- Validation error: HTTP 404 with error JSON (after ~100-200ms)
- Not found: HTTP 404 with error JSON (after ~100ms)

### 6. Async Health Check (Synchronous Endpoint for Comparison)

```bash
curl -X GET http://localhost:8080/api/async/health
```

**Expected Response:** HTTP 200 (immediate, no delay)
```json
{
  "status": "ok",
  "message": "Async endpoints are ready"
}
```

**Note:** This endpoint is synchronous to demonstrate the performance difference with async endpoints.

## Performance Testing Async Endpoints

### Concurrent Request Testing

Test the non-blocking nature of async endpoints by sending multiple concurrent requests:

```bash
# Send 10 concurrent requests
for i in {1..10}; do
  curl -X GET http://localhost:8080/api/async/users/1 &
done
wait
```

**Expected Behavior:**
- All 10 requests execute concurrently on the async thread pool
- Each request takes ~100ms (simulated I/O delay)
- Total time should be ~100ms (not 1000ms), proving non-blocking execution
- Request threads are freed immediately

### Comparing Sync vs Async Performance

```bash
# Synchronous endpoint - blocks request thread
time curl -X GET http://localhost:8080/api/users/1

# Asynchronous endpoint - non-blocking
time curl -X GET http://localhost:8080/api/async/users/1
```

**Expected:** Both take similar time for single request, but async frees the request thread.

### Monitoring Thread Pool Usage

The async executor is configured in `AsyncConfig`:
- Core pool size: 10 threads
- Max pool size: 20 threads
- Queue capacity: 100 tasks
- Thread name prefix: "hkj-async-"

You can monitor thread usage with tools like JConsole, VisualVM, or application logs showing thread names.

## Error Handling in Async Operations

### 1. Async Error Propagation

```bash
curl -X GET http://localhost:8080/api/async/users/999
```

**Demonstrates:**
- Error occurs in async operation
- Error is wrapped in Left
- EitherTReturnValueHandler unwraps and maps to HTTP 404
- Proper error JSON response

### 2. Exception During Async Execution

If an exception occurs during async execution (not a Left value, but an actual exception):

**Response:** HTTP 500
```json
{
  "success": false,
  "error": {
    "type": "NullPointerException",
    "message": "Internal server error"
  }
}
```

### 3. Async Operation Short-Circuiting

When using `flatMap` to compose async operations:

```bash
curl -X GET http://localhost:8080/api/async/users/999/enriched
```

**Behavior:**
1. findByIdAsync(999) returns Left(UserNotFoundError)
2. loadProfileAsync is NEVER called (short-circuit)
3. Response: HTTP 404 after ~100ms (only first async operation)

**Demonstrates:** EitherT maintains fail-fast semantics even in async chains.

## Configuration

Async support can be configured via `application.yml`:

```yaml
hkj:
  web:
    async-either-t-enabled: true  # Enable/disable async support (default: true)
    default-error-status: 400      # Default HTTP status for errors

  async:
    executor-core-pool-size: 10
    executor-max-pool-size: 20
    executor-queue-capacity: 100
    executor-thread-name-prefix: "hkj-async-"
    default-timeout-ms: 30000
```

### Disabling Async Support

To disable async support:
```yaml
hkj:
  web:
    async-either-t-enabled: false
```

When disabled, controllers returning `EitherT` will cause Spring to use default handling (likely errors).

## Verification Checklist - Async Support

### Basic Async Operations
- [ ] Async user retrieval returns HTTP 200 on success (with delay)
- [ ] Async user retrieval returns HTTP 404 on not found (with delay)
- [ ] Async operations execute on thread pool (not request thread)
- [ ] Multiple concurrent async requests execute in parallel
- [ ] Request threads are freed during async execution

### Composed Async Operations
- [ ] flatMap chains multiple async operations correctly
- [ ] Enriched user endpoint combines multiple async results
- [ ] Error in first async operation short-circuits the chain
- [ ] Error in second async operation propagates correctly
- [ ] All operations in chain execute asynchronously

### Error Handling
- [ ] Left values map to correct HTTP status codes
- [ ] Exceptions during async execution return HTTP 500
- [ ] Error JSON format matches sync Either format
- [ ] Error propagation works through flatMap chains

### Performance
- [ ] Async endpoints don't block request threads
- [ ] Concurrent requests execute in parallel on thread pool
- [ ] Total time for N concurrent requests ~= single request time
- [ ] Thread pool settings are respected (core/max/queue)

### Configuration
- [ ] Async support can be disabled via config
- [ ] Thread pool settings can be customized
- [ ] Async executor is properly initialized
- [ ] Graceful shutdown waits for async tasks to complete
