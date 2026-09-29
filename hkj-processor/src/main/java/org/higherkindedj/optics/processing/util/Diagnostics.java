// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.tools.Diagnostic;

/**
 * The shared what/why/fix diagnostic format for HKJ annotation processors.
 *
 * <p>Every processor-reported problem should tell the user three things: <b>what</b> is wrong
 * (naming the offending element), <b>why</b> it is a problem (what the processor found or needs),
 * and how to <b>fix</b> it (the exact remedy, as an imperative sentence). The reference shape:
 *
 * <pre>
 * &#64;GenerateMapping: target field 'UserDto.fullName' has no source. Found on User:
 * [name, email, age]. Add a &#64;MapField(to="fullName") method to the MappingSpec, supply a
 * leaf-optic default method, or drop the field.
 * </pre>
 *
 * <p>Callers pass the three parts as complete sentences; {@link #format} joins them after the
 * annotation tag. New processors should use this from their first diagnostic; existing ones are
 * migrated as they are touched.
 */
public final class Diagnostics {

  private Diagnostics() {}

  /**
   * Reports an error in the what/why/fix format, attached to {@code element}.
   *
   * @param messager the processing-round messager; must not be null
   * @param element the offending element; must not be null
   * @param annotation the annotation tag, for example {@code "@GenerateFocus"}; must not be null
   * @param what one sentence naming what is wrong; must not be null
   * @param why one sentence of context: what was found or needed; must not be null
   * @param fix one imperative sentence prescribing the remedy; must not be null
   */
  public static void error(
      Messager messager, Element element, String annotation, String what, String why, String fix) {
    Objects.requireNonNull(messager, "messager must not be null");
    Objects.requireNonNull(element, "element must not be null");
    messager.printMessage(Diagnostic.Kind.ERROR, format(annotation, what, why, fix), element);
  }

  /**
   * Reports a warning in the what/why/fix format, attached to {@code element}.
   *
   * @param messager the processing-round messager; must not be null
   * @param element the offending element; must not be null
   * @param annotation the annotation tag; must not be null
   * @param what one sentence naming what is wrong; must not be null
   * @param why one sentence of context; must not be null
   * @param fix one imperative sentence prescribing the remedy; must not be null
   */
  public static void warning(
      Messager messager, Element element, String annotation, String what, String why, String fix) {
    Objects.requireNonNull(messager, "messager must not be null");
    Objects.requireNonNull(element, "element must not be null");
    messager.printMessage(Diagnostic.Kind.WARNING, format(annotation, what, why, fix), element);
  }

