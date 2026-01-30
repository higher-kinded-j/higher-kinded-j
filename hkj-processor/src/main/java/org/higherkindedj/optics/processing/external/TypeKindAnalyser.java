// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Analyses external types to determine what kind of optics can be generated.
 *
 * <p>This class examines a {@link TypeElement} and produces a {@link TypeAnalysis} that describes:
 *
 * <ul>
 *   <li>The kind of type (record, sealed interface, enum, wither class)
 *   <li>Fields and their accessors
 *   <li>Wither methods for immutable update
 *   <li>Container fields that need traversals
 *   <li>Whether the type has mutable fields
 * </ul>
 */
public class TypeKindAnalyser {

  private final Types typeUtils;

  /**
   * Creates a new TypeKindAnalyser.
   *
   * @param typeUtils the type utilities from the processing environment
   */
  public TypeKindAnalyser(Types typeUtils) {
    this.typeUtils = typeUtils;
  }

  /**
   * Analyses a type element to determine what optics can be generated.
   *
   * @param typeElement the type to analyse
   * @return the analysis result
   */
  public TypeAnalysis analyseType(TypeElement typeElement) {
    // Check for record first
    if (typeElement.getKind() == ElementKind.RECORD) {
      return analyseRecord(typeElement);
    }

    // Check for sealed interface
    if (typeElement.getKind() == ElementKind.INTERFACE
        && typeElement.getModifiers().contains(Modifier.SEALED)) {
      return analyseSealedInterface(typeElement);
    }

    // Check for enum
    if (typeElement.getKind() == ElementKind.ENUM) {
      return analyseEnum(typeElement);
    }

    // Check for class with wither methods
    if (typeElement.getKind() == ElementKind.CLASS) {
      return analyseClass(typeElement);
    }

    // Unsupported type
    return TypeAnalysis.unsupported(typeElement, false);
  }

  private TypeAnalysis analyseRecord(TypeElement recordElement) {
    List<FieldInfo> fields = new ArrayList<>();

    for (RecordComponentElement component : recordElement.getRecordComponents()) {
      String name = component.getSimpleName().toString();
      TypeMirror type = component.asType();

      Optional<ContainerType> containerType = detectContainerType(type);
      if (containerType.isPresent()) {
        fields.add(FieldInfo.forRecordComponent(name, type, containerType.get()));
      } else {
        fields.add(FieldInfo.forRecordComponent(name, type));
      }
    }

    return TypeAnalysis.forRecord(recordElement, fields);
  }

  private TypeAnalysis analyseSealedInterface(TypeElement sealedInterface) {
    List<TypeElement> permittedSubtypes = new ArrayList<>();

    for (TypeMirror permittedType : sealedInterface.getPermittedSubclasses()) {
      TypeElement subtypeElement = (TypeElement) typeUtils.asElement(permittedType);
      permittedSubtypes.add(subtypeElement);
    }

    return TypeAnalysis.forSealedInterface(sealedInterface, permittedSubtypes);
  }

  private TypeAnalysis analyseEnum(TypeElement enumElement) {
    List<String> constants = new ArrayList<>();

    for (var enclosed : enumElement.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.ENUM_CONSTANT) {
        constants.add(enclosed.getSimpleName().toString());
      }
    }

