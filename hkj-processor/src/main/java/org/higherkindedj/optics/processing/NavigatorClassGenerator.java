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
   * @param component the record component the field belongs to
   * @param type the field type to analyse
   * @return AFFINE for optional/nullable types, TRAVERSAL for collection types, FOCUS otherwise
   */
  private PathKind getFieldPathKind(RecordComponentElement component, TypeMirror type) {
    PathKind kind = getFieldPathKindRecursive(type, 0);
    // A container decides the path kind on its own; @Nullable only widens a field that would
    // otherwise be a plain focus, which is the same precedence the static Focus methods use.
    if (kind == PathKind.FOCUS && NullableAnnotations.hasNullableAnnotation(component)) {
      return PathKind.AFFINE;
    }
    return kind;
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

    // Check for subtypes of Collection. A superinterface is always a declared type, error types
    // included, so the element is read without a kind check.
    for (TypeMirror iface : typeElement.getInterfaces()) {
      TypeElement ifaceElement = (TypeElement) ((DeclaredType) iface).asElement();
      if (COLLECTION_TYPES.contains(ifaceElement.getQualifiedName().toString())) {
        PathKind innerKind = getInnerPathKind(declaredType, 0, depth);
        return PathKind.TRAVERSAL.widen(innerKind);
      }
    }

    // Consult TraversableGenerator SPI for additional container types.
    TraversableGenerator matched = wideningGenerator(type);
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

  /**
   * Generates navigator inner classes with path kind tracking.
   *
   * <p>Depth limiting is handled by the navigation-method generation (via {@code currentDepth +
   * 1}); this method is only entered at the root level.
   */
  private void generateNavigatorsWithPathKind(
      TypeSpec.Builder focusClassBuilder,
      TypeElement recordElement,
      int currentDepth,
      PathKind currentPathKind) {

    List<? extends RecordComponentElement> components = recordElement.getRecordComponents();

    for (RecordComponentElement component : components) {
      if (!shouldGenerateNavigator(component)) {
        continue;
      }

      TypeMirror fieldType = component.asType();

      TypeElement fieldTypeElement = navigableTypeElement(fieldType);
      if (fieldTypeElement != null) {
        // Direct navigable type (e.g., Address headquarters)
        PathKind fieldKind = getFieldPathKind(component, fieldType);
        PathKind widenedKind = currentPathKind.widen(fieldKind);

        TypeSpec navigatorClass =
            generateNavigatorClass(
                component, recordElement, fieldTypeElement, currentDepth, widenedKind);
        focusClassBuilder.addType(navigatorClass);

      } else {
        // Otherwise the field may be an SPI container wrapping a navigable inner type
        // (e.g., Either<String, Address> where Address is navigable).
        SpiNavigable spiNavigable = spiNavigable(fieldType);
        if (spiNavigable != null) {
          PathKind spiKind =
              switch (spiNavigable.generator().getCardinality()) {
                case ZERO_OR_ONE -> PathKind.AFFINE;
                case ZERO_OR_MORE -> PathKind.TRAVERSAL;
              };
          PathKind widenedKind = currentPathKind.widen(spiKind);

          TypeSpec navigatorClass =
              generateNavigatorClass(
                  component, recordElement, spiNavigable.element(), currentDepth, widenedKind);
          focusClassBuilder.addType(navigatorClass);
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
    String navigatorClassName = ProcessorUtils.capitalise(componentName) + "Navigator";
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

  /** Adds the delegate methods one path kind's navigator forwards to its underlying path. */
  @FunctionalInterface
  private interface DelegateMethods {
    void addTo(
        TypeSpec.Builder navigatorBuilder,
        TypeVariableName sourceTypeVar,
        TypeName targetTypeName,
        ParameterizedTypeName delegateType);
  }

  /** Adds delegate methods that forward to the underlying path. */
  private void addDelegateMethods(
      TypeSpec.Builder navigatorBuilder,
      TypeVariableName sourceTypeVar,
      TypeName targetTypeName,
      ParameterizedTypeName delegateType,
      PathKind pathKind) {

    // Selected as a value rather than dispatched to as a statement: the three kinds are the whole
    // enum, and a switch expression says so without a default arm nothing can reach.
    DelegateMethods delegates =
        switch (pathKind) {
          case FOCUS -> this::addFocusPathDelegateMethods;
          case AFFINE -> this::addAffinePathDelegateMethods;
          case TRAVERSAL -> this::addTraversalPathDelegateMethods;
        };
    delegates.addTo(navigatorBuilder, sourceTypeVar, targetTypeName, delegateType);
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

        // Check if the field type is also navigable and we haven't exceeded depth.
        // Navigable types are always declared types, so no TypeElement re-check is needed.
        if (currentDepth < maxDepth && isNavigableType(fieldType)) {
          // The navigator is a nested class of the target record's Focus class.
          // Use $T for the enclosing Focus class to ensure proper cross-package imports.
          String fieldNavigatorClassName = ProcessorUtils.capitalise(fieldName) + "Navigator";
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

    // Compute the widening chain and collect any $T args for SPI optic expressions
    List<Object> wideningArgs = new ArrayList<>();
    Widening widening = widening(fieldType, fieldKind, wideningArgs);
    String wideningExpr = widening.expression();
    TypeName innerTypeName = widening.focusType();

    // Set the correct return type using the inner type.
    // The widening methods (.some(), .each(), .nullable()) on the path types automatically
    // produce the correct wider path type, so no explicit .asTraversal()/.asAffine() is needed.
    ClassName pathClass = getPathClassName(widenedKind);
    ParameterizedTypeName innerReturnType =
        ParameterizedTypeName.get(pathClass, sourceTypeVar, innerTypeName);
    methodBuilder.returns(innerReturnType);

    // An SPI ZERO_OR_MORE field whose element type is navigable is wrapped in that element's
    // navigator. A hardcoded collection (List, Set, Collection) is not, because
    // generateNavigatorsWithPathKind only emits navigator classes for SPI containers and for
    // directly navigable fields.
    boolean wrapInNavigator = false;
    ClassName navigatorFromTargetFocus = null;
    if (fieldKind == PathKind.TRAVERSAL
        && currentDepth < maxDepth
        && spiNavigable(fieldType) != null) {
      wrapInNavigator = true;
      String innerNavigatorClassName = ProcessorUtils.capitalise(fieldName) + "Navigator";
      navigatorFromTargetFocus =
          ClassName.get(targetPackage, targetFocusClassName, innerNavigatorClassName);
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

  /** An SPI container's generator, paired with the navigable element it focuses on. */
  private record SpiNavigable(TraversableGenerator generator, TypeElement element) {}

  /**
   * Returns the generator and navigable element of an SPI container focused on a navigable type, or
   * {@code null} when the field is not one.
   *
   * <p>Every site that asks reads the answer from here, so the navigator class, the method's return
   * type and the wrapping decision cannot disagree about which fields have one. Hardcoded
   * Optional/Collection fields are excluded because they widen through their own path and never get
   * a navigator class.
   */
  private SpiNavigable spiNavigable(TypeMirror fieldType) {
    if (fieldType.getKind() != TypeKind.DECLARED || isHardcodedWideningType(fieldType)) {
      return null;
    }
    return spiNavigableUnder(fieldType, wideningGenerator(fieldType));
  }

  /**
   * The navigable element a declared, non-hardcoded container focuses on under {@code generator},
   * or {@code null} when there is none.
   *
   * <p>Split from {@link #spiNavigable} so that {@link #widensUndenotableSpiContainer} can ask the
   * same question of a generator the widening guard has already turned away.
   */
  private SpiNavigable spiNavigableUnder(TypeMirror fieldType, TraversableGenerator generator) {
    if (generator == null) {
      // A Collection subtype such as ArrayList is widened by the interface walk rather than by a
      // generator, so there is no focus type argument to read.
      return null;
    }
    List<? extends TypeMirror> typeArgs = ((DeclaredType) fieldType).getTypeArguments();
    int focusIdx = generator.getFocusTypeArgumentIndex();
    if (focusIdx >= typeArgs.size()) {
      return null; // a raw container carries no type argument
    }
    // The argument is resolved first: `Map<String, ? extends Address>` focuses on Address, and
    // an unbounded or super-bounded wildcard resolves to no type at all.
    TypeMirror innerType = ProcessorUtils.resolveWildcard(typeArgs.get(focusIdx));
    TypeElement element = innerType == null ? null : navigableTypeElement(innerType);
    return element == null ? null : new SpiNavigable(generator, element);
  }

  /**
   * Builds the via statement for navigating from current path kind to the widened kind.
   *
   * <p>Returns a {@link CodeBlock} so that JavaPoet can properly resolve the {@code $T} reference
   * to {@code targetFocusClass}, ensuring correct import generation even when the Focus class is in
   * a different package.
   */
  // Package-private for tests.
  CodeBlock buildViaStatement(
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
  boolean isNavigableType(TypeMirror type) {
    return navigableTypeElement(type) != null;
  }

  /**
   * Returns the element of a navigable type, or {@code null} when the type is not navigable.
   *
   * <p>Navigability and the element are answered together because they are never useful apart: only
   * a declared type can be navigable, so a caller holding a navigable type already holds its
   * element.
   */
  private TypeElement navigableTypeElement(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return null;
    }
    TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
    boolean navigable =
        navigableTypes.contains(typeElement.getQualifiedName().toString())
            || typeElement.getAnnotation(GenerateFocus.class) != null;
    return navigable ? typeElement : null;
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
   * The generator that widens {@code type}, or {@code null} when none matches or the widening it
   * would emit cannot be written.
   *
   * <p>Every widening site reads its generator from here, so the path kind, the navigator class and
   * the composition call cannot disagree about which containers widen.
   */
  private TraversableGenerator wideningGenerator(TypeMirror type) {
    TraversableGenerator matched = findSpiGenerator(type);
    return matched != null && widensUndenotably(matched, type) ? null : matched;
  }

  /**
   * Whether {@code generator} cannot write the widening it would emit for {@code type}.
   *
   * <p>A generator that names an optic instance — {@code .some(Affines.eitherRight())}, {@code
   * .each(EachInstances.mapValuesEach())} — has that instance's type arguments inferred from the
   * field type, which a raw or wildcard-carrying container gives javac no way to do. A generator
   * with no optic expression widens through {@code .nullable()} or {@code .each()} instead, whose
   * free type variable takes either without complaint.
   *
   * <p>Such a container is left un-widened here, and the declaration is rejected by {@code
   * FocusProcessor}, which sees the component it is written on.
   */
  private static boolean widensUndenotably(TraversableGenerator generator, TypeMirror type) {
    return !generator.generateOpticExpression().isEmpty()
        && ProcessorUtils.hasUndenotableTypeArguments(type);
  }

  /**
   * Whether a navigator for {@code component} would widen through an SPI container whose type
   * arguments leave the optic instance undenotable.
   *
   * <p>Answered here because the navigator generator decides which fields get a navigator; the
   * diagnostic is reported by {@code FocusProcessor}, which sees each component once.
   *
   * @param component the record component to inspect
   * @return true when only the type arguments stand between this component and a navigator
   */
  boolean widensUndenotableSpiContainer(RecordComponentElement component) {
    TypeMirror fieldType = component.asType();
    // The guards run in the order the navigator itself decides: a filtered-out or directly
    // navigable field never reaches the SPI question, and Optional and the collections widen
    // through their own path.
    if (!shouldGenerateNavigator(component)
        || fieldType.getKind() != TypeKind.DECLARED
        || navigableTypeElement(fieldType) != null
        || isHardcodedWideningType(fieldType)) {
      return false;
    }
    TraversableGenerator generator = findSpiGenerator(fieldType);
    return generator != null
        && widensUndenotably(generator, fieldType)
        && spiNavigableUnder(fieldType, generator) != null;
  }

  /**
   * Checks if a declared type is handled by the hardcoded OPTIONAL_TYPES or COLLECTION_TYPES sets.
   * These types have their own widening mechanisms and should not be treated as SPI containers for
   * navigator generation. The caller has established that the type is declared.
   */
  private boolean isHardcodedWideningType(TypeMirror type) {
    TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();
    return OPTIONAL_TYPES.contains(qualifiedName) || COLLECTION_TYPES.contains(qualifiedName);
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
    TypeElement fieldTypeElement = navigableTypeElement(fieldType);
    boolean directlyNavigable = fieldTypeElement != null;

    // Otherwise the field may be an SPI container wrapping a navigable inner type
    // (e.g., Either<String, Address> where Address is navigable). Hardcoded Optional/Collection
    // fields are excluded: createFocusPathMethod widens those through .some()/.each() instead.
    SpiNavigable spiNavigable = directlyNavigable ? null : spiNavigable(fieldType);
    boolean spiContainerNavigable = spiNavigable != null;
    TraversableGenerator spiGenerator = spiContainerNavigable ? spiNavigable.generator() : null;
    TypeElement innerNavigableType = spiContainerNavigable ? spiNavigable.element() : null;

    if (!directlyNavigable && !spiContainerNavigable) {
      return null; // Not navigable, use standard method
    }

    // Navigator class name
    String navigatorClassName = ProcessorUtils.capitalise(componentName) + "Navigator";
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
    String pathWidening = widening(fieldType, fieldKind, wideningArgs).expression();

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

  /** The widening a navigator method applies, and the type the widened path focuses on. */
  private record Widening(String expression, TypeName focusType) {}

  /** One peeled container layer: how the path widens, and the type argument it then focuses on. */
  private record Layer(String expression, PathKind kind, TypeMirror argument) {}

  /**
   * Builds the widening a field needs, together with the type the widened path focuses on.
   *
   * <p>The expression may contain {@code $T} placeholders for types from SPI generators. The
   * corresponding {@link ClassName} objects are appended to {@code wideningArgs}.
   */
  private Widening widening(TypeMirror fieldType, PathKind fieldKind, List<Object> wideningArgs) {
    TypeName fieldTypeName = TypeName.get(fieldType).box();
    if (fieldKind == PathKind.FOCUS) {
      return new Widening("", fieldTypeName);
    }

    // The caller's kind comes from getFieldPathKind, so a field the container and SPI analysis
    // leaves as a plain focus can only have been widened by @Nullable, which focuses the field
    // itself. Everything past this point was widened by that analysis, and only a declared type
    // ever is.
    if (getFieldPathKindRecursive(fieldType, 0) == PathKind.FOCUS) {
      return new Widening(".nullable()", fieldTypeName);
    }

    // Peel one container layer at a time until the chain reaches the kind the method declares.
    // Optional<List<String>> declares a TraversalPath, so .some() on its own would hand back an
    // AffinePath: it takes .some().each() to arrive, focusing String. The walk mirrors the fold
    // in getFieldPathKindRecursive layer for layer, so it always reaches that kind, and a layer
    // with no type argument to descend into ends it in any case.
    StringBuilder expression = new StringBuilder();
    TypeMirror current = fieldType;
    PathKind reached = PathKind.FOCUS;
    while (true) {
      Layer layer = peelLayer(current, wideningArgs);
      expression.append(layer.expression());
      reached = reached.widen(layer.kind());
      if (layer.argument() == null) {
        // A raw container has no argument, so the field type itself is the focus.
        return new Widening(expression.toString(), fieldTypeName);
      }
      TypeMirror resolved = ProcessorUtils.resolveWildcard(layer.argument());
      if (resolved == null) {
        // An unbounded or super-bounded wildcard focuses Object.
        return new Widening(expression.toString(), ClassName.get(Object.class));
      }
      if (reached == fieldKind) {
        return new Widening(expression.toString(), TypeName.get(resolved).box());
      }
      current = resolved;
    }
  }

  /**
   * Peels one container layer off a type the widening analysis has already recognised.
   *
   * <p>A generator is consulted before the Collection-subtype walk, which is the one place this
   * order differs from {@link #getFieldPathKindRecursive}: a Guava {@code ImmutableList} implements
   * {@code java.util.List}, so the walk would widen it with a bare {@code .each()} and lose the
   * generator's copying optic. Both routes report TRAVERSAL over the same type argument, so the
   * kind the two agree on is unaffected.
   */
  private Layer peelLayer(TypeMirror type, List<Object> wideningArgs) {
    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();
    if (OPTIONAL_TYPES.contains(qualifiedName)) {
      return new Layer(".some()", PathKind.AFFINE, typeArgument(declaredType, 0));
    }
    if (COLLECTION_TYPES.contains(qualifiedName)) {
      return new Layer(".each()", PathKind.TRAVERSAL, typeArgument(declaredType, 0));
    }

    TraversableGenerator generator = wideningGenerator(type);
    if (generator == null) {
      // No generator, so the analysis recognised this layer through the Collection-subtype walk.
      return new Layer(".each()", PathKind.TRAVERSAL, typeArgument(declaredType, 0));
    }
    PathKind kind =
        switch (generator.getCardinality()) {
          case ZERO_OR_ONE -> PathKind.AFFINE;
          case ZERO_OR_MORE -> PathKind.TRAVERSAL;
        };
    String opticExpr = generator.generateOpticExpression();
    String expression;
    if (opticExpr.isEmpty()) {
      // A generator with no optic expression widens through the built-in methods.
      expression =
          switch (generator.getCardinality()) {
            case ZERO_OR_ONE -> ".nullable()";
            case ZERO_OR_MORE -> ".each()";
          };
    } else {
      String resolvedExpr =
          OpticExpressionResolver.resolve(opticExpr, generator.getRequiredImports(), wideningArgs);
      expression =
          switch (generator.getCardinality()) {
            case ZERO_OR_ONE -> ".some(" + resolvedExpr + ")";
            case ZERO_OR_MORE -> ".each(" + resolvedExpr + ")";
          };
    }
    return new Layer(
        expression, kind, typeArgument(declaredType, generator.getFocusTypeArgumentIndex()));
  }

  /** The type argument at the index, or {@code null} when the container is raw. */
  private static TypeMirror typeArgument(DeclaredType declaredType, int index) {
    List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
    return index < typeArgs.size() ? typeArgs.get(index) : null;
  }
}
