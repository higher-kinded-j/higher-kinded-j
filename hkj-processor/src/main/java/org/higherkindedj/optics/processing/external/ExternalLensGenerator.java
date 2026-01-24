// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
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
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

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
  private final List<TraversableGenerator> traversalGenerators;

  /**
   * Creates a new ExternalLensGenerator.
   *
   * @param filer the filer for writing generated files
   * @param messager the messager for reporting diagnostics
   */
  public ExternalLensGenerator(Filer filer, Messager messager) {
    this.filer = filer;
    this.messager = messager;

    // Load traversal generators via SPI
    this.traversalGenerators = new ArrayList<>();
    ServiceLoader.load(TraversableGenerator.class, getClass().getClassLoader())
        .forEach(traversalGenerators::add);
  }

  /**
   * Generates a lenses class for an external record.
   *
   * @param analysis the type analysis for the record
   * @param targetPackage the target package for the generated class
   */
  public void generateForRecord(TypeAnalysis analysis, String targetPackage) {
    TypeElement recordElement = analysis.typeElement();
    String recordName = recordElement.getSimpleName().toString();
    String lensesClassName = recordName + "Lenses";

    TypeName recordTypeName = getParameterisedTypeName(recordElement);

    TypeSpec.Builder lensesClassBuilder =
        TypeSpec.classBuilder(lensesClassName)
            .addAnnotation(GENERATED_ANNOTATION)
            .addJavadoc(
                "Generated optics for {@link $T}. Do not edit.", ClassName.get(recordElement))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    List<? extends RecordComponentElement> components = recordElement.getRecordComponents();

    // Generate lens methods
    for (FieldInfo field : analysis.fields()) {
      lensesClassBuilder.addMethod(
          createRecordLensMethod(field, recordElement, components, recordTypeName));
    }

    // Generate with methods
    for (FieldInfo field : analysis.fields()) {
      lensesClassBuilder.addMethod(createWithMethod(field, recordElement, recordTypeName));
    }

    // Generate traversal methods for container fields
    for (FieldInfo field : analysis.fields()) {
      if (field.hasTraversal()) {
        MethodSpec traversalMethod =
            createTraversalMethod(field, recordElement, components, recordTypeName);
        if (traversalMethod != null) {
          lensesClassBuilder.addMethod(traversalMethod);
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
   */
  public void generateForWitherClass(TypeAnalysis analysis, String targetPackage) {
    TypeElement classElement = analysis.typeElement();
    String className = classElement.getSimpleName().toString();
    String lensesClassName = className + "Lenses";

    TypeName classTypeName = getParameterisedTypeName(classElement);

    TypeSpec.Builder lensesClassBuilder =
        TypeSpec.classBuilder(lensesClassName)
            .addAnnotation(GENERATED_ANNOTATION)
            .addJavadoc(
                "Generated optics for {@link $T}. Do not edit.", ClassName.get(classElement))
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    // Generate lens methods using wither pattern
    for (int i = 0; i < analysis.fields().size(); i++) {
      FieldInfo field = analysis.fields().get(i);
      WitherInfo wither = analysis.witherMethods().get(i);
      lensesClassBuilder.addMethod(
          createWitherLensMethod(field, wither, classElement, classTypeName));
    }

    // Generate with methods
    for (int i = 0; i < analysis.fields().size(); i++) {
      FieldInfo field = analysis.fields().get(i);
      lensesClassBuilder.addMethod(createWithMethod(field, classElement, classTypeName));
    }

    writeFile(targetPackage, lensesClassBuilder.build());
  }

  private MethodSpec createRecordLensMethod(
      FieldInfo field,
      TypeElement recordElement,
      List<? extends RecordComponentElement> allComponents,
      TypeName recordTypeName) {

    TypeName componentTypeName = TypeName.get(field.type());

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
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(lensTypeName);

    for (TypeParameterElement typeParam : recordElement.getTypeParameters()) {
      methodBuilder.addTypeVariable(TypeVariableName.get(typeParam));
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
      FieldInfo field, WitherInfo wither, TypeElement classElement, TypeName classTypeName) {

    TypeName fieldTypeName = TypeName.get(field.type());

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
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(lensTypeName);

    for (TypeParameterElement typeParam : classElement.getTypeParameters()) {
      methodBuilder.addTypeVariable(TypeVariableName.get(typeParam));
    }

    // Use wither method for setting: source.withYear(newValue)
    methodBuilder.addStatement(
        "return $T.of($T::$L, (source, newValue) -> source.$L(newValue))",
        Lens.class,
        classTypeName,
        wither.getterMethodName(),
        wither.witherMethodName());

    return methodBuilder.build();
  }

  private MethodSpec createWithMethod(FieldInfo field, TypeElement typeElement, TypeName typeName) {

    TypeName fieldTypeName = TypeName.get(field.type());
    String methodName = "with" + capitalise(field.name());
    String parameterName = "new" + capitalise(field.name());
    String lensesClassName = typeElement.getSimpleName().toString() + "Lenses";

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
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(typeName)
            .addParameter(typeName, "source")
            .addParameter(fieldTypeName, parameterName);

    List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
    for (TypeParameterElement typeParam : typeParameters) {
      methodBuilder.addTypeVariable(TypeVariableName.get(typeParam));
    }

    String typeArguments =
        typeParameters.stream()
            .map(p -> p.getSimpleName().toString())
            .collect(Collectors.joining(", "));

    if (typeArguments.isEmpty()) {
      methodBuilder.addStatement("return $L().set($L, source)", field.name(), parameterName);
    } else {
      methodBuilder.addStatement(
          "return $L.<$L>$L().set($L, source)",
          lensesClassName,
          typeArguments,
          field.name(),
          parameterName);
    }

    return methodBuilder.build();
  }

  private MethodSpec createTraversalMethod(
      FieldInfo field,
      TypeElement recordElement,
      List<? extends RecordComponentElement> allComponents,
      TypeName recordTypeName) {

    // Find the appropriate traversal generator
    TraversableGenerator generator = null;
    for (TraversableGenerator g : traversalGenerators) {
      if (g.supports(field.type())) {
        generator = g;
        break;
      }
    }

    if (generator == null) {
      return null; // No generator found for this container type
    }

    // Find the matching record component
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

    final TypeName focusType = getFocusType(field.type(), generator);
    if (focusType == null) {
      return null;
    }

    final ClassName recordClassName = ClassName.get(recordElement);
    final ParameterizedTypeName traversalTypeName =
        ParameterizedTypeName.get(ClassName.get(Traversal.class), recordClassName, focusType);

    final CodeBlock modifyFBody =
        generator.generateModifyF(component, recordClassName, allComponents);

    // Create F extends WitnessArity<TypeArity.Unary>
    final ParameterizedTypeName witnessArityBound =
        ParameterizedTypeName.get(
            ClassName.get(WitnessArity.class), ClassName.get(TypeArity.class).nestedClass("Unary"));

    final TypeSpec traversalImpl =
        TypeSpec.anonymousClassBuilder("")
            .addSuperinterface(traversalTypeName)
            .addMethod(
                MethodSpec.methodBuilder("modifyF")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .addTypeVariable(TypeVariableName.get("F", witnessArityBound))
                    .addParameter(
                        ParameterizedTypeName.get(
                            ClassName.get(Function.class),
                            focusType,
                            ParameterizedTypeName.get(
                                ClassName.get(Kind.class), TypeVariableName.get("F"), focusType)),
                        "f")
                    .addParameter(recordClassName, "source")
                    .addParameter(
                        ParameterizedTypeName.get(
                            ClassName.get(Applicative.class), TypeVariableName.get("F")),
                        "applicative")
                    .returns(
                        ParameterizedTypeName.get(
                            ClassName.get(Kind.class), TypeVariableName.get("F"), recordClassName))
                    .addCode(modifyFBody)
                    .build())
            .build();

    String methodName = field.name() + "Traversal";

    return MethodSpec.methodBuilder(methodName)
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
            recordClassName,
            focusType.box())
        .returns(traversalTypeName)
        .addStatement("return $L", traversalImpl)
        .build();
  }

  private TypeName getFocusType(TypeMirror type, TraversableGenerator generator) {
    if (type instanceof ArrayType arrayType) {
      return TypeName.get(arrayType.getComponentType()).box();
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
      return TypeName.get(declaredType.getTypeArguments().get(typeArgumentIndex)).box();
    }
    return null;
  }

  private TypeName getParameterisedTypeName(TypeElement typeElement) {
    List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
    if (typeParameters.isEmpty()) {
      return ClassName.get(typeElement);
    } else {
      List<TypeVariableName> typeVars = typeParameters.stream().map(TypeVariableName::get).toList();
      return ParameterizedTypeName.get(
          ClassName.get(typeElement), typeVars.toArray(new TypeName[0]));
    }
  }

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

  private String capitalise(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
  }
}
