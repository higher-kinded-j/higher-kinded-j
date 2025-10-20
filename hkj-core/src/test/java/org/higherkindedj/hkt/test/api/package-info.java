/**
 * Hierarchical, progressive disclosure test API for type class implementations.
 *
 * <p>This package provides a fluent, stage-based approach to testing type class implementations
 * where each step reveals only contextually relevant options through IDE autocomplete.
 *
 * <h2>Design Philosophy</h2>
 *
 * <p>The API follows these core principles:
 *
 * <ul>
 *   <li><strong>Progressive Disclosure:</strong> Each stage shows only relevant next steps
 *   <li><strong>Type Safety:</strong> Impossible to skip required parameters
 *   <li><strong>Hierarchical Structure:</strong> Mirrors type class hierarchy
 *   <li><strong>Clear Error Messages:</strong> Helpful guidance when configuration is incomplete
 *   <li><strong>Composability:</strong> Easy to test complex type class hierarchies
 * </ul>
 *
 * <h2>Entry Point</h2>
 *
 * <p>All testing begins with {@link org.higherkindedj.hkt.test.api.TypeClassTest}:
 *
 * <pre>{@code
 * TypeClassTest.functor(MyFunctor.class)
 *     .instance(functor)
 *     .withKind(validKind)
 *     .withMapper(mapper)
 *     .testAll();
 * }</pre>
 *
 * <h2>Supported Type Classes</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.test.api.typeclass.functor.FunctorTestStage} - Functor testing
 *   <li>{@link org.higherkindedj.hkt.test.api.typeclass.applicative.ApplicativeTestStage} -
 *       Applicative testing
 *   <li>{@link org.higherkindedj.hkt.test.api.typeclass.monad.MonadTestStage} - Monad testing
 *   <li>{@link org.higherkindedj.hkt.test.api.typeclass.monaderror.MonadErrorTestStage} -
 *       MonadError testing
 *   <li>{@link org.higherkindedj.hkt.test.api.typeclass.traverse.TraverseTestStage} - Traverse
 *       testing
 *   <li>{@link org.higherkindedj.hkt.test.api.typeclass.foldable.FoldableTestStage} - Foldable
 *       testing
 *   <li>{@link org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage} - KindHelper
 *       testing
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Simple Functor Test</h3>
 *
 * <pre>{@code
 * @Test
 * void testFunctor() {
 *     TypeClassTest.functor(MyFunctor.class)
 *         .instance(functor)
 *         .withKind(validKind)
 *         .withMapper(INT_TO_STRING)
 *         .testAll();
 * }
 * }</pre>
 *
 * <h3>Comprehensive MonadError Test with Validation</h3>
 *
 * <pre>{@code
 * @Test
 * void testMonadError() {
 *     TypeClassTest.monadError(EitherMonad.class)
 *         .instance(monadError)
 *         .withKind(validKind)
 *         .withMonadOperations(mapper, flatMapper, functionKind)
 *         .withErrorHandling(handler, fallback)
 *         .withLawsTesting(testValue, testFunction, chainFunction, equality)
 *         .configureValidation()
 *             .useInheritanceValidation(
 *                 EitherFunctor.class,
 *                 EitherMonad.class,
 *                 EitherMonad.class,
 *                 EitherMonad.class)
 *             .testAll();
 * }
 * }</pre>
 *
 * <h3>Selective Testing</h3>
 *
 * <pre>{@code
 * @Test
 * void testOperationsOnly() {
 *     TypeClassTest.monad(MyMonad.class)
 *         .instance(monad)
 *         .withKind(validKind)
 *         .withMonadOperations(mapper, flatMapper, functionKind)
 *         .selectTests()
 *             .onlyOperations()
 *             .test();
 * }
 * }</pre>
 *
 * <h3>KindHelper Testing</h3>
 *
 * <pre>{@code
 * @Test
 * void testKindHelper() {
 *     TypeClassTest.kindHelper(Either.class)
 *         .helper(EITHER)
 *         .withInstance(Either.right("test"))
 *         .testAll();
 * }
 * }</pre>
 *
 * <h2>Migration from Legacy API</h2>
 *
 * <p>The legacy {@link org.higherkindedj.hkt.test.patterns.TypeClassTestPattern} API is deprecated.
 * Migrate to the new hierarchical API:
 *
 * <h3>Before (Legacy)</h3>
 *
 * <pre>{@code
 * TypeClassTestPattern.testCompleteFunctor(
 *     functor,
 *     MyFunctor.class,
 *     validKind,
 *     mapper,
 *     equalityChecker);
 * }</pre>
 *
 * <h3>After (New API)</h3>
 *
 * <pre>{@code
 * TypeClassTest.functor(MyFunctor.class)
 *     .instance(functor)
 *     .withKind(validKind)
 *     .withMapper(mapper)
 *     .withEqualityChecker(equalityChecker)
 *     .testAll();
 * }</pre>
 *
 * <h2>Key Benefits</h2>
 *
 * <ul>
 *   <li><strong>Better IDE Support:</strong> Progressive disclosure provides clear next steps
 *   <li><strong>Type Safety:</strong> Compile-time guarantees for required parameters
 *   <li><strong>Flexibility:</strong> Easy to run complete suites or selective tests
 *   <li><strong>Consistency:</strong> Uniform API across all type classes
 *   <li><strong>Maintainability:</strong> Changes to test patterns affect all implementations
 * </ul>
 *
 * <h2>Architecture</h2>
 *
 * <p>The API uses a stage-based pattern where each stage:
 *
 * <ol>
 *   <li>Validates previous stage's requirements
 *   <li>Provides methods for next configuration step
 *   <li>Offers optional execution shortcuts
 *   <li>Allows selective test execution
 * </ol>
 *
 * <p>Stages are immutable where possible, creating new instances for method chaining. Internal
 * executor classes handle the actual test execution by delegating to {@link
 * org.higherkindedj.hkt.test.api.typeclass.internal.TestMethodRegistry}.
 *
 * @see org.higherkindedj.hkt.test.api.TypeClassTest
 * @see org.higherkindedj.hkt.test.patterns.TypeClassTestPattern
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.hkt.test.api;
