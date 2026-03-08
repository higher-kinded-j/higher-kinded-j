// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Static registry of all concrete Path types and their relationships.
 *
 * <p>This registry is used by the {@link PathTypeMismatchChecker} to determine whether two types
 * belong to the same Path family. It maps fully qualified class names to their simple names and
 * provides lookup methods for type comparison.
 */
public final class PathTypeRegistry {

  private PathTypeRegistry() {}

  /** Set of all registered Path type simple names. */
  private static final Set<String> PATH_SIMPLE_NAMES =
      Set.of(
          // Effect paths
          "MaybePath",
          "EitherPath",
          "TryPath",
          "IOPath",
          "VTaskPath",
          "DefaultVTaskPath",
          "ValidationPath",
          "IdPath",
          "OptionalPath",
          "GenericPath",
          "TrampolinePath",
          "FreePath",
          "FreeApPath",
          "ListPath",
          "StreamPath",
          "VStreamPath",
          "DefaultVStreamPath",
          "NonDetPath",
          "ReaderPath",
          "WithStatePath",
          "WriterPath",
          "LazyPath",
          "CompletableFuturePath",
          // Expression path
          "ForPath",
          // Optics paths
          "FocusPath",
          "AffinePath",
          "TraversalPath");

  /** Maps fully qualified class names to their simple names. */
  private static final Map<String, String> QUALIFIED_TO_SIMPLE =
      Map.ofEntries(
          Map.entry("org.higherkindedj.hkt.effect.MaybePath", "MaybePath"),
          Map.entry("org.higherkindedj.hkt.effect.EitherPath", "EitherPath"),
          Map.entry("org.higherkindedj.hkt.effect.TryPath", "TryPath"),
          Map.entry("org.higherkindedj.hkt.effect.IOPath", "IOPath"),
          Map.entry("org.higherkindedj.hkt.effect.VTaskPath", "VTaskPath"),
          Map.entry("org.higherkindedj.hkt.effect.DefaultVTaskPath", "DefaultVTaskPath"),
          Map.entry("org.higherkindedj.hkt.effect.ValidationPath", "ValidationPath"),
          Map.entry("org.higherkindedj.hkt.effect.IdPath", "IdPath"),
          Map.entry("org.higherkindedj.hkt.effect.OptionalPath", "OptionalPath"),
          Map.entry("org.higherkindedj.hkt.effect.GenericPath", "GenericPath"),
          Map.entry("org.higherkindedj.hkt.effect.TrampolinePath", "TrampolinePath"),
          Map.entry("org.higherkindedj.hkt.effect.FreePath", "FreePath"),
          Map.entry("org.higherkindedj.hkt.effect.FreeApPath", "FreeApPath"),
          Map.entry("org.higherkindedj.hkt.effect.ListPath", "ListPath"),
          Map.entry("org.higherkindedj.hkt.effect.StreamPath", "StreamPath"),
          Map.entry("org.higherkindedj.hkt.effect.VStreamPath", "VStreamPath"),
          Map.entry("org.higherkindedj.hkt.effect.DefaultVStreamPath", "DefaultVStreamPath"),
          Map.entry("org.higherkindedj.hkt.effect.NonDetPath", "NonDetPath"),
          Map.entry("org.higherkindedj.hkt.effect.ReaderPath", "ReaderPath"),
          Map.entry("org.higherkindedj.hkt.effect.WithStatePath", "WithStatePath"),
          Map.entry("org.higherkindedj.hkt.effect.WriterPath", "WriterPath"),
          Map.entry("org.higherkindedj.hkt.effect.LazyPath", "LazyPath"),
          Map.entry("org.higherkindedj.hkt.effect.CompletableFuturePath", "CompletableFuturePath"),
          Map.entry("org.higherkindedj.hkt.expression.ForPath", "ForPath"),
          Map.entry("org.higherkindedj.optics.focus.FocusPath", "FocusPath"),
          Map.entry("org.higherkindedj.optics.focus.AffinePath", "AffinePath"),
          Map.entry("org.higherkindedj.optics.focus.TraversalPath", "TraversalPath"));

  /** Suggested conversion methods between Path types. */
  private static final Map<String, String> CONVERSION_METHODS =
      Map.ofEntries(
          Map.entry("toEitherPath", "EitherPath"),
          Map.entry("toMaybePath", "MaybePath"),
          Map.entry("toTryPath", "TryPath"),
          Map.entry("toOptionalPath", "OptionalPath"),
          Map.entry("toValidationPath", "ValidationPath"),
          Map.entry("toIdPath", "IdPath"));

  /**
   * Returns the total number of registered Path types.
   *
   * @return the count of registered Path types
   */
  public static int registeredTypeCount() {
    return QUALIFIED_TO_SIMPLE.size();
  }

  /**
   * Returns an unmodifiable set of all registered Path type simple names.
   *
   * @return set of simple names
   */
  public static Set<String> allSimpleNames() {
    return PATH_SIMPLE_NAMES;
  }

  /**
   * Checks whether the given fully qualified type name is a registered Path type.
   *
   * @param qualifiedName the fully qualified class name
   * @return true if it is a registered Path type
   */
  public static boolean isPathType(String qualifiedName) {
    return QUALIFIED_TO_SIMPLE.containsKey(qualifiedName);
  }

  /**
   * Checks whether the given simple name is a registered Path type.
   *
   * @param simpleName the simple class name
   * @return true if it is a registered Path type simple name
   */
  public static boolean isPathTypeBySimpleName(String simpleName) {
    return PATH_SIMPLE_NAMES.contains(simpleName);
  }

  /**
   * Returns the Path category (simple name) for a given fully qualified type name.
   *
   * @param qualifiedName the fully qualified class name
   * @return the simple name if registered, or empty if not a known Path type
   */
  public static Optional<String> getPathCategory(String qualifiedName) {
    return Optional.ofNullable(QUALIFIED_TO_SIMPLE.get(qualifiedName));
  }

  /**
   * Checks whether two fully qualified type names belong to the same Path family.
   *
   * <p>Two types are in the same family if they resolve to the same simple name in the registry.
   * For example, "org.higherkindedj.hkt.effect.MaybePath" and itself are the same family.
   *
   * @param qualifiedName1 the first fully qualified class name
   * @param qualifiedName2 the second fully qualified class name
   * @return true if both are registered Path types with the same simple name
   */
  public static boolean areSamePathFamily(String qualifiedName1, String qualifiedName2) {
    String simple1 = QUALIFIED_TO_SIMPLE.get(qualifiedName1);
    String simple2 = QUALIFIED_TO_SIMPLE.get(qualifiedName2);
    if (simple1 == null || simple2 == null) {
      return false;
    }
    return simple1.equals(simple2);
  }

  /**
   * Suggests a conversion method to convert from one Path type to another.
   *
   * @param fromType the simple name of the source Path type
   * @param toType the simple name of the target Path type
   * @return the conversion method name if one exists, or empty
   */
  public static Optional<String> suggestedConversion(String fromType, String toType) {
    for (Map.Entry<String, String> entry : CONVERSION_METHODS.entrySet()) {
      if (entry.getValue().equals(toType)) {
        return Optional.of(entry.getKey() + "()");
      }
    }
    return Optional.empty();
  }
}
