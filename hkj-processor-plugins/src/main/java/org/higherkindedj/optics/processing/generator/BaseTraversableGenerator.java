// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * An abstract base class for {@link TraversableGenerator} implementations that provides common
 * helper methods to reduce code duplication.
 */
public abstract class BaseTraversableGenerator implements TraversableGenerator {

  /** Creates a new {@code BaseTraversableGenerator}. */
  protected BaseTraversableGenerator() {}

  /**
   * Extracts the primary generic type from a container-like record component.
   *
   * <p>For example, for a component of type {@code List<String>}, this returns "String". A wildcard
   * argument is resolved to the type it stands for, as {@link #getTypeArgumentName} describes.
   *
   * @param component The record component to inspect.
   * @return The {@link TypeName} of the first generic argument, or {@code Object} as a fallback.
   */
  protected TypeName getGenericTypeName(final RecordComponentElement component) {
    return getTypeArgumentName(component, 0);
  }

  /**
   * Extracts one type argument of a container-like record component, named so that it can be
   * written into generated source.
   *
   * <p>A wildcard argument is resolved to the type it stands for: {@code ? extends T} is named
   * {@code T}, and {@code ?} or {@code ? super T} is named {@code Object}. A wildcard is not
   * denotable on its own, so naming it verbatim would emit source that does not compile; the type
   * it resolves to is both denotable and the one {@code @GenerateFocus} reads for the same
   * component.
   *
   * @param component The record component to inspect.
   * @param index The type argument to read.
   * @return The {@link TypeName} of that argument, or {@code Object} when the component has no such
   *     argument.
   */
  protected TypeName getTypeArgumentName(final RecordComponentElement component, final int index) {
    return argumentName(component, index, ProcessorUtils::typeNameOf);
  }

  /**
   * {@link #getTypeArgumentName(RecordComponentElement, int)} for a file written into {@code
   * targetPackage}: a type-use annotation that package cannot name is left off, rather than copied
   * into a file that could not compile with it. The two-argument form keeps a package-private one,
   * which is right only where the file lands in the type's own package.
   *
   * @param component The record component to inspect.
   * @param index The type argument to read.
   * @param targetPackage The package the generated file is written into.
   * @return The {@link TypeName} of that argument, or {@code Object} when the component has no such
   *     argument.
   * @since 0.4.11
   */
  protected TypeName getTypeArgumentName(
      final RecordComponentElement component, final int index, final String targetPackage) {
    return argumentName(component, index, type -> ProcessorUtils.typeNameOf(type, targetPackage));
  }

  /** The type argument at {@code index}, named by {@code naming}, or {@code Object}. */
  private TypeName argumentName(
      final RecordComponentElement component,
      final int index,
      final Function<TypeMirror, TypeName> naming) {
    if (component.asType() instanceof DeclaredType containerType
        && containerType.getTypeArguments().size() > index) {
      final TypeMirror resolved = resolveEffectiveType(containerType.getTypeArguments().get(index));
      if (resolved != null) {
        return naming.apply(resolved).box();
      }
    }
    return ClassName.get(Object.class); // Raw, absent, or a wildcard standing for anything at all.
  }

  /**
   * Generates the comma-separated argument list needed to call the record's constructor.
   *
   * <p>It replaces the component being modified with the {@code newComponentValue} placeholder and
   * uses the accessor for all other components.
   *
   * <p>Every argument here has its component's own type: an accessor reads one, and the component
   * being modified is a container, which is never primitive. So the call reaches the canonical
   * constructor as it stands, where a value handed over boxed would need the unboxing {@code
   * ProcessorUtils.canonicalArgument} writes for a lens.
   *
   * @param changedComponent The name of the component being modified.
   * @param newComponentValue The string representing the new value for the modified component
   *     (e.g., "Optional.of(newValue)").
   * @param allComponents The list of all components in the record.
   * @return A string representing the complete argument list for the record's constructor.
   */
  protected String generateConstructorArgs(
      final String changedComponent,
      final String newComponentValue,
      final List<? extends RecordComponentElement> allComponents) {

    return allComponents.stream()
        .map(
            c -> {
              final String name = c.getSimpleName().toString();
              return name.equals(changedComponent) ? newComponentValue : "source." + name + "()";
            })
        .collect(Collectors.joining(", "));
  }
}
