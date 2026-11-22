// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import com.palantir.javapoet.WildcardTypeName;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;
import javax.lang.model.util.Types;

/**
 * Names a type as the author wrote it, with its type-use annotations kept.
 *
 * <p>{@link TypeName#get(TypeMirror)} rebuilds a name from the element alone and never consults
 * {@link TypeMirror#getAnnotationMirrors()} at any depth, so every type-use annotation on a
 * declaration is dropped on the way into generated source. A client is generated into the annotated
 * interface's own package, which is the consumer's to mark: under a {@code @NullMarked} {@code
 * package-info} or {@code module-info}, an unannotated type <em>means</em> non-null, so a dropped
 * {@code @Nullable} does not leave the contract unstated - it states the opposite of what the
 * author wrote, on a facade that is meant to mirror their interface exactly.
 *
 * <p>Both {@code typeNameOf} forms are copies of the ones in {@code hkj-processor}'s {@code
 * ProcessorUtils} that name a type for a known package, kept rather than shared. Sharing would mean
 * depending on that module, which is an annotation processor: it would join this one on the
 * consumer's processor path and generate optics they never asked for. The walk they perform is
 * javapoet's own; if one copy changes, the other should, and {@code ProcessorUtils} points back
 * here. Both forms are needed: a client restates each method as a member of the client interface,
 * read under the type arguments the client gives the interfaces it extends, and that reading drops
 * what the plain form keeps. The member form of {@link #typeVariableOf(TypeVariable,
 * TypeParameterElement, DeclaredType, String)} has no counterpart there: it follows the loop {@code
 * PathProcessor} writes a bridge's type parameters with, but pairs the bounds from the end, for the
 * reason the method gives.
 */
final class TypeNames {

  private TypeNames() {
    // Utility class - prevent instantiation
  }

  /**
   * The name of a type as written, with its type-use annotations kept, for a client written into
   * {@code targetPackage}.
   *
   * <p>An annotation is kept only where that package can write it cleanly. One javac could not
   * resolve, which is one missing from the compile classpath, as an annotation a base interface in
   * a jar was compiled against can be, one private or package-private to another package, and one
   * deprecated anywhere in its nesting are left off: the first three fail the build compiling the
   * client, and the last draws a warning there that no one can suppress. So is one whose arguments
   * name a class, an enum constant or an annotation that fails the same test, as
   * {@code @Tag(Internal.class)} does where {@code Internal} is package-private to another package.
   * The package matters because a client restates the methods it inherits, which a base interface
   * in another package may have annotated with something only that package can name.
   *
   * <p>A type read with {@link Types#asMemberOf} has already lost the annotations written on a type
   * variable's use; name one of those with {@link #typeNameOf(TypeMirror, TypeMirror, DeclaredType,
   * String)}.
   *
   * @param type the type to name; must not be null
   * @param targetPackage the package the client is written into; must not be null
   * @return its name, annotated as the source annotated it wherever the client can say so
   *     (non-null)
   */
  static TypeName typeNameOf(TypeMirror type, String targetPackage) {
    return new Naming(Map.of(), targetPackage).name(type, type);
  }

  /**
   * The name of a member's type read under an owner, with the type-use annotations its declaration
   * wrote kept, for a client written into {@code targetPackage}.
   *
   * <p>{@link Types#asMemberOf} replaces each type variable a member's type names with what the
   * owner binds it to, and javac's substitution drops any annotation written on that use of the
   * variable: {@code EitherPath<ApiErr, @Nullable T>} comes back as {@code EitherPath<ApiErr, T>},
   * even under the declaring interface itself, where {@code T} is bound to {@code T}. An annotation
   * on a variable's use applies to whatever replaces the variable, so this walks the substituted
   * type beside the declared one and writes each such annotation onto the replacement, unless the
   * replacement already carries one of the same annotation type.
   *
   * <p>The variable may be bound on the way, by a supertype clause between the member and the
   * owner: under {@code Mid<M> extends Repo<@Nullable M>}, the member's {@code T} is Mid's
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
   * @param targetPackage the package the client is written into; must not be null
   * @return its name, annotated as the source annotated it wherever the client can say so
   *     (non-null)
   */
  static TypeName typeNameOf(
      TypeMirror type, TypeMirror declared, DeclaredType owner, String targetPackage) {
    return new Naming(clausesOf(owner), targetPackage).name(type, declared);
  }

  /**
   * Whether a client in {@code targetPackage} can write this annotation cleanly: its type, and
   * every class, enum constant and annotation its arguments name, as {@link
   * #writableFrom(TypeMirror, String)} decides for a type. Only the arguments written count, as
   * javapoet writes no others.
   */
  private static boolean writableFrom(AnnotationMirror annotation, String targetPackage) {
    return writableFrom(annotation.getAnnotationType(), targetPackage)
        && annotation.getElementValues().values().stream()
            .allMatch(value -> writableFrom(value, targetPackage));
  }

  private static boolean writableFrom(AnnotationValue value, String targetPackage) {
    return value.accept(
        new SimpleAnnotationValueVisitor14<Boolean, Void>(true) {
          @Override
          public Boolean visitType(TypeMirror type, Void unused) {
            return writableFrom(type, targetPackage);
          }

          @Override
          public Boolean visitEnumConstant(VariableElement constant, Void unused) {
            return nestingWritableFrom(constant, targetPackage);
          }

          @Override
          public Boolean visitAnnotation(AnnotationMirror nested, Void unused) {
            return writableFrom(nested, targetPackage);
          }

          @Override
          public Boolean visitArray(List<? extends AnnotationValue> values, Void unused) {
            return values.stream().allMatch(element -> writableFrom(element, targetPackage));
          }
        },
        null);
  }

