// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyKind;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.NamingConvention;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.ProtobufCollection;
import org.higherkindedj.optics.processing.util.Reachability;
import org.higherkindedj.optics.processing.util.Reachability.Crossing;

/**
 * What a spec's lens method rebuilds its source type through, and whether it can.
 *
 * <p>Each of the four copy strategies names methods as strings: the accessor a lens reads through,
 * and the wither, builder chain, setter or constructor order it writes through. The generated lens
 * calls them as written, so a name the source type does not carry, or one that cannot take the
 * value the lens sets, is a compile error in a file its author never wrote; and a read or call
 * javac can only make unchecked is a warning there that no suppression its author writes reaches.
 * Every one of them is read here instead, against the type the spec names, and refused at the spec
 * method.
 *
 * <p>The rules the refusals rest on are javac's own, and two of them are deliberately not repeated:
 * a call whose binding rests on inference, and a focus written as a wildcard, whose type such a
 * lens draws from its getter as much as from the wildcard. Both are read leniently, so that nothing
 * javac would accept without an unchecked warning is refused here; a wildcard focus's upper bound
 * still holds a read against an unchecked one. {@link CallBinding} answers which method or
 * constructor a generated call binds, and {@link ConstructorOrderChecks} what order a
 * {@code @ViaConstructor} rebuild passes its arguments in.
 *
 * <p>Split from {@link SpecInterfaceAnalyser}, which asks it one question per lens method and keeps
 * the rest of a spec's reading: its source type, its optic kinds, and its prism and traversal
 * hints.
 */
final class CopyStrategyChecks {

  private static final String VIA_BUILDER_FQN = "org.higherkindedj.optics.annotations.ViaBuilder";
  private static final String WITHER_FQN = "org.higherkindedj.optics.annotations.Wither";
  private static final String VIA_CONSTRUCTOR_FQN =
      "org.higherkindedj.optics.annotations.ViaConstructor";
  private static final String VIA_COPY_AND_SET_FQN =
      "org.higherkindedj.optics.annotations.ViaCopyAndSet";

  /**
   * The {@code @SuppressWarnings} id that keeps quiet a {@code @ViaCopyAndSet} setter spelt as a
   * wither whose result the lens drops, where it sets the copy it is called on after all.
   */
  static final String DROPPED_RESULT = "dropped-result";

  private final Types typeUtils;
  private final Elements elementUtils;
  private final Messager messager;
  private final ConstructorOrderChecks constructorOrders;

  /**
   * Creates the checks over a round's utilities.
   *
   * @param typeUtils the round's type utilities; must not be null
   * @param elementUtils the round's element utilities; must not be null
   * @param messager where refusals are reported; must not be null
   */
  CopyStrategyChecks(Types typeUtils, Elements elementUtils, Messager messager) {
    this.typeUtils = typeUtils;
    this.elementUtils = elementUtils;
    this.messager = messager;
    this.constructorOrders = new ConstructorOrderChecks(typeUtils, elementUtils, messager);
  }

  // ----- Copy Strategy Parsing -----

  /**
   * The strategy a lens method declares, and the values its annotation carries.
   *
   * @param kind which strategy the method names
   * @param info the names that strategy rebuilds through
   */
  record CopyStrategyResult(CopyStrategyKind kind, CopyStrategyInfo info) {}

