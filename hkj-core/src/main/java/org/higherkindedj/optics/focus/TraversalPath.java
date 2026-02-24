// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.focus;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstApplicative;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.constant.ConstKindHelper;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.vtask.Par;
import org.higherkindedj.hkt.vtask.VTask;
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
 * A type-safe path through a data structure that focuses on zero or more elements.
 *
 * <p>TraversalPath wraps a {@link Traversal} and provides fluent navigation and composition
 * methods. Use this when navigating to collections or when the path may focus on multiple values.
 *
 * <p>In the Focus DSL hierarchy, TraversalPath is the most general type:
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
 * // Navigate to all employees in a company
 * TraversalPath<Company, Employee> employeesPath = CompanyFocus.departments().employees();
 *
 * // Get all values
 * List<Employee> employees = employeesPath.getAll(company);
 *
 * // Get the first value
 * Optional<Employee> first = employeesPath.preview(company);
 *
 * // Set all values to the same value
 * Company updated = employeesPath.setAll(defaultEmployee, company);
 *
 * // Modify all values
 * Company modified = employeesPath.modifyAll(Employee::promote, company);
 *
 * // Filter to specific elements
 * TraversalPath<Company, Employee> seniors = employeesPath.filter(e -> e.age() > 50);
 * }</pre>
 *
 * @param <S> the source type (the whole structure)
 * @param <A> the focused value type (the parts)
 * @see FocusPath for paths focusing on exactly one element
 * @see AffinePath for paths focusing on zero or one element
 * @see FocusPaths for utility methods and optics factories
 */
@NullMarked
public sealed interface TraversalPath<S, A> permits TraversalFocusPath, TracedTraversalFocusPath {

  /**
   * Extracts all focused values from the source.
   *
   * @param source the source structure
   * @return list of all focused values (may be empty)
   */
  List<A> getAll(S source);

  /**
   * Extracts the first focused value if any.
   *
   * @param source the source structure
   * @return Optional containing the first value, or empty if no elements are focused
   */
  default Optional<A> preview(S source) {
    List<A> all = getAll(source);
    return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
  }

  /**
   * Creates a new source with all focused values replaced by the given value.
   *
   * @param value the new value for all focused elements
   * @param source the source structure
   * @return a new structure with all focused values updated
   */
  S setAll(A value, S source);

  /**
   * Creates a new source with all focused values transformed.
   *
   * @param f the transformation function
   * @param source the source structure
   * @return a new structure with all focused values modified
   */
  S modifyAll(Function<A, A> f, S source);

  /**
   * Counts the number of focused elements.
   *
   * @param source the source structure
   * @return the number of focused elements
   */
  default int count(S source) {
    return getAll(source).size();
  }

  /**
   * Checks if the traversal focuses on no elements.
   *
   * @param source the source structure
   * @return true if no elements are focused
   */
  default boolean isEmpty(S source) {
    return getAll(source).isEmpty();
  }

  /**
   * Checks if any focused element matches the predicate.
   *
   * @param predicate the condition to test
   * @param source the source structure
   * @return true if any element matches
   */
  default boolean exists(Predicate<A> predicate, S source) {
    return getAll(source).stream().anyMatch(predicate);
  }

  /**
   * Checks if all focused elements match the predicate.
   *
   * <p>Returns true if there are no focused elements (vacuously true).
   *
   * @param predicate the condition to test
   * @param source the source structure
   * @return true if all elements match (or there are no elements)
   */
  default boolean all(Predicate<A> predicate, S source) {
    return getAll(source).stream().allMatch(predicate);
  }

  /**
   * Finds the first focused element matching the predicate.
   *
   * @param predicate the condition to test
   * @param source the source structure
   * @return Optional containing the first matching element, or empty
   */
  default Optional<A> find(Predicate<A> predicate, S source) {
    return getAll(source).stream().filter(predicate).findFirst();
  }

  // ===== Filtering =====

  /**
   * Creates a new TraversalPath that only focuses on elements matching the predicate.
   *
   * <p>Elements that don't match are preserved unchanged during modifications but excluded from
   * queries like {@code getAll}.
   *
   * @param predicate the filter condition
   * @return a new TraversalPath focusing only on matching elements
   */
  TraversalPath<S, A> filter(Predicate<A> predicate);

