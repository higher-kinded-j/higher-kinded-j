// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.example.config;

import org.higherkindedj.optics.validated.ValidatedPrism;
import org.higherkindedj.spring.example.controller.UserDto;
import org.higherkindedj.spring.example.controller.UserMappingImpl;
import org.higherkindedj.spring.example.domain.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the generated user mapping as an injectable bean: the surface a consumer depends on is
 * the {@link ValidatedPrism} the Impl exposes, not the spec interface (which declares nothing) and
 * not the Impl class (which would pin the dependency to generated code).
 *
 * <p>Spring resolves the full generic type, so {@code ValidatedPrism<UserDto, User>} coexists with
 * codecs for other pairs; only two codecs for the <em>same</em> pair would need a qualifier. A test
 * slice can substitute a fake built with {@code ValidatedPrism.of(...)} - the interface is sealed,
 * so a fake is constructed as a value, never mocked.
 *
 * <p>Injection is optional, not idiomatically required: a generated mapping is a stateless pure
 * function, and calling {@code UserMappingImpl.INSTANCE} directly (as this app's PATCH endpoint
 * does) loses nothing but the substitution seam.
 */
@Configuration
public class MappingConfiguration {

  /**
   * The user wire codec: parse a {@link UserDto} into the domain, or render a {@link User} back.
   *
   * @return the generated mapping's {@link ValidatedPrism} surface
   */
  @Bean
  public ValidatedPrism<UserDto, User> userCodec() {
    return UserMappingImpl.INSTANCE.asValidatedPrism();
  }
}
