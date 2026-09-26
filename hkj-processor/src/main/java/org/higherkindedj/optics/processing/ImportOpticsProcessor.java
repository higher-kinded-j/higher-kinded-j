// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import java.util.ArrayList;
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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
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
 * <p>A spec declares {@code OpticsSpec<S>} and each of its optic methods itself.
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

  private static final String OPTICS_SPEC_FQN = "org.higherkindedj.optics.annotations.OpticsSpec";

  private static final String IMPORT_OPTICS_FQN =
      "org.higherkindedj.optics.annotations.ImportOptics";

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(ImportOptics.class)) {
      switch (element.getKind()) {
        case PACKAGE -> processPackageAnnotation((PackageElement) element);
        case INTERFACE -> processInterface((TypeElement) element);
        case CLASS -> processClass((TypeElement) element);
        default ->
            Diagnostics.error(
                processingEnv.getMessager(),
                element,
                "@ImportOptics",
                "cannot be applied to '" + element.getSimpleName() + "'.",
                "It is read on a package-info.java, or on a class or interface that lists the"
                    + " types to import or extends OpticsSpec<S>.",
                "Move the annotation to a package-info.java, a class or an interface.");
      }
    }
    return true;
  }

  /**
   * An interface is a spec when OpticsSpec is one of its own super-interfaces, and otherwise lists
   * the classes to import. One that reaches OpticsSpec only through another interface is refused,
   * rather than read for a class list it was not written to have.
   */
  private void processInterface(TypeElement anInterface) {
    if (isSpecInterface(anInterface)) {
      processSpecInterface(anInterface);
    } else if (!refusesIndirectSpec(anInterface)) {
      processTypeAnnotation(anInterface);
    }
  }

  /**
   * A class that implements OpticsSpec goes to the spec analyser, which refuses it: a spec is read
   * for its abstract methods, and only an interface is. Any other class lists the classes to
   * import.
   */
  private void processClass(TypeElement aClass) {
    if (opticsSpecReachedFrom(aClass.asType()) != null) {
      processSpecInterface(aClass);
    } else {
      processTypeAnnotation(aClass);
    }
  }

  /**
   * The {@code OpticsSpec} a type reaches through its supertypes, under the type arguments it is
   * reached with, or null where it reaches none. The type itself is not asked.
   */
  private DeclaredType opticsSpecReachedFrom(TypeMirror type) {
    for (TypeMirror supertype : processingEnv.getTypeUtils().directSupertypes(type)) {
      // The supertypes of a class or interface are classes or interfaces.
      DeclaredType declared = (DeclaredType) supertype;
      if (((TypeElement) declared.asElement()).getQualifiedName().contentEquals(OPTICS_SPEC_FQN)) {
        return declared;
      }
      DeclaredType reached = opticsSpecReachedFrom(declared);
      if (reached != null) {
        return reached;
      }
    }
    return null;
  }

  /**
   * Reports an interface that reaches {@code OpticsSpec} only through another interface, and
   * returns whether it did. The fix names the source type where the path to {@code OpticsSpec} pins
   * one.
   */
  private boolean refusesIndirectSpec(TypeElement anInterface) {
    for (TypeMirror superInterface : anInterface.getInterfaces()) {
      DeclaredType reached = opticsSpecReachedFrom(superInterface);
      if (reached != null) {
        String name = anInterface.getSimpleName().toString();
        String through = ProcessorUtils.simpleTypeName(superInterface);
        List<? extends TypeMirror> arguments = reached.getTypeArguments();
        String fix =
            arguments.size() == 1 && arguments.getFirst().getKind() == TypeKind.DECLARED
                ? "Declare OpticsSpec<"
                    + ProcessorUtils.simpleTypeName(arguments.getFirst())
                    + "> on '"
                    + name
                    + "' itself: 'interface "
                    + name
                    + " extends OpticsSpec<"
                    + ProcessorUtils.simpleTypeName(arguments.getFirst())
                    + ">, "
                    + through
                    + "'."
                : "Declare OpticsSpec<S> on '"
                    + name
                    + "' itself, naming the type its optics are for.";
        Diagnostics.error(
            processingEnv.getMessager(),
            anInterface,
            "@ImportOptics",
            "'" + name + "' extends OpticsSpec only through '" + through + "'.",
            "A spec interface is read from the OpticsSpec<S> it declares itself, and reaching it"
                + " through another interface is not supported yet, so nothing would be generated.",
            fix);
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if a type element is a spec interface (extends OpticsSpec).
   *
   * @param typeElement the type element to check
   * @return true if the interface extends OpticsSpec
   */
  private boolean isSpecInterface(TypeElement typeElement) {
    for (TypeMirror superInterface : typeElement.getInterfaces()) {
      // Super-interfaces returned by getInterfaces() are always declared types.
      DeclaredType declaredType = (DeclaredType) superInterface;
      TypeElement interfaceElement = (TypeElement) declaredType.asElement();
      String fqn = interfaceElement.getQualifiedName().toString();
      if (fqn.equals(OPTICS_SPEC_FQN)) {
        return true;
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
    // A class is refused by the analyser, and is told to become an interface before anything else.
    boolean listsClasses =
        specInterface.getKind() == ElementKind.INTERFACE && refusesClassList(specInterface);

    ImportOptics annotation = specInterface.getAnnotation(ImportOptics.class);

    String targetPackage = annotation.targetPackage();
    if (targetPackage.isEmpty()) {
      targetPackage =
          processingEnv.getElementUtils().getPackageOf(specInterface).getQualifiedName().toString();
    }

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
        Reachability.companion(
            targetPackage,
            processingEnv
                .getElementUtils()
                .getPackageOf(specInterface)
                .getQualifiedName()
                .toString()),
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
   * Reports a spec interface that also lists classes to import, and returns whether it did. A spec
   * reads its source type from {@code OpticsSpec<S>}, so a class list beside it would go unread.
   */
  private boolean refusesClassList(TypeElement specInterface) {
    if (listedClasses(specInterface).isEmpty()) {
      return false;
    }
    AnnotationMirror importOptics = ProcessorUtils.findAnnotation(specInterface, IMPORT_OPTICS_FQN);
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.ERROR,
            Diagnostics.format(
                "@ImportOptics",
                "spec interface '"
                    + specInterface.getSimpleName()
                    + "' also lists classes to import.",
                "A spec generates the optics its methods declare for the type its OpticsSpec<S>"
                    + " names, and does not read a class list, so the classes listed would get none.",
                "Move the class list to a package-info.java or to another class or interface, or"
                    + " remove it."),
            specInterface,
            importOptics,
            ProcessorUtils.getAnnotationValue(importOptics, "value"));
    return true;
  }

  private void processPackageAnnotation(PackageElement packageElement) {
    ImportOptics annotation = packageElement.getAnnotation(ImportOptics.class);

    String targetPackage = annotation.targetPackage();
    if (targetPackage.isEmpty()) {
      targetPackage = packageElement.getQualifiedName().toString();
    }

    boolean allowMutable = annotation.allowMutable();

    List<TypeElement> classesToProcess = typesToImport(packageElement);
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

    List<TypeElement> classesToProcess = typesToImport(typeElement);
    for (TypeElement externalType : classesToProcess) {
      processType(externalType, targetPackage, allowMutable, typeElement);
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
   * The class literals the {@code @ImportOptics} on {@code element} lists, as written: none where
   * it lists none, or where the element carries no {@code @ImportOptics} at all.
   *
   * <p>The literals are read through the mirror API, since a {@code Class} object for a type being
   * compiled does not exist yet.
   */
  // Package-private for tests.
  List<? extends AnnotationValue> listedClasses(Element element) {
    AnnotationMirror importOptics = ProcessorUtils.findAnnotation(element, IMPORT_OPTICS_FQN);
    AnnotationValue value =
        importOptics == null ? null : ProcessorUtils.getAnnotationValue(importOptics, "value");
    if (value == null) {
      return List.of();
    }
    @SuppressWarnings("unchecked") // a Class<?>[] value holds its entries as a list
    List<? extends AnnotationValue> entries = (List<? extends AnnotationValue>) value.getValue();
    return entries;
  }

  /**
   * The types an importer's class list names, in the order it lists them. An entry naming no class,
   * interface, record or enum is reported where it is written, and a list naming nothing at all is
   * reported as importing nothing.
   */
  private List<TypeElement> typesToImport(Element importer) {
    List<? extends AnnotationValue> listed = listedClasses(importer);
    AnnotationMirror importOptics = ProcessorUtils.findAnnotation(importer, IMPORT_OPTICS_FQN);
    if (listed.isEmpty()) {
      reportNothingImported(importer, importOptics);
      return List.of();
    }
    List<TypeElement> types = new ArrayList<>();
    for (AnnotationValue entry : listed) {
      TypeMirror type = (TypeMirror) entry.getValue();
      if (processingEnv.getTypeUtils().asElement(type) instanceof TypeElement typeElement) {
        types.add(typeElement);
      } else {
        String literal = ProcessorUtils.simpleTypeName(type) + ".class";
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.ERROR,
                Diagnostics.format(
                    "@ImportOptics",
                    "'" + literal + "' names no class, interface, record or enum.",
                    "Optics are generated from a type's declaration, its components, subtypes,"
                        + " constants or withers, and a primitive, an array or void has none.",
                    "Remove '" + literal + "' from the list."),
                importer,
                importOptics,
                entry);
      }
    }
    return types;
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
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.WARNING,
            Diagnostics.format(
                "@ImportOptics",
                name + " lists no classes to import, so nothing is generated.",
                anInterface
                    ? "Optics are generated for each class the annotation lists, and for the"
                        + " source type of an interface extending OpticsSpec<S>."
                    : "Optics are generated for each class the annotation lists.",
                anInterface
                    ? "List the classes to import, as @ImportOptics({Order.class}), extend"
                        + " OpticsSpec<S> to make it a spec interface, or remove the annotation."
                    : "List the classes to import, as @ImportOptics({Order.class}), or remove the"
                        + " annotation."),
            importer,
            importOptics);
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
