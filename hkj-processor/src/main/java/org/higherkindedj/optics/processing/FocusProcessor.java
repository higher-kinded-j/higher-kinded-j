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
      case NESTED -> {
        // Nested container type: composed widening chain
        List<Object> args =
            new ArrayList<>(List.of(FOCUS_PATH_CLASS, Lens.class, recordTypeName, recordTypeName));
        String chainExpr = buildWideningChainExpression(pathTypeInfo.wideningChain(), args);
        methodBuilder.addStatement("return " + baseLens + chainExpr, args.toArray());
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

  /**
   * Builds the chained widening expression for a nested container.
   *
   * <p>For example, {@code Optional<List<String>>} produces {@code .some().each()}, and {@code
   * Either<E, Map<K, V>>} produces {@code
   * .some(Affines.eitherRight()).each(EachInstances.mapValuesEach())}.
   *
   * @param chain the list of widening steps
   * @param args the mutable list of JavaPoet arguments (for SPI import resolution)
   * @return the chained expression string
   */
  private String buildWideningChainExpression(List<WideningStep> chain, List<Object> args) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < chain.size(); i++) {
      WideningStep step = chain.get(i);
      // Check if the next step requires argument type matching (SPI with parameters).
      // If so, the current no-arg step needs a type witness to help Java's type inference.
      boolean nextStepNeedsTypeWitness =
          i + 1 < chain.size() && isParameterisedStep(chain.get(i + 1));
      switch (step.type()) {
        case OPTIONAL -> {
          if (nextStepNeedsTypeWitness && step.innerTypeMirror() != null) {
            sb.append(".<$T>some()");
            args.add(TypeName.get(step.innerTypeMirror()));
          } else {
            sb.append(".some()");
          }
        }
        case COLLECTION -> {
          if (nextStepNeedsTypeWitness && step.innerTypeMirror() != null) {
            sb.append(".<$T>each()");
            args.add(TypeName.get(step.innerTypeMirror()));
          } else {
            sb.append(".each()");
          }
        }
        case NULLABLE -> sb.append(".nullable()");
        case SPI_ZERO_OR_ONE -> {
          TraversableGenerator gen = step.spiGenerator();
          String opticExpr = gen.generateOpticExpression();
          String resolvedExpr =
              OpticExpressionResolver.resolve(opticExpr, gen.getRequiredImports(), args);
          sb.append(".some(").append(resolvedExpr).append(")");
        }
        case SPI_ZERO_OR_MORE -> {
          TraversableGenerator gen = step.spiGenerator();
          String opticExpr = gen.generateOpticExpression();
          String resolvedExpr =
              OpticExpressionResolver.resolve(opticExpr, gen.getRequiredImports(), args);
          sb.append(".each(").append(resolvedExpr).append(")");
        }
        case KIND_EXACTLY_ONE, KIND_ZERO_OR_ONE -> {
          KindFieldInfo kindInfo = step.kindInfo();
          sb.append(buildTraverseOverCall(kindInfo)).append(".headOption()");
        }
        case KIND_ZERO_OR_MORE -> {
          KindFieldInfo kindInfo = step.kindInfo();
          sb.append(buildTraverseOverCall(kindInfo));
        }
        default -> {
          // NONE or NESTED should not appear in a chain
        }
      }
    }
    return sb.toString();
  }

  /** Returns true if this widening step requires argument type matching (has parameters). */
  private static boolean isParameterisedStep(WideningStep step) {
    return step.type() == WideningType.SPI_ZERO_OR_ONE
        || step.type() == WideningType.SPI_ZERO_OR_MORE;
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
    SPI_ZERO_OR_MORE,
    /** Nested container type - composed widening chain. */
    NESTED
  }

  /**
   * Represents a single widening step in a nested container chain.
   *
   * @param type the widening type for this step
   * @param kindInfo Kind field info if this step is a Kind type (may be null)
   * @param spiGenerator SPI generator if this step is an SPI type (may be null)
   * @param innerTypeMirror the type produced by unwrapping this container (may be null)
   */
  private record WideningStep(
      WideningType type,
      KindFieldInfo kindInfo,
      TraversableGenerator spiGenerator,
      TypeMirror innerTypeMirror) {}

  /** Maximum recursion depth for nested container analysis. */
  private static final int MAX_NESTING_DEPTH = 3;

  /** Holds information about path type analysis. */
  private record PathTypeInfo(
      ClassName pathClass,
      TypeName innerType,
      WideningType wideningType,
      List<WideningStep> wideningChain,
      KindFieldInfo kindInfo,
      TraversableGenerator spiGenerator) {

    /** Creates a PathTypeInfo without Kind info or SPI generator. */
    static PathTypeInfo of(ClassName pathClass, TypeName innerType, WideningType wideningType) {
      return new PathTypeInfo(pathClass, innerType, wideningType, List.of(), null, null);
    }

    /** Creates a PathTypeInfo for a Kind field. */
    static PathTypeInfo forKind(ClassName pathClass, KindFieldInfo kindInfo) {
      WideningType widening =
          switch (kindInfo.semantics()) {
            case EXACTLY_ONE -> WideningType.KIND_EXACTLY_ONE;
            case ZERO_OR_ONE -> WideningType.KIND_ZERO_OR_ONE;
            case ZERO_OR_MORE -> WideningType.KIND_ZERO_OR_MORE;
          };
      return new PathTypeInfo(
          pathClass, kindInfo.elementType(), widening, List.of(), kindInfo, null);
    }

    /** Creates a PathTypeInfo for an SPI-detected field. */
    static PathTypeInfo forSpi(
        ClassName pathClass,
        TypeName innerType,
        WideningType wideningType,
        TraversableGenerator generator) {
      return new PathTypeInfo(pathClass, innerType, wideningType, List.of(), null, generator);
    }

    /** Creates a PathTypeInfo for a nested container with a widening chain. */
    static PathTypeInfo forNested(
        ClassName pathClass, TypeName innerType, List<WideningStep> chain) {
      return new PathTypeInfo(pathClass, innerType, WideningType.NESTED, chain, null, null);
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
      TypeMirror innerTypeMirror = getFirstTypeArgument(declaredType);
      Optional<PathTypeInfo> nested =
          analyseNestedContainer(
              new WideningStep(WideningType.OPTIONAL, null, null, innerTypeMirror),
              innerTypeMirror,
              widenCollections);
      if (nested.isPresent()) {
        return nested.get();
      }
      TypeName innerType = extractTypeArgument(declaredType);
      return PathTypeInfo.of(AFFINE_PATH_CLASS, innerType, WideningType.OPTIONAL);
    }

    // Check for Collection types (takes precedence over @Nullable)
    if (COLLECTION_TYPES.contains(qualifiedName)) {
      TypeMirror innerTypeMirror = getFirstTypeArgument(declaredType);
      Optional<PathTypeInfo> nested =
          analyseNestedContainer(
              new WideningStep(WideningType.COLLECTION, null, null, innerTypeMirror),
              innerTypeMirror,
              widenCollections);
      if (nested.isPresent()) {
        return nested.get();
      }
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
    TraversableGenerator matchedGenerator = findSpiGenerator(type, component);
    if (matchedGenerator != null && matchedGenerator.getCardinality() == Cardinality.ZERO_OR_ONE) {
      int typeArgIndex = matchedGenerator.getFocusTypeArgumentIndex();
      TypeMirror innerTypeMirror = getTypeArgumentAt(declaredType, typeArgIndex);
      Optional<PathTypeInfo> nested =
          analyseNestedContainer(
              new WideningStep(
                  WideningType.SPI_ZERO_OR_ONE, null, matchedGenerator, innerTypeMirror),
              innerTypeMirror,
              widenCollections);
      if (nested.isPresent()) {
        return nested.get();
      }
      TypeName innerType = extractTypeArgumentAt(declaredType, typeArgIndex);
      return PathTypeInfo.forSpi(
          AFFINE_PATH_CLASS, innerType, WideningType.SPI_ZERO_OR_ONE, matchedGenerator);
    }

    // When widenCollections is enabled, also widen ZERO_OR_MORE SPI types to TraversalPath
    if (widenCollections
        && matchedGenerator != null
        && matchedGenerator.getCardinality() == Cardinality.ZERO_OR_MORE) {
      int typeArgIndex = matchedGenerator.getFocusTypeArgumentIndex();
      TypeMirror innerTypeMirror = getTypeArgumentAt(declaredType, typeArgIndex);
      Optional<PathTypeInfo> nested =
          analyseNestedContainer(
              new WideningStep(
                  WideningType.SPI_ZERO_OR_MORE, null, matchedGenerator, innerTypeMirror),
              innerTypeMirror,
              widenCollections);
      if (nested.isPresent()) {
        return nested.get();
      }
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

  /**
   * Finds the highest-priority SPI generator that supports the given type.
   *
   * @param type the type to check
   * @param component the record component for diagnostic messages (may be null)
   * @return the matched generator, or null if none found
   */
  private TraversableGenerator findSpiGenerator(TypeMirror type, Element component) {
    TraversableGenerator matchedGenerator = null;
    for (TraversableGenerator generator : traversableGenerators) {
      if (generator.supports(type)) {
        if (matchedGenerator != null && matchedGenerator.priority() == generator.priority()) {
          if (component != null) {
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
          }
        } else if (matchedGenerator == null) {
          matchedGenerator = generator;
        }
      }
    }
    return matchedGenerator;
  }

  /**
   * Analyses whether an inner type contains nested containers and, if so, builds the full widening
   * chain starting with the given outer step.
   *
   * @param outerStep the widening step for the outermost container
   * @param innerTypeMirror the inner type to check for further nesting
   * @param widenCollections whether to widen ZERO_OR_MORE SPI types
   * @return a PathTypeInfo for the nested chain, or empty if no nesting was found
   */
  private Optional<PathTypeInfo> analyseNestedContainer(
      WideningStep outerStep, TypeMirror innerTypeMirror, boolean widenCollections) {
    if (innerTypeMirror == null) {
      return Optional.empty();
    }
    List<WideningStep> innerChain = analyseNestedType(innerTypeMirror, widenCollections, 1);
    if (innerChain.isEmpty()) {
      return Optional.empty();
    }
    List<WideningStep> fullChain = new ArrayList<>();
    fullChain.add(outerStep);
    fullChain.addAll(innerChain);
    TypeName deepInnerType = resolveDeepInnerType(innerTypeMirror, innerChain);
    ClassName pathClass = computeComposedPathClass(fullChain);
    return Optional.of(PathTypeInfo.forNested(pathClass, deepInnerType, fullChain));
  }

  /**
   * Recursively analyses a type to detect nested container patterns. Returns a list of widening
   * steps for the inner containers, or an empty list if the type is not a recognised container.
   *
   * @param type the type to analyse
   * @param widenCollections whether to widen ZERO_OR_MORE SPI types
   * @param depth current recursion depth
   * @return list of widening steps for nested containers
   */
  private List<WideningStep> analyseNestedType(
      TypeMirror type, boolean widenCollections, int depth) {
    if (depth >= MAX_NESTING_DEPTH || type.getKind() != TypeKind.DECLARED) {
      return List.of();
    }

    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    // Check for Optional types
    if (OPTIONAL_TYPES.contains(qualifiedName)) {
      List<WideningStep> steps = new ArrayList<>();
      TypeMirror innerTypeMirror = getFirstTypeArgument(declaredType);
      steps.add(new WideningStep(WideningType.OPTIONAL, null, null, innerTypeMirror));
      if (innerTypeMirror != null) {
        steps.addAll(analyseNestedType(innerTypeMirror, widenCollections, depth + 1));
      }
      return steps;
    }

    // Check for Collection types
    if (COLLECTION_TYPES.contains(qualifiedName)) {
      List<WideningStep> steps = new ArrayList<>();
      TypeMirror innerTypeMirror = getFirstTypeArgument(declaredType);
      steps.add(new WideningStep(WideningType.COLLECTION, null, null, innerTypeMirror));
      if (innerTypeMirror != null) {
        steps.addAll(analyseNestedType(innerTypeMirror, widenCollections, depth + 1));
      }
      return steps;
    }

    // Check for SPI types
    TraversableGenerator gen = findSpiGenerator(type, null);
    if (gen != null && gen.getCardinality() == Cardinality.ZERO_OR_ONE) {
      List<WideningStep> steps = new ArrayList<>();
      TypeMirror innerTypeMirror = getTypeArgumentAt(declaredType, gen.getFocusTypeArgumentIndex());
      steps.add(new WideningStep(WideningType.SPI_ZERO_OR_ONE, null, gen, innerTypeMirror));
      if (innerTypeMirror != null) {
        steps.addAll(analyseNestedType(innerTypeMirror, widenCollections, depth + 1));
      }
      return steps;
    }
    if (widenCollections && gen != null && gen.getCardinality() == Cardinality.ZERO_OR_MORE) {
      List<WideningStep> steps = new ArrayList<>();
      TypeMirror innerTypeMirror = getTypeArgumentAt(declaredType, gen.getFocusTypeArgumentIndex());
      steps.add(new WideningStep(WideningType.SPI_ZERO_OR_MORE, null, gen, innerTypeMirror));
      if (innerTypeMirror != null) {
        steps.addAll(analyseNestedType(innerTypeMirror, widenCollections, depth + 1));
      }
      return steps;
    }

    return List.of();
  }

  /**
   * Computes the final path class by composing the widening lattice across a chain.
   *
   * <p>The widening lattice: Focus + Affine = Affine, Focus + Traversal = Traversal, Affine +
   * Traversal = Traversal.
   */
  private ClassName computeComposedPathClass(List<WideningStep> chain) {
    boolean hasTraversal = false;
    boolean hasAffine = false;
    for (WideningStep step : chain) {
      switch (step.type()) {
        case COLLECTION, KIND_ZERO_OR_MORE, SPI_ZERO_OR_MORE -> hasTraversal = true;
        case OPTIONAL, NULLABLE, KIND_ZERO_OR_ONE, KIND_EXACTLY_ONE, SPI_ZERO_OR_ONE ->
            hasAffine = true;
        default -> {}
      }
    }
    if (hasTraversal) return TRAVERSAL_PATH_CLASS;
    if (hasAffine) return AFFINE_PATH_CLASS;
    return FOCUS_PATH_CLASS;
  }

  /**
   * Resolves the deepest inner type by walking through the nested container chain.
   *
   * @param typeMirror the type at the current nesting level
   * @param chain the remaining widening steps
   * @return the innermost type name
   */
  private TypeName resolveDeepInnerType(TypeMirror typeMirror, List<WideningStep> chain) {
    if (chain.isEmpty() || typeMirror.getKind() != TypeKind.DECLARED) {
      return TypeName.get(typeMirror).box();
    }

    DeclaredType declaredType = (DeclaredType) typeMirror;
    WideningStep step = chain.getFirst();

    int typeArgIndex = 0;
    if (step.spiGenerator() != null) {
      typeArgIndex = step.spiGenerator().getFocusTypeArgumentIndex();
    }

    TypeMirror innerTypeMirror = getTypeArgumentAt(declaredType, typeArgIndex);
    if (innerTypeMirror == null) {
      return ClassName.get(Object.class);
    }

    if (chain.size() == 1) {
      // Resolve wildcard bounds on the innermost type
      TypeMirror resolved = ProcessorUtils.resolveWildcard(innerTypeMirror);
      return resolved != null ? TypeName.get(resolved).box() : ClassName.get(Object.class);
    }

    return resolveDeepInnerType(innerTypeMirror, chain.subList(1, chain.size()));
  }

  /** Gets the first type argument of a declared type, or null if none. */
  private TypeMirror getFirstTypeArgument(DeclaredType declaredType) {
    return getTypeArgumentAt(declaredType, 0);
  }

  /** Gets the type argument at the given index, or null if out of bounds. */
  private TypeMirror getTypeArgumentAt(DeclaredType declaredType, int index) {
    List<? extends TypeMirror> args = declaredType.getTypeArguments();
    if (args.isEmpty() || index >= args.size()) {
      return null;
    }
    TypeMirror arg = args.get(index);
    // Resolve wildcard bounds
    TypeMirror resolved = ProcessorUtils.resolveWildcard(arg);
    return resolved != null ? resolved : null;
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
    if (info.wideningType == WideningType.NESTED) {
      ClassName pathClass = info.pathClass();
      if (pathClass.equals(TRAVERSAL_PATH_CLASS)) return "TraversalPath";
      if (pathClass.equals(AFFINE_PATH_CLASS)) return "AffinePath";
      return "FocusPath";
    }
    return switch (info.wideningType) {
      case OPTIONAL, NULLABLE, KIND_ZERO_OR_ONE, KIND_EXACTLY_ONE, SPI_ZERO_OR_ONE -> "AffinePath";
      case COLLECTION, KIND_ZERO_OR_MORE, SPI_ZERO_OR_MORE -> "TraversalPath";
      case NONE -> "FocusPath";
      case NESTED -> "FocusPath"; // handled above, unreachable
    };
  }

  /** Gets the appropriate get method name for Javadoc examples. */
  private String getPathGetMethod(PathTypeInfo info) {
    if (info.wideningType == WideningType.NESTED) {
      ClassName pathClass = info.pathClass();
      if (pathClass.equals(TRAVERSAL_PATH_CLASS)) return "getAll";
      if (pathClass.equals(AFFINE_PATH_CLASS)) return "getOptional";
      return "get";
    }
    return switch (info.wideningType) {
      case OPTIONAL, NULLABLE, KIND_ZERO_OR_ONE, KIND_EXACTLY_ONE, SPI_ZERO_OR_ONE -> "getOptional";
      case COLLECTION, KIND_ZERO_OR_MORE, SPI_ZERO_OR_MORE -> "getAll";
      case NONE -> "get";
      case NESTED -> "get"; // handled above, unreachable
    };
  }

  private void error(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }
}
