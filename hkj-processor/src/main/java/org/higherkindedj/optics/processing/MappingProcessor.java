// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.google.auto.service.AutoService;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.higherkindedj.optics.annotations.ArityCeilings;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.processing.util.Diagnostics;

/**
 * Annotation processor for {@code @GenerateMapping} — the Step-0 slice of the bidirectional
 * record↔DTO mapper (issue #600).
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
 * plus a lawful {@code asLens()} write-back, and no {@code parse} (truthful types). Sealed
 * interface pairs dispatch {@code build}/{@code parse} over their permitted subtype pairs, each
 * delegating to its own spec.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateMapping")
public class MappingProcessor extends AbstractProcessor {

  private static final String TAG = "@GenerateMapping";
  private static final String MAPPING_SPEC = "org.higherkindedj.optics.annotations.MappingSpec";
  private static final String VALIDATED_PRISM = "org.higherkindedj.optics.validated.ValidatedPrism";
  private static final String GETTER = "org.higherkindedj.optics.Getter";
  private static final ClassName VALIDATED_PRISM_TYPE =
      ClassName.get("org.higherkindedj.optics.validated", "ValidatedPrism");
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
              : beanPair ? beanAnalyser.propertyCount(wireBean) : 0;
      boolean parseCapable =
          sealedPair
              || domainRecord.getRecordComponents().size()
                  == wireCount - derivedCandidateCount(spec);
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
   * A spec's abstract methods must all be zero-parameter {@code @MapField} renames — anything else
   * would leave the generated Impl with an unimplemented member (or a meaningless rename on a
   * sealed mapping, which has no components).
   */
  private boolean validateSpecMethods(TypeElement spec, boolean sealedPair) {
    for (ExecutableElement method : ElementFilter.methodsIn(spec.getEnclosedElements())) {
      MapField mapField = method.getAnnotation(MapField.class);
      if (!method.getModifiers().contains(Modifier.ABSTRACT)) {
        if (mapField != null) {
          Diagnostics.error(
              processingEnv.getMessager(),
              method,
              TAG,
              "@MapField method '" + method.getSimpleName() + "' must be abstract.",
              "A rename is a marker method the generated Impl stubs out; a method with a body"
                  + " (default, static or private) would double as callable code.",
              "Remove the body, or remove the @MapField annotation.");
          return false;
        }
        continue;
      }
      if (mapField == null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            method,
            TAG,
            "abstract method '" + method.getSimpleName() + "' is neither a rename nor a leaf.",
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
    }
    return true;
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
        "Map concrete record types, or wait for the full mapper's generics support.");
    return false;
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

    if (spec.getInterfaces().size() != 1) {
      Diagnostics.error(
          processingEnv.getMessager(),
          element,
          TAG,
          "'" + spec.getSimpleName() + "' extends interfaces besides MappingSpec.",
          "Renames and leaves declared on other supertypes are invisible to this slice's"
              + " classification, so the generated Impl could silently miss inherited members.",
          "Declare every rename and leaf directly on the spec; spec inheritance arrives with the"
              + " full mapper.");
      return;
    }

    TypeElement sealedDomain = asSealed(specSuper.getTypeArguments().get(0));
    TypeElement sealedWire = asSealed(specSuper.getTypeArguments().get(1));
    if (!validateSpecMethods(spec, sealedDomain != null && sealedWire != null)) {
      return;
    }
    if (sealedDomain != null && sealedWire != null) {
      if (!checkNotGeneric(spec, sealedDomain, sealedWire)) {
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

    // The wire may be a record (component-wise) or a bean-shaped class (getters/setters).
    TypeElement wireRecord = asRecord(wireArg);
    WireShape wireShape;
    if (wireRecord != null) {
      if (!checkNotGeneric(spec, domain, wireRecord)) {
        return;
      }
      wireShape = recordWireShape(wireRecord);
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
    }

    Map<String, String> renames = collectRenames(spec, domain, wireShape);
    if (renames == null) {
      return;
    }

    List<DerivedField> derived = collectDerived(spec, domain, wireShape, renames);
    if (derived == null) {
      return;
    }

    if (wireShape.componentCount() - derived.size() < domain.getRecordComponents().size()) {
      if (!derived.isEmpty()) {
        reportProjectionWithDerived(spec, domain, wireShape, derived);
        return;
      }
      List<Correspondence> projection = classifyProjection(spec, domain, wireShape, renames);
      if (projection == null) {
        return;
      }
      writeLensImpl(spec, domain, wireShape, projection);
      return;
    }

    List<Correspondence> correspondences =
        classify(spec, registry, domain, wireShape, renames, derived);
    if (correspondences == null) {
      return;
    }
    writeImpl(spec, domain, wireShape, correspondences);
  }

  /**
   * Wraps a record wire in a {@link WireShape}: accessor is the component name, positional build.
   */
  private static WireShape recordWireShape(TypeElement wire) {
    List<WireShape.WireComponent> components =
        wire.getRecordComponents().stream()
            .map(
                c ->
                    new WireShape.WireComponent(
                        c.getSimpleName().toString(), c.asType(), c.getSimpleName().toString()))
            .toList();
    return new WireShape.RecordShape(wire, components);
  }

  private enum Kind {
    IDENTITY,
    LEAF,
    LIST,
    OPTIONAL,
    MAP,
    DERIVED
  }

  /**
   * {@code prism} is an expression yielding the ValidatedPrism for every non-identity kind, except
   * {@code DERIVED}, where it yields the spec's Getter accessor instead.
   */
  private record Correspondence(String name, String wireName, Kind kind, CodeBlock prism) {
    boolean fallible() {
      return kind != Kind.IDENTITY;
    }
  }

  private record PrismResolution(CodeBlock accessor, boolean ambiguous) {
    static final PrismResolution NONE = new PrismResolution(null, false);
  }

  /**
   * Resolves the ValidatedPrism carrying a (wireType -> domainType) correspondence through a single
   * same-round mapping spec for the pair (via its generated impl's {@code asValidatedPrism()}).
   * Explicit leaves are matched by {@link #classify} before identity classification ever runs —
   * whole-component leaves directly, container ELEMENT/VALUE leaves through {@link
   * #containerLeafCorrespondence} — so by the time a nested spec is consulted no leaf exists for
   * the pair. More than one candidate spec is reported as an error.
   */
  private PrismResolution resolveNestedSpec(
      TypeElement spec,
      List<RegisteredSpec> registry,
      String name,
      TypeMirror wireType,
      TypeMirror domainType) {
    List<RegisteredSpec> nested =
        registry.stream()
            .filter(RegisteredSpec::parseCapable)
            .filter(
                r ->
                    processingEnv.getTypeUtils().isSameType(r.domain(), domainType)
                        && processingEnv.getTypeUtils().isSameType(r.wire(), wireType))
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
      return new PrismResolution(
          CodeBlock.of("$T.INSTANCE.asValidatedPrism()", nested.getFirst().impl()), false);
    }
    return PrismResolution.NONE;
  }

  private Map<String, String> collectRenames(TypeElement spec, TypeElement domain, WireShape wire) {
    Map<String, String> renames = new LinkedHashMap<>();
    for (ExecutableElement method : ElementFilter.methodsIn(spec.getEnclosedElements())) {
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
    }
    return renames;
  }

  /** A derived wire field: a spec default method named after a wire-only component. */
  private record DerivedField(String wireName) {}

  /** True for a zero-parameter {@code default} method returning {@code Getter} (any type args). */
  private static boolean isDerivedCandidate(ExecutableElement method) {
    return method.isDefault()
        && method.getParameters().isEmpty()
        && method.getReturnType() instanceof DeclaredType returnType
        && ((TypeElement) returnType.asElement()).getQualifiedName().contentEquals(GETTER);
  }

  /** Counts derived candidates for the registry's parse-capability arithmetic. */
  private static long derivedCandidateCount(TypeElement spec) {
    return ElementFilter.methodsIn(spec.getEnclosedElements()).stream()
        .filter(MappingProcessor::isDerivedCandidate)
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
      TypeElement spec, TypeElement domain, WireShape wire, Map<String, String> renames) {
    List<DerivedField> derived = new ArrayList<>();
    for (ExecutableElement method : ElementFilter.methodsIn(spec.getEnclosedElements())) {
      if (!isDerivedCandidate(method)) {
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
      DeclaredType returnType = (DeclaredType) method.getReturnType();
      boolean shapeMatches =
          returnType.getTypeArguments().size() == 2
              && processingEnv
                  .getTypeUtils()
                  .isSameType(returnType.getTypeArguments().getFirst(), domain.asType())
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
                + method.getReturnType()
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
            + "', which is the projection shape (Lens tier). A projection's asLens() writes wire"
            + " values straight back into the domain, but build recomputes a derived component,"
            + " so the write-back could never honour the value being set (an unlawful lens).",
        "Remove the derived methods and map the smaller wire as a plain projection, or add wire"
            + " components until every domain component keeps a counterpart.");
  }

  private List<Correspondence> classify(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
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

    if (domain.getRecordComponents().size() > ArityCeilings.ASSEMBLY) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'"
              + domain.getSimpleName()
              + "' has "
              + domain.getRecordComponents().size()
              + " components; the accumulating parse supports at most "
              + ArityCeilings.ASSEMBLY
              + ".",
          "parse is assembled with Validated.fields(), which locates up to "
              + ArityCeilings.ASSEMBLY
              + " fields.",
          "Group related components into nested records (each pair nests through its own spec),"
              + " or map the record by hand.");
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
      // An explicit leaf always wins — even over a same-typed identity match, so a
      // ValidatedPrism<X, X> can validate or normalise a component the types alone would copy.
      ExecutableElement directLeaf =
          findLeaf(spec, name, wireComponent.type(), domainComponent.asType());
      if (directLeaf != null) {
        result.add(
            new Correspondence(
                name, wireName, Kind.LEAF, CodeBlock.of("$L()", directLeaf.getSimpleName())));
        continue;
      }
      // The same rule lifted through containers: an ELEMENT/VALUE-typed leaf on a List, Optional
      // or Map component beats the identity copy the container types alone would take. Only
      // explicit leaves pre-empt identity; nested-spec resolution never does (the scalar
      // precedent).
      Correspondence containerLeaf =
          containerLeafCorrespondence(
              spec, name, wireName, wireComponent.type(), domainComponent.asType());
      if (containerLeaf != null) {
        result.add(containerLeaf);
        continue;
      }
      if (processingEnv.getTypeUtils().isSameType(domainComponent.asType(), wireComponent.type())) {
        result.add(new Correspondence(name, wireName, Kind.IDENTITY, null));
        continue;
      }
      TypeMirror wireElement = containerElement(wireComponent.type(), "java.util.List");
      TypeMirror domainElement = containerElement(domainComponent.asType(), "java.util.List");
      if (wireElement != null && domainElement != null) {
        PrismResolution lifted =
            resolveNestedSpec(spec, registry, name, wireElement, domainElement);
        if (lifted.ambiguous()) {
          return null;
        }
        if (lifted.accessor() != null) {
          result.add(new Correspondence(name, wireName, Kind.LIST, lifted.accessor()));
          continue;
        }
      }
      wireElement = containerElement(wireComponent.type(), "java.util.Optional");
      domainElement = containerElement(domainComponent.asType(), "java.util.Optional");
      if (wireElement != null && domainElement != null) {
        PrismResolution lifted =
            resolveNestedSpec(spec, registry, name, wireElement, domainElement);
        if (lifted.ambiguous()) {
          return null;
        }
        if (lifted.accessor() != null) {
          result.add(new Correspondence(name, wireName, Kind.OPTIONAL, lifted.accessor()));
          continue;
        }
      }
      DeclaredType wireMapType = asMapType(wireComponent.type());
      DeclaredType domainMapType = asMapType(domainComponent.asType());
      if (wireMapType != null && domainMapType != null) {
        if (wireMapType.getTypeArguments().isEmpty()
            || domainMapType.getTypeArguments().isEmpty()) {
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
          result.add(new Correspondence(name, wireName, Kind.MAP, lifted.accessor()));
          continue;
        }
        // Values resolving to nothing fall through to the no-usable-source error, like List
        // elements.
      }
      PrismResolution direct =
          resolveNestedSpec(spec, registry, name, wireComponent.type(), domainComponent.asType());
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
                + wireComponent.type()
                + " vs "
                + domainComponent.asType()
                + ") and no matching leaf method was found."
                + leafNearMissHint(spec, name)
                + projectionSpecHint(registry, wireComponent.type(), domainComponent.asType())
                + " Found on "
                + domain.getSimpleName()
                + ": "
                + domainNames
                + ".",
            "Add 'default ValidatedPrism<"
                + wireComponent.type()
                + ", "
                + domainComponent.asType()
                + "> "
                + name
                + "()' to the spec, or declare a @GenerateMapping spec mapping those records in"
                + " the same compilation.");
        return null;
      }
      result.add(new Correspondence(name, wireName, Kind.LEAF, direct.accessor()));
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
   * Classifies the Lens tier: the wire record is smaller, so it maps as a lossy projection. Every
   * wire component must identity-match a domain component (a fallible leaf would make the
   * write-back partial).
   */
  private List<Correspondence> classifyProjection(
      TypeElement spec, TypeElement domain, WireShape wire, Map<String, String> renames) {
    Map<String, String> domainByWire = new LinkedHashMap<>();
    renames.forEach((domainName, wireName) -> domainByWire.put(wireName, domainName));
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
                + "', so it maps as a projection (Lens tier): every wire component must name a"
                + " domain component. Found on "
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
            "The projection's asLens() writes each wire component back to its own domain"
                + " component; a shared source would discard one wire value on write-back (an"
                + " unlawful lens).",
            "Point the @MapField rename at a different domain component, or drop one wire"
                + " component.");
        return null;
      }
      if (!processingEnv
          .getTypeUtils()
          .isSameType(domainComponent.asType(), wireComponent.type())) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "projection field '"
                + wire.element().getSimpleName()
                + "."
                + wireName
                + "' changes type ("
                + domainComponent.asType()
                + " -> "
                + wireComponent.type()
                + ").",
            "A projection (Lens tier) writes wire values straight back into the domain, so the"
                + " types must match exactly; a leaf would make the write-back fallible.",
            "Align the types, or wait for the full mapper's validated patch.");
        return null;
      }
      result.add(new Correspondence(name, wireName, Kind.IDENTITY, null));
    }
    return result;
  }

  /**
   * The container analogue of the whole-component leaf check, applied BEFORE the identity
   * short-circuit: an explicit ELEMENT/VALUE-typed leaf on a {@code List}, {@code Optional} or
   * {@code Map} component wins even when both sides declare the same container type, so a
   * normalising {@code ValidatedPrism<X, X>} still runs. For {@code Map} the key types must already
   * match (keys are identity-only); mismatches fall through to the post-identity diagnostics.
   * Returns null when no such leaf exists.
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
    DeclaredType wireMap = asMapType(wireType);
    DeclaredType domainMap = asMapType(domainType);
    if (wireMap != null
        && domainMap != null
        && wireMap.getTypeArguments().size() == 2
        && domainMap.getTypeArguments().size() == 2
        && processingEnv
            .getTypeUtils()
            .isSameType(
                wireMap.getTypeArguments().getFirst(), domainMap.getTypeArguments().getFirst())) {
      return elementLeafCorrespondence(
          spec,
          name,
          wireName,
          Kind.MAP,
          wireMap.getTypeArguments().get(1),
          domainMap.getTypeArguments().get(1));
    }
    return null;
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
    for (ExecutableElement method : ElementFilter.methodsIn(spec.getEnclosedElements())) {
      if (!method.getSimpleName().contentEquals(name)
          || !method.isDefault()
          || !method.getParameters().isEmpty()) {
        continue;
      }
      if (!(method.getReturnType() instanceof DeclaredType returnType)) {
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
    for (ExecutableElement method : ElementFilter.methodsIn(spec.getEnclosedElements())) {
      if (method.getSimpleName().contentEquals(name) && method.isDefault()) {
        return " A default method '"
            + name
            + "()' exists but returns '"
            + method.getReturnType()
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
      case MAP -> CodeBlock.of("$L.buildValues(domain.$L())", c.prism(), c.name());
      case IDENTITY -> CodeBlock.of("domain.$L()", c.name());
      case DERIVED -> CodeBlock.of("$L.get(domain)", c.prism());
    };
  }

  /**
   * The read expression for the wire component named {@code wireName}, from the {@code wire} var.
   */
  private static CodeBlock wireRead(WireShape wire, String wireName) {
    return wire.componentNamed(wireName).orElseThrow().readFrom("wire");
  }

  /**
   * Whether a component's parse read must be null-guarded: only bean properties can be null, and
   * only reference-typed ones (a primitive identity read cannot be null, and a derived field is not
   * read at all).
   */
  private static boolean beanGuard(Correspondence c, WireShape wire) {
    if (!(wire instanceof WireShape.BeanShape) || c.kind() == Kind.DERIVED) {
      return false;
    }
    if (c.kind() == Kind.IDENTITY) {
      return !wire.componentNamed(c.wireName()).orElseThrow().type().getKind().isPrimitive();
    }
    return true;
  }

  /**
   * The {@code ifPresent} guard emitted into bean-wire impls: a null property read becomes a
   * located {@code FieldError} (the {@code fields()} ladder attaches the component label), so a
   * null never reaches a leaf's {@code parse}, which rejects it.
   */
  private static MethodSpec ifPresentHelper() {
    TypeVariableName s = TypeVariableName.get("S");
    TypeVariableName a = TypeVariableName.get("A");
    TypeName validatedOfA =
        ParameterizedTypeName.get(VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), a);
    TypeName parseFn =
        ParameterizedTypeName.get(
            ClassName.get("java.util.function", "Function"),
            WildcardTypeName.supertypeOf(s),
            validatedOfA);
    return MethodSpec.methodBuilder("ifPresent")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .addTypeVariable(s)
        .addTypeVariable(a)
        .returns(validatedOfA)
        .addParameter(s, "value")
        .addParameter(parseFn, "parse")
        .addJavadoc(
            "Guards a nullable bean-property read: a null becomes a located {@code FieldError},"
                + " otherwise the value is parsed.\n")
        .addStatement(
            "return value == null ? $T.invalidNel($T.of($S)) : parse.apply(value)",
            VALIDATED,
            FIELD_ERROR,
            "must not be null")
        .build();
  }

  private void writeImpl(
      TypeElement spec, TypeElement domain, WireShape wire, List<Correspondence> comps) {
    ClassName specName = ClassName.get(spec);
    ClassName implName = implClassName(spec);
    TypeName domainName = TypeName.get(domain.asType());
    TypeName wireName = TypeName.get(wire.element().asType());
    TypeName parseReturn =
        ParameterizedTypeName.get(
            VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), domainName);

    CodeBlock buildBody = wire.buildStatements(wireName, wc -> buildValue(wc, comps));

    ClassName optional = ClassName.get("java.util", "Optional");
    CodeBlock.Builder parseChain = CodeBlock.builder().add("return $T.fields()", VALIDATED);
    for (Correspondence c : comps) {
      CodeBlock read = wireRead(wire, c.wireName());
      // A bean property is null when unset, so its read is guarded before it reaches a leaf (whose
      // parse rejects null) or the identity copy; the guard locates the null under the field label.
      boolean guard = beanGuard(c, wire);
      parseChain.add(
          switch (c.kind()) {
            case LEAF ->
                guard
                    ? CodeBlock.of(
                        "\n.field($S, ifPresent($L, $L::parse))", c.name(), read, c.prism())
                    : CodeBlock.of("\n.field($S, $L.parse($L))", c.name(), c.prism(), read);
            case LIST ->
                guard
                    ? CodeBlock.of(
                        "\n.field($S, ifPresent($L, $L::parseAll))", c.name(), read, c.prism())
                    : CodeBlock.of("\n.field($S, $L.parseAll($L))", c.name(), c.prism(), read);
            case OPTIONAL ->
                guard
                    ? CodeBlock.of(
                        "\n.field($S, ifPresent($L, o -> o.map(v ->"
                            + " $L.parse(v).map($T::of)).orElseGet(() ->"
                            + " $T.validNel($T.empty()))))",
                        c.name(),
                        read,
                        c.prism(),
                        optional,
                        VALIDATED,
                        optional)
                    : CodeBlock.of(
                        "\n.field($S, $L.map(v -> $L.parse(v).map($T::of)).orElseGet(() ->"
                            + " $T.validNel($T.empty())))",
                        c.name(),
                        read,
                        c.prism(),
                        optional,
                        VALIDATED,
                        optional);
            case MAP ->
                guard
                    ? CodeBlock.of(
                        "\n.field($S, ifPresent($L, $L::parseValues))", c.name(), read, c.prism())
                    : CodeBlock.of("\n.field($S, $L.parseValues($L))", c.name(), c.prism(), read);
            case IDENTITY ->
                guard
                    ? CodeBlock.of(
                        "\n.field($S, ifPresent($L, $T::validNel))", c.name(), read, VALIDATED)
                    : CodeBlock.of("\n.field($S, $T.validNel($L))", c.name(), VALIDATED, read);
            // A derived component carries no domain data; parse reconstructs without it.
            case DERIVED -> CodeBlock.of("");
          });
    }
    parseChain.add("\n.apply($T::new)", domainName);

    // Derived fields are non-identity, so they exclude the Iso tier too: wire -> domain -> wire
    // recomputes the derived component, an identity only for wire values already consistent. A
    // bean's null-guarded reference reads are fallible too, so only an all-primitive bean stays
    // lossless (its reads cannot be null).
    boolean lossless = comps.stream().noneMatch(c -> c.fallible() || beanGuard(c, wire));
    boolean needsGuardHelper = comps.stream().anyMatch(c -> beanGuard(c, wire));
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
                    + " accumulating, located {@code parse}.\n")
            .addMethod(buildMethod(domainName, wireName, buildBody))
            .addMethod(
                MethodSpec.methodBuilder("parse")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(parseReturn)
                    .addParameter(wireName, "wire")
                    .addStatement("$T.requireNonNull(wire, $S)", OBJECTS, "wire must not be null")
                    .addStatement("$L", parseChain.build())
                    .build())
            .addMethod(asValidatedPrismMethod(wireName, domainName));

    addRenameStubs(implBuilder, spec);

    if (needsGuardHelper) {
      implBuilder.addMethod(ifPresentHelper());
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

  private void writeLensImpl(
      TypeElement spec, TypeElement domain, WireShape wire, List<Correspondence> comps) {
    ClassName specName = ClassName.get(spec);
    TypeName domainName = TypeName.get(domain.asType());
    TypeName wireName = TypeName.get(wire.element().asType());

    CodeBlock buildBody =
        wire.buildStatements(
            wireName,
            wc ->
                CodeBlock.of(
                    "domain.$L()",
                    comps.stream()
                        .filter(x -> x.wireName().equals(wc.name()))
                        .findFirst()
                        .orElseThrow()
                        .name()));

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
                    + " components cannot be reconstructed (truthful types).\n")
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
                    + " own mapping.\n")
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
      TypeElement spec, ClassName implName, ClassName specName, String javadoc) {
    return TypeSpec.classBuilder(implName)
        .addOriginatingElement(spec)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addAnnotation(GENERATED)
        .addSuperinterface(specName)
        .addJavadoc(javadoc, specName)
        .addField(
            FieldSpec.builder(
                    implName, "INSTANCE", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()", implName)
                .build())
        .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());
  }

  /**
   * The {@code build} method. {@code body} is the complete, terminated build statement(s) — a
   * record or bean wire supplies these via {@link WireShape#buildStatements}, and the sealed path
   * supplies a terminated {@code return switch} — so they are emitted verbatim with {@code
   * addCode}.
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

  private static void addRenameStubs(TypeSpec.Builder implBuilder, TypeElement spec) {
    for (ExecutableElement method : ElementFilter.methodsIn(spec.getEnclosedElements())) {
      // Only abstract zero-parameter @MapField methods survive validateSpecMethods.
      if (method.getAnnotation(MapField.class) == null) {
        continue;
      }
      implBuilder.addMethod(
          MethodSpec.methodBuilder(method.getSimpleName().toString())
              .addAnnotation(Override.class)
              .addModifiers(Modifier.PUBLIC)
              .returns(TypeName.get(method.getReturnType()))
              .addJavadoc("Rename declaration only; not invocable.\n")
              .addStatement(
                  "throw new $T($S)",
                  UnsupportedOperationException.class,
                  "@MapField methods declare renames and are not invocable")
              .build());
    }
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

  /** A concrete (non-abstract, non-record, non-enum) class: a candidate bean-shaped wire (#628). */
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

  private static boolean hasWildcardArgument(DeclaredType map) {
    return map.getTypeArguments().stream().anyMatch(t -> t.getKind() == TypeKind.WILDCARD);
  }

  private static List<String> wireNames(List<? extends RecordComponentElement> comps) {
    return comps.stream().map(c -> c.getSimpleName().toString()).toList();
  }
}
