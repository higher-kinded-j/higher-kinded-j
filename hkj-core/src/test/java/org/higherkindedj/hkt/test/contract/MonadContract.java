// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Monad contract.
 *
 * <p>Covers map/ap/map2 (inherited) plus the Monad-specific {@code flatMap} operation;
 * null-argument validation asserts each operation rejects null with a {@link NullPointerException}
 * (type-only via {@link ContractValidations#rejectsNull}); laws delegate to the shipped {@code
 * hkj-test} {@link MonadLaws} (left identity, right identity, associativity). Execution and failure
 * accumulation live in the shared {@link ContractEngine}.
 */
public final class MonadContract {

  private MonadContract() {}

  /** Stage 1: supply the instance under test. */
  public static final class Start<F extends WitnessArity<TypeArity.Unary>> {
    private final Class<?> contextClass;

    Start(Class<?> contextClass) {
      this.contextClass = contextClass;
    }

    public <A> WithInstance<F, A> instance(Monad<F> monad) {
      return new WithInstance<>(contextClass, monad);
    }
  }

  /** Stage 2: supply a sample {@code Kind}. */
  public static final class WithInstance<F extends WitnessArity<TypeArity.Unary>, A> {
    private final Class<?> contextClass;
    private final Monad<F> monad;

    WithInstance(Class<?> contextClass, Monad<F> monad) {
      this.contextClass = contextClass;
      this.monad = monad;
    }

    public <B> WithKind<F, A, B> withKind(Kind<F, A> kind) {
      return new WithKind<>(contextClass, monad, kind);
    }
  }

  /** Stage 3: supply the operation fixtures. */
  public static final class WithKind<F extends WitnessArity<TypeArity.Unary>, A, B> {
    private final Class<?> contextClass;
    private final Monad<F> monad;
    private final Kind<F, A> kind;

    WithKind(Class<?> contextClass, Monad<F> monad, Kind<F, A> kind) {
      this.contextClass = contextClass;
      this.monad = monad;
      this.kind = kind;
    }

    public Builder<F, A, B> withMonadOperations(
        Kind<F, A> kind2,
        Function<A, B> mapper,
        Function<A, Kind<F, B>> flatMapper,
        Kind<F, Function<A, B>> functionKind,
        BiFunction<A, A, B> combiningFunction) {
      return new Builder<>(
          contextClass, monad, kind, kind2, mapper, flatMapper, functionKind, combiningFunction);
    }
  }

  /** Stage 4: optional law configuration and execution. */
  public static final class Builder<F extends WitnessArity<TypeArity.Unary>, A, B>
      extends AbstractContractBuilder {
    private final Monad<F> monad;
    private final Kind<F, A> kind;
    private final Kind<F, A> kind2;
    private final Function<A, B> mapper;
    private final Function<A, Kind<F, B>> flatMapper;
    private final Kind<F, Function<A, B>> functionKind;
    private final BiFunction<A, A, B> combiningFunction;
    private Maybe<A> testValue = Maybe.nothing();
    private Maybe<Function<A, Kind<F, B>>> testFunction = Maybe.nothing();
    private Maybe<Function<B, Kind<F, B>>> chainFunction = Maybe.nothing();
    private Maybe<BiPredicate<Kind<F, ?>, Kind<F, ?>>> equality = Maybe.nothing();

    Builder(
        Class<?> contextClass,
        Monad<F> monad,
        Kind<F, A> kind,
        Kind<F, A> kind2,
        Function<A, B> mapper,
        Function<A, Kind<F, B>> flatMapper,
        Kind<F, Function<A, B>> functionKind,
        BiFunction<A, A, B> combiningFunction) {
      super("Monad", contextClass);
      this.monad = monad;
      this.kind = kind;
      this.kind2 = kind2;
      this.mapper = mapper;
      this.flatMapper = flatMapper;
      this.functionKind = functionKind;
      this.combiningFunction = combiningFunction;
    }

    /** Configures law verification (left identity, right identity, associativity). */
    public Builder<F, A, B> withLawsTesting(
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
                () -> assertThat(monad.map(mapper, kind)).isNotNull()));
        checks.add(
            new Check(
                "flatMap returns non-null",
                Category.OPERATIONS,
                () -> assertThat(monad.flatMap(flatMapper, kind)).isNotNull()));
        checks.add(
            new Check(
                "ap returns non-null",
                Category.OPERATIONS,
                () -> assertThat(monad.ap(functionKind, kind)).isNotNull()));
      }

      if (selected.contains(Category.VALIDATIONS)) {
        checks.add(
            new Check(
                "null-argument validations (map, ap, flatMap, map2)",
                Category.VALIDATIONS,
                () -> {
                  ContractValidations.rejectsNull(() -> monad.map(null, kind));
                  ContractValidations.rejectsNull(() -> monad.map(mapper, null));
                  ContractValidations.rejectsNull(() -> monad.ap(null, kind));
                  ContractValidations.rejectsNull(() -> monad.ap(functionKind, null));
                  ContractValidations.rejectsNull(() -> monad.flatMap(null, kind));
                  ContractValidations.rejectsNull(() -> monad.flatMap(flatMapper, null));
                  ContractValidations.rejectsNull(() -> monad.map2(null, kind2, combiningFunction));
                  ContractValidations.rejectsNull(() -> monad.map2(kind, null, combiningFunction));
                  ContractValidations.rejectsNull(
                      () -> monad.map2(kind, kind2, (BiFunction<A, A, B>) null));
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
                  assertThatThrownBy(() -> monad.map(throwing, kind)).isSameAs(boom);
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
                  assertThatThrownBy(() -> monad.flatMap(throwing, kind)).isSameAs(boom);
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
                () -> MonadLaws.assertLeftIdentity(monad, value, f, eq)));
        checks.add(
            new Check(
                "right identity law",
                Category.LAWS,
                () -> MonadLaws.assertRightIdentity(monad, kind, eq)));
        checks.add(
            new Check(
                "associativity law",
                Category.LAWS,
                () -> MonadLaws.assertAssociativity(monad, kind, f, g, eq)));
      }

      return checks;
    }
  }
}
