// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.clientexample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.spring.client.JsonResponseErrorDecoderFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end test of the generated {@link UserClientApi} client against a {@link
 * MockRestServiceServer}: a 2xx response yields {@code Right(UserDto)} and a typed-error response
 * yields {@code Left(ApiError)} decoded from the {@code {"success":false,"error":…}} envelope.
 */
@DisplayName("UserClientApi (generated) end-to-end")
class UserClientApiEndToEndTest {

  private final JsonMapper jsonMapper = JsonMapper.builder().build();
  private MockRestServiceServer server;
  private UserClientApi client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://users.test");
    server = MockRestServiceServer.bindTo(builder).build();
    RestClient restClient = builder.build();
    HttpServiceProxyFactory factory =
        HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    UserClientApiHttpExchange http = factory.createClient(UserClientApiHttpExchange.class);
    client = new UserClientApiClient(http, new JsonResponseErrorDecoderFactory(jsonMapper));
  }

  @Test
  @DisplayName("a 200 response becomes Right(UserDto) with the path variable substituted")
  void success() {
    server
        .expect(requestTo("http://users.test/api/users/1"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"id\":\"1\",\"email\":\"alice@example.com\",\"firstName\":\"Alice\",\"lastName\":\"Smith\"}",
                MediaType.APPLICATION_JSON));

    EitherPath<ApiError, UserDto> path = client.getUser("1");

    assertThatEither(path.run())
        .isRight()
        .hasRight(new UserDto("1", "alice@example.com", "Alice", "Smith"));
    server.verify();
  }

  @Test
  @DisplayName("a 404 envelope response becomes Left(ApiError) with the typed error preserved")
  void typedError() {
    server
        .expect(requestTo("http://users.test/api/users/999"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .body(
                    "{\"success\":false,\"error\":{\"userId\":\"999\",\"message\":\"no user 999\"}}")
                .contentType(MediaType.APPLICATION_JSON));

    EitherPath<ApiError, UserDto> path = client.getUser("999");

    assertThatEither(path.run())
        .isLeft()
        .hasLeftSatisfying(
            error -> {
              assertThat(error.userId()).isEqualTo("999");
              assertThat(error.message()).isEqualTo("no user 999");
            });
    server.verify();
  }

  @Test
  @DisplayName("the deferred VTask variant posts the body and yields Right when run")
  void deferredCreate() {
    server
        .expect(requestTo("http://users.test/api/users"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                "{\"id\":\"7\",\"email\":\"grace@example.com\",\"firstName\":\"Grace\",\"lastName\":\"Hopper\"}",
                MediaType.APPLICATION_JSON));

    Either<ApiError, UserDto> result =
        client.create(new NewUser("grace@example.com", "Grace", "Hopper")).unsafeRun();

    assertThatEither(result)
        .isRight()
        .hasRight(new UserDto("7", "grace@example.com", "Grace", "Hopper"));
    server.verify();
  }
}
