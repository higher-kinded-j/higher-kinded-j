// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.annotations.GenerateIsos;

/**
 * An annotation processor that generates a container class with static Iso fields for each method
 * annotated with {@link GenerateIsos}.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateIsos")
@SupportedSourceVersion(SourceVersion.RELEASE_24)
public final class IsoProcessor extends AbstractProcessor {

  @Override
  public boolean process(
      final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
    for (final TypeElement annotation : annotations) {
      for (final Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element.getKind() == ElementKind.METHOD) {
          try {
            processMethod((ExecutableElement) element);
          } catch (final IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
          }
        }
      }
    }
    return true;
  }

  private void processMethod(final ExecutableElement method) throws IOException {
    final TypeElement classElement = (TypeElement) method.getEnclosingElement();
    final String methodName = method.getSimpleName().toString();
    final String className = classElement.getSimpleName().toString();
    final String packageName =
        processingEnv.getElementUtils().getPackageOf(classElement).getQualifiedName().toString();

    final DeclaredType isoType = (DeclaredType) method.getReturnType();
    final List<? extends TypeMirror> typeArguments = isoType.getTypeArguments();
    if (typeArguments.size() != 2) {
      processingEnv
          .getMessager()
          .printMessage(Diagnostic.Kind.ERROR, "Iso must have two type arguments", method);
      return;
    }

    final TypeName sTypeName = TypeName.get(typeArguments.get(0));
    final TypeName aTypeName = TypeName.get(typeArguments.get(1));
    final TypeName isoTypeName =
        ParameterizedTypeName.get(ClassName.get(Iso.class), sTypeName, aTypeName);

    final String generatedClassName = className + "Isos";

    final FieldSpec isoField =
        FieldSpec.builder(isoTypeName, methodName, PUBLIC, STATIC, FINAL)
            .initializer("$T.$L()", ClassName.get(classElement), methodName)
            .build();

    final MethodSpec constructor =
        MethodSpec.constructorBuilder()
            .addModifiers(javax.lang.model.element.Modifier.PRIVATE)
            .build();

    final ClassName generatedAnnotation =
        ClassName.get("org.higherkindedj.optics.annotations", "Generated");

    final TypeSpec isoContainer =
        TypeSpec.classBuilder(generatedClassName)
            // Add the @Generated annotation to the class
            .addAnnotation(generatedAnnotation)
            .addModifiers(PUBLIC, FINAL)
            .addField(isoField)
            .addMethod(constructor)
            .build();

    JavaFile.builder(packageName, isoContainer).build().writeTo(processingEnv.getFiler());
  }
}
