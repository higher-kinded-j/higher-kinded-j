// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.TraverseTraversals;
import org.jspecify.annotations.NullMarked;

/**
 * A type-safe path through a data structure that focuses on zero or one element.
 *
 * <p>AffinePath wraps an {@link Affine} and provides fluent navigation and composition methods. Use
 * this when navigating to values that may or may not exist, such as Optional fields or the result
 * of navigating through a prism.
 *
 * <p>In the Focus DSL hierarchy, AffinePath sits between FocusPath and TraversalPath:
 *
 * <pre>
 *      FocusPath<S, A>     (exactly one element)
 *            │
 *      AffinePath<S, A>    (zero or one element)
 *            │
 *    TraversalPath<S, A>   (zero or more elements)
 * </pre>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Navigate to an optional email field
 * AffinePath<User, String> emailPath = UserFocus.email();
 *
 * // Get the value (may be empty)
 * Optional<String> email = emailPath.getOptional(user);
 *
 * // Set a value (always succeeds)
 * User updated = emailPath.set("new@email.com", user);
 *
 * // Modify only if present
 * User modified = emailPath.modify(String::toLowerCase, user);
 *
 * // Check if present
 * if (emailPath.matches(user)) {
 *     // ...
 * }
 * }</pre>
 *
 * @param <S> the source type (the whole structure)
 * @param <A> the focused value type (the part)
 * @see FocusPath for paths focusing on exactly one element
 * @see TraversalPath for paths focusing on zero or more elements
 * @see FocusPaths for utility methods and optics factories
 */
@NullMarked
public sealed interface AffinePath<S, A> permits AffineFocusPath {

  /**
   * Extracts the focused value if present.
   *
   * @param source the source structure
   * @return Optional containing the value, or empty if not focused
   */
  Optional<A> getOptional(S source);

  /**
   * Creates a new source with the focused value replaced.
   *
   * <p>Unlike a Prism, this operation always succeeds. If the focused element was previously
   * absent, it becomes present with the new value (if the underlying structure supports this).
   *
   * @param value the new value
   * @param source the source structure
   * @return a new structure with the updated value
   */
  S set(A value, S source);

  /**
   * Creates a new source with the focused value transformed, if present.
   *
   * <p>If the focused element is absent, the original structure is returned unchanged.
   *
   * @param f the transformation function
   * @param source the source structure
   * @return a new structure with the modified value, or the original if not focused
   */
  default S modify(Function<A, A> f, S source) {
    return getOptional(source).map(a -> set(f.apply(a), source)).orElse(source);
  }

  /**
   * Checks if this path focuses on a value in the given source.
   *
   * @param source the source structure to test
   * @return true if a value is focused, false otherwise
   */
  default boolean matches(S source) {
    return getOptional(source).isPresent();
  }

  /**
   * Returns the focused value or a default if absent.
   *
   * @param defaultValue the value to return if not focused
   * @param source the source structure
   * @return the focused value or the default
   */
  default A getOrElse(A defaultValue, S source) {
    return getOptional(source).orElse(defaultValue);
  }

  /**
   * Applies a function to the focused value and returns the result in an Optional.
   *
   * @param f the function to apply
   * @param source the source structure
   * @param <B> the result type
   * @return Optional containing the result, or empty if not focused
   */
  default <B> Optional<B> mapOptional(Function<? super A, ? extends B> f, S source) {
    return getOptional(source).map(f);
  }

