// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.processing.GeneratorRegistry;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
import org.higherkindedj.optics.processing.util.NestedOptic;
import org.higherkindedj.optics.processing.util.NestedTypeNames;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Generates lens classes for external types (records and wither-based classes).
 *
 * <p>This generator creates a utility class with static methods that return {@link Lens} instances
 * for each field/component of the target type. For record types, the canonical constructor is used
 * for immutable updates. For wither-based classes, the corresponding wither method is used.
 */
public class ExternalLensGenerator {

  private static final ClassName GENERATED_ANNOTATION =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");

  private final Filer filer;
  private final Messager messager;
  private final GeneratorRegistry generatorRegistry;

  /**
   * Creates a new ExternalLensGenerator.
   *
   * @param filer the filer for writing generated files
   * @param messager the messager for reporting diagnostics
   */
  public ExternalLensGenerator(Filer filer, Messager messager) {
    this.filer = filer;
    this.messager = messager;
    this.generatorRegistry =
        GeneratorRegistry.fromServiceLoader(getClass().getClassLoader(), messager);
  }

  /**
   * Creates a new ExternalLensGenerator with an explicit list of traversal generators.
   *
   * <p>Package-private; intended for tests that need to control the available generators.
   *
   * @param filer the filer for writing generated files
   * @param messager the messager for reporting diagnostics
   * @param traversalGenerators the traversal generators to use instead of SPI discovery
   */
  ExternalLensGenerator(
      Filer filer, Messager messager, List<TraversableGenerator> traversalGenerators) {
    this.filer = filer;
    this.messager = messager;
    this.generatorRegistry = GeneratorRegistry.of(traversalGenerators, messager);
  }

  /**
   * Generates a lenses class for an external record.
   *
   * @param analysis the type analysis for the record
   * @param targetPackage the target package for the generated class
   * @param originatingElement the annotated element that triggered generation
   */
  public void generateForRecord(
      TypeAnalysis analysis, String targetPackage, Element originatingElement) {
    TypeElement recordElement = analysis.typeElement();
    String recordName = recordElement.getSimpleName().toString();
    String lensesClassName = recordName + "Lenses";

    TypeName recordTypeName = getParameterisedTypeName(recordElement, targetPackage);

    TypeSpec.Builder lensesClassBuilder =
        TypeSpec.classBuilder(lensesClassName)
            .addAnnotation(GENERATED_ANNOTATION)
            .addJavadoc(
                "Generated optics for {@link $T}. Do not edit.", ClassName.get(recordElement))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addOriginatingElement(originatingElement);

    List<? extends RecordComponentElement> components = recordElement.getRecordComponents();

    // Generate lens methods
    for (FieldInfo field : analysis.fields()) {
      lensesClassBuilder.addMethod(
          createRecordLensMethod(field, recordElement, components, recordTypeName, targetPackage));
    }

    // Generate with methods
    for (FieldInfo field : analysis.fields()) {
      lensesClassBuilder.addMethod(
          createWithMethod(
              field,
              recordElement,
              recordTypeName,
              ClassName.get(targetPackage, lensesClassName),
              targetPackage));
    }

    // Generate traversal methods for container fields
    final NestedTypeNames names = new NestedTypeNames(lensesClassName);
    for (FieldInfo field : analysis.fields()) {
      if (field.hasTraversal()) {
        NestedOptic traversal =
            createTraversal(field, recordElement, components, recordTypeName, names, targetPackage);
        if (traversal != null) {
          traversal.addTo(lensesClassBuilder);
        }
      }
    }

    writeFile(targetPackage, lensesClassBuilder.build());
  }

