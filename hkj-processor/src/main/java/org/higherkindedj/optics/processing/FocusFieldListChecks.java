// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.processing.WideningAnalysis.StepKind;
import org.higherkindedj.optics.processing.WideningAnalysis.Widening;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.NamingConvention;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Checks the field names a {@code @GenerateFocus} record lists in {@code includeFields}, {@code
 * excludeFields} and {@code widenFields}, and the settings beside which a list changes nothing.
 *
 * <p>The lists are read by name, so a name that matches no component would change nothing without a
 * word, and in {@code includeFields} would leave every field without a navigator. Such a name is an
 * error at the value, offering the component it most likely meant. Repeated names warn at each
 * later occurrence. A list the record's other settings leave unread, and a {@code widenFields} name
 * whose method widening cannot change, draw a warning instead: the generated class is the one the
 * record would have without them, so only the annotation misleads.
 *
 * <p>Every message points at the value written, and its fix line can be followed as written. A
 * record can draw more than one, as an unknown name in a list the record never reads does, and
 * following every fix line it draws leaves none.
 */
final class FocusFieldListChecks {

  private static final String TAG = "@GenerateFocus";

  /** Which components get navigators under {@code includeFields}, once navigators are on. */
  private static final String INCLUDED = "the components includeFields names";

  /** Which components get navigators under {@code excludeFields}, once navigators are on. */
  private static final String EXCLUDED = "every component but those excludeFields names";

  /** A list of field names the annotation reads, with what a name in it does. */
  private enum FieldList {
    INCLUDE(
        "includeFields",
        "gives navigators only to the components it names, so a name that matches none gives no"
            + " component a navigator"),
    EXCLUDE(
        "excludeFields",
        "leaves the components it names without navigators, so a name that matches none leaves"
            + " out nothing"),
    WIDEN(
        "widenFields",
        "widens the containers of the components it names, so a name that matches none widens"
            + " nothing");

    private final String attribute;
    private final String effect;

    FieldList(String attribute, String effect) {
      this.attribute = attribute;
      this.effect = effect;
    }
  }

  private final Messager messager;
  private final WideningAnalysis analysis;
  private final NavigatorClassGenerator navigatorGenerator;

  /**
   * Creates the checks for one record's Focus companion.
   *
   * @param messager the messager each finding is reported through
   * @param analysis the analysis that says what each component's Focus method widens to
   * @param navigatorGenerator the generator used to check navigator namesakes
   */
  FocusFieldListChecks(
      Messager messager, WideningAnalysis analysis, NavigatorClassGenerator navigatorGenerator) {
    this.messager = messager;
    this.analysis = analysis;
    this.navigatorGenerator = navigatorGenerator;
  }

  /**
   * Reports unknown and repeated names in the record's field lists, each list its other settings
   * leave unread, and each {@code widenFields} name whose method widening cannot change.
   *
   * <p>Asked only of a record whose annotation reads as written (see {@link
   * NavigatorClassGenerator#unreadable}): a value javac could not resolve is no array of names.
   *
   * @param record a record annotated {@code @GenerateFocus}
   */
  void check(TypeElement record) {
    AnnotationMirror annotation =
        ProcessorUtils.findAnnotation(record, GenerateFocus.class.getCanonicalName());
    Map<String, RecordComponentElement> components = componentsByName(record);
    // Only a list written out, and not written empty, has a name to check or anything to change.
    Map<FieldList, AnnotationValue> written = new EnumMap<>(FieldList.class);
    for (FieldList list : FieldList.values()) {
      AnnotationValue value = ProcessorUtils.getAnnotationValue(annotation, list.attribute);
      if (value != null && !entries(value).isEmpty()) {
        written.put(list, value);
        reportDuplicates(record, annotation, list, value);
      }
    }
    written.forEach(
        (list, value) -> {
          Set<String> reportedUnknown = new HashSet<>();
          entries(value).stream()
              .filter(entry -> !components.containsKey(nameIn(entry)))
              .filter(entry -> reportedUnknown.add(nameIn(entry)))
              .forEach(
                  entry -> reportUnknown(record, annotation, list, value, entry, components));
        });

    GenerateFocus settings = record.getAnnotation(GenerateFocus.class);
    if (!settings.generateNavigators()) {
      // With both lists written, turning navigators on would leave one beside the other, so each
      // is offered only its removal.
      boolean both =
          written.containsKey(FieldList.INCLUDE) && written.containsKey(FieldList.EXCLUDE);
      for (FieldList list : List.of(FieldList.INCLUDE, FieldList.EXCLUDE)) {
        AnnotationValue value = written.get(list);
        if (value != null) {
          reportNavigatorsOff(record, annotation, list, value, both);
        }
      }
    } else if (written.containsKey(FieldList.INCLUDE) && written.containsKey(FieldList.EXCLUDE)) {
      reportExcludeBesideInclude(record, annotation, written.get(FieldList.EXCLUDE));
    }

    AnnotationValue widen = written.get(FieldList.WIDEN);
    if (widen == null) {
      return;
    }
    if (settings.widenCollections()) {
      reportWidenedByCollections(record, annotation, widen);
      return;
    }
    for (AnnotationValue entry : entries(widen)) {
      RecordComponentElement component = components.get(nameIn(entry));
      if (component != null) {
        checkWidenable(record, annotation, widen, entry, component);
      }
    }
  }