  /**
   * Transforms the focused value with an effectful function.
   *
   * <p>This method enables effectful modifications such as validation, async operations, or
   * computations that may fail. If this path has no focus, the original source is returned wrapped
   * in the applicative's {@code of}.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * // Validation of optional field
   * AffinePath<User, String> emailPath = UserFocus.optionalEmail();
   *
   * Kind<Validated.Witness, User> result = emailPath.modifyF(
   *     email -> EmailValidator.validate(email),
   *     user,
   *     ValidatedApplicative.INSTANCE
   * );
   *
   * // Async update of optional config value
   * Kind<CompletableFutureKind.Witness, Config> result = configPath.modifyF(
   *     value -> fetchUpdatedValue(value),
   *     config,
   *     CompletableFutureApplicative.INSTANCE
   * );
   * }</pre>
   *
   * @param f the effectful transformation function
   * @param source the source structure
   * @param applicative the applicative instance for the effect type F
   * @param <F> the effect type (e.g., Optional, Validated, IO)
   * @return the modified structure wrapped in the effect
   */
  default <F extends WitnessArity<TypeArity.Unary>> Kind<F, S> modifyF(
      Function<A, Kind<F, A>> f, S source, Applicative<F> applicative) {
    return toAffine().modifyF(f, source, applicative);
  }

  // ===== Composition Methods =====

  /**
   * Composes this path with a lens, producing an AffinePath.
   *
   * <p>The result is an AffinePath because this path may have no focus.
   *
   * @param lens the lens to compose with
   * @param <B> the new focused type
   * @return an AffinePath focusing on the composed target
   */
  <B> AffinePath<S, B> via(Lens<A, B> lens);

  /**
   * Composes this path with a FocusPath.
   *
   * <p>This is a convenience method that extracts the lens from the other path. The result is an
   * AffinePath because this path may have no focus.
   *
   * @param other the FocusPath to compose with
   * @param <B> the new focused type
   * @return an AffinePath focusing on the composed target
   */
  default <B> AffinePath<S, B> via(FocusPath<A, B> other) {
    return via(other.toLens());
  }

  /**
   * Composes this path with another AffinePath.
   *
   * <p>This is a convenience method that extracts the affine from the other path. The result is an
   * AffinePath because both paths may have no focus.
   *
   * @param other the AffinePath to compose with
   * @param <B> the new focused type
   * @return an AffinePath that may or may not focus on a value
   */
  default <B> AffinePath<S, B> via(AffinePath<A, B> other) {
    return via(other.toAffine());
  }

  /**
   * Composes this path with a TraversalPath.
   *
   * <p>This is a convenience method that extracts the traversal from the other path. The result is
   * a TraversalPath because the other path may focus on multiple elements.
   *
   * @param other the TraversalPath to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on multiple elements
   */
  default <B> TraversalPath<S, B> via(TraversalPath<A, B> other) {
    return via(other.toTraversal());
  }

  /**
   * Composes this path with a prism, producing an AffinePath.
   *
   * <p>The result is an AffinePath because both this path and the prism may have no focus.
   *
   * @param prism the prism to compose with
   * @param <B> the new focused type
   * @return an AffinePath that may or may not focus on a value
   */
  <B> AffinePath<S, B> via(Prism<A, B> prism);

  /**
   * Composes this path with an affine, producing an AffinePath.
   *
   * @param affine the affine to compose with
   * @param <B> the new focused type
   * @return an AffinePath that may or may not focus on a value
   */
  <B> AffinePath<S, B> via(Affine<A, B> affine);

  /**
   * Composes this path with an iso, producing an AffinePath.
   *
   * @param iso the iso to compose with
   * @param <B> the new focused type
   * @return an AffinePath focusing on the converted target
   */
  <B> AffinePath<S, B> via(Iso<A, B> iso);

  /**
   * Composes this path with a traversal, producing a TraversalPath.
   *
   * @param traversal the traversal to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on multiple elements
   */
  <B> TraversalPath<S, B> via(Traversal<A, B> traversal);

  // ===== then() Aliases =====

  /**
   * Alias for {@link #via(Lens)}. Composes this path with a lens.
   *
   * <p>This method provides an alternative naming convention familiar to users of other optics
   * libraries.
   *
   * @param lens the lens to compose with
   * @param <B> the new focused type
   * @return an AffinePath focusing on the composed target
   */
  default <B> AffinePath<S, B> then(Lens<A, B> lens) {
    return via(lens);
  }

