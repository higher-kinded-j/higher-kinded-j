// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

/**
 * The guarded canonical-constructor call that ends every accumulating assembly: {@code parse}, the
 * validated {@code patch}, a flattened group's own ladder, the fallible merge and the staged {@code
 * assemble()}. The components parse and accumulate first, and only once every one is valid does the
 * record's constructor run. An exception it throws then is the record's own invariant refusing that
 * combination, so it becomes a {@code FieldError} at the record's own path (the enclosing {@code
 * field(label, ...)} locates a nested record) rather than escaping and discarding every error the
 * enclosing ladders have accumulated.
 *
 * <p>Any {@code RuntimeException} counts, as it does for {@code ValidatedPrism.canonical}: the null
 * guard keeps a {@code null} component out of the constructor, so what it throws is overwhelmingly
 * the invariant. Its message becomes the error's; an exception without one, or with a blank one,
 * reads {@code "not a valid Range"}.
 *
 * <p>An assembly whose legs are still parsing ends in a constructor <em>thunk</em> ({@code () ->
 * new Range(lo, hi)}) rather than the constructed value, and the emitted {@code hkj$construct}
 * helper runs it, so only the constructor runs inside the guard and a leaf's own exception still
 * escapes. The same helper serves a {@code fields()} ladder and a chunked ladder alike, and the
 * thunk is target-typed by the helper's parameter wherever the chain ends. The staged {@code
 * assemble()} holds values already validated, so its curried chain runs nothing but the
 * constructor, and a plain {@code try} around it is exact without deepening the curried lambda
 * javac must type.
 */
final class GuardedConstruction {

  private GuardedConstruction() {}

  private static final ClassName VALIDATED =
      ClassName.get("org.higherkindedj.hkt.validated", "Validated");
  private static final ClassName FIELD_ERROR =
      ClassName.get("org.higherkindedj.hkt.validated", "FieldError");
  private static final ClassName NEL =
      ClassName.get("org.higherkindedj.hkt.nonemptylist", "NonEmptyList");
  private static final ClassName SUPPLIER = ClassName.get("java.util.function", "Supplier");

  /**
   * The {@code hkj$construct} helper every thunk-ended assembly calls: it runs the assembled thunk,
   * and an exception the constructor throws becomes an unlabelled {@code FieldError}, which the
   * enclosing label locates.
   */
  static MethodSpec helper() {
    TypeVariableName t = TypeVariableName.get("T");
    TypeName channel = ParameterizedTypeName.get(NEL, FIELD_ERROR);
    return MethodSpec.methodBuilder("hkj$construct")
        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
        .addTypeVariable(t)
        .returns(ParameterizedTypeName.get(VALIDATED, channel, t))
        .addParameter(
            ParameterizedTypeName.get(VALIDATED, channel, ParameterizedTypeName.get(SUPPLIER, t)),
            "assembled")
        .addParameter(String.class, "fallbackMessage")
        .addJavadoc(
            "Runs the canonical constructor over the validated components: an exception it throws is"
                + " the record's\ninvariant refusing them, reported at the record's path with its"
                + " message, or with {@code\nfallbackMessage} when it has none.\n")
        .addCode("return assembled.flatMap(\n$>$>constructor -> {\n$>")
        .addCode(
            guarded(CodeBlock.of("$T.validNel(constructor.get())", VALIDATED), "fallbackMessage"))
        .addCode("$<});\n$<$<")
        .build();
  }

  /**
   * The guarded call over an assembly ending in a constructor thunk: {@code
   * hkj$construct(assembled, "not a valid Range")}. Its arguments indent a continuation below the
   * call, wherever it lands.
   */
  static CodeBlock call(CodeBlock assembled, TypeName type) {
    return CodeBlock.of("hkj$$construct(\n$>$>$L,\n$S$<$<)", assembled, fallbackMessage(type));
  }

  /**
   * The {@code return} of a guarded call. It is plain code rather than a javapoet statement, whose
   * first line break would indent the call's arguments a second continuation deeper.
   */
  static CodeBlock returning(CodeBlock assembled, TypeName type) {
    return CodeBlock.of("return $L;\n", call(assembled, type));
  }

