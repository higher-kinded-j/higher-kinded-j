// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;

/**
 * Generates navigator wrapper classes for fluent cross-type navigation.
 *
 * <p>Navigator classes wrap FocusPath instances and provide navigation methods for each field of
 * nested types that are also annotated with {@code @GenerateFocus}.
 *
 * <p>This generator supports path type widening:
 *
 * <ul>
 *   <li>{@code FocusPath} → {@code AffinePath} when navigating through optional fields
 *   <li>{@code FocusPath}/{@code AffinePath} → {@code TraversalPath} when navigating through
 *       collections
 * </ul>
 *
 * <p>For example, given:
 *
 * <pre>{@code
 * @GenerateFocus(generateNavigators = true)
 * record Company(String name, Address headquarters, Optional<Address> backup) {}
 *
 * @GenerateFocus(generateNavigators = true)
 * record Address(String street, String city) {}
 * }</pre>
 *
 * <p>This generator creates navigators that return the appropriate path types:
 *
 * <ul>
 *   <li>{@code headquarters().city()} returns {@code FocusPath<Company, String>}
 *   <li>{@code backup().some().city()} returns {@code AffinePath<Company, String>}
 * </ul>
 */
public class NavigatorClassGenerator {

  /** Represents the kind of path being navigated. */
  public enum PathKind {
    /** FocusPath - exactly one element, always present. */
    FOCUS,
    /** AffinePath - zero or one element (optional). */
    AFFINE,
    /** TraversalPath - zero or more elements (collection). */
    TRAVERSAL;

    /** Returns the widened path kind when composing with another kind. */
    public PathKind widen(PathKind other) {
      if (this == TRAVERSAL || other == TRAVERSAL) {
        return TRAVERSAL;
      }
      if (this == AFFINE || other == AFFINE) {
        return AFFINE;
      }
      return FOCUS;
    }
  }

