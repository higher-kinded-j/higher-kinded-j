// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Focus DSL for path-based optic navigation.
 *
 * <p>This package provides a fluent, type-safe DSL for navigating through nested data structures
 * using optics. Instead of manually composing lenses, prisms, and traversals, you can use Focus
 * paths to navigate with intuitive method chains that mirror the shape of your data.
 *
 * <h2>Core Types</h2>
 *
 * <p>The Focus DSL provides three path types, corresponding to the optic hierarchy:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.optics.focus.FocusPath} - wraps a {@link
 *       org.higherkindedj.optics.Lens}, focusing on exactly one element
 *   <li>{@link org.higherkindedj.optics.focus.AffinePath} - wraps an {@link
 *       org.higherkindedj.optics.Affine}, focusing on zero or one element
 *   <li>{@link org.higherkindedj.optics.focus.TraversalPath} - wraps a {@link
 *       org.higherkindedj.optics.Traversal}, focusing on zero or more elements
 * </ul>
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * // Given generated Focus classes
 * @GenerateLenses
 * @GenerateFocus
 * record Company(String name, List<Department> departments) {}
 *
 * // Navigate through the structure
 * List<String> employeeNames = CompanyFocus
 *     .departments()           // TraversalPath<Company, Department>
 *     .employees()             // TraversalPath<Company, Employee>
 *     .name()                  // TraversalPath<Company, String>
 *     .getAll(company);
 *
 * // Modify all values
 * Company updated = CompanyFocus.departments().employees().name()
 *     .modifyAll(String::toUpperCase, company);
 * }</pre>
 *
 * <h2>Collection Navigation</h2>
 *
 * <p>Focus paths provide methods for navigating collections:
 *
 * <ul>
 *   <li>{@code each()} - traverse all elements in a List, Set, or array
 *   <li>{@code at(int)} - focus on a single element by index
 *   <li>{@code atKey(K)} - focus on a map value by key
 *   <li>{@code some()} - unwrap an Optional value
 *   <li>{@code traverseOver(Traverse)} - traverse Kind-wrapped collections using Traverse type
 *       class
 * </ul>
 *
 * <h2>Type Class Integration</h2>
 *
 * <p>The Focus DSL integrates with higher-kinded-j type classes:
 *
 * <ul>
 *   <li><b>Effectful Operations</b> - Use {@code modifyF()} with any Applicative for validation,
 *       async operations, or other effects
 *   <li><b>Monoid Aggregation</b> - Use {@code foldMap()} on TraversalPath to aggregate values
 *       using a Monoid
 *   <li><b>Traverse Support</b> - Use {@code traverseOver()} to navigate into Kind-wrapped
 *       collections
 * </ul>
 *
 * <pre>{@code
 * // Effectful modification with Maybe (MaybeMonad extends Applicative)
 * Kind<MaybeKind.Witness, Config> result = configPath.modifyF(
 *     key -> validateApiKey(key),  // Returns Maybe
 *     config,
 *     MaybeMonad.INSTANCE
 * );
 *
 * // Aggregate with Monoid
 * int totalSalary = employeesPath.via(salaryLens).foldMap(
 *     Monoids.intSum(),
 *     salary -> salary,
 *     company
 * );
 *
 * // Traverse Kind-wrapped collection
 * TraversalPath<User, Role> allRoles = rolesPath
 *     .<ListKind.Witness, Role>traverseOver(ListTraverse.INSTANCE);
 * }</pre>
 *
 * <h2>Conditional and Sum Type Support</h2>
 *
 * <ul>
 *   <li><b>Conditional Modification</b> - Use {@code modifyWhen()} to update only elements matching
 *       a predicate
 *   <li><b>Sum Types</b> - Use {@code AffinePath.instanceOf()} to focus on specific variants of
 *       sealed interfaces
 * </ul>
 *
 * <pre>{@code
 * // Conditional modification
 * Company updated = employeesPath.modifyWhen(
 *     e -> e.yearsOfService() > 5,
 *     Employee::promote,
 *     company
 * );
 *
 * // Sum type navigation
 * TraversalPath<Drawing, Circle> circles = shapesPath
 *     .via(AffinePath.instanceOf(Circle.class));
 * }</pre>
 *
 * <h2>Debugging</h2>
 *
 * <p>Use {@code traced()} to observe path navigation:
 *
 * <pre>{@code
 * TraversalPath<Company, Employee> traced = employeesPath.traced(
 *     (company, employees) -> System.out.println("Found " + employees.size() + " employees")
 * );
 * }</pre>
 *
 * <h2>Composition</h2>
 *
 * <p>Focus paths compose with existing optics via the {@code via()} method:
 *
 * <pre>{@code
 * // Compose with an existing lens
 * FocusPath<User, String> fullName = UserFocus.name().via(fullNameLens);
 *
 * // Compose with a prism (produces AffinePath)
 * AffinePath<Shape, Circle> circle = ShapeFocus.value().via(circlePrism);
 * }</pre>
 *
 * @see org.higherkindedj.optics.focus.FocusPath
 * @see org.higherkindedj.optics.focus.AffinePath
 * @see org.higherkindedj.optics.focus.TraversalPath
 * @see org.higherkindedj.optics.focus.FocusPaths
 * @see org.higherkindedj.optics.util.TraverseTraversals
 */
@NullMarked
package org.higherkindedj.optics.focus;

import org.jspecify.annotations.NullMarked;
