// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service interface for Path bridge generation.
 *
 * <p>When applied to an interface, an annotation processor generates a companion class named {@code
 * {InterfaceName}Paths} that provides Path-wrapped versions of the service methods. This enables
 * fluent Effect Path composition with existing service interfaces.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * @GeneratePathBridge
 * public interface UserService {
 *
 *     @PathVia
 *     Optional<User> findById(Long id);
 *
 *     @PathVia
 *     Either<Error, User> createUser(CreateUserRequest request);
 *
 *     @PathVia(doc = "Validates user data")
 *     Validated<List<Error>, User> validateUser(User user);
 * }
 * }</pre>
 *
 * <p>The processor generates:
 *
 * <pre>{@code
 * public final class UserServicePaths {
 *     private final UserService delegate;
 *
 *     public UserServicePaths(UserService delegate) {
 *         this.delegate = Objects.requireNonNull(delegate);
 *     }
 *
 *     public OptionalPath<User> findById(Long id) {
 *         return Path.optional(delegate.findById(id));
 *     }
 *
 *     public EitherPath<Error, User> createUser(CreateUserRequest request) {
 *         return Path.either(delegate.createUser(request));
 *     }
 *
 *     // Validates user data
 *     public ValidationPath<List<Error>, User> validateUser(User user, Semigroup<List<Error>> semigroup) {
 *         return Path.validated(delegate.validateUser(user), semigroup);
 *     }
 * }
 * }</pre>
 *
 * <h2>Supported Return Types</h2>
 *
 * <ul>
 *   <li>{@code Optional<T>} → {@code OptionalPath<T>}
 *   <li>{@code Maybe<T>} → {@code MaybePath<T>}
 *   <li>{@code Either<E, T>} → {@code EitherPath<E, T>}
 *   <li>{@code Try<T>} → {@code TryPath<T>}
 *   <li>{@code Validated<E, T>} → {@code ValidationPath<E, T>} (requires Semigroup parameter)
 *   <li>{@code IO<T>} → {@code IOPath<T>}
 * </ul>
 *
 * <h2>Type Parameters</h2>
 *
 * <p>The bridge holds one delegate of the annotated interface, so it declares whatever that
 * interface declares, bounds and all: {@code Repo<T>} yields {@code RepoPaths<T>} wrapping a {@code
 * Repo<T>}. A {@link PathVia} method that declares parameters of its own keeps them on the bridge
 * method, where its arguments and return type name them.
 *
 * <h2>Inherited Methods</h2>
 *
 * <p>The bridge wraps the {@link PathVia} methods the interface <em>has</em>, not only those it
 * declares: a bridge for {@code StringStore extends Store<String>} picks up {@code Store}'s, read
 * under {@code String}. Java's own precedence applies, so an overridden method is bridged once, as
 * the override declares it. An interface with no {@code @PathVia} method anywhere is reported
 * rather than yielding a bridge with a constructor and nothing else.
 *
 * <h2>Return Types the Bridge Refuses</h2>
 *
 * <p>Two shapes in the table above are accepted by the language and have no bridge that compiles
 * where it lands, so they are refused at the declaration instead:
 *
 * <ul>
 *   <li>a <strong>raw</strong> effect - {@code Optional} rather than {@code Optional<Item>} - which
 *       the bridge could only pass on as an unchecked conversion, in a file that cannot carry the
 *       suppression
 *   <li>a {@code Validated} whose <strong>error type is a wildcard</strong>, which the bridge has
 *       to name twice, once in the {@code ValidationPath} and once in the {@code Semigroup} the
 *       caller supplies; a wildcard is a different captured type at each mention, so no caller
 *       could satisfy both. Wildcards elsewhere - including {@code Validated}'s value type - are
 *       named once and carry through
 * </ul>
 *
 * <h2>Varargs</h2>
 *
 * <p>A varargs delegate method stays varargs on the bridge, so call sites keep their shape. Two
 * things make the bridge take the array instead: a {@code Semigroup} appended after it, which
 * leaves the array no longer last, and a non-reifiable element type ({@code T...}), which would
 * make the bridge method a second heap-pollution warning.
 *
 * @see PathVia
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface GeneratePathBridge {

  /**
   * The package where the generated class should be placed. If empty (the default), the generated
   * class will be placed in the same package as the annotated interface.
   *
   * @return the target package name, or empty string to use the source package
   */
  String targetPackage() default "";

  /**
   * The suffix to append to the interface name for the generated class. Defaults to "Paths".
   *
   * <p>For example, with the default suffix, {@code UserService} generates {@code
   * UserServicePaths}.
   *
   * @return the class name suffix
   */
  String suffix() default "Paths";
}
