# SecurityContext: Authentication and Authorisation Patterns

> *"The thing about a shark, it's got lifeless eyes, black eyes, like a doll's eyes. When it comes at you it doesn't seem to be livin'... until he bites you."*
>
> -- Quint, *Jaws*

The unauthorised request looks just like any other. Same headers, same format, same endpoint. It doesn't seem malicious, until it's accessing data it shouldn't, performing actions reserved for administrators, or exfiltrating information that will cost you millions. By the time you notice, the damage is done.

> *"You think when you wake up in the mornin yesterday don't count. But yesterday is all that does count."*
>
> -- Cormac McCarthy, *No Country for Old Men*

Yesterday's unvalidated request is today's security incident. Yesterday's missing role check is today's data breach. Security isn't a feature you add later; it's context that must flow through every layer of your application, verified at every boundary, never assumed.

`SecurityContext` provides patterns for propagating authentication state and performing authorisation checks within the scoped context system, ensuring security decisions flow correctly through virtual thread boundaries.

~~~admonish info title="What You'll Learn"
- Propagating authentication state through concurrent operations
- Implementing role-based access control with `hasRole` and `requireRole`
- Guarding operations with authorisation checks
- Handling unauthenticated and anonymous contexts safely
- Propagating authentication tokens to downstream services
- Combining security context with request context
~~~

~~~admonish example title="Example Code"
- [SecurityContextExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/context/SecurityContextExample.java) -- Complete security pattern examples
~~~

---

## The SecurityContext Utility Class

`SecurityContext` provides pre-defined `ScopedValue` instances for authentication and authorisation:

```java
public final class SecurityContext {
    private SecurityContext() {}  // Utility class -- no instantiation

    /**
     * The authenticated principal (user identity).
     * Null if the request is anonymous/unauthenticated.
     * Unbound ScopedValue indicates a configuration error.
     */
    public static final ScopedValue<Principal> PRINCIPAL = ScopedValue.newInstance();

    /**
     * Set of roles granted to the current user.
     * Empty set for anonymous users, never null.
     */
    public static final ScopedValue<Set<String>> ROLES = ScopedValue.newInstance();

    /**
     * Set of fine-grained permissions granted to the current user.
     * For systems that need more granularity than roles.
     */
    public static final ScopedValue<Set<String>> PERMISSIONS = ScopedValue.newInstance();

    /**
     * Authentication token for propagation to downstream services.
     * Typically a JWT or OAuth token.
     */
    public static final ScopedValue<String> AUTH_TOKEN = ScopedValue.newInstance();

    /**
     * Session identifier for audit and tracking.
     */
    public static final ScopedValue<String> SESSION_ID = ScopedValue.newInstance();
}
```

~~~admonish warning title="Security Principle: Defence in Depth"
Never rely solely on context propagation for security. Always:
1. Validate authentication at system boundaries (API gateway, filters)
2. Check authorisation before sensitive operations
3. Log security-relevant events with full context
4. Assume any layer could be bypassed and verify at each level
~~~

---

## Principal: User Identity

The `PRINCIPAL` represents the authenticated user. It can be:
- **Non-null**: An authenticated user
- **Null**: An anonymous/unauthenticated user
- **Unbound**: A configuration error (security context not established)

### Defining a Principal

```java
/**
 * Simple principal implementation.
 * Extend with additional claims as needed.
 */
public record UserPrincipal(
    String id,
    String username,
    String email,
    Instant authenticatedAt
) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
```

### Establishing Security Context

At your authentication boundary (filter, interceptor):

```java
public class SecurityFilter implements Filter {

    private final AuthenticationService authService;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;

        // Attempt authentication
        AuthResult authResult = authService.authenticate(request);

        // Establish security context
        ScopedValue.where(SecurityContext.PRINCIPAL, authResult.principal())
            .where(SecurityContext.ROLES, authResult.roles())
            .where(SecurityContext.PERMISSIONS, authResult.permissions())
            .where(SecurityContext.AUTH_TOKEN, authResult.token())
            .where(SecurityContext.SESSION_ID, authResult.sessionId())
            .run(() -> {
                try {
                    chain.doFilter(req, res);
                } catch (IOException | ServletException e) {
                    throw new RuntimeException(e);
                }
            });
    }
}

record AuthResult(
    Principal principal,      // null if anonymous
    Set<String> roles,        // empty if anonymous
    Set<String> permissions,  // empty if anonymous
    String token,             // null if anonymous
    String sessionId          // always present for tracking
) {
    static AuthResult anonymous(String sessionId) {
        return new AuthResult(null, Set.of(), Set.of(), null, sessionId);
    }
}
```

