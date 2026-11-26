// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateGetters;
import org.higherkindedj.optics.annotations.GenerateIsos;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateSetters;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.TypeKey;

/**
 * The elements one optics processor holds for a later annotation-processing round, shared by the
 * processors behind {@code @GenerateLenses}, {@code @GenerateFocus}, {@code @GenerateIsos},
 * {@code @GeneratePrisms}, {@code @GenerateGetters}, {@code @GenerateSetters},
 * {@code @GenerateFolds} and {@code @GenerateTraversals}.
 *
 * <p>A type another processor generates does not exist yet in the round that writes it. javac hands
 * the processor an error type in its place, named as the source wrote it, so a type the source
 * imports reads as its simple name alone. A companion generated against it writes that name with no
 * import, and stops compiling once the real type is written, or misreads the shape it stands for: a
 * fold takes it for something to iterate. An annotation naming such a type, or a constant such a
 * type declares, is read no better: a type-use annotation is left off the generated code, and an
 * attribute of the processor's own annotation cannot be read at all. So an element whose companion
 * would read anything javac could not resolve waits, and is looked at again in the next round,
 * where javac supplies what was written.
 *
 * <p>What is read is the element's annotations, then, for a type, its declaration, and for a
 * method, its return type. A declaration is the bounds of its type parameters, the types of its
 * record components with the annotations on them, its permitted subtypes, its superclass and its
 * interfaces. Each type is read as written: every type argument, array component, wildcard bound
 * and enclosing type, with the type-use annotations on each. Each type declared in source that the
 * element reaches so, however far, is read the same way, with its own annotations: a component is
 * classified through its supertypes, and a navigator restates the components of the record it steps
 * into and reads that record's settings. Each processor reads each such declaration once a round,
 * for every element that reaches it, and one whose reach is found to name nothing is not read
 * through again that round. An annotation counts where its type did not resolve, since it may be a
 * type-use annotation the generated code would copy. A value it sets that javac could not resolve
 * counts where the annotation is copied, as a type-use one is, or read, as HKJ's own are; another
 * library's annotation on a declaration is neither, so what it sets is not read.
 *
 * <p>Only what a declaration in source names makes an element wait. One Gradle's incremental build
 * hands over by name is read from the class file the last build wrote, and javac does not resolve
 * again, in a later round, a type that class file names, so waiting for it would never end; such a
 * type is named in full, and the companion is written importing it, as from source. Such an element
 * still waits for a declaration in source it reaches, which a later round can still resolve, as a
 * navigator into a record recompiled beside it must. A type read from a class file is read through
 * only where it carries an optics annotation, as each one Gradle hands over does, so a chain of
 * them reaches the declaration in source behind it; a dependency's other types name nothing a later
 * round brings.
 *
 * <p>A missing type named as a companion is named does not count, and nor does a type nested in
 * one, such as a navigator. The companions are those of each element carrying an optics annotation
 * that this processor has met in any round so far, and the classes each {@code @ImportOptics}
 * importer met so far generates. Such a companion is written once its element is generated, so
 * waiting for it would never end, and two elements naming each other's would each wait for the
 * other, across two generators or a generator and an importer. A simple name is taken for a
 * companion's whatever package it was imported from, as javac keeps no import for a type it could
 * not resolve, so a type of another package named like a companion is taken for it too, and written
 * as the source wrote it. A qualified name is a companion's only where it names the package the
 * companion is written into, so one naming another package waits for that type. Where that package
 * cannot be read yet, as where a target package is a constant a later round brings, any part of a
 * name matches the companion's simple name. {@link WaitingImporters} matches its own companions by
 * the whole name, simple or qualified.
 *
 * <p>javac keeps, for the whole compilation, its failure to find a type a class file names before a
 * later round writes it, so in Gradle's incremental build a declaration in source naming that type
 * never sees it resolve, though the round brought it. A missing type named like one a round has
 * brought as a root element, or nested in one, does not count either: it is named in full, and the
 * companion written importing it compiles once javac reads what the round brought.
 *
 * <p>Nothing is written in the last round. There an element still waiting is reported, with what
 * never resolved, beside javac's own error at each name its source wrote, unless an error ended the
 * rounds early: javac makes the round after one with an error the last, and that error is the one
 * to correct. The mapping specs stay silent there instead (see {@link WaitingSpecs}), as javac's
 * error names all a spec waits for. An element here may wait for a record it only reaches, whose
 * file holds the name, so the report names the element left without a companion, in full where it
 * was read from a class file, which gives it no position, and each name read in another declaration
 * is reported as in it.
 */
