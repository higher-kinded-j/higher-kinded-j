// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.*;
import java.util.*;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.processing.WideningAnalysis.SpiLookup;
import org.higherkindedj.optics.processing.WideningAnalysis.Tier;
import org.higherkindedj.optics.processing.WideningAnalysis.Widening;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.Reachability;

/**
 * Generates navigator wrapper classes for fluent cross-type navigation.
 *
 * <p>Navigator classes wrap FocusPath instances and provide navigation methods for each field of
 * nested types that are also annotated with {@code @GenerateFocus}.
 *
 * <p>This generator supports path type widening:
 *
 * <ul>
 *   <li>{@code FocusPath} → {@code AffinePath} when navigating through optional fields
 *   <li>{@code FocusPath}/{@code AffinePath} → {@code TraversalPath} when navigating through
 *       collections
 * </ul>
 *
 * <p>For example, given:
 *
 * <pre>{@code
 * @GenerateFocus(generateNavigators = true)
 * record Company(String name, Address headquarters, Optional<Address> backup) {}
 *
 * @GenerateFocus(generateNavigators = true)
 * record Address(String street, String city) {}
 * }</pre>
 *
 * <p>This generator creates navigators that return the appropriate path types:
 *
 * <ul>
 *   <li>{@code headquarters().city()} returns {@code FocusPath<Company, String>}
 *   <li>{@code backup().some().city()} returns {@code AffinePath<Company, String>}
 * </ul>
 *
 * <p>A navigation method never works a container's widening out for itself: it composes the static
 * Focus method the target record's own companion generated, so the two report the same path type
 * for the same declaration by construction (issue #719). For a target annotated in this round,
 * {@link WideningAnalysis} says what that method is about to return; for any other target, the
 * return type is read from the method its companion actually published.
 *
 * <p>A target with type parameters of its own gets a navigator that declares them after the source,
 * {@code InnerNavigator<S, T>}, and a component reaching it passes the type arguments it was
 * declared with: {@code Inner<String> inner} gives {@code InnerNavigator<Outer, String>}. A
 * component with none it can pass, a wildcard ({@code Inner<?>}) or a raw {@code Inner}, keeps its
 * plain path.
 */
public class NavigatorClassGenerator {

  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");

  private final ProcessingEnvironment processingEnv;
  private final Set<String> navigableTypes;
  private final int maxDepth;
  private final WideningAnalysis analysis;

  /**
   * Creates a new navigator class generator.
   *
   * @param processingEnv the processing environment
   * @param navigableTypes set of fully qualified type names that have @GenerateFocus
   * @param maxDepth maximum depth for navigator chains
   * @param analysis the analysis that answers what a component's Focus method returns
   */
  public NavigatorClassGenerator(
      ProcessingEnvironment processingEnv,
      Set<String> navigableTypes,
      int maxDepth,
      WideningAnalysis analysis) {
    this.processingEnv = processingEnv;
    this.navigableTypes = navigableTypes;
    this.maxDepth = Math.max(1, Math.min(10, maxDepth));
    this.analysis = analysis;
  }

  /**
   * What the Focus method for {@code component} on {@code record} widens to.
   *
   * <p>The settings are the declaring record's, not the navigating one's, because the method being
   * described was generated under them. The walk reports nothing, since a navigator walks into the
   * component again for every route that reaches it; the declaration pass reports any generator
   * conflict it meets, through {@link #declaredWidening}.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return the widening its Focus method carries
   */
  private Widening widening(TypeElement record, RecordComponentElement component) {
    return analysis.analyse(component, widensContainers(record, component));
  }

  /**
   * {@link #widening}, for the declaration pass over a component a navigator method takes: it
   * reports against the component each equal-priority generator conflict the walk meets.
   *
   * <p>The processor asks for a component's navigator method once, while generating the record that
   * declares it, and falls back to the static Focus method, which reports for itself, only when
   * there is none. So this is the one reporting walk for a component a navigator method takes.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return the widening its navigator method carries
   */
  private Widening declaredWidening(TypeElement record, RecordComponentElement component) {
    return analysis.analyseDeclaration(component, widensContainers(record, component));
  }

  /**
   * Whether the Focus method for this component steps into a {@code ZERO_OR_MORE} container.
   *
   * <p>A container whose element reaches a navigator always does — reaching that element is what
   * the navigator exists for — and otherwise the declaring record's {@code widenCollections}
   * decides.
   *
   * <p>The question is the one {@link #navigatorTarget} answers, not merely whether the element is
   * navigable. A container the widening steps into but no navigator is offered for would leave the
   * navigation method declaring a traversal and returning a focus, and every reason a navigator is
   * declined — the element is given a wildcard, the record turned navigators off, the field is
   * filtered out — reaches that same disagreement.
   */
  private boolean widensContainers(TypeElement record, RecordComponentElement component) {
    return (spiNavigable(component.asType(), companionPackage(record)) != null
            && navigatorTarget(record, component) != null)
        || focusSettings(record).widenCollections();
  }

  /**
   * The {@code @GenerateFocus} settings a record's companion was generated under.
   *
   * <p>Only ever asked of a record the processor has already established is annotated: the one it
   * is generating for, or a target already found to carry the annotation.
   */
  private static GenerateFocus focusSettings(TypeElement record) {
    return record.getAnnotation(GenerateFocus.class);
  }

  /** The Focus companion class a record generates, honouring a redirected target package. */
  private ClassName focusClassOf(TypeElement record) {
    return focusClassOf(record, focusSettings(record));
  }

  /**
   * The package a record's Focus companion is written into, which is where every question about
   * that companion's navigators is answered from. Asking it from the navigating record's package
   * instead would describe a target's companion as it was never generated.
   */
  private String companionPackage(TypeElement record) {
    return focusClassOf(record).packageName();
  }

  /** The Focus companion class a record generates under the given settings. */
  private ClassName focusClassOf(TypeElement record, GenerateFocus settings) {
    String targetPackage = settings.targetPackage();
    String packageName =
        targetPackage.isEmpty()
            ? processingEnv.getElementUtils().getPackageOf(record).getQualifiedName().toString()
            : targetPackage;
    return ClassName.get(packageName, record.getSimpleName() + "Focus");
  }

  /**
   * Generates navigator inner classes for a Focus class.
   *
   * @param focusClassBuilder the builder for the Focus class
   * @param recordElement the record being processed
   * @param currentDepth current depth in the navigation chain
   */
  public void generateNavigators(
      TypeSpec.Builder focusClassBuilder, TypeElement recordElement, int currentDepth) {

    for (RecordComponentElement component : recordElement.getRecordComponents()) {
      // The reasons a component gets no navigator are told apart here, because only some of them
      // show in the declaration; navigatorTarget conflates them by design.
      Target target = navigatorTarget(recordElement, component);
      if (target == null) {
        switch (skippedTarget(recordElement, component)) {
          case Reach.Uninstantiable(Target uninstantiable) ->
              reportUninstantiableTargetSkipped(recordElement, component, uninstantiable);
          case Reach.Unpublished(TypeElement unpublished) ->
              reportUnpublishedTargetSkipped(recordElement, component, unpublished);
          case Reach.Hidden hidden -> reportHiddenTargetSkipped(recordElement, component, hidden);
          default -> {}
        }
        continue;
      }
      focusClassBuilder.addType(
          generateNavigatorClass(
              component, target.record(), currentDepth, widening(recordElement, component).tier()));
    }
  }

