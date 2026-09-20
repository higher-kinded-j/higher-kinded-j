// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyInfo;
import org.higherkindedj.optics.processing.external.SpecAnalysis.CopyStrategyKind;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * What a spec's lens method rebuilds its source type through, and whether it can.
 *
 * <p>Each of the four copy strategies names methods as strings: the accessor a lens reads through,
 * and the wither, builder chain, setter or constructor order it writes through. The generated lens
 * calls them as written, so a name the source type does not carry, or one that cannot take the
 * value the lens sets, is a compile error in a file its author never wrote. Every one of them is
 * read here instead, against the type the spec names, and refused at the spec method.
 *
 * <p>The rules the refusals rest on are javac's own, and two of them are deliberately not repeated:
 * a call whose binding rests on inference, and a focus written as a wildcard, whose type such a
 * lens draws from its getter as much as from the wildcard. Both are read leniently, so that nothing
 * javac would accept is refused here. {@link WitherBinding} answers which method a one-argument
 * call binds.
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
  CopyStrategyChecks(Types typeUtils, Elements elementUtils, Messager messager) {
    this.typeUtils = typeUtils;
    this.elementUtils = elementUtils;
    this.messager = messager;
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
    // analyse() admits a source type only when asElement gives a TypeElement, which on javac
    // leaves DECLARED, ERROR and INTERSECTION - every one of them a DeclaredType. That is what
    // makes the cast total; 'is a declared type' on its own would not.
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
      if (readsThroughUnusableGetter(
              method,
              "@ViaBuilder",
              declaredSource,
              sourceTypeElement,
              getter.isEmpty() ? fieldName : getter,
              focusType,
              targetPackage)
          || rebuildsThroughUnusableBuilder(
              method,
              declaredSource,
              sourceTypeElement,
              toBuilder,
              setter.isEmpty() ? fieldName : setter,
              build,
              focusType,
              targetPackage)) {
        return Optional.empty();
      }
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.VIA_BUILDER,
              CopyStrategyInfo.forBuilder(
                  getter,
                  toBuilder,
                  setter,
                  build,
                  builderFocusType(
                      method, declaredSource, sourceTypeElement, getter, toBuilder, setter))));
    }

    // Check for @Wither
    AnnotationMirror wither = ProcessorUtils.findAnnotation(method, WITHER_FQN);
    if (wither != null) {
      String getter = ProcessorUtils.getAnnotationString(wither, "getter", "");
      String witherMethod = ProcessorUtils.getAnnotationString(wither, "value", "");
      if (readsThroughUnusableGetter(
              method,
              "@Wither",
              declaredSource,
              sourceTypeElement,
              getter.isEmpty() ? fieldName : getter,
              focusType,
              targetPackage)
          || rebuildsThroughUnusableWither(
              method, declaredSource, sourceTypeElement, focusType, witherMethod, targetPackage)) {
        return Optional.empty();
      }
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.WITHER,
              CopyStrategyInfo.forWither(
                  getter,
                  witherMethod,
                  writtenFocusType(
                      method, declaredSource, sourceTypeElement, getter, witherMethod))));
    }

    // Check for @ViaConstructor
    AnnotationMirror viaConstructor = ProcessorUtils.findAnnotation(method, VIA_CONSTRUCTOR_FQN);
    if (viaConstructor != null) {
      if (rebuildsThroughUnwritableConstructor(method, declaredSource, "@ViaConstructor")) {
        return Optional.empty();
      }
      String[] parameterOrder =
          ProcessorUtils.getAnnotationStringArray(viaConstructor, "parameterOrder");
      if (readsThroughUnusableGetter(
              method,
              "@ViaConstructor",
              declaredSource,
              sourceTypeElement,
              fieldName,
              focusType,
              targetPackage)
          || rebuildsThroughUnreadableParameter(
              method, declaredSource, sourceTypeElement, parameterOrder, targetPackage)) {
        return Optional.empty();
      }
      return Optional.of(
          new CopyStrategyResult(
              CopyStrategyKind.VIA_CONSTRUCTOR,
              CopyStrategyInfo.forConstructor(
                  parameterOrder,
                  constructorFocusType(
                      method, declaredSource, sourceTypeElement, parameterOrder))));
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
      if (readsThroughUnusableGetter(
              method,
              "@ViaCopyAndSet",
              declaredSource,
              sourceTypeElement,
              fieldName,
              focusType,
              targetPackage)
          || setsThroughUnusableSetter(
              method,
              "@ViaCopyAndSet",
              declaredSource,
              sourceTypeElement,
              setter,
              focusType,
              targetPackage)) {
        return Optional.empty();
      }
      TypeMirror written = writtenFocusType(method, declaredSource, sourceTypeElement, "", setter);
      if (copyConstructor.isEmpty()) {
        return Optional.of(
            new CopyStrategyResult(
                CopyStrategyKind.VIA_COPY_AND_SET,
                CopyStrategyInfo.forCopyAndSet(null, setter, written)));
      }
      return resolveCopyConstructorParameterType(
              method, declaredSource, targetPackage, copyConstructor)
          .map(
              parameterType ->
                  new CopyStrategyResult(
                      CopyStrategyKind.VIA_COPY_AND_SET,
                      // Naming S itself is honoured by casting to nothing: a cast to the
                      // argument's own type says nothing, and javac reports it as redundant.
                      // Answering it here, with Types, leaves the generator one rule - a null
                      // parameter type means no cast - rather than a comparison of rendered names.
                      CopyStrategyInfo.forCopyAndSet(
                          typeUtils.isSameType(parameterType, sourceType) ? null : parameterType,
                          setter,
                          written)));
    }

    Diagnostics.error(
        messager,
        method,
        "@ImportOptics",
        "Lens method '" + method.getSimpleName() + "' carries no copy strategy annotation.",
        "A lens has to rebuild '"
            + sourceType
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

  /** The zero-parameter instance method of that name, which an accessor call needs, or null. */
  private ExecutableElement accessorNamed(List<ExecutableElement> members, String name) {
    return members.stream()
        .filter(
            member ->
                member.getSimpleName().contentEquals(name)
                    && member.getParameters().isEmpty()
                    && !member.getModifiers().contains(Modifier.STATIC))
        .findFirst()
        .orElse(null);
  }

  /** The element of a type a generated call is made on, or null where the type has none. */
  private TypeElement elementOf(TypeMirror type) {
    return typeUtils.asElement(type) instanceof TypeElement element ? element : null;
  }

  /**
   * The type a further call in a chain is made on: the step's own type, or for a step declared with
   * a type variable of its own the bound that variable carries, which is what javac infers it to
   * where nothing else pins it down. A bound naming more than one type leaves no single type to
   * read members on, and answers null for the caller to leave to javac.
   */
  private TypeMirror stepType(TypeMirror handedBack) {
    if (handedBack.getKind() != TypeKind.TYPEVAR) {
      return handedBack;
    }
    TypeMirror bound = ((TypeVariable) handedBack).getUpperBound();
    return bound.getKind() == TypeKind.INTERSECTION ? null : bound;
  }

  /**
   * The type a lens hands its focus back as, for checking what an accessor reads: the focus itself,
   * or null for a focus written as a wildcard, whose type javac infers from the accessor rather
   * than the other way about.
   */
  private TypeMirror focusRead(TypeMirror focusType) {
    return focusType.getKind() == TypeKind.WILDCARD ? null : focusType;
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
      String targetPackage) {
    String ownerName = ProcessorUtils.simpleTypeName(owner);
    List<String> accessors =
        callableMembers(ownerElement, targetPackage).stream()
            .filter(
                member ->
                    member.getParameters().isEmpty()
                        && !member.getModifiers().contains(Modifier.STATIC))
            .map(member -> member.getSimpleName().toString())
            .distinct()
            .sorted()
            .toList();
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
            + ProcessorUtils.nearestName(name, accessors)
                .map(near -> " Did you mean '" + near + "'?")
                .orElse(""),
        fix);
  }

  /**
   * Reports a getter the generated lens cannot read its focus through, and returns whether it did.
   *
   * <p>Every strategy reads with the same lambda, {@code source -> source.getX()}, so the name has
   * to be a zero-parameter instance method the generated class can call, and the value it reads has
   * to be one the lens can hand back as its focus. A focus written as a wildcard is inferred from
   * this very accessor, so there is nothing to hold it to.
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
    if (sourceType.getKind() == TypeKind.ERROR) {
      return false;
    }
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
          targetPackage);
      return true;
    }
    // Read on the captured source, as the call reads it: a value a wildcard types is a type of
    // its own there, and one the lens can hand back.
    TypeMirror read =
        ProcessorUtils.returnTypeIn(
            typeUtils, (DeclaredType) typeUtils.capture(sourceType), getter);
    TypeMirror focus = focusRead(focusType);
    if (focus == null || typeUtils.isAssignable(read, focus)) {
      return false;
    }
    // Named as the spec's own instantiation writes it, which is what the author reads, and in
    // full where a simple name would name both types.
    TypeMirror declaredRead = ProcessorUtils.returnTypeIn(typeUtils, sourceType, getter);
    boolean sameName =
        ProcessorUtils.simpleTypeName(declaredRead).equals(ProcessorUtils.simpleTypeName(focus));
    String focusName = sameName ? focus.toString() : ProcessorUtils.simpleTypeName(focus);
    String readName =
        sameName ? declaredRead.toString() : ProcessorUtils.simpleTypeName(declaredRead);
    // A focus is a type argument, so a primitive read is declared as its wrapper.
    String readAsFocus =
        declaredRead.getKind().isPrimitive()
            ? ProcessorUtils.simpleTypeName(
                typeUtils.boxedClass((PrimitiveType) declaredRead).asType())
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
   * Reports a setter a generated lens cannot set through on the source type, and returns whether it
   * did.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param setterName the method the strategy sets through
   * @param focusType the lens's focus, which the call passes
   * @param targetPackage the package the optics class is generated into
   * @return true when no method of the name can take the value, and an error was reported
   */
  private boolean setsThroughUnusableSetter(
      ExecutableElement method,
      String tag,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String setterName,
      TypeMirror focusType,
      String targetPackage) {
    if (sourceType.getKind() == TypeKind.ERROR) {
      return false;
    }
    return boundSetter(
            method,
            tag,
            sourceType,
            sourceTypeElement,
            setterName,
            focusType,
            "setter",
            ProcessorUtils.simpleTypeName(sourceType),
            targetPackage)
        instanceof SetterCall.Refused;
  }

  /**
   * What a generated one-argument call on {@code owner} comes to.
   *
   * <p>A focus written as a wildcard leaves the choice to javac, as it does for a wither, so the
   * call is undecided there rather than refused, and so is one javac settles by inference.
   *
   * @param method the annotated lens method, for error reporting
   * @param tag the strategy annotation, which the message names
   * @param owner the type the call is made on
   * @param ownerElement that type's element, whose members are searched
   * @param setterName the method the strategy sets through
   * @param focusType the lens's focus, which the call passes
   * @param attribute the annotation attribute that names it
   * @param sourceName the source type, which a remedy offers another strategy for
   * @param targetPackage the package the optics class is generated into
   * @return the method the call binds, a refusal that has been reported, or undecided
   */
  private SetterCall boundSetter(
      ExecutableElement method,
      String tag,
      DeclaredType owner,
      TypeElement ownerElement,
      String setterName,
      TypeMirror focusType,
      String attribute,
      String sourceName,
      String targetPackage) {
    List<ExecutableElement> named =
        callableMembers(ownerElement, targetPackage).stream()
            .filter(member -> member.getSimpleName().contentEquals(setterName))
            .toList();
    List<ExecutableElement> oneParameter =
        named.stream().filter(member -> member.getParameters().size() == 1).toList();
    if (named.isEmpty()) {
      reportMissingSetter(method, tag, owner, ownerElement, setterName, attribute, targetPackage);
      return new SetterCall.Refused();
    }
    if (focusRead(focusType) == null) {
      if (oneParameter.isEmpty()) {
        reportSetterOfAnotherArity(method, tag, owner, setterName, named, attribute, sourceName);
        return new SetterCall.Refused();
      }
      // Which of several such methods the call binds is javac's to settle, and so is what it
      // hands back; where the name carries only one, that is the one.
      return oneParameter.size() == 1
          ? new SetterCall.Binds(oneParameter.getFirst())
          : new SetterCall.Undecided();
    }
    String call = "'" + setterName + "(newValue)'";
    return switch (WitherBinding.resolve(typeUtils, owner, focusType, named)) {
      case WitherBinding.Binds(ExecutableElement bound) -> {
        if (!bound.getModifiers().contains(Modifier.STATIC)) {
          yield new SetterCall.Binds(bound);
        }
        Diagnostics.error(
            messager,
            method,
            tag,
            "'"
                + signatureOn(owner, bound)
                + "' is static, so the generated lens cannot set through it on a '"
                + ProcessorUtils.simpleTypeName(owner)
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
      case WitherBinding.NoneApplies() -> {
        Diagnostics.error(
            messager,
            method,
            tag,
            "No method '"
                + setterName
                + "' of '"
                + ProcessorUtils.simpleTypeName(owner)
                + "' takes the lens's focus type '"
                + ProcessorUtils.simpleTypeName(focusType)
                + "'.",
            "The generated lens sets through "
                + call
                + " with the new value typed '"
                + ProcessorUtils.simpleTypeName(focusType)
                + "'. Found on '"
                + ProcessorUtils.simpleTypeName(owner)
                + "': "
                + named.stream().map(member -> signatureOn(owner, member)).toList()
                + ".",
            "Set "
                + tag
                + "'s '"
                + attribute
                + "' to a method that takes the value the getter reads"
                + (tag.equals("@ViaBuilder")
                    ? ", or point @ViaBuilder's 'getter' at an accessor one of them takes and"
                        + " declare the focus as its type"
                    : "")
                + "; otherwise "
                + rebuildWith(sourceName, tag)
                + ".");
        yield new SetterCall.Refused();
      }
      case WitherBinding.Ambiguous(List<ExecutableElement> methods) -> {
        List<String> signatures =
            methods.stream().map(member -> "'" + signatureOn(owner, member) + "'").toList();
        Diagnostics.error(
            messager,
            method,
            tag,
            "The generated call to '"
                + setterName
                + "' on a '"
                + ProcessorUtils.simpleTypeName(owner)
                + "' cannot choose between "
                + String.join(", ", signatures.subList(0, signatures.size() - 1))
                + " and "
                + signatures.getLast()
                + ".",
            "The generated lens sets through "
                + call
                + " with the new value typed '"
                + ProcessorUtils.simpleTypeName(focusType)
                + "', which each of them takes, with no parameter more specific than every other.",
            "Declare the lens's focus as the parameter type of the one you mean.");
        yield new SetterCall.Refused();
      }
      case WitherBinding.Undecided(List<ExecutableElement> ignored) -> new SetterCall.Undecided();
    };
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

  /** Reports a setter no overload of which takes one argument, whatever the lens's focus is. */
  private void reportSetterOfAnotherArity(
      ExecutableElement method,
      String tag,
      DeclaredType owner,
      String setterName,
      List<ExecutableElement> named,
      String attribute,
      String sourceName) {
    String ownerName = ProcessorUtils.simpleTypeName(owner);
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
            + named.stream().map(member -> signatureOn(owner, member)).toList()
            + ".",
        "Set "
            + tag
            + "'s '"
            + attribute
            + "' to a method that takes the value the lens sets; otherwise "
            + rebuildWith(sourceName, tag)
            + ".");
  }

  /** Reports a setter the generated optic calls and the type does not have. */
  private void reportMissingSetter(
      ExecutableElement method,
      String tag,
      TypeMirror owner,
      TypeElement ownerElement,
      String setterName,
      String attribute,
      String targetPackage) {
    String ownerName = ProcessorUtils.simpleTypeName(owner);
    List<String> setters =
        callableMembers(ownerElement, targetPackage).stream()
            .filter(
                member ->
                    member.getParameters().size() == 1
                        && !member.getModifiers().contains(Modifier.STATIC))
            .map(member -> member.getSimpleName().toString())
            .distinct()
            .sorted()
            .toList();
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
            + ProcessorUtils.nearestName(setterName, setters)
                .map(near -> " Did you mean '" + near + "'?")
                .orElse(""),
        "Set " + tag + "'s '" + attribute + "' to a method '" + ownerName + "' declares.");
  }

  /**
   * Reports a {@code @ViaBuilder} whose chain the generated lens cannot walk, and returns whether
   * it did.
   *
   * <p>The lens rebuilds with {@code source.toBuilder().setter(newValue).build()}, so each step has
   * to exist where the one before it leads: the builder the source hands back, the setter that
   * takes the focus, and a {@code build} that hands the source type back.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param toBuilderName the method that hands back the builder
   * @param setterName the builder method that takes the focus
   * @param buildName the builder method that hands the source type back
   * @param focusType the lens's focus
   * @param targetPackage the package the optics class is generated into
   * @return true when the chain cannot be walked, and an error was reported
   */
  private boolean rebuildsThroughUnusableBuilder(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String toBuilderName,
      String setterName,
      String buildName,
      TypeMirror focusType,
      String targetPackage) {
    if (sourceType.getKind() == TypeKind.ERROR) {
      return false;
    }
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
              + "' declares.",
          targetPackage);
      return true;
    }
    TypeMirror builderType =
        stepType(ProcessorUtils.returnTypeIn(typeUtils, sourceType, toBuilder));
    if (builderType == null || builderType.getKind() == TypeKind.ERROR) {
      return false;
    }
    TypeElement builderElement = elementOf(builderType);
    if (builderElement == null) {
      reportBuilderStep(method, toBuilderName + "()", builderType, "a builder to set through");
      return true;
    }
    if (reportsUnnameableStep(
        method, builderElement, toBuilderName + "()", sourceType, targetPackage)) {
      return true;
    }
    SetterCall setter =
        boundSetter(
            method,
            "@ViaBuilder",
            (DeclaredType) builderType,
            builderElement,
            setterName,
            focusType,
            "setter",
            ProcessorUtils.simpleTypeName(sourceType),
            targetPackage);
    // Which method an undecided call binds is javac's to settle, so what it hands back is too.
    if (!(setter instanceof SetterCall.Binds(ExecutableElement bound))) {
      return setter instanceof SetterCall.Refused;
    }
    TypeMirror setType =
        stepType(ProcessorUtils.returnTypeIn(typeUtils, (DeclaredType) builderType, bound));
    if (setType == null || setType.getKind() == TypeKind.ERROR) {
      return false;
    }
    TypeElement setElement = elementOf(setType);
    if (setElement == null) {
      reportBuilderStep(
          method,
          signatureOn((DeclaredType) builderType, bound),
          setType,
          "a builder to build from");
      return true;
    }
    if (reportsUnnameableStep(
        method,
        setElement,
        signatureOn((DeclaredType) builderType, bound),
        sourceType,
        targetPackage)) {
      return true;
    }
    ExecutableElement build = accessorNamed(callableMembers(setElement, targetPackage), buildName);
    if (build == null) {
      reportMissingAccessor(
          method,
          "@ViaBuilder",
          setType,
          setElement,
          buildName,
          "finish the value it rebuilds",
          "the '" + ProcessorUtils.simpleTypeName(setType) + "' the setter hands back",
          "Set @ViaBuilder's 'build' to a method '"
              + ProcessorUtils.simpleTypeName(setType)
              + "' declares.",
          targetPackage);
      return true;
    }
    TypeMirror built = ProcessorUtils.returnTypeIn(typeUtils, (DeclaredType) setType, build);
    if (typeUtils.isAssignable(built, sourceType)) {
      return false;
    }
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String builtName = ProcessorUtils.simpleTypeName(built);
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
            + "' is not.",
        "Set @ViaBuilder's 'build' to the method that finishes a '"
            + source
            + "', or "
            + rebuildWith(source, "@ViaBuilder")
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
            + rebuildWith(ProcessorUtils.simpleTypeName(sourceType), "@ViaBuilder")
            + ".");
    return true;
  }

  /** Reports a builder step that hands back something no further call can be made on. */
  private void reportBuilderStep(
      ExecutableElement method, String step, TypeMirror handedBack, String wanted) {
    String handedBackName = ProcessorUtils.simpleTypeName(handedBack);
    Diagnostics.error(
        messager,
        method,
        "@ViaBuilder",
        "'" + step + "' hands back '" + handedBackName + "', which is not " + wanted + ".",
        "The generated lens rebuilds with"
            + " 'source.toBuilder().setter(newValue).build()', and each step is called on what the"
            + " step before it hands back.",
        "Name the builder methods this type's chain declares, or rebuild it with @Wither,"
            + " @ViaConstructor or @ViaCopyAndSet.");
  }

  /**
   * Reports a {@code @ViaConstructor} parameter order naming an accessor the source type does not
   * have, and returns whether it did.
   *
   * <p>Every name but the lens's own reads an argument, {@code source.x()}, for the constructor
   * call the lens rebuilds with.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param parameterOrder the names the annotation carries
   * @param targetPackage the package the optics class is generated into
   * @return true when one of them names no accessor, and an error was reported
   */
  private boolean rebuildsThroughUnreadableParameter(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      String[] parameterOrder,
      String targetPackage) {
    if (sourceType.getKind() == TypeKind.ERROR) {
      return false;
    }
    String fieldName = method.getSimpleName().toString();
    if (parameterOrder.length > 0
        && Arrays.stream(parameterOrder).noneMatch(parameter -> parameter.equals(fieldName))) {
      Diagnostics.error(
          messager,
          method,
          "@ViaConstructor",
          "'parameterOrder' names no argument for the lens's own '" + fieldName + "'.",
          "The generated lens rebuilds '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' from the order given, and passes the value it sets where the lens's own name"
              + " stands; naming it nowhere would set nothing.",
          "Add '"
              + fieldName
              + "' to @ViaConstructor's 'parameterOrder', at the place the constructor takes it.");
      return true;
    }
    List<ExecutableElement> members = callableMembers(sourceTypeElement, targetPackage);
    for (String parameter : parameterOrder) {
      if (parameter.equals(fieldName) || accessorNamed(members, parameter) != null) {
        continue;
      }
      reportMissingAccessor(
          method,
          "@ViaConstructor",
          sourceType,
          sourceTypeElement,
          parameter,
          "read the argument '" + parameter + "'",
          "'source'",
          "Name in @ViaConstructor's 'parameterOrder' the accessors '"
              + ProcessorUtils.simpleTypeName(sourceType)
              + "' declares, in the order its constructor takes them.",
          targetPackage);
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
   * method the call binds, as {@link WitherBinding} repeats javac's choice, that is checked, not
   * any method that carries the name. The call has to bind one method, an instance method, since a
   * static one never reads the value it is called on, and that method has to hand back the source
   * type. Every method of the name that the generated class can reach is weighed, of any arity,
   * static or not, as javac weighs them.
   *
   * <p>Where the choice rests on inference that is not repeated, the candidates are read leniently:
   * the name passes when one of them is an instance method that hands back the source type, and
   * javac settles the rest at the generated call. A wildcard focus is read that way too, since the
   * type such a lens infers is drawn from the getter as much as from the wildcard.
   *
   * @param method the annotated lens method, for error reporting
   * @param sourceType the source type {@code S}, as the spec names it
   * @param sourceTypeElement the element of {@code S}, whose members are searched
   * @param focusType the lens's focus type, which the wither is called with
   * @param witherName the method the annotation names
   * @param targetPackage the package the optics class is generated into
   * @return true when the call binds no such method, and an error was reported; a source type that
   *     did not resolve is left to javac
   */
  private boolean rebuildsThroughUnusableWither(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeElement sourceTypeElement,
      TypeMirror focusType,
      String witherName,
      String targetPackage) {
    // A source type that did not resolve has no members to read, and javac's own error names the
    // type that is missing, which is the one worth reading.
    if (sourceType.getKind() == TypeKind.ERROR) {
      return false;
    }
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
    // the type the call passes is not the one the spec writes, and which method it binds is
    // javac's to settle.
    if (focusType.getKind() == TypeKind.WILDCARD) {
      return noCandidateRebuilds(
          method,
          sourceType,
          named.stream().filter(candidate -> candidate.getParameters().size() == 1).toList(),
          null);
    }
    return switch (WitherBinding.resolve(typeUtils, sourceType, focusType, named)) {
      case WitherBinding.Binds(ExecutableElement bound) -> {
        if (bound.getModifiers().contains(Modifier.STATIC)) {
          reportStaticWither(method, sourceType, bound, focusType);
          yield true;
        }
        if (ProcessorUtils.returnsOwner(typeUtils, sourceType, bound)) {
          yield false;
        }
        // Only an overloaded name needs the focus to say which method it binds.
        reportWitherOfAnotherType(method, sourceType, bound, named.size() > 1 ? focusType : null);
        yield true;
      }
      case WitherBinding.NoneApplies() -> {
        reportInapplicableWither(method, sourceType, focusType, named);
        yield true;
      }
      case WitherBinding.Ambiguous(List<ExecutableElement> methods) -> {
        reportAmbiguousWither(method, sourceType, focusType, methods);
        yield true;
      }
      case WitherBinding.Undecided(List<ExecutableElement> candidates) ->
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
   * Whether the generated class can call a member on the source type.
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
    List<ExecutableElement> withers =
        callable.stream()
            .filter(
                member ->
                    member.getParameters().size() == 1
                        && !member.getModifiers().contains(Modifier.STATIC)
                        && ProcessorUtils.returnsOwner(typeUtils, sourceType, member))
            .toList();
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
          sets + " No one-parameter instance method of '" + source + "' hands it back.",
          ProcessorUtils.capitalise(rebuildWith(source, "@Wither")) + ".");
      return;
    }
    List<String> names =
        withers.stream()
            .map(member -> member.getSimpleName().toString())
            .distinct()
            .sorted()
            .toList();
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
            + ProcessorUtils.nearestName(witherName, names)
                .map(near -> " Did you mean '" + near + "'?")
                .orElse("")
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
            + rebuildWith(source, "@Wither")
            + ".");
  }

  /** Reports a {@code @Wither} none of whose methods takes the value the lens sets. */
  private void reportInapplicableWither(
      ExecutableElement method,
      DeclaredType sourceType,
      TypeMirror focusType,
      List<ExecutableElement> named) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    String witherName = named.getFirst().getSimpleName().toString();
    String found =
        settingThrough(witherName, focusType)
            + ". Found on '"
            + source
            + "': "
            + named.stream().map(member -> signatureOn(sourceType, member)).toList()
            + ".";
    // A parameter typed by one of the source's wildcards takes no value whatever the focus, so the
    // remedy there is the source type, not the focus.
    boolean wildcardSource =
        sourceType.getTypeArguments().stream()
            .anyMatch(typeArgument -> typeArgument.getKind() == TypeKind.WILDCARD);
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "No method '"
            + witherName
            + "' of '"
            + source
            + "' takes the lens's focus type '"
            + ProcessorUtils.simpleTypeName(focusType)
            + "'.",
        wildcardSource
            ? found
                + " A parameter a wildcard of '"
                + source
                + "' stands in takes no value at all, since the type it stands for is unknown."
            : found,
        (wildcardSource
                ? "Declare the spec over the type each wildcard stands for, or "
                    + rebuildWith(source, "@Wither")
                : "Name a wither that takes the value the getter reads, or point 'getter' at an"
                    + " accessor one of them takes and declare the focus as its type; otherwise "
                    + rebuildWith(source, "@Wither"))
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
        methods.stream().map(member -> "'" + signatureOn(sourceType, member) + "'").toList();
    Diagnostics.error(
        messager,
        method,
        "@Wither",
        "The generated call to '"
            + witherName
            + "' cannot choose between "
            + String.join(", ", signatures.subList(0, signatures.size() - 1))
            + " and "
            + signatures.getLast()
            + ".",
        settingThrough(witherName, argument)
            + ", which each of them takes, with no parameter more specific than every other.",
        "Declare the lens's focus as the parameter type of the one you mean, or "
            + rebuildWith(source, "@Wither")
            + ".");
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
            + rebuildWith(source, "@Wither")
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
            + rebuildWith(source, "@Wither")
            + ".");
  }

  /** The remedy every wither refusal ends on, for a diagnostic's fix: an unfinished clause. */
  private static String rebuildWith(String source, String tag) {
    List<String> others =
        List.of("@Wither", "@ViaBuilder", "@ViaConstructor", "@ViaCopyAndSet").stream()
            .filter(strategy -> !strategy.equals(tag))
            .toList();
    return "rebuild '"
        + source
        + "' with "
        + String.join(", ", others.subList(0, others.size() - 1))
        + " or "
        + others.getLast();
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
    List<? extends TypeMirror> parameters =
        ProcessorUtils.memberOf(typeUtils, sourceType, member).getParameterTypes();
    List<String> names =
        new ArrayList<>(parameters.stream().map(ProcessorUtils::simpleTypeName).toList());
    if (member.isVarArgs()) {
      // The last parameter is an array the author wrote as a variable-arity one.
      String last = names.getLast();
      names.set(names.size() - 1, last.substring(0, last.length() - "[]".length()) + "...");
    }
    return member.getSimpleName()
        + String.join(", ", names).transform(joined -> "(" + joined + ")");
  }

  /**
   * Reports a source type whose own constructor call cannot be written, and returns whether it did.
   *
   * <p>A strategy that rebuilds through a constructor emits {@code new S(...)}, and a wildcard
   * cannot be written as a type argument there: {@code new Node<?>(...)} is not Java, whatever the
   * arguments. The strategies that rebuild through a wither or a builder name no constructor and
   * are unaffected, so this is asked per strategy rather than of the source type as a whole.
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

    boolean wildcard =
        declared.getTypeArguments().stream()
            .anyMatch(argument -> argument.getKind() == TypeKind.WILDCARD);
    // A DeclaredType's element is always a TypeElement. Only a member type that is not static
    // carries an enclosing instance; a nested interface, enum or record is implicitly static.
    TypeElement element = (TypeElement) declared.asElement();
    boolean innerClass =
        element.getNestingKind() == NestingKind.MEMBER
            && !element.getModifiers().contains(Modifier.STATIC);
    if (!wildcard && !innerClass) {
      return false;
    }
    String name = ProcessorUtils.simpleTypeName(declared);
    Diagnostics.error(
        messager,
        method,
        annotation,
        "'"
            + name
            + "' is "
            + (wildcard ? "written with a wildcard type argument" : "an inner class")
            + ", and this strategy rebuilds it through a constructor.",
        "The generated set function calls 'new "
            + name
            + "(...)', which is not something that can be written for "
            + (wildcard
                ? "a wildcard: a constructor call has to name the type argument."
                : "an inner class: the call needs an enclosing instance the generated class has"
                    + " no way to reach."),
        wildcard
            ? "Name the type the wildcard stands for, or use @Wither, which rebuilds through a"
                + " method and needs no constructor."
            : "Declare the source type static, or use @Wither, which rebuilds through a method and"
                + " needs no constructor.");
    return true;
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
   * @return the primitive type to unbox the focus to, or null
   */
  private TypeMirror writtenFocusType(
      ExecutableElement method,
      DeclaredType receiver,
      TypeElement receiverElement,
      String getter,
      String written) {
    return Optional.ofNullable(primitiveRead(method, receiver, receiverElement, getter))
        .map(read -> unboxedFocus(read, writtenParameters(receiverElement, written)))
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
   * @return the primitive type to unbox the focus to, or null
   */
  private TypeMirror builderFocusType(
      ExecutableElement method,
      DeclaredType source,
      TypeElement sourceElement,
      String getter,
      String toBuilder,
      String setter) {
    String named = setter.isEmpty() ? method.getSimpleName().toString() : setter;
    return Optional.ofNullable(primitiveRead(method, source, sourceElement, getter))
        .flatMap(
            read ->
                builderOf(source, sourceElement, toBuilder)
                    .map(builder -> unboxedFocus(read, writtenParameters(builder, named))))
        .orElse(null);
  }

  /**
   * The builder {@code toBuilder} hands back, or empty where the source declares no such method, or
   * one handing back something with no members to call. Either way nothing is unboxed, and javac
   * reports the call the generator writes.
   */
  private Optional<TypeElement> builderOf(
      DeclaredType source, TypeElement sourceElement, String toBuilder) {
    return zeroArgumentMethods(source, sourceElement, toBuilder).map(typeUtils::asElement).stream()
        .flatMap(element -> ElementFilter.typesIn(List.of(element)).stream())
        .findFirst();
  }

  /**
   * The type the lens reads its focus through, when that is a primitive: the zero-argument method
   * the strategy names, or the optic method's own name where it names none. Null for a getter that
   * hands back a reference type, which the call takes as it is, and for one the source does not
   * declare, which javac reports at the generated call.
   */
  private TypeMirror primitiveRead(
      ExecutableElement method, DeclaredType receiver, TypeElement receiverElement, String getter) {
    String named = getter.isEmpty() ? method.getSimpleName().toString() : getter;
    return zeroArgumentMethods(receiver, receiverElement, named)
        .filter(type -> type.getKind().isPrimitive())
        .orElse(null);
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
   * receiverElement}, the candidates a call passing one argument chooses among.
   */
  private List<TypeMirror> writtenParameters(TypeElement receiverElement, String written) {
    return ElementFilter.methodsIn(elementUtils.getAllMembers(receiverElement)).stream()
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
   * harmlessly, since it cannot take a call it is not applicable to. The focus also has to be an
   * argument of the call at all, which a {@code parameterOrder} naming other getters leaves it out
   * of. Answering it here, with {@code Types}, leaves the generator the one rule
   * {@code @ViaCopyAndSet}'s cast follows: a null type means no cast.
   *
   * @param method the annotated optic method, named after its getter
   * @param source the source type {@code S}
   * @param sourceElement the source type's element
   * @param parameterOrder the getters the constructor call reads its arguments from, in order
   * @return the primitive type to unbox the focus to, or null
   */
  private TypeMirror constructorFocusType(
      ExecutableElement method,
      DeclaredType source,
      TypeElement sourceElement,
      String[] parameterOrder) {
    int focus = List.of(parameterOrder).indexOf(method.getSimpleName().toString());
    if (focus < 0) {
      // The call passes the focus nowhere, so there is no argument to unbox.
      return null;
    }
    List<TypeMirror> candidates =
        ElementFilter.constructorsIn(sourceElement.getEnclosedElements()).stream()
            .map(ExecutableElement::getParameters)
            .filter(parameters -> parameters.size() == parameterOrder.length)
            .map(parameters -> parameters.get(focus).asType())
            .toList();
    return Optional.ofNullable(primitiveRead(method, source, sourceElement, ""))
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
              + sourceType
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
   * Finds the supertype relation the cast depends on, reporting when it does not hold.
   *
   * <p>A hierarchy containing a type this round cannot resolve - one another processor has yet to
   * generate, say - reads as having no supertypes at all, which would make every name look wrong.
   * The compiler is asked directly before any name is rejected, so an unreadable hierarchy costs
   * the instantiation rather than drawing an error that blames the attribute for a missing type
   * javac is already reporting.
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
            + sourceType
            + "' does not extend or implement.",
        "The generated set function passes the source to the copy constructor as '("
            + parameterElement.getSimpleName()
            + ") source', and only a supertype of the source can be cast to there.",
        "Name a supertype of '" + sourceType + "', or drop the attribute to pass it unchanged.");
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
   * Returns whether the generated class may call a constructor of {@code member}'s kind.
   *
   * <p>{@code protected} is package access here: it reaches a subclass, and the generated optics
   * class is not one.
   *
   * @param member the constructor being considered
   * @param targetPackage the package the optics class is generated into
   * @return true if the generated class can call it
   */
  private boolean isAccessibleFrom(Element member, String targetPackage) {
    Set<Modifier> modifiers = member.getModifiers();
    if (modifiers.contains(Modifier.PRIVATE)) {
      return false;
    }
    if (modifiers.contains(Modifier.PUBLIC)) {
      return true;
    }
    return elementUtils.getPackageOf(member).getQualifiedName().contentEquals(targetPackage);
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
    for (ExecutableElement constructor :
        ElementFilter.constructorsIn(sourceType.asElement().getEnclosedElements())) {
      List<? extends VariableElement> parameters = constructor.getParameters();
      // A constructor the generated class cannot call is no use, however well it fits.
      if (parameters.size() != 1 || !isAccessibleFrom(constructor, targetPackage)) {
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
        ElementFilter.constructorsIn(sourceType.asElement().getEnclosedElements()).stream()
            .filter(constructor -> constructor.getParameters().size() == 1)
            .filter(constructor -> isAccessibleFrom(constructor, targetPackage))
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