final class WaitingOptics {

  /** The companion each optics annotation generates, by the suffix its name takes. */
  private static final Map<Class<? extends Annotation>, String> COMPANIONS =
      Map.of(
          GenerateLenses.class, "Lenses",
          GenerateFocus.class, "Focus",
          GenerateIsos.class, "Isos",
          GeneratePrisms.class, "Prisms",
          GenerateGetters.class, "Getters",
          GenerateSetters.class, "Setters",
          GenerateFolds.class, "Folds",
          GenerateTraversals.class, "Traversals");

  /** The same suffixes, by the qualified name of the annotation, as a mirror names it. */
  private static final Map<String, String> COMPANION_SUFFIXES =
      COMPANIONS.entrySet().stream()
          .collect(
              Collectors.toUnmodifiableMap(
                  entry -> entry.getKey().getCanonicalName(), Map.Entry::getValue));

  /** The annotation an importer carries, as a mirror names it. */
  private static final String IMPORT_OPTICS = ImportOptics.class.getCanonicalName();

  /**
   * The prefix of HKJ's own annotations, whose values the generators read: the optics annotations,
   * and those read beside them, such as {@code @TraverseField}.
   */
  private static final String HKJ_ANNOTATIONS = "org.higherkindedj.";

  /** How many names a report lists before it counts the rest. */
  private static final int REPORTED = 10;

  private final Class<? extends Annotation> annotation;
  private final String tag;

  /** The elements waiting for a later round, in the order met. */
  private final Set<Key> waiting = new LinkedHashSet<>();

  /**
   * The companions of every element carrying an optics annotation this processor has met, and the
   * classes every importer it has met generates, which no element waits for: each by its simple
   * name, and by its qualified name where the package it is written into can be read.
   */
  private final Set<String> companions = new HashSet<>();

  /** The simple names of those companions whose package could not be read. */
  private final Set<String> unplaced = new HashSet<>();

  /**
   * The qualified names of the types the rounds this processor has seen brought as their root
   * elements, and of the types nested in them. One javac still cannot resolve is not waited for.
   */
  private final Set<String> brought = new HashSet<>();

  /**
   * Creates the waiting elements of one processor.
   *
   * @param annotation the annotation the processor generates for
   */
  WaitingOptics(Class<? extends Annotation> annotation) {
    this.annotation = annotation;
    this.tag = "@" + annotation.getSimpleName();
  }

  /** An element as the rounds hold it, looked up afresh in each. */
  private sealed interface Key permits TypeAt, MethodAt {

    /** The element this key names, as the current round presents it. */
    Stream<Element> in(Elements elements);
  }

  /** A type, by its key. */
  private record TypeAt(TypeKey type) implements Key {

    @Override
    public Stream<Element> in(Elements elements) {
      return Stream.of(type.in(elements));
    }
  }

  /**
   * A method, by its type's key, its own name, and where it stands among the methods of that name
   * its type declares, which tells it apart from an overload.
   */
  private record MethodAt(TypeKey type, String name, int overload) implements Key {

    @Override
    public Stream<Element> in(Elements elements) {
      return named(type.in(elements), name).skip(overload).limit(1).map(Element.class::cast);
    }
  }

  private static Stream<ExecutableElement> named(Element type, String name) {
    return ElementFilter.methodsIn(type.getEnclosedElements()).stream()
        .filter(method -> method.getSimpleName().contentEquals(name));
  }

  private static Key keyOf(Elements elements, Element element) {
    if (element instanceof ExecutableElement method) {
      Element type = method.getEnclosingElement();
      String name = method.getSimpleName().toString();
      return new MethodAt(
          TypeKey.of(elements, type),
          name,
          named(type, name).takeWhile(candidate -> !candidate.equals(method)).toList().size());
    }
    return new TypeAt(TypeKey.of(elements, element));
  }

