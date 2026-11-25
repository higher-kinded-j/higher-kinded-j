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
import javax.lang.model.util.Elements;
import org.higherkindedj.optics.processing.spi.GenerationContext;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * A starting point for a {@link TraversableGenerator}, built-in or a plugin's. It implements none
 * of the SPI's methods: a subclass still implements {@code supports} and {@code generateModifyF}.
 * It adds helpers for the body {@code generateModifyF} writes: {@link
 * #getTypeArgumentName(RecordComponentElement, int, GenerationContext)} names one of a component's
 * type arguments, resolving a wildcard so that the generated source compiles, and {@link
 * #getTypeArgumentName(RecordComponentElement, int, Function)} does the same with a naming of the
 * caller's, for a body shared between forms. {@link #generateConstructorArgs} writes the arguments
 * that rebuild the record with one component replaced.
 */
public abstract class BaseTraversableGenerator implements TraversableGenerator {

  /** Creates a new {@code BaseTraversableGenerator}. */
  protected BaseTraversableGenerator() {}

  /**
   * The first type argument of a container-like record component, named as {@link
   * #getTypeArgumentName(RecordComponentElement, int)} names it: for a component of type {@code
   * List<String>}, {@code String}.
   *
   * @param component The record component to inspect.
   * @return The {@link TypeName} of the first generic argument, or {@code Object} as a fallback.
   * @deprecated since 0.6.0, scheduled for removal in 0.7.0. Call {@link
   *     #getTypeArgumentName(RecordComponentElement, int, GenerationContext)} with index 0 and the
   *     context {@code generateModifyF} is handed, which leaves off an annotation the file cannot
   *     write cleanly.
   */
  @Deprecated(since = "0.6.0", forRemoval = true)
  protected TypeName getGenericTypeName(final RecordComponentElement component) {
    return getTypeArgumentName(component, 0);
  }

  /**
   * {@link #getTypeArgumentName(RecordComponentElement, int, Function)}, with each type named as
   * {@link ProcessorUtils#typeNameOf(Elements, TypeMirror)} names it, but with a deprecation read
   * from {@code @Deprecated} alone. A package-private annotation is kept, which is right only where
   * the file lands in the type's own package.
   *
   * @param component The record component to inspect.
   * @param index The type argument to read.
   * @return The {@link TypeName} of that argument, or {@code Object} when the component has no such
   *     argument.
   * @since 0.4.10
   * @deprecated since 0.6.0, scheduled for removal in 0.7.0. Call {@link
   *     #getTypeArgumentName(RecordComponentElement, int, GenerationContext)} with the context
   *     {@code generateModifyF} is handed, which leaves off an annotation the file cannot write
   *     cleanly.
   */
  @Deprecated(since = "0.6.0", forRemoval = true)
  @SuppressWarnings("removal") // delegates to the deprecated method until its removal
  protected TypeName getTypeArgumentName(final RecordComponentElement component, final int index) {
    return getTypeArgumentName(component, index, ProcessorUtils::typeNameOf);
  }

  /**
   * {@link #getTypeArgumentName(RecordComponentElement, int, GenerationContext)} for a file written
   * into {@code targetPackage}, but with a deprecation read from {@code @Deprecated} alone, so a
   * type-use annotation deprecated only by a javadoc tag is kept.
   *
   * @param component The record component to inspect.
   * @param index The type argument to read.
   * @param targetPackage The package the generated file is written into.
   * @return The {@link TypeName} of that argument, or {@code Object} when the component has no such
   *     argument.
   * @since 0.4.11
   * @deprecated since 0.6.0, scheduled for removal in 0.7.0. Call {@link
   *     #getTypeArgumentName(RecordComponentElement, int, GenerationContext)}, which reads a
   *     javadoc {@code @deprecated} tag too.
   */
  @Deprecated(since = "0.6.0", forRemoval = true)
  @SuppressWarnings("removal") // delegates to the deprecated method until its removal
  protected TypeName getTypeArgumentName(
      final RecordComponentElement component, final int index, final String targetPackage) {
    return getTypeArgumentName(
        component, index, type -> ProcessorUtils.typeNameOf(type, targetPackage));
  }

  /**
   * {@link #getTypeArgumentName(RecordComponentElement, int, Function)} for a file written where
   * {@code context} says, each type named as {@link GenerationContext#typeNameOf} names it. A
   * type-use annotation the file cannot write cleanly is left off: one its package cannot name, and
   * one deprecated, by {@code @Deprecated} or by a javadoc {@code @deprecated} tag alone. A body
   * written in {@link #generateModifyF(RecordComponentElement, ClassName, List, GenerationContext)}
   * names its type arguments with this.
   *
   * @param component The record component to inspect.
   * @param index The type argument to read.
   * @param context Where the generated file is written, and the round's element utilities.
   * @return The {@link TypeName} of that argument, or {@code Object} when the component has no such
   *     argument.
   * @since 0.6.0
   */
  protected TypeName getTypeArgumentName(
      final RecordComponentElement component, final int index, final GenerationContext context) {
    return getTypeArgumentName(component, index, context::typeNameOf);
  }

  /**
   * One type argument of a container-like record component, named by {@code naming} so that it can
   * be written into generated source.
   *
   * <p>A wildcard argument is resolved to the type it stands for: {@code ? extends T} is named as
   * {@code naming} names {@code T}, and {@code ?} or {@code ? super T} is named {@code Object}. A
   * wildcard is not denotable on its own, so naming it verbatim would emit source that does not
   * compile; the type it resolves to is both denotable and the one {@code @GenerateFocus} reads for
   * the same component. A component with no argument at {@code index}, such as a raw one, is read
   * as {@code Object} too, and the name is boxed.
   *
   * <p>A body shared between the forms of {@code generateModifyF} reads its type arguments through
   * this, with the naming each form hands it.
   *
   * @param component The record component to inspect.
   * @param index The type argument to read.
   * @param naming Names the type the argument stands for.
   * @return The {@link TypeName} of that argument, or {@code Object} when the component has no such
   *     argument.
   * @since 0.6.0
   */
  protected TypeName getTypeArgumentName(
      final RecordComponentElement component,
      final int index,
      final Function<? super TypeMirror, ? extends TypeName> naming) {
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
