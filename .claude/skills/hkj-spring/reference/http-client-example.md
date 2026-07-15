# @HkjHttpClient Example Walkthrough

Condensed walkthrough of calling another service and keeping the typed error, from the
`hkj-spring/client-example` module (`.../spring/clientexample/`), a standalone client application
that calls the `hkj-spring/example` server.

---

## Setup

Add the starter (it bundles `spring-boot-restclient`, which binds `spring.http.serviceclient.*` and
applies the base URL; it is not pulled in by `spring-boot-starter-web` alone):

```gradle
dependencies {
    implementation("io.github.higher-kinded-j:hkj-spring-boot-starter:LATEST_VERSION")
}
```

Configure the base URL and timeouts per group. The group name defaults to the decapitalised
interface name:

```yaml
spring:
  http:
    serviceclient:
      userClientApi:
        base-url: http://users.internal
        connect-timeout: 2s
        read-timeout: 2s
```

---

## The Client Interface

A normal Spring `@HttpExchange` interface, annotated `@HkjHttpClient`, returning Effect Paths over
your own DTO and error types:

```java
public record UserDto(String id, String name) {}
public record ApiError(String code, String message) {}   // concrete error: binds with no annotations

@HttpExchange("/users")
@HkjHttpClient
public interface UserClientApi {

    @GetExchange("/{id}")
    EitherPath<ApiError, UserDto> getUser(@PathVariable String id);

    @PostExchange
    VTaskPath<Either<ApiError, UserDto>> create(@RequestBody UserDto body);
}
```

---

## What Gets Generated

Three siblings in the same package (you never name them; you autowire `UserClientApi`):

- `UserClientApiHttpExchange` : native `@HttpExchange` interface, return type unwrapped to
  `ResponseEntity<T>`, mapping annotations copied through. The piece Spring proxies.
- `UserClientApiClient` : implements `UserClientApi`, folding each outcome into the declared Path.
- `UserClientApiClientConfiguration` : `@Configuration` + `@ImportHttpServices` group + the client
  `@Bean`. Component-scanned with your app (same package as the interface).

---

## Using the Client

```java
@Service
public class ProfileService {

    private final UserClientApi users;
    public ProfileService(UserClientApi users) { this.users = users; }   // autowire the interface

    public Profile load(String id) {
        return users.getUser(id)                 // EitherPath<ApiError, UserDto>
            .run()                               // Either<ApiError, UserDto>
            .fold(this::onError, this::toProfile);
    }
}
```

A 2xx becomes `Right(body)`; a 4xx/5xx becomes `Left(decoded)`, decoded from the server's
`{"success":false,"error":…}` envelope into `ApiError`. Transport failures and undecodable bodies
propagate (they are not typed domain errors).

---

## Decoding the Error

A concrete `ApiError` binds directly. For a **sealed** hierarchy, add closed polymorphic type info
so Jackson can pick the subtype (never `Id.CLASS`/default typing on untrusted responses):

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserNotFoundError.class, name = "not-found"),
    @JsonSubTypes.Type(value = ConflictError.class, name = "conflict")
})
public sealed interface DomainError permits UserNotFoundError, ConflictError {}
```

Map specific statuses to subtypes, per method (highest precedence):

```java
@GetExchange("/{id}")
@OnStatus(value = 404, error = UserNotFoundError.class)
@OnStatus(value = 409, error = ConflictError.class)
EitherPath<DomainError, UserDto> getUser(@PathVariable String id);
```

…or globally for every client (the analogue of the server's `hkj.web.error-status-mappings`):

```yaml
hkj:
  client:
    status-error-mappings:
      404: com.example.UserNotFoundError
      429: com.example.RateLimitError
```

Precedence: `@OnStatus` (per method) beats the global mapping beats the declared type.

---

## Resilience (the VTaskPath variant)

`VTaskPath` defers the call onto a virtual thread, so the resilience combinators compose:

```java
Either<ApiError, UserDto> result =
    users.create(body)
        .withRetry(RetryPolicy.exponentialBackoffWithJitter(3, Duration.ofMillis(100)))
        .withCircuitBreaker(breaker)
        .timeout(Duration.ofSeconds(2))
        .unsafeRun();
```

---

## Testing the Client

Drive the generated client against a `MockRestServiceServer` (no Spring context needed):

```java
RestClient.Builder builder = RestClient.builder().baseUrl("http://users.test");
MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
var factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build())).build();
UserClientApiHttpExchange http = factory.createClient(UserClientApiHttpExchange.class);
UserClientApi client = new UserClientApiClient(http, new JsonResponseErrorDecoderFactory(jsonMapper));

server.expect(requestTo("http://users.test/users/1"))
      .andRespond(withSuccess("{\"id\":\"1\",\"name\":\"Ada\"}", MediaType.APPLICATION_JSON));
assertThatEither(client.getUser("1").run()).isRight().hasRight(new UserDto("1", "Ada"));

server.expect(requestTo("http://users.test/users/2"))
      .andRespond(withStatus(HttpStatus.NOT_FOUND)
          .body("{\"success\":false,\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"no user\"}}")
          .contentType(MediaType.APPLICATION_JSON));
assertThatEither(client.getUser("2").run()).isLeft();
```

To prove the **base-url wiring** end to end, a `@SpringBootTest` can point the group's base URL at a
stub server (e.g. a JDK `HttpServer`) via `@DynamicPropertySource` and assert the autowired client
resolves against it (see `UserClientApiBaseUrlTest` in the `hkj-spring/client-example` module).

---

## Key Takeaways

- `@HkjHttpClient` is the outbound inverse of the server handlers: the typed error survives the hop.
- Autowire your own interface; wiring (base URL, timeouts) is configuration via
  `spring.http.serviceclient.<group>.*`.
- Three return types: `EitherPath` (blocking), `VTaskPath<Either>` (deferred + resilience),
  `MaybePath` (404 → `Nothing`).
- Override status → error mapping per method (`@OnStatus`), globally
  (`hkj.client.status-error-mappings`), or wholesale (a `ResponseErrorDecoder` bean).
- Use `Id.NAME` + `@JsonSubTypes` for sealed errors; never `Id.CLASS` / default typing on untrusted
  responses.
