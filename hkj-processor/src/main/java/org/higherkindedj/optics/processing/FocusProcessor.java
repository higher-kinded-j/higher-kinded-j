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
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.processing.spi.Cardinality;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
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
public class FocusProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

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

  /** ClassName for FocusPath, the path every generated method is built from. */
  private static final ClassName FOCUS_PATH_CLASS = WideningAnalysis.FOCUS_PATH_CLASS;

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
          Diagnostics.error(
              processingEnv.getMessager(),
              element,
              "@GenerateFocus",
              "can only be applied to records, but '"
                  + element.getSimpleName()
                  + "' is a "
                  + element.getKind().toString().toLowerCase(java.util.Locale.ROOT)
                  + ".",
              "The processor derives FocusPath methods from record components.",
              "Move the annotation to a record, or use @ImportOptics with an OpticsSpec"
                  + " interface for types you cannot change.");
          continue;
        }
        writeFocusFile((TypeElement) element, navigableTypes);
      }
    }
    return true;
  }

  @ExcludeFromJacocoGeneratedReport
  private void writeFocusFile(TypeElement element, Set<String> navigableTypes) {
    try {
      generateFocusFile(element, navigableTypes);
    } catch (IOException e) {
      Diagnostics.error(
          processingEnv.getMessager(),
          element,
          "@GenerateFocus",
          "could not write the generated Focus companion for '" + element.getSimpleName() + "'.",
          "The filer reported: " + e.getMessage() + ".",
          "Check build-output permissions and free disk space, then rebuild.");
    }
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

    String focusClassName = recordName + "Focus";

    final ClassName generatedAnnotation =
        ClassName.get("org.higherkindedj.optics.annotations", "Generated");

    String navigatorNote =
        generateNavigators
            ? "\n\n<p>This class includes navigator classes for fluent cross-type navigation."
            : "";

    TypeSpec.Builder focusClassBuilder =
        TypeSpec.classBuilder(focusClassName)
            .addOriginatingElement(recordElement)
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

    // One analysis answers what each component widens to, for the static methods here and for the
    // navigator methods that compose them (issue #719).
    WideningAnalysis analysis = new WideningAnalysis(processingEnv, traversableGenerators);

    // Create navigator generator if enabled
    NavigatorClassGenerator navigatorGenerator = null;
    if (generateNavigators) {
      navigatorGenerator =
          new NavigatorClassGenerator(processingEnv, navigableTypes, maxNavigatorDepth, analysis);
    }

    // Generate FocusPath methods for each component
    for (RecordComponentElement component : components) {
      reportUndenotableSpiWidening(component, widenCollections, navigatorGenerator, analysis);
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
                component, recordElement, components, recordTypeName, widenCollections, analysis);
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
      boolean widenCollections,
      WideningAnalysis analysis) {

    String componentName = component.getSimpleName().toString();

    // Detect path type widening based on field type and annotations
    WideningAnalysis.Widening widening = analysis.analyse(component, widenCollections);

    ClassName pathClass = widening.tier().pathClass();
    TypeName innerTypeName = widening.focusType();
    ParameterizedTypeName returnTypeName =
        ParameterizedTypeName.get(pathClass, recordTypeName, innerTypeName);

    String pathDescription = widening.tier().description();
    String getMethodName = widening.tier().getMethod();

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

    // Generate code based on path type widening. The component name rides along as the path's
    // field-name segment, so generated paths self-locate (issue #592).
    String baseLens =
        String.format(
            "$T.of($T.of($T::%s, (source, newValue) -> new $T(%s)), \"%s\")",
            componentName, constructorArgs, componentName);

    // One expression per widening, built by the same analysis the return type came from, so the
    // method's shape and its body cannot disagree.
    List<Object> args =
        new ArrayList<>(List.of(FOCUS_PATH_CLASS, Lens.class, recordTypeName, recordTypeName));
    String wideningExpression = WideningAnalysis.expression(widening.steps(), args);
    methodBuilder.addStatement("return " + baseLens + wideningExpression, args.toArray());

    return methodBuilder.build();
  }

  /**
   * Rejects a component whose SPI container is raw or carries a wildcard type argument, where the
   * widening that container would otherwise receive cannot be written.
   *
   * <p>The walk mirrors the one {@link WideningAnalysis} makes, so it reaches the same containers
   * and stops where that one stops. A layer the current settings do not widen ends it: a {@code
   * ZERO_OR_MORE} SPI field is left a plain {@code FocusPath} unless collections are widened or a
   * navigator takes it, and the analysis then neither widens it nor looks inside it, so neither
   * that layer nor anything under it can be reported.
   *
   * @param component the record component to inspect
   * @param widenCollections whether ZERO_OR_MORE SPI types widen
   * @param navigatorGenerator the navigator generator, or null when navigators are off
   * @param analysis the widening analysis whose walk this one mirrors
   */
  private void reportUndenotableSpiWidening(
      RecordComponentElement component,
      boolean widenCollections,
      NavigatorClassGenerator navigatorGenerator,
      WideningAnalysis analysis) {

    // A navigator only ever reads the component's own type, and the walk reaches a nested
    // container only when that type denotes its own arguments, so this answer belongs to the
    // first layer alone.
    boolean navigatorWidens =
        navigatorGenerator != null && navigatorGenerator.widensUndenotableSpiContainer(component);

    TypeMirror current = component.asType();
    for (int depth = 0; depth < WideningAnalysis.MAX_NESTING_DEPTH; depth++) {
      if (current.getKind() != TypeKind.DECLARED) {
        return;
      }
      DeclaredType declaredType = (DeclaredType) current;
      boolean recognised = analysis.recognisedContainer(current);

      // Optional and the collections widen through .some()/.each(), whose free type variable
      // takes an undenotable argument without complaint. Only a generator's container, which
      // widens through an inferred optic instance, is at risk.
      TraversableGenerator generator = recognised ? null : analysis.findSpiGenerator(current, null);
      if (!recognised && generator == null) {
        return;
      }
      if (!widensHere(generator, widenCollections, navigatorWidens)) {
        return;
      }
      if (generator != null && WideningAnalysis.widensUndenotably(generator, declaredType)) {
        reportUndenotableContainer(component, declaredType);
        return;
      }
      int typeArgIndex = generator == null ? 0 : generator.getFocusTypeArgumentIndex();
      current = analysis.typeArgumentAt(declaredType, typeArgIndex);
      if (current == null) {
        return;
      }
    }
  }

  /**
   * Whether the analysis widens this layer, and so goes on to look inside it.
   *
   * <p>Optional and the collections always do, and so does a {@code ZERO_OR_ONE} generator. A
   * {@code ZERO_OR_MORE} generator only does when collections are widened or a navigator takes the
   * field; left alone it stays a plain {@code FocusPath}, and the type arguments of everything
   * beneath it are never asked for an optic.
   *
   * @param generator the generator for this layer, or null when Optional or a collection widens it
   * @param widenCollections whether ZERO_OR_MORE SPI types widen
   * @param navigatorWidens whether a navigator widens the component's own type
   */
  private static boolean widensHere(
      TraversableGenerator generator, boolean widenCollections, boolean navigatorWidens) {
    return generator == null
        || generator.getCardinality() == Cardinality.ZERO_OR_ONE
        || widenCollections
        || navigatorWidens;
  }

  /**
   * Reports the container the widening cannot be written for, in the terms it went wrong in: a raw
   * container has no type arguments to infer from, a parameterised one has a wildcard among them.
   */
  private void reportUndenotableContainer(
      RecordComponentElement component, DeclaredType declaredType) {
    boolean raw = declaredType.getTypeArguments().isEmpty();
    String qualifiedComponent =
        component.getEnclosingElement().getSimpleName() + "." + component.getSimpleName();
    Diagnostics.error(
        processingEnv.getMessager(),
        component,
        "@GenerateFocus",
        raw
            ? "record component '"
                + qualifiedComponent
                + "' has a raw "
                + declaredType.asElement().getSimpleName()
                + "."
            : "record component '"
                + qualifiedComponent
                + "' has a wildcard type argument in "
                + simpleTypeName(declaredType)
                + ".",
        "The optic instance that widens that container is inferred from the field type, and "
            + (raw
                ? "a raw type offers no type arguments to infer it from."
                : "a wildcard has no ground instantiation to infer it from."),
        "Declare the component with concrete type arguments, such as "
            + concreteAlternative(declaredType)
            + ".");
  }

  /**
   * The container written with concrete type arguments: each wildcard replaced by the type it
   * resolves to, and a raw container filled in from the bounds its type parameters declare.
   */
  private static String concreteAlternative(DeclaredType declaredType) {
    TypeElement element = (TypeElement) declaredType.asElement();
    Stream<String> arguments =
        declaredType.getTypeArguments().isEmpty()
            ? element.getTypeParameters().stream()
                .map(parameter -> simpleTypeName(parameter.getBounds().getFirst()))
            : declaredType.getTypeArguments().stream()
                .map(ProcessorUtils::resolveWildcard)
                .map(resolved -> resolved == null ? "Object" : simpleTypeName(resolved));
    return element.getSimpleName() + "<" + arguments.collect(Collectors.joining(", ")) + ">";
  }

  /** Renders a type for a diagnostic, with package qualifiers dropped and type arguments spaced. */
  private static String simpleTypeName(TypeMirror type) {
    return type.toString().replaceAll("\\b(?:[a-z][\\p{Alnum}_]*\\.)+", "").replace(",", ", ");
  }
}
