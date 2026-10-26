// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.external.SetterChecks.Receiver;
import org.higherkindedj.optics.processing.external.SetterChecks.SetterCall;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.NamingConvention;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The chain a {@code @ViaBuilder} lens rebuilds through, {@code
 * source.toBuilder().setter(newValue).build()}, and whether the generated lens can walk it.
 *
 * <p>{@link #chain} reads each step on what the one before it hands back, captured, as javac reads
 * the call, and names each step in a message as the spec reaches it, never by a capture. Where the
 * setter adds to a collection the builder copied, the chain calls the clear method {@link
 * CollectionClear} finds first, so that the lens replaces the collection. {@link #builderOf} reads
 * the builder the setter is called on, which decides whether the focus is passed unboxed.
 *
 * <p>Split from {@link CopyStrategyChecks}, which reads the annotation and decides how the focus is
 * passed, and asks this class for the chain. {@link SetterChecks} reads the setter's call.
 */
final class BuilderChainChecks {

  private final Types typeUtils;
  private final Elements elementUtils;
  private final Messager messager;
  private final CallableMembers members;
  private final SetterChecks setters;

  /**
   * Creates the checks over a round's utilities.
   *
   * @param typeUtils the round's type utilities; must not be null
   * @param elementUtils the round's element utilities; must not be null
   * @param messager where refusals are reported; must not be null
   * @param members the reading of a type's callable methods; must not be null
   * @param setters the checks of the setter's call; must not be null
   */
  BuilderChainChecks(
      Types typeUtils,
      Elements elementUtils,
      Messager messager,
      CallableMembers members,
      SetterChecks setters) {
    this.typeUtils = typeUtils;
    this.elementUtils = elementUtils;
    this.messager = messager;
    this.members = members;
    this.setters = setters;
  }

  /**
   * What reading a {@code @ViaBuilder} chain came to: a chain the generated lens rebuilds through,
   * or a refusal already reported.
   */
  sealed interface BuilderChain {

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
  BuilderChain chain(
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
        CallableMembers.accessorNamed(
            typeUtils, sourceType, members.of(sourceTypeElement, targetPackage), toBuilderName);
    if (toBuilder == null) {
      members.reportMissingAccessor(
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
    List<ExecutableElement> builderMembers = members.of(builderElement, targetPackage);
    // A builder that builds the source type, as a self-typed Builder<Dog> does, is no reason to
    // blame the inheritance, and a misspelt setter on it draws the plain message and its offer.
    boolean buildsTheSource =
        Optional.ofNullable(
                CallableMembers.accessorNamed(
                    typeUtils, (DeclaredType) builderType, builderMembers, buildName))
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
              + RefusalWording.rebuildAnotherWay(sourceType, "@ViaBuilder")
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
            setters.standIns(sourceType, capturedSource));
    SetterCall setter =
        setters.boundSetter(
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
    if (setters.setsUnchecked(
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
    String setterShown = members.signatureOn(builderShown, bound);
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
    ExecutableElement build =
        CallableMembers.accessorNamed(
            typeUtils, (DeclaredType) setType, members.of(setElement, targetPackage), buildName);
    if (build == null) {
      members.reportMissingAccessor(
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
        fix + ", or " + RefusalWording.rebuildAnotherWay(sourceType, "@ViaBuilder") + ".");
    return new BuilderChain.Refused();
  }

  /**
   * The builder {@code toBuilder} hands back, the one the setter is called on, read as the chain
   * reads it; or empty where the source declares no such method, or one handing back something with
   * no members to call, or bounded by several types whose setters javac weighs together. In each
   * case {@link CopyStrategyChecks} passes the focus as it is, and {@link #chain} reports what it
   * refuses.
   */
  Optional<TypeElement> builderOf(
      DeclaredType source,
      TypeElement sourceElement,
      String toBuilder,
      String setter,
      String targetPackage) {
    return Optional.ofNullable(
            CallableMembers.accessorNamed(
                typeUtils, source, members.of(sourceElement, targetPackage), toBuilder))
        .map(
            method ->
                ProcessorUtils.returnTypeIn(
                    typeUtils, (DeclaredType) typeUtils.capture(source), method))
        .map(builder -> stepTypeFor(builder, setter, targetPackage))
        .map(this::elementOf);
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
            + RefusalWording.described(typeUtils, builtShown, sourceType)
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
            + RefusalWording.UNCHECKED_IN_GENERATED_CODE,
        "Point @ViaBuilder's '"
            + attribute
            + "' at "
            + (rawStep == null
                ? "a method that returns '" + source + "'"
                : "a method that hands back the builder with its type arguments")
            + ", or "
            + RefusalWording.rebuildAnotherWay(sourceType, "@ViaBuilder")
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
    if (members.isVisibleFrom(step, targetPackage)) {
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
            + RefusalWording.rebuildAnotherWay(sourceType, "@ViaBuilder")
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
            + RefusalWording.rebuildAnotherWay(sourceType, "@ViaBuilder")
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
        ProcessorUtils.unlessWildcard(
            ProcessorUtils.returnTypeIn(typeUtils, (DeclaredType) setDeclared, build));
    if (declared == null) {
      return reachedName(built, setShown);
    }
    return typeUtils.isAssignable(declared, sourceType) == buildsSource
        ? declared
        : build.getReturnType();
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
    return members.accessorNames(sourceTypeElement, targetPackage).stream()
        .map(BuilderChainChecks::propertySpelt)
        .filter(property -> !property.equals(own))
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * The property an accessor reads, capitalised: what follows {@code get} or {@code is}, as a
   * bean's getter or its beanspec spelling {@code geteMails} spells it, or the whole name of a
   * record's.
   */
  private static String propertySpelt(String accessor) {
    return ProcessorUtils.capitalise(NamingConvention.ACCESSOR.withoutPrefix(accessor));
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
                        ? members.of(element, targetPackage).stream()
                            .filter(
                                member ->
                                    member.getParameters().size() == 1
                                        && members.offerable(member, element)
                                        && member.getReturnType().getKind() != TypeKind.VOID)
                            .map(member -> member.getSimpleName().toString())
                        : members.accessorNames(element, targetPackage).stream())
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
            + RefusalWording.listed(boundNames)
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
            + CallableMembers.didYouMean(
                (setter ? NamingConvention.BUILDER_SETTER : NamingConvention.ACCESSOR)
                    .nearest(name, names)),
        "Set @ViaBuilder's '"
            + attribute
            + "' to a method one of them declares that the generated class can call.");
    return true;
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
        .flatMap(element -> members.of(element, targetPackage).stream())
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

  /** The element of a type a generated call is made on, or null where the type has none. */
  private TypeElement elementOf(TypeMirror type) {
    return typeUtils.asElement(type) instanceof TypeElement element ? element : null;
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
}
