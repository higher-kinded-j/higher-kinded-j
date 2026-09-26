// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.processing.external.ExternalLensGenerator;
import org.higherkindedj.optics.processing.external.ExternalPrismGenerator;
import org.higherkindedj.optics.processing.external.SpecAnalysis;
import org.higherkindedj.optics.processing.external.SpecInterfaceAnalyser;
import org.higherkindedj.optics.processing.external.SpecInterfaceGenerator;
import org.higherkindedj.optics.processing.external.TypeAnalysis;
import org.higherkindedj.optics.processing.external.TypeKindAnalyser;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.Reachability;
import org.higherkindedj.optics.processing.util.Reachability.Crossing;

/**
 * Annotation processor for {@link ImportOptics}.
 *
 * <p>This processor generates optics for external types that you do not own. It supports two modes:
 *
 * <h2>1. Package/Type Annotation with Class List</h2>
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
 * <h2>2. Spec Interface</h2>
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
 *
 * <p>A spec declares {@code OpticsSpec<S>} and each of its optic methods itself: reading either
 * through another interface is not supported yet.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.ImportOptics")
public class ImportOpticsProcessor extends AbstractProcessor {

  /** What makes a class one with wither methods, for the refusals that find none. */
  private static final String WITHER_METHODS_ARE =
      "A class has wither methods when a public 'withX' hands back the class, under its own type"
          + " arguments, as a subtype, or as a retag the call infers back to it, beside a public"
          + " 'x()', 'getX()' or 'isX()' returning exactly what 'withX' takes.";

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /** Creates a new ImportOpticsProcessor. */
  public ImportOpticsProcessor() {}

  private static final String IMPORT_OPTICS_FQN =
      "org.higherkindedj.optics.annotations.ImportOptics";

  /** The importers met but not processed yet: arriving this round, or waiting for a type. */
  private final Set<WaitingImporters.Key> unprocessed = new LinkedHashSet<>();

  /**
   * Processes each importer once every type it names resolves. One naming a type another processor
   * has not written yet waits for a later round (see {@link WaitingImporters}).
   */
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Elements elements = processingEnv.getElementUtils();
    roundEnv
        .getElementsAnnotatedWith(ImportOptics.class)
        .forEach(element -> unprocessed.add(WaitingImporters.Key.of(elements, element)));
    // A file written in the last round draws a warning, and an importer still waiting names a type
    // that never resolved, which javac reports itself.
    if (roundEnv.processingOver()) {
      return true;
    }
    for (WaitingImporters.Key key : List.copyOf(unprocessed)) {
      Element element = key.in(elements);
      // ImportOptics is not @Inherited, so every element met carries it itself.
      AnnotationMirror importOptics = ProcessorUtils.findAnnotation(element, IMPORT_OPTICS_FQN);
      if (!WaitingImporters.waits(
          elements, processingEnv.getTypeUtils(), element, listedClasses(importOptics))) {
        unprocessed.remove(key);
        importFrom(element, importOptics);
      }
    }
    return true;
  }

  private void importFrom(Element element, AnnotationMirror importOptics) {
    switch (element.getKind()) {
      case PACKAGE -> processPackage((PackageElement) element, importOptics);
      case INTERFACE, CLASS -> processImporter((TypeElement) element, importOptics);
      default -> reportPlacement(element);
    }
  }

  /**
   * Refuses {@code @ImportOptics} on a record, an enum or an annotation interface, naming which: to
   * the language each is a kind of class or interface, so the kind found is what tells them apart.
   */
  private void reportPlacement(Element element) {
    String kind =
        switch (element.getKind()) {
          case RECORD -> "record";
          case ENUM -> "enum";
          default -> "annotation interface";
        };
    Diagnostics.error(
        processingEnv.getMessager(),
        element,
        "@ImportOptics",
        "cannot be applied to " + kind + " '" + element.getSimpleName() + "'.",
        "It is read on a package-info.java, on a class or interface listing the types to import,"
            + " or on an interface extending OpticsSpec<S>, and a "
            + kind
            + " is none of those.",
        "Move the annotation to a package-info.java, or to a class or interface that is not a"
            + " record, an enum or an annotation interface.");
  }

  private void processPackage(PackageElement pkg, AnnotationMirror importOptics) {
    List<AnnotationValue> listed = listedClasses(importOptics);
    if (listed.isEmpty()) {
      reportNothingImported(pkg, importOptics);
    } else {
      importListed(pkg, importOptics, listed, pkg.getQualifiedName().toString());
    }
  }

  /**
   * An interface declaring {@code OpticsSpec<S>} itself is a spec, and any other class or interface
   * listing classes imports them. One listing none that reaches {@code OpticsSpec} anyway goes to
   * the analyser, which says why it cannot be read as a spec.
   */
  private void processImporter(TypeElement type, AnnotationMirror importOptics) {
    List<AnnotationValue> listed = listedClasses(importOptics);
    if (SpecInterfaceAnalyser.isSpecInterface(type)) {
      processSpecInterface(type, importOptics, listed);
    } else if (!listed.isEmpty()) {
      importListed(type, importOptics, listed, packageOf(type));
    } else if (SpecInterfaceAnalyser.reachesOpticsSpec(processingEnv.getTypeUtils(), type)) {
      processSpecInterface(type, importOptics, listed);
    } else {
      reportNothingImported(type, importOptics);
    }
  }

  private String packageOf(Element element) {
    return processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
  }

  /** The package the annotation names for what it generates, or {@code defaultPackage}. */
  private static String targetPackage(AnnotationMirror importOptics, String defaultPackage) {
    String written = ProcessorUtils.getAnnotationString(importOptics, "targetPackage", "");
    return written.isEmpty() ? defaultPackage : written;
  }

  /**
   * Processes a spec interface to generate optics, or reports why it cannot: a class, or an
   * interface reaching {@code OpticsSpec} only through another, is refused by the analyser.
   *
   * @param specInterface the type read as a spec
   * @param importOptics its {@code @ImportOptics}
   * @param listed the classes the annotation lists, which a spec does not read
   */
  private void processSpecInterface(
      TypeElement specInterface, AnnotationMirror importOptics, List<AnnotationValue> listed) {
    boolean listsClasses = !listed.isEmpty();
    if (listsClasses) {
      reportClassList(specInterface, importOptics);
    }

    String targetPackage = targetPackage(importOptics, packageOf(specInterface));

    SpecInterfaceAnalyser analyser =
        new SpecInterfaceAnalyser(
            processingEnv.getTypeUtils(),
            processingEnv.getElementUtils(),
            processingEnv.getMessager());

    Optional<SpecAnalysis> analysisOpt = analyser.analyse(specInterface, targetPackage);

    if (analysisOpt.isEmpty() || listsClasses) {
      // Errors already reported, by the analyser or for the class list
      return;
    }

    SpecAnalysis analysis = analysisOpt.get();
    // The generated class implements the spec and writes each optic's type out in full, the source
    // type and every focus with it.
    if (!Reachability.check(
        processingEnv,
        "@ImportOptics",
        specInterface,
        Reachability.companion(targetPackage, packageOf(specInterface)),
        Stream.concat(
            Stream.of(
                Reachability.declared(specInterface),
                Crossing.over(
                    "source type '" + ProcessorUtils.simpleTypeName(analysis.sourceType()) + "'",
                    analysis.sourceType())),
            analysis.opticMethods().stream()
                .map(
                    optic ->
                        Crossing.member(
                            "optic '" + optic.methodName() + "'",
                            optic.method().getReturnType()))))) {
      return;
    }

    SpecInterfaceGenerator generator =
        new SpecInterfaceGenerator(processingEnv.getFiler(), processingEnv.getMessager());

    generator.generate(analysis, targetPackage, specInterface);
  }

  /**
   * Reports a spec interface that also lists classes to import. A spec reads its source type from
   * {@code OpticsSpec<S>}, so a class list beside it would go unread.
   */
  private void reportClassList(TypeElement specInterface, AnnotationMirror importOptics) {
    Diagnostics.reportAt(
        processingEnv.getMessager(),
        Diagnostic.Kind.ERROR,
        specInterface,
        importOptics,
        ProcessorUtils.getAnnotationValue(importOptics, "value"),
        "@ImportOptics",
        "'"
            + specInterface.getSimpleName()
            + "' extends "
            + ProcessorUtils.simpleTypeName(SpecInterfaceAnalyser.declaredOpticsSpec(specInterface))
            + " and also lists classes to import.",
        "A spec generates the optics its methods declare for the type its OpticsSpec<S> names,"
            + " and does not read a class list, so the classes listed would get none.",
        "Move the class list to a package-info.java or to another class or interface, or remove"
            + " it.");
  }

  /** Generates optics for each class an importer lists. */
  private void importListed(
      Element importer,
      AnnotationMirror importOptics,
      List<AnnotationValue> listed,
      String defaultPackage) {
    String targetPackage = targetPackage(importOptics, defaultPackage);
    // A boolean element's value prints as true or false.
    boolean allowMutable =
        Boolean.parseBoolean(
            ProcessorUtils.getAnnotationString(importOptics, "allowMutable", "false"));
    for (TypeElement imported : typesToImport(importer, importOptics, listed)) {
      processType(imported, targetPackage, allowMutable, importer);
    }
  }

  /**
   * The types the companion of one imported type names: a record and its components, a sealed
   * interface and its permitted subtypes, an enum, or a class with a wither and the fields it
   * reads. A type the processor refuses names nothing.
   */
  private Stream<Crossing> importedCrossings(TypeElement type, TypeAnalysis analysis) {
    return switch (analysis.typeKind()) {
      case RECORD -> Reachability.record(type, type.getRecordComponents());
      case SEALED_INTERFACE -> Reachability.sum(processingEnv.getTypeUtils(), type);
      case ENUM -> Stream.of(Reachability.declared(type));
      case WITHER_CLASS ->
          Stream.of(
                  Stream.of(Reachability.declared(type)),
                  Reachability.bounds(type),
                  analysis.fields().stream()
                      .map(
                          field ->
                              Crossing.member(
                                  "field '" + field.name() + "' of '" + type.getSimpleName() + "'",
                                  field.type())))
              .flatMap(Function.identity());
      case UNSUPPORTED -> Stream.empty();
    };
  }

  private void processType(
      TypeElement typeElement, String targetPackage, boolean allowMutable, Element sourceElement) {

    TypeKindAnalyser typeAnalyser = new TypeKindAnalyser(processingEnv.getTypeUtils());
    ExternalLensGenerator lensGenerator =
        new ExternalLensGenerator(processingEnv.getFiler(), processingEnv.getMessager());
    ExternalPrismGenerator prismGenerator =
        new ExternalPrismGenerator(processingEnv.getFiler(), processingEnv.getMessager());

    // Asked before the shape, not after a wither pairs: read from a class file, a hidden
    // parameter's own signatures resolve both names to the inner one, so none would pair.
    if (hidesAnEnclosingTypeParameter(sourceElement, typeElement)) {
      return;
    }

    TypeAnalysis analysis = typeAnalyser.analyseType(typeElement);
    if (!Reachability.check(
        processingEnv,
        "@ImportOptics",
        sourceElement,
        Reachability.companion(
            targetPackage,
            processingEnv
                .getElementUtils()
                .getPackageOf(sourceElement)
                .getQualifiedName()
                .toString()),
        importedCrossings(typeElement, analysis))) {
      return;
    }

    switch (analysis.typeKind()) {
      case RECORD -> lensGenerator.generateForRecord(analysis, targetPackage, sourceElement);

      case SEALED_INTERFACE ->
          prismGenerator.generateForSealedInterface(analysis, targetPackage, sourceElement);

      case ENUM -> prismGenerator.generateForEnum(analysis, targetPackage, sourceElement);

      case WITHER_CLASS -> {
        if (analysis.hasMutableFields() && !allowMutable) {
          Diagnostics.error(
              processingEnv.getMessager(),
              sourceElement,
              "@ImportOptics",
              "type '" + typeElement.getQualifiedName() + "' has mutable fields (setters).",
              "Lens laws may not hold for mutable types.",
              "Use allowMutable = true to acknowledge the limitation, or create an OpticsSpec"
                  + " interface for explicit control.");
          return;
        }
        lensGenerator.generateForWitherClass(analysis, targetPackage, sourceElement);
      }

      case UNSUPPORTED -> {
        if (analysis.hasMutableFields()) {
          Diagnostics.error(
              processingEnv.getMessager(),
              sourceElement,
              "@ImportOptics",
              "type '"
                  + typeElement.getQualifiedName()
                  + "' is a mutable class without wither"
                  + " methods.",
              "Lenses require immutable updates, and no copy mechanism was found. "
                  + WITHER_METHODS_ARE,
              "Define an OpticsSpec interface with custom copy logic, or add wither methods.");
        } else {
          Diagnostics.error(
              processingEnv.getMessager(),
              sourceElement,
              "@ImportOptics",
              "type '"
                  + typeElement.getQualifiedName()
                  + "' is not a record, sealed interface, enum, or class with wither methods.",
              "The processor cannot determine how to generate optics for this shape. "
                  + WITHER_METHODS_ARE,
              "Make the type one of those shapes, or define an OpticsSpec interface for it.");
        }
      }
    }
  }

  /**
   * The class literals an {@code @ImportOptics} lists, as written, or none where it lists none.
   *
   * <p>The literals are read through the mirror API, since a {@code Class} object for a type being
   * compiled does not exist yet, and reading the annotation reflectively fails outright on one that
   * does not resolve.
   */
  private static List<AnnotationValue> listedClasses(AnnotationMirror importOptics) {
    AnnotationValue value = ProcessorUtils.getAnnotationValue(importOptics, "value");
    // A Class<?>[] value is a list of its entries, even when written as a single literal.
    return value == null
        ? List.of()
        : ((List<?>) value.getValue()).stream().map(AnnotationValue.class::cast).toList();
  }

  /**
   * The types an importer's class list names, in the order it lists them. An entry naming no class,
   * interface, record or enum is reported where it is written.
   */
  private List<TypeElement> typesToImport(
      Element importer, AnnotationMirror importOptics, List<AnnotationValue> listed) {
    List<TypeElement> types = new ArrayList<>();
    for (AnnotationValue entry : listed) {
      // Every literal resolves by now: an importer listing one that does not waits.
      TypeMirror type = (TypeMirror) entry.getValue();
      if (processingEnv.getTypeUtils().asElement(type) instanceof TypeElement typeElement) {
        types.add(typeElement);
      } else {
        String literal = ProcessorUtils.simpleTypeName(type) + ".class";
        String names =
            switch (type.getKind()) {
              case ARRAY -> "an array type";
              case VOID -> "void";
              default -> "a primitive type";
            };
        Diagnostics.reportAt(
            processingEnv.getMessager(),
            Diagnostic.Kind.ERROR,
            importer,
            importOptics,
            entry,
            "@ImportOptics",
            "'" + literal + "' names " + names + ", which has no optics to import.",
            "Optics are generated from the declaration of a class, interface, record or enum.",
            "Remove '" + literal + "' from the list.");
      }
    }
    return List.copyOf(types);
  }

  /**
   * Warns that an importer lists no classes. Nothing is generated for it, and silence would leave
   * the author to find that out from a missing class.
   */
  private void reportNothingImported(Element importer, AnnotationMirror importOptics) {
    boolean anInterface = importer.getKind() == ElementKind.INTERFACE;
    String name =
        importer instanceof PackageElement pkg
            ? "package '" + pkg.getQualifiedName() + "'"
            : "'" + importer.getSimpleName() + "'";
    Diagnostics.reportAt(
        processingEnv.getMessager(),
        Diagnostic.Kind.WARNING,
        importer,
        importOptics,
        null,
        "@ImportOptics",
        name + " lists no classes to import, so nothing is generated.",
        anInterface
            ? "Optics are generated for each class the annotation lists, and for the source type"
                + " of an interface extending OpticsSpec<S>."
            : "Optics are generated for each class the annotation lists.",
        anInterface
            ? "List the classes to import, as @ImportOptics({Order.class}), extend OpticsSpec<S>"
                + " to make it a spec interface, or remove the annotation."
            : "List the classes to import, as @ImportOptics({Order.class}), or remove the"
                + " annotation.");
  }

  /**
   * Reports an inner class that declares a type parameter under a name one of its enclosing classes
   * already uses, and returns whether it did.
   *
   * <p>Inside the inner class the enclosing parameter is hidden, which is harmless there. The
   * generated lenses name the class under both, {@code Outer<T>.In<T>}, and declare every parameter
   * in scope on each method, where one method cannot declare two of the same name. A top-level or
   * static type has only its own parameters in scope, whose names are distinct.
   *
   * @param sourceElement the annotated element, for error reporting
   * @param type the imported type
   * @return true when a parameter hides another and an error was reported
   */
  private boolean hidesAnEnclosingTypeParameter(Element sourceElement, TypeElement type) {
    List<TypeParameterElement> inScope = ProcessorUtils.typeParametersInScope(type);
    for (int later = 1; later < inScope.size(); later++) {
      TypeParameterElement inner = inScope.get(later);
      for (int earlier = 0; earlier < later; earlier++) {
        TypeParameterElement outer = inScope.get(earlier);
        if (outer.getSimpleName().contentEquals(inner.getSimpleName())) {
          Diagnostics.error(
              processingEnv.getMessager(),
              sourceElement,
              "@ImportOptics",
              "type '"
                  + type.getQualifiedName()
                  + "' names the type parameter '"
                  + inner.getSimpleName()
                  + "' of '"
                  + inner.getGenericElement().getSimpleName()
                  + "', which hides the '"
                  + outer.getSimpleName()
                  + "' of its enclosing class '"
                  + outer.getGenericElement().getSimpleName()
                  + "'.",
              "The generated lenses name it as '"
                  + ProcessorUtils.simpleTypeName(type.asType())
                  + "' and declare every one of those parameters on each method, and a method"
                  + " cannot declare two type parameters with the same name.",
              "Import it through an OpticsSpec interface instead, which names the type under type"
                  + " parameters of its own, and give each field a @Wither lens.");
          return true;
        }
      }
    }
    return false;
  }
}
