// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.patterns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.WitnessArity;

/**
 * Flexible validation configuration for type class testing.
 *
 * <p>This allows tests to specify exactly which validation behaviour to expect for each operation,
 * accommodating inheritance hierarchies where methods may use different validation contexts.
 */
public final class FlexibleValidationConfig {

  private FlexibleValidationConfig() {}

  /** Configuration for Functor validation expectations. */
  public static class FunctorValidation<F extends WitnessArity<TypeArity.Unary>, A, B> {
    protected final Functor<F> functor;
    protected final Kind<F, A> validKind;
    protected final Function<A, B> validMapper;
    protected final BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker;

    private boolean mapHasClassContext = false;
    private Class<?> mapContextClass = null;
    private String mapFunctionName = "f";

    public FunctorValidation(
        Functor<F> functor,
        Kind<F, A> validKind,
        Function<A, B> validMapper,
        BiPredicate<Kind<F, ?>, Kind<F, ?>> equalityChecker) {
      this.functor = functor;
      this.validKind = validKind;
      this.validMapper = validMapper;
      this.equalityChecker = equalityChecker;
    }

    /** Specify that map() operations do not include class context in error messages. */
    public FunctorValidation<F, A, B> mapWithoutClassContext() {
      this.mapHasClassContext = false;
      this.mapContextClass = null;
      return this;
    }

    /** Specify that map() operations include the given class in error messages. */
    public FunctorValidation<F, A, B> mapWithClassContext(Class<?> contextClass) {
      this.mapHasClassContext = true;
      this.mapContextClass = contextClass;
      return this;
    }

    /** Specify the function parameter name used in map() error messages. */
    public FunctorValidation<F, A, B> mapWithFunctionName(String functionName) {
      this.mapFunctionName = functionName;
      return this;
    }

    /** Execute validation tests with the configured expectations. */
    public void test() {
      testMapValidation();
    }

