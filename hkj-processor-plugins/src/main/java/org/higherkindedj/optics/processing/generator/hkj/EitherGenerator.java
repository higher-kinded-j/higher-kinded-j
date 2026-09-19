// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.hkj;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import io.avaje.spi.ServiceProvider;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;
import org.higherkindedj.optics.processing.spi.Cardinality;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields of type {@link Either},
 * focusing on the right value.
 */
@ServiceProvider(TraversableGenerator.class)
public class EitherGenerator extends BaseTraversableGenerator {

  /** Creates a new generator for {@link Either} fields. */
  public EitherGenerator() {}

  private static final String FQN_EITHER = "org.higherkindedj.hkt.either.Either";

  @Override
  public boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }
    final Element element = declaredType.asElement();
    return element != null && element.toString().equals(FQN_EITHER);
  }

  @Override
  public Cardinality getCardinality() {
    return Cardinality.ZERO_OR_ONE;
  }

  @Override
  public int getFocusTypeArgumentIndex() {
    return 1; // Either<L, R> focuses on R (the second type argument)
  }

  @Override
  public String generateOpticExpression() {
    return "Affines.eitherRight()";
  }

  @Override
  public Set<String> getRequiredImports() {
    return Set.of("org.higherkindedj.optics.util.Affines");
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {
    // A caller that names no package gets the record's own, which is where a companion lands
    // unless a targetPackage or an import sends it elsewhere.
    return generateModifyF(
        component, recordClassName, allComponents, recordClassName.packageName());
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents,
      final String targetPackage) {

    final String componentName = component.getSimpleName().toString();

    final String constructorArgs =
        generateConstructorArgs(componentName, "Either.right(newValue)", allComponents);

    return CodeBlock.builder()
        // The local keeps the component's own type, wildcards and all, so that the accessor
        // assigns to it.
        .addStatement(
            "final $T either = source.$L()",
            ProcessorUtils.typeNameOf(component.asType(), targetPackage),
            componentName)
        .beginControlFlow("if (either.isRight())")
        .addStatement("final var g_of_b = f.apply(either.getRight())")
        .addStatement(
            "return applicative.map(newValue -> new $T($L), g_of_b)",
            recordTypeName(component, recordClassName),
            constructorArgs)
        .nextControlFlow("else")
        .addStatement("return applicative.of(source)")
        .endControlFlow()
        .build();
  }
}
