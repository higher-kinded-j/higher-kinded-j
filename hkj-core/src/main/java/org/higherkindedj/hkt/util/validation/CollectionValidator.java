// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import java.util.Collection;
import java.util.Objects;
import org.higherkindedj.hkt.util.context.CollectionContext;

/**
 * Handles collection and array validations.
 *
 * <p>This validator provides consistent error messaging for collection operations and supports
 * various collection types while maintaining type safety.
 */
public final class CollectionValidator {

  private CollectionValidator() {
    throw new AssertionError(
        "CollectionValidator is a utility class and should not be instantiated");
  }

  /**
   * Validates that a collection is non-null and non-empty.
   *
   * @param collection The collection to validate
   * @param parameterName The parameter name for error messaging
   * @param <T> The collection type
   * @return The validated collection
   * @throws NullPointerException if collection is null
   * @throws IllegalArgumentException if collection is empty
   */
  public static <T extends Collection<?>> T requireNonEmpty(T collection, String parameterName) {

    var context = CollectionContext.collection(parameterName);

    Objects.requireNonNull(collection, context.nullParameterMessage());

    if (collection.isEmpty()) {
      throw new IllegalArgumentException(context.emptyMessage());
    }

    return collection;
  }

  /**
   * Validates that an array is non-null and non-empty.
   *
   * @param array The array to validate
   * @param parameterName The parameter name for error messaging
   * @param <T> The array element type
   * @return The validated array
   * @throws NullPointerException if array is null
   * @throws IllegalArgumentException if array is empty
   */
  public static <T> T[] requireNonEmpty(T[] array, String parameterName) {
    var context = CollectionContext.array(parameterName);

    Objects.requireNonNull(array, context.nullParameterMessage());

    if (array.length == 0) {
      throw new IllegalArgumentException(context.emptyMessage());
    }

    return array;
  }

  /**
   * Validates that a collection meets minimum size requirements.
   *
   * @param collection The collection to validate
   * @param minSize The minimum required size
   * @param parameterName The parameter name for error messaging
   * @param <T> The collection type
   * @return The validated collection
   * @throws NullPointerException if collection is null
   * @throws IllegalArgumentException if collection is too small
   */
  public static <T extends Collection<?>> T requireMinSize(
      T collection, int minSize, String parameterName) {

    var context = CollectionContext.collection(parameterName);

    Objects.requireNonNull(collection, context.nullParameterMessage());

    if (collection.size() < minSize) {
      throw new IllegalArgumentException(
          context.customMessage(
              "%s must have at least %d elements, got %d",
              parameterName, minSize, collection.size()));
    }

    return collection;
  }

  /**
   * Validates that a collection size is within bounds.
   *
   * @param collection The collection to validate
   * @param minSize The minimum size (inclusive)
   * @param maxSize The maximum size (inclusive)
   * @param parameterName The parameter name for error messaging
   * @param <T> The collection type
   * @return The validated collection
   * @throws NullPointerException if collection is null
   * @throws IllegalArgumentException if collection size is out of bounds
   */
  public static <T extends Collection<?>> T requireSizeInRange(
      T collection, int minSize, int maxSize, String parameterName) {

    var context = CollectionContext.collection(parameterName);

    Objects.requireNonNull(collection, context.nullParameterMessage());

    int size = collection.size();
    if (size < minSize || size > maxSize) {
      throw new IllegalArgumentException(
          context.customMessage(
              "%s size must be between %d and %d, got %d", parameterName, minSize, maxSize, size));
    }

    return collection;
  }
}