  /**
   * Alias for {@link #via(FocusPath)}. Composes this path with a FocusPath.
   *
   * @param other the FocusPath to compose with
   * @param <B> the new focused type
   * @return an AffinePath focusing on the composed target
   */
  default <B> AffinePath<S, B> then(FocusPath<A, B> other) {
    return via(other);
  }

  /**
   * Alias for {@link #via(Prism)}. Composes this path with a prism.
   *
   * @param prism the prism to compose with
   * @param <B> the new focused type
   * @return an AffinePath that may or may not focus on a value
   */
  default <B> AffinePath<S, B> then(Prism<A, B> prism) {
    return via(prism);
  }

  /**
   * Alias for {@link #via(Affine)}. Composes this path with an affine.
   *
   * @param affine the affine to compose with
   * @param <B> the new focused type
   * @return an AffinePath that may or may not focus on a value
   */
  default <B> AffinePath<S, B> then(Affine<A, B> affine) {
    return via(affine);
  }

  /**
   * Alias for {@link #via(AffinePath)}. Composes this path with another AffinePath.
   *
   * @param other the AffinePath to compose with
   * @param <B> the new focused type
   * @return an AffinePath that may or may not focus on a value
   */
  default <B> AffinePath<S, B> then(AffinePath<A, B> other) {
    return via(other);
  }

  /**
   * Alias for {@link #via(Iso)}. Composes this path with an iso.
   *
   * @param iso the iso to compose with
   * @param <B> the new focused type
   * @return an AffinePath focusing on the composed target
   */
  default <B> AffinePath<S, B> then(Iso<A, B> iso) {
    return via(iso);
  }

  /**
   * Alias for {@link #via(Traversal)}. Composes this path with a traversal.
   *
   * @param traversal the traversal to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on multiple elements
   */
  default <B> TraversalPath<S, B> then(Traversal<A, B> traversal) {
    return via(traversal);
  }

  /**
   * Alias for {@link #via(TraversalPath)}. Composes this path with a TraversalPath.
   *
   * @param other the TraversalPath to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on multiple elements
   */
  default <B> TraversalPath<S, B> then(TraversalPath<A, B> other) {
    return via(other);
  }

  // ===== Collection Navigation =====

  /**
   * When the focused type is {@code List<E>}, traverses all elements.
   *
   * @param <E> the element type of the list
   * @return a TraversalPath over list elements
   */
  @SuppressWarnings("unchecked")
  default <E> TraversalPath<S, E> each() {
    return via((Traversal<A, E>) FocusPaths.listElements());
  }

  /**
   * Traverses all elements using the provided {@link org.higherkindedj.optics.Each} instance.
   *
   * <p>This method provides type-safe traversal for any container type that has an Each instance.
   *
   * @param eachInstance the Each instance for the focused container type
   * @param <E> the element type within the container
   * @return a TraversalPath focusing on all elements
   * @see org.higherkindedj.optics.Each
   * @see org.higherkindedj.optics.each.EachInstances
   */
  default <E> TraversalPath<S, E> each(Each<A, E> eachInstance) {
    return via(eachInstance.each());
  }

  /**
   * When the focused type is {@code List<E>}, focuses on the element at the specified index.
   *
   * @param index the index to focus on
   * @param <E> the element type of the list
   * @return an AffinePath that may be empty if the index is out of bounds
   */
  @SuppressWarnings("unchecked")
  default <E> AffinePath<S, E> at(int index) {
    return via((Affine<A, E>) FocusPaths.listAt(index));
  }

  /**
   * When the focused type is {@code Map<K, V>}, focuses on the value for the specified key.
   *
   * @param key the key to focus on
   * @param <K> the key type of the map
   * @param <V> the value type of the map
   * @return an AffinePath that may be empty if the key is not present
   */
  @SuppressWarnings("unchecked")
  default <K, V> AffinePath<S, V> atKey(K key) {
    return via((Affine<A, V>) FocusPaths.mapAt(key));
  }

