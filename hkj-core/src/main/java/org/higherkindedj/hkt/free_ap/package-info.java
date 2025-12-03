// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Free Applicative functor for independent/parallel composition of effects.
 *
 * <p>This package provides the {@link org.higherkindedj.hkt.free_ap.FreeAp} type, which captures
 * applicative structure (independent computations) without requiring the underlying type F to have
 * any instances.
 *
 * <h2>Key Components</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.free_ap.FreeAp} - The free applicative type
 *   <li>{@link org.higherkindedj.hkt.free_ap.FreeApKind} - HKT representation
 *   <li>{@link org.higherkindedj.hkt.free_ap.FreeApKindHelper} - Widen/narrow operations
 *   <li>{@link org.higherkindedj.hkt.free_ap.FreeApFunctor} - Functor instance
 *   <li>{@link org.higherkindedj.hkt.free_ap.FreeApApplicative} - Applicative instance
 * </ul>
 *
 * <h2>Free Applicative vs Free Monad</h2>
 *
 * <table border="1">
 * <caption>Comparison of Free Applicative and Free Monad</caption>
 * <tr><th>Aspect</th><th>Free Monad</th><th>Free Applicative</th></tr>
 * <tr><td>Composition</td><td>Sequential/dependent</td><td>Independent/parallel</td></tr>
 * <tr><td>Core operation</td><td>flatMap: A → Free[F, B]</td><td>ap: FreeAp[F, A→B] × FreeAp[F, A]</td></tr>
 * <tr><td>Structure</td><td>Tree (one branch at a time)</td><td>DAG (multiple independent branches)</td></tr>
 * <tr><td>Analysis</td><td>Cannot see full structure</td><td>Full structure visible</td></tr>
 * <tr><td>Parallelism</td><td>Not possible</td><td>Natural fit</td></tr>
 * </table>
 *
 * <h2>When to Use FreeAp</h2>
 *
 * <ul>
 *   <li>When computations are independent (neither depends on the other's result)
 *   <li>When you want to parallelize operations
 *   <li>When you want to batch similar operations
 *   <li>When you need to analyze the program structure before execution
 *   <li>For validation that collects all errors (with Validated)
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Define operations
 * sealed interface DbOp<A> { ... }
 * record GetUser(int id) implements DbOp<User> {}
 * record GetPosts(int userId) implements DbOp<List<Post>> {}
 *
 * // Build independent fetches
 * FreeAp<DbOp.Witness, User> userFetch = FreeAp.lift(new GetUser(1));
 * FreeAp<DbOp.Witness, List<Post>> postsFetch = FreeAp.lift(new GetPosts(1));
 *
 * // Combine - these are INDEPENDENT
 * FreeAp<DbOp.Witness, Profile> profile = userFetch.map2(
 *     postsFetch,
 *     Profile::new
 * );
 *
 * // Interpret - can be parallelized!
 * Natural<DbOp.Witness, IO.Witness> interpreter = ...;
 * IO<Profile> result = profile.foldMap(interpreter, ioApplicative);
 * }</pre>
 *
 * @see org.higherkindedj.hkt.free_ap.FreeAp
 * @see org.higherkindedj.hkt.free_ap.FreeApApplicative
 * @see org.higherkindedj.hkt.free.Free
 * @see org.higherkindedj.hkt.Applicative
 */
@NullMarked
package org.higherkindedj.hkt.free_ap;

import org.jspecify.annotations.NullMarked;
