// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.typecheck;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A type environment mapping variable names to their types.
 *
 * <p>The type environment is used during type checking to look up the declared types of variables.
 * It is immutable: binding a new variable creates a new environment.
 */
public final class TypeEnv {

  private final Map<String, Type> bindings;

  private TypeEnv(Map<String, Type> bindings) {
    this.bindings = bindings;
  }

  /** Create an empty type environment. */
  public static TypeEnv empty() {
    return new TypeEnv(Map.of());
  }

  /**
   * Create a type environment with a single binding.
   *
   * @param name the variable name
   * @param type the variable's type
   * @return a new type environment
   */
  public static TypeEnv of(String name, Type type) {
    return new TypeEnv(Map.of(name, type));
  }

  /**
   * Create a type environment with multiple bindings.
   *
   * @param bindings the variable bindings
   * @return a new type environment
   */
  public static TypeEnv of(Map<String, Type> bindings) {
    return new TypeEnv(Map.copyOf(bindings));
  }

  /**
   * Look up the type of a variable.
   *
   * @param name the variable name
   * @return the type, or empty if not bound
   */
  public Optional<Type> lookup(String name) {
    return Optional.ofNullable(bindings.get(name));
  }

  /**
   * Create a new environment with an additional binding.
   *
   * <p>If the variable is already bound, the new binding shadows the old one.
   *
   * @param name the variable name
   * @param type the variable's type
   * @return a new type environment with the binding
   */
  public TypeEnv bind(String name, Type type) {
    Map<String, Type> newBindings = new HashMap<>(bindings);
    newBindings.put(name, type);
    return new TypeEnv(Map.copyOf(newBindings));
  }

  /**
   * Check if a variable is bound in this environment.
   *
   * @param name the variable name
   * @return true if bound
   */
  public boolean contains(String name) {
    return bindings.containsKey(name);
  }

  @Override
  public String toString() {
    return "TypeEnv" + bindings;
  }
}