  /**
   * When the focused type is {@code Optional<E>}, unwraps to the inner value.
   *
   * @param <E> the inner type of the Optional
   * @return an AffinePath that may be empty
   */
  @SuppressWarnings("unchecked")
  default <E> AffinePath<S, E> some() {
    return via((Affine<A, E>) FocusPaths.optionalSome());
  }

  /**
   * When the focused type may be null, creates an AffinePath that safely handles null values.
   *
   * <p>This is useful for working with legacy APIs or records that use null to represent absent
   * values instead of {@link java.util.Optional}. The resulting AffinePath treats null as absent
   * (empty Optional) and non-null as present.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Given an AffinePath to an optionally-present nullable field
   * AffinePath<Config, @Nullable String> apiKeyPath = ConfigFocus.apiKey();
   *
   * // Chain with nullable() to handle the null case
   * AffinePath<Config, String> safeApiKey = apiKeyPath.nullable();
   *
   * // getOptional returns empty for both "path doesn't match" and "value is null"
   * Optional<String> result = safeApiKey.getOptional(config);
   * }</pre>
   *
   * @param <E> the non-null element type
   * @return an AffinePath that treats null as absent
   * @see FocusPaths#nullable()
   */
  @SuppressWarnings("unchecked")
  default <E> AffinePath<S, E> nullable() {
    return via((Affine<A, E>) FocusPaths.nullable());
  }

  /**
   * When the optionally-focused value is a traversable container {@code Kind<F, E>}, creates a
   * TraversalPath that focuses on all elements within it.
   *
   * <p>This enables generic traversal over any type with a {@link org.higherkindedj.hkt.Traverse}
   * instance, not just {@code List}. For example, {@code Set}, {@code Tree}, or custom collections.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * // Given: Config with optional Kind<ListKind.Witness, Feature> features field
   * AffinePath<Config, Kind<ListKind.Witness, Feature>> featuresPath = ConfigFocus.features();
   *
   * // Traverse into the List elements when present
   * TraversalPath<Config, Feature> allFeatures = featuresPath.traverseOver(
   *     ListTraverse.INSTANCE
   * );
   *
   * // Now can operate on all features (if present)
   * List<Feature> features = allFeatures.getAll(config);
   * Config updated = allFeatures.modifyAll(Feature::enable, config);
   * }</pre>
   *
   * <p>For standard {@code List<E>} fields, prefer the {@link #each()} method which handles the
   * conversion automatically.
   *
   * @param <F> the witness type of the traversable container
   * @param <E> the element type within the container
   * @param traverse the Traverse instance for the container type
   * @return a TraversalPath focusing on all elements within the container
   * @see #each() for simpler List traversal
   * @see org.higherkindedj.optics.util.TraverseTraversals
   */
  @SuppressWarnings("unchecked")
  default <F extends WitnessArity<TypeArity.Unary>, E> TraversalPath<S, E> traverseOver(
      Traverse<F> traverse) {
    Traversal<Kind<F, E>, E> traversal = TraverseTraversals.forTraverse(traverse);
    return via((Traversal<A, E>) traversal);
  }

  // ===== List Decomposition Methods =====

  /**
   * When the focused type is {@code List<E>}, decomposes into head and tail.
   *
   * <p>This method returns an AffinePath because either this path may have no focus, or the list
   * may be empty.
   *
   * @param <E> the element type of the list
   * @return an AffinePath to a (head, tail) pair
   * @throws ClassCastException if the focused type {@code A} is not a {@code List}
   * @see #headTail() for Java-familiar alias
   */
  @SuppressWarnings("unchecked")
  default <E> AffinePath<S, Pair<E, List<E>>> cons() {
    return via((Prism<A, Pair<E, List<E>>>) (Prism<?, ?>) FocusPaths.<E>listCons());
  }

