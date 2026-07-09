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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Annotation processor for {@code @GenerateErrorEnvelope} (issue #610): generates the {@code
 * <Name>s} companion for a sealed domain-error hierarchy whose permitted record variants each carry
 * exactly one {@code ErrorEnvelope<C>} component.
 *
 * <p>The context type {@code C} is discovered structurally from that component's type argument -
 * never from a class-literal attribute - and every variant must agree on it. For each variant the
 * companion offers two factories ({@code TimeSource.system()} convenience and an explicit {@code
 * TimeSource} overload) that fill the envelope with the UPPER_SNAKE variant name as code, the
 * humanised variant name as message, the source-read timestamp, and the all-absent context. A
 * fluent {@code ContextBuilder} over {@code C}'s components and an {@code editContext} wither
 * (rebuilding the concrete variant via an exhaustive switch over the permitted variants) complete
 * the companion.
 *
 * <p>Constraints, each a what/why/fix diagnostic: the hierarchy, its variants and the context must
 * all be non-generic (the generated code names them directly); nested sealed sub-hierarchies are
 * not recursed into (flatten to direct record variants); context components must be reference types
 * (the all-absent instance holds nulls).
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateErrorEnvelope")
public class ErrorEnvelopeProcessor extends AbstractProcessor {

  private static final String TAG = "@GenerateErrorEnvelope";
  private static final String ENVELOPE_FQN = "org.higherkindedj.hkt.error.ErrorEnvelope";
  private static final ClassName ENVELOPE =
      ClassName.get("org.higherkindedj.hkt.error", "ErrorEnvelope");
  private static final ClassName TIME_SOURCE =
      ClassName.get("org.higherkindedj.hkt.time", "TimeSource");
  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");
  private static final ClassName OBJECTS = ClassName.get("java.util", "Objects");
  private static final ClassName UNARY_OPERATOR =
      ClassName.get("java.util.function", "UnaryOperator");

