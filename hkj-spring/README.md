# Higher-Kinded-J Spring Boot Integration

This module provides Spring Boot 3.5.7 integration for higher-kinded-j, enabling type-safe functional programming patterns in Spring applications.

## Quick Start

### 1. Add Dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.higher-kinded-j:hkj-spring-starter:VERSION")
}
```

### 2. Use Either in Controllers

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public Either<DomainError, User> getUser(@PathVariable String id) {
        return userService.findById(id);
        // Right(user) → HTTP 200 with JSON
        // Left(UserNotFoundError) → HTTP 404 with error JSON
    }
}
```

That's it! The starter auto-configures everything.

## Features

### ✅ Automatic Either → HTTP Response Conversion

Return `Either<Error, Data>` from controllers and the framework automatically:
- Converts `Right(data)` → HTTP 200 with JSON body
- Converts `Left(error)` → HTTP 4xx/5xx based on error type

Error → Status Code mapping:
- `NotFoundError` → 404
- `ValidationError` → 400
- `AuthorizationError` → 403
- `AuthenticationError` → 401
- Default → 400

### ✅ Zero Configuration

Auto-configuration activates when:
- higher-kinded-j is on the classpath
- Spring Web MVC is present
- Application is a servlet web app

### ✅ Optics Support

Use `@GenerateLenses` on domain models:

```java
@GenerateLenses
public record User(String id, String email, String name) {}

// Auto-generates UserLenses.id(), UserLenses.email(), etc.
```

## Module Structure

```
hkj-spring/
├── autoconfigure/     # Auto-configuration classes
│   ├── HkjAutoConfiguration
│   ├── HkjWebMvcAutoConfiguration
│   └── EitherReturnValueHandler
├── starter/           # Dependency aggregator
└── example/           # Working example application
    ├── UserController
    ├── UserService
    └── Domain models
```

## Example Application

See `hkj-spring/example/` for a complete working example.

### Running the Example

```bash
./gradlew :hkj-spring:example:bootRun
```

### Try These Endpoints

```bash
# Get all users (200 OK)
curl http://localhost:8080/api/users

# Get user by ID (200 OK)
curl http://localhost:8080/api/users/1

# Get non-existent user (404 Not Found)
curl http://localhost:8080/api/users/999

# Create user with valid data (200 OK)
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","firstName":"Test","lastName":"User"}'

# Create user with invalid email (400 Bad Request)
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"email":"invalid","firstName":"Test","lastName":"User"}'
```

## How It Works

### 1. EitherReturnValueHandler

The core component that intercepts `Either` return values:

```java
@GetMapping("/{id}")
public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);
}
```

The handler:
1. Checks if return type is `Either`
2. Calls `either.fold()` to handle both cases
3. For `Left`: Writes error JSON with appropriate HTTP status
4. For `Right`: Writes success JSON with HTTP 200

### 2. Auto-Configuration

`HkjWebMvcAutoConfiguration` registers the handler automatically:

```java
@AutoConfiguration
@ConditionalOnClass({DispatcherServlet.class, Kind.class})
@ConditionalOnWebApplication(type = SERVLET)
public class HkjWebMvcAutoConfiguration implements WebMvcConfigurer {

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        handlers.add(new EitherReturnValueHandler());
    }
}
```

### 3. Error Type Detection

HTTP status codes are determined by examining error class names:

```java
public record UserNotFoundError(String userId) implements DomainError {
    // Class name contains "NotFound" → HTTP 404
}

public record ValidationError(String field, String message) implements DomainError {
    // Class name contains "Validation" → HTTP 400
}
```

## Benefits

### Type-Safe Error Handling

```java
// Before: Exceptions hidden in implementation
public User getUser(String id) throws UserNotFoundException {
    // ...
}

// After: Errors explicit in type signature
public Either<DomainError, User> getUser(String id) {
    // Compiler enforces error handling
}
```

### Composable Operations

```java
@GetMapping("/{id}/email")
public Either<DomainError, String> getUserEmail(@PathVariable String id) {
    return userService.findById(id)
        .map(User::email);  // Functor composition
}

@GetMapping("/{id}/orders")
public Either<DomainError, List<Order>> getUserOrders(@PathVariable String id) {
    return userService.findById(id)
        .flatMap(orderService::getOrdersForUser);  // Monadic composition
}
```

### No Try-Catch Boilerplate

```java
// Before: Try-catch everywhere
@GetMapping("/{id}")
public ResponseEntity<?> getUser(@PathVariable String id) {
    try {
        User user = userService.findById(id);
        return ResponseEntity.ok(user);
    } catch (UserNotFoundException e) {
        return ResponseEntity.notFound().build();
    } catch (Exception e) {
        return ResponseEntity.status(500).build();
    }
}

// After: Clean functional style
@GetMapping("/{id}")
public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);
}
```

## Architecture

### Spring Boot 3.x Compatibility

Uses `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` for auto-configuration discovery (Spring Boot 3.x standard).

### Conditional Configuration

Only activates when required dependencies are present:
- `@ConditionalOnClass(Kind.class)` - higher-kinded-j present
- `@ConditionalOnClass(DispatcherServlet.class)` - Spring MVC present
- `@ConditionalOnWebApplication(type = SERVLET)` - Servlet app

### Non-Invasive

Doesn't modify existing Spring Boot behavior. Only adds support for functional return types.

## Advanced Usage

### Custom Error Mapping

Extend or customize error → status code mapping:

```java
@Configuration
public class CustomEitherConfiguration implements WebMvcConfigurer {

    @Override
    public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
        // Register custom handler with different mapping logic
        handlers.add(new CustomEitherReturnValueHandler());
    }
}
```

### Optics for Data Transformation

```java
@Service
public class UserService {

    private static final Lens<User, String> userToEmail = UserLenses.email();

    public Either<DomainError, User> updateEmail(String id, String newEmail) {
        return repository.findById(id)
            .map(user -> userToEmail.set(newEmail, user))
            .flatMap(repository::save);
    }
}
```

## Testing

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn200ForExistingUser() throws Exception {
        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void shouldReturn404ForMissingUser() throws Exception {
        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.message").exists());
    }
}
```

## Requirements

- Java 24+
- Spring Boot 3.5.7+
- higher-kinded-j core library

## License

MIT (same as higher-kinded-j)

## Next Steps

- [ ] Validated return value handler (accumulating validation)
- [ ] EitherT async support
- [ ] Jackson custom serializers
- [ ] Configuration properties
- [ ] Spring Security integration
- [ ] WebFlux support (reactive)
- [ ] Actuator metrics
- [ ] Comprehensive test suite

## Contributing

See main [higher-kinded-j CONTRIBUTING.md](../CONTRIBUTING.md)
