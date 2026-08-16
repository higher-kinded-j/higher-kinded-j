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
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
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
public class ComposeEffectsProcessor extends AbstractProcessor {

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

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
  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");
  private static final ClassName NULL_MARKED =
      ClassName.get("org.jspecify.annotations", "NullMarked");

  /**
   * One composed effect, resolved from a {@code Class<XOp<?>>} record component.
   *
   * @param field the record component's name, which names the generated accessor
   * @param algebra the algebra's qualified name, for diagnostics
   * @param witness the algebra's {@code XKind.Witness}
   * @param ops the algebra's generated {@code XOps}, whose {@code Bound} types the BoundSet
   */
  private record EffectBinding(String field, String algebra, ClassName witness, ClassName ops) {}

  /**
   * Resolves every record component to the algebra it names, or null when any component does not
   * name one.
   *
   * <p>{@code @ComposeEffects} requires each field to be {@code Class<XOp<?>>} where {@code XOp}
   * carries {@code @EffectAlgebra}: reading that type is what lets the generated support spell the
   * composed witness.
   *
   * <p>Every bad component is reported before giving up, so one recompile shows the whole list.
   */
  private List<EffectBinding> resolveBindings(List<? extends RecordComponentElement> components) {
    List<EffectBinding> bindings = new ArrayList<>();
    boolean resolved = true;
    for (RecordComponentElement component : components) {
      TypeElement algebra = effectAlgebraOf(component);
      if (algebra == null) {
        resolved = false;
        continue;
      }
      String algebraName = algebra.getSimpleName().toString();
      String packageName = effectAlgebraPackage(algebra);
      bindings.add(
          new EffectBinding(
              component.getSimpleName().toString(),
              algebra.getQualifiedName().toString(),
              ClassName.get(packageName, algebraName + "Kind", "Witness"),
              ClassName.get(packageName, algebraName + "Ops")));
    }
    return resolved ? bindings : null;
  }

  /** The effect algebra a component names, or null (having reported why) when it names none. */
  private TypeElement effectAlgebraOf(RecordComponentElement component) {
    String expected =
        "@ComposeEffects fields must be declared Class<XOp<?>> where XOp is annotated"
            + " @EffectAlgebra; field '"
            + component.getSimpleName()
            + "' is ";
    if (component.asType().getKind() == TypeKind.ERROR) {
      return null; // javac already reports the unresolvable type; a second message adds nothing
    }
    if (!(component.asType() instanceof DeclaredType declared)
        || !((TypeElement) declared.asElement()).getQualifiedName().contentEquals("java.lang.Class")
        || declared.getTypeArguments().size() != 1) {
      error(expected + component.asType(), component);
      return null;
    }
    TypeMirror argument = declared.getTypeArguments().get(0);
    if (argument.getKind() == TypeKind.ERROR) {
      return null; // as above
    }
    if (!(argument instanceof DeclaredType algebraType)
        || !(algebraType.asElement() instanceof TypeElement algebra)) {
      error(expected + component.asType(), component);
      return null;
    }
    return isComposableAlgebra(algebra, component) ? algebra : null;
  }

  /**
   * Reports whether an algebra can be composed, given what {@code @EffectAlgebra} generates.
   *
   * <p>Composition names {@code XKind.Witness} and {@code XOps.Bound}, so the algebra has to be one
   * {@code @EffectAlgebra} actually generates those for. Checking here keeps a rejected algebra to
   * a single pointed diagnostic; emitting a support class that names types the other processor
   * declined to write would bury it under a page of "package does not exist".
   */
  private boolean isComposableAlgebra(TypeElement algebra, RecordComponentElement component) {
    if (algebra.getAnnotation(EffectAlgebra.class) == null) {
      error(
          "@ComposeEffects field '"
              + component.getSimpleName()
              + "' names "
              + algebra.getQualifiedName()
              + ", which is not annotated @EffectAlgebra. Composition derives the witness and Ops"
              + " from that annotation, so an algebra with more than one type parameter (whose"
              + " witness would itself be generic) cannot take part.",
          component);
      return false;
    }
    if (!algebra.getModifiers().contains(Modifier.SEALED)) {
      error(
          "@ComposeEffects field '"
              + component.getSimpleName()
              + "' names "
              + algebra.getQualifiedName()
              + ", which @EffectAlgebra rejects because it is not a sealed interface, so its Kind"
              + " and Ops are not generated.",
          component);
      return false;
    }
    if (algebra.getTypeParameters().size() != 1) {
      error(
          "@ComposeEffects field '"
              + component.getSimpleName()
              + "' names "
              + algebra.getQualifiedName()
              + ", which @EffectAlgebra rejects because it does not have exactly one type"
              + " parameter, so its Kind and Ops are not generated.",
          component);
      return false;
    }
    return true;
  }

