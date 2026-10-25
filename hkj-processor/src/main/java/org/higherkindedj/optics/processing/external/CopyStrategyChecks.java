// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.external.BuilderChainChecks.BuilderChain;
import org.higherkindedj.optics.processing.external.SetterChecks.Receiver;
import org.higherkindedj.optics.processing.external.SetterChecks.SetterCall;
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
 * still holds a read against an unchecked one.
 *
 * <p>This class reads each strategy's annotation and decides how the focus is passed. It makes
 * these checks itself: the accessor, the {@code @Wither}, whether the source type's own constructor
 * call can be written, the names a {@code @ViaConstructor} order reads, and a
 * {@code @ViaCopyAndSet}'s copy constructor and the result its setter drops. {@link CallBinding}
 * answers which method or constructor a generated call binds, {@link SetterChecks} whether a
 * setter's or a wither's call can take the value, {@link BuilderChainChecks} whether the generated
 * lens can walk a {@code @ViaBuilder} chain, and {@link ConstructorOrderChecks} what order a
 * {@code @ViaConstructor} rebuild passes its arguments in. These checks, CallBinding aside, read
 * the methods a generated class can call through {@link CallableMembers}, a lens's focus through
 * {@link LensFocus}, and word what their refusals share through {@link RefusalWording}.
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
  private final CallableMembers members;
  private final SetterChecks setters;
  private final BuilderChainChecks builderChains;

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
    this.members = new CallableMembers(typeUtils, elementUtils, messager);
    this.setters = new SetterChecks(typeUtils, elementUtils, messager, members);
    this.builderChains =
        new BuilderChainChecks(typeUtils, elementUtils, messager, members, setters);
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
      if (!(builderChains.chain(
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
              members.of(sourceTypeElement, targetPackage),
              members.constructorsOf(sourceTypeElement, targetPackage),
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
   * The wither offered for a name the type declares, as {@link SetterChecks} offers a setter for
   * one.
   */
  private String witherOffer(
      List<ExecutableElement> named,
      String witherName,
      DeclaredType sourceType,
      List<ExecutableElement> callable) {
    return CallableMembers.takesNoSingleArgument(named)
        ? CallableMembers.didYouMean(
            NamingConvention.WITHER.sameValueAmong(
                witherName, witherNames(sourceType, withersAmong(sourceType, callable))))
        : "";
  }

  /** The names of {@code withers} a diagnostic may offer: those that can take a value at all. */
  private List<String> witherNames(DeclaredType sourceType, List<ExecutableElement> withers) {
    return CallableMembers.namesOf(
        withers.stream()
            .filter(wither -> members.offerable(wither, (TypeElement) sourceType.asElement()))
            .filter(wither -> setters.takesTheValue(Receiver.OnSource.of(sourceType), wither))
            .toList());
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
    for (String accessor : members.accessorNames(element, targetPackage)) {
      for (ProtobufCollection kind : ProtobufCollection.values()) {
        if (accessor.startsWith("get") && accessor.endsWith(kind.getterSuffix())) {
          byField.put(
              accessor.substring("get".length(), accessor.length() - kind.getterSuffix().length()),
              accessor);
        }
      }
    }
    String field = ProcessorUtils.capitalise(NamingConvention.ACCESSOR.withoutPrefix(written));
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
        CallableMembers.accessorNamed(
            typeUtils, sourceType, members.of(sourceTypeElement, targetPackage), getterName);
    String ownerName = ProcessorUtils.simpleTypeName(sourceType);
    // @ViaCopyAndSet and @ViaConstructor read through the lens method's own name, and carry no
    // attribute that could point anywhere else.
    boolean namesItsGetter = tag.equals("@Wither") || tag.equals("@ViaBuilder");
    if (getter == null) {
      members.reportMissingAccessor(
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
    TypeMirror focus = LensFocus.focusRead(focusType);
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
            ? ProcessorUtils.simpleTypeName(LensFocus.asFocus(typeUtils, declaredRead))
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
            + RefusalWording.described(typeUtils, declaredRead, target)
            + ", which reaches the lens's focus '"
            + ProcessorUtils.simpleTypeName(focusType)
            + "' only through an unchecked conversion.",
        "The generated lens hands what 'source."
            + getterName
            + "()' reads back as its focus, and "
            + RefusalWording.UNCHECKED_IN_GENERATED_CODE,
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
                        RefusalWording.rawView(
                            typeUtils, (DeclaredType) declaredRead, (DeclaredType) target))
                    + "'"
                : "'" + ProcessorUtils.simpleTypeName(declaredRead) + "'")
            + ".");
    return true;
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
        setters.boundSetter(
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
            && setters.setsUnchecked(
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
    return members.of(sourceTypeElement, targetPackage).stream()
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
            + RefusalWording.listed(unkept)
            + ".",
        "Where '"
            + setterName
            + "' sets the copy it is called on, annotate '"
            + method.getSimpleName()
            + "()' @SuppressWarnings(\""
            + DROPPED_RESULT
            + "\"); otherwise "
            + (witherServes
                ? RefusalWording.rebuildWith(source, "@ViaCopyAndSet")
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
            + members.signatureOn(sourceType, member)
            + "' returns '"
            + ProcessorUtils.simpleTypeName(
                ProcessorUtils.returnTypeIn(typeUtils, sourceType, member))
            + "'";
    return ProcessorUtils.returnsOwner(typeUtils, sourceType, member)
        ? returned + ", whose type the call infers from more than what it is assigned to"
        : returned;
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
          || CallableMembers.accessorNamed(
                  typeUtils, rebuild.source(), rebuild.members(), parameter)
              != null) {
        continue;
      }
      members.reportMissingAccessor(
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
   * the one {@link SetterChecks#boundThroughTheGetter} says, is checked only for an unchecked call.
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
    List<ExecutableElement> all =
        ElementFilter.methodsIn(elementUtils.getAllMembers(sourceTypeElement));
    List<ExecutableElement> callable =
        all.stream().filter(member -> members.callable(member, targetPackage)).toList();
    List<ExecutableElement> named =
        callable.stream()
            .filter(member -> member.getSimpleName().contentEquals(witherName))
            .toList();
    if (named.isEmpty()) {
      boolean declared =
          all.stream().anyMatch(member -> member.getSimpleName().contentEquals(witherName));
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
              : setters.boundThroughTheGetter(
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
                      setters.setsUnchecked(
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
          yield setters.setsUnchecked(
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
        anyWithers.stream().filter(wither -> members.offerable(wither, sourceElement)).toList();
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
          ProcessorUtils.capitalise(RefusalWording.rebuildAnotherWay(sourceType, "@Wither")) + ".");
      return;
    }
    List<String> signatures =
        withers.stream()
            .map(member -> members.signatureOn(sourceType, member))
            .distinct()
            .sorted()
            .toList();
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "'" + source + "' has no method '" + witherName + "' for the generated lens to call.",
        sets
            + CallableMembers.didYouMean(
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
            + RefusalWording.rebuildAnotherWay(sourceType, "@Wither")
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
        setters
            .standInNamed(
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
            + named.stream().map(member -> members.signatureOn(listed, member)).toList()
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
                ? setters.declareTheSpecOver(sourceType, witherName)
                : setters
                        .narrowerFocus(sourceType, getterName, focusType, sourceType, named)
                        .orElse(
                            "Name a wither that takes the value the getter reads, or point"
                                + " 'getter' at an accessor one of them takes and declare the"
                                + " focus as its type")
                    + "; otherwise "
                    + RefusalWording.rebuildAnotherWay(sourceType, "@Wither"))
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
        methods.stream()
            .map(member -> "'" + CallableMembers.signatureAsDeclared(member) + "'")
            .toList();
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "The generated call to '"
            + witherName
            + "' cannot choose between "
            + RefusalWording.listed(signatures)
            + ".",
        settingThrough(witherName, argument)
            + ", which each of them takes, with no parameter more specific than every other.",
        setters.ambiguityFix(sourceType, methods, sourceType, "@Wither"));
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
    String signature = members.signatureOn(sourceType, bound);
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
            + RefusalWording.rebuildAnotherWay(sourceType, "@Wither")
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
    String signature = members.signatureOn(sourceType, bound);
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
            + (!CallableMembers.constructorUnwritable(sourceType)
                ? "rebuild '"
                    + source
                    + "' with @ViaBuilder or @ViaConstructor, or with @ViaCopyAndSet through a"
                    + " setter that sets the copy it is called on"
                : RefusalWording.rebuildAnotherWay(sourceType, "@Wither"))
            + ".");
  }

  /** How the generated setter calls the wither, for a diagnostic's reason: an unfinished clause. */
  private static String settingThrough(String witherName, TypeMirror argument) {
    return "The generated lens sets through 'source."
        + witherName
        + "(newValue)' with the new value typed '"
        + ProcessorUtils.simpleTypeName(argument)
        + "'";
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
    boolean wildcard = CallableMembers.writtenWithAWildcard(declared);
    TypeElement element = (TypeElement) declared.asElement();
    boolean innerClass = CallableMembers.innerClass(element);
    boolean abstractType = CallableMembers.abstractType(element);
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
                builderChains
                    .builderOf(source, sourceElement, toBuilder, named, targetPackage)
                    .map(
                        builder ->
                            unboxedFocus(read, writtenParameters(builder, named, targetPackage))))
        .orElse(null);
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
    return members
        .zeroArgumentReturn(receiver, receiverElement, named)
        .filter(type -> type.getKind().isPrimitive())
        .filter(read -> LensFocus.unboxesTo(typeUtils, focusType, read))
        .orElse(null);
  }

  /**
   * The parameter types of the one-argument methods named {@code written} on {@code
   * receiverElement} that the generated class can call, the candidates a call passing one argument
   * chooses among, as {@link CallBinding} weighs them.
   */
  private List<TypeMirror> writtenParameters(
      TypeElement receiverElement, String written, String targetPackage) {
    return members.of(receiverElement, targetPackage).stream()
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
        members.constructorsOf(sourceElement, targetPackage).stream()
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

    if (!members.isVisibleFrom(parameterElement, targetPackage)) {
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
        members.constructorsOf((TypeElement) sourceType.asElement(), targetPackage)) {
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
   * <p>Unlike {@link ProcessorUtils#memberTypeOf} this does not guard on {@link
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
        members.constructorsOf((TypeElement) sourceType.asElement(), targetPackage).stream()
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
