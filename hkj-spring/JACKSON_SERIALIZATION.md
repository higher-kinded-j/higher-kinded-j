# Jackson Serialization for Higher-Kinded-J Types

This document explains how higher-kinded-j types (Either, Validated) are serialized to JSON using Jackson.

## Overview

The `hkj-spring-autoconfigure` module provides custom Jackson serializers and deserializers for:
- `Either<L, R>` - Sum type for error handling
- `Validated<E, A>` - Validation with error accumulation
- `EitherOrBoth<W, A>` - Inclusive-or (success that may carry warnings)
- `NonEmptyList<A>` - Non-empty list (serialised as a plain JSON array)

These serializers are **automatically registered** when using Spring Boot auto-configuration. The shapes below are fixed — there are no format-toggle properties. `Maybe` and `Try` have **no** Jackson support: return them at the top level (where the return-value handlers apply) rather than nesting them in DTOs.

## JSON Format

### Either Serialization

**Either.Right (success):**
```json
{
  "isRight": true,
  "right": <value>
}
```

**Either.Left (error):**
```json
{
  "isRight": false,
  "left": <error>
}
```

**Example:**
```java
Either<String, User> success = Either.right(new User("1", "alice@example.com"));
// JSON: {"isRight": true, "right": {"id": "1", "email": "alice@example.com"}}

Either<String, User> error = Either.left("User not found");
// JSON: {"isRight": false, "left": "User not found"}
```

### Validated Serialization

**Validated.Valid (success):**
```json
{
  "valid": true,
  "value": <value>
}
```

**Validated.Invalid (errors):**
```json
{
  "valid": false,
  "errors": <errors>
}
```

**Example:**
```java
Validated<List<String>, User> success = Validated.valid(new User("1", "alice@example.com"));
// JSON: {"valid": true, "value": {"id": "1", "email": "alice@example.com"}}

Validated<List<String>, User> errors = Validated.invalid(List.of("Invalid email", "Name required"));
// JSON: {"valid": false, "errors": ["Invalid email", "Name required"]}
```

### EitherOrBoth Serialization

`EitherOrBoth` has three cases, so it uses a `kind` discriminator:

```json
{"kind": "left",  "left":  <warnings>}
{"kind": "right", "right": <value>}
{"kind": "both",  "left":  <warnings>, "right": <value>}
```

