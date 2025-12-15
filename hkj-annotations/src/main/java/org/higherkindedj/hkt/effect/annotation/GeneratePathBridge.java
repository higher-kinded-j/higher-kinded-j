// Copyright (c) 2025 Magnus Smith
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
