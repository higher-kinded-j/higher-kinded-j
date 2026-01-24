// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Provides the {@link org.higherkindedj.hkt.context.Context} effect type for reading from Java's
 * {@link java.lang.ScopedValue} API in a functional, composable way.
 *
 * <p>This package contains:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.context.Context} — The core effect type representing
 *       computations that read from scoped values
 *   <li>{@link org.higherkindedj.hkt.context.ContextKind} — HKT witness interface for type class
 *       integration
 *   <li>{@link org.higherkindedj.hkt.context.ContextKindHelper} — Utilities for widen/narrow
 *       operations and factory methods
 *   <li>{@link org.higherkindedj.hkt.context.ContextMonad} — Monad type class instance
 *   <li>{@link org.higherkindedj.hkt.context.RequestContext} — Pre-defined scoped values for HTTP
 *       request context (trace ID, locale, tenant, etc.)
 *   <li>{@link org.higherkindedj.hkt.context.SecurityContext} — Pre-defined scoped values for
 *       security context (principal, roles, permissions)
 * </ul>
 *
 * <h2>Key Features</h2>
 *
 * <ul>
 *   <li><b>Thread-Safe Propagation:</b> Values bound in a parent thread are automatically visible
 *       to child virtual threads forked within the same scope
 *   <li><b>Composability:</b> Full support for {@code map}, {@code flatMap}, and integration with
 *       Higher-Kinded-J type classes
 *   <li><b>Error Handling:</b> Built-in support for recovery and error transformation
 *   <li><b>VTask Integration:</b> Convert to {@link org.higherkindedj.hkt.vtask.VTask} for
 *       execution within virtual thread contexts
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Define a scoped value
 * static final ScopedValue<String> USER_ID = ScopedValue.newInstance();
 *
 * // Create a context that reads it
 * Context<String, String> getUserId = Context.ask(USER_ID);
 *
 * // Transform and compose
 * Context<String, User> fetchUser = getUserId.flatMap(id ->
 *     Context.succeed(userRepository.findById(id)));
 *
 * // Run within a scoped binding
 * User user = ScopedValue
 *     .where(USER_ID, "user-123")
 *     .call(() -> fetchUser.run());
 * }</pre>
 *
 * <h2>Comparison with Reader</h2>
 *
 * <p>{@code Context<R, A>} is conceptually similar to {@link org.higherkindedj.hkt.reader.Reader
 * Reader<R, A>}, but with crucial differences:
 *
 * <ul>
 *   <li><b>Reader:</b> Requires explicit parameter passing at {@code run(r)}
 *   <li><b>Context:</b> Reads from thread-scoped {@link java.lang.ScopedValue}, inherits
 *       automatically across virtual thread boundaries
 * </ul>
 *
 * <p>Use {@code Context} when values need to propagate to child threads without explicit passing.
 * Use {@code Reader} for explicit dependency injection where thread propagation is not needed.
 *
 * @see org.higherkindedj.hkt.context.Context
 * @see org.higherkindedj.hkt.context.RequestContext
 * @see org.higherkindedj.hkt.context.SecurityContext
 * @see java.lang.ScopedValue
 */
@NullMarked
package org.higherkindedj.hkt.context;

import org.jspecify.annotations.NullMarked;
