// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Functor contract.
 *
 * <p>Fluent progressive-disclosure stages (you cannot reach {@code verify()} without supplying
 * instance, kind and mapper), while the heavy lifting — execution, failure accumulation, selection
 * — lives in the shared {@link ContractEngine}. Laws delegate to the shipped {@code hkj-test}
 * {@link FunctorLaws}. Optional configuration is modelled with {@link Maybe} rather than nullable
 * fields, and law verification refuses to silently default to reference equality.
 */
public final class FunctorContract {

  private FunctorContract() {}

  /** Stage 1: supply the instance under test. */
  public static final class Start<F extends WitnessArity<TypeArity.Unary>> {
    private final Class<?> contextClass;

    Start(Class<?> contextClass) {
      this.contextClass = contextClass;
    }

    public <A> WithInstance<F, A> instance(Functor<F> functor) {
      return new WithInstance<>(contextClass, functor);
    }
  }

  /** Stage 2: supply a sample {@code Kind}. */
  public static final class WithInstance<F extends WitnessArity<TypeArity.Unary>, A> {
    private final Class<?> contextClass;
    private final Functor<F> functor;

    WithInstance(Class<?> contextClass, Functor<F> functor) {
      this.contextClass = contextClass;
      this.functor = functor;
    }

    public <B> WithKind<F, A, B> withKind(Kind<F, A> kind) {
      return new WithKind<>(contextClass, functor, kind);
    }
  }

  /** Stage 3: supply the mapper. */
  public static final class WithKind<F extends WitnessArity<TypeArity.Unary>, A, B> {
    private final Class<?> contextClass;
    private final Functor<F> functor;
    private final Kind<F, A> kind;

    WithKind(Class<?> contextClass, Functor<F> functor, Kind<F, A> kind) {
      this.contextClass = contextClass;
      this.functor = functor;
      this.kind = kind;
    }

    public Builder<F, A, B> withMapper(Function<A, B> mapper) {
      return new Builder<>(contextClass, functor, kind, mapper);
    }
  }

  /** Stage 4: optional configuration and execution. */
  public static final class Builder<F extends WitnessArity<TypeArity.Unary>, A, B>
      extends AbstractContractBuilder {
    private final Functor<F> functor;
    private final Kind<F, A> kind;
    private final Function<A, B> mapper;
    private Maybe<Function<B, Object>> secondMapper = Maybe.nothing();
    private Maybe<BiPredicate<Kind<F, ?>, Kind<F, ?>>> equality = Maybe.nothing();

    Builder(Class<?> contextClass, Functor<F> functor, Kind<F, A> kind, Function<A, B> mapper) {
      super("Functor", contextClass);
      this.functor = functor;
      this.kind = kind;
      this.mapper = mapper;
    }

    /** Second mapper for the composition law; defaults to {@code Object::toString}. */
    public Builder<F, A, B> withSecondMapper(Function<? super B, ?> g) {
      this.secondMapper = Maybe.just(b -> g.apply(b));
      return this;
    }

    /** Equality used for law verification; required when {@link Category#LAWS} is exercised. */
    public Builder<F, A, B> withEqualityChecker(BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
      this.equality = Maybe.just(eq);
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
                () -> assertThat(functor.map(mapper, kind)).isNotNull()));
      }

      if (selected.contains(Category.VALIDATIONS)) {
        checks.add(
            new Check(
                "map rejects null function",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () -> Validation.function().validateMap((Function<A, B>) null, kind),
                        () -> functor.map((Function<A, B>) null, kind))));
        checks.add(
            new Check(
                "map rejects null kind",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () -> Validation.function().validateMap(mapper, (Kind<F, A>) null),
                        () -> functor.map(mapper, null))));
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
                  assertThatThrownBy(() -> functor.map(throwing, kind)).isSameAs(boom);
                }));
      }

      if (selected.contains(Category.LAWS)) {
        BiPredicate<Kind<F, ?>, Kind<F, ?>> eq = requireLaws(equality, "withEqualityChecker");
        Function<B, Object> g = secondMapper.orElse(Object::toString);
        checks.add(
            new Check(
                "identity law",
                Category.LAWS,
                () -> FunctorLaws.assertIdentity(functor, kind, eq)));
        checks.add(
            new Check(
                "composition law",
                Category.LAWS,
                () -> FunctorLaws.assertComposition(functor, kind, mapper, g, eq)));
      }

      return checks;
    }
  }
}