  /** Where {@code @EffectAlgebra} puts the algebra's generated types. */
  private String effectAlgebraPackage(TypeElement algebra) {
    String targetPackage = algebra.getAnnotation(EffectAlgebra.class).targetPackage();
    return targetPackage.isEmpty()
        ? processingEnv.getElementUtils().getPackageOf(algebra).getQualifiedName().toString()
        : targetPackage;
  }

  /**
   * The composed witness from position {@code from} onwards: the last effect's own witness, and
   * every earlier one an {@code EitherFKind.Witness} over it and the rest. Position 0 is the
   * composition itself.
   */
  private static TypeName composedWitness(List<EffectBinding> bindings, int from) {
    if (from == bindings.size() - 1) {
      return bindings.get(from).witness();
    }
    return ParameterizedTypeName.get(
        EITHERF_KIND.nestedClass("Witness"),
        bindings.get(from).witness(),
        composedWitness(bindings, from + 1));
  }

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

      // Resolve the algebras before looking for duplicates: a field's declared type may be
      // Class<X> for several X that share one algebra, and two malformed fields are the same
      // java.lang.Class as each other, which would otherwise be reported as a duplicate.
      List<EffectBinding> bindings = resolveBindings(components);
      if (bindings == null) {
        continue; // fields do not name effect algebras; errors already reported
      }

      Set<String> seenWitnesses = new HashSet<>();
      boolean hasDuplicates = false;
      for (int i = 0; i < bindings.size(); i++) {
        if (!seenWitnesses.add(bindings.get(i).witness().toString())) {
          error(
              "Duplicate effect algebra: "
                  + bindings.get(i).algebra()
                  + " (field '"
                  + bindings.get(i).field()
                  + "'). Each effect algebra may only appear once.",
              components.get(i));
          hasDuplicates = true;
        }
      }
      if (hasDuplicates) continue;

