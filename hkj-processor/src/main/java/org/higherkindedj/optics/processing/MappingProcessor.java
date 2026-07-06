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
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
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
 * wherever a leaf does), or lift through {@code List}/{@code Optional} containers of either.
 * {@code @MapField} declares renames. A wire record with fewer components maps as a lossy
 * projection: {@code build} plus a lawful {@code asLens()} write-back, and no {@code parse}
 * (truthful types). Sealed interface pairs dispatch {@code build}/{@code parse} over their
 * permitted subtype pairs, each delegating to its own spec. Map value lifting arrives with the full
 * mapper.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("org.higherkindedj.optics.annotations.GenerateMapping")
public class MappingProcessor extends AbstractProcessor {

  private static final String TAG = "@GenerateMapping";
  private static final String MAPPING_SPEC = "org.higherkindedj.optics.annotations.MappingSpec";
  private static final String VALIDATED_PRISM = "org.higherkindedj.optics.validated.ValidatedPrism";
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
    Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(GenerateMapping.class);
    List<RegisteredSpec> registry = new ArrayList<>();
    for (Element element : elements) {
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
      boolean recordPair = domainRecord != null && wireRecord != null;
      boolean sealedPair = asSealed(domainArg) != null && asSealed(wireArg) != null;
      if (!recordPair && !sealedPair) {
        continue;
      }
      // Only parse-capable specs may be nested into: equal-count record pairs and sealed pairs.
      // Projections (smaller wire, no parse) register too, so failed lookups can name them.
      boolean parseCapable =
          sealedPair
              || domainRecord.getRecordComponents().size()
                  == wireRecord.getRecordComponents().size();
      registry.add(new RegisteredSpec(domainArg, wireArg, implClassName(spec), spec, parseCapable));
    }
    for (Element element : elements) {
      processSpec(element, registry);
    }
    return true;
  }

  /** A valid spec seen this round; nested components resolve against the parse-capable ones. */
  private record RegisteredSpec(
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

    TypeElement domain = asRecord(specSuper.getTypeArguments().get(0));
    TypeElement wire = asRecord(specSuper.getTypeArguments().get(1));
    if (domain == null || wire == null) {
      Diagnostics.error(
          processingEnv.getMessager(),
          element,
          TAG,
          "the MappingSpec type arguments of '"
              + spec.getSimpleName()
              + "' must both be records, or both sealed interfaces.",
          "Records map component-wise; sealed hierarchies map by dispatching over their permitted"
              + " subtype pairs.",
          "Use two record types, or two sealed interface types.");
      return;
    }

    if (!checkNotGeneric(spec, domain, wire)) {
      return;
    }

    Map<String, String> renames = collectRenames(spec, domain, wire);
    if (renames == null) {
      return;
    }

    if (wire.getRecordComponents().size() < domain.getRecordComponents().size()) {
      List<Correspondence> projection = classifyProjection(spec, domain, wire, renames);
      if (projection == null) {
        return;
      }
      writeLensImpl(spec, domain, wire, projection);
      return;
    }

    List<Correspondence> correspondences = classify(spec, registry, domain, wire, renames);
    if (correspondences == null) {
      return;
    }
    writeImpl(spec, domain, wire, correspondences);
  }

  private enum Kind {
    IDENTITY,
    LEAF,
    LIST,
    OPTIONAL
  }

  /** {@code prism} is an expression yielding the ValidatedPrism for every non-identity kind. */
  private record Correspondence(String name, String wireName, Kind kind, CodeBlock prism) {
    boolean fallible() {
      return kind != Kind.IDENTITY;
    }
  }

  private record PrismResolution(CodeBlock accessor, boolean ambiguous) {
    static final PrismResolution NONE = new PrismResolution(null, false);
  }

  /**
   * Resolves the ValidatedPrism carrying a (wireType -> domainType) correspondence: an explicit
   * leaf method wins, then a single same-round mapping spec for the pair (via its generated impl's
   * {@code asValidatedPrism()}). More than one candidate spec is reported as an error.
   */
  private PrismResolution resolvePrism(
      TypeElement spec,
      List<RegisteredSpec> registry,
      String name,
      TypeMirror wireType,
      TypeMirror domainType) {
    ExecutableElement leaf = findLeaf(spec, name, wireType, domainType);
    if (leaf != null) {
      return new PrismResolution(CodeBlock.of("$L()", leaf.getSimpleName()), false);
    }
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

  private Map<String, String> collectRenames(
      TypeElement spec, TypeElement domain, TypeElement wire) {
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
      boolean onWire =
          wire.getRecordComponents().stream()
              .anyMatch(c -> c.getSimpleName().contentEquals(mapField.to()));
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
                + wire.getSimpleName()
                + ".",
            "Found on " + wire.getSimpleName() + ": " + wireNames(wire.getRecordComponents()) + ".",
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

  private List<Correspondence> classify(
      TypeElement spec,
      List<RegisteredSpec> registry,
      TypeElement domain,
      TypeElement wire,
      Map<String, String> renames) {
    List<Correspondence> result = new ArrayList<>();
    List<? extends RecordComponentElement> wireComponents = wire.getRecordComponents();
    List<String> domainNames =
        domain.getRecordComponents().stream().map(c -> c.getSimpleName().toString()).toList();

    if (wireComponents.size() != domain.getRecordComponents().size()) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'"
              + wire.getSimpleName()
              + "' has more components than '"
              + domain.getSimpleName()
              + "'.",
          "build must fill every wire component from a domain source, and the extras have none. A"
              + " wire with fewer components maps as a projection (Lens tier).",
          "Remove the extra wire components, or add matching domain components.");
      return null;
    }

    if (domain.getRecordComponents().size() > 12) {
      Diagnostics.error(
          processingEnv.getMessager(),
          spec,
          TAG,
          "'"
              + domain.getSimpleName()
              + "' has "
              + domain.getRecordComponents().size()
              + " components; the accumulating parse supports at most 12.",
          "parse is assembled with Validated.fields(), which locates up to 12 fields.",
          "Group related components into nested records (each pair nests through its own spec),"
              + " or map the record by hand.");
      return null;
    }

    Map<String, String> claimedWire = new LinkedHashMap<>();
    for (RecordComponentElement domainComponent : domain.getRecordComponents()) {
      String name = domainComponent.getSimpleName().toString();
      String wireName = renames.getOrDefault(name, name);
      RecordComponentElement wireComponent =
          wireComponents.stream()
              .filter(c -> c.getSimpleName().toString().equals(wireName))
              .findFirst()
              .orElse(null);
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
            "Found on " + wire.getSimpleName() + ": " + wireNames(wireComponents) + ".",
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
          findLeaf(spec, name, wireComponent.asType(), domainComponent.asType());
      if (directLeaf != null) {
        result.add(
            new Correspondence(
                name, wireName, Kind.LEAF, CodeBlock.of("$L()", directLeaf.getSimpleName())));
        continue;
      }
      if (processingEnv
          .getTypeUtils()
          .isSameType(domainComponent.asType(), wireComponent.asType())) {
        result.add(new Correspondence(name, wireName, Kind.IDENTITY, null));
        continue;
      }
      TypeMirror wireElement = containerElement(wireComponent.asType(), "java.util.List");
      TypeMirror domainElement = containerElement(domainComponent.asType(), "java.util.List");
      if (wireElement != null && domainElement != null) {
        PrismResolution lifted = resolvePrism(spec, registry, name, wireElement, domainElement);
        if (lifted.ambiguous()) {
          return null;
        }
        if (lifted.accessor() != null) {
          result.add(new Correspondence(name, wireName, Kind.LIST, lifted.accessor()));
          continue;
        }
      }
      wireElement = containerElement(wireComponent.asType(), "java.util.Optional");
      domainElement = containerElement(domainComponent.asType(), "java.util.Optional");
      if (wireElement != null && domainElement != null) {
        PrismResolution lifted = resolvePrism(spec, registry, name, wireElement, domainElement);
        if (lifted.ambiguous()) {
          return null;
        }
        if (lifted.accessor() != null) {
          result.add(new Correspondence(name, wireName, Kind.OPTIONAL, lifted.accessor()));
          continue;
        }
      }
      if (containerElement(wireComponent.asType(), "java.util.Map") != null
          && containerElement(domainComponent.asType(), "java.util.Map") != null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "field '" + name + "' needs Map value lifting, which this slice does not support.",
            "List and Optional lifting are supported; Map arrives with the full mapper.",
            "Map the field by hand for now, or align the Map value types.");
        return null;
      }
      PrismResolution direct =
          resolvePrism(spec, registry, name, wireComponent.asType(), domainComponent.asType());
      if (direct.ambiguous()) {
        return null;
      }
      if (direct.accessor() == null) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "target field '" + wire.getSimpleName() + "." + wireName + "' has no usable source.",
            "The types differ ("
                + wireComponent.asType()
                + " vs "
                + domainComponent.asType()
                + ") and no matching leaf method was found."
                + leafNearMissHint(spec, name)
                + projectionSpecHint(registry, wireComponent.asType(), domainComponent.asType())
                + " Found on "
                + domain.getSimpleName()
                + ": "
                + domainNames
                + ".",
            "Add 'default ValidatedPrism<"
                + wireComponent.asType()
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
    return result;
  }

  /**
   * Classifies the Lens tier: the wire record is smaller, so it maps as a lossy projection. Every
   * wire component must identity-match a domain component (a fallible leaf would make the
   * write-back partial).
   */
  private List<Correspondence> classifyProjection(
      TypeElement spec, TypeElement domain, TypeElement wire, Map<String, String> renames) {
    Map<String, String> domainByWire = new LinkedHashMap<>();
    renames.forEach((domainName, wireName) -> domainByWire.put(wireName, domainName));
    Set<String> usedDomain = new LinkedHashSet<>();
    List<Correspondence> result = new ArrayList<>();
    for (RecordComponentElement wireComponent : wire.getRecordComponents()) {
      String wireName = wireComponent.getSimpleName().toString();
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
                + wire.getSimpleName()
                + "."
                + wireName
                + "' has no domain source.",
            "'"
                + wire.getSimpleName()
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
          .isSameType(domainComponent.asType(), wireComponent.asType())) {
        Diagnostics.error(
            processingEnv.getMessager(),
            spec,
            TAG,
            "projection field '"
                + wire.getSimpleName()
                + "."
                + wireName
                + "' changes type ("
                + domainComponent.asType()
                + " -> "
                + wireComponent.asType()
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

  private void writeImpl(
      TypeElement spec, TypeElement domain, TypeElement wire, List<Correspondence> comps) {
    ClassName specName = ClassName.get(spec);
    ClassName implName = implClassName(spec);
    TypeName domainName = TypeName.get(domain.asType());
    TypeName wireName = TypeName.get(wire.asType());
    TypeName parseReturn =
        ParameterizedTypeName.get(
            VALIDATED, ParameterizedTypeName.get(NEL, FIELD_ERROR), domainName);

    CodeBlock.Builder buildArgs = CodeBlock.builder();
    boolean first = true;
    for (RecordComponentElement wireComponent : wire.getRecordComponents()) {
      String name = wireComponent.getSimpleName().toString();
      Correspondence c =
          comps.stream().filter(x -> x.wireName().equals(name)).findFirst().orElseThrow();
      if (!first) {
        buildArgs.add(", ");
      }
      first = false;
      buildArgs.add(
          switch (c.kind()) {
            case LEAF -> CodeBlock.of("$L.build(domain.$L())", c.prism(), c.name());
            case LIST -> CodeBlock.of("$L.buildAll(domain.$L())", c.prism(), c.name());
            case OPTIONAL -> CodeBlock.of("domain.$L().map($L::build)", c.name(), c.prism());
            case IDENTITY -> CodeBlock.of("domain.$L()", c.name());
          });
    }

    ClassName optional = ClassName.get("java.util", "Optional");
    CodeBlock.Builder parseChain = CodeBlock.builder().add("return $T.fields()", VALIDATED);
    for (Correspondence c : comps) {
      parseChain.add(
          switch (c.kind()) {
            case LEAF ->
                CodeBlock.of(
                    "\n.field($S, $L.parse(wire.$L()))", c.name(), c.prism(), c.wireName());
            case LIST ->
                CodeBlock.of(
                    "\n.field($S, $L.parseAll(wire.$L()))", c.name(), c.prism(), c.wireName());
            case OPTIONAL ->
                CodeBlock.of(
                    "\n.field($S, wire.$L().map(v -> $L.parse(v).map($T::of)).orElseGet(() ->"
                        + " $T.validNel($T.empty())))",
                    c.name(),
                    c.wireName(),
                    c.prism(),
                    optional,
                    VALIDATED,
                    optional);
            case IDENTITY ->
                CodeBlock.of(
                    "\n.field($S, $T.validNel(wire.$L()))", c.name(), VALIDATED, c.wireName());
          });
    }
    parseChain.add("\n.apply($T::new)", domainName);

    boolean lossless = comps.stream().noneMatch(Correspondence::fallible);
    CodeBlock.Builder reverseArgs = CodeBlock.builder();
    boolean firstReverse = true;
    for (Correspondence c : comps) {
      if (!firstReverse) {
        reverseArgs.add(", ");
      }
      firstReverse = false;
      reverseArgs.add("wire.$L()", c.wireName());
    }

    TypeSpec.Builder implBuilder =
        implSkeleton(
                spec,
                implName,
                specName,
                "Generated bidirectional mapping for {@link $T}: total {@code build} and"
                    + " accumulating, located {@code parse}.\n")
            .addMethod(
                buildMethod(
                    domainName,
                    wireName,
                    CodeBlock.of("return new $T($L)", wireName, buildArgs.build())))
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

    if (lossless) {
      ClassName iso = ClassName.get("org.higherkindedj.optics", "Iso");
      implBuilder.addMethod(
          MethodSpec.methodBuilder("asIso")
              .addModifiers(Modifier.PUBLIC)
              .returns(ParameterizedTypeName.get(iso, domainName, wireName))
              .addJavadoc(
                  "The lossless mapping as an {@link $T}; emitted only when no fallible leaf"
                      + " exists, so the round trip is total (truthful types).\n",
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
      TypeElement spec, TypeElement domain, TypeElement wire, List<Correspondence> comps) {
    ClassName specName = ClassName.get(spec);
    TypeName domainName = TypeName.get(domain.asType());
    TypeName wireName = TypeName.get(wire.asType());

    CodeBlock.Builder buildArgs = CodeBlock.builder();
    boolean first = true;
    for (RecordComponentElement wireComponent : wire.getRecordComponents()) {
      String name = wireComponent.getSimpleName().toString();
      Correspondence c =
          comps.stream().filter(x -> x.wireName().equals(name)).findFirst().orElseThrow();
      if (!first) {
        buildArgs.add(", ");
      }
      first = false;
      buildArgs.add("domain.$L()", c.name());
    }

    CodeBlock.Builder setArgs = CodeBlock.builder();
    first = true;
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
        setArgs.add("wire.$L()", c.wireName());
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
            .addMethod(
                buildMethod(
                    domainName,
                    wireName,
                    CodeBlock.of("return new $T($L)", wireName, buildArgs.build())))
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
            .addMethod(buildMethod(domainName, wireName, buildSwitch.build()))
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

  private static MethodSpec buildMethod(TypeName domainName, TypeName wireName, CodeBlock body) {
    return MethodSpec.methodBuilder("build")
        .addModifiers(Modifier.PUBLIC)
        .returns(wireName)
        .addParameter(domainName, "domain")
        .addStatement("$T.requireNonNull(domain, $S)", OBJECTS, "domain must not be null")
        .addStatement("$L", body)
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

  private DeclaredType findMappingSpec(TypeElement spec) {
    for (TypeMirror iface : spec.getInterfaces()) {
      // Superinterface mirrors are always declared (or error) types, both DeclaredType.
      DeclaredType declared = (DeclaredType) iface;
      if (((TypeElement) declared.asElement()).getQualifiedName().contentEquals(MAPPING_SPEC)) {
        return declared;
      }
    }
    return null;
  }

  private TypeElement asRecord(TypeMirror mirror) {
    // A DeclaredType's element is always a TypeElement.
    if (mirror instanceof DeclaredType declared) {
      TypeElement type = (TypeElement) declared.asElement();
      if (type.getKind() == ElementKind.RECORD) {
        return type;
      }
    }
    return null;
  }

  private TypeElement asSealed(TypeMirror mirror) {
    if (mirror instanceof DeclaredType declared) {
      TypeElement type = (TypeElement) declared.asElement();
      if (type.getKind() == ElementKind.INTERFACE
          && type.getModifiers().contains(Modifier.SEALED)) {
        return type;
      }
    }
    return null;
  }

  private TypeMirror containerElement(TypeMirror mirror, String rawName) {
    if (mirror instanceof DeclaredType declared
        && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals(rawName)
        && declared.getTypeArguments().size() == (rawName.endsWith("Map") ? 2 : 1)) {
      return declared.getTypeArguments().get(rawName.endsWith("Map") ? 1 : 0);
    }
    return null;
  }

  private static List<String> wireNames(List<? extends RecordComponentElement> comps) {
    return comps.stream().map(c -> c.getSimpleName().toString()).toList();
  }
}