  /** A record's components by name, in declaration order. */
  private static Map<String, RecordComponentElement> componentsByName(TypeElement record) {
    Map<String, RecordComponentElement> components = new LinkedHashMap<>();
    for (RecordComponentElement component : record.getRecordComponents()) {
      components.put(component.getSimpleName().toString(), component);
    }
    return components;
  }

  /** The names an array value holds, each as the value javac reads it, in the order written. */
  private static List<AnnotationValue> entries(AnnotationValue array) {
    return ((List<?>) array.getValue()).stream().map(AnnotationValue.class::cast).toList();
  }

  /** The name one entry of a field list writes. */
  private static String nameIn(AnnotationValue entry) {
    return (String) entry.getValue();
  }


  /** Reports each repeated name after its first occurrence in one field list. */
  private void reportDuplicates(
      TypeElement record, AnnotationMirror annotation, FieldList list, AnnotationValue value) {
    Set<String> seen = new HashSet<>();
    for (AnnotationValue entry : entries(value)) {
      String name = nameIn(entry);
      if (!seen.add(name)) {
        Diagnostics.reportAt(
            messager,
            Diagnostic.Kind.WARNING,
            record,
            annotation,
            entry,
            TAG,
            list.attribute
                + " on "
                + record.getSimpleName()
                + " names \""
                + name
                + "\" again.",
            "The first \"" + name + "\" already names it, so this changes nothing.",
            "Remove the repeated \""
                + name
                + "\" from "
                + list.attribute
                + " on "
                + record.getSimpleName()
                + ".");
      }
    }
  }

  /**
   * Whether a component is a safe suggestion for a field-list name.
   *
   * <p>Widening suggestions must change the path, and an include suggestion must not reserve a
   * navigator class name already taken by another included field.
   */
  /**
   * Whether a component can usefully be suggested for a field-list correction.
   *
   * @param list the field list being corrected
   * @param component the component being considered
   * @return whether naming it changes widening, or the list does not test widening
   */
  private boolean usableSuggestion(FieldList list, RecordComponentElement component) {
    return list != FieldList.WIDEN || widenFieldsChanges(component);
  }

  /** Whether naming a component in {@code widenFields} changes its generated path. */
  private boolean widenFieldsChanges(RecordComponentElement component) {
    Widening unnamed = analysis.analyse(component, false);
    Widening named = analysis.analyse(component, true);
    return named.steps().size() != unnamed.steps().size() || named.declined() != null;
  }