  /**
   * The {@code return} of an assembly over values already validated, whose chain runs only the
   * constructor: a {@code try} returning it, reporting a refusal as the helper does.
   */
  static CodeBlock returningCaught(CodeBlock assembled, TypeName type) {
    return guarded(assembled, CodeBlock.of("$S", fallbackMessage(type)));
  }

  /**
   * A {@code fields()} ladder over {@code legs}, ending in {@code terminal}: the legs indent a
   * continuation below the ladder's head.
   */
  static CodeBlock ladder(List<CodeBlock> legs, CodeBlock terminal) {
    CodeBlock.Builder ladder = CodeBlock.builder().add("$T.fields()$>$>", VALIDATED);
    legs.forEach(ladder::add);
    return ladder.add(terminal).add("$<$<").build();
  }

  /** The constructor thunk {@code () -> new Range(lo, hi)}. */
  static CodeBlock thunk(TypeName type, CodeBlock args) {
    return CodeBlock.of("() -> new $T($L)", type, args);
  }

  /**
   * The constructor thunk behind the reads its arguments close over, {@code { var note =
   * domain.note(); return () -> new Slot(code, lo, hi, note); }}: each read runs once the
   * components have validated, as before, but outside the guard, so an exception it throws is not
   * mistaken for the record's invariant.
   */
  static CodeBlock boundThunk(List<CodeBlock> reads, TypeName type, CodeBlock args) {
    CodeBlock.Builder block = CodeBlock.builder().add("{\n$>");
    reads.forEach(read -> block.add("$L;\n", read));
    return block.add("return $L;\n$<}", thunk(type, args)).build();
  }

  /**
   * A {@code fields()} ladder's terminal over the legs' lambda parameters, {@code .apply((lo, hi)
   * -> body)}, where {@code body} ends in a constructor thunk.
   */
  static CodeBlock apply(List<String> params, CodeBlock body) {
    return CodeBlock.of("\n.apply(($L) -> $L)", String.join(", ", params), body);
  }

  /**
   * {@link #apply} over a bare thunk whose arguments are exactly the parameters, in order: {@code
   * .apply((lo, hi) -> () -> new Range(lo, hi))}.
   */
  static CodeBlock applyThunk(List<String> params, TypeName type) {
    return apply(params, thunk(type, CodeBlock.of("$L", String.join(", ", params))));
  }

  /**
   * The lambda parameter names for a ladder's legs, named after the legs so the emitted terminal
   * reads as the constructor call it is. A name the enclosing method already declares ({@code
   * reserved}) takes underscore suffixes until free of the method's names and of every leg's, since
   * a lambda parameter may not shadow a method parameter (JLS 6.4), and merge methods carry the
   * spec author's own parameter names.
   */
  static List<String> parameterNames(List<String> legs, Set<String> reserved) {
    Set<String> taken = new HashSet<>(reserved);
    taken.addAll(legs);
    List<String> params = new ArrayList<>(legs.size());
    for (String leg : legs) {
      StringBuilder name = new StringBuilder(leg);
      if (reserved.contains(leg)) {
        do {
          name.append('_');
        } while (taken.contains(name.toString()));
        taken.add(name.toString());
      }
      params.add(name.toString());
    }
    return List.copyOf(params);
  }

  /** {@code "not a valid Range"}, after the record's simple name. */
  private static String fallbackMessage(TypeName type) {
    ClassName raw =
        type instanceof ParameterizedTypeName parameterized
            ? parameterized.rawType()
            : (ClassName) type;
    return "not a valid " + raw.simpleName();
  }

  /**
   * {@code try { return value; }}, returning a {@code RuntimeException} it throws as an unlabelled
   * {@code FieldError} carrying its message, or {@code fallback} when that is null or blank.
   */
  private static CodeBlock guarded(CodeBlock value, Object fallback) {
    return CodeBlock.builder()
        .beginControlFlow("try")
        .addStatement("return $L", value)
        .nextControlFlow("catch (RuntimeException refused)")
        .addStatement("String message = refused.getMessage()")
        .addStatement(
            "return $T.invalidNel($T.of(message == null || message.isBlank() ? $L : message))",
            VALIDATED,
            FIELD_ERROR,
            fallback)
        .endControlFlow()
        .build();
  }
}
