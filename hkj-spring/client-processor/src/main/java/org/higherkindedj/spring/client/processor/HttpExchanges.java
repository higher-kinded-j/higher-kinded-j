// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Reads a client interface's type-level {@code @HttpExchange} as Spring reads it for an HTTP
 * interface. Spring proxies the generated native interface, which extends nothing, so whatever
 * Spring would have found on the client's bases has to be written onto it. This is javax.lang.model
 * reading alone.
 */
final class HttpExchanges {

  private static final String HTTP_EXCHANGE =
      "org.springframework.web.service.annotation.HttpExchange";

  private HttpExchanges() {
    // Utility class - prevent instantiation
  }

  /**
   * The type-level {@code @HttpExchange} annotations a client inherits: none when it declares one
   * of its own, which Spring then reads alone, and otherwise every one on the first super-interface
   * that carries any, walking them depth first, each in the order its {@code extends} clause names
   * them. Spring refuses more than one that differ, so copying them all leaves that refusal to
   * Spring, as for a plain HTTP interface.
   *
   * <p>An annotation written directly is looked for first, so a client that writes
   * {@code @HttpExchange} has no annotation type looked into, its own or a base's. Otherwise each
   * annotation is looked into, as Spring does, which is what lets two that differ on one base be
   * refused.
   */
  static List<? extends AnnotationMirror> inheritedBy(TypeElement client) {
    boolean declaresOne =
        client.getAnnotationMirrors().stream().anyMatch(HttpExchanges::isHttpExchange)
            || client.getAnnotationMirrors().stream().anyMatch(HttpExchanges::carriesHttpExchange);
    return declaresOne ? List.of() : inheritedFrom(client, new HashSet<>());
  }

  /**
   * The search below {@code type}. An interface met a second time is passed over, since nothing was
   * found there the first time.
   */
  private static List<? extends AnnotationMirror> inheritedFrom(
      TypeElement type, Set<TypeElement> visited) {
    for (TypeMirror parent : type.getInterfaces()) {
      if (parent.getKind() != TypeKind.DECLARED) {
        continue; // unresolved this round, so it carries nothing to read
      }
      TypeElement base = (TypeElement) ((DeclaredType) parent).asElement();
      if (!visited.add(base)) {
        continue;
      }
      List<? extends AnnotationMirror> own =
          base.getAnnotationMirrors().stream().filter(HttpExchanges::carriesHttpExchange).toList();
      List<? extends AnnotationMirror> found = own.isEmpty() ? inheritedFrom(base, visited) : own;
      if (!found.isEmpty()) {
        return found;
      }
    }
    return List.of();
  }

  private static boolean isHttpExchange(AnnotationMirror mirror) {
    return annotationOf(mirror).getQualifiedName().contentEquals(HTTP_EXCHANGE);
  }

  /**
   * Whether an annotation is {@code @HttpExchange} to Spring: the annotation itself, or one
   * composed from it at any depth. Each annotation is looked into with a set of its own, since one
   * shared across a type's annotations would pass over a second annotation that reaches a type the
   * first already did.
   */
  private static boolean carriesHttpExchange(AnnotationMirror mirror) {
    return carriesHttpExchange(annotationOf(mirror), new HashSet<>());
  }

  /**
   * Spring's merged-annotation search reads annotations by reflection, so it sees only those
   * retained at run time, at every level. Meta-annotations can be cyclic ({@code @Documented}
   * annotates itself), so each type is looked into once.
   */
  private static boolean carriesHttpExchange(TypeElement annotation, Set<TypeElement> seen) {
    if (annotation.getQualifiedName().contentEquals(HTTP_EXCHANGE)) {
      return true;
    }
    if (!seen.add(annotation) || !retainedAtRunTime(annotation)) {
      return false;
    }
    for (AnnotationMirror meta : annotation.getAnnotationMirrors()) {
      if (carriesHttpExchange(annotationOf(meta), seen)) {
        return true;
      }
    }
    return false;
  }

  /** Whether an annotation type is retained at run time; one with no {@code @Retention} is not. */
  private static boolean retainedAtRunTime(TypeElement annotation) {
    Retention retention = annotation.getAnnotation(Retention.class);
    return retention != null && retention.value() == RetentionPolicy.RUNTIME;
  }

  private static TypeElement annotationOf(AnnotationMirror mirror) {
    return (TypeElement) mirror.getAnnotationType().asElement();
  }
}
