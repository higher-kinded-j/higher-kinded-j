// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
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
import org.higherkindedj.optics.processing.WideningAnalysis.Step;
import org.higherkindedj.optics.processing.WideningAnalysis.Tier;
import org.higherkindedj.optics.processing.WideningAnalysis.Widening;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.Reachability;

/**
 * Generates the navigators {@code @GenerateFocus} adds to a Focus companion for fluent cross-type
 * navigation.
 *
 * <p>A navigator is a path: it extends {@code NavigatorFocusPath}, {@code NavigatorAffinePath} or
 * {@code NavigatorTraversalPath}, which forward every path method, final, to the path it wraps, and
 * it adds a navigation method for each field of the record it reaches. So it goes wherever a path
 * of its tier goes, and a field's method returns a subtype of what it returns without navigators.
 *
 * <p>For example, given:
 *
 * <pre>{@code
 * @GenerateFocus
 * record Company(String name, Address headquarters, @Nullable Address backup) {}
 *
 * @GenerateFocus
 * record Address(String street, String city) {}
 * }</pre>
 *
 * <p>This generator creates navigators that return the appropriate path types:
 *
 * <ul>
 *   <li>{@code headquarters().city()} returns {@code FocusPath<Company, String>}
 *   <li>{@code backup().city()} returns {@code AffinePath<Company, String>}
 * </ul>
 *
 * <p>A navigator never changes what a field's method focuses on: it is offered only over that
 * focus, so a container of navigable records gets one only where the record widens it. A field of
 * the target named like a no-argument method the navigator inherits has no navigation method it
 * could take, and is refused.
 *
 * <p>A navigation method never works a container's widening out for itself: it composes the static
 * Focus method the target record's own companion generated, so the two report the same path type
 * for the same declaration by construction (issue #719). For a target annotated in this round,
 * {@link WideningAnalysis} says what that method is about to return; for any other target, the
 * return type is read from the method its companion actually published.
 *
 * <p>Each navigator has a form nested in it for each tier wider than its own, {@code AffineForm} or
 * {@code TraversalForm}: the same navigator over that wider path. Composing a field's path with a
 * wider one lands on the wider tier, which the field's own navigator cannot hold, so a navigation
 * method hands on the form of that tier instead, and {@code orders().customer().email()} keeps
 * navigating after the hop into one customer. A form is left out where its path has a no-argument
 * method named like a field of the target, or where the target has a type parameter of the form's
 * name, and a hop that would need it returns the composed path. A record that writes {@code
 * generateNavigators = true} is told about the first, with the field to rename.
 *
 * <p>A target with type parameters of its own gets a navigator that declares them after the source,
 * {@code InnerNavigator<S, T>}, and a component reaching it passes the type arguments it was
 * declared with: {@code Inner<String> inner} gives {@code InnerNavigator<Outer, String>}. A
 * component with none it can pass, a wildcard ({@code Inner<?>}) or a raw {@code Inner}, gets no
 * navigator, and its method is the path its declaration widens to.
 */
public class NavigatorClassGenerator {

  private static final ClassName GENERATED =
      ClassName.get("org.higherkindedj.optics.annotations", "Generated");

  private final ProcessingEnvironment processingEnv;
  private final Set<String> navigableTypes;
  private final int maxDepth;
  private final WideningAnalysis analysis;

