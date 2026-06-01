// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.laws.BifunctorLaws;
import org.higherkindedj.hkt.maybe.Maybe;

/**
 * Bifunctor contract.
 *
 * <p>Exercises {@code bimap}/{@code first}/{@code second} across {@link Category#OPERATIONS},
 * {@link Category#VALIDATIONS}, {@link Category#EXCEPTIONS} and {@link Category#LAWS}.
 * Null-argument rejection is asserted type-only ({@link NullPointerException}) via {@link
 * ContractValidations#rejectsNull} — {@code bimap}/{@code first}/{@code second} are multi-argument
 * and their exact validation messages are implementation-dependent. Laws delegate to the shipped
 * {@code hkj-test} {@link BifunctorLaws} (identity, composition, first/second consistency).
 *
 * <p>Because sum-type bifunctors (Either, Validated) only apply one side's mapper per inhabitant,
 * the exception checks use {@link Builder#withFirstExceptionKind} / {@link
 * Builder#withSecondExceptionKind} to pick an inhabitant on which the relevant mapper is actually
 * invoked; both default to the supplied valid kind for product-type bifunctors (Writer, Const)
 * where both mappers always run. As with every contract, execution and failure accumulation live in
 * the shared {@link ContractEngine}.
 */
public final class BifunctorContract {

  private BifunctorContract() {}

  /** Stage 1: supply the instance under test. */
  public static final class Start<F extends WitnessArity<TypeArity.Binary>> {
    private final Class<?> contextClass;

    Start(Class<?> contextClass) {
      this.contextClass = contextClass;
    }

    public <A, B> WithInstance<F, A, B> instance(Bifunctor<F> bifunctor) {
      return new WithInstance<>(contextClass, bifunctor);
    }
  }

  /** Stage 2: supply a sample {@code Kind2}. */
  public static final class WithInstance<F extends WitnessArity<TypeArity.Binary>, A, B> {
    private final Class<?> contextClass;
    private final Bifunctor<F> bifunctor;

    WithInstance(Class<?> contextClass, Bifunctor<F> bifunctor) {
      this.contextClass = contextClass;
      this.bifunctor = bifunctor;
    }

    public WithKind2<F, A, B> withKind2(Kind2<F, A, B> kind) {
      return new WithKind2<>(contextClass, bifunctor, kind);
    }
  }

  /** Stage 3: supply the first-parameter mapper. */
  public static final class WithKind2<F extends WitnessArity<TypeArity.Binary>, A, B> {
    private final Class<?> contextClass;
    private final Bifunctor<F> bifunctor;
    private final Kind2<F, A, B> kind;

    WithKind2(Class<?> contextClass, Bifunctor<F> bifunctor, Kind2<F, A, B> kind) {
      this.contextClass = contextClass;
      this.bifunctor = bifunctor;
      this.kind = kind;
    }

    public <C> WithFirstMapper<F, A, B, C> withFirstMapper(Function<A, C> firstMapper) {
      return new WithFirstMapper<>(contextClass, bifunctor, kind, firstMapper);
    }
  }

  /** Stage 4: supply the second-parameter mapper. */
  public static final class WithFirstMapper<F extends WitnessArity<TypeArity.Binary>, A, B, C> {
    private final Class<?> contextClass;
    private final Bifunctor<F> bifunctor;
    private final Kind2<F, A, B> kind;
    private final Function<A, C> firstMapper;

    WithFirstMapper(
        Class<?> contextClass,
        Bifunctor<F> bifunctor,
        Kind2<F, A, B> kind,
        Function<A, C> firstMapper) {
      this.contextClass = contextClass;
      this.bifunctor = bifunctor;
      this.kind = kind;
      this.firstMapper = firstMapper;
    }

    public <D> Builder<F, A, B, C, D> withSecondMapper(Function<B, D> secondMapper) {
      return new Builder<>(contextClass, bifunctor, kind, firstMapper, secondMapper);
    }
  }

