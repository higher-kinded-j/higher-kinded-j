// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.TraverseTraversals;
import org.jspecify.annotations.NullMarked;

/**
 * A type-safe path through a data structure that focuses on exactly one element.
 *
 * <p>FocusPath wraps a {@link Lens} and provides fluent navigation and composition methods. Use
 * this when navigating to fields that are guaranteed to exist.
 *
 * <p>In the Focus DSL hierarchy, FocusPath is the most specific type:
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
 * // Create from a lens
 * FocusPath<User, String> namePath = FocusPath.of(UserLenses.name());
 *
 * // Get a value
 * String name = namePath.get(user);
 *
 * // Set a value
 * User updated = namePath.set("Bob", user);
 *
 * // Modify a value
 * User modified = namePath.modify(String::toUpperCase, user);
 *
 * // Compose with another lens
 * FocusPath<User, String> cityPath = namePath.via(AddressLenses.city());
 * }</pre>
 *
 * @param <S> the source type (the whole structure)
 * @param <A> the focused value type (the part)
 * @see AffinePath for paths focusing on zero or one element
 * @see TraversalPath for paths focusing on zero or more elements
 * @see FocusPaths for utility methods and optics factories
 */
@NullMarked
public sealed interface FocusPath<S, A> permits LensFocusPath {

  /**
   * Extracts the focused value from the source.
   *
   * <p>This operation always succeeds because a FocusPath guarantees exactly one focused element.
   *
   * @param source the source structure
   * @return the focused value
   */
  A get(S source);

  /**
   * Creates a new source with the focused value replaced.
   *
   * <p>This operation is immutable; the original source is not modified.
   *
   * @param value the new value
   * @param source the source structure
   * @return a new structure with the updated value
   */
  S set(A value, S source);

  /**
   * Creates a new source with the focused value transformed.
   *
   * <p>This is equivalent to {@code set(f.apply(get(source)), source)}.
   *
   * @param f the transformation function
   * @param source the source structure
   * @return a new structure with the modified value
   */
  default S modify(Function<A, A> f, S source) {
    return set(f.apply(get(source)), source);
  }

