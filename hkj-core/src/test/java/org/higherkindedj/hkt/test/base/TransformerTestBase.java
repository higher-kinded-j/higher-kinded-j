// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.base;

/**
 * Documentation and guidelines for testing monad transformer types.
 *
 * <p>This class provides comprehensive guidance for writing tests for monad transformers (EitherT,
 * MaybeT, OptionalT, ReaderT, StateT, etc.) using British spellings throughout.
 *
 * <h2>Overview of Monad Transformers</h2>
 *
 * <p>Monad transformers combine two monads into a single type, providing the capabilities of both.
 * The general structure is:
 *
 * <pre>{@code
 * TransformerT<F, ...> wraps Kind<F, InnerType<...>>
 *
 * For example:
 * - EitherT<F, E, A> wraps Kind<F, Either<E, A>>
 * - MaybeT<F, A> wraps Kind<F, Maybe<A>>
 * - OptionalT<F, A> wraps Kind<F, Optional<A>>
 * }</pre>
 *
 * <h2>Testing Strategy</h2>
 *
 * <p>Transformer tests should verify:
 *
 * <ol>
 *   <li><b>Outer monad behaviour</b> - How the transformer handles empty/error states in F
 *   <li><b>Inner type behaviour</b> - How the transformer handles Left/Right, Just/Nothing, etc.
 *   <li><b>Composition</b> - How both layers interact during operations
 *   <li><b>Law compliance</b> - Functor, Applicative, and Monad laws hold
 * </ol>
 *
 * <h2>Custom Assertions</h2>
 *
 * <p>Use the custom assertion classes to write cleaner, more readable tests:
 *
 * <ul>
 *   <li>{@code EitherTAssert} - for EitherT transformers
 *   <li>{@code MaybeTAssert} - for MaybeT transformers
 *   <li>{@code OptionalTAssert} - for OptionalT transformers
 * </ul>
 *
 * <h3>Example: Before and After</h3>
 *
 * <pre>{@code
 * // BEFORE: Verbose unwrapping and multiple assertions
 * Kind<EitherTKind.Witness<F, E>, A> result = eitherTMonad.map(mapper, input);
 * Optional<Either<E, A>> unwrapped = unwrapKindToOptionalEither(result);
 * assertThat(unwrapped).isPresent();
 * assertThat(unwrapped.get().isRight()).isTrue();
 * assertThat(unwrapped.get().right()).isEqualTo("expected");
 *
 * // AFTER: Clean fluent assertions
 * var result = eitherTMonad.map(mapper, input);
 * assertThatEitherT(result, this::unwrapToOptional)
 *     .isPresentRight()
 *     .hasRightValue("expected");
 * }</pre>
 *
 * <h2>Standard Test Structure</h2>
 *
 * <p>Transformer tests should follow this structure:
 *
 * <pre>{@code
 * @DisplayName("TransformerTMonad Complete Test Suite (Outer: F)")
 * class TransformerTMonadTest extends TypeClassTestBase<...> {
 *
 *   // 1. Test data and setup
 *   private MonadError<F, E> outerMonad;
 *   private MonadError<TransformerTKind.Witness<F, ...>, ...> transformerMonad;
 *
 *   @BeforeEach
 *   void setUpMonad() {
 *     outerMonad = ...;
 *     transformerMonad = new TransformerTMonad<>(outerMonad);
 *   }
 *
 *   // 2. Helper methods for unwrapping
 *   private <A> OuterType<InnerType<A>> unwrap(Kind<...> kind) { ... }
 *
 *   // 3. Helper methods for construction
 *   private <A> Kind<...> successValue(A value) { ... }
 *   private <A> Kind<...> errorValue(E error) { ... }
 *   private <A> Kind<...> emptyOuter() { ... }
 *
 *   // 4. TypeClassTestBase implementations
 *   @Override
 *   protected Kind<...> createValidKind() { return successValue(10); }
 *   // ... other abstract methods
 *
 *   // 5. Test classes
 *   @Nested class FunctorOperationTests { ... }
 *   @Nested class ApplicativeOperationTests { ... }
 *   @Nested class MonadOperationTests { ... }
 *   @Nested class MonadErrorOperationTests { ... }
 *   @Nested class EdgeCaseTests { ... }
 * }
 * }</pre>
 *
 * <h2>Helper Method Patterns</h2>
 *
 * <h3>Unwrapping Helper</h3>
 *
 * <p>Create a method to unwrap the transformer to the nested structure for assertions:
 *
 * <pre>{@code
 * private <A> Optional<Either<E, A>> unwrapToOptional(
 *     Kind<EitherTKind.Witness<F, E>, A> kind) {
 *   if (kind == null) return Optional.empty();
 *   var transformer = TRANSFORMER_T.narrow(kind);
 *   Kind<F, InnerType<A>> outerKind = transformer.value();
 *   return OUTER.narrow(outerKind);
 * }
 * }</pre>
 *
 * <h3>Construction Helpers</h3>
 *
 * <p>Create helper methods for common test scenarios:
 *
 * <pre>{@code
 * // For EitherT
 * private <R> Kind<...> rightT(R value) {
 *   return EITHER_T.widen(EitherT.right(outerMonad, value));
 * }
 *
 * private <R> Kind<...> leftT(E error) {
 *   return EITHER_T.widen(EitherT.left(outerMonad, error));
 * }
 *
 * private <R> Kind<...> emptyT() {
 *   Kind<F, Either<E, R>> emptyOuter = OUTER.widen(emptyValue);
 *   return EITHER_T.widen(EitherT.fromKind(emptyOuter));
 * }
 *
 * // For MaybeT
 * private <R> Kind<...> justT(R value) {
 *   return MAYBE_T.widen(MaybeT.just(outerMonad, value));
 * }
 *
 * private <R> Kind<...> nothingT() {
 *   return MAYBE_T.widen(MaybeT.nothing(outerMonad));
 * }
 *
 * // For OptionalT
 * private <R> Kind<...> someT(R value) {
 *   return OPTIONAL_T.widen(OptionalT.some(outerMonad, value));
 * }
 *
 * private <R> Kind<...> noneT() {
 *   return OPTIONAL_T.widen(OptionalT.none(outerMonad));
 * }
 * }</pre>
 *
 * <h2>Test Coverage Requirements</h2>
 *
 * <p>Each transformer test should cover:
 *
 * <h3>1. Functor Operations (map)</h3>
 *
 * <ul>
 *   <li>Success case: outer present, inner success → applies function
 *   <li>Inner error case: outer present, inner error → propagates error
 *   <li>Outer empty case: outer empty → propagates empty
 * </ul>
 *
 * <h3>2. Applicative Operations (of, ap)</h3>
 *
 * <ul>
 *   <li>Both success → applies function
 *   <li>Function error, value success → propagates function error
 *   <li>Function success, value error → propagates value error
 *   <li>Both error → propagates first error (usually function)
 *   <li>Either outer empty → result outer empty
 * </ul>
 *
 * <h3>3. Monad Operations (flatMap)</h3>
 *
 * <ul>
 *   <li>Success, function returns success → result success
 *   <li>Success, function returns error → result error
 *   <li>Success, function returns outer empty → result outer empty
 *   <li>Inner error → function not called, error propagated
 *   <li>Outer empty → function not called, empty propagated
 *   <li>Function throws exception → exception propagated
 * </ul>
 *
 * <h3>4. MonadError Operations (raiseError, handleErrorWith)</h3>
 *
 * <ul>
 *   <li>raiseError creates inner error in outer success
 *   <li>handleErrorWith recovers from inner error
 *   <li>handleErrorWith ignores success values
 *   <li>handleErrorWith propagates outer empty
 * </ul>
 *
 * <h3>5. Edge Cases</h3>
 *
 * <ul>
 *   <li>Null handling in construction
 *   <li>Null handling in operations
 *   <li>Exception propagation
 *   <li>Chained operations
 *   <li>Type preservation through transformations
 * </ul>
 *
 * <h2>Using Custom Assertions</h2>
 *
 * <h3>EitherT Assertions</h3>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.either_t.EitherTAssert.assertThatEitherT;
 *
 * // Success case
 * assertThatEitherT(result, this::unwrapToOptional)
 *     .isPresentRight()
 *     .hasRightValue("expected");
 *
 * // Error case
 * assertThatEitherT(result, this::unwrapToOptional)
 *     .isPresentLeft()
 *     .hasLeftValue(new TestError("E1"));
 *
 * // Empty outer
 * assertThatEitherT(result, this::unwrapToOptional).isEmpty();
 *
 * // With predicate
 * assertThatEitherT(result, this::unwrapToOptional)
 *     .isPresentRight()
 *     .rightMatches(s -> s.startsWith("prefix"));
 * }</pre>
 *
 * <h3>MaybeT Assertions</h3>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.maybe_t.MaybeTAssert.assertThatMaybeT;
 *
 * // Just case
 * assertThatMaybeT(result, this::unwrapToOptional)
 *     .isPresentJust()
 *     .hasJustValue("expected");
 *
 * // Nothing case
 * assertThatMaybeT(result, this::unwrapToOptional).isPresentNothing();
 *
 * // Empty outer
 * assertThatMaybeT(result, this::unwrapToOptional).isEmpty();
 * }</pre>
 *
 * <h3>OptionalT Assertions</h3>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.optional_t.OptionalTAssert.assertThatOptionalT;
 *
 * // Some case
 * assertThatOptionalT(result, this::unwrapToOptional)
 *     .isPresentSome()
 *     .hasSomeValue("expected");
 *
 * // None case
 * assertThatOptionalT(result, this::unwrapToOptional).isPresentNone();
 *
 * // Empty outer
 * assertThatOptionalT(result, this::unwrapToOptional).isEmpty();
 * }</pre>
 *
 * <h2>Common Patterns</h2>
 *
 * <h3>Testing Three Scenarios</h3>
 *
 * <p>Most operations should test three scenarios:
 *
 * <pre>{@code
 * @Nested
 * @DisplayName("map operation")
 * class MapTests {
 *
 *   @Test
 *   @DisplayName("map should apply function when inner success")
 *   void map_innerSuccess_appliesFunction() {
 *     var input = successValue(10);
 *     var result = transformerMonad.map(Object::toString, input);
 *     assertThat...isPresentSuccess().hasValue("10");
 *   }
 *
 *   @Test
 *   @DisplayName("map should propagate inner error")
 *   void map_innerError_propagatesError() {
 *     var input = errorValue(testError);
 *     var result = transformerMonad.map(Object::toString, input);
 *     assertThat...isPresentError().hasError(testError);
 *   }
 *
 *   @Test
 *   @DisplayName("map should propagate outer empty")
 *   void map_outerEmpty_propagatesEmpty() {
 *     var input = emptyOuter();
 *     var result = transformerMonad.map(Object::toString, input);
 *     assertThat...isEmpty();
 *   }
 * }
 * }</pre>
 *
 * <h2>British Spellings</h2>
 *
 * <p>Use British spellings consistently in all documentation:
 *
 * <ul>
 *   <li>behaviour (not behavior)
 *   <li>standardised (not standardized)
 *   <li>initialise (not initialize)
 *   <li>organisation (not organization)
 *   <li>optimisation (not optimization)
 *   <li>specialised (not specialized)
 * </ul>
 *
 * @see EitherTAssert
 * @see MaybeTAssert
 * @see OptionalTAssert
 * @see TypeClassTestBase
 */
public final class TransformerTestBase {
  private TransformerTestBase() {
    throw new UnsupportedOperationException("Utility class - do not instantiate");
  }
}
