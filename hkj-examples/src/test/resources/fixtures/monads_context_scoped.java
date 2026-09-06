// Fixture for hkj-book/src/monads/context_scoped.md
//
// The page threads a trace id, a locale and a tenant through one request, first by hand and then
// through Context. The request domain, the services it calls and the ScopedValue keys the later
// snippets read are declared here; a snippet that declares its own copy of a key holder shadows
// this one.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.context.RequestContext;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.Scope;
import org.higherkindedj.hkt.vtask.VTask;
import org.junit.jupiter.api.Test;

record Request(String traceId, Locale locale) {}

record Response(String body) {}

record ValidatedRequest(String traceId) {}

record ProcessedData(String value) {}

record RequestInfo(String traceId, Locale locale, String tenant) {

  RequestInfo(String traceId, Locale locale) {
    this(traceId, locale, "default");
  }
}

record TenantConfig(String name) {}

record DatabaseConnection(String url) {}

record Config(String hostname) {}

record UserProfile(String userId) {}

record Orders(List<String> ids) {}

record Preferences(Locale locale) {}

record AggregatedResult(UserProfile profile, Orders orders, Preferences preferences) {}

record Result(String value) {}

record OrderRequest(String id, String traceId, String userId) {}

record Order(String id) {}

record OrderResult(PartialResult inventory, PartialResult shipping, PartialResult payment) {}

record PartialResult(String value) {}

final class Processor {

  ProcessedData process(String traceId) {
    return new ProcessedData(traceId);
  }
}

final class ProfileService {

  UserProfile fetch(String userId) {
    return new UserProfile(userId);
  }
}

final class SimpleLogger {

  void info(String message, Object... args) {}
}

class AppContext {

  static final ScopedValue<Locale> LOCALE = ScopedValue.newInstance();

  static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

  static final ScopedValue<String> TENANT_ID = ScopedValue.newInstance();
}

class LogContext {

  static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

  static final ScopedValue<String> SPAN_ID = ScopedValue.newInstance();

  static final ScopedValue<String> USER_ID = ScopedValue.newInstance();
}

class Fixture {

  static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

  static final ScopedValue<Locale> LOCALE = ScopedValue.newInstance();

  static final ScopedValue<Config> CONFIG = ScopedValue.newInstance();

  static final Map<String, TenantConfig> TENANT_CONFIGS =
      Map.of("acme-corp", new TenantConfig("acme"));

  static final Config productionConfig = new Config("db.example.com");

  static final Processor processor = new Processor();

  static final ProfileService profileService = new ProfileService();

  static final SimpleLogger logger = new SimpleLogger();

  static Response processRequest(Request request) {
    return new Response("ok");
  }

  static Context<String, String> processRequest() {
    return Context.ask(RequestContext.TRACE_ID);
  }

  static ValidatedRequest validateRequest(Request request) {
    return new ValidatedRequest(request.traceId());
  }

  static VTask<Response> formatResponse(ProcessedData data) {
    return VTask.succeed(new Response(data.value()));
  }

  static ProcessedData doProcess(ValidatedRequest request) {
    return new ProcessedData(request.traceId());
  }

  static String buildConnectionString(String tenantId) {
    return "jdbc:postgresql://db/" + tenantId + "_database";
  }

  static DatabaseConnection openConnection(String connectionString) {
    return new DatabaseConnection(connectionString);
  }

  static VTask<Orders> fetchUserOrders(String userId) {
    return VTask.succeed(new Orders(List.of("o-1")));
  }

  static VTask<Preferences> fetchUserPreferences(String userId) {
    return VTask.succeed(new Preferences(Locale.UK));
  }

  static VTask<Result> normalTask() {
    return VTask.succeed(new Result("normal"));
  }

  static VTask<Result> specialTask() {
    return VTask.succeed(new Result("special"));
  }

  static Result combine(Result first, Result second) {
    return new Result(first.value() + "+" + second.value());
  }

  static VTask<PartialResult> validateInventory(OrderRequest request) {
    return VTask.succeed(new PartialResult("inventory"));
  }

  static VTask<PartialResult> calculateShipping(OrderRequest request) {
    return VTask.succeed(new PartialResult("shipping"));
  }

  static VTask<PartialResult> processPayment(OrderRequest request) {
    return VTask.succeed(new PartialResult("payment"));
  }
}
