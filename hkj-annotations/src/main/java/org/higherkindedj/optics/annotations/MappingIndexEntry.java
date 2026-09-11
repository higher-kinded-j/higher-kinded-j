// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an index entry the mapping processor writes beside each generated {@code <Spec>Impl}, so a
 * later compilation can find the specs compiled into its dependencies.
 *
 * <p>Nested, sealed and merge resolution consult every {@code @GenerateMapping} spec in the same
 * compilation. A spec compiled in another module is a class file on the classpath, and the class
 * file carries everything resolution needs (the {@code MappingSpec} supertype with its type
 * arguments, the leaves, the record shapes); what javac offers no way to list is <em>which</em>
 * class files are specs. The index closes that gap: every generated Impl of a {@code MappingSpec}
 * is accompanied by one empty class in the package {@code org.higherkindedj.mapping.index},
 * carrying this annotation with the spec's canonical name, and a downstream compilation enumerates
 * that package and re-reads each named spec from the classpath as if it were in the round.
 *
 * <p>Not for hand use: the processor writes and reads these entries. The annotation is retained in
 * the class file (not runtime-visible) so that the compiler can read it without the annotations
 * module on the runtime classpath.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface MappingIndexEntry {

  /**
   * The canonical name of the {@code @GenerateMapping} spec this entry indexes.
   *
   * @return the spec's canonical name, as {@code Elements.getTypeElement} resolves it
   */
  String spec();
}