  /**
   * Reads the copy strategy annotation on a lens method.
   *
   * @param method the abstract lens method
   * @param sourceType the source type {@code S}, which annotation values are resolved against
   * @param sourceTypeElement the resolved element for {@code S}
   * @param focusType the focus type {@code A}, which a wither is called with
   * @param targetPackage the package the optics class is generated into
   * @return the strategy and its values, or empty if the method carries no strategy annotation or
   *     one whose values were rejected; either way an error has been reported
   */
  Optional<CopyStrategyResult> parse(
      ExecutableElement method,
      TypeMirror sourceType,
      TypeElement sourceTypeElement,
      TypeMirror focusType,
      String targetPackage) {
    // analyse() admits a source type only when asElement gives a TypeElement, and the processor
    // holds a spec back until its source type resolves, which leaves DECLARED alone. That is what
    // makes the cast total.
    DeclaredType declaredSource = (DeclaredType) sourceType;
    // Every strategy reads through an accessor, and names it by the lens method where the
    // annotation does not.
    String fieldName = method.getSimpleName().toString();

    // Check for @ViaBuilder
    AnnotationMirror viaBuilder = ProcessorUtils.findAnnotation(method, VIA_BUILDER_FQN);
    if (viaBuilder != null) {
      String getter = ProcessorUtils.getAnnotationString(viaBuilder, "getter", "");
      String toBuilder = ProcessorUtils.getAnnotationString(viaBuilder, "toBuilder", "toBuilder");
      String setter = ProcessorUtils.getAnnotationString(viaBuilder, "setter", "");
      String build = ProcessorUtils.getAnnotationString(viaBuilder, "build", "build");
      // As for a wither, the builder's setter is called with the unboxed value where the
      // generator casts it.
      TypeMirror builderWritten =
          builderFocusType(
              method,
              declaredSource,
              sourceTypeElement,
              getter,
              toBuilder,
              setter,
              focusType,
              targetPackage);
      if (readsThroughUnusableGetter(
          method,
          "@ViaBuilder",
          declaredSource,
          sourceTypeElement,
          getter.isEmpty() ? fieldName : getter,
          focusType,
          targetPackage)) {
        return Optional.empty();
      }
      if (!(builderChain(
              method,
              declaredSource,
              sourceTypeElement,
              getter.isEmpty() ? fieldName : getter,
              toBuilder,
              setter.isEmpty() ? fieldName : setter,
              build,
              builderWritten == null ? focusType : builderWritten,
              targetPackage)
          instanceof BuilderChain.Rebuilds(String clear))) {
        return Optional.empty();
      }
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.VIA_BUILDER,
              CopyStrategyInfo.forBuilder(
                  getter, toBuilder, setter, build, clear, builderWritten)));
    }

    // Check for @Wither
    AnnotationMirror wither = ProcessorUtils.findAnnotation(method, WITHER_FQN);
    if (wither != null) {
      String getter = ProcessorUtils.getAnnotationString(wither, "getter", "");
      String witherMethod = ProcessorUtils.getAnnotationString(wither, "value", "");
      // The generated lens passes a primitive unboxed where the name is overloaded, so that is
      // the value the call is resolved with.
      TypeMirror written =
          writtenFocusType(
              method,
              declaredSource,
              sourceTypeElement,
              getter,
              witherMethod,
              focusType,
              targetPackage);
      if (readsThroughUnusableGetter(
              method,
              "@Wither",
              declaredSource,
              sourceTypeElement,
              getter.isEmpty() ? fieldName : getter,
              focusType,
              targetPackage)
          || rebuildsThroughUnusableWither(
              method,
              declaredSource,
              sourceTypeElement,
              getter.isEmpty() ? fieldName : getter,
              written == null ? focusType : written,
              witherMethod,
              targetPackage)) {
        return Optional.empty();
      }
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.WITHER, CopyStrategyInfo.forWither(getter, witherMethod, written)));
    }

    // Check for @ViaConstructor
    AnnotationMirror viaConstructor = ProcessorUtils.findAnnotation(method, VIA_CONSTRUCTOR_FQN);
    if (viaConstructor != null) {
      if (rebuildsThroughUnwritableConstructor(method, declaredSource, "@ViaConstructor")) {
        return Optional.empty();
      }
      String[] written = ProcessorUtils.getAnnotationStringArray(viaConstructor, "parameterOrder");
      ConstructorOrderChecks.Rebuild rebuild =
          new ConstructorOrderChecks.Rebuild(
              method,
              declaredSource,
              focusType,
              callableMembers(sourceTypeElement, targetPackage),
              callableConstructors(sourceTypeElement, targetPackage),
              targetPackage,
              (order, focus) ->
                  constructorFocusType(
                      method, declaredSource, sourceTypeElement, order, focus, targetPackage));
      if (readsThroughUnusableGetter(
              method,
              "@ViaConstructor",
              declaredSource,
              sourceTypeElement,
              fieldName,
              focusType,
              targetPackage)
          || rebuildsThroughUnreadableParameter(rebuild, written)) {
        return Optional.empty();
      }
      // A source type the generated class cannot name is refused once, after the analysis, by the
      // reachability check every companion makes, and none of its constructors could be called.
      if (Reachability.firstHidden(
              elementUtils, targetPackage, Stream.of(Crossing.over("source type", declaredSource)))
          .isPresent()) {
        return Optional.of(
            new CopyStrategyResult(
                CopyStrategyKind.VIA_CONSTRUCTOR, CopyStrategyInfo.forConstructor(written, null)));
      }
      return constructorOrders
          .order(rebuild, List.of(written))
          .map(
              order ->
                  new CopyStrategyResult(
                      CopyStrategyKind.VIA_CONSTRUCTOR,
                      CopyStrategyInfo.forConstructor(
                          order.toArray(String[]::new), rebuild.unboxed(order))));
    }

    // Check for @ViaCopyAndSet
    AnnotationMirror viaCopyAndSet = ProcessorUtils.findAnnotation(method, VIA_COPY_AND_SET_FQN);
    if (viaCopyAndSet != null) {
      if (rebuildsThroughUnwritableConstructor(method, declaredSource, "@ViaCopyAndSet")) {
        return Optional.empty();
      }
      String copyConstructor =
          ProcessorUtils.getAnnotationString(viaCopyAndSet, "copyConstructor", "");
      String setter = ProcessorUtils.getAnnotationString(viaCopyAndSet, "setter", "");
      // The generated lens passes a primitive unboxed where the name is overloaded, so that is
      // the value the call is resolved with.
      TypeMirror written =
          writtenFocusType(
              method, declaredSource, sourceTypeElement, "", setter, focusType, targetPackage);
      if (readsThroughUnusableGetter(
          method,
          "@ViaCopyAndSet",
          declaredSource,
          sourceTypeElement,
          fieldName,
          focusType,
          targetPackage)) {
        return Optional.empty();
      }
      TypeMirror argument = written == null ? focusType : written;
      SetterCall setterCall =
          copySetterCall(
              method,
              declaredSource,
              sourceTypeElement,
              fieldName,
              setter,
              argument,
              targetPackage);
      if (setterCall instanceof SetterCall.Refused) {
        return Optional.empty();
      }
      // A setter spelt as a wither is taken at its word: what it returns is the value set, a new
      // one or, from a fluent wither, the copy. Any other setter is called for its effect, since a
      // set method that returns the source type may hand back the value it replaced or a snapshot.
      boolean witherSpelt = NamingConvention.WITHER.spells(setter);
      List<ExecutableElement> reachable =
          witherSpelt
              ? settersOneArgumentReaches(sourceTypeElement, setter, argument, targetPackage)
              : List.of();
      boolean returnsSource =
          witherSpelt
              && reachable.stream()
                  .allMatch(member -> returnsSourceForAnyArgument(declaredSource, member));
      Optional<CopyStrategyResult> result =
          copyConstructor.isEmpty()
              ? Optional.of(
                  new CopyStrategyResult(
                      CopyStrategyKind.VIA_COPY_AND_SET,
                      CopyStrategyInfo.forCopyAndSet(null, setter, written, returnsSource)))
              : resolveCopyConstructorParameterType(
                      method, declaredSource, targetPackage, copyConstructor)
                  .map(
                      parameterType ->
                          new CopyStrategyResult(
                              CopyStrategyKind.VIA_COPY_AND_SET,
                              // Naming S itself is honoured by casting to nothing: a cast to the
                              // argument's own type says nothing, and javac reports it as
                              // redundant. Answering it here, with Types, leaves the generator one
                              // rule - a null parameter type means no cast - rather than a
                              // comparison of rendered names.
                              CopyStrategyInfo.forCopyAndSet(
                                  typeUtils.isSameType(parameterType, sourceType)
                                      ? null
                                      : parameterType,
                                  setter,
                                  written,
                                  returnsSource)));
      if (result.isPresent() && witherSpelt && !returnsSource) {
        warnIfDropsWitherResult(method, declaredSource, setter, reachable, argument, setterCall);
      }
      return result;
    }

    Diagnostics.error(
        messager,
        method,
        "@ImportOptics",
        "Lens method '" + method.getSimpleName() + "' carries no copy strategy annotation.",
        "A lens has to rebuild '"
            + ProcessorUtils.qualifiedTypeName(sourceType)
            + "' to set through it, and only the strategy says how that type is copied.",
        "Add @ViaBuilder, @Wither, @ViaConstructor, or @ViaCopyAndSet to the method.");
    return Optional.empty();
  }

  /**
   * The methods of a type the generated class can call, in the order {@code getAllMembers} reads
   * them.
   */
  private List<ExecutableElement> callableMembers(TypeElement type, String targetPackage) {
    return ElementFilter.methodsIn(elementUtils.getAllMembers(type)).stream()
        .filter(member -> callableOn(member, targetPackage))
        .toList();
  }

  /** The names of a type's instance methods the generated class can call with no argument. */
  private List<String> accessorNames(TypeElement type, String targetPackage) {
    return namesOf(
        callableMembers(type, targetPackage).stream()
            .filter(member -> member.getParameters().isEmpty() && offerable(member, type))
            .toList());
  }

  /**
   * The names a strategy's setter is offered from: the instance methods of the type the call is
   * made on that the generated class can call with one argument, but under {@code @ViaBuilder} none
   * that hands nothing back, which ends the chain.
   */
  private List<String> setterNames(String tag, Receiver on, String targetPackage) {
    return namesOf(
        callableMembers(on.element(), targetPackage).stream()
            .filter(
                member ->
                    member.getParameters().size() == 1
                        && offerable(member, on.element())
                        && takesTheValue(on, member)
                        && !(tag.equals("@ViaBuilder")
                            && member.getReturnType().getKind() == TypeKind.VOID))
            .toList());
  }

  /**
   * Whether a diagnostic may offer {@code member}: an instance method, and none of {@code
   * Object}'s, {@code wait} or a record's {@code hashCode}, which is never what a spec meant. Nor
   * one deprecated, by its annotation or by a javadoc tag a class file keeps, whose call the
   * generated file would warn of, as a build under {@code -Werror} refuses: a protobuf map field's
   * {@code getPoints()}, beside {@code getPointsMap()}.
   */
  private boolean offerable(ExecutableElement member, TypeElement type) {
    return !member.getModifiers().contains(Modifier.STATIC)
        && !elementUtils.isDeprecated(member)
        && ElementFilter.methodsIn(
                elementUtils.getTypeElement("java.lang.Object").getEnclosedElements())
            .stream()
            .noneMatch(
                method -> method.equals(member) || elementUtils.overrides(member, method, type));
  }

  /**
   * Whether a one-parameter method can take a value the lens holds when called as {@code on} says:
   * not where its parameter is the type a wildcard the call reaches it through stands for, which
   * takes no value at all, as {@code withValue(T)} on a {@code Box<?>}.
   */
  private boolean takesTheValue(Receiver on, ExecutableElement member) {
    DeclaredType captured = (DeclaredType) typeUtils.capture(on.type());
    TypeMirror parameter =
        ProcessorUtils.memberOf(typeUtils, captured, member).getParameterTypes().getFirst();
    return standInNamed(on, captured, List.of(parameter)).isEmpty();
  }

  /**
   * The types capture made, in {@code captured}, of the wildcards {@code written} writes, each a
   * type of its own that no type a spec writes names. {@code written} is the type captured, or the
   * same class as a declaration writes it; an inner class reaches the wildcards of the type it is a
   * member of too.
   */
  private List<Element> standIns(DeclaredType written, DeclaredType captured) {
    List<? extends TypeMirror> arguments = written.getTypeArguments();
    // A type reached raw, as through a raw supertype, has no arguments to stand in for any.
    List<? extends TypeMirror> capturedArguments = captured.getTypeArguments();
    Stream<Element> here =
        IntStream.range(0, capturedArguments.size() == arguments.size() ? arguments.size() : 0)
            .filter(at -> arguments.get(at).getKind() == TypeKind.WILDCARD)
            .mapToObj(at -> typeUtils.asElement(capturedArguments.get(at)));
    TypeMirror enclosing = written.getEnclosingType();
    return Stream.concat(
            here,
            enclosing.getKind() == TypeKind.DECLARED
                ? standIns((DeclaredType) enclosing, (DeclaredType) captured.getEnclosingType())
                    .stream()
                : Stream.empty())
        .toList();
  }

  /** Whether any of {@code types} names one of {@code standIns}. */
  private static boolean mentionsAny(List<TypeMirror> types, List<Element> standIns) {
    return types.stream()
        .anyMatch(
            type -> standIns.stream().anyMatch(standIn -> ProcessorUtils.mentions(type, standIn)));
  }

  /**
   * Whose wildcard a parameter names the type of, where it does, as capture made it: the spec's
   * source type's, or the builder's own, as {@code toBuilder()} declares it hands the builder back.
   */
  private enum StandIn {
    SOURCE,
    BUILDER
  }

  /**
   * Whose wildcard {@code parameters} name the type of, if any: a call that passes them a value
   * never compiles, nor converts to them but unchecked, since no type a spec writes names one. A
   * parameter that is itself the type of a wildcard bounded below, {@code ? super Integer}, takes
   * that bound's values, and is not counted. On a builder, a wildcard is the builder's own where
   * {@code toBuilder()} writes it so, and the source type's wherever else the builder reaches it.
   *
   * @param on the type the call is made on
   * @param captured {@code on}'s type captured
   * @param parameters the parameters, read on {@code captured}
   */
  private Optional<StandIn> standInNamed(
      Receiver on, DeclaredType captured, List<TypeMirror> parameters) {
    List<TypeMirror> taking =
        parameters.stream().filter(parameter -> !boundedBelow(parameter)).toList();
    List<Element> owners = standIns(on.type(), captured);
    return switch (on) {
      case Receiver.OnSource ignored ->
          mentionsAny(taking, owners) ? Optional.of(StandIn.SOURCE) : Optional.empty();
      case Receiver.OnBuilder builder -> {
        // Only a wildcard capture made of the builder as reached counts, where toBuilder() writes
        // it: a type argument the source fills in is the source's to name.
        List<Element> buildersOwn =
            builder.declared() == null
                ? List.of()
                : standIns(builder.declared(), captured).stream().filter(owners::contains).toList();
        List<Element> sources =
            Stream.concat(
                    builder.sourceStandIns().stream(),
                    owners.stream().filter(standIn -> !buildersOwn.contains(standIn)))
                .toList();
        yield mentionsAny(taking, sources)
            ? Optional.of(StandIn.SOURCE)
            : mentionsAny(taking, buildersOwn) ? Optional.of(StandIn.BUILDER) : Optional.empty();
      }
    };
  }

  /** Whether a type is the type capture made of a wildcard bounded below, {@code ? super X}. */
  private static boolean boundedBelow(TypeMirror type) {
    return type.getKind() == TypeKind.TYPEVAR
        && ((TypeVariable) type).getLowerBound().getKind() != TypeKind.NULL;
  }

  /**
   * The type a generated one-argument call is made on, as the call reaches it and as a message
   * names it: the source type itself, or the builder {@code toBuilder()} hands back.
   */
  private sealed interface Receiver {

    /**
     * The type the call is made on, as the call reaches it.
     *
     * @return that type
     */
    DeclaredType type();

    /**
     * The class of {@link #type()}, whose methods the call chooses among.
     *
     * @return that class
     */
    TypeElement element();

    /**
     * The type as a message names it.
     *
     * @return that type
     */
    DeclaredType shown();

    /**
     * The type as a message names it where the call's parameter names the type a wildcard of the
     * source stands for: as it is declared, in its own type variables or the source's, which name
     * what the wildcard stands in for.
     *
     * @return that type
     */
    DeclaredType named();

    /**
     * The source type, as the spec writes it.
     *
     * @param type the source type
     * @param element its class
     */
    record OnSource(DeclaredType type, TypeElement element) implements Receiver {

      /**
       * The source type as the receiver of a call.
       *
       * @param type the source type, whose element is its class
       * @return the receiver
       */
      static OnSource of(DeclaredType type) {
        return new OnSource(type, (TypeElement) type.asElement());
      }

      @Override
      public DeclaredType shown() {
        return type;
      }

      @Override
      public DeclaredType named() {
        return (DeclaredType) element.asType();
      }
    }

    /**
     * The builder {@code toBuilder()} hands back, called on the source captured, so that each
     * wildcard of the source type the spec writes is a type of its own wherever the builder names
     * it.
     *
     * @param type the builder as the call reaches it
     * @param element its class
     * @param handedBackBy the call that hands it back, as a message names it
     * @param shown the builder as a message names it
     * @param declared the builder as {@code toBuilder()} declares it hands it back, in the source's
     *     own type variables, or null where that declaration names no type of its class
     * @param sourceStandIns the types capture made of the source type's wildcards
     */
    record OnBuilder(
        DeclaredType type,
        TypeElement element,
        String handedBackBy,
        DeclaredType shown,
        DeclaredType declared,
        List<Element> sourceStandIns)
        implements Receiver {

      public OnBuilder {
        sourceStandIns = List.copyOf(sourceStandIns);
      }

      @Override
      public DeclaredType named() {
        return Objects.requireNonNullElse(declared, shown);
      }
    }
  }

  /**
   * The convention a strategy's setter is offered in: a builder carries on with what its setter
   * hands back, where a copy's setter is meant to set the copy it is called on.
   */
  private static NamingConvention setterConvention(String tag) {
    return tag.equals("@ViaBuilder") ? NamingConvention.BUILDER_SETTER : NamingConvention.SETTER;
  }

  /**
   * The setter offered for a name the type declares: where none of its methods, {@code named},
   * takes one argument, the name is usually an accessor's, written where the setter for the same
   * value was meant. Only that value's setter is offered, since a name the type declares is not
   * misspelt.
   */
  private String setterOffer(
      String tag,
      List<ExecutableElement> named,
      String setterName,
      Receiver on,
      String targetPackage) {
    List<String> names = setterNames(tag, on, targetPackage);
    Optional<String> adder = collectionAdder(on, setterName, names);
    return didYouMean(
        takesNoSingleArgument(named)
            ? adder.or(() -> setterConvention(tag).sameValueAmong(setterName, names))
            : adder);
  }

  /**
   * The method a protobuf-java message's builder writes a repeated or a map field whole with, for a
   * setter spelt after the field another way, bare, as a setter or as the adder of one element:
   * {@code addAllTags} for {@code tags}, {@code setTags} or {@code addTags}, {@code putAllPoints}
   * for {@code putPoints}, among {@code names}, the setters a diagnostic may offer. Empty for any
   * other builder, which spells its methods its own way.
   */
  private Optional<String> collectionAdder(Receiver on, String written, List<String> names) {
    if (!ProcessorUtils.isSubtypeOfNamed(
        typeUtils, elementUtils, on.type(), ProtobufCollection.MESSAGE_BUILDER)) {
      return Optional.empty();
    }
    String field =
        ProcessorUtils.capitalise(
            written.replaceFirst("^(addAll|putAll|set|add|put|with)(?=\\p{Lu})", ""));
    return Stream.of(ProtobufCollection.values())
        .map(kind -> kind.adderPrefix() + field)
        .filter(adder -> !adder.equals(written) && names.contains(adder))
        .findFirst();
  }

  /** The wither offered for a name the type declares, as {@link #setterOffer} offers a setter. */
  private String witherOffer(
      List<ExecutableElement> named,
      String witherName,
      DeclaredType sourceType,
      List<ExecutableElement> callable) {
    return takesNoSingleArgument(named)
        ? didYouMean(
            NamingConvention.WITHER.sameValueAmong(
                witherName, witherNames(sourceType, withersAmong(sourceType, callable))))
        : "";
  }

  /** The names of {@code withers} a diagnostic may offer: those that can take a value at all. */
  private List<String> witherNames(DeclaredType sourceType, List<ExecutableElement> withers) {
    return namesOf(
        withers.stream()
            .filter(wither -> offerable(wither, (TypeElement) sourceType.asElement()))
            .filter(wither -> takesTheValue(Receiver.OnSource.of(sourceType), wither))
            .toList());
  }

  /** Whether none of {@code methods} takes one argument. */
  private static boolean takesNoSingleArgument(List<ExecutableElement> methods) {
    return methods.stream().noneMatch(member -> member.getParameters().size() == 1);
  }

  /**
   * The source type's withers among {@code callable}: one-parameter instance methods that hand it
   * back.
   */
  private List<ExecutableElement> withersAmong(
      DeclaredType sourceType, List<ExecutableElement> callable) {
    return callable.stream()
        .filter(
            member ->
                member.getParameters().size() == 1
                    && !member.getModifiers().contains(Modifier.STATIC)
                    && ProcessorUtils.returnsOwner(typeUtils, sourceType, member))
        .toList();
  }

  /** The names of {@code methods}, once each, sorted. */
  private static List<String> namesOf(List<ExecutableElement> methods) {
    return methods.stream()
        .map(member -> member.getSimpleName().toString())
        .distinct()
        .sorted()
        .toList();
  }

  /** A diagnostic's offer of the name likely meant, as a sentence that follows the reason. */
  private static String didYouMean(Optional<String> near) {
    return near.map(name -> " Did you mean '" + name + "'?").orElse("");
  }

  /** The constructors of a type the generated class can call. */
  private List<ExecutableElement> callableConstructors(TypeElement type, String targetPackage) {
    return ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
        .filter(constructor -> callableOn(constructor, targetPackage))
        .toList();
  }

  /**
   * The zero-parameter instance method of that name, which an accessor call needs, or null. Where
   * the type inherits one concrete method of the name beside an interface's declaration of it, as a
   * class's {@code String name()} beside a {@code CharSequence name()} it implements, the concrete
   * one is what the call runs, and what it reads is what the call passes on.
   */
  static ExecutableElement accessorNamed(List<ExecutableElement> members, String name) {
    return members.stream()
        .filter(
            member ->
                member.getSimpleName().contentEquals(name)
                    && member.getParameters().isEmpty()
                    && !member.getModifiers().contains(Modifier.STATIC))
        .min(
            Comparator.comparing(
                member -> member.getModifiers().contains(Modifier.ABSTRACT) || member.isDefault()))
        .orElse(null);
  }

  /** The element of a type a generated call is made on, or null where the type has none. */
  private TypeElement elementOf(TypeMirror type) {
    return typeUtils.asElement(type) instanceof TypeElement element ? element : null;
  }

  /**
   * The type a further call to {@code name} in a chain is read on: the step's own type, or for a
   * type variable the bound it carries, followed through a variable bounded by another, as javac
   * infers a step declared with a variable of its own where nothing else pins it down and reads
   * members of a captured wildcard. Where the bound names several types, the one whose methods of
   * that name take in every other's, as a class bound's do where it implements an interface bound;
   * javac weighs the methods of every type the bound names, so where none takes in the others', or
   * none has such a method, this answers null, for the caller to report or to leave to javac.
   */
  private TypeMirror stepTypeFor(TypeMirror handedBack, String name, String targetPackage) {
    List<TypeMirror> bounds = ProcessorUtils.typesReached(handedBack);
    if (bounds.size() == 1) {
      return bounds.getFirst();
    }
    List<TypeMirror> declaring =
        bounds.stream()
            .filter(bound -> !methodsNamed(bound, name, targetPackage).isEmpty())
            .toList();
    return declaring.stream()
        .filter(
            bound ->
                declaring.stream().allMatch(other -> takesIn(bound, other, name, targetPackage)))
        .findFirst()
        .orElse(null);
  }

  /**
   * The methods named {@code name} of the class a bound names that the generated class can call.
   */
  private List<ExecutableElement> methodsNamed(
      TypeMirror bound, String name, String targetPackage) {
    return ElementFilter.typesIn(Stream.ofNullable(typeUtils.asElement(bound)).toList()).stream()
        .flatMap(element -> callableMembers(element, targetPackage).stream())
        .filter(member -> member.getSimpleName().contentEquals(name))
        .toList();
  }

  /**
   * Whether a bound's methods named {@code name} take in another's: each of the other's is one of
   * them, inherited, or one of them overrides it, or overrides it as javac reads a type bounded by
   * both, which {@link #overridesInTheIntersection} says.
   */
  private boolean takesIn(TypeMirror bound, TypeMirror other, String name, String targetPackage) {
    List<ExecutableElement> own = methodsNamed(bound, name, targetPackage);
    TypeElement element = elementOf(bound);
    return methodsNamed(other, name, targetPackage).stream()
        .allMatch(
            theirs ->
                own.stream()
                    .anyMatch(
                        mine ->
                            mine.equals(theirs)
                                || elementUtils.overrides(mine, theirs, element)
                                || overridesInTheIntersection(bound, mine, other, theirs)));
  }

  /**
   * Whether a method of a class bound overrides another bound's method as javac reads a type
   * bounded by both: as a class that extends the one and implements the others, where a method that
   * is not abstract overrides an interface's whose signature its own subsigns (JLS 8.4.8.1), though
   * the class bound itself does not implement the interface. Two abstract methods are both weighed
   * by javac, and neither overrides the other.
   */
  private boolean overridesInTheIntersection(
      TypeMirror bound, ExecutableElement mine, TypeMirror other, ExecutableElement theirs) {
    return elementOf(bound).getKind() == ElementKind.CLASS
        && !mine.getModifiers().contains(Modifier.ABSTRACT)
        && typeUtils.isSubsignature(
            ProcessorUtils.memberOf(typeUtils, (DeclaredType) bound, mine),
            ProcessorUtils.memberOf(typeUtils, (DeclaredType) other, theirs));
  }

  /**
   * The type of {@code element}'s class among those {@code handedBack} names, as {@link
   * ProcessorUtils#typesReached} reads them, or null where it names none: a builder step as a
   * declaration writes it, which a message names and reads the step's methods on.
   */
  private DeclaredType declaredAs(TypeMirror handedBack, TypeElement element) {
    return ProcessorUtils.typesReached(handedBack).stream()
        .filter(
            type ->
                type.getKind() == TypeKind.DECLARED && element.equals(typeUtils.asElement(type)))
        .map(DeclaredType.class::cast)
        .findFirst()
        .orElse(null);
  }

  /**
   * A step reached through a wildcard, as a message names it where no declaration names its class:
   * the type itself, or its class alone where it names a type variable, as the types capture makes
   * of a wildcard do, none of which can be written.
   */
  private TypeMirror shownAlone(TypeMirror step) {
    return ProcessorUtils.namesTypeVariable(step) ? typeUtils.erasure(step) : step;
  }

  /**
   * Reports a call that none of the types a step's bound names declares, and returns whether it
   * did. javac looks the call up among the methods of every one of them, so the message names them
   * all, and offers a near miss from any.
   *
   * @param method the annotated lens method, for error reporting
   * @param handedBack what the step before hands back
   * @param step that step, as a message names it
   * @param name the method the generated lens calls on it
   * @param attribute the attribute that names the method, {@code setter} or {@code build}
   * @param targetPackage the package the optics class is generated into
   * @return true when no bound declares the method, and an error was reported
   */
  private boolean reportsUndeclaredOnEveryBound(
      ExecutableElement method,
      TypeMirror handedBack,
      String step,
      String name,
      String attribute,
      String targetPackage) {
    List<TypeMirror> bounds = ProcessorUtils.typesReached(handedBack);
    // A bound read from a class file can extend a type missing from the classpath, whose methods
    // no round brings; the chain is then written out as it stands, for javac to report.
    if (bounds.size() == 1
        || bounds.stream().anyMatch(bound -> !methodsNamed(bound, name, targetPackage).isEmpty())
        || bounds.stream().anyMatch(this::hierarchyUnresolved)) {
      return false;
    }
    boolean setter = attribute.equals("setter");
    List<String> names =
        ElementFilter.typesIn(bounds.stream().map(typeUtils::asElement).toList()).stream()
            .flatMap(
                element ->
                    setter
                        ? callableMembers(element, targetPackage).stream()
                            .filter(
                                member ->
                                    member.getParameters().size() == 1
                                        && offerable(member, element)
                                        && member.getReturnType().getKind() != TypeKind.VOID)
                            .map(member -> member.getSimpleName().toString())
                        : accessorNames(element, targetPackage).stream())
            .distinct()
            .sorted()
            .toList();
    List<String> boundNames =
        bounds.stream()
            .map(bound -> "'" + ProcessorUtils.simpleTypeName(shownAlone(bound)) + "'")
            .toList();
    String call = setter ? name + "(newValue)" : name + "()";
    Diagnostics.error(
        messager,
        method,
        "@ViaBuilder",
        "'"
            + step
            + "' hands back a type bounded by "
            + listed(boundNames)
            + ", which have no method '"
            + (setter ? name : name + "()")
            + "' the generated class in '"
            + targetPackage
            + "' can call to "
            + (setter ? "set through" : "finish the value it rebuilds")
            + ".",
        "The generated lens calls '"
            + call
            + "' on it, and javac looks the call up among the methods of every type its bound"
            + " names."
            + didYouMean(
                (setter ? NamingConvention.BUILDER_SETTER : NamingConvention.ACCESSOR)
                    .nearest(name, names)),
        "Set @ViaBuilder's '"
            + attribute
            + "' to a method one of them declares that the generated class can call.");
    return true;
  }

  /**
   * Whether a clear method hands back the builder the setter is bound on, so that the setter called
   * after it binds alike. javac reads the call among the methods of every type the builder's bound
   * names, so the clear method it binds has to be the setter's own bound's, no other bound
   * declaring one it does not take in; and what it returns, followed as the chain follows a step,
   * is to be that very builder, as a self-typed {@code B} is.
   *
   * @param clear the clear method, one of the builder's
   * @param builderReached what {@code toBuilder()} hands back, on the source captured
   * @param builderType the builder the setter is bound on
   * @param setterName the setter's name
   * @param targetPackage the package the optics class is generated into
   */
  private boolean handsBackTheBuilder(
      ExecutableElement clear,
      TypeMirror builderReached,
      DeclaredType builderType,
      String setterName,
      String targetPackage) {
    TypeMirror readOn =
        stepTypeFor(builderReached, clear.getSimpleName().toString(), targetPackage);
    if (readOn == null || !builderType.asElement().equals(typeUtils.asElement(readOn))) {
      return false;
    }
    DeclaredType called = (DeclaredType) typeUtils.capture(builderType);
    return Optional.ofNullable(
            stepTypeFor(
                ProcessorUtils.returnTypeIn(typeUtils, called, clear), setterName, targetPackage))
        .map(back -> typeUtils.isSameType(back, called))
        .orElse(false);
  }

  /**
   * The properties a source type reads through accessors other than {@code getterName}, each
   * capitalised as a clear method spells it: {@code AllTags} for {@code getAllTags()} or {@code
   * allTags()}. The lens's own property is never among them, however many accessors read it.
   */
  private Set<String> propertiesReadBesides(
      TypeElement sourceTypeElement, String getterName, String targetPackage) {
    String own = propertySpelt(getterName);
    return accessorNames(sourceTypeElement, targetPackage).stream()
        .map(CopyStrategyChecks::propertySpelt)
        .filter(property -> !property.equals(own))
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * The property an accessor reads, capitalised: what follows {@code get} or {@code is}, as a
   * bean's getter or its beanspec spelling {@code geteMails} spells it, or the whole name of a
   * record's.
   */
  private static String propertySpelt(String accessor) {
    return ProcessorUtils.capitalise(
        accessor.replaceFirst("^(get|is)(?=\\p{Lu}|\\p{Ll}\\p{Lu})", ""));
  }

  /**
   * The type a lens hands its focus back as, for checking what an accessor reads: the focus itself,
   * or null for a focus written as a wildcard, whose type javac infers from the accessor rather
   * than the other way about.
   */
  private TypeMirror focusRead(TypeMirror focusType) {
    return unlessWildcard(focusType);
  }

  /**
   * A type, or null where it is a wildcard, which names no one type: a focus javac infers, or a
   * builder step a message cannot name as declared.
   */
  private static TypeMirror unlessWildcard(TypeMirror type) {
    return type.getKind() == TypeKind.WILDCARD ? null : type;
  }

  /**
   * Reports an accessor a generated optic calls and the type does not have.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param owner the type the call is made on
   * @param ownerElement that type's element, whose members are searched
   * @param name the method the annotation names
   * @param purpose what the generated optic calls it for, as a verb phrase
   * @param on what the call is made on, as the reason names it
   * @param fix the remedy, since only some of the strategies carry an attribute to point at
   * @param offered the method to offer before any the name is a near miss of, or empty
   * @param targetPackage the package the optics class is generated into
   */
  private void reportMissingAccessor(
      ExecutableElement method,
      String tag,
      TypeMirror owner,
      TypeElement ownerElement,
      String name,
      String purpose,
      String on,
      String fix,
      Optional<String> offered,
      String targetPackage) {
    String ownerName = ProcessorUtils.simpleTypeName(owner);
    Diagnostics.error(
        messager,
        method,
        tag,
        "'"
            + ownerName
            + "' has no method '"
            + name
            + "()' for the generated lens to "
            + purpose
            + ".",
        "The generated lens calls '"
            + name
            + "()' on "
            + on
            + ", so it needs a zero-parameter instance method of that name that the generated"
            + " class in '"
            + targetPackage
            + "' can call."
            + didYouMean(
                offered.or(
                    () ->
                        NamingConvention.ACCESSOR.nearest(
                            name, accessorNames(ownerElement, targetPackage)))),
        fix);
  }

  /**
   * The getter a protobuf-java message reads a repeated or a map field whole with, for an accessor
   * spelt after the field, bare or as a bean's getter, or a near miss of one: {@code getTagsList()}
   * for {@code tags} or {@code getTags}, {@code getPointsMap()} for {@code getPoint}. It is offered
   * before any other near miss, since a repeated field's {@code getTags(int)} reads one element and
   * a map field's {@code getPoints()} is deprecated. Empty for any other type, or where no field is
   * near.
   */
  private Optional<String> collectionGetter(
      DeclaredType source, TypeElement element, String written, String targetPackage) {
    if (!ProcessorUtils.isSubtypeOfNamed(
        typeUtils, elementUtils, source, ProtobufCollection.MESSAGE)) {
      return Optional.empty();
    }
    // Each such getter by the field it reads, as protobuf spells it after 'get'.
    Map<String, String> byField = new LinkedHashMap<>();
    for (String accessor : accessorNames(element, targetPackage)) {
      for (ProtobufCollection kind : ProtobufCollection.values()) {
        if (accessor.startsWith("get") && accessor.endsWith(kind.getterSuffix())) {
          byField.put(
              accessor.substring("get".length(), accessor.length() - kind.getterSuffix().length()),
              accessor);
        }
      }
    }
    String field = ProcessorUtils.capitalise(written.replaceFirst("^(get|is)(?=\\p{Lu})", ""));
    return Optional.ofNullable(byField.get(field))
        .or(
            () ->
                ProcessorUtils.nearestName(field, List.copyOf(byField.keySet())).map(byField::get));
  }

  /**
   * Reports a getter the generated lens cannot read its focus through, and returns whether it did.
   *
   * <p>Every strategy reads with the same lambda, {@code source -> source.getX()}, so the name has
   * to be a zero-parameter instance method the generated class can call, and the value it reads has
   * to be one the lens can hand back as its focus without an unchecked conversion. A focus written
   * as a wildcard is inferred from this very accessor, so only its upper bound holds the read, and
   * only against an unchecked one.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param getterName the accessor the strategy reads through
   * @param focusType the lens's focus
   * @param targetPackage the package the optics class is generated into
   * @return true when the lens cannot read through it, and an error was reported
   */
  private boolean readsThroughUnusableGetter(
      ExecutableElement method,
      String tag,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String getterName,
      TypeMirror focusType,
      String targetPackage) {
    ExecutableElement getter =
        accessorNamed(callableMembers(sourceTypeElement, targetPackage), getterName);
    String ownerName = ProcessorUtils.simpleTypeName(sourceType);
    // @ViaCopyAndSet and @ViaConstructor read through the lens method's own name, and carry no
    // attribute that could point anywhere else.
    boolean namesItsGetter = tag.equals("@Wither") || tag.equals("@ViaBuilder");
    if (getter == null) {
      reportMissingAccessor(
          method,
          tag,
          sourceType,
          sourceTypeElement,
          getterName,
          "read the value it focuses",
          "'source'",
          namesItsGetter
              ? "Set " + tag + "'s 'getter' to a method '" + ownerName + "' declares."
              : "Name the lens method after a zero-parameter method '"
                  + ownerName
                  + "' declares, which is the accessor this strategy reads through.",
          collectionGetter(sourceType, sourceTypeElement, getterName, targetPackage),
          targetPackage);
      return true;
    }
    // Read on the captured source, as the call reads it: a value a wildcard types is a type of
    // its own there, and one the lens can hand back.
    TypeMirror read = ProcessorUtils.returnTypeCalledOn(typeUtils, sourceType, getter);
    TypeMirror focus = focusRead(focusType);
    if (focus == null) {
      // A wildcard focus is inferred from this accessor, within its upper bound: a read that
      // reaches the bound only unchecked leaves whatever javac infers unchecked too.
      TypeMirror bound = ((WildcardType) focusType).getExtendsBound();
      return bound != null
          && readsUnchecked(
              method, tag, sourceType, getter, getterName, read, focusType, bound, namesItsGetter);
    }
    if (typeUtils.isAssignable(read, focus)) {
      return readsUnchecked(
          method, tag, sourceType, getter, getterName, read, focusType, focus, namesItsGetter);
    }
    // Named as the spec's own instantiation writes it, which is what the author reads, and in
    // full where a simple name would name both types.
    TypeMirror declaredRead = ProcessorUtils.returnTypeIn(typeUtils, sourceType, getter);
    boolean sameName =
        ProcessorUtils.simpleTypeName(declaredRead).equals(ProcessorUtils.simpleTypeName(focus));
    String focusName =
        sameName ? ProcessorUtils.qualifiedTypeName(focus) : ProcessorUtils.simpleTypeName(focus);
    String readName =
        sameName
            ? ProcessorUtils.qualifiedTypeName(declaredRead)
            : ProcessorUtils.simpleTypeName(declaredRead);
    // A focus is a type argument, so a primitive read is declared as its wrapper.
    String readAsFocus =
        declaredRead.getKind().isPrimitive()
            ? ProcessorUtils.simpleTypeName(asFocus(typeUtils, declaredRead))
            : readName;
    Diagnostics.error(
        messager,
        method,
        tag,
        "'"
            + getterName
            + "()' reads '"
            + readName
            + "', not the lens's focus '"
            + focusName
            + "'.",
        "The generated lens reads through 'source."
            + getterName
            + "()' and hands what it reads back as its focus, which '"
            + readName
            + "' is not.",
        (namesItsGetter
                ? "Point " + tag + "'s 'getter' at an accessor that reads '" + focusName + "'"
                : "Name the lens method after an accessor that reads '" + focusName + "'")
            // A lens can be declared over what an accessor reads, unless it reads nothing, or a
            // type only a wildcard stands for, neither of which can be written as a focus.
            + (declaredRead.getKind() == TypeKind.VOID
                    || declaredRead.getKind() == TypeKind.WILDCARD
                ? "."
                : ", or declare the lens over '"
                    + readAsFocus
                    + "' and rebuild it through a method that takes one."));
    return true;
  }

  /**
   * Reports an accessor whose read reaches the lens's focus only through an unchecked conversion,
   * and returns whether it did. A raw {@code List} reaches {@code List<String>} that way, and the
   * generated read hands it back as the focus, so the conversion would be made in the generated
   * file.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param sourceType the source type {@code S}, as the spec names it
   * @param getter the accessor the lens reads through
   * @param getterName its name
   * @param read what it reads on the captured source, as the generated call reads it
   * @param focusType the lens's focus, as the spec writes it
   * @param target the type the read has to reach: the focus, or the upper bound of a wildcard one
   * @param namesItsGetter whether the strategy carries a {@code getter} attribute to point
   *     elsewhere
   * @return true when the read is unchecked, and an error was reported
   */
  private boolean readsUnchecked(
      ExecutableElement method,
      String tag,
      DeclaredType sourceType,
      ExecutableElement getter,
      String getterName,
      TypeMirror read,
      TypeMirror focusType,
      TypeMirror target,
      boolean namesItsGetter) {
    // An accessor that declares type variables of its own has them inferred at the generated
    // call, from the focus the read is handed back as, so what it reads is not what it is passed.
    TypeMirror returned = getter.getReturnType();
    if (!ProcessorUtils.convertsUnchecked(typeUtils, read, target)
        || getter.getTypeParameters().stream()
            .anyMatch(variable -> ProcessorUtils.mentions(returned, variable))) {
      return false;
    }
    TypeMirror declaredRead = ProcessorUtils.returnTypeIn(typeUtils, sourceType, getter);
    String targetName = ProcessorUtils.simpleTypeName(target);
    Diagnostics.error(
        messager,
        method,
        tag,
        "'"
            + getterName
            + "()' reads "
            + described(typeUtils, declaredRead, target)
            + ", which reaches the lens's focus '"
            + ProcessorUtils.simpleTypeName(focusType)
            + "' only through an unchecked conversion.",
        "The generated lens hands what 'source."
            + getterName
            + "()' reads back as its focus, and "
            + UNCHECKED_IN_GENERATED_CODE,
        (namesItsGetter
                ? "Point " + tag + "'s 'getter' at an accessor that reads '" + targetName + "'"
                : "Name the lens method after an accessor that reads '" + targetName + "'")
            + ", or declare the lens over '"
            + ProcessorUtils.simpleTypeName(declaredRead)
            + "'"
            // The lens's own declaration then names the raw type, which the author answers for.
            + (ProcessorUtils.firstRawIn(declaredRead) == null
                ? ""
                : ", with @SuppressWarnings(\"rawtypes\") on the lens method,")
            + " and rebuild it through "
            + (tag.equals("@ViaConstructor") ? "a constructor" : "a method")
            + " that takes "
            // A declared read converts unchecked only to a declared focus.
            + (declaredRead.getKind() == TypeKind.DECLARED
                ? "the raw '"
                    + ProcessorUtils.simpleTypeName(
                        rawView(typeUtils, (DeclaredType) declaredRead, (DeclaredType) target))
                    + "'"
                : "'" + ProcessorUtils.simpleTypeName(declaredRead) + "'")
            + ".");
    return true;
  }

  /**
   * Why an unchecked conversion or call in generated code is refused, as the end of a reason: the
   * clause after "and".
   */
  static final String UNCHECKED_IN_GENERATED_CODE =
      "javac flags that as unchecked in the generated file, which no @SuppressWarnings on your spec"
          + " reaches.";

  /**
   * The raw type a declared type converts to {@code to} unchecked through: itself where it is raw,
   * and otherwise the raw supertype it reaches {@code to}'s class as, as a class extending a raw
   * {@code ArrayList} reaches {@code List}. An unchecked conversion from a declared type is always
   * to a declared type, whose class the source reaches through such a supertype.
   */
  private static DeclaredType rawView(Types types, DeclaredType from, DeclaredType to) {
    return ProcessorUtils.isRaw(from)
        ? from
        : (DeclaredType) ProcessorUtils.supertypeOf(types, from, (TypeElement) to.asElement());
  }

  /**
   * A type as an unchecked conversion's message names it: {@code the raw type 'List'} where the
   * type is raw itself, {@code 'Legacy', a raw 'List'} where it reaches its target through a raw
   * supertype, and the type alone where it is an array or a type variable, whose name shows the raw
   * type it holds or is bounded by.
   *
   * @param types the round's type utilities
   * @param from the type converted
   * @param to the type it is converted to
   * @return the description, in quotes
   */
  static String described(Types types, TypeMirror from, TypeMirror to) {
    String name = "'" + ProcessorUtils.simpleTypeName(from) + "'";
    if (from.getKind() != TypeKind.DECLARED) {
      return name;
    }
    DeclaredType declared = (DeclaredType) from;
    return ProcessorUtils.isRaw(declared)
        ? "the raw type " + name
        : name
            + ", a raw '"
            + ProcessorUtils.simpleTypeName(rawView(types, declared, (DeclaredType) to))
            + "'";
  }

  /**
   * Reports a one-argument call the generated lens would make unchecked, and returns whether it
   * did: a call made through a raw type that erases its parameter, or one that takes the lens's
   * focus only through an unchecked conversion, as a parameter written {@code List<String>} takes a
   * focus written raw. The first rests on the call alone, so it is asked whatever the focus is; the
   * second is not asked of a focus written as a wildcard, whose type javac infers.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param on the type the call is made on
   * @param bound the method the call binds, an instance method
   * @param focusType the lens's focus, which the call passes
   * @param call the call as the generated lens writes it, for the reason
   * @param sourceType the source type, which the remedy offers another strategy for
   * @return true when the call is unchecked, and an error was reported
   */
  private boolean setsUnchecked(
      ExecutableElement method,
      String tag,
      Receiver on,
      ExecutableElement bound,
      TypeMirror focusType,
      String call,
      DeclaredType sourceType) {
    DeclaredType owner = on.type();
    if (ProcessorUtils.callsUnchecked(typeUtils, owner, bound)) {
      String signature = signatureAsDeclared(bound);
      String reason =
          "A raw type erases the parameters of the methods called through it, and "
              + UNCHECKED_IN_GENERATED_CODE;
      String rebuild = rebuildAnotherWay(sourceType, tag);
      // The source type is never raw itself, so a raw owner is a builder its toBuilder handed
      // back; otherwise the method is inherited from a raw supertype.
      if (ProcessorUtils.erasesItsMembers(owner)) {
        Diagnostics.error(
            messager,
            method,
            tag,
            "'"
                + ((Receiver.OnBuilder) on).handedBackBy()
                + "' hands back the raw type '"
                + ProcessorUtils.simpleTypeName(on.shown())
                + "', so the generated call to '"
                + signature
                + "' on it is unchecked.",
            reason,
            "Point "
                + tag
                + "'s 'toBuilder' at a method that hands back the builder with its type"
                + " arguments, or "
                + rebuild
                + ".");
        return true;
      }
      Diagnostics.error(
          messager,
          method,
          tag,
          "'"
              + ProcessorUtils.simpleTypeName(on.shown())
              + "' inherits '"
              + signature
              + "' from the raw type '"
              + ProcessorUtils.simpleTypeName(
                  ProcessorUtils.supertypeOf(
                      typeUtils, owner, (TypeElement) bound.getEnclosingElement()))
              + "', so the generated call to it is unchecked.",
          reason,
          ProcessorUtils.capitalise(rebuild) + ".");
      return true;
    }
    TypeMirror focus = focusRead(focusType);
    if (focus == null) {
      return false;
    }
    // Decided as the call reads the parameter, on the owner captured: a wildcard of the owner is
    // a type of its own there, which a raw focus reaches only unchecked, where on the owner as
    // declared 'List<T>' reads as the reifiable 'List<?>' a raw focus reaches freely.
    DeclaredType captured = (DeclaredType) typeUtils.capture(owner);
    TypeMirror called =
        ProcessorUtils.memberOf(typeUtils, captured, bound).getParameterTypes().getFirst();
    if (!ProcessorUtils.convertsUnchecked(typeUtils, focus, called)) {
      return false;
    }
    // Where a wildcard's type stands in the parameter, no type the spec writes names it.
    Optional<StandIn> standIn = standInNamed(on, captured, List.of(called));
    if (standIn.isPresent()) {
      reportTakesAWildcardUnchecked(method, tag, on, bound, focus, call, sourceType, standIn.get());
      return true;
    }
    TypeMirror parameter =
        ProcessorUtils.memberOf(typeUtils, owner, bound).getParameterTypes().getFirst();
    String parameterName = ProcessorUtils.simpleTypeName(parameter);
    Diagnostics.error(
        messager,
        method,
        tag,
        "'"
            + signatureOn(on.shown(), bound)
            + "' takes the lens's focus, "
            + described(typeUtils, focus, parameter)
            + ", only through an unchecked conversion to '"
            + parameterName
            + "'.",
        "The generated lens sets through " + call + ", and " + UNCHECKED_IN_GENERATED_CODE,
        "Declare the lens over '"
            + parameterName
            + "', the type '"
            + bound.getSimpleName()
            + "' takes; otherwise "
            + rebuildAnotherWay(sourceType, tag)
            + ".");
    return true;
  }

  /**
   * Reports a one-argument call whose parameter names the type a wildcard stands for, which the
   * lens's focus reaches only through an unchecked conversion: a raw {@code List} passed to {@code
   * items(List<T>)} on the builder a {@code Bin<?>} hands back. No type the spec writes names the
   * parameter there, so the message names the method as it is declared. Where the wildcard is the
   * spec's source type's, a type argument in its place, or a type parameter of the spec, names the
   * type, and every other strategy would set the value through a method taking the same type; where
   * it is the builder's own, the method that hands the builder back is at fault.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param on the type the call is made on
   * @param bound the method the call binds
   * @param focus the lens's focus, which the call passes
   * @param call the call as the generated lens writes it, for the reason
   * @param sourceType the source type, as the spec names it
   * @param standIn whose wildcard the parameter names the type of
   */
  private void reportTakesAWildcardUnchecked(
      ExecutableElement method,
      String tag,
      Receiver on,
      ExecutableElement bound,
      TypeMirror focus,
      String call,
      DeclaredType sourceType,
      StandIn standIn) {
    boolean sources = standIn == StandIn.SOURCE;
    Diagnostics.error(
        messager,
        method,
        tag,
        "'"
            + (sources ? signatureOn(on.named(), bound) : signatureAsDeclared(bound))
            + "' takes a type that names what a wildcard of '"
            + ProcessorUtils.simpleTypeName(sources ? sourceType : on.shown())
            + "' stands for, which the lens's focus, "
            + described(typeUtils, focus, bound.getParameters().getFirst().asType())
            + ", reaches only through an unchecked conversion.",
        "The generated lens sets through " + call + ", and " + UNCHECKED_IN_GENERATED_CODE,
        sources
            ? declareTheSpecOver(sourceType, bound.getSimpleName().toString()) + "."
            : "Point "
                + tag
                + "'s 'toBuilder' at a method that hands back the builder with its type"
                + " arguments; otherwise "
                + rebuildAnotherWay(sourceType, tag)
                + ".");
  }

  /**
   * The remedy for a parameter that names what a wildcard of the source type stands for, as an
   * unfinished clause: the spec names the type instead, and the focus as {@code method} then takes
   * it. No other strategy helps, since each sets the value through a method that names the type
   * alike.
   */
  private String declareTheSpecOver(DeclaredType sourceType, String method) {
    return "Declare the spec over '"
        + ProcessorUtils.simpleTypeName(typeUtils.erasure(sourceType))
        + "' with a type argument in place of each wildcard, a type parameter of the spec if need"
        + " be, and the focus as the type '"
        + method
        + "' then takes";
  }

  /**
   * The remedy that rebuilds the source type with another strategy, as an unfinished clause: any of
   * them, or where no constructor call can rebuild it, as {@link #constructorUnwritable} says, only
   * those that name no constructor.
   */
  private static String rebuildAnotherWay(DeclaredType sourceType, String tag) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    return constructorUnwritable(sourceType)
        ? rebuildWith(source, tag, List.of("@Wither", "@ViaBuilder"))
        : rebuildWith(source, tag);
  }

  /**
   * The setter call a {@code @ViaCopyAndSet} lens makes on its copy, refused where no method of the
   * name can take the value or the call it binds is unchecked, and an error was reported.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param getterName the accessor the lens reads its focus through
   * @param setterName the method the strategy sets through
   * @param focusType the value the call passes: the lens's focus, or the primitive it is unboxed to
   * @param targetPackage the package the optics class is generated into
   * @return the method the call binds, a refusal that has been reported, or undecided
   */
  private SetterCall copySetterCall(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String getterName,
      String setterName,
      TypeMirror focusType,
      String targetPackage) {
    Receiver on = new Receiver.OnSource(sourceType, sourceTypeElement);
    SetterCall setter =
        boundSetter(
            method,
            "@ViaCopyAndSet",
            on,
            setterName,
            focusType,
            "setter",
            sourceType,
            getterName,
            targetPackage);
    return setter instanceof SetterCall.Binds(ExecutableElement bound)
            && setsUnchecked(
                method,
                "@ViaCopyAndSet",
                on,
                bound,
                focusType,
                "'" + setterName + "(newValue)'",
                sourceType)
        ? new SetterCall.Refused()
        : setter;
  }

  /**
   * The methods of {@code setterName} on {@code S} that a {@code @ViaCopyAndSet} lens's setter call
   * might bind: those the generated class can call that one argument reaches, as {@link
   * CallBinding#takesArity} reads it, and that can take {@code argument}, read through their
   * erasure so that none javac might bind is left out. A wildcard focus is inferred from the getter
   * as much as from the wildcard, so every one its arity reaches stays.
   */
  private List<ExecutableElement> settersOneArgumentReaches(
      TypeElement sourceTypeElement, String setterName, TypeMirror argument, String targetPackage) {
    return callableMembers(sourceTypeElement, targetPackage).stream()
        .filter(member -> member.getSimpleName().contentEquals(setterName))
        .filter(member -> CallBinding.takesArity(member, 1))
        .filter(
            member -> argument.getKind() == TypeKind.WILDCARD || takesArgument(member, argument))
        .toList();
  }

  /**
   * Whether {@code argument} can be passed to {@code member}, one argument reaching it, read
   * through its erasure: as its first parameter, or as the element of its array.
   */
  private boolean takesArgument(ExecutableElement member, TypeMirror argument) {
    TypeMirror first = typeUtils.erasure(member.getParameters().getFirst().asType());
    return typeUtils.isAssignable(argument, first)
        || (member.isVarArgs()
            && member.getParameters().size() == 1
            && typeUtils.isAssignable(argument, ((ArrayType) first).getComponentType()));
  }

  /**
   * Whether {@code member} returns an {@code S} whatever the call passes it, so that a wither's
   * result can be handed back as one.
   *
   * <p>The call's result has to be an {@code S} whichever of the methods it might bind javac
   * settles on, since that can rest on inference these checks do not repeat. Each has to return one
   * as {@link ProcessorUtils#returnsOwner} reads it, and a return that names the method's own type
   * variables only where nothing else it declares names them, since the call then infers them from
   * more than the {@code S} it hands back.
   */
  private boolean returnsSourceForAnyArgument(DeclaredType sourceType, ExecutableElement member) {
    return ProcessorUtils.returnsOwner(typeUtils, sourceType, member)
        && (member.getTypeParameters().stream()
                .noneMatch(variable -> ProcessorUtils.mentions(member.getReturnType(), variable))
            || inferredFromTheTargetAlone(member));
  }

  /**
   * Whether nothing a generic method declares but its return names its type variables: no
   * parameter, no thrown type, and no bound of one variable names another.
   */
  private static boolean inferredFromTheTargetAlone(ExecutableElement member) {
    List<? extends TypeParameterElement> variables = member.getTypeParameters();
    List<TypeMirror> signature =
        Stream.concat(
                member.getParameters().stream().map(Element::asType),
                member.getThrownTypes().stream())
            .toList();
    return variables.stream()
        .noneMatch(
            variable ->
                signature.stream().anyMatch(type -> ProcessorUtils.mentions(type, variable))
                    || variables.stream()
                        .filter(other -> !other.equals(variable))
                        .flatMap(other -> other.getBounds().stream())
                        .anyMatch(bound -> ProcessorUtils.mentions(bound, variable)));
  }

  /**
   * Warns that a {@code @ViaCopyAndSet} lens drops what its setter returns where the setter is
   * spelt as a wither and some method of its name may return an {@code S}: a wither's new value
   * would be lost. {@code @SuppressWarnings} with {@link #DROPPED_RESULT} on the lens method, or a
   * declaration enclosing it, keeps it quiet.
   *
   * @param method the annotated lens method, which the warning is reported on
   * @param sourceType the source type {@code S}, as the spec names it
   * @param setterName the method the strategy sets through
   * @param reachable the methods the setter call might bind, some of which do not return an {@code
   *     S} whatever they are passed
   * @param argument what the call passes
   * @param setterCall the method the call binds, where that is decided, which {@code @Wither} would
   *     bind too
   */
  private void warnIfDropsWitherResult(
      ExecutableElement method,
      DeclaredType sourceType,
      String setterName,
      List<ExecutableElement> reachable,
      TypeMirror argument,
      SetterCall setterCall) {
    // A call that binds a method returning no S drops nothing, whatever the others return.
    if ((setterCall instanceof SetterCall.Binds(ExecutableElement binding)
            && !mayReturnSource(sourceType, binding))
        || reachable.stream().noneMatch(member -> mayReturnSource(sourceType, member))
        || Diagnostics.suppressed(method, DROPPED_RESULT)) {
      return;
    }
    String source = ProcessorUtils.simpleTypeName(sourceType);
    List<String> unkept =
        reachable.stream()
            .filter(member -> !returnsSourceForAnyArgument(sourceType, member))
            .map(member -> returnPhrase(sourceType, member))
            .toList();
    // @Wither binds the method the focus binds, and serves where that one returns an S. Under a
    // wildcard focus, or where the binding rests on inference, javac may settle on another, so
    // @Wither is not offered.
    boolean witherServes =
        argument.getKind() != TypeKind.WILDCARD
            && setterCall instanceof SetterCall.Binds(ExecutableElement bound)
            && ProcessorUtils.returnsOwner(typeUtils, sourceType, bound);
    Diagnostics.warning(
        messager,
        method,
        "@ViaCopyAndSet",
        "The generated lens returns the copy it calls '"
            + setterName
            + "' on, and drops what '"
            + setterName
            + "' returns.",
        "A method spelt as a wither usually hands back a new value and leaves the copy as it was,"
            + " and then the lens's set changes nothing. The lens can hand back the result only"
            + " where every method of that name the new value can be passed to returns a '"
            + source
            + "', and "
            + listed(unkept)
            + ".",
        "Where '"
            + setterName
            + "' sets the copy it is called on, annotate '"
            + method.getSimpleName()
            + "()' @SuppressWarnings(\""
            + DROPPED_RESULT
            + "\"); otherwise "
            + (witherServes
                ? rebuildWith(source, "@ViaCopyAndSet")
                : "name a setter that sets the copy, or rebuild '"
                    + source
                    + "' with @ViaBuilder or @ViaConstructor")
            + ".");
  }

  /**
   * Whether what {@code member} returns may be, or hold, an {@code S}, read through both erasures:
   * the source type's class, a subclass or a supertype of it, a raw or another instantiation of
   * one, or a type variable bounded by one; or a type with such an argument, as {@code Optional<S>}
   * is.
   */
  private boolean mayReturnSource(DeclaredType sourceType, ExecutableElement member) {
    TypeMirror returned = ProcessorUtils.returnTypeIn(typeUtils, sourceType, member);
    return relatedByErasure(returned, sourceType)
        || (returned instanceof DeclaredType declared
            && declared.getTypeArguments().stream()
                .anyMatch(argument -> relatedByErasure(argument, sourceType)));
  }

  /** Whether one of two types' erasures is assignable to the other's. */
  private boolean relatedByErasure(TypeMirror type, TypeMirror other) {
    TypeMirror erased = typeUtils.erasure(type);
    TypeMirror otherErased = typeUtils.erasure(other);
    return typeUtils.isAssignable(erased, otherErased)
        || typeUtils.isAssignable(otherErased, erased);
  }

  /**
   * What {@code member} returns, as the dropped-result warning's reason names it: a type that is
   * not an {@code S}, or a retag that more than the source type infers.
   */
  private String returnPhrase(DeclaredType sourceType, ExecutableElement member) {
    String returned =
        "'"
            + signatureOn(sourceType, member)
            + "' returns '"
            + ProcessorUtils.simpleTypeName(
                ProcessorUtils.returnTypeIn(typeUtils, sourceType, member))
            + "'";
    return ProcessorUtils.returnsOwner(typeUtils, sourceType, member)
        ? returned + ", whose type the call infers from more than what it is assigned to"
        : returned;
  }

  /**
   * What a generated one-argument call comes to, made as {@code on} says.
   *
   * <p>A focus written as a wildcard leaves the choice to javac, as it does for a wither, so the
   * call is undecided there rather than refused, and so is one javac settles by inference. Of
   * several methods of the name, the call binds the one {@link #boundThroughTheGetter} says, where
   * it says one.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param on the type the call is made on, whose members are searched
   * @param setterName the method the strategy sets through
   * @param focusType the lens's focus, which the call passes
   * @param attribute the annotation attribute that names it
   * @param source the source type {@code S}, which a remedy offers another strategy for, and whose
   *     getter the lens reads its focus through
   * @param getterName that getter
   * @param targetPackage the package the optics class is generated into
   * @return the method the call binds, a refusal that has been reported, or undecided
   */
  private SetterCall boundSetter(
      ExecutableElement method,
      String tag,
      Receiver on,
      String setterName,
      TypeMirror focusType,
      String attribute,
      DeclaredType source,
      String getterName,
      String targetPackage) {
    DeclaredType owner = on.type();
    String ownerName = ProcessorUtils.simpleTypeName(on.shown());
    List<ExecutableElement> named =
        callableMembers(on.element(), targetPackage).stream()
            .filter(member -> member.getSimpleName().contentEquals(setterName))
            .toList();
    List<ExecutableElement> oneParameter =
        named.stream().filter(member -> member.getParameters().size() == 1).toList();
    if (named.isEmpty()) {
      reportMissingSetter(method, tag, on, setterName, attribute, targetPackage);
      return new SetterCall.Refused();
    }
    if (focusRead(focusType) == null) {
      if (oneParameter.isEmpty()) {
        reportSetterOfAnotherArity(
            method,
            tag,
            on,
            setterName,
            named,
            attribute,
            source,
            setterOffer(tag, named, setterName, on, targetPackage));
        return new SetterCall.Refused();
      }
      // Where the name carries only one such method, that is the one. Of several, a protobuf
      // builder's addAllTags among them, the call binds as one passing what the getter reads,
      // where javac infers the focus from it alone; else it is javac's to settle, and so is what
      // it hands back.
      return oneParameter.size() == 1
          ? new SetterCall.Binds(oneParameter.getFirst())
          : boundThroughTheGetter(
                  (WildcardType) focusType, owner, named, source, getterName, targetPackage)
              .<SetterCall>map(SetterCall.Binds::new)
              .orElseGet(SetterCall.Undecided::new);
    }
    String call = "'" + setterName + "(newValue)'";
    return switch (CallBinding.resolve(typeUtils, owner, List.of(focusType), named)) {
      case CallBinding.Binds(ExecutableElement bound) -> {
        if (!bound.getModifiers().contains(Modifier.STATIC)) {
          yield new SetterCall.Binds(bound);
        }
        Diagnostics.error(
            messager,
            method,
            tag,
            "'"
                + signatureOn(on.shown(), bound)
                + "' is static, so the generated lens cannot set through it on a '"
                + ownerName
                + "'.",
            "The generated lens sets through "
                + call
                + " with the new value typed '"
                + ProcessorUtils.simpleTypeName(focusType)
                + "', which binds that method, and a static method never reads the value it is"
                + " called on.",
            "Set " + tag + "'s '" + attribute + "' to an instance method.");
        yield new SetterCall.Refused();
      }
      case CallBinding.NoneApplies() -> {
        reportInapplicableSetter(
            method,
            tag,
            on,
            setterName,
            focusType,
            attribute,
            source,
            getterName,
            named,
            targetPackage);
        yield new SetterCall.Refused();
      }
      case CallBinding.Ambiguous(List<ExecutableElement> methods) -> {
        List<String> signatures =
            methods.stream().map(member -> "'" + signatureAsDeclared(member) + "'").toList();
        Diagnostics.error(
            messager,
            method,
            tag,
            "The generated call to '"
                + setterName
                + "' on a '"
                + ownerName
                + "' cannot choose between "
                + listed(signatures)
                + ".",
            "The generated lens sets through "
                + call
                + " with the new value typed '"
                + ProcessorUtils.simpleTypeName(focusType)
                + "', which each of them takes, with no parameter more specific than every other.",
            ambiguityFix(owner, methods, source, tag));
        yield new SetterCall.Refused();
      }
      case CallBinding.Undecided(List<ExecutableElement> ignored) -> new SetterCall.Undecided();
    };
  }

  /**
   * The method a one-argument call binds where the lens's focus is a wildcard and the name carries
   * several methods. The generated lens is {@code Lens.of(source -> source.getter(), (source,
   * newValue) -> ...)}, and javac infers a focus written as {@code ?} or {@code ? extends T} from
   * the getter alone, the leftmost lambda, since nothing else bounds it from below (JLS 18.5.2.2):
   * as what the getter reads, boxed. The getter is the method a call with no argument binds, as
   * javac chooses among methods of its name, and the call binds as one passing what it reads. Empty
   * where javac weighs more than the getter, or cannot infer the focus from it: a wildcard bounded
   * from below, a getter with type parameters of its own, whose read javac infers from the focus
   * rather than the other way about, a read the wildcard's bound does not admit, or a call that
   * rests on inference or binds nothing. javac settles or reports each of those at the generated
   * call.
   *
   * @param focusType the lens's focus
   * @param owner the type the call is made on
   * @param named the methods of the name the call chooses among
   * @param source the source type {@code S}, whose getter the lens reads its focus through
   * @param getterName that getter
   * @param targetPackage the package the optics class is generated into
   * @return the method the call binds, or empty
   */
  private Optional<ExecutableElement> boundThroughTheGetter(
      WildcardType focusType,
      DeclaredType owner,
      List<ExecutableElement> named,
      DeclaredType source,
      String getterName,
      String targetPackage) {
    if (focusType.getSuperBound() != null) {
      return Optional.empty();
    }
    TypeMirror bound =
        Objects.requireNonNullElse(
            focusType.getExtendsBound(), elementUtils.getTypeElement("java.lang.Object").asType());
    List<ExecutableElement> getters =
        callableMembers((TypeElement) source.asElement(), targetPackage).stream()
            .filter(member -> member.getSimpleName().contentEquals(getterName))
            .toList();
    return Optional.of(CallBinding.resolve(typeUtils, source, List.of(), getters))
        .filter(CallBinding.Binds.class::isInstance)
        .map(binding -> ((CallBinding.Binds) binding).candidate())
        .filter(getter -> getter.getTypeParameters().isEmpty())
        .map(
            getter ->
                asFocus(typeUtils, ProcessorUtils.returnTypeCalledOn(typeUtils, source, getter)))
        .filter(read -> typeUtils.isAssignable(read, bound))
        .map(read -> CallBinding.resolve(typeUtils, owner, List.of(read), named))
        .filter(CallBinding.Binds.class::isInstance)
        .map(binding -> ((CallBinding.Binds) binding).candidate());
  }

  /**
   * Reports a setter none of whose methods, {@code named}, takes the lens's focus. Where a
   * parameter names the type a wildcard stands for, no focus would do, so the message says whose
   * wildcard it is, and names the methods as declared, which name what the wildcard stands in for:
   * on the builder as {@code toBuilder()} declares it, for a wildcard of the source; otherwise it
   * lists the methods and offers what would.
   */
  private void reportInapplicableSetter(
      ExecutableElement method,
      String tag,
      Receiver on,
      String setterName,
      TypeMirror focusType,
      String attribute,
      DeclaredType source,
      String getterName,
      List<ExecutableElement> named,
      String targetPackage) {
    DeclaredType captured = (DeclaredType) typeUtils.capture(on.type());
    Optional<StandIn> standIn =
        standInNamed(
            on,
            captured,
            named.stream()
                .filter(member -> member.getParameters().size() == 1)
                .map(member -> ProcessorUtils.firstParameterTypeIn(typeUtils, captured, member))
                .toList());
    // The source's wildcard is named where it is declared, as the call reaches it.
    DeclaredType listed =
        standIn.filter(StandIn.SOURCE::equals).map(ignored -> on.named()).orElse(on.shown());
    String listedName = ProcessorUtils.simpleTypeName(listed);
    String focusName = ProcessorUtils.simpleTypeName(focusType);
    String found =
        "The generated lens sets through '"
            + setterName
            + "(newValue)' with the new value typed '"
            + focusName
            + "'. Found on '"
            + listedName
            + "': "
            + named.stream()
                .map(
                    member ->
                        standIn.isPresent() && standIn.get() == StandIn.BUILDER
                            ? signatureAsDeclared(member)
                            : signatureOn(listed, member))
                .toList()
            + ".";
    String reason;
    String fix;
    if (standIn.isPresent()) {
      boolean sources = standIn.get() == StandIn.SOURCE;
      reason =
          found
              + " A parameter that a wildcard of '"
              + ProcessorUtils.simpleTypeName(sources ? source : on.shown())
              + "' stands in takes no value at all, since the type it stands for is unknown.";
      fix =
          sources
              ? declareTheSpecOver(source, setterName)
              : "Point "
                  + tag
                  + "'s 'toBuilder' at a method that hands back the builder with its type"
                  + " arguments, or "
                  + rebuildAnotherWay(source, tag);
    } else {
      reason = found + setterOffer(tag, named, setterName, on, targetPackage);
      fix =
          narrowerFocus(source, getterName, focusType, on.type(), named)
                  .orElse(
                      "Set "
                          + tag
                          + "'s '"
                          + attribute
                          + "' to a method that takes the value the getter reads"
                          + (tag.equals("@ViaBuilder")
                              ? ", or point @ViaBuilder's 'getter' at an accessor one of them"
                                  + " takes and declare the focus as its type"
                              : ""))
              + "; otherwise "
              + rebuildAnotherWay(source, tag);
    }
    Diagnostics.error(
        messager,
        method,
        tag,
        "No method '"
            + setterName
            + "' of '"
            + listedName
            + "' takes the lens's focus type '"
            + focusName
            + "'.",
        reason,
        fix + ".");
  }

  /**
   * What a generated setter call came to: the method it binds, a refusal already reported, or a
   * choice javac settles for itself.
   */
  private sealed interface SetterCall {

    /**
     * The call binds this method.
     *
     * @param method the method the call binds
     */
    record Binds(ExecutableElement method) implements SetterCall {}

    /** No method of the name can take the value, and an error was reported. */
    record Refused() implements SetterCall {}

    /** Which method the call binds rests on inference, so the rest is left to javac. */
    record Undecided() implements SetterCall {}
  }

  /**
   * What reading a {@code @ViaBuilder} chain came to: a chain the generated lens rebuilds through,
   * or a refusal already reported.
   */
  private sealed interface BuilderChain {

    /**
     * The lens rebuilds through the chain.
     *
     * @param clear the builder method the lens calls before the setter, or empty for none
     */
    record Rebuilds(String clear) implements BuilderChain {}

    /** The generated lens could not walk the chain, and an error was reported. */
    record Refused() implements BuilderChain {}
  }

  /**
   * Reports a setter no overload of which takes one argument, whatever the lens's focus is, with
   * {@code offer}, the {@link #setterOffer} for its name.
   */
  private void reportSetterOfAnotherArity(
      ExecutableElement method,
      String tag,
      Receiver on,
      String setterName,
      List<ExecutableElement> named,
      String attribute,
      DeclaredType source,
      String offer) {
    String ownerName = ProcessorUtils.simpleTypeName(on.shown());
    Diagnostics.error(
        messager,
        method,
        tag,
        "No method '" + setterName + "' of '" + ownerName + "' takes one argument.",
        "The generated lens sets through '"
            + setterName
            + "(newValue)', passing the one value it sets. Found on '"
            + ownerName
            + "': "
            + named.stream().map(member -> signatureOn(on.shown(), member)).toList()
            + "."
            + offer,
        "Set "
            + tag
            + "'s '"
            + attribute
            + "' to a method that takes the value the lens sets; otherwise "
            + rebuildAnotherWay(source, tag)
            + ".");
  }

  /** Reports a setter the generated optic calls and the type it is called on does not have. */
  private void reportMissingSetter(
      ExecutableElement method,
      String tag,
      Receiver on,
      String setterName,
      String attribute,
      String targetPackage) {
    String ownerName = ProcessorUtils.simpleTypeName(on.shown());
    Diagnostics.error(
        messager,
        method,
        tag,
        "'"
            + ownerName
            + "' has no method '"
            + setterName
            + "' for the generated lens to set"
            + " through.",
        "The generated lens sets through '"
            + setterName
            + "(newValue)' on '"
            + ownerName
            + "', so it needs a method of that name there that the generated class in '"
            + targetPackage
            + "' can call."
            + didYouMean(
                collectionAdder(on, setterName, setterNames(tag, on, targetPackage))
                    .or(
                        () ->
                            setterConvention(tag)
                                .nearest(setterName, setterNames(tag, on, targetPackage)))),
        "Set " + tag + "'s '" + attribute + "' to a method '" + ownerName + "' declares.");
  }

  /**
   * Reads the chain a {@code @ViaBuilder} lens rebuilds through, and reports one the generated lens
   * cannot walk.
   *
   * <p>The lens rebuilds with {@code source.toBuilder().setter(newValue).build()}, so each step has
   * to exist where the one before it leads: the builder the source hands back, the setter that
   * takes the focus, and a {@code build} that hands the source type back. Each step is read on what
   * the one before it hands back captured, as javac reads the call, {@code toBuilder()} on the
   * source: a self-typed builder handed back with wildcards, as Lombok's {@code @SuperBuilder}
   * hands back a {@code SupBuilder<?, ?>}, leads on to the builder and the source type its
   * parameters are bounded by, and a step bounded by several types is read on the one that declares
   * the next call. None of it may be unchecked: a setter called through a raw builder that erases
   * its parameter, or a {@code build} that reaches the source type only by an unchecked conversion,
   * which a raw builder's erasure makes of a generic one.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param getterName the accessor the lens reads its focus through
   * @param toBuilderName the method that hands back the builder
   * @param setterName the builder method that takes the focus
   * @param buildName the builder method that hands the source type back
   * @param focusType the lens's focus
   * @param targetPackage the package the optics class is generated into
   * @return the chain the lens rebuilds through, or a refusal, already reported
   */
  private BuilderChain builderChain(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String getterName,
      String toBuilderName,
      String setterName,
      String buildName,
      TypeMirror focusType,
      String targetPackage) {
    ExecutableElement toBuilder =
        accessorNamed(callableMembers(sourceTypeElement, targetPackage), toBuilderName);
    if (toBuilder == null) {
      reportMissingAccessor(
          method,
          "@ViaBuilder",
          sourceType,
          sourceTypeElement,
          toBuilderName,
          "rebuild through",
          "'source'",
          "Set @ViaBuilder's 'toBuilder' to a method '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' declares"
              // Lombok writes no toBuilder() on an abstract class, only on each concrete one.
              + (sourceTypeElement.getModifiers().contains(Modifier.ABSTRACT)
                  ? ", or declare the spec over a concrete subclass that declares one."
                  : "."),
          Optional.empty(),
          targetPackage);
      return new BuilderChain.Refused();
    }
    // Read as the generated call reads it, on the source captured, so that a wildcard of the
    // source type the spec writes is a type of its own wherever the builder names it: a Bag<?>
    // whose toBuilder() hands back a Builder<List<T>> hands back a builder of a list of that type.
    DeclaredType capturedSource = (DeclaredType) typeUtils.capture(sourceType);
    TypeMirror builderReached = ProcessorUtils.returnTypeIn(typeUtils, capturedSource, toBuilder);
    if (reportsUndeclaredOnEveryBound(
        method, builderReached, toBuilderName + "()", setterName, "setter", targetPackage)) {
      return new BuilderChain.Refused();
    }
    TypeMirror builderType = stepTypeFor(builderReached, setterName, targetPackage);
    // A source type read from a class file can name a type missing from the classpath; one
    // declared in source is waited for until it resolves. Which of several bounds' setters the
    // call binds is javac's to settle.
    if (builderType == null || builderType.getKind() == TypeKind.ERROR) {
      return new BuilderChain.Rebuilds("");
    }
    TypeElement builderElement = elementOf(builderType);
    if (builderElement == null) {
      reportBuilderStep(
          method,
          toBuilderName + "()",
          shownAlone(builderType),
          "a builder to set through",
          sourceType);
      return new BuilderChain.Refused();
    }
    // A message names the builder as the source type the spec writes reads it, else as
    // toBuilder() declares it, either naming this very class, whose methods are read on it alike;
    // else as the type reached, where that can be written.
    DeclaredType builderDeclared = declaredAs(toBuilder.getReturnType(), builderElement);
    DeclaredType builderShown =
        Optional.ofNullable(
                declaredAs(
                    ProcessorUtils.returnTypeIn(typeUtils, sourceType, toBuilder), builderElement))
            .or(() -> Optional.ofNullable(builderDeclared))
            .orElseGet(() -> (DeclaredType) shownAlone(builderType));
    if (reportsUnnameableStep(
        method, builderElement, toBuilderName + "()", sourceType, targetPackage)) {
      return new BuilderChain.Refused();
    }
    // A builder read from a class file can extend a type missing from the classpath, which no
    // round brings; one declared in source is waited for until it resolves (WaitingImporters).
    // The chain is written out as it stands, for javac to report.
    if (hierarchyUnresolved(builderType)) {
      return new BuilderChain.Rebuilds("");
    }
    // A toBuilder() the source type inherits hands back its declaring class's builder, which sets
    // only what that class holds, as a Lombok @SuperBuilder subclass without toBuilder = true of
    // its own meets for a property it adds.
    Element declaring = toBuilder.getEnclosingElement();
    boolean inherited = !declaring.equals(sourceTypeElement);
    String sourceClass = ProcessorUtils.simpleTypeName(typeUtils.erasure(sourceType));
    String parentClass = ProcessorUtils.simpleTypeName(typeUtils.erasure(declaring.asType()));
    List<ExecutableElement> builderMembers = callableMembers(builderElement, targetPackage);
    // A builder that builds the source type, as a self-typed Builder<Dog> does, is no reason to
    // blame the inheritance, and a misspelt setter on it draws the plain message and its offer.
    boolean buildsTheSource =
        Optional.ofNullable(accessorNamed(builderMembers, buildName))
            .map(
                build ->
                    typeUtils.isAssignable(
                        ProcessorUtils.returnTypeCalledOn(
                            typeUtils, (DeclaredType) builderType, build),
                        sourceType))
            .orElse(false);
    if (inherited
        && !buildsTheSource
        && builderMembers.stream()
            .noneMatch(member -> member.getSimpleName().contentEquals(setterName))) {
      Diagnostics.error(
          messager,
          method,
          "@ViaBuilder",
          inheritsToBuilder(sourceClass, toBuilderName, parentClass)
              + ", whose builder '"
              + ProcessorUtils.simpleTypeName(builderShown)
              + "' has no method '"
              + setterName
              + "' for the generated lens to set through.",
          "The generated lens rebuilds with 'source."
              + toBuilderName
              + "().setter(newValue).build()', and the builder that '"
              + parentClass
              + "' hands back neither sets '"
              + setterName
              + "' nor builds '"
              + sourceClass
              + "'.",
          ownToBuilder(toBuilderName, sourceClass)
              + ", or "
              + rebuildAnotherWay(sourceType, "@ViaBuilder")
              + ".");
      return new BuilderChain.Refused();
    }
    Receiver on =
        new Receiver.OnBuilder(
            (DeclaredType) builderType,
            builderElement,
            toBuilderName + "()",
            builderShown,
            builderDeclared,
            standIns(sourceType, capturedSource));
    SetterCall setter =
        boundSetter(
            method,
            "@ViaBuilder",
            on,
            setterName,
            focusType,
            "setter",
            sourceType,
            getterName,
            targetPackage);
    // Which method an undecided call binds is javac's to settle, so what it hands back is too.
    if (!(setter instanceof SetterCall.Binds(ExecutableElement bound))) {
      return setter instanceof SetterCall.Refused
          ? new BuilderChain.Refused()
          : new BuilderChain.Rebuilds("");
    }
    if (setsUnchecked(
        method,
        "@ViaBuilder",
        on,
        bound,
        focusType,
        "'" + setterName + "(newValue)'",
        sourceType)) {
      return new BuilderChain.Refused();
    }
    String clear =
        CollectionClear.of(
                typeUtils,
                elementUtils,
                (DeclaredType) builderType,
                builderMembers,
                setterName,
                bound,
                propertiesReadBesides(sourceTypeElement, getterName, targetPackage))
            .filter(
                candidate ->
                    handsBackTheBuilder(
                        candidate,
                        builderReached,
                        (DeclaredType) builderType,
                        setterName,
                        targetPackage))
            .map(candidate -> candidate.getSimpleName().toString())
            .orElse("");
    // The chain carries on from what the setter returns on the builder captured, as the generated
    // call reads it; a message names the step as the builder the spec reaches declares it.
    TypeMirror setReached =
        ProcessorUtils.returnTypeCalledOn(typeUtils, (DeclaredType) builderType, bound);
    String setterShown = signatureOn(builderShown, bound);
    if (reportsUndeclaredOnEveryBound(
        method, setReached, setterShown, buildName, "build", targetPackage)) {
      return new BuilderChain.Refused();
    }
    TypeMirror setType = stepTypeFor(setReached, buildName, targetPackage);
    // A builder read from a class file can hand back a type missing from the classpath; one
    // declared in source is waited for until it resolves. Which of several bounds' build methods
    // the call binds is javac's to settle.
    if (setType == null || setType.getKind() == TypeKind.ERROR) {
      return new BuilderChain.Rebuilds(clear);
    }
    TypeElement setElement = elementOf(setType);
    // Named where it can be as the builder the spec reaches declares it, naming the class build()
    // is read on, so build() is read on it alike.
    DeclaredType setDeclared =
        setElement == null
            ? null
            : declaredAs(ProcessorUtils.returnTypeIn(typeUtils, builderShown, bound), setElement);
    TypeMirror setShown = setDeclared != null ? setDeclared : reachedName(setType, builderShown);
    if (setElement == null) {
      reportBuilderStep(method, setterShown, setShown, "a builder to build from", sourceType);
      return new BuilderChain.Refused();
    }
    if (reportsUnnameableStep(method, setElement, setterShown, sourceType, targetPackage)) {
      return new BuilderChain.Refused();
    }
    ExecutableElement build = accessorNamed(callableMembers(setElement, targetPackage), buildName);
    if (build == null) {
      reportMissingAccessor(
          method,
          "@ViaBuilder",
          setShown,
          setElement,
          buildName,
          "finish the value it rebuilds",
          "the '" + ProcessorUtils.simpleTypeName(setShown) + "' the setter hands back",
          "Set @ViaBuilder's 'build' to a method '"
              + ProcessorUtils.simpleTypeName(setShown)
              + "' declares.",
          Optional.empty(),
          targetPackage);
      return new BuilderChain.Refused();
    }
    TypeMirror built = ProcessorUtils.returnTypeCalledOn(typeUtils, (DeclaredType) setType, build);
    boolean buildsSource = typeUtils.isAssignable(built, sourceType);
    TypeMirror builtShown =
        builtShown(build, built, buildsSource, setDeclared, setShown, sourceType);
    if (buildsSource) {
      return buildsUnchecked(
              method,
              sourceType,
              toBuilderName,
              (DeclaredType) builderType,
              setterShown,
              buildName,
              (DeclaredType) setType,
              built,
              builtShown)
          ? new BuilderChain.Refused()
          : new BuilderChain.Rebuilds(clear);
    }
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String builtName = ProcessorUtils.simpleTypeName(builtShown);
    String inheritance =
        inherited
            ? " "
                + inheritsToBuilder(sourceClass, toBuilderName, parentClass)
                + ", so the chain builds with the builder that '"
                + parentClass
                + "' hands back."
            : "";
    String fix =
        inherited
            ? ownToBuilder(toBuilderName, sourceClass)
            : "Set @ViaBuilder's 'build' to the method that finishes a '" + source + "'";
    Diagnostics.error(
        messager,
        method,
        "@ViaBuilder",
        "'" + buildName + "()' returns '" + builtName + "', not the source type '" + source + "'.",
        "The generated lens finishes with '"
            + buildName
            + "()' and hands its result back as the source type '"
            + source
            + "', which '"
            + builtName
            + "' is not."
            + inheritance,
        fix + ", or " + rebuildAnotherWay(sourceType, "@ViaBuilder") + ".");
    return new BuilderChain.Refused();
  }

  /**
   * Reports a {@code build()} that returns the source type only through an unchecked conversion,
   * and returns whether it did. Called on a raw builder, a method that returns the source type with
   * the builder's type arguments returns its erasure, so a chain that turns raw at any step can end
   * this way: at the builder {@code toBuilder()} hands back, at the one the setter hands back, or
   * at {@code build()} itself.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, which the generated lens hands the result back as
   * @param toBuilderName the method that hands back the builder
   * @param builder the builder it hands back, which the setter is called on
   * @param setterShown the setter the call binds, as a message names it
   * @param buildName the builder method that hands the source type back
   * @param set the builder the setter hands back, which {@code build()} is called on
   * @param built what {@code build()} returns there
   * @param builtShown the same, as the message names it
   * @return true when the result is unchecked, and an error was reported
   */
  private boolean buildsUnchecked(
      ExecutableElement method,
      DeclaredType sourceType,
      String toBuilderName,
      DeclaredType builder,
      String setterShown,
      String buildName,
      DeclaredType set,
      TypeMirror built,
      TypeMirror builtShown) {
    if (!ProcessorUtils.convertsUnchecked(typeUtils, built, sourceType)) {
      return false;
    }
    String source = ProcessorUtils.simpleTypeName(sourceType);
    // The step that turned the chain raw, and the attribute that names it.
    String rawStep;
    String attribute;
    if (!ProcessorUtils.erasesItsMembers(set)) {
      rawStep = null;
      attribute = "build";
    } else if (ProcessorUtils.erasesItsMembers(builder)) {
      rawStep = toBuilderName + "()";
      attribute = "toBuilder";
    } else {
      rawStep = setterShown;
      attribute = "setter";
    }
    Diagnostics.error(
        messager,
        method,
        "@ViaBuilder",
        "'"
            + buildName
            + "()' returns "
            + described(typeUtils, builtShown, sourceType)
            + ", which reaches the source type '"
            + source
            + "' only through an unchecked conversion.",
        "The generated lens hands what '"
            + buildName
            + "()' returns back as the source type"
            + (rawStep == null
                ? ""
                : ", and on the raw '"
                    + ProcessorUtils.simpleTypeName(set)
                    + "' that '"
                    + rawStep
                    + "' hands back, it returns the erasure of what it declares")
            + "; "
            + UNCHECKED_IN_GENERATED_CODE,
        "Point @ViaBuilder's '"
            + attribute
            + "' at "
            + (rawStep == null
                ? "a method that returns '" + source + "'"
                : "a method that hands back the builder with its type arguments")
            + ", or "
            + rebuildAnotherWay(sourceType, "@ViaBuilder")
            + ".");
    return true;
  }

  /**
   * Reports a builder step that hands back a type the generated class cannot name, and returns
   * whether it did: its members may all be public, and a call on a type out of reach is a compile
   * error in a file its author never wrote.
   */
  private boolean reportsUnnameableStep(
      ExecutableElement method,
      TypeElement step,
      String stepCall,
      DeclaredType sourceType,
      String targetPackage) {
    if (isVisibleFrom(step, targetPackage)) {
      return false;
    }
    Diagnostics.error(
        messager,
        method,
        "@ViaBuilder",
        "'"
            + step.getSimpleName()
            + "', which '"
            + stepCall
            + "' hands back, cannot be named from '"
            + targetPackage
            + "'.",
        "The generated lens rebuilds through that type, and a class it cannot see is a compile"
            + " error in a file its author never wrote.",
        "Make '"
            + step.getSimpleName()
            + "' public, or "
            + rebuildAnotherWay(sourceType, "@ViaBuilder")
            + ".");
    return true;
  }

  /** Reports a builder step that hands back something no further call can be made on. */
  private void reportBuilderStep(
      ExecutableElement method,
      String step,
      TypeMirror handedBack,
      String wanted,
      DeclaredType sourceType) {
    String handedBackName = ProcessorUtils.simpleTypeName(handedBack);
    Diagnostics.error(
        messager,
        method,
        "@ViaBuilder",
        "'" + step + "' hands back '" + handedBackName + "', which is not " + wanted + ".",
        "The generated lens rebuilds with"
            + " 'source.toBuilder().setter(newValue).build()', and each step is called on what the"
            + " step before it hands back.",
        "Name the builder methods this type's chain declares, or "
            + rebuildAnotherWay(sourceType, "@ViaBuilder")
            + ".");
  }

  /**
   * A builder step's type as a message names it where its declared reading is a wildcard, from what
   * it returns read on the type before it captured. The type capture makes of a wildcard has no
   * name a reader can write, so the step is named by what holds it: the builder it was called on,
   * as declared, where it hands that builder back, as a self-typed builder's setter does; else the
   * bound it reaches, where that names no type variable; else that bound's class alone.
   *
   * @param reached what the step returns on the type before it captured
   * @param receiver the type before it, as a message names it
   */
  private TypeMirror reachedName(TypeMirror reached, TypeMirror receiver) {
    if (typeUtils.isSameType(typeUtils.erasure(reached), typeUtils.erasure(receiver))) {
      return receiver;
    }
    // A bound naming more than one type is named by the first.
    return shownAlone(ProcessorUtils.typesReached(reached).getFirst());
  }

  /**
   * What {@code build()} returns, as a message names it: as it returns it on the setter's step as
   * declared, where that step has a declared name and the reading agrees with the call's on whether
   * the source type takes it. A declared reading the source type takes where the call's does not, a
   * {@code Box<List<?>>} for a {@code Box<List<CAP>>}, names a type the check did not find, and the
   * method's own declaration names it instead. A step with no declared name leaves nothing declared
   * to read {@code build()} on, and the call's reading is named as it can be.
   *
   * @param build the builder method that finishes the source type
   * @param built what it returns on the setter's step captured, as the call reads it
   * @param buildsSource whether the source type takes {@code built}
   * @param setDeclared the setter's step as declared, or null where it names nothing
   * @param setShown the setter's step as a message names it
   * @param sourceType the source type the lens hands the result back as
   */
  private TypeMirror builtShown(
      ExecutableElement build,
      TypeMirror built,
      boolean buildsSource,
      TypeMirror setDeclared,
      TypeMirror setShown,
      DeclaredType sourceType) {
    if (setDeclared == null) {
      return reachedName(built, setShown);
    }
    TypeMirror declared =
        unlessWildcard(ProcessorUtils.returnTypeIn(typeUtils, (DeclaredType) setDeclared, build));
    if (declared == null) {
      return reachedName(built, setShown);
    }
    return typeUtils.isAssignable(declared, sourceType) == buildsSource
        ? declared
        : build.getReturnType();
  }

  /**
   * Reports a {@code @ViaConstructor} parameter order that leaves out the lens's own name, or names
   * an accessor the source type does not have, and returns whether it did.
   *
   * <p>Every name but the lens's own reads an argument, {@code source.x()}, for the constructor
   * call the lens rebuilds with.
   *
   * @param rebuild the lens and what it rebuilds
   * @param written the names the annotation carries, empty where it carries none
   * @return true when the order cannot be read, and an error was reported
   */
  private boolean rebuildsThroughUnreadableParameter(
      ConstructorOrderChecks.Rebuild rebuild, String[] written) {
    if (constructorOrders.leavesOutTheLens(rebuild, List.of(written))) {
      return true;
    }
    for (String parameter : written) {
      if (parameter.equals(rebuild.fieldName())
          || accessorNamed(rebuild.members(), parameter) != null) {
        continue;
      }
      reportMissingAccessor(
          rebuild.method(),
          "@ViaConstructor",
          rebuild.source(),
          (TypeElement) rebuild.source().asElement(),
          parameter,
          "read the argument '" + parameter + "'",
          "'source'",
          "Name in @ViaConstructor's 'parameterOrder' the accessors '"
              + rebuild.sourceName()
              + "' declares, in the order its constructor takes them.",
          Optional.empty(),
          rebuild.targetPackage());
      return true;
    }
    return false;
  }

  /**
   * Parameter types as their author wrote them, for a diagnostic: the last of a variable-arity
   * method or constructor, an array to the compiler, as {@code T...}.
   */
  static List<String> parametersAsWritten(
      List<? extends TypeMirror> parameters, boolean variableArity) {
    return IntStream.range(0, parameters.size())
        .mapToObj(
            at -> {
              String name = ProcessorUtils.simpleTypeName(parameters.get(at));
              return variableArity && at == parameters.size() - 1
                  ? name.substring(0, name.length() - "[]".length()) + "..."
                  : name;
            })
        .toList();
  }

  /**
   * Reports a {@code @Wither} whose generated call does not bind a method that rebuilds the source
   * type, and returns whether it did.
   *
   * <p>The generated lens sets through {@code source.withX(newValue)}, with {@code newValue} typed
   * by the lens's focus, and javac picks among the methods of that name by that type. So it is the
   * method the call binds, as {@link CallBinding} repeats javac's choice, that is checked, not any
   * method that carries the name. The call has to bind one method, an instance method, since a
   * static one never reads the value it is called on, and that method has to hand back the source
   * type. Every method of the name that the generated class can reach is weighed, of any arity,
   * static or not, as javac weighs them.
   *
   * <p>Where the choice rests on inference that is not repeated, the candidates are read leniently:
   * the name passes when one of them is an instance method that hands back the source type, and
   * javac settles the rest at the generated call. A wildcard focus is read that way too, as far as
   * javac's choice rests on it: the one method of the name that takes one argument, or of several,
   * the one {@link #boundThroughTheGetter} says, is checked only for an unchecked call.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param focusType the lens's focus type, which the wither is called with
   * @param witherName the method the annotation names
   * @param targetPackage the package the optics class is generated into
   * @return true when the call binds no such method, and an error was reported
   */
  private boolean rebuildsThroughUnusableWither(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String getterName,
      TypeMirror focusType,
      String witherName,
      String targetPackage) {
    List<ExecutableElement> members =
        ElementFilter.methodsIn(elementUtils.getAllMembers(sourceTypeElement));
    List<ExecutableElement> callable =
        members.stream().filter(member -> callableOn(member, targetPackage)).toList();
    List<ExecutableElement> named =
        callable.stream()
            .filter(member -> member.getSimpleName().contentEquals(witherName))
            .toList();
    if (named.isEmpty()) {
      boolean declared =
          members.stream().anyMatch(member -> member.getSimpleName().contentEquals(witherName));
      reportMissingWither(
          method, sourceType, callable, focusType, witherName, targetPackage, declared);
      return true;
    }
    // A wildcard focus is inferred from the getter's type as much as from the wildcard itself, so
    // the type the call passes is not the one the spec writes, and the checks below that rest on
    // it are left to javac.
    if (focusType.getKind() == TypeKind.WILDCARD) {
      List<ExecutableElement> oneParameter =
          named.stream().filter(candidate -> candidate.getParameters().size() == 1).toList();
      // Where the name carries only one such method, that is the one called; of several, the
      // one boundThroughTheGetter says, where it says one. Whether a call to it is unchecked rests
      // on the call alone.
      Optional<ExecutableElement> called =
          oneParameter.size() == 1
              ? Optional.of(oneParameter.getFirst())
              : boundThroughTheGetter(
                  (WildcardType) focusType,
                  sourceType,
                  named,
                  sourceType,
                  getterName,
                  targetPackage);
      return noCandidateRebuilds(method, sourceType, oneParameter, null)
          || called
              .map(
                  bound ->
                      setsUnchecked(
                          method,
                          "@Wither",
                          Receiver.OnSource.of(sourceType),
                          bound,
                          focusType,
                          "'source." + witherName + "(newValue)'",
                          sourceType))
              .orElse(false);
    }
    return switch (CallBinding.resolve(typeUtils, sourceType, List.of(focusType), named)) {
      case CallBinding.Binds(ExecutableElement bound) -> {
        if (bound.getModifiers().contains(Modifier.STATIC)) {
          reportStaticWither(method, sourceType, bound, focusType);
          yield true;
        }
        if (ProcessorUtils.returnsOwner(typeUtils, sourceType, bound)) {
          yield setsUnchecked(
              method,
              "@Wither",
              Receiver.OnSource.of(sourceType),
              bound,
              focusType,
              "'source." + witherName + "(newValue)'",
              sourceType);
        }
        // Only an overloaded name needs the focus to say which method it binds.
        reportWitherOfAnotherType(method, sourceType, bound, named.size() > 1 ? focusType : null);
        yield true;
      }
      case CallBinding.NoneApplies() -> {
        reportInapplicableWither(method, sourceType, getterName, focusType, named, callable);
        yield true;
      }
      case CallBinding.Ambiguous(List<ExecutableElement> methods) -> {
        reportAmbiguousWither(method, sourceType, focusType, methods);
        yield true;
      }
      case CallBinding.Undecided(List<ExecutableElement> candidates) ->
          noCandidateRebuilds(method, sourceType, candidates, focusType);
    };
  }

  /**
   * Reports a name none of whose candidates rebuilds the source type, and returns whether it did.
   *
   * <p>This is the lenient reading, for a call whose binding rests on inference the checks do not
   * repeat. Whichever candidate javac settles on has to be an instance method that hands the source
   * type back, so the name passes as soon as one of them is, and is refused when none is: a static
   * method never reads the value it is called on, and another type does not compile where the lens
   * hands its result back as the source type.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param candidates the methods the call might bind; an empty list is left alone, since the call
   *     then binds nothing these checks can read and javac reports it
   * @param choosing the focus that chose among them, or null where no one type did
   * @return true when none of them rebuilds the source type, and an error was reported
   */
  private boolean noCandidateRebuilds(
      ExecutableElement method,
      DeclaredType sourceType,
      List<ExecutableElement> candidates,
      TypeMirror choosing) {
    if (candidates.isEmpty()) {
      return false;
    }
    List<ExecutableElement> rebuilding =
        candidates.stream()
            .filter(candidate -> ProcessorUtils.returnsOwner(typeUtils, sourceType, candidate))
            .toList();
    if (rebuilding.isEmpty()) {
      reportWitherOfAnotherType(method, sourceType, candidates.getFirst(), null);
      return true;
    }
    if (rebuilding.stream()
        .anyMatch(candidate -> !candidate.getModifiers().contains(Modifier.STATIC))) {
      return false;
    }
    reportStaticWither(method, sourceType, rebuilding.getFirst(), choosing);
    return true;
  }

  /**
   * Whether the generated class can call a member on the source type, a method or a constructor.
   *
   * <p>The call names no type but the source's own, so it is the member's own access that decides,
   * not where it was declared: a {@code public} method a package-private class declares is called
   * through the public type that inherits it, as javac calls it. {@code protected} counts as
   * package access, since a generated companion extends nothing.
   */
  private boolean callableOn(ExecutableElement member, String targetPackage) {
    Set<Modifier> modifiers = member.getModifiers();
    if (modifiers.contains(Modifier.PRIVATE)) {
      return false;
    }
    return modifiers.contains(Modifier.PUBLIC)
        || elementUtils.getPackageOf(member).getQualifiedName().contentEquals(targetPackage);
  }

  /**
   * Reports a {@code @Wither} naming no method the generated class can call, offering the source
   * type's withers: its one-parameter instance methods that hand it back.
   */
  private void reportMissingWither(
      ExecutableElement method,
      DeclaredType sourceType,
      List<ExecutableElement> callable,
      TypeMirror focusType,
      String witherName,
      String targetPackage,
      boolean declared) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    // A deprecated wither is no remedy, since the generated file's call to one warns.
    TypeElement sourceElement = (TypeElement) sourceType.asElement();
    List<ExecutableElement> anyWithers = withersAmong(sourceType, callable);
    List<ExecutableElement> withers =
        anyWithers.stream().filter(wither -> offerable(wither, sourceElement)).toList();
    String sets =
        "The generated lens sets through 'source."
            + witherName
            + "(newValue)', so it needs a method of that name "
            + (declared
                ? "the generated class in '"
                    + targetPackage
                    + "' can call, and '"
                    + witherName
                    + "' is declared where it cannot."
                : "on '" + source + "', declared or inherited.");
    if (withers.isEmpty()) {
      Diagnostics.error(
          messager,
          method,
          "@Wither",
          "'" + source + "' has no method '" + witherName + "' for the generated lens to call.",
          sets
              + (anyWithers.isEmpty()
                  ? " No one-parameter instance method of '" + source + "' hands it back."
                  : " Each one-parameter instance method of '"
                      + source
                      + "' that hands it back is deprecated, and the generated file's call to one"
                      + " would warn."),
          ProcessorUtils.capitalise(rebuildAnotherWay(sourceType, "@Wither")) + ".");
      return;
    }
    List<String> signatures =
        withers.stream()
            .map(member -> signatureOn(sourceType, member))
            .distinct()
            .sorted()
            .toList();
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "'" + source + "' has no method '" + witherName + "' for the generated lens to call.",
        sets
            + didYouMean(
                NamingConvention.WITHER.nearest(witherName, witherNames(sourceType, withers)))
            + " Withers found on '"
            + source
            + "': "
            + signatures
            + ".",
        "Name one of the withers found on '"
            + source
            + "' that takes the value '"
            + ProcessorUtils.simpleTypeName(focusType)
            + "' the getter reads, or "
            + rebuildAnotherWay(sourceType, "@Wither")
            + ".");
  }

  /**
   * Reports a {@code @Wither} none of whose methods takes the value the lens sets, offering a
   * wither from {@code callable}, as {@link #witherOffer} says.
   */
  private void reportInapplicableWither(
      ExecutableElement method,
      DeclaredType sourceType,
      String getterName,
      TypeMirror focusType,
      List<ExecutableElement> named,
      List<ExecutableElement> callable) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String witherName = named.getFirst().getSimpleName().toString();
    // A parameter typed by one of the source's wildcards takes no value whatever the focus, so the
    // remedy there is the source type, not the focus, nor another strategy, whose methods name the
    // type alike; such a parameter is listed as declared, naming what the wildcard stands for.
    DeclaredType captured = (DeclaredType) typeUtils.capture(sourceType);
    Receiver on = Receiver.OnSource.of(sourceType);
    boolean wildcardStandsIn =
        standInNamed(
                on,
                captured,
                named.stream()
                    .filter(member -> member.getParameters().size() == 1)
                    .map(member -> ProcessorUtils.firstParameterTypeIn(typeUtils, captured, member))
                    .toList())
            .isPresent();
    DeclaredType listed = wildcardStandsIn ? on.named() : sourceType;
    String listedName = ProcessorUtils.simpleTypeName(listed);
    String found =
        settingThrough(witherName, focusType)
            + ". Found on '"
            + listedName
            + "': "
            + named.stream().map(member -> signatureOn(listed, member)).toList()
            + ".";
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "No method '"
            + witherName
            + "' of '"
            + listedName
            + "' takes the lens's focus type '"
            + ProcessorUtils.simpleTypeName(focusType)
            + "'.",
        wildcardStandsIn
            ? found
                + " A parameter that a wildcard of '"
                + source
                + "' stands in takes no value at all, since the type it stands for is unknown."
            : found + witherOffer(named, witherName, sourceType, callable),
        (wildcardStandsIn
                ? declareTheSpecOver(sourceType, witherName)
                : narrowerFocus(sourceType, getterName, focusType, sourceType, named)
                        .orElse(
                            "Name a wither that takes the value the getter reads, or point"
                                + " 'getter' at an accessor one of them takes and declare the"
                                + " focus as its type")
                    + "; otherwise "
                    + rebuildAnotherWay(sourceType, "@Wither"))
            + ".");
  }

  /** Reports a {@code @Wither} whose call takes more than one method equally well. */
  private void reportAmbiguousWither(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeMirror argument,
      List<ExecutableElement> methods) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String witherName = methods.getFirst().getSimpleName().toString();
    List<String> signatures =
        methods.stream().map(member -> "'" + signatureAsDeclared(member) + "'").toList();
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "The generated call to '"
            + witherName
            + "' cannot choose between "
            + listed(signatures)
            + ".",
        settingThrough(witherName, argument)
            + ", which each of them takes, with no parameter more specific than every other.",
        ambiguityFix(sourceType, methods, sourceType, "@Wither"));
  }

  /**
   * The fix for a one-argument call javac cannot choose for: a focus of the type the one meant
   * takes, or, where the methods take one type under the source's instantiation, as {@code
   * withX(T)} and {@code withX(String)} do over a {@code W<String>}, another strategy, since no
   * focus can tell them apart.
   */
  private String ambiguityFix(
      DeclaredType owner, List<ExecutableElement> methods, DeclaredType source, String tag) {
    TypeMirror first = ProcessorUtils.firstParameterTypeIn(typeUtils, owner, methods.getFirst());
    boolean oneType =
        methods.stream()
            .allMatch(
                method ->
                    typeUtils.isSameType(
                        ProcessorUtils.firstParameterTypeIn(typeUtils, owner, method), first));
    return oneType
        ? ProcessorUtils.capitalise(rebuildAnotherWay(source, tag)) + "."
        : "Declare the lens's focus as the parameter type of the one you mean, or "
            + rebuildAnotherWay(source, tag)
            + ".";
  }

  /**
   * Reports a {@code @Wither} whose call binds a static method: a static method never reads the
   * value it is called on, so it cannot rebuild it.
   */
  private void reportStaticWither(
      ExecutableElement method,
      DeclaredType sourceType,
      ExecutableElement bound,
      TypeMirror choosing) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String signature = signatureOn(sourceType, bound);
    String sets =
        choosing == null
            ? "The generated lens sets through '" + signature + "'"
            : settingThrough(bound.getSimpleName().toString(), choosing)
                + ", which binds '"
                + signature
                + "'";
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "'"
            + signature
            + "' is static, so the generated lens cannot rebuild a '"
            + source
            + "' through it.",
        sets + ", and a static method never reads the '" + source + "' it is called on.",
        "Declare the lens's focus as the parameter type of an instance overload, name an instance"
            + " wither, or "
            + rebuildAnotherWay(sourceType, "@Wither")
            + ".");
  }

  /**
   * Reports a {@code @Wither} whose call binds a method that hands back something other than the
   * source type.
   *
   * <p>The generated set function returns what the wither returns, as the source type, so the
   * wither has to hand back that type as {@link ProcessorUtils#returnsOwner} reads it. A supertype,
   * or the type under other arguments ({@code Draft<String>} read on a {@code Draft<T>}), does not
   * compile there, and a raw return is an unchecked conversion in a file the author cannot edit.
   * The wither is read on the source type as the spec names it, so {@code Draft<String>
   * withId(String)} serves an {@code OpticsSpec<Draft<String>>} as it should.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param bound the method the call binds
   * @param choosing the argument that chose {@code bound} among overloads of its name, or null when
   *     it has none
   */
  private void reportWitherOfAnotherType(
      ExecutableElement method,
      DeclaredType sourceType,
      ExecutableElement bound,
      TypeMirror choosing) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String signature = signatureOn(sourceType, bound);
    String returned =
        ProcessorUtils.simpleTypeName(ProcessorUtils.returnTypeIn(typeUtils, sourceType, bound));
    String sets =
        choosing == null
            ? "The generated lens sets through '" + signature + "'"
            : settingThrough(bound.getSimpleName().toString(), choosing)
                + ", which binds '"
                + signature
                + "',";
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "'" + signature + "' returns '" + returned + "', not the source type '" + source + "'.",
        sets
            + " and hands its result back as the source type '"
            + source
            + "', which '"
            + returned
            + "' is not.",
        "Name a wither that returns '"
            + source
            + "', or declare the spec over the type the wither does return when that is an"
            + " instantiation of the same class; otherwise "
            // @ViaCopyAndSet drops a wither's result, so only a setter that sets the copy serves.
            + (!constructorUnwritable(sourceType)
                ? "rebuild '"
                    + source
                    + "' with @ViaBuilder or @ViaConstructor, or with @ViaCopyAndSet through a"
                    + " setter that sets the copy it is called on"
                : rebuildAnotherWay(sourceType, "@Wither"))
            + ".");
  }

  /**
   * The remedy a diagnostic's fix ends on, offering every strategy but {@code tag}: an unfinished
   * clause.
   */
  static String rebuildWith(String source, String tag) {
    return rebuildWith(
        source, tag, List.of("@Wither", "@ViaBuilder", "@ViaConstructor", "@ViaCopyAndSet"));
  }

  /**
   * The remedy that rebuilds {@code source} with another of {@code strategies} than {@code tag}, as
   * an unfinished clause.
   */
  private static String rebuildWith(String source, String tag, List<String> strategies) {
    List<String> others = strategies.stream().filter(strategy -> !strategy.equals(tag)).toList();
    return "rebuild '"
        + source
        + "' with "
        + (others.size() == 1
            ? others.getFirst()
            : String.join(", ", others.subList(0, others.size() - 1)) + " or " + others.getLast());
  }

  /** Names as a sentence lists them: {@code 'a()'}, {@code 'a()' and 'b()'}, or with commas. */
  static String listed(List<String> names) {
    return names.size() == 1
        ? names.getFirst()
        : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
  }

  /**
   * That a class inherits its {@code toBuilder()}, as the start of a sentence: {@code 'Dog'
   * inherits ...}.
   */
  private static String inheritsToBuilder(
      String sourceClass, String toBuilderName, String parentClass) {
    return "'" + sourceClass + "' inherits '" + toBuilderName + "()' from '" + parentClass + "'";
  }

  /**
   * The remedy for a {@code toBuilder()} a class inherits, as an unfinished clause: declaring one
   * of its own, which Lombok's {@code @SuperBuilder} writes for a class that asks for it.
   */
  private static String ownToBuilder(String toBuilderName, String sourceClass) {
    return "Declare a '"
        + toBuilderName
        + "()' on '"
        + sourceClass
        + "' that hands back its own builder (for a Lombok @SuperBuilder, set toBuilder = true on '"
        + sourceClass
        + "' too)";
  }

  /** How the generated setter calls the wither, for a diagnostic's reason: an unfinished clause. */
  private static String settingThrough(String witherName, TypeMirror argument) {
    return "The generated lens sets through 'source."
        + witherName
        + "(newValue)' with the new value typed '"
        + ProcessorUtils.simpleTypeName(argument)
        + "'";
  }

  /** A method as the source type sees it, {@code withId(String)}, telling overloads apart. */
  private String signatureOn(DeclaredType sourceType, ExecutableElement member) {
    List<String> names =
        parametersAsWritten(
            ProcessorUtils.memberOf(typeUtils, sourceType, member).getParameterTypes(),
            member.isVarArgs());
    return member.getSimpleName()
        + String.join(", ", names).transform(joined -> "(" + joined + ")");
  }

  /**
   * A method as its type declares it, {@code withX(T)}, for the overloads a call cannot choose
   * between: two that one instantiation makes alike, {@code withX(T)} and {@code withX(String)}
   * over a {@code W<String>}, still read apart.
   */
  private static String signatureAsDeclared(ExecutableElement member) {
    return member.getSimpleName()
        + String.join(
                ", ",
                parametersAsWritten(
                    member.getParameters().stream().map(VariableElement::asType).toList(),
                    member.isVarArgs()))
            .transform(joined -> "(" + joined + ")");
  }

  /**
   * Reports a source type whose own constructor call cannot be written, and returns whether it did.
   *
   * <p>A strategy that rebuilds through a constructor emits {@code new S(...)}, and a wildcard
   * cannot be written as a type argument there: {@code new Node<?>(...)} is not Java, whatever the
   * arguments. Nor can an abstract class or an interface be instantiated, whatever constructors it
   * declares. The strategies that rebuild through a wither or a builder name no constructor and are
   * unaffected, so this is asked per strategy rather than of the source type as a whole.
   *
   * <p>Only the outermost arguments matter. A wildcard nested inside one, {@code Node<List<?>>},
   * writes perfectly well.
   *
   * @param method the annotated optic method, for error reporting
   * @param declared the source type {@code S}
   * @param annotation the strategy annotation tag, for the diagnostic
   * @return true when the source type was rejected and an error reported
   */
  private boolean rebuildsThroughUnwritableConstructor(
      ExecutableElement method, DeclaredType declared, String annotation) {
    boolean wildcard = writtenWithAWildcard(declared);
    TypeElement element = (TypeElement) declared.asElement();
    boolean innerClass = innerClass(element);
    boolean abstractType = abstractType(element);
    String name = ProcessorUtils.simpleTypeName(declared);
    String withWither =
        "use @Wither or @ViaBuilder, which rebuild through methods and need no constructor.";
    if (wildcard) {
      reportUnwritableConstructor(
          method,
          annotation,
          name,
          "written with a wildcard type argument",
          "a wildcard: a constructor call has to name the type argument.",
          "Name the type the wildcard stands for, or " + withWither);
    } else if (innerClass) {
      reportUnwritableConstructor(
          method,
          annotation,
          name,
          "an inner class",
          "an inner class: the call needs an enclosing instance the generated class has no way"
              + " to reach.",
          "Declare the source type static, or " + withWither);
    } else if (abstractType) {
      reportUnwritableConstructor(
          method,
          annotation,
          name,
          element.getKind().isInterface() ? "an interface" : "an abstract class",
          "a type that cannot be instantiated.",
          "Name a concrete class as the spec's source type, or " + withWither);
    }
    return wildcard || innerClass || abstractType;
  }

  /**
   * Whether no constructor call can rebuild a source type: one written with a wildcard type
   * argument, which the call would have to name; an inner class, which needs an enclosing instance;
   * or an abstract class or an interface.
   */
  private static boolean constructorUnwritable(DeclaredType sourceType) {
    TypeElement element = (TypeElement) sourceType.asElement();
    return writtenWithAWildcard(sourceType) || innerClass(element) || abstractType(element);
  }

  /** Whether a type is written with a wildcard among its own type arguments. */
  private static boolean writtenWithAWildcard(DeclaredType type) {
    return type.getTypeArguments().stream()
        .anyMatch(argument -> argument.getKind() == TypeKind.WILDCARD);
  }

  /**
   * Whether a class carries an enclosing instance: only a member type that is not static does; a
   * nested interface, enum or record is implicitly static.
   */
  private static boolean innerClass(TypeElement element) {
    return element.getNestingKind() == NestingKind.MEMBER
        && !element.getModifiers().contains(Modifier.STATIC);
  }

  /** Whether a type cannot be instantiated: an interface is abstract too, as javac reads it. */
  private static boolean abstractType(TypeElement element) {
    return element.getModifiers().contains(Modifier.ABSTRACT);
  }

  /** Reports a source type whose own constructor call cannot be written, for the reason given. */
  private void reportUnwritableConstructor(
      ExecutableElement method,
      String annotation,
      String name,
      String whatItIs,
      String writtenFor,
      String fix) {
    Diagnostics.error(
        messager,
        method,
        annotation,
        "'" + name + "' is " + whatItIs + ", and this strategy rebuilds it through a constructor.",
        "The generated set function calls 'new "
            + name
            + "(...)', which is not something that can be written for "
            + writtenFor,
        fix);
  }

  /**
   * The primitive type the method a strategy writes through takes the focus as, or null to pass
   * {@code newValue} unchanged: the wither of {@code @Wither}, the setter of
   * {@code @ViaCopyAndSet}, both called on the source type. Overloaded at one parameter, they
   * choose among themselves exactly as constructors do, so {@link #constructorFocusType}'s rule
   * applies unchanged.
   *
   * @param method the annotated optic method
   * @param receiver the type the call is made on
   * @param receiverElement that type's element
   * @param getter the getter named by the strategy, or empty to read the optic method's own name
   * @param written the name of the method the focus is passed to
   * @param focusType the lens's focus
   * @param targetPackage the package the optics class is generated into, whose reach decides which
   *     methods the call weighs
   * @return the primitive type to unbox the focus to, or null
   */
  private TypeMirror writtenFocusType(
      ExecutableElement method,
      DeclaredType receiver,
      TypeElement receiverElement,
      String getter,
      String written,
      TypeMirror focusType,
      String targetPackage) {
    return Optional.ofNullable(primitiveRead(method, receiver, receiverElement, getter, focusType))
        .map(read -> unboxedFocus(read, writtenParameters(receiverElement, written, targetPackage)))
        .orElse(null);
  }

  /**
   * The primitive type {@code @ViaBuilder}'s setter takes the focus as, or null to pass {@code
   * newValue} unchanged. The setter is called on the builder {@code toBuilder()} hands back, not on
   * the source, so the candidates are read there; a {@code toBuilder} that names no method of the
   * source leaves the call to javac, and nothing is unboxed.
   *
   * @param method the annotated optic method
   * @param source the source type {@code S}
   * @param sourceElement the source type's element
   * @param getter the getter the strategy names, or empty to read the optic method's own name
   * @param toBuilder the name of the method handing back the builder
   * @param setter the builder's setter, or empty to read the optic method's own name
   * @param focusType the lens's focus
   * @param targetPackage the package the optics class is generated into, whose reach decides which
   *     methods the call weighs
   * @return the primitive type to unbox the focus to, or null
   */
  private TypeMirror builderFocusType(
      ExecutableElement method,
      DeclaredType source,
      TypeElement sourceElement,
      String getter,
      String toBuilder,
      String setter,
      TypeMirror focusType,
      String targetPackage) {
    String named = setter.isEmpty() ? method.getSimpleName().toString() : setter;
    return Optional.ofNullable(primitiveRead(method, source, sourceElement, getter, focusType))
        .flatMap(
            read ->
                builderOf(source, sourceElement, toBuilder, named, targetPackage)
                    .map(
                        builder ->
                            unboxedFocus(read, writtenParameters(builder, named, targetPackage))))
        .orElse(null);
  }

  /**
   * The builder {@code toBuilder} hands back, the one the setter is called on, read as the chain
   * reads it; or empty where the source declares no such method, or one handing back something with
   * no members to call, or bounded by several types whose setters javac weighs together. Either way
   * nothing is unboxed, and the chain's own checks report what they refuse.
   */
  private Optional<TypeElement> builderOf(
      DeclaredType source,
      TypeElement sourceElement,
      String toBuilder,
      String setter,
      String targetPackage) {
    return Optional.ofNullable(
            accessorNamed(callableMembers(sourceElement, targetPackage), toBuilder))
        .map(
            method ->
                ProcessorUtils.returnTypeIn(
                    typeUtils, (DeclaredType) typeUtils.capture(source), method))
        .map(builder -> stepTypeFor(builder, setter, targetPackage))
        .map(this::elementOf);
  }

  /**
   * The type the lens reads its focus through, when that is a primitive the focus unboxes to: the
   * zero-argument method the strategy names, or the optic method's own name where it names none.
   * Null for a getter that hands back a reference type, which the call takes as it is, and for one
   * the source does not declare, which javac reports at the generated call.
   *
   * <p>Null too for a focus wider than the primitive's wrapper, {@code Number} over an {@code int}
   * getter: a cast to {@code int} would compile there and throw for a value of another kind, so the
   * focus is passed as it is and the call is held to it.
   */
  private TypeMirror primitiveRead(
      ExecutableElement method,
      DeclaredType receiver,
      TypeElement receiverElement,
      String getter,
      TypeMirror focusType) {
    String named = getter.isEmpty() ? method.getSimpleName().toString() : getter;
    return zeroArgumentMethods(receiver, receiverElement, named)
        .filter(type -> type.getKind().isPrimitive())
        .filter(read -> unboxesTo(focusType, read))
        .orElse(null);
  }

  /**
   * Whether the value a lens sets unboxes to {@code read}: the focus itself, or for a focus written
   * as a wildcard, its lower bound. javac infers {@code ?} and {@code ? extends Number} as the
   * getter's own wrapper, which always does; {@code ? super Number} as no narrower than {@code
   * Number}, which does not.
   */
  private boolean unboxesTo(TypeMirror focusType, TypeMirror read) {
    TypeMirror passed =
        focusType.getKind() == TypeKind.WILDCARD
            ? ((WildcardType) focusType).getSuperBound()
            : focusType;
    return passed == null || typeUtils.isAssignable(passed, read);
  }

  /**
   * The remedy where the focus is wider than the wrapper of the primitive its getter reads, {@code
   * Number} over an {@code int} getter, and one of the methods the call might bind takes that
   * primitive. The value such a lens sets may be any {@code Number}, so it is passed as it is, and
   * only a focus of the wrapper itself is passed unboxed. An unfinished clause, or empty where the
   * focus is not wider so.
   */
  private Optional<String> narrowerFocus(
      DeclaredType source,
      String getterName,
      TypeMirror focusType,
      DeclaredType owner,
      List<ExecutableElement> candidates) {
    return zeroArgumentMethods(source, (TypeElement) source.asElement(), getterName)
        .filter(read -> read.getKind().isPrimitive() && !unboxesTo(focusType, read))
        .filter(
            read ->
                candidates.stream()
                    .anyMatch(
                        candidate ->
                            candidate.getParameters().size() == 1
                                && typeUtils.isAssignable(
                                    read,
                                    ProcessorUtils.firstParameterTypeIn(
                                        typeUtils, owner, candidate))))
        .map(
            read ->
                declareFocusAs(asFocus(typeUtils, read), getterName)
                    + ", which the call then passes unboxed");
  }

  /**
   * A type as a lens's focus declares it: a primitive as its wrapper, since a focus is a type
   * argument, and anything else as it is.
   */
  static TypeMirror asFocus(Types types, TypeMirror read) {
    return read.getKind().isPrimitive() ? types.boxedClass((PrimitiveType) read).asType() : read;
  }

  /** The fix that declares the focus a getter reads, for a diagnostic: an unfinished clause. */
  static String declareFocusAs(TypeMirror focus, String getterName) {
    return "Declare the lens's focus as '"
        + ProcessorUtils.simpleTypeName(focus)
        + "', the type '"
        + getterName
        + "()' reads";
  }

  /** The type the zero-argument method {@code named} hands back, read on {@code receiver}. */
  private Optional<TypeMirror> zeroArgumentMethods(
      DeclaredType receiver, TypeElement receiverElement, String named) {
    return ElementFilter.methodsIn(elementUtils.getAllMembers(receiverElement)).stream()
        .filter(candidate -> candidate.getSimpleName().contentEquals(named))
        .filter(candidate -> candidate.getParameters().isEmpty())
        .map(candidate -> ProcessorUtils.memberTypeOf(typeUtils, receiver, candidate))
        .findFirst();
  }

  /**
   * The parameter types of the one-argument methods named {@code written} on {@code
   * receiverElement} that the generated class can call, the candidates a call passing one argument
   * chooses among, as {@link CallBinding} weighs them.
   */
  private List<TypeMirror> writtenParameters(
      TypeElement receiverElement, String written, String targetPackage) {
    return callableMembers(receiverElement, targetPackage).stream()
        .filter(candidate -> candidate.getSimpleName().contentEquals(written))
        .filter(candidate -> candidate.getParameters().size() == 1)
        .map(candidate -> candidate.getParameters().getFirst().asType())
        .toList();
  }

  /**
   * The primitive {@code read} is unboxed to where that cannot move the call, or null to pass the
   * focus boxed.
   *
   * <p>Unboxed, the focus has the type its getter hands back, and every other argument is a getter
   * read already, so the call binds exactly where the rebuild {@code new S(source.cents(),
   * source.owner())} binds: the constructor or method the strategy describes. The question is only
   * whether to write the cast at all, and the answer is whether one of the candidates takes exactly
   * {@code read}. That one is applicable by strict invocation, and it is the most specific of the
   * candidates that are: another primitive is either unreachable, because a narrowing conversion is
   * never applied to an argument, or wider than {@code read}, which makes {@code read}'s own the
   * more specific (JLS 15.12.2.5); a reference parameter needs boxing, which the first phase does
   * not do (JLS 15.12.2.2).
   *
   * <p>Where no candidate takes it, unboxing could move the call, as it would from {@code P(Long,
   * String)} to a {@code P(double, String)} the boxed focus never reached, so the focus is passed
   * as it is. A lone candidate is not overloaded at all and needs no cast to settle it.
   */
  private TypeMirror unboxedFocus(TypeMirror read, List<TypeMirror> candidates) {
    if (candidates.size() < 2) {
      return null;
    }
    boolean taken =
        candidates.stream().anyMatch(candidate -> typeUtils.isSameType(candidate, read));
    return taken ? typeUtils.getPrimitiveType(read.getKind()) : null;
  }

  /**
   * The primitive type {@code @ViaConstructor}'s constructor call passes the focus as, or null to
   * pass {@code newValue} unchanged.
   *
   * <p>The lens focuses a primitive boxed, and the first phase of overload resolution allows no
   * unboxing (JLS 15.12.2.2), so a {@code Long} can bind to a {@code (Number, String)} overload
   * before the {@code (long, String)} one is considered. The call means the constructor that {@code
   * new S(source.cents(), source.currency())} binds to, every argument read through its own getter,
   * so unboxing the focus to its getter's type leaves that same constructor the most specific one
   * applicable.
   *
   * <p>The candidates are the constructors of the call's own arity, read at the focus's place, and
   * {@link #unboxedFocus} decides among them; one that no other argument fits is among them
   * harmlessly, since it cannot take a call it is not applicable to. The order always passes the
   * focus somewhere: one that names the lens nowhere is refused before this is asked, and one read
   * from the parameter names always names it. Answering it here, with {@code Types}, leaves the
   * generator the one rule {@code @ViaCopyAndSet}'s cast follows: a null type means no cast.
   *
   * @param method the annotated optic method, named after its getter
   * @param source the source type {@code S}
   * @param sourceElement the source type's element
   * @param parameterOrder the getters the constructor call reads its arguments from, in order, the
   *     lens method's own name among them
   * @param focusType the lens's focus
   * @param targetPackage the package the optics class is generated into, whose reach decides which
   *     constructors the call weighs
   * @return the primitive type to unbox the focus to, or null
   */
  private TypeMirror constructorFocusType(
      ExecutableElement method,
      DeclaredType source,
      TypeElement sourceElement,
      List<String> parameterOrder,
      TypeMirror focusType,
      String targetPackage) {
    int focus = parameterOrder.indexOf(method.getSimpleName().toString());
    List<TypeMirror> candidates =
        callableConstructors(sourceElement, targetPackage).stream()
            .filter(constructor -> constructor.getParameters().size() == parameterOrder.size())
            .<TypeMirror>map(
                constructor ->
                    ProcessorUtils.memberOf(typeUtils, source, constructor)
                        .getParameterTypes()
                        .get(focus))
            .toList();
    return Optional.ofNullable(primitiveRead(method, source, sourceElement, "", focusType))
        .map(read -> unboxedFocus(read, candidates))
        .orElse(null);
  }

  /**
   * Resolves the type named by {@code @ViaCopyAndSet(copyConstructor = ...)} to the supertype of
   * {@code S} that the generated cast will name.
   *
   * <p>The attribute names the copy constructor's <em>parameter</em> type, so the emitted argument
   * is {@code (ParameterType) source}. Four things have to hold for that to compile, and each is
   * checked here rather than left to javac, which would report it inside a generated file the user
   * did not write: the name resolves, it names a supertype of {@code S}, the generated class is
   * allowed to name it, and {@code S} has a constructor that accepts it.
   *
   * <p>The supertype is returned as {@code S}'s own {@code extends}/{@code implements} clause
   * instantiates it, so a base declared {@code Holder<String>} is named with its argument rather
   * than raw. A clause that is itself raw is named raw, which is what the source says.
   *
   * <p>Naming {@code S} itself resolves to {@code S}; the generator then emits no cast, since a
   * cast to the argument's own type says nothing.
   *
   * @param method the annotated optic method, for error reporting
   * @param sourceType the source type {@code S}
   * @param targetPackage the package the optics class is generated into
   * @param copyConstructor the fully qualified name from the annotation; never empty
   * @return the resolved supertype, or empty if it was rejected (an error has been reported)
   */
  private Optional<TypeMirror> resolveCopyConstructorParameterType(
      ExecutableElement method,
      DeclaredType sourceType,
      String targetPackage,
      String copyConstructor) {

    TypeElement parameterElement = elementUtils.getTypeElement(copyConstructor);
    if (parameterElement == null) {
      Diagnostics.error(
          messager,
          method,
          "@ViaCopyAndSet",
          "copyConstructor names '" + copyConstructor + "', which does not resolve to a type.",
          "The attribute is a plain string, so it is not resolved against the spec interface's"
              + " imports, and it takes no type arguments.",
          "Give the copy constructor's parameter type as a fully qualified class name - a nested"
              + " class as 'com.example.Outer.Base', a generic base as the class alone - or drop"
              + " the attribute to pass '"
              + ProcessorUtils.qualifiedTypeName(sourceType)
              + "' unchanged.");
      return Optional.empty();
    }

    Optional<TypeMirror> supertype = resolveSupertype(method, sourceType, parameterElement);
    if (supertype.isEmpty()) {
      return Optional.empty();
    }

    if (!isVisibleFrom(parameterElement, targetPackage)) {
      Diagnostics.error(
          messager,
          method,
          "@ViaCopyAndSet",
          "copyConstructor names '"
              + parameterElement.getQualifiedName()
              + "', which is not public and so cannot be named from '"
              + targetPackage
              + "'.",
          "The generated optics class writes the cast as '("
              + parameterElement.getSimpleName()
              + ") source', so it has to be able to name the type; passing the source unchanged"
              + " never names it.",
          "Name a public supertype, generate into '"
              + elementUtils.getPackageOf(parameterElement).getQualifiedName()
              + "' with @ImportOptics(targetPackage = ...), or drop the attribute.");
      return Optional.empty();
    }

    // The cast that will be emitted, not the name the attribute gave: where S pins the supertype's
    // arguments the two differ, and only the emitted one explains the rejection.
    if (!hasConstructorAccepting(sourceType, supertype.get(), targetPackage)) {
      Diagnostics.error(
          messager,
          method,
          "@ViaCopyAndSet",
          "copyConstructor names '"
              + parameterElement.getQualifiedName()
              + "', which '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' reaches as '"
              + ProcessorUtils.simpleTypeName(supertype.get())
              + "', and no constructor accepts.",
          "The generated set function calls 'new "
              + ProcessorUtils.simpleTypeName(sourceType)
              + "(("
              + ProcessorUtils.simpleTypeName(supertype.get())
              + ") source)'. Found "
              + describeSingleArgumentConstructors(sourceType, targetPackage)
              + ".",
          "Name a supertype of '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' that one of those constructors takes, as the class alone without type"
              + " arguments, or drop the attribute to pass the source unchanged.");
      return Optional.empty();
    }

    return supertype;
  }

  /**
   * Whether a type's hierarchy holds a supertype that does not resolve, so the members it inherits
   * cannot all be read this round. Each supertype is walked once.
   */
  private boolean hierarchyUnresolved(TypeMirror type) {
    Deque<TypeMirror> pending = new ArrayDeque<>(List.of(type));
    Set<Element> walked = new HashSet<>();
    while (!pending.isEmpty()) {
      List<? extends TypeMirror> supertypes = typeUtils.directSupertypes(pending.pop());
      if (supertypes.stream().anyMatch(supertype -> supertype.getKind() == TypeKind.ERROR)) {
        return true;
      }
      supertypes.stream()
          .filter(supertype -> walked.add(typeUtils.asElement(supertype)))
          .forEach(pending::add);
    }
    return false;
  }

  /**
   * Finds the supertype relation the cast depends on, reporting when it does not hold.
   *
   * <p>A hierarchy javac cannot read, as a class file whose supertype is missing from the classpath
   * has, reads as having no supertypes at all, which would make every name look wrong. The compiler
   * is asked directly before any name is rejected, so an unreadable hierarchy costs the
   * instantiation rather than drawing an error that blames the attribute for a missing type javac
   * is already reporting.
   *
   * @param method the annotated optic method, for error reporting
   * @param sourceType the source type {@code S}
   * @param parameterElement the resolved element the attribute names
   * @return the supertype to name, or empty if it was rejected (an error has been reported)
   */
  private Optional<TypeMirror> resolveSupertype(
      ExecutableElement method, TypeMirror sourceType, TypeElement parameterElement) {

    TypeMirror walked = ProcessorUtils.supertypeOf(typeUtils, sourceType, parameterElement);
    if (walked != null) {
      return Optional.of(walked);
    }

    TypeMirror erased = typeUtils.erasure(parameterElement.asType());
    if (typeUtils.isAssignable(typeUtils.erasure(sourceType), erased)) {
      return Optional.of(erased);
    }

    Diagnostics.error(
        messager,
        method,
        "@ViaCopyAndSet",
        "copyConstructor names '"
            + parameterElement.getQualifiedName()
            + "', which '"
            + ProcessorUtils.qualifiedTypeName(sourceType)
            + "' does not extend or implement.",
        "The generated set function passes the source to the copy constructor as '("
            + parameterElement.getSimpleName()
            + ") source', and only a supertype of the source can be cast to there.",
        "Name a supertype of '"
            + ProcessorUtils.qualifiedTypeName(sourceType)
            + "', or drop the attribute to pass it unchanged.");
    return Optional.empty();
  }

  /**
   * Returns whether the generated class may name {@code type}.
   *
   * @param type the type the generated cast would name
   * @param targetPackage the package the optics class is generated into
   * @return true if {@code type} is public, or package-private in the generated class's own package
   */
  private boolean isVisibleFrom(TypeElement type, String targetPackage) {
    for (Element enclosing = type; enclosing instanceof TypeElement nested; ) {
      if (!nested.getModifiers().contains(Modifier.PUBLIC)) {
        return elementUtils.getPackageOf(type).getQualifiedName().contentEquals(targetPackage);
      }
      enclosing = nested.getEnclosingElement();
    }
    return true;
  }

  /**
   * Returns whether {@code sourceType} declares a constructor the generated class can call a single
   * {@code argument} through.
   *
   * @param sourceType the instantiated source type {@code S}, which the constructors are read under
   * @param argument the type the generated cast produces
   * @param targetPackage the package the optics class is generated into
   * @return true if some constructor it can reach accepts it
   */
  private boolean hasConstructorAccepting(
      DeclaredType sourceType, TypeMirror argument, String targetPackage) {
    // A constructor the generated class cannot call is no use, however well it fits.
    for (ExecutableElement constructor :
        callableConstructors((TypeElement) sourceType.asElement(), targetPackage)) {
      if (constructor.getParameters().size() != 1) {
        continue;
      }
      TypeMirror parameterType = constructorParameterType(sourceType, constructor);
      if (typeUtils.isAssignable(argument, parameterType)) {
        return true;
      }
      // A varargs parameter is always an array type, so the component is there to read.
      if (constructor.isVarArgs()
          && typeUtils.isAssignable(argument, ((ArrayType) parameterType).getComponentType())) {
        return true;
      }
    }
    return false;
  }

  /**
   * The constructor's one parameter as seen under the source type's instantiation.
   *
   * <p>Read off the constructor directly, the parameter speaks the source type's own declaration:
   * {@code Node<X>} declaring {@code Node(Base<X> other)} gives {@code Base<X>}. The argument it is
   * compared against comes from a supertype walk over the instantiated type, so it speaks the
   * spec's variables, {@code Base<U>}. Where the source type declares parameters of its own the two
   * can never match until one is rewritten in the other's terms; where it declares none, the
   * rewrite is a no-op and the declared parameter was already the answer.
   *
   * <p>Only the class's own variables are substituted. A constructor that declares parameters of
   * its own keeps them, and is left to be rejected.
   *
   * <p>Unlike {@link #memberTypeOf} this does not guard on {@link
   * ProcessorUtils#carriesInstantiation}, and needs no guard: a source type naming a raw type is
   * refused at the spec's declaration (#771) before any member is read, so every type reaching here
   * supplies its arguments or has none to supply. A guard would bury a fault the gate already
   * reports.
   *
   * @param sourceType the instantiated source type {@code S}
   * @param constructor a single-argument constructor, whose one parameter is read
   * @return the parameter type under {@code sourceType}'s instantiation
   */
  private TypeMirror constructorParameterType(
      DeclaredType sourceType, ExecutableElement constructor) {
    // Total: asMemberOf answers with an ExecutableType for an executable member, and the only
    // shape that would not - an unresolvable source type, whose members resolve to itself - never
    // reaches here, because such a type enumerates no constructors for the caller to loop over.
    ExecutableType asMember = (ExecutableType) typeUtils.asMemberOf(sourceType, constructor);
    return asMember.getParameterTypes().getFirst();
  }

  /**
   * Names the single-argument constructors the generated class can call, for the rejection message.
   *
   * <p>Only the reachable ones: naming a constructor the generated class cannot call would send the
   * reader after a type that fails the same way.
   *
   * @param sourceType the instantiated source type {@code S}, so the list names the parameters as
   *     it sees them
   * @param targetPackage the package the optics class is generated into
   * @return the parameter types it takes one at a time, or a phrase saying it takes none
   */
  private String describeSingleArgumentConstructors(DeclaredType sourceType, String targetPackage) {
    // The same instantiation the check uses, so a reader comparing the list against the name they
    // gave is comparing like with like. The names carry type arguments and the attribute does not,
    // so the list is there to be recognised rather than copied from.
    List<String> parameterTypes =
        callableConstructors((TypeElement) sourceType.asElement(), targetPackage).stream()
            .filter(constructor -> constructor.getParameters().size() == 1)
            .map(
                constructor ->
                    ProcessorUtils.simpleTypeName(
                        constructorParameterType(sourceType, constructor)))
            .toList();
    return parameterTypes.isEmpty()
        ? "no single-argument constructor it can call"
        : "single-argument constructors taking " + parameterTypes;
  }
}
