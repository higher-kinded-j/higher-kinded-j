// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.pcollections;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;
import org.higherkindedj.optics.util.Traversals;

/**
 * Base class for {@link org.higherkindedj.optics.processing.spi.TraversableGenerator}s that target
 * PCollections persistent map types ({@code PMap}, {@code PSortedMap}). The traversal focuses on
 * the <em>values</em> of the map; keys are passed through unchanged.
 *
 * <p>Each PCollections map implements {@link java.util.Map}, so the source field's {@link
 * Map#entrySet()} can be copied directly into a JDK {@link ArrayList}. After traversal, a JDK
 * {@link Map} is rebuilt from the new entries and handed to the persistent type's {@code from(Map)}
 * factory.
 */
public abstract class PCollectionsBaseMapValueTraversableGenerator
    extends BaseTraversableGenerator {

  /** Root package for the PCollections public API. */
  protected static final String PCOLLECTIONS_PACKAGE = "org.pcollections";

  /** {@code org.pcollections.PMap} interface. */
  public static final ClassName PMAP = ClassName.get(PCOLLECTIONS_PACKAGE, "PMap");

  /** {@code org.pcollections.PSortedMap} interface. */
  public static final ClassName PSORTED_MAP = ClassName.get(PCOLLECTIONS_PACKAGE, "PSortedMap");

  /** {@code org.pcollections.HashTreePMap} concrete factory. */
  public static final ClassName HASH_TREE_PMAP =
      ClassName.get(PCOLLECTIONS_PACKAGE, "HashTreePMap");

  /** {@code org.pcollections.TreePMap} concrete factory (sorted map). */
  public static final ClassName TREE_PMAP = ClassName.get(PCOLLECTIONS_PACKAGE, "TreePMap");

  /** The PCollections map interface this generator matches against. */
  protected final ClassName supportedType;

  /** The concrete factory class used to reconstruct instances. */
  protected final ClassName constructedType;

  /**
   * @param supportedType the persistent map interface this generator recognises
   * @param constructedType the concrete factory whose {@code from(Map)} reconstructs instances of
   *     {@code supportedType}
   */
  protected PCollectionsBaseMapValueTraversableGenerator(
      final ClassName supportedType, final ClassName constructedType) {
    this.supportedType = supportedType;
    this.constructedType = constructedType;
  }

  @Override
  public final boolean supports(final TypeMirror type) {
    if (!(type instanceof DeclaredType declaredType)) {
      return false;
    }
    final Element element = declaredType.asElement();
    return element != null && element.toString().equals(supportedType.canonicalName());
  }

  @Override
  public int getFocusTypeArgumentIndex() {
    return 1; // PMap<K, V> focuses on V
  }

  @Override
  public String generateOpticExpression() {
    // Each PCollections map implements java.util.Map, so the single-argument
    // mapValuesEachCollecting overload (bound M extends Map) applies directly.
    return "EachInstances.mapValuesEachCollecting(map -> "
        + constructedType.simpleName()
        + ".from(map))";
  }

  @Override
  public Set<String> getRequiredImports() {
    return Set.of(
        "org.higherkindedj.optics.each.EachInstances",
        constructedType.packageName() + "." + constructedType.simpleName());
  }

  @Override
  public CodeBlock generateModifyF(
      final RecordComponentElement component,
      final ClassName recordClassName,
      final List<? extends RecordComponentElement> allComponents) {

    final String componentName = component.getSimpleName().toString();

    return CodeBlock.builder()
        // 1. Pull the persistent map's entries into a JDK ArrayList for traversal.
        .addStatement(
            "final var sourceEntries = new $T<>(source.$L().entrySet())",
            ArrayList.class,
            componentName)
        // 2. Lift each entry into the applicative, applying f to the value and reconstructing
        //    the entry around the new value.
        .addStatement(
            "final $T<Map.Entry<$T, $T>, $T<F, Map.Entry<$T, $T>>> entryF = \n"
                + "    entry -> applicative.map(newValue -> $T.entry(entry.getKey(), newValue),"
                + " f.apply(entry.getValue()))",
            Function.class,
            getKeyTypeName(component),
            getValueTypeName(component),
            Kind.class,
            getKeyTypeName(component),
            getValueTypeName(component),
            Map.class)
        // 3. Traverse the entry list.
        .addStatement(
            "final var effectOfEntries = $T.traverseList(sourceEntries, entryF, applicative)",
            Traversals.class)
        // 4. Map the effect-of-entries into a JDK Map keyed by entry key, valued by entry value.
        //    The intermediate JDK Map type is fixed by the entryF declaration above and the
        //    Map.Entry method references in the collector, giving step 5 a fully-resolved
        //    Map<K, V> to hand to the persistent factory.
        .addStatement(
            "final var effectOfMap = applicative.map(\n"
                + "    newEntries -> newEntries.stream().collect($T.toMap($T.Entry::getKey,"
                + " $T.Entry::getValue)),\n"
                + "    effectOfEntries)",
            Collectors.class,
            Map.class,
            Map.class)
        // 5. Reconstruct the record, handing the rebuilt JDK Map to the persistent factory inline
        //    so that the call is in a position where javac can fully resolve any generic bounds
        //    on {@code from(Map)} (e.g. TreePMap's {@code K extends Comparable<? super K>}).
        .addStatement(
            "return applicative.map(\n    jdkMap -> new $T($L),\n    effectOfMap)",
            recordClassName,
            buildConstructorArgsWithFromCall(componentName, allComponents))
        .build();
  }

  /**
   * Builds the record constructor argument list as a {@link CodeBlock} so that JavaPoet emits an
   * import for {@code constructedType} when the converted component refers to {@code
   * <constructedType>.from(jdkMap)}. All other components are forwarded by accessor as literal
   * text.
   */
  private CodeBlock buildConstructorArgsWithFromCall(
      final String changedComponent, final List<? extends RecordComponentElement> allComponents) {
    final CodeBlock.Builder builder = CodeBlock.builder();
    boolean first = true;
    for (final RecordComponentElement c : allComponents) {
      if (!first) {
        builder.add(", ");
      }
      first = false;
      final String name = c.getSimpleName().toString();
      if (name.equals(changedComponent)) {
        builder.add("$T.from(jdkMap)", constructedType);
      } else {
        builder.add("source.$L()", name);
      }
    }
    return builder.build();
  }

  private TypeName getValueTypeName(final RecordComponentElement component) {
    if (component.asType() instanceof DeclaredType containerType) {
      if (containerType.getTypeArguments().size() < 2) {
        return ClassName.get(Object.class);
      }
      return TypeName.get(containerType.getTypeArguments().get(1)).box();
    }
    return ClassName.get(Object.class);
  }

  private TypeName getKeyTypeName(final RecordComponentElement component) {
    if (component.asType() instanceof DeclaredType containerType) {
      if (containerType.getTypeArguments().isEmpty()) {
        return ClassName.get(Object.class);
      }
      return TypeName.get(containerType.getTypeArguments().getFirst()).box();
    }
    return ClassName.get(Object.class);
  }
}
