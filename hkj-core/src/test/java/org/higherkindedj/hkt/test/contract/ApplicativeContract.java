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
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.laws.ApplicativeLaws;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Applicative contract.
 *
 * <p>Covers the Functor-level {@code map} operation/exception plus the Applicative-specific {@code
 * ap}/{@code map2} operations; null-argument validation asserts each operation rejects null with a
 * {@link NullPointerException} (type-only via {@link ContractValidations#rejectsNull} — the exact
 * message is implementation-dependent for the multi-argument {@code ap}/{@code map2}); laws
 * delegate to the shipped {@code hkj-test} {@link ApplicativeLaws} (identity, homomorphism,
 * interchange). As with every contract, execution and failure accumulation live in the shared
 * {@link ContractEngine}.
 */
public final class ApplicativeContract {

  private ApplicativeContract() {}

  /** Stage 1: supply the instance under test. */
  public static final class Start<F extends WitnessArity<TypeArity.Unary>> {
    private final Class<?> contextClass;

    Start(Class<?> contextClass) {
      this.contextClass = contextClass;
    }

    public <A> WithInstance<F, A> instance(Applicative<F> applicative) {
      return new WithInstance<>(contextClass, applicative);
    }
  }

  /** Stage 2: supply a sample {@code Kind}. */
  public static final class WithInstance<F extends WitnessArity<TypeArity.Unary>, A> {
    private final Class<?> contextClass;
    private final Applicative<F> applicative;

    WithInstance(Class<?> contextClass, Applicative<F> applicative) {
      this.contextClass = contextClass;
      this.applicative = applicative;
    }

    public <B> WithKind<F, A, B> withKind(Kind<F, A> kind) {
      return new WithKind<>(contextClass, applicative, kind);
    }
  }

  /** Stage 3: supply the operation fixtures. */
  public static final class WithKind<F extends WitnessArity<TypeArity.Unary>, A, B> {
    private final Class<?> contextClass;
    private final Applicative<F> applicative;
    private final Kind<F, A> kind;

    WithKind(Class<?> contextClass, Applicative<F> applicative, Kind<F, A> kind) {
      this.contextClass = contextClass;
      this.applicative = applicative;
      this.kind = kind;
    }

    public Builder<F, A, B> withOperations(
        Kind<F, A> kind2,
        Function<A, B> mapper,
        Kind<F, Function<A, B>> functionKind,
        BiFunction<A, A, B> combiningFunction) {
      return new Builder<>(
          contextClass, applicative, kind, kind2, mapper, functionKind, combiningFunction);
    }
  }

  /** Stage 4: optional law configuration and execution. */
  public static final class Builder<F extends WitnessArity<TypeArity.Unary>, A, B>
      extends AbstractContractBuilder {
    private final Applicative<F> applicative;
    private final Kind<F, A> kind;
    private final Kind<F, A> kind2;
    private final Function<A, B> mapper;
    private final Kind<F, Function<A, B>> functionKind;
    private final BiFunction<A, A, B> combiningFunction;
    private Maybe<A> testValue = Maybe.nothing();
    private Maybe<Function<A, B>> testFunction = Maybe.nothing();
    private Maybe<BiPredicate<Kind<F, ?>, Kind<F, ?>>> equality = Maybe.nothing();

    Builder(
        Class<?> contextClass,
        Applicative<F> applicative,
        Kind<F, A> kind,
        Kind<F, A> kind2,
        Function<A, B> mapper,
        Kind<F, Function<A, B>> functionKind,
        BiFunction<A, A, B> combiningFunction) {
      super("Applicative", contextClass);
      this.applicative = applicative;
      this.kind = kind;
      this.kind2 = kind2;
      this.mapper = mapper;
      this.functionKind = functionKind;
      this.combiningFunction = combiningFunction;
    }

    /** Configures law verification (identity, homomorphism, interchange). */
    public Builder<F, A, B> withLawsTesting(
        A testValue, Function<A, B> testFunction, BiPredicate<Kind<F, ?>, Kind<F, ?>> equality) {
      this.testValue = Maybe.just(testValue);
      this.testFunction = Maybe.just(testFunction);
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
                () -> assertThat(applicative.map(mapper, kind)).isNotNull()));
        checks.add(
            new Check(
                "ap returns non-null",
                Category.OPERATIONS,
                () -> assertThat(applicative.ap(functionKind, kind)).isNotNull()));
        checks.add(
            new Check(
                "map2 returns non-null",
                Category.OPERATIONS,
                () -> assertThat(applicative.map2(kind, kind2, combiningFunction)).isNotNull()));
      }

      if (selected.contains(Category.VALIDATIONS)) {
        checks.add(
            new Check(
                "null-argument validations (map, ap, map2)",
                Category.VALIDATIONS,
                () -> {
                  ContractValidations.rejectsNull(() -> applicative.map(null, kind));
                  ContractValidations.rejectsNull(() -> applicative.map(mapper, null));
                  ContractValidations.rejectsNull(() -> applicative.ap(null, kind));
                  ContractValidations.rejectsNull(() -> applicative.ap(functionKind, null));
                  ContractValidations.rejectsNull(
                      () -> applicative.map2(null, kind2, combiningFunction));
                  ContractValidations.rejectsNull(
                      () -> applicative.map2(kind, null, combiningFunction));
                  ContractValidations.rejectsNull(
                      () -> applicative.map2(kind, kind2, (BiFunction<A, A, B>) null));
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
                  assertThatThrownBy(() -> applicative.map(throwing, kind)).isSameAs(boom);
                }));
      }

      if (selected.contains(Category.LAWS)) {
        BiPredicate<Kind<F, ?>, Kind<F, ?>> eq = requireLaws(equality, "withLawsTesting");
        A value = requireLaws(testValue, "withLawsTesting");
        Function<A, B> f = requireLaws(testFunction, "withLawsTesting");
        checks.add(
            new Check(
                "identity law",
                Category.LAWS,
                () -> ApplicativeLaws.assertIdentity(applicative, kind, eq)));
        checks.add(
            new Check(
                "homomorphism law",
                Category.LAWS,
                () -> ApplicativeLaws.assertHomomorphism(applicative, value, f, eq)));
        checks.add(
            new Check(
                "interchange law",
                Category.LAWS,
                () -> ApplicativeLaws.assertInterchange(applicative, functionKind, value, eq)));
      }

      return checks;
    }
  }
}