This applies to **nested** `EitherOrBoth` values. A top-level `EitherOrBoth` / `EitherOrBothPath` controller return value is shaped by `EitherOrBothPathReturnValueHandler` instead: the success body is the bare value, `Both` warnings travel JSON-encoded in the `X-Hkj-Warnings` response header, and a `Left` produces the `{"success": false, "error": ...}` envelope (see [CONFIGURATION.md](CONFIGURATION.md#hkjwebeither-or-both-path-enabled)).

## When Jackson Serializers Are Used

Jackson serializers are used in the following scenarios:

### 1. Nested Either/Validated in Response DTOs

When Either or Validated appear **inside** other objects:

```java
public record BatchResult(
    String batchId,
    List<Either<String, User>> results  // Each Either will be serialized
) {}

@GetMapping("/batch/{id}")
public BatchResult getBatchResult(@PathVariable String id) {
    return new BatchResult(
        id,
        List.of(
            Either.right(user1),
            Either.left("User 2 not found"),
            Either.right(user3)
        )
    );
}
```

**Response:**
```json
{
  "batchId": "batch-123",
  "results": [
    {
      "isRight": true,
      "right": {"id": "1", "email": "user1@example.com"}
    },
    {
      "isRight": false,
      "left": "User 2 not found"
    },
    {
      "isRight": true,
      "right": {"id": "3", "email": "user3@example.com"}
    }
  ]
}
```

### 2. Manual ObjectMapper Usage

When you use ObjectMapper directly:

```java
@Service
public class ReportService {

    @Autowired
    private ObjectMapper objectMapper;

    public String generateReport(Either<Error, Report> reportResult) {
        try {
            // Jackson serializer is used here
            return objectMapper.writeValueAsString(reportResult);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize report", e);
        }
    }
}
```

### 3. Testing

When writing tests that serialize/deserialize:

```java
@Test
void shouldSerializeEither() throws Exception {
    Either<String, User> either = Either.right(new User("1", "alice@example.com"));

    String json = objectMapper.writeValueAsString(either);

    assertThat(json).contains("\"isRight\":true");
    assertThat(json).contains("\"right\"");
}
```

## When Jackson Serializers Are NOT Used

Jackson serializers are **NOT** used for top-level controller return values because the return value handlers take precedence:

```java
@GetMapping("/users/{id}")
public Either<DomainError, User> getUser(@PathVariable String id) {
    return userService.findById(id);
    // Handled by EitherPathReturnValueHandler, NOT Jackson serializer
}
```

In this case, `EitherPathReturnValueHandler` produces a cleaner unwrapped response:

**Success (Right):**
```json
{
  "id": "1",
  "email": "alice@example.com"
}
```

**Error (Left):**
```json
{
  "success": false,
  "error": "User with id '999' not found"
}
```

This is intentional - the unwrapped format is cleaner for API consumers.

## Comparison: Return Value Handlers vs Jackson Serializers

| Scenario | Handler Used | JSON Format |
|----------|--------------|-------------|
| Top-level Either in controller | EitherPathReturnValueHandler | Unwrapped (clean API) |
| Top-level Validated in controller | ValidationPathReturnValueHandler | Wrapped with valid/errors |
| Nested Either in DTO | Jackson EitherSerializer | Wrapped with isRight/left/right |
| Nested Validated in DTO | Jackson ValidatedSerializer | Wrapped with valid/value/errors |
| Manual ObjectMapper.writeValue() | Jackson serializers | Wrapped format |

## Client-Side Deserialization (`@HkjHttpClient`)

The serializers above are about *producing* JSON. The `@HkjHttpClient` client does the reverse: it
*reads* a failed HTTP response and decodes the body back into your declared error type. The default
decoder expects the server envelope `{"success": false, "error": <E>}` and deserialises `<E>` with
the same `JsonMapper`, reusing `HkjJacksonModule` so a nested `Either`/`Validated` inside the error
round-trips.

**A concrete error type needs no annotations:**

```java
public record ApiError(String code, String message) {}     // decodes directly
```

**A sealed error hierarchy needs a closed discriminator** so the decoder knows which subtype to
build (the deserialisation counterpart of the wrapped formats above):

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserNotFoundError.class, name = "not_found"),
    @JsonSubTypes.Type(value = RateLimitError.class, name = "rate_limited")
})
public sealed interface ApiError permits UserNotFoundError, RateLimitError {}
```

Use `Id.NAME`, never `Id.CLASS` / default typing, because the body comes from a remote service: see
[SECURITY.md](SECURITY.md#outbound-calls-hkjhttpclient).

**A client-only application** (one that calls services but does not itself return Path types) does
not get the server-side auto-configuration, so it must register `HkjJacksonModule` on its
`JsonMapper` for nested `Either`/`Validated` errors to deserialise:

```java
JsonMapper mapper = JsonMapper.builder().addModule(new HkjJacksonModule()).build();
```

Which type the body decodes into is the method's declared error type, narrowed by any `@OnStatus`
override or `hkj.client.status-error-mappings` entry. See the
[HTTP Client Reference](HTTP_CLIENT.md#error-decoding) for the decoding pipeline.

## Configuration

### Automatic Configuration (Default)

Jackson serializers are automatically registered when using Spring Boot:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
// HkjJacksonModule is automatically registered via auto-configuration
```

### Manual Configuration (Advanced)

If you need to manually configure the ObjectMapper:

```java
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new HkjJacksonModule());
        return mapper;
    }
}
```

### Disabling Auto-Configuration

To disable the Jackson auto-configuration:

```java
@SpringBootApplication(exclude = {HkjJacksonAutoConfiguration.class})
public class MyApplication {
    // ...
}
```

Or in `application.yml`:
```yaml
spring:
  autoconfigure:
    exclude:
      - org.higherkindedj.spring.autoconfigure.HkjJacksonAutoConfiguration
```

## Advanced Usage

