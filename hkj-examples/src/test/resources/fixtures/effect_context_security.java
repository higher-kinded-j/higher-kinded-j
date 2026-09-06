// Fixture for hkj-book/src/effect/context_security.md
//
// The page carries one authenticated principal through roles, permissions, downstream calls and
// audit logging. Every snippet that declares a class is a sibling of Fixture rather than a
// subclass, so the whole cast is declared at top level here.
//
// `SecurityContext` is declared in full: several snippets show one method at a time on it, and
// each of those shadows this copy for its own compilation unit.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.slf4j.Logger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

record HttpRequest(String traceId, Locale locale, String path, String method) {}

record Request(String path) {}

/** The principal the testing section builds. */
record UserPrincipal(String id, String name, String email, Instant createdAt)
    implements Principal {

  @Override
  public String getName() {
    return name;
  }
}

record Response(int status) {

  static Response ok(Object body) {
    return new Response(200);
  }

  static Response forbidden(String reason) {
    return new Response(403);
  }

  static Response unauthorized(String reason) {
    return new Response(401);
  }

  static Response serverError() {
    return new Response(500);
  }
}

record UserProfile(String handle) {}

record DashboardData(String body) {}

enum MenuItem {
  HOME,
  PROFILE,
  REPORTS,
  SETTINGS
}

record Report(String body) {}

record ReportRequest(String id) {}

record AuditLog(String entries) {}

record DocumentUpdate(String body) {}

record Document(String id, String ownerId, Set<String> sharedWith, boolean isPublic) {}

record Order(String id, String paymentMethodId) {}

record ValidationResult(boolean ok, String detail) {

  static ValidationResult success() {
    return new ValidationResult(true, "");
  }

  static ValidationResult failure(String detail) {
    return new ValidationResult(false, detail);
  }
}

record OrderValidation(int checks) {

  static OrderValidation combine(List<ValidationResult> results) {
    return new OrderValidation(results.size());
  }
}

record AuthResult(
    Principal principal, Set<String> roles, Set<String> permissions, String token,
    String sessionId) {

  static AuthResult anonymous(String sessionId) {
    return new AuthResult(null, Set.of(), Set.of(), null, sessionId);
  }
}

abstract sealed class SecurityException extends RuntimeException
    permits UnauthenticatedException, UnauthorisedException, ForbiddenException {

  SecurityException(String message) {
    super(message);
  }

  SecurityException(String message, Throwable cause) {
    super(message, cause);
  }
}

final class UnauthenticatedException extends SecurityException {

  UnauthenticatedException(String message) {
    super(message);
  }
}

final class UnauthorisedException extends SecurityException {

  UnauthorisedException(String message) {
    super(message);
  }
}

final class ForbiddenException extends SecurityException {

  ForbiddenException(String message) {
    super(message);
  }
}

final class RequestContext {

  static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

  static final ScopedValue<Locale> LOCALE = ScopedValue.newInstance();

  static final ScopedValue<Instant> REQUEST_TIME = ScopedValue.newInstance();
}

final class SecurityContext {

  static final ScopedValue<Principal> PRINCIPAL = ScopedValue.newInstance();

  static final ScopedValue<Set<String>> ROLES = ScopedValue.newInstance();

  static final ScopedValue<Set<String>> PERMISSIONS = ScopedValue.newInstance();

  static final ScopedValue<String> AUTH_TOKEN = ScopedValue.newInstance();

  static final ScopedValue<String> SESSION_ID = ScopedValue.newInstance();

  static Context<Principal, Boolean> isAuthenticated() {
    return Context.asks(PRINCIPAL, principal -> principal != null);
  }

  static Context<Principal, Principal> requireAuthenticated() {
    return Context.<Principal>ask(PRINCIPAL)
        .flatMap(
            principal ->
                principal != null
                    ? Context.<Principal, Principal>succeed(principal)
                    : Context.<Principal, Principal>fail(
                        new UnauthenticatedException("Authentication required")));
  }

  static Context<Principal, Maybe<Principal>> principalIfPresent() {
    return Context.asks(
        PRINCIPAL, principal -> principal != null ? Maybe.just(principal) : Maybe.nothing());
  }

  static Context<Set<String>, Boolean> hasRole(String role) {
    return Context.asks(ROLES, roles -> roles.contains(role));
  }

