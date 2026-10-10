// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The order a {@code @ViaConstructor} lens rebuilds its source type through, and whether the
 * constructor the generated call binds takes it.
 *
 * <p>The generated lens rebuilds through {@code new S(source.a(), newValue, source.c())}: the
 * accessor each name in the order reads, and the new value where the lens method's own name stands.
 * The order is the annotation's {@code parameterOrder}, or, where that is left empty, the one the
 * constructors' parameter names give. Either way it is held to the constructor {@link CallBinding}
 * says the call binds, so an order no constructor takes, or one javac cannot choose a constructor
 * for, is refused at the spec method rather than in the generated file.
 *
 * <p>A constructor's parameter names are the only thing that tells two parameters of one type
 * apart. A parameter named after an accessor that reads a value it takes, under one of {@link
 * ProcessorUtils#getterSpellings}, has to be passed that accessor's value: {@code {"y", "x"}} for a
 * lens {@code x} over {@code Point(int x, int y)} compiles, and writes each value into the other's
 * field. A parameter named after nothing the type reads, as every parameter of a class compiled
 * with neither {@code -parameters} nor {@code -g} is, leaves its place to the order.
 *
 * <p>Every order a refusal offers to paste is one resolved here to a constructor that follows it,
 * so that a fix line does not lead into another refusal; where none is, the remedy is another
 * strategy.
 *
 * <p>Split from {@link CopyStrategyChecks}, which reads the annotation and decides how the focus is
 * passed, and asks this class for the order.
 */
final class ConstructorOrderChecks {

  /**
   * One lens's rebuild through a constructor: what each check of its order reads.
   *
   * @param method the annotated lens method, named after its getter
   * @param source the source type {@code S}, as the spec names it
   * @param focus the lens's focus
   * @param members the methods of {@code S} the generated class can call
   * @param constructors the constructors of {@code S} the generated class can call
   * @param targetPackage the package the optics class is generated into
   * @param unboxing the primitive an order passes a focus as, or null where it passes it as it is,
   *     as {@link CopyStrategyChecks} decides it
   */
  record Rebuild(
      ExecutableElement method,
      DeclaredType source,
      TypeMirror focus,
      List<ExecutableElement> members,
      List<ExecutableElement> constructors,
      String targetPackage,
      BiFunction<List<String>, TypeMirror, TypeMirror> unboxing) {

    /** The primitive the call for an order passes the focus as, or null to pass it as it is. */
    TypeMirror unboxed(List<String> order) {
      return unboxing.apply(order, focus);
    }

    /** The same rebuild with another focus, as a fix that names one would make it. */
    Rebuild withFocus(TypeMirror another) {
      return new Rebuild(method, source, another, members, constructors, targetPackage, unboxing);
    }

    /** The lens method's own name, which an order writes where the new value goes. */
    String fieldName() {
      return method.getSimpleName().toString();
    }

    /** The source type as a diagnostic names it. */
    String sourceName() {
      return ProcessorUtils.simpleTypeName(source);
    }
  }

  private static final String TAG = "@ViaConstructor";

  private final Types typeUtils;
  private final Elements elementUtils;
  private final Messager messager;

  /**
   * Creates the checks over a round's utilities.
   *
   * @param typeUtils the round's type utilities; must not be null
   * @param elementUtils the round's element utilities; must not be null
   * @param messager where refusals are reported; must not be null
   */
  ConstructorOrderChecks(Types typeUtils, Elements elementUtils, Messager messager) {
    this.typeUtils = typeUtils;
    this.elementUtils = elementUtils;
    this.messager = messager;
  }

  /**
   * The order the lens rebuilds through: the one written, or where none is, the one the parameter
   * names give.
   *
   * @param rebuild the lens and what it rebuilds
   * @param written the annotation's order, each name an accessor of the source type and the lens's
   *     own among them, or empty to read one from the parameter names
   * @return the order, or empty where it is refused, which has been reported
   */
  Optional<List<String>> order(Rebuild rebuild, List<String> written) {
    boolean derived = written.isEmpty();
    Optional<List<String>> order = derived ? derivedOrder(rebuild) : Optional.of(written);
    // An order read from the names is held to a longer constructor only once the call is known to
    // rebuild through the shorter one, so that the refusal can say so.
    if (order.isEmpty()
        || rebuildsThroughUnfitConstructor(rebuild, order.get(), derived)
        || derived && leavesOutALongerConstructor(rebuild, order.get())) {
      return Optional.empty();
    }
    return order;
  }

  /**
   * Reports a written order that names no argument for the lens's own value, and returns whether it
   * did: the generated lens passes the value it sets where the lens's name stands, so such an order
   * would set nothing.
   *
   * @param rebuild the lens and what it rebuilds
   * @param written the annotation's order, empty where it writes none
   * @return true when the order leaves the lens out, and an error was reported
   */
  boolean leavesOutTheLens(Rebuild rebuild, List<String> written) {
    String fieldName = rebuild.fieldName();
    if (written.isEmpty() || written.contains(fieldName)) {
      return false;
    }
    Diagnostics.error(
        messager,
        rebuild.method(),
        TAG,
        "'parameterOrder' names no argument for the lens's own '" + fieldName + "'.",
        "The generated lens rebuilds '"
            + rebuild.sourceName()
            + "' from the order given, and passes the value it sets where the lens's own name"
            + " stands; naming it nowhere would set nothing.",
        namedRemedy(rebuild, written, List.of())
            .orElse(
                "Add '"
                    + fieldName
                    + "' to @ViaConstructor's 'parameterOrder', at the place the constructor takes"
                    + " it, or "
                    + CopyStrategyChecks.rebuildWith(rebuild.sourceName(), TAG)
                    + "."));
    return true;
  }

  /**
   * The order a lens that writes none rebuilds through, read from the constructors' parameter
   * names, or empty where they give no one order, which has been reported.
   *
   * <p>A constructor gives an order where each of its parameters is named after an accessor that
   * reads a value it takes, and one of them is the lens's own: {@code Point(int x, int y)} gives
   * {@code {"getX", "getY"}} to a lens named {@code getX} over a {@code Point} whose accessors are
   * {@code getX()} and {@code getY()}. Constructors that give the same order are one answer, as
   * overloads over wider types do; two that give different orders leave the choice to the author.
   */
  private Optional<List<String>> derivedOrder(Rebuild rebuild) {
    String fieldName = rebuild.fieldName();
    String source = rebuild.sourceName();
    List<List<String>> orders = namedOrders(rebuild, List.of());
    if (orders.size() == 1) {
      return Optional.of(orders.getFirst());
    }
    if (takesNoParameterForTheLens(rebuild, rebuild.constructors())) {
      Diagnostics.error(
          messager,
          rebuild.method(),
          TAG,
          "No constructor of '"
              + source
              + "' takes a parameter named after the lens's own '"
              + fieldName
              + "()'.",
          "With no 'parameterOrder' written, the generated lens reads its order from a"
              + " constructor's parameter names, and each parameter of every constructor is named"
              + " after another accessor. "
              + constructorsFound(rebuild),
          cannotSetFix(source, fieldName));
      return Optional.empty();
    }
    if (orders.isEmpty()) {
      // Only a class file can have lost its names, and javac then reads them as arg0, arg1.
      boolean unnamed =
          !ProcessorUtils.compiledFromSource(elementUtils, rebuild.source().asElement())
              && rebuild.constructors().stream()
                  .flatMap(constructor -> constructor.getParameters().stream())
                  .anyMatch(parameter -> parameter.getSimpleName().toString().matches("arg\\d+"));
      Diagnostics.error(
          messager,
          rebuild.method(),
          TAG,
          "No 'parameterOrder' is written, and no constructor of '"
              + source
              + "' gives one by its parameter names.",
          "With no order written, the generated lens takes it from a constructor each of whose"
              + " parameters is named after an accessor of '"
              + source
              + "' that reads a value it takes, as 'name()', 'getName()' or 'isName()' for a"
              + " parameter 'name', and one of them the lens's own '"
              + fieldName
              + "()'. "
              + constructorsFound(rebuild)
              + (unnamed
                  ? " A class compiled with neither -parameters nor -g keeps no parameter names, and"
                      + " javac reads them as 'arg0', 'arg1'."
                  : ""),
          rebuild.constructors().isEmpty()
              ? anotherStrategy(source)
              : "Name in @ViaConstructor's 'parameterOrder' the accessors '"
                  + source
                  + "' declares, in the order its constructor takes them, or "
                  + CopyStrategyChecks.rebuildWith(source, TAG)
                  + ".");
      return Optional.empty();
    }
    // Only an order the call would rebuild through a constructor that follows is offered, the
    // longest first; where none is, the focus the getter reads may be what keeps them out.
    List<List<String>> holding =
        orders.stream()
            .filter(named -> holds(rebuild, named))
            .sorted(Comparator.comparingInt((List<String> named) -> named.size()).reversed())
            .toList();
    TypeMirror readAsFocus = readAsFocus(rebuild);
    boolean focusFixes =
        !typeUtils.isSameType(readAsFocus, rebuild.focus())
            && orders.stream().anyMatch(named -> holds(rebuild.withFocus(readAsFocus), named));
    Diagnostics.error(
        messager,
        rebuild.method(),
        TAG,
        "No 'parameterOrder' is written, and more than one constructor of '"
            + source
            + "' gives one by its parameter names.",
        "With no order written, the generated lens takes it from the one constructor each of whose"
            + " parameters is named after an accessor that reads a value it takes, and here their"
            + " orders differ, so the one to rebuild through has to be written.",
        !holding.isEmpty()
            ? "Write the one the lens rebuilds through: " + offered(holding) + "."
            : focusFixes
                ? CopyStrategyChecks.declareFocusAs(readAsFocus, fieldName)
                    + "; otherwise "
                    + CopyStrategyChecks.rebuildWith(source, TAG)
                    + "."
                : "Write the order of the constructor the lens should rebuild through as"
                    + " @ViaConstructor's 'parameterOrder', or "
                    + CopyStrategyChecks.rebuildWith(source, TAG)
                    + ".");
    return Optional.empty();
  }

  /**
   * Reports an order read from the parameter names that a longer constructor would take more than,
   * and returns whether it did: rebuilt through the shorter one, a value only the longer takes may
   * come back reset after every set. Of a record's constructors only the canonical one counts,
   * since it takes every value the record holds and every other constructor hands on to it.
   */
  private boolean leavesOutALongerConstructor(Rebuild rebuild, List<String> order) {
    boolean record = rebuild.source().asElement().getKind() == ElementKind.RECORD;
    Optional<ExecutableElement> longer =
        rebuild.constructors().stream()
            .filter(constructor -> !record || isCanonical(constructor))
            .filter(constructor -> constructor.getParameters().size() > order.size())
            .findFirst();
    if (longer.isEmpty()) {
      return false;
    }
    String signature = constructorSignature(longer.get());
    Diagnostics.error(
        messager,
        rebuild.method(),
        TAG,
        "No 'parameterOrder' is written, and the order the parameter names give, "
            + literal(order)
            + ", leaves out what '"
            + signature
            + "' takes.",
        "Read from the names, the order rebuilds through a constructor of "
            + order.size()
            + " parameters, and '"
            + signature
            + "' takes more, so a value only it takes may come back reset after every set.",
        "Write the order of the constructor the lens should rebuild through as"
            + " @ViaConstructor's 'parameterOrder'; written out, "
            + literal(order)
            + " rebuilds through the shorter one as it is. Otherwise "
            + CopyStrategyChecks.rebuildWith(rebuild.sourceName(), TAG)
            + ".");
    return true;
  }

  /**
   * Reports an order the generated call cannot rebuild the source type with, and returns whether it
   * did.
   *
   * <p>A focus written as a wildcard is inferred from the getter, and an accessor that declares
   * type variables of its own from the parameter it is passed to. Neither gives a type to choose a
   * constructor by, so which one such a call binds is left to javac, as a call that rests on
   * inference is. Where only one constructor could take it, its names hold the order all the same.
   *
   * @param rebuild the lens and what it rebuilds
   * @param order the names the call reads its arguments through, each one an accessor
   * @param derived whether the order was read from the parameter names rather than written, so that
   *     the call has to bind a constructor it was read from
   * @return true when the call cannot rebuild through the order, and an error was reported
   */
  private boolean rebuildsThroughUnfitConstructor(
      Rebuild rebuild, List<String> order, boolean derived) {
    String source = rebuild.sourceName();
    String call = callOf(rebuild, order);
    if (choosesByInference(rebuild, order)) {
      Optional<ExecutableElement> sole = soleConstructorOfLength(rebuild, order);
      return sole.isPresent() && crossesParameterNames(rebuild, sole.get(), order, call);
    }
    List<TypeMirror> arguments = argumentsOf(rebuild, order);
    String taken =
        arguments.stream()
            .map(ProcessorUtils::simpleTypeName)
            .collect(Collectors.joining(", ", "(", ")"));
    return switch (CallBinding.resolve(
        typeUtils, rebuild.source(), arguments, rebuild.constructors())) {
      case CallBinding.Binds(ExecutableElement bound) -> {
        if (!derived || givesOrder(rebuild, bound, order)) {
          yield crossesParameterNames(rebuild, bound, order, call);
        }
        reportOrderBindingAnother(rebuild, bound, order, call);
        yield true;
      }
      case CallBinding.NoneApplies() -> {
        reportOrderNoConstructorTakes(rebuild, order, call, taken);
        yield true;
      }
      case CallBinding.Ambiguous(List<ExecutableElement> tied) -> {
        List<String> signatures =
            tied.stream()
                .map(constructor -> "'" + constructorSignature(constructor) + "'")
                .toList();
        Diagnostics.error(
            messager,
            rebuild.method(),
            TAG,
            "The generated call '"
                + call
                + "' cannot choose between "
                + String.join(", ", signatures.subList(0, signatures.size() - 1))
                + " and "
                + signatures.getLast()
                + ".",
            "Each of them takes "
                + taken
                + ", the arguments the generated lens rebuilds '"
                + source
                + "' with, and none has parameters more specific than every other's.",
            anotherStrategy(source));
        yield true;
      }
      case CallBinding.Undecided(List<ExecutableElement> ignored) -> false;
    };
  }

  /**
   * Reports an order read from the parameter names whose call binds a constructor it was not read
   * from: one that names its parameters after nothing, whose order the names cannot speak for, or
   * one named after other accessors, which takes no parameter for the lens at all.
   */
  private void reportOrderBindingAnother(
      Rebuild rebuild, ExecutableElement bound, List<String> order, String call) {
    String source = rebuild.sourceName();
    String signature = constructorSignature(bound);
    Diagnostics.error(
        messager,
        rebuild.method(),
        TAG,
        "The order read from the parameter names, "
            + literal(order)
            + ", binds '"
            + signature
            + "', not a constructor it was read from.",
        "No 'parameterOrder' is written, so the generated lens rebuilds through '"
            + call
            + "', and javac binds that call to '"
            + signature
            + "' rather than to a constructor whose parameters the order follows.",
        takesNoParameterForTheLens(rebuild, List.of(bound))
            ? ProcessorUtils.capitalise(CopyStrategyChecks.rebuildWith(source, TAG))
                + ", since '"
                + signature
                + "' takes no parameter named after the lens's own '"
                + rebuild.fieldName()
                + "()'."
            : "Write the order '"
                + signature
                + "' takes its arguments in as @ViaConstructor's 'parameterOrder', or "
                + CopyStrategyChecks.rebuildWith(source, TAG)
                + ". Written out, "
                + literal(order)
                + " binds '"
                + signature
                + "' just the same.");
  }

  /**
   * Reports an order no constructor takes, offering the fix that moves it on: the focus the getter
   * reads, where the call would then rebuild through a constructor that follows the order; else an
   * order the names give; else another strategy.
   */
  private void reportOrderNoConstructorTakes(
      Rebuild rebuild, List<String> order, String call, String taken) {
    String source = rebuild.sourceName();
    String fieldName = rebuild.fieldName();
    TypeMirror readAsFocus = readAsFocus(rebuild);
    boolean focusFixes =
        !typeUtils.isSameType(readAsFocus, rebuild.focus())
            && holds(rebuild.withFocus(readAsFocus), order);
    Diagnostics.error(
        messager,
        rebuild.method(),
        TAG,
        "No constructor of '"
            + source
            + "' takes "
            + taken
            + ", the arguments the generated lens rebuilds it with.",
        "The generated lens rebuilds through '" + call + "'. " + constructorsFound(rebuild),
        rebuild.constructors().isEmpty()
            ? anotherStrategy(source)
            : focusFixes
                ? CopyStrategyChecks.declareFocusAs(readAsFocus, fieldName)
                    + "; otherwise "
                    + CopyStrategyChecks.rebuildWith(source, TAG)
                    + "."
                : namedRemedy(rebuild, order, List.of())
                    .orElse(
                        "Name in @ViaConstructor's 'parameterOrder' one accessor of '"
                            + source
                            + "' for each parameter of a constructor found, in the order it"
                            + " takes them, or "
                            + CopyStrategyChecks.rebuildWith(source, TAG)
                            + "."));
  }

  /**
   * Reports an order that passes an argument against the bound constructor's parameter names, and
   * returns whether it did. A parameter named after an accessor that reads a value it takes has to
   * be passed that accessor's value; one named after nothing that reads keeps what the order passes
   * there.
   */
  private boolean crossesParameterNames(
      Rebuild rebuild, ExecutableElement bound, List<String> order, String call) {
    List<List<String>> readers = namedReaders(rebuild, bound);
    OptionalInt misplaced = firstCrossing(readers, order);
    if (misplaced.isEmpty()) {
      return false;
    }
    String fieldName = rebuild.fieldName();
    String source = rebuild.sourceName();
    String signature = constructorSignature(bound);
    List<String> repaired = repaired(readers, fieldName, order);
    if (!repaired.contains(fieldName)) {
      // The repair keeps the lens's value wherever a parameter named after nothing can take it, so
      // here every place the order passes it is a parameter named after another accessor.
      int lens = order.indexOf(fieldName);
      String owner = readers.get(lens).getFirst();
      Diagnostics.error(
          messager,
          rebuild.method(),
          TAG,
          "'"
              + signature
              + "' takes no parameter named after the lens's own '"
              + fieldName
              + "()'.",
          "The generated lens rebuilds through '"
              + call
              + "', passing the new value where '"
              + signature
              + "' takes '"
              + bound.getParameters().get(lens).getSimpleName()
              + "', a parameter its name says takes what '"
              + owner
              + "()' reads, not what the lens reads.",
          // A lens named after what the parameter's own accessor reads is an alias of it, unless
          // that accessor already has its own place in the order.
          (order.contains(owner)
                  ? ""
                  : "If '"
                      + fieldName
                      + "()' reads the same value as '"
                      + owner
                      + "()', name the lens method '"
                      + owner
                      + "' and write that name in the order too. ")
              + namedRemedy(rebuild, order, List.of())
                  .orElse("Otherwise " + CopyStrategyChecks.rebuildWith(source, TAG) + "."));
      return true;
    }
    int at = misplaced.getAsInt();
    String parameter = bound.getParameters().get(at).getSimpleName().toString();
    Diagnostics.error(
        messager,
        rebuild.method(),
        TAG,
        "'parameterOrder' passes "
            + (order.get(at).equals(fieldName)
                ? "the new value"
                : "'source." + order.get(at) + "()'")
            + " where '"
            + signature
            + "' takes '"
            + parameter
            + "'.",
        "The generated lens rebuilds through '"
            + call
            + "', and '"
            + parameter
            + "' is named after '"
            + readers.get(at).getFirst()
            + "()', an accessor of '"
            + source
            + "' that reads a value it takes. Another value passed in its place is written into"
            + " the wrong field, which breaks the lens laws.",
        namedRemedy(rebuild, order, List.of(repaired)).orElse(anotherStrategy(source)));
    return true;
  }

  /**
   * The remedy the constructors' parameter names give for an order: {@code preferred}, then the
   * orders the names give that pass the lens's value, each offered only where the call would
   * rebuild through a constructor that follows it, and none the one written; or, where every
   * constructor is named after other accessors, that no order can set it. Empty where the names say
   * neither, for the caller's own.
   */
  private Optional<String> namedRemedy(
      Rebuild rebuild, List<String> written, List<List<String>> preferred) {
    List<List<String>> offers =
        Stream.concat(preferred.stream(), namedOrders(rebuild, written).stream())
            .filter(Predicate.not(written::equals))
            .filter(named -> holds(rebuild, named))
            .distinct()
            .toList();
    if (!offers.isEmpty()) {
      return Optional.of(
          "Pass the arguments where the constructor's parameter names place them: "
              + offered(offers)
              + ".");
    }
    return takesNoParameterForTheLens(rebuild, rebuild.constructors())
        ? Optional.of(cannotSetFix(rebuild.sourceName(), rebuild.fieldName()))
        : Optional.empty();
  }

  /** What the lens's own getter reads, as a focus declares it. */
  private TypeMirror readAsFocus(Rebuild rebuild) {
    return CopyStrategyChecks.asFocus(
        typeUtils,
        ProcessorUtils.returnTypeIn(
            typeUtils,
            rebuild.source(),
            CopyStrategyChecks.accessorNamed(rebuild.members(), rebuild.fieldName())));
  }

  /** The fix that turns to another strategy: a whole fix line. */
  private static String anotherStrategy(String source) {
    return ProcessorUtils.capitalise(CopyStrategyChecks.rebuildWith(source, TAG)) + ".";
  }

  /**
   * The first place an order passes an argument against a constructor's parameter names: a
   * parameter named after an accessor, passed none of that accessor's spellings. A parameter named
   * after nothing that reads takes whatever the order passes there.
   */
  private static OptionalInt firstCrossing(List<List<String>> readers, List<String> order) {
    return IntStream.range(0, order.size())
        .filter(at -> !readers.get(at).isEmpty() && !readers.get(at).contains(order.get(at)))
        .findFirst();
  }

  /** The fix where no constructor takes the lens's own value: a whole fix line. */
  private static String cannotSetFix(String source, String fieldName) {
    return "@ViaConstructor cannot set '"
        + fieldName
        + "()' on a '"
        + source
        + "', since no constructor takes a parameter named after it: "
        + CopyStrategyChecks.rebuildWith(source, TAG)
        + ".";
  }

  /**
   * Whether an order would rebuild through a constructor that follows it: the call binds one whose
   * parameters named after accessors are each passed that accessor's value. Only such an order is
   * offered to paste.
   */
  private boolean holds(Rebuild rebuild, List<String> order) {
    return boundBy(rebuild, order)
        .filter(constructor -> firstCrossing(namedReaders(rebuild, constructor), order).isEmpty())
        .isPresent();
  }

  /** The constructor the generated call for an order binds, where that can be said. */
  private Optional<ExecutableElement> boundBy(Rebuild rebuild, List<String> order) {
    if (choosesByInference(rebuild, order)) {
      return soleConstructorOfLength(rebuild, order);
    }
    return CallBinding.resolve(
                typeUtils, rebuild.source(), argumentsOf(rebuild, order), rebuild.constructors())
            instanceof CallBinding.Binds(ExecutableElement bound)
        ? Optional.of(bound)
        : Optional.empty();
  }

  /**
   * Whether the call for an order rests on inference: a focus written as a wildcard, unless the
   * call passes it cast to a primitive, or an accessor that declares type variables of its own.
   */
  private static boolean choosesByInference(Rebuild rebuild, List<String> order) {
    return rebuild.focus().getKind() == TypeKind.WILDCARD && rebuild.unboxed(order) == null
        || order.stream()
            .filter(name -> !name.equals(rebuild.fieldName()))
            .anyMatch(
                name ->
                    !CopyStrategyChecks.accessorNamed(rebuild.members(), name)
                        .getTypeParameters()
                        .isEmpty());
  }

  /**
   * The one constructor that takes as many arguments as the order passes, where none takes any
   * number: with no type to choose by, the call binds that one or does not compile.
   */
  private static Optional<ExecutableElement> soleConstructorOfLength(
      Rebuild rebuild, List<String> order) {
    List<ExecutableElement> ofLength =
        rebuild.constructors().stream()
            .filter(constructor -> constructor.getParameters().size() == order.size())
            .toList();
    return rebuild.constructors().stream().noneMatch(ExecutableElement::isVarArgs)
            && ofLength.size() == 1
        ? Optional.of(ofLength.getFirst())
        : Optional.empty();
  }

  /** The types of the arguments the call for an order passes, the focus as it is passed. */
  private List<TypeMirror> argumentsOf(Rebuild rebuild, List<String> order) {
    TypeMirror unboxed = rebuild.unboxed(order);
    TypeMirror passed = unboxed == null ? rebuild.focus() : unboxed;
    return order.stream()
        .map(
            name ->
                name.equals(rebuild.fieldName())
                    ? passed
                    : ProcessorUtils.returnTypeIn(
                        typeUtils,
                        rebuild.source(),
                        CopyStrategyChecks.accessorNamed(rebuild.members(), name)))
        .toList();
  }

  /** The call the generated lens writes for an order, for a diagnostic: {@code new S(...)}. */
  private static String callOf(Rebuild rebuild, List<String> order) {
    TypeMirror unboxed = rebuild.unboxed(order);
    return "new "
        + rebuild.sourceName()
        + order.stream()
            .map(
                name ->
                    name.equals(rebuild.fieldName())
                        ? (unboxed == null
                                ? ""
                                : "(" + ProcessorUtils.simpleTypeName(unboxed) + ") ")
                            + "newValue"
                        : "source." + name + "()")
            .collect(Collectors.joining(", ", "(", ")"));
  }

  /**
   * Whether a constructor gives {@code order} by its parameter names, as {@link #derivedOrder}
   * reads them: each parameter named after an accessor that reads a value it takes.
   */
  private boolean givesOrder(Rebuild rebuild, ExecutableElement constructor, List<String> order) {
    List<List<String>> readers = namedReaders(rebuild, constructor);
    return complete(readers) && spelt(readers, rebuild.fieldName(), order).equals(order);
  }

  /**
   * Whether each of the constructors names every parameter after an accessor, none of them the
   * lens's own: no order then passes the lens's value anywhere their names allow.
   */
  private boolean takesNoParameterForTheLens(
      Rebuild rebuild, List<ExecutableElement> constructors) {
    return !constructors.isEmpty()
        && constructors.stream()
            .map(constructor -> namedReaders(rebuild, constructor))
            .allMatch(
                readers ->
                    complete(readers)
                        && readers.stream()
                            .noneMatch(spellings -> spellings.contains(rebuild.fieldName())));
  }

  /**
   * The distinct orders the constructors' parameter names give that pass the lens's own value
   * somewhere, each spelt as {@link #spelt} spells it.
   */
  private List<List<String>> namedOrders(Rebuild rebuild, List<String> style) {
    return rebuild.constructors().stream()
        .map(constructor -> namedReaders(rebuild, constructor))
        .filter(ConstructorOrderChecks::complete)
        .map(readers -> spelt(readers, rebuild.fieldName(), style))
        .filter(named -> named.contains(rebuild.fieldName()))
        .distinct()
        .toList();
  }

  /**
   * For each parameter of a constructor, the spellings of its name that name an accessor the
   * generated class can call and that reads a value the parameter takes, in the order {@link
   * ProcessorUtils#getterSpellings} prefers them. An empty list is a parameter named after nothing
   * that reads. The parameters are read as {@link CallBinding} reads them, so a constructor's own
   * type variable stands as its erasure.
   */
  private List<List<String>> namedReaders(Rebuild rebuild, ExecutableElement constructor) {
    List<TypeMirror> parameters =
        CallBinding.parametersOf(
            typeUtils, (DeclaredType) typeUtils.capture(rebuild.source()), constructor);
    return IntStream.range(0, parameters.size())
        .mapToObj(
            at ->
                ProcessorUtils.getterSpellings(parameterName(constructor, at)).stream()
                    .filter(
                        spelling ->
                            Optional.ofNullable(
                                    CopyStrategyChecks.accessorNamed(rebuild.members(), spelling))
                                .filter(
                                    accessor ->
                                        fitsParameter(
                                            rebuild.source(), accessor, parameters.get(at)))
                                .isPresent())
                    .toList())
        .toList();
  }

  /**
   * Whether what an accessor reads can be passed where {@code parameter} stands with no unchecked
   * conversion, which would be a warning in a file its author cannot edit: a subtype of it, or
   * across a primitive and a reference, a value boxing or unboxing turns into one. An accessor
   * whose read names a type variable of its own takes its type from the parameter it is passed to.
   */
  private boolean fitsParameter(
      DeclaredType source, ExecutableElement accessor, TypeMirror parameter) {
    TypeMirror returned = accessor.getReturnType();
    if (accessor.getTypeParameters().stream()
        .anyMatch(variable -> ProcessorUtils.mentions(returned, variable))) {
      return true;
    }
    TypeMirror read = ProcessorUtils.returnTypeIn(typeUtils, source, accessor);
    return read.getKind().isPrimitive() == parameter.getKind().isPrimitive()
        ? typeUtils.isSubtype(read, parameter)
        : typeUtils.isAssignable(read, parameter);
  }

  /**
   * The name of a constructor's parameter: a record component's, for a record's canonical
   * constructor, since a class file can drop the names of a compact one's parameters while it keeps
   * its components'; otherwise the parameter's own.
   */
  private String parameterName(ExecutableElement constructor, int at) {
    return (isCanonical(constructor)
            ? ((TypeElement) constructor.getEnclosingElement()).getRecordComponents().get(at)
            : constructor.getParameters().get(at))
        .getSimpleName()
        .toString();
  }

  /**
   * Whether a constructor is its record's canonical one: it takes the record's components, their
   * types in their order. A class has no components, and no constructor that takes some.
   */
  private boolean isCanonical(ExecutableElement constructor) {
    List<? extends RecordComponentElement> components =
        ((TypeElement) constructor.getEnclosingElement()).getRecordComponents();
    List<? extends VariableElement> parameters = constructor.getParameters();
    return components.size() == parameters.size()
        && IntStream.range(0, parameters.size())
            .allMatch(
                at ->
                    typeUtils.isSameType(parameters.get(at).asType(), components.get(at).asType()));
  }

  /** Whether every parameter of a constructor is named after an accessor that reads into it. */
  private static boolean complete(List<List<String>> readers) {
    return readers.stream().noneMatch(List::isEmpty);
  }

  /**
   * The order a constructor whose every parameter is named gives, one accessor for each place:
   * {@link #spelling chosen} as an offer spells it, in the style of {@code style}.
   */
  private static List<String> spelt(
      List<List<String>> readers, String fieldName, List<String> style) {
    return readers.stream().map(spellings -> spelling(spellings, fieldName, style)).toList();
  }

  /**
   * A written order put right against a bound constructor's names: each place whose parameter is
   * named after an accessor takes that accessor, and the places named after nothing take what the
   * order passes that those have not taken. A place named after nothing keeps the name the order
   * writes there where it can, and the lens's own name is placed before any other, so that a name
   * no parameter is named after never pushes the lens's value out. So {@code {"y", "x"}} over
   * {@code N(int x, int b)} comes back as {@code {"x", "y"}}. The order has as many names as the
   * constructor has parameters, since the call binds it.
   */
  private static List<String> repaired(
      List<List<String>> readers, String fieldName, List<String> order) {
    List<String> result = new ArrayList<>();
    List<String> left = new ArrayList<>(order);
    for (List<String> spellings : readers) {
      String placed = spellings.isEmpty() ? null : spelling(spellings, fieldName, order);
      result.add(placed);
      left.remove(placed);
    }
    left.sort(Comparator.comparing(name -> !name.equals(fieldName)));
    for (int at = 0; at < result.size(); at++) {
      if (result.get(at) == null) {
        String written = order.get(at);
        result.set(at, left.remove(left.contains(written) ? left.indexOf(written) : 0));
      }
    }
    return List.copyOf(result);
  }

  /**
   * The spelling an offer writes at one place: the lens's own name where it reads there, so that
   * the order passes the lens's value; else one {@code style} already uses, so that an offer keeps
   * the author's style; else the first that reads.
   */
  private static String spelling(List<String> spellings, String fieldName, List<String> style) {
    return spellings.contains(fieldName)
        ? fieldName
        : spellings.stream().filter(style::contains).findFirst().orElse(spellings.getFirst());
  }

  /** Orders as a fix line offers them: each a {@code @ViaConstructor} to paste. */
  private static String offered(List<List<String>> orders) {
    return orders.stream()
        .map(order -> "@ViaConstructor(parameterOrder = " + literal(order) + ")")
        .collect(Collectors.joining(" or "));
  }

  /** An order as an annotation writes it, {@code {"x", "y"}}. */
  private static String literal(List<String> order) {
    return order.stream()
        .map(name -> "\"" + name + "\"")
        .collect(Collectors.joining(", ", "{", "}"));
  }

  /** The constructors the generated class can call, for a diagnostic's reason: a sentence. */
  private static String constructorsFound(Rebuild rebuild) {
    return rebuild.constructors().isEmpty()
        ? "'"
            + rebuild.sourceName()
            + "' declares no constructor the generated class in '"
            + rebuild.targetPackage()
            + "' can call."
        : "Constructors found: "
            + rebuild.constructors().stream()
                .map(ConstructorOrderChecks::constructorSignature)
                .toList()
            + ".";
  }

  /**
   * A constructor as its class declares it, with its parameters' names, {@code Point(int x, int
   * y)}: the names are what an order is held to. Declared rather than instantiated, so that two
   * constructors one instantiation makes alike, {@code Box(T, String)} and {@code Box(String, T)}
   * over a {@code Box<String>}, still read apart.
   */
  private static String constructorSignature(ExecutableElement constructor) {
    List<String> written =
        CopyStrategyChecks.parametersAsWritten(
            constructor.getParameters().stream().map(VariableElement::asType).toList(),
            constructor.isVarArgs());
    return ((TypeElement) constructor.getEnclosingElement()).getSimpleName()
        + IntStream.range(0, written.size())
            .mapToObj(
                at -> written.get(at) + " " + constructor.getParameters().get(at).getSimpleName())
            .collect(Collectors.joining(", ", "(", ")"));
  }
}
