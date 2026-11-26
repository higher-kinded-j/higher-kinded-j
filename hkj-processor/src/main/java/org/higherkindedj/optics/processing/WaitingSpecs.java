// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MapKey;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.TypeKey;

/**
 * The mapping and merge specs that wait for a later annotation-processing round.
 *
 * <p>A type another processor generates does not exist yet in the round that writes it. javac hands
 * the processor an error type in its place, and an error type passes every check classification
 * makes: {@code Types.isSameType} is true for it, and its element reads as a class. Classified in
 * that round, a valid spec is refused for a reason that is not true, or given an Impl built against
 * the placeholder, which stops compiling once the real type is written. So a spec whose
 * classification would read an unresolved type waits, and so does a spec naming the domain or wire
 * type of a mapping that waits, since resolving its nesting without that mapping could name another
 * spec or none. javac supplies a generated type in the round after the one that writes it, and each
 * round asks again.
 *
 * <p>A constant such a type declares is missing in that round too. Set as the value of a {@code
 * MapField} or {@code MapKey}, as {@code @MapField(to = Names.FULL)} does, it reaches the processor
 * as javac's mark for a value it could not resolve (see {@link ProcessorUtils#unresolvedMark}), and
 * reading the annotation's member through its proxy throws, which stops javac. So a mapping spec
 * also waits while one of the two, on a method of the spec or of an interface it extends, sets such
 * a mark; a merge spec's own methods carry neither, and a merge nesting a waiting mapping waits
 * with it. Every interface is read for this, whatever file the compiler says it came from: both
 * take a {@code String}, which a class file holds as a plain value, so a mark found is a source
 * one, even in an interface the compiler cannot place.
 *
 * <p>A spec is classified only once it holds no mark. Every member whose value the processor reads
 * through a proxy is declared on the spec or an interface it extends, each of which is read here,
 * and that is what keeps those reads safe: a read of another spec's member would need that spec
 * checked too.
 *
 * <p>Only a declaration in source makes a spec wait for a type. A type a later round can supply is
 * named from source; a dependency's class file naming a type missing from the classpath is a broken
 * dependency no round repairs. In Gradle's incremental build a type of this compilation is read
 * from a class file too, the generated types it names deleted, so a domain, wire or parent
 * interface that names one no longer holds a spec back there. A class file still names the types a
 * spec nests through, though, so its record components and permitted subtypes count towards the
 * specs a waiting mapping holds back. A spec still waiting when processing ends generates nothing
 * and reports nothing: everything it waits for is a source reference, which javac reports as {@code
 * cannot find symbol}. Which file a type was read from is the compiler's to say (see {@link
 * ProcessorUtils#compiledFromSource}); a type it cannot place is read as a class file. A spec's own
 * declaration is read whatever the compiler says, the spec Gradle's incremental build hands over by
 * name as well as one met in source, so the spec still waits for a type it names itself, and for a
 * waiting spec it nests through a record component or a permitted subtype. A generated type named
 * only inside a record, bean or mix-in the spec reads no longer holds it back there, and the spec
 * is classified against the placeholder.
 *
 * <p>The mapping specs a compilation has met are kept here for both processors ({@link
 * #meetMappings}): {@link MergeProcessor} is first invoked only in a round carrying a {@code
 * GenerateMerge}, and a merge still has to see the mapping specs of every round before it, whether
 * processed or waiting.
 */
final class WaitingSpecs {

  /**
   * The {@code @GenerateMapping} interfaces each compilation has met, in the order met. Keyed
   * weakly by the compilation's {@link Elements}, the one utility a build tool hands every
   * processor unwrapped, and holding keys only, so a finished compilation is never kept reachable.
   */
  private static final Map<Elements, Set<TypeKey>> MAPPING_SPECS = new WeakHashMap<>();

  /**
   * The annotations whose values the mapping processor reads through a proxy: each takes one {@code
   * String} and goes on a method. An annotation with members it comes to read that way belongs here
   * too, or a constant a later round writes stops javac there.
   */
  private static final Set<String> VALUED =
      Set.of(MapField.class.getCanonicalName(), MapKey.class.getCanonicalName());