  /**
   * Says why a component asking for a navigator did not get one, when the reason is that it reaches
   * a generic target through a wildcard or a raw type.
   *
   * <p>The declaring record asked for navigators and gets one fewer than the components suggest,
   * which is the same surprise a delegate-name collision produces and is reported the same way.
   * Most other reasons a component has no navigator are visible in what it is: not navigable, or
   * filtered out by the record's own include/exclude. The one that is not, a target with no
   * companion to compose, is reported the same way.
   *
   * <p>This reason is given ahead of a missing or unreachable companion, because it shows in the
   * declaration and holds however the target's module was built. No chain is offered in place of
   * the navigator, because none compiles: a path into the target is over one instantiation of it,
   * which a wildcard or a raw type is not.
   *
   * @param recordElement the record declaring the component
   * @param component the component whose navigator was not generated
   * @param target the target as the component reaches it, already found uninstantiable
   */
  private void reportUninstantiableTargetSkipped(
      TypeElement recordElement, RecordComponentElement component, Target target) {

    String componentName = component.getSimpleName().toString();
    String name = target.record().getSimpleName().toString();
    String written =
        ProcessorUtils.isRaw(target.type())
            ? "the raw " + name
            : ProcessorUtils.simpleTypeName(target.type());
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.NOTE,
            "Navigator for field '"
                + componentName
                + "' is not generated: a navigator into "
                + name
                + " has to name one type for each of its type parameters, which "
                + written
                + " does not. "
                + focusClassOf(recordElement).simpleName()
                + "."
                + componentName
                + "() keeps its plain path, and a path over one instantiation of "
                + name
                + " does not compose with it either. "
                + ProcessorUtils.concreteAlternative(target.type())
                    .map(
                        alternative ->
                            "Write " + alternative + ", or another concrete instantiation,")
                    .orElse(
                        "Write " + name + " with a concrete type argument for each type parameter")
                + " in place of "
                + written
                + " to get a navigator.",
            component);
  }

  /**
   * Says why a component asking for a navigator did not get one, when its target is a record from a
   * dependency with no companion to compose: none on this module's compile classpath, or one this
   * module cannot access.
   *
   * <p>Nothing in the declaration shows this: the target visibly carries {@code @GenerateFocus},
   * and the reason is another module's build, so it is the least visible reason a navigator can be
   * missing and the one an author most needs told.
   *
   * @param recordElement the record declaring the component
   * @param component the component whose navigator was not generated
   * @param unpublished the record it reaches, already established by the caller
   */
  private void reportUnpublishedTargetSkipped(
      TypeElement recordElement, RecordComponentElement component, TypeElement unpublished) {

    String componentName = component.getSimpleName().toString();
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.NOTE,
            "Navigator for field '"
                + componentName
                + "' is not generated: "
                + unpublished.getQualifiedName()
                + " carries @GenerateFocus, but no public "
                + focusClassOf(unpublished).canonicalName()
                + " is on this module's compile classpath, which usually means the module declaring "
                + unpublished.getSimpleName()
                + " did not run hkj-processor. Add hkj-processor to that module's annotation"
                + " processor path and rebuild it; until then "
                + focusClassOf(recordElement).simpleName()
                + "."
                + componentName
                + "() keeps its plain path.",
            component);
  }

  /**
   * Says why a component asking for a navigator did not get one, when the navigator would name a
   * type this Focus class's package cannot reach: the target record, or one of its components, is
   * {@code private} or hidden in another package. The Focus class itself can still name the target,
   * so the component keeps its plain path.
   *
   * @param recordElement the record declaring the component
   * @param component the component whose navigator was not generated
   * @param hidden the target and the type its navigator would name, already established
   */
  private void reportHiddenTargetSkipped(
      TypeElement recordElement, RecordComponentElement component, Reach.Hidden hidden) {

    String componentName = component.getSimpleName().toString();
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.NOTE,
            "Navigator for field '"
                + componentName
                + "' is not generated: a navigator into "
                + hidden.record().getSimpleName()
                + " names '"
                + hidden.hidden().getSimpleName()
                + "', which cannot be reached from '"
                + companionPackage(recordElement)
                + "'. "
                + Reachability.fix(
                    processingEnv.getElementUtils(),
                    hidden.hidden(),
                    new Reachability.Target(
                        companionPackage(recordElement), "", home -> Optional.empty()))
                + " Until then "
                + focusClassOf(recordElement).simpleName()
                + "."
                + componentName
                + "() keeps its plain path.",
            component);
  }

  /**
   * Why a component that reaches an annotated record has no navigator into it: the component gives
   * the record a wildcard or no type arguments, or, where the reason does not show in the
   * declaration, the record is one from a dependency with no companion this module can compose, or
   * its navigator would name a type this Focus class's package cannot reach.
   *
   * <p>A wildcard or a raw type is the reason given whenever it applies. It shows in the
   * declaration and holds however the record's module was built, so naming a missing or unreachable
   * companion instead would promise a navigator that fixing it could not deliver.
   *
   * @param record the record that declares the component
   * @param component the component, which has no navigator
   * @return an {@code Uninstantiable}, {@code Unpublished} or {@code Hidden} reach, or {@code None}
   *     when none applies
   */
  private Reach skippedTarget(TypeElement record, RecordComponentElement component) {
    if (!shouldGenerateNavigator(record, component)) {
      return new Reach.None();
    }
    TypeMirror fieldType = component.asType();
    TypeMirror reached =
        fieldType.getKind() == TypeKind.DECLARED
                && !analysis.recognisedContainer(fieldType)
                && analysis.spiLookup(fieldType) instanceof SpiLookup.Admitted admitted
            ? spiElement(fieldType, admitted.generator())
            : fieldType;
    if (reached == null) {
      return new Reach.None();
    }
    Reach reach = reach(reached, companionPackage(record));
    if (Target.instantiable(reached)) {
      return reach;
    }
    return switch (reach) {
      case Reach.Navigable(TypeElement navigable) ->
          new Reach.Uninstantiable(new Target(navigable, (DeclaredType) reached));
      case Reach.Unpublished(TypeElement unpublished) ->
          new Reach.Uninstantiable(new Target(unpublished, (DeclaredType) reached));
      case Reach.Hidden(TypeElement hidden, TypeElement _) ->
          new Reach.Uninstantiable(new Target(hidden, (DeclaredType) reached));
      default -> reach;
    };
  }

  /**
   * A navigable record, as a component reaches it: the component's own type, or the element of the
   * SPI container the component is.
   *
   * @param record the navigable record
   * @param type the record as the component instantiates it, whose type arguments a navigator into
   *     it is instantiated with
   */
  private record Target(TypeElement record, DeclaredType type) {

    /** Whether a navigator into the record can be instantiated as the component reaches it. */
    boolean instantiable() {
      return instantiable(type);
    }

    /**
     * Whether a navigator into a record can be instantiated with the type arguments a type gives
     * it: one type for each of its type parameters, which a wildcard among them or a raw type does
     * not supply. A non-generic record has none to supply and always can.
     */
    static boolean instantiable(TypeMirror type) {
      return !ProcessorUtils.hasUndenotableTypeArguments(type);
    }
  }

  /**
   * The navigable record a component reaches, instantiable or not, before the navigator question is
   * asked of it.
   *
   * @param component the component to read
   * @param fromPackage the package of the companion whose navigator is in question
   * @return the navigable record it reaches, or null when it reaches none
   */
  private Target navigableTarget(RecordComponentElement component, String fromPackage) {
    TypeMirror fieldType = component.asType();
    TypeElement direct = navigableTypeElement(fieldType, fromPackage);
    if (direct != null) {
      return new Target(direct, (DeclaredType) fieldType);
    }
    return spiNavigable(fieldType, fromPackage);
  }

  /**
   * The record a record's Focus method for this component navigates to, or null when that method
   * hands back a path instead.
   *
   * <p>A component reaches a navigator by being a navigable type itself, or by being an SPI
   * container of one, and in either case only when it gives that type a navigator can be
   * instantiated with. Every site that asks — the navigator class, the method that returns it, and
   * a navigation method composing it from another record — reads the answer from here, so they
   * cannot disagree about which components have one.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return the navigable record its Focus method reaches, or null
   */
  private Target navigatorTarget(TypeElement record, RecordComponentElement component) {
    if (!focusSettings(record).generateNavigators()
        || !shouldGenerateNavigator(record, component)) {
      return null;
    }
    Target target = navigableTarget(component, companionPackage(record));
    return target == null || !target.instantiable() ? null : target;
  }

  /**
   * Generates a navigator class for a specific field.
   *
   * @param component the record component (field)
   * @param targetRecord the target record type (the field's type)
   * @param currentDepth current depth
   * @param tier the path tier for this navigator
   * @return the generated navigator TypeSpec
   */
  private TypeSpec generateNavigatorClass(
      RecordComponentElement component, TypeElement targetRecord, int currentDepth, Tier tier) {

    String componentName = component.getSimpleName().toString();
    String navigatorClassName = ProcessorUtils.capitalise(componentName) + "Navigator";
    TypeName targetTypeName = TypeName.get(targetRecord.asType());

    // The source comes first, then the target's own type parameters under their own names: the
    // navigation methods read the target's components, which are written in those names. The
    // source takes another name where the target has claimed S.
    TypeVariableName sourceTypeVar =
        TypeVariableName.get(ProcessorUtils.freeTypeVariableName("S", targetRecord));
    List<TypeVariableName> targetTypeVars =
        targetRecord.getTypeParameters().stream()
            .map(parameter -> ProcessorUtils.typeVariableOf(parameter, analysis.targetPackage()))
            .toList();

    // The delegate type depends on the path kind
    ClassName pathClass = tier.pathClass();
    ParameterizedTypeName delegateType =
        ParameterizedTypeName.get(pathClass, sourceTypeVar, targetTypeName);

    String tierDescription =
        switch (tier) {
          case FOCUS -> "FocusPath";
          case AFFINE -> "AffinePath (optional navigation)";
          case TRAVERSAL -> "TraversalPath (collection navigation)";
        };

    // A nested type is its own class file, and a coverage tool reads the marker there. The class
    // redeclares the target's type parameters, so a raw bound among them is answered here, the one
    // place a member's suppression cannot reach.
    TypeSpec.Builder navigatorBuilder =
        TypeSpec.classBuilder(navigatorClassName)
            .addAnnotation(GENERATED)
            .addAnnotations(ProcessorUtils.rawTypesSuppression(targetRecord.asType(), targetRecord))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addTypeVariable(sourceTypeVar)
            .addTypeVariables(targetTypeVars)
            .addJavadoc(
                "Navigator for fluent access to {@code $L} fields.\n\n"
                    + "<p>This navigator wraps a {@link $T} and provides direct navigation methods\n"
                    + "for all fields of {@link $T}.\n\n"
                    + "<p>Path type: $L\n\n"
                    + "@param <$L> the source type at the root of the navigation",
                componentName,
                pathClass,
                ClassName.get(targetRecord),
                tierDescription,
                sourceTypeVar.name());
    for (TypeVariableName targetTypeVar : targetTypeVars) {
      navigatorBuilder.addJavadoc(
          "\n@param <$L> the {@code $L} of the {@code $L} navigated to",
          targetTypeVar.name(),
          targetTypeVar.name(),
          targetRecord.getSimpleName());
    }

    // Add delegate field
    navigatorBuilder.addField(
        FieldSpec.builder(delegateType, "delegate", Modifier.PRIVATE, Modifier.FINAL).build());

    // Add constructor
    navigatorBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(delegateType, "delegate")
            .addStatement("this.delegate = java.util.Objects.requireNonNull(delegate)")
            .build());

    // Add delegate accessor methods based on path kind
    addDelegateMethods(navigatorBuilder, sourceTypeVar, targetTypeName, delegateType, tier);

    // Add navigation methods for each field of the target record
    addNavigationMethods(
        navigatorBuilder, component, targetRecord, sourceTypeVar, currentDepth + 1, tier);

    return navigatorBuilder.build();
  }

  /**
   * A navigator class instantiated as every navigator is: over a source first, then with the type
   * arguments its target is given, in the order the target declares its parameters.
   */
  private static ParameterizedTypeName navigatorType(
      ClassName navigatorClass, TypeName source, List<TypeName> typeArguments) {
    return ParameterizedTypeName.get(
        navigatorClass,
        Stream.concat(Stream.of(source), typeArguments.stream()).toArray(TypeName[]::new));
  }

  /** Type arguments as the generated Focus class writes them. */
  private List<TypeName> typeNamesOf(List<? extends TypeMirror> arguments) {
    return arguments.stream()
        .map(argument -> ProcessorUtils.typeNameOf(argument, analysis.targetPackage()))
        .toList();
  }

  /** Adds the delegate methods one path kind's navigator forwards to its underlying path. */
  @FunctionalInterface
  private interface DelegateMethods {
    void addTo(
        TypeSpec.Builder navigatorBuilder,
        TypeVariableName sourceTypeVar,
        TypeName targetTypeName,
        ParameterizedTypeName delegateType);
  }

  /** Adds delegate methods that forward to the underlying path. */
  private void addDelegateMethods(
      TypeSpec.Builder navigatorBuilder,
      TypeVariableName sourceTypeVar,
      TypeName targetTypeName,
      ParameterizedTypeName delegateType,
      Tier tier) {

    // Selected as a value rather than dispatched to as a statement: the three kinds are the whole
    // enum, and a switch expression says so without a default arm nothing can reach.
    DelegateMethods delegates =
        switch (tier) {
          case FOCUS -> this::addFocusPathDelegateMethods;
          case AFFINE -> this::addAffinePathDelegateMethods;
          case TRAVERSAL -> this::addTraversalPathDelegateMethods;
        };
    delegates.addTo(navigatorBuilder, sourceTypeVar, targetTypeName, delegateType);
  }

  /** Adds delegate methods for FocusPath navigators. */
  private void addFocusPathDelegateMethods(
      TypeSpec.Builder navigatorBuilder,
      TypeVariableName sourceTypeVar,
      TypeName targetTypeName,
      ParameterizedTypeName delegateType) {

    // get(S source) -> A
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("get")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(targetTypeName)
            .addStatement("return delegate.get(source)")
            .addJavadoc(
                "Extracts the focused value from the source.\n\n"
                    + "@param source the source structure\n"
                    + "@return the focused value")
            .build());

    // set(A value, S source) -> S
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("set")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(targetTypeName, "value")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.set(value, source)")
            .addJavadoc(
                "Creates a new source with the focused value replaced.\n\n"
                    + "@param value the new value\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with the updated value")
            .build());

    // modify(Function<A, A> f, S source) -> S
    ParameterizedTypeName functionType =
        ParameterizedTypeName.get(ClassName.get(Function.class), targetTypeName, targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("modify")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(functionType, "f")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.modify(f, source)")
            .addJavadoc(
                "Creates a new source with the focused value transformed.\n\n"
                    + "@param f the transformation function\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with the modified value")
            .build());

    // toLens() -> Lens<S, A>
    ParameterizedTypeName lensType =
        ParameterizedTypeName.get(ClassName.get(Lens.class), sourceTypeVar, targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("toLens")
            .addModifiers(Modifier.PUBLIC)
            .returns(lensType)
            .addStatement("return delegate.toLens()")
            .addJavadoc("Extracts the underlying lens.\n\n" + "@return the wrapped Lens")
            .build());

    // toPath() -> FocusPath<S, A>
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("toPath")
            .addModifiers(Modifier.PUBLIC)
            .returns(delegateType)
            .addStatement("return delegate")
            .addJavadoc("Returns the underlying FocusPath.\n\n" + "@return the wrapped FocusPath")
            .build());
  }

  /** Adds delegate methods for AffinePath navigators. */
  private void addAffinePathDelegateMethods(
      TypeSpec.Builder navigatorBuilder,
      TypeVariableName sourceTypeVar,
      TypeName targetTypeName,
      ParameterizedTypeName delegateType) {

    // getOptional(S source) -> Optional<A>
    ParameterizedTypeName optionalType =
        ParameterizedTypeName.get(ClassName.get(Optional.class), targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("getOptional")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(optionalType)
            .addStatement("return delegate.getOptional(source)")
            .addJavadoc(
                "Extracts the focused value if present.\n\n"
                    + "@param source the source structure\n"
                    + "@return Optional containing the value, or empty if not focused")
            .build());

    // set(A value, S source) -> S
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("set")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(targetTypeName, "value")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.set(value, source)")
            .addJavadoc(
                "Creates a new source with the focused value replaced.\n\n"
                    + "@param value the new value\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with the updated value")
            .build());

    // modify(Function<A, A> f, S source) -> S
    ParameterizedTypeName functionType =
        ParameterizedTypeName.get(ClassName.get(Function.class), targetTypeName, targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("modify")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(functionType, "f")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.modify(f, source)")
            .addJavadoc(
                "Modifies the focused value if present.\n\n"
                    + "@param f the transformation function\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with the modified value, or original if not focused")
            .build());

    // matches(S source) -> boolean
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("matches")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(TypeName.BOOLEAN)
            .addStatement("return delegate.matches(source)")
            .addJavadoc(
                "Checks if this path focuses on a value in the given source.\n\n"
                    + "@param source the source structure to test\n"
                    + "@return true if a value is focused, false otherwise")
            .build());

    // toPath() -> AffinePath<S, A>
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("toPath")
            .addModifiers(Modifier.PUBLIC)
            .returns(delegateType)
            .addStatement("return delegate")
            .addJavadoc("Returns the underlying AffinePath.\n\n" + "@return the wrapped AffinePath")
            .build());
  }

  /** Adds delegate methods for TraversalPath navigators. */
  private void addTraversalPathDelegateMethods(
      TypeSpec.Builder navigatorBuilder,
      TypeVariableName sourceTypeVar,
      TypeName targetTypeName,
      ParameterizedTypeName delegateType) {

    // getAll(S source) -> List<A>
    ParameterizedTypeName listType =
        ParameterizedTypeName.get(ClassName.get(List.class), targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("getAll")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(listType)
            .addStatement("return delegate.getAll(source)")
            .addJavadoc(
                "Extracts all focused values from the source.\n\n"
                    + "@param source the source structure\n"
                    + "@return list of all focused values (may be empty)")
            .build());

    // setAll(A value, S source) -> S
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("setAll")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(targetTypeName, "value")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.setAll(value, source)")
            .addJavadoc(
                "Creates a new source with all focused values replaced.\n\n"
                    + "@param value the new value for all focused elements\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with all focused values updated")
            .build());

    // modifyAll(Function<A, A> f, S source) -> S
    ParameterizedTypeName functionType =
        ParameterizedTypeName.get(ClassName.get(Function.class), targetTypeName, targetTypeName);
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("modifyAll")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(functionType, "f")
            .addParameter(sourceTypeVar, "source")
            .returns(sourceTypeVar)
            .addStatement("return delegate.modifyAll(f, source)")
            .addJavadoc(
                "Creates a new source with all focused values transformed.\n\n"
                    + "@param f the transformation function\n"
                    + "@param source the source structure\n"
                    + "@return a new structure with all focused values modified")
            .build());

    // count(S source) -> int
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("count")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(TypeName.INT)
            .addStatement("return delegate.count(source)")
            .addJavadoc(
                "Counts the number of focused elements.\n\n"
                    + "@param source the source structure\n"
                    + "@return the number of focused elements")
            .build());

    // isEmpty(S source) -> boolean
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("isEmpty")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(sourceTypeVar, "source")
            .returns(TypeName.BOOLEAN)
            .addStatement("return delegate.isEmpty(source)")
            .addJavadoc(
                "Checks if the traversal focuses on no elements.\n\n"
                    + "@param source the source structure\n"
                    + "@return true if no elements are focused")
            .build());

    // toPath() -> TraversalPath<S, A>
    navigatorBuilder.addMethod(
        MethodSpec.methodBuilder("toPath")
            .addModifiers(Modifier.PUBLIC)
            .returns(delegateType)
            .addStatement("return delegate")
            .addJavadoc(
                "Returns the underlying TraversalPath.\n\n" + "@return the wrapped TraversalPath")
            .build());
  }

  /** Returns the set of delegate method names for a given path tier. */
  private static Set<String> getDelegateMethodNames(Tier tier) {
    return switch (tier) {
      case FOCUS -> Set.of("get", "set", "modify", "toLens", "toPath");
      case AFFINE -> Set.of("getOptional", "set", "modify", "matches", "toPath");
      case TRAVERSAL -> Set.of("getAll", "setAll", "modifyAll", "count", "isEmpty", "toPath");
    };
  }

  /**
   * Adds a navigation method for each field of the target record.
   *
   * <p>Each one composes the static Focus method that record's own companion generated for the
   * field, rather than rebuilding its lens and working the widening out again. The two therefore
   * report the same path type for the same declaration, the field-name segments the static method
   * carries come along through {@code via}, and a shape only one of them understands — a {@code
   * Kind} component, a nested container — cannot arrive here half-widened (issue #719).
   *
   * <p>A field whose published method this compilation cannot compose is left out of the navigator,
   * with a note on the component the navigator belongs to.
   */
  private void addNavigationMethods(
      TypeSpec.Builder navigatorBuilder,
      RecordComponentElement owner,
      TypeElement targetRecord,
      TypeVariableName sourceTypeVar,
      int currentDepth,
      Tier currentTier) {

    ClassName targetFocusClass = focusClassOf(targetRecord);
    Set<String> delegateNames = getDelegateMethodNames(currentTier);

    for (RecordComponentElement component : targetRecord.getRecordComponents()) {
      String fieldName = component.getSimpleName().toString();

      // Skip fields that would collide with delegate method names
      if (delegateNames.contains(fieldName)) {
        processingEnv
            .getMessager()
            .printMessage(
                Diagnostic.Kind.NOTE,
                "Navigator field '"
                    + fieldName
                    + "' in "
                    + targetRecord.getSimpleName()
                    + " collides with a delegate method name. "
                    + "Use .toPath().via("
                    + targetFocusClass.simpleName()
                    + "."
                    + fieldName
                    + "()) as a workaround.",
                owner);
        continue;
      }

      CodeBlock focusMethod = focusMethodCall(targetFocusClass, targetRecord, fieldName);
      switch (fieldShape(targetRecord, targetFocusClass, component)) {
        case FieldShape.Unresolvable unresolvable ->
            reportUnresolvableField(owner, targetFocusClass, fieldName, unresolvable.type());
        case FieldShape.Unrecognised _ ->
            reportUnrecognisedField(owner, targetRecord, targetFocusClass, fieldName);
        case FieldShape.Path path ->
            navigatorBuilder.addMethod(
                navigationMethod(
                        component,
                        fieldName,
                        sourceTypeVar,
                        currentTier.widen(path.tier()),
                        path.focusType())
                    .addStatement("return delegate.via($L)", focusMethod)
                    .build());
        case FieldShape.Navigator navigator -> {
          Tier composed = currentTier.widen(navigator.tier());
          MethodSpec.Builder methodBuilder =
              navigationMethod(
                  component, fieldName, sourceTypeVar, composed, navigator.focusType());
          addNavigatorComposition(
              methodBuilder, navigator, focusMethod, sourceTypeVar, currentDepth, composed);
          navigatorBuilder.addMethod(methodBuilder.build());
        }
      }
    }
  }

  /**
   * A call to the static Focus method a target's companion generated for one field.
   *
   * <p>A generic target's method declares the target's type parameters, and they are passed
   * explicitly: under the navigator's own, which carry the same names. A call whose result is
   * composed further, {@code InnerFocus.deeper().toPath()}, has nothing else to infer them from.
   */
  private static CodeBlock focusMethodCall(
      ClassName targetFocusClass, TypeElement targetRecord, String fieldName) {
    if (targetRecord.getTypeParameters().isEmpty()) {
      return CodeBlock.of("$T.$L()", targetFocusClass, fieldName);
    }
    CodeBlock typeArguments =
        targetRecord.getTypeParameters().stream()
            .map(parameter -> CodeBlock.of("$L", parameter.getSimpleName()))
            .collect(CodeBlock.joining(", "));
    return CodeBlock.of("$T.<$L>$L()", targetFocusClass, typeArguments, fieldName);
  }

  /** A navigation method's declaration: its name, the path it returns, and its javadoc. */
  private static MethodSpec.Builder navigationMethod(
      RecordComponentElement component,
      String fieldName,
      TypeVariableName sourceTypeVar,
      Tier composed,
      TypeName focusType) {
    return MethodSpec.methodBuilder(fieldName)
        // The method writes the target's field type out. The target's type-parameter bounds are
        // the navigator class's, and answered there.
        .addAnnotations(ProcessorUtils.rawTypesSuppression(List.of(component.asType())))
        .addModifiers(Modifier.PUBLIC)
        .returns(ParameterizedTypeName.get(composed.pathClass(), sourceTypeVar, focusType))
        .addJavadoc(
            "Navigates to the {@code $L} field.\n\n"
                + "@return a $L focusing on the {@code $L} field",
            fieldName,
            composed.description(),
            fieldName);
  }

  /**
   * Says why a navigator has no method for one of its target's fields: the method the target's
   * companion published names a type this compilation cannot resolve.
   */
  private void reportUnresolvableField(
      RecordComponentElement owner, ClassName targetFocusClass, String fieldName, TypeMirror type) {
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.NOTE,
            "Navigator for field '"
                + owner.getSimpleName()
                + "' has no '"
                + fieldName
                + "' method: "
                + targetFocusClass.canonicalName()
                + "."
                + fieldName
                + "() names "
                + ProcessorUtils.qualifiedTypeName(type)
                + ", which is not on this module's compile classpath. Put the module declaring it on"
                + " this module's compile classpath to navigate through '"
                + fieldName
                + "'.",
            owner);
  }

  /**
   * Says why a navigator has no method for one of its target's fields: the target's companion
   * publishes no method for it that a navigator can compose, so it was not generated from the
   * record as the record now is.
   */
  private void reportUnrecognisedField(
      RecordComponentElement owner,
      TypeElement targetRecord,
      ClassName targetFocusClass,
      String fieldName) {
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.NOTE,
            "Navigator for field '"
                + owner.getSimpleName()
                + "' has no '"
                + fieldName
                + "' method: "
                + targetFocusClass.canonicalName()
                + " has no public static "
                + fieldName
                + "() returning a path or navigator over "
                + targetRecord.getSimpleName()
                + ", so hkj-processor did not generate it from "
                + targetRecord.getSimpleName()
                + " as it now is. Rebuild the module declaring "
                + targetRecord.getSimpleName()
                + " with hkj-processor; if "
                + targetFocusClass.simpleName()
                + " is a class of your own, rename it or redirect the generated one with"
                + " targetPackage.",
            owner);
  }

  /**
   * Composes a field whose own Focus method hands back a navigator.
   *
   * <p>That navigator holds a path of the field's own tier, so it can only be handed on when
   * composing with this navigator's delegate lands on that same tier. A wider composition has no
   * navigator to wrap the result in, so the composed path is the answer and navigation stops there;
   * so it does at the depth limit.
   *
   * <p>The navigator handed on is instantiated with the type arguments the field gives its own
   * target, after this navigator's source.
   */
  private void addNavigatorComposition(
      MethodSpec.Builder methodBuilder,
      FieldShape.Navigator navigator,
      CodeBlock focusMethod,
      TypeVariableName sourceTypeVar,
      int currentDepth,
      Tier composed) {

    if (currentDepth < maxDepth && composed == navigator.tier()) {
      methodBuilder.returns(
          navigatorType(navigator.navigatorClass(), sourceTypeVar, navigator.typeArguments()));
      methodBuilder.addStatement(
          "return new $T<>(delegate.via($L.toPath()))", navigator.navigatorClass(), focusMethod);
      return;
    }
    methodBuilder.addStatement("return delegate.via($L.toPath())", focusMethod);
  }

  /**
   * What a target record's static Focus method for one field hands back, or why a navigation method
   * cannot compose it from this compilation.
   */
  private sealed interface FieldShape {

    /** A method returning a plain path of this tier and focus. */
    record Path(Tier tier, TypeName focusType) implements FieldShape {}

    /**
     * A method returning a navigator that wraps a path of this tier and focus, instantiated with
     * these type arguments after its source.
     */
    record Navigator(
        Tier tier, TypeName focusType, ClassName navigatorClass, List<TypeName> typeArguments)
        implements FieldShape {

      public Navigator {
        typeArguments = List.copyOf(typeArguments);
      }
    }

    /** A published method naming a type this compilation cannot resolve. */
    record Unresolvable(TypeMirror type) implements FieldShape {}

    /** A companion with no method for the field that a navigator can compose. */
    record Unrecognised() implements FieldShape {}
  }

  /**
   * The shape of the static Focus method a navigation method composes.
   *
   * <p>A target annotated in this round has no companion to read yet, so its shape is worked out by
   * the same analysis its companion is about to be generated from. Any other target, from a
   * dependency or from an earlier round, already has one, so its shape is read from the method that
   * was actually published rather than worked out again: a dependency may have been built under a
   * different processor path, version or classpath, and composing anything but what it published
   * would name something that is not there.
   */
  private FieldShape fieldShape(
      TypeElement targetRecord, ClassName targetFocusClass, RecordComponentElement component) {
    if (navigableTypes.contains(targetRecord.getQualifiedName().toString())) {
      Widening widening = widening(targetRecord, component);
      Target target = navigatorTarget(targetRecord, component);
      return target == null
          ? new FieldShape.Path(widening.tier(), widening.focusType())
          : new FieldShape.Navigator(
              widening.tier(),
              widening.focusType(),
              targetFocusClass.nestedClass(
                  ProcessorUtils.capitalise(component.getSimpleName().toString()) + "Navigator"),
              typeNamesOf(target.type().getTypeArguments()));
    }
    return publishedShape(targetRecord, targetFocusClass, component.getSimpleName().toString());
  }

  /**
   * Reads the shape of a target's published static Focus method for one field. The companion is
   * known to exist and to be public: a target outside this round is navigable only once {@link
   * #reach} has found it so.
   *
   * <p>A generic target's method declares the target's type parameters, under the target's names,
   * and is read over the target instantiated with them. A navigator into it passes its own as the
   * method's type arguments and writes what the method returns in their names, so it composes only
   * a method whose type parameters carry those names, bounded no more tightly than its own.
   */
  private FieldShape publishedShape(
      TypeElement targetRecord, ClassName targetFocusClass, String fieldName) {
    TypeElement focus =
        processingEnv.getElementUtils().getTypeElement(targetFocusClass.canonicalName());
    return ElementFilter.methodsIn(focus.getEnclosedElements()).stream()
        .filter(
            method ->
                method.getSimpleName().contentEquals(fieldName)
                    && publicStatic(method)
                    && method.getParameters().isEmpty()
                    && declaresTargetParameters(method, targetRecord))
        .findFirst()
        .map(method -> shapeOf(focus, sourceOf(targetRecord, method), method.getReturnType()))
        .orElseGet(FieldShape.Unrecognised::new);
  }

  /**
   * Whether a published method declares the target's type parameters: the same names in the same
   * order, each bounded no more tightly than the target's own, so that the navigator's variables,
   * which carry the target's names and bounds, can be passed as its type arguments.
   */
  private boolean declaresTargetParameters(ExecutableElement method, TypeElement targetRecord) {
    Types types = processingEnv.getTypeUtils();
    List<? extends TypeParameterElement> declared = method.getTypeParameters();
    List<? extends TypeParameterElement> own = targetRecord.getTypeParameters();
    return namesOf(declared).equals(namesOf(own))
        && IntStream.range(0, own.size())
            .allMatch(
                i ->
                    declared.get(i).getBounds().stream()
                        .allMatch(
                            bound ->
                                own.get(i).getBounds().stream()
                                    .anyMatch(
                                        ownBound ->
                                            types.isSubtype(
                                                types.erasure(ownBound), types.erasure(bound)))));
  }

  /** The simple names of a list of type parameters, in order. */
  private static List<String> namesOf(List<? extends TypeParameterElement> parameters) {
    return parameters.stream().map(parameter -> parameter.getSimpleName().toString()).toList();
  }

  /**
   * The target a published method is over: the target instantiated with the method's own type
   * variables, which the caller has matched to the target's parameters one for one.
   */
  private DeclaredType sourceOf(TypeElement targetRecord, ExecutableElement method) {
    return processingEnv
        .getTypeUtils()
        .getDeclaredType(
            targetRecord,
            method.getTypeParameters().stream().map(Element::asType).toArray(TypeMirror[]::new));
  }

  /**
   * The shape a published method's return type describes: a path over the target, or a navigator
   * nested in its companion that generated code in another package can name and construct.
   *
   * @param focus the target's companion
   * @param source the target, as the method is over it
   * @param returned the method's return type
   */
  private FieldShape shapeOf(TypeElement focus, DeclaredType source, TypeMirror returned) {
    Optional<TypeMirror> unresolved = unresolvedPart(returned);
    if (unresolved.isPresent()) {
      return new FieldShape.Unresolvable(unresolved.orElseThrow());
    }
    if (!(returned instanceof DeclaredType declared)
        || !declared.asElement().getEnclosingElement().equals(focus)) {
      return pathShape(source, returned);
    }
    TypeElement navigator = (TypeElement) declared.asElement();
    if (!nameableNavigator(navigator, declared, source)) {
      return new FieldShape.Unrecognised();
    }
    // A navigator says which path it wraps through its own toPath(), and is composed as
    // `new Navigator<>(path)`, so it must take that path in a public constructor.
    Optional<ExecutableElement> toPath =
        ElementFilter.methodsIn(navigator.getEnclosedElements()).stream()
            .filter(
                method ->
                    method.getSimpleName().contentEquals("toPath")
                        && method.getModifiers().contains(Modifier.PUBLIC)
                        && method.getParameters().isEmpty())
            .findFirst();
    if (toPath.isEmpty()) {
      return new FieldShape.Unrecognised();
    }
    // The path it wraps is over its own first type parameter, so a navigator constructed from a
    // path over this navigator's source is over that source too. Any parameter it declares after
    // that one takes the type the composing method's return type names for it.
    TypeMirror wraps = toPath.orElseThrow().getReturnType();
    FieldShape wrapped = pathShape(navigator.getTypeParameters().getFirst().asType(), wraps);
    if (!(wrapped instanceof FieldShape.Path path)) {
      return wrapped;
    }
    // The focus is read under the instantiation the method returns, which writes it in the names
    // the navigator composing it declares.
    TypeMirror declaredFocus = ((DeclaredType) wraps).getTypeArguments().getLast();
    TypeMirror focusType =
        ((DeclaredType)
                ProcessorUtils.returnTypeIn(
                    processingEnv.getTypeUtils(), declared, toPath.orElseThrow()))
            .getTypeArguments()
            .getLast();
    List<? extends TypeMirror> arguments = declared.getTypeArguments();
    return constructibleFrom(navigator, wraps)
        ? new FieldShape.Navigator(
            path.tier(),
            ProcessorUtils.typeNameOf(focusType, declaredFocus, declared, analysis.targetPackage()),
            ClassName.get(navigator),
            typeNamesOf(arguments.subList(1, arguments.size())))
        : new FieldShape.Unrecognised();
  }

  /**
   * Whether a navigator class nested in a companion can be named as {@code Navigator<S, ...>} from
   * generated code in another package, and was published over the target.
   */
  private boolean nameableNavigator(
      TypeElement navigator, DeclaredType returned, DeclaredType source) {
    return navigator.getKind() == ElementKind.CLASS
        && publicStatic(navigator)
        && !returned.getTypeArguments().isEmpty()
        && processingEnv.getTypeUtils().isSameType(returned.getTypeArguments().getFirst(), source);
  }

  /**
   * Whether a navigator can be constructed from the path its {@code toPath()} returns: it is not
   * abstract, and a public constructor takes exactly that path.
   */
  private boolean constructibleFrom(TypeElement navigator, TypeMirror wraps) {
    Types types = processingEnv.getTypeUtils();
    return !navigator.getModifiers().contains(Modifier.ABSTRACT)
        && ElementFilter.constructorsIn(navigator.getEnclosedElements()).stream()
            .anyMatch(
                constructor ->
                    constructor.getModifiers().contains(Modifier.PUBLIC)
                        && constructor.getParameters().size() == 1
                        && types.isSameType(
                            constructor.getParameters().getFirst().asType(), wraps));
  }

  /** Whether a member can be named from generated code in another package without an instance. */
  private static boolean publicStatic(Element element) {
    return element.getModifiers().containsAll(Set.of(Modifier.PUBLIC, Modifier.STATIC));
  }

  /**
   * A FocusPath, AffinePath or TraversalPath from {@code source}, as the path shape it names. The
   * focus keeps its type-use annotations, so a {@code @Nullable} element reads as the dependency
   * wrote it.
   */
  private FieldShape pathShape(TypeMirror source, TypeMirror type) {
    Optional<TypeMirror> unresolved = unresolvedPart(type);
    if (unresolved.isPresent()) {
      return new FieldShape.Unresolvable(unresolved.orElseThrow());
    }
    if (!(type instanceof DeclaredType declared)
        || declared.getTypeArguments().size() != 2
        || !processingEnv
            .getTypeUtils()
            .isSameType(declared.getTypeArguments().getFirst(), source)) {
      return new FieldShape.Unrecognised();
    }
    String name = ((TypeElement) declared.asElement()).getQualifiedName().toString();
    return Arrays.stream(Tier.values())
        .filter(tier -> tier.pathClass().canonicalName().equals(name))
        .<FieldShape>map(
            tier ->
                new FieldShape.Path(
                    tier,
                    ProcessorUtils.typeNameOf(
                        declared.getTypeArguments().get(1), analysis.targetPackage())))
        .findFirst()
        .orElseGet(FieldShape.Unrecognised::new);
  }

  /** The first part of a type javac could not resolve, if it has one. */
  private static Optional<TypeMirror> unresolvedPart(TypeMirror type) {
    return switch (type.getKind()) {
      case ERROR -> Optional.of(type);
      case DECLARED ->
          ((DeclaredType) type)
              .getTypeArguments().stream()
                  .map(NavigatorClassGenerator::unresolvedPart)
                  .flatMap(Optional::stream)
                  .findFirst();
      case ARRAY -> unresolvedPart(((ArrayType) type).getComponentType());
      case WILDCARD -> {
        WildcardType wildcard = (WildcardType) type;
        TypeMirror bound =
            wildcard.getExtendsBound() != null
                ? wildcard.getExtendsBound()
                : wildcard.getSuperBound();
        yield bound == null ? Optional.empty() : unresolvedPart(bound);
      }
      default -> Optional.empty();
    };
  }

  /**
   * Returns the navigable element of an SPI container focused on a navigable type, or {@code null}
   * when the field is not one.
   *
   * <p>Every site that asks reads the answer from here, so the navigator class, the method's return
   * type and the wrapping decision cannot disagree about which fields have one. Hardcoded
   * Optional/Collection fields are excluded because they widen through their own path and never get
   * a navigator class.
   */
  private Target spiNavigable(TypeMirror fieldType, String fromPackage) {
    if (fieldType.getKind() != TypeKind.DECLARED || analysis.recognisedContainer(fieldType)) {
      return null;
    }
    // A container the analysis turns away gets no navigator: the static method it would compose
    // leaves the container itself in focus, so there is no element to navigate to.
    return analysis.spiLookup(fieldType) instanceof SpiLookup.Admitted admitted
        ? spiNavigableUnder(fieldType, admitted.generator(), fromPackage)
        : null;
  }

  /**
   * The navigable element a declared, non-hardcoded container focuses on under {@code generator},
   * or {@code null} when there is none.
   *
   * <p>Split from {@link #spiNavigable} so that {@link #widensUndenotableSpiContainer} can ask the
   * same question of a generator the lookup refused.
   */
  private Target spiNavigableUnder(
      TypeMirror fieldType, TraversableGenerator generator, String fromPackage) {
    TypeMirror innerType = spiElement(fieldType, generator);
    TypeElement element = innerType == null ? null : navigableTypeElement(innerType, fromPackage);
    return element == null ? null : new Target(element, (DeclaredType) innerType);
  }

  /**
   * The element type a declared, non-hardcoded container focuses on under {@code generator}, or
   * {@code null} when it names none.
   */
  private static TypeMirror spiElement(TypeMirror fieldType, TraversableGenerator generator) {
    List<? extends TypeMirror> typeArgs = ((DeclaredType) fieldType).getTypeArguments();
    int focusIdx = generator.getFocusTypeArgumentIndex();
    if (focusIdx >= typeArgs.size()) {
      return null; // a raw container carries no type argument
    }
    // The argument is resolved first: `Map<String, ? extends Address>` focuses on Address, and
    // an unbounded or super-bounded wildcard resolves to no type at all.
    return ProcessorUtils.resolveWildcard(typeArgs.get(focusIdx));
  }

  /**
   * Whether a record's include/exclude filters let this component have a navigator.
   *
   * <p>The filters are the declaring record's, so a navigation method composing another record's
   * Focus method reads the same answer that record's own companion did.
   */
  private static boolean shouldGenerateNavigator(
      TypeElement record, RecordComponentElement component) {
    String fieldName = component.getSimpleName().toString();
    GenerateFocus settings = focusSettings(record);
    List<String> includeFields = Arrays.asList(settings.includeFields());
    if (!includeFields.isEmpty()) {
      return includeFields.contains(fieldName);
    }
    return !Arrays.asList(settings.excludeFields()).contains(fieldName);
  }

  /** How a type relates to navigation. */
  private sealed interface Reach {

    /** A record whose Focus companion a navigator can compose. */
    record Navigable(TypeElement record) implements Reach {}

    /** A record outside this round that carries the annotation but has no companion to compose. */
    record Unpublished(TypeElement record) implements Reach {}

    /** A record whose navigator would name {@code hidden}, which this package cannot reach. */
    record Hidden(TypeElement record, TypeElement hidden) implements Reach {}

    /**
     * An annotated record a component gives a wildcard or no type arguments, so that no navigator
     * into it can be instantiated. Only {@code skippedTarget} answers this, never {@code reach}.
     */
    record Uninstantiable(Target target) implements Reach {}

    /** Anything else. */
    record None() implements Reach {}
  }

  /**
   * How a type relates to navigation: navigable, a record whose companion cannot be composed, or
   * neither.
   *
   * <p>Two questions, and both are load-bearing. {@code navigableTypes} holds the records annotated
   * in this round, whose {@code Focus} classes this round is about to write and so cannot yet look
   * up. A type outside that set, from a dependency or from an earlier round, is answered by the
   * annotation instead, which is why {@link GenerateFocus} is retained in the class file: a record
   * in one module stays navigable from another's {@code Focus}.
   *
   * <p>Such a type is held to what was actually published. Navigating into it composes its {@code
   * Focus} class from generated code in another package, so a record whose module did not run the
   * processor, or whose companion is not public, has nothing to compose and is unpublished rather
   * than navigable. Only a record can be navigable, as in-round: the annotation is refused on
   * anything else where it is declared, so a class file carrying it elsewhere came from a module
   * that never checked.
   */
  private Reach reach(TypeMirror type, String fromPackage) {
    Reach reach = published(type);
    if (reach instanceof Reach.Navigable(TypeElement record)) {
      // A navigator names the target and the type of each component it steps to, from the
      // package of the companion it belongs to: the one being written, or, for a target record,
      // the one that record's own processor run writes.
      Optional<TypeElement> hidden =
          Reachability.firstHidden(
              processingEnv.getElementUtils(),
              fromPackage,
              Reachability.record(record, record.getRecordComponents()));
      if (hidden.isPresent()) {
        return new Reach.Hidden(record, hidden.get());
      }
    }
    return reach;
  }

  /** Whether a type is navigable, published or neither, before asking what its navigator names. */
  private Reach published(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return new Reach.None();
    }
    TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
    if (navigableTypes.contains(typeElement.getQualifiedName().toString())) {
      return new Reach.Navigable(typeElement);
    }
    if (typeElement.getKind() != ElementKind.RECORD
        || !(typeElement.getAnnotation(GenerateFocus.class) instanceof GenerateFocus settings)) {
      return new Reach.None();
    }
    TypeElement companion =
        processingEnv
            .getElementUtils()
            .getTypeElement(focusClassOf(typeElement, settings).canonicalName());
    return companion != null && companion.getModifiers().contains(Modifier.PUBLIC)
        ? new Reach.Navigable(typeElement)
        : new Reach.Unpublished(typeElement);
  }

  /**
   * Returns the element of a navigable type, or {@code null} when the type is not navigable.
   *
   * <p>Navigability and the element are answered together because they are never useful apart: only
   * a declared type can be navigable, so a caller holding a navigable type already holds its
   * element.
   */
  private TypeElement navigableTypeElement(TypeMirror type, String fromPackage) {
    return reach(type, fromPackage) instanceof Reach.Navigable navigable
        ? navigable.record()
        : null;
  }

  /**
   * Whether a navigator for {@code component} would have stepped into an SPI container whose type
   * arguments leave the optic instance undenotable.
   *
   * <p>Answered here because the navigator generator decides which fields get a navigator. Such a
   * field is handed back to the static method, and {@code FocusProcessor} asks the analysis to step
   * into the container on the navigator's behalf, so that it is met and turned away, and the
   * declaration is rejected from the same result that method is built from.
   *
   * @param component the record component to inspect
   * @return true when only the type arguments stand between this component and a navigator
   */
  boolean widensUndenotableSpiContainer(RecordComponentElement component) {
    TypeMirror fieldType = component.asType();
    TypeElement record = (TypeElement) component.getEnclosingElement();
    // The guards run in the order the navigator itself decides: a filtered-out or directly
    // navigable field never reaches the SPI question, and Optional and the collections widen
    // through their own path.
    if (!shouldGenerateNavigator(record, component)
        || fieldType.getKind() != TypeKind.DECLARED
        || navigableTypeElement(fieldType, companionPackage(record)) != null
        || analysis.recognisedContainer(fieldType)) {
      return false;
    }
    if (!(analysis.spiLookup(fieldType) instanceof SpiLookup.Refused refused)) {
      return false;
    }
    // The element must be one a navigator is offered for. One the container gives a wildcard or
    // a raw type gets none whatever the container's own arguments, so that container is left
    // alone as it would have been anyway.
    Target navigable = spiNavigableUnder(fieldType, refused.generator(), companionPackage(record));
    return navigable != null && navigable.instantiable();
  }

  /**
   * Creates a method spec for a navigator-returning method (replaces the standard FocusPath
   * method).
   *
   * @param component the record component
   * @param recordElement the record being processed
   * @param recordTypeName the record's type name
   * @return the method spec
   */
  public MethodSpec createNavigatorMethod(
      RecordComponentElement component, TypeElement recordElement, TypeName recordTypeName) {

    String componentName = component.getSimpleName().toString();

    // A component whose type reaches a navigable one it can instantiate a navigator for — directly,
    // or as the element of an SPI container — gets this navigator method in place of the plain path
    // method. Hardcoded Optional/Collection fields are not among them: createFocusPathMethod widens
    // those through .some()/.each() instead.
    Target target = navigatorTarget(recordElement, component);
    if (target == null) {
      return null; // Not navigable, or filtered out: use the standard method
    }

    String navigatorClassName = ProcessorUtils.capitalise(componentName) + "Navigator";
    ClassName navigatorClass = focusClassOf(recordElement).nestedClass(navigatorClassName);
    ParameterizedTypeName returnType =
        navigatorType(
            navigatorClass, recordTypeName, typeNamesOf(target.type().getTypeArguments()));
    TypeName javadocTargetType = ClassName.get(target.record());

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(componentName)
            .addJavadoc(
                "Creates a navigator for the {@code $L} field of a {@link $T}.\n\n"
                    + "<p>The returned navigator enables fluent navigation into the fields of\n"
                    + "{@link $T}. For example:\n"
                    + "<pre>{@code\n"
                    + "$L.$L().fieldName().get(instance);\n"
                    + "}</pre>\n\n"
                    + "@return A navigator for the {@code $L} field.",
                componentName,
                recordTypeName,
                javadocTargetType,
                recordElement.getSimpleName() + "Focus",
                componentName,
                componentName)
            // The navigator type and the record's type-parameter bounds are written out here.
            .addAnnotations(ProcessorUtils.rawTypesSuppression(component.asType(), recordElement))
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(returnType);

    // Add type parameters if the record is generic
    for (TypeParameterElement typeParam : recordElement.getTypeParameters()) {
      methodBuilder.addTypeVariable(
          ProcessorUtils.typeVariableOf(typeParam, analysis.targetPackage()));
    }

    // Build the constructor arguments for the setter lambda
    String constructorArgs = ProcessorUtils.rebuildArguments(recordElement, componentName);

    // The widening is the one the static Focus method would have carried, from the same analysis,
    // and the component name rides along as the path's field-name segment so that a navigated path
    // self-locates the way a static one does (issue #592).
    List<Object> args =
        new ArrayList<>(
            List.of(
                navigatorClassName,
                WideningAnalysis.FOCUS_PATH_CLASS,
                Lens.class,
                recordTypeName,
                componentName,
                recordTypeName,
                constructorArgs,
                componentName));
    // The declaration pass reaches this component only here, so this walk is the one that reports
    // a generator conflict on it; every other walk into the component stays silent.
    String wideningExpression =
        WideningAnalysis.expression(
            declaredWidening(recordElement, component).steps(), args, analysis.targetPackage());
    methodBuilder.addStatement(
        "return new $L<>($T.of($T.of($T::$L, (source, newValue) -> new $T($L)), \"$L\")"
            + wideningExpression
            + ")",
        args.toArray());

    return methodBuilder.build();
  }
}
