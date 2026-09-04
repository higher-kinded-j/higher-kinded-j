// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.AnnotationSpec;
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
 *   <li>A widening that names an optic instance cannot be written for a raw or wildcard-carrying
 *       container. The walk turns such a container away and the widening {@linkplain
 *       Widening#declined() names it}, so that the declaration is rejected where it is written
 *       rather than inside generated source (issue #718). {@code Set} and {@code Collection} fall
 *       under that rule; {@code List} deliberately does not, and keeps the no-argument {@code
 *       .each()} whose free type variable takes either without complaint. Routing {@code List}
 *       through an instance too would drag {@code List<?>} and raw {@code List} into the rejection
 *       for no runtime gain, since that traversal is the one they already work with.
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
   * <p>A walk that turns a container away stops there, so the steps still describe a method that
   * can be written: the widening as far as it goes, with the declined container left in focus. The
   * declaration is rejected from the same result, so what the method emits and what the diagnostic
   * names cannot disagree.
   *
   * @param tier the path type the widened method returns
   * @param focusType the type the widened path focuses on
   * @param steps the layers peeled to get there, empty when nothing widens
   * @param declined the container the walk would have widened but turned away, because the optic
   *     instance that widens it cannot be instantiated from the container's type arguments; null
   *     when every layer reached was widened or left alone
   */
  public record Widening(Tier tier, TypeName focusType, List<Step> steps, DeclaredType declined) {}

  private final ProcessingEnvironment processingEnv;
  private final GeneratorRegistry generatorRegistry;

  /**
   * Creates the analysis over a set of SPI generators.
   *
   * @param processingEnv the processing environment
   * @param traversableGenerators the SPI generators, in registration order
   */
  public WideningAnalysis(
      ProcessingEnvironment processingEnv, List<TraversableGenerator> traversableGenerators) {
    this.processingEnv = processingEnv;
    this.generatorRegistry =
        GeneratorRegistry.of(traversableGenerators, processingEnv.getMessager());
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
    DeclaredType declined = collect(component, componentType, widenCollections, 0, steps);
    // A container turned away at the outermost layer leaves no step behind, so a @Nullable one
    // still widens through .nullable(): the method that yields compiles, and the declaration is
    // rejected all the same.
    if (steps.isEmpty() && NullableAnnotations.hasNullableAnnotation(component)) {
      steps.add(new Step(StepKind.NULLABLE, null, null, null));
    }
    return new Widening(tier(steps), focusType(componentType, steps), List.copyOf(steps), declined);
  }

  /**
   * Peels the container layers off a type, appending one step for each.
   *
   * <p>Two kinds of container end the walk short of the leaf. A layer the current settings do not
   * widen is left alone, and a layer whose widening cannot be written is turned away and handed
   * back, so that the declaration can be rejected from the result the method is built from. Nothing
   * beneath either is looked at, because nothing beneath either is ever asked for an optic.
   *
   * @return the container turned away, or null when every layer reached was widened or left alone
   */
  private DeclaredType collect(
      RecordComponentElement component,
      TypeMirror type,
      boolean widenCollections,
      int depth,
      List<Step> steps) {

    if (depth >= MAX_NESTING_DEPTH || type.getKind() != TypeKind.DECLARED) {
      return null;
    }
    DeclaredType declaredType = (DeclaredType) type;
    TypeElement typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    if (OPTIONAL_TYPES.contains(qualifiedName)) {
      return descend(
          component, declaredType, StepKind.OPTIONAL, null, 0, widenCollections, depth, steps);
    }
    StepKind collectionStep = COLLECTION_TYPES.get(qualifiedName);
    if (collectionStep != null) {
      // A Set or a Collection names an Each whose type arguments come from the field type, so a
      // raw or wildcard-carrying one has no widening that can be written (issues #718, #725).
      return namesOpticInstance(collectionStep) && ProcessorUtils.hasUndenotableTypeArguments(type)
          ? declaredType
          : descend(
              component, declaredType, collectionStep, null, 0, widenCollections, depth, steps);
    }

    // A Kind field is read from the component's own declaration, so only the outermost layer of a
    // component can be one; a Kind nested inside a container is left to the container's element.
    if (depth == 0) {
      Optional<KindFieldInfo> kindInfo = new KindFieldAnalyser(processingEnv).analyse(component);
      if (kindInfo.isPresent()) {
        steps.add(new Step(kindStep(kindInfo.get()), kindInfo.get(), null, null));
        return null;
      }
    }
    return collectSpi(component, declaredType, widenCollections, depth, steps);
  }

  /**
   * Widens a layer through the SPI generator that supports it, leaves it alone, or turns it away.
   *
   * <p>A container the walk never steps into is never asked for an optic, so it is left alone
   * whether or not its widening could be written; only a container the walk would have stepped into
   * is turned away.
   *
   * @return the container turned away, or null when it was widened or left alone
   */
  private DeclaredType collectSpi(
      RecordComponentElement component,
      DeclaredType declaredType,
      boolean widenCollections,
      int depth,
      List<Step> steps) {

    // The component rides along at every layer, so an equal-priority conflict on a type reached
    // only inside a container is still reported against the declaration that reaches it.
    return switch (spiLookup(declaredType, component)) {
      case SpiLookup.None _ -> null;
      case SpiLookup.Refused refused ->
          stepsInto(refused.generator(), widenCollections) ? declaredType : null;
      case SpiLookup.Admitted admitted ->
          stepsInto(admitted.generator(), widenCollections)
              ? descend(
                  component,
                  declaredType,
                  spiStep(admitted.generator()),
                  admitted.generator(),
                  admitted.generator().getFocusTypeArgumentIndex(),
                  widenCollections,
                  depth,
                  steps)
              : null;
    };
  }

  /**
   * Whether the walk steps into a container this generator widens.
   *
   * <p>A {@code ZERO_OR_ONE} container is always stepped into. A {@code ZERO_OR_MORE} one is left
   * un-widened by default, for backwards compatibility: the method keeps the container itself in
   * focus until {@code widenCollections} says otherwise.
   */
  private static boolean stepsInto(TraversableGenerator generator, boolean widenCollections) {
    return generator.getCardinality() == Cardinality.ZERO_OR_ONE || widenCollections;
  }

  /** The step a generator's cardinality calls for. */
  private static StepKind spiStep(TraversableGenerator generator) {
    return generator.getCardinality() == Cardinality.ZERO_OR_ONE
        ? StepKind.SPI_ZERO_OR_ONE
        : StepKind.SPI_ZERO_OR_MORE;
  }

  /**
   * Records one layer and continues into the type argument it unwraps to.
   *
   * @return what the walk beneath this layer turned away, or null
   */
  private DeclaredType descend(
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
    return innerType == null
        ? null
        : collect(component, innerType, widenCollections, depth + 1, steps);
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
      return ProcessorUtils.typeNameOf(componentType).box();
    }
    Step last = steps.getLast();
    return switch (last.kind()) {
      // .nullable() rules out the null rather than unwrapping, so the component stays in focus -
      // but without the annotation that put the step there. Affines.nullable() is
      // Affine<@Nullable A, A>: the widening is what makes the focus non-null, and repeating
      // @Nullable on it would describe a value the affine never yields.
      case NULLABLE -> nullRuledOut(ProcessorUtils.typeNameOf(componentType).box());
      case KIND_EXACTLY_ONE, KIND_ZERO_OR_ONE, KIND_ZERO_OR_MORE -> last.kindInfo().elementType();
      case OPTIONAL, LIST, SET, COLLECTION, SPI_ZERO_OR_ONE, SPI_ZERO_OR_MORE ->
          last.innerType() == null
              ? ClassName.get(Object.class)
              : ProcessorUtils.typeNameOf(last.innerType()).box();
    };
  }

  /**
   * The component's name with a nullness annotation on the component itself dropped.
   *
   * <p>Only the outermost one goes: {@code @Nullable List<@Nullable String>} widened by {@code
   * .nullable()} focuses a present {@code List<@Nullable String>}, whose elements are still as
   * nullable as they were written. Anything else the author wrote at that position stays, because
   * it is only the nullness the widening consumed.
   */
  private static TypeName nullRuledOut(TypeName name) {
    List<AnnotationSpec> kept =
        name.annotations().stream()
            .filter(
                annotation ->
                    !NullableAnnotations.NULLABLE_ANNOTATION_NAMES.contains(
                        annotation.type().toString()))
            .toList();
    return kept.size() == name.annotations().size()
        ? name
        : name.withoutAnnotations().annotated(kept);
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
    args.add(ProcessorUtils.typeNameOf(step.innerType()));
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
  private static TypeMirror typeArgumentAt(DeclaredType declaredType, int index) {
    List<? extends TypeMirror> args = declaredType.getTypeArguments();
    if (index >= args.size()) {
      return null;
    }
    return ProcessorUtils.resolveWildcard(args.get(index));
  }

  /**
   * What the SPI has to say about a container.
   *
   * <p>Classified once, here, so that no site ever holds a bare generator for a container whose
   * widening cannot be written. Each site chooses what to do with a refused one: the walk turns it
   * away and {@linkplain Widening#declined() records it}, the navigator declines to step into it,
   * and the navigator's turned-away check asks what it would have reached.
   */
  public sealed interface SpiLookup {

    /** No generator on the annotation processor path supports the type. */
    record None() implements SpiLookup {}

    /**
     * A generator supports the type, but the widening it would emit cannot be written.
     *
     * <p>A generator that names an optic instance — {@code .some(Affines.eitherRight())}, {@code
     * .each(EachInstances.mapValuesEach())} — has that instance's type arguments inferred from the
     * field type, which a raw or wildcard-carrying container gives javac no way to do. A generator
     * with no optic expression widens through {@code .nullable()} or {@code .each()} instead, whose
     * free type variable takes either without complaint, and is admitted.
     *
     * <p>Such a container is left un-widened, and its declaration is rejected where it is written
     * rather than inside generated source (issue #718).
     *
     * @param generator the generator that supports the type
     */
    record Refused(TraversableGenerator generator) implements SpiLookup {}

    /**
     * A generator supports the type and its widening can be written.
     *
     * @param generator the generator to widen with
     */
    record Admitted(TraversableGenerator generator) implements SpiLookup {}
  }

  /**
   * Classifies what the SPI has to say about {@code type}, reading the highest-priority generator
   * from the {@link GeneratorRegistry} every generator-choosing site reads.
   *
   * @param type the type to look up
   * @param component the record component to report an equal-priority conflict against, or null
   * @return none, a refused generator, or an admitted one
   */
  public SpiLookup spiLookup(TypeMirror type, Element component) {
    TraversableGenerator generator = generatorRegistry.generatorFor(type, component);
    if (generator == null) {
      return new SpiLookup.None();
    }
    boolean writable =
        generator.generateOpticExpression().isEmpty()
            || !ProcessorUtils.hasUndenotableTypeArguments(type);
    return writable ? new SpiLookup.Admitted(generator) : new SpiLookup.Refused(generator);
  }
}