  private WaitingSpecs() {}

  /**
   * A generated Impl, by the module and package it is written into and its simple name: the parts
   * an unresolved name has to agree with before it is taken for one.
   */
  private record ImplName(String module, String packageName, String simpleName) {

    static ImplName of(Elements elements, TypeElement spec) {
      var impl = MappingProcessor.implClassName(spec);
      return new ImplName(TypeKey.moduleOf(elements, spec), impl.packageName(), impl.simpleName());
    }
  }

  /**
   * The interfaces among a round's annotated elements, the only declarations a spec can be. One of
   * another kind is refused where it stands, having nothing to wait for.
   */
  static List<TypeKey> interfaces(Elements elements, Set<? extends Element> annotated) {
    return annotated.stream()
        .filter(element -> element.getKind() == ElementKind.INTERFACE)
        .map(element -> TypeKey.of(elements, element))
        .toList();
  }

  /**
   * Records the {@code @GenerateMapping} interfaces a round brings, and answers every one the
   * compilation has met, whichever processor met it first.
   */
  static List<TypeElement> meetMappings(Elements elements, Set<? extends Element> annotated) {
    synchronized (MAPPING_SPECS) {
      Set<TypeKey> met =
          MAPPING_SPECS.computeIfAbsent(elements, compilation -> new LinkedHashSet<>());
      met.addAll(interfaces(elements, annotated));
      return met.stream().map(key -> key.in(elements)).toList();
    }
  }

  /**
   * The specs that wait this round: those whose classification would read an unresolved type
   * declared in source, then, until none joins, those naming the domain or wire type of a mapping
   * spec that waits. Specs and the types they name are told apart by {@link TypeKey}, so a
   * same-named type in another module neither holds a spec back nor lets it through. A merge spec
   * is never nested, so only a mapping spec passes its waiting on. The Impl a spec is generated as
   * never counts as unresolved, since the processors write it: a spec naming one would otherwise
   * wait for itself. It is recognised by the module and package the name resolves to where it is
   * written, never by its simple name alone, so a same-named type another processor writes into
   * another package still waits.
   */
  static Set<TypeKey> among(
      Elements elements, List<TypeElement> mappingSpecs, List<TypeElement> mergeSpecs) {
    Set<ImplName> impls =
        Stream.concat(mappingSpecs.stream(), mergeSpecs.stream())
            .map(spec -> ImplName.of(elements, spec))
            .collect(Collectors.toSet());
    Map<TypeKey, Reach> reaches = new LinkedHashMap<>();
    Map<TypeKey, Set<TypeKey>> pairs = new LinkedHashMap<>();
    for (TypeElement spec : mappingSpecs) {
      Reach reach = new Reach(elements, impls);
      reach.declaration(spec);
      reach.values(spec);
      Set<TypeKey> pair = new LinkedHashSet<>();
      Stream.of(MappingProcessor.findMappingSpec(spec), MappingProcessor.findUpdateSpec(spec))
          .filter(Objects::nonNull)
          .flatMap(supertype -> supertype.getTypeArguments().stream())
          .forEach(
              side -> {
                reach.shape(side, spec);
                declaredType(side)
                    .map(declared -> TypeKey.of(elements, declared.asElement()))
                    .ifPresent(pair::add);
              });
      reaches.put(TypeKey.of(elements, spec), reach);
      pairs.put(TypeKey.of(elements, spec), pair);
    }
    for (TypeElement spec : mergeSpecs) {
      Reach reach = new Reach(elements, impls);
      reach.declaration(spec);
      for (ExecutableElement method : ElementFilter.methodsIn(spec.getEnclosedElements())) {
        reach.shape(method.getReturnType(), spec);
        method.getParameters().forEach(parameter -> reach.shape(parameter.asType(), spec));
      }
      reaches.put(TypeKey.of(elements, spec), reach);
    }
    Set<TypeKey> waiting =
        reaches.entrySet().stream()
            .filter(entry -> entry.getValue().unresolved)
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    List<TypeKey> joining;
    do {
      Set<TypeKey> waitedFor =
          waiting.stream()
              .flatMap(key -> pairs.getOrDefault(key, Set.of()).stream())
              .collect(Collectors.toSet());
      joining =
          reaches.entrySet().stream()
              .filter(entry -> !waiting.contains(entry.getKey()))
              .filter(entry -> !Collections.disjoint(entry.getValue().names, waitedFor))
              .map(Map.Entry::getKey)
              .toList();
      waiting.addAll(joining);
    } while (!joining.isEmpty());
    return Set.copyOf(waiting);
  }

