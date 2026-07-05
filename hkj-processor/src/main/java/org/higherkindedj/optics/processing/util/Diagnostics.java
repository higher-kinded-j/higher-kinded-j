// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import java.util.Objects;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
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
