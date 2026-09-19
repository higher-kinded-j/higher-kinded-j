// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

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
    List<WitherInfo> detected = detectWitherMethods(classElement);
    boolean hasMutableFields = detectMutableFields(classElement);

    if (detected.isEmpty()) {
      // No withers found - this is an unsupported class
      return TypeAnalysis.unsupported(classElement, hasMutableFields);
    }

    // One lens per field name: a class that spells its getter more than one way has a wither for
    // each, and the pair whose getter the rule prefers is the one generated. The choice is made
    // over all of a field's withers before any is reported, so that what the notes say is what
    // was generated however many spellings there are.
    Map<String, List<WitherInfo>> byField = new LinkedHashMap<>();
    for (WitherInfo wither : detected) {
      byField.computeIfAbsent(wither.fieldName(), field -> new ArrayList<>()).add(wither);
    }
    List<WitherInfo> witherMethods = new ArrayList<>();
    List<TypeAnalysis.LeftOutWither> leftOut = new ArrayList<>();
    byField.forEach(
        (field, candidates) -> {
          // Each rank stands for one getter spelling, and a wither with no getter never reaches
          // here, so exactly one candidate is lowest.
          WitherInfo kept =
              candidates.stream()
                  .min(Comparator.comparingInt(TypeKindAnalyser::getterRank))
                  .orElseThrow();
          witherMethods.add(kept);
          candidates.stream()
              .filter(candidate -> candidate != kept)
              .forEach(candidate -> leftOut.add(new TypeAnalysis.LeftOutWither(kept, candidate)));
        });

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

    return TypeAnalysis.forWitherClass(
        classElement, fields, List.copyOf(witherMethods), hasMutableFields, List.copyOf(leftOut));
  }

  /**
   * The spellings a field's getter is looked for under, in the order the pairing rule prefers them:
   * the field's own name, then {@code getXxx}, then {@code isXxx}.
   */
  private static List<String> getterSpellings(String fieldName) {
    return List.of(
        fieldName,
        "get" + ProcessorUtils.capitalise(fieldName),
        "is" + ProcessorUtils.capitalise(fieldName));
  }

  /**
   * How far down those spellings a wither's getter is. Two withers that reach one field name are
   * told apart by it, so which lens is generated does not depend on the order the class declares
   * its members in. The getter is always one of the spellings, since that is what paired it.
   */
  private static int getterRank(WitherInfo wither) {
    return getterSpellings(wither.fieldName()).indexOf(wither.getterMethodName());
  }

  /**
   * Detects wither methods on a class.
   *
   * <p>A wither method must:
   *
   * <ul>
   *   <li>Be named {@code withXxx} where {@code xxx} is the field name
   *   <li>Take exactly one parameter
   *   <li>Hand back the declaring class under its own type arguments, as {@link
   *       ProcessorUtils#returnsOwner} reads it
   *   <li>Be public and non-static
   * </ul>
   *
   * @param classElement the class to analyse
   * @return list of detected wither methods
   */
  public List<WitherInfo> detectWitherMethods(TypeElement classElement) {
    List<WitherInfo> withers = new ArrayList<>();
    // A class element's own type is always a declared type.
    DeclaredType classType = (DeclaredType) classElement.asType();

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

      // Must hand back the declaring class as declared: the lens returns the result as that type,
      // so a raw return would be an unchecked conversion, and 'Tagged<String>' from a 'Tagged<T>'
      // no conversion at all.
      if (!ProcessorUtils.returnsOwner(typeUtils, classType, method)) {
        continue;
      }

      // Extract field name from withXxx -> xxx
      String fieldName = extractFieldName(methodName);

      // Find corresponding getter method
      String getterName = findGetterMethod(classElement, fieldName, method.getParameters().get(0));
      if (getterName == null) {
        continue; // No getter found, skip this wither
      }

      withers.add(WitherInfo.of(method, fieldName, getterName, isOverloaded(classElement, method)));
    }

    return withers;
  }

  /**
   * Whether another one-parameter method of the wither's name could take the generated call.
   *
   * <p>The lens hands its setter a boxed value, and javac's first phase prefers a method that takes
   * it without unboxing, so an overload taking the box, a supertype of it or a type variable binds
   * ahead of a wither taking a primitive. The generated lens passes such a parameter unboxed where
   * one exists. Where none does, the call has only the wither to bind and the cast would be noise.
   *
   * <p>Only a method the call could bind counts: one that takes the boxed value, and one the
   * generated class could call, so a {@code private} overload and one no boxed value reaches are
   * both left out. An override declares the same parameter type and is the same method to a caller,
   * so it is not one either; the search is by parameter type rather than by declaring type for that
   * reason.
   *
   * @param classElement the class the wither is read on
   * @param wither the wither method
   * @return true when the class, or a supertype, declares another one-parameter method of the name
   */
  private boolean isOverloaded(TypeElement classElement, ExecutableElement wither) {
    TypeMirror declared = wither.getParameters().getFirst().asType();
    TypeMirror parameter = typeUtils.erasure(declared);
    // What the lens hands the setter, which is what the call is resolved with.
    TypeMirror argument =
        declared.getKind().isPrimitive()
            ? typeUtils.boxedClass((PrimitiveType) declared).asType()
            : declared;
    Name name = wither.getSimpleName();
    Deque<TypeMirror> queue = new ArrayDeque<>();
    Set<String> seen = new HashSet<>();
    queue.add(classElement.asType());
    while (!queue.isEmpty()) {
      TypeMirror current = queue.poll();
      // Every supertype of a class is declared; nothing else reaches the queue.
      TypeElement element = (TypeElement) ((DeclaredType) current).asElement();
      if (!seen.add(element.getQualifiedName().toString())) {
        continue;
      }
      for (ExecutableElement method : ElementFilter.methodsIn(element.getEnclosedElements())) {
        if (method.getSimpleName().equals(name)
            && method.getParameters().size() == 1
            && !method.getModifiers().contains(Modifier.PRIVATE)) {
          TypeMirror other = typeUtils.erasure(method.getParameters().getFirst().asType());
          if (!typeUtils.isSameType(other, parameter) && typeUtils.isAssignable(argument, other)) {
            return true;
          }
        }
      }
      queue.addAll(typeUtils.directSupertypes(current));
    }
    return false;
  }

  private String extractFieldName(String witherMethodName) {
    // withYear -> year, withDayOfMonth -> dayOfMonth
    String afterWith = witherMethodName.substring(4);
    return afterWith.substring(0, 1).toLowerCase(Locale.ROOT) + afterWith.substring(1);
  }

  private String findGetterMethod(
      TypeElement classElement, String fieldName, VariableElement witherParam) {
    TypeMirror expectedType = witherParam.asType();

    List<String> getterCandidates = getterSpellings(fieldName);

    // The spellings are tried in order, so a class that declares more than one of them pairs
    // through the same spelling whichever order its members are read in.
    for (String candidate : getterCandidates) {
      for (var enclosed : classElement.getEnclosedElements()) {
        if (enclosed.getKind() != ElementKind.METHOD) {
          continue;
        }

        ExecutableElement method = (ExecutableElement) enclosed;
        if (!method.getSimpleName().contentEquals(candidate)) {
          continue;
        }
        // Must be public, non-static, take no parameters
        if (!method.getModifiers().contains(Modifier.PUBLIC)
            || method.getModifiers().contains(Modifier.STATIC)
            || !method.getParameters().isEmpty()) {
          continue;
        }
        // Return type must match wither parameter type
        if (typeUtils.isSameType(method.getReturnType(), expectedType)) {
          return candidate;
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
   * <p>The match is exact: {@code java.util.List} is a container, {@code ArrayList} is not. The
   * standard traversal for each container rebuilds a value of the interface type ({@code
   * Traversals.forList()} hands back an unmodifiable {@code List}), and a field declared as a
   * subtype could not take that value back: the write side of the composed optic would throw {@code
   * ClassCastException} on first use. So a field of a concrete container type gets no traversal
   * from {@code @ImportOptics}, and is refused by {@code @ThroughField}, which asks for an explicit
   * one.
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

    // Check for declared types (List, Set, Collection, Optional, Map)
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

    // Check for Collection
    if (qualifiedName.equals("java.util.Collection")) {
      if (!declaredType.getTypeArguments().isEmpty()) {
        return Optional.of(
            ContainerType.of(
                ContainerType.Kind.COLLECTION, declaredType.getTypeArguments().get(0)));
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
}
