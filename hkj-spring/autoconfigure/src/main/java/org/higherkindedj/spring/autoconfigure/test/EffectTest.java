// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.autoconfigure.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Test annotation for effect boundary testing with a minimal Spring context.
 *
 * <p>When the {@link #effects()} parameter is provided, the annotation auto-discovers
 * {@code @Interpreter} beans for each listed effect algebra, combines them, and registers an {@code
 * EffectBoundary} bean in the test context. This eliminates manual wiring in integration tests.
 *
 * <h2>Usage with effects (auto-wired boundary)</h2>
 *
 * <pre>{@code
 * @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
 * @EffectTest(effects = {OrderOp.class, InventoryOp.class})
 * class OrderServiceTest {
 *
 *   @Autowired EffectBoundary<...> boundary;
 *
 *   @Test
 *   void shouldPlaceOrder() {
 *     OrderResult result = boundary.run(service.placeOrder(request));
 *     assertThat(result.status()).isEqualTo(OrderStatus.CONFIRMED);
 *   }
 * }
 * }</pre>
 *
 * <h2>Usage without effects (manual boundary setup)</h2>
 *
 * <pre>{@code
 * @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
 * @EffectTest
 * class OrderServiceTest {
 *   // Manually create boundary in test — useful for TestBoundary with Id monad
 * }
 * }</pre>
 *
 * <p>For pure unit tests without any Spring context, use {@link
 * org.higherkindedj.hkt.effect.boundary.TestBoundary} directly.
 *
 * @see org.higherkindedj.hkt.effect.boundary.EffectBoundary
 * @see org.higherkindedj.hkt.effect.boundary.TestBoundary
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ImportAutoConfiguration(EffectTestAutoConfiguration.class)
@Import(EffectTestRegistrar.class)
public @interface EffectTest {

  /**
   * The effect algebra classes to auto-wire. When provided, the registrar discovers
   * {@code @Interpreter} beans for each algebra, combines them, and registers an {@code
   * EffectBoundary} bean in the test context.
   *
   * <p>When empty (default), no automatic boundary registration occurs.
   *
   * @return the effect algebra classes
   */
  Class<?>[] effects() default {};
}
