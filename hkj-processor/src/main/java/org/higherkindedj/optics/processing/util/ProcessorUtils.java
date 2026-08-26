// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;

/**
 * Shared utility methods for annotation processors in the optics module.
 *
 * <p>The helpers here read the type model and derive names. They live together so that a subtlety
 * settled for one processor is settled for all of them.
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
   * Whether a type is a declared type that carries type arguments.
   *
   * <p>{@code List<String>} does; {@code List}, {@code String}, {@code int[]} and a type variable
   * do not. A generator asks this to decide whether a value it narrows by erasure needs the warning
   * answering.
   *
   * @param type the type to test; must not be null
   * @return true when {@code type} is a parameterised declared type
   * @since 0.4.10
   */
  public static boolean hasTypeArguments(TypeMirror type) {
    return type instanceof DeclaredType declared && !declared.getTypeArguments().isEmpty();
  }

  /**
   * The sum type as one of its permitted subtypes instantiates it.
   *
   * <p>A prism for a subtype is written against the sum type the <em>subtype</em> names, not the
   * sum type's own declaration: {@code GenCircle<T> implements GenShape<T>} focuses {@code
   * GenShape<T>}, while {@code Tagged implements GenShape<String>} focuses {@code GenShape<String>}
   * and needs no parameter of its own.
   *
   * @param sumType the sealed type; must not be null
   * @param subtype the permitted subtype whose clause names it; must not be null
   * @return the sum type as {@code subtype} names it, or null when it does not name it directly
   * @since 0.4.10
   */
  public static DeclaredType sumTypeAsNamedBy(TypeElement sumType, TypeElement subtype) {
    List<TypeMirror> candidates = new ArrayList<>(subtype.getInterfaces());
    candidates.add(subtype.getSuperclass());
    for (TypeMirror candidate : candidates) {
      if (candidate instanceof DeclaredType declared && declared.asElement().equals(sumType)) {
        return declared;
      }
    }
    return null;
  }

  /**
   * A method's return type as the given owner sees it.
   *
   * @param types the round's type utilities; must not be null
   * @param owner the instantiated type the method is read on; must not be null
   * @param method the method to read; must not be null
   * @return the return type under {@code owner}'s instantiation
   * @since 0.4.10
   */
  public static TypeMirror returnTypeIn(Types types, DeclaredType owner, ExecutableElement method) {
    return memberOf(types, owner, method).getReturnType();
  }

  /**
   * A method's first parameter type as the given owner sees it.
   *
   * @param types the round's type utilities; must not be null
   * @param owner the instantiated type the method is read on; must not be null
   * @param method the method to read, which must take at least one parameter; must not be null
   * @return the first parameter's type under {@code owner}'s instantiation
   * @since 0.4.10
   */
  public static TypeMirror firstParameterTypeIn(
      Types types, DeclaredType owner, ExecutableElement method) {
    return memberOf(types, owner, method).getParameterTypes().getFirst();
  }

  /**
   * The member as the owner sees it.
   *
   * <p>Cast, not a fallback: {@code asMemberOf} answers with an {@link ExecutableType} for an
   * executable member, and a member read as declared where a substitution was wanted is the very
   * defect these helpers close - better to fail than to quietly return it.
   *
   * <p>A member reached through a <em>raw</em> supertype comes back erased, which is what the
   * language says a raw type's members are. Nothing is done to soften that: reading the declaration
   * instead lets analysis pass and leaves the generator emitting a call the erased member cannot
   * take.
   */
  private static ExecutableType memberOf(
      Types types, DeclaredType owner, ExecutableElement member) {
    return (ExecutableType) types.asMemberOf(owner, member);
  }

  /**
   * Whether a type is the given type parameter, or names it at any depth.
   *
   * <p>A parameter can hide in more places than a type argument. {@code Outer<T>.Inner} names
   * {@code T} through its enclosing type, {@code List<? extends T>} through a wildcard bound,
   * {@code T[]} through an array component, and {@code Foo & Bar<T>} through one arm of an
   * intersection. An enclosing type that is absent, or a static member type, is a {@code NoType},
   * which matches nothing and ends the walk.
   *
   * @param type the type to search; must not be null
   *     <p>A type variable is a leaf: this answers whether the variable is named, not what its own
   *     bound goes on to name, so a self-referential type terminates.
   * @param parameter the element of the type parameter to look for; must not be null
   * @return true when {@code type} names {@code parameter}
   * @since 0.4.10
   */
  public static boolean mentions(TypeMirror type, Element parameter) {
    return switch (type) {
      case TypeVariable variable -> variable.asElement().equals(parameter);
      // Before DeclaredType: javac's intersection implements that interface, so the other order
      // sends an intersection down the declared arm, where it reports no arguments and no
      // enclosing type, and every bound it names is missed.
      case IntersectionType intersection ->
          intersection.getBounds().stream().anyMatch(bound -> mentions(bound, parameter));
      case DeclaredType declared ->
          mentions(declared.getEnclosingType(), parameter)
              || declared.getTypeArguments().stream().anyMatch(a -> mentions(a, parameter));
      case ArrayType array -> mentions(array.getComponentType(), parameter);
      case WildcardType wildcard ->
          (wildcard.getExtendsBound() != null && mentions(wildcard.getExtendsBound(), parameter))
              || (wildcard.getSuperBound() != null
                  && mentions(wildcard.getSuperBound(), parameter));
      default -> false;
    };
  }

  /**
   * Renders a type for a diagnostic, with package qualifiers dropped and type arguments spaced.
   *
   * <p>Type arguments and enclosing types are kept, so {@code java.util.List<java.lang.String>}
   * reads as {@code List<String>} and {@code com.external.Outer.Inner} as {@code Outer.Inner}. A
   * diagnostic that offers a corrected declaration needs both: a rendering that drops either one
   * suggests source that does not compile.
   *
   * @param type the type to render; must not be null
   * @return the rendered name (non-null)
   * @since 0.4.10
   */
  public static String simpleTypeName(TypeMirror type) {
    return type.toString().replaceAll("\\b(?:[a-z][\\p{Alnum}_]*\\.)+", "").replace(",", ", ");
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