  /**
   * One round's elements, those waiting from an earlier round, looked up afresh, then those the
   * round brings, with what the round has read of each declaration they reach.
   */
  final class Round {

    private final ProcessingEnvironment processingEnv;
    private final List<Element> elements;
    private final Reader reader;

    private Round(ProcessingEnvironment processingEnv, List<Element> elements, Reader reader) {
      this.processingEnv = processingEnv;
      this.elements = elements;
      this.reader = reader;
    }

    /**
     * The elements this round looks at, in the order met: none in the last round.
     *
     * @return the elements (non-null, unmodifiable)
     */
    List<Element> elements() {
      return elements;
    }

    /**
     * Whether an element of this round can be generated now: true where it names nothing javac
     * could not resolve, as a type, an annotation or a value, in a declaration in source it reads
     * or reaches. One that does is held for the next round.
     *
     * @param element one of {@link #elements}
     * @return true where the element is generated in this round
     */
    boolean ready(Element element) {
      if (!reader.waits(element)) {
        return true;
      }
      waiting.add(keyOf(processingEnv.getElementUtils(), element));
      return false;
    }
  }

  /**
   * This round's elements. In the last round there are none, and each element still waiting is
   * reported instead.
   *
   * @param processingEnv the processor's environment
   * @param roundEnv the round
   * @return the round
   */
  Round round(ProcessingEnvironment processingEnv, RoundEnvironment roundEnv) {
    Elements elements = processingEnv.getElementUtils();
    List<Element> waited = waiting.stream().flatMap(key -> key.in(elements)).toList();
    waiting.clear();
    // What another optics annotation or an importer asks for counts too, and so does what an
    // element met in an earlier round asks for, so two elements naming each other's companions
    // across two processors are each generated, however long either waits. A waiting element is
    // asked again, as the package its companion is written into may have resolved since.
    Stream.concat(
            Stream.concat(COMPANIONS.keySet().stream(), Stream.of(ImportOptics.class))
                .flatMap(annotated -> roundEnv.getElementsAnnotatedWith(annotated).stream()),
            waited.stream())
        .forEach(element -> noteCompanions(processingEnv, element));
    ElementFilter.typesIn(roundEnv.getRootElements()).forEach(this::bring);
    Reader reader = new Reader(elements, new Exempt(companions, unplaced, brought));
    if (roundEnv.processingOver()) {
      if (!roundEnv.errorRaised()) {
        waited.forEach(element -> report(processingEnv, element, reader));
      }
      return new Round(processingEnv, List.of(), reader);
    }
    List<Element> met =
        Stream.concat(waited.stream(), roundEnv.getElementsAnnotatedWith(annotation).stream())
            .distinct()
            .toList();
    return new Round(processingEnv, met, reader);
  }

  /**
   * Notes a type a round brings, and each type nested in it. javac keeps, for the whole
   * compilation, its failure to find a type a class file names before a later round writes it, so a
   * declaration in source naming that type never sees it resolve, though the round brought it: the
   * type is named in full there, and the companion written importing it compiles once javac reads
   * what the round brought.
   */
  private void bring(TypeElement type) {
    brought.add(type.getQualifiedName().toString());
    ElementFilter.typesIn(type.getEnclosedElements()).forEach(this::bring);
  }

  private void report(ProcessingEnvironment processingEnv, Element element, Reader reader) {
    // Still waiting in the last round, so something it names still has not resolved.
    List<String> awaited = reader.awaited(element);
    String listed =
        String.join(", ", awaited.subList(0, Math.min(REPORTED, awaited.size())))
            + (awaited.size() > REPORTED ? ", and " + (awaited.size() - REPORTED) + " more" : "");
    Diagnostics.error(
        processingEnv.getMessager(),
        element,
        tag,
        "generated nothing for '"
            + nameOf(processingEnv.getElementUtils(), element)
            + "', as no round of this compilation resolved what it names: "
            + listed
            + ".",
        "Generated code written before they resolve could misread them, so the generator waits"
            + " for the round that brings them, and none did.",
        "Correct each name or make it accessible, or add the dependency or the processor that"
            + " writes it.");
  }

