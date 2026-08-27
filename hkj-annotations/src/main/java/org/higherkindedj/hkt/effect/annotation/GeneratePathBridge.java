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
 * under {@code String}. An overridden method is bridged once, and so is one that two unrelated
 * superinterfaces both declare. Note that {@code @PathVia} is not inherited by an override: a
 * method that overrides an annotated one hides it unless it carries the annotation too.
 *
 * <p>An interface with no {@code @PathVia} method anywhere draws a warning; the bridge is still
 * written, with a constructor and nothing else. A processor warning is not suppressible, so a build
 * running {@code -Werror} treats it as an error.
 *
 * <h2>What the Bridge Refuses</h2>
 *
 * <p>The bridge is source the author never wrote and cannot edit. Every shape below is one the
 * language accepts and the bridge has no compiling, warning-free rendering of, so it is refused at
 * the declaration, where it can be acted on:
 *
 * <ul>
 *   <li>a <strong>raw</strong> type anywhere the bridge writes it down: {@code Optional} as a
 *       return type, {@code Optional<List>} as its argument, {@code List} as a parameter. Each is a
 *       {@code [rawtypes]} warning in a file the author's own {@code @SuppressWarnings} does not
 *       reach
 *   <li>a {@code Validated} whose <strong>error type is a wildcard</strong>, which the bridge has
 *       to name twice, once in the {@code ValidationPath} and once in the {@code Semigroup} the
 *       caller supplies; a wildcard is a different captured type at each mention, so no caller
 *       could satisfy both. Wildcards elsewhere, {@code Validated}'s value type included, are named
 *       once and carry through
 *   <li>a method <strong>type parameter sharing a name</strong> with one the interface declares,
 *       where the signature needs the interface's. The bridge declares both sets in one scope,
 *       which the delegate never does, so the method's type parameter would hide the interface's
 *   <li>a {@code static} or {@code private} method, which the delegate reference cannot call
 *   <li>a {@code @PathVia(name = ...)} that is not a Java identifier, or that lands on a signature
 *       another {@code @PathVia} already bridges to
 *   <li>with {@code targetPackage}, any type in the signature or in a bound that the target package
 *       cannot see
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
