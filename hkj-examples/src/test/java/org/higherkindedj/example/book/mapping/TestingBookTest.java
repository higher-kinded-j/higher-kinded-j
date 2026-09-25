// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.lang.reflect.Modifier;
import org.higherkindedj.optics.validated.ValidatedBuild;
import org.higherkindedj.optics.validated.ValidatedParse;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The proofs of the checkpoint answers on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/testing.html">Injecting, Testing, and
 * Diagnostics</a> page. The page {@code {{#include}}}s the anchored regions below, so the answers
 * it shows are this test, and it is green.
 */
@DisplayName("the Injecting, Testing, and Diagnostics page's checkpoint answers hold")
class TestingBookTest {

  // ANCHOR: two_customer_surfaces
  @Configuration
  static class CustomerSurfaces {
    @Bean
    ValidatedPrism<CustomerDto, Customer> customerCodec() {
      return CustomerMappingImpl.INSTANCE.asValidatedPrism();
    }

    @Bean
    ValidatedParse<CustomerView, Customer> customerViewParser() {
      return CustomerViewMappingImpl.INSTANCE.asValidatedParse();
    }
  }

  record SignupHandler(ValidatedParse<CustomerDto, Customer> parser) {}

  // ANCHOR_END: two_customer_surfaces

  @Test
  @DisplayName("Spring injects the one surface whose full generic type fits")
  void springInjectsBySurfaceAndGenericType() {
    // ANCHOR: check_generic_injection
    try (var context =
        new AnnotationConfigApplicationContext(CustomerSurfaces.class, SignupHandler.class)) {
      SignupHandler handler = context.getBean(SignupHandler.class);

      assertThat(handler.parser()).isSameAs(context.getBean("customerCodec"));
      assertThatValidated(handler.parser().parse(new CustomerDto("Ada", "ada@example.org")))
          .hasValue(new Customer("Ada", new EmailAddress("ada@example.org")));
    }
    // ANCHOR_END: check_generic_injection
  }

  @Test
  @DisplayName("a build-only surface is faked with ValidatedBuild.of, and nothing else will do")
  void aBuildOnlySurfaceIsFakedWithOf() {
    // ANCHOR: check_fake_build
    CustomerRequest fixed = new CustomerRequest();
    ValidatedBuild<CustomerRequest, Customer> requests = ValidatedBuild.of(customer -> fixed);
    assertThat(requests.build(new Customer("Ada", new EmailAddress("ada@example.org"))))
        .isSameAs(fixed);

    assertThatThrownBy(() -> Mockito.mock(ValidatedBuild.class)) // what @MockitoBean calls
        .hasMessageContaining("Sealed interfaces");
    assertThat(ValidatedBuild.class.isAssignableFrom(ValidatedParse.class)).isFalse();
    assertThat(ValidatedBuild.class.isAssignableFrom(CustomerRequestMappingImpl.class)).isFalse();
    assertThat(Modifier.isFinal(CustomerRequestMappingImpl.class.getModifiers())).isTrue();
    // ANCHOR_END: check_fake_build
  }
}
