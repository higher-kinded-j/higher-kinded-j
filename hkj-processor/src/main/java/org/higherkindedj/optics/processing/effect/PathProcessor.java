// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import java.io.IOException;
import java.util.*;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.PathVia;

/**
 * Annotation processor that generates Path bridge classes for service interfaces.
 *
 * <p>This processor handles {@link GeneratePathBridge} annotations on interfaces and generates
 * companion classes that wrap service methods returning effect types (Optional, Either, Try, etc.)
 * into corresponding Path types.
 *
 * <h2>Generated Code Structure</h2>
 *
 * <p>For an interface {@code UserService}, the processor generates {@code UserServicePaths} with:
 *
 * <ul>
 *   <li>A constructor taking the delegate interface
 *   <li>Methods for each {@link PathVia}-annotated method, returning the appropriate Path type
 * </ul>
 *
 * @see GeneratePathBridge
 * @see PathVia
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.hkt.effect.annotation.GeneratePathBridge")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class PathProcessor extends AbstractProcessor {

  private static final ClassName OBJECTS = ClassName.get("java.util", "Objects");
  private static final ClassName PATH = ClassName.get("org.higherkindedj.hkt.effect", "Path");
  private static final ClassName SEMIGROUP = ClassName.get("org.higherkindedj.hkt", "Semigroup");
  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");

  // Path type class names
  private static final ClassName MAYBE_PATH =
      ClassName.get("org.higherkindedj.hkt.effect", "MaybePath");
  private static final ClassName EITHER_PATH =
      ClassName.get("org.higherkindedj.hkt.effect", "EitherPath");
  private static final ClassName TRY_PATH =
      ClassName.get("org.higherkindedj.hkt.effect", "TryPath");
  private static final ClassName VALIDATION_PATH =
      ClassName.get("org.higherkindedj.hkt.effect", "ValidationPath");
  private static final ClassName OPTIONAL_PATH =
      ClassName.get("org.higherkindedj.hkt.effect", "OptionalPath");
  private static final ClassName IO_PATH = ClassName.get("org.higherkindedj.hkt.effect", "IOPath");

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement annotation : annotations) {
      Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
      for (Element element : annotatedElements) {
        if (element.getKind() != ElementKind.INTERFACE) {
          error("@GeneratePathBridge can only be applied to interfaces.", element);
          continue;
        }
        try {
          generateBridgeClass((TypeElement) element);
        } catch (IOException e) {
          error("Could not generate Path bridge class: " + e.getMessage(), element);
        }
      }
    }
    return true;
  }

  private void generateBridgeClass(TypeElement interfaceElement) throws IOException {
    String interfaceName = interfaceElement.getSimpleName().toString();
    String defaultPackage =
        processingEnv
            .getElementUtils()
            .getPackageOf(interfaceElement)
            .getQualifiedName()
            .toString();

    GeneratePathBridge annotation = interfaceElement.getAnnotation(GeneratePathBridge.class);
    String targetPackage = annotation.targetPackage();
    String packageName = targetPackage.isEmpty() ? defaultPackage : targetPackage;
    String suffix = annotation.suffix();
    String bridgeClassName = interfaceName + suffix;

    ClassName interfaceClassName = ClassName.get(interfaceElement);

    // Build the bridge class
    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(bridgeClassName)
            .addAnnotation(GENERATED)
            .addJavadoc(
                "Generated Path bridge for {@link $T}.\n\n<p>Do not edit.\n", interfaceClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    // Add delegate field
    classBuilder.addField(
        FieldSpec.builder(interfaceClassName, "delegate", Modifier.PRIVATE, Modifier.FINAL)
            .build());

    // Add constructor
    classBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(interfaceClassName, "delegate")
            .addJavadoc(
                "Creates a new Path bridge wrapping the given delegate.\n\n"
                    + "@param delegate the service to wrap; must not be null\n")
            .addStatement(
                "this.delegate = $T.requireNonNull(delegate, $S)",
                OBJECTS,
                "delegate must not be null")
            .build());

    // Process @PathVia methods
    for (Element enclosed : interfaceElement.getEnclosedElements()) {
      if (enclosed.getKind() == ElementKind.METHOD) {
        ExecutableElement method = (ExecutableElement) enclosed;
        PathVia pathVia = method.getAnnotation(PathVia.class);
        if (pathVia != null) {
          MethodSpec bridgeMethod = createBridgeMethod(method, pathVia);
          if (bridgeMethod != null) {
            classBuilder.addMethod(bridgeMethod);
          }
        }
      }
    }

    // Write the file
    JavaFile javaFile =
        JavaFile.builder(packageName, classBuilder.build())
            .addFileComment("Generated by PathProcessor. Do not edit.")
            .build();

    javaFile.writeTo(processingEnv.getFiler());
  }

  private MethodSpec createBridgeMethod(ExecutableElement method, PathVia pathVia) {
    String methodName =
        pathVia.name().isEmpty() ? method.getSimpleName().toString() : pathVia.name();
    String doc = pathVia.doc();

    TypeMirror returnType = method.getReturnType();
    PathTypeMapping mapping = determinePathType(returnType);

    if (mapping == null) {
      error(
          "Unsupported return type for @PathVia: "
              + returnType
              + ". Supported types: Optional, Maybe, Either, Try, Validated, IO.",
          method);
      return null;
    }

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .returns(mapping.pathType());

    // Add documentation
    if (!doc.isEmpty()) {
      methodBuilder.addJavadoc("$L\n\n", doc);
    }
    methodBuilder.addJavadoc(
        "@return Path-wrapped result from {@link $L#$L}\n",
        method.getEnclosingElement().getSimpleName(),
        method.getSimpleName());

    // Copy parameters
    List<String> paramNames = new ArrayList<>();
    for (VariableElement param : method.getParameters()) {
      String paramName = param.getSimpleName().toString();
      paramNames.add(paramName);
      methodBuilder.addParameter(TypeName.get(param.asType()), paramName);
    }

    // Add Semigroup parameter for Validated types
    if (mapping.requiresSemigroup()) {
      methodBuilder.addParameter(
          ParameterizedTypeName.get(SEMIGROUP, mapping.errorType()), "semigroup");
      paramNames.add("semigroup");
    }

    // Build the method body
    String delegateCall =
        "delegate."
            + method.getSimpleName()
            + "("
            + String.join(", ", paramNames.subList(0, method.getParameters().size()))
            + ")";

    if (mapping.requiresSemigroup()) {
      methodBuilder.addStatement(
          "return $T.$L($L, semigroup)", PATH, mapping.factoryMethod(), delegateCall);
    } else {
      methodBuilder.addStatement("return $T.$L($L)", PATH, mapping.factoryMethod(), delegateCall);
    }

    return methodBuilder.build();
  }

  private PathTypeMapping determinePathType(TypeMirror returnType) {
    if (!(returnType instanceof DeclaredType declaredType)) {
      return null;
    }

    Element typeElement = declaredType.asElement();
    String typeName = typeElement.getSimpleName().toString();
    String qualifiedName =
        typeElement instanceof TypeElement te ? te.getQualifiedName().toString() : typeName;

    List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();

    return switch (qualifiedName) {
      case "java.util.Optional" -> {
        TypeName valueType =
            typeArgs.isEmpty() ? ClassName.get(Object.class) : TypeName.get(typeArgs.get(0));
        yield new PathTypeMapping(
            ParameterizedTypeName.get(OPTIONAL_PATH, valueType), "optional", false, null);
      }
      case "org.higherkindedj.hkt.maybe.Maybe" -> {
        TypeName valueType =
            typeArgs.isEmpty() ? ClassName.get(Object.class) : TypeName.get(typeArgs.get(0));
        yield new PathTypeMapping(
            ParameterizedTypeName.get(MAYBE_PATH, valueType), "maybe", false, null);
      }
      case "org.higherkindedj.hkt.either.Either" -> {
        TypeName errorType =
            typeArgs.size() >= 1 ? TypeName.get(typeArgs.get(0)) : ClassName.get(Object.class);
        TypeName valueType =
            typeArgs.size() >= 2 ? TypeName.get(typeArgs.get(1)) : ClassName.get(Object.class);
        yield new PathTypeMapping(
            ParameterizedTypeName.get(EITHER_PATH, errorType, valueType), "either", false, null);
      }
      case "org.higherkindedj.hkt.trymonad.Try" -> {
        TypeName valueType =
            typeArgs.isEmpty() ? ClassName.get(Object.class) : TypeName.get(typeArgs.get(0));
        yield new PathTypeMapping(
            ParameterizedTypeName.get(TRY_PATH, valueType), "tryPath", false, null);
      }
      case "org.higherkindedj.hkt.validated.Validated" -> {
        TypeName errorType =
            typeArgs.size() >= 1 ? TypeName.get(typeArgs.get(0)) : ClassName.get(Object.class);
        TypeName valueType =
            typeArgs.size() >= 2 ? TypeName.get(typeArgs.get(1)) : ClassName.get(Object.class);
        yield new PathTypeMapping(
            ParameterizedTypeName.get(VALIDATION_PATH, errorType, valueType),
            "validated",
            true,
            errorType);
      }
      case "org.higherkindedj.hkt.io.IO" -> {
        TypeName valueType =
            typeArgs.isEmpty() ? ClassName.get(Object.class) : TypeName.get(typeArgs.get(0));
        yield new PathTypeMapping(ParameterizedTypeName.get(IO_PATH, valueType), "io", false, null);
      }
      default -> null;
    };
  }

  private void error(String message, Element element) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
  }

  /**
   * Represents the mapping from a source type to a Path type.
   *
   * @param pathType the Path type to use
   * @param factoryMethod the Path factory method name
   * @param requiresSemigroup whether a Semigroup parameter is needed
   * @param errorType the error type (for Validated)
   */
  private record PathTypeMapping(
      TypeName pathType, String factoryMethod, boolean requiresSemigroup, TypeName errorType) {}
}