    private void testMapValidation() {
      if (mapHasClassContext && mapContextClass != null) {
        String className = mapContextClass.getSimpleName();
        assertThatThrownBy(() -> functor.map(null, validKind))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining(
                "function " + mapFunctionName + " for " + className + ".map cannot be null");

        assertThatThrownBy(() -> functor.map(validMapper, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Kind for " + className + ".map cannot be null");
      } else {
        assertThatThrownBy(() -> functor.map(null, validKind))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("function " + mapFunctionName + " for map cannot be null");

        assertThatThrownBy(() -> functor.map(validMapper, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Kind for map cannot be null");
      }
    }
  }

  /** Configuration for Applicative validation expectations. */
  public static class ApplicativeValidation<F extends WitnessArity<TypeArity.Unary>, A, B> {
    protected final Applicative<F> applicative;
    protected final Kind<F, A> validKind;
    protected final Kind<F, A> validKind2;
    protected final Function<A, B> validMapper;
    protected final Kind<F, Function<A, B>> validFunctionKind;
    protected final BiFunction<A, A, B> validCombiningFunction;

    // Validation expectations
    private boolean mapHasClassContext = false;
    private Class<?> mapContextClass = null;
    private String mapFunctionName = "f";

    private boolean apHasClassContext = false;
    private Class<?> apContextClass = null;

    private boolean map2HasClassContext = false;
    private Class<?> map2ContextClass = null;

    public ApplicativeValidation(
        Applicative<F> applicative,
        Kind<F, A> validKind,
        Kind<F, A> validKind2,
        Function<A, B> validMapper,
        Kind<F, Function<A, B>> validFunctionKind,
        BiFunction<A, A, B> validCombiningFunction) {
      this.applicative = applicative;
      this.validKind = validKind;
      this.validKind2 = validKind2;
      this.validMapper = validMapper;
      this.validFunctionKind = validFunctionKind;
      this.validCombiningFunction = validCombiningFunction;
    }

    /** Specify that map() operations do not include class context in error messages. */
    public ApplicativeValidation<F, A, B> mapWithoutClassContext() {
      this.mapHasClassContext = false;
      this.mapContextClass = null;
      return this;
    }

    /** Specify that map() operations include the given class in error messages. */
    public ApplicativeValidation<F, A, B> mapWithClassContext(Class<?> contextClass) {
      this.mapHasClassContext = true;
      this.mapContextClass = contextClass;
      return this;
    }

    /** Specify the function parameter name used in map() error messages. */
    public ApplicativeValidation<F, A, B> mapWithFunctionName(String functionName) {
      this.mapFunctionName = functionName;
      return this;
    }

    /** Specify that ap() operations include the given class in error messages. */
    public ApplicativeValidation<F, A, B> apWithClassContext(Class<?> contextClass) {
      this.apHasClassContext = true;
      this.apContextClass = contextClass;
      return this;
    }

    /** Specify that map2() operations include the given class in error messages. */
    public ApplicativeValidation<F, A, B> map2WithClassContext(Class<?> contextClass) {
      this.map2HasClassContext = true;
      this.map2ContextClass = contextClass;
      return this;
    }

    /** Execute validation tests with the configured expectations. */
    public void test() {
      // Only test configured operations
      if (mapContextClass != null) {
        testMapValidation();
      }
      if (apContextClass != null) {
        testApValidation();
      }
      if (map2ContextClass != null) {
        testMap2Validation();
      }
    }

    private void testMapValidation() {
      if (mapHasClassContext && mapContextClass != null) {
        String className = mapContextClass.getSimpleName();
        assertThatThrownBy(() -> applicative.map(null, validKind))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining(
                "Function " + mapFunctionName + " for " + className + ".map cannot be null");

        assertThatThrownBy(() -> applicative.map(validMapper, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Kind for " + className + ".map cannot be null");
      } else {
        assertThatThrownBy(() -> applicative.map(null, validKind))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Function " + mapFunctionName + " for map cannot be null");

        assertThatThrownBy(() -> applicative.map(validMapper, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Kind for map cannot be null");
      }
    }

    private void testApValidation() {
      if (apHasClassContext && apContextClass != null) {
        String className = apContextClass.getSimpleName();
        assertThatThrownBy(() -> applicative.ap(null, validKind))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Kind for " + className + ".ap")
            .hasMessageContaining("function");

        assertThatThrownBy(() -> applicative.ap(validFunctionKind, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Kind for " + className + ".ap")
            .hasMessageContaining("argument");
      } else {
        assertThatThrownBy(() -> applicative.ap(null, validKind))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Kind for ap");

        assertThatThrownBy(() -> applicative.ap(validFunctionKind, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Kind for ap");
      }
    }

    private void testMap2Validation() {
      BiFunction<A, A, B> nullBiFunction = null;

      if (map2HasClassContext && map2ContextClass != null) {
        String className = map2ContextClass.getSimpleName();

        // Special handling for Applicative interface default implementation
        if (map2ContextClass == Applicative.class) {
          // Applicative's default map2 uses generic error messages without class context
          assertThatThrownBy(() -> applicative.map2(null, validKind2, validCombiningFunction))
              .isInstanceOf(NullPointerException.class)
              .hasMessageContaining("Kind<F, A> fa for map2 cannot be null");

          assertThatThrownBy(() -> applicative.map2(validKind, null, validCombiningFunction))
              .isInstanceOf(NullPointerException.class)
              .hasMessageContaining("Kind<F, B> fb for map2 cannot be null");

          assertThatThrownBy(() -> applicative.map2(validKind, validKind2, nullBiFunction))
              .isInstanceOf(NullPointerException.class)
              .hasMessageContaining("combining function")
              .hasMessageContaining("map2")
              .hasMessageContaining("cannot be null");
        } else {
          // Regular class implementation with full class context
          assertThatThrownBy(() -> applicative.map2(null, validKind2, validCombiningFunction))
              .isInstanceOf(NullPointerException.class)
              .hasMessageContaining("Kind for " + className + ".map2")
              .hasMessageContaining("first");

          assertThatThrownBy(() -> applicative.map2(validKind, null, validCombiningFunction))
              .isInstanceOf(NullPointerException.class)
              .hasMessageContaining("Kind for " + className + ".map2")
              .hasMessageContaining("second");

          assertThatThrownBy(() -> applicative.map2(validKind, validKind2, nullBiFunction))
              .isInstanceOf(NullPointerException.class)
              .hasMessageContaining("combining function")
              .hasMessageContaining(className + ".map2");
        }
      } else {
        // No class context - generic validation
        assertThatThrownBy(() -> applicative.map2(null, validKind2, validCombiningFunction))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("cannot be null");

        assertThatThrownBy(() -> applicative.map2(validKind, null, validCombiningFunction))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("cannot be null");

        assertThatThrownBy(() -> applicative.map2(validKind, validKind2, nullBiFunction))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("combining function")
            .hasMessageContaining("map2");
      }
    }
  }

  /** Configuration for Monad validation expectations. */
  public static class MonadValidation<F extends WitnessArity<TypeArity.Unary>, A, B>
      extends ApplicativeValidation<F, A, B> {
    protected final Monad<F> monad;
    protected final Function<A, Kind<F, B>> validFlatMapper;

    private boolean flatMapHasClassContext = false;
    private Class<?> flatMapContextClass = null;
    private String flatMapFunctionName = "f";

    public MonadValidation(
        Monad<F> monad,
        Kind<F, A> validKind,
        Kind<F, A> validKind2,
        Function<A, B> validMapper,
        Kind<F, Function<A, B>> validFunctionKind,
        BiFunction<A, A, B> validCombiningFunction,
        Function<A, Kind<F, B>> validFlatMapper) {
      super(monad, validKind, validKind2, validMapper, validFunctionKind, validCombiningFunction);
      this.monad = monad;
      this.validFlatMapper = validFlatMapper;
    }

    @Override
    public MonadValidation<F, A, B> mapWithoutClassContext() {
      super.mapWithoutClassContext();
      return this;
    }

    @Override
    public MonadValidation<F, A, B> mapWithClassContext(Class<?> contextClass) {
      super.mapWithClassContext(contextClass);
      return this;
    }

    @Override
    public MonadValidation<F, A, B> mapWithFunctionName(String functionName) {
      super.mapWithFunctionName(functionName);
      return this;
    }

    @Override
    public MonadValidation<F, A, B> apWithClassContext(Class<?> contextClass) {
      super.apWithClassContext(contextClass);
      return this;
    }

    @Override
    public MonadValidation<F, A, B> map2WithClassContext(Class<?> contextClass) {
      super.map2WithClassContext(contextClass);
      return this;
    }

    public MonadValidation<F, A, B> flatMapWithClassContext(Class<?> contextClass) {
      this.flatMapHasClassContext = true;
      this.flatMapContextClass = contextClass;
      return this;
    }

    public MonadValidation<F, A, B> flatMapWithoutClassContext() {
      this.flatMapHasClassContext = false;
      this.flatMapContextClass = null;
      return this;
    }

    @Override
    public void test() {
      // Test inherited Applicative operations (map, ap, map2) if configured
      super.test();

      // Test Monad-specific flatMap operation if configured
      if (flatMapContextClass != null) {
        testFlatMapValidation();
      }
    }

    private void testFlatMapValidation() {
      if (flatMapHasClassContext && flatMapContextClass != null) {
        String className = flatMapContextClass.getSimpleName();
        assertThatThrownBy(() -> monad.flatMap(null, validKind))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining(
                "Function "
                    + flatMapFunctionName
                    + " for "
                    + className
                    + ".flatMap cannot be null");

        assertThatThrownBy(() -> monad.flatMap(validFlatMapper, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Kind for " + className + ".flatMap cannot be null");
      } else {
        assertThatThrownBy(() -> monad.flatMap(null, validKind))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining(
                "Function " + flatMapFunctionName + " for flatMap cannot be null");

        assertThatThrownBy(() -> monad.flatMap(validFlatMapper, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("Kind for flatMap cannot be null");
      }
    }
  }

  /** Configuration for MonadError validation expectations. */
  public static class MonadErrorValidation<F extends WitnessArity<TypeArity.Unary>, E, A, B>
      extends MonadValidation<F, A, B> {
    private final MonadError<F, E> monadError;
    private final Function<E, Kind<F, A>> validHandler;
    private final Kind<F, A> validFallback;

    private boolean handleErrorWithHasClassContext = false;
    private Class<?> handleErrorWithContextClass = null;

    public MonadErrorValidation(
        MonadError<F, E> monadError,
        Kind<F, A> validKind,
        Kind<F, A> validKind2,
        Function<A, B> validMapper,
        Kind<F, Function<A, B>> validFunctionKind,
        BiFunction<A, A, B> validCombiningFunction,
        Function<A, Kind<F, B>> validFlatMapper,
        Function<E, Kind<F, A>> validHandler,
        Kind<F, A> validFallback) {
      super(
          monadError,
          validKind,
          validKind2,
          validMapper,
          validFunctionKind,
          validCombiningFunction,
          validFlatMapper);
      this.monadError = monadError;
      this.validHandler = validHandler;
      this.validFallback = validFallback;
    }

    @Override
    public MonadErrorValidation<F, E, A, B> mapWithoutClassContext() {
      super.mapWithoutClassContext();
      return this;
    }

    @Override
    public MonadErrorValidation<F, E, A, B> mapWithClassContext(Class<?> contextClass) {
      super.mapWithClassContext(contextClass);
      return this;
    }

    @Override
    public MonadErrorValidation<F, E, A, B> mapWithFunctionName(String functionName) {
      super.mapWithFunctionName(functionName);
      return this;
    }

    @Override
    public MonadErrorValidation<F, E, A, B> apWithClassContext(Class<?> contextClass) {
      super.apWithClassContext(contextClass);
      return this;
    }

    @Override
    public MonadErrorValidation<F, E, A, B> map2WithClassContext(Class<?> contextClass) {
      super.map2WithClassContext(contextClass);
      return this;
    }

    @Override
    public MonadErrorValidation<F, E, A, B> flatMapWithClassContext(Class<?> contextClass) {
      super.flatMapWithClassContext(contextClass);
      return this;
    }

    @Override
    public MonadErrorValidation<F, E, A, B> flatMapWithoutClassContext() {
      super.flatMapWithoutClassContext();
      return this;
    }

    public MonadErrorValidation<F, E, A, B> handleErrorWithClassContext(Class<?> contextClass) {
      this.handleErrorWithHasClassContext = true;
      this.handleErrorWithContextClass = contextClass;
      return this;
    }

    @Override
    public void test() {
      super.test();
      testHandleErrorWithValidation();
      testRecoverWithValidation();
    }

    private void testHandleErrorWithValidation() {
      if (handleErrorWithHasClassContext && handleErrorWithContextClass != null) {
        String className = handleErrorWithContextClass.getSimpleName();
        assertThatThrownBy(() -> monadError.handleErrorWith(null, validHandler))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining(className)
            .hasMessageContaining("handleErrorWith");

        assertThatThrownBy(() -> monadError.handleErrorWith(validKind, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining(className)
            .hasMessageContaining("handleErrorWith");
      } else {
        assertThatThrownBy(() -> monadError.handleErrorWith(null, validHandler))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("handleErrorWith");

        assertThatThrownBy(() -> monadError.handleErrorWith(validKind, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("handleErrorWith");
      }
    }

    private void testRecoverWithValidation() {
      if (handleErrorWithHasClassContext && handleErrorWithContextClass != null) {
        String className = handleErrorWithContextClass.getSimpleName();

        assertThatThrownBy(() -> monadError.recoverWith(null, validFallback))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining(className)
            .satisfies(
                t -> {
                  String msg = t.getMessage();
                  assertThat(msg)
                      .satisfiesAnyOf(
                          m -> assertThat(m).contains("recoverWith"),
                          m -> assertThat(m).contains("handleErrorWith"));
                });

        assertThatThrownBy(() -> monadError.recoverWith(validKind, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining(className)
            .satisfies(
                t -> {
                  String msg = t.getMessage();
                  assertThat(msg)
                      .satisfiesAnyOf(
                          m -> assertThat(m).contains("recoverWith"),
                          m -> assertThat(m).contains("handleErrorWith"));
                });
      } else {
        assertThatThrownBy(() -> monadError.recoverWith(null, validFallback))
            .isInstanceOf(NullPointerException.class)
            .satisfies(
                t -> {
                  String msg = t.getMessage();
                  assertThat(msg)
                      .satisfiesAnyOf(
                          m -> assertThat(m).contains("recoverWith"),
                          m -> assertThat(m).contains("handleErrorWith"));
                });

        assertThatThrownBy(() -> monadError.recoverWith(validKind, null))
            .isInstanceOf(NullPointerException.class)
            .satisfies(
                t -> {
                  String msg = t.getMessage();
                  assertThat(msg)
                      .satisfiesAnyOf(
                          m -> assertThat(m).contains("recoverWith"),
                          m -> assertThat(m).contains("handleErrorWith"));
                });
      }
    }
  }
}