  /**
   * Generates a lenses class for a wither-based class.
   *
   * @param analysis the type analysis for the class
   * @param targetPackage the target package for the generated class
   * @param originatingElement the annotated element that triggered generation
   */
  public void generateForWitherClass(
      TypeAnalysis analysis, String targetPackage, Element originatingElement) {
    TypeElement classElement = analysis.typeElement();
    String className = classElement.getSimpleName().toString();
    String lensesClassName = className + "Lenses";

    TypeName classTypeName = getParameterisedTypeName(classElement, targetPackage);

    TypeSpec.Builder lensesClassBuilder =
        TypeSpec.classBuilder(lensesClassName)
            .addAnnotation(GENERATED_ANNOTATION)
            .addJavadoc(
                "Generated optics for {@link $T}. Do not edit.", ClassName.get(classElement))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addOriginatingElement(originatingElement);

    // Generate lens methods using wither pattern
    for (int i = 0; i < analysis.fields().size(); i++) {
      FieldInfo field = analysis.fields().get(i);
      WitherInfo wither = analysis.witherMethods().get(i);
      lensesClassBuilder.addMethod(
          createWitherLensMethod(field, wither, classElement, classTypeName, targetPackage));
    }

    // Generate with methods
    for (int i = 0; i < analysis.fields().size(); i++) {
      FieldInfo field = analysis.fields().get(i);
      lensesClassBuilder.addMethod(
          createWithMethod(
              field,
              classElement,
              classTypeName,
              ClassName.get(targetPackage, lensesClassName),
              targetPackage));
    }

    for (TypeAnalysis.LeftOutWither leftOut : analysis.withersLeftOut()) {
      noteWitherLeftOut(className, leftOut, originatingElement);
    }

    writeFile(targetPackage, lensesClassBuilder.build());
  }

  /**
   * Notes a wither no lens is generated from, because another wither reaches the same field name.
   *
   * <p>A note rather than a warning: the class is someone else's, and the author of the import
   * cannot change how it spells its getters. What they can do is name the pair they want through a
   * spec interface, which the fix says.
   */
  private void noteWitherLeftOut(
      String className, TypeAnalysis.LeftOutWither leftOut, Element originatingElement) {
    Diagnostics.note(
        messager,
        originatingElement,
        "@ImportOptics",
        "'"
            + className
            + "' pairs more than one wither with the field '"
            + leftOut.skipped().fieldName()
            + "', and only one lens can carry that name.",
        signatureOf(leftOut.generated())
            + " pairs with '"
            + leftOut.generated().getterMethodName()
            + "()' and is generated; "
            + signatureOf(leftOut.skipped())
            + " pairs with '"
            + leftOut.skipped().getterMethodName()
            + "()' and is left out.",
        "Name the one you want through a spec interface's @Wither, importing the type there"
            + " rather than by class literal.");
  }

  /** A wither as the note names it, {@code 'withN(int)'}. */
  private static String signatureOf(WitherInfo wither) {
    return "'"
        + wither.witherMethodName()
        + "("
        + ProcessorUtils.simpleTypeName(wither.parameterType())
        + ")'";
  }

  private MethodSpec createRecordLensMethod(
      FieldInfo field,
      TypeElement recordElement,
      List<? extends RecordComponentElement> allComponents,
      TypeName recordTypeName,
      String targetPackage) {

    TypeName componentTypeName = ProcessorUtils.typeNameOf(field.type(), targetPackage);

    ParameterizedTypeName lensTypeName =
        ParameterizedTypeName.get(
            ClassName.get(Lens.class), recordTypeName, componentTypeName.box());

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(field.name())
            .addJavadoc(
                "Creates a {@link $T} for the {@code $L} field of a {@link $T}.\n\n"
                    + "@return A non-null {@code Lens<$T, $T>}.",
                Lens.class,
                field.name(),
                recordTypeName,
                recordTypeName,
                componentTypeName.box())
            // The lens type and the record's type-parameter bounds are written out here.
            .addAnnotations(ProcessorUtils.rawTypesSuppression(field.type(), recordElement))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(lensTypeName);

    for (TypeParameterElement typeParam : recordElement.getTypeParameters()) {
      methodBuilder.addTypeVariable(ProcessorUtils.typeVariableOf(typeParam, targetPackage));
    }

    String constructorArgs =
        allComponents.stream()
            .map(
                c ->
                    c.getSimpleName().toString().equals(field.name())
                        ? "newValue"
                        : "source." + c.getSimpleName() + "()")
            .collect(Collectors.joining(", "));

    methodBuilder.addStatement(
        "return $T.of($T::$L, (source, newValue) -> new $T($L))",
        Lens.class,
        recordTypeName,
        field.accessorMethod(),
        recordTypeName,
        constructorArgs);

    return methodBuilder.build();
  }