  /** The no-argument methods each tier's navigator inherits, read once per generator. */
  private final Map<Tier, Set<String>> inheritedNoArgumentMethods = new EnumMap<>(Tier.class);

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
    return analysis.analyse(component, widensCollections(record, component));
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
    return analysis.analyseDeclaration(component, widensCollections(record, component));
  }

  /**
   * Whether a record widens a component's {@code ZERO_OR_MORE} container: every one under {@code
   * widenCollections}, or this one when {@code widenFields} names it. The static Focus method and
   * the navigators read the same answer.
   *
   * <p>The declaring record decides, and nothing else does: a navigator steps into a container only
   * where the field is widened anyway, so turning navigators on never changes what a field's method
   * focuses on.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return true when the container is widened
   */
  static boolean widensCollections(TypeElement record, RecordComponentElement component) {
    GenerateFocus settings = focusSettings(record);
    return settings.widenCollections()
        || Arrays.asList(settings.widenFields()).contains(component.getSimpleName().toString());
  }

  /**
   * Whether a record's {@code @GenerateFocus} names a constant javac has not resolved, which reads
   * as a value of the wrong type. javac reports the constant itself, and neither the record's own
   * companion nor a navigator into it can be generated from settings that cannot be read, so the
   * processor writes no companion for the record and treats it as not navigable. Every attribute is
   * read, so one added later is covered too.
   *
   * @param settings the annotation as javac reads it
   * @return true when an attribute cannot be read
   */
  static boolean unreadable(GenerateFocus settings) {
    try {
      for (Method attribute : GenerateFocus.class.getDeclaredMethods()) {
        attribute.invoke(settings);
      }
      return false;
    } catch (ReflectiveOperationException e) {
      return true;
    }
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
          case Reach.Uninstantiable(Target uninstantiable, Reach instantiated) ->
              reportUninstantiableTargetSkipped(
                  recordElement, component, uninstantiable, instantiated);
          case Reach.Unpublished(TypeElement unpublished) ->
              reportUnpublishedTargetSkipped(recordElement, component, unpublished);
          case Reach.Hidden hidden -> reportHiddenTargetSkipped(recordElement, component, hidden);
          default -> warnIncludedWithoutNavigator(recordElement, component);
        }
        continue;
      }
      Tier tier = widening(recordElement, component).tier();
      focusClassBuilder.addType(generateNavigatorClass(component, target, currentDepth, tier));
      noteMissingForms(recordElement, component, target, tier);
    }
  }

  /**
   * Notes a navigator left without a form because a field of its target is named like a no-argument
   * method of that form's path, on a record that asks for navigators in so many words.
   *
   * <p>A hop into the component from a path of that tier then returns the composed path, so a chain
   * such as {@code orders().customer().email()} stops there, and {@code headOption()} after it
   * reaches the path's own method. A record that leaves navigators to the default asked for
   * nothing, and is told nothing, and a field that clashes with the navigator's own tier is refused
   * already.
   */
  private void noteMissingForms(
      TypeElement record, RecordComponentElement component, Target target, Tier tier) {
    if (!writesAttribute(record, "generateNavigators") || !inheritedNames(tier, target).isEmpty()) {
      return;
    }
    // Each wider tier left without a form, with the target fields its path's methods clash with.
    record Clash(Tier tier, List<String> fields) {}
    List<Clash> clashes =
        tier.wider().stream()
            .filter(wider -> formNameFree(wider, target))
            .map(wider -> new Clash(wider, inheritedNames(wider, target)))
            .filter(clash -> !clash.fields().isEmpty())
            .toList();
    if (clashes.isEmpty()) {
      return;
    }
    String name = component.getSimpleName().toString();
    String targetName = target.record().getSimpleName().toString();
    Diagnostics.note(
        processingEnv.getMessager(),
        component,
        "@GenerateFocus",
        "the navigator for '"
            + record.getSimpleName()
            + "."
            + name
            + "' has no "
            + clashes.stream()
                .map(clash -> clash.tier().formName())
                .collect(Collectors.joining(" or "))
            + ".",
        clashes.stream()
            .map(
                clash ->
                    ProcessorUtils.capitalise(clash.tier().article())
                        + " "
                        + clash.tier().formName()
                        + " would be "
                        + clash.tier().withArticle()
                        + ", which has its own "
                        + noArgumentCalls(clash.fields())
                        + ", so it could not navigate to "
                        + qualifiedFields(targetName, clash.fields())
                        + ", and a hop into '"
                        + name
                        + "' from "
                        + clash.tier().withArticle()
                        + " returns a plain "
                        + clash.tier().description()
                        + ".")
            .collect(Collectors.joining(" ")),
        "Rename "
            + qualifiedFields(
                targetName,
                clashes.stream().flatMap(clash -> clash.fields().stream()).distinct().toList())
            + " to give the navigator for '"
            + name
            + "' "
            + clashes.stream()
                .map(clash -> clash.tier().article() + " " + clash.tier().formName())
                .collect(Collectors.joining(" and "))
            + ", or remove generateNavigators = true from @GenerateFocus on "
            + record.getSimpleName()
            + " to go without this note.");
  }

  /** Calls to no-argument methods as a message names them: {@code head() and last()}. */
  private static String noArgumentCalls(List<String> methods) {
    return methods.stream().map(method -> method + "()").collect(Collectors.joining(" and "));
  }

  /**
   * The sentence saying why the change a fix line offers would still give a field no navigator: the
   * navigator would have methods of its own named like fields of the record it reaches.
   *
   * @param navigator the navigator the change would give, as the sentence opens: {@code Widened,
   *     its navigator}
   */
  private static String clashSentence(
      String navigator, Tier tier, String targetName, List<String> clashes) {
    return navigator
        + " would be "
        + tier.withArticle()
        + ", which has its own "
        + noArgumentCalls(clashes)
        + ", so it could not navigate to "
        + qualifiedFields(targetName, clashes)
        + ".";
  }

  /**
   * The sentence, after a space, saying why a fix line asks for one of two fields to be renamed
   * first: the navigator it offers one would share its class name with the other's, which has a
   * navigator or would gain one from the same change. Empty where there is no such field.
   */
  private String sharedNameSentence(
      TypeElement record,
      RecordComponentElement component,
      Optional<RecordComponentElement> namesake) {
    return namesake
        .map(
            other ->
                " A navigator for '"
                    + component.getSimpleName()
                    + "' would share its class name, "
                    + navigatorClassName(component)
                    + ", with "
                    + (navigatorTarget(record, other) != null
                        ? "the navigator for " + qualifiedField(record, other)
                        : "the one " + qualifiedField(record, other) + " would gain too")
                    + ", since a navigator class takes its field's name with the first letter in"
                    + " upper case.")
        .orElse("");
  }

  /**
   * What a fix line asks to be renamed before its change gives a field the navigator it offers: the
   * target's fields named like the navigator's own methods, and either of two fields whose
   * navigators would share a class name. Empty where nothing needs renaming.
   */
  private static Optional<String> renamedFirst(
      TypeElement record,
      RecordComponentElement component,
      String targetName,
      List<String> clashes,
      Optional<RecordComponentElement> namesake) {
    Optional<String> either = namesake.map(other -> eitherField(record, component, other));
    if (clashes.isEmpty()) {
      return either;
    }
    String fields = qualifiedFields(targetName, clashes);
    return Optional.of(either.map(pair -> fields + " and either " + pair).orElse(fields));
  }

  /**
   * The sentences a note on a missing or unreachable companion adds where the change it asks for
   * would give the component a navigator that a rename must come before: the target's fields named
   * like the navigator's own methods, and another field whose navigator would share the class name,
   * with what to rename as well. Empty where neither applies.
   *
   * @param skipped why the component has no navigator, which the same change mends for any other
   *     component skipped for the same reason
   */
  private String renamedAsWell(
      TypeElement record, RecordComponentElement component, TypeElement target, Reach skipped) {
    Tier tier = methodPath(record, component).tier();
    String name = target.getSimpleName().toString();
    List<String> clashes = inheritedNames(tier, target);
    Optional<RecordComponentElement> namesake =
        namesake(
            record,
            component,
            freedByRenaming(record, target, clashes)
                .or(other -> skippedTarget(record, other).equals(skipped)));
    return (clashes.isEmpty()
            ? ""
            : " " + clashSentence("A navigator into " + name, tier, name, clashes))
        + sharedNameSentence(record, component, namesake)
        + renamedFirst(record, component, name, clashes, namesake)
            .map(renames -> " Rename " + renames + " as well.")
            .orElse("");
  }

  /** Two fields of a record, either of which a fix line asks to be renamed. */
  private static String eitherField(
      TypeElement record, RecordComponentElement component, RecordComponentElement other) {
    return qualifiedField(record, component) + " or " + qualifiedField(record, other);
  }

  /** A field of a record as a message names it: {@code 'Order.items'}. */
  private static String qualifiedField(TypeElement record, RecordComponentElement component) {
    return "'" + record.getSimpleName() + "." + component.getSimpleName() + "'";
  }

  /** Fields of a record as a message names them: {@code 'Customer.headOption' and ...}. */
  private static String qualifiedFields(String record, List<String> fields) {
    return fields.stream()
        .map(field -> "'" + record + "." + field + "'")
        .collect(Collectors.joining(" and "));
  }

  /**
   * Says why a component asking for a navigator did not get one, when the reason is that it reaches
   * a generic target through a wildcard or a raw type.
   *
   * <p>The declaring record asked for navigators and gets one fewer than the components suggest.
   * Most other reasons a component has no navigator are visible in what it is: not navigable, or
   * filtered out by the record's own include/exclude. The one that is not, a target with no
   * companion to compose, is reported the same way.
   *
   * <p>This reason is given ahead of a missing or unreachable companion, because it shows in the
   * declaration and holds however the target's module was built. A missing or unreachable companion
   * still stands in the way of the instantiation the note offers, so the offer names it rather than
   * promising a navigator.
   *
   * <p>No chain through the target's companion is offered, because none compiles: each of its paths
   * starts from one instantiation of the target, which a wildcard or a raw type is not. A lens from
   * the type as declared does compose, so the note says so. The component's own method always
   * focuses on that type here: a container the record does not widen has no navigator to miss, and
   * gets no note.
   *
   * <p>Where the target has a field named like one of the navigator's own methods, or another
   * field's navigator would share this one's class name, a concrete type alone would lead straight
   * to the note or error for that, so the offer asks for the rename first.
   *
   * @param recordElement the record declaring the component
   * @param component the component whose navigator was not generated
   * @param target the target as the component reaches it, already found uninstantiable
   * @param instantiated what a concrete instantiation of the target would reach: {@code Navigable},
   *     {@code Unpublished} or {@code Hidden}
   */
  private void reportUninstantiableTargetSkipped(
      TypeElement recordElement,
      RecordComponentElement component,
      Target target,
      Reach instantiated) {

    String componentName = component.getSimpleName().toString();
    String name = target.record().getSimpleName().toString();
    String written =
        ProcessorUtils.isRaw(target.type())
            ? "the raw " + name
            : ProcessorUtils.simpleTypeName(target.type());
    MethodPath method = methodPath(recordElement, component);
    List<String> clashes = inheritedNames(method.tier(), target);
    Optional<RecordComponentElement> namesake =
        namesake(
            recordElement, component, freedByRenaming(recordElement, target.record(), clashes));
    String outcome =
        switch (instantiated) {
          case Reach.Unpublished(TypeElement unpublished) ->
              ". A navigator into "
                  + name
                  + " also needs a public "
                  + focusClassOf(unpublished).canonicalName()
                  + " on this module's compile classpath: add hkj-processor to the annotation"
                  + " processor path of the module declaring "
                  + name
                  + ", and rebuild it.";
          case Reach.Hidden hidden ->
              ". A navigator into "
                  + name
                  + " also names '"
                  + hidden.hidden().getSimpleName()
                  + "', which cannot be reached from '"
                  + companionPackage(recordElement)
                  + "'. "
                  + reachabilityFix(recordElement, hidden);
          default -> " to get a navigator.";
        };
    String write =
        ProcessorUtils.concreteAlternative(target.type())
                .map(alternative -> "write " + alternative + ", or another concrete instantiation,")
                .orElse("write " + name + " with a concrete type argument for each type parameter")
            + " in place of "
            + written
            + outcome;
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
                + "() returns "
                + method.description()
                + " instead. A path from one instantiation of "
                + name
                + " does not compose with it, but a lens you write from "
                + written
                + " does, through "
                + (method.nullable() ? ".some(Affines.nullable()).via()" : ".via()")
                + "."
                + (clashes.isEmpty()
                    ? ""
                    : " "
                        + clashSentence(
                            "A navigator into one instantiation of " + name,
                            method.tier(),
                            name,
                            clashes))
                + sharedNameSentence(recordElement, component, namesake)
                + " "
                + renamedFirst(recordElement, component, name, clashes, namesake)
                    .map(renames -> "Rename " + renames + ", then " + write)
                    .orElseGet(() -> ProcessorUtils.capitalise(write)),
            component);
  }

  /**
   * Says why a component asking for a navigator did not get one, when its target is a record from a
   * dependency with no companion to compose: none on this module's compile classpath, or one this
   * module cannot access.
   *
   * <p>Nothing in the declaration shows this: the target visibly carries {@code @GenerateFocus},
   * and the reason is another module's build, so it is the least visible reason a navigator can be
   * missing and the one an author most needs told. Where the rebuild alone would lead to another
   * note or error, the note names the rename that has to come with it.
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
                + "() returns "
                + methodPath(recordElement, component).description()
                + "."
                + renamedAsWell(
                    recordElement, component, unpublished, new Reach.Unpublished(unpublished)),
            component);
  }

  /**
   * Says why a component asking for a navigator did not get one, when the navigator would name a
   * type this Focus class's package cannot reach: the target record, or one of its components, is
   * {@code private} or hidden in another package. The Focus class itself can still name the target,
   * so the component keeps its own method. Where making the type reachable alone would lead to
   * another note or error, the note names the rename that has to come with it.
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
                + reachabilityFix(recordElement, hidden)
                + " Until then "
                + focusClassOf(recordElement).simpleName()
                + "."
                + componentName
                + "() returns "
                + methodPath(recordElement, component).description()
                + "."
                + renamedAsWell(recordElement, component, hidden.record(), hidden),
            component);
  }

  /**
   * What makes the type a navigator into a hidden target names reachable from this Focus class's
   * package.
   */
  private String reachabilityFix(TypeElement recordElement, Reach.Hidden hidden) {
    return Reachability.fix(
        processingEnv.getElementUtils(),
        hidden.hidden(),
        new Reachability.Target(companionPackage(recordElement), "", home -> Optional.empty()));
  }

  /**
   * What a component's Focus method returns, or would return with every container widened: a note
   * names it, and a navigator's target is read from its focus.
   *
   * @param tier the path type the method returns
   * @param source the record the path starts from
   * @param focus the type the path focuses on
   * @param nullable whether that focus may be null, as an element written {@code @Nullable} may
   */
  private record MethodPath(Tier tier, TypeMirror source, TypeMirror focus, boolean nullable) {

    /** The method's return type as its signature spells it: {@code AffinePath<Outer, Inner<?>>}. */
    String description() {
      return tier.description()
          + "<"
          + ProcessorUtils.simpleTypeName(source)
          + ", "
          + (nullable ? "@Nullable " : "")
          + ProcessorUtils.simpleTypeName(focus)
          + ">";
    }
  }

  /**
   * What a component's own Focus method returns, read from the widening it carries.
   *
   * <p>A component's path is not always a plain path over itself: a {@code @Nullable} one widens
   * through {@code .nullable()}, an {@code Either} or a {@code List} through its own step, and a
   * {@code Map} or an array steps into its element where the record widens it. Containers nested in
   * one another compose to the leaf.
   *
   * <p>This focus is what a navigator for the component reaches, so a navigator never changes what
   * the method focuses on.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return the method's path type, source and focus
   */
  private MethodPath methodPath(TypeElement record, RecordComponentElement component) {
    return pathOf(record, component, widening(record, component));
  }

  /**
   * What a component's Focus method would return were every container in it widened: the path a
   * diagnostic offers to reach by widening, and the record it would navigate to.
   *
   * <p>Generation, and every statement about what a field's method returns, reads {@link
   * #methodPath}. Only a fix that offers widening reads this, as do the checks that keep it from
   * offering a widening that would give no usable navigator.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return the widened method's path type, source and focus
   */
  private MethodPath widenedPath(TypeElement record, RecordComponentElement component) {
    return pathOf(record, component, analysis.analyse(component, true));
  }

  /**
   * The path a widening arrives at. Its focus is what the last layer the walk peeled unwraps to:
   * {@code .nullable()} rules out the component's own null and leaves it in focus, and each
   * container's step records the element it unwraps to, a {@code Kind} layer's included.
   */
  private static MethodPath pathOf(
      TypeElement record, RecordComponentElement component, Widening widening) {
    TypeMirror focus = component.asType();
    boolean nullable = false;
    for (Step step : widening.steps()) {
      if (step.innerType() != null) {
        focus = step.innerType();
        nullable = NullableAnnotations.isNullable(focus);
      }
    }
    return new MethodPath(widening.tier(), record.asType(), focus, nullable);
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
    // A navigator is offered only over what the method focuses on, so a container the record
    // leaves whole has no navigator to miss.
    TypeMirror reached = methodPath(record, component).focus();
    Reach reach = reach(reached, companionPackage(record));
    if (Target.instantiable(reached)) {
      return reach;
    }
    return switch (reach) {
      case Reach.Navigable(TypeElement navigable) ->
          new Reach.Uninstantiable(new Target(navigable, (DeclaredType) reached), reach);
      case Reach.Unpublished(TypeElement unpublished) ->
          new Reach.Uninstantiable(new Target(unpublished, (DeclaredType) reached), reach);
      case Reach.Hidden(TypeElement hidden, TypeElement _) ->
          new Reach.Uninstantiable(new Target(hidden, (DeclaredType) reached), reach);
      default -> reach;
    };
  }

  /**
   * A navigable record, as a component reaches it: the component's own type, or the element its
   * method steps into through the containers the component nests.
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
   * The record a record's Focus method for this component navigates to, or null when that method
   * hands back a path instead.
   *
   * <p>A component reaches a navigator by being a navigable type itself, or by being a container of
   * one that the record widens, and in either case only when it gives that type a navigator can be
   * instantiated with. A reachable navigator is still declined where its target has a field named
   * like one of the path's own methods, on a record that leaves navigators to the default, and
   * where an earlier component's navigator already has its class name. Every site that asks — the
   * navigator class, the method that returns it, and a navigation method composing it from another
   * record — reads the answer from here, so they cannot disagree about which components have one.
   *
   * @param record the record that declares the component
   * @param component the component
   * @return the navigable record its Focus method reaches, or null
   */
  private Target navigatorTarget(TypeElement record, RecordComponentElement component) {
    Target target = reachableTarget(record, component);
    return target == null
            || !declinedInheritedNames(record, component, target).isEmpty()
            || sharedNavigatorName(record, component).isPresent()
        ? null
        : target;
  }

  /**
   * The earlier component whose navigator class already has the name this component's would take: a
   * navigator class takes its field's name with the first letter in upper case, so two components
   * whose names differ only in that letter's case name the same class. The first in declaration
   * order with a navigator keeps it, so a component added after another never takes its navigator
   * away. Only the declaring record's own components are read, so a record is judged as its own
   * companion was generated.
   */
  private Optional<RecordComponentElement> sharedNavigatorName(
      TypeElement record, RecordComponentElement component) {
    List<RecordComponentElement> components = List.copyOf(record.getRecordComponents());
    String className = navigatorClassName(component);
    return components.stream()
        .limit(components.indexOf(component))
        .filter(earlier -> navigatorClassName(earlier).equals(className))
        .filter(earlier -> navigatorTarget(record, earlier) != null)
        .findFirst();
  }

  /**
   * The other component whose navigator would share its class name with the one a fix line offers
   * this component, so that following the fix line alone would lead to the shared-class-name note
   * or error. Its name differs from this one's only in the case of its first letter, and it has a
   * navigator: an earlier one would keep the name, and a later one would lose its navigator to this
   * one. An earlier one that the same change gives a navigator would keep the name too. A later one
   * that the same change gives a navigator counts where the record writes {@code generateNavigators
   * = true}, where it would draw the error; on the default it had none to lose.
   *
   * @param record the record that declares both components
   * @param component the component the fix line offers a navigator
   * @param alsoGiven the other components the fix line's change gives a navigator as well
   * @return the component the fix line asks to be renamed, beside this one
   */
  private Optional<RecordComponentElement> namesake(
      TypeElement record,
      RecordComponentElement component,
      Predicate<RecordComponentElement> alsoGiven) {
    List<RecordComponentElement> components = List.copyOf(record.getRecordComponents());
    String className = navigatorClassName(component);
    int position = components.indexOf(component);
    return components.stream()
        .filter(other -> !other.equals(component))
        .filter(other -> navigatorClassName(other).equals(className))
        .filter(
            other ->
                navigatorTarget(record, other) != null
                    || ((components.indexOf(other) < position
                            || writesAttribute(record, "generateNavigators"))
                        && alsoGiven.test(other)))
        .findFirst();
  }

  /**
   * The other components that would reach a target with nothing declined once a fix line renames
   * these fields of it. On a record that leaves navigators to the default, a component that reaches
   * the target is declined for each field named like one of its navigator's methods, so renaming
   * them all gives it a navigator; with none renamed, this adds nothing to what has one already.
   */
  private Predicate<RecordComponentElement> freedByRenaming(
      TypeElement record, TypeElement target, List<String> fields) {
    return other -> {
      Target reachable = reachableTarget(record, other);
      return reachable != null
          && reachable.record().equals(target)
          && fields.containsAll(declinedInheritedNames(record, other, reachable));
    };
  }

  /**
   * The navigable record a navigator for this component would reach, before the question of its
   * field names is asked: navigators on and not filtered out, a target that can be instantiated,
   * and one the field's own method focuses on.
   */
  private Target reachableTarget(TypeElement record, RecordComponentElement component) {
    if (!focusSettings(record).generateNavigators()
        || !shouldGenerateNavigator(record, component)) {
      return null;
    }
    return unfilteredTarget(record, component);
  }

  /**
   * The same target question before include/exclude filters, for followable field suggestions: the
   * navigable record the field's own method focuses on, where a navigator into it can be
   * instantiated. A navigator only adds navigation to that path, so it is offered over nothing
   * else, and a container the record leaves whole keeps its method's path with no navigator.
   */
  private Target unfilteredTarget(TypeElement record, RecordComponentElement component) {
    Target target =
        navigableElement(methodPath(record, component).focus(), companionPackage(record));
    return target == null || !target.instantiable() ? null : target;
  }

  /**
   * The fields of a navigator's target named like a no-argument method the navigator would inherit,
   * where the declaring record leaves navigators to the default; empty otherwise. Such a record
   * asked for no navigator in particular, so the field keeps the path it has without one, with a
   * note. A record that writes {@code generateNavigators = true} gets the navigator, and an error
   * for each such field.
   */
  private List<String> declinedInheritedNames(
      TypeElement record, RecordComponentElement component, Target target) {
    if (writesAttribute(record, "generateNavigators")) {
      return List.of();
    }
    return inheritedNames(methodPath(record, component).tier(), target);
  }

  /** The target fields that would clash, whether navigators were requested explicitly or not. */
  private List<String> inheritedNames(Tier tier, Target target) {
    return inheritedNames(tier, target.record());
  }

  /** The fields of a record that a navigator of this tier could not navigate to. */
  private List<String> inheritedNames(Tier tier, TypeElement target) {
    Set<String> inherited = inheritedNoArgumentMethods(tier);
    return target.getRecordComponents().stream()
        .map(field -> field.getSimpleName().toString())
        .filter(inherited::contains)
        .toList();
  }

  /**
   * The wider tiers a field's navigator has a form for: each tier wider than its own, except one
   * whose path has a no-argument method named like a field of the target, since a form of that tier
   * could not navigate to it, and one whose name is not {@linkplain #formNameFree free}.
   *
   * <p>A hop into the field from a wider path hands on the form of the composed tier, so the
   * navigator's own companion and every navigation method in this round composing it read the forms
   * from here.
   *
   * @param tier the tier of the field's own navigator
   * @param target the record the navigator reaches
   * @return the tiers of the forms nested in the navigator, narrowest first
   */
  private List<Tier> widerForms(Tier tier, Target target) {
    return tier.wider().stream()
        .filter(wider -> formNameFree(wider, target))
        .filter(wider -> inheritedNames(wider, target).isEmpty())
        .toList();
  }

  /**
   * Whether a form's name is free inside a navigator into the target: no type parameter of the
   * target takes it, nor a top-level type of the unnamed package, which generated code cannot
   * qualify. Inside the navigator the form would hide either.
   */
  private boolean formNameFree(Tier form, Target target) {
    return !namesOf(target.record().getTypeParameters()).contains(form.formName())
        && processingEnv.getElementUtils().getTypeElement(form.formName()) == null;
  }

  /** The simple name of the navigator class a field's method returns. */
  private static String navigatorClassName(RecordComponentElement component) {
    return ProcessorUtils.capitalise(component.getSimpleName().toString()) + "Navigator";
  }

  /**
   * Generates the navigator class for a field, with a form nested in it for each wider tier.
   *
   * <p>A form is the same navigator over a wider path: the one a navigation method from a navigator
   * of that tier hands on, since composing that navigator's path with the field's own lands on a
   * path of that tier, which the field's own navigator cannot hold. A form is generated whether or
   * not any hop in this compilation reaches it, because a record in a dependant module may.
   *
   * @param component the record component (field)
   * @param target the record the field's method reaches, and the type arguments it gives it
   * @param currentDepth current depth
   * @param tier the path tier of the field's own navigator
   * @return the generated navigator TypeSpec
   */
  private TypeSpec generateNavigatorClass(
      RecordComponentElement component, Target target, int currentDepth, Tier tier) {

    String componentName = component.getSimpleName().toString();
    List<TargetField> fields = targetFields(component, target.record(), tier);
    List<Tier> forms = widerForms(tier, target);
    TypeSpec.Builder navigator =
        navigatorClass(
            component,
            target.record(),
            currentDepth,
            tier,
            navigatorClassName(component),
            CodeBlock.of("Navigator for fluent access to {@code $L} fields.", componentName),
            forms,
            fields);
    for (Tier form : forms) {
      navigator.addType(
          navigatorClass(
                  component,
                  target.record(),
                  currentDepth,
                  form,
                  form.formName(),
                  CodeBlock.of(
                      "The {@code $L} navigator that a hop from $L {@link $T} hands on.",
                      componentName,
                      form.article(),
                      form.pathClass()),
                  List.of(),
                  fields)
              .build());
    }
    return navigator.build();
  }

  /**
   * Builds one navigator class for a field: its own, or one of its wider forms.
   *
   * @param component the record component (field)
   * @param targetRecord the target record type (the field's type)
   * @param currentDepth current depth
   * @param tier the path tier for this navigator
   * @param navigatorClassName the class's simple name
   * @param summary the first sentence of the class's javadoc
   * @param forms the tiers of the wider forms nested in it, which its javadoc names
   * @param fields the target's fields it navigates to, as {@link #targetFields} found them
   * @return the navigator class, ready to build
   */
  private TypeSpec.Builder navigatorClass(
      RecordComponentElement component,
      TypeElement targetRecord,
      int currentDepth,
      Tier tier,
      String navigatorClassName,
      CodeBlock summary,
      List<Tier> forms,
      List<TargetField> fields) {

    String componentName = component.getSimpleName().toString();
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

    // The navigator is a path of the component's own tier, and is built from the very path the
    // field's method would return without it: an element the container declares nullable stays so.
    // Its annotations are the ones the method writes, which leaves out any this package cannot
    // name.
    Optional<TypeMirror> nullableElement = nullableElement(component);
    TypeName elementTypeName =
        nullableElement
            .map(
                element ->
                    targetTypeName.annotated(
                        ProcessorUtils.typeNameOf(element, analysis.targetPackage()).annotations()))
            .orElse(targetTypeName);
    ClassName pathClass = tier.pathClass();
    ParameterizedTypeName pathType =
        ParameterizedTypeName.get(pathClass, sourceTypeVar, elementTypeName);

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
            .addJavadoc(summary)
            .addJavadoc(
                "\n\n<p>This navigator is $L {@link $T}, with a navigation method for each field\n"
                    + "of {@link $T}.\n\n",
                tier.article(),
                pathClass,
                ClassName.get(targetRecord));
    if (!forms.isEmpty()) {
      // A hop composes this navigator's path with a wider one, and lands on that tier.
      navigatorBuilder.addJavadoc(
          "<p>A hop into {@code $L} from a wider path hands on $L, nested here:\n"
              + "the same navigator over that path.\n\n",
          componentName,
          forms.stream()
              .map(form -> "{@code " + form.formName() + "}")
              .collect(Collectors.joining(" or ")));
    }
    navigatorBuilder.addJavadoc(
        "<p>Path type: $L\n\n@param <$L> the source type at the root of the navigation",
        tierDescription,
        sourceTypeVar.name());
    for (TypeVariableName targetTypeVar : targetTypeVars) {
      navigatorBuilder.addJavadoc(
          "\n@param <$L> the {@code $L} of the {@code $L} navigated to",
          targetTypeVar.name(),
          targetTypeVar.name(),
          targetRecord.getSimpleName());
    }

    // The navigator is the path it wraps: the base forwards every path method to it, final, so the
    // navigator only adds its navigation methods.
    navigatorBuilder.superclass(
        ParameterizedTypeName.get(tier.navigatorBaseClass(), sourceTypeVar, elementTypeName));
    navigatorBuilder.addMethod(
        MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(pathType, "path")
            .addStatement("super(path)")
            .build());

    // Add navigation methods for each field of the target record. A navigation method reads the
    // components of each element it reaches, so a null element is passed over rather than read.
    CodeBlock elements =
        nullableElement.isPresent()
            ? CodeBlock.of("toPath().some($T.nullable())", WideningAnalysis.AFFINES_CLASS)
            : CodeBlock.of("toPath()");
    addNavigationMethods(
        navigatorBuilder, targetRecord, sourceTypeVar, currentDepth + 1, tier, elements, fields);

    return navigatorBuilder;
  }

  /**
   * The element a component reaches its navigable record as, where the container holding it
   * declares that element nullable; empty for a component that is the record itself, or a container
   * whose elements are not nullable.
   */
  private Optional<TypeMirror> nullableElement(RecordComponentElement component) {
    MethodPath path = methodPath((TypeElement) component.getEnclosingElement(), component);
    return path.nullable() ? Optional.of(path.focus()) : Optional.empty();
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
      Tier currentTier,
      CodeBlock elements,
      List<TargetField> fields) {

    ClassName targetFocusClass = focusClassOf(targetRecord);

    for (TargetField field : fields) {
      RecordComponentElement component = field.component();
      String fieldName = component.getSimpleName().toString();
      CodeBlock focusMethod = focusMethodCall(targetFocusClass, targetRecord, fieldName);
      switch (field.shape()) {
        case FieldShape.Path path ->
            navigatorBuilder.addMethod(
                navigationMethod(
                        component,
                        fieldName,
                        sourceTypeVar,
                        currentTier.widen(path.tier()),
                        path.focusType())
                    .addStatement("return $L.via($L)", elements, focusMethod)
                    .build());
        case FieldShape.Navigator navigator -> {
          Tier composed = currentTier.widen(navigator.tier());
          MethodSpec.Builder methodBuilder =
              navigationMethod(
                  component, fieldName, sourceTypeVar, composed, navigator.focusType());
          addNavigatorComposition(
              methodBuilder,
              navigator,
              elements,
              focusMethod,
              sourceTypeVar,
              currentDepth,
              composed);
          navigatorBuilder.addMethod(methodBuilder.build());
        }
      }
    }
  }

  /** A field of a navigator's target, with the shape of the method a navigation method composes. */
  private record TargetField(RecordComponentElement component, FieldShape.Composable shape) {}

  /**
   * The fields of a navigator's target that it navigates to, each with the shape of the static
   * Focus method a navigation method composes for it, reporting each field it cannot navigate to.
   *
   * <p>A field named like a no-argument method of the navigator's tier has no navigation method it
   * could take, since the call would reach the path's own method instead, and is refused. A field
   * whose published method this compilation cannot compose is left out, with a note on the
   * component the navigator belongs to. The navigator and each of its forms are built from the one
   * list, so a field is reported once however many forms there are.
   *
   * @param owner the component whose navigator this is
   * @param targetRecord the record the navigator reaches
   * @param tier the tier of the component's own navigator
   * @return the fields it navigates to, in declaration order
   */
  private List<TargetField> targetFields(
      RecordComponentElement owner, TypeElement targetRecord, Tier tier) {
    ClassName targetFocusClass = focusClassOf(targetRecord);
    Set<String> inheritedNames = inheritedNoArgumentMethods(tier);
    List<TargetField> fields = new ArrayList<>();
    for (RecordComponentElement component : targetRecord.getRecordComponents()) {
      String fieldName = component.getSimpleName().toString();
      if (inheritedNames.contains(fieldName)) {
        reportInheritedName(owner, targetRecord, fieldName, tier);
        continue;
      }
      switch (fieldShape(targetRecord, targetFocusClass, component)) {
        case FieldShape.Unresolvable unresolvable ->
            reportUnresolvableField(owner, targetFocusClass, fieldName, unresolvable.type());
        case FieldShape.Unrecognised _ ->
            reportUnrecognisedField(owner, targetRecord, targetFocusClass, fieldName);
        case FieldShape.Composable composable -> fields.add(new TargetField(component, composable));
      }
    }
    return List.copyOf(fields);
  }

  /**
   * The no-argument methods a navigator of this tier inherits, which no navigation method may take
   * the name of. They are read from the base the navigator extends, in the hkj-core this module
   * compiles against, so the set is exactly what the generated class inherits.
   */
  private Set<String> inheritedNoArgumentMethods(Tier tier) {
    return inheritedNoArgumentMethods.computeIfAbsent(
        tier,
        t ->
            Optional.ofNullable(
                    processingEnv
                        .getElementUtils()
                        .getTypeElement(t.navigatorBaseClass().canonicalName()))
                .map(
                    base ->
                        ElementFilter.methodsIn(processingEnv.getElementUtils().getAllMembers(base))
                            .stream()
                            .filter(method -> method.getParameters().isEmpty())
                            .map(method -> method.getSimpleName().toString())
                            .collect(Collectors.toUnmodifiableSet()))
                .orElseGet(Set::of));
  }

  /**
   * Refuses a navigator whose target has a field named like a no-argument method every path of its
   * tier has. Skipping the field would leave a call such as {@code name().last()} compiling against
   * the path's own {@code last()}, which reads a different focus or fails when the path runs.
   */
  private void reportInheritedName(
      RecordComponentElement owner, TypeElement targetRecord, String fieldName, Tier tier) {
    TypeElement declaring = (TypeElement) owner.getEnclosingElement();
    String record = declaring.getSimpleName().toString();
    String component = owner.getSimpleName().toString();
    Diagnostics.error(
        processingEnv.getMessager(),
        owner,
        "@GenerateFocus",
        "the navigator for '"
            + record
            + "."
            + component
            + "' cannot navigate to '"
            + targetRecord.getSimpleName()
            + "."
            + fieldName
            + "'.",
        "A navigator is "
            + tier.withArticle()
            + ", which has its own "
            + fieldName
            + "(), so "
            + focusClassOf(declaring).simpleName()
            + "."
            + component
            + "()."
            + fieldName
            + "() would call that method instead of navigating to the field.",
        "Rename '"
            + targetRecord.getSimpleName()
            + "."
            + fieldName
            + "', or "
            + keepPlainPath(declaring, component)
            + " to keep '"
            + component
            + "' a plain path.");
  }

  /**
   * The words a fix line uses to leave one of a record's fields without a navigator: listed in
   * {@code excludeFields}, taken out of {@code includeFields}, or, where it is the only field
   * {@code includeFields} names, navigators turned off, since an empty {@code includeFields}
   * filters nothing. The list goes with them, since no list is read once navigators are off, and
   * one left behind draws a warning. A field {@code includeFields} leaves out has no navigator to
   * keep from it, so where the list is written it names the field.
   */
  private static String keepPlainPath(TypeElement record, String component) {
    List<String> included = Arrays.asList(focusSettings(record).includeFields());
    if (included.isEmpty()) {
      return addToFieldList(record, "excludeFields", component);
    }
    if (included.size() > 1) {
      return FocusFieldListChecks.removeName(record, "includeFields", component);
    }
    return writesAttribute(record, "generateNavigators")
        ? "remove includeFields and replace generateNavigators = true with generateNavigators ="
            + " false on "
            + record.getSimpleName()
        : "remove includeFields and add generateNavigators = false to @GenerateFocus on "
            + record.getSimpleName();
  }

  /**
   * The words a fix line uses to put a field into one of a record's field-name lists: added to the
   * list where the annotation writes one, or the attribute written out where it does not, so that
   * following it never writes the attribute twice.
   */
  private static String addToFieldList(TypeElement record, String attribute, String field) {
    return addToFieldList(record, attribute, field, "");
  }

  /**
   * {@link #addToFieldList(TypeElement, String, String)}, with words to follow the field's name.
   *
   * @param aside the words, such as {@code ", or its new name,"}
   */
  private static String addToFieldList(
      TypeElement record, String attribute, String field, String aside) {
    return writesAttribute(record, attribute)
        ? "add \"" + field + "\"" + aside + " to " + attribute + " on " + record.getSimpleName()
        : "add "
            + attribute
            + " = \""
            + field
            + "\""
            + aside
            + " to @GenerateFocus on "
            + record.getSimpleName();
  }

  /**
   * The words a fix line puts after a field's name where it asks for that field or another to be
   * renamed first, since the name it writes is then the field's new one.
   */
  private static String orItsNewName(Optional<RecordComponentElement> namesake) {
    return namesake.isPresent() ? ", or its new name," : "";
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
                + "@return $L focusing on the {@code $L} field",
            fieldName,
            composed.withArticle(),
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
   * <p>That navigator holds a path of the field's own tier, and each of its forms a path of a wider
   * one, so the one handed on is whichever holds the tier that composing with this navigator's own
   * path lands on. Where none does, a form the target's companion does not publish, the composed
   * path is the answer and navigation stops there; so it does at the depth limit.
   *
   * <p>The navigator handed on is instantiated with the type arguments the field gives its own
   * target, after this navigator's source.
   */
  private void addNavigatorComposition(
      MethodSpec.Builder methodBuilder,
      FieldShape.Navigator navigator,
      CodeBlock elements,
      CodeBlock focusMethod,
      TypeVariableName sourceTypeVar,
      int currentDepth,
      Tier composed) {

    Optional<ClassName> holding =
        currentDepth < maxDepth ? navigator.holding(composed) : Optional.empty();
    if (holding.isPresent()) {
      methodBuilder.returns(
          navigatorType(holding.orElseThrow(), sourceTypeVar, navigator.typeArguments()));
      methodBuilder.addStatement(
          "return new $T<>($L.via($L.toPath()))", holding.orElseThrow(), elements, focusMethod);
      return;
    }
    methodBuilder.addStatement("return $L.via($L.toPath())", elements, focusMethod);
  }

  /**
   * What a target record's static Focus method for one field hands back, or why a navigation method
   * cannot compose it from this compilation.
   */
  private sealed interface FieldShape {

    /** A method a navigation method can compose: a plain path, or a navigator. */
    sealed interface Composable extends FieldShape {}

    /** A method returning a plain path of this tier and focus. */
    record Path(Tier tier, TypeName focusType) implements Composable {}

    /**
     * A method returning a navigator that wraps a path of this tier and focus, instantiated with
     * these type arguments after its source, with a form nested in it for each of {@code forms}.
     */
    record Navigator(
        Tier tier,
        TypeName focusType,
        ClassName navigatorClass,
        List<TypeName> typeArguments,
        List<Tier> forms)
        implements Composable {

      public Navigator {
        typeArguments = List.copyOf(typeArguments);
        forms = List.copyOf(forms);
      }

      /** This navigator with these forms nested in it. */
      Navigator withForms(List<Tier> forms) {
        return new Navigator(tier, focusType, navigatorClass, typeArguments, forms);
      }

      /**
       * The class that holds a path of a tier no narrower than the navigator's own: the navigator
       * itself, or its form of that tier; empty where it has no such form.
       */
      Optional<ClassName> holding(Tier composed) {
        if (composed == tier) {
          return Optional.of(navigatorClass);
        }
        return forms.contains(composed)
            ? Optional.of(navigatorClass.nestedClass(composed.formName()))
            : Optional.empty();
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
              targetFocusClass.nestedClass(navigatorClassName(component)),
              typeNamesOf(target.type().getTypeArguments()),
              widerForms(widening.tier(), target));
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
    List<? extends TypeParameterElement> declared = method.getTypeParameters();
    List<? extends TypeParameterElement> own = targetRecord.getTypeParameters();
    return namesOf(declared).equals(namesOf(own)) && boundedNoTighter(declared, own);
  }

  /**
   * Whether there are as many type parameters as among {@code own}, each bounded no more tightly
   * than the one in the same place, so that what {@code own} accepts they accept too.
   */
  private boolean boundedNoTighter(
      List<? extends TypeParameterElement> declared, List<? extends TypeParameterElement> own) {
    Types types = processingEnv.getTypeUtils();
    return declared.size() == own.size()
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
    FieldShape shape = navigatorShape(navigator, declared, source);
    return shape instanceof FieldShape.Navigator own
        ? own.withForms(publishedForms(navigator, declared, source, own))
        : shape;
  }

  /**
   * The wider forms a published navigator has nested in it: for each tier wider than its own, a
   * class named for that tier that is a navigator of that tier over the same focus, composed as the
   * navigator itself is. A navigator that nests no such class has no forms, and a hop from a wider
   * path into its field returns the composed path.
   *
   * @param navigator the published navigator class
   * @param declared the navigator as the published method returns it
   * @param source the target, as the method is over it
   * @param own the navigator's own shape
   * @return the tiers of the forms it publishes, narrowest first
   */
  private List<Tier> publishedForms(
      TypeElement navigator, DeclaredType declared, DeclaredType source, FieldShape.Navigator own) {
    // Each form is looked up by its name, which leaves a nested class this module cannot read,
    // such as one missing from a stripped jar, as no form rather than a failed compilation.
    return own.tier().wider().stream()
        .filter(
            tier ->
                Optional.ofNullable(
                        processingEnv
                            .getElementUtils()
                            .getTypeElement(navigator.getQualifiedName() + "." + tier.formName()))
                    .filter(form -> publishedForm(form, tier, navigator, declared, source, own))
                    .isPresent())
        .toList();
  }

  /**
   * Whether a class nested in a published navigator is its form of a tier. A form declares the
   * navigator's type parameters, and is read under the type arguments the method gives the
   * navigator, as the hop that hands it on writes them, so it must accept whatever its navigator
   * does. Only a static member can be read so: javac cannot instantiate an inner class of a generic
   * class on its own. Whether it is a class at all is asked with the rest of what a navigator must
   * be, once it is instantiated.
   */
  private boolean publishedForm(
      TypeElement form,
      Tier tier,
      TypeElement navigator,
      DeclaredType declared,
      DeclaredType source,
      FieldShape.Navigator own) {
    if (!publicStatic(form)
        || !boundedNoTighter(form.getTypeParameters(), navigator.getTypeParameters())) {
      return false;
    }
    DeclaredType instantiated =
        processingEnv
            .getTypeUtils()
            .getDeclaredType(form, declared.getTypeArguments().toArray(TypeMirror[]::new));
    return navigatorShape(form, instantiated, source) instanceof FieldShape.Navigator shape
        && shape.tier() == tier
        && shape.focusType().equals(own.focusType());
  }

  /**
   * The shape of a published navigator class, as a method returns it: a navigator of the tier and
   * focus its own {@code toPath()} reports, where generated code in another package can name it and
   * construct it from that path. Its forms are left to the caller.
   *
   * @param navigator the navigator class
   * @param declared the navigator as it is returned
   * @param source the target, as the returning method is over it
   * @return the navigator's shape without forms, or why it cannot be composed
   */
  private FieldShape navigatorShape(
      TypeElement navigator, DeclaredType declared, DeclaredType source) {
    if (!nameableNavigator(navigator, declared, source)) {
      return new FieldShape.Unrecognised();
    }
    // A navigator says which path it wraps through its own toPath(), and is composed as
    // `new Navigator<>(path)`, so it must take that path in a public constructor.
    // A navigator that extends one of hkj-core's navigator bases inherits it; one that does not
    // declares its own.
    Optional<ExecutableElement> toPath = toPathOf(navigator);
    if (toPath.isEmpty()) {
      return new FieldShape.Unrecognised();
    }
    // The path it wraps is over its own first type parameter, so a navigator constructed from a
    // path over this navigator's source is over that source too. Any parameter it declares after
    // that one takes the type the composing method's return type names for it.
    TypeMirror wraps =
        ProcessorUtils.returnTypeIn(
            processingEnv.getTypeUtils(), (DeclaredType) navigator.asType(), toPath.orElseThrow());
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
            typeNamesOf(arguments.subList(1, arguments.size())),
            List.of())
        : new FieldShape.Unrecognised();
  }

  /**
   * The public no-argument {@code toPath()} a navigator declares or inherits, looked for in the
   * navigator and then in its supertypes. Only methods are asked for, so a class nested in the
   * navigator is never read, and one this module cannot read leaves the navigator composable.
   */
  private Optional<ExecutableElement> toPathOf(TypeElement type) {
    return ElementFilter.methodsIn(type.getEnclosedElements()).stream()
        .filter(
            method ->
                method.getSimpleName().contentEquals("toPath")
                    && method.getModifiers().contains(Modifier.PUBLIC)
                    && method.getParameters().isEmpty())
        .findFirst()
        .or(
            () ->
                processingEnv.getTypeUtils().directSupertypes(type.asType()).stream()
                    .map(supertype -> (TypeElement) ((DeclaredType) supertype).asElement())
                    .flatMap(supertype -> toPathOf(supertype).stream())
                    .findFirst());
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
   * A path's focus as a navigator target, or {@code null} when it is not a navigable record.
   *
   * <p>A component's navigator target is read from the focus of {@link #methodPath}, so the
   * navigator class, the method's return type and the wrapping decision cannot disagree with the
   * method about which fields have one. A container the analysis turns away, or one the record
   * leaves whole, keeps the container itself in focus, so there is no element to navigate to.
   */
  private Target navigableElement(TypeMirror focus, String fromPackage) {
    TypeElement record = navigableTypeElement(focus, fromPackage);
    return record == null ? null : new Target(record, (DeclaredType) focus);
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
     * into it can be instantiated, with what a concrete instantiation would reach. Only {@code
     * skippedTarget} answers this, never {@code reach}.
     */
    record Uninstantiable(Target target, Reach instantiated) implements Reach {}

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
        || !(typeElement.getAnnotation(GenerateFocus.class) instanceof GenerateFocus settings)
        || unreadable(settings)) {
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
   * Notes a container of navigable records that gets no navigator because its record does not widen
   * it, on a record that asks for navigators in so many words. A record that writes {@code
   * generateNavigators = true} may expect the container to be stepped into; one that leaves the
   * default says nothing about it, so is told nothing.
   *
   * <p>Where the element has a field named like a method of the widened path, widening alone would
   * lead straight to the refusal of a navigator that cannot navigate to it, so the note asks for
   * the field to be renamed first. A call written for the navigator, such as {@code
   * byTicker().last()}, may still compile against the whole container's path and fail when the path
   * runs, so the field is not left without a word. Where another field's navigator would share the
   * widened one's class name, the note asks for one of the two to be renamed first, for the same
   * reason.
   */
  private void noteUnwidenedContainer(TypeElement record, RecordComponentElement component) {
    // Only a container left whole stops the method short of a record that widening would reach:
    // the processor widens every other container however the record is annotated.
    if (!writesAttribute(record, "generateNavigators")
        || Arrays.asList(focusSettings(record).includeFields())
            .contains(component.getSimpleName().toString())
        || !shouldGenerateNavigator(record, component)
        || widensCollections(record, component)
        || navigableElement(methodPath(record, component).focus(), companionPackage(record))
            != null) {
      return;
    }
    MethodPath widened = widenedPath(record, component);
    Target element = navigableElement(widened.focus(), companionPackage(record));
    if (element == null || !element.instantiable()) {
      return;
    }
    String recordName = record.getSimpleName().toString();
    String name = component.getSimpleName().toString();
    String elementName = element.record().getSimpleName().toString();
    List<String> clashes = inheritedNames(widened.tier(), element);
    Optional<RecordComponentElement> namesake =
        namesake(record, component, freedByRenaming(record, element.record(), clashes));
    String reason =
        "A navigator steps into a container only where the record widens it, and "
            + recordName
            + " does not widen '"
            + name
            + "'."
            + (clashes.isEmpty()
                ? ""
                : " "
                    + clashSentence("Widened, its navigator", widened.tier(), elementName, clashes))
            + sharedNameSentence(record, component, namesake);
    String widen =
        addToFieldList(record, "widenFields", name, orItsNewName(namesake))
            + " to navigate into its elements";
    String fix =
        renamedFirst(record, component, elementName, clashes, namesake)
            .map(
                renames ->
                    "Rename "
                        + renames
                        + ", then "
                        + widen
                        + ", or "
                        + keepPlainPath(record, name)
                        + " to go without this note.")
            .orElseGet(() -> ProcessorUtils.capitalise(widen) + ".");
    Diagnostics.note(
        processingEnv.getMessager(),
        component,
        "@GenerateFocus",
        "record component '"
            + recordName
            + "."
            + name
            + "' has no navigator over its "
            + elementName
            + " elements.",
        reason,
        fix);
  }

  /**
   * Warns at an includeFields entry that asks for a navigator the field cannot have. Existing
   * target diagnostics take precedence, and the widening declaration reports refused containers.
   * Whether the field navigates, and which other fields could, is read from {@link #methodPath}, as
   * navigator generation reads it; the widening the fix may offer is read from {@link
   * #widenedPath}.
   */
  private void warnIncludedWithoutNavigator(TypeElement record, RecordComponentElement component) {
    String name = component.getSimpleName().toString();
    if (!Arrays.asList(focusSettings(record).includeFields()).contains(name)
        || reachableTarget(record, component) != null
        || unresolvedPart(component.asType()).isPresent()
        || widening(record, component).declined() != null) {
      return;
    }
    // An unreadable target has javac's own error at its annotation, whether reached directly or
    // through a container. Ask about what the method would reach with every container widened.
    MethodPath widened = widenedPath(record, component);
    if (widened.focus() instanceof DeclaredType declared
        && declared.asElement().getAnnotation(GenerateFocus.class) instanceof GenerateFocus settings
        && unreadable(settings)) {
      return;
    }
    Target widenedTarget = navigableElement(widened.focus(), companionPackage(record));
    Target refused = refusedContainerTarget(component, companionPackage(record));

    String recordName = record.getSimpleName().toString();
    String reason;
    String fix;
    if (widenedTarget != null) {
      String targetName = widenedTarget.record().getSimpleName().toString();
      // Widening may expose a path-method clash too, or give the field a navigator whose class name
      // another field's navigator has. In either case offering widening alone would lead straight
      // to a note or error, so the fields are renamed first, as the unwidened-container note asks.
      // A widened element with no navigator, being uninstantiable, takes no class name.
      List<String> clashes = inheritedNames(widened.tier(), widenedTarget);
      Optional<RecordComponentElement> namesake =
          widenedTarget.instantiable()
              ? namesake(
                  record, component, freedByRenaming(record, widenedTarget.record(), clashes))
              : Optional.empty();
      reason =
          "A navigator steps into a container only where the record widens it, and "
              + recordName
              + " does not widen \""
              + name
              + "\"."
              + (clashes.isEmpty()
                  ? ""
                  : " "
                      + clashSentence(
                          "Widened, its navigator", widened.tier(), targetName, clashes))
              + sharedNameSentence(record, component, namesake);
      String widen = addToFieldList(record, "widenFields", name, orItsNewName(namesake));
      fix =
          renamedFirst(record, component, targetName, clashes, namesake)
              .map(
                  renames ->
                      "Rename "
                          + renames
                          + ", then "
                          + widen
                          + ", or "
                          + keepPlainPath(record, name)
                          + " to keep its plain path.")
              .orElseGet(
                  () ->
                      ProcessorUtils.capitalise(widen)
                          + (widenedTarget.instantiable()
                              ? "."
                              : " to step into its " + targetName + " elements."));
    } else if (refused != null) {
      // The concrete type and the widening give this field alone a navigator, and the element has
      // no field named like a path method, or refusedContainerTarget would have found none.
      Optional<RecordComponentElement> namesake =
          namesake(record, component, freedByRenaming(record, refused.record(), List.of()));
      reason =
          ProcessorUtils.simpleTypeName(component.asType())
              + " holds "
              + refused.record().getSimpleName()
              + " records, but its wildcard type arguments prevent widening as written."
              + sharedNameSentence(record, component, namesake);
      String write =
          "write "
              + ProcessorUtils.concreteAlternative((DeclaredType) component.asType()).orElseThrow()
              + " in place of "
              + ProcessorUtils.simpleTypeName(component.asType())
              + " and "
              + addToFieldList(record, "widenFields", name, orItsNewName(namesake));
      fix =
          namesake
              .map(
                  other ->
                      "Rename "
                          + eitherField(record, component, other)
                          + ", then "
                          + write
                          + ", or "
                          + keepPlainPath(record, name)
                          + " to keep its plain path.")
              .orElseGet(() -> ProcessorUtils.capitalise(write) + ".");
    } else {
      TypeElement plain =
          methodPath(record, component).focus() instanceof DeclaredType declared
                  && declared.asElement().getKind() == ElementKind.RECORD
              ? (TypeElement) declared.asElement()
              : null;
      reason =
          "Navigator generation cannot reach a @GenerateFocus record through "
              + ProcessorUtils.simpleTypeName(component.asType())
              + ". It needs a @GenerateFocus record or a supported container of one.";
      if (hasOtherIncludedNavigator(record, component)) {
        fix =
            ProcessorUtils.capitalise(
                    FocusFieldListChecks.removeName(record, "includeFields", name))
                + ".";
      } else {
        List<String> candidates =
            record.getRecordComponents().stream()
                .filter(
                    candidate -> {
                      Target target = unfilteredTarget(record, candidate);
                      return target != null
                          && inheritedNames(methodPath(record, candidate).tier(), target).isEmpty();
                    })
                .map(candidate -> candidate.getSimpleName().toString())
                .toList();
        fix =
            candidates.isEmpty()
                ? "Remove includeFields and set generateNavigators = false in @GenerateFocus on "
                    + recordName
                    + "."
                : !firstReplacementName(record).equals(name)
                    ? ProcessorUtils.capitalise(
                            FocusFieldListChecks.removeName(record, "includeFields", name))
                        + "."
                    : "Replace \""
                        + name
                        + "\" in includeFields on "
                        + recordName
                        + " with a navigator field from "
                        + candidates
                        + ".";
      }
      if (plain != null) {
        reason = plain.getSimpleName() + " is a record without @GenerateFocus. " + reason;
        if (annotationTarget(record, component).isPresent()) {
          // Annotating the record gives a navigator to every included field it would let navigate.
          Optional<RecordComponentElement> namesake =
              namesake(
                  record,
                  component,
                  other ->
                      shouldGenerateNavigator(record, other)
                          && annotationTarget(record, other).filter(plain::equals).isPresent());
          reason += sharedNameSentence(record, component, namesake);
          String annotate = "annotate record " + plain.getSimpleName() + " with @GenerateFocus.";
          fix +=
              " Alternatively, "
                  + namesake
                      .map(
                          other ->
                              "rename "
                                  + eitherField(record, component, other)
                                  + ", then "
                                  + annotate)
                      .orElse(annotate);
        }
      }
    }
    AnnotationMirror annotation =
        ProcessorUtils.findAnnotation(record, GenerateFocus.class.getCanonicalName());
    AnnotationValue included = ProcessorUtils.getAnnotationValue(annotation, "includeFields");
    String explanation = reason;
    String correction = fix;
    ((List<?>) included.getValue())
        .stream()
            .map(AnnotationValue.class::cast)
            .filter(entry -> name.equals(entry.getValue()))
            .forEach(
                entry ->
                    Diagnostics.reportAt(
                        processingEnv.getMessager(),
                        Diagnostic.Kind.WARNING,
                        record,
                        annotation,
                        entry,
                        "@GenerateFocus",
                        "includeFields on "
                            + recordName
                            + " names \""
                            + name
                            + "\", whose field has no navigator.",
                        explanation,
                        correction));
  }

  /** The refused SPI container's resolved element, without reading its generator. */
  private Target refusedContainerTarget(RecordComponentElement component, String fromPackage) {
    if (WideningAnalysis.spiCandidate(component.asType())
        && analysis.spiLookup(component.asType()) instanceof SpiLookup.Refused refused
        && refused.element() != null) {
      TypeElement element = navigableTypeElement(refused.element(), fromPackage);
      if (element != null) {
        Target target = new Target(element, (DeclaredType) refused.element());
        // A refused container left whole by the declaration is a zero-or-more container:
        // zero-or-one containers are already rejected by the declaration's widening pass.
        if (target.instantiable()
            && ProcessorUtils.concreteAlternative((DeclaredType) component.asType()).isPresent()
            && inheritedNames(Tier.TRAVERSAL, target).isEmpty()) {
          return target;
        }
      }
    }
    return null;
  }

  /**
   * The first included field needing a replacement, in the annotation's written order. The caller
   * has ruled out other included navigators and usable corrections; an unwidened clashing target
   * has its own removal, so it does not take this replacement.
   */
  private String firstReplacementName(TypeElement record) {
    return Arrays.stream(focusSettings(record).includeFields())
        .filter(
            name ->
                record.getRecordComponents().stream()
                    .anyMatch(
                        component ->
                            component.getSimpleName().contentEquals(name)
                                && navigableElement(
                                        widenedPath(record, component).focus(),
                                        companionPackage(record))
                                    == null))
        .findFirst()
        .orElseThrow();
  }

  /**
   * Another included field keeps its navigator, or its own diagnostic explains how to obtain one.
   * In either case, removing this name must not disable or duplicate the other field's request.
   */
  private boolean hasOtherIncludedNavigator(TypeElement record, RecordComponentElement component) {
    return record.getRecordComponents().stream()
        .filter(candidate -> !candidate.equals(component))
        .filter(candidate -> shouldGenerateNavigator(record, candidate))
        .anyMatch(
            candidate -> {
              if (reachableTarget(record, candidate) != null
                  || !(skippedTarget(record, candidate) instanceof Reach.None)) {
                return true;
              }
              MethodPath widened = widenedPath(record, candidate);
              Target widenedTarget = navigableElement(widened.focus(), companionPackage(record));
              if (widenedTarget != null) {
                return inheritedNames(widened.tier(), widenedTarget).isEmpty();
              }
              return refusedContainerTarget(candidate, companionPackage(record)) != null
                  || annotationTarget(record, candidate).isPresent();
            });
  }

  /**
   * The record in this compilation whose annotation would give the field a usable navigator: the
   * record the field's method focuses on, as itself or as a container's element. Empty where
   * annotating it would not.
   */
  private Optional<TypeElement> annotationTarget(
      TypeElement record, RecordComponentElement component) {
    MethodPath path = methodPath(record, component);
    if (!(path.focus() instanceof DeclaredType declared)
        || declared.asElement().getKind() != ElementKind.RECORD) {
      return Optional.empty();
    }
    TypeElement plain = (TypeElement) declared.asElement();
    Target annotated = new Target(plain, declared);
    return ProcessorUtils.compiledFromSource(processingEnv.getElementUtils(), plain)
            && annotated.instantiable()
            && inheritedNames(path.tier(), annotated).isEmpty()
            && Reachability.firstHidden(
                    processingEnv.getElementUtils(),
                    companionPackage(record),
                    Reachability.record(plain, plain.getRecordComponents()))
                .isEmpty()
        ? Optional.of(plain)
        : Optional.empty();
  }

  /**
   * Notes a component whose navigator was declined because its target has fields the navigator
   * could not take, on a record that leaves navigators to the default.
   *
   * <p>Where another field's navigator would share this one's class name once those fields are
   * renamed, renaming them alone would lead to the shared-class-name note instead, so the note asks
   * for one of the two to be renamed too.
   */
  private void noteInheritedNamesDeclined(
      TypeElement record, RecordComponentElement component, Target target, List<String> declined) {
    String recordName = record.getSimpleName().toString();
    String name = component.getSimpleName().toString();
    String targetName = target.record().getSimpleName().toString();
    String fields = qualifiedFields(targetName, declined);
    Optional<RecordComponentElement> namesake =
        namesake(record, component, freedByRenaming(record, target.record(), declined));
    Diagnostics.note(
        processingEnv.getMessager(),
        component,
        "@GenerateFocus",
        "record component '"
            + recordName
            + "."
            + name
            + "' has no navigator into "
            + targetName
            + ".",
        "A navigator is "
            + methodPath(record, component).tier().withArticle()
            + ", which has its own "
            + noArgumentCalls(declined)
            + ", so it cannot navigate to "
            + fields
            + ", and "
            + focusClassOf(record).simpleName()
            + "."
            + name
            + "() keeps the path it has without one."
            + sharedNameSentence(record, component, namesake),
        "Rename "
            + renamedFirst(record, component, targetName, declined, namesake).orElseThrow()
            + " to navigate into "
            + targetName
            + ", or "
            + keepPlainPath(record, name)
            + " to keep the path without this note.");
  }

  /**
   * Says why a component's method keeps its plain path. Where {@link #navigatorTarget} declined a
   * navigator the component could reach, the rule that declined it is named; otherwise a container
   * the record left whole may be. The declaration pass reaches each component once, here, so each
   * is told once.
   */
  private void reportPlainPath(TypeElement record, RecordComponentElement component) {
    Target reachable = reachableTarget(record, component);
    if (reachable == null) {
      noteUnwidenedContainer(record, component);
      return;
    }
    List<String> declined = declinedInheritedNames(record, component, reachable);
    if (!declined.isEmpty()) {
      noteInheritedNamesDeclined(record, component, reachable, declined);
      return;
    }
    reportSharedNavigatorName(
        record, component, reachable, sharedNavigatorName(record, component).orElseThrow());
  }

  /**
   * Reports a component whose navigator class would take the name of an earlier component's: an
   * error where the record writes {@code generateNavigators = true}, and otherwise a note, the
   * component keeping its plain path either way.
   *
   * <p>Where the record the component reaches has a field named like one of the navigator's own
   * methods, a rename alone would lead straight to the refusal of a navigator that cannot navigate
   * to it, so the message asks for that field to be renamed too. Only a record that writes {@code
   * generateNavigators = true} gets this far with one, since on the default such a field declines
   * the navigator first.
   */
  private void reportSharedNavigatorName(
      TypeElement record,
      RecordComponentElement component,
      Target target,
      RecordComponentElement earlier) {
    String recordName = record.getSimpleName().toString();
    String name = component.getSimpleName().toString();
    String earlierName = earlier.getSimpleName().toString();
    String targetName = target.record().getSimpleName().toString();
    Tier tier = methodPath(record, component).tier();
    List<String> clashes = inheritedNames(tier, target);
    String explanation =
        "'"
            + recordName
            + "."
            + earlierName
            + "' already has the navigator class "
            + navigatorClassName(earlier)
            + ", and a navigator for '"
            + name
            + "' would take the same name, since a navigator class takes its field's name with the"
            + " first letter in upper case."
            + (clashes.isEmpty()
                ? ""
                : " " + clashSentence("A navigator for '" + name + "'", tier, targetName, clashes));
    String rename =
        "Rename "
            + renamedFirst(record, component, targetName, clashes, Optional.of(earlier))
                .orElseThrow();
    if (writesAttribute(record, "generateNavigators")) {
      Diagnostics.error(
          processingEnv.getMessager(),
          component,
          "@GenerateFocus",
          "the navigator for '"
              + recordName
              + "."
              + name
              + "' cannot share its class name with '"
              + recordName
              + "."
              + earlierName
              + "'.",
          explanation,
          rename + ", or " + keepPlainPath(record, name) + " to keep '" + name + "' a plain path.");
    } else {
      Diagnostics.note(
          processingEnv.getMessager(),
          component,
          "@GenerateFocus",
          "record component '"
              + recordName
              + "."
              + name
              + "' has no navigator, because '"
              + recordName
              + "."
              + earlierName
              + "' has its class name.",
          explanation
              + " "
              + focusClassOf(record).simpleName()
              + "."
              + name
              + "() keeps the path it has without one.",
          rename
              + " to give both a navigator, or "
              + keepPlainPath(record, name)
              + " to keep the path without this note.");
    }
  }

  /** Whether a record's {@code @GenerateFocus} writes the named attribute out. */
  private static boolean writesAttribute(TypeElement record, String attribute) {
    return record.getAnnotationMirrors().stream()
        .filter(
            mirror ->
                ((TypeElement) mirror.getAnnotationType().asElement())
                    .getQualifiedName()
                    .contentEquals(GenerateFocus.class.getCanonicalName()))
        .flatMap(mirror -> mirror.getElementValues().keySet().stream())
        .anyMatch(written -> written.getSimpleName().contentEquals(attribute));
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
    // or as the element of a container its method steps into — gets this navigator method in place
    // of the plain path method.
    Target target = navigatorTarget(recordElement, component);
    if (target == null) {
      reportPlainPath(recordElement, component);
      return null; // Not navigable, filtered out, or declined: use the standard method
    }

    ClassName navigatorClass =
        focusClassOf(recordElement).nestedClass(navigatorClassName(component));
    ParameterizedTypeName returnType =
        navigatorType(
            navigatorClass, recordTypeName, typeNamesOf(target.type().getTypeArguments()));
    TypeName javadocTargetType = ClassName.get(target.record());
    // The declaration pass reaches this component only here, so this walk is the one that reports
    // a generator conflict on it; every other walk into the component stays silent.
    Widening declared = declaredWidening(recordElement, component);

    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder(componentName)
            .addJavadoc(
                "Creates a navigator for the {@code $L} field of {@link $T}.\n\n"
                    + "<p>The returned navigator enables fluent navigation into the fields of\n"
                    + "{@link $T}. For example:\n"
                    + "<pre>{@code\n"
                    + "$L.$L().fieldName().$L(instance);\n"
                    + "}</pre>\n\n"
                    + "@return A navigator for the {@code $L} field.",
                componentName,
                recordTypeName,
                javadocTargetType,
                recordElement.getSimpleName() + "Focus",
                componentName,
                declared.tier().getMethod(),
                componentName)
            // The navigator type and the record's type-parameter bounds are written out here, and
            // so may a cast to a raw or wildcard container.
            .addAnnotations(
                WideningAnalysis.warningSuppression(component, recordElement, declared.steps()))
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
    //
    // The navigator's type arguments are written out rather than inferred, so that its element
    // type, nullness included, is the one the static method declares, whatever a diamond would
    // infer.
    List<Object> args =
        new ArrayList<>(
            List.of(
                returnType,
                WideningAnalysis.FOCUS_PATH_CLASS,
                Lens.class,
                recordTypeName,
                componentName,
                recordTypeName,
                constructorArgs,
                componentName));
    String wideningExpression =
        WideningAnalysis.expression(declared.steps(), args, analysis.targetPackage());
    methodBuilder.addStatement(
        "return new $T($T.of($T.of($T::$L, (source, newValue) -> new $T($L)), \"$L\")"
            + wideningExpression
            + ")",
        args.toArray());

    return methodBuilder.build();
  }
}