  /**
   * An element as a report names it: by its simple name in source, where the report points at it,
   * and in full where it was read from a class file, which gives it no position.
   */
  private static String nameOf(Elements elements, Element element) {
    if (!ProcessorUtils.readFromClassFile(elements, element)) {
      return element.getSimpleName().toString();
    }
    return element instanceof ExecutableElement method
        ? ((TypeElement) method.getEnclosingElement()).getQualifiedName()
            + "."
            + method.getSimpleName()
        : ((TypeElement) element).getQualifiedName().toString();
  }

  /**
   * Notes the classes an element is generated as: for each optics annotation it carries, the
   * companion that annotation asks for, and for an importer, each class it generates. Each is
   * written into the package the annotation's {@code targetPackage} names, or the element's own.
   */
  private void noteCompanions(ProcessingEnvironment processingEnv, Element element) {
    Element owner =
        element instanceof ExecutableElement method ? method.getEnclosingElement() : element;
    String ownPackage =
        processingEnv.getElementUtils().getPackageOf(owner).getQualifiedName().toString();
    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
      String annotationName = qualifiedName(mirror);
      Stream<String> generated =
          annotationName.equals(IMPORT_OPTICS)
              ? ImportOpticsProcessor.generatedNames(processingEnv.getTypeUtils(), element, mirror)
              : Stream.ofNullable(COMPANION_SUFFIXES.get(annotationName))
                  .map(suffix -> owner.getSimpleName() + suffix);
      Optional<String> target = targetPackage(mirror, ownPackage);
      generated.forEach(
          simpleName -> {
            companions.add(simpleName);
            target.ifPresentOrElse(
                packageName ->
                    companions.add(
                        packageName.isEmpty() ? simpleName : packageName + "." + simpleName),
                () -> unplaced.add(simpleName));
          });
    }
  }

  /**
   * The package an annotation's {@code targetPackage} names, or {@code ownPackage} where it names
   * none. It is empty where the value cannot be read as a package: javac's mark for a constant it
   * could not resolve, or a value of another type, from a class file written against another
   * version of the annotation.
   */
  private static Optional<String> targetPackage(AnnotationMirror mirror, String ownPackage) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        mirror.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals("targetPackage")) {
        AnnotationValue value = entry.getValue();
        return value.getValue() instanceof String written && !ProcessorUtils.unresolvedMark(value)
            ? Optional.of(written.isEmpty() ? ownPackage : written)
            : Optional.empty();
      }
    }
    return Optional.of(ownPackage);
  }

  private static String qualifiedName(AnnotationMirror mirror) {
    // An annotation type's element is a TypeElement, one that did not resolve included.
    return ((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().toString();
  }

  /**
   * What no element waits for in a round: the companions met so far, each by its simple name and,
   * where its package can be read, its qualified name; the simple names of those whose package
   * cannot; and the types the rounds have brought.
   */
  private record Exempt(Set<String> companions, Set<String> unplaced, Set<String> brought) {

    Exempt {
      companions = Set.copyOf(companions);
      unplaced = Set.copyOf(unplaced);
      brought = Set.copyOf(brought);
    }

    /**
     * Whether a name javac could not resolve, as written, is a companion's or that of a type nested
     * in one, such as a navigator: whether the name, or a part of it ending at a dot, is a
     * companion's simple or qualified name, or any part of it the simple name of one whose package
     * cannot be read.
     */
    boolean companion(String name) {
      return IntStream.rangeClosed(0, name.length())
              .filter(end -> end == name.length() || name.charAt(end) == '.')
              .mapToObj(end -> name.substring(0, end))
              .anyMatch(companions::contains)
          || Stream.of(name.split("\\.")).anyMatch(unplaced::contains);
    }
  }

  /** What one declaration names that javac could not resolve, and the declarations it reaches. */
  private record Found(List<String> names, List<Element> reached) {

    Found {
      names = List.copyOf(names);
      reached = List.copyOf(reached);
    }
  }

  /**
   * One round's reading of the elements it looks at, for one processor: each declaration an element
   * reaches is read once, for every element that reaches it, and one whose reach names nothing is
   * not read through again.
   */
  private static final class Reader {

    private final Elements elements;
    private final Exempt exempt;
    private final Map<Element, Found> reached = new HashMap<>();

    /** Declarations whose whole reach was read and names nothing. */
    private final Set<Element> clean = new HashSet<>();

    /** Declarations that name, or reach one that names, something javac could not resolve. */
    private final Set<Element> dirty = new HashSet<>();

    Reader(Elements elements, Exempt exempt) {
      this.elements = elements;
      this.exempt = exempt;
    }

    /**
     * Whether an element names, or reaches a declaration that names, something javac could not
     * resolve. Each declaration on the way to the first such name is noted as reaching it, so a
     * later element that reaches one of them stops there; where nothing is found, each declaration
     * read is noted as naming nothing, and is not read through again.
     */
    boolean waits(Element element) {
      Map<Element, Element> from = new HashMap<>();
      from.put(element, element);
      Deque<Element> pending = new ArrayDeque<>(List.of(element));
      while (!pending.isEmpty()) {
        Element next = pending.removeFirst();
        Found found = next.equals(element) ? Scan.of(elements, exempt, element, true) : read(next);
        if (dirty.contains(next) || !found.names().isEmpty()) {
          // Each declaration back along the way to the element reaches the name too.
          for (Element at = next; !at.equals(element); at = from.get(at)) {
            dirty.add(at);
          }
          dirty.add(element);
          return true;
        }
        for (Element reachable : found.reached()) {
          if (!clean.contains(reachable) && from.putIfAbsent(reachable, next) == null) {
            pending.add(reachable);
          }
        }
      }
      clean.addAll(from.keySet());
      return false;
    }

    /**
     * What an element names that javac could not resolve, each once: its own declaration first,
     * then each it reaches, nearest first, with where each was read.
     */
    List<String> awaited(Element element) {
      Found own = Scan.of(elements, exempt, element, true);
      Set<String> awaited = new LinkedHashSet<>(own.names());
      Set<Element> seen = new HashSet<>(Set.of(element));
      Deque<Element> pending = new ArrayDeque<>(own.reached());
      while (!pending.isEmpty()) {
        Element next = pending.removeFirst();
        if (seen.add(next)) {
          Found found = read(next);
          awaited.addAll(found.names());
          pending.addAll(found.reached());
        }
      }
      return List.copyOf(awaited);
    }

    /** What a declaration an element reaches names, read once a round. */
    private Found read(Element declaration) {
      return reached.computeIfAbsent(
          declaration, reachable -> Scan.of(elements, exempt, reachable, false));
    }
  }

  /**
   * The reading of one declaration, {@code site}: its annotations, then a type's declaration or a
   * method's return type, noting each declaration it reaches rather than reading it. What a site
   * read from a class file names does not count. Where {@code own}, the site is the element the
   * reading is for, and what it names needs no saying where.
   */
  private static final class Scan {

    private final Elements elements;
    private final Exempt exempt;
    private final Element site;
    private final boolean own;
    private final boolean counts;
    private final Set<String> names = new LinkedHashSet<>();
    private final Set<Element> next = new LinkedHashSet<>();

    private Scan(Elements elements, Exempt exempt, Element site, boolean own) {
      this.elements = elements;
      this.exempt = exempt;
      this.site = site;
      this.own = own;
      this.counts = !ProcessorUtils.readFromClassFile(elements, site);
    }

    /** What {@code site} names that javac could not resolve, and the declarations it reaches. */
    static Found of(Elements elements, Exempt exempt, Element site, boolean own) {
      Scan scan = new Scan(elements, exempt, site, own);
      scan.annotations(site);
      // The optics annotations sit on a type or a method, and what is reached is a type.
      if (site instanceof ExecutableElement method) {
        scan.written(method.getReturnType(), site);
      } else {
        scan.declaration((TypeElement) site);
      }
      return new Found(List.copyOf(scan.names), List.copyOf(scan.next));
    }

    /**
     * A type's declaration: its type parameters and their bounds, its record components with their
     * types, its permitted subtypes, its superclass and its interfaces.
     */
    private void declaration(TypeElement type) {
      type.getTypeParameters()
          .forEach(
              parameter -> {
                annotations(parameter);
                parameter.getBounds().forEach(bound -> written(bound, parameter));
              });
      type.getRecordComponents()
          .forEach(
              component -> {
                annotations(component);
                written(component.asType(), component);
              });
      type.getPermittedSubclasses().forEach(subtype -> written(subtype, type));
      written(type.getSuperclass(), type);
      type.getInterfaces().forEach(parent -> written(parent, type));
    }

    /**
     * A type as {@code member} of the site writes it: unresolved itself, or through any part of it
     * or any type-use annotation on it. A declared type in source is reached, and so is one read
     * from a class file that carries an optics annotation.
     */
    private void written(TypeMirror type, Element member) {
      type.getAnnotationMirrors().forEach(mirror -> annotation(mirror, member, true));
      switch (type.getKind()) {
        case ERROR -> {
          String name = ((DeclaredType) type).asElement().toString();
          if (!exempt.companion(name) && !exempt.brought().contains(name)) {
            name(name + in());
          }
          // javac keeps the type arguments of a type it could not resolve.
          ((DeclaredType) type).getTypeArguments().forEach(part -> written(part, member));
        }
        case DECLARED -> {
          DeclaredType declared = (DeclaredType) type;
          declared.getTypeArguments().forEach(part -> written(part, member));
          written(declared.getEnclosingType(), member);
          Element element = declared.asElement();
          if (ProcessorUtils.compiledFromSource(elements, element)
              || ProcessorUtils.readFromClassFile(elements, element) && carriesOptics(element)) {
            next.add(element);
          }
        }
        case ARRAY -> written(((ArrayType) type).getComponentType(), member);
        case WILDCARD -> {
          WildcardType wildcard = (WildcardType) type;
          Stream.of(wildcard.getExtendsBound(), wildcard.getSuperBound())
              .filter(Objects::nonNull)
              .forEach(bound -> written(bound, member));
        }
        default -> {
          // A primitive, void or none resolves by construction, and a type variable's bounds are
          // read where it is declared.
        }
      }
    }

    private boolean carriesOptics(Element type) {
      return type.getAnnotationMirrors().stream()
          .anyMatch(mirror -> COMPANION_SUFFIXES.containsKey(qualifiedName(mirror)));
    }

    /** The annotations on {@code member}, a declaration in the site or the site itself. */
    private void annotations(Element member) {
      member
          .getAnnotationMirrors()
          .forEach(
              mirror ->
                  annotation(mirror, member, qualifiedName(mirror).startsWith(HKJ_ANNOTATIONS)));
    }

    /**
     * An annotation whose own type did not resolve, or, where its values are {@code read}, one that
     * sets a value javac could not resolve. Such a value is named by the attribute it sets and
     * where it was set, since javac hands it over as a mark that does not say what was written.
     */
    private void annotation(AnnotationMirror mirror, Element member, boolean read) {
      DeclaredType type = mirror.getAnnotationType();
      if (type.getKind() == TypeKind.ERROR) {
        String name = type.asElement().toString();
        if (!exempt.brought().contains(name)) {
          name(name + in());
        }
      } else if (read) {
        mirror
            .getElementValues()
            .forEach(
                (attribute, value) -> {
                  if (unresolved(value)) {
                    name(
                        "a value of @"
                            + type.asElement().getSimpleName()
                            + "'s "
                            + attribute.getSimpleName()
                            + "()"
                            + on(member));
                  }
                });
      }
    }

    /**
     * Whether an annotation value is javac's mark for one it could not resolve, or holds one: as an
     * element of an array, or as a value an annotation nested in it sets.
     */
    private boolean unresolved(AnnotationValue value) {
      return switch (value.getValue()) {
        case List<?> values ->
            values.stream().map(AnnotationValue.class::cast).anyMatch(this::unresolved);
        case AnnotationMirror nested ->
            nested.getElementValues().values().stream().anyMatch(this::unresolved);
        default -> ProcessorUtils.unresolvedMark(value);
      };
    }

    /** Notes a name javac could not resolve, where the site is one whose names count. */
    private void name(String unresolved) {
      if (counts) {
        names.add(unresolved);
      }
    }

    /** Where a name read in the site was read, unless the site is the element itself. */
    private String in() {
      return own ? "" : " in " + site.getSimpleName();
    }

    /**
     * Where a value {@code member} carries was set: on the member, then in the site, each unless it
     * is the element itself.
     */
    private String on(Element member) {
      return member.equals(site)
          ? (own ? "" : " on " + site.getSimpleName())
          : " on " + member.getSimpleName() + in();
    }
  }
}
