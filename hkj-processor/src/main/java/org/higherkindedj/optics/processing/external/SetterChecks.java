// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.Diagnostics;
import org.higherkindedj.optics.processing.util.NamingConvention;
import org.higherkindedj.optics.processing.util.ProcessorUtils;
import org.higherkindedj.optics.processing.util.ProtobufCollection;

/**
 * The one-argument call a lens passes its new value to, and whether it can: a {@code @ViaBuilder}
 * builder's setter, or a {@code @ViaCopyAndSet} copy's.
 *
 * <p>The call is read as javac reads it, on the type it is made on, a {@link Receiver}: the source
 * type, or the builder {@code toBuilder()} hands back, captured. {@link #boundSetter} reads which
 * method the call binds, and {@link #setsUnchecked} whether javac could only make it unchecked.
 * Each refusal is made at the spec method: a name with no method that takes the value, a static
 * method, a call javac cannot choose a method for or could only make unchecked, and a parameter
 * that names the type a wildcard stands for. A name the type lacks, or none of whose methods takes
 * one argument, is offered the setter it most likely meant, where one is near; a protobuf-java
 * builder's adder of one element is offered the method that writes the field whole.
 *
 * <p>A {@code @Wither}'s call, made on the source, is held to the same rules for its wildcards and
 * its remedies, so {@link #setsUnchecked}, {@link #takesTheValue}, {@link #standInNamed}, {@link
 * #declareTheSpecOver}, {@link #narrowerFocus}, {@link #ambiguityFix} and {@link
 * #boundThroughTheGetter} serve its checks too.
 *
 * <p>Split from {@link CopyStrategyChecks}, which reads the annotation and asks this class for the
 * call, as {@link BuilderChainChecks} does for a builder's setter.
 */
final class SetterChecks {

  private final Types typeUtils;
  private final Elements elementUtils;
  private final Messager messager;
  private final CallableMembers members;

  /**
   * Creates the checks over a round's utilities.
   *
   * @param typeUtils the round's type utilities; must not be null
   * @param elementUtils the round's element utilities; must not be null
   * @param messager where refusals are reported; must not be null
   * @param members the reading of a type's callable methods; must not be null
   */
  SetterChecks(Types typeUtils, Elements elementUtils, Messager messager, CallableMembers members) {
    this.typeUtils = typeUtils;
    this.elementUtils = elementUtils;
    this.messager = messager;
    this.members = members;
  }

  /**
   * The type a generated one-argument call is made on, as the call reaches it and as a message
   * names it: the source type itself, or the builder {@code toBuilder()} hands back.
   */
  sealed interface Receiver {

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
   * Whose wildcard a parameter names the type of, where it does, as capture made it: the spec's
   * source type's, or the builder's own, as {@code toBuilder()} declares it hands the builder back.
   */
  enum StandIn {
    SOURCE,
    BUILDER
  }

  /**
   * What a generated setter call came to: the method it binds, a refusal already reported, or a
   * choice javac settles for itself.
   */
  sealed interface SetterCall {

    /**
     * The call binds this method.
     *
     * @param method the method the call binds
     */
    record Binds(ExecutableElement method) implements SetterCall {}

    /** The call cannot be written as the lens needs it, and an error was reported. */
    record Refused() implements SetterCall {}