  private MethodSpec createWitherLensMethod(
      FieldInfo field,
      WitherInfo wither,
      TypeElement classElement,
      TypeName classTypeName,
      String targetPackage) {

    TypeName fieldTypeName = ProcessorUtils.typeNameOf(field.type(), targetPackage);
    List<TypeParameterElement> typeParameters = ProcessorUtils.typeParametersInScope(classElement);

    ParameterizedTypeName lensTypeName =
        ParameterizedTypeName.get(ClassName.get(Lens.class), classTypeName, fieldTypeName.box());

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(field.name())
            .addJavadoc(
                "Creates a {@link $T} for the {@code $L} field of a {@link $T}.\n\n"
                    + "@return A non-null {@code Lens<$T, $T>}.",
                Lens.class,
                field.name(),
                classTypeName,
                classTypeName,
                fieldTypeName.box())
            // The lens type and the class's type-parameter bounds are written out here.
            .addAnnotations(ProcessorUtils.rawTypesSuppression(field.type(), typeParameters))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(lensTypeName);

    for (TypeParameterElement typeParam : typeParameters) {
      methodBuilder.addTypeVariable(ProcessorUtils.typeVariableOf(typeParam, targetPackage));
    }

    // Set through the wither: source.withYear(newValue). The lens hands the setter a boxed value,
    // which would bind an overload taking the box, or a supertype of it, ahead of a wither taking
    // a primitive, so where the name is overloaded such a parameter is passed unboxed and the call
    // binds the method that paired.
    TypeMirror parameterType = wither.parameterType();
    CodeBlock argument =
        parameterType.getKind().isPrimitive() && wither.overloaded()
            ? CodeBlock.of("($T) newValue", TypeName.get(parameterType))
            : CodeBlock.of("newValue");
    methodBuilder.addStatement(
        "return $T.of($T::$L, (source, newValue) -> source.$L($L))",
        Lens.class,
        classTypeName,
        wither.getterMethodName(),
        wither.witherMethodName(),
        argument);

    return methodBuilder.build();
  }

  private MethodSpec createWithMethod(
      FieldInfo field,
      TypeElement typeElement,
      TypeName typeName,
      ClassName lensesClass,
      String targetPackage) {

    TypeName fieldTypeName = ProcessorUtils.typeNameOf(field.type(), targetPackage);
    String methodName = "with" + ProcessorUtils.capitalise(field.name());
    String parameterName = "new" + ProcessorUtils.capitalise(field.name());
    List<TypeParameterElement> typeParameters = ProcessorUtils.typeParametersInScope(typeElement);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addJavadoc(
                "Creates a new {@link $T} instance with an updated {@code $L} field.\n"
                    + "<p>This is a convenience method that uses the {@link #$L()} lens.\n\n"
                    + "@param source The original {@code $T} instance.\n"
                    + "@param $L The new value for the {@code $L} field.\n"
                    + "@return A new, updated {@code $T} instance.",
                typeName,
                field.name(),
                field.name(),
                typeName,
                parameterName,
                field.name(),
                typeName)
            .addAnnotations(ProcessorUtils.rawTypesSuppression(field.type(), typeParameters))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(typeName)
            .addParameter(typeName, "source")
            .addParameter(fieldTypeName, parameterName);

    for (TypeParameterElement typeParam : typeParameters) {
      methodBuilder.addTypeVariable(ProcessorUtils.typeVariableOf(typeParam, targetPackage));
    }

    String typeArguments =
        typeParameters.stream()
            .map(p -> p.getSimpleName().toString())
            .collect(Collectors.joining(", "));

    if (typeArguments.isEmpty()) {
      methodBuilder.addStatement("return $L().set($L, source)", field.name(), parameterName);
    } else {
      // $T, not the simple name: a type parameter named like the companion would shadow it.
      methodBuilder.addStatement(
          "return $T.<$L>$L().set($L, source)",
          lensesClass,
          typeArguments,
          field.name(),
          parameterName);
    }

    return methodBuilder.build();
  }

