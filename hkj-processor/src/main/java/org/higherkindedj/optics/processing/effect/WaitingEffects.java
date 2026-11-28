// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
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
 * which stops javac, and a class literal's reads as an error type, which tells a check nothing. A
 * type a declaration names reads as an error type too, named as the source wrote it: a bridge or an
 * Ops class written against it loses its import, and a supertype shows no members. So a type waits
 * while what its processor reads, on it or on what it names, is unresolved, and is read again in
 * the next round, where javac supplies what was written. Each processor says what it reads, reading
 * each type there through {@link #partsOf} and each declaration's supertypes through {@link
 * #supertypesOf}; a value read through a proxy is read only once its type is ready, which is what
 * keeps that read safe. Only a type a declaration in source writes makes a type wait (see {@link
 * #inSource}), as a class file names each of its own types in full; the one exception is an
 * unresolved supertype, which hides the members a bridge reads wherever it is named.
 *
 * <p>A class file holds a constant as its value, so a type read from one sets no mark. A class
 * literal it names that the classpath lacks reads as an error type, which no later round resolves,
 * so a processor that waits on that error type waits for good there.
 *
 * <p>javac can also hide a type a round has brought. Where a class file names a type that is
 * missing until a processor writes it, as in the build after a change in Gradle's incremental
 * build, javac's Filer looks the name up as the processor writes it. That lookup fails, and javac
 * answers every processor from the failed lookup for the rest of the compilation. Such a type reads
 * as an error type, and so does each type naming it. As an element it keeps its name, modifiers,
 * annotations, permitted subtypes and record components, and has no type parameters, supertypes or
 * other members. {@code Elements.getFileObjectOf} throws on it, so a processor must not ask which
 * file such a type came from. javac's own compile reads it as written. No later round shows it, so
 * nothing waits for it (see {@link #stale}). A processor reads what it keeps, leaves to javac's
 * compile a check it cannot make without the rest, and says why where it cannot generate without
 * it. A processor first called in a round after the one that brought such a type cannot tell it
 * from one never written, as javac calls a processor only once its annotation appears, so it waits
 * there as for a missing class.
 *
 * <p>Nothing is written in the last round, which javac also makes of the round after an error. A
 * type still waiting there generates nothing and reports nothing. What a declaration in source
 * waits for is a name it wrote, which javac reports as {@code cannot find symbol}.
 */
final class WaitingEffects {

  /**
   * Why javac hides a type a round has brought (see {@link #stale}), as a refusal that cannot
   * generate without what it hides says it.
   */
  static final String HIDDEN_BECAUSE =
      "A class file this compilation read, such as one Gradle did not recompile, names it, and"
          + " another processor writes it. javac looked the name up as the file was written, found"
          + " nothing, and keeps that answer for every processor until the compilation ends.";

  /** The annotation the processor generates or checks for. */
  private final Class<? extends Annotation> annotation;

  /** The types waiting for a later round, in the order met. */
  private final Set<TypeKey> waiting = new LinkedHashSet<>();

  /** The qualified names of the types the rounds this processor has seen brought as roots. */
  private final Set<String> brought = new HashSet<>();

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
    ElementFilter.typesIn(roundEnv.getRootElements())
        .forEach(type -> brought.add(type.getQualifiedName().toString()));
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
   * Whether javac hides a type from the processors though a round has brought it: an error type
   * whose outermost enclosing type, or itself where none encloses it, is a top-level type that also
   * reads as an error type and that a round this processor has seen brought as a root element. Such
   * a type keeps its name, modifiers, annotations, permitted subtypes and record components as an
   * element, and lacks its type parameters, supertypes and other members. A name javac could not
   * find in a type it can read, as {@code Holder.Typo}, belongs to that type, and is not hidden.
   *
   * @param type a type the processor reads
   * @return true where the type is an error type that waiting would never resolve, though javac has
   *     its source
   */
  boolean stale(TypeMirror type) {
    if (type.getKind() != TypeKind.ERROR) {
      return false;
    }
    Element outermost = outermost(((DeclaredType) type).asElement());
    return outermost.getEnclosingElement() instanceof PackageElement
        && outermost.asType().getKind() == TypeKind.ERROR
        && brought.contains(((TypeElement) outermost).getQualifiedName().toString());
  }

  /**
   * Whether a processor waits for a type it reads: an error type a later round may still resolve,
   * which is any but a stale one (see {@link #stale}).
   *
   * @param type a type the processor reads
   * @return true where the type is an error type javac does not hide
   */
  boolean waitsFor(TypeMirror type) {
    return type.getKind() == TypeKind.ERROR && !stale(type);
  }

  /**
   * Every type a type names as written: the type itself, the type of each type-use annotation on
   * it, then the parts of each type argument and enclosing type, of an array's component and of a
   * wildcard's bound. A type variable's bounds are read where it is declared, so they are not among
   * them.
   *
   * @param type a type a processor reads
   * @return the type and its parts, outermost first (non-null)
   */
  static Stream<TypeMirror> partsOf(TypeMirror type) {
    Stream<TypeMirror> inner =
        switch (type.getKind()) {
          // javac keeps the type arguments of a type it could not resolve.
          case DECLARED, ERROR ->
              Stream.concat(
                  Stream.of(((DeclaredType) type).getEnclosingType()),
                  ((DeclaredType) type).getTypeArguments().stream());
          case ARRAY -> Stream.of(((ArrayType) type).getComponentType());
          case WILDCARD ->
              Stream.of(
                      ((WildcardType) type).getExtendsBound(),
                      ((WildcardType) type).getSuperBound())
                  .filter(Objects::nonNull);
          default -> Stream.empty();
        };
    return Stream.concat(
        Stream.concat(Stream.of(type), annotationTypesOf(type)),
        inner.flatMap(WaitingEffects::partsOf));
  }

  /**
   * The types of the annotations on a type or a declaration. javac reads an annotation it could not
   * resolve, written where a declaration annotation may stand, as the declaration's, since without
   * its type it cannot tell whether it is a type-use one: {@code @Tag String text} on a component
   * puts {@code Tag} on the component until a round brings it, and on the component's type from
   * then on.
   *
   * @param construct a type a processor reads, or a declaration whose type it reads
   * @return the types of its annotations (non-null)
   */
  static Stream<TypeMirror> annotationTypesOf(AnnotatedConstruct construct) {
    return construct.getAnnotationMirrors().stream().map(AnnotationMirror::getAnnotationType);
  }

  /**
   * A supertype as a declaration's superclass or interface clause writes it.
   *
   * @param declaration the declaration whose clause it is
   * @param type the supertype, as the clause writes it
   */
  record Supertype(TypeElement declaration, TypeMirror type) {}

  /**
   * Every supertype a declaration names, as its superclass and interfaces write them, and those
   * each of them names in turn, each declaration read once. One that reads as an error type names
   * none: javac shows nothing of it beyond its name.
   *
   * @param type a declaration a processor reads the members or the hierarchy of
   * @return its supertypes, nearest first, each with the declaration writing it (non-null)
   */
  static List<Supertype> supertypesOf(TypeElement type) {
    List<Supertype> supertypes = new ArrayList<>();
    Set<Element> read = new HashSet<>(Set.of(type));
    Deque<TypeElement> pending = new ArrayDeque<>(List.of(type));
    while (!pending.isEmpty()) {
      TypeElement next = pending.removeFirst();
      for (TypeMirror supertype :
          Stream.concat(Stream.of(next.getSuperclass()), next.getInterfaces().stream()).toList()) {
        if (supertype.getKind() == TypeKind.NONE) {
          continue;
        }
        supertypes.add(new Supertype(next, supertype));
        if (supertype.getKind() == TypeKind.DECLARED) {
          // Cast, not a pattern: a declared type's element is a type.
          TypeElement declaration = (TypeElement) ((DeclaredType) supertype).asElement();
          if (read.add(declaration)) {
            pending.add(declaration);
          }
        }
      }
    }
    return List.copyOf(supertypes);
  }

  /**
   * Whether javac compiled a declaration from source, where what it writes may name a type a later
   * round brings. A class file was compiled against every type it names, so one it names that javac
   * cannot resolve is missing from the classpath, as an annotation a dependency declares
   * compile-only is, or hidden (see {@link #stale}): no round brings either, and javac reports
   * neither, as no source writes it. A declaration javac could not resolve, such as a permitted
   * subtype a later round brings, or one it hides, is read from no file a processor may ask about,
   * and is not taken for source.
   *
   * @param elements the compilation's element utilities
   * @param declaration the type declaring what a processor reads
   * @return true where the declaration is read from a source file
   */
  static boolean inSource(Elements elements, TypeElement declaration) {
    return declaration.asType().getKind() != TypeKind.ERROR
        && ProcessorUtils.compiledFromSource(elements, declaration);
  }

  /**
   * What to do about a type javac hides (see {@link #stale}), as a refusal that cannot generate
   * without what it hides says it. It names the outermost type, the one a processor writes.
   *
   * @param refused what the refusal refuses, as the fix names it, such as "it" or "the bridge"
   * @param hidden the hidden type
   * @return the fix, as a sentence (non-null)
   */
  static String hiddenFix(String refused, TypeElement hidden) {
    return "Build from clean, which compiles every type from source; each incremental build after"
        + " a change refuses "
        + refused
        + " again. To build incrementally, declare '"
        + outermost(hidden).getSimpleName()
        + "' in source rather than have a processor write it.";
  }

  /**
   * The outermost type enclosing an element, or the element itself where no type encloses it.
   * Walked by hand: javac's {@code Elements.getOutermostTypeElement} casts the owner of a name it
   * could not find, which is no class, to one.
   */
  private static Element outermost(Element element) {
    Element outermost = element;
    while (outermost.getEnclosingElement() instanceof TypeElement enclosing) {
      outermost = enclosing;
    }
    return outermost;
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
