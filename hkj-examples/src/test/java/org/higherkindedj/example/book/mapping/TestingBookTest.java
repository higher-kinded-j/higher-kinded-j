// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Function;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.validated.ValidatedParse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

  // ANCHOR: render_configuration
  @Configuration
  static class RenderingConfiguration {
    @Bean
    Function<Customer, CustomerDto> renderCustomer() {
      return CustomerMappingImpl.INSTANCE::build; // the one method the renderer calls
    }
  }

  record ResponseRenderer(Function<Customer, CustomerDto> render) {}

  // ANCHOR_END: render_configuration

  @Test
  @DisplayName("a renderer is wired with build alone, and the spec is never a bean")
  void aRendererIsWiredWithBuildAlone() {
    // ANCHOR: check_register_build
    try (var context =
        new AnnotationConfigApplicationContext(
            RenderingConfiguration.class, ResponseRenderer.class)) {
      ResponseRenderer renderer = context.getBean(ResponseRenderer.class);
      assertThat(renderer.render().apply(new Customer("Ada", new EmailAddress("ada@example.org"))))
          .isEqualTo(new CustomerDto("Ada", "ada@example.org"));

      assertThat(context.getBeanNamesForType(CustomerMapping.class)).isEmpty(); // no bean
    }
    assertThat(CustomerMappingImpl.class.getMethods())
        .extracting(Method::getName)
        .doesNotContain("asIso"); // its leaf withholds the iso
    // ANCHOR_END: check_register_build
  }

  @Test
  @DisplayName("a parse-only surface is faked with ValidatedParse.of, and nothing else will do")
  void aParseOnlySurfaceIsFakedWithOf() {
    // ANCHOR: check_fake_parse
    ValidatedParse<CustomerView, Customer> rejecting =
        ValidatedParse.of(view -> Validated.invalidNel(FieldError.of("rejected").at("email")));

    assertThatValidated(rejecting.parse(new CustomerView("Ada", "ada@corp.example")))
        .hasFieldErrors("email: rejected");
    assertThat(ValidatedParse.class.isSealed()).isTrue(); // no mock, no anonymous class
    assertThat(Modifier.isFinal(CustomerViewMappingImpl.class.getModifiers()))
        .isTrue(); // no subclass
    // ANCHOR_END: check_fake_parse
  }
}
