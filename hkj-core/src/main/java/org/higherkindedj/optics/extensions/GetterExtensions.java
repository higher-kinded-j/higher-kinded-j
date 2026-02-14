// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.extensions;

import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Getter;
import org.jspecify.annotations.NullMarked;

/**
 * Extension utilities for {@link Getter} that integrate with higher-kinded-j core types.
 *
 * <p>This class provides null-safe alternatives to {@link Getter}'s methods, returning {@link
 * Maybe} instead of potentially null values. This ensures safe handling of nullable fields while
 * maintaining consistency with higher-kinded-j's functional programming patterns.
 *
 * <h2>Why Null-Safe Getters?</h2>
 *
 * <p>While optics typically assume non-null values, real-world data structures often contain
 * nullable fields. {@code GetterExtensions} bridges this gap by:
 *
 * <ul>
 *   <li>Converting potentially null values to {@code Maybe.nothing()} automatically
 *   <li>Allowing safe chaining of operations without null checks
 *   <li>Maintaining type safety throughout the transformation pipeline
 *   <li>Providing consistent error handling with other optics extensions
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.optics.extensions.GetterExtensions.*;
 *
 * // Define a record with nullable fields
 * record Person(String name, @Nullable Address address) {}
 * record Address(String city, @Nullable String zipCode) {}
 *
 * // Create getters (potentially returning null)
 * Getter<Person, Address> addressGetter = Getter.of(Person::address);
 * Getter<Address, String> zipCodeGetter = Getter.of(Address::zipCode);
 *
 * Person person = new Person("Alice", null);
 *
 * // Get address safely as Maybe
 * Maybe<Address> address = getMaybe(addressGetter, person);
 * address.ifJust(addr -> System.out.println("City: " + addr.city()));
 * // Output: (nothing - no output)
 *
 * // Chain operations safely
 * Maybe<String> zipCode = getMaybe(addressGetter, person)
 *     .flatMap(addr -> getMaybe(zipCodeGetter, addr));
 *
 * String result = zipCode.orElse("No ZIP code available");
 * }</pre>
 *
 * <h2>Integration with Lens</h2>
 *
 * <p>Since every {@link org.higherkindedj.optics.Lens} can be viewed as a {@code Getter} (via
 * {@code Lens::asGetter} or direct usage), these extensions work seamlessly with lenses:
 *
 * <pre>{@code
 * Lens<Person, Address> addressLens = PersonLenses.address();
 *
 * // Use as Getter with Maybe
 * Maybe<Address> address = getMaybe(addressLens, person);
 * }</pre>
 *
 * <h2>Method Correspondence</h2>
 *
 * <table>
 *   <caption>Getter methods and their Maybe-based equivalents</caption>
 *   <thead>
 *     <tr>
 *       <th>Getter Method</th>
 *       <th>GetterExtensions Method</th>
 *       <th>Return Type</th>
 *       <th>Null Handling</th>
 *     </tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@link Getter#get(Object)}</td>
 *       <td>{@link #getMaybe(Getter, Object)}</td>
 *       <td>{@code Maybe<A>}</td>
 *       <td>null → {@code Maybe.nothing()}</td>
 *     </tr>
 *   </tbody>
 * </table>
 *
 * @see Getter
 * @see Maybe
 * @see FoldExtensions
 * @since 1.0
 */
@NullMarked
public final class GetterExtensions {

  private GetterExtensions() {
    throw new UnsupportedOperationException("Utility class - do not instantiate");
  }

  /**
   * Gets the focused value as {@link Maybe}, returning {@code Maybe.nothing()} if the value is
   * null.
   *
   * <p>This method provides null-safe access to getter values, converting null results to {@code
   * Maybe.nothing()} and non-null results to {@code Maybe.just(value)}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * record User(String name, @Nullable String email) {}
   *
   * Getter<User, String> emailGetter = Getter.of(User::email);
   *
   * User userWithEmail = new User("Alice", "alice@example.com");
   * User userWithoutEmail = new User("Bob", null);
   *
   * // Safe access with Maybe
   * Maybe<String> email1 = getMaybe(emailGetter, userWithEmail);
   * email1.ifJust(e -> sendEmail(e)); // Sends email
   *
   * Maybe<String> email2 = getMaybe(emailGetter, userWithoutEmail);
   * email2.ifJust(e -> sendEmail(e)); // Does nothing
   *
   * // Chain operations safely
   * Maybe<String> uppercaseEmail = getMaybe(emailGetter, userWithEmail)
   *     .map(String::toUpperCase);
   *
   * // Provide default value
   * String displayEmail = getMaybe(emailGetter, userWithoutEmail)
   *     .orElse("no-email@example.com");
   * }</pre>
   *
   * <h3>When to Use This</h3>
   *
   * <ul>
   *   <li>When working with data structures that may contain null values
   *   <li>When you want to chain operations safely without null checks
   *   <li>When integrating with external systems that may return null
   *   <li>When you want consistent {@code Maybe}-based error handling across your codebase
   * </ul>
   *
   * <h3>Performance Note</h3>
   *
   * <p>This method has minimal overhead compared to direct {@code get} usage. The conversion from
   * null to {@code Maybe.nothing()} is a simple branch, and {@code Maybe} instances are typically
   * allocated on the stack by modern JVMs with escape analysis.
   *
   * @param getter The getter to use for extracting the value
   * @param source The source structure to extract from
   * @param <S> The source type
   * @param <A> The focused value type
   * @return {@code Maybe.just(value)} if the value is non-null, {@code Maybe.nothing()} if the
   *     value is null
   */
  public static <S, A> Maybe<A> getMaybe(Getter<S, A> getter, S source) {
    return Maybe.fromNullable(getter.get(source));
  }
}
