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
 */
final class TypeNames {

  private TypeNames() {
    // Utility class - prevent instantiation
  }

  /**
   * The name of a type as written, with its type-use annotations kept.
   *
   * @param type the type to name; must not be null
   * @return its name, annotated as the source annotated it (non-null)
   */
  static TypeName typeNameOf(TypeMirror type) {
    List<AnnotationSpec> annotations =
        type.getAnnotationMirrors().stream().map(AnnotationSpec::get).toList();
    // Dispatch on the kind, as javapoet's own visitor does, rather than on the interface: javac's
    // intersection implements DeclaredType, so a pattern switch would send one down the declared
    // arm and ask it for a class element it does not have.
    TypeName name =
        switch (type.getKind()) {
          case ARRAY -> ArrayTypeName.of(typeNameOf(((ArrayType) type).getComponentType()));
          case WILDCARD -> wildcardNameOf((WildcardType) type);
          case DECLARED, ERROR -> declaredNameOf((DeclaredType) type);
          default -> TypeName.get(type);
        };
    return annotations.isEmpty() ? name : name.annotated(annotations);
  }

  private static TypeName declaredNameOf(DeclaredType declared) {
    ClassName rawType = ClassName.get((TypeElement) declared.asElement());
    TypeMirror enclosingType = declared.getEnclosingType();
    // A static member has no enclosing instance type, so javac reports NONE for it and the kind
    // test alone settles both cases.
    TypeName enclosing =
        enclosingType.getKind() == TypeKind.NONE ? null : typeNameOf(enclosingType);
    List<TypeName> argumentNames =
        declared.getTypeArguments().stream().map(TypeNames::typeNameOf).toList();
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

  private static TypeName wildcardNameOf(WildcardType wildcard) {
    TypeMirror extendsBound = wildcard.getExtendsBound();
    if (extendsBound != null) {
      return WildcardTypeName.subtypeOf(typeNameOf(extendsBound));
    }
    TypeMirror superBound = wildcard.getSuperBound();
    return superBound == null
        ? WildcardTypeName.subtypeOf(ClassName.OBJECT)
        : WildcardTypeName.supertypeOf(typeNameOf(superBound));
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
   * @return its name, with its bounds annotated as the source annotated them (non-null)
   */
  static TypeVariableName typeVariableOf(TypeParameterElement parameter) {
    TypeName[] bounds =
        parameter.getBounds().stream().map(TypeNames::typeNameOf).toArray(TypeName[]::new);
    return TypeVariableName.get(parameter.getSimpleName().toString(), bounds);
  }
}