---

## Authentication Checks

### isAuthenticated: Query Authentication State

```java
public final class SecurityContext {
    // ... ScopedValue declarations ...

    /**
     * Check if the current context has an authenticated principal.
     * Returns false for anonymous users (null principal).
     *
     * @return Context that evaluates to true if authenticated
     */
    public static Context<Principal, Boolean> isAuthenticated() {
        return Context.asks(PRINCIPAL, principal -> principal != null);
    }
}

// Usage
public VTask<Response> handleRequest(Request request) {
    boolean authenticated = SecurityContext.isAuthenticated()
        .toVTask()
        .runSafe()
        .getOrElse(false);

    if (authenticated) {
        return handleAuthenticatedRequest(request);
    } else {
        return handleAnonymousRequest(request);
    }
}
```

### requireAuthenticated: Enforce Authentication

```java
public final class SecurityContext {
    // ... other methods ...

    /**
     * Require authentication, failing if the user is anonymous.
     *
     * @return Context containing the Principal, or failing with UnauthenticatedException
     */
    public static Context<Principal, Principal> requireAuthenticated() {
        return Context.ask(PRINCIPAL).flatMap(principal ->
            principal != null
                ? Context.succeed(principal)
                : Context.fail(new UnauthenticatedException("Authentication required")));
    }
}

// Usage -- protect an endpoint
public VTask<UserProfile> getMyProfile() {
    return SecurityContext.requireAuthenticated()
        .map(principal -> profileService.getProfile(principal.getName()))
        .toVTask();
}
```

### principalIfPresent: Optional Access

```java
public final class SecurityContext {
    // ... other methods ...

    /**
     * Get the principal if authenticated, or empty Maybe if anonymous.
     * Useful for features that work differently for authenticated vs anonymous users.
     *
     * @return Context containing Maybe of the Principal
     */
    public static Context<Principal, Maybe<Principal>> principalIfPresent() {
        return Context.asks(PRINCIPAL, principal ->
            principal != null ? Maybe.just(principal) : Maybe.nothing());
    }
}

// Usage -- personalised vs generic greeting
public VTask<String> getGreeting() {
    return SecurityContext.principalIfPresent()
        .map(maybePrincipal -> maybePrincipal
            .map(p -> "Welcome back, " + p.getName() + "!")
            .orElse("Welcome, guest!"))
        .toVTask();
}
```

---

## Role-Based Access Control

### hasRole: Query Role Membership

```java
public final class SecurityContext {
    // ... other methods ...

    /**
     * Check if the current user has a specific role.
     * Returns false if user is anonymous or doesn't have the role.
     *
     * @param role The role to check
     * @return Context that evaluates to true if user has the role
     */
    public static Context<Set<String>, Boolean> hasRole(String role) {
        Objects.requireNonNull(role, "role cannot be null");
        return Context.asks(ROLES, roles -> roles.contains(role));
    }

    /**
     * Check if the current user has any of the specified roles.
     *
     * @param roles The roles to check (at least one must match)
     * @return Context that evaluates to true if user has any of the roles
     */
    public static Context<Set<String>, Boolean> hasAnyRole(String... roles) {
        Objects.requireNonNull(roles, "roles cannot be null");
        Set<String> required = Set.of(roles);
        return Context.asks(ROLES, userRoles ->
            userRoles.stream().anyMatch(required::contains));
    }

    /**
     * Check if the current user has all of the specified roles.
     *
     * @param roles The roles to check (all must match)
     * @return Context that evaluates to true if user has all the roles
     */
    public static Context<Set<String>, Boolean> hasAllRoles(String... roles) {
        Objects.requireNonNull(roles, "roles cannot be null");
        Set<String> required = Set.of(roles);
        return Context.asks(ROLES, userRoles -> userRoles.containsAll(required));
    }
}
```

### Using Role Checks for Conditional Logic

