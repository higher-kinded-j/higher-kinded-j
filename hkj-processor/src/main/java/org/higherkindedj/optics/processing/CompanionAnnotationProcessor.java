// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/**
 * Claims the hkj annotations no generating processor names: those a processor reads from the
 * element it generates for, such as {@code @MapField} on a mapping spec, those it writes onto the
 * code it generates, such as {@code @Generated}, and the coverage marker written by hand.
 *
 * <p>javac reports an annotation no processor claims under {@code -Xlint:processing}, so a build
 * run with {@code -Xlint:all -Werror} would fail on each of these, in a user's own sources and in
 * every generated file, whatever else they contain. The processors that use them name only the
 * annotation they generate for, and read these from the elements it marks, so claiming them here
 * changes nothing those processors see.
 *
 * <p>A claimed annotation is offered to no processor after this one, and javac offers a round to no
 * processor that has not yet run once every annotation in it is claimed. A processor that supports
 * every annotation, such as Lombok's, belongs ahead of {@code hkj-processor} on the processor path,
 * as it does beside the generating processors, which claim their own annotations.
 *
 * <p>Every other annotation {@code hkj-annotations} declares is claimed by the processor that
 * generates for it, save {@code @PathConfig}, which no processor reads. This one generates nothing,
 * so it is registered with Gradle as isolating: there is no output for Gradle to track, and the
 * types carrying these annotations, every generated class among them, are not reprocessed on each
 * incremental compile as an aggregating processor's would be.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({
  // Written onto generated code.
  "org.higherkindedj.optics.annotations.Generated",
  "org.higherkindedj.optics.annotations.MappingIndexEntry",
  // Written by hand, on code a coverage report leaves out.
  "org.higherkindedj.annotation.Generated",
  // Read from a mapping spec.
  "org.higherkindedj.optics.annotations.MapField",
  "org.higherkindedj.optics.annotations.MapKey",
  "org.higherkindedj.optics.annotations.OptionalBridge",
  "org.higherkindedj.optics.annotations.Flatten",
  "org.higherkindedj.optics.annotations.Unmapped",
  // Read from a record optics are generated for.
  "org.higherkindedj.optics.annotations.TraverseField",
  // Read from an optics spec interface.
  "org.higherkindedj.optics.annotations.InstanceOf",
  "org.higherkindedj.optics.annotations.MatchWhen",
  "org.higherkindedj.optics.annotations.ThroughField",
  "org.higherkindedj.optics.annotations.TraverseWith",
  "org.higherkindedj.optics.annotations.ViaBuilder",
  "org.higherkindedj.optics.annotations.ViaConstructor",
  "org.higherkindedj.optics.annotations.ViaCopyAndSet",
  "org.higherkindedj.optics.annotations.Wither",
  // Read from a service a Path bridge is generated for.
  "org.higherkindedj.hkt.effect.annotation.PathVia"
})
public class CompanionAnnotationProcessor extends AbstractProcessor {

  /** Creates a new CompanionAnnotationProcessor. */
  public CompanionAnnotationProcessor() {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /** Claims the companion annotations in the round; there is nothing to generate for them. */
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    return true;
  }
}