  /** Stage 5: optional configuration and execution. */
  public static final class Builder<F extends WitnessArity<TypeArity.Binary>, A, B, C, D>
      extends AbstractContractBuilder {
    private final Bifunctor<F> bifunctor;
    private final Kind2<F, A, B> kind;
    private final Function<A, C> firstMapper;
    private final Function<B, D> secondMapper;
    private Maybe<Function<C, String>> compositionFirstMapper = Maybe.nothing();
    private Maybe<Function<D, String>> compositionSecondMapper = Maybe.nothing();
    private Maybe<BiPredicate<Kind2<F, ?, ?>, Kind2<F, ?, ?>>> equality = Maybe.nothing();
    private Maybe<Kind2<F, A, B>> firstExceptionKind = Maybe.nothing();
    private Maybe<Kind2<F, A, B>> secondExceptionKind = Maybe.nothing();

    Builder(
        Class<?> contextClass,
        Bifunctor<F> bifunctor,
        Kind2<F, A, B> kind,
        Function<A, C> firstMapper,
        Function<B, D> secondMapper) {
      super("Bifunctor", contextClass);
      this.bifunctor = bifunctor;
      this.kind = kind;
      this.firstMapper = firstMapper;
      this.secondMapper = secondMapper;
    }

    /** Second-stage first mapper for the composition law. */
    public Builder<F, A, B, C, D> withCompositionFirstMapper(Function<C, String> mapper) {
      this.compositionFirstMapper = Maybe.just(mapper);
      return this;
    }

    /** Second-stage second mapper for the composition law. */
    public Builder<F, A, B, C, D> withCompositionSecondMapper(Function<D, String> mapper) {
      this.compositionSecondMapper = Maybe.just(mapper);
      return this;
    }

    /** Equality used for law verification; required when {@link Category#LAWS} is exercised. */
    public Builder<F, A, B, C, D> withEqualityChecker(
        BiPredicate<Kind2<F, ?, ?>, Kind2<F, ?, ?>> eq) {
      this.equality = Maybe.just(eq);
      return this;
    }

    /**
     * Inhabitant on which the first mapper is invoked (e.g. a {@code Left}/{@code Invalid}); used
     * by the exception checks. Defaults to the supplied valid kind.
     */
    public Builder<F, A, B, C, D> withFirstExceptionKind(Kind2<F, A, B> exceptionKind) {
      this.firstExceptionKind = Maybe.just(exceptionKind);
      return this;
    }

    /**
     * Inhabitant on which the second mapper is invoked (e.g. a {@code Right}/{@code Valid}); used
     * by the exception checks. Defaults to the supplied valid kind.
     */
    public Builder<F, A, B, C, D> withSecondExceptionKind(Kind2<F, A, B> exceptionKind) {
      this.secondExceptionKind = Maybe.just(exceptionKind);
      return this;
    }

    @Override
    protected List<Check> checks(List<Category> selected) {
      List<Check> checks = new ArrayList<>();

      if (selected.contains(Category.OPERATIONS)) {
        checks.add(
            new Check(
                "bimap returns non-null",
                Category.OPERATIONS,
                () -> assertThat(bifunctor.bimap(firstMapper, secondMapper, kind)).isNotNull()));
        checks.add(
            new Check(
                "first returns non-null",
                Category.OPERATIONS,
                () -> assertThat(bifunctor.first(firstMapper, kind)).isNotNull()));
        checks.add(
            new Check(
                "second returns non-null",
                Category.OPERATIONS,
                () -> assertThat(bifunctor.second(secondMapper, kind)).isNotNull()));
      }

      if (selected.contains(Category.VALIDATIONS)) {
        checks.add(
            new Check(
                "bimap null-argument validations",
                Category.VALIDATIONS,
                () -> {
                  ContractValidations.rejectsNull(
                      () -> bifunctor.bimap((Function<A, C>) null, secondMapper, kind));
                  ContractValidations.rejectsNull(
                      () -> bifunctor.bimap(firstMapper, (Function<B, D>) null, kind));
                  ContractValidations.rejectsNull(
                      () -> bifunctor.bimap(firstMapper, secondMapper, (Kind2<F, A, B>) null));
                }));
        checks.add(
            new Check(
                "first null-argument validations",
                Category.VALIDATIONS,
                () -> {
                  ContractValidations.rejectsNull(
                      () -> bifunctor.first((Function<A, C>) null, kind));
                  ContractValidations.rejectsNull(
                      () -> bifunctor.first(firstMapper, (Kind2<F, A, B>) null));
                }));
        checks.add(
            new Check(
                "second null-argument validations",
                Category.VALIDATIONS,
                () -> {
                  ContractValidations.rejectsNull(
                      () -> bifunctor.second((Function<B, D>) null, kind));
                  ContractValidations.rejectsNull(
                      () -> bifunctor.second(secondMapper, (Kind2<F, A, B>) null));
                }));
      }

      if (selected.contains(Category.EXCEPTIONS)) {
        Kind2<F, A, B> firstKind = firstExceptionKind.orElse(kind);
        Kind2<F, A, B> secondKind = secondExceptionKind.orElse(kind);
        checks.add(
            new Check(
                "bimap/first propagate first-mapper exception",
                Category.EXCEPTIONS,
                () -> {
                  RuntimeException boom = new RuntimeException("contract: bifunctor first boom");
                  Function<A, C> throwing =
                      a -> {
                        throw boom;
                      };
                  assertThatThrownBy(() -> bifunctor.bimap(throwing, secondMapper, firstKind))
                      .isSameAs(boom);
                  assertThatThrownBy(() -> bifunctor.first(throwing, firstKind)).isSameAs(boom);
                }));
        checks.add(
            new Check(
                "bimap/second propagate second-mapper exception",
                Category.EXCEPTIONS,
                () -> {
                  RuntimeException boom = new RuntimeException("contract: bifunctor second boom");
                  Function<B, D> throwing =
                      b -> {
                        throw boom;
                      };
                  assertThatThrownBy(() -> bifunctor.bimap(firstMapper, throwing, secondKind))
                      .isSameAs(boom);
                  assertThatThrownBy(() -> bifunctor.second(throwing, secondKind)).isSameAs(boom);
                }));
      }

      if (selected.contains(Category.LAWS)) {
        BiPredicate<Kind2<F, ?, ?>, Kind2<F, ?, ?>> eq =
            requireLaws(equality, "withEqualityChecker");
        Function<C, String> f2 = requireLaws(compositionFirstMapper, "withCompositionFirstMapper");
        Function<D, String> g2 =
            requireLaws(compositionSecondMapper, "withCompositionSecondMapper");
        checks.add(
            new Check(
                "identity law",
                Category.LAWS,
                () -> BifunctorLaws.assertIdentity(bifunctor, kind, eq)));
        checks.add(
            new Check(
                "composition law",
                Category.LAWS,
                () ->
                    BifunctorLaws.assertComposition(
                        bifunctor, kind, firstMapper, f2, secondMapper, g2, eq)));
        checks.add(
            new Check(
                "first-consistency law",
                Category.LAWS,
                () -> BifunctorLaws.assertFirstConsistency(bifunctor, kind, firstMapper, eq)));
        checks.add(
            new Check(
                "second-consistency law",
                Category.LAWS,
                () -> BifunctorLaws.assertSecondConsistency(bifunctor, kind, secondMapper, eq)));
      }

      return checks;
    }
  }
}
