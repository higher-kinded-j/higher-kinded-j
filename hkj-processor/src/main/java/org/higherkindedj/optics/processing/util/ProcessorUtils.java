// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

/**
 * Shared utility methods for annotation processors in the optics module.
 *
 * <p>This class provides common string manipulation utilities used across multiple processors.
 */
public final class ProcessorUtils {

  private ProcessorUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Resolves a wildcard type to its effective type for focus extraction.
   *
   * <ul>
   *   <li>{@code ? extends T} → {@code T} (upper bound)
   *   <li>{@code ? super T} → {@code null} (caller should treat as Object)
   *   <li>{@code ?} (unbounded) → {@code null} (caller should treat as Object)
   * </ul>
   *
   * <p>If the type is not a wildcard, it is returned unchanged.
   *
   * @param type the type to resolve
   * @return the resolved type, or null if the wildcard should be treated as Object
   * @since 0.4.0
   */
  public static TypeMirror resolveWildcard(TypeMirror type) {
    if (type instanceof WildcardType wildcard) {
      TypeMirror extendsBound = wildcard.getExtendsBound();
      if (extendsBound != null) {
        return extendsBound;
      }
      // ? super T or unbounded ? — caller should use Object
      return null;
    }
    return type;
  }

  /**
   * The name the effect's type variable takes in a traversal generated for this record, which the
   * record must not have taken for itself.
   *
   * <p>{@code modifyF} is generated inside a method that carries the record's type variables, so a
   * record declaring its own {@code F} would have the effect shadowed by it, and the traversal
   * would then be written in terms of the wrong one. Both the processor, which declares the
   * variable, and the generators, which write uses of it into the body, read the name from here so
   * that they cannot disagree about it.
   *
   * @param recordElement the annotated record
   * @return {@code F}, or {@code F} followed by the first number the record leaves free
   * @since 0.4.10
   */
  public static String effectVariableName(TypeElement recordElement) {
    Set<String> taken =
        recordElement.getTypeParameters().stream()
            .map(parameter -> parameter.getSimpleName().toString())
            .collect(Collectors.toSet());
    String name = "F";
    for (int suffix = 1; taken.contains(name); suffix++) {
      name = "F" + suffix;
    }
    return name;
  }

  /**
   * Whether a container's type arguments leave an optic instance composed over it undenotable.
   *
   * <p>An optic handed to a Focus path — {@code .some(Affines.eitherRight())}, {@code
   * .each(EachInstances.mapValuesEach())} — has its own type arguments inferred from the field
   * type. A raw container offers none to infer them from and a wildcard has no ground
   * instantiation, so in either case javac cannot instantiate the optic's own type variables and
   * the composition call does not apply to the path.
   *
   * <p>Only the container's own arguments count: {@code Either<String, ? extends Leaf>} is
   * undenotable, {@code Either<String, List<? extends Leaf>>} is not, because the wildcard there
   * belongs to the {@code List} and {@code Either} still has a ground instantiation.
   *
   * @param type the type to inspect
   * @return true when {@code type} is a declared generic type that is raw or carries a wildcard
   *     type argument
   * @since 0.4.10
   */
  public static boolean hasUndenotableTypeArguments(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    DeclaredType declaredType = (DeclaredType) type;
    List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
    if (typeArguments.isEmpty()) {
      // A generic element with no arguments is raw; a non-generic one simply has none to give.
      return !((TypeElement) declaredType.asElement()).getTypeParameters().isEmpty();
    }
    return typeArguments.stream().anyMatch(arg -> arg.getKind() == TypeKind.WILDCARD);
  }

  /**
   * Converts a string to camelCase.
   *
   * <p>Handles various input formats:
   *
   * <ul>
   *   <li>SNAKE_CASE: "MY_CONSTANT" → "myConstant"
   *   <li>ALL_CAPS: "MONDAY" → "monday"
   *   <li>PascalCase: "MyClass" → "myClass"
   *   <li>Already camelCase: "myMethod" → "myMethod"
   * </ul>
   *
   * @param s the string to convert
   * @return the camelCase version of the string
   */
  public static String toCamelCase(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }

    // Handle SNAKE_CASE (with underscores)
    if (s.contains("_")) {
      String[] parts = s.split("_");
      StringBuilder camelCaseString = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
      for (int i = 1; i < parts.length; i++) {
        if (!parts[i].isEmpty()) {
          camelCaseString
              .append(parts[i].substring(0, 1).toUpperCase(Locale.ROOT))
              .append(parts[i].substring(1).toLowerCase(Locale.ROOT));
        }
      }
      return camelCaseString.toString();
    }

    // Handle ALL_CAPS (no underscores but all uppercase letters)
    if (isAllUpperCase(s)) {
      return s.toLowerCase(Locale.ROOT);
    }

    // Handle PascalCase
    if (Character.isUpperCase(s.charAt(0))) {
      return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    return s;
  }

  /**
   * Checks if a string contains only uppercase letters.
   *
   * <p>Non-letter characters are ignored in the check.
   *
   * @param s the string to check
   * @return true if all letter characters are uppercase, false otherwise
   */
  public static boolean isAllUpperCase(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isLetter(c) && !Character.isUpperCase(c)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Capitalises the first character, locale-neutrally; null and empty inputs pass through.
   *
   * @param s the string, may be null
   * @return the capitalised string, or {@code s} unchanged when null or empty
   */
  public static String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
  }
}
