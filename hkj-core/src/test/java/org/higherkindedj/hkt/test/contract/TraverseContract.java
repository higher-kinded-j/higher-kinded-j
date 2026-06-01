// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.laws.TraverseLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Traverse contract.
 *
 * <p>{@link Traverse} extends {@code Functor} and {@code Foldable}, so this contract exercises the
 * inherited {@code map}/{@code foldMap} operations alongside the traverse-specific {@code
 * traverse}, across {@link Category#OPERATIONS}, {@link Category#VALIDATIONS}, {@link
 * Category#EXCEPTIONS} and {@link Category#LAWS}. Null-argument rejection is asserted
 * production-aligned (re-derived from {@link Validation}, since every shipped traverse routes
 * {@code map}/{@code foldMap}/{@code traverse} through {@code Validation.function()}). The identity
 * law delegates to the shipped {@code hkj-test} {@link TraverseLaws}; it is only exercised when
 * {@link Builder#withEqualityChecker} has supplied a structural equality over {@code Kind<F, ?>} —
 * the contract refuses to default to reference equality. As with every contract, execution and
 * failure accumulation live in the shared {@link ContractEngine}.
 */
public final class TraverseContract {

  private TraverseContract() {}

  /** Stage 1: supply the instance under test. */
  public static final class Start<F extends WitnessArity<TypeArity.Unary>> {
    private final Class<?> contextClass;

    Start(Class<?> contextClass) {
      this.contextClass = contextClass;
    }

    public <A> WithInstance<F, A> instance(Traverse<F> traverse) {
      return new WithInstance<>(contextClass, traverse);
    }
  }

  /** Stage 2: supply a sample {@code Kind}. */
  public static final class WithInstance<F extends WitnessArity<TypeArity.Unary>, A> {
    private final Class<?> contextClass;
    private final Traverse<F> traverse;

    WithInstance(Class<?> contextClass, Traverse<F> traverse) {
      this.contextClass = contextClass;
      this.traverse = traverse;
    }

    public <B> WithKind<F, A, B> withKind(Kind<F, A> kind) {
      return new WithKind<>(contextClass, traverse, kind);
    }
  }

  /** Stage 3: supply the Functor mapper. */
  public static final class WithKind<F extends WitnessArity<TypeArity.Unary>, A, B> {
    private final Class<?> contextClass;
    private final Traverse<F> traverse;
    private final Kind<F, A> kind;

    WithKind(Class<?> contextClass, Traverse<F> traverse, Kind<F, A> kind) {
      this.contextClass = contextClass;
      this.traverse = traverse;
      this.kind = kind;
    }

    public WithMapper<F, A, B> withMapper(Function<A, B> mapper) {
      return new WithMapper<>(contextClass, traverse, kind, mapper);
    }
  }

  /** Stage 4: supply the applicative and effectful traverse function. */
  public static final class WithMapper<F extends WitnessArity<TypeArity.Unary>, A, B> {
    private final Class<?> contextClass;
    private final Traverse<F> traverse;
    private final Kind<F, A> kind;
    private final Function<A, B> mapper;

    WithMapper(
        Class<?> contextClass, Traverse<F> traverse, Kind<F, A> kind, Function<A, B> mapper) {
      this.contextClass = contextClass;
      this.traverse = traverse;
      this.kind = kind;
      this.mapper = mapper;
    }

    public <G extends WitnessArity<TypeArity.Unary>> WithApplicative<F, G, A, B> withApplicative(
        Applicative<G> applicative, Function<A, Kind<G, B>> traverseFunction) {
      return new WithApplicative<>(
          contextClass, traverse, kind, mapper, applicative, traverseFunction);
    }
  }

  /** Stage 5: supply the Foldable monoid and foldMap function. */
  public static final class WithApplicative<
      F extends WitnessArity<TypeArity.Unary>, G extends WitnessArity<TypeArity.Unary>, A, B> {
    private final Class<?> contextClass;
    private final Traverse<F> traverse;
    private final Kind<F, A> kind;
    private final Function<A, B> mapper;
    private final Applicative<G> applicative;
    private final Function<A, Kind<G, B>> traverseFunction;

    WithApplicative(
        Class<?> contextClass,
        Traverse<F> traverse,
        Kind<F, A> kind,
        Function<A, B> mapper,
        Applicative<G> applicative,
        Function<A, Kind<G, B>> traverseFunction) {
      this.contextClass = contextClass;
      this.traverse = traverse;
      this.kind = kind;
      this.mapper = mapper;
      this.applicative = applicative;
      this.traverseFunction = traverseFunction;
    }

    public <M> Builder<F, G, A, B, M> withFoldable(
        Monoid<M> monoid, Function<A, M> foldMapFunction) {
      return new Builder<>(
          contextClass,
          traverse,
          kind,
          mapper,
          applicative,
          traverseFunction,
          monoid,
          foldMapFunction);
    }
  }

  /** Stage 6: optional law configuration and execution. */
  public static final class Builder<
          F extends WitnessArity<TypeArity.Unary>, G extends WitnessArity<TypeArity.Unary>, A, B, M>
      extends AbstractContractBuilder {
    private final Traverse<F> traverse;
    private final Kind<F, A> kind;
    private final Function<A, B> mapper;
    private final Applicative<G> applicative;
    private final Function<A, Kind<G, B>> traverseFunction;
    private final Monoid<M> monoid;
    private final Function<A, M> foldMapFunction;
    private Maybe<BiPredicate<Kind<F, ?>, Kind<F, ?>>> equality = Maybe.nothing();

    Builder(
        Class<?> contextClass,
        Traverse<F> traverse,
        Kind<F, A> kind,
        Function<A, B> mapper,
        Applicative<G> applicative,
        Function<A, Kind<G, B>> traverseFunction,
        Monoid<M> monoid,
        Function<A, M> foldMapFunction) {
      super("Traverse", contextClass);
      this.traverse = traverse;
      this.kind = kind;
      this.mapper = mapper;
      this.applicative = applicative;
      this.traverseFunction = traverseFunction;
      this.monoid = monoid;
      this.foldMapFunction = foldMapFunction;
    }

    /**
     * Equality (over {@code Kind<F, ?>}) used for the identity law; required when {@link
     * Category#LAWS} is exercised.
     */
    public Builder<F, G, A, B, M> withEqualityChecker(BiPredicate<Kind<F, ?>, Kind<F, ?>> eq) {
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
                () -> assertThat(traverse.map(mapper, kind)).isNotNull()));
        checks.add(
            new Check(
                "foldMap returns non-null",
                Category.OPERATIONS,
                () -> assertThat(traverse.foldMap(monoid, foldMapFunction, kind)).isNotNull()));
        checks.add(
            new Check(
                "traverse returns non-null",
                Category.OPERATIONS,
                () ->
                    assertThat(traverse.traverse(applicative, traverseFunction, kind))
                        .isNotNull()));
      }

      if (selected.contains(Category.VALIDATIONS)) {
        checks.add(
            new Check(
                "map rejects null function",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () -> Validation.function().validateMap((Function<A, B>) null, kind),
                        () -> traverse.map((Function<A, B>) null, kind))));
        checks.add(
            new Check(
                "map rejects null kind",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () -> Validation.function().validateMap(mapper, (Kind<F, A>) null),
                        () -> traverse.map(mapper, null))));
        checks.add(
            new Check(
                "foldMap rejects null monoid",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () -> Validation.function().validateFoldMap(null, foldMapFunction, kind),
                        () -> traverse.foldMap(null, foldMapFunction, kind))));
        checks.add(
            new Check(
                "foldMap rejects null function",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () -> Validation.function().validateFoldMap(monoid, null, kind),
                        () -> traverse.foldMap(monoid, null, kind))));
        checks.add(
            new Check(
                "foldMap rejects null kind",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () ->
                            Validation.function()
                                .validateFoldMap(monoid, foldMapFunction, (Kind<F, A>) null),
                        () -> traverse.foldMap(monoid, foldMapFunction, null))));
        checks.add(
            new Check(
                "traverse rejects null applicative",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () -> Validation.function().validateTraverse(null, traverseFunction, kind),
                        () -> traverse.traverse(null, traverseFunction, kind))));
        checks.add(
            new Check(
                "traverse rejects null function",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () -> Validation.function().validateTraverse(applicative, null, kind),
                        () -> traverse.traverse(applicative, null, kind))));
        checks.add(
            new Check(
                "traverse rejects null kind",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () ->
                            Validation.function()
                                .validateTraverse(applicative, traverseFunction, (Kind<F, A>) null),
                        () -> traverse.traverse(applicative, traverseFunction, null))));
      }

      if (selected.contains(Category.EXCEPTIONS)) {
        checks.add(
            new Check(
                "map propagates mapper exception",
                Category.EXCEPTIONS,
                () -> {
                  RuntimeException boom = new RuntimeException("contract: map boom");
                  Function<A, B> throwing =
                      a -> {
                        throw boom;
                      };
                  assertThatThrownBy(() -> traverse.map(throwing, kind)).isSameAs(boom);
                }));
        checks.add(
            new Check(
                "foldMap propagates function exception",
                Category.EXCEPTIONS,
                () -> {
                  RuntimeException boom = new RuntimeException("contract: foldMap boom");
                  Function<A, M> throwing =
                      a -> {
                        throw boom;
                      };
                  assertThatThrownBy(() -> traverse.foldMap(monoid, throwing, kind)).isSameAs(boom);
                }));
        checks.add(
            new Check(
                "traverse propagates function exception",
                Category.EXCEPTIONS,
                () -> {
                  RuntimeException boom = new RuntimeException("contract: traverse boom");
                  Function<A, Kind<G, B>> throwing =
                      a -> {
                        throw boom;
                      };
                  assertThatThrownBy(() -> traverse.traverse(applicative, throwing, kind))
                      .isSameAs(boom);
                }));
      }

      if (selected.contains(Category.LAWS)) {
        BiPredicate<Kind<F, ?>, Kind<F, ?>> eq = requireLaws(equality, "withEqualityChecker");
        checks.add(
            new Check(
                "identity law",
                Category.LAWS,
                () -> TraverseLaws.assertIdentity(traverse, kind, eq)));
      }

      return checks;
    }
  }
}