  /**
   * Whether a client in {@code targetPackage} can write this type cleanly: resolved, with every
   * type in its nesting visible from there, and none of them deprecated. A primitive is, and an
   * array is where its component is.
   *
   * <p>A deprecation written only as a javadoc tag is not seen here: reading that needs the round's
   * {@code Elements}, which this walk does not take. Such an annotation is copied, as it was
   * before.
   */
  private static boolean writableFrom(TypeMirror type, String targetPackage) {
    return switch (type.getKind()) {
      case DECLARED -> nestingWritableFrom(((DeclaredType) type).asElement(), targetPackage);
      case ARRAY -> writableFrom(((ArrayType) type).getComponentType(), targetPackage);
      case ERROR -> false;
      default -> true;
    };
  }

  /** Whether an element, and each type it is nested in, is visible from the package and current. */
  private static boolean nestingWritableFrom(Element element, String targetPackage) {
    for (Element current = element;
        current.getKind() != ElementKind.PACKAGE;
        current = current.getEnclosingElement()) {
      Set<Modifier> modifiers = current.getModifiers();
      if (modifiers.contains(Modifier.PRIVATE)
          || current.getAnnotation(Deprecated.class) != null
          || (!modifiers.contains(Modifier.PUBLIC) && !packageOf(current).equals(targetPackage))) {
        return false;
      }
    }
    return true;
  }

  /** The package a type is declared in, walked out from the type itself. */
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
   * One naming walk: the supertype clauses a variable may be bound through, and the package the
   * client is written into, which decides the annotations it can carry.
   */
  private record Naming(Map<Element, TypeMirror> clauses, String targetPackage) {

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
      // way to the owner; the last of them is what the substituted type stands in for. Each
      // variable is followed once, so a cycle of clauses stops where it closes.
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
          annotations.values().stream()
              .filter(annotation -> writableFrom(annotation, targetPackage))
              .map(AnnotationSpecs::copyOf)
              .toList();
      // Dispatch on the kind, as javapoet's own visitor does, rather than on the interface: javac's
      // intersection implements DeclaredType, so a pattern switch would send one down the declared
      // arm and ask it for a class element it does not have.
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
      // Outer, not Inner - and ClassName.get(element) carries none of it. Rebuilding the name under
      // the enclosing keeps it; for an unannotated enclosing it reproduces the same name.
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
   * {@code Object}. Between them {@code <T extends @Nullable Object>} becomes {@code <T>}, and the
   * generated client no longer admits an instantiation the interface it fronts permits.
   *
   * <p>The bounds are all that is copied. An annotation written on the parameter itself, as in
   * {@code <@Marker T>}, is left behind: the same {@code TypeVariableName} declares the parameter
   * and is written wherever it is named, and an annotation legal on the declaration need not be
   * legal at a use - a {@code TYPE_PARAMETER} one is rejected outright as a type argument.
   *
   * @param parameter the type parameter to name; must not be null
   * @param targetPackage the package the client is written into; must not be null
   * @return its name, with its bounds annotated as the source annotated them wherever the client
   *     can say so (non-null)
   */
  static TypeVariableName typeVariableOf(TypeParameterElement parameter, String targetPackage) {
    TypeName[] bounds =
        parameter.getBounds().stream()
            .map(bound -> typeNameOf(bound, targetPackage))
            .toArray(TypeName[]::new);
    return TypeVariableName.get(parameter.getSimpleName().toString(), bounds);
  }

  /**
   * The declaration of a method's own type parameter as the method is read under an owner, with the
   * annotations its declaration wrote on its bounds kept.
   *
   * <p>{@link Types#asMemberOf} leaves the method's own variables unbound but substitutes inside
   * their bounds, so {@code <X extends T>} on a {@code Repo<T>} the client extends as {@code
   * Repo<UserDto>} is read as {@code <X extends UserDto>}. Named from the declaration instead, the
   * bound would still say {@code T}, which the client cannot name. Each bound is named as {@link
   * #typeNameOf(TypeMirror, TypeMirror, DeclaredType, String)} names a member's type, beside the
   * bound the declaration wrote.
   *
   * @param variable the variable as the owner has it, one of {@code asMemberOf}'s type variables;
   *     must not be null
   * @param declared the same parameter as the method declares it; must not be null
   * @param owner the instantiation the method was read under; must not be null
   * @param targetPackage the package the client is written into; must not be null
   * @return its name, with its bounds annotated as the source annotated them wherever the client
   *     can say so (non-null)
   */
  static TypeVariableName typeVariableOf(
      TypeVariable variable,
      TypeParameterElement declared,
      DeclaredType owner,
      String targetPackage) {
    TypeMirror upperBound = variable.getUpperBound();
    List<? extends TypeMirror> bounds =
        upperBound.getKind() == TypeKind.INTERSECTION
            ? ((IntersectionType) upperBound).getBounds()
            : List.of(upperBound);
    List<? extends TypeMirror> declaredBounds = declared.getBounds();
    // Substitution keeps the declaration's bounds in order, but javac rebuilds an intersection of
    // interfaces alone with Object in front, so the two are paired from the end. A bound with no
    // counterpart is that Object, named from itself, which javapoet leaves off the declaration.
    int offset = bounds.size() - declaredBounds.size();
    Naming naming = new Naming(clausesOf(owner), targetPackage);
    TypeName[] names =
        IntStream.range(0, bounds.size())
            .mapToObj(
                index ->
                    naming.name(
                        bounds.get(index),
                        index >= offset ? declaredBounds.get(index - offset) : bounds.get(index)))
            .toArray(TypeName[]::new);
    return TypeVariableName.get(declared.getSimpleName().toString(), names);
  }
}