  // Package-private for tests.
  NestedOptic createTraversal(
      FieldInfo field,
      TypeElement recordElement,
      List<? extends RecordComponentElement> allComponents,
      TypeName recordTypeName,
      NestedTypeNames names,
      String targetPackage) {

    // Find the matching record component first: it anchors any conflict warning
    RecordComponentElement component = null;
    for (RecordComponentElement c : allComponents) {
      if (c.getSimpleName().toString().equals(field.name())) {
        component = c;
        break;
      }
    }

    if (component == null) {
      return null;
    }

    final TraversableGenerator generator = generatorRegistry.generatorFor(field.type(), component);
    if (generator == null) {
      return null; // No generator found for this container type
    }

    final TypeName focusType = getFocusType(field.type(), generator, targetPackage);
    if (focusType == null) {
      return null;
    }

    final ClassName recordClassName = ClassName.get(recordElement);
    // The instantiated name, not the raw one: the generated method declares the record's own type
    // parameters, so every use of the record in this signature has to name them.
    final ParameterizedTypeName traversalTypeName =
        ParameterizedTypeName.get(ClassName.get(Traversal.class), recordTypeName, focusType);

    final CodeBlock modifyFBody =
        generator.generateModifyF(component, recordClassName, allComponents);

    // Create F extends WitnessArity<TypeArity.Unary>
    final ParameterizedTypeName witnessArityBound =
        ParameterizedTypeName.get(
            ClassName.get(WitnessArity.class), ClassName.get(TypeArity.class).nestedClass("Unary"));

    // A record that claims F for a parameter of its own takes the effect elsewhere, read from the
    // one place the generators writing uses of it also read.
    final TypeVariableName effect =
        TypeVariableName.get(ProcessorUtils.effectVariableName(recordElement), witnessArityBound);

    final String implementationName = names.claim(field.name(), "Traversal");
    final TypeSpec.Builder implementation =
        TypeSpec.classBuilder(implementationName)
            .addAnnotation(GENERATED_ANNOTATION)
            // The class names the traversal type in its superinterface clause and redeclares the
            // record's type parameters, neither of which a member annotation reaches.
            .addAnnotations(ProcessorUtils.rawTypesSuppression(field.type(), recordElement))
            .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
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
                    .build());

    String methodName = field.name() + "Traversal";

    final MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addAnnotations(ProcessorUtils.rawTypesSuppression(field.type(), recordElement))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addJavadoc(
                "Creates a {@link $T} for the {@code $L} field of a {@link $T}.\n"
                    + "<p>This traversal focuses on all items within the {@code $L} collection,"
                    + " allowing an effectful function\n"
                    + "to be applied to each one.\n\n"
                    + "@return A non-null {@code Traversal<$T, $T>}.",
                ClassName.get(Traversal.class),
                field.name(),
                recordClassName,
                field.name(),
                recordTypeName,
                focusType.box())
            .returns(traversalTypeName);

    // The record's own parameters, as the lens methods beside this one already declare them, and
    // on the implementation too, which is declared beside the factory rather than inside it.
    for (TypeParameterElement typeParameter : recordElement.getTypeParameters()) {
      TypeVariableName typeVariable = ProcessorUtils.typeVariableOf(typeParameter, targetPackage);
      methodBuilder.addTypeVariable(typeVariable);
      implementation.addTypeVariable(typeVariable);
    }
    final String typeArguments = recordElement.getTypeParameters().isEmpty() ? "" : "<>";

    return new NestedOptic(
        implementation.build(),
        methodBuilder.addStatement("return new $L$L()", implementationName, typeArguments).build());
  }

  // Package-private for tests.
  TypeName getFocusType(TypeMirror type, TraversableGenerator generator, String targetPackage) {
    if (type instanceof ArrayType arrayType) {
      return ProcessorUtils.typeNameOf(arrayType.getComponentType(), targetPackage).box();
    } else if (type instanceof DeclaredType declaredType) {
      if (declaredType.getTypeArguments().isEmpty()) {
        return null; // Cannot traverse a raw type.
      }

      // Use the generator's SPI method to determine which type argument to focus on.
      // For most types (List, Optional, etc.) this is 0.
      // For types like Either<L,R>, Validated<E,A>, Map<K,V> this is 1.
      int typeArgumentIndex = generator.getFocusTypeArgumentIndex();

      if (declaredType.getTypeArguments().size() <= typeArgumentIndex) {
        return null; // Not enough type arguments for this generator.
      }
      // A wildcard argument is resolved: no class can implement a traversal type that names one.
      return ProcessorUtils.resolvedTypeNameOf(
          declaredType.getTypeArguments().get(typeArgumentIndex));
    }
    return null;
  }

  // The type under its own type variables, and an inner class under its enclosing class's too,
  // Outer<X>.Line: named from the element alone it would be raw.
  private TypeName getParameterisedTypeName(TypeElement typeElement, String targetPackage) {
    return ProcessorUtils.typeNameOf(typeElement.asType(), targetPackage);
  }

  @ExcludeFromJacocoGeneratedReport
  private void writeFile(String packageName, TypeSpec typeSpec) {
    try {
      JavaFile.builder(packageName, typeSpec)
          .addFileComment("Generated by hkj-optics-processor. Do not edit.")
          .build()
          .writeTo(filer);
    } catch (IOException e) {
      messager.printMessage(
          Diagnostic.Kind.ERROR, "Could not write generated file: " + e.getMessage());
    }
  }
}