  // ===== Composition Methods =====

  /**
   * Composes this path with a lens, producing a TraversalPath.
   *
   * <p>For each element this traversal focuses on, the lens focuses on a field of that element.
   *
   * @param lens the lens to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on the composed targets
   */
  <B> TraversalPath<S, B> via(Lens<A, B> lens);

  /**
   * Composes this path with a FocusPath.
   *
   * <p>This is a convenience method that extracts the lens from the other path. For each element
   * this traversal focuses on, the FocusPath focuses on a field of that element.
   *
   * @param other the FocusPath to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on the composed targets
   */
  default <B> TraversalPath<S, B> via(FocusPath<A, B> other) {
    return via(other.toLens());
  }

  /**
   * Composes this path with an AffinePath.
   *
   * <p>This is a convenience method that extracts the affine from the other path. For each element
   * this traversal focuses on, the AffinePath optionally focuses on a nested value.
   *
   * @param other the AffinePath to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on the composed targets
   */
  default <B> TraversalPath<S, B> via(AffinePath<A, B> other) {
    return via(other.toAffine().asTraversal());
  }

  /**
   * Composes this path with another TraversalPath.
   *
   * <p>This is a convenience method that extracts the traversal from the other path. For each
   * element this traversal focuses on, the other TraversalPath may focus on multiple nested
   * elements.
   *
   * @param other the TraversalPath to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on all nested targets
   */
  default <B> TraversalPath<S, B> via(TraversalPath<A, B> other) {
    return via(other.toTraversal());
  }

  /**
   * Composes this path with a prism, producing a TraversalPath.
   *
   * <p>For each element this traversal focuses on, the prism optionally matches.
   *
   * @param prism the prism to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on matching elements
   */
  <B> TraversalPath<S, B> via(Prism<A, B> prism);

  /**
   * Composes this path with an affine, producing a TraversalPath.
   *
   * @param affine the affine to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on the composed targets
   */
  <B> TraversalPath<S, B> via(Affine<A, B> affine);

  /**
   * Composes this path with another traversal, producing a TraversalPath.
   *
   * @param traversal the traversal to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on all nested targets
   */
  <B> TraversalPath<S, B> via(Traversal<A, B> traversal);

  /**
   * Composes this path with an iso, producing a TraversalPath.
   *
   * @param iso the iso to compose with
   * @param <B> the new focused type
   * @return a TraversalPath with converted values
   */
  <B> TraversalPath<S, B> via(Iso<A, B> iso);

  // ===== then() Aliases =====

  /**
   * Alias for {@link #via(Lens)}. Composes this path with a lens.
   *
   * <p>This method provides an alternative naming convention familiar to users of other optics
   * libraries.
   *
   * @param lens the lens to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on the composed targets
   */
  default <B> TraversalPath<S, B> then(Lens<A, B> lens) {
    return via(lens);
  }

  /**
   * Alias for {@link #via(FocusPath)}. Composes this path with a FocusPath.
   *
   * @param other the FocusPath to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on the composed targets
   */
  default <B> TraversalPath<S, B> then(FocusPath<A, B> other) {
    return via(other);
  }

  /**
   * Alias for {@link #via(Prism)}. Composes this path with a prism.
   *
   * @param prism the prism to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on matching elements
   */
  default <B> TraversalPath<S, B> then(Prism<A, B> prism) {
    return via(prism);
  }

  /**
   * Alias for {@link #via(Affine)}. Composes this path with an affine.
   *
   * @param affine the affine to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on the composed targets
   */
  default <B> TraversalPath<S, B> then(Affine<A, B> affine) {
    return via(affine);
  }

  /**
   * Alias for {@link #via(AffinePath)}. Composes this path with an AffinePath.
   *
   * @param other the AffinePath to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on the composed targets
   */
  default <B> TraversalPath<S, B> then(AffinePath<A, B> other) {
    return via(other);
  }

  /**
   * Alias for {@link #via(Traversal)}. Composes this path with a traversal.
   *
   * @param traversal the traversal to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on all nested targets
   */
  default <B> TraversalPath<S, B> then(Traversal<A, B> traversal) {
    return via(traversal);
  }