### Nested Complex Scenarios

You can nest Either and Validated arbitrarily deep:

```java
public record ValidationResult(
    String processId,
    Either<String, Map<String, Validated<List<String>, User>>> results
) {}

@PostMapping("/validate-batch")
public ValidationResult validateBatch(@RequestBody List<UserRequest> requests) {
    // Complex nesting - all serialized correctly
    return new ValidationResult(
        UUID.randomUUID().toString(),
        Either.right(
            Map.of(
                "user1", Validated.valid(user1),
                "user2", Validated.invalid(List.of("Invalid email", "Name too short"))
            )
        )
    );
}
```

### Type Safety Considerations

Due to Java's type erasure, deserialized Either and Validated have `Object` types:

```java
String json = "{\"isRight\":true,\"right\":42}";
Either<?, ?> either = objectMapper.readValue(json, Either.class);

// Type is lost - either.getRight() returns Object, not Integer
Object value = either.getRight();  // Returns 42 as Object
```

For type-safe deserialization, use DTOs:

```java
public record UserResult(Either<String, User> result) {}

UserResult result = objectMapper.readValue(json, UserResult.class);
// Now the Either is properly typed through the DTO
```

## Testing Examples

### Unit Test Example

```java
@Test
void shouldSerializeNestedEither() throws Exception {
    record Response(String id, Either<String, User> data) {}

    Response response = new Response(
        "123",
        Either.right(new User("1", "alice@example.com"))
    );

    String json = objectMapper.writeValueAsString(response);

    assertThat(json).contains("\"id\":\"123\"");
    assertThat(json).contains("\"isRight\":true");
    assertThat(json).contains("\"email\":\"alice@example.com\"");
}
```

### Integration Test Example

```java
@SpringBootTest
@AutoConfigureMockMvc
class BatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldSerializeBatchResultWithNestedEither() throws Exception {
        mockMvc.perform(get("/api/batch/123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.batchId").value("123"))
            .andExpect(jsonPath("$.results[0].isRight").value(true))
            .andExpect(jsonPath("$.results[1].isRight").value(false))
            .andExpect(jsonPath("$.results[1].left").value("Error message"));
    }
}
```

## Troubleshooting

### Serializers Not Applied

**Problem:** Either/Validated not serializing with custom format

**Solution:** Verify HkjJacksonModule is registered by probing real serialisation behaviour (Jackson 3.x does not expose registered module IDs directly). The example module's `/api/users/debug/jackson-modules` endpoint serialises a sample `Either` with the injected `JsonMapper` and reports whether the tagged shape came back:

```java
@GetMapping("/debug/jackson-modules")
public Map<String, Object> getJacksonInfo() {
    String probe = jsonMapper.writeValueAsString(Either.<String, String>right("probe"));
    boolean hkjModulePresent = probe.contains("\"isRight\"");
    return Map.of("hkjModulePresent", hkjModulePresent, "eitherProbe", probe);
}
```

Or verify programmatically that the module is configured correctly by checking that serialization works as expected:
```java
@Test
void verifyModuleRegistered() throws Exception {
    Either<String, User> either = Either.right(new User("1", "test@example.com"));
    String json = objectMapper.writeValueAsString(either);

    // HkjJacksonModule produces this format
    assertThat(json).contains("\"isRight\":true");
    assertThat(json).contains("\"right\"");
}
```

### Conflicting JSON Format

**Problem:** Getting unwrapped format for nested Either

**Possible Causes:**
1. Return value handler is being used instead of Jackson
2. Custom ObjectMapper not configured with HkjJacksonModule

**Solution:** Ensure you're testing nested scenario, not top-level return value

### Type Erasure Issues

**Problem:** Deserialized values have wrong types

**Solution:** Use DTOs with specific types instead of raw Either/Validated deserialization

## See Also

- [Return Value Handlers Documentation](./README.md#return-value-handlers)
- [Testing Guide](./example/TESTING.md)
- [Configuration Reference](./CONFIGURATION.md)
- [HTTP Client Reference](./HTTP_CLIENT.md) - Decoding error bodies in `@HkjHttpClient` clients
