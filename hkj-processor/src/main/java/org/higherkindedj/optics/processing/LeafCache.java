// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.Reachability;
import org.higherkindedj.optics.processing.util.TypeKey;

/**
 * Makes a generated Impl read each {@code default} leaf it calls once, on first use, and keep it.
 * The Impl calls a leaf on every {@code build}, {@code parse}, {@code updateFrom} or merge, so a
 * leaf that constructs its codec, such as {@code localDate(DateTimeFormatter.ofPattern(...))},
 * would otherwise construct it on every call.
 *
 * <p>A processor emits every leaf call through {@link #call} while it resolves a spec, which
 * records the leaf against the spec, and {@link #addOverrides} then overrides exactly the recorded
 * leaves in the spec's Impl. The call sites stay {@code email()}: the override is what they reach.
 * A member the Impl never calls, such as an inherited derived field its wire omits, stays inert and
 * leaves no trace in the Impl.
 *
 * <p>The read is lazy, on first use rather than in the constructor. A self-recursive spec's leaf
 * may reach its own Impl's {@code INSTANCE}, which the constructor runs before it is set, and a
 * spec holding {@code MAPPER = XImpl.INSTANCE} constructs the Impl part-way through its own
 * initialisation, when a later constant a leaf reads is still null. Laziness covers an Impl
 * constructed during that initialisation, not one used during it: a {@code build} run from a spec
 * constant's initialiser reads the leaf then, and keeps what it read.
 *
 * <p>No lock is taken. A race reads the leaf twice, which a leaf that answers the same codec each
 * time cannot tell apart, and never more often than a read per call did. A lock would hold a
 * monitor across the leaf's own code, which may initialise a class or reach another Impl's leaf,
 * and two threads taking those in opposite orders would deadlock.
 */
final class LeafCache {

  /** The leaves each spec's Impl calls, recorded by {@link #call}, by the spec. */
  private final Map<TypeKey, Set<String>> called = new HashMap<>();

  /**
   * A call to the spec's leaf, as the Impl emits it, recorded for {@link #addOverrides}.
   *
   * @param elements the compilation's element utilities
   * @param spec the spec whose leaf the Impl calls
   * @param leaf the leaf's name
   * @return the call, {@code leaf()}
   */
  CodeBlock call(Elements elements, TypeElement spec, CharSequence leaf) {
    called.computeIfAbsent(TypeKey.of(elements, spec), key -> new HashSet<>()).add(leaf.toString());
    return CodeBlock.of("$L()", leaf);
  }

  /**
   * Overrides each {@code default} leaf the spec's Impl calls, whether the spec declares it or
   * inherits it, with one that reads it on first use and keeps it, then forgets the spec.
   *
   * <p>A leaf takes no parameters, and Java allows one such method per name, so a recorded name
   * picks exactly one member however many overloads share it. An abstract leaf is already a field
   * of an element-mapped Impl. The override writes the leaf's type out, which it always can: a
   * called leaf converts a pair the Impl maps, and {@link Reachability} has refused any type in
   * that pair the Impl could not name before generation starts.
   *
   * @param env the processing environment
   * @param implBuilder the Impl being built, which implements the spec
   * @param spec the spec
   * @param specName the spec's name, the Impl's only superinterface
   * @param members the spec's members, own and inherited
   */
  void addOverrides(
      ProcessingEnvironment env,
      TypeSpec.Builder implBuilder,
      TypeElement spec,
      ClassName specName,
      List<ExecutableElement> members) {
    Set<String> leaves = called.remove(TypeKey.of(env.getElementUtils(), spec));
    if (leaves == null) {
      return;
    }
    for (ExecutableElement method : members) {
      if (!leaves.contains(method.getSimpleName().toString())
          || !method.getParameters().isEmpty()
          || !method.isDefault()) {
        continue;
      }
      DeclaredType leafType =
          (DeclaredType)
              ProcessorUtils.memberOf(env.getTypeUtils(), (DeclaredType) spec.asType(), method)
                  .getReturnType();
      String name = method.getSimpleName().toString();
      String field = "hkj$leaf$" + name;
      TypeName typeName =
          ProcessorUtils.typeNameOf(
              leafType,
              method.getReturnType(),
              (DeclaredType) spec.asType(),
              specName.packageName());
      List<AnnotationSpec> suppression = ProcessorUtils.rawTypesSuppression(List.of(leafType));
      implBuilder
          .addField(
              FieldSpec.builder(typeName, field, Modifier.PRIVATE, Modifier.VOLATILE)
                  .addAnnotations(suppression)
                  .build())
          .addMethod(
              MethodSpec.methodBuilder(name)
                  .addAnnotation(Override.class)
                  .addAnnotations(suppression)
                  .addModifiers(Modifier.PUBLIC)
                  .returns(typeName)
                  .addJavadoc(
                      "{@inheritDoc}\n\n<p>Read on first use and kept, rather than read on every"
                          + " call.\n")
                  .addStatement("$T leaf = $N", typeName, field)
                  .beginControlFlow("if (leaf == null)")
                  .addStatement("leaf = $T.super.$N()", specName, name)
                  .addStatement("$N = leaf", field)
                  .endControlFlow()
                  .addStatement("return leaf")
                  .build());
    }
  }
}