    /** Which method the call binds rests on inference, so the rest is left to javac. */
    record Undecided() implements SetterCall {}
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
  SetterCall boundSetter(
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
        members.of(on.element(), targetPackage).stream()
            .filter(member -> member.getSimpleName().contentEquals(setterName))
            .toList();
    List<ExecutableElement> oneParameter =
        named.stream().filter(member -> member.getParameters().size() == 1).toList();
    if (named.isEmpty()) {
      reportMissingSetter(method, tag, on, setterName, attribute, targetPackage);
      return new SetterCall.Refused();
    }
    if (LensFocus.focusRead(focusType) == null) {
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
                + members.signatureOn(on.shown(), bound)
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
            methods.stream()
                .map(member -> "'" + CallableMembers.signatureAsDeclared(member) + "'")
                .toList();
        Diagnostics.error(
            messager,
            method,
            tag,
            "The generated call to '"
                + setterName
                + "' on a '"
                + ownerName
                + "' cannot choose between "
                + RefusalWording.listed(signatures)
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
   * as what the getter reads, boxed. The getter is the one {@link CallableMembers#accessorNamed}
   * chooses, as for every read the lens makes, and the one-argument call binds as one passing what
   * it reads. Empty where javac weighs more than the getter, or cannot infer the focus from it: a
   * wildcard bounded from below, a getter with type parameters of its own, whose read javac infers
   * from the focus rather than the other way about, a read the wildcard's bound does not admit, or
   * a call that rests on inference or binds nothing. javac settles or reports each of those at the
   * generated call.
   *
   * @param focusType the lens's focus
   * @param owner the type the call is made on
   * @param named the methods of the name the call chooses among
   * @param source the source type {@code S}, whose getter the lens reads its focus through
   * @param getterName that getter
   * @param targetPackage the package the optics class is generated into
   * @return the method the call binds, or empty
   */
  Optional<ExecutableElement> boundThroughTheGetter(
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
    return Optional.ofNullable(
            CallableMembers.accessorNamed(
                typeUtils,
                source,
                members.of((TypeElement) source.asElement(), targetPackage),
                getterName))
        .filter(getter -> getter.getTypeParameters().isEmpty())
        .map(
            getter ->
                LensFocus.asFocus(
                    typeUtils, ProcessorUtils.returnTypeCalledOn(typeUtils, source, getter)))
        .filter(read -> typeUtils.isAssignable(read, bound))
        .map(read -> CallBinding.resolve(typeUtils, owner, List.of(read), named))
        .filter(CallBinding.Binds.class::isInstance)
        .map(binding -> ((CallBinding.Binds) binding).candidate());
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
  boolean setsUnchecked(
      ExecutableElement method,
      String tag,
      Receiver on,
      ExecutableElement bound,
      TypeMirror focusType,
      String call,
      DeclaredType sourceType) {
    DeclaredType owner = on.type();
    if (ProcessorUtils.callsUnchecked(typeUtils, owner, bound)) {
      String signature = CallableMembers.signatureAsDeclared(bound);
      String reason =
          "A raw type erases the parameters of the methods called through it, and "
              + RefusalWording.UNCHECKED_IN_GENERATED_CODE;
      String rebuild = RefusalWording.rebuildAnotherWay(sourceType, tag);
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
    TypeMirror focus = LensFocus.focusRead(focusType);
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
            + members.signatureOn(on.shown(), bound)
            + "' takes the lens's focus, "
            + RefusalWording.described(typeUtils, focus, parameter)
            + ", only through an unchecked conversion to '"
            + parameterName
            + "'.",
        "The generated lens sets through "
            + call
            + ", and "
            + RefusalWording.UNCHECKED_IN_GENERATED_CODE,
        "Declare the lens over '"
            + parameterName
            + "', the type '"
            + bound.getSimpleName()
            + "' takes; otherwise "
            + RefusalWording.rebuildAnotherWay(sourceType, tag)
            + ".");
    return true;
  }

  /**
   * Whether a one-parameter method can take a value the lens holds when called as {@code on} says:
   * not where its parameter is the type a wildcard the call reaches it through stands for, which
   * takes no value at all, as {@code withValue(T)} on a {@code Box<?>}.
   */
  boolean takesTheValue(Receiver on, ExecutableElement member) {
    DeclaredType captured = (DeclaredType) typeUtils.capture(on.type());
    TypeMirror parameter =
        ProcessorUtils.memberOf(typeUtils, captured, member).getParameterTypes().getFirst();
    return standInNamed(on, captured, List.of(parameter)).isEmpty();
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
  Optional<StandIn> standInNamed(Receiver on, DeclaredType captured, List<TypeMirror> parameters) {
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

  /**
   * The types capture made, in {@code captured}, of the wildcards {@code written} writes, each a
   * type of its own that no type a spec writes names. {@code written} is the type captured, or the
   * same class as a declaration writes it; an inner class reaches the wildcards of the type it is a
   * member of too.
   */
  List<Element> standIns(DeclaredType written, DeclaredType captured) {
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

  /** Whether a type is the type capture made of a wildcard bounded below, {@code ? super X}. */
  private static boolean boundedBelow(TypeMirror type) {
    return type.getKind() == TypeKind.TYPEVAR
        && ((TypeVariable) type).getLowerBound().getKind() != TypeKind.NULL;
  }

  /**
   * The remedy for a parameter that names what a wildcard of the source type stands for, as an
   * unfinished clause: the spec names the type instead, and the focus as {@code method} then takes
   * it. No other strategy helps, since each sets the value through a method that names the type
   * alike.
   */
  String declareTheSpecOver(DeclaredType sourceType, String method) {
    return "Declare the spec over '"
        + ProcessorUtils.simpleTypeName(typeUtils.erasure(sourceType))
        + "' with a type argument in place of each wildcard, a type parameter of the spec if need"
        + " be, and the focus as the type '"
        + method
        + "' then takes";
  }

  /**
   * The remedy where the focus is wider than the wrapper of the primitive its getter reads, {@code
   * Number} over an {@code int} getter, and one of the methods the call might bind takes that
   * primitive. The value such a lens sets may be any {@code Number}, so it is passed as it is, and
   * only a focus of the wrapper itself is passed unboxed. An unfinished clause, or empty where the
   * focus is not wider so.
   */
  Optional<String> narrowerFocus(
      DeclaredType source,
      String getterName,
      TypeMirror focusType,
      DeclaredType owner,
      List<ExecutableElement> candidates) {
    return members
        .zeroArgumentReturn(source, (TypeElement) source.asElement(), getterName)
        .filter(
            read ->
                read.getKind().isPrimitive() && !LensFocus.unboxesTo(typeUtils, focusType, read))
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
                RefusalWording.declareFocusAs(LensFocus.asFocus(typeUtils, read), getterName)
                    + ", which the call then passes unboxed");
  }

  /**
   * The fix for a one-argument call javac cannot choose for: a focus of the type the one meant
   * takes, or, where the methods take one type under the source's instantiation, as {@code
   * withX(T)} and {@code withX(String)} do over a {@code W<String>}, another strategy, since no
   * focus can tell them apart.
   */
  String ambiguityFix(
      DeclaredType owner, List<ExecutableElement> methods, DeclaredType source, String tag) {
    TypeMirror first = ProcessorUtils.firstParameterTypeIn(typeUtils, owner, methods.getFirst());
    boolean oneType =
        methods.stream()
            .allMatch(
                method ->
                    typeUtils.isSameType(
                        ProcessorUtils.firstParameterTypeIn(typeUtils, owner, method), first));
    return oneType
        ? ProcessorUtils.capitalise(RefusalWording.rebuildAnotherWay(source, tag)) + "."
        : "Declare the lens's focus as the parameter type of the one you mean, or "
            + RefusalWording.rebuildAnotherWay(source, tag)
            + ".";
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
                            ? CallableMembers.signatureAsDeclared(member)
                            : members.signatureOn(listed, member))
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
                  + RefusalWording.rebuildAnotherWay(source, tag);
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
              + RefusalWording.rebuildAnotherWay(source, tag);
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
            + named.stream().map(member -> members.signatureOn(on.shown(), member)).toList()
            + "."
            + offer,
        "Set "
            + tag
            + "'s '"
            + attribute
            + "' to a method that takes the value the lens sets; otherwise "
            + RefusalWording.rebuildAnotherWay(source, tag)
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
            + CallableMembers.didYouMean(
                collectionAdder(on, setterName, setterNames(tag, on, targetPackage))
                    .or(
                        () ->
                            setterConvention(tag)
                                .nearest(setterName, setterNames(tag, on, targetPackage)))),
        "Set " + tag + "'s '" + attribute + "' to a method '" + ownerName + "' declares.");
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
    return CallableMembers.didYouMean(
        CallableMembers.takesNoSingleArgument(named)
            ? adder.or(() -> setterConvention(tag).sameValueAmong(setterName, names))
            : adder);
  }

  /**
   * The names a strategy's setter is offered from: the instance methods of the type the call is
   * made on that the generated class can call with one argument, but under {@code @ViaBuilder} none
   * that hands nothing back, which ends the chain.
   */
  private List<String> setterNames(String tag, Receiver on, String targetPackage) {
    return CallableMembers.namesOf(
        members.of(on.element(), targetPackage).stream()
            .filter(
                member ->
                    member.getParameters().size() == 1
                        && members.offerable(member, on.element())
                        && takesTheValue(on, member)
                        && !(tag.equals("@ViaBuilder")
                            && member.getReturnType().getKind() == TypeKind.VOID))
            .toList());
  }

  /**
   * The convention a strategy's setter is offered in: a builder carries on with what its setter
   * hands back, where a copy's setter is meant to set the copy it is called on.
   */
  private static NamingConvention setterConvention(String tag) {
    return tag.equals("@ViaBuilder") ? NamingConvention.BUILDER_SETTER : NamingConvention.SETTER;
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
            + (sources
                ? members.signatureOn(on.named(), bound)
                : CallableMembers.signatureAsDeclared(bound))
            + "' takes a type that names what a wildcard of '"
            + ProcessorUtils.simpleTypeName(sources ? sourceType : on.shown())
            + "' stands for, which the lens's focus, "
            + RefusalWording.described(typeUtils, focus, bound.getParameters().getFirst().asType())
            + ", reaches only through an unchecked conversion.",
        "The generated lens sets through "
            + call
            + ", and "
            + RefusalWording.UNCHECKED_IN_GENERATED_CODE,
        sources
            ? declareTheSpecOver(sourceType, bound.getSimpleName().toString()) + "."
            : "Point "
                + tag
                + "'s 'toBuilder' at a method that hands back the builder with its type"
                + " arguments; otherwise "
                + RefusalWording.rebuildAnotherWay(sourceType, tag)
                + ".");
  }
}
