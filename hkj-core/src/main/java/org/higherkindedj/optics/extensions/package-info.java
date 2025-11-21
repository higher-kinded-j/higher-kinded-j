// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.

/**
 * Extension utilities for integrating optics with hkj-core types.
 *
 * <p>This package provides static helper methods that make it easier to use optics (Lens, Prism,
 * Traversal, Fold, Getter) with hkj-core types like Maybe, Either, Validated, and Try.
 *
 * <h2>Key Classes</h2>
 *
 * <ul>
 *   <li>{@link org.higherkindedj.optics.extensions.LensExtensions} - Helpers for {@link
 *       org.higherkindedj.optics.Lens}
 *   <li>{@link org.higherkindedj.optics.extensions.PrismExtensions} - Helpers for {@link
 *       org.higherkindedj.optics.Prism}
 *   <li>{@link org.higherkindedj.optics.extensions.TraversalExtensions} - Helpers for {@link
 *       org.higherkindedj.optics.Traversal}
 *   <li>{@link org.higherkindedj.optics.extensions.FoldExtensions} - Maybe-based alternatives to
 *       Fold's Optional-returning methods
 *   <li>{@link org.higherkindedj.optics.extensions.GetterExtensions} - Null-safe access with Maybe
 * </ul>
 *
 * <h2>Design Philosophy</h2>
 *
 * <p>These extensions follow the principle that higher-kinded-j core types ({@code Maybe}, {@code
 * Either}, {@code Validated}) should be preferred over Java standard library alternatives ({@code
 * Optional}, exceptions, null) when working within the higher-kinded-j ecosystem.
 *
 * <h2>Common Patterns</h2>
 *
 * <h3>Null-safe Lens Access</h3>
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.extensions.LensExtensions.*;
 *
 * Lens<User, String> emailLens = UserLenses.email();
 * Maybe<String> email = getMaybe(emailLens, user);
 * }</pre>
 *
 * <h3>Null-safe Getter Access</h3>
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.extensions.GetterExtensions.*;
 *
 * Getter<User, Address> addressGetter = Getter.of(User::address);
 * Maybe<Address> address = getMaybe(addressGetter, user);
 * }</pre>
 *
 * <h3>Maybe-based Fold Operations</h3>
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.extensions.FoldExtensions.*;
 *
 * // Instead of Optional<User>
 * Maybe<User> user = previewMaybe(userFold, data);
 * user.ifJust(u -> System.out.println("Found: " + u));
 *
 * // Find with Maybe
 * Maybe<Item> item = findMaybe(itemFold, i -> i.price() > 100, order);
 * }</pre>
 *
 * <h3>Validated Modifications</h3>
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.extensions.LensExtensions.*;
 *
 * Either<String, User> result = modifyEither(
 *     emailLens,
 *     email -> email.contains("@")
 *         ? Either.right(email)
 *         : Either.left("Invalid email format"),
 *     user
 * );
 * }</pre>
 *
 * <h3>Bulk Validation with Error Accumulation</h3>
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.extensions.TraversalExtensions.*;
 *
 * Traversal<List<User>, String> allEmails =
 *     Traversals.forList().andThen(UserLenses.email().asTraversal());
 *
 * Validated<List<String>, List<User>> results = modifyAllValidated(
 *     allEmails,
 *     email -> email.contains("@")
 *         ? Validated.valid(email)
 *         : Validated.invalid("Invalid: " + email),
 *     users
 * );
 * // If validation fails, get ALL error messages, not just the first one
 * }</pre>
 *
 * @see org.higherkindedj.hkt.maybe
 * @see org.higherkindedj.hkt.either
 * @see org.higherkindedj.hkt.validated
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.optics.extensions;
