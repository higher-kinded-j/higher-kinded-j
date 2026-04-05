// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.ArrayList;
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
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

/**
 * Annotation processor for {@link EffectAlgebra @EffectAlgebra} that generates HKT boilerplate for
 * sealed effect algebra interfaces.
 *
 * <p>For each annotated sealed interface {@code FooOp<A>}, generates:
 *
 * <ol>
 *   <li>{@code FooOpKind} — Kind marker interface with inner {@code Witness} class
 *   <li>{@code FooOpKindHelper} — Enum singleton with {@code widen()}/{@code narrow()} methods
 *   <li>{@code FooOpFunctor} — {@code Functor<Witness>} implementation
 *   <li>{@code FooOpOps} — Smart constructors plus {@code Bound} inner class
 *   <li>{@code FooOpInterpreter} — Abstract interpreter skeleton
 * </ol>
 *
 * @see EffectAlgebra
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.hkt.effect.annotation.EffectAlgebra")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class EffectAlgebraProcessor extends AbstractProcessor {

  /** Creates a new EffectAlgebraProcessor. */
  public EffectAlgebraProcessor() {}

  // -------------------------------------------------------------------------
  // Common type references
  // -------------------------------------------------------------------------

  private static final ClassName KIND = ClassName.get("org.higherkindedj.hkt", "Kind");
  private static final ClassName FUNCTOR = ClassName.get("org.higherkindedj.hkt", "Functor");
  private static final ClassName NATURAL = ClassName.get("org.higherkindedj.hkt", "Natural");
  private static final ClassName TYPE_ARITY = ClassName.get("org.higherkindedj.hkt", "TypeArity");
  private static final ClassName WITNESS_ARITY =
      ClassName.get("org.higherkindedj.hkt", "WitnessArity");
  private static final ClassName FREE = ClassName.get("org.higherkindedj.hkt.free", "Free");
  private static final ClassName INJECT = ClassName.get("org.higherkindedj.hkt.inject", "Inject");
  private static final ClassName VALIDATION =
      ClassName.get("org.higherkindedj.hkt.util.validation", "Validation");
  private static final ClassName OPERATION =
      ClassName.get("org.higherkindedj.hkt.util.validation", "Operation");
  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");
  private static final ClassName NULL_MARKED =
      ClassName.get("org.jspecify.annotations", "NullMarked");
  private static final ClassName FUNCTION = ClassName.get("java.util.function", "Function");
  private static final ClassName OBJECTS = ClassName.get("java.util", "Objects");

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element.getKind() != ElementKind.INTERFACE) {
          error("@EffectAlgebra can only annotate sealed interfaces", element);
          continue;
        }
        TypeElement typeElement = (TypeElement) element;
        if (!typeElement.getModifiers().contains(Modifier.SEALED)) {
          error("@EffectAlgebra can only annotate sealed interfaces", element);
          continue;
        }
        if (typeElement.getTypeParameters().size() != 1) {
          error("Sealed interface must have exactly one type parameter", element);
          continue;
        }

        // Validate all permits are records with no extra type params
        List<TypeElement> permits = getPermittedRecords(typeElement);
        if (permits == null) continue; // errors already reported

        try {
          EffectAlgebra ann = typeElement.getAnnotation(EffectAlgebra.class);
          String packageName = resolveTargetPackage(typeElement, ann);
          String baseName = typeElement.getSimpleName().toString();

          generateKind(packageName, baseName, typeElement);
          generateKindHelper(packageName, baseName, typeElement);
          generateFunctor(packageName, baseName, typeElement);
          generateOps(packageName, baseName, typeElement, permits);
          generateInterpreter(packageName, baseName, typeElement, permits);
        } catch (Exception e) {
          error(
              "Failed to generate code: " + e.getClass().getName() + ": " + e.getMessage(),
              element);
        }
      }
    }
    return true;
  }

  // =========================================================================
  // Validation
  // =========================================================================

  private List<TypeElement> getPermittedRecords(TypeElement sealedInterface) {
    List<? extends TypeMirror> permitted = sealedInterface.getPermittedSubclasses();
    if (permitted.isEmpty()) {
      error("Sealed interface has no permitted subtypes", sealedInterface);
      return null;
    }

    List<TypeElement> permits = new ArrayList<>();
    for (TypeMirror mirror : permitted) {
      Element permitElement = processingEnv.getTypeUtils().asElement(mirror);
      if (permitElement == null || permitElement.getKind() != ElementKind.RECORD) {
        error("Permit must be a record type: " + mirror, sealedInterface);
        return null;
      }
      TypeElement permitType = (TypeElement) permitElement;
      // Check for extra type params beyond the parent's <A>
      int parentParamCount = sealedInterface.getTypeParameters().size();
      if (permitType.getTypeParameters().size() > parentParamCount) {
        error(
            "Permit " + permitType.getSimpleName() + " has extra type parameters beyond <A>",
            permitType);
        return null;
      }
      permits.add(permitType);
    }
    return permits;
  }

  // =========================================================================
  // 1. Generate *Kind.java
  // =========================================================================

  private void generateKind(String packageName, String baseName, TypeElement source)
      throws IOException {
    String kindName = baseName + "Kind";
    ClassName witnessClass = ClassName.get(packageName, kindName, "Witness");

    // Kind<*Kind.Witness, A> interface
    TypeVariableName typeA = TypeVariableName.get("A");
    ParameterizedTypeName kindSuper = ParameterizedTypeName.get(KIND, witnessClass, typeA);

    TypeSpec witnessType =
        TypeSpec.classBuilder("Witness")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addSuperinterface(
                ParameterizedTypeName.get(
                    WITNESS_ARITY, ClassName.get("org.higherkindedj.hkt", "TypeArity", "Unary")))
            .addMethod(
                MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PRIVATE)
                    .addStatement(
                        "throw new $T($S)", UnsupportedOperationException.class, "Witness class")
                    .build())
            .addJavadoc(
                "Phantom type marker (witness type) for the {@code $L<?>} type constructor.\n",
                baseName)
            .build();

    TypeSpec kindType =
        TypeSpec.interfaceBuilder(kindName)
            .addAnnotation(NULL_MARKED)
            .addAnnotation(GENERATED)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(typeA)
            .addSuperinterface(kindSuper)
            .addType(witnessType)
            .addJavadoc(
                "Kind interface marker for {@link $L $L<A>} in Higher-Kinded-J.\n\n"
                    + "@param <A> The result type (the varying parameter)\n",
                baseName,
                baseName)
            .build();

    JavaFile.builder(packageName, kindType)
        .addFileComment("Generated by hkj-processor. Do not edit.")
        .build()
        .writeTo(processingEnv.getFiler());
  }

  // =========================================================================
  // 2. Generate *KindHelper.java
  // =========================================================================

  private void generateKindHelper(String packageName, String baseName, TypeElement source)
      throws IOException {
    String kindName = baseName + "Kind";
    String helperName = baseName + "KindHelper";
    String holderName = baseName + "Holder";
    String singletonName = toUpperSnakeCase(baseName);
    ClassName kindClass = ClassName.get(packageName, kindName);
    ClassName witnessClass = ClassName.get(packageName, kindName, "Witness");
    ClassName sourceClass = ClassName.get(packageName, baseName);

    TypeVariableName typeA = TypeVariableName.get("A");

    // Holder record: record FooHolder<A>(Foo<A> value) implements FooKind<A> {}
    TypeSpec holderType =
        TypeSpec.recordBuilder(holderName)
            .recordConstructor(
                MethodSpec.constructorBuilder()
                    .addParameter(ParameterizedTypeName.get(sourceClass, typeA), "value")
                    .build())
            .addTypeVariable(typeA)
            .addSuperinterface(ParameterizedTypeName.get(kindClass, typeA))
            .build();

    // widen method
    MethodSpec widenMethod =
        MethodSpec.methodBuilder("widen")
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(typeA)
            .addParameter(ParameterizedTypeName.get(sourceClass, typeA), "value")
            .returns(ParameterizedTypeName.get(KIND, witnessClass, typeA))
            .addStatement("$T.kind().requireForWiden(value, $T.class)", VALIDATION, sourceClass)
            .addStatement("return new $L<>(value)", holderName)
            .addJavadoc(
                "Widens a concrete {@code $L<A>} into its Kind representation.\n\n"
                    + "@param value The concrete instance. Must not be null.\n"
                    + "@param <A> The result type\n"
                    + "@return The widened Kind representation\n",
                baseName)
            .build();

    // narrow method
    MethodSpec narrowMethod =
        MethodSpec.methodBuilder("narrow")
            .addAnnotation(
                AnnotationSpec.builder(SuppressWarnings.class)
                    .addMember("value", "$S", "unchecked")
                    .build())
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(typeA)
            .addParameter(ParameterizedTypeName.get(KIND, witnessClass, typeA), "kind")
            .returns(ParameterizedTypeName.get(sourceClass, typeA))
            .addStatement("$T.kind().requireNonNull(kind, $T.FROM_KIND)", VALIDATION, OPERATION)
            .addStatement("return (($L<A>) kind).value()", holderName)
            .addJavadoc(
                "Narrows a Kind representation back to concrete {@code $L<A>}.\n\n"
                    + "@param kind The Kind representation. Must not be null.\n"
                    + "@param <A> The result type\n"
                    + "@return The concrete $L\n",
                baseName,
                baseName)
            .build();

    TypeSpec helperType =
        TypeSpec.enumBuilder(helperName)
            .addAnnotation(NULL_MARKED)
            .addAnnotation(GENERATED)
            .addModifiers(Modifier.PUBLIC)
            .addEnumConstant(singletonName)
            .addType(holderType)
            .addMethod(widenMethod)
            .addMethod(narrowMethod)
            .addJavadoc(
                "Helper for converting between concrete {@link $L} and its HKT representation "
                    + "{@link $L}.\n\n@see $L\n@see $LKind\n",
                baseName,
                kindName,
                baseName,
                baseName)
            .build();

    JavaFile.builder(packageName, helperType)
        .addFileComment("Generated by hkj-processor. Do not edit.")
        .build()
        .writeTo(processingEnv.getFiler());
  }

  // =========================================================================
  // 3. Generate *Functor.java
  // =========================================================================

  private void generateFunctor(String packageName, String baseName, TypeElement source)
      throws IOException {
    String kindName = baseName + "Kind";
    String helperName = baseName + "KindHelper";
    String functorName = baseName + "Functor";
    String singletonName = toUpperSnakeCase(baseName);
    ClassName witnessClass = ClassName.get(packageName, kindName, "Witness");
    ClassName sourceClass = ClassName.get(packageName, baseName);
    ClassName helperClass = ClassName.get(packageName, helperName);

    TypeVariableName typeA = TypeVariableName.get("A");
    TypeVariableName typeB = TypeVariableName.get("B");

    // Check if the sealed interface has a mapK method
    boolean hasMapK = hasMapKMethod(source);

    // Build map method body
    MethodSpec.Builder mapBuilder =
        MethodSpec.methodBuilder("map")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(typeA)
            .addTypeVariable(typeB)
            .addParameter(
                ParameterizedTypeName.get(
                    FUNCTION,
                    WildcardTypeName.supertypeOf(typeA),
                    WildcardTypeName.subtypeOf(typeB)),
                "f")
            .addParameter(ParameterizedTypeName.get(KIND, witnessClass, typeA), "fa")
            .returns(ParameterizedTypeName.get(KIND, witnessClass, typeB))
            .addStatement("$T.function().require(f, $S, $T.MAP)", VALIDATION, "f", OPERATION)
            .addStatement("$T.kind().requireNonNull(fa, $T.MAP)", VALIDATION, OPERATION)
            .addStatement("$T<A> op = $T.$L.narrow(fa)", sourceClass, helperClass, singletonName);

    if (hasMapK) {
      mapBuilder.addStatement("return $T.$L.widen(op.mapK(f))", helperClass, singletonName);
    } else {
      // Cast-through for simple effect algebras without continuation-passing
      mapBuilder
          .addAnnotation(
              AnnotationSpec.builder(SuppressWarnings.class)
                  .addMember("value", "$S", "unchecked")
                  .build())
          .addStatement(
              "return ($T) $T.$L.widen(($T) ($T) op)",
              ParameterizedTypeName.get(KIND, witnessClass, typeB),
              helperClass,
              singletonName,
              ParameterizedTypeName.get(sourceClass, typeB),
              ParameterizedTypeName.get(sourceClass, WildcardTypeName.subtypeOf(typeB)));
    }

    MethodSpec mapMethod = mapBuilder.build();

    // Instance field + factory
    TypeSpec.Builder functorBuilder =
        TypeSpec.classBuilder(functorName)
            .addAnnotation(NULL_MARKED)
            .addAnnotation(GENERATED)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(FUNCTOR, witnessClass))
            .addField(
                FieldSpec.builder(
                        ClassName.get(packageName, functorName),
                        "INSTANCE",
                        Modifier.PRIVATE,
                        Modifier.STATIC,
                        Modifier.FINAL)
                    .initializer("new $L()", functorName)
                    .build())
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addMethod(
                MethodSpec.methodBuilder("instance")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(ClassName.get(packageName, functorName))
                    .addStatement("return INSTANCE")
                    .addJavadoc("Returns the singleton Functor instance.\n")
                    .build())
            .addMethod(mapMethod)
            .addJavadoc(
                "Functor instance for {@link $L}.\n\n@see $L\n@see $LKind\n",
                baseName,
                baseName,
                baseName);

    JavaFile.builder(packageName, functorBuilder.build())
        .addFileComment("Generated by hkj-processor. Do not edit.")
        .build()
        .writeTo(processingEnv.getFiler());
  }

  // =========================================================================
  // 4. Generate *Ops.java
  // =========================================================================

  private void generateOps(
      String packageName, String baseName, TypeElement source, List<TypeElement> permits)
      throws IOException {
    String kindName = baseName + "Kind";
    String helperName = baseName + "KindHelper";
    String functorName = baseName + "Functor";
    String opsName = baseName + "Ops";
    String singletonName = toUpperSnakeCase(baseName);
    ClassName witnessClass = ClassName.get(packageName, kindName, "Witness");
    ClassName sourceClass = ClassName.get(packageName, baseName);
    ClassName helperClass = ClassName.get(packageName, helperName);
    ClassName functorClass = ClassName.get(packageName, functorName);

    TypeSpec.Builder opsBuilder =
        TypeSpec.classBuilder(opsName)
            .addAnnotation(NULL_MARKED)
            .addAnnotation(GENERATED)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addField(
                FieldSpec.builder(
                        functorClass, "FUNCTOR", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$T.instance()", functorClass)
                    .build())
            .addMethod(
                MethodSpec.methodBuilder("functor")
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                    .returns(functorClass)
                    .addStatement("return FUNCTOR")
                    .build());

    // Static factory method per permit
    for (TypeElement permit : permits) {
      opsBuilder.addMethod(
          generateOpsMethod(
              permit,
              baseName,
              packageName,
              sourceClass,
              helperClass,
              singletonName,
              witnessClass));
    }

    // Bound inner class
    opsBuilder.addType(
        generateBoundClass(
            permits,
            baseName,
            packageName,
            sourceClass,
            helperClass,
            singletonName,
            witnessClass,
            functorClass));

    // boundTo factory method
    TypeVariableName typeG =
        TypeVariableName.get(
            "G",
            ParameterizedTypeName.get(
                WITNESS_ARITY, ClassName.get("org.higherkindedj.hkt", "TypeArity", "Unary")));
    opsBuilder.addMethod(
        MethodSpec.methodBuilder("boundTo")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addTypeVariable(typeG)
            .addParameter(ParameterizedTypeName.get(INJECT, witnessClass, typeG), "inject")
            .addParameter(ParameterizedTypeName.get(FUNCTOR, typeG), "functorG")
            .returns(ParameterizedTypeName.get(ClassName.get(packageName, opsName, "Bound"), typeG))
            .addStatement("return new Bound<>(inject, functorG)")
            .addJavadoc(
                "Creates a Bound instance for combined-effect programs.\n\n"
                    + "@param inject The Inject instance for embedding into the combined effect type\n"
                    + "@param functorG The Functor for the combined effect type\n"
                    + "@param <G> The combined effect type\n"
                    + "@return A Bound instance\n")
            .build());

    opsBuilder.addJavadoc(
        "Smart constructors for {@link $L} operations, lifting them into the Free monad.\n\n"
            + "@see $L\n@see $LKind\n",
        baseName,
        baseName,
        baseName);

    JavaFile.builder(packageName, opsBuilder.build())
        .addFileComment("Generated by hkj-processor. Do not edit.")
        .build()
        .writeTo(processingEnv.getFiler());
  }

  private MethodSpec generateOpsMethod(
      TypeElement permit,
      String baseName,
      String packageName,
      ClassName sourceClass,
      ClassName helperClass,
      String singletonName,
      ClassName witnessClass) {
    String methodName = permitToMethodName(permit);
    List<? extends RecordComponentElement> components = permit.getRecordComponents();
    TypeVariableName typeA = TypeVariableName.get("A");

    MethodSpec.Builder builder =
        MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addTypeVariable(typeA);

    // Add parameters for all record components
    for (RecordComponentElement component : components) {
      builder.addParameter(TypeName.get(component.asType()), component.getSimpleName().toString());
    }

    // Return type: Free<Witness, A>
    builder.returns(ParameterizedTypeName.get(FREE, witnessClass, typeA));

    // Build constructor call
    StringBuilder constructorArgs = new StringBuilder();
    for (int i = 0; i < components.size(); i++) {
      if (i > 0) constructorArgs.append(", ");
      constructorArgs.append(components.get(i).getSimpleName());
    }

    ClassName permitClass = ClassName.get(packageName, baseName, permit.getSimpleName().toString());

    builder.addStatement(
        "$T<A> op = new $T<>($L)", sourceClass, permitClass, constructorArgs.toString());
    builder.addStatement(
        "return $T.liftF($T.$L.widen(op), functor())", FREE, helperClass, singletonName);

    builder.addJavadoc(
        "Lifts a {@code $L} into a Free program.\n\n"
            + "@param <A> The result type\n"
            + "@return A Free program wrapping the operation\n",
        permit.getSimpleName());

    return builder.build();
  }

  private TypeSpec generateBoundClass(
      List<TypeElement> permits,
      String baseName,
      String packageName,
      ClassName sourceClass,
      ClassName helperClass,
      String singletonName,
      ClassName witnessClass,
      ClassName functorClass) {

    TypeVariableName typeG =
        TypeVariableName.get(
            "G",
            ParameterizedTypeName.get(
                WITNESS_ARITY, ClassName.get("org.higherkindedj.hkt", "TypeArity", "Unary")));

    TypeSpec.Builder boundBuilder =
        TypeSpec.classBuilder("Bound")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addTypeVariable(typeG)
            .addField(
                ParameterizedTypeName.get(INJECT, witnessClass, typeG),
                "inject",
                Modifier.PRIVATE,
                Modifier.FINAL)
            .addField(
                ParameterizedTypeName.get(FUNCTOR, typeG),
                "functorG",
                Modifier.PRIVATE,
                Modifier.FINAL);

    // Constructor
    boundBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addParameter(ParameterizedTypeName.get(INJECT, witnessClass, typeG), "inject")
            .addParameter(ParameterizedTypeName.get(FUNCTOR, typeG), "functorG")
            .addStatement(
                "this.inject = $T.requireNonNull(inject, $S)", OBJECTS, "inject must not be null")
            .addStatement(
                "this.functorG = $T.requireNonNull(functorG, $S)",
                OBJECTS,
                "functorG must not be null")
            .build());

    // Bound method per permit
    for (TypeElement permit : permits) {
      boundBuilder.addMethod(
          generateBoundMethod(permit, baseName, packageName, witnessClass, typeG));
    }

    boundBuilder.addJavadoc(
        "Bound instance for using $L in combined-effect programs.\n\n"
            + "@param <G> The combined effect type\n",
        baseName);

    return boundBuilder.build();
  }

  private MethodSpec generateBoundMethod(
      TypeElement permit,
      String baseName,
      String packageName,
      ClassName witnessClass,
      TypeVariableName typeG) {
    String methodName = permitToMethodName(permit);
    String opsName = baseName + "Ops";
    List<? extends RecordComponentElement> components = permit.getRecordComponents();
    TypeVariableName typeA = TypeVariableName.get("A");

    MethodSpec.Builder builder =
        MethodSpec.methodBuilder(methodName).addModifiers(Modifier.PUBLIC).addTypeVariable(typeA);

    // Add parameters for record components
    for (RecordComponentElement component : components) {
      builder.addParameter(TypeName.get(component.asType()), component.getSimpleName().toString());
    }

    builder.returns(ParameterizedTypeName.get(FREE, typeG, typeA));

    // Build args for standalone call
    StringBuilder args = new StringBuilder();
    for (int i = 0; i < components.size(); i++) {
      if (i > 0) args.append(", ");
      args.append(components.get(i).getSimpleName());
    }

    builder.addStatement(
        "$T<$T, A> standalone = $L.$L($L)",
        FREE,
        witnessClass,
        opsName,
        methodName,
        args.toString());
    builder.addStatement("return $T.translate(standalone, inject::inject, functorG)", FREE);

    builder.addJavadoc(
        "Lifts a {@code $L} into the combined effect type.\n\n"
            + "@param <A> The result type\n"
            + "@return A Free program in the combined effect type\n",
        permit.getSimpleName());

    return builder.build();
  }

  // =========================================================================
  // 5. Generate *Interpreter.java
  // =========================================================================

  private void generateInterpreter(
      String packageName, String baseName, TypeElement source, List<TypeElement> permits)
      throws IOException {
    String kindName = baseName + "Kind";
    String helperName = baseName + "KindHelper";
    String interpreterName = baseName + "Interpreter";
    String singletonName = toUpperSnakeCase(baseName);
    ClassName witnessClass = ClassName.get(packageName, kindName, "Witness");
    ClassName sourceClass = ClassName.get(packageName, baseName);
    ClassName helperClass = ClassName.get(packageName, helperName);

    TypeVariableName typeM =
        TypeVariableName.get(
            "M",
            ParameterizedTypeName.get(
                WITNESS_ARITY, ClassName.get("org.higherkindedj.hkt", "TypeArity", "Unary")));
    TypeVariableName typeA = TypeVariableName.get("A");

    TypeSpec.Builder interpreterBuilder =
        TypeSpec.classBuilder(interpreterName)
            .addAnnotation(NULL_MARKED)
            .addAnnotation(GENERATED)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addTypeVariable(typeM)
            .addSuperinterface(ParameterizedTypeName.get(NATURAL, witnessClass, typeM));

    // Abstract handle method per permit
    for (TypeElement permit : permits) {
      String handleName = "handle" + permit.getSimpleName();
      ClassName permitClass =
          ClassName.get(packageName, baseName, permit.getSimpleName().toString());

      interpreterBuilder.addMethod(
          MethodSpec.methodBuilder(handleName)
              .addModifiers(Modifier.PROTECTED, Modifier.ABSTRACT)
              .addTypeVariable(typeA)
              .addParameter(ParameterizedTypeName.get(permitClass, typeA), "op")
              .returns(ParameterizedTypeName.get(KIND, typeM, typeA))
              .addJavadoc(
                  "Handles a {@code $L} operation.\n\n"
                      + "@param op The operation to handle\n"
                      + "@param <A> The result type\n"
                      + "@return The interpreted result in the target monad\n",
                  permit.getSimpleName())
              .build());
    }

    // apply() method with switch dispatch
    MethodSpec.Builder applyBuilder =
        MethodSpec.methodBuilder("apply")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addTypeVariable(typeA)
            .addParameter(ParameterizedTypeName.get(KIND, witnessClass, typeA), "fa")
            .returns(ParameterizedTypeName.get(KIND, typeM, typeA))
            .addStatement("$T.kind().requireNonNull(fa, $T.FROM_KIND)", VALIDATION, OPERATION)
            .addStatement("$T<A> op = $T.$L.narrow(fa)", sourceClass, helperClass, singletonName);

    // Build switch expression
    StringBuilder switchBlock = new StringBuilder();
    switchBlock.append("return switch (op) {\n");
    for (TypeElement permit : permits) {
      String handleName = "handle" + permit.getSimpleName();
      ClassName permitClass =
          ClassName.get(packageName, baseName, permit.getSimpleName().toString());
      switchBlock
          .append("      case $")
          .append(permit.getSimpleName())
          .append("<A> p -> ")
          .append(handleName)
          .append("(p);\n");
    }
    switchBlock.append("    }");

    // Use addCode with format string for the switch
    applyBuilder.addCode("return switch (op) {\n");
    for (TypeElement permit : permits) {
      String handleName = "handle" + permit.getSimpleName();
      ClassName permitClass =
          ClassName.get(packageName, baseName, permit.getSimpleName().toString());
      applyBuilder.addCode("  case $T<A> p -> $L(p);\n", permitClass, handleName);
    }
    applyBuilder.addCode("};\n");

    interpreterBuilder.addMethod(applyBuilder.build());

    interpreterBuilder.addJavadoc(
        "Abstract interpreter skeleton for {@link $L}.\n\n"
            + "<p>Subclasses must implement a handler for each sealed permit. The {@link #apply}\n"
            + "method dispatches via pattern matching.\n\n"
            + "@param <M> The target monad type\n"
            + "@see $L\n@see $LKind\n",
        baseName,
        baseName,
        baseName);

    JavaFile.builder(packageName, interpreterBuilder.build())
        .addFileComment("Generated by hkj-processor. Do not edit.")
        .build()
        .writeTo(processingEnv.getFiler());
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private String resolveTargetPackage(TypeElement typeElement, EffectAlgebra annotation) {
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

  private boolean hasMapKMethod(TypeElement sealedInterface) {
    for (Element enclosed : sealedInterface.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.METHOD
          && enclosed.getSimpleName().toString().equals("mapK")) {
        return true;
      }
    }
    return false;
  }

  /** Converts a permit record name to a method name (lowercase first letter). */
  private String permitToMethodName(TypeElement permit) {
    String name = permit.getSimpleName().toString();
    return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
  }

  /** Converts a class name like "FooOp" to "FOO_OP" for enum singleton naming. */
  private static String toUpperSnakeCase(String name) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (Character.isUpperCase(c) && i > 0) {
        // Only add underscore if previous char is lowercase
        if (Character.isLowerCase(name.charAt(i - 1))) {
          sb.append('_');
        }
      }
      sb.append(Character.toUpperCase(c));
    }
    return sb.toString();
  }

  private void error(String msg, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element);
  }
}