  /**
   * Alias for {@link #cons()} using Java-familiar naming.
   *
   * @param <E> the element type of the list
   * @return an AffinePath to a (head, tail) pair
   */
  default <E> AffinePath<S, Pair<E, List<E>>> headTail() {
    return cons();
  }

  /**
   * When the focused type is {@code List<E>}, decomposes into init and last.
   *
   * <p>This method returns an AffinePath because either this path may have no focus, or the list
   * may be empty.
   *
   * @param <E> the element type of the list
   * @return an AffinePath to an (init, last) pair
   * @throws ClassCastException if the focused type {@code A} is not a {@code List}
   * @see #initLast() for Java-familiar alias
   */
  @SuppressWarnings("unchecked")
  default <E> AffinePath<S, Pair<List<E>, E>> snoc() {
    return via((Prism<A, Pair<List<E>, E>>) (Prism<?, ?>) FocusPaths.<E>listSnoc());
  }

  /**
   * Alias for {@link #snoc()} using Java-familiar naming.
   *
   * @param <E> the element type of the list
   * @return an AffinePath to an (init, last) pair
   */
  default <E> AffinePath<S, Pair<List<E>, E>> initLast() {
    return snoc();
  }

  /**
   * When the focused type is {@code List<E>}, focuses on the head (first element).
   *
   * @param <E> the element type of the list
   * @return an AffinePath to the head element
   */
  @SuppressWarnings("unchecked")
  default <E> AffinePath<S, E> head() {
    return via((Affine<A, E>) (Affine<?, ?>) FocusPaths.<E>listHead());
  }

  /**
   * When the focused type is {@code List<E>}, focuses on the last element.
   *
   * @param <E> the element type of the list
   * @return an AffinePath to the last element
   */
  @SuppressWarnings("unchecked")
  default <E> AffinePath<S, E> last() {
    return via((Affine<A, E>) (Affine<?, ?>) FocusPaths.<E>listLast());
  }

  /**
   * When the focused type is {@code List<E>}, focuses on the tail (all elements except first).
   *
   * @param <E> the element type of the list
   * @return an AffinePath to the tail
   */
  @SuppressWarnings("unchecked")
  default <E> AffinePath<S, List<E>> tail() {
    return via((Affine<A, List<E>>) (Affine<?, ?>) FocusPaths.<E>listTail());
  }

  /**
   * When the focused type is {@code List<E>}, focuses on the init (all elements except last).
   *
   * @param <E> the element type of the list
   * @return an AffinePath to the init
   */
  @SuppressWarnings("unchecked")
  default <E> AffinePath<S, List<E>> init() {
    return via((Affine<A, List<E>>) (Affine<?, ?>) FocusPaths.<E>listInit());
  }

  // ===== Conversion Methods =====

  /**
   * Extracts the underlying affine.
   *
   * @return the wrapped Affine
   */
  Affine<S, A> toAffine();

  /**
   * Views this path as a TraversalPath.
   *
   * <p>This is always valid because an AffinePath (zero or one) is a special case of TraversalPath
   * (zero or more).
   *
   * @return a TraversalPath view of this path
   */
  default TraversalPath<S, A> asTraversal() {
    return TraversalPath.of(toAffine().asTraversal());
  }

  /**
   * Views this path as a Fold.
   *
   * @return a Fold view of the underlying affine
   */
  default Fold<S, A> asFold() {
    return toAffine().asFold();
  }

  // ===== Debugging =====

