// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator.apache;

import com.palantir.javapoet.CodeBlock;
import io.avaje.spi.ServiceProvider;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;

/**
 * A {@link TraversableGenerator} that adds support for traversing fields that are Apache
 * Collections 4 {@code TreeMultiSet}.
 *
 * <p>Both the traversal and the Focus path rebuild the multiset with the source's comparator, so a
 * multiset kept in a custom order stays in it, and an element with no natural order is sorted as
 * the source sorted it. A multiset in natural order has a null comparator, which the constructor
 * reads as natural order again.
 *
 * <p>{@code TreeMultiSet} has no constructor that takes a comparator and the elements together, so
 * the elements are collected into a multiset made with the comparator. Made from the comparator
 * alone and filled afterwards, the multiset would be typed for a capture of the comparator's {@code
 * ? super} wildcard, which the record's component cannot hold.
 */
@ServiceProvider(TraversableGenerator.class)
public final class ApacheTreeMultiSetGenerator
    extends ApacheBaseSingleIterableTraversableGenerator {

  /** Creates a new generator for Apache Commons Collections {@code TreeMultiSet} fields. */
  public ApacheTreeMultiSetGenerator() {
    super(TREE_MULTI_SET);
  }

  @Override
  public String generateOpticExpression() {
    return "EachInstances.fromIterableRebuilding((source, list) -> list.stream()"
        + ".collect(Collectors.toCollection(() -> new "
        + supportedType.simpleName()
        + "<>(source.comparator()))))";
  }

  @Override
  public Set<String> getRequiredImports() {
    return Set.of(
        "org.higherkindedj.optics.each.EachInstances",
        "java.util.stream.Collectors",
        supportedType.canonicalName());
  }

  /** A sorted multiset is ordered by its element. */
  @Override
  public OptionalInt getOrderedTypeArgumentIndex() {
    return OptionalInt.of(0);
  }

  @Override
  protected CodeBlock rebuild(final String componentName) {
    return CodeBlock.of(
        "newList.stream().collect($T.toCollection(() -> new $T<>(source.$L().comparator())))",
        Collectors.class,
        supportedType,
        componentName);
  }
}