  /** A mirror as a declared type, or empty: an error type is not one, whatever it implements. */
  private static Optional<DeclaredType> declaredType(TypeMirror mirror) {
    return mirror.getKind() == TypeKind.DECLARED
        ? Optional.of((DeclaredType) mirror)
        : Optional.empty();
  }

  /**
   * What one spec's classification reads, as far as a later round can still change it: whether any
   * of it is unresolved, and every type it names, which is how a spec nesting a waiting one is
   * found.
   */
  private static final class Reach {

    private final Elements elements;
    private final Set<ImplName> impls;
    private final Set<TypeKey> names = new HashSet<>();
    private final Set<TypeKey> opened = new HashSet<>();
    private final Set<TypeKey> valued = new HashSet<>();
    private boolean unresolved;

    Reach(Elements elements, Set<ImplName> impls) {
      this.elements = elements;
      this.impls = impls;
    }

    /**
     * A spec, or an interface it extends that is declared in source: its type parameters, its
     * method signatures and every supertype clause, the interfaces behind them included.
     */
    void declaration(TypeElement type) {
      type.getTypeParameters()
          .forEach(parameter -> parameter.getBounds().forEach(bound -> type(bound, type)));
      ElementFilter.methodsIn(type.getEnclosedElements()).forEach(this::signature);
      for (TypeMirror parent : type.getInterfaces()) {
        type(parent, type);
        declaredType(parent)
            .map(declared -> (TypeElement) declared.asElement())
            .filter(this::fromSource)
            .filter(element -> opened.add(key(element)))
            .ifPresent(this::declaration);
      }
    }

    /**
     * The {@code MapField} and {@code MapKey} on the methods of a mapping spec, or of an interface
     * it extends: unresolved where one sets a value javac could not resolve. Every interface the
     * type extends is read in turn, each once for this spec, from source or a class file alike.
     */
    void values(TypeElement type) {
      if (!valued.add(key(type))) {
        return;
      }
      unresolved |=
          ElementFilter.methodsIn(type.getEnclosedElements()).stream()
              .flatMap(method -> method.getAnnotationMirrors().stream())
              .filter(
                  mirror ->
                      VALUED.contains(
                          ((TypeElement) mirror.getAnnotationType().asElement())
                              .getQualifiedName()
                              .toString()))
              .flatMap(mirror -> mirror.getElementValues().values().stream())
              .anyMatch(ProcessorUtils::unresolvedMark);
      type.getInterfaces().stream()
          .flatMap(parent -> declaredType(parent).stream())
          .forEach(parent -> values((TypeElement) parent.asElement()));
    }

    /**
     * A type classification reads, written in the declaration {@code site}: as written, then opened
     * (see {@link #open}).
     */
    void shape(TypeMirror mirror, Element site) {
      type(mirror, site);
      open(mirror);
    }

