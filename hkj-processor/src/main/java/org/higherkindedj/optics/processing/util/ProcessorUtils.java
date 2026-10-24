// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import com.palantir.javapoet.WildcardTypeName;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

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
   * The type a type argument stands for, boxed: the argument itself, the bound of an {@code ?
   * extends} wildcard, or {@code Object} for an unbounded or super-bounded wildcard, which stands
   * for no type of its own.
   *
   * <p>A generated method focuses on this type, and writes it out wherever an explicit type
   * argument is needed, because the wildcard the declaration wrote can be neither.
   *
   * @param typeArgument a type argument as written
   * @param targetPackage the package the generated file is written into; must not be null
   * @return the type it stands for, boxed
   * @since 0.4.11
   */
  public static TypeName resolvedTypeNameOf(TypeMirror typeArgument, String targetPackage) {
    TypeMirror resolved = resolveWildcard(typeArgument);
    return resolved == null
        ? ClassName.get(Object.class)
        : typeNameOf(resolved, targetPackage).box();
  }

  /**
   * The supertype of {@code type} declared by {@code target}, instantiated with the type arguments
   * it is reached by.
   *
   * <p>The instantiation is the point: {@code Box<X> extends Base<X>} reached from {@code Box<U>}
   * answers {@code Base<U>}, which is the type a generated cast has to name and the type a match
   * against the source has to be made against. {@code type} itself counts as a match, so a caller
   * asking for a type's own element gets it back under its own arguments.
   *
   * @param typeUtils the round's type utilities; must not be null
   * @param type the declared type to search from; must not be null
   * @param target the declaring element to look for; must not be null
   * @return the instantiated supertype, or null when {@code target} does not declare one
   * @since 0.4.10
   */
  public static TypeMirror supertypeOf(Types typeUtils, TypeMirror type, TypeElement target) {
    Name targetName = target.getQualifiedName();
    Deque<TypeMirror> queue = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    queue.add(type);
    while (!queue.isEmpty()) {
      TypeMirror current = queue.poll();
      if (!seen.add(current.toString())) {
        continue;
      }
      // Callers search from a type they have already resolved to a TypeElement, and every
      // supertype of a declared type is itself declared.
      TypeElement element = (TypeElement) ((DeclaredType) current).asElement();
      if (element.getQualifiedName().contentEquals(targetName)) {
        return current;
      }
      queue.addAll(typeUtils.directSupertypes(current));
    }
    return null;
  }

  /**
   * Whether a member, and every type enclosing it, is visible from a generated class's package.
   *
   * <p>Generated code names what it calls; a member it cannot see is a compile error in a file its
   * author never wrote. {@code protected} counts as package access here, because a generated
   * companion extends nothing — being a subclass is never how it reaches anything.
   *
   * @param elements the round's element utilities
   * @param member the member or type to test
   * @param targetPackage the package the generated class is written into
   * @return true when the member and every type enclosing it can be named from there
   * @since 0.4.10
   */
  public static boolean reachableFrom(Elements elements, Element member, String targetPackage) {
    for (Element current = member;
        current.getKind() != ElementKind.PACKAGE;
        current = current.getEnclosingElement()) {
      if (!visibleFrom(
          current, elements.getPackageOf(current).getQualifiedName().toString(), targetPackage)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether one type or member, declared in {@code home}, can be named from {@code targetPackage}:
   * not private, and either public or declared in that package. This is the one visibility rule
   * generated code is held to, whether what is named is a member it calls or an annotation it
   * writes.
   */
  private static boolean visibleFrom(Element element, String home, String targetPackage) {
    Set<Modifier> modifiers = element.getModifiers();
    return !modifiers.contains(Modifier.PRIVATE)
        && (modifiers.contains(Modifier.PUBLIC) || home.equals(targetPackage));
  }

  /**
   * The first type named anywhere inside {@code type} that a generated class in {@code
   * targetPackage} could not name, or null when every one of them is reachable.
   *
   * <p>Written out, a type names more than its own head: {@code Iso<Box<Secret>, String>} names
   * {@code Secret}, and a field declaring it does not compile wherever {@code Secret} cannot be
   * seen. So the walk descends through type arguments, array components, wildcard bounds and
   * enclosing types, the same layers a type variable can hide in.
   *
   * @param elements the round's element utilities
   * @param type the type the generated source will write out
   * @param targetPackage the package the generated class is written into
   * @return the first unreachable type element, or null
   * @since 0.4.10
   */
  public static TypeElement firstUnreachableIn(
      Elements elements, TypeMirror type, String targetPackage) {
    switch (type.getKind()) {
      case ARRAY -> {
        return firstUnreachableIn(elements, ((ArrayType) type).getComponentType(), targetPackage);
      }
      case WILDCARD -> {
        WildcardType wildcard = (WildcardType) type;
        for (TypeMirror bound :
            new TypeMirror[] {wildcard.getExtendsBound(), wildcard.getSuperBound()}) {
          if (bound != null) {
            TypeElement unreachable = firstUnreachableIn(elements, bound, targetPackage);
            if (unreachable != null) {
              return unreachable;
            }
          }
        }
        return null;
      }
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        if (!reachableFrom(elements, element, targetPackage)) {
          return element;
        }
        for (TypeMirror argument : declared.getTypeArguments()) {
          TypeElement unreachable = firstUnreachableIn(elements, argument, targetPackage);
          if (unreachable != null) {
            return unreachable;
          }
        }
        return firstUnreachableIn(elements, declared.getEnclosingType(), targetPackage);
      }
      default -> {
        return null;
      }
    }
  }

  /**
   * Whether a type is written raw: a generic element named with no arguments at all.
   *
   * <p>Raw, not merely bare. A non-generic type has no arguments either and is not raw, and the
   * difference matters because a raw supertype erases every member of the type below it, whatever
   * that member declares.
   *
   * @param declared the type as it was written; must not be null
   * @return true when its element declares type parameters and the type supplies none
   * @since 0.4.10
   */
  public static boolean isRaw(DeclaredType declared) {
    return !((TypeElement) declared.asElement()).getTypeParameters().isEmpty()
        && declared.getTypeArguments().isEmpty();
  }

  /**
   * A type, or null where it is a wildcard, which names no one type.
   *
   * @param type the type; must not be null
   * @return the type, or null where it is a wildcard
   * @since 0.5.0
   */
  public static TypeMirror unlessWildcard(TypeMirror type) {
    return type.getKind() == TypeKind.WILDCARD ? null : type;
  }

  /**
   * The first raw type named anywhere in {@code type}, or null when none is raw.
   *
   * <p>A generic type written without its arguments, at any depth: a bare {@code List} head, a
   * {@code List} inside an argument or a wildcard bound, a {@code List[]} component. The enclosing
   * link counts too: {@code Outer.Inner} is raw in {@code Outer} even where {@code Inner} declares
   * nothing of its own (JLS 4.8); an absent or static enclosing type is a {@code NoType}, which
   * ends the walk. A caller that copies the type verbatim into generated source asks this first,
   * because every raw name it copies is a {@code [rawtypes]} warning in the file it lands in.
   *
   * <p>An intersection is not walked: a type parameter's bounds are read from {@code getBounds()},
   * which flattens them into one bound per type, never from {@code getUpperBound()}, which hands
   * back a single intersection mirror this answers "no raw" for.
   *
   * @param type the type as it was written
   * @return the element of the first raw type named, or null
   * @since 0.4.11
   */
  public static TypeElement firstRawIn(TypeMirror type) {
    switch (type.getKind()) {
      case ARRAY -> {
        return firstRawIn(((ArrayType) type).getComponentType());
      }
      case WILDCARD -> {
        // A bound is written out with the wildcard that carries it, so `? extends List` puts a
        // raw List in the generated file as surely as a bare one does.
        WildcardType wildcard = (WildcardType) type;
        for (TypeMirror bound :
            new TypeMirror[] {wildcard.getExtendsBound(), wildcard.getSuperBound()}) {
          if (bound != null) {
            TypeElement raw = firstRawIn(bound);
            if (raw != null) {
              return raw;
            }
          }
        }
        return null;
      }
      case DECLARED -> {
        DeclaredType declared = (DeclaredType) type;
        if (isRaw(declared)) {
          return (TypeElement) declared.asElement();
        }
        for (TypeMirror argument : declared.getTypeArguments()) {
          TypeElement raw = firstRawIn(argument);
          if (raw != null) {
            return raw;
          }
        }
        return firstRawIn(declared.getEnclosingType());
      }
      default -> {
        return null;
      }
    }
  }

  /**
   * Whether a value of type {@code from} reaches {@code to} only through an unchecked conversion
   * (JLS 5.1.9): it is assignable, and not as a subtype. A raw {@code List} reaches {@code
   * List<String>} that way, and so does a class that extends a raw {@code ArrayList}.
   *
   * <p>Generated code that passes or hands back such a value carries an {@code [unchecked]} warning
   * in a file its author cannot edit, so a caller holding a generated read or call to a type asks
   * this as well as whether the value is assignable at all. Boxing and unboxing are assignable
   * without subtyping too, and convert nothing unchecked, so a primitive on either side answers
   * false. So does a reifiable target, such as {@code List<?>}, which javac converts a raw {@code
   * List} to without a warning.
   *
   * @param types the round's type utilities; must not be null
   * @param from the type of the value passed or handed back; must not be null
   * @param to the type it is passed or handed back as; must not be null
   * @return true when the conversion is unchecked
   * @since 0.5.0
   */
  public static boolean convertsUnchecked(Types types, TypeMirror from, TypeMirror to) {
    return !from.getKind().isPrimitive()
        && !to.getKind().isPrimitive()
        && types.isAssignable(from, to)
        && !types.isSubtype(from, to)
        && !reifiableTarget(to);
  }

  /**
   * Whether the target of an unchecked conversion is reifiable (JLS 4.7), which javac converts to
   * without a warning (JLS 5.1.9): every type argument it names, and every one a type enclosing it
   * names, an unbounded wildcard, or an array of such a type. A wildcard bounded above by {@code
   * Object} alone, annotated or not, is unbounded too, as javac reads it (JLS 4.5.1). Only a
   * declared or an array type is the target of an unchecked conversion, and the walk out through
   * enclosing types ends at the {@code NoType} past the outermost.
   */
  private static boolean reifiableTarget(TypeMirror target) {
    return target.getKind() == TypeKind.ARRAY
        ? reifiableTarget(((ArrayType) target).getComponentType())
        : Stream.iterate(
                target,
                type -> type.getKind() == TypeKind.DECLARED,
                type -> ((DeclaredType) type).getEnclosingType())
            .flatMap(type -> ((DeclaredType) type).getTypeArguments().stream())
            .allMatch(
                argument ->
                    argument instanceof WildcardType wildcard
                        && wildcard.getSuperBound() == null
                        && (wildcard.getExtendsBound() == null
                            || isObject(wildcard.getExtendsBound())));
  }

  /**
   * Whether a type is {@code java.lang.Object}, read by its class so an annotation is no matter.
   */
  private static boolean isObject(TypeMirror type) {
    return type instanceof DeclaredType declared
        && ((TypeElement) declared.asElement())
            .getQualifiedName()
            .contentEquals("java.lang.Object");
  }

  /**
   * Whether a type reaches its members erased, as javac treats a raw type (JLS 4.8): raw itself, or
   * a member type of a raw type, as {@code Outer.Builder} is for an inner class of a generic {@code
   * Outer} named without its arguments. A static nested type has no enclosing instance, so the walk
   * out through enclosing types ends there.
   *
   * @param type the type as it is reached; must not be null
   * @return true when its members are read erased
   * @since 0.5.0
   */
  public static boolean erasesItsMembers(DeclaredType type) {
    TypeMirror enclosing = type.getEnclosingType();
    return isRaw(type)
        || (enclosing.getKind() == TypeKind.DECLARED && erasesItsMembers((DeclaredType) enclosing));
  }

  /**
   * Whether a parameter takes any collection of its elements: an {@code Iterable}, a {@code Map},
   * or where Guava is on the classpath a {@code Table}, with type arguments that are all {@code ?
   * extends} or unbounded wildcards. A Lombok {@code @Singular} collection's setter takes one,
   * {@code tags(Collection<? extends String>)}, and adds what it is handed to what the builder
   * holds; beside a clear method named as {@link #singularClearNames} names it, that is how such a
   * setter is told apart. A container with no type arguments, as a {@code ByteString} is an {@code
   * Iterable<Byte>} of its own, takes nothing of the kind.
   *
   * @param types the round's type utilities; must not be null
   * @param elements the round's element utilities; must not be null
   * @param parameter the parameter's type, as the call reads it; must not be null
   * @return true when it takes any {@code Iterable}, {@code Map} or {@code Table} of its elements
   * @since 0.5.0
   */
  public static boolean takesAnyCollection(Types types, Elements elements, TypeMirror parameter) {
    if (parameter.getKind() != TypeKind.DECLARED) {
      return false;
    }
    List<? extends TypeMirror> arguments = ((DeclaredType) parameter).getTypeArguments();
    return !arguments.isEmpty()
        && Stream.of("java.lang.Iterable", "java.util.Map", "com.google.common.collect.Table")
            .map(elements::getTypeElement)
            .filter(Objects::nonNull)
            .anyMatch(
                container ->
                    types.isSubtype(types.erasure(parameter), types.erasure(container.asType())))
        && arguments.stream()
            .allMatch(
                argument ->
                    argument.getKind() == TypeKind.WILDCARD
                        && ((WildcardType) argument).getSuperBound() == null);
  }

  /**
   * Whether a call to an instance member on {@code receiver} is unchecked (JLS 4.8): the receiver
   * reaches the member's declaring type as a raw type, as {@link #erasesItsMembers} reads one, and
   * erasing that type changes one of the member's parameters. {@code id(T)} called on a raw {@code
   * Builder} is, and so is {@code add(E)} called on a class that extends a raw {@code ArrayList};
   * {@code name(String)} on a raw {@code Builder} is not, since erasure leaves its parameter as it
   * is.
   *
   * <p>javac reports such a call as {@code [unchecked]} wherever it is made, so generated code that
   * makes one carries the warning in a file its author cannot edit.
   *
   * @param types the round's type utilities; must not be null
   * @param receiver the type the call is made on; must not be null
   * @param member the instance method or constructor called; must not be null
   * @return true when the call is unchecked
   * @since 0.5.0
   */
  public static boolean callsUnchecked(
      Types types, DeclaredType receiver, ExecutableElement member) {
    // A member is reached on the type it is called on or on one of its supertypes.
    DeclaredType declaring =
        (DeclaredType) supertypeOf(types, receiver, (TypeElement) member.getEnclosingElement());
    return erasesItsMembers(declaring)
        && member.getParameters().stream()
            .map(Element::asType)
            .anyMatch(parameter -> !types.isSameType(parameter, types.erasure(parameter)));
  }

  /**
   * The annotations a generated member adds for the raw types it writes out or infers:
   * {@code @SuppressWarnings("rawtypes")} when one of its types names a raw type, none otherwise.
   *
   * <p>The raw type is the author's: it warns where they declared it, and their own suppression
   * answers for it there. A generated file is a separate compilation unit that suppression does not
   * reach, so a member restating the type, or holding a lambda whose parameter javac infers to it,
   * answers for it itself. Only {@code rawtypes}: an unchecked operation that the author's types
   * imply is a hole in generated code, never the author's to accept.
   *
   * <p>Suppression is the answer where a generated member merely restates a type its author
   * declared. Where the generated code would do more than restate it, inferring an optic instance
   * from it or rebuilding through it unchecked, the shape is refused instead, as
   * {@code @ImportOptics} does for a raw source type, or the generator writes a cast of its own
   * that it answers for, and suppresses {@code unchecked} for that cast. {@code @GenerateFocus},
   * for one, casts an instance typed for a container's element to the raw or wildcard {@code
   * Optional}, {@code Maybe}, {@code List}, {@code Set}, {@code Collection} or {@code Kind} a
   * component declares: reading through it gives that element, and writing builds a new container
   * that the declaration admits. {@link #warningSuppression} merges that {@code unchecked} with any
   * {@code rawtypes} the member needs. A generator may also refuse for a reason of its own:
   * {@code @PathVia} refuses a raw type in a bridged signature because the bridge builds its Path
   * from the effect's type arguments, while answering for a raw bound on its class.
   *
   * <p>A declaration takes one {@code @SuppressWarnings}, so a member needing a second token for a
   * reason of its own merges both into a single annotation rather than adding another.
   *
   * @param types the types the member may write out or infer; a wider set only widens where the
   *     suppression lands; must not be null
   * @return the suppression when {@link #firstRawIn} finds a raw type in any of them, else no
   *     annotations
   * @since 0.4.11
   */
  public static List<AnnotationSpec> rawTypesSuppression(List<? extends TypeMirror> types) {
    return warningSuppression(namesRawType(types), false);
  }

  /**
   * Whether any of the types names a raw type, as {@link #firstRawIn} finds one.
   *
   * @param types the types a generated member may write out or infer; must not be null
   * @return true when one of them names a raw type
   * @since 0.5.0
   */
  public static boolean namesRawType(List<? extends TypeMirror> types) {
    return types.stream().anyMatch(type -> firstRawIn(type) != null);
  }

  /**
   * Whether a member generated around a type that redeclares {@code owner}'s type parameters names
   * a raw type, in the type or in one of those bounds.
   *
   * @param written the type the member is generated around; must not be null
   * @param owner the type whose type parameters the member redeclares; must not be null
   * @return true when the type or one of those bounds names a raw type
   * @since 0.5.0
   */
  public static boolean namesRawType(TypeMirror written, TypeElement owner) {
    return namesRawType(withBounds(written, owner.getTypeParameters()));
  }

  /**
   * The one {@code @SuppressWarnings} a generated member carries: {@code rawtypes} for a raw type
   * it restates, as {@link #rawTypesSuppression(List)} describes, and {@code unchecked} for a cast
   * of its own that its generator answers for. A declaration takes one {@code SuppressWarnings}, so
   * a member needing both carries them as one array, written the way an author would write it.
   *
   * @param rawTypes whether the member restates a raw type
   * @param unchecked whether the member writes such a cast
   * @return the suppression, or no annotations when neither applies
   * @since 0.5.0
   */
  public static List<AnnotationSpec> warningSuppression(boolean rawTypes, boolean unchecked) {
    AnnotationSpec.Builder suppression = AnnotationSpec.builder(SuppressWarnings.class);
    if (rawTypes && unchecked) {
      return List.of(suppression.addMember("value", "{$S, $S}", "rawtypes", "unchecked").build());
    }
    if (rawTypes || unchecked) {
      return List.of(
          suppression.addMember("value", "$S", rawTypes ? "rawtypes" : "unchecked").build());
    }
    return List.of();
  }

  /**
   * The {@code @SuppressWarnings("rawtypes")} annotations for a generated member that redeclares
   * {@code owner}'s type parameters as well as naming a type of its own: it writes those bounds out
   * too, so a raw bound reaches it even where the type it is generated around names none.
   *
   * <p>Callers pass the type the member is generated around, not the one it finally writes. Which
   * of the two a generator emits, a component or the element it widens to, is that generator's
   * detail, and asking the wider question only widens where the suppression lands.
   *
   * @param written the type the member is generated around; must not be null
   * @param owner the type whose type parameters the member redeclares; must not be null
   * @return the suppression when the type or one of those bounds names a raw type, else no
   *     annotations
   * @since 0.4.11
   */
  public static List<AnnotationSpec> rawTypesSuppression(TypeMirror written, TypeElement owner) {
    return rawTypesSuppression(written, owner.getTypeParameters());
  }

  /**
   * The {@code @SuppressWarnings("rawtypes")} annotations for a generated member that redeclares
   * the given type parameters as well as naming a type of its own. It serves a member that
   * redeclares more than one type's parameters, such as those {@link #typeParametersInScope}
   * returns.
   *
   * @param written the type the member is generated around; must not be null
   * @param redeclared the type parameters the member redeclares, bounds and all; must not be null
   * @return the suppression when the type or one of those bounds names a raw type, else no
   *     annotations
   * @since 0.4.11
   */
  public static List<AnnotationSpec> rawTypesSuppression(
      TypeMirror written, List<? extends TypeParameterElement> redeclared) {
    return rawTypesSuppression(withBounds(written, redeclared));
  }

  /** A type with the bounds of the type parameters a member redeclares beside it. */
  private static List<TypeMirror> withBounds(
      TypeMirror written, List<? extends TypeParameterElement> redeclared) {
    return Stream.concat(
            Stream.of(written),
            redeclared.stream().flatMap(parameter -> parameter.getBounds().stream()))
        .toList();
  }

  /**
   * The type parameters a use of {@code type} has to supply, outermost first: those of each class
   * it is an inner class of, then its own.
   *
   * <p>An inner class is written under its enclosing class's arguments, {@code Outer<X>.Line}, and
   * left without them it is raw (JLS 4.8) even when it declares no parameters of its own. A static
   * member, like a top-level type, has no enclosing instance type, so it contributes its own
   * parameters alone. The walk follows {@code getEnclosingType}, as {@link #firstRawIn} does.
   *
   * @param type the type a generated member names; must not be null
   * @return the parameters in scope for it, possibly empty; unmodifiable
   * @since 0.4.11
   */
  public static List<TypeParameterElement> typeParametersInScope(TypeElement type) {
    return typeParametersNamedBy((DeclaredType) type.asType());
  }

  private static List<TypeParameterElement> typeParametersNamedBy(DeclaredType declared) {
    TypeMirror enclosing = declared.getEnclosingType();
    Stream<TypeParameterElement> outer =
        enclosing.getKind() == TypeKind.DECLARED
            ? typeParametersNamedBy((DeclaredType) enclosing).stream()
            : Stream.empty();
    return Stream.concat(outer, ((TypeElement) declared.asElement()).getTypeParameters().stream())
        .toList();
  }

  /**
   * Whether a declared type carries an instantiation for {@code asMemberOf} to substitute.
   *
   * <p>Asked before reading a member under a type, because {@code asMemberOf} does the wrong thing
   * at both ends of the question. Where there is nothing to substitute it can still lose what the
   * member declares: under a raw site javac erases every member, so a field typed {@code
   * List<String>} on a raw {@code Holder} comes back as {@code List} and matches no container.
   * Where there is something to substitute, skipping it hands back a variable the caller never
   * wrote.
   *
   * <p>The enclosing chain counts, not just the type's own arguments. A member of {@code
   * Outer<List<String>>.Holder} speaks {@code Outer}'s variables even though {@code Holder}
   * declares none of its own, and a chain that ends without arguments anywhere is either wholly
   * non-generic or raw - in both of which the declaration was already the answer.
   *
   * <p>This asks about the <em>site's own</em> arguments, which is not the same question as whether
   * substitution has anything to do. A member inherited from {@code Emails<Email>} is substituted
   * under a non-generic spec, whose chain carries no arguments at all; guarding that read with this
   * would read the declaration and defeat the substitution. Use it where the member is declared on
   * the site itself.
   *
   * @param type the instantiated type a member is about to be read under; must not be null
   * @return true when some link of its enclosing chain carries type arguments
   * @since 0.4.10
   */
  public static boolean carriesInstantiation(DeclaredType type) {
    TypeMirror current = type;
    while (current instanceof DeclaredType declared) {
      if (!declared.getTypeArguments().isEmpty()) {
        return true;
      }
      current = declared.getEnclosingType();
    }
    return false;
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
   * @return the sum type as {@code subtype} names it, or the sum type itself when the clause does
   *     not resolve and javac's own error is the one worth reading
   * @since 0.4.10
   */
  public static DeclaredType sumTypeAsNamedBy(TypeElement sumType, TypeElement subtype) {
    // Only the implemented interfaces: a sealed type reached here is an interface, so a subtype
    // that is permitted by it names it there. A superclass could never be the match.
    for (TypeMirror candidate : subtype.getInterfaces()) {
      // Cast, not a pattern: an implements clause yields declared types only - an unresolvable one
      // is an ErrorType, which is a DeclaredType too - so there is no other kind to test for.
      DeclaredType declared = (DeclaredType) candidate;
      if (declared.asElement().equals(sumType)) {
        return declared;
      }
    }
    return (DeclaredType) sumType.asType();
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
   * A method's return type as a call made on {@code receiver} reads it: on the receiver captured,
   * as javac captures the type of the expression a call is made on. Each wildcard the receiver
   * carries is a type of its own there, bounded as the parameter it stands for is, so a self-typed
   * {@code B name(String)} called on a {@code SupBuilder<?, ?>}, as Lombok's {@code @SuperBuilder}
   * hands one back, returns a builder, where {@link #returnTypeIn} reads the wildcard itself.
   *
   * @param types the round's type utilities; must not be null
   * @param receiver the type the call is made on; must not be null
   * @param method the method called; must not be null
   * @return the return type the call has
   * @since 0.5.0
   */
  public static TypeMirror returnTypeCalledOn(
      Types types, DeclaredType receiver, ExecutableElement method) {
    return returnTypeIn(types, (DeclaredType) types.capture(receiver), method);
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
   * Whether a method, read on {@code owner}, hands back an {@code owner}: a wither a generated lens
   * rebuilds through has to, since the lens returns what it returns as the owner.
   *
   * <p>Two shapes do. A return that is {@code owner} or a subtype of it, read under {@code owner}'s
   * instantiation or, as the call reads it, on {@code owner} captured: a self-typed {@code S
   * withName(String)} on a {@code Shape<?>} returns a type of its own there, bounded by {@code
   * Shape}, where read as declared it returns the wildcard itself. And a retag, {@code <U> Draft<U>
   * withId(String)} on a {@code Draft<T>}: the owner's class with some arguments replaced by type
   * variables the method declares, which the call infers back to the owner's own. That inference is
   * repeated here. Each such variable is bound to the owner's argument where the return first names
   * it against a concrete argument; a wildcard argument binds nothing, since the call reads it as a
   * range rather than a type. Every bound of each variable then has to admit its argument with all
   * of them in place, as {@link #boundsRefusing} reads it, since a bound may name another ({@code
   * <A, B extends A>}) or the variable itself ({@code <U extends Comparable<? super U>>}), and the
   * return with them in place has to be the owner. A variable that only a wildcard stands for is
   * left unbound, which refuses a retag javac might still infer. A raw return, one under other
   * arguments ({@code Draft<String>}) and a supertype hand back something else.
   *
   * @param types the round's type utilities; must not be null
   * @param owner the type the method is read on, as the generated code names it; must not be null
   * @param method the method to read; must not be null
   * @return true when its return is an {@code owner}, or infers to one
   * @since 0.4.11
   */
  public static boolean returnsOwner(Types types, DeclaredType owner, ExecutableElement method) {
    return types.isSubtype(returnTypeIn(types, owner, method), owner)
        || types.isSubtype(returnTypeCalledOn(types, owner, method), owner)
        || retagsOwner(types, owner, method);
  }

  private static boolean retagsOwner(Types types, DeclaredType owner, ExecutableElement method) {
    // The call reads the method on its receiver's captured type, where each of the owner's
    // wildcards is a type of its own, and binds the method's variables against the owner as
    // written, which is what the generated lens hands its result back as.
    TypeMirror returned = returnTypeCalledOn(types, owner, method);
    // asElement answers null for a primitive or void return, which equals no element.
    if (!owner.asElement().equals(types.asElement(returned))) {
      return false;
    }
    List<? extends TypeMirror> returnedArguments = ((DeclaredType) returned).getTypeArguments();
    List<? extends TypeMirror> ownerArguments = owner.getTypeArguments();
    Map<Element, TypeMirror> inferred = new HashMap<>();
    List<TypeVariable> variables = new ArrayList<>();
    for (int i = 0; i < returnedArguments.size(); i++) {
      TypeMirror argument = returnedArguments.get(i);
      boolean bindable =
          argument.getKind() == TypeKind.TYPEVAR
              && method.getTypeParameters().contains(((TypeVariable) argument).asElement())
              && ownerArguments.get(i).getKind() != TypeKind.WILDCARD;
      if (bindable) {
        TypeVariable variable = (TypeVariable) argument;
        // The first binding stands, as it does for inference; a later place is checked, not bound.
        if (inferred.putIfAbsent(variable.asElement(), ownerArguments.get(i)) == null) {
          variables.add(variable);
        }
      }
    }
    if (inferred.isEmpty()) {
      return false;
    }
    return variables.stream()
            .allMatch(variable -> boundsRefusing(types, variable, inferred).isEmpty())
        && substitute(types, returned, inferred, List.of())
            .map(substituted -> types.isSubtype(substituted, owner))
            .orElse(false);
  }

  /**
   * The bounds of {@code variable} that refuse the type {@code bindings} binds it to, in the order
   * the variable declares them, each read with every variable {@code bindings} holds replaced by
   * its binding: empty when every bound admits it. This is the check javac makes, without a
   * warning, of a type argument a method call is given or infers. So a bound may name another
   * variable ({@code <A, B extends A>}) or the variable itself ({@code <T extends Comparable<T>>}),
   * an intersection is read arm by arm, and a binding that is itself a type variable is weighed by
   * its own bounds: a {@code U extends Integer} passes {@code Number}, and an unbounded {@code U}
   * does not.
   *
   * <p>A binding passes a bound as a subtype of it, or by a conversion javac makes without a
   * warning: a class extending a raw {@code ArrayList} passes the reifiable {@code Collection<?>},
   * and {@code Collection<? extends Object>} too. One that reaches the bound only through an
   * unchecked conversion ({@link #convertsUnchecked}), as a raw {@code Comparable} reaches {@code
   * Comparable<Money>}, is refused, since javac would warn and erase the call's result. This is
   * laxer than javac's check of a parameterised type's own arguments (JLS 4.5), which no raw
   * supertype passes.
   *
   * <p>A variable {@code bindings} leaves out reads as itself, as javac reads one it does not
   * substitute. A bound {@link #substitute} has no reading for, such as a member of a generic class
   * named through a raw one, is left to javac, which refuses it where it is declared.
   *
   * <p>The bounds are read off the mirror given, so one read off a member under an instantiation,
   * such as a method's return read on its owner, names the owner's arguments rather than its
   * parameters.
   *
   * @param types the round's type utilities; must not be null
   * @param variable the variable whose bounds are checked; must not be null
   * @param bindings each variable, by its element, to the reference type it stands for, never a
   *     wildcard; must not be null, and must hold {@code variable}
   * @return the bounds that refuse the binding, each read with the bindings in place; unmodifiable
   * @throws IllegalArgumentException if {@code bindings} holds nothing for {@code variable}
   * @since 0.5.0
   */
  public static List<TypeMirror> boundsRefusing(
      Types types, TypeVariable variable, Map<? extends Element, ? extends TypeMirror> bindings) {
    TypeMirror argument = bindings.get(variable.asElement());
    if (argument == null) {
      throw new IllegalArgumentException(
          "no binding for type parameter '"
              + variable.asElement().getSimpleName()
              + "' of '"
              + variable.asElement().getEnclosingElement().getSimpleName()
              + "'");
    }
    return boundsOf(variable).stream()
        .map(bound -> substitute(types, bound, bindings, List.of()))
        .flatMap(Optional::stream)
        .filter(
            read -> !types.isAssignable(argument, read) || convertsUnchecked(types, argument, read))
        .toList();
  }

  /**
   * The one bound {@code variable} can be declared with to meet every type in {@code needed}, or
   * empty where one bound will not plainly do, for a fix line that offers the declaration.
   *
   * <p>It is offered only where declaring it is plainly right. The types needed come down to one
   * once any another implies is dropped ({@link #strongest}). That type is not raw, nor an array,
   * which bounds no type parameter, nor a type variable bounded by this one, which would close a
   * cycle. And it implies every bound the variable declares already, so declaring it alone keeps
   * them. Two bounds or more can clash in ways only javac settles, a generic interface inherited
   * twice or two methods of one signature, so they are left to the caller to list.
   *
   * @param types the round's type utilities; must not be null
   * @param variable the variable the bound would be declared on; must not be null
   * @param needed the types the variable has to be, at least one; must not be null
   * @return the one bound to declare, or empty where none plainly does
   * @since 0.5.0
   */
  public static Optional<TypeMirror> boundToDeclare(
      Types types, TypeVariable variable, Collection<? extends TypeMirror> needed) {
    List<TypeMirror> strongest = strongest(types, needed);
    if (strongest.size() != 1) {
      return Optional.empty();
    }
    TypeMirror bound = strongest.getFirst();
    boolean writable =
        switch (bound.getKind()) {
          case DECLARED -> firstRawIn(bound) == null;
          case TYPEVAR -> !boundedBy((TypeVariable) bound, variable);
          // An array bounds no type parameter.
          default -> false;
        };
    boolean keepsItsBounds =
        ((TypeParameterElement) variable.asElement())
            .getBounds().stream().allMatch(declared -> types.isSubtype(bound, declared));
    return writable && keepsItsBounds ? Optional.of(bound) : Optional.empty();
  }

  /**
   * The types, each once, less any that another of them implies: {@code Integer} for {@code Number}
   * and {@code Integer}, {@code Comparable<U>} for {@code Object & Comparable<U>}.
   *
   * @param types the round's type utilities; must not be null
   * @param candidates the types to reduce; must not be null
   * @return the types none of the others implies, in the order first given; unmodifiable
   * @since 0.5.0
   */
  public static List<TypeMirror> strongest(
      Types types, Collection<? extends TypeMirror> candidates) {
    List<TypeMirror> distinct = new ArrayList<>();
    for (TypeMirror type : candidates) {
      if (distinct.stream().noneMatch(kept -> types.isSameType(kept, type))) {
        distinct.add(type);
      }
    }
    return distinct.stream()
        .filter(
            type ->
                distinct.stream().noneMatch(other -> other != type && types.isSubtype(other, type)))
        .toList();
  }

  /**
   * Whether a type variable is bounded by another through a chain of type variables: declaring
   * {@code U extends V} closes a cycle where {@code V} is bounded by {@code U}.
   */
  private static boolean boundedBy(TypeVariable from, TypeVariable variable) {
    TypeMirror current = from;
    while (current instanceof TypeVariable link) {
      if (link.asElement().equals(variable.asElement())) {
        return true;
      }
      current = link.getUpperBound();
    }
    return false;
  }

  /**
   * {@code type} as javac reads it with each variable {@code pinned} names replaced by the type it
   * stands for and each of {@code free} by an unbounded wildcard, or empty where {@link
   * Types#getDeclaredType}, {@link Types#getArrayType} and {@link Types#getWildcardType} can write
   * no such type.
   *
   * <p>This is javac's own substitution, the one it checks a parameterised type's arguments against
   * their bounds with (JLS 4.5): a free variable reads as the wildcard written in its place, so
   * {@code List<X>} reads as {@code List<?>}. As a wildcard's bound javac reads a free variable its
   * own way, {@code ? super X} as {@code ?} and {@code ? extends X} as {@code ? extends} the bound
   * {@code X} declares, unsubstituted. A free variable anywhere else, an array's component or the
   * whole of {@code type}, leaves no type to write. Neither does an extends bound whose declared
   * bound is an intersection, nor a member of a generic class named through a raw one, {@code
   * Gen.In<X>}, which javac refuses as improperly formed.
   *
   * <p>A type that names none of the variables is handed back as it is, never rebuilt, so a shape
   * those builders would reject, such as a wildcard nested in a wildcard's bound, is left alone. An
   * intersection is read arm by arm, as {@link TypeParameterElement#getBounds()} hands a bound
   * over: passed whole, it would come back as its erasure.
   *
   * @param types the round's type utilities; must not be null
   * @param type the type to read, never an intersection; must not be null
   * @param pinned each variable to replace, by its element, with the reference type it stands for,
   *     never a wildcard; must not be null
   * @param free the variables to read as wildcards, none of them in {@code pinned}; must not be
   *     null
   * @return the reading, or empty where there is no type to write
   * @since 0.5.0
   */
  public static Optional<TypeMirror> substitute(
      Types types,
      TypeMirror type,
      Map<? extends Element, ? extends TypeMirror> pinned,
      Collection<? extends Element> free) {
    return Optional.ofNullable(substituted(types, type, pinned, free));
  }

  /** {@link #substitute(Types, TypeMirror, Map, Collection)}, with null for empty. */
  private static TypeMirror substituted(
      Types types,
      TypeMirror type,
      Map<? extends Element, ? extends TypeMirror> pinned,
      Collection<? extends Element> free) {
    if (Stream.concat(pinned.keySet().stream(), free.stream())
        .noneMatch(variable -> mentions(type, variable))) {
      return type;
    }
    return switch (type.getKind()) {
      // A free variable reads as a wildcard only as a type argument, which the declared arm writes.
      case TYPEVAR -> pinned.get(((TypeVariable) type).asElement());
      case ARRAY -> {
        TypeMirror component =
            substituted(types, ((ArrayType) type).getComponentType(), pinned, free);
        yield component == null ? null : types.getArrayType(component);
      }
      case WILDCARD -> substitutedWildcard(types, (WildcardType) type, pinned, free);
      default -> {
        // The one shape left that can name a variable, since a bound's intersection arrives
        // split into its arms.
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        if (!instantiated(declared.getEnclosingType())
            && instantiated(((DeclaredType) element.asType()).getEnclosingType())) {
          yield null;
        }
        TypeMirror enclosing = substituted(types, declared.getEnclosingType(), pinned, free);
        List<TypeMirror> arguments = new ArrayList<>();
        for (TypeMirror argument : declared.getTypeArguments()) {
          arguments.add(
              argument instanceof TypeVariable variable && free.contains(variable.asElement())
                  ? types.getWildcardType(null, null)
                  : substituted(types, argument, pinned, free));
        }
        if (enclosing == null || arguments.contains(null)) {
          yield null;
        }
        // A top-level or static class's enclosing type is a NoType, which names no containing type.
        yield types.getDeclaredType(
            enclosing instanceof DeclaredType containing ? containing : null,
            element,
            arguments.toArray(TypeMirror[]::new));
      }
    };
  }

  /** A wildcard that names a variable, as {@link #substituted} reads it. */
  private static TypeMirror substitutedWildcard(
      Types types,
      WildcardType wildcard,
      Map<? extends Element, ? extends TypeMirror> pinned,
      Collection<? extends Element> free) {
    // The wildcard names a variable, so it has a bound, and only one.
    TypeMirror extendsBound = wildcard.getExtendsBound();
    TypeMirror bound = extendsBound != null ? extendsBound : wildcard.getSuperBound();
    if (bound instanceof TypeVariable variable && free.contains(variable.asElement())) {
      if (extendsBound == null) {
        return types.getWildcardType(null, null);
      }
      TypeMirror declared = variable.getUpperBound();
      return declared instanceof IntersectionType ? null : types.getWildcardType(declared, null);
    }
    TypeMirror read = substituted(types, bound, pinned, free);
    if (read == null) {
      return null;
    }
    return extendsBound != null
        ? types.getWildcardType(read, null)
        : types.getWildcardType(null, read);
  }

  /** Whether an enclosing type, a NoType included, carries an instantiation. */
  private static boolean instantiated(TypeMirror enclosing) {
    return enclosing instanceof DeclaredType declared && carriesInstantiation(declared);
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
   *
   * <p>That is this helper's policy, not the only one in the processor, and the difference is
   * deliberate rather than drift. A caller that must not erase guards the call itself, because what
   * a raw site should produce is the caller's question:
   *
   * <ul>
   *   <li>{@link #memberTypeOf} guards with {@link #carriesInstantiation} and reads the declaration
   *       under a raw site. Erasing there rejected a container the spec had written, which is the
   *       {@code @ThroughField} regression #738 caught. A raw source type is now refused at the
   *       spec's declaration (#771), so the site the guard reads as declared today is a non-generic
   *       one.
   *   <li>{@code MappingProcessor.componentType} asks whether the record <em>declares</em>
   *       parameters rather than whether the site supplies them, so that a concrete pair never
   *       relies on {@code asMemberOf} accepting a record component. A raw domain cannot reach it:
   *       {@code @GenerateMapping} refuses one at the declaration.
   * </ul>
   *
   * <p>Settled under #740: the three readers are not near-copies to be merged. Consolidating them
   * would have to pick one raw-site answer, and they want different ones.
   *
   * @param types the round's type utilities; must not be null
   * @param owner the instantiated type the member is read on; must not be null
   * @param member the member to read; must not be null
   * @return the member's signature under {@code owner}'s instantiation
   * @since 0.4.10
   */
  public static ExecutableType memberOf(Types types, DeclaredType owner, ExecutableElement member) {
    return (ExecutableType) types.asMemberOf(owner, member);
  }

  /**
   * The bounds a type variable is written with, as whoever reads it sees them.
   *
   * <p>Kind, not {@code instanceof}: an intersection type implements {@link DeclaredType} too, so a
   * pattern would take the first arm of {@code A & B} for the whole bound. Read off a variable
   * rather than a {@link javax.lang.model.element.TypeParameterElement} so that a member read under
   * an instantiation reports the bounds it has <em>there</em> - an inherited {@code <R extends T>}
   * is {@code <R extends String>} under {@code Base<String>}, and {@code T} is a name the reader
   * cannot write.
   *
   * @param variable the type variable to read; must not be null
   * @return its upper bound, or the arms of that bound when it is an intersection
   * @since 0.4.10
   */
  public static List<? extends TypeMirror> boundsOf(TypeVariable variable) {
    TypeMirror upperBound = variable.getUpperBound();
    return upperBound.getKind() == TypeKind.INTERSECTION
        ? ((IntersectionType) upperBound).getBounds()
        : List.of(upperBound);
  }

  /**
   * The types a call made on a value of {@code type} is read on: the type itself, or for a type
   * variable the bound it carries, followed through a variable bounded by another; and each type of
   * a bound naming several, reached the same way. javac reads a member of a type variable among the
   * members of every type its bound names, and capture can bound a variable by another together
   * with an interface, a bound no declaration can write.
   *
   * @param type the type a call is made on; must not be null
   * @return the types reached, one where no bound names several (non-null)
   * @since 0.5.0
   */
  public static List<TypeMirror> typesReached(TypeMirror type) {
    TypeMirror step = type;
    // A bound never leads back to the variable it bounds, so the walk ends.
    while (step.getKind() == TypeKind.TYPEVAR) {
      step = ((TypeVariable) step).getUpperBound();
    }
    return step.getKind() == TypeKind.INTERSECTION
        ? ((IntersectionType) step)
            .getBounds().stream().flatMap(bound -> typesReached(bound).stream()).toList()
        : List.of(step);
  }

  /**
   * Whether a type is a subtype of the class or interface a canonical name names, where that is on
   * the classpath: false where it is not, as for a library the build does not use.
   *
   * @param types the round's type utilities; must not be null
   * @param elements the round's element utilities; must not be null
   * @param type the type to test; must not be null
   * @param canonicalName the supertype's canonical name, such as {@code
   *     com.google.protobuf.MessageLite.Builder}; must not be null
   * @return true when {@code type} is a subtype of it
   * @since 0.5.0
   */
  public static boolean isSubtypeOfNamed(
      Types types, Elements elements, TypeMirror type, String canonicalName) {
    return Optional.ofNullable(elements.getTypeElement(canonicalName))
        .filter(
            supertype -> types.isSubtype(types.erasure(type), types.erasure(supertype.asType())))
        .isPresent();
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
   * Whether a type is a type variable or names one at any depth.
   *
   * <p>{@link #mentions} asks after one parameter; this asks after any, looking through the same
   * layers: {@code List<T>}, {@code T[]}, {@code List<? extends T>}, {@code Outer<T>.Inner} and
   * {@code Runnable & List<T>} all name one.
   *
   * @param type the type to search; must not be null
   * @return true when {@code type} is or names a type variable
   * @since 0.5.0
   */
  public static boolean namesTypeVariable(TypeMirror type) {
    return switch (type) {
      case TypeVariable variable -> true;
      // Before DeclaredType, for the reason mentions gives.
      case IntersectionType intersection ->
          intersection.getBounds().stream().anyMatch(ProcessorUtils::namesTypeVariable);
      case DeclaredType declared ->
          namesTypeVariable(declared.getEnclosingType())
              || declared.getTypeArguments().stream().anyMatch(ProcessorUtils::namesTypeVariable);
      case ArrayType array -> namesTypeVariable(array.getComponentType());
      case WildcardType wildcard ->
          (wildcard.getExtendsBound() != null && namesTypeVariable(wildcard.getExtendsBound()))
              || (wildcard.getSuperBound() != null && namesTypeVariable(wildcard.getSuperBound()));
      default -> false;
    };
  }

  /**
   * The name of a type as written, with its type-use annotations kept.
   *
   * <p>{@link TypeName#get(TypeMirror)} rebuilds a name from the element alone and never consults
   * {@link TypeMirror#getAnnotationMirrors()} at any depth, so every type-use annotation on the
   * declaration is dropped on the way into generated source. That is not merely a loss of
   * information: inside a {@code @NullMarked} scope an unannotated type <em>means</em> non-null, so
   * a dropped {@code @Nullable} asserts the opposite of what the author wrote. The scope is the
   * consumer's to set - a {@code package-info} or {@code module-info} carrying {@code @NullMarked}
   * covers generated files in that package as surely as an annotation the generator stamps itself -
   * so a generator cannot know that its output is unconstrained.
   *
   * <p>The walk mirrors javapoet's own, re-attaching each mirror's annotations as it goes, so an
   * annotation is kept wherever it was written: on the type itself, on a type argument at any
   * depth, on an array's component or on the array, and on a wildcard bound. A type read with
   * {@link Types#asMemberOf} has already lost the annotations written on a type variable's use;
   * name one of those with {@link #typeNameOf(TypeMirror, TypeMirror, DeclaredType, String)}.
   *
   * <p>An annotation is kept only where some generated file could write it cleanly. One javac could
   * not resolve, which is one missing from the compile classpath, one private anywhere in its
   * nesting, and one deprecated anywhere in its nesting are left off: the first two fail the build
   * compiling the generated file, and the third draws a warning there that no one can suppress. A
   * package-private one is kept: it can be written from its own package, which is where a generated
   * file lands unless a target package says otherwise. A generator that knows its package names
   * with {@link #typeNameOf(TypeMirror, String)}, which decides exactly.
   *
   * @param type the type to name; must not be null
   * @return its name, annotated as the source annotated it wherever some file could say so
   *     (non-null)
   * @since 0.4.10
   */
  public static TypeName typeNameOf(TypeMirror type) {
    return new Naming(Map.of(), annotation -> writableSomewhere(annotation.getAnnotationType()))
        .name(type, type);
  }

  /**
   * {@link #typeNameOf(TypeMirror)} for a file written into {@code targetPackage}: an annotation is
   * kept only where that package can write it cleanly, resolved, with every type in its nesting
   * public or declared in that package, and none of them private or deprecated.
   *
   * @param type the type to name; must not be null
   * @param targetPackage the package the generated file is written into; must not be null
   * @return its name, annotated as the source annotated it wherever the file can say so (non-null)
   * @since 0.4.11
   */
  public static TypeName typeNameOf(TypeMirror type, String targetPackage) {
    return new Naming(
            Map.of(), annotation -> writableFrom(annotation.getAnnotationType(), targetPackage))
        .name(type, type);
  }

  /**
   * The name of a member's type read under an owner, with the type-use annotations its declaration
   * wrote kept, for a file written into {@code targetPackage}.
   *
   * <p>{@link Types#asMemberOf} replaces each type variable a member's type names with what the
   * owner binds it to, and javac's substitution drops any annotation written on that use of the
   * variable: {@code ValidatedPrism<String, @Nullable T>} comes back as {@code
   * ValidatedPrism<String, T>}, even under the declaring type itself, where {@code T} is bound to
   * {@code T}. An annotation on a variable's use applies to whatever replaces the variable, so this
   * walks the substituted type beside the declared one and writes each such annotation onto the
   * replacement, unless the replacement already carries one of the same annotation type.
   *
   * <p>The variable may be bound on the way, by a supertype clause between the member and the
   * owner: under {@code Mid<M> extends NameLeaf<@Nullable M>}, the member's {@code N} is Mid's
   * {@code @Nullable M} before it is the owner's anything. The walk follows the owner's
   * superinterface clauses as written, from the variable to what each clause binds it to, and keeps
   * what each of them wrote. Where the declaration and the substituted type part company, below a
   * replaced variable, it goes on with the substituted type alone. Annotations are kept as {@link
   * #typeNameOf(TypeMirror, String)} keeps them.
   *
   * @param type the member's type as the owner has it, {@code asMemberOf}'s answer; must not be
   *     null
   * @param declared the same type as the member declares it, such as its own {@code
   *     getReturnType()}; must not be null
   * @param owner the instantiation the member was read under; must not be null
   * @param targetPackage the package the generated file is written into; must not be null
   * @return its name, annotated as the source annotated it wherever the file can say so (non-null)
   * @since 0.4.11
   */
  public static TypeName typeNameOf(
      TypeMirror type, TypeMirror declared, DeclaredType owner, String targetPackage) {
    return new Naming(
            clausesOf(owner),
            annotation -> writableFrom(annotation.getAnnotationType(), targetPackage))
        .name(type, declared);
  }

  /**
   * Whether some generated file can write this annotation type cleanly: resolved, and neither
   * private nor deprecated anywhere in its nesting.
   */
  private static boolean writableSomewhere(DeclaredType annotationType) {
    return writable(annotationType, element -> !element.getModifiers().contains(Modifier.PRIVATE));
  }

  /**
   * Whether a generated file in {@code targetPackage} can write this annotation type cleanly:
   * resolved, and every type in its nesting public or declared in that package, and neither private
   * nor deprecated.
   */
  private static boolean writableFrom(DeclaredType annotationType, String targetPackage) {
    return writable(
        annotationType, element -> visibleFrom(element, packageOf(element), targetPackage));
  }

  /**
   * Whether an annotation type can be written, given what counts as visible to the file writing it.
   *
   * <p>A deprecation written only as a javadoc {@code @deprecated} tag is not seen: reading that
   * needs the round's {@link Elements}, which naming a type does not take. Such an annotation is
   * written, and the generated file carries javac's deprecation warning for it, as it did before
   * any of them were left off.
   */
  private static boolean writable(DeclaredType annotationType, Predicate<Element> visible) {
    if (annotationType.getKind() == TypeKind.ERROR) {
      return false;
    }
    for (Element current = annotationType.asElement();
        current.getKind() != ElementKind.PACKAGE;
        current = current.getEnclosingElement()) {
      if (current.getAnnotation(Deprecated.class) != null || !visible.test(current)) {
        return false;
      }
    }
    return true;
  }

  private static String packageOf(Element element) {
    Element current = element;
    while (current.getKind() != ElementKind.PACKAGE) {
      current = current.getEnclosingElement();
    }
    return ((PackageElement) current).getQualifiedName().toString();
  }

  /**
   * What the owner, and each superinterface clause below it as written, binds each type parameter
   * to: the owner's own arguments, then every clause reached from its type, the first binding of a
   * parameter kept. A variable bound to itself, as the declaring type's own instantiation binds
   * each, leads nowhere and is left out; so is a raw clause, which binds nothing.
   */
  private static Map<Element, TypeMirror> clausesOf(DeclaredType owner) {
    Map<Element, TypeMirror> clauses = new HashMap<>();
    bindClause(owner, clauses);
    bindSupertypeClauses((TypeElement) owner.asElement(), new HashSet<>(), clauses);
    return Map.copyOf(clauses);
  }

  private static void bindSupertypeClauses(
      TypeElement type, Set<TypeElement> visited, Map<Element, TypeMirror> clauses) {
    if (!visited.add(type)) {
      return;
    }
    for (TypeMirror parent : type.getInterfaces()) {
      DeclaredType clause = (DeclaredType) parent;
      bindClause(clause, clauses);
      bindSupertypeClauses((TypeElement) clause.asElement(), visited, clauses);
    }
  }

  private static void bindClause(DeclaredType clause, Map<Element, TypeMirror> clauses) {
    List<? extends TypeParameterElement> parameters =
        ((TypeElement) clause.asElement()).getTypeParameters();
    List<? extends TypeMirror> arguments = clause.getTypeArguments();
    if (arguments.size() != parameters.size()) {
      return;
    }
    for (int index = 0; index < parameters.size(); index++) {
      TypeMirror argument = arguments.get(index);
      if (!(argument instanceof TypeVariable variable
          && variable.asElement().equals(parameters.get(index)))) {
        clauses.putIfAbsent(parameters.get(index), argument);
      }
    }
  }

  /**
   * One naming walk: the supertype clauses a variable may be bound through, and which annotations
   * the file being written can carry.
   */
  private record Naming(
      Map<Element, TypeMirror> clauses, Predicate<? super AnnotationMirror> writable) {

    /**
     * The declaration's counterpart of {@code type}, where it is of the same kind, or {@code type}
     * itself where the two part company: below a variable nothing binds, the declaration has no
     * parts of its own, and the walk goes on with the substituted type alone.
     */
    private static TypeMirror sameKindOr(TypeMirror declared, TypeMirror type) {
      return declared.getKind() == type.getKind() ? declared : type;
    }

    TypeName name(TypeMirror type, TypeMirror declared) {
      Map<Element, AnnotationMirror> annotations = new LinkedHashMap<>();
      type.getAnnotationMirrors()
          .forEach(
              annotation ->
                  annotations.putIfAbsent(annotation.getAnnotationType().asElement(), annotation));
      // A variable's use keeps what was written on it, and so does each clause binding it on the
      // way to the owner; the last of them is what the substituted type stands in for. A type that
      // implements its own spec binds each way round - the domain pattern binds the record's
      // variable to the spec's, the record's own clause binds it back - so each variable is
      // followed once and a cycle stops where it closes. What the closing clause wrote is not
      // read: a binding that leads back where it started answers for nothing the walk has not
      // already passed through.
      TypeMirror twin = declared;
      Set<Element> followed = new HashSet<>();
      while (twin.getKind() == TypeKind.TYPEVAR
          && followed.add(((TypeVariable) twin).asElement())) {
        twin.getAnnotationMirrors()
            .forEach(
                annotation ->
                    annotations.putIfAbsent(
                        annotation.getAnnotationType().asElement(), annotation));
        TypeMirror bound = clauses.get(((TypeVariable) twin).asElement());
        if (bound == null) {
          break;
        }
        twin = bound;
      }
      List<AnnotationSpec> specs =
          annotations.values().stream().filter(writable).map(AnnotationSpec::get).toList();
      // Dispatch on the kind, as javapoet's own visitor does, rather than on the interface: javac's
      // intersection implements DeclaredType, so a pattern switch would send one down the declared
      // arm and ask it for a class element it does not have. Everything this does not rebuild -
      // a primitive, a type variable, void, and the kinds that have no name at all - javapoet
      // names from the mirror alone, and it stays javapoet's call which of those it refuses.
      TypeName name =
          switch (type.getKind()) {
            case ARRAY -> arrayNameOf((ArrayType) type, twin);
            case WILDCARD -> wildcardNameOf((WildcardType) type, twin);
            case DECLARED, ERROR -> declaredNameOf((DeclaredType) type, twin);
            default -> TypeName.get(type);
          };
      return specs.isEmpty() ? name : name.annotated(specs);
    }

    private TypeName arrayNameOf(ArrayType array, TypeMirror declared) {
      ArrayType twin = (ArrayType) sameKindOr(declared, array);
      return ArrayTypeName.of(name(array.getComponentType(), twin.getComponentType()));
    }

    private TypeName declaredNameOf(DeclaredType declared, TypeMirror written) {
      // The declaration corresponds part for part only where it names the same shape: a raw read
      // of a generic type drops the arguments.
      DeclaredType sameKind = (DeclaredType) sameKindOr(written, declared);
      DeclaredType twin =
          sameKind.getTypeArguments().size() == declared.getTypeArguments().size()
              ? sameKind
              : declared;
      ClassName rawType = ClassName.get((TypeElement) declared.asElement());
      TypeMirror enclosingType = declared.getEnclosingType();
      // A static member has no enclosing instance type, so javac reports NONE for it and the kind
      // test alone settles both cases.
      TypeName enclosing =
          enclosingType.getKind() == TypeKind.NONE
              ? null
              : name(enclosingType, twin.getEnclosingType());
      List<? extends TypeMirror> arguments = declared.getTypeArguments();
      List<? extends TypeMirror> twinArguments = twin.getTypeArguments();
      List<TypeName> argumentNames =
          IntStream.range(0, arguments.size())
              .mapToObj(index -> name(arguments.get(index), twinArguments.get(index)))
              .toList();
      if (enclosing instanceof ParameterizedTypeName parameterised) {
        return parameterised.nestedClass(rawType.simpleName(), argumentNames);
      }
      // An annotation on the enclosing type is written before it - `@Marker Outer.Inner` annotates
      // Outer, not Inner - and ClassName.get(element) names the whole nesting from the element
      // alone, so it carries none of it. Rebuilding the name under the enclosing keeps what was
      // written there; for an unannotated enclosing it reproduces the same name.
      if (enclosing instanceof ClassName enclosingName) {
        rawType = enclosingName.nestedClass(rawType.simpleName());
      }
      return argumentNames.isEmpty()
          ? rawType
          : ParameterizedTypeName.get(rawType, argumentNames.toArray(new TypeName[0]));
    }

    private TypeName wildcardNameOf(WildcardType wildcard, TypeMirror declared) {
      // A substituted wildcard keeps the declared one's kind, so an extends bound pairs with an
      // extends bound and a super bound with a super bound.
      WildcardType twin = (WildcardType) sameKindOr(declared, wildcard);
      TypeMirror extendsBound = wildcard.getExtendsBound();
      if (extendsBound != null) {
        return WildcardTypeName.subtypeOf(name(extendsBound, twin.getExtendsBound()));
      }
      TypeMirror superBound = wildcard.getSuperBound();
      return superBound == null
          ? WildcardTypeName.subtypeOf(ClassName.OBJECT)
          : WildcardTypeName.supertypeOf(name(superBound, twin.getSuperBound()));
    }
  }

  /**
   * The declaration of a type parameter, with the annotations on its bounds kept.
   *
   * <p>{@link TypeVariableName#get(TypeParameterElement)} names each bound through {@link
   * TypeName#get(TypeMirror)}, which drops the annotation, and then removes any bound that is bare
   * {@code Object}. Between them {@code <T extends @Nullable Object>} becomes {@code <T>}, which is
   * the narrower declaration: the generated type no longer admits an instantiation the type it
   * wraps permits.
   *
   * <p>Naming the bounds through {@link #typeNameOf(TypeMirror)} is enough to fix both halves. An
   * annotated {@code Object} is not equal to the bare one, so it survives the removal that the bare
   * bound is still rightly subject to.
   *
   * <p>Annotations are kept as {@link #typeNameOf(TypeMirror)} keeps them, so a generator that
   * knows the package it is writing into declares its parameters with {@link
   * #typeVariableOf(TypeParameterElement, String)} instead.
   *
   * <p>The bounds are all that is copied. An annotation written on the parameter itself, as in
   * {@code <@Marker T>}, is left behind: one {@code TypeVariableName} both declares a parameter and
   * is written wherever that parameter is named, and a generator reuses the same one for both. An
   * annotation that is legal on the declaration need not be legal at a use - a {@code
   * TYPE_PARAMETER} one is rejected outright as a type argument - so carrying it would emit source
   * the consuming build cannot compile. Nothing is lost for nullness: JSpecify states a nullable
   * parameter as {@code <T extends @Nullable Object>}, which is a bound.
   *
   * @param parameter the type parameter to name; must not be null
   * @return its name, with its bounds annotated wherever some file could say so (non-null)
   * @since 0.4.10
   */
  public static TypeVariableName typeVariableOf(TypeParameterElement parameter) {
    TypeName[] bounds =
        parameter.getBounds().stream().map(ProcessorUtils::typeNameOf).toArray(TypeName[]::new);
    return TypeVariableName.get(parameter.getSimpleName().toString(), bounds);
  }

  /**
   * {@link #typeVariableOf(TypeParameterElement)} for a file written into {@code targetPackage},
   * its bounds named as {@link #typeNameOf(TypeMirror, String)} names a type.
   *
   * @param parameter the type parameter to name; must not be null
   * @param targetPackage the package the generated file is written into; must not be null
   * @return its name, with its bounds annotated wherever the file can say so (non-null)
   * @since 0.4.11
   */
  public static TypeVariableName typeVariableOf(
      TypeParameterElement parameter, String targetPackage) {
    TypeName[] bounds =
        parameter.getBounds().stream()
            .map(bound -> typeNameOf(bound, targetPackage))
            .toArray(TypeName[]::new);
    return TypeVariableName.get(parameter.getSimpleName().toString(), bounds);
  }

  /**
   * Renders a type for a diagnostic, with package qualifiers dropped and type arguments spaced.
   *
   * <p>Type arguments and enclosing types are kept, so {@code java.util.List<java.lang.String>}
   * reads as {@code List<String>} and {@code com.external.Outer.Inner} as {@code Outer.Inner}. A
   * diagnostic that offers a corrected declaration needs both: a rendering that drops either one
   * suggests source that does not compile.
   *
   * <p>A declared type, array, wildcard, type variable or primitive is built from its element,
   * resolved arguments or kind rather than {@code toString()}, so a type-use annotation never
   * enters: {@code @Nullable Set<?>} reads as {@code Set<?>}, the same way the corrected
   * declaration beside it is rendered. The annotation is not what a diagnostic about the type is
   * about, and a message that prints it in one half and drops it in the other reads as advice to
   * remove it. The remaining kinds, an unresolvable type or an intersection, render from {@code
   * toString()} with annotations stripped. {@link #qualifiedTypeName} is the same rendering with
   * every package kept. Generated source is the opposite decision: {@link #typeNameOf} keeps
   * type-use annotations, because there the annotation is part of the source being emitted.
   *
   * @param type the type to render; must not be null
   * @return the rendered name (non-null)
   * @since 0.4.10
   */
  public static String simpleTypeName(TypeMirror type) {
    return diagnosticName(type, Spelling.SIMPLE);
  }

  /**
   * Renders a type for a diagnostic that names it in full, with type arguments spaced and type-use
   * annotations left out.
   *
   * <p>This is the spelling for any message that names a type in full, and the one a line the
   * reader pastes needs, such as the leaf a fix line offers: every package is kept, so {@code
   * java.util.Optional<java.time.LocalDate>} compiles without an import. In every other respect it
   * is {@link #simpleTypeName}'s rendering, so a type-use annotation never enters. {@code
   * toString()} would spell a {@code @Nullable String} as {@code
   * java.lang.@org.jspecify.annotations.Nullable String}: legal, seldom seen, and a nullness claim
   * the pasted line has no use for, since the processors match a leaf's types with annotations
   * ignored.
   *
   * @param type the type to render; must not be null
   * @return the rendered name, fully qualified (non-null)
   * @since 0.4.11
   */
  public static String qualifiedTypeName(TypeMirror type) {
    return diagnosticName(type, Spelling.QUALIFIED);
  }

  /** How a rendered type spells a class: within its enclosing classes, or with its package too. */
  private enum Spelling {
    SIMPLE,
    QUALIFIED
  }

  /** The type's name in a diagnostic, spelt as {@code spelling} says. */
  private static String diagnosticName(TypeMirror type, Spelling spelling) {
    return switch (type.getKind()) {
      case DECLARED -> declaredName((DeclaredType) type, spelling);
      case ARRAY -> diagnosticName(((ArrayType) type).getComponentType(), spelling) + "[]";
      case WILDCARD -> wildcardName((WildcardType) type, spelling);
      case TYPEVAR -> ((TypeVariable) type).asElement().getSimpleName().toString();
      // The kind names the type on its own; toString would carry any type-use annotation.
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE ->
          type.getKind().name().toLowerCase(Locale.ROOT);
      // The string form, for what remains: an unresolvable type, whose element walk loses the
      // qualifier on a nested name that the string keeps, and an intersection, which a structural
      // arm would have to render bound by bound. Annotations are stripped, argument lists and all.
      default -> {
        String written = stripAnnotations(type.toString());
        yield (spelling == Spelling.SIMPLE
                ? written.replaceAll("\\b(?:[a-z][\\p{Alnum}_]*\\.)+", "")
                : written)
            .replace(",", ", ");
      }
    };
  }

  /**
   * Strips annotation tokens from a type's string form, for the shapes that render through {@code
   * toString()}.
   *
   * <p>A scan rather than a pattern, because an annotation argument may contain the very characters
   * a pattern would stop at: a bracket or a quote inside a string ({@code @Marker(")")}) or a
   * nested annotation ({@code @Outer(@Inner(1))}). The scan tracks parenthesis depth and string and
   * character literals, escapes included. javac prints an annotated type as {@code enclosing.@Anno
   * Simple}, so the dot before the annotation stays where it joins a package or an enclosing type
   * to the name ({@code com.example.Money}, {@code Missing.Inner}), and goes with the annotation
   * where javac prints it for an empty enclosing ({@code .@Anno Missing}).
   *
   * @param rendered the type's string form; must not be null
   * @return the string with annotation tokens and their trailing spaces removed (non-null)
   */
  static String stripAnnotations(String rendered) {
    StringBuilder out = new StringBuilder();
    int i = 0;
    while (i < rendered.length()) {
      char c = rendered.charAt(i);
      if (c == '@') {
        if (standsForAnEmptyEnclosing(out)) {
          out.setLength(out.length() - 1);
        }
        i = skipAnnotation(rendered, i);
      } else {
        out.append(c);
        i++;
      }
    }
    return out.toString();
  }

  /**
   * Whether the text so far ends in a dot javac printed for an empty enclosing: one with no name
   * before it, where a name ends in an identifier character or an argument list's {@code '>'}.
   */
  private static boolean standsForAnEmptyEnclosing(StringBuilder out) {
    int length = out.length();
    if (length == 0 || out.charAt(length - 1) != '.') {
      return false;
    }
    if (length == 1) {
      return true;
    }
    char before = out.charAt(length - 2);
    return !Character.isJavaIdentifierPart(before) && before != '>';
  }

  /** Consumes one annotation token starting at the {@code '@'}, returning the next index. */
  private static int skipAnnotation(String rendered, int at) {
    int i = at + 1;
    while (i < rendered.length()
        && (Character.isLetterOrDigit(rendered.charAt(i))
            || rendered.charAt(i) == '_'
            || rendered.charAt(i) == '.')) {
      i++;
    }
    if (i < rendered.length() && rendered.charAt(i) == '(') {
      int depth = 0;
      char quote = 0;
      do {
        char c = rendered.charAt(i);
        if (quote != 0) {
          if (c == '\\') {
            i++;
          } else if (c == quote) {
            quote = 0;
          }
        } else if (c == '"' || c == '\'') {
          quote = c;
        } else if (c == '(') {
          depth++;
        } else if (c == ')') {
          depth--;
        }
        i++;
      } while (i < rendered.length() && depth > 0);
    }
    while (i < rendered.length() && rendered.charAt(i) == ' ') {
      i++;
    }
    return i;
  }

  /** The declared type's name: enclosing chain, simple name, and resolved arguments. */
  private static String declaredName(DeclaredType declared, Spelling spelling) {
    String arguments =
        declared.getTypeArguments().isEmpty()
            ? ""
            : declared.getTypeArguments().stream()
                .map(argument -> diagnosticName(argument, spelling))
                .collect(Collectors.joining(", ", "<", ">"));
    return declaredHead(declared, spelling) + arguments;
  }

  /**
   * The declared type's name without its arguments: the enclosing chain and the simple name.
   *
   * <p>For a diagnostic that rebuilds the argument list itself, the way {@code @GenerateFocus}
   * suggests a concrete alternative to a wildcard: the head has to keep the nesting the full
   * rendering keeps, or the suggestion names a type that does not compile.
   *
   * @param declared the type whose head to render; must not be null
   * @return the enclosing chain and simple name, without arguments (non-null)
   * @since 0.4.11
   */
  public static String declaredHead(DeclaredType declared) {
    return declaredHead(declared, Spelling.SIMPLE);
  }

  /** The declared type's head, spelt as {@code spelling} says. */
  private static String declaredHead(DeclaredType declared, Spelling spelling) {
    if (declared.getEnclosingType().getKind() == TypeKind.DECLARED) {
      // A member reached through an instance carries the enclosing type, arguments and all:
      // Outer<String>.Holder, or Outer.Holder where the outer level is written raw.
      return diagnosticName(declared.getEnclosingType(), spelling)
          + "."
          + declared.asElement().getSimpleName();
    }
    // A static or top-level nesting has a NoType enclosing type, but the enclosing classes still
    // print: Registry.Tag, as the declaration site would write it, or in full with its package.
    TypeElement element = (TypeElement) declared.asElement();
    if (spelling == Spelling.QUALIFIED) {
      return element.getQualifiedName().toString();
    }
    String prefix = "";
    for (Element outer = element.getEnclosingElement();
        outer instanceof TypeElement typeElement;
        outer = typeElement.getEnclosingElement()) {
      prefix = typeElement.getSimpleName() + "." + prefix;
    }
    return prefix + element.getSimpleName();
  }

  /** The wildcard as written: bare, extends-bounded or super-bounded. */
  private static String wildcardName(WildcardType wildcard, Spelling spelling) {
    if (wildcard.getExtendsBound() != null) {
      return "? extends " + diagnosticName(wildcard.getExtendsBound(), spelling);
    }
    if (wildcard.getSuperBound() != null) {
      return "? super " + diagnosticName(wildcard.getSuperBound(), spelling);
    }
    return "?";
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
    return freeTypeVariableName("F", recordElement);
  }

  /**
   * A name for a local or lambda parameter in generated code that no name already in scope takes:
   * the candidate, underscore-suffixed until free of {@code taken}, which then claims it. A lambda
   * parameter may not shadow a local or a parameter of the method holding it (JLS 6.4), and a merge
   * method's parameters carry the spec author's own names, so generated code cannot assume any name
   * is free.
   *
   * @param candidate the name to use when it is free
   * @param taken the names already in scope; the returned name is added to it
   * @return {@code candidate}, or {@code candidate} followed by as many underscores as it takes
   * @since 0.4.11
   */
  public static String freeName(String candidate, Set<String> taken) {
    StringBuilder name = new StringBuilder(candidate);
    while (taken.contains(name.toString())) {
      name.append('_');
    }
    taken.add(name.toString());
    return name.toString();
  }

  /**
   * A type-variable name a method generated inside the record's own type variables can declare
   * without shadowing one of them.
   *
   * @param preferred the name to use when the record has not taken it
   * @param recordElement the annotated record
   * @return {@code preferred}, or {@code preferred} followed by the first number the record leaves
   *     free
   * @since 0.4.11
   */
  public static String freeTypeVariableName(String preferred, TypeElement recordElement) {
    Set<String> taken =
        recordElement.getTypeParameters().stream()
            .map(parameter -> parameter.getSimpleName().toString())
            .collect(Collectors.toSet());
    String name = preferred;
    for (int suffix = 1; taken.contains(name); suffix++) {
      name = preferred + suffix;
    }
    return name;
  }

  /**
   * A generic type written with concrete type arguments, for a diagnostic's fix line, or empty when
   * one of its type parameters admits no type the fix line can name.
   *
   * <p>Each argument becomes a type its parameter admits. A wildcard becomes the type its bound
   * names, so {@code ? super Integer} is {@code Integer}. An unbounded wildcard, or a raw type's
   * missing argument, becomes the bound the parameter declares, which is {@code Object} for an
   * unbounded one. That bound is named only when it is a single type, neither raw nor written in
   * terms of the type's own parameters: {@code T extends Comparable<T>} admits no type its
   * declaration alone names, and {@code Object} would fall outside it.
   *
   * @param declaredType a generic type that is raw or carries a wildcard type argument; must not be
   *     null
   * @return its head with concrete type arguments, such as {@code Either<String, Object>}, or empty
   * @since 0.5.0
   */
  public static Optional<String> concreteAlternative(DeclaredType declaredType) {
    TypeElement element = (TypeElement) declaredType.asElement();
    List<? extends TypeParameterElement> parameters = element.getTypeParameters();
    List<? extends TypeMirror> written = declaredType.getTypeArguments();
    List<Optional<TypeMirror>> arguments =
        IntStream.range(0, parameters.size())
            .mapToObj(
                i ->
                    concreteArgument(
                        written.isEmpty() ? null : written.get(i), parameters.get(i), element))
            .toList();
    return arguments.stream().allMatch(Optional::isPresent)
        ? Optional.of(
            declaredHead(declaredType)
                + "<"
                + arguments.stream()
                    .map(argument -> simpleTypeName(argument.orElseThrow()))
                    .collect(Collectors.joining(", "))
                + ">")
        : Optional.empty();
  }

  /**
   * The type a fix line writes for one type argument: the argument itself, the type a wildcard's
   * bound names, or the parameter's own bound where the argument names none.
   *
   * @param written the argument as written, or null for a raw type, which writes none
   * @param parameter the type parameter the argument is for
   * @param element the generic type declaring the parameter
   * @return the type to write, or empty when the parameter admits none a fix line can name
   */
  private static Optional<TypeMirror> concreteArgument(
      TypeMirror written, TypeParameterElement parameter, TypeElement element) {
    TypeMirror named =
        written instanceof WildcardType wildcard
            ? (wildcard.getExtendsBound() != null
                ? wildcard.getExtendsBound()
                : wildcard.getSuperBound())
            : written;
    if (named != null) {
      return Optional.of(named);
    }
    List<? extends TypeMirror> bounds = parameter.getBounds();
    TypeMirror bound = bounds.getFirst();
    return bounds.size() == 1
            && firstRawIn(bound) == null
            && element.getTypeParameters().stream().noneMatch(own -> mentions(bound, own))
        ? Optional.of(bound)
        : Optional.empty();
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
   * <p>A navigator into a generic record asks the same question of the record as a component gives
   * it, because the navigator takes the record's type arguments as its own.
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
      return isRaw(declaredType);
    }
    return typeArguments.stream().anyMatch(arg -> arg.getKind() == TypeKind.WILDCARD);
  }

  /**
   * Whether a call constructing {@code type} from {@code arity} arguments has another constructor
   * to bind to: {@code type} declares more than one constructor taking {@code arity} parameters.
   * Only those compete. The phases of overload resolution before variable arity (JLS 15.12.2.2 and
   * 15.12.2.3) read a constructor at its declared parameter count, and the constructor meant is
   * applicable by the second of them.
   *
   * @param type the type constructed; must not be null
   * @param arity the number of arguments the call passes
   * @return true when more than one constructor of {@code type} takes {@code arity} parameters
   * @since 0.4.11
   */
  public static boolean overloadsConstructor(TypeElement type, int arity) {
    return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
            .filter(constructor -> constructor.getParameters().size() == arity)
            .count()
        > 1;
  }

  /**
   * The argument a generated call passes to {@code record}'s canonical constructor for a component
   * of type {@code component}, from {@code boxed}, an expression of the component's boxed type: the
   * lambda parameter a {@code Validated} ladder hands on, or a lens's {@code newValue}.
   *
   * <p>It is {@code boxed} itself, or {@code (long) boxed} where the component is primitive and the
   * record {@link #overloadsConstructor overloads} its canonical constructor. The first phase of
   * overload resolution allows no unboxing (JLS 15.12.2.2), so a {@code Long} finds {@code
   * Money(Number, String)} applicable, and binds to it, before the canonical {@code Money(long,
   * String)} is considered. With every argument exactly its component's type, the canonical
   * constructor is the most specific one applicable. The cast is written only where another
   * constructor competes, and only on a boxed value: on one already primitive, javac reports it as
   * redundant.
   *
   * <p>The cast names the primitive itself, which no package can hide and no annotation belongs on,
   * so it is written from the type rather than through {@link #typeNameOf}.
   *
   * @param record the record constructed, whose components answer whether a cast is needed; must
   *     not be null
   * @param component the component's type as the record declares it; must not be null
   * @param boxed an expression of the component's boxed type; must not be null
   * @return the argument for the component
   * @since 0.4.11
   */
  public static CodeBlock canonicalArgument(
      TypeElement record, TypeMirror component, CodeBlock boxed) {
    return component.getKind().isPrimitive()
            && overloadsConstructor(record, record.getRecordComponents().size())
        ? CodeBlock.of("($T) $L", TypeName.get(component).withoutAnnotations(), boxed)
        : boxed;
  }

  /**
   * The arguments a generated lens's set function, {@code (source, newValue) -> new Money(newValue,
   * source.currency())}, passes to {@code record}'s canonical constructor: every component read
   * from {@code source} as it was, and {@code focused} replaced by {@code newValue}. The lens
   * focuses a primitive component boxed, so {@code newValue} goes through {@link
   * #canonicalArgument}; each read already has its component's own type.
   *
   * @param record the record the lens rebuilds; must not be null
   * @param focused the name of the component the lens focuses; must not be null
   * @return the arguments in component order, comma-separated
   * @since 0.4.11
   */
  public static String rebuildArguments(TypeElement record, String focused) {
    return record.getRecordComponents().stream()
        .map(
            component ->
                component.getSimpleName().contentEquals(focused)
                    ? canonicalArgument(record, component.asType(), CodeBlock.of("newValue"))
                        .toString()
                    : "source." + component.getSimpleName() + "()")
        .collect(Collectors.joining(", "));
  }

  /**
   * The {@link #toCamelCase camelCase} form of an enum constant or type name, as a name a generated
   * method can take: the constant {@code NEW} names {@code new_()} and a subtype {@code Default}
   * names {@code default_()}, as {@link #escapeKeyword} says.
   *
   * @param s the enum constant or type name the method is named after
   * @return the camelCase form, with a trailing underscore when it is a keyword or literal
   */
  public static String toMethodName(String s) {
    return escapeKeyword(toCamelCase(s));
  }

  /**
   * A name a generated method can take: {@code name} itself, or {@code name} with a trailing
   * underscore when it is a Java keyword or literal ({@code true}, {@code false}, {@code null}),
   * which no method can be called.
   *
   * @param name the name derived from the source, such as a property or a type's simple name
   * @return {@code name}, escaped when it is a keyword or literal
   */
  public static String escapeKeyword(String name) {
    return SourceVersion.isKeyword(name) ? name + "_" : name;
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
   * The names Lombok gives a {@code @Singular} collection's clear method, whatever {@code
   * setterPrefix} its setters carry: {@code clear} and the property capitalised, {@code clearTags}
   * for {@code tags} and {@code clearEMails} for {@code eMails}, or under its {@code beanspec}
   * capitalisation, which leaves a name whose second letter is a capital as it is, {@code
   * cleareMails}.
   *
   * @param property the collection's property, as its field is named; must not be null
   * @return the clear method's names, the default spelling first
   * @since 0.5.0
   */
  public static List<String> singularClearNames(String property) {
    // beanspec keeps a name whose second letter is a capital as it is, and capitalises any other.
    String beanspec =
        property.length() > 1 && Character.isUpperCase(property.charAt(1))
            ? property
            : capitalise(property);
    return Stream.of("clear" + capitalise(property), "clear" + beanspec).distinct().toList();
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

  /**
   * The mirror of an annotation on an element, or null where it carries none of that type.
   *
   * @param element the annotated element; must not be null
   * @param annotationFqn the annotation's fully qualified name; must not be null
   * @return the mirror, or null
   * @since 0.4.11
   */
  public static AnnotationMirror findAnnotation(Element element, String annotationFqn) {
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      // An annotation type is always a declared type, and its element a TypeElement.
      TypeElement annotationType = (TypeElement) mirror.getAnnotationType().asElement();
      if (annotationType.getQualifiedName().contentEquals(annotationFqn)) {
        return mirror;
      }
    }
    return null;
  }

  /**
   * The value an annotation is written with for one element, or null where it is left at its
   * default. A diagnostic about that element can be located at the value.
   *
   * @param annotation the annotation's mirror; must not be null
   * @param elementName the element to read; must not be null
   * @return the written value, or null
   * @since 0.4.11
   */
  public static AnnotationValue getAnnotationValue(
      AnnotationMirror annotation, String elementName) {
    return annotation.getElementValues().entrySet().stream()
        .filter(entry -> entry.getKey().getSimpleName().contentEquals(elementName))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  /**
   * A string an annotation carries, or {@code defaultValue} where it does not name that element.
   *
   * <p>An element left at its declared default is not written into the mirror, so the caller's own
   * default stands for it.
   *
   * @param annotation the annotation's mirror; must not be null
   * @param elementName the element to read; must not be null
   * @param defaultValue what to answer where the annotation does not name it
   * @return the string, or the default
   * @since 0.4.11
   */
  public static String getAnnotationString(
      AnnotationMirror annotation, String elementName, String defaultValue) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotation.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals(elementName)) {
        // getValue() never returns null for a present annotation element.
        return entry.getValue().getValue().toString();
      }
    }
    return defaultValue;
  }

  /**
   * The strings an annotation's array element carries, empty where it names none.
   *
   * @param annotation the annotation's mirror; must not be null
   * @param elementName the element to read; must not be null
   * @return the strings, in the order written (non-null)
   * @since 0.4.11
   */
  public static String[] getAnnotationStringArray(AnnotationMirror annotation, String elementName) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotation.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals(elementName)) {
        Object value = entry.getValue().getValue();
        if (value instanceof List<?> list) {
          return list.stream()
              .map(element -> ((AnnotationValue) element).getValue().toString())
              .toArray(String[]::new);
        }
      }
    }
    return new String[0];
  }

  /**
   * The type an annotation's class-constant element names, or null where it names none.
   *
   * @param annotation the annotation's mirror; must not be null
   * @param elementName the element to read; must not be null
   * @return the type, or null
   * @since 0.4.11
   */
  public static TypeMirror getAnnotationTypeMirror(
      AnnotationMirror annotation, String elementName) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotation.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals(elementName)) {
        Object value = entry.getValue().getValue();
        if (value instanceof TypeMirror typeMirror) {
          return typeMirror;
        }
      }
    }
    return null;
  }

  /**
   * A member's type as the instantiated source type sees it, unwrapping an accessor's return.
   *
   * <p>Read off the element, a member of {@code Holder<T>} speaks {@code T}; the spec instantiated
   * it as {@code Holder<List<String>>}, so what the traversal has to be detected for is {@code
   * List<String>}. Reading the declaration instead both rejects a container it could have found and
   * names a variable the spec never wrote.
   *
   * <p>The guard is why this is not {@link #memberOf} outright: that helper lets a raw site erase,
   * and erasing here rejected a container the spec had written (#738). The two raw-site answers
   * differ on purpose - see {@link #memberOf} for the map of which reader wants which.
   *
   * @param types the round's type utilities; must not be null
   * @param sourceType the instantiated source type {@code S}
   * @param member the accessor to read
   * @return the member's type under {@code sourceType}'s instantiation
   * @since 0.4.11
   */
  public static TypeMirror memberTypeOf(
      Types types, DeclaredType sourceType, ExecutableElement member) {
    if (!carriesInstantiation(sourceType)) {
      return member.getReturnType();
    }
    // Total: asMemberOf answers with an ExecutableType for an executable member, and the one
    // shape that would not - an unresolvable source type, whose members resolve to itself -
    // enumerates no members for the caller to have found.
    return ((ExecutableType) types.asMemberOf(sourceType, member)).getReturnType();
  }

  /** How few edits apart two names must be for one to be offered as a misspelling of the other. */
  public static final int NEAR_MISS = 3;

  /**
   * The candidate nearest {@code name}, when it is few enough edits away to be offered as what was
   * meant. Of candidates equally near, the first is kept. For the name of a method that reads or
   * sets one value, {@link NamingConvention#nearest} offers the same value in another convention
   * first.
   *
   * @param name the name as written; must not be null
   * @param candidates the names it could have meant, in the order to prefer them; must not be null
   * @return the nearest candidate under {@link #NEAR_MISS} edits, or empty when none is that near
   * @since 0.4.11
   */
  public static Optional<String> nearestName(String name, List<String> candidates) {
    String best = null;
    int bestDistance = NEAR_MISS;
    for (String candidate : candidates) {
      int distance = editDistance(name, candidate);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return Optional.ofNullable(best);
  }

  /**
   * The Levenshtein distance between two names: the fewest single-character insertions, deletions
   * and substitutions that turn one into the other.
   *
   * @param a one name; must not be null
   * @param b the other; must not be null
   * @return the distance, zero when the names are equal
   * @since 0.4.11
   */
  public static int editDistance(String a, String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int substitution = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        current[j] =
            Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + substitution);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }

  /**
   * The modules compiled from source here that declare {@code packageName}, in name order. javac
   * writes a generated file into the module that declares its package, and a filer refuses a file
   * whose package more than one of them declares just as it refuses a name already taken, so a
   * write handler asks this before reporting a refused file as a collision. A module declares the
   * package in source through its {@code package-info.java} as surely as through a type, so either
   * counts. Where the compiler cannot say which file an element was read from, no module counts
   * (see {@link #compiledFromSource}), and the refused file is reported as a collision.
   *
   * @param elements the compilation's element utilities
   * @param packageName the generated file's package
   * @return the declaring modules' names (non-null; at most one outside a multi-module compilation)
   * @since 0.4.11
   */
  public static List<String> compiledModulesDeclaring(Elements elements, String packageName) {
    return elements.getAllPackageElements(packageName).stream()
        .filter(
            declared ->
                Stream.concat(
                        Stream.of(declared),
                        ElementFilter.typesIn(declared.getEnclosedElements()).stream())
                    .anyMatch(element -> compiledFromSource(elements, element)))
        .map(declared -> elements.getModuleOf(declared).getQualifiedName().toString())
        .sorted()
        .toList();
  }

  /**
   * Whether the compiler read an element from a source file of this compilation: a type's own, or a
   * package's {@code package-info.java}. Only the compiler can say, through {@code
   * Elements.getFileObjectOf}, a default method that throws {@link UnsupportedOperationException}
   * unless the compiler overrides it, as javac does. So this is the one place that asks: an element
   * the compiler cannot place is not taken for source, and each caller treats it as it treats one
   * read from a class file, rather than the compilation aborting on the unsupported operation. A
   * {@code false} is therefore no confirmation of a class file: it is also the answer for an
   * element read from no file at all, such as a package without a {@code package-info.java}.
   *
   * @param elements the compilation's element utilities
   * @param element a type or package
   * @return {@code true} only when the compiler reports the element's file as a source file
   * @since 0.4.11
   */
  public static boolean compiledFromSource(Elements elements, Element element) {
    try {
      return Optional.ofNullable(elements.getFileObjectOf(element))
          .map(JavaFileObject::getKind)
          .filter(JavaFileObject.Kind.SOURCE::equals)
          .isPresent();
    } catch (UnsupportedOperationException unsupported) {
      return false;
    }
  }
}