  /**
   * Refuses a name that matches no component, offering the component it most likely meant: a near
   * misspelling, a different case, or the accessor spelling of the component's name.
   */
  private void reportUnknown(
      TypeElement record,
      AnnotationMirror annotation,
      FieldList list,
      AnnotationValue value,
      AnnotationValue entry,
      Map<String, RecordComponentElement> components) {
    String recordName = record.getSimpleName().toString();
    String name = nameIn(entry);
    List<String> names = List.copyOf(components.keySet());
    List<String> alreadyNamed = entries(value).stream().map(FocusFieldListChecks::nameIn).toList();
    Optional<String> near =
        NamingConvention.ACCESSOR.nearest(
            name,
            names.stream()
                .filter(candidate -> !alreadyNamed.contains(candidate))
                .filter(candidate -> usableSuggestion(list, components.get(candidate)))
                .toList());
    Optional<RecordComponentElement> namesake =
        list == FieldList.INCLUDE && near.isPresent()
            ? navigatorGenerator.namesake(record, components.get(near.get()), other -> false)
            : Optional.empty();
    String namesakeExplanation =
        namesake
            .map(
                other ->
                    navigatorGenerator.suggestedNamesakeSentence(
                        record, components.get(near.orElseThrow()), other))
            .orElse("");
    String explanation =
        list.attribute
            + " "
            + list.effect
            + "."
            + near.map(meant -> " Did you mean '" + meant + "'?").orElse("")
            + (names.isEmpty()
                ? " " + recordName + " has no components."
                : " Found on " + recordName + ": " + names + ".")
            + namesakeExplanation;
    String fix =
        near.map(
                meant ->
                    namesake.isPresent()
                        ? navigatorGenerator.suggestedNamesakeFixLine(
                            record, components.get(meant), namesake.get(), name, list.attribute)
                        : "Replace \""
                            + name
                            + "\" with \""
                            + meant
                            + "\" in "
                            + list.attribute
                            + " on "
                            + recordName
                            + ".")
            .orElseGet(
                () ->
                    names.isEmpty()
                        ? ProcessorUtils.capitalise(removal(record, list, value, name)) + "."
                        : "Write a component of "
                            + recordName
                            + " in place of \""
                            + name
                            + "\", or "
                            + removal(record, list, value, name)
                            + ".");
    Diagnostics.reportAt(
        messager,
        Diagnostic.Kind.ERROR,
        record,
        annotation,
        entry,
        TAG,
        list.attribute
            + " on "
            + recordName
            + " names \""
            + name
            + "\", which is not a component of "
            + recordName
            + ".",
        explanation,
        fix);
  }

  /**
   * Warns of {@code includeFields} or {@code excludeFields} on a record that turns navigators off,
   * where neither is ever read. Turning navigators on is offered only where the other list is not
   * written too, since one beside the other draws a warning of its own.
   */
  private void reportNavigatorsOff(
      TypeElement record,
      AnnotationMirror annotation,
      FieldList list,
      AnnotationValue value,
      boolean both) {
    String recordName = record.getSimpleName().toString();
    Diagnostics.reportAt(
        messager,
        Diagnostic.Kind.WARNING,
        record,
        annotation,
        value,
        TAG,
        list.attribute
            + " on "
            + recordName
            + " changes nothing, because generateNavigators = false turns navigators off.",
        "includeFields and excludeFields choose which components get navigators, and with"
            + " navigators off none does.",
        "Remove "
            + list.attribute
            + " from @GenerateFocus on "
            + recordName
            + (both
                ? "."
                : ", or remove generateNavigators = false to give navigators to "
                    + (list == FieldList.INCLUDE ? INCLUDED : EXCLUDED)
                    + "."));
  }

  /**
   * Warns of {@code excludeFields} beside a non-empty {@code includeFields}, which alone decides
   * which components get navigators.
   */
  private void reportExcludeBesideInclude(
      TypeElement record, AnnotationMirror annotation, AnnotationValue exclude) {
    String recordName = record.getSimpleName().toString();
    Diagnostics.reportAt(
        messager,
        Diagnostic.Kind.WARNING,
        record,
        annotation,
        exclude,
        TAG,
        "excludeFields on "
            + recordName
            + " changes nothing, because includeFields is set beside it.",
        "A non-empty includeFields gives navigators to the components it names and to no others,"
            + " so excludeFields is never read.",
        "Remove excludeFields from @GenerateFocus on "
            + recordName
            + ", or remove includeFields to give navigators to "
            + EXCLUDED
            + ".");
  }

