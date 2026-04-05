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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.higherkindedj.hkt.effect.annotation.Handles;

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
  private static final ClassName INJECT =
      ClassName.get("org.higherkindedj.hkt.inject", "Inject");
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
    for (Element element :
        roundEnv.getElementsAnnotatedWith(ComposeEffects.class)) {
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

      // Check for duplicates
      Set<String> seen = new HashSet<>();
      boolean hasDuplicates = false;
      for (RecordComponentElement comp : components) {
        String typeName = comp.asType().toString();
        if (!seen.add(typeName)) {
          error("Duplicate effect algebra: " + typeName, comp);
          hasDuplicates = true;
        }
      }
      if (hasDuplicates) continue;

      try {
        ComposeEffects ann = typeElement.getAnnotation(ComposeEffects.class);
        String packageName = resolveTargetPackage(typeElement, ann);
        String baseName = typeElement.getSimpleName().toString();

        generateSupport(packageName, baseName, typeElement, components);
      } catch (IOException e) {
        error("Failed to generate code: " + e.getMessage(), element);
      }
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
                WITNESS_ARITY,
                ClassName.get("org.higherkindedj.hkt", "TypeArity", "Unary")));

    TypeSpec.Builder supportBuilder =
        TypeSpec.classBuilder(supportName)
            .addAnnotation(NULL_MARKED)
            .addAnnotation(GENERATED)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(
                MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    // Generate inject factory methods for each effect
    // For 2 effects: injectLeft() and injectRight()
    // For 3: injectLeft(), injectRightThen(injectLeft()), injectRightThen(injectRight())
    // For 4: similar nesting
    for (int i = 0; i < arity; i++) {
      supportBuilder.addMethod(
          generateInjectMethod(effectNames.get(i), i, arity));
    }

    // Generate functor() method
    supportBuilder.addMethod(generateFunctorMethod(effectNames, arity));

    // Generate BoundSet record
    supportBuilder.addType(generateBoundSetRecord(effectNames, arity));

    // Generate createBounds() convenience method
    supportBuilder.addMethod(generateCreateBoundsMethod(effectNames, arity));

    supportBuilder.addJavadoc(
        "Support infrastructure for the {@link $L} effect composition.\n\n"
            + "<p>Provides Inject instances, a composed Functor, and a BoundSet record\n"
            + "for using the composed effects in Free monad programs.\n\n"
            + "@see $L\n",
        baseName, baseName);

    JavaFile.builder(packageName, supportBuilder.build())
        .addFileComment("Generated by hkj-processor. Do not edit.")
        .build()
        .writeTo(processingEnv.getFiler());
  }

  private MethodSpec generateInjectMethod(String effectName, int position, int arity) {
    // Method name: inject<EffectName> (e.g., injectConsole)
    String methodName =
        "inject"
            + effectName.substring(0, 1).toUpperCase(Locale.ROOT)
            + effectName.substring(1);

    MethodSpec.Builder builder =
        MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc(
                "Creates an Inject instance for the {@code $L} effect.\n\n"
                    + "@return An Inject instance for embedding the effect into the composed type\n",
                effectName);

    // The method body depends on position in the nesting
    if (position == 0) {
      // First effect: always injectLeft()
      builder.addStatement("return $T.injectLeft()", INJECT_INSTANCES);
    } else if (position == arity - 1 && arity == 2) {
      // Last of 2: injectRight()
      builder.addStatement("return $T.injectRight()", INJECT_INSTANCES);
    } else if (position == arity - 1) {
      // Last of 3+: chain of injectRightThen ending with injectRight
      StringBuilder chain = new StringBuilder();
      for (int i = 1; i < position; i++) {
        chain.append("$T.injectRightThen(");
      }
      chain.append("$T.injectRight()");
      for (int i = 1; i < position; i++) {
        chain.append(")");
      }
      Object[] args = new Object[position];
      for (int i = 0; i < position - 1; i++) args[i] = INJECT_INSTANCES;
      args[position - 1] = INJECT_INSTANCES;
      builder.addStatement("return " + chain, args);
    } else {
      // Middle: injectRightThen chains ending with injectLeft
      StringBuilder chain = new StringBuilder();
      for (int i = 0; i < position; i++) {
        chain.append("$T.injectRightThen(");
      }
      chain.append("$T.injectLeft()");
      for (int i = 0; i < position; i++) {
        chain.append(")");
      }
      Object[] args = new Object[position + 1];
      for (int i = 0; i <= position; i++) args[i] = INJECT_INSTANCES;
      builder.addStatement("return " + chain, args);
    }

    // Return type is generic - use wildcard for simplicity
    builder.returns(INJECT);
    return builder.build();
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
    // For 2: new EitherFFunctor<>(functor1, functor2)
    // For 3: new EitherFFunctor<>(functor1, new EitherFFunctor<>(functor2, functor3))
    // We need the individual functors as parameters
    for (int i = 0; i < arity; i++) {
      builder.addParameter(FUNCTOR, effectNames.get(i) + "Functor");
    }

    if (arity == 2) {
      builder.addStatement(
          "return new $T<>($LFunctor, $LFunctor)",
          EITHERF_FUNCTOR,
          effectNames.get(0),
          effectNames.get(1));
    } else if (arity == 3) {
      builder.addStatement(
          "return new $T<>($LFunctor, new $T<>($LFunctor, $LFunctor))",
          EITHERF_FUNCTOR,
          effectNames.get(0),
          EITHERF_FUNCTOR,
          effectNames.get(1),
          effectNames.get(2));
    } else { // arity == 4
      builder.addStatement(
          "return new $T<>($LFunctor, new $T<>($LFunctor, new $T<>($LFunctor, $LFunctor)))",
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
                WITNESS_ARITY,
                ClassName.get("org.higherkindedj.hkt", "TypeArity", "Unary")));

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder("BoundSet")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addTypeVariable(typeF)
            .addJavadoc(
                "Convenience holder for Bound instances of all composed effects.\n\n"
                    + "@param <F> The composed effect witness type\n");

    // Add a field per effect
    MethodSpec.Builder ctorBuilder = MethodSpec.constructorBuilder().addModifiers(Modifier.PUBLIC);
    for (String name : effectNames) {
      classBuilder.addField(ClassName.OBJECT, name, Modifier.PRIVATE, Modifier.FINAL);
      ctorBuilder.addParameter(ClassName.OBJECT, name);
      ctorBuilder.addStatement("this.$L = $L", name, name);
    }
    classBuilder.addMethod(ctorBuilder.build());

    // Add getter per effect
    for (String name : effectNames) {
      classBuilder.addMethod(
          MethodSpec.methodBuilder(name)
              .addModifiers(Modifier.PUBLIC)
              .returns(ClassName.OBJECT)
              .addStatement("return $L", name)
              .build());
    }

    return classBuilder.build();
  }

  private MethodSpec generateCreateBoundsMethod(List<String> effectNames, int arity) {
    return MethodSpec.methodBuilder("createBounds")
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .returns(ClassName.OBJECT)
        .addJavadoc(
            "Creates a BoundSet with Bound instances for all composed effects.\n"
                + "Users should cast the returned BoundSet fields to their specific Bound types.\n")
        .addStatement("return null // TODO: implement when effect types are resolved")
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
            "@Handles references non-sealed interface: " + algebraType.getQualifiedName(),
            element);
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
              "Handler "
                  + found
                  + " doesn't match any operation in "
                  + algebraType.getSimpleName(),
              element);
        }
      }
    }
  }

  private TypeElement getHandlesAlgebraType(TypeElement interpreterType) {
    Handles annotation = interpreterType.getAnnotation(Handles.class);
    if (annotation == null) return null;

    try {
      // This will throw MirroredTypeException — that's how we get the TypeMirror
      annotation.value();
      return null; // unreachable
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
