// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.processing.external.ExternalLensGenerator;
import org.higherkindedj.optics.processing.external.ExternalPrismGenerator;
import org.higherkindedj.optics.processing.external.SpecAnalysis;
import org.higherkindedj.optics.processing.external.SpecInterfaceAnalyser;
import org.higherkindedj.optics.processing.external.SpecInterfaceGenerator;
import org.higherkindedj.optics.processing.external.TypeAnalysis;
import org.higherkindedj.optics.processing.external.TypeKindAnalyser;

/**
 * Annotation processor for {@link ImportOptics}.
 *
 * <p>This processor generates optics for external types that you do not own. It supports two modes:
 *
 * <h3>1. Package/Type Annotation with Class List</h3>
 *
 * <p>When applied to a package (via {@code package-info.java}) or type with a class list, it
 * analyses each referenced class and generates appropriate optics:
 *
 * <ul>
 *   <li>Records → Lens per component (in {@code <TypeName>Lenses.java})
 *   <li>Sealed interfaces → Prism per permitted subtype (in {@code <TypeName>Prisms.java})
 *   <li>Enums → Prism per constant (in {@code <TypeName>Prisms.java})
 *   <li>Classes with wither methods → Lens per wither (in {@code <TypeName>Lenses.java})
 * </ul>
 *
 * <h3>2. Spec Interface (Phase 2)</h3>
 *
 * <p>When applied to an interface extending {@code OpticsSpec<S>}, it generates a utility class
 * implementing the optics defined by the interface's abstract methods. This supports:
 *
 * <ul>
 *   <li>Explicit copy strategies: {@code @ViaBuilder}, {@code @Wither}, {@code @ViaConstructor},
 *       {@code @ViaCopyAndSet}
 *   <li>Prism hints: {@code @InstanceOf}, {@code @MatchWhen}
 *   <li>Traversal hints: {@code @TraverseWith}, {@code @ThroughField}
 * </ul>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.ImportOptics")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class ImportOpticsProcessor extends AbstractProcessor {

  private static final String OPTICS_SPEC_FQN = "org.higherkindedj.optics.annotations.OpticsSpec";

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(ImportOptics.class)) {
      note(
          "Processing @ImportOptics on: " + element + " (kind: " + element.getKind() + ")",
          element);

      if (element.getKind() == ElementKind.PACKAGE) {
        processPackageAnnotation((PackageElement) element);
      } else if (element.getKind() == ElementKind.INTERFACE) {
        // Check if it's a spec interface (extends OpticsSpec<S>)
        TypeElement typeElement = (TypeElement) element;
        boolean isSpec = isSpecInterface(typeElement);
        note("Is spec interface: " + isSpec, element);
        if (isSpec) {
          processSpecInterface(typeElement);
        } else {
          // Interface with class list (less common but supported)
          processTypeAnnotation(typeElement);
        }
      } else if (element.getKind() == ElementKind.CLASS) {
        processTypeAnnotation((TypeElement) element);
      } else {
        error("@ImportOptics can only be applied to packages or types.", element);
      }
    }
    return true;
  }

  /**
   * Checks if a type element is a spec interface (extends OpticsSpec).
   *
   * @param typeElement the type element to check
   * @return true if the interface extends OpticsSpec
   */
  private boolean isSpecInterface(TypeElement typeElement) {
    note("Checking interfaces for: " + typeElement.getQualifiedName(), typeElement);
    for (TypeMirror superInterface : typeElement.getInterfaces()) {
      note("  Found super interface: " + superInterface, typeElement);
      if (superInterface instanceof DeclaredType declaredType) {
        TypeElement interfaceElement = (TypeElement) declaredType.asElement();
        String fqn = interfaceElement.getQualifiedName().toString();
        note("  Interface FQN: " + fqn + " (expected: " + OPTICS_SPEC_FQN + ")", typeElement);
        if (fqn.equals(OPTICS_SPEC_FQN)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Processes a spec interface to generate optics.
   *
   * @param specInterface the spec interface extending OpticsSpec<S>
   */
  private void processSpecInterface(TypeElement specInterface) {
    note("processSpecInterface called for: " + specInterface.getQualifiedName(), specInterface);

    ImportOptics annotation = specInterface.getAnnotation(ImportOptics.class);

    String targetPackage = annotation.targetPackage();
    if (targetPackage.isEmpty()) {
      targetPackage =
          processingEnv.getElementUtils().getPackageOf(specInterface).getQualifiedName().toString();
    }
    note("Target package: " + targetPackage, specInterface);

    SpecInterfaceAnalyser analyser =
        new SpecInterfaceAnalyser(
            processingEnv.getTypeUtils(),
            processingEnv.getElementUtils(),
            processingEnv.getMessager());

    Optional<SpecAnalysis> analysisOpt = analyser.analyse(specInterface);
    note("Analysis result present: " + analysisOpt.isPresent(), specInterface);

    if (analysisOpt.isEmpty()) {
      // Errors already reported by analyser
      note("Analysis returned empty - not generating", specInterface);
      return;
    }

    note("Generating optics class...", specInterface);
    SpecInterfaceGenerator generator =
        new SpecInterfaceGenerator(processingEnv.getFiler(), processingEnv.getMessager());

    generator.generate(analysisOpt.get(), targetPackage);
    note("Generation complete", specInterface);
  }

  private void processPackageAnnotation(PackageElement packageElement) {
    ImportOptics annotation = packageElement.getAnnotation(ImportOptics.class);

    String targetPackage = annotation.targetPackage();
    if (targetPackage.isEmpty()) {
      targetPackage = packageElement.getQualifiedName().toString();
    }

    boolean allowMutable = annotation.allowMutable();

    List<TypeElement> classesToProcess = getClassArrayFromAnnotation(packageElement);
    for (TypeElement typeElement : classesToProcess) {
      processType(typeElement, targetPackage, allowMutable, packageElement);
    }
  }

  private void processTypeAnnotation(TypeElement typeElement) {
    ImportOptics annotation = typeElement.getAnnotation(ImportOptics.class);

    String targetPackage = annotation.targetPackage();
    if (targetPackage.isEmpty()) {
      targetPackage =
          processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
    }

    boolean allowMutable = annotation.allowMutable();

    List<TypeElement> classesToProcess = getClassArrayFromAnnotation(typeElement);
    for (TypeElement externalType : classesToProcess) {
      processType(externalType, targetPackage, allowMutable, typeElement);
    }
  }

  private void processType(
      TypeElement typeElement, String targetPackage, boolean allowMutable, Element sourceElement) {

    TypeKindAnalyser typeAnalyser = new TypeKindAnalyser(processingEnv.getTypeUtils());
    ExternalLensGenerator lensGenerator =
        new ExternalLensGenerator(processingEnv.getFiler(), processingEnv.getMessager());
    ExternalPrismGenerator prismGenerator =
        new ExternalPrismGenerator(processingEnv.getFiler(), processingEnv.getMessager());

    TypeAnalysis analysis = typeAnalyser.analyseType(typeElement);

    switch (analysis.typeKind()) {
      case RECORD -> lensGenerator.generateForRecord(analysis, targetPackage);

      case SEALED_INTERFACE -> prismGenerator.generateForSealedInterface(analysis, targetPackage);

      case ENUM -> prismGenerator.generateForEnum(analysis, targetPackage);

      case WITHER_CLASS -> {
        if (analysis.hasMutableFields() && !allowMutable) {
          error(
              "Type '"
                  + typeElement.getQualifiedName()
                  + "' has mutable fields (setters). "
                  + "Lens laws may not hold for mutable types. "
                  + "Either use allowMutable = true to acknowledge this limitation, "
                  + "or create a spec interface for explicit control.",
              sourceElement);
          return;
        }
        lensGenerator.generateForWitherClass(analysis, targetPackage);
      }

      case UNSUPPORTED -> {
        if (analysis.hasMutableFields()) {
          error(
              "Type '"
                  + typeElement.getQualifiedName()
                  + "' is a mutable class without wither methods. "
                  + "Cannot generate lenses for types that don't support immutable updates. "
                  + "Consider using a spec interface to define custom copy logic.",
              sourceElement);
        } else {
          error(
              "Type '"
                  + typeElement.getQualifiedName()
                  + "' is not a record, sealed interface, enum, or class with wither methods. "
                  + "Cannot determine how to generate optics for this type.",
              sourceElement);
        }
      }
    }
  }

  /**
   * Extracts the Class<?>[] value from an @ImportOptics annotation using the mirror API.
   *
   * <p>We cannot directly access the Class objects at compile time, so we use the annotation mirror
   * API to extract the TypeMirrors and convert them to TypeElements.
   */
  private List<TypeElement> getClassArrayFromAnnotation(Element element) {
    List<TypeElement> result = new ArrayList<>();

    // Find the @ImportOptics annotation mirror
    AnnotationMirror importOpticsMirror = null;
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      DeclaredType annotationType = mirror.getAnnotationType();
      TypeElement annotationElement = (TypeElement) annotationType.asElement();
      if (annotationElement
          .getQualifiedName()
          .contentEquals("org.higherkindedj.optics.annotations.ImportOptics")) {
        importOpticsMirror = mirror;
        break;
      }
    }

    if (importOpticsMirror == null) {
      return result;
    }

    // Find the "value" element
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        importOpticsMirror.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals("value")) {
        AnnotationValue value = entry.getValue();

        // The value is a List<AnnotationValue> where each item is a TypeMirror
        @SuppressWarnings("unchecked")
        List<? extends AnnotationValue> classValues =
            (List<? extends AnnotationValue>) value.getValue();

        for (AnnotationValue classValue : classValues) {
          TypeMirror typeMirror = (TypeMirror) classValue.getValue();
          TypeElement typeElement =
              (TypeElement) processingEnv.getTypeUtils().asElement(typeMirror);
          if (typeElement != null) {
            result.add(typeElement);
          }
        }
        break;
      }
    }

    return result;
  }

  private void error(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }

  private void note(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, msg, e);
  }
}
