// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.processing.kind.KindFieldAnalyser;
import org.higherkindedj.optics.processing.kind.KindFieldInfo;
import org.higherkindedj.optics.processing.spi.Cardinality;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.OpticExpressionResolver;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The one answer to what a record component's declared type widens to.
 *
 * <p>A container-typed component is reached two ways — the static {@code XFocus} method, and a
 * navigator method on a record that holds an {@code X} — and both must report the same path type
 * for the same declaration. They do because there is one analysis: this one. {@link FocusProcessor}
 * asks it what to emit, and {@link NavigatorClassGenerator} asks it what the emitted method returns
 * so that it can compose that method rather than re-derive the widening (issue #719).
 *
 * <p>What is recognised, and what is not:
 *
 * <ul>
 *   <li>{@code Optional} and {@code Maybe} widen to an {@code AffinePath} through {@code .some()}.
 *   <li>{@code List}, {@code Set} and {@code Collection} widen to a {@code TraversalPath}, each
 *       through the {@code Each} that rebuilds it: {@code List} through the no-argument {@code
 *       .each()}, which is {@code List}-only, and the other two through an instance that rebuilds
 *       their own shape (issue #725). A <em>subtype</em> — {@code ArrayList}, {@code TreeSet} —
 *       does not widen here: none of those three rebuilds it. Concrete containers widen through a
 *       {@link TraversableGenerator}, which knows how to rebuild the one it supports.
 *   <li>A {@code Kind<F, A>} component widens through {@code .traverseOver()}, at the outermost
 *       layer only, because the analysis reads it from the component's own declaration.
 *   <li>Every other container arrives through the SPI. A {@code ZERO_OR_ONE} generator always
 *       widens; a {@code ZERO_OR_MORE} one waits for {@code widenCollections}.
 *   <li>{@code @Nullable} widens a component the containers leave alone, and nothing else.
 * </ul>
 *
 * <p>Containers nest to three layers, and the chain always composes to the leaf: {@code
 * List<Optional<String>>} is {@code .each().some()} focusing {@code String}, not {@code .each()}
 * focusing {@code Optional<String>}.
 */
public final class WideningAnalysis {

  /** ClassName for FocusPath (in hkj-core, not available at processor compile time). */
  static final ClassName FOCUS_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "FocusPath");

  /** ClassName for AffinePath (for Optional field widening). */
  static final ClassName AFFINE_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "AffinePath");

  /** ClassName for TraversalPath (for collection field widening). */
  static final ClassName TRAVERSAL_PATH_CLASS =
      ClassName.get("org.higherkindedj.optics.focus", "TraversalPath");

  /** Optional types that widen to AffinePath via .some(). */
  private static final Set<String> OPTIONAL_TYPES =
      Set.of("java.util.Optional", "org.higherkindedj.hkt.maybe.Maybe");

  /** ClassName for EachInstances, which supplies the {@code Each} a collection widens through. */
  private static final ClassName EACH_INSTANCES_CLASS =
      ClassName.get("org.higherkindedj.optics.each", "EachInstances");

  /**
   * Collection types that widen to TraversalPath, and the step each one widens through.
   *
   * <p>The three do not share an expression. {@code List} widens through the no-argument {@code
   * .each()}, whose traversal is a {@code List} one; a {@code Set} or a {@code Collection} put
   * through that traversal fails its first cast, so each names the {@code Each} that rebuilds its
   * own shape instead (issue #725).
   */
  private static final Map<String, StepKind> COLLECTION_TYPES =
      Map.of(
          "java.util.List", StepKind.LIST,
          "java.util.Set", StepKind.SET,
          "java.util.Collection", StepKind.COLLECTION);

  /** How deep a chain of nested containers the analysis composes. */
  static final int MAX_NESTING_DEPTH = 3;

  /** The path type a widening arrives at. */
  public enum Tier {
    /** {@code FocusPath} - exactly one element, always present. */
    FOCUS,
    /** {@code AffinePath} - zero or one element. */
    AFFINE,
    /** {@code TraversalPath} - zero or more elements. */
    TRAVERSAL;

    /**
     * Returns the tier reached by composing this one with another.
     *
     * @param other the tier to compose with
     * @return the wider of the two
     */
    public Tier widen(Tier other) {
      if (this == TRAVERSAL || other == TRAVERSAL) {
        return TRAVERSAL;
      }
      if (this == AFFINE || other == AFFINE) {
        return AFFINE;
      }
      return FOCUS;
    }

    /** The path class a method of this tier returns. */
    ClassName pathClass() {
      return switch (this) {
        case FOCUS -> FOCUS_PATH_CLASS;
        case AFFINE -> AFFINE_PATH_CLASS;
        case TRAVERSAL -> TRAVERSAL_PATH_CLASS;
      };
    }

    /** The path class's simple name, for Javadoc prose. */
    String description() {
      return pathClass().simpleName();
    }

    /** The reader this tier's path offers, for the Javadoc example. */
    String getMethod() {
      return switch (this) {
        case FOCUS -> "get";
        case AFFINE -> "getOptional";
        case TRAVERSAL -> "getAll";
      };
    }
  }

  /** How one peeled container layer widens the path. */
  public enum StepKind {
    /** Optional/Maybe - AffinePath via {@code .some()}. */
    OPTIONAL(Tier.AFFINE),
    /** List - TraversalPath via the {@code List}-only no-argument {@code .each()}. */
    LIST(Tier.TRAVERSAL),
    /** Set - TraversalPath via {@code .each(EachInstances.setEach())}. */
    SET(Tier.TRAVERSAL),
    /** Collection - TraversalPath via {@code .each(EachInstances.collectionEach())}. */
    COLLECTION(Tier.TRAVERSAL),
    /** A {@code @Nullable} component - AffinePath via {@code .nullable()}. */
    NULLABLE(Tier.AFFINE),
    /** Kind with EXACTLY_ONE semantics - AffinePath via {@code .traverseOver().headOption()}. */
    KIND_EXACTLY_ONE(Tier.AFFINE),
    /** Kind with ZERO_OR_ONE semantics - AffinePath via {@code .traverseOver().headOption()}. */
    KIND_ZERO_OR_ONE(Tier.AFFINE),
    /** Kind with ZERO_OR_MORE semantics - TraversalPath via {@code .traverseOver()}. */
    KIND_ZERO_OR_MORE(Tier.TRAVERSAL),
    /** SPI-registered zero-or-one container - AffinePath via {@code .some(affine)}. */
    SPI_ZERO_OR_ONE(Tier.AFFINE),
    /** SPI-registered zero-or-more container - TraversalPath via {@code .each(each)}. */
    SPI_ZERO_OR_MORE(Tier.TRAVERSAL);

    private final Tier tier;

    StepKind(Tier tier) {
      this.tier = tier;
    }

    /** The tier this layer widens to on its own. */
    Tier tier() {
      return tier;
    }
  }

  /**
   * One layer of a widening chain.
   *
   * @param kind how this layer widens the path
   * @param kindInfo the Kind analysis for a {@code KIND_*} layer, otherwise null
   * @param generator the generator for an {@code SPI_*} layer, otherwise null
   * @param innerType the type this layer unwraps to, or null when it unwraps to nothing
   */
  public record Step(
      StepKind kind,
      KindFieldInfo kindInfo,
      TraversableGenerator generator,
      TypeMirror innerType) {}

  /**
   * What a component's declared type widens to.
   *
   * @param tier the path type the widened method returns
   * @param focusType the type the widened path focuses on
   * @param steps the layers peeled to get there, empty when nothing widens
   */
  public record Widening(Tier tier, TypeName focusType, List<Step> steps) {}

  private final ProcessingEnvironment processingEnv;
  private final List<TraversableGenerator> traversableGenerators;

  /**
   * Creates the analysis over a set of SPI generators.
   *
   * @param processingEnv the processing environment
   * @param traversableGenerators the SPI generators, pre-sorted by priority descending
   */
  public WideningAnalysis(
      ProcessingEnvironment processingEnv, List<TraversableGenerator> traversableGenerators) {
    this.processingEnv = processingEnv;
    this.traversableGenerators = List.copyOf(traversableGenerators);
  }

  /**
   * Analyses a record component's declared type and annotations.
   *
   * @param component the component to analyse
   * @param widenCollections whether {@code ZERO_OR_MORE} SPI containers widen
   * @return the widening its generated method carries
   */
  public Widening analyse(RecordComponentElement component, boolean widenCollections) {
    TypeMirror componentType = component.asType();
    List<Step> steps = new ArrayList<>();
    collect(component, componentType, widenCollections, 0, steps);
    if (steps.isEmpty() && NullableAnnotations.hasNullableAnnotation(component)) {
      steps.add(new Step(StepKind.NULLABLE, null, null, null));
    }
    return new Widening(tier(steps), focusType(componentType, steps), List.copyOf(steps));
  }

  /** Peels the container layers off a type, appending one step for each. */
  private void collect(
      RecordComponentElement component,
      TypeMirror type,
      boolean widenCollections,
      int depth,
      List<Step> steps) {

    if (depth >= MAX_NESTING_DEPTH || type.getKind() != TypeKind.DECLARED) {
      return;
    }
    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    if (OPTIONAL_TYPES.contains(qualifiedName)) {
      descend(component, declaredType, StepKind.OPTIONAL, null, 0, widenCollections, depth, steps);
      return;
    }
    StepKind collectionStep = COLLECTION_TYPES.get(qualifiedName);
    if (collectionStep != null) {
      // A Set or a Collection names an Each whose type arguments come from the field type, so a
      // raw or wildcard-carrying one has no widening that can be written; it is left alone here
      // and its declaration rejected where it is written (issues #718, #725).
      if (!namesOpticInstance(collectionStep)
          || !ProcessorUtils.hasUndenotableTypeArguments(type)) {
        descend(component, declaredType, collectionStep, null, 0, widenCollections, depth, steps);
      }
      return;
    }

    // A Kind field is read from the component's own declaration, so only the outermost layer of a
    // component can be one; a Kind nested inside a container is left to the container's element.
    if (depth == 0) {
      Optional<KindFieldInfo> kindInfo = new KindFieldAnalyser(processingEnv).analyse(component);
      if (kindInfo.isPresent()) {
        steps.add(new Step(kindStep(kindInfo.get()), kindInfo.get(), null, null));
        return;
      }
    }

    // The component rides along only at the outermost layer, so an equal-priority generator
    // conflict is reported against the declaration once rather than once per nested layer.
    TraversableGenerator generator = wideningGenerator(type, depth == 0 ? component : null);
    if (generator == null) {
      return;
    }
    if (generator.getCardinality() == Cardinality.ZERO_OR_ONE) {
      descend(
          component,
          declaredType,
          StepKind.SPI_ZERO_OR_ONE,
          generator,
          generator.getFocusTypeArgumentIndex(),
          widenCollections,
          depth,
          steps);
      return;
    }
    // A ZERO_OR_MORE container is left un-widened by default, for backwards compatibility: the
    // method keeps the container itself in focus until widenCollections says otherwise.
    if (widenCollections) {
      descend(
          component,
          declaredType,
          StepKind.SPI_ZERO_OR_MORE,
          generator,
          generator.getFocusTypeArgumentIndex(),
          widenCollections,
          depth,
          steps);
    }
  }

  /** Records one layer and continues into the type argument it unwraps to. */
  private void descend(
      RecordComponentElement component,
      DeclaredType declaredType,
      StepKind kind,
      TraversableGenerator generator,
      int typeArgumentIndex,
      boolean widenCollections,
      int depth,
      List<Step> steps) {

    TypeMirror innerType = typeArgumentAt(declaredType, typeArgumentIndex);
    steps.add(new Step(kind, null, generator, innerType));
    if (innerType != null) {
      collect(component, innerType, widenCollections, depth + 1, steps);
    }
  }

  /** The step a Kind field's cardinality semantics call for. */
  private static StepKind kindStep(KindFieldInfo kindInfo) {
    return switch (kindInfo.semantics()) {
      case EXACTLY_ONE -> StepKind.KIND_EXACTLY_ONE;
      case ZERO_OR_ONE -> StepKind.KIND_ZERO_OR_ONE;
      case ZERO_OR_MORE -> StepKind.KIND_ZERO_OR_MORE;
    };
  }

  /** The tier a chain arrives at, composing the lattice across its layers. */
  private static Tier tier(List<Step> steps) {
    Tier tier = Tier.FOCUS;
    for (Step step : steps) {
      tier = tier.widen(step.kind().tier());
    }
    return tier;
  }

  /** The type the widened path focuses on: what the last layer peeled unwraps to. */
  private static TypeName focusType(TypeMirror componentType, List<Step> steps) {
    if (steps.isEmpty()) {
      return TypeName.get(componentType).box();
    }
    Step last = steps.getLast();
    return switch (last.kind()) {
      // .nullable() rules out the null rather than unwrapping, so the component stays in focus.
      case NULLABLE -> TypeName.get(componentType).box();
      case KIND_EXACTLY_ONE, KIND_ZERO_OR_ONE, KIND_ZERO_OR_MORE -> last.kindInfo().elementType();
      case OPTIONAL, LIST, SET, COLLECTION, SPI_ZERO_OR_ONE, SPI_ZERO_OR_MORE ->
          last.innerType() == null
              ? ClassName.get(Object.class)
              : TypeName.get(last.innerType()).box();
    };
  }

  /**
   * Builds the chained widening expression a {@link Widening} emits.
   *
   * <p>For example, {@code Optional<List<String>>} produces {@code .some().each()}, and {@code
   * Either<E, Map<K, V>>} produces {@code
   * .some(Affines.eitherRight()).each(EachInstances.mapValuesEach())}.
   *
   * @param steps the layers to render
   * @param args the mutable list of JavaPoet arguments, appended to for SPI import resolution
   * @return the chained expression, empty when nothing widens
   */
  public static String expression(List<Step> steps, List<Object> args) {
    StringBuilder expression = new StringBuilder();
    for (int i = 0; i < steps.size(); i++) {
      Step step = steps.get(i);
      // A no-arg step before a generator's optic instance needs a type witness, so that javac has
      // the argument type to unify the instance against. A next step only exists because the walk
      // descended into a non-null inner type, so innerType() is non-null wherever this is read.
      boolean witness = i + 1 < steps.size() && isParameterised(steps.get(i + 1));
      // Rendered as a value rather than appended arm by arm: the ten kinds are the whole enum,
      // and a switch expression says so without a default arm nothing can reach.
      expression.append(
          switch (step.kind()) {
            case OPTIONAL -> witnessed("some", step, witness, args);
            case LIST -> witnessed("each", step, witness, args);
            case SET -> eachInstance("setEach", args);
            case COLLECTION -> eachInstance("collectionEach", args);
            case NULLABLE -> ".nullable()";
            case SPI_ZERO_OR_ONE -> ".some(" + opticExpression(step, args) + ")";
            case SPI_ZERO_OR_MORE -> ".each(" + opticExpression(step, args) + ")";
            case KIND_EXACTLY_ONE, KIND_ZERO_OR_ONE ->
                traverseOverCall(step.kindInfo()) + ".headOption()";
            case KIND_ZERO_OR_MORE -> traverseOverCall(step.kindInfo());
          });
    }
    return expression.toString();
  }

  /**
   * A no-arg widening call, with the element it hands on spelled out when the next layer's optic
   * instance has to unify against it.
   */
  private static String witnessed(String method, Step step, boolean witness, List<Object> args) {
    if (!witness) {
      return "." + method + "()";
    }
    args.add(TypeName.get(step.innerType()));
    return ".<$T>" + method + "()";
  }

  /** A widening through one of the stock {@code EachInstances} factories. */
  private static String eachInstance(String factory, List<Object> args) {
    args.add(EACH_INSTANCES_CLASS);
    return ".each($T." + factory + "())";
  }

  /** Resolves a generator's optic expression, collecting the imports it names into {@code args}. */
  private static String opticExpression(Step step, List<Object> args) {
    TraversableGenerator generator = step.generator();
    return OpticExpressionResolver.resolve(
        generator.generateOpticExpression(), generator.getRequiredImports(), args);
  }

  /** Whether a step widens through an optic instance whose type arguments must be inferred. */
  private static boolean isParameterised(Step step) {
    return namesOpticInstance(step.kind());
  }

  /**
   * Whether a step hands the path an optic instance rather than calling a no-argument widener.
   *
   * <p>Such an instance has its own type arguments inferred from the type it is handed, which is
   * what makes the layer before it need a type witness and what makes a raw or wildcard-carrying
   * container of its own unwritable.
   */
  private static boolean namesOpticInstance(StepKind kind) {
    return kind == StepKind.SET
        || kind == StepKind.COLLECTION
        || kind == StepKind.SPI_ZERO_OR_ONE
        || kind == StepKind.SPI_ZERO_OR_MORE;
  }

  /**
   * Builds the {@code traverseOver()} call a Kind field widens through.
   *
   * <p>The type parameters are written out because javac has nothing else to infer the witness and
   * element from.
   */
  private static String traverseOverCall(KindFieldInfo kindInfo) {
    String witnessType = kindInfo.witnessType();
    if (kindInfo.isParameterised() && !kindInfo.witnessTypeArgs().isEmpty()) {
      witnessType = witnessType + "<" + kindInfo.witnessTypeArgs() + ">";
    }
    return String.format(
        ".<%s, %s>traverseOver(%s)",
        witnessType, kindInfo.elementType(), kindInfo.traverseExpression());
  }

  /**
   * Whether the analysis recognises this declared type as a container of its own, rather than one
   * the SPI supplies.
   *
   * @param type a declared type
   * @return true for Optional, Maybe, List, Set and Collection
   */
  public boolean recognisedContainer(TypeMirror type) {
    String qualifiedName = qualifiedNameOf(type);
    return OPTIONAL_TYPES.contains(qualifiedName) || COLLECTION_TYPES.containsKey(qualifiedName);
  }

  /**
   * Whether the widening a {@linkplain #recognisedContainer recognised container} would receive
   * cannot be written, because it names an {@code Each} the container's own type arguments give
   * javac no way to instantiate.
   *
   * <p>{@code Optional}, {@code Maybe} and {@code List} widen through a no-argument call whose free
   * type variable takes a raw or wildcard-carrying container without complaint, so only {@code Set}
   * and {@code Collection} answer true here.
   *
   * @param type a declared type
   * @return true when the container is a {@code Set} or a {@code Collection} that is raw or carries
   *     a wildcard type argument
   * @since 0.4.10
   */
  public boolean recognisedWidensUndenotably(TypeMirror type) {
    StepKind step = COLLECTION_TYPES.get(qualifiedNameOf(type));
    return step != null
        && namesOpticInstance(step)
        && ProcessorUtils.hasUndenotableTypeArguments(type);
  }

  /** The qualified name of a declared type's element. */
  private static String qualifiedNameOf(TypeMirror type) {
    return ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
  }

  /**
   * The type argument at the index, with a wildcard resolved to the type it stands for.
   *
   * @param declaredType the container
   * @param index the argument to read
   * @return the resolved argument, or null when the container is raw or the wildcard resolves to no
   *     type at all
   */
  public TypeMirror typeArgumentAt(DeclaredType declaredType, int index) {
    List<? extends TypeMirror> args = declaredType.getTypeArguments();
    if (index >= args.size()) {
      return null;
    }
    return ProcessorUtils.resolveWildcard(args.get(index));
  }

  /**
   * Finds the highest-priority SPI generator that supports the given type.
   *
   * <p>Reached directly only by the diagnostic walks, which exist to report the container {@link
   * #wideningGenerator} turned away. Every widening site reads its generator from that guard.
   *
   * @param type the type to check
   * @param component the record component to report an equal-priority conflict against, or null
   * @return the matched generator, or null if none supports the type
   */
  public TraversableGenerator findSpiGenerator(TypeMirror type, Element component) {
    TraversableGenerator matched = null;
    for (TraversableGenerator generator : traversableGenerators) {
      if (generator.supports(type)) {
        if (matched != null && matched.priority() == generator.priority()) {
          warnOfConflict(type, component, matched, generator);
        } else if (matched == null) {
          matched = generator;
        }
      }
    }
    return matched;
  }

  /** Warns that two generators of equal priority both claim a type, and which one won. */
  private void warnOfConflict(
      TypeMirror type,
      Element component,
      TraversableGenerator matched,
      TraversableGenerator other) {
    if (component == null) {
      return;
    }
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.WARNING,
            "Multiple TraversableGenerator SPI providers with equal priority ("
                + other.priority()
                + ") support type "
                + type
                + ": "
                + matched.getClass().getName()
                + " and "
                + other.getClass().getName()
                + ". Using the first match.",
            component);
  }

  /**
   * The generator that widens {@code type}, or null when none matches or the widening it would emit
   * cannot be written.
   *
   * <p>Every widening site reads its generator from here, so the tier, the navigator class and the
   * composition call cannot disagree about which containers widen.
   *
   * @param type the type to widen
   * @param component the record component to report an equal-priority conflict against, or null
   * @return the generator to widen with, or null
   */
  public TraversableGenerator wideningGenerator(TypeMirror type, Element component) {
    TraversableGenerator matched = findSpiGenerator(type, component);
    return matched != null && widensUndenotably(matched, type) ? null : matched;
  }

  /**
   * Whether {@code generator} cannot write the widening it would emit for {@code type}.
   *
   * <p>A generator that names an optic instance — {@code .some(Affines.eitherRight())}, {@code
   * .each(EachInstances.mapValuesEach())} — has that instance's type arguments inferred from the
   * field type, which a raw or wildcard-carrying container gives javac no way to do. A generator
   * with no optic expression widens through {@code .nullable()} or {@code .each()} instead, whose
   * free type variable takes either without complaint.
   *
   * <p>Such a container is left un-widened, and its declaration is rejected where it is written
   * rather than inside generated source (issue #718).
   *
   * @param generator the generator that would widen the type
   * @param type the container type
   * @return true when the widening cannot be written
   */
  public static boolean widensUndenotably(TraversableGenerator generator, TypeMirror type) {
    return !generator.generateOpticExpression().isEmpty()
        && ProcessorUtils.hasUndenotableTypeArguments(type);
  }
}