      writeSupport(typeElement, bindings, element);
    }
  }

  @ExcludeFromJacocoGeneratedReport
  private void writeSupport(
      TypeElement typeElement, List<EffectBinding> bindings, Element element) {
    try {
      ComposeEffects ann = typeElement.getAnnotation(ComposeEffects.class);
      String packageName = resolveTargetPackage(typeElement, ann);
      String baseName = typeElement.getSimpleName().toString();

      generateSupport(packageName, baseName, typeElement, bindings);
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
      String packageName, String baseName, TypeElement source, List<EffectBinding> bindings)
      throws IOException {
    String supportName = baseName + "Support";
    int arity = bindings.size();

    TypeSpec.Builder supportBuilder =
        TypeSpec.classBuilder(supportName)
            .addAnnotation(NULL_MARKED)
            .addAnnotation(GENERATED)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addOriginatingElement(source);

    // Generate inject factory methods for each effect
    // For 2 effects: injectLeft() and injectRight()
    // For 3: injectLeft(), injectRightThen(injectLeft()), injectRightThen(injectRight())
    // For 4: similar nesting
    for (int i = 0; i < arity; i++) {
      supportBuilder.addMethod(generateInjectMethod(bindings, i));
    }

    // Generate functor() method
    supportBuilder.addMethod(generateFunctorMethod(bindings));

    // Generate BoundSet record
    supportBuilder.addType(generateBoundSetRecord(bindings));

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
   * @param bindings the resolved effects, in declaration order
   * @param position the effect this factory injects
   * @return the generated factory method
   */
  private MethodSpec generateInjectMethod(List<EffectBinding> bindings, int position) {
    EffectBinding binding = bindings.get(position);
    String effectName = binding.field();
    String methodName =
        "inject" + effectName.substring(0, 1).toUpperCase(Locale.ROOT) + effectName.substring(1);

    // Declaring the composed witness lets InjectInstances' type parameters infer from the
    // return type, so javac checks the nesting.
    MethodSpec.Builder builder =
        MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(
                ParameterizedTypeName.get(INJECT, binding.witness(), composedWitness(bindings, 0)))
            .addJavadoc(
                "Creates an Inject instance for the {@code $L} effect.\n\n"
                    + "@return An Inject embedding {@code $T} into the composed effect type\n",
                effectName,
                binding.witness());

    // Build the inject expression using CodeBlock for safety
    CodeBlock injectExpr = buildInjectExpression(position, bindings.size());
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

    // For the last position, the innermost call is injectRight(); otherwise injectLeft().
    // injectRight() already descends the final level, so the last effect needs one fewer
    // injectRightThen than its position: at arity 2 the second effect is injectRight() alone.
    boolean isLast = (position == arity - 1);
    CodeBlock inner =
        isLast
            ? CodeBlock.of("$T.injectRight()", INJECT_INSTANCES)
            : CodeBlock.of("$T.injectLeft()", INJECT_INSTANCES);

    // Wrap with injectRightThen() for each nesting level
    CodeBlock result = inner;
    int wraps = isLast ? position - 1 : position;
    for (int i = 0; i < wraps; i++) {
      result = CodeBlock.of("$T.injectRightThen($L)", INJECT_INSTANCES, result);
    }
    return result;
  }

  private MethodSpec generateFunctorMethod(List<EffectBinding> bindings) {
    int arity = bindings.size();
    List<String> effectNames = bindings.stream().map(EffectBinding::field).toList();

    MethodSpec.Builder builder =
        MethodSpec.methodBuilder("functor")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(
                ParameterizedTypeName.get(
                    EITHERF_FUNCTOR, bindings.getFirst().witness(), composedWitness(bindings, 1)))
            .addJavadoc("Returns a composed Functor for the combined effect type.\n\n");
    for (EffectBinding binding : bindings) {
      builder.addJavadoc(
          "@param $LFunctor the Functor for {@code $T}\n", binding.field(), binding.witness());
    }
    builder.addJavadoc("@return The composed Functor over the full effect type\n");

    // Build nested EitherFFunctor construction
    // For 2: EitherFFunctor.of(functor1, functor2)
    // For 3: EitherFFunctor.of(functor1, EitherFFunctor.of(functor2, functor3))
    // We need the individual functors as parameters
    for (EffectBinding binding : bindings) {
      builder.addParameter(
          ParameterizedTypeName.get(FUNCTOR, binding.witness()), binding.field() + "Functor");
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

  private TypeSpec generateBoundSetRecord(List<EffectBinding> bindings) {
    TypeName composed = composedWitness(bindings, 0);

    // The composition fixes the witness, so BoundSet needs no type parameter of its own:
    // each component is that algebra's Bound at the composed type.
    MethodSpec.Builder ctorBuilder = MethodSpec.constructorBuilder();
    TypeSpec.Builder record =
        TypeSpec.recordBuilder("BoundSet")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc("Convenience record holding Bound instances for all composed effects.\n\n");

    for (EffectBinding binding : bindings) {
      ctorBuilder.addParameter(
          ParameterizedTypeName.get(binding.ops().nestedClass("Bound"), composed), binding.field());
      record.addJavadoc("@param $L bound {@code $T} operations\n", binding.field(), binding.ops());
    }

    return record.recordConstructor(ctorBuilder.build()).build();
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

  // Package-private for tests.
  TypeElement getHandlesAlgebraType(TypeElement interpreterType) {
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
