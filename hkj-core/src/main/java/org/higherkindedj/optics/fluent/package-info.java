// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Fluent API for optic operations with Java-friendly naming conventions.
 *
 * <p>This package provides an ergonomic, operator-style API for working with optics in
 * higher-kinded-j. It offers two complementary styles:
 *
 * <h2>Static Methods (Concise)</h2>
 *
 * <p>Direct, functional-style operations for simple cases:
 *
 * <pre>{@code
 * // Getting values
 * String name = OpticOps.get(person, PersonLenses.name());
 * List<String> names = OpticOps.getAll(team, playerNames);
 *
 * // Setting values
 * Person updated = OpticOps.set(person, PersonLenses.age(), 30);
 * Team allScores100 = OpticOps.setAll(team, playerScores, 100);
 *
 * // Modifying values
 * Person older = OpticOps.modify(person, PersonLenses.age(), age -> age + 1);
 * Team bonusTeam = OpticOps.modifyAll(team, playerScores, score -> score + 10);
 *
 * // Querying
 * boolean hasAdults = OpticOps.exists(team, playerAges, age -> age >= 18);
 * int count = OpticOps.count(team, players);
 * }</pre>
 *
 * <h2>Fluent Builders (Explicit)</h2>
 *
 * <p>Method-chaining style for explicit workflows:
 *
 * <pre>{@code
 * // Getting values
 * String name = OpticOps.getting(person).through(PersonLenses.name());
 * Optional<Address> addr = OpticOps.getting(person).maybeThrough(addressPrism);
 * List<String> names = OpticOps.getting(team).allThrough(playerNames);
 *
 * // Setting values
 * Person updated = OpticOps.setting(person).through(PersonLenses.age(), 30);
 *
 * // Modifying values
 * Person older = OpticOps.modifying(person).through(PersonLenses.age(), age -> age + 1);
 *
 * // Querying
 * boolean hasAdults = OpticOps.querying(team).anyMatch(playerAges, age -> age >= 18);
 * int count = OpticOps.querying(team).count(players);
 * }</pre>
 *
 * <h2>Design Rationale</h2>
 *
 * <p>This API uses Java-centric naming ({@code get}, {@code set}, {@code modify}) instead of
 * Haskell's ({@code view}, {@code set}, {@code over}) to feel more natural to Java developers.
 *
 * <h2>When to Use</h2>
 *
 * <ul>
 *   <li><b>Static methods</b>: Simple, one-off operations where brevity is preferred
 *   <li><b>Fluent builders</b>: Complex workflows, or when IDE autocomplete guidance is helpful
 * </ul>
 *
 * <h2>Comparison with Direct Optic Methods</h2>
 *
 * <table border="1">
 * <tr><th>Direct Optic</th><th>Fluent API</th></tr>
 * <tr><td>{@code lens.get(source)}</td><td>{@code OpticOps.get(source, lens)}</td></tr>
 * <tr><td>{@code lens.set(value, source)}</td><td>{@code OpticOps.set(source, lens, value)}</td></tr>
 * <tr><td>{@code lens.modify(f, source)}</td><td>{@code OpticOps.modify(source, lens, f)}</td></tr>
 * <tr><td>{@code fold.getAll(source)}</td><td>{@code OpticOps.getAll(source, fold)}</td></tr>
 * </table>
 *
 * <p>The fluent API provides source-first ordering, which reads more naturally in many contexts.
 *
 * @see org.higherkindedj.optics.fluent.OpticOps
 */
package org.higherkindedj.optics.fluent;
