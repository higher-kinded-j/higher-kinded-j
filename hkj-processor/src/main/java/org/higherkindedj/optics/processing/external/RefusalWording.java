// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import java.util.List;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * The wording a copy strategy's refusals and warnings share: how one names an unchecked conversion
 * and a list of names, and the remedies it offers, another strategy or a focus declared as the type
 * its getter reads.
 *
 * <p>{@link CopyStrategyChecks}, {@link SetterChecks}, {@link BuilderChainChecks} and {@link
 * ConstructorOrderChecks} each phrase their refusals with it, so that whichever check refuses, the
 * remedy names the other strategies alike, and none that rebuilds through a constructor no call can
 * write.
 */
final class RefusalWording {

  private RefusalWording() {}

  /**
   * Why an unchecked conversion or call in generated code is refused, as the end of a reason: the
   * clause after "and".
   */
  static final String UNCHECKED_IN_GENERATED_CODE =
      "javac flags that as unchecked in the generated file, which no @SuppressWarnings on your spec"
          + " reaches.";

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
   * The raw type a declared type converts to {@code to} unchecked through: itself where it is raw,
   * and otherwise the raw supertype it reaches {@code to}'s class as, as a class extending a raw
   * {@code ArrayList} reaches {@code List}. An unchecked conversion from a declared type is always
   * to a declared type, whose class the source reaches through such a supertype.
   */
  static DeclaredType rawView(Types types, DeclaredType from, DeclaredType to) {
    return ProcessorUtils.isRaw(from)
        ? from
        : (DeclaredType) ProcessorUtils.supertypeOf(types, from, (TypeElement) to.asElement());
  }

  /** Names as a sentence lists them: {@code 'a()'}, {@code 'a()' and 'b()'}, or with commas. */
  static String listed(List<String> names) {
    return names.size() == 1
        ? names.getFirst()
        : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
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

  /**
   * The remedy that rebuilds the source type with another strategy, as an unfinished clause: any of
   * them, or where no constructor call can rebuild it, as {@link
   * CallableMembers#constructorUnwritable} says, only those that name no constructor.
   */
  static String rebuildAnotherWay(DeclaredType sourceType, String tag) {
    String source = ProcessorUtils.simpleTypeName(sourceType);
    return CallableMembers.constructorUnwritable(sourceType)
        ? rebuildWith(source, tag, List.of("@Wither", "@ViaBuilder"))
        : rebuildWith(source, tag);
  }

  /** The fix that declares the focus a getter reads, for a diagnostic: an unfinished clause. */
  static String declareFocusAs(TypeMirror focus, String getterName) {
    return "Declare the lens's focus as '"
        + ProcessorUtils.simpleTypeName(focus)
        + "', the type '"
        + getterName
        + "()' reads";
  }
}
