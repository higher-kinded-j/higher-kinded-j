// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFocus;
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
 * for the same declaration by construction (issue #719). {@link WideningAnalysis} is consulted only
 * for what that method returns, so that this one can declare it.
 */
public class NavigatorClassGenerator {

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
   * is generating for, or a navigable target, which is navigable because it carries the annotation.
   */
  private static GenerateFocus focusSettings(TypeElement record) {
    return record.getAnnotation(GenerateFocus.class);
  }

  /** The Focus companion class a record generates, honouring a redirected target package. */
  private ClassName focusClassOf(TypeElement record) {
    String targetPackage = focusSettings(record).targetPackage();
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
      // The two reasons a candidate gets no navigator are told apart here, because only one of
      // them is the component's target being generic; navigatorTarget conflates them by design.
      TypeElement candidate = navigatorCandidate(recordElement, component);
      if (candidate == null) {
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
   * Every other reason a component has no navigator is visible in what it is: not navigable, or
   * filtered out by the record's own include/exclude.
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

    TypeSpec.Builder navigatorBuilder =
        TypeSpec.classBuilder(navigatorClassName)
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
    addNavigationMethods(navigatorBuilder, targetRecord, sourceTypeVar, currentDepth + 1, tier);

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
   */
  private void addNavigationMethods(
      TypeSpec.Builder navigatorBuilder,
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
                component);
        continue;
      }

      Widening widening = widening(targetRecord, component);
      Tier composed = currentTier.widen(widening.tier());
      TypeElement navigatorTarget = navigatorTarget(targetRecord, component);

      MethodSpec.Builder methodBuilder =
          MethodSpec.methodBuilder(fieldName)
              .addModifiers(Modifier.PUBLIC)
              .returns(
                  ParameterizedTypeName.get(
                      composed.pathClass(), sourceTypeVar, widening.focusType()))
              .addJavadoc(
                  "Navigates to the {@code $L} field.\n\n"
                      + "@return a $L focusing on the {@code $L} field",
                  fieldName,
                  composed.description(),
                  fieldName);

      if (navigatorTarget == null) {
        methodBuilder.addStatement("return delegate.via($T.$L())", targetFocusClass, fieldName);
      } else {
        addNavigatorComposition(
            methodBuilder,
            targetFocusClass,
            fieldName,
            sourceTypeVar,
            currentDepth,
            composed,
            widening.tier());
      }

      navigatorBuilder.addMethod(methodBuilder.build());
    }
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
      ClassName targetFocusClass,
      String fieldName,
      TypeVariableName sourceTypeVar,
      int currentDepth,
      Tier composed,
      Tier fieldTier) {

    if (currentDepth < maxDepth && composed == fieldTier) {
      ClassName navigatorClass =
          targetFocusClass.nestedClass(ProcessorUtils.capitalise(fieldName) + "Navigator");
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
    return spiNavigableUnder(fieldType, analysis.wideningGenerator(fieldType, null));
  }

  /**
   * The navigable element a declared, non-hardcoded container focuses on under {@code generator},
   * or {@code null} when there is none.
   *
   * <p>Split from {@link #spiNavigable} so that {@link #widensUndenotableSpiContainer} can ask the
   * same question of a generator the widening guard has already turned away.
   */
  private SpiNavigable spiNavigableUnder(TypeMirror fieldType, TraversableGenerator generator) {
    if (generator == null) {
      // A Collection subtype such as ArrayList is widened by the interface walk rather than by a
      // generator, so there is no focus type argument to read.
      return null;
    }
    List<? extends TypeMirror> typeArgs = ((DeclaredType) fieldType).getTypeArguments();
    int focusIdx = generator.getFocusTypeArgumentIndex();
    if (focusIdx >= typeArgs.size()) {
      return null; // a raw container carries no type argument
    }
    // The argument is resolved first: `Map<String, ? extends Address>` focuses on Address, and
    // an unbounded or super-bounded wildcard resolves to no type at all.
    TypeMirror innerType = ProcessorUtils.resolveWildcard(typeArgs.get(focusIdx));
    TypeElement element = innerType == null ? null : navigableTypeElement(innerType);
    return element == null ? null : new SpiNavigable(generator, element);
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

  /**
   * Returns the element of a navigable type, or {@code null} when the type is not navigable.
   *
   * <p>Navigability and the element are answered together because they are never useful apart: only
   * a declared type can be navigable, so a caller holding a navigable type already holds its
   * element.
   */
  // Package-private for tests: the annotation fallback below is unreachable in production.
  TypeElement navigableTypeElement(TypeMirror type) {
    if (type.getKind() != TypeKind.DECLARED) {
      return null;
    }
    TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
    boolean navigable =
        navigableTypes.contains(typeElement.getQualifiedName().toString())
            || typeElement.getAnnotation(GenerateFocus.class) != null;
    return navigable ? typeElement : null;
  }

  /**
   * Whether a navigator for {@code component} would widen through an SPI container whose type
   * arguments leave the optic instance undenotable.
   *
   * <p>Answered here because the navigator generator decides which fields get a navigator; the
   * diagnostic is reported by {@code FocusProcessor}, which sees each component once.
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
    TraversableGenerator generator = analysis.findSpiGenerator(fieldType, null);
    return generator != null
        && WideningAnalysis.widensUndenotably(generator, fieldType)
        && spiNavigableUnder(fieldType, generator) != null;
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