```java
public VTask<DashboardData> getDashboard() {
    return SecurityContext.hasRole("ADMIN")
        .flatMap(isAdmin -> isAdmin
            ? Context.succeed(getAdminDashboard())
            : Context.succeed(getUserDashboard()))
        .toVTask();
}

public VTask<List<MenuItem>> getMenuItems() {
    return SecurityContext.hasAnyRole("ADMIN", "MANAGER")
        .map(canManage -> {
            List<MenuItem> items = new ArrayList<>();
            items.add(MenuItem.HOME);
            items.add(MenuItem.PROFILE);

            if (canManage) {
                items.add(MenuItem.REPORTS);
                items.add(MenuItem.SETTINGS);
            }

            return items;
        })
        .toVTask();
}
```

### requireRole: Enforce Role Membership

```java
public final class SecurityContext {
    // ... other methods ...

    /**
     * Require a specific role, failing if the user doesn't have it.
     *
     * @param role The required role
     * @return Context containing Unit on success, or failing with UnauthorisedException
     */
    public static Context<Set<String>, Unit> requireRole(String role) {
        Objects.requireNonNull(role, "role cannot be null");
        return hasRole(role).flatMap(has -> has
            ? Context.succeed(Unit.INSTANCE)
            : Context.fail(new UnauthorisedException("Role required: " + role)));
    }

    /**
     * Require any of the specified roles.
     *
     * @param roles The roles (at least one required)
     * @return Context containing Unit on success, or failing with UnauthorisedException
     */
    public static Context<Set<String>, Unit> requireAnyRole(String... roles) {
        Objects.requireNonNull(roles, "roles cannot be null");
        return hasAnyRole(roles).flatMap(has -> has
            ? Context.succeed(Unit.INSTANCE)
            : Context.fail(new UnauthorisedException(
                "One of these roles required: " + String.join(", ", roles))));
    }

    /**
     * Require all of the specified roles.
     *
     * @param roles The roles (all required)
     * @return Context containing Unit on success, or failing with UnauthorisedException
     */
    public static Context<Set<String>, Unit> requireAllRoles(String... roles) {
        Objects.requireNonNull(roles, "roles cannot be null");
        return hasAllRoles(roles).flatMap(has -> has
            ? Context.succeed(Unit.INSTANCE)
            : Context.fail(new UnauthorisedException(
                "All of these roles required: " + String.join(", ", roles))));
    }
}
```

### Using requireRole as a Guard

```java
// Pattern: Guard clause at the start of a method
public VTask<Report> generateFinancialReport(ReportRequest request) {
    return SecurityContext.requireRole("FINANCE")
        .flatMap(_ -> Context.succeed(reportService.generate(request)))
        .toVTask();
}

// Pattern: Guard with detailed error
public VTask<Void> deleteUser(String userId) {
    return SecurityContext.requireRole("ADMIN")
        .mapError(e -> new SecurityException(
            "Cannot delete user: " + e.getMessage()))
        .flatMap(_ -> Context.succeed(userService.delete(userId)))
        .toVTask();
}

// Pattern: Multiple guards combined
public VTask<AuditLog> viewAuditLog(String resourceId) {
    return SecurityContext.requireAuthenticated()
        .flatMap(_ -> SecurityContext.requireAnyRole("ADMIN", "AUDITOR"))
        .flatMap(_ -> Context.succeed(auditService.getLog(resourceId)))
        .toVTask();
}
```

---

## Permission-Based Access Control

For systems needing finer granularity than roles:

```java
public final class SecurityContext {
    // ... other methods ...

    /**
     * Check if the current user has a specific permission.
     */
    public static Context<Set<String>, Boolean> hasPermission(String permission) {
        Objects.requireNonNull(permission, "permission cannot be null");
        return Context.asks(PERMISSIONS, perms -> perms.contains(permission));
    }

    /**
     * Require a specific permission.
     */
    public static Context<Set<String>, Unit> requirePermission(String permission) {
        Objects.requireNonNull(permission, "permission cannot be null");
        return hasPermission(permission).flatMap(has -> has
            ? Context.succeed(Unit.INSTANCE)
            : Context.fail(new UnauthorisedException(
                "Permission required: " + permission)));
    }
}

// Usage with fine-grained permissions
public VTask<Document> readDocument(String documentId) {
    return SecurityContext.requirePermission("document:read")
        .flatMap(_ -> Context.succeed(documentService.get(documentId)))
        .toVTask();
}

public VTask<Void> updateDocument(String documentId, DocumentUpdate update) {
    return SecurityContext.requirePermission("document:write")
        .flatMap(_ -> Context.succeed(documentService.update(documentId, update)))
        .toVTask();
}

public VTask<Void> deleteDocument(String documentId) {
    return SecurityContext.requirePermission("document:delete")
        .flatMap(_ -> Context.succeed(documentService.delete(documentId)))
        .toVTask();
}
```

