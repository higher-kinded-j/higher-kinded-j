// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.annotations.GenerateAssembly;
import org.higherkindedj.optics.processing.util.ExcludeFromJacocoGeneratedReport;

/**
 * Annotation processor that generates per-record validated-assembly companions (issue #586).
 *
 * <p>For an annotated {@code record User(String name, int age)} it generates a same-package {@code
 * UserAssembly}: a staged, order-enforcing builder over {@code Validated<NonEmptyList<FieldError>,
 * User>} with one named method per component (labels come from the component names) and a terminal
 * {@code assemble()} that invokes the canonical constructor. The merge is a nested curried {@code
 * Validated.ap} chain with {@code NonEmptyList.semigroup()} (the accumulator on the function side,
 * so errors emerge in component-declaration order), giving exact arity with no ceiling and no
 * mechanism beyond the #581 primitives.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateAssembly")
public class AssemblyProcessor extends AbstractProcessor {

  private static final ClassName VALIDATED =
      ClassName.get("org.higherkindedj.hkt.validated", "Validated");
  private static final ClassName FIELD_ERROR =
      ClassName.get("org.higherkindedj.hkt.validated", "FieldError");
  private static final ClassName NEL =
      ClassName.get("org.higherkindedj.hkt.nonemptylist", "NonEmptyList");
  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");
  private static final ClassName OBJECTS = ClassName.get("java.util", "Objects");
  private static final TypeName CHANNEL = ParameterizedTypeName.get(NEL, FIELD_ERROR);

  /** Creates a new AssemblyProcessor. */
  public AssemblyProcessor() {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(GenerateAssembly.class)) {
      if (element.getKind() != ElementKind.RECORD) {
        error(
            "@GenerateAssembly was applied to "
                + element
                + ", which is not a record. The assembly companion is built from record"
                + " components. Fix: annotate a record, or use the hand-written"
                + " Validated.fields() builder for other types.",
            element);
        continue;
      }
      TypeElement record = (TypeElement) element;
      if (!record.getTypeParameters().isEmpty()) {
        error(
            "@GenerateAssembly on "
                + record.getQualifiedName()
                + ": the record is generic, and generic records are not supported. Fix: remove"
                + " the type parameters, or use the hand-written Validated.fields() builder.",
            element);
        continue;
      }
      if (record.getRecordComponents().isEmpty()) {
        error(
            "@GenerateAssembly on "
                + record.getQualifiedName()
                + ": the record has no components, so there is nothing to assemble. Fix: add"
                + " components or remove the annotation.",
            element);
        continue;
      }
      if (isPrivateOrEnclosedPrivately(record)) {
        error(
            "@GenerateAssembly on "
                + record.getQualifiedName()
                + ": the record (or an enclosing type) is private, so the generated companion in"
                + " the same package cannot see it. Fix: make the record and its enclosing types"
                + " at least package-private.",
            element);
        continue;
      }
      writeCompanion(record);
    }
    return true;
  }

  @ExcludeFromJacocoGeneratedReport
  private void writeCompanion(TypeElement record) {
    try {
      generateCompanion(record).writeTo(processingEnv.getFiler());
    } catch (FilerException e) {
      error(
          "@GenerateAssembly on "
              + record.getQualifiedName()
              + ": the companion class "
              + companionSimpleName(record)
              + " already exists in the package (a hand-written class, or another record whose"
              + " joined nested name collides). Fix: rename the record or the clashing type.",
          record);
    } catch (IOException e) {
      error("Could not generate assembly companion: " + e.getMessage(), record);
    }
  }

  private static boolean isPrivateOrEnclosedPrivately(Element element) {
    // A type's enclosing chain always terminates at its PackageElement, so the walk needs no
    // null guard.
    for (Element e = element; e.getKind() != ElementKind.PACKAGE; e = e.getEnclosingElement()) {
      if (e.getModifiers().contains(Modifier.PRIVATE)) {
        return true;
      }
    }
    return false;
  }

  private static String companionSimpleName(TypeElement record) {
    return String.join("", ClassName.get(record).simpleNames()) + "Assembly";
  }

  private JavaFile generateCompanion(TypeElement record) {
    String packageName =
        processingEnv.getElementUtils().getPackageOf(record).getQualifiedName().toString();
    ClassName recordName = ClassName.get(record);
    String companionSimpleName = companionSimpleName(record);
    ClassName companion = ClassName.get(packageName, companionSimpleName);

    List<? extends RecordComponentElement> components = record.getRecordComponents();
    int arity = components.size();

    TypeSpec.Builder outer =
        TypeSpec.classBuilder(companionSimpleName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(GENERATED)
            .addOriginatingElement(record)
            .addJavadoc(
                "Validated-assembly companion for {@link $T}: one named method per component, in\n"
                    + "declaration order; labels come from the component names; every error is\n"
                    + "collected, located, and emerges in declaration order.\n",
                recordName)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addMethod(
                MethodSpec.methodBuilder("fields")
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(companion.nestedClass("Stage0"))
                    .addJavadoc("Opens the assembly at the first component.\n")
                    .addStatement("return $T.INSTANCE", companion.nestedClass("Stage0"))
                    .build());

    for (int stage = 0; stage <= arity; stage++) {
      outer.addType(stageType(companion, recordName, components, stage));
    }
    return JavaFile.builder(packageName, outer.build()).build();
  }

  /** Stage {@code i} holds the first {@code i} components; stage {@code arity} is terminal. */
  private TypeSpec stageType(
      ClassName companion,
      ClassName recordName,
      List<? extends RecordComponentElement> components,
      int stage) {
    int arity = components.size();
    ClassName self = companion.nestedClass("Stage" + stage);
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(self.simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
    if (stage == arity) {
      builder.addJavadoc("Terminal stage: every component supplied; {@code assemble()} only.\n");
    } else {
      builder.addJavadoc(
          "Stage $L of $L: expects {@code $L} next.\n", stage, arity, name(components, stage));
    }

    MethodSpec.Builder ctor = MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE);
    for (int i = 0; i < stage; i++) {
      TypeName held = validatedOf(components.get(i));
      builder.addField(
          FieldSpec.builder(held, name(components, i), Modifier.PRIVATE, Modifier.FINAL).build());
      ctor.addParameter(held, name(components, i));
      ctor.addStatement("this.$1N = $1N", name(components, i));
    }
    if (stage == 0) {
      builder.addField(
          FieldSpec.builder(self, "INSTANCE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
              .initializer("new $T()", self)
              .build());
    }
    builder.addMethod(ctor.build());

    if (stage < arity) {
      builder.addMethod(componentMethod(companion, components, stage));
    } else {
      builder.addMethod(assembleMethod(recordName, components));
    }
    return builder.build();
  }

  /** The named, order-enforcing method for component {@code stage}. */
  private MethodSpec componentMethod(
      ClassName companion, List<? extends RecordComponentElement> components, int stage) {
    String label = name(components, stage);
    ClassName next = companion.nestedClass("Stage" + (stage + 1));
    CodeBlock.Builder args = CodeBlock.builder();
    for (int i = 0; i < stage; i++) {
      args.add("this.$N, ", name(components, i));
    }
    args.add("value.mapError(errors -> errors.map(err -> err.at($S)))", label);
    return MethodSpec.methodBuilder(label)
        .addModifiers(Modifier.PUBLIC)
        .returns(next)
        .addParameter(validatedOf(components.get(stage)), "value")
        .addJavadoc("Supplies {@code $L}; its errors are labelled {@code \"$L\"}.\n", label, label)
        .addStatement("$T.requireNonNull(value, $S)", OBJECTS, "value must not be null")
        .addStatement("return new $T($L)", next, args.build())
        .build();
  }

  /** The terminal merge: a nested curried {@code ap} chain, accumulator on the function side. */
  private MethodSpec assembleMethod(
      ClassName recordName, List<? extends RecordComponentElement> components) {
    int arity = components.size();
    CodeBlock.Builder curried = CodeBlock.builder();
    for (int i = 1; i <= arity; i++) {
      curried.add("a$L -> ", i);
    }
    curried.add("new $T(", recordName);
    for (int i = 1; i <= arity; i++) {
      curried.add(i == 1 ? "a$L" : ", a$L", i);
    }
    curried.add(")");

    CodeBlock expr = CodeBlock.of("this.$N.map($L)", name(components, 0), curried.build());
    for (int i = 1; i < arity; i++) {
      expr = CodeBlock.of("this.$N.ap($L, $T.semigroup())", name(components, i), expr, NEL);
    }
    return MethodSpec.methodBuilder("assemble")
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(VALIDATED, CHANNEL, recordName))
        .addJavadoc(
            "Assembles the record via its canonical constructor; every error is collected, in\n"
                + "component-declaration order.\n")
        .addStatement("return $L", expr)
        .build();
  }

  private TypeName validatedOf(RecordComponentElement component) {
    return ParameterizedTypeName.get(VALIDATED, CHANNEL, TypeName.get(component.asType()).box());
  }

  private static String name(List<? extends RecordComponentElement> components, int i) {
    return components.get(i).getSimpleName().toString();
  }

  private void error(String msg, Element e) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
  }
}