  /**
   * Alias for {@link #via(TraversalPath)}. Composes this path with another TraversalPath.
   *
   * @param other the TraversalPath to compose with
   * @param <B> the new focused type
   * @return a TraversalPath focusing on all nested targets
   */
  default <B> TraversalPath<S, B> then(TraversalPath<A, B> other) {
    return via(other);
  }

  /**
   * Alias for {@link #via(Iso)}. Composes this path with an iso.
   *
   * @param iso the iso to compose with
   * @param <B> the new focused type
   * @return a TraversalPath with converted values
   */
  default <B> TraversalPath<S, B> then(Iso<A, B> iso) {
    return via(iso);
  }

  // ===== Conditional Modification =====

  /**
   * Modifies only elements that satisfy the given predicate.
   *
   * <p>Elements that don't match the predicate are left unchanged. This is a convenience method
   * combining {@link #filter(Predicate)} with {@link #modifyAll(Function, Object)}.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * // Give raises only to senior employees
   * Company updated = CompanyFocus.employees()
   *     .modifyWhen(
   *         e -> e.yearsOfService() > 5,
   *         e -> e.withSalary(e.salary() * 1.1),
   *         company);
   * }</pre>
   *
   * @param condition the predicate that elements must satisfy to be modified
   * @param f the transformation function to apply to matching elements
   * @param source the source structure
   * @return a new structure with matching elements modified
   */
  default S modifyWhen(Predicate<A> condition, Function<A, A> f, S source) {
    return modifyAll(a -> condition.test(a) ? f.apply(a) : a, source);
  }

  // ===== Effectful Operations =====

