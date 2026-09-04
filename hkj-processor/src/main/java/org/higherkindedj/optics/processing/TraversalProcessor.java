// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Annotation processor that generates Traversal optics for record types.
 *
 * <p>Each record component is offered to the {@link TraversableGenerator} SPI, and a method is
 * generated for every component a generator claims. A component that holds elements but reaches no
 * method — a {@link java.util.Collection} or {@link java.util.Map} subtype no generator on the
 * annotation processor path supports, or a claimed container whose element type cannot be read — is
 * reported as a note where it is declared, because the generated class compiles perfectly well
 * without it and the missing method would otherwise be found at the call site. A note rather than a
 * warning: the annotation has no per-component opt-out and a processor warning cannot be
 * suppressed, so a warning would fail a {@code -Werror} build with no remedy short of changing the
 * record. A component that is not a container at all, a {@code String} or an {@code int}, is passed
 * over silently: not generating for it is the expected outcome, not a gap.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateTraversals")
public class TraversalProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /** Creates a new TraversalProcessor. */
  public TraversalProcessor() {}

  private static final String TAG = "@GenerateTraversals";

  private final List<TraversableGenerator> generators = new ArrayList<>();

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    ServiceLoader.load(TraversableGenerator.class, getClass().getClassLoader())
        .forEach(generators::add);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(GenerateTraversals.class)) {
      if (element.getKind() != ElementKind.RECORD) {
        error("The @GenerateTraversals annotation can only be applied to records.", element);
        continue;
      }
      writeTraversalsFile((TypeElement) element);
    }
    return true;
  }

  @ExcludeFromJacocoGeneratedReport
  private void writeTraversalsFile(TypeElement element) {
    try {
      generateTraversalsFile(element);
    } catch (IOException e) {
      error("Could not generate traversals file: " + e.getMessage(), element);
    }
  }

  private void generateTraversalsFile(TypeElement recordElement) throws IOException {
    String recordName = recordElement.getSimpleName().toString();
    String defaultPackage =
        processingEnv.getElementUtils().getPackageOf(recordElement).getQualifiedName().toString();

    // Check for custom target package in annotation
    GenerateTraversals annotation = recordElement.getAnnotation(GenerateTraversals.class);
    String targetPackage = annotation.targetPackage();
    String packageName = targetPackage.isEmpty() ? defaultPackage : targetPackage;

    String traversalsClassName = recordName + "Traversals";

    // Define the ClassName for your custom @Generated annotation
    final ClassName generatedAnnotation =
        ClassName.get("org.higherkindedj.optics.annotations", "Generated");

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(traversalsClassName)
            .addOriginatingElement(recordElement)
            // Add the @Generated annotation to the class
            .addAnnotation(generatedAnnotation)
            .addJavadoc(
                "Generated optics for {@link $T}. Do not edit.", ClassName.get(recordElement))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    final GeneratorRegistry registry =
        GeneratorRegistry.of(generators, processingEnv.getMessager());
    for (RecordComponentElement component : recordElement.getRecordComponents()) {
      TraversableGenerator generator = registry.generatorFor(component.asType(), component);
      if (generator == null) {
        if (holdsElements(component.asType())) {
          noteNoTraversal(
              component,
              "No TraversableGenerator on the annotation processor path supports "
                  + ProcessorUtils.simpleTypeName(
                      processingEnv.getTypeUtils().erasure(component.asType())),
              "Declare the component as a supported container (List, Set, Collection, Map,"
                  + " Optional or an array), or put a TraversableGenerator for its type on the"
                  + " annotation processor path");
        }
        continue;
      }
      MethodSpec traversalMethod = createTraversalMethod(component, recordElement, generator);
      if (traversalMethod != null) {
        classBuilder.addMethod(traversalMethod);
      }
    }

    JavaFile.builder(packageName, classBuilder.build())
        .addFileComment("Generated by hkj-optics-processor. Do not edit.")
        .build()
        .writeTo(processingEnv.getFiler());
  }

  private MethodSpec createTraversalMethod(
      RecordComponentElement component, TypeElement recordElement, TraversableGenerator generator) {

    final String componentName = component.getSimpleName().toString();
    final ClassName recordClassName = ClassName.get(recordElement);
    final TypeName recordTypeName = recordTypeName(recordElement, recordClassName);

    final TypeName focusType;
    final TypeMirror componentType = component.asType();

    if (componentType instanceof ArrayType arrayType) {
      focusType = ProcessorUtils.typeNameOf(arrayType.getComponentType()).box();
    } else if (componentType instanceof DeclaredType declaredType) {
      if (declaredType.getTypeArguments().isEmpty()) {
        // A raw List, or a type the generator claims that declares no type parameter at all: the
        // remedy differs, the gap is the same.
        TypeElement container = (TypeElement) declaredType.asElement();
        String parameters =
            container.getTypeParameters().stream()
                .map(parameter -> parameter.getSimpleName().toString())
                .collect(Collectors.joining(", "));
        noteNoTraversal(
            component,
            ProcessorUtils.simpleTypeName(componentType)
                + " is written without a type argument, so there is no element type to focus",
            parameters.isEmpty()
                ? "The generator "
                    + generator.getClass().getCanonicalName()
                    + " claims a type that declares no type parameter; narrow its supports()"
                : "Give the component its type arguments, as in "
                    + container.getSimpleName()
                    + "<"
                    + parameters
                    + ">");
        return null;
      }

      // Use the SPI's getFocusTypeArgumentIndex() so any generator can declare which type
      // argument it traverses (V for Map/PMap, R for Either, A for Validated, the default
      // index 0 for collection-like types).
      int typeArgumentIndex = generator.getFocusTypeArgumentIndex();

      if (declaredType.getTypeArguments().size() <= typeArgumentIndex) {
        noteNoTraversal(
            component,
            "The generator "
                + generator.getClass().getCanonicalName()
                + " focuses type argument "
                + typeArgumentIndex
                + ", and "
                + ProcessorUtils.simpleTypeName(componentType)
                + " has only "
                + declaredType.getTypeArguments().size(),
            "Declare the component with a type that generator can focus, or correct the"
                + " generator's getFocusTypeArgumentIndex()");
        return null;
      }
      focusType =
          ProcessorUtils.resolvedTypeNameOf(declaredType.getTypeArguments().get(typeArgumentIndex));

    } else {
      noteNoTraversal(
          component,
          "The generator "
              + generator.getClass().getCanonicalName()
              + " claims the component, but a traversal can only be generated for a declared type"
              + " or an array, and "
              + ProcessorUtils.simpleTypeName(componentType)
              + " is neither",
          "Declare the component as a declared container type or an array, or narrow the"
              + " generator's supports()");
      return null;
    }

    final ParameterizedTypeName traversalTypeName =
        ParameterizedTypeName.get(ClassName.get(Traversal.class), recordTypeName, focusType);

    final CodeBlock modifyFBody =
        generator.generateModifyF(component, recordClassName, recordElement.getRecordComponents());

    // Create F extends WitnessArity<TypeArity.Unary>
    final ParameterizedTypeName witnessArityBound =
        ParameterizedTypeName.get(
            ClassName.get(WitnessArity.class), ClassName.get(TypeArity.class).nestedClass("Unary"));
    final TypeVariableName effect =
        TypeVariableName.get(ProcessorUtils.effectVariableName(recordElement), witnessArityBound);

    final TypeSpec traversalImpl =
        TypeSpec.anonymousClassBuilder("")
            .addSuperinterface(traversalTypeName)
            .addMethod(
                MethodSpec.methodBuilder("modifyF")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addTypeVariable(effect)
                    .addParameter(
                        ParameterizedTypeName.get(
                            ClassName.get(Function.class),
                            focusType,
                            ParameterizedTypeName.get(
                                ClassName.get(Kind.class), effect, focusType)),
                        "f")
                    .addParameter(recordTypeName, "source")
                    .addParameter(
                        ParameterizedTypeName.get(ClassName.get(Applicative.class), effect),
                        "applicative")
                    .returns(
                        ParameterizedTypeName.get(
                            ClassName.get(Kind.class), effect, recordTypeName))
                    .addCode(modifyFBody)
                    .build())
            .build();

    final MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(componentName);
    for (TypeParameterElement typeParameter : recordElement.getTypeParameters()) {
      methodBuilder.addTypeVariable(ProcessorUtils.typeVariableOf(typeParameter));
    }

    return methodBuilder
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addJavadoc(
            "Creates a {@link $T} for the {@code $L} field of a {@link $T}.\n"
                + "<p>This traversal focuses on all items within the {@code $L} collection,"
                + " allowing an effectful function\n"
                + "to be applied to each one.\n\n"
                + "@return A non-null {@code Traversal<$T, $T>}.",
            ClassName.get(Traversal.class),
            component.getSimpleName(),
            recordClassName,
            component.getSimpleName(),
            recordTypeName,
            focusType.box())
        .returns(traversalTypeName)
        .addStatement("return $L", traversalImpl)
        .build();
  }

  /**
   * The record's type as the generated method must write it: {@code Holder<T>} for a generic
   * record, and the class name itself for every other record.
   *
   * @param recordElement the annotated record
   * @param recordClassName the record's class name
   * @return the record's type name, carrying its type variables where it declares any
   */
  private TypeName recordTypeName(TypeElement recordElement, ClassName recordClassName) {
    List<? extends TypeParameterElement> typeParameters = recordElement.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return recordClassName;
    }
    return ParameterizedTypeName.get(
        recordClassName,
        typeParameters.stream().map(ProcessorUtils::typeVariableOf).toArray(TypeName[]::new));
  }

  /**
   * Whether {@code type} unmistakably holds elements: a declared type that is a {@link
   * java.util.Collection} or a {@link java.util.Map} by erasure. A bare {@link Iterable} is not
   * counted — {@code java.nio.file.Path} implements it — so a component of such a type is passed
   * over as any other non-container is, and so is a type variable, even one bounded by a
   * collection. An unresolved type is kept out on purpose: {@code isSubtype} answers true for an
   * error type, and the author already has javac's error for it.
   */
  private boolean holdsElements(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return false;
    }
    Types types = processingEnv.getTypeUtils();
    TypeMirror erased = types.erasure(type);
    return isSubtypeOf(erased, "java.util.Collection") || isSubtypeOf(erased, "java.util.Map");
  }

  /** Only ever asked about the JDK's own types, which resolve in every round, so unguarded. */
  private boolean isSubtypeOf(TypeMirror erased, String qualifiedName) {
    Types types = processingEnv.getTypeUtils();
    TypeMirror supertype =
        types.erasure(processingEnv.getElementUtils().getTypeElement(qualifiedName).asType());
    return types.isSubtype(erased, supertype);
  }

  /**
   * Reports that no traversal is generated for {@code component}, in the what/why/fix format. A
   * note rather than a warning or an error: the record and its generated class are sound without
   * the method, the author may have wanted traversals for the other components alone, and there is
   * no per-component opt-out that would let a warning be answered under {@code -Werror}.
   */
  private void noteNoTraversal(RecordComponentElement component, String why, String fix) {
    Diagnostics.note(
        processingEnv.getMessager(),
        component,
        TAG,
        "no traversal was generated for component '"
            + component.getEnclosingElement().getSimpleName()
            + "."
            + component.getSimpleName()
            + "' of type "
            + ProcessorUtils.simpleTypeName(component.asType()),
        why,
        fix);
  }

  private void error(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }
}
