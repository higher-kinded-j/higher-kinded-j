// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
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
import org.higherkindedj.optics.processing.kind.KindFieldAnalyser;
import org.higherkindedj.optics.processing.kind.KindFieldInfo;
import org.higherkindedj.optics.processing.spi.Cardinality;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.OpticExpressionResolver;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Annotation processor for {@link GenerateFocus} that generates Focus DSL utility classes.
 *
 * <p>For each annotated record, this processor generates a companion class with the suffix "Focus"
 * containing static methods that return {@code FocusPath} instances for each record component.
 *
 * <h2>Supported Field Types</h2>
 *
 * <p>The processor automatically handles various field types with appropriate path widening:
 *
 * <ul>
 *   <li><b>Standard fields</b> - Generate {@code FocusPath}
 *   <li><b>Optional/Maybe fields</b> - Generate {@code AffinePath} via {@code .some()}
 *   <li><b>Collection fields</b> (List, Set) - Generate {@code TraversalPath} via {@code .each()}
 *   <li><b>Kind&lt;F, A&gt; fields</b> - Generate appropriate path via {@code .traverseOver()}
 * </ul>
 *
 * <h2>Kind Field Support</h2>
 *
 * <p>The processor recognises {@code Kind<F, A>} fields from the Higher-Kinded-J library and
 * automatically generates traversal code. For custom Kind types, use the {@link
 * org.higherkindedj.optics.annotations.TraverseField} annotation.
 *
 * <h2>Generated Code Structure</h2>
 *
 * <p>For a record like:
 *
 * <pre>{@code
 * @GenerateFocus
 * record Team(String name, Kind<ListKind.Witness, Member> members) {}
 * }</pre>
 *
 * <p>The processor generates:
 *
 * <pre>{@code
 * @Generated
 * public final class TeamFocus {
 *     private TeamFocus() {}
 *
 *     public static FocusPath<Team, String> name() {
 *         return FocusPath.of(Lens.of(...));
 *     }
 *
 *     public static TraversalPath<Team, Member> members() {
 *         return FocusPath.of(Lens.of(...))
 *             .<ListKind.Witness, Member>traverseOver(ListTraverse.INSTANCE);
 *     }
 * }
 * }</pre>
 *
 * @see org.higherkindedj.optics.annotations.TraverseField
 * @see org.higherkindedj.optics.annotations.KindSemantics
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateFocus")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class FocusProcessor extends AbstractProcessor {

  /** Creates a new FocusProcessor. */
  public FocusProcessor() {}

  private final List<TraversableGenerator> traversableGenerators = new ArrayList<>();

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    ServiceLoader.load(TraversableGenerator.class, getClass().getClassLoader())
        .forEach(traversableGenerators::add);
    // Sort by priority descending so highest-priority generators are checked first
    traversableGenerators.sort(Comparator.comparingInt(TraversableGenerator::priority).reversed());
  }

  /** ClassName for FocusPath (in hkj-core, not available at processor compile time). */
  private static final ClassName FOCUS_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "FocusPath");

  /** ClassName for AffinePath (for Optional field widening). */
  private static final ClassName AFFINE_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "AffinePath");

  /** ClassName for TraversalPath (for collection field widening). */
  private static final ClassName TRAVERSAL_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "TraversalPath");

  /** Optional types that widen to AffinePath via .some(). */
  private static final Set<String> OPTIONAL_TYPES =
      Set.of("java.util.Optional", "org.higherkindedj.hkt.maybe.Maybe");

  /** Collection types that widen to TraversalPath via .each(). */
  private static final Set<String> COLLECTION_TYPES =
      Set.of("java.util.List", "java.util.Set", "java.util.Collection");

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // First pass: collect all @GenerateFocus annotated types for navigator resolution
    Set<String> navigableTypes = new HashSet<>();
    Set<? extends Element> allAnnotated = roundEnv.getElementsAnnotatedWith(GenerateFocus.class);
    for (Element element : allAnnotated) {
      if (element.getKind() == ElementKind.RECORD) {
        TypeElement typeElement = (TypeElement) element;
        navigableTypes.add(typeElement.getQualifiedName().toString());
      }
    }

    // Second pass: generate Focus classes
    for (TypeElement annotation : annotations) {
      Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
      for (Element element : annotatedElements) {
        if (element.getKind() != ElementKind.RECORD) {
          error("The @GenerateFocus annotation can only be applied to records.", element);
          continue;
        }
        try {
          generateFocusFile((TypeElement) element, navigableTypes);
        } catch (IOException e) {
          error("Could not generate focus file: " + e.getMessage(), element);
        }
      }
    }
    return true;
  }

  private void generateFocusFile(TypeElement recordElement, Set<String> navigableTypes)
      throws IOException {
    String recordName = recordElement.getSimpleName().toString();
    String defaultPackage =
        processingEnv.getElementUtils().getPackageOf(recordElement).getQualifiedName().toString();

    // Check for annotation attributes
    GenerateFocus annotation = recordElement.getAnnotation(GenerateFocus.class);
    String targetPackage = annotation.targetPackage();
    String packageName = targetPackage.isEmpty() ? defaultPackage : targetPackage;
    boolean generateNavigators = annotation.generateNavigators();
    boolean widenCollections = annotation.widenCollections();
    int maxNavigatorDepth = annotation.maxNavigatorDepth();
    String[] includeFields = annotation.includeFields();
    String[] excludeFields = annotation.excludeFields();

    String focusClassName = recordName + "Focus";

    final ClassName generatedAnnotation =
        ClassName.get("org.higherkindedj.optics.annotations", "Generated");

    String navigatorNote =
        generateNavigators
            ? "\n\n<p>This class includes navigator classes for fluent cross-type navigation."
            : "";

    TypeSpec.Builder focusClassBuilder =
        TypeSpec.classBuilder(focusClassName)
            .addAnnotation(generatedAnnotation)
            .addJavadoc(
                "Generated Focus DSL paths for {@link $T}.\n\n"
                    + "<p>This class provides type-safe navigation paths for accessing and modifying\n"
                    + "fields within {@code $L} instances using the Focus DSL.$L\n\n"
                    + "<p>Do not edit this file; it is automatically generated.",
                ClassName.get(recordElement),
                recordName,
                navigatorNote)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    List<? extends RecordComponentElement> components = recordElement.getRecordComponents();
    TypeName recordTypeName = getParameterizedTypeName(recordElement);

    // Create navigator generator if enabled
    NavigatorClassGenerator navigatorGenerator = null;
    if (generateNavigators) {
      navigatorGenerator =
          new NavigatorClassGenerator(
              processingEnv,
              navigableTypes,
              maxNavigatorDepth,
              includeFields,
              excludeFields,
              traversableGenerators);
    }

    // Generate FocusPath methods for each component
    for (RecordComponentElement component : components) {
      MethodSpec method = null;

      // Try to create a navigator method if navigators are enabled
      if (navigatorGenerator != null) {
        method =
            navigatorGenerator.createNavigatorMethod(
                component, recordElement, components, recordTypeName);
      }

      // Fall back to standard FocusPath method if no navigator method was created
      if (method == null) {
        method =
            createFocusPathMethod(
                component, recordElement, components, recordTypeName, widenCollections);
      }

      focusClassBuilder.addMethod(method);
    }

    // Generate navigator inner classes if enabled
    if (navigatorGenerator != null) {
      navigatorGenerator.generateNavigators(focusClassBuilder, recordElement, 0);
    }

    JavaFile javaFile =
        JavaFile.builder(packageName, focusClassBuilder.build())
            .addFileComment("Generated by hkj-optics-processor. Do not edit.")
            .build();

    javaFile.writeTo(processingEnv.getFiler());
  }

  private TypeName getParameterizedTypeName(TypeElement typeElement) {
    List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return ClassName.get(typeElement);
    } else {
      List<TypeVariableName> typeVars = typeParameters.stream().map(TypeVariableName::get).toList();
      return ParameterizedTypeName.get(
          ClassName.get(typeElement), typeVars.toArray(new TypeName[0]));
    }
  }

  private MethodSpec createFocusPathMethod(
      RecordComponentElement component,
      TypeElement recordElement,
      List<? extends RecordComponentElement> allComponents,
      TypeName recordTypeName,
      boolean widenCollections) {

    String componentName = component.getSimpleName().toString();
    TypeMirror componentType = component.asType();
    TypeName componentTypeName = TypeName.get(componentType);

    // Detect path type widening based on field type and annotations
    PathTypeInfo pathTypeInfo = analyseFieldType(component, componentType, widenCollections);

    // Determine return type and inner type based on widening
    ClassName pathClass = pathTypeInfo.pathClass;
    TypeName innerTypeName =
        pathTypeInfo.innerType != null ? pathTypeInfo.innerType : componentTypeName.box();
    ParameterizedTypeName returnTypeName =
        ParameterizedTypeName.get(pathClass, recordTypeName, innerTypeName);

    String pathDescription = getPathDescription(pathTypeInfo);
    String getMethodName = getPathGetMethod(pathTypeInfo);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(componentName)
            .addJavadoc(
                "Creates a {@link $T} for the {@code $L} field of a {@link $T}.\n\n"
                    + "<p>The returned path can be composed with other optics for deep navigation:\n"
                    + "<pre>{@code\n"
                    + "$L.$L().$L(instance);  // Get the $L value\n"
                    + "$L.$L().set(newValue, instance);  // Set the $L value\n"
                    + "$L.$L().modify(fn, instance);  // Transform the $L value\n"
                    + "}</pre>\n\n"
                    + "@return A non-null {@code $L<$T, $T>}.",
                pathClass,
                componentName,
                recordTypeName,
                recordElement.getSimpleName() + "Focus",
                componentName,
                getMethodName,
                componentName,
                recordElement.getSimpleName() + "Focus",
                componentName,
                componentName,
                recordElement.getSimpleName() + "Focus",
                componentName,
                componentName,
                pathDescription,
                recordTypeName,
                innerTypeName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(returnTypeName);

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

    // Generate code based on path type widening
    String baseLens =
        String.format(
            "$T.of($T.of($T::%s, (source, newValue) -> new $T(%s)))",
            componentName, constructorArgs);

    switch (pathTypeInfo.wideningType) {
      case OPTIONAL ->
          methodBuilder.addStatement(
              "return " + baseLens + ".some()",
              FOCUS_PATH_CLASS,
              Lens.class,
              recordTypeName,
              recordTypeName);
      case COLLECTION ->
          methodBuilder.addStatement(
              "return " + baseLens + ".each()",
              FOCUS_PATH_CLASS,
              Lens.class,
              recordTypeName,
              recordTypeName);
      case NULLABLE ->
          methodBuilder.addStatement(
              "return " + baseLens + ".nullable()",
              FOCUS_PATH_CLASS,
              Lens.class,
              recordTypeName,
              recordTypeName);
      case KIND_EXACTLY_ONE -> {
        // Kind with exactly-one semantics (e.g., IdKind): AffinePath via
        // traverseOver().headOption()
        // Note: Although IdKind always contains exactly one element, traverseOver() returns
        // TraversalPath, so we narrow via headOption() to get AffinePath. This is type-safe.
        KindFieldInfo kindInfo = pathTypeInfo.kindInfo();
        String traverseOverCall = buildTraverseOverCall(kindInfo);
        methodBuilder.addStatement(
            "return " + baseLens + traverseOverCall + ".headOption()",
            FOCUS_PATH_CLASS,
            Lens.class,
            recordTypeName,
            recordTypeName);
      }
      case KIND_ZERO_OR_ONE -> {
        // Kind with zero-or-one semantics: AffinePath via traverseOver().headOption()
        KindFieldInfo kindInfo = pathTypeInfo.kindInfo();
        String traverseOverCall = buildTraverseOverCall(kindInfo);
        methodBuilder.addStatement(
            "return " + baseLens + traverseOverCall + ".headOption()",
            FOCUS_PATH_CLASS,
            Lens.class,
            recordTypeName,
            recordTypeName);
      }
      case KIND_ZERO_OR_MORE -> {
        // Kind with zero-or-more semantics: TraversalPath via traverseOver()
        KindFieldInfo kindInfo = pathTypeInfo.kindInfo();
        String traverseOverCall = buildTraverseOverCall(kindInfo);
        methodBuilder.addStatement(
            "return " + baseLens + traverseOverCall,
            FOCUS_PATH_CLASS,
            Lens.class,
            recordTypeName,
            recordTypeName);
      }
      case SPI_ZERO_OR_ONE -> {
        // SPI-registered zero-or-one type: AffinePath via .some(opticExpr)
        TraversableGenerator gen = pathTypeInfo.spiGenerator();
        String opticExpr = gen.generateOpticExpression();
        List<Object> args =
            new ArrayList<>(List.of(FOCUS_PATH_CLASS, Lens.class, recordTypeName, recordTypeName));
        String resolvedExpr =
            OpticExpressionResolver.resolve(opticExpr, gen.getRequiredImports(), args);
        methodBuilder.addStatement(
            "return " + baseLens + ".some(" + resolvedExpr + ")", args.toArray());
      }
      case SPI_ZERO_OR_MORE -> {
        // SPI-registered zero-or-more type: TraversalPath via .each(opticExpr)
        TraversableGenerator gen = pathTypeInfo.spiGenerator();
        String opticExpr = gen.generateOpticExpression();
        List<Object> args =
            new ArrayList<>(List.of(FOCUS_PATH_CLASS, Lens.class, recordTypeName, recordTypeName));
        String resolvedExpr =
            OpticExpressionResolver.resolve(opticExpr, gen.getRequiredImports(), args);
        methodBuilder.addStatement(
            "return " + baseLens + ".each(" + resolvedExpr + ")", args.toArray());
      }
      default ->
          methodBuilder.addStatement(
              "return " + baseLens, FOCUS_PATH_CLASS, Lens.class, recordTypeName, recordTypeName);
    }

    return methodBuilder.build();
  }

  /**
   * Builds the traverseOver() method call string for a Kind field.
   *
   * @param kindInfo the Kind field information
   * @return the traverseOver call string including type parameters
   */
  private String buildTraverseOverCall(KindFieldInfo kindInfo) {
    // Build witness type with any type arguments
    String witnessType = kindInfo.witnessType();
    if (kindInfo.isParameterised() && !kindInfo.witnessTypeArgs().isEmpty()) {
      witnessType = witnessType + "<" + kindInfo.witnessTypeArgs() + ">";
    }

    // Get element type as string
    String elementType = kindInfo.elementType().toString();

    // Build the traverseOver call with explicit type parameters for Java's type inference
    return String.format(
        ".<%s, %s>traverseOver(%s)", witnessType, elementType, kindInfo.traverseExpression());
  }

  /** Represents the type of path widening to apply. */
  private enum WideningType {
    /** No widening - standard FocusPath. */
    NONE,
    /** Optional/Maybe types - AffinePath via .some(). */
    OPTIONAL,
    /** Collection types - TraversalPath via .each(). */
    COLLECTION,
    /** Nullable types - AffinePath via .nullable(). */
    NULLABLE,
    /** Kind types with EXACTLY_ONE semantics - AffinePath via .traverseOver().headOption(). */
    KIND_EXACTLY_ONE,
    /** Kind types with ZERO_OR_ONE semantics - AffinePath via .traverseOver().headOption(). */
    KIND_ZERO_OR_ONE,
    /** Kind types with ZERO_OR_MORE semantics - TraversalPath via .traverseOver(). */
    KIND_ZERO_OR_MORE,
    /** SPI-registered zero-or-one type - AffinePath via .some(affine). */
    SPI_ZERO_OR_ONE,
    /** SPI-registered zero-or-more type - TraversalPath via .each(each). */
    SPI_ZERO_OR_MORE
  }

  /** Holds information about path type analysis. */
  private record PathTypeInfo(
      ClassName pathClass,
      TypeName innerType,
      WideningType wideningType,
      KindFieldInfo kindInfo,
      TraversableGenerator spiGenerator) {

    /** Creates a PathTypeInfo without Kind info or SPI generator. */
    static PathTypeInfo of(ClassName pathClass, TypeName innerType, WideningType wideningType) {
      return new PathTypeInfo(pathClass, innerType, wideningType, null, null);
    }

    /** Creates a PathTypeInfo for a Kind field. */
    static PathTypeInfo forKind(ClassName pathClass, KindFieldInfo kindInfo) {
      WideningType widening =
          switch (kindInfo.semantics()) {
            case EXACTLY_ONE -> WideningType.KIND_EXACTLY_ONE;
            case ZERO_OR_ONE -> WideningType.KIND_ZERO_OR_ONE;
            case ZERO_OR_MORE -> WideningType.KIND_ZERO_OR_MORE;
          };
      return new PathTypeInfo(pathClass, kindInfo.elementType(), widening, kindInfo, null);
    }

    /** Creates a PathTypeInfo for an SPI-detected field. */
    static PathTypeInfo forSpi(
        ClassName pathClass,
        TypeName innerType,
        WideningType wideningType,
        TraversableGenerator generator) {
      return new PathTypeInfo(pathClass, innerType, wideningType, null, generator);
    }
  }

  /** Analyses a field type and annotations to determine path widening. */
  private PathTypeInfo analyseFieldType(
      RecordComponentElement component, TypeMirror type, boolean widenCollections) {
    // Check for @Nullable annotation on the field first
    boolean isNullable = NullableAnnotations.hasNullableAnnotation(component);

    if (type.getKind() != TypeKind.DECLARED) {
      // Primitive and array types: no SPI widening in FocusProcessor (arrays remain FocusPath).
      // Users can manually call .each(EachInstances.arrayEach()) for array traversal.
      if (isNullable) {
        return PathTypeInfo.of(AFFINE_PATH_CLASS, TypeName.get(type).box(), WideningType.NULLABLE);
      }
      return PathTypeInfo.of(FOCUS_PATH_CLASS, null, WideningType.NONE);
    }

    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    // Check for Optional types (takes precedence over @Nullable)
    if (OPTIONAL_TYPES.contains(qualifiedName)) {
      TypeName innerType = extractTypeArgument(declaredType);
      return PathTypeInfo.of(AFFINE_PATH_CLASS, innerType, WideningType.OPTIONAL);
    }

    // Check for Collection types (takes precedence over @Nullable)
    if (COLLECTION_TYPES.contains(qualifiedName)) {
      TypeName innerType = extractTypeArgument(declaredType);
      return PathTypeInfo.of(TRAVERSAL_PATH_CLASS, innerType, WideningType.COLLECTION);
    }

    // Check for Kind<F, A> types
    KindFieldAnalyser kindAnalyser = new KindFieldAnalyser(processingEnv);
    Optional<KindFieldInfo> kindInfo = kindAnalyser.analyse(component);
    if (kindInfo.isPresent()) {
      KindFieldInfo info = kindInfo.get();
      // Note: EXACTLY_ONE also returns AffinePath because traverseOver() returns
      // TraversalPath and we narrow via headOption(). This is a safe type downgrade.
      ClassName pathClass =
          switch (info.semantics()) {
            case EXACTLY_ONE, ZERO_OR_ONE -> AFFINE_PATH_CLASS;
            case ZERO_OR_MORE -> TRAVERSAL_PATH_CLASS;
          };
      return PathTypeInfo.forKind(pathClass, info);
    }

    // Consult SPI generators for container types (e.g. Either, Try, Validated, Map).
    // ZERO_OR_ONE types are always widened to AffinePath.
    // ZERO_OR_MORE types are only widened to TraversalPath when widenCollections is enabled;
    // otherwise they remain FocusPath for backwards compatibility.
    // Generators are pre-sorted by priority descending; highest-priority match wins.
    // Only equal-priority conflicts emit a warning.
    TraversableGenerator matchedGenerator = null;
    for (TraversableGenerator generator : traversableGenerators) {
      if (generator.supports(type)) {
        if (matchedGenerator != null && matchedGenerator.priority() == generator.priority()) {
          processingEnv
              .getMessager()
              .printMessage(
                  Diagnostic.Kind.WARNING,
                  "Multiple TraversableGenerator SPI providers with equal priority ("
                      + generator.priority()
                      + ") support type "
                      + type
                      + ": "
                      + matchedGenerator.getClass().getName()
                      + " and "
                      + generator.getClass().getName()
                      + ". Using the first match.",
                  component);
        } else if (matchedGenerator == null) {
          matchedGenerator = generator;
        }
        // If matchedGenerator has higher priority, skip silently
      }
    }
    if (matchedGenerator != null && matchedGenerator.getCardinality() == Cardinality.ZERO_OR_ONE) {
      int typeArgIndex = matchedGenerator.getFocusTypeArgumentIndex();
      TypeName innerType = extractTypeArgumentAt(declaredType, typeArgIndex);
      return PathTypeInfo.forSpi(
          AFFINE_PATH_CLASS, innerType, WideningType.SPI_ZERO_OR_ONE, matchedGenerator);
    }

    // When widenCollections is enabled, also widen ZERO_OR_MORE SPI types to TraversalPath
    if (widenCollections
        && matchedGenerator != null
        && matchedGenerator.getCardinality() == Cardinality.ZERO_OR_MORE) {
      int typeArgIndex = matchedGenerator.getFocusTypeArgumentIndex();
      TypeName innerType = extractTypeArgumentAt(declaredType, typeArgIndex);
      return PathTypeInfo.forSpi(
          TRAVERSAL_PATH_CLASS, innerType, WideningType.SPI_ZERO_OR_MORE, matchedGenerator);
    }

    // Check for @Nullable annotation (after Kind and SPI checks)
    if (isNullable) {
      return PathTypeInfo.of(AFFINE_PATH_CLASS, TypeName.get(type).box(), WideningType.NULLABLE);
    }

    return PathTypeInfo.of(FOCUS_PATH_CLASS, null, WideningType.NONE);
  }

  /** Extracts the first type argument from a parameterised type. */
  private TypeName extractTypeArgument(DeclaredType declaredType) {
    return extractTypeArgumentAt(declaredType, 0);
  }

  /** Extracts the type argument at the given index from a parameterised type. */
  private TypeName extractTypeArgumentAt(DeclaredType declaredType, int index) {
    List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
    if (typeArgs.isEmpty() || index >= typeArgs.size()) {
      return ClassName.get(Object.class);
    }
    TypeMirror typeArg = typeArgs.get(index);
    // Resolve wildcard bounds: ? extends T → T, ? super T / ? → Object
    TypeMirror resolved = ProcessorUtils.resolveWildcard(typeArg);
    if (resolved == null) {
      return ClassName.get(Object.class);
    }
    return TypeName.get(resolved).box();
  }

  /** Gets the path class description for Javadoc. */
  private String getPathDescription(PathTypeInfo info) {
    return switch (info.wideningType) {
      case OPTIONAL, NULLABLE, KIND_ZERO_OR_ONE, KIND_EXACTLY_ONE, SPI_ZERO_OR_ONE -> "AffinePath";
      case COLLECTION, KIND_ZERO_OR_MORE, SPI_ZERO_OR_MORE -> "TraversalPath";
      case NONE -> "FocusPath";
    };
  }

  /** Gets the appropriate get method name for Javadoc examples. */
  private String getPathGetMethod(PathTypeInfo info) {
    return switch (info.wideningType) {
      case OPTIONAL, NULLABLE, KIND_ZERO_OR_ONE, KIND_EXACTLY_ONE, SPI_ZERO_OR_ONE -> "getOptional";
      case COLLECTION, KIND_ZERO_OR_MORE, SPI_ZERO_OR_MORE -> "getAll";
      case NONE -> "get";
    };
  }

  private void error(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }
}
