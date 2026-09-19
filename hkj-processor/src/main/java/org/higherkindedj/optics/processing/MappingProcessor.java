// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import com.palantir.javapoet.TypeVariableName;
import com.palantir.javapoet.WildcardTypeName;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.annotations.ArityCeilings;
import org.higherkindedj.optics.annotations.Flatten;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MapKey;
import org.higherkindedj.optics.annotations.OptionalBridge;
import org.higherkindedj.optics.annotations.Unmapped;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Annotation processor for {@code @GenerateMapping}: the bidirectional record↔DTO mapper.
 *
 * <p>For an interface {@code UserMapping extends MappingSpec<User, UserDto>} it generates a
 * same-package {@code UserMappingImpl} with a total {@code build(User) : UserDto} and an
 * accumulating {@code parse(UserDto) : Validated<NonEmptyList<FieldError>, User>} assembled with
 * {@code Validated.fields()}, so every parse failure is located by component name. Components match
 * by identical name and type — unless an explicit leaf overrides the copy (so a {@code
 * ValidatedPrism<X, X>} can validate or normalise a same-typed component); differing components
 * resolve through a validated leaf (a spec {@code default} method named after the component
 * returning {@code ValidatedPrism<Wire, Domain>}), through another spec in this compilation or, via
 * the classpath index ({@code MappingIndexes}), in a dependency (nesting — a full mapping's impl
 * exposes {@code asValidatedPrism()}, and a one-directional bean mapping's impl the half it has, so
 * a whole mapping plugs in wherever its direction is used), or lift through {@code List}/{@code
 * Optional} containers of either. {@code Map} components lift their values the same way; keys are
 * identity-only and must match exactly on both sides, and each entry's parse failures are located
 * by its key. {@code @MapField} declares renames. A wire component with no domain counterpart can
 * be a derived field: a spec {@code default} method named after the wire component returning {@code
 * Getter<Domain, WireComponentType>}. {@code build} fills it with the getter applied to the whole
 * domain value; {@code parse} ignores it (the data is derivable), and a spec with any derived field
 * never emits {@code asIso()}. A wire with fewer components maps as a lossy projection: {@code
 * build} plus a lawful {@code asLens()} write-back when every projected read is total, or a
 * validated {@code patch(domain, wire)} write-back when any component maps through a leaf, a nested
 * spec, a container lifting either of those, or a bridge, or reads a bean's reference property; an
 * identity container copies verbatim, so on a record wire it keeps the lens; no {@code parse}
 * either way (truthful types). Sealed interface pairs dispatch {@code build}/{@code parse} over
 * their permitted subtype pairs, each delegating to its own spec.
 *
 * <p>One null doctrine covers both wire shapes: every reference-typed {@code parse} read is
 * null-guarded into a located {@code FieldError} — an unset bean property is null, and a JSON
 * binder leaves a missing record component null just the same — so a component-level null is a
 * located, accumulated invalid, never an exception. The doctrine reaches inside containers too,
 * identity-copied ones included: a null <em>element</em> or map <em>value</em> locates by its index
 * or key ({@code emails.1: must not be null}). What stays the caller-contract {@code
 * requireNonNull}: a null wire itself, and a null map <em>key</em> (a structurally broken map, not
 * a wrong value). What stays bean-only is the <em>absence</em> contract: only a bean property is
 * legitimately unset, so only bean guards cost the Iso tier — {@code asIso()} is truthful for an
 * all-primitive bean, while a lossless record mapping keeps it with the parse-iso coherence law
 * scoped to wires whose reference components are non-null.
 *
 * <p>The wire may be a bean-shaped class instead of a record ({@link WireShape}): {@code build}
 * fills it through setters or a builder and {@code parse} reads it through getters; a domain {@code
 * Optional<T>} bridges to a nullable bean property {@code T}. A bean projection with a reference
 * property maps as the validated {@code patch}, since that property can read null; an all-primitive
 * one keeps the {@code asLens()} projection.
 *
 * <p>A bean crossed one way only maps that way. A bean with getters and no way to be written emits
 * {@code parse} and {@code asValidatedParse()} and no {@code build}; one that can be written but
 * declares no getter emits {@code build} and {@code asValidatedBuild()} and no {@code parse}. The
 * bean's shape decides, and a note says which way it maps and why. Such a mapping nests wherever
 * only its direction is used: a parse-only spec in a parse-only mapping, an {@code UpdateSpec} or a
 * merge fill, and a build-only spec in a build-only mapping.
 *
 * <p>The same bridge reaches a <em>record</em> wire by opt-in ({@link OptionalBridge}), never
 * implicitly: a record component is null-is-an-error by default and that stays the default, so a
 * domain {@code Optional<T>} against a nullable record component {@code T} bridges only where the
 * spec marks the component — on a bare abstract marker when the element copies or nests through the
 * element pair's own spec, or on the component's {@code default} leaf when a leaf converts it.
 * Either way the correspondence is the bean bridge's, so the two wire shapes share one emission and
 * one law.
 *
 * <p>A spec extending {@code UpdateSpec<Domain, Wire>} ({@link
 * org.higherkindedj.optics.annotations.UpdateSpec}) opts into the opposite null contract: a null
 * bean property means <em>absent — leave unchanged</em> rather than invalid. Such a spec emits only
 * {@code updateFrom(Wire) : Edits.Accumulated<Domain>}, folding the present (non-null) properties
 * into an {@code Update} via {@code Edits.accumulate} — no {@code build}, {@code parse}, or {@code
 * as*} tier. A primitive wire property (which can never be absent) is rejected with a diagnostic,
 * and an {@code UpdateSpec} never registers for nesting (it has no {@code parse}). The two clauses
 * are exclusive: a spec declaring both is refused at the declaration, since one Impl carries one
 * tier and the sparse one would win.
 *
 * <p>A spec method that collides with a member the Impl emits for the classified tier is rejected
 * with a diagnostic at the spec: a colliding {@code default} would otherwise be silently overridden
 * by the generated method, or — with a different return type — fail javac inside the generated
 * file. Overloads with a different erased signature, and static or private spec methods (never
 * inherited by the Impl), stay legal. The private static {@code hkj$ifPresent} guard sits in the
 * {@code $} namespace JLS 3.8 reserves for generated code, so no ordinary spec method can collide
 * with it or capture its call sites, and the sweep never reserves it.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateMapping")
@SupportedOptions(MappingIndexes.OPTION)
public class MappingProcessor extends AbstractProcessor {

  private static final String TAG = "@GenerateMapping";
  private static final String MAPPING_SPEC = "org.higherkindedj.optics.annotations.MappingSpec";
  private static final String UPDATE_SPEC = "org.higherkindedj.optics.annotations.UpdateSpec";
  private static final String VALIDATED_PRISM = "org.higherkindedj.optics.validated.ValidatedPrism";
  private static final String GETTER = "org.higherkindedj.optics.Getter";

  /**
   * The containers whose ELEMENTS lift, in the order they are probed. Both emit the same text —
   * {@code ValidatedPrism}'s bulk forms are overloaded on the container — so the pair's own type
   * chooses, and the one {@code Kind.ELEMENTS} covers them both.
   */
  private static final List<String> ELEMENT_CONTAINERS = List.of("java.util.List", "java.util.Set");

  /** The containers lifting takes, each of which a pair must declare exactly to lift. */
  private static final List<String> LIFTABLE_CONTAINERS =
      List.of("java.util.List", "java.util.Set", "java.util.Optional", "java.util.Map");

  private static final ClassName VALIDATED_PRISM_TYPE =
      ClassName.get("org.higherkindedj.optics.validated", "ValidatedPrism");
  private static final ClassName VALIDATED_PARSE_TYPE =
      ClassName.get("org.higherkindedj.optics.validated", "ValidatedParse");
  private static final ClassName VALIDATED_BUILD_TYPE =
      ClassName.get("org.higherkindedj.optics.validated", "ValidatedBuild");
  private static final ClassName EDITS = ClassName.get("org.higherkindedj.optics.edit", "Edits");
  private static final ClassName EDIT = ClassName.get("org.higherkindedj.optics.edit", "Edit");
  private static final ClassName ACCUMULATED =
      ClassName.get("org.higherkindedj.optics.edit", "Edits", "Accumulated");
  private static final ClassName SETTER = ClassName.get("org.higherkindedj.optics", "Setter");
  private static final ClassName VALIDATED =
      ClassName.get("org.higherkindedj.hkt.validated", "Validated");
  private static final ClassName FIELD_ERROR =
      ClassName.get("org.higherkindedj.hkt.validated", "FieldError");
  private static final ClassName NEL =
      ClassName.get("org.higherkindedj.hkt.nonemptylist", "NonEmptyList");
  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");
  private static final ClassName OBJECTS = ClassName.get("java.util", "Objects");

  /** The interfaces met but not processed yet: arriving this round, or waiting for a later one. */
  private final Set<WaitingSpecs.TypeKey> unprocessed = new LinkedHashSet<>();

  /** Creates a new MappingProcessor. */
  public MappingProcessor() {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   * Processes the specs that can be classified this round. A spec naming a type another processor
   * has not written yet, or nesting a spec that does, waits for the round that brings it (see
   * {@link WaitingSpecs}), and a declaration refused for its kind is refused where it stands.
   */
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Elements elements = processingEnv.getElementUtils();
    Set<? extends Element> annotated = roundEnv.getElementsAnnotatedWith(GenerateMapping.class);
    annotated.stream()
        .filter(element -> element.getKind() != ElementKind.INTERFACE)
        .forEach(element -> processSpec(element, List.of()));
    unprocessed.addAll(WaitingSpecs.interfaces(elements, annotated));
    List<TypeElement> specs = WaitingSpecs.meetMappings(elements, annotated);
    // Nothing is written in the last round, which javac also makes of the round after a refusal: a
    // spec still waiting then generates nothing.
    if (roundEnv.processingOver() || unprocessed.isEmpty()) {
      return true;
    }
    Set<WaitingSpecs.TypeKey> waiting = WaitingSpecs.among(elements, specs, List.of());
    List<RegisteredSpec> registry =
        scanRegistry(processingEnv, specs, unprocessed.iterator().next().in(elements), waiting);
    for (WaitingSpecs.TypeKey spec : List.copyOf(unprocessed)) {
      if (!waiting.contains(spec)) {
        unprocessed.remove(spec);
        processSpec(spec.in(elements), registry);
      }
    }
    return true;
  }

  /**
   * Registers the compilation's specs that are not waiting for a later round, then the classpath
   * index's specs compiled into dependencies. Shared with {@link MergeProcessor}, whose nested
   * fills resolve against the same specs, as sites that only parse. A record domain may pair with a
   * record wire or a bean-shaped wire; each spec registers with the {@link Surface} its Impl
   * carries.
   *
   * <p>A classpath spec is read from its class file by the same rules as a spec in source; the
   * index only names it (see {@code MappingIndexes}). An entry naming a type compiled here is an
   * earlier round's or the previous build's, and passed over: a spec of this compilation registers
   * from source, or waits. A named module reads no index, having written none. An entry whose spec
   * has lost its Impl registers as unusable, so that a use site needing the pair is told why (a
   * diagnostic at scan time would fire whether or not anything needs the pair, twice when both
   * processors scan, and as an unsuppressable warning).
   *
   * @param specs every {@code @GenerateMapping} interface this compilation has met
   * @param compiled any spec of this compilation, whose module is the one being compiled
   * @param waiting the specs waiting for a later round, which do not register
   */
  static List<RegisteredSpec> scanRegistry(
      ProcessingEnvironment env,
      List<TypeElement> specs,
      Element compiled,
      Set<WaitingSpecs.TypeKey> waiting) {
    List<RegisteredSpec> registry = new ArrayList<>();
    BeanPropertyAnalyser beanAnalyser = new BeanPropertyAnalyser(env);
    Elements elements = env.getElementUtils();
    for (TypeElement spec : specs) {
      if (!waiting.contains(WaitingSpecs.TypeKey.of(elements, spec))) {
        register(env, beanAnalyser, spec, Origin.THIS_COMPILATION, registry);
      }
    }
    if (!(MappingIndexes.indexUse(env, compiled) instanceof MappingIndexes.IndexUse.Usable)) {
      return List.copyOf(registry);
    }
    for (TypeElement spec : MappingIndexes.classpathSpecs(elements)) {
      if (MappingIndexes.compiledHere(elements, spec)) {
        continue;
      }
      Origin origin =
          elements.getTypeElement(implClassName(spec).canonicalName()) != null
              ? Origin.CLASSPATH
              : Origin.CLASSPATH_MISSING_IMPL;
      register(env, beanAnalyser, spec, origin, registry);
    }
    return List.copyOf(registry);
  }

  /** Registers one spec if it is a mappable pair; a malformed one is left for the diagnostics. */
  private static void register(
      ProcessingEnvironment env,
      BeanPropertyAnalyser beanAnalyser,
      TypeElement spec,
      Origin origin,
      List<RegisteredSpec> registry) {
    // A generic spec registers with its declared mirrors (Page<T>, PageDto<TDto>); use sites
    // resolve it by unification, an element-mapped one composing its of(...) factory from the
    // element prisms resolved at the use site.
    DeclaredType specSuper = findMappingSpec(spec);
    if (specSuper == null || specSuper.getTypeArguments().size() != 2) {
      return;
    }
    // The registry offers the tier the Impl actually carries. A spec declaring an UpdateSpec
    // supertype too belongs to the sparse tier, which emits no parse, so it registers unusable
    // however sound its MappingSpec clause looks: offering it for nesting would generate a call
    // into an Impl that carries only updateFrom. It registers rather than vanishing so that a
    // failed lookup can name it, which is the whole of the diagnostic across modules — a spec in
    // a dependency is never dispatched here, so nothing else reports it.
    boolean bothTiers = findUpdateSpec(spec) != null;
    TypeMirror domainArg = specSuper.getTypeArguments().get(0);
    TypeMirror wireArg = specSuper.getTypeArguments().get(1);
    TypeElement domainRecord = asRecord(domainArg);
    TypeElement wireRecord = asRecord(wireArg);
    TypeElement wireBean = wireRecord == null ? asBean(wireArg) : null;
    boolean recordPair = domainRecord != null && wireRecord != null;
    boolean beanPair = domainRecord != null && wireBean != null;
    boolean sealedPair = asSealed(domainArg) != null && asSealed(wireArg) != null;
    if (!recordPair && !beanPair && !sealedPair) {
      return;
    }
    // Every mappable pair registers with the surface its Impl carries, projections included, so a
    // failed lookup can name them; which sites a surface serves is the registration's to say. A
    // classpath spec whose Impl is missing registers for the hint only: nothing can delegate to it.
    Surface surface =
        bothTiers
            ? Surface.BOTH_TIERS
            : sealedPair
                ? Surface.FULL
                : pairSurface(
                    env,
                    beanAnalyser,
                    spec,
                    domainRecord,
                    (DeclaredType) domainArg,
                    wireRecord,
                    wireBean);
    registry.add(
        new RegisteredSpec(domainArg, wireArg, implClassName(spec), spec, surface, origin));
  }

  /**
   * The surface a record-domain spec's Impl carries, as the registry needs it before validation has
   * run. A one-directional bean carries its one direction whatever its width, since a mapping with
   * no reverse has nothing to lose by dropping a component. Any other pair is full when the wire
   * has a component for every domain slot (derived wire fields do not count against the wire, since
   * parse ignores them) and a projection otherwise. A bean the analysis refuses reads as having no
   * property, so it registers as a projection, which nothing nests, and its own spec says why.
   */
  private static Surface pairSurface(
      ProcessingEnvironment env,
      BeanPropertyAnalyser beanAnalyser,
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      TypeElement wireRecord,
      TypeElement wireBean) {
    Set<String> wireNames;
    if (wireRecord != null) {
      wireNames =
          wireRecord.getRecordComponents().stream()
              .map(c -> c.getSimpleName().toString())
              .collect(Collectors.toCollection(LinkedHashSet::new));
    } else {
      Optional<WireShape.BeanShape> bean = beanAnalyser.surface(spec, wireBean);
      WireShape.Direction direction =
          bean.map(WireShape::direction).orElse(WireShape.Direction.BIDIRECTIONAL);
      if (direction != WireShape.Direction.BIDIRECTIONAL) {
        return direction == WireShape.Direction.PARSE_ONLY
            ? Surface.PARSE_ONLY
            : Surface.BUILD_ONLY;
      }
      wireNames = new LinkedHashSet<>(bean.map(WireShape::componentNames).orElse(List.of()));
    }
    return domainSlots(env, spec, domain, domainDeclared)
            == wireNames.size() - derivedCandidateCount(env, spec, wireNames)
        ? Surface.FULL
        : Surface.PROJECTION;
  }

  /**
   * How many wire components the domain calls for, as the registry's parse-capability arithmetic
   * needs it before validation has run: one per component, except that a component a
   * {@code @Flatten} marker names calls for one per component of its record. Tolerant by design,
   * since a spec this round has not been validated yet: a marker on a non-record component counts
   * one, and validation reports it. Read from the class file for a classpath spec, which is why the
   * marker is retained there.
   */
  private static int domainSlots(
      ProcessingEnvironment env,
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared) {
    Set<String> flattenedNames =
        specMembers(env.getElementUtils(), spec).stream()
            .filter(method -> method.getAnnotation(Flatten.class) != null)
            .map(method -> method.getSimpleName().toString())
            .collect(Collectors.toSet());
    boolean generic = !domain.getTypeParameters().isEmpty();
    int slots = 0;
    for (RecordComponentElement component : domain.getRecordComponents()) {
      TypeElement record = null;
      if (flattenedNames.contains(component.getSimpleName().toString())) {
        record =
            asRecord(
                generic
                    ? env.getTypeUtils().asMemberOf(domainDeclared, component)
                    : component.asType());
      }
      slots += record == null ? 1 : record.getRecordComponents().size();
    }
    return slots;
  }

  /**
   * Where a registered spec came from. A spec in this compilation shadows a classpath spec for the
   * same pair, so adding a dependency never changes a resolution that already worked; two classpath
   * specs for one pair stay ambiguous. A classpath spec whose Impl is missing is never a candidate,
   * only a hint.
   */
  enum Origin {
    THIS_COMPILATION,
    CLASSPATH,
    CLASSPATH_MISSING_IMPL
  }

  /**
   * The surface a registered spec's Impl carries, which decides the use sites it can serve. A full
   * mapping serves every site. A one-directional bean mapping serves the sites that use only its
   * direction, exposing itself as the half it has. A projection serves none, its write-back being
   * no whole mapping to nest. Nor does a spec extending both {@code MappingSpec} and {@code
   * UpdateSpec}: it belongs to the sparse tier, so its Impl carries {@code updateFrom} alone,
   * whatever its wire, and {@link RegisteredSpec#unusable} explains it in its own words.
   */
  enum Surface {
    FULL("asValidatedPrism", ""),
    PARSE_ONLY("asValidatedParse", "is parse-only (no build)"),
    BUILD_ONLY("asValidatedBuild", "is build-only (no parse)"),
    PROJECTION("", "is a projection (no parse)"),
    BOTH_TIERS("", "");

    private final String accessor;
    private final String shortfall;

    Surface(String accessor, String shortfall) {
      this.accessor = accessor;
      this.shortfall = shortfall;
    }

    /** Whether a site using {@code need} can call a mapping with this surface. */
    private boolean serves(WireShape.Direction need) {
      return switch (this) {
        case FULL -> true;
        case PARSE_ONLY -> need == WireShape.Direction.PARSE_ONLY;
        case BUILD_ONLY -> need == WireShape.Direction.BUILD_ONLY;
        case PROJECTION, BOTH_TIERS -> false;
      };
    }

    /**
     * What this surface lacks for a site using {@code need}, for the hint that names it. A
     * projection has a {@code build}, so where only a build is needed it lacks the whole-mapping
     * surface a nesting site calls, not a parse.
     */
    private String shortfall(WireShape.Direction need) {
      return this == PROJECTION && need == WireShape.Direction.BUILD_ONLY
          ? "is a projection, whose build is not offered for nesting"
          : shortfall;
    }
  }

  /**
   * A valid spec of this compilation or named by the classpath index; nested components resolve
   * against the ones serving their site ({@link #serves}). Compare registrations by {@code spec()}:
   * the record's own equality also covers the two mirrors, whose {@code equals} is identity and
   * says nothing about the types.
   */
  record RegisteredSpec(
      TypeMirror domain,
      TypeMirror wire,
      ClassName impl,
      TypeElement spec,
      Surface surface,
      Origin origin) {

    boolean local() {
      return origin == Origin.THIS_COMPILATION;
    }

    /**
     * Whether this spec can serve a site using {@code need}: its surface carries that direction,
     * and its Impl is there to call.
     */
    boolean serves(WireShape.Direction need) {
      return origin != Origin.CLASSPATH_MISSING_IMPL && surface.serves(need);
    }

    /**
     * The expression a nesting site calls on this spec's non-generic Impl: the surface it exposes.
     * Asked only of a spec that {@link #serves} the site, so the surface has an accessor.
     */
    CodeBlock nestingPrism() {
      return CodeBlock.of("$T.INSTANCE.$L()", impl, surface.accessor);
    }

    /**
     * The spec as a diagnostic names it: a spec in this compilation by its simple name, a classpath
     * spec by its qualified name and provenance, so that two same-named specs from two dependencies
     * read apart.
     */
    String describe() {
      return local() ? spec.getSimpleName().toString() : spec.getQualifiedName() + " (classpath)";
    }

    /**
     * Why a registered spec cannot serve a use site, for the hint a failed lookup carries: {@code
     * maps} and {@code purpose} frame the sentence ("'X' maps this pair but is parse-only (no
     * build), so it cannot be nested in a mapping that builds and parses"), and the surface says
     * what it lacks for a site using {@code need}. A classpath spec whose Impl is missing, and one
     * declaring both tiers, each explain themselves whatever the site. Asked only of a spec that
     * does not serve the site, so a full surface reaches the sentence only when its Impl is
     * missing.
     */
    String unusable(String maps, String purpose, WireShape.Direction need) {
      if (surface == Surface.BOTH_TIERS) {
        return " '"
            + describe()
            + "' maps this pair but extends both MappingSpec and UpdateSpec, so it belongs to the"
            + " sparse tier and has no parse to nest: split it into one spec per tier, sharing"
            + " what they have in common through a mix-in both extend.";
      }
      if (origin == Origin.CLASSPATH_MISSING_IMPL) {
        return " '"
            + describe()
            + "' maps this pair, but its generated '"
            + impl.canonicalName()
            + "' is missing from the classpath: the index entry was written by hkj-processor, so"
            + " the Impl was generated and then lost (a partial build output, or a jar that dropped"
            + " it); rebuild that dependency from clean.";
      }
      return " '"
          + describe()
          + "' "
          + maps
          + " but "
          + surface.shortfall(need)
          + ", so it cannot "
          + purpose
          + ".";
    }
  }

  /**
   * The candidates a use site resolves among: the matching specs in this compilation when there are
   * any, else the matching classpath specs. {@code shadowed} holds the classpath specs a local one
   * displaced, for the note that names them; it is non-empty only when {@code chosen} is.
   */
  record Candidates(List<RegisteredSpec> chosen, List<RegisteredSpec> shadowed) {

    Candidates {
      chosen = List.copyOf(chosen);
      shadowed = List.copyOf(shadowed);
    }

    static Candidates nearest(List<RegisteredSpec> matching) {
      List<RegisteredSpec> local = matching.stream().filter(RegisteredSpec::local).toList();
      if (local.isEmpty()) {
        return new Candidates(matching, List.of());
      }
      return new Candidates(local, matching.stream().filter(r -> !r.local()).toList());
    }

    /** The chosen specs as an ambiguity diagnostic lists them. */
    List<String> names() {
      return chosen.stream().map(RegisteredSpec::describe).toList();
    }

    /**
     * Whether every candidate is a dependency's, so that "remove the duplicate" is no remedy and a
     * spec declared in this compilation, which shadows them all, is.
     */
    boolean allClasspath() {
      return chosen.stream().noneMatch(RegisteredSpec::local);
    }
  }

  /**
   * Where a use site may declare the spec it lacks, completing a fix sentence: in this compilation
   * or in a dependency compiled with the processor, unless the index is out of use here, in which
   * case the sentence says why. Shared with {@link MergeProcessor}.
   */
  static String declarationSites(ProcessingEnvironment env, Element inRound) {
    return switch (MappingIndexes.indexUse(env, inRound)) {
      case MappingIndexes.IndexUse.Usable _ ->
          " here or in a dependency compiled with hkj-processor on its processor path";
      case MappingIndexes.IndexUse.NamedModule _ ->
          " in this module (a named module reads no classpath index, so a spec in a dependency is"
              + " not consulted; not supported yet)";
      case MappingIndexes.IndexUse.Off _ ->
          " here (the classpath index is off for this compilation: "
              + MappingIndexes.OPTION
              + "=false)";
      case MappingIndexes.IndexUse.OwnedBy owned ->
          " here (the index package belongs to module '"
              + owned.module().getQualifiedName()
              + "' on the module path, so no dependency's spec is consulted; put spec-carrying"
              + " dependencies on the classpath)";
    };
  }

  /**
   * Notes, once per use site, that a spec in this compilation was preferred to classpath specs for
   * the same pair. Shared with {@link MergeProcessor}. A note, not a warning: the choice is the
   * rule, and the author may well intend it.
   */
  static void noteShadowed(
      ProcessingEnvironment env,
      Element at,
      String tag,
      String site,
      String fix,
      Candidates candidates) {
    if (candidates.shadowed().isEmpty()) {
      return;
    }
    Diagnostics.note(
        env.getMessager(),
        at,
        tag,
        site
            + " resolves through '"
            + candidates.chosen().getFirst().describe()
            + "' in this compilation, not through "
            + candidates.shadowed().stream().map(RegisteredSpec::describe).toList()
            + ".",
        "A spec in the compilation takes precedence over a classpath spec mapping the same pair, so"
            + " a dependency never changes a resolution that already worked.",
        fix);
  }

  static ClassName implClassName(TypeElement spec) {
    ClassName specName = ClassName.get(spec);
    // Nested specs join their enclosing simple names (the OuterInnerAssembly convention),
    // so the generated class is always top-level and self-references resolve.
    return ClassName.get(specName.packageName(), String.join("", specName.simpleNames()) + "Impl");
  }

  /**
   * The spec's vocabulary members: its own declared methods plus everything inherited from mix-in
   * interfaces, with Java's own precedence — an override hides its parents, and javac itself
   * rejects genuinely conflicting parents before the processor runs. Interface statics and privates
   * are not inherited, and {@code Object}'s members are filtered by kind.
   */
  private static List<ExecutableElement> specMembers(Elements elements, TypeElement spec) {
    return ElementFilter.methodsIn(elements.getAllMembers(spec)).stream()
        .filter(method -> method.getEnclosingElement().getKind() == ElementKind.INTERFACE)
        .toList();
  }

  private List<ExecutableElement> specMembers(TypeElement spec) {
    return specMembers(processingEnv.getElementUtils(), spec);
  }

  /**
   * A bare {@code @OptionalBridge} marker: the abstract placement, named after the domain component
   * whose {@code Optional} bridges without a leaf (its element copies, or nests through the spec
   * mapping the element pair), and the one placement the Impl owes a stub. The other is the
   * component's own leaf, which stays leaf-shaped and is matched as one — the two can never
   * coexist, since a marker and a same-named leaf declare one method with incompatible return
   * types. Asked only after {@link #validateSpecMethods}, which has already refused a parameterised
   * bridge, so the zero-parameter half of the shape is a precondition rather than a test.
   */
  private boolean isBridgeMarker(TypeElement owner, ExecutableElement method) {
    return method.getAnnotation(OptionalBridge.class) != null
        && method.getModifiers().contains(Modifier.ABSTRACT)
        && !isAbstractLeaf(owner, method);
  }

  /**
   * The element a bridged {@code Optional}'s type argument stands for: the argument itself, or the
   * bound of an {@code ? extends} wildcard. A wildcard argument can never be matched by a leaf (no
   * method may declare one as a type argument) and can never be {@code isSameType} with the wire
   * component, so reading it as written would refuse every wildcard-carrying component with a fix
   * the author cannot apply. The bridge resolves it instead, as the container generators do, and
   * the generated code types: {@code orElse(null)} widens to the bound, and an {@code
   * Optional<Bound>} is assignable back to the component's declared type.
   */
  private TypeMirror bridgeElement(TypeMirror domainElement) {
    TypeMirror resolved = ProcessorUtils.resolveWildcard(domainElement);
    return resolved != null
        ? resolved
        : processingEnv.getElementUtils().getTypeElement("java.lang.Object").asType();
  }

  /**
   * Whether the spec declares the {@link Kind#OPTIONAL_BRIDGE} correspondence on this domain
   * component, under either placement. Read on demand from the spec's members, like {@link
   * #findLeaf}, so classification needs no separate vocabulary to thread. Every surviving member
   * carrying the annotation is zero-parameter by then, so the name and the annotation identify it.
   */
  private boolean declaresBridge(TypeElement spec, String name) {
    return specMembers(spec).stream()
        .anyMatch(
            method ->
                method.getSimpleName().contentEquals(name)
                    && method.getAnnotation(OptionalBridge.class) != null);
  }

  /**
   * A bridged correspondence around the one its present value takes, carrying the element type when
   * — and only when — the leg has to name it. The bridged leg wraps the read back into the domain's
   * {@code Optional}, and {@code Optional} is invariant, so an element whose own type arguments
   * include a wildcard must be named: inference would capture it, and {@code Optional<List<CAP>>}
   * is not the declared {@code Optional<List<? extends X>>}. A wildcard deeper than the element's
   * own arguments never captures, and neither does anything else, so every other pair keeps the
   * shorter inferred leg.
   */
  private static Correspondence bridgedCorrespondence(Correspondence present, TypeMirror element) {
    return new Correspondence(
        present.name(),
        present.wireName(),
        Kind.OPTIONAL_BRIDGE,
        null,
        null,
        element instanceof DeclaredType declared && hasWildcardArgument(declared)
            ? ProcessorUtils.typeNameOf(element)
            : null,
        null,
        present);
  }

  /**
   * Refuses a getter-only {@code List} on a sparse update, whose whole contract is that a {@code
   * null} read means "leave unchanged". The JAXB convention creates the list on first call, so the
   * property has no unset state to read as {@code null}: an omitted field arrives as a present
   * empty list, and the edit faithfully writes it, replacing whatever the domain held. Nothing
   * fails, at compile time or at run time — the value is simply gone, which is why this is refused
   * rather than documented.
   *
   * <p>The dense tier keeps the same property, filling it through {@code addAll} (see {@link
   * #checkCollectionGettersFillable}); there absence has no meaning and every component is written.
   * The two tiers refuse and accept the same shape for opposite reasons, so each asks at its own
   * entry point rather than in the bean model they share.
   *
   * <p>The write site is the only signal available: whether a getter creates on first call cannot
   * be read from its signature, so a getter-only {@code List} that would in fact answer {@code
   * null} is refused with the rest. That over-refusal buys a diagnostic in place of silent data
   * loss, and the setter it asks for is a line.
   *
   * <p>The setter alone is not the remedy. A field initialiser ({@code tags = new ArrayList<>()})
   * defeats it the same way, as does a getter that still creates its list on first call, and
   * neither shows in a signature, so a setter-backed property is accepted whatever its field holds.
   * The fix line therefore names both halves of a property that reads {@code null} when omitted:
   * the setter, and a getter that answers {@code null} until it is set.
   */
  private boolean checkCollectionGettersCarryAbsence(TypeElement spec, WireShape.BeanShape bean) {
    for (WireShape.BeanProperty property : bean.properties()) {
      if (property.write().orElse(null) instanceof WireShape.WriteSite.CollectionAdd write) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "bean property '"
                + property.name()
                + "' on '"
                + bean.element().getSimpleName()
                + "' is a getter-only "
                + ProcessorUtils.simpleTypeName(property.type())
                + ", which cannot carry a sparse update's absence (not supported yet).",
            "A sparse update reads null as 'not provided, leave unchanged', and "
                + write.getter()
                + "() creates its list on first call, so it never answers null: a request that"
                + " omits '"
                + property.name()
                + "' would read as a present empty list and clear the domain value.",
            "Give '"
                + property.name()
                + "' a set"
                + ProcessorUtils.capitalise(property.name())
                + " setter, and let "
                + write.getter()
                + "() answer null until it is set, with no initialiser on the field and no list"
                + " created on first call, so an omitted field reads as absent; a generated class"
                + " whose getter cannot change needs a hand-written PATCH bean instead.");
        return false;
      }
    }
    return true;
  }

  /**
   * Refuses an accessor a two-way bean leaves out where leaving it out loses a value, on a mapping
   * that builds and parses. A bean maps the properties it both reads and writes, so a getter
   * nothing writes, or a writer nothing reads, is not mapped. That is harmless for a computed
   * getter or a builder's adder, and silent loss for an accessor named after a domain component the
   * bean carries under no name: build would never write the component.
   */
  private boolean checkAccessorsPair(
      TypeElement spec, TypeElement domain, WireShape.BeanShape bean, Set<String> unmapped) {
    List<UnpairedRefusal> refusals = uncarriedAccessors(spec, domain, bean, unmapped);
    refusals.forEach(refusal -> reportUncarriedAccessor(spec, domain, bean, refusal, false));
    return refusals.isEmpty();
  }

  /**
   * Refuses the accessors a PATCH bean leaves out where leaving them out ignores what a client
   * sends. An accessor named after a domain component the bean carries under no name is refused as
   * on a mapping that builds ({@link #checkAccessorsPair}), and so is a {@code setX} setter with no
   * getter, whatever it names: a setter is how the client's value arrives, so one the fold never
   * reads is a field the client can send and the update ignores. A builder's one-argument method is
   * no such signal, being only a candidate writer until a getter pairs it, and nor is a method that
   * merely starts with {@code set} ({@code setup}), so neither is refused unless it names a
   * component.
   */
  private boolean checkPatchAccessorsPair(
      TypeElement spec, TypeElement domain, WireShape.BeanShape bean, Set<String> unmapped) {
    List<UnpairedRefusal> refusals = uncarriedAccessors(spec, domain, bean, unmapped);
    refusals.forEach(refusal -> reportUncarriedAccessor(spec, domain, bean, refusal, true));
    // A setter already refused, or offered as a refusal's likely misspelling, is answered there.
    Set<String> answered =
        refusals.stream()
            .flatMap(
                refusal -> Stream.concat(Stream.of(refusal.accessor()), refusal.partner().stream()))
            .map(WireShape.UnpairedAccessor::name)
            .collect(Collectors.toSet());
    List<WireShape.UnpairedAccessor> unread =
        bean.unpaired().stream()
            .filter(accessor -> !unmapped.contains(accessor.name()))
            .filter(accessor -> accessor.role() == WireShape.UnpairedAccessor.Role.SETTER)
            .filter(setter -> Character.isUpperCase(setter.method().charAt(3)))
            .filter(setter -> !answered.contains(setter.name()))
            .toList();
    unread.forEach(setter -> reportUnreadSetter(spec, domain, bean, setter));
    return refusals.isEmpty() && unread.isEmpty();
  }

  /**
   * An unpaired accessor named after a domain component the bean carries under no name: the
   * component, and the unpaired accessor of the other role that is most likely its misspelling.
   */
  private record UnpairedRefusal(
      WireShape.UnpairedAccessor accessor,
      String component,
      Optional<WireShape.UnpairedAccessor> partner) {}

  /**
   * The unpaired accessors named after a domain component the bean carries under no name, in the
   * bean's order. Each is offered its likely misspelling: the nearest unpaired accessor of the
   * other role at the same type, since a rename to another type would pair nothing. A misspelling
   * is offered once, and never one named after a component the mapping needs, which is a refusal of
   * its own.
   */
  private List<UnpairedRefusal> uncarriedAccessors(
      TypeElement spec, TypeElement domain, WireShape.BeanShape bean, Set<String> unmapped) {
    Map<String, String> uncarried = uncarriedNames(spec, domain, bean);
    Set<String> offered = new HashSet<>();
    List<UnpairedRefusal> refusals = new ArrayList<>();
    for (WireShape.UnpairedAccessor accessor : bean.unpaired()) {
      String component = unmapped.contains(accessor.name()) ? null : uncarried.get(accessor.name());
      if (component != null) {
        Optional<WireShape.UnpairedAccessor> partner =
            nearestPartner(accessor, bean.unpaired(), uncarried, offered);
        partner.ifPresent(other -> offered.add(other.name()));
        refusals.add(new UnpairedRefusal(accessor, component, partner));
      }
    }
    return List.copyOf(refusals);
  }

  /**
   * The names a mapping needs from a bean that the bean has no property for, each with the domain
   * component it would carry. A component maps under the targets of the {@code @MapField} renames
   * declared for it, and under its own name unless one of those renames is declared on the spec
   * itself: a local rename binds or is refused, while an inherited one that finds nothing on this
   * wire is inert, and the component then maps by its own name. Every name of a component none of
   * whose names is a property is needed.
   */
  private Map<String, String> uncarriedNames(
      TypeElement spec, TypeElement domain, WireShape.BeanShape bean) {
    List<ExecutableElement> renames =
        specMembers(spec).stream()
            .filter(method -> method.getAnnotation(MapField.class) != null)
            .toList();
    Map<String, String> uncarried = new LinkedHashMap<>();
    for (RecordComponentElement component : domain.getRecordComponents()) {
      String name = component.getSimpleName().toString();
      List<ExecutableElement> renamedBy =
          renames.stream().filter(method -> method.getSimpleName().contentEquals(name)).toList();
      Stream<String> own =
          renamedBy.stream().anyMatch(method -> declaredLocally(method, spec))
              ? Stream.empty()
              : Stream.of(name);
      List<String> names =
          Stream.concat(
                  own, renamedBy.stream().map(method -> method.getAnnotation(MapField.class).to()))
              .toList();
      if (names.stream().noneMatch(wireName -> bean.componentNamed(wireName).isPresent())) {
        names.forEach(wireName -> uncarried.putIfAbsent(wireName, name));
      }
    }
    return uncarried;
  }

  /**
   * The accessors an {@code @Unmapped} marker reads as deliberately left out, or null once a marker
   * is reported. A marker the spec declares itself must name an accessor the bean leaves unpaired,
   * which is the typo guard; one inherited from a mix-in binds where it can and is otherwise inert,
   * like every other inherited vocabulary member, so one mix-in serves wires that differ.
   */
  private Set<String> unmappedNames(TypeElement spec, WireShape wire) {
    Set<String> unpaired =
        wire.unpaired().stream()
            .map(WireShape.UnpairedAccessor::name)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<String> marked = new LinkedHashSet<>();
    for (ExecutableElement method : specMembers(spec)) {
      if (method.getAnnotation(Unmapped.class) == null) {
        continue;
      }
      String name = method.getSimpleName().toString();
      if (unpaired.contains(name)) {
        marked.add(name);
        continue;
      }
      if (!declaredLocally(method, spec)) {
        continue;
      }
      boolean carried = wire.componentNamed(name).isPresent();
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "@Unmapped method '"
              + name
              + "' names "
              + (carried
                  ? "a property '" + wire.element().getSimpleName() + "' maps."
                  : "no accessor '" + wire.element().getSimpleName() + "' leaves out."),
          carried
              ? "The marker reads an accessor with no partner as deliberate, and '"
                  + name
                  + "' is read and written, so the mapping carries it like any other property."
              : "The marker reads an accessor with no partner as deliberate. Left unpaired on '"
                  + wire.element().getSimpleName()
                  + "': "
                  + unpaired
                  + ".",
          carried
              ? "Remove the marker; to leave the property out of the mapping, remove one of its"
                  + " accessors from '"
                  + wire.element().getSimpleName()
                  + "'."
              : "Name the marker after the accessor's property, or remove it."
                  + didYouMean(name, List.copyOf(unpaired)));
      return null;
    }
    return Set.copyOf(marked);
  }

  private Optional<WireShape.UnpairedAccessor> nearestPartner(
      WireShape.UnpairedAccessor accessor,
      List<WireShape.UnpairedAccessor> unpaired,
      Map<String, String> uncarried,
      Set<String> offered) {
    return unpaired.stream()
        .filter(other -> other.reads() != accessor.reads())
        .filter(other -> !uncarried.containsKey(other.name()) && !offered.contains(other.name()))
        .filter(other -> processingEnv.getTypeUtils().isSameType(other.type(), accessor.type()))
        .map(other -> Map.entry(other, levenshtein(accessor.name(), other.name())))
        .filter(near -> near.getValue() < NEAR_MISS)
        .min(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey);
  }

  private void reportUncarriedAccessor(
      TypeElement spec,
      TypeElement domain,
      WireShape.BeanShape bean,
      UnpairedRefusal refusal,
      boolean sparse) {
    WireShape.UnpairedAccessor accessor = refusal.accessor();
    String name = accessor.name();
    String component = "'" + domain.getSimpleName() + "." + refusal.component() + "'";
    String target =
        component + (refusal.component().equals(name) ? "" : " (renamed to '" + name + "')");
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        unpairedWhat(bean, accessor),
        sparse
            ? "updateFrom folds in only the properties a PATCH bean both reads and writes, so a"
                + " value the client sends for '"
                + name
                + "' would never reach "
                + target
                + ", and nothing would say so."
            : "A mapping carries only the properties a bean both reads and writes, so "
                + target
                + " would go unmapped without a word: build would never write it.",
        addPartner(bean, accessor, sparse)
            + refusal
                .partner()
                .map(
                    other ->
                        " Or, if "
                            + other.signature()
                            + " is meant to "
                            + (accessor.reads() ? "write" : "read")
                            + " '"
                            + name
                            + "', rename it "
                            + other.renamedFor(name)
                            + ".")
                .orElse(
                    (sparse
                            ? " Or, if clients must not change " + component
                            : " Or, if "
                                + accessor.signature()
                                + " is not meant to carry "
                                + component)
                        + ", declare "
                        + unmappedMarker(accessor)
                        + " on the spec."));
  }

  private void reportUnreadSetter(
      TypeElement spec,
      TypeElement domain,
      WireShape.BeanShape bean,
      WireShape.UnpairedAccessor setter) {
    String name = setter.name();
    String removal =
        "Remove " + setter.signature() + " from '" + bean.element().getSimpleName() + "'";
    // A component of that name is carried under a rename, or its setter would have been refused as
    // one the mapping needs.
    boolean renamed = componentNames(domain).contains(name);
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        unpairedWhat(bean, setter),
        "updateFrom folds in only the properties a PATCH bean both reads and writes, and a setter is"
            + " how a client's value arrives, so a value the client sends for '"
            + name
            + "' would be ignored without a word"
            + (renamed
                ? ": '"
                    + domain.getSimpleName()
                    + "."
                    + name
                    + "' is renamed by @MapField, and the update reads it under that name."
                : ", and '"
                    + domain.getSimpleName()
                    + "' has no component named '"
                    + name
                    + "' for a getter alone to map it to."),
        (renamed
                ? removal + "."
                : removal
                    + ", or rename it after the component it should update and give it a getter.")
            + " Or declare "
            + unmappedMarker(setter)
            + " on the spec, to leave it out deliberately.");
  }

  /** The marker declaration that reads an accessor's omission as deliberate. */
  private static String unmappedMarker(WireShape.UnpairedAccessor accessor) {
    return "'@Unmapped "
        + ProcessorUtils.simpleTypeName(accessor.type())
        + " "
        + accessor.name()
        + "();'";
  }

  /** The what-line of an unpaired accessor's refusal: which accessor it has, and which it lacks. */
  private static String unpairedWhat(
      WireShape.BeanShape bean, WireShape.UnpairedAccessor accessor) {
    return "bean property '"
        + accessor.name()
        + "' on '"
        + bean.element().getSimpleName()
        + "' has a "
        + accessor.role().label()
        + ", "
        + accessor.signature()
        + ", but no "
        + partnerRole(bean, accessor).label()
        + ", so the mapping leaves it out.";
  }

  /**
   * The role of the accessor that would pair {@code accessor}: a getter, or the bean's kind of
   * writer. A two-way bean always has a construction strategy, which names that writer.
   */
  private static WireShape.UnpairedAccessor.Role partnerRole(
      WireShape.BeanShape bean, WireShape.UnpairedAccessor accessor) {
    if (!accessor.reads()) {
      return WireShape.UnpairedAccessor.Role.GETTER;
    }
    return bean.strategy().orElseThrow() instanceof WireShape.ConstructionStrategy.Builder
        ? WireShape.UnpairedAccessor.Role.BUILDER_SETTER
        : WireShape.UnpairedAccessor.Role.SETTER;
  }

  /**
   * The fix that pairs {@code accessor}: the accessor it lacks, spelt out. A PATCH property must be
   * able to be absent, so on the sparse tier a primitive is offered its wrapper, on both accessors.
   */
  private String addPartner(
      WireShape.BeanShape bean, WireShape.UnpairedAccessor accessor, boolean sparse) {
    String owner = "'" + bean.element().getSimpleName() + "'";
    String suffix = BeanPropertyAnalyser.accessorSuffix(accessor.name());
    boolean boxed = sparse && accessor.type().getKind().isPrimitive();
    String type =
        boxed
            ? processingEnv
                .getTypeUtils()
                .boxedClass((PrimitiveType) accessor.type())
                .getSimpleName()
                .toString()
            : ProcessorUtils.simpleTypeName(accessor.type());
    String add =
        switch (partnerRole(bean, accessor)) {
          case GETTER -> "Add " + type + " get" + suffix + "() to " + owner;
          case SETTER -> "Add set" + suffix + "(" + type + ") to " + owner;
          case BUILDER_SETTER ->
              "Add a setter for '"
                  + accessor.name()
                  + "' taking "
                  + type
                  + " to the builder of "
                  + owner;
        };
    return add
        + (boxed
            ? ", and declare "
                + accessor.signature()
                + " with "
                + type
                + " too, since a PATCH property must be able to be absent."
            : ".");
  }

  /**
   * Refuses a PATCH bean that is not both read and written. A sparse update only reads its wire,
   * but it reads {@code null} as "not provided", and only a bean that is written can leave a
   * property unset: a read-only bean's getter may answer from its constructor or create its value
   * on first call, and either reads as present and overwrites the domain value, with nothing in the
   * signatures to say which. A bean that cannot be read has nothing to fold at all.
   *
   * <p>Being written is necessary, not sufficient: a written bean whose field carries an
   * initialiser reads that default as present just the same, and no signature shows it either, so
   * the fix line asks for the getter's half as well as the writer.
   */
  private boolean checkPatchBeanTwoWay(TypeElement spec, WireShape.BeanShape bean) {
    WireShape.Direction direction = bean.direction();
    if (direction == WireShape.Direction.BIDIRECTIONAL) {
      return true;
    }
    String name = bean.element().getSimpleName().toString();
    if (direction == WireShape.Direction.PARSE_ONLY) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'"
              + name
              + "' has getters but no way to be written, so it cannot serve as a sparse update's"
              + " PATCH bean.",
          "A sparse update reads null as 'not provided, leave unchanged', and only a bean that is"
              + " written can leave a property unset: a read-only bean's getter may answer from its"
              + " constructor or create its value on first call, and either reads as present and"
              + " overwrites the domain value.",
          "Give '"
              + name
              + "' setters or a builder for its properties, and getters that answer null until a"
              + " value is set, so an omitted field stays null, or extend MappingSpec instead,"
              + " whose dense parse needs no absence.");
    } else {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'" + name + "' declares no getters, so a sparse update has nothing to read from it.",
          "updateFrom reads each property of the PATCH bean and folds in the present ones, and '"
              + name
              + "' can be written but not read.",
          "Give '" + name + "' getX getters for the properties it carries.");
    }
    return false;
  }

  /**
   * Says which way a one-directional bean maps, once per spec. The bean's shape decides rather than
   * a declaration, so this note is what keeps an unintended reading in view: a bean meant to be
   * built whose constructor the Impl cannot call would otherwise lose its {@code build} unremarked.
   */
  private void noteOneDirectional(
      TypeElement spec, WireShape.BeanShape bean, BeanPropertyAnalyser analyser) {
    String name = bean.element().getSimpleName().toString();
    if (bean.direction() == WireShape.Direction.PARSE_ONLY) {
      boolean unreachable = analyser.declaresSetters(bean.element());
      Diagnostics.note(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'"
              + name
              + "' maps parse-only: the generated Impl carries parse and asValidatedParse(), and no"
              + " build.",
          unreachable
              ? "'"
                  + name
                  + "' has setters, but no no-args constructor the generated Impl can call from"
                  + " package '"
                  + processingEnv.getElementUtils().getPackageOf(spec).getQualifiedName()
                  + "', so nothing can write it."
              : "'"
                  + name
                  + "' has getters but no setX setters and no builder that fills it, so nothing"
                  + " can write it.",
          unreachable
              ? "If '"
                  + name
                  + "' should be built too, give it a no-args constructor the generated Impl can"
                  + " call: public, or package-private beside the spec."
              : "If '"
                  + name
                  + "' should be built too, give it a no-args constructor with setX setters"
                  + " matching its getters, or a builder.");
      return;
    }
    Diagnostics.note(
        processingEnv.getMessager(),
        spec,
        TAG,
        "'"
            + name
            + "' maps build-only: the generated Impl carries build and asValidatedBuild(), and no"
            + " parse.",
        "'" + name + "' can be written but declares no getX or isX getter, so nothing can read it.",
        "If '" + name + "' should be parsed too, give it getters matching its setters.");
  }

  /**
   * Refuses a getter-only {@code List} the build could not fill. Such a property is written by the
   * JAXB convention, {@code getX().addAll(...)}, and {@code addAll(Collection<? extends E>)} needs
   * the element type the declaration withholds: over a raw receiver the call is unchecked, which
   * fails any build running {@code -Werror}, and over a wildcard one the receiver and the argument
   * capture separately, so no argument can satisfy it. The generated Impl is what breaks, and the
   * author cannot edit it, so the refusal lands on the declaration that can be changed.
   *
   * <p>Asked only where a {@code build} is emitted. The sparse tier shares the bean model but reads
   * the property and never writes it, so the same bean maps there untouched.
   */
  private boolean checkCollectionGettersFillable(TypeElement spec, WireShape.BeanShape bean) {
    for (WireShape.BeanProperty property : bean.properties()) {
      if (property.write().orElse(null) instanceof WireShape.WriteSite.CollectionAdd write
          && !hasProperArguments(property.type())) {
        reportUnfillableCollectionGetter(spec, bean.element(), property, write);
        return false;
      }
    }
    return true;
  }

  /**
   * The two causes read differently to the author who hit them, so they say different things: a raw
   * property names no element type at all, and a wildcard one names a bound that is a fresh type at
   * every mention. Only the raw author is told to add type arguments; the wildcard author already
   * has one. Both are offered the setter, which takes the property's declared type whatever its
   * arguments.
   */
  private void reportUnfillableCollectionGetter(
      TypeElement spec,
      TypeElement bean,
      WireShape.BeanProperty property,
      WireShape.WriteSite.CollectionAdd write) {
    boolean raw = !scanTypable(property.type());
    String setter = "set" + ProcessorUtils.capitalise(property.name());
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "bean property '"
            + property.name()
            + "' on '"
            + bean.getSimpleName()
            + "' is a getter-only "
            + ProcessorUtils.simpleTypeName(property.type())
            + ", which a build cannot fill (not supported yet).",
        "A getter-only List is filled by the JAXB convention, "
            + write.getter()
            + "().addAll(...), and addAll needs an element type it can name: "
            + (raw
                ? "a raw List names none, so the call is unchecked"
                : "a wildcard argument is a fresh type at each mention, so the receiver and the"
                    + " argument never meet")
            + ".",
        (raw
                ? "Declare the type arguments on '"
                    + write.getter()
                    + "()', for example List<T>, or"
                : "Replace the wildcard on '"
                    + write.getter()
                    + "()' with the element type it stands for, or")
            + " give '"
            + property.name()
            + "' a "
            + setter
            + " setter, which takes the property as declared.");
  }

  /**
   * The write site of a wire property, or null when the wire is a record or names no such property.
   * Read where a correspondence turns on how the property is <em>written</em>, not just on its
   * type.
   */
  private static WireShape.WriteSite writeSite(WireShape wire, String wireName) {
    if (!(wire instanceof WireShape.BeanShape bean)) {
      return null;
    }
    return bean.properties().stream()
        .filter(property -> property.name().equals(wireName))
        .findFirst()
        .flatMap(WireShape.BeanProperty::write)
        .orElse(null);
  }

  /**
   * Refuses an {@code Optional} bridge onto a property written through its own getter — the JAXB
   * collection convention, {@code getX().addAll(...)}, which a getter-only {@code List} property
   * takes. The bridge exists to carry absence across a wire that has no {@code Optional}, and this
   * property cannot hold it: absence is written as {@code null}, which {@code addAll} cannot take,
   * and the getter answers with a freshly created empty list, so the round trip would silently
   * return a present empty value. The two fixes each give the pair an honest encoding: a domain
   * {@code List}, where empty <em>is</em> nothing, or a property that can hold a {@code null},
   * which needs both a setter to write it and a getter that returns it rather than creating a list.
   */
  private void reportGetterOnlyBridge(
      TypeElement spec,
      TypeElement domain,
      String name,
      String wireName,
      TypeMirror domainType,
      TypeMirror element,
      WireShape.WriteSite.CollectionAdd write) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "domain field '"
            + domain.getSimpleName()
            + "."
            + name
            + "' is "
            + ProcessorUtils.simpleTypeName(domainType)
            + ", bridged to the getter-only bean property '"
            + wireName
            + "' (not supported yet).",
        "The bridge writes an empty Optional as null, and '"
            + wireName
            + "' is written through its own getter (the JAXB convention, "
            + write.getter()
            + "().addAll(...)), whose list is created on first call, so the property cannot hold a"
            + " null and absence would read back as a present empty list.",
        "Declare '"
            + name
            + "' as "
            + ProcessorUtils.simpleTypeName(element)
            + ", dropping the Optional, so the property's own empty list encodes nothing, or give '"
            + wireName
            + "' a setter and a getter that returns what the setter stored, so absence can be"
            + " written as null and read back.");
  }

  /**
   * A {@code @Flatten} marker: the abstract method named after the domain record component that is
   * spread across the wire's flat components. Asked only after {@link #validateSpecMethods}, which
   * has refused a bodied, parameterised or otherwise-annotated one, so the annotation alone
   * identifies it.
   */
  private static boolean isFlattenMarker(ExecutableElement method) {
    return method.getAnnotation(Flatten.class) != null;
  }

  /** Zero-parameter, {@code ValidatedPrism}-returning and bodiless: an element-mapped leaf. */
  private boolean isAbstractLeaf(TypeElement owner, ExecutableElement method) {
    return method.getModifiers().contains(Modifier.ABSTRACT)
        && method.getAnnotation(MapField.class) == null
        && method.getAnnotation(Flatten.class) == null
        && method.getParameters().isEmpty()
        && memberTypeIn(owner, method) instanceof DeclaredType returnType
        && ((TypeElement) returnType.asElement()).getQualifiedName().contentEquals(VALIDATED_PRISM)
        && returnType.getTypeArguments().size() == 2;
  }

  /**
   * The spec's abstract leaves in declaration order (own members before inherited), one per name:
   * unrelated mix-ins agreeing on a leaf declare one fact. Most disagreements are javac's error
   * (two parameterisations of {@code ValidatedPrism} are never return-type-substitutable), but
   * wildcard-differing declarations may legally coexist, so the member kept is the
   * subtype-narrowest of its group, the same fold {@link #addMarkerStubs} applies and {@link
   * #checkGroupsHaveNarrowestReturns} has guarded. Each becomes a constructor-supplied field of the
   * generated Impl, surfaced through the {@code of(...)} factory.
   */
  private List<ExecutableElement> abstractLeaves(TypeElement spec) {
    Map<String, List<ExecutableElement>> leaves = new LinkedHashMap<>();
    for (ExecutableElement method : specMembers(spec)) {
      if (isAbstractLeaf(spec, method)) {
        leaves
            .computeIfAbsent(method.getSimpleName().toString(), name -> new ArrayList<>())
            .add(method);
      }
    }
    return leaves.values().stream().map(group -> narrowestMember(spec, group)).toList();
  }

  /**
   * A member's whole signature as its owner has it, not as its declaring interface wrote it.
   *
   * <p>A member inherited from a generic mix-in is declared in that mix-in's own vocabulary: {@code
   * Emails<T>} carrying {@code ValidatedPrism<String, T>} says {@code T}, and the spec saying
   * {@code extends Emails<EmailAddress>} is what makes it {@code EmailAddress}. Read as declared it
   * is then compared with, and emitted beside, types derived from the spec's instantiation - the
   * two vocabularies agreeing only by name coincidence, which is the whole of this defect family.
   *
   * <p>Total for any member of the owner: {@code owner.asType()} is the prototypical type, never a
   * wildcard instantiation, so a member declared on the spec itself substitutes to itself and a
   * generic spec's own parameters survive as themselves - which is right, because the generated
   * Impl declares them. A <em>raw</em> supertype is the one shape this cannot answer for: one
   * carrying vocabulary is refused before reaching here, and one carrying none arrives with its
   * members erased, which is what the language says they are.
   *
   * @param owner the type the member is read under: the annotated spec, or a mix-in being
   *     classified in its own right; must not be null
   * @param member one of its members, own or inherited; must not be null
   * @return the member's signature - parameters and return type - under {@code owner}'s
   *     instantiation
   */
  private ExecutableType memberSignatureIn(TypeElement owner, ExecutableElement member) {
    return ProcessorUtils.memberOf(
        processingEnv.getTypeUtils(), (DeclaredType) owner.asType(), member);
  }

  /**
   * The return half of {@link #memberSignatureIn}, which is what most readers want: the member's
   * return type as the owner has it.
   *
   * @param owner the type the member is read under; must not be null
   * @param member one of its members, own or inherited; must not be null
   * @return the member's return type under {@code owner}'s instantiation
   */
  private TypeMirror memberTypeIn(TypeElement owner, ExecutableElement member) {
    return memberSignatureIn(owner, member).getReturnType();
  }

  /**
   * An abstract leaf as the generated Impl carries it: its name, and its prism type under the spec.
   *
   * <p>Resolved once, here, rather than at the emission site: the skeleton builders take these
   * instead of the elements, so there is no {@code getReturnType()} left down there to read as
   * declared by mistake. The prism type stays a model type rather than a name, so the builders can
   * ask whether it names a raw type.
   */
  private record LeafField(String name, TypeMirror prismType) {

    /** The prism type as the Impl writes it out. */
    TypeName prismTypeName() {
      return ProcessorUtils.typeNameOf(prismType);
    }
  }

  /** The spec's abstract leaves, each with its prism type under the spec's instantiation. */
  private List<LeafField> leafFields(TypeElement spec) {
    return abstractLeaves(spec).stream()
        .map(leaf -> new LeafField(leaf.getSimpleName().toString(), memberTypeIn(spec, leaf)))
        .toList();
  }

  /**
   * Whether the spec declares this member itself rather than inheriting it from a mix-in. The
   * distinction is load-bearing rather than incidental: every rule that refuses vocabulary it
   * cannot use refuses only a local declaration, so one shared vocabulary serves specs the rule
   * does not reach alike, and a member the spec re-declares counts as its own ({@link #specMembers}
   * applies Java's override precedence before this is asked).
   */
  private static boolean declaredLocally(ExecutableElement method, TypeElement spec) {
    return method.getEnclosingElement().equals(spec);
  }

  /** Names a member for diagnostics, noting its declaring mix-in when inherited. */
  private static String inheritedNote(ExecutableElement method, TypeElement spec) {
    return declaredLocally(method, spec)
        ? ""
        : " (inherited from '" + method.getEnclosingElement().getSimpleName() + "')";
  }

  /**
   * Tier gate: a spec names one tier, so it extends {@code MappingSpec} or {@code UpdateSpec}, not
   * both. One spec generates one Impl, and the two tiers emit disjoint members — a full mapping
   * emits {@code build}/{@code parse}/{@code as*}, a sparse update {@code updateFrom} alone — so
   * nothing an Impl could emit satisfies both clauses.
   *
   * <p>Asked of the sparse arm, since that is where a spec declaring both is dispatched. The shape
   * is refused rather than silently sparse because the {@code MappingSpec} clause is then written
   * for nothing; {@link #register} enters such a spec unusable in step, so a parent nesting the
   * pair reports the missing mapping instead of generating a call to a {@code parse} the Impl never
   * emitted.
   *
   * <p>Reaches a spec of this compilation only. A spec in a dependency is never dispatched, so the
   * registry's side of the rule is what answers for it, and the hint its registration carries is
   * the only diagnostic the shape gets from across a module boundary.
   *
   * <p>Only the two direct clauses: a spec reaching either supertype through another interface is
   * already refused by {@link #checkMixins}, which lets no mapping spec be a mix-in.
   */
  private boolean checkOneTier(TypeElement spec, DeclaredType updateSuper) {
    DeclaredType mappingSuper = findMappingSpec(spec);
    if (mappingSuper == null) {
      return true;
    }
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "'"
            + spec.getSimpleName()
            + "' extends both '"
            + ProcessorUtils.simpleTypeName(mappingSuper)
            + "' and '"
            + ProcessorUtils.simpleTypeName(updateSuper)
            + "'.",
        "A spec generates one Impl on one tier, and the tiers emit disjoint members: a full"
            + " MappingSpec emits build/parse/as*, a sparse UpdateSpec emits updateFrom alone, so"
            + " no Impl can answer both clauses.",
        "Keep 'interface "
            + spec.getSimpleName()
            + " extends "
            + ProcessorUtils.simpleTypeName(mappingSuper)
            + "' and declare a second spec extending '"
            + ProcessorUtils.simpleTypeName(updateSuper)
            + "'; shared renames and leaves can live on a plain mix-in interface both extend.");
    return false;
  }

  /**
   * Mix-in gate: a spec may extend shared vocabulary interfaces besides its {@code
   * MappingSpec}/{@code UpdateSpec} supertype — plain interfaces carrying {@code @MapField} renames
   * and leaf/derived {@code default} methods. A mix-in must not itself be (or extend) a mapping
   * spec, and must not be reached through a raw supertype, which erases its members.
   */
  private boolean checkMixins(TypeElement spec) {
    // Every ancestor, not just the direct parents: members are collected with getAllMembers, which
    // walks the whole ancestry, so a gate that reads one level lets a non-generic mix-in carry in
    // a generic one's members and the free variable reaches the diagnostics.
    // The arguments the spec itself writes are checked as its MappingSpec clause is: a member is
    // emitted into the Impl at the argument given here, and a raw or wildcard one leaves a type
    // the Impl cannot name. Only the spec's own clauses: an argument further up belongs to the
    // interface that wrote it, whose own parameters asMemberOf substitutes for.
    // Every clause has resolved by now: a spec naming an unresolved type waits for a later round
    // (see WaitingSpecs), so each superinterface mirror is a declared type here.
    for (TypeMirror parent : spec.getInterfaces()) {
      DeclaredType declared = (DeclaredType) parent;
      TypeElement element = (TypeElement) declared.asElement();
      String name = element.getQualifiedName().toString();
      if (name.equals(MAPPING_SPEC) || name.equals(UPDATE_SPEC)) {
        continue;
      }
      if (!declared.getTypeArguments().stream()
          .allMatch(argument -> supportedArgument(spec, argument))) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "mix-in '"
                + element.getSimpleName()
                + "' is used at an unsupported instantiation: '"
                + declared
                + "'.",
            "Its members are written into the generated Impl at the arguments given here, and a raw"
                + " or wildcard argument leaves a type the Impl cannot name.",
            "Give '"
                + element.getSimpleName()
                + "' concrete arguments, or the spec's own type parameters.");
        return false;
      }
    }
    for (Inherited inherited : allSuperInterfaces(spec)) {
      DeclaredType parentType = (DeclaredType) inherited.type();
      TypeElement parentElement = (TypeElement) parentType.asElement();
      String parentName = parentElement.getQualifiedName().toString();
      if (parentName.equals(MAPPING_SPEC) || parentName.equals(UPDATE_SPEC)) {
        continue;
      }
      if (extendsMappingFamily(parentElement)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "mix-in '" + parentElement.getSimpleName() + "' is itself a mapping spec.",
            "A mix-in shares vocabulary (renames, leaves, derived fields); a mapping spec"
                + " generates an Impl of its own, and inheriting one spec from another would"
                + " conflate the two.",
            "Move the shared renames and leaves onto a plain interface and extend that instead.");
        return false;
      }
      // A generic mix-in resolves: its members are read under the spec's instantiation, so
      // 'Emails<T>' extended as 'Emails<EmailAddress>' carries ValidatedPrism<String,
      // EmailAddress> in. A raw route is the one thing substitution cannot answer for, and only
      // where the ancestor contributes something to read - a marker extending a raw JDK type
      // carries nothing in, and refusing it would tell the author to fix a type they do not own.
      // Erasure through a raw supertype only reaches members whose own declaring interface is
      // generic: javac substitutes nothing for a non-generic one, so nothing of its is lost, and
      // refusing it would name a clause the author may not own. Asking this also makes the verdict
      // independent of extends-clause order, because a generic ancestor reached both raw and
      // instantiated is rejected by javac itself.
      TypeElement rawLink = inherited.rawLink();
      if (rawLink != null
          && !parentElement.getTypeParameters().isEmpty()
          && carriesVocabulary(parentElement)) {
        TypeElement extender = inherited.rawExtender();
        String where = extender.equals(spec) ? "the spec" : "'" + extender.getSimpleName() + "'";
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            rawLink.equals(parentElement)
                ? "mix-in '" + parentElement.getSimpleName() + "' is extended raw by " + where + "."
                : "mix-in '"
                    + parentElement.getSimpleName()
                    + "' is reached through '"
                    + rawLink.getSimpleName()
                    + "', which "
                    + where
                    + " extends raw.",
            "Its members are read under the spec's instantiation, and a raw supertype erases every"
                + " one of them whatever they declare: a 'ValidatedPrism<String, Email>' arrives"
                + " bare, and a 'T' arrives as Object.",
            "Name the type arguments where "
                + where
                + " extends '"
                + rawLink.getSimpleName()
                + "', as 'extends "
                + rawLink.getSimpleName()
                + "<...>'.");
        return false;
      }
    }
    return true;
  }

  /**
   * Every interface {@code spec} inherits, directly or through another, each once.
   *
   * <p>Breadth-first from the direct parents, so a diamond yields the shared ancestor once however
   * many paths reach it. Every ancestor has resolved by the time it is walked: a spec naming an
   * unresolved type waits for a later round, and javac stops before processing when a class file's
   * superinterface is missing from the classpath.
   *
   * @param spec the spec interface to walk
   * @return its transitive super-interfaces (non-null, possibly empty)
   */
  private List<Inherited> allSuperInterfaces(TypeElement spec) {
    List<Inherited> found = new ArrayList<>();
    Deque<Inherited> pending = new ArrayDeque<>();
    spec.getInterfaces()
        .forEach(parent -> pending.addLast(inheritedFrom(spec, parent, null, null)));
    Set<Name> seen = new HashSet<>();
    while (!pending.isEmpty()) {
      Inherited current = pending.removeFirst();
      TypeElement element = (TypeElement) ((DeclaredType) current.type()).asElement();
      // The qualified name, not toString(): two same-named types from different packages are not
      // the same ancestor, and toString()'s form is the implementation's to choose.
      if (!seen.add(element.getQualifiedName())) {
        continue;
      }
      found.add(current);
      element
          .getInterfaces()
          .forEach(
              parent ->
                  pending.addLast(
                      inheritedFrom(element, parent, current.rawLink(), current.rawExtender())));
    }
    return found;
  }

  /**
   * One entry of the ancestry walk, recording who wrote its clause raw.
   *
   * <p>The first raw clause on the route is the one to name: correcting it restores every member
   * below it, and correcting anything further down cannot. The interface that wrote it is carried
   * alongside because that is the file the author has to open, and it is not always the spec.
   *
   * @param writer the interface whose extends clause names {@code parent}; must not be null
   * @param parent the clause as written; must not be null
   * @param routeLink the raw link already on this route, or null
   * @param routeExtender the interface that wrote {@code routeLink} raw, or null
   * @return the entry, carrying whichever raw clause comes first
   */
  private static Inherited inheritedFrom(
      TypeElement writer, TypeMirror parent, TypeElement routeLink, TypeElement routeExtender) {
    if (routeLink != null) {
      return new Inherited(parent, routeLink, routeExtender);
    }
    DeclaredType declared = (DeclaredType) parent;
    return ProcessorUtils.isRaw(declared)
        ? new Inherited(parent, (TypeElement) declared.asElement(), writer)
        : new Inherited(parent, null, null);
  }

  /**
   * An inherited interface, and the raw clause on the way to it, if there was one.
   *
   * <p>Rawness is carried rather than asked at the end because it erases <em>downwards</em>: a spec
   * extending a raw {@code Mid} gets erased members from {@code Emails<T>} above it, even though
   * {@code Mid implements Emails<T>} is written with its argument intact. The link is carried
   * rather than a flag because it is the clause the author has to correct, and it is not
   * necessarily the interface whose members went missing.
   *
   * @param type the inherited interface
   * @param rawLink the generic interface written raw on the way here, or null when none was
   * @param rawExtender the interface whose own clause wrote it raw, or null with {@code rawLink}
   */
  private record Inherited(TypeMirror type, TypeElement rawLink, TypeElement rawExtender) {}

  /**
   * Whether a mix-in contributes anything the spec would read off it.
   *
   * <p>Asked of every ancestor on a raw route, direct parents included, because a raw supertype
   * erases only what is read off it: an ancestor contributing nothing carries nothing in, and
   * refusing it would tell the author to correct a clause on a type they may not own. A plain
   * marker extending a raw JDK interface is accepted for exactly that reason.
   *
   * @param mixin the inherited interface
   * @return true when it declares a default method, a {@code @MapField} rename, an
   *     {@code @OptionalBridge} marker, or an abstract leaf
   */
  private boolean carriesVocabulary(TypeElement mixin) {
    return ElementFilter.methodsIn(mixin.getEnclosedElements()).stream()
        .anyMatch(
            method ->
                method.isDefault()
                    || method.getAnnotation(MapField.class) != null
                    || method.getAnnotation(OptionalBridge.class) != null
                    || isAbstractLeaf(mixin, method));
  }

  /** Whether an interface is, or transitively extends, {@code MappingSpec}/{@code UpdateSpec}. */
  private boolean extendsMappingFamily(TypeElement iface) {
    String name = iface.getQualifiedName().toString();
    if (name.equals(MAPPING_SPEC) || name.equals(UPDATE_SPEC)) {
      return true;
    }
    for (TypeMirror parent : iface.getInterfaces()) {
      if (extendsMappingFamily((TypeElement) ((DeclaredType) parent).asElement())) {
        return true;
      }
    }
    return false;
  }

  /**
   * A one-parameter abstract method over exactly the spec's declared pair, in either direction: the
   * hand-written-mapper reflex ({@code UserDto toDto(User)}), deserving a targeted answer rather
   * than the generic neither-rename-nor-leaf diagnostic.
   */
  private boolean isHandMapperShaped(
      TypeElement spec, ExecutableElement method, TypeMirror domainArg, TypeMirror wireArg) {
    if (method.getParameters().size() != 1) {
      return false;
    }
    Types types = processingEnv.getTypeUtils();
    // Both halves under the spec: the pair it is compared against is instantiated, so a member
    // read as declared would match only where the two vocabularies happen to share a name.
    ExecutableType asMember = memberSignatureIn(spec, method);
    TypeMirror parameter = asMember.getParameterTypes().getFirst();
    TypeMirror returned = asMember.getReturnType();
    return (types.isSameType(parameter, domainArg) && types.isSameType(returned, wireArg))
        || (types.isSameType(parameter, wireArg) && types.isSameType(returned, domainArg));
  }

  /**
   * Zero-parameter {@code default} returning a two-argument {@code ValidatedPrism}: leaf-shaped.
   */
  private boolean isLeafShaped(TypeElement owner, ExecutableElement method) {
    return method.isDefault()
        && method.getParameters().isEmpty()
        && memberTypeIn(owner, method) instanceof DeclaredType returnType
        && ((TypeElement) returnType.asElement()).getQualifiedName().contentEquals(VALIDATED_PRISM)
        && returnType.getTypeArguments().size() == 2;
  }

  /**
   * A locally declared leaf must name a domain component: an unmatched local leaf would silently
   * validate nothing - the typo'd-leaf hazard. Inherited leaves stay inert instead, so a shared
   * mix-in vocabulary may carry leaves for components only some extending specs have; helpers
   * belong in {@code private} or {@code static} methods, which are not leaf-shaped.
   */
  private boolean checkLocalLeavesBind(
      TypeElement spec, TypeElement domain, Set<String> flattenedInner) {
    List<String> components =
        domain.getRecordComponents().stream().map(c -> c.getSimpleName().toString()).toList();
    for (ExecutableElement method : specMembers(spec)) {
      if (!declaredLocally(method, spec) || !isLeafShaped(spec, method)) {
        continue;
      }
      // A key leaf binds by its @MapKey, not by its name, so its name is free; checkMapKeysApply
      // is what holds it to naming a real Map component.
      if (method.getAnnotation(MapKey.class) != null) {
        continue;
      }
      String name = method.getSimpleName().toString();
      // A flattened group's inner components take leaves by their own names, like any other.
      if (components.contains(name) || flattenedInner.contains(name)) {
        continue;
      }
      // The one leaf-shaped name a generated member also carries: the collision sweep owns it
      // and reports the collision, which is the better diagnostic for that mistake.
      if (name.equals("asValidatedPrism")) {
        continue;
      }
      // A flattened group's inner components are as nameable as the domain's own, so they join
      // the suggestion and the listing.
      List<String> nameable =
          Stream.concat(components.stream(), flattenedInner.stream().sorted()).toList();
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "leaf '" + name + "' names no component of " + domain.getSimpleName() + ".",
          "A leaf is a zero-parameter 'default' named after the DOMAIN component it parses (or an"
              + " inner component of a flattened one); an unmatched leaf would silently validate"
              + " nothing."
              + didYouMean(name, nameable)
              + " Found on "
              + domain.getSimpleName()
              + ": "
              + nameable
              + ".",
          "Rename the method to the component it parses, or make it 'private' or 'static' if it"
              + " is a helper.");
      return false;
    }
    return true;
  }

  /**
   * Validates every {@code @OptionalBridge} the spec carries, under either placement, before
   * classification consults it.
   *
   * <p>An unmatched <em>local</em> bridge is refused for the typo'd-leaf reason: it would silently
   * leave the component null-is-an-error, which is the whole thing the annotation was written to
   * change. An unmatched <em>inherited</em> one stays inert, so a shared mix-in vocabulary may
   * carry bridges for components only some extending specs have. A bound bridge is checked either
   * way: inheriting one does not make it mean something else.
   *
   * <p>On a bean wire the bridge is automatic, so a locally declared annotation is redundant rather
   * than wrong: a note, not an error, and silence for an inherited one — a mix-in serving a record
   * spec and a bean spec is exactly the shared vocabulary the book recommends.
   */
  private boolean checkBridgesApply(
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      Map<String, String> renames,
      List<Flattened> flattened) {
    List<String> components =
        Stream.concat(
                domain.getRecordComponents().stream().map(c -> c.getSimpleName().toString()),
                flattened.stream().flatMap(group -> group.inner().stream()))
            .toList();
    for (ExecutableElement method : specMembers(spec)) {
      if (method.getAnnotation(OptionalBridge.class) == null) {
        continue;
      }
      String name = method.getSimpleName().toString();
      boolean local = declaredLocally(method, spec);
      // An inner component of a flattened group bridges exactly as a top-level one: build reads
      // through the group and parse assembles it, so only the lookup knows the difference.
      Owned owned = ownedComponent(domain, domainDeclared, flattened, name);
      if (owned == null) {
        if (!local) {
          continue;
        }
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@OptionalBridge on '" + name + "' names no component of " + domain.getSimpleName(),
            "A bridge is named after the DOMAIN component whose Optional bridges to a nullable"
                + " wire component; an unmatched one would leave that component null-is-an-error,"
                + " which is what the annotation exists to change."
                + didYouMean(name, components)
                + " Found on "
                + domain.getSimpleName()
                + ": "
                + components
                + ".",
            "Rename the method to the component it bridges, or remove the annotation.");
        return false;
      }
      TypeMirror domainType = componentType(owned.ownerDeclared(), owned.component());
      if (containerElement(domainType, "java.util.Optional") == null) {
        boolean raw = isExactly(domainType, "java.util.Optional");
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@OptionalBridge on '"
                + name
                + "'"
                + inheritedNote(method, spec)
                + (raw
                    ? " names a raw Optional component."
                    : " names a component that is not Optional."),
            raw
                ? "The bridge carries the element of the Optional across, and a raw Optional"
                    + " declares no element type for it to carry."
                : "The bridge maps an empty Optional to a null wire component and back; '"
                    + owned.owner().getSimpleName()
                    + "."
                    + name
                    + "' is "
                    + domainType
                    + ", which has no absent state to bridge.",
            raw
                ? "Declare the type argument, for example Optional<String>."
                : "Declare the domain component as Optional<"
                    + domainType
                    + ">, or remove the annotation.");
        return false;
      }
      // A leaf placement maps the ELEMENT the bridge found, so one declared over the whole
      // Optional is not a bridged leaf at all: it wins as a plain whole-component leaf and leaves
      // the component null-is-an-error, silently defeating the annotation beside it. Both leaf
      // placements are checked - a concrete spec's 'default' body and a generic spec's abstract,
      // of()-supplied declaration - since findLeaf matches either. Both shapes have already
      // established a two-argument ValidatedPrism return, so the declared domain side is the
      // second argument.
      TypeMirror leafDomain =
          isLeafShaped(spec, method) || isAbstractLeaf(spec, method)
              ? ((DeclaredType) memberTypeIn(spec, method)).getTypeArguments().get(1)
              : null;
      if (leafDomain != null && containerElement(leafDomain, "java.util.Optional") != null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@OptionalBridge leaf '"
                + name
                + "'"
                + inheritedNote(method, spec)
                + " is declared over the whole Optional.",
            "A bridged leaf converts the element the bridge found, so it is declared over the"
                + " element types; declared over "
                + leafDomain
                + " it is an ordinary whole-component leaf, which parses a null wire value to a"
                + " located 'must not be null' rather than to an empty Optional.",
            // A primitive wire member is named by its wrapper, the one type a leaf can name.
            "Declare the leaf as 'ValidatedPrism<"
                + wire.componentNamed(renames.getOrDefault(name, name))
                    .map(c -> boxed(c.type()).toString())
                    .orElse("WireComponent")
                + ", "
                + containerElement(domainType, "java.util.Optional")
                + ">', or drop the annotation to keep the whole-Optional leaf.");
        return false;
      }
      // The marker's return type is the component's own type, so a spec that drifts from its
      // domain says so here rather than bridging whatever the component has become. A leaf
      // placement declares the ELEMENT mapping instead, and is checked as a leaf.
      TypeMirror declared = memberTypeIn(spec, method);
      if (isBridgeMarker(spec, method)
          && !processingEnv.getTypeUtils().isSameType(declared, domainType)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@OptionalBridge marker '"
                + name
                + "'"
                + inheritedNote(method, spec)
                + " returns "
                + declared
                + ", not the component's own type.",
            "A marker restates the domain component it bridges, so the spec fails to compile"
                + " rather than bridging a component that has since changed shape; '"
                + owned.owner().getSimpleName()
                + "."
                + name
                + "' is "
                + domainType
                + ".",
            "Declare the marker as '" + domainType + " " + name + "()'.");
        return false;
      }
      // A projection leaves some domain components unmapped; a bridge on one of those has no wire
      // side to check, and classification reports the shapes that are genuinely wrong.
      WireShape.WireComponent wireComponent =
          wire.componentNamed(renames.getOrDefault(name, name)).orElse(null);
      if (wireComponent == null) {
        continue;
      }
      if (!checkBridgeWireSide(
          spec,
          method,
          name,
          bridgeElement(containerElement(domainType, "java.util.Optional")),
          wire,
          wireComponent,
          local)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Validates every {@code @MapKey}: it must name a {@code Map} component of the domain (or of a
   * flattened group), or an {@code Optional<Map>} one the wire bridges ({@code bridged}), be
   * leaf-shaped over that {@code Map}'s KEY types, and be the only key leaf for it. An inherited
   * annotation naming nothing stays inert, as {@code @OptionalBridge}'s does — a mix-in may carry a
   * key leaf for components a given spec does not have.
   */
  private boolean checkMapKeysApply(
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      List<Flattened> flattened,
      Predicate<String> bridged) {
    List<String> components =
        Stream.concat(
                domain.getRecordComponents().stream().map(c -> c.getSimpleName().toString()),
                flattened.stream().flatMap(group -> group.inner().stream()))
            .toList();
    Set<String> claimed = new LinkedHashSet<>();
    for (ExecutableElement method : specMembers(spec)) {
      MapKey declared = method.getAnnotation(MapKey.class);
      if (declared == null) {
        continue;
      }
      String name = declared.value();
      boolean local = declaredLocally(method, spec);
      Owned owned = ownedComponent(domain, domainDeclared, flattened, name);
      if (owned == null) {
        if (!local) {
          continue;
        }
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapKey(\"" + name + "\") names no component of " + domain.getSimpleName(),
            "A key leaf names the DOMAIN Map component whose keys it converts; an unmatched one"
                + " converts nothing."
                + didYouMean(name, components)
                + " Found on "
                + domain.getSimpleName()
                + ": "
                + components
                + ".",
            "Point the annotation at the component it converts, or remove it.");
        return false;
      }
      TypeMirror componentType = componentType(owned.ownerDeclared(), owned.component());
      // A bridged Optional's present value lifts as its unbridged pair would, so a key leaf on one
      // converts the keys of the Map the Optional carries.
      TypeMirror optionalElement = containerElement(componentType, "java.util.Optional");
      TypeMirror domainType =
          optionalElement != null && bridged.test(name)
              ? bridgeElement(optionalElement)
              : componentType;
      DeclaredType asMap = asMapType(domainType);
      if (asMap == null || asMap.getTypeArguments().size() != 2) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapKey(\""
                + name
                + "\")"
                + inheritedNote(method, spec)
                + (asMap == null
                    ? " names a component that is not a Map."
                    : " names a raw Map component."),
            asMap == null
                ? "A key leaf converts the keys of a Map component; '"
                    + owned.owner().getSimpleName()
                    + "."
                    + name
                    + "' is "
                    + domainType
                    + ", which has no keys."
                : "A key leaf converts the key type, and a raw Map declares none.",
            asMap == null
                ? "Point the annotation at a Map component, or remove it."
                    + (optionalElement != null && asMapType(bridgeElement(optionalElement)) != null
                        ? " On a MappingSpec whose wire member is a plain nullable Map, a key leaf"
                            + " reaches the Map the Optional carries once it is bridged: declare"
                            + " '@OptionalBridge "
                            + componentType
                            + " "
                            + name
                            + "();'."
                        : "")
                : "Declare both type arguments, for example Map<Locale, String>.");
        return false;
      }
      if (!claimed.add(name)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "'" + name + "' has more than one @MapKey leaf.",
            "A Map component's keys convert through exactly one leaf; a second would leave the"
                + " choice between them arbitrary.",
            "Keep one @MapKey(\"" + name + "\") method and remove the others.");
        return false;
      }
      // The shape and the KEY types, checked here rather than left to findKeyLeaf: a key leaf
      // that does not convert this component's keys would simply not be found, and the keys
      // would copy by identity - the typo'd-leaf hazard, silent whenever the key types already
      // match. The wire side is unknown at this point (a spec may map several wires through
      // mix-ins), so the DOMAIN key is what is pinned.
      TypeMirror domainKey = asMap.getTypeArguments().getFirst();
      if (!leafShapedOverDomainKey(spec, method, domainKey)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapKey(\""
                + name
                + "\")"
                + inheritedNote(method, spec)
                + " does not declare a leaf over that component's key type.",
            "A key leaf is a zero-parameter 'default' method returning exactly"
                + " ValidatedPrism<WireKey, "
                + domainKey
                + "> (wire first, domain second); '"
                + method.getSimpleName()
                + "' returns "
                + memberTypeIn(spec, method)
                + ", so it would convert nothing and the keys would copy unvalidated.",
            "Return ValidatedPrism<WireKey, "
                + domainKey
                + "> from '"
                + method.getSimpleName()
                + "', or point the annotation at the component it does convert.");
        return false;
      }
    }
    return true;
  }

  /**
   * Whether a {@code @MapKey} method is leaf-shaped and produces the domain key type. The wire key
   * is deliberately not pinned: one mix-in may carry a key leaf used against several wires, and
   * {@link #findKeyLeaf} matches the full pair at the use site.
   */
  private boolean leafShapedOverDomainKey(
      TypeElement spec, ExecutableElement method, TypeMirror domainKey) {
    boolean leafShaped = method.isDefault() || method.getModifiers().contains(Modifier.ABSTRACT);
    if (!leafShaped
        || !method.getParameters().isEmpty()
        || !(memberTypeIn(spec, method) instanceof DeclaredType returnType)
        || !((TypeElement) returnType.asElement()).getQualifiedName().contentEquals(VALIDATED_PRISM)
        || returnType.getTypeArguments().size() != 2) {
      return false;
    }
    return processingEnv.getTypeUtils().isSameType(returnType.getTypeArguments().get(1), domainKey);
  }

  /**
   * Whether the domain component named {@code name} is read through the Optional bridge on this
   * wire, as {@link #bridges} decides it for its wire member. A component the wire leaves out
   * counts too, since it has no wire side to contradict the vocabulary a spec carries for it, which
   * stays as inert as it would on any other unprojected component.
   */
  private boolean bridgesOnWire(
      TypeElement spec, WireShape wire, Map<String, String> renames, String name) {
    return wire.componentNamed(renames.getOrDefault(name, name))
        .map(member -> bridges(spec, wire, name, member.type()))
        .orElse(true);
  }

  /**
   * Whether a domain {@code Optional} component takes the bridge against a wire member of this
   * type: the member is not itself {@code Optional}, and the wire is a bean, which bridges
   * automatically, or the spec declares the bridge. The one rule resolution and the key-leaf check
   * share.
   */
  private boolean bridges(
      TypeElement spec, WireShape wire, String name, TypeMirror wireMemberType) {
    return containerElement(wireMemberType, "java.util.Optional") == null
        && (wire instanceof WireShape.BeanShape || declaresBridge(spec, name));
  }

  /**
   * A record component and the record it belongs to: the domain's own, read under the domain's
   * instantiation, or an inner component of a flattened group, read under the group's.
   */
  private record Owned(
      TypeElement owner, DeclaredType ownerDeclared, RecordComponentElement component) {}

  private static Owned ownedComponent(
      TypeElement domain, DeclaredType domainDeclared, List<Flattened> flattened, String name) {
    RecordComponentElement own = componentNamed(domain, name);
    if (own != null) {
      return new Owned(domain, domainDeclared, own);
    }
    for (Flattened group : flattened) {
      RecordComponentElement inner = componentNamed(group.record(), name);
      if (inner != null) {
        return new Owned(group.record(), group.type(), inner);
      }
    }
    return null;
  }

  private static RecordComponentElement componentNamed(TypeElement record, String name) {
    return record.getRecordComponents().stream()
        .filter(c -> c.getSimpleName().contentEquals(name))
        .findFirst()
        .orElse(null);
  }

  private static List<String> componentNames(TypeElement record) {
    return record.getRecordComponents().stream().map(c -> c.getSimpleName().toString()).toList();
  }

  /**
   * The wire half of {@link #checkBridgesApply}: the component the bridge writes must be able to
   * hold the {@code null} that encodes absence. A component that cannot is an error; one that needs
   * no bridge at all is merely redundant, and says so as a note.
   */
  private boolean checkBridgeWireSide(
      TypeElement spec,
      ExecutableElement method,
      String name,
      TypeMirror element,
      WireShape wire,
      WireShape.WireComponent wireComponent,
      boolean local) {
    if (wireComponent.type().getKind().isPrimitive()) {
      // Removing the annotation is no way out: the domain Optional still has nothing to map to,
      // and a bean wire bridges without the annotation anyway. On a bean the wrapper leaves a
      // marker declared here redundant, so the fix drops it with the primitive.
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "@OptionalBridge on '"
              + name
              + "'"
              + inheritedNote(method, spec)
              + " bridges to the primitive "
              + wireMemberTerm(wire)
              + " '"
              + wireComponent.name()
              + "'.",
          "The bridge encodes an empty Optional as null, and the "
              + wireMemberTerm(wire)
              + " is declared "
              + wireComponent.type()
              + ", which can never be null.",
          primitiveBridgeFix(
                  spec,
                  wire,
                  name,
                  wireComponent.name(),
                  wireComponent.type(),
                  element,
                  LeafSite.bridged(true, wire),
                  local && wire instanceof WireShape.BeanShape)
              + ".");
      return false;
    }
    // The two shapes that need no bridge are redundant rather than wrong: the mapping is generated
    // exactly as it would be without the annotation. A locally declared one is worth saying so
    // about; an inherited one is not, because a shared mix-in legitimately carries a bridge for
    // components whose wire side differs from spec to spec - which is what a wire being a bean, or
    // declaring its own Optional, is.
    if (!local) {
      return true;
    }
    if (containerElement(wireComponent.type(), "java.util.Optional") != null) {
      Diagnostics.note(
          processingEnv.getMessager(),
          method,
          TAG,
          "@OptionalBridge on '"
              + name
              + "' is redundant: '"
              + wireComponent.name()
              + "' is already Optional.",
          "The bridge gives a plain nullable member an absent state; this one declares its own,"
              + " and maps by identity or through its element leaf either way.",
          "Remove the annotation, or keep it if the vocabulary is shared with a spec whose wire"
              + " component is a plain nullable one.");
      return true;
    }
    if (wire instanceof WireShape.BeanShape) {
      Diagnostics.note(
          processingEnv.getMessager(),
          method,
          TAG,
          "@OptionalBridge on '" + name + "' is redundant on a bean wire.",
          "A bean wire bridges a domain Optional to its nullable property automatically, because"
              + " bean conventions leave Optional off property types; the annotation opts a RECORD"
              + " wire into the same correspondence.",
          "Remove the annotation, or keep it if the vocabulary is shared with a record-wire spec.");
    }
    return true;
  }

  /**
   * A sealed dispatch has no components, so locally declared leaves, derived fields and
   * {@code @OptionalBridge} markers have nothing to bind to; inherited ones stay inert, so a shared
   * mix-in vocabulary still fits.
   */
  private boolean checkNoSealedVocabulary(TypeElement spec) {
    for (ExecutableElement method : specMembers(spec)) {
      if (!declaredLocally(method, spec)) {
        continue;
      }
      boolean leaf = isLeafShaped(spec, method);
      boolean bridge = method.getAnnotation(OptionalBridge.class) != null;
      // A key leaf is checked by its annotation, not its shape: a malformed one would otherwise
      // slip past every test here and sit inert on a spec that has no components to key.
      boolean key = method.getAnnotation(MapKey.class) != null;
      boolean unmapped = method.getAnnotation(Unmapped.class) != null;
      if (!leaf
          && !bridge
          && !key
          && !unmapped
          && !isDerivedCandidate(processingEnv.getTypeUtils(), spec, method)) {
        continue;
      }
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          (key
                  ? "@MapKey on '"
                  : bridge
                      ? "@OptionalBridge on '"
                      : unmapped ? "@Unmapped on '" : leaf ? "leaf '" : "derived field '")
              + method.getSimpleName()
              + "' has no meaning on a sealed mapping.",
          "Leaves, derived fields, bridges, key leaves and unmapped accessors bind to the"
              + " components and accessors of one pair; a sealed mapping dispatches over its"
              + " permitted subtypes and has neither.",
          "Move the method onto the subtype pair's own spec.");
      return false;
    }
    return true;
  }

  /** How few edits apart two names must be for one to be offered as a misspelling of the other. */
  private static final int NEAR_MISS = 3;

  /** A nearest-name hint for the unmatched-leaf diagnostic, when one is close enough to help. */
  private static String didYouMean(String name, List<String> candidates) {
    String best = null;
    int bestDistance = NEAR_MISS;
    for (String candidate : candidates) {
      int distance = levenshtein(name, candidate);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return best == null ? "" : " Did you mean '" + best + "()'?";
  }

  private static int levenshtein(String a, String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int substitution = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        current[j] =
            Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + substitution);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }

  /**
   * A spec's abstract methods must be zero-parameter {@code @MapField} renames or
   * {@code @OptionalBridge} markers, or, on a generic pair, element-mapped leaves; anything else
   * would leave the generated Impl with an unimplemented member. Each surviving member must also be
   * one the Impl can hold: no type parameters of its own (a field and a stub have nowhere to
   * declare them), a type reachable from the spec's package (the Impl writes it out in full), and,
   * for a same-named group, a subtype-narrowest return for the one member the Impl emits.
   */
  private boolean validateSpecMethods(
      TypeElement spec, boolean sealedPair, TypeMirror domainArg, TypeMirror wireArg) {
    for (ExecutableElement method : specMembers(spec)) {
      MapField mapField = method.getAnnotation(MapField.class);
      OptionalBridge bridge = method.getAnnotation(OptionalBridge.class);
      Flatten flatten = method.getAnnotation(Flatten.class);
      Unmapped unmapped = method.getAnnotation(Unmapped.class);
      if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
        if (bridge != null && !isLeafShaped(spec, method)) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@OptionalBridge method '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " is neither a marker nor a leaf.",
              "A bridge is declared on an abstract marker method named after the domain component,"
                  + " or on that component's 'default' leaf returning ValidatedPrism<WireComponent,"
                  + " OptionalElement>; a body of any other shape is neither.",
              "Remove the body to make it a marker, or give the method the leaf's return type.");
          return false;
        }
        if (mapField != null) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@MapField method '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " must be abstract.",
              "A rename is a marker method the generated Impl stubs out; a method with a body"
                  + " (default, static or private) would double as callable code.",
              "Remove the body, or remove the @MapField annotation.");
          return false;
        }
        if (unmapped != null) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@Unmapped method '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " must be abstract.",
              "An unmapped accessor is declared on a marker method the generated Impl stubs out; a"
                  + " method with a body (default, static or private) would double as callable"
                  + " code.",
              "Remove the body, or remove the @Unmapped annotation.");
          return false;
        }
        if (flatten != null) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@Flatten method '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " must be abstract.",
              "A flattened component is declared on a marker method the generated Impl stubs out;"
                  + " a method with a body (default, static or private) would double as callable"
                  + " code.",
              "Remove the body, or remove the @Flatten annotation.");
          return false;
        }
        continue;
      }
      if (!method.getTypeParameters().isEmpty()) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "abstract method '"
                + method.getSimpleName()
                + "'"
                + inheritedNote(method, spec)
                + " declares type parameters of its own.",
            "The generated Impl carries a leaf as a constructor-supplied field and a rename as a"
                + " stub, and neither has anywhere to declare the method's own type parameters, so"
                + " the generated file would name a variable nothing brings into scope.",
            mapField != null || bridge != null
                ? "Give '"
                    + method.getSimpleName()
                    + "' a concrete return type; a marker method declares a correspondence and the"
                    + " generated stub only has to name one."
                : "Declare the element types among the type parameters of '"
                    + method.getEnclosingElement().getSimpleName()
                    + "', where the spec can thread them, or give the method a body.");
        return false;
      }
      if (mapField == null && unmapped != null) {
        if (!method.getParameters().isEmpty()) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@Unmapped method '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " must not declare parameters.",
              "The marker is named after the accessor the mapping leaves out; the generated stub"
                  + " implements it without parameters.",
              "Remove the parameters.");
          return false;
        }
        if (!checkMemberTypeReachable(spec, method, "@Unmapped marker")) {
          return false;
        }
        continue;
      }
      if (mapField == null && flatten != null) {
        if (sealedPair) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@Flatten on '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " has no meaning on a sealed mapping.",
              "A flattened component is a record component spread across the wire; a sealed"
                  + " mapping dispatches over its permitted subtypes and has no components.",
              "Remove the @Flatten method; moving it to a mix-in does not help here, since a"
                  + " sealed mapping refuses an inherited marker too.");
          return false;
        }
        if (bridge != null) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@Flatten method '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " also carries @OptionalBridge.",
              "A flattened component is a record spread across the wire, and a bridged one is an"
                  + " Optional matched to a nullable wire component; one component cannot be both.",
              "Keep one of the two annotations.");
          return false;
        }
        if (!method.getParameters().isEmpty()) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@Flatten method '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " must not declare parameters.",
              "A flatten marker is named after the domain component it spreads; the generated stub"
                  + " implements it without parameters.",
              "Remove the parameters.");
          return false;
        }
        // A leaf-shaped marker would be neither: the leaf accessor is emitted only for a generic
        // spec's abstract leaves, and the stub only for markers, so the Impl would implement
        // nothing for the name.
        if (isExactly(memberTypeIn(spec, method), VALIDATED_PRISM)) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@Flatten method '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " returns a ValidatedPrism.",
              "A flatten marker restates the record type of the component it spreads; a leaf"
                  + " converts one component and is named after it, and one method cannot be"
                  + " both.",
              "Declare the marker as '<ComponentRecord> "
                  + method.getSimpleName()
                  + "()', and put any conversion in a leaf named after the inner component.");
          return false;
        }
        if (!checkMemberTypeReachable(spec, method, "@Flatten marker")) {
          return false;
        }
        continue;
      }
      if (mapField == null) {
        if (bridge != null && !method.getParameters().isEmpty()) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@OptionalBridge method '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " must not declare parameters.",
              "A bridge marker is named after the domain component whose Optional bridges; the"
                  + " generated stub implements it without parameters.",
              "Remove the parameters.");
          return false;
        }
        if (isHandMapperShaped(spec, method, domainArg, wireArg)) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "abstract method '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " redeclares the mapping itself.",
              "The spec declares vocabulary (renames, leaves, derived fields); the mapping"
                  + " methods are generated. This signature is what the generated Impl already"
                  + " exposes.",
              "Delete the method and call the generated Impl: 'build(Domain) : Wire' for the"
                  + " outbound direction, 'parse(Wire)' for the accumulating inbound one.");
          return false;
        }
        if (isAbstractLeaf(spec, method)) {
          if (!sealedPair && !spec.getTypeParameters().isEmpty()) {
            if (!checkMemberTypeReachable(spec, method, "abstract leaf")) {
              return false;
            }
            continue;
          }
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "abstract leaf '"
                  + method.getSimpleName()
                  + "'"
                  + inheritedNote(method, spec)
                  + " needs a generic spec.",
              "A concrete pair's leaf carries its own parser as a 'default' body; only a generic"
                  + " spec defers the element mapping to the generated 'of(...)' factory.",
              "Give the method a body ('default'), or make the spec generic in the element"
                  + " types.");
          return false;
        }
        // A bare @OptionalBridge marker: implementable as a stub, exactly like a rename, so the
        // Impl can hold it and the neither-rename-nor-leaf refusal below does not apply.
        if (bridge != null) {
          if (!checkMemberTypeReachable(spec, method, "@OptionalBridge marker")) {
            return false;
          }
          continue;
        }
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "abstract method '"
                + method.getSimpleName()
                + "'"
                + inheritedNote(method, spec)
                + " is neither a rename, a leaf, nor a bridge.",
            "A spec declares zero-parameter @MapField renames, @OptionalBridge markers and"
                + " 'default' leaf methods; the generated Impl cannot implement anything else.",
            "Make it a 'default' method, turn it into a '@MapField(to = ...)' rename, or annotate"
                + " it '@OptionalBridge' if it names an Optional component that bridges to a"
                + " nullable wire one.");
        return false;
      }
      if (flatten != null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapField method '"
                + method.getSimpleName()
                + "'"
                + inheritedNote(method, spec)
                + " also carries @Flatten.",
            "A rename points one domain component at one wire component, and a flattened"
                + " component spreads across several; one component cannot be both.",
            "Keep one of the two annotations; rename the group's inner components individually.");
        return false;
      }
      if (sealedPair) {
        // A dispatch has no components, so an inherited rename binds to nothing here and stays
        // inert, as an inherited leaf or bridge on the same spec already does: a sealed spec and
        // the subtype specs it dispatches to routinely share one vocabulary, and the marker it
        // still owes is emitted as a stub. Only a rename this spec wrote itself is refused.
        if (!declaredLocally(method, spec)) {
          continue;
        }
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapField has no meaning on a sealed mapping.",
            "Renames apply to record components; a sealed mapping dispatches over its permitted"
                + " subtypes and has no components.",
            "Remove the @MapField method.");
        return false;
      }
      if (!method.getParameters().isEmpty()) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapField method '" + method.getSimpleName() + "' must not declare parameters.",
            "A rename is a marker method named after the domain component; the generated stub"
                + " implements it without parameters.",
            "Remove the parameters.");
        return false;
      }
      if (!checkMemberTypeReachable(spec, method, "@MapField method")) {
        return false;
      }
    }
    return checkGroupsHaveNarrowestReturns(spec);
  }

  /**
   * Unrelated mix-ins may declare a same-named rename or leaf with covariantly differing returns
   * (override-equivalent abstracts may coexist, JLS 9.4.1.3), and the Impl emits one member for the
   * group, which must be return-type-substitutable for every declaration (JLS 8.4.8.3). For the
   * non-generic, identically-signatured members that survive validation, substitutability is
   * subtyping, with one exception the language admits through unchecked conversion: a raw return
   * beside incomparable parameterised ones has no subtype-narrowest, and is refused here rather
   * than emitting a member javac rejects inside the generated file.
   */
  private boolean checkGroupsHaveNarrowestReturns(TypeElement spec) {
    Map<String, List<ExecutableElement>> groups = new LinkedHashMap<>();
    for (ExecutableElement method : specMembers(spec)) {
      if (method.getAnnotation(MapField.class) != null
          || method.getAnnotation(Unmapped.class) != null
          || isBridgeMarker(spec, method)
          || isFlattenMarker(method)
          || isAbstractLeaf(spec, method)) {
        groups
            .computeIfAbsent(method.getSimpleName().toString(), name -> new ArrayList<>())
            .add(method);
      }
    }
    Types types = processingEnv.getTypeUtils();
    for (Map.Entry<String, List<ExecutableElement>> group : groups.entrySet()) {
      TypeMirror narrowest = memberTypeIn(spec, narrowestMember(spec, group.getValue()));
      for (ExecutableElement method : group.getValue()) {
        if (!types.isSubtype(narrowest, memberTypeIn(spec, method))) {
          Diagnostics.error(
              processingEnv.getMessager(),
              spec,
              TAG,
              "same-named members '"
                  + group.getKey()
                  + "' declare returns none of which satisfies the rest ("
                  + describeGroup(spec, group.getValue())
                  + ").",
              "The generated Impl emits one member for the group, and its return type has to be a"
                  + " subtype of every declaration.",
              "Align the returns, or give the methods different names.");
          return false;
        }
      }
    }
    return true;
  }

  /** The group's declarations for a diagnostic, sorted by interface name for a stable message. */
  private String describeGroup(TypeElement spec, List<ExecutableElement> group) {
    return group.stream()
        .map(
            method ->
                "'"
                    + ProcessorUtils.simpleTypeName(memberTypeIn(spec, method))
                    + "' from '"
                    + method.getEnclosingElement().getSimpleName()
                    + "'")
        .sorted()
        .collect(Collectors.joining(", "));
  }

  /**
   * The member of a same-named group whose return under the spec is a subtype of every other's: a
   * running minimum over the subtype order, whose result {@link #checkGroupsHaveNarrowestReturns}
   * has verified exists before anything emits.
   */
  private ExecutableElement narrowestMember(TypeElement spec, List<ExecutableElement> group) {
    Types types = processingEnv.getTypeUtils();
    ExecutableElement narrowest = null;
    for (ExecutableElement method : group) {
      if (narrowest == null
          || types.isSubtype(memberTypeIn(spec, method), memberTypeIn(spec, narrowest))) {
        narrowest = method;
      }
    }
    return narrowest;
  }

  /**
   * The generated Impl is a top-level class in the spec's package that writes this member's type
   * out in full, so every type the member names has to be visible there. Two routes get one past
   * the spec's own compile: a mix-in in another package hands over a package-private type the spec
   * never names itself, and a nested spec's member names a private type of its enclosing class,
   * which the flattened top-level Impl cannot see.
   */
  private boolean checkMemberTypeReachable(
      TypeElement spec, ExecutableElement method, String kind) {
    String implPackage = implClassName(spec).packageName();
    TypeElement unreachable =
        ProcessorUtils.firstUnreachableIn(
            processingEnv.getElementUtils(), memberTypeIn(spec, method), implPackage);
    if (unreachable == null) {
      return true;
    }
    Diagnostics.error(
        processingEnv.getMessager(),
        method,
        TAG,
        kind
            + " '"
            + method.getSimpleName()
            + "'"
            + inheritedNote(method, spec)
            + " names '"
            + unreachable.getSimpleName()
            + "', which cannot be reached from '"
            + implPackage
            + "'.",
        "The generated Impl writes the member's type out in full, so every type named inside it"
            + " has to be visible in the spec's package, where the Impl is declared.",
        "Make '"
            + unreachable.getSimpleName()
            + "' and the types enclosing it public, or declare the spec in the package they are"
            + " already visible from.");
    return false;
  }

  /** Generic specs or mapped types would leave the Impl naming undeclared type variables. */
  private boolean checkNotGeneric(TypeElement spec, TypeElement domain, TypeElement wire) {
    TypeElement offender =
        !spec.getTypeParameters().isEmpty()
            ? spec
            : !domain.getTypeParameters().isEmpty()
                ? domain
                : !wire.getTypeParameters().isEmpty() ? wire : null;
    if (offender == null) {
      return true;
    }
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "'" + offender.getSimpleName() + "' is generic, which this mapper does not support.",
        "The generated Impl names the mapped types directly; type parameters would leave it"
            + " referencing undeclared type variables.",
        "Map concrete types here; generic mappings (concrete instantiations and threaded specs)"
            + " are currently supported for record-record pairs only.");
    return false;
  }

  /**
   * Record pairs may use generic records two ways: concretely instantiated ({@code
   * MappingSpec<Page<User>, PageDto<UserDto>>}) or threaded through the spec's own type parameters
   * ({@code PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>>}); the two compose recursively
   * per argument. Raw uses, wildcards and foreign type variables stay diagnosed (the
   * foreign-variable check is defensive — a direct extends clause can only name the spec's own
   * variables).
   */
  private boolean checkGenericsSupported(
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      TypeElement wire,
      DeclaredType wireDeclared) {
    return supportedUse(spec, domain, domainDeclared, "domain")
        && supportedUse(spec, wire, wireDeclared, "wire");
  }

  /**
   * A record use is supported when every type argument is either a concrete type or one of the
   * spec's own type parameters (identity-threaded generics): {@code PageMapping<T> extends
   * MappingSpec<Page<T>, PageDto<T>>} threads {@code T} through {@code build}/{@code parse} and the
   * emitted optics. Raw uses and wildcards stay diagnosed; an argument mixing a spec variable
   * inside a concrete shape ({@code Page<List<T>>}) is threaded fine because the check is recursive
   * on both sides.
   */
  private boolean supportedUse(
      TypeElement spec, TypeElement record, DeclaredType used, String side) {
    if (record.getTypeParameters().isEmpty()) {
      return true;
    }
    if (used.getTypeArguments().size() != record.getTypeParameters().size()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'" + record.getSimpleName() + "' is used raw.",
          "A generic record maps only through named type arguments; a raw use leaves every"
              + " parameterised component unresolvable.",
          "Declare the type arguments: '" + record.getSimpleName() + "<...>'.");
      return false;
    }
    for (TypeMirror argument : used.getTypeArguments()) {
      if (!supportedArgument(spec, argument)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "'" + used + "' is not a supported instantiation.",
            "A type argument must be a concrete type or one of the spec's own type parameters;"
                + " wildcards and raw nested uses leave component types unresolvable.",
            "Use concrete arguments on the "
                + side
                + " (e.g. '"
                + record.getSimpleName()
                + "<User>'), or thread the spec's own type parameters (e.g. 'interface"
                + " PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>>').");
        return false;
      }
    }
    return true;
  }

  /**
   * A spec's own type variable threads through; anything else must be concrete. The two compose
   * recursively, so {@code Page<List<T>>} is a supported threaded use.
   */
  private boolean supportedArgument(TypeElement spec, TypeMirror argument) {
    // An unresolved argument steps aside rather than reading as unsupported. Only a class file can
    // still carry one here, naming a type missing from the classpath: a spec naming one from source
    // waits for a later round. ErrorType extends DeclaredType, so this check runs before the
    // pattern switch.
    if (argument.getKind() == TypeKind.ERROR) {
      return true;
    }
    if (argument instanceof javax.lang.model.type.TypeVariable variable) {
      return spec.getTypeParameters().contains(variable.asElement());
    }
    return switch (argument) {
      case javax.lang.model.type.ArrayType array ->
          supportedArgument(spec, array.getComponentType());
      case DeclaredType declared ->
          ((TypeElement) declared.asElement()).getTypeParameters().size()
                  == declared.getTypeArguments().size()
              && declared.getTypeArguments().stream().allMatch(a -> supportedArgument(spec, a));
      default -> argument.getKind().isPrimitive();
    };
  }

  /**
   * A component's type as seen under the spec's instantiation of its record. The substitution runs
   * only for generic records, so concrete pairs stay on the exact pre-instantiation path (and never
   * rely on {@code asMemberOf} accepting record components).
   *
   * <p>Reading the declaration's parameters rather than the site's arguments is what makes that
   * true, and it is safe only because a raw domain never gets here: {@code @GenerateMapping}
   * refuses one at the declaration. See {@link ProcessorUtils#memberOf} for why this reader and the
   * two in {@code SpecInterfaceAnalyser} answer a raw site differently on purpose.
   */
  private TypeMirror componentType(DeclaredType owner, RecordComponentElement component) {
    return ((TypeElement) owner.asElement()).getTypeParameters().isEmpty()
        ? component.asType()
        : processingEnv.getTypeUtils().asMemberOf(owner, component);
  }

  private void processSpec(Element element, List<RegisteredSpec> registry) {
    if (element.getKind() != ElementKind.INTERFACE) {
      Diagnostics.error(
          processingEnv.getMessager(),
          element,
          TAG,
          "can only be applied to interfaces, but '"
              + element.getSimpleName()
              + "' is a "
              + element.getKind().toString().toLowerCase(Locale.ROOT)
              + ".",
          "The mapping is specified as an interface extending MappingSpec<Domain, Wire>.",
          "Declare 'interface " + element.getSimpleName() + " extends MappingSpec<Domain, Wire>'.");
      return;
    }
    TypeElement spec = (TypeElement) element;

    // A spec extending UpdateSpec<Domain, Wire> opts into sparse null-as-absent PATCH: it
    // emits updateFrom() and nothing else, and register() never offers it for nesting, so an
    // UpdateSpec has no parse to nest. A spec declaring both supertypes is refused before either
    // tier runs: register() would otherwise have to guess which one the Impl carries.
    DeclaredType updateSuper = findUpdateSpec(spec);
    if (updateSuper != null) {
      if (!checkOneTier(spec, updateSuper)) {
        return;
      }
      processUpdateSpec(spec, updateSuper, registry);
      return;
    }

    DeclaredType specSuper = findMappingSpec(spec);
    if (specSuper == null || specSuper.getTypeArguments().size() != 2) {
      Diagnostics.error(
          processingEnv.getMessager(),
          element,
          TAG,
          "'" + spec.getSimpleName() + "' does not directly extend MappingSpec<Domain, Wire>.",
          "The two type arguments name the domain and wire records being mapped.",
          "Add 'extends MappingSpec<Domain, Wire>' with both type arguments.");
      return;
    }

    if (!checkMixins(spec)) {
      return;
    }

    TypeElement sealedDomain = asSealed(specSuper.getTypeArguments().get(0));
    TypeElement sealedWire = asSealed(specSuper.getTypeArguments().get(1));
    if (!validateSpecMethods(
        spec,
        sealedDomain != null && sealedWire != null,
        specSuper.getTypeArguments().get(0),
        specSuper.getTypeArguments().get(1))) {
      return;
    }
    if (sealedDomain != null && sealedWire != null) {
      if (!checkNotGeneric(spec, sealedDomain, sealedWire)) {
        return;
      }
      if (!checkNoSealedVocabulary(spec)) {
        return;
      }
      processSealedSpec(spec, registry, sealedDomain, sealedWire);
      return;
    }

    TypeMirror domainArg = specSuper.getTypeArguments().get(0);
    TypeMirror wireArg = specSuper.getTypeArguments().get(1);
    TypeElement domain = asRecord(domainArg);
    if (domain == null) {
      reportUnsupportedDomain(spec, domainArg);
      return;
    }
    DeclaredType domainDeclared = (DeclaredType) domainArg;

    // The wire may be a record (component-wise) or a bean-shaped class (getters/setters).
    TypeElement wireRecord = asRecord(wireArg);
    WireShape wireShape;
    TypeMirror wireUsed;
    if (wireRecord != null) {
      DeclaredType wireDeclared = (DeclaredType) wireArg;
      if (!checkGenericsSupported(spec, domain, domainDeclared, wireRecord, wireDeclared)) {
        return;
      }
      wireShape = recordWireShape(wireRecord, wireDeclared);
      wireUsed = wireDeclared;
    } else {
      TypeElement wireBean = asBean(wireArg);
      if (wireBean == null) {
        reportUnsupportedWire(spec, wireArg);
        return;
      }
      if (!checkNotGeneric(spec, domain, wireBean)) {
        return;
      }
      BeanPropertyAnalyser analyser = new BeanPropertyAnalyser(processingEnv);
      WireShape.BeanShape bean = analyser.analyse(spec, wireBean, TAG);
      if (bean == null || !checkCollectionGettersFillable(spec, bean)) {
        return;
      }
      if (bean.direction() != WireShape.Direction.BIDIRECTIONAL) {
        noteOneDirectional(spec, bean, analyser);
      }
      wireShape = bean;
      wireUsed = wireBean.asType();
    }

    // Flattened components come first: their inner components join the names leaves and renames
    // may bind to.
    List<Flattened> flattened = collectFlattened(spec, domain, domainDeclared, wireShape);
    if (flattened == null) {
      return;
    }
    // Asked after flattening, which refuses a group spread across a bean, so a flattened component
    // is never told to take an accessor of its record's type.
    Set<String> unmapped = unmappedNames(spec, wireShape);
    if (unmapped == null) {
      return;
    }
    if (wireShape instanceof WireShape.BeanShape bean
        && !checkAccessorsPair(spec, domain, bean, unmapped)) {
      return;
    }
    Set<String> flattenedInner =
        flattened.stream().flatMap(group -> group.inner().stream()).collect(Collectors.toSet());

    if (!checkLocalLeavesBind(spec, domain, flattenedInner)) {
      return;
    }

    Map<String, String> renames = collectRenames(spec, domain, wireShape, flattened);
    if (renames == null) {
      return;
    }

    if (!checkFlattenedNamesFree(spec, wireShape, renames, flattened)) {
      return;
    }

    if (!checkBridgesApply(spec, domain, domainDeclared, wireShape, renames, flattened)) {
      return;
    }

    if (!checkMapKeysApply(
        spec,
        domain,
        domainDeclared,
        flattened,
        name -> bridgesOnWire(spec, wireShape, renames, name))) {
      return;
    }

    List<DerivedField> derived =
        collectDerived(spec, domain, domainDeclared, wireShape, renames, flattened);
    if (derived == null) {
      return;
    }

    // A one-way bean has no lossiness to speak of: nothing is read back after a build, and nothing
    // is written after a parse, so it maps its one direction whatever its width.
    if (wireShape.direction() == WireShape.Direction.PARSE_ONLY) {
      List<Correspondence> parsed =
          classifyDomain(
              spec,
              registry,
              domain,
              domainDeclared,
              wireShape,
              renames,
              flattened,
              WireShape.Direction.PARSE_ONLY);
      if (parsed == null) {
        return;
      }
      writeParseOnlyImpl(spec, domainDeclared, wireShape, wireUsed, parsed);
      return;
    }
    if (wireShape.direction() == WireShape.Direction.BUILD_ONLY) {
      List<Correspondence> built =
          classifyWire(
              spec,
              registry,
              domain,
              domainDeclared,
              wireShape,
              renames,
              derived,
              WireShape.Direction.BUILD_ONLY);
      if (built == null) {
        return;
      }
      writeBuildOnlyImpl(spec, domainDeclared, wireShape, wireUsed, built);
      return;
    }

    if (wireShape.componentCount() - derived.size() < wireSlots(domain, flattened)) {
      if (!flattened.isEmpty()) {
        reportProjectionWithFlattened(spec, domain, wireShape, flattened);
        return;
      }
      if (!derived.isEmpty()) {
        reportProjectionWithDerived(spec, domain, wireShape, derived);
        return;
      }
      List<Correspondence> projection =
          classifyWire(
              spec,
              registry,
              domain,
              domainDeclared,
              wireShape,
              renames,
              List.of(),
              WireShape.Direction.BIDIRECTIONAL);
      if (projection == null) {
        return;
      }
      // A write-back that can fail is no lens: it maps as the validated patch tier instead.
      if (totalReads(projection, wireShape)) {
        writeLensImpl(spec, domain, domainDeclared, wireShape, wireUsed, projection);
        return;
      }
      writePatchImpl(spec, domain, domainDeclared, wireShape, wireUsed, projection);
      return;
    }

    List<Correspondence> correspondences =
        classify(spec, registry, domain, domainDeclared, wireShape, renames, derived, flattened);
    if (correspondences == null) {
      return;
    }
    writeImpl(spec, domain, domainDeclared, wireShape, wireUsed, correspondences);
  }

  /**
   * Processes a sparse-update spec ({@code extends UpdateSpec<Domain, Wire>}). The wire must be a
   * bean-shaped class (a record cannot signal absence) and the domain a record; sealed pairs are
   * rejected (dispatch has no sparse meaning). Every present (non-null) wire property folds into an
   * {@code Update} via {@code Edits.accumulate}; absent properties leave the domain unchanged. Only
   * {@code updateFrom} is emitted — no {@code build}, {@code parse}, or {@code as*} tier.
   */
  private void processUpdateSpec(
      TypeElement spec, DeclaredType updateSuper, List<RegisteredSpec> registry) {
    if (updateSuper.getTypeArguments().size() != 2) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'" + spec.getSimpleName() + "' does not extend UpdateSpec<Domain, Wire>.",
          "The two type arguments name the domain record and the bean-shaped PATCH wire.",
          "Add 'extends UpdateSpec<Domain, Wire>' with both type arguments.");
      return;
    }
    if (!checkMixins(spec)) {
      return;
    }

    TypeMirror domainArg = updateSuper.getTypeArguments().get(0);
    TypeMirror wireArg = updateSuper.getTypeArguments().get(1);

    if (asSealed(domainArg) != null || asSealed(wireArg) != null) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "a sparse UpdateSpec cannot map a sealed hierarchy.",
          "A sparse update edits the fields of one record; a sealed mapping dispatches over whole"
              + " values, and a wire subtype cannot choose the domain variant to patch at fold"
              + " time.",
          "Declare one UpdateSpec per concrete record pair.");
      return;
    }

    if (!validateSpecMethods(spec, false, domainArg, wireArg)) {
      return;
    }
    if (!checkNoDerivedFields(spec)) {
      return;
    }
    if (!checkNoBridges(spec)) {
      return;
    }

    TypeElement domain = asRecord(domainArg);
    if (domain == null) {
      reportUpdateDomainNotRecord(spec, domainArg);
      return;
    }
    if (!checkLocalLeavesBind(spec, domain, Set.of())) {
      return;
    }
    // The sparse tier refuses the bridge on its own terms, so no Optional here carries a Map to
    // key.
    if (!checkMapKeysApply(spec, domain, (DeclaredType) domainArg, List.of(), _ -> false)) {
      return;
    }

    // A record wire cannot express "absent" (every component is always present), so sparse PATCH is
    // a bean-only shape; a non-bean, non-record wire is rejected with a sparse-specific message.
    if (asRecord(wireArg) != null) {
      reportRecordWireOnUpdate(spec, domain, asRecord(wireArg));
      return;
    }
    TypeElement wireBean = asBean(wireArg);
    if (wireBean == null) {
      reportUpdateWireNotBean(spec, wireArg);
      return;
    }
    if (!checkNotGeneric(spec, domain, wireBean)) {
      return;
    }

    WireShape.BeanShape wireShape =
        new BeanPropertyAnalyser(processingEnv).analysePatch(spec, wireBean, TAG);
    if (wireShape == null || !checkPatchBeanTwoWay(spec, wireShape)) {
      return;
    }
    Set<String> unmapped = unmappedNames(spec, wireShape);
    if (unmapped == null
        || !checkPatchAccessorsPair(spec, domain, wireShape, unmapped)
        || !checkCollectionGettersCarryAbsence(spec, wireShape)) {
      return;
    }
    Map<String, String> renames = collectRenames(spec, domain, wireShape, List.of());
    if (renames == null) {
      return;
    }
    // Asked once the wire and the sources it already has are both known: whether an inherited
    // @Flatten marker is inert or refused turns on which of the group's inner properties this
    // PATCH bean carries with nothing else to fill them.
    if (!checkNoFlattened(spec, domain, (DeclaredType) domainArg, wireShape, renames)) {
      return;
    }

    List<UpdateEdit> edits = classifyUpdate(spec, domain, wireShape, renames, registry);
    if (edits == null) {
      return;
    }
    writeUpdateImpl(spec, domain, (DeclaredType) domainArg, wireShape, edits);
  }

  /**
   * One folded edit of a sparse update: the domain component it writes, the wire property it reads,
   * the {@link Kind} selecting the parse method for the emission (exactly as {@link Correspondence}
   * does for the dense tiers), and — when the present value parses through a prism — the {@code
   * ValidatedPrism} expression (a whole-component leaf, an element leaf lifted over its container,
   * or a nested spec's {@code asValidatedPrism()}). A plain identity edit folds as {@code
   * Edit.setIfPresent}; every other edit folds as {@code Edit.parseIfPresent(...).at(name)}, the
   * parser chosen by kind ({@code parse}, {@code parseAll}, {@code parseValues}, the
   * element-of-Optional lambda, or — for identity containers, which carry no prism yet still parse
   * — the emitted null-scan helper).
   */
  private record UpdateEdit(
      String domainName,
      String wireName,
      Kind kind,
      CodeBlock prism,
      TypeName domainElement,
      CodeBlock valuePrism) {

    static UpdateEdit identity(String domainName, String wireName, Kind kind) {
      return new UpdateEdit(domainName, wireName, kind, null, null, null);
    }

    static UpdateEdit validated(String domainName, String wireName, Kind kind, CodeBlock prism) {
      return new UpdateEdit(domainName, wireName, kind, prism, null, null);
    }

    /**
     * A container-lifted edit, taking the dense tiers' correspondence whole: the sparse tier
     * borrows their vocabulary, so it must borrow everything the emission reads from it.
     */
    static UpdateEdit lifted(String domainName, Correspondence c) {
      return new UpdateEdit(
          domainName, c.wireName(), c.kind(), c.prism(), c.domainElement(), c.valuePrism());
    }

    boolean parsed() {
      return prism != null || kind == Kind.IDENTITY_ELEMENTS || kind == Kind.IDENTITY_MAP;
    }
  }

  /**
   * Classifies each wire property against the domain for a sparse update. Coverage is one-sided:
   * every wire property must map to a domain component (a dangling wire property is an error), but
   * a domain component with no wire property is simply never edited. Each property matches by an
   * explicit leaf (named after the domain component) — whole-component first, then an element leaf
   * lifted over a {@code List}, {@code Optional} or {@code Map} value, exactly the dense tiers'
   * vocabulary — or by identity: the same type, or a wrapper of a primitive domain component (so an
   * {@code Integer} property can patch an {@code int} field). A primitive wire property can never
   * be absent and is rejected. Returns null after reporting.
   *
   * <p><b>Tie-break.</b> A whole-container leaf ({@code ValidatedPrism<List<S>, List<A>>}) wins
   * over the element interpretation, as the more specific declaration — the same order the dense
   * tiers check. Genuine ambiguity cannot arise: a leaf is one zero-parameter method with one
   * return type, and no {@code ValidatedPrism}'s type arguments can match a container pair and its
   * own element pair at once.
   *
   * <p>Identity containers keep wholesale replacement but gain the dense tiers' null scan: a
   * present same-typed {@code List}/{@code Map} passes by reference only when no element/value is
   * null; a null inside is a located, accumulating invalid at its index/key, so the located-null
   * doctrine holds on the sparse tier too. The scan requires a properly parameterised container
   * (see {@link #sparseIdentityKind}); raw and wildcard-argument containers stay plain identity
   * writes.
   */
  private List<UpdateEdit> classifyUpdate(
      TypeElement spec,
      TypeElement domain,
      WireShape wire,
      Map<String, String> renames,
      List<RegisteredSpec> registry) {
    Map<String, String> wireToDomain = new LinkedHashMap<>();
    renames.forEach((domainName, wireName) -> wireToDomain.put(wireName, domainName));

    List<UpdateEdit> edits = new ArrayList<>();
    Map<String, String> claimedBy = new LinkedHashMap<>();
    for (WireShape.WireComponent property : wire.components()) {
      String domainName = wireToDomain.getOrDefault(property.name(), property.name());
      RecordComponentElement domainComp =
          domain.getRecordComponents().stream()
              .filter(c -> c.getSimpleName().contentEquals(domainName))
              .findFirst()
              .orElse(null);
      if (domainComp == null) {
        reportDanglingWireProperty(spec, domain, property);
        return null;
      }
      // One wire property per domain component: a same-named property and a rename can otherwise
      // both land on one component, silently emitting two writes to the same slot.
      if (claimedBy.containsKey(domainName)) {
        reportDuplicateDomainTarget(
            spec, domain, domainName, claimedBy.get(domainName), property.name());
        return null;
      }
      claimedBy.put(domainName, property.name());
      if (property.type().getKind().isPrimitive()) {
        reportPrimitiveProperty(spec, property);
        return null;
      }
      TypeMirror wireType = property.type();
      TypeMirror domainType = domainComp.asType();

      // An explicit whole-component leaf wins even over a same-typed match, so it can validate or
      // normalise a copied field; on a container pair it also beats the element interpretation,
      // as the more specific declaration.
      ExecutableElement leaf = findLeaf(spec, domainName, wireType, domainType);
      if (leaf != null) {
        if (!checkKeyLeafReached(spec, domainName, leaf, wireType, domainType, LeafSite.PLAIN)) {
          return null;
        }
        edits.add(
            UpdateEdit.validated(
                domainName,
                property.name(),
                Kind.LEAF,
                CodeBlock.of("$L()", leaf.getSimpleName())));
        continue;
      }

      // An element leaf lifted over a List, Optional or Map value — the same vocabulary the dense
      // tiers accept, so one mix-in serves a full spec and an update spec alike. Checked before
      // identity so a normalising element ValidatedPrism<X, X> still runs on a same-typed
      // container; replacement stays wholesale, only element validation and location improve.
      Correspondence containerLeaf =
          containerLeafCorrespondence(
              spec, domainName, property.name(), wireType, domainType, LeafSite.PLAIN);
      if (containerLeaf != null) {
        edits.add(UpdateEdit.lifted(domainName, containerLeaf));
        continue;
      }

      // Same type (or a wrapper of a primitive component) — including a same-typed List, Map or
      // nested record — writes the present value straight in (wholesale replacement); identity
      // containers additionally scan for null elements/values, as in the dense tiers.
      if (identityMatch(wireType, domainType)) {
        edits.add(UpdateEdit.identity(domainName, property.name(), sparseIdentityKind(domainType)));
        continue;
      }

      // A domain Optional<T> component under a non-Optional wire property is the null-as-absent
      // bridge shape (a same-typed Optional wire was matched by identity, a differently-typed one
      // by an element leaf above), which sparseness cannot express: null already means "leave
      // unchanged", so "set to empty" has no encoding. An Optional-typed wire property CAN express
      // emptiness (a present empty Optional), so a leafless Optional pair falls through to the
      // no-update-source diagnostic, whose fix names the element leaf.
      if (containerElement(domainType, "java.util.Optional") != null
          && containerElement(wireType, "java.util.Optional") == null) {
        reportOptionalBridge(spec, domain, property, domainComp);
        return null;
      }

      // A nested record patched wholesale through its own full mapping spec's asValidatedPrism().
      PrismResolution nested =
          resolveNestedSpec(
              spec, registry, domainName, wireType, domainType, WireShape.Direction.PARSE_ONLY);
      if (nested.ambiguous()) {
        return null;
      }
      if (nested.accessor() != null) {
        edits.add(UpdateEdit.validated(domainName, property.name(), Kind.LEAF, nested.accessor()));
        continue;
      }

      reportNoUpdateSource(spec, registry, domain, wire, property, domainComp);
      return null;
    }
    return edits;
  }

  /**
   * A locally declared {@code default} method returning {@code Getter} (a derived field) has no
   * sparse meaning: a derived field feeds {@code build()}, which the sparse tier does not emit.
   *
   * <p>An <em>inherited</em> one stays inert, like every other inherited vocabulary member that
   * binds to nothing here: one mix-in serves a full spec and its PATCH sibling, which is the whole
   * point of a shared vocabulary, and the sparse tier simply never consults the method. Refusing it
   * would report at the mix-in, where both prescribed fixes break the full spec that needs it.
   */
  private boolean checkNoDerivedFields(TypeElement spec) {
    for (ExecutableElement method : specMembers(spec)) {
      if (!declaredLocally(method, spec)
          || !isDerivedCandidate(processingEnv.getTypeUtils(), spec, method)) {
        continue;
      }
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "the derived-field method '"
              + method.getSimpleName()
              + "' has no meaning on a sparse UpdateSpec.",
          "A derived field feeds build(); a sparse update only writes present wire properties into"
              + " the domain, so there is nothing to derive.",
          "Remove the method, move it to a mix-in a full MappingSpec also extends, or use a full"
              + " MappingSpec here if you need a total build().");
      return false;
    }
    return true;
  }

  /**
   * A locally declared {@code @OptionalBridge} has no sparse meaning: the bridge maps a {@code
   * null} to {@code Optional.empty()}, and a sparse update has already given {@code null} the
   * opposite reading - absent, leave unchanged. The two contracts cannot both hold on one property,
   * and the sparse one is what the spec opted into by extending {@code UpdateSpec}.
   *
   * <p>An <em>inherited</em> one stays inert, like every other inherited vocabulary member that
   * binds to nothing here: one mix-in serves a full spec and its PATCH sibling, which is the whole
   * point of a shared vocabulary, and the sparse tier simply never consults the annotation.
   */
  private boolean checkNoBridges(TypeElement spec) {
    for (ExecutableElement method : specMembers(spec)) {
      if (method.getAnnotation(OptionalBridge.class) == null || !declaredLocally(method, spec)) {
        continue;
      }
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "@OptionalBridge on '"
              + method.getSimpleName()
              + "' has no meaning on a sparse UpdateSpec.",
          "The bridge reads a null wire value as Optional.empty(); a sparse update already reads"
              + " it as absent - leave unchanged - so 'set to empty' has no encoding through a"
              + " plain property.",
          "Remove the annotation, or move the marker to a mix-in a full MappingSpec also extends,"
              + " and declare the PATCH property as Optional<T>, with the field defaulting to null"
              + " rather than Optional.empty(), if the client needs to set the field empty.");
      return false;
    }
    return true;
  }

  /**
   * Whether a present wire value can be written straight into the domain component: the same type,
   * or a wrapper of a primitive domain component (unboxing identity). The wire property is never a
   * primitive here — a primitive property is rejected before this by {@link #classifyUpdate}.
   */
  private boolean identityMatch(TypeMirror wireType, TypeMirror domainType) {
    if (processingEnv.getTypeUtils().isSameType(wireType, domainType)) {
      return true;
    }
    if (domainType.getKind().isPrimitive()) {
      TypeMirror boxed =
          processingEnv.getTypeUtils().boxedClass((PrimitiveType) domainType).asType();
      return processingEnv.getTypeUtils().isSameType(wireType, boxed);
    }
    return false;
  }

  /**
   * The domain of a sparse UpdateSpec is not a record (a sealed one was rejected earlier). Unlike
   * the full mapper's domain diagnostic, this references the positional record rebuild (there is no
   * {@code parse}), and does not offer a sealed hierarchy (the sparse tier rejects those).
   */
  private void reportUpdateDomainNotRecord(TypeElement spec, TypeMirror domainArg) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "the UpdateSpec domain type argument '" + domainArg + "' is not a record.",
        "A sparse update rebuilds the domain positionally through its canonical constructor, so the"
            + " domain must be a record; only the wire may be bean-shaped.",
        "Use a record for the domain, mapping the bean as the wire.");
  }

  /**
   * The wire of a sparse UpdateSpec is neither a record (rejected earlier) nor a bean. Unlike the
   * full mapper's wire diagnostic, the fix names only the bean-shaped PATCH DTO — a record wire is
   * rejected here, so offering one (as the full mapper does) would send the user in a circle.
   */
  private void reportUpdateWireNotBean(TypeElement spec, TypeMirror wireArg) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "the UpdateSpec wire type argument '" + wireArg + "' is not a bean-shaped class.",
        "A sparse update reads the present properties through the wire's getters, so the wire must"
            + " be a bean (a class with getters and setters or a builder); a record component is"
            + " always present, so a record cannot express an absent field.",
        "Use a bean-shaped PATCH DTO (wrapper-typed getters/setters).");
  }

  /**
   * A record wire on an UpdateSpec: records cannot express an absent (null-as-not-provided) field.
   */
  private void reportRecordWireOnUpdate(TypeElement spec, TypeElement domain, TypeElement wire) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "the wire '"
            + wire.getSimpleName()
            + "' is a record, which a sparse UpdateSpec cannot map.",
        "Sparse PATCH reads null as 'not provided, leave unchanged', but a record component is"
            + " always present, so absence is inexpressible.",
        "Use a bean-shaped PATCH DTO (wrapper-typed getters/setters), or a full MappingSpec<"
            + domain.getSimpleName()
            + ", "
            + wire.getSimpleName()
            + "> if you meant a total mapping.");
  }

  /**
   * A primitive wire property is always present, so it can never carry the null-as-absent signal.
   */
  private void reportPrimitiveProperty(TypeElement spec, WireShape.WireComponent property) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "the wire property '" + property.name() + "' is primitive and can never be absent.",
        "An all-absent PATCH body must fold to the identity update, but a primitive property always"
            + " carries a value (its default), so its 'absent' state cannot be distinguished.",
        "Declare '" + property.name() + "' on the PATCH DTO as " + boxed(property.type()) + ".");
  }

  /**
   * A wire property with no domain component to write into (one-sided coverage still requires one).
   */
  private void reportDanglingWireProperty(
      TypeElement spec, TypeElement domain, WireShape.WireComponent property) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "the wire property '"
            + property.name()
            + "' names no component of "
            + domain.getSimpleName()
            + ".",
        "A sparse update writes each present wire property into a domain component; there is no"
            + " build step for a derived field to feed. Found on "
            + domain.getSimpleName()
            + ": "
            + wireNames(domain.getRecordComponents())
            + ".",
        "Add a @MapField rename to a domain component, or remove the property.");
  }

  /**
   * A domain {@code Optional<T>} component bridged from a non-Optional wire property under
   * sparseness: null already means absent, so "set to empty" is inexpressible (a plain property has
   * only null and a value, one state short of JSON Merge Patch's three). The remedy names the wire
   * property's own type, so an element leaf the author already has lifts over the Optional instead
   * of being bypassed by an identity match. An Optional-typed wire property never lands here — it
   * patches by identity or elementwise through an element leaf, and leafless it reaches the
   * no-update-source diagnostic instead.
   */
  private void reportOptionalBridge(
      TypeElement spec,
      TypeElement domain,
      WireShape.WireComponent property,
      RecordComponentElement domainComp) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "the wire property '"
            + property.name()
            + "' bridges the domain Optional component "
            + domain.getSimpleName()
            + "."
            + domainComp.getSimpleName()
            + " ("
            + domainComp.asType()
            + "), which a sparse update cannot express.",
        "Under null-as-absent a null property means 'leave unchanged', so a plain property has no"
            + " state left to set the component to an empty Optional.",
        "Declare '"
            + property.name()
            + "' as Optional<"
            + property.type()
            + "> with the field defaulting to null, not Optional.empty(), which would read every"
            + " omitted property as a clear: null then leaves the component unchanged, and a present"
            + " empty Optional sets it empty.");
  }

  /** Two wire properties resolve to the same domain component (a same-named one and a rename). */
  private void reportDuplicateDomainTarget(
      TypeElement spec, TypeElement domain, String domainName, String first, String second) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "the wire properties '"
            + first
            + "' and '"
            + second
            + "' both write "
            + domain.getSimpleName()
            + "."
            + domainName
            + ".",
        "Each domain component takes at most one wire source, or the update would write the slot"
            + " twice (last write wins).",
        "Point the @MapField rename at a distinct component, or drop one of the properties.");
  }

  /**
   * A wire property matches a domain component by name but neither by type, through a leaf, nor
   * through a spec serving a parse; a spec that maps the pair without one is named, as a dense
   * lookup names it.
   */
  private void reportNoUpdateSource(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
      WireShape wire,
      WireShape.WireComponent property,
      RecordComponentElement domainComp) {
    // An element leaf may delegate to a nested Impl, so a one-directional one is named over the
    // pair such a leaf is offered over.
    LeafOffer offer =
        leafOffer(
            spec, domainComp.getSimpleName().toString(), property.type(), domainComp.asType());
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "the wire property '"
            + property.name()
            + "' ("
            + property.type()
            + ") cannot be written into "
            + domain.getSimpleName()
            + "."
            + domainComp.getSimpleName()
            + " ("
            + domainComp.asType()
            + ").",
        "A sparse update writes a present property by identity (same type, or a wrapper of a"
            + " primitive component), through a leaf named after the domain component, or — for a"
            + " pair declared as exactly List, Set, an array, Optional or Map — through an"
            + " element leaf lifted"
            + " over the container."
            + leafNearMissHint(
                spec, domainComp.getSimpleName().toString(), property.type(), domainComp.asType())
            + (domainComp.asType().getKind().isPrimitive() ? PRIMITIVE_REASON : "")
            + unusableSpecHint(
                registry,
                offer.wire(),
                offer.domain(),
                WireShape.Direction.PARSE_ONLY,
                "be nested in a sparse update, which parses what it reads"),
        domainComp.asType().getKind().isPrimitive()
            ? primitiveUpdateFix(spec, domain, wire, property, domainComp)
            : updateLeafSuggestion(spec, property, domainComp));
  }

  /**
   * The sparse fix for a primitive domain component, which no leaf can target ({@link
   * #PRIMITIVE_REASON}). The property cannot be primitive either, since a PATCH property must be
   * able to be absent; its wrapper, though, writes straight into the component. Otherwise the
   * component becomes the wrapper and a leaf converts into that.
   */
  private String primitiveUpdateFix(
      TypeElement spec,
      TypeElement domain,
      WireShape wire,
      WireShape.WireComponent property,
      RecordComponentElement domainComp) {
    String name = domainComp.getSimpleName().toString();
    TypeMirror wrapper = boxed(domainComp.asType());
    ExecutableElement leaf = findLeaf(spec, name, property.type(), wrapper);
    return "Declare '"
        + property.name()
        + "' on '"
        + wire.element().getSimpleName()
        + "' as "
        + wrapper
        + ", which a sparse update writes straight into the "
        + domainComp.asType()
        + " component, or declare '"
        + domain.getSimpleName()
        + "."
        + name
        + "' as "
        + wrapper
        + (leaf != null
            ? ", which the leaf '" + leaf.getSimpleName() + "()' then converts."
            : " and add a leaf 'default ValidatedPrism<"
                + property.type()
                + ", "
                + wrapper
                + "> "
                + name
                + "()'.");
  }

  /**
   * The fix line for {@link #reportNoUpdateSource}: for a container pair the element leaf comes
   * first (the shared-vocabulary form, lifted over the container), with the whole-container leaf as
   * the more specific alternative; scalar pairs keep the whole-component suggestion. The element
   * pair is {@link #liftedPair}'s, so the sparse tier offers the leaf the dense tiers offer.
   */
  private String updateLeafSuggestion(
      TypeElement spec, WireShape.WireComponent property, RecordComponentElement domainComp) {
    TypeMirror wireType = property.type();
    TypeMirror domainType = domainComp.asType();
    String name = domainComp.getSimpleName().toString();
    TypeMirror[] lifted = liftedPair(spec, name, wireType, domainType);
    // A same-named default method is the near miss the why names, which a leaf replaces.
    String declare =
        sameNamedDefault(spec, name) == null ? "Declare " : "Replace '" + name + "()' with ";
    if (lifted != null) {
      return declare
          + "an element leaf 'default ValidatedPrism<"
          + lifted[0]
          + ", "
          + lifted[1]
          + "> "
          + name
          + "()' (lifted over the container; it may delegate to a nested Impl's"
          + " asValidatedPrism()), a whole-container leaf 'default ValidatedPrism<"
          + wireType
          + ", "
          + domainType
          + "> "
          + name
          + "()', or align the types.";
    }
    return declare
        + "a leaf 'default ValidatedPrism<"
        + wireType
        + ", "
        + domainType
        + "> "
        + name
        + "()', or align the types.";
  }

  /**
   * Emits the sparse-update Impl: a single {@code updateFrom(Wire) : Edits.Accumulated<Domain>}
   * that folds each present wire property into an {@code Update}. Identity edits use {@code
   * Edit.setIfPresent} against an inline {@code Setter} that rebuilds the record; validated edits
   * use {@code Edit.parseIfPresent(...).at(name)} with the parse method their {@link Kind} selects
   * ({@code parse}, element-lifted {@code parseAll}/{@code parseValues}, the element-of-Optional
   * lambda, or the identity-container null scan), so a present-but-invalid value accumulates a
   * located {@code FieldError} — {@code phones.1: ...} for a bad second element. No {@code
   * build}/{@code parse}/{@code as*} tier is emitted.
   */
  private void writeUpdateImpl(
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      List<UpdateEdit> edits) {
    ClassName specName = ClassName.get(spec);
    ClassName implName = implClassName(spec);
    ClassName domainClass = ClassName.get(domain);
    TypeName wireName = TypeName.get(wire.element().asType());

    if (!checkNoEmittedCollisions(
        spec,
        "a sparse update",
        List.of(EmittedMember.of("updateFrom", wire.element().asType())))) {
      return;
    }

    TypeName accumulatedReturn =
        ParameterizedTypeName.get(ACCUMULATED, TypeName.get(domain.asType()));

    CodeBlock.Builder call = CodeBlock.builder().add("return $T.accumulate(", EDITS);
    boolean first = true;
    for (UpdateEdit edit : edits) {
      call.add(first ? "\n" : ",\n");
      first = false;
      CodeBlock setter = setterExpr(domainClass, domain, edit.domainName());
      CodeBlock read = wireRead(wire, edit.wireName());
      if (edit.parsed()) {
        CodeBlock parser =
            switch (edit.kind()) {
              case OPTIONAL -> elementOfOptionalParser(edit.prism());
              case IDENTITY_ELEMENTS -> CodeBlock.of("$T::hkj$$allPresent", implName);
              case IDENTITY_MAP -> CodeBlock.of("$T::hkj$$valuesPresent", implName);
              // LEAF and the container kinds, through the call the dense leg makes; the dense-only
              // kinds (OPTIONAL_BRIDGE, DERIVED) and plain IDENTITY are never constructed as parsed
              // sparse edits, and the Kind-canary test forces a deliberate arm here before any new
              // Kind can reach this switch.
              default ->
                  parseCall(edit.kind(), edit.prism(), edit.domainElement(), edit.valuePrism())
                      .asFunction();
            };
        call.add(
            "    $T.parseIfPresent($L, $L, $L).at($S)",
            EDIT,
            setter,
            read,
            parser,
            edit.domainName());
      } else {
        call.add("    $T.setIfPresent($L, $L)", EDIT, setter, read);
      }
    }
    call.add(")");

    MethodSpec updateFrom =
        MethodSpec.methodBuilder("updateFrom")
            .addAnnotations(pairSuppression(domainDeclared, wire))
            .addModifiers(Modifier.PUBLIC)
            .returns(accumulatedReturn)
            .addParameter(wireName, "wire")
            .addJavadoc(
                "Folds the present (non-null) properties of {@code wire} into an update: an absent"
                    + " property leaves the domain unchanged, a present one is set (or parsed"
                    + " through its leaf) and located on failure.\n")
            .addStatement("$T.requireNonNull(wire, $S)", OBJECTS, "wire must not be null")
            .addStatement("$L", call.build())
            .build();

    TypeSpec.Builder implBuilder =
        implSkeleton(
                spec,
                implName,
                specName,
                "Generated sparse PATCH write-back for {@link $T}: folds the present wire fields into"
                    + " an {@code Edits.Accumulated<Domain>}.\n",
                List.of())
            .addMethod(updateFrom);
    addMarkerStubs(implBuilder, spec);
    if (edits.stream().anyMatch(e -> scansUpdate(e, wire, "java.util.List"))) {
      implBuilder.addMethod(allPresentHelper());
    }
    if (edits.stream().anyMatch(e -> scansUpdate(e, wire, "java.util.Set"))) {
      implBuilder.addMethod(allPresentSetHelper());
    }
    if (edits.stream().anyMatch(e -> scansUpdateArray(e, wire))) {
      implBuilder.addMethod(allPresentArrayHelper());
    }
    if (edits.stream().anyMatch(e -> e.kind() == Kind.IDENTITY_MAP)) {
      implBuilder.addMethod(valuesPresentHelper());
    }
    writeFile(spec, specName.packageName(), implBuilder.build());
  }

  /**
   * An inline {@code Setter.fromGetSet(Domain::comp, (d, v) -> new Domain(...))} focusing one
   * domain component: the getter is the component accessor, and the writer rebuilds the record
   * positionally with the focused slot taken from {@code v}. Type inference fixes the focus type,
   * so no explicit generics are needed (a wrapper {@code v} auto-unboxes into a primitive slot).
   */
  private static CodeBlock setterExpr(
      ClassName domainClass, TypeElement domain, String focusedName) {
    CodeBlock.Builder args = CodeBlock.builder();
    boolean first = true;
    for (RecordComponentElement component : domain.getRecordComponents()) {
      if (!first) {
        args.add(", ");
      }
      first = false;
      String name = component.getSimpleName().toString();
      if (name.equals(focusedName)) {
        args.add("v");
      } else {
        args.add("d.$L()", name);
      }
    }
    return CodeBlock.of(
        "$T.fromGetSet($T::$L, (d, v) -> new $T($L))",
        SETTER,
        domainClass,
        focusedName,
        domainClass,
        args.build());
  }

  /**
   * Wraps a record wire in a {@link WireShape}: accessor is the component name, positional build.
   */
  private WireShape recordWireShape(TypeElement wire, DeclaredType declared) {
    List<WireShape.WireComponent> components =
        wire.getRecordComponents().stream()
            .map(
                c ->
                    new WireShape.WireComponent(
                        c.getSimpleName().toString(),
                        componentType(declared, c),
                        Optional.of(c.getSimpleName().toString())))
            .toList();
    return new WireShape.RecordShape(wire, components);
  }

  // Package-visible for the Kind-canary test, which pins the constant list so a new kind must
  // choose its emission before it can land: writeUpdateImpl's parser switch and the default arms of
  // buildCall, parseCall and bridgeParseLeg would otherwise give it a leaf's build and parse.
  enum Kind {
    IDENTITY,
    // Same-typed List/Set/array and Map components: copied by identity, but parse scans for
    // null elements/values so the located-null doctrine holds inside identity containers too.
    // A primitive array is plain IDENTITY: it has no element that could be null.
    IDENTITY_ELEMENTS,
    IDENTITY_MAP,
    LEAF,
    // An element-lifted List or Set. Both emit the same text: ValidatedPrism's parseAll and
    // buildAll are overloaded on the container, so the container's own type picks the form.
    ELEMENTS,
    // An element-lifted array. Its own kind, not ELEMENTS, because only it needs the array
    // constructor passed alongside: a generic array cannot be created without one.
    ARRAY,
    OPTIONAL,
    // A domain Optional<T> bridged to a nullable wire member T: empty <-> null/absent.
    OPTIONAL_BRIDGE,
    MAP,
    // A Map whose KEYS lift, values copied by identity; and one where both sides lift. The
    // receiver of both bulk forms is the key prism, with the value prism riding as an argument.
    MAP_KEYS,
    MAP_ENTRIES,
    DERIVED
  }

  /**
   * The flattened domain component an inner correspondence belongs to: its name and record type.
   * Membership is the one emission axis beside {@link Kind}; the sparse tier never forms a group
   * ({@code checkNoFlattened}) as the Kind canary makes it choose an emission for each kind.
   */
  private record Group(String name, TypeName type) {}

  /**
   * {@code prism} is an expression yielding the ValidatedPrism for every non-identity kind, except
   * {@code DERIVED}, where it yields the spec's Getter accessor instead. {@code group} is the
   * flattened domain component this correspondence is an inner component of, or null for a
   * component of the domain record itself; a group's members are contiguous in classification
   * order, and read from and assemble into the group's record rather than the domain. {@code
   * present} is the correspondence an {@code OPTIONAL_BRIDGE}'s present value takes, resolved as
   * the same pair unbridged would be; null for every other kind.
   */
  private record Correspondence(
      String name,
      String wireName,
      Kind kind,
      CodeBlock prism,
      Group group,
      TypeName domainElement,
      CodeBlock valuePrism,
      Correspondence present) {

    Correspondence(String name, String wireName, Kind kind, CodeBlock prism) {
      this(name, wireName, kind, prism, null, null, null, null);
    }

    /** The same correspondence as a member of {@code group}. */
    Correspondence in(Group group) {
      return new Correspondence(
          name, wireName, kind, prism, group, domainElement, valuePrism, present);
    }

    /**
     * The same correspondence carrying its domain element type, for the two legs that have to name
     * it rather than let it be inferred: {@code ARRAY}, because a generic array cannot be created
     * without its constructor ({@code Domain[]::new}), and a wildcard-carrying {@code
     * OPTIONAL_BRIDGE}, whose {@code Optional} would otherwise close over a capture (see {@link
     * MappingProcessor#bridgeParseLeg}). Null wherever inference suffices.
     */
    Correspondence withDomainElement(TypeName element) {
      return new Correspondence(name, wireName, kind, prism, group, element, valuePrism, present);
    }

    /**
     * The same correspondence carrying the value prism {@code MAP_ENTRIES} passes to the key
     * prism's bulk forms; null for every other kind.
     */
    Correspondence withValuePrism(CodeBlock values) {
      return new Correspondence(name, wireName, kind, prism, group, domainElement, values, present);
    }

    /**
     * The kind the leg's value takes: this correspondence's own, or, for a bridge, the kind its
     * present value takes, which decides the scans a bridged container carries.
     */
    Kind valueKind() {
      return kind == Kind.OPTIONAL_BRIDGE ? present.kind() : kind;
    }

    boolean fallible() {
      return switch (kind) {
        // Identity-container null scans guard hostile bindings, like every identity guard;
        // they do not make the mapping fallible for tier selection.
        case IDENTITY, IDENTITY_ELEMENTS, IDENTITY_MAP -> false;
        default -> true;
      };
    }
  }

  /**
   * The identity kind for a leg that pins the scan's result to the component's <em>exact</em> type
   * instead of letting it be inferred: the sparse tier's {@code setIfPresent} method reference. The
   * bridged leg names its {@code Optional}'s argument outright instead, so it keeps the scan (see
   * {@link #bridgeParseLeg}). This adds one exclusion to {@link #identityKind}: a wildcard-argument
   * container stays a plain copy, because the helper captures the wildcard afresh on its argument
   * and return sides, so the capture the leg receives is not the type the component declares. An
   * array carries its element type in the type itself, so it has neither this failure mode nor the
   * raw one.
   */
  private Kind sparseIdentityKind(TypeMirror type) {
    Kind kind = identityKind(type);
    if (kind == Kind.IDENTITY || type instanceof ArrayType) {
      return kind;
    }
    // Only the wildcard half of the rung can still be false here: identityKind has already
    // collapsed a raw container to IDENTITY.
    return hasProperArguments(type) ? kind : Kind.IDENTITY;
  }

  /**
   * Identity components copy verbatim; a same-typed {@code List}, {@code Set}, {@code Map} or
   * reference array additionally scans for nulls — but only where the emitted generic helper can
   * type ({@link #scanTypable}). A raw container takes the plain identity leg instead, guarded like
   * any other reference read ({@code hkj$ifPresent}), giving up only the element scan the helper
   * cannot express. {@link #sparseIdentityKind} narrows this further where the result's type is
   * pinned rather than inferred.
   */
  private Kind identityKind(TypeMirror type) {
    Kind kind = containerKind(type);
    if (kind == Kind.IDENTITY || type instanceof ArrayType) {
      return kind;
    }
    return scanTypable(type) ? kind : Kind.IDENTITY;
  }

  /** Which identity container a component is, by type alone, before any typability rule. */
  private static Kind containerKind(TypeMirror type) {
    if (ELEMENT_CONTAINERS.stream().anyMatch(container -> isExactly(type, container))
        // A primitive array has no element that could be null, so it needs no scan.
        || (type instanceof ArrayType array && !array.getComponentType().getKind().isPrimitive())) {
      return Kind.IDENTITY_ELEMENTS;
    }
    if (isExactly(type, "java.util.Map")) {
      return Kind.IDENTITY_MAP;
    }
    return Kind.IDENTITY;
  }

  /**
   * The typability rule every tier shares: an identity container carries the emitted null scan only
   * where it is not raw. The helpers are generic ({@code <E> hkj$allPresent(List<E>)}), and a raw
   * argument erases both the call and its result, so the ladder the leg feeds no longer types and
   * the generated Impl does not compile. This is the shared half only — a leg that pins the scan's
   * result type rather than inferring it excludes a wildcard argument as well ({@link
   * #sparseIdentityKind}). Asked only of a type {@link #isExactly} has already matched to {@code
   * List}, {@code Set} or {@code Map}, so it is a declared generic type by then; an array names its
   * element type in the type itself and is never asked. Shared with {@link MergeProcessor}, whose
   * identity fills carry the same scan, like the helpers themselves.
   */
  static boolean scanTypable(TypeMirror type) {
    return !ProcessorUtils.isRaw((DeclaredType) type);
  }

  /**
   * The stricter rung: whether a container names its element type outright, so a generic call can
   * be written <em>over</em> that element rather than merely inferred at the call. It adds the
   * wildcard exclusion to {@link #scanTypable} — a wildcard argument is captured afresh at each
   * mention, so two mentions of one declaration are two different types. Asked wherever the emitted
   * code has to relate the element to something else: {@link #sparseIdentityKind}'s pinned scan
   * result, and the {@code getX().addAll(...)} write, whose receiver and argument must meet.
   *
   * <p>Shallow on purpose, like {@link #hasWildcardArgument}: only the container's own arguments
   * decide, because only they are captured. Asked, like {@link #scanTypable}, of a type already
   * matched to a declared container, so the cast holds.
   */
  static boolean hasProperArguments(TypeMirror type) {
    return scanTypable(type) && !hasWildcardArgument((DeclaredType) type);
  }

  /**
   * Whether a mirror is exactly the given container type (not a subtype). Shared with {@code
   * MergeProcessor}, like the emitted guard helpers.
   */
  static boolean isExactly(TypeMirror type, String qualifiedName) {
    return type instanceof DeclaredType declared
        && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals(qualifiedName);
  }

  private record PrismResolution(CodeBlock accessor, boolean ambiguous) {
    static final PrismResolution NONE = new PrismResolution(null, false);
  }

  /**
   * How a use site's fix lines declare the leaf that would stand in for a nested spec: named after
   * the component, over the pair being resolved. A component carrying an {@code @OptionalBridge}
   * marker needs the leaf to be its only spec method, since a marker and a same-named leaf are one
   * method with incompatible return types; on a record wire the leaf carries the annotation too, as
   * the bridge needs it there, and on a bean wire, which bridges without it, the leaf is bare.
   * Every other site declares a bare leaf.
   */
  private record LeafSite(String annotation, String placement) {
    static final LeafSite PLAIN = new LeafSite("", "");
    static final LeafSite BRIDGE_MARKER =
        new LeafSite("@OptionalBridge ", ", as the component's only spec method,");
    static final LeafSite BEAN_MARKER = new LeafSite("", ", as the component's only spec method,");

    /** The site for a bridged component: where it carries the marker, and on which wire. */
    static LeafSite bridged(boolean marked, WireShape wire) {
      return !marked ? PLAIN : wire instanceof WireShape.BeanShape ? BEAN_MARKER : BRIDGE_MARKER;
    }

    boolean marked() {
      return !equals(PLAIN);
    }

    String declaration(String name, TypeMirror wireType, TypeMirror domainType) {
      return "'"
          + annotation
          + "default ValidatedPrism<"
          + wireType
          + ", "
          + domainType
          + "> "
          + name
          + "()'"
          + placement;
    }
  }

  /**
   * The specs a use site resolves a pair among: those serving the site's direction whose declared
   * pair covers it, this compilation's first. Shared by {@link #resolveNestedSpec} and {@link
   * #bridgeOffer}, so the bridge is offered as a bare marker exactly when resolution would find a
   * single spec.
   */
  private Candidates servingCandidates(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeMirror wireType,
      TypeMirror domainType,
      WireShape.Direction need) {
    return Candidates.nearest(
        registry.stream()
            .filter(r -> r.serves(need))
            .filter(r -> covers(spec, r, domainType, wireType))
            .toList());
  }

  /**
   * Resolves the prism carrying a (wireType -> domainType) correspondence through a single mapping
   * spec for the pair that serves the site's direction, {@code need}, through the surface its
   * generated Impl exposes: {@code asValidatedPrism()} for a full mapping, or the one half a
   * one-directional mapping has. Explicit leaves are matched by {@link #classify} and {@link
   * #classifyUpdate} before identity classification ever runs — whole-component leaves directly,
   * container ELEMENT/VALUE leaves through {@link #containerLeafCorrespondence} — so by the time a
   * nested spec is consulted no leaf exists for the pair. More than one candidate spec is reported
   * as an error.
   */
  private PrismResolution resolveNestedSpec(
      TypeElement spec,
      List<RegisteredSpec> registry,
      String name,
      TypeMirror wireType,
      TypeMirror domainType,
      WireShape.Direction need) {
    return resolveNestedSpec(
        spec, registry, name, wireType, domainType, need, LeafSite.PLAIN, List.of());
  }

  /**
   * The full overload: {@code site} is how a fix line declares the leaf that would stand in for the
   * spec, and {@code active} carries the (domain, wire) pairs already being composed on the current
   * element-mapped recursion, so a spec whose leaf pair covers itself is caught instead of
   * overflowing the stack.
   */
  private PrismResolution resolveNestedSpec(
      TypeElement spec,
      List<RegisteredSpec> registry,
      String name,
      TypeMirror wireType,
      TypeMirror domainType,
      WireShape.Direction need,
      LeafSite site,
      List<DeclaredType> active) {
    Candidates candidates = servingCandidates(spec, registry, wireType, domainType, need);
    List<RegisteredSpec> nested = candidates.chosen();
    if (nested.size() > 1) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "field '" + name + "' matches more than one mapping spec: " + candidates.names() + ".",
          "A nested component resolves to the single spec mapping ("
              + domainType
              + ", "
              + wireType
              + "); with several, the choice would be arbitrary.",
          "Add the leaf "
              + site.declaration(name, wireType, domainType)
              + " delegating to the spec you want, or "
              + (candidates.allClasspath()
                  ? "declare a @GenerateMapping spec for the pair in this compilation, which takes"
                      + " precedence over a dependency's."
                  : "remove the duplicate spec."));
      return new PrismResolution(null, true);
    }
    if (nested.size() == 1) {
      noteShadowed(
          processingEnv,
          spec,
          TAG,
          "field '" + name + "'",
          "Keep it, or delegate explicitly with the leaf "
              + site.declaration(name, wireType, domainType)
              + " if the classpath spec is the one meant.",
          candidates);
      RegisteredSpec match = nested.getFirst();
      if (match.spec().getTypeParameters().isEmpty()) {
        return new PrismResolution(match.nestingPrism(), false);
      }
      Map<Element, TypeMirror> bindings = new LinkedHashMap<>();
      unify(match.domain(), domainType, bindings);
      unify(match.wire(), wireType, bindings);
      List<ExecutableElement> leaves = abstractLeaves(match.spec());
      if (leaves.isEmpty()) {
        CodeBlock arguments =
            match.spec().getTypeParameters().stream()
                .map(
                    variable ->
                        CodeBlock.of("$T", ProcessorUtils.typeNameOf(bindings.get(variable))))
                .collect(CodeBlock.joining(", "));
        return new PrismResolution(
            CodeBlock.of("$T.<$L>instance().asValidatedPrism()", match.impl(), arguments), false);
      }
      return elementMappedComposition(
          spec, registry, name, match, bindings, leaves, domainType, wireType, site, active);
    }
    return PrismResolution.NONE;
  }

  /**
   * Composes an element-mapped nested call: {@code XImpl.of(prism, ...).asValidatedPrism()}, one
   * prism per abstract leaf under the use site's bindings. A single-leaf spec's element pair may
   * resolve through a leaf on the using spec named after the component (the container-leaf
   * convention generalised); any pair may resolve through another registered mapping. An
   * unresolvable pair is diagnosed with both levers.
   */
  private PrismResolution elementMappedComposition(
      TypeElement spec,
      List<RegisteredSpec> registry,
      String name,
      RegisteredSpec match,
      Map<Element, TypeMirror> bindings,
      List<ExecutableElement> leaves,
      TypeMirror domainType,
      TypeMirror wireType,
      LeafSite site,
      List<DeclaredType> active) {
    Types types = processingEnv.getTypeUtils();
    DeclaredType instantiated =
        types.getDeclaredType(
            match.spec(),
            match.spec().getTypeParameters().stream()
                .map(bindings::get)
                .toArray(TypeMirror[]::new));
    // A self-covering element mapping re-enters composition for the same instantiated spec; the
    // instantiated type carries both sides, so one isSameType catches the cycle before it
    // overflows the stack, while a legitimately shrinking recursion (Page<Page<T>>) never repeats.
    if (active.stream().anyMatch(seen -> types.isSameType(seen, instantiated))) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "field '"
              + name
              + "' nests the element-mapped '"
              + match.describe()
              + "', which maps itself: resolving its leaf returns to the pair ("
              + domainType
              + ", "
              + wireType
              + ").",
          "An of(...) composition needs a prism for every leaf; a self-covering element mapping"
              + " would need its own prism as that input, so the composition never terminates.",
          "Break the cycle with the leaf "
              + site.declaration(name, wireType, domainType)
              + " on this spec, or map the element with a non-recursive spec.");
      return new PrismResolution(null, true);
    }
    List<DeclaredType> nestedActive = new ArrayList<>(active);
    nestedActive.add(instantiated);
    List<CodeBlock> prisms = new ArrayList<>();
    for (ExecutableElement leaf : leaves) {
      DeclaredType substituted =
          (DeclaredType) ProcessorUtils.returnTypeIn(types, instantiated, leaf);
      TypeMirror elementWire = substituted.getTypeArguments().get(0);
      TypeMirror elementDomain = substituted.getTypeArguments().get(1);
      ExecutableElement outerLeaf =
          leaves.size() == 1 ? findLeaf(spec, name, elementWire, elementDomain) : null;
      if (outerLeaf != null) {
        prisms.add(CodeBlock.of("$L()", outerLeaf.getSimpleName()));
        continue;
      }
      // The element prism goes into of(...), which takes a whole ValidatedPrism, so it needs both
      // directions whatever the site nesting the outer mapping does.
      PrismResolution nested =
          resolveNestedSpec(
              spec,
              registry,
              name,
              elementWire,
              elementDomain,
              WireShape.Direction.BIDIRECTIONAL,
              site,
              nestedActive);
      if (nested.ambiguous()) {
        return nested;
      }
      if (nested.accessor() != null) {
        prisms.add(nested.accessor());
        continue;
      }
      // A leaf on this spec, named after the component, serves only a single-leaf mapping: with
      // several, one name cannot say which leaf it stands in for. There the leaf offered is over
      // the whole pair and builds the composition itself, and no spec is offered, since the other
      // leaves may want theirs too. A leaf already named after the component is replaced, as the
      // spec cannot declare both; a spec is offered only for a record pair, the one it can map.
      boolean single = leaves.size() == 1;
      boolean records =
          single && Stream.of(elementWire, elementDomain).allMatch(type -> asRecord(type) != null);
      String declare =
          sameNamedDefault(spec, name) == null ? "Declare " : "Replace '" + name + "()' with ";
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "field '"
              + name
              + "' nests the element-mapped '"
              + match.describe()
              + "', but the element pair ("
              + elementDomain
              + ", "
              + elementWire
              + ") for its leaf '"
              + leaf.getSimpleName()
              + "' has no mapping.",
          "An element-mapped Impl is built by of(...), one ValidatedPrism per abstract leaf. "
              + (single
                  ? "The prism must come from a leaf on this spec or another mapping,"
                  : "'"
                      + match.describe()
                      + "' has "
                      + leaves.size()
                      + " leaves, and a leaf on this spec named after the component can stand in"
                      + " only for a single-leaf mapping, so each prism must come from another"
                      + " mapping,")
              + declarationSites(processingEnv, spec)
              + "."
              + unusableSpecHint(
                  registry, elementWire, elementDomain, WireShape.Direction.BIDIRECTIONAL),
          (single
                  ? declare + site.declaration(name, elementWire, elementDomain) + " on this spec"
                  : declare
                      + site.declaration(name, wireType, domainType)
                      + " on this spec, returning "
                      + match.impl().canonicalName()
                      + ".of(...).asValidatedPrism() with one ValidatedPrism per leaf, in the order "
                      + leaves.stream()
                          .map(l -> "'" + l.getSimpleName() + "'")
                          .collect(Collectors.joining(", ")))
              + (records
                  ? ", or map "
                      + elementWire
                      + " to "
                      + elementDomain
                      + " with its own @GenerateMapping spec."
                  : "."));
      return new PrismResolution(null, true);
    }
    return new PrismResolution(
        CodeBlock.of("$T.of($L).asValidatedPrism()", match.impl(), CodeBlock.join(prisms, ", ")),
        false);
  }

  /**
   * Whether a registered spec's declared pair covers a use site: a concrete registration must match
   * exactly, a threaded one by unification — every spec variable bound consistently across both
   * sides to an argument the using spec supports (concrete, or its own variables).
   */
  private boolean covers(
      TypeElement user, RegisteredSpec candidate, TypeMirror domainType, TypeMirror wireType) {
    Types types = processingEnv.getTypeUtils();
    if (candidate.spec().getTypeParameters().isEmpty()) {
      return types.isSameType(candidate.domain(), domainType)
          && types.isSameType(candidate.wire(), wireType);
    }
    Map<Element, TypeMirror> bindings = new LinkedHashMap<>();
    return unify(candidate.domain(), domainType, bindings)
        && unify(candidate.wire(), wireType, bindings)
        && bindings.size() == candidate.spec().getTypeParameters().size()
        // unify binds only the candidate's own variables, so a full-size binding map is a total
        // one; each bound argument must be usable by the spec at the use site.
        && bindings.values().stream().allMatch(binding -> supportedArgument(user, binding));
  }

  /**
   * Structural first-order unification of a declared mirror against a use-site mirror, binding the
   * declared side's type variables. ERROR kinds step aside (never a spurious match); bindings must
   * stay consistent across repeated occurrences.
   */
  private boolean unify(TypeMirror declared, TypeMirror actual, Map<Element, TypeMirror> bindings) {
    Types types = processingEnv.getTypeUtils();
    // The declared side comes from a registered spec, whose mirrors resolved (an unresolved pair
    // never registers); only the use site can carry an ERROR, where a record read from a class file
    // names a type missing from the classpath, and it steps aside.
    if (actual.getKind() == TypeKind.ERROR) {
      return false;
    }
    if (declared instanceof javax.lang.model.type.TypeVariable variable) {
      TypeMirror existing = bindings.get(variable.asElement());
      if (existing != null) {
        return types.isSameType(existing, actual);
      }
      bindings.put(variable.asElement(), actual);
      return true;
    }
    if (declared instanceof DeclaredType declaredType
        && actual instanceof DeclaredType actualType) {
      List<? extends TypeMirror> declaredArguments = declaredType.getTypeArguments();
      List<? extends TypeMirror> actualArguments = actualType.getTypeArguments();
      if (!types.isSameType(types.erasure(declaredType), types.erasure(actualType))
          || declaredArguments.size() != actualArguments.size()) {
        return false;
      }
      for (int i = 0; i < declaredArguments.size(); i++) {
        if (!unify(declaredArguments.get(i), actualArguments.get(i), bindings)) {
          return false;
        }
      }
      return true;
    }
    if (declared instanceof ArrayType declaredArray && actual instanceof ArrayType actualArray) {
      return unify(declaredArray.getComponentType(), actualArray.getComponentType(), bindings);
    }
    return types.isSameType(declared, actual);
  }

  /**
   * The spec's renames as domain name to wire name, collected from every {@code @MapField} member.
   *
   * <p>A rename binds on both sides at once: its method names a domain component and its {@code to}
   * names a wire component. A <em>locally declared</em> rename that misses either end is refused,
   * which is the typo'd-rename guard. An <em>inherited</em> one that misses either end is inert and
   * simply not collected, like every other inherited vocabulary member that binds to nothing here:
   * one mix-in serves specs whose domains differ and whose wires differ, a projection and a PATCH
   * bean included, and neither has to carry a component only its siblings have. Nothing is silently
   * mismapped by the omission, because every wire component still has to name a source, so a wire
   * that does carry the rename's target and has no other source for it is reported against that
   * component instead.
   */
  private Map<String, String> collectRenames(
      TypeElement spec, TypeElement domain, WireShape wire, List<Flattened> flattened) {
    Set<String> flattenedInner =
        flattened.stream().flatMap(group -> group.inner().stream()).collect(Collectors.toSet());
    Map<String, String> renames = new LinkedHashMap<>();
    Map<String, ExecutableElement> renameSources = new LinkedHashMap<>();
    for (ExecutableElement method : specMembers(spec)) {
      MapField mapField = method.getAnnotation(MapField.class);
      if (mapField == null) {
        continue;
      }
      String name = method.getSimpleName().toString();
      // A rename and a flatten marker on one method are refused where they meet; split across a
      // mix-in and the spec they are two legal declarations of one name, so the rename is refused
      // here rather than silently outranked by the group.
      if (flattenedNamed(flattened, name) != null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapField method '"
                + name
                + "'"
                + inheritedNote(method, spec)
                + " names a flattened component.",
            "A rename points one domain component at one wire component, and a flattened"
                + " component spreads across several; one component cannot be both.",
            "Remove the rename; rename the group's inner components individually.");
        return null;
      }
      // A flattened group's inner components are renamed by their own names, like any other.
      boolean onDomain =
          flattenedInner.contains(name)
              || domain.getRecordComponents().stream()
                  .anyMatch(c -> c.getSimpleName().contentEquals(name));
      if (!onDomain) {
        // An inherited rename for a component this domain does not have is inert, exactly as an
        // inherited leaf for one is: a shared vocabulary may speak about components only some
        // extending specs declare.
        if (!declaredLocally(method, spec)) {
          continue;
        }
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapField method '"
                + name
                + "' does not name a component of "
                + domain.getSimpleName()
                + ".",
            "Renames are declared as an abstract method named after the DOMAIN component (or an"
                + " inner component of a flattened one). Found on "
                + domain.getSimpleName()
                + ": "
                + Stream.concat(
                        wireNames(domain.getRecordComponents()).stream(),
                        flattenedInner.stream().sorted())
                    .toList()
                + ".",
            "Rename the method to a domain component, or remove @MapField.");
        return null;
      }
      boolean onWire = wire.componentNamed(mapField.to()).isPresent();
      if (!onWire) {
        // The wire side is where a shared vocabulary most often cannot bind, since a projection or
        // a PATCH bean deliberately carries a subset: an inherited rename whose target this wire
        // omits is inert, and the component it renamed is simply not mapped here.
        if (!declaredLocally(method, spec)) {
          continue;
        }
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapField(to = \""
                + mapField.to()
                + "\") on '"
                + name
                + "' names no component of "
                + wire.element().getSimpleName()
                + ".",
            "Found on " + wire.element().getSimpleName() + ": " + wire.componentNames() + ".",
            "Point 'to' at an existing wire component.");
        return null;
      }
      String existing = renames.get(name);
      if (existing != null) {
        // Unrelated mix-ins may both declare the abstract rename (JLS 9.4.1 lets
        // override-equivalent abstracts coexist); two declarations of the same fact are one
        // rename, but two different targets have no most-specific winner.
        if (existing.equals(mapField.to())) {
          continue;
        }
        ExecutableElement first = renameSources.get(name);
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "component '" + name + "' has conflicting renames.",
            "'"
                + name
                + "' is renamed to '"
                + existing
                + "'"
                + inheritedNote(first, spec)
                + " and to '"
                + mapField.to()
                + "'"
                + inheritedNote(method, spec)
                + "; neither declaration overrides the other, so there is no winner.",
            "Override the rename on the spec itself, or align the mix-ins on one target.");
        return null;
      }
      if (renames.containsValue(mapField.to())) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapField(to = \""
                + mapField.to()
                + "\") on '"
                + name
                + "' targets a wire component"
                + " another rename already claims.",
            "Each wire component takes exactly one domain source.",
            "Point each rename at a distinct wire component.");
        return null;
      }
      renames.put(name, mapField.to());
      renameSources.put(name, method);
    }
    return renames;
  }

  /**
   * A domain record component spread across the wire's flat components: the marker's name, the
   * component's record type (under the domain's instantiation), and its components' names. Looked
   * up by {@code name()} only: the record's own equality covers a mirror, whose {@code equals} is
   * identity and says nothing about the type.
   */
  private record Flattened(String name, DeclaredType type, TypeElement record, List<String> inner) {

    Flattened {
      inner = List.copyOf(inner);
    }

    Group group() {
      return new Group(name, ProcessorUtils.typeNameOf(type));
    }
  }

  /**
   * Collects the spec's {@code @Flatten} markers, each naming a domain record component whose own
   * components map to the wire's flat components. Runs before renames and leaves are collected,
   * since a group's inner components join the names those may bind to. A local marker naming no
   * component is refused (the typo'd-marker hazard); an inherited one stays inert, so a shared
   * mix-in may carry markers for components only some extending specs have. Bean wires, generic
   * specs and groups wider than one {@code fields()} ladder are not supported yet. Returns null
   * after reporting.
   */
  private List<Flattened> collectFlattened(
      TypeElement spec, TypeElement domain, DeclaredType domainDeclared, WireShape wire) {
    List<String> components =
        domain.getRecordComponents().stream().map(c -> c.getSimpleName().toString()).toList();
    // Every name a wire component may be sourced from, and who owns it, so a group's inner
    // component can be refused for colliding with the domain or with another group.
    Map<String, String> owners = new LinkedHashMap<>();
    components.forEach(name -> owners.put(name, domain.getSimpleName().toString()));
    List<Flattened> groups = new ArrayList<>();
    List<ExecutableElement> unmatched = new ArrayList<>();
    for (ExecutableElement method : specMembers(spec)) {
      if (!isFlattenMarker(method)) {
        continue;
      }
      String name = method.getSimpleName().toString();
      // Unrelated mix-ins agreeing on a marker declare one fact (JLS 9.4.1).
      if (flattenedNamed(groups, name) != null) {
        continue;
      }
      RecordComponentElement component = componentNamed(domain, name);
      if (component == null) {
        // Judged once every group is known, since the name may be a group's inner component.
        if (declaredLocally(method, spec)) {
          unmatched.add(method);
        }
        continue;
      }
      if (!spec.getTypeParameters().isEmpty()) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@Flatten on '" + name + "' is declared on a generic spec (not supported yet).",
            "A flattened group is classified under the domain's instantiation, which a spec's own"
                + " type variables leave open.",
            "Map the pair with a non-generic spec over the concrete instantiation.");
        return null;
      }
      if (wire instanceof WireShape.BeanShape) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@Flatten on '" + name + "' spreads across a bean-shaped wire (not supported yet).",
            "A flattened group is read from and written to record components; a bean's getters"
                + " and setters are not wired through it.",
            "Map the pair with a record wire.");
        return null;
      }
      TypeMirror type = componentType(domainDeclared, component);
      TypeElement record = asRecord(type);
      if (record == null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@Flatten on '"
                + name
                + "' names a component of type "
                + ProcessorUtils.simpleTypeName(type)
                + ", which is not a record.",
            "A flattened component is spread by its record's components; only a record has them.",
            "Make the component a record, or map it through a leaf.");
        return null;
      }
      DeclaredType declaredType = (DeclaredType) type;
      if (hasWildcardArgument(declaredType) || ProcessorUtils.firstRawIn(type) != null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@Flatten on '"
                + name
                + "' spreads "
                + ProcessorUtils.simpleTypeName(type)
                + ", a raw or wildcard-carrying type (not supported yet).",
            "The group's record is reassembled by name in the generated code ('new "
                + record.getSimpleName()
                + "(...)'), which a raw or wildcard type argument cannot be written into.",
            "Declare the component with exact type arguments, or nest it through its own spec.");
        return null;
      }
      // The marker restates the record it spreads, so a spec that drifts from its domain fails
      // to compile rather than spreading whatever the component has become (the bridge marker's
      // rule).
      TypeMirror declared = memberTypeIn(spec, method);
      if (!processingEnv.getTypeUtils().isSameType(declared, type)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@Flatten marker '"
                + name
                + "'"
                + inheritedNote(method, spec)
                + " returns "
                + ProcessorUtils.simpleTypeName(declared)
                + ", not the component's own type.",
            "A marker restates the domain component it spreads, so the spec fails to compile"
                + " rather than spreading a component that has since changed shape; '"
                + domain.getSimpleName()
                + "."
                + name
                + "' is "
                + ProcessorUtils.simpleTypeName(type)
                + ".",
            "Declare the marker as '" + ProcessorUtils.simpleTypeName(type) + " " + name + "()'.");
        return null;
      }
      if (record.getRecordComponents().isEmpty()) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@Flatten on '"
                + name
                + "' spreads "
                + record.getSimpleName()
                + ", which has no components.",
            "A flattened component is spread by its record's components, and a record with none"
                + " would leave the domain component with nothing to assemble it from.",
            "Give the record a component, or map the component through a leaf.");
        return null;
      }
      // One ladder, not a chunked one: a group's ladder is an expression inside the outer
      // ladder's field(...), while ChunkedAssembly emits locals and a return statement. A wider
      // group needs a per-group parse helper method whose body can chunk, which is not written
      // yet.
      if (record.getRecordComponents().size() > ArityCeilings.ASSEMBLY) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@Flatten on '"
                + name
                + "' spreads "
                + record.getSimpleName()
                + ", which has more than "
                + ArityCeilings.ASSEMBLY
                + " components (not supported yet).",
            "A flattened group assembles through one fields() ladder, which takes at most "
                + ArityCeilings.ASSEMBLY
                + " legs.",
            "Split the record into two, each spread by its own marker.");
        return null;
      }
      List<String> inner = new ArrayList<>();
      for (RecordComponentElement innerComponent : record.getRecordComponents()) {
        String innerName = innerComponent.getSimpleName().toString();
        String owner = owners.putIfAbsent(innerName, name);
        if (owner != null) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@Flatten on '"
                  + name
                  + "' spreads a component '"
                  + innerName
                  + "' that '"
                  + owner
                  + "' also has.",
              "Every wire component takes exactly one source, and a flattened group's components"
                  + " are sourced by name, so a name shared with the domain or with another group"
                  + " would claim one wire component twice.",
              "Rename one of the two record components: every wire component takes one source,"
                  + " and both would claim the same one.");
          return null;
        }
        inner.add(innerName);
      }
      groups.add(new Flattened(name, declaredType, record, inner));
    }
    // A local marker naming nothing on the domain: a group's inner component, which would be a
    // second level of spreading, or a plain typo. An inherited one stayed inert above.
    for (ExecutableElement method : unmatched) {
      String name = method.getSimpleName().toString();
      Flattened enclosing =
          groups.stream().filter(group -> group.inner().contains(name)).findFirst().orElse(null);
      if (enclosing != null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@Flatten on '"
                + name
                + "' names a component of the flattened group '"
                + enclosing.name()
                + "' (not supported yet).",
            "Spreading is one level deep: a record inside a flattened group maps through its own"
                + " @GenerateMapping spec against a nested wire component.",
            "Give the inner pair its own spec, or flatten '"
                + name
                + "' into "
                + enclosing.record().getSimpleName()
                + " itself.");
        return null;
      }
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "@Flatten on '" + name + "' names no component of " + domain.getSimpleName() + ".",
          "A flatten marker is named after the DOMAIN record component whose own components"
              + " spread across the wire; an unmatched one would flatten nothing."
              + didYouMean(name, components)
              + " Found on "
              + domain.getSimpleName()
              + ": "
              + components
              + ".",
          "Rename the method to the component it spreads, or remove the annotation.");
      return null;
    }
    return List.copyOf(groups);
  }

  /**
   * A flattened component has no wire counterpart of its own, so a wire component of its name is
   * either a mistake (the component was meant to map whole) or another component's rename target.
   * Decided after renames are collected, so the second reading is not refused.
   */
  private boolean checkFlattenedNamesFree(
      TypeElement spec, WireShape wire, Map<String, String> renames, List<Flattened> flattened) {
    for (Flattened group : flattened) {
      if (wire.componentNamed(group.name()).isEmpty() || renames.containsValue(group.name())) {
        continue;
      }
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "@Flatten on '"
              + group.name()
              + "' spreads a component while the wire also carries a component named '"
              + group.name()
              + "'.",
          "A flattened component has no wire counterpart of its own (its record's components are"
              + " the wire's), so nothing sources that wire component.",
          "Remove the annotation to map the component as a whole, remove the wire component, or"
              + " point a @MapField rename at it from the domain component that feeds it.");
      return false;
    }
    return true;
  }

  /** A wire with fewer components than the domain's sources cannot carry a flattened group yet. */
  private void reportProjectionWithFlattened(
      TypeElement spec, TypeElement domain, WireShape wire, List<Flattened> flattened) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "'"
            + wire.element().getSimpleName()
            + "' has fewer components than '"
            + domain.getSimpleName()
            + "' spreads (not supported yet).",
        "A flattened group belongs to the full tier, where every source has a wire counterpart;"
            + " a wire with fewer components is a projection, whose write-back has no shape for a"
            + " group yet. Flattened: "
            + flattened.stream().map(Flattened::name).toList()
            + ".",
        "Add the missing wire components, or map the projection without the flattened"
            + " component.");
  }

  /**
   * A sparse update folds present wire properties into single-component edits; a flattened group
   * has no edit shape yet, so an {@code UpdateSpec} never forms one. Whether its marker is refused
   * or left inert depends on where the marker was written and on what this PATCH wire carries.
   *
   * <p>A <em>locally declared</em> marker is refused whatever this wire looks like: it is
   * vocabulary written for a spec that cannot use it, exactly as a local derived field or
   * {@code @OptionalBridge} is. An <em>inherited</em> one is judged against the wire rather than
   * waved through, which is where this rule parts company with those two. Refusing it outright
   * would report at a mix-in whose full spec needs the marker, and both remedies would break that
   * spec; staying silent whatever the wire looks like would trade one pointed refusal for a
   * dangling-property error per spread property, naming neither the marker nor the reason.
   *
   * <p>What separates the two is whether this bean carries any of the group's inner names with
   * nothing else to fill them. A bean declaring the group's own component instead, or omitting the
   * group altogether, carries none, so the marker is unused: inert, and the component patches whole
   * by identity wherever the bean has it. A bean carrying one is asking for the spread, and keeps
   * the refusal - including a bean that declares the component and an inner name both, which asks
   * for two readings of one component. What counts as filled is read under the renames in force, so
   * a property an unrelated rename points at is no evidence of a spread even where the group
   * happens to have a component of that name.
   */
  private boolean checkNoFlattened(
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      Map<String, String> renames) {
    Set<String> sourced =
        componentNames(domain).stream()
            .map(name -> renames.getOrDefault(name, name))
            .collect(Collectors.toUnmodifiableSet());
    for (ExecutableElement method : specMembers(spec)) {
      if (!isFlattenMarker(method)) {
        continue;
      }
      MarkerGroup group = markerGroup(domain, domainDeclared, method);
      List<String> spread =
          group == null
              ? List.of()
              : componentNames(group.record()).stream()
                  .filter(inner -> !sourced.contains(inner))
                  .filter(inner -> wire.componentNamed(inner).isPresent())
                  .toList();
      if (!declaredLocally(method, spec) && spread.isEmpty()) {
        continue;
      }
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "@Flatten on '"
              + method.getSimpleName()
              + "'"
              + inheritedNote(method, spec)
              + " has no meaning on a sparse UpdateSpec (not supported yet).",
          "A sparse update folds each present wire property into an edit of one domain component;"
              + " a flattened group would have to fold several properties into one nested"
              + " record, which no edit expresses yet."
              + (spread.isEmpty()
                  ? ""
                  : " '"
                      + wire.element().getSimpleName()
                      + "' carries "
                      + spread
                      + " with nothing else to source them, so the group is spread here rather"
                      + " than merely unused."),
          !spread.isEmpty()
              ? "Replace "
                  + spread
                  + " on '"
                  + wire.element().getSimpleName()
                  + "' with one '"
                  + method.getSimpleName()
                  + "' property, a getter and a setter over "
                  + ProcessorUtils.simpleTypeName(group.type())
                  + ", which patches the component whole; or map the pair with a full MappingSpec"
                  + " against a record wire, where the group can spread."
              : group == null
                  // Offering the mix-in placement here would bury a typo: an unmatched marker is
                  // inert on every tier, so the move would silence the mistake rather than fix it.
                  ? "Remove the @Flatten method: '"
                      + domain.getSimpleName()
                      + "' has no record component named '"
                      + method.getSimpleName()
                      + "' for it to spread."
                  : "Remove the @Flatten method, or move it to a mix-in a full MappingSpec also"
                      + " extends: an inherited marker this PATCH wire does not spread is inert.");
      return false;
    }
    return true;
  }

  /** The record a {@code @Flatten} marker would spread: the domain component's type and element. */
  private record MarkerGroup(TypeMirror type, TypeElement record) {}

  /**
   * The record a {@code @Flatten} marker would spread here, or null when it names no record
   * component of this domain - a marker speaking about a component only its sibling specs declare,
   * or one whose component is not a record. Either way there is nothing for this wire to spread;
   * {@link #collectFlattened} judges the same two shapes on the tiers that flatten, where a group
   * really is formed.
   *
   * <p>{@code domainDeclared} carries no substitution on the sparse path, where a generic domain is
   * already refused; it is threaded so the lookup reads the component's type the one way the rest
   * of the processor does.
   */
  private MarkerGroup markerGroup(
      TypeElement domain, DeclaredType domainDeclared, ExecutableElement marker) {
    RecordComponentElement component = componentNamed(domain, marker.getSimpleName().toString());
    if (component == null) {
      return null;
    }
    TypeMirror type = componentType(domainDeclared, component);
    TypeElement record = asRecord(type);
    return record == null ? null : new MarkerGroup(type, record);
  }

  /** A derived wire field: a spec default method named after a wire-only component. */
  private record DerivedField(String wireName) {}

  /** True for a zero-parameter {@code default} method returning {@code Getter} (any type args). */
  private static boolean isDerivedCandidate(
      Types types, TypeElement owner, ExecutableElement method) {
    return method.isDefault()
        && method.getParameters().isEmpty()
        && ProcessorUtils.returnTypeIn(types, (DeclaredType) owner.asType(), method)
            instanceof DeclaredType returnType
        && ((TypeElement) returnType.asElement()).getQualifiedName().contentEquals(GETTER);
  }

  /**
   * Counts the derived fields that bind here, for the registry's parse-capability arithmetic. A
   * derived field fills the wire component named after it, so a candidate whose name this wire does
   * not carry is inert and costs the wire nothing; counting it would make an ordinary full mapping
   * register as a projection and refuse to be nested. The mirror of {@link #domainSlots}'s rule for
   * a {@code @Flatten} marker, which likewise counts only where the domain has the component.
   */
  private static long derivedCandidateCount(
      ProcessingEnvironment env, TypeElement spec, Set<String> wireNames) {
    return specMembers(env.getElementUtils(), spec).stream()
        .filter(method -> isDerivedCandidate(env.getTypeUtils(), spec, method))
        .filter(method -> wireNames.contains(method.getSimpleName().toString()))
        .count();
  }

  /**
   * Collects the spec's derived wire fields: zero-parameter {@code default} methods returning
   * {@code Getter<Domain, WireComponentType>}, each named after a wire component with no domain
   * counterpart (the mirror of leaf methods, which are named after domain components). {@code
   * build} fills the component with the getter applied to the whole domain value; {@code parse}
   * ignores it. Returns null after reporting a malformed declaration.
   */
  private List<DerivedField> collectDerived(
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      Map<String, String> renames,
      List<Flattened> flattened) {
    List<DerivedField> derived = new ArrayList<>();
    for (ExecutableElement method : specMembers(spec)) {
      if (!isDerivedCandidate(processingEnv.getTypeUtils(), spec, method)) {
        continue;
      }
      String name = method.getSimpleName().toString();
      // Every wire component takes exactly one source, and a flattened group sources its record's
      // components by name; a derived field of one of those names would be a second source.
      Flattened spreading =
          flattened.stream().filter(group -> group.inner().contains(name)).findFirst().orElse(null);
      if (spreading != null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "derived field '"
                + name
                + "' fills a wire component the flattened component '"
                + spreading.name()
                + "' already spreads into.",
            "Every wire component takes exactly one source, and '"
                + name
                + "' is a component of "
                + spreading.record().getSimpleName()
                + ", which '"
                + spreading.name()
                + "' spreads by name.",
            "Rename the derived field after a wire component no group fills, or remove the @Flatten"
                + " marker.");
        return null;
      }
      if (domain.getRecordComponents().stream()
          .anyMatch(c -> c.getSimpleName().contentEquals(name))) {
        // Reading as a leaf is an authoring hazard for the spec that wrote the method, not for one
        // that merely inherits it: a sibling whose domain happens to carry a component of that
        // name maps it as usual and never derives anything.
        if (!declaredLocally(method, spec)) {
          continue;
        }
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "default method '" + name + "' returns a Getter but is named after a domain component.",
            "The name decides what a default method declares: a leaf is named after a DOMAIN"
                + " component and returns ValidatedPrism<WireComponent, DomainComponent>; a"
                + " derived wire field is named after a wire component with NO domain counterpart"
                + " and returns Getter<"
                + domain.getSimpleName()
                + ", WireComponentType>. Named '"
                + name
                + "', this method reads as a leaf, but a leaf never returns Getter.",
            "Return a ValidatedPrism to make it a leaf, or rename the method after the wire-only"
                + " component it derives.");
        return null;
      }
      if (wire.direction() == WireShape.Direction.PARSE_ONLY) {
        // A derived field fills a wire component on build, and a parse-only mapping builds
        // nothing, so it has nothing to do. An inherited one stays inert, as everywhere else.
        if (!declaredLocally(method, spec)) {
          continue;
        }
        reportDerivedOnParseOnly(method, wire);
        return null;
      }
      WireShape.WireComponent wireComponent = wire.componentNamed(name).orElse(null);
      if (wireComponent == null) {
        // A derived field is named for the wire, as a rename's 'to' is, so a shared vocabulary
        // binds it only where that wire carries the component: an inherited one this wire omits is
        // inert, and nothing derives it here. A projection stays refused for the derived fields it
        // does carry, which are the ones this spec can actually fill.
        if (!declaredLocally(method, spec)) {
          continue;
        }
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "derived field method '"
                + name
                + "' names no component of "
                + wire.element().getSimpleName()
                + ".",
            "A default method returning Getter declares a derived wire field, so its name must be"
                + " the wire component build fills. Found on "
                + wire.element().getSimpleName()
                + ": "
                + wire.componentNames()
                + ".",
            "Rename the method after the wire component it derives, or remove it.");
        return null;
      }
      DeclaredType returnType = (DeclaredType) memberTypeIn(spec, method);
      boolean shapeMatches =
          returnType.getTypeArguments().size() == 2
              && processingEnv
                  .getTypeUtils()
                  .isSameType(returnType.getTypeArguments().getFirst(), domainDeclared)
              && processingEnv
                  .getTypeUtils()
                  .isSameType(returnType.getTypeArguments().get(1), wireComponent.type());
      if (!shapeMatches) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "derived field '"
                + name
                + "' must return Getter<"
                + domain.getSimpleName()
                + ", "
                + wireComponent.type()
                + "> but returns '"
                + memberTypeIn(spec, method)
                + "'.",
            "build fills the wire component by applying the getter to the whole domain value, so"
                + " the first type argument must be the domain record and the second the wire"
                + " component's type.",
            "Declare 'default Getter<"
                + domain.getSimpleName()
                + ", "
                + wireComponent.type()
                + "> "
                + name
                + "()'.");
        return null;
      }
      if (renames.containsValue(name)) {
        String renameSource =
            renames.entrySet().stream()
                .filter(e -> e.getValue().equals(name))
                .findFirst()
                .orElseThrow()
                .getKey();
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "derived field '"
                + name
                + "' fills a wire component the @MapField rename on '"
                + renameSource
                + "' also targets.",
            "Each wire component takes exactly one source; the rename routes a domain component to"
                + " it while the derived getter computes it from the whole domain value.",
            "Point the rename at a distinct wire component, or remove the derived method.");
        return null;
      }
      derived.add(new DerivedField(name));
    }
    return derived;
  }

  /**
   * A derived field on a parse-only mapping has nothing to fill: it computes a wire component for
   * {@code build}, which the mapping does not have, and {@code parse} ignores it.
   */
  private void reportDerivedOnParseOnly(ExecutableElement method, WireShape wire) {
    String wireName = wire.element().getSimpleName().toString();
    Diagnostics.error(
        processingEnv.getMessager(),
        method,
        TAG,
        "derived field '"
            + method.getSimpleName()
            + "' has nothing to fill: '"
            + wireName
            + "' maps parse-only.",
        "A default method returning Getter computes a wire component for build, and '"
            + wireName
            + "' is never built, having getters and no way to be written; parse ignores a derived"
            + " field, so this one would never run.",
        "Remove the method, or give '"
            + wireName
            + "' setters or a builder, so the mapping builds it too.");
  }

  /**
   * A projection cannot carry derived fields: its {@code asLens()} writes wire values straight back
   * into the domain, but {@code build} recomputes a derived component, so the write-back could
   * never honour the value being set (an unlawful lens).
   */
  private void reportProjectionWithDerived(
      TypeElement spec, TypeElement domain, WireShape wire, List<DerivedField> derived) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "'" + wire.element().getSimpleName() + "' combines a projection with derived fields.",
        "Setting the derived fields "
            + derived.stream().map(DerivedField::wireName).toList()
            + " aside, '"
            + wire.element().getSimpleName()
            + "' has fewer components than '"
            + domain.getSimpleName()
            + "', which is the projection shape. The projection write-back (asLens or patch)"
            + " writes wire values back into the domain, but build recomputes a derived"
            + " component, so the write-back could never honour the value being set.",
        "Remove the derived methods and map the smaller wire as a plain projection, or add wire"
            + " components until every domain component keeps a counterpart.");
  }

  private List<Correspondence> classify(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      Map<String, String> renames,
      List<DerivedField> derived,
      List<Flattened> flattened) {
    List<WireShape.WireComponent> wireComponents = wire.components();

    if (wireComponents.size() - derived.size() != wireSlots(domain, flattened)) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'"
              + wire.element().getSimpleName()
              + "' has more components than '"
              + domain.getSimpleName()
              + (flattened.isEmpty()
                  ? "'."
                  : "' fills, flattened "
                      + flattened.stream().map(Flattened::name).toList()
                      + " included."),
          "build must fill every wire component from a domain source or a derived field, and the"
              + " extras have neither. A wire with fewer components maps as a projection (Lens"
              + " tier).",
          "Remove the extra wire components, add matching domain components, declare derived"
              + " fields ('default Getter<"
              + domain.getSimpleName()
              + ", ComponentType>' methods named after the extras), or spread a nested domain"
              + " component across the extras with an '@Flatten' marker named after it.");
      return null;
    }

    List<Correspondence> result =
        classifyDomain(
            spec,
            registry,
            domain,
            domainDeclared,
            wire,
            renames,
            flattened,
            WireShape.Direction.BIDIRECTIONAL);
    if (result == null) {
      return null;
    }
    for (DerivedField field : derived) {
      // Diagnostics in collectDerived guarantee the derived names are disjoint from the
      // domain-sourced claims, and the count check above that together they cover the wire.
      result.add(derivedCorrespondence(field));
    }
    return result;
  }

  /**
   * Classifies every domain component against the wire: its counterpart by name or rename, claimed
   * once, and a flattened component through each component of its record. Shared by the full tier,
   * which has already checked that no wire component is left over, and the parse-only tier, which
   * reads the components the domain needs and ignores any other getter, having nothing to fill.
   * Nested specs resolve against those serving {@code need}.
   */
  private List<Correspondence> classifyDomain(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      Map<String, String> renames,
      List<Flattened> flattened,
      WireShape.Direction need) {
    List<Correspondence> result = new ArrayList<>();
    Map<String, String> claimedWire = new LinkedHashMap<>();
    List<String> domainNames = componentNames(domain);
    for (RecordComponentElement domainComponent : domain.getRecordComponents()) {
      String name = domainComponent.getSimpleName().toString();
      Flattened group = flattenedNamed(flattened, name);
      if (group == null) {
        Correspondence resolved =
            classifyComponent(
                spec,
                registry,
                domain,
                domainDeclared,
                domainNames,
                null,
                wire,
                renames,
                claimedWire,
                domainComponent,
                need);
        if (resolved == null) {
          return null;
        }
        result.add(resolved);
        continue;
      }
      // A flattened component contributes one correspondence per component of its record, each
      // resolved against the wire exactly as a top-level component would be, and tagged with the
      // group so build reads through it and parse assembles it. The members are contiguous and in
      // the record's declaration order, which is what the group's ladder and constructor need.
      Group tag = group.group();
      List<String> innerNames = componentNames(group.record());
      for (RecordComponentElement inner : group.record().getRecordComponents()) {
        Correspondence resolved =
            classifyComponent(
                spec,
                registry,
                group.record(),
                group.type(),
                innerNames,
                tag,
                wire,
                renames,
                claimedWire,
                inner,
                need);
        if (resolved == null) {
          return null;
        }
        result.add(resolved.in(tag));
      }
    }
    return result;
  }

  /** A derived wire field's correspondence: build fills it through the spec's getter. */
  private static Correspondence derivedCorrespondence(DerivedField field) {
    return new Correspondence(
        field.wireName(), field.wireName(), Kind.DERIVED, CodeBlock.of("$L()", field.wireName()));
  }

  /**
   * Classifies one record component of {@code owner} (the domain, or a flattened component's
   * record) against the wire: its wire counterpart by name or rename, claimed once, then resolved
   * by {@link #resolveCorrespondence}. Reports and returns null when the component has no
   * counterpart or the counterpart is already taken.
   */
  private Correspondence classifyComponent(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement owner,
      DeclaredType ownerDeclared,
      List<String> ownerNames,
      Group group,
      WireShape wire,
      Map<String, String> renames,
      Map<String, String> claimedWire,
      RecordComponentElement component,
      WireShape.Direction need) {
    String name = component.getSimpleName().toString();
    // The component as an error names it: the domain path, which for a group member runs
    // through the flattened component (address.street), as its located failures will.
    String source = group == null ? name : group.name() + "." + name;
    String wireName = renames.getOrDefault(name, name);
    TypeMirror domainType = componentType(ownerDeclared, component);
    WireShape.WireComponent wireComponent = wire.componentNamed(wireName).orElse(null);
    if (wireComponent == null) {
      // A record component with no counterpart may be a nested record the wire carries flat:
      // the shape @Flatten exists for, so the fix names it where it could apply.
      String flattenOffer =
          group == null && asRecord(domainType) != null
              ? " Or, if the wire carries the components of "
                  + ProcessorUtils.simpleTypeName(domainType)
                  + " as flat fields, spread it with '@Flatten "
                  + ProcessorUtils.simpleTypeName(domainType)
                  + " "
                  + name
                  + "();'."
              : "";
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "domain field '"
              + owner.getSimpleName()
              + "."
              + name
              + "'"
              + (group == null ? "" : " (spread from '" + group.name() + "')")
              + " has no wire counterpart named '"
              + wireName
              + "'.",
          "Found on " + wire.element().getSimpleName() + ": " + wire.componentNames() + ".",
          "Align the component names, or add a '@MapField(to = ...)' rename on the spec."
              + flattenOffer);
      return null;
    }
    String previousSource = claimedWire.putIfAbsent(wireName, source);
    if (previousSource != null) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "domain components '"
              + previousSource
              + "' and '"
              + source
              + "' both map to wire component '"
              + wireName
              + "'.",
          "Each wire component takes exactly one domain source; a @MapField rename may not"
              + " collide with another component's mapping.",
          "Point the rename at a distinct wire component.");
      return null;
    }
    return resolveCorrespondence(
        spec,
        registry,
        owner,
        wire,
        name,
        wireName,
        wireComponent.type(),
        domainType,
        ownerNames,
        need,
        false);
  }

  /**
   * How many wire components the domain's sources call for: one per component, except that a
   * flattened component calls for one per component of its record.
   */
  private static int wireSlots(TypeElement domain, List<Flattened> flattened) {
    int slots = domain.getRecordComponents().size() - flattened.size();
    for (Flattened group : flattened) {
      slots += group.inner().size();
    }
    return slots;
  }

  private static Flattened flattenedNamed(List<Flattened> flattened, String name) {
    return flattened.stream().filter(group -> group.name().equals(name)).findFirst().orElse(null);
  }

  /**
   * Resolves one domain-component/wire-component pair to its correspondence by {@link
   * #resolvePair}, or, for a domain {@code Optional} against a nullable wire member, through the
   * Optional bridge, whose present value resolves by that same rule — reporting and returning null
   * when nothing usable exists. Shared by the full tier ({@link #classify}) and the wire-driven
   * tiers ({@link #classifyWire}), so a projection resolves exactly like a full-tier component.
   */
  private Correspondence resolveCorrespondence(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
      WireShape wire,
      String name,
      String wireName,
      TypeMirror wireType,
      TypeMirror domainType,
      List<String> domainNames,
      WireShape.Direction need,
      boolean projection) {
    // Optional bridge: a domain Optional<DE> maps to a nullable wire component PE. Empty <->
    // null/absent; the present value maps as the pair (PE, DE) would unbridged.
    //
    // A bean wire takes it automatically, since bean conventions leave Optional off property
    // types. A record wire takes it only where the spec asked, by @OptionalBridge: on a record,
    // null is a defect by default and that stays the default. A leaf over the whole Optional still
    // wins, as the more specific declaration. The bridge's shape is one no rung of resolvePair
    // takes, so asking it first changes no other pair's resolution.
    TypeMirror optionalElement = containerElement(domainType, "java.util.Optional");
    if (optionalElement != null
        && bridges(spec, wire, name, wireType)
        && findLeaf(spec, name, wireType, domainType) == null) {
      return resolveBridge(
          spec,
          registry,
          domain,
          wire,
          name,
          wireName,
          wireType,
          domainType,
          optionalElement,
          need);
    }
    PairResolution resolved =
        resolvePair(spec, registry, name, wireName, wireType, domainType, need, LeafSite.PLAIN);
    if (resolved.reported()) {
      return null;
    }
    if (resolved.correspondence() == null) {
      // A container lifts its elements one by one, so the leaf and the spec that would give it a
      // source are over its elements, exactly as for a bridged one.
      LeafOffer offer = leafOffer(spec, name, wireType, domainType);
      MemberSite member = new MemberSite(spec, registry, domain, wire, name, wireName, need);
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "target field '"
              + wire.element().getSimpleName()
              + "."
              + wireName
              + "' has no usable source.",
          "The types differ ("
              + wireType
              + " vs "
              + domainType
              + ") and no matching leaf method was found."
              + leafNearMissHint(spec, name, wireType, domainType)
              + (hasPrimitive(wireType, domainType) ? PRIMITIVE_REASON : "")
              + liftingRuleHint(spec, name, wireType, domainType)
              + unusableSpecHint(registry, offer.wire(), offer.domain(), need)
              + " Found on "
              + domain.getSimpleName()
              + ": "
              + domainNames
              + ".",
          (hasPrimitive(wireType, domainType)
                  ? primitiveFix(member, wireType, domainType)
                  : referenceFix(member, wireType, domainType))
              + (projection
                  ? " On a projection, a leaf's write-back can fail, so the projection"
                      + " maps through the validated patch(domain, wire), never asLens()."
                  : ""));
      return null;
    }
    return resolved.correspondence();
  }

  /**
   * One member a no-usable-source refusal is about: the spec, the specs it resolves nested pairs
   * among, the domain record and wire the member belongs to, its domain and wire names, and the
   * direction the site uses. Gathered once, so the fix lines computed for it read the same site.
   */
  private record MemberSite(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
      WireShape wire,
      String name,
      String wireName,
      WireShape.Direction need) {

    MemberSite {
      registry = List.copyOf(registry);
    }
  }

  /** Why a pair with a primitive side is offered no leaf of its own. Shared with the merge. */
  static final String PRIMITIVE_REASON =
      " A ValidatedPrism names reference types only, so no leaf converts a primitive component.";

  private static boolean hasPrimitive(TypeMirror wireType, TypeMirror domainType) {
    return wireType.getKind().isPrimitive() || domainType.getKind().isPrimitive();
  }

  /**
   * The fix for a pair of reference types nothing maps: the Optional bridge where the pair has its
   * shape, then the leaf over the pair (over its elements where a container lifts them), a spec
   * where both sides are records, and the declaration that would let a container lift where it does
   * not as declared.
   */
  private String referenceFix(MemberSite member, TypeMirror wireType, TypeMirror domainType) {
    LeafOffer offer = leafOffer(member.spec(), member.name(), wireType, domainType);
    return bridgeOffer(
            member.spec(), member.registry(), member.name(), wireType, domainType, member.need())
        + leafLine(
            member.spec(),
            member.name(),
            "'default ValidatedPrism<"
                + offer.wire()
                + ", "
                + offer.domain()
                + "> "
                + member.name()
                + "()'")
        + (offer.lifts() ? ", a leaf over the " + offer.parts() + " types" : "")
        // A spec is offered only for a record pair, as the bridged refusal offers it.
        + (offer.records()
            ? ", or declare a @GenerateMapping spec mapping those records,"
                + declarationSites(processingEnv, member.spec())
            : "")
        + liftableOffer(member, wireType, domainType)
        + ".";
  }

  /**
   * How a fix line introduces a leaf named after the component: added to the spec, or, where the
   * spec already has a same-named default method that is not the leaf the pair needs, in its place,
   * since the spec cannot declare both.
   */
  private String leafLine(TypeElement spec, String name, String declaration) {
    return sameNamedDefault(spec, name) instanceof ExecutableElement existing
        ? "Replace '" + existing.getSimpleName() + "()' with " + declaration
        : "Add " + declaration + " to the spec";
  }

  /** A default method named after the component, which a leaf for it would have to replace. */
  private ExecutableElement sameNamedDefault(TypeElement spec, String name) {
    return specMembers(spec).stream()
        .filter(method -> method.getSimpleName().contentEquals(name) && method.isDefault())
        .findFirst()
        .orElse(null);
  }

  /**
   * The fix for a pair with a primitive side, which no leaf can map ({@link #PRIMITIVE_REASON}). A
   * primitive and its own wrapper need only agree; any other pair is aligned, or its primitive
   * sides declared as their wrappers and the pair then mapped as any reference pair is: through a
   * leaf the spec already has over the wrappers, the Optional bridge, or a new leaf.
   */
  private String primitiveFix(MemberSite member, TypeMirror wireType, TypeMirror domainType) {
    Types types = processingEnv.getTypeUtils();
    TypeMirror wireBoxed = boxed(wireType);
    TypeMirror domainBoxed = boxed(domainType);
    String wireMember =
        "'" + member.wire().element().getSimpleName() + "." + member.wireName() + "'";
    String domainMember = "'" + member.domain().getSimpleName() + "." + member.name() + "'";
    if (types.isSameType(wireBoxed, domainBoxed)) {
      return "Declare "
          + wireMember
          + " and "
          + domainMember
          + " both "
          + types.unboxedType(wireBoxed)
          + ", or both "
          + wireBoxed
          + ", so that they copy.";
    }
    List<String> wrappers = new ArrayList<>();
    if (wireType.getKind().isPrimitive()) {
      wrappers.add("the " + wireMemberTerm(member.wire()) + " " + wireMember + " as " + wireBoxed);
    }
    if (domainType.getKind().isPrimitive()) {
      wrappers.add(domainMember + " as " + domainBoxed);
    }
    ExecutableElement leaf = findLeaf(member.spec(), member.name(), wireBoxed, domainBoxed);
    // Only a primitive wire member meets a domain Optional here, so a bridge offer is about the
    // null the wrapper can then hold.
    String bridge =
        leaf != null
            ? ""
            : bridgeOffer(
                    member.spec(),
                    member.registry(),
                    member.name(),
                    wireBoxed,
                    domainBoxed,
                    member.need())
                .strip();
    return "Align the component types, or declare "
        + String.join(" and ", wrappers)
        + (leaf != null
            ? ", which the leaf '" + leaf.getSimpleName() + "()' then converts."
            : bridge.isEmpty()
                ? ", and " + lowerFirst(referenceFix(member, wireBoxed, domainBoxed))
                : ", which can hold the null an empty Optional bridges to, and "
                    + lowerFirst(bridge));
  }

  private TypeMirror boxed(TypeMirror type) {
    return boxed(processingEnv.getTypeUtils(), type);
  }

  /** The wrapper type of a primitive, or the type itself. Shared with the merge processor. */
  static TypeMirror boxed(Types types, TypeMirror type) {
    return type instanceof PrimitiveType primitive ? types.boxedClass(primitive).asType() : type;
  }

  private static String lowerFirst(String sentence) {
    return Character.toLowerCase(sentence.charAt(0)) + sentence.substring(1);
  }

  /**
   * The outcome of resolving a pair: its correspondence, a refusal already reported, or neither,
   * which each caller refuses in its own terms.
   */
  private record PairResolution(Correspondence correspondence, boolean reported) {
    static final PairResolution NONE = new PairResolution(null, false);
    static final PairResolution REPORTED = new PairResolution(null, true);

    static PairResolution of(Correspondence correspondence) {
      return new PairResolution(correspondence, false);
    }
  }

  /**
   * Resolves a (wire, domain) pair by the one rule a component takes, and an Optional bridge's
   * present value with it: an explicit leaf first (beating even a same-typed identity match),
   * container element/value leaves, identity, nested-spec lifting through {@code List}/{@code
   * Set}/array/{@code Optional}/{@code Map}, then a direct nested spec. The container rungs report
   * the shapes that can never lift (an unnameable array, a raw or wildcard {@code Map}, key types
   * that differ with no key leaf); a pair no rung takes is {@link PairResolution#NONE}, for the
   * caller to refuse. {@code site} is how a fix line declares a leaf in a spec's place.
   */
  private PairResolution resolvePair(
      TypeElement spec,
      List<RegisteredSpec> registry,
      String name,
      String wireName,
      TypeMirror wireType,
      TypeMirror domainType,
      WireShape.Direction need,
      LeafSite site) {
    // An explicit leaf always wins — even over a same-typed identity match, so a
    // ValidatedPrism<X, X> can validate or normalise a component the types alone would copy.
    ExecutableElement directLeaf = findLeaf(spec, name, wireType, domainType);
    if (directLeaf != null) {
      if (!checkKeyLeafReached(spec, name, directLeaf, wireType, domainType, site)) {
        return PairResolution.REPORTED;
      }
      return PairResolution.of(
          new Correspondence(
              name, wireName, Kind.LEAF, CodeBlock.of("$L()", directLeaf.getSimpleName())));
    }
    // The same rule lifted through containers: an ELEMENT/VALUE-typed leaf on a List, Optional
    // or Map component beats the identity copy the container types alone would take. Only
    // explicit leaves pre-empt identity; nested-spec resolution never does (the scalar
    // precedent).
    Correspondence containerLeaf =
        containerLeafCorrespondence(spec, name, wireName, wireType, domainType, site);
    if (containerLeaf != null) {
      return PairResolution.of(containerLeaf);
    }
    if (processingEnv.getTypeUtils().isSameType(domainType, wireType)) {
      return PairResolution.of(new Correspondence(name, wireName, identityKind(domainType), null));
    }
    TypeMirror[] elements = elementPair(wireType, domainType);
    if (elements != null) {
      PrismResolution lifted =
          resolveNestedSpec(spec, registry, name, elements[0], elements[1], need, site, List.of());
      if (lifted.ambiguous()) {
        return PairResolution.REPORTED;
      }
      if (lifted.accessor() != null) {
        return PairResolution.of(
            new Correspondence(name, wireName, Kind.ELEMENTS, lifted.accessor()));
      }
    }
    TypeMirror[] arrayElements = arrayPair(wireType, domainType);
    if (arrayElements != null && !nameableArrayPair(arrayElements)) {
      // The sides differ (identity has already been ruled out), so this array would have to
      // lift - and it cannot. Saying why beats the no-usable-source fall-through, which would
      // offer an element leaf the emission could never carry. A pair that HAS such a leaf was
      // already reported by the leaf route, so only the leafless case reports here.
      if (findLeaf(spec, name, arrayElements[0], arrayElements[1]) == null) {
        reportUnnameableArray(spec, name, arrayElements, site);
      }
      return PairResolution.REPORTED;
    }
    if (arrayElements != null) {
      PrismResolution lifted =
          resolveNestedSpec(
              spec, registry, name, arrayElements[0], arrayElements[1], need, site, List.of());
      if (lifted.ambiguous()) {
        return PairResolution.REPORTED;
      }
      if (lifted.accessor() != null) {
        return PairResolution.of(
            new Correspondence(name, wireName, Kind.ARRAY, lifted.accessor())
                .withDomainElement(ProcessorUtils.typeNameOf(arrayElements[1])));
      }
    }
    TypeMirror wireElement = containerElement(wireType, "java.util.Optional");
    TypeMirror domainElement = containerElement(domainType, "java.util.Optional");
    if (wireElement != null && domainElement != null) {
      PrismResolution lifted =
          resolveNestedSpec(
              spec, registry, name, wireElement, domainElement, need, site, List.of());
      if (lifted.ambiguous()) {
        return PairResolution.REPORTED;
      }
      if (lifted.accessor() != null) {
        return PairResolution.of(
            new Correspondence(name, wireName, Kind.OPTIONAL, lifted.accessor()));
      }
    }
    DeclaredType wireMapType = asMapType(wireType);
    DeclaredType domainMapType = asMapType(domainType);
    if (wireMapType != null && domainMapType != null) {
      if (wireMapType.getTypeArguments().isEmpty() || domainMapType.getTypeArguments().isEmpty()) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "field '" + name + "' uses a raw Map, which cannot lift.",
            "Value lifting resolves a ValidatedPrism for the value type, and a raw Map declares"
                + " neither key nor value type.",
            "Declare both type arguments on each side, for example Map<String, EmailAddress>.");
        return PairResolution.REPORTED;
      }
      if (hasWildcardArgument(wireMapType) || hasWildcardArgument(domainMapType)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "field '" + name + "' uses wildcard Map type arguments, which cannot lift.",
            "Value lifting resolves a ValidatedPrism for the exact key and value types; a"
                + " wildcard leaves them unknown.",
            "Declare exact type arguments on both sides, for example Map<String,"
                + " EmailAddress>.");
        return PairResolution.REPORTED;
      }
      TypeMirror wireKey = wireMapType.getTypeArguments().getFirst();
      TypeMirror domainKey = domainMapType.getTypeArguments().getFirst();
      // A @MapKey leaf converts the keys; without one they can only pass through, so they must
      // already match. (A key leaf that matched has been classified before identity, so reaching
      // here with one means only the VALUES still need a source.)
      ExecutableElement keyLeaf = findKeyLeaf(spec, name, wireKey, domainKey);
      if (keyLeaf == null && !processingEnv.getTypeUtils().isSameType(wireKey, domainKey)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "field '"
                + name
                + "' maps between Maps whose key types differ ("
                + wireKey
                + " vs "
                + domainKey
                + ").",
            "Keys pass through as identity unless a @MapKey leaf converts them.",
            "Declare '@MapKey(\""
                + name
                + "\") default ValidatedPrism<"
                + wireKey
                + ", "
                + domainKey
                + "> "
                + name
                + "Key()' on the spec, or align the key types.");
        return PairResolution.REPORTED;
      }
      PrismResolution lifted =
          resolveNestedSpec(
              spec,
              registry,
              name,
              wireMapType.getTypeArguments().get(1),
              domainMapType.getTypeArguments().get(1),
              need,
              site,
              List.of());
      if (lifted.ambiguous()) {
        return PairResolution.REPORTED;
      }
      if (lifted.accessor() != null) {
        return PairResolution.of(
            keyLeaf == null
                ? new Correspondence(name, wireName, Kind.MAP, lifted.accessor())
                : new Correspondence(
                        name,
                        wireName,
                        Kind.MAP_ENTRIES,
                        CodeBlock.of("$L()", keyLeaf.getSimpleName()))
                    .withValuePrism(lifted.accessor()));
      }
      // Values resolving to nothing fall through to the direct nested spec, and then to the
      // caller's refusal, like List elements.
    }
    PrismResolution direct =
        resolveNestedSpec(spec, registry, name, wireType, domainType, need, site, List.of());
    if (direct.ambiguous()) {
      return PairResolution.REPORTED;
    }
    return direct.accessor() == null
        ? PairResolution.NONE
        : PairResolution.of(new Correspondence(name, wireName, Kind.LEAF, direct.accessor()));
  }

  /**
   * The Optional bridge: a domain {@code Optional<DE>} against a nullable wire member {@code PE},
   * an empty {@code Optional} mapping to {@code null}. The present value resolves as the pair
   * ({@code PE}, {@code DE}) would unbridged, by {@link #resolvePair}: copied, through a leaf,
   * lifted element by element through a container's element leaf or spec, or nested through the
   * spec for the pair. Absence is the only thing the bridge excuses, so a present container keeps
   * the null scan or located element failures its unbridged pair carries.
   */
  private Correspondence resolveBridge(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
      WireShape wire,
      String name,
      String wireName,
      TypeMirror wireType,
      TypeMirror domainType,
      TypeMirror optionalElement,
      WireShape.Direction need) {
    // The element a wildcard argument stands for, so a component declared Optional<? extends
    // Number> bridges on Number rather than dead-ending: no leaf can ever be declared over a
    // wildcard, so refusing here would leave the author a diagnostic with no reachable fix.
    TypeMirror bridged = bridgeElement(optionalElement);
    // A component carrying the marker needs any leaf a fix line offers in a spec's place to
    // replace it, carrying the annotation on a record wire.
    LeafSite site = LeafSite.bridged(declaresBridge(spec, name), wire);
    // An empty Optional is a null on the wire, which a primitive can never hold, so no conversion
    // of the present value could make the pair map. Only a bean without a marker reaches here with
    // one: a marker on a primitive member is refused where the marker is checked.
    if (wireType.getKind().isPrimitive()) {
      reportPrimitiveBridge(spec, domain, wire, name, wireName, wireType, domainType, bridged);
      return null;
    }
    PairResolution present =
        resolvePair(spec, registry, name, wireName, wireType, bridged, need, site);
    if (present.reported()) {
      return null;
    }
    if (present.correspondence() == null) {
      reportUnbridged(spec, registry, domain, wire, name, wireName, wireType, bridged, need, site);
      return null;
    }
    // A property written through its own getter has no absent state to carry the bridge's empty.
    // Asked only of a pair the bridge would otherwise take, so a component whose present value
    // nothing converts keeps the diagnostic that names what would.
    if (writeSite(wire, wireName) instanceof WireShape.WriteSite.CollectionAdd write) {
      reportGetterOnlyBridge(spec, domain, name, wireName, domainType, bridged, write);
      return null;
    }
    return bridgedCorrespondence(present.correspondence(), bridged);
  }

  /**
   * Refuses a bridge whose present value nothing converts. The bridge is the only way to map a
   * domain {@code Optional} to a plain nullable member, so the refusal names what would convert the
   * present value, never the whole {@code Optional}: a leaf over {@code Optional<DE>} is matched as
   * a plain leaf and bypasses the bridge. For a container that is its elements (a {@code Map}'s
   * values), which lift one by one as they would unbridged, so the fix offered is an element leaf
   * or a spec; a leaf over the whole container stays a valid override.
   */
  private void reportUnbridged(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
      WireShape wire,
      String name,
      String wireName,
      TypeMirror wireType,
      TypeMirror bridged,
      WireShape.Direction need,
      LeafSite site) {
    LeafOffer offer = leafOffer(spec, name, wireType, bridged);
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "domain field '"
            + domain.getSimpleName()
            + "."
            + name
            + "' is Optional<"
            + bridged
            + ">, bridged to the nullable "
            + wireMemberTerm(wire)
            + " '"
            + wireName
            + "' of type "
            + wireType
            + ", but "
            + (offer.lifts()
                ? "its "
                    + offer.parts()
                    + " types differ ("
                    + offer.wire()
                    + " vs "
                    + offer.domain()
                    + ")"
                : "the element types differ")
            + " and neither a leaf nor a mapping spec converts them.",
        "A domain Optional bridges to a nullable "
            + wireMemberTerm(wire)
            + " (empty maps to absent); the present "
            + (offer.lifts()
                ? "container lifts its "
                    + offer.parts()
                    + "s one by one, as it would unbridged, each copied when the types match,"
                    + " nested through a @GenerateMapping spec for their pair, or mapped through a"
                    + " leaf"
                : "element is copied when the types match, nested through a @GenerateMapping spec"
                    + " for the element pair, or mapped through a leaf")
            + " named after the domain component returning ValidatedPrism<"
            + offer.wire()
            + ", "
            + offer.domain()
            + "> (the "
            + (offer.lifts()
                ? offer.parts() + " types, not the container"
                : "element types, not the Optional")
            + ")."
            + unusableSpecHint(registry, offer.wire(), offer.domain(), need),
        // A bean bridges without the annotation, so its leaf is bare, and replaces the marker
        // only where the spec carries one.
        "Declare '"
            + site.annotation()
            + "default ValidatedPrism<"
            + offer.wire()
            + ", "
            + offer.domain()
            + "> "
            + name
            + "()'"
            + (site.marked()
                ? " as the component's only spec method, replacing any marker for it"
                : " on the spec")
            // A spec is offered only for a record pair: the commonest refusal is a value type
            // against a String, which no spec can map, and every pair reads the spec in the why.
            + (offer.records()
                ? ", declare a @GenerateMapping spec mapping those records,"
                    + declarationSites(processingEnv, spec)
                : "")
            + ", or align the "
            + (offer.lifts() ? offer.parts() : "element")
            + " types.");
  }

  /**
   * Refuses a bean's bridge onto a primitive property, which can never hold the {@code null} that
   * stands for an empty {@code Optional}. No leaf rescues it, since a leaf converts only a present
   * value and cannot name a primitive either, so the fix declares the property as its wrapper, or,
   * where the element is that wrapper already, drops the {@code Optional} from the domain so the
   * two primitives copy. Only a bean without a marker reaches here: a marker on a primitive member
   * is refused where the marker is checked.
   */
  private void reportPrimitiveBridge(
      TypeElement spec,
      TypeElement domain,
      WireShape wire,
      String name,
      String wireName,
      TypeMirror wireType,
      TypeMirror domainType,
      TypeMirror bridged) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "domain field '"
            + domain.getSimpleName()
            + "."
            + name
            + "' is "
            + domainType
            + ", bridged to the "
            + wireMemberTerm(wire)
            + " '"
            + wireName
            + "', which is the primitive "
            + wireType
            + ".",
        "An empty Optional is a null on the wire, and "
            + wireType
            + " can never hold one, so the "
            + wireMemberTerm(wire)
            + " cannot carry an absent value either way; no leaf can change that, since a leaf"
            + " converts only a present value and a ValidatedPrism cannot name a primitive.",
        primitiveBridgeFix(spec, wire, name, wireName, wireType, bridged, LeafSite.PLAIN, false)
            + (processingEnv.getTypeUtils().isSameType(boxed(wireType), bridged)
                ? ", or declare '" + domain.getSimpleName() + "." + name + "' as " + wireType
                : "")
            + ".");
  }

  /**
   * The fix for a bridge onto a primitive wire member: declare the member as its wrapper, which
   * then bridges like any nullable member, with a leaf from the wrapper to the element where the
   * two differ. {@code site} is how that leaf is declared, in place of any marker, and {@code
   * dropMarker} whether the wrapper leaves a marker the spec declares redundant, as on a bean wire,
   * which bridges without one. The caller ends the sentence.
   */
  private String primitiveBridgeFix(
      TypeElement spec,
      WireShape wire,
      String name,
      String wireName,
      TypeMirror wireType,
      TypeMirror element,
      LeafSite site,
      boolean dropMarker) {
    TypeMirror wrapper = boxed(wireType);
    ExecutableElement leaf = findLeaf(spec, name, wrapper, element);
    boolean converted = leaf != null || processingEnv.getTypeUtils().isSameType(wrapper, element);
    return "Declare '"
        + wireName
        + "' on '"
        + wire.element().getSimpleName()
        + "' as "
        + wrapper
        + (leaf != null ? ", which the leaf '" + leaf.getSimpleName() + "()' then converts" : "")
        + (converted
            ? dropMarker ? ", and remove the annotation, which a bean wire does not need" : ""
            : " and add " + site.declaration(name, wrapper, element) + " to the spec");
  }

  /**
   * The (wire, domain) types a container pair lifts one by one: the elements of a {@code List},
   * {@code Set}, {@code Optional} or array, or the values of a {@code Map}; null for a pair that
   * converts whole. It is the pair every fix line names when it offers the leaf, or the spec, that
   * a lifting would take up, on every tier, so it names only what the lifting accepts: an array
   * whose element can name its constructor, a {@code Map} whose keys pass through or convert
   * through a key leaf, and no wildcard, over which no leaf can be declared. Any other container is
   * offered the leaf over the whole container, the one it can declare.
   */
  private TypeMirror[] liftedPair(
      TypeElement spec, String name, TypeMirror wireType, TypeMirror domainType) {
    TypeMirror[] lifted = containerElementPair(spec, name, wireType, domainType);
    return lifted == null
            || Arrays.stream(lifted).anyMatch(type -> type.getKind() == TypeKind.WILDCARD)
        ? null
        : lifted;
  }

  private TypeMirror[] containerElementPair(
      TypeElement spec, String name, TypeMirror wireType, TypeMirror domainType) {
    TypeMirror[] elements = elementPair(wireType, domainType);
    if (elements != null) {
      return elements;
    }
    TypeMirror[] arrays = arrayPair(wireType, domainType);
    if (arrays != null) {
      return nameableArrayPair(arrays) ? arrays : null;
    }
    TypeMirror wireElement = containerElement(wireType, "java.util.Optional");
    TypeMirror domainElement = containerElement(domainType, "java.util.Optional");
    if (wireElement != null && domainElement != null) {
      return new TypeMirror[] {wireElement, domainElement};
    }
    DeclaredType[] maps = mapPair(wireType, domainType);
    if (maps == null) {
      return null;
    }
    // Keys that neither match nor convert are refused before any value leaf is consulted, so a
    // value leaf offered there would lead straight back to the same refusal.
    TypeMirror wireKey = maps[0].getTypeArguments().getFirst();
    TypeMirror domainKey = maps[1].getTypeArguments().getFirst();
    return processingEnv.getTypeUtils().isSameType(wireKey, domainKey)
            || findKeyLeaf(spec, name, wireKey, domainKey) != null
        ? new TypeMirror[] {maps[0].getTypeArguments().get(1), maps[1].getTypeArguments().get(1)}
        : null;
  }

  /**
   * The pair a fix line offers a leaf, or a spec, over: a container's elements when it lifts them
   * one by one ({@link #liftedPair}), else the pair itself. {@code parts} names what the container
   * lifts, a map value or an element, and is null for a whole pair; {@code records} is whether both
   * sides are records, the only pair a spec can map.
   */
  private record LeafOffer(TypeMirror wire, TypeMirror domain, String parts, boolean records) {

    boolean lifts() {
      return parts != null;
    }
  }

  private LeafOffer leafOffer(
      TypeElement spec, String name, TypeMirror wireType, TypeMirror domainType) {
    TypeMirror[] lifted = liftedPair(spec, name, wireType, domainType);
    TypeMirror wire = lifted == null ? wireType : lifted[0];
    TypeMirror domain = lifted == null ? domainType : lifted[1];
    return new LeafOffer(
        wire,
        domain,
        lifted == null ? null : isExactly(wireType, "java.util.Map") ? "map value" : "element",
        asRecord(domain) != null && asRecord(wire) != null);
  }

  /**
   * Classifies a mapping driven by its wire, where every wire component must name its source: a
   * projection, whose wire is smaller, so it maps lossily; or a build-only bean, which build fills
   * whatever its width, a derived field sourcing any component no domain component names ({@code
   * derived}, empty for a projection, which refuses them). Each pair resolves exactly like a
   * full-tier component (explicit leaf first, identity, nested specs, container lifting) via {@link
   * #resolveCorrespondence}, against the specs serving {@code need}. A projection whose reads are
   * all total keeps the lawful {@code asLens()} write-back; otherwise it maps as the validated
   * {@code patch} tier (see {@link #totalReads}).
   */
  private List<Correspondence> classifyWire(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      Map<String, String> renames,
      List<DerivedField> derived,
      WireShape.Direction need) {
    boolean built = wire.direction() == WireShape.Direction.BUILD_ONLY;
    Map<String, String> domainByWire = new LinkedHashMap<>();
    renames.forEach((domainName, wireName) -> domainByWire.put(wireName, domainName));
    List<String> domainNames =
        domain.getRecordComponents().stream().map(c -> c.getSimpleName().toString()).toList();
    Set<String> usedDomain = new LinkedHashSet<>();
    List<Correspondence> result = new ArrayList<>();
    for (WireShape.WireComponent wireComponent : wire.components()) {
      String wireName = wireComponent.name();
      if (derived.stream().anyMatch(field -> field.wireName().equals(wireName))) {
        continue;
      }
      String name = domainByWire.getOrDefault(wireName, wireName);
      RecordComponentElement domainComponent =
          domain.getRecordComponents().stream()
              .filter(c -> c.getSimpleName().contentEquals(name))
              .findFirst()
              .orElse(null);
      if (domainComponent == null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            (built ? "build-only field '" : "projection field '")
                + wire.element().getSimpleName()
                + "."
                + wireName
                + "' has no domain source.",
            "'"
                + wire.element().getSimpleName()
                + (built
                    ? "' maps build-only, so build fills each of its components from a domain"
                        + " component or a derived field."
                    : "' is smaller than '"
                        + domain.getSimpleName()
                        + "', so it maps as a projection: every wire component must name a domain"
                        + " component.")
                + " Found on "
                + domain.getSimpleName()
                + ": "
                + wireNames(domain.getRecordComponents())
                + ".",
            // A derived field's Getter names the component's type as a type argument, which a
            // primitive cannot be, so only a reference component is offered one.
            built && !wireComponent.type().getKind().isPrimitive()
                ? "Align the component names, add a @MapField rename, or declare a derived field"
                    + " 'default Getter<"
                    + domain.getSimpleName()
                    + ", "
                    + ProcessorUtils.simpleTypeName(wireComponent.type())
                    + "> "
                    + wireName
                    + "()' that computes it."
                : "Align the component names, or add a @MapField rename.");
        return null;
      }
      if (!usedDomain.add(name)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "domain component '" + name + "' sources more than one wire component.",
            built
                ? "Each wire component takes its own source, and a @MapField rename moves its domain"
                    + " component to the wire component it names, so the same-named one is left"
                    + " without one."
                : "The projection write-back (asLens or patch) writes each wire component back to"
                    + " its own domain component; a shared source would discard one wire value on"
                    + " write-back.",
            built
                ? "Declare a derived field for one of the two wire components, or drop one of them."
                : "Point the @MapField rename at a different domain component, or drop one wire"
                    + " component.");
        return null;
      }
      Correspondence resolved =
          resolveCorrespondence(
              spec,
              registry,
              domain,
              wire,
              name,
              wireName,
              wireComponent.type(),
              componentType(domainDeclared, domainComponent),
              domainNames,
              need,
              !built);
      if (resolved == null) {
        return null;
      }
      result.add(resolved);
    }
    // A derived field sources the wire component the loop above left to it.
    derived.stream().map(MappingProcessor::derivedCorrespondence).forEach(result::add);
    return result;
  }

  /**
   * The container analogue of the whole-component leaf check, applied BEFORE the identity
   * short-circuit (by the dense tiers and {@link #classifyUpdate} alike): an explicit
   * ELEMENT/VALUE-typed leaf on a {@code List}, {@code Set}, {@code Optional} or {@code Map}
   * component wins even when both sides declare the same container type, so a normalising {@code
   * ValidatedPrism<X, X>} still runs. For {@code Map} the key types must already match (keys are
   * identity-only); mismatches fall through to the post-identity diagnostics. Returns null when no
   * such leaf exists. {@code site} is how the unnameable-array refusal declares the whole-array
   * leaf.
   */
  private Correspondence containerLeafCorrespondence(
      TypeElement spec,
      String name,
      String wireName,
      TypeMirror wireType,
      TypeMirror domainType,
      LeafSite site) {
    TypeMirror[] elements = elementPair(wireType, domainType);
    if (elements != null) {
      return elementLeafCorrespondence(
          spec, name, wireName, Kind.ELEMENTS, elements[0], elements[1]);
    }
    TypeMirror[] arrayElements = arrayPair(wireType, domainType);
    if (arrayElements != null) {
      ExecutableElement leaf = findLeaf(spec, name, arrayElements[0], arrayElements[1]);
      if (!nameableArrayPair(arrayElements)) {
        // A leafless pair falls through: same-typed, it copies by identity, which needs no array
        // creation and so stays legal. A leaf CANNOT be honoured, and dropping it silently would
        // leave the component copied unvalidated - the typo'd-leaf hazard, so it is reported.
        if (leaf != null) {
          reportUnnameableArray(spec, name, arrayElements, site);
        }
        return null;
      }
      return leaf == null
          ? null
          : new Correspondence(
                  name, wireName, Kind.ARRAY, CodeBlock.of("$L()", leaf.getSimpleName()))
              .withDomainElement(ProcessorUtils.typeNameOf(arrayElements[1]));
    }
    TypeMirror wireElement = containerElement(wireType, "java.util.Optional");
    TypeMirror domainElement = containerElement(domainType, "java.util.Optional");
    if (wireElement != null && domainElement != null) {
      return elementLeafCorrespondence(
          spec, name, wireName, Kind.OPTIONAL, wireElement, domainElement);
    }
    return mapLeafCorrespondence(spec, name, wireName, wireType, domainType);
  }

  /**
   * The {@code Map} half of {@link #containerLeafCorrespondence}: a value leaf named after the
   * component, a key leaf a {@code @MapKey} names, or both.
   *
   * <p>Without a key leaf the key types must already match — keys pass through by identity, and a
   * mismatch falls through to the post-identity diagnostic that offers {@code @MapKey} as the fix.
   * With one, the keys convert and the values are copied, lifted through their own leaf, or (back
   * in {@link #resolvePair}) through a nested spec.
   */
  private Correspondence mapLeafCorrespondence(
      TypeElement spec, String name, String wireName, TypeMirror wireType, TypeMirror domainType) {
    DeclaredType[] pair = mapPair(wireType, domainType);
    if (pair == null) {
      return null;
    }
    TypeMirror wireKey = pair[0].getTypeArguments().getFirst();
    TypeMirror domainKey = pair[1].getTypeArguments().getFirst();
    TypeMirror wireValue = pair[0].getTypeArguments().get(1);
    TypeMirror domainValue = pair[1].getTypeArguments().get(1);
    ExecutableElement keyLeaf = findKeyLeaf(spec, name, wireKey, domainKey);
    ExecutableElement valueLeaf = findLeaf(spec, name, wireValue, domainValue);
    if (keyLeaf == null) {
      return valueLeaf != null && processingEnv.getTypeUtils().isSameType(wireKey, domainKey)
          ? new Correspondence(
              name, wireName, Kind.MAP, CodeBlock.of("$L()", valueLeaf.getSimpleName()))
          : null;
    }
    CodeBlock keys = CodeBlock.of("$L()", keyLeaf.getSimpleName());
    if (valueLeaf != null) {
      return new Correspondence(name, wireName, Kind.MAP_ENTRIES, keys)
          .withValuePrism(CodeBlock.of("$L()", valueLeaf.getSimpleName()));
    }
    return processingEnv.getTypeUtils().isSameType(wireValue, domainValue)
        ? new Correspondence(name, wireName, Kind.MAP_KEYS, keys)
        : null;
  }

  /**
   * The key leaf a {@code @MapKey} declares for {@code name}, or null. Its shape is a value leaf's,
   * over the KEY types; {@link #checkMapKeysApply} has already reported an annotation that names no
   * {@code Map} component, so a null here means only that the leaf does not convert this pair.
   */
  private ExecutableElement findKeyLeaf(
      TypeElement spec, String name, TypeMirror wireKey, TypeMirror domainKey) {
    for (ExecutableElement method : specMembers(spec)) {
      MapKey declared = method.getAnnotation(MapKey.class);
      if (declared != null
          && declared.value().equals(name)
          && leafConverts(spec, method, wireKey, domainKey)) {
        return method;
      }
    }
    return null;
  }

  /**
   * Whether a key leaf declared on this spec is reached, refusing one that a leaf over the whole
   * component pre-empts. The whole-component leaf is tried before a {@code Map}'s key and value
   * leaves, so the keys never reach the key leaf, and a key leaf that silently never runs is the
   * typo'd-leaf hazard again. An inherited key leaf stays inert, as one naming nothing does: a
   * mix-in may carry it for specs that map the component by its parts, while this one maps it
   * whole. {@code site} is how a leaf is declared in the whole leaf's place, carrying the
   * annotation where a record wire's bridge needs it.
   */
  private boolean checkKeyLeafReached(
      TypeElement spec,
      String name,
      ExecutableElement wholeLeaf,
      TypeMirror wireType,
      TypeMirror domainType,
      LeafSite site) {
    ExecutableElement keyLeaf =
        specMembers(spec).stream()
            .filter(method -> declaredLocally(method, spec))
            .filter(
                method ->
                    method.getAnnotation(MapKey.class) instanceof MapKey declared
                        && declared.value().equals(name))
            .findFirst()
            .orElse(null);
    if (keyLeaf == null) {
      return true;
    }
    String whole = "'" + wholeLeaf.getSimpleName() + "()'";
    String key = "'" + keyLeaf.getSimpleName() + "()'";
    String keepWhole = "remove " + key + " to keep converting '" + name + "' whole";
    // Mapping by parts is offered only where it works once the whole leaf goes: the spec declares
    // that leaf, so it can replace it, and the key leaf converts the keys of the Map left, which
    // on a bean is the one its Optional bridges. The value leaf beside it is offered only where
    // the values can be named (liftedPair).
    TypeMirror domainMap =
        containerElement(domainType, "java.util.Optional") instanceof TypeMirror element
            ? bridgeElement(element)
            : domainType;
    DeclaredType[] maps = declaredLocally(wholeLeaf, spec) ? mapPair(wireType, domainMap) : null;
    TypeMirror[] values =
        maps != null
                && findKeyLeaf(
                        spec,
                        name,
                        maps[0].getTypeArguments().getFirst(),
                        maps[1].getTypeArguments().getFirst())
                    != null
            ? liftedPair(spec, name, wireType, domainMap)
            : null;
    String fix;
    if (values == null) {
      fix = ProcessorUtils.capitalise(keepWhole) + ".";
    } else if (processingEnv.getTypeUtils().isSameType(values[0], values[1])) {
      // A record wire's bridge is declared by the annotation, so the whole leaf gives way to the
      // bare marker rather than to nothing.
      fix =
          (site.annotation().isEmpty()
                  ? "Remove " + whole
                  : "Replace "
                      + whole
                      + " with the marker '"
                      + site.annotation()
                      + processingEnv
                          .getTypeUtils()
                          .getDeclaredType(
                              processingEnv.getElementUtils().getTypeElement("java.util.Optional"),
                              domainMap)
                      + " "
                      + name
                      + "();'")
              + ", so the keys convert through "
              + key
              + " and the values copy, or "
              + keepWhole
              + ".";
    } else {
      fix =
          "Replace "
              + whole
              + " with the value leaf '"
              + site.annotation()
              + "default ValidatedPrism<"
              + values[0]
              + ", "
              + values[1]
              + "> "
              + name
              + "()', which converts the values while "
              + key
              + " converts the keys, or "
              + keepWhole
              + ".";
    }
    Diagnostics.error(
        processingEnv.getMessager(),
        keyLeaf,
        TAG,
        "@MapKey(\""
            + name
            + "\") leaf "
            + key
            + " never runs: "
            + whole
            + inheritedNote(wholeLeaf, spec)
            + " is a leaf over the whole component '"
            + name
            + "'.",
        "A whole-component leaf is tried before a Map's key and value leaves.",
        fix);
    return false;
  }

  /**
   * The (wire, domain) element types when both sides declare the <em>same</em> element container —
   * exactly {@code List} or exactly {@code Set}, each parameterised — else null.
   *
   * <p>Lifting needs the same container on both sides, so a {@code List} against a {@code Set} is
   * not a pair: it falls through to the no-usable-source diagnostic like any other mismatch, rather
   * than silently changing the collection's contract. A subtype ({@code ArrayList}) is not a pair
   * either, matching {@code Map}'s exactness rule.
   */
  private TypeMirror[] elementPair(TypeMirror wireType, TypeMirror domainType) {
    for (String container : ELEMENT_CONTAINERS) {
      TypeMirror wireElement = containerElement(wireType, container);
      TypeMirror domainElement = containerElement(domainType, container);
      if (wireElement != null && domainElement != null) {
        return new TypeMirror[] {wireElement, domainElement};
      }
    }
    return null;
  }

  /**
   * The (wire, domain) component types when both sides are arrays of <em>reference</em> elements,
   * else null.
   *
   * <p>A primitive array has no element mapping to do — a {@code ValidatedPrism} cannot focus a
   * primitive, and a primitive element cannot be null either — so it stays a plain identity copy
   * rather than reaching a lifting route it could never satisfy.
   */
  private static TypeMirror[] arrayPair(TypeMirror wireType, TypeMirror domainType) {
    return wireType instanceof ArrayType wireArray
            && domainType instanceof ArrayType domainArray
            && !wireArray.getComponentType().getKind().isPrimitive()
            && !domainArray.getComponentType().getKind().isPrimitive()
        ? new TypeMirror[] {wireArray.getComponentType(), domainArray.getComponentType()}
        : null;
  }

  /**
   * Whether an array of this element type can name its own constructor, which a lifted array's
   * emission needs on both sides ({@code Domain[]::new} to parse into, {@code Wire[]::new} to build
   * into).
   *
   * <p>Java forbids <em>generic array creation</em>, so a type variable ({@code T[]::new}) and a
   * parameterised type ({@code List<String>[]::new}) are both rejected by javac — inside the
   * generated Impl, where the author cannot fix them. Raw and unbounded-wildcard element types are
   * reifiable and compile, as does an array of anything nameable (or of a primitive).
   */
  private static boolean canNameArrayConstructor(TypeMirror element) {
    return switch (element.getKind()) {
      // An unresolved element, which only a class file naming a type missing from the classpath can
      // still carry here, steps aside rather than reading as unnameable.
      case ERROR -> true;
      case DECLARED ->
          ((DeclaredType) element)
              .getTypeArguments().stream()
                  .allMatch(
                      argument ->
                          argument instanceof WildcardType wildcard
                              && wildcard.getExtendsBound() == null
                              && wildcard.getSuperBound() == null);
      case ARRAY -> {
        TypeMirror component = ((ArrayType) element).getComponentType();
        yield component.getKind().isPrimitive() || canNameArrayConstructor(component);
      }
      default -> false;
    };
  }

  /** Whether both sides of an array pair can name their constructors, as lifting needs. */
  private static boolean nameableArrayPair(TypeMirror[] elements) {
    return canNameArrayConstructor(elements[0]) && canNameArrayConstructor(elements[1]);
  }

  /**
   * The one shape a lifted array cannot emit: an element type no {@code new} can name. Reported
   * rather than left to the no-usable-source fall-through, which would offer an element leaf the
   * emission could never carry. The whole-array leaf it offers instead is declared as {@code site}
   * declares one, so on a component carrying the {@code @OptionalBridge} marker it carries the
   * annotation and replaces the marker.
   */
  private void reportUnnameableArray(
      TypeElement spec, String name, TypeMirror[] elements, LeafSite site) {
    TypeMirror offending = canNameArrayConstructor(elements[1]) ? elements[0] : elements[1];
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "field '"
            + name
            + "' is an array whose element type "
            + offending
            + " cannot name an array constructor.",
        "Lifting an array builds a new array of the element type, and Java forbids creating an"
            + " array of a type variable or of a parameterised type, so the generated '"
            + offending
            + "[]::new' would not compile.",
        "Declare the component as a List on both sides, which lifts the same way and needs no"
            + " array creation, or map the arrays whole with the leaf "
            + site.declaration(
                name,
                processingEnv.getTypeUtils().getArrayType(elements[0]),
                processingEnv.getTypeUtils().getArrayType(elements[1]))
            + " over the array types.");
  }

  /**
   * The (wire, domain) pair as parameterised {@code Map} types, else null — key sameness NOT
   * required, because a {@code @MapKey} leaf may convert them. {@link #liftedPair} adds the key
   * gate where a fix line must offer only a value leaf the resolver would consult.
   */
  private DeclaredType[] mapPair(TypeMirror wireType, TypeMirror domainType) {
    DeclaredType wireMap = asMapType(wireType);
    DeclaredType domainMap = asMapType(domainType);
    return wireMap != null
            && domainMap != null
            && wireMap.getTypeArguments().size() == 2
            && domainMap.getTypeArguments().size() == 2
        ? new DeclaredType[] {wireMap, domainMap}
        : null;
  }

  private Correspondence elementLeafCorrespondence(
      TypeElement spec,
      String name,
      String wireName,
      Kind kind,
      TypeMirror wireElement,
      TypeMirror domainElement) {
    ExecutableElement leaf = findLeaf(spec, name, wireElement, domainElement);
    return leaf == null
        ? null
        : new Correspondence(name, wireName, kind, CodeBlock.of("$L()", leaf.getSimpleName()));
  }

  private ExecutableElement findLeaf(
      TypeElement spec, String name, TypeMirror wireType, TypeMirror domainType) {
    for (ExecutableElement method : specMembers(spec)) {
      // A @MapKey leaf converts the KEY side of the component its annotation names, never the
      // component its own name would suggest, so it is never a value leaf.
      if (!method.getSimpleName().contentEquals(name)
          || method.getAnnotation(MapField.class) != null
          || method.getAnnotation(MapKey.class) != null) {
        continue;
      }
      if (leafConverts(spec, method, wireType, domainType)) {
        return method;
      }
    }
    return null;
  }

  /**
   * Whether {@code method} is leaf-shaped and converts exactly this pair: a zero-parameter {@code
   * default} or abstract method returning {@code ValidatedPrism<wireType, domainType>}. Shared by
   * the value leaves ({@link #findLeaf}, matched by name) and the key leaves ({@link #findKeyLeaf},
   * matched by {@code @MapKey}), so the two can never drift on what counts as a leaf.
   */
  private boolean leafConverts(
      TypeElement spec, ExecutableElement method, TypeMirror wireType, TypeMirror domainType) {
    boolean leafShaped = method.isDefault() || method.getModifiers().contains(Modifier.ABSTRACT);
    if (!leafShaped || !method.getParameters().isEmpty()) {
      return false;
    }
    if (!(memberTypeIn(spec, method) instanceof DeclaredType returnType)) {
      return false;
    }
    TypeElement raw = (TypeElement) returnType.asElement();
    if (!raw.getQualifiedName().contentEquals(VALIDATED_PRISM)
        || returnType.getTypeArguments().size() != 2) {
      return false;
    }
    return processingEnv.getTypeUtils().isSameType(returnType.getTypeArguments().get(0), wireType)
        && processingEnv
            .getTypeUtils()
            .isSameType(returnType.getTypeArguments().get(1), domainType);
  }

  /**
   * Says why a same-named default method is not the pair's leaf, spelling out the leaves that would
   * be: the one over the whole pair, and the one over its elements where a container lifts them.
   * Neither exists for a primitive side, which no {@code ValidatedPrism} can name, and which the
   * caller explains.
   */
  private String leafNearMissHint(
      TypeElement spec, String name, TypeMirror wireType, TypeMirror domainType) {
    ExecutableElement method = sameNamedDefault(spec, name);
    if (method == null) {
      return "";
    }
    LeafOffer offer = leafOffer(spec, name, wireType, domainType);
    return " A default method '"
        + name
        + "()' exists but returns '"
        + memberTypeIn(spec, method)
        + "'"
        + (method.getParameters().isEmpty() ? "" : " and declares parameters")
        + (hasPrimitive(wireType, domainType)
            ? "."
            : " — a leaf must be a zero-parameter default method returning exactly"
                + " ValidatedPrism<"
                + wireType
                + ", "
                + domainType
                + "> (wire first, domain second)"
                + (offer.lifts()
                    ? ", or ValidatedPrism<"
                        + offer.wire()
                        + ", "
                        + offer.domain()
                        + "> over the "
                        + offer.parts()
                        + " types"
                    : "")
                + ".");
  }

  /**
   * The lifting rule, said where two containers meet that do not lift as declared: element lifting,
   * through a leaf or a spec, reaches one level into a component both sides declare as the same one
   * of the containers {@link #liftedPair} accepts. Said for containers that are not those, and for
   * lifted elements that are containers again, where a leaf over the inner elements is the likely
   * mistake. Empty for any other pair.
   */
  private String liftingRuleHint(
      TypeElement spec, String name, TypeMirror wireType, TypeMirror domainType) {
    LeafOffer offer = leafOffer(spec, name, wireType, domainType);
    return containerLike(offer.wire()) && containerLike(offer.domain())
        ? " Element lifting, through a leaf or a spec, reaches one level into a component both"
            + " sides declare as the same container, named exactly: List, Set, Optional, an array"
            + " of a reference type, or a Map, whose values lift, each over an element type named"
            + " rather than a wildcard."
        : "";
  }

  /** Whether a type is a container of any kind: an array, an Optional, a Collection or a Map. */
  private boolean containerLike(TypeMirror type) {
    Types types = processingEnv.getTypeUtils();
    Elements elements = processingEnv.getElementUtils();
    return type.getKind() == TypeKind.ARRAY
        || isExactly(type, "java.util.Optional")
        || Stream.of("java.util.Collection", "java.util.Map")
            .anyMatch(
                container ->
                    types.isSubtype(
                        types.erasure(type),
                        types.erasure(elements.getTypeElement(container).asType())));
  }

  /**
   * For a container pair that does not lift as declared, the declaration that would: each side as
   * the exact container it already is ({@code ArrayList<E>} is a {@code List<E>}), its elements
   * named rather than bounded by an {@code extends} wildcard. Offered only when that pair has a
   * source, so that following it maps the component: the elements copy, a leaf named after the
   * component converts them, or the one plain spec serving the site maps them (an element-mapped
   * one resolves only once its own leaves do). Empty otherwise, for a pair that lifts as declared,
   * whose elements would have resolved already if they had a source, and for a generic record,
   * whose declaration names type variables where these types are instantiated.
   */
  private String liftableOffer(MemberSite member, TypeMirror wireType, TypeMirror domainType) {
    TypeMirror wireView = liftableView(wireType);
    TypeMirror domainView = liftableView(domainType);
    TypeMirror[] elements =
        Stream.of(member.domain(), member.wire().element())
                .anyMatch(owner -> !owner.getTypeParameters().isEmpty())
            ? null
            : liftedPair(member.spec(), member.name(), wireView, domainView);
    if (elements == null) {
      return "";
    }
    ExecutableElement leaf = findLeaf(member.spec(), member.name(), elements[0], elements[1]);
    List<RegisteredSpec> serving =
        servingCandidates(member.spec(), member.registry(), elements[0], elements[1], member.need())
            .chosen();
    String through =
        processingEnv.getTypeUtils().isSameType(elements[0], elements[1])
            ? "copies"
            : leaf != null
                ? "lifts through the leaf '" + leaf.getSimpleName() + "()'"
                : serving.size() == 1 && abstractLeaves(serving.getFirst().spec()).isEmpty()
                    ? "lifts through '" + serving.getFirst().describe() + "'"
                    : null;
    return through == null
        ? ""
        : ", or declare the component as "
            + domainView
            + " on '"
            + member.domain().getSimpleName()
            + "' and as "
            + wireView
            + " on '"
            + member.wire().element().getSimpleName()
            + "', which then "
            + through;
  }

  /**
   * The exact container a type already is, as lifting names it: its {@code List}, {@code Set},
   * {@code Optional} or {@code Map} supertype, an {@code extends} wildcard argument read as its
   * bound. The type itself when it is none of those, or when an argument is unbounded or bounded
   * below, which names no element type to declare.
   */
  private TypeMirror liftableView(TypeMirror type) {
    if (!(type instanceof DeclaredType)) {
      return type;
    }
    Types types = processingEnv.getTypeUtils();
    for (String container : LIFTABLE_CONTAINERS) {
      if (ProcessorUtils.supertypeOf(
              types, type, processingEnv.getElementUtils().getTypeElement(container))
          instanceof DeclaredType view) {
        List<TypeMirror> arguments =
            view.getTypeArguments().stream().map(ProcessorUtils::resolveWildcard).toList();
        return arguments.stream().anyMatch(argument -> argument == null)
            ? type
            : types.getDeclaredType(
                (TypeElement) view.asElement(), arguments.toArray(TypeMirror[]::new));
      }
    }
    return type;
  }

  /** How a diagnostic names one wire member: a bean has properties, a record has components. */
  private static String wireMemberTerm(WireShape wire) {
    return switch (wire) {
      case WireShape.BeanShape _ -> "bean property";
      case WireShape.RecordShape _ -> "record component";
    };
  }

  /**
   * The {@link OptionalBridge} half of the no-usable-source fix, offered first when the pair has
   * the bridge's shape: a domain {@code Optional<E>} against a wire component that can hold the
   * {@code null} encoding absence. The alternative the generic fix goes on to offer is a leaf over
   * the whole {@code Optional}, which maps the pair without giving it an absent state, so the
   * declaration that does is named first, and in full: both offers are pasteable, and the author
   * chooses by what the field means. The bare marker is offered wherever the present element needs
   * no leaf: it copies, or a single mapping serving the site covers the element pair, which the
   * bridge nests through. With two, the element leaf that chooses between them is offered instead.
   * Empty when the shape cannot bridge. Never asked of a primitive wire member, which cannot hold
   * the {@code null} and is offered its wrapper first ({@link #primitiveFix}).
   */
  private String bridgeOffer(
      TypeElement spec,
      List<RegisteredSpec> registry,
      String name,
      TypeMirror wireType,
      TypeMirror domainType,
      WireShape.Direction need) {
    TypeMirror declaredElement = containerElement(domainType, "java.util.Optional");
    if (declaredElement == null || containerElement(wireType, "java.util.Optional") != null) {
      return "";
    }
    TypeMirror element = bridgeElement(declaredElement);
    // A container's elements lift one by one, as they would unbridged, so the spec the bridge
    // nests them through, and the leaf that would stand in for it, are over the elements.
    LeafOffer offer = leafOffer(spec, name, wireType, element);
    boolean needsNoLeaf =
        processingEnv.getTypeUtils().isSameType(wireType, element)
            || servingCandidates(spec, registry, offer.wire(), offer.domain(), need).chosen().size()
                == 1;
    return needsNoLeaf
        ? "Add '@OptionalBridge "
            + domainType
            + " "
            + name
            + "();' to the spec, so an absent value reads as a null wire component and back. "
        : "Add '@OptionalBridge default ValidatedPrism<"
            + offer.wire()
            + ", "
            + offer.domain()
            + "> "
            + name
            + "()' to the spec, a leaf over the ELEMENT types, so an absent value reads as a null"
            + " wire component and a present one converts. ";
  }

  private String unusableSpecHint(
      List<RegisteredSpec> registry,
      TypeMirror wireType,
      TypeMirror domainType,
      WireShape.Direction need) {
    return unusableSpecHint(
        registry, wireType, domainType, need, "be nested in a mapping that " + site(need));
  }

  /**
   * Names a spec that maps the pair but does not serve the site, and what it lacks; {@code purpose}
   * is what the site would have used it for. Empty when no registered spec maps the pair.
   */
  private String unusableSpecHint(
      List<RegisteredSpec> registry,
      TypeMirror wireType,
      TypeMirror domainType,
      WireShape.Direction need,
      String purpose) {
    return registry.stream()
        .filter(r -> !r.serves(need))
        .filter(
            r ->
                processingEnv.getTypeUtils().isSameType(r.domain(), domainType)
                    && processingEnv.getTypeUtils().isSameType(r.wire(), wireType))
        .findFirst()
        .map(r -> r.unusable("maps this pair", purpose, need))
        .orElse("");
  }

  /** How a nesting site reads in a hint: what the mapping doing the nesting does. */
  private static String site(WireShape.Direction need) {
    return switch (need) {
      case BIDIRECTIONAL -> "builds and parses";
      case PARSE_ONLY -> "only parses";
      case BUILD_ONLY -> "only builds";
    };
  }

  /**
   * The expression {@code build} fills a wire component with, from its correspondence. The value is
   * the same on either wire shape; only the write around it differs.
   */
  private static CodeBlock buildValue(WireShape.WireComponent wc, List<Correspondence> comps) {
    // Classification claims every wire component exactly once before emission, so the lookup
    // cannot miss; there is deliberately no fallback to cover.
    Correspondence c =
        comps.stream().filter(x -> x.wireName().equals(wc.name())).findFirst().orElseThrow();
    return switch (c.kind()) {
      case LEAF, ELEMENTS, ARRAY, MAP, MAP_KEYS, MAP_ENTRIES -> buildCall(c, wc).on(domainRead(c));
      case OPTIONAL -> CodeBlock.of("$L.map($L)", domainRead(c), buildCall(c, wc).asFunction());
      // The domain Optional is carried as-is, or its present value built as the unbridged pair's.
      case OPTIONAL_BRIDGE -> bridgeBuildValue(wc, c);
      case IDENTITY, IDENTITY_ELEMENTS, IDENTITY_MAP -> domainRead(c);
      case DERIVED -> CodeBlock.of("$L.get(domain)", c.prism());
    };
  }

  /**
   * One {@code ValidatedPrism} call a converting correspondence makes: the prism, the method, and
   * the argument that follows the value in the two-argument bulk forms (an array constructor, or a
   * {@code Map}'s value prism), null in the one-argument forms. A leg applies the call to a value
   * it holds ({@link #on}) or hands it on as a function ({@link #asFunction}), and both render from
   * here, so a bridged leg makes exactly the call its unbridged pair makes, on either tier.
   */
  private record PrismCall(CodeBlock prism, String method, CodeBlock trailing) {

    CodeBlock on(CodeBlock value) {
      return trailing == null
          ? CodeBlock.of("$L.$L($L)", prism, method, value)
          : CodeBlock.of("$L.$L($L, $L)", prism, method, value, trailing);
    }

    /** A two-argument form cannot be a bare method reference, so it rides in a lambda. */
    CodeBlock asFunction() {
      return trailing == null
          ? CodeBlock.of("$L::$L", prism, method)
          : CodeBlock.of("v -> $L", on(CodeBlock.of("v")));
    }
  }

  /**
   * The call {@code build} makes for a converting correspondence: a leaf's {@code build}, or the
   * bulk form its container lifts through. The wire element type names the array a lifted array
   * renders into.
   */
  private static PrismCall buildCall(Correspondence c, WireShape.WireComponent wc) {
    return switch (c.kind()) {
      case ELEMENTS -> new PrismCall(c.prism(), "buildAll", null);
      case ARRAY ->
          new PrismCall(
              c.prism(),
              "buildAll",
              CodeBlock.of(
                  "$T[]::new",
                  ProcessorUtils.typeNameOf(((ArrayType) wc.type()).getComponentType())));
      case MAP -> new PrismCall(c.prism(), "buildValues", null);
      case MAP_KEYS -> new PrismCall(c.prism(), "buildKeys", null);
      // The key prism is the receiver, and the value prism rides as the argument.
      case MAP_ENTRIES -> new PrismCall(c.prism(), "buildEntries", c.valuePrism());
      default -> new PrismCall(c.prism(), "build", null);
    };
  }

  /**
   * The call {@code parse} makes for a converting kind: a leaf's {@code parse}, or the bulk form
   * its container lifts through. The domain element type names the array a lifted array parses
   * into. Taken apart rather than as a correspondence because the sparse tier's edits carry the
   * same four parts.
   */
  private static PrismCall parseCall(
      Kind kind, CodeBlock prism, TypeName domainElement, CodeBlock valuePrism) {
    return switch (kind) {
      case ELEMENTS -> new PrismCall(prism, "parseAll", null);
      case ARRAY -> new PrismCall(prism, "parseAll", CodeBlock.of("$T[]::new", domainElement));
      case MAP -> new PrismCall(prism, "parseValues", null);
      case MAP_KEYS -> new PrismCall(prism, "parseKeys", null);
      case MAP_ENTRIES -> new PrismCall(prism, "parseEntries", valuePrism);
      default -> new PrismCall(prism, "parse", null);
    };
  }

  private static PrismCall parseCall(Correspondence c) {
    return parseCall(c.kind(), c.prism(), c.domainElement(), c.valuePrism());
  }

  /**
   * The domain-side read {@code build} takes a correspondence's value from: the component itself,
   * or, for a member of a flattened group, the component of the group's record.
   */
  private static CodeBlock domainRead(Correspondence c) {
    return c.group() == null
        ? CodeBlock.of("domain.$L()", c.name())
        : CodeBlock.of("domain.$L().$L()", c.group().name(), c.name());
  }

  /**
   * The bridged build value: the domain {@code Optional}, carried as-is when its present value
   * copies, or mapped through the call its unbridged pair's build makes, then unwrapped to the
   * {@code null} that encodes absence.
   *
   * <p>The {@code null} is written on a bean wire too, never skipped. A bean may initialise the
   * field behind a property ({@code status = "ACTIVE"}), and a skipped write would leave that
   * default in place for {@code parse} to read back as present, breaking the round trip with
   * nothing to say so. Writing it makes {@code build} independent of how the bean was constructed.
   */
  private static CodeBlock bridgeBuildValue(WireShape.WireComponent wc, Correspondence c) {
    CodeBlock present =
        c.present().prism() == null
            ? domainRead(c)
            : CodeBlock.of("$L.map($L)", domainRead(c), buildCall(c.present(), wc).asFunction());
    return CodeBlock.of("$L.orElse(null)", present);
  }

  /**
   * The total {@code build} body on either wire shape: the record constructor or the bean strategy.
   */
  private static CodeBlock wireBuildBody(
      WireShape wire, TypeName wireName, List<Correspondence> comps) {
    return wire.buildStatements(wireName, wc -> buildValue(wc, comps));
  }

  /**
   * The read expression for the wire component named {@code wireName}, from the {@code wire} var.
   */
  private static CodeBlock wireRead(WireShape wire, String wireName) {
    return wire.componentNamed(wireName).orElseThrow().readFrom("wire");
  }

  /**
   * The parse ladder's legs, one per domain component. A flattened component's members (contiguous
   * in classification order) fold into one leg carrying their own {@code fields()} ladder, which
   * assembles the group's record and locates every failure under the component's name ({@code
   * address.street}): the located-null doctrine and the leaf vocabulary apply inside the group
   * exactly as at the top level. A derived field contributes no leg.
   */
  private List<CodeBlock> parseLegs(WireShape wire, List<Correspondence> comps) {
    List<CodeBlock> legs = new ArrayList<>();
    for (List<Correspondence> run : runs(comps)) {
      Correspondence first = run.getFirst();
      if (first.group() == null) {
        // An unset bean property and a Jackson-bound missing record component both read null, so
        // every reference read is guarded before it reaches a leaf (whose parse rejects null) or
        // the identity copy; the guard locates the null under the field label.
        CodeBlock leg =
            parseLeg(wire, first, wireRead(wire, first.wireName()), guardedRead(first, wire));
        if (!leg.isEmpty()) {
          legs.add(leg);
        }
        continue;
      }
      CodeBlock.Builder inner = CodeBlock.builder().add("$T.fields()$>", VALIDATED);
      for (Correspondence member : run) {
        inner.add(
            parseLeg(wire, member, wireRead(wire, member.wireName()), guardedRead(member, wire)));
      }
      inner.add("\n.apply($T::new)$<", first.group().type());
      legs.add(CodeBlock.of("\n.field($S, $L)", first.group().name(), inner.build()));
    }
    return legs;
  }

  /**
   * The constructor arguments of {@code asIso()}'s reverse direction, in domain component order,
   * each read straight from the wire: a lossless mapping copies every component by identity, and a
   * flattened group's members reassemble its record in place.
   */
  private static CodeBlock reverseArgs(WireShape wire, List<Correspondence> comps) {
    return runs(comps).stream()
        .map(
            run ->
                run.getFirst().group() == null
                    ? wireRead(wire, run.getFirst().wireName())
                    : CodeBlock.of(
                        "new $T($L)",
                        run.getFirst().group().type(),
                        run.stream()
                            .map(member -> wireRead(wire, member.wireName()))
                            .collect(CodeBlock.joining(", "))))
        .collect(CodeBlock.joining(", "));
  }

  /**
   * The one place a group's contiguity is relied on: consecutive members of the same group fold
   * into one run, in the group record's component order (which {@link #classify} produces and the
   * group's ladder and constructor need); every other correspondence is a run of its own.
   */
  private static List<List<Correspondence>> runs(List<Correspondence> comps) {
    List<List<Correspondence>> runs = new ArrayList<>();
    for (Correspondence c : comps) {
      if (!runs.isEmpty()
          && c.group() != null
          && c.group().equals(runs.getLast().getFirst().group())) {
        runs.getLast().add(c);
      } else {
        runs.add(new ArrayList<>(List.of(c)));
      }
    }
    return runs;
  }

  /**
   * Whether a component's parse read must be null-guarded: every reference read, on both wire
   * shapes. An unset bean property is null, and Jackson binds a missing JSON property on a record
   * component to null just the same — "a record can never read null" is false at every JSON
   * boundary, so the guard policy is shape-independent. Only a primitive identity read (which
   * cannot be null) goes unguarded.
   */
  private static boolean guardedRead(Correspondence c, WireShape wire) {
    // Derived fields are not read; an Optional bridge maps null to Optional.empty, so both are
    // null-safe and never guarded.
    if (c.kind() == Kind.DERIVED || c.kind() == Kind.OPTIONAL_BRIDGE) {
      return false;
    }
    if (c.kind() == Kind.IDENTITY) {
      return !wire.componentNamed(c.wireName()).orElseThrow().type().getKind().isPrimitive();
    }
    return true;
  }

  /**
   * Whether a leg's guard is the {@code hkj$ifPresent} wrapper. Identity containers guard through
   * their own scanning helpers instead, so they need those emitted, not this one.
   */
  private static boolean usesIfPresent(Correspondence c, WireShape wire) {
    return guardedRead(c, wire)
        && c.kind() != Kind.IDENTITY_ELEMENTS
        && c.kind() != Kind.IDENTITY_MAP;
  }

  /**
   * Whether every correspondence reads totally, so the mapping can be an optic: {@code asIso()} on
   * a full mapping, {@code asLens()} on a projection. A fallible correspondence, or a guarded read
   * on a bean wire ({@link #lossyRead}), makes a read partial: the full tier then withholds {@code
   * asIso()}, and a projection maps as the validated {@code patch}. On a record wire this is
   * exactly "no fallible correspondence"; an all-primitive bean can never read null, so it keeps
   * both optics.
   */
  private static boolean totalReads(List<Correspondence> comps, WireShape wire) {
    return comps.stream().noneMatch(c -> c.fallible() || lossyRead(c, wire));
  }

  /**
   * Whether a guarded read is partial: only on a bean wire. A bean's reference property is
   * legitimately unset in normal use, so its guarded read can fail, and {@link #totalReads} counts
   * it. A record wire's guard exists for hostile input (a null-carrying JSON binding), not for a
   * representable absent state, so a lossless record mapping keeps {@code asIso()} — the parse-iso
   * coherence law is scoped to wires whose reference components are non-null.
   */
  private static boolean lossyRead(Correspondence c, WireShape wire) {
    return wire instanceof WireShape.BeanShape && guardedRead(c, wire);
  }

  /**
   * The {@code hkj$allPresent} guard for identity-copied {@code List} components: total over a null
   * list ({@code must not be null}, labelled by the ladder), and each null element is a located
   * invalid at its index, accumulating - the same doctrine {@code ValidatedParse#parseAll} enforces
   * on lifted legs. Valid lists are passed through by reference; identity legs copy, they do not
   * rebuild.
   */
  static MethodSpec allPresentHelper() {
    TypeVariableName e = TypeVariableName.get("E");
    TypeName listOfE = ParameterizedTypeName.get(ClassName.get("java.util", "List"), e);
    TypeName validatedOfList =
        ParameterizedTypeName.get(VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), listOfE);
    TypeName nelOfError = ParameterizedTypeName.get(NEL, FIELD_ERROR);
    return MethodSpec.methodBuilder("hkj$allPresent")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .addTypeVariable(e)
        .returns(validatedOfList)
        .addParameter(listOfE, "values")
        .addJavadoc(
            "Guards an identity-copied list: a null element is a located invalid at its index,"
                + " accumulating.\n")
        .beginControlFlow("if (values == null)")
        .addStatement("return $T.invalidNel($T.of($S))", VALIDATED, FIELD_ERROR, "must not be null")
        .endControlFlow()
        .addStatement("$T failures = null", nelOfError)
        // iterate rather than index: values.get(i) is quadratic on a LinkedList
        .addStatement("int i = 0")
        .beginControlFlow("for (Object element : values)")
        .beginControlFlow("if (element == null)")
        .addStatement(
            "$T located = $T.of($T.of($S).at($T.valueOf(i)))",
            nelOfError,
            NEL,
            FIELD_ERROR,
            "must not be null",
            ClassName.get(String.class))
        .addStatement(
            "failures = failures == null ? located : $T.<$T>semigroup().combine(failures,"
                + " located)",
            NEL,
            FIELD_ERROR)
        .endControlFlow()
        .addStatement("i++")
        .endControlFlow()
        .addStatement(
            "return failures == null ? $T.valid(values) : $T.invalid(failures)",
            VALIDATED,
            VALIDATED)
        .build();
  }

  /**
   * The {@code hkj$allPresent} overload for identity-copied {@code Set} components. A set has no
   * index, and a null element has no rendering to locate by either — but a set holds at most one,
   * so the failure needs no location: it reports as {@code must not contain a null element} under
   * the component's own label, distinct from {@code must not be null}, which says the set itself is
   * absent. Matches {@code ValidatedParse#parseAll(Set)}, exactly as the list form matches its own.
   */
  static MethodSpec allPresentSetHelper() {
    TypeVariableName e = TypeVariableName.get("E");
    TypeName setOfE = ParameterizedTypeName.get(ClassName.get("java.util", "Set"), e);
    TypeName validatedOfSet =
        ParameterizedTypeName.get(VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), setOfE);
    return MethodSpec.methodBuilder("hkj$allPresent")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .addTypeVariable(e)
        .returns(validatedOfSet)
        .addParameter(setOfE, "values")
        .addJavadoc(
            "Guards an identity-copied set: a null element is an unlocated invalid, a set holding"
                + " at most one.\n")
        .beginControlFlow("if (values == null)")
        .addStatement("return $T.invalidNel($T.of($S))", VALIDATED, FIELD_ERROR, "must not be null")
        .endControlFlow()
        .beginControlFlow("for ($T element : values)", ClassName.get(Object.class))
        .beginControlFlow("if (element == null)")
        .addStatement(
            "return $T.invalidNel($T.of($S))",
            VALIDATED,
            FIELD_ERROR,
            "must not contain a null element")
        .endControlFlow()
        .endControlFlow()
        .addStatement("return $T.valid(values)", VALIDATED)
        .build();
  }

  /**
   * The {@code hkj$allPresent} overload for identity-copied reference arrays: an array has stable
   * indices, so a null element locates by its index exactly as a list's does. A primitive array
   * never reaches here — it has no element that could be null.
   */
  static MethodSpec allPresentArrayHelper() {
    TypeVariableName e = TypeVariableName.get("E");
    TypeName arrayOfE = ArrayTypeName.of(e);
    TypeName validatedOfArray =
        ParameterizedTypeName.get(VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), arrayOfE);
    TypeName nelOfError = ParameterizedTypeName.get(NEL, FIELD_ERROR);
    return MethodSpec.methodBuilder("hkj$allPresent")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .addTypeVariable(e)
        .returns(validatedOfArray)
        .addParameter(arrayOfE, "values")
        .addJavadoc(
            "Guards an identity-copied array: a null element is a located invalid at its index,"
                + " accumulating.\n")
        .beginControlFlow("if (values == null)")
        .addStatement("return $T.invalidNel($T.of($S))", VALIDATED, FIELD_ERROR, "must not be null")
        .endControlFlow()
        .addStatement("$T failures = null", nelOfError)
        .beginControlFlow("for (int i = 0; i < values.length; i++)")
        .beginControlFlow("if (values[i] == null)")
        .addStatement(
            "$T located = $T.of($T.of($S).at($T.valueOf(i)))",
            nelOfError,
            NEL,
            FIELD_ERROR,
            "must not be null",
            ClassName.get(String.class))
        .addStatement(
            "failures = failures == null ? located : $T.<$T>semigroup().combine(failures,"
                + " located)",
            NEL,
            FIELD_ERROR)
        .endControlFlow()
        .endControlFlow()
        .addStatement(
            "return failures == null ? $T.valid(values) : $T.invalid(failures)",
            VALIDATED,
            VALIDATED)
        .build();
  }

  /**
   * The {@code hkj$valuesPresent} guard for identity-copied {@code Map} components: total over a
   * null map, and each null value is a located invalid under its key, accumulating - matching
   * {@code ValidatedParse#parseValues}. Keys are structural: a null key stays the caller's {@code
   * NullPointerException}, as in the bulk forms.
   */
  static MethodSpec valuesPresentHelper() {
    TypeVariableName k = TypeVariableName.get("K");
    TypeVariableName v = TypeVariableName.get("V");
    TypeName mapOfKv = ParameterizedTypeName.get(ClassName.get("java.util", "Map"), k, v);
    TypeName validatedOfMap =
        ParameterizedTypeName.get(VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), mapOfKv);
    TypeName nelOfError = ParameterizedTypeName.get(NEL, FIELD_ERROR);
    TypeName entry = ParameterizedTypeName.get(ClassName.get("java.util.Map", "Entry"), k, v);
    return MethodSpec.methodBuilder("hkj$valuesPresent")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .addTypeVariable(k)
        .addTypeVariable(v)
        .returns(validatedOfMap)
        .addParameter(mapOfKv, "values")
        .addJavadoc(
            "Guards an identity-copied map: a null value is a located invalid under its key,"
                + " accumulating; a null key is the caller's NullPointerException.\n")
        .beginControlFlow("if (values == null)")
        .addStatement("return $T.invalidNel($T.of($S))", VALIDATED, FIELD_ERROR, "must not be null")
        .endControlFlow()
        .addStatement("$T failures = null", nelOfError)
        .beginControlFlow("for ($T entry : values.entrySet())", entry)
        .addStatement("$T.requireNonNull(entry.getKey(), $S)", OBJECTS, "map keys must not be null")
        .beginControlFlow("if (entry.getValue() == null)")
        .addStatement(
            "$T located = $T.of($T.of($S).at($T.valueOf(entry.getKey())))",
            nelOfError,
            NEL,
            FIELD_ERROR,
            "must not be null",
            ClassName.get(String.class))
        .addStatement(
            "failures = failures == null ? located : $T.<$T>semigroup().combine(failures,"
                + " located)",
            NEL,
            FIELD_ERROR)
        .endControlFlow()
        .endControlFlow()
        .addStatement(
            "return failures == null ? $T.valid(values) : $T.invalid(failures)",
            VALIDATED,
            VALIDATED)
        .build();
  }

  /**
   * The {@code hkj$ifPresent} guard emitted into impls with guarded reads (both wire shapes): a
   * null read becomes a located {@code FieldError} (the {@code fields()} ladder attaches the
   * component label), so a null never reaches a leaf's {@code parse}, which rejects it. The name
   * lives in the {@code $} namespace, which JLS 3.8 reserves for mechanically generated code, so no
   * ordinary spec method can collide with the declaration or capture its call sites through
   * overload resolution — which is why the collision sweep needs no reservation for it. Shared with
   * {@link MergeProcessor}, whose fallible merge legs carry the same guard, like {@link
   * #scanRegistry}.
   */
  static MethodSpec ifPresentHelper() {
    TypeVariableName s = TypeVariableName.get("S");
    TypeVariableName a = TypeVariableName.get("A");
    TypeName validatedOfA =
        ParameterizedTypeName.get(VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), a);
    TypeName parseFn =
        ParameterizedTypeName.get(
            ClassName.get("java.util.function", "Function"),
            WildcardTypeName.supertypeOf(s),
            validatedOfA);
    return MethodSpec.methodBuilder("hkj$ifPresent")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .addTypeVariable(s)
        .addTypeVariable(a)
        .returns(validatedOfA)
        .addParameter(s, "value")
        .addParameter(parseFn, "parse")
        .addJavadoc(
            "Guards a nullable read: a null becomes a located {@code FieldError}, otherwise the"
                + " value is parsed.\n")
        .addStatement(
            "return value == null ? $T.invalidNel($T.of($S)) : parse.apply(value)",
            VALIDATED,
            FIELD_ERROR,
            "must not be null")
        .build();
  }

  private void writeImpl(
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      TypeMirror wireUsed,
      List<Correspondence> comps) {
    ClassName specName = ClassName.get(spec);
    ClassName implName = implClassName(spec);
    TypeName domainName = ProcessorUtils.typeNameOf(domainDeclared);
    TypeName wireName = ProcessorUtils.typeNameOf(wireUsed);
    // Derived fields are non-identity, so they exclude the Iso tier too: wire -> domain -> wire
    // recomputes the derived component, an identity only for wire values already consistent.
    boolean lossless = totalReads(comps, wire);

    List<EmittedMember> emitted = new ArrayList<>();
    emitted.add(EmittedMember.of("build", domainDeclared));
    emitted.add(EmittedMember.of("parse", wireUsed));
    emitted.add(EmittedMember.of("asValidatedPrism"));
    if (lossless) {
      emitted.add(EmittedMember.of("asIso"));
    }
    if (!checkNoEmittedCollisions(spec, "a full mapping", reserveFactoryIfGeneric(spec, emitted))) {
      return;
    }

    CodeBlock buildBody = wireBuildBody(wire, wireName, comps);

    CodeBlock reverseArgs = reverseArgs(wire, comps);
    List<AnnotationSpec> suppression = pairSuppression(domainDeclared, wire);

    TypeSpec.Builder implBuilder =
        implSkeleton(
                spec,
                implName,
                specName,
                "Generated bidirectional mapping for {@link $T}: total {@code build} and"
                    + " accumulating, located {@code parse}.\n",
                leafFields(spec))
            .addMethod(buildMethod(domainName, wireName, suppression, buildBody))
            .addMethod(
                parseMethod(domainName, wireName, suppression, parseBody(wire, comps, domainName)))
            .addMethod(asValidatedPrismMethod(wireName, domainName));

    addMarkerStubs(implBuilder, spec);
    addReadHelpers(implBuilder, comps, wire);

    if (lossless) {
      ClassName iso = ClassName.get("org.higherkindedj.optics", "Iso");
      implBuilder.addMethod(
          MethodSpec.methodBuilder("asIso")
              .addModifiers(Modifier.PUBLIC)
              .returns(ParameterizedTypeName.get(iso, domainName, wireName))
              .addJavadoc(
                  "The lossless mapping as an {@link $T}; emitted only when no fallible leaf and"
                      + " no derived field exists, so the round trip is total (truthful types).\n",
                  iso)
              .addStatement(
                  "return $T.of(this::build, wire -> new $T($L))", iso, domainName, reverseArgs)
              .build());
    }
    writeFile(spec, specName.packageName(), implBuilder.build());
  }

  /**
   * Emits the parse-only tier: a bean with getters and no way to be written. {@code parse} is the
   * full tier's, reading each domain component through its getter; there is no {@code build}, so no
   * {@code asValidatedPrism()} and no {@code asIso()}, and the mapping is exposed for nesting as
   * the {@code ValidatedParse} it is.
   */
  private void writeParseOnlyImpl(
      TypeElement spec,
      DeclaredType domainDeclared,
      WireShape wire,
      TypeMirror wireUsed,
      List<Correspondence> comps) {
    ClassName specName = ClassName.get(spec);
    TypeName domainName = ProcessorUtils.typeNameOf(domainDeclared);
    TypeName wireName = ProcessorUtils.typeNameOf(wireUsed);
    if (!checkNoEmittedCollisions(
        spec,
        "a parse-only mapping",
        List.of(EmittedMember.of("parse", wireUsed), EmittedMember.of("asValidatedParse")))) {
      return;
    }
    TypeSpec.Builder implBuilder =
        implSkeleton(
                spec,
                implClassName(spec),
                specName,
                "Generated parse-only mapping for {@link $T}: accumulating, located {@code parse}"
                    + " and no {@code build}, since the wire offers nothing to write it through"
                    + " (truthful types).\n",
                leafFields(spec))
            .addMethod(
                parseMethod(
                    domainName,
                    wireName,
                    pairSuppression(domainDeclared, wire),
                    parseBody(wire, comps, domainName)))
            .addMethod(asValidatedParseMethod(wireName, domainName));
    addMarkerStubs(implBuilder, spec);
    addReadHelpers(implBuilder, comps, wire);
    writeFile(spec, specName.packageName(), implBuilder.build());
  }

  /**
   * Emits the build-only tier: a bean that can be written but declares no getter. {@code build} is
   * the full tier's, filling each property from its domain component or a derived field; there is
   * no {@code parse}, so no {@code asValidatedPrism()} and no optic, and the mapping is exposed for
   * nesting as the {@code ValidatedBuild} it is.
   */
  private void writeBuildOnlyImpl(
      TypeElement spec,
      DeclaredType domainDeclared,
      WireShape wire,
      TypeMirror wireUsed,
      List<Correspondence> comps) {
    ClassName specName = ClassName.get(spec);
    TypeName domainName = ProcessorUtils.typeNameOf(domainDeclared);
    TypeName wireName = ProcessorUtils.typeNameOf(wireUsed);
    if (!checkNoEmittedCollisions(
        spec,
        "a build-only mapping",
        List.of(EmittedMember.of("build", domainDeclared), EmittedMember.of("asValidatedBuild")))) {
      return;
    }
    TypeSpec.Builder implBuilder =
        implSkeleton(
                spec,
                implClassName(spec),
                specName,
                "Generated build-only mapping for {@link $T}: total {@code build} and no {@code"
                    + " parse}, since the wire offers nothing to read it back through (truthful"
                    + " types).\n",
                leafFields(spec))
            .addMethod(
                buildMethod(
                    domainName,
                    wireName,
                    pairSuppression(domainDeclared, wire),
                    wireBuildBody(wire, wireName, comps)))
            .addMethod(asValidatedBuildMethod(wireName, domainName));
    addMarkerStubs(implBuilder, spec);
    writeFile(spec, specName.packageName(), implBuilder.build());
  }

  /**
   * The accumulating {@code parse} body over the correspondences' legs: one {@code
   * Validated.fields()} ladder, or chunked ladders past the arity ceiling with identical error
   * semantics. Shared by the full and parse-only tiers, which parse alike.
   */
  private CodeBlock parseBody(WireShape wire, List<Correspondence> comps, TypeName domainName) {
    List<CodeBlock> parseLegs = parseLegs(wire, comps);
    if (parseLegs.size() <= ArityCeilings.ASSEMBLY) {
      CodeBlock.Builder parseChain = CodeBlock.builder().add("return $T.fields()", VALIDATED);
      parseLegs.forEach(parseChain::add);
      parseChain.add("\n.apply($T::new)", domainName);
      return CodeBlock.builder().addStatement("$L", parseChain.build()).build();
    }
    // Wider than one fields() ladder: chunked ladders, identical error semantics.
    return ChunkedAssembly.emit(
        parseLegs,
        VALIDATED,
        NEL,
        Set.of("wire"),
        values -> CodeBlock.of("new $T($L)", domainName, CodeBlock.join(values, ", ")));
  }

  /**
   * The {@code parse} method over a body from {@link #parseBody}, carrying the pair's {@link
   * #pairSuppression}.
   */
  private static MethodSpec parseMethod(
      TypeName domainName, TypeName wireName, List<AnnotationSpec> suppression, CodeBlock body) {
    return MethodSpec.methodBuilder("parse")
        .addAnnotations(suppression)
        .addModifiers(Modifier.PUBLIC)
        .returns(
            ParameterizedTypeName.get(
                VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), domainName))
        .addParameter(wireName, "wire")
        .addStatement("$T.requireNonNull(wire, $S)", OBJECTS, "wire must not be null")
        .addCode(body)
        .build();
  }

  /**
   * Adds the guard and null-scan helpers the legs call, each only where some leg calls it, in the
   * one order every reading tier emits them.
   */
  private void addReadHelpers(
      TypeSpec.Builder implBuilder, List<Correspondence> comps, WireShape wire) {
    if (comps.stream().anyMatch(c -> usesIfPresent(c, wire))) {
      implBuilder.addMethod(ifPresentHelper());
    }
    if (comps.stream().anyMatch(c -> scansList(c, wire))) {
      implBuilder.addMethod(allPresentHelper());
    }
    if (comps.stream().anyMatch(c -> scansSet(c, wire))) {
      implBuilder.addMethod(allPresentSetHelper());
    }
    if (comps.stream().anyMatch(c -> scansArray(c, wire))) {
      implBuilder.addMethod(allPresentArrayHelper());
    }
    if (comps.stream().anyMatch(MappingProcessor::scansMap)) {
      implBuilder.addMethod(valuesPresentHelper());
    }
  }

  /**
   * The element-of-Optional parser lambda, shared by the dense {@code OPTIONAL} leg and the sparse
   * {@code OPTIONAL} edit so the two tiers cannot drift: a present element parses through its leaf
   * or nested spec, an empty Optional is valid emptiness.
   */
  private static CodeBlock elementOfOptionalParser(CodeBlock prism) {
    ClassName optional = ClassName.get("java.util", "Optional");
    return CodeBlock.of(
        "o -> o.map(v -> $L.map($T::of)).orElseGet(() -> $T.validNel($T.empty()))",
        parseCall(Kind.LEAF, prism, null, null).on(CodeBlock.of("v")),
        optional,
        VALIDATED,
        optional);
  }

  /**
   * One {@code Validated.fields()} leg for a correspondence — shared by the full tier's {@code
   * parse} and the projection tier's {@code patch}. Every reference read is guarded (see {@link
   * #guardedRead}), so the leaf and container legs always wrap their read in the {@code
   * hkj$ifPresent} helper — a null becomes a located {@code FieldError} instead of reaching a leaf
   * (whose parse rejects null). {@code guard} only varies the identity leg, whose primitive reads
   * can never be null.
   */
  private CodeBlock parseLeg(WireShape wire, Correspondence c, CodeBlock read, boolean guard) {
    ClassName optional = ClassName.get("java.util", "Optional");
    return switch (c.kind()) {
      case LEAF, ELEMENTS, ARRAY, MAP, MAP_KEYS, MAP_ENTRIES ->
          CodeBlock.of(
              "\n.field($S, hkj$$ifPresent($L, $L))", c.name(), read, parseCall(c).asFunction());
      case OPTIONAL ->
          CodeBlock.of(
              "\n.field($S, hkj$$ifPresent($L, $L))",
              c.name(),
              read,
              elementOfOptionalParser(c.prism()));
      case IDENTITY ->
          guard
              ? CodeBlock.of(
                  "\n.field($S, hkj$$ifPresent($L, $T::validNel))", c.name(), read, VALIDATED)
              : CodeBlock.of("\n.field($S, $T.validNel($L))", c.name(), VALIDATED, read);
      // Identity containers copy by reference, but a null element/value is a located invalid
      // at its index/key - the same doctrine the lifted legs enforce via parseAll/parseValues.
      case IDENTITY_ELEMENTS -> CodeBlock.of("\n.field($S, hkj$$allPresent($L))", c.name(), read);
      case IDENTITY_MAP -> CodeBlock.of("\n.field($S, hkj$$valuesPresent($L))", c.name(), read);
      // A nullable read bridges to the domain Optional: null becomes Optional.empty, so it is
      // never guarded and never fails on absence. A present value still goes through whatever the
      // unbridged component would have used - its leaf, its container's lifting, its nested spec,
      // or an identity container's null scan.
      case OPTIONAL_BRIDGE -> bridgeParseLeg(c, read, optional);
      // A derived component carries no domain data; parse reconstructs without it.
      case DERIVED -> CodeBlock.of("");
    };
  }

  /**
   * The bridged leg: an absent (null) read is valid emptiness, and a present one parses exactly as
   * its unbridged pair would, through the call that pair's leg makes, through the identity
   * container's null scan, or straight through. They share one shape so the bridge cannot drift
   * from the legs it stands in for.
   *
   * <p>Absence is the only thing the bridge excuses. A wire {@code List} that is present but holds
   * a null element is not absent, and the located-null doctrine applies to it exactly as it does to
   * the same component declared without the {@code Optional}; without this the bridge would be a
   * hole in the one rule the annotation is documented as the single carve-out from. So a copied
   * container scans by {@link #identityKind}, the rule the unbridged leg takes: only a raw
   * container, which no leg can scan, copies plain. A wildcard-argument container keeps its scan,
   * because the leg names the {@code Optional}'s argument outright rather than letting the scan's
   * result be inferred into it.
   */
  private static CodeBlock bridgeParseLeg(Correspondence c, CodeBlock read, ClassName optional) {
    CodeBlock present =
        switch (c.valueKind()) {
          case IDENTITY -> null;
          case IDENTITY_ELEMENTS -> CodeBlock.of("hkj$$allPresent(v)");
          case IDENTITY_MAP -> CodeBlock.of("hkj$$valuesPresent(v)");
          default -> parseCall(c.present()).on(CodeBlock.of("v"));
        };
    // A wildcard-carrying element is named rather than inferred, on whichever shape carries it:
    // Optional is invariant, so a captured argument would not be the type the component declares.
    CodeBlock witness =
        c.domainElement() == null ? CodeBlock.of("") : CodeBlock.of("<$T>", c.domainElement());
    return present == null
        ? CodeBlock.of(
            "\n.field($S, $T.validNel($T.$LofNullable($L)))",
            c.name(),
            VALIDATED,
            optional,
            witness,
            read)
        : CodeBlock.of(
            "\n.field($S, $T.ofNullable($L).map(v -> $L.map($T::$Lof)).orElseGet("
                + "() -> $T.validNel($T.empty())))",
            c.name(),
            optional,
            read,
            present,
            optional,
            witness,
            VALIDATED,
            optional);
  }

  /**
   * Emits the validated patch tier: a projection whose wire carries fallible correspondences, or,
   * on a bean wire, a guarded reference read. {@code build} stays total; the write-back is {@code
   * patch(domain, wire)} returning {@code Validated<NonEmptyList<FieldError>, Domain>} — every
   * projected component validates (a null reference read becomes a located {@code FieldError}, or
   * an empty {@code Optional} on a bridged component), unprojected components are read from the
   * domain argument, and all failures accumulate. Dense semantics: every projected component
   * applies; contrast {@code UpdateSpec}'s sparse null-as-absent {@code updateFrom}.
   *
   * <p>{@code patch} only reads the wire, through record accessors or bean getters, and rebuilds
   * the domain through its canonical constructor, so the two shapes differ only in {@code build},
   * which writes the wire through the record constructor or the bean's construction strategy.
   */
  private void writePatchImpl(
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      TypeMirror wireUsed,
      List<Correspondence> comps) {
    ClassName specName = ClassName.get(spec);
    ClassName implName = implClassName(spec);
    TypeName domainName = ProcessorUtils.typeNameOf(domainDeclared);
    TypeName wireName = ProcessorUtils.typeNameOf(wireUsed);
    TypeName patchReturn =
        ParameterizedTypeName.get(
            VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), domainName);

    if (!checkNoEmittedCollisions(
        spec,
        "a validating projection",
        reserveFactoryIfGeneric(
            spec,
            List.of(
                EmittedMember.of("build", domainDeclared),
                EmittedMember.of("patch", domainDeclared, wireUsed))))) {
      return;
    }

    CodeBlock buildBody = wireBuildBody(wire, wireName, comps);
    List<AnnotationSpec> suppression = pairSuppression(domainDeclared, wire);

    List<CodeBlock> patchLegs = new ArrayList<>();
    for (Correspondence c : comps) {
      // A JSON-bound record leaves an absent component null, exactly like an unset bean
      // property, so every reference read is guarded into a located FieldError (the locked
      // null policy, the same guardedRead the full tier uses); a primitive
      // read can never be null and copies directly.
      patchLegs.add(parseLeg(wire, c, wireRead(wire, c.wireName()), guardedRead(c, wire)));
    }

    CodeBlock patchBody;
    if (patchLegs.size() <= ArityCeilings.ASSEMBLY) {
      CodeBlock.Builder patchChain = CodeBlock.builder().add("return $T.fields()", VALIDATED);
      patchLegs.forEach(patchChain::add);

      // Lambda parameters are named after the projected components, but the enclosing method
      // already declares 'domain' and 'wire', and a lambda parameter may not shadow either (JLS
      // 6.4). Colliding names take underscore suffixes until free of the method parameters AND of
      // every component name (a renamed parameter must not capture another component's reference).
      Set<String> takenParamNames = new LinkedHashSet<>(List.of("domain", "wire"));
      for (RecordComponentElement domainComponent : domain.getRecordComponents()) {
        takenParamNames.add(domainComponent.getSimpleName().toString());
      }
      Map<String, String> lambdaParamFor = new LinkedHashMap<>();
      for (Correspondence c : comps) {
        String candidate = c.name();
        if (candidate.equals("domain") || candidate.equals("wire")) {
          do {
            candidate = candidate + "_";
          } while (takenParamNames.contains(candidate));
        }
        takenParamNames.add(candidate);
        lambdaParamFor.put(c.name(), candidate);
      }

      CodeBlock.Builder lambdaParams = CodeBlock.builder();
      boolean firstParam = true;
      for (Correspondence c : comps) {
        if (!firstParam) {
          lambdaParams.add(", ");
        }
        firstParam = false;
        lambdaParams.add("$L", lambdaParamFor.get(c.name()));
      }
      patchChain.add(
          "\n.apply(($L) -> new $T($L))",
          lambdaParams.build(),
          domainName,
          patchCtorArgs(domain, comps, name -> CodeBlock.of("$L", lambdaParamFor.get(name))));
      patchBody = CodeBlock.builder().addStatement("$L", patchChain.build()).build();
    } else {
      // Wider than one fields() ladder: chunked ladders; projected components read from the
      // tuples, unprojected components from the domain argument, exactly as the lambda form.
      patchBody =
          ChunkedAssembly.emit(
              patchLegs,
              VALIDATED,
              NEL,
              Set.of("domain", "wire"),
              values -> {
                // values align 1:1 with comps: every projected leg emits exactly one .field
                // (a projection can never carry a DERIVED correspondence, the only empty leg),
                // so index i pairs comps.get(i) with its parsed value.
                Map<String, CodeBlock> valueFor = new LinkedHashMap<>();
                for (int i = 0; i < comps.size(); i++) {
                  valueFor.put(comps.get(i).name(), values.get(i));
                }
                return CodeBlock.of(
                    "new $T($L)", domainName, patchCtorArgs(domain, comps, valueFor::get));
              });
    }

    TypeSpec.Builder implBuilder =
        implSkeleton(
                spec,
                implName,
                specName,
                "Generated projection mapping for {@link $T}: total {@code build} and a validated"
                    + " {@code patch} write-back. No {@code parse} is emitted, since the dropped"
                    + " components cannot be reconstructed, and no {@code asLens()}, since a"
                    + " write-back that can fail has no lawful total {@code set} (truthful types).\n",
                leafFields(spec))
            .addMethod(buildMethod(domainName, wireName, suppression, buildBody))
            .addMethod(
                MethodSpec.methodBuilder("patch")
                    .addAnnotations(suppression)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(patchReturn)
                    .addParameter(domainName, "domain")
                    .addParameter(wireName, "wire")
                    .addJavadoc(
                        "Writes the wire's projected components onto {@code domain}, validating"
                            + " each one; every bad field is reported at once, located under its"
                            + " component name, and unprojected components stay untouched. Dense:"
                            + " every projected component is written, never skipped, so a {@code"
                            + " null} reference read is a located error (a bridged {@code Optional}"
                            + " component reads it as empty) — contrast {@code UpdateSpec}'s sparse"
                            + " {@code updateFrom}. Nulls locate through"
                            + " nesting too: a nested wire value delegates to the nested spec's"
                            + " parse, whose reference legs carry the same guard.\n")
                    .addStatement(
                        "$T.requireNonNull(domain, $S)", OBJECTS, "domain must not be null")
                    .addStatement("$T.requireNonNull(wire, $S)", OBJECTS, "wire must not be null")
                    .addCode(patchBody)
                    .build());
    addMarkerStubs(implBuilder, spec);
    // A patch tier need not carry a guarded read: a bridged component makes the write-back
    // partial yet reads its own null as empty, so a projection whose only partial reads are
    // bridges needs no guard emitted.
    addReadHelpers(implBuilder, comps, wire);
    writeFile(spec, specName.packageName(), implBuilder.build());
  }

  /**
   * The wire type a scanning leg reads, or null when the leg carries no element scan: an identity
   * {@code List} or {@code Set}, or a bridged one. The emitted call text is the same for either
   * container — which {@code hkj$allPresent} overload to <em>declare</em> is what this answers.
   */
  private static TypeMirror scannedElementsType(Correspondence c, WireShape wire) {
    return c.valueKind() == Kind.IDENTITY_ELEMENTS
        ? wire.componentNamed(c.wireName()).orElseThrow().type()
        : null;
  }

  /** Whether a leg emits the {@code hkj$allPresent} List overload. */
  private boolean scansList(Correspondence c, WireShape wire) {
    return isExactly(scannedElementsType(c, wire), "java.util.List");
  }

  /** Whether a leg emits the {@code hkj$allPresent} Set overload. */
  private boolean scansSet(Correspondence c, WireShape wire) {
    return isExactly(scannedElementsType(c, wire), "java.util.Set");
  }

  /** Whether a leg emits the {@code hkj$allPresent} array overload. */
  private boolean scansArray(Correspondence c, WireShape wire) {
    return scannedElementsType(c, wire) instanceof ArrayType;
  }

  /**
   * The sparse tier's twin of {@link #scansList}/{@link #scansSet}: which {@code hkj$allPresent}
   * overload a scanning edit needs declared. A sparse identity edit matches its wire and domain
   * types, so either side names the container.
   */
  private boolean scansUpdate(UpdateEdit edit, WireShape wire, String container) {
    return edit.kind() == Kind.IDENTITY_ELEMENTS
        && isExactly(wire.componentNamed(edit.wireName()).orElseThrow().type(), container);
  }

  /** The sparse tier's array twin of {@link #scansUpdate}. */
  private boolean scansUpdateArray(UpdateEdit edit, WireShape wire) {
    return edit.kind() == Kind.IDENTITY_ELEMENTS
        && wire.componentNamed(edit.wireName()).orElseThrow().type() instanceof ArrayType;
  }

  /** Whether a leg emits the {@code hkj$valuesPresent} scan: an identity Map, or a bridged one. */
  private static boolean scansMap(Correspondence c) {
    return c.valueKind() == Kind.IDENTITY_MAP;
  }

  /**
   * The patch constructor arguments: projected components take the supplied value expression,
   * unprojected components read from the domain argument.
   */
  private static CodeBlock patchCtorArgs(
      TypeElement domain, List<Correspondence> comps, Function<String, CodeBlock> projectedValue) {
    CodeBlock.Builder args = CodeBlock.builder();
    boolean first = true;
    for (RecordComponentElement domainComponent : domain.getRecordComponents()) {
      String name = domainComponent.getSimpleName().toString();
      boolean projected = comps.stream().anyMatch(c -> c.name().equals(name));
      if (!first) {
        args.add(", ");
      }
      first = false;
      args.add(projected ? projectedValue.apply(name) : CodeBlock.of("domain.$L()", name));
    }
    return args.build();
  }

  private void writeLensImpl(
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      TypeMirror wireUsed,
      List<Correspondence> comps) {
    ClassName specName = ClassName.get(spec);
    TypeName domainName = ProcessorUtils.typeNameOf(domainDeclared);
    TypeName wireName = ProcessorUtils.typeNameOf(wireUsed);

    if (!checkNoEmittedCollisions(
        spec,
        "a lossy projection",
        reserveFactoryIfGeneric(
            spec,
            List.of(EmittedMember.of("build", domainDeclared), EmittedMember.of("asLens"))))) {
      return;
    }

    CodeBlock buildBody = wireBuildBody(wire, wireName, comps);

    CodeBlock.Builder setArgs = CodeBlock.builder();
    boolean first = true;
    for (RecordComponentElement domainComponent : domain.getRecordComponents()) {
      String name = domainComponent.getSimpleName().toString();
      Correspondence c = comps.stream().filter(x -> x.name().equals(name)).findFirst().orElse(null);
      if (!first) {
        setArgs.add(", ");
      }
      first = false;
      if (c == null) {
        setArgs.add("domain.$L()", name);
      } else {
        setArgs.add(wireRead(wire, c.wireName()));
      }
    }

    ClassName lens = ClassName.get("org.higherkindedj.optics", "Lens");
    TypeSpec.Builder implBuilder =
        implSkeleton(
                spec,
                implClassName(spec),
                specName,
                "Generated projection mapping for {@link $T}: total {@code build} and a lawful"
                    + " {@code asLens()} write-back. No {@code parse} is emitted — the dropped"
                    + " components cannot be reconstructed (truthful types).\n",
                leafFields(spec))
            .addMethod(
                buildMethod(domainName, wireName, pairSuppression(domainDeclared, wire), buildBody))
            .addMethod(
                MethodSpec.methodBuilder("asLens")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(lens, domainName, wireName))
                    .addJavadoc(
                        "The projection as a {@link $T}: {@code get} is {@code build}; {@code"
                            + " set} writes the wire components back and keeps the rest of the"
                            + " domain.\n",
                        lens)
                    .addStatement(
                        "return $T.of(this::build, (domain, wire) -> new $T($L))",
                        lens,
                        domainName,
                        setArgs.build())
                    .build());
    addMarkerStubs(implBuilder, spec);
    writeFile(spec, specName.packageName(), implBuilder.build());
  }

  /** A permitted subtype of a sealed pair, and whether it is on the wire side. */
  private record Permitted(TypeMirror subtype, boolean wireSide) {}

  /**
   * Whether every permitted subtype on both sides is one sealed dispatch can delegate, reporting
   * each that is not, so that one compilation names them all. For any such subtype the missing-spec
   * refusal would offer a spec that could never serve the dispatch.
   */
  private boolean checkDispatchable(TypeElement spec, TypeElement domain, TypeElement wire) {
    List<Permitted> refused =
        Stream.concat(
                domain.getPermittedSubclasses().stream()
                    .map(subtype -> new Permitted(subtype, false)),
                wire.getPermittedSubclasses().stream().map(subtype -> new Permitted(subtype, true)))
            .filter(permitted -> !dispatchable(permitted))
            .toList();
    refused.forEach(permitted -> reportUndispatchable(spec, domain, wire, permitted));
    return refused.isEmpty();
  }

  /**
   * Whether dispatch can hand a subtype to the one spec mapping it, which needs a type a spec can
   * name: a record or a sealed interface, or on the wire side a bean as well, and not generic,
   * since dispatch leaves a generic subtype's type arguments free and no spec's pair matches that.
   */
  private static boolean dispatchable(Permitted permitted) {
    TypeMirror subtype = permitted.subtype();
    return ((TypeElement) ((DeclaredType) subtype).asElement()).getTypeParameters().isEmpty()
        && (asRecord(subtype) != null
            || asSealed(subtype) != null
            || (permitted.wireSide() && asBean(subtype) != null));
  }

  private void reportUndispatchable(
      TypeElement spec, TypeElement domain, TypeElement wire, Permitted permitted) {
    TypeMirror subtype = permitted.subtype();
    TypeElement type = (TypeElement) ((DeclaredType) subtype).asElement();
    boolean generic = !type.getTypeParameters().isEmpty();
    // An interface or an abstract class stands for subtypes of its own, so a sealed interface with
    // its own sealed spec is the nearer shape; anything else is nearest a record.
    boolean abstractType = type.getModifiers().contains(Modifier.ABSTRACT);
    String shape =
        generic
            ? "generic"
            : switch (type.getKind()) {
              case ENUM -> "an enum";
              case INTERFACE -> "an interface that is not sealed";
              default -> abstractType ? "an abstract class" : "a class";
            };
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "permitted subtype '"
            + subtype
            + "' of '"
            + (permitted.wireSide() ? wire : domain).getSimpleName()
            + "' is "
            + shape
            + ", which sealed dispatch does not support yet.",
        "Dispatch delegates each subtype to the one spec mapping it, and "
            + (generic
                ? "no spec matches '"
                    + subtype
                    + "' for every type argument dispatch can meet: a spec over one instantiation"
                    + " covers only that one."
                : "a spec maps only a record or a sealed interface"
                    + (permitted.wireSide() ? ", or on the wire side a bean" : "")
                    + "."),
        "Declare '"
            + type.getSimpleName()
            + (generic
                ? "' without type parameters"
                : abstractType
                    ? "' as a sealed interface, mapped by a sealed spec of its own, or as a record"
                    : "' as a record")
            + ", or drop this spec and map the pair with a hand-written ValidatedPrism<"
            + wire.getSimpleName()
            + ", "
            + domain.getSimpleName()
            + "> leaf wherever it nests.");
  }

  /** One dispatch arm of a sealed mapping: a domain subtype, its wire subtype, and the impl. */
  private record SealedPair(TypeMirror domain, TypeMirror wire, ClassName impl) {}

  private void processSealedSpec(
      TypeElement spec, List<RegisteredSpec> registry, TypeElement domain, TypeElement wire) {
    List<? extends TypeMirror> wirePermitted = wire.getPermittedSubclasses();
    if (!checkDispatchable(spec, domain, wire)) {
      return;
    }
    List<SealedPair> pairs = new ArrayList<>();
    for (TypeMirror domainSubtype : domain.getPermittedSubclasses()) {
      Candidates nearest =
          Candidates.nearest(
              registry.stream()
                  .filter(r -> r.serves(WireShape.Direction.BIDIRECTIONAL))
                  .filter(r -> processingEnv.getTypeUtils().isSameType(r.domain(), domainSubtype))
                  .filter(
                      r ->
                          wirePermitted.stream()
                              .anyMatch(w -> processingEnv.getTypeUtils().isSameType(r.wire(), w)))
                  .toList());
      List<RegisteredSpec> candidates = nearest.chosen();
      if (candidates.isEmpty()) {
        String projectionHint =
            registry.stream()
                .filter(r -> !r.serves(WireShape.Direction.BIDIRECTIONAL))
                .filter(r -> processingEnv.getTypeUtils().isSameType(r.domain(), domainSubtype))
                .filter(
                    r ->
                        wirePermitted.stream()
                            .anyMatch(w -> processingEnv.getTypeUtils().isSameType(r.wire(), w)))
                .findFirst()
                .map(
                    r ->
                        r.unusable(
                            "maps it", "take part in dispatch", WireShape.Direction.BIDIRECTIONAL))
                .orElse("");
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "permitted subtype '"
                + domainSubtype
                + "' of '"
                + domain.getSimpleName()
                + "' has no mapping spec.",
            "Sealed dispatch delegates each domain subtype to the one spec mapping it to a"
                + " permitted subtype of "
                + wire.getSimpleName()
                + "."
                + projectionHint,
            "Declare a @GenerateMapping spec for '"
                + domainSubtype
                + "',"
                + declarationSites(processingEnv, spec)
                + ".");
        return;
      }
      if (candidates.size() > 1) {
        // Sealed dispatch has no leaf to delegate through, so when every candidate is a
        // dependency's the one remedy is a spec of this compilation's own, which shadows them.
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "permitted subtype '"
                + domainSubtype
                + "' of '"
                + domain.getSimpleName()
                + "' matches more than one mapping spec: "
                + nearest.names()
                + ".",
            "With several specs for one subtype, the dispatch choice would be arbitrary.",
            nearest.allClasspath()
                ? "Declare a @GenerateMapping spec for this subtype pair in this compilation, which"
                    + " takes precedence over a dependency's, or drop one of the two dependencies."
                : "Keep exactly one spec per subtype pair.");
        return;
      }
      noteShadowed(
          processingEnv,
          spec,
          TAG,
          "permitted subtype '" + domainSubtype + "'",
          "Keep it, or remove the spec in this compilation if the classpath spec is the one meant.",
          nearest);
      RegisteredSpec match = candidates.getFirst();
      pairs.add(new SealedPair(domainSubtype, match.wire(), match.impl()));
    }
    for (TypeMirror wireSubtype : wirePermitted) {
      long targets =
          pairs.stream()
              .filter(pair -> processingEnv.getTypeUtils().isSameType(pair.wire(), wireSubtype))
              .count();
      if (targets == 0) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "permitted subtype '"
                + wireSubtype
                + "' of '"
                + wire.getSimpleName()
                + "' is never produced.",
            "parse must dispatch every wire subtype back to a domain subtype; this one has no"
                + " mapping spec from any.",
            "Add a domain subtype and spec for it, or remove it from the sealed wire interface.");
        return;
      }
      if (targets > 1) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "permitted subtype '"
                + wireSubtype
                + "' of '"
                + wire.getSimpleName()
                + "' is targeted by more than one domain subtype.",
            "parse dispatches on the wire subtype; two sources would make the reverse direction"
                + " ambiguous.",
            "Give each domain subtype its own wire subtype.");
        return;
      }
    }
    writeSealedImpl(spec, domain, wire, pairs);
  }

  private void writeSealedImpl(
      TypeElement spec, TypeElement domain, TypeElement wire, List<SealedPair> pairs) {
    ClassName specName = ClassName.get(spec);
    TypeName domainName = TypeName.get(domain.asType());
    TypeName wireName = TypeName.get(wire.asType());

    if (!checkNoEmittedCollisions(
        spec,
        "a sealed dispatch mapping",
        List.of(
            EmittedMember.of("build", domain.asType()),
            EmittedMember.of("parse", wire.asType()),
            EmittedMember.of("asValidatedPrism")))) {
      return;
    }

    TypeName parseReturn =
        ParameterizedTypeName.get(
            VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), domainName);

    CodeBlock.Builder buildSwitch = CodeBlock.builder().add("return switch (domain) {$>");
    CodeBlock.Builder parseSwitch = CodeBlock.builder().add("return switch (wire) {$>");
    for (SealedPair pair : pairs) {
      buildSwitch.add("\ncase $T v -> $T.INSTANCE.build(v);", pair.domain(), pair.impl());
      parseSwitch.add(
          "\ncase $T v -> $T.INSTANCE.parse(v).map(d -> ($T) d);",
          pair.wire(),
          pair.impl(),
          domainName);
    }
    buildSwitch.add("$<\n}");
    parseSwitch.add("$<\n}");

    TypeSpec.Builder implBuilder =
        implSkeleton(
                spec,
                implClassName(spec),
                specName,
                "Generated sealed-dispatch mapping for {@link $T}: {@code build} and {@code"
                    + " parse} switch over the permitted subtype pairs, each delegating to its"
                    + " own mapping.\n",
                List.of())
            .addMethod(
                buildMethod(
                    domainName,
                    wireName,
                    List.of(),
                    CodeBlock.builder().addStatement("$L", buildSwitch.build()).build()))
            .addMethod(
                MethodSpec.methodBuilder("parse")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(parseReturn)
                    .addParameter(wireName, "wire")
                    .addStatement("$T.requireNonNull(wire, $S)", OBJECTS, "wire must not be null")
                    .addStatement("$L", parseSwitch.build())
                    .build())
            .addMethod(asValidatedPrismMethod(wireName, domainName));
    // A sealed dispatch declares no vocabulary of its own, but it may inherit an abstract marker
    // from a mix-in it shares with the subtype specs, which stays inert here and still has to be
    // implemented: the Impl declares the interface. Locally declared vocabulary is refused before
    // this point, so only the inert inherited kind reaches the stub.
    addMarkerStubs(implBuilder, spec);
    writeFile(spec, specName.packageName(), implBuilder.build());
  }

  private static TypeSpec.Builder implSkeleton(
      TypeElement spec,
      ClassName implName,
      ClassName specName,
      String javadoc,
      List<LeafField> abstractLeaves) {
    List<TypeVariableName> variables =
        spec.getTypeParameters().stream().map(ProcessorUtils::typeVariableOf).toList();
    TypeSpec.Builder builder =
        TypeSpec.classBuilder(implName)
            .addOriginatingElement(spec)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(GENERATED)
            // Each type parameter is restated with its bounds in the class's own type-parameter
            // clause, which no member annotation reaches, so a raw bound is answered on the class.
            .addAnnotations(
                ProcessorUtils.rawTypesSuppression(
                    spec.getTypeParameters().stream()
                        .flatMap(parameter -> parameter.getBounds().stream())
                        .toList()))
            .addJavadoc(javadoc, specName);
    if (variables.isEmpty()) {
      return builder
          .addSuperinterface(specName)
          .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
          .addField(
              FieldSpec.builder(
                      implName, "INSTANCE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                  .initializer("new $T()", implName)
                  .build());
    }
    if (!abstractLeaves.isEmpty()) {
      return elementMappedSkeleton(builder, implName, specName, variables, abstractLeaves);
    }
    builder.addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
    // A generic Impl (threaded type parameters) cannot carry a typed static
    // INSTANCE, so it follows hkj-core's generic-singleton convention (EitherMonad.instance()):
    // one stateless cached instance behind an unchecked-but-sound cast.
    TypeName[] wildcards = new TypeName[variables.size()];
    Arrays.fill(wildcards, WildcardTypeName.subtypeOf(Object.class));
    TypeName rawInstanceType = ParameterizedTypeName.get(implName, wildcards);
    TypeName typedInstanceType =
        ParameterizedTypeName.get(implName, variables.toArray(new TypeName[0]));
    return builder
        .addTypeVariables(variables)
        .addSuperinterface(ParameterizedTypeName.get(specName, variables.toArray(new TypeName[0])))
        .addField(
            FieldSpec.builder(
                    rawInstanceType, "INSTANCE", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T<>()", implName)
                .build())
        .addMethod(
            MethodSpec.methodBuilder("instance")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addTypeVariables(variables)
                .returns(typedInstanceType)
                .addAnnotation(
                    AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "$S", "unchecked")
                        .build())
                .addJavadoc(
                    "The stateless singleton, shared across instantiations — the cast is sound"
                        + " because the Impl holds no state typed by its parameters (the {@code"
                        + " EitherMonad.instance()} convention).\n")
                .addStatement("return ($T) INSTANCE", typedInstanceType)
                .build());
  }

  /**
   * The element-mapped skeleton: the spec's abstract leaves become constructor-supplied fields
   * behind a public {@code of(...)} factory taking one {@code ValidatedPrism} per leaf, in
   * declaration order. The Impl carries leaf-typed state, so unlike the stateless threaded form
   * there is no shared singleton: every {@code of(...)} call is a fresh, immutable instance.
   */
  private static TypeSpec.Builder elementMappedSkeleton(
      TypeSpec.Builder builder,
      ClassName implName,
      ClassName specName,
      List<TypeVariableName> variables,
      List<LeafField> abstractLeaves) {
    TypeName typed = ParameterizedTypeName.get(implName, variables.toArray(new TypeName[0]));
    // Each prism type is written out verbatim, on the leaf's field and accessor and as a parameter
    // of the constructor and the factory, so a raw type in one leaf lands in all four.
    List<AnnotationSpec> anyLeafRaw =
        ProcessorUtils.rawTypesSuppression(
            abstractLeaves.stream().map(LeafField::prismType).toList());
    MethodSpec.Builder constructor =
        MethodSpec.constructorBuilder().addAnnotations(anyLeafRaw).addModifiers(Modifier.PRIVATE);
    MethodSpec.Builder factory =
        MethodSpec.methodBuilder("of")
            .addAnnotations(anyLeafRaw)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addTypeVariables(variables)
            .returns(typed)
            .addJavadoc(
                "Creates the element-mapped mapping: each abstract leaf arrives as its {@code"
                    + " ValidatedPrism}, in declaration order.\n");
    StringJoiner arguments = new StringJoiner(", ");
    for (LeafField leaf : abstractLeaves) {
      String name = leaf.name();
      TypeName prismType = leaf.prismTypeName();
      List<AnnotationSpec> leafRaw = ProcessorUtils.rawTypesSuppression(List.of(leaf.prismType()));
      builder.addField(
          FieldSpec.builder(prismType, name, Modifier.PRIVATE, Modifier.FINAL)
              .addAnnotations(leafRaw)
              .build());
      constructor
          .addParameter(prismType, name)
          .addStatement(
              "this.$1L = $2T.requireNonNull($1L, $3S)", name, OBJECTS, name + " must not be null");
      factory.addParameter(prismType, name);
      arguments.add(name);
      builder.addMethod(
          MethodSpec.methodBuilder(name)
              .addAnnotation(Override.class)
              .addAnnotations(leafRaw)
              .addModifiers(Modifier.PUBLIC)
              .returns(prismType)
              .addStatement("return $L", name)
              .build());
    }
    factory.addStatement("return new $T<>($L)", implName, arguments.toString());
    return builder
        .addTypeVariables(variables)
        .addSuperinterface(ParameterizedTypeName.get(specName, variables.toArray(new TypeName[0])))
        .addMethod(constructor.build())
        .addMethod(factory.build());
  }

  /**
   * The suppression a method mapping between the domain and the wire carries: {@code build}, {@code
   * parse}, {@code patch} and {@code updateFrom}. Some of them hold lambdas whose parameters javac
   * types from either side's components: an element or bridged leg, a patch's assembly, a chunk's
   * tuple. Which ones do is the emitter's detail, so each method asks of the whole pair, and one
   * holding no such lambda carries it too: a {@code build} on either wire shape, or a projection's
   * when the raw type is on a component it drops. A flattened group's inner components are not
   * asked. Their wire side is a wire component already, and their domain side is written out in one
   * place only, the array-constructor reference of a lifted inner array ({@code List[]::new}),
   * which javac does not report.
   */
  private List<AnnotationSpec> pairSuppression(DeclaredType domainDeclared, WireShape wire) {
    TypeElement domain = (TypeElement) domainDeclared.asElement();
    return ProcessorUtils.rawTypesSuppression(
        Stream.concat(
                domain.getRecordComponents().stream()
                    .map(component -> componentType(domainDeclared, component)),
                wire.components().stream().map(WireShape.WireComponent::type))
            .toList());
  }

  /**
   * The {@code build} method. {@code body} is the complete, terminated build statement(s): a record
   * or bean wire supplies them via {@link #wireBuildBody}, and the sealed path via a terminated
   * {@code return switch}, so both are emitted verbatim with {@code addCode}. {@code suppression}
   * is the pair's {@link #pairSuppression}; a sealed dispatch only delegates to each subtype pair's
   * Impl, holds no lambda over a component, and passes none.
   */
  private static MethodSpec buildMethod(
      TypeName domainName, TypeName wireName, List<AnnotationSpec> suppression, CodeBlock body) {
    return MethodSpec.methodBuilder("build")
        .addAnnotations(suppression)
        .addModifiers(Modifier.PUBLIC)
        .returns(wireName)
        .addParameter(domainName, "domain")
        .addStatement("$T.requireNonNull(domain, $S)", OBJECTS, "domain must not be null")
        .addCode(body)
        .build();
  }

  private static MethodSpec asValidatedPrismMethod(TypeName wireName, TypeName domainName) {
    return MethodSpec.methodBuilder("asValidatedPrism")
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(VALIDATED_PRISM_TYPE, wireName, domainName))
        .addJavadoc(
            "This mapping as a {@link $T} leaf, so other mappings can nest it directly or lift"
                + " it through containers.\n",
            VALIDATED_PRISM_TYPE)
        .addStatement("return $T.of(this::parse, this::build)", VALIDATED_PRISM_TYPE)
        .build();
  }

  private static MethodSpec asValidatedParseMethod(TypeName wireName, TypeName domainName) {
    return MethodSpec.methodBuilder("asValidatedParse")
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(VALIDATED_PARSE_TYPE, wireName, domainName))
        .addJavadoc(
            "This parse-only mapping as a {@link $T}, so a mapping that only parses can nest it"
                + " directly or lift it through containers.\n",
            VALIDATED_PARSE_TYPE)
        .addStatement("return $T.of(this::parse)", VALIDATED_PARSE_TYPE)
        .build();
  }

  private static MethodSpec asValidatedBuildMethod(TypeName wireName, TypeName domainName) {
    return MethodSpec.methodBuilder("asValidatedBuild")
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(VALIDATED_BUILD_TYPE, wireName, domainName))
        .addJavadoc(
            "This build-only mapping as a {@link $T}, so a mapping that only builds can nest it"
                + " directly or lift it through containers.\n",
            VALIDATED_BUILD_TYPE)
        .addStatement("return $T.of(this::build)", VALIDATED_BUILD_TYPE)
        .build();
  }

  private void addMarkerStubs(TypeSpec.Builder implBuilder, TypeElement spec) {
    // Only abstract zero-parameter @MapField, @OptionalBridge and @Flatten methods survive
    // validateSpecMethods. Unrelated mix-ins agreeing on a marker contribute one stub, whose
    // return has to be return-type-substitutable for every declaration (JLS 8.4.8.3): the
    // subtype-narrowest of the group, which checkGroupsHaveNarrowestReturns has verified exists. A
    // name an abstract leaf shares gets no stub at all: the leaf accessor elementMappedSkeleton
    // emits already implements the member, and the group guard has proven its return satisfies the
    // marker declaration too; the rename's to-mapping is read from collectRenames and the bridge
    // from bridgeRequested either way. One method carrying both annotations is one stub, named
    // for the rename it also declares.
    Set<String> leafNames =
        abstractLeaves(spec).stream()
            .map(leaf -> leaf.getSimpleName().toString())
            .collect(Collectors.toSet());
    Map<String, List<ExecutableElement>> markers = new LinkedHashMap<>();
    for (ExecutableElement method : specMembers(spec)) {
      boolean marker =
          method.getAnnotation(MapField.class) != null
              || isBridgeMarker(spec, method)
              || isFlattenMarker(method)
              || method.getAnnotation(Unmapped.class) != null;
      if (marker && !leafNames.contains(method.getSimpleName().toString())) {
        markers
            .computeIfAbsent(method.getSimpleName().toString(), name -> new ArrayList<>())
            .add(method);
      }
    }
    for (Map.Entry<String, List<ExecutableElement>> marker : markers.entrySet()) {
      List<ExecutableElement> group = marker.getValue();
      TypeMirror narrowest = memberTypeIn(spec, narrowestMember(spec, group));
      boolean rename = group.stream().anyMatch(m -> m.getAnnotation(MapField.class) != null);
      boolean bridge = group.stream().anyMatch(m -> m.getAnnotation(OptionalBridge.class) != null);
      // A flatten marker never shares a method with a rename or a bridge (validateSpecMethods
      // refuses the combination), so its vocabulary stands alone.
      boolean flatten = group.stream().anyMatch(MappingProcessor::isFlattenMarker);
      boolean unmapped = group.stream().anyMatch(m -> m.getAnnotation(Unmapped.class) != null);
      String vocabulary =
          flatten
              ? "Flatten"
              : unmapped
                  ? "Unmapped"
                  : rename && bridge ? "Rename and bridge" : rename ? "Rename" : "Bridge";
      String message =
          flatten
              ? "@Flatten markers declare flattened components and are not invocable"
              : unmapped
                  ? "@Unmapped markers declare accessors the mapping leaves out and are not"
                      + " invocable"
                  : rename && bridge
                      ? "@MapField and @OptionalBridge methods declare correspondences and are not"
                          + " invocable"
                      : rename
                          ? "@MapField methods declare renames and are not invocable"
                          : "@OptionalBridge markers declare bridges and are not invocable";
      // The stub restates the declared return, so a raw type the author wrote lands here verbatim.
      implBuilder.addMethod(
          MethodSpec.methodBuilder(marker.getKey())
              .addAnnotation(Override.class)
              .addAnnotations(ProcessorUtils.rawTypesSuppression(List.of(narrowest)))
              .addModifiers(Modifier.PUBLIC)
              .returns(ProcessorUtils.typeNameOf(narrowest))
              .addJavadoc(vocabulary + " declaration only; not invocable.\n")
              .addStatement("throw new $T($S)", UnsupportedOperationException.class, message)
              .build());
    }
  }

  /**
   * One member the generated Impl will declare, described for the collision sweep: its name and
   * parameter types, compared against spec methods by erased signature.
   */
  private record EmittedMember(String name, List<TypeMirror> params) {
    static EmittedMember of(String name, TypeMirror... params) {
      return new EmittedMember(name, List.of(params));
    }
  }

  /**
   * A generic Impl also declares its static factory — the {@code instance()} singleton accessor
   * when stateless, the {@code of(...)} constructor when element-mapped; a spec method with that
   * erased signature would clash with or shadow it in the generated file, so every record tier
   * reserves it alongside its own members.
   */
  private List<EmittedMember> reserveFactoryIfGeneric(TypeElement spec, List<EmittedMember> base) {
    if (spec.getTypeParameters().isEmpty()) {
      return base;
    }
    List<EmittedMember> all = new ArrayList<>(base);
    List<ExecutableElement> leaves = abstractLeaves(spec);
    if (leaves.isEmpty()) {
      all.add(EmittedMember.of("instance"));
    } else {
      all.add(
          EmittedMember.of(
              "of",
              leaves.stream().map(leaf -> memberTypeIn(spec, leaf)).toArray(TypeMirror[]::new)));
    }
    return all;
  }

  /**
   * Rejects spec methods that are override-equivalent (JLS 8.4.2: name plus erased parameter types)
   * to a member the Impl emits for this tier. Without the check, a colliding {@code default} is
   * silently overridden by the generated method — the user's logic never runs on {@code INSTANCE} —
   * or, with a different return type, the generated file fails javac with no diagnostic pointing at
   * the spec. Static and private spec methods are not inherited by the Impl, so they can never
   * collide; overloads with a different erased signature stay legal, and each tier reserves only
   * the members it emits, so a helper named after another tier's member (say {@code patch} on a
   * full mapping) stays legal too. The {@code hkj$ifPresent} guard needs no reservation: its {@code
   * $} name is out of reach of ordinary spec methods.
   */
  private boolean checkNoEmittedCollisions(
      TypeElement spec, String tier, List<EmittedMember> emitted) {
    for (ExecutableElement method : specMembers(spec)) {
      if (method.getModifiers().contains(Modifier.STATIC)
          || method.getModifiers().contains(Modifier.PRIVATE)) {
        continue;
      }
      for (EmittedMember member : emitted) {
        if (!overrideEquivalent(spec, method, member)) {
          continue;
        }
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "'"
                + methodSignature(spec, method)
                + "'"
                + inheritedNote(method, spec)
                + " collides with the '"
                + member.name()
                + "' member the generated "
                + implClassName(spec).simpleName()
                + " emits for this tier ("
                + tier
                + ").",
            "The generated Impl declares an override-equivalent '"
                + member.name()
                + "', so this method is either silently overridden (its logic never runs on"
                + " INSTANCE) or fails the generated file's compile with a raw javac error.",
            "Rename the method, or remove it and rely on the generated '"
                + member.name()
                + "'; to customise how a component maps, declare a ValidatedPrism leaf default"
                + " named after it.");
        return false;
      }
    }
    return true;
  }

  /**
   * Override-equivalence (JLS 8.4.2) against a member that does not exist yet: same name and same
   * erased parameter types.
   */
  private boolean overrideEquivalent(
      TypeElement spec, ExecutableElement method, EmittedMember member) {
    if (!method.getSimpleName().contentEquals(member.name())
        || method.getParameters().size() != member.params().size()) {
      return false;
    }
    Types types = processingEnv.getTypeUtils();
    // Under the spec, as the members it is compared against are: an inherited 'build(D)' erases
    // to 'build(Object)' where it is declared, which collides with nothing, and the generated
    // Impl then declares a second 'build' the author never sees until javac rejects their file.
    List<? extends TypeMirror> specParams = memberSignatureIn(spec, method).getParameterTypes();
    for (int i = 0; i < member.params().size(); i++) {
      TypeMirror specParam = specParams.get(i);
      // An unresolved parameter type, inherited from a class file naming a type missing from the
      // classpath, matches everything under javac's isSameType; treat it as no collision rather
      // than report a spurious one.
      if (specParam.getKind() == TypeKind.ERROR
          || !types.isSameType(types.erasure(specParam), types.erasure(member.params().get(i)))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Renders a spec method as {@code name(SimpleParamType, ...)} for the collision diagnostic, under
   * the spec: an inherited member named as declared would print a type variable the author's own
   * file never mentions.
   */
  private String methodSignature(TypeElement spec, ExecutableElement method) {
    StringJoiner params = new StringJoiner(", ", "(", ")");
    for (TypeMirror parameter : memberSignatureIn(spec, method).getParameterTypes()) {
      params.add(parameterTypeName(parameter));
    }
    return method.getSimpleName() + params.toString();
  }

  /**
   * The compact display name of a parameter type: the element's simple name for declared types, so
   * packages, type arguments and type-use annotations never clutter the diagnostic; any other shape
   * renders as the shared diagnostic name, which keeps annotations out the same way.
   */
  private static String parameterTypeName(TypeMirror type) {
    return type instanceof DeclaredType declared
        ? declared.asElement().getSimpleName().toString()
        : ProcessorUtils.simpleTypeName(type);
  }

  void writeFile(TypeElement spec, String packageName, TypeSpec impl) {
    try {
      JavaFile.builder(packageName, impl)
          .addFileComment("Generated by hkj-processor. Do not edit.")
          .build()
          .writeTo(processingEnv.getFiler());
      writeIndexEntry(spec);
    } catch (FilerException e) {
      List<String> modules =
          ProcessorUtils.compiledModulesDeclaring(processingEnv.getElementUtils(), packageName);
      if (modules.size() > 1) {
        Diagnostics.sharedPackage(
            processingEnv.getMessager(),
            spec,
            TAG,
            "the generated mapping for '" + spec.getSimpleName() + "'",
            packageName,
            modules,
            e.getMessage());
      } else {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "could not write the generated mapping for '"
                + spec.getSimpleName()
                + "': the class already exists.",
            "Nested specs join their enclosing simple names, so two specs can collide on one Impl"
                + " name (for example Outer.Inner and OuterInner). The filer reported: "
                + e.getMessage()
                + ".",
            "Rename one of the colliding specs.");
      }
    } catch (IOException e) {
      writeFailure(spec, e);
    }
  }

  /**
   * Indexes a spec for the compilations that will depend on this one: every {@code MappingSpec}
   * pair (an {@code UpdateSpec} has no parse, so nothing nests it), when the index is in use here
   * (see {@code MappingIndexes.IndexUse}). Written after the Impl, so an entry never describes an
   * Impl that failed to write; a collision on the entry's name is reported here, any other write
   * failure by the caller.
   *
   * <p>A spec declaring both supertypes never reaches this, having been refused before either tier
   * emitted anything, so the {@code MappingSpec} test alone settles it. An entry written for one by
   * an earlier release is registered unusable downstream rather than offered for nesting; see
   * {@link #register}.
   */
  private void writeIndexEntry(TypeElement spec) throws IOException {
    if (findMappingSpec(spec) == null
        || !(MappingIndexes.indexUse(processingEnv, spec)
            instanceof MappingIndexes.IndexUse.Usable)) {
      return;
    }
    try {
      JavaFile.builder(MappingIndexes.INDEX_PACKAGE, MappingIndexes.entry(spec, GENERATED))
          .addFileComment("Generated by hkj-processor. Do not edit.")
          .build()
          .writeTo(processingEnv.getFiler());
    } catch (FilerException e) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "could not write the index entry for '"
              + spec.getSimpleName()
              + "': '"
              + MappingIndexes.entryName(spec).canonicalName()
              + "' already exists.",
          "An entry is named after the spec's canonical name with '$' for each dot, so a spec whose"
              + " own name carries a '$' (which the language reserves for generated code) can share"
              + " an entry with another spec. The filer reported: "
              + e.getMessage()
              + ".",
          "Rename the spec whose name contains '$'.");
    }
  }

  private void writeFailure(TypeElement spec, IOException e) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "could not write the generated mapping for '" + spec.getSimpleName() + "'.",
        "The filer reported: " + e.getMessage() + ".",
        "Check build-output permissions and free disk space, then rebuild.");
  }

  /** The direct {@code MappingSpec<Domain, Wire>} supertype, or null if none. */
  static DeclaredType findMappingSpec(TypeElement spec) {
    for (TypeMirror iface : spec.getInterfaces()) {
      // Superinterface mirrors are always declared (or error) types, both DeclaredType.
      DeclaredType declared = (DeclaredType) iface;
      if (((TypeElement) declared.asElement()).getQualifiedName().contentEquals(MAPPING_SPEC)) {
        return declared;
      }
    }
    return null;
  }

  /** The direct {@code UpdateSpec<Domain, Wire>} supertype, or null if none. */
  static DeclaredType findUpdateSpec(TypeElement spec) {
    for (TypeMirror iface : spec.getInterfaces()) {
      DeclaredType declared = (DeclaredType) iface;
      if (((TypeElement) declared.asElement()).getQualifiedName().contentEquals(UPDATE_SPEC)) {
        return declared;
      }
    }
    return null;
  }

  private static TypeElement asRecord(TypeMirror mirror) {
    // A DeclaredType's element is always a TypeElement.
    if (mirror instanceof DeclaredType declared) {
      TypeElement type = (TypeElement) declared.asElement();
      if (type.getKind() == ElementKind.RECORD) {
        return type;
      }
    }
    return null;
  }

  private static TypeElement asSealed(TypeMirror mirror) {
    if (mirror instanceof DeclaredType declared) {
      TypeElement type = (TypeElement) declared.asElement();
      if (type.getKind() == ElementKind.INTERFACE
          && type.getModifiers().contains(Modifier.SEALED)) {
        return type;
      }
    }
    return null;
  }

  /** A concrete (non-abstract, non-record, non-enum) class: a candidate bean-shaped wire. */
  private static TypeElement asBean(TypeMirror mirror) {
    if (mirror instanceof DeclaredType declared) {
      TypeElement type = (TypeElement) declared.asElement();
      if (type.getKind() == ElementKind.CLASS && !type.getModifiers().contains(Modifier.ABSTRACT)) {
        return type;
      }
    }
    return null;
  }

  /** The domain must be a record (or a sealed interface, handled earlier); a bean domain is not. */
  private void reportUnsupportedDomain(TypeElement spec, TypeMirror domainArg) {
    if (asBean(domainArg) != null) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "the domain type argument '"
              + domainArg
              + "' is a bean-shaped class, which this mapper does not support on the domain side.",
          "parse assembles the domain through its canonical constructor, so the domain must be a"
              + " record (or a sealed interface of records); only the wire may be bean-shaped.",
          "Use a record or sealed interface for the domain, mapping the bean as the wire instead.");
      return;
    }
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "the MappingSpec type arguments of '"
            + spec.getSimpleName()
            + "' must both be records, or both sealed interfaces.",
        "Records map component-wise; sealed hierarchies map by dispatching over their permitted"
            + " subtype pairs; a record domain may also map to a bean-shaped wire.",
        "Use two record types, two sealed interface types, or a record domain with a bean wire.");
  }

  /** The wire (against a record domain) must be a record or a bean-shaped class. */
  private void reportUnsupportedWire(TypeElement spec, TypeMirror wireArg) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "the wire type argument '" + wireArg + "' is neither a record nor a bean-shaped class.",
        "A record domain maps to a record wire (component-wise) or to a bean-shaped wire read"
            + " through getters and written through setters or a builder.",
        "Use a record, or a concrete bean class, for the wire.");
  }

  private TypeMirror containerElement(TypeMirror mirror, String rawName) {
    if (mirror instanceof DeclaredType declared
        && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals(rawName)
        && declared.getTypeArguments().size() == 1) {
      return declared.getTypeArguments().getFirst();
    }
    return null;
  }

  /** The mirror as a {@code java.util.Map} declared type (raw or parameterised), else null. */
  private static DeclaredType asMapType(TypeMirror mirror) {
    if (mirror instanceof DeclaredType declared
        && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals("java.util.Map")) {
      return declared;
    }
    return null;
  }

  /**
   * Whether a declared type carries a wildcard argument that capture conversion would replace with
   * a fresh variable. The enclosing link counts: {@code Outer<? extends Number>.Inner<String>}
   * captures on the {@code Outer} half even though {@code Inner} writes no wildcard of its own, so
   * the walk follows {@code getEnclosingType} as {@code ProcessorUtils.firstRawIn} does for raw. An
   * absent or static enclosing type is a {@code NoType}, which ends the walk. Only the type's own
   * arguments are read, not theirs: a wildcard nested inside one is never captured.
   */
  private static boolean hasWildcardArgument(DeclaredType declared) {
    if (declared.getTypeArguments().stream().anyMatch(t -> t.getKind() == TypeKind.WILDCARD)) {
      return true;
    }
    return declared.getEnclosingType() instanceof DeclaredType enclosing
        && hasWildcardArgument(enclosing);
  }

  private static List<String> wireNames(List<? extends RecordComponentElement> comps) {
    return comps.stream().map(c -> c.getSimpleName().toString()).toList();
  }
}