  /**
   * Whether {@code @SuppressWarnings(id)} sits on {@code element} or on a declaration enclosing it,
   * so a warning its reporter checks first can be kept quiet where it is meant, as {@code
   * hkj-checker} honours its check ids. {@code "all"} is not honoured, since it is the compiler's
   * own lint switch.
   *
   * @param element the element the warning is about; must not be null
   * @param id the warning's suppression id; must not be null
   * @return whether the warning is suppressed there
   */
  public static boolean suppressed(Element element, String id) {
    Objects.requireNonNull(element, "element must not be null");
    Objects.requireNonNull(id, "id must not be null");
    // Every declaration's enclosing chain ends at its package.
    for (Element at = element; at.getKind() != ElementKind.PACKAGE; at = at.getEnclosingElement()) {
      SuppressWarnings suppression = at.getAnnotation(SuppressWarnings.class);
      if (suppression != null && List.of(suppression.value()).contains(id)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Reports a note in the what/why/fix format, attached to {@code element}.
   *
   * <p>A note is for a gap the author should know about but may be unable to close where it is
   * reported — a generated method that is missing from a class that is otherwise sound. Unlike a
   * warning, a note does not fail a {@code -Werror} build, which matters because javac does not
   * apply {@code @SuppressWarnings} to a {@link Messager} warning: one is suppressible only where
   * its reporter asks {@link #suppressed} first.
   *
   * @param messager the processing-round messager; must not be null
   * @param element the element the note is about; must not be null
   * @param annotation the annotation tag; must not be null
   * @param what one sentence naming the gap; must not be null
   * @param why one sentence of context; must not be null
   * @param fix one imperative sentence prescribing the remedy; must not be null
   */
  public static void note(
      Messager messager, Element element, String annotation, String what, String why, String fix) {
    Objects.requireNonNull(messager, "messager must not be null");
    Objects.requireNonNull(element, "element must not be null");
    messager.printMessage(Diagnostic.Kind.NOTE, format(annotation, what, why, fix), element);
  }

  /**
   * Reports a message in the what/why/fix format located at an annotation on {@code element}, or at
   * one of the annotation's values, so the compiler points at what was written rather than at the
   * declaration.
   *
   * @param messager the processing-round messager; must not be null
   * @param kind the severity; must not be null
   * @param element the annotated element; must not be null
   * @param annotationMirror the annotation the message is about; must not be null
   * @param value the value to point at, or null to point at the annotation
   * @param annotation the annotation tag; must not be null
   * @param what one sentence naming what is wrong; must not be null
   * @param why one sentence of context; must not be null
   * @param fix one imperative sentence prescribing the remedy; must not be null
   */
  public static void reportAt(
      Messager messager,
      Diagnostic.Kind kind,
      Element element,
      AnnotationMirror annotationMirror,
      AnnotationValue value,
      String annotation,
      String what,
      String why,
      String fix) {
    Objects.requireNonNull(messager, "messager must not be null");
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(element, "element must not be null");
    Objects.requireNonNull(annotationMirror, "annotationMirror must not be null");
    messager.printMessage(
        kind, format(annotation, what, why, fix), element, annotationMirror, value);
  }

  /**
   * Reports a generated file javac could not place in a module, since more than one module being
   * compiled declares its package (see {@link ProcessorUtils#compiledModulesDeclaring}).
   *
   * @param messager the processing-round messager; must not be null
   * @param element the element the file is generated for; must not be null
   * @param annotation the annotation tag; must not be null
   * @param file what could not be written, for example {@code "the generated mapping for 'M'"}
   * @param packageName the file's package; must not be null
   * @param modules the modules declaring the package; must not be null
   * @param reason what the filer reported; must not be null
   */
  public static void sharedPackage(
      Messager messager,
      Element element,
      String annotation,
      String file,
      String packageName,
      List<String> modules,
      String reason) {
    error(
        messager,
        element,
        annotation,
        "could not write "
            + file
            + ": its package '"
            + packageName
            + "' is declared by more than one module being compiled ("
            + modules.stream().map(module -> "'" + module + "'").collect(Collectors.joining(", "))
            + ").",
        "javac writes a generated class into the module that declares its package, and cannot"
            + " choose between them. The filer reported: "
            + reason,
        "Declare the package in only one of those modules, or compile the modules separately.");
  }

  /**
   * Joins the three parts after the annotation tag: {@code "@Tag: what why fix"}.
   *
   * @param annotation the annotation tag; must not be null
   * @param what one sentence naming what is wrong; must not be null
   * @param why one sentence of context; must not be null
   * @param fix one imperative sentence prescribing the remedy; must not be null
   * @return the formatted message (non-null)
   */
  public static String format(String annotation, String what, String why, String fix) {
    Objects.requireNonNull(annotation, "annotation must not be null");
    Objects.requireNonNull(what, "what must not be null");
    Objects.requireNonNull(why, "why must not be null");
    Objects.requireNonNull(fix, "fix must not be null");
    return annotation
        + ": "
        + ensureSentence(what)
        + " "
        + ensureSentence(why)
        + " "
        + ensureSentence(fix);
  }

  /** Trims and guarantees sentence-ending punctuation, so sloppy callers still format cleanly. */
  private static String ensureSentence(String part) {
    String trimmed = part.trim();
    if (trimmed.isEmpty()) {
      return trimmed;
    }
    char last = trimmed.charAt(trimmed.length() - 1);
    return (last == '.' || last == '?' || last == '!') ? trimmed : trimmed + ".";
  }
}