  /**
   * Transforms the focused value with an effectful function.
   *
   * <p>This method enables effectful modifications such as validation, async operations, or
   * computations that may fail. The effect is captured in the functor F.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * // Validation example
   * FocusPath<User, String> emailPath = UserFocus.email();
   *
   * Kind<Validated.Witness, User> result = emailPath.modifyF(
   *     email -> EmailValidator.validate(email),
   *     user,
   *     ValidatedFunctor.INSTANCE
   * );
   *
   * // Optional example (transformation that may fail)
   * Kind<OptionalKind.Witness, Config> result = configPath.modifyF(
   *     value -> parseConfig(value),  // Returns Optional
   *     config,
   *     OptionalFunctor.INSTANCE
   * );
   * }</pre>
   *
   * @param f the effectful transformation function
   * @param source the source structure
   * @param functor the functor instance for the effect type F
   * @param <F> the effect type (e.g., Optional, Validated, IO)
   * @return the modified structure wrapped in the effect
   */
  default <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S source, Functor<F> functor) {
    return toLens().modifyF(f, source, functor);
  }

  // ===== Composition Methods =====

  /**
   * Composes this path with a lens to focus deeper into the structure.
   *
   * <p>The result is a FocusPath because both this path and the lens focus on exactly one element.
   *
   * @param lens the lens to compose with
   * @param <B> the new focused type
   * @return a new FocusPath focusing on the composed target
   */
  <B> FocusPath<S, B> via(Lens<A, B> lens);

  /**
   * Composes this path with another FocusPath.
   *
   * <p>This is a convenience method that extracts the lens from the other path. The result is a
   * FocusPath because both paths focus on exactly one element.
   *
   * @param other the FocusPath to compose with
   * @param <B> the new focused type
   * @return a new FocusPath focusing on the composed target
   */
  default <B> FocusPath<S, B> via(FocusPath<A, B> other) {
    return via(other.toLens());
  }

  /**
   * Composes this path with an AffinePath.
   *
   * <p>This is a convenience method that extracts the affine from the other path. The result is an
   * AffinePath because the other path may have no focus.
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
   * Composes this path with an iso to focus through a type conversion.
   *
   * <p>The result is a FocusPath because an iso is a lossless, reversible transformation.
   *
   * @param iso the iso to compose with
   * @param <B> the new focused type
   * @return a new FocusPath focusing on the composed target
   */
  <B> FocusPath<S, B> via(Iso<A, B> iso);

  /**
   * Composes this path with a prism, producing an AffinePath.
   *
   * <p>The result is an AffinePath because the prism may not match (zero or one focus).
   *
   * @param prism the prism to compose with
   * @param <B> the new focused type
   * @return an AffinePath that may or may not focus on a value
   */
  <B> AffinePath<S, B> via(Prism<A, B> prism);

  /**
   * Composes this path with an affine, producing an AffinePath.
   *
   * <p>The result is an AffinePath because the affine may have no focus.
   *
   * @param affine the affine to compose with
   * @param <B> the new focused type
   * @return an AffinePath that may or may not focus on a value
   */
  <B> AffinePath<S, B> via(Affine<A, B> affine);

  /**
   * Composes this path with a traversal, producing a TraversalPath.
   *
   * <p>The result is a TraversalPath because the traversal may focus on multiple elements.
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
   * @return a new FocusPath focusing on the composed target
   */
  default <B> FocusPath<S, B> then(Lens<A, B> lens) {
    return via(lens);
  }

  /**
   * Alias for {@link #via(FocusPath)}. Composes this path with another FocusPath.
   *
   * @param other the FocusPath to compose with
   * @param <B> the new focused type
   * @return a new FocusPath focusing on the composed target
   */
  default <B> FocusPath<S, B> then(FocusPath<A, B> other) {
    return via(other);
  }

  /**
   * Alias for {@link #via(Iso)}. Composes this path with an iso.
   *
   * @param iso the iso to compose with
   * @param <B> the new focused type
   * @return a new FocusPath focusing on the composed target
   */
  default <B> FocusPath<S, B> then(Iso<A, B> iso) {
    return via(iso);
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
   * Alias for {@link #via(AffinePath)}. Composes this path with an AffinePath.
   *
   * @param other the AffinePath to compose with
   * @param <B> the new focused type
   * @return an AffinePath that may or may not focus on a value
   */
  default <B> AffinePath<S, B> then(AffinePath<A, B> other) {
    return via(other);
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
   * <p>This method requires that the focused type {@code A} is a {@code List<E>}. It returns a
   * TraversalPath that focuses on each element in the list.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // departments() returns FocusPath<Company, List<Department>>
   * TraversalPath<Company, Department> allDepts = CompanyFocus.departmentList().each();
   * }</pre>
   *
   * @param <E> the element type of the list
   * @return a TraversalPath over list elements
   * @throws ClassCastException if the focused type {@code A} is not a {@code List}
   */
  @SuppressWarnings("unchecked")
  default <E> TraversalPath<S, E> each() {
    // This cast is safe when A is List<E>
    return via((Traversal<A, E>) FocusPaths.listElements());
  }

  /**
   * When the focused type is {@code List<E>}, focuses on the element at the specified index.
   *
   * <p>This method returns an AffinePath because the index may be out of bounds.
   *
   * @param index the index to focus on
   * @param <E> the element type of the list
   * @return an AffinePath that may be empty if the index is out of bounds
   * @throws ClassCastException if the focused type {@code A} is not a {@code List}
   */
  @SuppressWarnings("unchecked")
  default <E> AffinePath<S, E> at(int index) {
    return via((Affine<A, E>) FocusPaths.listAt(index));
  }

  /**
   * When the focused type is {@code Map<K, V>}, focuses on the value for the specified key.
   *
   * <p>This method returns an AffinePath because the key may not exist in the map.
   *
   * @param key the key to focus on
   * @param <K> the key type of the map
   * @param <V> the value type of the map
   * @return an AffinePath that may be empty if the key is not present
   * @throws ClassCastException if the focused type {@code A} is not a {@code Map}
   */
  @SuppressWarnings("unchecked")
  default <K, V> AffinePath<S, V> atKey(K key) {
    return via((Affine<A, V>) FocusPaths.mapAt(key));
  }

  /**
   * When the focused type is {@code Optional<E>}, unwraps to the inner value.
   *
   * <p>This method returns an AffinePath because the Optional may be empty.
   *
   * @param <E> the inner type of the Optional
   * @return an AffinePath that may be empty if the Optional is empty
   * @throws ClassCastException if the focused type {@code A} is not an {@code Optional}
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
   * record LegacyUser(String name, @Nullable String nickname) {}
   *
   * // Generate focus for the record (nickname is nullable)
   * FocusPath<LegacyUser, @Nullable String> nicknamePath = LegacyUserFocus.nickname();
   *
   * // Chain with nullable() to get null-safe access
   * AffinePath<LegacyUser, String> safeNickname = nicknamePath.nullable();
   *
   * // Now use it like any other AffinePath
   * LegacyUser user = new LegacyUser("Alice", null);
   * Optional<String> result = safeNickname.getOptional(user);  // Optional.empty()
   *
   * LegacyUser withNick = new LegacyUser("Bob", "Bobby");
   * Optional<String> present = safeNickname.getOptional(withNick);  // Optional.of("Bobby")
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
   * When the focused value is a traversable container {@code Kind<F, E>}, creates a TraversalPath
   * that focuses on all elements within it.
   *
   * <p>This enables generic traversal over any type with a {@link org.higherkindedj.hkt.Traverse}
   * instance, not just {@code List}. For example, {@code Set}, {@code Tree}, or custom collections.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * // Given: User with Kind<ListKind.Witness, Role> roles field
   * FocusPath<User, Kind<ListKind.Witness, Role>> rolesPath = UserFocus.roles();
   *
   * // Traverse into the List elements using Traverse type class
   * TraversalPath<User, Role> allRoles = rolesPath.traverseOver(
   *     ListTraverse.INSTANCE
   * );
   *
   * // Now can operate on all roles
   * List<Role> roles = allRoles.getAll(user);
   * User updated = allRoles.modifyAll(Role::promote, user);
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
  default <F, E> TraversalPath<S, E> traverseOver(Traverse<F> traverse) {
    Traversal<Kind<F, E>, E> traversal = TraverseTraversals.forTraverse(traverse);
    return via((Traversal<A, E>) traversal);
  }

  // ===== Conversion Methods =====

  /**
   * Extracts the underlying lens.
   *
   * @return the wrapped Lens
   */
  Lens<S, A> toLens();

  /**
   * Views this path as an AffinePath.
   *
   * <p>This is always valid because a FocusPath (exactly one) is a special case of AffinePath (zero
   * or one).
   *
   * @return an AffinePath view of this path
   */
  default AffinePath<S, A> asAffine() {
    Lens<S, A> lens = toLens();
    return AffinePath.of(Affine.of(s -> Optional.of(lens.get(s)), (s, a) -> lens.set(a, s)));
  }

  /**
   * Views this path as a TraversalPath.
   *
   * <p>This is always valid because a FocusPath (exactly one) is a special case of TraversalPath
   * (zero or more).
   *
   * @return a TraversalPath view of this path
   */
  default TraversalPath<S, A> asTraversal() {
    return TraversalPath.of(toLens().asTraversal());
  }

  /**
   * Views this path as a Fold.
   *
   * @return a Fold view of the underlying lens
   */
  default Fold<S, A> asFold() {
    return toLens().asFold();
  }

  // ===== Debugging =====

  /**
   * Creates a new FocusPath that invokes an observer during get operations.
   *
   * <p>This method is useful for debugging complex path navigations by logging or inspecting values
   * as they are accessed.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * FocusPath<Company, String> debugPath = CompanyFocus.ceo().name()
   *     .traced((company, name) -> log.debug("Accessing CEO name: {} from {}", name, company));
   *
   * // Every get() call will now log the accessed value
   * String name = debugPath.get(company);
   * }</pre>
   *
   * @param observer a consumer that receives the source and focused value during get operations
   * @return a new FocusPath that invokes the observer
   */
  default FocusPath<S, A> traced(BiConsumer<S, A> observer) {
    FocusPath<S, A> self = this;
    return FocusPath.of(
        Lens.of(
            s -> {
              A a = self.get(s);
              observer.accept(s, a);
              return a;
            },
            (s, a) -> self.set(a, s)));
  }

  // ===== Factory Methods =====

  /**
   * Creates a FocusPath from a lens.
   *
   * @param lens the lens to wrap
   * @param <S> the source type
   * @param <A> the focused type
   * @return a new FocusPath
   */
  static <S, A> FocusPath<S, A> of(Lens<S, A> lens) {
    return new LensFocusPath<>(lens);
  }

  /**
   * Creates an identity FocusPath that focuses on the source itself.
   *
   * <p>This is useful as a starting point for path composition.
   *
   * @param <S> the source/target type
   * @return an identity FocusPath
   */
  static <S> FocusPath<S, S> identity() {
    return of(Lens.of(Function.identity(), (s, a) -> a));
  }
}
