// Fixture for hkj-book/src/spring/declarative_http_clients.md
//
// `UserClientApi` here is a REAL @HkjHttpClient interface, so the client processor generates its
// exchange, client and configuration on the gate's processor path. The page's own client snippet
// declares its own copy, which shadows this one; the processor then generates from that.
//
// The error hierarchy is declared here too, so the snippets that raise and map its variants
// compile; the snippet that shows the hierarchy declares it for itself.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.InputStream;
import java.time.Duration;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.spring.client.HkjClientExchange;
import org.higherkindedj.spring.client.HkjHttpClient;
import org.higherkindedj.spring.client.OnStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import tools.jackson.databind.json.JsonMapper;

/** The reader's own DTO and the envelope error the generated client decodes into. */
record UserDto(String id, String name) {}

record ApiError(String code, String message) {}

record Tick(String symbol, double price) {}

/** What a caller renders a UserDto into. */
record Profile(String id, String displayName) {}

/** The typed errors the page maps statuses onto. */
sealed interface DomainError permits UserNotFoundError, ConflictError, ValidationError {}

record UserNotFoundError(String id) implements DomainError {}

record ConflictError(String id) implements DomainError {}

record ValidationError(String field, String message) implements DomainError {}

/** The client the page declares. */
@HttpExchange("/users")
@HkjHttpClient
interface UserClientApi {

  @GetExchange("/{id}")
  EitherPath<ApiError, UserDto> getUser(@PathVariable String id);

  @PostExchange
  VTaskPath<Either<ApiError, UserDto>> create(@RequestBody UserDto body);
}

class Fixture {

  static final RestClient restClient = RestClient.create();

  static final UserDto body = new UserDto("42", "Ada");

  static final CircuitBreaker breaker = CircuitBreaker.withDefaults();

  static final JsonMapper jsonMapper = JsonMapper.builder().build();

  UserClientApi userClientApi;

  Profile handleError(ApiError error) {
    return new Profile("", error.message());
  }

  Profile renderUser(UserDto user) {
    return new Profile(user.id(), user.name());
  }
}
