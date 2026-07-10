// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.ArrayList;
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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.higherkindedj.optics.annotations.ArityCeilings;
import org.higherkindedj.optics.annotations.GenerateMerge;
import org.higherkindedj.optics.processing.util.Diagnostics;

/**
 * Annotation processor for {@code @GenerateMerge} (issue #613): forward-only assembly of one target
 * record from several source records, declared by the spec interface's single abstract method — the
 * signature carries target and sources, never class-literal attributes.
 *
 * <p>Each target component is filled from the one source with a same-named component: identity when
 * the types match, through a zero-parameter {@code default} leaf method returning {@code
 * ValidatedPrism<SourceComponent, TargetComponent>} when they differ. Ambiguous and unfilled
 * components are compile errors. Truthful types: with any fallible leaf the declared return type
 * must be {@code Validated<NonEmptyList<FieldError>, Target>}, and without one it must be the plain
 * target.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateMerge")
public class MergeProcessor extends AbstractProcessor {

  private static final String TAG = "@GenerateMerge";
  private static final String VALIDATED_PRISM = "org.higherkindedj.optics.validated.ValidatedPrism";
  private static final String VALIDATED_FQN = "org.higherkindedj.hkt.validated.Validated";
  private static final String NEL_FQN = "org.higherkindedj.hkt.nonemptylist.NonEmptyList";
  private static final String FIELD_ERROR_FQN = "org.higherkindedj.hkt.validated.FieldError";
  private static final ClassName VALIDATED =
      ClassName.get("org.higherkindedj.hkt.validated", "Validated");
  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");
  private static final ClassName OBJECTS = ClassName.get("java.util", "Objects");

  /** Creates a new MergeProcessor. */
  public MergeProcessor() {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    // Nested fills resolve against the same round's @GenerateMapping specs (shared scan).
    List<MappingProcessor.RegisteredSpec> registry = MappingProcessor.scanRegistry(roundEnv);
    for (Element element : roundEnv.getElementsAnnotatedWith(GenerateMerge.class)) {
      processSpec(element, registry);
    }
    return true;
  }

  private void processSpec(Element element, List<MappingProcessor.RegisteredSpec> registry) {
    if (element.getKind() != ElementKind.INTERFACE) {
      Diagnostics.error(
          processingEnv.getMessager(),
          element,
          TAG,
          "can only be applied to interfaces, but '"
              + element.getSimpleName()
              + "' is a "
              + element.getKind().toString().toLowerCase(java.util.Locale.ROOT)
              + ".",
          "The merge is declared by an interface whose single abstract method names the target"
              + " and sources.",
          "Declare 'interface "
              + element.getSimpleName()
              + " { Target assemble(SourceA a, SourceB b); }'.");
      return;
    }
    TypeElement spec = (TypeElement) element;
    if (!spec.getTypeParameters().isEmpty()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'" + spec.getSimpleName() + "' is generic, which this merge does not support.",
          "The generated Impl names the merged types directly; type parameters would leave it"
              + " referencing undeclared type variables.",
          "Merge concrete record types.");
      return;
    }

    if (!spec.getInterfaces().isEmpty()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'" + spec.getSimpleName() + "' extends other interfaces.",
          "The merge method and leaves declared on supertypes are invisible to this slice's"
              + " classification, so the generated Impl could silently miss inherited members.",
          "Declare the merge method and every leaf directly on the spec.");
      return;
    }

    ExecutableElement mergeMethod = findMergeMethod(spec);
    if (mergeMethod == null) {
      return;
    }
    if (!mergeMethod.getTypeParameters().isEmpty()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          mergeMethod,
          TAG,
          "merge method '" + mergeMethod.getSimpleName() + "' declares type parameters.",
          "The generated implementation must override the method exactly; type parameters would"
              + " leave the override generic.",
          "Remove the type parameters from the merge method.");
      return;
    }

    List<? extends VariableElement> sources = mergeMethod.getParameters();
    if (sources.size() < 2) {
      Diagnostics.error(
          processingEnv.getMessager(),
          mergeMethod,
          TAG,
          "merge method '" + mergeMethod.getSimpleName() + "' declares fewer than two sources.",
          "A merge assembles one target from several sources; the one-source case is a mapping.",
          "Add further source parameters, or use @GenerateMapping for a single source.");
      return;
    }
    for (VariableElement source : sources) {
      TypeElement sourceRecord = asRecord(source.asType());
      if (sourceRecord == null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            mergeMethod,
            TAG,
            "source parameter '" + source.getSimpleName() + "' is not a record.",
            "The merge derives fills from record components on every source.",
            "Use record types for all sources.");
        return;
      }
      if (!sourceRecord.getTypeParameters().isEmpty()) {
        Diagnostics.error(
            processingEnv.getMessager(),
            mergeMethod,
            TAG,
            "'" + sourceRecord.getSimpleName() + "' is generic, which this merge does not support.",
            "The generated Impl names the merged types directly; type parameters would leave it"
                + " referencing undeclared type variables.",
            "Merge concrete record types.");
        return;
      }
    }

    ReturnShape shape = analyseReturn(spec, mergeMethod);
    if (shape == null) {
      return;
    }

    List<Fill> fills = classify(spec, registry, mergeMethod, shape.target());
    if (fills == null) {
      return;
    }

    boolean fallible = fills.stream().anyMatch(Fill::fallible);
    if (fallible && !shape.fallibleDeclared()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          mergeMethod,
          TAG,
          "'"
              + mergeMethod.getSimpleName()
              + "' uses fallible fills but declares a plain '"
              + shape.target().getSimpleName()
              + "' return.",
          "Truthful types: a merge that can fail must say so in its signature.",
          "Declare 'Validated<NonEmptyList<FieldError>, "
              + shape.target().getSimpleName()
              + "> "
              + mergeMethod.getSimpleName()
              + "(...)'.");
      return;
    }
    if (!fallible && shape.fallibleDeclared()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          mergeMethod,
          TAG,
          "'"
              + mergeMethod.getSimpleName()
              + "' declares a Validated return but every fill is an identity copy.",
          "Truthful types: a merge that cannot fail must not claim it can.",
          "Declare the plain '" + shape.target().getSimpleName() + "' return type.");
      return;
    }
    if (fallible && shape.target().getRecordComponents().size() > ArityCeilings.ASSEMBLY) {
      Diagnostics.error(
          processingEnv.getMessager(),
          mergeMethod,
          TAG,
          "'"
              + shape.target().getSimpleName()
              + "' has "
              + shape.target().getRecordComponents().size()
              + " components; the accumulating merge supports at most "
              + ArityCeilings.ASSEMBLY
              + ".",
          "The fallible path is assembled with Validated.fields(), which locates up to "
              + ArityCeilings.ASSEMBLY
              + " fields.",
          "Group related components into nested records, or map the record by hand.");
      return;
    }

    writeImpl(spec, mergeMethod, shape, fills);
  }

  /** The single abstract method that declares the merge; leaves stay {@code default}. */
  private ExecutableElement findMergeMethod(TypeElement spec) {
    List<ExecutableElement> abstractMethods = new ArrayList<>();
    for (ExecutableElement method : ElementFilter.methodsIn(spec.getEnclosedElements())) {
      if (method.getModifiers().contains(Modifier.ABSTRACT)) {
        abstractMethods.add(method);
      }
    }
    if (abstractMethods.size() != 1) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'"
              + spec.getSimpleName()
              + "' declares "
              + abstractMethods.size()
              + " abstract methods; a merge spec declares exactly one.",
          "The single abstract method carries the whole declaration (target return type, source"
              + " parameters); leaves are zero-parameter 'default' methods.",
          "Keep one abstract merge method and make everything else 'default'.");
      return null;
    }
    return abstractMethods.getFirst();
  }

  /** The declared return: either the plain target record, or Validated<NEL<FieldError>, Target>. */
  private record ReturnShape(TypeElement target, boolean fallibleDeclared) {}

  private ReturnShape analyseReturn(TypeElement spec, ExecutableElement mergeMethod) {
    TypeMirror declared = mergeMethod.getReturnType();
    if (declared instanceof DeclaredType declaredType
        && ((TypeElement) declaredType.asElement())
            .getQualifiedName()
            .contentEquals(VALIDATED_FQN)) {
      List<? extends TypeMirror> args = declaredType.getTypeArguments();
      boolean errorChannelOk =
          args.size() == 2
              && args.get(0) instanceof DeclaredType errorArg
              && ((TypeElement) errorArg.asElement()).getQualifiedName().contentEquals(NEL_FQN)
              && errorArg.getTypeArguments().size() == 1
              && errorArg.getTypeArguments().get(0) instanceof DeclaredType fieldError
              && ((TypeElement) fieldError.asElement())
                  .getQualifiedName()
                  .contentEquals(FIELD_ERROR_FQN);
      if (!errorChannelOk) {
        Diagnostics.error(
            processingEnv.getMessager(),
            mergeMethod,
            TAG,
            "'"
                + mergeMethod.getSimpleName()
                + "' declares a Validated return with the wrong"
                + " error channel.",
            "Merge failures accumulate as NonEmptyList<FieldError>, located by target component"
                + " name.",
            "Declare 'Validated<NonEmptyList<FieldError>, Target>'.");
        return null;
      }
      TypeElement target = asRecord(args.get(1));
      if (target == null) {
        return targetNotARecord(mergeMethod, args.get(1));
      }
      return checkTarget(mergeMethod, target, true);
    }
    TypeElement target = asRecord(declared);
    if (target == null) {
      return targetNotARecord(mergeMethod, declared);
    }
    return checkTarget(mergeMethod, target, false);
  }

  private ReturnShape targetNotARecord(ExecutableElement mergeMethod, TypeMirror mirror) {
    Diagnostics.error(
        processingEnv.getMessager(),
        mergeMethod,
        TAG,
        "merge target '" + mirror + "' is not a record.",
        "The merge fills the target's record components; only records declare them.",
        "Return a record type (optionally wrapped in Validated<NonEmptyList<FieldError>, ...>).");
    return null;
  }

  private ReturnShape checkTarget(
      ExecutableElement mergeMethod, TypeElement target, boolean fallibleDeclared) {
    if (!target.getTypeParameters().isEmpty()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          mergeMethod,
          TAG,
          "'" + target.getSimpleName() + "' is generic, which this merge does not support.",
          "The generated Impl names the merged types directly; type parameters would leave it"
              + " referencing undeclared type variables.",
          "Merge concrete record types.");
      return null;
    }
    return new ReturnShape(target, fallibleDeclared);
  }

  /** One target-component fill: from which source parameter, and through which leaf (if any). */
  private record Fill(String component, String sourceParam, CodeBlock prism) {
    boolean fallible() {
      return prism != null;
    }
  }

  private List<Fill> classify(
      TypeElement spec,
      List<MappingProcessor.RegisteredSpec> registry,
      ExecutableElement mergeMethod,
      TypeElement target) {
    List<Fill> fills = new ArrayList<>();
    for (RecordComponentElement targetComponent : target.getRecordComponents()) {
      String name = targetComponent.getSimpleName().toString();
      List<VariableElement> holders = new ArrayList<>();
      for (VariableElement source : mergeMethod.getParameters()) {
        TypeElement sourceRecord = asRecord(source.asType());
        boolean has =
            sourceRecord.getRecordComponents().stream()
                .anyMatch(c -> c.getSimpleName().contentEquals(name));
        if (has) {
          holders.add(source);
        }
      }
      if (holders.isEmpty()) {
        Diagnostics.error(
            processingEnv.getMessager(),
            mergeMethod,
            TAG,
            "target component '"
                + target.getSimpleName()
                + "."
                + name
                + "' is not filled by any source.",
            "Every target component needs exactly one same-named source component.",
            "Add '" + name + "' to one of the sources, or drop it from the target.");
        return null;
      }
      if (holders.size() > 1) {
        Diagnostics.error(
            processingEnv.getMessager(),
            mergeMethod,
            TAG,
            "target component '"
                + name
                + "' is ambiguous: "
                + holders.stream().map(h -> "'" + h.getSimpleName() + "'").toList()
                + " both carry it.",
            "Every target component needs exactly one source; with several, the choice would be"
                + " arbitrary.",
            "Rename the component on all but one source (a typed disambiguation mechanism is a"
                + " planned follow-on).");
        return null;
      }
      VariableElement holder = holders.getFirst();
      RecordComponentElement sourceComponent =
          asRecord(holder.asType()).getRecordComponents().stream()
              .filter(c -> c.getSimpleName().contentEquals(name))
              .findFirst()
              .orElseThrow();
      // An explicit leaf always wins - even over a same-typed identity match, so a
      // ValidatedPrism<X, X> can validate or normalise a component the types alone would copy.
      ExecutableElement leaf =
          findLeaf(spec, name, sourceComponent.asType(), targetComponent.asType());
      if (leaf == null
          && processingEnv
              .getTypeUtils()
              .isSameType(sourceComponent.asType(), targetComponent.asType())) {
        fills.add(new Fill(name, holder.getSimpleName().toString(), null));
        continue;
      }
      if (leaf != null) {
        fills.add(
            new Fill(
                name,
                holder.getSimpleName().toString(),
                CodeBlock.of("$L()", leaf.getSimpleName())));
        continue;
      }
      List<MappingProcessor.RegisteredSpec> nested =
          registry.stream()
              .filter(MappingProcessor.RegisteredSpec::parseCapable)
              .filter(
                  r ->
                      processingEnv.getTypeUtils().isSameType(r.wire(), sourceComponent.asType())
                          && processingEnv
                              .getTypeUtils()
                              .isSameType(r.domain(), targetComponent.asType()))
              .toList();
      if (nested.size() > 1) {
        Diagnostics.error(
            processingEnv.getMessager(),
            mergeMethod,
            TAG,
            "target component '"
                + name
                + "' matches more than one mapping spec: "
                + nested.stream().map(r -> r.spec().getSimpleName().toString()).toList()
                + ".",
            "A nested fill resolves to the single spec mapping ("
                + targetComponent.asType()
                + ", "
                + sourceComponent.asType()
                + "); with several, the choice would be arbitrary.",
            "Add a leaf method '"
                + name
                + "()' delegating to the spec you want, or remove the duplicate spec.");
        return null;
      }
      if (nested.size() == 1) {
        fills.add(
            new Fill(
                name,
                holder.getSimpleName().toString(),
                CodeBlock.of("$T.INSTANCE.asValidatedPrism()", nested.getFirst().impl())));
        continue;
      }
      boolean primitiveInvolved =
          sourceComponent.asType().getKind().isPrimitive()
              || targetComponent.asType().getKind().isPrimitive();
      String fix =
          primitiveInvolved
              ? "Align the component types on the two records - a ValidatedPrism cannot carry a"
                  + " primitive type argument, so box the primitive on one side."
              : "Add 'default ValidatedPrism<"
                  + sourceComponent.asType()
                  + ", "
                  + targetComponent.asType()
                  + "> "
                  + name
                  + "()' to the spec (source first, target second), or declare a @GenerateMapping"
                  + " spec mapping those records in the same compilation.";
      Diagnostics.error(
          processingEnv.getMessager(),
          mergeMethod,
          TAG,
          "target component '" + target.getSimpleName() + "." + name + "' has no usable fill.",
          "The types differ ("
              + sourceComponent.asType()
              + " vs "
              + targetComponent.asType()
              + ") and no matching leaf method was found."
              + leafNearMissHint(spec, name)
              + projectionSpecHint(registry, sourceComponent.asType(), targetComponent.asType()),
          fix);
      return null;
    }
    return fills;
  }

  private String leafNearMissHint(TypeElement spec, String name) {
    for (ExecutableElement method : ElementFilter.methodsIn(spec.getEnclosedElements())) {
      if (method.getSimpleName().contentEquals(name) && method.isDefault()) {
        return " A default method '"
            + name
            + "()' exists but returns '"
            + method.getReturnType()
            + "'"
            + (method.getParameters().isEmpty() ? "" : " and declares parameters")
            + " — a leaf must be a zero-parameter default method returning exactly"
            + " ValidatedPrism<SourceComponent, TargetComponent> (source first, target second).";
      }
    }
    return "";
  }

  private String projectionSpecHint(
      List<MappingProcessor.RegisteredSpec> registry,
      TypeMirror sourceType,
      TypeMirror targetType) {
    return registry.stream()
        .filter(r -> !r.parseCapable())
        .filter(
            r ->
                processingEnv.getTypeUtils().isSameType(r.wire(), sourceType)
                    && processingEnv.getTypeUtils().isSameType(r.domain(), targetType))
        .findFirst()
        .map(
            r ->
                " '"
                    + r.spec().getSimpleName()
                    + "' maps this pair but is a projection (no parse), so it cannot fill a"
                    + " merge.")
        .orElse("");
  }

  private ExecutableElement findLeaf(
      TypeElement spec, String name, TypeMirror sourceType, TypeMirror targetType) {
    for (ExecutableElement method : ElementFilter.methodsIn(spec.getEnclosedElements())) {
      if (!method.getSimpleName().contentEquals(name)
          || !method.isDefault()
          || !method.getParameters().isEmpty()) {
        continue;
      }
      if (!(method.getReturnType() instanceof DeclaredType returnType)) {
        continue;
      }
      TypeElement raw = (TypeElement) returnType.asElement();
      if (!raw.getQualifiedName().contentEquals(VALIDATED_PRISM)
          || returnType.getTypeArguments().size() != 2) {
        continue;
      }
      if (processingEnv.getTypeUtils().isSameType(returnType.getTypeArguments().get(0), sourceType)
          && processingEnv
              .getTypeUtils()
              .isSameType(returnType.getTypeArguments().get(1), targetType)) {
        return method;
      }
    }
    return null;
  }

  private void writeImpl(
      TypeElement spec, ExecutableElement mergeMethod, ReturnShape shape, List<Fill> fills) {
    ClassName specName = ClassName.get(spec);
    // Nested specs join their enclosing simple names, so the generated class is always top-level.
    ClassName implName =
        ClassName.get(specName.packageName(), String.join("", specName.simpleNames()) + "Impl");
    TypeName targetName = TypeName.get(shape.target().asType());

    MethodSpec.Builder method =
        MethodSpec.methodBuilder(mergeMethod.getSimpleName().toString())
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .returns(TypeName.get(mergeMethod.getReturnType()));
    for (VariableElement source : mergeMethod.getParameters()) {
      method.addParameter(TypeName.get(source.asType()), source.getSimpleName().toString());
      method.addStatement(
          "$T.requireNonNull($L, $S)",
          OBJECTS,
          source.getSimpleName(),
          source.getSimpleName() + " must not be null");
    }

    if (shape.fallibleDeclared()) {
      CodeBlock.Builder chain = CodeBlock.builder().add("return $T.fields()", VALIDATED);
      for (Fill fill : fills) {
        if (fill.fallible()) {
          chain.add(
              "\n.field($S, $L.parse($L.$L()))",
              fill.component(),
              fill.prism(),
              fill.sourceParam(),
              fill.component());
        } else {
          chain.add(
              "\n.field($S, $T.validNel($L.$L()))",
              fill.component(),
              VALIDATED,
              fill.sourceParam(),
              fill.component());
        }
      }
      chain.add("\n.apply($T::new)", targetName);
      method.addStatement("$L", chain.build());
    } else {
      CodeBlock.Builder args = CodeBlock.builder();
      boolean first = true;
      for (Fill fill : fills) {
        if (!first) {
          args.add(", ");
        }
        first = false;
        args.add("$L.$L()", fill.sourceParam(), fill.component());
      }
      method.addStatement("return new $T($L)", targetName, args.build());
    }

    TypeSpec impl =
        TypeSpec.classBuilder(implName)
            .addOriginatingElement(spec)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(GENERATED)
            .addSuperinterface(specName)
            .addJavadoc(
                "Generated forward-only merge for {@link $T}: one target assembled from several"
                    + " sources, no inverse (truthful types).\n",
                specName)
            .addField(
                FieldSpec.builder(
                        implName, "INSTANCE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("new $T()", implName)
                    .build())
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
            .addMethod(method.build())
            .build();
    writeFile(spec, specName.packageName(), impl);
  }

  void writeFile(TypeElement spec, String packageName, TypeSpec impl) {
    try {
      JavaFile.builder(packageName, impl)
          .addFileComment("Generated by hkj-processor. Do not edit.")
          .build()
          .writeTo(processingEnv.getFiler());
    } catch (FilerException e) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "could not write the generated merge for '"
              + spec.getSimpleName()
              + "': the class already exists.",
          "Nested specs join their enclosing simple names, so two specs can collide on one Impl"
              + " name. The filer reported: "
              + e.getMessage()
              + ".",
          "Rename one of the colliding specs.");
    } catch (IOException e) {
      writeFailure(spec, e);
    }
  }

  private void writeFailure(TypeElement spec, IOException e) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "could not write the generated merge for '" + spec.getSimpleName() + "'.",
        "The filer reported: " + e.getMessage() + ".",
        "Check build-output permissions and free disk space, then rebuild.");
  }

  private TypeElement asRecord(TypeMirror mirror) {
    if (mirror instanceof DeclaredType declared) {
      TypeElement type = (TypeElement) declared.asElement();
      if (type.getKind() == ElementKind.RECORD) {
        return type;
      }
    }
    return null;
  }
}