---

## Resource-Level Authorisation

Beyond role/permission checks, you often need to verify access to specific resources:

```java
public class DocumentAuthorisation {

    /**
     * Check if the current user can access a specific document.
     */
    public Context<Principal, Boolean> canAccess(Document document) {
        return SecurityContext.principalIfPresent()
            .map(maybePrincipal -> maybePrincipal
                .map(principal -> isOwnerOrShared(principal, document))
                .orElse(document.isPublic()));
    }

    /**
     * Require access to a specific document.
     */
    public Context<Principal, Document> requireAccess(Document document) {
        return canAccess(document).flatMap(allowed -> allowed
            ? Context.succeed(document)
            : Context.fail(new ForbiddenException(
                "Access denied to document: " + document.id())));
    }

    private boolean isOwnerOrShared(Principal principal, Document document) {
        return document.ownerId().equals(principal.getName())
            || document.sharedWith().contains(principal.getName());
    }
}

// Usage
public VTask<Document> getDocument(String documentId) {
    return VTask.delay(() -> documentRepository.findById(documentId))
        .flatMap(document -> documentAuth.requireAccess(document).toVTask());
}
```

---

## Token Propagation to Downstream Services

When calling external services, propagate authentication tokens:

```java
public class AuthenticatedHttpClient {
    private final HttpClient httpClient;

    /**
     * Make an authenticated HTTP request.
     * Automatically includes the auth token from security context.
     */
    public <T> VTask<T> get(String url, Class<T> responseType) {
        return VTask.delay(() -> {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET();

            // Propagate auth token if present
            if (SecurityContext.AUTH_TOKEN.isBound()) {
                String token = SecurityContext.AUTH_TOKEN.get();
                if (token != null) {
                    builder.header("Authorization", "Bearer " + token);
                }
            }

            // Also propagate trace context
            if (RequestContext.TRACE_ID.isBound()) {
                builder.header("X-Trace-ID", RequestContext.TRACE_ID.get());
            }

            HttpResponse<String> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString());

            return parseResponse(response, responseType);
        });
    }

    /**
     * Make a request with explicit token override.
     * Useful for service-to-service calls with different credentials.
     */
    public <T> VTask<T> getWithToken(String url, String token, Class<T> responseType) {
        return ScopedValue
            .where(SecurityContext.AUTH_TOKEN, token)
            .call(() -> get(url, responseType).run());
    }
}
```

---

## Security Context in Concurrent Operations

Security context propagates to forked virtual threads:

```java
public VTask<OrderValidation> validateOrder(Order order) {
    // All forked tasks inherit security context
    return Scope.<ValidationResult>allSucceed()
        .fork(() -> {
            // Has access to PRINCIPAL, ROLES, etc.
            return validateInventory(order);
        })
        .fork(() -> {
            // Also has security context
            return validatePaymentMethod(order);
        })
        .fork(() -> {
            // Security context available here too
            return validateShippingAddress(order);
        })
        .join(OrderValidation::combine)
        .run();
}

private VTask<ValidationResult> validatePaymentMethod(Order order) {
    return VTask.delay(() -> {
        // Can check permissions for payment validation
        Principal principal = SecurityContext.PRINCIPAL.get();
        if (principal == null) {
            return ValidationResult.failure("Authentication required for payment");
        }

        // Verify the payment method belongs to the user
        if (!paymentService.belongsTo(order.paymentMethodId(), principal.getName())) {
            return ValidationResult.failure("Payment method does not belong to user");
        }

        return ValidationResult.success();
    });
}
```

