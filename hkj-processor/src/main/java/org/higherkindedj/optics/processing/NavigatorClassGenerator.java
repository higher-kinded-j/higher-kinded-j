// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import org.higherkindedj.optics.processing.spi.Cardinality;
import org.higherkindedj.optics.processing.spi.TraversableGenerator;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

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
   * described was generated under them.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return the widening its Focus method carries
   */
  private Widening widening(TypeElement record, RecordComponentElement component) {
    return analysis.analyse(component, widensContainers(record, component));
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
   * declined — the element is generic, the record turned navigators off, the field is filtered out
   * — reaches that same disagreement.
   */
  private boolean widensContainers(TypeElement record, RecordComponentElement component) {
    return (spiNavigable(component.asType()) != null && navigatorTarget(record, component) != null)
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
      // The reasons a candidate gets no navigator are told apart here, because only some of them
      // show in the declaration; navigatorTarget conflates them by design.
      TypeElement candidate = navigatorCandidate(recordElement, component);
      if (candidate == null) {
        TypeElement unpublished = unpublishedCandidate(recordElement, component);
        if (unpublished != null) {
          reportUnpublishedTargetSkipped(recordElement, component, unpublished);
        }
        continue;
      }
      if (declaresTypeParameters(candidate)) {
        reportGenericTargetSkipped(recordElement, component, candidate);
        continue;
      }
      focusClassBuilder.addType(
          generateNavigatorClass(
              component, candidate, currentDepth, widening(recordElement, component).tier()));
    }
  }

  /**
   * Says why a component asking for a navigator did not get one, when the reason is that its target
   * is generic.
   *
   * <p>The declaring record asked for navigators and gets one fewer than the components suggest,
   * which is the same surprise a delegate-name collision produces and is reported the same way.
   * Most other reasons a component has no navigator are visible in what it is: not navigable, or
   * filtered out by the record's own include/exclude. The one that is not, a target with no
   * companion to compose, is reported the same way.
   *
   * @param recordElement the record declaring the component
   * @param component the component whose navigator was not generated
   * @param navigable the generic target it reaches, already established by the caller
   */
  private void reportGenericTargetSkipped(
      TypeElement recordElement, RecordComponentElement component, TypeElement navigable) {

    String componentName = component.getSimpleName().toString();
    String chain =
        focusClassOf(recordElement).simpleName()
            + "."
            + componentName
            + "().via("
            + focusClassOf(navigable).simpleName()
            + ".…())";
    processingEnv
        .getMessager()
        .printMessage(
            Diagnostic.Kind.NOTE,
            "Navigator for field '"
                + componentName
                + "' is not generated: "
                + navigable.getSimpleName()
                + " declares type parameters, which a navigator has no way to name. "
                + (keepsContainerInFocus(recordElement, component)
                    ? "Its Focus method keeps the container itself in focus, so add"
                        + " widenCollections = true to step into it, then chain "
                        + chain
                        + " through the element."
                    : "Use " + chain + " to chain through it."),
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
   * The record a component would have a navigator for, were its companion published: it reaches,
   * directly or as an SPI container's element, a non-generic record from a dependency that carries
   * {@code @GenerateFocus} but has no companion this module can compose.
   *
   * @param record the record that declares the component
   * @param component the component, which has no navigator
   * @return that record, or null when the component reaches none
   */
  private TypeElement unpublishedCandidate(TypeElement record, RecordComponentElement component) {
    if (!shouldGenerateNavigator(record, component)) {
      return null;
    }
    TypeMirror fieldType = component.asType();
    TypeMirror reached =
        fieldType.getKind() == TypeKind.DECLARED
                && !analysis.recognisedContainer(fieldType)
                && analysis.spiLookup(fieldType, null) instanceof SpiLookup.Admitted admitted
            ? spiElement(fieldType, admitted.generator())
            : fieldType;
    return reached != null
            && reach(reached) instanceof Reach.Unpublished unpublished
            && !declaresTypeParameters(unpublished.record())
        ? unpublished.record()
        : null;
  }

  /**
   * Whether a component's Focus method stops at its container rather than the element inside it.
   *
   * <p>A {@code ZERO_OR_MORE} SPI container is left un-widened unless the record asks for it, so
   * its path is focused on the container and composes with nothing the element declares. Every
   * other navigable shape — a direct field, a built-in collection, an {@code Optional}, a {@code
   * ZERO_OR_ONE} container — is already stepped into by the time the note is written, and chains
   * through the element as written.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return true when the container is still in focus, so the element is a step further on
   */
  private boolean keepsContainerInFocus(TypeElement record, RecordComponentElement component) {
    SpiNavigable spiNavigable = spiNavigable(component.asType());
    return spiNavigable != null
        && spiNavigable.generator().getCardinality() == Cardinality.ZERO_OR_MORE
        && !focusSettings(record).widenCollections();
  }

  /**
   * The navigable type a component reaches, generic or not, before the navigator question is asked
   * of it.
   *
   * @param component the component to read
   * @return the navigable type it reaches, or null when it reaches none
   */
  private TypeElement navigableTarget(RecordComponentElement component) {
    TypeMirror fieldType = component.asType();
    TypeElement direct = navigableTypeElement(fieldType);
    if (direct != null) {
      return direct;
    }
    SpiNavigable spiNavigable = spiNavigable(fieldType);
    return spiNavigable == null ? null : spiNavigable.element();
  }

  /**
   * The type a record's Focus method for this component navigates to, or null when that method
   * hands back a path instead.
   *
   * <p>A component reaches a navigator by being a navigable type itself, or by being an SPI
   * container of one, and in either case only when that type declares no type parameters of its
   * own. Every site that asks — the navigator class, the method that returns it, and a navigation
   * method composing it from another record — reads the answer from here, so they cannot disagree
   * about which components have one.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return the navigable type its Focus method reaches, or null
   */
  private TypeElement navigatorTarget(TypeElement record, RecordComponentElement component) {
    TypeElement candidate = navigatorCandidate(record, component);
    return candidate == null || declaresTypeParameters(candidate) ? null : candidate;
  }

  /**
   * The navigable type a component would get a navigator for, before its own type parameters are
   * considered.
   *
   * <p>Split from {@link #navigatorTarget} so that the note explaining a generic target and the
   * gate declining it read one answer rather than two. Re-deriving it would report the target's
   * genericity as the reason a field the record itself filtered out has no navigator, which is a
   * reason its author cannot act on.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return the navigable type it reaches while asking for a navigator, or null
   */
  private TypeElement navigatorCandidate(TypeElement record, RecordComponentElement component) {
    if (!focusSettings(record).generateNavigators()
        || !shouldGenerateNavigator(record, component)) {
      return null;
    }
    return navigableTarget(component);
  }

  /**
   * Whether a navigable type declares type parameters of its own.
   *
   * <p>A navigator is an inner class parameterised by the source type alone, and its navigation
   * methods read the target's components from the target's own declaration. Both would name the
   * target's variables, which are in scope on neither. The component keeps its plain path method,
   * which carries the instantiation and composes the same way.
   *
   * @param navigable the navigable type the component reaches
   * @return true when it declares type parameters
   */
  private static boolean declaresTypeParameters(TypeElement navigable) {
    return !navigable.getTypeParameters().isEmpty();
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

    // Type parameter S for the source type in the navigator
    TypeVariableName sourceTypeVar = TypeVariableName.get("S");

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

    // A nested type is its own class file, and a coverage tool reads the marker there.
    TypeSpec.Builder navigatorBuilder =
        TypeSpec.classBuilder(navigatorClassName)
            .addAnnotation(GENERATED)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .addTypeVariable(sourceTypeVar)
            .addJavadoc(
                "Navigator for fluent access to {@code $L} fields.\n\n"
                    + "<p>This navigator wraps a {@link $T} and provides direct navigation methods\n"
                    + "for all fields of {@link $T}.\n\n"
                    + "<p>Path type: $L\n\n"
                    + "@param <S> the source type at the root of the navigation",
                componentName,
                pathClass,
                targetTypeName,
                tierDescription);

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

      switch (fieldShape(targetRecord, targetFocusClass, component)) {
        case FieldShape.Unresolvable unresolvable ->
            reportUnresolvableField(owner, targetFocusClass, fieldName, unresolvable.type());
        case FieldShape.Unrecognised _ ->
            reportUnrecognisedField(owner, targetRecord, targetFocusClass, fieldName);
        case FieldShape.Path path ->
            navigatorBuilder.addMethod(
                navigationMethod(
                        fieldName, sourceTypeVar, currentTier.widen(path.tier()), path.focusType())
                    .addStatement("return delegate.via($T.$L())", targetFocusClass, fieldName)
                    .build());
        case FieldShape.Navigator navigator -> {
          Tier composed = currentTier.widen(navigator.tier());
          MethodSpec.Builder methodBuilder =
              navigationMethod(fieldName, sourceTypeVar, composed, navigator.focusType());
          addNavigatorComposition(
              methodBuilder,
              navigator.navigatorClass(),
              targetFocusClass,
              fieldName,
              sourceTypeVar,
              currentDepth,
              composed,
              navigator.tier());
          navigatorBuilder.addMethod(methodBuilder.build());
        }
      }
    }
  }

  /** A navigation method's declaration: its name, the path it returns, and its javadoc. */
  private static MethodSpec.Builder navigationMethod(
      String fieldName, TypeVariableName sourceTypeVar, Tier composed, TypeName focusType) {
    return MethodSpec.methodBuilder(fieldName)
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
                + type
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
   */
  private void addNavigatorComposition(
      MethodSpec.Builder methodBuilder,
      ClassName navigatorClass,
      ClassName targetFocusClass,
      String fieldName,
      TypeVariableName sourceTypeVar,
      int currentDepth,
      Tier composed,
      Tier fieldTier) {

    if (currentDepth < maxDepth && composed == fieldTier) {
      methodBuilder.returns(ParameterizedTypeName.get(navigatorClass, sourceTypeVar));
      methodBuilder.addStatement(
          "return new $T<>(delegate.via($T.$L().toPath()))",
          navigatorClass,
          targetFocusClass,
          fieldName);
      return;
    }
    methodBuilder.addStatement(
        "return delegate.via($T.$L().toPath())", targetFocusClass, fieldName);
  }

  /**
   * What a target record's static Focus method for one field hands back, or why a navigation method
   * cannot compose it from this compilation.
   */
  private sealed interface FieldShape {

    /** A method returning a plain path of this tier and focus. */
    record Path(Tier tier, TypeName focusType) implements FieldShape {}

    /** A method returning a navigator that wraps a path of this tier and focus. */
    record Navigator(Tier tier, TypeName focusType, ClassName navigatorClass)
        implements FieldShape {}

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
      return navigatorTarget(targetRecord, component) == null
          ? new FieldShape.Path(widening.tier(), widening.focusType())
          : new FieldShape.Navigator(
              widening.tier(),
              widening.focusType(),
              targetFocusClass.nestedClass(
                  ProcessorUtils.capitalise(component.getSimpleName().toString()) + "Navigator"));
    }
    return publishedShape(targetRecord, targetFocusClass, component.getSimpleName().toString());
  }

  /**
   * Reads the shape of a target's published static Focus method for one field. The companion is
   * known to exist and to be public: a target outside this round is navigable only once {@link
   * #reach} has found it so.
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
                    && method.getTypeParameters().isEmpty())
        .findFirst()
        .map(method -> shapeOf(focus, targetRecord, method.getReturnType()))
        .orElseGet(FieldShape.Unrecognised::new);
  }

  /**
   * The shape a published method's return type describes: a path over the target, or a navigator
   * nested in its companion that generated code in another package can name and construct.
   */
  private FieldShape shapeOf(TypeElement focus, TypeElement targetRecord, TypeMirror returned) {
    Optional<TypeMirror> unresolved = unresolvedPart(returned);
    if (unresolved.isPresent()) {
      return new FieldShape.Unresolvable(unresolved.orElseThrow());
    }
    if (!(returned instanceof DeclaredType declared)
        || !declared.asElement().getEnclosingElement().equals(focus)) {
      return pathShape(targetRecord.asType(), returned);
    }
    TypeElement navigator = (TypeElement) declared.asElement();
    if (!nameableNavigator(navigator, declared, targetRecord)) {
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
    TypeMirror wraps = toPath.orElseThrow().getReturnType();
    FieldShape wrapped = pathShape(navigator.getTypeParameters().getFirst().asType(), wraps);
    if (!(wrapped instanceof FieldShape.Path path)) {
      return wrapped;
    }
    return constructibleFrom(navigator, wraps)
        ? new FieldShape.Navigator(path.tier(), path.focusType(), ClassName.get(navigator))
        : new FieldShape.Unrecognised();
  }

  /**
   * Whether a navigator class nested in a companion can be named as {@code Navigator<S>} from
   * generated code in another package, and was published over the target.
   */
  private boolean nameableNavigator(
      TypeElement navigator, DeclaredType returned, TypeElement targetRecord) {
    return navigator.getKind() == ElementKind.CLASS
        && publicStatic(navigator)
        && navigator.getTypeParameters().size() == 1
        && returned.getTypeArguments().size() == 1
        && processingEnv
            .getTypeUtils()
            .isSameType(returned.getTypeArguments().getFirst(), targetRecord.asType());
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
                    tier, ProcessorUtils.typeNameOf(declared.getTypeArguments().get(1))))
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

  /** An SPI container's generator, paired with the navigable element it focuses on. */
  private record SpiNavigable(TraversableGenerator generator, TypeElement element) {}

  /**
   * Returns the generator and navigable element of an SPI container focused on a navigable type, or
   * {@code null} when the field is not one.
   *
   * <p>Every site that asks reads the answer from here, so the navigator class, the method's return
   * type and the wrapping decision cannot disagree about which fields have one. Hardcoded
   * Optional/Collection fields are excluded because they widen through their own path and never get
   * a navigator class.
   */
  private SpiNavigable spiNavigable(TypeMirror fieldType) {
    if (fieldType.getKind() != TypeKind.DECLARED || analysis.recognisedContainer(fieldType)) {
      return null;
    }
    // A container the analysis turns away gets no navigator: the static method it would compose
    // leaves the container itself in focus, so there is no element to navigate to.
    return analysis.spiLookup(fieldType, null) instanceof SpiLookup.Admitted admitted
        ? spiNavigableUnder(fieldType, admitted.generator())
        : null;
  }

  /**
   * The navigable element a declared, non-hardcoded container focuses on under {@code generator},
   * or {@code null} when there is none.
   *
   * <p>Split from {@link #spiNavigable} so that {@link #widensUndenotableSpiContainer} can ask the
   * same question of a generator the lookup refused.
   */
  private SpiNavigable spiNavigableUnder(TypeMirror fieldType, TraversableGenerator generator) {
    TypeMirror innerType = spiElement(fieldType, generator);
    TypeElement element = innerType == null ? null : navigableTypeElement(innerType);
    return element == null ? null : new SpiNavigable(generator, element);
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
  private Reach reach(TypeMirror type) {
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
  private TypeElement navigableTypeElement(TypeMirror type) {
    return reach(type) instanceof Reach.Navigable navigable ? navigable.record() : null;
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
        || navigableTypeElement(fieldType) != null
        || analysis.recognisedContainer(fieldType)) {
      return false;
    }
    if (!(analysis.spiLookup(fieldType, null) instanceof SpiLookup.Refused refused)) {
      return false;
    }
    // The element must be one a navigator is offered for. A generic element gets none whatever
    // the container's arguments, so that container is left alone as it would have been anyway.
    SpiNavigable navigable = spiNavigableUnder(fieldType, refused.generator());
    return navigable != null && !declaresTypeParameters(navigable.element());
  }

  /**
   * Creates a method spec for a navigator-returning method (replaces the standard FocusPath
   * method).
   *
   * @param component the record component
   * @param recordElement the record being processed
   * @param allComponents all components of the record
   * @param recordTypeName the record's type name
   * @return the method spec
   */
  public MethodSpec createNavigatorMethod(
      RecordComponentElement component,
      TypeElement recordElement,
      List<? extends RecordComponentElement> allComponents,
      TypeName recordTypeName) {

    String componentName = component.getSimpleName().toString();

    // A component whose type reaches a non-generic navigable one — directly, or as the element of
    // an SPI container — gets this navigator method in place of the plain path method. Hardcoded
    // Optional/Collection fields are not among them: createFocusPathMethod widens those through
    // .some()/.each() instead.
    TypeElement target = navigatorTarget(recordElement, component);
    if (target == null) {
      return null; // Not navigable, or filtered out: use the standard method
    }

    String navigatorClassName = ProcessorUtils.capitalise(componentName) + "Navigator";
    ClassName navigatorClass = focusClassOf(recordElement).nestedClass(navigatorClassName);
    ParameterizedTypeName returnType = ParameterizedTypeName.get(navigatorClass, recordTypeName);
    TypeName javadocTargetType = TypeName.get(target.asType());

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
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(returnType);

    // Add type parameters if the record is generic
    for (TypeParameterElement typeParam : recordElement.getTypeParameters()) {
      methodBuilder.addTypeVariable(ProcessorUtils.typeVariableOf(typeParam));
    }

    // Build the constructor arguments for the setter lambda
    String constructorArgs =
        allComponents.stream()
            .map(
                c ->
                    c.getSimpleName().toString().equals(componentName)
                        ? "newValue"
                        : "source." + c.getSimpleName() + "()")
            .collect(Collectors.joining(", "));

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
    String wideningExpression =
        WideningAnalysis.expression(widening(recordElement, component).steps(), args);
    methodBuilder.addStatement(
        "return new $L<>($T.of($T.of($T::$L, (source, newValue) -> new $T($L)), \"$L\")"
            + wideningExpression
            + ")",
        args.toArray());

    return methodBuilder.build();
  }
}
