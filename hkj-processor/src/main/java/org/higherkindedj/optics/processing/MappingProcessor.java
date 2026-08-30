// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.AnnotationSpec;
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
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
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
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.annotations.ArityCeilings;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MapField;
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
 * returning {@code ValidatedPrism<Wire, Domain>}), through another spec in the same compilation
 * (nesting — every generated impl exposes {@code asValidatedPrism()}, so a whole mapping plugs in
 * wherever a leaf does), or lift through {@code List}/{@code Optional} containers of either. {@code
 * Map} components lift their values the same way; keys are identity-only and must match exactly on
 * both sides, and each entry's parse failures are located by its key. {@code @MapField} declares
 * renames. A wire component with no domain counterpart can be a derived field: a spec {@code
 * default} method named after the wire component returning {@code Getter<Domain,
 * WireComponentType>}. {@code build} fills it with the getter applied to the whole domain value;
 * {@code parse} ignores it (the data is derivable), and a spec with any derived field never emits
 * {@code asIso()}. A wire record with fewer components maps as a lossy projection: {@code build}
 * plus a lawful {@code asLens()} write-back when every projected component matches by identity, or
 * a validated {@code patch(domain, wire)} write-back when any component maps through a leaf, nested
 * spec or container; no {@code parse} either way (truthful types). Sealed interface pairs dispatch
 * {@code build}/{@code parse} over their permitted subtype pairs, each delegating to its own spec.
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
 * Optional<T>} bridges to a nullable bean property {@code T}. A reference-typed bean projection is
 * deferred (the validated-patch tier); an all-primitive one keeps the {@code asLens()} projection.
 *
 * <p>A spec extending {@code UpdateSpec<Domain, Wire>} ({@link
 * org.higherkindedj.optics.annotations.UpdateSpec}) opts into the opposite null contract: a null
 * bean property means <em>absent — leave unchanged</em> rather than invalid. Such a spec emits only
 * {@code updateFrom(Wire) : Edits.Accumulated<Domain>}, folding the present (non-null) properties
 * into an {@code Update} via {@code Edits.accumulate} — no {@code build}, {@code parse}, or {@code
 * as*} tier. A primitive wire property (which can never be absent) is rejected with a diagnostic,
 * and an {@code UpdateSpec} never registers for nesting (it has no {@code parse}).
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
public class MappingProcessor extends AbstractProcessor {

  private static final String TAG = "@GenerateMapping";
  private static final String MAPPING_SPEC = "org.higherkindedj.optics.annotations.MappingSpec";
  private static final String UPDATE_SPEC = "org.higherkindedj.optics.annotations.UpdateSpec";
  private static final String VALIDATED_PRISM = "org.higherkindedj.optics.validated.ValidatedPrism";
  private static final String GETTER = "org.higherkindedj.optics.Getter";
  private static final ClassName VALIDATED_PRISM_TYPE =
      ClassName.get("org.higherkindedj.optics.validated", "ValidatedPrism");
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

  /** Creates a new MappingProcessor. */
  public MappingProcessor() {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    List<RegisteredSpec> registry = scanRegistry(processingEnv, roundEnv);
    for (Element element : roundEnv.getElementsAnnotatedWith(GenerateMapping.class)) {
      processSpec(element, registry);
    }
    return true;
  }

  /**
   * Scans the round for valid {@code @GenerateMapping} specs. Shared with {@link MergeProcessor},
   * whose nested fills resolve against the same parse-capable specs. A record domain may pair with
   * a record wire or a bean-shaped wire; the bean's parse-capability is computed from its
   * getter/setter property count.
   */
  static List<RegisteredSpec> scanRegistry(ProcessingEnvironment env, RoundEnvironment roundEnv) {
    List<RegisteredSpec> registry = new ArrayList<>();
    BeanPropertyAnalyser beanAnalyser = new BeanPropertyAnalyser(env);
    for (Element element : roundEnv.getElementsAnnotatedWith(GenerateMapping.class)) {
      if (element.getKind() != ElementKind.INTERFACE) {
        continue;
      }
      TypeElement spec = (TypeElement) element;
      // A generic spec registers with its declared mirrors (Page<T>, PageDto<TDto>); use sites
      // resolve it by unification, an element-mapped one composing its of(...) factory from the
      // element prisms resolved at the use site.
      DeclaredType specSuper = findMappingSpec(spec);
      if (specSuper == null || specSuper.getTypeArguments().size() != 2) {
        continue;
      }
      TypeMirror domainArg = specSuper.getTypeArguments().get(0);
      TypeMirror wireArg = specSuper.getTypeArguments().get(1);
      TypeElement domainRecord = asRecord(domainArg);
      TypeElement wireRecord = asRecord(wireArg);
      TypeElement wireBean = wireRecord == null ? asBean(wireArg) : null;
      boolean recordPair = domainRecord != null && wireRecord != null;
      boolean beanPair = domainRecord != null && wireBean != null;
      boolean sealedPair = asSealed(domainArg) != null && asSealed(wireArg) != null;
      if (!recordPair && !beanPair && !sealedPair) {
        continue;
      }
      // Only parse-capable specs may be nested into: equal-count record/bean pairs (derived wire
      // fields do not count against the wire, since parse ignores them) and sealed pairs.
      // Projections (smaller wire, no parse) register too, so failed lookups can name them.
      int wireCount =
          recordPair
              ? wireRecord.getRecordComponents().size()
              : beanPair ? beanAnalyser.propertyCount(spec, wireBean) : 0;
      boolean parseCapable =
          sealedPair
              || domainRecord.getRecordComponents().size()
                  == wireCount - derivedCandidateCount(env, spec);
      registry.add(new RegisteredSpec(domainArg, wireArg, implClassName(spec), spec, parseCapable));
    }
    return registry;
  }

  /** A valid spec seen this round; nested components resolve against the parse-capable ones. */
  record RegisteredSpec(
      TypeMirror domain, TypeMirror wire, ClassName impl, TypeElement spec, boolean parseCapable) {}