  static Context<Set<String>, Boolean> hasAnyRole(String... roles) {
    Set<String> required = Set.of(roles);
    return Context.asks(ROLES, userRoles -> userRoles.stream().anyMatch(required::contains));
  }

  static Context<Set<String>, Unit> requireRole(String role) {
    return hasRole(role)
        .flatMap(
            has ->
                has
                    ? Context.<Set<String>, Unit>succeed(Unit.INSTANCE)
                    : Context.<Set<String>, Unit>fail(
                        new UnauthorisedException("Role required: " + role)));
  }

  static Context<Set<String>, Unit> requireAnyRole(String... roles) {
    return hasAnyRole(roles)
        .flatMap(
            has ->
                has
                    ? Context.<Set<String>, Unit>succeed(Unit.INSTANCE)
                    : Context.<Set<String>, Unit>fail(
                        new UnauthorisedException("One of these roles required")));
  }

  static Context<Set<String>, Boolean> hasPermission(String permission) {
    return Context.asks(PERMISSIONS, perms -> perms.contains(permission));
  }

  static Context<Set<String>, Unit> requirePermission(String permission) {
    return hasPermission(permission)
        .flatMap(
            has ->
                has
                    ? Context.<Set<String>, Unit>succeed(Unit.INSTANCE)
                    : Context.<Set<String>, Unit>fail(
                        new UnauthorisedException("Permission required: " + permission)));
  }
}

class ContextLogger {

  private final Logger delegate;

  ContextLogger(Class<?> clazz) {
    this.delegate = LoggerFactory.getLogger(clazz);
  }

  void info(String message, Object... args) {
    delegate.info(message, args);
  }

  void warn(String message, Object... args) {
    delegate.warn(message, args);
  }

  void error(String message, Object... args) {
    delegate.error(message, args);
  }
}

final class SecurityAuditLogger {

  void logAccess(String resource, String action) {}

  void logAccessDenied(String resource, String action, String reason) {}
}

final class AuthenticationService {

  AuthResult authenticate(HttpServletRequest request) {
    return AuthResult.anonymous("s-1");
  }
}

final class ProfileService {

  UserProfile getProfile(String name) {
    return new UserProfile(name);
  }
}

final class ReportService {

  Report generate(ReportRequest request) {
    return new Report("report");
  }
}

final class UserAdminService {

  Void delete(String userId) {
    return null;
  }
}

final class AuditService {

  AuditLog getLog(String resourceId) {
    return new AuditLog("entries");
  }
}

final class DocumentService {

  Document get(String id) {
    return new Document(id, "owner", Set.of(), true);
  }

  Void update(String id, DocumentUpdate update) {
    return null;
  }

  Void delete(String id) {
    return null;
  }
}

final class DocumentRepository {

  Document findById(String id) {
    return new Document(id, "owner", Set.of(), true);
  }
}

final class DocumentAuthorisation {

  Context<Principal, Document> requireAccess(Document document) {
    return Context.succeed(document);
  }
}

final class PaymentService {

  boolean belongsTo(String paymentMethodId, String owner) {
    return true;
  }
}

class Fixture {

  static final ContextLogger log = new ContextLogger(Fixture.class);

  static final ProfileService profileService = new ProfileService();

  static final ReportService reportService = new ReportService();

  static final UserAdminService userService = new UserAdminService();

  static final AuditService auditService = new AuditService();

  static final DocumentService documentService = new DocumentService();

  static final DocumentRepository documentRepository = new DocumentRepository();

  static final DocumentAuthorisation documentAuth = new DocumentAuthorisation();

  static final PaymentService paymentService = new PaymentService();

  static DashboardData getAdminDashboard() {
    return new DashboardData("admin");
  }

  static DashboardData getUserDashboard() {
    return new DashboardData("user");
  }

  static VTask<Response> handleAuthenticatedRequest(Request request) {
    return VTask.succeed(new Response(200));
  }

  static VTask<Response> handleAnonymousRequest(Request request) {
    return VTask.succeed(new Response(401));
  }

  static VTask<ValidationResult> validateInventory(Order order) {
    return VTask.succeed(ValidationResult.success());
  }

  static VTask<ValidationResult> validateShippingAddress(Order order) {
    return VTask.succeed(ValidationResult.success());
  }

  static AuthResult authenticate(HttpRequest request) {
    return AuthResult.anonymous("s-1");
  }

  static <T> T parseResponse(java.net.http.HttpResponse<String> response, Class<T> type) {
    return null;
  }
}
