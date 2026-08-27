// Copyright (c) 2025 - 2026 Magnus Smith
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
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.annotations.GenerateIsos;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * An annotation processor that generates a container class with static Iso fields for each method
 * annotated with {@link GenerateIsos}.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateIsos")
public final class IsoProcessor extends AbstractProcessor {

  private static final String TAG = "@GenerateIsos";

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /** Creates a new IsoProcessor. */
  public IsoProcessor() {}

  @Override
  public boolean process(
      final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
    for (final TypeElement annotation : annotations) {
      for (final Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
        if (element.getKind() == ElementKind.METHOD) {
          writeIsoFile((ExecutableElement) element);
        }
      }
    }
    return true;
  }

  @ExcludeFromJacocoGeneratedReport
  private void writeIsoFile(final ExecutableElement method) {
    try {
      processMethod(method);
    } catch (final IOException e) {
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
    }
  }

  /**
   * Reports a method the generated field cannot be written for, and returns whether it did.
   *
   * <p>The iso is published as a {@code public static final} field initialised by calling the
   * method, so every question here is one about that field: can its type be written down, and can
   * its initialiser reach the method. Answering them at the declaration is the point — left
   * ungated, each of these emitted a field javac then refused, in a file the author never wrote.
   *
   * @param method the annotated method
   * @param targetPackage the package the generated class is written into
   * @return true when the method was rejected and an error reported
   */
  private boolean rejectsUnwritableMethod(
      final ExecutableElement method, final String targetPackage) {

    final String name = method.getSimpleName().toString();
    if (!(method.getReturnType() instanceof DeclaredType returned)
        || !isoElement(returned)
        || returned.getTypeArguments().size() != 2) {
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "'" + name + "' does not return an Iso with both type arguments.",
          "The generated field is typed from the two arguments of the method's returned Iso, and"
              + " there is nothing to read them off.",
          "Return 'Iso<S, A>' naming both, as 'Iso<Point, Tuple2<Integer, Integer>>'.");
      return true;
    }
    // What matters is whether a variable reaches the field's own type, not whether the method
    // declares one: '<T> Iso<Box, String>' names none of its T and writes down perfectly well,
    // while an instance method of 'Holder<X>' returning 'Iso<Box<X>, X>' declares nothing and
    // names X twice.
    if (returned.getTypeArguments().stream().anyMatch(IsoProcessor::namesTypeVariable)) {
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "the iso returned by '" + name + "' names a type variable.",
          "It is published as a static final field, which has nowhere to declare one; the field"
              + " would name a variable nothing brings into scope.",
          "Give the iso concrete type arguments at the declaration (e.g. 'Iso<Box<String>,"
              + " String>' rather than '<T> Iso<Box<T>, T>'), or drop @GenerateIsos and call '"
              + name
              + "()' directly.");
      return true;
    }
    if (!method.getModifiers().contains(STATIC)) {
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "'" + name + "' is not static.",
          "The generated field initialises itself by calling the method, and a static initialiser"
              + " has no instance to call it on.",
          "Make '" + name + "' static.");
      return true;
    }
    if (!method.getParameters().isEmpty()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "'" + name + "' takes parameters.",
          "The generated field initialises itself by calling the method with no arguments, and"
              + " there is nothing for it to pass.",
          "Take the arguments away, or drop @GenerateIsos and call '" + name + "(...)' directly.");
      return true;
    }
    final TypeElement unreachable =
        returned.getTypeArguments().stream()
            .map(
                argument ->
                    ProcessorUtils.firstUnreachableIn(
                        processingEnv.getElementUtils(), argument, targetPackage))
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);
    if (unreachable != null) {
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "the iso returned by '"
              + name
              + "' names '"
              + unreachable.getSimpleName()
              + "', which cannot be reached from '"
              + targetPackage
              + "'.",
          "The generated field writes its own type out in full, so every type named inside it has"
              + " to be visible where the field is declared.",
          "Make '"
              + unreachable.getSimpleName()
              + "' and the types enclosing it public, or generate into the package they are"
              + " already visible from.");
      return true;
    }
    if (!ProcessorUtils.reachableFrom(processingEnv.getElementUtils(), method, targetPackage)) {
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "'" + name + "' cannot be reached from '" + targetPackage + "'.",
          "The generated field calls the method from the class written into that package, and"
              + " neither the method nor a type enclosing it is visible there.",
          "Make '"
              + name
              + "' and the types enclosing it public, or generate into the package they are"
              + " already visible from.");
      return true;
    }
    return false;
  }

  /**
   * Whether a declared type is {@code org.higherkindedj.optics.Iso} itself.
   *
   * <p>Cast, not a pattern: a declared type's element is a {@link TypeElement}, an unresolvable one
   * included, so there is no other kind for a test to turn away.
   */
  private static boolean isoElement(final DeclaredType type) {
    return ((TypeElement) type.asElement())
        .getQualifiedName()
        .contentEquals(Iso.class.getCanonicalName());
  }

  /**
   * Whether a type names a type variable anywhere inside it.
   *
   * <p>Asked of the field's own type arguments, so it has to see through every layer one can be
   * buried in: {@code List<T>}, {@code T[]}, {@code List<? extends T>} and {@code Outer<T>.Inner}
   * all name one, and a field naming any of them declares nothing that brings it into scope.
   */
  private static boolean namesTypeVariable(final TypeMirror type) {
    return switch (type.getKind()) {
      case TYPEVAR -> true;
      case ARRAY -> namesTypeVariable(((ArrayType) type).getComponentType());
      case WILDCARD -> {
        final WildcardType wildcard = (WildcardType) type;
        yield (wildcard.getExtendsBound() != null && namesTypeVariable(wildcard.getExtendsBound()))
            || (wildcard.getSuperBound() != null && namesTypeVariable(wildcard.getSuperBound()));
      }
      case DECLARED -> {
        final DeclaredType declared = (DeclaredType) type;
        yield declared.getTypeArguments().stream().anyMatch(IsoProcessor::namesTypeVariable)
            || namesTypeVariable(declared.getEnclosingType());
      }
      default -> false;
    };
  }

  private void processMethod(final ExecutableElement method) throws IOException {
    final TypeElement classElement = (TypeElement) method.getEnclosingElement();
    final String methodName = method.getSimpleName().toString();
    final String className = classElement.getSimpleName().toString();
    final String defaultPackage =
        processingEnv.getElementUtils().getPackageOf(classElement).getQualifiedName().toString();

    // Check for custom target package in annotation
    final GenerateIsos annotation = method.getAnnotation(GenerateIsos.class);
    final String targetPackage = annotation.targetPackage();
    final String packageName = targetPackage.isEmpty() ? defaultPackage : targetPackage;

    if (rejectsUnwritableMethod(method, packageName)) {
      return;
    }
    // Checked by the gate above: a two-argument Iso, naming no type variable.
    final List<? extends TypeMirror> typeArguments =
        ((DeclaredType) method.getReturnType()).getTypeArguments();

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
        MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build();

    final ClassName generatedAnnotation =
        ClassName.get("org.higherkindedj.optics.annotations", "Generated");

    final TypeSpec isoContainer =
        TypeSpec.classBuilder(generatedClassName)
            .addOriginatingElement(method)
            // Add the @Generated annotation to the class
            .addAnnotation(generatedAnnotation)
            .addModifiers(PUBLIC, FINAL)
            .addField(isoField)
            .addMethod(constructor)
            .build();

    JavaFile.builder(packageName, isoContainer).build().writeTo(processingEnv.getFiler());
  }
}
