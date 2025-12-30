// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.interpret;

import java.util.HashMap;
import java.util.Map;

/**
 * An evaluation environment mapping variable names to their runtime values.
 *
 * <p>The environment is used during interpretation to look up the values of variables. It is
 * immutable: binding a new variable creates a new environment.
 *
 * <p>This is threaded through the interpreter using the State monad, so we never need to pass it
 * explicitly through recursive calls.
 */
public final class Environment {

  private final Map<String, Object> bindings;

  private Environment(Map<String, Object> bindings) {
    this.bindings = bindings;
  }

  /** Create an empty environment. */
  public static Environment empty() {
    return new Environment(Map.of());
  }

  /**
   * Create an environment with a single binding.
   *
   * @param name the variable name
   * @param value the variable's value
   * @return a new environment
   */
  public static Environment of(String name, Object value) {
    return new Environment(Map.of(name, value));
  }

  /**
   * Create an environment with two bindings.
   *
   * @param name1 the first variable name
   * @param value1 the first variable's value
   * @param name2 the second variable name
   * @param value2 the second variable's value
   * @return a new environment
   */
  public static Environment of(String name1, Object value1, String name2, Object value2) {
    return new Environment(Map.of(name1, value1, name2, value2));
  }

  /**
   * Create an environment with three bindings.
   *
   * @param name1 the first variable name
   * @param value1 the first variable's value
   * @param name2 the second variable name
   * @param value2 the second variable's value
   * @param name3 the third variable name
   * @param value3 the third variable's value
   * @return a new environment
   */
  public static Environment of(
      String name1, Object value1, String name2, Object value2, String name3, Object value3) {
    return new Environment(Map.of(name1, value1, name2, value2, name3, value3));
  }

  /**
   * Create an environment from a map.
   *
   * @param bindings the variable bindings
   * @return a new environment
   */
  public static Environment of(Map<String, Object> bindings) {
    return new Environment(Map.copyOf(bindings));
  }

  /**
   * Look up the value of a variable.
   *
   * @param name the variable name
   * @return the value
   * @throws IllegalArgumentException if the variable is not bound
   */
  public Object lookup(String name) {
    Object value = bindings.get(name);
    if (value == null && !bindings.containsKey(name)) {
      throw new IllegalArgumentException("Undefined variable: " + name);
    }
    return value;
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

  /**
   * Create a new environment with an additional binding.
   *
   * <p>If the variable is already bound, the new binding shadows the old one.
   *
   * @param name the variable name
   * @param value the variable's value
   * @return a new environment with the binding
   */
  public Environment bind(String name, Object value) {
    Map<String, Object> newBindings = new HashMap<>(bindings);
    newBindings.put(name, value);
    return new Environment(Map.copyOf(newBindings));
  }

  @Override
  public String toString() {
    return "Environment" + bindings;
  }
}
