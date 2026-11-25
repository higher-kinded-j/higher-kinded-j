// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.hkj;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import io.avaje.spi.ServiceProvider;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;
import org.higherkindedj.optics.processing.spi.Cardinality;
import org.higherkindedj.optics.processing.spi.GenerationContext;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@link Try}. This
 * version uses the safe `fold` method to avoid unhandled checked exceptions.
 */
@ServiceProvider(TraversableGenerator.class)
public class TryGenerator extends BaseTraversableGenerator {

  /** Creates a new generator for {@link Try} fields. */
  public TryGenerator() {}

  private static final String FQN_TRY = "org.higherkindedj.hkt.trymonad.Try";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }
    final Element element = declaredType.asElement();
    return element.toString().equals(FQN_TRY);
  }

  @Override
  public Cardinality getCardinality() {
    return Cardinality.ZERO_OR_ONE;
  }

  @Override
  public String generateOpticExpression() {
    return "Affines.trySuccess()";
  }

  @Override
  public Set<String> getRequiredImports() {
    return Set.of("org.higherkindedj.optics.util.Affines");
  }

  /**
   * {@inheritDoc} Final, as hkj's processors call the {@link GenerationContext} form. It names
   * types as the {@code String} form does, for the record's own package.
   */
  @Override
  public final CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {
    // A caller that names no package gets the record's own, which is where a companion lands
    // unless a targetPackage or an import sends it elsewhere.
    return generateModifyF(
        component, recordClassName, allComponents, recordClassName.packageName());
  }

  /**
   * {@inheritDoc} Final, as hkj's processors call the {@link GenerationContext} form; a type-use
   * annotation deprecated by a javadoc tag alone is kept here, as this form reads
   * {@code @Deprecated} alone.
   *
   * @deprecated since 0.6.0, scheduled for removal in 0.7.0. Call {@link
   *     #generateModifyF(RecordComponentElement, ClassName, List, GenerationContext)}, which reads
   *     a javadoc {@code @deprecated} tag too.
   */
  @Deprecated(since = "0.6.0", forRemoval = true)
  @SuppressWarnings("removal") // implements the deprecated method until its removal
  @Override
  public final CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents,
      final String targetPackage) {
    return modifyF(
        component,
        recordClassName,
        allComponents,
        type -> ProcessorUtils.typeNameOf(type, targetPackage));
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents,
      final GenerationContext context) {
    return modifyF(component, recordClassName, allComponents, context::typeNameOf);
  }

  /** The body each form writes, with every type it names named by {@code naming}. */
  private CodeBlock modifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents,
      final Function<TypeMirror, TypeName> naming) {

    final String componentName = component.getSimpleName().toString();

    // Use the inherited helper to generate the constructor arguments.
    // The new value is wrapped in Try.success().
    final String constructorArgs =
        generateConstructorArgs(componentName, "Try.success(newValue)", allComponents);

    return CodeBlock.builder()
        .addStatement(
            "final $T tryA = source.$L()", naming.apply(component.asType()), componentName)
        // Use the safe `fold` method to handle both cases without throwing exceptions.
        .addStatement(
            "return tryA.fold(\n"
                + "    cause -> {$>\n"
                + "        // Case 1: The Try is a Failure. The traversal has no effect.\n"
                + "        // Return the original, unchanged source record lifted into the"
                + " applicative.\n"
                + "        return applicative.of(source);$<\n"
                + "}, \n"
                + "    successValue -> {$>\n"
                + "        // Case 2: The Try is a Success. Apply the effectful function.\n"
                + "        final var g_of_b = f.apply(successValue);\n"
                + "        // Map over the effect to reconstruct the record with the new value.\n"
                + "        return applicative.map(newValue -> new $T($L), g_of_b);$<\n"
                + "    }\n"
                + ")",
            // The record's class name for reconstruction
            recordTypeName(component, recordClassName),
            // The argument list for the record's constructor
            constructorArgs)
        .build();
  }
}
