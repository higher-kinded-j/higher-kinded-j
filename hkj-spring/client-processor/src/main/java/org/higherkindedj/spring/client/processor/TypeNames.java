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
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;

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
 * <p>This is a copy of {@code ProcessorUtils.typeNameOf} in {@code hkj-processor}, kept rather than
 * shared. Sharing would mean depending on that module, which is an annotation processor: it would
 * join this one on the consumer's processor path and generate optics they never asked for. The two
 * copies are small, and the walk they perform is javapoet's own; if one changes, the other should.
 * Only the form that names a type for a known package is copied. The member form there puts back
 * what reading a member under an instantiation drops, and a client restates each method as its
 * interface declares it, never read under an instantiation.
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
   * client, and the last draws a warning there that no one can suppress. The package matters
   * because a client restates the methods it inherits, which a base interface in another package
   * may have annotated with something only that package can name.
   *
   * @param type the type to name; must not be null
   * @param targetPackage the package the client is written into; must not be null
   * @return its name, annotated as the source annotated it wherever the client can say so
   *     (non-null)
   */
  static TypeName typeNameOf(TypeMirror type, String targetPackage) {
    List<AnnotationSpec> annotations =
        type.getAnnotationMirrors().stream()
            .filter(annotation -> writableFrom(annotation.getAnnotationType(), targetPackage))
            .map(AnnotationSpec::get)
            .toList();
    // Dispatch on the kind, as javapoet's own visitor does, rather than on the interface: javac's
    // intersection implements DeclaredType, so a pattern switch would send one down the declared
    // arm and ask it for a class element it does not have.
    TypeName name =
        switch (type.getKind()) {
          case ARRAY ->
              ArrayTypeName.of(typeNameOf(((ArrayType) type).getComponentType(), targetPackage));
          case WILDCARD -> wildcardNameOf((WildcardType) type, targetPackage);
          case DECLARED, ERROR -> declaredNameOf((DeclaredType) type, targetPackage);
          default -> TypeName.get(type);
        };
    return annotations.isEmpty() ? name : name.annotated(annotations);
  }

  /**
   * Whether a client in {@code targetPackage} can write this annotation type cleanly: resolved,
   * with every type in its nesting visible from there, and none of them deprecated.
   *
   * <p>A deprecation written only as a javadoc tag is not seen here: reading that needs the round's
   * {@code Elements}, which this walk does not take. Such an annotation is copied, as it was
   * before.
   */
  private static boolean writableFrom(DeclaredType annotationType, String targetPackage) {
    if (annotationType.getKind() == TypeKind.ERROR) {
      return false;
    }
    for (Element current = annotationType.asElement();
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

  private static TypeName declaredNameOf(DeclaredType declared, String targetPackage) {
    ClassName rawType = ClassName.get((TypeElement) declared.asElement());
    TypeMirror enclosingType = declared.getEnclosingType();
    // A static member has no enclosing instance type, so javac reports NONE for it and the kind
    // test alone settles both cases.
    TypeName enclosing =
        enclosingType.getKind() == TypeKind.NONE ? null : typeNameOf(enclosingType, targetPackage);
    List<TypeName> argumentNames =
        declared.getTypeArguments().stream()
            .map(argument -> typeNameOf(argument, targetPackage))
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

  private static TypeName wildcardNameOf(WildcardType wildcard, String targetPackage) {
    TypeMirror extendsBound = wildcard.getExtendsBound();
    if (extendsBound != null) {
      return WildcardTypeName.subtypeOf(typeNameOf(extendsBound, targetPackage));
    }
    TypeMirror superBound = wildcard.getSuperBound();
    return superBound == null
        ? WildcardTypeName.subtypeOf(ClassName.OBJECT)
        : WildcardTypeName.supertypeOf(typeNameOf(superBound, targetPackage));
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
}