  private static ClassName implClassName(TypeElement spec) {
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

  /** Zero-parameter, {@code ValidatedPrism}-returning and bodiless: an element-mapped leaf. */
  private boolean isAbstractLeaf(TypeElement owner, ExecutableElement method) {
    return method.getModifiers().contains(Modifier.ABSTRACT)
        && method.getAnnotation(MapField.class) == null
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
   * subtype-narrowest of its group, the same fold {@link #addRenameStubs} applies and {@link
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
   * declared by mistake.
   */
  private record LeafField(String name, TypeName prismType) {}

  /** The spec's abstract leaves, each with its prism type under the spec's instantiation. */
  private List<LeafField> leafFields(TypeElement spec) {
    return abstractLeaves(spec).stream()
        .map(
            leaf ->
                new LeafField(
                    leaf.getSimpleName().toString(),
                    ProcessorUtils.typeNameOf(memberTypeIn(spec, leaf))))
        .toList();
  }

  /** Names a member for diagnostics, noting its declaring mix-in when inherited. */
  private static String inheritedNote(ExecutableElement method, TypeElement spec) {
    return method.getEnclosingElement().equals(spec)
        ? ""
        : " (inherited from '" + method.getEnclosingElement().getSimpleName() + "')";
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
    for (TypeMirror parent : spec.getInterfaces()) {
      if (parent.getKind() != TypeKind.DECLARED) {
        continue;
      }
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
      TypeMirror parent = inherited.type();
      // ErrorType extends DeclaredType, so unresolved parents step aside first: javac already
      // reports the missing type, and there is nothing for the gate to judge.
      if (parent.getKind() == TypeKind.ERROR) {
        continue;
      }
      DeclaredType parentType = (DeclaredType) parent;
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
   * many paths reach it. An unresolved parent is kept for the caller to step over, since javac
   * already reports it and there is nothing beyond it to walk.
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
      if (current.type().getKind() != TypeKind.DECLARED) {
        found.add(current);
        continue;
      }
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
    if (routeLink != null || parent.getKind() != TypeKind.DECLARED) {
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
   * @return true when it declares a default method, a {@code @MapField} rename, or an abstract leaf
   */
  private boolean carriesVocabulary(TypeElement mixin) {
    return ElementFilter.methodsIn(mixin.getEnclosedElements()).stream()
        .anyMatch(
            method ->
                method.isDefault()
                    || method.getAnnotation(MapField.class) != null
                    || isAbstractLeaf(mixin, method));
  }

  /** Whether an interface is, or transitively extends, {@code MappingSpec}/{@code UpdateSpec}. */
  private boolean extendsMappingFamily(TypeElement iface) {
    String name = iface.getQualifiedName().toString();
    if (name.equals(MAPPING_SPEC) || name.equals(UPDATE_SPEC)) {
      return true;
    }
    for (TypeMirror parent : iface.getInterfaces()) {
      // The same ERROR step-aside as checkMixins: an unresolved superinterface is javac's
      // diagnostic, and an error type can neither be nor extend the mapping family.
      if (parent.getKind() == TypeKind.ERROR) {
        continue;
      }
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
  private boolean checkLocalLeavesBind(TypeElement spec, TypeElement domain) {
    List<String> components =
        domain.getRecordComponents().stream().map(c -> c.getSimpleName().toString()).toList();
    for (ExecutableElement method : specMembers(spec)) {
      if (!method.getEnclosingElement().equals(spec) || !isLeafShaped(spec, method)) {
        continue;
      }
      String name = method.getSimpleName().toString();
      if (components.contains(name)) {
        continue;
      }
      // The one leaf-shaped name a generated member also carries: the collision sweep owns it
      // and reports the collision, which is the better diagnostic for that mistake.
      if (name.equals("asValidatedPrism")) {
        continue;
      }
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          "leaf '" + name + "' names no component of " + domain.getSimpleName() + ".",
          "A leaf is a zero-parameter 'default' named after the DOMAIN component it parses; an"
              + " unmatched leaf would silently validate nothing."
              + didYouMean(name, components)
              + " Found on "
              + domain.getSimpleName()
              + ": "
              + components
              + ".",
          "Rename the method to the component it parses, or make it 'private' or 'static' if it"
              + " is a helper.");
      return false;
    }
    return true;
  }

  /**
   * A sealed dispatch has no components, so locally declared leaves and derived fields have nothing
   * to bind to; inherited ones stay inert, so a shared mix-in vocabulary still fits.
   */
  private boolean checkNoSealedVocabulary(TypeElement spec) {
    for (ExecutableElement method : specMembers(spec)) {
      if (!method.getEnclosingElement().equals(spec)) {
        continue;
      }
      boolean leaf = isLeafShaped(spec, method);
      if (!leaf && !isDerivedCandidate(processingEnv.getTypeUtils(), spec, method)) {
        continue;
      }
      Diagnostics.error(
          processingEnv.getMessager(),
          method,
          TAG,
          (leaf ? "leaf '" : "derived field '")
              + method.getSimpleName()
              + "' has no meaning on a sealed mapping.",
          "Leaves and derived fields bind to record components; a sealed mapping dispatches over"
              + " its permitted subtypes and has no components.",
          "Move the method onto the subtype pair's own spec.");
      return false;
    }
    return true;
  }

  /** A nearest-name hint for the unmatched-leaf diagnostic, when one is close enough to help. */
  private static String didYouMean(String name, List<String> candidates) {
    String best = null;
    int bestDistance = 3;
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
   * A spec's abstract methods must be zero-parameter {@code @MapField} renames, or, on a generic
   * pair, element-mapped leaves; anything else would leave the generated Impl with an unimplemented
   * member. Each surviving member must also be one the Impl can hold: no type parameters of its own
   * (a field and a stub have nowhere to declare them), a type reachable from the spec's package
   * (the Impl writes it out in full), and, for a same-named group, a subtype-narrowest return for
   * the one member the Impl emits.
   */
  private boolean validateSpecMethods(
      TypeElement spec, boolean sealedPair, TypeMirror domainArg, TypeMirror wireArg) {
    for (ExecutableElement method : specMembers(spec)) {
      MapField mapField = method.getAnnotation(MapField.class);
      if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
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
            mapField != null
                ? "Give '"
                    + method.getSimpleName()
                    + "' a concrete return type; a rename is a marker method and the generated"
                    + " stub only has to name one."
                : "Declare the element types among the type parameters of '"
                    + method.getEnclosingElement().getSimpleName()
                    + "', where the spec can thread them, or give the method a body.");
        return false;
      }
      if (mapField == null) {
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
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "abstract method '"
                + method.getSimpleName()
                + "'"
                + inheritedNote(method, spec)
                + " is neither a rename nor a leaf.",
            "A spec declares zero-parameter @MapField renames and 'default' leaf methods; the"
                + " generated Impl cannot implement anything else.",
            "Make it a 'default' method, or turn it into a '@MapField(to = ...)' rename.");
        return false;
      }
      if (sealedPair) {
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
      if (method.getAnnotation(MapField.class) != null || isAbstractLeaf(spec, method)) {
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
    // An unresolved argument steps aside so javac's cannot-find-symbol is the only diagnostic
    // (the overrideEquivalent precedent) — note ErrorType extends DeclaredType, so this
    // check must run before the pattern switch.
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
    // emits updateFrom() and nothing else. It never reaches scanRegistry (which matches the direct
    // MappingSpec supertype only), so an UpdateSpec is never nestable — it has no parse.
    DeclaredType updateSuper = findUpdateSpec(spec);
    if (updateSuper != null) {
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
      wireShape = new BeanPropertyAnalyser(processingEnv).analyse(spec, wireBean, TAG);
      if (wireShape == null) {
        return;
      }
      wireUsed = wireBean.asType();
    }

    if (!checkLocalLeavesBind(spec, domain)) {
      return;
    }

    Map<String, String> renames = collectRenames(spec, domain, wireShape);
    if (renames == null) {
      return;
    }

    List<DerivedField> derived = collectDerived(spec, domain, domainDeclared, wireShape, renames);
    if (derived == null) {
      return;
    }

    if (wireShape.componentCount() - derived.size() < domain.getRecordComponents().size()) {
      if (!derived.isEmpty()) {
        reportProjectionWithDerived(spec, domain, wireShape, derived);
        return;
      }
      // A bean projection with a reference property could read null, which neither the lawful
      // lens nor the record-shaped patch tier covers yet (the patch tier ships records only) —
      // deferred
      // rather than emitted unlawfully. An all-primitive bean can never read null and projects
      // as a lawful lens.
      if (wireShape instanceof WireShape.BeanShape && !allPrimitive(wireShape)) {
        reportBeanProjectionDeferred(spec, domain, wireShape);
        return;
      }
      List<Correspondence> projection =
          classifyProjection(spec, registry, domain, domainDeclared, wireShape, renames);
      if (projection == null) {
        return;
      }
      // An all-identity projection keeps the lawful total asLens(); any fallible correspondence
      // makes the write-back partial, which maps as the validated patch tier instead.
      if (projection.stream().noneMatch(Correspondence::fallible)) {
        writeLensImpl(spec, domain, domainDeclared, wireShape, wireUsed, projection);
        return;
      }
      // Bean projections with reference properties are deferred above, and an all-primitive
      // bean cannot carry a fallible correspondence, so the patch tier only ever sees a record.
      writePatchImpl(
          spec, domain, domainDeclared, (WireShape.RecordShape) wireShape, wireUsed, projection);
      return;
    }

    List<Correspondence> correspondences =
        classify(spec, registry, domain, domainDeclared, wireShape, renames, derived);
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

    TypeElement domain = asRecord(domainArg);
    if (domain == null) {
      reportUpdateDomainNotRecord(spec, domainArg);
      return;
    }
    if (!checkLocalLeavesBind(spec, domain)) {
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

    WireShape wireShape = new BeanPropertyAnalyser(processingEnv).analyse(spec, wireBean, TAG);
    if (wireShape == null) {
      return;
    }

    Map<String, String> renames = collectRenames(spec, domain, wireShape);
    if (renames == null) {
      return;
    }

    List<UpdateEdit> edits = classifyUpdate(spec, domain, wireShape, renames, registry);
    if (edits == null) {
      return;
    }
    writeUpdateImpl(spec, domain, wireShape, edits);
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
  private record UpdateEdit(String domainName, String wireName, Kind kind, CodeBlock prism) {

    static UpdateEdit identity(String domainName, String wireName, Kind kind) {
      return new UpdateEdit(domainName, wireName, kind, null);
    }

    static UpdateEdit validated(String domainName, String wireName, Kind kind, CodeBlock prism) {
      return new UpdateEdit(domainName, wireName, kind, prism);
    }

    boolean parsed() {
      return prism != null || kind == Kind.IDENTITY_LIST || kind == Kind.IDENTITY_MAP;
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
          containerLeafCorrespondence(spec, domainName, property.name(), wireType, domainType);
      if (containerLeaf != null) {
        edits.add(
            UpdateEdit.validated(
                domainName, property.name(), containerLeaf.kind(), containerLeaf.prism()));
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
      PrismResolution nested = resolveNestedSpec(spec, registry, domainName, wireType, domainType);
      if (nested.ambiguous()) {
        return null;
      }
      if (nested.accessor() != null) {
        edits.add(UpdateEdit.validated(domainName, property.name(), Kind.LEAF, nested.accessor()));
        continue;
      }

      reportNoUpdateSource(spec, domain, property, domainComp);
      return null;
    }
    return edits;
  }

  /**
   * A spec {@code default} method returning {@code Getter} (a derived field) has no sparse meaning.
   */
  private boolean checkNoDerivedFields(TypeElement spec) {
    for (ExecutableElement method : specMembers(spec)) {
      if (isDerivedCandidate(processingEnv.getTypeUtils(), spec, method)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "the derived-field method '"
                + method.getSimpleName()
                + "' has no meaning on a sparse UpdateSpec.",
            "A derived field feeds build(); a sparse update only writes present wire properties into"
                + " the domain, so there is nothing to derive.",
            "Remove the method, or use a full MappingSpec if you need a total build().");
        return false;
      }
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
        "Use the wrapper type for '"
            + property.name()
            + "' on the PATCH DTO (e.g. Integer, Boolean).");
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
   * sparseness: null already means absent, so "set to empty" is inexpressible (and null-clears
   * would be JSON Merge Patch's opposite contract). An Optional-typed wire property never lands
   * here — it patches by identity or elementwise through an element leaf, and leafless it reaches
   * the no-update-source diagnostic instead.
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
        "Under null-as-absent a null property means 'leave unchanged', so setting the component to"
            + " an empty Optional has no encoding; a null-clears rule would be the opposite contract"
            + " (JSON Merge Patch).",
        "Model the field as a nested record or a sentinel value instead of Optional, or declare the"
            + " PATCH property as Optional<"
            + containerElement(domainComp.asType(), "java.util.Optional")
            + "> (a present empty Optional then encodes 'set to empty').");
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

  /** A wire property matches a domain component by name but neither by type nor through a leaf. */
  private void reportNoUpdateSource(
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
            + " pair declared as exactly List, Optional or Map — through an element leaf lifted"
            + " over the container."
            + leafNearMissHint(spec, domainComp.getSimpleName().toString()),
        // A leaf cannot target a primitive component: a ValidatedPrism's domain arg is a reference
        // type, so findLeaf's isSameType(wrapper, primitive) can never match. Steer to alignment.
        domainComp.asType().getKind().isPrimitive()
            ? "Align the types: make '"
                + domainComp.getSimpleName()
                + "' a wrapper type, or match the wire property to "
                + domainComp.asType()
                + "."
            : updateLeafSuggestion(property, domainComp));
  }

  /**
   * The fix line for {@link #reportNoUpdateSource}: for a container pair the element leaf comes
   * first (the shared-vocabulary form, lifted over the container), with the whole-container leaf as
   * the more specific alternative; scalar pairs keep the whole-component suggestion.
   */
  private String updateLeafSuggestion(
      WireShape.WireComponent property, RecordComponentElement domainComp) {
    TypeMirror wireType = property.type();
    TypeMirror domainType = domainComp.asType();
    String name = domainComp.getSimpleName().toString();
    TypeMirror wireElement = null;
    TypeMirror domainElement = null;
    for (String container : List.of("java.util.List", "java.util.Optional")) {
      wireElement = containerElement(wireType, container);
      domainElement = containerElement(domainType, container);
      if (wireElement != null && domainElement != null) {
        break;
      }
    }
    if (wireElement == null || domainElement == null) {
      // The same gate containerLeafCorrespondence applies: mismatched key types reject the pair
      // before any leaf is consulted, so suggesting an element (value) leaf there would be futile.
      DeclaredType[] mapPair = liftableMapPair(wireType, domainType);
      if (mapPair != null) {
        wireElement = mapPair[0].getTypeArguments().get(1);
        domainElement = mapPair[1].getTypeArguments().get(1);
      }
    }
    // A wildcard element type can never match a leaf (isSameType is false for wildcards), so the
    // element form is only offered when a leaf declaring it could actually bind.
    if (wireElement != null
        && domainElement != null
        && wireElement.getKind() != TypeKind.WILDCARD
        && domainElement.getKind() != TypeKind.WILDCARD) {
      return "Declare an element leaf 'default ValidatedPrism<"
          + wireElement
          + ", "
          + domainElement
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
    return "Declare a leaf 'default ValidatedPrism<"
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
      TypeElement spec, TypeElement domain, WireShape wire, List<UpdateEdit> edits) {
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
              case LIST -> CodeBlock.of("$L::parseAll", edit.prism());
              case MAP -> CodeBlock.of("$L::parseValues", edit.prism());
              case OPTIONAL -> elementOfOptionalParser(edit.prism());
              case IDENTITY_LIST -> CodeBlock.of("$T::hkj$$allPresent", implName);
              case IDENTITY_MAP -> CodeBlock.of("$T::hkj$$valuesPresent", implName);
              // LEAF; the dense-only kinds (OPTIONAL_BRIDGE, DERIVED) and plain IDENTITY are
              // never constructed as parsed sparse edits, and the Kind-canary test forces a
              // deliberate arm here before any new Kind can reach this switch.
              default -> CodeBlock.of("$L::parse", edit.prism());
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
    addRenameStubs(implBuilder, spec);
    if (edits.stream().anyMatch(e -> e.kind() == Kind.IDENTITY_LIST)) {
      implBuilder.addMethod(allPresentHelper());
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
                        c.getSimpleName().toString()))
            .toList();
    return new WireShape.RecordShape(wire, components);
  }

  // Package-visible for the Kind-canary test, which pins the constant list so a new kind must
  // choose its sparse emission (writeUpdateImpl's parser switch) before it can land.
  enum Kind {
    IDENTITY,
    // Same-typed List/Map components: copied by identity, but parse scans for null
    // elements/values so the located-null doctrine holds inside identity containers too.
    IDENTITY_LIST,
    IDENTITY_MAP,
    LEAF,
    LIST,
    OPTIONAL,
    // A domain Optional<T> bridged to a nullable bean property T: empty <-> null/absent.
    OPTIONAL_BRIDGE,
    MAP,
    DERIVED
  }

  /**
   * {@code prism} is an expression yielding the ValidatedPrism for every non-identity kind, except
   * {@code DERIVED}, where it yields the spec's Getter accessor instead.
   */
  private record Correspondence(String name, String wireName, Kind kind, CodeBlock prism) {
    boolean fallible() {
      return switch (kind) {
        // Identity-container null scans guard hostile bindings, like every identity guard;
        // they do not make the mapping fallible for tier selection.
        case IDENTITY, IDENTITY_LIST, IDENTITY_MAP -> false;
        default -> true;
      };
    }
  }

  /**
   * The sparse tier's identity kind: the null scan applies only where the emitted generic helper
   * can type — an exactly-{@code List}/{@code Map} component whose type arguments are proper types.
   * A raw or wildcard-argument container stays a plain identity write ({@code setIfPresent}): the
   * helper's method reference must produce the component's exact type, and a wildcard captures
   * differently on the argument and return sides while a raw type erases the call, so either shape
   * would fail to compile inside the user's generated Impl.
   */
  private Kind sparseIdentityKind(TypeMirror type) {
    Kind kind = identityKind(type);
    if (kind == Kind.IDENTITY) {
      return Kind.IDENTITY;
    }
    DeclaredType declared = (DeclaredType) type;
    boolean scanTypable = !declared.getTypeArguments().isEmpty() && !hasWildcardArgument(declared);
    return scanTypable ? kind : Kind.IDENTITY;
  }

  /** Identity components copy verbatim; a same-typed List/Map additionally scans for nulls. */
  private Kind identityKind(TypeMirror type) {
    if (isExactly(type, "java.util.List")) {
      return Kind.IDENTITY_LIST;
    }
    if (isExactly(type, "java.util.Map")) {
      return Kind.IDENTITY_MAP;
    }
    return Kind.IDENTITY;
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
   * Resolves the ValidatedPrism carrying a (wireType -> domainType) correspondence through a single
   * same-round mapping spec for the pair (via its generated impl's {@code asValidatedPrism()}).
   * Explicit leaves are matched by {@link #classify} and {@link #classifyUpdate} before identity
   * classification ever runs — whole-component leaves directly, container ELEMENT/VALUE leaves
   * through {@link #containerLeafCorrespondence} — so by the time a nested spec is consulted no
   * leaf exists for the pair. More than one candidate spec is reported as an error.
   */
  private PrismResolution resolveNestedSpec(
      TypeElement spec,
      List<RegisteredSpec> registry,
      String name,
      TypeMirror wireType,
      TypeMirror domainType) {
    return resolveNestedSpec(spec, registry, name, wireType, domainType, List.of());
  }

  /**
   * The guarded overload: {@code active} carries the (domain, wire) pairs already being composed on
   * the current element-mapped recursion, so a spec whose leaf pair covers itself is caught instead
   * of overflowing the stack.
   */
  private PrismResolution resolveNestedSpec(
      TypeElement spec,
      List<RegisteredSpec> registry,
      String name,
      TypeMirror wireType,
      TypeMirror domainType,
      List<DeclaredType> active) {
    List<RegisteredSpec> nested =
        registry.stream()
            .filter(RegisteredSpec::parseCapable)
            .filter(r -> covers(spec, r, domainType, wireType))
            .toList();
    if (nested.size() > 1) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "field '"
              + name
              + "' matches more than one mapping spec: "
              + nested.stream().map(r -> r.spec().getSimpleName().toString()).toList()
              + ".",
          "A nested component resolves to the single spec mapping ("
              + domainType
              + ", "
              + wireType
              + "); with several, the choice would be arbitrary.",
          "Add a leaf method '"
              + name
              + "()' delegating to the spec you want, or remove the duplicate spec.");
      return new PrismResolution(null, true);
    }
    if (nested.size() == 1) {
      RegisteredSpec match = nested.getFirst();
      if (match.spec().getTypeParameters().isEmpty()) {
        return new PrismResolution(
            CodeBlock.of("$T.INSTANCE.asValidatedPrism()", match.impl()), false);
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
          spec, registry, name, match, bindings, leaves, domainType, wireType, active);
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
              + match.spec().getSimpleName()
              + "', which maps itself: resolving its leaf returns to the pair ("
              + domainType
              + ", "
              + wireType
              + ").",
          "An of(...) composition needs a prism for every leaf; a self-covering element mapping"
              + " would need its own prism as that input, so the composition never terminates.",
          "Break the cycle with a leaf on this spec for the pair, or map the element with a"
              + " non-recursive spec.");
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
      PrismResolution nested =
          resolveNestedSpec(spec, registry, name, elementWire, elementDomain, nestedActive);
      if (nested.ambiguous()) {
        return nested;
      }
      if (nested.accessor() != null) {
        prisms.add(nested.accessor());
        continue;
      }
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "field '"
              + name
              + "' nests the element-mapped '"
              + match.spec().getSimpleName()
              + "', but the element pair ("
              + elementDomain
              + ", "
              + elementWire
              + ") for its leaf '"
              + leaf.getSimpleName()
              + "' has no mapping.",
          "An element-mapped Impl is built by of(...), one ValidatedPrism per abstract leaf; the"
              + " prism must come from a leaf on this spec or another mapping in this"
              + " compilation.",
          "Declare 'default ValidatedPrism<"
              + elementWire
              + ", "
              + elementDomain
              + "> "
              + name
              + "()' on this spec, or map the pair with its own @GenerateMapping spec.");
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
    // never registers); only the use site can carry an ERROR, and it steps aside.
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

  private Map<String, String> collectRenames(TypeElement spec, TypeElement domain, WireShape wire) {
    Map<String, String> renames = new LinkedHashMap<>();
    Map<String, ExecutableElement> renameSources = new LinkedHashMap<>();
    for (ExecutableElement method : specMembers(spec)) {
      MapField mapField = method.getAnnotation(MapField.class);
      if (mapField == null) {
        continue;
      }
      String name = method.getSimpleName().toString();
      boolean onDomain =
          domain.getRecordComponents().stream()
              .anyMatch(c -> c.getSimpleName().contentEquals(name));
      if (!onDomain) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "@MapField method '"
                + name
                + "' does not name a component of "
                + domain.getSimpleName()
                + ".",
            "Renames are declared as an abstract method named after the DOMAIN component. Found"
                + " on "
                + domain.getSimpleName()
                + ": "
                + wireNames(domain.getRecordComponents())
                + ".",
            "Rename the method to a domain component, or remove @MapField.");
        return null;
      }
      boolean onWire = wire.componentNamed(mapField.to()).isPresent();
      if (!onWire) {
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

  /** Counts derived candidates for the registry's parse-capability arithmetic. */
  private static long derivedCandidateCount(ProcessingEnvironment env, TypeElement spec) {
    return specMembers(env.getElementUtils(), spec).stream()
        .filter(method -> isDerivedCandidate(env.getTypeUtils(), spec, method))
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
      Map<String, String> renames) {
    List<DerivedField> derived = new ArrayList<>();
    for (ExecutableElement method : specMembers(spec)) {
      if (!isDerivedCandidate(processingEnv.getTypeUtils(), spec, method)) {
        continue;
      }
      String name = method.getSimpleName().toString();
      if (domain.getRecordComponents().stream()
          .anyMatch(c -> c.getSimpleName().contentEquals(name))) {
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
      WireShape.WireComponent wireComponent = wire.componentNamed(name).orElse(null);
      if (wireComponent == null) {
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
      List<DerivedField> derived) {
    List<Correspondence> result = new ArrayList<>();
    List<WireShape.WireComponent> wireComponents = wire.components();
    List<String> domainNames =
        domain.getRecordComponents().stream().map(c -> c.getSimpleName().toString()).toList();

    if (wireComponents.size() - derived.size() != domain.getRecordComponents().size()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'"
              + wire.element().getSimpleName()
              + "' has more components than '"
              + domain.getSimpleName()
              + "'.",
          "build must fill every wire component from a domain source or a derived field, and the"
              + " extras have neither. A wire with fewer components maps as a projection (Lens"
              + " tier).",
          "Remove the extra wire components, add matching domain components, or declare derived"
              + " fields ('default Getter<"
              + domain.getSimpleName()
              + ", ComponentType>' methods named after the extras).");
      return null;
    }

    Map<String, String> claimedWire = new LinkedHashMap<>();
    for (RecordComponentElement domainComponent : domain.getRecordComponents()) {
      String name = domainComponent.getSimpleName().toString();
      String wireName = renames.getOrDefault(name, name);
      WireShape.WireComponent wireComponent = wire.componentNamed(wireName).orElse(null);
      if (wireComponent == null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "domain field '"
                + domain.getSimpleName()
                + "."
                + name
                + "' has no wire counterpart named '"
                + wireName
                + "'.",
            "Found on " + wire.element().getSimpleName() + ": " + wire.componentNames() + ".",
            "Align the component names, or add a '@MapField(to = ...)' rename on the spec.");
        return null;
      }
      String previousSource = claimedWire.putIfAbsent(wireName, name);
      if (previousSource != null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "domain components '"
                + previousSource
                + "' and '"
                + name
                + "' both map to wire component '"
                + wireName
                + "'.",
            "Each wire component takes exactly one domain source; a @MapField rename may not"
                + " collide with another component's mapping.",
            "Point the rename at a distinct wire component.");
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
              false);
      if (resolved == null) {
        return null;
      }
      result.add(resolved);
    }
    for (DerivedField field : derived) {
      // Diagnostics in collectDerived guarantee the derived names are disjoint from the
      // domain-sourced claims, and the count check above that together they cover the wire.
      result.add(
          new Correspondence(
              field.wireName(),
              field.wireName(),
              Kind.DERIVED,
              CodeBlock.of("$L()", field.wireName())));
    }
    return result;
  }

  /**
   * Resolves one domain-component/wire-component pair to its correspondence: an explicit leaf first
   * (beating even a same-typed identity match), container element/value leaves, identity,
   * nested-spec lifting through {@code List}/{@code Optional}/{@code Map}, the bean Optional
   * bridge, then a direct nested spec — reporting and returning null when nothing usable exists.
   * Shared by the full tier ({@link #classify}) and the projection tiers ({@link
   * #classifyProjection}), so a projection resolves exactly like a full-tier component.
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
      boolean projection) {
    // An explicit leaf always wins — even over a same-typed identity match, so a
    // ValidatedPrism<X, X> can validate or normalise a component the types alone would copy.
    ExecutableElement directLeaf = findLeaf(spec, name, wireType, domainType);
    if (directLeaf != null) {
      return new Correspondence(
          name, wireName, Kind.LEAF, CodeBlock.of("$L()", directLeaf.getSimpleName()));
    }
    // The same rule lifted through containers: an ELEMENT/VALUE-typed leaf on a List, Optional
    // or Map component beats the identity copy the container types alone would take. Only
    // explicit leaves pre-empt identity; nested-spec resolution never does (the scalar
    // precedent).
    Correspondence containerLeaf =
        containerLeafCorrespondence(spec, name, wireName, wireType, domainType);
    if (containerLeaf != null) {
      return containerLeaf;
    }
    if (processingEnv.getTypeUtils().isSameType(domainType, wireType)) {
      return new Correspondence(name, wireName, identityKind(domainType), null);
    }
    TypeMirror wireElement = containerElement(wireType, "java.util.List");
    TypeMirror domainElement = containerElement(domainType, "java.util.List");
    if (wireElement != null && domainElement != null) {
      PrismResolution lifted = resolveNestedSpec(spec, registry, name, wireElement, domainElement);
      if (lifted.ambiguous()) {
        return null;
      }
      if (lifted.accessor() != null) {
        return new Correspondence(name, wireName, Kind.LIST, lifted.accessor());
      }
    }
    wireElement = containerElement(wireType, "java.util.Optional");
    domainElement = containerElement(domainType, "java.util.Optional");
    if (wireElement != null && domainElement != null) {
      PrismResolution lifted = resolveNestedSpec(spec, registry, name, wireElement, domainElement);
      if (lifted.ambiguous()) {
        return null;
      }
      if (lifted.accessor() != null) {
        return new Correspondence(name, wireName, Kind.OPTIONAL, lifted.accessor());
      }
    }
    // Optional bridge: a domain Optional<DE> maps to a nullable bean property PE, since
    // beans never declare Optional. Empty <-> null/absent; the element is copied (identity) or
    // mapped through a leaf, exactly as an Optional element would be. (An Optional bridge through
    // a nested spec is a follow-up.)
    if (wire instanceof WireShape.BeanShape && domainElement != null && wireElement == null) {
      if (processingEnv.getTypeUtils().isSameType(wireType, domainElement)) {
        return new Correspondence(name, wireName, Kind.OPTIONAL_BRIDGE, null);
      }
      ExecutableElement bridgeLeaf = findLeaf(spec, name, wireType, domainElement);
      if (bridgeLeaf != null) {
        return new Correspondence(
            name, wireName, Kind.OPTIONAL_BRIDGE, CodeBlock.of("$L()", bridgeLeaf.getSimpleName()));
      }
      // A bridge is the only way to map a domain Optional to a plain bean property, so a failed
      // one is a dedicated diagnostic that names the ELEMENT types (not the whole Optional) — a
      // leaf over Optional<DE> would be matched as a plain leaf and bypass the bridge.
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "domain field '"
              + domain.getSimpleName()
              + "."
              + name
              + "' is Optional<"
              + domainElement
              + ">, bridged to the nullable bean property '"
              + wireName
              + "' of type "
              + wireType
              + ", but the element types differ and no leaf converts them.",
          "A domain Optional bridges to a nullable bean property (empty maps to absent); the"
              + " present element is copied when the types match, or mapped through a leaf named"
              + " after the domain component returning ValidatedPrism<"
              + wireType
              + ", "
              + domainElement
              + "> (the element types, not the Optional).",
          "Add 'default ValidatedPrism<"
              + wireType
              + ", "
              + domainElement
              + "> "
              + name
              + "()' to the spec, or align the element types.");
      return null;
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
        return null;
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
        return null;
      }
      TypeMirror wireKey = wireMapType.getTypeArguments().getFirst();
      TypeMirror domainKey = domainMapType.getTypeArguments().getFirst();
      if (!processingEnv.getTypeUtils().isSameType(wireKey, domainKey)) {
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
            "Keys pass through as identity; only Map values lift through a leaf or nested spec,"
                + " so the key types must match exactly.",
            "Align the key types (mapping the value type through a leaf or spec), or"
                + " restructure to a List of entry records mapped through their own spec.");
        return null;
      }
      PrismResolution lifted =
          resolveNestedSpec(
              spec,
              registry,
              name,
              wireMapType.getTypeArguments().get(1),
              domainMapType.getTypeArguments().get(1));
      if (lifted.ambiguous()) {
        return null;
      }
      if (lifted.accessor() != null) {
        return new Correspondence(name, wireName, Kind.MAP, lifted.accessor());
      }
      // Values resolving to nothing fall through to the no-usable-source error, like List
      // elements.
    }
    PrismResolution direct = resolveNestedSpec(spec, registry, name, wireType, domainType);
    if (direct.ambiguous()) {
      return null;
    }
    if (direct.accessor() == null) {
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
              + leafNearMissHint(spec, name)
              + projectionSpecHint(registry, wireType, domainType)
              + " Found on "
              + domain.getSimpleName()
              + ": "
              + domainNames
              + ".",
          // A leaf cannot target a primitive component: a ValidatedPrism's domain argument is a
          // reference type, so findLeaf's isSameType(wrapper, primitive) can never match, and
          // suggesting 'ValidatedPrism<..., int>' would be uncompilable Java. Steer to
          // alignment, as the sparse tier does.
          domainType.getKind().isPrimitive()
              ? "Make '"
                  + name
                  + "' a wrapper type (a ValidatedPrism cannot focus a primitive component), or"
                  + " align the component types."
              : "Add 'default ValidatedPrism<"
                  + wireType
                  + ", "
                  + domainType
                  + "> "
                  + name
                  + "()' to the spec, or declare a @GenerateMapping spec mapping those records in"
                  + " the same compilation."
                  + (projection
                      ? " On a projection, adding the leaf makes the write-back fallible: the"
                          + " Impl then emits the validated patch(domain, wire) instead of"
                          + " asLens()."
                      : ""));
      return null;
    }
    return new Correspondence(name, wireName, Kind.LEAF, direct.accessor());
  }

  /**
   * Classifies a projection: the wire record is smaller, so it maps lossily, and every wire
   * component must name a domain component. Each pair then resolves exactly like a full-tier
   * component (explicit leaf first, identity, nested specs, container lifting) via {@link
   * #resolveCorrespondence}. All-identity projections keep the lawful total {@code asLens()}
   * write-back; any fallible correspondence selects the validated {@code patch} tier.
   */
  private List<Correspondence> classifyProjection(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape wire,
      Map<String, String> renames) {
    Map<String, String> domainByWire = new LinkedHashMap<>();
    renames.forEach((domainName, wireName) -> domainByWire.put(wireName, domainName));
    List<String> domainNames =
        domain.getRecordComponents().stream().map(c -> c.getSimpleName().toString()).toList();
    Set<String> usedDomain = new LinkedHashSet<>();
    List<Correspondence> result = new ArrayList<>();
    for (WireShape.WireComponent wireComponent : wire.components()) {
      String wireName = wireComponent.name();
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
            "projection field '"
                + wire.element().getSimpleName()
                + "."
                + wireName
                + "' has no domain source.",
            "'"
                + wire.element().getSimpleName()
                + "' is smaller than '"
                + domain.getSimpleName()
                + "', so it maps as a projection: every wire component must name a domain"
                + " component. Found on "
                + domain.getSimpleName()
                + ": "
                + wireNames(domain.getRecordComponents())
                + ".",
            "Align the component names, or add a @MapField rename.");
        return null;
      }
      if (!usedDomain.add(name)) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "domain component '" + name + "' sources more than one wire component.",
            "The projection write-back (asLens or patch) writes each wire component back to its"
                + " own domain component; a shared source would discard one wire value on"
                + " write-back.",
            "Point the @MapField rename at a different domain component, or drop one wire"
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
              true);
      if (resolved == null) {
        return null;
      }
      result.add(resolved);
    }
    return result;
  }

  /**
   * The container analogue of the whole-component leaf check, applied BEFORE the identity
   * short-circuit (by the dense tiers and {@link #classifyUpdate} alike): an explicit
   * ELEMENT/VALUE-typed leaf on a {@code List}, {@code Optional} or {@code Map} component wins even
   * when both sides declare the same container type, so a normalising {@code ValidatedPrism<X, X>}
   * still runs. For {@code Map} the key types must already match (keys are identity-only);
   * mismatches fall through to the post-identity diagnostics. Returns null when no such leaf
   * exists.
   */
  private Correspondence containerLeafCorrespondence(
      TypeElement spec, String name, String wireName, TypeMirror wireType, TypeMirror domainType) {
    TypeMirror wireElement = containerElement(wireType, "java.util.List");
    TypeMirror domainElement = containerElement(domainType, "java.util.List");
    if (wireElement != null && domainElement != null) {
      return elementLeafCorrespondence(spec, name, wireName, Kind.LIST, wireElement, domainElement);
    }
    wireElement = containerElement(wireType, "java.util.Optional");
    domainElement = containerElement(domainType, "java.util.Optional");
    if (wireElement != null && domainElement != null) {
      return elementLeafCorrespondence(
          spec, name, wireName, Kind.OPTIONAL, wireElement, domainElement);
    }
    DeclaredType[] mapPair = liftableMapPair(wireType, domainType);
    if (mapPair != null) {
      return elementLeafCorrespondence(
          spec,
          name,
          wireName,
          Kind.MAP,
          mapPair[0].getTypeArguments().get(1),
          mapPair[1].getTypeArguments().get(1));
    }
    return null;
  }

  /**
   * The (wire, domain) pair as value-liftable {@code Map} types, else null: both sides
   * parameterised {@code Map}s whose key types match — the single gate shared by {@link
   * #containerLeafCorrespondence} and the sparse no-update-source suggestion, so the suggestion can
   * never offer a value leaf the resolver would refuse to consult.
   */
  private DeclaredType[] liftableMapPair(TypeMirror wireType, TypeMirror domainType) {
    DeclaredType wireMap = asMapType(wireType);
    DeclaredType domainMap = asMapType(domainType);
    return wireMap != null
            && domainMap != null
            && wireMap.getTypeArguments().size() == 2
            && domainMap.getTypeArguments().size() == 2
            && processingEnv
                .getTypeUtils()
                .isSameType(
                    wireMap.getTypeArguments().getFirst(), domainMap.getTypeArguments().getFirst())
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
      boolean leafShaped = method.isDefault() || method.getModifiers().contains(Modifier.ABSTRACT);
      if (!method.getSimpleName().contentEquals(name)
          || !leafShaped
          || method.getAnnotation(MapField.class) != null
          || !method.getParameters().isEmpty()) {
        continue;
      }
      if (!(memberTypeIn(spec, method) instanceof DeclaredType returnType)) {
        continue;
      }
      TypeElement raw = (TypeElement) returnType.asElement();
      if (!raw.getQualifiedName().contentEquals(VALIDATED_PRISM)
          || returnType.getTypeArguments().size() != 2) {
        continue;
      }
      boolean matches =
          processingEnv.getTypeUtils().isSameType(returnType.getTypeArguments().get(0), wireType)
              && processingEnv
                  .getTypeUtils()
                  .isSameType(returnType.getTypeArguments().get(1), domainType);
      if (matches) {
        return method;
      }
    }
    return null;
  }

  private String leafNearMissHint(TypeElement spec, String name) {
    for (ExecutableElement method : specMembers(spec)) {
      if (method.getSimpleName().contentEquals(name) && method.isDefault()) {
        return " A default method '"
            + name
            + "()' exists but returns '"
            + memberTypeIn(spec, method)
            + "'"
            + (method.getParameters().isEmpty() ? "" : " and declares parameters")
            + " — a leaf must be a zero-parameter default method returning exactly"
            + " ValidatedPrism<WireComponent, DomainComponent> (wire first, domain second).";
      }
    }
    return "";
  }

  private String projectionSpecHint(
      List<RegisteredSpec> registry, TypeMirror wireType, TypeMirror domainType) {
    return registry.stream()
        .filter(r -> !r.parseCapable())
        .filter(
            r ->
                processingEnv.getTypeUtils().isSameType(r.domain(), domainType)
                    && processingEnv.getTypeUtils().isSameType(r.wire(), wireType))
        .findFirst()
        .map(
            r ->
                " '"
                    + r.spec().getSimpleName()
                    + "' maps this pair but is a projection (no parse), so it cannot be nested.")
        .orElse("");
  }

  /** The expression {@code build} fills a wire component with, from its correspondence. */
  private static CodeBlock buildValue(WireShape.WireComponent wc, List<Correspondence> comps) {
    Correspondence c =
        comps.stream().filter(x -> x.wireName().equals(wc.name())).findFirst().orElseThrow();
    return switch (c.kind()) {
      case LEAF -> CodeBlock.of("$L.build(domain.$L())", c.prism(), c.name());
      case LIST -> CodeBlock.of("$L.buildAll(domain.$L())", c.prism(), c.name());
      case OPTIONAL -> CodeBlock.of("domain.$L().map($L::build)", c.name(), c.prism());
      // The domain Optional is carried as-is (identity) or its element built through the leaf; the
      // conditional ifPresent write lives in beanBuildBody, so an empty Optional skips the write.
      case OPTIONAL_BRIDGE ->
          c.prism() == null
              ? CodeBlock.of("domain.$L()", c.name())
              : CodeBlock.of("domain.$L().map($L::build)", c.name(), c.prism());
      case MAP -> CodeBlock.of("$L.buildValues(domain.$L())", c.prism(), c.name());
      case IDENTITY, IDENTITY_LIST, IDENTITY_MAP -> CodeBlock.of("domain.$L()", c.name());
      case DERIVED -> CodeBlock.of("$L.get(domain)", c.prism());
    };
  }

  /**
   * The bean {@code build} body: the strategy frames the construction, and each property writes its
   * build value between the frame. An {@code Optional}-bridged property writes conditionally, so an
   * empty domain Optional leaves the bean property unset (protecting null-hostile setters).
   */
  private static CodeBlock beanBuildBody(
      WireShape.BeanShape bean, TypeName wireType, List<Correspondence> comps) {
    WireShape.ConstructionStrategy strategy = bean.strategy();
    String receiver = strategy.receiver();
    CodeBlock.Builder body = CodeBlock.builder().add(strategy.prologue(wireType));
    for (WireShape.BeanProperty property : bean.properties()) {
      Correspondence c =
          comps.stream()
              .filter(x -> x.wireName().equals(property.name()))
              .findFirst()
              .orElseThrow();
      CodeBlock value = buildValue(property.asWireComponent(), comps);
      if (c.kind() == Kind.OPTIONAL_BRIDGE) {
        body.addStatement(
            "$L.ifPresent(v -> $L)", value, property.write().write(receiver, CodeBlock.of("v")));
      } else {
        body.addStatement("$L", property.write().write(receiver, value));
      }
    }
    return body.add(strategy.epilogue()).build();
  }

  /**
   * The read expression for the wire component named {@code wireName}, from the {@code wire} var.
   */
  private static CodeBlock wireRead(WireShape wire, String wireName) {
    return wire.componentNamed(wireName).orElseThrow().readFrom("wire");
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
    return guardedRead(c, wire) && c.kind() != Kind.IDENTITY_LIST && c.kind() != Kind.IDENTITY_MAP;
  }

  /**
   * Whether a guarded read costs the Iso tier: only on a bean wire. A bean's reference property is
   * legitimately unset in normal use, so its guarded read is fallible and {@code asIso()} stays
   * truthful only for an all-primitive bean. A record wire's guard exists for hostile input (a
   * null-carrying JSON binding), not for a representable absent state, so a lossless record mapping
   * keeps {@code asIso()} — the parse-iso coherence law is scoped to wires whose reference
   * components are non-null.
   */
  private static boolean lossyRead(Correspondence c, WireShape wire) {
    return wire instanceof WireShape.BeanShape && guardedRead(c, wire);
  }

  /**
   * The {@code hkj$allPresent} guard for identity-copied {@code List} components: total over a null
   * list ({@code must not be null}, labelled by the ladder), and each null element is a located
   * invalid at its index, accumulating - the same doctrine {@code ValidatedPrism#parseAll} enforces
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
   * The {@code hkj$valuesPresent} guard for identity-copied {@code Map} components: total over a
   * null map, and each null value is a located invalid under its key, accumulating - matching
   * {@code ValidatedPrism#parseValues}. Keys are structural: a null key stays the caller's {@code
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
    TypeName parseReturn =
        ParameterizedTypeName.get(
            VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), domainName);

    // Derived fields are non-identity, so they exclude the Iso tier too: wire -> domain -> wire
    // recomputes the derived component, an identity only for wire values already consistent. A
    // bean's null-guarded reference reads are fallible too (an unset property is a representable
    // state), so only an all-primitive bean stays lossless; a record wire's guards are for
    // hostile null bindings only and do not cost the Iso tier.
    boolean lossless = comps.stream().noneMatch(c -> c.fallible() || lossyRead(c, wire));
    boolean needsGuardHelper = comps.stream().anyMatch(c -> usesIfPresent(c, wire));
    boolean needsAllPresent = comps.stream().anyMatch(c -> c.kind() == Kind.IDENTITY_LIST);
    boolean needsValuesPresent = comps.stream().anyMatch(c -> c.kind() == Kind.IDENTITY_MAP);

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

    CodeBlock buildBody =
        switch (wire) {
          case WireShape.RecordShape r -> r.buildStatements(wireName, wc -> buildValue(wc, comps));
          case WireShape.BeanShape b -> beanBuildBody(b, wireName, comps);
        };

    List<CodeBlock> parseLegs = new ArrayList<>();
    for (Correspondence c : comps) {
      // An unset bean property and a Jackson-bound missing record component both read null, so
      // every reference read is guarded before it reaches a leaf (whose parse rejects null) or
      // the identity copy; the guard locates the null under the field label.
      CodeBlock leg = parseLeg(c, wireRead(wire, c.wireName()), guardedRead(c, wire));
      if (!leg.isEmpty()) {
        parseLegs.add(leg);
      }
    }
    CodeBlock parseBody;
    if (parseLegs.size() <= ArityCeilings.ASSEMBLY) {
      CodeBlock.Builder parseChain = CodeBlock.builder().add("return $T.fields()", VALIDATED);
      parseLegs.forEach(parseChain::add);
      parseChain.add("\n.apply($T::new)", domainName);
      parseBody = CodeBlock.builder().addStatement("$L", parseChain.build()).build();
    } else {
      // Wider than one fields() ladder: chunked ladders, identical error semantics.
      parseBody =
          ChunkedAssembly.emit(
              parseLegs,
              VALIDATED,
              NEL,
              Set.of("wire"),
              values -> CodeBlock.of("new $T($L)", domainName, CodeBlock.join(values, ", ")));
    }

    CodeBlock.Builder reverseArgs = CodeBlock.builder();
    boolean firstReverse = true;
    for (Correspondence c : comps) {
      if (!firstReverse) {
        reverseArgs.add(", ");
      }
      firstReverse = false;
      reverseArgs.add(wireRead(wire, c.wireName()));
    }

    TypeSpec.Builder implBuilder =
        implSkeleton(
                spec,
                implName,
                specName,
                "Generated bidirectional mapping for {@link $T}: total {@code build} and"
                    + " accumulating, located {@code parse}.\n",
                leafFields(spec))
            .addMethod(buildMethod(domainName, wireName, buildBody))
            .addMethod(
                MethodSpec.methodBuilder("parse")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(parseReturn)
                    .addParameter(wireName, "wire")
                    .addStatement("$T.requireNonNull(wire, $S)", OBJECTS, "wire must not be null")
                    .addCode(parseBody)
                    .build())
            .addMethod(asValidatedPrismMethod(wireName, domainName));

    addRenameStubs(implBuilder, spec);

    if (needsGuardHelper) {
      implBuilder.addMethod(ifPresentHelper());
    }
    if (needsAllPresent) {
      implBuilder.addMethod(allPresentHelper());
    }
    if (needsValuesPresent) {
      implBuilder.addMethod(valuesPresentHelper());
    }

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
                  "return $T.of(this::build, wire -> new $T($L))",
                  iso,
                  domainName,
                  reverseArgs.build())
              .build());
    }
    writeFile(spec, specName.packageName(), implBuilder.build());
  }

  /**
   * The element-of-Optional parser lambda, shared by the dense {@code OPTIONAL} leg and the sparse
   * {@code OPTIONAL} edit so the two tiers cannot drift: a present element parses through the leaf,
   * an empty Optional is valid emptiness.
   */
  private static CodeBlock elementOfOptionalParser(CodeBlock prism) {
    ClassName optional = ClassName.get("java.util", "Optional");
    return CodeBlock.of(
        "o -> o.map(v -> $L.parse(v).map($T::of)).orElseGet(() -> $T.validNel($T.empty()))",
        prism,
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
  private CodeBlock parseLeg(Correspondence c, CodeBlock read, boolean guard) {
    ClassName optional = ClassName.get("java.util", "Optional");
    return switch (c.kind()) {
      case LEAF ->
          CodeBlock.of("\n.field($S, hkj$$ifPresent($L, $L::parse))", c.name(), read, c.prism());
      case LIST ->
          CodeBlock.of("\n.field($S, hkj$$ifPresent($L, $L::parseAll))", c.name(), read, c.prism());
      case OPTIONAL ->
          CodeBlock.of(
              "\n.field($S, hkj$$ifPresent($L, $L))",
              c.name(),
              read,
              elementOfOptionalParser(c.prism()));
      case MAP ->
          CodeBlock.of(
              "\n.field($S, hkj$$ifPresent($L, $L::parseValues))", c.name(), read, c.prism());
      case IDENTITY ->
          guard
              ? CodeBlock.of(
                  "\n.field($S, hkj$$ifPresent($L, $T::validNel))", c.name(), read, VALIDATED)
              : CodeBlock.of("\n.field($S, $T.validNel($L))", c.name(), VALIDATED, read);
      // Identity containers copy by reference, but a null element/value is a located invalid
      // at its index/key - the same doctrine the lifted legs enforce via parseAll/parseValues.
      case IDENTITY_LIST -> CodeBlock.of("\n.field($S, hkj$$allPresent($L))", c.name(), read);
      case IDENTITY_MAP -> CodeBlock.of("\n.field($S, hkj$$valuesPresent($L))", c.name(), read);
      // A nullable bean read bridges to the domain Optional: null becomes Optional.empty, so
      // it is never guarded and never fails on absence.
      case OPTIONAL_BRIDGE ->
          c.prism() == null
              ? CodeBlock.of(
                  "\n.field($S, $T.validNel($T.ofNullable($L)))",
                  c.name(),
                  VALIDATED,
                  optional,
                  read)
              : CodeBlock.of(
                  "\n.field($S, $T.ofNullable($L).map(v -> $L.parse(v).map($T::of)).orElseGet("
                      + "() -> $T.validNel($T.empty())))",
                  c.name(),
                  optional,
                  read,
                  c.prism(),
                  optional,
                  VALIDATED,
                  optional);
      // A derived component carries no domain data; parse reconstructs without it.
      case DERIVED -> CodeBlock.of("");
    };
  }

  /**
   * Emits the validated patch tier: a projection whose wire carries fallible correspondences.
   * {@code build} stays total; the write-back is {@code patch(domain, wire)} returning {@code
   * Validated<NonEmptyList<FieldError>, Domain>} — every projected component validates (a null
   * reference read becomes a located {@code FieldError}, matching the bean-parse convention),
   * unprojected components are read from the domain argument, and all failures accumulate. Dense
   * semantics: every projected component applies; contrast {@code UpdateSpec}'s sparse
   * null-as-absent {@code updateFrom}.
   */
  private void writePatchImpl(
      TypeElement spec,
      TypeElement domain,
      DeclaredType domainDeclared,
      WireShape.RecordShape wire,
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
        "a leaf-carrying projection",
        reserveFactoryIfGeneric(
            spec,
            List.of(
                EmittedMember.of("build", domainDeclared),
                EmittedMember.of("patch", domainDeclared, wireUsed))))) {
      return;
    }

    CodeBlock buildBody = wire.buildStatements(wireName, wc -> buildValue(wc, comps));

    List<CodeBlock> patchLegs = new ArrayList<>();
    for (Correspondence c : comps) {
      // A JSON-bound record leaves an absent component null, exactly like an unset bean
      // property, so every reference read is guarded into a located FieldError (the locked
      // null policy, the same guardedRead the full tier uses); a primitive
      // read can never be null and copies directly.
      patchLegs.add(parseLeg(c, wireRead(wire, c.wireName()), guardedRead(c, wire)));
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
                    + " {@code patch} write-back. No {@code parse} is emitted — the dropped"
                    + " components cannot be reconstructed (truthful types).\n",
                leafFields(spec))
            .addMethod(buildMethod(domainName, wireName, buildBody))
            .addMethod(
                MethodSpec.methodBuilder("patch")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(patchReturn)
                    .addParameter(domainName, "domain")
                    .addParameter(wireName, "wire")
                    .addJavadoc(
                        "Writes the wire's projected components onto {@code domain}, validating"
                            + " each one; every bad field is reported at once, located under its"
                            + " component name, and unprojected components stay untouched. Dense:"
                            + " every projected component applies, and a {@code null} reference"
                            + " read is a located error, never absence — contrast {@code"
                            + " UpdateSpec}'s sparse {@code updateFrom}. Nulls locate through"
                            + " nesting too: a nested wire value delegates to the nested spec's"
                            + " parse, whose reference legs carry the same guard.\n")
                    .addStatement(
                        "$T.requireNonNull(domain, $S)", OBJECTS, "domain must not be null")
                    .addStatement("$T.requireNonNull(wire, $S)", OBJECTS, "wire must not be null")
                    .addCode(patchBody)
                    .build());
    addRenameStubs(implBuilder, spec);
    // A patch tier always carries at least one fallible correspondence, which is always a
    // reference read, so the guard helper is always needed.
    implBuilder.addMethod(ifPresentHelper());
    if (comps.stream().anyMatch(c -> c.kind() == Kind.IDENTITY_LIST)) {
      implBuilder.addMethod(allPresentHelper());
    }
    if (comps.stream().anyMatch(c -> c.kind() == Kind.IDENTITY_MAP)) {
      implBuilder.addMethod(valuesPresentHelper());
    }
    writeFile(spec, specName.packageName(), implBuilder.build());
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

    CodeBlock buildBody =
        switch (wire) {
          case WireShape.RecordShape r -> r.buildStatements(wireName, wc -> buildValue(wc, comps));
          case WireShape.BeanShape b -> beanBuildBody(b, wireName, comps);
        };

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
            .addMethod(buildMethod(domainName, wireName, buildBody))
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
    addRenameStubs(implBuilder, spec);
    writeFile(spec, specName.packageName(), implBuilder.build());
  }

  /** One dispatch arm of a sealed mapping: a domain subtype, its wire subtype, and the impl. */
  private record SealedPair(TypeMirror domain, TypeMirror wire, ClassName impl) {}

  private void processSealedSpec(
      TypeElement spec, List<RegisteredSpec> registry, TypeElement domain, TypeElement wire) {
    List<? extends TypeMirror> wirePermitted = wire.getPermittedSubclasses();
    List<SealedPair> pairs = new ArrayList<>();
    for (TypeMirror domainSubtype : domain.getPermittedSubclasses()) {
      List<RegisteredSpec> candidates =
          registry.stream()
              .filter(RegisteredSpec::parseCapable)
              .filter(r -> processingEnv.getTypeUtils().isSameType(r.domain(), domainSubtype))
              .filter(
                  r ->
                      wirePermitted.stream()
                          .anyMatch(w -> processingEnv.getTypeUtils().isSameType(r.wire(), w)))
              .toList();
      if (candidates.isEmpty()) {
        String projectionHint =
            registry.stream()
                .filter(r -> !r.parseCapable())
                .filter(r -> processingEnv.getTypeUtils().isSameType(r.domain(), domainSubtype))
                .filter(
                    r ->
                        wirePermitted.stream()
                            .anyMatch(w -> processingEnv.getTypeUtils().isSameType(r.wire(), w)))
                .findFirst()
                .map(
                    r ->
                        " '"
                            + r.spec().getSimpleName()
                            + "' maps it but is a projection (no parse), so it cannot take part"
                            + " in dispatch.")
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
            "Declare a @GenerateMapping spec for '" + domainSubtype + "' in the same compilation.");
        return;
      }
      if (candidates.size() > 1) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "permitted subtype '"
                + domainSubtype
                + "' of '"
                + domain.getSimpleName()
                + "' matches more than one mapping spec: "
                + candidates.stream().map(r -> r.spec().getSimpleName().toString()).toList()
                + ".",
            "With several specs for one subtype, the dispatch choice would be arbitrary.",
            "Keep exactly one spec per subtype pair.");
        return;
      }
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
    MethodSpec.Builder constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE);
    MethodSpec.Builder factory =
        MethodSpec.methodBuilder("of")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addTypeVariables(variables)
            .returns(typed)
            .addJavadoc(
                "Creates the element-mapped mapping: each abstract leaf arrives as its {@code"
                    + " ValidatedPrism}, in declaration order.\n");
    StringJoiner arguments = new StringJoiner(", ");
    for (LeafField leaf : abstractLeaves) {
      String name = leaf.name();
      TypeName prismType = leaf.prismType();
      builder.addField(
          FieldSpec.builder(prismType, name, Modifier.PRIVATE, Modifier.FINAL).build());
      constructor
          .addParameter(prismType, name)
          .addStatement(
              "this.$1L = $2T.requireNonNull($1L, $3S)", name, OBJECTS, name + " must not be null");
      factory.addParameter(prismType, name);
      arguments.add(name);
      builder.addMethod(
          MethodSpec.methodBuilder(name)
              .addAnnotation(Override.class)
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
   * The {@code build} method. {@code body} is the complete, terminated build statement(s): a record
   * wire supplies them via {@link WireShape.RecordShape#buildStatements}, a bean wire via {@link
   * #beanBuildBody}, and the sealed path via a terminated {@code return switch} — so all three are
   * emitted verbatim with {@code addCode}.
   */
  private static MethodSpec buildMethod(TypeName domainName, TypeName wireName, CodeBlock body) {
    return MethodSpec.methodBuilder("build")
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

  private void addRenameStubs(TypeSpec.Builder implBuilder, TypeElement spec) {
    // Only abstract zero-parameter @MapField methods survive validateSpecMethods. Unrelated
    // mix-ins agreeing on a rename contribute one stub, whose return has to be
    // return-type-substitutable for every declaration (JLS 8.4.8.3): the subtype-narrowest of
    // the group, which checkGroupsHaveNarrowestReturns has verified exists. A name an abstract
    // leaf shares gets no stub at all: the leaf accessor elementMappedSkeleton emits already
    // implements the member, and the group guard has proven its return satisfies the rename
    // declaration too; the rename's to-mapping is read from collectRenames either way.
    Set<String> leafNames =
        abstractLeaves(spec).stream()
            .map(leaf -> leaf.getSimpleName().toString())
            .collect(Collectors.toSet());
    Map<String, List<ExecutableElement>> renames = new LinkedHashMap<>();
    for (ExecutableElement method : specMembers(spec)) {
      if (method.getAnnotation(MapField.class) != null
          && !leafNames.contains(method.getSimpleName().toString())) {
        renames
            .computeIfAbsent(method.getSimpleName().toString(), name -> new ArrayList<>())
            .add(method);
      }
    }
    for (Map.Entry<String, List<ExecutableElement>> rename : renames.entrySet()) {
      TypeMirror narrowest = memberTypeIn(spec, narrowestMember(spec, rename.getValue()));
      implBuilder.addMethod(
          MethodSpec.methodBuilder(rename.getKey())
              .addAnnotation(Override.class)
              .addModifiers(Modifier.PUBLIC)
              .returns(ProcessorUtils.typeNameOf(narrowest))
              .addJavadoc("Rename declaration only; not invocable.\n")
              .addStatement(
                  "throw new $T($S)",
                  UnsupportedOperationException.class,
                  "@MapField methods declare renames and are not invocable")
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
      // An unresolved parameter type matches everything under javac's isSameType; treat it as no
      // collision, so the real cannot-find-symbol diagnostic is not shadowed by a spurious one.
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
      params.add(simpleTypeName(parameter));
    }
    return method.getSimpleName() + params.toString();
  }

  /**
   * The compact display name of a parameter type: the element's simple name for declared types, so
   * packages, type arguments and type-use annotations never clutter the diagnostic; a type variable
   * renders as its own name.
   */
  private static String simpleTypeName(TypeMirror type) {
    return type instanceof DeclaredType declared
        ? declared.asElement().getSimpleName().toString()
        : type.toString();
  }

  void writeFile(TypeElement spec, String packageName, TypeSpec impl) {
    try {
      JavaFile.builder(packageName, impl)
          .addFileComment("Generated by hkj-processor. Do not edit.")
          .build()
          .writeTo(processingEnv.getFiler());
    } catch (FilerException e) {
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
    } catch (IOException e) {
      writeFailure(spec, e);
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

  private static DeclaredType findMappingSpec(TypeElement spec) {
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
  private static DeclaredType findUpdateSpec(TypeElement spec) {
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

  /** Whether every wire component is a primitive (so no read can be null). */
  private static boolean allPrimitive(WireShape wire) {
    return wire.components().stream().allMatch(c -> c.type().getKind().isPrimitive());
  }

  /**
   * A bean projection with a reference property maps as a validated patch rather than a lawful lens
   * (a null read cannot be written back through a total {@code set}); that tier is a follow-up.
   */
  private void reportBeanProjectionDeferred(TypeElement spec, TypeElement domain, WireShape wire) {
    Diagnostics.error(
        processingEnv.getMessager(),
        spec,
        TAG,
        "'"
            + wire.element().getSimpleName()
            + "' is a bean projection of '"
            + domain.getSimpleName()
            + "' with a reference-typed property, which is not yet supported.",
        "A projection maps as build() plus a lawful asLens() write-back, but a bean's reference"
            + " property can read null, which a total lens set cannot honour. Record projections"
            + " map that shape as a validated patch(domain, wire); the bean flavour is a"
            + " follow-up to the bean mapper. An all-primitive bean projection (no null possible)"
            + " is supported today.",
        "Use a record wire for the projection (which supports the validated patch tier), or map"
            + " the full bean by adding the dropped domain components to it.");
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

  private static boolean hasWildcardArgument(DeclaredType map) {
    return map.getTypeArguments().stream().anyMatch(t -> t.getKind() == TypeKind.WILDCARD);
  }

  private static List<String> wireNames(List<? extends RecordComponentElement> comps) {
    return comps.stream().map(c -> c.getSimpleName().toString()).toList();
  }
}
