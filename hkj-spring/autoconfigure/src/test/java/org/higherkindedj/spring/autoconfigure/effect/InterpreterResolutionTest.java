// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.io.IOKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Tests for {@link InterpreterResolution}: profile eligibility, profile specificity, ambiguity
 * detection, and missing-interpreter failure.
 */
@DisplayName("InterpreterResolution Tests")
class InterpreterResolutionTest {

  interface OrderAlgebra {}

  /** Trivial pass-through transformation so the beans satisfy the Natural requirement. */
  static class PassThrough implements Natural<IOKind.Witness, IOKind.Witness> {
    @Override
    public <A> Kind<IOKind.Witness, A> apply(Kind<IOKind.Witness, A> fa) {
      return fa;
    }
  }

  @Interpreter(OrderAlgebra.class)
  static class ProductionOrderInterpreter extends PassThrough {}

  @Interpreter(value = OrderAlgebra.class, profile = "test")
  static class StubOrderInterpreter extends PassThrough {}

  @Interpreter(OrderAlgebra.class)
  static class RivalOrderInterpreter extends PassThrough {}

  private static AnnotationConfigApplicationContext contextWith(
      String[] activeProfiles, Class<?>... beans) {
    var context = new AnnotationConfigApplicationContext();
    context.getEnvironment().setActiveProfiles(activeProfiles);
    context.register(beans);
    context.refresh();
    return context;
  }

  @Test
  @DisplayName("Selects the unrestricted interpreter when no profile is active")
  void selectsUnrestrictedInterpreterByDefault() {
    try (var context =
        contextWith(new String[0], ProductionOrderInterpreter.class, StubOrderInterpreter.class)) {
      Natural<?, ?> combined =
          InterpreterResolution.resolveAndCombine(
              context, new Class<?>[] {OrderAlgebra.class}, "Test");

      assertThat(combined).isInstanceOf(ProductionOrderInterpreter.class);
    }
  }

  @Test
  @DisplayName("A profile-restricted stub shadows the production interpreter when active")
  void profiledStubShadowsProductionInterpreter() {
    try (var context =
        contextWith(
            new String[] {"test"}, ProductionOrderInterpreter.class, StubOrderInterpreter.class)) {
      Natural<?, ?> combined =
          InterpreterResolution.resolveAndCombine(
              context, new Class<?>[] {OrderAlgebra.class}, "Test");

      assertThat(combined).isInstanceOf(StubOrderInterpreter.class);
    }
  }

  @Test
  @DisplayName("Two unrestricted interpreters for one algebra fail fast as ambiguous")
  void ambiguousUnrestrictedInterpretersFailFast() {
    try (var context =
        contextWith(new String[0], ProductionOrderInterpreter.class, RivalOrderInterpreter.class)) {
      assertThatExceptionOfType(BeanCreationException.class)
          .isThrownBy(
              () ->
                  InterpreterResolution.resolveAndCombine(
                      context, new Class<?>[] {OrderAlgebra.class}, "Test"))
          .withMessageContaining("ambiguous interpreters for OrderAlgebra");
    }
  }

  @Test
  @DisplayName("A missing interpreter fails fast listing the algebra")
  void missingInterpreterFailsFast() {
    try (var context = contextWith(new String[0], StubOrderInterpreter.class)) {
      // The stub's profile is inactive, so no interpreter is eligible
      assertThatExceptionOfType(BeanCreationException.class)
          .isThrownBy(
              () ->
                  InterpreterResolution.resolveAndCombine(
                      context, new Class<?>[] {OrderAlgebra.class}, "Test"))
          .withMessageContaining("no interpreter found")
          .withMessageContaining("OrderAlgebra");
    }
  }
}
