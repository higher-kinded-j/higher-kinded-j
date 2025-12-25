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
    validated-user-details: true
    either-authentication: true
    either-authorization: true
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

- Username format validation
- Account status validation (enabled, locked, expired)
- Credentials expiration checking
- **ALL errors reported at once**

#### Example

```java
@Bean
public UserDetailsService userDetailsService() {
    return new ValidatedUserDetailsService();
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

- Extracts authorities from JWT claims
- Handles missing/invalid claims gracefully
- Configurable claim name and authority prefix
- Returns empty authorities on error (no exceptions)

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
```

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
Either<AuthorizationError, AuthorizationSuccess> result =
    checkAuthentication(auth)           // Is user authenticated?
        .flatMap(this::checkAuthorities)     // Has required roles?
        .flatMap(auth -> checkRequestPath(auth, context));  // Path allowed?

// Short-circuits on first error (Either flatMap semantics)
```

#### Built-in Rules

The default `EitherAuthorizationManager` requires:
1. Authentication present
2. Authentication authenticated
3. `ROLE_ADMIN` authority
4. Blocks `/api/admin/dangerous` paths

---

## Configuration Reference

### Security Properties

```yaml
hkj:
  security:
    # Enable/disable security integration
    enabled: false  # default: false (opt-in)

    # ValidatedUserDetailsService
    validated-user-details: true  # default: true

    # EitherAuthenticationConverter (JWT)
    either-authentication: true  # default: true
    jwt-authorities-claim: "roles"  # default: "roles"
    jwt-authority-prefix: "ROLE_"   # default: "ROLE_"

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

## See Also

- [Spring Security Reference](https://docs.spring.io/spring-security/reference/index.html)
- [hkj-spring README](README.md) - Main documentation
- [CONFIGURATION.md](CONFIGURATION.md) - Configuration properties

---

## License

Apache License 2.0 - See [LICENSE.md](../LICENSE.md)
