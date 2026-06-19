// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.client;

import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.higherkindedj.hkt.effect.EitherPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves that {@code spring.http.serviceclient.userClientApi.base-url} is actually applied to the
 * generated client's proxy. A real call is made through the Boot-wired client to a JDK stub HTTP
 * server bound to the configured base URL. This fails if {@code spring-boot-restclient} (which
 * binds the {@code spring.http.serviceclient.*} properties) is absent, where the bean-existence
 * wiring test would still pass.
 */
@SpringBootTest
@DisplayName("UserClientApi base-url wiring (end-to-end)")
class UserClientApiBaseUrlTest {

  private static HttpServer stub;

  @Autowired UserClientApi userClientApi;

  @DynamicPropertySource
  static void baseUrl(DynamicPropertyRegistry registry) throws IOException {
    stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    stub.createContext(
        "/users/1",
        exchange -> {
          byte[] body = "{\"id\":\"1\",\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    stub.start();
    registry.add(
        "spring.http.serviceclient.userClientApi.base-url",
        () -> "http://127.0.0.1:" + stub.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    if (stub != null) {
      stub.stop(0);
    }
  }

  @Test
  @DisplayName("the autowired client resolves requests against the configured base URL")
  void resolvesAgainstConfiguredBaseUrl() {
    EitherPath<ApiError, UserDto> path = userClientApi.getUser("1");

    assertThatEither(path.run()).isRight().hasRight(new UserDto("1", "Ada"));
  }
}
