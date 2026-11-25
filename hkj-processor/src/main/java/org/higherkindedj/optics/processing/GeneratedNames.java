// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ClassName;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The check a generator makes before writing a class: that no class it did not generate already has
 * the name. javac's Filer refuses a name a source of the compilation declares, so the check covers
 * what it lets through, a class read from a class file (see {@link
 * ProcessorUtils#replacesUngeneratedClass}). {@code @GenerateMapping}, {@code @GenerateMerge},
 * {@code @GenerateErrorEnvelope} and {@code @GenerateIsos} make it here, and {@code @PathSource}
 * makes it in its own name checks; the other generators do not make it yet.
 */
final class GeneratedNames {

  private GeneratedNames() {}

  /**
   * Refuses, at {@code at}, a class name another class read from a class file already has, and says
   * whether it refused. Gradle's incremental build reads a hand-written class it did not recompile
   * that way, so without this the generated class would replace it, where a clean build refuses the
   * name.
   *
   * @param env the processing environment
   * @param at the element the generated class is written for
   * @param tag the annotation the error is reported under
   * @param generated the class about to be written
   * @param rename how else the generated class could take another name, as a clause
   * @return whether the name was refused, and nothing should be written
   */
  static boolean refusesReplacing(
      ProcessingEnvironment env, Element at, String tag, ClassName generated, String rename) {
    String name = generated.canonicalName();
    if (!ProcessorUtils.replacesUngeneratedClass(env.getElementUtils(), name)) {
      return false;
    }
    // HkjHttpClientProcessor.refusesReplacing words its refusal the same way.
    Diagnostics.error(
        env.getMessager(),
        at,
        tag,
        "the generated class '" + name + "' has the name of a class on the classpath.",
        "javac reads that class from a class file without HKJ's @Generated, so it is a dependency's,"
            + " another processor's output from an earlier build, or a hand-written class Gradle's"
            + " incremental build did not recompile, and the generated class would replace it.",
        "Rename the class on the classpath where it is yours, or "
            + rename
            + ", or rebuild from clean where it is an earlier build's output.");
    return true;
  }
}
