// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.pcollections;

import com.palantir.javapoet.CodeBlock;
import io.avaje.spi.ServiceProvider;
import java.util.OptionalInt;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing the values of fields of type
 * {@code org.pcollections.PSortedMap}. Reconstruction uses {@code TreePMap.from(Comparator, Map)}
 * with the source's comparator, so a map kept in a custom key order stays in it, and a key with no
 * natural order is sorted as the source sorted it. A {@code PSortedMap} in natural order reports
 * that order as its comparator, so it is kept too.
 */
@ServiceProvider(TraversableGenerator.class)
public final class PSortedMapValueGenerator extends PCollectionsBaseMapValueTraversableGenerator {

  /** Creates a new generator for {@code PSortedMap} fields. */
  public PSortedMapValueGenerator() {
    super(PSORTED_MAP, TREE_PMAP);
  }

  @Override
  public String generateOpticExpression() {
    return "EachInstances.mapValuesEachRebuilding((source, map) -> "
        + constructedType.simpleName()
        + ".from(source.comparator(), map))";
  }

  /** A sorted map is ordered by its key, though it focuses its value. */
  @Override
  public OptionalInt getOrderedTypeArgumentIndex() {
    return OptionalInt.of(0);
  }

  @Override
  protected CodeBlock rebuild(final String componentName) {
    return CodeBlock.of(
        "$T.from(source.$L().comparator(), jdkMap)", constructedType, componentName);
  }
}
