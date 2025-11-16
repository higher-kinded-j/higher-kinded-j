// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java record for which a Folds utility class should be generated. The generated class will
 * be named by appending "Folds" to the record's name.
 *
 * <p>Folds are read-only optics that allow querying and extracting data from structures. Unlike
 * {@link GenerateLenses}, which generates both getters and setters, Folds only provide read
 * operations.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @GenerateFolds
 * record User(String name, int age, List<Order> orders) {}
 *
 * // Generates UserFolds with methods:
 * // - Fold<User, String> name()
 * // - Fold<User, Integer> age()
 * // - Fold<User, List<Order>> orders()
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface GenerateFolds {}