  /**
   * Creates a new AffinePath that invokes an observer during get operations.
   *
   * <p>This method is useful for debugging complex path navigations by logging or inspecting values
   * as they are accessed. The observer receives the source and the focused value (if present).
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * AffinePath<Config, String> debugPath = ConfigFocus.apiKey()
   *     .traced((config, key) -> log.debug("Accessing API key: {} from {}", key, config));
   *
   * // Every getOptional() call will now log the accessed value
   * Optional<String> key = debugPath.getOptional(config);
   * }</pre>
   *
   * @param observer a consumer that receives the source and focused value during get operations
   * @return a new AffinePath that invokes the observer
   */
  default AffinePath<S, A> traced(BiConsumer<S, Optional<A>> observer) {
    AffinePath<S, A> self = this;
    return AffinePath.of(
        Affine.of(
            s -> {
              Optional<A> result = self.getOptional(s);
              observer.accept(s, result);
              return result;
            },
            (s, a) -> self.set(a, s)));
  }

  // ===== Effect Path Bridge Methods =====

  /**
   * Extracts the optionally focused value and wraps it in a {@link MaybePath}.
   *
   * <p>This bridges from the optics domain to the effect domain, translating the Optional semantics
   * of AffinePath to the Maybe semantics of MaybePath. If this path focuses on a value, the result
   * is Just; if it doesn't match, the result is Nothing.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * AffinePath<Config, String> apiKeyPath = ConfigFocus.optionalApiKey();
   * Config config = new Config(Optional.of("secret-key"));
   *
   * // Extract and process with effect operations
   * MaybePath<String> result = apiKeyPath.toMaybePath(config)
   *     .filter(key -> key.startsWith("secret"))
   *     .map(String::toUpperCase);
   * }</pre>
   *
   * @param source the source structure
   * @return a MaybePath that is Just if the affine matches, Nothing otherwise
   */
  default MaybePath<A> toMaybePath(S source) {
    return getOptional(source).map(Path::just).orElseGet(Path::nothing);
  }

  /**
   * Extracts the optionally focused value and wraps it in an {@link EitherPath}.
   *
   * <p>This bridges from the optics domain to the effect domain for error-handling computations. If
   * this path focuses on a value, the result is Right; if it doesn't match, the result is Left with
   * the provided error.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * AffinePath<User, String> emailPath = UserFocus.optionalEmail();
   * User user = new User("Alice", Optional.empty());
   *
   * // Extract with explicit error handling
   * EitherPath<String, String> result = emailPath.toEitherPath(user, "Email is required")
   *     .via(email -> validateEmail(email));
   * }</pre>
   *
   * @param source the source structure
   * @param errorIfAbsent the error to use if the affine doesn't match
   * @param <E> the error type
   * @return an EitherPath containing Right if matched, Left otherwise
   */
  default <E> EitherPath<E, A> toEitherPath(S source, E errorIfAbsent) {
    return getOptional(source)
        .<EitherPath<E, A>>map(Path::right)
        .orElseGet(() -> Path.left(errorIfAbsent));
  }

  /**
   * Extracts the optionally focused value and wraps it in a {@link TryPath}.
   *
   * <p>This bridges from the optics domain to the effect domain for exception-handling
   * computations. If this path focuses on a value, the result is Success; if it doesn't match, the
   * result is Failure with the exception provided by the supplier.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * AffinePath<Config, String> urlPath = ConfigFocus.optionalUrl();
   * Config config = new Config(Optional.empty());
   *
   * // Extract with exception on absence
   * TryPath<URI> result = urlPath.toTryPath(config,
   *         () -> new IllegalStateException("URL is required"))
   *     .via(url -> Path.tryOf(() -> new URI(url)));
   * }</pre>
   *
   * @param source the source structure
   * @param exceptionIfAbsent supplies the exception if the affine doesn't match
   * @return a TryPath containing Success if matched, Failure otherwise
   */
  default TryPath<A> toTryPath(S source, Supplier<? extends Throwable> exceptionIfAbsent) {
    return getOptional(source)
        .map(Path::success)
        .orElseGet(() -> Path.failure(exceptionIfAbsent.get()));
  }