  /** Warns of {@code widenFields} beside {@code widenCollections = true}, which widens them all. */
  private void reportWidenedByCollections(
      TypeElement record, AnnotationMirror annotation, AnnotationValue widen) {
    String recordName = record.getSimpleName().toString();
    Diagnostics.reportAt(
        messager,
        Diagnostic.Kind.WARNING,
        record,
        annotation,
        widen,
        TAG,
        "widenFields on "
            + recordName
            + " changes nothing, because widenCollections = true widens every field widenFields"
            + " could name.",
        "widenCollections = true widens every Map, array of objects and third-party collection,"
            + " which is all widenFields can widen.",
        "Remove widenFields from @GenerateFocus on "
            + recordName
            + ", or remove widenCollections = true to widen only the fields widenFields names.");
  }

  /**
   * Warns of a {@code widenFields} name whose method widening cannot change: one that widens its
   * container whether named or not, or one that holds no container {@code widenFields} widens.
   *
   * <p>{@code widenFields} widens only a zero-or-more SPI container, so a walk it changes either
   * steps further or turns such a container away. A container turned away either way is refused at
   * the component already, and draws nothing more here.
   */
  private void checkWidenable(
      TypeElement record,
      AnnotationMirror annotation,
      AnnotationValue widen,
      AnnotationValue entry,
      RecordComponentElement component) {
    Widening unnamed = analysis.analyse(component, false);
    if (widenFieldsChanges(component)) {
      return;
    }
    String recordName = record.getSimpleName().toString();
    String name = nameIn(entry);
    TypeMirror type = component.asType();
    String written = ProcessorUtils.simpleTypeName(type);
    String removal = ProcessorUtils.capitalise(removal(record, FieldList.WIDEN, widen, name));
    // A @Nullable component widens through .nullable() whatever it holds, which is no container.
    if (unnamed.steps().stream().anyMatch(step -> step.kind() != StepKind.NULLABLE)) {
      Diagnostics.reportAt(
          messager,
          Diagnostic.Kind.WARNING,
          record,
          annotation,
          entry,
          TAG,
          "widenFields on "
              + recordName
              + " names \""
              + name
              + "\", whose "
              + written
              + " is widened without it.",
          recordName
              + "Focus."
              + name
              + "() returns "
              + unnamed.tier().withArticle()
              + " whether widenFields names it or not.",
          removal + ".");
      return;
    }
    // A type with type arguments may be a collection whose generator is missing from the path,
    // which is the remedy then, rather than dropping the name.
    String generatorOffer =
        type.getKind() == TypeKind.DECLARED && !((DeclaredType) type).getTypeArguments().isEmpty()
            ? ", or, where "
                + ((DeclaredType) type).asElement().getSimpleName()
                + " is a collection, add a TraversableGenerator that supports it to the annotation"
                + " processor path, as hkj-processor-plugins does for Eclipse Collections, Guava,"
                + " Vavr, Apache Commons and PCollections"
            : "";
    Diagnostics.reportAt(
        messager,
        Diagnostic.Kind.WARNING,
        record,
        annotation,
        entry,
        TAG,
        "widenFields on "
            + recordName
            + " names \""
            + name
            + "\", which holds no container widenFields widens.",
        "widenFields widens a Map, an array of objects, or another collection that a"
            + " TraversableGenerator on the processor path can widen, and none can widen "
            + written
            + ".",
        removal + generatorOffer + ".");
  }

  /**
   * The words a fix line uses to take a name out of a list: the attribute itself where the name is
   * all it holds, so that following it leaves no empty list behind.
   */
  private static String removal(
      TypeElement record, FieldList list, AnnotationValue value, String name) {
    return entries(value).size() == 1
        ? "remove " + list.attribute + " from @GenerateFocus on " + record.getSimpleName()
        : removeName(record, list.attribute, name);
  }

  /**
   * The words a fix line uses to take one name out of a list that holds others, shared with the
   * navigator fix lines so that the two spell it alike.
   *
   * @param record the record whose annotation writes the list
   * @param attribute the list's attribute name
   * @param name the name to take out
   * @return the words, starting in lower case
   */
  static String removeName(TypeElement record, String attribute, String name) {
    return "remove \"" + name + "\" from " + attribute + " on " + record.getSimpleName();
  }
}
