// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.util.validation.Validation;

/**
 * Foldable contract.
 *
 * <p>{@link Foldable} has a single operation ({@code foldMap}) and no algebraic laws, so this
 * contract exercises {@link Category#OPERATIONS}, {@link Category#VALIDATIONS} and {@link
 * Category#EXCEPTIONS} only. Like every contract it carries no execution logic of its own — checks
 * are described as values and run by the shared {@link ContractEngine}; null-argument rejection is
 * delegated to {@link ContractValidations}.
 */
public final class FoldableContract {

  private FoldableContract() {}

  /** Stage 1: supply the instance under test. */
  public static final class Start<F extends WitnessArity<TypeArity.Unary>> {
    private final Class<?> contextClass;

    Start(Class<?> contextClass) {
      this.contextClass = contextClass;
    }

    public <A> WithInstance<F, A> instance(Foldable<F> foldable) {
      return new WithInstance<>(contextClass, foldable);
    }
  }

  /** Stage 2: supply a sample {@code Kind}. */
  public static final class WithInstance<F extends WitnessArity<TypeArity.Unary>, A> {
    private final Class<?> contextClass;
    private final Foldable<F> foldable;

    WithInstance(Class<?> contextClass, Foldable<F> foldable) {
      this.contextClass = contextClass;
      this.foldable = foldable;
    }

    public WithKind<F, A> withKind(Kind<F, A> kind) {
      return new WithKind<>(contextClass, foldable, kind);
    }
  }

  /** Stage 3: supply the monoid and foldMap function. */
  public static final class WithKind<F extends WitnessArity<TypeArity.Unary>, A> {
    private final Class<?> contextClass;
    private final Foldable<F> foldable;
    private final Kind<F, A> kind;

    WithKind(Class<?> contextClass, Foldable<F> foldable, Kind<F, A> kind) {
      this.contextClass = contextClass;
      this.foldable = foldable;
      this.kind = kind;
    }

    public <M> Builder<F, A, M> withOperations(Monoid<M> monoid, Function<A, M> foldMapFunction) {
      return new Builder<>(contextClass, foldable, kind, monoid, foldMapFunction);
    }
  }

  /** Stage 4: execution. */
  public static final class Builder<F extends WitnessArity<TypeArity.Unary>, A, M>
      extends AbstractContractBuilder {
    private final Foldable<F> foldable;
    private final Kind<F, A> kind;
    private final Monoid<M> monoid;
    private final Function<A, M> foldMapFunction;

    Builder(
        Class<?> contextClass,
        Foldable<F> foldable,
        Kind<F, A> kind,
        Monoid<M> monoid,
        Function<A, M> foldMapFunction) {
      super("Foldable", contextClass);
      this.foldable = foldable;
      this.kind = kind;
      this.monoid = monoid;
      this.foldMapFunction = foldMapFunction;
    }

    @Override
    protected List<Check> checks(List<Category> selected) {
      List<Check> checks = new ArrayList<>();

      if (selected.contains(Category.OPERATIONS)) {
        checks.add(
            new Check(
                "foldMap returns non-null",
                Category.OPERATIONS,
                () -> assertThat(foldable.foldMap(monoid, foldMapFunction, kind)).isNotNull()));
      }

      if (selected.contains(Category.VALIDATIONS)) {
        checks.add(
            new Check(
                "foldMap rejects null monoid",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () -> Validation.function().validateFoldMap(null, foldMapFunction, kind),
                        () -> foldable.foldMap(null, foldMapFunction, kind))));
        checks.add(
            new Check(
                "foldMap rejects null function",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () -> Validation.function().validateFoldMap(monoid, null, kind),
                        () -> foldable.foldMap(monoid, null, kind))));
        checks.add(
            new Check(
                "foldMap rejects null kind",
                Category.VALIDATIONS,
                () ->
                    ContractValidations.assertRejectsLikeProduction(
                        () ->
                            Validation.function()
                                .validateFoldMap(monoid, foldMapFunction, (Kind<F, A>) null),
                        () -> foldable.foldMap(monoid, foldMapFunction, null))));
      }

      if (selected.contains(Category.EXCEPTIONS)) {
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
                  assertThatThrownBy(() -> foldable.foldMap(monoid, throwing, kind)).isSameAs(boom);
                }));
      }

      return checks;
    }
  }
}
