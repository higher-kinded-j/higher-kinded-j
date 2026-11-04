// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
import org.higherkindedj.optics.processing.NavigatorClassGenerator.RenameFirst;
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
 * error at the value, offering the component it most likely meant among those the list can use and
 * does not name already, with any rename that must come first. A list the record's other settings
 * leave unread, a {@code widenFields} name whose method widening cannot change, and a name a list
 * writes again draw a warning instead: the generated class is the one the record would have without
 * them, so only the annotation misleads.
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
   * @param navigatorGenerator the generator that says which components a navigator can be given,
   *     and what must be renamed first
   */
  FocusFieldListChecks(
      Messager messager, WideningAnalysis analysis, NavigatorClassGenerator navigatorGenerator) {
    this.messager = messager;
    this.analysis = analysis;
    this.navigatorGenerator = navigatorGenerator;
  }

  /**
   * Reports each name in the record's field lists that matches no component or repeats an earlier
   * one, each list its other settings leave unread, and each {@code widenFields} name whose method
   * widening cannot change.
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
      }
    }
    written.forEach((list, value) -> checkNames(record, annotation, list, value, components));

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
    // A name written again is told once, at its first copy, and the copy draws its own warning.
    for (AnnotationValue entry : firstCopies(widen)) {
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

  /** The names an array value holds, in the order written. */
  private static List<String> namesIn(AnnotationValue array) {
    return entries(array).stream().map(FocusFieldListChecks::nameIn).toList();
  }

  /** The first entry of each name an array value holds, in the order written. */
  private static Collection<AnnotationValue> firstCopies(AnnotationValue array) {
    return entries(array).stream()
        .collect(
            Collectors.toMap(
                FocusFieldListChecks::nameIn,
                Function.identity(),
                (first, repeat) -> first,
                LinkedHashMap::new))
        .values();
  }

  /**
   * Reports each name in one list that repeats an earlier one, or that matches no component. A name
   * written again is reported as a repeat at each later copy, and only its first copy is checked
   * against the components, so that following every fix line leaves it written once.
   *
   * <p>A component the list names already, or one offered in place of another name, is not offered
   * again, since following both fix lines would name it twice. A name that spells a component in
   * another case, or as its accessor, is offered it before any near misspelling is, so that which
   * name gets an offer does not turn on the order they are written in.
   */
  private void checkNames(
      TypeElement record,
      AnnotationMirror annotation,
      FieldList list,
      AnnotationValue value,
      Map<String, RecordComponentElement> components) {
    Set<String> seen = new HashSet<>();
    List<AnnotationValue> unknown = new ArrayList<>();
    for (AnnotationValue entry : entries(value)) {
      if (!seen.add(nameIn(entry))) {
        reportRepeated(record, annotation, list, entry);
      } else if (!components.containsKey(nameIn(entry))) {
        unknown.add(entry);
      }
    }
    Set<String> claimed = new HashSet<>(seen);
    Map<String, RecordComponentElement> offers = new HashMap<>();
    for (boolean sameValue : List.of(true, false)) {
      for (AnnotationValue entry : unknown) {
        String name = nameIn(entry);
        if (!offers.containsKey(name)) {
          offer(record, list, name, components, claimed, sameValue)
              .ifPresent(
                  meant -> {
                    offers.put(name, meant);
                    claimed.add(meant.getSimpleName().toString());
                  });
        }
      }
    }
    unknown.forEach(
        entry ->
            reportUnknown(
                record,
                annotation,
                list,
                value,
                entry,
                components,
                Optional.ofNullable(offers.get(nameIn(entry)))));
  }

  /** Warns at a name one list writes again, which changes nothing the first copy did not. */
  private void reportRepeated(
      TypeElement record, AnnotationMirror annotation, FieldList list, AnnotationValue entry) {
    String recordName = record.getSimpleName().toString();
    String name = nameIn(entry);
    Diagnostics.reportAt(
        messager,
        Diagnostic.Kind.WARNING,
        record,
        annotation,
        entry,
        TAG,
        list.attribute + " on " + recordName + " names \"" + name + "\" again.",
        list.attribute + " reads each name once, so a name written again changes nothing.",
        "Remove the repeated \"" + name + "\" from " + list.attribute + " on " + recordName + ".");
  }

  /**
   * The component a name that matches none most likely meant, among those the list can use and that
   * are not claimed already, so that following the fix line draws no further error or warning.
   *
   * @param claimed the names the list writes, and the components offered in place of other names
   * @param sameValue whether to offer only a component the name spells in another case or as its
   *     accessor, rather than also a near misspelling of one
   */
  private Optional<RecordComponentElement> offer(
      TypeElement record,
      FieldList list,
      String name,
      Map<String, RecordComponentElement> components,
      Set<String> claimed,
      boolean sameValue) {
    List<String> candidates =
        components.entrySet().stream()
            .filter(candidate -> !claimed.contains(candidate.getKey()))
            .filter(candidate -> usable(record, list, candidate.getValue()))
            .map(Map.Entry::getKey)
            .toList();
    return (sameValue
            ? NamingConvention.ACCESSOR.sameValueAmong(name, candidates)
            : NamingConvention.ACCESSOR.nearest(name, candidates))
        .map(components::get);
  }

  /**
   * Whether naming a component in a list does what the list is for, without a warning or error of
   * its own: {@code includeFields} names a component a navigator can be given, and {@code
   * widenFields} one the record does not widen already whose method widening changes, without
   * turning its container away. {@code excludeFields} can name any component.
   */
  private boolean usable(TypeElement record, FieldList list, RecordComponentElement component) {
    return switch (list) {
      case INCLUDE -> navigatorGenerator.isNavigatorField(record, component);
      case EXCLUDE -> true;
      case WIDEN -> {
        Widening named = analysis.analyse(component, true);
        yield !NavigatorClassGenerator.widensCollections(record, component)
            && named.declined() == null
            && named.steps().size() != analysis.analyse(component, false).steps().size();
      }
    };
  }

  /**
   * What must be renamed before the fix line names the component it offers, where naming it would
   * give the component a navigator: {@code excludeFields} gives none.
   */
  private Optional<RenameFirst> renameBefore(
      TypeElement record, FieldList list, RecordComponentElement component) {
    return switch (list) {
      case INCLUDE -> navigatorGenerator.renameBeforeIncluding(record, component);
      case EXCLUDE -> Optional.empty();
      case WIDEN -> navigatorGenerator.renameBeforeWidening(record, component);
    };
  }

  /**
   * Refuses a name that matches no component, offering the component it most likely meant, with any
   * rename that must come first, or, where there is none to offer, the components there are.
   */
  private void reportUnknown(
      TypeElement record,
      AnnotationMirror annotation,
      FieldList list,
      AnnotationValue value,
      AnnotationValue entry,
      Map<String, RecordComponentElement> components,
      Optional<RecordComponentElement> offer) {
    String recordName = record.getSimpleName().toString();
    String name = nameIn(entry);
    List<String> names = List.copyOf(components.keySet());
    Optional<RenameFirst> rename = offer.flatMap(meant -> renameBefore(record, list, meant));
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
        list.attribute
            + " "
            + list.effect
            + "."
            + offer.map(meant -> " Did you mean '" + meant.getSimpleName() + "'?").orElse("")
            + (names.isEmpty()
                ? " " + recordName + " has no components."
                : " Found on " + recordName + ": " + names + ".")
            + rename.map(RenameFirst::reason).orElse(""),
        offer
            .map(
                meant -> {
                  String replace =
                      "replace \""
                          + name
                          + "\" with \""
                          + meant.getSimpleName()
                          + "\""
                          + rename.map(RenameFirst::aside).orElse("")
                          + " in "
                          + list.attribute
                          + " on "
                          + recordName
                          + ".";
                  return rename
                      .map(first -> "Rename " + first.renames() + ", then " + replace)
                      .orElseGet(() -> ProcessorUtils.capitalise(replace));
                })
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
                            + "."));
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
    Widening named = analysis.analyse(component, true);
    if (named.steps().size() != unnamed.steps().size() || named.declined() != null) {
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
   * all it holds, however many times it is written, so that following it leaves no empty list
   * behind.
   */
  private static String removal(
      TypeElement record, FieldList list, AnnotationValue value, String name) {
    return namesIn(value).stream().allMatch(name::equals)
        ? "remove " + list.attribute + " from @GenerateFocus on " + record.getSimpleName()
        : NavigatorClassGenerator.removeName(record, list.attribute, name);
  }
}
