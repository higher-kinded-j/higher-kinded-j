// Fixture for .claude/skills/hkj-spring/reference/http-client-example.md
//
// `UserClientApi` here is a REAL @HkjHttpClient interface, so the client processor generates
// UserClientApiHttpExchange / UserClientApiClient / UserClientApiClientConfiguration for it on the
// gate's processor path. The "Testing the Client" snippet names those generated types, and they are
// the genuine article rather than a stand-in: if the generator's names or the generated client's
// constructor ever change, that snippet stops compiling, which is exactly the point.
//
// The page's own client snippet declares its own UserClientApi, which shadows this one; the
// processor then generates from the snippet's copy. They are deliberately the same shape.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures.

import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.resilience.CircuitBreaker;
import org.higherkindedj.hkt.resilience.RetryPolicy;
import org.higherkindedj.spring.client.HkjHttpClient;
import org.higherkindedj.spring.client.JsonResponseErrorDecoderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;

/** The reader's own DTO and error; a snippet that declares its own shadows these. */
record UserDto(String id, String name) {}

record ApiError(String code, String message) {}

/** What the calling service maps a UserDto into. */
record Profile(String id, String displayName) {}

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

  static final UserClientApi users = null;
  static final UserDto body = new UserDto("42", "Ada");
  static final CircuitBreaker breaker = CircuitBreaker.withDefaults();
  static final JsonMapper jsonMapper = JsonMapper.builder().build();
}