  private static final ClassName FOCUS_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "FocusPath");
  private static final ClassName AFFINE_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "AffinePath");
  private static final ClassName TRAVERSAL_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "TraversalPath");

  // Optional types that widen to AffinePath
  private static final Set<String> OPTIONAL_TYPES =
      Set.of("java.util.Optional", "org.higherkindedj.hkt.maybe.Maybe");

  // Collection types that widen to TraversalPath
  private static final Set<String> COLLECTION_TYPES =
      Set.of("java.util.List", "java.util.Set", "java.util.Collection");

  private final ProcessingEnvironment processingEnv;
  private final Set<String> navigableTypes;
  private final int maxDepth;
  private final Set<String> includeFields;
  private final Set<String> excludeFields;

  /**
   * Creates a new navigator class generator.
   *
   * @param processingEnv the processing environment
   * @param navigableTypes set of fully qualified type names that have @GenerateFocus
   * @param maxDepth maximum depth for navigator chains
   * @param includeFields fields to include (empty means all)
   * @param excludeFields fields to exclude
   */
  public NavigatorClassGenerator(
      ProcessingEnvironment processingEnv,
      Set<String> navigableTypes,
      int maxDepth,
      String[] includeFields,
      String[] excludeFields) {
    this.processingEnv = processingEnv;
    this.navigableTypes = navigableTypes;
    this.maxDepth = Math.max(1, Math.min(10, maxDepth));
    this.includeFields = new HashSet<>(Arrays.asList(includeFields));
    this.excludeFields = new HashSet<>(Arrays.asList(excludeFields));
  }

  /** Returns the ClassName for a given PathKind. */
  private ClassName getPathClassName(PathKind kind) {
    return switch (kind) {
      case FOCUS -> FOCUS_PATH_CLASS;
      case AFFINE -> AFFINE_PATH_CLASS;
      case TRAVERSAL -> TRAVERSAL_PATH_CLASS;
    };
  }

  /**
   * Determines what path kind a field type introduces.
   *
   * @param type the field type to analyze
   * @return AFFINE for optional types, TRAVERSAL for collection types, FOCUS otherwise
   */
  private PathKind getFieldPathKind(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return PathKind.FOCUS;
    }

    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    if (OPTIONAL_TYPES.contains(qualifiedName)) {
      return PathKind.AFFINE;
    }
    if (COLLECTION_TYPES.contains(qualifiedName)) {
      return PathKind.TRAVERSAL;
    }

    // Check for subtypes of Collection
    for (TypeMirror iface : typeElement.getInterfaces()) {
      if (iface.getKind() == TypeKind.DECLARED) {
        TypeElement ifaceElement = (TypeElement) ((DeclaredType) iface).asElement();
        if (COLLECTION_TYPES.contains(ifaceElement.getQualifiedName().toString())) {
          return PathKind.TRAVERSAL;
        }
      }
    }

    return PathKind.FOCUS;
  }

  /**
   * Generates navigator inner classes for a Focus class.
   *
   * @param focusClassBuilder the builder for the Focus class
   * @param recordElement the record being processed
   * @param currentDepth current depth in the navigation chain
   */
  public void generateNavigators(
      TypeSpec.Builder focusClassBuilder, TypeElement recordElement, int currentDepth) {

    generateNavigatorsWithPathKind(focusClassBuilder, recordElement, currentDepth, PathKind.FOCUS);
  }

  /** Generates navigator inner classes with path kind tracking. */
  private void generateNavigatorsWithPathKind(
      TypeSpec.Builder focusClassBuilder,
      TypeElement recordElement,
      int currentDepth,
      PathKind currentPathKind) {

    if (currentDepth >= maxDepth) {
      return;
    }

    List<? extends RecordComponentElement> components = recordElement.getRecordComponents();

    for (RecordComponentElement component : components) {
      if (!shouldGenerateNavigator(component)) {
        continue;
      }

      TypeMirror fieldType = component.asType();
      if (!isNavigableType(fieldType)) {
        continue;
      }

      TypeElement fieldTypeElement = getTypeElement(fieldType);
      if (fieldTypeElement == null) {
        continue;
      }

      // Determine the path kind for this field
      PathKind fieldKind = getFieldPathKind(fieldType);
      PathKind widenedKind = currentPathKind.widen(fieldKind);

      // Generate the navigator class for this field
      TypeSpec navigatorClass =
          generateNavigatorClass(
              component, recordElement, fieldTypeElement, currentDepth, widenedKind);

      focusClassBuilder.addType(navigatorClass);
    }
  }

  /**
   * Generates a navigator class for a specific field.
   *
   * @param component the record component (field)
   * @param sourceRecord the source record type
   * @param targetRecord the target record type (the field's type)
   * @param currentDepth current depth
   * @param pathKind the path kind for this navigator
   * @return the generated navigator TypeSpec
   */
  private TypeSpec generateNavigatorClass(
      RecordComponentElement component,
      TypeElement sourceRecord,
      TypeElement targetRecord,
      int currentDepth,
      PathKind pathKind) {

    String componentName = component.getSimpleName().toString();
    String navigatorClassName = capitalise(componentName) + "Navigator";
    TypeName targetTypeName = TypeName.get(targetRecord.asType());

    // Type parameter S for the source type in the navigator
    TypeVariableName sourceTypeVar = TypeVariableName.get("S");

    // The delegate type depends on the path kind
    ClassName pathClass = getPathClassName(pathKind);
    ParameterizedTypeName delegateType =
        ParameterizedTypeName.get(pathClass, sourceTypeVar, targetTypeName);

    String pathKindDescription =
        switch (pathKind) {
          case FOCUS -> "FocusPath";
          case AFFINE -> "AffinePath (optional navigation)";
          case TRAVERSAL -> "TraversalPath (collection navigation)";
        };

    TypeSpec.Builder navigatorBuilder =
        TypeSpec.classBuilder(navigatorClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addTypeVariable(sourceTypeVar)
            .addJavadoc(
                "Navigator for fluent access to {@code $L} fields.\n\n"
                    + "<p>This navigator wraps a {@link $T} and provides direct navigation methods\n"
                    + "for all fields of {@link $T}.\n\n"
                    + "<p>Path type: $L\n\n"
                    + "@param <S> the source type at the root of the navigation",
                componentName,
                pathClass,
                targetTypeName,
                pathKindDescription);

    // Add delegate field
    navigatorBuilder.addField(
        FieldSpec.builder(delegateType, "delegate", Modifier.PRIVATE, Modifier.FINAL).build());

    // Add constructor
    navigatorBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(delegateType, "delegate")
            .addStatement("this.delegate = java.util.Objects.requireNonNull(delegate)")
            .build());

    // Add delegate accessor methods based on path kind
    addDelegateMethods(navigatorBuilder, sourceTypeVar, targetTypeName, delegateType, pathKind);

    // Add navigation methods for each field of the target record
    addNavigationMethods(navigatorBuilder, targetRecord, sourceTypeVar, currentDepth + 1, pathKind);

    return navigatorBuilder.build();
  }

  /** Adds delegate methods that forward to the underlying path. */
  private void addDelegateMethods(
      TypeSpec.Builder navigatorBuilder,
      TypeVariableName sourceTypeVar,
      TypeName targetTypeName,
      ParameterizedTypeName delegateType,
      PathKind pathKind) {

    switch (pathKind) {
      case FOCUS ->
          addFocusPathDelegateMethods(
              navigatorBuilder, sourceTypeVar, targetTypeName, delegateType);
      case AFFINE ->
          addAffinePathDelegateMethods(
              navigatorBuilder, sourceTypeVar, targetTypeName, delegateType);
      case TRAVERSAL ->
          addTraversalPathDelegateMethods(
              navigatorBuilder, sourceTypeVar, targetTypeName, delegateType);
    }
  }

  /** Adds delegate methods for FocusPath navigators. */
  private void addFocusPathDelegateMethods(
      TypeSpec.Builder navigatorBuilder,
      TypeVariableName sourceTypeVar,
      TypeName targetTypeName,
      ParameterizedTypeName delegateType) {

    // get(S source) -> A
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("get")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(targetTypeName)
            .addStatement("return delegate.get(source)")
            .addJavadoc(
                "Extracts the focused value from the source.\n\n"
                    + "@param source the source structure\n"
                    + "@return the focused value")
            .build());

    // set(A value, S source) -> S
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("set")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(targetTypeName, "value")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.set(value, source)")
            .addJavadoc(
                "Creates a new source with the focused value replaced.\n\n"
                    + "@param value the new value\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with the updated value")
            .build());

    // modify(Function<A, A> f, S source) -> S
    ParameterizedTypeName functionType =
        ParameterizedTypeName.get(ClassName.get(Function.class), targetTypeName, targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("modify")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(functionType, "f")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.modify(f, source)")
            .addJavadoc(
                "Creates a new source with the focused value transformed.\n\n"
                    + "@param f the transformation function\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with the modified value")
            .build());

    // toLens() -> Lens<S, A>
    ParameterizedTypeName lensType =
        ParameterizedTypeName.get(ClassName.get(Lens.class), sourceTypeVar, targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("toLens")
            .addModifiers(Modifier.PUBLIC)
            .returns(lensType)
            .addStatement("return delegate.toLens()")
            .addJavadoc("Extracts the underlying lens.\n\n" + "@return the wrapped Lens")
            .build());

    // toPath() -> FocusPath<S, A>
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("toPath")
            .addModifiers(Modifier.PUBLIC)
            .returns(delegateType)
            .addStatement("return delegate")
            .addJavadoc("Returns the underlying FocusPath.\n\n" + "@return the wrapped FocusPath")
            .build());
  }

  /** Adds delegate methods for AffinePath navigators. */
  private void addAffinePathDelegateMethods(
      TypeSpec.Builder navigatorBuilder,
      TypeVariableName sourceTypeVar,
      TypeName targetTypeName,
      ParameterizedTypeName delegateType) {

    // getOptional(S source) -> Optional<A>
    ParameterizedTypeName optionalType =
        ParameterizedTypeName.get(ClassName.get(Optional.class), targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("getOptional")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(optionalType)
            .addStatement("return delegate.getOptional(source)")
            .addJavadoc(
                "Extracts the focused value if present.\n\n"
                    + "@param source the source structure\n"
                    + "@return Optional containing the value, or empty if not focused")
            .build());

    // set(A value, S source) -> S
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("set")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(targetTypeName, "value")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.set(value, source)")
            .addJavadoc(
                "Creates a new source with the focused value replaced.\n\n"
                    + "@param value the new value\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with the updated value")
            .build());

    // modify(Function<A, A> f, S source) -> S
    ParameterizedTypeName functionType =
        ParameterizedTypeName.get(ClassName.get(Function.class), targetTypeName, targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("modify")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(functionType, "f")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.modify(f, source)")
            .addJavadoc(
                "Modifies the focused value if present.\n\n"
                    + "@param f the transformation function\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with the modified value, or original if not focused")
            .build());

    // matches(S source) -> boolean
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("matches")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(TypeName.BOOLEAN)
            .addStatement("return delegate.matches(source)")
            .addJavadoc(
                "Checks if this path focuses on a value in the given source.\n\n"
                    + "@param source the source structure to test\n"
                    + "@return true if a value is focused, false otherwise")
            .build());

    // toPath() -> AffinePath<S, A>
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("toPath")
            .addModifiers(Modifier.PUBLIC)
            .returns(delegateType)
            .addStatement("return delegate")
            .addJavadoc("Returns the underlying AffinePath.\n\n" + "@return the wrapped AffinePath")
            .build());
  }

  /** Adds delegate methods for TraversalPath navigators. */
  private void addTraversalPathDelegateMethods(
      TypeSpec.Builder navigatorBuilder,
      TypeVariableName sourceTypeVar,
      TypeName targetTypeName,
      ParameterizedTypeName delegateType) {

    // getAll(S source) -> List<A>
    ParameterizedTypeName listType =
        ParameterizedTypeName.get(ClassName.get(List.class), targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("getAll")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(listType)
            .addStatement("return delegate.getAll(source)")
            .addJavadoc(
                "Extracts all focused values from the source.\n\n"
                    + "@param source the source structure\n"
                    + "@return list of all focused values (may be empty)")
            .build());

    // setAll(A value, S source) -> S
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("setAll")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(targetTypeName, "value")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.setAll(value, source)")
            .addJavadoc(
                "Creates a new source with all focused values replaced.\n\n"
                    + "@param value the new value for all focused elements\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with all focused values updated")
            .build());

    // modifyAll(Function<A, A> f, S source) -> S
    ParameterizedTypeName functionType =
        ParameterizedTypeName.get(ClassName.get(Function.class), targetTypeName, targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("modifyAll")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(functionType, "f")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.modifyAll(f, source)")
            .addJavadoc(
                "Creates a new source with all focused values transformed.\n\n"
                    + "@param f the transformation function\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with all focused values modified")
            .build());

    // count(S source) -> int
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("count")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(TypeName.INT)
            .addStatement("return delegate.count(source)")
            .addJavadoc(
                "Counts the number of focused elements.\n\n"
                    + "@param source the source structure\n"
                    + "@return the number of focused elements")
            .build());

    // isEmpty(S source) -> boolean
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("isEmpty")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(TypeName.BOOLEAN)
            .addStatement("return delegate.isEmpty(source)")
            .addJavadoc(
                "Checks if the traversal focuses on no elements.\n\n"
                    + "@param source the source structure\n"
                    + "@return true if no elements are focused")
            .build());

    // toPath() -> TraversalPath<S, A>
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("toPath")
            .addModifiers(Modifier.PUBLIC)
            .returns(delegateType)
            .addStatement("return delegate")
            .addJavadoc(
                "Returns the underlying TraversalPath.\n\n" + "@return the wrapped TraversalPath")
            .build());
  }

  /** Adds navigation methods for each field of the target record. */
  private void addNavigationMethods(
      TypeSpec.Builder navigatorBuilder,
      TypeElement targetRecord,
      TypeVariableName sourceTypeVar,
      int currentDepth,
      PathKind currentPathKind) {

    List<? extends RecordComponentElement> components = targetRecord.getRecordComponents();
    String targetFocusClassName = targetRecord.getSimpleName().toString() + "Focus";
    String targetPackage =
        processingEnv.getElementUtils().getPackageOf(targetRecord).getQualifiedName().toString();
    ClassName targetFocusClass = ClassName.get(targetPackage, targetFocusClassName);

    for (RecordComponentElement component : components) {
      String fieldName = component.getSimpleName().toString();
      TypeMirror fieldType = component.asType();
      TypeName fieldTypeName = TypeName.get(fieldType).box();

      // Determine the path kind for this field and widen appropriately
      PathKind fieldKind = getFieldPathKind(fieldType);
      PathKind widenedKind = currentPathKind.widen(fieldKind);

      // Return type based on widened path kind
      ClassName pathClass = getPathClassName(widenedKind);
      ParameterizedTypeName returnType =
          ParameterizedTypeName.get(pathClass, sourceTypeVar, fieldTypeName);

      String pathDescription =
          switch (widenedKind) {
            case FOCUS -> "FocusPath";
            case AFFINE -> "AffinePath";
            case TRAVERSAL -> "TraversalPath";
          };

      MethodSpec.Builder methodBuilder =
          MethodSpec.methodBuilder(fieldName)
              .addModifiers(Modifier.PUBLIC)
              .returns(returnType)
              .addJavadoc(
                  "Navigates to the {@code $L} field.\n\n"
                      + "@return a $L focusing on the {@code $L} field",
                  fieldName,
                  pathDescription,
                  fieldName);

      // Determine the via method and conversion needed based on path widening
      String viaStatement =
          buildViaStatement(currentPathKind, widenedKind, targetFocusClass, fieldName);

      // Check if the field type is also navigable and we haven't exceeded depth
      if (currentDepth < maxDepth && isNavigableType(fieldType)) {
        TypeElement fieldTypeElement = getTypeElement(fieldType);
        if (fieldTypeElement != null) {
          // Return a navigator instead of plain path
          String fieldNavigatorClassName = capitalise(fieldName) + "Navigator";
          ClassName navigatorClass =
              ClassName.get(targetPackage, targetFocusClassName, fieldNavigatorClassName);
          ParameterizedTypeName navigatorType =
              ParameterizedTypeName.get(navigatorClass, sourceTypeVar);

          methodBuilder.returns(navigatorType);
          methodBuilder.addStatement(
              "return new $T.$L<>($L)", targetFocusClass, fieldNavigatorClassName, viaStatement);
        } else {
          methodBuilder.addStatement("return $L", viaStatement);
        }
      } else {
        methodBuilder.addStatement("return $L", viaStatement);
      }

      navigatorBuilder.addMethod(methodBuilder.build());
    }
  }

  /** Builds the via statement for navigating from current path kind to the widened kind. */
  private String buildViaStatement(
      PathKind currentKind, PathKind widenedKind, ClassName targetFocusClass, String fieldName) {
    String baseVia =
        String.format("delegate.via(%s.%s().toLens())", targetFocusClass.simpleName(), fieldName);

    // If the path kind is widening, we need to add conversion
    if (currentKind == PathKind.FOCUS && widenedKind == PathKind.AFFINE) {
      // FocusPath.via() with AffinePath widens to AffinePath - handled automatically by via()
      // overloads
      return baseVia;
    } else if (currentKind == PathKind.FOCUS && widenedKind == PathKind.TRAVERSAL) {
      // FocusPath to TraversalPath - use asTraversal()
      return baseVia + ".asTraversal()";
    } else if (currentKind == PathKind.AFFINE && widenedKind == PathKind.TRAVERSAL) {
      // AffinePath to TraversalPath - use asTraversal()
      return baseVia + ".asTraversal()";
    }

    return baseVia;
  }

  /** Determines if a field should have a navigator generated. */
  private boolean shouldGenerateNavigator(RecordComponentElement component) {
    String fieldName = component.getSimpleName().toString();

    // If includeFields is specified, only include those fields
    if (!includeFields.isEmpty()) {
      return includeFields.contains(fieldName);
    }

    // Otherwise, exclude fields in excludeFields
    return !excludeFields.contains(fieldName);
  }

  /** Checks if a type is navigable (has @GenerateFocus annotation). */
  private boolean isNavigableType(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    // Check if the type is in our set of navigable types
    if (navigableTypes.contains(qualifiedName)) {
      return true;
    }

    // Also check if the type has @GenerateFocus annotation
    return typeElement.getAnnotation(GenerateFocus.class) != null;
  }

  /** Gets the TypeElement for a TypeMirror. */
  private TypeElement getTypeElement(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return null;
    }
    DeclaredType declaredType = (DeclaredType) type;
    return (TypeElement) declaredType.asElement();
  }

  /** Capitalises the first letter of a string. */
  private String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /**
   * Creates a method spec for a navigator-returning method (replaces the standard FocusPath
   * method).
   *
   * @param component the record component
   * @param recordElement the record being processed
   * @param allComponents all components of the record
   * @param recordTypeName the record's type name
   * @return the method spec
   */
  public MethodSpec createNavigatorMethod(
      RecordComponentElement component,
      TypeElement recordElement,
      List<? extends RecordComponentElement> allComponents,
      TypeName recordTypeName) {

    String componentName = component.getSimpleName().toString();
    TypeMirror fieldType = component.asType();
    TypeName componentTypeName = TypeName.get(fieldType).box();

    // Check if this field should have a navigator generated (respects include/exclude filters)
    if (!shouldGenerateNavigator(component)) {
      return null; // Filtered out, use standard method
    }

    TypeElement fieldTypeElement = getTypeElement(fieldType);
    if (fieldTypeElement == null || !isNavigableType(fieldType)) {
      return null; // Not navigable, use standard method
    }

    // Navigator class name
    String navigatorClassName = capitalise(componentName) + "Navigator";
    String packageName =
        processingEnv.getElementUtils().getPackageOf(recordElement).getQualifiedName().toString();
    String focusClassName = recordElement.getSimpleName().toString() + "Focus";
    ClassName navigatorClass = ClassName.get(packageName, focusClassName, navigatorClassName);

    // Return type: ComponentNavigator<RecordType>
    ParameterizedTypeName returnType = ParameterizedTypeName.get(navigatorClass, recordTypeName);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(componentName)
            .addJavadoc(
                "Creates a navigator for the {@code $L} field of a {@link $T}.\n\n"
                    + "<p>The returned navigator enables fluent navigation into the fields of\n"
                    + "{@link $T}. For example:\n"
                    + "<pre>{@code\n"
                    + "$L.$L().fieldName().get(instance);\n"
                    + "}</pre>\n\n"
                    + "@return A navigator for the {@code $L} field.",
                componentName,
                recordTypeName,
                componentTypeName,
                recordElement.getSimpleName() + "Focus",
                componentName,
                componentName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(returnType);

    // Add type parameters if the record is generic
    for (TypeParameterElement typeParam : recordElement.getTypeParameters()) {
      methodBuilder.addTypeVariable(TypeVariableName.get(typeParam));
    }

    // Build the constructor arguments for the setter lambda
    String constructorArgs =
        allComponents.stream()
            .map(
                c ->
                    c.getSimpleName().toString().equals(componentName)
                        ? "newValue"
                        : "source." + c.getSimpleName() + "()")
            .collect(Collectors.joining(", "));

    // Generate: return new ComponentNavigator<>(FocusPath.of(Lens.of(...)));
    methodBuilder.addStatement(
        "return new $L<>($T.of($T.of($T::$L, (source, newValue) -> new $T($L))))",
        navigatorClassName,
        FOCUS_PATH_CLASS,
        Lens.class,
        recordTypeName,
        componentName,
        recordTypeName,
        constructorArgs);

    return methodBuilder.build();
  }
}