```
┌──────────────────────────────────────────────────────────────────────┐
│              Security Context Propagation in Scope                   │
│                                                                      │
│   Authentication Filter                                              │
│   ┌──────────────────────────────────────────────────────────────┐   │
│   │ ScopedValue.where(PRINCIPAL, user)                           │   │
│   │            .where(ROLES, Set.of("USER", "CUSTOMER"))         │   │
│   │            .where(AUTH_TOKEN, "jwt-token-xyz")               │   │
│   └──────────────────────┬───────────────────────────────────────┘   │
│                          │                                           │
│                          ▼                                           │
│   Order Service                                                      │
│   ┌──────────────────────────────────────────────────────────────┐   │
│   │ Scope.allSucceed()                                           │   │
│   │   │                                                          │   │
│   │   ├── fork(validateInventory)                                │   │
│   │   │      PRINCIPAL = user ✓                                  │   │
│   │   │      ROLES = ["USER","CUSTOMER"] ✓                       │   │
│   │   │                                                          │   │
│   │   ├── fork(validatePayment)                                  │   │
│   │   │      PRINCIPAL = user ✓                                  │   │
│   │   │      AUTH_TOKEN = "jwt-token-xyz" ✓                      │   │
│   │   │                                                          │   │
│   │   └── fork(validateShipping)                                 │   │
│   │          PRINCIPAL = user ✓                                  │   │
│   │          Can call hasRole("CUSTOMER") ✓                      │   │
│   │                                                              │   │
│   └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Audit Logging with Security Context

Log security-relevant events with full context:

```java
public class SecurityAuditLogger {
    private static final ContextLogger log = new ContextLogger(SecurityAuditLogger.class);

    public void logAccess(String resource, String action) {
        String principalName = SecurityContext.PRINCIPAL.isBound()
            && SecurityContext.PRINCIPAL.get() != null
            ? SecurityContext.PRINCIPAL.get().getName()
            : "anonymous";

        String sessionId = SecurityContext.SESSION_ID.isBound()
            ? SecurityContext.SESSION_ID.get()
            : "no-session";

        log.info("AUDIT: user={} session={} action={} resource={}",
            principalName, sessionId, action, resource);
    }

    public void logAccessDenied(String resource, String action, String reason) {
        String principalName = SecurityContext.PRINCIPAL.isBound()
            && SecurityContext.PRINCIPAL.get() != null
            ? SecurityContext.PRINCIPAL.get().getName()
            : "anonymous";

        log.warn("AUDIT_DENIED: user={} action={} resource={} reason={}",
            principalName, action, resource, reason);
    }

    public void logAuthenticationSuccess(Principal principal) {
        log.info("AUTH_SUCCESS: user={} at={}",
            principal.getName(),
            Instant.now());
    }

    public void logAuthenticationFailure(String attemptedUsername, String reason) {
        log.warn("AUTH_FAILURE: attempted_user={} reason={} at={}",
            attemptedUsername, reason, Instant.now());
    }
}
```

---

## Exception Types

Define clear exception types for security failures:

```java
/**
 * Base exception for all security-related failures.
 */