  /**
   * Extracts the optionally focused value and wraps it in an {@link OptionalPath}.
   *
   * <p>This bridges from the optics domain to the effect domain, preserving the Optional semantics
   * directly. This is useful when you want to work with java.util.Optional via the Path API.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * AffinePath<User, String> nicknamePath = UserFocus.optionalNickname();
   * User user = new User("Alice", Optional.of("Ali"));
   *
   * // Extract and process
   * OptionalPath<String> result = nicknamePath.toOptionalPath(user)
   *     .map(String::toLowerCase);
   * }</pre>
   *
   * @param source the source structure
   * @return an OptionalPath containing the value if the affine matches
   */
  default OptionalPath<A> toOptionalPath(S source) {
    return Path.optional(getOptional(source));
  }

  // ===== Factory Methods =====

  /**
   * Creates an AffinePath from an affine.
   *
   * @param affine the affine to wrap
   * @param <S> the source type
   * @param <A> the focused type
   * @return a new AffinePath
   */
  static <S, A> AffinePath<S, A> of(Affine<S, A> affine) {
    return new AffineFocusPath<>(affine);
  }

  /**
   * Creates an AffinePath that focuses on instances of a specific subclass.
   *
   * <p>This is useful for working with sealed interfaces or class hierarchies, allowing you to
   * focus on a specific variant of a sum type.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * sealed interface Shape permits Circle, Rectangle {}
   * record Circle(double radius) implements Shape {}
   * record Rectangle(double width, double height) implements Shape {}
   *
   * // Focus on Circle instances only
   * AffinePath<Shape, Circle> circlePath = AffinePath.instanceOf(Circle.class);
   *
   * // Get the Circle if the shape is one
   * Optional<Circle> maybeCircle = circlePath.getOptional(someShape);
   *
   * // Compose with further navigation
   * AffinePath<Shape, Double> radiusPath = circlePath.via(CircleFocus.radius());
   * }</pre>
   *
   * @param subclass the class of the subtype to focus on
   * @param <S> the source (supertype)
   * @param <A> the focused subtype
   * @return an AffinePath that focuses only on instances of the specified subclass
   */
  static <S, A extends S> AffinePath<S, A> instanceOf(Class<A> subclass) {
    Affine<S, A> affine =
        Affine.of(
            s -> subclass.isInstance(s) ? Optional.of(subclass.cast(s)) : Optional.empty(),
            (s, a) -> a);
    return of(affine);
  }

  /**
   * Creates an AffinePath for a nullable field with implicit null-safety.
   *
   * <p>This is a convenience factory for creating paths to fields that may be null. The path treats
   * null values as absent (empty Optional), providing seamless integration with the Focus DSL for
   * legacy APIs that use null instead of {@link Optional}.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * record LegacyUser(String name, @Nullable String nickname) {}
   *
   * // Create an AffinePath directly for the nullable field
   * AffinePath<LegacyUser, String> nicknamePath = AffinePath.ofNullable(
   *     LegacyUser::nickname,
   *     (user, nickname) -> new LegacyUser(user.name(), nickname)
   * );
   *
   * // Use like any AffinePath
   * LegacyUser user = new LegacyUser("Alice", null);
   * Optional<String> result = nicknamePath.getOptional(user);  // Optional.empty()
   *
   * LegacyUser updated = nicknamePath.set("Ally", user);
   * // LegacyUser[name=Alice, nickname=Ally]
   * }</pre>
   *
   * @param getter extracts the potentially-null value from the source
   * @param setter creates a new source with the value set (receives non-null value)
   * @param <S> the source type
   * @param <A> the focused type (non-null)
   * @return an AffinePath that handles null safely
   */
  @SuppressWarnings("nullness") // Intentionally working with nullable values
  static <S, A> AffinePath<S, A> ofNullable(Function<S, A> getter, BiFunction<S, A, S> setter) {
    return of(Affine.of(s -> Optional.ofNullable(getter.apply(s)), (s, a) -> setter.apply(s, a)));
  }
}