  /**
   * Transforms all focused values with an effectful function.
   *
   * <p>This method enables effectful modifications such as validation, async operations, or
   * computations that may fail. The effects from all focused elements are combined using the
   * applicative's sequencing.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * // Validate all employee emails
   * TraversalPath<Company, String> emailsPath = CompanyFocus.employees().email();
   *
   * Kind<Validated.Witness, Company> result = emailsPath.modifyF(
   *     email -> EmailValidator.validate(email),
   *     company,
   *     ValidatedApplicative.INSTANCE
   * );
   *
   * // Async fetch for all items
   * Kind<CompletableFutureKind.Witness, Order> result = orderItemsPath.modifyF(
   *     item -> fetchUpdatedPrice(item),
   *     order,
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
    return toTraversal().modifyF(f, source, applicative);
  }

  // ===== Monoid-based Aggregation =====

  /**
   * Maps each focused element to a monoidal value and combines them.
   *
   * <p>This is the fundamental operation for aggregating values from a traversal. Many common
   * operations can be expressed in terms of foldMap with an appropriate monoid.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * // Sum all salaries
   * TraversalPath<Company, Integer> salariesPath = CompanyFocus.employees().salary();
   * int total = salariesPath.foldMap(Monoids.intSum(), s -> s, company);
   *
   * // Concatenate all names with separator
   * String names = namesPath.foldMap(
   *     Monoid.of("", (a, b) -> a.isEmpty() ? b : a + ", " + b),
   *     Function.identity(),
   *     source
   * );
   *
   * // Check if any element matches
   * boolean hasAdmin = rolesPath.foldMap(
   *     Monoids.booleanOr(),
   *     role -> role.equals("admin"),
   *     user
   * );
   * }</pre>
   *
   * @param monoid the monoid for combining mapped values
   * @param f the function to map each element to a monoidal value
   * @param source the source structure
   * @param <M> the monoidal result type
   * @return the combined result of mapping all focused elements
   */
  default <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
    return asFold().foldMap(monoid, f, source);
  }

  /**
   * Reduces all focused elements using a monoid.
   *
   * <p>This is equivalent to {@code foldMap(monoid, Function.identity(), source)}.
   *
   * @param monoid the monoid for combining elements
   * @param source the source structure
   * @return the combined result of all focused elements
   */
  default A fold(Monoid<A> monoid, S source) {
    return foldMap(monoid, Function.identity(), source);
  }

  // ===== Collection Navigation =====

  /**
   * When each focused element is a {@code List<E>}, flattens to traverse all nested elements.
   *
   * @param <E> the element type of the nested lists
   * @return a TraversalPath over all nested list elements
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
   * When each focused element is a {@code List<E>}, focuses on elements at the specified index.
   *
   * @param index the index to focus on in each list
   * @param <E> the element type of the lists
   * @return a TraversalPath focusing on elements at the index (skipping lists that are too short)
   */
  @SuppressWarnings("unchecked")
  default <E> TraversalPath<S, E> at(int index) {
    // Compose with affine, which gives us a Traversal (Traversal >>> Affine = Traversal)
    Affine<A, E> indexAffine = (Affine<A, E>) FocusPaths.listAt(index);
    return via(indexAffine.asTraversal());
  }

  /**
   * When each focused element is a {@code Map<K, V>}, focuses on values at the specified key.
   *
   * @param key the key to look up in each map
   * @param <K> the key type
   * @param <V> the value type
   * @return a TraversalPath focusing on values at the key (skipping maps without the key)
   */
  @SuppressWarnings("unchecked")
  default <K, V> TraversalPath<S, V> atKey(K key) {
    Affine<A, V> keyAffine = (Affine<A, V>) FocusPaths.mapAt(key);
    return via(keyAffine.asTraversal());
  }

  /**
   * When each focused element is {@code Optional<E>}, flattens to focus on present values.
   *
   * @param <E> the inner type of the Optionals
   * @return a TraversalPath over present Optional values
   */
  @SuppressWarnings("unchecked")
  default <E> TraversalPath<S, E> some() {
    Affine<A, E> someAffine = (Affine<A, E>) FocusPaths.optionalSome();
    return via(someAffine.asTraversal());
  }

  /**
   * For each element this traversal focuses on, if that element is a traversable container {@code
   * Kind<F, E>}, flattens to traverse all nested elements.
   *
   * <p>This enables generic traversal over any type with a {@link org.higherkindedj.hkt.Traverse}
   * instance, not just {@code List}. For example, {@code Set}, {@code Tree}, or custom collections.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * // Given: Company with List<Department>, where each Department has Kind<ListKind.Witness, Employee>
   * TraversalPath<Company, Kind<ListKind.Witness, Employee>> deptEmployeesPath =
   *     CompanyFocus.departments().via(DepartmentFocus.employees());
   *
   * // Traverse into the nested Lists
   * TraversalPath<Company, Employee> allEmployees = deptEmployeesPath.traverseOver(
   *     ListTraverse.INSTANCE
   * );
   *
   * // Now can operate on all employees across all departments
   * List<Employee> employees = allEmployees.getAll(company);
   * Company updated = allEmployees.modifyAll(Employee::giveRaise, company);
   * }</pre>
   *
   * <p>For standard {@code List<E>} fields, prefer the {@link #each()} method which handles the
   * conversion automatically.
   *
   * @param <F> the witness type of the traversable container
   * @param <E> the element type within the container
   * @param traverse the Traverse instance for the container type
   * @return a TraversalPath focusing on all nested elements within each container
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
   * When each focused element is a {@code List<E>}, decomposes into head and tail pairs.
   *
   * <p>Empty lists are skipped (do not produce a focus).
   *
   * @param <E> the element type of the lists
   * @return a TraversalPath over (head, tail) pairs
   */
  @SuppressWarnings("unchecked")
  default <E> TraversalPath<S, Pair<E, List<E>>> cons() {
    Prism<A, Pair<E, List<E>>> prism =
        (Prism<A, Pair<E, List<E>>>) (Prism<?, ?>) FocusPaths.<E>listCons();
    return via(prism.asTraversal());
  }

  /**
   * Alias for {@link #cons()} using Java-familiar naming.
   *
   * @param <E> the element type of the lists
   * @return a TraversalPath over (head, tail) pairs
   */
  default <E> TraversalPath<S, Pair<E, List<E>>> headTail() {
    return cons();
  }

  /**
   * When each focused element is a {@code List<E>}, decomposes into init and last pairs.
   *
   * <p>Empty lists are skipped (do not produce a focus).
   *
   * @param <E> the element type of the lists
   * @return a TraversalPath over (init, last) pairs
   */
  @SuppressWarnings("unchecked")
  default <E> TraversalPath<S, Pair<List<E>, E>> snoc() {
    Prism<A, Pair<List<E>, E>> prism =
        (Prism<A, Pair<List<E>, E>>) (Prism<?, ?>) FocusPaths.<E>listSnoc();
    return via(prism.asTraversal());
  }

  /**
   * Alias for {@link #snoc()} using Java-familiar naming.
   *
   * @param <E> the element type of the lists
   * @return a TraversalPath over (init, last) pairs
   */
  default <E> TraversalPath<S, Pair<List<E>, E>> initLast() {
    return snoc();
  }

  /**
   * When each focused element is a {@code List<E>}, focuses on head elements.
   *
   * <p>Empty lists are skipped.
   *
   * @param <E> the element type of the lists
   * @return a TraversalPath over head elements
   */
  @SuppressWarnings("unchecked")
  default <E> TraversalPath<S, E> head() {
    Affine<A, E> affine = (Affine<A, E>) (Affine<?, ?>) FocusPaths.<E>listHead();
    return via(affine.asTraversal());
  }

  /**
   * When each focused element is a {@code List<E>}, focuses on last elements.
   *
   * <p>Empty lists are skipped.
   *
   * @param <E> the element type of the lists
   * @return a TraversalPath over last elements
   */
  @SuppressWarnings("unchecked")
  default <E> TraversalPath<S, E> last() {
    Affine<A, E> affine = (Affine<A, E>) (Affine<?, ?>) FocusPaths.<E>listLast();
    return via(affine.asTraversal());
  }

  /**
   * When each focused element is a {@code List<E>}, focuses on tails.
   *
   * <p>Empty lists are skipped.
   *
   * @param <E> the element type of the lists
   * @return a TraversalPath over tails
   */
  @SuppressWarnings("unchecked")
  default <E> TraversalPath<S, List<E>> tail() {
    Affine<A, List<E>> affine = (Affine<A, List<E>>) (Affine<?, ?>) FocusPaths.<E>listTail();
    return via(affine.asTraversal());
  }

  /**
   * When each focused element is a {@code List<E>}, focuses on inits.
   *
   * <p>Empty lists are skipped.
   *
   * @param <E> the element type of the lists
   * @return a TraversalPath over inits
   */
  @SuppressWarnings("unchecked")
  default <E> TraversalPath<S, List<E>> init() {
    Affine<A, List<E>> affine = (Affine<A, List<E>>) (Affine<?, ?>) FocusPaths.<E>listInit();
    return via(affine.asTraversal());
  }

  // ===== Narrowing Methods =====

  /**
   * Narrows this TraversalPath to an AffinePath focusing on the first element.
   *
   * <p>This method converts a TraversalPath (zero or more elements) to an AffinePath (zero or one
   * element) by focusing only on the first element if present for queries. For modifications, the
   * new value is applied to all elements of the underlying traversal to preserve update semantics.
   *
   * <p><b>Query behaviour:</b> {@code getOptional} returns only the first element (if any).
   *
   * <p><b>Modification behaviour:</b> {@code set} and {@code modify} update all elements targeted
   * by the underlying traversal, not just the first. This ensures that modifications are consistent
   * with the original traversal's semantics.
   *
   * <p>This is useful when composing with optics that produce a TraversalPath but you only need the
   * first element, such as when working with HKT types that have "zero or one" semantics (like
   * Maybe, Either, Try).
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * // TraversalPath from traverseOver
   * TraversalPath<Config, Value> allValues = configPath.traverseOver(MaybeTraverse.INSTANCE);
   *
   * // Narrow to just the first (and likely only) value
   * AffinePath<Config, Value> firstValue = allValues.headOption();
   *
   * // Now can use AffinePath methods
   * Optional<Value> maybeValue = firstValue.getOptional(config);
   * }</pre>
   *
   * @return an AffinePath focusing on the first element of this traversal
   */
  default AffinePath<S, A> headOption() {
    TraversalPath<S, A> self = this;
    return AffinePath.of(
        Affine.of(
            self::preview,
            (s, a) -> {
              // Set all focused elements to the same value
              // This preserves the semantics of the underlying traversal
              return self.setAll(a, s);
            }));
  }

  // ===== Conversion Methods =====

  /**
   * Extracts the underlying traversal.
   *
   * @return the wrapped Traversal
   */
  Traversal<S, A> toTraversal();

  /**
   * Views this path as a Fold.
   *
   * <p>This method converts the underlying Traversal to a Fold using the Const applicative, which
   * is the standard functional programming approach for this conversion.
   *
   * @return a Fold view of the underlying traversal
   */
  default Fold<S, A> asFold() {
    Traversal<S, A> traversal = toTraversal();
    return new Fold<>() {
      @Override
      public <M> M foldMap(Monoid<M> monoid, Function<? super A, ? extends M> f, S source) {
        ConstApplicative<M> constApp = new ConstApplicative<>(monoid);
        Kind<ConstKind.Witness<M>, S> result =
            traversal.modifyF(
                a -> ConstKindHelper.CONST.widen(new Const<>(f.apply(a))), source, constApp);
        Const<M, S> finalConst = ConstKindHelper.CONST.narrow(result);
        return finalConst.value();
      }
    };
  }

  // ===== Debugging =====

  /**
   * Creates a new TraversalPath that invokes an observer during get operations.
   *
   * <p>This method is useful for debugging complex path navigations by logging or inspecting values
   * as they are accessed. The observer receives the source and the list of all focused values.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * TraversalPath<Company, Employee> debugPath = CompanyFocus.employees()
   *     .traced((company, employees) ->
   *         log.debug("Found {} employees in {}", employees.size(), company.name()));
   *
   * // Every getAll() call will now log the accessed values
   * List<Employee> employees = debugPath.getAll(company);
   * }</pre>
   *
   * @param observer a consumer that receives the source and all focused values during get
   *     operations
   * @return a new TraversalPath that invokes the observer
   */
  default TraversalPath<S, A> traced(BiConsumer<S, List<A>> observer) {
    return new TracedTraversalFocusPath<>(this, observer);
  }

  // ===== Effect Path Bridge Methods =====

  /**
   * Applies an effectful function to each focused element and executes them in parallel.
   *
   * <p>This method bridges from the optics domain to the concurrent effect domain. Each focused
   * element is mapped to a {@link VTaskPath} via the provided function, and all resulting tasks are
   * executed in parallel using {@link Par#traverse}. The results are collected in order.
   *
   * <p>This is the optics equivalent of {@code Traversable.traverse} specialised for {@code
   * VTaskPath}, providing parallel effectful traversal of focused elements with structured
   * concurrency.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * TraversalPath<Company, Employee> employeesPath = CompanyFocus.employees();
   * Company company = new Company(List.of(emp1, emp2, emp3));
   *
   * // Enrich each employee in parallel via remote service
   * VTaskPath<List<EnrichedEmployee>> enriched = employeesPath.traverseWith(
   *     employee -> Path.vtask(() -> enrichmentService.enrich(employee)),
   *     company
   * );
   *
   * List<EnrichedEmployee> results = enriched.unsafeRun();
   * }</pre>
   *
   * @param f the effectful function to apply to each focused element
   * @param source the source structure
   * @param <B> the result type of the effectful function
   * @return a VTaskPath that produces a list of all results in order
   * @see Par#traverse
   * @see #toVStreamPath for lazy streaming alternative
   */
  default <B> VTaskPath<List<B>> traverseWith(Function<A, VTaskPath<B>> f, S source) {
    return Path.vtaskPath(Par.traverse(getAll(source), a -> f.apply(a).run()));
  }

  /**
   * Extracts all focused values and wraps them in a {@link ListPath}.
   *
   * <p>This bridges from the optics domain to the effect domain, allowing all focused values to be
   * processed using effect-based operations. ListPath uses positional zipWith semantics where
   * elements are combined index-by-index.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * TraversalPath<Company, Employee> employeesPath = CompanyFocus.employees();
   * Company company = new Company(List.of(emp1, emp2, emp3));
   *
   * // Extract all employees and process
   * ListPath<String> names = employeesPath.toListPath(company)
   *     .map(Employee::name)
   *     .map(String::toUpperCase);
   * }</pre>
   *
   * @param source the source structure
   * @return a ListPath containing all focused values
   */
  default ListPath<A> toListPath(S source) {
    return Path.listPath(getAll(source));
  }

  /**
   * Extracts all focused values and wraps them in a {@link NonDetPath}.
   *
   * <p>This bridges from the optics domain to the effect domain using non-deterministic semantics.
   * NonDetPath uses Cartesian product for zipWith, producing all combinations of values.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * TraversalPath<Config, String> optionsPath = ConfigFocus.options();
   * Config config = new Config(List.of("a", "b", "c"));
   *
   * // Non-deterministic processing - Cartesian product on combination
   * NonDetPath<String> combinations = optionsPath.toNonDetPath(config)
   *     .zipWith(Path.list(List.of("1", "2")), (opt, num) -> opt + num);
   * // Results in: ["a1", "a2", "b1", "b2", "c1", "c2"]
   * }</pre>
   *
   * @param source the source structure
   * @return a NonDetPath containing all focused values
   */
  default NonDetPath<A> toNonDetPath(S source) {
    return Path.list(getAll(source));
  }

  /**
   * Extracts all focused values and wraps them in a {@link StreamPath}.
   *
   * <p>This bridges from the optics domain to the effect domain using lazy stream semantics. This
   * is useful for large collections where eager evaluation would be inefficient.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * TraversalPath<Database, Record> recordsPath = DatabaseFocus.records();
   * Database db = getLargeDatabase();
   *
   * // Lazy stream processing
   * StreamPath<String> ids = recordsPath.toStreamPath(db)
   *     .map(Record::id)
   *     .filter(id -> id.startsWith("A"));
   * }</pre>
   *
   * @param source the source structure
   * @return a StreamPath streaming all focused values
   */
  default StreamPath<A> toStreamPath(S source) {
    return Path.streamFromList(getAll(source));
  }

  /**
   * Extracts all focused values and wraps them in a {@link VStreamPath}.
   *
   * <p>This bridges from the optics domain to the effect domain using lazy, pull-based streaming
   * semantics with virtual thread execution. The focused values are materialised into a list and
   * then wrapped in a VStream.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * TraversalPath<Company, Employee> employeesPath = CompanyFocus.employees();
   * Company company = new Company(List.of(emp1, emp2, emp3));
   *
   * // Extract employees as a VStreamPath for virtual-thread processing
   * VStreamPath<String> names = employeesPath.toVStreamPath(company)
   *     .map(Employee::name)
   *     .filter(n -> n.startsWith("A"));
   * }</pre>
   *
   * @param source the source structure
   * @return a VStreamPath streaming all focused values
   */
  default VStreamPath<A> toVStreamPath(S source) {
    return Path.vstreamFromList(getAll(source));
  }

  /**
   * Extracts the first focused value (if any) and wraps it in a {@link MaybePath}.
   *
   * <p>This bridges from the optics domain to the effect domain, narrowing the traversal to at most
   * one element. This is useful when you only need the first element of a traversal.
   *
   * <h2>Example Usage</h2>
   *
   * <pre>{@code
   * TraversalPath<Team, Employee> membersPath = TeamFocus.members();
   * Team team = new Team(List.of(emp1, emp2, emp3));
   *
   * // Get just the first member
   * MaybePath<Employee> firstMember = membersPath.toMaybePath(team);
   *
   * // Process if present
   * MaybePath<String> firstName = firstMember.map(Employee::name);
   * }</pre>
   *
   * @param source the source structure
   * @return a MaybePath containing the first value if present
   */
  default MaybePath<A> toMaybePath(S source) {
    List<A> all = getAll(source);
    return all.isEmpty() ? Path.nothing() : Path.just(all.get(0));
  }

  // ===== Factory Methods =====

  /**
   * Creates a TraversalPath from a traversal.
   *
   * @param traversal the traversal to wrap
   * @param <S> the source type
   * @param <A> the focused type
   * @return a new TraversalPath
   */
  static <S, A> TraversalPath<S, A> of(Traversal<S, A> traversal) {
    return new TraversalFocusPath<>(traversal);
  }
}
