// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.higherkindedj.hkt.effect.annotation.Handles;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;

/**
 * Annotation processor for {@link ComposeEffects @ComposeEffects} and {@link Handles @Handles}.
 *
 * <p>For {@code @ComposeEffects}, generates a Support class with:
 *
 * <ul>
 *   <li>Static Inject factory methods for all effect combinations
 *   <li>A {@code BoundSet} record containing Bound instances for all effects
 *   <li>A {@code functor()} method returning the composed Functor
 * </ul>
 *
 * <p>For {@code @Handles}, validates that interpreter classes have handler methods for all
 * operations in the referenced effect algebra.
 *
 * @see ComposeEffects
 * @see Handles
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({
  "org.higherkindedj.hkt.effect.annotation.ComposeEffects",
  "org.higherkindedj.hkt.effect.annotation.Handles"
})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class ComposeEffectsProcessor extends AbstractProcessor {

  /** Creates a new ComposeEffectsProcessor. */
  public ComposeEffectsProcessor() {}

  private static final ClassName EITHERF_KIND =
      ClassName.get("org.higherkindedj.hkt.eitherf", "EitherFKind");
  private static final ClassName EITHERF_FUNCTOR =
      ClassName.get("org.higherkindedj.hkt.eitherf", "EitherFFunctor");
  private static final ClassName INJECT = ClassName.get("org.higherkindedj.hkt.inject", "Inject");
  private static final ClassName INJECT_INSTANCES =
      ClassName.get("org.higherkindedj.hkt.inject", "InjectInstances");
  private static final ClassName FUNCTOR = ClassName.get("org.higherkindedj.hkt", "Functor");
  private static final ClassName KIND = ClassName.get("org.higherkindedj.hkt", "Kind");
  private static final ClassName TYPE_ARITY = ClassName.get("org.higherkindedj.hkt", "TypeArity");
  private static final ClassName WITNESS_ARITY =
      ClassName.get("org.higherkindedj.hkt", "WitnessArity");
  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");
  private static final ClassName NULL_MARKED =
      ClassName.get("org.jspecify.annotations", "NullMarked");
  private static final ClassName OBJECTS = ClassName.get("java.util", "Objects");
  private static final ClassName FREE = ClassName.get("org.higherkindedj.hkt.free", "Free");

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    processComposeEffects(roundEnv);
    processHandles(roundEnv);
    return true;
  }

  // =========================================================================
  // @ComposeEffects processing
  // =========================================================================

  private void processComposeEffects(RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(ComposeEffects.class)) {
      if (element.getKind() != ElementKind.RECORD) {
        error("@ComposeEffects can only annotate record types", element);
        continue;
      }

      TypeElement typeElement = (TypeElement) element;
      List<? extends RecordComponentElement> components = typeElement.getRecordComponents();

      if (components.size() < 2 || components.size() > 4) {
        error(
            "@ComposeEffects requires 2-4 effect algebra fields, got " + components.size(),
            element);
        continue;
      }

      // Check for duplicates using proper type comparison
      List<RecordComponentElement> seenComponents = new ArrayList<>();
      boolean hasDuplicates = false;
      for (RecordComponentElement comp : components) {
        for (RecordComponentElement prev : seenComponents) {
          if (processingEnv.getTypeUtils().isSameType(comp.asType(), prev.asType())) {
            error(
                "Duplicate effect algebra type: "
                    + comp.asType()
                    + " (field '"
                    + comp.getSimpleName()
                    + "'). Each effect algebra may only appear once.",
                comp);
            hasDuplicates = true;
            break;
          }
        }
        seenComponents.add(comp);
      }
      if (hasDuplicates) continue;

      writeSupport(typeElement, components, element);
    }
  }

  @ExcludeFromJacocoGeneratedReport
  private void writeSupport(
      TypeElement typeElement, List<? extends RecordComponentElement> components, Element element) {
    try {
      ComposeEffects ann = typeElement.getAnnotation(ComposeEffects.class);
      String packageName = resolveTargetPackage(typeElement, ann);
      String baseName = typeElement.getSimpleName().toString();

      generateSupport(packageName, baseName, typeElement, components);
    } catch (IOException e) {
      error(
          "Failed to generate support class for "
              + typeElement.getQualifiedName()
              + ": "
              + e.getMessage(),
          element);
    }
  }

  private void generateSupport(
      String packageName,
      String baseName,
      TypeElement source,
      List<? extends RecordComponentElement> components)
      throws IOException {
    String supportName = baseName + "Support";
    int arity = components.size();

    // Extract effect names from field names
    List<String> effectNames = new ArrayList<>();
    for (RecordComponentElement comp : components) {
      effectNames.add(comp.getSimpleName().toString());
    }

    TypeVariableName typeF =
        TypeVariableName.get(
            "F",
            ParameterizedTypeName.get(
                WITNESS_ARITY, ClassName.get("org.higherkindedj.hkt", "TypeArity", "Unary")));

    TypeSpec.Builder supportBuilder =
        TypeSpec.classBuilder(supportName)
            .addAnnotation(NULL_MARKED)
            .addAnnotation(GENERATED)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    // Generate inject factory methods for each effect
    // For 2 effects: injectLeft() and injectRight()
    // For 3: injectLeft(), injectRightThen(injectLeft()), injectRightThen(injectRight())
    // For 4: similar nesting
    for (int i = 0; i < arity; i++) {
      supportBuilder.addMethod(generateInjectMethod(effectNames.get(i), i, arity));
    }

    // Generate functor() method
    supportBuilder.addMethod(generateFunctorMethod(effectNames, arity));

    // Generate BoundSet record
    supportBuilder.addType(generateBoundSetRecord(effectNames, arity));

    supportBuilder.addJavadoc(
        "Support infrastructure for the {@link $L} effect composition.\n\n"
            + "<p>Provides Inject instances, a composed Functor, and a BoundSet record\n"
            + "for using the composed effects in Free monad programs.\n\n"
            + "@see $L\n",
        baseName,
        baseName);

    JavaFile.builder(packageName, supportBuilder.build())
        .addFileComment("Generated by hkj-processor. Do not edit.")
        .build()
        .writeTo(processingEnv.getFiler());
  }

  /**
   * Generates a static inject factory method for one effect in the composition.
   *
   * <p>For right-nested EitherF composition {@code EitherF<F, EitherF<G, H>>}:
   *
   * <ul>
   *   <li>Position 0 (F): {@code InjectInstances.injectLeft()}
   *   <li>Position 1 (G): {@code InjectInstances.injectRightThen(InjectInstances.injectLeft())}
   *   <li>Position 2 (H): {@code InjectInstances.injectRightThen(InjectInstances.injectRight())}
   * </ul>
   *
   * @param effectName the field name of the effect
   * @param position zero-based position in the composition
   * @param arity total number of effects (2-4)
   * @return the generated method spec
   */
  private MethodSpec generateInjectMethod(String effectName, int position, int arity) {
    String methodName =
        "inject" + effectName.substring(0, 1).toUpperCase(Locale.ROOT) + effectName.substring(1);

    MethodSpec.Builder builder =
        MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(INJECT)
            .addJavadoc(
                "Creates an Inject instance for the {@code $L} effect.\n\n"
                    + "@return An Inject instance for embedding the effect into the composed type\n",
                effectName);

    // Build the inject expression using CodeBlock for safety
    CodeBlock injectExpr = buildInjectExpression(position, arity);
    builder.addCode("return $L;\n", injectExpr);

    return builder.build();
  }

  /**
   * Builds the inject expression for a given position in the EitherF nesting.
   *
   * @param position zero-based position
   * @param arity total number of effects
   * @return the CodeBlock for the inject expression
   */
  private CodeBlock buildInjectExpression(int position, int arity) {
    if (position == 0) {
      return CodeBlock.of("$T.injectLeft()", INJECT_INSTANCES);
    }

    // For the last position, the innermost call is injectRight(); otherwise injectLeft()
    boolean isLast = (position == arity - 1);
    CodeBlock inner =
        isLast
            ? CodeBlock.of("$T.injectRight()", INJECT_INSTANCES)
            : CodeBlock.of("$T.injectLeft()", INJECT_INSTANCES);

    // Wrap with injectRightThen() for each nesting level
    CodeBlock result = inner;
    for (int i = 0; i < position; i++) {
      result = CodeBlock.of("$T.injectRightThen($L)", INJECT_INSTANCES, result);
    }
    return result;
  }

  private MethodSpec generateFunctorMethod(List<String> effectNames, int arity) {
    MethodSpec.Builder builder =
        MethodSpec.methodBuilder("functor")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(FUNCTOR)
            .addJavadoc(
                "Returns a composed Functor for the combined effect type.\n\n"
                    + "@return The composed Functor instance\n");

    // Build nested EitherFFunctor construction
    // For 2: EitherFFunctor.of(functor1, functor2)
    // For 3: EitherFFunctor.of(functor1, EitherFFunctor.of(functor2, functor3))
    // We need the individual functors as parameters
    for (int i = 0; i < arity; i++) {
      builder.addParameter(FUNCTOR, effectNames.get(i) + "Functor");
    }

    if (arity == 2) {
      builder.addStatement(
          "return $T.of($LFunctor, $LFunctor)",
          EITHERF_FUNCTOR,
          effectNames.get(0),
          effectNames.get(1));
    } else if (arity == 3) {
      builder.addStatement(
          "return $T.of($LFunctor, $T.of($LFunctor, $LFunctor))",
          EITHERF_FUNCTOR,
          effectNames.get(0),
          EITHERF_FUNCTOR,
          effectNames.get(1),
          effectNames.get(2));
    } else { // arity == 4
      builder.addStatement(
          "return $T.of($LFunctor, $T.of($LFunctor, $T.of($LFunctor, $LFunctor)))",
          EITHERF_FUNCTOR,
          effectNames.get(0),
          EITHERF_FUNCTOR,
          effectNames.get(1),
          EITHERF_FUNCTOR,
          effectNames.get(2),
          effectNames.get(3));
    }

    return builder.build();
  }

  private TypeSpec generateBoundSetRecord(List<String> effectNames, int arity) {
    TypeVariableName typeF =
        TypeVariableName.get(
            "F",
            ParameterizedTypeName.get(
                WITNESS_ARITY, ClassName.get("org.higherkindedj.hkt", "TypeArity", "Unary")));

    // Build record constructor whose parameters become record components
    MethodSpec.Builder ctorBuilder = MethodSpec.constructorBuilder();
    for (String name : effectNames) {
      ctorBuilder.addParameter(ClassName.OBJECT, name);
    }

    return TypeSpec.recordBuilder("BoundSet")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addTypeVariable(typeF)
        .recordConstructor(ctorBuilder.build())
        .addJavadoc(
            "Convenience record holding Bound instances for all composed effects.\n\n"
                + "@param <F> The composed effect witness type\n")
        .build();
  }

  // =========================================================================
  // @Handles processing
  // =========================================================================

  private void processHandles(RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Handles.class)) {
      if (element.getKind() != ElementKind.CLASS) {
        error("@Handles can only annotate classes", element);
        continue;
      }

      TypeElement interpreterType = (TypeElement) element;

      // Get the referenced effect algebra class
      TypeElement algebraType = getHandlesAlgebraType(interpreterType);
      if (algebraType == null) continue;

      // Check that the algebra is sealed
      if (!algebraType.getModifiers().contains(Modifier.SEALED)) {
        error(
            "@Handles references non-sealed interface: " + algebraType.getQualifiedName(), element);
        continue;
      }

      // Get all permits of the algebra
      List<? extends TypeMirror> permits = algebraType.getPermittedSubclasses();
      Set<String> expectedHandlers = new HashSet<>();
      for (TypeMirror permit : permits) {
        Element permitElement = processingEnv.getTypeUtils().asElement(permit);
        if (permitElement != null) {
          expectedHandlers.add("handle" + permitElement.getSimpleName());
        }
      }

      // Check which handlers exist on the interpreter
      Set<String> foundHandlers = new HashSet<>();
      for (Element enclosed : interpreterType.getEnclosedElements()) {
        if (enclosed.getKind() == ElementKind.METHOD) {
          String methodName = enclosed.getSimpleName().toString();
          if (methodName.startsWith("handle")) {
            foundHandlers.add(methodName);
          }
        }
      }

      // Report missing handlers
      for (String expected : expectedHandlers) {
        if (!foundHandlers.contains(expected)) {
          String opName = expected.substring("handle".length());
          error(
              "No handler for operation: "
                  + opName
                  + ". Expected method: "
                  + expected
                  + "("
                  + algebraType.getSimpleName()
                  + "."
                  + opName
                  + ")",
              element);
        }
      }

      // Report extra handlers
      for (String found : foundHandlers) {
        if (!expectedHandlers.contains(found)) {
          warning(
              "Handler " + found + " doesn't match any operation in " + algebraType.getSimpleName(),
              element);
        }
      }
    }
  }

  private TypeElement getHandlesAlgebraType(TypeElement interpreterType) {
    Handles annotation = interpreterType.getAnnotation(Handles.class);
    if (annotation == null) return null;

    try {
      // This will throw MirroredTypeException, which is how we get the TypeMirror
      annotation.value();
      throw new AssertionError("Expected MirroredTypeException from annotation.value()");
    } catch (MirroredTypeException e) {
      TypeMirror mirror = e.getTypeMirror();
      Element element = processingEnv.getTypeUtils().asElement(mirror);
      if (element instanceof TypeElement typeElement) {
        return typeElement;
      }
      error("Cannot resolve @Handles value type", interpreterType);
      return null;
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private String resolveTargetPackage(TypeElement typeElement, ComposeEffects annotation) {
    String targetPackage = annotation.targetPackage();
    if (targetPackage.isEmpty()) {
      return processingEnv
          .getElementUtils()
          .getPackageOf(typeElement)
          .getQualifiedName()
          .toString();
    }
    return targetPackage;
  }

  private void error(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
  }

  private void warning(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, element);
  }
}
