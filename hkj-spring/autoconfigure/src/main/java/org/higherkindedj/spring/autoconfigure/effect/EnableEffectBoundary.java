// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure.effect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Enables automatic effect boundary configuration for Spring Boot applications.
 *
 * <p>This annotation auto-discovers {@link Interpreter @Interpreter}-annotated beans, combines them
 * using {@code Interpreters.combine()}, and registers an {@code EffectBoundary} as a singleton
 * bean.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * @SpringBootApplication
 * @EnableEffectBoundary({PaymentGatewayOp.class, FraudCheckOp.class,
 *                        LedgerOp.class, NotificationOp.class})
 * public class PaymentApplication { }
 * }</pre>
 *
 * <h2>What It Does</h2>
 *
 * <ol>
 *   <li>Reads the effect algebra class list from the annotation
 *   <li>Scans for {@code @Interpreter}-annotated beans matching each algebra
 *   <li>Calls {@code Interpreters.combine()} with the discovered interpreters
 *   <li>Registers {@code EffectBoundary<ComposedWitness>} as a singleton bean
 *   <li>Validates at startup: missing interpreter produces a clear error
 * </ol>
 *
 * <p>This replaces manual wiring (e.g., {@code PaymentEffectsWiring.java}, 284 lines) with a single
 * annotation.
 *
 * @see Interpreter
 * @see EffectBoundaryRegistrar
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(EffectBoundaryRegistrar.class)
public @interface EnableEffectBoundary {

  /**
   * The effect algebra classes to compose. The order determines the EitherF nesting: left-to-right
   * = outer-to-inner.
   *
   * @return the effect algebra classes
   */
  Class<?>[] value();
}
