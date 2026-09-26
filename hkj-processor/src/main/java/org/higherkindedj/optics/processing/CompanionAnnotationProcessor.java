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
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Claims the hkj annotations no generating processor names: those a processor reads from the
 * element it generates for, such as {@code @MapField} on a mapping spec, those it writes onto the
 * code it generates, such as {@code @Generated}, the coverage marker written by hand, and
 * {@code @PathConfig}, which no processor reads.
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
 * <p>Where {@code @PathConfig} is written, it reports a note saying the annotation has no effect.
 * Every other annotation {@code hkj-annotations} declares is claimed by the processor that
 * generates for it.
 *
 * <p>This one generates nothing, so it is registered with Gradle as isolating: there is no output
 * for Gradle to track, and the types carrying these annotations, every generated class among them,
 * are not reprocessed on each incremental compile as an aggregating processor's would be. An
 * isolating processor may also claim a {@code SOURCE}-retained annotation such as
 * {@code @PathConfig} without costing a build incremental compilation, which an aggregating one may
 * not.
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
  "org.higherkindedj.hkt.effect.annotation.PathVia",
  // Read by no processor; its note says so.
  CompanionAnnotationProcessor.PATH_CONFIG
})
public class CompanionAnnotationProcessor extends AbstractProcessor {

  /** A name rather than {@code PathConfig.class}, whose deprecation for removal fails -Werror. */
  static final String PATH_CONFIG = "org.higherkindedj.hkt.effect.annotation.PathConfig";

  private static final String DEFAULT_PATH_SUFFIX = "Path";

  /** Creates a new CompanionAnnotationProcessor. */
  public CompanionAnnotationProcessor() {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   * Claims the companion annotations in the round, and notes each package {@code @PathConfig} is
   * written on; there is nothing to generate for them.
   */
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    annotations.stream()
        .filter(annotation -> annotation.getQualifiedName().contentEquals(PATH_CONFIG))
        .flatMap(
            annotation ->
                ElementFilter.packagesIn(roundEnv.getElementsAnnotatedWith(annotation)).stream())
        .forEach(this::notePathConfigHasNoEffect);
    return true;
  }

  /**
   * Reports that {@code @PathConfig} changes nothing generated, at the annotation. A note rather
   * than a warning: javac already warns of the removal, and a build that turns that off with {@code
   * -Xlint:-removal} could not turn off a processor's warning, which would fail it under {@code
   * -Werror}.
   */
  private void notePathConfigHasNoEffect(PackageElement pkg) {
    AnnotationMirror pathConfig = ProcessorUtils.findAnnotation(pkg, PATH_CONFIG);
    String suffix =
        ProcessorUtils.getAnnotationString(pathConfig, "pathSuffix", DEFAULT_PATH_SUFFIX);
    String message =
        Diagnostics.format(
            "@PathConfig",
            "it has no effect on package '" + pkg.getQualifiedName() + "'",
            "No processor reads it, so every Path generated in the package comes out as it would"
                + " without it, and it is deprecated for removal.",
            "Remove it; nothing generated changes." + suffixFix(suffix));
    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message, pkg, pathConfig);
  }

  /**
   * How to get the Path class names {@code pathSuffix} asked for, or nothing where it asked for the
   * default or for a suffix {@code @PathSource} cannot take: an empty one names the source type
   * itself, and one that is not part of an identifier names no class. An unresolved constant reads
   * back as {@code <error>}, which is not.
   */
  private String suffixFix(String suffix) {
    if (suffix.equals(DEFAULT_PATH_SUFFIX)
        || suffix.isEmpty()
        || !SourceVersion.isIdentifier("A" + suffix)) {
      return "";
    }
    String literal = processingEnv.getElementUtils().getConstantExpression(suffix);
    return " To name the package's Path classes with "
        + literal
        + " instead, which renames them, add suffix = "
        + literal
        + " to each @PathSource.";
  }
}
