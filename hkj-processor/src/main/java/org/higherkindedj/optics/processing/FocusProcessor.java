// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.Reachability;

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
 *   <li><b>Optional/Maybe fields</b> - Generate {@code AffinePath}, an {@code Optional} via {@code
 *       .some(FocusPaths.optionalSome())} and a {@code Maybe} via {@code .some(Affines.just())}
 *   <li><b>Collection fields</b> (List, Set, Collection) - Generate {@code TraversalPath}, each
 *       through the {@code Each} that rebuilds it
 *   <li><b>Kind&lt;F, A&gt; fields</b> - Generate appropriate path via {@code
 *       .each(EachInstances.fromTraverse())}
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
 *             .each(EachInstances.<ListKind.Witness, Member>fromTraverse(ListTraverse.INSTANCE));
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
  }

  /** ClassName for FocusPath, the path every generated method is built from. */
  private static final ClassName FOCUS_PATH_CLASS = WideningAnalysis.FOCUS_PATH_CLASS;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // First pass: collect all @GenerateFocus annotated types for navigator resolution
    Set<String> navigableTypes = new HashSet<>();
    Set<? extends Element> allAnnotated = roundEnv.getElementsAnnotatedWith(GenerateFocus.class);
    // A record whose settings cannot be read writes no companion, so nothing navigates into it.
    for (Element element : allAnnotated) {
      if (element.getKind() == ElementKind.RECORD
          && !NavigatorClassGenerator.unreadable(element.getAnnotation(GenerateFocus.class))) {
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
    if (NavigatorClassGenerator.unreadable(annotation)) {
      return;
    }
    String targetPackage = annotation.targetPackage();
    String packageName = targetPackage.isEmpty() ? defaultPackage : targetPackage;
    if (!Reachability.check(
        processingEnv,
        "@GenerateFocus",
        recordElement,
        Reachability.companion(packageName, defaultPackage),
        Reachability.record(recordElement, recordElement.getRecordComponents()))) {
      return;
    }
    boolean generateNavigators = annotation.generateNavigators();
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
    TypeName recordTypeName = getParameterizedTypeName(recordElement, packageName);

    // One analysis answers what each component widens to, for the static methods here and for the
    // navigator methods that compose them.
    WideningAnalysis analysis =
        new WideningAnalysis(processingEnv, traversableGenerators, packageName);

    // A name in a field list that matches no component is an error, but the companion is still
    // written, as it is for a container refused at a component, so that code using its paths does
    // not fail on a missing class as well.
    new FocusFieldListChecks(processingEnv.getMessager(), analysis).check(recordElement);

    // Create navigator generator if enabled
    NavigatorClassGenerator navigatorGenerator = null;
    if (generateNavigators) {
      navigatorGenerator =
          new NavigatorClassGenerator(processingEnv, navigableTypes, maxNavigatorDepth, analysis);
    }

    // Generate FocusPath methods for each component
    for (RecordComponentElement component : components) {
      // What the Kind analysis passes over, a @TraverseField it cannot apply or a library witness
      // it does not know, is reported here, where each component is seen once, rather than from
      // the analysis, which a navigator may run more than once.
      analysis.reportPassedOver(component);
      MethodSpec method = null;

      // Try to create a navigator method if navigators are enabled
      if (navigatorGenerator != null) {
        method = navigatorGenerator.createNavigatorMethod(component, recordElement, recordTypeName);
      }

      // Fall back to the static FocusPath method when no navigator took the field. The one
      // analysis says what that method widens to, and a container it turned away is rejected from
      // the same result, so the method's shape and the diagnostic cannot disagree. A field a
      // navigator did take has an instantiable record in focus, which a walk that turned a
      // container away never leaves, so no container is turned away for it. This walk reports any
      // generator conflict on a static field; the navigator method reports for a field it takes.
      if (method == null) {
        WideningAnalysis.Widening widening =
            analysis.analyseDeclaration(
                component, NavigatorClassGenerator.widensCollections(recordElement, component));
        if (widening.declined() != null) {
          reportUndenotableContainer(component, widening.declined());
        }
        method =
            createFocusPathMethod(component, recordElement, recordTypeName, widening, packageName);
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

  private TypeName getParameterizedTypeName(TypeElement typeElement, String packageName) {
    List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return ClassName.get(typeElement);
    } else {
      List<TypeVariableName> typeVars =
          typeParameters.stream()
              .map(parameter -> ProcessorUtils.typeVariableOf(parameter, packageName))
              .toList();
      return ParameterizedTypeName.get(
          ClassName.get(typeElement), typeVars.toArray(new TypeName[0]));
    }
  }

  private MethodSpec createFocusPathMethod(
      RecordComponentElement component,
      TypeElement recordElement,
      TypeName recordTypeName,
      WideningAnalysis.Widening widening,
      String packageName) {

    String componentName = component.getSimpleName().toString();

    ClassName pathClass = widening.tier().pathClass();
    TypeName innerTypeName = widening.focusType();
    ParameterizedTypeName returnTypeName =
        ParameterizedTypeName.get(pathClass, recordTypeName, innerTypeName);

    String pathDescription = widening.tier().description();
    String getMethodName = widening.tier().getMethod();

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(componentName)
            .addJavadoc(
                "Creates $L {@link $T} for the {@code $L} field of {@link $T}.\n\n"
                    + "<p>The returned path can be composed with other optics for deep navigation:\n"
                    + "<pre>{@code\n"
                    + "$L.$L().$L(instance);  // Get the $L value\n"
                    + "$L.$L().set(newValue, instance);  // Set the $L value\n"
                    + "$L.$L().modify(fn, instance);  // Transform the $L value\n"
                    + "}</pre>\n\n"
                    + "@return A non-null {@code $L<$T, $T>}.",
                widening.tier().article(),
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
            // The path type, the setter lambda's inferred value and the record's type-parameter
            // bounds all land in this method, and so may a cast to a raw or wildcard container.
            .addAnnotations(
                WideningAnalysis.warningSuppression(component, recordElement, widening.steps()))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(returnTypeName);

    // Add type parameters if the record is generic
    for (TypeParameterElement typeParam : recordElement.getTypeParameters()) {
      methodBuilder.addTypeVariable(ProcessorUtils.typeVariableOf(typeParam, packageName));
    }

    // Build the constructor arguments for the setter lambda
    String constructorArgs = ProcessorUtils.rebuildArguments(recordElement, componentName);

    // Generate code based on path type widening. The component name rides along as the path's
    // field-name segment, so generated paths self-locate.
    String baseLens =
        String.format(
            Locale.ROOT,
            "$T.of($T.of($T::%s, (source, newValue) -> new $T(%s)), \"%s\")",
            componentName,
            constructorArgs,
            componentName);

    // One expression per widening, built by the same analysis the return type came from, so the
    // method's shape and its body cannot disagree.
    List<Object> args =
        new ArrayList<>(List.of(FOCUS_PATH_CLASS, Lens.class, recordTypeName, recordTypeName));
    String wideningExpression = WideningAnalysis.expression(widening.steps(), args, packageName);
    methodBuilder.addStatement("return " + baseLens + wideningExpression, args.toArray());

    return methodBuilder.build();
  }

  /**
   * Reports the container the widening cannot be written for, in the terms it went wrong in: a raw
   * container has no type arguments to infer from, a parameterised one has a wildcard among them.
   */
  private void reportUndenotableContainer(
      RecordComponentElement component, DeclaredType declaredType) {
    boolean raw = ProcessorUtils.isRaw(declaredType);
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
                + ProcessorUtils.simpleTypeName(declaredType)
                + ".",
        "The optic instance that widens that container is inferred from the field type, and "
            + (raw
                ? "a raw type offers no type arguments to infer it from."
                : "a wildcard has no ground instantiation to infer it from."),
        "Declare the component with concrete type arguments"
            + ProcessorUtils.concreteAlternative(declaredType)
                .map(alternative -> ", such as " + alternative)
                .orElse("")
            + (raw
                // A raw container has no element type for @GenerateTraversals to focus either, so
                // it draws a note and no traversal there; only the lens takes it as written.
                ? ", or drop @GenerateFocus from the record: @GenerateLenses composes no optic"
                    + " instance and takes the component as written."
                : ", or drop @GenerateFocus from the record: @GenerateLenses and"
                    + " @GenerateTraversals compose no optic instance and take the component as"
                    + " written."));
  }
}