  /** Creates a new ErrorEnvelopeProcessor. */
  public ErrorEnvelopeProcessor() {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(GenerateErrorEnvelope.class)) {
      processHierarchy(element);
    }
    return true;
  }

  /** One permitted record variant: its element, and where its envelope component sits. */
  private record Variant(TypeElement record, int envelopeIndex, DeclaredType contextType) {}

  private void processHierarchy(Element element) {
    if (element.getKind() != ElementKind.INTERFACE) {
      Diagnostics.error(
          processingEnv.getMessager(),
          element,
          TAG,
          "can only be applied to sealed interfaces, but '"
              + element.getSimpleName()
              + "' is "
              + indefinite(element.getKind().toString().toLowerCase(Locale.ROOT))
              + ".",
          "The companion is generated from the closed set of permitted record variants, which only"
              + " a sealed interface declares.",
          "Declare 'sealed interface "
              + element.getSimpleName()
              + "' whose permitted variants are records.");
      return;
    }
    TypeElement iface = (TypeElement) element;
    if (!iface.getModifiers().contains(Modifier.SEALED)) {
      Diagnostics.error(
          processingEnv.getMessager(),
          iface,
          TAG,
          "'" + iface.getSimpleName() + "' is an interface but is not sealed.",
          "The companion's factories and exhaustive editContext switch need the closed set of"
              + " permitted variants; an open interface has none.",
          "Add the 'sealed' modifier and make every permitted variant a record.");
      return;
    }
    if (!iface.getTypeParameters().isEmpty()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          iface,
          TAG,
          "'" + iface.getSimpleName() + "' is generic, which this companion does not support.",
          "The generated factories and the editContext switch name the hierarchy and its variants"
              + " directly; type parameters would leave them referencing undeclared type"
              + " variables.",
          "Declare the hierarchy without type parameters.");
      return;
    }

    List<Variant> variants = new ArrayList<>();
    for (TypeMirror permitted : iface.getPermittedSubclasses()) {
      TypeElement variant = (TypeElement) ((DeclaredType) permitted).asElement();
      if (variant.getKind() != ElementKind.RECORD) {
        rejectNonRecordVariant(iface, variant);
        return;
      }
      if (!variant.getTypeParameters().isEmpty()) {
        Diagnostics.error(
            processingEnv.getMessager(),
            variant,
            TAG,
            "permitted variant '"
                + variant.getSimpleName()
                + "' of '"
                + iface.getSimpleName()
                + "' is generic, which this companion does not support.",
            "The factories and the editContext switch name each variant directly; type parameters"
                + " would leave the generated code referencing undeclared type variables.",
            "Declare '" + variant.getSimpleName() + "' with concrete component types.");
        return;
      }
      Variant model = analyseVariant(iface, variant);
      if (model == null) {
        return;
      }
      variants.add(model);
    }

    if (variants.isEmpty()) {
      // A sealed interface with no permitted subtypes is already a javac error; nothing to add.
      return;
    }

    DeclaredType contextType = variants.getFirst().contextType();
    for (Variant variant : variants) {
      if (!processingEnv.getTypeUtils().isSameType(contextType, variant.contextType())) {
        Diagnostics.error(
            processingEnv.getMessager(),
            variant.record(),
            TAG,
            "variants of '"
                + iface.getSimpleName()
                + "' disagree on the context type: '"
                + variants.getFirst().record().getSimpleName()
                + "' carries ErrorEnvelope<"
                + contextType
                + "> but '"
                + variant.record().getSimpleName()
                + "' carries ErrorEnvelope<"
                + variant.contextType()
                + ">.",
            "One companion serves the whole hierarchy; a single context schema is what makes the"
                + " generated builder and editContext type-safe.",
            "Use the same context record in every variant's ErrorEnvelope component.");
        return;
      }
    }

    TypeElement context = asRecord(contextType);
    if (context == null) {
      Diagnostics.error(
          processingEnv.getMessager(),
          iface,
          TAG,
          "context type '" + contextType + "' is not a record.",
          "The context is records-as-schema: the generated ContextBuilder is derived from the"
              + " context record's components.",
          "Declare the context as a record with nullable components.");
      return;
    }
    if (!context.getTypeParameters().isEmpty()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          iface,
          TAG,
          "context type '" + contextType + "' is generic, which this companion does not support.",
          "The ContextBuilder and the all-absent instance are derived from the context record's"
              + " components; type variables would leave the generated fields undeclared.",
          "Use a concrete (non-generic) context record.");
      return;
    }
    for (RecordComponentElement component : context.getRecordComponents()) {
      if (component.asType().getKind().isPrimitive()) {
        Diagnostics.error(
            processingEnv.getMessager(),
            iface,
            TAG,
            "context component '"
                + component.getSimpleName()
                + "' of '"
                + context.getSimpleName()
                + "' is a primitive "
                + component.asType()
                + ".",
            "The all-absent context instance holds null for every component not yet set, which a"
                + " primitive component cannot represent.",
            "Declare the component as '"
                + processingEnv
                    .getTypeUtils()
                    .boxedClass((PrimitiveType) component.asType())
                    .getSimpleName()
                + "' or another reference type.");
        return;
      }
    }

    writeCompanion(iface, context, variants);
  }

  /** Non-record variants; a nested sealed sub-hierarchy gets its own flatten-it diagnostic. */
  private void rejectNonRecordVariant(TypeElement iface, TypeElement variant) {
    String kind = variant.getKind().toString().toLowerCase(Locale.ROOT);
    if (variant.getModifiers().contains(Modifier.SEALED)) {
      Diagnostics.error(
          processingEnv.getMessager(),
          variant,
          TAG,
          "permitted variant '"
              + variant.getSimpleName()
              + "' of '"
              + iface.getSimpleName()
              + "' is a nested sealed "
              + kind
              + ", not a record.",
          "The companion's factories and editContext arms are derived from direct record variants;"
              + " the processor does not recurse into sub-hierarchies.",
          "Flatten the sub-hierarchy: make the leaf records of '"
              + variant.getSimpleName()
              + "' direct permitted variants of '"
              + iface.getSimpleName()
              + "'.");
      return;
    }
    Diagnostics.error(
        processingEnv.getMessager(),
        variant,
        TAG,
        "permitted variant '"
            + variant.getSimpleName()
            + "' of '"
            + iface.getSimpleName()
            + "' is "
            + indefinite(kind)
            + ", not a record.",
        "The companion derives each variant's factory and editContext arm from its record"
            + " components.",
        "Make '"
            + variant.getSimpleName()
            + "' a record carrying its domain components plus one ErrorEnvelope<...> component.");
  }

  /** {@code "class"} to {@code "a class"}; {@code "interface"} to {@code "an interface"}. */
  private static String indefinite(String noun) {
    return ("aeiou".indexOf(noun.charAt(0)) >= 0 ? "an " : "a ") + noun;
  }

  /** Locates the single {@code ErrorEnvelope<C>} component and reads {@code C} structurally. */
  private Variant analyseVariant(TypeElement iface, TypeElement variant) {
    List<? extends RecordComponentElement> components = variant.getRecordComponents();
    List<Integer> envelopeIndices = new ArrayList<>();
    for (int i = 0; i < components.size(); i++) {
      if (isEnvelope(components.get(i).asType())) {
        envelopeIndices.add(i);
      }
    }
    if (envelopeIndices.size() != 1) {
      Diagnostics.error(
          processingEnv.getMessager(),
          variant,
          TAG,
          "variant '"
              + variant.getSimpleName()
              + "' of '"
              + iface.getSimpleName()
              + "' declares "
              + (envelopeIndices.isEmpty() ? "no" : String.valueOf(envelopeIndices.size()))
              + " ErrorEnvelope component"
              + (envelopeIndices.size() > 1 ? "s" : "")
              + "; exactly one is required.",
          "The envelope carries the variant's code, message, timestamp and typed context; the"
              + " context type is discovered structurally from that component's type argument.",
          "Declare exactly one 'ErrorEnvelope<YourContext> envelope' component on the variant"
              + " record.");
      return null;
    }
    int index = envelopeIndices.getFirst();
    DeclaredType envelopeType = (DeclaredType) components.get(index).asType();
    List<? extends TypeMirror> typeArguments = envelopeType.getTypeArguments();
    if (typeArguments.size() != 1 || !(typeArguments.getFirst() instanceof DeclaredType context)) {
      Diagnostics.error(
          processingEnv.getMessager(),
          variant,
          TAG,
          "the ErrorEnvelope component on '"
              + variant.getSimpleName()
              + "' does not declare a usable context type argument.",
          "The context type is discovered structurally from the component's type argument, never"
              + " from a class-literal attribute.",
          "Declare the component as 'ErrorEnvelope<YourContext>' with a concrete context record.");
      return null;
    }
    return new Variant(variant, index, context);
  }

  private boolean isEnvelope(TypeMirror mirror) {
    return mirror instanceof DeclaredType declared
        && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals(ENVELOPE_FQN);
  }

  private void writeCompanion(TypeElement iface, TypeElement context, List<Variant> variants) {
    ClassName ifaceName = ClassName.get(iface);
    String packageName =
        processingEnv.getElementUtils().getPackageOf(iface).getQualifiedName().toString();
    // Nested hierarchies join their enclosing simple names, so the companion is always top-level.
    ClassName companion =
        ClassName.get(packageName, String.join("", ifaceName.simpleNames()) + "s");
    ClassName contextName = ClassName.get(context);
    ClassName builderName = companion.nestedClass("ContextBuilder");
    TypeName envelopeOfContext = ParameterizedTypeName.get(ENVELOPE, contextName);

    TypeSpec.Builder outer =
        TypeSpec.classBuilder(companion.simpleName())
            .addOriginatingElement(iface)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(GENERATED)
            .addJavadoc(
                "Generated error-envelope companion for {@link $T}: per-variant factories that\n"
                    + "fill the envelope (code, message, timestamp, all-absent context), a fluent\n"
                    + "{@link ContextBuilder} over {@link $T}, and a context wither that rebuilds\n"
                    + "the concrete variant.\n",
                ifaceName,
                contextName)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addField(absentContextField(context, contextName));

    for (Variant variant : variants) {
      outer.addMethod(convenienceFactory(variant));
      outer.addMethod(timedFactory(variant, contextName));
    }
    outer.addMethod(
        MethodSpec.methodBuilder("context")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(builderName)
            .addJavadoc("Opens a context builder with every component absent.\n")
            .addStatement("return new $T(ABSENT_CONTEXT)", builderName)
            .build());
    outer.addMethod(witherMethod(ifaceName, builderName, variants));
    outer.addMethod(rebuildMethod(envelopeOfContext, builderName));
    outer.addType(contextBuilderType(context, contextName, builderName));

    writeFile(iface, packageName, outer.build());
  }

  /** The all-absent context instance: every component null, via the canonical constructor. */
  private FieldSpec absentContextField(TypeElement context, ClassName contextName) {
    CodeBlock.Builder args = CodeBlock.builder();
    boolean first = true;
    for (RecordComponentElement component : context.getRecordComponents()) {
      args.add(first ? "($T) null" : ", ($T) null", TypeName.get(component.asType()));
      first = false;
    }
    return FieldSpec.builder(
            contextName, "ABSENT_CONTEXT", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
        .addJavadoc(
            "The all-absent context: every component null. The context record's components must"
                + " therefore be nullable; a null-rejecting compact constructor would fail here at"
                + " class initialisation.\n")
        .initializer("new $T($L)", contextName, args.build())
        .build();
  }

  /** The {@code TimeSource.system()} convenience factory, delegating to the timed overload. */
  private MethodSpec convenienceFactory(Variant variant) {
    ClassName variantName = ClassName.get(variant.record());
    MethodSpec.Builder method =
        MethodSpec.methodBuilder(factoryName(variant))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(variantName)
            .addJavadoc(
                "Creates {@link $T} with a live-clock envelope; see the {@code TimeSource}\n"
                    + "overload for deterministic timestamps.\n",
                variantName);
    CodeBlock.Builder args = CodeBlock.builder().add("$T.system()", TIME_SOURCE);
    for (RecordComponentElement component : domainComponents(variant)) {
      String name = component.getSimpleName().toString();
      method.addParameter(TypeName.get(component.asType()), name);
      args.add(", $N", name);
    }
    return method.addStatement("return $L($L)", factoryName(variant), args.build()).build();
  }

  /** The explicit-{@code TimeSource} factory that actually fills the envelope. */
  private MethodSpec timedFactory(Variant variant, ClassName contextName) {
    ClassName variantName = ClassName.get(variant.record());
    String simpleName = variant.record().getSimpleName().toString();
    String code = upperSnake(simpleName);
    String message = humanise(simpleName);
    MethodSpec.Builder method =
        MethodSpec.methodBuilder(factoryName(variant))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(variantName)
            .addJavadoc(
                "Creates {@link $T}: code {@code $L}, message {@code $L}, timestamp read from\n"
                    + "{@code time}, context all-absent.\n",
                variantName,
                code,
                message)
            .addParameter(TIME_SOURCE, "time");
    for (RecordComponentElement component : domainComponents(variant)) {
      method.addParameter(TypeName.get(component.asType()), component.getSimpleName().toString());
    }
    method.addStatement("$T.requireNonNull(time, $S)", OBJECTS, "time must not be null");

    CodeBlock envelope =
        CodeBlock.of("$T.of(time, $S, $S, ABSENT_CONTEXT)", ENVELOPE, code, message);
    CodeBlock.Builder args = CodeBlock.builder();
    List<? extends RecordComponentElement> components = variant.record().getRecordComponents();
    for (int i = 0; i < components.size(); i++) {
      CodeBlock argument =
          i == variant.envelopeIndex()
              ? envelope
              : CodeBlock.of("$N", components.get(i).getSimpleName().toString());
      args.add(i == 0 ? "$L" : ", $L", argument);
    }
    return method.addStatement("return new $T($L)", variantName, args.build()).build();
  }

  /** The context wither: an exhaustive switch over the permitted variants. */
  private MethodSpec witherMethod(
      ClassName ifaceName, ClassName builderName, List<Variant> variants) {
    MethodSpec.Builder method =
        MethodSpec.methodBuilder("editContext")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(ifaceName)
            .addParameter(ifaceName, "error")
            .addParameter(ParameterizedTypeName.get(UNARY_OPERATOR, builderName), "edit")
            .addJavadoc(
                "Rebuilds {@code error} with its context transformed by {@code edit}, seeded from\n"
                    + "the current context; code, message and timestamp are preserved.\n")
            .addStatement("$T.requireNonNull(error, $S)", OBJECTS, "error must not be null")
            .addStatement("$T.requireNonNull(edit, $S)", OBJECTS, "edit must not be null");
    CodeBlock.Builder sw = CodeBlock.builder().add("return switch (error) {\n").indent();
    for (Variant variant : variants) {
      ClassName variantName = ClassName.get(variant.record());
      CodeBlock.Builder args = CodeBlock.builder();
      List<? extends RecordComponentElement> components = variant.record().getRecordComponents();
      for (int i = 0; i < components.size(); i++) {
        String accessor = components.get(i).getSimpleName().toString();
        CodeBlock argument =
            i == variant.envelopeIndex()
                ? CodeBlock.of("rebuild(v.$L(), edit)", accessor)
                : CodeBlock.of("v.$L()", accessor);
        args.add(i == 0 ? "$L" : ", $L", argument);
      }
      sw.add("case $T v -> new $T($L);\n", variantName, variantName, args.build());
    }
    sw.unindent().add("};\n");
    return method.addCode(sw.build()).build();
  }

  /** Applies the edit to a builder seeded from the envelope's current context. */
  private MethodSpec rebuildMethod(TypeName envelopeOfContext, ClassName builderName) {
    return MethodSpec.methodBuilder("rebuild")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .returns(envelopeOfContext)
        .addParameter(envelopeOfContext, "envelope")
        .addParameter(ParameterizedTypeName.get(UNARY_OPERATOR, builderName), "edit")
        .addStatement(
            "return envelope.withContext(edit.apply(new $T(envelope.context())).build())",
            builderName)
        .build();
  }

  /** The fluent builder over the context record's components. */
  private TypeSpec contextBuilderType(
      TypeElement context, ClassName contextName, ClassName builderName) {
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(builderName.simpleName())
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addJavadoc(
                "Fluent builder over {@link $T}'s components; components not set stay as seeded.\n",
                contextName);
    MethodSpec.Builder ctor =
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(contextName, "seed");
    CodeBlock.Builder buildArgs = CodeBlock.builder();
    boolean first = true;
    for (RecordComponentElement component : context.getRecordComponents()) {
      String name = component.getSimpleName().toString();
      TypeName type = TypeName.get(component.asType());
      builder.addField(FieldSpec.builder(type, name, Modifier.PRIVATE).build());
      ctor.addStatement("this.$1N = seed.$1N()", name);
      builder.addMethod(
          MethodSpec.methodBuilder(name)
              .addModifiers(Modifier.PUBLIC)
              .returns(builderName)
              .addParameter(type, name)
              .addJavadoc("Sets {@code $L}.\n", name)
              .addStatement("this.$1N = $1N", name)
              .addStatement("return this")
              .build());
      buildArgs.add(first ? "$N" : ", $N", name);
      first = false;
    }
    builder.addMethod(ctor.build());
    builder.addMethod(
        MethodSpec.methodBuilder("build")
            .addModifiers(Modifier.PUBLIC)
            .returns(contextName)
            .addJavadoc("Builds the context via its canonical constructor.\n")
            .addStatement("return new $T($L)", contextName, buildArgs.build())
            .build());
    return builder.build();
  }

  private static String factoryName(Variant variant) {
    return ProcessorUtils.toCamelCase(variant.record().getSimpleName().toString());
  }

  private List<RecordComponentElement> domainComponents(Variant variant) {
    List<RecordComponentElement> domain = new ArrayList<>();
    List<? extends RecordComponentElement> components = variant.record().getRecordComponents();
    for (int i = 0; i < components.size(); i++) {
      if (i != variant.envelopeIndex()) {
        domain.add(components.get(i));
      }
    }
    return domain;
  }

  /** Splits a PascalCase name into its words, keeping acronym runs together. */
  private static List<String> camelWords(String name) {
    return List.of(name.split("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"));
  }

  /** {@code PaymentDeclined} to {@code PAYMENT_DECLINED}. */
  private static String upperSnake(String name) {
    return String.join("_", camelWords(name)).toUpperCase(Locale.ROOT);
  }

  /** {@code PaymentDeclined} to {@code Payment declined}; acronym words keep their case. */
  private static String humanise(String name) {
    List<String> words = camelWords(name);
    StringBuilder message = new StringBuilder();
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      boolean acronym = word.length() > 1 && ProcessorUtils.isAllUpperCase(word);
      String humanised = acronym ? word : word.toLowerCase(Locale.ROOT);
      if (i == 0) {
        humanised = acronym ? humanised : ProcessorUtils.capitalise(humanised);
      } else {
        message.append(' ');
      }
      message.append(humanised);
    }
    return message.toString();
  }

  void writeFile(TypeElement iface, String packageName, TypeSpec companion) {
    try {
      JavaFile.builder(packageName, companion)
          .addFileComment("Generated by hkj-processor. Do not edit.")
          .build()
          .writeTo(processingEnv.getFiler());
    } catch (FilerException e) {
      Diagnostics.error(
          processingEnv.getMessager(),
          iface,
          TAG,
          "could not write the generated companion for '"
              + iface.getSimpleName()
              + "': the class already exists.",
          "A hand-written type or another hierarchy's companion already claims the name (nested"
              + " hierarchies join their enclosing simple names, so two hierarchies can collide)."
              + " The filer reported: "
              + e.getMessage()
              + ".",
          "Rename the hierarchy or the clashing type.");
    } catch (IOException e) {
      Diagnostics.error(
          processingEnv.getMessager(),
          iface,
          TAG,
          "could not write the generated companion for '" + iface.getSimpleName() + "'.",
          "The filer reported: " + e.getMessage() + ".",
          "Check build-output permissions and free disk space, then rebuild.");
    }
  }

  /** The type's record element, or null; a declared type's element is always a TypeElement. */
  private TypeElement asRecord(DeclaredType declared) {
    Element element = declared.asElement();
    return element.getKind() == ElementKind.RECORD ? (TypeElement) element : null;
  }
}
