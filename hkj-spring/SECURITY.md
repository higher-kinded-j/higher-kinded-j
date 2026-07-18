# Higher-Kinded-J Spring Security Integration

Functional error handling for Spring Security using Either and Validated.

---

## Overview

The hkj-spring security integration brings functional programming patterns to Spring Security:

- **`ValidatedUserDetailsService`** - User authentication with error accumulation
- **`EitherAuthenticationConverter`** - JWT conversion with Either error handling
- **`EitherAuthorizationManager`** - Functional authorization decisions

### Key Benefits

- **Type-safe error handling** - No exceptions, explicit error types
- **Error accumulation** - Report ALL validation errors at once
- **Composable security rules** - Chain checks with flatMap/map
- **Better testability** - Pure functions, no side effects
- **Clear success/failure semantics** - Left = error, Right = success

---

## Quick Start

### 1. Enable Security Integration

```yaml
# application.yml
hkj:
  security:
    enabled: true
    either-authentication: true
    either-authorization: true
    # Opt-in: only enable when you will register accounts — once unique, this bean
    # becomes the application-wide UserDetailsService and it starts EMPTY.
    # validated-user-details: true
```

### 2. Configure Spring Security

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            EitherAuthorizationManager authManager) throws Exception {

        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/admin/**").access(authManager)
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(new EitherAuthenticationConverter())));

        return http.build();
    }
}
```

---

## Components

### ValidatedUserDetailsService

Accumulates validation errors instead of fail-fast exceptions.

#### Features

- Username format validation, with **ALL errors reported at once**
- In-memory user store, populated explicitly via `addUser(...)`
- `validateAccountStatus(...)` helper for an accumulated view of enabled/locked/expired checks (status *enforcement* stays with the authentication provider, per the `UserDetailsService` contract)

> **Warning — the service starts empty; sample users are opt-in.**
> `new ValidatedUserDetailsService()` registers **no** users; authentication fails for every
> username until you call `addUser(...)`. The `withSampleUsers()` factory pre-populates
> well-known demo accounts (`admin`/`admin123`, `user`/`user123`, `disabled`/`disabled123`)
> with plaintext `{noop}` passwords — use it for demos and tests only, **never in
> production**.

#### Example

```java
@Bean
public UserDetailsService userDetailsService(
        PasswordEncoder encoder,
        @Value("${app.security.alice-password}") String alicePassword) {
    var service = new ValidatedUserDetailsService();
    service.addUser(User.builder()
        .username("alice")
        .password(encoder.encode(alicePassword))  // never hard-code — source from config/secrets
        .roles("USER")
        .build());
    return service;
}
```

For a throwaway demo or test context only:

```java
@Bean
@Profile("demo")
public UserDetailsService demoUserDetailsService() {
    return ValidatedUserDetailsService.withSampleUsers();  // NEVER in production
}
```

**Error Accumulation:**

```
Username: "a@"

Traditional Spring Security:
Error: "Username must be at least 3 characters"

ValidatedUserDetailsService:
Error: "Username must be at least 3 characters; Username can only contain letters, numbers, underscores, and hyphens"
```

#### Adding Custom Users

```java
ValidatedUserDetailsService service = new ValidatedUserDetailsService();

UserDetails customUser = User.builder()
    .username("alice")
    .password("{noop}password123")
    .roles("USER", "MODERATOR")
    .build();

service.addUser(customUser);
```

---

### EitherAuthenticationConverter

Converts JWT tokens to Authentication using Either for functional error handling.

#### Features

- Extracts authorities from JWT claims using `Either` internally
- Configurable claim name and authority prefix
- **Rejects on failure (by default):** a *malformed* authorities claim always folds the `Left` into a thrown `BadCredentialsException` (HTTP 401); a *missing* claim does the same while `reject-missing-authorities-claim` stays `true` (the default), so a bad token yields no authenticated principal. Set that flag to `false` to let legitimately role-less tokens (e.g. client-credentials) authenticate with empty authorities — malformed claims stay rejected regardless

#### Example

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) {
    http.oauth2ResourceServer(oauth2 -> oauth2
        .jwt(jwt -> jwt
            .jwtAuthenticationConverter(new EitherAuthenticationConverter())));

    return http.build();
}
```

#### Custom Configuration

```java
// Custom claim name and authority prefix
EitherAuthenticationConverter converter =
    new EitherAuthenticationConverter("permissions", "PERMISSION_");

// JWT with "permissions": ["READ", "WRITE"]
// Becomes: PERMISSION_READ, PERMISSION_WRITE
```

#### Configuration Properties

```yaml
hkj:
  security:
    jwt-authorities-claim: "roles"      # JWT claim containing roles
    jwt-authority-prefix: "ROLE_"        # Prefix added to each role
    reject-missing-authorities-claim: true  # default: true
```

A token whose authorities claim is **missing** or **malformed** is rejected with
`BadCredentialsException` (surfaced as HTTP 401) — the converter never authenticates a token whose
authorities it could not read. If your IdP legitimately issues role-less tokens (e.g.
client-credentials), set `reject-missing-authorities-claim: false` to let a token with **no**
authorities claim authenticate with an empty authority set. A **malformed** (wrong-type) claim is
always rejected regardless of this flag.

> A present-but-empty list (`"roles": []`) always authenticates with empty authorities — it is a
> valid claim, not a missing one.

---

### EitherAuthorizationManager

Functional authorization decisions using Either composition.

#### Features

- Composes authorization checks with flatMap
- Type-safe error tracking
- Explicit success/failure states
- Path-based authorization rules

#### Example

```java
@Bean
public SecurityFilterChain filterChain(
        HttpSecurity http,
        EitherAuthorizationManager authManager) {

    http.authorizeHttpRequests(authz -> authz
        .requestMatchers("/api/admin/**").access(authManager)
        .anyRequest().authenticated());

    return http.build();
}
```

#### Authorization Flow

```java
// Rules are chained with flatMap: the first Left denies access
Either<AuthorizationError, Authentication> result = checkAuthentication(auth);
for (AuthorizationRule rule : rules) {
    result = result.flatMap(a -> rule.check(a, context));
}
// Short-circuits on first error (Either flatMap semantics)
```

Build your own policy with the rule factories, evaluated in order:

```java
var authManager = new EitherAuthorizationManager(List.of(
    EitherAuthorizationManager.requireAuthority("ROLE_ADMIN"),
    EitherAuthorizationManager.denyPathPrefix("/api/admin/dangerous")));
```

#### Built-in Rules

The no-arg `EitherAuthorizationManager` keeps the historical default policy as three explicit rules:
1. Authentication present and authenticated (always checked first)
2. `requireAuthority("ROLE_ADMIN")`
3. `denyPathPrefix("/api/admin/dangerous")` (matched against the decoded, normalised path)

---

## Configuration Reference

### Security Properties

```yaml
hkj:
  security:
    # Enable/disable security integration
    enabled: false  # default: false (opt-in)

    # ValidatedUserDetailsService (opt-in: starts empty and, once unique, becomes the
    # application-wide UserDetailsService)
    validated-user-details: false  # default: false

    # EitherAuthenticationConverter (JWT)
    either-authentication: true  # default: true
    jwt-authorities-claim: "roles"  # default: "roles"
    jwt-authority-prefix: "ROLE_"   # default: "ROLE_"
    reject-missing-authorities-claim: true  # default: true (false = lenient, role-less tokens ok)

    # EitherAuthorizationManager
    either-authorization: true  # default: true
```

### Auto-Configuration Conditions

All beans are conditional:

```java
@ConditionalOnClass(HttpSecurity.class)
@ConditionalOnProperty(prefix = "hkj.security", name = "enabled")
@ConditionalOnMissingBean
```

You can override any bean by defining your own.

---

## Examples

### Example 1: Custom ValidatedUserDetailsService

```java
@Service
public class DatabaseUserDetailsService extends ValidatedUserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        // Use Validated for comprehensive validation
        Validated<List<UserValidationError>, UserDetails> validated =
            validateUsername(username)
                .flatMap(this::loadFromDatabase)
                .flatMap(this::validateAccountStatus);

        return validated.fold(
            errors -> {
                String message = errors.stream()
                    .map(UserValidationError::message)
                    .collect(Collectors.joining("; "));
                throw new UsernameNotFoundException(message);
            },
            user -> user
        );
    }

    private Validated<List<UserValidationError>, User> loadFromDatabase(String username) {
        return userRepository.findByUsername(username)
            .map(Validated::<List<UserValidationError>, User>valid)
            .orElse(Validated.invalid(List.of(
                new UserValidationError("User not found: " + username)
            )));
    }
}
```

### Example 2: Custom Authorization Rules

```java
@Component
public class CustomAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision check(
            Supplier<Authentication> authentication,
            RequestAuthorizationContext context) {

        Either<AuthError, AuthSuccess> result =
            checkAuthentication(authentication.get())
                .flatMap(this::checkTenantAccess)
                .flatMap(this::checkRateLimit)
                .flatMap(auth -> checkPermissions(auth, context));

        return result.fold(
            error -> new AuthorizationDecision(false),
            success -> new AuthorizationDecision(true)
        );
    }

    private Either<AuthError, Authentication> checkTenantAccess(Authentication auth) {
        String tenant = extractTenant(auth);
        boolean hasAccess = tenantService.hasAccess(tenant);

        return hasAccess ?
            Either.right(auth) :
            Either.left(new AuthError("Tenant access denied"));
    }
}
```

### Example 3: JWT with Custom Claims

```java
@Configuration
public class JwtSecurityConfig {

    @Bean
    public EitherAuthenticationConverter authConverter() {
        // Extract roles from "authorities" claim
        // Add "AUTH_" prefix instead of "ROLE_"
        return new EitherAuthenticationConverter("authorities", "AUTH_");
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            EitherAuthenticationConverter converter) {

        http.oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt
                .jwtAuthenticationConverter(converter)));

        return http.build();
    }
}
```

---

## Testing

### Testing ValidatedUserDetailsService

```java
@Test
void shouldAccumulateMultipleValidationErrors() {
    ValidatedUserDetailsService service = new ValidatedUserDetailsService();

    // Username with multiple issues
    assertThatThrownBy(() -> service.loadUserByUsername("a@"))
        .isInstanceOf(UsernameNotFoundException.class)
        .hasMessageMatching(".*at least 3 characters.*can only contain.*");
}
```

### Testing EitherAuthenticationConverter

```java
@Test
void shouldConvertJwtWithRoles() {
    EitherAuthenticationConverter converter = new EitherAuthenticationConverter();

    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject("user123")
        .claim("roles", List.of("USER", "ADMIN"))
        .build();

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token.getAuthorities()).extracting("authority")
        .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
}

@Test
void shouldRejectJwtWithMissingRolesClaim() {
    EitherAuthenticationConverter converter = new EitherAuthenticationConverter();

    Jwt jwt = Jwt.withTokenValue("token")
        .header("alg", "none")
        .subject("user123")
        .claim("other", "value")   // no "roles" claim
        .build();

    assertThatThrownBy(() -> converter.convert(jwt))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessageContaining("Missing authorities claim");
}
```

### Testing EitherAuthorizationManager

```java
@Test
void shouldGrantAccessForAdmin() {
    EitherAuthorizationManager manager = new EitherAuthorizationManager();

    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getAuthorities()).thenReturn(
        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
    );
    when(request.getRequestURI()).thenReturn("/api/admin/users");

    AuthorizationDecision decision = manager.check(() -> authentication, context);

    assertThat(decision.isGranted()).isTrue();
}
```

---

## Migration from Traditional Spring Security

### Before: Exception-based UserDetailsService

```java
@Override
public UserDetails loadUserByUsername(String username) {
    if (username == null || username.length() < 3) {
        throw new UsernameNotFoundException("Invalid username");
    }

    User user = userRepository.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found"));

    if (!user.isEnabled()) {
        throw new DisabledException("Account disabled");
    }

    return user;
}
```

### After: Validated-based UserDetailsService

```java
@Override
public UserDetails loadUserByUsername(String username) {
    Validated<List<UserValidationError>, UserDetails> validated =
        validateUsername(username)
            .flatMap(this::loadUser)
            .flatMap(this::validateAccountStatus);

    return validated.fold(
        errors -> {
            // ALL errors reported at once
            String message = errors.stream()
                .map(UserValidationError::message)
                .collect(Collectors.joining("; "));
            throw new UsernameNotFoundException(message);
        },
        user -> user
    );
}
```

**Benefits:**
- Reports all validation errors (not just first)
- More testable (pure functions)
- Type-safe error handling
- Composable validation rules

---

## Best Practices

### 1. Enable Security Integration Explicitly

```yaml
# Opt-in to avoid conflicts with existing security config
hkj:
  security:
    enabled: true
```

### 2. Customize Error Messages

```java
public record UserValidationError(String field, String message) {}

private Validated<List<UserValidationError>, String> validateEmail(String email) {
    if (email == null || !email.contains("@")) {
        return Validated.invalid(List.of(
            new UserValidationError("email", "Must be valid email format")
        ));
    }
    return Validated.valid(email);
}
```

### 3. Use Specific Error Types

```java
// Specific error types for better error handling
public sealed interface AuthError permits
    MissingAuthenticationError,
    InvalidRoleError,
    PathDeniedError {}

public record MissingAuthenticationError() implements AuthError {}
public record InvalidRoleError(String required, List<String> actual) implements AuthError {}
public record PathDeniedError(String path, String reason) implements AuthError {}
```

### 4. Test with Real Scenarios

```java
@Test
void shouldHandleRealWorldAuthenticationFlow() {
    // Test complete auth flow
    Jwt jwt = createRealJwt();
    Authentication auth = converter.convert(jwt);
    AuthorizationDecision decision = manager.check(() -> auth, context);

    assertThat(decision.isGranted()).isTrue();
}
```

---

## Troubleshooting

### Issue: Security beans not created

**Solution:** Enable security integration explicitly:

```yaml
hkj:
  security:
    enabled: true
```

### Issue: JWT roles not mapping correctly

**Solution:** Check JWT claim name and prefix:

```yaml
hkj:
  security:
    jwt-authorities-claim: "roles"  # or "authorities", "permissions", etc.
    jwt-authority-prefix: "ROLE_"   # or "" for no prefix
```

### Issue: Authorization always fails

**Solution:** Ensure authentication has required role:

```java
// EitherAuthorizationManager requires ROLE_ADMIN by default
when(authentication.getAuthorities()).thenReturn(
    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
);
```

---

## Outbound Calls (`@HkjHttpClient`)

`@HkjHttpClient` generates a typed client for calling *other* services (the inverse of the
server-side handlers). Three security concerns apply when this service is the caller.

**Treat decoded error bodies as untrusted input.** A failed response is deserialised into your
declared error type via Jackson. For a sealed `DomainError` hierarchy this is polymorphic
deserialisation, so always pin a **closed** discriminator:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")   // safe: names you control
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserNotFoundError.class, name = "not_found"),
    @JsonSubTypes.Type(value = RateLimitError.class, name = "rate_limited")
})
public sealed interface ApiError permits UserNotFoundError, RateLimitError {}
```

Never use `JsonTypeInfo.Id.CLASS` / `Id.MINIMAL_CLASS` or enable default typing for a type decoded
from a remote response: an attacker who controls the upstream (or sits on the path) could name an
arbitrary class and turn deserialisation into a gadget chain. With `Id.NAME` the decoder can only
construct the subtypes you enumerated. See
[Jackson Serialization](JACKSON_SERIALIZATION.md#client-side-deserialization-hkjhttpclient) for the
round-trip details.

**Streams are bounded.** The SSE translator caps each frame line at 1 MiB and raises
`SseStreamException` rather than buffering an unbounded line, so a hostile or buggy upstream cannot
exhaust caller memory.

**Credential propagation.** The generated client is a standard Spring HTTP Service proxy over a
`RestClient`. Attach outbound credentials (bearer tokens, API keys, mTLS) with a `RestClient`
interceptor or `spring.http.serviceclient.<group>.default-headers`, and scope them to the trust
boundary you are crossing: do not blindly forward an inbound caller's token to an unrelated service.

---

## See Also

- [Spring Security Reference](https://docs.spring.io/spring-security/reference/index.html)
- [hkj-spring README](README.md) - Main documentation
- [CONFIGURATION.md](CONFIGURATION.md) - Configuration properties
- [HTTP Client Reference](HTTP_CLIENT.md) - Declarative `@HkjHttpClient` clients

---

## License

Apache License 2.0 - See [LICENSE.md](../LICENSE.md)
