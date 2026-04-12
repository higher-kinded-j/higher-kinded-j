# Spring Boot Example Walkthrough

Condensed walkthrough of the `hkj-spring/example` module.

---

## Project Structure

```
hkj-spring/example/
  src/main/java/.../spring/example/
    controller/
      UserController.java       # REST endpoints returning Either/Validated
    service/
      UserService.java          # Business logic with Effect Paths
    model/
      User.java                 # Domain record
      DomainError.java          # Sealed error hierarchy
    config/
      ErrorStatusMapping.java   # Domain error -> HTTP status mapping
```

---

## Domain Model

```java
public record User(String id, String name, String email) {}

public sealed interface DomainError {
    record UserNotFound(String id) implements DomainError {}
    record ValidationFailed(List<String> errors) implements DomainError {}
    record DuplicateEmail(String email) implements DomainError {}
}
```

---

## Service Layer (Functional Core)

```java
@Service
public class UserService {

    private final UserRepository repository;

    // Returns Either: explicit about possible errors
    public Either<DomainError, User> findById(String id) {
        return Path.maybe(repository.findById(id))
            .toEitherPath(new DomainError.UserNotFound(id))
            .run();
    }

    // Returns Validated: accumulates ALL errors
    public Validated<List<String>, User> validateAndCreate(String name, String email) {
        return Path.valid(name, Semigroup.listSemigroup())
            .via(n -> validateName(n))
            .zipWithAccum(
                validateEmail(email),
                (validName, validEmail) -> new User(UUID.randomUUID().toString(), validName, validEmail))
            .run();
    }

    // Pure validation functions
    private ValidationPath<List<String>, String> validateName(String name) {
        if (name == null || name.isBlank()) return Path.invalid(List.of("Name is required"), Semigroup.listSemigroup());
        if (name.length() < 2) return Path.invalid(List.of("Name too short"), Semigroup.listSemigroup());
        return Path.valid(name, Semigroup.listSemigroup());
    }

    private ValidationPath<List<String>, String> validateEmail(String email) {
        if (email == null || email.isBlank()) return Path.invalid(List.of("Email is required"), Semigroup.listSemigroup());
        if (!email.contains("@")) return Path.invalid(List.of("Invalid email format"), Semigroup.listSemigroup());
        return Path.valid(email, Semigroup.listSemigroup());
    }
}
```

---

## Controller (Imperative Shell)

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired private UserService userService;

    // Either -> automatic HTTP response
    @GetMapping("/{id}")
    public Either<DomainError, User> getUser(@PathVariable String id) {
        return userService.findById(id);
    }

    // Validated -> automatic error accumulation response
    @PostMapping
    public Validated<List<String>, User> createUser(@RequestBody CreateUserRequest req) {
        return userService.validateAndCreate(req.name(), req.email());
    }
}

record CreateUserRequest(String name, String email) {}
```

---

## Error Status Mapping

The starter uses the `ErrorStatusCodeMapper` utility, which applies class-name heuristics (no user interface to implement). Design your error class names to trigger the right status codes:

| Error class name contains... | HTTP status |
|------------------------------|-------------|
| `NotFound` | 404 |
| `Validation` or `Invalid` | 400 |
| `Authorization` or `Forbidden` | 403 |
| `Authentication` or `Unauthorized` | 401 |
| (default) | configured default (500) |

In the example above the `DomainError` record names match by design:

- `UserNotFound` -> 404 (matches `NotFound`)
- `ValidationFailed` -> 400 (matches `Validation`)
- `DuplicateEmail` -> 500 (no heuristic match, uses default)

Configure the default status in `application.yml`:

```yaml
hkj:
  web:
    either:
      default-error-status: 500
```

---

## Example Requests and Responses

### Successful Lookup

```
GET /api/users/123
-> 200 OK
{
  "id": "123",
  "name": "Alice",
  "email": "alice@example.com"
}
```

### Not Found

```
GET /api/users/999
-> 404 Not Found
{
  "type": "UserNotFound",
  "id": "999"
}
```

### Validation Errors (Accumulated)

```
POST /api/users
{ "name": "", "email": "bad" }
-> 400 Bad Request
["Name is required", "Invalid email format"]
```

---

## Testing

> **`@WebMvcTest` excludes third-party auto-configurations.** The
> hkj-spring-boot-starter's auto-configs (`HkjJacksonAutoConfiguration`,
> `HkjWebMvcAutoConfiguration`) are loaded via
> `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
> which `@WebMvcTest` intentionally trims. Without `@ImportAutoConfiguration`,
> MockMvc will see a raw `Either` POJO with no status-code mapping and the
> assertions below will fail. Importing the three auto-configs restores the
> production rendering contract inside the slice.

```java
@WebMvcTest(UserController.class)
@ImportAutoConfiguration({
    HkjAutoConfiguration.class,
    HkjJacksonAutoConfiguration.class,
    HkjWebMvcAutoConfiguration.class
})
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;

    @Test
    void getUser_found() throws Exception {
        when(userService.findById("1"))
            .thenReturn(Either.right(new User("1", "Alice", "alice@example.com")));

        mockMvc.perform(get("/api/users/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void getUser_notFound() throws Exception {
        when(userService.findById("99"))
            .thenReturn(Either.left(new DomainError.UserNotFound("99")));

        mockMvc.perform(get("/api/users/99"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createUser_validationErrors() throws Exception {
        when(userService.validateAndCreate("", "bad"))
            .thenReturn(Validated.invalid(List.of("Name is required", "Invalid email format")));

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "", "email": "bad"}
                    """))
            .andExpect(status().isBadRequest());
    }
}
```

> If you prefer full-fidelity over slice isolation, swap the annotations for
> `@SpringBootTest(classes = HkjSpringExampleApplication.class) +
> @AutoConfigureMockMvc` — see `UserControllerIntegrationTest` for the full
> pattern.

---

## Key Takeaways

1. **Service layer returns functional types** (Either, Validated) — explicit error contracts
2. **Controller is a thin shell** — just delegates to service and returns the result
3. **Auto-configuration handles conversion** — no manual ResponseEntity construction
4. **Error status mapping is centralised** — one switch expression, not scattered `@ExceptionHandler` methods
5. **Testing is straightforward** — mock the service, assert on status codes and JSON