public sealed class SecurityException extends RuntimeException
    permits UnauthenticatedException, UnauthorisedException, ForbiddenException {

    public SecurityException(String message) {
        super(message);
    }

    public SecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Thrown when authentication is required but not present.
 * Maps to HTTP 401 Unauthorized.
 */
public final class UnauthenticatedException extends SecurityException {
    public UnauthenticatedException(String message) {
        super(message);
    }
}

/**
 * Thrown when the user lacks required roles or permissions.
 * Maps to HTTP 403 Forbidden.
 */
public final class UnauthorisedException extends SecurityException {
    public UnauthorisedException(String message) {
        super(message);
    }
}

/**
 * Thrown when access to a specific resource is denied.
 * Maps to HTTP 403 Forbidden.
 */
public final class ForbiddenException extends SecurityException {
    public ForbiddenException(String message) {
        super(message);
    }
}
```

---

## Combining Security and Request Context

Security context works alongside request context:

```java
public class SecureRequestHandler {
    private static final ContextLogger log = new ContextLogger(SecureRequestHandler.class);
    private final SecurityAuditLogger audit = new SecurityAuditLogger();

    public Response handle(HttpRequest request) {
        // Establish both contexts together
        AuthResult auth = authenticate(request);

        return ScopedValue
            // Request context
            .where(RequestContext.TRACE_ID, request.traceId())
            .where(RequestContext.LOCALE, request.locale())
            .where(RequestContext.REQUEST_TIME, Instant.now())
            // Security context
            .where(SecurityContext.PRINCIPAL, auth.principal())
            .where(SecurityContext.ROLES, auth.roles())
            .where(SecurityContext.AUTH_TOKEN, auth.token())
            .where(SecurityContext.SESSION_ID, auth.sessionId())
            .call(() -> {
                log.info("Handling request");  // Has trace ID
                audit.logAccess(request.path(), request.method());  // Has principal

                try {
                    return processRequest(request);
                } catch (SecurityException e) {
                    audit.logAccessDenied(request.path(), request.method(), e.getMessage());
                    return mapSecurityException(e);
                }
            });
    }

    private Response mapSecurityException(SecurityException e) {
        return switch (e) {
            case UnauthenticatedException _ -> Response.unauthorized();
            case UnauthorisedException _ -> Response.forbidden(e.getMessage());
            case ForbiddenException _ -> Response.forbidden(e.getMessage());
        };
    }
}
```

---

## Testing Security Patterns

### Testing Role Checks

```java
@Test
void shouldAllowAdminAccess() {
    Principal admin = new UserPrincipal("1", "admin", "admin@example.com", Instant.now());

    Boolean result = ScopedValue
        .where(SecurityContext.PRINCIPAL, admin)
        .where(SecurityContext.ROLES, Set.of("ADMIN", "USER"))
        .call(() -> SecurityContext.hasRole("ADMIN").run());

    assertThat(result).isTrue();
}

@Test
void shouldDenyAccessWithoutRole() {
    Principal user = new UserPrincipal("2", "user", "user@example.com", Instant.now());

    Boolean result = ScopedValue
        .where(SecurityContext.PRINCIPAL, user)
        .where(SecurityContext.ROLES, Set.of("USER"))
        .call(() -> SecurityContext.hasRole("ADMIN").run());

    assertThat(result).isFalse();
}

@Test
void shouldThrowWhenRoleRequired() {
    Principal user = new UserPrincipal("2", "user", "user@example.com", Instant.now());

    assertThatThrownBy(() -> ScopedValue
        .where(SecurityContext.PRINCIPAL, user)
        .where(SecurityContext.ROLES, Set.of("USER"))
        .call(() -> SecurityContext.requireRole("ADMIN").run()))
        .isInstanceOf(UnauthorisedException.class)
        .hasMessageContaining("ADMIN");
}
```

### Testing Anonymous Access

```java
@Test
void shouldHandleAnonymousUser() {
    Boolean authenticated = ScopedValue
        .where(SecurityContext.PRINCIPAL, (Principal) null)
        .where(SecurityContext.ROLES, Set.of())
        .call(() -> SecurityContext.isAuthenticated().run());

    assertThat(authenticated).isFalse();
}

@Test
void shouldReturnEmptyMaybeForAnonymous() {
    Maybe<Principal> result = ScopedValue
        .where(SecurityContext.PRINCIPAL, (Principal) null)
        .call(() -> SecurityContext.principalIfPresent().run());

    assertThat(result.isNothing()).isTrue();
}
```

---

## Summary

| Method | Returns | Purpose |
|--------|---------|---------|
| `isAuthenticated()` | `Context<Principal, Boolean>` | Check if user is authenticated |
| `requireAuthenticated()` | `Context<Principal, Principal>` | Require authentication or fail |
| `principalIfPresent()` | `Context<Principal, Maybe<Principal>>` | Get principal optionally |
| `hasRole(role)` | `Context<Set<String>, Boolean>` | Check role membership |
| `hasAnyRole(roles...)` | `Context<Set<String>, Boolean>` | Check any role matches |
| `hasAllRoles(roles...)` | `Context<Set<String>, Boolean>` | Check all roles match |
| `requireRole(role)` | `Context<Set<String>, Unit>` | Require role or fail |
| `requireAnyRole(roles...)` | `Context<Set<String>, Unit>` | Require any role or fail |
| `requireAllRoles(roles...)` | `Context<Set<String>, Unit>` | Require all roles or fail |
| `hasPermission(perm)` | `Context<Set<String>, Boolean>` | Check permission |
| `requirePermission(perm)` | `Context<Set<String>, Unit>` | Require permission or fail |

~~~admonish info title="Hands-On Learning"
Practice security patterns in [Tutorial 04: Security Patterns](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/context/Tutorial04_SecurityPatterns.java) (6 exercises, ~25 minutes).
~~~

~~~admonish tip title="See Also"
- [Context Effect](../monads/context_scoped.md) -- Core Context documentation
- [RequestContext Patterns](context_request.md) -- Request tracing and metadata
- [Context vs ConfigContext](context_vs_config.md) -- When to use each
- [VTask](../monads/vtask_monad.md) -- Virtual thread effect type
~~~

---

**Previous:** [RequestContext Patterns](context_request.md)
**Next:** [Context vs ConfigContext](context_vs_config.md)
