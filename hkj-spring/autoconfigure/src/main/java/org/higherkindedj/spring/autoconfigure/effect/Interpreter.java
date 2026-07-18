// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure.effect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

/**
 * Marks a class as an effect interpreter for use with {@link EnableEffectBoundary}.
 *
 * <p>This is a Spring {@link Component} meta-annotation, so classes annotated with
 * {@code @Interpreter} are automatically discovered and registered as Spring beans. They can use
 * constructor injection, profile-based switching, and all other Spring bean lifecycle features.
 *
 * <p>The {@link #value()} attribute specifies which effect algebra this interpreter handles. The
 * {@link EnableEffectBoundary} registrar matches interpreters to algebras using this attribute.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * @Interpreter(PaymentGatewayOp.class)
 * public class StripeGatewayInterpreter
 *     extends PaymentGatewayOpInterpreter<IOKind.Witness> {
 *
 *     private final StripeClient client;
 *
 *     public StripeGatewayInterpreter(StripeClient client) {
 *         this.client = client;  // Spring constructor injection
 *     }
 * }
 * }</pre>
 *
 * <h2>Profile-Based Switching</h2>
 *
 * <p>Use the {@link #profile()} attribute to make an interpreter eligible only under a specific
 * Spring profile (any profile expression accepted by {@code Environment.acceptsProfiles}):
 *
 * <pre>{@code
 * @Interpreter(value = PaymentGatewayOp.class, profile = "test")
 * public class StubGatewayInterpreter
 *     extends PaymentGatewayOpInterpreter<IOKind.Witness> { ... }
 * }</pre>
 *
 * <p>Note: the bean itself is still created in every profile (the annotation is not
 * {@code @Profile}); the profile restricts which interpreter the boundary <em>selects</em>. If more
 * than one interpreter is eligible for the same algebra, boundary creation fails fast with the
 * ambiguous candidates listed — selection never depends on classpath scan order.
 *
 * @see EnableEffectBoundary
 * @see InterpreterResolution
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Interpreter {

  /**
   * The effect algebra class that this interpreter handles.
   *
   * @return the effect algebra class
   */
  Class<?> value();

  /**
   * Optional Spring profile expression under which this interpreter is eligible for selection. When
   * empty (default), the interpreter is eligible in all profiles.
   *
   * @return the Spring profile expression, or empty for all profiles
   */
  String profile() default "";
}
