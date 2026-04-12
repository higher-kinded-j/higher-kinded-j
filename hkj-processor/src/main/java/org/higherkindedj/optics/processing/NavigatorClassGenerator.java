// Copyright (c) 2025 - 2026 Magnus Smith
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
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.OpticExpressionResolver;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

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

    /**
     * Returns the widened path kind when composing with another kind.
     *
     * @param other the other path kind to compose with
     * @return the widened path kind
     */
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
  private final List<TraversableGenerator> traversableGenerators;

  /**
   * Creates a new navigator class generator.
   *
   * @param processingEnv the processing environment
   * @param navigableTypes set of fully qualified type names that have @GenerateFocus
   * @param maxDepth maximum depth for navigator chains
   * @param includeFields fields to include (empty means all)
   * @param excludeFields fields to exclude
   * @param traversableGenerators SPI generators for recognising additional container types
   */
  public NavigatorClassGenerator(
      ProcessingEnvironment processingEnv,
      Set<String> navigableTypes,
      int maxDepth,
      String[] includeFields,
      String[] excludeFields,
      List<TraversableGenerator> traversableGenerators) {
    this.processingEnv = processingEnv;
    this.navigableTypes = navigableTypes;
    this.maxDepth = Math.max(1, Math.min(10, maxDepth));
    this.includeFields = new HashSet<>(Arrays.asList(includeFields));
    this.excludeFields = new HashSet<>(Arrays.asList(excludeFields));
    this.traversableGenerators = traversableGenerators != null ? traversableGenerators : List.of();
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
   * Determines what path kind a field type introduces, considering annotations. Recursively
   * composes path kinds for nested container types (e.g., Optional&lt;List&lt;String&gt;&gt;
   * produces TRAVERSAL).
   *
   * @param component the record component (may be null for nested navigation)
   * @param type the field type to analyse
   * @return AFFINE for optional/nullable types, TRAVERSAL for collection types, FOCUS otherwise
   */
  private PathKind getFieldPathKind(RecordComponentElement component, TypeMirror type) {
    // Check for @Nullable annotation first
    if (component != null && NullableAnnotations.hasNullableAnnotation(component)) {
      return PathKind.AFFINE;
    }

    return getFieldPathKindRecursive(type, 0);
  }

  /** Maximum recursion depth for nested container path kind analysis. */
  private static final int MAX_NAVIGATOR_NESTING_DEPTH = 3;

  /**
   * Recursively determines the composed path kind for a type, accounting for nested containers.
   *
   * @param type the type to analyse
   * @param depth current recursion depth
   * @return the composed path kind
   */
  private PathKind getFieldPathKindRecursive(TypeMirror type, int depth) {
    if (depth >= MAX_NAVIGATOR_NESTING_DEPTH || type.getKind() != TypeKind.DECLARED) {
      return PathKind.FOCUS;
    }

    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    if (OPTIONAL_TYPES.contains(qualifiedName)) {
      PathKind innerKind = getInnerPathKind(declaredType, 0, depth);
      return PathKind.AFFINE.widen(innerKind);
    }
    if (COLLECTION_TYPES.contains(qualifiedName)) {
      PathKind innerKind = getInnerPathKind(declaredType, 0, depth);
      return PathKind.TRAVERSAL.widen(innerKind);
    }

    // Check for subtypes of Collection
    for (TypeMirror iface : typeElement.getInterfaces()) {
      if (iface.getKind() == TypeKind.DECLARED) {
        TypeElement ifaceElement = (TypeElement) ((DeclaredType) iface).asElement();
        if (COLLECTION_TYPES.contains(ifaceElement.getQualifiedName().toString())) {
          PathKind innerKind = getInnerPathKind(declaredType, 0, depth);
          return PathKind.TRAVERSAL.widen(innerKind);
        }
      }
    }

    // Consult TraversableGenerator SPI for additional container types.
    TraversableGenerator matched = findSpiGenerator(type);
    if (matched != null) {
      PathKind spiKind =
          switch (matched.getCardinality()) {
            case ZERO_OR_ONE -> PathKind.AFFINE;
            case ZERO_OR_MORE -> PathKind.TRAVERSAL;
          };
      PathKind innerKind =
          getInnerPathKind(declaredType, matched.getFocusTypeArgumentIndex(), depth);
      return spiKind.widen(innerKind);
    }

    return PathKind.FOCUS;
  }

  /**
   * Gets the composed path kind of the inner type argument at the given index.
   *
   * @param declaredType the outer container type
   * @param typeArgIndex the index of the type argument to check
   * @param currentDepth the current recursion depth
   * @return the path kind of the inner type, or FOCUS if not a container
   */
  private PathKind getInnerPathKind(DeclaredType declaredType, int typeArgIndex, int currentDepth) {
    List<? extends TypeMirror> args = declaredType.getTypeArguments();
    if (args.isEmpty() || typeArgIndex >= args.size()) {
      return PathKind.FOCUS;
    }
    TypeMirror innerType = args.get(typeArgIndex);
    // Resolve wildcards
    TypeMirror resolved = ProcessorUtils.resolveWildcard(innerType);
    if (resolved == null) {
      return PathKind.FOCUS;
    }
    return getFieldPathKindRecursive(resolved, currentDepth + 1);
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

      if (isNavigableType(fieldType)) {
        // Direct navigable type (e.g., Address headquarters)
        TypeElement fieldTypeElement = getTypeElement(fieldType);
        if (fieldTypeElement == null) {
          continue;
        }

        PathKind fieldKind = getFieldPathKind(component, fieldType);
        PathKind widenedKind = currentPathKind.widen(fieldKind);

        TypeSpec navigatorClass =
            generateNavigatorClass(
                component, recordElement, fieldTypeElement, currentDepth, widenedKind);
        focusClassBuilder.addType(navigatorClass);

      } else {
        // Check if the field is an SPI container wrapping a navigable inner type
        // (e.g., Either<String, Address> where Address is navigable).
        // Skip types already handled by hardcoded OPTIONAL_TYPES/COLLECTION_TYPES
        // (e.g., Optional<Branch>, List<Leaf>) as they have their own widening.
        TraversableGenerator generator =
            isHardcodedWideningType(fieldType) ? null : findSpiGenerator(fieldType);
        if (generator != null && fieldType.getKind() == TypeKind.DECLARED) {
          DeclaredType declaredType = (DeclaredType) fieldType;
          int focusIdx = generator.getFocusTypeArgumentIndex();
          if (focusIdx < declaredType.getTypeArguments().size()) {
            TypeMirror innerType = declaredType.getTypeArguments().get(focusIdx);
            if (isNavigableType(innerType)) {
              TypeElement innerTypeElement = getTypeElement(innerType);
              if (innerTypeElement != null) {
                PathKind spiKind =
                    switch (generator.getCardinality()) {
                      case ZERO_OR_ONE -> PathKind.AFFINE;
                      case ZERO_OR_MORE -> PathKind.TRAVERSAL;
                    };
                PathKind widenedKind = currentPathKind.widen(spiKind);

                TypeSpec navigatorClass =
                    generateNavigatorClass(
                        component, recordElement, innerTypeElement, currentDepth, widenedKind);
                focusClassBuilder.addType(navigatorClass);
              }
            }
          }
        }
      }
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

  /** Returns the set of delegate method names for a given path kind. */
  private static Set<String> getDelegateMethodNames(PathKind pathKind) {
    return switch (pathKind) {
      case FOCUS -> Set.of("get", "set", "modify", "toLens", "toPath");
      case AFFINE -> Set.of("getOptional", "set", "modify", "matches", "toPath");
      case TRAVERSAL -> Set.of("getAll", "setAll", "modifyAll", "count", "isEmpty", "toPath");
    };
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
    Set<String> delegateNames = getDelegateMethodNames(currentPathKind);

    for (RecordComponentElement component : components) {
      String fieldName = component.getSimpleName().toString();

      // Skip fields that would collide with delegate method names
      if (delegateNames.contains(fieldName)) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.NOTE,
                "Navigator field '"
                    + fieldName
                    + "' in "
                    + targetRecord.getSimpleName()
                    + " collides with a delegate method name. "
                    + "Use .toPath().via("
                    + targetFocusClassName
                    + "."
                    + fieldName
                    + "().toLens()) as a workaround.",
                component);
        continue;
      }
      TypeMirror fieldType = component.asType();
      TypeName fieldTypeName = TypeName.get(fieldType).box();

      // Determine the path kind for this field and widen appropriately.
      // Pass the component to detect @Nullable annotations.
      PathKind fieldKind = getFieldPathKind(component, fieldType);
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

      if (fieldKind != PathKind.FOCUS) {
        // Widened field: the target Focus class's static method returns a widened path
        // (AffinePath for Optional/SPI ZERO_OR_ONE/@Nullable, TraversalPath for Collection/SPI
        // ZERO_OR_MORE)
        // or a navigator, so we can't call .toLens() on it.
        // Instead, construct an inline Lens for the container field and apply widening.
        generateWidenedNavigationMethod(
            methodBuilder,
            component,
            targetRecord,
            fieldName,
            fieldType,
            fieldKind,
            currentPathKind,
            widenedKind,
            sourceTypeVar,
            targetPackage,
            targetFocusClassName,
            currentDepth);
      } else {
        // Standard path: target Focus static method returns FocusPath, so .toLens() works.
        // buildViaStatement returns a CodeBlock using $T for the target Focus class,
        // ensuring JavaPoet generates proper imports even for cross-package references.
        final CodeBlock viaStatement =
            buildViaStatement(currentPathKind, widenedKind, fieldKind, targetFocusClass, fieldName);

        // Check if the field type is also navigable and we haven't exceeded depth
        if (currentDepth < maxDepth && isNavigableType(fieldType)) {
          TypeElement fieldTypeElement = getTypeElement(fieldType);
          if (fieldTypeElement != null) {
            // The navigator is a nested class of the target record's Focus class.
            // Use $T for the enclosing Focus class to ensure proper cross-package imports.
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
      }

      navigatorBuilder.addMethod(methodBuilder.build());
    }
  }

  /**
   * Generates a navigation method body for a widened field (fieldKind != FOCUS).
   *
   * <p>For widened fields, the target Focus class's static method returns a widened path type
   * (AffinePath, TraversalPath) or a navigator, so we cannot call {@code .toLens()} on it. Instead
   * we construct an inline {@code Lens.of(Record::field, setter)} and apply the appropriate
   * widening expression ({@code .some()}, {@code .each()}, {@code .nullable()}, etc.).
   *
   * <p>The method also extracts the inner type from the container to set the correct return type,
   * and optionally wraps the result in a navigator if the inner type is navigable.
   */
  private void generateWidenedNavigationMethod(
      MethodSpec.Builder methodBuilder,
      RecordComponentElement component,
      TypeElement targetRecord,
      String fieldName,
      TypeMirror fieldType,
      PathKind fieldKind,
      PathKind currentPathKind,
      PathKind widenedKind,
      TypeVariableName sourceTypeVar,
      String targetPackage,
      String targetFocusClassName,
      int currentDepth) {

    TypeName targetRecordTypeName = TypeName.get(targetRecord.asType());

    // Build constructor args for the inline Lens setter lambda
    String constructorArgs =
        targetRecord.getRecordComponents().stream()
            .map(
                c ->
                    c.getSimpleName().toString().equals(fieldName)
                        ? "newValue"
                        : "source." + c.getSimpleName() + "()")
            .collect(Collectors.joining(", "));

    // Compute the widening expression and collect any $T args for SPI optic expressions
    List<Object> wideningArgs = new ArrayList<>();
    String wideningExpr = buildPathWidening(component, fieldType, fieldKind, wideningArgs);

    // Determine the inner type that the widening produces.
    // For containers (Optional, List, SPI types), this is the element type inside.
    // For @Nullable, this is the field type itself.
    TypeName innerTypeName = extractInnerType(fieldType);
    if (innerTypeName == null) {
      // Fallback: no inner type extraction possible (shouldn't happen for widened types)
      innerTypeName = TypeName.get(fieldType).box();
    }

    // Set the correct return type using the inner type.
    // The widening methods (.some(), .each(), .nullable()) on the path types automatically
    // produce the correct wider path type, so no explicit .asTraversal()/.asAffine() is needed.
    ClassName pathClass = getPathClassName(widenedKind);
    ParameterizedTypeName innerReturnType =
        ParameterizedTypeName.get(pathClass, sourceTypeVar, innerTypeName);
    methodBuilder.returns(innerReturnType);

    // Check if the inner type is navigable and we should wrap in a navigator.
    // Only wrap for SPI ZERO_OR_MORE types, not hardcoded collection types (List, Set, Collection),
    // because generateNavigatorsWithPathKind only generates navigator classes for SPI containers
    // and directly navigable types, not for hardcoded collection types.
    boolean wrapInNavigator = false;
    ClassName navigatorFromTargetFocus = null;
    if (fieldKind == PathKind.TRAVERSAL && !isHardcodedWideningType(fieldType)) {
      // For SPI ZERO_OR_MORE TRAVERSAL fields, check if inner type is navigable
      TypeElement innerTypeElement = extractInnerTypeElement(fieldType);
      if (currentDepth < maxDepth
          && innerTypeElement != null
          && innerTypeElement.getAnnotation(GenerateFocus.class) != null) {
        wrapInNavigator = true;
        String innerNavigatorClassName = capitalise(fieldName) + "Navigator";
        navigatorFromTargetFocus =
            ClassName.get(targetPackage, targetFocusClassName, innerNavigatorClassName);
      }
    }

    // Build the statement: delegate.via(Lens.of(Record::field, setter)).widen().kindWiden()
    // Optionally wrapped in: new Navigator<>(...)
    if (wrapInNavigator) {
      List<Object> allArgs =
          new ArrayList<>(
              List.of(
                  navigatorFromTargetFocus,
                  Lens.class,
                  targetRecordTypeName,
                  fieldName,
                  targetRecordTypeName,
                  constructorArgs));
      allArgs.addAll(wideningArgs);
      methodBuilder.returns(ParameterizedTypeName.get(navigatorFromTargetFocus, sourceTypeVar));
      methodBuilder.addStatement(
          "return new $T<>(delegate.via($T.of($T::$L, (source, newValue) -> new $T($L)))"
              + wideningExpr
              + ")",
          allArgs.toArray());
    } else {
      List<Object> allArgs =
          new ArrayList<>(
              List.of(
                  Lens.class,
                  targetRecordTypeName,
                  fieldName,
                  targetRecordTypeName,
                  constructorArgs));
      allArgs.addAll(wideningArgs);
      methodBuilder.addStatement(
          "return delegate.via($T.of($T::$L, (source, newValue) -> new $T($L)))" + wideningExpr,
          allArgs.toArray());
    }
  }

  /**
   * Extracts the inner type from a container type.
   *
   * <p>For {@code Optional<T>}, {@code List<T>}, {@code Set<T>} returns T. For SPI types, returns
   * the focus type argument. Returns null if the type is not a recognised container.
   */
  private TypeName extractInnerType(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return null;
    }
    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    // Hardcoded Optional and Collection types use first type argument
    if (OPTIONAL_TYPES.contains(qualifiedName) || COLLECTION_TYPES.contains(qualifiedName)) {
      List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
      if (!typeArgs.isEmpty()) {
        TypeMirror resolved = ProcessorUtils.resolveWildcard(typeArgs.get(0));
        return resolved != null ? TypeName.get(resolved).box() : ClassName.get(Object.class);
      }
      return null;
    }

    // Check Collection subtypes
    for (TypeMirror iface : typeElement.getInterfaces()) {
      if (iface.getKind() == TypeKind.DECLARED) {
        TypeElement ifaceElement = (TypeElement) ((DeclaredType) iface).asElement();
        if (COLLECTION_TYPES.contains(ifaceElement.getQualifiedName().toString())) {
          List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
          if (!typeArgs.isEmpty()) {
            TypeMirror resolved = ProcessorUtils.resolveWildcard(typeArgs.get(0));
            return resolved != null ? TypeName.get(resolved).box() : ClassName.get(Object.class);
          }
        }
      }
    }

    // SPI types: use the focus type argument index
    TraversableGenerator generator = findSpiGenerator(type);
    if (generator != null) {
      int focusIdx = generator.getFocusTypeArgumentIndex();
      List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
      if (focusIdx < typeArgs.size()) {
        TypeMirror resolved = ProcessorUtils.resolveWildcard(typeArgs.get(focusIdx));
        return resolved != null ? TypeName.get(resolved).box() : ClassName.get(Object.class);
      }
    }

    return null;
  }

  /**
   * Extracts the inner TypeElement from a container type, for checking navigability.
   *
   * <p>Returns null if no inner type element can be determined.
   */
  private TypeElement extractInnerTypeElement(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return null;
    }
    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    TypeMirror innerType = null;

    // Hardcoded Optional and Collection types
    if (OPTIONAL_TYPES.contains(qualifiedName) || COLLECTION_TYPES.contains(qualifiedName)) {
      List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
      if (!typeArgs.isEmpty()) {
        innerType = ProcessorUtils.resolveWildcard(typeArgs.get(0));
      }
    }

    // SPI types
    if (innerType == null) {
      TraversableGenerator generator = findSpiGenerator(type);
      if (generator != null) {
        int focusIdx = generator.getFocusTypeArgumentIndex();
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (focusIdx < typeArgs.size()) {
          innerType = ProcessorUtils.resolveWildcard(typeArgs.get(focusIdx));
        }
      }
    }

    return innerType != null ? getTypeElement(innerType) : null;
  }

  /**
   * Builds the via statement for navigating from current path kind to the widened kind.
   *
   * <p>Returns a {@link CodeBlock} so that JavaPoet can properly resolve the {@code $T} reference
   * to {@code targetFocusClass}, ensuring correct import generation even when the Focus class is in
   * a different package.
   */
  private CodeBlock buildViaStatement(
      PathKind currentKind,
      PathKind widenedKind,
      PathKind fieldKind,
      ClassName targetFocusClass,
      String fieldName) {
    // Use $T so JavaPoet adds the import for targetFocusClass automatically
    CodeBlock baseVia = CodeBlock.of("delegate.via($T.$L().toLens())", targetFocusClass, fieldName);

    // When the field introduces widening (e.g., SPI types like Either→AFFINE, Map→TRAVERSAL),
    // the Focus static method still returns FocusPath (no widening in the static method), so
    // .toLens() works. We then convert the result to the correct path type.
    if (widenedKind == PathKind.TRAVERSAL && currentKind != PathKind.TRAVERSAL) {
      return CodeBlock.of("$L.asTraversal()", baseVia);
    }
    if (widenedKind == PathKind.AFFINE && currentKind == PathKind.FOCUS) {
      return CodeBlock.of("$L.asAffine()", baseVia);
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

  /**
   * Finds the highest-priority SPI generator that supports the given type. Emits a warning if
   * multiple generators with equal priority match.
   *
   * @param type the type to check
   * @return the matched generator, or null if none found
   */
  private TraversableGenerator findSpiGenerator(TypeMirror type) {
    TraversableGenerator matched = null;
    for (TraversableGenerator generator : traversableGenerators) {
      if (generator.supports(type)) {
        if (matched != null && matched.priority() == generator.priority()) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.WARNING,
                  "Multiple TraversableGenerator SPI providers with equal priority ("
                      + generator.priority()
                      + ") support type "
                      + type
                      + ": "
                      + matched.getClass().getName()
                      + " and "
                      + generator.getClass().getName()
                      + ". Using the first match.");
        } else if (matched == null) {
          matched = generator;
        }
      }
    }
    return matched;
  }

  /**
   * Checks if a type is handled by the hardcoded OPTIONAL_TYPES or COLLECTION_TYPES sets. These
   * types have their own widening mechanisms and should not be treated as SPI containers for
   * navigator generation.
   */
  private boolean isHardcodedWideningType(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();
    return OPTIONAL_TYPES.contains(qualifiedName) || COLLECTION_TYPES.contains(qualifiedName);
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

    // Check direct navigability first
    boolean directlyNavigable = false;
    TypeElement fieldTypeElement = getTypeElement(fieldType);
    if (fieldTypeElement != null && isNavigableType(fieldType)) {
      directlyNavigable = true;
    }

    // Check if field is an SPI container wrapping a navigable inner type
    // (e.g., Either<String, Address> where Address is navigable).
    // Skip types already handled by hardcoded OPTIONAL_TYPES/COLLECTION_TYPES
    // (Optional<Branch> fields are handled by createFocusPathMethod via .some() widening).
    boolean spiContainerNavigable = false;
    TraversableGenerator spiGenerator = null;
    TypeElement innerNavigableType = null;
    if (!directlyNavigable
        && fieldType.getKind() == TypeKind.DECLARED
        && !isHardcodedWideningType(fieldType)) {
      spiGenerator = findSpiGenerator(fieldType);
      if (spiGenerator != null) {
        DeclaredType declaredType = (DeclaredType) fieldType;
        int focusIdx = spiGenerator.getFocusTypeArgumentIndex();
        if (focusIdx < declaredType.getTypeArguments().size()) {
          TypeMirror innerType = declaredType.getTypeArguments().get(focusIdx);
          if (isNavigableType(innerType)) {
            innerNavigableType = getTypeElement(innerType);
            spiContainerNavigable = innerNavigableType != null;
          }
        }
      }
    }

    if (!directlyNavigable && !spiContainerNavigable) {
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

    // For javadoc, use the inner navigable type for SPI containers
    TypeName javadocTargetType =
        spiContainerNavigable ? TypeName.get(innerNavigableType.asType()) : componentTypeName;

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
                javadocTargetType,
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

    // Determine path kind to apply correct widening for @Nullable fields and SPI types
    PathKind fieldKind = getFieldPathKind(component, fieldType);
    List<Object> wideningArgs = new ArrayList<>();
    String pathWidening = buildPathWidening(component, fieldType, fieldKind, wideningArgs);

    // Generate: return new ComponentNavigator<>(FocusPath.of(Lens.of(...))widening);
    if (wideningArgs.isEmpty()) {
      // Non-SPI case: pathWidening is a plain string, use $L
      methodBuilder.addStatement(
          "return new $L<>($T.of($T.of($T::$L, (source, newValue) -> new $T($L)))$L)",
          navigatorClassName,
          FOCUS_PATH_CLASS,
          Lens.class,
          recordTypeName,
          componentName,
          recordTypeName,
          constructorArgs,
          pathWidening);
    } else {
      // SPI case: pathWidening contains $T placeholders, embed in format string
      List<Object> args =
          new ArrayList<>(
              List.of(
                  navigatorClassName,
                  FOCUS_PATH_CLASS,
                  Lens.class,
                  recordTypeName,
                  componentName,
                  recordTypeName,
                  constructorArgs));
      args.addAll(wideningArgs);
      methodBuilder.addStatement(
          "return new $L<>($T.of($T.of($T::$L, (source, newValue) -> new $T($L)))"
              + pathWidening
              + ")",
          args.toArray());
    }

    return methodBuilder.build();
  }

  /**
   * Builds the path widening expression for a field. Uses SPI optic expressions when available,
   * otherwise falls back to the standard widening methods.
   *
   * <p>The returned string may contain {@code $T} placeholders for types from SPI generators. The
   * corresponding {@link ClassName} objects are appended to {@code wideningArgs}.
   */
  private String buildPathWidening(
      RecordComponentElement component,
      TypeMirror fieldType,
      PathKind fieldKind,
      List<Object> wideningArgs) {
    if (fieldKind == PathKind.FOCUS) {
      return "";
    }

    // Check if @Nullable annotation drives the widening
    if (component != null && NullableAnnotations.hasNullableAnnotation(component)) {
      return ".nullable()";
    }

    // Check hardcoded Optional types
    if (fieldType.getKind() == TypeKind.DECLARED) {
      DeclaredType declaredType = (DeclaredType) fieldType;
      TypeElement typeElement = (TypeElement) declaredType.asElement();
      String qualifiedName = typeElement.getQualifiedName().toString();
      if (OPTIONAL_TYPES.contains(qualifiedName)) {
        return ".some()";
      }
      if (COLLECTION_TYPES.contains(qualifiedName)) {
        return ".each()";
      }
    }

    // Consult SPI generators for the optic expression
    for (TraversableGenerator generator : traversableGenerators) {
      if (generator.supports(fieldType)) {
        String opticExpr = generator.generateOpticExpression();
        if (!opticExpr.isEmpty()) {
          String resolvedExpr =
              OpticExpressionResolver.resolve(
                  opticExpr, generator.getRequiredImports(), wideningArgs);
          return switch (generator.getCardinality()) {
            case ZERO_OR_ONE -> ".some(" + resolvedExpr + ")";
            case ZERO_OR_MORE -> ".each(" + resolvedExpr + ")";
          };
        }
        // Fallback to simple widening if no optic expression
        return switch (generator.getCardinality()) {
          case ZERO_OR_ONE -> ".nullable()";
          case ZERO_OR_MORE -> ".each()";
        };
      }
    }

    // Fallback
    return switch (fieldKind) {
      case AFFINE -> ".nullable()";
      case TRAVERSAL -> ".each()";
      default -> "";
    };
  }
}
