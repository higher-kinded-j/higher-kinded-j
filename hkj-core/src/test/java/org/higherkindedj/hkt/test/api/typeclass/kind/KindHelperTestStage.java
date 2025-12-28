// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.typeclass.kind;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern;
import org.higherkindedj.hkt.test.patterns.KindHelperTestPattern.KindHelper;

/**
 * Generic KindHelper test stage for custom type implementations.
 *
 * <p>Use this for testing KindHelper implementations for your own types. For built-in types
 * (Either, Maybe, IO), use the specific test methods in {@link
 * org.higherkindedj.hkt.test.api.CoreTypeTest}.
 *
 * <h2>Usage Example:</h2>
 *
 * <pre>{@code
 * TypeClassTest.kindHelper()
 *     .forType(MyType.class, myInstance)
 *     .withHelper(MY_HELPER::widen, MY_HELPER::narrow)
 *     .test();
 * }</pre>
 */
public final class KindHelperTestStage {

  private KindHelperTestStage() {
    throw new AssertionError("KindHelperTestStage is a utility class");
  }

  /**
   * Creates a new KindHelper test builder for custom types.
   *
   * @return A builder for configuring KindHelper tests
   */
  public static KindHelperBuilder builder() {
    return new KindHelperBuilder();
  }

  /** Builder for custom KindHelper testing. */
  public static final class KindHelperBuilder {

    private KindHelperBuilder() {}

    /**
     * Test KindHelper for a custom type with explicit configuration.
     *
     * @param targetClass The target type class
     * @param instance The instance to test
     * @param <T> The target type
     * @return Configuration stage for custom KindHelper
     */
    public <T> CustomKindHelperConfig<T> forType(Class<T> targetClass, T instance) {
      return new CustomKindHelperConfig<>(targetClass, instance);
    }
  }

  /** Configuration for custom KindHelper testing. */
  public static final class CustomKindHelperConfig<T> {
    private final Class<T> targetClass;
    private final T instance;

    private CustomKindHelperConfig(Class<T> targetClass, T instance) {
      this.targetClass = targetClass;
      this.instance = instance;
    }

    /**
     * Provides widen/narrow functions for the custom type.
     *
     * @param widenFunc Function to widen T to Kind
     * @param narrowFunc Function to narrow Kind to T
     * @param <F> The witness type
     * @param <A> The value type
     * @return Configuration stage for testing
     */
    public <F extends WitnessArity<TypeArity.Unary>, A> BaseKindHelperConfig<T, F, A> withHelper(
        Function<T, Kind<F, A>> widenFunc, Function<Kind<F, A>, T> narrowFunc) {
      return new BaseKindHelperConfig<>(instance, targetClass, widenFunc, narrowFunc);
    }
  }

  /** Base configuration with fluent API. */
  public static class BaseKindHelperConfig<T, F extends WitnessArity<TypeArity.Unary>, A> {
    protected final T instance;
    protected final Class<T> targetClass;
    protected final Function<T, Kind<F, A>> widenFunc;
    protected final Function<Kind<F, A>, T> narrowFunc;

    // Test selection flags
    private boolean includeRoundTrip = true;
    private boolean includeValidations = true;
    private boolean includeInvalidType = true;
    private boolean includeIdempotency = true;
    private boolean includeEdgeCases = true;
    private boolean includePerformance = false;
    private boolean includeConcurrency = false;

    protected BaseKindHelperConfig(
        T instance,
        Class<T> targetClass,
        Function<T, Kind<F, A>> widenFunc,
        Function<Kind<F, A>, T> narrowFunc) {
      this.instance = instance;
      this.targetClass = targetClass;
      this.widenFunc = widenFunc;
      this.narrowFunc = narrowFunc;
    }

    @SuppressWarnings("unchecked")
    protected <SELF extends BaseKindHelperConfig<T, F, A>> SELF self() {
      return (SELF) this;
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipRoundTrip() {
      this.includeRoundTrip = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipValidations() {
      this.includeValidations = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipInvalidType() {
      this.includeInvalidType = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipIdempotency() {
      this.includeIdempotency = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipEdgeCases() {
      this.includeEdgeCases = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF skipPerformance() {
      this.includePerformance = false;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF withPerformanceTests() {
      this.includePerformance = true;
      return self();
    }

    public <SELF extends BaseKindHelperConfig<T, F, A>> SELF withConcurrencyTests() {
      this.includeConcurrency = true;
      return self();
    }

    /** Executes all configured tests. */
    public void test() {
      KindHelper<T, F, A> helper = KindHelperTestPattern.adapt(widenFunc, narrowFunc);

      if (includeRoundTrip) {
        KindHelperTestPattern.testRoundTripWithHelper(instance, helper);
      }
      if (includeValidations) {
        KindHelperTestPattern.testValidationsWithHelper(targetClass, helper);
      }
      if (includeInvalidType) {
        KindHelperTestPattern.testInvalidTypeWithHelper(targetClass, helper);
      }
      if (includeIdempotency) {
        KindHelperTestPattern.testIdempotencyWithHelper(instance, helper);
      }
      if (includeEdgeCases) {
        KindHelperTestPattern.testEdgeCasesWithHelper(instance, targetClass, helper);
      }
      if (includePerformance) {
        KindHelperTestPattern.testPerformance(instance, widenFunc, narrowFunc);
      }
      if (includeConcurrency) {
        KindHelperTestPattern.testConcurrentAccess(instance, widenFunc, narrowFunc);
      }
    }

    /** Executes all tests including implementation standards validation. */
    public void testWithValidation(Class<?> helperClass) {
      test();
      KindHelperTestPattern.validateImplementationStandards(targetClass, helperClass);
    }
  }
}
