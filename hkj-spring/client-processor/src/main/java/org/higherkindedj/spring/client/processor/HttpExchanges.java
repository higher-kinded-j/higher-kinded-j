// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Reads a client interface's type-level {@code @HttpExchange} as Spring reads it for an HTTP
 * interface. Spring proxies the generated native interface, which extends nothing, so whatever
 * Spring would have found on the client's bases has to be written onto it. This is javax.lang.model
 * reading, apart from one question it cannot answer, which the caller does: whether Spring could
 * read an annotation type javac could not resolve.
 */
final class HttpExchanges {

  private static final String HTTP_EXCHANGE =
      "org.springframework.web.service.annotation.HttpExchange";

  private static final None NONE = new None();

  private HttpExchanges() {
    // Utility class - prevent instantiation
  }

  /** What the search for the type-level {@code @HttpExchange} a client inherits finds. */
  sealed interface Inherited permits None, Found, Unreadable {}

  /** Nothing to inherit: the client declares its own, or no base carries one. */
  record None() implements Inherited {}

  /**
   * The annotations Spring would read on {@code base}, the first interface the search finds
   * carrying any, in the order the base declares them.
   */
  record Found(TypeElement base, List<AnnotationMirror> annotations) implements Inherited {

    Found {
      annotations = List.copyOf(annotations);
    }
  }

  /**
   * A base that carries no annotation the processor can see is {@code @HttpExchange}, but carries
   * {@code annotation}, which leads to {@code missing}, a type javac could not resolve: the
   * annotation's own type, or one it is composed from. Spring, with the type on its classpath, may
   * read an {@code @HttpExchange} through it and stop there, so the search cannot tell what Spring
   * reads. {@code writer} is the type whose annotations name {@code missing}: {@code base}, or an
   * annotation type on the way.
   */
  record Unreadable(
      TypeElement base, AnnotationMirror annotation, DeclaredType missing, TypeElement writer)
      implements Inherited {}

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
   *
   * <p>A base that carries none the processor can see, but carries an annotation it cannot look
   * into, ends the search as {@link Unreadable}. A base that carries one it can see is read as it
   * is, as Spring reads that one whatever else the base carries. The client's own annotations are
   * all copied, so one it cannot look into is copied as it stands, beside a base's: should Spring
   * read an {@code @HttpExchange} through it, Spring refuses the two at startup, as it refuses any
   * two that differ.
   *
   * @param seenBySpring whether Spring could read an annotation type javac could not resolve, from
   *     the type whose annotations name it: one retained only for the compiler cannot be, and is
   *     passed over
   */
  static Inherited inheritedBy(
      TypeElement client, BiPredicate<TypeElement, DeclaredType> seenBySpring) {
    if (client.getAnnotationMirrors().stream().anyMatch(HttpExchanges::isHttpExchange)) {
      return NONE;
    }
    Search search = new Search(seenBySpring);
    return search.readOn(client) instanceof Found ? NONE : search.below(client, new HashSet<>());
  }

  /** One search: what Spring could read of an annotation type javac could not resolve. */
  private record Search(BiPredicate<TypeElement, DeclaredType> seenBySpring) {

    /**
     * The search below {@code type}. An interface met a second time is passed over, since nothing
     * was found there the first time.
     */
    Inherited below(TypeElement type, Set<TypeElement> visited) {
      for (TypeMirror parent : type.getInterfaces()) {
        if (parent.getKind() != TypeKind.DECLARED) {
          continue; // unresolved this round, so it carries nothing to read
        }
        TypeElement base = (TypeElement) ((DeclaredType) parent).asElement();
        if (!visited.add(base)) {
          continue;
        }
        Inherited own = readOn(base);
        Inherited found = own instanceof None ? below(base, visited) : own;
        if (!(found instanceof None)) {
          return found;
        }
      }
      return NONE;
    }

    /** What Spring would read on one interface, as {@link #inheritedBy} describes it. */
    Inherited readOn(TypeElement type) {
      List<AnnotationMirror> carrying = new ArrayList<>();
      Inherited unreadable = NONE;
      for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
        switch (lookInto(mirror, type, new HashSet<>())) {
          case Plain.CARRIES -> carrying.add(mirror);
          case Plain.NOTHING -> {}
          case Unknown(DeclaredType missing, TypeElement writer) -> {
            if (unreadable instanceof None) {
              unreadable = new Unreadable(type, mirror, missing, writer);
            }
          }
        }
      }
      return carrying.isEmpty() ? unreadable : new Found(type, carrying);
    }

    /**
     * Whether an annotation is {@code @HttpExchange} to Spring: the annotation itself, or one
     * composed from it at any depth. Spring's merged-annotation search reads annotations by
     * reflection, so it sees only those retained at run time, at every level. Meta-annotations can
     * be cyclic ({@code @Documented} annotates itself), so each type is looked into once, with a
     * set for each annotation looked into, since one shared across a type's annotations would pass
     * over a second annotation that reaches a type the first already did. A path the processor can
     * see leads to {@code @HttpExchange} settles it, whatever another path leads to.
     */
    private Finding lookInto(AnnotationMirror mirror, TypeElement writer, Set<TypeElement> seen) {
      DeclaredType type = mirror.getAnnotationType();
      if (type.getKind() == TypeKind.ERROR) {
        return seenBySpring.test(writer, type) ? new Unknown(type, writer) : Plain.NOTHING;
      }
      TypeElement annotation = annotationOf(mirror);
      if (annotation.getQualifiedName().contentEquals(HTTP_EXCHANGE)) {
        return Plain.CARRIES;
      }
      if (!seen.add(annotation) || !retainedAtRunTime(annotation)) {
        return Plain.NOTHING;
      }
      Finding found = Plain.NOTHING;
      for (AnnotationMirror meta : annotation.getAnnotationMirrors()) {
        Finding finding = lookInto(meta, annotation, seen);
        if (finding == Plain.CARRIES) {
          return Plain.CARRIES;
        }
        if (found == Plain.NOTHING) {
          found = finding;
        }
      }
      return found;
    }
  }

  private static boolean isHttpExchange(AnnotationMirror mirror) {
    return annotationOf(mirror).getQualifiedName().contentEquals(HTTP_EXCHANGE);
  }

  /** What looking into an annotation, and those it is composed from, finds. */
  private sealed interface Finding permits Plain, Unknown {}

  /** The annotation is {@code @HttpExchange} to Spring, or it plainly is not. */
  private enum Plain implements Finding {
    CARRIES,
    NOTHING
  }

  /**
   * The annotation leads to {@code missing}, a type javac could not resolve, whose retention and
   * meta-annotations cannot be read, and to nothing the processor can see is {@code @HttpExchange}.
   * {@code writer} is the element whose annotations name it.
   */
  private record Unknown(DeclaredType missing, TypeElement writer) implements Finding {}

  /**
   * Whether an annotation type is retained at run time, where Spring reads it by reflection; one
   * with no {@code @Retention} is not.
   */
  static boolean retainedAtRunTime(TypeElement annotation) {
    Retention retention = annotation.getAnnotation(Retention.class);
    return retention != null && retention.value() == RetentionPolicy.RUNTIME;
  }

  private static TypeElement annotationOf(AnnotationMirror mirror) {
    return (TypeElement) mirror.getAnnotationType().asElement();
  }
}
