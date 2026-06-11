// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * MonadError contract.
 *
 * <p>Covers the inherited Monad operations (map/flatMap/ap) plus the MonadError-specific {@code
 * handleErrorWith}/{@code recoverWith}; null-argument validation asserts each operation rejects
 * null with a {@link NullPointerException} (type-only via {@link ContractValidations#rejectsNull});
 * laws delegate to the shipped {@code hkj-test} {@link MonadLaws} (MonadError adds no laws of its
 * own beyond the Monad laws). Execution and failure accumulation live in the shared {@link
 * ContractEngine}.
 */
public final class MonadErrorContract {

  private MonadErrorContract() {}

  /** Stage 1: supply the instance under test. */
  public static final class Start<F extends WitnessArity<TypeArity.Unary>, E> {
    private final Class<?> contextClass;

    Start(Class<?> contextClass) {
      this.contextClass = contextClass;
    }

    public <A> WithInstance<F, E, A> instance(MonadError<F, E> monadError) {
      return new WithInstance<>(contextClass, monadError);
    }
  }

  /** Stage 2: supply a sample {@code Kind}. */
  public static final class WithInstance<F extends WitnessArity<TypeArity.Unary>, E, A> {
    private final Class<?> contextClass;
    private final MonadError<F, E> monadError;

    WithInstance(Class<?> contextClass, MonadError<F, E> monadError) {
      this.contextClass = contextClass;
      this.monadError = monadError;
    }

    public <B> WithKind<F, E, A, B> withKind(Kind<F, A> kind) {
      return new WithKind<>(contextClass, monadError, kind);
    }
  }

  /** Stage 3: supply the monad operation fixtures. */
  public static final class WithKind<F extends WitnessArity<TypeArity.Unary>, E, A, B> {
    private final Class<?> contextClass;
    private final MonadError<F, E> monadError;
    private final Kind<F, A> kind;

    WithKind(Class<?> contextClass, MonadError<F, E> monadError, Kind<F, A> kind) {
      this.contextClass = contextClass;
      this.monadError = monadError;
      this.kind = kind;
    }

    public WithOperations<F, E, A, B> withMonadOperations(
        Function<A, B> mapper,
        Function<A, Kind<F, B>> flatMapper,
        Kind<F, Function<A, B>> functionKind) {
      return new WithOperations<>(contextClass, monadError, kind, mapper, flatMapper, functionKind);
    }
  }

  /** Stage 4: supply the error-handling fixtures. */
  public static final class WithOperations<F extends WitnessArity<TypeArity.Unary>, E, A, B> {
    private final Class<?> contextClass;
    private final MonadError<F, E> monadError;
    private final Kind<F, A> kind;
    private final Function<A, B> mapper;
    private final Function<A, Kind<F, B>> flatMapper;
    private final Kind<F, Function<A, B>> functionKind;

    WithOperations(
        Class<?> contextClass,
        MonadError<F, E> monadError,
        Kind<F, A> kind,
        Function<A, B> mapper,
        Function<A, Kind<F, B>> flatMapper,
        Kind<F, Function<A, B>> functionKind) {
      this.contextClass = contextClass;
      this.monadError = monadError;
      this.kind = kind;
      this.mapper = mapper;
      this.flatMapper = flatMapper;
      this.functionKind = functionKind;
    }

    public Builder<F, E, A, B> withErrorHandling(
        Function<E, Kind<F, A>> handler, Kind<F, A> fallback) {
      return new Builder<>(
          contextClass, monadError, kind, mapper, flatMapper, functionKind, handler, fallback);
    }
  }

  /** Stage 5: optional law configuration and execution. */
  public static final class Builder<F extends WitnessArity<TypeArity.Unary>, E, A, B>
      extends AbstractContractBuilder {
    private final MonadError<F, E> monadError;
    private final Kind<F, A> kind;
    private final Function<A, B> mapper;
    private final Function<A, Kind<F, B>> flatMapper;
    private final Kind<F, Function<A, B>> functionKind;
    private final Function<E, Kind<F, A>> handler;
    private final Kind<F, A> fallback;
    private Maybe<A> testValue = Maybe.nothing();
    private Maybe<Function<A, Kind<F, B>>> testFunction = Maybe.nothing();
    private Maybe<Function<B, Kind<F, B>>> chainFunction = Maybe.nothing();
    private Maybe<BiPredicate<Kind<F, ?>, Kind<F, ?>>> equality = Maybe.nothing();

    Builder(
        Class<?> contextClass,
        MonadError<F, E> monadError,
        Kind<F, A> kind,
        Function<A, B> mapper,
        Function<A, Kind<F, B>> flatMapper,
        Kind<F, Function<A, B>> functionKind,
        Function<E, Kind<F, A>> handler,
        Kind<F, A> fallback) {
      super("MonadError", contextClass);
      this.monadError = monadError;
      this.kind = kind;
      this.mapper = mapper;
      this.flatMapper = flatMapper;
      this.functionKind = functionKind;
      this.handler = handler;
      this.fallback = fallback;
    }

    /**
     * Configures law verification (the Monad laws: left identity, right identity, associativity).
     */
    public Builder<F, E, A, B> withLawsTesting(
        A testValue,
        Function<A, Kind<F, B>> testFunction,
        Function<B, Kind<F, B>> chainFunction,
        BiPredicate<Kind<F, ?>, Kind<F, ?>> equality) {
      this.testValue = Maybe.just(testValue);
      this.testFunction = Maybe.just(testFunction);
      this.chainFunction = Maybe.just(chainFunction);
      this.equality = Maybe.just(equality);
      return this;
    }

    @Override
    protected List<Check> checks(List<Category> selected) {
      List<Check> checks = new ArrayList<>();

      if (selected.contains(Category.OPERATIONS)) {
        checks.add(
            new Check(
                "map returns non-null",
                Category.OPERATIONS,
                () -> assertThat(monadError.map(mapper, kind)).isNotNull()));
        checks.add(
            new Check(
                "flatMap returns non-null",
                Category.OPERATIONS,
                () -> assertThat(monadError.flatMap(flatMapper, kind)).isNotNull()));
        checks.add(
            new Check(
                "ap returns non-null",
                Category.OPERATIONS,
                () -> assertThat(monadError.ap(functionKind, kind)).isNotNull()));
        checks.add(
            new Check(
                "handleErrorWith returns non-null",
                Category.OPERATIONS,
                () -> assertThat(monadError.handleErrorWith(kind, handler)).isNotNull()));
        checks.add(
            new Check(
                "recoverWith returns non-null",
                Category.OPERATIONS,
                () -> assertThat(monadError.recoverWith(kind, fallback)).isNotNull()));
      }

      if (selected.contains(Category.VALIDATIONS)) {
        checks.add(
            new Check(
                "null-argument validations (map, ap, flatMap, handleErrorWith, recoverWith, recover)",
                Category.VALIDATIONS,
                () -> {
                  ContractValidations.rejectsNull(() -> monadError.map(null, kind));
                  ContractValidations.rejectsNull(() -> monadError.map(mapper, null));
                  ContractValidations.rejectsNull(() -> monadError.ap(null, kind));
                  ContractValidations.rejectsNull(() -> monadError.ap(functionKind, null));
                  ContractValidations.rejectsNull(() -> monadError.flatMap(null, kind));
                  ContractValidations.rejectsNull(() -> monadError.flatMap(flatMapper, null));
                  ContractValidations.rejectsNull(() -> monadError.handleErrorWith(null, handler));
                  ContractValidations.rejectsNull(() -> monadError.handleErrorWith(kind, null));
                  ContractValidations.rejectsNull(() -> monadError.recoverWith(null, fallback));
                  ContractValidations.rejectsNull(() -> monadError.recoverWith(kind, null));
                  // recover's value is @Nullable (a null value is valid), so only the null
                  // source is rejected. This also covers instances that inherit the default
                  // recover (e.g. ValidatedMonad), which fails fast via handleErrorWith.
                  ContractValidations.rejectsNull(() -> monadError.recover(null, null));
                }));
      }

      if (selected.contains(Category.EXCEPTIONS)) {
        checks.add(
            new Check(
                "map propagates mapper exception",
                Category.EXCEPTIONS,
                () -> {
                  RuntimeException boom = new RuntimeException("contract: mapper boom");
                  Function<A, B> throwing =
                      a -> {
                        throw boom;
                      };
                  assertThatThrownBy(() -> monadError.map(throwing, kind)).isSameAs(boom);
                }));
        checks.add(
            new Check(
                "flatMap propagates function exception",
                Category.EXCEPTIONS,
                () -> {
                  RuntimeException boom = new RuntimeException("contract: flatMap boom");
                  Function<A, Kind<F, B>> throwing =
                      a -> {
                        throw boom;
                      };
                  assertThatThrownBy(() -> monadError.flatMap(throwing, kind)).isSameAs(boom);
                }));
      }

      if (selected.contains(Category.LAWS)) {
        BiPredicate<Kind<F, ?>, Kind<F, ?>> eq = requireLaws(equality, "withLawsTesting");
        A value = requireLaws(testValue, "withLawsTesting");
        Function<A, Kind<F, B>> f = requireLaws(testFunction, "withLawsTesting");
        Function<B, Kind<F, B>> g = requireLaws(chainFunction, "withLawsTesting");
        checks.add(
            new Check(
                "left identity law",
                Category.LAWS,
                () -> MonadLaws.assertLeftIdentity(monadError, value, f, eq)));
        checks.add(
            new Check(
                "right identity law",
                Category.LAWS,
                () -> MonadLaws.assertRightIdentity(monadError, kind, eq)));
        checks.add(
            new Check(
                "associativity law",
                Category.LAWS,
                () -> MonadLaws.assertAssociativity(monadError, kind, f, g, eq)));
      }

      return checks;
    }
  }
}
