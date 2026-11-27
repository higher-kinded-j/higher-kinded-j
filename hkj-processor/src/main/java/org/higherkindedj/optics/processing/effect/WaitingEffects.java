// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import java.lang.annotation.Annotation;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.TypeKey;

/**
 * The annotated types one effect processor holds for a later annotation-processing round, one
 * instance for each annotation it generates or checks for.
 *
 * <p>These are {@code @EffectAlgebra}, {@code @ComposeEffects}, {@code @Handles},
 * {@code @GeneratePathBridge} and {@code @PathSource}.
 *
 * <p>A constant another processor writes is missing in the round that writes it, and so is a class.
 * Set as the value of an effect annotation, as {@code @GeneratePathBridge(suffix = Names.SUFFIX)}
 * does, either reaches the processor as javac's mark for a value it could not resolve (see {@link
 * ProcessorUtils#unresolvedMark}). Read through the annotation's proxy, a constant's mark throws,
 * which stops javac, and a class literal's reads as an error type, which tells a check nothing. So
 * a type waits while what its processor reads, on it or on what it names, is unresolved, and is
 * read again in the next round, where javac supplies what was written. Each processor says what it
 * reads; a value read through a proxy is read only once its type is ready, which is what keeps that
 * read safe.
 *
 * <p>A class file holds a constant as its value, so a type read from one sets no mark. A class
 * literal it names that the classpath lacks reads as an error type, which no later round resolves,
 * so a processor that waits on that error type waits for good there.
 *
 * <p>Nothing is written in the last round, which javac also makes of the round after an error. A
 * type still waiting there generates nothing and reports nothing. What a declaration in source
 * waits for is a name it wrote, which javac reports as {@code cannot find symbol}.
 */
final class WaitingEffects {

  /** The annotation the processor generates or checks for. */
  private final Class<? extends Annotation> annotation;

  /** The types waiting for a later round, in the order met. */
  private final Set<TypeKey> waiting = new LinkedHashSet<>();

  /**
   * Creates the waiting types of one annotation.
   *
   * @param annotation the annotation the processor generates or checks for
   */
  WaitingEffects(Class<? extends Annotation> annotation) {
    this.annotation = annotation;
  }

  /**
   * The elements a round looks at: each type waiting from an earlier round, looked up afresh, as
   * javac attributes its annotations again each round, then each element the round brings carrying
   * the annotation. None in the last round.
   *
   * @param elements the compilation's element utilities
   * @param roundEnv the round
   * @return the elements, in the order met (non-null, unmodifiable)
   */
  List<Element> round(Elements elements, RoundEnvironment roundEnv) {
    List<TypeKey> waited = List.copyOf(waiting);
    waiting.clear();
    if (roundEnv.processingOver()) {
      return List.of();
    }
    return Stream.<Element>concat(
            waited.stream().map(key -> key.in(elements)),
            roundEnv.getElementsAnnotatedWith(annotation).stream())
        .toList();
  }

  /**
   * Whether a type can be generated or checked now: true where it is resolved. One that is not is
   * held for the next round.
   *
   * @param elements the compilation's element utilities
   * @param type one of the round's elements, of a kind the processor accepts
   * @param unresolved whether what the processor reads of the type holds something javac could not
   *     resolve
   * @return true where the type is generated or checked in this round
   */
  boolean ready(Elements elements, TypeElement type, boolean unresolved) {
    if (!unresolved) {
      return true;
    }
    waiting.add(TypeKey.of(elements, type));
    return false;
  }

  /**
   * Whether an annotation an element carries sets a value javac could not resolve. An effect
   * annotation's members each take one value, never an array or an annotation, so each value is the
   * mark or is not.
   *
   * @param element the annotated element
   * @param annotation the annotation read
   * @return true where the element carries the annotation and one of its values is the mark
   */
  static boolean unresolved(Element element, Class<? extends Annotation> annotation) {
    return Stream.ofNullable(ProcessorUtils.findAnnotation(element, annotation.getCanonicalName()))
        .map(AnnotationMirror::getElementValues)
        .flatMap(values -> values.values().stream())
        .anyMatch(ProcessorUtils::unresolvedMark);
  }
}