    /**
     * Opens a declared type, and its type arguments, once each. Declared in source, its record
     * components, permitted subtypes, superclass and interfaces open in turn, since nesting,
     * flattening, dispatch and bean analysis read them, and so do the types its static methods
     * return, which is where a builder comes from; the member signatures of the type and of the
     * types nested in it are read as written. Read from a class file, its record components and
     * permitted subtypes open for the types they name and nothing else. Each type opens once, so a
     * recursive record ends.
     */
    private void open(TypeMirror mirror) {
      declaredType(mirror)
          .ifPresent(
              declared -> {
                declared.getTypeArguments().forEach(this::open);
                TypeElement element = (TypeElement) declared.asElement();
                if (!opened.add(key(element))) {
                  return;
                }
                if (fromSource(element)) {
                  element.getRecordComponents().forEach(c -> shape(c.asType(), element));
                  element.getPermittedSubclasses().forEach(subtype -> shape(subtype, element));
                  shape(element.getSuperclass(), element);
                  element.getInterfaces().forEach(parent -> shape(parent, element));
                  ElementFilter.methodsIn(element.getEnclosedElements()).stream()
                      .filter(method -> method.getModifiers().contains(Modifier.STATIC))
                      .forEach(method -> shape(method.getReturnType(), element));
                  members(element);
                } else {
                  element.getRecordComponents().forEach(component -> name(component.asType()));
                  element.getPermittedSubclasses().forEach(this::name);
                }
              });
    }

    /** A class-file type's names, which no unresolved part of it turns into waiting. */
    private void name(TypeMirror mirror) {
      declaredType(mirror)
          .ifPresent(
              declared -> {
                names.add(key(declared.asElement()));
                declared.getTypeArguments().forEach(this::name);
                open(declared);
              });
    }

    private void members(TypeElement type) {
      ElementFilter.methodsIn(type.getEnclosedElements()).forEach(this::signature);
      ElementFilter.constructorsIn(type.getEnclosedElements()).forEach(this::signature);
      ElementFilter.typesIn(type.getEnclosedElements()).forEach(this::members);
    }

    private void signature(ExecutableElement method) {
      Element site = method.getEnclosingElement();
      method
          .getTypeParameters()
          .forEach(parameter -> parameter.getBounds().forEach(bound -> type(bound, site)));
      type(method.getReturnType(), site);
      method.getParameters().forEach(parameter -> type(parameter.asType(), site));
      method.getThrownTypes().forEach(thrown -> type(thrown, site));
    }

    /**
     * One type as written in the declaration {@code site}: unresolved if any part of it is, its
     * enclosing type included, naming every declared type in it.
     */
    private void type(TypeMirror mirror, Element site) {
      switch (mirror.getKind()) {
        case ERROR -> unresolved |= !impls.contains(written(mirror, site));
        case DECLARED -> {
          DeclaredType declared = (DeclaredType) mirror;
          names.add(key(declared.asElement()));
          declared.getTypeArguments().forEach(argument -> type(argument, site));
          type(declared.getEnclosingType(), site);
        }
        case ARRAY -> type(((ArrayType) mirror).getComponentType(), site);
        case WILDCARD -> {
          WildcardType wildcard = (WildcardType) mirror;
          Stream.of(wildcard.getExtendsBound(), wildcard.getSuperBound())
              .filter(Objects::nonNull)
              .forEach(bound -> type(bound, site));
        }
        default -> {
          // A primitive, void or none resolves by construction, and a type variable's bounds are
          // read from its declaration.
        }
      }
    }

    /**
     * The Impl an unresolved type would be, as its site names it. javac leaves an unresolved simple
     * name unqualified, so it is read in the site's own package, where a spec's Impl is written; a
     * qualified name is read as written. A simple name imported from another package therefore
     * matches nothing, and its spec waits the one round until that Impl is written, rather than
     * being classified against the placeholder.
     */
    private ImplName written(TypeMirror unresolved, Element site) {
      String name =
          ((TypeElement) ((DeclaredType) unresolved).asElement()).getQualifiedName().toString();
      int dot = name.lastIndexOf('.');
      return new ImplName(
          TypeKey.moduleOf(elements, site),
          dot < 0
              ? elements.getPackageOf(site).getQualifiedName().toString()
              : name.substring(0, dot),
          name.substring(dot + 1));
    }

    private TypeKey key(Element type) {
      return TypeKey.of(elements, type);
    }

    private boolean fromSource(TypeElement type) {
      return ProcessorUtils.compiledFromSource(elements, type);
    }
  }
}