    return TypeAnalysis.forEnum(enumElement, constants);
  }

  private TypeAnalysis analyseClass(TypeElement classElement) {
    List<WitherInfo> witherMethods = detectWitherMethods(classElement);
    boolean hasMutableFields = detectMutableFields(classElement);

    if (witherMethods.isEmpty()) {
      // No withers found - this is an unsupported class
      return TypeAnalysis.unsupported(classElement, hasMutableFields);
    }

    // Convert wither methods to field info
    List<FieldInfo> fields = new ArrayList<>();
    for (WitherInfo wither : witherMethods) {
      Optional<ContainerType> containerType = detectContainerType(wither.parameterType());
      if (containerType.isPresent()) {
        fields.add(
            FieldInfo.forGetter(
                wither.fieldName(),
                wither.parameterType(),
                wither.getterMethodName(),
                CopyStrategy.WITHER,
                containerType.get()));
      } else {
        fields.add(
            FieldInfo.forGetter(
                wither.fieldName(),
                wither.parameterType(),
                wither.getterMethodName(),
                CopyStrategy.WITHER));
      }
    }

    return TypeAnalysis.forWitherClass(classElement, fields, witherMethods, hasMutableFields);
  }

  /**
   * Detects wither methods on a class.
   *
   * <p>A wither method must:
   *
   * <ul>
   *   <li>Be named {@code withXxx} where {@code xxx} is the field name
   *   <li>Take exactly one parameter
   *   <li>Return the same type as the declaring class
   *   <li>Be public and non-static
   * </ul>
   *
   * @param classElement the class to analyse
   * @return list of detected wither methods
   */
  public List<WitherInfo> detectWitherMethods(TypeElement classElement) {
    List<WitherInfo> withers = new ArrayList<>();
    TypeMirror classType = classElement.asType();

    for (var enclosed : classElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.METHOD) {
        continue;
      }

      ExecutableElement method = (ExecutableElement) enclosed;
      String methodName = method.getSimpleName().toString();

      // Must start with "with" and have more characters
      if (!methodName.startsWith("with") || methodName.length() <= 4) {
        continue;
      }

      // Must be public and non-static
      if (!method.getModifiers().contains(Modifier.PUBLIC)
          || method.getModifiers().contains(Modifier.STATIC)) {
        continue;
      }

      // Must take exactly one parameter
      if (method.getParameters().size() != 1) {
        continue;
      }

      // Must return the same type (or a subtype) as the declaring class
      TypeMirror returnType = method.getReturnType();
      if (!typeUtils.isAssignable(returnType, typeUtils.erasure(classType))) {
        continue;
      }

      // Extract field name from withXxx -> xxx
      String fieldName = extractFieldName(methodName);

      // Find corresponding getter method
      String getterName = findGetterMethod(classElement, fieldName, method.getParameters().get(0));
      if (getterName == null) {
        continue; // No getter found, skip this wither
      }

      withers.add(WitherInfo.of(method, fieldName, getterName));
    }

    return withers;
  }

  private String extractFieldName(String witherMethodName) {
    // withYear -> year, withDayOfMonth -> dayOfMonth
    String afterWith = witherMethodName.substring(4);
    return afterWith.substring(0, 1).toLowerCase(Locale.ROOT) + afterWith.substring(1);
  }

  private String findGetterMethod(
      TypeElement classElement, String fieldName, VariableElement witherParam) {
    TypeMirror expectedType = witherParam.asType();

    // Try various getter naming conventions
    String[] getterCandidates = {
      fieldName, // record-style: year()
      "get" + capitalise(fieldName), // JavaBean: getYear()
      "is" + capitalise(fieldName) // boolean: isActive()
    };

    for (var enclosed : classElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.METHOD) {
        continue;
      }

      ExecutableElement method = (ExecutableElement) enclosed;
      String methodName = method.getSimpleName().toString();

      // Check if method name matches any getter pattern
      for (String candidate : getterCandidates) {
        if (methodName.equals(candidate)) {
          // Must be public, non-static, take no parameters
          if (!method.getModifiers().contains(Modifier.PUBLIC)
              || method.getModifiers().contains(Modifier.STATIC)
              || !method.getParameters().isEmpty()) {
            continue;
          }

          // Return type must match wither parameter type
          if (typeUtils.isSameType(method.getReturnType(), expectedType)) {
            return methodName;
          }
        }
      }
    }

    return null;
  }

  /**
   * Detects whether a class has mutable fields (setters).
   *
   * @param classElement the class to analyse
   * @return true if the class has setter methods
   */
  public boolean detectMutableFields(TypeElement classElement) {
    for (var enclosed : classElement.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.METHOD) {
        continue;
      }

      ExecutableElement method = (ExecutableElement) enclosed;
      String methodName = method.getSimpleName().toString();

      // Check for setter pattern: setXxx with void return
      if (methodName.startsWith("set")
          && methodName.length() > 3
          && method.getModifiers().contains(Modifier.PUBLIC)
          && !method.getModifiers().contains(Modifier.STATIC)
          && method.getParameters().size() == 1
          && method.getReturnType().getKind() == TypeKind.VOID) {
        return true;
      }
    }

    return false;
  }

  /**
   * Detects if a type is a container type that can have a traversal generated.
   *
   * <p>This method uses exact type matching (e.g., only {@code java.util.List}, not subtypes like
   * {@code ArrayList}). For subtype-aware detection, use {@link
   * #detectContainerTypeWithSubtypes(TypeMirror, javax.lang.model.util.Elements)}.
   *
   * @param type the type to check
   * @return the container type info if detected, empty otherwise
   */
  public Optional<ContainerType> detectContainerType(TypeMirror type) {
    // Check for array
    if (type.getKind() == TypeKind.ARRAY) {
      ArrayType arrayType = (ArrayType) type;
      return Optional.of(ContainerType.of(ContainerType.Kind.ARRAY, arrayType.getComponentType()));
    }

    // Check for declared types (List, Set, Optional, Map)
    if (type.getKind() != TypeKind.DECLARED) {
      return Optional.empty();
    }

    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    // Check for List
    if (qualifiedName.equals("java.util.List")) {
      if (!declaredType.getTypeArguments().isEmpty()) {
        return Optional.of(
            ContainerType.of(ContainerType.Kind.LIST, declaredType.getTypeArguments().get(0)));
      }
    }

    // Check for Set
    if (qualifiedName.equals("java.util.Set")) {
      if (!declaredType.getTypeArguments().isEmpty()) {
        return Optional.of(
            ContainerType.of(ContainerType.Kind.SET, declaredType.getTypeArguments().get(0)));
      }
    }

    // Check for Optional
    if (qualifiedName.equals("java.util.Optional")) {
      if (!declaredType.getTypeArguments().isEmpty()) {
        return Optional.of(
            ContainerType.of(ContainerType.Kind.OPTIONAL, declaredType.getTypeArguments().get(0)));
      }
    }

    // Check for Map (traverse values)
    if (qualifiedName.equals("java.util.Map")) {
      if (declaredType.getTypeArguments().size() >= 2) {
        return Optional.of(
            ContainerType.forMap(
                declaredType.getTypeArguments().get(0), declaredType.getTypeArguments().get(1)));
      }
    }

    return Optional.empty();
  }

  /**
   * Detects if a type is a container type, including subtypes like {@code ArrayList} for {@code
   * List}.
   *
   * <p>This method checks the type hierarchy to determine if a type implements or extends a known
   * container type. For example, {@code ArrayList<String>} is detected as {@code List<String>}.
   *
   * <p>Supported container types:
   *
   * <ul>
   *   <li>{@code List<A>} and subtypes (ArrayList, LinkedList, etc.)
   *   <li>{@code Set<A>} and subtypes (HashSet, TreeSet, LinkedHashSet, etc.)
   *   <li>{@code Optional<A>} (exact match only, as Optional is final)
   *   <li>{@code Map<K, V>} and subtypes (HashMap, TreeMap, etc.)
   *   <li>{@code A[]} arrays
   * </ul>
   *
   * @param type the type to check
   * @param elementUtils the element utilities for looking up type elements
   * @return the container type info if detected, empty otherwise
   */
  public Optional<ContainerType> detectContainerTypeWithSubtypes(
      TypeMirror type, Elements elementUtils) {
    // Check for array first
    if (type.getKind() == TypeKind.ARRAY) {
      ArrayType arrayType = (ArrayType) type;
      return Optional.of(ContainerType.of(ContainerType.Kind.ARRAY, arrayType.getComponentType()));
    }

    // Check for declared types
    if (type.getKind() != TypeKind.DECLARED) {
      return Optional.empty();
    }

    DeclaredType declaredType = (DeclaredType) type;

    // Get the erased type for subtype checking
    TypeMirror erasedType = typeUtils.erasure(type);

    // Check for List (and subtypes like ArrayList, LinkedList)
    TypeElement listElement = elementUtils.getTypeElement("java.util.List");
    if (listElement != null
        && typeUtils.isSubtype(erasedType, typeUtils.erasure(listElement.asType()))) {
      List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
      if (!typeArgs.isEmpty()) {
        return Optional.of(ContainerType.of(ContainerType.Kind.LIST, typeArgs.get(0)));
      }
      // Raw type - cannot determine element type
      return Optional.empty();
    }

    // Check for Set (and subtypes like HashSet, TreeSet)
    TypeElement setElement = elementUtils.getTypeElement("java.util.Set");
    if (setElement != null
        && typeUtils.isSubtype(erasedType, typeUtils.erasure(setElement.asType()))) {
      List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
      if (!typeArgs.isEmpty()) {
        return Optional.of(ContainerType.of(ContainerType.Kind.SET, typeArgs.get(0)));
      }
      // Raw type - cannot determine element type
      return Optional.empty();
    }

    // Check for Optional (exact match, as Optional is final)
    TypeElement optionalElement = elementUtils.getTypeElement("java.util.Optional");
    if (optionalElement != null
        && typeUtils.isSameType(erasedType, typeUtils.erasure(optionalElement.asType()))) {
      List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
      if (!typeArgs.isEmpty()) {
        return Optional.of(ContainerType.of(ContainerType.Kind.OPTIONAL, typeArgs.get(0)));
      }
      // Raw type - cannot determine element type
      return Optional.empty();
    }

    // Check for Map (and subtypes like HashMap, TreeMap)
    TypeElement mapElement = elementUtils.getTypeElement("java.util.Map");
    if (mapElement != null
        && typeUtils.isSubtype(erasedType, typeUtils.erasure(mapElement.asType()))) {
      List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
      if (typeArgs.size() >= 2) {
        return Optional.of(ContainerType.forMap(typeArgs.get(0), typeArgs.get(1)));
      }
      // Raw type - cannot determine element types
      return Optional.empty();
    }

    return Optional.empty();
  }

  private String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
  }
}
