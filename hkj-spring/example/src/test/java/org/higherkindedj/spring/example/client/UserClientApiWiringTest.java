// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Verifies the headline ergonomic path: the generated {@code @ImportHttpServices} group + {@code
 * UserClientApiClientConfiguration} wire the client in a real Spring context, so it is autowired by
 * the user's own interface type, with the base URL taken from {@code
 * spring.http.serviceclient.userClientApi.*} in {@code application.yml}.
 */
@SpringBootTest
@DisplayName("UserClientApi bean wiring")
class UserClientApiWiringTest {

  @Autowired ApplicationContext context;

  @Test
  @DisplayName("the client is registered as a bean of the annotated interface type")
  void clientBeanIsWired() {
    UserClientApi client = context.getBean(UserClientApi.class);

    assertThat(client).isInstanceOf(UserClientApiClient.class);
    assertThat(context.getBean(UserClientApiHttpExchange.class))
        .as("the @ImportHttpServices proxy is registered for the group")
        .isNotNull();
  }
}
